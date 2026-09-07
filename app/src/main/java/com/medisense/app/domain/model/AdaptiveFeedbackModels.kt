package com.medisense.app.domain.model

/**
 * Enumeration of intervention and guidance categories for adaptive feedback (Module 25).
 */
enum class InterventionCategory(val displayName: String, val description: String) {
    SYMPTOM_MANAGEMENT("Symptom Management", "Targeted symptom tracking and relief scenarios"),
    LIFESTYLE_MODIFICATION("Lifestyle Modification", "Dietary, sleep, hydration, and activity adjustments"),
    MEDICATION_ADHERENCE("Medication Adherence", "Adherence tracking and scheduled routine reminders"),
    CLINICAL_FOLLOWUP("Clinical Follow-up", "Consultation readiness and specialist follow-up"),
    ROUTINE_MONITORING("Routine Monitoring", "Regular vital signs and longitudinal biomarker monitoring");

    companion object {
        fun fromString(value: String?): InterventionCategory {
            return entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: SYMPTOM_MANAGEMENT
        }
    }
}

/**
 * User-recorded qualitative observation of an intervention's outcome.
 */
enum class ObservedResponse(val displayName: String, val qualitativeScore: Float) {
    IMPROVED("Improved / Symptoms Reduced", 1.0f),
    PARTIALLY_IMPROVED("Partially Improved", 0.7f),
    UNCHANGED("Unchanged / Baseline Maintained", 0.3f),
    WORSENED("Worsened / Increased Severity", -0.5f),
    UNCLEAR("Unclear / Variable Response", 0.1f),
    NOT_OBSERVED("Not Observed / Skipped", 0.0f);

    companion object {
        fun fromString(value: String?): ObservedResponse {
            return entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: NOT_OBSERVED
        }
    }
}

/**
 * Deterministic categorization of difference between expected and observed state.
 */
enum class DiscrepancyCategory(val displayName: String, val severityLevel: Int) {
    ALIGNED("Aligned with Expected Trend", 0),
    SLIGHT_DEVIATION("Slight Deviation", 1),
    MODERATE_DEVIATION("Moderate Deviation", 2),
    LARGE_DEVIATION("Large Deviation", 3),
    INSUFFICIENT_OBSERVATION("Insufficient Observation", 0);

    companion object {
        fun fromString(value: String?): DiscrepancyCategory {
            return entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: INSUFFICIENT_OBSERVATION
        }
    }
}

/**
 * Data confidence level for adaptive user profile and category-level evidence.
 */
enum class ResponseConfidenceLevel(val displayName: String, val multiplier: Float) {
    HIGH("High Confidence", 1.0f),
    MODERATE("Moderate Confidence", 0.7f),
    LOW("Low Confidence", 0.4f),
    INSUFFICIENT_DATA("Insufficient Data", 0.2f);

    companion object {
        fun fromString(value: String?): ResponseConfidenceLevel {
            return entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: INSUFFICIENT_DATA
        }
    }
}

/**
 * Multi-category interaction pattern status.
 */
enum class InteractionPatternStatus(val displayName: String) {
    NO_EVIDENCE("No Interaction Detected"),
    INSUFFICIENT_DATA("Insufficient Data for Interaction Analysis"),
    POSSIBLE_INTERACTION_PATTERN("Possible Multi-Category Interaction Pattern");

    companion object {
        fun fromString(value: String?): InteractionPatternStatus {
            return entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: INSUFFICIENT_DATA
        }
    }
}

/**
 * Clean domain representation of an intervention response and feedback record.
 */
data class InterventionResponseRecord(
    val id: Long,
    val userId: String,
    val sourcePredictionId: Long?,
    val counterfactualId: String?,
    val interventionCategory: InterventionCategory,
    val interventionDescription: String,
    val baselineStateSummary: String,
    val expectedResponse: String,
    val observedResponse: ObservedResponse,
    val expectedValue: Float?,
    val observedValue: Float?,
    val discrepancyValue: Float,
    val discrepancyCategory: DiscrepancyCategory,
    val userNotes: String?,
    val observationTimestamp: Long,
    val createdAt: Long,
    val updatedAt: Long,
    val confidenceLevel: ResponseConfidenceLevel,
    val dataQualityStatus: String,
    val modelVersion: String,
    val algorithmVersion: String
)

/**
 * Aggregated response statistics for a single intervention category.
 */
data class CategoryResponseSummary(
    val category: InterventionCategory,
    val totalObservations: Int,
    val alignedCount: Int,
    val slightDeviationCount: Int,
    val moderateDeviationCount: Int,
    val largeDeviationCount: Int,
    val averageDiscrepancy: Float,
    val alignmentRatio: Float,
    val confidenceLevel: ResponseConfidenceLevel
)

/**
 * Personal Intervention Response Profile (PIRP) representing user historical feedback memory.
 */
data class PersonalInterventionResponseProfile(
    val userId: String,
    val totalObservations: Int,
    val validObservationsCount: Int,
    val categorySummaries: Map<InterventionCategory, CategoryResponseSummary>,
    val overallConfidenceLevel: ResponseConfidenceLevel,
    val interactionPatternStatus: InteractionPatternStatus,
    val patternDescription: String,
    val lastObservationTimestamp: Long?
)

/**
 * Adaptively re-ranked counterfactual scenario candidate.
 */
data class AdaptiveCounterfactualCandidate(
    val scenarioId: String,
    val title: String,
    val description: String,
    val category: InterventionCategory,
    val baselineScore: Float,
    val expectedShiftDescription: String,
    val evidenceBoost: Float,
    val discrepancyPenalty: Float,
    val confidenceWeight: Float,
    val adaptiveScore: Float,
    val rank: Int,
    val rankingRationale: String,
    val safeDisclaimer: String = "Non-diagnostic decision-support scenario based on historical observation alignment."
)

/**
 * Engine result packaging adaptive candidates, profile, and recent observations.
 */
data class AdaptiveFeedbackEngineResult(
    val candidates: List<AdaptiveCounterfactualCandidate>,
    val profile: PersonalInterventionResponseProfile,
    val recentRecords: List<InterventionResponseRecord>,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Input payload for recording an observation on an intervention scenario.
 */
data class ObservationInput(
    val sourcePredictionId: Long? = null,
    val counterfactualId: String? = null,
    val category: InterventionCategory,
    val interventionDescription: String,
    val baselineStateSummary: String,
    val expectedResponse: String,
    val observedResponse: ObservedResponse,
    val expectedValue: Float? = null,
    val observedValue: Float? = null,
    val userNotes: String? = null,
    val observationTimestamp: Long = System.currentTimeMillis()
)
