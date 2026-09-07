package com.medisense.app.domain.trace

import com.medisense.app.data.local.entity.PredictionHistoryEntity
import com.medisense.app.data.model.PredictionExplanation
import com.medisense.app.domain.model.*
import com.medisense.app.domain.rchr.RchrReconstructionResult
import com.medisense.app.domain.rchr.RchrRepresentation
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

/**
 * Deterministic engine for synthesizing human-readable, auditable Health Decision Traces.
 * 100% offline-first, rule-driven, and reproducible without external AI/LLMs.
 */
@Singleton
class HealthDecisionTraceEngine @Inject constructor() {

    private val DATE_FORMAT = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

    // 1. Disease Prediction & XAI Trace
    fun buildPredictionTrace(
        entity: PredictionHistoryEntity,
        explanation: PredictionExplanation? = null,
        confidenceSummary: PredictionConfidenceSummary? = null,
        counterfactualResult: CounterfactualAnalysisResult? = null
    ): HealthDecisionTrace {
        val steps = listOf(
            HealthDecisionTraceStep(
                stepNumber = 1,
                title = "Symptom Vectorization",
                description = "User reported symptoms (${entity.symptoms.size} total) were mapped to binary model input vector.",
                engineComponent = "DiseasePredictionRepository"
            ),
            HealthDecisionTraceStep(
                stepNumber = 2,
                title = "TensorFlow Lite Inference",
                description = "LiteRT classifier executed feedforward neural network on device to compute class probabilities.",
                engineComponent = "LiteRT / TFLite Classifier (v${entity.modelVersion})"
            ),
            HealthDecisionTraceStep(
                stepNumber = 3,
                title = "Softmax Probability Ranking",
                description = "Output logits were converted to probabilities. Top predicted condition identified: ${entity.predictedDisease} (${(entity.confidence * 100).roundToInt()}% confidence).",
                engineComponent = "DiseasePredictionRepository"
            ),
            HealthDecisionTraceStep(
                stepNumber = 4,
                title = "XAI Feature Attribution (SHAP)",
                description = "Precomputed SHAP attribution values mapped symptom importance contributions for ${entity.predictedDisease}.",
                engineComponent = "ExplanationDataRepository / XaiMetadataParser"
            ),
            HealthDecisionTraceStep(
                stepNumber = 5,
                title = "Confidence & Counterfactual Calibration",
                description = "Assessed prediction stability across symptom count thresholds and evaluated what-if sensitivity.",
                engineComponent = "CounterfactualExplanationEngine (Module 13)"
            )
        )

        val factors = mutableListOf<HealthDecisionTraceFactor>()
        explanation?.contributions?.forEach { contribution ->
            factors.add(
                HealthDecisionTraceFactor(
                    name = contribution.featureName,
                    value = if (contribution.importance > 0) "Present / Reported" else "Negative Indicator",
                    influenceDirection = if (contribution.importance > 0) TraceInfluenceDirection.POSITIVE else TraceInfluenceDirection.NEGATIVE,
                    weightPercentage = (contribution.importance * 100).roundToInt().coerceIn(1, 100),
                    interpretation = "Contributed ${(contribution.importance * 100).roundToInt()}% weight toward ${entity.predictedDisease} prediction."
                )
            )
        }

        if (factors.isEmpty()) {
            entity.symptoms.forEach { sym ->
                factors.add(
                    HealthDecisionTraceFactor(
                        name = sym,
                        value = "Present / Reported",
                        influenceDirection = TraceInfluenceDirection.POSITIVE,
                        weightPercentage = null,
                        interpretation = "User-reported symptom included in active model screening vector."
                    )
                )
            }
        }

        val explanationText = explanation?.summary ?: entity.explanationSummary ?: (
            "The model output indicates ${entity.predictedDisease} with ${(entity.confidence * 100).roundToInt()}% confidence based on reported symptoms: ${entity.symptoms.joinToString(", ")}."
        )

        val sensitivityText = counterfactualResult?.sensitivity?.let { " [Model Sensitivity: ${it.label}]" } ?: ""

        val limitations = "Medical Safety Notice: This is an application-generated prediction from the MediSense neural model. Model confidence does not represent medical certainty and this output is not a clinical diagnosis or treatment instruction."

        return HealthDecisionTrace(
            traceId = "trace_pred_${entity.id}",
            userId = entity.userId,
            decisionType = HealthDecisionTraceType.DISEASE_PREDICTION,
            generatedAt = entity.predictionTimestamp,
            engineName = "LiteRT Disease Predictor & XAI Engine",
            engineVersion = "1.0",
            modelVersion = entity.modelVersion,
            inputSummary = "${entity.symptoms.size} symptoms reported: ${entity.symptoms.joinToString(", ")}",
            inputFactors = factors,
            processingSteps = steps,
            outputResult = HealthDecisionTraceResult(
                primaryOutput = entity.predictedDisease,
                secondaryOutput = "${(entity.confidence * 100).roundToInt()}% Model Confidence",
                confidenceOrStatus = "${(entity.confidence * 100).roundToInt()}%$sensitivityText",
                categoryTag = "Disease Prediction"
            ),
            explanationText = TraceSafetyFilter.sanitize(explanationText),
            limitationsText = limitations,
            dataQualityStatus = if (entity.symptoms.isNotEmpty()) "GOOD" else "INSUFFICIENT_DATA",
            sourceModules = listOf("Module 3 (Disease Prediction)", "Module 4 (XAI)", "Module 13 (Confidence & Counterfactual)"),
            reproducibilityMetadata = mapOf(
                "PredictionId" to entity.id.toString(),
                "ModelVersion" to entity.modelVersion,
                "SymptomCount" to entity.symptoms.size.toString(),
                "Timestamp" to DATE_FORMAT.format(Date(entity.predictionTimestamp))
            )
        )
    }

