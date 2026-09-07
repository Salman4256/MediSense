package com.medisense.app.data.sync.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PredictionHistoryDto(
    @SerialName("id")
    val id: Long,
    @SerialName("user_id")
    val userId: String,
    @SerialName("predicted_disease")
    val predictedDisease: String,
    @SerialName("confidence")
    val confidence: Float,
    @SerialName("symptoms")
    val symptoms: List<String> = emptyList(),
    @SerialName("explanation_summary")
    val explanationSummary: String? = null,
    @SerialName("prediction_timestamp")
    val predictionTimestamp: Long,
    @SerialName("model_version")
    val modelVersion: String = "1.0",
    @SerialName("updated_at")
    val updatedAt: Long
)
