package com.medisense.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity storing user-recorded observations and computed feedback discrepancies
 * following predictive and counterfactual intervention scenarios (Module 25).
 *
 * Strictly offline-first and associated with the canonical Supabase Auth userId.
 */
@Entity(
    tableName = "intervention_response_records",
    indices = [
        Index(value = ["user_id"]),
        Index(value = ["source_prediction_id"]),
        Index(value = ["intervention_category"]),
        Index(value = ["observation_timestamp"])
    ]
)
data class InterventionResponseRecordEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "user_id")
    val userId: String,

    @ColumnInfo(name = "source_prediction_id")
    val sourcePredictionId: Long? = null,

    @ColumnInfo(name = "counterfactual_id")
    val counterfactualId: String? = null,

    @ColumnInfo(name = "intervention_category")
    val interventionCategory: String, // SYMPTOM_MANAGEMENT, LIFESTYLE_MODIFICATION, MEDICATION_ADHERENCE, CLINICAL_FOLLOWUP, ROUTINE_MONITORING

    @ColumnInfo(name = "intervention_description")
    val interventionDescription: String,

    @ColumnInfo(name = "baseline_state_summary")
    val baselineStateSummary: String,

    @ColumnInfo(name = "expected_response")
    val expectedResponse: String,

    @ColumnInfo(name = "observed_response")
    val observedResponse: String, // IMPROVED, PARTIALLY_IMPROVED, UNCHANGED, WORSENED, UNCLEAR, NOT_OBSERVED

    @ColumnInfo(name = "expected_value")
    val expectedValue: Float? = null,

    @ColumnInfo(name = "observed_value")
    val observedValue: Float? = null,

    @ColumnInfo(name = "discrepancy_value")
    val discrepancyValue: Float = 0.0f,

    @ColumnInfo(name = "discrepancy_category")
    val discrepancyCategory: String = "INSUFFICIENT_OBSERVATION", // ALIGNED, SLIGHT_DEVIATION, MODERATE_DEVIATION, LARGE_DEVIATION, INSUFFICIENT_OBSERVATION

    @ColumnInfo(name = "user_notes")
    val userNotes: String? = null,

    @ColumnInfo(name = "observation_timestamp")
    val observationTimestamp: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "confidence_level")
    val confidenceLevel: String = "INSUFFICIENT_DATA", // LOW, MODERATE, HIGH, INSUFFICIENT_DATA

    @ColumnInfo(name = "data_quality_status")
    val dataQualityStatus: String = "VALID", // VALID, VALID_WITH_WARNING, INSUFFICIENT_DATA, INVALID

    @ColumnInfo(name = "model_version")
    val modelVersion: String = "1.0",

    @ColumnInfo(name = "algorithm_version")
    val algorithmVersion: String = "1.0",

    @ColumnInfo(name = "pending_sync")
    val pendingSync: Boolean = true
)
