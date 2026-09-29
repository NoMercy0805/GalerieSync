package de.example.nasphotosync.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Der letzte bekannte Zustand einer Datei innerhalb eines Sync-Profils.
 * Damit erkennt die Sync-Engine, ob eine Datei neu ist (noch nicht in
 * dieser Tabelle) oder schon einmal synchronisiert wurde.
 */
@Entity(tableName = "synced_files")
data class SyncedFileEntity(
    @PrimaryKey val id: String,   // "<profileId>/<fileName>"
    val profileId: String,
    val fileName: String,
    val sizeBytes: Long,
    val localLastModified: Long,
    val remoteLastModified: Long,
    val lastSyncedAt: Long
)
