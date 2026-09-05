package com.medisense.app.domain.model

/**
 * High-level classification of application data for transparent privacy governance.
 */
enum class PrivacyDataCategory(
    val title: String,
    val description: String,
    val storageLocation: String,
    val retentionPolicy: String
) {
    IDENTITY_DATA(
        title = "Identity & Session Data",
        description = "Supabase Auth UUID, user email, and encrypted local session token.",
        storageLocation = "Local device & Supabase Auth",
        retentionPolicy = "Retained during active session; cleared on sign out or account deletion."
    ),
    HEALTH_DATA(
        title = "Health Records & Clinical Inputs",
        description = "Health profile, reported symptoms, medication schedules, and appointment reminders.",
        storageLocation = "Local Room database & optional PostgreSQL sync",
        retentionPolicy = "Stored locally on device for offline access. Removable via local data clearing."
    ),
    DERIVED_HEALTH_INTELLIGENCE(
        title = "Derived AI & Analytical Intelligence",
        description = "Longitudinal trend patterns, RCHR health representation, contextual risk scores, and counterfactual analysis.",
        storageLocation = "Deterministic on-device computation",
        retentionPolicy = "Computed transiently or cached locally; never shared with third-party tracking."
    ),
    TECHNICAL_DATA(
        title = "Technical & Security Telemetry",
        description = "Timestamps, application version, and local non-sensitive audit event logs.",
        storageLocation = "Local device only",
        retentionPolicy = "Stored locally on device for transparency; never transmitted to analytics servers."
    )
}
