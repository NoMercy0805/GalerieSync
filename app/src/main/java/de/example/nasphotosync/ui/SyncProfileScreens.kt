package de.example.nasphotosync.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import de.example.nasphotosync.data.LocalAlbum
import de.example.nasphotosync.data.MediaStoreRepository
import de.example.nasphotosync.data.SyncDatabase
import de.example.nasphotosync.data.SyncDirection
import de.example.nasphotosync.data.SyncProfileEntity
import de.example.nasphotosync.ui.theme.NapsPrimary
import de.example.nasphotosync.ui.theme.NapsSurface
import kotlinx.coroutines.launch

@Composable
fun SyncProfilesScreen(onAddProfile: () -> Unit) {
    val context = LocalContext.current
    val db = remember { SyncDatabase.getInstance(context) }
    val profiles by db.syncProfileDao().observeAll().collectAsStateWithLifecycle(initialValue = emptyList())

    Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Synchronisationen", fontSize = 22.sp, fontWeight = FontWeight.Bold)
            IconButton(onClick = onAddProfile) { Icon(Icons.Default.Add, contentDescription = "Neue Synchronisation") }
        }
        Spacer(Modifier.height(12.dp))

        if (profiles.isEmpty()) {
            Text("Noch keine Synchronisation eingerichtet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(profiles, key = { it.id }) { profile -> ProfileRow(profile) }
            }
        }
    }
}

@Composable
private fun ProfileRow(profile: SyncProfileEntity) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(NapsSurface)
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Sync, contentDescription = null, tint = NapsPrimary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Text(profile.name, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
        }
        Spacer(Modifier.height(4.dp))
        Text("${profile.sourceLabel} ${directionArrow(profile.direction)}", fontSize = 12.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(7.dp).clip(RoundedCornerShape(4.dp)).background(if (profile.isActive) de.example.nasphotosync.ui.theme.NapsSuccess else Color.Gray))
            Spacer(Modifier.width(6.dp))
            Text(
                if (profile.isActive) "Aktiv" else "Inaktiv",
                fontSize = 11.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun directionArrow(direction: String) = when (direction) {
    "PHONE_TO_NAS" -> "→ NAS"
    "NAS_TO_PHONE" -> "→ Handy"
    else -> "↔ NAS"
}

@Composable
fun NewSyncProfileScreen(onDone: () -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val db = remember { SyncDatabase.getInstance(context) }
    val mediaStore = remember { MediaStoreRepository(context) }
    val scope = rememberCoroutineScope()

    val nasConnections by db.nasConnectionDao().observeAll().collectAsStateWithLifecycle(initialValue = emptyList())
    var direction by remember { mutableStateOf(SyncDirection.PHONE_TO_NAS) }
    var selectedNasId by remember { mutableStateOf<String?>(null) }
    var albums by remember { mutableStateOf<List<LocalAlbum>>(emptyList()) }
    var selectedAlbum by remember { mutableStateOf<LocalAlbum?>(null) }
    var targetPath by remember { mutableStateOf("") }

    LaunchedEffect(Unit) { albums = mediaStore.listAlbums() }
    LaunchedEffect(nasConnections) { if (selectedNasId == null) selectedNasId = nasConnections.firstOrNull()?.id }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(androidx.compose.foundation.rememberScrollState())
            .padding(20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Zurück") }
            Text("Neue Synchronisation", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(16.dp))

        if (nasConnections.isEmpty()) {
            Text("Zuerst im Tab \"NAS\" eine Verbindung einrichten.", color = MaterialTheme.colorScheme.error)
            return@Column
        }

        Text("① Richtung", fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Spacer(Modifier.height(8.dp))
        DirectionCard("Handy → NAS", "Fotos vom Smartphone auf die NAS", SyncDirection.PHONE_TO_NAS, direction) { direction = it }
        Spacer(Modifier.height(8.dp))
        DirectionCard("NAS → Handy", "Fotos von der NAS aufs Smartphone", SyncDirection.NAS_TO_PHONE, direction) { direction = it }
        Spacer(Modifier.height(8.dp))
        DirectionCard("Bidirektional", "Beide Richtungen synchronisieren", SyncDirection.BIDIRECTIONAL, direction) { direction = it }

        Spacer(Modifier.height(20.dp))
        Text("NAS-Verbindung", fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Spacer(Modifier.height(8.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(nasConnections, key = { it.id }) { conn ->
                val active = conn.id == selectedNasId
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(if (active) NapsPrimary else NapsSurface)
                        .clickable { selectedNasId = conn.id }
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    Text(conn.name, color = if (active) Color.White else MaterialTheme.colorScheme.onSurface, fontSize = 13.sp)
                }
            }
        }

        Spacer(Modifier.height(20.dp))
        Text("② Quellordner (Handy)", fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Spacer(Modifier.height(8.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(albums, key = { it.bucketId }) { album ->
                val active = album.bucketId == selectedAlbum?.bucketId
                Column(
                    modifier = Modifier
                        .width(90.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (active) NapsPrimary.copy(alpha = 0.25f) else NapsSurface)
                        .clickable { selectedAlbum = album }
                        .padding(8.dp)
                ) {
                    if (album.coverUri != null) {
                        AsyncImage(
                            model = album.coverUri, contentDescription = null, contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxWidth().height(70.dp).clip(RoundedCornerShape(8.dp))
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(album.name, fontSize = 11.sp, maxLines = 1)
                    Text("${album.itemCount}", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        Spacer(Modifier.height(20.dp))
        Text("Zielordner (NAS)", fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = targetPath, onValueChange = { targetPath = it },
            placeholder = { Text("/Fotos/Handy") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(24.dp))
        val canSave = selectedNasId != null && selectedAlbum != null && targetPath.isNotBlank()
        Button(
            onClick = {
                scope.launch {
                    db.syncProfileDao().upsert(
                        SyncProfileEntity(
                            id = java.util.UUID.randomUUID().toString(),
                            name = "${selectedAlbum!!.name} ${directionArrow(direction.name)}",
                            nasConnectionId = selectedNasId!!,
                            direction = direction.name,
                            sourceBucketId = selectedAlbum!!.bucketId,
                            sourceLabel = selectedAlbum!!.name,
                            targetPath = targetPath,
                            isActive = true,
                            lastSyncAt = null
                        )
                    )
                    onDone()
                }
            },
            enabled = canSave,
            colors = ButtonDefaults.buttonColors(containerColor = NapsPrimary),
            modifier = Modifier.fillMaxWidth()
        ) { Text("Speichern") }
    }
}

@Composable
private fun DirectionCard(title: String, subtitle: String, value: SyncDirection, selected: SyncDirection, onSelect: (SyncDirection) -> Unit) {
    val active = value == selected
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (active) NapsPrimary.copy(alpha = 0.2f) else NapsSurface)
            .clickable { onSelect(value) }
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = active, onClick = { onSelect(value) }, colors = RadioButtonDefaults.colors(selectedColor = NapsPrimary))
        Column {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
