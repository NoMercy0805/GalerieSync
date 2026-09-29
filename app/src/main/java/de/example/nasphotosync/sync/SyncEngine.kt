package de.example.nasphotosync.sync

import de.example.nasphotosync.data.SyncDirection

/**
 * Zentrale Sync-Abstraktion.
 *
 * Die Engine entscheidet über die Richtung und Konflikte.
 * Der eigentliche NAS-Zugriff wird über einen Provider gekapselt.
 */
interface NasStorageProvider {
    suspend fun list(path: String): List<NasFile>
    suspend fun download(remotePath: String, target: java.io.OutputStream)
    suspend fun upload(source: java.io.InputStream, remotePath: String)
    suspend fun delete(remotePath: String)
}

data class NasFile(
    val path: String,
    val size: Long,
    val lastModified: Long,
    val contentHash: String? = null
)

data class SyncConflict(
    val phonePath: String?,
    val nasPath: String,
    val reason: String
)

class SyncEngine(
    private val provider: NasStorageProvider
) {
    suspend fun synchronize(
        direction: SyncDirection,
        phoneItems: List<PhoneFile>,
        nasItems: List<NasFile>
    ): List<SyncConflict> {
        // Implementierung folgt im nächsten Schritt.
        // Absichtlich noch keine destruktiven Löschoperationen.
        return emptyList()
    }
}

data class PhoneFile(
    val uri: String,
    val size: Long,
    val lastModified: Long,
    val contentHash: String? = null
)
