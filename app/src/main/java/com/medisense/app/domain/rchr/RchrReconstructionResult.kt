package com.medisense.app.domain.rchr

/**
 * Represents a single decoded/reconstructed health attribute.
 */
data class ReconstructedAttribute(
    val category: String, // "Profile", "Symptoms", "Predictions", "Medications", "Adherence", "Appointments", "Temporal Dynamics", "Personal Context"
    val attributeKey: String,
    val encodedValue: String,
    val humanReadableMeaning: String,
    val isAvailable: Boolean
)

/**
 * Result of the deterministic round-trip reconstruction and consistency validation.
 */
data class RchrReconstructionResult(
    val representationVersion: String,
    val reconstructedAt: Long = System.currentTimeMillis(),
    val reconstructedAttributes: List<ReconstructedAttribute>,
    val unavailableAttributes: List<String>,
    val mismatchedAttributes: List<String>,
    val totalReconstructableCount: Int,
    val successfullyReconstructedCount: Int,
    val reconstructionConsistencyScore: Float, // 0.0f - 100.0f
    val consistencyFormulaDescription: String = "(Successfully Reconstructed Encoded Attributes / Total Reconstructable Encoded Attributes) × 100",
    val isConsistent: Boolean
)
