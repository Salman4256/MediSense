package com.medisense.app.domain.model

/**
 * Categories of contextual health signals evaluated by the risk engine.
 */
enum class ContextualRiskCategory(val displayName: String) {
    SYMPTOM_RECURRENCE("Symptom Recurrence Patterns"),
    RECENT_HEALTH_ACTIVITY("Recent Prediction Activity"),
    CONFIDENCE_DYNAMICS("Prediction Confidence Dynamics"),
    MEDICATION_ADHERENCE("Medication Adherence Context"),
    APPOINTMENT_CONTEXT("Clinical Appointment Context"),
    CHRONIC_ALLERGY_CONTEXT("Pre-existing Conditions & Allergies"),
    TEMPORAL_PATTERNS("Temporal Trends & Variability"),
    PROFILE_BASELINE("Health Profile Baseline")
}
