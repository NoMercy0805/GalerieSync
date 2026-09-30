package de.galeriesync

import android.Manifest
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.work.*
import coil.compose.AsyncImage
import de.galeriesync.data.*
import de.galeriesync.sync.MediaFolder
import de.galeriesync.sync.MediaScanner
import de.galeriesync.sync.SmbProvider
import de.galeriesync.sync.SyncService
import de.galeriesync.worker.SyncWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

private fun currentWifiName(context:android.content.Context):String?{val wm=context.applicationContext.getSystemService(android.content.Context.WIFI_SERVICE) as? WifiManager?:return null;return runCatching{wm.connectionInfo?.ssid?.trim('"')}.getOrNull()?.takeIf{it.isNotBlank()&&it!="<unknown ssid>"} }

private val Navy=Color(0xFF071A27);private val Panel=Color(0xFF102A3A);private val Panel2=Color(0xFF17384B)
private val Blue=Color(0xFF3DA5FF);private val Green=Color(0xFF42D77D);private val Muted=Color(0xFF9CB1BF)
private val Scheme=darkColorScheme(primary=Blue,onPrimary=Color.White,background=Navy,surface=Panel,surfaceVariant=Panel2,onBackground=Color.White,onSurface=Color.White,onSurfaceVariant=Color.White,outline=Color(0xFF345568))
class MainActivity:ComponentActivity(){override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);setContent{MaterialTheme(colorScheme=Scheme){App()}}}}
enum class Tab(val label:String,val icon:String){HOME("Übersicht","⌂"),SYNC("Synchronisation","↻"),NAS("NAS","▣"),SETTINGS("Einstellungen","⚙")}
enum class Editor{NONE,NAS,SYNC}

@Composable fun App(){
 val context=LocalContext.current;val store=remember{SettingsStore(context)}
 var nas by remember{mutableStateOf(store.loadConnections())};var profiles by remember{mutableStateOf(store.loadProfiles())}
 var tab by remember{mutableStateOf(Tab.HOME)};var editor by remember{mutableStateOf(Editor.NONE)};var settingsPage by remember{mutableStateOf<String?>(null)};var editing by remember{mutableStateOf<NasSettings?>(null)}
 var status by remember{mutableStateOf("Bereit")};var running by remember{mutableStateOf(false)};var syncCurrent by remember{mutableIntStateOf(0)};var syncTotal by remember{mutableIntStateOf(0)};var syncFile by remember{mutableStateOf("")};var syncJob by remember{mutableStateOf<Job?>(null)};var nasTestStates by remember{mutableStateOf<Map<String,Boolean>>(emptyMap())};val scope=rememberCoroutineScope()
 val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()){}
 LaunchedEffect(Unit){val p=if(Build.VERSION.SDK_INT>=33)arrayOf(Manifest.permission.READ_MEDIA_IMAGES,Manifest.permission.READ_MEDIA_VIDEO)else arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE);if(p.any{ContextCompat.checkSelfPermission(context,it)!=PackageManager.PERMISSION_GRANTED})permission.launch(p)}
 fun saveNas(v:NasSettings){nas=(nas.filterNot{it.id==v.id}+v);store.saveConnections(nas)}
 fun deleteNas(v:NasSettings){nas=nas.filterNot{it.id==v.id};profiles=profiles.filterNot{it.host==v.host&&it.share==v.share};store.saveConnections(nas);store.saveProfiles(profiles)}
 fun saveProfile(v:NasSettings){profiles=profiles.filterNot{it.id==v.id}+v;store.saveProfiles(profiles)}
 BackHandler(enabled=editor!=Editor.NONE||settingsPage!=null){if(editor!=Editor.NONE){editor=Editor.NONE;editing=null}else settingsPage=null}
 fun runProfile(p:NasSettings){syncJob=scope.launch{running=true;syncCurrent=0;syncTotal=0;syncFile="";status="Synchronisiere "+p.profileName.ifBlank{"Profil"}+" …";val r=runCatching{SyncService(context).run(p){i,total,n->syncCurrent=i;syncTotal=total;syncFile=n;status=n}};status=r.fold({result->if(result.failed==0)"Fertig über ${result.transport} · ${result.copied} übertragen · ${result.skipped} übersprungen" else "SMB · ${result.copied} übertragen · ${result.failed} Fehler · ${result.lastError?:"Details unbekannt"}"},{"SMB-Fehler · "+(it.message?:"Unbekannt")});running=false;syncFile=""}}
 if(editor==Editor.NAS){NasEditor(editing?:NasSettings(),{editor=Editor.NONE},{saveNas(it);editor=Editor.NONE});return}
 if(editor==Editor.SYNC){SyncEditor(editing,nas,{editor=Editor.NONE},{saveProfile(it);editor=Editor.NONE});return}
 if(running){SyncProgressScreen(profiles.firstOrNull{it.enabled}?:NasSettings(),syncCurrent,syncTotal,syncFile){syncJob?.cancel();running=false;status="Synchronisation abgebrochen";syncFile=""};return}
 Scaffold(containerColor=Navy,bottomBar={NavigationBar(containerColor=Color(0xFF091F2D)){Tab.entries.forEach{t->NavigationBarItem(selected=tab==t,onClick={tab=t},icon={Text(t.icon,fontSize=20.sp)},label={Text(t.label,fontSize=10.sp)})}}}){pad->
  Box(Modifier.padding(pad).fillMaxSize()){when(tab){
   Tab.HOME->Home(nas,profiles,status,running,syncCurrent,syncTotal,syncFile,{profiles.filter{it.enabled}.forEach{runProfile(it)}})
   Tab.NAS->NasList(nas,nasTestStates,{editing=null;editor=Editor.NAS},{editing=it;editor=Editor.NAS},{deleteNas(it)},{n->scope.launch{status="Teste Verbindung …";val result=runCatching{withContext(Dispatchers.IO){SmbProvider(n).test()}};nasTestStates=nasTestStates+(n.id to result.isSuccess);status=result.fold({"Verbunden"},{"Fehler · "+(it.message?:"Unbekannt")})}})
   Tab.SYNC->ProfileList(profiles,running,{editing=null;editor=Editor.SYNC},{editing=it;editor=Editor.SYNC},{p->profiles=profiles.filterNot{it.id==p.id};store.saveProfiles(profiles)},{p->val v=p.copy(enabled=!p.enabled);profiles=profiles.map{if(it.id==p.id)v else it};store.saveProfiles(profiles)},{runProfile(it)})
   Tab.SETTINGS->SettingsScreen(settingsPage,{settingsPage=it})
  }}
 }
}

