package de.galeriesync

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

private val Navy=Color(0xFF071A27);private val Panel=Color(0xFF102A3A);private val Panel2=Color(0xFF17384B)
private val Blue=Color(0xFF3DA5FF);private val Green=Color(0xFF42D77D);private val Muted=Color(0xFF9CB1BF)
private val Scheme=darkColorScheme(primary=Blue,background=Navy,surface=Panel,surfaceVariant=Panel2,onBackground=Color.White,onSurface=Color.White,onSurfaceVariant=Color.White,outline=Color(0xFF345568))
class MainActivity:ComponentActivity(){override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);setContent{MaterialTheme(colorScheme=Scheme){App()}}}}
enum class Tab(val label:String,val icon:String){HOME("Übersicht","⌂"),SYNC("Synchronisation","↻"),NAS("NAS","▣"),SETTINGS("Einstellungen","⚙")}
enum class Editor{NONE,NAS,SYNC}

@Composable fun App(){
 val context=LocalContext.current;val store=remember{SettingsStore(context)}
 var nas by remember{mutableStateOf(store.loadConnections())};var profiles by remember{mutableStateOf(store.loadProfiles())}
 var tab by remember{mutableStateOf(Tab.HOME)};var editor by remember{mutableStateOf(Editor.NONE)};var editing by remember{mutableStateOf<NasSettings?>(null)}
 var status by remember{mutableStateOf("Bereit")};var running by remember{mutableStateOf(false)};val scope=rememberCoroutineScope()
 val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()){}
 LaunchedEffect(Unit){val p=if(Build.VERSION.SDK_INT>=33)arrayOf(Manifest.permission.READ_MEDIA_IMAGES,Manifest.permission.READ_MEDIA_VIDEO)else arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE);if(p.any{ContextCompat.checkSelfPermission(context,it)!=PackageManager.PERMISSION_GRANTED})permission.launch(p)}
 fun saveNas(v:NasSettings){nas=(nas.filterNot{it.id==v.id}+v);store.saveConnections(nas)}
 fun deleteNas(v:NasSettings){nas=nas.filterNot{it.id==v.id};profiles=profiles.filterNot{it.host==v.host&&it.share==v.share};store.saveConnections(nas);store.saveProfiles(profiles)}
 fun saveProfile(v:NasSettings){profiles=profiles.filterNot{it.id==v.id}+v;store.saveProfiles(profiles)}
 fun runProfile(p:NasSettings){scope.launch{running=true;status="Synchronisiere "+p.profileName.ifBlank{"Profil"}+" …";val r=runCatching{SyncService(context).run(p){_,_,n->status=n}};status=r.fold({"Fertig · "+it.copied+" übertragen · "+it.failed+" Fehler"},{"Fehler · "+(it.message?:"Unbekannt")});running=false}}
 if(editor==Editor.NAS){NasEditor(editing?:NasSettings(),{editor=Editor.NONE},{saveNas(it);editor=Editor.NONE});return}
 if(editor==Editor.SYNC){SyncEditor(editing,nas,{editor=Editor.NONE},{saveProfile(it);editor=Editor.NONE});return}
 Scaffold(containerColor=Navy,bottomBar={NavigationBar(containerColor=Color(0xFF091F2D)){Tab.entries.forEach{t->NavigationBarItem(selected=tab==t,onClick={tab=t},icon={Text(t.icon,fontSize=20.sp)},label={Text(t.label,fontSize=10.sp)})}}}){pad->
  Box(Modifier.padding(pad).fillMaxSize()){when(tab){
   Tab.HOME->Home(nas,profiles,status,running,{profiles.filter{it.enabled}.forEach{runProfile(it)}})
   Tab.NAS->NasList(nas,{editing=null;editor=Editor.NAS},{editing=it;editor=Editor.NAS},{deleteNas(it)},{n->scope.launch{status="Teste Verbindung …";status=runCatching{withContext(Dispatchers.IO){SmbProvider(n).test()};"Verbunden"}.getOrElse{"Fehler · "+it.message}}})
   Tab.SYNC->ProfileList(profiles,running,{editing=null;editor=Editor.SYNC},{editing=it;editor=Editor.SYNC},{p->profiles=profiles.filterNot{it.id==p.id};store.saveProfiles(profiles)},{p->val v=p.copy(enabled=!p.enabled);profiles=profiles.map{if(it.id==p.id)v else it};store.saveProfiles(profiles)},{runProfile(it)})
   Tab.SETTINGS->SettingsScreen()
  }}
 }
}

