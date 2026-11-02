package com.krypton.player

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Artwork is decoded off the UI thread and held in a small bounded process cache. */
@Composable
internal fun TrackArtwork(
    track: Track?,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 13.dp
) {
    val context = LocalContext.current
    val artwork = produceState<Bitmap?>(
        initialValue = track?.let { ArtworkCache.get(it.id) },
        key1 = track?.id,
        key2 = track?.uri
    ) {
        val item = track
        value = if (item == null) null else withContext(Dispatchers.IO) {
            getOrLoadArtwork(context.applicationContext, item.id, item.uri)
        }
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius))
            .background(Brush.linearGradient(listOf(ColorArtworkStart, ColorArtworkEnd))),
        contentAlignment = Alignment.Center
    ) {
        val bitmap = artwork.value
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = track?.let { "Album cover for ${it.title}" } ?: "Album cover",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Icon(
                imageVector = Icons.Default.MusicNote,
                contentDescription = null,
                tint = Accent,
                modifier = Modifier.fillMaxSize(0.48f)
            )
        }
    }
}

/** Accent color shared by the Player and its queue sheet, derived from the current cover. */
@Composable
internal fun rememberArtworkAccent(track: Track?): State<Color> {
    val context = LocalContext.current
    return produceState(initialValue = Accent, key1 = track?.id, key2 = track?.uri) {
        val item = track
        value = if (item == null) Accent else {
            val bitmap = withContext(Dispatchers.IO) {
                getOrLoadArtwork(context.applicationContext, item.id, item.uri)
            }
            bitmap?.let(::dominantArtworkAccent) ?: Accent
        }
    }
}

private val ColorArtworkStart = Color(0xFF252930)
private val ColorArtworkEnd = Color(0xFF111419)

private object ArtworkCache {
    private val bitmaps = object : LruCache<Long, Bitmap>(5 * 1024) {
        override fun sizeOf(key: Long, value: Bitmap): Int =
            (value.allocationByteCount / 1024).coerceAtLeast(1)
    }
    private val missing = LruCache<Long, Boolean>(256)

    @Synchronized fun get(id: Long): Bitmap? = bitmaps.get(id)
    @Synchronized fun isKnown(id: Long): Boolean = bitmaps.get(id) != null || missing.get(id) == true
    @Synchronized fun put(id: Long, bitmap: Bitmap?) {
        if (bitmap == null) missing.put(id, true) else bitmaps.put(id, bitmap)
    }
}

private fun getOrLoadArtwork(context: Context, id: Long, uri: Uri): Bitmap? {
    ArtworkCache.get(id)?.let { return it }
    if (ArtworkCache.isKnown(id)) return null
    val bitmap = readEmbeddedArtwork(context, uri)
    ArtworkCache.put(id, bitmap)
    return bitmap
}

/** Quantized color-frequency sampling ignores near-black/near-white pixels and boosts saturation. */
private fun dominantArtworkAccent(bitmap: Bitmap): Color {
    val width = bitmap.width.coerceAtLeast(1)
    val height = bitmap.height.coerceAtLeast(1)
    val step = maxOf(1, maxOf(width, height) / 72)
    val colors = HashMap<Int, Int>()
    val hsv = FloatArray(3)
    var bestKey = -1
    var bestScore = -1.0
    var bestHue = 174f

    var y = 0
    while (y < height) {
        var x = 0
        while (x < width) {
            val pixel = bitmap.getPixel(x, y)
            if (android.graphics.Color.alpha(pixel) >= 190) {
                android.graphics.Color.colorToHSV(pixel, hsv)
                val saturation = hsv[1]
                val brightness = hsv[2]
                if (saturation >= 0.22f && brightness in 0.16f..0.94f) {
                    val hueBin = (hsv[0] / 15f).toInt().coerceIn(0, 23)
                    val satBin = (saturation * 4).toInt().coerceIn(0, 3)
                    val valueBin = (brightness * 4).toInt().coerceIn(0, 3)
                    val key = hueBin * 16 + satBin * 4 + valueBin
                    val count = (colors[key] ?: 0) + 1
                    colors[key] = count
                    val score = count * (0.55 + saturation * 0.7) * (0.65 + brightness * 0.35)
                    if (score > bestScore) {
                        bestScore = score
                        bestKey = key
                        bestHue = hueBin * 15f + 7.5f
                    }
                }
            }
            x += step
        }
        y += step
    }
    if (bestKey < 0) return Accent
    val saturationBin = (bestKey % 16) / 4
    val valueBin = bestKey % 4
    val saturation = maxOf(0.62f, (saturationBin + 0.5f) / 4f).coerceAtMost(0.88f)
    val brightness = maxOf(0.88f, (valueBin + 0.5f) / 4f).coerceAtMost(0.96f)
    return Color(android.graphics.Color.HSVToColor(floatArrayOf(bestHue, saturation, brightness)))
}

private fun readEmbeddedArtwork(context: Context, uri: Uri): Bitmap? {
    val retriever = MediaMetadataRetriever()
    return try {
        retriever.setDataSource(context, uri)
        val bytes = retriever.embeddedPicture ?: return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val sampleSize = calculateSampleSize(bounds.outWidth, bounds.outHeight, 256, 256)
        val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
    } catch (_: Exception) {
        null
    } finally {
        try { retriever.release() } catch (_: RuntimeException) { }
    }
}

private fun calculateSampleSize(width: Int, height: Int, requestedWidth: Int, requestedHeight: Int): Int {
    var sample = 1
    if (height > requestedHeight || width > requestedWidth) {
        val halfHeight = height / 2
        val halfWidth = width / 2
        while (halfHeight / sample >= requestedHeight || halfWidth / sample >= requestedWidth) sample *= 2
    }
    return sample.coerceAtLeast(1)
}
