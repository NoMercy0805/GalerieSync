package de.galeriesync.worker
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import de.galeriesync.data.SettingsStore
import de.galeriesync.sync.SyncProgress
import de.galeriesync.sync.SyncService
import de.galeriesync.vpn.VpnManager
class SyncWorker(appContext:Context,params:WorkerParameters):CoroutineWorker(appContext,params){
 private val nm=applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
 private val channel="galeriesync_sync"
 private fun ensureChannel(){if(Build.VERSION.SDK_INT>=26)nm.createNotificationChannel(NotificationChannel(channel,"Synchronisation",NotificationManager.IMPORTANCE_LOW))}
 private fun notification(title:String,text:String,progress:Int?=null)=NotificationCompat.Builder(applicationContext,channel).setSmallIcon(android.R.drawable.stat_sys_upload).setContentTitle(title).setContentText(text).setStyle(NotificationCompat.BigTextStyle().bigText(text)).setOnlyAlertOnce(true).setSilent(true).setOngoing(progress!=null).apply{if(progress!=null)setProgress(100,progress.coerceIn(0,100),false)}.build()
 private fun foreground(text:String,percent:Int?=null):ForegroundInfo{ensureChannel();return if(Build.VERSION.SDK_INT>=29) ForegroundInfo(4101,notification("GalerieSync synchronisiert",text,percent),android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC) else ForegroundInfo(4101,notification("GalerieSync synchronisiert",text,percent))}
 private fun notifyDone(title:String,text:String){if(SettingsStore(applicationContext).loadNotificationsEnabled()){ensureChannel();nm.notify(4102,notification(title,text))}}
 override suspend fun doWork():Result{
  val store=SettingsStore(applicationContext);val requested=inputData.getString("profile_id");val profiles=store.loadProfiles().filter{it.enabled&&(requested==null||it.id==requested)};if(profiles.isEmpty())return Result.success()
  setForeground(foreground("Synchronisation wird vorbereitet …",0))
  var activeProfile:de.galeriesync.data.NasSettings?=null;var startedVpn=false;var lastBytes=0L;var lastAt=System.currentTimeMillis();var speed=0L;var lastNotificationAt=0L
  return runCatching{
   try{
    startedVpn=VpnManager.connectIfNeeded(applicationContext)
    profiles.forEach{p->
     activeProfile=p
     if(startedVpn)VpnManager.waitForSmb(p.host)
     val result=SyncService(applicationContext).run(p){sp:SyncProgress->
      val percent=if(sp.totalBytes>0)((sp.doneBytes*100/sp.totalBytes).toInt()) else if(sp.totalFiles>0)(sp.doneFiles*100/sp.totalFiles) else 0
      val now=System.currentTimeMillis();if(now-lastAt>=1000&&sp.doneBytes>=lastBytes){speed=(sp.doneBytes-lastBytes)*1000L/(now-lastAt).coerceAtLeast(1);lastBytes=sp.doneBytes;lastAt=now};setProgressAsync(workDataOf("doneFiles" to sp.doneFiles,"totalFiles" to sp.totalFiles,"doneBytes" to sp.doneBytes,"totalBytes" to sp.totalBytes,"message" to sp.message));val text=if(sp.totalFiles>0)"$percent % · ${sp.doneFiles} von ${sp.totalFiles} Dateien · ${formatBytes(sp.doneBytes)} / ${formatBytes(sp.totalBytes)}"+if(speed>0)" · ${formatBytes(speed)}/s" else "" else sp.message
      if(now-lastNotificationAt>=1000){lastNotificationAt=now;nm.notify(4101,notification("GalerieSync synchronisiert",text,percent))}
     }
     val time=java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"))
     val summary="${result.copied} kopiert · ${result.deleted} gelöscht · ${result.skipped} unverändert · ${result.failed} Fehler"
     store.addSyncHistory("$time · [${p.id}] ${p.profileName.ifBlank{p.name.ifBlank{p.host}}} · $summary")
     store.saveProfileLastSync(p.id,"$time · $summary")
    }
   }finally{if(startedVpn)VpnManager.disconnect()}
   nm.cancel(4101);notifyDone("Sync erfolgreich","GalerieSync wurde erfolgreich synchronisiert");Result.success()
  }.getOrElse{error->
   val time=java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"))
   activeProfile?.let{p->store.addSyncHistory("$time · [${p.id}] ${p.profileName} · Fehlgeschlagen: "+(error.message?:"Unbekannter Fehler"))}
   val it=error;nm.cancel(4101);notifyDone("GalerieSync","Synchronisation fehlgeschlagen: "+(it.message?:"Unbekannter Fehler"));if(requested!=null)Result.failure(workDataOf("error" to (it.message?:"Unbekannter Fehler"))) else Result.retry()}
 }
 private fun formatBytes(v:Long):String=when{v>=1073741824L->"%.1f GB".format(v/1073741824.0);v>=1048576L->"%.1f MB".format(v/1048576.0);v>=1024L->"%.1f KB".format(v/1024.0);else->"$v B"}
}
