package com.schiwy.galeriesync.data

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore

data class LocalAlbum(
    val bucketId: String,
    val name: String,
    val itemCount: Int,
    val coverUri: Uri?
)

data class LocalPhoto(
    val uri: Uri,
    val bucketId: String,
    val fileName: String,
    val sizeBytes: Long,
    val dateModifiedEpochMs: Long
)

/**
 * Liest Alben und Fotos ueber den MediaStore aus - funktioniert ohne
 * Dateisystem-Berechtigungen, nur ueber READ_MEDIA_IMAGES (API 33+) bzw.
 * READ_EXTERNAL_STORAGE (aeltere Versionen).
 */
class MediaStoreRepository(private val context: Context) {

    fun listAlbums(): List<LocalAlbum> {
        val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Images.Media.BUCKET_ID,
            MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
            MediaStore.Images.Media._ID
        )
        val albums = LinkedHashMap<String, MutableList<Long>>()
        val names = HashMap<String, String>()

        context.contentResolver.query(
            collection, projection, null, null,
            "${MediaStore.Images.Media.DATE_MODIFIED} DESC"
        )?.use { cursor ->
            val idxBucketId = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_ID)
            val idxBucketName = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
            val idxId = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            while (cursor.moveToNext()) {
                val bucketId = cursor.getString(idxBucketId) ?: continue
                val bucketName = cursor.getString(idxBucketName) ?: "Unbenannt"
                val id = cursor.getLong(idxId)
                names[bucketId] = bucketName
                albums.getOrPut(bucketId) { mutableListOf() }.add(id)
            }
        }

        return albums.map { (bucketId, ids) ->
            LocalAlbum(
                bucketId = bucketId,
                name = names[bucketId] ?: bucketId,
                itemCount = ids.size,
                coverUri = ids.firstOrNull()?.let {
                    ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, it)
                }
            )
        }.sortedByDescending { it.itemCount }
    }

    fun listPhotosInBuckets(bucketIds: Set<String>): List<LocalPhoto> {
        if (bucketIds.isEmpty()) return emptyList()
        val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.BUCKET_ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.DATE_MODIFIED
        )
        val placeholders = bucketIds.joinToString(",") { "?" }
        val selection = "${MediaStore.Images.Media.BUCKET_ID} IN ($placeholders)"
        val result = mutableListOf<LocalPhoto>()

        context.contentResolver.query(
            collection, projection, selection, bucketIds.toTypedArray(), null
        )?.use { cursor ->
            val idxId = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val idxBucketId = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_ID)
            val idxName = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            val idxSize = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
            val idxDateModified = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_MODIFIED)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idxId)
                result.add(
                    LocalPhoto(
                        uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id),
                        bucketId = cursor.getString(idxBucketId) ?: "",
                        fileName = cursor.getString(idxName) ?: "img_$id.jpg",
                        sizeBytes = cursor.getLong(idxSize),
                        dateModifiedEpochMs = cursor.getLong(idxDateModified) * 1000L
                    )
                )
            }
        }
        return result
    }
}
