package com.schiwy.galeriesync.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SyncedFileDao {
    @Query("SELECT * FROM synced_files")
    suspend fun getAll(): List<SyncedFile>

    @Query("SELECT * FROM synced_files")
    fun observeAll(): Flow<List<SyncedFile>>

    @Query("SELECT * FROM synced_files WHERE bucketId = :bucketId")
    suspend fun getForBucket(bucketId: String): List<SyncedFile>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(file: SyncedFile)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(files: List<SyncedFile>)

    @Query("DELETE FROM synced_files WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT COUNT(*) FROM synced_files")
    fun observeCount(): Flow<Int>
}
