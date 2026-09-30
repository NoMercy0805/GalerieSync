package de.galeriesync.data
import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.json.JSONArray
import org.json.JSONObject

class SettingsStore(context:Context){
 private val prefs=EncryptedSharedPreferences.create(context,"secure_settings",MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM)
 private fun encode(s:NasSettings)=JSONObject().apply{
  put("id",s.id);put("name",s.name);put("host",s.host);put("share",s.share);put("username",s.username);put("password",s.password);put("domain",s.domain)
  put("remoteFolder",s.remoteFolder);put("wifiOnly",s.wifiOnly);put("direction",s.direction.name);put("profileName",s.profileName);put("enabled",s.enabled)
  put("sourceFolders",JSONArray(s.sourceFolders.toList()))
 }
 private fun decode(o:JSONObject)=NasSettings(id=o.optString("id",java.util.UUID.randomUUID().toString()),name=o.optString("name"),host=o.optString("host"),share=o.optString("share"),username=o.optString("username"),password=o.optString("password"),domain=o.optString("domain"),remoteFolder=o.optString("remoteFolder","GalerieSync"),wifiOnly=o.optBoolean("wifiOnly",true),direction=runCatching{SyncDirection.valueOf(o.optString("direction",SyncDirection.PHONE_TO_NAS.name))}.getOrDefault(SyncDirection.PHONE_TO_NAS),sourceFolders=(0 until o.optJSONArray("sourceFolders")?.length().orZero()).mapNotNull{o.optJSONArray("sourceFolders")?.optString(it)}.toSet(),profileName=o.optString("profileName"),enabled=o.optBoolean("enabled",true))
 private fun Int?.orZero()=this?:0
 private fun loadList(key:String):List<NasSettings>{val raw=prefs.getString(key,"[]")?:"[]";return runCatching{val a=JSONArray(raw);(0 until a.length()).map{decode(a.getJSONObject(it))}}.getOrDefault(emptyList())}
 private fun saveList(key:String,v:List<NasSettings>){val a=JSONArray();v.forEach{a.put(encode(it))};prefs.edit().putString(key,a.toString()).apply()}
 fun loadConnections():List<NasSettings>{val list=loadList("nas_connections");if(list.isNotEmpty())return list;val old=load();return if(old.host.isNotBlank())listOf(old.copy(name=old.name.ifBlank{old.host})) else emptyList()}
 fun saveConnections(v:List<NasSettings>)=saveList("nas_connections",v)
 fun loadProfiles():List<NasSettings>{val list=loadList("sync_profiles");if(list.isNotEmpty())return list;return emptyList()}
 fun saveProfiles(v:List<NasSettings>)=saveList("sync_profiles",v)
 fun loadTrustedWifi():Set<String> = prefs.getStringSet("trusted_wifi",emptySet())?:emptySet()
 fun saveTrustedWifi(v:Set<String>)=prefs.edit().putStringSet("trusted_wifi",v).apply()
 fun loadThemeMode():String = prefs.getString("theme_mode","SYSTEM")?:"SYSTEM"
 fun saveThemeMode(v:String)=prefs.edit().putString("theme_mode",v).apply()
 fun loadAutoSyncEnabled():Boolean = prefs.getBoolean("auto_sync_enabled",false)
 fun saveAutoSyncEnabled(v:Boolean)=prefs.edit().putBoolean("auto_sync_enabled",v).apply()
 fun loadAutoSyncNetwork():String = prefs.getString("auto_sync_network","WIFI")?:"WIFI"
 fun saveAutoSyncNetwork(v:String)=prefs.edit().putString("auto_sync_network",v).apply()
 fun loadAutoSyncCharging():Boolean = prefs.getBoolean("auto_sync_charging",false)
 fun saveAutoSyncCharging(v:Boolean)=prefs.edit().putBoolean("auto_sync_charging",v).apply()
 fun loadAutoSyncValue():Int = prefs.getInt("auto_sync_value",15)
 fun loadAutoSyncUnit():String = prefs.getString("auto_sync_unit","MINUTES")?:"MINUTES"
 fun saveAutoSyncSchedule(value:Int,unit:String)=prefs.edit().putInt("auto_sync_value",value).putString("auto_sync_unit",unit).apply()
 fun loadNotificationsEnabled():Boolean = prefs.getBoolean("sync_notifications",false)
 fun saveNotificationsEnabled(v:Boolean)=prefs.edit().putBoolean("sync_notifications",v).apply()
 fun loadNasStorage():Map<String,Pair<Long,Long>>{val raw=prefs.getString("nas_storage","{}")?:"{}";return runCatching{val o=JSONObject(raw);o.keys().asSequence().associateWith{k->val v=o.getJSONObject(k);v.optLong("total",0L) to v.optLong("free",0L)}}.getOrDefault(emptyMap())}
 fun saveNasStorage(id:String,total:Long,free:Long){val raw=prefs.getString("nas_storage","{}")?:"{}";val o=runCatching{JSONObject(raw)}.getOrDefault(JSONObject());o.put(id,JSONObject().put("total",total).put("free",free));prefs.edit().putString("nas_storage",o.toString()).apply()}
 fun removeNasStorage(id:String){val raw=prefs.getString("nas_storage","{}")?:"{}";val o=runCatching{JSONObject(raw)}.getOrDefault(JSONObject());o.remove(id);prefs.edit().putString("nas_storage",o.toString()).apply()}
 fun loadNasStorageUpdated():Map<String,Long>{val raw=prefs.getString("nas_storage_updated","{}")?:"{}";return runCatching{val o=JSONObject(raw);o.keys().asSequence().associateWith{o.optLong(it,0L)}}.getOrDefault(emptyMap())}
 fun saveNasStorageUpdated(id:String,time:Long){val raw=prefs.getString("nas_storage_updated","{}")?:"{}";val o=runCatching{JSONObject(raw)}.getOrDefault(JSONObject());o.put(id,time);prefs.edit().putString("nas_storage_updated",o.toString()).apply()}
 fun loadProfileLastSync():Map<String,String>{val raw=prefs.getString("profile_last_sync","{}")?:"{}";return runCatching{val o=JSONObject(raw);o.keys().asSequence().associateWith{o.optString(it,"")}}.getOrDefault(emptyMap())}
 fun saveProfileLastSync(id:String,text:String){val raw=prefs.getString("profile_last_sync","{}")?:"{}";val o=runCatching{JSONObject(raw)}.getOrDefault(JSONObject());o.put(id,text);prefs.edit().putString("profile_last_sync",o.toString()).apply()}
 fun loadSyncHistory():List<String> = (prefs.getString("sync_history","")?:"").lines().filter{it.isNotBlank()}
 fun addSyncHistory(entry:String){val items=(listOf(entry)+loadSyncHistory()).take(100);prefs.edit().putString("sync_history",items.joinToString("\n")).apply()}
 fun loadVpnConfig():String = prefs.getString("wireguard_config","")?:""
 fun saveVpnConfig(v:String)=prefs.edit().putString("wireguard_config",v).apply()
 fun load()=NasSettings(host=prefs.getString("host","")?:"",share=prefs.getString("share","")?:"",username=prefs.getString("username","")?:"",password=prefs.getString("password","")?:"",domain=prefs.getString("domain","")?:"",remoteFolder=prefs.getString("folder","GalerieSync")?:"GalerieSync",wifiOnly=prefs.getBoolean("wifiOnly",true),direction=runCatching{SyncDirection.valueOf(prefs.getString("direction",SyncDirection.PHONE_TO_NAS.name)!!)}.getOrDefault(SyncDirection.PHONE_TO_NAS),sourceFolders=prefs.getStringSet("sourceFolders",emptySet())?:emptySet())
 fun save(s:NasSettings)=prefs.edit().putString("host",s.host).putString("share",s.share).putString("username",s.username).putString("password",s.password).putString("domain",s.domain).putString("folder",s.remoteFolder).putBoolean("wifiOnly",s.wifiOnly).putString("direction",s.direction.name).putStringSet("sourceFolders",s.sourceFolders).apply()
}
