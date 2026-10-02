package com.theveloper.pixelplay.soundcloud

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import androidx.core.net.toUri
import com.kyant.taglib.Picture
import com.kyant.taglib.TagLib
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.utils.AlbumArtUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

data class SoundCloudDownloadCatalog(
    val songs: List<Song>,
    val repairs: List<Song> = emptyList(),
)

@Singleton
class SoundCloudDownloadService @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val artworkPreferences by lazy {
        context.getSharedPreferences(ARTWORK_PREFERENCES, Context.MODE_PRIVATE)
    }
    private val repairedPreferences by lazy {
        context.getSharedPreferences(REPAIRED_PREFERENCES, Context.MODE_PRIVATE)
    }
    private val http = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    /**
     * Saves a progressive/HTTP audio URL into public Music/PixelPlayer/SoundCloud
     * and writes the track's real title, artist, album, and cover into the file.
     */
    fun downloadToMusicFolder(track: SoundCloudResolvedTrack, tags: SoundCloudFileTags): File {
        val safeTitle = tags.title.replace(UNSAFE_FILENAME, "_").take(80).ifBlank { "track" }
        val safeArtist = tags.artist.replace(UNSAFE_FILENAME, "_").take(40).ifBlank { "Unknown artist" }
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
        val request = audioRequest(track.streamUrl)

        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("Download HTTP ${response.code}")
            }
            val body = response.body ?: throw IllegalStateException("Empty download body")
            val artwork = tags.artworkUrl?.let { downloadArtwork(it) }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = contentValuesFor(fileName, track.mimeType, tags)
                values.put(MediaStore.Audio.Media.IS_PENDING, 1)
                val resolver = context.contentResolver
                val collection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                val uri = resolver.insert(collection, values)
                    ?: throw IllegalStateException("MediaStore insert failed")
                resolver.openOutputStream(uri)?.use { out -> body.byteStream().copyTo(out) }
                    ?: throw IllegalStateException("Could not open MediaStore stream")
                val embedded = embedTags(uri, tags, artwork)
                values.clear()
                values.putAll(metadataValues(tags))
                values.put(MediaStore.Audio.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                rememberArtwork(fileName, tags.artworkUrl)
                if (embedded) markRepaired(fileName)
                return File(downloadDirectory(), fileName)
            }

            val dir = downloadDirectory()
            if (!dir.exists() && !dir.mkdirs()) {
                throw IllegalStateException("Could not create ${dir.absolutePath}")
            }
            val outFile = File(dir, fileName)
            outFile.outputStream().use { out -> body.byteStream().copyTo(out) }
            val embedded = embedTags(outFile.toUri(), tags, artwork)
            rememberArtwork(fileName, tags.artworkUrl)
            if (embedded) markRepaired(fileName)
            return outFile
        }
    }

    /**
     * Writes corrected tags and cover art into a download that was saved with
     * placeholder metadata. Returns an artwork URI the downloads tab can show.
     */
    fun rewriteTags(song: Song, tags: SoundCloudFileTags): String? {
        val uri = song.contentUriString.toUri()
        val artwork = tags.artworkUrl?.let { downloadArtwork(it) }
        val tagged = embedTags(uri, tags, artwork)
        if (!tagged) return null
        if (uri.scheme == "content") {
            val values = metadataValues(tags)
            values.put(MediaStore.Audio.Media.DATE_MODIFIED, System.currentTimeMillis() / 1000L)
            context.contentResolver.update(uri, values, null, null)
        }
        val fileName = song.path.takeIf { it.isNotBlank() }?.let { File(it).name }
        if (!fileName.isNullOrBlank()) {
            rememberArtwork(fileName, tags.artworkUrl)
            markRepaired(fileName)
        }
        val mediaStoreId = song.id.toLongOrNull()
        val localArt = if (mediaStoreId != null && !song.path.isBlank()) {
            AlbumArtUtils.getAlbumArtUri(
                appContext = context,
                path = song.path,
                songId = mediaStoreId,
                forceRefresh = true,
            )
        } else {
            null
        }
        return localArt ?: tags.artworkUrl
    }

    fun rememberArtwork(song: Song, artworkUrl: String?) {
        val fileName = song.path.takeIf { it.isNotBlank() }?.let { File(it).name } ?: return
        rememberArtwork(fileName, artworkUrl)
    }

    private fun rememberArtwork(fileName: String, artworkUrl: String?) {
        val usableUrl = artworkUrl?.takeIf { it.isNotBlank() } ?: return
        artworkPreferences.edit().putString(fileName, usableUrl).apply()
    }

    private fun savedArtwork(fileName: String?): String? =
        fileName?.let { artworkPreferences.getString(it, null) }?.takeIf { it.isNotBlank() }

    private fun isRepaired(fileName: String): Boolean =
        repairedPreferences.getBoolean(fileName, false)

    private fun markRepaired(fileName: String) {
        repairedPreferences.edit().putBoolean(fileName, true).apply()
    }

    /** Local SoundCloud downloads as playable [Song]s (MediaStore first, file fallback). */
    fun listDownloadedSongs(): SoundCloudDownloadCatalog {
        val fromStore = queryMediaStoreDownloads()
        if (fromStore.songs.isNotEmpty() || fromStore.repairs.isNotEmpty()) return fromStore
        return listDownloadedFiles().map { fileToEntry(it) }.toCatalog()
    }

    fun listDownloadedFiles(): List<File> {
        val dir = downloadDirectory()
        if (!dir.isDirectory) return emptyList()
        return dir.listFiles()
            ?.filter { it.isFile && it.extension.lowercase() in AUDIO_EXTS }
            ?.sortedByDescending { it.lastModified() }
            .orEmpty()
    }

    private fun queryMediaStoreDownloads(): SoundCloudDownloadCatalog {
        val entries = ArrayList<DownloadEntry>()
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.ALBUM_ARTIST,
            MediaStore.Audio.Media.GENRE,
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
                val albumArtistCol = cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM_ARTIST)
                val genreCol = cursor.getColumnIndex(MediaStore.Audio.Media.GENRE)
                val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
                val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)
                val addedCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
                val modifiedCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_MODIFIED)
                val displayCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val path = cursor.getString(dataCol).orEmpty()
                    val displayName = cursor.getString(displayCol)
                    val (fileArtist, fileTitle) = splitDisplayName(displayName ?: File(path).name)
                    val rawTitle = cursor.getString(titleCol)
                    val rawArtist = cursor.getString(artistCol)
                    val rawAlbum = cursor.getString(albumCol)
                    val rawAlbumArtist = if (albumArtistCol >= 0) cursor.getString(albumArtistCol) else null
                    val rawGenre = if (genreCol >= 0) cursor.getString(genreCol) else null
                    val title = rawTitle?.takeUnless { it.isSoundCloudPlaceholder() || it == "<unknown>" }
                        ?: fileTitle
                    val artist = rawArtist?.takeUnless { it.isSoundCloudPlaceholder() } ?: fileArtist
                    val album = rawAlbum?.takeUnless { it.isSoundCloudPlaceholder() } ?: artist
                    val albumArtist = rawAlbumArtist?.takeUnless { it.isSoundCloudPlaceholder() } ?: artist
                    val genre = rawGenre?.takeUnless { it.isSoundCloudPlaceholder() }
                    val fileName = displayName ?: File(path).name
                    val albumArt = AlbumArtUtils.getAlbumArtUri(
                        appContext = context,
                        path = path,
                        songId = id,
                        forceRefresh = false,
                    ) ?: savedArtwork(fileName)
                    val song = Song(
                        id = id.toString(),
                        title = title,
                        artist = artist,
                        artistId = -1L,
                        artists = emptyList(),
                        album = album,
                        albumId = cursor.getLong(albumIdCol),
                        albumArtist = albumArtist,
                        path = path,
                        contentUriString = ContentUris.withAppendedId(
                            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                            id,
                        ).toString(),
                        albumArtUriString = albumArt,
                        duration = cursor.getLong(durationCol).coerceAtLeast(0L),
                        genre = genre,
                        dateAdded = cursor.getLong(addedCol),
                        dateModified = cursor.getLong(modifiedCol),
                        mimeType = cursor.getString(mimeCol),
                        bitrate = null,
                        sampleRate = null,
                    )
                    val needsRepair = !isRepaired(fileName) && (
                        rawArtist.isSoundCloudPlaceholder() ||
                            rawAlbum.isSoundCloudPlaceholder() ||
                            rawAlbumArtist.isSoundCloudPlaceholder() ||
                            rawGenre.isSoundCloudPlaceholder() ||
                            albumArt.isNullOrBlank() ||
                            albumArt.startsWith("http")
                        )
                    entries.add(DownloadEntry(song, needsRepair))
                }
            }
        } catch (_: SecurityException) {
            // Fall back to filesystem listing.
        } catch (_: Exception) {
            // Fall back to filesystem listing.
        }
        return entries.toCatalog()
    }

    private fun fileToEntry(file: File): DownloadEntry {
        val (fileArtist, fileTitle) = splitDisplayName(file.name)
        val id = "sc_dl_${file.absolutePath.hashCode().toUInt()}"
        val artwork = savedArtwork(file.name)
        val song = Song(
            id = id,
            title = fileTitle,
            artist = fileArtist,
            artistId = -1L,
            artists = emptyList(),
            album = fileArtist,
            albumId = -1L,
            albumArtist = fileArtist,
            path = file.absolutePath,
            contentUriString = file.toURI().toString(),
            albumArtUriString = artwork,
            duration = 0L,
            genre = null,
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
        return DownloadEntry(song, needsRepair = !isRepaired(file.name))
    }

    private fun List<DownloadEntry>.toCatalog(): SoundCloudDownloadCatalog {
        return SoundCloudDownloadCatalog(
            songs = map { it.song },
            repairs = filter { it.needsRepair }.map { it.song },
        )
    }

    private fun splitDisplayName(displayName: String): Pair<String, String> {
        val base = displayName.substringBeforeLast('.')
        val parts = base.split(" - ", limit = 2)
        return if (parts.size == 2) {
            parts[0].trim().ifBlank { "Unknown artist" } to parts[1].trim().ifBlank { base }
        } else {
            "Unknown artist" to base
        }
    }

    private fun contentValuesFor(
        fileName: String,
        mimeType: String?,
        tags: SoundCloudFileTags,
    ): ContentValues = metadataValues(tags).apply {
        put(MediaStore.Audio.Media.DISPLAY_NAME, fileName)
        put(MediaStore.Audio.Media.MIME_TYPE, mimeType ?: "audio/mpeg")
        put(MediaStore.Audio.Media.RELATIVE_PATH, "Music/PixelPlayer/SoundCloud")
    }

    private fun metadataValues(tags: SoundCloudFileTags): ContentValues = ContentValues().apply {
        put(MediaStore.Audio.Media.TITLE, tags.title)
        put(MediaStore.Audio.Media.ARTIST, tags.artist)
        put(MediaStore.Audio.Media.ALBUM, tags.album)
        put(MediaStore.Audio.Media.ALBUM_ARTIST, tags.albumArtist)
        if (tags.genre.isNullOrBlank()) {
            putNull(MediaStore.Audio.Media.GENRE)
        } else {
            put(MediaStore.Audio.Media.GENRE, tags.genre)
        }
    }

    private fun audioRequest(url: String): Request = Request.Builder()
        .url(url)
        .header("User-Agent", USER_AGENT)
        .header("Referer", "https://soundcloud.com/")
        .build()

    private fun downloadArtwork(url: String): Pair<ByteArray, String>? {
        return try {
            http.newCall(audioRequest(url)).execute().use { response ->
                if (!response.isSuccessful) return null
                val bytes = response.body?.bytes() ?: return null
                if (bytes.isEmpty() || bytes.size > MAX_ARTWORK_BYTES) return null
                val mime = response.header("Content-Type")
                    ?.substringBefore(';')
                    ?.trim()
                    ?.takeIf { it.startsWith("image/") }
                    ?: "image/jpeg"
                bytes to mime
            }
        } catch (t: Throwable) {
            Timber.w(t, "SoundCloud artwork download failed")
            null
        }
    }

    private fun embedTags(
        uri: Uri,
        tags: SoundCloudFileTags,
        artwork: Pair<ByteArray, String>?,
    ): Boolean {
        return try {
            openReadWrite(uri)?.use { fd ->
                val existing = TagLib.getMetadata(fd.dup().detachFd())
                val propertyMap = HashMap(existing?.propertyMap ?: emptyMap())
                propertyMap["TITLE"] = arrayOf(tags.title)
                propertyMap["ARTIST"] = arrayOf(tags.artist)
                propertyMap["ALBUM"] = arrayOf(tags.album)
                propertyMap["ALBUMARTIST"] = arrayOf(tags.albumArtist)
                if (tags.genre.isNullOrBlank()) {
                    propertyMap.remove("GENRE")
                } else {
                    propertyMap["GENRE"] = arrayOf(tags.genre)
                }
                val saved = TagLib.savePropertyMap(fd.dup().detachFd(), propertyMap)
                if (!saved) return false
                if (artwork != null) {
                    TagLib.savePictures(
                        fd.dup().detachFd(),
                        arrayOf(
                            Picture(
                                data = artwork.first,
                                description = "Front Cover",
                                pictureType = "Front Cover",
                                mimeType = artwork.second,
                            ),
                        ),
                    )
                }
                true
            } ?: false
        } catch (t: Throwable) {
            Timber.w(t, "SoundCloud tag embed failed for %s", uri)
            false
        }
    }

    private fun openReadWrite(uri: Uri): ParcelFileDescriptor? {
        return when (uri.scheme) {
            "file" -> {
                val path = uri.path ?: return null
                ParcelFileDescriptor.open(File(path), ParcelFileDescriptor.MODE_READ_WRITE)
            }
            else -> context.contentResolver.openFileDescriptor(uri, "rw")
        }
    }

    private data class DownloadEntry(val song: Song, val needsRepair: Boolean)

    companion object {
        private const val ARTWORK_PREFERENCES = "soundcloud_download_artwork"
        private const val REPAIRED_PREFERENCES = "soundcloud_download_tags_repaired"
        private const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:128.0) Gecko/20100101 Firefox/128.0"
        private const val MAX_ARTWORK_BYTES = 8 * 1024 * 1024
        private val AUDIO_EXTS = setOf("mp3", "m4a", "ogg", "aac", "wav", "flac")
        private val UNSAFE_FILENAME = Regex("""[\\/:*?"<>|]""")

        fun downloadDirectory(): File = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC),
            "PixelPlayer/SoundCloud",
        )
    }
}
