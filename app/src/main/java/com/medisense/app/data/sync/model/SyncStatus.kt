package com.medisense.app.data.sync.model

/**
 * Observable synchronization state for cloud backup.
 */
enum class SyncStatus {
    IDLE,
    SYNCING,
    SUCCESS,
    PARTIAL_SUCCESS,
    FAILED,
    OFFLINE,
    AUTH_REQUIRED
}
