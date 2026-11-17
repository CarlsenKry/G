package com.krypton.player

import android.content.Context
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.Virtualizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Audio effects are owned by the playback service, not by an Activity. This keeps
 * the EQ attached to Media3's audio session while the UI is closed or recreated.
 */
internal data class EqBandState(
    val label: String,
    val frequencyHz: Int,
    val levelDb: Float
)

internal data class AudioEffectsUiState(
    val available: Boolean = false,
    val enabled: Boolean = false,
    val bands: List<EqBandState> = emptyList(),
    val minLevelDb: Float = -15f,
    val maxLevelDb: Float = 15f,
    val presets: List<String> = emptyList(),
    val selectedPreset: Int = -1,
    val bassBoostSupported: Boolean = false,
    val bassBoostEnabled: Boolean = false,
    val bassBoostStrength: Float = 0f,
    val virtualizerSupported: Boolean = false,
    val virtualizerEnabled: Boolean = false,
    val virtualizerStrength: Float = 0f,
    val error: String? = null
)

internal object KryptonAudioEffects {
    private val mutableState = MutableStateFlow(AudioEffectsUiState())
    val state: StateFlow<AudioEffectsUiState> = mutableState.asStateFlow()

    private var sessionId: Int = 0
    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var virtualizer: Virtualizer? = null
    private var preferences: android.content.SharedPreferences? = null

    @Synchronized
    fun attach(context: Context, newSessionId: Int) {
        if (newSessionId <= 0 || newSessionId == sessionId && equalizer != null) return
        releaseEffectsOnly()
        sessionId = newSessionId
        preferences = context.applicationContext.getSharedPreferences("krypton_audio_effects", Context.MODE_PRIVATE)

        try {
            val eq = Equalizer(0, newSessionId)
            equalizer = eq
            val range = eq.bandLevelRange
            val minimum = range.getOrNull(0)?.toFloat()?.div(100f) ?: -15f
            val maximum = range.getOrNull(1)?.toFloat()?.div(100f) ?: 15f
            val savedEnabled = preferences?.getBoolean("eq_enabled", true) ?: true
            val bands = (0 until eq.numberOfBands.toInt()).map { index ->
                val band = index.toShort()
                val frequency = (eq.getCenterFreq(band).toInt() / 1000).coerceAtLeast(1)
                val saved = preferences?.getInt("band_$index", Int.MIN_VALUE)
                val level = (saved?.takeIf { it != Int.MIN_VALUE }?.toFloat()?.div(100f) ?: 0f)
                    .coerceIn(minimum, maximum)
                eq.setBandLevel(band, (level * 100f).toInt().toShort())
                EqBandState(formatFrequency(frequency), frequency, level)
            }
            val presets = (0 until eq.numberOfPresets.toInt()).map { eq.getPresetName(it.toShort()) }
            eq.enabled = savedEnabled

            bassBoost = runCatching { BassBoost(0, newSessionId) }.getOrNull()?.also {
                it.enabled = preferences?.getBoolean("bass_enabled", false) ?: false
                it.setStrength((preferences?.getInt("bass_strength", 250) ?: 250).coerceIn(0, 1000).toShort())
            }
            virtualizer = runCatching { Virtualizer(0, newSessionId) }.getOrNull()?.also {
                it.enabled = preferences?.getBoolean("virtualizer_enabled", false) ?: false
                it.setStrength((preferences?.getInt("virtualizer_strength", 200) ?: 200).coerceIn(0, 1000).toShort())
            }

            mutableState.value = AudioEffectsUiState(
                available = true,
                enabled = eq.enabled,
                bands = bands,
                minLevelDb = minimum,
                maxLevelDb = maximum,
                presets = presets,
                bassBoostSupported = bassBoost?.strengthSupported == true,
                bassBoostEnabled = bassBoost?.enabled == true,
                bassBoostStrength = (bassBoost?.roundedStrength?.toFloat() ?: 0f) / 1000f,
                virtualizerSupported = virtualizer?.strengthSupported == true,
                virtualizerEnabled = virtualizer?.enabled == true,
                virtualizerStrength = (virtualizer?.roundedStrength?.toFloat() ?: 0f) / 1000f
            )
        } catch (error: Exception) {
            releaseEffectsOnly()
            mutableState.value = AudioEffectsUiState(error = error.message ?: "This device does not expose an equalizer for the current audio session.")
        }
    }

