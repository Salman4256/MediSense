package com.medisense.app.domain.adaptive

import com.medisense.app.domain.model.DiscrepancyCategory
import com.medisense.app.domain.model.ObservedResponse
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

/**
 * Deterministic discrepancy calculation engine (Module 25).
 * Computes divergence between expected intervention response and actual user-recorded outcome.
 */
@Singleton
class AdaptiveDiscrepancyEngine @Inject constructor() {

    data class DiscrepancyResult(
        val discrepancyValue: Float,
        val discrepancyCategory: DiscrepancyCategory,
        val calculatedExpectedValue: Float,
        val calculatedObservedValue: Float,
        val dataQualityStatus: String
    )

    /**
     * Calculates discrepancy magnitude and category given numerical or qualitative input.
     */
    fun calculateDiscrepancy(
        expectedResponse: String,
        observedResponse: ObservedResponse,
        expectedValue: Float?,
        observedValue: Float?
    ): DiscrepancyResult {
        if (observedResponse == ObservedResponse.NOT_OBSERVED) {
            return DiscrepancyResult(
                discrepancyValue = 0.0f,
                discrepancyCategory = DiscrepancyCategory.INSUFFICIENT_OBSERVATION,
                calculatedExpectedValue = expectedValue ?: 0.0f,
                calculatedObservedValue = observedValue ?: 0.0f,
                dataQualityStatus = "INSUFFICIENT_DATA"
            )
        }

        val effExpected: Float = expectedValue ?: run {
            if (expectedResponse.contains("worsen", ignoreCase = true)) {
                -0.5f
            } else if (expectedResponse.contains("unchanged", ignoreCase = true) || expectedResponse.contains("maintain", ignoreCase = true)) {
                0.3f
            } else {
                AdaptiveFeedbackConfiguration.DEFAULT_EXPECTED_IMPROVEMENT_VALUE
            }
        }

        val effObserved: Float = observedValue ?: observedResponse.qualitativeScore

        val rawDiscrepancy = abs(effExpected - effObserved)
        // Clamp normalized discrepancy to [0.0, 2.0]
        val clampedDiscrepancy = rawDiscrepancy.coerceIn(0.0f, 2.0f)

        val category = when {
            clampedDiscrepancy <= AdaptiveFeedbackConfiguration.DISCREPANCY_ALIGNED_THRESHOLD -> DiscrepancyCategory.ALIGNED
            clampedDiscrepancy <= AdaptiveFeedbackConfiguration.DISCREPANCY_SLIGHT_THRESHOLD -> DiscrepancyCategory.SLIGHT_DEVIATION
            clampedDiscrepancy <= AdaptiveFeedbackConfiguration.DISCREPANCY_MODERATE_THRESHOLD -> DiscrepancyCategory.MODERATE_DEVIATION
            else -> DiscrepancyCategory.LARGE_DEVIATION
        }

        val qualityStatus = if (observedResponse == ObservedResponse.UNCLEAR) {
            "VALID_WITH_WARNING"
        } else {
            "VALID"
        }

        return DiscrepancyResult(
            discrepancyValue = clampedDiscrepancy,
            discrepancyCategory = category,
            calculatedExpectedValue = effExpected,
            calculatedObservedValue = effObserved,
            dataQualityStatus = qualityStatus
        )
    }
}
