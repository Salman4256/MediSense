package com.medisense.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.medisense.app.data.local.entity.MedicationHistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MedicationHistoryDao {

    @Query("SELECT * FROM medication_history WHERE userId = :userId ORDER BY scheduledDate DESC, scheduledTime DESC")
    fun getHistoryForUser(userId: String): Flow<List<MedicationHistoryEntity>>

    @Query("SELECT * FROM medication_history WHERE medicationId = :medicationId AND userId = :userId ORDER BY scheduledDate DESC")
    fun getHistoryForMedication(medicationId: Long, userId: String): Flow<List<MedicationHistoryEntity>>

    @Query("SELECT * FROM medication_history WHERE medicationId = :medicationId AND scheduledDate = :date AND scheduledTime = :time AND userId = :userId LIMIT 1")
    suspend fun getOccurrenceHistory(medicationId: Long, date: Long, time: String, userId: String): MedicationHistoryEntity?

    @Query("SELECT * FROM medication_history WHERE medicationId = :medicationId AND scheduledDate = :date AND scheduledTime = :time LIMIT 1")
    suspend fun findExistingRecord(medicationId: Long, date: Long, time: String): MedicationHistoryEntity?

    @Query("SELECT * FROM medication_history WHERE userId = :userId AND scheduledDate = :date")
    fun getHistoryForDateFlow(userId: String, date: Long): Flow<List<MedicationHistoryEntity>>

    @Query("SELECT * FROM medication_history WHERE userId = :userId AND scheduledDate BETWEEN :startDate AND :endDate")
    fun getHistoryForDateRangeFlow(userId: String, startDate: Long, endDate: Long): Flow<List<MedicationHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(history: MedicationHistoryEntity): Long

    @Update
    suspend fun updateHistory(history: MedicationHistoryEntity)
}