@Composable private fun Header(t:String,s:String?=null){Column(Modifier.fillMaxWidth().padding(20.dp,18.dp,20.dp,10.dp)){Text(t,fontSize=25.sp,fontWeight=FontWeight.Bold);s?.let{Text(it,color=Muted,fontSize=13.sp)}}}
@Composable private fun CardBox(content:@Composable ColumnScope.()->Unit){Card(Modifier.fillMaxWidth(),shape=RoundedCornerShape(16.dp),colors=CardDefaults.cardColors(containerColor=Panel)){Column(Modifier.padding(16.dp),content=content)}}
@Composable private fun Field(label:String,value:String,on:(String)->Unit,password:Boolean=false){OutlinedTextField(value,on,label={Text(label)},modifier=Modifier.fillMaxWidth(),singleLine=true,shape=RoundedCornerShape(12.dp),colors=OutlinedTextFieldDefaults.colors(focusedContainerColor=Panel,unfocusedContainerColor=Panel,focusedTextColor=Color.White,unfocusedTextColor=Color.White,focusedLabelColor=Blue,unfocusedLabelColor=Muted,focusedBorderColor=Blue,unfocusedBorderColor=Color(0xFF345568),cursorColor=Blue),visualTransformation=if(password)PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None)}
@Composable private fun BackTitle(title:String,onBack:()->Unit){Row(Modifier.fillMaxWidth().padding(12.dp),verticalAlignment=Alignment.CenterVertically){TextButton(onClick=onBack){Text("‹",fontSize=30.sp,color=Blue)};Text(title,fontSize=22.sp,fontWeight=FontWeight.Bold,color=Color.White)}}

