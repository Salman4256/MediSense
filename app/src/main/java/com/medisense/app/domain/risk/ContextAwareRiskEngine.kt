package com.medisense.app.domain.risk

import com.medisense.app.domain.model.ContextualRiskAssessment
import com.medisense.app.domain.model.ContextualRiskCategory
import com.medisense.app.domain.model.ContextualRiskFactor
import com.medisense.app.domain.model.ContextualRiskLevel
import com.medisense.app.domain.model.FactorEffectDirection
import com.medisense.app.domain.model.LongitudinalHealthSummary
import com.medisense.app.domain.model.PersonalHealthContext
import com.medisense.app.domain.model.TrendDirection
import com.medisense.app.domain.rchr.RchrRepresentation
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

/**
 * Deterministic, interpretable Context-Aware Health Risk Engine.
 * Evaluates unified contextual signals across Modules 1–10.
 *
 * NOTE: Produces transparent application-defined contextual priority indicators.
 * DOES NOT diagnose disease, modify ML models, or prescribe medications.
 */
@Singleton
class ContextAwareRiskEngine @Inject constructor() {

    /**
     * Evaluates the contextual health priority and produces an explainable assessment.
     */
    fun evaluate(
        userId: String,
        personalContext: PersonalHealthContext?,
        longitudinalSummary: LongitudinalHealthSummary?,
        rchr: RchrRepresentation?
    ): ContextualRiskAssessment {
        // 1. Data Availability Audit
        val hasProfile = personalContext != null && personalContext.profileCompleteness > 0
        val hasPredictions = (personalContext?.predictions?.totalCount ?: 0) > 0 ||
                (longitudinalSummary?.predictionActivity?.currentPeriodCount ?: 0) > 0
        val hasMedications = (personalContext?.medications?.activeCount ?: 0) > 0 ||
                (longitudinalSummary?.adherenceTrend?.totalRecordedEvents ?: 0) > 0
        val hasAppointments = (personalContext?.appointments?.upcomingCount ?: 0) > 0 ||
                (longitudinalSummary?.appointmentActivity?.currentPeriodCount ?: 0) > 0
        val hasLongitudinal = longitudinalSummary?.hasSufficientData == true
        val hasRchr = rchr?.hasSufficientData == true

        val availabilityMap = mapOf(
            "Health Profile" to hasProfile,
            "Prediction History" to hasPredictions,
            "Medication Regimen" to hasMedications,
            "Appointments" to hasAppointments,
            "Longitudinal Trends" to hasLongitudinal,
            "Composite Health (RCHR)" to hasRchr
        )

        val activeDataCount = listOf(hasProfile, hasPredictions, hasMedications, hasAppointments, hasLongitudinal).count { it }

        // 2. Data Sufficiency Check
        if (activeDataCount < ContextualRiskConfiguration.MIN_DATA_POINTS_FOR_SUFFICIENCY && !hasPredictions) {
            val emptyFactors = createDefaultUnavailableFactors()
            return ContextualRiskAssessment(
                userId = userId,
                overallScore = null,
                riskLevel = ContextualRiskLevel.INSUFFICIENT_DATA,
                contributingFactors = emptyFactors,
                positiveContributors = emptyList(),
                neutralOrMitigatingFactors = emptyList(),
                unavailableFactors = emptyFactors,
                generatedSummary = ContextualRiskExplanationBuilder.buildAssessmentSummary(
                    ContextualRiskLevel.INSUFFICIENT_DATA,
                    null,
                    emptyFactors
                ),
                dataAvailabilitySummary = availabilityMap,
                hasSufficientData = false
            )
        }

        // 3. Deterministic Factor Evaluation
        val evaluatedFactors = mutableListOf<ContextualRiskFactor>()

        // A. Symptom Recurrence Factor (Module 9B + Module 10 confirmation)
        evaluatedFactors.add(
            evaluateSymptomRecurrenceFactor(longitudinalSummary, rchr)
        )

        // B. Recent Prediction Activity Factor (Module 8/9B + Module 9A)
        evaluatedFactors.add(
            evaluatePredictionActivityFactor(personalContext, longitudinalSummary, rchr)
        )

        // C. Chronic & Allergy Profile Factor (Module 2 + Module 9A)
        evaluatedFactors.add(
            evaluateChronicAllergyFactor(personalContext, rchr)
        )

        // D. Medication Adherence Context Factor (Module 6 + Module 9B)
        evaluatedFactors.add(
            evaluateMedicationAdherenceFactor(personalContext, longitudinalSummary, rchr)
        )

        // E. Prediction Confidence Dynamics Factor (Module 9B)
        evaluatedFactors.add(
            evaluateConfidenceDynamicsFactor(longitudinalSummary, personalContext)
        )

        // F. Appointment Context Factor (Module 7 + Module 9A)
        evaluatedFactors.add(
            evaluateAppointmentContextFactor(personalContext, longitudinalSummary)
        )

        // G. Temporal Health Patterns Factor (Module 9B)
        evaluatedFactors.add(
            evaluateTemporalPatternsFactor(longitudinalSummary, rchr)
        )

        // 4. Weighted Score Calculation
        val activeFactors = evaluatedFactors.filter { it.isAvailable }
        val overallScore: Int
        val finalLevel: ContextualRiskLevel

        if (activeFactors.isEmpty()) {
            overallScore = 0
            finalLevel = ContextualRiskLevel.INSUFFICIENT_DATA
        } else {
            val totalWeight = activeFactors.sumOf { it.weight.toDouble() }.toFloat()
            val totalWeightedSum = activeFactors.sumOf { (it.rawContributionScore * it.weight).toDouble() }.toFloat()

            val rawScore = if (totalWeight > 0f) {
                totalWeightedSum / totalWeight
            } else 0f

            overallScore = rawScore.roundToInt().coerceIn(
                ContextualRiskConfiguration.SCORE_MIN,
                ContextualRiskConfiguration.SCORE_MAX
            )

            finalLevel = when {
                overallScore <= ContextualRiskConfiguration.THRESHOLD_LOW_MAX -> ContextualRiskLevel.LOW
                overallScore <= ContextualRiskConfiguration.THRESHOLD_MODERATE_MAX -> ContextualRiskLevel.MODERATE
                else -> ContextualRiskLevel.HIGH
            }
        }

        // 5. Partition Factors
        val positiveContributors = evaluatedFactors
            .filter { it.effectDirection == FactorEffectDirection.INCREASES_SCORE }
            .sortedByDescending { it.weightedContribution }

        val neutralOrMitigating = evaluatedFactors
            .filter { it.effectDirection == FactorEffectDirection.DECREASES_SCORE || it.effectDirection == FactorEffectDirection.NEUTRAL }
            .sortedByDescending { it.weight }

        val unavailableFactors = evaluatedFactors
            .filter { it.effectDirection == FactorEffectDirection.UNAVAILABLE }

        val summary = ContextualRiskExplanationBuilder.buildAssessmentSummary(
            riskLevel = finalLevel,
            overallScore = overallScore,
            factors = evaluatedFactors
        )

        return ContextualRiskAssessment(
            userId = userId,
            overallScore = overallScore,
            riskLevel = finalLevel,
            contributingFactors = evaluatedFactors,
            positiveContributors = positiveContributors,
            neutralOrMitigatingFactors = neutralOrMitigating,
            unavailableFactors = unavailableFactors,
            generatedSummary = summary,
            dataAvailabilitySummary = availabilityMap,
            hasSufficientData = true
        )
    }