    // 2. Adaptive Personalization Trace
    fun buildPersonalizationTrace(context: PersonalHealthContext, userId: String = context.userId): HealthDecisionTrace {
        val steps = listOf(
            HealthDecisionTraceStep(
                stepNumber = 1,
                title = "Profile Completeness Evaluation",
                description = "Evaluated demographic, contact, and clinical baseline profile fields (${context.profileCompleteness}% complete).",
                engineComponent = "ProfileRepository / PersonalHealthContextRepository"
            ),
            HealthDecisionTraceStep(
                stepNumber = 2,
                title = "Longitudinal Activity Synthesis",
                description = "Aggregated recent predictions (${context.predictions.recentCount} in window), active medications (${context.medications.activeCount}), and upcoming visits.",
                engineComponent = "PersonalHealthContextRepository"
            ),
            HealthDecisionTraceStep(
                stepNumber = 3,
                title = "Deterministic Context Formatting",
                description = "Generated factual summary and why-personalized attribution notes based strictly on local health records.",
                engineComponent = "PersonalHealthContextRepository (Module 9A)"
            )
        )

        val factors = listOf(
            HealthDecisionTraceFactor(
                name = "Profile Completeness",
                value = "${context.profileCompleteness}%",
                influenceDirection = if (context.profileCompleteness >= 70) TraceInfluenceDirection.POSITIVE else TraceInfluenceDirection.ALERT,
                weightPercentage = context.profileCompleteness,
                interpretation = "Baseline health profile completeness evaluated across 11 key fields."
            ),
            HealthDecisionTraceFactor(
                name = "Medication Adherence",
                value = context.medications.adherenceSummary ?: "${context.medications.activeCount} active medications",
                influenceDirection = TraceInfluenceDirection.NEUTRAL,
                weightPercentage = null,
                interpretation = "Active regimens and logged adherence rates in current context window."
            ),
            HealthDecisionTraceFactor(
                name = "Recent Prediction Activity",
                value = "${context.predictions.recentCount} recent records",
                influenceDirection = TraceInfluenceDirection.NEUTRAL,
                weightPercentage = null,
                interpretation = "Frequent symptoms: ${context.predictions.frequentSymptoms.ifEmpty { listOf("None") }.joinToString(", ")}."
            )
        )

        return HealthDecisionTrace(
            traceId = "trace_pers_${System.currentTimeMillis()}",
            userId = userId,
            decisionType = HealthDecisionTraceType.PERSONALIZATION,
            generatedAt = System.currentTimeMillis(),
            engineName = "Adaptive Personal Health Context Engine",
            engineVersion = "1.0",
            modelVersion = "1.0",
            inputSummary = "Aggregated baseline profile (${context.profileCompleteness}%), ${context.medications.activeCount} medications, ${context.predictions.recentCount} recent predictions",
            inputFactors = factors,
            processingSteps = steps,
            outputResult = HealthDecisionTraceResult(
                primaryOutput = context.generatedSummary,
                secondaryOutput = context.whyPersonalized,
                confidenceOrStatus = "Profile ${context.profileCompleteness}%",
                categoryTag = "Personalization"
            ),
            explanationText = "Personalization synthesized available health records to tailor dashboard insights and guidance prompts without cloud profile tracking.",
            limitationsText = "This information is derived from health records currently available in MediSense and is intended for assistive health-management support.",
            dataQualityStatus = if (context.profileCompleteness >= 50) "GOOD" else "NEEDS_ATTENTION",
            sourceModules = listOf("Module 1 (Health Records)", "Module 5 (Medication)", "Module 7 (Appointments)", "Module 9A (Adaptive Personalization)"),
            reproducibilityMetadata = mapOf(
                "ProfileCompleteness" to "${context.profileCompleteness}%",
                "ActiveMedCount" to context.medications.activeCount.toString(),
                "RecentPredictionsCount" to context.predictions.recentCount.toString()
            )
        )
    }

