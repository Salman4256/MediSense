package com.medisense.app.domain.risk

import com.medisense.app.domain.model.ContextualRiskFactor
import com.medisense.app.domain.model.ContextualRiskLevel
import com.medisense.app.domain.model.FactorEffectDirection

/**
 * Generates transparent, deterministic, and non-diagnostic human-readable explanations.
 */
object ContextualRiskExplanationBuilder {

    /**
     * Builds a concise overall summary of the user's health context assessment.
     */
    fun buildAssessmentSummary(
        riskLevel: ContextualRiskLevel,
        overallScore: Int?,
        factors: List<ContextualRiskFactor>
    ): String {
        if (riskLevel == ContextualRiskLevel.INSUFFICIENT_DATA || overallScore == null) {
            return "Not enough health history is available to calculate a meaningful contextual score. Logging symptoms, medications, or health records will provide personalized health context."
        }

        val positive = factors.filter { it.effectDirection == FactorEffectDirection.INCREASES_SCORE }
        val mitigating = factors.filter { it.effectDirection == FactorEffectDirection.DECREASES_SCORE }

        val builder = StringBuilder()
        when (riskLevel) {
            ContextualRiskLevel.LOW -> {
                builder.append("Your health context shows stable baseline activity with low contextual priority. ")
            }
            ContextualRiskLevel.MODERATE -> {
                builder.append("Your health context shows moderate activity with specific factors contributing to increased focus. ")
            }
            ContextualRiskLevel.HIGH -> {
                builder.append("Several concurrent health signals are currently contributing to a higher application-defined contextual score. ")
            }
            ContextualRiskLevel.INSUFFICIENT_DATA -> {}
        }

        if (positive.isNotEmpty()) {
            val topTitles = positive.take(2).joinToString(" and ") { it.title.lowercase() }
            builder.append("Key contributors include $topTitles. ")
        }

        if (mitigating.isNotEmpty()) {
            val topMitigating = mitigating.first().title.lowercase()
            builder.append("Positive routines such as $topMitigating helped balance your contextual score.")
        }

        return builder.toString().trim()
    }

    /**
     * Explains a specific factor's contribution in plain, objective terms.
     */
    fun buildFactorExplanation(
        factorId: String,
        score: Float,
        detail: String
    ): String {
        return when (factorId) {
            ContextualRiskConfiguration.ID_SYMPTOM_RECURRENCE -> {
                if (score > 40f) {
                    "Repeated symptom activity ($detail) was detected across your recorded health checks."
                } else {
                    "No significant recurring symptom patterns were identified in recent checks."
                }
            }
            ContextualRiskConfiguration.ID_PREDICTION_ACTIVITY -> {
                if (score > 30f) {
                    "Recent health checkup sessions ($detail) contributed to active contextual monitoring."
                } else {
                    "Few or no recent prediction checkups were recorded in the active period."
                }
            }
            ContextualRiskConfiguration.ID_CONFIDENCE_DYNAMICS -> {
                if (score > 50f) {
                    "Prediction model consistency trends remained stable across recent assessment inputs ($detail)."
                } else {
                    "Prediction consistency dynamics are within baseline variability."
                }
            }
            ContextualRiskConfiguration.ID_MEDICATION_ADHERENCE -> {
                if (score <= 20f) {
                    "High medication adherence ($detail) actively supports routine consistency."
                } else if (score <= 50f) {
                    "Moderate medication adherence ($detail) recorded across scheduled doses."
                } else {
                    "Missed or irregular medication doses ($detail) contributed to higher contextual attention."
                }
            }
            ContextualRiskConfiguration.ID_APPOINTMENT_CONTEXT -> {
                if (score <= 25f) {
                    "Upcoming medical appointment scheduled ($detail), reflecting active clinical follow-up."
                } else {
                    "No upcoming follow-up appointments currently scheduled in your planner."
                }
            }
            ContextualRiskConfiguration.ID_CHRONIC_ALLERGY -> {
                if (score > 0f) {
                    "Recorded baseline health profile notes pre-existing conditions or allergies ($detail)."
                } else {
                    "No pre-existing chronic conditions or allergies recorded in your profile."
                }
            }
            ContextualRiskConfiguration.ID_TEMPORAL_PATTERNS -> {
                if (score > 30f) {
                    "Temporal trend analysis identified longitudinal patterns ($detail) in health history."
                } else {
                    "No temporal variability or periodic symptom fluctuations detected."
                }
            }
            else -> detail
        }
    }
}
