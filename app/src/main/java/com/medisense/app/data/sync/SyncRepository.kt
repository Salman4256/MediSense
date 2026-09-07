package com.medisense.app.data.sync

import com.medisense.app.data.local.dao.SyncMetadataDao
import com.medisense.app.data.local.entity.SyncMetadataEntity
import com.medisense.app.data.remote.supabase.AuthService
import com.medisense.app.data.sync.model.SyncResult
import com.medisense.app.data.sync.model.SyncStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for managing synchronization state and initiating sync workflows.
 * Exposes sync metadata flow and synchronous/asynchronous sync trigger methods.
 */
@Singleton
class SyncRepository @Inject constructor(
    private val syncEngine: SyncEngine,
    private val syncMetadataDao: SyncMetadataDao,
    private val authService: AuthService
) {

    private val _syncStatus = MutableStateFlow(SyncStatus.IDLE)
    val syncStatus: StateFlow<SyncStatus> = _syncStatus.asStateFlow()

    private val _lastSyncResult = MutableStateFlow<SyncResult?>(null)
    val lastSyncResult: StateFlow<SyncResult?> = _lastSyncResult.asStateFlow()

    /**
     * Observes synchronization metadata for the currently authenticated user.
     */
    fun observeSyncMetadata(): Flow<SyncMetadataEntity?> {
        val userId = authService.getCurrentUserId() ?: return flowOf(null)
        return syncMetadataDao.observeSyncMetadata(userId)
    }

    /**
     * Gets the latest stored sync metadata snapshot.
     */
    suspend fun getSyncMetadata(): SyncMetadataEntity? {
        val userId = authService.getCurrentUserId() ?: return null
        return syncMetadataDao.getSyncMetadata(userId)
    }

    /**
     * Triggers a full synchronization cycle and updates the observable status.
     */
    suspend fun triggerSync(): SyncResult {
        _syncStatus.value = SyncStatus.SYNCING
        val result = syncEngine.synchronize()
        _lastSyncResult.value = result
        _syncStatus.value = result.status
        return result
    }

    /**
     * Returns the currently authenticated user ID if any.
     */
    fun getCurrentUserId(): String? {
        return authService.getCurrentUserId()
    }
}