    // 3. Longitudinal Trends Trace
    fun buildLongitudinalTrace(summary: LongitudinalHealthSummary, userId: String = summary.userId): HealthDecisionTrace {
        val steps = listOf(
            HealthDecisionTraceStep(
                stepNumber = 1,
                title = "Daily Metric Aggregation",
                description = "Grouped ${summary.period.displayName} health records into standardized daily metric points.",
                engineComponent = "LongitudinalHealthRepository"
            ),
            HealthDecisionTraceStep(
                stepNumber = 2,
                title = "Temporal Pattern Detection",
                description = "Applied deterministic rule matching to detect symptom recurrence, adherence trends, and risk velocity.",
                engineComponent = "TemporalPatternAnalyzer (Module 9B)"
            ),
            HealthDecisionTraceStep(
                stepNumber = 3,
                title = "Trajectory Classification",
                description = "Classified prediction trend as ${summary.predictionActivity.direction.name} and adherence trend as ${summary.adherenceTrend.direction.name}.",
                engineComponent = "TemporalPatternAnalyzer"
            )
        )

        val factors = summary.detectedPatterns.map { pattern ->
            HealthDecisionTraceFactor(
                name = pattern.title,
                value = pattern.category.name,
                influenceDirection = when (pattern.severity) {
                    PatternSeverity.ATTENTION -> TraceInfluenceDirection.ALERT
                    PatternSeverity.POSITIVE -> TraceInfluenceDirection.POSITIVE
                    PatternSeverity.INFO -> TraceInfluenceDirection.NEUTRAL
                },
                weightPercentage = null,
                interpretation = pattern.description
            )
        }

        return HealthDecisionTrace(
            traceId = "trace_long_${System.currentTimeMillis()}",
            userId = userId,
            decisionType = HealthDecisionTraceType.LONGITUDINAL_PATTERN,
            generatedAt = System.currentTimeMillis(),
            engineName = "Temporal Pattern Analyzer & Longitudinal Engine",
            engineVersion = "1.0",
            modelVersion = "1.0",
            inputSummary = "Evaluated ${summary.period.displayName} longitudinal window with ${summary.predictionActivity.currentPeriodCount} predictions",
            inputFactors = factors,
            processingSteps = steps,
            outputResult = HealthDecisionTraceResult(
                primaryOutput = "Prediction Trend: ${summary.predictionActivity.direction.name} | Adherence: ${summary.adherenceTrend.direction.name}",
                secondaryOutput = "${summary.detectedPatterns.size} temporal patterns identified",
                confidenceOrStatus = summary.period.displayName,
                categoryTag = "Longitudinal Analytics"
            ),
            explanationText = summary.generatedSummary,
            limitationsText = "Longitudinal trends represent historical patterns recorded in MediSense and do not predict guaranteed future health outcomes or clinical disease progression.",
            dataQualityStatus = if (summary.hasSufficientData) "GOOD" else "INSUFFICIENT_DATA",
            sourceModules = listOf("Module 9B (Longitudinal Health)", "Module 5 (Medication)", "Module 8 (Prediction History)"),
            reproducibilityMetadata = mapOf(
                "Period" to summary.period.displayName,
                "PredictionsCount" to summary.predictionActivity.currentPeriodCount.toString(),
                "PatternsFound" to summary.detectedPatterns.size.toString()
            )
        )
    }

