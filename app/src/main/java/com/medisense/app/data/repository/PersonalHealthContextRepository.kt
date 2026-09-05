package com.medisense.app.data.repository

import com.medisense.app.data.local.dao.AppointmentDao
import com.medisense.app.data.local.dao.HealthProfileDao
import com.medisense.app.data.local.dao.MedicationDao
import com.medisense.app.data.local.dao.MedicationHistoryDao
import com.medisense.app.data.local.dao.PredictionHistoryDao
import com.medisense.app.data.local.entity.AppointmentEntity
import com.medisense.app.data.local.entity.HealthProfileEntity
import com.medisense.app.data.local.entity.MedicationEntity
import com.medisense.app.data.local.entity.MedicationHistoryEntity
import com.medisense.app.data.local.entity.PredictionHistoryEntity
import com.medisense.app.data.remote.supabase.AuthService
import com.medisense.app.domain.model.AllergyContext
import com.medisense.app.domain.model.AppointmentContext
import com.medisense.app.domain.model.ChronicConditionContext
import com.medisense.app.domain.model.DemographicContext
import com.medisense.app.domain.model.MedicationContext
import com.medisense.app.domain.model.PersonalHealthContext
import com.medisense.app.domain.model.PersonalizationFeatures
import com.medisense.app.domain.model.PredictionContext
import com.medisense.app.utils.MedicationDateTimeUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PersonalHealthContextRepository @Inject constructor(
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
     * Reactively observes and aggregates the user's unified personal health context.
     * Guaranteed 100% offline-first from local Room database tables.
     */
    fun observePersonalHealthContext(): Flow<PersonalHealthContext> {
        val userId = getCurrentUserId()
        return combine(
            healthProfileDao.observeHealthProfile(userId),
            predictionHistoryDao.observePredictionHistory(userId),
            medicationDao.getActiveMedicationsForUser(userId),
            medicationHistoryDao.getHistoryForUser(userId),
            appointmentDao.observeAppointments(userId)
        ) { profile, predictions, activeMeds, medHistory, appointments ->
            buildPersonalHealthContext(
                userId = userId,
                profile = profile,
                predictions = predictions,
                activeMeds = activeMeds,
                medHistory = medHistory,
                appointments = appointments
            )
        }.flowOn(Dispatchers.IO)
    }

    private fun buildPersonalHealthContext(
        userId: String,
        profile: HealthProfileEntity?,
        predictions: List<PredictionHistoryEntity>,
        activeMeds: List<MedicationEntity>,
        medHistory: List<MedicationHistoryEntity>,
        appointments: List<AppointmentEntity>
    ): PersonalHealthContext {
        val now = System.currentTimeMillis()

        // 1. Demographics & Profile metrics
        val completeness = PersonalizationFeatures.calculateProfileCompleteness(profile)
        val age = PersonalizationFeatures.calculateAge(profile?.dateOfBirth)
        val bmi = PersonalizationFeatures.calculateBmi(profile?.height, profile?.weight)

        val demographics = DemographicContext(
            age = age,
            dateOfBirth = profile?.dateOfBirth,
            gender = profile?.gender,
            bloodGroup = profile?.bloodGroup,
            heightCm = profile?.height,
            weightKg = profile?.weight,
            bmi = bmi
        )

        // 2. Chronic Conditions & Allergies
        val chronicList = PersonalizationFeatures.parseListString(profile?.existingDiseases)
        val chronicConditions = ChronicConditionContext(
            hasChronicConditions = chronicList.isNotEmpty(),
            conditions = chronicList
        )

        val allergiesList = PersonalizationFeatures.parseListString(profile?.allergies)
        val allergies = AllergyContext(
            hasAllergies = allergiesList.isNotEmpty(),
            allergiesList = allergiesList
        )

        // 3. Predictions & Recurring Symptoms
        val recentPredictions = PersonalizationFeatures.filterRecentPredictions(predictions, now)
        val frequentSymptoms = PersonalizationFeatures.extractFrequentSymptoms(recentPredictions, topN = 3)
        val frequentDiseases = PersonalizationFeatures.extractFrequentDiseases(recentPredictions, topN = 2)
        val avgConfidence = PersonalizationFeatures.calculateAverageConfidence(recentPredictions)
        val latestPred = predictions.firstOrNull()?.predictedDisease

        val predictionContext = PredictionContext(
            totalCount = predictions.size,
            recentCount = recentPredictions.size,
            frequentSymptoms = frequentSymptoms,
            frequentDiseases = frequentDiseases,
            avgConfidence = avgConfidence,
            latestPrediction = latestPred
        )

        // 4. Medications & Adherence
        val recentMedHistory = PersonalizationFeatures.filterRecentMedicationHistory(medHistory, now)
        val adherenceStats = MedicationDateTimeUtils.calculateAdherence(recentMedHistory)
        val activeMedNames = activeMeds.map { it.medicineName }
        val adherencePercentage = if (recentMedHistory.isNotEmpty()) adherenceStats.percentage else null
        val adherenceSummary = if (adherencePercentage != null) {
            "${adherencePercentage.toInt()}% adherence (${adherenceStats.takenCount}/${adherenceStats.totalScheduled} taken)"
        } else if (activeMeds.isNotEmpty()) {
            "${activeMeds.size} active medication${if (activeMeds.size > 1) "s" else ""}"
        } else null

        val medicationContext = MedicationContext(
            activeCount = activeMeds.size,
            activeNames = activeMedNames,
            adherencePercentage = adherencePercentage,
            adherenceSummary = adherenceSummary
        )

        // 5. Appointments
        val upcomingAppts = PersonalizationFeatures.filterUpcomingAppointments(appointments, now)
        val nextAppt = upcomingAppts.firstOrNull()
        val dateFormat = SimpleDateFormat("MMM dd, yyyy • hh:mm a", Locale.getDefault())
        val nextApptFormattedDate = nextAppt?.let { dateFormat.format(Date(it.appointmentTimestamp)) }

        val appointmentContext = AppointmentContext(
            upcomingCount = upcomingAppts.size,
            nextAppointmentDate = nextApptFormattedDate,
            nextAppointmentDoctor = nextAppt?.doctorName,
            nextAppointmentType = nextAppt?.appointmentType
        )

        // 6. Personalization Score & Summary
        val personalizationScore = PersonalizationFeatures.calculatePersonalizationScore(
            completeness = completeness,
            activeMedCount = activeMeds.size,
            adherenceStats = if (recentMedHistory.isNotEmpty()) adherenceStats else null,
            recentPredictionCount = recentPredictions.size,
            hasRecurringSymptoms = frequentSymptoms.size >= 2,
            upcomingAppointmentCount = upcomingAppts.size
        )

        val generatedSummary = PersonalizationFeatures.generateFactualSummary(
            profileCompleteness = completeness,
            recentPredictions = recentPredictions,
            frequentSymptoms = frequentSymptoms,
            activeMeds = activeMeds,
            adherenceStats = if (recentMedHistory.isNotEmpty()) adherenceStats else null,
            upcomingAppointments = upcomingAppts
        )

        val hasSufficientData = completeness > 0 || predictions.isNotEmpty() || activeMeds.isNotEmpty() || appointments.isNotEmpty()

        return PersonalHealthContext(
            userId = userId,
            demographics = demographics,
            chronicConditions = chronicConditions,
            allergies = allergies,
            medications = medicationContext,
            predictions = predictionContext,
            appointments = appointmentContext,
            profileCompleteness = completeness,
            personalizationScore = personalizationScore,
            generatedSummary = generatedSummary,
            hasSufficientData = hasSufficientData
        )
    }
}
