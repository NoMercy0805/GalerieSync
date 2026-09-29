package de.example.nasphotosync.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SyncProfileDao {
    @Query("SELECT * FROM sync_profiles ORDER BY name ASC")
    fun observeAll(): Flow<List<SyncProfileEntity>>

    @Query("SELECT * FROM sync_profiles WHERE id = :id")
    suspend fun getById(id: String): SyncProfileEntity?

    @Query("SELECT * FROM sync_profiles WHERE nasConnectionId = :nasId")
    suspend fun getForConnection(nasId: String): List<SyncProfileEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(profile: SyncProfileEntity)

    @Query("DELETE FROM sync_profiles WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("UPDATE sync_profiles SET lastSyncAt = :timestamp WHERE id = :id")
    suspend fun updateLastSyncAt(id: String, timestamp: Long)
}