    @Synchronized
    fun setEnabled(enabled: Boolean) {
        runCatching { equalizer?.enabled = enabled }
        preferences?.edit()?.putBoolean("eq_enabled", enabled)?.apply()
        mutableState.value = mutableState.value.copy(enabled = enabled)
    }

    @Synchronized
    fun setBandLevel(index: Int, levelDb: Float) {
        val current = mutableState.value
        val band = current.bands.getOrNull(index) ?: return
        val clamped = levelDb.coerceIn(current.minLevelDb, current.maxLevelDb)
        runCatching { equalizer?.setBandLevel(index.toShort(), (clamped * 100f).toInt().toShort()) }
        preferences?.edit()?.putInt("band_$index", (clamped * 100f).toInt())?.apply()
        mutableState.value = current.copy(
            bands = current.bands.toMutableList().also { it[index] = band.copy(levelDb = clamped) },
            selectedPreset = -2
        )
    }

    @Synchronized
    fun applyPreset(index: Int) {
        val eq = equalizer ?: return
        if (index !in 0 until eq.numberOfPresets.toInt()) return
        runCatching { eq.usePreset(index.toShort()) }.onSuccess {
            val current = mutableState.value
            val bands = current.bands.mapIndexed { bandIndex, band ->
                band.copy(levelDb = eq.getBandLevel(bandIndex.toShort()).toFloat() / 100f)
            }
            val editor = preferences?.edit()
            bands.forEachIndexed { bandIndex, band -> editor?.putInt("band_$bandIndex", (band.levelDb * 100f).toInt()) }
            editor?.apply()
            mutableState.value = current.copy(bands = bands, selectedPreset = index)
        }
    }

    @Synchronized
    fun reset() {
        val current = mutableState.value
        val flat = current.bands.map { it.copy(levelDb = 0f.coerceIn(current.minLevelDb, current.maxLevelDb)) }
        flat.forEachIndexed { index, band ->
            runCatching { equalizer?.setBandLevel(index.toShort(), (band.levelDb * 100f).toInt().toShort()) }
        }
        val editor = preferences?.edit()?.putBoolean("eq_enabled", true)
        flat.forEachIndexed { index, band -> editor?.putInt("band_$index", (band.levelDb * 100f).toInt()) }
        editor?.apply()
        runCatching { equalizer?.enabled = true }
        mutableState.value = current.copy(enabled = true, bands = flat, selectedPreset = -1)
    }

    @Synchronized
    fun setBassBoostEnabled(enabled: Boolean) {
        runCatching { bassBoost?.enabled = enabled }
        preferences?.edit()?.putBoolean("bass_enabled", enabled)?.apply()
        mutableState.value = mutableState.value.copy(bassBoostEnabled = enabled)
    }

    @Synchronized
    fun setBassBoostStrength(value: Float) {
        val strength = (value.coerceIn(0f, 1f) * 1000f).toInt()
        runCatching { bassBoost?.setStrength(strength.toShort()) }
        preferences?.edit()?.putInt("bass_strength", strength)?.apply()
        mutableState.value = mutableState.value.copy(bassBoostStrength = strength / 1000f)
    }

    @Synchronized
    fun setVirtualizerEnabled(enabled: Boolean) {
        runCatching { virtualizer?.enabled = enabled }
        preferences?.edit()?.putBoolean("virtualizer_enabled", enabled)?.apply()
        mutableState.value = mutableState.value.copy(virtualizerEnabled = enabled)
    }

    @Synchronized
    fun setVirtualizerStrength(value: Float) {
        val strength = (value.coerceIn(0f, 1f) * 1000f).toInt()
        runCatching { virtualizer?.setStrength(strength.toShort()) }
        preferences?.edit()?.putInt("virtualizer_strength", strength)?.apply()
        mutableState.value = mutableState.value.copy(virtualizerStrength = strength / 1000f)
    }

    @Synchronized
    fun release() {
        releaseEffectsOnly()
        sessionId = 0
        mutableState.value = AudioEffectsUiState()
    }

    private fun releaseEffectsOnly() {
        runCatching { equalizer?.release() }
        runCatching { bassBoost?.release() }
        runCatching { virtualizer?.release() }
        equalizer = null
        bassBoost = null
        virtualizer = null
    }

    private fun formatFrequency(frequency: Int): String = when {
        frequency >= 1000 -> "${frequency / 1000}.${(frequency % 1000) / 100}kHz"
        else -> "${frequency}Hz"
    }
}