@Composable private fun Header(t:String,s:String?=null){Column(Modifier.fillMaxWidth().padding(20.dp,18.dp,20.dp,10.dp)){Text(t,fontSize=25.sp,fontWeight=FontWeight.Bold);s?.let{Text(it,color=Muted,fontSize=13.sp)}}}
@Composable private fun CardBox(content:@Composable ColumnScope.()->Unit){Card(Modifier.fillMaxWidth(),shape=RoundedCornerShape(16.dp),colors=CardDefaults.cardColors(containerColor=Panel)){Column(Modifier.padding(16.dp),content=content)}}
@Composable private fun Field(label:String,value:String,on:(String)->Unit,password:Boolean=false){OutlinedTextField(value,on,label={Text(label)},modifier=Modifier.fillMaxWidth(),singleLine=true,shape=RoundedCornerShape(12.dp),colors=OutlinedTextFieldDefaults.colors(focusedContainerColor=Panel,unfocusedContainerColor=Panel,focusedTextColor=Color.White,unfocusedTextColor=Color.White,focusedLabelColor=Blue,unfocusedLabelColor=Muted,focusedBorderColor=Blue,unfocusedBorderColor=Color(0xFF345568),cursorColor=Blue),visualTransformation=if(password)PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None)}
@Composable private fun BackTitle(title:String,onBack:()->Unit){Row(Modifier.fillMaxWidth().padding(12.dp),verticalAlignment=Alignment.CenterVertically){TextButton(onClick=onBack){Text("‹",fontSize=30.sp)};Text(title,fontSize=22.sp,fontWeight=FontWeight.Bold)}}

@Composable private fun Home(nas:List<NasSettings>,profiles:List<NasSettings>,status:String,running:Boolean,onAll:()->Unit){Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())){Box(Modifier.fillMaxWidth().height(190.dp).background(Brush.verticalGradient(listOf(Color(0xFF154E6A),Navy)))){Column(Modifier.align(Alignment.BottomStart).padding(24.dp)){Text("GalerieSync",fontSize=30.sp,fontWeight=FontWeight.Bold);Text("Deine Fotos. Sicher. Überall.",color=Muted)}};Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){CardBox{Text("Synchronisationen",fontWeight=FontWeight.Bold);Text(profiles.count{it.enabled}.toString()+" aktiv · "+profiles.size+" insgesamt",color=if(profiles.any{it.enabled})Green else Muted)};CardBox{Text("NAS-Verbindungen",fontWeight=FontWeight.Bold);Text(nas.size.toString()+" gespeichert",color=Muted)};CardBox{Text("Status",fontWeight=FontWeight.Bold);Text(status,color=Muted)};Button(onClick=onAll,enabled=!running&&profiles.any{it.enabled},modifier=Modifier.fillMaxWidth()){Text(if(running)"Synchronisiere …" else "Alle aktiven synchronisieren")}}}}

