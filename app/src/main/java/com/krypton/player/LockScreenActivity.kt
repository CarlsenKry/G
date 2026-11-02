package com.krypton.player

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Krypton's custom media surface. It can be shown above Android's keyguard, but it does not
 * replace or dismiss the system's secure PIN/pattern/password screen.
 */
class LockScreenActivity : ComponentActivity() {
    private var controller by mutableStateOf<MediaController?>(null)
    private var tracks by mutableStateOf<List<Track>>(emptyList())
    private var connectionFuture: com.google.common.util.concurrent.ListenableFuture<MediaController>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
        window.statusBarColor = android.graphics.Color.BLACK
        window.navigationBarColor = android.graphics.Color.BLACK

        val token = SessionToken(this, android.content.ComponentName(this, PlaybackService::class.java))
        val future = MediaController.Builder(this, token).buildAsync()
        connectionFuture = future
        future.addListener({
            val connected = runCatching { future.get() }.getOrNull()
            if (connected != null && !isFinishing && !isDestroyed) controller = connected else connected?.release()
        }, MoreExecutors.directExecutor())

        lifecycleScope.launch {
            tracks = withContext(Dispatchers.IO) {
                runCatching { MusicLibrary.query(this@LockScreenActivity) }.getOrDefault(emptyList())
            }
        }

        setContent {
            MaterialTheme(colorScheme = darkColorScheme(
                background = Color.Black,
                surface = Surface,
                primary = Accent,
                onBackground = TextPrimary,
                onSurface = TextPrimary
            )) {
                LockScreenContent(
                    controller = controller,
                    tracks = tracks,
                    onSlideUnlock = { finish() }
                )
            }
        }
    }

    override fun onDestroy() {
        controller?.release()
        controller = null
        connectionFuture = null
        super.onDestroy()
    }
}

