package com.medisense.app.ui.onboarding

import androidx.lifecycle.ViewModel
import com.medisense.app.data.local.session.SharedPreferencesSessionManager
import com.medisense.app.data.remote.supabase.AuthService
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class FirstLaunchViewModel @Inject constructor(
    private val sessionManager: SharedPreferencesSessionManager,
    private val authService: AuthService
) : ViewModel() {

    fun isUserLoggedIn(): Boolean {
        return authService.isUserLoggedIn() || sessionManager.isUserLoggedIn()
    }

    fun completeOnboarding() {
        sessionManager.setCompletedOnboarding(true)
    }
}
