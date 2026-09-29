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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.work.*
import de.galeriesync.data.*
import de.galeriesync.sync.SmbProvider
import de.galeriesync.sync.SyncService
import de.galeriesync.worker.SyncWorker
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

private val Navy=Color(0xFF071A27); private val Panel=Color(0xFF102A3A); private val Panel2=Color(0xFF17384B)
private val Blue=Color(0xFF3DA5FF); private val Green=Color(0xFF42D77D); private val Muted=Color(0xFF9CB1BF)
private val Scheme=darkColorScheme(primary=Blue,background=Navy,surface=Panel,onBackground=Color.White,onSurface=Color.White)

class MainActivity:ComponentActivity(){override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);setContent{MaterialTheme(colorScheme=Scheme){GalerieSyncApp()}}}}

enum class AppTab(val title:String,val glyph:String){HOME("Übersicht","⌂"),SYNC("Synchronisation","↻"),NAS("NAS","▣"),SETTINGS("Einstellungen","⚙")}

@Composable fun GalerieSyncApp(){
 val context=LocalContext.current; val store=remember{SettingsStore(context)}; var settings by remember{mutableStateOf(store.load())}; var tab by remember{mutableStateOf(AppTab.HOME)}; var setup by remember{mutableStateOf(false)}; var status by remember{mutableStateOf("Bereit")}; var running by remember{mutableStateOf(false)}; var progress by remember{mutableStateOf(0f)}; val scope=rememberCoroutineScope()
 val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()){}
 LaunchedEffect(Unit){val p=if(Build.VERSION.SDK_INT>=33)arrayOf(Manifest.permission.READ_MEDIA_IMAGES,Manifest.permission.READ_MEDIA_VIDEO)else arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE);if(p.any{ContextCompat.checkSelfPermission(context,it)!=PackageManager.PERMISSION_GRANTED})permission.launch(p)}
 fun save(v:NasSettings){settings=v;store.save(v)}
 fun sync(){scope.launch{running=true;progress=0f;status="Synchronisiere …";val r=runCatching{SyncService(context).run(settings){c,t,n->progress=if(t==0)0f else c.toFloat()/t;status=n}};status=r.fold({"Fertig · "+it.copied+" übertragen · "+it.failed+" Fehler"},{"Fehler · "+(it.message?:"Unbekannt")});progress=if(r.isSuccess)1f else progress;running=false}}
 if(setup){SetupScreen(settings,::save,{setup=false},{setup=false;tab=AppTab.SYNC});return}
 Scaffold(containerColor=Navy,bottomBar={NavigationBar(containerColor=Color(0xFF091F2D)){AppTab.entries.forEach{t->NavigationBarItem(selected=tab==t,onClick={tab=t},icon={Text(t.glyph,fontSize=20.sp)},label={Text(t.title,fontSize=10.sp)})}}}){pad->
  Box(Modifier.padding(pad).fillMaxSize()){when(tab){
   AppTab.HOME->HomeScreen(settings,status,running,progress,{sync()},{setup=true})
   AppTab.SYNC->SyncProfiles(settings,status,running,{sync()},{setup=true})
   AppTab.NAS->NasScreen(settings,{setup=true},{scope.launch{status="Teste Verbindung …";status=runCatching{SmbProvider(settings).test();"Verbunden"}.getOrElse{"Fehler · "+it.message}}})
   AppTab.SETTINGS->SettingsScreen(settings,::save)
  }}
 }
}

@Composable private fun Header(title:String,subtitle:String?=null){Column(Modifier.fillMaxWidth().padding(20.dp,18.dp,20.dp,10.dp)){Text(title,fontSize=25.sp,fontWeight=FontWeight.Bold);subtitle?.let{Text(it,color=Muted,fontSize=13.sp)}}}
@Composable private fun BoxCard(modifier:Modifier=Modifier,content:@Composable ColumnScope.()->Unit){Card(modifier.fillMaxWidth(),shape=RoundedCornerShape(14.dp),colors=CardDefaults.cardColors(containerColor=Panel)){Column(Modifier.padding(16.dp),content=content)}}
@Composable private fun Label(text:String){Text(text,color=Muted,fontSize=12.sp)}
@Composable private fun StatusDot(active:Boolean=true){Text("●",color=if(active)Green else Muted,fontSize=13.sp)}

