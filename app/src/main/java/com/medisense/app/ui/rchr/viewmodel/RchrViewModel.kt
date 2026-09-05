package com.medisense.app.ui.rchr.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.medisense.app.data.remote.supabase.AuthService
import com.medisense.app.data.repository.RchrRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RchrViewModel @Inject constructor(
    private val repository: RchrRepository,
    private val authService: AuthService
) : ViewModel() {

    private val _uiState = MutableStateFlow<RchrUiState>(RchrUiState.Loading)
    val uiState: StateFlow<RchrUiState> = _uiState.asStateFlow()

    init {
        observeRchrRepresentation()
    }

    fun observeRchrRepresentation() {
        if (!authService.isUserLoggedIn()) {
            _uiState.value = RchrUiState.Empty("Please sign in to generate your Composite Health Representation (RCHR).")
            return
        }

        viewModelScope.launch {
            _uiState.value = RchrUiState.Loading
            repository.observeRchrRepresentation()
                .catch { e ->
                    _uiState.value = RchrUiState.Error(e.message ?: "Failed to generate Composite Health Representation.")
                }
                .collect { representation ->
                    if (representation.hasSufficientData) {
                        // Automatically compute reconstruction preview for convenience, or on demand
                        val reconstruction = repository.reconstructAndValidate(representation)
                        _uiState.value = RchrUiState.Success(
                            representation = representation,
                            reconstructionResult = reconstruction,
                            isReconstructed = false
                        )
                    } else {
                        _uiState.value = RchrUiState.Empty(
                            "No health records available yet to generate a Composite Health Representation. Complete your profile, log symptoms, or add medications to begin."
                        )
                    }
                }
        }
    }

    fun triggerReconstruction() {
        val currentState = _uiState.value
        if (currentState is RchrUiState.Success) {
            val reconstruction = repository.reconstructAndValidate(currentState.representation)
            _uiState.value = currentState.copy(
                reconstructionResult = reconstruction,
                isReconstructed = true
            )
        }
    }
}
