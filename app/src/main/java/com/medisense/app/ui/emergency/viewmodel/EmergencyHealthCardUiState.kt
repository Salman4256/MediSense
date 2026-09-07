package com.medisense.app.ui.emergency.viewmodel

import com.medisense.app.domain.model.EmergencyHealthCard

/**
 * UI State for Emergency & Critical Health Access Card screen.
 */
sealed interface EmergencyHealthCardUiState {
    object Loading : EmergencyHealthCardUiState
    
    data class Content(
        val card: EmergencyHealthCard,
        val isExporting: Boolean = false
    ) : EmergencyHealthCardUiState

    data class Error(
        val message: String
    ) : EmergencyHealthCardUiState
}
