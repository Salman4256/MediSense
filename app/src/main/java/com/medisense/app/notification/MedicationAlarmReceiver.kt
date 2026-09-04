package com.medisense.app.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import com.medisense.app.data.local.dao.MedicationDao
import com.medisense.app.ui.medication.ui.AlarmActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MedicationAlarmReceiver : BroadcastReceiver() {

    @Inject lateinit var medicationDao: MedicationDao
    @Inject lateinit var scheduler: MedicationScheduler

    override fun onReceive(context: Context, intent: Intent) {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val wakeLock = powerManager?.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
            "MediSense:MedicationAlarmWakeLock"
        )
        wakeLock?.acquire(15 * 1000L)

        val medicationId = intent.getLongExtra(MedicationNotificationManager.EXTRA_MEDICATION_ID, 0L)
        val userId = intent.getStringExtra(MedicationNotificationManager.EXTRA_USER_ID) ?: ""
        var medicineName = intent.getStringExtra(MedicationNotificationManager.EXTRA_MEDICATION_NAME) ?: ""
        var dosage = intent.getStringExtra(MedicationNotificationManager.EXTRA_DOSAGE) ?: ""
        var instructions = intent.getStringExtra(MedicationNotificationManager.EXTRA_INSTRUCTIONS) ?: ""
        val scheduledDate = intent.getLongExtra(MedicationNotificationManager.EXTRA_SCHEDULED_DATE, System.currentTimeMillis())
        val scheduledTime = intent.getStringExtra(MedicationNotificationManager.EXTRA_SCHEDULED_TIME) ?: ""

        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val med = if (medicationId > 0) medicationDao.getMedicationById(medicationId, userId) else null

                if (med != null) {
                    if (!med.active) {
                        return@launch
                    }
                    medicineName = med.medicineName
                    dosage = "${med.dosage} ${med.dosageUnit}"
                    instructions = med.instructions
                }

                if (medicineName.isBlank()) {
                    medicineName = "Medication"
                }

                // 1. Show Heads-up / FullScreen Notification with Alarm Sound & Actions
                MedicationNotificationManager.showMedicationAlarmNotification(
                    context = context,
                    medicationId = medicationId,
                    userId = userId,
                    medicineName = medicineName,
                    dosage = dosage,
                    instructions = instructions,
                    scheduledDate = scheduledDate,
                    scheduledTime = scheduledTime
                )

                // 2. Launch Full Screen Alarm Screen
                val alarmIntent = Intent(context, AlarmActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    putExtra(MedicationNotificationManager.EXTRA_MEDICATION_ID, medicationId)
                    putExtra(MedicationNotificationManager.EXTRA_USER_ID, userId)
                    putExtra(MedicationNotificationManager.EXTRA_MEDICATION_NAME, medicineName)
                    putExtra(MedicationNotificationManager.EXTRA_DOSAGE, dosage)
                    putExtra(MedicationNotificationManager.EXTRA_INSTRUCTIONS, instructions)
                    putExtra(MedicationNotificationManager.EXTRA_SCHEDULED_DATE, scheduledDate)
                    putExtra(MedicationNotificationManager.EXTRA_SCHEDULED_TIME, scheduledTime)
                    putExtra(MedicationNotificationManager.EXTRA_NOTIFICATION_ID, medicationId.toInt())
                }
                context.startActivity(alarmIntent)

                // 3. Automatically schedule subsequent upcoming dose
                if (med != null && med.active) {
                    scheduler.scheduleNextReminder(med)
                }
            } catch (e: Exception) {
                // Safe handling
            } finally {
                try {
                    if (wakeLock?.isHeld == true) {
                        wakeLock.release()
                    }
                } catch (ignored: Exception) {}
                pendingResult.finish()
            }
        }
    }
}
