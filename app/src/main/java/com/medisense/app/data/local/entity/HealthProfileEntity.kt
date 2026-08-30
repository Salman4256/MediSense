package com.medisense.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Entity(tableName = "health_profiles")
@Serializable
data class HealthProfileEntity(
    @PrimaryKey
    @SerialName("id")
    val id: String,
    
    @SerialName("user_id")
    val userId: String,
    
    @SerialName("full_name")
    val fullName: String?,
    
    @SerialName("date_of_birth")
    val dateOfBirth: String?, // ISO-8601 string: yyyy-MM-dd
    
    @SerialName("gender")
    val gender: String?,
    
    @SerialName("blood_group")
    val bloodGroup: String?,
    
    @SerialName("height")
    val height: Double?,
    
    @SerialName("weight")
    val weight: Double?,
    
    @SerialName("allergies")
    val allergies: String?,
    
    @SerialName("existing_diseases")
    val existingDiseases: String?,
    
    @SerialName("current_medications")
    val currentMedications: String?,
    
    @SerialName("family_history")
    val familyHistory: String?,
    
    @SerialName("emergency_contact_name")
    val emergencyContactName: String?,
    
    @SerialName("emergency_contact_number")
    val emergencyContactNumber: String?,
    
    @SerialName("notes")
    val notes: String?,
    
    @SerialName("created_at")
    val createdAt: String? = null,
    
    @SerialName("updated_at")
    val updatedAt: String? = null,
    
    @kotlinx.serialization.Transient
    val pendingSync: Boolean = false
)
