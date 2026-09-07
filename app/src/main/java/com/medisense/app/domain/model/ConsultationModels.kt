package com.medisense.app.domain.model

/**
 * Supported time window filters for consultation summary generation.
 */
enum class ConsultationPeriod(val displayName: String, val dayCount: Int?) {
    LAST_7_DAYS("Last 7 Days", 7),
    LAST_30_DAYS("Last 30 Days", 30),
    LAST_90_DAYS("Last 90 Days", 90),
    ALL_HISTORY("All Available History", null)
}

/**
 * Data completeness indicator for consultation readiness.
 */
enum class ConsultationDataCompleteness(val label: String) {
    GOOD_DATA("Complete Records Available"),
    PARTIAL_DATA("Partial Records Available"),
    INSUFFICIENT_DATA("Limited Health History")
}

/**
 * Category attribution for smart question suggestions.
 */
enum class ConsultationQuestionSource(val displayName: String) {
    SYMPTOM_PATTERN("Symptom Pattern"),
    PREDICTION_HISTORY("Prediction History"),
    MEDICATION_ADHERENCE("Medication & Adherence"),
    APPOINTMENT_TOPIC("Appointment Topic"),
    DATA_QUALITY("Data Completeness"),
    PROFILE_GAP("Health Profile"),
    USER_CUSTOM("My Custom Question")
}

/**
 * Individual discussion prompt or question to discuss with a healthcare professional.
 */
data class ConsultationQuestion(
    val id: String,
    val questionText: String,
    val category: ConsultationQuestionSource,
    val rationale: String,
    val isCustom: Boolean = false,
    val isSelected: Boolean = true
)

/**
 * High-level visit overview and period metadata.
 */
data class ConsultationVisitOverview(
    val generationTimestamp: Long = System.currentTimeMillis(),
    val period: ConsultationPeriod,
    val dataPeriodLabel: String,
    val totalRecordsCount: Int,
    val completenessStatus: ConsultationDataCompleteness,
    val statusMessage: String
)

/**
 * Demographic and clinical baseline summary for consultation.
 */
data class ConsultationProfileSummary(
    val fullName: String?,
    val ageOrDob: String?,
    val gender: String?,
    val bloodGroup: String?,
    val allergies: String?,
    val existingConditions: String?,
    val currentMedications: String?,
    val notes: String?,
    val isProfileComplete: Boolean
)

/**
 * Recurrent symptoms and recent observations.
 */
data class ConsultationSymptomsSummary(
    val totalSymptomsCount: Int,
    val frequentSymptoms: List<String>,
    val recentObservations: List<Pair<String, String>> // Symptom -> Date
)

/**
 * Individual historical model prediction record.
 */
data class ConsultationPredictionItem(
    val predictedCondition: String,
    val confidencePercentage: Int,
    val predictionDate: String,
    val reportedSymptoms: List<String>,
    val modelVersion: String
)

/**
 * Consolidated model predictions section.
 */
data class ConsultationPredictionsSummary(
    val totalPredictionsCount: Int,
    val recentPredictions: List<ConsultationPredictionItem>,
    val mostFrequentCondition: String?
)

/**
 * Individual medication regimen item.
 */
data class ConsultationMedicationItem(
    val name: String,
    val dosage: String,
    val frequency: String,
    val instructions: String,
    val isActive: Boolean
)

/**
 * Medication regimen and adherence tracking section.
 */
data class ConsultationMedicationSummary(
    val activeMedications: List<ConsultationMedicationItem>,
    val adherenceSummary: String,
    val adherencePercentage: Int?,
    val missedOrSkippedCount: Int
)

/**
 * Relevant doctor appointments section.
 */
data class ConsultationAppointmentSummary(
    val upcomingAppointmentDoctor: String?,
    val upcomingAppointmentDate: String?,
    val upcomingAppointmentClinic: String?,
    val upcomingAppointmentType: String?,
    val recentPastAppointmentsCount: Int
)

/**
 * Longitudinal trends and temporal patterns.
 */
data class ConsultationTrendsSummary(
    val detectedPatterns: List<String>,
    val symptomTrend: String,
    val adherenceTrend: String
)

/**
 * Personal health context and priority indicators.
 */
data class ConsultationContextSummary(
    val personalContextSummary: String,
    val contextualPriorityNotice: String?
)

/**
 * Data quality, validation, and completeness notices.
 */
data class ConsultationDataQualitySummary(
    val dataQualityStatus: HealthDataQualityStatus,
    val notices: List<String>,
    val hasMissingData: Boolean
)

/**
 * Root domain model representing a complete, structured consultation preparation summary.
 */
data class ConsultationSummary(
    val visitOverview: ConsultationVisitOverview,
    val profile: ConsultationProfileSummary,
    val symptoms: ConsultationSymptomsSummary,
    val predictions: ConsultationPredictionsSummary,
    val medications: ConsultationMedicationSummary,
    val appointments: ConsultationAppointmentSummary,
    val trends: ConsultationTrendsSummary,
    val context: ConsultationContextSummary,
    val dataQuality: ConsultationDataQualitySummary,
    val suggestedQuestions: List<ConsultationQuestion>,
    val customQuestions: List<ConsultationQuestion>,
    val safetyDisclaimer: String = DEFAULT_SAFETY_DISCLAIMER
) {
    companion object {
        const val DEFAULT_SAFETY_DISCLAIMER =
            "Medical Safety Notice: Consultation preparation is based on information recorded in MediSense and is intended to help organize health information for discussion with a healthcare professional. It does not provide a medical diagnosis, clinical treatment plan, or medication instructions."
    }
}
