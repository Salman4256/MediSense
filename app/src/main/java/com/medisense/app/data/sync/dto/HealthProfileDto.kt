package com.medisense.app.data.sync.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class HealthProfileDto(
    @SerialName("id")
    val id: String,
    @SerialName("user_id")
    val userId: String,
    @SerialName("full_name")
    val fullName: String? = null,
    @SerialName("date_of_birth")
    val dateOfBirth: String? = null,
    @SerialName("gender")
    val gender: String? = null,
    @SerialName("blood_group")
    val bloodGroup: String? = null,
    @SerialName("height")
    val height: Double? = null,
    @SerialName("weight")
    val weight: Double? = null,
    @SerialName("allergies")
    val allergies: String? = null,
    @SerialName("existing_diseases")
    val existingDiseases: String? = null,
    @SerialName("current_medications")
    val currentMedications: String? = null,
    @SerialName("family_history")
    val familyHistory: String? = null,
    @SerialName("emergency_contact_name")
    val emergencyContactName: String? = null,
    @SerialName("emergency_contact_number")
    val emergencyContactNumber: String? = null,
    @SerialName("notes")
    val notes: String? = null,
    @SerialName("created_at")
    val createdAt: String? = null,
    @SerialName("updated_at")
    val updatedAt: String? = null
)
