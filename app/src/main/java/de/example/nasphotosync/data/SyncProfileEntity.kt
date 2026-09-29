package de.example.nasphotosync.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Ein Synchronisationsprofil: verknuepft eine NAS-Verbindung mit einem
 * lokalen Ordner (MediaStore-Album), einem Zielordner auf der NAS, und
 * einer Richtung. Mehrere Profile koennen dieselbe NAS-Verbindung nutzen.
 */
@Entity(tableName = "sync_profiles")
data class SyncProfileEntity(
    @PrimaryKey val id: String,
    val name: String,
    val nasConnectionId: String,
    val direction: String,        // "PHONE_TO_NAS" | "NAS_TO_PHONE" | "BIDIRECTIONAL"
    val sourceBucketId: String,   // lokales Album (MediaStore BUCKET_ID)
    val sourceLabel: String,      // Anzeigename, z.B. "DCIM/Camera"
    val targetPath: String,       // Ordner auf der NAS, z.B. "/Fotos/Handy"
    val isActive: Boolean,
    val lastSyncAt: Long?
)
