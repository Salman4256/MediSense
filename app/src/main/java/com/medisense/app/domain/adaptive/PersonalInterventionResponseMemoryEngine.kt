package com.medisense.app.domain.adaptive

import com.medisense.app.domain.model.*
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Memory synthesis engine for Personal Intervention Response Profile (PIRP) (Module 25).
 * Deterministically computes category alignment stats, confidence levels, and interaction indicators.
 */
@Singleton
class PersonalInterventionResponseMemoryEngine @Inject constructor() {

    /**
     * Synthesizes user records into a structured PersonalInterventionResponseProfile.
     */
    fun synthesizeProfile(
        userId: String,
        records: List<InterventionResponseRecord>
    ): PersonalInterventionResponseProfile {
        if (records.isEmpty()) {
            return PersonalInterventionResponseProfile(
                userId = userId,
                totalObservations = 0,
                validObservationsCount = 0,
                categorySummaries = emptyMap(),
                overallConfidenceLevel = ResponseConfidenceLevel.INSUFFICIENT_DATA,
                interactionPatternStatus = InteractionPatternStatus.INSUFFICIENT_DATA,
                patternDescription = "No previous intervention observations recorded for this user.",
                lastObservationTimestamp = null
            )
        }

        val validRecords = records.filter {
            it.dataQualityStatus != "INVALID" && it.observedResponse != ObservedResponse.NOT_OBSERVED
        }

        val categorySummaries = mutableMapOf<InterventionCategory, CategoryResponseSummary>()

        for (category in InterventionCategory.entries) {
            val categoryRecords = validRecords.filter { it.interventionCategory == category }
            val count = categoryRecords.size

            if (count == 0) {
                categorySummaries[category] = CategoryResponseSummary(
                    category = category,
                    totalObservations = 0,
                    alignedCount = 0,
                    slightDeviationCount = 0,
                    moderateDeviationCount = 0,
                    largeDeviationCount = 0,
                    averageDiscrepancy = 0.0f,
                    alignmentRatio = 0.0f,
                    confidenceLevel = ResponseConfidenceLevel.INSUFFICIENT_DATA
                )
                continue
            }

            val alignedCount = categoryRecords.count { it.discrepancyCategory == DiscrepancyCategory.ALIGNED }
            val slightCount = categoryRecords.count { it.discrepancyCategory == DiscrepancyCategory.SLIGHT_DEVIATION }
            val modCount = categoryRecords.count { it.discrepancyCategory == DiscrepancyCategory.MODERATE_DEVIATION }
            val largeCount = categoryRecords.count { it.discrepancyCategory == DiscrepancyCategory.LARGE_DEVIATION }
            val avgDiscrepancy = categoryRecords.map { it.discrepancyValue }.average().toFloat()
            val alignmentRatio = (alignedCount + 0.5f * slightCount) / count.toFloat()

            val catConfidence = when {
                count >= AdaptiveFeedbackConfiguration.MIN_OBSERVATIONS_FOR_HIGH_CONFIDENCE -> ResponseConfidenceLevel.HIGH
                count >= AdaptiveFeedbackConfiguration.MIN_OBSERVATIONS_FOR_MODERATE_CONFIDENCE -> ResponseConfidenceLevel.MODERATE
                count >= AdaptiveFeedbackConfiguration.MIN_OBSERVATIONS_FOR_LOW_CONFIDENCE -> ResponseConfidenceLevel.LOW
                else -> ResponseConfidenceLevel.INSUFFICIENT_DATA
            }

            categorySummaries[category] = CategoryResponseSummary(
                category = category,
                totalObservations = count,
                alignedCount = alignedCount,
                slightDeviationCount = slightCount,
                moderateDeviationCount = modCount,
                largeDeviationCount = largeCount,
                averageDiscrepancy = avgDiscrepancy,
                alignmentRatio = alignmentRatio.coerceIn(0.0f, 1.0f),
                confidenceLevel = catConfidence
            )
        }

        val totalValid = validRecords.size
        val overallConfidence = when {
            totalValid >= 10 -> ResponseConfidenceLevel.HIGH
            totalValid >= 4 -> ResponseConfidenceLevel.MODERATE
            totalValid >= 1 -> ResponseConfidenceLevel.LOW
            else -> ResponseConfidenceLevel.INSUFFICIENT_DATA
        }

        // Multi-category interaction pattern analysis
        val (patternStatus, patternDesc) = evaluateInteractionPatterns(categorySummaries, totalValid)

        val latestTimestamp = records.maxOfOrNull { it.observationTimestamp }

        return PersonalInterventionResponseProfile(
            userId = userId,
            totalObservations = records.size,
            validObservationsCount = totalValid,
            categorySummaries = categorySummaries,
            overallConfidenceLevel = overallConfidence,
            interactionPatternStatus = patternStatus,
            patternDescription = patternDesc,
            lastObservationTimestamp = latestTimestamp
        )
    }

    private fun evaluateInteractionPatterns(
        summaries: Map<InterventionCategory, CategoryResponseSummary>,
        totalValid: Int
    ): Pair<InteractionPatternStatus, String> {
        if (totalValid < AdaptiveFeedbackConfiguration.MIN_MULTI_CATEGORY_OBSERVATIONS) {
            return Pair(
                InteractionPatternStatus.INSUFFICIENT_DATA,
                "Insufficient observations across multiple categories for composite pattern detection."
            )
        }

        val activeCategories = summaries.filter { it.value.totalObservations >= 2 }
        if (activeCategories.size < 2) {
            return Pair(
                InteractionPatternStatus.NO_EVIDENCE,
                "Observations are concentrated in a single category. No cross-category interaction pattern detected."
            )
        }

        val lifestyleSummary = summaries[InterventionCategory.LIFESTYLE_MODIFICATION]
        val medicationSummary = summaries[InterventionCategory.MEDICATION_ADHERENCE]
        val symptomSummary = summaries[InterventionCategory.SYMPTOM_MANAGEMENT]

        val highAlignmentCount = activeCategories.count { it.value.alignmentRatio >= 0.7f }

        return if (highAlignmentCount >= 2) {
            val names = activeCategories.filter { it.value.alignmentRatio >= 0.7f }.keys.joinToString(" and ") { it.displayName }
            Pair(
                InteractionPatternStatus.POSSIBLE_INTERACTION_PATTERN,
                "Strong historical alignment observed across $names. Multi-domain adherence may correlate with improved response stability."
            )
        } else {
            Pair(
                InteractionPatternStatus.NO_EVIDENCE,
                "Observations recorded across multiple categories show independent baseline trajectories."
            )
        }
    }
}
