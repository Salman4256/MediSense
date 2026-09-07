package com.medisense.app.domain.adaptive

import com.medisense.app.domain.model.*
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Deterministic Adaptive Counterfactual Re-Ranking Engine (Module 25).
 * Adjusts counterfactual scenario prioritization based on historical response evidence.
 */
@Singleton
class AdaptiveCounterfactualReRankingEngine @Inject constructor() {

    data class RawScenarioCandidate(
        val scenarioId: String,
        val title: String,
        val description: String,
        val category: InterventionCategory,
        val baselineScore: Float,
        val expectedShiftDescription: String
    )

    /**
     * Re-ranks raw scenario candidates against the user's Personal Intervention Response Profile.
     */
    fun reRankCandidates(
        rawCandidates: List<RawScenarioCandidate>,
        profile: PersonalInterventionResponseProfile
    ): List<AdaptiveCounterfactualCandidate> {
        val evaluated = rawCandidates.map { raw ->
            val summary = profile.categorySummaries[raw.category]

            val (boost, penalty, weight) = if (summary != null && summary.totalObservations > 0) {
                val b = summary.alignmentRatio * AdaptiveFeedbackConfiguration.MAX_EVIDENCE_BOOST
                val p = (summary.averageDiscrepancy.coerceIn(0.0f, 1.0f) * (1.0f - summary.alignmentRatio)) * AdaptiveFeedbackConfiguration.MAX_DISCREPANCY_PENALTY
                val w = summary.confidenceLevel.multiplier
                Triple(b, p, w)
            } else {
                Triple(0.0f, 0.0f, AdaptiveFeedbackConfiguration.CONFIDENCE_INSUFFICIENT_MULTIPLIER)
            }

            val netAdjustment = (boost * weight) - (penalty * weight)
            val adaptiveScore = (raw.baselineScore + netAdjustment)
                .coerceIn(AdaptiveFeedbackConfiguration.MIN_ADAPTIVE_SCORE, AdaptiveFeedbackConfiguration.MAX_ADAPTIVE_SCORE)

            val rationale = generateRationale(raw.category, summary, boost, penalty, weight, netAdjustment)

            AdaptiveCounterfactualCandidate(
                scenarioId = raw.scenarioId,
                title = raw.title,
                description = raw.description,
                category = raw.category,
                baselineScore = raw.baselineScore,
                expectedShiftDescription = raw.expectedShiftDescription,
                evidenceBoost = boost,
                discrepancyPenalty = penalty,
                confidenceWeight = weight,
                adaptiveScore = adaptiveScore,
                rank = 0, // assigned after sorting
                rankingRationale = rationale,
                safeDisclaimer = AdaptiveFeedbackConfiguration.SAFE_NON_DIAGNOSTIC_DISCLAIMER
            )
        }

        // Deterministic sort: highest adaptiveScore first, then baselineScore descending, then scenarioId ascending
        val sorted = evaluated.sortedWith(
            compareByDescending<AdaptiveCounterfactualCandidate> { it.adaptiveScore }
                .thenByDescending { it.baselineScore }
                .thenBy { it.scenarioId }
        )

        return sorted.mapIndexed { index, candidate ->
            candidate.copy(rank = index + 1)
        }
    }

    private fun generateRationale(
        category: InterventionCategory,
        summary: CategoryResponseSummary?,
        boost: Float,
        penalty: Float,
        weight: Float,
        netAdjustment: Float
    ): String {
        if (summary == null || summary.totalObservations == 0) {
            return "Ranked primarily on baseline feature importance. No past observations recorded for ${category.displayName} (Weight: 20%)."
        }

        return when {
            netAdjustment > 0.05f -> {
                val pct = (summary.alignmentRatio * 100).toInt()
                "Rank boosted by +${String.format("%.2f", netAdjustment * 100)}% due to consistent historical observation alignment ($pct% alignment across ${summary.totalObservations} observation(s))."
            }
            netAdjustment < -0.05f -> {
                "Rank adjusted by ${String.format("%.2f", netAdjustment * 100)}% reflecting past observed deviations in ${category.displayName} (${summary.largeDeviationCount + summary.moderateDeviationCount} deviation(s) recorded)."
            }
            else -> {
                "Stable ranking. Historical observations for ${category.displayName} align closely with baseline expectations with minimal deviation."
            }
        }
    }
}
