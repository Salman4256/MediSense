package com.medisense.app.data.model

import java.io.Serializable

data class PredictionExplanation(
    val diseaseName: String,
    val probability: Float,
    val contributions: List<XaiFeatureContribution>,
    val summary: String,
    val modelVersion: String = "1.0",
    val isAvailable: Boolean = true
) : Serializable
