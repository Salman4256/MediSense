package com.medisense.app.domain.model

import java.io.Serializable

/**
 * Transparent, explainable summary of the algorithmic confidence of a prediction output.
 *
 * @param topPrediction Primary disease predicted by LiteRT model
 * @param confidenceValue Raw normalized probability (0.0 to 1.0)
 * @param confidencePercentage Integer percentage (0 to 100%)
 * @param confidenceLevel Categorized confidence tier (LOW, MODERATE, HIGH, INSUFFICIENT_DATA)
 * @param interpretation Human-readable explanation of what this model confidence represents
 * @param disclaimer Mandatory medical disclaimer distinguishing model confidence from clinical certainty
 */
data class PredictionConfidenceSummary(
    val topPrediction: String,
    val confidenceValue: Float,
    val confidencePercentage: Int,
    val confidenceLevel: ConfidenceLevel,
    val interpretation: String,
    val disclaimer: String
) : Serializable
