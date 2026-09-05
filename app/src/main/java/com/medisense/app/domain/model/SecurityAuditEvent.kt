package com.medisense.app.domain.model

/**
 * Domain model representing a local, user-scoped security audit event.
 */
data class SecurityAuditEvent(
    val id: Long = 0,
    val userId: String,
    val eventType: SecurityAuditEventType,
    val timestamp: Long = System.currentTimeMillis(),
    val description: String,
    val appVersion: String = "1.0"
)
