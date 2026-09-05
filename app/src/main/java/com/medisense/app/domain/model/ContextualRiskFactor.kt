package com.medisense.app.domain.model

/**
 * Directional effect of a factor on the overall contextual score.
 */
enum class FactorEffectDirection {
    INCREASES_SCORE,
    DECREASES_SCORE,
    NEUTRAL,
    UNAVAILABLE
}

/**
 * Represents an individual explainable contributing factor in the risk assessment.
 */
data class ContextualRiskFactor(
    val factorId: String,
    val category: ContextualRiskCategory,
    val title: String,
    val description: String,
    val rawContributionScore: Float, // Normalized 0.0 to 100.0
    val weightedContribution: Float, // Contribution in points towards the final weighted score
    val weight: Float, // Factor weight (e.g. 0.20)
    val source: String, // E.g., "Longitudinal Health Trends (Module 9B)"
    val isAvailable: Boolean,
    val effectDirection: FactorEffectDirection,
    val explanation: String // Human-readable, deterministic explanation
)
