package com.medisense.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.medisense.app.data.local.entity.MedicationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MedicationDao {

    @Query("SELECT * FROM medications WHERE userId = :userId ORDER BY createdAt DESC")
    fun getMedicationsForUser(userId: String): Flow<List<MedicationEntity>>

    @Query("SELECT * FROM medications WHERE userId = :userId AND active = 1 ORDER BY createdAt DESC")
    fun getActiveMedicationsForUser(userId: String): Flow<List<MedicationEntity>>

    @Query("SELECT * FROM medications WHERE userId = :userId AND active = 1")
    suspend fun getActiveMedicationsForUserSync(userId: String): List<MedicationEntity>

    @Query("SELECT * FROM medications WHERE active = 1")
    suspend fun getAllActiveMedicationsSync(): List<MedicationEntity>

    @Query("SELECT * FROM medications WHERE id = :id AND userId = :userId LIMIT 1")
    suspend fun getMedicationById(id: Long, userId: String): MedicationEntity?

    @Query("SELECT * FROM medications WHERE id = :id LIMIT 1")
    suspend fun getMedicationByIdSync(id: Long): MedicationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMedication(medication: MedicationEntity): Long

    @Update
    suspend fun updateMedication(medication: MedicationEntity)

    @Query("DELETE FROM medications WHERE id = :id AND userId = :userId")
    suspend fun deleteMedicationById(id: Long, userId: String)

    @Query("DELETE FROM medications WHERE userId = :userId")
    suspend fun deleteAllMedicationsForUser(userId: String)
}
