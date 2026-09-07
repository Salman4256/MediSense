package com.medisense.app.domain.model

/**
 * High-level data quality status for the user's stored health data.
 */
enum class HealthDataQualityStatus {
    GOOD,
    NEEDS_ATTENTION,
    INSUFFICIENT_DATA
}