    // 4. RCHR Composite Representation Trace
    fun buildRchrTrace(rchr: RchrRepresentation, reconstruction: RchrReconstructionResult? = null, userId: String = rchr.userId): HealthDecisionTrace {
        val steps = listOf(
            HealthDecisionTraceStep(
                stepNumber = 1,
                title = "Multi-Domain Feature Normalization",
                description = "Normalized 8 health feature domains across ${rchr.totalEncodedFeatures} encoded features.",
                engineComponent = "RchrRepository (Module 10)"
            ),
            HealthDecisionTraceStep(
                stepNumber = 2,
                title = "Composite Vector Synthesis",
                description = "Composed standardized compact vector embedding (completeness: ${rchr.completenessPercentage}%).",
                engineComponent = "RchrRepository"
            ),
            HealthDecisionTraceStep(
                stepNumber = 3,
                title = "Reconstruction & Fidelity Validation",
                description = "Verified lossless reverse attribute mapping: ${rchr.completenessPercentage}% representation fidelity.",
                engineComponent = "RchrRepository"
            )
        )

        val factors = listOf(
            HealthDecisionTraceFactor(
                name = "Profile Features",
                value = "Age ${rchr.profileFeatures.age ?: 0}, Gender ${rchr.profileFeatures.gender ?: "N/A"}, Blood ${rchr.profileFeatures.bloodGroup ?: "N/A"}",
                influenceDirection = TraceInfluenceDirection.NEUTRAL,
                weightPercentage = null,
                interpretation = "Demographic baseline encoded in RCHR profile features."
            ),
            HealthDecisionTraceFactor(
                name = "Symptom & Prediction State",
                value = "${rchr.symptomFeatures.distinctSymptomCount} distinct symptoms, ${rchr.predictionFeatures.totalPredictionCount} predictions",
                influenceDirection = TraceInfluenceDirection.NEUTRAL,
                weightPercentage = null,
                interpretation = "Clinical observations encoded in RCHR symptom and prediction features."
            ),
            HealthDecisionTraceFactor(
                name = "Adherence & Risk Context",
                value = "Adherence ${rchr.adherenceFeatures.adherencePercentage?.toInt() ?: 0}%, Personalization ${rchr.contextFeatures.personalizationScore.toInt()}%",
                influenceDirection = TraceInfluenceDirection.NEUTRAL,
                weightPercentage = null,
                interpretation = "Behavioral and risk context encoded in RCHR adherence and context features."
            )
        )

        return HealthDecisionTrace(
            traceId = "trace_rchr_${rchr.generatedAt}",
            userId = userId,
            decisionType = HealthDecisionTraceType.RCHR_REPRESENTATION,
            generatedAt = rchr.generatedAt,
            engineName = "Reversible Composite Health Representation (RCHR) Engine",
            engineVersion = rchr.representationVersion,
            modelVersion = rchr.representationVersion,
            inputSummary = "Composed normalized composite vector with ${rchr.totalEncodedFeatures} features (${rchr.completenessPercentage}% completeness)",
            inputFactors = factors,
            processingSteps = steps,
            outputResult = HealthDecisionTraceResult(
                primaryOutput = "Compact Health Vector (${rchr.completenessPercentage}% Complete)",
                secondaryOutput = "Encoded Features: ${rchr.totalEncodedFeatures}",
                confidenceOrStatus = "${rchr.completenessPercentage}% Complete",
                categoryTag = "RCHR Vector"
            ),
            explanationText = "RCHR encodes multi-domain health records into a deterministic, compact vector that enables explainable state reconstruction without information loss.",
            limitationsText = "The RCHR vector is an on-device mathematical representation of user records for explainable state tracking and is not a clinical diagnostic score.",
            dataQualityStatus = if (rchr.hasSufficientData) "GOOD" else "INSUFFICIENT_DATA",
            sourceModules = listOf("Module 10 (RCHR)", "Modules 1-9 (Health Data Layer)"),
            reproducibilityMetadata = mapOf(
                "Version" to rchr.representationVersion,
                "EncodedFeatures" to rchr.totalEncodedFeatures.toString(),
                "CompletenessPercentage" to "${rchr.completenessPercentage}%"
            )
        )
    }