@Composable private fun Home(nas:List<NasSettings>,profiles:List<NasSettings>,status:String,running:Boolean,current:Int,total:Int,file:String,onAll:()->Unit){
 Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())){
  Box(Modifier.fillMaxWidth().height(220.dp).background(Brush.verticalGradient(listOf(Color(0xFF15506B),Color(0xFF0D3449),Navy)))){Column(Modifier.align(Alignment.BottomStart).padding(24.dp)){Text("GalerieSync",fontSize=32.sp,fontWeight=FontWeight.Bold,color=Color.White);Text("Deine Fotos. Sicher. Überall.",color=Muted,fontSize=16.sp)}}
  Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
   CardBox{Row(verticalAlignment=Alignment.CenterVertically){Text("●",color=if(profiles.any{it.enabled})Green else Muted,fontSize=16.sp);Spacer(Modifier.width(10.dp));Column(Modifier.weight(1f)){Text("Aktive Synchronisationen",fontWeight=FontWeight.Bold,color=Color.White);Text(profiles.count{it.enabled}.toString()+" aktiv",color=if(profiles.any{it.enabled})Green else Muted)}}}
   CardBox{Text(if(running)"Synchronisation läuft" else "Letzte Synchronisation / Status",fontWeight=FontWeight.Bold,color=Color.White);if(running&&total>0){LinearProgressIndicator(progress={current.toFloat()/total.coerceAtLeast(1)},modifier=Modifier.fillMaxWidth().padding(vertical=10.dp));Text("$current von $total Dateien",color=Color.White);Text(file,color=Muted,fontSize=12.sp)}else Text(status,color=Muted)}
   CardBox{Text("NAS-Verbindungen",fontWeight=FontWeight.Bold,color=Color.White);Text(nas.size.toString()+" gespeichert",color=Muted)}
   Button(onClick=onAll,enabled=!running&&profiles.any{it.enabled},modifier=Modifier.fillMaxWidth().height(54.dp)){Text(if(running)"Synchronisiere …" else "↻  Jetzt synchronisieren",color=Color.White,fontWeight=FontWeight.Bold)}
  }
 }
}

@Composable private fun SyncProgressScreen(profile:NasSettings,current:Int,total:Int,file:String,onCancel:()->Unit){
 Column(Modifier.fillMaxSize().background(Navy)){BackTitle("Synchronisation",onCancel);Column(Modifier.fillMaxSize().padding(24.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(18.dp)){
  Spacer(Modifier.height(20.dp));Box(Modifier.size(170.dp),contentAlignment=Alignment.Center){CircularProgressIndicator(progress={if(total>0)current.toFloat()/total else 0f},modifier=Modifier.fillMaxSize(),strokeWidth=10.dp);Text("↻",fontSize=54.sp,color=Color.White)}
  Text("Synchronisiere …",fontSize=24.sp,fontWeight=FontWeight.Bold,color=Color.White);Text(if(total>0)"$current von $total Dateien" else "Dateien werden vorbereitet …",color=Muted)
  CardBox{Text(file.ifBlank{"Verbindung wird vorbereitet …"},color=Color.White,fontWeight=FontWeight.SemiBold);Text(if(file.isBlank())"Warte …" else "Wird synchronisiert …",color=Blue,fontSize=12.sp)}
  Spacer(Modifier.weight(1f));OutlinedButton(onClick=onCancel,modifier=Modifier.fillMaxWidth().height(54.dp)){Text("Abbrechen",color=Color.White)}
 }}
}

@Composable private fun NasList(items:List<NasSettings>,testStates:Map<String,Boolean>,onAdd:()->Unit,onEdit:(NasSettings)->Unit,onDelete:(NasSettings)->Unit,onTest:(NasSettings)->Unit){Column(Modifier.fillMaxSize()){Header("NAS","Verbindungen verwalten");Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)){if(items.isEmpty())CardBox{Text("Noch keine NAS-Verbindung",fontWeight=FontWeight.Bold);Text("Lege eine SMB-Verbindung zu deiner NAS an.",color=Muted)};items.forEach{n->CardBox{Row(verticalAlignment=Alignment.CenterVertically){val testState=testStates[n.id];Text("▣",fontSize=30.sp,color=when(testState){true->Green;false->MaterialTheme.colorScheme.error;null->Color.White});Spacer(Modifier.width(12.dp));Column(Modifier.weight(1f)){Text(n.name.ifBlank{n.host},fontWeight=FontWeight.Bold);Text(n.host+" · "+n.share,color=Muted,fontSize=12.sp)}};Row(horizontalArrangement=Arrangement.spacedBy(6.dp)){TextButton(onClick={onTest(n)}){Text("Test")};TextButton(onClick={onEdit(n)}){Text("Bearbeiten")};TextButton(onClick={onDelete(n)}){Text("Löschen",color=MaterialTheme.colorScheme.error)}}}};Button(onClick=onAdd,modifier=Modifier.fillMaxWidth()){Text("+  Neue NAS-Verbindung")}}}}

