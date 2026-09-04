package com.medisense.app.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.medisense.app.data.local.dao.AppointmentDao
import com.medisense.app.data.local.dao.MedicationDao
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class BootCompletedReceiver : BroadcastReceiver() {

    @Inject lateinit var medicationDao: MedicationDao
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
                    // 1. Reschedule all active medications
                    val activeMeds = medicationDao.getAllActiveMedicationsSync()
                    for (med in activeMeds) {
                        medicationScheduler.scheduleNextReminder(med)
                    }

                    // 2. Reschedule all upcoming scheduled appointments
                    val upcomingAppts = appointmentDao.getAllUpcomingScheduledAppointmentsSync(System.currentTimeMillis())
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
