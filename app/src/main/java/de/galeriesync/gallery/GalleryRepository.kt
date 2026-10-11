package de.galeriesync.gallery

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.media.MediaMetadataRetriever
import de.galeriesync.data.NasSettings
import de.galeriesync.sync.SmbProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.max
import kotlin.math.roundToInt

data class GallerySelection(val sourceId:String,val folder:String)

data class GalleryMedia(
    val sourceId:String,
    val sourceTitle:String,
    val remoteRoot:String,
    val path:String,
    val size:Long,
    val video:Boolean
){
    val name:String get()=path.replace('\\','/').substringAfterLast('/')
    fun relativePath():String{
        val root=remoteRoot.replace('\\','/').trim('/')
        val clean=path.replace('\\','/').trim('/')
        return when{
            root.isBlank()->clean
            clean==root->name
            clean.startsWith("$root/")->clean.removePrefix("$root/")
            else->clean
        }
    }
}

data class GalleryIndexResult(
    val items:List<GalleryMedia>,
    val refreshedSources:Set<String>,
    val errors:List<String>
)

data class GalleryCacheResult(val done:Int,val total:Int,val failed:Int)

class GalleryRepository(private val context:Context){
    companion object{
        private val lock=Any()
        private const val PREVIEW_MAX=1280
        private const val VIDEO_THUMB_MAX=640
        private const val PREVIEW_QUALITY=78
        private const val PREVIEW_PARALLELISM=3
        private const val PREFS="gallery_settings"
        private const val KEY_SOURCE="source_id"
        private const val KEY_FOLDER="folder"
        private val imageExtensions=setOf("jpg","jpeg","png","webp","heic","heif","avif","gif","bmp")
        private val videoExtensions=setOf("mp4","mov","mkv","webm","m4v","3gp")
    }

