package com.krypton.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch

private enum class LibrarySort(val key: String, val label: String) {
    RECENT("recent", "Most Recent"), TITLE("title", "Title"), MOST_PLAYS("most_plays", "Most Plays");

    companion object {
        fun fromKey(key: String?): LibrarySort = entries.firstOrNull { it.key == key } ?: RECENT
    }
}

@Composable
internal fun LibraryScreen(
    modifier: Modifier = Modifier,
    tracks: List<Track>,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    onPlay: (List<Track>, Track?) -> Unit,
    onQueue: (Track) -> Unit,
    playCount: (Long) -> Int,
    isFavorite: (Long) -> Boolean,
    onToggleFavorite: (Long) -> Unit,
    statsRevision: Int,
    onOpenPlaylists: () -> Unit,
    onOpenStatistics: () -> Unit
) {
    val context = LocalContext.current
    val sortPrefs = remember(context) { context.getSharedPreferences("krypton_library_sort", android.content.Context.MODE_PRIVATE) }
    var sortMode by remember(sortPrefs) { mutableStateOf(LibrarySort.fromKey(sortPrefs.getString("mode", "recent"))) }
    var inverted by remember(sortPrefs) { mutableStateOf(sortPrefs.getBoolean("inverted", false)) }
    var sortMenuExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(sortMode, inverted) {
        sortPrefs.edit().putString("mode", sortMode.key).putBoolean("inverted", inverted).apply()
    }

    val sortedLibrary = remember(tracks, sortMode, inverted, statsRevision) {
        when (sortMode) {
            LibrarySort.RECENT -> if (inverted) tracks.sortedBy { it.dateAddedSeconds } else tracks.sortedByDescending { it.dateAddedSeconds }
            LibrarySort.TITLE -> if (inverted) tracks.sortedByDescending { it.title.lowercase() } else tracks.sortedBy { it.title.lowercase() }
            LibrarySort.MOST_PLAYS -> if (inverted) tracks.sortedBy { playCount(it.id) } else tracks.sortedByDescending { playCount(it.id) }
        }
    }
    val visibleTracks = remember(sortedLibrary, searchQuery) {
        if (searchQuery.isBlank()) sortedLibrary else sortedLibrary.filter {
            it.title.contains(searchQuery, ignoreCase = true) || it.artist.contains(searchQuery, ignoreCase = true)
        }
    }

    Column(modifier.fillMaxSize()) {
        LibraryHeroCarousel(
            trackCount = tracks.size,
            hasTracks = tracks.isNotEmpty(),
            onPlayLibrary = { onPlay(sortedLibrary, null) },
            onOpenPlaylists = onOpenPlaylists,
            onOpenStatistics = onOpenStatistics
        )
        SearchField(value = searchQuery, onValueChange = onSearchChange)
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("YOUR COLLECTION", color = TextMuted, fontSize = 10.sp, letterSpacing = 2.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Box {
                Box(
                    modifier = Modifier.clip(RoundedCornerShape(22.dp)).background(Surface)
                        .clickable { sortMenuExpanded = true }.padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Sort, contentDescription = null, tint = Accent, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(7.dp))
                        Text("Sort by", color = TextPrimary, fontSize = 11.sp)
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = TextMuted)
                    }
                }
                DropdownMenu(expanded = sortMenuExpanded, onDismissRequest = { sortMenuExpanded = false }, containerColor = SurfaceRaised) {
                    LibrarySort.entries.forEach { mode ->
                        DropdownMenuItem(
                            text = { Text(mode.label, color = if (sortMode == mode) Accent else TextPrimary) },
                            leadingIcon = {
                                Icon(
                                    when (mode) {
                                        LibrarySort.RECENT -> Icons.Default.History
                                        LibrarySort.TITLE -> Icons.Default.SortByAlpha
                                        LibrarySort.MOST_PLAYS -> Icons.Default.BarChart
                                    }, null, tint = if (sortMode == mode) Accent else TextMuted
                                )
                            },
                            trailingIcon = { if (sortMode == mode) Icon(Icons.Default.Check, null, tint = Accent) },
                            onClick = { sortMode = mode; sortMenuExpanded = false }
                        )
                    }
                }
            }
            Spacer(Modifier.width(8.dp))
            Box(Modifier.width(1.dp).height(24.dp).background(DividerColor))
            Spacer(Modifier.width(8.dp))
            val ascending = when (sortMode) {
                // Most Recent and Most Plays are descending by default; Title is A–Z.
                LibrarySort.RECENT, LibrarySort.MOST_PLAYS -> inverted
                LibrarySort.TITLE -> !inverted
            }
            Box(
                modifier = Modifier.size(38.dp).clip(CircleShape)
                    .background(if (inverted) Color(0xFF252A31) else SurfaceRaised)
                    .clickable { inverted = !inverted },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (ascending) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                    contentDescription = if (ascending) "Currently ascending. Reverse to descending" else "Currently descending. Reverse to ascending",
                    tint = Accent,
                    modifier = Modifier.size(19.dp)
                )
            }
        }
        if (visibleTracks.isEmpty()) {
            EmptyLibrary(isSearching = searchQuery.isNotBlank())
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(start = 18.dp, end = 18.dp, bottom = 18.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                items(visibleTracks, key = { it.id }) { track ->
                    TrackRow(track = track, favorite = isFavorite(track.id), onPlay = { onPlay(visibleTracks, track) }, onQueue = { onQueue(track) }, onToggleFavorite = { onToggleFavorite(track.id) })
                }
            }
        }
    }
}

