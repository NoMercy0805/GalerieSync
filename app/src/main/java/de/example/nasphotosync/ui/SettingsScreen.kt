package de.example.nasphotosync.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SettingsScreen() {
    Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
        Text("Einstellungen", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(
            "Detaillierte Einstellungen (WLAN-only, Benachrichtigungen, Speicherverwaltung) folgen in der naechsten Ausbaustufe.",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
