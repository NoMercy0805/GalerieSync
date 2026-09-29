package com.schiwy.galeriesync.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Sicherheitsnetz fuer gespiegelte Loeschungen: Statt eine Datei beim
 * Spiegeln sofort endgueltig zu entfernen, wird sie zuerst in einen
 * versteckten Papierkorb-Ordner verschoben (lokal ueber MediaStore/Trash,
 * remote in einen ".galeriesync-papierkorb"-Ordner auf der NAS) und erst nach
 * Ablauf der Aufbewahrungsfrist endgueltig geloescht.
 */
@Entity(tableName = "trash_entries")
data class TrashEntry(
    @PrimaryKey val id: String,
    val originalFileName: String,
    val side: String,          // "local" oder "remote" - wo die Datei jetzt im Papierkorb liegt
    val trashPath: String,     // Pfad/URI im Papierkorb
    val deletedAt: Long        // Zeitpunkt der Verschiebung in den Papierkorb
)
