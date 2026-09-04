package com.medisense.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.medisense.app.data.local.entity.PredictionHistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PredictionHistoryDao {

    @Query("SELECT * FROM prediction_history WHERE userId = :userId ORDER BY predictionTimestamp DESC")
    fun observePredictionHistory(userId: String): Flow<List<PredictionHistoryEntity>>

    @Query("SELECT * FROM prediction_history WHERE id = :id AND userId = :userId LIMIT 1")
    suspend fun getPredictionHistoryById(id: Long, userId: String): PredictionHistoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPredictionHistory(entity: PredictionHistoryEntity): Long

    @Query("DELETE FROM prediction_history WHERE id = :id AND userId = :userId")
    suspend fun deletePredictionHistory(id: Long, userId: String)

    @Query("DELETE FROM prediction_history WHERE userId = :userId")
    suspend fun deleteAllPredictionHistory(userId: String)

    @Query("SELECT * FROM prediction_history WHERE userId = :userId AND pendingSync = 1")
    suspend fun getPendingSyncPredictionHistory(userId: String): List<PredictionHistoryEntity>
}