@Composable private fun NasList(items:List<NasSettings>,onAdd:()->Unit,onEdit:(NasSettings)->Unit,onDelete:(NasSettings)->Unit,onTest:(NasSettings)->Unit){Column(Modifier.fillMaxSize()){Header("NAS","Verbindungen verwalten");Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)){if(items.isEmpty())CardBox{Text("Noch keine NAS-Verbindung",fontWeight=FontWeight.Bold);Text("Lege SMB und optional UGREENlink-Zugangsdaten an.",color=Muted)};items.forEach{n->CardBox{Row(verticalAlignment=Alignment.CenterVertically){Text("▣",fontSize=30.sp);Spacer(Modifier.width(12.dp));Column(Modifier.weight(1f)){Text(n.name.ifBlank{n.host},fontWeight=FontWeight.Bold);Text(n.host+" · "+n.share,color=Muted,fontSize=12.sp);if(n.ugreenlinkId.isNotBlank())Text("UGREENlink hinterlegt",color=Green,fontSize=12.sp)}};Row(horizontalArrangement=Arrangement.spacedBy(6.dp)){TextButton(onClick={onTest(n)}){Text("Test")};TextButton(onClick={onEdit(n)}){Text("Bearbeiten")};TextButton(onClick={onDelete(n)}){Text("Löschen",color=MaterialTheme.colorScheme.error)}}}};Button(onClick=onAdd,modifier=Modifier.fillMaxWidth()){Text("+  Neue NAS-Verbindung")}}}}

@Composable private fun ProfileList(items:List<NasSettings>,running:Boolean,onAdd:()->Unit,onEdit:(NasSettings)->Unit,onDelete:(NasSettings)->Unit,onToggle:(NasSettings)->Unit,onRun:(NasSettings)->Unit){Column(Modifier.fillMaxSize()){Header("Synchronisationen","Ordner, Richtung und Ziel frei verwalten");Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)){if(items.isEmpty())CardBox{Text("Noch keine Synchronisation",fontWeight=FontWeight.Bold);Text("Erstelle beliebig viele Profile und wähle pro Profil eigene Ordner.",color=Muted)};items.forEach{p->CardBox{Row(verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text(p.profileName.ifBlank{syncProfileTitle(p.direction)},fontWeight=FontWeight.Bold);Text(p.sourceFolders.size.toString()+" Ordner · "+syncDirectionText(p.direction),color=Muted,fontSize=12.sp);Text((p.name.ifBlank{p.host})+"  →  /"+p.remoteFolder,color=Muted,fontSize=12.sp)};Switch(p.enabled,{onToggle(p)})};Row{TextButton(onClick={onRun(p)},enabled=!running&&p.enabled){Text("Jetzt")};TextButton(onClick={onEdit(p)}){Text("Bearbeiten")};TextButton(onClick={onDelete(p)}){Text("Löschen",color=MaterialTheme.colorScheme.error)}}}};Button(onClick=onAdd,enabled=true,modifier=Modifier.fillMaxWidth()){Text("+  Neue Synchronisation")}}}}