@Composable private fun ProfileList(items:List<NasSettings>,running:Boolean,onAdd:()->Unit,onEdit:(NasSettings)->Unit,onDelete:(NasSettings)->Unit,onToggle:(NasSettings)->Unit,onRun:(NasSettings)->Unit){Column(Modifier.fillMaxSize()){Header("Synchronisationen","Ordner, Richtung und Ziel frei verwalten");Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)){if(items.isEmpty())CardBox{Text("Noch keine Synchronisation",fontWeight=FontWeight.Bold);Text("Erstelle beliebig viele Profile und wähle pro Profil eigene Ordner.",color=Muted)};items.forEach{p->CardBox{Row(verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text(p.profileName.ifBlank{syncProfileTitle(p.direction)},fontWeight=FontWeight.Bold);Text(syncDirectionText(p.direction)+" · "+p.sourceFolders.size.toString()+" Ordner",color=Muted,fontSize=12.sp);Text((p.name.ifBlank{p.host})+"  →  /"+p.remoteFolder,color=Muted,fontSize=12.sp);Text(if(p.enabled)"● Aktiv" else "○ Inaktiv",color=if(p.enabled)Green else Muted,fontSize=12.sp)};Switch(p.enabled,{onToggle(p)})};Row{TextButton(onClick={onRun(p)},enabled=!running&&p.enabled){Text("Jetzt")};TextButton(onClick={onEdit(p)}){Text("Bearbeiten")};TextButton(onClick={onDelete(p)}){Text("Löschen",color=MaterialTheme.colorScheme.error)}}}};Button(onClick=onAdd,enabled=true,modifier=Modifier.fillMaxWidth()){Text("+  Neue Synchronisation")}}}}

