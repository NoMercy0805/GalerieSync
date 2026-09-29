package de.example.nasphotosync.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Storage
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
import de.example.nasphotosync.data.NasConnectionEntity
import de.example.nasphotosync.data.SyncDatabase
import de.example.nasphotosync.ui.theme.NapsSurface
import kotlinx.coroutines.launch

@Composable
fun NasListScreen(onAddNas: () -> Unit, onOpenNas: (String) -> Unit) {
    val context = LocalContext.current
    val db = remember { SyncDatabase.getInstance(context) }
    val connections by db.nasConnectionDao().observeAll().collectAsStateWithLifecycle(initialValue = emptyList())

    Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("NAS", fontSize = 22.sp, fontWeight = FontWeight.Bold)
            IconButton(onClick = onAddNas) { Icon(Icons.Default.Add, contentDescription = "NAS hinzufügen") }
        }
        Spacer(Modifier.height(12.dp))

        if (connections.isEmpty()) {
            Text("Noch keine NAS verbunden.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(connections, key = { it.id }) { conn ->
                    NasRow(conn, onClick = { onOpenNas(conn.id) })
                }
            }
        }
    }
}

@Composable
private fun NasRow(conn: NasConnectionEntity, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(NapsSurface)
            .clickable { onClick() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.Storage, contentDescription = null, tint = de.example.nasphotosync.ui.theme.NapsPrimary)
        Spacer(Modifier.width(12.dp))
        Column {
            Text(conn.name, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            Text("${conn.host}:${conn.port} · ${conn.protocol}", fontSize = 12.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun AddNasScreen(onDone: () -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val db = remember { SyncDatabase.getInstance(context) }
    val credentials = remember { de.example.nasphotosync.data.CredentialStore(context) }
    val scope = rememberCoroutineScope()

    var protocol by remember { mutableStateOf("SMB") }
    var name by remember { mutableStateOf("") }
    var host by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("5006") }
    var useHttps by remember { mutableStateOf(true) }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var remotePath by remember { mutableStateOf("/FotoSync") }
    var testing by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück") }
            Text("NAS hinzufügen", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(16.dp))

        // Protokoll-Tabs: SMB/WebDAV aktiv nutzbar, SFTP/UGREENlink als
        // "bald verfuegbar" markiert (noch keine funktionierende
        // Implementierung dahinter).
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(NapsSurface)
                .padding(4.dp)
        ) {
            listOf("SMB" to true, "WEBDAV" to true, "SFTP" to false, "UGREEN_LINK" to false).forEach { (proto, enabled) ->
                val active = protocol == proto
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(9.dp))
                        .then(if (active) Modifier.background(de.example.nasphotosync.ui.theme.NapsPrimary) else Modifier)
                        .then(if (enabled) Modifier.clickable { protocol = proto } else Modifier)
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        labelFor(proto),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (!enabled) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        else if (active) androidx.compose.ui.graphics.Color.White
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        if (protocol == "SFTP" || protocol == "UGREEN_LINK") {
            Spacer(Modifier.height(8.dp))
            Text("Noch nicht verfuegbar - bitte SMB oder WebDAV verwenden.", fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
        }

        Spacer(Modifier.height(18.dp))
        OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(value = host, onValueChange = { host = it }, label = { Text("Server / IP-Adresse") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = port, onValueChange = { port = it.filter { c -> c.isDigit() } },
                label = { Text("Port") }, modifier = Modifier.width(120.dp)
            )
            Spacer(Modifier.width(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("HTTPS")
                Switch(checked = useHttps, onCheckedChange = { useHttps = it })
            }
        }
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(value = username, onValueChange = { username = it }, label = { Text("Benutzername") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = password, onValueChange = { password = it }, label = { Text("Passwort") },
            visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(value = remotePath, onValueChange = { remotePath = it }, label = { Text("Basis-Ordner auf der NAS") }, modifier = Modifier.fillMaxWidth())

        Spacer(Modifier.height(20.dp))
        val canTest = protocol == "WEBDAV" && host.isNotBlank() && username.isNotBlank() && password.isNotBlank() && port.toIntOrNull() != null

        OutlinedButton(
            onClick = {
                testing = true
                testResult = null
                scope.launch {
                    val client = de.example.nasphotosync.webdav.WebDavClient(
                        "${if (useHttps) "https" else "http"}://$host:${port.toIntOrNull() ?: 5006}", username, password
                    )
                    val result = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { client.testConnection() }
                    testing = false
                    testResult = if (result.isSuccess) "✓ Verbindung erfolgreich!" else "✗ Fehlgeschlagen: ${result.exceptionOrNull()?.message}"
                }
            },
            enabled = canTest && !testing,
            modifier = Modifier.fillMaxWidth()
        ) { Text(if (testing) "Teste..." else "Verbindung testen") }

        testResult?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, fontSize = 13.sp)
        }

        Spacer(Modifier.height(16.dp))
        val canSave = (protocol == "WEBDAV" || protocol == "SMB") && name.isNotBlank() && host.isNotBlank() && username.isNotBlank()
        Button(
            onClick = {
                scope.launch {
                    val id = java.util.UUID.randomUUID().toString()
                    db.nasConnectionDao().upsert(
                        NasConnectionEntity(id, name, protocol, host, port.toIntOrNull() ?: 5006, useHttps, username, remotePath, System.currentTimeMillis())
                    )
                    credentials.savePassword(id, password)
                    onDone()
                }
            },
            enabled = canSave,
            colors = ButtonDefaults.buttonColors(containerColor = de.example.nasphotosync.ui.theme.NapsPrimary),
            modifier = Modifier.fillMaxWidth()
        ) { Text("Speichern") }
    }
}

private fun labelFor(proto: String) = when (proto) {
    "SMB" -> "SMB"
    "WEBDAV" -> "WebDAV"
    "SFTP" -> "SFTP"
    "UGREEN_LINK" -> "UGREENlink"
    else -> proto
}
