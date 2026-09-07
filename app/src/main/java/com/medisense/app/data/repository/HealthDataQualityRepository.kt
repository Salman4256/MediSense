package com.medisense.app.data.repository

import com.medisense.app.data.local.dao.*
import com.medisense.app.data.remote.supabase.AuthService
import com.medisense.app.domain.model.HealthDataQualityStatus
import com.medisense.app.domain.model.HealthDataQualitySummary
import com.medisense.app.domain.model.SecurityAuditEventType
import com.medisense.app.domain.quality.HealthDataQualityEngine
import com.medisense.app.domain.security.SecureLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for evaluating and observing user-scoped health data quality metrics.
 * Integrates with local Room DAOs, Auth session isolation, and Module 14 audit logging.
 */
@Singleton
class HealthDataQualityRepository @Inject constructor(
    private val authService: AuthService,
    private val qualityEngine: HealthDataQualityEngine,
    private val healthProfileDao: HealthProfileDao,
    private val medicationDao: MedicationDao,
    private val medicationHistoryDao: MedicationHistoryDao,
    private val appointmentDao: AppointmentDao,
    private val predictionHistoryDao: PredictionHistoryDao,
    private val syncMetadataDao: SyncMetadataDao,
    private val securityAuditRepository: SecurityAuditRepository
) {

    /**
     * Executes a complete, deterministic health data quality evaluation for the currently authenticated user.
     */
    suspend fun evaluateDataQuality(): HealthDataQualitySummary = withContext(Dispatchers.IO) {
        val userId = authService.getCurrentUserId()
        if (userId.isNullOrBlank() || userId == "local-user") {
            SecureLogger.d(TAG, "Data quality evaluation skipped: No authenticated user session.")
            return@withContext HealthDataQualitySummary(
                status = HealthDataQualityStatus.INSUFFICIENT_DATA,
                qualityScore = null,
                totalChecks = 0,
                passedChecks = 0,
                errorCount = 0,
                warningCount = 0,
                infoCount = 0,
                issues = emptyList(),
                categoryBreakdown = emptyMap(),
                evaluatedTimestamp = System.currentTimeMillis()
            )
        }

        // Fetch user-scoped records from Room database
        val profile = healthProfileDao.getHealthProfile(userId)
        val medications = medicationDao.getAllMedicationsForUserSync(userId)
        val historyList = medicationHistoryDao.getAllHistoryForUserSync(userId)
        val appointments = appointmentDao.getAllAppointmentsForUserSync(userId)
        val predictions = predictionHistoryDao.getAllPredictionHistoryForUserSync(userId)
        val syncMeta = syncMetadataDao.getSyncMetadata(userId)

        val summary = qualityEngine.evaluate(
            profile = profile,
            medications = medications,
            medicationHistory = historyList,
            appointments = appointments,
            predictions = predictions,
            syncMetadata = syncMeta
        )

        // Record privacy-safe telemetry event in Module 14 audit log
        try {
            securityAuditRepository.recordEvent(
                eventType = SecurityAuditEventType.DATA_QUALITY_CHECKED,
                customDescription = "Health data quality evaluated (status=${summary.status.name}, score=${summary.qualityScore ?: 0}%)"
            )
        } catch (e: Exception) {
            SecureLogger.e(TAG, "Failed to record audit telemetry for quality evaluation", e)
        }

        SecureLogger.d(TAG, "Data quality evaluated: ${summary.status.name}, score=${summary.qualityScore}%, issues=${summary.issues.size}")
        summary
    }

    /**
     * Emits the evaluated health data quality summary as a Flow.
     */
    fun observeDataQuality(): Flow<HealthDataQualitySummary> = flow {
        emit(evaluateDataQuality())
    }.flowOn(Dispatchers.IO)

    companion object {
        private const val TAG = "HealthDataQualityRepo"
    }
}
