package com.medisense.app.domain.quality

import com.medisense.app.domain.model.HealthDataQualityIssue

/**
 * Result returned by an individual domain data validator.
 */
data class ValidationResult(
    val totalChecks: Int,
    val passedChecks: Int,
    val issues: List<HealthDataQualityIssue>
)
