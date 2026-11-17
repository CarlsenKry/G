package com.krypton.player

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.zIndex
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Velocity
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
    onCycleRepeat: () -> Unit,
    backdropColor: Color? = null,
    onDismiss: () -> Unit = {}
) {
    // Minimal Now Playing: plain background, large cover, left-aligned text, a thin progress line,
    // three transport controls and one quiet row of secondary actions.
    // Backdrop: a readable mid-tone taken from the cover (null = plain theme background).
    val backdrop = remember(backdropColor) { backdropColor?.let { playerBackdrop(it) } }
    val topColor by animateColorAsState(backdrop?.first ?: Background, tween(600), label = "playerBackdropTop")
    val bottomColor by animateColorAsState(backdrop?.second ?: Background, tween(600), label = "playerBackdropBottom")
    val subtle = if (backdrop != null) TextPrimary.copy(alpha = 0.74f) else TextMuted
    val trackColor = if (backdrop != null) TextPrimary.copy(alpha = 0.25f) else DividerColor

    // Let the system bars wear the same colors while this screen is open, then restore them.
    val window = (LocalView.current.context as? android.app.Activity)?.window
    DisposableEffect(window) {
        val previousStatus = window?.statusBarColor
        val previousNavigation = window?.navigationBarColor
        onDispose {
            if (window != null) {
                previousStatus?.let { window.statusBarColor = it }
                previousNavigation?.let { window.navigationBarColor = it }
            }
        }
    }
    if (backdrop != null && window != null) SideEffect {
        window.statusBarColor = topColor.toArgb()
        window.navigationBarColor = bottomColor.toArgb()
    }

    // Swipe down anywhere on the screen to slide it away and return to the previous screen.
    val density = LocalDensity.current
    val latestDismiss by rememberUpdatedState(onDismiss)
    val heightHolder = remember { floatArrayOf(1f) }
    var dragY by remember { mutableFloatStateOf(0f) }
    val dismissConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput && available.y < 0f && dragY > 0f) {
                    val consumed = maxOf(available.y, -dragY)
                    dragY += consumed
                    return Offset(0f, consumed)
                }
                return Offset.Zero
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput && available.y > 0f) {
                    dragY += available.y
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (dragY <= 0f) return Velocity.Zero
                val height = heightHolder[0]
                val dismiss = dragY > height * 0.22f || available.y > 1600f
                animate(
                    initialValue = dragY,
                    targetValue = if (dismiss) height else 0f,
                    animationSpec = tween(if (dismiss) 240 else 200, easing = FastOutSlowInEasing)
                ) { value, _ -> dragY = value }
                if (dismiss) latestDismiss()
                return available
            }
        }
    }

    BoxWithConstraints(modifier.fillMaxSize().nestedScroll(dismissConnection)) {
        heightHolder[0] = with(density) { maxHeight.toPx() }.coerceAtLeast(1f)
        val artworkSize = minOf(maxWidth - 44.dp, maxHeight * 0.46f).coerceIn(200.dp, 380.dp)
        var lyricsDrag by remember { mutableFloatStateOf(0f) }
        Column(
            modifier = Modifier.fillMaxSize()
                .graphicsLayer {
                    translationY = dragY.coerceAtLeast(0f)
                    alpha = 1f - 0.45f * (dragY / heightHolder[0]).coerceIn(0f, 1f)
                }
                .background(Brush.verticalGradient(listOf(topColor, bottomColor)))
                .padding(horizontal = 22.dp)
                .verticalScroll(androidx.compose.foundation.rememberScrollState()),
            horizontalAlignment = Alignment.Start
        ) {
            Spacer(Modifier.height(20.dp))
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                // Swipe the cover to the left to open the lyrics.
                TrackArtwork(
                    track = track,
                    modifier = Modifier.size(artworkSize).pointerInput(onOpenLyrics) {
                        detectHorizontalDragGestures(
                            onHorizontalDrag = { change, amount ->
                                lyricsDrag += amount
                                change.consume()
                            },
                            onDragEnd = {
                                if (lyricsDrag < -60f) onOpenLyrics()
                                lyricsDrag = 0f
                            },
                            onDragCancel = { lyricsDrag = 0f }
                        )
                    },
                    cornerRadius = 22.dp,
                    highRes = true
                )
            }

            Spacer(Modifier.height(28.dp))
            Text(
                title,
                color = TextPrimary,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(4.dp))
            Text(
                artist,
                color = subtle,
                fontSize = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(22.dp))
            val safeDuration = durationMs.coerceAtLeast(0L)
            val progress = if (safeDuration > 0L) (positionMs.toFloat() / safeDuration).coerceIn(0f, 1f) else 0f
            // While the bar is held it follows the finger and the audio is seeked once on release,
            // so playback position updates cannot fight the drag.
            var dragFraction by remember { mutableStateOf<Float?>(null) }
            val shownPosition = dragFraction?.let { (safeDuration * it).toLong() } ?: positionMs
            MinimalProgress(
                fraction = dragFraction ?: progress,
                enabled = safeDuration > 0L,
                accent = accent,
                trackColor = trackColor,
                onDrag = { dragFraction = it },
                onRelease = { fraction ->
                    if (safeDuration > 0L) onSeek((safeDuration * fraction).toLong())
                    dragFraction = null
                }
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(formatPlayerTime(shownPosition), color = subtle, fontSize = 12.sp)
                Text(formatPlayerTime(durationMs), color = subtle, fontSize = 12.sp)
            }

            Spacer(Modifier.height(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                IconButton(onClick = onPrevious, modifier = Modifier.size(56.dp)) {
                    WingSkipGlyph(next = false, tint = TextPrimary, modifier = Modifier.size(38.dp))
                }
                Spacer(Modifier.width(22.dp))
                Box(
                    modifier = Modifier.size(68.dp).clip(CircleShape).background(accent).clickable(onClick = onPlayPause),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (playing) "Pause" else "Play",
                        tint = Background,
                        modifier = Modifier.size(36.dp)
                    )
                }
                Spacer(Modifier.width(22.dp))
                IconButton(onClick = onNext, modifier = Modifier.size(56.dp)) {
                    WingSkipGlyph(next = true, tint = TextPrimary, modifier = Modifier.size(38.dp))
                }
            }

            Spacer(Modifier.height(30.dp))
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(onClick = onToggleShuffle, modifier = Modifier.size(44.dp)) {
                    Icon(Icons.Default.Shuffle, "Shuffle", tint = if (shuffleEnabled) accent else subtle, modifier = Modifier.size(22.dp))
                }
                IconButton(onClick = onCycleRepeat, modifier = Modifier.size(44.dp)) {
                    Icon(
                        if (repeatMode == androidx.media3.common.Player.REPEAT_MODE_ONE) Icons.Default.RepeatOne else Icons.Default.Repeat,
                        "Repeat mode",
                        tint = if (repeatMode != androidx.media3.common.Player.REPEAT_MODE_OFF) accent else subtle,
                        modifier = Modifier.size(22.dp)
                    )
                }
                IconButton(onClick = onOpenLyrics, modifier = Modifier.size(44.dp)) {
                    Icon(Icons.Default.Lyrics, "Lyrics", tint = subtle, modifier = Modifier.size(22.dp))
                }
                IconButton(onClick = onOpenEqualizer, modifier = Modifier.size(44.dp)) {
                    Icon(Icons.Default.Equalizer, "Open equalizer", tint = subtle, modifier = Modifier.size(22.dp))
                }
                IconButton(onClick = onOpenQueue, modifier = Modifier.size(44.dp)) {
                    QueuePlayerGlyph(tint = subtle, modifier = Modifier.size(24.dp))
                }
            }
        }
    }
}

