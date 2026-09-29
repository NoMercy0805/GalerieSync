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
class SmbProvider(private val settings:NasSettings){
 private val context:CIFSContext by lazy{val props=Properties().apply{setProperty("jcifs.smb.client.enableSMB2","true");setProperty("jcifs.smb.client.disableSMB1","true")};BaseContext(PropertyConfiguration(props)).withCredentials(NtlmPasswordAuthenticator(settings.domain.ifBlank{null},settings.username,settings.password))}
 private fun url(path:String="")="smb://"+settings.host.trim('/')+"/"+settings.share.trim('/')+"/"+path.trimStart('/')
 private fun serverUrl()="smb://"+settings.host.trim('/')+"/"
 fun listShares():List<String>{val root=SmbFile(serverUrl(),context);root.connect();return root.listFiles().filter{it.isDirectory}.map{it.name.trimEnd('/')}.filter{it.isNotBlank()}.sorted()}
 fun test():Boolean{SmbFile(url(),context).connect();return true}
 fun ensureDirectory(path:String){val f=SmbFile(url(path),context);if(!f.exists())f.mkdirs()}
 fun listRecursive(path:String=""):List<String>{val out=mutableListOf<String>();val root=SmbFile(url(path),context);if(!root.exists())return out;root.listFiles().forEach{f->val rel=if(path.isBlank())f.name else path.trimEnd('/')+"/"+f.name;if(f.isDirectory)out+=listRecursive(rel) else out+=rel};return out}
 fun openInput(path:String):InputStream=SmbFile(url(path),context).inputStream
 fun openOutput(path:String):OutputStream=SmbFile(url(path),context).outputStream
}
