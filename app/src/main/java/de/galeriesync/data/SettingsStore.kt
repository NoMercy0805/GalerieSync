package de.galeriesync.data
import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
class SettingsStore(context: Context) {
    private val prefs = EncryptedSharedPreferences.create(context,"secure_settings",MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM)
    fun load() = NasSettings(host=prefs.getString("host","")?:"",share=prefs.getString("share","")?:"",username=prefs.getString("username","")?:"",password=prefs.getString("password","")?:"",domain=prefs.getString("domain","")?:"",remoteFolder=prefs.getString("folder","GalerieSync")?:"GalerieSync",wifiOnly=prefs.getBoolean("wifiOnly",true),direction=runCatching{SyncDirection.valueOf(prefs.getString("direction",SyncDirection.PHONE_TO_NAS.name)!!)}.getOrDefault(SyncDirection.PHONE_TO_NAS))
    fun save(s: NasSettings)=prefs.edit().putString("host",s.host).putString("share",s.share).putString("username",s.username).putString("password",s.password).putString("domain",s.domain).putString("folder",s.remoteFolder).putBoolean("wifiOnly",s.wifiOnly).putString("direction",s.direction.name).apply()
}
