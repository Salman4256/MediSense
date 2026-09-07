package com.medisense.app.domain.model

import java.io.Serializable

/**
 * Domain models for Module 21: Explainable Health Decision Trace & AI Audit Trail.
 * Provides a standardized, deterministic representation of how MediSense computational
 * engines processed input data, evaluated important factors, and produced explainable results.
 */

enum class HealthDecisionTraceType(
    val displayName: String,
    val sourceEngine: String,
    val categoryBadge: String
) {
    DISEASE_PREDICTION("Disease Prediction & XAI", "LiteRT Neural Classifier & SHAP", "Prediction"),
    PREDICTION_CONFIDENCE("Confidence & Uncertainty Analysis", "Confidence Calibration Engine", "Confidence"),
    COUNTERFACTUAL_ANALYSIS("What-If Counterfactual Ablation", "Counterfactual Sensitivity Engine", "Sensitivity"),
    PERSONALIZATION("Adaptive Personal Health Context", "Context Synthesis Engine", "Personalization"),
    LONGITUDINAL_PATTERN("Longitudinal Health Trends", "Temporal Pattern Analyzer", "Trends"),
    RCHR_REPRESENTATION("Composite Health Representation", "RCHR Vectorization Engine", "RCHR"),
    CONTEXTUAL_RISK("Context-Aware Health Risk Priority", "Contextual Risk Engine", "Risk Priority"),
    PERSONALIZED_GUIDANCE("Personalized Health Guidance", "Personalized Guidance Engine", "Guidance"),
    DATA_QUALITY_ASSESSMENT("Health Data Quality & Integrity", "Health Data Quality Engine", "Data Quality")
}

enum class TraceInfluenceDirection(val label: String) {
    POSITIVE("Increases Likelihood / Primary Factor"),
    NEGATIVE("Decreases Likelihood / Protective"),
    NEUTRAL("Baseline / Informational"),
    ALERT("Requires Attention")
}

data class HealthDecisionTraceFactor(
    val name: String,
    val value: String,
    val influenceDirection: TraceInfluenceDirection,
    val weightPercentage: Int? = null,
    val interpretation: String
) : Serializable

data class HealthDecisionTraceStep(
    val stepNumber: Int,
    val title: String,
    val description: String,
    val engineComponent: String
) : Serializable

data class HealthDecisionTraceResult(
    val primaryOutput: String,
    val secondaryOutput: String? = null,
    val confidenceOrStatus: String? = null,
    val categoryTag: String? = null
) : Serializable

data class HealthDecisionTrace(
    val traceId: String,
    val userId: String,
    val decisionType: HealthDecisionTraceType,
    val generatedAt: Long = System.currentTimeMillis(),
    val engineName: String,
    val engineVersion: String = "1.0",
    val modelVersion: String? = null,
    val inputSummary: String,
    val inputFactors: List<HealthDecisionTraceFactor>,
    val processingSteps: List<HealthDecisionTraceStep>,
    val outputResult: HealthDecisionTraceResult,
    val explanationText: String,
    val limitationsText: String,
    val dataQualityStatus: String,
    val sourceModules: List<String>,
    val reproducibilityMetadata: Map<String, String> = emptyMap()
) : Serializable
