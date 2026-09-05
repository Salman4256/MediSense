package com.medisense.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.medisense.app.data.local.entity.SecurityAuditEventEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for local security audit telemetry.
 * Strictly user-scoped by authenticated Supabase Auth UUID.
 */
@Dao
interface SecurityAuditEventDao {

    @Query("SELECT * FROM security_audit_events WHERE userId = :userId ORDER BY timestamp DESC LIMIT :limit")
    fun observeRecentAuditEvents(userId: String, limit: Int = 30): Flow<List<SecurityAuditEventEntity>>

    @Query("SELECT * FROM security_audit_events WHERE userId = :userId ORDER BY timestamp DESC")
    suspend fun getAuditEventsForUser(userId: String): List<SecurityAuditEventEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAuditEvent(event: SecurityAuditEventEntity): Long

    @Query("DELETE FROM security_audit_events WHERE userId = :userId")
    suspend fun deleteAllAuditEventsForUser(userId: String)
}
