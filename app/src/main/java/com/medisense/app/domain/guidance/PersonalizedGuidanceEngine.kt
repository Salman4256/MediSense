package com.medisense.app.domain.guidance

import com.medisense.app.domain.model.ContextualRiskAssessment
import com.medisense.app.domain.model.ContextualRiskLevel
import com.medisense.app.domain.model.GuidanceActionType
import com.medisense.app.domain.model.GuidanceCategory
import com.medisense.app.domain.model.GuidanceEngineResult
import com.medisense.app.domain.model.GuidancePriority
import com.medisense.app.domain.model.LongitudinalHealthSummary
import com.medisense.app.domain.model.PersonalHealthContext
import com.medisense.app.domain.model.PersonalizedGuidance
import com.medisense.app.domain.rchr.RchrRepresentation
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

/**
 * Deterministic, safety-constrained Adaptive Personalized Guidance Engine.
 * Evaluates unified contextual signals from Modules 1–11 and generates explainable,
 * non-diagnostic health-management guidance.
 */
@Singleton
class PersonalizedGuidanceEngine @Inject constructor() {

    /**
     * Evaluates existing health context and produces a prioritized, safety-filtered list of recommendations.
     */
    fun evaluate(
        userId: String,
        personalContext: PersonalHealthContext?,
        longitudinalSummary: LongitudinalHealthSummary?,
        rchr: RchrRepresentation?,
        riskAssessment: ContextualRiskAssessment?
    ): GuidanceEngineResult {
        val candidates = mutableListOf<PersonalizedGuidance>()
        var totalRulesEvaluated = 0

        val isCompletelyFreshUser = personalContext == null && longitudinalSummary == null && rchr == null && riskAssessment == null

        if (isCompletelyFreshUser) {
            candidates.add(
                PersonalizedGuidance(
                    id = GuidanceConfiguration.RULE_BASELINE_SETUP,
                    category = GuidanceCategory.RECORD_MAINTENANCE,
                    title = "Build your personal health context",
                    message = "Start by completing your health profile, logging medications, or tracking symptoms to receive personalized health guidance.",
                    explanation = "Limited health history is currently recorded in your account.",
                    priority = GuidancePriority.LOW,
                    sources = listOf(GuidanceConfiguration.SOURCE_MODULE_2),
                    actionType = GuidanceActionType.NAVIGATE_PROFILE,
                    actionLabel = "Set Up Profile"
                )
            )
        } else {
            // 1. RULE: Profile Completeness
            totalRulesEvaluated++
            val completeness = personalContext?.profileCompleteness
                ?: rchr?.profileFeatures?.profileCompletenessPercent
                ?: 0

            if (completeness < GuidanceConfiguration.PROFILE_COMPLETENESS_THRESHOLD) {
                val priority = if (completeness == 0) GuidancePriority.HIGH else GuidancePriority.MEDIUM
                candidates.add(
                    PersonalizedGuidance(
                        id = GuidanceConfiguration.RULE_PROFILE_COMPLETENESS,
                        category = GuidanceCategory.PROFILE_COMPLETENESS,
                        title = "Complete your health profile",
                        message = "Adding your health details, allergies, and emergency contacts helps MediSense build a more accurate health context.",
                        explanation = "Profile completeness is currently at $completeness%. Completing your profile improves personalization.",
                        priority = priority,
                        sources = listOf(
                            GuidanceConfiguration.SOURCE_MODULE_2,
                            GuidanceConfiguration.SOURCE_MODULE_9A
                        ),
                        actionType = GuidanceActionType.NAVIGATE_PROFILE,
                        actionLabel = "Update Profile"
                    )
                )
            }
        }

        // 2. RULE: Symptom Recurrence Monitoring (Deduplicated across 9B and RCHR)
        totalRulesEvaluated++
        val recurringFrom9B = longitudinalSummary?.recurringSymptoms?.filter { it.isRecurring }?.map { it.symptomName } ?: emptyList()
        val recurringFromRchr = rchr?.symptomFeatures?.recurringSymptoms ?: emptyList()
        val combinedRecurring = (recurringFrom9B + recurringFromRchr).distinct()

        if (combinedRecurring.isNotEmpty()) {
            val symptomsStr = combinedRecurring.take(3).joinToString(", ")
            val priority = if (combinedRecurring.size >= 2) GuidancePriority.HIGH else GuidancePriority.MEDIUM
            candidates.add(
                PersonalizedGuidance(
                    id = GuidanceConfiguration.RULE_SYMPTOM_MONITORING,
                    category = GuidanceCategory.SYMPTOM_MONITORING,
                    title = "Monitor recurring symptom patterns",
                    message = "Repeated symptom activity ($symptomsStr) was identified across your checkups. Continue logging episodes and consider discussing persistent symptoms with a doctor.",
                    explanation = "Longitudinal health analysis detected ${combinedRecurring.size} recurring symptom pattern(s) in your history.",
                    priority = priority,
                    sources = listOf(
                        GuidanceConfiguration.SOURCE_MODULE_9B,
                        GuidanceConfiguration.SOURCE_MODULE_10
                    ),
                    actionType = GuidanceActionType.NAVIGATE_TRENDS,
                    actionLabel = "View Trends"
                )
            )
        }

        // 3. RULE: Medication Adherence Context
        totalRulesEvaluated++
        val activeMedCount = personalContext?.medications?.activeCount
            ?: rchr?.medicationFeatures?.activeMedicationCount
            ?: 0

        val adherencePct = longitudinalSummary?.adherenceTrend?.currentAdherencePercentage
            ?: personalContext?.medications?.adherencePercentage
            ?: rchr?.adherenceFeatures?.adherencePercentage

        if (activeMedCount > 0 && adherencePct != null && adherencePct < GuidanceConfiguration.ADHERENCE_OPTIMAL_THRESHOLD) {
            val priority = if (adherencePct < GuidanceConfiguration.ADHERENCE_SUBOPTIMAL_THRESHOLD) GuidancePriority.HIGH else GuidancePriority.MEDIUM
            val pctRounded = adherencePct.roundToInt()
            candidates.add(
                PersonalizedGuidance(
                    id = GuidanceConfiguration.RULE_MEDICATION_ADHERENCE,
                    category = GuidanceCategory.MEDICATION_ADHERENCE,
                    title = "Follow your prescribed medication schedule",
                    message = "Recent dose records show missed or delayed times. Consistently following your prescribed regimen supports routine effectiveness.",
                    explanation = "Recorded adherence is currently at $pctRounded%. Keeping dose logs up to date helps maintain consistency.",
                    priority = priority,
                    sources = listOf(
                        GuidanceConfiguration.SOURCE_MODULE_6,
                        GuidanceConfiguration.SOURCE_MODULE_9B
                    ),
                    actionType = GuidanceActionType.NAVIGATE_MEDICATIONS,
                    actionLabel = "Review Medications"
                )
            )
        }

        // 4. RULE: Appointment Follow-up
        totalRulesEvaluated++
        val upcomingAppts = personalContext?.appointments?.upcomingCount
            ?: longitudinalSummary?.appointmentActivity?.upcomingCount
            ?: rchr?.appointmentFeatures?.upcomingAppointmentCount
            ?: 0

        val nextDoctor = personalContext?.appointments?.nextAppointmentDoctor
            ?: rchr?.appointmentFeatures?.nextAppointmentDoctor
        val nextDate = personalContext?.appointments?.nextAppointmentDate
            ?: rchr?.appointmentFeatures?.nextAppointmentDate

        if (upcomingAppts > 0) {
            val doctorDetail = if (!nextDoctor.isNullOrBlank() && !nextDate.isNullOrBlank()) {
                " with $nextDoctor on $nextDate"
            } else if (!nextDoctor.isNullOrBlank()) {
                " with $nextDoctor"
            } else ""

            candidates.add(
                PersonalizedGuidance(
                    id = GuidanceConfiguration.RULE_APPOINTMENT_FOLLOW_UP,
                    category = GuidanceCategory.APPOINTMENT_FOLLOW_UP,
                    title = "Prepare for your upcoming appointment",
                    message = "You have an appointment scheduled$doctorDetail. Consider preparing a summary of recent symptoms, questions, and current medications.",
                    explanation = "An upcoming medical consultation is scheduled in your appointments calendar.",
                    priority = GuidancePriority.MEDIUM,
                    sources = listOf(
                        GuidanceConfiguration.SOURCE_MODULE_7,
                        GuidanceConfiguration.SOURCE_MODULE_9A
                    ),
                    actionType = GuidanceActionType.NAVIGATE_APPOINTMENTS,
                    actionLabel = "View Appointments"
                )
            )
        }

        // 5. RULE: Health Trends & Temporal Patterns
        totalRulesEvaluated++
        val patternCount = longitudinalSummary?.detectedPatterns?.size
            ?: rchr?.temporalFeatures?.detectedPatternsCount
            ?: 0

        if (longitudinalSummary?.hasSufficientData == true || patternCount > 0) {
            val priority = if (patternCount > 0) GuidancePriority.MEDIUM else GuidancePriority.LOW
            val explanation = if (patternCount > 0) {
                "Longitudinal analytics identified $patternCount temporal health pattern(s) across your history."
            } else {
                "Sufficient longitudinal checkup history is available to review temporal trends."
            }

            candidates.add(
                PersonalizedGuidance(
                    id = GuidanceConfiguration.RULE_HEALTH_TRENDS,
                    category = GuidanceCategory.HEALTH_TRENDS,
                    title = "Review your longitudinal health trends",
                    message = "Your health timeline contains recorded checkup data. Reviewing trends helps you spot intervals and progress over time.",
                    explanation = explanation,
                    priority = priority,
                    sources = listOf(
                        GuidanceConfiguration.SOURCE_MODULE_9B,
                        GuidanceConfiguration.SOURCE_MODULE_10
                    ),
                    actionType = GuidanceActionType.NAVIGATE_TRENDS,
                    actionLabel = "View Trends"
                )
            )
        }

        // 6. RULE: Prediction History Tracking
        totalRulesEvaluated++
        val recentPredictionCount = longitudinalSummary?.predictionActivity?.currentPeriodCount
            ?: personalContext?.predictions?.recentCount
            ?: rchr?.predictionFeatures?.recentPredictionCount
            ?: 0

        if (recentPredictionCount > 0) {
            candidates.add(
                PersonalizedGuidance(
                    id = GuidanceConfiguration.RULE_PREDICTION_TRACKING,
                    category = GuidanceCategory.HEALTH_TRACKING,
                    title = "Review recent checkup records",
                    message = "You have recorded $recentPredictionCount recent checkup inquiry session(s). Keeping track of previous checkups provides helpful context for future doctor visits.",
                    explanation = "Recent symptom checkup inquiries were saved in your prediction history.",
                    priority = GuidancePriority.LOW,
                    sources = listOf(
                        GuidanceConfiguration.SOURCE_MODULE_8,
                        GuidanceConfiguration.SOURCE_MODULE_9A
                    ),
                    actionType = GuidanceActionType.NAVIGATE_PREDICTIONS,
                    actionLabel = "Checkup History"
                )
            )
        }

        // 7. RULE: Professional Review Suggestion (Driven by Module 11 Contextual Risk)
        totalRulesEvaluated++
        val isHighRisk = riskAssessment?.riskLevel == ContextualRiskLevel.HIGH
        val isModerateWithFactors = riskAssessment?.riskLevel == ContextualRiskLevel.MODERATE &&
                (riskAssessment.positiveContributors.size >= 2)

        if (isHighRisk || isModerateWithFactors) {
            val topFactors = riskAssessment?.positiveContributors?.take(2)?.joinToString(" and ") { it.title.lowercase() }
            val factorText = if (!topFactors.isNullOrBlank()) " ($topFactors)" else ""

            candidates.add(
                PersonalizedGuidance(
                    id = GuidanceConfiguration.RULE_PROFESSIONAL_REVIEW,
                    category = GuidanceCategory.PROFESSIONAL_REVIEW,
                    title = "Discuss persistent health signals with a doctor",
                    message = "Several concurrent health factors$factorText are currently noted in your MediSense history. Discussing persistent symptoms with a qualified healthcare provider is recommended.",
                    explanation = "The health context engine identified multiple contributing factors across your recent history.",
                    priority = GuidancePriority.HIGH,
                    sources = listOf(
                        GuidanceConfiguration.SOURCE_MODULE_11,
                        GuidanceConfiguration.SOURCE_MODULE_10
                    ),
                    actionType = GuidanceActionType.NAVIGATE_RISK,
                    actionLabel = "View Health Context"
                )
            )
        }

        // 8. RULE: Baseline Setup (When user has minimal records)
        totalRulesEvaluated++
        val hasSufficientData = (personalContext?.hasSufficientData == true) ||
                (longitudinalSummary?.hasSufficientData == true) ||
                (rchr?.hasSufficientData == true) ||
                (riskAssessment?.hasSufficientData == true)

        if (candidates.isEmpty()) {
            candidates.add(
                PersonalizedGuidance(
                    id = GuidanceConfiguration.RULE_BASELINE_SETUP,
                    category = GuidanceCategory.RECORD_MAINTENANCE,
                    title = "Build your personal health context",
                    message = "Start by completing your health profile, logging medications, or tracking symptoms to receive personalized health guidance.",
                    explanation = "Limited health history is currently recorded in your account.",
                    priority = GuidancePriority.LOW,
                    sources = listOf(GuidanceConfiguration.SOURCE_MODULE_2),
                    actionType = GuidanceActionType.NAVIGATE_PROFILE,
                    actionLabel = "Set Up Profile"
                )
            )
        }

        // 9. Centralized Safety Filtering
        val safeCandidates = GuidanceSafetyFilter.filterSafeGuidance(candidates)

        // 10. Deduplication & Categorical Ranking
        // If multiple candidates share the exact same category, keep the highest priority one and merge sources
        val deduplicated = mutableListOf<PersonalizedGuidance>()
        val categoryMap = safeCandidates.groupBy { it.category }

        for ((_, items) in categoryMap) {
            val topItem = items.maxByOrNull { it.priority.level } ?: items.first()
            val allSources = items.flatMap { it.sources }.distinct()
            deduplicated.add(topItem.copy(sources = allSources))
        }

        // 11. Deterministic Priority Sorting & Capacity Capping
        val sortedGuidance = deduplicated
            .sortedWith(
                compareByDescending<PersonalizedGuidance> { it.priority.level }
                    .thenBy { it.id }
            )
            .take(GuidanceConfiguration.MAX_DISPLAYED_RECOMMENDATIONS)

        val limitationNotice = if (!hasSufficientData) {
            "Some recommendations may be general because your available health history is still being built."
        } else null

        return GuidanceEngineResult(
            userId = userId,
            guidanceList = sortedGuidance,
            totalEvaluatedRules = totalRulesEvaluated,
            generatedTimestamp = System.currentTimeMillis(),
            hasSufficientData = hasSufficientData,
            dataLimitationsNotice = limitationNotice
        )
    }
}
