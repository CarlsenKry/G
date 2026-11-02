package com.krypton.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun PlaylistScreen(
    modifier: Modifier = Modifier,
    playlists: List<UserPlaylist>,
    tracks: List<Track>,
    onImport: () -> Unit,
    onCreate: (String) -> Unit,
    onOpen: (UserPlaylist) -> Unit,
    onPlay: (UserPlaylist) -> Unit,
    onRename: (String, String) -> Unit,
    onDelete: (String) -> Unit,
    onExport: (UserPlaylist) -> Unit
) {
    var createDialog by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    Column(modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("${playlists.size} ${if (playlists.size == 1) "PLAYLIST" else "PLAYLISTS"}", color = TextMuted, fontSize = 11.sp, letterSpacing = 1.8.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onImport, contentPadding = PaddingValues(horizontal = 7.dp)) {
                Icon(Icons.Default.FileOpen, contentDescription = null, tint = Accent, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(4.dp))
                Text("IMPORT M3U", color = Accent, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            }
            TextButton(onClick = { newName = ""; createDialog = true }, contentPadding = PaddingValues(horizontal = 7.dp)) {
                Icon(Icons.Default.Add, contentDescription = null, tint = Accent, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(2.dp))
                Text("NEW", color = Accent, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }
        if (playlists.isEmpty()) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 50.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(78.dp).clip(RoundedCornerShape(24.dp)).background(SurfaceRaised), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.QueueMusic, null, tint = Accent, modifier = Modifier.size(40.dp))
                }
                Spacer(Modifier.height(14.dp))
                Text("No playlists yet", color = TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Text("Create a playlist or import an existing M3U file.", color = TextMuted, fontSize = 12.sp)
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(playlists, key = { it.id }) { playlist ->
                    PlaylistCard(
                        playlist = playlist,
                        tracks = tracks,
                        onOpen = { onOpen(playlist) },
                        onPlay = { onPlay(playlist) },
                        onRename = { onRename(playlist.id, it) },
                        onDelete = { onDelete(playlist.id) },
                        onExport = { onExport(playlist) }
                    )
                }
            }
        }
    }

    if (createDialog) {
        NamePlaylistDialog(
            title = "Create playlist",
            value = newName,
            onValueChange = { newName = it },
            confirmLabel = "CREATE",
            onDismiss = { createDialog = false },
            onConfirm = { onCreate(newName); createDialog = false }
        )
    }
}

@Composable
private fun PlaylistCard(
    playlist: UserPlaylist,
    tracks: List<Track>,
    onOpen: () -> Unit,
    onPlay: () -> Unit,
    onRename: (String) -> Unit,
    onDelete: () -> Unit,
    onExport: () -> Unit
) {
    val availableTracks = playlist.trackIds.mapNotNull { id -> tracks.firstOrNull { it.id == id } }
    val duration = availableTracks.sumOf { it.durationMs }
    var menuExpanded by remember { mutableStateOf(false) }
    var renameDialog by remember { mutableStateOf(false) }
    var deleteDialog by remember { mutableStateOf(false) }
    var renameValue by remember(playlist.name) { mutableStateOf(playlist.name) }

    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Surface)
            .clickable(onClick = onOpen).padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (availableTracks.isNotEmpty()) {
            TrackArtwork(track = availableTracks.first(), modifier = Modifier.size(68.dp), cornerRadius = 17.dp)
        } else {
            Box(Modifier.size(68.dp).clip(RoundedCornerShape(17.dp)).background(Brush.linearGradient(listOf(Color(0xFF252930), Color(0xFF111419)))), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.QueueMusic, contentDescription = null, tint = Accent, modifier = Modifier.size(34.dp))
            }
        }
        Spacer(Modifier.width(13.dp))
        Column(Modifier.weight(1f)) {
            Text(playlist.name, color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(4.dp))
            Text("${availableTracks.size} songs  ·  ${formatDurationShort(duration)}", color = TextMuted, fontSize = 11.sp)
        }
        IconButton(onClick = onPlay, enabled = playlist.trackIds.isNotEmpty(), modifier = Modifier.size(42.dp)) {
            Icon(Icons.Default.PlayCircleFilled, contentDescription = "Play ${playlist.name}", tint = if (playlist.trackIds.isNotEmpty()) Accent else TextMuted, modifier = Modifier.size(31.dp))
        }
        Box {
            IconButton(onClick = { menuExpanded = true }, modifier = Modifier.size(34.dp)) { Icon(Icons.Default.MoreVert, "Playlist options", tint = TextMuted) }
            DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }, containerColor = SurfaceRaised) {
                DropdownMenuItem(text = { Text("Open playlist") }, leadingIcon = { Icon(Icons.Default.FolderOpen, null) }, onClick = { menuExpanded = false; onOpen() })
                DropdownMenuItem(text = { Text("Rename") }, leadingIcon = { Icon(Icons.Default.Edit, null) }, onClick = { menuExpanded = false; renameDialog = true })
                DropdownMenuItem(text = { Text("Export M3U") }, leadingIcon = { Icon(Icons.Default.FileDownload, null) }, onClick = { menuExpanded = false; onExport() })
                DropdownMenuItem(text = { Text("Delete", color = MaterialTheme.colorScheme.error) }, leadingIcon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) }, onClick = { menuExpanded = false; deleteDialog = true })
            }
        }
    }

    if (renameDialog) NamePlaylistDialog(
        title = "Rename playlist", value = renameValue, onValueChange = { renameValue = it }, confirmLabel = "SAVE",
        onDismiss = { renameDialog = false }, onConfirm = { onRename(renameValue); renameDialog = false }
    )
    if (deleteDialog) AlertDialog(
        onDismissRequest = { deleteDialog = false },
        title = { Text("Delete playlist?") },
        text = { Text("${playlist.name} will be removed. Your music files will not be deleted.") },
        confirmButton = { TextButton(onClick = { onDelete(); deleteDialog = false }) { Text("DELETE", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = { deleteDialog = false }) { Text("CANCEL") } },
        containerColor = SurfaceRaised
    )
}

