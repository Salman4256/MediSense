package com.medisense.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity representing an auditable security and privacy telemetry event.
 * Indexed by userId for swift user-scoped querying and deletion.
 */
@Entity(
    tableName = "security_audit_events",
    indices = [
        Index(value = ["userId"]),
        Index(value = ["timestamp"])
    ]
)
data class SecurityAuditEventEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val userId: String,
    val eventType: String,
    val timestamp: Long = System.currentTimeMillis(),
    val description: String,
    val appVersion: String = "1.0"
)
