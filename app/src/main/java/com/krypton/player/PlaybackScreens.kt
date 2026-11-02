package com.krypton.player

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun PlayerScreen(
    modifier: Modifier = Modifier,
    track: Track?,
    accent: Color,
    title: String,
    artist: String,
    playing: Boolean,
    positionMs: Long,
    durationMs: Long,
    shuffleEnabled: Boolean,
    repeatMode: Int,
    onOpenEqualizer: () -> Unit,
    onOpenQueue: () -> Unit,
    onOpenLyrics: () -> Unit,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSeek: (Long) -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit
) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val artworkSize = minOf(maxWidth * 0.74f, maxHeight * 0.40f).coerceIn(176.dp, 340.dp)
        Column(
            modifier = Modifier.fillMaxSize().background(
                androidx.compose.ui.graphics.Brush.verticalGradient(
                    listOf(accent.copy(alpha = 0.11f), Background, Background)
                )
            )
                .padding(horizontal = 18.dp)
                .verticalScroll(androidx.compose.foundation.rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().height(54.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(onClick = onOpenEqualizer) {
                    Icon(Icons.Default.Equalizer, contentDescription = "Open equalizer", tint = accent, modifier = Modifier.size(27.dp))
                }
                Text("NOW PLAYING", color = accent, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                IconButton(onClick = onOpenQueue) {
                    QueuePlayerGlyph(tint = accent, modifier = Modifier.size(29.dp))
                }
            }

            Spacer(Modifier.height(artworkSize * 0.035f))
            Box(
                modifier = Modifier.fillMaxWidth().height(artworkSize + 8.dp),
                contentAlignment = Alignment.Center
            ) {
                TrackArtwork(track = track, modifier = Modifier.size(artworkSize), cornerRadius = 25.dp)
                LyricsSwipeHandle(
                    modifier = Modifier.align(Alignment.CenterEnd),
                    onOpenLyrics = onOpenLyrics,
                    accent = accent
                )
            }

            Spacer(Modifier.height(15.dp))
            Text(
                title,
                color = TextPrimary,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(5.dp))
            Text(
                artist,
                color = TextMuted,
                fontSize = 15.sp,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(24.dp))
            val safeDuration = durationMs.coerceAtLeast(0L)
            val progress = if (safeDuration > 0L) (positionMs.toFloat() / safeDuration).coerceIn(0f, 1f) else 0f
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(formatPlayerTime(positionMs), color = accent, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                Text(formatPlayerTime(durationMs), color = TextMuted, fontSize = 12.sp)
            }
            Slider(
                value = progress,
                onValueChange = { fraction -> if (safeDuration > 0L) onSeek((safeDuration * fraction).toLong()) },
                enabled = safeDuration > 0L,
                modifier = Modifier.fillMaxWidth().height(30.dp),
                colors = SliderDefaults.colors(
                    thumbColor = accent,
                    activeTrackColor = accent,
                    inactiveTrackColor = DividerColor,
                    disabledThumbColor = TextMuted,
                    disabledActiveTrackColor = DividerColor,
                    disabledInactiveTrackColor = DividerColor
                )
            )

            Spacer(Modifier.height(20.dp))
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(onClick = onToggleShuffle, modifier = Modifier.size(42.dp)) {
                    Icon(Icons.Default.Shuffle, "Shuffle", tint = if (shuffleEnabled) accent else TextMuted, modifier = Modifier.size(23.dp))
                }
                IconButton(onClick = onPrevious, modifier = Modifier.size(54.dp)) {
                    WingSkipGlyph(next = false, tint = accent, modifier = Modifier.size(48.dp))
                }
                Box(
                    modifier = Modifier.size(width = 82.dp, height = 68.dp)
                        .clip(RoundedCornerShape(34.dp)).background(accent).clickable(onClick = onPlayPause),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(Modifier.fillMaxSize().padding(18.dp)) {
                        if (playing) {
                            drawRoundRect(Background, topLeft = androidx.compose.ui.geometry.Offset(size.width * 0.22f, 0f), size = androidx.compose.ui.geometry.Size(size.width * 0.19f, size.height), cornerRadius = androidx.compose.ui.geometry.CornerRadius(5.dp.toPx()))
                            drawRoundRect(Background, topLeft = androidx.compose.ui.geometry.Offset(size.width * 0.59f, 0f), size = androidx.compose.ui.geometry.Size(size.width * 0.19f, size.height), cornerRadius = androidx.compose.ui.geometry.CornerRadius(5.dp.toPx()))
                        } else {
                            val playPath = Path().apply {
                                moveTo(size.width * 0.22f, 0f)
                                quadraticTo(size.width * 0.13f, 0f, size.width * 0.13f, size.height * 0.15f)
                                lineTo(size.width * 0.13f, size.height * 0.85f)
                                quadraticTo(size.width * 0.13f, size.height, size.width * 0.22f, size.height)
                                lineTo(size.width * 0.88f, size.height * 0.57f)
                                quadraticTo(size.width, size.height * 0.5f, size.width * 0.88f, size.height * 0.43f)
                                close()
                            }
                            drawPath(playPath, Background)
                        }
                    }
                }
                IconButton(onClick = onNext, modifier = Modifier.size(54.dp)) {
                    WingSkipGlyph(next = true, tint = accent, modifier = Modifier.size(48.dp))
                }
                IconButton(onClick = onCycleRepeat, modifier = Modifier.size(42.dp)) {
                    Icon(Icons.Default.Repeat, "Repeat mode", tint = if (repeatMode != androidx.media3.common.Player.REPEAT_MODE_OFF) accent else TextMuted, modifier = Modifier.size(23.dp))
                }
            }
            Text(
                when (repeatMode) {
                    androidx.media3.common.Player.REPEAT_MODE_ONE -> "REPEAT ONE"
                    androidx.media3.common.Player.REPEAT_MODE_ALL -> "REPEAT ALL"
                    else -> "SHUFFLE ${if (shuffleEnabled) "ON" else "OFF"}"
                },
                color = TextMuted,
                fontSize = 9.sp,
                letterSpacing = 1.3.sp,
                modifier = Modifier.padding(bottom = 18.dp)
            )
        }
    }
}