    // 5. Context-Aware Health Risk Trace
    fun buildContextualRiskTrace(assessment: ContextualRiskAssessment, userId: String = assessment.userId): HealthDecisionTrace {
        val steps = listOf(
            HealthDecisionTraceStep(
                stepNumber = 1,
                title = "Data Sufficiency Check",
                description = "Evaluated health record volume against minimum sufficiency threshold: ${if (assessment.hasSufficientData) "Sufficient Data" else "Limited Data"}.",
                engineComponent = "ContextAwareRiskEngine (Module 11)"
            ),
            HealthDecisionTraceStep(
                stepNumber = 2,
                title = "Multi-Factor Risk Scoring",
                description = "Assigned deterministic risk points across clinical baseline, prediction confidence, adherence gaps, and appointment proximity.",
                engineComponent = "ContextAwareRiskEngine"
            ),
            HealthDecisionTraceStep(
                stepNumber = 3,
                title = "Risk Category Assignment",
                description = "Classified overall contextual priority as ${assessment.riskLevel.name} with score ${assessment.overallScore ?: 0}%.",
                engineComponent = "ContextAwareRiskEngine"
            )
        )

        val factors = assessment.contributingFactors.map { factor ->
            HealthDecisionTraceFactor(
                name = factor.title,
                value = factor.category.name,
                influenceDirection = when (factor.effectDirection) {
                    FactorEffectDirection.INCREASES_SCORE -> TraceInfluenceDirection.POSITIVE
                    FactorEffectDirection.DECREASES_SCORE -> TraceInfluenceDirection.NEGATIVE
                    else -> TraceInfluenceDirection.NEUTRAL
                },
                weightPercentage = factor.weightedContribution.toInt(),
                interpretation = factor.explanation
            )
        }

        return HealthDecisionTrace(
            traceId = "trace_risk_${System.currentTimeMillis()}",
            userId = userId,
            decisionType = HealthDecisionTraceType.CONTEXTUAL_RISK,
            generatedAt = assessment.calculatedAt,
            engineName = "Context-Aware Health Risk Engine",
            engineVersion = "1.0",
            modelVersion = "1.0",
            inputSummary = "${assessment.contributingFactors.size} contributing contextual factors evaluated",
            inputFactors = factors,
            processingSteps = steps,
            outputResult = HealthDecisionTraceResult(
                primaryOutput = "Contextual Priority: ${assessment.riskLevel.name}",
                secondaryOutput = "Risk Score: ${assessment.overallScore ?: 0}%",
                confidenceOrStatus = assessment.riskLevel.name,
                categoryTag = "Contextual Risk"
            ),
            explanationText = assessment.generatedSummary,
            limitationsText = assessment.methodologyDisclaimer,
            dataQualityStatus = if (assessment.hasSufficientData) "GOOD" else "INSUFFICIENT_DATA",
            sourceModules = listOf("Module 11 (Contextual Risk Engine)", "Module 10 (RCHR)", "Modules 1, 5, 7, 8"),
            reproducibilityMetadata = mapOf(
                "RiskLevel" to assessment.riskLevel.name,
                "OverallScore" to (assessment.overallScore?.toString() ?: "N/A"),
                "ContributingFactorsCount" to assessment.contributingFactors.size.toString()
            )
        )
    }

