package com.schiwy.galeriesync.data

import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import java.io.File

/**
 * Direkte Dateisystem-Operationen fuer lokale Fotos. Braucht
 * MANAGE_EXTERNAL_STORAGE, dafuer aber echtes stilles Verschieben/Loeschen
 * ohne Bestaetigungsdialog pro Datei (noetig fuer automatisches Spiegeln
 * von Loeschungen im Hintergrund).
 */
class FileSystemHelper(private val context: Context) {

    private val trashDir: File by lazy {
        File(Environment.getExternalStorageDirectory(), ".galeriesync-papierkorb").apply { mkdirs() }
    }

    /** Ermittelt den echten Dateisystem-Pfad zu einer MediaStore-URI. */
    fun resolvePath(uri: Uri): File? {
        val projection = arrayOf(MediaStore.Images.Media.DATA)
        context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val path = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA))
                if (path != null) return File(path)
            }
        }
        return null
    }

    /** Verschiebt eine lokale Datei in den Papierkorb-Ordner (Sicherheitsnetz statt Sofort-Loeschung). */
    fun moveToLocalTrash(file: File): File? {
        if (!file.exists()) return null
        val target = File(trashDir, "${System.currentTimeMillis()}_${file.name}")
        return if (file.renameTo(target)) {
            // MediaStore ueber die Aenderung informieren, damit die Galerie-App
            // die "geloeschte" Datei nicht mehr anzeigt.
            MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), null, null)
            target
        } else null
    }

    fun permanentlyDeleteFromTrash(trashPath: String) {
        val file = File(trashPath)
        if (file.exists()) file.delete()
    }

    /** Schreibt eine von der NAS heruntergeladene Datei in den richtigen lokalen Album-Ordner. */
    fun writeToAlbum(albumFolderName: String, fileName: String, bytesWriter: (File) -> Unit): File {
        val albumDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), albumFolderName)
        albumDir.mkdirs()
        val targetFile = File(albumDir, fileName)
        bytesWriter(targetFile)
        // MediaStore ueber die neue Datei informieren, damit sie sofort in der
        // Galerie auftaucht statt erst nach einem manuellen Rescan.
        MediaScannerConnection.scanFile(context, arrayOf(targetFile.absolutePath), null, null)
        return targetFile
    }

    fun sanitizeFolderName(name: String): String =
        name.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim().ifBlank { "Unbenannt" }
}
