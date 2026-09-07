package com.medisense.app.data.local.dao

import androidx.room.*
import com.medisense.app.data.local.entity.InterventionResponseRecordEntity
import kotlinx.coroutines.flow.Flow

/**
 * Room DAO for managing intervention response records and adaptive feedback history (Module 25).
 */
@Dao
interface InterventionResponseDao {

    @Query("SELECT * FROM intervention_response_records WHERE user_id = :userId ORDER BY observation_timestamp DESC")
    fun observeRecordsForUser(userId: String): Flow<List<InterventionResponseRecordEntity>>

    @Query("SELECT * FROM intervention_response_records WHERE user_id = :userId ORDER BY observation_timestamp DESC")
    suspend fun getRecordsForUserSync(userId: String): List<InterventionResponseRecordEntity>

    @Query("SELECT * FROM intervention_response_records WHERE user_id = :userId AND source_prediction_id = :predictionId ORDER BY observation_timestamp DESC")
    suspend fun getRecordsByPrediction(predictionId: Long, userId: String): List<InterventionResponseRecordEntity>

    @Query("SELECT * FROM intervention_response_records WHERE user_id = :userId AND intervention_category = :category ORDER BY observation_timestamp DESC")
    suspend fun getRecordsByCategory(category: String, userId: String): List<InterventionResponseRecordEntity>

    @Query("SELECT * FROM intervention_response_records WHERE id = :id AND user_id = :userId LIMIT 1")
    suspend fun getRecordById(id: Long, userId: String): InterventionResponseRecordEntity?

    @Query("SELECT * FROM intervention_response_records WHERE user_id = :userId AND pending_sync = 1")
    suspend fun getPendingSyncRecords(userId: String): List<InterventionResponseRecordEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecord(entity: InterventionResponseRecordEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecords(entities: List<InterventionResponseRecordEntity>): List<Long>

    @Update
    suspend fun updateRecord(entity: InterventionResponseRecordEntity)

    @Query("DELETE FROM intervention_response_records WHERE id = :id AND user_id = :userId")
    suspend fun deleteRecordById(id: Long, userId: String)

    @Query("DELETE FROM intervention_response_records WHERE user_id = :userId")
    suspend fun deleteAllRecordsForUser(userId: String)
}
