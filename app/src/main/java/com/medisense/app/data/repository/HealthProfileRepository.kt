package com.medisense.app.data.repository

import com.medisense.app.data.local.dao.HealthProfileDao
import com.medisense.app.data.local.entity.HealthProfileEntity
import com.medisense.app.data.remote.supabase.AuthService
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HealthProfileRepository @Inject constructor(
    private val healthProfileDao: HealthProfileDao,
    private val authService: AuthService,
    private val supabaseClient: SupabaseClient
) {

    fun getCurrentUserId(): String? {
        return authService.getCurrentUserId()
    }

    fun observeHealthProfile(userId: String): Flow<HealthProfileEntity?> {
        return healthProfileDao.observeHealthProfile(userId)
    }

    suspend fun loadProfile(userId: String): Result<HealthProfileEntity?> = withContext(Dispatchers.IO) {
        try {
            val local = healthProfileDao.getHealthProfile(userId)
            if (local != null) {
                return@withContext Result.success(local)
            }

            // If local doesn't exist, try cloud
            try {
                val remoteList = supabaseClient.postgrest["health_profiles"]
                    .select {
                        filter {
                            eq("user_id", userId)
                        }
                    }
                    .decodeList<HealthProfileEntity>()
                val remote = remoteList.firstOrNull()
                if (remote != null) {
                    healthProfileDao.insertHealthProfile(remote.copy(pendingSync = false))
                    return@withContext Result.success(remote)
                }
            } catch (e: Exception) {
                // Network failure or offline
            }
            Result.success(null)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun saveProfile(profile: HealthProfileEntity): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // Save to Room immediately
            val local = profile.copy(pendingSync = true)
            healthProfileDao.insertHealthProfile(local)

            // Try to sync with Supabase
            try {
                supabaseClient.postgrest["health_profiles"].upsert(profile)
                healthProfileDao.insertHealthProfile(profile.copy(pendingSync = false))
            } catch (e: Exception) {
                // If cloud sync fails, it remains pendingSync = true locally
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteProfile(profile: HealthProfileEntity): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            healthProfileDao.deleteHealthProfile(profile)
            try {
                supabaseClient.postgrest["health_profiles"].delete {
                    filter {
                        eq("id", profile.id)
                    }
                }
            } catch (e: Exception) {
                // Ignore cloud sync issues on delete for now
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
