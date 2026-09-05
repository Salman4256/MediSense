package com.medisense.app.domain.model

/**
 * Enumeration of high-level, privacy-safe audit event types.
 *
 * NOTE: These events capture system-level actions for local transparency telemetry.
 * They NEVER contain sensitive medical payloads, symptoms, or medication dosages.
 */
enum class SecurityAuditEventType {
    LOGIN,
    LOGOUT,
    ACCOUNT_SWITCH,
    PROFILE_UPDATED,
    PREDICTION_CREATED,
    PREDICTION_HISTORY_DELETED,
    MEDICATION_CREATED,
    MEDICATION_UPDATED,
    APPOINTMENT_CREATED,
    APPOINTMENT_UPDATED,
    AI_SESSION_STARTED,
    LOCAL_DATA_CLEARED,
    PRIVACY_SETTINGS_CHANGED
}
