package com.schiwy.galeriesync.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Der "letzte bekannte Zustand" einer Datei, die schon einmal erfolgreich
 * synchronisiert wurde. Anhand dieser Tabelle erkennt die Sync-Engine, ob
 * eine fehlende Datei auf einer Seite bedeutet "muss noch hochgeladen/
 * heruntergeladen werden" (steht noch nicht in dieser Tabelle) oder
 * "wurde geloescht, muss auf der anderen Seite gespiegelt werden" (stand
 * schon in dieser Tabelle, fehlt jetzt).
 */
@Entity(tableName = "synced_files")
data class SyncedFile(
    @PrimaryKey val id: String,           // stabiler Schluessel: relativer Pfad/Dateiname innerhalb des Album-Ordners
    val bucketId: String,                 // welches lokale Album (MediaStore bucket_id)
    val fileName: String,
    val remotePath: String,               // voller Pfad auf der NAS
    val sizeBytes: Long,
    val localLastModified: Long,
    val remoteLastModified: Long,
    val lastSyncedAt: Long
)
