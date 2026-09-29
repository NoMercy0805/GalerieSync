package de.example.nasphotosync.data

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

class MediaStoreRepository(private val context: Context) {

    fun listAlbums(): List<LocalAlbum> {
        val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Images.Media.BUCKET_ID,
            MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
            MediaStore.Images.Media._ID
        )
        val ids = LinkedHashMap<String, MutableList<Long>>()
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
                names[bucketId] = cursor.getString(idxBucketName) ?: "Unbenannt"
                ids.getOrPut(bucketId) { mutableListOf() }.add(cursor.getLong(idxId))
            }
        }

        return ids.map { (bucketId, itemIds) ->
            LocalAlbum(
                bucketId = bucketId,
                name = names[bucketId] ?: bucketId,
                itemCount = itemIds.size,
                coverUri = itemIds.firstOrNull()?.let { ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, it) }
            )
        }.sortedByDescending { it.itemCount }
    }

    fun listPhotosInBucket(bucketId: String): List<LocalPhoto> {
        val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.BUCKET_ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.DATE_MODIFIED
        )
        val result = mutableListOf<LocalPhoto>()
        context.contentResolver.query(
            collection, projection,
            "${MediaStore.Images.Media.BUCKET_ID} = ?", arrayOf(bucketId), null
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

    fun resolvePath(uri: Uri): java.io.File? {
        val projection = arrayOf(MediaStore.Images.Media.DATA)
        context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val path = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA))
                if (path != null) return java.io.File(path)
            }
        }
        return null
    }
}
