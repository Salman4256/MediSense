package com.medisense.app.domain.model

import java.io.Serializable

/**
 * Indicates how responsive the model's top prediction is to single-symptom counterfactual removals.
 *
 * NOTE: This reflects algorithmic sensitivity to tested symptom perturbations,
 * NOT a patient's clinical stability or disease severity.
 */
enum class ModelSensitivity(val label: String, val description: String) : Serializable {
    STABLE(
        "Stable Output",
        "The model's primary suspected condition remained unchanged across tested symptom changes."
    ),
    SENSITIVE(
        "Sensitive Output",
        "Removing one or more key symptoms changed the model's top suspected condition."
    ),
    INSUFFICIENT_DATA(
        "Insufficient Data",
        "Not enough symptoms were selected to test counterfactual sensitivity."
    )
}
