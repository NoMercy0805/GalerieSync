package de.galeriesync

import android.Manifest
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.os.Build
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.core.app.NotificationCompat
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
import androidx.core.view.WindowCompat
import androidx.work.*
import coil.compose.AsyncImage
import de.galeriesync.data.*
import de.galeriesync.sync.MediaFolder
import de.galeriesync.sync.MediaScanner
import de.galeriesync.sync.SmbProvider
import de.galeriesync.sync.SmbStorageInfo
import de.galeriesync.sync.SyncService
import de.galeriesync.worker.SyncWorker
import de.galeriesync.vpn.VpnManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

private fun currentWifiName(context:android.content.Context):String?{val cm=context.getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager;val network=cm?.activeNetwork;val caps=network?.let{cm.getNetworkCapabilities(it)};if(caps?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI)!=true)return null;val wm=context.applicationContext.getSystemService(android.content.Context.WIFI_SERVICE) as? WifiManager?:return null;return runCatching{wm.connectionInfo?.ssid?.trim('"')}.getOrNull()?.takeIf{it.isNotBlank()&&it!="<unknown ssid>"}}

private val Navy=Color(0xFF071A27);private val Panel=Color(0xFF102A3A);private val Panel2=Color(0xFF17384B)
private val Blue=Color(0xFF3DA5FF);private val Green=Color(0xFF42D77D);private val Muted=Color(0xFF9CB1BF)
private val Scheme=darkColorScheme(primary=Blue,onPrimary=Color.White,background=Navy,surface=Panel,surfaceVariant=Panel2,onBackground=Color.White,onSurface=Color.White,onSurfaceVariant=Color.White,outline=Color(0xFF345568))
private val LightScheme=lightColorScheme(primary=Color(0xFF0878C9),onPrimary=Color.White,background=Color(0xFFF3F7FA),surface=Color.White,surfaceVariant=Color(0xFFE3EEF5),onBackground=Color(0xFF10212C),onSurface=Color(0xFF10212C),onSurfaceVariant=Color(0xFF405665),outline=Color(0xFF78909C))
class MainActivity:ComponentActivity(){override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);setContent{val store=remember{SettingsStore(this)};var mode by remember{mutableStateOf(store.loadThemeMode())};val systemDark=androidx.compose.foundation.isSystemInDarkTheme();val dark=mode=="DARK"||(mode=="SYSTEM"&&systemDark);SideEffect{window.statusBarColor=if(dark)android.graphics.Color.rgb(7,26,39) else android.graphics.Color.rgb(243,247,250);window.navigationBarColor=if(dark)android.graphics.Color.rgb(9,31,45) else android.graphics.Color.WHITE;WindowCompat.getInsetsController(window,window.decorView).apply{isAppearanceLightStatusBars=!dark;isAppearanceLightNavigationBars=!dark}};CompositionLocalProvider(LocalThemeMode provides mode,LocalSetThemeMode provides{v->mode=v;store.saveThemeMode(v)}){MaterialTheme(colorScheme=if(dark)Scheme else LightScheme){App()}}}}}
private val LocalThemeMode=compositionLocalOf{"SYSTEM"}
private val LocalSetThemeMode=compositionLocalOf<(String)->Unit>{{}}
enum class Tab(val label:String,val icon:String){HOME("Übersicht","⌂"),SYNC("Synchronisation","↻"),NAS("NAS","▣"),SETTINGS("Einstellungen","⚙")}
enum class Editor{NONE,NAS,SYNC}