/** Futuristic wing-shaped transport glyph. The arrow and speed fins mirror for previous/next. */
@Composable
internal fun WingSkipGlyph(next: Boolean, tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        fun px(value: Float) = if (next) size.width * value else size.width * (1f - value)
        fun path(points: List<Pair<Float, Float>>): Path = Path().apply {
            moveTo(px(points.first().first), size.height * points.first().second)
            points.drop(1).forEach { (x, y) -> lineTo(px(x), size.height * y) }
            close()
        }
        // Main forward blade with rounded visual proportions.
        val blade = Path().apply {
            moveTo(px(0.16f), size.height * 0.18f)
            quadraticTo(px(0.10f), size.height * 0.18f, px(0.10f), size.height * 0.29f)
            lineTo(px(0.10f), size.height * 0.71f)
            quadraticTo(px(0.10f), size.height * 0.82f, px(0.16f), size.height * 0.82f)
            lineTo(px(0.59f), size.height * 0.56f)
            quadraticTo(px(0.70f), size.height * 0.50f, px(0.59f), size.height * 0.44f)
            close()
        }
        drawPath(blade, tint)
        drawPath(path(listOf(0.43f to 0.25f, 0.78f to 0.08f, 0.94f to 0.08f, 0.75f to 0.34f, 0.53f to 0.42f)), tint)
        drawPath(path(listOf(0.48f to 0.43f, 0.83f to 0.39f, 0.96f to 0.50f, 0.83f to 0.61f, 0.48f to 0.57f)), tint)
        drawPath(path(listOf(0.43f to 0.75f, 0.75f to 0.66f, 0.94f to 0.92f, 0.78f to 0.92f)), tint)
    }
}

/** The combined Queue + Player mark used in the bottom navigation and player toolbar. */
@Composable
internal fun QueuePlayerGlyph(tint: Color, modifier: Modifier = Modifier) {
    Box(modifier) {
        Icon(Icons.Default.QueueMusic, contentDescription = null, tint = tint, modifier = Modifier.fillMaxSize())
        Box(
            modifier = Modifier.align(Alignment.BottomEnd).size(13.dp).clip(CircleShape)
                .background(Surface).border(1.dp, Background, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.PlayArrow, contentDescription = null, tint = tint, modifier = Modifier.size(10.dp))
        }
    }
}

