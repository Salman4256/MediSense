package com.medisense.app.data.sync.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.medisense.app.data.sync.SyncEngine
import com.medisense.app.data.sync.model.SyncStatus
import com.medisense.app.domain.security.SecureLogger
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Background WorkManager worker for performing scheduled and on-demand health data synchronization.
 * Supports automatic exponential backoff retry on temporary network outages.
 */
@HiltWorker
class HealthSyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val syncEngine: SyncEngine
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            SecureLogger.d(TAG, "Executing background health data synchronization...")
            val syncResult = syncEngine.synchronize()

            when (syncResult.status) {
                SyncStatus.SUCCESS, SyncStatus.PARTIAL_SUCCESS -> {
                    SecureLogger.d(TAG, "Sync worker finished successfully: ${syncResult.message}")
                    Result.success()
                }
                SyncStatus.OFFLINE -> {
                    SecureLogger.d(TAG, "Sync worker deferred: Device offline.")
                    Result.retry()
                }
                SyncStatus.AUTH_REQUIRED -> {
                    SecureLogger.d(TAG, "Sync worker cancelled: No authenticated user session.")
                    Result.failure()
                }
                SyncStatus.FAILED -> {
                    SecureLogger.e(TAG, "Sync worker failed: ${syncResult.message}")
                    if (runAttemptCount < MAX_RETRIES) Result.retry() else Result.failure()
                }
                else -> Result.success()
            }
        } catch (e: Exception) {
            SecureLogger.e(TAG, "Unhandled exception in HealthSyncWorker: ${e.message}")
            if (runAttemptCount < MAX_RETRIES) Result.retry() else Result.failure()
        }
    }

    companion object {
        private const val TAG = "HealthSyncWorker"
        private const val MAX_RETRIES = 3
        const val WORK_NAME_PERIODIC = "medisense_periodic_health_sync"
        const val WORK_NAME_IMMEDIATE = "medisense_immediate_health_sync"
    }
}