@Composable fun App(){
 val context=LocalContext.current;val store=remember{SettingsStore(context)}
 var nas by remember{mutableStateOf(store.loadConnections())};var profiles by remember{mutableStateOf(store.loadProfiles())}
 var tab by remember{mutableStateOf(Tab.HOME)};var showHistory by remember{mutableStateOf(false)};var historyProfile by remember{mutableStateOf<NasSettings?>(null)};var editor by remember{mutableStateOf(Editor.NONE)};var settingsPage by remember{mutableStateOf<String?>(null)};var editing by remember{mutableStateOf<NasSettings?>(null)}
 var status by remember{mutableStateOf("Bereit")};var profileLastSync by remember{mutableStateOf(store.loadProfileLastSync())};var running by remember{mutableStateOf(false)};var syncCurrent by remember{mutableIntStateOf(0)};var syncTotal by remember{mutableIntStateOf(0)};var syncDoneBytes by remember{mutableLongStateOf(0L)};var syncTotalBytes by remember{mutableLongStateOf(0L)};var syncFile by remember{mutableStateOf("")};var syncJob by remember{mutableStateOf<Job?>(null)};var nasTestStates by remember{mutableStateOf<Map<String,Boolean>>(emptyMap())};var nasStorage by remember{mutableStateOf(store.loadNasStorage().mapValues{SmbStorageInfo(it.value.first,it.value.second)})};var nasStorageUpdated by remember{mutableStateOf(store.loadNasStorageUpdated())};val scope=rememberCoroutineScope()
 val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()){}

 val vpnPermission=rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()){status=if(it.resultCode==android.app.Activity.RESULT_OK)"VPN-Freigabe erteilt ✓" else "VPN-Freigabe nicht erteilt"}
 LaunchedEffect(Unit){val p=if(Build.VERSION.SDK_INT>=33)arrayOf(Manifest.permission.READ_MEDIA_IMAGES,Manifest.permission.READ_MEDIA_VIDEO)else arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE);if(p.any{ContextCompat.checkSelfPermission(context,it)!=PackageManager.PERMISSION_GRANTED})permission.launch(p)}
 fun saveNas(v:NasSettings){nas=(nas.filterNot{it.id==v.id}+v);store.saveConnections(nas)}
 fun deleteNas(v:NasSettings){nas=nas.filterNot{it.id==v.id};profiles=profiles.filterNot{it.host==v.host&&it.share==v.share};nasStorage=nasStorage-v.id;nasStorageUpdated=nasStorageUpdated-v.id;store.removeNasStorage(v.id);store.saveConnections(nas);store.saveProfiles(profiles)}
 fun saveProfile(v:NasSettings){profiles=profiles.filterNot{it.id==v.id}+v;store.saveProfiles(profiles)}
 BackHandler(enabled=showHistory||editor!=Editor.NONE||settingsPage!=null||running){if(showHistory){showHistory=false;historyProfile=null}else if(editor!=Editor.NONE){editor=Editor.NONE;editing=null}else if(settingsPage!=null)settingsPage=null else if(running)tab=Tab.HOME}
 fun syncNotification(text:String,progress:Int?=null,done:Boolean=false){if(!store.loadNotificationsEnabled())return;val nm=context.getSystemService(android.content.Context.NOTIFICATION_SERVICE) as NotificationManager;val channel="galeriesync_sync";if(Build.VERSION.SDK_INT>=26)nm.createNotificationChannel(NotificationChannel(channel,"Synchronisation",NotificationManager.IMPORTANCE_LOW));val n=NotificationCompat.Builder(context,channel).setSmallIcon(android.R.drawable.stat_sys_upload).setContentTitle(if(done)"GalerieSync" else "GalerieSync synchronisiert").setContentText(text).setOnlyAlertOnce(!done).setOngoing(!done).apply{if(progress!=null)setProgress(100,progress.coerceIn(0,100),false)}.build();nm.notify(if(done)4102 else 4101,n)}
  fun runProfile(p:NasSettings){if(running)return;if(VpnManager.needsVpn(context)){val intent=VpnManager.permissionIntent(context);if(intent!=null){status="VPN-Freigabe erforderlich";vpnPermission.launch(intent);return}};syncJob=scope.launch{running=true;syncCurrent=0;syncTotal=0;syncDoneBytes=0;syncTotalBytes=0;syncFile="";var startedVpn=false;syncNotification("Synchronisation wird gestartet …",0);status="Synchronisiere "+p.profileName.ifBlank{"Profil"}+" …";val r=runCatching{try{startedVpn=VpnManager.connectIfNeeded(context){status=it};if(startedVpn)VpnManager.waitForSmb(p.host){status=it};SyncService(context).run(p){sp->syncCurrent=sp.doneFiles;syncTotal=sp.totalFiles;syncDoneBytes=sp.doneBytes;syncTotalBytes=sp.totalBytes;syncFile=sp.message;status=sp.message;val pct=if(sp.totalBytes>0)((sp.doneBytes*100/sp.totalBytes).toInt()) else if(sp.totalFiles>0)(sp.doneFiles*100/sp.totalFiles) else 0;syncNotification(if(sp.totalFiles>0)"${sp.doneFiles} von ${sp.totalFiles} Dateien · ${formatBytes(sp.doneBytes)} / ${formatBytes(sp.totalBytes)}" else sp.message,pct)}}finally{if(startedVpn||VpnManager.isUp())VpnManager.disconnect()}};status=r.fold({result->if(result.failed==0)"Fertig über ${if(startedVpn)"SMB + VPN" else result.transport} · ${result.copied} übertragen · ${result.skipped} übersprungen" else "SMB · ${result.copied} übertragen · ${result.failed} Fehler · ${result.lastError?:"Details unbekannt"}"},{val raw=it.message.orEmpty();if(raw.contains("Datei:"))raw else "Synchronisation unterbrochen · Verbindung zur NAS verloren. Bitte erneut versuchen."});syncNotification(status,null,true);val stamp=java.text.SimpleDateFormat("dd.MM.yyyy HH:mm",java.util.Locale.GERMANY).format(java.util.Date());store.addSyncHistory("$stamp · [${p.id}] · ${p.profileName.ifBlank{syncProfileTitle(p.direction)}} · ${syncCurrent}/${syncTotal} Dateien · ${formatBytes(syncDoneBytes)} · $status");val last="$stamp · "+if(r.isSuccess)"Erfolgreich ✓" else "Fehlgeschlagen";profileLastSync=profileLastSync+(p.id to last);store.saveProfileLastSync(p.id,last);running=false;syncFile=""}}
 if(showHistory){val hp=historyProfile;val all=store.loadSyncHistory();val filtered=if(hp==null)all else all.filter{it.contains("[${hp.id}]")};SyncHistoryScreen(filtered,hp?.profileName?.ifBlank{syncProfileTitle(hp.direction)}){showHistory=false;historyProfile=null};return}
 if(editor==Editor.NAS){NasEditor(editing?:NasSettings(),{editor=Editor.NONE},{saveNas(it);editor=Editor.NONE});return}
 if(editor==Editor.SYNC){SyncEditor(editing,nas,{editor=Editor.NONE},{saveProfile(it);editor=Editor.NONE});return}
 
 Scaffold(containerColor=MaterialTheme.colorScheme.background,bottomBar={NavigationBar(containerColor=MaterialTheme.colorScheme.surface){Tab.entries.forEach{t->NavigationBarItem(selected=tab==t,onClick={tab=t},icon={Text(t.icon,fontSize=20.sp)},label={Text(t.label,fontSize=10.sp)})}}}){pad->
  Box(Modifier.padding(pad).fillMaxSize()){when(tab){
   Tab.HOME->Home(nas,profiles,status,running,syncCurrent,syncTotal,syncFile,{val enabled=profiles.filter{it.enabled};if(enabled.isNotEmpty()&&!running){syncJob=scope.launch{for(p in enabled){while(running)kotlinx.coroutines.delay(100);runProfile(p);while(running)kotlinx.coroutines.delay(100)}}}})
   Tab.NAS->NasList(nas,nasTestStates,nasStorage,nasStorageUpdated,{editing=null;editor=Editor.NAS},{editing=it;editor=Editor.NAS},{deleteNas(it)},{n->scope.launch{status="NAS wird aktualisiert …";val result=runCatching{withContext(Dispatchers.IO){val smb=SmbProvider(n);smb.test();val info=smb.storageInfo();nasStorage=nasStorage+(n.id to info);store.saveNasStorage(n.id,info.total,info.free);val now=System.currentTimeMillis();nasStorageUpdated=nasStorageUpdated+(n.id to now);store.saveNasStorageUpdated(n.id,now)}};nasTestStates=nasTestStates+(n.id to result.isSuccess);status=result.fold({"NAS-Speicher aktualisiert"},{"Fehler · "+(it.message?:"Unbekannt")})}})
   Tab.SYNC->ProfileList(profiles,profileLastSync,running,syncCurrent,syncTotal,syncDoneBytes,syncTotalBytes,syncFile,status,{editing=null;editor=Editor.SYNC},{editing=it;editor=Editor.SYNC},{p->profiles=profiles.filterNot{it.id==p.id};store.saveProfiles(profiles)},{p->val v=p.copy(enabled=!p.enabled);profiles=profiles.map{if(it.id==p.id)v else it};store.saveProfiles(profiles)},{runProfile(it)},{p->historyProfile=p;showHistory=true},{syncJob?.cancel();syncJob=null;running=false;syncFile="";status="Synchronisation abgebrochen";if(VpnManager.isUp())scope.launch{VpnManager.disconnect()}})
   Tab.SETTINGS->SettingsScreen(settingsPage,{settingsPage=it})
  }}
 }
}

