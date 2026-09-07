package com.medisense.app.data.repository

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.medisense.app.data.local.dao.*
import com.medisense.app.data.remote.supabase.AuthService
import com.medisense.app.domain.consultation.ConsultationPdfGenerator
import com.medisense.app.domain.consultation.ConsultationSummaryGenerator
import com.medisense.app.domain.model.*
import com.medisense.app.domain.security.SecureLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository orchestrating Smart Consultation Preparation & Doctor Visit Summaries.
 * Aggregates information deterministically across Modules 1–18 with strict user isolation.
 */
@Singleton
class ConsultationPreparationRepository @Inject constructor(
    private val authService: AuthService,
    private val healthProfileDao: HealthProfileDao,
    private val medicationDao: MedicationDao,
    private val medicationHistoryDao: MedicationHistoryDao,
    private val appointmentDao: AppointmentDao,
    private val predictionHistoryDao: PredictionHistoryDao,
    private val personalHealthContextRepository: PersonalHealthContextRepository,
    private val longitudinalHealthRepository: LongitudinalHealthRepository,
    private val contextualRiskRepository: ContextualRiskRepository,
    private val personalizedGuidanceRepository: PersonalizedGuidanceRepository,
    private val healthDataQualityRepository: HealthDataQualityRepository,
    private val securityAuditRepository: SecurityAuditRepository
) {

    private val TAG = "ConsultationPrepRepo"

    fun getCurrentUserId(): String? {
        return authService.getCurrentUserId()
    }

    /**
     * Deterministically compiles a Consultation Summary for the authenticated user based on selected time window.
     */
    suspend fun generateConsultationSummary(
        period: ConsultationPeriod = ConsultationPeriod.LAST_30_DAYS,
        customQuestions: List<ConsultationQuestion> = emptyList()
    ): ConsultationSummary = withContext(Dispatchers.IO) {
        val userId = authService.getCurrentUserId() ?: "offline-user"

        // 1. Fetch user-scoped Room data
        val profile = healthProfileDao.getHealthProfile(userId)
        val predictions = predictionHistoryDao.getAllPredictionHistoryForUserSync(userId)
        val medications = medicationDao.getAllMedicationsForUserSync(userId)
        val medicationHistory = medicationHistoryDao.getAllHistoryForUserSync(userId)
        val appointments = appointmentDao.getAllAppointmentsForUserSync(userId)

        // 2. Fetch domain representations
        val dataQuality = try {
            healthDataQualityRepository.evaluateDataQuality()
        } catch (e: Exception) {
            SecureLogger.e(TAG, "Data quality evaluation skipped in consultation prep", e)
            null
        }

        val personalContext = personalHealthContextRepository.observePersonalHealthContext().firstOrNull()
        val longitudinalSummary = longitudinalHealthRepository.observeLongitudinalSummary(
            when (period) {
                ConsultationPeriod.LAST_7_DAYS -> AnalysisPeriod.DAYS_7
                ConsultationPeriod.LAST_30_DAYS -> AnalysisPeriod.DAYS_30
                ConsultationPeriod.LAST_90_DAYS,
                ConsultationPeriod.ALL_HISTORY -> AnalysisPeriod.DAYS_90
            }
        ).firstOrNull()
        val riskAssessment = contextualRiskRepository.observeContextualRiskAssessment().firstOrNull()
        val guidanceResult = personalizedGuidanceRepository.observePersonalizedGuidance().firstOrNull()

        // 3. Generate structured consultation summary
        val summary = ConsultationSummaryGenerator.generate(
            userId = userId,
            profile = profile,
            predictions = predictions,
            medications = medications,
            medicationHistory = medicationHistory,
            appointments = appointments,
            longitudinalSummary = longitudinalSummary,
            personalContext = personalContext,
            riskAssessment = riskAssessment,
            guidanceResult = guidanceResult,
            dataQualitySummary = dataQuality,
            period = period,
            customQuestions = customQuestions,
            currentTime = System.currentTimeMillis()
        )

        // 4. Record privacy-safe audit telemetry
        try {
            securityAuditRepository.recordEvent(
                eventType = SecurityAuditEventType.CONSULTATION_SUMMARY_GENERATED,
                customDescription = "Consultation summary prepared (${period.displayName}, questions=${summary.suggestedQuestions.size + summary.customQuestions.size})"
            )
        } catch (e: Exception) {
            SecureLogger.e(TAG, "Failed to record audit event for consultation summary", e)
        }

        SecureLogger.d(TAG, "Generated consultation summary for user: $userId (period: ${period.displayName})")
        summary
    }

    /**
     * Converts a [ConsultationSummary] into a formatted, clinical-visit friendly plain-text document for quick sharing.
     */
    fun formatSummaryAsPlainText(summary: ConsultationSummary): String {
        return buildString {
            appendLine("========================================")
            appendLine("MEDISENSE DOCTOR CONSULTATION SUMMARY")
            appendLine("========================================")
            appendLine("Period: ${summary.visitOverview.dataPeriodLabel}")
            appendLine("Status: ${summary.visitOverview.completenessStatus.label}")
            appendLine()

            appendLine("1. PATIENT PROFILE")
            appendLine("• Name: ${summary.profile.fullName}")
            appendLine("• DOB / Age: ${summary.profile.ageOrDob}")
            appendLine("• Gender: ${summary.profile.gender} | Blood Group: ${summary.profile.bloodGroup}")
            appendLine("• Known Allergies: ${summary.profile.allergies}")
            appendLine("• Existing Conditions: ${summary.profile.existingConditions}")
            appendLine()

            appendLine("2. RECENT SYMPTOMS & OBSERVATIONS")
            if (summary.symptoms.frequentSymptoms.isNotEmpty()) {
                appendLine("• Recorded Symptoms: ${summary.symptoms.frequentSymptoms.joinToString(", ")}")
            } else {
                appendLine("• No specific symptoms recorded in this period.")
            }
            appendLine()

            appendLine("3. RECORDED PREDICTIONS (AI Model Estimations)")
            if (summary.predictions.recentPredictions.isNotEmpty()) {
                summary.predictions.recentPredictions.take(4).forEach { pred ->
                    appendLine("• ${pred.predictionDate}: ${pred.predictedCondition} (${pred.confidencePercentage}% model confidence) based on symptoms: ${pred.reportedSymptoms.joinToString(", ")}")
                }
                appendLine("  *Note: Model indications are educational and do not represent confirmed medical diagnoses.")
            } else {
                appendLine("• No prediction sessions recorded in this period.")
            }
            appendLine()

            appendLine("4. CURRENT MEDICATIONS & ADHERENCE")
            if (summary.medications.activeMedications.isNotEmpty()) {
                summary.medications.activeMedications.forEach { med ->
                    appendLine("• ${med.name} (${med.dosage}) - ${med.frequency} [${med.instructions}]")
                }
                appendLine("• Adherence: ${summary.medications.adherenceSummary}")
            } else {
                appendLine("• No active medications recorded in this period.")
            }
            appendLine()

            if (summary.appointments.upcomingAppointmentDoctor != null) {
                appendLine("5. APPOINTMENT INFORMATION")
                appendLine("• Upcoming Visit: ${summary.appointments.upcomingAppointmentDoctor} on ${summary.appointments.upcomingAppointmentDate}")
                if (summary.appointments.upcomingAppointmentClinic != null) {
                    appendLine("• Location: ${summary.appointments.upcomingAppointmentClinic}")
                }
                appendLine()
            }

            appendLine("6. QUESTIONS TO DISCUSS WITH YOUR DOCTOR")
            val allQuestions = (summary.suggestedQuestions.filter { it.isSelected } + summary.customQuestions.filter { it.isSelected })
            if (allQuestions.isNotEmpty()) {
                allQuestions.forEachIndexed { idx, q ->
                    appendLine("${idx + 1}. ${q.questionText}")
                    if (q.rationale.isNotBlank() && !q.isCustom) {
                        appendLine("   (Context: ${q.rationale})")
                    }
                }
            } else {
                appendLine("• What routine health screenings or lifestyle habits are recommended for my profile?")
            }
            appendLine()

            appendLine("----------------------------------------")
            appendLine("DISCLAIMER: ${summary.safetyDisclaimer}")
            appendLine("========================================")
        }
    }

    /**
     * Exports a consultation preparation summary as a multi-page PDF with a secure FileProvider URI.
     */
    suspend fun exportSummaryPdf(context: Context, summary: ConsultationSummary): HealthReportExportResult = withContext(Dispatchers.IO) {
        try {
            val genResult = ConsultationPdfGenerator.generatePdf(context, summary)
            if (genResult is HealthReportExportResult.Success) {
                val contentUri: Uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    genResult.file
                )

                try {
                    securityAuditRepository.recordEvent(
                        eventType = SecurityAuditEventType.CONSULTATION_SUMMARY_EXPORTED,
                        customDescription = "Consultation summary exported as PDF document"
                    )
                } catch (e: Exception) {
                    SecureLogger.e(TAG, "Failed to record audit event for consultation export", e)
                }

                HealthReportExportResult.Success(file = genResult.file, contentUri = contentUri)
            } else {
                genResult
            }
        } catch (e: Exception) {
            SecureLogger.e(TAG, "Failed to export consultation summary PDF", e)
            HealthReportExportResult.Error("Failed to export consultation summary PDF: ${e.message}", e)
        }
    }

    /**
     * Exports a consultation summary text file to a local cache file with a FileProvider URI for sharing.
     */
    suspend fun exportSummaryTextFile(context: Context, summary: ConsultationSummary): HealthReportExportResult = withContext(Dispatchers.IO) {
        try {
            val textContent = formatSummaryAsPlainText(summary)
            val reportsDir = File(context.cacheDir, "consultations").apply {
                if (!exists()) mkdirs()
            }
            val timestamp = System.currentTimeMillis()
            val outputFile = File(reportsDir, "medisense_consultation_summary_$timestamp.txt")

            FileOutputStream(outputFile).use { out ->
                out.write(textContent.toByteArray(Charsets.UTF_8))
            }

            val contentUri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                outputFile
            )

            try {
                securityAuditRepository.recordEvent(
                    eventType = SecurityAuditEventType.CONSULTATION_SUMMARY_EXPORTED,
                    customDescription = "Consultation summary exported as document"
                )
            } catch (e: Exception) {
                SecureLogger.e(TAG, "Failed to record audit event for consultation export", e)
            }

            HealthReportExportResult.Success(file = outputFile, contentUri = contentUri)
        } catch (e: Exception) {
            SecureLogger.e(TAG, "Failed to export consultation summary", e)
            HealthReportExportResult.Error("Failed to export consultation summary: ${e.message}", e)
        }
    }

    /**
     * Records audit telemetry when a summary is shared via system intent.
     */
    suspend fun recordSummarySharedAudit() = withContext(Dispatchers.IO) {
        try {
            securityAuditRepository.recordEvent(
                eventType = SecurityAuditEventType.CONSULTATION_SUMMARY_SHARED,
                customDescription = "Consultation summary shared via system intent"
            )
        } catch (e: Exception) {
            SecureLogger.e(TAG, "Failed to record audit event for consultation share", e)
        }
    }
}
