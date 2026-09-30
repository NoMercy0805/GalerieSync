package de.galeriesync.sync

import de.galeriesync.data.NasSettings
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.util.Base64

class WebDavProvider(private val settings:NasSettings){
 private val base=settings.webdavUrl.trim().trimEnd('/')
 private fun auth()="Basic "+Base64.getEncoder().encodeToString(((settings.webdavUsername.ifBlank{settings.username})+":"+(settings.webdavPassword.ifBlank{settings.password})).toByteArray())
 private fun encodePath(path:String)=path.trim('/').split('/').filter{it.isNotBlank()}.joinToString("/") { java.net.URLEncoder.encode(it,"UTF-8").replace("+","%20") }
 private fun url(path:String="")=URL(if(path.isBlank())base else base+"/"+encodePath(path))
 private fun connection(path:String="",method:String):HttpURLConnection=(url(path).openConnection() as HttpURLConnection).apply{requestMethod=method;connectTimeout=12000;readTimeout=30000;setRequestProperty("Authorization",auth());setRequestProperty("User-Agent","GalerieSync/0.4");doInput=true}
 fun test():Boolean{val c=connection("","PROPFIND");c.setRequestProperty("Depth","0");try{val code=c.responseCode;if(code !in 200..299)error("WebDAV HTTP $code");return true}finally{c.disconnect()}}
 fun ensureDirectory(path:String){var current="";path.trim('/').split('/').filter{it.isNotBlank()}.forEach{part->current=if(current.isBlank())part else "$current/$part";val p=connection(current,"PROPFIND");p.setRequestProperty("Depth","0");val exists=try{p.responseCode in 200..299}finally{p.disconnect()};if(!exists){val mk=connection(current,"MKCOL");try{val code=mk.responseCode;if(code !in 200..299 && code!=405)error("WebDAV Ordner $current: HTTP $code")}finally{mk.disconnect()}}}}
 fun listRecursive(path:String=""):List<String>{val c=connection(path,"PROPFIND");c.setRequestProperty("Depth","infinity");try{val code=c.responseCode;if(code !in 200..299)error("WebDAV Liste: HTTP $code");val xml=c.inputStream.bufferedReader().use{it.readText()};val href=Regex("<(?:[A-Za-z0-9]+:)?href[^>]*>(.*?)</(?:[A-Za-z0-9]+:)?href>",RegexOption.IGNORE_CASE);val basePath=URI(base).path.trimEnd('/');return href.findAll(xml).mapNotNull{m->java.net.URLDecoder.decode(m.groupValues[1],"UTF-8").substringBefore('?').let{h->val rel=h.removePrefix(basePath).trim('/');if(rel.isBlank()||rel==path.trim('/'))null else rel}}.filter{!it.endsWith("/")}.distinct().toList()}finally{c.disconnect()}}
 fun openInput(path:String):InputStream{val c=connection(path,"GET");val code=c.responseCode;if(code !in 200..299){c.disconnect();error("WebDAV Download: HTTP $code")};val bytes=c.inputStream.use{it.readBytes()};c.disconnect();return ByteArrayInputStream(bytes)}
 fun openOutput(path:String):OutputStream=object:ByteArrayOutputStream(){override fun close(){super.close();val c=connection(path,"PUT");c.doOutput=true;c.setFixedLengthStreamingMode(size());try{c.outputStream.use{it.write(toByteArray())};val code=c.responseCode;if(code !in 200..299)error("WebDAV Upload: HTTP $code")}finally{c.disconnect()}}}
}
