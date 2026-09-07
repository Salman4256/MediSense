package com.medisense.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.medisense.app.data.local.entity.SyncMetadataEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for local synchronization checkpoints.
 */
@Dao
interface SyncMetadataDao {

    @Query("SELECT * FROM sync_metadata WHERE userId = :userId LIMIT 1")
    fun observeSyncMetadata(userId: String): Flow<SyncMetadataEntity?>

    @Query("SELECT * FROM sync_metadata WHERE userId = :userId LIMIT 1")
    suspend fun getSyncMetadata(userId: String): SyncMetadataEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSyncMetadata(metadata: SyncMetadataEntity)

    @Query("DELETE FROM sync_metadata WHERE userId = :userId")
    suspend fun deleteSyncMetadataForUser(userId: String)
}
