package com.medisense.app.domain.adaptive

/**
 * Centralized deterministic configuration constants for Module 25 Adaptive Counterfactual Intervention.
 */
object AdaptiveFeedbackConfiguration {

    // Discrepancy Categorization Thresholds (normalized scale [0.0, 1.0+])
    const val DISCREPANCY_ALIGNED_THRESHOLD = 0.20f
    const val DISCREPANCY_SLIGHT_THRESHOLD = 0.45f
    const val DISCREPANCY_MODERATE_THRESHOLD = 0.75f

    // Confidence Multipliers
    const val CONFIDENCE_HIGH_MULTIPLIER = 1.0f
    const val CONFIDENCE_MODERATE_MULTIPLIER = 0.7f
    const val CONFIDENCE_LOW_MULTIPLIER = 0.4f
    const val CONFIDENCE_INSUFFICIENT_MULTIPLIER = 0.2f

    // Observation Count Thresholds for Category Confidence
    const val MIN_OBSERVATIONS_FOR_LOW_CONFIDENCE = 1
    const val MIN_OBSERVATIONS_FOR_MODERATE_CONFIDENCE = 3
    const val MIN_OBSERVATIONS_FOR_HIGH_CONFIDENCE = 6

    // Adaptive Re-ranking Bounds
    const val MAX_EVIDENCE_BOOST = 0.35f
    const val MAX_DISCREPANCY_PENALTY = 0.35f
    const val MIN_ADAPTIVE_SCORE = 0.05f
    const val MAX_ADAPTIVE_SCORE = 0.99f

    // Minimum observations to detect multi-category interaction patterns
    const val MIN_MULTI_CATEGORY_OBSERVATIONS = 4

    // Default expected value for qualitative improvement
    const val DEFAULT_EXPECTED_IMPROVEMENT_VALUE = 0.8f
    const val DEFAULT_BASELINE_VALUE = 0.0f

    // Safe Non-Diagnostic Disclaimer
    const val SAFE_NON_DIAGNOSTIC_DISCLAIMER =
        "Notice: This adaptive scenario is an algorithmic projection based on recorded historical observations. It does not constitute medical diagnosis, treatment, or clinical advice."
}
