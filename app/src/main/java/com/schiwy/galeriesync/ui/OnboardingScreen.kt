package com.schiwy.galeriesync.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.schiwy.galeriesync.data.NasConnection
import com.schiwy.galeriesync.data.NasProtocol
import com.schiwy.galeriesync.data.SettingsRepository
import com.schiwy.galeriesync.sync.SyncWorker
import com.schiwy.galeriesync.webdav.WebDavClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun OnboardingScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    val settings = remember { SettingsRepository(context) }
    val scope = rememberCoroutineScope()

    var host by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("5006") }
    var useHttps by remember { mutableStateOf(true) }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var remotePath by remember { mutableStateOf("/GalerieSync") }
    var testing by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        Text("NAS-Verbindung einrichten", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text(
            "WebDAV muss auf deiner UGREEN NAS in den Datei-Diensten aktiviert sein.",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(24.dp))

        OutlinedTextField(
            value = host, onValueChange = { host = it },
            label = { Text("NAS-Adresse (IP oder Hostname)") },
            placeholder = { Text("z.B. 192.168.1.50") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = port, onValueChange = { port = it.filter { c -> c.isDigit() } },
                label = { Text("Port") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.width(120.dp)
            )
            Spacer(Modifier.width(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("HTTPS")
                Switch(checked = useHttps, onCheckedChange = { useHttps = it })
            }
        }
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = username, onValueChange = { username = it },
            label = { Text("Benutzername") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = password, onValueChange = { password = it },
            label = { Text("Passwort") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = remotePath, onValueChange = { remotePath = it },
            label = { Text("Zielordner auf der NAS") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(24.dp))

        val canTest = host.isNotBlank() && username.isNotBlank() && password.isNotBlank() && port.toIntOrNull() != null

        OutlinedButton(
            onClick = {
                testing = true
                testResult = null
                scope.launch {
                    val client = WebDavClient(
                        "${if (useHttps) "https" else "http"}://$host:${port.toIntOrNull() ?: 5006}",
                        username, password
                    )
                    val result = withContext(Dispatchers.IO) { client.testConnection() }
                    testing = false
                    testResult = if (result.isSuccess) "✓ Verbindung erfolgreich!"
                    else "✗ Fehlgeschlagen: ${result.exceptionOrNull()?.message}"
                }
            },
            enabled = canTest && !testing,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (testing) "Teste..." else "Verbindung testen")
        }

        testResult?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, fontSize = 13.sp)
        }

        Spacer(Modifier.height(20.dp))
        Button(
            onClick = {
                scope.launch {
                    settings.saveConnection(
                        NasConnection(
                            host = host, port = port.toIntOrNull() ?: 5006, useHttps = useHttps,
                            protocol = NasProtocol.WEBDAV, username = username, remoteBasePath = remotePath,
                            isConfigured = true
                        ),
                        password
                    )
                    SyncWorker.schedulePeriodic(context)
                    onDone()
                }
            },
            enabled = canTest,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Speichern und weiter")
        }
    }
}
