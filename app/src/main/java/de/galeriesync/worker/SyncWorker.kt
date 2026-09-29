package de.galeriesync.worker
import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import de.galeriesync.data.SettingsStore
import de.galeriesync.sync.SyncService
class SyncWorker(appContext:Context,params:WorkerParameters):CoroutineWorker(appContext,params){
 override suspend fun doWork():Result=runCatching{
  val profiles=SettingsStore(applicationContext).loadProfiles().filter{it.enabled}
  profiles.forEach{SyncService(applicationContext).run(it){_,_,_->}}
 }.fold({Result.success()},{Result.retry()})
}