@Composable
private fun LibraryHeroCarousel(
    trackCount: Int,
    hasTracks: Boolean,
    onPlayLibrary: () -> Unit,
    onOpenPlaylists: () -> Unit,
    onOpenStatistics: () -> Unit
) {
    val pagerState = rememberPagerState(pageCount = { 3 })
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxWidth()) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxWidth().height(100.dp),
            contentPadding = PaddingValues(horizontal = 18.dp),
            pageSpacing = 9.dp
        ) { page ->
            val heroBrush = Brush.linearGradient(listOf(Color(0xFF1A1C21), Color(0xFF101216), Color(0xFF1D1F24)))
            Row(
                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(22.dp)).background(heroBrush).padding(horizontal = 15.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                    when (page) {
                        0 -> {
                            Text("YOUR MUSIC. YOUR MOMENT.", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Spacer(Modifier.height(5.dp))
                            Text(if (trackCount == 0) "Your offline listening space" else "$trackCount tracks · Stored on this device", color = Color(0xFFB7BBC2), fontSize = 10.sp, maxLines = 1)
                            Spacer(Modifier.height(8.dp))
                            HeroAction("PLAY LIBRARY", Icons.Default.PlayArrow, enabled = hasTracks, onClick = onPlayLibrary)
                        }
                        1 -> {
                            Text("YOUR PLAYLISTS.", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
                            Spacer(Modifier.height(5.dp))
                            Text("Create, organize and import M3U lists", color = Color(0xFFB7BBC2), fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Spacer(Modifier.height(8.dp))
                            HeroAction("OPEN PLAYLISTS", Icons.Default.PlaylistPlay, onClick = onOpenPlaylists)
                        }
                        else -> {
                            Text("YOUR LISTENING.", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
                            Spacer(Modifier.height(5.dp))
                            Text("See your time, plays and favorites", color = Color(0xFFB7BBC2), fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Spacer(Modifier.height(8.dp))
                            HeroAction("VIEW STATISTICS", Icons.Default.BarChart, onClick = onOpenStatistics)
                        }
                    }
                }
                Box(
                    modifier = Modifier.size(52.dp).clip(RoundedCornerShape(16.dp)).background(Color(0xFF22262C)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        when (page) {
                            0 -> Icons.Default.Headphones
                            1 -> Icons.Default.QueueMusic
                            else -> Icons.Default.BarChart
                        }, contentDescription = null, tint = Accent, modifier = Modifier.size(32.dp)
                    )
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 1.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            repeat(3) { index ->
                val active = pagerState.currentPage == index
                Box(
                    Modifier.padding(horizontal = 3.dp).size(width = if (active) 16.dp else 6.dp, height = 6.dp)
                        .clip(CircleShape).background(if (active) Accent else Color(0xFF434851))
                        .clickable { scope.launch { pagerState.animateScrollToPage(index) } }
                )
            }
        }
    }
}

@Composable
private fun HeroAction(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, enabled: Boolean = true, onClick: () -> Unit) {
    FilledTonalButton(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = Accent,
            contentColor = Background,
            disabledContainerColor = Color(0xFF343941),
            disabledContentColor = TextMuted
        ),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
        modifier = Modifier.height(31.dp)
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(4.dp))
        Text(label, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.7.sp)
    }
}

@Composable
private fun SearchField(value: String, onValueChange: (String) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp).padding(top = 10.dp)
            .clip(RoundedCornerShape(15.dp)).background(Surface).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.Search, contentDescription = null, tint = TextMuted, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.weight(1f),
            singleLine = true,
            textStyle = androidx.compose.ui.text.TextStyle(color = TextPrimary, fontSize = 14.sp),
            decorationBox = { innerTextField ->
                if (value.isEmpty()) Text("Search songs or artists", color = TextMuted, fontSize = 13.sp)
                innerTextField()
            }
        )
        if (value.isNotEmpty()) IconButton(onClick = { onValueChange("") }, modifier = Modifier.size(24.dp)) {
            Icon(Icons.Default.Close, contentDescription = "Clear search", tint = TextMuted, modifier = Modifier.size(17.dp))
        }
    }
}

