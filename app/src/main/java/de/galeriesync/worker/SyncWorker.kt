package de.galeriesync.worker
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import de.galeriesync.data.SettingsStore
import de.galeriesync.sync.SyncProgress
import de.galeriesync.sync.SyncService
import de.galeriesync.vpn.VpnManager
class SyncWorker(appContext:Context,params:WorkerParameters):CoroutineWorker(appContext,params){
 private val nm=applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
 private val channel="galeriesync_sync"
 private fun ensureChannel(){if(Build.VERSION.SDK_INT>=26)nm.createNotificationChannel(NotificationChannel(channel,"Synchronisation",NotificationManager.IMPORTANCE_LOW))}
 private fun notification(title:String,text:String,progress:Int?=null)=NotificationCompat.Builder(applicationContext,channel).setSmallIcon(android.R.drawable.stat_sys_upload).setContentTitle(title).setContentText(text).setOnlyAlertOnce(true).setOngoing(progress!=null).apply{if(progress!=null)setProgress(100,progress.coerceIn(0,100),false)}.build()
 private fun foreground(text:String,percent:Int?=null):ForegroundInfo{ensureChannel();return ForegroundInfo(4101,notification("GalerieSync synchronisiert",text,percent))}
 private fun notifyDone(title:String,text:String){if(SettingsStore(applicationContext).loadNotificationsEnabled()){ensureChannel();nm.notify(4102,notification(title,text))}}
 override suspend fun doWork():Result{
  val store=SettingsStore(applicationContext);val profiles=store.loadProfiles().filter{it.enabled};if(profiles.isEmpty())return Result.success()
  setForeground(foreground("Synchronisation wird gestartet …",0));if(store.loadNotificationsEnabled())nm.notify(4103,notification("GalerieSync","Synchronisation gestartet"))
  var startedVpn=false
  return runCatching{
   try{
    startedVpn=VpnManager.connectIfNeeded(applicationContext)
    profiles.forEach{p->
     if(startedVpn)VpnManager.waitForSmb(p.host)
     SyncService(applicationContext).run(p){sp:SyncProgress->
      val percent=if(sp.totalBytes>0)((sp.doneBytes*100/sp.totalBytes).toInt()) else if(sp.totalFiles>0)(sp.doneFiles*100/sp.totalFiles) else 0
      val text=if(sp.totalFiles>0)"${sp.doneFiles} von ${sp.totalFiles} Dateien · ${formatBytes(sp.doneBytes)} / ${formatBytes(sp.totalBytes)}" else sp.message
      setForeground(foreground(text,percent))
     }
    }
   }finally{if(startedVpn)VpnManager.disconnect()}
   notifyDone("GalerieSync","Synchronisation erfolgreich abgeschlossen");Result.success()
  }.getOrElse{notifyDone("GalerieSync","Synchronisation fehlgeschlagen: "+(it.message?:"Unbekannter Fehler"));Result.retry()}
 }
 private fun formatBytes(v:Long):String=when{v>=1073741824L->"%.1f GB".format(v/1073741824.0);v>=1048576L->"%.1f MB".format(v/1048576.0);v>=1024L->"%.1f KB".format(v/1024.0);else->"$v B"}
}