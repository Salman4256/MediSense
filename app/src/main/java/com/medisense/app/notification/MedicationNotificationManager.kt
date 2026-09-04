package com.medisense.app.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.medisense.app.MainActivity
import com.medisense.app.R
import com.medisense.app.ui.medication.ui.AlarmActivity

object MedicationNotificationManager {
    const val CHANNEL_MEDICATION_ALARM = "medication_alarm_channel"
    const val CHANNEL_MEDICATION_NAME = "Medication Alarms & Reminders"

    const val CHANNEL_APPOINTMENT = "appointment_reminder_channel"
    const val CHANNEL_APPOINTMENT_NAME = "Appointment Reminders"

    const val CHANNEL_GENERAL = "general_notifications_channel"
    const val CHANNEL_GENERAL_NAME = "General Notifications"

    // Action strings
    const val ACTION_TAKEN = "com.medisense.app.ACTION_MEDICATION_TAKEN"
    const val ACTION_SNOOZE = "com.medisense.app.ACTION_MEDICATION_SNOOZE"
    const val ACTION_SKIP = "com.medisense.app.ACTION_MEDICATION_SKIP"

    // Extras
    const val EXTRA_MEDICATION_ID = "extra_medication_id"
    const val EXTRA_USER_ID = "extra_user_id"
    const val EXTRA_MEDICATION_NAME = "extra_medication_name"
    const val EXTRA_DOSAGE = "extra_dosage"
    const val EXTRA_INSTRUCTIONS = "extra_instructions"
    const val EXTRA_SCHEDULED_DATE = "extra_scheduled_date"
    const val EXTRA_SCHEDULED_TIME = "extra_scheduled_time"
    const val EXTRA_APPOINTMENT_ID = "extra_appointment_id"
    const val EXTRA_DOCTOR_NAME = "extra_doctor_name"
    const val EXTRA_CLINIC_NAME = "extra_clinic_name"
    const val EXTRA_NOTIFICATION_ID = "extra_notification_id"

    fun createNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val alarmSound: Uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

            val audioAttributes = AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(AudioAttributes.USAGE_ALARM)
                .build()

