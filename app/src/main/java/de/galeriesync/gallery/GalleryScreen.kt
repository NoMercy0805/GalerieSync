package de.galeriesync.gallery

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.window.DialogProperties
import androidx.work.*
import coil.compose.AsyncImage
import de.galeriesync.data.NasSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class GalleryFolder(val name:String,val path:String,val count:Int)

@Composable
fun NasGalleryScreen(profiles:List<NasSettings>){
    val context=LocalContext.current
    val repo=remember{GalleryRepository(context)}
    val scope=rememberCoroutineScope()
    var selection by remember{mutableStateOf(repo.loadSelection())}
    var media by remember{mutableStateOf(emptyList<GalleryMedia>())}
    var folder by remember{mutableStateOf("")}
    var selected by remember{mutableStateOf<GalleryMedia?>(null)}
    var status by remember{mutableStateOf("")}
    var working by remember{mutableStateOf(false)}
    var progressDone by remember{mutableIntStateOf(0)}
    var progressTotal by remember{mutableIntStateOf(0)}
    var pickerOpen by remember{mutableStateOf(selection==null)}
    var deleteOfflineConfirm by remember{mutableStateOf(false)}

    fun reloadLocal(){
        media=repo.loadIndex()
        val progress=repo.previewProgress(media)
        progressDone=progress.first
        progressTotal=progress.second
    }

    fun startRefresh(){
        if(repo.loadSelection()==null){
            pickerOpen=true
            return
        }
        val request=OneTimeWorkRequestBuilder<GalleryCacheWorker>().build()
        WorkManager.getInstance(context).enqueueUniqueWork(GalleryCacheWorker.UNIQUE_WORK,ExistingWorkPolicy.REPLACE,request)
        working=true
        status="NAS-Ordner wird eingelesen …"
        scope.launch{
            WorkManager.getInstance(context).getWorkInfoByIdFlow(request.id).filterNotNull().takeWhile{info->
                info.progress.getString(GalleryCacheWorker.KEY_MESSAGE)?.let{status=it}
                val pTotal=info.progress.getInt(GalleryCacheWorker.KEY_TOTAL,0)
                if(pTotal>0){
                    progressTotal=pTotal
                    progressDone=info.progress.getInt(GalleryCacheWorker.KEY_DONE,progressDone)
                }
                if(info.state.isFinished){
                    status=info.outputData.getString(GalleryCacheWorker.KEY_MESSAGE)?:status
                    val outTotal=info.outputData.getInt(GalleryCacheWorker.KEY_TOTAL,0)
                    if(outTotal>0){
                        progressTotal=outTotal
                        progressDone=info.outputData.getInt(GalleryCacheWorker.KEY_DONE,progressDone)
                    }
                }
                !info.state.isFinished
            }.collect{}
            reloadLocal()
            working=false
        }
    }

    LaunchedEffect(selection){reloadLocal()}

    val activeProfile=selection?.sourceId?.let{id->profiles.firstOrNull{it.id==id}}
    val activeTitle=activeProfile?.let{p->p.profileName.ifBlank{p.name.ifBlank{p.share.ifBlank{p.host}}}}?:"NAS"
    val contents=remember(media,folder){folderContents(media,folder)}
    val openCount=(progressTotal-progressDone).coerceAtLeast(0)

    BackHandler(enabled=folder.isNotBlank()){folder=folder.substringBeforeLast('/',"")}

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)){
        Row(Modifier.fillMaxWidth().padding(horizontal=18.dp,vertical=10.dp),verticalAlignment=Alignment.CenterVertically){
            Column(Modifier.weight(1f)){
                Text("Galerie",fontSize=25.sp,fontWeight=FontWeight.Bold,color=MaterialTheme.colorScheme.onBackground)
                Text("Verkleinerte Offline-Vorschauen · ohne Speicherlimit",fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick={pickerOpen=true},enabled=profiles.isNotEmpty()){Text(if(selection==null)"Ordner wählen" else "Ordner ändern")}
        }

        if(selection!=null){
            Surface(Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=2.dp),shape=RoundedCornerShape(14.dp),color=MaterialTheme.colorScheme.surface){
                Row(Modifier.fillMaxWidth().padding(12.dp),verticalAlignment=Alignment.CenterVertically){
                    Text("📁",fontSize=25.sp)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)){
                        Text(activeTitle,fontWeight=FontWeight.Bold,maxLines=1,overflow=TextOverflow.Ellipsis)
                        Text("/"+selection!!.folder.ifBlank{""},fontSize=11.sp,color=MaterialTheme.colorScheme.onSurfaceVariant,maxLines=2,overflow=TextOverflow.Ellipsis)
                    }
                    TextButton(onClick={startRefresh()},enabled=!working){Text(if(working)"Läuft …" else "Aktualisieren")}
                }
            }
            if(progressDone>0){
                Row(Modifier.fillMaxWidth().padding(horizontal=16.dp),horizontalArrangement=Arrangement.End){
                    TextButton(onClick={deleteOfflineConfirm=true},enabled=!working){
                        Text("Offline-Daten löschen",color=MaterialTheme.colorScheme.error)
                    }
                }
            }
        }

        if(working&&progressTotal==0)LinearProgressIndicator(Modifier.fillMaxWidth())
        if(progressTotal>0){
            LinearProgressIndicator(progress={progressDone.toFloat()/progressTotal.coerceAtLeast(1)},modifier=Modifier.fillMaxWidth().padding(top=6.dp))
            Text("$progressDone Bilder fertig · $openCount offen · $progressTotal gesamt",Modifier.padding(horizontal=18.dp,vertical=5.dp),fontSize=12.sp,fontWeight=FontWeight.SemiBold,color=MaterialTheme.colorScheme.onSurface)
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
                        Text("Noch keine Offline-Vorschauen",fontWeight=FontWeight.Bold)
                        Text("Tippe auf Aktualisieren. Die App liest nur den gewählten NAS-Ordner ein und erstellt daraus verkleinerte lokale Vorschauen.",color=MaterialTheme.colorScheme.onSurfaceVariant,fontSize=13.sp)
                        Spacer(Modifier.height(12.dp))
                        Button(onClick={startRefresh()},modifier=Modifier.fillMaxWidth()){Text("Jetzt einlesen")}
                    }}
                }
            }
            else->{
                if(folder.isNotBlank()){
                    Row(Modifier.fillMaxWidth().padding(horizontal=8.dp,vertical=4.dp),verticalAlignment=Alignment.CenterVertically){
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
                        items(contents.first,key={"folder:"+it.path}){f->FolderTile(f){folder=f.path}}
                        items(contents.second,key={it.sourceId+"|"+it.path}){item->MediaTile(item,repo){selected=item}}
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
                selected=null
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
            text={Text("Alle lokal gespeicherten Galerie-Vorschauen werden gelöscht. NAS-Dateien, Ordnerauswahl und Synchronisation bleiben unverändert.")},
            confirmButton={
                TextButton(onClick={
                    deleteOfflineConfirm=false
                    selected=null
                    scope.launch{
                        val deleted=withContext(Dispatchers.IO){
                            media.asSequence().filterNot{it.video}.map{repo.previewFile(it)}.count{file->file.exists()&&file.delete()}
                        }
                        reloadLocal()
                        status="$deleted Offline-Vorschau(en) gelöscht"
                    }
                }){Text("Löschen",color=MaterialTheme.colorScheme.error)}
            },
            dismissButton={TextButton(onClick={deleteOfflineConfirm=false}){Text("Abbrechen")}}
        )
    }

    selected?.let{item->MediaDialog(item,repo){selected=null}}
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
        Surface(shape=RoundedCornerShape(20.dp),color=MaterialTheme.colorScheme.surface){
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
    Dialog(onDismissRequest=onClose,properties=DialogProperties(usePlatformDefaultWidth=false)){
        Box(Modifier.fillMaxSize().background(Color.Black)){
            when{
                item.video->Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center){
                    Column(horizontalAlignment=Alignment.CenterHorizontally){
                        Text("▶",fontSize=64.sp,color=Color.White)
                        Spacer(Modifier.height(12.dp))
                        Text("Video",fontWeight=FontWeight.Bold,color=Color.White)
                        Text("Das Originalvideo wird nicht geladen.",fontSize=12.sp,color=Color.White.copy(alpha=0.7f))
                    }
                }
                preview.exists()->AsyncImage(model=preview,contentDescription=item.name,modifier=Modifier.fillMaxSize(),contentScale=ContentScale.Fit)
                else->Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center){
                    Column(horizontalAlignment=Alignment.CenterHorizontally){
                        CircularProgressIndicator(color=Color.White)
                        Spacer(Modifier.height(12.dp))
                        Text("Offline-Vorschau wird noch erstellt",fontSize=12.sp,color=Color.White)
                    }
                }
            }

            Row(
                Modifier.fillMaxWidth().align(Alignment.TopCenter).background(Color.Black.copy(alpha=0.45f)).statusBarsPadding().padding(horizontal=8.dp,vertical=4.dp),
                verticalAlignment=Alignment.CenterVertically
            ){
                TextButton(onClick=onClose){Text("×",fontSize=34.sp,color=Color.White)}
                Text(item.name,Modifier.weight(1f),fontWeight=FontWeight.SemiBold,color=Color.White,maxLines=1,overflow=TextOverflow.Ellipsis)
            }

            Column(
                Modifier.fillMaxWidth().align(Alignment.BottomCenter).background(Color.Black.copy(alpha=0.45f)).navigationBarsPadding().padding(horizontal=18.dp,vertical=12.dp)
            ){
                Text(item.relativePath(),fontSize=11.sp,color=Color.White.copy(alpha=0.8f),maxLines=2,overflow=TextOverflow.Ellipsis)
                if(!item.video)Text("Vollbild aus der verkleinerten Offline-Vorschau · Original wird nie geladen",fontSize=10.sp,color=Color.White.copy(alpha=0.65f))
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
