package com.medisense.app.data.remote.supabase

import com.medisense.app.data.local.session.SharedPreferencesSessionManager
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthService @Inject constructor(
    private val supabaseClient: SupabaseClient,
    private val sessionManager: SharedPreferencesSessionManager
) {
    private val auth = supabaseClient.auth

    suspend fun login(email: String, password: String) {
        auth.signInWith(Email) {
            this.email = email
            this.password = password
        }
        val session = auth.currentSessionOrNull()
        if (session != null) {
            sessionManager.saveSession(session)
        }
    }

    suspend fun register(email: String, password: String, fullName: String) {
        auth.signUpWith(Email) {
            this.email = email
            this.password = password
        }
        val session = auth.currentSessionOrNull()
        if (session != null) {
            sessionManager.saveSession(session)
        }
    }

    suspend fun resetPassword(email: String) {
        auth.resetPasswordForEmail(email)
    }

    suspend fun logout() {
        try {
            auth.signOut(scope = io.github.jan.supabase.auth.SignOutScope.LOCAL)
        } catch (e: Exception) {
            // Ignore server errors during sign out to ensure local state is cleared
        }
        sessionManager.deleteSession()
    }

    fun isUserLoggedIn(): Boolean {
        return auth.currentSessionOrNull() != null || sessionManager.isUserLoggedIn()
    }

    fun getCurrentUserEmail(): String? {
        return auth.currentUserOrNull()?.email ?: sessionManager.getSavedUserEmail()
    }

    fun getCurrentUserId(): String? {
        return auth.currentUserOrNull()?.id ?: sessionManager.getSavedUserId()
    }
}
