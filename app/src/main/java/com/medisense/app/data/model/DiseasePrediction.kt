package com.medisense.app.data.model

import java.io.Serializable

data class DiseasePrediction(
    val diseaseName: String,
    val probability: Float,
    val rank: Int
) : Serializable
