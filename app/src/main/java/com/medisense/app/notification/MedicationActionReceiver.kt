package com.medisense.app.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.medisense.app.data.local.dao.MedicationDao
import com.medisense.app.data.repository.MedicationRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MedicationActionReceiver : BroadcastReceiver() {

    @Inject lateinit var medicationRepository: MedicationRepository
    @Inject lateinit var medicationDao: MedicationDao
    @Inject lateinit var scheduler: MedicationScheduler

    override fun onReceive(context: Context, intent: Intent) {
        val medicationId = intent.getLongExtra(MedicationNotificationManager.EXTRA_MEDICATION_ID, 0L)
        val userId = intent.getStringExtra(MedicationNotificationManager.EXTRA_USER_ID) ?: ""
        val scheduledDate = intent.getLongExtra(MedicationNotificationManager.EXTRA_SCHEDULED_DATE, System.currentTimeMillis())
        val scheduledTime = intent.getStringExtra(MedicationNotificationManager.EXTRA_SCHEDULED_TIME) ?: ""
        val notificationId = intent.getIntExtra(MedicationNotificationManager.EXTRA_NOTIFICATION_ID, medicationId.toInt())

        // Dismiss notification immediately on user tap
        MedicationNotificationManager.dismissNotification(context, notificationId)

        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                when (intent.action) {
                    MedicationNotificationManager.ACTION_TAKEN -> {
                        medicationRepository.recordTaken(
                            medicationId = medicationId,
                            scheduledDate = scheduledDate,
                            scheduledTime = scheduledTime
                        )
                        val med = medicationDao.getMedicationById(medicationId, userId)
                        if (med != null && med.active) {
                            scheduler.scheduleNextReminder(med)
                        }
                    }
                    MedicationNotificationManager.ACTION_SNOOZE -> {
                        scheduler.scheduleSnooze(medicationId, 10 * 60 * 1000L)
                    }
                    MedicationNotificationManager.ACTION_SKIP -> {
                        medicationRepository.recordSkipped(
                            medicationId = medicationId,
                            scheduledDate = scheduledDate,
                            scheduledTime = scheduledTime
                        )
                        val med = medicationDao.getMedicationById(medicationId, userId)
                        if (med != null && med.active) {
                            scheduler.scheduleNextReminder(med)
                        }
                    }
                }
            } catch (e: Exception) {
                // Safe handling
            } finally {
                pendingResult.finish()
            }
        }
    }
}
