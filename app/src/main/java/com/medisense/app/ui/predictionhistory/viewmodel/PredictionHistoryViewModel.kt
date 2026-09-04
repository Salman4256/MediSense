package com.medisense.app.ui.predictionhistory.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.medisense.app.data.local.entity.PredictionHistoryEntity
import com.medisense.app.data.repository.PredictionHistoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class PredictionHistoryUiState {
    object Loading : PredictionHistoryUiState()
    object Empty : PredictionHistoryUiState()
    data class Success(val history: List<PredictionHistoryEntity>) : PredictionHistoryUiState()
    data class Error(val message: String) : PredictionHistoryUiState()
}

sealed class PredictionDetailUiState {
    object Loading : PredictionDetailUiState()
    data class Success(val record: PredictionHistoryEntity) : PredictionDetailUiState()
    data class Error(val message: String) : PredictionDetailUiState()
}

@HiltViewModel
class PredictionHistoryViewModel @Inject constructor(
    private val repository: PredictionHistoryRepository
) : ViewModel() {

    private val _historyState = MutableStateFlow<PredictionHistoryUiState>(PredictionHistoryUiState.Loading)
    val historyState: StateFlow<PredictionHistoryUiState> = _historyState.asStateFlow()

    private val _detailState = MutableStateFlow<PredictionDetailUiState>(PredictionDetailUiState.Loading)
    val detailState: StateFlow<PredictionDetailUiState> = _detailState.asStateFlow()

    init {
        loadHistory()
    }

    fun loadHistory() {
        _historyState.value = PredictionHistoryUiState.Loading
        viewModelScope.launch {
            repository.observeHistory()
                .catch { e ->
                    _historyState.value = PredictionHistoryUiState.Error(e.message ?: "Failed to load prediction history")
                }
                .collect { list ->
                    if (list.isEmpty()) {
                        _historyState.value = PredictionHistoryUiState.Empty
                    } else {
                        _historyState.value = PredictionHistoryUiState.Success(list)
                    }
                }
        }
    }

    fun loadDetail(id: Long) {
        _detailState.value = PredictionDetailUiState.Loading
        viewModelScope.launch {
            try {
                val record = repository.getHistoryById(id)
                if (record != null) {
                    _detailState.value = PredictionDetailUiState.Success(record)
                } else {
                    _detailState.value = PredictionDetailUiState.Error("Prediction record not found")
                }
            } catch (e: Exception) {
                _detailState.value = PredictionDetailUiState.Error(e.message ?: "Failed to load prediction detail")
            }
        }
    }

    fun deleteHistoryItem(id: Long) {
        viewModelScope.launch {
            try {
                repository.deleteHistoryItem(id)
            } catch (e: Exception) {
                _historyState.value = PredictionHistoryUiState.Error(e.message ?: "Failed to delete prediction record")
            }
        }
    }

    fun deleteAllHistory() {
        viewModelScope.launch {
            try {
                repository.deleteAllHistory()
            } catch (e: Exception) {
                _historyState.value = PredictionHistoryUiState.Error(e.message ?: "Failed to clear prediction history")
            }
        }
    }
}
