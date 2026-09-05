package com.medisense.app.ui.personalization

import com.medisense.app.domain.model.PersonalHealthContext

sealed class PersonalHealthContextUiState {
    object Loading : PersonalHealthContextUiState()
    data class Success(val context: PersonalHealthContext) : PersonalHealthContextUiState()
    data class Empty(val message: String) : PersonalHealthContextUiState()
    data class Error(val message: String) : PersonalHealthContextUiState()
}