    private fun evaluateSymptomRecurrenceFactor(
        longitudinal: LongitudinalHealthSummary?,
        rchr: RchrRepresentation?
    ): ContextualRiskFactor {
        val recurringSymptoms = longitudinal?.recurringSymptoms?.filter { it.isRecurring }
            ?: emptyList()
        val rchrRecurring = rchr?.symptomFeatures?.recurringSymptoms ?: emptyList()

        // Double counting prevention: merge distinct recurring symptom names
        val combinedRecurring = (recurringSymptoms.map { it.symptomName } + rchrRecurring).distinct()

        val rawScore: Float
        val direction: FactorEffectDirection
        val detail: String

        if (combinedRecurring.isEmpty()) {
            rawScore = 0f
            direction = FactorEffectDirection.NEUTRAL
            detail = "0 recurring symptoms"
        } else if (combinedRecurring.size <= 2) {
            rawScore = 45f
            direction = FactorEffectDirection.INCREASES_SCORE
            detail = combinedRecurring.joinToString(", ")
        } else {
            rawScore = 75f
            direction = FactorEffectDirection.INCREASES_SCORE
            detail = "${combinedRecurring.size} symptoms: " + combinedRecurring.take(3).joinToString(", ")
        }

        val weight = ContextualRiskConfiguration.WEIGHT_SYMPTOM_RECURRENCE
        val weightedContrib = rawScore * weight

        return ContextualRiskFactor(
            factorId = ContextualRiskConfiguration.ID_SYMPTOM_RECURRENCE,
            category = ContextualRiskCategory.SYMPTOM_RECURRENCE,
            title = "Symptom Recurrence Patterns",
            description = "Frequency of repeated symptoms identified over historical checkups.",
            rawContributionScore = rawScore,
            weightedContribution = weightedContrib,
            weight = weight,
            source = ContextualRiskConfiguration.SOURCE_MODULE_9B,
            isAvailable = true,
            effectDirection = direction,
            explanation = ContextualRiskExplanationBuilder.buildFactorExplanation(
                ContextualRiskConfiguration.ID_SYMPTOM_RECURRENCE,
                rawScore,
                detail
            )
        )
    }

