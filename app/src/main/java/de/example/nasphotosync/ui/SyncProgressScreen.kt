package de.example.nasphotosync.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.example.nasphotosync.sync.FileStatus
import de.example.nasphotosync.sync.SyncStatusBus
import de.example.nasphotosync.sync.SyncUiState
import de.example.nasphotosync.ui.theme.NapsError
import de.example.nasphotosync.ui.theme.NapsPrimary
import de.example.nasphotosync.ui.theme.NapsSuccess
import de.example.nasphotosync.ui.theme.NapsSurface

@Composable
fun SyncProgressScreen(onBack: () -> Unit) {
    val state by SyncStatusBus.state.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Zurück") }
            Text("Synchronisation", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(20.dp))

        when (val s = state) {
            is SyncUiState.Idle -> {
                Text("Kein Sync aktiv.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            is SyncUiState.Running -> {
                val done = s.files.count { it.status == FileStatus.DONE || it.status == FileStatus.ERROR }
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                    CircularProgressIndicator(
                        progress = { if (s.files.isEmpty()) 0f else done.toFloat() / s.files.size },
                        modifier = Modifier.size(110.dp),
                        color = NapsPrimary,
                        trackColor = NapsSurface,
                        strokeWidth = 8.dp
                    )
                    Text("$done/${s.files.size}", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
                Spacer(Modifier.height(8.dp))
                Text("Synchronisiere ${s.profileName} ...", textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(16.dp))
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(s.files, key = { it.name }) { file ->
                        FileProgressRow(file.name, file.status)
                    }
                }
            }
            is SyncUiState.Done -> {
                Text("✓ ${s.profileName} fertig", fontWeight = FontWeight.Bold, color = NapsSuccess, fontSize = 16.sp)
                Text("${s.uploaded} hochgeladen, ${s.downloaded} heruntergeladen", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            is SyncUiState.Failed -> {
                Text("✗ ${s.profileName} fehlgeschlagen", fontWeight = FontWeight.Bold, color = NapsError, fontSize = 16.sp)
                Text(s.message, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun FileProgressRow(name: String, status: de.example.nasphotosync.sync.FileStatus) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(NapsSurface)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(name, fontSize = 13.sp, maxLines = 1, modifier = Modifier.weight(1f))
        when (status) {
            FileStatus.DONE -> Icon(Icons.Default.Check, contentDescription = "Fertig", tint = NapsSuccess, modifier = Modifier.size(18.dp))
            FileStatus.ERROR -> Icon(Icons.Default.Error, contentDescription = "Fehler", tint = NapsError, modifier = Modifier.size(18.dp))
            FileStatus.SYNCING -> CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = NapsPrimary)
            FileStatus.WAITING -> Icon(Icons.Default.HourglassEmpty, contentDescription = "Wartet", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
        }
    }
}