@Composable
private fun LyricsSwipeHandle(modifier: Modifier = Modifier, onOpenLyrics: () -> Unit, accent: Color) {
    var dragDistance by remember { mutableFloatStateOf(0f) }
    Column(
        modifier = modifier
            .width(39.dp)
            .height(100.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(SurfaceRaised.copy(alpha = 0.94f))
            .border(1.dp, accent.copy(alpha = 0.55f), RoundedCornerShape(20.dp))
            .clickable(onClick = onOpenLyrics)
            .pointerInput(onOpenLyrics) {
                detectHorizontalDragGestures(
                    onHorizontalDrag = { change, amount ->
                        dragDistance += amount
                        change.consume()
                    },
                    onDragEnd = {
                        if (dragDistance < -35f) onOpenLyrics()
                        dragDistance = 0f
                    },
                    onDragCancel = { dragDistance = 0f }
                )
            }
            .padding(vertical = 9.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Icon(Icons.Default.Lyrics, contentDescription = "Lyrics: swipe left or tap", tint = accent, modifier = Modifier.size(20.dp))
        Icon(Icons.Default.ChevronLeft, contentDescription = null, tint = TextPrimary, modifier = Modifier.size(21.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun QueueSheet(
    queue: List<QueueTrack>,
    accent: Color,
    onDismiss: () -> Unit,
    onSelect: (Int) -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = tintSurface(Surface, accent, 0.08f),
        contentColor = TextPrimary,
        dragHandle = { BottomSheetDefaults.DragHandle(color = TextMuted) }
    ) {
        Column(Modifier.fillMaxWidth().heightIn(max = 620.dp).padding(horizontal = 18.dp)) {
            Row(Modifier.fillMaxWidth().padding(bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("UP NEXT", color = TextMuted, fontSize = 10.sp, letterSpacing = 2.sp, fontWeight = FontWeight.Bold)
                    Text("${queue.size} queued tracks", color = TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                }
                Icon(Icons.Default.QueueMusic, contentDescription = null, tint = accent, modifier = Modifier.size(27.dp))
            }
            if (queue.isEmpty()) {
                Column(Modifier.fillMaxWidth().padding(vertical = 44.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.QueueMusic, contentDescription = null, tint = accent, modifier = Modifier.size(42.dp))
                    Spacer(Modifier.height(10.dp))
                    Text("Nothing in your queue", color = TextPrimary, fontWeight = FontWeight.Bold)
                    Text("Add tracks from your Library to play next.", color = TextMuted, fontSize = 12.sp)
                }
            } else {
                LazyColumn(contentPadding = PaddingValues(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(queue, key = { it.index }) { item ->
                        Row(
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(15.dp))
                                .background(if (item.isCurrent) accent.copy(alpha = 0.14f) else SurfaceRaised)
                                .clickable { onSelect(item.index) }.padding(11.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (item.isCurrent) Icon(Icons.Default.GraphicEq, null, tint = accent, modifier = Modifier.size(20.dp))
                            else Text((item.index + 1).toString().padStart(2, '0'), color = TextMuted, fontSize = 10.sp, modifier = Modifier.width(22.dp))
                            Spacer(Modifier.width(8.dp))
                            TrackArtwork(item.track, Modifier.size(46.dp), 11.dp)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(item.title, color = if (item.isCurrent) accent else TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(item.artist, color = TextMuted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            if (item.isCurrent) Text("PLAYING", color = accent, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun LyricsScreen(
    modifier: Modifier = Modifier,
    lines: List<LyricLine>,
    positionMs: Long,
    accent: Color,
    onPickLyrics: () -> Unit,
    onPickLyricsFolder: () -> Unit
) {
    val activeIndex = lines.indexOfLast { it.timeMs != null && it.timeMs <= positionMs }
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    LaunchedEffect(activeIndex, lines.size) {
        if (activeIndex >= 0 && lines.isNotEmpty()) listState.animateScrollToItem(activeIndex.coerceAtMost(lines.lastIndex))
    }
    if (lines.isEmpty()) {
        Column(
            modifier = modifier.fillMaxSize().padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(Icons.Default.Lyrics, null, tint = accent, modifier = Modifier.size(54.dp))
            Spacer(Modifier.height(16.dp))
            Text("No lyrics loaded", color = TextPrimary, fontSize = 19.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(7.dp))
            Text("Load an .lrc or .txt lyric file for the current track. Timed LRC lines will follow playback.", color = TextMuted, fontSize = 12.sp, lineHeight = 18.sp, textAlign = TextAlign.Center)
            Spacer(Modifier.height(18.dp))
            Button(onClick = onPickLyricsFolder, colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = Background)) {
                Text("CHOOSE MUSIC FOLDER", fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(8.dp))
            Text("Grant folder access once so Krypton can find matching .lrc files beside your songs. Embedded ID3, FLAC, Ogg/Opus and M4A lyric tags are checked automatically; while this screen is open, new or updated sidecar files are rechecked every five seconds.", color = TextMuted, fontSize = 11.sp, lineHeight = 16.sp, textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onPickLyrics) { Text("LOAD A LYRICS FILE MANUALLY", color = accent) }
        }
    } else {
        LazyColumn(
            state = listState,
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 32.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            item {
                Text("SYNCHRONIZED LYRICS", color = TextMuted, fontSize = 10.sp, letterSpacing = 2.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
            }
            items(lines.size) { index ->
                val line = lines[index]
                Text(
                    line.text,
                    modifier = Modifier.fillMaxWidth(),
                    color = if (index == activeIndex) accent else TextMuted,
                    fontSize = if (index == activeIndex) 22.sp else 17.sp,
                    fontWeight = if (index == activeIndex) FontWeight.Bold else FontWeight.Medium,
                    lineHeight = if (index == activeIndex) 29.sp else 24.sp,
                    textAlign = TextAlign.Center
                )
            }
            item {
                TextButton(onClick = onPickLyrics, modifier = Modifier.fillMaxWidth()) { Text("CHANGE LYRICS FILE", color = accent) }
                TextButton(onClick = onPickLyricsFolder, modifier = Modifier.fillMaxWidth()) { Text("SET / CHANGE LYRICS FOLDER", color = accent) }
            }
        }
    }
}

private fun tintSurface(base: Color, tint: Color, amount: Float): Color {
    val t = amount.coerceIn(0f, 1f)
    return Color(
        red = base.red * (1f - t) + tint.red * t,
        green = base.green * (1f - t) + tint.green * t,
        blue = base.blue * (1f - t) + tint.blue * t,
        alpha = base.alpha
    )
}

private fun formatPlayerTime(ms: Long): String {
    val totalSeconds = (ms.coerceAtLeast(0L) / 1000L).toInt()
    return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}