@Composable
private fun LockScreenContent(
    controller: MediaController?,
    tracks: List<Track>,
    onSlideUnlock: () -> Unit
) {
    var currentTrackId by remember { mutableStateOf<String?>(controller?.currentMediaItem?.mediaId) }
    var title by remember { mutableStateOf("Nothing playing") }
    var artist by remember { mutableStateOf("Open Krypton Player to start listening") }
    var playing by remember { mutableStateOf(false) }
    var shuffleEnabled by remember { mutableStateOf(false) }
    var queue by remember { mutableStateOf(emptyList<QueueTrack>()) }
    var queueOpen by remember { mutableStateOf(false) }
    var clockNow by remember { mutableLongStateOf(System.currentTimeMillis()) }

    fun refresh() {
        val player = controller ?: return
        currentTrackId = player.currentMediaItem?.mediaId
        title = player.mediaMetadata.title?.toString()
            ?: player.currentMediaItem?.mediaMetadata?.title?.toString()
            ?: "Nothing playing"
        artist = player.mediaMetadata.artist?.toString()
            ?: player.currentMediaItem?.mediaMetadata?.artist?.toString()
            ?: "Krypton Player"
        playing = player.isPlaying
        shuffleEnabled = player.shuffleModeEnabled
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
    }

    DisposableEffect(controller, tracks) {
        val listener = object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) = refresh()
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) = refresh()
            override fun onIsPlayingChanged(isPlaying: Boolean) = refresh()
        }
        controller?.addListener(listener)
        refresh()
        onDispose { controller?.removeListener(listener) }
    }

    LaunchedEffect(controller) {
        while (true) {
            refresh()
            clockNow = System.currentTimeMillis()
            delay(1_000L)
        }
    }

    val activeTrack = tracks.firstOrNull { it.id.toString() == currentTrackId }
    val accent by rememberArtworkAccent(activeTrack)
    val localTime = remember(clockNow) { SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(clockNow)) }
    val localDate = remember(clockNow) { DateFormat.getDateInstance(DateFormat.MEDIUM, Locale.getDefault()).format(Date(clockNow)) }

    BoxWithConstraints(
        modifier = Modifier.fillMaxSize().background(Color.Black)
    ) {
        val artworkSize = minOf(maxWidth * 0.65f, maxHeight * 0.30f).coerceIn(168.dp, 270.dp)
        Column(
            modifier = Modifier.fillMaxSize()
                .background(Color.Black)
                .padding(start = 25.dp, end = 25.dp, top = 12.dp, bottom = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().height(48.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                LockScreenTopButton(
                    contentDescription = "Open playback queue",
                    accent = accent,
                    onClick = { queueOpen = true }
                ) {
                    QueuePlayerGlyph(tint = accent, modifier = Modifier.size(25.dp))
                }
                LockScreenTopButton(
                    contentDescription = if (shuffleEnabled) "Turn shuffle off" else "Turn shuffle on",
                    accent = accent,
                    selected = shuffleEnabled,
                    onClick = {
                        controller?.let { player ->
                            player.shuffleModeEnabled = !player.shuffleModeEnabled
                            refresh()
                        }
                    }
                ) {
                    Icon(Icons.Default.Shuffle, contentDescription = null, tint = accent, modifier = Modifier.size(24.dp))
                }
            }

            Spacer(Modifier.height(7.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                Text(localTime.substringBefore(':'), color = TextPrimary, fontSize = 43.sp, fontWeight = FontWeight.Light, letterSpacing = (-1.5).sp)
                Text(":", color = accent, fontSize = 40.sp, fontWeight = FontWeight.Light, modifier = Modifier.padding(horizontal = 1.dp))
                Text(localTime.substringAfter(':'), color = TextPrimary, fontSize = 43.sp, fontWeight = FontWeight.Light, letterSpacing = (-1.5).sp)
            }
            Text(localDate, color = TextMuted, fontSize = 13.sp, letterSpacing = 0.5.sp)

            Spacer(Modifier.height(20.dp))
            Box(
                modifier = Modifier.size(artworkSize).border(
                    width = 1.6.dp,
                    brush = Brush.linearGradient(listOf(accent, accent.copy(alpha = 0.42f), accent)),
                    shape = RoundedCornerShape(29.dp)
                ).padding(3.dp),
                contentAlignment = Alignment.Center
            ) {
                TrackArtwork(track = activeTrack, modifier = Modifier.fillMaxSize(), cornerRadius = 26.dp)
            }

            Spacer(Modifier.height(15.dp))
            Text(
                title,
                color = TextPrimary,
                fontSize = 21.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(3.dp))
            Text(
                artist,
                color = TextMuted,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(27.dp))
            Row(
                modifier = Modifier.fillMaxWidth().height(74.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Box(Modifier.size(62.dp).clickable(enabled = controller != null) { controller?.seekToPreviousMediaItem() }, contentAlignment = Alignment.Center) {
                        WingSkipGlyph(next = false, tint = accent, modifier = Modifier.size(49.dp))
                    }
                }
                Box(
                    modifier = Modifier.width(104.dp).height(66.dp)
                        .clip(RoundedCornerShape(34.dp))
                        .background(Brush.linearGradient(listOf(accent.copy(alpha = 0.17f), Color(0xFF080A0E), accent.copy(alpha = 0.12f))))
                        .border(1.5.dp, Brush.linearGradient(listOf(accent, accent.copy(alpha = 0.40f), accent)), RoundedCornerShape(34.dp))
                        .clickable(enabled = controller != null) {
                            controller?.let { player -> if (player.isPlaying) player.pause() else player.play(); refresh() }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(Modifier.size(27.dp)) {
                        if (playing) {
                            drawRoundRect(accent, topLeft = androidx.compose.ui.geometry.Offset(size.width * 0.17f, 0f), size = androidx.compose.ui.geometry.Size(size.width * 0.22f, size.height), cornerRadius = androidx.compose.ui.geometry.CornerRadius(5.dp.toPx()))
                            drawRoundRect(accent, topLeft = androidx.compose.ui.geometry.Offset(size.width * 0.61f, 0f), size = androidx.compose.ui.geometry.Size(size.width * 0.22f, size.height), cornerRadius = androidx.compose.ui.geometry.CornerRadius(5.dp.toPx()))
                        } else {
                            val play = androidx.compose.ui.graphics.Path().apply {
                                moveTo(size.width * 0.18f, 0f)
                                lineTo(size.width * 0.88f, size.height * 0.5f)
                                lineTo(size.width * 0.18f, size.height)
                                close()
                            }
                            drawPath(play, accent)
                        }
                    }
                }
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Box(Modifier.size(62.dp).clickable(enabled = controller != null) { controller?.seekToNextMediaItem() }, contentAlignment = Alignment.Center) {
                        WingSkipGlyph(next = true, tint = accent, modifier = Modifier.size(49.dp))
                    }
                }
            }

            Spacer(Modifier.weight(1f))
            SlideToUnlock(accent = accent, onUnlock = onSlideUnlock)
        }

        if (queueOpen) {
            LockScreenQueueDialog(
                queue = queue,
                accent = accent,
                onDismiss = { queueOpen = false },
                onSelect = { index ->
                    controller?.seekTo(index, 0L)
                    controller?.play()
                    queueOpen = false
                    refresh()
                }
            )
        }
    }
}

@Composable
private fun LockScreenTopButton(
    contentDescription: String,
    accent: Color,
    selected: Boolean = false,
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier.size(width = 52.dp, height = 46.dp)
            .semantics { this.contentDescription = contentDescription }
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) accent.copy(alpha = 0.13f) else Color(0xFF050608))
            .border(1.dp, accent.copy(alpha = if (selected) 0.95f else 0.72f), RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

@Composable
private fun SlideToUnlock(accent: Color, onUnlock: () -> Unit) {
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val density = LocalDensity.current
    BoxWithConstraints(
        modifier = Modifier.fillMaxWidth().height(66.dp)
            .clip(RoundedCornerShape(34.dp))
            .background(Color(0xFF050608))
            .border(1.2.dp, Brush.linearGradient(listOf(accent.copy(alpha = 0.7f), Color(0xFF28303A), accent)), RoundedCornerShape(34.dp))
    ) {
        val handleSize = 56.dp
        val maxDrag = with(density) { (maxWidth - handleSize - 8.dp).toPx().coerceAtLeast(1f) }
        Box(
            modifier = Modifier.fillMaxSize().pointerInput(maxDrag) {
                detectHorizontalDragGestures(
                    onHorizontalDrag = { change, amount ->
                        change.consume()
                        dragOffset = (dragOffset + amount).coerceIn(-maxDrag, 0f)
                    },
                    onDragEnd = {
                        if (dragOffset <= -maxDrag * 0.68f) onUnlock() else dragOffset = 0f
                    },
                    onDragCancel = { dragOffset = 0f }
                )
            }
        ) {
            Row(
                modifier = Modifier.align(Alignment.CenterStart).padding(start = 20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("››", color = accent, fontSize = 26.sp, fontWeight = FontWeight.Light, letterSpacing = (-5).sp)
            }
            Text(
                "Slide to unlock",
                color = TextMuted,
                fontSize = 14.sp,
                letterSpacing = 1.4.sp,
                modifier = Modifier.align(Alignment.Center)
            )
            Box(
                modifier = Modifier.align(Alignment.CenterEnd)
                    .offset(x = with(density) { dragOffset.toDp() })
                    .size(handleSize)
                    .clip(CircleShape)
                    .background(Color(0xFF07090D))
                    .border(1.6.dp, Brush.linearGradient(listOf(accent, accent.copy(alpha = 0.35f), accent)), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = "Drag left to close Krypton lockscreen", tint = accent, modifier = Modifier.size(25.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LockScreenQueueDialog(
    queue: List<QueueTrack>,
    accent: Color,
    onDismiss: () -> Unit,
    onSelect: (Int) -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF080A0E),
        contentColor = TextPrimary,
        scrimColor = Color.Black.copy(alpha = 0.82f),
        dragHandle = {
            Box(Modifier.padding(top = 10.dp, bottom = 4.dp).width(38.dp).height(4.dp).clip(CircleShape).background(accent.copy(alpha = 0.7f)))
        }
    ) {
        Column(Modifier.fillMaxWidth().heightIn(max = 540.dp).padding(horizontal = 18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("PLAYBACK QUEUE", color = accent, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.6.sp)
                    Text("${queue.size} tracks", color = TextMuted, fontSize = 12.sp)
                }
                IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, contentDescription = "Close queue", tint = TextMuted) }
            }
            Spacer(Modifier.height(10.dp))
            if (queue.isEmpty()) {
                Box(Modifier.fillMaxWidth().height(150.dp), contentAlignment = Alignment.Center) {
                    Text("Nothing in the queue", color = TextMuted, fontSize = 13.sp)
                }
            } else {
                LazyColumn(contentPadding = PaddingValues(bottom = 26.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    items(queue, key = { it.index }) { item ->
                        Row(
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(13.dp))
                                .background(if (item.isCurrent) accent.copy(alpha = 0.13f) else Color(0xFF10151C))
                                .clickable { onSelect(item.index) }
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TrackArtwork(item.track, Modifier.size(42.dp), cornerRadius = 9.dp)
                            Spacer(Modifier.width(11.dp))
                            Column(Modifier.weight(1f)) {
                                Text(item.title, color = if (item.isCurrent) accent else TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(item.artist, color = TextMuted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            if (item.isCurrent) Text("PLAYING", color = accent, fontSize = 8.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
                        }
                    }
                }
            }
        }
    }
}
