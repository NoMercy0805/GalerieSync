package de.galeriesync.gallery

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.work.*
import coil.compose.AsyncImage
import de.galeriesync.data.NasSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class GallerySource(val id:String,val title:String,val count:Int)
private data class GalleryFolder(val name:String,val path:String,val count:Int)

@Composable
fun NasGalleryScreen(profiles:List<NasSettings>){
    val context=LocalContext.current
    val repo=remember{GalleryRepository(context)}
    val scope=rememberCoroutineScope()
    var media by remember{mutableStateOf(emptyList<GalleryMedia>())}
    var sourceId by remember{mutableStateOf<String?>(null)}
    var folder by remember{mutableStateOf("")}
    var selected by remember{mutableStateOf<GalleryMedia?>(null)}
    var status by remember{mutableStateOf("")}
    var working by remember{mutableStateOf(false)}

    fun startRefresh(){
        val request=OneTimeWorkRequestBuilder<GalleryCacheWorker>().build()
        WorkManager.getInstance(context).enqueueUniqueWork(GalleryCacheWorker.UNIQUE_WORK,ExistingWorkPolicy.REPLACE,request)
        working=true
        status="NAS-Ordner werden eingelesen …"
        scope.launch{
            WorkManager.getInstance(context).getWorkInfoByIdFlow(request.id).filterNotNull().takeWhile{info->
                info.progress.getString(GalleryCacheWorker.KEY_MESSAGE)?.let{status=it}
                if(info.state.isFinished)status=info.outputData.getString(GalleryCacheWorker.KEY_MESSAGE)?:status
                !info.state.isFinished
            }.collect{}
            media=withContext(Dispatchers.IO){repo.loadIndex()}
            working=false
        }
    }

    LaunchedEffect(Unit){
        media=withContext(Dispatchers.IO){repo.loadIndex()}
        if(media.isEmpty()&&profiles.isNotEmpty())startRefresh()
    }

    val grouped=media.groupBy{it.sourceId}
    val sources=remember(media,profiles){
        val map=linkedMapOf<String,GallerySource>()
        profiles.forEach{p->
            val title=p.profileName.ifBlank{p.name.ifBlank{p.remoteFolder.ifBlank{p.host}}}
            map[p.id]=GallerySource(p.id,title,0)
        }
        media.groupBy{it.sourceId}.forEach{(id,list)->
            map[id]=GallerySource(id,list.firstOrNull()?.sourceTitle?:map[id]?.title?:"NAS",list.size)
        }
        map.values.sortedBy{it.title.lowercase()}
    }
    val activeSource=sourceId?.let{id->sources.firstOrNull{it.id==id}}
    val sourceMedia=sourceId?.let{grouped[it].orEmpty()}.orEmpty()
    val contents=remember(sourceMedia,folder){folderContents(sourceMedia,folder)}

    BackHandler(enabled=sourceId!=null){
        if(folder.isNotBlank())folder=folder.substringBeforeLast('/',"") else sourceId=null
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)){
        Row(Modifier.fillMaxWidth().padding(horizontal=18.dp,vertical=10.dp),verticalAlignment=Alignment.CenterVertically){
            Column(Modifier.weight(1f)){
                Text("Galerie",fontSize=25.sp,fontWeight=FontWeight.Bold,color=MaterialTheme.colorScheme.onBackground)
                Text("Verkleinerte Offline-Vorschauen · ohne Speicherlimit",fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick={startRefresh()},enabled=!working&&profiles.isNotEmpty()){Text(if(working)"Läuft …" else "Aktualisieren")}
        }
        if(working)LinearProgressIndicator(Modifier.fillMaxWidth())
        if(status.isNotBlank())Text(status,Modifier.padding(horizontal=18.dp,vertical=6.dp),fontSize=11.sp,color=MaterialTheme.colorScheme.onSurfaceVariant,maxLines=2,overflow=TextOverflow.Ellipsis)

        if(sourceId==null){
            if(sources.isEmpty()){
                Box(Modifier.fillMaxSize().padding(24.dp),contentAlignment=Alignment.TopCenter){
                    Card{Column(Modifier.padding(18.dp)){Text("Noch keine Galerie-Quelle",fontWeight=FontWeight.Bold);Text("Lege zuerst eine Synchronisation mit einem NAS-Zielordner an. Danach kann die Galerie dessen Ordnerstruktur einlesen.",color=MaterialTheme.colorScheme.onSurfaceVariant,fontSize=13.sp)}}
                }
            }else{
                LazyVerticalGrid(columns=GridCells.Adaptive(150.dp),modifier=Modifier.fillMaxSize().padding(8.dp),horizontalArrangement=Arrangement.spacedBy(8.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
                    items(sources,key={it.id}){source->SourceTile(source){sourceId=source.id;folder=""}}
                }
            }
        }else{
            Row(Modifier.fillMaxWidth().padding(horizontal=8.dp,vertical=4.dp),verticalAlignment=Alignment.CenterVertically){
                TextButton(onClick={if(folder.isNotBlank())folder=folder.substringBeforeLast('/',"") else sourceId=null}){Text("‹",fontSize=28.sp)}
                Column(Modifier.weight(1f)){
                    Text(activeSource?.title?:"NAS",fontWeight=FontWeight.Bold,maxLines=1,overflow=TextOverflow.Ellipsis)
                    Text(if(folder.isBlank())"/" else "/$folder",fontSize=11.sp,color=MaterialTheme.colorScheme.onSurfaceVariant,maxLines=1,overflow=TextOverflow.Ellipsis)
                }
            }
            if(contents.first.isEmpty()&&contents.second.isEmpty()){
                Box(Modifier.fillMaxSize().padding(24.dp),contentAlignment=Alignment.TopCenter){Text("In diesem Ordner wurden noch keine Bilder oder Videos gefunden.",color=MaterialTheme.colorScheme.onSurfaceVariant)}
            }else{
                LazyVerticalGrid(columns=GridCells.Adaptive(110.dp),modifier=Modifier.fillMaxSize().padding(5.dp),horizontalArrangement=Arrangement.spacedBy(5.dp),verticalArrangement=Arrangement.spacedBy(5.dp)){
                    items(contents.first,key={"folder:"+it.path}){f->FolderTile(f){folder=f.path}}
                    items(contents.second,key={it.sourceId+"|"+it.path}){item->MediaTile(item,repo){selected=item}}
                }
            }
        }
    }

    selected?.let{item->MediaDialog(item,repo){selected=null}}
}

@Composable
private fun SourceTile(source:GallerySource,onClick:()->Unit){
    Card(Modifier.fillMaxWidth().height(126.dp).clickable{onClick()},shape=RoundedCornerShape(16.dp)){
        Column(Modifier.fillMaxSize().padding(14.dp),verticalArrangement=Arrangement.Center){
            Text("📁",fontSize=32.sp)
            Spacer(Modifier.height(6.dp))
            Text(source.title,fontWeight=FontWeight.Bold,maxLines=2,overflow=TextOverflow.Ellipsis)
            Text("${source.count} Medien",fontSize=11.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun FolderTile(folder:GalleryFolder,onClick:()->Unit){
    Card(Modifier.aspectRatio(1f).clickable{onClick()},shape=RoundedCornerShape(12.dp)){
        Column(Modifier.fillMaxSize().padding(10.dp),verticalArrangement=Arrangement.Center,horizontalAlignment=Alignment.CenterHorizontally){
            Text("📁",fontSize=34.sp)
            Spacer(Modifier.height(5.dp))
            Text(folder.name,fontSize=12.sp,fontWeight=FontWeight.SemiBold,maxLines=2,overflow=TextOverflow.Ellipsis)
            Text("${folder.count}",fontSize=10.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun MediaTile(item:GalleryMedia,repo:GalleryRepository,onClick:()->Unit){
    val preview=repo.previewFile(item)
    Card(Modifier.aspectRatio(1f).clickable{onClick()},shape=RoundedCornerShape(10.dp)){
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant),contentAlignment=Alignment.Center){
            when{
                item.video->Text("▶",fontSize=34.sp,color=MaterialTheme.colorScheme.primary)
                preview.exists()->AsyncImage(model=preview,contentDescription=item.name,modifier=Modifier.fillMaxSize(),contentScale=ContentScale.Crop)
                else->Column(horizontalAlignment=Alignment.CenterHorizontally){Text("▧",fontSize=30.sp,color=MaterialTheme.colorScheme.onSurfaceVariant);Text("Vorschau",fontSize=9.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)}
            }
            Surface(Modifier.align(Alignment.BottomCenter).fillMaxWidth(),color=Color.Black.copy(alpha=0.45f)){
                Text(item.name,Modifier.padding(5.dp),fontSize=9.sp,color=Color.White,maxLines=1,overflow=TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun MediaDialog(item:GalleryMedia,repo:GalleryRepository,onClose:()->Unit){
    val preview=repo.previewFile(item)
    Dialog(onDismissRequest=onClose){
        Surface(shape=RoundedCornerShape(18.dp),color=MaterialTheme.colorScheme.surface){
            Column(Modifier.fillMaxWidth().padding(12.dp)){
                when{
                    item.video->Box(Modifier.fillMaxWidth().height(280.dp).background(MaterialTheme.colorScheme.surfaceVariant),contentAlignment=Alignment.Center){Column(horizontalAlignment=Alignment.CenterHorizontally){Text("▶",fontSize=50.sp,color=MaterialTheme.colorScheme.primary);Text("Video",fontWeight=FontWeight.Bold);Text("Das Originalvideo wird nicht geladen.",fontSize=11.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)}}
                    preview.exists()->AsyncImage(model=preview,contentDescription=item.name,modifier=Modifier.fillMaxWidth().heightIn(max=600.dp),contentScale=ContentScale.Fit)
                    else->Box(Modifier.fillMaxWidth().height(280.dp),contentAlignment=Alignment.Center){Column(horizontalAlignment=Alignment.CenterHorizontally){CircularProgressIndicator();Spacer(Modifier.height(12.dp));Text("Offline-Vorschau wird noch erstellt",fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)}}
                }
                Spacer(Modifier.height(10.dp))
                Text(item.name,fontWeight=FontWeight.Bold,maxLines=2,overflow=TextOverflow.Ellipsis)
                Text(item.relativePath(),fontSize=10.sp,color=MaterialTheme.colorScheme.onSurfaceVariant,maxLines=2,overflow=TextOverflow.Ellipsis)
                if(!item.video)Text("Angezeigt wird immer nur die verkleinerte lokale Vorschau – nie das Originalbild.",fontSize=10.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick=onClose,modifier=Modifier.align(Alignment.End)){Text("Schließen")}
            }
        }
    }
}

private fun folderContents(items:List<GalleryMedia>,current:String):Pair<List<GalleryFolder>,List<GalleryMedia>>{
    val cleanCurrent=current.trim('/')
    val prefix=if(cleanCurrent.isBlank())"" else "$cleanCurrent/"
    val folderCounts=linkedMapOf<String,Int>()
    val direct=mutableListOf<GalleryMedia>()
    items.forEach{item->
        val relative=item.relativePath().trim('/')
        if(prefix.isNotEmpty()&&!relative.startsWith(prefix))return@forEach
        val rest=if(prefix.isEmpty())relative else relative.removePrefix(prefix)
        if(rest.isBlank())return@forEach
        if('/' in rest){
            val child=rest.substringBefore('/')
            folderCounts[child]=(folderCounts[child]?:0)+1
        }else direct+=item
    }
    val folders=folderCounts.map{(name,count)->GalleryFolder(name,if(cleanCurrent.isBlank())name else "$cleanCurrent/$name",count)}.sortedBy{it.name.lowercase()}
    return folders to direct.sortedBy{it.name.lowercase()}
}