/**
 * Turns a cover accent into a readable gradient pair: a clearly colored mid-tone on top (dark enough
 * for white text, never near-black) fading to a deeper shade of the same hue. Grey covers get a
 * neutral slate instead of a random tint.
 */
internal fun playerBackdrop(accent: Color): Pair<Color, Color> {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(accent.toArgb(), hsv)
    val neutral = hsv[1] < 0.14f
    val hue = hsv[0]
    val saturation = if (neutral) 0.06f else hsv[1].coerceIn(0.50f, 0.82f)
    fun shade(value: Float) = Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, saturation, value)))
    var value = if (neutral) 0.38f else 0.68f
    var top = shade(value)
    while (top.luminance() > 0.20f && value > 0.30f) {
        value -= 0.03f
        top = shade(value)
    }
    return top to shade((value * 0.52f).coerceAtLeast(0.15f))
}

/** Thin progress line with a generous touch area. Tap or drag to seek; the seek happens on release. */
@Composable
private fun MinimalProgress(
    fraction: Float,
    enabled: Boolean,
    accent: Color,
    trackColor: Color,
    onDrag: (Float) -> Unit,
    onRelease: (Float) -> Unit
) {
    var widthPx by remember { mutableFloatStateOf(1f) }
    var latest by remember { mutableFloatStateOf(0f) }
    Box(
        modifier = Modifier.fillMaxWidth().height(28.dp)
            .onSizeChanged { widthPx = it.width.toFloat().coerceAtLeast(1f) }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectTapGestures { offset -> onRelease((offset.x / widthPx).coerceIn(0f, 1f)) }
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        latest = (offset.x / widthPx).coerceIn(0f, 1f)
                        onDrag(latest)
                    },
                    onHorizontalDrag = { change, _ ->
                        change.consume()
                        latest = (change.position.x / widthPx).coerceIn(0f, 1f)
                        onDrag(latest)
                    },
                    onDragEnd = { onRelease(latest) },
                    onDragCancel = { onRelease(latest) }
                )
            },
        contentAlignment = Alignment.CenterStart
    ) {
        Box(Modifier.fillMaxWidth().height(3.dp).clip(CircleShape).background(trackColor))
        Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).height(3.dp).clip(CircleShape).background(if (enabled) accent else TextMuted))
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

