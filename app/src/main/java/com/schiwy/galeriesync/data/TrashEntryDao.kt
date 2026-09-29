package com.schiwy.galeriesync.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TrashEntryDao {
    @Query("SELECT * FROM trash_entries")
    suspend fun getAll(): List<TrashEntry>

    @Query("SELECT * FROM trash_entries")
    fun observeAll(): Flow<List<TrashEntry>>

    @Query("SELECT * FROM trash_entries WHERE deletedAt < :beforeTimestamp")
    suspend fun getOlderThan(beforeTimestamp: Long): List<TrashEntry>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: TrashEntry)

    @Query("DELETE FROM trash_entries WHERE id = :id")
    suspend fun deleteById(id: String)
}
