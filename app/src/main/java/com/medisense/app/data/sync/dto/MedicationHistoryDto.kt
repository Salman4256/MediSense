package com.medisense.app.data.sync.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MedicationHistoryDto(
    @SerialName("id")
    val id: Long,
    @SerialName("medication_id")
    val medicationId: Long,
    @SerialName("user_id")
    val userId: String,
    @SerialName("medicine_name")
    val medicineName: String,
    @SerialName("dosage")
    val dosage: String = "",
    @SerialName("scheduled_date")
    val scheduledDate: Long,
    @SerialName("scheduled_time")
    val scheduledTime: String,
    @SerialName("action_time")
    val actionTime: Long? = null,
    @SerialName("status")
    val status: String = "TAKEN",
    @SerialName("updated_at")
    val updatedAt: Long
)
