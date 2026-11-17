package com.krypton.player

import android.content.Context
import android.provider.MediaStore

/** A locally indexed audio item. dateAddedSeconds is MediaStore's added timestamp. */
data class Track(
    val id: Long,
    val title: String,
    val artist: String,
    val uri: android.net.Uri,
    val durationMs: Long = 0L,
    val dateAddedSeconds: Long = 0L,
    val displayName: String = title,
    val filePath: String? = null,
    val relativePath: String? = null
)

object MusicLibrary {
    fun query(context: Context): List<Track> {
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = mutableListOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.DATA
        ).apply {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) add(MediaStore.Audio.Media.RELATIVE_PATH)
        }.toTypedArray()
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        val result = mutableListOf<Track>()
        context.contentResolver.query(
            collection, projection, selection, null,
            "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val addedCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
            val displayCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
            val dataCol = cursor.getColumnIndex(MediaStore.Audio.Media.DATA)
            val relativePathCol = cursor.getColumnIndex(MediaStore.Audio.Media.RELATIVE_PATH)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val title = cursor.getString(titleCol)?.takeIf { it.isNotBlank() } ?: "Unknown title"
                result += Track(
                    id = id,
                    title = title,
                    artist = cursor.getString(artistCol)?.takeIf { it.isNotBlank() && it != "<unknown>" } ?: "Unknown artist",
                    uri = android.content.ContentUris.withAppendedId(collection, id),
                    durationMs = cursor.getLong(durationCol).coerceAtLeast(0L),
                    dateAddedSeconds = cursor.getLong(addedCol).coerceAtLeast(0L),
                    displayName = cursor.getString(displayCol)?.takeIf { it.isNotBlank() } ?: title,
                    filePath = if (dataCol >= 0) cursor.getString(dataCol)?.takeIf { it.isNotBlank() } else null,
                    relativePath = if (relativePathCol >= 0) cursor.getString(relativePathCol)?.takeIf { it.isNotBlank() } else null
                )
            }
        }
        return result
    }
}
