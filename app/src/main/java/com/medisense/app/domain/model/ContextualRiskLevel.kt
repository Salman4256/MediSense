package com.medisense.app.domain.model

/**
 * Application-defined contextual priority level.
 * NOTE: These are non-clinical, contextual priority indicators and are not medical diagnoses.
 */
enum class ContextualRiskLevel(val label: String, val description: String) {
    LOW(
        label = "Low Priority",
        description = "Few active contextual health indicators detected in available records."
    ),
    MODERATE(
        label = "Moderate Priority",
        description = "Notable health patterns or activity trends recorded across recent checks."
    ),
    HIGH(
        label = "High Priority",
        description = "Multiple concurrent contextual health signals detected in your health history."
    ),
    INSUFFICIENT_DATA(
        label = "Insufficient Data",
        description = "Not enough health history is available to calculate a meaningful contextual score."
    )
}
