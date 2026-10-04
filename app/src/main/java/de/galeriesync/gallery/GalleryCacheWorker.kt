package de.galeriesync.gallery

import android.content.Context
import androidx.work.CoroutineWorker
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
    }

    override suspend fun doWork():Result=withContext(Dispatchers.IO){
        val profiles=SettingsStore(applicationContext).loadProfiles()
        val repo=GalleryRepository(applicationContext)
        if(profiles.isEmpty())return@withContext Result.success(workDataOf(KEY_MESSAGE to "Keine Synchronisationsprofile vorhanden"))
        return@withContext runCatching{
            setProgress(workDataOf(KEY_MESSAGE to "NAS-Ordner werden eingelesen …"))
            val scan=repo.refreshIndex(profiles)
            val failed=repo.cacheMissingPreviews(profiles,scan.items,scan.refreshedSources){done,total,name->
                setProgress(workDataOf(KEY_MESSAGE to "Offline-Vorschauen $done / $total · $name",KEY_DONE to done,KEY_TOTAL to total))
            }
            val suffix=buildList{
                if(scan.errors.isNotEmpty())add("${scan.errors.size} NAS-Bereich(e) nicht erreichbar")
                if(failed>0)add("$failed Vorschau(en) konnten nicht erstellt werden")
            }
            val message=if(suffix.isEmpty())"Galerie und Offline-Vorschauen sind aktuell" else "Galerie aktualisiert · "+suffix.joinToString(" · ")
            Result.success(workDataOf(KEY_MESSAGE to message,KEY_DONE to scan.items.count{!it.video&&repo.previewFile(it).exists()},KEY_TOTAL to scan.items.count{!it.video}))
        }.getOrElse{e->
            Result.success(workDataOf(KEY_MESSAGE to ("Offline-Galerie bleibt verfügbar · "+(e.message?:"NAS nicht erreichbar"))))
        }
    }
}