@Composable
internal fun PlaylistDetailScreen(
    modifier: Modifier = Modifier,
    playlist: UserPlaylist,
    tracks: List<Track>,
    onPlay: (UserPlaylist, Track?) -> Unit,
    onShuffle: (UserPlaylist) -> Unit,
    onAddSongs: (List<Long>) -> Unit,
    onRemoveTrack: (Long) -> Unit,
    onExport: (UserPlaylist) -> Unit
) {
    val playlistTracks = remember(playlist.trackIds, tracks) { playlist.trackIds.mapNotNull { id -> tracks.firstOrNull { it.id == id } } }
    val context = androidx.compose.ui.platform.LocalContext.current
    val sortPrefs = remember(context, playlist.id) { context.getSharedPreferences("krypton_playlist_sort", android.content.Context.MODE_PRIVATE) }
    var detailSort by remember(playlist.id) { mutableStateOf(sortPrefs.getString(playlist.id, "playlist") ?: "playlist") }
    var orderMenuExpanded by remember { mutableStateOf(false) }
    var showAddDialog by remember { mutableStateOf(false) }
    LaunchedEffect(detailSort, playlist.id) { sortPrefs.edit().putString(playlist.id, detailSort).apply() }
    val displayTracks = remember(playlistTracks, detailSort) {
        when (detailSort) {
            "title_asc" -> playlistTracks.sortedBy { it.title.lowercase() }
            "title_desc" -> playlistTracks.sortedByDescending { it.title.lowercase() }
            else -> playlistTracks
        }
    }
    val displayPlaylist = remember(playlist, displayTracks) { playlist.copy(trackIds = displayTracks.map { it.id }) }
    Column(modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("${displayTracks.size} SONGS", color = TextMuted, fontSize = 10.sp, letterSpacing = 1.8.sp, fontWeight = FontWeight.Bold)
                Text(formatDurationShort(displayTracks.sumOf { it.durationMs }), color = TextMuted, fontSize = 11.sp)
            }
            IconButton(onClick = { onShuffle(displayPlaylist) }, enabled = displayTracks.isNotEmpty()) { Icon(Icons.Default.Shuffle, "Shuffle playlist", tint = if (displayTracks.isNotEmpty()) Accent else TextMuted) }
            Box {
                IconButton(onClick = { orderMenuExpanded = true }) { Icon(Icons.Default.Sort, "Sort playlist", tint = Accent) }
                DropdownMenu(expanded = orderMenuExpanded, onDismissRequest = { orderMenuExpanded = false }, containerColor = SurfaceRaised) {
                    listOf("playlist" to "Playlist order", "title_asc" to "Title A–Z", "title_desc" to "Title Z–A").forEach { (key, label) ->
                        DropdownMenuItem(
                            text = { Text(label, color = if (detailSort == key) Accent else TextPrimary) },
                            trailingIcon = { if (detailSort == key) Icon(Icons.Default.Check, null, tint = Accent) },
                            onClick = { detailSort = key; orderMenuExpanded = false }
                        )
                    }
                }
            }
            IconButton(onClick = { onExport(displayPlaylist) }) { Icon(Icons.Default.FileDownload, "Export M3U", tint = TextMuted) }
            IconButton(onClick = { showAddDialog = true }) { Icon(Icons.Default.Add, "Add songs", tint = Accent) }
        }
        if (playlistTracks.isEmpty()) {
            Column(Modifier.fillMaxWidth().padding(34.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.MusicNote, null, tint = Accent, modifier = Modifier.size(42.dp))
                Spacer(Modifier.height(12.dp))
                Text("This playlist is empty", color = TextPrimary, fontWeight = FontWeight.Bold)
                Text("Add songs from your library to get started.", color = TextMuted, fontSize = 12.sp)
                Spacer(Modifier.height(12.dp))
                Button(onClick = { showAddDialog = true }, colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Background)) { Text("ADD SONGS") }
            }
        } else {
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                items(displayTracks, key = { it.id }) { track ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Surface).clickable { onPlay(displayPlaylist, track) }.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TrackArtwork(track = track, modifier = Modifier.size(48.dp), cornerRadius = 12.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(track.title, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(track.artist, color = TextMuted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        IconButton(onClick = { onPlay(displayPlaylist, track) }) { Icon(Icons.Default.PlayArrow, "Play", tint = Accent) }
                        IconButton(onClick = { onRemoveTrack(track.id) }) { Icon(Icons.Default.Close, "Remove from playlist", tint = TextMuted) }
                    }
                }
            }
        }
    }
    if (showAddDialog) AddSongsDialog(
        tracks = tracks,
        existingIds = playlist.trackIds.toSet(),
        onDismiss = { showAddDialog = false },
        onAdd = { ids -> onAddSongs(ids); showAddDialog = false }
    )
}

