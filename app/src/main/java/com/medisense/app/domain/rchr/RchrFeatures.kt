package com.medisense.app.domain.rchr

import com.medisense.app.domain.model.TrendDirection

/**
 * Deterministic, explainable profile features encoded into RCHR.
 */
data class RchrProfileFeatures(
    val age: Int?,
    val ageGroup: String?, // "CHILD", "YOUNG_ADULT", "ADULT", "MIDDLE_AGED", "SENIOR", "UNKNOWN"
    val gender: String?,
    val bloodGroup: String?,
    val heightCm: Double?,
    val weightKg: Double?,
    val bmi: Double?,
    val bmiCategory: String?, // "UNDERWEIGHT", "NORMAL", "OVERWEIGHT", "OBESE", "UNKNOWN"
    val allergyCount: Int,
    val allergyList: List<String>,
    val chronicConditionCount: Int,
    val chronicConditionList: List<String>,
    val profileCompletenessPercent: Int
)

/**
 * Deterministic symptom recurrence and diversity features.
 */
data class RchrSymptomFeatures(
    val distinctSymptomCount: Int,
    val allRecordedSymptoms: List<String>,
    val frequentSymptoms: List<String>,
    val recurringSymptoms: List<String>,
    val recentSymptomCount: Int
)

/**
 * Encoded disease prediction history statistics and confidence outputs.
 */
data class RchrPredictionFeatures(
    val totalPredictionCount: Int,
    val recentPredictionCount: Int,
    val dominantPredictedDisease: String?,
    val topPredictedDiseases: List<String>,
    val averageConfidence: Float?,
    val confidenceRangeMin: Float?,
    val confidenceRangeMax: Float?,
    val confidenceTrendDirection: TrendDirection
)

/**
 * Encoded medication regimen features.
 */
data class RchrMedicationFeatures(
    val activeMedicationCount: Int,
    val activeMedicationNames: List<String>,
    val totalPrescribedMedicationCount: Int,
    val hasActiveMedications: Boolean
)

/**
 * Encoded medication adherence statistics.
 */
data class RchrAdherenceFeatures(
    val recordedDoseCount: Int,
    val takenCount: Int,
    val missedCount: Int,
    val skippedCount: Int,
    val adherencePercentage: Float?,
    val adherenceCategory: String?, // "OPTIMAL", "MODERATE", "SUBOPTIMAL", "INSUFFICIENT_DATA"
    val adherenceTrendDirection: TrendDirection
)

/**
 * Encoded doctor appointment engagement features.
 */
data class RchrAppointmentFeatures(
    val upcomingAppointmentCount: Int,
    val nextAppointmentDoctor: String?,
    val nextAppointmentDate: String?,
    val nextAppointmentType: String?,
    val pastAppointmentCount: Int,
    val appointmentTrendDirection: TrendDirection
)

/**
 * Encoded longitudinal and temporal health dynamics (from Module 9B).
 */
data class RchrTemporalFeatures(
    val analysisWindowDays: Int,
    val predictionActivityTrend: TrendDirection,
    val predictionChangePercent: Float?,
    val confidenceTrend: TrendDirection,
    val adherenceTrend: TrendDirection,
    val appointmentTrend: TrendDirection,
    val detectedPatternsCount: Int,
    val detectedPatternTitles: List<String>
)

/**
 * Encoded adaptive personal health context features (from Module 9A).
 */
data class RchrContextFeatures(
    val personalizationScore: Float,
    val contextCompleteness: Int,
    val contextSummary: String,
    val whyPersonalized: String
)

/**
 * Unified Reversible Composite Health Representation (RCHR).
 * Versioned, deterministic, and 100% inspectable.
 */
data class RchrRepresentation(
    val userId: String,
    val representationVersion: String = "1.0",
    val generatedAt: Long = System.currentTimeMillis(),
    val totalEncodedFeatures: Int,
    val completenessPercentage: Int,
    val profileFeatures: RchrProfileFeatures,
    val symptomFeatures: RchrSymptomFeatures,
    val predictionFeatures: RchrPredictionFeatures,
    val medicationFeatures: RchrMedicationFeatures,
    val adherenceFeatures: RchrAdherenceFeatures,
    val appointmentFeatures: RchrAppointmentFeatures,
    val temporalFeatures: RchrTemporalFeatures,
    val contextFeatures: RchrContextFeatures,
    val hasSufficientData: Boolean
)
