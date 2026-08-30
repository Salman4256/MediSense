package com.medisense.app.data.repository

import com.medisense.app.data.remote.supabase.AuthService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepository @Inject constructor(
    private val authService: AuthService
) {
    suspend fun login(email: String, password: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            authService.login(email, password)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun register(email: String, password: String, fullName: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            authService.register(email, password, fullName)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun resetPassword(email: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            authService.resetPassword(email)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun logout(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            authService.logout()
            Result.success(Unit)
        } catch (e: Exception) {
            // Even if an unexpected error occurs, treat logout as successful locally
            Result.success(Unit)
        }
    }

    fun isUserLoggedIn(): Boolean {
        return authService.isUserLoggedIn()
    }

    fun getCurrentUserEmail(): String? {
        return authService.getCurrentUserEmail()
    }
}
