package de.example.nasphotosync.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Sync
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
import de.example.nasphotosync.data.SyncDatabase
import de.example.nasphotosync.sync.SyncUiState
import de.example.nasphotosync.sync.SyncWorker
import de.example.nasphotosync.sync.SyncStatusBus
import de.example.nasphotosync.ui.theme.NapsPrimary
import de.example.nasphotosync.ui.theme.NapsSurface
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HomeScreen(onOpenProgress: () -> Unit) {
    val context = LocalContext.current
    val db = remember { SyncDatabase.getInstance(context) }

    val profiles by db.syncProfileDao().observeAll().collectAsStateWithLifecycle(initialValue = emptyList())
    val syncStatus by SyncStatusBus.state.collectAsStateWithLifecycle()
    val activeCount = profiles.count { it.isActive }
    val lastSync = profiles.mapNotNull { it.lastSyncAt }.maxOrNull()
    val isSyncing = syncStatus is SyncUiState.Running

    Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
        Text("NAS Photo Sync", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text("Deine Fotos. Sicher. Überall.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(24.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(NapsSurface)
                .padding(18.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(8.dp).clip(RoundedCornerShape(4.dp)).background(if (activeCount > 0) de.example.nasphotosync.ui.theme.NapsSuccess else MaterialTheme.colorScheme.onSurfaceVariant))
                Spacer(Modifier.width(8.dp))
                Text("Aktive Synchronisationen", fontWeight = FontWeight.SemiBold)
            }
            Text("$activeCount aktiv", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Spacer(Modifier.height(14.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(NapsSurface)
                .padding(18.dp)
        ) {
            Text("Letzte Synchronisation", fontWeight = FontWeight.SemiBold)
            Text(
                lastSync?.let { formatTimestamp(it) } ?: "Noch nie",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(Modifier.height(20.dp))
        Button(
            onClick = {
                SyncWorker.runNow(context)
                onOpenProgress()
            },
            enabled = !isSyncing && profiles.any { it.isActive },
            colors = ButtonDefaults.buttonColors(containerColor = NapsPrimary),
            modifier = Modifier.fillMaxWidth().height(50.dp)
        ) {
            Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(if (isSyncing) "Synchronisiert..." else "Jetzt synchronisieren")
        }

        if (profiles.isEmpty()) {
            Spacer(Modifier.height(16.dp))
            Text(
                "Noch keine Synchronisation eingerichtet - unten im Tab \"Synchronisation\" eine neue anlegen.",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun formatTimestamp(epochMs: Long): String {
    val format = SimpleDateFormat("dd.MM., HH:mm 'Uhr'", Locale.GERMAN)
    return format.format(Date(epochMs))
}
