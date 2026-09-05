package com.medisense.app.domain.counterfactual

import com.medisense.app.data.model.DiseasePrediction
import com.medisense.app.domain.model.ConfidenceLevel
import com.medisense.app.domain.model.PredictionConfidenceSummary
import kotlin.math.roundToInt

/**
 * Deterministic interpreter for model prediction outputs.
 * Provides transparent, non-diagnostic communication of algorithmic confidence.
 */
object PredictionConfidenceInterpreter {

    /**
     * Interprets the raw model output into a structured, educational confidence summary.
     */
    fun interpret(
        primaryPrediction: DiseasePrediction,
        symptomCount: Int
    ): PredictionConfidenceSummary {
        val prob = primaryPrediction.probability.coerceIn(0.0f, 1.0f)
        val percent = (prob * 100).roundToInt()

        val (level, interpretation) = when {
            symptomCount < 1 || prob < PredictionConfidenceConfiguration.CONFIDENCE_THRESHOLD_MINIMAL -> {
                ConfidenceLevel.INSUFFICIENT_DATA to "Few symptoms were reported; the model output may be inconclusive."
            }
            prob >= PredictionConfidenceConfiguration.CONFIDENCE_THRESHOLD_HIGH -> {
                ConfidenceLevel.HIGH to "The model shows a stronger relative preference for this condition based on your reported symptoms."
            }
            prob >= PredictionConfidenceConfiguration.CONFIDENCE_THRESHOLD_MODERATE -> {
                ConfidenceLevel.MODERATE to "The model shows a moderate relative preference for this condition."
            }
            else -> {
                ConfidenceLevel.LOW to "The available symptom combination produces a less decisive model output across multiple conditions."
            }
        }

        return PredictionConfidenceSummary(
            topPrediction = primaryPrediction.diseaseName,
            confidenceValue = prob,
            confidencePercentage = percent,
            confidenceLevel = level,
            interpretation = interpretation,
            disclaimer = PredictionConfidenceConfiguration.CONFIDENCE_SEPARATION_DISCLAIMER
        )
    }
}
