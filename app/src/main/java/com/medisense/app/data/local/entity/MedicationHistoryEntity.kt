package com.medisense.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "medication_history")
data class MedicationHistoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val medicationId: Long = 0,
    val userId: String = "",
    val medicineName: String = "",
    val dosage: String = "",
    val scheduledDate: Long = 0L,
    val scheduledTime: String = "",
    val actionTime: Long? = null,
    val status: String = "TAKEN"
)
