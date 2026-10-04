package de.galeriesync.gallery

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import de.galeriesync.data.NasSettings
import de.galeriesync.sync.SmbProvider
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
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
        private const val PREVIEW_QUALITY=78
        private const val PREFS="gallery_settings"
        private const val KEY_SOURCE="source_id"
        private const val KEY_FOLDER="folder"
        private val imageExtensions=setOf("jpg","jpeg","png","webp","heic","heif","avif","gif","bmp")
        private val videoExtensions=setOf("mp4","mov","mkv","webm","m4v","3gp")
    }

    private val cacheRoot=File(context.filesDir,"nas_gallery").apply{mkdirs()}
    private val previewRoot=File(cacheRoot,"previews").apply{mkdirs()}
    private val indexFile=File(cacheRoot,"index.json")
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
            pruneObsoletePreviews(found)
            GalleryIndexResult(found,setOf(profile.id),emptyList())
        }.getOrElse{e->
            GalleryIndexResult(loadIndex(),emptySet(),listOf("$title: "+(e.message?:e.javaClass.simpleName)))
        }
    }

    fun previewFile(item:GalleryMedia):File=File(previewRoot,previewName(item))

    fun previewProgress(items:List<GalleryMedia>):Pair<Int,Int>{
        val images=items.filterNot{it.video}
        return images.count{previewFile(it).exists()} to images.size
    }

    suspend fun cacheMissingPreviews(
        profiles:List<NasSettings>,
        items:List<GalleryMedia>,
        allowedSources:Set<String>,
        onProgress:suspend (done:Int,total:Int,name:String)->Unit
    ):GalleryCacheResult{
        val profileById=profiles.associateBy{it.id}
        val providers=mutableMapOf<String,SmbProvider>()
        val images=items.filter{!it.video&&it.sourceId in allowedSources}
        val total=images.size
        var done=images.count{previewFile(it).exists()}
        var failed=0
        onProgress(done,total,"")
        images.filterNot{previewFile(it).exists()}.forEach{item->
            val p=profileById[item.sourceId]
            val ok=if(p==null)false else runCatching{
                val smb=providers.getOrPut(item.sourceId){SmbProvider(p)}
                createPreview(smb,item.path,previewFile(item))
            }.getOrDefault(false)
            if(ok)done++ else failed++
            onProgress(done,total,item.name)
        }
        return GalleryCacheResult(done,total,failed)
    }

    private fun createPreview(smb:SmbProvider,path:String,out:File):Boolean{
        if(out.exists())return true
        val orientation=runCatching{
            smb.openInput(path).use{input->ExifInterface(input).getAttributeInt(ExifInterface.TAG_ORIENTATION,ExifInterface.ORIENTATION_NORMAL)}
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        val bounds=BitmapFactory.Options().apply{inJustDecodeBounds=true}
        smb.openInput(path).use{BitmapFactory.decodeStream(it,null,bounds)}
        if(bounds.outWidth<=0||bounds.outHeight<=0)return false
        var sample=1
        while(bounds.outWidth/sample>1800||bounds.outHeight/sample>1800)sample*=2
        val decoded=smb.openInput(path).use{BitmapFactory.decodeStream(it,null,BitmapFactory.Options().apply{inSampleSize=sample})}?:return false
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
        val rotated=if(!matrix.isIdentity)Bitmap.createBitmap(decoded,0,0,decoded.width,decoded.height,matrix,true) else decoded
        val maxDim=max(rotated.width,rotated.height).coerceAtLeast(1)
        val factor=if(maxDim>PREVIEW_MAX)PREVIEW_MAX.toFloat()/maxDim else 0.85f
        val targetW=(rotated.width*factor).roundToInt().coerceAtLeast(1)
        val targetH=(rotated.height*factor).roundToInt().coerceAtLeast(1)
        val preview=Bitmap.createScaledBitmap(rotated,targetW,targetH,true)
        val tmp=File(previewRoot,out.name+".tmp")
        val written=FileOutputStream(tmp).use{preview.compress(Bitmap.CompressFormat.JPEG,PREVIEW_QUALITY,it)}
        if(written){
            if(out.exists())out.delete()
            if(!tmp.renameTo(out)){tmp.copyTo(out,true);tmp.delete()}
        }else tmp.delete()
        if(preview!==rotated)preview.recycle()
        if(rotated!==decoded)rotated.recycle()
        decoded.recycle()
        return written&&out.exists()
    }

    private fun previewName(item:GalleryMedia):String{
        val raw=item.sourceId+"|"+item.path+"|"+item.size
        val digest=MessageDigest.getInstance("SHA-256").digest(raw.toByteArray())
        return digest.joinToString(""){"%02x".format(it)}+".jpg"
    }

    private fun pruneObsoletePreviews(items:List<GalleryMedia>){
        val expected=items.asSequence().filterNot{it.video}.map{previewName(it)}.toHashSet()
        previewRoot.listFiles()?.forEach{f->if(f.isFile&&f.extension.equals("jpg",true)&&f.name !in expected)f.delete()}
    }
}
