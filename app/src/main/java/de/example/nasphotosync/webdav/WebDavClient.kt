package de.example.nasphotosync.webdav

import android.util.Xml
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.asRequestBody
import okhttp3.toRequestBody
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.io.IOException
import java.io.StringReader
import java.util.concurrent.TimeUnit

data class WebDavEntry(
    val path: String,
    val name: String,
    val isDirectory: Boolean,
    val sizeBytes: Long,
    val lastModifiedEpochMs: Long
)

class WebDavException(message: String, val statusCode: Int? = null) : IOException(message)

/**
 * Schlanker WebDAV-Client (PROPFIND/MKCOL/PUT/GET/DELETE), selbst auf
 * OkHttp aufgebaut statt einer fertigen WebDAV-Bibliothek.
 */
class WebDavClient(
    private val baseUrl: String,
    username: String,
    password: String
) {
    private val credential = Credentials.basic(username, password)

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private fun urlFor(path: String): String {
        val cleanPath = if (path.startsWith("/")) path else "/$path"
        return baseUrl.trimEnd('/') + cleanPath.split("/").joinToString("/") { segment ->
            java.net.URLEncoder.encode(segment, "UTF-8").replace("+", "%20")
        }
    }

    fun testConnection(): Result<Unit> = try {
        list("/")
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(e)
    }

    fun list(path: String): List<WebDavEntry> {
        val body = """<?xml version="1.0" encoding="utf-8" ?>
            |<D:propfind xmlns:D="DAV:">
            |  <D:prop>
            |    <D:resourcetype/>
            |    <D:getcontentlength/>
            |    <D:getlastmodified/>
            |  </D:prop>
            |</D:propfind>""".trimMargin()

        val request = Request.Builder()
            .url(urlFor(path))
            .header("Authorization", credential)
            .header("Depth", "1")
            .method("PROPFIND", body.toRequestBody("application/xml".toMediaTypeOrNull()))
            .build()

        client.newCall(request).execute().use { response ->
            if (response.code !in listOf(207, 200)) {
                throw WebDavException("Verzeichnis konnte nicht gelesen werden (${response.code})", response.code)
            }
            val xml = response.body?.string() ?: return emptyList()
            return parseMultistatus(xml, path)
        }
    }

    fun createDirectory(path: String) {
        val request = Request.Builder()
            .url(urlFor(path))
            .header("Authorization", credential)
            .method("MKCOL", null)
            .build()
        client.newCall(request).execute().use { response ->
            if (response.code !in listOf(201, 405)) {
                throw WebDavException("Ordner konnte nicht angelegt werden: $path (${response.code})", response.code)
            }
        }
    }

    fun createDirectoriesRecursive(path: String) {
        val segments = path.trim('/').split("/").filter { it.isNotBlank() }
        var current = ""
        for (segment in segments) {
            current += "/$segment"
            createDirectory(current)
        }
    }

    fun upload(localFile: File, remotePath: String) {
        val mediaType = guessMediaType(localFile.name)
        val request = Request.Builder()
            .url(urlFor(remotePath))
            .header("Authorization", credential)
            .put(localFile.asRequestBody(mediaType))
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw WebDavException("Upload fehlgeschlagen: $remotePath (${response.code})", response.code)
            }
        }
    }

    fun download(remotePath: String, targetFile: File) {
        val request = Request.Builder()
            .url(urlFor(remotePath))
            .header("Authorization", credential)
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw WebDavException("Download fehlgeschlagen: $remotePath (${response.code})", response.code)
            }
            targetFile.parentFile?.mkdirs()
            response.body?.byteStream()?.use { input ->
                targetFile.outputStream().use { output -> input.copyTo(output) }
            }
        }
    }

    fun delete(remotePath: String) {
        val request = Request.Builder()
            .url(urlFor(remotePath))
            .header("Authorization", credential)
            .delete()
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful && response.code != 404) {
                throw WebDavException("Loeschen fehlgeschlagen: $remotePath (${response.code})", response.code)
            }
        }
    }

    private fun guessMediaType(fileName: String) = when (fileName.substringAfterLast('.', "").lowercase()) {
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "gif" -> "image/gif"
        "heic" -> "image/heic"
        "webp" -> "image/webp"
        "mp4" -> "video/mp4"
        else -> "application/octet-stream"
    }.toMediaTypeOrNull()

    private fun parseMultistatus(xml: String, requestedPath: String): List<WebDavEntry> {
        val parser = Xml.newPullParser()
        parser.setInput(StringReader(xml))

        val entries = mutableListOf<WebDavEntry>()
        var currentHref: String? = null
        var isCollection = false
        var contentLength = 0L
        var lastModified = 0L
        var inProp = false

        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            val localName = parser.name?.substringAfter(':') ?: ""
            when (eventType) {
                XmlPullParser.START_TAG -> when (localName) {
                    "response" -> { currentHref = null; isCollection = false; contentLength = 0L; lastModified = 0L }
                    "href" -> currentHref = parser.nextText()
                    "collection" -> isCollection = true
                    "prop" -> inProp = true
                    "getcontentlength" -> if (inProp) contentLength = parser.nextText().toLongOrNull() ?: 0L
                    "getlastmodified" -> if (inProp) lastModified = parseHttpDate(parser.nextText())
                }
                XmlPullParser.END_TAG -> when (localName) {
                    "prop" -> inProp = false
                    "response" -> {
                        val href = currentHref
                        if (href != null) {
                            val decodedPath = java.net.URLDecoder.decode(href, "UTF-8")
                            val name = decodedPath.trimEnd('/').substringAfterLast('/')
                            val normalizedRequested = requestedPath.trimEnd('/').substringAfterLast('/')
                            if (name.isNotBlank() && name != normalizedRequested) {
                                entries.add(WebDavEntry(decodedPath, name, isCollection, contentLength, lastModified))
                            }
                        }
                    }
                }
            }
            eventType = parser.next()
        }
        return entries
    }

    private fun parseHttpDate(value: String): Long = try {
        val format = java.text.SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", java.util.Locale.US)
        format.parse(value)?.time ?: 0L
    } catch (e: Exception) {
        0L
    }
}
