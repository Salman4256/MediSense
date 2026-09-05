package com.medisense.app.ui.rchr.viewmodel

import com.medisense.app.domain.rchr.RchrReconstructionResult
import com.medisense.app.domain.rchr.RchrRepresentation

sealed interface RchrUiState {
    object Loading : RchrUiState
    data class Empty(val message: String) : RchrUiState
    data class Success(
        val representation: RchrRepresentation,
        val reconstructionResult: RchrReconstructionResult? = null,
        val isReconstructed: Boolean = false
    ) : RchrUiState
    data class Error(val message: String) : RchrUiState
}
