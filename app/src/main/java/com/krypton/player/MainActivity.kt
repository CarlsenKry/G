package com.krypton.player

import android.Manifest
import android.content.pm.PackageManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

internal val Background = Color(0xFF000000)
internal val Surface = Color(0xFF0B0D10)
internal val SurfaceRaised = Color(0xFF171A1F)
internal val Accent = Color(0xFFE9EBEF)
internal val AccentDeep = Color(0xFFADB3BC)
internal val TextPrimary = Color(0xFFF4F5F7)
internal val TextMuted = Color(0xFF9298A1)
internal val DividerColor = Color(0xFF282C33)

internal data class QueueTrack(
    val index: Int,
    val title: String,
    val artist: String,
    val isCurrent: Boolean,
    val track: Track? = null
)

internal fun Track.toMediaItem(): MediaItem = MediaItem.Builder()
    .setMediaId(id.toString())
    .setUri(uri)
    .setMediaMetadata(
        MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(artist)
            .build()
    )
    .build()

class MainActivity : ComponentActivity() {
    private var controller by mutableStateOf<MediaController?>(null)
    private var tracks by mutableStateOf<List<Track>>(emptyList())
    private var playlists by mutableStateOf<List<UserPlaylist>>(emptyList())
    private lateinit var playlistRepository: PlaylistRepository
    private var exportTarget: UserPlaylist? = null
    private var lyricsTargetTrackId: Long? = null
    private var loadedLyrics by mutableStateOf<List<LyricLine>>(emptyList())
    private var loadedLyricsTrackId by mutableStateOf<Long?>(null)
    private var lyricsFolderTreeUri by mutableStateOf<Uri?>(null)
    private var lyricsGeneration = 0