@Composable private fun appBg()=MaterialTheme.colorScheme.background
@Composable private fun appPanel()=MaterialTheme.colorScheme.surface
@Composable private fun appText()=MaterialTheme.colorScheme.onSurface
@Composable private fun appMuted()=MaterialTheme.colorScheme.onSurfaceVariant
@Composable private fun Header(t:String,s:String?=null){Column(Modifier.fillMaxWidth().padding(20.dp,18.dp,20.dp,10.dp)){Text(t,fontSize=25.sp,fontWeight=FontWeight.Bold);s?.let{Text(it,color=MaterialTheme.colorScheme.onSurfaceVariant,fontSize=13.sp)}}}
@Composable private fun CardBox(content:@Composable ColumnScope.()->Unit){Card(Modifier.fillMaxWidth(),shape=RoundedCornerShape(16.dp),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface),content={Column(Modifier.padding(16.dp),content=content)})}
@Composable private fun Field(label:String,value:String,on:(String)->Unit,password:Boolean=false){OutlinedTextField(value,on,label={Text(label)},modifier=Modifier.fillMaxWidth(),singleLine=true,shape=RoundedCornerShape(12.dp),colors=OutlinedTextFieldDefaults.colors(focusedContainerColor=MaterialTheme.colorScheme.surface,unfocusedContainerColor=MaterialTheme.colorScheme.surface,focusedTextColor=MaterialTheme.colorScheme.onSurface,unfocusedTextColor=MaterialTheme.colorScheme.onSurface,focusedLabelColor=MaterialTheme.colorScheme.primary,unfocusedLabelColor=MaterialTheme.colorScheme.onSurfaceVariant,focusedBorderColor=MaterialTheme.colorScheme.primary,unfocusedBorderColor=MaterialTheme.colorScheme.outline,cursorColor=MaterialTheme.colorScheme.primary),visualTransformation=if(password)PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None)}
@Composable private fun BackTitle(title:String,onBack:()->Unit){Row(Modifier.fillMaxWidth().padding(12.dp),verticalAlignment=Alignment.CenterVertically){TextButton(onClick=onBack){Text("‹",fontSize=30.sp,color=MaterialTheme.colorScheme.primary)};Text(title,fontSize=22.sp,fontWeight=FontWeight.Bold,color=MaterialTheme.colorScheme.onBackground)}}

@Composable private fun Home(nas:List<NasSettings>,profiles:List<NasSettings>,status:String,running:Boolean,current:Int,total:Int,file:String,onAll:()->Unit){
 Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())){
  Box(Modifier.fillMaxWidth().height(220.dp).background(Brush.verticalGradient(listOf(Color(0xFF15506B),Color(0xFF0D3449),Navy)))){Column(Modifier.align(Alignment.BottomStart).padding(24.dp)){Text("GalerieSync",fontSize=32.sp,fontWeight=FontWeight.Bold,color=Color.White);Text("Deine Fotos. Sicher. Überall.",color=MaterialTheme.colorScheme.onSurfaceVariant,fontSize=16.sp)}}
  Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
   CardBox{Row(verticalAlignment=Alignment.CenterVertically){Text("●",color=if(profiles.any{it.enabled})Green else Muted,fontSize=16.sp);Spacer(Modifier.width(10.dp));Column(Modifier.weight(1f)){Text("Aktive Synchronisationen",fontWeight=FontWeight.Bold,color=MaterialTheme.colorScheme.onSurface);Text(profiles.count{it.enabled}.toString()+" aktiv",color=if(profiles.any{it.enabled})Green else Muted)}}}
   Card(Modifier.fillMaxWidth(),shape=RoundedCornerShape(16.dp),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface)){Column(Modifier.padding(16.dp)){Text(if(running)"Synchronisation läuft" else "Letzte Synchronisation / Status",fontWeight=FontWeight.Bold,color=MaterialTheme.colorScheme.onSurface);if(running&&total>0){val pct=(current*100/total.coerceAtLeast(1));LinearProgressIndicator(progress={current.toFloat()/total.coerceAtLeast(1)},modifier=Modifier.fillMaxWidth().padding(vertical=10.dp));Text("$pct % · $current von $total Dateien",color=MaterialTheme.colorScheme.onSurface);Text(file,color=MaterialTheme.colorScheme.onSurfaceVariant,fontSize=12.sp)}else Text(status,color=MaterialTheme.colorScheme.onSurfaceVariant)}}
   CardBox{Text("NAS-Verbindungen",fontWeight=FontWeight.Bold,color=MaterialTheme.colorScheme.onSurface);Text(nas.size.toString()+" gespeichert",color=MaterialTheme.colorScheme.onSurfaceVariant)}
   Button(onClick=onAll,enabled=!running&&profiles.any{it.enabled},modifier=Modifier.fillMaxWidth().height(54.dp)){Text(if(running)"Synchronisiere …" else "↻  Jetzt synchronisieren",color=Color.White,fontWeight=FontWeight.Bold)}
  }
 }
}

