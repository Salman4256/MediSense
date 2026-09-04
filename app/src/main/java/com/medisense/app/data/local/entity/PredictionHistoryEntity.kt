package com.medisense.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import com.medisense.app.data.local.Converters

@Entity(tableName = "prediction_history")
@TypeConverters(Converters::class)
data class PredictionHistoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val userId: String,
    val predictedDisease: String,
    val confidence: Float,
    val symptoms: List<String>,
    val explanationSummary: String? = null,
    val predictionTimestamp: Long = System.currentTimeMillis(),
    val modelVersion: String = "1.0",
    val pendingSync: Boolean = true
)
