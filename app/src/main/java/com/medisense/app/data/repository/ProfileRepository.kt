package com.medisense.app.data.repository

import com.medisense.app.data.remote.supabase.AuthService
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProfileRepository @Inject constructor(
    private val firebaseAuthService: AuthService
) {

    fun getCurrentUserId(): String = firebaseAuthService.getCurrentUserId() ?: "local-user"

    fun isUserLoggedIn(): Boolean = firebaseAuthService.isUserLoggedIn()

    suspend fun getProfile(): Result<Any> {
        return Result.failure(Exception("Not implemented"))
    }

    suspend fun updateProfile(profile: Any): Result<Unit> {
        return Result.failure(Exception("Not implemented"))
    }

    fun logout() {
        // stubbed
    }
}
