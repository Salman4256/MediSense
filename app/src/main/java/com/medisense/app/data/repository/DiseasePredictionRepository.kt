package com.medisense.app.data.repository

import com.medisense.app.data.model.DiseasePrediction
import com.medisense.app.data.model.PredictionExplanation
import com.medisense.app.data.model.Symptom
import com.medisense.app.ml.DiseasePredictionEngine
import com.medisense.app.ml.xai.XaiExplanationEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
open class DiseasePredictionRepository @Inject constructor(
    private val predictionEngine: DiseasePredictionEngine?,
    private val xaiExplanationEngine: XaiExplanationEngine?
) {
    open fun getSymptoms(): List<Symptom> {
        val engine = predictionEngine ?: return emptyList()
        return engine.getSymptoms().mapIndexed { index, name ->
            Symptom(
                id = index,
                displayName = name.replace("_", " ").split(" ").joinToString(" ") {
                    it.replaceFirstChar { char -> char.uppercase() }
                },
                modelFeatureName = name
            )
        }
    }

    open suspend fun predict(selectedSymptoms: List<Symptom>): List<DiseasePrediction> = withContext(Dispatchers.Default) {
        val engine = predictionEngine ?: return@withContext emptyList()
        val symptoms = engine.getSymptoms()
        val inputVector = FloatArray(symptoms.size)

        for (symptom in selectedSymptoms) {
            if (symptom.id in inputVector.indices) {
                inputVector[symptom.id] = 1.0f
            }
        }

        val probabilities = engine.predict(inputVector)
        val labels = engine.getLabels()

        labels.mapIndexed { index, name ->
            DiseasePrediction(
                diseaseName = name,
                probability = probabilities.getOrElse(index) { 0.0f },
                rank = 0
            )
        }
        .sortedByDescending { it.probability }
        .mapIndexed { rank, prediction ->
            prediction.copy(rank = rank + 1)
        }
    }

    open suspend fun generateExplanation(
        prediction: DiseasePrediction,
        selectedSymptoms: List<Symptom>
    ): PredictionExplanation = withContext(Dispatchers.Default) {
        val xai = xaiExplanationEngine ?: return@withContext PredictionExplanation(prediction.diseaseName, prediction.probability, emptyList(), "")
        xai.generateExplanation(
            diseaseName = prediction.diseaseName,
            probability = prediction.probability,
            selectedSymptoms = selectedSymptoms
        )
    }
}
