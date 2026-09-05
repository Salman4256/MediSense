package com.medisense.app.data.repository

import com.medisense.app.data.local.dao.SecurityAuditEventDao
import com.medisense.app.data.local.entity.SecurityAuditEventEntity
import com.medisense.app.data.remote.supabase.AuthService
import com.medisense.app.domain.model.SecurityAuditEvent
import com.medisense.app.domain.model.SecurityAuditEventType
import com.medisense.app.domain.security.SecureLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for recording and observing privacy-safe local security telemetry.
 * Enforces strict user-scoped data isolation based on authenticated Supabase Auth UUID.
 */
@Singleton
class SecurityAuditRepository @Inject constructor(
    private val auditDao: SecurityAuditEventDao,
    private val authService: AuthService
) {

    /**
     * Records a local security audit event with generic non-sensitive description.
     */
    suspend fun recordEvent(
        eventType: SecurityAuditEventType,
        customDescription: String? = null
    ): Long = withContext(Dispatchers.IO) {
        val userId = authService.getCurrentUserId() ?: "local-user"
        val description = customDescription ?: getDefaultDescription(eventType)

        val entity = SecurityAuditEventEntity(
            userId = userId,
            eventType = eventType.name,
            timestamp = System.currentTimeMillis(),
            description = description,
            appVersion = "1.0"
        )

        val rowId = auditDao.insertAuditEvent(entity)
        SecureLogger.d("SecurityAudit", "Recorded audit event: ${eventType.name} for user: $userId")
        rowId
    }

    /**
     * Observes recent user-scoped audit events for the active user.
     */
    fun observeRecentAuditEvents(limit: Int = 30): Flow<List<SecurityAuditEvent>> {
        val userId = authService.getCurrentUserId() ?: "local-user"
        return auditDao.observeRecentAuditEvents(userId, limit)
            .map { list ->
                list.map { entity ->
                    SecurityAuditEvent(
                        id = entity.id,
                        userId = entity.userId,
                        eventType = runCatching { SecurityAuditEventType.valueOf(entity.eventType) }
                            .getOrDefault(SecurityAuditEventType.PRIVACY_SETTINGS_CHANGED),
                        timestamp = entity.timestamp,
                        description = entity.description,
                        appVersion = entity.appVersion
                    )
                }
            }
            .flowOn(Dispatchers.IO)
    }

    /**
     * Clears all audit telemetry for the current user.
     */
    suspend fun clearAuditHistory(): Unit = withContext(Dispatchers.IO) {
        val userId = authService.getCurrentUserId() ?: "local-user"
        auditDao.deleteAllAuditEventsForUser(userId)
    }

    private fun getDefaultDescription(eventType: SecurityAuditEventType): String {
        return when (eventType) {
            SecurityAuditEventType.LOGIN -> "User signed in successfully"
            SecurityAuditEventType.LOGOUT -> "User signed out of session"
            SecurityAuditEventType.ACCOUNT_SWITCH -> "Active account switched"
            SecurityAuditEventType.PROFILE_UPDATED -> "Health profile record updated"
            SecurityAuditEventType.PREDICTION_CREATED -> "Disease prediction analysis performed"
            SecurityAuditEventType.PREDICTION_HISTORY_DELETED -> "Prediction history record deleted"
            SecurityAuditEventType.MEDICATION_CREATED -> "Medication schedule created"
            SecurityAuditEventType.MEDICATION_UPDATED -> "Medication schedule updated"
            SecurityAuditEventType.APPOINTMENT_CREATED -> "Doctor appointment scheduled"
            SecurityAuditEventType.APPOINTMENT_UPDATED -> "Doctor appointment updated"
            SecurityAuditEventType.AI_SESSION_STARTED -> "AI Health Assistant consultation opened"
            SecurityAuditEventType.LOCAL_DATA_CLEARED -> "Local health records and caches cleared"
            SecurityAuditEventType.PRIVACY_SETTINGS_CHANGED -> "Privacy and security settings updated"
        }
    }
}
