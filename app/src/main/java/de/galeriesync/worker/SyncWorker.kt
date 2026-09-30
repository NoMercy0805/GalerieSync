package de.galeriesync.worker
import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import de.galeriesync.data.SettingsStore
import de.galeriesync.sync.SyncService
import de.galeriesync.vpn.VpnManager
class SyncWorker(appContext:Context,params:WorkerParameters):CoroutineWorker(appContext,params){
 override suspend fun doWork():Result=runCatching{
  val profiles=SettingsStore(applicationContext).loadProfiles().filter{it.enabled}
  var startedVpn=false
  try{
   if(profiles.isNotEmpty())startedVpn=VpnManager.connectIfNeeded(applicationContext)
   profiles.forEach{p->if(startedVpn)VpnManager.waitForSmb(p.host);SyncService(applicationContext).run(p){_,_,_->}}
  }finally{if(startedVpn)VpnManager.disconnect()}
 }.fold({Result.success()},{Result.retry()})
}
