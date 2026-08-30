package com.medisense.app.data.repository

import com.medisense.app.data.model.DiseasePrediction
import com.medisense.app.data.model.Symptom
import com.medisense.app.ml.DiseasePredictionEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DiseasePredictionRepository @Inject constructor(
    private val predictionEngine: DiseasePredictionEngine
) {
    fun getSymptoms(): List<Symptom> {
        return predictionEngine.getSymptoms().mapIndexed { index, name ->
            Symptom(
                id = index,
                displayName = name.replace("_", " ").split(" ").joinToString(" ") {
                    it.replaceFirstChar { char -> char.uppercase() }
                },
                modelFeatureName = name
            )
        }
    }

    suspend fun predict(selectedSymptoms: List<Symptom>): List<DiseasePrediction> = withContext(Dispatchers.Default) {
        val symptoms = predictionEngine.getSymptoms()
        val inputVector = FloatArray(symptoms.size)

        for (symptom in selectedSymptoms) {
            if (symptom.id in inputVector.indices) {
                inputVector[symptom.id] = 1.0f
            }
        }

        val probabilities = predictionEngine.predict(inputVector)
        val labels = predictionEngine.getLabels()

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
}