@Composable private fun SyncProgressScreen(profile:NasSettings,current:Int,total:Int,file:String,status:String,onBack:()->Unit,onCancel:()->Unit){
 Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)){BackTitle("Synchronisation",onBack);Column(Modifier.fillMaxSize().padding(24.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(18.dp)){
  Spacer(Modifier.height(20.dp));Box(Modifier.size(170.dp),contentAlignment=Alignment.Center){CircularProgressIndicator(progress={if(total>0)current.toFloat()/total else 0f},modifier=Modifier.fillMaxSize(),strokeWidth=10.dp);Text("↻",fontSize=54.sp,color=Color.White)}
  Text("Synchronisiere …",fontSize=24.sp,fontWeight=FontWeight.Bold,color=Color.White);Text(if(total>0)"$current von $total Dateien" else "Dateien werden vorbereitet …",color=MaterialTheme.colorScheme.onSurfaceVariant)
  CardBox{Text(file.ifBlank{status.ifBlank{"Verbindung wird vorbereitet …"}},color=Color.White,fontWeight=FontWeight.SemiBold);Text(if(file.isBlank())status.ifBlank{"Warte …"} else "Wird synchronisiert …",color=if(status.contains("Fehler",true))MaterialTheme.colorScheme.error else Blue,fontSize=12.sp)}
  Spacer(Modifier.weight(1f));OutlinedButton(onClick=onCancel,modifier=Modifier.fillMaxWidth().height(54.dp)){Text("Abbrechen",color=Color.White)}
 }}
}

@Composable private fun NasList(items:List<NasSettings>,testStates:Map<String,Boolean>,storage:Map<String,SmbStorageInfo>,updated:Map<String,Long>,onAdd:()->Unit,onEdit:(NasSettings)->Unit,onDelete:(NasSettings)->Unit,onTest:(NasSettings)->Unit){Column(Modifier.fillMaxSize()){Header("NAS","Verbindungen verwalten");Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)){if(items.isEmpty())CardBox{Text("Noch keine NAS-Verbindung",fontWeight=FontWeight.Bold);Text("Lege eine SMB-Verbindung zu deiner NAS an.",color=MaterialTheme.colorScheme.onSurfaceVariant)};items.forEach{n->CardBox{Row(verticalAlignment=Alignment.CenterVertically){val testState=testStates[n.id];Text("▣",fontSize=30.sp,color=when(testState){true->Green;false->MaterialTheme.colorScheme.error;null->MaterialTheme.colorScheme.onSurface});Spacer(Modifier.width(12.dp));Column(Modifier.weight(1f)){Text(n.name.ifBlank{n.host},fontWeight=FontWeight.Bold);Text(n.host+" · "+n.share,color=MaterialTheme.colorScheme.onSurfaceVariant,fontSize=12.sp);storage[n.id]?.let{s->Spacer(Modifier.height(8.dp));if(s.total>0){val used=s.used.toFloat()/s.total;LinearProgressIndicator(progress={used},modifier=Modifier.fillMaxWidth());Text("${formatBytes(s.used)} belegt · ${formatBytes(s.free)} frei · ${formatBytes(s.total)} gesamt",color=MaterialTheme.colorScheme.onSurfaceVariant,fontSize=11.sp)}else{Text("${formatBytes(s.free)} frei",color=MaterialTheme.colorScheme.onSurfaceVariant,fontSize=11.sp)};updated[n.id]?.let{t->Text("Zuletzt aktualisiert: "+java.text.SimpleDateFormat("dd.MM.yyyy HH:mm",java.util.Locale.GERMANY).format(java.util.Date(t)),color=MaterialTheme.colorScheme.onSurfaceVariant,fontSize=10.sp)}}}};Row(horizontalArrangement=Arrangement.spacedBy(6.dp)){TextButton(onClick={onTest(n)}){Text(if(storage[n.id]!=null)"Aktualisieren" else "Speicher aktualisieren")};TextButton(onClick={onEdit(n)}){Text("Bearbeiten")};TextButton(onClick={onDelete(n)}){Text("Löschen",color=MaterialTheme.colorScheme.error)}}}};Button(onClick=onAdd,modifier=Modifier.fillMaxWidth()){Text("+  Neue NAS-Verbindung")}}}}

@Composable private fun ProfileList(items:List<NasSettings>,lastSync:Map<String,String>,running:Boolean,current:Int,total:Int,doneBytes:Long,totalBytes:Long,file:String,status:String,onAdd:()->Unit,onEdit:(NasSettings)->Unit,onDelete:(NasSettings)->Unit,onToggle:(NasSettings)->Unit,onRun:(NasSettings)->Unit,onHistory:(NasSettings)->Unit,onCancel:()->Unit){Column(Modifier.fillMaxSize()){Header("Synchronisationen","Ordner, Richtung und Ziel frei verwalten");Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)){if(running){CardBox{Text("Synchronisation läuft",fontWeight=FontWeight.Bold);if(totalBytes>0)LinearProgressIndicator(progress={(doneBytes.toDouble()/totalBytes).toFloat().coerceIn(0f,1f)},modifier=Modifier.fillMaxWidth().padding(vertical=10.dp)) else if(total>0)LinearProgressIndicator(progress={current.toFloat()/total.coerceAtLeast(1)},modifier=Modifier.fillMaxWidth().padding(vertical=10.dp)) else LinearProgressIndicator(modifier=Modifier.fillMaxWidth().padding(vertical=10.dp));if(total>0)Text("$current erledigt · ${(total-current).coerceAtLeast(0)} offen · $total gesamt",color=MaterialTheme.colorScheme.onSurfaceVariant);if(totalBytes>0)Text("${formatBytes(doneBytes)} von ${formatBytes(totalBytes)}",color=MaterialTheme.colorScheme.onSurfaceVariant,fontSize=12.sp);if(file.isNotBlank())Text(file,color=MaterialTheme.colorScheme.onSurfaceVariant,fontSize=11.sp);OutlinedButton(onClick=onCancel,modifier=Modifier.fillMaxWidth().padding(top=8.dp)){Text("Synchronisation abbrechen")}}};if(items.isEmpty())CardBox{Text("Noch keine Synchronisation",fontWeight=FontWeight.Bold);Text("Erstelle beliebig viele Profile und wähle pro Profil eigene Ordner.",color=MaterialTheme.colorScheme.onSurfaceVariant)};items.forEach{p->CardBox{Row(verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text(p.profileName.ifBlank{syncProfileTitle(p.direction)},fontWeight=FontWeight.Bold);Text(syncDirectionText(p.direction)+" · "+p.sourceFolders.size.toString()+" Ordner",color=MaterialTheme.colorScheme.onSurfaceVariant,fontSize=12.sp);Text((p.name.ifBlank{p.host})+"  →  /"+p.remoteFolder,color=MaterialTheme.colorScheme.onSurfaceVariant,fontSize=12.sp);Text(if(p.enabled)"● Aktiv" else "○ Inaktiv",color=if(p.enabled)Green else Muted,fontSize=12.sp);lastSync[p.id]?.let{Text("Zuletzt: $it",color=MaterialTheme.colorScheme.onSurfaceVariant,fontSize=11.sp)}};Switch(p.enabled,{onToggle(p)})};var menu by remember{mutableStateOf(false)};Row(verticalAlignment=Alignment.CenterVertically){Button(onClick={onRun(p)},enabled=!running&&p.enabled,modifier=Modifier.weight(1f)){Text("Jetzt synchronisieren")};TextButton(onClick={onHistory(p)}){Text("Verlauf")};Box{TextButton(onClick={menu=true}){Text("⋮",fontSize=22.sp)};DropdownMenu(expanded=menu,onDismissRequest={menu=false}){DropdownMenuItem(text={Text("Bearbeiten")},onClick={menu=false;onEdit(p)});DropdownMenuItem(text={Text("Löschen",color=MaterialTheme.colorScheme.error)},onClick={menu=false;onDelete(p)})}}}}};Button(onClick=onAdd,enabled=true,modifier=Modifier.fillMaxWidth()){Text("+  Neue Synchronisation")}}}}

