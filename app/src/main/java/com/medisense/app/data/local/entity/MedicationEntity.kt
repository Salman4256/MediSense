package com.medisense.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import com.medisense.app.data.local.Converters

@Entity(tableName = "medications")
@TypeConverters(Converters::class)
data class MedicationEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val userId: String,
    val medicineName: String,
    val dosage: String = "",
    val dosageUnit: String = "mg",
    val frequency: String = "ONCE_DAILY",
    val scheduledTimes: List<String> = emptyList(),
    val startDate: Long = System.currentTimeMillis(),
    val endDate: Long? = null,
    val instructions: String = "",
    val active: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val pendingSync: Boolean = true
)
