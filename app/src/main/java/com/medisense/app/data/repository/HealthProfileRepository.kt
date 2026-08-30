package com.medisense.app.data.repository

import android.content.Context
import com.medisense.app.data.local.dao.HealthProfileDao
import com.medisense.app.data.remote.supabase.HealthProfileApi
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

@Singleton
class HealthProfileRepository @Inject constructor(
    private val context: Context,
    private val healthProfileDao: HealthProfileDao,
    private val healthProfileApi: HealthProfileApi
) {
    fun getProfile(): Flow<Any?> = emptyFlow()
    
    suspend fun saveProfile(profile: Any) {}
    
    suspend fun syncProfile() {}
}
