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
import androidx.activity.enableEdgeToEdge
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PermMedia
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Storage as OutlinedStorage
import androidx.compose.material.icons.outlined.Info
import androidx.compose.ui.graphics.vector.ImageVector
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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

private val Scheme=darkColorScheme(
 primary=Color(0xFF6AA6FF),onPrimary=Color(0xFF071426),
 primaryContainer=Color(0xFF24446F),onPrimaryContainer=Color(0xFFD8E7FF),
 secondary=Color(0xFF43E0A0),onSecondary=Color(0xFF071B13),
 background=Color(0xFF0D0E12),surface=Color(0xFF1C1D23),surfaceVariant=Color(0xFF292B33),
 onBackground=Color(0xFFF5F5F7),onSurface=Color(0xFFF5F5F7),onSurfaceVariant=Color(0xFFC4C6CD),
 outline=Color(0xFF666973),error=Color(0xFFFF6B55)
)
private val LightScheme=lightColorScheme(
 primary=Color(0xFF1268FF),onPrimary=Color.White,
 primaryContainer=Color(0xFFE3EDFF),onPrimaryContainer=Color(0xFF0D3C8C),
 secondary=Color(0xFF00A971),onSecondary=Color.White,
 background=Color(0xFFF6F7FB),surface=Color(0xFFFFFFFF),surfaceVariant=Color(0xFFF0F1F6),
 onBackground=Color(0xFF191A1C),onSurface=Color(0xFF191A1C),onSurfaceVariant=Color(0xFF6E7074),
 outline=Color(0xFFDADADE),error=Color(0xFFF0421F)
)
class MainActivity:ComponentActivity(){
 override fun onCreate(savedInstanceState:Bundle?){
  super.onCreate(savedInstanceState)
  enableEdgeToEdge()
  setContent{
   val store=remember{SettingsStore(this)}
   var mode by remember{mutableStateOf(store.loadThemeMode())}
   val systemDark=androidx.compose.foundation.isSystemInDarkTheme()
   val dark=mode=="DARK"||(mode=="SYSTEM"&&systemDark)
   SideEffect{
    window.statusBarColor=if(dark)android.graphics.Color.rgb(15,16,19) else android.graphics.Color.rgb(242,242,245)
    window.navigationBarColor=if(dark)android.graphics.Color.rgb(30,31,36) else android.graphics.Color.WHITE
    WindowCompat.getInsetsController(window,window.decorView).apply{isAppearanceLightStatusBars=!dark;isAppearanceLightNavigationBars=!dark}
   }
   CompositionLocalProvider(LocalThemeMode provides mode,LocalSetThemeMode provides{v->mode=v;store.saveThemeMode(v)}){
    MaterialTheme(colorScheme=if(dark)Scheme else LightScheme,shapes=Shapes(small=RoundedCornerShape(14.dp),medium=RoundedCornerShape(22.dp),large=RoundedCornerShape(28.dp))){
     Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)){
      Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)){App()}
     }
    }
   }
  }
 }
}
private val LocalThemeMode=compositionLocalOf{"SYSTEM"}
private val LocalSetThemeMode=compositionLocalOf<(String)->Unit>{{}}
enum class Tab(val label:String,val icon:String){GALLERY("Galerie","▦"),SYNC("Synchronisation","↻"),NAS("NAS","▣")}
enum class Editor{NONE,NAS,SYNC}

