package com.medisense.app.data.repository

import com.medisense.app.data.local.dao.MedicationDao
import com.medisense.app.data.local.dao.MedicationHistoryDao
import com.medisense.app.data.local.entity.MedicationEntity
import com.medisense.app.data.local.entity.MedicationHistoryEntity
import com.medisense.app.data.remote.firebase.FirebaseAuthService
import com.medisense.app.notification.MedicationScheduler
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

data class AdherenceStats(
    val totalDoses: Int = 0,
    val takenDoses: Int = 0,
    val skippedDoses: Int = 0,
    val missedDoses: Int = 0,
    val adherencePercentage: Int = 0
)

@Singleton
class MedicationRepository @Inject constructor(
    private val medicationDao: MedicationDao,
    private val historyDao: MedicationHistoryDao,
    private val scheduler: MedicationScheduler,
    private val firebaseAuthService: FirebaseAuthService
) {

    fun getCurrentUserId(): String {
        return firebaseAuthService.getCurrentUserId() ?: "local-user"
    }

    fun getAllMedications(): Flow<List<MedicationEntity>> {
        return medicationDao.getAllMedications(getCurrentUserId())
    }

    fun getActiveMedications(): Flow<List<MedicationEntity>> {
        return medicationDao.getActiveMedications(getCurrentUserId())
    }

    fun getMedicationById(id: Long): Flow<MedicationEntity?> {
        return medicationDao.getMedicationById(id)
    }

    suspend fun getMedicationByIdSnapshot(id: Long): MedicationEntity? {
        return medicationDao.getMedicationByIdSnapshot(id)
    }

    suspend fun saveMedication(medication: MedicationEntity): Long {
        val userId = getCurrentUserId()
        val isNew = medication.id == 0L
        val entityToSave = medication.copy(
            userId = userId,
            updatedAt = System.currentTimeMillis()
        )

        return if (isNew) {
            val generatedId = medicationDao.insertMedication(entityToSave)
            val savedEntity = entityToSave.copy(id = generatedId)
            if (savedEntity.isActive) {
                scheduler.scheduleMedication(savedEntity)
            }
            Timber.d("Inserted new medication ID %d and scheduled reminders", generatedId)
            generatedId
        } else {
            // Cancel previous alarms first
            scheduler.cancelMedication(entityToSave.id)
            medicationDao.updateMedication(entityToSave)
            if (entityToSave.isActive) {
                scheduler.scheduleMedication(entityToSave)
            }
            Timber.d("Updated medication ID %d and rescheduled reminders", entityToSave.id)
            entityToSave.id
        }
    }

    suspend fun deleteMedication(medication: MedicationEntity) {
        scheduler.cancelMedication(medication.id)
        medicationDao.deleteMedication(medication)
        Timber.d("Deleted medication ID %d and canceled alarms", medication.id)
    }

    suspend fun toggleMedicationActive(id: Long, isActive: Boolean) {
        if (!isActive) {
            scheduler.cancelMedication(id)
        }
        medicationDao.updateActiveStatus(id, isActive)
        if (isActive) {
            val updated = medicationDao.getMedicationByIdSnapshot(id)
            if (updated != null) {
                scheduler.scheduleMedication(updated)
            }
        }
        Timber.d("Toggled active status of medication ID %d to %b", id, isActive)
    }

    fun getAllHistory(): Flow<List<MedicationHistoryEntity>> {
        return historyDao.getAllHistory()
    }

    fun getHistoryForMedication(medicationId: Long): Flow<List<MedicationHistoryEntity>> {
        return historyDao.getHistoryForMedication(medicationId)
    }

    fun getHistoryForDateRange(startTime: Long, endTime: Long): Flow<List<MedicationHistoryEntity>> {
        return historyDao.getHistoryForDateRange(startTime, endTime)
    }

    suspend fun logHistoryEvent(
        medicationId: Long,
        medicationName: String,
        dosage: String,
        scheduledTime: Long,
        status: String,
        notes: String? = null
    ): Long {
        return historyDao.insertHistory(
            MedicationHistoryEntity(
                medicationId = medicationId,
                medicationName = medicationName,
                dosage = dosage,
                scheduledTime = scheduledTime,
                actualTime = System.currentTimeMillis(),
                status = status,
                notes = notes
            )
        )
    }

    fun getAdherenceStats(): Flow<AdherenceStats> {
        return historyDao.getAllHistory().map { historyList ->
            if (historyList.isEmpty()) {
                AdherenceStats()
            } else {
                val total = historyList.size
                val taken = historyList.count { it.status.equals("TAKEN", ignoreCase = true) }
                val skipped = historyList.count { it.status.equals("SKIPPED", ignoreCase = true) }
                val missed = historyList.count { it.status.equals("MISSED", ignoreCase = true) }
                val rate = if (total > 0) ((taken.toDouble() / total.toDouble()) * 100).toInt() else 0
                AdherenceStats(
                    totalDoses = total,
                    takenDoses = taken,
                    skippedDoses = skipped,
                    missedDoses = missed,
                    adherencePercentage = rate
                )
            }
        }
    }
}
