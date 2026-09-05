package com.medisense.app.domain.counterfactual

/**
 * Centralized configuration parameters for Module 13 Prediction Confidence & Counterfactual Analysis.
 *
 * NOTE: All thresholds and tiers are application-defined heuristics for educational decision-support.
 * They are NOT clinically calibrated diagnostic probability cutoffs.
 */
object PredictionConfidenceConfiguration {

    // Thresholds for Confidence Levels
    const val CONFIDENCE_THRESHOLD_HIGH = 0.75f // 75%+ probability
    const val CONFIDENCE_THRESHOLD_MODERATE = 0.50f // 50% - 74.9% probability
    const val CONFIDENCE_THRESHOLD_MINIMAL = 0.15f // Below this is considered insufficient/inconclusive

    // Counterfactual Constraints
    const val MIN_SYMPTOMS_FOR_COUNTERFACTUAL = 2 // Need at least 2 symptoms to ablate 1
    const val MAX_COUNTERFACTUAL_SYMPTOMS = 3 // Evaluate at most top 3 symptoms to keep runtime low

    // Mandatory Non-Diagnostic Disclaimers
    const val CONFIDENCE_SEPARATION_DISCLAIMER =
        "Model confidence reflects how strongly the algorithm prioritizes this output. It is not medical certainty and is not a clinical diagnosis."

    const val SENSITIVITY_DISCLAIMER =
        "This sensitivity indicator describes how the current model output responds to tested symptom changes. It is not a clinical stability or disease-severity measure."
}
