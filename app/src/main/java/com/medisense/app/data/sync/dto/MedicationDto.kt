package com.medisense.app.data.sync.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MedicationDto(
    @SerialName("id")
    val id: Long,
    @SerialName("user_id")
    val userId: String,
    @SerialName("medicine_name")
    val medicineName: String,
    @SerialName("dosage")
    val dosage: String = "",
    @SerialName("dosage_unit")
    val dosageUnit: String = "mg",
    @SerialName("frequency")
    val frequency: String = "ONCE_DAILY",
    @SerialName("scheduled_times")
    val scheduledTimes: List<String> = emptyList(),
    @SerialName("start_date")
    val startDate: Long,
    @SerialName("end_date")
    val endDate: Long? = null,
    @SerialName("instructions")
    val instructions: String = "",
    @SerialName("active")
    val active: Boolean = true,
    @SerialName("created_at")
    val createdAt: Long,
    @SerialName("updated_at")
    val updatedAt: Long,
    @SerialName("is_deleted")
    val isDeleted: Boolean = false
)
