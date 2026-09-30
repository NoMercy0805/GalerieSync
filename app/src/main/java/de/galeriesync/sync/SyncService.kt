package de.galeriesync.sync
import android.content.ContentValues
import android.content.Context
import android.provider.MediaStore
import de.galeriesync.data.NasSettings
import de.galeriesync.data.SyncDirection
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
data class SyncResult(val copied:Int,val skipped:Int,val failed:Int,val transport:String="SMB",val lastError:String?=null)
class SyncService(private val context:Context){
 suspend fun run(settings:NasSettings,progress:(Int,Int,String)->Unit={_,_,_->}):SyncResult=withContext(Dispatchers.IO){
  require(settings.host.isNotBlank()&&settings.share.isNotBlank()){"NAS-Adresse und Freigabe fehlen."};val smb=SmbProvider(settings);smb.test();smb.ensureDirectory(settings.remoteFolder)
  val local=MediaScanner(context.contentResolver).scan(settings.sourceFolders);val root=settings.remoteFolder.trimEnd('/')
  var copied=0;var skipped=0;var failed=0;var lastError:String?=null
  val remote=smb.listRecursive(root).map{it.removePrefix(root+"/")}.toSet()
  if(settings.direction==SyncDirection.PHONE_TO_NAS||settings.direction==SyncDirection.BIDIRECTIONAL){
   val total=local.size
   local.forEach{m->
    val relative=structuredRelativePath(m.relativePath);val target=root+"/"+relative
    if(relative in remote){skipped++;progress(copied+skipped,total,"Übersprungen: "+m.displayName)}
    else try{
     smb.test();target.substringBeforeLast('/').takeIf{it!=root}?.let{smb.ensureDirectory(it)}
     context.contentResolver.openInputStream(m.uri)!!.use{input->BufferedInputStream(input).use{src->BufferedOutputStream(smb.openOutput(target)).use{dst->src.copyTo(dst)}}}
     copied++;progress(copied+skipped,total,"Hochgeladen: "+m.displayName)
    }catch(t:Throwable){failed++;lastError=t.message?:t.javaClass.simpleName;throw IOException("SMB-Verbindung unterbrochen bei "+m.displayName+": "+lastError,t)}
   }
  }
  if(settings.direction==SyncDirection.NAS_TO_PHONE||settings.direction==SyncDirection.BIDIRECTIONAL){
   val existing=local.map{it.displayName.lowercase()}.toSet();val files=smb.listRecursive(root).filter{it.substringAfterLast('/').lowercase() !in existing};val total=files.size
   files.forEach{p->try{smb.test();downloadToMediaStore(smb,p,root);copied++;progress(copied,total,"Heruntergeladen: "+p.substringAfterLast('/'))}catch(t:Throwable){failed++;lastError=t.message?:t.javaClass.simpleName;throw IOException("SMB-Verbindung unterbrochen bei "+p.substringAfterLast('/')+": "+lastError,t)}}
  }
  SyncResult(copied,skipped,failed,"SMB",lastError)
 }
 private fun structuredRelativePath(relativePath:String):String{
  val clean=relativePath.trimStart('/')
  val parts=clean.split('/').filter{it.isNotBlank()}
  if(parts.size<2)return clean
  val top=parts.first()
  val content=if(top.lowercase() in setOf("dcim","pictures","movies"))parts.drop(1).joinToString("/") else parts.joinToString("/")
  return if(content.isBlank())"Bilder & Videos" else "Bilder & Videos/$content"
 }
 private fun downloadToMediaStore(smb:SmbProvider,remotePath:String,root:String){val name=remotePath.substringAfterLast('/');val mime=when(name.substringAfterLast('.',"").lowercase()){"jpg","jpeg"->"image/jpeg";"png"->"image/png";"webp"->"image/webp";"heic"->"image/heic";"mp4"->"video/mp4";"mov"->"video/quicktime";"mkv"->"video/x-matroska";else->return};val collection=if(mime.startsWith("video/"))MediaStore.Video.Media.EXTERNAL_CONTENT_URI else MediaStore.Images.Media.EXTERNAL_CONTENT_URI;val values=ContentValues().apply{put(MediaStore.MediaColumns.DISPLAY_NAME,name);put(MediaStore.MediaColumns.MIME_TYPE,mime);val rel=remotePath.removePrefix(root).trimStart('/').substringBeforeLast('/',"");val base=if(mime.startsWith("video/"))"Movies/GalerieSync" else "Pictures/GalerieSync";put(MediaStore.MediaColumns.RELATIVE_PATH,if(rel.isBlank())base else "$base/$rel");put(MediaStore.MediaColumns.IS_PENDING,1)};val uri=context.contentResolver.insert(collection,values)?:error("MediaStore insert fehlgeschlagen");try{context.contentResolver.openOutputStream(uri)!!.use{out->BufferedOutputStream(out).use{dst->BufferedInputStream(smb.openInput(remotePath)).use{it.copyTo(dst)}}};values.clear();values.put(MediaStore.MediaColumns.IS_PENDING,0);context.contentResolver.update(uri,values,null,null)}catch(t:Throwable){context.contentResolver.delete(uri,null,null);throw t}}
}
