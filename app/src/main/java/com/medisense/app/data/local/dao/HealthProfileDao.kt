package com.medisense.app.data.local.dao

import androidx.room.*
import com.medisense.app.data.local.entity.HealthProfileEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HealthProfileDao {

    @Query("SELECT * FROM health_profiles WHERE userId = :userId LIMIT 1")
    fun observeHealthProfile(userId: String): Flow<HealthProfileEntity?>

    @Query("SELECT * FROM health_profiles WHERE userId = :userId LIMIT 1")
    suspend fun getHealthProfile(userId: String): HealthProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHealthProfile(profile: HealthProfileEntity)

    @Update
    suspend fun updateHealthProfile(profile: HealthProfileEntity)

    @Delete
    suspend fun deleteHealthProfile(profile: HealthProfileEntity)

    @Query("SELECT * FROM health_profiles WHERE pendingSync = 1")
    suspend fun getPendingSyncProfiles(): List<HealthProfileEntity>
}
