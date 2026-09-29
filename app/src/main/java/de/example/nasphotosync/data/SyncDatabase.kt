package de.example.nasphotosync.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [NasConnectionEntity::class, SyncProfileEntity::class, SyncedFileEntity::class],
    version = 1,
    exportSchema = false
)
abstract class SyncDatabase : RoomDatabase() {
    abstract fun nasConnectionDao(): NasConnectionDao
    abstract fun syncProfileDao(): SyncProfileDao
    abstract fun syncedFileDao(): SyncedFileDao

    companion object {
        @Volatile private var instance: SyncDatabase? = null

        fun getInstance(context: Context): SyncDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    SyncDatabase::class.java,
                    "nasphotosync.db"
                ).fallbackToDestructiveMigration().build().also { instance = it }
            }
        }
    }
}
