package com.medisense.app.data.repository

import com.medisense.app.data.local.dao.AppointmentDao
import com.medisense.app.data.local.dao.HealthProfileDao
import com.medisense.app.data.local.dao.MedicationDao
import com.medisense.app.data.local.dao.MedicationHistoryDao
import com.medisense.app.data.local.dao.PredictionHistoryDao
import com.medisense.app.data.remote.supabase.AuthService
import com.medisense.app.domain.analytics.TemporalPatternAnalyzer
import com.medisense.app.domain.model.AnalysisPeriod
import com.medisense.app.domain.model.PersonalHealthContext
import com.medisense.app.domain.model.PersonalizationFeatures
import com.medisense.app.domain.rchr.RchrEngine
import com.medisense.app.domain.rchr.RchrReconstructionResult
import com.medisense.app.domain.rchr.RchrRepresentation
import com.medisense.app.utils.MedicationDateTimeUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RchrRepository @Inject constructor(
    private val healthProfileDao: HealthProfileDao,
    private val predictionHistoryDao: PredictionHistoryDao,
    private val medicationDao: MedicationDao,
    private val medicationHistoryDao: MedicationHistoryDao,
    private val appointmentDao: AppointmentDao,
    private val authService: AuthService
) {

    fun getCurrentUserId(): String {
        return authService.getCurrentUserId() ?: "offline-user"
    }

    /**
     * Reactively generates the Reversible Composite Health Representation (RCHR)
     * by combining all local Room health tables.
     */
    fun observeRchrRepresentation(): Flow<RchrRepresentation> {
        val userId = getCurrentUserId()

        return combine(
            healthProfileDao.observeHealthProfile(userId),
            predictionHistoryDao.observePredictionHistory(userId),
            medicationDao.getMedicationsForUser(userId),
            medicationHistoryDao.getHistoryForUser(userId),
            appointmentDao.observeAppointments(userId)
        ) { profile, predictions, medications, medHistory, appointments ->

            // 1. Compute Longitudinal Dynamics (Module 9B)
            val temporalSummary = TemporalPatternAnalyzer.analyzeLongitudinalHealth(
                userId = userId,
                period = AnalysisPeriod.DAYS_30,
                predictions = predictions,
                medications = medications,
                medicationHistory = medHistory,
                appointments = appointments
            )

            // 2. Compute Personal Context (Module 9A)
            val completeness = PersonalizationFeatures.calculateProfileCompleteness(profile)
            val activeMeds = medications.filter { it.active }
            val adhStats = MedicationDateTimeUtils.calculateAdherence(medHistory)
            val recurring = temporalSummary.recurringSymptoms.filter { it.isRecurring }
            val upcoming = appointments.filter { it.status == "SCHEDULED" && it.appointmentTimestamp >= System.currentTimeMillis() }

            val personalizationScore = PersonalizationFeatures.calculatePersonalizationScore(
                completeness = completeness,
                activeMedCount = activeMeds.size,
                adherenceStats = if (medHistory.isNotEmpty()) adhStats else null,
                recentPredictionCount = temporalSummary.predictionActivity.currentPeriodCount,
                hasRecurringSymptoms = recurring.isNotEmpty(),
                upcomingAppointmentCount = upcoming.size
            )

            val frequentSymptoms = PersonalizationFeatures.extractFrequentSymptoms(predictions)
            val contextSummary = PersonalizationFeatures.generateFactualSummary(
                profileCompleteness = completeness,
                recentPredictions = predictions.filter { it.predictionTimestamp >= System.currentTimeMillis() - (30 * 24 * 60 * 60 * 1000L) },
                frequentSymptoms = frequentSymptoms,
                activeMeds = activeMeds,
                adherenceStats = if (medHistory.isNotEmpty()) adhStats else null,
                upcomingAppointments = upcoming
            )

            val personalContext = PersonalHealthContext(
                userId = userId,
                demographics = com.medisense.app.domain.model.DemographicContext(),
                chronicConditions = com.medisense.app.domain.model.ChronicConditionContext(
                    hasChronicConditions = profile?.existingDiseases.isNullOrBlank().not(),
                    conditions = PersonalizationFeatures.parseListString(profile?.existingDiseases)
                ),
                allergies = com.medisense.app.domain.model.AllergyContext(
                    hasAllergies = profile?.allergies.isNullOrBlank().not(),
                    allergiesList = PersonalizationFeatures.parseListString(profile?.allergies)
                ),
                medications = com.medisense.app.domain.model.MedicationContext(
                    activeCount = activeMeds.size,
                    activeNames = activeMeds.map { it.medicineName }
                ),
                predictions = com.medisense.app.domain.model.PredictionContext(
                    totalCount = predictions.size,
                    recentCount = temporalSummary.predictionActivity.currentPeriodCount,
                    frequentSymptoms = frequentSymptoms
                ),
                appointments = com.medisense.app.domain.model.AppointmentContext(upcomingCount = upcoming.size),
                profileCompleteness = completeness,
                personalizationScore = personalizationScore,
                generatedSummary = contextSummary,
                hasSufficientData = completeness > 0 || predictions.isNotEmpty() || medications.isNotEmpty() || appointments.isNotEmpty()
            )

            // 3. Build Deterministic RCHR
            RchrEngine.buildRepresentation(
                userId = userId,
                profile = profile,
                predictions = predictions,
                medications = medications,
                medicationHistory = medHistory,
                appointments = appointments,
                temporalSummary = temporalSummary,
                personalContext = personalContext
            )
        }.flowOn(Dispatchers.IO)
    }

    /**
     * Executes round-trip reconstruction and consistency validation on an RCHR.
     */
    fun reconstructAndValidate(representation: RchrRepresentation): RchrReconstructionResult {
        return RchrEngine.reconstructHealthState(representation)
    }
}
