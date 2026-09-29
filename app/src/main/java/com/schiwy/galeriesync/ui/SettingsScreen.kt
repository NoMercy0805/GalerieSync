package com.schiwy.galeriesync.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.schiwy.galeriesync.data.SettingsRepository
import com.schiwy.galeriesync.sync.SyncWorker
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onEditAlbums: () -> Unit,
    onEditConnection: () -> Unit
) {
    val context = LocalContext.current
    val settings = remember { SettingsRepository(context) }
    val scope = rememberCoroutineScope()

    val wifiOnly by settings.wifiOnly.collectAsStateWithLifecycle(initialValue = true)
    val mirrorDeletions by settings.mirrorDeletions.collectAsStateWithLifecycle(initialValue = true)
    val trashRetentionDays by settings.trashRetentionDays.collectAsStateWithLifecycle(initialValue = 30)
    val connection by settings.connection.collectAsStateWithLifecycle(
        initialValue = com.schiwy.galeriesync.data.NasConnection()
    )

    Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Zurück") }
            Text("Einstellungen", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(16.dp))

        SettingsRow(title = "NAS-Verbindung", subtitle = connection.host.ifBlank { "Nicht eingerichtet" }, onClick = onEditConnection)
        SettingsRow(title = "Synchronisierte Alben", subtitle = "Auswahl bearbeiten", onClick = onEditAlbums)

        Spacer(Modifier.height(20.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Nur über WLAN synchronisieren", fontWeight = FontWeight.SemiBold)
                Text("Verhindert Datenverbrauch über Mobilfunk", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(
                checked = wifiOnly,
                onCheckedChange = { checked ->
                    scope.launch {
                        settings.setWifiOnly(checked)
                        SyncWorker.schedulePeriodic(context)
                    }
                }
            )
        }

        Spacer(Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Löschungen spiegeln", fontWeight = FontWeight.SemiBold)
                Text(
                    "Wenn auf einer Seite gelöscht wird, wandert die Datei auch auf der anderen Seite in den Papierkorb",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = mirrorDeletions,
                onCheckedChange = { checked -> scope.launch { settings.setMirrorDeletions(checked) } }
            )
        }

        Spacer(Modifier.height(16.dp))
        Text("Papierkorb-Aufbewahrung: $trashRetentionDays Tage", fontWeight = FontWeight.SemiBold)
        Slider(
            value = trashRetentionDays.toFloat(),
            onValueChange = { value -> scope.launch { settings.setTrashRetentionDays(value.toInt()) } },
            valueRange = 7f..90f,
            steps = 82
        )
    }
}

@Composable
private fun SettingsRow(title: String, subtitle: String, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 12.dp)
    ) {
        Text(title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
        Text(subtitle, fontSize = 12.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
