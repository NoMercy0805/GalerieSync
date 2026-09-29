package de.example.nasphotosync.data

import androidx.room.Database
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.RoomDatabase

@Entity(tableName = "sync_items")
data class SyncItemEntity(
    @PrimaryKey val key: String,
    val phoneUri: String?,
    val nasPath: String,
    val size: Long,
    val lastModified: Long,
    val contentHash: String?,
    val lastSyncedAt: Long?,
    val status: String
)

@Database(entities = [SyncItemEntity::class], version = 1, exportSchema = false)
abstract class SyncDatabase : RoomDatabase()
