package com.theveloper.pixelplay.presentation.viewmodel

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.provider.OpenableColumns
import com.theveloper.pixelplay.data.media.AudioMetadataReader
import com.theveloper.pixelplay.data.media.guessImageMimeType
import com.theveloper.pixelplay.data.media.imageExtensionFromMimeType
import com.theveloper.pixelplay.data.media.isValidImageData
import com.theveloper.pixelplay.data.media.resolveAudioFileExtension
import com.theveloper.pixelplay.data.model.Song
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import javax.inject.Inject

data class ExternalSongLoadResult(
    val song: Song,
    val relativePath: String?,
    val bucketId: Long?,
    val displayName: String?
)

class ExternalMediaStateHolder @Inject constructor(
    @ApplicationContext private val context: Context
) {

    suspend fun buildExternalQueue(
        result: ExternalSongLoadResult,
        originalUri: Uri
    ): List<Song> {
        val continuation = loadAdditionalSongsFromFolder(result, originalUri)
        if (continuation.isEmpty()) {
            return listOf(result.song)
        }

        val queue = mutableListOf(result.song)
        continuation.forEach { song ->
            if (queue.none { it.id == song.id }) {
                queue.add(song)
            }
        }

        return queue
    }

    private suspend fun loadAdditionalSongsFromFolder(
        reference: ExternalSongLoadResult,
        originalUri: Uri
    ): List<Song> = withContext(Dispatchers.IO) {
        val relativePath = reference.relativePath
        val bucketId = reference.bucketId
        if (relativePath.isNullOrEmpty() && bucketId == null) {
            return@withContext emptyList()
        }

        val selection: String
        val selectionArgs: Array<String>
        if (bucketId != null) {
            selection = "${MediaStore.Audio.Media.BUCKET_ID} = ?"
            selectionArgs = arrayOf(bucketId.toString())
        } else {
            selection = "${MediaStore.Audio.Media.RELATIVE_PATH} = ?"
            selectionArgs = arrayOf(relativePath!!)
        }

        val resolver = context.contentResolver
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.DISPLAY_NAME
        )

        val siblings = mutableListOf<Pair<Uri, String?>>()
        try {
            resolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                "LOWER(${MediaStore.Audio.Media.DISPLAY_NAME}) ASC"
            )?.use { cursor ->
                val idIndex = cursor.getColumnIndex(MediaStore.Audio.Media._ID)
                val nameIndex = cursor.getColumnIndex(MediaStore.Audio.Media.DISPLAY_NAME)
                if (idIndex != -1) {
                    while (cursor.moveToNext()) {
                        val mediaId = cursor.getLong(idIndex)
                        val mediaUri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, mediaId)
                        val siblingName = if (nameIndex != -1) cursor.getString(nameIndex) else null
                        siblings.add(mediaUri to siblingName)
                    }
                }
            }
        } catch (securityException: SecurityException) {
            Timber.w(securityException, "Unable to load sibling songs for uri: $originalUri")
            return@withContext emptyList()
        } catch (illegalArgumentException: IllegalArgumentException) {
            Timber.w(illegalArgumentException, "Invalid query while loading sibling songs for uri: $originalUri")
            return@withContext emptyList()
        }

        if (siblings.isEmpty()) return@withContext emptyList()

        val normalizedTargetUri = originalUri.toString()
        val normalizedDisplayName = reference.displayName?.lowercase()?.trim()

        val startIndex = siblings.indexOfFirst { (itemUri, displayName) ->
            itemUri == originalUri ||
                itemUri.toString() == normalizedTargetUri ||
                (normalizedDisplayName != null && displayName?.lowercase()?.trim() == normalizedDisplayName)
        }

        val candidates = if (startIndex != -1) {
            siblings.drop(startIndex + 1)
        } else {
            // Include everything except target if not found in list (fallback) or logic implies future only?
            // "drop startIndex + 1" implies queue continues AFTER current song.
            siblings.filterNot { (itemUri, displayName) ->
                itemUri == originalUri ||
                    itemUri.toString() == normalizedTargetUri ||
                    (normalizedDisplayName != null && displayName?.lowercase()?.trim() == normalizedDisplayName)
            }
        }

        if (candidates.isEmpty()) return@withContext emptyList()

        val resolved = mutableListOf<Song>()
        for ((candidateUri, _) in candidates) {
            val additional = buildExternalSongFromUri(candidateUri, captureFolderInfo = false)
            val song = additional?.song ?: continue
            if (song.id != reference.song.id) {
                resolved.add(song)
            }
        }

        resolved
    }

    suspend fun buildExternalSongFromUri(
        uri: Uri,
        captureFolderInfo: Boolean = true
    ): ExternalSongLoadResult? = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver

        var displayName: String? = null
        var relativePath: String? = null
        var bucketId: Long? = null
        var storeTitle: String? = null
        var storeArtist: String? = null
        var storeAlbum: String? = null
        var storeDuration: Long? = null
        var storeTrack: Int? = null
        var storeYear: Int? = null
        var storeDateAddedSeconds: Long? = null
        var storeDataPath: String? = null
        var storeMediaId: Long? = null

        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            OpenableColumns.DISPLAY_NAME,
            MediaStore.Audio.Media.RELATIVE_PATH,
            MediaStore.Audio.Media.BUCKET_ID,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.TRACK,
            MediaStore.Audio.Media.YEAR,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.DATA
        )

        try {
            resolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val mediaIdIndex = cursor.getColumnIndex(MediaStore.Audio.Media._ID)
                    if (mediaIdIndex != -1) storeMediaId = cursor.getLong(mediaIdIndex)

                    val displayNameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (displayNameIndex != -1) displayName = cursor.getString(displayNameIndex)

                    val relativePathIndex = cursor.getColumnIndex(MediaStore.Audio.Media.RELATIVE_PATH)
                    if (relativePathIndex != -1) relativePath = cursor.getString(relativePathIndex)

                    val bucketIdIndex = cursor.getColumnIndex(MediaStore.Audio.Media.BUCKET_ID)
                    if (bucketIdIndex != -1) bucketId = cursor.getLong(bucketIdIndex)

                    val durationIndex = cursor.getColumnIndex(MediaStore.Audio.Media.DURATION)
                    if (durationIndex != -1) storeDuration = cursor.getLong(durationIndex)

                    val titleIndex = cursor.getColumnIndex(MediaStore.Audio.Media.TITLE)
                    if (titleIndex != -1) storeTitle = cursor.getString(titleIndex)

                    val artistIndex = cursor.getColumnIndex(MediaStore.Audio.Media.ARTIST)
                    if (artistIndex != -1) storeArtist = cursor.getString(artistIndex)

                    val albumIndex = cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM)
                    if (albumIndex != -1) storeAlbum = cursor.getString(albumIndex)
                    
                    val trackIndex = cursor.getColumnIndex(MediaStore.Audio.Media.TRACK)
                    if (trackIndex != -1) storeTrack = cursor.getInt(trackIndex)
                    
                    val yearIndex = cursor.getColumnIndex(MediaStore.Audio.Media.YEAR)
                    if (yearIndex != -1) storeYear = cursor.getInt(yearIndex)
                    
                    val dateAddedIndex = cursor.getColumnIndex(MediaStore.Audio.Media.DATE_ADDED)
                    if (dateAddedIndex != -1) storeDateAddedSeconds = cursor.getLong(dateAddedIndex)

                    val dataPathIndex = cursor.getColumnIndex(MediaStore.Audio.Media.DATA)
                    if (dataPathIndex != -1) storeDataPath = cursor.getString(dataPathIndex)
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Error querying MediaStore for uri: $uri")
        }

        // Fallback or read from file metadata
        val metadata = AudioMetadataReader.read(context, uri) ?: return@withContext null

        // Try to persist artwork
        val albumArtUriString = metadata.artwork?.let { artwork ->
             if (isValidImageData(artwork.bytes)) {
                 persistExternalAlbumArt(uri, artwork.bytes, artwork.mimeType)
             } else null
        }

        val finalTitle = storeTitle ?: metadata.title ?: displayName ?: "Unknown Title"
        val finalArtist = storeArtist ?: metadata.artist ?: "Unknown Artist"
        val finalAlbum = storeAlbum ?: metadata.album ?: "Unknown Album"
        // Use metadata duration if store duration is missing or 0
        val finalDuration = storeDuration?.takeIf { it > 0 } ?: metadata.durationMs ?: 0L

        val mimeType = context.contentResolver.getType(uri) ?: "audio/*"
        val directFilePath = resolveDirectFilePath(uri, storeDataPath)
        val cachedPlaybackFile = if (directFilePath == null && uri.scheme == "content") {
            persistExternalAudioForPlayback(uri)
        } else {
            null
        }
        val playbackContentUriString = when {
            cachedPlaybackFile != null -> Uri.fromFile(cachedPlaybackFile).toString()
            directFilePath != null -> Uri.fromFile(File(directFilePath)).toString()
            else -> uri.toString()
        }
        val playbackPath = cachedPlaybackFile?.absolutePath
            ?: directFilePath
            ?: uri.toString()

        val mediaStoreSongId = storeMediaId ?: uri.mediaStoreAudioId()
        val songId = mediaStoreSongId?.toString() ?: "external:${uri}"
        
        val song = Song(
            id = songId, 
            title = finalTitle,
            artist = finalArtist,
            artistId = -1, // No DB ID
            album = finalAlbum,
            albumId = -1, // No DB ID
            albumArtist = metadata.albumArtist,
            path = playbackPath,
            contentUriString = playbackContentUriString,
            albumArtUriString = albumArtUriString,
            duration = finalDuration,
            genre = metadata.genre, // Metadata reader might provide genre
            trackNumber = storeTrack ?: metadata.trackNumber ?: 0,
            year = storeYear ?: metadata.year ?: 0,
            dateAdded = storeDateAddedSeconds ?: (System.currentTimeMillis() / 1000),
            mimeType = mimeType,
            bitrate = metadata.bitrate,
            sampleRate = metadata.sampleRate
        )
        
        ExternalSongLoadResult(
            song = song,
            relativePath = if (captureFolderInfo) relativePath else null,
            bucketId = if (captureFolderInfo) bucketId else null,
            displayName = displayName
        )
    }

    private fun Uri.mediaStoreAudioId(): Long? {
        val normalizedUri = toString().substringBefore('?').substringBefore('#')
        if (
            !normalizedUri.startsWith("content://media/", ignoreCase = true) ||
            !normalizedUri.contains("/audio/media/", ignoreCase = true)
        ) {
            return null
        }

        return normalizedUri.substringAfterLast('/').toLongOrNull()?.takeIf { it > 0L }
    }

    private fun resolveDirectFilePath(uri: Uri, storeDataPath: String?): String? {
        storeDataPath
            ?.takeIf { it.isNotBlank() }
            ?.let(::File)
            ?.takeIf { it.exists() && it.canRead() }
            ?.absolutePath
            ?.let { return it }

        if (uri.scheme == "file") {
            return uri.path
                ?.takeIf { it.isNotBlank() }
                ?.let(::File)
                ?.takeIf { it.exists() && it.canRead() }
                ?.absolutePath
        }

        return null
    }

    private fun persistExternalAudioForPlayback(uri: Uri): File? {
        return runCatching {
            val directory = File(context.cacheDir, "external_audio")
            if (!directory.exists()) {
                directory.mkdirs()
            }

            val fileNamePrefix = "audio_${uri.toString().hashCode()}."
            directory.listFiles { file ->
                file.name.startsWith(fileNamePrefix)
            }?.forEach { it.delete() }

            val extension = resolveAudioFileExtension(context, uri).trimStart('.').ifBlank { "mp3" }
            val file = File(directory, "$fileNamePrefix$extension")
            context.contentResolver.openInputStream(uri)?.use { input ->
                file.outputStream().use { output ->
                    input.copyTo(output)
                }
            } ?: return@runCatching null

            file.takeIf { it.length() > 0L }
        }.onFailure { throwable ->
            Timber.w(throwable, "Unable to persist external audio for playback uri: $uri")
        }.getOrNull()
    }

    private fun persistExternalAlbumArt(uri: Uri, data: ByteArray, mimeType: String? = null): String? {
        return runCatching {
            if (!isValidImageData(data)) {
                throw IllegalArgumentException("Invalid embedded album art for uri: $uri")
            }
            val directory = File(context.cacheDir, "external_artwork")
            if (!directory.exists()) {
                directory.mkdirs()
            }
            val resolvedMimeType = mimeType ?: guessImageMimeType(data)
            val extension = imageExtensionFromMimeType(resolvedMimeType) ?: "jpg"
            val fileNamePrefix = "art_${uri.toString().hashCode()}."
            directory.listFiles { file ->
                file.name.startsWith(fileNamePrefix)
            }?.forEach { it.delete() }
            val fileName = "$fileNamePrefix$extension"
            val file = File(directory, fileName)
            file.outputStream().use { output ->
                output.write(data)
            }
            Uri.fromFile(file).toString()
        }.onFailure { throwable ->
            Timber.w(throwable, "Unable to persist album art for external uri: $uri")
        }.getOrNull()
    }
}
