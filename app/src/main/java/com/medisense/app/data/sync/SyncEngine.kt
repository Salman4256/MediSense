package com.medisense.app.data.sync

import com.medisense.app.data.local.dao.*
import com.medisense.app.data.local.entity.*
import com.medisense.app.data.remote.supabase.AuthService
import com.medisense.app.data.repository.SecurityAuditRepository
import com.medisense.app.data.sync.dto.*
import com.medisense.app.data.sync.model.SyncResult
import com.medisense.app.data.sync.model.SyncStatus
import com.medisense.app.data.sync.remote.ISupabaseSyncDataSource
import com.medisense.app.domain.model.SecurityAuditEventType
import com.medisense.app.domain.security.SecureLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Centralized, offline-first synchronization engine.
 * Synchronizes Room database entities with Supabase PostgreSQL using authenticated user UUID isolation
 * and Last-Write-Wins (LWW) deterministic conflict resolution.
 */
@Singleton
open class SyncEngine @Inject constructor(
    private val authService: AuthService,
    private val connectivityChecker: NetworkConnectivityChecker,
    private val remoteDataSource: ISupabaseSyncDataSource,
    private val healthProfileDao: HealthProfileDao,
    private val medicationDao: MedicationDao,
    private val medicationHistoryDao: MedicationHistoryDao,
    private val appointmentDao: AppointmentDao,
    private val predictionHistoryDao: PredictionHistoryDao,
    private val syncMetadataDao: SyncMetadataDao,
    private val securityAuditRepository: SecurityAuditRepository
) {

    suspend fun synchronize(): SyncResult = withContext(Dispatchers.IO) {
        // 1. Verify User Authentication
        val userId = authService.getCurrentUserId()
        if (userId.isNullOrBlank() || userId == "local-user") {
            SecureLogger.d("SyncEngine", "Sync skipped: No authenticated user session.")
            return@withContext SyncResult(
                status = SyncStatus.AUTH_REQUIRED,
                message = "Sign in to synchronize your health records with secure cloud backup."
            )
        }

        // 2. Verify Internet Connectivity
        if (!connectivityChecker.isOnline()) {
            SecureLogger.d("SyncEngine", "Sync skipped: Device is offline.")
            val meta = syncMetadataDao.getSyncMetadata(userId)
            val pendingCount = countPendingRecords(userId)
            syncMetadataDao.upsertSyncMetadata(
                SyncMetadataEntity(
                    userId = userId,
                    lastSyncTimestamp = meta?.lastSyncTimestamp ?: 0L,
                    lastSyncStatus = SyncStatus.OFFLINE.name,
                    lastSyncMessage = "Offline. Local changes remain saved on device.",
                    pendingUploadCount = pendingCount,
                    updatedAt = System.currentTimeMillis()
                )
            )
            return@withContext SyncResult(
                status = SyncStatus.OFFLINE,
                message = "Device is offline. Local changes remain saved on device."
            )
        }

        SecureLogger.i("SyncEngine", "Starting synchronization cycle for user: $userId")
        val metadata = syncMetadataDao.getSyncMetadata(userId)
        val lastSyncCheckpoint = metadata?.lastSyncTimestamp ?: 0L

        var uploadedCount = 0
        var downloadedCount = 0
        var conflictsResolved = 0
        var failedCount = 0

        try {
            // A. Sync Health Profile
            val profileUploadResult = syncHealthProfileUpload(userId)
            uploadedCount += profileUploadResult
            val profileDownloadResult = syncHealthProfileDownload(userId)
            downloadedCount += profileDownloadResult

            // B. Sync Medications
            val (medsUploaded, medsFailed) = syncMedicationsUpload(userId)
            uploadedCount += medsUploaded
            failedCount += medsFailed
            val (medsDownloaded, medsConflicts) = syncMedicationsDownload(userId, lastSyncCheckpoint)
            downloadedCount += medsDownloaded
            conflictsResolved += medsConflicts

            // C. Sync Medication History
            val (historyUploaded, historyFailed) = syncMedicationHistoryUpload(userId)
            uploadedCount += historyUploaded
            failedCount += historyFailed
            val historyDownloaded = syncMedicationHistoryDownload(userId, lastSyncCheckpoint)
            downloadedCount += historyDownloaded

            // D. Sync Appointments
            val (apptsUploaded, apptsFailed) = syncAppointmentsUpload(userId)
            uploadedCount += apptsUploaded
            failedCount += apptsFailed
            val (apptsDownloaded, apptsConflicts) = syncAppointmentsDownload(userId, lastSyncCheckpoint)
            downloadedCount += apptsDownloaded
            conflictsResolved += apptsConflicts

            // E. Sync Prediction History
            val (predsUploaded, predsFailed) = syncPredictionHistoryUpload(userId)
            uploadedCount += predsUploaded
            failedCount += predsFailed
            val predsDownloaded = syncPredictionHistoryDownload(userId, lastSyncCheckpoint)
            downloadedCount += predsDownloaded

            val now = System.currentTimeMillis()
            val remainingPending = countPendingRecords(userId)
            val overallStatus = if (failedCount == 0) SyncStatus.SUCCESS else SyncStatus.PARTIAL_SUCCESS
            val resultMessage = if (failedCount == 0) {
                "Synchronized successfully ($uploadedCount uploaded, $downloadedCount updated)."
            } else {
                "Synchronized with $failedCount partial failures."
            }

            syncMetadataDao.upsertSyncMetadata(
                SyncMetadataEntity(
                    userId = userId,
                    lastSyncTimestamp = now,
                    lastSyncStatus = overallStatus.name,
                    lastSyncMessage = resultMessage,
                    pendingUploadCount = remainingPending,
                    failedRecordCount = failedCount,
                    updatedAt = now
                )
            )

            // Non-sensitive telemetry audit event
            securityAuditRepository.recordEvent(
                eventType = SecurityAuditEventType.CLOUD_SYNC,
                customDescription = "Cloud data synchronization completed"
            )

            SecureLogger.i("SyncEngine", "Sync completed: $uploadedCount uploaded, $downloadedCount downloaded, $failedCount failed.")

            SyncResult(
                status = overallStatus,
                recordsUploaded = uploadedCount,
                recordsDownloaded = downloadedCount,
                conflictsResolved = conflictsResolved,
                recordsFailed = failedCount,
                timestamp = now,
                message = resultMessage
            )
        } catch (e: Exception) {
            SecureLogger.e("SyncEngine", "Critical error during synchronization", e)
            val now = System.currentTimeMillis()
            val remainingPending = countPendingRecords(userId)
            syncMetadataDao.upsertSyncMetadata(
                SyncMetadataEntity(
                    userId = userId,
                    lastSyncTimestamp = lastSyncCheckpoint,
                    lastSyncStatus = SyncStatus.FAILED.name,
                    lastSyncMessage = "Sync failed: Temporary network or server issue.",
                    pendingUploadCount = remainingPending,
                    failedRecordCount = failedCount + 1,
                    updatedAt = now
                )
            )
            SyncResult(
                status = SyncStatus.FAILED,
                recordsUploaded = uploadedCount,
                recordsDownloaded = downloadedCount,
                conflictsResolved = conflictsResolved,
                recordsFailed = failedCount + 1,
                timestamp = now,
                message = "Synchronization failed. Offline data remains safe."
            )
        }
    }

    // ==========================================
    // HEALTH PROFILE SYNC
    // ==========================================
    private suspend fun syncHealthProfileUpload(userId: String): Int {
        val pending = healthProfileDao.getPendingSyncProfileForUser(userId) ?: return 0
        val dto = HealthProfileDto(
            id = if (pending.id.isNotBlank()) pending.id else UUID.randomUUID().toString(),
            userId = userId,
            fullName = pending.fullName,
            dateOfBirth = pending.dateOfBirth,
            gender = pending.gender,
            bloodGroup = pending.bloodGroup,
            height = pending.height,
            weight = pending.weight,
            allergies = pending.allergies,
            existingDiseases = pending.existingDiseases,
            currentMedications = pending.currentMedications,
            familyHistory = pending.familyHistory,
            emergencyContactName = pending.emergencyContactName,
            emergencyContactNumber = pending.emergencyContactNumber,
            notes = pending.notes,
            createdAt = pending.createdAt,
            updatedAt = pending.updatedAt
        )
        val success = remoteDataSource.upsertHealthProfile(dto)
        return if (success) {
            healthProfileDao.markProfileSynced(userId)
            1
        } else 0
    }

    private suspend fun syncHealthProfileDownload(userId: String): Int {
        val remote = remoteDataSource.fetchHealthProfile(userId) ?: return 0
        val local = healthProfileDao.getHealthProfile(userId)
        if (local == null) {
            val entity = HealthProfileEntity(
                id = remote.id,
                userId = userId,
                fullName = remote.fullName,
                dateOfBirth = remote.dateOfBirth,
                gender = remote.gender,
                bloodGroup = remote.bloodGroup,
                height = remote.height,
                weight = remote.weight,
                allergies = remote.allergies,
                existingDiseases = remote.existingDiseases,
                currentMedications = remote.currentMedications,
                familyHistory = remote.familyHistory,
                emergencyContactName = remote.emergencyContactName,
                emergencyContactNumber = remote.emergencyContactNumber,
                notes = remote.notes,
                createdAt = remote.createdAt,
                updatedAt = remote.updatedAt,
                pendingSync = false
            )
            healthProfileDao.insertHealthProfile(entity)
            return 1
        }
        return 0
    }

    // ==========================================
    // MEDICATIONS SYNC
    // ==========================================
    private suspend fun syncMedicationsUpload(userId: String): Pair<Int, Int> {
        val pendingList = medicationDao.getPendingSyncMedications(userId)
        if (pendingList.isEmpty()) return 0 to 0

        val dtos = pendingList.map { entity ->
            MedicationDto(
                id = entity.id,
                userId = userId,
                medicineName = entity.medicineName,
                dosage = entity.dosage,
                dosageUnit = entity.dosageUnit,
                frequency = entity.frequency,
                scheduledTimes = entity.scheduledTimes,
                startDate = entity.startDate,
                endDate = entity.endDate,
                instructions = entity.instructions,
                active = entity.active,
                createdAt = entity.createdAt,
                updatedAt = entity.updatedAt,
                isDeleted = false
            )
        }

        val success = remoteDataSource.upsertMedications(dtos)
        return if (success) {
            for (item in pendingList) {
                medicationDao.markMedicationSynced(item.id, userId)
            }
            pendingList.size to 0
        } else {
            0 to pendingList.size
        }
    }

    private suspend fun syncMedicationsDownload(userId: String, sinceTimestamp: Long): Pair<Int, Int> {
        val remoteList = remoteDataSource.fetchMedications(userId, sinceTimestamp)
        if (remoteList.isEmpty()) return 0 to 0

        val localList = medicationDao.getAllMedicationsForUserSync(userId).associateBy { it.id }
        val toInsert = mutableListOf<MedicationEntity>()
        var conflicts = 0

        for (remote in remoteList) {
            val local = localList[remote.id]
            if (local == null) {
                // Insert new remote record
                if (!remote.isDeleted) {
                    toInsert.add(
                        MedicationEntity(
                            id = remote.id,
                            userId = userId,
                            medicineName = remote.medicineName,
                            dosage = remote.dosage,
                            dosageUnit = remote.dosageUnit,
                            frequency = remote.frequency,
                            scheduledTimes = remote.scheduledTimes,
                            startDate = remote.startDate,
                            endDate = remote.endDate,
                            instructions = remote.instructions,
                            active = remote.active,
                            createdAt = remote.createdAt,
                            updatedAt = remote.updatedAt,
                            pendingSync = false
                        )
                    )
                }
            } else {
                // Conflict resolution: Last-Write-Wins (LWW)
                if (remote.updatedAt >= local.updatedAt) {
                    conflicts++
                    if (remote.isDeleted) {
                        medicationDao.deleteMedicationById(local.id, userId)
                    } else {
                        toInsert.add(
                            local.copy(
                                medicineName = remote.medicineName,
                                dosage = remote.dosage,
                                dosageUnit = remote.dosageUnit,
                                frequency = remote.frequency,
                                scheduledTimes = remote.scheduledTimes,
                                startDate = remote.startDate,
                                endDate = remote.endDate,
                                instructions = remote.instructions,
                                active = remote.active,
                                updatedAt = remote.updatedAt,
                                pendingSync = false
                            )
                        )
                    }
                }
            }
        }

        if (toInsert.isNotEmpty()) {
            medicationDao.upsertMedications(toInsert)
        }
        return toInsert.size to conflicts
    }

    // ==========================================
    // MEDICATION HISTORY SYNC
    // ==========================================
    private suspend fun syncMedicationHistoryUpload(userId: String): Pair<Int, Int> {
        val historyList = medicationHistoryDao.getAllHistoryForUserSync(userId)
        if (historyList.isEmpty()) return 0 to 0

        val dtos = historyList.map {
            MedicationHistoryDto(
                id = it.id,
                medicationId = it.medicationId,
                userId = userId,
                medicineName = it.medicineName,
                dosage = it.dosage,
                scheduledDate = it.scheduledDate,
                scheduledTime = it.scheduledTime,
                actionTime = it.actionTime,
                status = it.status,
                updatedAt = it.scheduledDate
            )
        }

        val success = remoteDataSource.upsertMedicationHistory(dtos)
        return if (success) historyList.size to 0 else 0 to historyList.size
    }

    private suspend fun syncMedicationHistoryDownload(userId: String, sinceTimestamp: Long): Int {
        val remoteList = remoteDataSource.fetchMedicationHistory(userId, sinceTimestamp)
        if (remoteList.isEmpty()) return 0

        val localList = medicationHistoryDao.getAllHistoryForUserSync(userId).associateBy { it.id }
        val toInsert = mutableListOf<MedicationHistoryEntity>()

        for (remote in remoteList) {
            val local = localList[remote.id]
            if (local == null) {
                toInsert.add(
                    MedicationHistoryEntity(
                        id = remote.id,
                        medicationId = remote.medicationId,
                        userId = userId,
                        medicineName = remote.medicineName,
                        dosage = remote.dosage,
                        scheduledDate = remote.scheduledDate,
                        scheduledTime = remote.scheduledTime,
                        actionTime = remote.actionTime,
                        status = remote.status
                    )
                )
            }
        }

        if (toInsert.isNotEmpty()) {
            medicationHistoryDao.upsertHistory(toInsert)
        }
        return toInsert.size
    }

    // ==========================================
    // APPOINTMENTS SYNC
    // ==========================================
    private suspend fun syncAppointmentsUpload(userId: String): Pair<Int, Int> {
        val pendingList = appointmentDao.getPendingSyncAppointments(userId)
        if (pendingList.isEmpty()) return 0 to 0

        val dtos = pendingList.map {
            AppointmentDto(
                id = it.id,
                userId = userId,
                doctorName = it.doctorName,
                clinicName = it.clinicName,
                appointmentType = it.appointmentType,
                appointmentDate = it.appointmentDate,
                appointmentTime = it.appointmentTime,
                appointmentTimestamp = it.appointmentTimestamp,
                reminderMinutesBefore = it.reminderMinutesBefore,
                notes = it.notes,
                status = it.status,
                createdAt = it.createdAt,
                updatedAt = it.updatedAt,
                isDeleted = false
            )
        }

        val success = remoteDataSource.upsertAppointments(dtos)
        return if (success) {
            for (item in pendingList) {
                appointmentDao.markAppointmentSynced(item.id, userId)
            }
            pendingList.size to 0
        } else {
            0 to pendingList.size
        }
    }

    private suspend fun syncAppointmentsDownload(userId: String, sinceTimestamp: Long): Pair<Int, Int> {
        val remoteList = remoteDataSource.fetchAppointments(userId, sinceTimestamp)
        if (remoteList.isEmpty()) return 0 to 0

        val localList = appointmentDao.getAllAppointmentsForUserSync(userId).associateBy { it.id }
        val toInsert = mutableListOf<AppointmentEntity>()
        var conflicts = 0

        for (remote in remoteList) {
            val local = localList[remote.id]
            if (local == null) {
                if (!remote.isDeleted) {
                    toInsert.add(
                        AppointmentEntity(
                            id = remote.id,
                            userId = userId,
                            doctorName = remote.doctorName,
                            clinicName = remote.clinicName,
                            appointmentType = remote.appointmentType,
                            appointmentDate = remote.appointmentDate,
                            appointmentTime = remote.appointmentTime,
                            appointmentTimestamp = remote.appointmentTimestamp,
                            reminderMinutesBefore = remote.reminderMinutesBefore,
                            notes = remote.notes,
                            status = remote.status,
                            createdAt = remote.createdAt,
                            updatedAt = remote.updatedAt,
                            pendingSync = false
                        )
                    )
                }
            } else {
                if (remote.updatedAt >= local.updatedAt) {
                    conflicts++
                    if (remote.isDeleted) {
                        appointmentDao.deleteAppointmentById(local.id, userId)
                    } else {
                        toInsert.add(
                            local.copy(
                                doctorName = remote.doctorName,
                                clinicName = remote.clinicName,
                                appointmentType = remote.appointmentType,
                                appointmentDate = remote.appointmentDate,
                                appointmentTime = remote.appointmentTime,
                                appointmentTimestamp = remote.appointmentTimestamp,
                                reminderMinutesBefore = remote.reminderMinutesBefore,
                                notes = remote.notes,
                                status = remote.status,
                                updatedAt = remote.updatedAt,
                                pendingSync = false
                            )
                        )
                    }
                }
            }
        }

        if (toInsert.isNotEmpty()) {
            appointmentDao.upsertAppointments(toInsert)
        }
        return toInsert.size to conflicts
    }

    // ==========================================
    // PREDICTION HISTORY SYNC
    // ==========================================
    private suspend fun syncPredictionHistoryUpload(userId: String): Pair<Int, Int> {
        val pendingList = predictionHistoryDao.getPendingSyncPredictionHistory(userId)
        if (pendingList.isEmpty()) return 0 to 0

        val dtos = pendingList.map {
            PredictionHistoryDto(
                id = it.id,
                userId = userId,
                predictedDisease = it.predictedDisease,
                confidence = it.confidence,
                symptoms = it.symptoms,
                explanationSummary = it.explanationSummary,
                predictionTimestamp = it.predictionTimestamp,
                modelVersion = it.modelVersion,
                updatedAt = it.predictionTimestamp
            )
        }

        val success = remoteDataSource.upsertPredictionHistory(dtos)
        return if (success) {
            for (item in pendingList) {
                predictionHistoryDao.markPredictionSynced(item.id, userId)
            }
            pendingList.size to 0
        } else {
            0 to pendingList.size
        }
    }

    private suspend fun syncPredictionHistoryDownload(userId: String, sinceTimestamp: Long): Int {
        val remoteList = remoteDataSource.fetchPredictionHistory(userId, sinceTimestamp)
        if (remoteList.isEmpty()) return 0

        val localList = predictionHistoryDao.getAllPredictionHistoryForUserSync(userId).associateBy { it.id }
        val toInsert = mutableListOf<PredictionHistoryEntity>()

        for (remote in remoteList) {
            val local = localList[remote.id]
            if (local == null) {
                toInsert.add(
                    PredictionHistoryEntity(
                        id = remote.id,
                        userId = userId,
                        predictedDisease = remote.predictedDisease,
                        confidence = remote.confidence,
                        symptoms = remote.symptoms,
                        explanationSummary = remote.explanationSummary,
                        predictionTimestamp = remote.predictionTimestamp,
                        modelVersion = remote.modelVersion,
                        pendingSync = false
                    )
                )
            }
        }

        if (toInsert.isNotEmpty()) {
            predictionHistoryDao.upsertPredictionHistory(toInsert)
        }
        return toInsert.size
    }

    private suspend fun countPendingRecords(userId: String): Int {
        val profilePending = if (healthProfileDao.getPendingSyncProfileForUser(userId) != null) 1 else 0
        val medPending = medicationDao.getPendingSyncMedications(userId).size
        val apptPending = appointmentDao.getPendingSyncAppointments(userId).size
        val predPending = predictionHistoryDao.getPendingSyncPredictionHistory(userId).size
        return profilePending + medPending + apptPending + predPending
    }
}
