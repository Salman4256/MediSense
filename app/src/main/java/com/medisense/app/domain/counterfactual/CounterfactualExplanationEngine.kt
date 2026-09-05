package com.medisense.app.domain.counterfactual

import com.medisense.app.data.model.DiseasePrediction
import com.medisense.app.data.model.PredictionExplanation
import com.medisense.app.data.model.Symptom
import com.medisense.app.data.repository.DiseasePredictionRepository
import com.medisense.app.domain.model.CounterfactualAnalysisResult
import com.medisense.app.domain.model.CounterfactualChangeType
import com.medisense.app.domain.model.CounterfactualExplanation
import com.medisense.app.domain.model.ModelSensitivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Deterministic counterfactual "What-If" analysis engine.
 * Simulates single-symptom ablations against the existing LiteRT model
 * to explain model sensitivity and decision boundaries.
 */
@Singleton
class CounterfactualExplanationEngine @Inject constructor(
    private val predictionRepository: DiseasePredictionRepository
) {

    /**
     * Evaluates counterfactuals for the top influential symptoms.
     */
    suspend fun evaluateCounterfactuals(
        primaryPrediction: DiseasePrediction,
        selectedSymptoms: List<Symptom>,
        explanation: PredictionExplanation
    ): CounterfactualAnalysisResult = withContext(Dispatchers.Default) {
        val confidenceSummary = PredictionConfidenceInterpreter.interpret(
            primaryPrediction = primaryPrediction,
            symptomCount = selectedSymptoms.size
        )

        // If fewer than 2 symptoms, counterfactual ablation cannot be performed
        if (selectedSymptoms.size < PredictionConfidenceConfiguration.MIN_SYMPTOMS_FOR_COUNTERFACTUAL) {
            return@withContext CounterfactualAnalysisResult(
                confidenceSummary = confidenceSummary,
                sensitivity = ModelSensitivity.INSUFFICIENT_DATA,
                sensitivityExplanation = "At least 2 symptoms are needed to perform what-if counterfactual analysis.",
                counterfactuals = emptyList(),
                hasSufficientSymptoms = false
            )
        }

        // Map feature contributions from Module 4 XAI
        val importanceMap = explanation.contributions.associate {
            it.featureName.trim().lowercase() to it.importance
        }

        // Select top influential symptoms (up to MAX_COUNTERFACTUAL_SYMPTOMS)
        val topSymptoms = selectedSymptoms
            .sortedByDescending { symptom ->
                importanceMap[symptom.modelFeatureName.trim().lowercase()] ?: 0.1f
            }
            .take(PredictionConfidenceConfiguration.MAX_COUNTERFACTUAL_SYMPTOMS)

        val counterfactualList = mutableListOf<CounterfactualExplanation>()
        val origPct = (primaryPrediction.probability * 100).roundToInt()

        for (targetSymptom in topSymptoms) {
            val counterfactualSymptoms = selectedSymptoms.filter { it.id != targetSymptom.id }
            val counterfactualPredictions = predictionRepository.predict(counterfactualSymptoms)
            val counterfactualTop = counterfactualPredictions.firstOrNull() ?: primaryPrediction

            val isChanged = !counterfactualTop.diseaseName.equals(primaryPrediction.diseaseName, ignoreCase = true)
            val delta = counterfactualTop.probability - primaryPrediction.probability
            val newPct = (counterfactualTop.probability * 100).roundToInt()
            val importance = importanceMap[targetSymptom.modelFeatureName.trim().lowercase()] ?: 0.1f

            val changeType = when {
                isChanged -> CounterfactualChangeType.CHANGED_PREDICTION
                abs(delta) >= 0.01f -> CounterfactualChangeType.REMOVE_SYMPTOM
                else -> CounterfactualChangeType.NO_CHANGE
            }

            val explanationText = when {
                isChanged -> {
                    "Removing ${targetSymptom.displayName} shifts the model's top prediction from ${primaryPrediction.diseaseName} ($origPct%) to ${counterfactualTop.diseaseName} ($newPct%)."
                }
                delta < -0.01f -> {
                    "Removing ${targetSymptom.displayName} reduces model confidence from $origPct% to $newPct%, while ${primaryPrediction.diseaseName} remains the top prediction."
                }
                delta > 0.01f -> {
                    "Removing ${targetSymptom.displayName} increases model confidence from $origPct% to $newPct%, while ${primaryPrediction.diseaseName} remains the top prediction."
                }
                else -> {
                    "Removing ${targetSymptom.displayName} produces negligible change in model confidence ($origPct%)."
                }
            }

            counterfactualList.add(
                CounterfactualExplanation(
                    removedSymptom = targetSymptom.modelFeatureName,
                    removedSymptomDisplayName = targetSymptom.displayName,
                    originalPrediction = primaryPrediction.diseaseName,
                    originalConfidence = primaryPrediction.probability,
                    resultingPrediction = counterfactualTop.diseaseName,
                    resultingConfidence = counterfactualTop.probability,
                    confidenceDelta = delta,
                    isPredictionChanged = isChanged,
                    changeType = changeType,
                    explanation = explanationText,
                    importanceWeight = importance
                )
            )
        }

        // Deterministic Ranking: Changed predictions first, then largest confidence delta, then highest XAI weight
        val rankedCounterfactuals = counterfactualList.sortedWith(
            compareByDescending<CounterfactualExplanation> { it.isPredictionChanged }
                .thenByDescending { abs(it.confidenceDelta) }
                .thenByDescending { it.importanceWeight }
        )

        // Evaluate Model Output Sensitivity
        val hasPredictionChange = rankedCounterfactuals.any { it.isPredictionChanged }
        val sensitivity = if (hasPredictionChange) ModelSensitivity.SENSITIVE else ModelSensitivity.STABLE

        val sensitivityExplanation = if (hasPredictionChange) {
            "Removing one or more tested symptoms altered the model's top predicted condition. This indicates that the prediction is sensitive to specific key symptoms."
        } else {
            "The top predicted condition remained unchanged across all tested symptom removals, showing consistent model output across these perturbations."
        }

        return@withContext CounterfactualAnalysisResult(
            confidenceSummary = confidenceSummary,
            sensitivity = sensitivity,
            sensitivityExplanation = sensitivityExplanation,
            counterfactuals = rankedCounterfactuals,
            hasSufficientSymptoms = true
        )
    }
}
