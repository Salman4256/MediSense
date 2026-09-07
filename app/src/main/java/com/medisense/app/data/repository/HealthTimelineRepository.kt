package com.medisense.app.data.repository

import com.medisense.app.data.local.dao.*
import com.medisense.app.data.remote.supabase.AuthService
import com.medisense.app.domain.engine.HealthTimelineEngine
import com.medisense.app.domain.model.*
import com.medisense.app.domain.security.SecureLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository orchestrating the Unified Explainable Health Timeline & Health Journey.
 * Aggregates information deterministically across Modules 1–17 with strict user-isolation.
 */
@Singleton
class HealthTimelineRepository @Inject constructor(
    private val authService: AuthService,
    private val healthProfileDao: HealthProfileDao,
    private val medicationDao: MedicationDao,
    private val medicationHistoryDao: MedicationHistoryDao,
    private val appointmentDao: AppointmentDao,
    private val predictionHistoryDao: PredictionHistoryDao,
    private val securityAuditEventDao: SecurityAuditEventDao,
    private val personalHealthContextRepository: PersonalHealthContextRepository,
    private val longitudinalHealthRepository: LongitudinalHealthRepository,
    private val contextualRiskRepository: ContextualRiskRepository,
    private val personalizedGuidanceRepository: PersonalizedGuidanceRepository,
    private val healthDataQualityRepository: HealthDataQualityRepository,
    private val securityAuditRepository: SecurityAuditRepository
) {

    private val TAG = "HealthTimelineRepo"

    fun getCurrentUserId(): String? {
        return authService.getCurrentUserId()
    }

    /**
     * Reactively observes the Unified Health Timeline, merging emissions from underlying Room DAOs and repositories.
     * Evaluates deterministically offline without making any network calls.
     */
    fun observeTimeline(filterFlow: Flow<HealthTimelineFilter>): Flow<Pair<List<HealthTimelineEvent>, HealthTimelineSummary>> {
        val userId = authService.getCurrentUserId() ?: "offline-user"

        return combine(
            filterFlow,
            healthProfileDao.observeHealthProfile(userId),
            predictionHistoryDao.observePredictionHistory(userId),
            medicationDao.getMedicationsForUser(userId),
            medicationHistoryDao.getHistoryForUser(userId),
            appointmentDao.observeAppointments(userId),
            securityAuditEventDao.observeRecentAuditEvents(userId, 50),
            longitudinalHealthRepository.observeLongitudinalSummary(AnalysisPeriod.DAYS_30),
            personalHealthContextRepository.observePersonalHealthContext(),
            contextualRiskRepository.observeContextualRiskAssessment(),
            personalizedGuidanceRepository.observePersonalizedGuidance()
        ) { array ->
            val filter = array[0] as HealthTimelineFilter
            val profile = array[1] as? com.medisense.app.data.local.entity.HealthProfileEntity
            val predictions = (array[2] as? List<*>)?.filterIsInstance<com.medisense.app.data.local.entity.PredictionHistoryEntity>() ?: emptyList()
            val medications = (array[3] as? List<*>)?.filterIsInstance<com.medisense.app.data.local.entity.MedicationEntity>() ?: emptyList()
            val medicationHistory = (array[4] as? List<*>)?.filterIsInstance<com.medisense.app.data.local.entity.MedicationHistoryEntity>() ?: emptyList()
            val appointments = (array[5] as? List<*>)?.filterIsInstance<com.medisense.app.data.local.entity.AppointmentEntity>() ?: emptyList()
            val auditEvents = (array[6] as? List<*>)?.filterIsInstance<com.medisense.app.data.local.entity.SecurityAuditEventEntity>() ?: emptyList()
            val longitudinalSummary = array[7] as? LongitudinalHealthSummary
            val personalContext = array[8] as? PersonalHealthContext
            val riskAssessment = array[9] as? ContextualRiskAssessment
            val guidanceResult = array[10] as? GuidanceEngineResult

            HealthTimelineEngine.buildTimeline(
                userId = userId,
                profile = profile,
                predictions = predictions,
                medications = medications,
                medicationHistory = medicationHistory,
                appointments = appointments,
                auditEvents = auditEvents,
                longitudinalSummary = longitudinalSummary,
                personalContext = personalContext,
                riskAssessment = riskAssessment,
                guidanceResult = guidanceResult,
                dataQualitySummary = null, // In reactive combine, data quality is computed on demand or kept light
                filter = filter,
                currentTime = System.currentTimeMillis()
            )
        }.flowOn(Dispatchers.Default)
    }

    /**
     * Executes a one-shot snapshot evaluation of the Health Timeline for the authenticated user.
     */
    suspend fun getTimelineSnapshot(filter: HealthTimelineFilter): Pair<List<HealthTimelineEvent>, HealthTimelineSummary> = withContext(Dispatchers.IO) {
        val userId = authService.getCurrentUserId() ?: "offline-user"

        val profile = healthProfileDao.getHealthProfile(userId)
        val predictions = predictionHistoryDao.getAllPredictionHistoryForUserSync(userId)
        val medications = medicationDao.getAllMedicationsForUserSync(userId)
        val medicationHistory = medicationHistoryDao.getAllHistoryForUserSync(userId)
        val appointments = appointmentDao.getAllAppointmentsForUserSync(userId)
        val auditEvents = securityAuditEventDao.getAuditEventsForUser(userId)

        val dataQuality = try {
            healthDataQualityRepository.evaluateDataQuality()
        } catch (e: Exception) {
            SecureLogger.e(TAG, "Data quality evaluation skipped in timeline snapshot", e)
            null
        }

        val personalContext = personalHealthContextRepository.observePersonalHealthContext().firstOrNull()
        val longitudinalSummary = longitudinalHealthRepository.observeLongitudinalSummary(AnalysisPeriod.DAYS_30).firstOrNull()
        val riskAssessment = contextualRiskRepository.observeContextualRiskAssessment().firstOrNull()
        val guidanceResult = personalizedGuidanceRepository.observePersonalizedGuidance().firstOrNull()

        val (events, summary) = HealthTimelineEngine.buildTimeline(
            userId = userId,
            profile = profile,
            predictions = predictions,
            medications = medications,
            medicationHistory = medicationHistory,
            appointments = appointments,
            auditEvents = auditEvents,
            longitudinalSummary = longitudinalSummary,
            personalContext = personalContext,
            riskAssessment = riskAssessment,
            guidanceResult = guidanceResult,
            dataQualitySummary = dataQuality,
            filter = filter,
            currentTime = System.currentTimeMillis()
        )

        SecureLogger.d(TAG, "Health timeline computed (${events.size} events, ${summary.activeMedicationsCount} active meds)")
        Pair(events, summary)
    }
}
