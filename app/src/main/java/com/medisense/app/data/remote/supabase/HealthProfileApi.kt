package com.medisense.app.data.remote.supabase

import com.medisense.app.data.local.entity.HealthProfileEntity
import kotlinx.coroutines.delay

/**
 * Mock interface simulating Supabase API calls.
 */
class HealthProfileApi {

    suspend fun getHealthProfile(userId: String): HealthProfileEntity? {
        // Simulate network delay
        delay(1000)
        // In a real scenario, this would query the Supabase health_profiles table
        return null // Return null to indicate no profile found on server initially
    }

    suspend fun upsertHealthProfile(profile: HealthProfileEntity): Boolean {
        // Simulate network delay
        delay(1000)
        // In a real scenario, this would upsert the record to Supabase
        return true
    }
}
