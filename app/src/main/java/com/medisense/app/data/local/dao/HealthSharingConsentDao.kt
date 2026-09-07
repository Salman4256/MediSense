package com.medisense.app.data.local.dao

import androidx.room.*
import com.medisense.app.data.local.entity.HealthSharingConsentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HealthSharingConsentDao {

    @Query("SELECT * FROM health_sharing_consents WHERE userId = :userId ORDER BY createdAt DESC")
    fun observeConsentsForUser(userId: String): Flow<List<HealthSharingConsentEntity>>

    @Query("SELECT * FROM health_sharing_consents WHERE userId = :userId ORDER BY createdAt DESC")
    suspend fun getConsentsForUserSync(userId: String): List<HealthSharingConsentEntity>

    @Query("SELECT * FROM health_sharing_consents WHERE id = :id AND userId = :userId LIMIT 1")
    suspend fun getConsentById(id: Long, userId: String): HealthSharingConsentEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConsent(entity: HealthSharingConsentEntity): Long

    @Update
    suspend fun updateConsent(entity: HealthSharingConsentEntity)

    @Query("UPDATE health_sharing_consents SET status = :status, revokedAt = :revokedAt WHERE id = :id AND userId = :userId")
    suspend fun updateConsentStatus(id: Long, userId: String, status: String, revokedAt: Long?)

    @Query("DELETE FROM health_sharing_consents WHERE id = :id AND userId = :userId")
    suspend fun deleteConsentById(id: Long, userId: String)

    @Query("DELETE FROM health_sharing_consents WHERE userId = :userId")
    suspend fun deleteAllConsentsForUser(userId: String)
}
