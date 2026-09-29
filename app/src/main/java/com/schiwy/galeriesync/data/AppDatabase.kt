package com.schiwy.galeriesync.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [SyncedFile::class, TrashEntry::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun syncedFileDao(): SyncedFileDao
    abstract fun trashEntryDao(): TrashEntryDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "galeriesync.db"
                ).fallbackToDestructiveMigration().build().also { instance = it }
            }
        }
    }
}
