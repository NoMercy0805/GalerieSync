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
  put("ugreenlinkId",s.ugreenlinkId);put("ugreenUsername",s.ugreenUsername);put("ugreenPassword",s.ugreenPassword);put("webdavUrl",s.webdavUrl);put("webdavUsername",s.webdavUsername);put("webdavPassword",s.webdavPassword)
  put("remoteFolder",s.remoteFolder);put("wifiOnly",s.wifiOnly);put("direction",s.direction.name);put("profileName",s.profileName);put("enabled",s.enabled)
  put("sourceFolders",JSONArray(s.sourceFolders.toList()))
 }
 private fun decode(o:JSONObject)=NasSettings(id=o.optString("id",java.util.UUID.randomUUID().toString()),name=o.optString("name"),host=o.optString("host"),share=o.optString("share"),username=o.optString("username"),password=o.optString("password"),domain=o.optString("domain"),ugreenlinkId=o.optString("ugreenlinkId"),ugreenUsername=o.optString("ugreenUsername"),ugreenPassword=o.optString("ugreenPassword"),webdavUrl=o.optString("webdavUrl"),webdavUsername=o.optString("webdavUsername"),webdavPassword=o.optString("webdavPassword"),remoteFolder=o.optString("remoteFolder","GalerieSync"),wifiOnly=o.optBoolean("wifiOnly",true),direction=runCatching{SyncDirection.valueOf(o.optString("direction",SyncDirection.PHONE_TO_NAS.name))}.getOrDefault(SyncDirection.PHONE_TO_NAS),sourceFolders=(0 until o.optJSONArray("sourceFolders")?.length().orZero()).mapNotNull{o.optJSONArray("sourceFolders")?.optString(it)}.toSet(),profileName=o.optString("profileName"),enabled=o.optBoolean("enabled",true))
 private fun Int?.orZero()=this?:0
 private fun loadList(key:String):List<NasSettings>{val raw=prefs.getString(key,"[]")?:"[]";return runCatching{val a=JSONArray(raw);(0 until a.length()).map{decode(a.getJSONObject(it))}}.getOrDefault(emptyList())}
 private fun saveList(key:String,v:List<NasSettings>){val a=JSONArray();v.forEach{a.put(encode(it))};prefs.edit().putString(key,a.toString()).apply()}
 fun loadConnections():List<NasSettings>{val list=loadList("nas_connections");if(list.isNotEmpty())return list;val old=load();return if(old.host.isNotBlank())listOf(old.copy(name=old.name.ifBlank{old.host})) else emptyList()}
 fun saveConnections(v:List<NasSettings>)=saveList("nas_connections",v)
 fun loadProfiles():List<NasSettings>{val list=loadList("sync_profiles");if(list.isNotEmpty())return list;return emptyList()}
 fun saveProfiles(v:List<NasSettings>)=saveList("sync_profiles",v)
 fun load()=NasSettings(host=prefs.getString("host","")?:"",share=prefs.getString("share","")?:"",username=prefs.getString("username","")?:"",password=prefs.getString("password","")?:"",domain=prefs.getString("domain","")?:"",remoteFolder=prefs.getString("folder","GalerieSync")?:"GalerieSync",wifiOnly=prefs.getBoolean("wifiOnly",true),direction=runCatching{SyncDirection.valueOf(prefs.getString("direction",SyncDirection.PHONE_TO_NAS.name)!!)}.getOrDefault(SyncDirection.PHONE_TO_NAS),sourceFolders=prefs.getStringSet("sourceFolders",emptySet())?:emptySet())
 fun save(s:NasSettings)=prefs.edit().putString("host",s.host).putString("share",s.share).putString("username",s.username).putString("password",s.password).putString("domain",s.domain).putString("folder",s.remoteFolder).putBoolean("wifiOnly",s.wifiOnly).putString("direction",s.direction.name).putStringSet("sourceFolders",s.sourceFolders).apply()
}
