package com.medisense.app.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.medisense.app.data.local.entity.AppointmentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AppointmentDao {

    @Query("SELECT * FROM appointments WHERE userId = :userId ORDER BY appointmentTimestamp ASC")
    fun observeAppointments(userId: String): Flow<List<AppointmentEntity>>

    @Query("SELECT * FROM appointments WHERE userId = :userId AND appointmentTimestamp >= :currentTime AND status = 'SCHEDULED' ORDER BY appointmentTimestamp ASC")
    fun observeUpcomingAppointments(userId: String, currentTime: Long): Flow<List<AppointmentEntity>>

    @Query("SELECT * FROM appointments WHERE status = 'SCHEDULED' AND appointmentTimestamp >= :currentTime ORDER BY appointmentTimestamp ASC")
    suspend fun getAllUpcomingScheduledAppointmentsSync(currentTime: Long): List<AppointmentEntity>

    @Query("SELECT * FROM appointments WHERE userId = :userId AND status = 'COMPLETED' ORDER BY appointmentTimestamp DESC")
    fun observeCompletedAppointments(userId: String): Flow<List<AppointmentEntity>>

    @Query("SELECT * FROM appointments WHERE userId = :userId AND status = 'CANCELLED' ORDER BY appointmentTimestamp DESC")
    fun observeCancelledAppointments(userId: String): Flow<List<AppointmentEntity>>

    @Query("SELECT * FROM appointments WHERE id = :id AND userId = :userId LIMIT 1")
    suspend fun getAppointment(id: Long, userId: String): AppointmentEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAppointment(appointment: AppointmentEntity): Long

    @Update
    suspend fun updateAppointment(appointment: AppointmentEntity)

    @Delete
    suspend fun deleteAppointment(appointment: AppointmentEntity)

    @Query("DELETE FROM appointments WHERE id = :id AND userId = :userId")
    suspend fun deleteAppointmentById(id: Long, userId: String)

    @Query("DELETE FROM appointments WHERE userId = :userId")
    suspend fun deleteAllAppointmentsForUser(userId: String)

    @Query("UPDATE appointments SET status = :status, updatedAt = :updatedAt, pendingSync = 1 WHERE id = :id AND userId = :userId")
    suspend fun updateAppointmentStatus(id: Long, userId: String, status: String, updatedAt: Long = System.currentTimeMillis())
}
