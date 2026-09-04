package com.medisense.app.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class AppointmentAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val appointmentId = intent.getLongExtra(MedicationNotificationManager.EXTRA_APPOINTMENT_ID, 0L)
        val doctorName = intent.getStringExtra(MedicationNotificationManager.EXTRA_DOCTOR_NAME) ?: "Doctor"
        val clinicName = intent.getStringExtra(MedicationNotificationManager.EXTRA_CLINIC_NAME) ?: "Clinic"
        val appointmentDate = intent.getStringExtra("appointment_date") ?: ""
        val appointmentTime = intent.getStringExtra("appointment_time") ?: ""

        MedicationNotificationManager.showAppointmentReminderNotification(
            context = context,
            appointmentId = appointmentId,
            doctorName = doctorName,
            clinicName = clinicName,
            appointmentDate = appointmentDate,
            appointmentTime = appointmentTime
        )
    }
}