@Composable private fun NasEditor(initial:NasSettings,onBack:()->Unit,onSave:(NasSettings)->Unit){
 var s by remember{mutableStateOf(initial)};var shares by remember{mutableStateOf(emptyList<String>())};var msg by remember{mutableStateOf("")};var ugreenMsg by remember{mutableStateOf("")};var busy by remember{mutableStateOf(false)};var ugreenBusy by remember{mutableStateOf(false)};val scope=rememberCoroutineScope()
 Column(Modifier.fillMaxSize().background(Navy)){
  BackTitle(if(initial.host.isBlank())"NAS hinzufügen" else "NAS bearbeiten",onBack)
  Column(Modifier.padding(20.dp,16.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)){
    Text("Lokale Verbindung",color=Color.White,fontWeight=FontWeight.Bold,fontSize=19.sp)
    Text("SMB im lokalen Netzwerk",color=MaterialTheme.colorScheme.onSurfaceVariant,fontSize=12.sp)
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
 Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)){
  BackTitle(if(existing==null)"Neue Synchronisation" else "Synchronisation bearbeiten",onBack)
  Box(Modifier.weight(1f)){
  Column(Modifier.fillMaxSize().padding(16.dp).padding(bottom=if(step==1)118.dp else 0.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)){
   if(connections.isEmpty()){
    CardBox{Text("Zuerst eine NAS hinzufügen",color=Color.White,fontWeight=FontWeight.Bold);Text("Synchronisationen benötigen eine gespeicherte NAS-Verbindung.",color=MaterialTheme.colorScheme.onSurfaceVariant)}
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
     Column(Modifier.weight(1f)){Text("Ordner auswählen",color=Color.White,fontWeight=FontWeight.Bold,fontSize=20.sp);Text("Fotos & Videos auf dem Smartphone",color=MaterialTheme.colorScheme.onSurfaceVariant,fontSize=12.sp)}
     Surface(shape=RoundedCornerShape(20.dp),color=Panel2){Text(p.sourceFolders.size.toString()+" gewählt",Modifier.padding(10.dp,6.dp),color=Blue,fontSize=12.sp,fontWeight=FontWeight.Bold)}
    }
    val allSelected=folders.isNotEmpty()&&p.sourceFolders.containsAll(folders.map{it.path})
    TextButton(onClick={p=p.copy(sourceFolders=if(allSelected)emptySet() else folders.map{it.path}.toSet())},enabled=folders.isNotEmpty(),modifier=Modifier.fillMaxWidth()){
     Text(if(allSelected)"Auswahl aufheben" else "Alle Ordner auswählen",color=Blue,fontWeight=FontWeight.SemiBold)
    }
    folders.sortedWith(compareBy<MediaFolder>({folderGroupOrder(it.path)},{folderRelativeSortKey(it.path).lowercase(java.util.Locale.GERMANY)})).forEach{f->
     val active=f.path in p.sourceFolders
     Card(Modifier.fillMaxWidth().clickable{p=p.copy(sourceFolders=if(active)p.sourceFolders-f.path else p.sourceFolders+f.path)},shape=RoundedCornerShape(16.dp),colors=CardDefaults.cardColors(containerColor=if(active)Color(0xFF17466A) else Panel)){
      Column(Modifier.padding(12.dp)){
       Row(verticalAlignment=Alignment.CenterVertically){Checkbox(active,null);Spacer(Modifier.width(4.dp));Column{Text(f.name,fontWeight=FontWeight.Bold,fontSize=17.sp,color=Color.White);Text(f.count.toString()+" Dateien",color=MaterialTheme.colorScheme.onSurfaceVariant,fontSize=12.sp)}}
       if(f.previews.isNotEmpty()){Spacer(Modifier.height(8.dp));Row(horizontalArrangement=Arrangement.spacedBy(5.dp)){f.previews.take(4).forEach{u->AsyncImage(model=u,contentDescription=null,contentScale=ContentScale.Crop,modifier=Modifier.weight(1f).height(68.dp))}}}
      }
     }
    }

   }
  }
  if(step==1){
   Surface(modifier=Modifier.align(Alignment.BottomCenter).fillMaxWidth(),shadowElevation=12.dp,color=MaterialTheme.colorScheme.surface){
    Column(Modifier.padding(horizontal=16.dp,vertical=10.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
     Field("Basisordner auf der NAS",p.remoteFolder,{p=p.copy(remoteFolder=it)})
     if(p.sourceFolders.isNotEmpty()){
      val first=p.sourceFolders.sorted().first();val clean=first.trim('/');val parts=clean.split('/').filter{it.isNotBlank()};val rest=if(parts.firstOrNull()?.lowercase() in setOf("dcim","pictures","movies"))parts.drop(1).joinToString("/") else clean
      Text("NAS: "+p.remoteFolder.trimEnd('/')+"/Bilder & Videos/"+rest+(if(p.sourceFolders.size>1)"  +${p.sourceFolders.size-1} weitere" else ""),color=MaterialTheme.colorScheme.onSurfaceVariant,fontSize=11.sp,maxLines=1)
     }
     Button(onClick={onSave(p.copy(profileName=p.profileName.ifBlank{syncProfileTitle(p.direction)}))},enabled=p.sourceFolders.isNotEmpty()&&p.remoteFolder.isNotBlank(),modifier=Modifier.fillMaxWidth()){Text("Synchronisation speichern")}
    }
   }
  }
 }
 }
}

