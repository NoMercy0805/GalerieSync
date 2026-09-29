package de.example.nasphotosync.sync

import android.content.Context
import de.example.nasphotosync.data.ConnectionType
import de.example.nasphotosync.data.CredentialStore
import de.example.nasphotosync.data.FileSystemHelper
import de.example.nasphotosync.data.MediaStoreRepository
import de.example.nasphotosync.data.SyncDatabase
import de.example.nasphotosync.data.SyncDirection
import de.example.nasphotosync.data.SyncProfileEntity
import de.example.nasphotosync.data.SyncedFileEntity
import de.example.nasphotosync.webdav.WebDavClient

sealed class SyncResult {
    data class Success(val uploaded: Int, val downloaded: Int) : SyncResult()
    data class Failed(val message: String) : SyncResult()
}

/**
 * Fuehrt den eigentlichen Abgleich fuer EIN Sync-Profil aus. Vergleicht
 * lokale Fotos vs. Dateien auf der NAS vs. letzten bekannten Zustand
 * (synced_files) und laedt entsprechend hoch/runter - je nach in Profil
 * eingestellter Richtung. Meldet Fortschritt live ueber SyncStatusBus.
 *
 * Loeschungen werden in dieser Phase NICHT gespiegelt (bewusst einfacher
 * gehalten) - das ist fuer eine spaetere Ausbaustufe vorgesehen.
 */
class SyncEngine(private val context: Context) {

    private val db = SyncDatabase.getInstance(context)
    private val credentials = CredentialStore(context)
    private val mediaStore = MediaStoreRepository(context)
    private val fileSystem = FileSystemHelper(context)

    suspend fun runSync(profile: SyncProfileEntity): SyncResult {
        val connection = db.nasConnectionDao().getById(profile.nasConnectionId)
            ?: return SyncResult.Failed("NAS-Verbindung nicht gefunden")

        if (connection.protocol != ConnectionType.WEBDAV.name) {
            SyncStatusBus.update(SyncUiState.Failed(profile.name, "${connection.protocol} wird noch nicht unterstuetzt - bitte WebDAV verwenden."))
            return SyncResult.Failed("${connection.protocol} wird noch nicht unterstuetzt")
        }

        val password = credentials.getPassword(connection.id)
        val scheme = if (connection.useHttps) "https" else "http"
        val client = WebDavClient("$scheme://${connection.host}:${connection.port}", connection.username, password)

        return try {
            client.createDirectoriesRecursive(profile.targetPath)

            val localPhotos = mediaStore.listPhotosInBucket(profile.sourceBucketId).associateBy { it.fileName }
            val remoteEntries = client.list(profile.targetPath).filter { !it.isDirectory }.associateBy { it.name }
            val known = db.syncedFileDao().getForProfile(profile.id).associateBy { it.fileName }

            val direction = SyncDirection.valueOf(profile.direction)
            val allNames = when (direction) {
                SyncDirection.PHONE_TO_NAS -> localPhotos.keys
                SyncDirection.NAS_TO_PHONE -> remoteEntries.keys
                SyncDirection.BIDIRECTIONAL -> localPhotos.keys + remoteEntries.keys
            }

            val progressList = allNames.map { FileProgress(it, FileStatus.WAITING) }.toMutableList()
            SyncStatusBus.update(SyncUiState.Running(profile.name, progressList.toList()))

            var uploaded = 0
            var downloaded = 0

            for ((index, name) in allNames.withIndex()) {
                progressList[index] = FileProgress(name, FileStatus.SYNCING)
                SyncStatusBus.update(SyncUiState.Running(profile.name, progressList.toList()))

                val local = localPhotos[name]
                val remote = remoteEntries[name]
                val alreadyKnown = known[name] != null

                try {
                    when {
                        direction != SyncDirection.NAS_TO_PHONE && local != null && remote == null && !alreadyKnown -> {
                            val localFile = mediaStore.resolvePath(local.uri)
                            if (localFile != null && localFile.exists()) {
                                client.upload(localFile, "${profile.targetPath}/$name")
                                db.syncedFileDao().upsert(
                                    SyncedFileEntity("${profile.id}/$name", profile.id, name, local.sizeBytes, local.dateModifiedEpochMs, System.currentTimeMillis(), System.currentTimeMillis())
                                )
                                uploaded++
                            }
                        }
                        direction != SyncDirection.PHONE_TO_NAS && local == null && remote != null && !alreadyKnown -> {
                            fileSystem.writeToFolder(fileSystem.sanitizeFolderName(profile.sourceLabel), name) { target ->
                                client.download(remote.path, target)
                            }
                            db.syncedFileDao().upsert(
                                SyncedFileEntity("${profile.id}/$name", profile.id, name, remote.sizeBytes, System.currentTimeMillis(), remote.lastModifiedEpochMs, System.currentTimeMillis())
                            )
                            downloaded++
                        }
                        local != null && remote != null -> {
                            db.syncedFileDao().upsert(
                                SyncedFileEntity("${profile.id}/$name", profile.id, name, local.sizeBytes, local.dateModifiedEpochMs, remote.lastModifiedEpochMs, System.currentTimeMillis())
                            )
                        }
                    }
                    progressList[index] = FileProgress(name, FileStatus.DONE)
                } catch (e: Exception) {
                    progressList[index] = FileProgress(name, FileStatus.ERROR)
                }
                SyncStatusBus.update(SyncUiState.Running(profile.name, progressList.toList()))
            }

            db.syncProfileDao().updateLastSyncAt(profile.id, System.currentTimeMillis())
            SyncStatusBus.update(SyncUiState.Done(profile.name, uploaded, downloaded))
            SyncResult.Success(uploaded, downloaded)
        } catch (e: Exception) {
            SyncStatusBus.update(SyncUiState.Failed(profile.name, e.message ?: "Unbekannter Fehler"))
            SyncResult.Failed(e.message ?: "Unbekannter Fehler")
        }
    }
}
