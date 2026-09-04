package com.medisense.app.data.repository

import com.medisense.app.data.local.dao.AppointmentDao
import com.medisense.app.data.local.entity.AppointmentEntity
import com.medisense.app.data.remote.supabase.AuthService
import com.medisense.app.notification.AppointmentScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppointmentRepository @Inject constructor(
    private val appointmentDao: AppointmentDao,
    private val authService: AuthService,
    private val appointmentScheduler: AppointmentScheduler
) {

    fun getCurrentUserId(): String {
        return authService.getCurrentUserId() ?: "offline-user"
    }

    fun observeAppointments(): Flow<List<AppointmentEntity>> {
        val userId = getCurrentUserId()
        return appointmentDao.observeAppointments(userId)
    }

    fun observeUpcomingAppointments(): Flow<List<AppointmentEntity>> {
        val userId = getCurrentUserId()
        return appointmentDao.observeUpcomingAppointments(userId, System.currentTimeMillis())
    }

    fun observeCompletedAppointments(): Flow<List<AppointmentEntity>> {
        val userId = getCurrentUserId()
        return appointmentDao.observeCompletedAppointments(userId)
    }

    fun observeCancelledAppointments(): Flow<List<AppointmentEntity>> {
        val userId = getCurrentUserId()
        return appointmentDao.observeCancelledAppointments(userId)
    }

    suspend fun getAppointment(id: Long): AppointmentEntity? = withContext(Dispatchers.IO) {
        val userId = getCurrentUserId()
        appointmentDao.getAppointment(id, userId)
    }

    suspend fun addAppointment(
        doctorName: String,
        clinicName: String,
        appointmentType: String,
        appointmentDate: String,
        appointmentTime: String,
        appointmentTimestamp: Long,
        reminderMinutesBefore: Int,
        notes: String?
    ): Result<Long> = withContext(Dispatchers.IO) {
        try {
            val userId = getCurrentUserId()
            val appointment = AppointmentEntity(
                userId = userId,
                doctorName = doctorName.trim(),
                clinicName = clinicName.trim(),
                appointmentType = appointmentType,
                appointmentDate = appointmentDate,
                appointmentTime = appointmentTime,
                appointmentTimestamp = appointmentTimestamp,
                reminderMinutesBefore = reminderMinutesBefore,
                notes = notes?.trim()?.ifBlank { null },
                status = "SCHEDULED",
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis(),
                pendingSync = true
            )

            val id = appointmentDao.insertAppointment(appointment)
            val createdAppointment = appointment.copy(id = id)

            // Schedule local exact reminder
            appointmentScheduler.scheduleReminder(createdAppointment)
            Result.success(id)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateAppointment(appointment: AppointmentEntity): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val updated = appointment.copy(
                updatedAt = System.currentTimeMillis(),
                pendingSync = true
            )
            appointmentDao.updateAppointment(updated)

            if (updated.status == "SCHEDULED") {
                appointmentScheduler.cancelReminder(updated)
                appointmentScheduler.scheduleReminder(updated)
            } else {
                appointmentScheduler.cancelReminder(updated)
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteAppointment(appointment: AppointmentEntity): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            appointmentScheduler.cancelReminder(appointment)
            appointmentDao.deleteAppointment(appointment)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteAppointmentById(id: Long): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val userId = getCurrentUserId()
            val appt = appointmentDao.getAppointment(id, userId)
            if (appt != null) {
                appointmentScheduler.cancelReminder(appt)
            }
            appointmentDao.deleteAppointmentById(id, userId)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun markAppointmentCompleted(id: Long): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val userId = getCurrentUserId()
            val appointment = appointmentDao.getAppointment(id, userId)
            if (appointment != null) {
                appointmentScheduler.cancelReminder(appointment)
                appointmentDao.updateAppointmentStatus(id, userId, "COMPLETED")
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun cancelAppointment(id: Long): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val userId = getCurrentUserId()
            val appointment = appointmentDao.getAppointment(id, userId)
            if (appointment != null) {
                appointmentScheduler.cancelReminder(appointment)
                appointmentDao.updateAppointmentStatus(id, userId, "CANCELLED")
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