@Composable
private fun AddSongsDialog(tracks: List<Track>, existingIds: Set<Long>, onDismiss: () -> Unit, onAdd: (List<Long>) -> Unit) {
    var selected by remember { mutableStateOf(existingIds) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add songs") },
        text = {
            LazyColumn(Modifier.heightIn(max = 430.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                items(tracks, key = { it.id }) { track ->
                    val checked = track.id in selected
                    Row(Modifier.fillMaxWidth().clickable { selected = if (checked) selected - track.id else selected + track.id }.padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = checked, onCheckedChange = { value -> selected = if (value) selected + track.id else selected - track.id }, colors = CheckboxDefaults.colors(checkedColor = Accent))
                        TrackArtwork(track = track, modifier = Modifier.size(38.dp), cornerRadius = 9.dp)
                        Spacer(Modifier.width(9.dp))
                        Column(Modifier.weight(1f)) {
                            Text(track.title, color = TextPrimary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(track.artist, color = TextMuted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onAdd(selected.filterNot { it in existingIds }) }) { Text("ADD", color = Accent) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("CANCEL") } },
        containerColor = SurfaceRaised
    )
}

@Composable
private fun NamePlaylistDialog(title: String, value: String, onValueChange: (String) -> Unit, confirmLabel: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Text("PLAYLIST NAME", color = TextMuted, fontSize = 9.sp, letterSpacing = 1.5.sp)
                Spacer(Modifier.height(8.dp))
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    singleLine = true,
                    textStyle = androidx.compose.ui.text.TextStyle(color = TextPrimary, fontSize = 15.sp),
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Surface).padding(12.dp),
                    decorationBox = { inner -> if (value.isEmpty()) Text("e.g. Amapiano", color = TextMuted); inner() }
                )
            }
        },
        confirmButton = { TextButton(onClick = onConfirm, enabled = value.isNotBlank()) { Text(confirmLabel, color = Accent) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("CANCEL") } },
        containerColor = SurfaceRaised
    )
}

internal fun formatDurationShort(durationMs: Long): String {
    val totalMinutes = (durationMs.coerceAtLeast(0L) / 60_000L)
    val hours = totalMinutes / 60L
    val minutes = totalMinutes % 60L
    return if (hours > 0L) "${hours}h ${minutes.toString().padStart(2, '0')}m" else "${minutes}m"
}
