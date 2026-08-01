package com.medisense.app.data.repository

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.medisense.app.data.local.dao.HealthProfileDao
import com.medisense.app.data.local.entity.HealthProfileEntity
import com.medisense.app.data.remote.supabase.HealthProfileApi
import com.medisense.app.sync.HealthProfileSyncWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HealthProfileRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val healthProfileDao: HealthProfileDao,
    private val healthProfileApi: HealthProfileApi
) {

    // Mock method to get current Supabase User ID
    fun getCurrentUserId(): String {
        return "mock-supabase-user-id"
    }

    fun getHealthProfile(): Flow<HealthProfileEntity?> {
        return healthProfileDao.getHealthProfileFlow(getCurrentUserId())
    }

    suspend fun saveProfile(profile: HealthProfileEntity) {
        // Save to Room immediately, mark as pending_sync
        val profileToSave = profile.copy(
            userId = getCurrentUserId(),
            pendingSync = true,
            updatedAt = System.currentTimeMillis()
        )
        healthProfileDao.insertOrUpdate(profileToSave)
        
        // Trigger WorkManager for background sync
        enqueueSync()
    }

    private fun enqueueSync() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
            
        val syncRequest = OneTimeWorkRequestBuilder<HealthProfileSyncWorker>()
            .setConstraints(constraints)
            .build()
            
        WorkManager.getInstance(context).enqueueUniqueWork(
            "HealthProfileSyncWorker",
            ExistingWorkPolicy.REPLACE,
            syncRequest
        )
    }
}
