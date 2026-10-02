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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.withContext
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import java.io.IOException
data class SyncProgress(val doneFiles:Int,val totalFiles:Int,val doneBytes:Long,val totalBytes:Long,val message:String,val alreadyExisting:Int=0,val pendingUploads:Int=0,val uploadedThisRun:Int=0,val activeUploads:List<String> = emptyList())
data class SyncResult(val copied:Int,val skipped:Int,val failed:Int,val deleted:Int=0,val transport:String="SMB",val lastError:String?=null)
class SyncService(private val context:Context){
 suspend fun run(settings:NasSettings,progress:(SyncProgress)->Unit={}):SyncResult=withContext(Dispatchers.IO){
  require(settings.host.isNotBlank()&&settings.share.isNotBlank()){"NAS-Adresse und Freigabe fehlen."};val smb=SmbProvider(settings);var connected=false;var connectError:Throwable?=null;repeat(5){if(!connected){runCatching{smb.test()}.onSuccess{connected=true}.onFailure{connectError=it};if(!connected)kotlinx.coroutines.delay(1000)}};if(!connected)throw IOException("NAS nach VPN-Aufbau nicht erreichbar: "+(connectError?.message?:"SMB-Verbindung fehlgeschlagen"),connectError);smb.ensureDirectory(settings.remoteFolder)
  progress(SyncProgress(0,0,0,0,"Dateien werden geprüft …"))
  val local=MediaScanner(context.contentResolver).scan(settings.sourceFolders);val root=settings.remoteFolder.trim('/');val settingsStore=SettingsStore(context);val speedLimitBytes=settingsStore.loadSpeedLimitKbps()*1024L;val concurrentUploads=settingsStore.loadConcurrentUploads()
  var copied=0;var skipped=0;var alreadyCurrent=0;var failed=0;var deleted=0;var lastError:String?=null
  val remoteInfo=smb.listRecursiveInfo(root).filterNot{it.path.endsWith(".galeriesync-part")}
  var remote=remoteInfo.map{it.path}.toSet()
  var remoteByKey=remoteInfo.associateBy{canonicalPath(it.path)}
  progress(SyncProgress(0,local.size,0,0,"Vorhandene Dateien werden geprüft · 0 / ${local.size}",0,0,0))
  val store=SettingsStore(context)
  if(settings.direction==SyncDirection.BIDIRECTIONAL){
   val localPaths=local.map{structuredRelativePath(it.relativePath)}.toSet()
   val previous=store.loadSyncSnapshot(settings.id)
   if(previous.isNotEmpty()){
    val deletedOnPhone=previous-localPaths
    val deletedOnNas=previous-remote
    deletedOnPhone.intersect(remote).forEach{rel->smb.delete(root+"/"+rel);deleted++}
    remote=remote-deletedOnPhone
    // Android MediaStore deletion needs explicit user approval on modern Android,
    // so NAS-side deletions are not silently propagated to the phone.
   }
  }
  if(settings.direction==SyncDirection.PHONE_TO_NAS||settings.direction==SyncDirection.BIDIRECTIONAL){
   // Persistent one-way state: current local inventory + previous successful inventory.
   // This lets us distinguish a real phone-side deletion from an unrelated NAS-only file.
   val previousIndex=store.loadSyncIndex(settings.id)
   // Reliability first: verify every local file against its exact SMB target.
   // This is intentionally sequential because this mode proved correct on the target NAS.
   val plan=ArrayList<Triple<LocalMedia,String,SmbProvider.RemoteFileInfo?>>(local.size)
   local.forEachIndexed{index,m->
    coroutineContext.ensureActive()
    val relative=structuredRelativePath(m.relativePath)
    val size=runCatching{smb.fileSize(root+"/"+relative)}.getOrDefault(-1L)
    val existing=if(size>=0L)SmbProvider.RemoteFileInfo(relative,size) else null
    plan+=Triple(m,relative,existing)
    val checked=index+1
    val matching=plan.count{(media,_,remoteFile)->remoteFile!=null&&remoteFile.size==media.size}
    val needsUpload=checked-matching
    progress(SyncProgress(checked,local.size,0,0,"Vorhandene Dateien werden geprüft · $checked / ${local.size} · $matching bereits vorhanden",matching,needsUpload,0))
   }
   val currentByKey=plan.associateBy{canonicalPath(it.second)}
   val pathMatches=plan.count{it.third!=null}
   val sizeMatches=plan.count{(m,_,existing)->existing!=null&&existing.size==m.size}
   val pending=plan.filter{(m,_,existing)->existing==null||existing.size!=m.size}
   alreadyCurrent=sizeMatches
   val total=plan.size
   val totalBytes=local.sumOf{it.size};val pendingBytes=pending.sumOf{it.first.size};var completedUploadBytes=0L

   // Sync deletions: only remove remote files that were in our last successful index
   // and are now genuinely absent from the current phone inventory.
   val deletedKeys=previousIndex.keys-currentByKey.keys
   if(previousIndex.isNotEmpty()&&deletedKeys.isNotEmpty()){
    deletedKeys.forEach{key->remoteByKey[key]?.let{info->runCatching{smb.delete(root+"/"+info.path)}.onSuccess{deleted++}.onFailure{t->failed++;lastError="Löschen: "+info.path+" · "+(t.message?:t.javaClass.simpleName)}}}
   }

   progress(SyncProgress(alreadyCurrent,total,totalBytes-pendingBytes,totalBytes,"NAS: ${remoteInfo.size} Dateien · Pfadtreffer: $pathMatches · Größen-Treffer: $sizeMatches · ${pending.size} Uploads",alreadyCurrent,pending.size,0))
   val uploadMutex=Mutex();val uploadGate=Semaphore(concurrentUploads);val activeUploads=linkedSetOf<String>()
   pending.map{(m,relative,existing)->async{
    uploadGate.withPermit{
    coroutineContext.ensureActive()
    uploadMutex.withLock{activeUploads+=m.displayName}
    val activeNow=suspend{uploadMutex.withLock{activeUploads.toList()}}
    val target=root+"/"+relative
    if(settings.direction==SyncDirection.BIDIRECTIONAL&&existing!=null&&smb.lastModified(target)>m.modified+2000L){skipped++;progress(SyncProgress(alreadyCurrent+copied+skipped,total,(totalBytes-pendingBytes+completedUploadBytes).coerceAtMost(totalBytes),totalBytes,"Konflikt übersprungen: "+m.displayName,alreadyCurrent,pending.size,copied));return@withPermit}
    var success=false;var fileError:Throwable?=null
    for(attempt in 1..3){
     var attemptBytes=0L;val temp=target+".galeriesync-part"
     try{
      if(attempt>1){progress(SyncProgress(alreadyCurrent+copied+skipped,total,(totalBytes-pendingBytes+completedUploadBytes).coerceAtMost(totalBytes),totalBytes,"Erneuter Versuch $attempt/3 · "+m.displayName,alreadyCurrent,pending.size,copied));kotlinx.coroutines.delay(1000L*attempt);smb.test()}
      target.substringBeforeLast('/').takeIf{it!=root}?.let{smb.ensureDirectory(it)};runCatching{smb.delete(temp)}
      context.contentResolver.openInputStream(m.uri)?.use{input->BufferedInputStream(input).use{src->BufferedOutputStream(smb.openOutput(temp)).use{dst->
       val buffer=ByteArray(256*1024);val throttleStart=System.nanoTime();var lastReport=0L
       while(true){coroutineContext.ensureActive();val read=src.read(buffer);if(read<0)break;dst.write(buffer,0,read);attemptBytes+=read
        if(speedLimitBytes>0){val expectedMs=attemptBytes*1000L/speedLimitBytes;val elapsedMs=(System.nanoTime()-throttleStart)/1_000_000L;if(expectedMs>elapsedMs)kotlinx.coroutines.delay((expectedMs-elapsedMs).coerceAtMost(1000L))}
        if(attemptBytes-lastReport>=1024*1024){lastReport=attemptBytes;progress(SyncProgress(alreadyCurrent+copied+skipped,total,(totalBytes-pendingBytes+completedUploadBytes+attemptBytes).coerceAtMost(totalBytes),totalBytes,"Hochladen",alreadyCurrent,pending.size,copied,activeNow()))}
       };dst.flush()
      }}}?:throw IOException("Lokale Datei kann nicht geöffnet werden")
      if(smb.fileSize(temp)!=m.size)throw IOException("Größenprüfung fehlgeschlagen")
      smb.move(temp,target);success=true;uploadMutex.withLock{completedUploadBytes+=m.size;copied++};uploadMutex.withLock{activeUploads-=m.displayName};progress(SyncProgress(alreadyCurrent+copied+skipped,total,(totalBytes-pendingBytes+completedUploadBytes).coerceAtMost(totalBytes),totalBytes,"Hochladen",alreadyCurrent,pending.size,copied,activeNow()));break
     }catch(t:CancellationException){runCatching{smb.delete(temp)};throw t}catch(t:Throwable){fileError=t;runCatching{smb.delete(temp)}}
    }
    if(!success){uploadMutex.withLock{activeUploads-=m.displayName;failed++};lastError="Datei: "+m.displayName+" · "+(fileError?.message?:"Unbekannter Fehler");progress(SyncProgress(alreadyCurrent+copied+skipped,total,(totalBytes-pendingBytes+completedUploadBytes).coerceAtMost(totalBytes),totalBytes,"Fehler, weiter mit nächster Datei: "+m.displayName,alreadyCurrent,pending.size,copied))}
    }
   }}.awaitAll()
   // Advance deletion state only after a completely successful run.
   if(failed==0){store.saveSyncIndex(settings.id,plan.map{(m,relative,_)->SettingsStore.SyncFileState(canonicalPath(relative),m.size,m.modified)})}
  }
  if(settings.direction==SyncDirection.NAS_TO_PHONE||settings.direction==SyncDirection.BIDIRECTIONAL){
   val existing=local.map{structuredRelativePath(it.relativePath).lowercase()}.toSet();val files=smb.listRecursive(root).filter{remotePath->val rel=remotePath;!rel.endsWith(".galeriesync-part")&&rel.lowercase() !in existing};val total=files.size
   progress(SyncProgress(0,total,0,0,"Dateien werden abgeglichen …"))
   files.forEach{p->try{downloadToMediaStore(smb,p,root);copied++;progress(SyncProgress(copied,total,0,0,"Heruntergeladen: "+p.substringAfterLast('/')))}catch(t:Throwable){failed++;lastError=t.message?:t.javaClass.simpleName;throw IOException("SMB-Verbindung unterbrochen bei "+p.substringAfterLast('/')+": "+lastError,t)}}
  }
  if(settings.direction==SyncDirection.BIDIRECTIONAL&&failed==0){
   val afterLocal=MediaScanner(context.contentResolver).scan(settings.sourceFolders).map{structuredRelativePath(it.relativePath)}.toSet()
   val afterRemote=smb.listRecursive(root).filterNot{it.endsWith(".galeriesync-part")}.toSet()
   store.saveSyncSnapshot(settings.id,afterLocal.intersect(afterRemote))
  }
  SyncResult(copied,skipped+if(settings.direction==SyncDirection.PHONE_TO_NAS||settings.direction==SyncDirection.BIDIRECTIONAL) alreadyCurrent else 0,failed,deleted,"SMB",lastError)
 }
 private fun canonicalPath(path:String):String{
  val normalized=path.replace('\\','/').trim('/').lowercase(java.util.Locale.ROOT)
  // Older versions stored media below an extra "Bilder & Videos" directory.
  // Normalize that legacy layout so existing files are skipped instead of re-uploaded.
  return normalized.removePrefix("bilder & videos/")
 }
 private fun structuredRelativePath(relativePath:String):String{
  // Keep Android's MediaStore path exactly below the configured NAS target.
  // Example: DCIM/Camera/IMG.jpg -> <remoteFolder>/DCIM/Camera/IMG.jpg
  //          Pictures/Screenshots/a.png -> <remoteFolder>/Pictures/Screenshots/a.png
  val clean=relativePath.replace('\\','/').trim('/')
  return clean.ifBlank{"Bilder & Videos"}
 }
 private fun downloadToMediaStore(smb:SmbProvider,remotePath:String,root:String){val name=remotePath.substringAfterLast('/');val mime=when(name.substringAfterLast('.',"").lowercase()){"jpg","jpeg"->"image/jpeg";"png"->"image/png";"webp"->"image/webp";"heic"->"image/heic";"mp4"->"video/mp4";"mov"->"video/quicktime";"mkv"->"video/x-matroska";else->return};val collection=if(mime.startsWith("video/"))MediaStore.Video.Media.EXTERNAL_CONTENT_URI else MediaStore.Images.Media.EXTERNAL_CONTENT_URI;val values=ContentValues().apply{put(MediaStore.MediaColumns.DISPLAY_NAME,name);put(MediaStore.MediaColumns.MIME_TYPE,mime);val rel=remotePath.removePrefix(root).trimStart('/').substringBeforeLast('/',"");val base=if(mime.startsWith("video/"))"Movies/GalerieSync" else "Pictures/GalerieSync";put(MediaStore.MediaColumns.RELATIVE_PATH,if(rel.isBlank())base else "$base/$rel");put(MediaStore.MediaColumns.IS_PENDING,1)};val uri=context.contentResolver.insert(collection,values)?:error("MediaStore insert fehlgeschlagen");try{context.contentResolver.openOutputStream(uri)!!.use{out->BufferedOutputStream(out).use{dst->BufferedInputStream(smb.openInput(remotePath)).use{it.copyTo(dst)}}};values.clear();values.put(MediaStore.MediaColumns.IS_PENDING,0);context.contentResolver.update(uri,values,null,null)}catch(t:Throwable){context.contentResolver.delete(uri,null,null);throw t}}
}
