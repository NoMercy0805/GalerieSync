package de.example.nasphotosync.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Eine gespeicherte NAS-Verbindung. Das Passwort liegt NICHT hier (Room-DB
 * ist unverschluesselt), sondern separat in CredentialStore
 * (EncryptedSharedPreferences, Android Keystore), referenziert ueber die id.
 */
@Entity(tableName = "nas_connections")
data class NasConnectionEntity(
    @PrimaryKey val id: String,
    val name: String,
    val protocol: String,      // "SMB" | "WEBDAV" | "SFTP" | "UGREEN_LINK"
    val host: String,
    val port: Int,
    val useHttps: Boolean,
    val username: String,
    val remoteBasePath: String,
    val createdAt: Long
)
