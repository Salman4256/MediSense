package com.medisense.app.ui.guidance.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.medisense.app.data.remote.supabase.AuthService
import com.medisense.app.data.repository.PersonalizedGuidanceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PersonalizedGuidanceViewModel @Inject constructor(
    private val repository: PersonalizedGuidanceRepository,
    private val authService: AuthService
) : ViewModel() {

    private val _uiState = MutableStateFlow<PersonalizedGuidanceUiState>(PersonalizedGuidanceUiState.Loading)
    val uiState: StateFlow<PersonalizedGuidanceUiState> = _uiState.asStateFlow()

    init {
        loadGuidance()
    }

    fun loadGuidance() {
        if (!authService.isUserLoggedIn()) {
            _uiState.value = PersonalizedGuidanceUiState.Error("Please log in to view your personalized health guidance.")
            return
        }

        _uiState.value = PersonalizedGuidanceUiState.Loading
        viewModelScope.launch {
            repository.observePersonalizedGuidance()
                .catch { e ->
                    _uiState.value = PersonalizedGuidanceUiState.Error(e.message ?: "Failed to generate personalized guidance.")
                }
                .collect { result ->
                    if (result.guidanceList.isEmpty()) {
                        _uiState.value = PersonalizedGuidanceUiState.Empty("No personalized guidance is available yet. Logging health history will build your recommendations.")
                    } else {
                        _uiState.value = PersonalizedGuidanceUiState.Success(result)
                    }
                }
        }
    }
}
