package com.krypton.player

import android.content.ContentResolver
import android.content.ContentUris
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.MediaStore
import java.io.ByteArrayOutputStream
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.charset.Charset

data class LyricLine(val timeMs: Long?, val text: String)

/** Lyrics parsing, embedded metadata extraction and automatic same-folder sidecar lookup. */
object LyricsReader {
    private val lrcPattern = Regex("""\[(\d{1,3}):(\d{2})(?:\.(\d{1,3}))?]""")
    private val metadataTimePattern = Regex("""\[(?:ar|ti|al|by|re|ve|length|offset):.*?]""", RegexOption.IGNORE_CASE)

    /** Parse LRC timestamps. Untimed lines remain available for plain TXT lyrics. */
    fun parse(text: String): List<LyricLine> {
        val output = mutableListOf<LyricLine>()
        text.lineSequence().forEach { rawLine ->
            val line = rawLine.trim().removePrefix("\uFEFF")
            if (line.isBlank() || metadataTimePattern.matches(line)) return@forEach
            val stamps = lrcPattern.findAll(line).toList()
            val lyric = line.replace(lrcPattern, "").trim()
            if (stamps.isEmpty()) {
                if (lyric.isNotBlank()) output += LyricLine(null, lyric)
            } else if (lyric.isNotBlank()) {
                stamps.forEach stampLoop@{ match ->
                    val minutes = match.groupValues[1].toLongOrNull() ?: return@stampLoop
                    val seconds = match.groupValues[2].toLongOrNull() ?: return@stampLoop
                    if (seconds > 59) return@stampLoop
                    val fractionText = match.groupValues[3]
                    val millis = fractionText.padEnd(3, '0').take(3).toLongOrNull() ?: 0L
                    output += LyricLine(minutes * 60_000L + seconds * 1_000L + millis, lyric)
                }
            }
        }
        return output.sortedBy { it.timeMs ?: Long.MAX_VALUE }
    }

    fun readDocument(resolver: ContentResolver, uri: Uri): List<LyricLine> =
        resolver.openInputStream(uri)?.bufferedReader()?.use { parse(it.readText()) }.orEmpty()

    /** Sidecar names for song.mp3, including common TXT fallback. */
    fun sidecarNames(audioDisplayName: String): List<String> {
        val stem = audioDisplayName.substringBeforeLast('.', audioDisplayName)
        return listOf("$stem.lrc", "$stem.txt")
    }

    /**
     * Automatic order: same-folder .lrc, embedded synchronized/unsynchronized lyrics,
     * then same-folder .txt. MediaStore is tried first; a persisted SAF tree grant is
     * used when Android does not expose non-media sidecars through MediaStore.
     */
    fun detectForTrack(
        resolver: ContentResolver,
        track: Track,
        lyricsFolderTreeUri: Uri?
    ): List<LyricLine> {
        val names = sidecarNames(track.displayName)
        readSidecar(resolver, track, names.first(), lyricsFolderTreeUri)?.let { parsed ->
            if (parsed.isNotEmpty()) return parsed
        }
        readEmbedded(resolver, track.uri).let { if (it.isNotEmpty()) return it }
        readSidecar(resolver, track, names.last(), lyricsFolderTreeUri)?.let { parsed ->
            if (parsed.isNotEmpty()) return parsed
        }
        return emptyList()
    }

    /** Recheck sidecars without re-reading embedded audio tags; used for newly added LRC files. */
    fun detectSidecarForTrack(
        resolver: ContentResolver,
        track: Track,
        lyricsFolderTreeUri: Uri?
    ): List<LyricLine> {
        val names = sidecarNames(track.displayName)
        readSidecar(resolver, track, names.first(), lyricsFolderTreeUri)?.let { if (it.isNotEmpty()) return it }
        readSidecar(resolver, track, names.last(), lyricsFolderTreeUri)?.let { if (it.isNotEmpty()) return it }
        return emptyList()
    }

    /** Extract standard ID3 USLT/SYLT and FLAC Vorbis-comment LYRICS fields. */
    fun fromEmbeddedMetadata(metadata: Map<String, String>): List<LyricLine> {
        val value = metadata.entries.firstOrNull {
            it.key.equals("lyrics", true) || it.key.equals("unsyncedlyrics", true) ||
                it.key.equals("syncedlyrics", true) || it.key.equals("uslt", true) ||
                it.key.equals("sylt", true)
        }?.value ?: return emptyList()
        return parse(value)
    }