@Composable private fun DirectionCard(t:String,on:Boolean,click:()->Unit){Card(Modifier.fillMaxWidth().clickable{click()},shape=RoundedCornerShape(14.dp),colors=CardDefaults.cardColors(containerColor=if(on)Color(0xFF17466A) else Panel)){Row(Modifier.padding(16.dp),verticalAlignment=Alignment.CenterVertically){Text(t,Modifier.weight(1f),fontWeight=FontWeight.SemiBold,color=Color.White);RadioButton(on,click)}}}
@Composable private fun SettingsScreen(page:String?,open:(String?)->Unit){val context=LocalContext.current
 if(page!=null){Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)){BackTitle(page){open(null)};Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)){when(page){
  "Allgemein"->ThemeSettings()
  "WLAN-Einstellungen"->VpnFallbackSettings(context)
  "Automatische Synchronisation"->AutoSyncSettings(context)
  "Daten & Speicher"->CardBox{Text("Lokale Daten",color=Color.White,fontWeight=FontWeight.Bold);Text("NAS-Zugangsdaten werden verschlüsselt gespeichert. Spiegel-Profile können entfernte Dateien auf der Gegenseite ebenfalls entfernen.",color=MaterialTheme.colorScheme.onSurfaceVariant)}
  else->CardBox{Text("GalerieSync",color=Color.White,fontWeight=FontWeight.Bold,fontSize=20.sp);Text("Version 0.4.0",color=MaterialTheme.colorScheme.onSurfaceVariant);Text("Fotos und Videos zwischen Smartphone und NAS synchronisieren.",color=MaterialTheme.colorScheme.onSurfaceVariant)}
 }}};return}
 Column(Modifier.fillMaxSize()){Header("Einstellungen");Column(Modifier.padding(horizontal=16.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(2.dp)){
  SettingsRow("⚙","Allgemein","Design, Benachrichtigungen, Sprache"){open("Allgemein")}
  SettingsRow("◉","WLAN & VPN","Vertrauenswürdige WLANs · VPN-Fallback"){open("WLAN-Einstellungen")}
  SettingsRow("◷","Automatische Synchronisation","Zeitplan · Hintergrund"){open("Automatische Synchronisation")}
  SettingsRow("▣","Daten & Speicher","Speicherorte, Cache, Datenbank"){open("Daten & Speicher")}
  SettingsRow("ⓘ","Über die App","GalerieSync · Version 0.4.0"){open("Über die App")}
 }}}
