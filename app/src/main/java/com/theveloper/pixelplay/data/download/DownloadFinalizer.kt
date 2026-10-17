package com.theveloper.pixelplay.data.download

import android.os.ParcelFileDescriptor
import android.util.Base64
import android.util.Log
import com.antonkarpenko.ffmpegkit.FFmpegKit
import com.antonkarpenko.ffmpegkit.ReturnCode
import com.kyant.taglib.Picture
import com.kyant.taglib.TagLib
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Host-side post-processing of an extension download, ported from SpotiFLAC-Mobile's
 * NativeDownloadFinalizer: decrypt protected streams with FFmpeg, then (re)write tags,
 * since the backend cannot tag a file that was still encrypted when it finished.
 */
@Singleton
class DownloadFinalizer @Inject constructor() {

    /** The decryption the extension asked for, or null when the file is already playable. */
    fun decryptionFor(response: DownloadResponse): DecryptionInfo? {
        val info = response.decryption
        val key = info?.key?.trim().orEmpty().ifEmpty { response.decryptionKey?.trim().orEmpty() }
        if (key.isEmpty()) return null
        return (info ?: DecryptionInfo(strategy = "ffmpeg.mov_key")).copy(key = key)
    }

    /** Decrypts [input] next to itself. Returns the decrypted file or throws with FFmpeg's output. */
    fun decrypt(input: File, info: DecryptionInfo): File {
        if (info.strategy.isNotBlank() && normalizeStrategy(info.strategy) !in MOV_KEY_STRATEGIES) {
            throw IllegalStateException("Unsupported decryption strategy: ${info.strategy}")
        }
        val inputFormat = info.inputFormat.trim().ifEmpty { "mov" }
        val preferredExt = normalizeExt(info.outputExtension).ifEmpty { ".flac" }

        // Same fallback ladder as the reference: FLAC (audio only) -> M4A -> MP4 -> MOV muxer
        // (the MP4 muxer rejects some codecs such as AC-4, MOV keeps the codec params).
        val attempts = buildList {
            add(Attempt(preferredExt, audioOnly = preferredExt == ".flac", forceMov = false))
            if (preferredExt == ".flac") add(Attempt(".m4a", audioOnly = false, forceMov = false))
            if (preferredExt == ".flac" || preferredExt == ".m4a") add(Attempt(".mp4", audioOnly = false, forceMov = false))
            add(Attempt(".mp4", audioOnly = false, forceMov = true))
        }

        var lastOutput = ""
        for (key in keyCandidates(info.key)) {
            for (attempt in attempts) {
                val output = File(input.parentFile, "decrypted${attempt.ext}")
                output.delete()
                val args = buildList {
                    addAll(listOf("-v", "error", "-decryption_key", key, "-f", inputFormat, "-i", input.absolutePath))
                    if (attempt.audioOnly) addAll(listOf("-map", "0:a"))
                    addAll(listOf("-c", "copy"))
                    when {
                        attempt.forceMov -> addAll(listOf("-f", "mov"))
                        // Without this FFmpeg writes FLAC-in-MP4 under a .flac name.
                        attempt.ext == ".flac" -> addAll(listOf("-f", "flac"))
                    }
                    addAll(listOf(output.absolutePath, "-y"))
                }
                val session = FFmpegKit.executeWithArguments(args.toTypedArray())
                lastOutput = session.output.orEmpty()
                if (ReturnCode.isSuccess(session.returnCode) && output.length() > 0) {
                    Log.i(TAG, "Decrypted ${input.name} -> ${output.name}")
                    input.delete()
                    return output
                }
                output.delete()
            }
        }
        throw IllegalStateException("Failed to decrypt encrypted stream: ${lastOutput.takeLast(300)}")
    }