@Composable fun App(){
 val context=LocalContext.current;val store=remember{SettingsStore(context)}
 var nas by remember{mutableStateOf(store.loadConnections())};var profiles by remember{mutableStateOf(store.loadProfiles())}
 var tab by remember{mutableStateOf(Tab.GALLERY)};var showHistory by remember{mutableStateOf(false)};var historyProfile by remember{mutableStateOf<NasSettings?>(null)};var editor by remember{mutableStateOf(Editor.NONE)};var settingsPage by remember{mutableStateOf<String?>(null)};var editing by remember{mutableStateOf<NasSettings?>(null)}
 var status by remember{mutableStateOf("Bereit")};var profileLastSync by remember{mutableStateOf(store.loadProfileLastSync())};var running by remember{mutableStateOf(false)};var syncCurrent by remember{mutableIntStateOf(0)};var syncTotal by remember{mutableIntStateOf(0)};var syncDoneBytes by remember{mutableLongStateOf(0L)};var syncTotalBytes by remember{mutableLongStateOf(0L)};var syncFile by remember{mutableStateOf("")};var syncSpeed by remember{mutableLongStateOf(0L)};var speedBytes by remember{mutableLongStateOf(0L)};var speedTime by remember{mutableLongStateOf(System.currentTimeMillis())};var syncJob by remember{mutableStateOf<Job?>(null)};var nasTestStates by remember{mutableStateOf<Map<String,Boolean>>(emptyMap())};var nasStorage by remember{mutableStateOf(store.loadNasStorage().mapValues{SmbStorageInfo(it.value.first,it.value.second)})};var nasStorageUpdated by remember{mutableStateOf(store.loadNasStorageUpdated())};val scope=rememberCoroutineScope()
 val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()){}
 val vpnPermission=rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()){status=if(it.resultCode==android.app.Activity.RESULT_OK)"VPN-Freigabe erteilt ✓" else "VPN-Freigabe nicht erteilt"}
 LaunchedEffect(Unit){val p=if(Build.VERSION.SDK_INT>=33)arrayOf(Manifest.permission.READ_MEDIA_IMAGES,Manifest.permission.READ_MEDIA_VIDEO)else arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE);if(p.any{ContextCompat.checkSelfPermission(context,it)!=PackageManager.PERMISSION_GRANTED})permission.launch(p)}
 fun saveNas(v:NasSettings){nas=(nas.filterNot{it.id==v.id}+v);store.saveConnections(nas)}
 fun deleteNas(v:NasSettings){nas=nas.filterNot{it.id==v.id};profiles=profiles.filterNot{it.host==v.host&&it.share==v.share};nasStorage=nasStorage-v.id;nasStorageUpdated=nasStorageUpdated-v.id;store.removeNasStorage(v.id);store.saveConnections(nas);store.saveProfiles(profiles)}
 fun saveProfile(v:NasSettings){profiles.firstOrNull{it.id==v.id}?.let{old->if(old.host!=v.host||old.share!=v.share||old.remoteFolder!=v.remoteFolder){store.saveSyncSnapshot(v.id,emptySet())}else{store.releaseRemovedSourceFolders(v.id,old.sourceFolders,v.sourceFolders)}};profiles=profiles.filterNot{it.id==v.id}+v;store.saveProfiles(profiles)}
 BackHandler(enabled=showHistory||editor!=Editor.NONE||settingsPage!=null||running){if(showHistory){showHistory=false;historyProfile=null}else if(editor!=Editor.NONE){editor=Editor.NONE;editing=null}else if(settingsPage!=null)settingsPage=null else if(running)tab=Tab.GALLERY}
 fun syncNotification(text:String,progress:Int?=null,done:Boolean=false){if(!store.loadNotificationsEnabled())return;val nm=context.getSystemService(android.content.Context.NOTIFICATION_SERVICE) as NotificationManager;val channel="galeriesync_sync";if(Build.VERSION.SDK_INT>=26)nm.createNotificationChannel(NotificationChannel(channel,"Synchronisation",NotificationManager.IMPORTANCE_LOW));val n=NotificationCompat.Builder(context,channel).setSmallIcon(android.R.drawable.stat_sys_upload).setContentTitle(if(done)"GalerieSync" else "GalerieSync synchronisiert").setContentText(text).setStyle(NotificationCompat.BigTextStyle().bigText(text)).setOnlyAlertOnce(true).setSilent(true).setOngoing(!done).apply{if(progress!=null)setProgress(100,progress.coerceIn(0,100),false)}.build();nm.notify(if(done)4102 else 4101,n)}
 fun runProfile(p:NasSettings){
  if(running)return
  if(VpnManager.needsVpn(context)){val intent=VpnManager.permissionIntent(context);if(intent!=null){status="VPN-Freigabe erforderlich";vpnPermission.launch(intent);return}}
  val request=androidx.work.OneTimeWorkRequestBuilder<SyncWorker>().setInputData(androidx.work.workDataOf("profile_id" to p.id)).build()
  WorkManager.getInstance(context).enqueueUniqueWork("GalerieSync-manual",androidx.work.ExistingWorkPolicy.REPLACE,request)
  status="Synchronisation im Hintergrund gestartet · "+p.profileName.ifBlank{"Profil"};running=true
  scope.launch{var lastBytes=0L;var lastAt=System.currentTimeMillis();WorkManager.getInstance(context).getWorkInfoByIdFlow(request.id).collect{info->if(info!=null){val d=info.progress;syncCurrent=d.getInt("doneFiles",syncCurrent);syncTotal=d.getInt("totalFiles",syncTotal);val newBytes=d.getLong("doneBytes",syncDoneBytes);syncTotalBytes=d.getLong("totalBytes",syncTotalBytes);syncFile=d.getString("message")?:syncFile;val now=System.currentTimeMillis();if(now-lastAt>=750&&newBytes>=lastBytes){syncSpeed=(newBytes-lastBytes)*1000L/(now-lastAt).coerceAtLeast(1);lastBytes=newBytes;lastAt=now};syncDoneBytes=newBytes;if(info.state.isFinished){profileLastSync=store.loadProfileLastSync();running=false;syncSpeed=0;status=if(info.state==androidx.work.WorkInfo.State.SUCCEEDED)"Synchronisation abgeschlossen ✓" else "Synchronisation unterbrochen · Details siehe Benachrichtigung";return@collect}}}}
 }

 Scaffold(containerColor=Color.Transparent,topBar={Row(Modifier.fillMaxWidth().padding(top=16.dp,start=28.dp,end=28.dp,bottom=0.dp),horizontalArrangement=Arrangement.End){Surface(modifier=Modifier.size(42.dp),shape=RoundedCornerShape(14.dp),color=MaterialTheme.colorScheme.surfaceVariant,tonalElevation=0.dp,shadowElevation=0.dp){IconButton(onClick={settingsPage="ROOT"},modifier=Modifier.fillMaxSize()){Icon(Icons.Outlined.Settings,contentDescription="Einstellungen",modifier=Modifier.size(22.dp),tint=MaterialTheme.colorScheme.primary)}}}},bottomBar={Surface(modifier=Modifier.fillMaxWidth().padding(horizontal=10.dp,vertical=6.dp),shape=RoundedCornerShape(28.dp),color=MaterialTheme.colorScheme.surface,tonalElevation=0.dp,shadowElevation=2.dp){Row(Modifier.fillMaxWidth().padding(horizontal=7.dp,vertical=6.dp),horizontalArrangement=Arrangement.SpaceEvenly){Tab.entries.forEach{t->val selected=tab==t;Column(Modifier.weight(1f).clickable{tab=t}.background(if(selected)MaterialTheme.colorScheme.primaryContainer else Color.Transparent,RoundedCornerShape(25.dp)).padding(vertical=9.dp,horizontal=4.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(5.dp)){when(t){Tab.GALLERY->Icon(Icons.Rounded.PermMedia,contentDescription="Galerie",modifier=Modifier.size(23.dp),tint=if(selected)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant);Tab.NAS->Icon(Icons.Rounded.Storage,contentDescription="NAS",modifier=Modifier.size(23.dp),tint=if(selected)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant);Tab.SYNC->Icon(Icons.Outlined.Schedule,contentDescription="Sync",modifier=Modifier.size(23.dp),tint=if(selected)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)};Text(t.label,fontSize=11.sp,fontWeight=if(selected)FontWeight.Bold else FontWeight.Medium,color=if(selected)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,maxLines=1)}}}}}){pad->
  Box(Modifier.padding(bottom=pad.calculateBottomPadding()).fillMaxSize()){when(tab){
   Tab.GALLERY->de.galeriesync.gallery.NasGalleryScreen(profiles)
   Tab.NAS->NasList(nas,nasTestStates,nasStorage,nasStorageUpdated,{editing=null;editor=Editor.NAS},{editing=it;editor=Editor.NAS},{deleteNas(it)},{n->scope.launch{status="NAS wird aktualisiert …";val result=runCatching{withContext(Dispatchers.IO){val smb=SmbProvider(n);smb.test();val info=smb.storageInfo();nasStorage=nasStorage+(n.id to info);store.saveNasStorage(n.id,info.total,info.free);val now=System.currentTimeMillis();nasStorageUpdated=nasStorageUpdated+(n.id to now);store.saveNasStorageUpdated(n.id,now)}};nasTestStates=nasTestStates+(n.id to result.isSuccess);status=result.fold({"NAS-Speicher aktualisiert"},{"Fehler · "+(it.message?:"Unbekannt")})}})
   Tab.SYNC->ProfileList(profiles,profileLastSync,running,syncCurrent,syncTotal,syncDoneBytes,syncTotalBytes,syncFile,syncSpeed,status,{editing=null;editor=Editor.SYNC},{editing=it;editor=Editor.SYNC},{p->profiles=profiles.filterNot{it.id==p.id};store.saveProfiles(profiles)},{p->val v=p.copy(enabled=!p.enabled);profiles=profiles.map{if(it.id==p.id)v else it};store.saveProfiles(profiles)},{runProfile(it)},{p->historyProfile=p;showHistory=true},{WorkManager.getInstance(context).cancelUniqueWork("GalerieSync-manual");syncJob?.cancel();syncJob=null;running=false;syncFile="";status="Synchronisation abgebrochen";if(VpnManager.isUp())scope.launch{VpnManager.disconnect()}})
  }}
 }

 if(showHistory){
  val hp=historyProfile
  val all=store.loadSyncHistory()
  val filtered=if(hp==null)all else all.filter{it.contains("[${hp.id}]")}
  SyncHistoryScreen(filtered,hp?.profileName?.ifBlank{syncProfileTitle(hp.direction)}){showHistory=false;historyProfile=null}
 }
 if(editor==Editor.NAS)NasEditor(editing?:NasSettings(),{editor=Editor.NONE;editing=null},{saveNas(it);editor=Editor.NONE;editing=null})
 if(editor==Editor.SYNC)SyncEditor(editing,nas,{editor=Editor.NONE;editing=null},{saveProfile(it);editor=Editor.NONE;editing=null})
 if(settingsPage!=null)SettingsScreen(if(settingsPage=="ROOT")null else settingsPage,{settingsPage=it?:"ROOT"},{settingsPage=null})
}

@Composable private fun appBg()=MaterialTheme.colorScheme.background
@Composable private fun appPanel()=MaterialTheme.colorScheme.surface
@Composable private fun appText()=MaterialTheme.colorScheme.onSurface
@Composable private fun appMuted()=MaterialTheme.colorScheme.onSurfaceVariant
@Composable private fun Header(t:String,s:String?=null){Column(Modifier.fillMaxWidth().padding(start=24.dp,top=16.dp,end=24.dp,bottom=16.dp)){Text(t,fontSize=30.sp,fontWeight=FontWeight.Bold,color=MaterialTheme.colorScheme.onBackground);s?.let{Text(it,color=MaterialTheme.colorScheme.onSurfaceVariant,fontSize=13.sp)}}}
@Composable private fun CardBox(content:@Composable ColumnScope.()->Unit){Card(Modifier.fillMaxWidth(),shape=RoundedCornerShape(26.dp),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface),elevation=CardDefaults.cardElevation(defaultElevation=0.dp),content={Column(Modifier.padding(16.dp),content=content)})}
@Composable private fun Field(label:String,value:String,on:(String)->Unit,password:Boolean=false){OutlinedTextField(value,on,label={Text(label)},modifier=Modifier.fillMaxWidth(),singleLine=true,shape=RoundedCornerShape(18.dp),colors=OutlinedTextFieldDefaults.colors(focusedContainerColor=MaterialTheme.colorScheme.surface,unfocusedContainerColor=MaterialTheme.colorScheme.surface,focusedTextColor=MaterialTheme.colorScheme.onSurface,unfocusedTextColor=MaterialTheme.colorScheme.onSurface,focusedLabelColor=MaterialTheme.colorScheme.primary,unfocusedLabelColor=MaterialTheme.colorScheme.onSurfaceVariant,focusedBorderColor=MaterialTheme.colorScheme.primary,unfocusedBorderColor=MaterialTheme.colorScheme.outline,cursorColor=MaterialTheme.colorScheme.primary),visualTransformation=if(password)PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None)}
@Composable private fun BackTitle(title:String,onBack:()->Unit){Row(Modifier.fillMaxWidth().padding(12.dp),verticalAlignment=Alignment.CenterVertically){TextButton(onClick=onBack){Text("‹",fontSize=30.sp,color=MaterialTheme.colorScheme.primary)};Text(title,fontSize=22.sp,fontWeight=FontWeight.Bold,color=MaterialTheme.colorScheme.onBackground)}}

