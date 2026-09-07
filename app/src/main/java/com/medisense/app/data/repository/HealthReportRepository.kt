package com.medisense.app.data.repository

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.medisense.app.data.local.dao.*
import com.medisense.app.data.remote.supabase.AuthService
import com.medisense.app.domain.model.AnalysisPeriod
import com.medisense.app.domain.model.HealthReport
import com.medisense.app.domain.model.HealthReportExportResult
import com.medisense.app.domain.model.SecurityAuditEventType
import com.medisense.app.domain.report.HealthReportGenerator
import com.medisense.app.domain.report.HealthReportPdfGenerator
import com.medisense.app.domain.security.SecureLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Central repository for generating unified personal health reports, exporting local PDFs,
 * and handling privacy-safe file sharing URI generation.
 */
@Singleton
class HealthReportRepository @Inject constructor(
    private val authService: AuthService,
    private val healthProfileDao: HealthProfileDao,
    private val medicationDao: MedicationDao,
    private val medicationHistoryDao: MedicationHistoryDao,
    private val appointmentDao: AppointmentDao,
    private val predictionHistoryDao: PredictionHistoryDao,
    private val syncMetadataDao: SyncMetadataDao,
    private val personalHealthContextRepository: PersonalHealthContextRepository,
    private val longitudinalHealthRepository: LongitudinalHealthRepository,
    private val rchrRepository: RchrRepository,
    private val contextualRiskRepository: ContextualRiskRepository,
    private val personalizedGuidanceRepository: PersonalizedGuidanceRepository,
    private val healthDataQualityRepository: HealthDataQualityRepository,
    private val securityAuditRepository: SecurityAuditRepository
) {

    fun getCurrentUserId(): String? {
        return authService.getCurrentUserId()
    }

    /**
     * Deterministically generates a unified health report for the currently authenticated user.
     * Guaranteed 100% offline-first execution from local Room tables and existing module engines.
     */
    suspend fun generateHealthReport(): HealthReport = withContext(Dispatchers.IO) {
        val userId = authService.getCurrentUserId() ?: "offline-user"

        // 1. Fetch user-scoped raw data from Room
        val profile = healthProfileDao.getHealthProfile(userId)
        val medications = medicationDao.getAllMedicationsForUserSync(userId)
        val medicationHistory = medicationHistoryDao.getAllHistoryForUserSync(userId)
        val appointments = appointmentDao.getAllAppointmentsForUserSync(userId)
        val predictions = predictionHistoryDao.getAllPredictionHistoryForUserSync(userId)
        val syncMetadata = syncMetadataDao.getSyncMetadata(userId)

        // 2. Fetch computed domain outputs from existing module repositories
        val dataQuality = healthDataQualityRepository.evaluateDataQuality()
        val personalContext = personalHealthContextRepository.observePersonalHealthContext().first()
        val longitudinalSummary = longitudinalHealthRepository.observeLongitudinalSummary(AnalysisPeriod.DAYS_30).first()
        val rchr = rchrRepository.observeRchrRepresentation().first()
        val riskAssessment = contextualRiskRepository.observeContextualRiskAssessment().first()
        val guidanceResult = personalizedGuidanceRepository.observePersonalizedGuidance().first()

        // 3. Assemble unified HealthReport
        val report = HealthReportGenerator.generate(
            profile = profile,
            medications = medications,
            medicationHistory = medicationHistory,
            appointments = appointments,
            predictions = predictions,
            syncMetadata = syncMetadata,
            dataQuality = dataQuality,
            personalContext = personalContext,
            longitudinalSummary = longitudinalSummary,
            rchr = rchr,
            riskAssessment = riskAssessment,
            guidanceResult = guidanceResult
        )

        // 4. Record privacy-safe telemetry event in Module 14 audit log
        try {
            securityAuditRepository.recordEvent(
                eventType = SecurityAuditEventType.HEALTH_REPORT_GENERATED,
                customDescription = "Personal health report generated (predictions=${report.predictions.totalPredictions}, meds=${report.medications.activeMedicationsCount})"
            )
        } catch (e: Exception) {
            SecureLogger.e(TAG, "Failed to record audit event for report generation", e)
        }

        SecureLogger.d(TAG, "Generated health report for user: $userId (timestamp: ${report.metadata.generatedTimestamp})")
        report
    }

    /**
     * Exports a [HealthReport] to a local PDF file and returns the secure FileProvider [Uri].
     */
    suspend fun exportPdf(context: Context, report: HealthReport): HealthReportExportResult = withContext(Dispatchers.IO) {
        val exportResult = HealthReportPdfGenerator.generatePdf(context, report)

        if (exportResult is HealthReportExportResult.Success) {
            val file = exportResult.file
            val contentUri: Uri = try {
                FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )
            } catch (e: Exception) {
                SecureLogger.e(TAG, "Failed to create FileProvider URI for report PDF", e)
                return@withContext HealthReportExportResult.Error("Failed to create secure file URI: ${e.message}", e)
            }

            // Record audit telemetry
            try {
                securityAuditRepository.recordEvent(
                    eventType = SecurityAuditEventType.HEALTH_REPORT_EXPORTED,
                    customDescription = "Health report PDF exported locally"
                )
            } catch (e: Exception) {
                SecureLogger.e(TAG, "Failed to record audit event for PDF export", e)
            }

            HealthReportExportResult.Success(file = file, contentUri = contentUri)
        } else {
            exportResult
        }
    }

    /**
     * Records a privacy-safe audit event when the user completes a report share action.
     */
    suspend fun recordReportSharedAudit(): Unit = withContext(Dispatchers.IO) {
        try {
            securityAuditRepository.recordEvent(
                eventType = SecurityAuditEventType.HEALTH_REPORT_SHARED,
                customDescription = "Health report PDF shared via secure system intent"
            )
        } catch (e: Exception) {
            SecureLogger.e(TAG, "Failed to record audit event for report sharing", e)
        }
    }

    companion object {
        private const val TAG = "HealthReportRepository"
    }
}
