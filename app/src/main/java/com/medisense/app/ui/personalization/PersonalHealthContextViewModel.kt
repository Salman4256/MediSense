package com.medisense.app.ui.personalization

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.medisense.app.data.remote.supabase.AuthService
import com.medisense.app.data.repository.PersonalHealthContextRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PersonalHealthContextViewModel @Inject constructor(
    private val repository: PersonalHealthContextRepository,
    private val authService: AuthService
) : ViewModel() {

    private val _uiState = MutableStateFlow<PersonalHealthContextUiState>(PersonalHealthContextUiState.Loading)
    val uiState: StateFlow<PersonalHealthContextUiState> = _uiState.asStateFlow()

    init {
        loadPersonalHealthContext()
    }

    fun loadPersonalHealthContext() {
        if (!authService.isUserLoggedIn()) {
            _uiState.value = PersonalHealthContextUiState.Empty("Please sign in to view your personalized health context.")
            return
        }

        _uiState.value = PersonalHealthContextUiState.Loading
        viewModelScope.launch {
            repository.observePersonalHealthContext()
                .catch { e ->
                    _uiState.value = PersonalHealthContextUiState.Error(e.message ?: "Failed to generate health context")
                }
                .collect { context ->
                    if (context.hasSufficientData) {
                        _uiState.value = PersonalHealthContextUiState.Success(context)
                    } else {
                        _uiState.value = PersonalHealthContextUiState.Empty("Start by completing your health record or logging symptoms to see your personalized health context.")
                    }
                }
        }
    }
}
