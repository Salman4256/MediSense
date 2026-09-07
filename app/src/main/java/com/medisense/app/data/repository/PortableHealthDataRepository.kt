package com.medisense.app.data.repository

import android.content.Context
import com.medisense.app.data.local.dao.*
import com.medisense.app.data.remote.supabase.AuthService
import com.medisense.app.domain.model.*
import com.medisense.app.domain.portability.PortableHealthDataMapper
import com.medisense.app.domain.portability.PortableHealthRecordSerializer
import com.medisense.app.domain.security.SecureLogger
import com.medisense.app.utils.MedicationDateTimeUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository orchestrating Personal Health Data Portability & Interoperability (Module 23).
 * Operates strictly 100% offline-first, local-only without remote EHR/cloud transfers,
 * and enforces strict user isolation by Supabase Auth UUID.
 */
@Singleton
class PortableHealthDataRepository @Inject constructor(
    private val authService: AuthService,
    private val healthProfileDao: HealthProfileDao,
    private val medicationDao: MedicationDao,
    private val medicationHistoryDao: MedicationHistoryDao,
    private val appointmentDao: AppointmentDao,
    private val predictionHistoryDao: PredictionHistoryDao,
    private val emergencyHealthCardRepository: EmergencyHealthCardRepository,
    private val healthDecisionTraceRepository: HealthDecisionTraceRepository,
    private val longitudinalHealthRepository: LongitudinalHealthRepository,
    private val personalHealthContextRepository: PersonalHealthContextRepository,
    private val contextualRiskRepository: ContextualRiskRepository,
    private val personalizedGuidanceRepository: PersonalizedGuidanceRepository,
    private val rchrRepository: RchrRepository,
    private val healthTimelineRepository: HealthTimelineRepository,
    private val healthDataQualityRepository: HealthDataQualityRepository,
    private val securityAuditRepository: SecurityAuditRepository
) {

    fun getCurrentUserId(): String {
        return authService.getCurrentUserId() ?: "offline-user"
    }

    /**
     * Builds a [PortableExportPreview] showing category breakdown, estimated resource count,
     * data quality warnings, and a JSON preview sample.
     */
    suspend fun generatePreview(scope: PortableExportScope): PortableExportPreview = withContext(Dispatchers.IO) {
        val userId = getCurrentUserId()

        val profile = healthProfileDao.getHealthProfile(userId)
        val medications = medicationDao.getAllMedicationsForUserSync(userId)
        val medicationHistory = medicationHistoryDao.getAllHistoryForUserSync(userId)
        val adherenceStats = MedicationDateTimeUtils.calculateAdherence(medicationHistory)
        val appointments = appointmentDao.getAllAppointmentsForUserSync(userId)
        val predictions = predictionHistoryDao.getAllPredictionHistoryForUserSync(userId)
        val longitudinal = longitudinalHealthRepository.observeLongitudinalSummary(AnalysisPeriod.DAYS_30).firstOrNull()
        val personalContext = personalHealthContextRepository.observePersonalHealthContext().firstOrNull()
        val risk = contextualRiskRepository.observeContextualRiskAssessment().firstOrNull()
        val guidance = personalizedGuidanceRepository.observePersonalizedGuidance().firstOrNull()
        val rchr = rchrRepository.observeRchrRepresentation().firstOrNull()
        val decisionTraces = healthDecisionTraceRepository.observeDecisionTraces().firstOrNull().orEmpty()
        val timelinePair = healthTimelineRepository.observeTimeline(flowOf(HealthTimelineFilter())).firstOrNull()
        val timelineEvents = timelinePair?.first.orEmpty()
        val emergencyCard = emergencyHealthCardRepository.getEmergencyHealthCard(userId)
        val qualitySummary = healthDataQualityRepository.evaluateDataQuality()

        val counts = PortableHealthDataMapper.computeCategoryCounts(
            scope = scope,
            profile = profile,
            medications = medications,
            medicationHistory = medicationHistory,
            appointments = appointments,
            predictions = predictions,
            longitudinal = longitudinal,
            personalContext = personalContext,
            guidance = guidance,
            rchr = rchr,
            decisionTraces = decisionTraces,
            timelineEvents = timelineEvents,
            emergencyCard = emergencyCard,
            qualitySummary = qualitySummary
        )

        val bundle = PortableHealthDataMapper.mapToBundle(
            userId = userId,
            scope = scope,
            profile = profile,
            medications = medications,
            medicationHistory = medicationHistory,
            adherenceStats = adherenceStats,
            appointments = appointments,
            predictions = predictions,
            longitudinal = longitudinal,
            personalContext = personalContext,
            riskAssessment = risk,
            guidance = guidance,
            rchr = rchr,
            decisionTraces = decisionTraces,
            timelineEvents = timelineEvents,
            emergencyCard = emergencyCard,
            qualitySummary = qualitySummary
        )

        val previewSample = PortableHealthRecordSerializer.generatePreviewJsonSample(bundle)

        PortableExportPreview(
            packageId = bundle.meta.packageId,
            totalCategoriesSelected = scope.selectedCategories.size,
            estimatedResourceCount = bundle.meta.totalResourceCount,
            categoryBreakdown = counts,
            qualitySummary = qualitySummary,
            previewJsonSample = previewSample
        )
    }

    /**
     * Executes the portable data mapping and serializes the resulting [PortableHealthRecordBundle]
     * to a local `.json` file in `cacheDir/exports/`. Records security audit telemetry.
     */
    suspend fun exportPortableBundle(
        context: Context,
        scope: PortableExportScope
    ): Result<PortableExportResult.Success> = withContext(Dispatchers.IO) {
        try {
            val userId = getCurrentUserId()

            // Record export started audit event
            securityAuditRepository.recordEvent(
                eventType = SecurityAuditEventType.HEALTH_DATA_EXPORT_STARTED,
                customDescription = "Portable health data export started for ${scope.selectedCategories.size} categories"
            )

            val profile = healthProfileDao.getHealthProfile(userId)
            val medications = medicationDao.getAllMedicationsForUserSync(userId)
            val medicationHistory = medicationHistoryDao.getAllHistoryForUserSync(userId)
            val adherenceStats = MedicationDateTimeUtils.calculateAdherence(medicationHistory)
            val appointments = appointmentDao.getAllAppointmentsForUserSync(userId)
            val predictions = predictionHistoryDao.getAllPredictionHistoryForUserSync(userId)
            val longitudinal = longitudinalHealthRepository.observeLongitudinalSummary(AnalysisPeriod.DAYS_30).firstOrNull()
            val personalContext = personalHealthContextRepository.observePersonalHealthContext().firstOrNull()
            val risk = contextualRiskRepository.observeContextualRiskAssessment().firstOrNull()
            val guidance = personalizedGuidanceRepository.observePersonalizedGuidance().firstOrNull()
            val rchr = rchrRepository.observeRchrRepresentation().firstOrNull()
            val decisionTraces = healthDecisionTraceRepository.observeDecisionTraces().firstOrNull().orEmpty()
            val timelinePair = healthTimelineRepository.observeTimeline(flowOf(HealthTimelineFilter())).firstOrNull()
            val timelineEvents = timelinePair?.first.orEmpty()
            val emergencyCard = emergencyHealthCardRepository.getEmergencyHealthCard(userId)
            val qualitySummary = healthDataQualityRepository.evaluateDataQuality()

            val bundle = PortableHealthDataMapper.mapToBundle(
                userId = userId,
                scope = scope,
                profile = profile,
                medications = medications,
                medicationHistory = medicationHistory,
                adherenceStats = adherenceStats,
                appointments = appointments,
                predictions = predictions,
                longitudinal = longitudinal,
                personalContext = personalContext,
                riskAssessment = risk,
                guidance = guidance,
                rchr = rchr,
                decisionTraces = decisionTraces,
                timelineEvents = timelineEvents,
                emergencyCard = emergencyCard,
                qualitySummary = qualitySummary
            )

            val exportFile = PortableHealthRecordSerializer.writeBundleToFile(context, bundle)
            val summary = PortableHealthRecordSerializer.generatePreviewSummary(bundle)

            // Record export completed audit event
            securityAuditRepository.recordEvent(
                eventType = SecurityAuditEventType.HEALTH_DATA_EXPORT_COMPLETED,
                customDescription = "Portable health data bundle ${bundle.meta.packageId} exported (${bundle.meta.totalResourceCount} resources)"
            )

            Result.success(
                PortableExportResult.Success(
                    file = exportFile,
                    resourceCount = bundle.meta.totalResourceCount,
                    sha256Fingerprint = bundle.meta.sha256Fingerprint,
                    packageId = bundle.meta.packageId,
                    previewSummary = summary
                )
            )
        } catch (e: Exception) {
            SecureLogger.e(TAG, "Failed to export portable health record bundle", e)
            Result.failure(e)
        }
    }

    /**
     * Records a local security audit log entry when an export is shared via system intent.
     */
    suspend fun recordExportShared(packageId: String) = withContext(Dispatchers.IO) {
        try {
            securityAuditRepository.recordEvent(
                eventType = SecurityAuditEventType.HEALTH_DATA_EXPORT_SHARED,
                customDescription = "Portable health record $packageId shared via system chooser"
            )
        } catch (e: Exception) {
            SecureLogger.e(TAG, "Failed to log export shared event", e)
        }
    }

    /**
     * Records a local security audit log entry when the user cancels the export workflow.
     */
    suspend fun recordExportCancelled(reason: String) = withContext(Dispatchers.IO) {
        try {
            securityAuditRepository.recordEvent(
                eventType = SecurityAuditEventType.HEALTH_DATA_EXPORT_CANCELLED,
                customDescription = "Portable health data export cancelled: $reason"
            )
        } catch (e: Exception) {
            SecureLogger.e(TAG, "Failed to log export cancelled event", e)
        }
    }

    companion object {
        private const val TAG = "PortableHealthDataRepo"
    }
}
