package de.example.nasphotosync

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.weight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import de.example.nasphotosync.sync.SyncWorker
import de.example.nasphotosync.ui.AddNasScreen
import de.example.nasphotosync.ui.HomeScreen
import de.example.nasphotosync.ui.NasListScreen
import de.example.nasphotosync.ui.NewSyncProfileScreen
import de.example.nasphotosync.ui.SettingsScreen
import de.example.nasphotosync.ui.SyncProfilesScreen
import de.example.nasphotosync.ui.SyncProgressScreen
import de.example.nasphotosync.ui.theme.NasPhotoSyncTheme

private object Routes {
    const val HOME = "home"
    const val SYNC_LIST = "sync_list"
    const val NEW_PROFILE = "new_profile"
    const val NAS_LIST = "nas_list"
    const val ADD_NAS = "add_nas"
    const val SETTINGS = "settings"
    const val PROGRESS = "progress"
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val mediaPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            Manifest.permission.READ_MEDIA_IMAGES else Manifest.permission.READ_EXTERNAL_STORAGE
        if (ContextCompat.checkSelfPermission(this, mediaPermission) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            registerForActivityResult(ActivityResultContracts.RequestPermission()) {}.launch(mediaPermission)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            registerForActivityResult(ActivityResultContracts.RequestPermission()) {}.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        SyncWorker.schedulePeriodic(applicationContext)

        setContent {
            NasPhotoSyncTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    NasPhotoSyncApp()
                }
            }
        }
    }
}

@Composable
private fun NasPhotoSyncApp() {
    val navController = rememberNavController()
    val context = androidx.compose.ui.platform.LocalContext.current
    var showAllFilesPrompt by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
            showAllFilesPrompt = true
        }
    }

    val allFilesLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        showAllFilesPrompt = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()
    }

    Scaffold(
        bottomBar = { BottomNavBar(navController) }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize()) {
            NavHost(navController = navController, startDestination = Routes.HOME, modifier = Modifier.weight(1f)) {
                composable(Routes.HOME) {
                    HomeScreen(onOpenProgress = { navController.navigate(Routes.PROGRESS) })
                }
                composable(Routes.SYNC_LIST) {
                    SyncProfilesScreen(onAddProfile = { navController.navigate(Routes.NEW_PROFILE) })
                }
                composable(Routes.NEW_PROFILE) {
                    NewSyncProfileScreen(
                        onDone = { navController.popBackStack() },
                        onBack = { navController.popBackStack() }
                    )
                }
                composable(Routes.NAS_LIST) {
                    NasListScreen(
                        onAddNas = { navController.navigate(Routes.ADD_NAS) },
                        onOpenNas = { }
                    )
                }
                composable(Routes.ADD_NAS) {
                    AddNasScreen(
                        onDone = { navController.popBackStack() },
                        onBack = { navController.popBackStack() }
                    )
                }
                composable(Routes.SETTINGS) { SettingsScreen() }
                composable(Routes.PROGRESS) { SyncProgressScreen(onBack = { navController.popBackStack() }) }
            }
        }
    }

    if (showAllFilesPrompt) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showAllFilesPrompt = false },
            title = { Text("Zugriff auf alle Dateien") },
            text = { Text("Damit heruntergeladene Fotos von der NAS zuverlässig im richtigen Ordner gespeichert werden können, braucht die App diese Berechtigung.") },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                        data = Uri.parse("package:${context.packageName}")
                    }
                    allFilesLauncher.launch(intent)
                }) { Text("Erlauben") }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { showAllFilesPrompt = false }) { Text("Später") }
            }
        )
    }
}

@Composable
private fun BottomNavBar(navController: NavHostController) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val current = backStackEntry?.destination?.route

    NavigationBar {
        NavigationBarItem(
            selected = current == Routes.HOME,
            onClick = { navController.navigate(Routes.HOME) { popUpTo(Routes.HOME) { inclusive = true } } },
            icon = { Icon(Icons.Default.Home, contentDescription = "Übersicht") },
            label = { Text("Übersicht") }
        )
        NavigationBarItem(
            selected = current == Routes.SYNC_LIST || current == Routes.NEW_PROFILE,
            onClick = { navController.navigate(Routes.SYNC_LIST) },
            icon = { Icon(Icons.Default.Sync, contentDescription = "Synchronisation") },
            label = { Text("Synchronisation") }
        )
        NavigationBarItem(
            selected = current == Routes.NAS_LIST || current == Routes.ADD_NAS,
            onClick = { navController.navigate(Routes.NAS_LIST) },
            icon = { Icon(Icons.Default.Storage, contentDescription = "NAS") },
            label = { Text("NAS") }
        )
        NavigationBarItem(
            selected = current == Routes.SETTINGS,
            onClick = { navController.navigate(Routes.SETTINGS) },
            icon = { Icon(Icons.Default.Settings, contentDescription = "Einstellungen") },
            label = { Text("Einstellungen") }
        )
    }
}
