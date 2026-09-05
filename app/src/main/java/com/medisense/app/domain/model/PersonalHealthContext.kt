package com.medisense.app.domain.model

/**
 * Structured, human-readable representation of a user's unified health context.
 * Combines health records (PHR), prediction history, medication adherence, and scheduled appointments.
 */
data class PersonalHealthContext(
    val userId: String,
    val demographics: DemographicContext,
    val chronicConditions: ChronicConditionContext,
    val allergies: AllergyContext,
    val medications: MedicationContext,
    val predictions: PredictionContext,
    val appointments: AppointmentContext,
    val profileCompleteness: Int, // 0 to 100%
    val personalizationScore: Float, // 0.0 to 100.0 transparent relevance/engagement index
    val generatedSummary: String,
    val whyPersonalized: String = "Based on your recent health records, prediction history, medication adherence, and scheduled appointments.",
    val hasSufficientData: Boolean
)

data class DemographicContext(
    val age: Int? = null,
    val dateOfBirth: String? = null,
    val gender: String? = null,
    val bloodGroup: String? = null,
    val heightCm: Double? = null,
    val weightKg: Double? = null,
    val bmi: Double? = null
)

data class ChronicConditionContext(
    val hasChronicConditions: Boolean = false,
    val conditions: List<String> = emptyList()
)

data class AllergyContext(
    val hasAllergies: Boolean = false,
    val allergiesList: List<String> = emptyList()
)

data class MedicationContext(
    val activeCount: Int = 0,
    val activeNames: List<String> = emptyList(),
    val adherencePercentage: Float? = null,
    val adherenceSummary: String? = null
)

data class PredictionContext(
    val totalCount: Int = 0,
    val recentCount: Int = 0,
    val frequentSymptoms: List<String> = emptyList(),
    val frequentDiseases: List<String> = emptyList(),
    val avgConfidence: Float? = null,
    val latestPrediction: String? = null
)

data class AppointmentContext(
    val upcomingCount: Int = 0,
    val nextAppointmentDate: String? = null,
    val nextAppointmentDoctor: String? = null,
    val nextAppointmentType: String? = null
)
