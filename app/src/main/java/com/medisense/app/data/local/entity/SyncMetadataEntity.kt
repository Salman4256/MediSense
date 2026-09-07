package com.medisense.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Local Room entity storing synchronization checkpoints and status per user.
 */
@Entity(tableName = "sync_metadata")
data class SyncMetadataEntity(
    @PrimaryKey
    val userId: String,
    val lastSyncTimestamp: Long = 0L,
    val lastSyncStatus: String = "IDLE",
    val lastSyncMessage: String? = null,
    val pendingUploadCount: Int = 0,
    val failedRecordCount: Int = 0,
    val updatedAt: Long = System.currentTimeMillis()
)