    private val pickLyrics = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val targetId = lyricsTargetTrackId
            lyricsGeneration++
            runCatching { contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            lifecycleScope.launch {
                val parsed = withContext(Dispatchers.IO) {
                    runCatching { LyricsReader.readDocument(contentResolver, uri) }.getOrDefault(emptyList())
                }
                loadedLyrics = parsed
                loadedLyricsTrackId = targetId
            }
        }
    }

    private val pickLyricsFolder = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            runCatching { contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            lyricsFolderTreeUri = uri
            getSharedPreferences("krypton_lyrics", MODE_PRIVATE).edit().putString("folder_tree_uri", uri.toString()).apply()
        }
    }

    private val permission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) loadTracks()
    }

    private val importM3u = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) importPlaylist(uri)
    }

    private val exportM3u = registerForActivityResult(ActivityResultContracts.CreateDocument("audio/x-mpegurl")) { uri ->
        val playlist = exportTarget
        exportTarget = null
        if (uri != null && playlist != null) exportPlaylist(uri, playlist)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, true)
        window.statusBarColor = android.graphics.Color.BLACK
        window.navigationBarColor = android.graphics.Color.BLACK
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isStatusBarContrastEnforced = false
            window.isNavigationBarContrastEnforced = false
        }
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }
        playlistRepository = PlaylistRepository(this)
        playlists = playlistRepository.all()
        lyricsFolderTreeUri = getSharedPreferences("krypton_lyrics", MODE_PRIVATE)
            .getString("folder_tree_uri", null)?.let { runCatching { Uri.parse(it) }.getOrNull() }

        val token = SessionToken(this, android.content.ComponentName(this, PlaybackService::class.java))
        val future = MediaController.Builder(this, token).buildAsync()
        future.addListener({
            val connected = runCatching { future.get() }.getOrNull()
            if (connected != null && !isFinishing && !isDestroyed) controller = connected else connected?.release()
        }, MoreExecutors.directExecutor())

        val readPermission = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
        if (ContextCompat.checkSelfPermission(this, readPermission) == PackageManager.PERMISSION_GRANTED) loadTracks()
        else permission.launch(readPermission)

        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    background = Background,
                    surface = Surface,
                    primary = Accent,
                    secondary = AccentDeep,
                    onBackground = TextPrimary,
                    onSurface = TextPrimary,
                    onPrimary = Background
                )
            ) {
                KryptonApp(
                    tracks = tracks,
                    playlists = playlists,
                    controller = controller,
                    lyrics = loadedLyrics,
                    lyricsTrackId = loadedLyricsTrackId,
                    lyricsFolderTreeUri = lyricsFolderTreeUri,
                    onPickLyricsFolder = { pickLyricsFolder.launch(lyricsFolderTreeUri) },
                    onDetectLyrics = ::detectLyricsForTrack,
                    onCheckSidecarLyrics = ::checkSidecarLyrics,
                    onPickLyrics = { trackId ->
                        lyricsTargetTrackId = trackId
                        pickLyrics.launch(arrayOf("text/plain", "application/octet-stream", "*/*"))
                    },
                    onRefresh = ::loadTracks,
                    onPlay = { sequence, track -> playTracks(sequence, track?.let { selected -> sequence.indexOfFirst { it.id == selected.id }.takeIf { it >= 0 } } ?: 0) },
                    onQueue = { track -> controller?.addMediaItem(track.toMediaItem()) },
                    onOpenM3uImport = { importM3u.launch(arrayOf("audio/x-mpegurl", "audio/mpegurl", "application/vnd.apple.mpegurl", "text/plain", "*/*")) },
                    onExportPlaylist = ::beginExportPlaylist,
                    onCreatePlaylist = { name -> playlistRepository.create(name); reloadPlaylists() },
                    onRenamePlaylist = { id, name -> playlistRepository.rename(id, name); reloadPlaylists() },
                    onDeletePlaylist = { id -> playlistRepository.delete(id); reloadPlaylists() },
                    onAddPlaylistTracks = { id, ids -> playlistRepository.addTracks(id, ids); reloadPlaylists() },
                    onRemovePlaylistTrack = { id, trackId ->
                        val playlist = playlistRepository.all().firstOrNull { it.id == id }
                        if (playlist != null) playlistRepository.replaceTracks(id, playlist.trackIds.filterNot { it == trackId })
                        reloadPlaylists()
                    },
                    onPlayPlaylist = { playlist, startTrackId, shuffle ->
                        val original = playlist.trackIds.mapNotNull { id -> tracks.firstOrNull { it.id == id } }
                        val ordered = if (shuffle) original.shuffled() else original
                        if (ordered.isNotEmpty()) {
                            val start = startTrackId?.let { id -> ordered.indexOfFirst { it.id == id }.takeIf { it >= 0 } } ?: 0
                            playTracks(ordered, start)
                        }
                    }
                )
            }
        }
    }

    private fun detectLyricsForTrack(track: Track, folderTreeUri: Uri?) {
        val generation = ++lyricsGeneration
        loadedLyrics = emptyList()
        loadedLyricsTrackId = track.id
        lifecycleScope.launch {
            val detected = withContext(Dispatchers.IO) {
                runCatching { LyricsReader.detectForTrack(contentResolver, track, folderTreeUri) }
                    .getOrDefault(emptyList())
            }
            val stillCurrent = controller?.currentMediaItem?.mediaId == track.id.toString()
            if (generation == lyricsGeneration && stillCurrent) {
                loadedLyrics = detected
                loadedLyricsTrackId = track.id
            }
        }
    }

    private fun checkSidecarLyrics(track: Track, folderTreeUri: Uri?) {
        lifecycleScope.launch {
            val sidecar = withContext(Dispatchers.IO) {
                runCatching { LyricsReader.detectSidecarForTrack(contentResolver, track, folderTreeUri) }
                    .getOrDefault(emptyList())
            }
            if (sidecar.isNotEmpty() && controller?.currentMediaItem?.mediaId == track.id.toString() &&
                (loadedLyricsTrackId != track.id || loadedLyrics != sidecar)) {
                loadedLyrics = sidecar
                loadedLyricsTrackId = track.id
            }
        }
    }

    private fun playTracks(items: List<Track>, startIndex: Int) {
        val player = controller ?: return
        if (items.isEmpty()) return
        player.setMediaItems(items.map { it.toMediaItem() }, startIndex.coerceIn(items.indices), 0L)
        player.prepare()
        player.play()
    }

    private fun loadTracks() {
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { MusicLibrary.query(this@MainActivity) }.getOrDefault(emptyList()) }
            if (!isFinishing && !isDestroyed) tracks = result
        }
    }

    private fun reloadPlaylists() { playlists = playlistRepository.all() }

    private fun beginExportPlaylist(playlist: UserPlaylist) {
        exportTarget = playlist
        val safeName = playlist.name.trim().replace(Regex("[^A-Za-z0-9._ -]"), "_").ifBlank { "playlist" }
        exportM3u.launch("$safeName.m3u")
    }

    private fun importPlaylist(uri: Uri) {
        lifecycleScope.launch {
            val librarySnapshot = tracks
            val parsed = withContext(Dispatchers.IO) {
                runCatching {
                    val text = contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }.orEmpty()
                    val fileName = contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                        if (cursor.moveToFirst()) cursor.getString(cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME)) else null
                    }
                    val title = fileName?.substringBeforeLast('.', fileName) ?: "Imported playlist"
                    val ids = resolveM3uEntries(text, librarySnapshot)
                    title to ids
                }.getOrDefault("Imported playlist" to emptyList())
            }
            playlistRepository.imported(parsed.first, parsed.second)
            reloadPlaylists()
        }
    }

    private fun resolveM3uEntries(contents: String, library: List<Track>): List<Long> {
        val found = mutableListOf<Long>()
        contents.lineSequence().map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .forEach { line ->
                val decoded = runCatching { URLDecoder.decode(line.substringAfterLast('/'), StandardCharsets.UTF_8.name()) }.getOrDefault(line.substringAfterLast('/'))
                val base = decoded.substringAfterLast('\\').substringBeforeLast('.', decoded)
                val match = library.firstOrNull { track ->
                    track.uri.toString() == line ||
                        track.displayName.equals(decoded, ignoreCase = true) ||
                        track.title.equals(base, ignoreCase = true)
                }
                match?.let { found += it.id }
            }
        return found.distinct()
    }

    private fun exportPlaylist(uri: Uri, playlist: UserPlaylist) {
        val librarySnapshot = tracks
        lifecycleScope.launch(Dispatchers.IO) {
            val current = librarySnapshot.associateBy { it.id }
            val body = buildString {
                appendLine("#EXTM3U")
                playlist.trackIds.mapNotNull(current::get).forEach { track ->
                    appendLine("#EXTINF:${(track.durationMs / 1000L).coerceAtLeast(0L)},${track.artist} - ${track.title}")
                    appendLine(track.filePath?.takeIf { it.isNotBlank() } ?: track.uri.toString())
                }
            }
            runCatching { contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(body) } }
        }
    }

    override fun onDestroy() {
        controller?.release()
        controller = null
        super.onDestroy()
    }
}