@Composable private fun NasEditor(initial:NasSettings,onBack:()->Unit,onSave:(NasSettings)->Unit){
 var s by remember{mutableStateOf(initial)};var shares by remember{mutableStateOf(emptyList<String>())};var msg by remember{mutableStateOf("")};var ugreenMsg by remember{mutableStateOf("")};var busy by remember{mutableStateOf(false)};var ugreenBusy by remember{mutableStateOf(false)};val scope=rememberCoroutineScope()
 Column(Modifier.fillMaxSize().background(Navy)){
  BackTitle(if(initial.host.isBlank())"NAS hinzufügen" else "NAS bearbeiten",onBack)
  Column(Modifier.padding(20.dp,16.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)){
    Text("Lokale Verbindung",color=Color.White,fontWeight=FontWeight.Bold,fontSize=19.sp)
    Text("SMB im lokalen Netzwerk",color=Muted,fontSize=12.sp)
    Field("Name",s.name,{s=s.copy(name=it)})
    Field("Server / IP-Adresse",s.host,{s=s.copy(host=it)})
    Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){Box(Modifier.weight(1f)){Field("Benutzername",s.username,{s=s.copy(username=it)})};Box(Modifier.weight(1f)){Field("Passwort",s.password,{s=s.copy(password=it)},true)}}
    Button(onClick={scope.launch{busy=true;msg=runCatching{shares=withContext(Dispatchers.IO){SmbProvider(s.copy(share="")).listShares()};if(shares.isEmpty())"SMB erreichbar · Freigabe manuell eintragen" else "✓ "+shares.size+" Freigaben gefunden"}.getOrElse{"✕ "+(it.message?:it.javaClass.simpleName)};busy=false}},enabled=!busy&&s.host.isNotBlank()&&s.username.isNotBlank(),modifier=Modifier.fillMaxWidth()){Text(if(busy)"Suche …" else "Verbindung testen & Freigaben suchen",color=if(!busy&&s.host.isNotBlank()&&s.username.isNotBlank())Color.White else Muted)}
    if(msg.isNotBlank())Text(msg,color=if(msg.startsWith("✓"))Green else Muted,fontSize=12.sp)
    if(shares.isNotEmpty()){Text("Freigabe auswählen",color=Color.White,fontWeight=FontWeight.SemiBold);shares.forEach{sh->Surface(Modifier.fillMaxWidth().clickable{s=s.copy(share=sh)},shape=RoundedCornerShape(12.dp),color=if(s.share==sh)Color(0xFF17466A) else Panel){Row(Modifier.padding(14.dp),verticalAlignment=Alignment.CenterVertically){Text("▣",color=Blue);Spacer(Modifier.width(10.dp));Text(sh,Modifier.weight(1f),color=Color.White);RadioButton(s.share==sh,null)}}}}
    Field("SMB-Freigabe",s.share,{s=s.copy(share=it)})
      Spacer(Modifier.height(4.dp))
   Button(onClick={onSave(s.copy(name=s.name.ifBlank{s.host}))},enabled=(s.host.isNotBlank()&&s.share.isNotBlank()),modifier=Modifier.fillMaxWidth().height(52.dp),shape=RoundedCornerShape(12.dp)){Text("NAS-Verbindung speichern",color=if((s.host.isNotBlank()&&s.share.isNotBlank()))Color.White else Muted,fontWeight=FontWeight.Bold)}
   Spacer(Modifier.height(18.dp))
  }
 }
}
@Composable private fun SyncEditor(existing:NasSettings?,connections:List<NasSettings>,onBack:()->Unit,onSave:(NasSettings)->Unit){
 var step by remember{mutableIntStateOf(0)}
 var selected by remember{mutableStateOf(existing?:connections.firstOrNull())}
 var p by remember{mutableStateOf(existing?:connections.firstOrNull()?.copy(id=java.util.UUID.randomUUID().toString(),profileName="",sourceFolders=emptySet(),remoteFolder="GalerieSync")?:NasSettings())}
 var folders by remember{mutableStateOf(emptyList<MediaFolder>())}
 val context=LocalContext.current
 LaunchedEffect(step){if(step==1)folders=withContext(Dispatchers.IO){MediaScanner(context.contentResolver).folderMedia()}}
 Column(Modifier.fillMaxSize().background(Navy)){
  BackTitle(if(existing==null)"Neue Synchronisation" else "Synchronisation bearbeiten",onBack)
  Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)){
   if(connections.isEmpty()){
    CardBox{Text("Zuerst eine NAS hinzufügen",color=Color.White,fontWeight=FontWeight.Bold);Text("Synchronisationen benötigen eine gespeicherte NAS-Verbindung.",color=Muted)}
   }else if(step==0){
    Field("Name der Synchronisation",p.profileName,{p=p.copy(profileName=it)})
    Text("NAS auswählen",color=Color.White,fontWeight=FontWeight.Bold)
    connections.forEach{n->val active=selected?.id==n.id;Card(Modifier.fillMaxWidth().clickable{selected=n;p=p.copy(host=n.host,share=n.share,username=n.username,password=n.password,domain=n.domain,name=n.name)},colors=CardDefaults.cardColors(containerColor=if(active)Color(0xFF17466A) else Panel)){Row(Modifier.padding(12.dp),verticalAlignment=Alignment.CenterVertically){Text(n.name.ifBlank{n.host},Modifier.weight(1f),fontWeight=FontWeight.Bold,color=Color.White);RadioButton(active,null)}}}
    Text("Richtung",color=Color.White,fontWeight=FontWeight.Bold)
    DirectionCard("Handy → NAS",p.direction==SyncDirection.PHONE_TO_NAS){p=p.copy(direction=SyncDirection.PHONE_TO_NAS)}
    DirectionCard("NAS → Handy",p.direction==SyncDirection.NAS_TO_PHONE){p=p.copy(direction=SyncDirection.NAS_TO_PHONE)}
    DirectionCard("Bidirektional",p.direction==SyncDirection.BIDIRECTIONAL){p=p.copy(direction=SyncDirection.BIDIRECTIONAL)}
    Button(onClick={step=1},modifier=Modifier.fillMaxWidth()){Text("Ordner auswählen",color=Color.White)}
   }else{
    Row(verticalAlignment=Alignment.CenterVertically){
     Column(Modifier.weight(1f)){Text("Ordner auswählen",color=Color.White,fontWeight=FontWeight.Bold,fontSize=20.sp);Text("Fotos & Videos auf dem Smartphone",color=Muted,fontSize=12.sp)}
     Surface(shape=RoundedCornerShape(20.dp),color=Panel2){Text(p.sourceFolders.size.toString()+" gewählt",Modifier.padding(10.dp,6.dp),color=Blue,fontSize=12.sp,fontWeight=FontWeight.Bold)}
    }
    val allSelected=folders.isNotEmpty()&&p.sourceFolders.containsAll(folders.map{it.path})
    TextButton(onClick={p=p.copy(sourceFolders=if(allSelected)emptySet() else folders.map{it.path}.toSet())},enabled=folders.isNotEmpty(),modifier=Modifier.fillMaxWidth()){
     Text(if(allSelected)"Auswahl aufheben" else "Alle Ordner auswählen",color=Blue,fontWeight=FontWeight.SemiBold)
    }
    folders.forEach{f->
     val active=f.path in p.sourceFolders
     Card(Modifier.fillMaxWidth().clickable{p=p.copy(sourceFolders=if(active)p.sourceFolders-f.path else p.sourceFolders+f.path)},shape=RoundedCornerShape(16.dp),colors=CardDefaults.cardColors(containerColor=if(active)Color(0xFF17466A) else Panel)){
      Column(Modifier.padding(12.dp)){
       Row(verticalAlignment=Alignment.CenterVertically){Checkbox(active,null);Spacer(Modifier.width(4.dp));Column{Text(f.name,fontWeight=FontWeight.Bold,fontSize=17.sp,color=Color.White);Text(f.count.toString()+" Dateien",color=Muted,fontSize=12.sp)}}
       if(f.previews.isNotEmpty()){Spacer(Modifier.height(8.dp));Row(horizontalArrangement=Arrangement.spacedBy(5.dp)){f.previews.take(4).forEach{u->AsyncImage(model=u,contentDescription=null,contentScale=ContentScale.Crop,modifier=Modifier.weight(1f).height(68.dp))}}}
      }
     }
    }
    Field("Basisordner auf der NAS",p.remoteFolder,{p=p.copy(remoteFolder=it)})
    if(p.sourceFolders.isNotEmpty()){
     Text("Ordnerstruktur auf der NAS",color=Color.White,fontWeight=FontWeight.SemiBold)
     p.sourceFolders.sorted().take(4).forEach{path->
      val clean=path.trim('/')
      val parts=clean.split('/').filter{it.isNotBlank()}
      val rest=if(parts.firstOrNull()?.lowercase() in setOf("dcim","pictures","movies"))parts.drop(1).joinToString("/") else clean
      Text("• "+p.remoteFolder.trimEnd('/')+"/Bilder & Videos/"+rest,color=Muted,fontSize=12.sp)
     }
     if(p.sourceFolders.size>4)Text("… und "+(p.sourceFolders.size-4)+" weitere",color=Muted,fontSize=12.sp)
    }
    Button(onClick={onSave(p.copy(profileName=p.profileName.ifBlank{syncProfileTitle(p.direction)}))},enabled=p.sourceFolders.isNotEmpty()&&p.remoteFolder.isNotBlank(),modifier=Modifier.fillMaxWidth()){Text("Synchronisation speichern",color=if(p.sourceFolders.isNotEmpty()&&p.remoteFolder.isNotBlank())Color.White else Muted)}
   }
  }
 }
}

