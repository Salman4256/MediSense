package com.medisense.app.domain.model

import java.io.Serializable

/**
 * Aggregated counterfactual and confidence analysis result for a completed prediction.
 *
 * @param confidenceSummary Interpreted model confidence summary
 * @param sensitivity Evaluated stability of the model output across tested symptom perturbations
 * @param sensitivityExplanation Clear explanation of the sensitivity indicator
 * @param counterfactuals Deterministically ranked list of tested counterfactual scenarios (top 3)
 * @param hasSufficientSymptoms Whether enough symptoms (>= 2) were selected for counterfactual evaluation
 */
data class CounterfactualAnalysisResult(
    val confidenceSummary: PredictionConfidenceSummary,
    val sensitivity: ModelSensitivity,
    val sensitivityExplanation: String,
    val counterfactuals: List<CounterfactualExplanation>,
    val hasSufficientSymptoms: Boolean
) : Serializable
