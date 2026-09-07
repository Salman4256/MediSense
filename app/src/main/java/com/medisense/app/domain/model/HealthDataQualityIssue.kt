package com.medisense.app.domain.model

/**
 * Represents a single detected health data quality, completeness, or consistency issue.
 */
data class HealthDataQualityIssue(
    val id: String,
    val category: HealthDataQualityCategory,
    val severity: HealthDataQualitySeverity,
    val title: String,
    val explanation: String,
    val affectedRecordType: String,
    val recordId: String? = null,
    val suggestedCorrection: String,
    val navigationDestinationId: Int? = null
)