@Composable private fun DirectionCard(t:String,on:Boolean,click:()->Unit){Card(Modifier.fillMaxWidth().clickable{click()},shape=RoundedCornerShape(14.dp),colors=CardDefaults.cardColors(containerColor=if(on)Color(0xFF17466A) else Panel)){Row(Modifier.padding(16.dp),verticalAlignment=Alignment.CenterVertically){Text(t,Modifier.weight(1f),fontWeight=FontWeight.SemiBold,color=Color.White);RadioButton(on,click)}}}
@Composable private fun SettingsScreen(page:String?,open:(String?)->Unit){val context=LocalContext.current
 if(page!=null){Column(Modifier.fillMaxSize().background(Navy)){BackTitle(page){open(null)};Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)){when(page){
  "Allgemein"->CardBox{Text("Darstellung",color=Color.White,fontWeight=FontWeight.Bold);Text("GalerieSync verwendet das optimierte dunkle Design.",color=Muted);Text("Benachrichtigungen und Sprache folgen derzeit den Android-Systemeinstellungen.",color=Muted,fontSize=12.sp)}
  "WLAN-Einstellungen"->VpnFallbackSettings(context)
  "Automatische Synchronisation"->CardBox{Text("Hintergrund-Synchronisation",color=Color.White,fontWeight=FontWeight.Bold);Text("Aktive Profile können alle 15 Minuten geprüft werden.",color=Muted);Button(onClick={schedule(context)},modifier=Modifier.fillMaxWidth()){Text("Automatik aktivieren",color=Color.White)}}
  "Daten & Speicher"->CardBox{Text("Lokale Daten",color=Color.White,fontWeight=FontWeight.Bold);Text("NAS-Zugangsdaten werden verschlüsselt gespeichert. GalerieSync löscht beim Synchronisieren keine Dateien automatisch.",color=Muted)}
  else->CardBox{Text("GalerieSync",color=Color.White,fontWeight=FontWeight.Bold,fontSize=20.sp);Text("Version 0.4.0",color=Muted);Text("Fotos und Videos zwischen Smartphone und NAS synchronisieren.",color=Muted)}
 }}};return}
 Column(Modifier.fillMaxSize()){Header("Einstellungen");Column(Modifier.padding(horizontal=16.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(2.dp)){
  SettingsRow("⚙","Allgemein","Design, Benachrichtigungen, Sprache"){open("Allgemein")}
  SettingsRow("◉","WLAN & VPN","Vertrauenswürdige WLANs · VPN-Fallback"){open("WLAN-Einstellungen")}
  SettingsRow("◷","Automatische Synchronisation","Zeitplan · Hintergrund"){open("Automatische Synchronisation")}
  SettingsRow("▣","Daten & Speicher","Speicherorte, Cache, Datenbank"){open("Daten & Speicher")}
  SettingsRow("ⓘ","Über die App","GalerieSync · Version 0.4.0"){open("Über die App")}
 }}}
