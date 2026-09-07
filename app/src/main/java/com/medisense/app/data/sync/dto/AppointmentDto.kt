package com.medisense.app.data.sync.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AppointmentDto(
    @SerialName("id")
    val id: Long,
    @SerialName("user_id")
    val userId: String,
    @SerialName("doctor_name")
    val doctorName: String,
    @SerialName("clinic_name")
    val clinicName: String,
    @SerialName("appointment_type")
    val appointmentType: String = "GENERAL_CHECKUP",
    @SerialName("appointment_date")
    val appointmentDate: String,
    @SerialName("appointment_time")
    val appointmentTime: String,
    @SerialName("appointment_timestamp")
    val appointmentTimestamp: Long,
    @SerialName("reminder_minutes_before")
    val reminderMinutesBefore: Int = 30,
    @SerialName("notes")
    val notes: String? = null,
    @SerialName("status")
    val status: String = "SCHEDULED",
    @SerialName("created_at")
    val createdAt: Long,
    @SerialName("updated_at")
    val updatedAt: Long,
    @SerialName("is_deleted")
    val isDeleted: Boolean = false
)
