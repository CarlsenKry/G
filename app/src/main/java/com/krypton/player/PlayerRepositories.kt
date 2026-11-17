package com.krypton.player

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/** Persists user-created playlists as MediaStore track IDs. */
data class UserPlaylist(
    val id: String,
    val name: String,
    val trackIds: List<Long>,
    val createdAt: Long = System.currentTimeMillis()
)

class PlaylistRepository(context: Context) {
    private val prefs = context.getSharedPreferences("krypton_playlists", Context.MODE_PRIVATE)

    @Synchronized fun all(): List<UserPlaylist> {
        val source = prefs.getString("items", "[]") ?: "[]"
        return runCatching {
            val array = JSONArray(source)
            buildList {
                for (i in 0 until array.length()) {
                    val item = array.optJSONObject(i) ?: continue
                    val ids = item.optJSONArray("trackIds") ?: JSONArray()
                    add(
                        UserPlaylist(
                            id = item.optString("id"),
                            name = item.optString("name", "Untitled playlist"),
                            trackIds = (0 until ids.length()).mapNotNull { ids.optLong(it).takeIf { value -> value > 0L } },
                            createdAt = item.optLong("createdAt", System.currentTimeMillis())
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    @Synchronized fun create(name: String): UserPlaylist {
        val item = UserPlaylist(id = java.util.UUID.randomUUID().toString(), name = name.trim().ifBlank { "New playlist" }, trackIds = emptyList())
        save(all() + item)
        return item
    }

    @Synchronized fun rename(id: String, name: String) {
        save(all().map { if (it.id == id) it.copy(name = name.trim().ifBlank { it.name }) else it })
    }

    @Synchronized fun delete(id: String) { save(all().filterNot { it.id == id }) }

    @Synchronized fun addTracks(id: String, trackIds: List<Long>) {
        save(all().map { playlist ->
            if (playlist.id == id) playlist.copy(trackIds = (playlist.trackIds + trackIds).distinct()) else playlist
        })
    }

    @Synchronized fun replaceTracks(id: String, trackIds: List<Long>) {
        save(all().map { if (it.id == id) it.copy(trackIds = trackIds.distinct()) else it })
    }

    @Synchronized fun imported(name: String, trackIds: List<Long>): UserPlaylist {
        val item = UserPlaylist(
            id = java.util.UUID.randomUUID().toString(),
            name = name.trim().ifBlank { "Imported playlist" },
            trackIds = trackIds.distinct()
        )
        save(all() + item)
        return item
    }

    private fun save(items: List<UserPlaylist>) {
        val array = JSONArray()
        items.forEach { playlist ->
            val ids = JSONArray()
            playlist.trackIds.forEach { trackId -> ids.put(trackId) }
            array.put(
                JSONObject()
                    .put("id", playlist.id)
                    .put("name", playlist.name)
                    .put("trackIds", ids)
                    .put("createdAt", playlist.createdAt)
            )
        }
        prefs.edit().putString("items", array.toString()).apply()
    }
}

data class DailyListening(val date: LocalDate, val durationMs: Long)
data class TrackListening(val track: Track, val durationMs: Long, val playCount: Int)
data class StatsSnapshot(
    val totalListenedMs: Long,
    val tracksPlayed: Int,
    val favorites: Int,
    val librarySize: Int,
    val lastSevenDays: List<DailyListening>,
    val mostListened: List<TrackListening>
)

/** Playback statistics are stored locally and only count a play after 50% completion. */
class PlaybackStatsStore(context: Context) {
    private val prefs = context.getSharedPreferences("krypton_playback_stats", Context.MODE_PRIVATE)

    fun playCount(trackId: Long): Int = prefs.getInt("track_${trackId}_plays", 0)

    fun isFavorite(trackId: Long): Boolean = prefs.getStringSet("favorite_track_ids", emptySet())?.contains(trackId.toString()) == true

    fun toggleFavorite(trackId: Long) {
        val updated = prefs.getStringSet("favorite_track_ids", emptySet())?.toMutableSet() ?: mutableSetOf()
        if (!updated.add(trackId.toString())) updated.remove(trackId.toString())
        prefs.edit().putStringSet("favorite_track_ids", updated).apply()
    }

    fun recordListening(track: Track, deltaMs: Long) {
        if (deltaMs <= 0L) return
        val today = LocalDate.now().toString()
        val edit = prefs.edit()
        edit.putLong("total_listened_ms", prefs.getLong("total_listened_ms", 0L) + deltaMs)
        edit.putLong("day_${today}", prefs.getLong("day_${today}", 0L) + deltaMs)
        edit.putLong("track_${track.id}_listened_ms", prefs.getLong("track_${track.id}_listened_ms", 0L) + deltaMs)
        edit.putString("track_${track.id}_title", track.title)
        edit.putString("track_${track.id}_artist", track.artist)
        edit.apply()
    }

    fun recordQualifiedPlay(track: Track) {
        prefs.edit()
            .putInt("tracks_played", prefs.getInt("tracks_played", 0) + 1)
            .putInt("track_${track.id}_plays", playCount(track.id) + 1)
            .putString("track_${track.id}_title", track.title)
            .putString("track_${track.id}_artist", track.artist)
            .apply()
    }

    fun snapshot(tracks: List<Track>): StatsSnapshot {
        val today = LocalDate.now()
        val days = (6 downTo 0).map { offset ->
            val date = today.minusDays(offset.toLong())
            DailyListening(date, prefs.getLong("day_${date}", 0L))
        }
        val ranking = tracks.map { track ->
            TrackListening(
                track = track,
                durationMs = prefs.getLong("track_${track.id}_listened_ms", 0L),
                playCount = playCount(track.id)
            )
        }.filter { it.durationMs > 0L }.sortedByDescending { it.durationMs }.take(20)
        return StatsSnapshot(
            totalListenedMs = prefs.getLong("total_listened_ms", 0L),
            tracksPlayed = prefs.getInt("tracks_played", 0),
            favorites = prefs.getStringSet("favorite_track_ids", emptySet())?.size ?: 0,
            librarySize = tracks.size,
            lastSevenDays = days,
            mostListened = ranking
        )
    }
}
