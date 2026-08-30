package com.medisense.app.ui.prediction.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.medisense.app.data.model.DiseasePrediction
import com.medisense.app.data.model.Symptom
import com.medisense.app.data.repository.DiseasePredictionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class PredictionUiState {
    object Idle : PredictionUiState()
    object Loading : PredictionUiState()
    data class Success(val predictions: List<DiseasePrediction>) : PredictionUiState()
    data class Error(val message: String) : PredictionUiState()
}

@HiltViewModel
class DiseasePredictionViewModel @Inject constructor(
    private val repository: DiseasePredictionRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<PredictionUiState>(PredictionUiState.Idle)
    val uiState: StateFlow<PredictionUiState> = _uiState.asStateFlow()

    private val _symptoms = MutableStateFlow<List<Symptom>>(emptyList())
    val symptoms: StateFlow<List<Symptom>> = _symptoms.asStateFlow()

    private val _selectedSymptoms = MutableStateFlow<List<Symptom>>(emptyList())
    val selectedSymptoms: StateFlow<List<Symptom>> = _selectedSymptoms.asStateFlow()

    init {
        loadSymptoms()
    }

    private fun loadSymptoms() {
        viewModelScope.launch {
            try {
                _symptoms.value = repository.getSymptoms()
            } catch (e: Exception) {
                _uiState.value = PredictionUiState.Error("Failed to load symptoms catalog")
            }
        }
    }

    fun selectSymptom(symptom: Symptom) {
        val current = _selectedSymptoms.value.toMutableList()
        if (!current.contains(symptom)) {
            current.add(symptom)
            _selectedSymptoms.value = current
        }
    }

    fun deselectSymptom(symptom: Symptom) {
        val current = _selectedSymptoms.value.toMutableList()
        if (current.remove(symptom)) {
            _selectedSymptoms.value = current
        }
    }

    fun clearSelections() {
        _selectedSymptoms.value = emptyList()
        _uiState.value = PredictionUiState.Idle
    }

    fun runPrediction() {
        val selected = _selectedSymptoms.value
        if (selected.isEmpty()) {
            _uiState.value = PredictionUiState.Error("Please select at least one symptom")
            return
        }

        _uiState.value = PredictionUiState.Loading
        viewModelScope.launch {
            try {
                val results = repository.predict(selected)
                _uiState.value = PredictionUiState.Success(results)
            } catch (e: Exception) {
                _uiState.value = PredictionUiState.Error(e.message ?: "Prediction failed")
            }
        }
    }

    fun resetState() {
        _uiState.value = PredictionUiState.Idle
    }
}
