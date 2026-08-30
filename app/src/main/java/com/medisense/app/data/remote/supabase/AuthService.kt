package com.medisense.app.data.remote.supabase

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import javax.inject.Inject

class AuthService @Inject constructor(
    private val supabaseClient: SupabaseClient
) {
    private val auth = supabaseClient.auth

    suspend fun login(email: String, password: String) {
        auth.signInWith(Email) {
            this.email = email
            this.password = password
        }
    }

    suspend fun register(email: String, password: String, fullName: String) {
        auth.signUpWith(Email) {
            this.email = email
            this.password = password
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
    }

    fun isUserLoggedIn(): Boolean {
        return auth.currentSessionOrNull() != null
    }

    fun getCurrentUserEmail(): String? {
        return auth.currentUserOrNull()?.email
    }

    fun getCurrentUserId(): String? {
        return auth.currentUserOrNull()?.id
    }
}
