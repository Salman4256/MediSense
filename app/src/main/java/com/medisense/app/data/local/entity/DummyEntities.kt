package com.medisense.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "predictions")
data class PredictionEntity(@PrimaryKey val id: Int = 0)

@Entity(tableName = "explanations")
data class ExplanationEntity(@PrimaryKey val id: Int = 0)

@Entity(tableName = "chat_messages")
data class ChatMessageEntity(@PrimaryKey val id: Int = 0)

@Entity(tableName = "medications")
data class MedicationEntity(@PrimaryKey val id: Int = 0)

@Entity(tableName = "medication_history")
data class MedicationHistoryEntity(@PrimaryKey val id: Int = 0)