@Composable private fun NasEditor(initial:NasSettings,onBack:()->Unit,onSave:(NasSettings)->Unit){var s by remember{mutableStateOf(initial)};var shares by remember{mutableStateOf(emptyList<String>())};var msg by remember{mutableStateOf("")};var busy by remember{mutableStateOf(false)};val scope=rememberCoroutineScope();Column(Modifier.fillMaxSize().background(Navy)){BackTitle(if(initial.host.isBlank())"NAS hinzufügen" else "NAS bearbeiten",onBack);Column(Modifier.padding(horizontal=16.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(14.dp)){CardBox{Text("Lokale NAS-Verbindung",fontWeight=FontWeight.Bold,fontSize=18.sp);Text("Verbinde GalerieSync direkt über SMB im lokalen Netzwerk.",color=Muted,fontSize=12.sp);Spacer(Modifier.height(12.dp));Field("Name",s.name,{s=s.copy(name=it)});Field("Server / IP-Adresse",s.host,{s=s.copy(host=it)});Field("Benutzername",s.username,{s=s.copy(username=it)});Field("Passwort",s.password,{s=s.copy(password=it)},true)};Button(onClick={scope.launch{busy=true;msg=runCatching{shares=withContext(Dispatchers.IO){SmbProvider(s.copy(share="")).listShares()};"✓ Verbindung erfolgreich"}.getOrElse{"✕ "+(it.message?:it.javaClass.simpleName)};busy=false}},enabled=!busy&&s.host.isNotBlank()&&s.username.isNotBlank(),modifier=Modifier.fillMaxWidth()){Text(if(busy)"Suche Freigaben …" else "SMB verbinden & Freigaben suchen")};if(msg.isNotBlank())Text(msg,color=if(msg.startsWith("✓"))Green else MaterialTheme.colorScheme.error);shares.forEach{sh->Card(Modifier.fillMaxWidth().clickable{s=s.copy(share=sh)},colors=CardDefaults.cardColors(containerColor=if(s.share==sh)Color(0xFF17466A) else Panel)){Row(Modifier.padding(12.dp),verticalAlignment=Alignment.CenterVertically){Text(sh,Modifier.weight(1f));RadioButton(s.share==sh,{s=s.copy(share=sh)})}}};Field("SMB-Freigabe",s.share,{s=s.copy(share=it)});CardBox{Row(verticalAlignment=Alignment.CenterVertically){Text("UGREENlink",fontWeight=FontWeight.Bold,fontSize=18.sp);Spacer(Modifier.weight(1f));Text("OPTIONAL",color=Blue,fontSize=11.sp,fontWeight=FontWeight.Bold)};Text("Fernzugang für unterwegs. Zugangsdaten werden verschlüsselt gespeichert.",color=Muted,fontSize=12.sp);Spacer(Modifier.height(10.dp));Field("UGREENlink-ID",s.ugreenlinkId,{s=s.copy(ugreenlinkId=it)});Field("UGREEN Benutzername",s.ugreenUsername,{s=s.copy(ugreenUsername=it)});Field("UGREEN Passwort",s.ugreenPassword,{s=s.copy(ugreenPassword=it)},true)};Button(onClick={onSave(s.copy(name=s.name.ifBlank{s.host}))},enabled=s.host.isNotBlank()&&s.share.isNotBlank(),modifier=Modifier.fillMaxWidth()){Text("NAS-Verbindung speichern")}}}}

@Composable private fun SyncEditor(existing:NasSettings?,connections:List<NasSettings>,onBack:()->Unit,onSave:(NasSettings)->Unit){var step by remember{mutableIntStateOf(0)};var selected by remember{mutableStateOf(existing?:connections.firstOrNull())};var p by remember{mutableStateOf(existing?:connections.firstOrNull()?.copy(id=java.util.UUID.randomUUID().toString(),profileName="",sourceFolders=emptySet(),remoteFolder="GalerieSync")?:NasSettings())};var folders by remember{mutableStateOf(emptyList<MediaFolder>())};val context=LocalContext.current;LaunchedEffect(step){if(step==1)folders=withContext(Dispatchers.IO){MediaScanner(context.contentResolver).folderMedia()}};Column(Modifier.fillMaxSize()){BackTitle(if(existing==null)"Neue Synchronisation" else "Synchronisation bearbeiten",onBack);Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)){if(connections.isEmpty()){CardBox{Text("Zuerst eine NAS hinzufügen",fontWeight=FontWeight.Bold);Text("Synchronisationen benötigen eine gespeicherte NAS-Verbindung.",color=Muted)}}else if(step==0){Field("Name der Synchronisation",p.profileName,{p=p.copy(profileName=it)});Text("NAS auswählen",fontWeight=FontWeight.Bold);connections.forEach{n->val active=selected?.id==n.id;Card(Modifier.fillMaxWidth().clickable{selected=n;p=p.copy(host=n.host,share=n.share,username=n.username,password=n.password,domain=n.domain,name=n.name,ugreenlinkId=n.ugreenlinkId,ugreenUsername=n.ugreenUsername,ugreenPassword=n.ugreenPassword)},colors=CardDefaults.cardColors(containerColor=if(active)Color(0xFF17466A) else Panel)){Row(Modifier.padding(12.dp),verticalAlignment=Alignment.CenterVertically){Text(n.name.ifBlank{n.host},Modifier.weight(1f),fontWeight=FontWeight.Bold);RadioButton(active,null)}}};Text("Richtung",fontWeight=FontWeight.Bold);DirectionCard("Handy → NAS",p.direction==SyncDirection.PHONE_TO_NAS){p=p.copy(direction=SyncDirection.PHONE_TO_NAS)};DirectionCard("NAS → Handy",p.direction==SyncDirection.NAS_TO_PHONE){p=p.copy(direction=SyncDirection.NAS_TO_PHONE)};DirectionCard("Bidirektional",p.direction==SyncDirection.BIDIRECTIONAL){p=p.copy(direction=SyncDirection.BIDIRECTIONAL)};Button(onClick={step=1},modifier=Modifier.fillMaxWidth()){Text("Ordner auswählen")}}else{Text("Quellordner auf dem Smartphone",fontWeight=FontWeight.Bold);Text("Nur der Ordnername wird angezeigt. Die vollständige Struktur bleibt intern für den Sync erhalten.",color=Muted,fontSize=12.sp);folders.forEach{f->val active=f.path in p.sourceFolders;Card(Modifier.fillMaxWidth().clickable{p=p.copy(sourceFolders=if(active)p.sourceFolders-f.path else p.sourceFolders+f.path)},shape=RoundedCornerShape(16.dp),colors=CardDefaults.cardColors(containerColor=if(active)Color(0xFF17466A) else Panel)){Column(Modifier.padding(12.dp)){Row(verticalAlignment=Alignment.CenterVertically){Checkbox(active,null);Column{Text(f.name,fontWeight=FontWeight.Bold,fontSize=17.sp);Text(f.count.toString()+" Dateien",color=Muted,fontSize=12.sp)}};if(f.previews.isNotEmpty()){Spacer(Modifier.height(8.dp));Row(horizontalArrangement=Arrangement.spacedBy(5.dp)){f.previews.take(4).forEach{u->AsyncImage(model=u,contentDescription=null,contentScale=ContentScale.Crop,modifier=Modifier.weight(1f).height(68.dp))}}}}};Field("Zielordner auf der NAS",p.remoteFolder,{p=p.copy(remoteFolder=it)});Button(onClick={onSave(p.copy(profileName=p.profileName.ifBlank{syncProfileTitle(p.direction)}))},enabled=p.sourceFolders.isNotEmpty()&&p.remoteFolder.isNotBlank(),modifier=Modifier.fillMaxWidth()){Text("Synchronisation speichern")}}}}}

}
@Composable private fun DirectionCard(t:String,on:Boolean,click:()->Unit){Card(Modifier.fillMaxWidth().clickable{click()},colors=CardDefaults.cardColors(containerColor=if(on)Color(0xFF17466A) else Panel)){Row(Modifier.padding(12.dp),verticalAlignment=Alignment.CenterVertically){Text(t,Modifier.weight(1f));RadioButton(on,click)}}}
@Composable private fun SettingsScreen(){val context=LocalContext.current;Column(Modifier.fillMaxSize()){Header("Einstellungen");Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){CardBox{Text("Automatische Synchronisation",fontWeight=FontWeight.Bold);Text("Alle aktivierten Profile im WLAN ausführen.",color=Muted);Button(onClick={schedule(context)},modifier=Modifier.fillMaxWidth()){Text("Automatik aktivieren (15 min)")}};CardBox{Text("Über die App",fontWeight=FontWeight.Bold);Text("GalerieSync · Version 0.4.0",color=Muted)}}}}
private fun syncDirectionText(d:SyncDirection)=when(d){SyncDirection.PHONE_TO_NAS->"Handy → NAS";SyncDirection.NAS_TO_PHONE->"NAS → Handy";SyncDirection.BIDIRECTIONAL->"Bidirektional"}
private fun syncProfileTitle(d:SyncDirection)=when(d){SyncDirection.PHONE_TO_NAS->"Handy → NAS";SyncDirection.NAS_TO_PHONE->"NAS → Handy";SyncDirection.BIDIRECTIONAL->"Bidirektionale Synchronisation"}
fun schedule(context:android.content.Context){val c=Constraints.Builder().setRequiredNetworkType(NetworkType.UNMETERED).build();val r=PeriodicWorkRequestBuilder<SyncWorker>(15,TimeUnit.MINUTES).setConstraints(c).build();WorkManager.getInstance(context).enqueueUniquePeriodicWork("GalerieSync",ExistingPeriodicWorkPolicy.UPDATE,r)}
