package de.example.nasphotosync.data

import android.content.Context
import android.media.MediaScannerConnection
import android.os.Environment
import java.io.File

/**
 * Schreibt von der NAS heruntergeladene Dateien in den passenden lokalen
 * Ordner und meldet sie beim MediaStore an, damit sie sofort in der
 * Galerie auftauchen. Braucht MANAGE_EXTERNAL_STORAGE fuer freies
 * Schreiben ausserhalb des eigenen App-Ordners.
 */
class FileSystemHelper(private val context: Context) {

    fun writeToFolder(folderName: String, fileName: String, bytesWriter: (File) -> Unit): File {
        val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), folderName)
        dir.mkdirs()
        val target = File(dir, fileName)
        bytesWriter(target)
        MediaScannerConnection.scanFile(context, arrayOf(target.absolutePath), null, null)
        return target
    }

    fun sanitizeFolderName(name: String): String =
        name.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim().ifBlank { "Unbenannt" }
}
