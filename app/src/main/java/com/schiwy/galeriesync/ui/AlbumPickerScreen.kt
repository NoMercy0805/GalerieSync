package com.schiwy.galeriesync.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.schiwy.galeriesync.data.LocalAlbum
import com.schiwy.galeriesync.data.MediaStoreRepository
import com.schiwy.galeriesync.data.SettingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@Composable
fun AlbumPickerScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    val settings = remember { SettingsRepository(context) }
    val mediaStore = remember { MediaStoreRepository(context) }
    val scope = rememberCoroutineScope()

    var albums by remember { mutableStateOf<List<LocalAlbum>>(emptyList()) }
    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }

    LaunchedEffect(Unit) {
        albums = mediaStore.listAlbums()
        selected = settings.selectedBuckets.first()
    }

    Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
        Text("Alben auswaehlen", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text(
            "Welche Alben sollen mit der NAS synchronisiert werden?",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(16.dp))

        LazyColumn(modifier = Modifier.weight(1f)) {
            items(albums, key = { it.bucketId }) { album ->
                val isSelected = album.bucketId in selected
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .then(Modifier),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = isSelected,
                        onCheckedChange = { checked ->
                            selected = if (checked) selected + album.bucketId else selected - album.bucketId
                        }
                    )
                    if (album.coverUri != null) {
                        AsyncImage(
                            model = album.coverUri,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp))
                        )
                        Spacer(Modifier.width(12.dp))
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(album.name, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                        Text("${album.itemCount} Fotos", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Button(
            onClick = {
                scope.launch {
                    settings.setSelectedBuckets(selected)
                    onDone()
                }
            },
            enabled = selected.isNotEmpty(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("${selected.size} Album(en) ausgewaehlt - Weiter")
        }
    }
}
