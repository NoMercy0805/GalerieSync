package de.galeriesync.sync
import de.galeriesync.data.NasSettings
import jcifs.CIFSContext
import jcifs.config.PropertyConfiguration
import jcifs.context.BaseContext
import jcifs.smb.NtlmPasswordAuthenticator
import jcifs.smb.SmbFile
import java.io.InputStream
import java.io.OutputStream
import java.util.Properties
data class SmbStorageInfo(val total:Long,val free:Long){val used:Long get()=(total-free).coerceAtLeast(0)}
class SmbProvider(private val settings:NasSettings){
 private val context:CIFSContext by lazy{val props=Properties().apply{setProperty("jcifs.smb.client.enableSMB2","true");setProperty("jcifs.smb.client.disableSMB1","true")};BaseContext(PropertyConfiguration(props)).withCredentials(NtlmPasswordAuthenticator(settings.domain.ifBlank{null},settings.username,settings.password))}
 private fun url(path:String="")="smb://"+settings.host.trim('/')+"/"+settings.share.trim('/')+"/"+path.trimStart('/')
 private fun serverUrl()="smb://"+settings.host.trim('/')+"/"
 fun listShares():List<String>{
  val root=SmbFile(serverUrl(),context)
  root.connect()
  return root.listFiles().filter{it.isDirectory}.map{it.name.trimEnd('/')}.filter{it.isNotBlank()}.sorted()
 }
 fun diagnose():String=try{val root=SmbFile(serverUrl(),context);root.connect();"SMB-Server erreichbar"}catch(e:Exception){throw IllegalStateException(e.message ?: e.javaClass.simpleName,e)}
 fun test():Boolean{SmbFile(url(),context).connect();return true}
 fun storageInfo():SmbStorageInfo{val f=SmbFile(url(),context);f.connect();val free=f.diskFreeSpace;val total=runCatching{f.length()}.getOrDefault(0L);return SmbStorageInfo(total,free)}
 fun ensureDirectory(path:String){val f=SmbFile(url(path),context);if(!f.exists())f.mkdirs()}
 fun listRecursive(path:String=""):List<String>{val out=mutableListOf<String>();val clean=path.trim('/');val root=SmbFile(url(clean),context);if(!root.exists())return out
  fun children(dir:SmbFile):Array<SmbFile>{repeat(2){try{return dir.listFiles()}catch(e:jcifs.smb.SmbException){if(!e.message.orEmpty().contains("cannot find the file",true))throw e;Thread.sleep(150)}};return emptyArray()}
  fun walk(dir:SmbFile,relative:String){children(dir).forEach{f->try{val name=f.name.trimEnd('/');val rel=if(relative.isBlank())name else relative.trimEnd('/')+"/"+name;if(f.isDirectory)walk(f,rel) else out+=rel}catch(e:jcifs.smb.SmbException){if(!e.message.orEmpty().contains("cannot find the file",true))throw e}}};walk(root,clean);return out}
 data class RemoteFileInfo(val path:String,val size:Long)
 fun listRecursiveInfo(path:String=""):List<RemoteFileInfo>{val out=mutableListOf<RemoteFileInfo>();val clean=path.trim('/');val root=SmbFile(url(clean),context);if(!root.exists())return out
  fun children(dir:SmbFile):Array<SmbFile>{repeat(2){try{return dir.listFiles()}catch(e:jcifs.smb.SmbException){if(!e.message.orEmpty().contains("cannot find the file",true))throw e;Thread.sleep(150)}};return emptyArray()}
  fun walk(dir:SmbFile,relative:String){children(dir).forEach{f->try{val name=f.name.trimEnd('/');val rel=if(relative.isBlank())name else relative.trimEnd('/')+"/"+name;if(f.isDirectory)walk(f,rel) else out+=RemoteFileInfo(rel,runCatching{f.length()}.getOrDefault(-1L))}catch(e:jcifs.smb.SmbException){if(!e.message.orEmpty().contains("cannot find the file",true))throw e}}};walk(root,clean);return out}
 fun delete(path:String){val f=SmbFile(url(path),context);if(f.exists())f.delete()}
 fun fileSize(path:String):Long{val f=SmbFile(url(path),context);return if(f.exists())f.length() else -1L}
 fun lastModified(path:String):Long{val f=SmbFile(url(path),context);return if(f.exists())f.lastModified() else 0L}
 fun move(from:String,to:String){val src=SmbFile(url(from),context);val dst=SmbFile(url(to),context);if(dst.exists())dst.delete();src.renameTo(dst)}
 fun openInput(path:String):InputStream=SmbFile(url(path),context).inputStream
 fun openOutput(path:String):OutputStream=SmbFile(url(path),context).outputStream
}