    private fun evaluatePredictionActivityFactor(
        context: PersonalHealthContext?,
        longitudinal: LongitudinalHealthSummary?,
        rchr: RchrRepresentation?
    ): ContextualRiskFactor {
        val recentCount = longitudinal?.predictionActivity?.currentPeriodCount
            ?: context?.predictions?.recentCount
            ?: rchr?.predictionFeatures?.recentPredictionCount
            ?: 0

        val totalCount = context?.predictions?.totalCount
            ?: rchr?.predictionFeatures?.totalPredictionCount
            ?: recentCount

        val isAvailable = totalCount > 0 || recentCount > 0

        val rawScore: Float
        val direction: FactorEffectDirection
        val detail: String

        if (!isAvailable) {
            rawScore = 0f
            direction = FactorEffectDirection.UNAVAILABLE
            detail = "No recorded checkup sessions"
        } else if (recentCount == 0) {
            rawScore = 10f
            direction = FactorEffectDirection.NEUTRAL
            detail = "0 in current period ($totalCount total)"
        } else if (recentCount <= 2) {
            rawScore = 35f
            direction = FactorEffectDirection.INCREASES_SCORE
            detail = "$recentCount checkup(s) recorded"
        } else {
            rawScore = 70f
            direction = FactorEffectDirection.INCREASES_SCORE
            detail = "$recentCount frequent checkup sessions"
        }

        val weight = ContextualRiskConfiguration.WEIGHT_PREDICTION_ACTIVITY
        val weightedContrib = rawScore * weight

        return ContextualRiskFactor(
            factorId = ContextualRiskConfiguration.ID_PREDICTION_ACTIVITY,
            category = ContextualRiskCategory.RECENT_HEALTH_ACTIVITY,
            title = "Recent Prediction Activity",
            description = "Volume of recent health checkup inquiries recorded in the app.",
            rawContributionScore = rawScore,
            weightedContribution = weightedContrib,
            weight = weight,
            source = ContextualRiskConfiguration.SOURCE_MODULE_8,
            isAvailable = isAvailable,
            effectDirection = direction,
            explanation = ContextualRiskExplanationBuilder.buildFactorExplanation(
                ContextualRiskConfiguration.ID_PREDICTION_ACTIVITY,
                rawScore,
                detail
            )
        )
    }