    private fun readEmbedded(resolver: ContentResolver, audioUri: Uri): List<LyricLine> {
        val prefix = runCatching {
            resolver.openInputStream(audioUri)?.use(::readMetadataPrefix)
        }.getOrNull()
        if (prefix != null) {
            parseId3(prefix).takeIf { it.isNotEmpty() }?.let { return it }
            parseId3Anywhere(prefix).takeIf { it.isNotEmpty() }?.let { return it }
            parseFlacComments(prefix).takeIf { it.isNotEmpty() }?.let { return it }
            parseOggComments(prefix).takeIf { it.isNotEmpty() }?.let { return it }
            parseMp4Lyrics(prefix).takeIf { it.isNotEmpty() }?.let { return it }
        }
        // Some M4A files put the moov/ilst metadata at the end rather than at the front.
        if (prefix == null || !isMp4Container(prefix)) return emptyList()
        val tail = readLastBytes(resolver, audioUri, 8 * 1024 * 1024) ?: return emptyList()
        return parseMp4Lyrics(tail)
    }

    private fun readMetadataPrefix(input: java.io.InputStream): ByteArray? {
        val header = ByteArray(10)
        val headerCount = input.readUpTo(header)
        if (headerCount <= 0) return null
        val isId3 = headerCount == 10 && header[0] == 'I'.code.toByte() &&
            header[1] == 'D'.code.toByte() && header[2] == '3'.code.toByte()
        val targetSize = if (isId3) {
            (10L + synchsafe(header, 6)).coerceIn(10L, 8L * 1024L * 1024L).toInt()
        } else 2 * 1024 * 1024
        val output = ByteArrayOutputStream(minOf(targetSize, 64 * 1024))
        output.write(header, 0, headerCount)
        val chunk = ByteArray(16 * 1024)
        while (output.size() < targetSize) {
            val wanted = minOf(chunk.size, targetSize - output.size())
            val read = input.read(chunk, 0, wanted)
            if (read <= 0) break
            output.write(chunk, 0, read)
        }
        return output.toByteArray()
    }

    private fun isMp4Container(data: ByteArray): Boolean =
        data.size >= 8 && String(data, 4, 4, Charsets.ISO_8859_1) == "ftyp"

    private fun readLastBytes(resolver: ContentResolver, uri: Uri, maxBytes: Int): ByteArray? {
        return runCatching {
            resolver.openFileDescriptor(uri, "r")?.use { descriptor ->
                val channel = FileInputStream(descriptor.fileDescriptor).channel
                val size = channel.size()
                if (size <= 0L) return@use null
                val length = minOf(size, maxBytes.toLong()).toInt()
                val buffer = ByteBuffer.allocate(length)
                channel.position(size - length)
                while (buffer.hasRemaining() && channel.read(buffer) > 0) { /* bounded read */ }
                buffer.flip()
                ByteArray(buffer.remaining()).also { buffer.get(it) }
            }
        }.getOrNull()
    }

    private fun parseId3Anywhere(data: ByteArray): List<LyricLine> {
        val offset = indexOfBytes(data, "ID3".toByteArray(Charsets.ISO_8859_1))
        return if (offset <= 0) emptyList() else parseId3(data.copyOfRange(offset, data.size))
    }

    private fun readSidecar(
        resolver: ContentResolver,
        track: Track,
        name: String,
        treeUri: Uri?
    ): List<LyricLine>? {
        val mediaStoreUri = findSidecarInMediaStore(resolver, track.relativePath, name)
        if (mediaStoreUri != null) {
            runCatching { readDocument(resolver, mediaStoreUri) }.getOrNull()
                ?.takeIf { it.isNotEmpty() }?.let { return it }
        }
        if (treeUri != null) {
            val documentUri = findSidecarInTree(resolver, treeUri, track.relativePath, name)
            if (documentUri != null) {
                runCatching { readDocument(resolver, documentUri) }.getOrNull()
                    ?.takeIf { it.isNotEmpty() }?.let { return it }
            }
        }
        return null
    }

