package com.medisense.app.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.medisense.app.MainActivity
import com.medisense.app.data.local.entity.AppointmentEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppointmentScheduler @Inject constructor(
    @param:ApplicationContext private val context: Context
) {

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun scheduleReminder(appointment: AppointmentEntity) {
        if (appointment.status != "SCHEDULED") {
            cancelReminder(appointment)
            return
        }

        val reminderOffsetMillis = appointment.reminderMinutesBefore * 60 * 1000L
        val reminderTime = appointment.appointmentTimestamp - reminderOffsetMillis
        val now = System.currentTimeMillis()

        if (appointment.appointmentTimestamp <= now) {
            return
        }

        val triggerTime = if (reminderTime > now) reminderTime else now + 5000L

        val intent = Intent(context, AppointmentAlarmReceiver::class.java).apply {
            putExtra(MedicationNotificationManager.EXTRA_APPOINTMENT_ID, appointment.id)
            putExtra(MedicationNotificationManager.EXTRA_DOCTOR_NAME, appointment.doctorName)
            putExtra(MedicationNotificationManager.EXTRA_CLINIC_NAME, appointment.clinicName)
            putExtra("appointment_date", appointment.appointmentDate)
            putExtra("appointment_time", appointment.appointmentTime)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            (appointment.id + 80000).toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val showIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val showPendingIntent = PendingIntent.getActivity(
            context,
            (appointment.id + 90000).toInt(),
            showIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                val alarmClockInfo = AlarmManager.AlarmClockInfo(triggerTime, showPendingIntent)
                alarmManager.setAlarmClock(alarmClockInfo, pendingIntent)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerTime,
                    pendingIntent
                )
            } else {
                alarmManager.setExact(
                    AlarmManager.RTC_WAKEUP,
                    triggerTime,
                    pendingIntent
                )
            }
        } catch (e: Exception) {
            try {
                alarmManager.set(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
            } catch (ignored: Exception) {}
        }
    }

    fun cancelReminder(appointment: AppointmentEntity) {
        val intent = Intent(context, AppointmentAlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            (appointment.id + 80000).toInt(),
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
        }
    }
}
