package com.medisense.app.domain.adaptive

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Deterministic Safety Filter for Adaptive Counterfactual Interventions (Module 25).
 * Ensures all user-facing scenario recommendations and explanations adhere to non-diagnostic standards.
 */
@Singleton
class AdaptiveFeedbackSafetyFilter @Inject constructor() {

    private val prohibitedTerms = listOf(
        "cure",
        "cures",
        "curative",
        "prescribe",
        "prescription",
        "guaranteed to heal",
        "stop taking medication",
        "substitute for medical treatment",
        "emergency diagnosis",
        "definite diagnosis"
    )

    /**
     * Inspects and sanitizes explanation or rationale text to guarantee non-clinical tone.
     */
    fun sanitizeExplanation(input: String): String {
        var sanitized = input
        for (term in prohibitedTerms) {
            if (sanitized.contains(term, ignoreCase = true)) {
                sanitized = sanitized.replace(Regex("(?i)\\b$term\\b"), "[clinical guidance]")
            }
        }
        return sanitized
    }

    /**
     * Checks if text contains any prohibited medical certainty or prescription claims.
     */
    fun containsProhibitedClaims(text: String): Boolean {
        return prohibitedTerms.any { text.contains(it, ignoreCase = true) }
    }

    /**
     * Formats a clinical consultation discussion point from an adaptive observation discrepancy.
     */
    fun formatConsultationDiscussionPoint(
        categoryName: String,
        observedDeviationSummary: String
    ): String {
        return "Observation for discussion: Patient noted variations in $categoryName ($observedDeviationSummary). Discuss with physician during next routine review."
    }
}