    private fun evaluateChronicAllergyFactor(
        context: PersonalHealthContext?,
        rchr: RchrRepresentation?
    ): ContextualRiskFactor {
        val chronicList = context?.chronicConditions?.conditions
            ?: rchr?.profileFeatures?.chronicConditionList
            ?: emptyList()

        val allergyList = context?.allergies?.allergiesList
            ?: rchr?.profileFeatures?.allergyList
            ?: emptyList()

        val totalCount = chronicList.size + allergyList.size
        val hasProfile = (context?.profileCompleteness ?: 0) > 0

        val rawScore: Float
        val direction: FactorEffectDirection
        val detail: String

        if (!hasProfile) {
            rawScore = 0f
            direction = FactorEffectDirection.UNAVAILABLE
            detail = "Profile not completed"
        } else if (totalCount == 0) {
            rawScore = 0f
            direction = FactorEffectDirection.NEUTRAL
            detail = "None recorded"
        } else if (totalCount <= 2) {
            rawScore = 35f
            direction = FactorEffectDirection.INCREASES_SCORE
            val names = (chronicList + allergyList).joinToString(", ")
            detail = "$totalCount item(s): $names"
        } else {
            rawScore = 65f
            direction = FactorEffectDirection.INCREASES_SCORE
            val names = (chronicList + allergyList).take(3).joinToString(", ")
            detail = "$totalCount items: $names"
        }

        val weight = ContextualRiskConfiguration.WEIGHT_CHRONIC_ALLERGY
        val weightedContrib = rawScore * weight

        return ContextualRiskFactor(
            factorId = ContextualRiskConfiguration.ID_CHRONIC_ALLERGY,
            category = ContextualRiskCategory.CHRONIC_ALLERGY_CONTEXT,
            title = "Pre-existing Conditions & Allergies",
            description = "Documented background health conditions and known sensitivities.",
            rawContributionScore = rawScore,
            weightedContribution = weightedContrib,
            weight = weight,
            source = ContextualRiskConfiguration.SOURCE_MODULE_2,
            isAvailable = hasProfile,
            effectDirection = direction,
            explanation = ContextualRiskExplanationBuilder.buildFactorExplanation(
                ContextualRiskConfiguration.ID_CHRONIC_ALLERGY,
                rawScore,
                detail
            )
        )
    }

    private fun evaluateMedicationAdherenceFactor(
        context: PersonalHealthContext?,
        longitudinal: LongitudinalHealthSummary?,
        rchr: RchrRepresentation?
    ): ContextualRiskFactor {
        val adherencePercent = longitudinal?.adherenceTrend?.currentAdherencePercentage
            ?: context?.medications?.adherencePercentage
            ?: rchr?.adherenceFeatures?.adherencePercentage

        val hasMedications = (context?.medications?.activeCount ?: 0) > 0 ||
                (rchr?.medicationFeatures?.hasActiveMedications == true)

        val rawScore: Float
        val direction: FactorEffectDirection
        val detail: String

        if (!hasMedications && adherencePercent == null) {
            rawScore = 0f
            direction = FactorEffectDirection.UNAVAILABLE
            detail = "No active medication regimen"
        } else if (adherencePercent == null) {
            rawScore = 25f
            direction = FactorEffectDirection.NEUTRAL
            detail = "Regimen active, awaiting dose logs"
        } else if (adherencePercent >= 80f) {
            rawScore = 10f
            direction = FactorEffectDirection.DECREASES_SCORE
            detail = "${adherencePercent.roundToInt()}% adherence"
        } else if (adherencePercent >= 50f) {
            rawScore = 45f
            direction = FactorEffectDirection.INCREASES_SCORE
            detail = "${adherencePercent.roundToInt()}% moderate adherence"
        } else {
            rawScore = 80f
            direction = FactorEffectDirection.INCREASES_SCORE
            detail = "${adherencePercent.roundToInt()}% low adherence"
        }

        val weight = ContextualRiskConfiguration.WEIGHT_MEDICATION_ADHERENCE
        val weightedContrib = rawScore * weight

        return ContextualRiskFactor(
            factorId = ContextualRiskConfiguration.ID_MEDICATION_ADHERENCE,
            category = ContextualRiskCategory.MEDICATION_ADHERENCE,
            title = "Medication Adherence Context",
            description = "Regularity of recorded prescription schedule adherence.",
            rawContributionScore = rawScore,
            weightedContribution = weightedContrib,
            weight = weight,
            source = ContextualRiskConfiguration.SOURCE_MODULE_6,
            isAvailable = hasMedications || adherencePercent != null,
            effectDirection = direction,
            explanation = ContextualRiskExplanationBuilder.buildFactorExplanation(
                ContextualRiskConfiguration.ID_MEDICATION_ADHERENCE,
                rawScore,
                detail
            )
        )
    }

