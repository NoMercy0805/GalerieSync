package com.schiwy.galeriesync.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.schiwy.galeriesync.data.AppDatabase
import com.schiwy.galeriesync.data.SettingsRepository
import com.schiwy.galeriesync.sync.SyncWorker
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HomeScreen(onOpenSettings: () -> Unit) {
    val context = LocalContext.current
    val settings = remember { SettingsRepository(context) }
    val db = remember { AppDatabase.getInstance(context) }

    val lastSyncAt by settings.lastSyncAt.collectAsStateWithLifecycle(initialValue = null)
    val syncedCount by db.syncedFileDao().observeCount().collectAsStateWithLifecycle(initialValue = 0)
    val selectedBuckets by settings.selectedBuckets.collectAsStateWithLifecycle(initialValue = emptySet())

    val workInfos by WorkManager.getInstance(context)
        .getWorkInfosForUniqueWorkFlow("galeriesync_manual")
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val isSyncing = workInfos.any { it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED }

    Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("GalerieSync", fontSize = 24.sp, fontWeight = FontWeight.Bold)
            IconButton(onClick = onOpenSettings) {
                Icon(Icons.Default.Settings, contentDescription = "Einstellungen")
            }
        }
        Spacer(Modifier.height(20.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.primaryContainer)
                .padding(20.dp)
        ) {
            Text("Letzter Sync", fontSize = 13.sp, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f))
            Text(
                lastSyncAt?.let { formatTimestamp(it) } ?: "Noch nie",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Column {
                    Text("Alben", fontSize = 12.sp, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f))
                    Text("${selectedBuckets.size}", fontWeight = FontWeight.Bold)
                }
                Column {
                    Text("Synchronisierte Dateien", fontSize = 12.sp, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f))
                    Text("$syncedCount", fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(Modifier.height(20.dp))
        Button(
            onClick = { SyncWorker.runNow(context) },
            enabled = !isSyncing,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (isSyncing) "Synchronisiere..." else "Jetzt synchronisieren")
        }

        if (selectedBuckets.isEmpty()) {
            Spacer(Modifier.height(16.dp))
            Text(
                "Keine Alben ausgewaehlt - in den Einstellungen festlegen, was synchronisiert werden soll.",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

private fun formatTimestamp(epochMs: Long): String {
    val format = SimpleDateFormat("dd.MM.yyyy, HH:mm 'Uhr'", Locale.GERMAN)
    return format.format(Date(epochMs))
}