@Composable private fun HomeScreen(s:NasSettings,status:String,running:Boolean,progress:Float,onSync:()->Unit,onSetup:()->Unit){
 Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())){
  Box(Modifier.fillMaxWidth().height(220.dp).background(Brush.verticalGradient(listOf(Color(0xFF154E6A),Navy)))){Column(Modifier.align(Alignment.BottomStart).padding(24.dp)){Text("GalerieSync",fontSize=30.sp,fontWeight=FontWeight.Bold);Text("Deine Fotos. Sicher. Überall.",color=Color.White.copy(.78f))}}
  Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
   BoxCard(Modifier.clickable{onSetup()}){Row(verticalAlignment=Alignment.CenterVertically){StatusDot(s.host.isNotBlank());Spacer(Modifier.width(9.dp));Column(Modifier.weight(1f)){Text("Aktive Synchronisation",fontWeight=FontWeight.SemiBold);Text(if(s.host.isBlank())"Noch kein NAS eingerichtet" else "1 aktiv · "+directionText(s.direction),color=if(s.host.isBlank())Muted else Green,fontSize=13.sp)};Text("›",fontSize=28.sp,color=Muted)}}
   BoxCard{Label("LETZTE SYNCHRONISATION");Spacer(Modifier.height(7.dp));Text(if(status=="Bereit")"Noch keine Synchronisation in dieser Sitzung" else status,fontWeight=FontWeight.SemiBold);if(running){Spacer(Modifier.height(12.dp));LinearProgressIndicator(progress={progress},Modifier.fillMaxWidth())}}
   Button(onClick=onSync,enabled=!running&&s.host.isNotBlank(),modifier=Modifier.fillMaxWidth().height(52.dp),shape=RoundedCornerShape(10.dp)){Text(if(running)"↻  Synchronisiere …" else "↻  Jetzt synchronisieren",fontWeight=FontWeight.Bold)}
   Text("NAS",fontWeight=FontWeight.Bold);BoxCard{Row(verticalAlignment=Alignment.CenterVertically){Text("▣",fontSize=28.sp);Spacer(Modifier.width(12.dp));Column{Text(if(s.host.isBlank())"NAS hinzufügen" else s.host,fontWeight=FontWeight.Bold);Text(if(s.share.isBlank())"SMB-Verbindung konfigurieren" else s.share+" · SMB",color=Muted,fontSize=13.sp)}}}
  }
 }
}

@Composable private fun SyncProfiles(s:NasSettings,status:String,running:Boolean,onSync:()->Unit,onAdd:()->Unit){Column(Modifier.fillMaxSize()){Header("Synchronisationen","Profile und Übertragungen");Column(Modifier.padding(horizontal=16.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)){BoxCard{Row{Column(Modifier.weight(1f)){Text(profileTitle(s.direction),fontWeight=FontWeight.Bold);Text("Handy-Medien  →  /"+s.remoteFolder,color=Muted,fontSize=13.sp);Spacer(Modifier.height(8.dp));Row{StatusDot(s.host.isNotBlank());Text(if(s.host.isBlank())" Nicht konfiguriert" else " Aktiv · "+status,color=if(s.host.isBlank())Muted else Green,fontSize=12.sp)}};Text("↻",fontSize=25.sp,color=Blue)}};Button(onClick=onSync,enabled=!running&&s.host.isNotBlank(),modifier=Modifier.fillMaxWidth()){Text(if(running)"Synchronisiere …" else "Jetzt synchronisieren")};OutlinedButton(onClick=onAdd,modifier=Modifier.fillMaxWidth()){Text("+  Neue Synchronisation")};Text("In dieser Testphase wird nichts automatisch gelöscht.",color=Muted,fontSize=12.sp)}}}

@Composable private fun NasScreen(s:NasSettings,onEdit:()->Unit,onTest:()->Unit){Column(Modifier.fillMaxSize()){Header("NAS","Verbindungen verwalten");Column(Modifier.padding(horizontal=16.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)){BoxCard{Row(verticalAlignment=Alignment.CenterVertically){Text("▣",fontSize=32.sp);Spacer(Modifier.width(12.dp));Column(Modifier.weight(1f)){Text(if(s.host.isBlank())"UGREEN NAS" else s.host,fontWeight=FontWeight.Bold);Text((s.host.ifBlank{"Nicht eingerichtet"})+" · SMB",color=Muted,fontSize=13.sp);Row{StatusDot(s.host.isNotBlank());Text(if(s.host.isBlank())" Konfiguration erforderlich" else " Bereit",color=if(s.host.isBlank())Muted else Green,fontSize=12.sp)}}}};BoxCard{Text("Verbindungsdetails",fontWeight=FontWeight.Bold);Spacer(Modifier.height(12.dp));Detail("Server / IP",s.host.ifBlank{"—"});Detail("Freigabe",s.share.ifBlank{"—"});Detail("Zielordner","/"+s.remoteFolder);Detail("Protokoll","SMB 2/3")};Button(onClick=onTest,enabled=s.host.isNotBlank(),modifier=Modifier.fillMaxWidth()){Text("✓  Verbindung testen")};OutlinedButton(onClick=onEdit,modifier=Modifier.fillMaxWidth()){Text("✎  Bearbeiten")}}}}
@Composable private fun Detail(a:String,b:String){Row(Modifier.fillMaxWidth().padding(vertical=5.dp)){Text(a,color=Muted,modifier=Modifier.weight(1f));Text(b)}}

