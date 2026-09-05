package com.medisense.app.ui.prediction.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.medisense.app.data.model.DiseasePrediction
import com.medisense.app.data.model.PredictionExplanation
import com.medisense.app.data.model.Symptom
import com.medisense.app.data.repository.DiseasePredictionRepository
import com.medisense.app.data.repository.PredictionHistoryRepository
import com.medisense.app.domain.counterfactual.CounterfactualExplanationEngine
import com.medisense.app.domain.model.CounterfactualAnalysisResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class PredictionResultUiState {
    object Idle : PredictionResultUiState()
    object Loading : PredictionResultUiState()
    data class Success(
        val primaryPrediction: DiseasePrediction,
        val secondaryPredictions: List<DiseasePrediction>,
        val selectedSymptoms: List<Symptom>,
        val explanation: PredictionExplanation,
        val counterfactualResult: CounterfactualAnalysisResult
    ) : PredictionResultUiState()
    data class Error(val message: String) : PredictionResultUiState()
}

@HiltViewModel
class PredictionResultViewModel @Inject constructor(
    private val predictionRepository: DiseasePredictionRepository,
    private val historyRepository: PredictionHistoryRepository,
    private val counterfactualEngine: CounterfactualExplanationEngine
) : ViewModel() {

    private val _uiState = MutableStateFlow<PredictionResultUiState>(PredictionResultUiState.Idle)
    val uiState: StateFlow<PredictionResultUiState> = _uiState.asStateFlow()

    private var hasPersisted = false

    fun loadResults(predictions: List<DiseasePrediction>, symptoms: List<Symptom>) {
        if (predictions.isEmpty()) {
            _uiState.value = PredictionResultUiState.Error("No prediction results available")
            return
        }

        _uiState.value = PredictionResultUiState.Loading
        viewModelScope.launch {
            try {
                val primary = predictions.first()
                val secondaries = if (predictions.size > 1) predictions.subList(1, minOf(predictions.size, 10)) else emptyList()
                val explanation = predictionRepository.generateExplanation(primary, symptoms)
                val counterfactualResult = counterfactualEngine.evaluateCounterfactuals(primary, symptoms, explanation)

                _uiState.value = PredictionResultUiState.Success(
                    primaryPrediction = primary,
                    secondaryPredictions = secondaries,
                    selectedSymptoms = symptoms,
                    explanation = explanation,
                    counterfactualResult = counterfactualResult
                )

                // Persist completed prediction to Prediction History (Module 8)
                if (!hasPersisted) {
                    hasPersisted = true
                    historyRepository.savePredictionResult(
                        predictedDisease = primary.diseaseName,
                        confidence = primary.probability,
                        symptoms = symptoms.map { it.displayName },
                        explanationSummary = explanation.summary,
                        modelVersion = explanation.modelVersion
                    )
                }
            } catch (e: Exception) {
                _uiState.value = PredictionResultUiState.Error(e.message ?: "Failed to generate explanation")
            }
        }
    }
}
