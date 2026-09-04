package com.medisense.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "appointments")
data class AppointmentEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val userId: String,
    val doctorName: String,
    val clinicName: String,
    val appointmentType: String,
    val appointmentDate: String,
    val appointmentTime: String,
    val appointmentTimestamp: Long,
    val reminderMinutesBefore: Int = 30,
    val notes: String? = null,
    val status: String = "SCHEDULED",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val pendingSync: Boolean = true
)
