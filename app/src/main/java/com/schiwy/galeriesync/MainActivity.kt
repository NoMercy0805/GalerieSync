package com.schiwy.galeriesync

import android.os.Build
import android.os.Bundle
import android.os.Environment
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.schiwy.galeriesync.data.SettingsRepository
import com.schiwy.galeriesync.ui.AlbumPickerScreen
import com.schiwy.galeriesync.ui.HomeScreen
import com.schiwy.galeriesync.ui.OnboardingScreen
import com.schiwy.galeriesync.ui.PermissionsScreen
import com.schiwy.galeriesync.ui.SettingsScreen
import com.schiwy.galeriesync.ui.theme.GalerieSyncTheme
import kotlinx.coroutines.flow.first

private object Routes {
    const val PERMISSIONS = "permissions"
    const val ONBOARDING = "onboarding"
    const val ALBUMS = "albums"
    const val HOME = "home"
    const val SETTINGS = "settings"
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            GalerieSyncTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    GalerieSyncNavHost()
                }
            }
        }
    }
}

@Composable
private fun GalerieSyncNavHost() {
    val navController = rememberNavController()
    val context = androidx.compose.ui.platform.LocalContext.current
    val settings = remember { SettingsRepository(context) }

    var startDestination by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        val hasAllFiles = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Environment.isExternalStorageManager() else true
        val isConfigured = settings.connection.first().isConfigured
        val hasAlbums = settings.selectedBuckets.first().isNotEmpty()
        startDestination = when {
            !hasAllFiles -> Routes.PERMISSIONS
            !isConfigured -> Routes.ONBOARDING
            !hasAlbums -> Routes.ALBUMS
            else -> Routes.HOME
        }
    }

    val dest = startDestination ?: return

    NavHost(navController = navController, startDestination = dest) {
        composable(Routes.PERMISSIONS) {
            PermissionsScreen(onAllGranted = {
                navController.navigate(Routes.ONBOARDING) { popUpTo(Routes.PERMISSIONS) { inclusive = true } }
            })
        }
        composable(Routes.ONBOARDING) {
            OnboardingScreen(onDone = {
                navController.navigate(Routes.ALBUMS) { popUpTo(Routes.ONBOARDING) { inclusive = true } }
            })
        }
        composable(Routes.ALBUMS) {
            AlbumPickerScreen(onDone = {
                navController.navigate(Routes.HOME) { popUpTo(Routes.ALBUMS) { inclusive = true } }
            })
        }
        composable(Routes.HOME) {
            HomeScreen(onOpenSettings = { navController.navigate(Routes.SETTINGS) })
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onEditAlbums = { navController.navigate(Routes.ALBUMS) },
                onEditConnection = { navController.navigate(Routes.ONBOARDING) }
            )
        }
    }
}
