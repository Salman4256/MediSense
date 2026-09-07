package com.medisense.app.domain.model

/**
 * Categorization of health data quality issues.
 */
enum class HealthDataQualityCategory {
    PROFILE_COMPLETENESS,
    PROFILE_VALIDITY,
    MEDICATION_DATA,
    MEDICATION_HISTORY,
    APPOINTMENT_DATA,
    PREDICTION_HISTORY,
    TEMPORAL_CONSISTENCY,
    CROSS_RECORD_CONSISTENCY,
    SYNC_READINESS
}
