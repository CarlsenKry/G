package com.krypton.player

import androidx.media3.common.Player

object PlaybackOptions {
    fun setShuffle(player: Player, enabled: Boolean) {
        player.shuffleModeEnabled = enabled
    }

    fun cycleRepeat(player: Player) {
        player.repeatMode = when (player.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    fun setSpeed(player: Player, speed: Float) {
        player.setPlaybackSpeed(speed.coerceIn(0.5f, 2.0f))
    }
}