@Composable private fun ModalWindow(title:String,onClose:()->Unit,onBack:(()->Unit)?=null,content:@Composable BoxScope.()->Unit){
 Dialog(onDismissRequest=onClose,properties=DialogProperties(usePlatformDefaultWidth=false,dismissOnClickOutside=true)){
  Box(
   Modifier.fillMaxSize().background(Color.Black.copy(alpha=0.24f)).windowInsetsPadding(WindowInsets.safeDrawing).padding(horizontal=18.dp,vertical=28.dp),
   contentAlignment=Alignment.Center
  ){
   Surface(
    Modifier.widthIn(max=520.dp).fillMaxWidth().fillMaxHeight(0.78f),
    shape=RoundedCornerShape(26.dp),
    color=MaterialTheme.colorScheme.surface,
    tonalElevation=0.dp,
    shadowElevation=22.dp,
    border=androidx.compose.foundation.BorderStroke(1.dp,MaterialTheme.colorScheme.outline.copy(alpha=0.28f))
   ){
    Column(Modifier.fillMaxSize()){
     Box(Modifier.fillMaxWidth().padding(top=9.dp),contentAlignment=Alignment.Center){
      Surface(Modifier.width(38.dp).height(4.dp),shape=RoundedCornerShape(50),color=MaterialTheme.colorScheme.outline.copy(alpha=0.45f)){}
     }
     Row(Modifier.fillMaxWidth().padding(horizontal=12.dp,vertical=7.dp),verticalAlignment=Alignment.CenterVertically){
      if(onBack!=null){
       IconButton(onClick=onBack,modifier=Modifier.size(40.dp)){Text("‹",fontSize=30.sp,color=MaterialTheme.colorScheme.primary)}
      }else Spacer(Modifier.width(40.dp))
      Text(title,Modifier.weight(1f),fontSize=20.sp,fontWeight=FontWeight.Bold,maxLines=1,color=MaterialTheme.colorScheme.onSurface)
      IconButton(onClick=onClose,modifier=Modifier.size(40.dp)){Text("✕",fontSize=18.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)}
     }
     HorizontalDivider(color=MaterialTheme.colorScheme.outline.copy(alpha=0.22f))
     Box(Modifier.fillMaxWidth().weight(1f),content=content)
    }
   }
  }
 }
}

@Composable private fun Home(nas:List<NasSettings>,profiles:List<NasSettings>,status:String,running:Boolean,current:Int,total:Int,file:String,speed:Long,onAll:()->Unit){
 Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())){
  Box(Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=12.dp).background(MaterialTheme.colorScheme.surface,RoundedCornerShape(28.dp))){Column(Modifier.padding(start=24.dp,end=24.dp,top=26.dp,bottom=26.dp)){Text("GalerieSync",fontSize=32.sp,fontWeight=FontWeight.Bold,color=MaterialTheme.colorScheme.onBackground);Text("Deine Fotos. Sicher. Überall.",color=MaterialTheme.colorScheme.onSurfaceVariant,fontSize=16.sp)}}
  Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
   CardBox{Row(verticalAlignment=Alignment.CenterVertically){Text("●",color=if(profiles.any{it.enabled})MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,fontSize=16.sp);Spacer(Modifier.width(10.dp));Column(Modifier.weight(1f)){Text("Aktive Synchronisationen",fontWeight=FontWeight.Bold,color=MaterialTheme.colorScheme.onSurface);Text(profiles.count{it.enabled}.toString()+" aktiv",color=if(profiles.any{it.enabled})MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant)}}}
   Card(Modifier.fillMaxWidth(),shape=RoundedCornerShape(26.dp),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface)){Column(Modifier.padding(20.dp)){Text(if(running)"Synchronisation läuft" else "Letzte Synchronisation / Status",fontWeight=FontWeight.Bold,color=MaterialTheme.colorScheme.onSurface);if(running&&total>0){val pct=(current*100/total.coerceAtLeast(1));LinearProgressIndicator(progress={current.toFloat()/total.coerceAtLeast(1)},modifier=Modifier.fillMaxWidth().padding(vertical=10.dp));Text("$pct % · $current von $total Dateien",color=MaterialTheme.colorScheme.onSurface);if(speed>0)Text("Geschwindigkeit: ${formatSpeed(speed)}",color=MaterialTheme.colorScheme.primary,fontSize=12.sp);Text(file,color=MaterialTheme.colorScheme.onSurfaceVariant,fontSize=12.sp)}else Text(status,color=MaterialTheme.colorScheme.onSurfaceVariant)}}
   CardBox{Text("NAS-Verbindungen",fontWeight=FontWeight.Bold,color=MaterialTheme.colorScheme.onSurface);Text(nas.size.toString()+" gespeichert",color=MaterialTheme.colorScheme.onSurfaceVariant)}
   Button(onClick=onAll,enabled=!running&&profiles.any{it.enabled},modifier=Modifier.fillMaxWidth().height(54.dp)){Text(if(running)"Synchronisiere …" else "↻  Jetzt synchronisieren",color=Color.White,fontWeight=FontWeight.Bold)}
  }
 }
}

@Composable private fun SyncProgressScreen(profile:NasSettings,current:Int,total:Int,file:String,status:String,onBack:()->Unit,onCancel:()->Unit){
 Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)){BackTitle("Synchronisation",onBack);Column(Modifier.fillMaxSize().padding(24.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(18.dp)){
  Spacer(Modifier.height(20.dp));Box(Modifier.size(170.dp),contentAlignment=Alignment.Center){CircularProgressIndicator(progress={if(total>0)current.toFloat()/total else 0f},modifier=Modifier.fillMaxSize(),strokeWidth=10.dp);Text("↻",fontSize=54.sp,color=MaterialTheme.colorScheme.primary)}
  Text("Synchronisiere …",fontSize=24.sp,fontWeight=FontWeight.Bold,color=MaterialTheme.colorScheme.onBackground);Text(if(total>0)"$current von $total Dateien" else "Dateien werden vorbereitet …",color=MaterialTheme.colorScheme.onSurfaceVariant)
  CardBox{Text(file.ifBlank{status.ifBlank{"Verbindung wird vorbereitet …"}},color=MaterialTheme.colorScheme.onSurface,fontWeight=FontWeight.SemiBold);Text(if(file.isBlank())status.ifBlank{"Warte …"} else "Wird synchronisiert …",color=if(status.contains("Fehler",true))MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,fontSize=12.sp)}
  Spacer(Modifier.weight(1f));OutlinedButton(onClick=onCancel,modifier=Modifier.fillMaxWidth().height(54.dp)){Text("Abbrechen",color=Color.White)}
 }}
}

