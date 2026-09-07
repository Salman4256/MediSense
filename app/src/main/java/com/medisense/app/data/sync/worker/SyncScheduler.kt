package com.medisense.app.data.sync.worker

import android.content.Context
import androidx.work.*
import com.medisense.app.domain.security.SecureLogger
import java.util.concurrent.TimeUnit

/**
 * Utility to schedule and manage periodic and immediate background sync tasks.
 */
object SyncScheduler {

    private const val TAG = "SyncScheduler"

    /**
     * Schedules periodic background sync every [intervalHours] (default 6 hours)
     * when the device has an active network connection.
     */
    fun schedulePeriodicSync(context: Context, intervalHours: Long = 6) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true)
            .build()

        val periodicWorkRequest = PeriodicWorkRequestBuilder<HealthSyncWorker>(
            intervalHours, TimeUnit.HOURS,
            15, TimeUnit.MINUTES // Flex interval
        )
            .setConstraints(constraints)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                WorkRequest.MIN_BACKOFF_MILLIS,
                TimeUnit.MILLISECONDS
            )
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            HealthSyncWorker.WORK_NAME_PERIODIC,
            ExistingPeriodicWorkPolicy.KEEP,
            periodicWorkRequest
        )
        SecureLogger.d(TAG, "Periodic health sync scheduled every $intervalHours hours.")
    }

    /**
     * Enqueues an immediate one-time sync task when network is available.
     */
    fun scheduleImmediateSync(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val oneTimeWorkRequest = OneTimeWorkRequestBuilder<HealthSyncWorker>()
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            HealthSyncWorker.WORK_NAME_IMMEDIATE,
            ExistingWorkPolicy.REPLACE,
            oneTimeWorkRequest
        )
        SecureLogger.d(TAG, "Immediate one-time health sync scheduled.")
    }

    /**
     * Cancels all scheduled sync jobs (e.g., during account sign-out or data reset).
     */
    fun cancelAllSync(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(HealthSyncWorker.WORK_NAME_PERIODIC)
        WorkManager.getInstance(context).cancelUniqueWork(HealthSyncWorker.WORK_NAME_IMMEDIATE)
        SecureLogger.d(TAG, "All background health sync jobs cancelled.")
    }
}
