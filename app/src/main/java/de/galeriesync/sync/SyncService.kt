package de.galeriesync.sync
import android.content.ContentValues
import android.content.Context
import android.provider.MediaStore
import de.galeriesync.data.NasSettings
import de.galeriesync.data.SyncDirection
import de.galeriesync.data.SettingsStore
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import java.io.IOException
data class SyncProgress(val doneFiles:Int,val totalFiles:Int,val doneBytes:Long,val totalBytes:Long,val message:String)
data class SyncResult(val copied:Int,val skipped:Int,val failed:Int,val transport:String="SMB",val lastError:String?=null)
class SyncService(private val context:Context){
 suspend fun run(settings:NasSettings,progress:(SyncProgress)->Unit={}):SyncResult=withContext(Dispatchers.IO){
  require(settings.host.isNotBlank()&&settings.share.isNotBlank()){"NAS-Adresse und Freigabe fehlen."};val smb=SmbProvider(settings);var connected=false;var connectError:Throwable?=null;repeat(5){if(!connected){runCatching{smb.test()}.onSuccess{connected=true}.onFailure{connectError=it};if(!connected)kotlinx.coroutines.delay(1000)}};if(!connected)throw IOException("NAS nach VPN-Aufbau nicht erreichbar: "+(connectError?.message?:"SMB-Verbindung fehlgeschlagen"),connectError);smb.ensureDirectory(settings.remoteFolder)
  progress(SyncProgress(0,0,0,0,"Dateien werden geprüft …"))
  val local=MediaScanner(context.contentResolver).scan(settings.sourceFolders);val root=settings.remoteFolder.trimEnd('/');val speedLimitBytes=SettingsStore(context).loadSpeedLimitKbps()*1024L
  var copied=0;var skipped=0;var alreadyCurrent=0;var failed=0;var lastError:String?=null
  val remoteInfo=smb.listRecursiveInfo(root).map{info->SmbProvider.RemoteFileInfo(info.path.removePrefix(root+"/").trimStart('/'),info.size)}.filterNot{it.path.endsWith(".galeriesync-part")}
  var remote=remoteInfo.map{it.path}.toSet()
  var remoteByKey=remoteInfo.associateBy{canonicalPath(it.path)}
  progress(SyncProgress(0,0,0,0,"Abgleich abgeschlossen · Änderungen werden vorbereitet …"))
  val store=SettingsStore(context)
  if(settings.direction==SyncDirection.BIDIRECTIONAL){
   val localPaths=local.map{structuredRelativePath(it.relativePath)}.toSet()
   val previous=store.loadSyncSnapshot(settings.id)
   if(previous.isNotEmpty()){
    val deletedOnPhone=previous-localPaths
    val deletedOnNas=previous-remote
    deletedOnPhone.intersect(remote).forEach{rel->smb.delete(root+"/"+rel)}
    remote=remote-deletedOnPhone
    // Android MediaStore deletion needs explicit user approval on modern Android,
    // so NAS-side deletions are not silently propagated to the phone.
   }
  }
  if(settings.direction==SyncDirection.PHONE_TO_NAS||settings.direction==SyncDirection.BIDIRECTIONAL){
   val localPaths=local.map{structuredRelativePath(it.relativePath)}.toSet()
   val localKeys=localPaths.map{canonicalPath(it)}.toSet()
   if(settings.direction==SyncDirection.PHONE_TO_NAS){val obsolete=remoteInfo.filter{canonicalPath(it.path) !in localKeys};obsolete.forEach{info->smb.delete(root+"/"+info.path)};val obsoleteKeys=obsolete.map{canonicalPath(it.path)}.toSet();remote=remote.filter{canonicalPath(it) !in obsoleteKeys}.toSet();remoteByKey=remoteByKey.filterKeys{it !in obsoleteKeys}}
   val pending=local.filter{m->val relative=structuredRelativePath(m.relativePath);val existing=remoteByKey[canonicalPath(relative)];existing==null||existing.size!=m.size}
   alreadyCurrent=local.size-pending.size
   val total=local.size
   val totalBytes=local.sumOf{it.size};var processedBytes=local.filterNot{it in pending}.sumOf{it.size}
   progress(SyncProgress(alreadyCurrent,total,processedBytes,totalBytes,if(pending.isEmpty())"Alles aktuell" else "$alreadyCurrent von $total bereits vorhanden · ${pending.size} ausstehend"))
   val progressMutex=Mutex();val uploadSlots=Semaphore(2);var lastProgressAt=0L
   pending.map{m->async{
    uploadSlots.withPermit{
     val relative=structuredRelativePath(m.relativePath);val target=root+"/"+relative
     if(settings.direction==SyncDirection.BIDIRECTIONAL&&relative in remote&&smb.fileSize(target)!=m.size&&smb.lastModified(target)>m.modified+2000L){progressMutex.withLock{skipped++;processedBytes+=m.size;progress(SyncProgress(alreadyCurrent+copied+skipped,total,processedBytes.coerceAtMost(totalBytes),totalBytes,"Konflikt: NAS-Version ist neuer · "+m.displayName))}}
     else try{
      var uploaded=false;var attempt=0;var uploadError:Throwable?=null
      while(!uploaded&&attempt<3){attempt++;var attemptBytes=0L
       try{
        if(attempt>1){progressMutex.withLock{progress(SyncProgress(alreadyCurrent+copied+skipped,total,processedBytes.coerceAtMost(totalBytes),totalBytes,"Verbindung wird wiederhergestellt · Versuch $attempt/3 · "+m.displayName))};kotlinx.coroutines.delay(1000L*attempt);smb.test()}
        target.substringBeforeLast('/').takeIf{it!=root}?.let{smb.ensureDirectory(it)}
        val temp=target+".galeriesync-part";smb.delete(temp)
        context.contentResolver.openInputStream(m.uri)!!.use{input->BufferedInputStream(input).use{src->BufferedOutputStream(smb.openOutput(temp)).use{dst->
         val buffer=ByteArray(256*1024);var lastReported=0L;val throttleStart=System.nanoTime()
         while(true){val read=src.read(buffer);if(read<0)break;dst.write(buffer,0,read);attemptBytes+=read;if(speedLimitBytes>0){val expectedMs=attemptBytes*1000L/speedLimitBytes;val elapsedMs=(System.nanoTime()-throttleStart)/1_000_000L;if(expectedMs>elapsedMs)kotlinx.coroutines.delay((expectedMs-elapsedMs).coerceAtMost(1000L))};progressMutex.withLock{processedBytes+=read;val now=System.currentTimeMillis();if(attemptBytes-lastReported>=1024&&now-lastProgressAt>=1000){lastReported=attemptBytes;lastProgressAt=now;progress(SyncProgress(alreadyCurrent+copied+skipped,total,processedBytes.coerceAtMost(totalBytes),totalBytes,"Hochladen: "+m.displayName))}}}
         dst.flush()
        }}}
        if(smb.fileSize(temp)!=m.size)throw IOException("Größenprüfung fehlgeschlagen")
        smb.move(temp,target);uploaded=true
       }catch(t:Throwable){uploadError=t;progressMutex.withLock{processedBytes=(processedBytes-attemptBytes).coerceAtLeast(0)};runCatching{smb.delete(target+".galeriesync-part")}}
      }
      if(!uploaded)throw IOException("Verbindung zur NAS während der Übertragung verloren.",uploadError)
      progressMutex.withLock{copied++;progress(SyncProgress(alreadyCurrent+copied+skipped,total,processedBytes.coerceAtMost(totalBytes),totalBytes,"Hochgeladen: "+m.displayName))}
     }catch(t:Throwable){progressMutex.withLock{failed++;lastError="Datei: "+m.displayName+" · "+(t.message?:"SMB-Verbindung unterbrochen")};throw IOException("Synchronisation unterbrochen · Datei: "+m.displayName+" · Verbindung zur NAS während der Übertragung verloren.",t)}
    }
   }}.awaitAll()
  }
  if(settings.direction==SyncDirection.NAS_TO_PHONE||settings.direction==SyncDirection.BIDIRECTIONAL){
   val existing=local.map{structuredRelativePath(it.relativePath).lowercase()}.toSet();val files=smb.listRecursive(root).filter{remotePath->val rel=remotePath.removePrefix(root+"/");!rel.endsWith(".galeriesync-part")&&rel.lowercase() !in existing};val total=files.size
   progress(SyncProgress(0,total,0,0,"Dateien werden abgeglichen …"))
   files.forEach{p->try{downloadToMediaStore(smb,p,root);copied++;progress(SyncProgress(copied,total,0,0,"Heruntergeladen: "+p.substringAfterLast('/')))}catch(t:Throwable){failed++;lastError=t.message?:t.javaClass.simpleName;throw IOException("SMB-Verbindung unterbrochen bei "+p.substringAfterLast('/')+": "+lastError,t)}}
  }
  if(settings.direction==SyncDirection.BIDIRECTIONAL&&failed==0){
   val afterLocal=MediaScanner(context.contentResolver).scan(settings.sourceFolders).map{structuredRelativePath(it.relativePath)}.toSet()
   val afterRemote=smb.listRecursive(root).map{it.removePrefix(root+"/")}.filterNot{it.endsWith(".galeriesync-part")}.toSet()
   store.saveSyncSnapshot(settings.id,afterLocal.intersect(afterRemote))
  }
  SyncResult(copied,skipped+if(settings.direction==SyncDirection.PHONE_TO_NAS||settings.direction==SyncDirection.BIDIRECTIONAL) alreadyCurrent else 0,failed,"SMB",lastError)
 }
 private fun canonicalPath(path:String)=path.replace('\\','/').trim('/').lowercase(java.util.Locale.ROOT)
 private fun structuredRelativePath(relativePath:String):String{
  // Keep Android's MediaStore path exactly below the configured NAS target.
  // Example: DCIM/Camera/IMG.jpg -> <remoteFolder>/DCIM/Camera/IMG.jpg
  //          Pictures/Screenshots/a.png -> <remoteFolder>/Pictures/Screenshots/a.png
  val clean=relativePath.replace('\\','/').trim('/')
  return clean.ifBlank{"Bilder & Videos"}
 }
 private fun downloadToMediaStore(smb:SmbProvider,remotePath:String,root:String){val name=remotePath.substringAfterLast('/');val mime=when(name.substringAfterLast('.',"").lowercase()){"jpg","jpeg"->"image/jpeg";"png"->"image/png";"webp"->"image/webp";"heic"->"image/heic";"mp4"->"video/mp4";"mov"->"video/quicktime";"mkv"->"video/x-matroska";else->return};val collection=if(mime.startsWith("video/"))MediaStore.Video.Media.EXTERNAL_CONTENT_URI else MediaStore.Images.Media.EXTERNAL_CONTENT_URI;val values=ContentValues().apply{put(MediaStore.MediaColumns.DISPLAY_NAME,name);put(MediaStore.MediaColumns.MIME_TYPE,mime);val rel=remotePath.removePrefix(root).trimStart('/').substringBeforeLast('/',"");val base=if(mime.startsWith("video/"))"Movies/GalerieSync" else "Pictures/GalerieSync";put(MediaStore.MediaColumns.RELATIVE_PATH,if(rel.isBlank())base else "$base/$rel");put(MediaStore.MediaColumns.IS_PENDING,1)};val uri=context.contentResolver.insert(collection,values)?:error("MediaStore insert fehlgeschlagen");try{context.contentResolver.openOutputStream(uri)!!.use{out->BufferedOutputStream(out).use{dst->BufferedInputStream(smb.openInput(remotePath)).use{it.copyTo(dst)}}};values.clear();values.put(MediaStore.MediaColumns.IS_PENDING,0);context.contentResolver.update(uri,values,null,null)}catch(t:Throwable){context.contentResolver.delete(uri,null,null);throw t}}
}
