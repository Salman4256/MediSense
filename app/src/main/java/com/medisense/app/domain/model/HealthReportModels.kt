package com.medisense.app.domain.model

import java.io.File
import android.net.Uri

/**
 * Root domain model representing a comprehensive, unified personal health report.
 * Aggregates information deterministically across Modules 1–16 for the authenticated user.
 */
data class HealthReport(
    val metadata: HealthReportMetadata,
    val profile: HealthReportProfile,
    val dataQuality: HealthReportDataQuality,
    val predictions: HealthReportPredictionSummary,
    val medications: HealthReportMedicationSummary,
    val appointments: HealthReportAppointmentSummary,
    val trends: HealthReportTrendSummary,
    val context: HealthReportContextSummary,
    val rchr: HealthReportRchrSummary,
    val riskPriority: HealthReportRiskSummary,
    val guidance: HealthReportGuidanceSummary,
    val syncStatus: HealthReportSyncSummary,
    val safetyDisclaimer: String = DEFAULT_SAFETY_DISCLAIMER
) {
    companion object {
        const val DEFAULT_SAFETY_DISCLAIMER =
            "Medical Safety Notice: MediSense provides educational and health-management information based on the data available in the application. Model predictions, trends, contextual indicators, and personalized guidance are not medical diagnoses or treatment plans and should not replace advice from a qualified healthcare professional."
    }
}

/**
 * Metadata descriptor for the generated health report.
 * NOTE: Does NOT expose the authenticated Supabase Auth UUID to the user.
 */
data class HealthReportMetadata(
    val title: String = "MediSense Comprehensive Personal Health Report",
    val reportVersion: String = "1.0",
    val generatedTimestamp: Long = System.currentTimeMillis(),
    val appVersion: String = "1.0",
    val format: String = "In-App / PDF"
)

/**
 * Demographic and baseline clinical profile section of the report.
 */
data class HealthReportProfile(
    val fullName: String?,
    val dateOfBirth: String?,
    val gender: String?,
    val bloodGroup: String?,
    val heightCm: Double?,
    val weightKg: Double?,
    val bmi: Double?,
    val allergies: String?,
    val existingDiseases: String?,
    val currentMedications: String?,
    val familyHistory: String?,
    val emergencyContactName: String?,
    val emergencyContactNumber: String?,
    val notes: String?,
    val completenessPercentage: Int,
    val incompleteFields: List<String>
)

/**
 * Health data quality and consistency section (reusing Module 16).
 */
data class HealthReportDataQuality(
    val status: HealthDataQualityStatus,
    val qualityScore: Int?,
    val totalChecks: Int,
    val passedChecks: Int,
    val errorCount: Int,
    val warningCount: Int,
    val infoCount: Int,
    val unresolvedIssues: List<HealthDataQualityIssue>
)

/**
 * Disease prediction history section (reusing Module 8 & Module 13).
 */
data class HealthReportPredictionSummary(
    val totalPredictions: Int,
    val recentPredictions: List<HealthReportPredictionItem>,
    val mostFrequentCondition: String?,
    val averageConfidencePercentage: Int?
)

/**
 * Individual historical prediction record item for the report.
 */
data class HealthReportPredictionItem(
    val id: Long,
    val predictedDisease: String,
    val confidencePercentage: Int,
    val confidenceLevel: String,
    val predictionDate: String,
    val modelVersion: String,
    val symptoms: List<String>,
    val explanationSummary: String?,
    val uncertaintyInterpretation: String?
)

/**
 * Medication regimen and adherence tracking section (reusing Module 6).
 */
data class HealthReportMedicationSummary(
    val activeMedicationsCount: Int,
    val totalMedicationsCount: Int,
    val activeMedications: List<HealthReportMedicationItem>,
    val totalDosesLogged: Int,
    val takenDosesCount: Int,
    val skippedDosesCount: Int,
    val missedDosesCount: Int,
    val adherencePercentage: Int?,
    val adherenceRating: String
)

/**
 * Individual medication item details.
 */
data class HealthReportMedicationItem(
    val id: Long,
    val medicineName: String,
    val dosage: String,
    val frequency: String,
    val scheduledTimes: List<String>,
    val startDate: String,
    val endDate: String?,
    val instructions: String?,
    val isActive: Boolean
)

/**
 * Doctor appointments schedule section (reusing Module 7).
 */
data class HealthReportAppointmentSummary(
    val totalAppointments: Int,
    val upcomingAppointments: List<HealthReportAppointmentItem>,
    val pastAppointments: List<HealthReportAppointmentItem>
)

/**
 * Individual doctor appointment item details.
 */
data class HealthReportAppointmentItem(
    val id: Long,
    val doctorName: String,
    val clinicName: String,
    val appointmentType: String,
    val appointmentDate: String,
    val appointmentTime: String,
    val reminderMinutesBefore: Int,
    val status: String,
    val notes: String?
)

/**
 * Longitudinal health trends and temporal dynamics section (reusing Module 9B).
 */
data class HealthReportTrendSummary(
    val periodLabel: String,
    val predictionFrequencyTrend: String,
    val topRecurringSymptoms: List<String>,
    val confidenceTrend: String,
    val adherenceTrend: String,
    val appointmentActivity: String,
    val detectedPatterns: List<String>
)

/**
 * Personal health context summary (reusing Module 9A).
 */
data class HealthReportContextSummary(
    val profileCompleteness: Int,
    val demographicSummary: String,
    val chronicConditionsSummary: String,
    val allergySummary: String,
    val activeMedicationSummary: String,
    val adherenceContext: String,
    val upcomingAppointmentContext: String
)

/**
 * Reversible Composite Health Representation summary (reusing Module 10).
 */
data class HealthReportRchrSummary(
    val rchrVersion: String,
    val encodedCategories: List<String>,
    val activeFeatureGroupsCount: Int,
    val reconstructionConsistencyScore: Int?,
    val explanationNotice: String =
        "The reconstruction consistency score describes how consistently stored health-management information can be reconstructed from the structured representation. It is not a measure of medical accuracy."
)

/**
 * Contextual health risk priority summary (reusing Module 11).
 */
data class HealthReportRiskSummary(
    val priorityLevel: ContextualRiskLevel,
    val priorityScore: Int?,
    val hasSufficientData: Boolean,
    val contributingFactors: List<String>,
    val plainLanguageExplanation: String,
    val notice: String =
        "This is an application-defined health-management indicator based on stored records. It is not a clinically validated risk score, emergency detector, or diagnosis."
)

/**
 * Personalized guidance recommendations summary (reusing Module 12).
 */
data class HealthReportGuidanceSummary(
    val totalGuidanceCount: Int,
    val items: List<HealthReportGuidanceItem>
)

/**
 * Individual guidance recommendation item.
 */
data class HealthReportGuidanceItem(
    val category: GuidanceCategory,
    val priority: GuidancePriority,
    val title: String,
    val actionableGuidance: String,
    val clinicalReason: String,
    val sourceModule: String
)

/**
 * Cloud data synchronization status summary (reusing Module 15).
 */
data class HealthReportSyncSummary(
    val syncStatus: String,
    val lastSyncTimestamp: Long?,
    val pendingRecordsCount: Int,
    val isOfflineMode: Boolean
)

/**
 * Outcome descriptor for PDF generation / export.
 */
sealed class HealthReportExportResult {
    data class Success(val file: File, val contentUri: Uri) : HealthReportExportResult()
    data class Error(val message: String, val throwable: Throwable? = null) : HealthReportExportResult()
}