@Composable
private fun KryptonApp(
    tracks: List<Track>,
    playlists: List<UserPlaylist>,
    controller: MediaController?,
    lyrics: List<LyricLine>,
    lyricsTrackId: Long?,
    lyricsFolderTreeUri: Uri?,
    onPickLyricsFolder: () -> Unit,
    onDetectLyrics: (Track, Uri?) -> Unit,
    onCheckSidecarLyrics: (Track, Uri?) -> Unit,
    onPickLyrics: (Long?) -> Unit,
    onRefresh: () -> Unit,
    onPlay: (List<Track>, Track?) -> Unit,
    onQueue: (Track) -> Unit,
    onOpenM3uImport: () -> Unit,
    onExportPlaylist: (UserPlaylist) -> Unit,
    onCreatePlaylist: (String) -> Unit,
    onRenamePlaylist: (String, String) -> Unit,
    onDeletePlaylist: (String) -> Unit,
    onAddPlaylistTracks: (String, List<Long>) -> Unit,
    onRemovePlaylistTrack: (String, Long) -> Unit,
    onPlayPlaylist: (UserPlaylist, Long?, Boolean) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val statsStore = remember(context) { PlaybackStatsStore(context) }
    var selectedTab by remember { mutableStateOf("Library") }
    val navigationHistory = remember { androidx.compose.runtime.mutableStateListOf<String>() }
    fun navigateTo(tab: String) {
        if (tab != selectedTab) {
            navigationHistory.add(selectedTab)
            selectedTab = tab
        }
    }
    fun navigateBack(fallback: String = "Library") {
        selectedTab = if (navigationHistory.isNotEmpty()) {
            navigationHistory.removeAt(navigationHistory.lastIndex)
        } else if (selectedTab != fallback) {
            fallback
        } else {
            selectedTab
        }
    }
    val activeTrack = tracks.firstOrNull { it.id.toString() == controller?.currentMediaItem?.mediaId }
    val activeAccent by rememberArtworkAccent(activeTrack)
    LaunchedEffect(activeTrack?.id, lyricsFolderTreeUri) {
        activeTrack?.let { onDetectLyrics(it, lyricsFolderTreeUri) }
    }
    LaunchedEffect(activeTrack?.id, lyricsFolderTreeUri, selectedTab) {
        val target = activeTrack ?: return@LaunchedEffect
        // While the Lyrics panel is open, watch for a sidecar added after the song started.
        if (selectedTab == "Lyrics") {
            while (true) {
                kotlinx.coroutines.delay(5_000L)
                if (activeTrack?.id == target.id) onCheckSidecarLyrics(target, lyricsFolderTreeUri)
            }
        }
    }
    var activePlaylistId by remember { mutableStateOf<String?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var currentTitle by remember { mutableStateOf("Nothing playing") }
    var currentArtist by remember { mutableStateOf("Choose a track from your library") }
    var isPlaying by remember { mutableStateOf(false) }
    var queue by remember { mutableStateOf(emptyList<QueueTrack>()) }
    var currentPosition by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var shuffleEnabled by remember { mutableStateOf(false) }
    var repeatMode by remember { mutableIntStateOf(Player.REPEAT_MODE_OFF) }
    var queueSheetOpen by remember { mutableStateOf(false) }
    BackHandler(enabled = true) {
        if (queueSheetOpen) queueSheetOpen = false else navigateBack()
    }
    fun startTrackAndOpenPlayer(sequence: List<Track>, selected: Track?) {
        val alreadyPlaying = selected != null && controller?.currentMediaItem?.mediaId == selected.id.toString()
        if (!alreadyPlaying) onPlay(sequence, selected)
        navigateTo("Queue")
    }
    var statsRevision by remember { mutableIntStateOf(0) }

    fun refreshPlayerState() {
        val player = controller ?: return
        currentTitle = player.mediaMetadata.title?.toString() ?: player.currentMediaItem?.mediaMetadata?.title?.toString() ?: "Nothing playing"
        currentArtist = player.mediaMetadata.artist?.toString() ?: player.currentMediaItem?.mediaMetadata?.artist?.toString() ?: "Krypton Player"
        isPlaying = player.isPlaying
        shuffleEnabled = player.shuffleModeEnabled
        repeatMode = player.repeatMode
        queue = (0 until player.mediaItemCount).map { index ->
            val item = player.getMediaItemAt(index)
            QueueTrack(
                index = index,
                title = item.mediaMetadata.title?.toString() ?: "Track ${index + 1}",
                artist = item.mediaMetadata.artist?.toString() ?: "Unknown artist",
                isCurrent = index == player.currentMediaItemIndex,
                track = tracks.firstOrNull { it.id.toString() == item.mediaId }
            )
        }
        currentPosition = player.currentPosition.coerceAtLeast(0L)
        duration = player.duration.takeIf { it > 0L } ?: 0L
    }

    DisposableEffect(controller) {
        val playerListener = object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) = refreshPlayerState()
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) = refreshPlayerState()
            override fun onIsPlayingChanged(playing: Boolean) = refreshPlayerState()
        }
        controller?.addListener(playerListener)
        refreshPlayerState()
        onDispose { controller?.removeListener(playerListener) }
    }

    val latestTracks by rememberUpdatedState(tracks)
    LaunchedEffect(controller) {
        var previousToken = ""
        var countedThisPlay = false
        var wasPlaying = false
        var previousTick = android.os.SystemClock.elapsedRealtime()
        var tick = 0
        while (true) {
            val player = controller
            if (player != null) {
                val mediaId = player.currentMediaItem?.mediaId.orEmpty()
                val token = "$mediaId:${player.currentMediaItemIndex}"
                val position = player.currentPosition.coerceAtLeast(0L)
                val itemDuration = player.duration.takeIf { it > 0L } ?: 0L
                if (token != previousToken) {
                    previousToken = token
                    countedThisPlay = false
                    wasPlaying = false
                }
                val now = android.os.SystemClock.elapsedRealtime()
                val currentTrack = mediaId.toLongOrNull()?.let { id -> latestTracks.firstOrNull { it.id == id } }
                if (player.isPlaying && currentTrack != null) {
                    val elapsed = if (wasPlaying) (now - previousTick).coerceIn(0L, 1500L) else 0L
                    if (elapsed > 0L) statsStore.recordListening(currentTrack, elapsed)
                    if (!countedThisPlay && itemDuration > 0L && position >= itemDuration / 2L) {
                        statsStore.recordQualifiedPlay(currentTrack)
                        countedThisPlay = true
                    }
                }
                wasPlaying = player.isPlaying
                previousTick = now
                tick++
                if (tick % 5 == 0) statsRevision++
                refreshPlayerState()
            }
            kotlinx.coroutines.delay(1000L)
        }
    }

    val activePlaylist = playlists.firstOrNull { it.id == activePlaylistId }

    Scaffold(
        containerColor = Background,
        bottomBar = {
            // No persistent Library/Queue tab strip. The compact now-playing bar is
            // present only during active playback and is the route back to the Player.
            if (isPlaying && controller?.currentMediaItem != null && selectedTab != "Queue") {
                MiniPlayerBar(
                    track = activeTrack,
                    title = currentTitle,
                    artist = currentArtist,
                    onOpen = { navigateTo("Queue") },
                    onPause = { controller?.pause() },
                    onNext = { controller?.seekToNextMediaItem() }
                )
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            when (selectedTab) {
                "Library" -> {
                    AppHeader(section = "Library", onRefresh = onRefresh, onSettings = { navigateTo("Settings") })
                    LibraryScreen(
                        modifier = Modifier.weight(1f),
                        tracks = tracks,
                        searchQuery = searchQuery,
                        onSearchChange = { searchQuery = it },
                        onPlay = { sequence, selected -> startTrackAndOpenPlayer(sequence, selected) },
                        onQueue = onQueue,
                        playCount = statsStore::playCount,
                        isFavorite = statsStore::isFavorite,
                        onToggleFavorite = { id -> statsStore.toggleFavorite(id); statsRevision++ },
                        statsRevision = statsRevision,
                        onOpenPlaylists = { navigateTo("Playlists") },
                        onOpenStatistics = { navigateTo("Statistics") }
                    )
                }
                "Queue" -> {
                    PlayerScreen(
                        modifier = Modifier.weight(1f),
                        track = activeTrack,
                        accent = activeAccent,
                        title = currentTitle,
                        artist = currentArtist,
                        playing = isPlaying,
                        positionMs = currentPosition,
                        durationMs = duration,
                        shuffleEnabled = shuffleEnabled,
                        repeatMode = repeatMode,
                        onOpenEqualizer = { navigateTo("Equalizer") },
                        onOpenQueue = { queueSheetOpen = true },
                        onOpenLyrics = { navigateTo("Lyrics") },
                        onPlayPause = { controller?.let { player -> if (player.isPlaying) player.pause() else player.play() } },
                        onPrevious = { controller?.seekToPreviousMediaItem() },
                        onNext = { controller?.seekToNextMediaItem() },
                        onSeek = { target -> controller?.seekTo(target) },
                        onToggleShuffle = { controller?.let { player -> player.shuffleModeEnabled = !player.shuffleModeEnabled; refreshPlayerState() } },
                        onCycleRepeat = {
                            controller?.let { player ->
                                player.repeatMode = when (player.repeatMode) {
                                    Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                                    Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                                    else -> Player.REPEAT_MODE_OFF
                                }
                                refreshPlayerState()
                            }
                        }
                    )
                }
                "Settings" -> {
                    AppHeader(section = "Settings", onBack = { navigateBack() })
                    SettingsScreen(
                        modifier = Modifier.weight(1f),
                        onOpenLockScreen = { context.startActivity(Intent(context, LockScreenActivity::class.java)) }
                    )
                }
                "Playlists" -> {
                    NestedHeader(title = "Playlists", onBack = { navigateBack("Library") })
                    PlaylistScreen(
                        modifier = Modifier.weight(1f), playlists = playlists, tracks = tracks,
                        onImport = onOpenM3uImport, onCreate = onCreatePlaylist,
                        onOpen = { playlist -> activePlaylistId = playlist.id; navigateTo("PlaylistDetail") },
                        onPlay = { playlist -> onPlayPlaylist(playlist, null, false); navigateTo("Queue") },
                        onRename = onRenamePlaylist, onDelete = onDeletePlaylist, onExport = onExportPlaylist
                    )
                }
                "PlaylistDetail" -> {
                    NestedHeader(title = activePlaylist?.name ?: "Playlist", onBack = { navigateBack("Playlists") })
                    if (activePlaylist != null) {
                        PlaylistDetailScreen(
                            modifier = Modifier.weight(1f), playlist = activePlaylist, tracks = tracks,
                            onPlay = { playlist, track -> onPlayPlaylist(playlist, track?.id, false); navigateTo("Queue") },
                            onShuffle = { playlist -> onPlayPlaylist(playlist, null, true); navigateTo("Queue") },
                            onAddSongs = { ids -> onAddPlaylistTracks(activePlaylist.id, ids) },
                            onRemoveTrack = { id -> onRemovePlaylistTrack(activePlaylist.id, id) },
                            onExport = onExportPlaylist
                        )
                    }
                }
                "Statistics" -> {
                    NestedHeader(title = "Listening Stats", onBack = { navigateBack("Library") })
                    StatisticsScreen(modifier = Modifier.weight(1f), stats = statsStore.snapshot(tracks))
                }
                "Equalizer" -> {
                    NestedHeader(title = "Equalizer", onBack = { navigateBack("Queue") })
                    EqualizerScreen(modifier = Modifier.weight(1f))
                }
                "Lyrics" -> {
                    NestedHeader(title = "Lyrics", onBack = { navigateBack("Queue") })
                    val activeTrackId = controller?.currentMediaItem?.mediaId?.toLongOrNull()
                    val activeLyrics = if (lyricsTrackId == activeTrackId) lyrics else emptyList()
                    LyricsScreen(
                        modifier = Modifier.weight(1f),
                        lines = activeLyrics,
                        positionMs = currentPosition,
                        accent = activeAccent,
                        onPickLyrics = { onPickLyrics(activeTrackId) },
                        onPickLyricsFolder = onPickLyricsFolder
                    )
                }
            }
        }
        if (selectedTab == "Queue" && queueSheetOpen) {
            QueueSheet(
                queue = queue,
                accent = activeAccent,
                onDismiss = { queueSheetOpen = false },
                onSelect = { index ->
                    controller?.seekTo(index, 0L)
                    controller?.play()
                    queueSheetOpen = false
                }
            )
        }
    }
}

