package com.medisense.app.ui.profile.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.medisense.app.data.remote.supabase.AuthService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProfileUiState(
    val email: String = "",
    val userId: String = "",
    val displayName: String = "",
    val avatarLetter: String = "?",
    val isLoggingOut: Boolean = false,
    val logoutSuccess: Boolean = false
)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val authService: AuthService
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    init {
        loadProfileInfo()
    }

    private fun loadProfileInfo() {
        val email = authService.getCurrentUserEmail() ?: ""
        val userId = authService.getCurrentUserId() ?: ""

        // Derive display name from email local part
        val displayName = email
            .substringBefore("@")
            .replace(".", " ")
            .replace("_", " ")
            .split(" ")
            .joinToString(" ") { word ->
                word.replaceFirstChar { it.uppercaseChar() }
            }

        val avatarLetter = displayName.firstOrNull()?.uppercase() ?: "?"

        _uiState.value = ProfileUiState(
            email = email,
            userId = userId,
            displayName = displayName,
            avatarLetter = avatarLetter
        )
    }

    fun logout() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoggingOut = true)
            try {
                authService.logout()
                _uiState.value = _uiState.value.copy(isLoggingOut = false, logoutSuccess = true)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoggingOut = false)
            }
        }
    }
}
