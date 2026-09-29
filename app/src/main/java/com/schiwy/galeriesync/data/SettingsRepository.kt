package com.schiwy.galeriesync.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "galeriesync_settings")

enum class NasProtocol { WEBDAV, SMB }

data class NasConnection(
    val host: String = "",
    val port: Int = 443,
    val useHttps: Boolean = true,
    val protocol: NasProtocol = NasProtocol.WEBDAV,
    val username: String = "",
    val remoteBasePath: String = "/GalerieSync",
    val isConfigured: Boolean = false
)

/**
 * Verwaltet alle App-Einstellungen. Das NAS-Passwort liegt NIE in der
 * normalen DataStore-Datei (die ist nicht verschluesselt) - dafuer wird
 * EncryptedSharedPreferences genutzt, das den Schluessel im Android
 * Keystore (Hardware-gestuetzt, wo verfuegbar) ablegt.
 */
class SettingsRepository(private val context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val encryptedPrefs = EncryptedSharedPreferences.create(
        context,
        "galeriesync_secure",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    private val KEY_HOST = stringPreferencesKey("nas_host")
    private val KEY_PORT = intPreferencesKey("nas_port")
    private val KEY_HTTPS = booleanPreferencesKey("nas_https")
    private val KEY_PROTOCOL = stringPreferencesKey("nas_protocol")
    private val KEY_USERNAME = stringPreferencesKey("nas_username")
    private val KEY_REMOTE_PATH = stringPreferencesKey("nas_remote_path")
    private val KEY_CONFIGURED = booleanPreferencesKey("nas_configured")

    private val KEY_WIFI_ONLY = booleanPreferencesKey("sync_wifi_only")
    private val KEY_SELECTED_BUCKETS = stringSetPreferencesKey("sync_selected_buckets")
    private val KEY_MIRROR_DELETIONS = booleanPreferencesKey("sync_mirror_deletions")
    private val KEY_TRASH_RETENTION_DAYS = intPreferencesKey("sync_trash_retention_days")
    private val KEY_LAST_SYNC_AT = longPreferencesKey("sync_last_at")

    val connection: Flow<NasConnection> = context.settingsDataStore.data.map { prefs ->
        NasConnection(
            host = prefs[KEY_HOST] ?: "",
            port = prefs[KEY_PORT] ?: 443,
            useHttps = prefs[KEY_HTTPS] ?: true,
            protocol = prefs[KEY_PROTOCOL]?.let { runCatching { NasProtocol.valueOf(it) }.getOrNull() } ?: NasProtocol.WEBDAV,
            username = prefs[KEY_USERNAME] ?: "",
            remoteBasePath = prefs[KEY_REMOTE_PATH] ?: "/GalerieSync",
            isConfigured = prefs[KEY_CONFIGURED] ?: false
        )
    }

    suspend fun saveConnection(conn: NasConnection, password: String) {
        context.settingsDataStore.edit { prefs ->
            prefs[KEY_HOST] = conn.host
            prefs[KEY_PORT] = conn.port
            prefs[KEY_HTTPS] = conn.useHttps
            prefs[KEY_PROTOCOL] = conn.protocol.name
            prefs[KEY_USERNAME] = conn.username
            prefs[KEY_REMOTE_PATH] = conn.remoteBasePath
            prefs[KEY_CONFIGURED] = true
        }
        encryptedPrefs.edit().putString("nas_password", password).apply()
    }

    fun getPassword(): String = encryptedPrefs.getString("nas_password", "") ?: ""

    val wifiOnly: Flow<Boolean> = context.settingsDataStore.data.map { it[KEY_WIFI_ONLY] ?: true }
    suspend fun setWifiOnly(value: Boolean) {
        context.settingsDataStore.edit { it[KEY_WIFI_ONLY] = value }
    }

    val selectedBuckets: Flow<Set<String>> = context.settingsDataStore.data.map { it[KEY_SELECTED_BUCKETS] ?: emptySet() }
    suspend fun setSelectedBuckets(buckets: Set<String>) {
        context.settingsDataStore.edit { it[KEY_SELECTED_BUCKETS] = buckets }
    }

    // Standardmaessig an, wie vom Nutzer gewuenscht: Loeschungen werden
    // gespiegelt (ueber den Papierkorb mit Aufbewahrungsfrist als
    // Sicherheitsnetz).
    val mirrorDeletions: Flow<Boolean> = context.settingsDataStore.data.map { it[KEY_MIRROR_DELETIONS] ?: true }
    suspend fun setMirrorDeletions(value: Boolean) {
        context.settingsDataStore.edit { it[KEY_MIRROR_DELETIONS] = value }
    }

    val trashRetentionDays: Flow<Int> = context.settingsDataStore.data.map { it[KEY_TRASH_RETENTION_DAYS] ?: 30 }
    suspend fun setTrashRetentionDays(days: Int) {
        context.settingsDataStore.edit { it[KEY_TRASH_RETENTION_DAYS] = days }
    }

    val lastSyncAt: Flow<Long?> = context.settingsDataStore.data.map { it[KEY_LAST_SYNC_AT] }
    suspend fun setLastSyncAt(timestamp: Long) {
        context.settingsDataStore.edit { it[KEY_LAST_SYNC_AT] = timestamp }
    }
}
