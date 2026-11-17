package com.krypton.player

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

/**
 * One complete Krypton palette. Every screen reads its colours from the top-level
 * tokens in MainActivity.kt (Background, Surface, Accent, ...), which forward to the
 * currently selected theme, so changing the theme restyles the whole app instantly.
 */
internal data class KryptonTheme(
    val id: String,
    val label: String,
    val background: Color,
    val surface: Color,
    val surfaceRaised: Color,
    val surfaceHigh: Color,
    val accent: Color,
    val accentDeep: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val divider: Color
)

internal object KryptonThemes {
    /** The original neutral monochrome look. Stays the default so nothing changes until the user picks a theme. */
    val Mono = KryptonTheme(
        id = "mono", label = "Mono",
        background = Color(0xFF000000), surface = Color(0xFF0B0D10), surfaceRaised = Color(0xFF171A1F), surfaceHigh = Color(0xFF22262C),
        accent = Color(0xFFE9EBEF), accentDeep = Color(0xFFADB3BC),
        textPrimary = Color(0xFFF4F5F7), textSecondary = Color(0xFFB7BBC2), textMuted = Color(0xFF9298A1), divider = Color(0xFF282C33)
    )
    val Midnight = KryptonTheme(
        id = "midnight", label = "Midnight",
        background = Color(0xFF050913), surface = Color(0xFF0C1424), surfaceRaised = Color(0xFF16213A), surfaceHigh = Color(0xFF1F2E4D),
        accent = Color(0xFF5AA9FF), accentDeep = Color(0xFF3B7BD6),
        textPrimary = Color(0xFFEEF3FF), textSecondary = Color(0xFFB4C2DD), textMuted = Color(0xFF8393B3), divider = Color(0xFF22304D)
    )
    val Ember = KryptonTheme(
        id = "ember", label = "Ember",
        background = Color(0xFF0A0605), surface = Color(0xFF150E0B), surfaceRaised = Color(0xFF241812), surfaceHigh = Color(0xFF33231A),
        accent = Color(0xFFFF8A3D), accentDeep = Color(0xFFE0661A),
        textPrimary = Color(0xFFFFF3EA), textSecondary = Color(0xFFDDC3B2), textMuted = Color(0xFFA98F7E), divider = Color(0xFF3A2A20)
    )
    val Emerald = KryptonTheme(
        id = "emerald", label = "Emerald",
        background = Color(0xFF030A07), surface = Color(0xFF08140F), surfaceRaised = Color(0xFF11241B), surfaceHigh = Color(0xFF1A3629),
        accent = Color(0xFF3DDC97), accentDeep = Color(0xFF22B573),
        textPrimary = Color(0xFFEAFBF3), textSecondary = Color(0xFFB2D6C4), textMuted = Color(0xFF7FA08F), divider = Color(0xFF1E3A2C)
    )
    val Violet = KryptonTheme(
        id = "violet", label = "Violet",
        background = Color(0xFF07050E), surface = Color(0xFF100C1C), surfaceRaised = Color(0xFF1C1530), surfaceHigh = Color(0xFF2A2146),
        accent = Color(0xFFB388FF), accentDeep = Color(0xFF8C5CF0),
        textPrimary = Color(0xFFF3EEFF), textSecondary = Color(0xFFC8BBE6), textMuted = Color(0xFF9486B3), divider = Color(0xFF2D2450)
    )
    val Rose = KryptonTheme(
        id = "rose", label = "Rose",
        background = Color(0xFF0B0509), surface = Color(0xFF160C12), surfaceRaised = Color(0xFF26141E), surfaceHigh = Color(0xFF361E2B),
        accent = Color(0xFFFF6B9A), accentDeep = Color(0xFFE04578),
        textPrimary = Color(0xFFFFEFF4), textSecondary = Color(0xFFE0BDCB), textMuted = Color(0xFFAE8C9A), divider = Color(0xFF3E2434)
    )

    val all: List<KryptonTheme> = listOf(Mono, Midnight, Ember, Emerald, Violet, Rose)

    fun byId(id: String?): KryptonTheme = all.firstOrNull { it.id == id } ?: Mono
}

/** Holds the selected theme as Compose state and persists it in SharedPreferences. */
internal object ThemeState {
    private const val PREFS = "krypton_theme"
    private const val KEY_THEME = "theme_id"
    private const val KEY_ARTWORK = "artwork_accent"

    var current: KryptonTheme by mutableStateOf(KryptonThemes.Mono)
        private set

    /** When true the Player, Queue and Lyrics use a colour sampled from the cover art instead of the theme accent. */
    var useArtworkAccent: Boolean by mutableStateOf(true)
        private set

    fun load(context: Context) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        current = KryptonThemes.byId(prefs.getString(KEY_THEME, null))
        useArtworkAccent = prefs.getBoolean(KEY_ARTWORK, true)
    }

    fun select(context: Context, theme: KryptonTheme) {
        current = theme
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_THEME, theme.id).apply()
    }

    fun setArtworkAccent(context: Context, enabled: Boolean) {
        useArtworkAccent = enabled
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_ARTWORK, enabled).apply()
    }
}

/** Mixes [tint] into [base] by [amount] (0..1). Used for theme-tinted gradients and surfaces. */
internal fun blendColor(base: Color, tint: Color, amount: Float): Color {
    val t = amount.coerceIn(0f, 1f)
    return Color(
        red = base.red * (1f - t) + tint.red * t,
        green = base.green * (1f - t) + tint.green * t,
        blue = base.blue * (1f - t) + tint.blue * t,
        alpha = base.alpha
    )
}