            // 1. Medication Alarm Channel (MAX importance, high priority popup)
            val medChannel = NotificationChannel(
                CHANNEL_MEDICATION_ALARM,
                CHANNEL_MEDICATION_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Urgent medication reminder alarms and full-screen alerts"
                enableLights(true)
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 200, 500, 200, 500, 200, 500)
                setSound(alarmSound, audioAttributes)
                lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
                setBypassDnd(true)
            }
            notificationManager.createNotificationChannel(medChannel)

            // 2. Appointment Reminder Channel
            val notifSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val apptAudioAttributes = AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .build()

            val apptChannel = NotificationChannel(
                CHANNEL_APPOINTMENT,
                CHANNEL_APPOINTMENT_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Doctor and clinic appointment reminders"
                enableLights(true)
                enableVibration(true)
                setSound(notifSound, apptAudioAttributes)
                lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
            }
            notificationManager.createNotificationChannel(apptChannel)

            // 3. General Channel
            val genChannel = NotificationChannel(
                CHANNEL_GENERAL,
                CHANNEL_GENERAL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "General health notifications and updates"
            }
            notificationManager.createNotificationChannel(genChannel)
        }
    }

    fun showMedicationAlarmNotification(
        context: Context,
        medicationId: Long,
        userId: String,
        medicineName: String,
        dosage: String,
        instructions: String? = null,
        scheduledDate: Long,
        scheduledTime: String
    ) {
        createNotificationChannels(context)

        val notificationId = medicationId.toInt()

        // 1. Full-screen intent launching AlarmActivity
        val fullScreenIntent = Intent(context, AlarmActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_MEDICATION_ID, medicationId)
            putExtra(EXTRA_USER_ID, userId)
            putExtra(EXTRA_MEDICATION_NAME, medicineName)
            putExtra(EXTRA_DOSAGE, dosage)
            putExtra(EXTRA_INSTRUCTIONS, instructions)
            putExtra(EXTRA_SCHEDULED_DATE, scheduledDate)
            putExtra(EXTRA_SCHEDULED_TIME, scheduledTime)
            putExtra(EXTRA_NOTIFICATION_ID, notificationId)
        }
        val fullScreenPendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 2. Taken Action Intent
        val takenIntent = Intent(context, MedicationActionReceiver::class.java).apply {
            action = ACTION_TAKEN
            putExtra(EXTRA_MEDICATION_ID, medicationId)
            putExtra(EXTRA_USER_ID, userId)
            putExtra(EXTRA_SCHEDULED_DATE, scheduledDate)
            putExtra(EXTRA_SCHEDULED_TIME, scheduledTime)
            putExtra(EXTRA_NOTIFICATION_ID, notificationId)
        }
        val takenPendingIntent = PendingIntent.getBroadcast(
            context,
            notificationId * 10 + 1,
            takenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 3. Snooze Action Intent (10 min)
        val snoozeIntent = Intent(context, MedicationActionReceiver::class.java).apply {
            action = ACTION_SNOOZE
            putExtra(EXTRA_MEDICATION_ID, medicationId)
            putExtra(EXTRA_USER_ID, userId)
            putExtra(EXTRA_NOTIFICATION_ID, notificationId)
        }
        val snoozePendingIntent = PendingIntent.getBroadcast(
            context,
            notificationId * 10 + 2,
            snoozeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 4. Skip Action Intent
        val skipIntent = Intent(context, MedicationActionReceiver::class.java).apply {
            action = ACTION_SKIP
            putExtra(EXTRA_MEDICATION_ID, medicationId)
            putExtra(EXTRA_USER_ID, userId)
            putExtra(EXTRA_SCHEDULED_DATE, scheduledDate)
            putExtra(EXTRA_SCHEDULED_TIME, scheduledTime)
            putExtra(EXTRA_NOTIFICATION_ID, notificationId)
        }
        val skipPendingIntent = PendingIntent.getBroadcast(
            context,
            notificationId * 10 + 3,
            skipIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val alarmSound: Uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)

        val dosageText = if (dosage.isNotBlank()) "Dosage: $dosage" else "Scheduled dose"
        val subtitle = if (!instructions.isNullOrBlank()) "$dosageText • $instructions" else "$dosageText • Time: $scheduledTime"

        val builder = NotificationCompat.Builder(context, CHANNEL_MEDICATION_ALARM)
            .setSmallIcon(R.drawable.ic_pill)
            .setContentTitle("💊 Time to take $medicineName")
            .setContentText(subtitle)
            .setStyle(NotificationCompat.BigTextStyle().bigText("Time to take your medication:\n$medicineName ($dosageText)\nScheduled Time: $scheduledTime${if (!instructions.isNullOrBlank()) "\nInstructions: $instructions" else ""}"))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(fullScreenPendingIntent)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .setSound(alarmSound)
            .setVibrate(longArrayOf(0, 500, 200, 500, 200, 500, 200, 500))
            .setAutoCancel(true)
            .setOngoing(false)
            .addAction(R.drawable.ic_check_circle, "TAKEN", takenPendingIntent)
            .addAction(R.drawable.ic_snooze, "Snooze (10m)", snoozePendingIntent)
            .addAction(R.drawable.ic_skip, "Skip", skipPendingIntent)

        try {
            NotificationManagerCompat.from(context).notify(notificationId, builder.build())
        } catch (e: SecurityException) {
            // Handled when notification permission is denied
        }
    }

    fun showAppointmentReminderNotification(
        context: Context,
        appointmentId: Long,
        doctorName: String,
        clinicName: String,
        appointmentDate: String,
        appointmentTime: String
    ) {
        createNotificationChannels(context)

        val notificationId = (appointmentId + 80000).toInt()

        val mainIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val mainPendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            mainIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_APPOINTMENT)
            .setSmallIcon(R.drawable.ic_appointment)
            .setContentTitle("📅 Upcoming Doctor Appointment")
            .setContentText("Dr. $doctorName at $clinicName ($appointmentTime)")
            .setStyle(NotificationCompat.BigTextStyle().bigText("You have an upcoming appointment:\nDoctor: Dr. $doctorName\nClinic: $clinicName\nDate: $appointmentDate at $appointmentTime"))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(mainPendingIntent)
            .setAutoCancel(true)

        try {
            NotificationManagerCompat.from(context).notify(notificationId, builder.build())
        } catch (e: SecurityException) {}
    }

    fun showMissedDoseReminderNotification(
        context: Context,
        medicationId: Long,
        userId: String,
        medicineName: String,
        dosage: String,
        instructions: String? = null,
        scheduledDate: Long,
        scheduledTime: String
    ) {
        createNotificationChannels(context)

        val notificationId = (medicationId + 90000).toInt()

        // Content intent: Opens MainActivity when tapped
        val mainIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_MEDICATION_ID, medicationId)
            putExtra(EXTRA_USER_ID, userId)
        }
        val mainPendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            mainIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Action: Taken
        val takenIntent = Intent(context, MedicationActionReceiver::class.java).apply {
            action = ACTION_TAKEN
            putExtra(EXTRA_MEDICATION_ID, medicationId)
            putExtra(EXTRA_USER_ID, userId)
            putExtra(EXTRA_SCHEDULED_DATE, scheduledDate)
            putExtra(EXTRA_SCHEDULED_TIME, scheduledTime)
            putExtra(EXTRA_NOTIFICATION_ID, notificationId)
        }
        val takenPendingIntent = PendingIntent.getBroadcast(
            context,
            notificationId * 10 + 1,
            takenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Action: Skip
        val skipIntent = Intent(context, MedicationActionReceiver::class.java).apply {
            action = ACTION_SKIP
            putExtra(EXTRA_MEDICATION_ID, medicationId)
            putExtra(EXTRA_USER_ID, userId)
            putExtra(EXTRA_SCHEDULED_DATE, scheduledDate)
            putExtra(EXTRA_SCHEDULED_TIME, scheduledTime)
            putExtra(EXTRA_NOTIFICATION_ID, notificationId)
        }
        val skipPendingIntent = PendingIntent.getBroadcast(
            context,
            notificationId * 10 + 3,
            skipIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val dosageText = if (dosage.isNotBlank()) " ($dosage)" else ""
        val subtitle = "Missed scheduled dose at $scheduledTime$dosageText. Tap to mark."
        val bigText = buildString {
            append("⚠️ You missed your scheduled medication dose:\n")
            append("• Medicine: $medicineName$dosageText\n")
            append("• Scheduled Time: $scheduledTime\n")
            if (!instructions.isNullOrBlank()) {
                append("• Instructions: $instructions\n")
            }
            append("Please take your dose now or mark it as taken/skipped.")
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_MEDICATION_ALARM)
            .setSmallIcon(R.drawable.ic_pill)
            .setContentTitle("⚠️ Missed Dose: $medicineName")
            .setContentText(subtitle)
            .setStyle(NotificationCompat.BigTextStyle().bigText(bigText))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(mainPendingIntent)
            .setAutoCancel(true)
            .setOngoing(false)
            .addAction(R.drawable.ic_check_circle, "TAKEN", takenPendingIntent)
            .addAction(R.drawable.ic_skip, "SKIP", skipPendingIntent)

        try {
            NotificationManagerCompat.from(context).notify(notificationId, builder.build())
        } catch (e: SecurityException) {
            // Handled when notification permission is denied
        }
    }

    fun dismissNotification(context: Context, notificationId: Int) {
        try {
            NotificationManagerCompat.from(context).cancel(notificationId)
        } catch (ignored: Exception) {}
    }
}