@Composable
private fun TrackRow(track: Track, favorite: Boolean, onPlay: () -> Unit, onQueue: () -> Unit, onToggleFavorite: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(17.dp)).background(Surface)
            .clickable(onClick = onPlay).padding(start = 11.dp, end = 5.dp, top = 9.dp, bottom = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TrackArtwork(track = track, modifier = Modifier.size(52.dp), cornerRadius = 13.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(track.title, color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(3.dp))
            Text(track.artist, color = TextMuted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        IconButton(onClick = onQueue, modifier = Modifier.size(32.dp)) { Icon(Icons.Default.Add, contentDescription = "Add to queue", tint = TextMuted, modifier = Modifier.size(19.dp)) }
        IconButton(onClick = onToggleFavorite, modifier = Modifier.size(32.dp)) { Icon(if (favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder, contentDescription = if (favorite) "Remove from favorites" else "Add to favorites", tint = if (favorite) Accent else TextMuted, modifier = Modifier.size(17.dp)) }
        IconButton(onClick = onPlay, modifier = Modifier.size(32.dp)) { Icon(Icons.Default.PlayArrow, contentDescription = "Play track", tint = Accent, modifier = Modifier.size(20.dp)) }
    }
}

@Composable
private fun EmptyLibrary(isSearching: Boolean) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 36.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(modifier = Modifier.size(76.dp).clip(CircleShape).background(SurfaceRaised), contentAlignment = Alignment.Center) {
            Icon(if (isSearching) Icons.Default.Search else Icons.Default.LibraryMusic, null, tint = Accent, modifier = Modifier.size(32.dp))
        }
        Spacer(Modifier.height(15.dp))
        Text(if (isSearching) "No matches found" else "Your library is waiting", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Spacer(Modifier.height(6.dp))
        Text(if (isSearching) "Try another title or artist." else "Add audio files to your device, then rescan your library.", color = TextMuted, fontSize = 12.sp)
    }
}
