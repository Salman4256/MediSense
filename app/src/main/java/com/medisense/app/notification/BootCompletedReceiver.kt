package com.medisense.app.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.medisense.app.data.local.dao.AppointmentDao
import com.medisense.app.data.local.dao.MedicationDao
import com.medisense.app.data.local.dao.MedicationHistoryDao
import com.medisense.app.data.local.entity.MedicationHistoryEntity
import com.medisense.app.utils.MedicationDateTimeUtils
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Date
import javax.inject.Inject

@AndroidEntryPoint
class BootCompletedReceiver : BroadcastReceiver() {

    @Inject lateinit var medicationDao: MedicationDao
    @Inject lateinit var medicationHistoryDao: MedicationHistoryDao
    @Inject lateinit var medicationScheduler: MedicationScheduler
    @Inject lateinit var appointmentDao: AppointmentDao
    @Inject lateinit var appointmentScheduler: AppointmentScheduler

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_LOCKED_BOOT_COMPLETED ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED ||
            action == "android.intent.action.QUICKBOOT_POWERON"
        ) {
            val pendingResult = goAsync()

            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val now = System.currentTimeMillis()
                    val today = MedicationDateTimeUtils.getStartOfDay(now)
                    val yesterday = today - (24 * 60 * 60 * 1000L)

                    // 1. Check all active medications for missed doses during power off
                    val activeMeds = medicationDao.getAllActiveMedicationsSync()
                    for (med in activeMeds) {
                        val checkDates = listOf(yesterday, today)
                        for (dateMillis in checkDates) {
                            val slots = MedicationDateTimeUtils.getScheduledSlotsForDate(med, dateMillis)
                            for (slotMillis in slots) {
                                // If the dose was scheduled in the past within 24 hours
                                if (slotMillis < now && (now - slotMillis) <= (24 * 60 * 60 * 1000L)) {
                                    val timeStr = MedicationDateTimeUtils.formatTime12H(Date(slotMillis))
                                    val existing = medicationHistoryDao.getOccurrenceHistory(med.id, dateMillis, timeStr, med.userId)
                                        ?: medicationHistoryDao.findExistingRecord(med.id, dateMillis, timeStr)

                                    if (existing == null) {
                                        // Phone was switched off when alarm was scheduled -> Post missed dose reminder!
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

                                        // Insert MISSED history entry
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
                                        medicationHistoryDao.insertHistory(missedRecord)
                                    }
                                }
                            }
                        }

                        // Re-schedule the next future reminder alarm
                        medicationScheduler.scheduleNextReminder(med)
                    }

                    // 2. Reschedule all upcoming scheduled doctor appointments
                    val upcomingAppts = appointmentDao.getAllUpcomingScheduledAppointmentsSync(now)
                    for (appt in upcomingAppts) {
                        appointmentScheduler.scheduleReminder(appt)
                    }
                } catch (e: Exception) {
                    // Safe error handling on device boot
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}
