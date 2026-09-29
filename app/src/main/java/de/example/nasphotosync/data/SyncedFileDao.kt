package de.example.nasphotosync.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SyncedFileDao {
    @Query("SELECT * FROM synced_files WHERE profileId = :profileId")
    suspend fun getForProfile(profileId: String): List<SyncedFileEntity>

    @Query("SELECT COUNT(*) FROM synced_files")
    fun observeCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM synced_files WHERE profileId = :profileId")
    suspend fun countForProfile(profileId: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(file: SyncedFileEntity)

    @Query("DELETE FROM synced_files WHERE id = :id")
    suspend fun deleteById(id: String)
}
