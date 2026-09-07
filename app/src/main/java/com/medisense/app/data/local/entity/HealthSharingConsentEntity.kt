package com.medisense.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "health_sharing_consents")
data class HealthSharingConsentEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val userId: String,
    val purpose: String,
    val recipientLabel: String,
    val selectedCategories: List<String>,
    val format: String,
    val packageFingerprint: String,
    val status: String,
    val createdAt: Long,
    val revokedAt: Long? = null,
    val expiresAt: Long? = null,
    val disclosureAccepted: Boolean = true,
    val dataQualityAcknowledged: Boolean = true
)
