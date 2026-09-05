package com.medisense.app.domain.risk

/**
 * Centralized, fully-documented configuration and constants for the Context-Aware Health Risk Engine.
 * Ensures zero magic numbers and 100% deterministic calculation.
 */
object ContextualRiskConfiguration {

    // Minimum data categories required for score calculation (e.g. at least 2 distinct data sources)
    const val MIN_DATA_POINTS_FOR_SUFFICIENCY = 2

    // Score Range
    const val SCORE_MIN = 0
    const val SCORE_MAX = 100

    // Thresholds for application-defined contextual priority levels
    const val THRESHOLD_LOW_MAX = 29          // 0..29 -> LOW
    const val THRESHOLD_MODERATE_MAX = 59     // 30..59 -> MODERATE
    const val THRESHOLD_HIGH_MIN = 60         // 60..100 -> HIGH

    // Factor Weights (Weights sum to 1.0 when all are available)
    const val WEIGHT_SYMPTOM_RECURRENCE = 0.22f      // 22%
    const val WEIGHT_PREDICTION_ACTIVITY = 0.18f     // 18%
    const val WEIGHT_CHRONIC_ALLERGY = 0.13f         // 13%
    const val WEIGHT_MEDICATION_ADHERENCE = 0.15f    // 15%
    const val WEIGHT_CONFIDENCE_DYNAMICS = 0.12f     // 12%
    const val WEIGHT_APPOINTMENT_CONTEXT = 0.10f     // 10%
    const val WEIGHT_TEMPORAL_PATTERNS = 0.10f       // 10%

    // Factor Identifiers
    const val ID_SYMPTOM_RECURRENCE = "factor_symptom_recurrence"
    const val ID_PREDICTION_ACTIVITY = "factor_prediction_activity"
    const val ID_CHRONIC_ALLERGY = "factor_chronic_allergy"
    const val ID_MEDICATION_ADHERENCE = "factor_medication_adherence"
    const val ID_CONFIDENCE_DYNAMICS = "factor_confidence_dynamics"
    const val ID_APPOINTMENT_CONTEXT = "factor_appointment_context"
    const val ID_TEMPORAL_PATTERNS = "factor_temporal_patterns"

    // Source labels
    const val SOURCE_MODULE_9B = "Longitudinal Health Trends (Module 9B)"
    const val SOURCE_MODULE_9A = "Personal Health Context (Module 9A)"
    const val SOURCE_MODULE_10 = "Composite Health Representation (Module 10)"
    const val SOURCE_MODULE_2 = "Personal Health Records (Module 2)"
    const val SOURCE_MODULE_6 = "Medication Schedule & Adherence (Module 6)"
    const val SOURCE_MODULE_7 = "Medical Appointments (Module 7)"
    const val SOURCE_MODULE_8 = "Prediction History Archive (Module 8)"
}
