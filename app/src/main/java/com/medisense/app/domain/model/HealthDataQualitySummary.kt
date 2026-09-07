package com.medisense.app.domain.model

/**
 * Summary evaluation of data quality and consistency across all user health records.
 */
data class HealthDataQualitySummary(
    val status: HealthDataQualityStatus,
    val qualityScore: Int?, // 0..100 percentage of passed checks, or null if INSUFFICIENT_DATA
    val totalChecks: Int,
    val passedChecks: Int,
    val errorCount: Int,
    val warningCount: Int,
    val infoCount: Int,
    val issues: List<HealthDataQualityIssue>,
    val categoryBreakdown: Map<HealthDataQualityCategory, Int> = emptyMap(),
    val evaluatedTimestamp: Long = System.currentTimeMillis()
)