    private fun findSidecarInMediaStore(resolver: ContentResolver, relativePath: String?, name: String): Uri? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || relativePath.isNullOrBlank()) return null
        return runCatching {
            val collection = MediaStore.Files.getContentUri("external")
            val projection = arrayOf(MediaStore.Files.FileColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME)
            val selection = "${MediaStore.MediaColumns.RELATIVE_PATH}=?"
            resolver.query(collection, projection, selection, arrayOf(relativePath), null)?.use { cursor ->
                var match: Uri? = null
                while (cursor.moveToNext()) {
                    if (cursor.getString(1).equals(name, ignoreCase = true)) {
                        match = ContentUris.withAppendedId(collection, cursor.getLong(0))
                        break
                    }
                }
                match
            }
        }.getOrNull()
    }

    private fun findSidecarInTree(
        resolver: ContentResolver,
        treeUri: Uri,
        trackRelativePath: String?,
        fileName: String
    ): Uri? {
        if (trackRelativePath.isNullOrBlank()) return null
        return runCatching {
            val rootId = DocumentsContract.getTreeDocumentId(treeUri)
            val rootPath = rootId.substringAfter(':', "").trim('/')
            val trackPath = trackRelativePath.trim('/')
            val folderRemainder = when {
                rootPath.isEmpty() -> trackPath
                trackPath == rootPath -> ""
                trackPath.startsWith("$rootPath/") -> trackPath.removePrefix("$rootPath/")
                else -> return null
            }
            var parentId = rootId
            folderRemainder.split('/').filter { it.isNotBlank() }.forEach { segment ->
                parentId = findDocumentChild(resolver, treeUri, parentId, segment, directoryOnly = true) ?: return null
            }
            val fileId = findDocumentChild(resolver, treeUri, parentId, fileName, directoryOnly = false) ?: return null
            DocumentsContract.buildDocumentUriUsingTree(treeUri, fileId)
        }.getOrNull()
    }

    private fun findDocumentChild(
        resolver: ContentResolver,
        treeUri: Uri,
        parentId: String,
        name: String,
        directoryOnly: Boolean
    ): String? {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentId)
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE
        )
        resolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
            while (cursor.moveToNext()) {
                if (!cursor.getString(1).equals(name, ignoreCase = true)) continue
                val isDirectory = cursor.getString(2) == DocumentsContract.Document.MIME_TYPE_DIR
                if (directoryOnly == isDirectory) return cursor.getString(0)
            }
        }
        return null
    }

    private fun parseId3(data: ByteArray): List<LyricLine> {
        if (data.size < 10 || data[0] != 'I'.code.toByte() || data[1] != 'D'.code.toByte() || data[2] != '3'.code.toByte()) return emptyList()
        val version = data[3].toInt() and 0xff
        if (version !in 2..4) return emptyList()
        val tagSize = synchsafe(data, 6).coerceAtMost(data.size - 10)
        var offset = 10
        val end = 10 + tagSize
        // Skip an optional ID3 extended header before the first frame.
        if ((data[5].toInt() and 0x40) != 0 && offset + 4 <= end) {
            val extendedSize = if (version == 4) synchsafe(data, offset) else int32(data, offset)
            val skip = if (version == 4) extendedSize else extendedSize + 4
            if (skip > 0 && skip <= end - offset) offset += skip
        }
        val output = mutableListOf<LyricLine>()
        while (offset < end) {
            val headerSize = if (version == 2) 6 else 10
            if (offset + headerSize > end) break
            val idLength = if (version == 2) 3 else 4
            val frameId = String(data, offset, idLength, Charsets.ISO_8859_1)
            if (frameId.all { it == '\u0000' }) break
            val frameSize = if (version == 2) {
                ((data[offset + 3].toInt() and 0xff) shl 16) or
                    ((data[offset + 4].toInt() and 0xff) shl 8) or (data[offset + 5].toInt() and 0xff)
            } else if (version == 4) synchsafe(data, offset + 4) else int32(data, offset + 4)
            if (frameSize <= 0 || frameSize > end - offset - headerSize) break
            val frame = data.copyOfRange(offset + headerSize, offset + headerSize + frameSize)
            when (frameId) {
                "USLT", "ULT" -> parseUslt(frame)?.let { output += parse(it) }
                "SYLT", "SLT" -> output += parseSylt(frame)
                "TXXX", "TXX" -> parseLyricsUserText(frame)?.let { output += parse(it) }
            }
            offset += headerSize + frameSize
        }
        return output.distinctBy { Pair(it.timeMs, it.text) }.sortedBy { it.timeMs ?: Long.MAX_VALUE }
    }

    private fun parseUslt(frame: ByteArray): String? {
        if (frame.size < 5) return null
        val encoding = frame[0].toInt() and 0xff
        val start = 4 // encoding byte + 3-byte language
        val delimiter = findTextTerminator(frame, start, encoding) ?: return null
        val lyricStart = delimiter + terminatorLength(encoding)
        if (lyricStart >= frame.size) return null
        return decodeText(frame, lyricStart, frame.size, encoding).trim().takeIf { it.isNotBlank() }
    }

    private fun parseSylt(frame: ByteArray): List<LyricLine> {
        if (frame.size < 7) return emptyList()
        val encoding = frame[0].toInt() and 0xff
        val timestampFormat = frame[4].toInt() and 0xff
        var cursor = findTextTerminator(frame, 6, encoding)?.plus(terminatorLength(encoding)) ?: return emptyList()
        val result = mutableListOf<LyricLine>()
        while (cursor < frame.size) {
            val textEnd = findTextTerminator(frame, cursor, encoding) ?: break
            val text = decodeText(frame, cursor, textEnd, encoding).trim()
            cursor = textEnd + terminatorLength(encoding)
            if (cursor + 4 > frame.size) break
            val stamp = int32(frame, cursor).toLong() and 0xffffffffL
            cursor += 4
            val timeMs = if (timestampFormat == 2) stamp else if (timestampFormat == 1) stamp * 1000L / 75L else null
            if (text.isNotBlank() && timeMs != null) result += LyricLine(timeMs, text)
        }
        return result.sortedBy { it.timeMs ?: Long.MAX_VALUE }
    }

    private fun parseLyricsUserText(frame: ByteArray): String? {
        if (frame.size < 3) return null
        val encoding = frame[0].toInt() and 0xff
        val descriptionEnd = findTextTerminator(frame, 1, encoding) ?: return null
        val description = decodeText(frame, 1, descriptionEnd, encoding).trim()
        if (!description.contains("lyric", ignoreCase = true)) return null
        val valueStart = descriptionEnd + terminatorLength(encoding)
        return decodeText(frame, valueStart, frame.size, encoding).trim().takeIf { it.isNotBlank() }
    }

    private fun parseOggComments(data: ByteArray): List<LyricLine> {
        val vorbisMarker = byteArrayOf(0x03, 'v'.code.toByte(), 'o'.code.toByte(), 'r'.code.toByte(), 'b'.code.toByte(), 'i'.code.toByte(), 's'.code.toByte())
        val opusMarker = "OpusTags".toByteArray(Charsets.ISO_8859_1)
        val vorbisAt = indexOfBytes(data, vorbisMarker)
        val opusAt = indexOfBytes(data, opusMarker)
        val packetStart = when {
            vorbisAt >= 0 -> vorbisAt + vorbisMarker.size
            opusAt >= 0 -> opusAt + opusMarker.size
            else -> return emptyList()
        }
        if (packetStart + 8 > data.size) return emptyList()
        val comments = parseVorbisCommentBlock(data.copyOfRange(packetStart, data.size))
        val lyrics = comments.entries.firstOrNull {
            it.key.equals("LYRICS", true) || it.key.equals("UNSYNCEDLYRICS", true) || it.key.equals("SYNCEDLYRICS", true)
        }?.value
        return if (lyrics.isNullOrBlank()) emptyList() else parse(lyrics)
    }

    private fun parseMp4Lyrics(data: ByteArray): List<LyricLine> {
        // iTunes-style ©lyr item: atom header followed by a data atom and UTF-8 lyrics.
        val marker = byteArrayOf(0xA9.toByte(), 'l'.code.toByte(), 'y'.code.toByte(), 'r'.code.toByte())
        val itemAt = indexOfBytes(data, marker)
        if (itemAt < 0) return emptyList()
        val dataAt = indexOfBytes(data, "data".toByteArray(Charsets.ISO_8859_1), itemAt + marker.size)
        if (dataAt < 0 || dataAt + 16 > data.size) return emptyList()
        val atomSize = int32(data, dataAt - 4).toLong() and 0xffffffffL
        if (atomSize < 16L || atomSize > data.size - (dataAt - 4)) return emptyList()
        val start = dataAt + 12
        val end = (dataAt - 4 + atomSize.toInt()).coerceAtMost(data.size)
        val lyrics = String(data, start, end - start, Charsets.UTF_8).trim('\u0000', ' ', '\n', '\r')
        return if (lyrics.isBlank()) emptyList() else parse(lyrics)
    }

    private fun indexOfBytes(data: ByteArray, needle: ByteArray, from: Int = 0): Int {
        if (needle.isEmpty() || from < 0 || data.size < needle.size) return -1
        val last = data.size - needle.size
        for (i in from..last) {
            var match = true
            for (j in needle.indices) if (data[i + j] != needle[j]) { match = false; break }
            if (match) return i
        }
        return -1
    }

    private fun parseFlacComments(data: ByteArray): List<LyricLine> {
        if (data.size < 8 || String(data, 0, 4, Charsets.ISO_8859_1) != "fLaC") return emptyList()
        var offset = 4
        while (offset + 4 <= data.size) {
            val header = data[offset].toInt() and 0xff
            val type = header and 0x7f
            val length = ((data[offset + 1].toInt() and 0xff) shl 16) or
                ((data[offset + 2].toInt() and 0xff) shl 8) or (data[offset + 3].toInt() and 0xff)
            offset += 4
            if (length < 0 || offset + length > data.size) break
            if (type == 4) {
                val block = data.copyOfRange(offset, offset + length)
                val comments = parseVorbisCommentBlock(block)
                val lyric = comments.entries.firstOrNull {
                    it.key.equals("LYRICS", true) || it.key.equals("UNSYNCEDLYRICS", true) || it.key.equals("SYNCEDLYRICS", true)
                }?.value
                if (!lyric.isNullOrBlank()) return parse(lyric)
            }
            offset += length
            if ((header and 0x80) != 0) break
        }
        return emptyList()
    }

    private fun parseVorbisCommentBlock(block: ByteArray): Map<String, String> {
        val result = linkedMapOf<String, String>()
        var offset = 0
        if (block.size < 8) return result
        val vendorLength = littleEndianInt(block, offset).coerceIn(0, block.size - 4)
        offset += 4 + vendorLength
        if (offset + 4 > block.size) return result
        val count = littleEndianInt(block, offset).coerceIn(0, minOf(10_000, block.size))
        offset += 4
        repeat(count) {
            if (offset + 4 > block.size) return@repeat
            val length = littleEndianInt(block, offset)
            offset += 4
            if (length < 0 || offset + length > block.size) return@repeat
            val entry = String(block, offset, length, Charsets.UTF_8)
            val split = entry.indexOf('=')
            if (split > 0) result[entry.substring(0, split)] = entry.substring(split + 1)
            offset += length
        }
        return result
    }

    private fun findTextTerminator(data: ByteArray, start: Int, encoding: Int): Int? {
        if (encoding == 1 || encoding == 2) {
            var i = start
            while (i + 1 < data.size) {
                if (data[i] == 0.toByte() && data[i + 1] == 0.toByte()) return i
                i += 2
            }
        } else {
            for (i in start until data.size) if (data[i] == 0.toByte()) return i
        }
        return null
    }

    private fun terminatorLength(encoding: Int) = if (encoding == 1 || encoding == 2) 2 else 1

    private fun decodeText(data: ByteArray, start: Int, end: Int, encoding: Int): String {
        if (start >= end) return ""
        val charset: Charset = when (encoding) {
            1 -> Charsets.UTF_16
            2 -> Charsets.UTF_16BE
            3 -> Charsets.UTF_8
            else -> Charsets.ISO_8859_1
        }
        return String(data, start, end - start, charset).removePrefix("\uFEFF")
    }

    private fun synchsafe(data: ByteArray, offset: Int): Int =
        ((data[offset].toInt() and 0x7f) shl 21) or
            ((data[offset + 1].toInt() and 0x7f) shl 14) or
            ((data[offset + 2].toInt() and 0x7f) shl 7) or (data[offset + 3].toInt() and 0x7f)

    private fun int32(data: ByteArray, offset: Int): Int =
        ((data[offset].toInt() and 0xff) shl 24) or ((data[offset + 1].toInt() and 0xff) shl 16) or
            ((data[offset + 2].toInt() and 0xff) shl 8) or (data[offset + 3].toInt() and 0xff)

    private fun littleEndianInt(data: ByteArray, offset: Int): Int =
        (data[offset].toInt() and 0xff) or ((data[offset + 1].toInt() and 0xff) shl 8) or
            ((data[offset + 2].toInt() and 0xff) shl 16) or ((data[offset + 3].toInt() and 0xff) shl 24)

    private fun java.io.InputStream.readUpTo(buffer: ByteArray): Int {
        var total = 0
        while (total < buffer.size) {
            val read = read(buffer, total, buffer.size - total)
            if (read <= 0) break
            total += read
        }
        return total
    }
}
