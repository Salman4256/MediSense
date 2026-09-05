package com.medisense.app.ui.guidance.viewmodel

import com.medisense.app.domain.model.GuidanceEngineResult

sealed interface PersonalizedGuidanceUiState {
    object Loading : PersonalizedGuidanceUiState
    data class Success(val result: GuidanceEngineResult) : PersonalizedGuidanceUiState
    data class Empty(val message: String) : PersonalizedGuidanceUiState
    data class Error(val message: String) : PersonalizedGuidanceUiState
}
