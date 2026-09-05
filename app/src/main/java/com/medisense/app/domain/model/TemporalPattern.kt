package com.medisense.app.domain.model

enum class PatternSeverity {
    INFO,
    POSITIVE,
    ATTENTION
}

enum class PatternCategory {
    SYMPTOMS,
    PREDICTIONS,
    MEDICATION,
    APPOINTMENTS,
    GENERAL
}

/**
 * Represents a deterministic, explainable pattern detected across longitudinal health data.
 */
data class TemporalPattern(
    val id: String,
    val title: String,
    val description: String,
    val category: PatternCategory,
    val severity: PatternSeverity,
    val firstObservedDate: Long? = null,
    val lastObservedDate: Long? = null,
    val observationCount: Int = 1
)