@Composable
private fun MiniPlayerBar(
    track: Track?,
    title: String,
    artist: String,
    onOpen: () -> Unit,
    onPause: () -> Unit,
    onNext: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 7.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(SurfaceRaised)
            .clickable(onClick = onOpen)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TrackArtwork(track = track, modifier = Modifier.size(44.dp), cornerRadius = 10.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(2.dp))
            Text(artist, color = TextMuted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        IconButton(onClick = onPause, modifier = Modifier.size(38.dp)) {
            Icon(Icons.Default.Pause, contentDescription = "Pause playback", tint = TextPrimary, modifier = Modifier.size(21.dp))
        }
        IconButton(onClick = onNext, modifier = Modifier.size(38.dp)) {
            Icon(Icons.Default.SkipNext, contentDescription = "Next track", tint = TextPrimary, modifier = Modifier.size(22.dp))
        }
    }
}

@Composable
private fun AppHeader(
    section: String,
    onRefresh: (() -> Unit)? = null,
    onSettings: (() -> Unit)? = null,
    onBack: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 22.dp, end = 14.dp, top = 18.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(42.dp).clip(RoundedCornerShape(14.dp))
                .background(Brush.linearGradient(listOf(Accent, Color(0xFFB6BAC1)))),
            contentAlignment = Alignment.Center
        ) { Icon(Icons.Default.GraphicEq, contentDescription = null, tint = Background, modifier = Modifier.size(25.dp)) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("KRYPTON", color = Accent, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 3.sp)
            Text(section, color = TextPrimary, fontSize = 25.sp, fontWeight = FontWeight.Bold)
        }
        if (onRefresh != null) IconButton(onClick = onRefresh) {
            Icon(Icons.Default.Refresh, contentDescription = "Rescan music", tint = TextMuted)
        }
        if (onSettings != null) IconButton(onClick = onSettings) {
            Icon(Icons.Default.Settings, contentDescription = "Settings", tint = TextPrimary)
        }
        if (onBack != null) IconButton(onClick = onBack) {
            Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = TextPrimary)
        }
    }
}

