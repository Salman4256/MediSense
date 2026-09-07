package com.medisense.app.domain.model

/**
 * Severity level of an application data quality check.
 * Note: These are DATA QUALITY indicators, NOT clinical/medical risk severities.
 */
enum class HealthDataQualitySeverity {
    INFO,
    WARNING,
    ERROR
}