@Composable private fun ThemeSettings(){val context=LocalContext.current;val store=remember{SettingsStore(context)};val mode=LocalThemeMode.current;val setMode=LocalSetThemeMode.current;var notifications by remember{mutableStateOf(store.loadNotificationsEnabled())};Column(verticalArrangement=Arrangement.spacedBy(12.dp)){CardBox{Text("Darstellung",fontWeight=FontWeight.Bold);Text("Wähle das Erscheinungsbild von GalerieSync.",color=MaterialTheme.colorScheme.onSurfaceVariant,fontSize=12.sp);listOf("SYSTEM" to "System","LIGHT" to "Hell","DARK" to "Dunkel").forEach{(v,label)->Row(Modifier.fillMaxWidth().clickable{setMode(v)}.padding(vertical=8.dp),verticalAlignment=Alignment.CenterVertically){RadioButton(mode==v,{setMode(v)});Spacer(Modifier.width(8.dp));Text(label)}}};CardBox{Text("Benachrichtigungen",fontWeight=FontWeight.Bold);Text("Zeigt Start, Fortschritt und Ergebnis von Synchronisationen an.",color=MaterialTheme.colorScheme.onSurfaceVariant,fontSize=12.sp);Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Text("Sync-Benachrichtigungen",Modifier.weight(1f));Switch(checked=notifications,onCheckedChange={on->if(on&&Build.VERSION.SDK_INT>=33&&ContextCompat.checkSelfPermission(context,Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED){androidx.core.app.ActivityCompat.requestPermissions(context as android.app.Activity,arrayOf(Manifest.permission.POST_NOTIFICATIONS),7001)};notifications=on;store.saveNotificationsEnabled(on)})}}}}
@Composable private fun VpnFallbackSettings(context:android.content.Context){
 val store=remember{SettingsStore(context)};var trusted by remember{mutableStateOf(store.loadTrustedWifi())};var config by remember{mutableStateOf(store.loadVpnConfig())};var current by remember{mutableStateOf(currentWifiName(context))};var message by remember{mutableStateOf("")};var testing by remember{mutableStateOf(false)};var confirmRemove by remember{mutableStateOf(false)};val scope=rememberCoroutineScope()
 val wifiPermission=rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()){grants->if(grants.values.all{it}){current=currentWifiName(context);message=if(current!=null)"WLAN erkannt: $current" else "WLAN-Name konnte nicht gelesen werden. Prüfe, ob WLAN und Standortdienste aktiv sind."}else message="WLAN-Berechtigung wurde nicht erteilt."}
 val importConfig=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){uri->if(uri!=null){config=runCatching{context.contentResolver.openInputStream(uri)?.bufferedReader()?.use{it.readText()}.orEmpty()}.getOrDefault("");if(config.isNotBlank()){store.saveVpnConfig(config);message="WireGuard-Konfiguration importiert ✓"}}}
 fun refreshWifi(){message="WLAN wird erkannt …";val needed=if(Build.VERSION.SDK_INT>=33)arrayOf(Manifest.permission.NEARBY_WIFI_DEVICES,Manifest.permission.ACCESS_FINE_LOCATION)else arrayOf(Manifest.permission.ACCESS_FINE_LOCATION);if(needed.any{ContextCompat.checkSelfPermission(context,it)!=PackageManager.PERMISSION_GRANTED})wifiPermission.launch(needed) else {current=currentWifiName(context);message=if(current!=null)"WLAN erkannt: $current" else "WLAN-Name konnte nicht gelesen werden. Prüfe, ob WLAN und Standortdienste aktiv sind."}}
 LaunchedEffect(Unit){refreshWifi()}
 Column(verticalArrangement=Arrangement.spacedBy(12.dp)){
  CardBox{
   Text("Heim-WLANs",color=Color.White,fontWeight=FontWeight.Bold,fontSize=18.sp)
   Text("In diesen WLANs verbindet sich GalerieSync direkt per SMB. In allen anderen WLANs und über Mobilfunk wird automatisch das FRITZ!Box-VPN verwendet.",color=MaterialTheme.colorScheme.onSurfaceVariant,fontSize=12.sp)
   Spacer(Modifier.height(10.dp))
   Text(current?.let{"Aktuell verbunden: $it"}?:"Aktuelles WLAN nicht erkannt",color=if(current!=null)Green else Muted,fontWeight=FontWeight.SemiBold)
   Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){Button(onClick={current?.let{trusted=trusted+it;store.saveTrustedWifi(trusted);message="$it als Heim-WLAN gespeichert ✓"}?:refreshWifi()},modifier=Modifier.weight(1f)){Text(if(current!=null)"Dieses WLAN hinzufügen" else "WLAN erkennen",color=Color.White)}}
   if(trusted.isNotEmpty()){Spacer(Modifier.height(8.dp));trusted.sorted().forEach{ssid->Surface(Modifier.fillMaxWidth(),shape=RoundedCornerShape(12.dp),color=Panel2){Row(Modifier.padding(12.dp),verticalAlignment=Alignment.CenterVertically){Text("◉",color=Green);Spacer(Modifier.width(10.dp));Text(ssid,Modifier.weight(1f),color=Color.White);TextButton(onClick={trusted=trusted-ssid;store.saveTrustedWifi(trusted)}){Text("Entfernen",color=MaterialTheme.colorScheme.error)}}}}}
  }
  CardBox{
   Text("FRITZ!Box VPN",color=Color.White,fontWeight=FontWeight.Bold,fontSize=18.sp)
   Text(if(config.isBlank())"Noch nicht eingerichtet" else "WireGuard-Konfiguration eingerichtet ✓",color=if(config.isBlank())Muted else Green)
   Text("Exportiere die WireGuard-Konfiguration in der FRITZ!Box und wähle anschließend einfach die .conf-Datei aus.",color=MaterialTheme.colorScheme.onSurfaceVariant,fontSize=12.sp)
   Spacer(Modifier.height(10.dp))
   Button(onClick={importConfig.launch(arrayOf("text/plain","application/octet-stream","*/*"))},modifier=Modifier.fillMaxWidth()){Text(if(config.isBlank())"WireGuard-Datei auswählen" else "Konfiguration ersetzen",color=Color.White)}
   if(config.isNotBlank()){Button(onClick={val i=VpnManager.permissionIntent(context);if(i!=null){message="VPN-Freigabe fehlt – starte einmal einen Sync und bestätige die Android-Abfrage."}else scope.launch{testing=true;message="VPN-Test wird gestartet …";val r=runCatching{VpnManager.connectForTest(context){message=it};val targets=store.loadConnections();require(targets.isNotEmpty()){"Keine NAS-Verbindung gespeichert."};var ok=false;var detail="";for(n in targets){val r=runCatching{VpnManager.waitForSmb(n.host){message=it};withContext(Dispatchers.IO){SmbProvider(n).test()}};if(r.isSuccess){ok=true;detail=n.name.ifBlank{n.host};break}else detail=r.exceptionOrNull()?.message?:"SMB-Test fehlgeschlagen"};require(ok){detail};"VPN aktiv und NAS über SMB erreichbar ✓"};message=r.getOrElse{"VPN-Test fehlgeschlagen: "+(it.message?:"Unbekannter Fehler")};VpnManager.disconnect();testing=false}},enabled=!testing,modifier=Modifier.fillMaxWidth()){Text(if(testing)"VPN wird getestet …" else "VPN & NAS testen",color=Color.White)};Text("Der Test baut den WireGuard-Tunnel auf und prüft danach, ob eine gespeicherte NAS per SMB erreichbar ist.",color=MaterialTheme.colorScheme.onSurfaceVariant,fontSize=11.sp);TextButton(onClick={confirmRemove=true}){Text("VPN-Konfiguration entfernen",color=MaterialTheme.colorScheme.error)}}
  }
  if(message.isNotBlank())Text(message,color=if(message.contains("✓"))Green else Muted,fontSize=12.sp)
 }
 if(confirmRemove){AlertDialog(onDismissRequest={confirmRemove=false},title={Text("VPN-Konfiguration entfernen?")},text={Text("Bist du sicher? Die gespeicherte WireGuard-Konfiguration wird aus GalerieSync entfernt.")},confirmButton={TextButton(onClick={config="";store.saveVpnConfig("");message="VPN-Konfiguration entfernt";confirmRemove=false}){Text("Entfernen",color=MaterialTheme.colorScheme.error)}},dismissButton={TextButton(onClick={confirmRemove=false}){Text("Abbrechen")}})}
}
@Composable private fun SettingsRow(icon:String,title:String,subtitle:String,onClick:()->Unit){Surface(Modifier.fillMaxWidth().clickable{onClick()},color=MaterialTheme.colorScheme.surface){Row(Modifier.padding(16.dp),verticalAlignment=Alignment.CenterVertically){Text(icon,fontSize=24.sp,color=MaterialTheme.colorScheme.onSurface);Spacer(Modifier.width(14.dp));Column(Modifier.weight(1f)){Text(title,color=MaterialTheme.colorScheme.onSurface,fontWeight=FontWeight.Bold);Text(subtitle,color=MaterialTheme.colorScheme.onSurfaceVariant,fontSize=12.sp)};Text("›",color=MaterialTheme.colorScheme.onSurface,fontSize=26.sp)}}}
private fun syncDirectionText(d:SyncDirection)=when(d){SyncDirection.PHONE_TO_NAS->"Handy → NAS";SyncDirection.NAS_TO_PHONE->"NAS → Handy";SyncDirection.BIDIRECTIONAL->"Bidirektional"}
private fun syncProfileTitle(d:SyncDirection)=when(d){SyncDirection.PHONE_TO_NAS->"Handy → NAS";SyncDirection.NAS_TO_PHONE->"NAS → Handy";SyncDirection.BIDIRECTIONAL->"Bidirektionale Synchronisation"}
@Composable private fun AutoSyncSettings(context:android.content.Context){
 val store=remember{SettingsStore(context)};var value by remember{mutableStateOf(store.loadAutoSyncValue().toString())};var unit by remember{mutableStateOf(store.loadAutoSyncUnit())};var saved by remember{mutableStateOf("")};var enabled by remember{mutableStateOf(store.loadAutoSyncEnabled())};var network by remember{mutableStateOf(store.loadAutoSyncNetwork())};var charging by remember{mutableStateOf(store.loadAutoSyncCharging())}
 CardBox{
  Text("Hintergrund-Synchronisation",fontWeight=FontWeight.Bold,fontSize=18.sp)
  Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text("Automatisch synchronisieren",fontWeight=FontWeight.SemiBold);Text(if(enabled)"Aktiv" else "Deaktiviert",color=MaterialTheme.colorScheme.onSurfaceVariant,fontSize=12.sp)};Switch(checked=enabled,onCheckedChange={on->enabled=on;store.saveAutoSyncEnabled(on);if(on){val raw=value.toLongOrNull()?:15L;val minutes=when(unit){"HOURS"->raw*60;"DAYS"->raw*1440;"WEEKS"->raw*10080;else->raw}.coerceAtLeast(15);schedule(context,minutes);saved="Automatische Synchronisation aktiviert"}else{WorkManager.getInstance(context).cancelUniqueWork("GalerieSync");saved="Automatische Synchronisation deaktiviert"}})};Text("Lege fest, wie oft aktive Profile automatisch geprüft werden.",color=MaterialTheme.colorScheme.onSurfaceVariant,fontSize=12.sp)
  Spacer(Modifier.height(8.dp))
  Text("Netzwerk",fontWeight=FontWeight.SemiBold)
  Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp)){listOf("WIFI" to "Nur WLAN","ANY" to "WLAN + Mobilfunk").forEach{(v,label)->FilterChip(selected=network==v,onClick={network=v;store.saveAutoSyncNetwork(v)},label={Text(label)})}}
  Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Text("Nur beim Laden",Modifier.weight(1f));Switch(checked=charging,onCheckedChange={charging=it;store.saveAutoSyncCharging(it)})}
  Spacer(Modifier.height(8.dp))
  OutlinedTextField(value=value,onValueChange={value=it.filter(Char::isDigit).take(4)},label={Text("Intervall")},singleLine=true,modifier=Modifier.fillMaxWidth())
  Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp)){listOf("MINUTES" to "Minuten","HOURS" to "Stunden","DAYS" to "Tage","WEEKS" to "Wochen").forEach{(v,label)->FilterChip(selected=unit==v,onClick={unit=v},label={Text(label)})}}
  Text("Android führt Hintergrundarbeit nicht sekundengenau aus. Das kleinste unterstützte Intervall sind 15 Minuten.",color=MaterialTheme.colorScheme.onSurfaceVariant,fontSize=11.sp)
  Button(onClick={val raw=value.toLongOrNull()?:15L;val minutes=when(unit){"HOURS"->raw*60;"DAYS"->raw*1440;"WEEKS"->raw*10080;else->raw}.coerceAtLeast(15);store.saveAutoSyncSchedule(raw.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),unit);enabled=true;store.saveAutoSyncEnabled(true);schedule(context,minutes);saved="Automatik gespeichert · $value "+when(unit){"HOURS"->"Stunden";"DAYS"->"Tage";"WEEKS"->"Wochen";else->"Minuten"}},modifier=Modifier.fillMaxWidth()){Text("Zeitplan speichern & aktivieren")}
  if(saved.isNotBlank())Text(saved,color=Green,fontSize=12.sp)
 }
}
fun schedule(context:android.content.Context,minutes:Long){val store=SettingsStore(context);val network=if(store.loadAutoSyncNetwork()=="ANY")NetworkType.CONNECTED else NetworkType.UNMETERED;val c=Constraints.Builder().setRequiredNetworkType(network).setRequiresCharging(store.loadAutoSyncCharging()).build();val r=PeriodicWorkRequestBuilder<SyncWorker>(minutes.coerceAtLeast(15),TimeUnit.MINUTES).setConstraints(c).build();WorkManager.getInstance(context).enqueueUniquePeriodicWork("GalerieSync",ExistingPeriodicWorkPolicy.UPDATE,r)}