    /** Fills in tags (and cover) the file is missing, preferring backend-resolved metadata. */
    fun writeTags(file: File, track: ExtTrack, response: DownloadResponse) {
        runCatching {
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_WRITE).use { fd ->
                val existing = TagLib.getMetadata(fd.dup().detachFd(), readPictures = false)
                val map = HashMap(existing?.propertyMap ?: emptyMap())
                fun put(key: String, value: String?) {
                    if (value.isNullOrBlank() || !map[key].isNullOrEmpty()) return
                    map[key] = arrayOf(value)
                }
                fun putNumber(key: String, value: Int) { if (value > 0) put(key, value.toString()) }
                put("TITLE", response.title ?: track.name)
                put("ARTIST", response.artist ?: track.artists)
                put("ALBUM", response.album ?: track.albumName)
                put("ALBUMARTIST", response.albumArtist ?: track.albumArtist)
                putNumber("TRACKNUMBER", response.trackNumber.takeIf { it > 0 } ?: track.trackNumber)
                putNumber("DISCNUMBER", response.discNumber.takeIf { it > 0 } ?: track.discNumber)
                put("DATE", response.releaseDate ?: track.releaseDate)
                put("ISRC", response.isrc ?: track.isrc)
                put("GENRE", response.genre ?: track.genre)
                put("LABEL", response.label ?: track.label)
                put("COPYRIGHT", response.copyright ?: track.copyright)
                put("COMPOSER", response.composer ?: track.composer)
                put("LYRICS", response.lyricsLrc)
                if (!TagLib.savePropertyMap(fd.dup().detachFd(), map)) Log.w(TAG, "Tag save failed for ${file.name}")

                val hasCover = TagLib.getPictures(fd.dup().detachFd()).isNotEmpty()
                val coverUrl = response.coverUrl?.takeIf { it.isNotBlank() } ?: track.coverUrl
                if (!hasCover && !coverUrl.isNullOrBlank()) {
                    fetch(coverUrl)?.let { bytes ->
                        val mime = if (bytes.size > 3 && bytes[0] == 0x89.toByte() && bytes[1] == 'P'.code.toByte()) "image/png" else "image/jpeg"
                        TagLib.savePictures(
                            fd.dup().detachFd(),
                            arrayOf(Picture(data = bytes, description = "Front Cover", pictureType = "Front Cover", mimeType = mime)),
                        )
                    }
                }
            }
        }.onFailure { Log.w(TAG, "Tagging ${file.name} failed", it) }
    }

    private fun fetch(url: String): ByteArray? = runCatching {
        (URL(url).openConnection() as HttpURLConnection).run {
            connectTimeout = 15_000
            readTimeout = 15_000
            try { if (responseCode in 200..299) inputStream.use { it.readBytes() } else null } finally { disconnect() }
        }
    }.getOrNull()

    private data class Attempt(val ext: String, val audioOnly: Boolean, val forceMov: Boolean)

    private companion object {
        const val TAG = "DownloadFinalizer"

        val MOV_KEY_STRATEGIES = setOf("ffmpeg.mov_key", "mov_decryption_key", "mp4_decryption_key", "ffmpeg.mp4_decryption_key")

        fun normalizeStrategy(s: String) = s.trim().lowercase().replace("ffmpeg_", "ffmpeg.")

        fun normalizeExt(ext: String): String {
            val e = ext.trim().lowercase()
            return if (e.isEmpty() || e.startsWith(".")) e else ".$e"
        }

        /** Extensions hand out keys as hex, 0x-hex or base64; FFmpeg wants plain hex. */
        fun keyCandidates(raw: String): List<String> {
            val out = linkedSetOf<String>()
            val trimmed = raw.trim()
            if (trimmed.isEmpty()) return emptyList()
            out += trimmed
            val noPrefix = trimmed.removePrefix("0x").removePrefix("0X")
            out += noPrefix
            val hex = noPrefix.replace(Regex("[^0-9a-fA-F]"), "")
            if (hex.isNotEmpty() && hex.length % 2 == 0) out += hex
            runCatching { Base64.decode(noPrefix.replace(Regex("\\s+"), ""), Base64.DEFAULT) }
                .getOrNull()?.takeIf { it.isNotEmpty() }
                ?.let { bytes -> out += bytes.joinToString("") { "%02x".format(it) } }
            return out.filter { it.isNotBlank() }
        }
    }
}
