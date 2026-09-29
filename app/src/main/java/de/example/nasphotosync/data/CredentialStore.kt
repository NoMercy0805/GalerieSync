package de.example.nasphotosync.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Speichert NAS-Passwoerter verschluesselt (Android Keystore), getrennt
 * von der normalen Room-Datenbank. Ein Passwort pro NAS-Verbindungs-Id.
 */
class CredentialStore(context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context,
        "nasphotosync_secure",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun savePassword(connectionId: String, password: String) {
        prefs.edit().putString("pw_$connectionId", password).apply()
    }

    fun getPassword(connectionId: String): String =
        prefs.getString("pw_$connectionId", "") ?: ""

    fun deletePassword(connectionId: String) {
        prefs.edit().remove("pw_$connectionId").apply()
    }
}
