package de.galeriesync.gallery

import android.widget.MediaController
import android.widget.VideoView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.work.*
import coil.compose.AsyncImage
import de.galeriesync.data.NasSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private data class GalleryFolder(val name:String,val path:String,val count:Int)
private fun mediaKey(item:GalleryMedia)=item.sourceId+"|"+item.path

@Composable
fun NasGalleryScreen(profiles:List<NasSettings>){
    val context=LocalContext.current
    val repo=remember{GalleryRepository(context)}
    val scope=rememberCoroutineScope()
    val workManager=remember{WorkManager.getInstance(context)}
    val initialMedia=remember(repo){repo.loadIndex()}
    val initialProgress=remember(repo,initialMedia){repo.previewProgress(initialMedia)}
    var selection by remember{mutableStateOf(repo.loadSelection())}
    var media by remember{mutableStateOf(initialMedia)}
    var folder by rememberSaveable{mutableStateOf("")}
    var selectedKey by rememberSaveable{mutableStateOf<String?>(null)}
    var status by remember{mutableStateOf("")}
    var working by remember{mutableStateOf(false)}
    var progressDone by remember{mutableIntStateOf(initialProgress.first)}
    var progressTotal by remember{mutableIntStateOf(initialProgress.second)}
    var pickerOpen by remember{mutableStateOf(selection==null)}
    var deleteOfflineConfirm by remember{mutableStateOf(false)}
    var galleryMenuOpen by remember{mutableStateOf(false)}
    val selected=remember(media,selectedKey){selectedKey?.let{key->media.firstOrNull{mediaKey(it)==key}}}

    fun reloadLocal(){
        media=repo.loadIndex()
        val progress=repo.previewProgress(media)
        progressDone=progress.first
        progressTotal=progress.second
    }

    fun applyWorkerProgress(info:WorkInfo){
        info.progress.getString(GalleryCacheWorker.KEY_MESSAGE)?.let{status=it}
        val pTotal=info.progress.getInt(GalleryCacheWorker.KEY_TOTAL,0)
        if(pTotal>0){
            progressTotal=pTotal
            progressDone=info.progress.getInt(GalleryCacheWorker.KEY_DONE,progressDone)
        }
    }

    fun startRefresh(){
        if(repo.loadSelection()==null){
            pickerOpen=true
            return
        }
        val request=OneTimeWorkRequestBuilder<GalleryCacheWorker>().build()
        workManager.enqueueUniqueWork(GalleryCacheWorker.UNIQUE_WORK,ExistingWorkPolicy.REPLACE,request)
        working=true
        status="NAS-Ordner wird eingelesen …"
    }

    LaunchedEffect(selection){reloadLocal()}

    LaunchedEffect(Unit){
        var activeId:java.util.UUID?=null
        while(true){
            val infos=withContext(Dispatchers.IO){
                runCatching{workManager.getWorkInfosForUniqueWork(GalleryCacheWorker.UNIQUE_WORK).get()}.getOrDefault(emptyList())
            }
            val active=infos.firstOrNull{it.state==WorkInfo.State.RUNNING||it.state==WorkInfo.State.ENQUEUED||it.state==WorkInfo.State.BLOCKED}
            if(active!=null){
                val newlyAttached=activeId!=active.id
                activeId=active.id
                if(newlyAttached||media.isEmpty())reloadLocal()
                working=true
                applyWorkerProgress(active)
                if(status.isBlank())status="Galerie-Aktualisierung läuft im Hintergrund …"
            }else{
                val finishedId=activeId
                if(finishedId!=null){
                    val finished=infos.firstOrNull{it.id==finishedId}
                    finished?.outputData?.getString(GalleryCacheWorker.KEY_MESSAGE)?.let{status=it}
                    reloadLocal()
                }
                activeId=null
                working=false
            }
            delay(750)
        }
    }

    val contents=remember(media,folder){folderContents(media,folder)}
    val openCount=(progressTotal-progressDone).coerceAtLeast(0)

    BackHandler(enabled=folder.isNotBlank()){folder=folder.substringBeforeLast('/',"")}

    Column(Modifier.fillMaxSize()){
        Row(Modifier.fillMaxWidth().padding(start=6.dp,end=18.dp,top=0.dp,bottom=2.dp),verticalAlignment=Alignment.CenterVertically){
            if(selection!=null){
                Box{
                    IconButton(onClick={galleryMenuOpen=true},modifier=Modifier.size(40.dp)){Text("⋮",fontSize=25.sp,color=MaterialTheme.colorScheme.onSurface)}
                    DropdownMenu(expanded=galleryMenuOpen,onDismissRequest={galleryMenuOpen=false}){
                        DropdownMenuItem(text={Text(if(working)"Aktualisierung läuft" else "Aktualisieren")},onClick={galleryMenuOpen=false;startRefresh()},enabled=!working)
                        DropdownMenuItem(text={Text("Ordner ändern")},onClick={galleryMenuOpen=false;pickerOpen=true},enabled=profiles.isNotEmpty())
                        if(progressDone>0)DropdownMenuItem(text={Text("Offline-Daten löschen",color=MaterialTheme.colorScheme.error)},onClick={galleryMenuOpen=false;deleteOfflineConfirm=true},enabled=!working)
                    }
                }
            }
            Column(Modifier.weight(1f)){
                Text("Galerie",fontSize=25.sp,fontWeight=FontWeight.Bold,color=MaterialTheme.colorScheme.onBackground)
                Text("Verkleinerte Offline-Medien · ohne Speicherlimit",fontSize=11.sp,color=MaterialTheme.colorScheme.onSurfaceVariant,maxLines=1,overflow=TextOverflow.Ellipsis)
            }
            if(selection==null)TextButton(onClick={pickerOpen=true},enabled=profiles.isNotEmpty()){Text("Ordner wählen")}
        }

        if(working&&progressTotal==0)LinearProgressIndicator(Modifier.fillMaxWidth())
        if(progressTotal>0){
            LinearProgressIndicator(progress={progressDone.toFloat()/progressTotal.coerceAtLeast(1)},modifier=Modifier.fillMaxWidth().padding(top=2.dp))
            Text("$progressDone Medien fertig · $openCount offen · $progressTotal gesamt",Modifier.padding(horizontal=18.dp,vertical=5.dp),fontSize=12.sp,fontWeight=FontWeight.SemiBold,color=MaterialTheme.colorScheme.onSurface)
        }
        if(working){
            Text("Aktualisierung läuft im Hintergrund weiter",Modifier.padding(horizontal=18.dp,vertical=2.dp),fontSize=11.sp,fontWeight=FontWeight.SemiBold,color=MaterialTheme.colorScheme.primary)
        }
        if(status.isNotBlank())Text(status,Modifier.padding(horizontal=18.dp,vertical=4.dp),fontSize=11.sp,color=MaterialTheme.colorScheme.onSurfaceVariant,maxLines=2,overflow=TextOverflow.Ellipsis)

        when{
            selection==null->{
                Box(Modifier.fillMaxSize().padding(24.dp),contentAlignment=Alignment.TopCenter){
                    Card{Column(Modifier.padding(18.dp)){
                        Text("Galerie-Ordner auswählen",fontWeight=FontWeight.Bold)
                        Text("Wähle einmal den NAS-Ordner aus, dessen Bilder und Videos in der Galerie erscheinen sollen.",color=MaterialTheme.colorScheme.onSurfaceVariant,fontSize=13.sp)
                        Spacer(Modifier.height(12.dp))
                        Button(onClick={pickerOpen=true},enabled=profiles.isNotEmpty(),modifier=Modifier.fillMaxWidth()){Text("NAS-Ordner auswählen")}
                        if(profiles.isEmpty())Text("Lege zuerst eine Synchronisation mit einer NAS-Verbindung an.",color=MaterialTheme.colorScheme.onSurfaceVariant,fontSize=11.sp)
                    }}
                }
            }
            media.isEmpty()&&!working->{
                Box(Modifier.fillMaxSize().padding(24.dp),contentAlignment=Alignment.TopCenter){
                    Card{Column(Modifier.padding(18.dp)){
                        Text("Noch keine Offline-Medien",fontWeight=FontWeight.Bold)
                        Text("Tippe auf Aktualisieren. Die App liest nur den gewählten NAS-Ordner ein und erstellt daraus verkleinerte Bilder und komprimierte Offline-Videos.",color=MaterialTheme.colorScheme.onSurfaceVariant,fontSize=13.sp)
                        Spacer(Modifier.height(12.dp))
                        Button(onClick={startRefresh()},modifier=Modifier.fillMaxWidth()){Text("Jetzt einlesen")}
                    }}
                }
            }
            else->{
                if(folder.isNotBlank()){
                    Row(Modifier.fillMaxWidth().padding(horizontal=8.dp,vertical=0.dp),verticalAlignment=Alignment.CenterVertically){
                        TextButton(onClick={folder=folder.substringBeforeLast('/',"")}){Text("‹",fontSize=28.sp)}
                        Column(Modifier.weight(1f)){
                            Text(folder.substringAfterLast('/'),fontWeight=FontWeight.Bold,maxLines=1,overflow=TextOverflow.Ellipsis)
                            Text("/$folder",fontSize=11.sp,color=MaterialTheme.colorScheme.onSurfaceVariant,maxLines=1,overflow=TextOverflow.Ellipsis)
                        }
                    }
                }
                if(contents.first.isEmpty()&&contents.second.isEmpty()){
                    Box(Modifier.fillMaxSize().padding(24.dp),contentAlignment=Alignment.TopCenter){Text("In diesem Ordner wurden noch keine Bilder oder Videos gefunden.",color=MaterialTheme.colorScheme.onSurfaceVariant)}
                }else{
                    LazyVerticalGrid(columns=GridCells.Adaptive(110.dp),modifier=Modifier.fillMaxSize().padding(5.dp),horizontalArrangement=Arrangement.spacedBy(5.dp),verticalArrangement=Arrangement.spacedBy(5.dp)){
                        items(contents.first,key={"folder:"+it.path}){f->
                            val folderPrefix=f.path.trim('/')+"/"
                            val previews=media.asSequence()
                                .filter{m->m.relativePath().trim('/').startsWith(folderPrefix)&&repo.previewFile(m).exists()}
                                .take(4)
                                .toList()
                            FolderTile(f,previews,repo){folder=f.path}
                        }
                        items(contents.second,key={it.sourceId+"|"+it.path}){item->MediaTile(item,repo){selectedKey=mediaKey(item)}}
                    }
                }
            }
        }
    }

    if(pickerOpen){
        GalleryFolderPicker(
            profiles=profiles,
            initialSelection=selection,
            onClose={pickerOpen=false},
            onSelect={newSelection->
                repo.saveSelection(newSelection)
                selection=newSelection
                folder=""
                selectedKey=null
                status=""
                progressDone=0
                progressTotal=0
                media=emptyList()
                pickerOpen=false
                startRefresh()
            }
        )
    }

    if(deleteOfflineConfirm){
        AlertDialog(
            onDismissRequest={deleteOfflineConfirm=false},
            title={Text("Offline-Daten löschen?")},
            text={Text("Alle lokal gespeicherten Bildvorschauen und komprimierten Offline-Videos werden gelöscht. NAS-Dateien, Ordnerauswahl und Synchronisation bleiben unverändert.")},
            confirmButton={
                TextButton(onClick={
                    deleteOfflineConfirm=false
                    selectedKey=null
                    scope.launch{
                        val deleted=withContext(Dispatchers.IO){repo.clearOfflineData()}
                        reloadLocal()
                        status="$deleted Offline-Datei(en) gelöscht"
                    }
                }){Text("Löschen",color=MaterialTheme.colorScheme.error)}
            },
            dismissButton={TextButton(onClick={deleteOfflineConfirm=false}){Text("Abbrechen")}}
        )
    }

    selected?.let{item->MediaDialog(item,repo){selectedKey=null}}
}