@Composable private fun SettingsScreen(s:NasSettings,onSave:(NasSettings)->Unit){val context=LocalContext.current;Column(Modifier.fillMaxSize()){Header("Einstellungen");Column(Modifier.padding(horizontal=16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){BoxCard{Text("Allgemein",fontWeight=FontWeight.Bold);Text("Design, Benachrichtigungen, Sprache",color=Muted,fontSize=13.sp)};BoxCard{Row(verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text("WLAN-Einstellungen",fontWeight=FontWeight.Bold);Text("Synchronisation auf WLAN beschränken",color=Muted,fontSize=13.sp)};Switch(s.wifiOnly,{onSave(s.copy(wifiOnly=it))})}};BoxCard{Text("Automatische Synchronisation",fontWeight=FontWeight.Bold);Text("Hintergrund-Sync mit WorkManager",color=Muted,fontSize=13.sp);Spacer(Modifier.height(10.dp));Button(onClick={schedule(context)},modifier=Modifier.fillMaxWidth()){Text("Automatik aktivieren (15 min)")}};BoxCard{Text("Über die App",fontWeight=FontWeight.Bold);Text("GalerieSync · Version 0.2.0 UI Preview",color=Muted,fontSize=13.sp)}}}}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun SetupScreen(initial:NasSettings,onSave:(NasSettings)->Unit,onBack:()->Unit,onDone:()->Unit){var s by remember{mutableStateOf(initial)};var step by remember{mutableIntStateOf(0)};var test by remember{mutableStateOf("")};var testing by remember{mutableStateOf(false)};var shares by remember{mutableStateOf(emptyList<String>())};var manualShare by remember{mutableStateOf(false)};val scope=rememberCoroutineScope()
 Scaffold(containerColor=Navy,topBar={TopAppBar(title={Text(when(step){0->"NAS hinzufügen";1->"Neue Synchronisation";else->"Ordner auswählen"})},navigationIcon={TextButton(onClick={if(step>0)step-- else onBack()}){Text("‹",fontSize=30.sp)}})}){pad->Column(Modifier.padding(pad).padding(16.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)){
  if(step==0){Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){Protocol("SMB",true);Protocol("WebDAV",false);Protocol("SFTP",false);Protocol("UGREENlink",false)};Field("Name","UGREEN NAS",{},false);Field("Server / IP-Adresse",s.host,{s=s.copy(host=it)});Field("Benutzername",s.username,{s=s.copy(username=it)});OutlinedTextField(s.password,{s=s.copy(password=it)},label={Text("Passwort")},visualTransformation=PasswordVisualTransformation(),modifier=Modifier.fillMaxWidth(),singleLine=true);Button(enabled=!testing&&s.host.isNotBlank()&&s.username.isNotBlank(),onClick={scope.launch{testing=true;test=runCatching{shares=SmbProvider(s.copy(share="")).listShares();manualShare=shares.isEmpty();if(shares.isEmpty())"⚠ SMB erreichbar, aber Freigaben können nicht automatisch aufgelistet werden." else "✓ Verbindung erfolgreich · Freigabe auswählen"}.getOrElse{manualShare=true;"✕ SMB-Fehler: "+(it.message?:it.javaClass.simpleName)};testing=false}},modifier=Modifier.fillMaxWidth()){Text(if(testing)"Suche Freigaben …" else "Verbinden & Freigaben suchen")};if(test.isNotBlank())Text(test,color=if(test.startsWith("✓"))Green else MaterialTheme.colorScheme.error);if(shares.isNotEmpty()){Text("Gefundene Freigaben",fontWeight=FontWeight.Bold);shares.forEach{share->Card(Modifier.fillMaxWidth().clickable{s=s.copy(share=share)},colors=CardDefaults.cardColors(containerColor=if(s.share==share)Color(0xFF17466A) else Panel)){Row(Modifier.padding(14.dp),verticalAlignment=Alignment.CenterVertically){Text("▣",color=Blue);Spacer(Modifier.width(10.dp));Text(share,Modifier.weight(1f),fontWeight=FontWeight.SemiBold);RadioButton(s.share==share,{s=s.copy(share=share)})}}}};TextButton(onClick={manualShare=!manualShare}){Text(if(manualShare)"Manuelle Eingabe ausblenden" else "Freigabe nicht gefunden?")};if(manualShare){Field("SMB-Freigabe",s.share,{s=s.copy(share=it)})};Button(onClick={onSave(s);step=1},enabled=s.host.isNotBlank()&&s.share.isNotBlank(),modifier=Modifier.fillMaxWidth()){Text("Speichern & Weiter")}}
  if(step==1){Text("1   Richtung",fontWeight=FontWeight.Bold);DirectionCard("Handy → NAS","Fotos vom Smartphone auf die NAS",s.direction==SyncDirection.PHONE_TO_NAS){s=s.copy(direction=SyncDirection.PHONE_TO_NAS)};DirectionCard("NAS → Handy","Fotos von der NAS auf das Smartphone",s.direction==SyncDirection.NAS_TO_PHONE){s=s.copy(direction=SyncDirection.NAS_TO_PHONE)};DirectionCard("Bidirektional","Beide Richtungen synchronisieren",s.direction==SyncDirection.BIDIRECTIONAL){s=s.copy(direction=SyncDirection.BIDIRECTIONAL)};Text("2   Quellen & Ziel",fontWeight=FontWeight.Bold);BoxCard{Label("QUELLORDNER (HANDY)");Text("Alle Fotos & Videos",fontWeight=FontWeight.Bold);Spacer(Modifier.height(12.dp));Label("ZIELORDNER (NAS)");Text("/"+s.remoteFolder,fontWeight=FontWeight.Bold)};Button(onClick={step=2},modifier=Modifier.fillMaxWidth()){Text("Weiter")}}
  if(step==2){Text("Quellordner (auf dem Smartphone)",fontWeight=FontWeight.Bold);BoxCard{Text("Alle Bilder & Videos",fontWeight=FontWeight.Bold);Text("Android MediaStore",color=Muted,fontSize=12.sp)};Text("Zielordner (auf der NAS)",fontWeight=FontWeight.Bold);Field("NAS-Ordner",s.remoteFolder,{s=s.copy(remoteFolder=it)});Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){Folder("Fotos");Folder("Videos");Folder("Backups");Folder("Familie")};Button(onClick={onSave(s);onDone()},modifier=Modifier.fillMaxWidth()){Text("Synchronisation speichern")}}
 }}
}
@Composable private fun Protocol(t:String,active:Boolean){Surface(color=if(active)Blue else Panel2,shape=RoundedCornerShape(7.dp)){Text(t,Modifier.padding(10.dp,7.dp),fontSize=11.sp,color=if(active)Color.White else Muted)}}
@Composable private fun Field(label:String,value:String,on:(String)->Unit,enabled:Boolean=true){OutlinedTextField(value,on,label={Text(label)},modifier=Modifier.fillMaxWidth(),singleLine=true,enabled=enabled)}
@Composable private fun DirectionCard(title:String,sub:String,selected:Boolean,onClick:()->Unit){Card(Modifier.fillMaxWidth().clickable{onClick()},colors=CardDefaults.cardColors(containerColor=if(selected)Color(0xFF17466A) else Panel),shape=RoundedCornerShape(12.dp)){Row(Modifier.padding(14.dp),verticalAlignment=Alignment.CenterVertically){Text(if(selected)"↻" else "▣",fontSize=25.sp,color=Blue);Spacer(Modifier.width(12.dp));Column(Modifier.weight(1f)){Text(title,fontWeight=FontWeight.Bold);Text(sub,color=Muted,fontSize=11.sp)};RadioButton(selected,onClick)}}}
@Composable private fun RowScope.Folder(name:String){Card(Modifier.weight(1f),colors=CardDefaults.cardColors(containerColor=Panel2)){Column(Modifier.padding(8.dp),horizontalAlignment=Alignment.CenterHorizontally){Text("📁",fontSize=25.sp);Text(name,fontSize=10.sp)}}}
private fun directionText(d:SyncDirection)=when(d){SyncDirection.PHONE_TO_NAS->"Handy → NAS";SyncDirection.NAS_TO_PHONE->"NAS → Handy";SyncDirection.BIDIRECTIONAL->"Bidirektional"}
private fun profileTitle(d:SyncDirection)=when(d){SyncDirection.PHONE_TO_NAS->"Fotos Handy → NAS";SyncDirection.NAS_TO_PHONE->"Fotos NAS → Handy";SyncDirection.BIDIRECTIONAL->"Fotos bidirektional"}
fun schedule(context:android.content.Context){val c=Constraints.Builder().setRequiredNetworkType(NetworkType.UNMETERED).build();val r=PeriodicWorkRequestBuilder<SyncWorker>(15,TimeUnit.MINUTES).setConstraints(c).build();WorkManager.getInstance(context).enqueueUniquePeriodicWork("GalerieSync",ExistingPeriodicWorkPolicy.UPDATE,r)}
