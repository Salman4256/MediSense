package com.medisense.app.domain.model

import java.io.Serializable

/**
 * Represents the deterministic outcome of a single-symptom counterfactual "What-If" test.
 *
 * @param removedSymptom The symptom removed during counterfactual simulation
 * @param removedSymptomDisplayName Human-friendly name of the removed symptom
 * @param originalPrediction Primary condition predicted with full symptom set
 * @param originalConfidence Original confidence probability (0.0 to 1.0)
 * @param resultingPrediction Top condition predicted after symptom removal
 * @param resultingConfidence Resulting confidence probability (0.0 to 1.0)
 * @param confidenceDelta Difference between resulting and original confidence
 * @param isPredictionChanged Whether the primary suspected condition shifted
 * @param changeType Classification of the counterfactual outcome
 * @param explanation Human-readable, non-diagnostic explanation of the shift
 * @param importanceWeight Feature importance score from Module 4 XAI
 */
data class CounterfactualExplanation(
    val removedSymptom: String,
    val removedSymptomDisplayName: String,
    val originalPrediction: String,
    val originalConfidence: Float,
    val resultingPrediction: String,
    val resultingConfidence: Float,
    val confidenceDelta: Float,
    val isPredictionChanged: Boolean,
    val changeType: CounterfactualChangeType,
    val explanation: String,
    val importanceWeight: Float
) : Serializable