private fun formatBytes(v:Long):String{val locale=java.util.Locale.GERMANY;return when{v>=1_099_511_627_776L->"%.2f TB".format(locale,v/1_099_511_627_776.0);v>=1_073_741_824L->"%.2f GB".format(locale,v/1_073_741_824.0);v>=1_048_576L->"%.1f MB".format(locale,v/1_048_576.0);v>=1_024L->"%.1f KB".format(locale,v/1_024.0);else->"$v B"}}

private fun folderGroupOrder(path:String):Int{val top=path.trim('/').substringBefore('/').lowercase();return when(top){"dcim"->0;"pictures"->1;"movies"->2 else->3}}
private fun folderRelativeSortKey(path:String):String{val clean=path.trim('/');val parts=clean.split('/').filter{it.isNotBlank()};return if(parts.firstOrNull()?.lowercase() in setOf("dcim","pictures","movies"))parts.drop(1).joinToString("/") else clean}

@Composable private fun SyncHistoryScreen(items:List<String>,profileName:String?=null,onBack:()->Unit){Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)){BackTitle(profileName?.let{"Verlauf · $it"}?:"Synchronisationsverlauf",onBack);Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)){if(items.isEmpty())CardBox{Text("Noch keine Synchronisationen",fontWeight=FontWeight.Bold);Text("Ab der nächsten Synchronisation wird der Verlauf hier gespeichert.",color=MaterialTheme.colorScheme.onSurfaceVariant)} else items.forEach{entry->CardBox{Text(entry.substringBefore(" · "),fontWeight=FontWeight.Bold);Text(entry.substringAfter(" · "),color=MaterialTheme.colorScheme.onSurfaceVariant)}}}}}
