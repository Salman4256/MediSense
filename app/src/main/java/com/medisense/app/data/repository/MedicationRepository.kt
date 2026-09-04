package com.medisense.app.data.repository

import com.medisense.app.data.local.dao.MedicationDao
import com.medisense.app.data.local.dao.MedicationHistoryDao
import com.medisense.app.data.local.entity.MedicationEntity
import com.medisense.app.data.local.entity.MedicationHistoryEntity
import com.medisense.app.data.remote.supabase.AuthService
import com.medisense.app.notification.MedicationNotificationManager
import com.medisense.app.notification.MedicationScheduler
import com.medisense.app.utils.AdherenceStats
import com.medisense.app.utils.MedicationDateTimeUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MedicationRepository @Inject constructor(
    private val medicationDao: MedicationDao,
    private val historyDao: MedicationHistoryDao,
    private val scheduler: MedicationScheduler,
    private val authService: AuthService
) {

    /**
     * Returns the currently authenticated Supabase user's UUID.
     */
    fun getCurrentUserId(): String = authService.getCurrentUserId() ?: "offline-user"

    /**
     * Observes all medications for the authenticated user.
     */
    fun observeMedications(): Flow<List<MedicationEntity>> {
        return medicationDao.getMedicationsForUser(getCurrentUserId())
    }

    fun getAllMedications(): Flow<List<MedicationEntity>> = observeMedications()

    /**
     * Observes active medications for the authenticated user.
     */
    fun observeActiveMedications(): Flow<List<MedicationEntity>> {
        return medicationDao.getActiveMedicationsForUser(getCurrentUserId())
    }

    fun getActiveMedications(): Flow<List<MedicationEntity>> = observeActiveMedications()

    /**
     * Retrieves a single medication by ID ensuring user ownership.
     */
    suspend fun getMedicationById(medicationId: Long): MedicationEntity? = withContext(Dispatchers.IO) {
        medicationDao.getMedicationById(medicationId, getCurrentUserId())
    }

    /**
     * Inserts a new medication record into Room database and schedules the next occurrence.
     */
    suspend fun addMedication(medication: MedicationEntity): Result<Long> = withContext(Dispatchers.IO) {
        try {
            val currentUserId = getCurrentUserId()
            val userScoped = medication.copy(
                userId = currentUserId,
                pendingSync = true,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
            val id = medicationDao.insertMedication(userScoped)
            val saved = userScoped.copy(id = id)

            if (saved.active) {
                scheduler.scheduleNextReminder(saved)
            }

            Result.success(id)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Updates an existing medication record and reschedules the next occurrence.
     */
    suspend fun updateMedication(medication: MedicationEntity): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val currentUserId = getCurrentUserId()
            val existing = medicationDao.getMedicationById(medication.id, currentUserId)
                ?: return@withContext Result.failure(IllegalStateException("Medication not found or unauthorized"))

            val updated = medication.copy(
                userId = currentUserId,
                pendingSync = true,
                createdAt = existing.createdAt,
                updatedAt = System.currentTimeMillis()
            )
            medicationDao.updateMedication(updated)

            if (updated.active) {
                scheduler.scheduleNextReminder(updated)
            } else {
                scheduler.cancelReminder(updated.id)
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Deletes a medication record and cancels its future reminder alarm.
     */
    suspend fun deleteMedication(medicationId: Long): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val currentUserId = getCurrentUserId()
            scheduler.cancelReminder(medicationId)
            medicationDao.deleteMedicationById(medicationId, currentUserId)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteMedication(medication: MedicationEntity): Result<Unit> {
        return deleteMedication(medication.id)
    }

    /**
     * Toggles active state for a medication.
     */
    suspend fun toggleActive(medicationId: Long, active: Boolean): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val currentUserId = getCurrentUserId()
            val existing = medicationDao.getMedicationById(medicationId, currentUserId)
                ?: return@withContext Result.failure(IllegalStateException("Medication not found"))

            val updated = existing.copy(
                active = active,
                updatedAt = System.currentTimeMillis(),
                pendingSync = true
            )
            medicationDao.updateMedication(updated)

            if (active) {
                scheduler.scheduleNextReminder(updated)
            } else {
                scheduler.cancelReminder(medicationId)
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Observes medication adherence history for the current user.
     */
    fun getHistory(): Flow<List<MedicationHistoryEntity>> {
        return historyDao.getHistoryForUser(getCurrentUserId())
    }

    /**
     * Observes adherence history for a specific medication.
     */
    fun getHistoryForMedication(medicationId: Long): Flow<List<MedicationHistoryEntity>> {
        return historyDao.getHistoryForMedication(medicationId, getCurrentUserId())
    }

    /**
     * Retrieves history for a specific scheduled occurrence.
     */
    suspend fun getOccurrenceHistory(
        medicationId: Long,
        scheduledDate: Long,
        scheduledTime: String
    ): MedicationHistoryEntity? = withContext(Dispatchers.IO) {
        historyDao.getOccurrenceHistory(medicationId, scheduledDate, scheduledTime, getCurrentUserId())
    }

    /**
     * Records a status (TAKEN, SKIPPED, or MISSED) for a scheduled dose occurrence.
     */
    suspend fun recordStatus(
        medicationId: Long,
        scheduledDate: Long,
        scheduledTime: String,
        status: String,
        actionTime: Long? = System.currentTimeMillis()
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val currentUserId = getCurrentUserId()
            val medication = medicationDao.getMedicationById(medicationId, currentUserId)

            val existing = historyDao.getOccurrenceHistory(medicationId, scheduledDate, scheduledTime, currentUserId)
                ?: historyDao.findExistingRecord(medicationId, scheduledDate, scheduledTime)

            if (existing != null) {
                // If it was already marked TAKEN, do not overwrite to SKIPPED
                if (existing.status.equals("TAKEN", ignoreCase = true) && status.equals("SKIPPED", ignoreCase = true)) {
                    return@withContext Result.success(Unit)
                }

                historyDao.updateHistory(
                    existing.copy(
                        status = status.uppercase(),
                        actionTime = actionTime
                    )
                )
            } else {
                val record = MedicationHistoryEntity(
                    medicationId = medicationId,
                    userId = currentUserId,
                    medicineName = medication?.medicineName ?: "Medication",
                    dosage = if (medication != null) "${medication.dosage} ${medication.dosageUnit}" else "",
                    scheduledDate = scheduledDate,
                    scheduledTime = scheduledTime,
                    actionTime = actionTime,
                    status = status.uppercase()
                )
                historyDao.insertHistory(record)
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Observes today's adherence statistics.
     */
    fun getTodayAdherence(): Flow<AdherenceStats> {
        val today = MedicationDateTimeUtils.getStartOfDay()
        return historyDao.getHistoryForDateFlow(getCurrentUserId(), today).map { list ->
            MedicationDateTimeUtils.calculateAdherence(list)
        }
    }

    /**
     * Observes weekly (last 7 days) adherence statistics.
     */
    fun getWeeklyAdherence(): Flow<AdherenceStats> {
        val start7Days = MedicationDateTimeUtils.getStartOf7DaysAgo()
        val endToday = MedicationDateTimeUtils.getEndOfDay()
        return historyDao.getHistoryForDateRangeFlow(getCurrentUserId(), start7Days, endToday).map { list ->
            MedicationDateTimeUtils.calculateAdherence(list)
        }
    }

    /**
     * Observes overall adherence statistics calculated from complete user history.
     */
    fun getOverallAdherence(): Flow<AdherenceStats> {
        return historyDao.getHistoryForUser(getCurrentUserId()).map { historyList ->
            MedicationDateTimeUtils.calculateAdherence(historyList)
        }
    }

    fun getAdherenceStats(): Flow<AdherenceStats> = getOverallAdherence()

    /**
     * Evaluates overdue scheduled occurrences that have passed the grace period without being marked TAKEN or SKIPPED.
     */
    suspend fun evaluateMissedDoses(
        gracePeriodMillis: Long = MedicationDateTimeUtils.DEFAULT_GRACE_PERIOD_MILLIS
    ) = withContext(Dispatchers.IO) {
        try {
            val currentUserId = getCurrentUserId()
            val activeMeds = medicationDao.getActiveMedicationsForUserSync(currentUserId)
            val now = System.currentTimeMillis()
            val today = MedicationDateTimeUtils.getStartOfDay(now)
            val timeFormat = java.text.SimpleDateFormat("hh:mm a", java.util.Locale.getDefault())

            for (med in activeMeds) {
                val slots = MedicationDateTimeUtils.getScheduledSlotsForDate(med, today)
                for (slotMillis in slots) {
                    if (now > slotMillis + gracePeriodMillis) {
                        val timeStr = timeFormat.format(java.util.Date(slotMillis))
                        val existing = historyDao.getOccurrenceHistory(med.id, today, timeStr, currentUserId)
                            ?: historyDao.findExistingRecord(med.id, today, timeStr)

                        if (existing == null) {
                            val missedRecord = MedicationHistoryEntity(
                                medicationId = med.id,
                                userId = currentUserId,
                                medicineName = med.medicineName,
                                dosage = "${med.dosage} ${med.dosageUnit}",
                                scheduledDate = today,
                                scheduledTime = timeStr,
                                actionTime = slotMillis + gracePeriodMillis,
                                status = "MISSED"
                            )
                            historyDao.insertHistory(missedRecord)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // Safe evaluation error handling
        }
    }

    suspend fun recordStatus(
        medicationId: Long,
        scheduledTime: String,
        status: String
    ): Result<Unit> {
        val today = MedicationDateTimeUtils.getStartOfDay()
        return recordStatus(medicationId, today, scheduledTime, status)
    }

    suspend fun recordTaken(
        medicationId: Long,
        scheduledDate: Long,
        scheduledTime: String,
        actionTime: Long = System.currentTimeMillis()
    ): Result<Unit> = recordStatus(medicationId, scheduledDate, scheduledTime, "TAKEN", actionTime)

    suspend fun recordTaken(
        medicationId: Long,
        scheduledTime: String
    ): Result<Unit> = recordTaken(medicationId, MedicationDateTimeUtils.getStartOfDay(), scheduledTime)

    suspend fun recordSkipped(
        medicationId: Long,
        scheduledDate: Long,
        scheduledTime: String,
        actionTime: Long = System.currentTimeMillis()
    ): Result<Unit> = recordStatus(medicationId, scheduledDate, scheduledTime, "SKIPPED", actionTime)

    suspend fun recordSkipped(
        medicationId: Long,
        scheduledTime: String
    ): Result<Unit> = recordSkipped(medicationId, MedicationDateTimeUtils.getStartOfDay(), scheduledTime)

    suspend fun recordMissed(
        medicationId: Long,
        scheduledDate: Long,
        scheduledTime: String,
        actionTime: Long = System.currentTimeMillis()
    ): Result<Unit> = recordStatus(medicationId, scheduledDate, scheduledTime, "MISSED", actionTime)

    suspend fun recordMissed(
        medicationId: Long,
        scheduledTime: String
    ): Result<Unit> = recordMissed(medicationId, MedicationDateTimeUtils.getStartOfDay(), scheduledTime)

    /**
     * Scans for past scheduled medication dose slots (e.g. while the phone was switched off or out of charge),
     * fires missed reminder notifications, logs MISSED history entries, and reschedules future alarms.
     */
    suspend fun checkAndHandleMissedDoses(context: android.content.Context) = withContext(Dispatchers.IO) {
        try {
            val now = System.currentTimeMillis()
            val today = MedicationDateTimeUtils.getStartOfDay(now)
            val yesterday = today - (24 * 60 * 60 * 1000L)

            val activeMeds = medicationDao.getAllActiveMedicationsSync()
            for (med in activeMeds) {
                val checkDates = listOf(yesterday, today)
                for (dateMillis in checkDates) {
                    val slots = MedicationDateTimeUtils.getScheduledSlotsForDate(med, dateMillis)
                    for (slotMillis in slots) {
                        if (slotMillis < now && (now - slotMillis) <= (24 * 60 * 60 * 1000L)) {
                            val timeStr = MedicationDateTimeUtils.formatTime12H(Date(slotMillis))
                            val existing = historyDao.getOccurrenceHistory(med.id, dateMillis, timeStr, med.userId)
                                ?: historyDao.findExistingRecord(med.id, dateMillis, timeStr)

                            if (existing == null) {
                                MedicationNotificationManager.showMissedDoseReminderNotification(
                                    context = context,
                                    medicationId = med.id,
                                    userId = med.userId,
                                    medicineName = med.medicineName,
                                    dosage = "${med.dosage} ${med.dosageUnit}",
                                    instructions = med.instructions,
                                    scheduledDate = dateMillis,
                                    scheduledTime = timeStr
                                )

                                val missedRecord = MedicationHistoryEntity(
                                    medicationId = med.id,
                                    userId = med.userId,
                                    medicineName = med.medicineName,
                                    dosage = "${med.dosage} ${med.dosageUnit}",
                                    scheduledDate = dateMillis,
                                    scheduledTime = timeStr,
                                    actionTime = now,
                                    status = "MISSED"
                                )
                                historyDao.insertHistory(missedRecord)
                            }
                        }
                    }
                }
                scheduler.scheduleNextReminder(med)
            }
        } catch (e: Exception) {
            // Safe fallback
        }
    }
}
