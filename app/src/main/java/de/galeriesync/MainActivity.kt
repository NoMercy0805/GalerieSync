package de.galeriesync
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.work.*
import de.galeriesync.data.*
import de.galeriesync.sync.SyncService
import de.galeriesync.worker.SyncWorker
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
class MainActivity:ComponentActivity(){override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);setContent{GalerieSyncApp()}}}
@OptIn(ExperimentalMaterial3Api::class)
@Composable fun GalerieSyncApp(){val context=LocalContext.current;val store=remember{SettingsStore(context)};var s by remember{mutableStateOf(store.load())};var status by remember{mutableStateOf("Bereit")};var running by remember{mutableStateOf(false)};val scope=rememberCoroutineScope();val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()){}
 LaunchedEffect(Unit){val perms=if(Build.VERSION.SDK_INT>=33)arrayOf(Manifest.permission.READ_MEDIA_IMAGES,Manifest.permission.READ_MEDIA_VIDEO)else arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE);if(perms.any{ContextCompat.checkSelfPermission(context,it)!=PackageManager.PERMISSION_GRANTED})permission.launch(perms)}
 Scaffold(topBar={TopAppBar(title={Text("GalerieSync")})}){pad->Column(Modifier.fillMaxSize().padding(pad).padding(16.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)){Text("UGREEN NAS · erste Testversion",style=MaterialTheme.typography.titleMedium)
  OutlinedTextField(s.host,{s=s.copy(host=it);store.save(s)},label={Text("NAS-Adresse / IP")},modifier=Modifier.fillMaxWidth());OutlinedTextField(s.share,{s=s.copy(share=it);store.save(s)},label={Text("SMB-Freigabe")},modifier=Modifier.fillMaxWidth());OutlinedTextField(s.username,{s=s.copy(username=it);store.save(s)},label={Text("Benutzer")},modifier=Modifier.fillMaxWidth());OutlinedTextField(s.password,{s=s.copy(password=it);store.save(s)},label={Text("Passwort")},modifier=Modifier.fillMaxWidth());OutlinedTextField(s.remoteFolder,{s=s.copy(remoteFolder=it);store.save(s)},label={Text("NAS-Zielordner")},modifier=Modifier.fillMaxWidth())
  var expanded by remember{mutableStateOf(false)};ExposedDropdownMenuBox(expanded,{expanded=!expanded}){OutlinedTextField(s.direction.name.replace('_',' '),{},readOnly=true,label={Text("Sync-Richtung")},trailingIcon={ExposedDropdownMenuDefaults.TrailingIcon(expanded)},modifier=Modifier.menuAnchor().fillMaxWidth());DropdownMenu(expanded,{expanded=false}){SyncDirection.entries.forEach{d->DropdownMenuItem(text={Text(d.name.replace('_',' '))},onClick={s=s.copy(direction=d);store.save(s);expanded=false})}}}
  Row{Checkbox(s.wifiOnly,{s=s.copy(wifiOnly=it);store.save(s)});Text("Nur bei WLAN synchronisieren",Modifier.padding(top=12.dp))}
  Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){Button(enabled=!running,onClick={scope.launch{running=true;status="Teste Verbindung…";status=runCatching{de.galeriesync.sync.SmbProvider(s).test();"Verbindung erfolgreich"}.getOrElse{"Fehler: "+it.message};running=false}}){Text("Verbindung testen")};Button(enabled=!running,onClick={scope.launch{running=true;status="Synchronisation läuft…";val result=runCatching{SyncService(context).run(s){c,t,n->status="$c/$t · $n"}};status=result.fold({"Fertig: "+it.copied+" kopiert, "+it.skipped+" übersprungen, "+it.failed+" Fehler"},{"Fehler: "+it.message});running=false}}){Text(if(running)"Läuft…" else "Synchronisieren")}}
  Button(onClick={schedule(context)},modifier=Modifier.fillMaxWidth()){Text("Automatische Synchronisation aktivieren (15 min)")};Card(Modifier.fillMaxWidth()){Text(status,Modifier.padding(16.dp))};Text("Hinweis: Die Testversion löscht keine Dateien automatisch.",style=MaterialTheme.typography.bodySmall)
 }}}
fun schedule(context:android.content.Context){val constraints=Constraints.Builder().setRequiredNetworkType(NetworkType.UNMETERED).build();val request=PeriodicWorkRequestBuilder<SyncWorker>(15,TimeUnit.MINUTES).setConstraints(constraints).build();WorkManager.getInstance(context).enqueueUniquePeriodicWork("GalerieSync",ExistingPeriodicWorkPolicy.UPDATE,request)}
