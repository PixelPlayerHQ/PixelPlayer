package com.theveloper.pixelplay.data.media

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.util.Log
import androidx.core.net.toUri
import com.kyant.taglib.Picture
import com.kyant.taglib.TagLib
import com.theveloper.pixelplay.data.database.MusicDao
import com.theveloper.pixelplay.data.database.TelegramDao // Added
import com.theveloper.pixelplay.data.database.TelegramSongEntity // Added
import kotlinx.coroutines.flow.first // Added
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.gagravarr.opus.OpusFile
import org.gagravarr.opus.OpusTags
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.Locale

private const val TAG = "SongMetadataEditor"
private const val METADATA_EDIT_TIMEOUT_MS = 30_000L

/**
 * Error types for metadata editing operations
 */
enum class MetadataEditError {
    FILE_NOT_FOUND,
    NO_WRITE_PERMISSION,
    INVALID_INPUT,
    UNSUPPORTED_FORMAT,
    TAGLIB_ERROR,
    TIMEOUT,
    FILE_CORRUPTED,
    IO_ERROR,
    UNKNOWN
}


class SongMetadataEditor(
    private val context: Context,
    private val musicDao: MusicDao,
    private val telegramDao: TelegramDao // Added
) {

    // File extensions that require VorbisJava (TagLib has issues with these via file descriptors)
    private val opusExtensions = setOf("opus", "ogg")

    /**
     * Maximum allowed length for metadata fields to prevent buffer overflows
     */
    private object MetadataLimits {
        const val MAX_TITLE_LENGTH = 500
        const val MAX_ARTIST_LENGTH = 500
        const val MAX_ALBUM_LENGTH = 500
        const val MAX_GENRE_LENGTH = 100
        const val MAX_LYRICS_LENGTH = 50_000
    }

    /**
     * Validates metadata input and returns error message if invalid
     */
    private fun validateMetadataInput(
        title: String,
        artist: String,
        album: String,
        genre: String,
        lyrics: String
    ): String? {
        if (title.isBlank()) return "Title cannot be empty"
        if (title.length > MetadataLimits.MAX_TITLE_LENGTH) return "Title too long"
        if (artist.length > MetadataLimits.MAX_ARTIST_LENGTH) return "Artist name too long"
        if (album.length > MetadataLimits.MAX_ALBUM_LENGTH) return "Album name too long"
        if (genre.length > MetadataLimits.MAX_GENRE_LENGTH) return "Genre too long"
        if (lyrics.length > MetadataLimits.MAX_LYRICS_LENGTH) return "Lyrics too long"
        return null
    }

    /**
     * Checks if the file can be written to
     */
    private fun checkFileWritePermission(filePath: String): Boolean {
        val file = File(filePath)
        if (!file.exists()) return false
        if (!file.canWrite()) return false
        // Also check parent directory for potential rename operations
        val parent = file.parentFile ?: return false
        return parent.canWrite()
    }

    fun editSongMetadata(
        songId: Long,
        newTitle: String,
        newArtist: String,
        newAlbum: String,
        newGenre: String,
        newLyrics: String,
        newTrackNumber: Int,
        coverArtUpdate: CoverArtUpdate? = null,
    ): SongMetadataEditResult {
        // Input validation first
        val validationError = validateMetadataInput(newTitle, newArtist, newAlbum, newGenre, newLyrics)
        if (validationError != null) {
            Timber.w("Metadata validation failed: $validationError")
            return SongMetadataEditResult(
                success = false,
                updatedAlbumArtUri = null,
                error = MetadataEditError.INVALID_INPUT,
                errorMessage = validationError
            )
        }

        return try {
            val trimmedLyrics = newLyrics.trim()
            val trimmedGenre = newGenre.trim()
            val normalizedGenre = trimmedGenre.takeIf { it.isNotBlank() }

            // 1. FIRST: Get file path (Handle both MediaStore and Telegram/Negative IDs)
            val isTelegramSong = songId < 0
            val filePath = if (isTelegramSong) {
                runBlocking { musicDao.getSongById(songId).first()?.filePath }
            } else {
                getFilePathFromMediaStore(songId)
            }

            if (filePath.isNullOrBlank() && !isTelegramSong) {
                Log.e(TAG, "Could not get file path for songId: $songId")
                return SongMetadataEditResult(
                    success = false,
                    updatedAlbumArtUri = null,
                    error = MetadataEditError.FILE_NOT_FOUND,
                    errorMessage = "Could not find file in media library"
                )
            }

            // Check write permissions before attempting edit
            if (!filePath.isNullOrBlank() && !checkFileWritePermission(filePath)) {
                Log.e(TAG, "No write permission for file: $filePath")
                return SongMetadataEditResult(
                    success = false,
                    updatedAlbumArtUri = null,
                    error = MetadataEditError.NO_WRITE_PERMISSION,
                    errorMessage = "Cannot write to this file. It may be on read-only storage or protected."
                )
            }

            // Get file extension to determine which library to use
            val finalFilePath = filePath ?: ""
            val extension = finalFilePath.substringAfterLast('.', "").lowercase(Locale.ROOT)
            val useVorbisJava = extension in opusExtensions

            // 2. Update the actual file with ALL metadata (if it exists)
            val fileExists = finalFilePath.isNotBlank() && File(finalFilePath).exists()
            
            val fileUpdateSuccess = if (!fileExists) {
                if (isTelegramSong) {
                     Log.w(TAG, "METADATA_EDIT: Telegram file not found (streaming?). Skipping file tags, updating DB only.")
                     true
                } else {
                     Log.e(TAG, "METADATA_EDIT: File does not exist: $finalFilePath")
                     false
                }
            } else if (useVorbisJava) {
                Log.e(TAG, "METADATA_EDIT: Opus/Ogg file detected - skipping file modification to prevent corruption")
                Log.e(TAG, "METADATA_EDIT: Will update DBs only for: $finalFilePath")
                true // Skip file modification, proceed to DB update
            } else {
                Log.e(TAG, "METADATA_EDIT: Using TagLib for $extension file: $finalFilePath")
                updateFileMetadataWithTagLib(
                    filePath = finalFilePath,
                    newTitle = newTitle,
                    newArtist = newArtist,
                    newAlbum = newAlbum,
                    newGenre = trimmedGenre,
                    newLyrics = trimmedLyrics,
                    newTrackNumber = newTrackNumber,
                    coverArtUpdate = coverArtUpdate
                )
            }

            if (!fileUpdateSuccess) {
                Log.e(TAG, "Failed to update file metadata for songId: $songId")
                return SongMetadataEditResult(
                    success = false,
                    updatedAlbumArtUri = null,
                    error = MetadataEditError.TAGLIB_ERROR,
                    errorMessage = "Failed to write metadata to file"
                )
            }

            // 3. Update MediaStore (Local) OR Telegram Database (Telegram)
            if (isTelegramSong) {
                // Update Telegram Database
                 runBlocking {
                    // Update the cached items so SyncWorker doesn't overwrite our changes
                     val songEntity = musicDao.getSongById(songId).first()
                     if (songEntity?.telegramChatId != null && songEntity.telegramFileId != null) {
                        val telegramId = "${songEntity.telegramChatId}_${songEntity.telegramFileId}"
                         // Currently we don't have a direct update method in TelegramDao,
                         // assuming we fetch, modify, insert (REPLACE)
                         val telegramSong = telegramDao.getSongsByIds(listOf(telegramId)).first().firstOrNull()
                         if (telegramSong != null) {
                             val updatedTelegramSong = telegramSong.copy(
                                 title = newTitle,
                                 artist = newArtist,
                                 // Telegram entity doesn't have album/genre/lyrics fields in current schema
                                 // but updating title/artist is the most important
                             )
                             telegramDao.insertSongs(listOf(updatedTelegramSong))
                             Timber.d("Updated TelegramDao for song: $telegramId")
                         }
                     }
                 }
            } else {
                // Update MediaStore to reflect the changes
                val mediaStoreSuccess = updateMediaStoreMetadata(
                    songId = songId,
                    title = newTitle,
                    artist = newArtist,
                    album = newAlbum,
                    genre = trimmedGenre,
                    trackNumber = newTrackNumber
                )
    
                if (!mediaStoreSuccess) {
                    Timber.w("MediaStore update failed, but file was updated for songId: $songId")
                    // Continue anyway since the file was updated
                }
            }

            // 3. Update local database and save cover art preview
            var storedCoverArtUri: String? = null
            runBlocking {
                musicDao.updateSongMetadata(
                    songId,
                    newTitle,
                    newArtist,
                    newAlbum,
                    normalizedGenre,
                    newTrackNumber
                )

                coverArtUpdate?.let { update ->
                    storedCoverArtUri = saveCoverArtPreview(songId, update)
                    storedCoverArtUri?.let { coverUri ->
                        musicDao.updateSongAlbumArt(songId, coverUri)
                    }
                }
            }

            // 4. Force media rescan with the known file path
            if (finalFilePath.isNotBlank()) {
                forceMediaRescan(finalFilePath)
            }

            Log.e(TAG, "METADATA_EDIT: Successfully updated metadata for songId: $songId")
            SongMetadataEditResult(success = true, updatedAlbumArtUri = storedCoverArtUri)

        } catch (e: SecurityException) {
            Timber.e(e, "Security exception editing metadata for songId: $songId")
            SongMetadataEditResult(
                success = false,
                updatedAlbumArtUri = null,
                error = MetadataEditError.NO_WRITE_PERMISSION,
                errorMessage = "Permission denied: ${e.localizedMessage}"
            )
        } catch (e: IOException) {
            Timber.e(e, "IO exception editing metadata for songId: $songId")
            SongMetadataEditResult(
                success = false,
                updatedAlbumArtUri = null,
                error = MetadataEditError.IO_ERROR,
                errorMessage = "Error accessing file: ${e.localizedMessage}"
            )
        } catch (e: OutOfMemoryError) {
            Timber.e(e, "OOM editing metadata for songId: $songId")
            SongMetadataEditResult(
                success = false,
                updatedAlbumArtUri = null,
                error = MetadataEditError.FILE_CORRUPTED,
                errorMessage = "File too large or corrupted"
            )
        } catch (e: Exception) {
            Timber.e(e, "Failed to update metadata for songId: $songId")
            // Determine error type from exception
            val errorType = when {
                e.message?.contains("corrupt", ignoreCase = true) == true -> MetadataEditError.FILE_CORRUPTED
                e.message?.contains("unsupported", ignoreCase = true) == true -> MetadataEditError.UNSUPPORTED_FORMAT
                else -> MetadataEditError.UNKNOWN
            }
            SongMetadataEditResult(
                success = false,
                updatedAlbumArtUri = null,
                error = errorType,
                errorMessage = e.localizedMessage ?: "Unknown error occurred"
            )
        }
    }

    /**
     * FLAC files with high sample rates (>96kHz) or bit depths (>24bit) can cause issues with TagLib.
     * This function detects such files and logs warnings.
     */
    private fun isProblematicFlacFile(filePath: String): FlacAnalysisResult {
        val extension = filePath.substringAfterLast('.', "").lowercase(Locale.ROOT)
        if (extension != "flac") {
            return FlacAnalysisResult.NotFlac
        }
        
        // Try to read FLAC header to detect sample rate and bit depth
        return try {
            val file = File(filePath)
            file.inputStream().use { inputStream ->
                val header = ByteArray(42)
                val bytesRead = inputStream.read(header)
                
                if (bytesRead < 42) {
                    return FlacAnalysisResult.NotFlac
                }
                
                // Check FLAC signature "fLaC"
                if (header[0].toInt().toChar() != 'f' ||
                    header[1].toInt().toChar() != 'L' ||
                    header[2].toInt().toChar() != 'a' ||
                    header[3].toInt().toChar() != 'C'
                ) {
                    return FlacAnalysisResult.NotFlac
                }
                
                // STREAMINFO starts at byte 8 (after 4 byte magic + 4 byte block header)
                // Sample rate is at bytes 18-20 (bits 0-19 of STREAMINFO)
                // Bit depth is in byte 20-21
                val sampleRate = ((header[18].toInt() and 0xFF) shl 12) or
                    ((header[19].toInt() and 0xFF) shl 4) or
                    ((header[20].toInt() and 0xF0) shr 4)
                
                val bitsPerSample = (((header[20].toInt() and 0x01) shl 4) or
                    ((header[21].toInt() and 0xF0) shr 4)) + 1
                
                Log.d(TAG, "FLAC analysis: sampleRate=$sampleRate, bitsPerSample=$bitsPerSample")
                
                // Consider problematic if sample rate > 96kHz or bit depth > 24
                val isProblematic = sampleRate > 96000 || bitsPerSample > 24
                
                if (isProblematic) {
                    Log.w(TAG, "FLAC file may be problematic: $filePath (${sampleRate}Hz, ${bitsPerSample}bit)")
                    FlacAnalysisResult.Problematic(sampleRate, bitsPerSample)
                } else {
                    FlacAnalysisResult.Safe(sampleRate, bitsPerSample)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not analyze FLAC file: $filePath", e)
            // If we can't analyze, assume it might be problematic
            FlacAnalysisResult.Unknown
        }
    }

    private sealed class FlacAnalysisResult {
        object NotFlac : FlacAnalysisResult()
        data class Safe(val sampleRate: Int, val bitsPerSample: Int) : FlacAnalysisResult()
        data class Problematic(val sampleRate: Int, val bitsPerSample: Int) : FlacAnalysisResult()
        object Unknown : FlacAnalysisResult()
    }

    private fun updateFileMetadataWithTagLib(
        filePath: String,
        newTitle: String,
        newArtist: String,
        newAlbum: String,
        newGenre: String,
        newLyrics: String,
        newTrackNumber: Int,
        coverArtUpdate: CoverArtUpdate? = null
    ): Boolean {
        // Check for problematic FLAC files first
        when (val flacResult = isProblematicFlacFile(filePath)) {
            is FlacAnalysisResult.Problematic -> {
                Log.w(TAG, "TAGLIB: Skipping file modification for high-resolution FLAC (${flacResult.sampleRate}Hz, ${flacResult.bitsPerSample}bit)")
                Log.w(TAG, "TAGLIB: High-res FLAC files may not work correctly with TagLib. Will update MediaStore only.")
                // Return true to indicate we should proceed with MediaStore-only update
                // The calling code will still update MediaStore and local DB
                return true
            }
            else -> { /* Continue with normal processing */ }
        }
        
        return try {
            val audioFile = File(filePath)
            if (!audioFile.exists()) {
                Log.e(TAG, "TAGLIB: Audio file does not exist: $filePath")
                return false
            }
            Log.e(TAG, "TAGLIB: Opening file: $filePath")

            // Open file with read/write permissions
            ParcelFileDescriptor.open(audioFile, ParcelFileDescriptor.MODE_READ_WRITE).use { fd ->
                // Get existing metadata or create empty map
                Log.e(TAG, "TAGLIB: Getting existing metadata...")
                val metadataFd = fd.dup()
                val existingMetadata = TagLib.getMetadata(metadataFd.detachFd())
                Log.e(TAG, "TAGLIB: Existing metadata: ${existingMetadata?.propertyMap?.keys}")
                val propertyMap = HashMap(existingMetadata?.propertyMap ?: emptyMap())

                // Update metadata fields
                propertyMap["TITLE"] = arrayOf(newTitle)
                propertyMap["ARTIST"] = arrayOf(newArtist)
                propertyMap["ALBUM"] = arrayOf(newAlbum)
                propertyMap.upsertOrRemove("GENRE", newGenre)
                propertyMap.upsertOrRemove("LYRICS", newLyrics)
                propertyMap["TRACKNUMBER"] = arrayOf(newTrackNumber.toString())
                propertyMap["ALBUMARTIST"] = arrayOf(newArtist)
                Log.e(TAG, "TAGLIB: Updated property map, saving...")

                // Save metadata
                val saveFd = fd.dup()
                val metadataSaved = TagLib.savePropertyMap(saveFd.detachFd(), propertyMap)
                Log.e(TAG, "TAGLIB: savePropertyMap result: $metadataSaved")
                if (!metadataSaved) {
                    Log.e(TAG, "TAGLIB: Failed to save metadata for file: $filePath")
                    return false
                }

                // Update cover art if provided
                coverArtUpdate?.let { update ->
                    val picture = Picture(
                        data = update.bytes,
                        description = "Front Cover",
                        pictureType = "Front Cover",
                        mimeType = update.mimeType
                    )
                    val pictureFd = fd.dup()
                    val coverSaved = TagLib.savePictures(pictureFd.detachFd(), arrayOf(picture))
                    if (!coverSaved) {
                        Log.w(TAG, "TAGLIB: Failed to save cover art, but metadata was saved")
                    } else {
                        Log.d(TAG, "TAGLIB: Successfully embedded cover art")
                    }
                }
            }

            // Force file system sync to ensure data is written to disk
            try {
                java.io.RandomAccessFile(audioFile, "rw").use { raf ->
                    raf.fd.sync()
                }
            } catch (e: Exception) {
                Log.w(TAG, "Could not sync file, changes should still be persisted", e)
            }

            Log.e(TAG, "TAGLIB: SUCCESS - Updated file metadata: ${audioFile.path}")
            true

        } catch (e: Exception) {
            Log.e(TAG, "TAGLIB ERROR: ${e.javaClass.simpleName}: ${e.message}")
            e.printStackTrace()
            false
        }
    }

    private fun updateFileMetadataWithVorbisJava(
        filePath: String,
        newTitle: String,
        newArtist: String,
        newAlbum: String,
        newGenre: String,
        newLyrics: String,
        newTrackNumber: Int
    ): Boolean {
        val audioFile = File(filePath)
        val originalExtension = audioFile.extension
        var tempFile: File? = null
        var backupFile: File? = null
        
        return try {
            if (!audioFile.exists()) {
                Log.e(TAG, "VORBISJAVA: Audio file does not exist: $filePath")
                return false
            }

            Log.e(TAG, "VORBISJAVA: Reading Opus file: $filePath")
            
            // Read existing file
            val opusFile = OpusFile(audioFile)
            val tags = opusFile.tags ?: OpusTags()
            
            Log.e(TAG, "VORBISJAVA: Existing tags: ${tags.allComments}")
            
            // Clear existing tags and set new ones
            tags.removeComments("TITLE")
            tags.removeComments("ARTIST")
            tags.removeComments("ALBUM")
            tags.removeComments("GENRE")
            tags.removeComments("LYRICS")
            tags.removeComments("TRACKNUMBER")
            tags.removeComments("ALBUMARTIST")
            
            // Add new values (only if not blank)
            if (newTitle.isNotBlank()) tags.addComment("TITLE", newTitle)
            if (newArtist.isNotBlank()) {
                tags.addComment("ARTIST", newArtist)
                tags.addComment("ALBUMARTIST", newArtist)
            }
            if (newAlbum.isNotBlank()) tags.addComment("ALBUM", newAlbum)
            if (newGenre.isNotBlank()) tags.addComment("GENRE", newGenre)
            if (newLyrics.isNotBlank()) tags.addComment("LYRICS", newLyrics)
            if (newTrackNumber > 0) tags.addComment("TRACKNUMBER", newTrackNumber.toString())
            
            Log.e(TAG, "VORBISJAVA: Updated tags: ${tags.allComments}")
            
            // Create temp file with same extension as original
            tempFile = File(audioFile.parentFile, "${audioFile.nameWithoutExtension}_temp.${originalExtension}")
            
            Log.e(TAG, "VORBISJAVA: Writing to temp file: ${tempFile.path}")
            FileOutputStream(tempFile).use { fos ->
                val newOpusFile = OpusFile(fos, opusFile.info, tags)
                
                // Copy audio packets
                var packet = opusFile.nextAudioPacket
                while (packet != null) {
                    newOpusFile.writeAudioData(packet)
                    packet = opusFile.nextAudioPacket
                }
                
                newOpusFile.close()
            }
            opusFile.close()
            
            // Verify temp file was created and has content
            if (!tempFile.exists() || tempFile.length() == 0L) {
                Log.e(TAG, "VORBISJAVA: Temp file creation failed or is empty")
                return false
            }
            Log.e(TAG, "VORBISJAVA: Temp file size: ${tempFile.length()} bytes, original: ${audioFile.length()} bytes")
            
            // Create backup of original file before replacing
            backupFile = File(audioFile.parentFile, "${audioFile.nameWithoutExtension}_backup.${originalExtension}")
            if (!audioFile.renameTo(backupFile)) {
                Log.e(TAG, "VORBISJAVA: Failed to create backup of original file")
                tempFile.delete()
                return false
            }
            Log.e(TAG, "VORBISJAVA: Created backup: ${backupFile.path}")
            
            // Rename temp file to original name
            if (!tempFile.renameTo(audioFile)) {
                Log.e(TAG, "VORBISJAVA: Failed to rename temp file to original")
                // Restore backup
                backupFile.renameTo(audioFile)
                return false
            }
            
            // Delete backup on success
            backupFile.delete()
            Log.e(TAG, "VORBISJAVA: SUCCESS - Updated file metadata: ${audioFile.path}")
            true

        } catch (e: Exception) {
            Log.e(TAG, "VORBISJAVA ERROR: ${e.javaClass.simpleName}: ${e.message}")
            e.printStackTrace()
            
            // Cleanup on error
            tempFile?.delete()
            // Try to restore backup if it exists
            if (backupFile?.exists() == true && !audioFile.exists()) {
                backupFile.renameTo(audioFile)
            }
            false
        }
    }

    private fun updateMediaStoreMetadata(
        songId: Long,
        title: String,
        artist: String,
        album: String,
        genre: String,
        trackNumber: Int
    ): Boolean {
        return try {
            val uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, songId)

            val values = ContentValues().apply {
                put(MediaStore.Audio.Media.TITLE, title)
                put(MediaStore.Audio.Media.ARTIST, artist)
                put(MediaStore.Audio.Media.ALBUM, album)
                put(MediaStore.Audio.Media.GENRE, genre)
                put(MediaStore.Audio.Media.TRACK, trackNumber)
                put(MediaStore.Audio.Media.DATE_MODIFIED, System.currentTimeMillis() / 1000)
                put(MediaStore.Audio.Media.ALBUM_ARTIST, artist)
            }

            val rowsUpdated = context.contentResolver.update(uri, values, null, null)
            val success = rowsUpdated > 0

            Timber.d("MediaStore update: $rowsUpdated row(s) affected")
            success

        } catch (e: Exception) {
            Timber.e(e, "Failed to update MediaStore for songId: $songId")
            false
        }
    }

    private fun forceMediaRescan(filePath: String) {
        try {
            val file = File(filePath)
            if (file.exists()) {
                Log.e(TAG, "RESCAN: Starting MediaScanner for: $filePath")
                // Use MediaScannerConnection to force rescan
                val latch = java.util.concurrent.CountDownLatch(1)
                MediaScannerConnection.scanFile(
                    context,
                    arrayOf(filePath),
                    null
                ) { path, uri ->
                    Log.e(TAG, "RESCAN: Completed for: $path, new URI: $uri")
                    latch.countDown()
                }
                // Wait for scan to complete (max 5 seconds)
                val completed = latch.await(5, java.util.concurrent.TimeUnit.SECONDS)
                if (!completed) {
                    Log.w(TAG, "RESCAN: MediaScanner timeout for: $filePath")
                }
            } else {
                Log.e(TAG, "RESCAN: File does not exist: $filePath")
            }
        } catch (e: Exception) {
            Log.e(TAG, "RESCAN ERROR: ${e.message}")
        }
    }

    private fun getFilePathFromMediaStore(songId: Long): String? {
        Log.e(TAG, "getFilePathFromMediaStore: Looking up songId: $songId")
        return try {
            context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.Audio.Media.DATA),
                "${MediaStore.Audio.Media._ID} = ?",
                arrayOf(songId.toString()),
                null
            )?.use { cursor ->
                Log.e(TAG, "getFilePathFromMediaStore: Cursor count: ${cursor.count}")
                if (cursor.moveToFirst()) {
                    val path = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA))
                    Log.e(TAG, "getFilePathFromMediaStore: Found path: $path")
                    path
                } else {
                    Log.e(TAG, "getFilePathFromMediaStore: No file found for songId: $songId")
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "getFilePathFromMediaStore: Error querying MediaStore: ${e.message}")
            null
        }
    }
    private fun saveCoverArtPreview(songId: Long, coverArtUpdate: CoverArtUpdate): String? {
        return try {
            val extension = imageExtensionFromMimeType(coverArtUpdate.mimeType) ?: "jpg"
            val directory = File(context.cacheDir, "").apply {
                if (!exists()) mkdirs()
            }

            // Clean up old cover art files for this song
            directory.listFiles { file ->
                file.name.startsWith("song_art_${songId}")
            }?.forEach { it.delete() }

            // Save new cover art
            val file = File(directory, "song_art_${songId}_${System.currentTimeMillis()}.$extension")
            FileOutputStream(file).use { outputStream ->
                outputStream.write(coverArtUpdate.bytes)
            }

            file.toUri().toString()
        } catch (e: Exception) {
            Timber.e(e, "Error saving cover art preview for songId: $songId")
            null
        }
    }

    private fun imageExtensionFromMimeType(mimeType: String): String? {
        return when (mimeType) {
            "image/jpeg", "image/jpg" -> "jpg"
            "image/png" -> "png"
            "image/webp" -> "webp"
            "image/gif" -> "gif"
            else -> null
        }
    }
}

private fun MutableMap<String, Array<String>>.upsertOrRemove(key: String, value: String) {
    if (value.isBlank()) {
        remove(key)
    } else {
        this[key] = arrayOf(value)
    }
}

// Data classes
data class SongMetadataEditResult(
    val success: Boolean,
    val updatedAlbumArtUri: String?,
    val error: MetadataEditError? = null,
    val errorMessage: String? = null
)

data class CoverArtUpdate(
    val bytes: ByteArray,
    val mimeType: String = "image/jpeg"
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as CoverArtUpdate

        if (!bytes.contentEquals(other.bytes)) return false
        if (mimeType != other.mimeType) return false

        return true
    }

    override fun hashCode(): Int {
        var result = bytes.contentHashCode()
        result = 31 * result + mimeType.hashCode()
        return result
    }
}