@Composable
private fun NestedHeader(title: String, onBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = TextPrimary) }
        Text(title, color = TextPrimary, fontSize = 23.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun SettingsScreen(modifier: Modifier = Modifier, onOpenLockScreen: () -> Unit) {
    LazyColumn(modifier = modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 18.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { SettingsGroupLabel("PLAYBACK") }
        item { SettingsCard(Icons.Default.GraphicEq, "Audio engine", "Powered by AndroidX Media3 with background media-session playback.", "ACTIVE") }
        item { SettingsCard(Icons.Default.Lyrics, "Lyrics support", "Open the lyrics panel from the player edge and load a timed .lrc or .txt file for synchronized display.", "PLAYER INTEGRATION") }
        item { SettingsGroupLabel("PERSONALIZATION") }
        item { LockScreenSettingsCard(onOpen = onOpenLockScreen) }
        item { SettingsCard(Icons.Default.Equalizer, "Equalizer & sound effects", "Service-managed graphic EQ with device-supported bass boost and stereo virtualizer controls.", "PLAYBACK SESSION LINKED") }
        item { SettingsCard(Icons.Default.Palette, "Appearance", "Krypton is currently using its dark studio theme.", "DARK THEME") }
        item { Text("KRYPTON PLAYER  ·  OFFLINE FIRST", color = TextMuted, fontSize = 9.sp, letterSpacing = 1.5.sp, modifier = Modifier.fillMaxWidth().padding(vertical = 18.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center) }
    }
}

@Composable
private fun LockScreenSettingsCard(onOpen: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(17.dp))
            .background(Surface)
            .clickable(onClick = onOpen)
            .padding(15.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(42.dp).clip(RoundedCornerShape(13.dp)).background(SurfaceRaised),
            contentAlignment = Alignment.Center
        ) { Icon(Icons.Default.Lock, contentDescription = null, tint = Accent, modifier = Modifier.size(21.dp)) }
        Spacer(Modifier.width(13.dp))
        Column(Modifier.weight(1f)) {
            Text("Krypton lockscreen player", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text("Minimal black screen, artwork-reactive colors, queue and shuffle controls, and a slide-to-close handle. Android device security remains enabled.", color = TextMuted, fontSize = 11.sp, lineHeight = 16.sp)
            Spacer(Modifier.height(8.dp))
            Text("OPEN LOCKSCREEN PLAYER  →", color = Accent, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.1.sp)
        }
    }
}

@Composable
private fun SettingsGroupLabel(text: String) {
    Text(text, color = TextMuted, fontSize = 10.sp, letterSpacing = 2.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 4.dp, top = 10.dp, bottom = 2.dp))
}

@Composable
private fun SettingsCard(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, status: String) {
    Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(17.dp)).background(Surface).padding(15.dp), verticalAlignment = Alignment.Top) {
        Box(modifier = Modifier.size(42.dp).clip(RoundedCornerShape(13.dp)).background(SurfaceRaised), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = Accent, modifier = Modifier.size(21.dp))
        }
        Spacer(Modifier.width(13.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(subtitle, color = TextMuted, fontSize = 11.sp, lineHeight = 16.sp)
            Spacer(Modifier.height(8.dp))
            Text(status, color = Accent, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
        }
    }
}

