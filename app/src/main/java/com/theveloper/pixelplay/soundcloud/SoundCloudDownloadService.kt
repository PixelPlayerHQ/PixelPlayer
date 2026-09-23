package com.theveloper.pixelplay.soundcloud

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.utils.AlbumArtUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SoundCloudDownloadService @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val http = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    /**
     * Saves a progressive/HTTP audio URL into public Music/PixelPlayer/SoundCloud
     * so it can appear in the system library / PixelPlayer local scan.
     */
    fun downloadToMusicFolder(track: SoundCloudResolvedTrack): File {
        val safeTitle = track.title.replace(Regex("""[\\/:*?"<>|]"""), "_").take(80).ifBlank { "track" }
        val safeArtist = track.artist.replace(Regex("""[\\/:*?"<>|]"""), "_").take(40).ifBlank { "SoundCloud" }
        val ext = when {
            track.mimeType?.contains("mpeg") == true -> "mp3"
            track.mimeType?.contains("mp4") == true || track.mimeType?.contains("m4a") == true -> "m4a"
            track.mimeType?.contains("ogg") == true || track.mimeType?.contains("opus") == true -> "ogg"
            track.streamUrl.contains(".m3u8") -> throw IllegalStateException(
                "This track is HLS-only — download needs a progressive stream. Try another track.",
            )
            else -> "mp3"
        }
        val fileName = "$safeArtist - $safeTitle.$ext"

        val request = Request.Builder()
            .url(track.streamUrl)
            .header(
                "User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:128.0) Gecko/20100101 Firefox/128.0",
            )
            .header("Referer", "https://soundcloud.com/")
            .build()

        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("Download HTTP ${response.code}")
            }
            val body = response.body ?: throw IllegalStateException("Empty download body")

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Audio.Media.DISPLAY_NAME, fileName)
                    put(MediaStore.Audio.Media.MIME_TYPE, track.mimeType ?: "audio/mpeg")
                    put(MediaStore.Audio.Media.RELATIVE_PATH, "Music/PixelPlayer/SoundCloud")
                    put(MediaStore.Audio.Media.IS_PENDING, 1)
                    put(MediaStore.Audio.Media.ARTIST, track.artist)
                    put(MediaStore.Audio.Media.TITLE, track.title)
                    put(MediaStore.Audio.Media.ALBUM, "SoundCloud")
                }
                val resolver = context.contentResolver
                val collection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                val uri = resolver.insert(collection, values)
                    ?: throw IllegalStateException("MediaStore insert failed")
                resolver.openOutputStream(uri)?.use { out -> body.byteStream().copyTo(out) }
                    ?: throw IllegalStateException("Could not open MediaStore stream")
                values.clear()
                values.put(MediaStore.Audio.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                return File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC),
                    "PixelPlayer/SoundCloud/$fileName",
                )
            }

            val dir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC),
                "PixelPlayer/SoundCloud",
            )
            if (!dir.exists() && !dir.mkdirs()) {
                throw IllegalStateException("Could not create ${dir.absolutePath}")
            }
            val outFile = File(dir, fileName)
            outFile.outputStream().use { out -> body.byteStream().copyTo(out) }
            return outFile
        }
    }

    /** Local SoundCloud downloads as playable [Song]s (MediaStore first, file fallback). */
    fun listDownloadedSongs(): List<Song> {
        val fromStore = queryMediaStoreDownloads()
        if (fromStore.isNotEmpty()) return fromStore
        return listDownloadedFiles().map { fileToSong(it) }
    }

    fun listDownloadedFiles(): List<File> {
        val dir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC),
            "PixelPlayer/SoundCloud",
        )
        if (!dir.isDirectory) return emptyList()
        return dir.listFiles()
            ?.filter { it.isFile && it.extension.lowercase() in AUDIO_EXTS }
            ?.sortedByDescending { it.lastModified() }
            .orEmpty()
    }

    private fun queryMediaStoreDownloads(): List<Song> {
        val songs = ArrayList<Song>()
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.MIME_TYPE,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.DATE_MODIFIED,
            MediaStore.Audio.Media.DISPLAY_NAME,
        )
        val (selection, args) = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            "${MediaStore.Audio.Media.RELATIVE_PATH} LIKE ?" to arrayOf("Music/PixelPlayer/SoundCloud%")
        } else {
            "${MediaStore.Audio.Media.DATA} LIKE ?" to arrayOf("%/Music/PixelPlayer/SoundCloud/%")
        }
        try {
            context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                args,
                "${MediaStore.Audio.Media.DATE_ADDED} DESC",
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val albumIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
                val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)
                val addedCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
                val modifiedCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_MODIFIED)
                val displayCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val path = cursor.getString(dataCol).orEmpty()
                    val title = cursor.getString(titleCol)
                        ?.takeIf { it.isNotBlank() && it != "<unknown>" }
                        ?: cursor.getString(displayCol)?.substringBeforeLast('.')
                        ?: "Unknown"
                    val artist = cursor.getString(artistCol)
                        ?.takeIf { it.isNotBlank() && it != "<unknown>" }
                        ?: "SoundCloud"
                    val albumId = cursor.getLong(albumIdCol)
                    val albumArt = AlbumArtUtils.getAlbumArtUri(
                        appContext = context,
                        path = path,
                        songId = id,
                        forceRefresh = false,
                    )
                    songs.add(
                        Song(
                            id = id.toString(),
                            title = title,
                            artist = artist,
                            artistId = -1L,
                            artists = emptyList(),
                            album = cursor.getString(albumCol)?.takeIf { it.isNotBlank() } ?: "SoundCloud",
                            albumId = albumId,
                            albumArtist = artist,
                            path = path,
                            contentUriString = ContentUris.withAppendedId(
                                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                                id,
                            ).toString(),
                            albumArtUriString = albumArt,
                            duration = cursor.getLong(durationCol).coerceAtLeast(0L),
                            genre = "SoundCloud",
                            dateAdded = cursor.getLong(addedCol),
                            dateModified = cursor.getLong(modifiedCol),
                            mimeType = cursor.getString(mimeCol),
                            bitrate = null,
                            sampleRate = null,
                        ),
                    )
                }
            }
        } catch (_: SecurityException) {
            // Fall back to filesystem listing.
        } catch (_: Exception) {
            // Fall back to filesystem listing.
        }
        return songs
    }

    private fun fileToSong(file: File): Song {
        val base = file.nameWithoutExtension
        val parts = base.split(" - ", limit = 2)
        val (artist, title) = if (parts.size == 2) parts[0] to parts[1] else "SoundCloud" to base
        val id = "sc_dl_${file.absolutePath.hashCode().toUInt()}"
        return Song(
            id = id,
            title = title,
            artist = artist,
            artistId = -1L,
            artists = emptyList(),
            album = "SoundCloud",
            albumId = -1L,
            albumArtist = artist,
            path = file.absolutePath,
            contentUriString = file.toURI().toString(),
            albumArtUriString = null,
            duration = 0L,
            genre = "SoundCloud",
            dateAdded = file.lastModified() / 1000L,
            dateModified = file.lastModified() / 1000L,
            mimeType = when (file.extension.lowercase()) {
                "mp3" -> "audio/mpeg"
                "m4a" -> "audio/mp4"
                "ogg" -> "audio/ogg"
                else -> "audio/*"
            },
            bitrate = null,
            sampleRate = null,
        )
    }

    companion object {
        private val AUDIO_EXTS = setOf("mp3", "m4a", "ogg", "aac", "wav", "flac")
    }
}
