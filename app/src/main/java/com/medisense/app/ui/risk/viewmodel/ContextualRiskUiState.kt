package com.medisense.app.ui.risk.viewmodel

import com.medisense.app.domain.model.ContextualRiskAssessment

sealed class ContextualRiskUiState {
    object Loading : ContextualRiskUiState()
    data class Success(val assessment: ContextualRiskAssessment) : ContextualRiskUiState()
    data class InsufficientData(val assessment: ContextualRiskAssessment) : ContextualRiskUiState()
    data class Error(val message: String) : ContextualRiskUiState()
}