    private val cacheRoot=File(context.filesDir,"nas_gallery").apply{mkdirs()}
    private val previewRoot=File(cacheRoot,"previews").apply{mkdirs()}
    private val videoRoot=File(cacheRoot,"videos").apply{mkdirs()}
    private val tempRoot=File(context.cacheDir,"galeriesync_gallery").apply{mkdirs()}
    private val indexFile=File(cacheRoot,"index.json")
    private val folderIndexFile=File(cacheRoot,"folder_index.json")
    private val prefs=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE)

    fun loadSelection():GallerySelection?{
        val source=prefs.getString(KEY_SOURCE,"").orEmpty()
        if(source.isBlank())return null
        return GallerySelection(source,prefs.getString(KEY_FOLDER,"").orEmpty().trim('/'))
    }

    fun saveSelection(selection:GallerySelection){
        prefs.edit().putString(KEY_SOURCE,selection.sourceId).putString(KEY_FOLDER,selection.folder.trim('/')).apply()
    }

    private fun loadRawIndex():List<GalleryMedia> = synchronized(lock){
        if(!indexFile.exists())return@synchronized emptyList()
        runCatching{
            val a=JSONArray(indexFile.readText())
            (0 until a.length()).mapNotNull{i->
                val o=a.optJSONObject(i)?:return@mapNotNull null
                GalleryMedia(
                    sourceId=o.optString("sourceId"),
                    sourceTitle=o.optString("sourceTitle"),
                    remoteRoot=o.optString("remoteRoot"),
                    path=o.optString("path"),
                    size=o.optLong("size",0L),
                    video=o.optBoolean("video",false)
                ).takeIf{it.sourceId.isNotBlank()&&it.path.isNotBlank()}
            }
        }.getOrDefault(emptyList())
    }

    fun loadIndex():List<GalleryMedia>{
        val selection=loadSelection()?:return emptyList()
        val root=selection.folder.trim('/')
        return loadRawIndex().filter{it.sourceId==selection.sourceId&&it.remoteRoot.trim('/')==root}
    }

    private fun saveIndex(items:List<GalleryMedia>)=synchronized(lock){
        val a=JSONArray()
        items.forEach{m->
            a.put(JSONObject().apply{
                put("sourceId",m.sourceId)
                put("sourceTitle",m.sourceTitle)
                put("remoteRoot",m.remoteRoot)
                put("path",m.path)
                put("size",m.size)
                put("video",m.video)
            })
        }
        val tmp=File(cacheRoot,"index.json.tmp")
        tmp.writeText(a.toString())
        if(indexFile.exists())indexFile.delete()
        if(!tmp.renameTo(indexFile)){tmp.copyTo(indexFile,true);tmp.delete()}
    }

    private fun saveFolderPreviewIndex(items:List<GalleryMedia>)=synchronized(lock){
        val folders=linkedMapOf<String,MutableList<GalleryMedia>>()
        items.asSequence()
            .sortedWith(compareBy<GalleryMedia>({it.video},{it.path.lowercase()}))
            .forEach{item->
                val relative=item.relativePath().trim('/')
                val directory=relative.substringBeforeLast('/',"")
                if(directory.isBlank())return@forEach
                var current=""
                directory.split('/').filter{it.isNotBlank()}.forEach{part->
                    current=if(current.isBlank())part else "$current/$part"
                    val previews=folders.getOrPut(current){mutableListOf()}
                    if(previews.size<4)previews+=item
                }
            }
        val root=JSONObject()
        folders.forEach{(path,previews)->
            root.put(path,JSONArray().apply{
                previews.forEach{m->put(JSONObject().apply{
                    put("sourceId",m.sourceId)
                    put("sourceTitle",m.sourceTitle)
                    put("remoteRoot",m.remoteRoot)
                    put("path",m.path)
                    put("size",m.size)
                    put("video",m.video)
                })}
            })
        }
        val tmp=File(cacheRoot,"folder_index.json.tmp")
        tmp.writeText(root.toString())
        if(folderIndexFile.exists())folderIndexFile.delete()
        if(!tmp.renameTo(folderIndexFile)){tmp.copyTo(folderIndexFile,true);tmp.delete()}
    }

    private fun loadFolderPreviewIndex():Map<String,List<GalleryMedia>> = synchronized(lock){
        if(!folderIndexFile.exists())return@synchronized emptyMap()
        runCatching{
            val root=JSONObject(folderIndexFile.readText())
            buildMap{
                val keys=root.keys()
                while(keys.hasNext()){
                    val folder=keys.next()
                    val array=root.optJSONArray(folder)?:continue
                    val previews=(0 until array.length()).mapNotNull{i->
                        val o=array.optJSONObject(i)?:return@mapNotNull null
                        GalleryMedia(
                            sourceId=o.optString("sourceId"),
                            sourceTitle=o.optString("sourceTitle"),
                            remoteRoot=o.optString("remoteRoot"),
                            path=o.optString("path"),
                            size=o.optLong("size",0L),
                            video=o.optBoolean("video",false)
                        ).takeIf{it.sourceId.isNotBlank()&&it.path.isNotBlank()}
                    }
                    if(previews.isNotEmpty())put(folder,previews)
                }
            }
        }.getOrDefault(emptyMap())
    }

    fun loadFolderPreviews(currentFolder:String):Map<String,List<GalleryMedia>>{
        val clean=currentFolder.trim('/')
        return loadFolderPreviewIndex().filterKeys{path->path.substringBeforeLast('/',"")==clean}
    }

    private fun folderPreviewKeys():Set<String> = loadFolderPreviewIndex().values
        .asSequence().flatten().map{it.sourceId+"|"+it.path}.toSet()

    fun refreshIndex(profiles:List<NasSettings>):GalleryIndexResult{
        val selection=loadSelection()
            ?:return GalleryIndexResult(emptyList(),emptySet(),listOf("Kein Galerie-Ordner ausgewählt"))
        val profile=profiles.firstOrNull{it.id==selection.sourceId}
            ?:return GalleryIndexResult(loadIndex(),emptySet(),listOf("Die ausgewählte Galerie-Quelle ist nicht mehr vorhanden"))
        val title=profile.profileName.ifBlank{profile.name.ifBlank{profile.share.ifBlank{profile.host}}}
        val root=selection.folder.trim('/')
        return runCatching{
            val smb=SmbProvider(profile)
            val found=smb.listRecursiveInfo(root).mapNotNull{f->
                val ext=f.path.substringAfterLast('.',"").lowercase()
                when{
                    ext in imageExtensions->GalleryMedia(profile.id,title,root,f.path,f.size,false)
                    ext in videoExtensions->GalleryMedia(profile.id,title,root,f.path,f.size,true)
                    else->null
                }
            }.distinctBy{it.sourceId+"|"+it.path}.sortedBy{it.path.lowercase()}
            saveIndex(found)
            saveFolderPreviewIndex(found)
            pruneObsoleteOfflineData(found)
            GalleryIndexResult(found,setOf(profile.id),emptyList())
        }.getOrElse{e->
            GalleryIndexResult(loadIndex(),emptySet(),listOf("$title: "+(e.message?:e.javaClass.simpleName)))
        }
    }

    fun previewFile(item:GalleryMedia):File=File(previewRoot,previewName(item))
    fun offlineVideoFile(item:GalleryMedia):File=File(videoRoot,videoName(item))

    fun previewProgress(items:List<GalleryMedia>):Pair<Int,Int>{
        return items.count{item->if(item.video)offlineVideoFile(item).exists() else previewFile(item).exists()} to items.size
    }

    fun clearOfflineData():Int{
        var deleted=0
        previewRoot.listFiles()?.forEach{if(it.isFile&&it.delete())deleted++}
        videoRoot.listFiles()?.forEach{if(it.isFile&&it.delete())deleted++}
        tempRoot.listFiles()?.forEach{if(it.isFile)it.delete()}
        return deleted
    }

    suspend fun cacheMissingPreviews(
        profiles:List<NasSettings>,
        items:List<GalleryMedia>,
        allowedSources:Set<String>,
        onProgress:suspend (done:Int,total:Int,name:String)->Unit
    ):GalleryCacheResult=coroutineScope{
        val providers=profiles.filter{it.id in allowedSources}.associate{it.id to SmbProvider(it)}
        val eligible=items.filter{it.sourceId in allowedSources}
        val coverKeys=folderPreviewKeys()
        val missingImages=eligible
            .filter{!it.video&&!previewFile(it).exists()}
            .sortedByDescending{(it.sourceId+"|"+it.path) in coverKeys}
        val missingVideos=eligible.filter{it.video&&!offlineVideoFile(it).exists()}
        val total=eligible.size
        val done=AtomicInteger(total-missingImages.size-missingVideos.size)
        val failed=AtomicInteger(0)
        val slots=Semaphore(PREVIEW_PARALLELISM)
        val progressMutex=Mutex()
        val reportScope=this

        suspend fun report(name:String){
            progressMutex.lock()
            try{onProgress(done.get(),total,name)}finally{progressMutex.unlock()}
        }

        eligible.filter{it.video&&offlineVideoFile(it).exists()&&!previewFile(it).exists()}.forEach{
            runCatching{createVideoThumbnail(offlineVideoFile(it),previewFile(it))}
        }

        report("")
        missingImages.map{item->
            async(Dispatchers.IO){
                slots.acquire()
                val ok=try{
                    val smb=providers[item.sourceId]
                    smb!=null&&runCatching{createPreview(smb,item.path,previewFile(item))}.getOrDefault(false)
                }finally{slots.release()}
                if(ok)done.incrementAndGet() else failed.incrementAndGet()
                report(item.name)
            }
        }.awaitAll()

        for(item in missingVideos){
            val smb=providers[item.sourceId]
            report("Video wird vorbereitet · ${item.name}")
            val ok=smb!=null&&runCatching{
                createOfflineVideo(smb,item,offlineVideoFile(item)){percent->
                    reportScope.launch{report("Video $percent % · ${item.name}")}
                }
            }.getOrDefault(false)
            if(ok)done.incrementAndGet() else failed.incrementAndGet()
            report(item.name)
        }
        GalleryCacheResult(done.get(),total,failed.get())
    }

    private fun createPreview(smb:SmbProvider,path:String,out:File):Boolean{
        if(out.exists())return true
        val source=File.createTempFile("gallery_",".source",tempRoot)
        val tmp=File(previewRoot,out.name+".tmp")
        var decoded:Bitmap?=null
        var rotated:Bitmap?=null
        var preview:Bitmap?=null
        try{
            smb.openInput(path).use{input->FileOutputStream(source).use{output->input.copyTo(output,256*1024)}}

            val orientation=runCatching{
                ExifInterface(source.absolutePath).getAttributeInt(ExifInterface.TAG_ORIENTATION,ExifInterface.ORIENTATION_NORMAL)
            }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

            val bounds=BitmapFactory.Options().apply{inJustDecodeBounds=true}
            BitmapFactory.decodeFile(source.absolutePath,bounds)
            if(bounds.outWidth<=0||bounds.outHeight<=0)return false

            var sample=1
            while(bounds.outWidth/sample>1800||bounds.outHeight/sample>1800)sample*=2
            decoded=BitmapFactory.decodeFile(source.absolutePath,BitmapFactory.Options().apply{inSampleSize=sample})?:return false

            val matrix=Matrix().apply{
                when(orientation){
                    ExifInterface.ORIENTATION_ROTATE_90->postRotate(90f)
                    ExifInterface.ORIENTATION_ROTATE_180->postRotate(180f)
                    ExifInterface.ORIENTATION_ROTATE_270->postRotate(270f)
                    ExifInterface.ORIENTATION_FLIP_HORIZONTAL->postScale(-1f,1f)
                    ExifInterface.ORIENTATION_FLIP_VERTICAL->postScale(1f,-1f)
                    ExifInterface.ORIENTATION_TRANSPOSE->{postRotate(90f);postScale(-1f,1f)}
                    ExifInterface.ORIENTATION_TRANSVERSE->{postRotate(270f);postScale(-1f,1f)}
                }
            }
            rotated=if(!matrix.isIdentity)Bitmap.createBitmap(decoded!!,0,0,decoded!!.width,decoded!!.height,matrix,true) else decoded
            val maxDim=max(rotated!!.width,rotated!!.height).coerceAtLeast(1)
            val factor=if(maxDim>PREVIEW_MAX)PREVIEW_MAX.toFloat()/maxDim else 0.85f
            val targetW=(rotated!!.width*factor).roundToInt().coerceAtLeast(1)
            val targetH=(rotated!!.height*factor).roundToInt().coerceAtLeast(1)
            preview=Bitmap.createScaledBitmap(rotated!!,targetW,targetH,true)

            val written=FileOutputStream(tmp).use{preview!!.compress(Bitmap.CompressFormat.JPEG,PREVIEW_QUALITY,it)}
            if(!written)return false
            if(out.exists())out.delete()
            if(!tmp.renameTo(out)){tmp.copyTo(out,true);tmp.delete()}
            return out.exists()
        }finally{
            if(preview!=null&&preview!==rotated&&!preview!!.isRecycled)preview!!.recycle()
            if(rotated!=null&&rotated!==decoded&&!rotated!!.isRecycled)rotated!!.recycle()
            if(decoded!=null&&!decoded!!.isRecycled)decoded!!.recycle()
            if(source.exists())source.delete()
            if(tmp.exists())tmp.delete()
        }
    }

    private suspend fun createOfflineVideo(smb:SmbProvider,item:GalleryMedia,out:File,onProgress:(Int)->Unit):Boolean{
        if(out.exists())return true
        val ext=item.path.substringAfterLast('.',"mp4").lowercase().takeIf{it.matches(Regex("[a-z0-9]{2,5}"))}?:"mp4"
        val source=File.createTempFile("gallery_video_",".$ext",tempRoot)
        val tmp=File(videoRoot,out.name+".tmp.mp4")
        try{
            smb.openInput(item.path).use{input->FileOutputStream(source).use{output->input.copyTo(output,512*1024)}}
            if(tmp.exists())tmp.delete()
            val encoded=GalleryVideoTranscoder(context).transcode(source,tmp,onProgress)
            if(!encoded||!tmp.exists()||tmp.length()==0L)return false
            if(out.exists())out.delete()
            if(!tmp.renameTo(out)){tmp.copyTo(out,true);tmp.delete()}
            if(out.exists())runCatching{createVideoThumbnail(out,previewFile(item))}
            return out.exists()&&out.length()>0L
        }finally{
            if(source.exists())source.delete()
            if(tmp.exists())tmp.delete()
        }
    }

    private fun createVideoThumbnail(video:File,out:File):Boolean{
        if(out.exists())return true
        val retriever=MediaMetadataRetriever()
        var frame:Bitmap?=null
        var rotated:Bitmap?=null
        var scaled:Bitmap?=null
        val tmp=File(previewRoot,out.name+".tmp")
        try{
            retriever.setDataSource(video.absolutePath)
            frame=retriever.getFrameAtTime(0L,MediaMetadataRetriever.OPTION_CLOSEST_SYNC)?:return false
            val rotation=retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toFloatOrNull()?:0f
            rotated=if(rotation!=0f)Bitmap.createBitmap(frame!!,0,0,frame!!.width,frame!!.height,Matrix().apply{postRotate(rotation)},true) else frame
            val maxDim=max(rotated!!.width,rotated!!.height).coerceAtLeast(1)
            val factor=(VIDEO_THUMB_MAX.toFloat()/maxDim).coerceAtMost(1f)
            scaled=if(factor<1f)Bitmap.createScaledBitmap(rotated!!,(rotated!!.width*factor).roundToInt().coerceAtLeast(1),(rotated!!.height*factor).roundToInt().coerceAtLeast(1),true) else rotated
            val written=FileOutputStream(tmp).use{scaled!!.compress(Bitmap.CompressFormat.JPEG,PREVIEW_QUALITY,it)}
            if(!written)return false
            if(out.exists())out.delete()
            if(!tmp.renameTo(out)){tmp.copyTo(out,true);tmp.delete()}
            return out.exists()
        }finally{
            retriever.release()
            if(scaled!=null&&scaled!==rotated&&!scaled!!.isRecycled)scaled!!.recycle()
            if(rotated!=null&&rotated!==frame&&!rotated!!.isRecycled)rotated!!.recycle()
            if(frame!=null&&!frame!!.isRecycled)frame!!.recycle()
            if(tmp.exists())tmp.delete()
        }
    }

    private fun mediaHash(item:GalleryMedia):String{
        val raw=item.sourceId+"|"+item.path+"|"+item.size
        val digest=MessageDigest.getInstance("SHA-256").digest(raw.toByteArray())
        return digest.joinToString(""){"%02x".format(it)}
    }

    private fun previewName(item:GalleryMedia):String=mediaHash(item)+".jpg"
    private fun videoName(item:GalleryMedia):String=mediaHash(item)+".mp4"

    private fun pruneObsoleteOfflineData(items:List<GalleryMedia>){
        val expectedPreviews=items.asSequence().map{previewName(it)}.toHashSet()
        val expectedVideos=items.asSequence().filter{it.video}.map{videoName(it)}.toHashSet()
        previewRoot.listFiles()?.forEach{f->if(f.isFile&&f.extension.equals("jpg",true)&&f.name !in expectedPreviews)f.delete()}
        videoRoot.listFiles()?.forEach{f->if(f.isFile&&f.extension.equals("mp4",true)&&f.name !in expectedVideos)f.delete()}
    }
}
