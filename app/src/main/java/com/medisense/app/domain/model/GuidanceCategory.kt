package com.medisense.app.domain.model

/**
 * Standardized categories for personalized health-management guidance.
 */
enum class GuidanceCategory(val displayName: String) {
    HEALTH_TRACKING("Health Tracking"),
    MEDICATION_ADHERENCE("Medication Management"),
    APPOINTMENT_FOLLOW_UP("Appointment Follow-up"),
    PROFILE_COMPLETENESS("Profile Setup"),
    SYMPTOM_MONITORING("Symptom Monitoring"),
    HEALTH_TRENDS("Health Trends"),
    RECORD_MAINTENANCE("Record Maintenance"),
    PROFESSIONAL_REVIEW("Professional Review")
}