    private fun evaluateConfidenceDynamicsFactor(
        longitudinal: LongitudinalHealthSummary?,
        context: PersonalHealthContext?
    ): ContextualRiskFactor {
        val trend = longitudinal?.confidenceTrend?.direction ?: TrendDirection.STABLE
        val avgConfidence = longitudinal?.confidenceTrend?.currentAvgConfidence
            ?: context?.predictions?.avgConfidence

        val isAvailable = avgConfidence != null

        val rawScore: Float
        val direction: FactorEffectDirection
        val detail: String

        if (!isAvailable) {
            rawScore = 0f
            direction = FactorEffectDirection.UNAVAILABLE
            detail = "Insufficient prediction history"
        } else {
            val pct = (avgConfidence!! * 100).roundToInt()
            when (trend) {
                TrendDirection.INCREASING, TrendDirection.DECLINING -> {
                    rawScore = 60f
                    direction = FactorEffectDirection.INCREASES_SCORE
                    detail = "$pct% avg consistency (trending up)"
                }
                TrendDirection.STABLE, TrendDirection.INSUFFICIENT_DATA -> {
                    rawScore = 30f
                    direction = FactorEffectDirection.NEUTRAL
                    detail = "$pct% avg consistency (stable)"
                }
                TrendDirection.DECREASING, TrendDirection.IMPROVING -> {
                    rawScore = 15f
                    direction = FactorEffectDirection.DECREASES_SCORE
                    detail = "$pct% avg consistency (trending down)"
                }
            }
        }

        val weight = ContextualRiskConfiguration.WEIGHT_CONFIDENCE_DYNAMICS
        val weightedContrib = rawScore * weight

        return ContextualRiskFactor(
            factorId = ContextualRiskConfiguration.ID_CONFIDENCE_DYNAMICS,
            category = ContextualRiskCategory.CONFIDENCE_DYNAMICS,
            title = "Prediction Confidence Dynamics",
            description = "Statistical consistency across historical prediction outputs.",
            rawContributionScore = rawScore,
            weightedContribution = weightedContrib,
            weight = weight,
            source = ContextualRiskConfiguration.SOURCE_MODULE_9B,
            isAvailable = isAvailable,
            effectDirection = direction,
            explanation = ContextualRiskExplanationBuilder.buildFactorExplanation(
                ContextualRiskConfiguration.ID_CONFIDENCE_DYNAMICS,
                rawScore,
                detail
            )
        )
    }

    private fun evaluateAppointmentContextFactor(
        context: PersonalHealthContext?,
        longitudinal: LongitudinalHealthSummary?
    ): ContextualRiskFactor {
        val upcomingCount = context?.appointments?.upcomingCount
            ?: longitudinal?.appointmentActivity?.upcomingCount
            ?: 0

        val nextDate = context?.appointments?.nextAppointmentDate
        val nextDoc = context?.appointments?.nextAppointmentDoctor

        val rawScore: Float
        val direction: FactorEffectDirection
        val detail: String

        if (upcomingCount > 0) {
            rawScore = 15f
            direction = FactorEffectDirection.DECREASES_SCORE
            detail = if (nextDoc != null && nextDate != null) {
                "$upcomingCount upcoming ($nextDoc on $nextDate)"
            } else {
                "$upcomingCount upcoming appointment(s)"
            }
        } else {
            rawScore = 35f
            direction = FactorEffectDirection.NEUTRAL
            detail = "No upcoming follow-ups scheduled"
        }

        val weight = ContextualRiskConfiguration.WEIGHT_APPOINTMENT_CONTEXT
        val weightedContrib = rawScore * weight

        return ContextualRiskFactor(
            factorId = ContextualRiskConfiguration.ID_APPOINTMENT_CONTEXT,
            category = ContextualRiskCategory.APPOINTMENT_CONTEXT,
            title = "Clinical Appointment Context",
            description = "Scheduled healthcare provider checkups and proactive consultations.",
            rawContributionScore = rawScore,
            weightedContribution = weightedContrib,
            weight = weight,
            source = ContextualRiskConfiguration.SOURCE_MODULE_7,
            isAvailable = true,
            effectDirection = direction,
            explanation = ContextualRiskExplanationBuilder.buildFactorExplanation(
                ContextualRiskConfiguration.ID_APPOINTMENT_CONTEXT,
                rawScore,
                detail
            )
        )
    }