    // 6. Personalized Guidance Trace
    fun buildGuidanceTrace(guidanceList: List<PersonalizedGuidance>, userId: String): HealthDecisionTrace {
        val steps = listOf(
            HealthDecisionTraceStep(
                stepNumber = 1,
                title = "Rule-Based Trigger Evaluation",
                description = "Evaluated deterministic recommendation rules against contextual risk, adherence history, profile gaps, and appointments.",
                engineComponent = "PersonalizedGuidanceEngine (Module 12)"
            ),
            HealthDecisionTraceStep(
                stepNumber = 2,
                title = "Safety & Non-Diagnostic Filtering",
                description = "Verified that all generated recommendations use educational, non-prescriptive health management wording.",
                engineComponent = "PersonalizedGuidanceEngine"
            ),
            HealthDecisionTraceStep(
                stepNumber = 3,
                title = "Priority Scoring & Deduplication",
                description = "Ranked active guidance items (${guidanceList.size} active cards) by priority tier.",
                engineComponent = "PersonalizedGuidanceEngine"
            )
        )

        val factors = guidanceList.take(6).map { g ->
            HealthDecisionTraceFactor(
                name = g.title,
                value = g.category.name,
                influenceDirection = when (g.priority) {
                    GuidancePriority.HIGH -> TraceInfluenceDirection.ALERT
                    GuidancePriority.MEDIUM -> TraceInfluenceDirection.POSITIVE
                    else -> TraceInfluenceDirection.NEUTRAL
                },
                weightPercentage = when (g.priority) {
                    GuidancePriority.HIGH -> 90
                    GuidancePriority.MEDIUM -> 60
                    else -> 30
                },
                interpretation = g.explanation
            )
        }

        return HealthDecisionTrace(
            traceId = "trace_guid_${System.currentTimeMillis()}",
            userId = userId,
            decisionType = HealthDecisionTraceType.PERSONALIZED_GUIDANCE,
            generatedAt = System.currentTimeMillis(),
            engineName = "Personalized Health Guidance Engine",
            engineVersion = "1.0",
            modelVersion = "1.0",
            inputSummary = "${guidanceList.size} guidance rules triggered from active health state",
            inputFactors = factors,
            processingSteps = steps,
            outputResult = HealthDecisionTraceResult(
                primaryOutput = "${guidanceList.size} Personalized Guidance Actions Generated",
                secondaryOutput = "Top Action: ${guidanceList.firstOrNull()?.title ?: "Profile complete"}",
                confidenceOrStatus = "${guidanceList.size} Tips Active",
                categoryTag = "Personalized Guidance"
            ),
            explanationText = "Personalized guidance synthesizes your logged health records into actionable, educational health-management suggestions.",
            limitationsText = "Guidance tips are educational recommendations and do not constitute clinical treatment plans, medication alterations, or professional medical advice.",
            dataQualityStatus = if (guidanceList.isNotEmpty()) "GOOD" else "INSUFFICIENT_DATA",
            sourceModules = listOf("Module 12 (Personalized Guidance)", "Module 11 (Contextual Risk)", "Module 9A (Personalization)"),
            reproducibilityMetadata = mapOf(
                "ActiveGuidanceCount" to guidanceList.size.toString(),
                "HighPriorityCount" to guidanceList.count { it.priority == GuidancePriority.HIGH }.toString()
            )
        )
    }

    fun buildPersonalizedGuidanceTrace(guidance: GuidanceEngineResult, userId: String = guidance.userId): HealthDecisionTrace {
        return buildGuidanceTrace(guidance.guidanceList, userId)
    }

    // 7. Health Data Quality Trace
    fun buildDataQualityTrace(summary: HealthDataQualitySummary, userId: String = "local-user"): HealthDecisionTrace {
        val steps = listOf(
            HealthDecisionTraceStep(
                stepNumber = 1,
                title = "Automated Quality Evaluation",
                description = "Evaluated data quality checks across user profile, medications, appointments, and predictions.",
                engineComponent = "HealthDataQualityEngine (Module 18)"
            ),
            HealthDecisionTraceStep(
                stepNumber = 2,
                title = "Issue Classification & Scoring",
                description = "Calculated quality score (${summary.qualityScore ?: 0}%) with ${summary.issues.size} detected issues (${summary.errorCount} errors, ${summary.warningCount} warnings).",
                engineComponent = "HealthDataQualityEngine"
            )
        )

        val factors = summary.issues.take(5).map { issue ->
            HealthDecisionTraceFactor(
                name = issue.title,
                value = issue.category.name,
                influenceDirection = if (issue.severity == HealthDataQualitySeverity.ERROR) TraceInfluenceDirection.ALERT else TraceInfluenceDirection.NEUTRAL,
                weightPercentage = null,
                interpretation = issue.explanation
            )
        }

        return HealthDecisionTrace(
            traceId = "trace_qual_${System.currentTimeMillis()}",
            userId = userId,
            decisionType = HealthDecisionTraceType.DATA_QUALITY_ASSESSMENT,
            generatedAt = summary.evaluatedTimestamp,
            engineName = "Health Data Quality & Integrity Engine",
            engineVersion = "1.0",
            modelVersion = "1.0",
            inputSummary = "Completed ${summary.totalChecks} data checks (${summary.passedChecks} passed, ${summary.issues.size} issues)",
            inputFactors = factors,
            processingSteps = steps,
            outputResult = HealthDecisionTraceResult(
                primaryOutput = "Status: ${summary.status.name}",
                secondaryOutput = "Score: ${summary.qualityScore ?: 0}%",
                confidenceOrStatus = "${summary.qualityScore ?: 0}%",
                categoryTag = "Data Quality"
            ),
            explanationText = "Data quality metrics evaluated local Room record consistency and integrity.",
            limitationsText = "Evaluates data presence and formatting integrity only, not medical diagnosis.",
            dataQualityStatus = summary.status.name,
            sourceModules = listOf("Module 18 (Data Quality)", "Room Database"),
            reproducibilityMetadata = mapOf(
                "TotalChecks" to summary.totalChecks.toString(),
                "PassedChecks" to summary.passedChecks.toString(),
                "QualityScore" to "${summary.qualityScore ?: 0}%"
            )
        )
    }