@Composable
private fun GalleryFolderPicker(profiles:List<NasSettings>,initialSelection:GallerySelection?,onClose:()->Unit,onSelect:(GallerySelection)->Unit){
    if(profiles.isEmpty()){
        AlertDialog(onDismissRequest=onClose,title={Text("Kein NAS-Profil")},text={Text("Lege zuerst eine Synchronisation mit einer NAS-Verbindung an.")},confirmButton={TextButton(onClick=onClose){Text("Schließen")}})
        return
    }
    var profileId by remember{mutableStateOf(initialSelection?.sourceId?.takeIf{id->profiles.any{it.id==id}}?:profiles.first().id)}
    val active=profiles.firstOrNull{it.id==profileId}?:profiles.first()
    var path by remember(profileId){mutableStateOf(initialSelection?.takeIf{it.sourceId==profileId}?.folder?:active.remoteFolder.trim('/'))}
    var dirs by remember{mutableStateOf(emptyList<GalleryRemoteFolder>())}
    var loading by remember{mutableStateOf(false)}
    var error by remember{mutableStateOf("")}

    LaunchedEffect(profileId,path){
        loading=true
        error=""
        val result=runCatching{withContext(Dispatchers.IO){GalleryNasBrowser(active).listDirectories(path)}}
        dirs=result.getOrDefault(emptyList())
        error=result.exceptionOrNull()?.message.orEmpty()
        loading=false
    }

    Dialog(onDismissRequest=onClose){
        Surface(shape=RoundedCornerShape(18.dp),color=MaterialTheme.colorScheme.surface,shadowElevation=14.dp){
            Column(Modifier.fillMaxWidth().padding(16.dp)){
                Text("Galerie-Ordner auswählen",fontSize=20.sp,fontWeight=FontWeight.Bold)
                Text("Nur dieser Ordner und seine Unterordner werden in die Galerie aufgenommen.",fontSize=11.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))

                if(profiles.size>1){
                    Text("NAS / Profil",fontWeight=FontWeight.SemiBold)
                    profiles.forEach{p->
                        val activeRow=p.id==profileId
                        Row(Modifier.fillMaxWidth().clickable{profileId=p.id}.padding(vertical=5.dp),verticalAlignment=Alignment.CenterVertically){
                            RadioButton(selected=activeRow,onClick={profileId=p.id})
                            Spacer(Modifier.width(6.dp))
                            Column{
                                Text(p.profileName.ifBlank{p.name.ifBlank{p.host}},fontWeight=FontWeight.SemiBold)
                                Text(p.host+" · "+p.share,fontSize=10.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    HorizontalDivider()
                    Spacer(Modifier.height(8.dp))
                }

                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
                    TextButton(onClick={path=path.substringBeforeLast('/',"")},enabled=path.isNotBlank()){Text("‹",fontSize=28.sp)}
                    Column(Modifier.weight(1f)){
                        Text(if(path.isBlank())"/" else "/$path",fontWeight=FontWeight.Bold,maxLines=2,overflow=TextOverflow.Ellipsis)
                        Text(active.share.ifBlank{"SMB-Freigabe"},fontSize=10.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                Button(onClick={onSelect(GallerySelection(profileId,path))},modifier=Modifier.fillMaxWidth()){Text("Diesen Ordner verwenden")}
                Spacer(Modifier.height(8.dp))

                when{
                    loading->Box(Modifier.fillMaxWidth().height(150.dp),contentAlignment=Alignment.Center){CircularProgressIndicator()}
                    error.isNotBlank()->Column(Modifier.fillMaxWidth().padding(vertical=12.dp)){
                        Text("Ordner konnte nicht gelesen werden",fontWeight=FontWeight.Bold)
                        Text(error,fontSize=11.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                        if(path.isNotBlank())TextButton(onClick={path=path.substringBeforeLast('/',"")}){Text("Eine Ebene zurück")}
                    }
                    dirs.isEmpty()->Text("Keine weiteren Unterordner.",fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.padding(vertical=16.dp))
                    else->Column(Modifier.heightIn(max=330.dp).verticalScroll(rememberScrollState())){
                        dirs.forEach{dir->
                            Row(Modifier.fillMaxWidth().clickable{path=dir.path}.padding(vertical=11.dp,horizontal=4.dp),verticalAlignment=Alignment.CenterVertically){
                                Text("📁",fontSize=22.sp)
                                Spacer(Modifier.width(10.dp))
                                Text(dir.name,Modifier.weight(1f),fontWeight=FontWeight.SemiBold,maxLines=1,overflow=TextOverflow.Ellipsis)
                                Text("›",fontSize=24.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }

                TextButton(onClick=onClose,modifier=Modifier.align(Alignment.End)){Text("Abbrechen")}
            }
        }
    }
}

@Composable
private fun FolderTile(folder:GalleryFolder,previewItems:List<GalleryMedia>,repo:GalleryRepository,onClick:()->Unit){
    val previewFiles=previewItems.map{repo.previewFile(it)}.filter{it.exists()}.take(4)
    Card(
        Modifier.aspectRatio(0.82f).clickable{onClick()},
        shape=RoundedCornerShape(14.dp),
        colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface),
        elevation=CardDefaults.cardElevation(defaultElevation=0.dp)
    ){
        Column(Modifier.fillMaxSize()){
            Box(Modifier.fillMaxWidth().weight(1f).background(MaterialTheme.colorScheme.surfaceVariant)){
                when(previewFiles.size){
                    0->Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center){Text("📁",fontSize=34.sp)}
                    1->AsyncImage(model=previewFiles[0],contentDescription=folder.name,modifier=Modifier.fillMaxSize(),contentScale=ContentScale.Crop)
                    else->Column(Modifier.fillMaxSize(),verticalArrangement=Arrangement.spacedBy(2.dp)){
                        Row(Modifier.weight(1f).fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(2.dp)){
                            AsyncImage(model=previewFiles[0],contentDescription=null,modifier=Modifier.weight(1f).fillMaxHeight(),contentScale=ContentScale.Crop)
                            AsyncImage(model=previewFiles.getOrElse(1){previewFiles[0]},contentDescription=null,modifier=Modifier.weight(1f).fillMaxHeight(),contentScale=ContentScale.Crop)
                        }
                        Row(Modifier.weight(1f).fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(2.dp)){
                            AsyncImage(model=previewFiles.getOrElse(2){previewFiles[0]},contentDescription=null,modifier=Modifier.weight(1f).fillMaxHeight(),contentScale=ContentScale.Crop)
                            AsyncImage(model=previewFiles.getOrElse(3){previewFiles.getOrElse(1){previewFiles[0]}},contentDescription=null,modifier=Modifier.weight(1f).fillMaxHeight(),contentScale=ContentScale.Crop)
                        }
                    }
                }
            }
            Column(Modifier.fillMaxWidth().padding(horizontal=8.dp,vertical=7.dp)){
                Text(folder.name,fontSize=12.sp,fontWeight=FontWeight.Bold,maxLines=1,overflow=TextOverflow.Ellipsis,color=MaterialTheme.colorScheme.onSurface)
                Text("${folder.count}",fontSize=10.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun MediaTile(item:GalleryMedia,repo:GalleryRepository,onClick:()->Unit){
    val preview=repo.previewFile(item)
    val videoReady=!item.video||repo.offlineVideoFile(item).exists()
    Card(Modifier.aspectRatio(1f).clickable{onClick()},shape=RoundedCornerShape(14.dp)){
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant),contentAlignment=Alignment.Center){
            when{
                preview.exists()->AsyncImage(model=preview,contentDescription=item.name,modifier=Modifier.fillMaxSize(),contentScale=ContentScale.Crop)
                item.video->Column(horizontalAlignment=Alignment.CenterHorizontally){
                    Text("▶",fontSize=34.sp,color=MaterialTheme.colorScheme.primary)
                    if(!videoReady)Text("Offline wird erstellt",fontSize=9.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
                else->Column(horizontalAlignment=Alignment.CenterHorizontally){Text("▧",fontSize=30.sp,color=MaterialTheme.colorScheme.onSurfaceVariant);Text("Vorschau",fontSize=9.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)}
            }
            if(item.video){
                Surface(Modifier.align(Alignment.Center),shape=RoundedCornerShape(30.dp),color=Color.Black.copy(alpha=0.55f)){
                    Text("▶",Modifier.padding(horizontal=15.dp,vertical=9.dp),fontSize=24.sp,color=Color.White)
                }
            }
            Surface(Modifier.align(Alignment.BottomCenter).fillMaxWidth(),color=Color.Black.copy(alpha=0.45f)){
                Text(item.name,Modifier.padding(5.dp),fontSize=9.sp,color=Color.White,maxLines=1,overflow=TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun MediaDialog(item:GalleryMedia,repo:GalleryRepository,onClose:()->Unit){
    val itemFolder=item.relativePath().substringBeforeLast('/',"")
    val pages=remember(item.sourceId,item.path,itemFolder){
        if(item.video)listOf(item)
        else repo.loadIndex().asSequence()
            .filter{!it.video&&it.sourceId==item.sourceId&&it.relativePath().substringBeforeLast('/',"")==itemFolder}
            .sortedBy{it.name.lowercase()}
            .toList()
            .ifEmpty{listOf(item)}
    }
    val initialPage=remember(item.sourceId,item.path,pages){
        pages.indexOfFirst{it.sourceId==item.sourceId&&it.path==item.path}.coerceAtLeast(0)
    }
    val pagerState=rememberPagerState(initialPage=initialPage,pageCount={pages.size})
    val currentItem=pages[pagerState.currentPage.coerceIn(0,pages.lastIndex)]
    var zoomActive by remember{mutableStateOf(false)}

    LaunchedEffect(pagerState.currentPage){zoomActive=false}

    Dialog(onDismissRequest=onClose,properties=DialogProperties(usePlatformDefaultWidth=false)){
        Box(Modifier.fillMaxSize().background(Color.Black)){
            if(item.video){
                val offlineVideo=repo.offlineVideoFile(item)
                if(offlineVideo.exists()){
                    OfflineVideoPlayer(offlineVideo)
                }else{
                    Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center){
                        Column(horizontalAlignment=Alignment.CenterHorizontally){
                            CircularProgressIndicator(color=Color.White)
                            Spacer(Modifier.height(12.dp))
                            Text("Offline-Video wird noch erstellt",fontWeight=FontWeight.Bold,color=Color.White)
                            Text("Das Originalvideo wird nicht abgespielt.",fontSize=12.sp,color=Color.White.copy(alpha=0.7f))
                        }
                    }
                }
            }else{
                HorizontalPager(state=pagerState,modifier=Modifier.fillMaxSize(),userScrollEnabled=!zoomActive){page->
                    val pageItem=pages[page]
                    val preview=repo.previewFile(pageItem)
                    if(preview.exists()){
                        ZoomablePreview(pageItem,preview,page==pagerState.currentPage){active->
                            if(page==pagerState.currentPage)zoomActive=active
                        }
                    }else{
                        Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center){
                            Column(horizontalAlignment=Alignment.CenterHorizontally){
                                CircularProgressIndicator(color=Color.White)
                                Spacer(Modifier.height(12.dp))
                                Text("Offline-Vorschau wird noch erstellt",fontSize=12.sp,color=Color.White)
                            }
                        }
                    }
                }
            }

            Row(
                Modifier.fillMaxWidth().align(Alignment.TopCenter).background(Color.Black.copy(alpha=0.45f)).statusBarsPadding().padding(horizontal=8.dp,vertical=4.dp),
                verticalAlignment=Alignment.CenterVertically
            ){
                TextButton(onClick=onClose){Text("×",fontSize=34.sp,color=Color.White)}
                Text(currentItem.name,Modifier.weight(1f),fontWeight=FontWeight.SemiBold,color=Color.White,maxLines=1,overflow=TextOverflow.Ellipsis)
                if(!item.video&&pages.size>1)Text("${pagerState.currentPage+1} / ${pages.size}",fontSize=12.sp,color=Color.White.copy(alpha=0.8f),modifier=Modifier.padding(end=10.dp))
            }

            Column(
                Modifier.fillMaxWidth().align(Alignment.BottomCenter).background(Color.Black.copy(alpha=0.45f)).navigationBarsPadding().padding(horizontal=18.dp,vertical=12.dp)
            ){
                Text(currentItem.relativePath(),fontSize=11.sp,color=Color.White.copy(alpha=0.8f),maxLines=2,overflow=TextOverflow.Ellipsis)
                if(currentItem.video){
                    Text("Komprimierte Offline-Version · Original wird nicht abgespielt",fontSize=10.sp,color=Color.White.copy(alpha=0.65f))
                }else{
                    Text("Links/rechts wischen · mit zwei Fingern zoomen · verkleinerte Offline-Vorschau · Original wird nie geladen",fontSize=10.sp,color=Color.White.copy(alpha=0.65f))
                }
            }
        }
    }
}

@Composable
private fun OfflineVideoPlayer(file:File){
    var view by remember(file.absolutePath){mutableStateOf<VideoView?>(null)}
    var resumePosition by rememberSaveable(file.absolutePath){mutableStateOf(0)}
    var resumePlaying by rememberSaveable(file.absolutePath){mutableStateOf(true)}

    LaunchedEffect(view,file.absolutePath){
        while(true){
            val current=view
            if(current!=null&&runCatching{current.duration>0}.getOrDefault(false)){
                resumePosition=runCatching{current.currentPosition}.getOrDefault(resumePosition)
                resumePlaying=runCatching{current.isPlaying}.getOrDefault(resumePlaying)
            }
            delay(250)
        }
    }

    DisposableEffect(file.absolutePath){
        onDispose{
            view?.let{current->
                runCatching{
                    if(current.duration>0){
                        resumePosition=current.currentPosition
                        resumePlaying=current.isPlaying
                    }
                    current.stopPlayback()
                }
            }
        }
    }

    AndroidView(
        factory={ctx->
            VideoView(ctx).apply{
                view=this
                tag=file.absolutePath
                val controller=MediaController(ctx)
                controller.setAnchorView(this)
                setMediaController(controller)
                setOnPreparedListener{
                    if(resumePosition>0)seekTo(resumePosition)
                    if(resumePlaying)start()
                }
                setVideoPath(file.absolutePath)
            }
        },
        update={videoView->
            view=videoView
            if(videoView.tag!=file.absolutePath){
                videoView.tag=file.absolutePath
                videoView.setVideoPath(file.absolutePath)
            }
        },
        modifier=Modifier.fillMaxSize()
    )
}

@Composable
private fun ZoomablePreview(item:GalleryMedia,previewFile:File,isCurrent:Boolean,onZoomActive:(Boolean)->Unit){
    var scale by remember(item.sourceId,item.path){mutableFloatStateOf(1f)}
    var offsetX by remember(item.sourceId,item.path){mutableFloatStateOf(0f)}
    var offsetY by remember(item.sourceId,item.path){mutableFloatStateOf(0f)}

    LaunchedEffect(isCurrent){
        if(!isCurrent){
            scale=1f
            offsetX=0f
            offsetY=0f
            onZoomActive(false)
        }
    }

    Box(
        Modifier.fillMaxSize().pointerInput(item.sourceId,item.path){
            awaitEachGesture{
                awaitFirstDown(requireUnconsumed=false)
                do{
                    val event=awaitPointerEvent()
                    val pressed=event.changes.count{it.pressed}
                    if(pressed>=2||scale>1.01f){
                        val zoom=event.calculateZoom()
                        val pan=event.calculatePan()
                        val newScale=(scale*zoom).coerceIn(1f,5f)
                        scale=newScale
                        if(newScale<=1.01f){
                            offsetX=0f
                            offsetY=0f
                        }else{
                            offsetX+=pan.x
                            offsetY+=pan.y
                        }
                        onZoomActive(newScale>1.01f)
                        event.changes.forEach{it.consume()}
                    }
                }while(event.changes.any{it.pressed})
            }
        },
        contentAlignment=Alignment.Center
    ){
        AsyncImage(
            model=previewFile,
            contentDescription=item.name,
            modifier=Modifier.fillMaxSize().graphicsLayer{
                scaleX=scale
                scaleY=scale
                translationX=offsetX
                translationY=offsetY
            },
            contentScale=ContentScale.Fit
        )
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
