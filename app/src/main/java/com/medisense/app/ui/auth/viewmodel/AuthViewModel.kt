package com.medisense.app.ui.auth.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.medisense.app.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _authState = MutableStateFlow<AuthState>(AuthState.Idle)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    fun checkSession() {
        _authState.value = AuthState.Loading
        if (authRepository.isUserLoggedIn()) {
            _authState.value = AuthState.Authenticated
        } else {
            _authState.value = AuthState.Unauthenticated
        }
    }

    fun login(email: String, password: String) = viewModelScope.launch {
        _authState.value = AuthState.Loading
        val result = authRepository.login(email, password)
        if (result.isSuccess) {
            _authState.value = AuthState.Success
        } else {
            _authState.value = AuthState.Error(result.exceptionOrNull()?.message ?: "Login failed")
        }
    }

    fun register(email: String, password: String, fullName: String) = viewModelScope.launch {
        _authState.value = AuthState.Loading
        val result = authRepository.register(email, password, fullName)
        if (result.isSuccess) {
            _authState.value = AuthState.Success
        } else {
            _authState.value = AuthState.Error(result.exceptionOrNull()?.message ?: "Registration failed")
        }
    }

    fun resetPassword(email: String) = viewModelScope.launch {
        _authState.value = AuthState.Loading
        val result = authRepository.resetPassword(email)
        if (result.isSuccess) {
            _authState.value = AuthState.Success
        } else {
            _authState.value = AuthState.Error(result.exceptionOrNull()?.message ?: "Password reset failed")
        }
    }

    fun logout() = viewModelScope.launch {
        _authState.value = AuthState.Loading
        val result = authRepository.logout()
        if (result.isSuccess) {
            _authState.value = AuthState.Unauthenticated
        } else {
            _authState.value = AuthState.Error(result.exceptionOrNull()?.message ?: "Logout failed")
        }
    }

    fun resetState() {
        _authState.value = AuthState.Idle
    }
}
