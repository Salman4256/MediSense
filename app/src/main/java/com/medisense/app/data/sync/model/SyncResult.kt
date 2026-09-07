package com.medisense.app.data.sync.model

/**
 * Summary outcome of a completed synchronization cycle.
 */
data class SyncResult(
    val status: SyncStatus,
    val recordsUploaded: Int = 0,
    val recordsDownloaded: Int = 0,
    val conflictsResolved: Int = 0,
    val recordsFailed: Int = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val message: String = ""
)
