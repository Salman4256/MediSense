package com.medisense.app.ui.risk.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.medisense.app.data.remote.supabase.AuthService
import com.medisense.app.data.repository.ContextualRiskRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ContextualRiskViewModel @Inject constructor(
    private val repository: ContextualRiskRepository,
    private val authService: AuthService
) : ViewModel() {

    private val _uiState = MutableStateFlow<ContextualRiskUiState>(ContextualRiskUiState.Loading)
    val uiState: StateFlow<ContextualRiskUiState> = _uiState.asStateFlow()

    init {
        loadAssessment()
    }

    fun loadAssessment() {
        if (!authService.isUserLoggedIn()) {
            _uiState.value = ContextualRiskUiState.Error("Please log in to view your health context assessment.")
            return
        }

        _uiState.value = ContextualRiskUiState.Loading
        viewModelScope.launch {
            repository.observeContextualRiskAssessment()
                .catch { e ->
                    _uiState.value = ContextualRiskUiState.Error(e.message ?: "Failed to evaluate contextual health risk")
                }
                .collect { assessment ->
                    if (assessment.hasSufficientData) {
                        _uiState.value = ContextualRiskUiState.Success(assessment)
                    } else {
                        _uiState.value = ContextualRiskUiState.InsufficientData(assessment)
                    }
                }
        }
    }
}
