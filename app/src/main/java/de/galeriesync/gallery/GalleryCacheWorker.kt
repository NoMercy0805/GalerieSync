package de.galeriesync.gallery

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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class GalleryCacheWorker(context:Context,params:WorkerParameters):CoroutineWorker(context,params){
    companion object{
        const val UNIQUE_WORK="GalerieSync-gallery-cache"
        const val KEY_MESSAGE="message"
        const val KEY_DONE="done"
        const val KEY_TOTAL="total"
        const val KEY_OPEN="open"
        private const val NOTIFICATION_RUNNING=4201
        private const val NOTIFICATION_DONE=4202
        private const val CHANNEL="galeriesync_gallery"
    }

    private val nm=applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private fun ensureChannel(){
        if(Build.VERSION.SDK_INT>=26){
            nm.createNotificationChannel(NotificationChannel(CHANNEL,"Galerie-Offline-Medien",NotificationManager.IMPORTANCE_LOW))
        }
    }

    private fun notification(title:String,text:String,percent:Int?=null,ongoing:Boolean=true)=
        NotificationCompat.Builder(applicationContext,CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setOngoing(ongoing)
            .apply{
                if(ongoing){
                    if(percent==null)setProgress(0,0,true)
                    else setProgress(100,percent.coerceIn(0,100),false)
                }
            }
            .build()

    private fun foreground(text:String,percent:Int?=null):ForegroundInfo{
        ensureChannel()
        val n=notification("GalerieSync bereitet Offline-Medien vor",text,percent,true)
        return if(Build.VERSION.SDK_INT>=29){
            ForegroundInfo(NOTIFICATION_RUNNING,n,android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        }else ForegroundInfo(NOTIFICATION_RUNNING,n)
    }

    private fun updateForegroundNotification(text:String,percent:Int?=null){
        ensureChannel()
        nm.notify(NOTIFICATION_RUNNING,notification("GalerieSync bereitet Offline-Medien vor",text,percent,true))
    }

    private fun notifyDone(title:String,text:String){
        if(SettingsStore(applicationContext).loadNotificationsEnabled()){
            ensureChannel()
            nm.notify(NOTIFICATION_DONE,notification(title,text,null,false))
        }
    }

    override suspend fun doWork():Result=withContext(Dispatchers.IO){
        val store=SettingsStore(applicationContext)
        val profiles=store.loadProfiles()
        val repo=GalleryRepository(applicationContext)
        val selection=repo.loadSelection()
            ?:return@withContext Result.success(workDataOf(KEY_MESSAGE to "Bitte zuerst einen Galerie-Ordner auswählen"))
        if(profiles.none{it.id==selection.sourceId})
            return@withContext Result.success(workDataOf(KEY_MESSAGE to "Die ausgewählte NAS-Quelle ist nicht mehr vorhanden"))

        setForeground(foreground("Galerie wird vorbereitet …"))
        var lastNotificationAt=0L

        try{
            val result=runCatching{
                val scanMessage="NAS-Ordner wird im Hintergrund eingelesen …"
                setProgress(workDataOf(KEY_MESSAGE to scanMessage))
                updateForegroundNotification(scanMessage)

                val scan=repo.refreshIndex(profiles)
                if(scan.refreshedSources.isEmpty()){
                    val (done,total)=repo.previewProgress(scan.items)
                    val message="Offline-Galerie bleibt verfügbar · "+scan.errors.joinToString(" · ")
                    notifyDone("Galerie nicht aktualisiert",message)
                    return@runCatching Result.success(workDataOf(
                        KEY_MESSAGE to message,
                        KEY_DONE to done,KEY_TOTAL to total,KEY_OPEN to (total-done).coerceAtLeast(0)
                    ))
                }

                val cache=repo.cacheMissingPreviews(profiles,scan.items,setOf(selection.sourceId)){done,total,name->
                    val open=(total-done).coerceAtLeast(0)
                    val detail=if(name.isBlank())"" else " · $name"
                    val message="Offline-Medien: $done fertig · $open offen · $total gesamt$detail"
                    val percent=if(total>0)(done*100/total).coerceIn(0,100) else 0
                    setProgress(workDataOf(
                        KEY_MESSAGE to message,
                        KEY_DONE to done,KEY_TOTAL to total,KEY_OPEN to open
                    ))
                    val now=System.currentTimeMillis()
                    if(now-lastNotificationAt>=1000L||done==total){
                        lastNotificationAt=now
                        updateForegroundNotification(message,percent)
                    }
                }

                val suffix=buildList{
                    if(scan.errors.isNotEmpty())add("${scan.errors.size} NAS-Bereich(e) nicht erreichbar")
                    if(cache.failed>0)add("${cache.failed} Medium/Medien konnten nicht vorbereitet werden")
                }
                val message=if(suffix.isEmpty())"Galerie und Offline-Medien sind aktuell" else "Galerie aktualisiert · "+suffix.joinToString(" · ")
                val summary="${cache.done} von ${cache.total} Medien offline vorbereitet"+if(cache.failed>0)" · ${cache.failed} Fehler" else ""
                notifyDone("Galerie bereit",summary)
                Result.success(workDataOf(
                    KEY_MESSAGE to message,
                    KEY_DONE to cache.done,
                    KEY_TOTAL to cache.total,
                    KEY_OPEN to (cache.total-cache.done).coerceAtLeast(0)
                ))
            }.getOrElse{e->
                val items=repo.loadIndex()
                val (done,total)=repo.previewProgress(items)
                val message="Offline-Galerie bleibt verfügbar · "+(e.message?:"NAS nicht erreichbar")
                notifyDone("Galerie nicht vollständig aktualisiert",message)
                Result.success(workDataOf(
                    KEY_MESSAGE to message,
                    KEY_DONE to done,KEY_TOTAL to total,KEY_OPEN to (total-done).coerceAtLeast(0)
                ))
            }
            result
        }finally{
            nm.cancel(NOTIFICATION_RUNNING)
        }
    }
}
