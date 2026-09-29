package com.schiwy.galeriesync.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Fuehrt einmalig durch alle noetigen Berechtigungen: Fotos lesen,
 * Benachrichtigungen (fuer den Sync-Fortschritt), und "Zugriff auf alle
 * Dateien" (fuer stilles Verschieben/Loeschen im Hintergrund - ohne das
 * kann der Papierkorb-Mechanismus nicht automatisch laufen).
 */
@Composable
fun PermissionsScreen(onAllGranted: () -> Unit) {
    val context = LocalContext.current

    var mediaGranted by remember { mutableStateOf(hasMediaPermission(context)) }
    var notificationsGranted by remember {
        mutableStateOf(Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || hasNotificationPermission(context))
    }
    var allFilesGranted by remember { mutableStateOf(hasAllFilesAccess()) }

    val mediaLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        mediaGranted = granted
    }
    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        notificationsGranted = granted
    }
    val allFilesLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        allFilesGranted = hasAllFilesAccess()
    }

    LaunchedEffect(mediaGranted, notificationsGranted, allFilesGranted) {
        if (mediaGranted && notificationsGranted && allFilesGranted) onAllGranted()
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text("Berechtigungen einrichten", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(
            "GalerieSync braucht drei Berechtigungen, um automatisch im Hintergrund synchronisieren zu koennen.",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(28.dp))

        PermissionRow(
            title = "Fotos lesen",
            description = "Um deine Bilder zu finden und hochzuladen.",
            granted = mediaGranted
        ) {
            val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                Manifest.permission.READ_MEDIA_IMAGES else Manifest.permission.READ_EXTERNAL_STORAGE
            mediaLauncher.launch(permission)
        }

        Spacer(Modifier.height(16.dp))
        PermissionRow(
            title = "Benachrichtigungen",
            description = "Zeigt an, wenn ein Sync laeuft oder fertig ist.",
            granted = notificationsGranted
        ) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        Spacer(Modifier.height(16.dp))
        PermissionRow(
            title = "Zugriff auf alle Dateien",
            description = "Noetig, damit gespiegelte Loeschungen (Papierkorb) still im Hintergrund laufen koennen, ohne bei jeder Datei nachzufragen.",
            granted = allFilesGranted
        ) {
            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                data = Uri.parse("package:${context.packageName}")
            }
            allFilesLauncher.launch(intent)
        }
    }
}

@Composable
private fun PermissionRow(title: String, description: String, granted: Boolean, onRequest: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Text(description, fontSize = 12.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        if (granted) {
            Text("✓ Erteilt", color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Bold)
        } else {
            Button(onClick = onRequest) { Text("Erlauben") }
        }
    }
}

private fun hasMediaPermission(context: android.content.Context): Boolean {
    val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
        Manifest.permission.READ_MEDIA_IMAGES else Manifest.permission.READ_EXTERNAL_STORAGE
    return androidx.core.content.ContextCompat.checkSelfPermission(context, permission) == android.content.pm.PackageManager.PERMISSION_GRANTED
}

private fun hasNotificationPermission(context: android.content.Context): Boolean {
    return androidx.core.content.ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED
}

private fun hasAllFilesAccess(): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Environment.isExternalStorageManager() else true
}