/** The combined Queue + Player mark used in the player action row and lockscreen. */
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun QueueSheet(
    queue: List<QueueTrack>,
    accent: Color,
    onDismiss: () -> Unit,
    onSelect: (Int) -> Unit,
    onMove: (Int, Int) -> Unit = { _, _ -> }
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
                ReorderableQueueList(queue = queue, accent = accent, onSelect = onSelect, onMove = onMove)
            }
        }
    }
}

/**
 * The queue list with drag-to-reorder. The three-dot handle at the end of each row starts a drag;
 * rows glide out of the way (animateItem), the lifted row follows the finger and the list
 * auto-scrolls near its edges. The order is changed locally at once and sent to the player once on drop.
 */
@Composable
private fun ReorderableQueueList(
    queue: List<QueueTrack>,
    accent: Color,
    onSelect: (Int) -> Unit,
    onMove: (Int, Int) -> Unit
) {
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val latestQueue by rememberUpdatedState(queue)

    var items by remember { mutableStateOf(queue) }
    var draggedKey by remember { mutableStateOf<String?>(null) }
    var settlingKey by remember { mutableStateOf<String?>(null) }
    var awaitingSync by remember { mutableStateOf(false) }
    var dragStartIndex by remember { mutableIntStateOf(-1) }
    var initialOffset by remember { mutableIntStateOf(0) }
    var draggedDistance by remember { mutableFloatStateOf(0f) }
    val settle = remember { Animatable(0f) }

    // Follow the player's queue, except while a drag (or the hand-over of its result) is in progress.
    LaunchedEffect(queue) {
        if (draggedKey == null) {
            if (!awaitingSync) items = queue
            else if (queue.map { it.key } == items.map { it.key }) {
                awaitingSync = false
                items = queue
            }
        }
    }
    LaunchedEffect(awaitingSync) {
        if (awaitingSync) {
            delay(1500)
            awaitingSync = false
            if (draggedKey == null) items = latestQueue
        }
    }

    fun draggedInfo() = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == draggedKey }

    fun reorderIfNeeded() {
        val key = draggedKey ?: return
        val info = draggedInfo() ?: return
        val from = items.indexOfFirst { it.key == key }
        if (from < 0 || info.index != from) return // layout has not caught up with the last swap yet
        val center = initialOffset + draggedDistance + info.size / 2f
        val target = listState.layoutInfo.visibleItemsInfo.firstOrNull {
            it.key != key && center >= it.offset && center <= it.offset + it.size
        } ?: return
        val to = items.indexOfFirst { it.key == target.key }
        if (to < 0 || to == from) return
        items = items.toMutableList().apply { add(to, removeAt(from)) }
    }

    fun finishDrag() {
        val key = draggedKey ?: return
        val info = draggedInfo()
        val translation = if (info != null) (initialOffset + draggedDistance) - info.offset else 0f
        val from = dragStartIndex
        val to = items.indexOfFirst { it.key == key }
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            settle.snapTo(translation)
            settlingKey = key
            draggedKey = null
            if (from >= 0 && to >= 0 && from != to) {
                awaitingSync = true
                onMove(from, to)
            }
            settle.animateTo(0f, tween(220, easing = FastOutSlowInEasing))
            if (settlingKey == key) settlingKey = null
        }
    }

    // Scroll the list while the lifted row is held near the top or bottom edge.
    LaunchedEffect(draggedKey) {
        if (draggedKey == null) return@LaunchedEffect
        while (isActive && draggedKey != null) {
            val info = draggedInfo()
            if (info != null) {
                val layout = listState.layoutInfo
                val top = initialOffset + draggedDistance
                val bottom = top + info.size
                val zone = 90f
                val delta = when {
                    top < layout.viewportStartOffset + zone ->
                        -((layout.viewportStartOffset + zone - top) / zone * 26f).coerceAtMost(26f)
                    bottom > layout.viewportEndOffset - zone ->
                        ((bottom - (layout.viewportEndOffset - zone)) / zone * 26f).coerceAtMost(26f)
                    else -> 0f
                }
                if (delta != 0f) {
                    listState.scrollBy(delta)
                    reorderIfNeeded()
                }
            }
            delay(16)
        }
    }

    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        itemsIndexed(items, key = { _, item -> item.key }) { position, item ->
            val dragged = item.key == draggedKey
            val settling = item.key == settlingKey
            val lift by animateFloatAsState(if (dragged) 1f else 0f, tween(160), label = "queueRowLift")
            val shape = RoundedCornerShape(15.dp)
            Row(
                modifier = Modifier.fillMaxWidth()
                    .then(if (dragged || settling) Modifier else Modifier.animateItem())
                    .zIndex(if (dragged || settling) 1f else 0f)
                    .graphicsLayer {
                        translationY = when {
                            dragged -> {
                                val offset = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == item.key }?.offset
                                if (offset != null) (initialOffset + draggedDistance) - offset else 0f
                            }
                            settling -> settle.value
                            else -> 0f
                        }
                        val scale = 1f + 0.03f * lift
                        scaleX = scale
                        scaleY = scale
                    }
                    .shadow(10.dp * lift, shape)
                    .clip(shape)
                    .background(if (item.isCurrent) accent.copy(alpha = 0.14f) else blendColor(SurfaceRaised, accent, 0.12f * lift))
                    .clickable { onSelect(position) }
                    .padding(start = 11.dp, top = 11.dp, bottom = 11.dp, end = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (item.isCurrent) Icon(Icons.Default.GraphicEq, null, tint = accent, modifier = Modifier.size(20.dp))
                else Text((position + 1).toString().padStart(2, '0'), color = TextMuted, fontSize = 10.sp, modifier = Modifier.width(22.dp))
                Spacer(Modifier.width(8.dp))
                TrackArtwork(item.track, Modifier.size(46.dp), 11.dp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(item.title, color = if (item.isCurrent) accent else TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(item.artist, color = TextMuted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (item.isCurrent) Text("PLAYING", color = accent, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                QueueDragHandle(
                    active = dragged,
                    accent = accent,
                    modifier = Modifier.pointerInput(Unit) {
                        // A plain tap on the handle must not select the row behind it.
                        detectTapGestures { }
                    }.pointerInput(item.key) {
                        detectVerticalDragGestures(
                            onDragStart = {
                                val info = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == item.key }
                                if (info != null) {
                                    settlingKey = null
                                    dragStartIndex = items.indexOfFirst { it.key == item.key }
                                    initialOffset = info.offset
                                    draggedDistance = 0f
                                    draggedKey = item.key
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                }
                            },
                            onVerticalDrag = { change, amount ->
                                change.consume()
                                if (draggedKey == item.key) {
                                    draggedDistance += amount
                                    reorderIfNeeded()
                                }
                            },
                            onDragEnd = { finishDrag() },
                            onDragCancel = { finishDrag() }
                        )
                    }
                )
            }
        }
    }
}

/** Three vertical dots: the visible grab handle for reordering a queue row. */
@Composable
private fun QueueDragHandle(active: Boolean, accent: Color, modifier: Modifier = Modifier) {
    val color = if (active) accent else TextSecondary
    Canvas(modifier.size(width = 44.dp, height = 48.dp)) {
        val radius = 2.5.dp.toPx()
        val spacing = 8.dp.toPx()
        val center = Offset(size.width / 2f, size.height / 2f)
        for (step in -1..1) drawCircle(color, radius, Offset(center.x, center.y + step * spacing))
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

private fun tintSurface(base: Color, tint: Color, amount: Float): Color = blendColor(base, tint, amount)

private fun formatPlayerTime(ms: Long): String {
    val totalSeconds = (ms.coerceAtLeast(0L) / 1000L).toInt()
    return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}