@Composable private fun NasList(items:List<NasSettings>,testStates:Map<String,Boolean>,storage:Map<String,SmbStorageInfo>,updated:Map<String,Long>,onAdd:()->Unit,onEdit:(NasSettings)->Unit,onDelete:(NasSettings)->Unit,onTest:(NasSettings)->Unit){
 Column(Modifier.fillMaxSize()){
  Row(Modifier.fillMaxWidth().padding(start=24.dp,end=80.dp,top=18.dp,bottom=16.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween){
   Text("NAS",fontSize=30.sp,fontWeight=FontWeight.Bold,color=MaterialTheme.colorScheme.onBackground)
   FilledTonalButton(onClick=onAdd,shape=RoundedCornerShape(18.dp),contentPadding=PaddingValues(horizontal=14.dp,vertical=10.dp)){Icon(Icons.Rounded.Add,contentDescription=null,modifier=Modifier.size(20.dp));Spacer(Modifier.width(5.dp));Text("Hinzufügen",fontSize=13.sp,fontWeight=FontWeight.SemiBold)}
  }
  Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal=16.dp,vertical=8.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){
   if(items.isEmpty()){
    CardBox{Text("Noch keine NAS-Verbindung",fontWeight=FontWeight.Bold);Text("Mit + oben rechts eine NAS-Verbindung hinzufügen.",color=MaterialTheme.colorScheme.onSurfaceVariant)}
   }
   items.forEach{n->
    val testState=testStates[n.id]
    val info=storage[n.id]
    val connected=testState==true
    Card(modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(26.dp),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface)){
     Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
      Row(verticalAlignment=Alignment.CenterVertically){
       Surface(shape=RoundedCornerShape(14.dp),color=MaterialTheme.colorScheme.primary.copy(alpha=.12f)){
        Box(Modifier.size(58.dp),contentAlignment=Alignment.Center){Icon(Icons.Rounded.Storage,contentDescription=null,modifier=Modifier.size(32.dp),tint=MaterialTheme.colorScheme.primary)}
       }
       Spacer(Modifier.width(14.dp))
       Column(Modifier.weight(1f)){
        Text(n.name.ifBlank{n.host},fontSize=20.sp,fontWeight=FontWeight.Bold,color=MaterialTheme.colorScheme.onSurface)
        Text(n.host+" · SMB",fontSize=13.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(5.dp))
        Text(when(testState){true->"● Verbunden";false->"● Verbindung fehlgeschlagen";null->"● Nicht geprüft"},
         fontSize=13.sp,fontWeight=FontWeight.SemiBold,color=when(testState){true->MaterialTheme.colorScheme.secondary;false->MaterialTheme.colorScheme.error;null->MaterialTheme.colorScheme.onSurfaceVariant})
       }
      }
      if(info!=null){
       HorizontalDivider(color=MaterialTheme.colorScheme.outline.copy(alpha=.25f))
       Text("Speicherplatz",fontSize=16.sp,fontWeight=FontWeight.Bold,color=MaterialTheme.colorScheme.onSurface)
       if(info.total>0){
        val usedFraction=(info.used.toDouble()/info.total.toDouble()).toFloat().coerceIn(0f,1f)
        Row(verticalAlignment=Alignment.CenterVertically){
         LinearProgressIndicator(progress={usedFraction},modifier=Modifier.weight(1f).height(9.dp),color=MaterialTheme.colorScheme.primary,trackColor=MaterialTheme.colorScheme.primary.copy(alpha=.16f))
         Spacer(Modifier.width(12.dp))
         Text("${(usedFraction*100).toInt()} %",fontWeight=FontWeight.Bold,color=MaterialTheme.colorScheme.onSurface)
        }
        Text("${formatBytes(info.free)} von ${formatBytes(info.total)} frei",fontSize=13.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
       }else Text("${formatBytes(info.free)} frei",fontSize=13.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
       updated[n.id]?.let{t->Text("Stand: "+java.text.SimpleDateFormat("dd.MM.yyyy HH:mm",java.util.Locale.GERMANY).format(java.util.Date(t)),fontSize=11.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)}
      }
     }
    }
    Card(modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(26.dp),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface)){
     Column{
      Row(Modifier.fillMaxWidth().clickable{onTest(n)}.padding(18.dp),verticalAlignment=Alignment.CenterVertically){
       Icon(Icons.Rounded.Refresh,contentDescription=null,modifier=Modifier.size(24.dp),tint=MaterialTheme.colorScheme.primary)
       Spacer(Modifier.width(15.dp))
       Text(if(info!=null)"Speicher aktualisieren" else "Speicher aktualisieren",modifier=Modifier.weight(1f),fontWeight=FontWeight.SemiBold)
       Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight,contentDescription=null,modifier=Modifier.size(22.dp),tint=MaterialTheme.colorScheme.onSurfaceVariant)
      }
      HorizontalDivider(Modifier.padding(horizontal=18.dp),color=MaterialTheme.colorScheme.outline.copy(alpha=.2f))
      Row(Modifier.fillMaxWidth().clickable{onEdit(n)}.padding(18.dp),verticalAlignment=Alignment.CenterVertically){
       Icon(Icons.Rounded.Edit,contentDescription=null,modifier=Modifier.size(24.dp),tint=MaterialTheme.colorScheme.primary)
       Spacer(Modifier.width(15.dp))
       Text("Bearbeiten",modifier=Modifier.weight(1f),fontWeight=FontWeight.SemiBold)
       Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight,contentDescription=null,modifier=Modifier.size(22.dp),tint=MaterialTheme.colorScheme.onSurfaceVariant)
      }
      HorizontalDivider(Modifier.padding(horizontal=18.dp),color=MaterialTheme.colorScheme.outline.copy(alpha=.2f))
      Row(Modifier.fillMaxWidth().clickable{onDelete(n)}.padding(18.dp),verticalAlignment=Alignment.CenterVertically){
       Icon(Icons.Rounded.DeleteOutline,contentDescription=null,modifier=Modifier.size(24.dp),tint=MaterialTheme.colorScheme.error)
       Spacer(Modifier.width(15.dp))
       Text("Löschen",modifier=Modifier.weight(1f),fontWeight=FontWeight.SemiBold,color=MaterialTheme.colorScheme.error)
       Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight,contentDescription=null,modifier=Modifier.size(22.dp),tint=MaterialTheme.colorScheme.onSurfaceVariant)
      }
     }
    }
   }
  }
 }
}

