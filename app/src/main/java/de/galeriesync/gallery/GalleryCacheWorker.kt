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
        const val KEY_OPEN="open"
    }

    override suspend fun doWork():Result=withContext(Dispatchers.IO){
        val profiles=SettingsStore(applicationContext).loadProfiles()
        val repo=GalleryRepository(applicationContext)
        val selection=repo.loadSelection()
            ?:return@withContext Result.success(workDataOf(KEY_MESSAGE to "Bitte zuerst einen Galerie-Ordner auswählen"))
        if(profiles.none{it.id==selection.sourceId})
            return@withContext Result.success(workDataOf(KEY_MESSAGE to "Die ausgewählte NAS-Quelle ist nicht mehr vorhanden"))
        return@withContext runCatching{
            setProgress(workDataOf(KEY_MESSAGE to "NAS-Ordner wird eingelesen …"))
            val scan=repo.refreshIndex(profiles)
            if(scan.refreshedSources.isEmpty()){
                val (done,total)=repo.previewProgress(scan.items)
                return@runCatching Result.success(workDataOf(
                    KEY_MESSAGE to ("Offline-Galerie bleibt verfügbar · "+scan.errors.joinToString(" · ")),
                    KEY_DONE to done,KEY_TOTAL to total,KEY_OPEN to (total-done).coerceAtLeast(0)
                ))
            }
            val cache=repo.cacheMissingPreviews(profiles,scan.items,setOf(selection.sourceId)){done,total,name->
                val open=(total-done).coerceAtLeast(0)
                val detail=if(name.isBlank())"" else " · $name"
                setProgress(workDataOf(
                    KEY_MESSAGE to "Offline-Vorschauen: $done fertig · $open offen · $total gesamt$detail",
                    KEY_DONE to done,KEY_TOTAL to total,KEY_OPEN to open
                ))
            }
            val suffix=buildList{
                if(scan.errors.isNotEmpty())add("${scan.errors.size} NAS-Bereich(e) nicht erreichbar")
                if(cache.failed>0)add("${cache.failed} Vorschau(en) konnten nicht erstellt werden")
            }
            val message=if(suffix.isEmpty())"Galerie und Offline-Vorschauen sind aktuell" else "Galerie aktualisiert · "+suffix.joinToString(" · ")
            Result.success(workDataOf(
                KEY_MESSAGE to message,
                KEY_DONE to cache.done,
                KEY_TOTAL to cache.total,
                KEY_OPEN to (cache.total-cache.done).coerceAtLeast(0)
            ))
        }.getOrElse{e->
            val items=repo.loadIndex()
            val (done,total)=repo.previewProgress(items)
            Result.success(workDataOf(
                KEY_MESSAGE to ("Offline-Galerie bleibt verfügbar · "+(e.message?:"NAS nicht erreichbar")),
                KEY_DONE to done,KEY_TOTAL to total,KEY_OPEN to (total-done).coerceAtLeast(0)
            ))
        }
    }
}
