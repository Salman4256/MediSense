package com.medisense.app.data.model

import kotlinx.serialization.Serializable

@Serializable
data class Symptom(
    val id: Int,
    val displayName: String,
    val modelFeatureName: String
) : java.io.Serializable