@Composable private fun VpnFallbackSettings(context:android.content.Context){
 val store=remember{SettingsStore(context)};var trusted by remember{mutableStateOf(store.loadTrustedWifi())};var config by remember{mutableStateOf(store.loadVpnConfig())};var current by remember{mutableStateOf(currentWifiName(context))};var message by remember{mutableStateOf("")}
 val wifiPermission=rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()){current=currentWifiName(context);if(current==null)message="WLAN-Name konnte nicht gelesen werden."}
 val importConfig=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){uri->if(uri!=null){config=runCatching{context.contentResolver.openInputStream(uri)?.bufferedReader()?.use{it.readText()}.orEmpty()}.getOrDefault("");if(config.isNotBlank()){store.saveVpnConfig(config);message="WireGuard-Konfiguration importiert ✓"}}}
 fun refreshWifi(){val needed=if(Build.VERSION.SDK_INT>=33)arrayOf(Manifest.permission.NEARBY_WIFI_DEVICES)else arrayOf(Manifest.permission.ACCESS_FINE_LOCATION);if(needed.any{ContextCompat.checkSelfPermission(context,it)!=PackageManager.PERMISSION_GRANTED})wifiPermission.launch(needed) else current=currentWifiName(context)}
 LaunchedEffect(Unit){refreshWifi()}
 Column(verticalArrangement=Arrangement.spacedBy(12.dp)){
  CardBox{
   Text("Heim-WLANs",color=Color.White,fontWeight=FontWeight.Bold,fontSize=18.sp)
   Text("In diesen WLANs verbindet sich GalerieSync direkt per SMB. Außerhalb wird später automatisch das FRITZ!Box-VPN verwendet.",color=Muted,fontSize=12.sp)
   Spacer(Modifier.height(10.dp))
   Text(current?.let{"Aktuell verbunden: $it"}?:"Aktuelles WLAN nicht erkannt",color=if(current!=null)Green else Muted,fontWeight=FontWeight.SemiBold)
   Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){Button(onClick={current?.let{trusted=trusted+it;store.saveTrustedWifi(trusted);message="$it als Heim-WLAN gespeichert ✓"}?:refreshWifi()},modifier=Modifier.weight(1f)){Text(if(current!=null)"Dieses WLAN hinzufügen" else "WLAN erkennen",color=Color.White)}}
   if(trusted.isNotEmpty()){Spacer(Modifier.height(8.dp));trusted.sorted().forEach{ssid->Surface(Modifier.fillMaxWidth(),shape=RoundedCornerShape(12.dp),color=Panel2){Row(Modifier.padding(12.dp),verticalAlignment=Alignment.CenterVertically){Text("◉",color=Green);Spacer(Modifier.width(10.dp));Text(ssid,Modifier.weight(1f),color=Color.White);TextButton(onClick={trusted=trusted-ssid;store.saveTrustedWifi(trusted)}){Text("Entfernen",color=MaterialTheme.colorScheme.error)}}}}}
  }
  CardBox{
   Text("FRITZ!Box VPN",color=Color.White,fontWeight=FontWeight.Bold,fontSize=18.sp)
   Text(if(config.isBlank())"Noch nicht eingerichtet" else "WireGuard-Konfiguration eingerichtet ✓",color=if(config.isBlank())Muted else Green)
   Text("Exportiere die WireGuard-Konfiguration in der FRITZ!Box und wähle anschließend einfach die .conf-Datei aus.",color=Muted,fontSize=12.sp)
   Spacer(Modifier.height(10.dp))
   Button(onClick={importConfig.launch(arrayOf("text/plain","application/octet-stream","*/*"))},modifier=Modifier.fillMaxWidth()){Text(if(config.isBlank())"WireGuard-Datei auswählen" else "Konfiguration ersetzen",color=Color.White)}
   if(config.isNotBlank())TextButton(onClick={config="";store.saveVpnConfig("");message="VPN-Konfiguration entfernt"}){Text("VPN-Konfiguration entfernen",color=MaterialTheme.colorScheme.error)}
  }
  if(message.isNotBlank())Text(message,color=if(message.contains("✓"))Green else Muted,fontSize=12.sp)
 }
}
@Composable private fun SettingsRow(icon:String,title:String,subtitle:String,onClick:()->Unit){Surface(Modifier.fillMaxWidth().clickable{onClick()},color=Panel){Row(Modifier.padding(16.dp),verticalAlignment=Alignment.CenterVertically){Text(icon,fontSize=24.sp,color=Color.White);Spacer(Modifier.width(14.dp));Column(Modifier.weight(1f)){Text(title,color=Color.White,fontWeight=FontWeight.Bold);Text(subtitle,color=Muted,fontSize=12.sp)};Text("›",color=Color.White,fontSize=26.sp)}}}
private fun syncDirectionText(d:SyncDirection)=when(d){SyncDirection.PHONE_TO_NAS->"Handy → NAS";SyncDirection.NAS_TO_PHONE->"NAS → Handy";SyncDirection.BIDIRECTIONAL->"Bidirektional"}
private fun syncProfileTitle(d:SyncDirection)=when(d){SyncDirection.PHONE_TO_NAS->"Handy → NAS";SyncDirection.NAS_TO_PHONE->"NAS → Handy";SyncDirection.BIDIRECTIONAL->"Bidirektionale Synchronisation"}
fun schedule(context:android.content.Context){val c=Constraints.Builder().setRequiredNetworkType(NetworkType.UNMETERED).build();val r=PeriodicWorkRequestBuilder<SyncWorker>(15,TimeUnit.MINUTES).setConstraints(c).build();WorkManager.getInstance(context).enqueueUniquePeriodicWork("GalerieSync",ExistingPeriodicWorkPolicy.UPDATE,r)}