@Composable private fun ProfileList(items:List<NasSettings>,lastSync:Map<String,String>,running:Boolean,current:Int,total:Int,doneBytes:Long,totalBytes:Long,file:String,speed:Long,status:String,onAdd:()->Unit,onEdit:(NasSettings)->Unit,onDelete:(NasSettings)->Unit,onToggle:(NasSettings)->Unit,onRun:(NasSettings)->Unit,onHistory:(NasSettings)->Unit,onCancel:()->Unit){
 val hasCompleted=status.contains("abgeschlossen",ignoreCase=true)
 val hasError=status.contains("Fehler",ignoreCase=true)||status.contains("unterbrochen",ignoreCase=true)||status.contains("abgebrochen",ignoreCase=true)
 val fraction=when{
  running && total>0 -> (current.toFloat()/total.toFloat()).coerceIn(0f,1f)
  running && totalBytes>0 -> (doneBytes.toFloat()/totalBytes.toFloat()).coerceIn(0f,1f)
  hasCompleted -> 1f
  else -> 0f
 }
 val percentage=if(hasCompleted&&!running)100 else (fraction*100).toInt().coerceIn(0,100)
 val title=when{running->"Synchronisierung läuft";hasCompleted->"Synchronisation abgeschlossen";hasError->"Synchronisation unterbrochen";else->"Bereit zur Synchronisation"}
 Column(Modifier.fillMaxSize()){
  Column(Modifier.fillMaxWidth().padding(start=24.dp,end=24.dp,top=15.dp,bottom=10.dp)){
   Text("Synchronisation",fontSize=30.sp,fontWeight=FontWeight.Bold,color=MaterialTheme.colorScheme.onBackground)
   Text("Deine Fotos. Sicher. Überall.",fontSize=13.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
  }
  Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal=16.dp,vertical=8.dp),verticalArrangement=Arrangement.spacedBy(16.dp)){
   Card(Modifier.fillMaxWidth(),shape=RoundedCornerShape(26.dp),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface)){
    Column(Modifier.fillMaxWidth().padding(horizontal=18.dp,vertical=22.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(12.dp)){
     Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween){
      Text("SYNCHRONISIERUNG",fontSize=11.sp,fontWeight=FontWeight.Bold,color=MaterialTheme.colorScheme.onSurfaceVariant)
      Text(if(running)"● Aktiv" else if(hasCompleted)"✓ Fertig" else if(hasError)"● Unterbrochen" else "● Bereit",
       fontSize=12.sp,color=if(hasCompleted)MaterialTheme.colorScheme.secondary else if(hasError)MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
     }
     Spacer(Modifier.height(4.dp))
     Box(Modifier.size(190.dp),contentAlignment=Alignment.Center){
      CircularProgressIndicator(progress={fraction},modifier=Modifier.fillMaxSize(),strokeWidth=11.dp,
       color=if(hasCompleted&&!running)MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
       trackColor=MaterialTheme.colorScheme.primary.copy(alpha=.14f))
      Column(horizontalAlignment=Alignment.CenterHorizontally){
       Text("$percentage%",fontSize=38.sp,fontWeight=FontWeight.Bold,color=MaterialTheme.colorScheme.onSurface)
       Text(if(hasCompleted&&!running)"Abgeschlossen" else if(running)"Fortschritt" else "Bereit",
        fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
      }
     }
     Text(title,fontSize=21.sp,fontWeight=FontWeight.Bold,color=MaterialTheme.colorScheme.onSurface)
     Text(if(running&&total>0)"$current von $total Dateien" else if(running)"Dateien werden vorbereitet …" else if(hasCompleted)"Alle Dateien erfolgreich abgeglichen" else status,
      fontSize=14.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
     if(running){
      Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){
       val remain=(total-current).coerceAtLeast(0)
       SyncMetric(if(total>0)"$remain" else "–","Offen",Modifier.weight(1f))
       SyncMetric(if(totalBytes>0)formatBytes(doneBytes) else "–","Übertragen",Modifier.weight(1f))
       SyncMetric(if(speed>0)formatSpeed(speed) else "–","Tempo",Modifier.weight(1f))
      }
      if(file.isNotBlank()){
       Surface(Modifier.fillMaxWidth(),shape=RoundedCornerShape(17.dp),color=MaterialTheme.colorScheme.surfaceVariant){
        Row(Modifier.padding(14.dp),verticalAlignment=Alignment.CenterVertically){
         Icon(Icons.Rounded.PermMedia,contentDescription=null,tint=MaterialTheme.colorScheme.primary)
         Spacer(Modifier.width(12.dp))
         Column(Modifier.weight(1f)){
          Text(file,fontSize=13.sp,maxLines=2,fontWeight=FontWeight.Medium,color=MaterialTheme.colorScheme.onSurface)
          Text("Wird synchronisiert …",fontSize=11.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
         }
        }
       }
      }
      OutlinedButton(onClick=onCancel,modifier=Modifier.fillMaxWidth().height(48.dp),shape=RoundedCornerShape(25.dp)){
       Text("Abbrechen",fontWeight=FontWeight.SemiBold)
      }
     }
    }
   }
   Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){
    Text("Meine Synchronisationen",fontSize=19.sp,fontWeight=FontWeight.Bold,color=MaterialTheme.colorScheme.onBackground)
    Text("${items.count{it.enabled}} aktiv",fontSize=12.sp,color=MaterialTheme.colorScheme.primary)
   }
   if(items.isEmpty()){
    CardBox{
     Text("Noch keine Synchronisation",fontWeight=FontWeight.Bold)
     Text("Erstelle ein Profil und wähle deine Ordner aus.",color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
   }
   items.forEach{p->
    Card(Modifier.fillMaxWidth(),shape=RoundedCornerShape(24.dp),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface)){
     Column(Modifier.padding(17.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
      Row(verticalAlignment=Alignment.CenterVertically){
       Surface(shape=RoundedCornerShape(15.dp),color=MaterialTheme.colorScheme.primaryContainer){
        Box(Modifier.size(50.dp),contentAlignment=Alignment.Center){
         Icon(Icons.Rounded.PermMedia,contentDescription=null,tint=MaterialTheme.colorScheme.onPrimaryContainer)
        }
       }
       Spacer(Modifier.width(12.dp))
       Column(Modifier.weight(1f)){
        Text(p.profileName.ifBlank{syncProfileTitle(p.direction)},fontWeight=FontWeight.Bold,fontSize=17.sp,color=MaterialTheme.colorScheme.onSurface)
        Text(syncDirectionText(p.direction)+" · "+p.sourceFolders.size+" Ordner",fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
        Text((p.name.ifBlank{p.host})+" → /"+p.remoteFolder,fontSize=11.sp,maxLines=2,color=MaterialTheme.colorScheme.onSurfaceVariant)
       }
       Switch(checked=p.enabled,onCheckedChange={onToggle(p)})
      }
      lastSync[p.id]?.let{Text("Zuletzt: $it",fontSize=11.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)}
      Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(7.dp)){
       Button(onClick={onRun(p)},enabled=!running&&p.enabled,modifier=Modifier.weight(1f),shape=RoundedCornerShape(16.dp)){Text("Jetzt synchronisieren",maxLines=1)}
       OutlinedButton(onClick={onHistory(p)},shape=RoundedCornerShape(16.dp)){Text("Verlauf")}
       var menu by remember{mutableStateOf(false)}
       Box{
        IconButton(onClick={menu=true}){Text("⋮",fontSize=23.sp)}
        DropdownMenu(expanded=menu,onDismissRequest={menu=false}){
         DropdownMenuItem(text={Text("Bearbeiten")},onClick={menu=false;onEdit(p)})
         DropdownMenuItem(text={Text("Löschen",color=MaterialTheme.colorScheme.error)},onClick={menu=false;onDelete(p)})
        }
       }
      }
     }
    }
   }
   Button(onClick=onAdd,modifier=Modifier.fillMaxWidth().height(50.dp),shape=RoundedCornerShape(18.dp)){
    Text("+  Neue Synchronisation",fontWeight=FontWeight.SemiBold)
   }
   de.galeriesync.gallery.NasGalleryScreen(items,manageOffline=true)
  }
 }
}

@Composable private fun SyncMetric(value:String,label:String,modifier:Modifier=Modifier){
 Column(modifier.background(MaterialTheme.colorScheme.surfaceVariant,shape=RoundedCornerShape(15.dp)).padding(vertical=13.dp,horizontal=6.dp),
  horizontalAlignment=Alignment.CenterHorizontally){
  Text(value,fontSize=13.sp,fontWeight=FontWeight.Bold,maxLines=1,color=MaterialTheme.colorScheme.onSurface)
  Text(label,fontSize=10.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
 }
}

@Composable private fun NasEditor(initial:NasSettings,onBack:()->Unit,onSave:(NasSettings)->Unit){
 var s by remember{mutableStateOf(initial)};var shares by remember{mutableStateOf(emptyList<String>())};var msg by remember{mutableStateOf("")};var ugreenMsg by remember{mutableStateOf("")};var busy by remember{mutableStateOf(false)};var ugreenBusy by remember{mutableStateOf(false)};val scope=rememberCoroutineScope()
 ModalWindow(if(initial.host.isBlank())"NAS hinzufügen" else "NAS bearbeiten",onBack){
  Column(Modifier.fillMaxSize().padding(20.dp,16.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)){
   Text("Lokale Verbindung",color=MaterialTheme.colorScheme.onSurface,fontWeight=FontWeight.Bold,fontSize=19.sp)
   Text("SMB im lokalen Netzwerk",color=MaterialTheme.colorScheme.onSurfaceVariant,fontSize=12.sp)
   Field("Name",s.name,{s=s.copy(name=it)})
   Field("Server / IP-Adresse",s.host,{s=s.copy(host=it)})
   Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){Box(Modifier.weight(1f)){Field("Benutzername",s.username,{s=s.copy(username=it)})};Box(Modifier.weight(1f)){Field("Passwort",s.password,{s=s.copy(password=it)},true)}}
   Button(onClick={scope.launch{busy=true;msg=runCatching{shares=withContext(Dispatchers.IO){SmbProvider(s.copy(share="")).listShares()};if(shares.isEmpty())"SMB erreichbar · Freigabe manuell eintragen" else "✓ "+shares.size+" Freigaben gefunden"}.getOrElse{"✕ "+(it.message?:it.javaClass.simpleName)};busy=false}},enabled=!busy&&s.host.isNotBlank()&&s.username.isNotBlank(),modifier=Modifier.fillMaxWidth()){Text(if(busy)"Suche …" else "Verbindung testen & Freigaben suchen",color=if(!busy&&s.host.isNotBlank()&&s.username.isNotBlank())Color.White else MaterialTheme.colorScheme.onSurfaceVariant)}
   if(msg.isNotBlank())Text(msg,color=if(msg.startsWith("✓"))MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,fontSize=12.sp)
   if(shares.isNotEmpty()){Text("Freigabe auswählen",color=MaterialTheme.colorScheme.onSurface,fontWeight=FontWeight.SemiBold);shares.forEach{sh->Surface(Modifier.fillMaxWidth().clickable{s=s.copy(share=sh)},shape=RoundedCornerShape(12.dp),color=if(s.share==sh)MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface){Row(Modifier.padding(14.dp),verticalAlignment=Alignment.CenterVertically){Text("▣",color=MaterialTheme.colorScheme.primary);Spacer(Modifier.width(10.dp));Text(sh,Modifier.weight(1f),color=MaterialTheme.colorScheme.onSurface);RadioButton(s.share==sh,null)}}}}
   Field("SMB-Freigabe",s.share,{s=s.copy(share=it)})
   Spacer(Modifier.height(4.dp))
   Button(onClick={onSave(s.copy(name=s.name.ifBlank{s.host}))},enabled=(s.host.isNotBlank()&&s.share.isNotBlank()),modifier=Modifier.fillMaxWidth().height(52.dp),shape=RoundedCornerShape(12.dp)){Text("NAS-Verbindung speichern",color=if((s.host.isNotBlank()&&s.share.isNotBlank()))Color.White else MaterialTheme.colorScheme.onSurfaceVariant,fontWeight=FontWeight.Bold)}
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
 ModalWindow(if(existing==null)"Neue Synchronisation" else "Synchronisation bearbeiten",onBack){
  Box(Modifier.fillMaxSize()){
   Column(Modifier.fillMaxSize().padding(16.dp).padding(bottom=if(step==1)118.dp else 0.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)){
    if(connections.isEmpty()){
     CardBox{Text("Zuerst eine NAS hinzufügen",color=MaterialTheme.colorScheme.onSurface,fontWeight=FontWeight.Bold);Text("Synchronisationen benötigen eine gespeicherte NAS-Verbindung.",color=MaterialTheme.colorScheme.onSurfaceVariant)}
    }else if(step==0){
     Field("Name der Synchronisation",p.profileName,{p=p.copy(profileName=it)})
     Text("NAS auswählen",color=MaterialTheme.colorScheme.onSurface,fontWeight=FontWeight.Bold)
     connections.forEach{n->val active=selected?.id==n.id;Card(Modifier.fillMaxWidth().clickable{selected=n;p=p.copy(host=n.host,share=n.share,username=n.username,password=n.password,domain=n.domain,name=n.name)},colors=CardDefaults.cardColors(containerColor=if(active)MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)){Row(Modifier.padding(12.dp),verticalAlignment=Alignment.CenterVertically){Text(n.name.ifBlank{n.host},Modifier.weight(1f),fontWeight=FontWeight.Bold,color=Color.White);RadioButton(active,null)}}}
     Text("Richtung",color=MaterialTheme.colorScheme.onSurface,fontWeight=FontWeight.Bold)
     DirectionCard("Handy → NAS",p.direction==SyncDirection.PHONE_TO_NAS){p=p.copy(direction=SyncDirection.PHONE_TO_NAS)}
     DirectionCard("NAS → Handy",p.direction==SyncDirection.NAS_TO_PHONE){p=p.copy(direction=SyncDirection.NAS_TO_PHONE)}
     DirectionCard("Bidirektional",p.direction==SyncDirection.BIDIRECTIONAL){p=p.copy(direction=SyncDirection.BIDIRECTIONAL)}
     Button(onClick={step=1},modifier=Modifier.fillMaxWidth()){Text("Ordner auswählen",color=Color.White)}
    }else{
     Row(verticalAlignment=Alignment.CenterVertically){
      Column(Modifier.weight(1f)){Text("Ordner auswählen",color=MaterialTheme.colorScheme.onSurface,fontWeight=FontWeight.Bold,fontSize=20.sp);Text("Fotos & Videos auf dem Smartphone",color=MaterialTheme.colorScheme.onSurfaceVariant,fontSize=12.sp)}
      Surface(shape=RoundedCornerShape(20.dp),color=MaterialTheme.colorScheme.surfaceVariant){Text(p.sourceFolders.size.toString()+" gewählt",Modifier.padding(10.dp,6.dp),color=MaterialTheme.colorScheme.primary,fontSize=12.sp,fontWeight=FontWeight.Bold)}
     }
     val allSelected=folders.isNotEmpty()&&p.sourceFolders.containsAll(folders.map{it.path})
     TextButton(onClick={p=p.copy(sourceFolders=if(allSelected)emptySet() else folders.map{it.path}.toSet())},enabled=folders.isNotEmpty(),modifier=Modifier.fillMaxWidth()){
      Text(if(allSelected)"Auswahl aufheben" else "Alle Ordner auswählen",color=MaterialTheme.colorScheme.primary,fontWeight=FontWeight.SemiBold)
     }
     folders.sortedWith(compareBy<MediaFolder>({folderGroupOrder(it.path)},{folderRelativeSortKey(it.path).lowercase(java.util.Locale.GERMANY)})).forEach{f->
      val active=f.path in p.sourceFolders
      Card(Modifier.fillMaxWidth().clickable{p=p.copy(sourceFolders=if(active)p.sourceFolders-f.path else p.sourceFolders+f.path)},shape=RoundedCornerShape(22.dp),colors=CardDefaults.cardColors(containerColor=if(active)MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)){
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

@Composable private fun DirectionCard(t:String,on:Boolean,click:()->Unit){Card(Modifier.fillMaxWidth().clickable{click()},shape=RoundedCornerShape(22.dp),colors=CardDefaults.cardColors(containerColor=if(on)MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)){Row(Modifier.padding(16.dp),verticalAlignment=Alignment.CenterVertically){Text(t,Modifier.weight(1f),fontWeight=FontWeight.SemiBold,color=if(on)MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface);RadioButton(on,click)}}}

@Composable private fun SettingsScreen(page:String?,open:(String?)->Unit,onClose:()->Unit){
 val context=LocalContext.current
 Dialog(onDismissRequest=onClose,properties=DialogProperties(usePlatformDefaultWidth=false)){
  Surface(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing),color=MaterialTheme.colorScheme.background){
   Column(Modifier.fillMaxSize()){
    Row(Modifier.fillMaxWidth().padding(horizontal=20.dp,vertical=18.dp),verticalAlignment=Alignment.CenterVertically){
     if(page!=null)TextButton(onClick={open(null)}){Text("‹",fontSize=26.sp)}
     Text(page?:"Einstellungen",Modifier.weight(1f),fontSize=30.sp,fontWeight=FontWeight.Bold,color=MaterialTheme.colorScheme.onBackground)
     IconButton(onClick=onClose){Text("×",fontSize=26.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)}
    }
  Column(Modifier.fillMaxSize().padding(horizontal=16.dp,vertical=12.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)){
   if(page!=null){
    when(page){
     "Allgemein"->ThemeSettings()
     "WLAN-Einstellungen"->VpnFallbackSettings(context)
     "Automatische Synchronisation"->AutoSyncSettings(context)
     "Daten & Speicher"->CardBox{Text("Lokale Daten",color=MaterialTheme.colorScheme.onSurface,fontWeight=FontWeight.Bold);Text("NAS-Zugangsdaten werden verschlüsselt gespeichert. Spiegel-Profile können entfernte Dateien auf der Gegenseite ebenfalls entfernen.",color=MaterialTheme.colorScheme.onSurfaceVariant)}
     else->CardBox{Text("GalerieSync",color=MaterialTheme.colorScheme.onSurface,fontWeight=FontWeight.Bold,fontSize=20.sp);Text("Version 0.4.0",color=MaterialTheme.colorScheme.onSurfaceVariant);Text("Fotos und Videos zwischen Smartphone und NAS synchronisieren.",color=MaterialTheme.colorScheme.onSurfaceVariant)}
    }
   }else{
    Surface(Modifier.fillMaxWidth(),shape=RoundedCornerShape(24.dp),color=MaterialTheme.colorScheme.surface){
     Column(Modifier.padding(horizontal=14.dp,vertical=5.dp)){
    SettingsRow(Icons.Outlined.Palette,"Allgemein","Design, Benachrichtigungen, Sprache"){open("Allgemein")}
    SettingsRow(Icons.Outlined.Wifi,"WLAN & VPN","Vertrauenswürdige WLANs · VPN-Fallback"){open("WLAN-Einstellungen")}
    SettingsRow(Icons.Outlined.Schedule,"Automatische Synchronisation","Zeitplan · Hintergrund"){open("Automatische Synchronisation")}
    SettingsRow(Icons.Outlined.OutlinedStorage,"Daten & Speicher","Speicherorte, Cache, Datenbank"){open("Daten & Speicher")}
    SettingsRow(Icons.Outlined.Info,"Über die App","GalerieSync · Version 0.4.0"){open("Über die App")}
     }
    }
   }
  }
   }
  }
 }
}

@Composable private fun ThemeSettings(){val context=LocalContext.current;val store=remember{SettingsStore(context)};val uploadPrefs=remember{context.getSharedPreferences("upload_settings",android.content.Context.MODE_PRIVATE)};val mode=LocalThemeMode.current;val setMode=LocalSetThemeMode.current;var notifications by remember{mutableStateOf(store.loadNotificationsEnabled())};val notificationPermissionLauncher=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){granted->notifications=granted;store.saveNotificationsEnabled(granted)};var speedLimit by remember{mutableStateOf((store.loadSpeedLimitKbps()/1024L).takeIf{it>0}?.toString()?:"")};var concurrentUploads by remember{mutableIntStateOf(uploadPrefs.getInt("concurrent_uploads",2).coerceIn(1,6))};Column(verticalArrangement=Arrangement.spacedBy(12.dp)){CardBox{Text("Gleichzeitige Uploads",fontWeight=FontWeight.Bold);Text("Legt fest, wie viele Dateien parallel zur NAS hochgeladen werden. Maximum: 6.",color=MaterialTheme.colorScheme.onSurfaceVariant,fontSize=12.sp);Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp)){(1..6).forEach{n->FilterChip(selected=concurrentUploads==n,onClick={concurrentUploads=n;uploadPrefs.edit().putInt("concurrent_uploads",n).apply()},label={Text(n.toString())})}}};CardBox{Text("Synchronisationsgeschwindigkeit",fontWeight=FontWeight.Bold);Text("Optionales Limit für Uploads zur NAS. Leer oder 0 bedeutet unbegrenzt.",color=MaterialTheme.colorScheme.onSurfaceVariant,fontSize=12.sp);OutlinedTextField(value=speedLimit,onValueChange={speedLimit=it.filter(Char::isDigit).take(5)},label={Text("Limit in MB/s")},singleLine=true,modifier=Modifier.fillMaxWidth());Button(onClick={store.saveSpeedLimitKbps((speedLimit.toLongOrNull()?:0L)*1024L)},modifier=Modifier.fillMaxWidth()){Text("Geschwindigkeitslimit speichern")}};CardBox{Text("Darstellung",fontWeight=FontWeight.Bold);Text("Wähle das Erscheinungsbild von GalerieSync.",color=MaterialTheme.colorScheme.onSurfaceVariant,fontSize=12.sp);listOf("SYSTEM" to "System","LIGHT" to "Hell","DARK" to "Dunkel").forEach{(v,label)->Row(Modifier.fillMaxWidth().clickable{setMode(v)}.padding(vertical=8.dp),verticalAlignment=Alignment.CenterVertically){RadioButton(mode==v,{setMode(v)});Spacer(Modifier.width(8.dp));Text(label)}}};CardBox{Text("Benachrichtigungen",fontWeight=FontWeight.Bold);Text("Zeigt Start, Fortschritt und Ergebnis von Synchronisationen an.",color=MaterialTheme.colorScheme.onSurfaceVariant,fontSize=12.sp);Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Text("Sync-Benachrichtigungen",Modifier.weight(1f));Switch(checked=notifications,onCheckedChange={on->if(!on){notifications=false;store.saveNotificationsEnabled(false)}else if(Build.VERSION.SDK_INT>=33&&ContextCompat.checkSelfPermission(context,Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED){notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)}else{notifications=true;store.saveNotificationsEnabled(true)}})}}}}
@Composable private fun VpnFallbackSettings(context:android.content.Context){
 val store=remember{SettingsStore(context)};var trusted by remember{mutableStateOf(store.loadTrustedWifi())};var config by remember{mutableStateOf(store.loadVpnConfig())};var current by remember{mutableStateOf(currentWifiName(context))};var message by remember{mutableStateOf("")};var testing by remember{mutableStateOf(false)};var confirmRemove by remember{mutableStateOf(false)};val scope=rememberCoroutineScope()
 val wifiPermission=rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()){grants->if(grants.values.all{it}){current=currentWifiName(context);message=if(current!=null)"WLAN erkannt: $current" else "WLAN-Name konnte nicht gelesen werden. Prüfe, ob WLAN und Standortdienste aktiv sind."}else message="WLAN-Berechtigung wurde nicht erteilt."}
 val importConfig=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){uri->if(uri!=null){config=runCatching{context.contentResolver.openInputStream(uri)?.bufferedReader()?.use{it.readText()}.orEmpty()}.getOrDefault("");if(config.isNotBlank()){store.saveVpnConfig(config);message="WireGuard-Konfiguration importiert ✓"}}}
 fun refreshWifi(){message="WLAN wird erkannt …";val needed=if(Build.VERSION.SDK_INT>=33)arrayOf(Manifest.permission.NEARBY_WIFI_DEVICES,Manifest.permission.ACCESS_FINE_LOCATION)else arrayOf(Manifest.permission.ACCESS_FINE_LOCATION);if(needed.any{ContextCompat.checkSelfPermission(context,it)!=PackageManager.PERMISSION_GRANTED})wifiPermission.launch(needed) else {current=currentWifiName(context);message=if(current!=null)"WLAN erkannt: $current" else "WLAN-Name konnte nicht gelesen werden. Prüfe, ob WLAN und Standortdienste aktiv sind."}}
 LaunchedEffect(Unit){refreshWifi()}
 Column(verticalArrangement=Arrangement.spacedBy(12.dp)){
  CardBox{
   Text("Heim-WLANs",color=MaterialTheme.colorScheme.onSurface,fontWeight=FontWeight.Bold,fontSize=18.sp)
   Text("In diesen WLANs verbindet sich GalerieSync direkt per SMB. In allen anderen WLANs und über Mobilfunk wird automatisch das FRITZ!Box-VPN verwendet.",color=MaterialTheme.colorScheme.onSurfaceVariant,fontSize=12.sp)
   Spacer(Modifier.height(10.dp))
   Text(current?.let{"Aktuell verbunden: $it"}?:"Aktuelles WLAN nicht erkannt",color=if(current!=null)MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,fontWeight=FontWeight.SemiBold)
   Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){Button(onClick={current?.let{trusted=trusted+it;store.saveTrustedWifi(trusted);message="$it als Heim-WLAN gespeichert ✓"}?:refreshWifi()},modifier=Modifier.weight(1f)){Text(if(current!=null)"Dieses WLAN hinzufügen" else "WLAN erkennen",color=Color.White)}}
   if(trusted.isNotEmpty()){Spacer(Modifier.height(8.dp));trusted.sorted().forEach{ssid->Surface(Modifier.fillMaxWidth(),shape=RoundedCornerShape(12.dp),color=MaterialTheme.colorScheme.surfaceVariant){Row(Modifier.padding(12.dp),verticalAlignment=Alignment.CenterVertically){Text("◉",color=MaterialTheme.colorScheme.secondary);Spacer(Modifier.width(10.dp));Text(ssid,Modifier.weight(1f),color=MaterialTheme.colorScheme.onSurface);TextButton(onClick={trusted=trusted-ssid;store.saveTrustedWifi(trusted)}){Text("Entfernen",color=MaterialTheme.colorScheme.error)}}}}}
  }
  CardBox{
   Text("FRITZ!Box VPN",color=MaterialTheme.colorScheme.onSurface,fontWeight=FontWeight.Bold,fontSize=18.sp)
   Text(if(config.isBlank())"Noch nicht eingerichtet" else "WireGuard-Konfiguration eingerichtet ✓",color=if(config.isBlank())MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.secondary)
   Text("Exportiere die WireGuard-Konfiguration in der FRITZ!Box und wähle anschließend einfach die .conf-Datei aus.",color=MaterialTheme.colorScheme.onSurfaceVariant,fontSize=12.sp)
   Spacer(Modifier.height(10.dp))
   Button(onClick={importConfig.launch(arrayOf("text/plain","application/octet-stream","*/*"))},modifier=Modifier.fillMaxWidth()){Text(if(config.isBlank())"WireGuard-Datei auswählen" else "Konfiguration ersetzen",color=Color.White)}
   if(config.isNotBlank()){Button(onClick={val i=VpnManager.permissionIntent(context);if(i!=null){message="VPN-Freigabe fehlt – starte einmal einen Sync und bestätige die Android-Abfrage."}else scope.launch{testing=true;message="VPN-Test wird gestartet …";val r=runCatching{VpnManager.connectForTest(context){message=it};val targets=store.loadConnections();require(targets.isNotEmpty()){"Keine NAS-Verbindung gespeichert."};var ok=false;var detail="";for(n in targets){val r=runCatching{VpnManager.waitForSmb(n.host){message=it};withContext(Dispatchers.IO){SmbProvider(n).test()}};if(r.isSuccess){ok=true;detail=n.name.ifBlank{n.host};break}else detail=r.exceptionOrNull()?.message?:"SMB-Test fehlgeschlagen"};require(ok){detail};"VPN aktiv und NAS über SMB erreichbar ✓"};message=r.getOrElse{"VPN-Test fehlgeschlagen: "+(it.message?:"Unbekannter Fehler")};VpnManager.disconnect();testing=false}},enabled=!testing,modifier=Modifier.fillMaxWidth()){Text(if(testing)"VPN wird getestet …" else "VPN & NAS testen",color=Color.White)};Text("Der Test baut den WireGuard-Tunnel auf und prüft danach, ob eine gespeicherte NAS per SMB erreichbar ist.",color=MaterialTheme.colorScheme.onSurfaceVariant,fontSize=11.sp);TextButton(onClick={confirmRemove=true}){Text("VPN-Konfiguration entfernen",color=MaterialTheme.colorScheme.error)}}
  }
  if(message.isNotBlank())Text(message,color=if(message.contains("✓"))MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,fontSize=12.sp)
 }
 if(confirmRemove){AlertDialog(onDismissRequest={confirmRemove=false},title={Text("VPN-Konfiguration entfernen?")},text={Text("Bist du sicher? Die gespeicherte WireGuard-Konfiguration wird aus GalerieSync entfernt.")},confirmButton={TextButton(onClick={config="";store.saveVpnConfig("");message="VPN-Konfiguration entfernt";confirmRemove=false}){Text("Entfernen",color=MaterialTheme.colorScheme.error)}},dismissButton={TextButton(onClick={confirmRemove=false}){Text("Abbrechen")}})}
}
@Composable private fun SettingsRow(icon:ImageVector,title:String,subtitle:String,onClick:()->Unit){
 Column(Modifier.fillMaxWidth()){
  Row(Modifier.fillMaxWidth().clickable{onClick()}.padding(horizontal=10.dp,vertical=17.dp),verticalAlignment=Alignment.CenterVertically){
   Surface(modifier=Modifier.size(46.dp),shape=RoundedCornerShape(15.dp),color=MaterialTheme.colorScheme.primaryContainer){
    Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center){
     Icon(icon,contentDescription=null,modifier=Modifier.size(23.dp),tint=MaterialTheme.colorScheme.primary)
    }
   }
   Spacer(Modifier.width(14.dp))
   Column(Modifier.weight(1f)){
    Text(title,color=MaterialTheme.colorScheme.onSurface,fontWeight=FontWeight.SemiBold,fontSize=16.sp)
    Text(subtitle,color=MaterialTheme.colorScheme.onSurfaceVariant,fontSize=12.sp)
   }
   Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight,contentDescription=null,modifier=Modifier.size(22.dp),tint=MaterialTheme.colorScheme.onSurfaceVariant)
  }
  HorizontalDivider(Modifier.padding(start=70.dp,end=12.dp),color=MaterialTheme.colorScheme.outline.copy(alpha=0.18f))
 }
}
private fun syncDirectionText(d:SyncDirection)=when(d){SyncDirection.PHONE_TO_NAS->"Handy → NAS";SyncDirection.NAS_TO_PHONE->"NAS → Handy";SyncDirection.BIDIRECTIONAL->"Bidirektional"}
private fun syncProfileTitle(d:SyncDirection)=when(d){SyncDirection.PHONE_TO_NAS->"Handy → NAS";SyncDirection.NAS_TO_PHONE->"NAS → Handy";SyncDirection.BIDIRECTIONAL->"Bidirektionale Synchronisation"}
@Composable private fun AutoSyncSettings(context:android.content.Context){
 val store=remember{SettingsStore(context)};var value by remember{mutableStateOf(store.loadAutoSyncValue().toString())};var unit by remember{mutableStateOf(store.loadAutoSyncUnit())};var saved by remember{mutableStateOf("")};var enabled by remember{mutableStateOf(store.loadAutoSyncEnabled())};var network by remember{mutableStateOf(store.loadAutoSyncNetwork())};var charging by remember{mutableStateOf(store.loadAutoSyncCharging())};var startHour by remember{mutableIntStateOf(store.loadAutoSyncStartHour())};var startMinute by remember{mutableIntStateOf(store.loadAutoSyncStartMinute())}
 CardBox{
  Text("Hintergrund-Synchronisation",fontWeight=FontWeight.Bold,fontSize=18.sp)
  Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text("Automatisch synchronisieren",fontWeight=FontWeight.SemiBold);Text(if(enabled)"Aktiv" else "Deaktiviert",color=MaterialTheme.colorScheme.onSurfaceVariant,fontSize=12.sp)};Switch(checked=enabled,onCheckedChange={on->enabled=on;store.saveAutoSyncEnabled(on);if(on){val raw=value.toLongOrNull()?:15L;val minutes=when(unit){"HOURS"->raw*60;"DAYS"->raw*1440;"WEEKS"->raw*10080;else->raw}.coerceAtLeast(15);schedule(context,minutes);saved="Automatische Synchronisation aktiviert"}else{WorkManager.getInstance(context).cancelUniqueWork("GalerieSync");saved="Automatische Synchronisation deaktiviert"}})};Text("Lege fest, wie oft aktive Profile automatisch geprüft werden.",color=MaterialTheme.colorScheme.onSurfaceVariant,fontSize=12.sp)
  Spacer(Modifier.height(8.dp))
  Text("Netzwerk",fontWeight=FontWeight.SemiBold)
  Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp)){listOf("WIFI" to "Nur WLAN","ANY" to "WLAN + Mobilfunk").forEach{(v,label)->FilterChip(selected=network==v,onClick={network=v;store.saveAutoSyncNetwork(v)},label={Text(label)})}}
  Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Text("Nur beim Laden",Modifier.weight(1f));Switch(checked=charging,onCheckedChange={charging=it;store.saveAutoSyncCharging(it)})}
  Spacer(Modifier.height(8.dp))
  Text("Startzeit",fontWeight=FontWeight.SemiBold);OutlinedButton(onClick={android.app.TimePickerDialog(context,{_,h,m->startHour=h;startMinute=m},startHour,startMinute,true).show()},modifier=Modifier.fillMaxWidth()){Text("%02d:%02d Uhr".format(startHour,startMinute))}
  OutlinedTextField(value=value,onValueChange={value=it.filter(Char::isDigit).take(4)},label={Text("Intervall")},singleLine=true,modifier=Modifier.fillMaxWidth())
  Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp)){listOf("MINUTES" to "Minuten","HOURS" to "Stunden","DAYS" to "Tage","WEEKS" to "Wochen").forEach{(v,label)->FilterChip(selected=unit==v,onClick={unit=v},label={Text(label)})}}
  Text("Android führt Hintergrundarbeit nicht sekundengenau aus. Das kleinste unterstützte Intervall sind 15 Minuten.",color=MaterialTheme.colorScheme.onSurfaceVariant,fontSize=11.sp)
  Button(onClick={val raw=value.toLongOrNull()?:15L;val minutes=when(unit){"HOURS"->raw*60;"DAYS"->raw*1440;"WEEKS"->raw*10080;else->raw}.coerceAtLeast(15);store.saveAutoSyncSchedule(raw.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),unit);store.saveAutoSyncStartTime(startHour,startMinute);enabled=true;store.saveAutoSyncEnabled(true);schedule(context,minutes);saved="Automatik gespeichert · Start %02d:%02d ·".format(startHour,startMinute)+ " · $value "+when(unit){"HOURS"->"Stunden";"DAYS"->"Tage";"WEEKS"->"Wochen";else->"Minuten"}},modifier=Modifier.fillMaxWidth()){Text("Zeitplan speichern & aktivieren")}
  if(saved.isNotBlank())Text(saved,color=MaterialTheme.colorScheme.secondary,fontSize=12.sp)
 }
}
fun schedule(context:android.content.Context,minutes:Long){val store=SettingsStore(context);val network=if(store.loadAutoSyncNetwork()=="ANY")NetworkType.CONNECTED else NetworkType.UNMETERED;val c=Constraints.Builder().setRequiredNetworkType(network).setRequiresCharging(store.loadAutoSyncCharging()).build();val now=java.time.ZonedDateTime.now();var first=now.withHour(store.loadAutoSyncStartHour()).withMinute(store.loadAutoSyncStartMinute()).withSecond(0).withNano(0);val interval=java.time.Duration.ofMinutes(minutes.coerceAtLeast(15));while(!first.isAfter(now))first=first.plus(interval);val delay=java.time.Duration.between(now,first).toMillis();val r=PeriodicWorkRequestBuilder<SyncWorker>(minutes.coerceAtLeast(15),TimeUnit.MINUTES).setInitialDelay(delay,TimeUnit.MILLISECONDS).setConstraints(c).build();WorkManager.getInstance(context).enqueueUniquePeriodicWork("GalerieSync",ExistingPeriodicWorkPolicy.UPDATE,r)}

