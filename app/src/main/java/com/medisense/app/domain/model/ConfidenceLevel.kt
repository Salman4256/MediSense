package com.medisense.app.domain.model

import java.io.Serializable

/**
 * Standardized application-defined confidence tier for disease prediction outputs.
 *
 * NOTE: This is an algorithmic confidence indicator reflecting how strongly the model
 * output favors a specific classification. It is NOT a clinical certainty rating.
 */
enum class ConfidenceLevel(val label: String) : Serializable {
    HIGH("High Confidence"),
    MODERATE("Moderate Confidence"),
    LOW("Low Confidence"),
    INSUFFICIENT_DATA("Insufficient Data")
}
