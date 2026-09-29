package com.schiwy.galeriesync.sync

import android.content.Context
import com.schiwy.galeriesync.data.AppDatabase
import com.schiwy.galeriesync.data.FileSystemHelper
import com.schiwy.galeriesync.data.MediaStoreRepository
import com.schiwy.galeriesync.data.NasProtocol
import com.schiwy.galeriesync.data.SettingsRepository
import com.schiwy.galeriesync.data.SyncedFile
import com.schiwy.galeriesync.data.TrashEntry
import com.schiwy.galeriesync.webdav.WebDavClient
import com.schiwy.galeriesync.webdav.WebDavEntry
import kotlinx.coroutines.flow.first
import java.io.File
import java.util.concurrent.TimeUnit

sealed class SyncResult {
    object NotConfigured : SyncResult()
    data class Success(val uploaded: Int, val downloaded: Int, val trashedLocal: Int, val trashedRemote: Int) : SyncResult()
    data class Failed(val message: String) : SyncResult()
}

/**
 * Der eigentliche Zwei-Wege-Abgleich. Pro ausgewaehltem Album wird
 * verglichen: lokale Fotos vs. Dateien auf der NAS vs. letzter bekannter
 * Zustand (synced_files-Tabelle). Daraus ergibt sich fuer jede Datei genau
 * eine von vier Aktionen: hochladen, herunterladen, lokal in den
 * Papierkorb spiegeln, oder remote in den Papierkorb spiegeln.
 */
class SyncEngine(private val context: Context) {

    private val settings = SettingsRepository(context)
    private val db = AppDatabase.getInstance(context)
    private val mediaStore = MediaStoreRepository(context)
    private val fileSystem = FileSystemHelper(context)

