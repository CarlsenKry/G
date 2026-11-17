package com.krypton.player

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.collectLatest

@Composable
internal fun EqualizerScreen(modifier: Modifier = Modifier) {
    val state by KryptonAudioEffects.state.collectAsState()
    // Keep the status label explicitly non-null: Material3 Text accepts String,
    // not String?, and state.error is nullable in AudioEffectsUiState.
    val statusMessage: String = when {
        state.available -> "Connected to the active playback session"
        !state.error.isNullOrBlank() -> state.error.orEmpty()
        else -> "Start a track to connect the audio effects"
    }
    Column(
        modifier = modifier.fillMaxSize().background(Background)
            .padding(horizontal = 18.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Surface).padding(15.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(text = "EQUALIZER", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp, letterSpacing = 1.sp)
                Text(
                    text = statusMessage,
                    color = TextMuted,
                    fontSize = 10.sp,
                    lineHeight = 15.sp
                )
            }
            Switch(checked = state.enabled, onCheckedChange = KryptonAudioEffects::setEnabled, enabled = state.available)
        }

        if (!state.available) {
            Text(
                "The equalizer is owned by Krypton's background audio service. Once a track starts, the controls connect to that same audio session and remain active when you leave this screen.",
                color = TextMuted,
                fontSize = 11.sp,
                lineHeight = 17.sp,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }

        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Surface).padding(15.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("PRESETS", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 1.4.sp, modifier = Modifier.weight(1f))
                TextButton(onClick = KryptonAudioEffects::reset, enabled = state.available) { Text("RESET", color = Accent, fontSize = 10.sp) }
            }
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = state.selectedPreset == -1,
                    onClick = KryptonAudioEffects::reset,
                    label = { Text("Flat") },
                    enabled = state.available,
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Accent, selectedLabelColor = Background)
                )
                state.presets.forEachIndexed { index, name ->
                    FilterChip(
                        selected = state.selectedPreset == index,
                        onClick = { KryptonAudioEffects.applyPreset(index) },
                        label = { Text(name.replaceFirstChar { it.uppercase() }) },
                        enabled = state.available,
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Accent, selectedLabelColor = Background)
                    )
                }
            }
        }

        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Surface).padding(horizontal = 13.dp, vertical = 16.dp)) {
            Text("FREQUENCY BANDS", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 1.4.sp)
            Spacer(Modifier.height(12.dp))
            if (state.bands.isEmpty()) {
                Box(Modifier.fillMaxWidth().height(190.dp), contentAlignment = Alignment.Center) {
                    Text("Waiting for an active audio session…", color = TextMuted, fontSize = 11.sp)
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth().height(218.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    state.bands.forEachIndexed { index, band ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                            Text(if (band.levelDb > 0) "+%.1f".format(band.levelDb) else "%.1f".format(band.levelDb), color = Accent, fontSize = 9.sp, fontWeight = FontWeight.SemiBold)
                            VerticalBandSlider(
                                value = band.levelDb,
                                valueRange = state.minLevelDb..state.maxLevelDb,
                                enabled = state.available && state.enabled,
                                onValueChange = { KryptonAudioEffects.setBandLevel(index, it) },
                                modifier = Modifier.width(42.dp).height(160.dp)
                            )
                            Spacer(Modifier.height(5.dp))
                            Text(band.label, color = TextMuted, fontSize = 9.sp)
                        }
                    }
                }
            }
            Text("Drag a band up or down to tune the sound.", color = TextMuted, fontSize = 10.sp, modifier = Modifier.padding(top = 4.dp))
        }

        EffectStrengthCard(
            title = "BASS BOOST",
            subtitle = "Add weight to low frequencies",
            supported = state.available && state.bassBoostSupported,
            enabled = state.bassBoostEnabled,
            strength = state.bassBoostStrength,
            onEnabled = KryptonAudioEffects::setBassBoostEnabled,
            onStrength = KryptonAudioEffects::setBassBoostStrength
        )
        EffectStrengthCard(
            title = "VIRTUALIZER",
            subtitle = "Widen the stereo listening space",
            supported = state.available && state.virtualizerSupported,
            enabled = state.virtualizerEnabled,
            strength = state.virtualizerStrength,
            onEnabled = KryptonAudioEffects::setVirtualizerEnabled,
            onStrength = KryptonAudioEffects::setVirtualizerStrength
        )
        Spacer(Modifier.height(18.dp))
    }
}

@Composable
private fun EffectStrengthCard(
    title: String,
    subtitle: String,
    supported: Boolean,
    enabled: Boolean,
    strength: Float,
    onEnabled: (Boolean) -> Unit,
    onStrength: (Float) -> Unit
) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Surface).padding(15.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 1.3.sp)
                Text(if (supported) subtitle else "Not supported by the current device audio effect", color = TextMuted, fontSize = 10.sp)
            }
            Switch(checked = enabled, onCheckedChange = onEnabled, enabled = supported)
        }
        Slider(
            value = strength,
            onValueChange = onStrength,
            enabled = supported && enabled,
            valueRange = 0f..1f,
            colors = SliderDefaults.colors(thumbColor = Accent, activeTrackColor = Accent, inactiveTrackColor = DividerColor)
        )
    }
}

@Composable
private fun VerticalBandSlider(
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    enabled: Boolean,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    Canvas(
        modifier = modifier
            .pointerInput(enabled, valueRange) {
                if (enabled) detectTapGestures { position ->
                    val top = 10.dp.toPx()
                    val bottom = size.height - 10.dp.toPx()
                    val fraction = (1f - ((position.y - top) / (bottom - top))).coerceIn(0f, 1f)
                    onValueChange(valueRange.start + fraction * (valueRange.endInclusive - valueRange.start))
                }
            }
            .pointerInput(enabled, valueRange) {
                if (enabled) detectDragGestures { change, _ ->
                    val top = 10.dp.toPx()
                    val bottom = size.height - 10.dp.toPx()
                    val fraction = (1f - ((change.position.y - top) / (bottom - top))).coerceIn(0f, 1f)
                    onValueChange(valueRange.start + fraction * (valueRange.endInclusive - valueRange.start))
                    change.consume()
                }
            }
    ) {
        val x = size.width / 2f
        val top = 10.dp.toPx()
        val bottom = size.height - 10.dp.toPx()
        val zeroFraction = ((0f - valueRange.start) / (valueRange.endInclusive - valueRange.start)).coerceIn(0f, 1f)
        val zeroY = bottom - zeroFraction * (bottom - top)
        val fraction = ((value - valueRange.start) / (valueRange.endInclusive - valueRange.start)).coerceIn(0f, 1f)
        val thumbY = bottom - fraction * (bottom - top)
        drawLine(DividerColor, Offset(x, top), Offset(x, bottom), strokeWidth = 5.dp.toPx(), cap = StrokeCap.Round)
        drawLine(TextMuted.copy(alpha = 0.75f), Offset(x - 7.dp.toPx(), zeroY), Offset(x + 7.dp.toPx(), zeroY), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
        drawLine(Accent, Offset(x, zeroY), Offset(x, thumbY), strokeWidth = 5.dp.toPx(), cap = StrokeCap.Round)
        drawCircle(if (enabled) Accent else TextMuted, radius = 8.dp.toPx(), center = Offset(x, thumbY))
        drawCircle(Surface, radius = 3.dp.toPx(), center = Offset(x, thumbY))
    }
}
