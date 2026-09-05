package com.medisense.app.domain.model

/**
 * Complete, deterministic Context-Aware Health Risk Assessment.
 * Combines signals from Modules 1–10 into an explainable contextual priority score.
 */
data class ContextualRiskAssessment(
    val userId: String,
    val overallScore: Int?, // 0 to 100, or null if insufficient data
    val riskLevel: ContextualRiskLevel,
    val contributingFactors: List<ContextualRiskFactor>,
    val positiveContributors: List<ContextualRiskFactor>,
    val neutralOrMitigatingFactors: List<ContextualRiskFactor>,
    val unavailableFactors: List<ContextualRiskFactor>,
    val generatedSummary: String,
    val dataAvailabilitySummary: Map<String, Boolean>,
    val methodologyDisclaimer: String = "This contextual health score is an application-defined indicator based on available health information. It is not a medical diagnosis, clinically validated risk score, or substitute for professional medical advice.",
    val hasSufficientData: Boolean,
    val calculatedAt: Long = System.currentTimeMillis()
)
