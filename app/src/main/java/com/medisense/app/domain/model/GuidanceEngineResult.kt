package com.medisense.app.domain.model

/**
 * Output result from the Adaptive Personalized Guidance Engine.
 *
 * @param userId Supabase Auth UUID of the user
 * @param guidanceList Filtered, deduplicated, and ranked guidance recommendations
 * @param totalEvaluatedRules Total number of rules considered during execution
 * @param generatedTimestamp Milliseconds timestamp when guidance was produced
 * @param hasSufficientData Whether the user has adequate history for tailored guidance
 * @param dataLimitationsNotice Explains why some guidance might be generalized due to limited data
 */
data class GuidanceEngineResult(
    val userId: String,
    val guidanceList: List<PersonalizedGuidance>,
    val totalEvaluatedRules: Int,
    val generatedTimestamp: Long = System.currentTimeMillis(),
    val hasSufficientData: Boolean,
    val dataLimitationsNotice: String? = null
)