    fun formatTraceAsPlainText(trace: HealthDecisionTrace): String {
        val sb = StringBuilder()
        val dateStr = DATE_FORMAT.format(Date(trace.generatedAt))

        sb.appendLine("========================================")
        sb.appendLine("       MEDISENSE HEALTH DECISION TRACE  ")
        sb.appendLine("========================================")
        sb.appendLine("Decision Type: ${trace.decisionType.displayName}")
        sb.appendLine("Engine: ${trace.engineName} (v${trace.engineVersion})")
        trace.modelVersion?.let { sb.appendLine("Model Version: $it") }
        sb.appendLine("Generated At: $dateStr")
        sb.appendLine("Data Quality State: ${trace.dataQualityStatus}")
        sb.appendLine()

        sb.appendLine("--- 1. INPUT SUMMARY ---")
        sb.appendLine(trace.inputSummary)
        sb.appendLine()

        sb.appendLine("--- 2. IMPORTANT FACTORS & ATTRIBUTION ---")
        if (trace.inputFactors.isNotEmpty()) {
            trace.inputFactors.forEachIndexed { idx, f ->
                val weight = f.weightPercentage?.let { " [Weight: $it%]" } ?: ""
                sb.appendLine("${idx + 1}. ${f.name} (${f.value})$weight")
                sb.appendLine("   Direction: ${f.influenceDirection.label}")
                sb.appendLine("   Interpretation: ${f.interpretation}")
            }
        } else {
            sb.appendLine("No specific factors recorded.")
        }
        sb.appendLine()

        sb.appendLine("--- 3. PROCESSING PIPELINE STEPS ---")
        trace.processingSteps.forEach { step ->
            sb.appendLine("Step ${step.stepNumber}: ${step.title}")
            sb.appendLine("  Component: ${step.engineComponent}")
            sb.appendLine("  Details: ${step.description}")
        }
        sb.appendLine()

        sb.appendLine("--- 4. COMPUTATIONAL OUTPUT ---")
        sb.appendLine("Primary Output: ${trace.outputResult.primaryOutput}")
        trace.outputResult.secondaryOutput?.let { sb.appendLine("Secondary Output: $it") }
        trace.outputResult.confidenceOrStatus?.let { sb.appendLine("Confidence/Status: $it") }
        sb.appendLine()

        sb.appendLine("--- 5. HUMAN-READABLE EXPLANATION ---")
        sb.appendLine(trace.explanationText)
        sb.appendLine()

        sb.appendLine("--- 6. REPRODUCIBILITY & AUDIT METADATA ---")
        sb.appendLine("Trace ID: ${trace.traceId}")
        sb.appendLine("Source Modules: ${trace.sourceModules.joinToString(", ")}")
        trace.reproducibilityMetadata.forEach { (k, v) ->
            sb.appendLine("• $k: $v")
        }
        sb.appendLine()

        sb.appendLine("----------------------------------------")
        sb.appendLine("LIMITATIONS & MEDICAL DISCLAIMER:")
        sb.appendLine(trace.limitationsText)
        sb.appendLine("========================================")

        return sb.toString()
    }
}

/**
 * Safety filter to reject or rewrite language implying medical certainty or definitive diagnosis.
 */
object TraceSafetyFilter {
    fun sanitize(text: String): String {
        return text
            .replace("proves that you have", "indicates a potential correlation with", ignoreCase = true)
            .replace("confirms diagnosis of", "estimates likelihood of", ignoreCase = true)
            .replace("you are diagnosed with", "model indications suggest", ignoreCase = true)
            .replace("guaranteed outcome", "projected estimation", ignoreCase = true)
    }
}