    suspend fun runSync(): SyncResult {
        val conn = settings.connection.first()
        if (!conn.isConfigured || conn.host.isBlank()) return SyncResult.NotConfigured
        if (conn.protocol != NasProtocol.WEBDAV) {
            return SyncResult.Failed("SMB wird in dieser Version noch nicht unterstuetzt - bitte WebDAV verwenden.")
        }

        val password = settings.getPassword()
        val scheme = if (conn.useHttps) "https" else "http"
        val client = WebDavClient("$scheme://${conn.host}:${conn.port}", conn.username, password)

        val mirrorDeletions = settings.mirrorDeletions.first()
        val trashRetentionDays = settings.trashRetentionDays.first()
        val selectedBuckets = settings.selectedBuckets.first()
        if (selectedBuckets.isEmpty()) return SyncResult.Success(0, 0, 0, 0)

        val albums = mediaStore.listAlbums().associateBy { it.bucketId }
        val remoteTrashPath = "${conn.remoteBasePath}/.galeriesync-papierkorb"

        return try {
            client.createDirectoriesRecursive(conn.remoteBasePath)
            client.createDirectoriesRecursive(remoteTrashPath)

            var uploaded = 0
            var downloaded = 0
            var trashedLocal = 0
            var trashedRemote = 0

            for (bucketId in selectedBuckets) {
                val albumName = fileSystem.sanitizeFolderName(albums[bucketId]?.name ?: bucketId)
                val remoteBucketPath = "${conn.remoteBasePath}/$albumName"
                client.createDirectoriesRecursive(remoteBucketPath)

                val localPhotos = mediaStore.listPhotosInBuckets(setOf(bucketId)).associateBy { it.fileName }
                val remoteEntries = client.list(remoteBucketPath).filter { !it.isDirectory }.associateBy { it.name }
                val knownFiles = db.syncedFileDao().getForBucket(bucketId).associateBy { it.fileName }

                val allNames = localPhotos.keys + remoteEntries.keys + knownFiles.keys
                for (name in allNames) {
                    val local = localPhotos[name]
                    val remote = remoteEntries[name]
                    val known = knownFiles[name]
                    val syncId = "$bucketId/$name"

                    when {
                        local != null && remote == null && known == null -> {
                            val localFile = fileSystem.resolvePath(local.uri)
                            if (localFile != null && localFile.exists()) {
                                client.upload(localFile, "$remoteBucketPath/$name")
                                db.syncedFileDao().upsert(
                                    SyncedFile(syncId, bucketId, name, "$remoteBucketPath/$name", local.sizeBytes, local.dateModifiedEpochMs, System.currentTimeMillis(), System.currentTimeMillis())
                                )
                                uploaded++
                            }
                        }
                        local == null && remote != null && known == null -> {
                            fileSystem.writeToAlbum(albumName, name) { targetFile ->
                                client.download(remote.path, targetFile)
                            }
                            db.syncedFileDao().upsert(
                                SyncedFile(syncId, bucketId, name, remote.path, remote.sizeBytes, System.currentTimeMillis(), remote.lastModifiedEpochMs, System.currentTimeMillis())
                            )
                            downloaded++
                        }
                        local == null && remote != null && known != null -> {
                            // Lokal geloescht -> remote spiegeln (in den Papierkorb, nicht sofort endgueltig)
                            if (mirrorDeletions) {
                                val trashPath = moveRemoteToTrash(client, remote, remoteTrashPath)
                                db.trashEntryDao().insert(
                                    TrashEntry(syncId, name, "remote", trashPath, System.currentTimeMillis())
                                )
                                trashedRemote++
                            }
                            db.syncedFileDao().deleteById(syncId)
                        }
                        local != null && remote == null && known != null -> {
                            // Remote geloescht -> lokal spiegeln (in den Papierkorb)
                            if (mirrorDeletions) {
                                val localFile = fileSystem.resolvePath(local.uri)
                                if (localFile != null) {
                                    val trashed = fileSystem.moveToLocalTrash(localFile)
                                    if (trashed != null) {
                                        db.trashEntryDao().insert(
                                            TrashEntry(syncId, name, "local", trashed.absolutePath, System.currentTimeMillis())
                                        )
                                        trashedLocal++
                                    }
                                }
                            }
                            db.syncedFileDao().deleteById(syncId)
                        }
                        local != null && remote != null -> {
                            db.syncedFileDao().upsert(
                                SyncedFile(syncId, bucketId, name, remote.path, local.sizeBytes, local.dateModifiedEpochMs, remote.lastModifiedEpochMs, System.currentTimeMillis())
                            )
                        }
                        else -> { /* lokal == null && remote == null: nichts zu tun */ }
                    }
                }
            }

            cleanupExpiredTrash(client, trashRetentionDays)
            settings.setLastSyncAt(System.currentTimeMillis())
            SyncResult.Success(uploaded, downloaded, trashedLocal, trashedRemote)
        } catch (e: Exception) {
            SyncResult.Failed(e.message ?: "Unbekannter Fehler")
        }
    }

    private fun moveRemoteToTrash(client: WebDavClient, entry: WebDavEntry, remoteTrashPath: String): String {
        val trashPath = "$remoteTrashPath/${System.currentTimeMillis()}_${entry.name}"
        try {
            client.move(entry.path, trashPath)
        } catch (e: Exception) {
            // Falls MOVE nicht unterstuetzt wird: direkt loeschen als Rueckfallebene
            client.delete(entry.path)
            return entry.path // schon weg, cleanupExpiredTrash wird das DELETE einfach nochmal (folgenlos) versuchen
        }
        return trashPath
    }

    /** Loescht Papierkorb-Eintraege endgueltig, die aelter als die Aufbewahrungsfrist sind. */
    private suspend fun cleanupExpiredTrash(client: WebDavClient, retentionDays: Int) {
        val cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(retentionDays.toLong())
        val expired = db.trashEntryDao().getOlderThan(cutoff)
        for (entry in expired) {
            try {
                if (entry.side == "local") {
                    fileSystem.permanentlyDeleteFromTrash(entry.trashPath)
                } else {
                    client.delete(entry.trashPath)
                }
                db.trashEntryDao().deleteById(entry.id)
            } catch (e: Exception) {
                // beim naechsten Durchlauf erneut versuchen
            }
        }
    }
}