private fun formatSpeed(v:Long):String=formatBytes(v)+"/s"
private fun formatBytes(v:Long):String{val locale=java.util.Locale.GERMANY;return when{v>=1_099_511_627_776L->"%.2f TB".format(locale,v/1_099_511_627_776.0);v>=1_073_741_824L->"%.2f GB".format(locale,v/1_073_741_824.0);v>=1_048_576L->"%.1f MB".format(locale,v/1_048_576.0);v>=1_024L->"%.1f KB".format(locale,v/1_024.0);else->"$v B"}}

private fun folderGroupOrder(path:String):Int{val top=path.trim('/').substringBefore('/').lowercase();return when(top){"dcim"->0;"pictures"->1;"movies"->2 else->3}}
private fun folderRelativeSortKey(path:String):String{val clean=path.trim('/');val parts=clean.split('/').filter{it.isNotBlank()};return if(parts.firstOrNull()?.lowercase() in setOf("dcim","pictures","movies"))parts.drop(1).joinToString("/") else clean}

@Composable private fun SyncHistoryScreen(items:List<String>,profileName:String?=null,onBack:()->Unit){
 ModalWindow(profileName?.let{"Verlauf · $it"}?:"Synchronisationsverlauf",onBack){
  Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)){
   if(items.isEmpty())CardBox{Text("Noch keine Synchronisationen",fontWeight=FontWeight.Bold);Text("Ab der nächsten Synchronisation wird der Verlauf hier gespeichert.",color=MaterialTheme.colorScheme.onSurfaceVariant)} else items.forEach{entry->CardBox{Text(entry.substringBefore(" · "),fontWeight=FontWeight.Bold);Text(entry.substringAfter(" · "),color=MaterialTheme.colorScheme.onSurfaceVariant)}}
  }
 }
}