    private fun evaluateTemporalPatternsFactor(
        longitudinal: LongitudinalHealthSummary?,
        rchr: RchrRepresentation?
    ): ContextualRiskFactor {
        val patterns = longitudinal?.detectedPatterns ?: emptyList()
        val patternCount = patterns.size.coerceAtLeast(rchr?.temporalFeatures?.detectedPatternsCount ?: 0)

        val rawScore: Float
        val direction: FactorEffectDirection
        val detail: String

        if (patternCount == 0) {
            rawScore = 0f
            direction = FactorEffectDirection.NEUTRAL
            detail = "No recurring temporal patterns"
        } else if (patternCount == 1) {
            rawScore = 40f
            direction = FactorEffectDirection.INCREASES_SCORE
            val title = patterns.firstOrNull()?.title ?: "1 temporal pattern"
            detail = title
        } else {
            rawScore = 70f
            direction = FactorEffectDirection.INCREASES_SCORE
            detail = "$patternCount patterns detected"
        }

        val weight = ContextualRiskConfiguration.WEIGHT_TEMPORAL_PATTERNS
        val weightedContrib = rawScore * weight

        return ContextualRiskFactor(
            factorId = ContextualRiskConfiguration.ID_TEMPORAL_PATTERNS,
            category = ContextualRiskCategory.TEMPORAL_PATTERNS,
            title = "Temporal Trends & Variability",
            description = "Cyclic or progressive health patterns detected across timelines.",
            rawContributionScore = rawScore,
            weightedContribution = weightedContrib,
            weight = weight,
            source = ContextualRiskConfiguration.SOURCE_MODULE_9B,
            isAvailable = true,
            effectDirection = direction,
            explanation = ContextualRiskExplanationBuilder.buildFactorExplanation(
                ContextualRiskConfiguration.ID_TEMPORAL_PATTERNS,
                rawScore,
                detail
            )
        )
    }

    private fun createDefaultUnavailableFactors(): List<ContextualRiskFactor> {
        val factors = mutableListOf<ContextualRiskFactor>()
        val categories = listOf(
            Triple(ContextualRiskConfiguration.ID_SYMPTOM_RECURRENCE, ContextualRiskCategory.SYMPTOM_RECURRENCE, ContextualRiskConfiguration.WEIGHT_SYMPTOM_RECURRENCE),
            Triple(ContextualRiskConfiguration.ID_PREDICTION_ACTIVITY, ContextualRiskCategory.RECENT_HEALTH_ACTIVITY, ContextualRiskConfiguration.WEIGHT_PREDICTION_ACTIVITY),
            Triple(ContextualRiskConfiguration.ID_CHRONIC_ALLERGY, ContextualRiskCategory.CHRONIC_ALLERGY_CONTEXT, ContextualRiskConfiguration.WEIGHT_CHRONIC_ALLERGY),
            Triple(ContextualRiskConfiguration.ID_MEDICATION_ADHERENCE, ContextualRiskCategory.MEDICATION_ADHERENCE, ContextualRiskConfiguration.WEIGHT_MEDICATION_ADHERENCE),
            Triple(ContextualRiskConfiguration.ID_CONFIDENCE_DYNAMICS, ContextualRiskCategory.CONFIDENCE_DYNAMICS, ContextualRiskConfiguration.WEIGHT_CONFIDENCE_DYNAMICS),
            Triple(ContextualRiskConfiguration.ID_APPOINTMENT_CONTEXT, ContextualRiskCategory.APPOINTMENT_CONTEXT, ContextualRiskConfiguration.WEIGHT_APPOINTMENT_CONTEXT),
            Triple(ContextualRiskConfiguration.ID_TEMPORAL_PATTERNS, ContextualRiskCategory.TEMPORAL_PATTERNS, ContextualRiskConfiguration.WEIGHT_TEMPORAL_PATTERNS)
        )

        for ((id, cat, weight) in categories) {
            factors.add(
                ContextualRiskFactor(
                    factorId = id,
                    category = cat,
                    title = cat.displayName,
                    description = "Health indicator currently has insufficient history.",
                    rawContributionScore = 0f,
                    weightedContribution = 0f,
                    weight = weight,
                    source = "MediSense Unified Data",
                    isAvailable = false,
                    effectDirection = FactorEffectDirection.UNAVAILABLE,
                    explanation = "Not enough recorded data to evaluate this factor."
                )
            )
        }
        return factors
    }
}
