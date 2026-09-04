package com.medisense.app.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.medisense.app.MainActivity
import com.medisense.app.data.local.entity.MedicationEntity
import com.medisense.app.utils.MedicationDateTimeUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MedicationScheduler @Inject constructor(
    @param:ApplicationContext private val context: Context
) {

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun canScheduleExactAlarms(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                alarmManager.canScheduleExactAlarms()
            } catch (e: Exception) {
                false
            }
        } else {
            true
        }
    }

    /**
     * Schedules only the next upcoming required occurrence for the medication.
     */
    fun scheduleNextReminder(medication: MedicationEntity) {
        if (!medication.active) {
            cancelReminder(medication.id)
            return
        }

        val nextTime = MedicationDateTimeUtils.getNextOccurrence(medication)
        if (nextTime == null) {
            cancelReminder(medication.id)
            return
        }

        val scheduledDate = MedicationDateTimeUtils.getStartOfDay(nextTime)
        val scheduledTime = MedicationDateTimeUtils.formatTime12H(Date(nextTime))

        val intent = Intent(context, MedicationAlarmReceiver::class.java).apply {
            putExtra(MedicationNotificationManager.EXTRA_MEDICATION_ID, medication.id)
            putExtra(MedicationNotificationManager.EXTRA_USER_ID, medication.userId)
            putExtra(MedicationNotificationManager.EXTRA_MEDICATION_NAME, medication.medicineName)
            putExtra(MedicationNotificationManager.EXTRA_DOSAGE, "${medication.dosage} ${medication.dosageUnit}")
            putExtra(MedicationNotificationManager.EXTRA_INSTRUCTIONS, medication.instructions)
            putExtra(MedicationNotificationManager.EXTRA_SCHEDULED_DATE, scheduledDate)
            putExtra(MedicationNotificationManager.EXTRA_SCHEDULED_TIME, scheduledTime)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            medication.id.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Show intent for AlarmClockInfo
        val showIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val showPendingIntent = PendingIntent.getActivity(
            context,
            medication.id.toInt() + 50000,
            showIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                val alarmClockInfo = AlarmManager.AlarmClockInfo(nextTime, showPendingIntent)
                alarmManager.setAlarmClock(alarmClockInfo, pendingIntent)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    nextTime,
                    pendingIntent
                )
            } else {
                alarmManager.setExact(
                    AlarmManager.RTC_WAKEUP,
                    nextTime,
                    pendingIntent
                )
            }
        } catch (e: SecurityException) {
            // Fallback for missing exact alarm permission
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        nextTime,
                        pendingIntent
                    )
                } else {
                    alarmManager.set(
                        AlarmManager.RTC_WAKEUP,
                        nextTime,
                        pendingIntent
                    )
                }
            } catch (ignored: Exception) {}
        } catch (e: Exception) {
            // General scheduling fallback
            try {
                alarmManager.set(
                    AlarmManager.RTC_WAKEUP,
                    nextTime,
                    pendingIntent
                )
            } catch (ignored: Exception) {}
        }
    }

    /**
     * Schedules a temporary snooze reminder (default: 10 minutes from now).
     */
    fun scheduleSnooze(medicationId: Long, snoozeDurationMillis: Long = 10 * 60 * 1000L) {
        val triggerTime = System.currentTimeMillis() + snoozeDurationMillis
        val scheduledDate = MedicationDateTimeUtils.getStartOfDay(triggerTime)
        val scheduledTime = MedicationDateTimeUtils.formatTime12H(Date(triggerTime))

        val intent = Intent(context, MedicationAlarmReceiver::class.java).apply {
            putExtra(MedicationNotificationManager.EXTRA_MEDICATION_ID, medicationId)
            putExtra(MedicationNotificationManager.EXTRA_SCHEDULED_DATE, scheduledDate)
            putExtra(MedicationNotificationManager.EXTRA_SCHEDULED_TIME, scheduledTime)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            medicationId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val showIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val showPendingIntent = PendingIntent.getActivity(
            context,
            medicationId.toInt() + 50000,
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

    /**
     * Cancels any pending alarm for the specified medication ID.
     */
    fun cancelReminder(medicationId: Long) {
        val intent = Intent(context, MedicationAlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            medicationId.toInt(),
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
        }
    }

    fun cancelMedication(medication: MedicationEntity) {
        cancelReminder(medication.id)
    }
}
