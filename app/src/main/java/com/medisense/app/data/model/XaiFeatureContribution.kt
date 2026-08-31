package com.medisense.app.data.model

import java.io.Serializable

enum class ContributionDirection {
    SUPPORTS,
    OPPOSES,
    NEUTRAL
}

data class XaiFeatureContribution(
    val featureName: String,
    val displayName: String,
    val contribution: Float, // Normalized relative contribution (0.0 to 1.0)
    val direction: ContributionDirection = ContributionDirection.SUPPORTS,
    val importance: Float = contribution // Raw feature importance weight
) : Serializable
