package com.medisense.app.domain.model

import java.io.Serializable

/**
 * Categorizes the outcome of a single-symptom counterfactual ablation test.
 */
enum class CounterfactualChangeType(val label: String) : Serializable {
    CHANGED_PREDICTION("Top Prediction Changed"),
    REMOVE_SYMPTOM("Confidence Adjusted"),
    NO_CHANGE("No Change in Output")
}
