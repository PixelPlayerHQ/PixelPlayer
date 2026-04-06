package com.theveloper.pixelplay.data.media

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.graphics.BitmapFactory
import android.media.MediaScannerConnection
import android.os.Build
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.net.toUri
import com.kyant.taglib.Picture
import com.kyant.taglib.TagLib
import com.theveloper.pixelplay.data.database.ArtistEntity
import com.theveloper.pixelplay.data.database.MusicDao
import com.theveloper.pixelplay.data.database.SongArtistCrossRef
import com.theveloper.pixelplay.data.database.TelegramDao // Added
import com.theveloper.pixelplay.data.database.TelegramSongEntity // Added
import com.theveloper.pixelplay.data.database.serializeArtistRefs
import com.theveloper.pixelplay.data.model.ArtistRef
import com.theveloper.pixelplay.data.preferences.UserPreferencesRepository
import com.theveloper.pixelplay.data.worker.collectArtistNames
import com.theveloper.pixelplay.utils.AlbumArtUtils
import com.theveloper.pixelplay.utils.LocalArtworkUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first // Added
import kotlinx.coroutines.withContext
import org.gagravarr.opus.OpusFile
import org.gagravarr.opus.OpusTags
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.images.AndroidArtwork
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.Locale

private const val TAG = "SongMetadataEditor"

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
    private val telegramDao: TelegramDao, // Added
    private val userPreferencesRepository: UserPreferencesRepository
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

    private suspend fun updateSongArtistMetadata(
        songId: Long,
        title: String,
        artist: String,
        album: String,
        genre: String?,
        trackNumber: Int,
        discNumber: Int?
    ) {
        val existingArtists = musicDao.getAllArtistsListRaw()
        val existingByNormalizedName =
            existingArtists.associateBy { it.name.trim().lowercase(Locale.ROOT) }.toMutableMap()
        var nextArtistId = (musicDao.getMaxArtistId() ?: 0L) + 1L

        val artistDelimiters = userPreferencesRepository.artistDelimitersFlow.first()
        val wordDelimiters = userPreferencesRepository.artistWordDelimitersFlow.first()
        val extractFromTitle = userPreferencesRepository.extractArtistsFromTitleFlow.first()

        val artistNames =
            collectArtistNames(
                rawArtistName = artist,
                title = title,
                artistDelimiters = artistDelimiters,
                wordDelimiters = wordDelimiters,
                extractFromTitle = extractFromTitle
            ).ifEmpty {
                listOf(artist.trim().ifEmpty { "Unknown Artist" })
            }

        val artistsToEnsure = mutableListOf<ArtistEntity>()
        val artistRefs = artistNames.mapIndexed { index, rawName ->
            val normalizedName = rawName.trim()
            val normalizedKey = normalizedName.lowercase(Locale.ROOT)
            val existingArtist = existingByNormalizedName[normalizedKey]
            val artistId =
                if (existingArtist != null) {
                    existingArtist.id
                } else {
                    val newId = nextArtistId++
                    val newArtist = ArtistEntity(
                        id = newId,
                        name = normalizedName,
                        trackCount = 0,
                        imageUrl = null,
                        customImageUri = null
                    )
                    existingByNormalizedName[normalizedKey] = newArtist
                    artistsToEnsure += newArtist
                    newId
                }

            ArtistRef(
                id = artistId,
                name = normalizedName,
                isPrimary = index == 0
            )
        }.filter { it.name.isNotEmpty() }

        val primaryArtistId = artistRefs.firstOrNull()?.id ?: -1L
        val artistsJson = serializeArtistRefs(artistRefs)
        val crossRefs = artistRefs.map { ref ->
            SongArtistCrossRef(
                songId = songId,
                artistId = ref.id,
                isPrimary = ref.isPrimary
            )
        }

        musicDao.updateSongMetadataAndArtistLinks(
            songId = songId,
            title = title,
            artist = artist,
            artistId = primaryArtistId,
            artistsJson = artistsJson,
            album = album,
            genre = genre,
            trackNumber = trackNumber,
            discNumber = discNumber,
            artistsToEnsure = artistsToEnsure,
            crossRefs = crossRefs
        )
    }

    @RequiresApi(Build.VERSION_CODES.R)
    suspend fun editSongMetadata(
        songId: Long,
        newTitle: String,
        newArtist: String,
        newAlbum: String,
        newGenre: String,
        newLyrics: String,
        newTrackNumber: Int,
        newDiscNumber: Int?,
        coverArtUpdate: CoverArtUpdate? = null,
    ): SongMetadataEditResult = withContext(Dispatchers.IO) {
        val validationError = validateMetadataInput(newTitle, newArtist, newAlbum, newGenre, newLyrics)
        if (validationError != null) {
            Timber.w("Metadata validation failed: $validationError")
            return@withContext SongMetadataEditResult(
                success = false,
                updatedAlbumArtUri = null,
                error = MetadataEditError.INVALID_INPUT,
                errorMessage = validationError
            )
        }

        try {
            val trimmedLyrics = newLyrics.trim()
            val trimmedGenre = newGenre.trim()
            val normalizedGenre = trimmedGenre.takeIf { it.isNotBlank() }

            val isTelegramSong = songId < 0
            val filePath = if (isTelegramSong) {
                musicDao.getSongById(songId).first()?.filePath
            } else {
                getFilePathFromMediaStore(songId)
            }

            if (filePath.isNullOrBlank() && !isTelegramSong) {
                Timber.tag(TAG).e("Could not get file path for songId: $songId")
                return@withContext SongMetadataEditResult(
                    success = false,
                    updatedAlbumArtUri = null,
                    error = MetadataEditError.FILE_NOT_FOUND,
                    errorMessage = "Could not find file in media library"
                )
            }

            if (!filePath.isNullOrBlank() && !checkFileWritePermission(filePath)) {
                Timber.tag(TAG).e("No write permission for file: $filePath")
                return@withContext SongMetadataEditResult(
                    success = false,
                    updatedAlbumArtUri = null,
                    error = MetadataEditError.NO_WRITE_PERMISSION,
                    errorMessage = "Cannot write to this file. It may be on read-only storage or protected."
                )
            }

            val finalFilePath = filePath ?: ""
            val extension = finalFilePath.substringAfterLast('.', "").lowercase(Locale.ROOT)
            val flacAnalysis = isProblematicFlacFile(finalFilePath)
            val isHighResFlac = flacAnalysis is FlacAnalysisResult.Problematic
            val useJAudioTaggerPrimary = extension in setOf("wav", "ogg") || isHighResFlac
            val fileExists = finalFilePath.isNotBlank() && File(finalFilePath).exists()

            val fileUpdateSuccess = if (!fileExists) {
                if (isTelegramSong) {
                    Timber.tag(TAG)
                        .w("METADATA_EDIT: Telegram file not found (streaming?). Skipping file tags, updating DB only.")
                    true
                } else {
                    Timber.tag(TAG).e("METADATA_EDIT: File does not exist: $finalFilePath")
                    false
                }
            } else if (useJAudioTaggerPrimary) {
                Timber.tag(TAG)
                    .d("METADATA_EDIT: Using JAudioTagger as primary for $extension file: $finalFilePath")
                updateFileMetadataWithJAudioTagger(
                    filePath = finalFilePath,
                    newTitle = newTitle,
                    newArtist = newArtist,
                    newAlbum = newAlbum,
                    newGenre = trimmedGenre,
                    newLyrics = trimmedLyrics,
                    newTrackNumber = newTrackNumber,
                    newDiscNumber = newDiscNumber,
                    coverArtUpdate = coverArtUpdate
                )
            } else {
                Timber.tag(TAG).d("METADATA_EDIT: Using TagLib for $extension file: $finalFilePath")
                val tagLibSuccess = updateFileMetadataWithTagLib(
                    filePath = finalFilePath,
                    newTitle = newTitle,
                    newArtist = newArtist,
                    newAlbum = newAlbum,
                    newGenre = trimmedGenre,
                    newLyrics = trimmedLyrics,
                    newTrackNumber = newTrackNumber,
                    newDiscNumber = newDiscNumber,
                    coverArtUpdate = coverArtUpdate
                )

                if (!tagLibSuccess) {
                    Timber.tag(TAG)
                        .w("METADATA_EDIT: TagLib failed for $extension, falling back to JAudioTagger")
                    updateFileMetadataWithJAudioTagger(
                        filePath = finalFilePath,
                        newTitle = newTitle,
                        newArtist = newArtist,
                        newAlbum = newAlbum,
                        newGenre = trimmedGenre,
                        newLyrics = trimmedLyrics,
                        newTrackNumber = newTrackNumber,
                        newDiscNumber = newDiscNumber,
                        coverArtUpdate = coverArtUpdate
                    )
                } else {
                    true
                }
            }

            if (!fileUpdateSuccess) {
                Timber.tag(TAG).e("Failed to update file metadata for songId: $songId")
                return@withContext SongMetadataEditResult(
                    success = false,
                    updatedAlbumArtUri = null,
                    error = MetadataEditError.TAGLIB_ERROR,
                    errorMessage = "Failed to write metadata to file"
                )
            }

            if (isTelegramSong) {
                val songEntity = musicDao.getSongById(songId).first()
                if (songEntity?.telegramChatId != null && songEntity.telegramFileId != null) {
                    val telegramId = "${songEntity.telegramChatId}_${songEntity.telegramFileId}"
                    val telegramSong = telegramDao.getSongsByIds(listOf(telegramId)).first().firstOrNull()
                    if (telegramSong != null) {
                        val updatedTelegramSong = telegramSong.copy(
                            title = newTitle,
                            artist = newArtist,
                        )
                        telegramDao.insertSongs(listOf(updatedTelegramSong))
                        Timber.d("Updated TelegramDao for song: $telegramId")
                    }
                }
            } else {
                val mediaStoreSuccess = updateMediaStoreMetadata(
                    songId = songId,
                    title = newTitle,
                    artist = newArtist,
                    album = newAlbum,
                    genre = trimmedGenre,
                    trackNumber = newTrackNumber,
                    discNumber = newDiscNumber
                )
                if (!mediaStoreSuccess) {
                    Timber.w("MediaStore update failed, but file was updated for songId: $songId")
                }
            }

            var storedCoverArtUri: String? = null
            updateSongArtistMetadata(
                songId = songId,
                title = newTitle,
                artist = newArtist,
                album = newAlbum,
                genre = normalizedGenre,
                trackNumber = newTrackNumber,
                discNumber = newDiscNumber
            )

            coverArtUpdate?.let {
                AlbumArtUtils.clearCacheForSong(context, songId)
                storedCoverArtUri = if (it.isDeletion) null else LocalArtworkUri.buildSongUriWithTimestamp(songId)
                musicDao.updateSongAlbumArt(songId, storedCoverArtUri)
            }

            if (finalFilePath.isNotBlank()) {
                forceMediaRescan(finalFilePath)
            }

            Timber.tag(TAG).e("METADATA_EDIT: Successfully updated metadata for songId: $songId")
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

                Timber.tag(TAG)
                    .d("FLAC analysis: sampleRate=$sampleRate, bitsPerSample=$bitsPerSample")
                
                // Consider problematic if sample rate > 96kHz or bit depth > 24
                val isProblematic = sampleRate > 96000 || bitsPerSample > 24
                
                if (isProblematic) {
                    Timber.tag(TAG)
                        .w("FLAC file may be problematic: $filePath (${sampleRate}Hz, ${bitsPerSample}bit)")
                    FlacAnalysisResult.Problematic(sampleRate, bitsPerSample)
                } else {
                    FlacAnalysisResult.Safe(sampleRate, bitsPerSample)
                }
            }
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "Could not analyze FLAC file: $filePath")
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
        newDiscNumber: Int?,
        coverArtUpdate: CoverArtUpdate? = null
    ): Boolean {
        // Check for problematic FLAC files first
        when (val flacResult = isProblematicFlacFile(filePath)) {
            is FlacAnalysisResult.Problematic -> {
                Timber.tag(TAG)
                    .w("TAGLIB: Skipping file modification for high-resolution FLAC (${flacResult.sampleRate}Hz, ${flacResult.bitsPerSample}bit)")
                Timber.tag(TAG)
                    .w("TAGLIB: High-res FLAC files may not work correctly with TagLib. Will update MediaStore only.")
                // Return true to indicate we should proceed with MediaStore-only update
                // The calling code will still update MediaStore and local DB
                return true
            }
            else -> { /* Continue with normal processing */ }
        }
        
        return try {
            val audioFile = File(filePath)
            if (!audioFile.exists()) {
                Timber.tag(TAG).e("TAGLIB: Audio file does not exist: $filePath")
                return false
            }
            Timber.tag(TAG).e("TAGLIB: Opening file: $filePath")

            // Open file with read/write permissions
            ParcelFileDescriptor.open(audioFile, ParcelFileDescriptor.MODE_READ_WRITE).use { fd ->
                // Get existing metadata or create empty map
                Timber.tag(TAG).e("TAGLIB: Getting existing metadata...")
                val metadataFd = fd.dup()
                val existingMetadata = TagLib.getMetadata(metadataFd.detachFd())
                Timber.tag(TAG)
                    .e("TAGLIB: Existing metadata: ${existingMetadata?.propertyMap?.keys}")
                val propertyMap = HashMap(existingMetadata?.propertyMap ?: emptyMap())

                // Update metadata fields
                propertyMap["TITLE"] = arrayOf(newTitle)
                propertyMap["ARTIST"] = arrayOf(newArtist)
                propertyMap["ALBUM"] = arrayOf(newAlbum)
                propertyMap.upsertOrRemove("GENRE", newGenre)
                propertyMap.upsertOrRemove("LYRICS", newLyrics)
                propertyMap["TRACKNUMBER"] = arrayOf(newTrackNumber.toString())
                if (newDiscNumber != null && newDiscNumber > 0) {
                    propertyMap["DISCNUMBER"] = arrayOf(newDiscNumber.toString())
                } else {
                    propertyMap.remove("DISCNUMBER")
                }
                propertyMap["ALBUMARTIST"] = arrayOf(newArtist)
                Timber.tag(TAG).e("TAGLIB: Updated property map, saving...")

                // Save metadata
                val saveFd = fd.dup()
                val metadataSaved = TagLib.savePropertyMap(saveFd.detachFd(), propertyMap)
                Timber.tag(TAG).e("TAGLIB: savePropertyMap result: $metadataSaved")
                if (!metadataSaved) {
                    Timber.tag(TAG).e("TAGLIB: Failed to save metadata for file: $filePath")
                    return false
                }

                // Update cover art if provided
                coverArtUpdate?.let { update ->
                    val pictures = if (update.isDeletion) {
                        emptyArray<Picture>()
                    } else if (update.bytes != null) {
                        arrayOf(
                            Picture(
                                data = update.bytes,
                                description = "Front Cover",
                                pictureType = "Front Cover",
                                mimeType = update.mimeType
                            )
                        )
                    } else {
                        null
                    }

                    pictures?.let {
                        val pictureFd = fd.dup()
                        val coverSaved = TagLib.savePictures(pictureFd.detachFd(), it)
                        if (!coverSaved) {
                            Timber.tag(TAG)
                                .w("TAGLIB: Failed to save cover art, but metadata was saved")
                        } else {
                            Timber.tag(TAG)
                                .d("TAGLIB: Successfully ${if (update.isDeletion) "removed" else "embedded"} cover art")
                        }
                    }
                }
            }

            // Force file system sync to ensure data is written to disk
            try {
                java.io.RandomAccessFile(audioFile, "rw").use { raf ->
                    raf.fd.sync()
                }
            } catch (e: Exception) {
                Timber.tag(TAG).w(e, "Could not sync file, changes should still be persisted")
            }

            Timber.tag(TAG).e("TAGLIB: SUCCESS - Updated file metadata: ${audioFile.path}")
            true

        } catch (e: Exception) {
            Timber.tag(TAG).e("TAGLIB ERROR: ${e.javaClass.simpleName}: ${e.message}")
            e.printStackTrace()
            false
        }
    }

    private fun updateFileMetadataWithJAudioTagger(
        filePath: String,
        newTitle: String,
        newArtist: String,
        newAlbum: String,
        newGenre: String,
        newLyrics: String,
        newTrackNumber: Int,
        newDiscNumber: Int?,
        coverArtUpdate: CoverArtUpdate? = null
    ): Boolean {
        val targetFile = File(filePath)
        
        return try {
            // Suppress JAudioTagger's verbose logging
            java.util.logging.Logger.getLogger("org.jaudiotagger").level = java.util.logging.Level.OFF
            
            val audioFile = AudioFileIO.read(targetFile)
            val tag = audioFile.tag ?: audioFile.createDefaultTag()
            
            // Update text fields
            tag.setField(FieldKey.TITLE, newTitle)
            tag.setField(FieldKey.ARTIST, newArtist)
            tag.setField(FieldKey.ALBUM, newAlbum)
            tag.setField(FieldKey.ALBUM_ARTIST, newArtist)
            
            if (newGenre.isNotBlank()) {
                tag.setField(FieldKey.GENRE, newGenre)
            } else {
                tag.deleteField(FieldKey.GENRE)
            }
            
            if (newLyrics.isNotBlank()) {
                tag.setField(FieldKey.LYRICS, newLyrics)
            } else {
                tag.deleteField(FieldKey.LYRICS)
            }
            
            tag.setField(FieldKey.TRACK, newTrackNumber.toString())
            if (newDiscNumber != null && newDiscNumber > 0) {
                tag.setField(FieldKey.DISC_NO, newDiscNumber.toString())
            } else {
                tag.deleteField(FieldKey.DISC_NO)
            }

            // Update cover art if provided
            coverArtUpdate?.let { update ->
                if (update.isDeletion) {
                    tag.deleteArtworkField()
                    Timber.tag(TAG).d("JAUDIOTAGGER: Removed cover art")
                } else if (update.bytes != null) {
                    try {
                        tag.deleteArtworkField()
                        val artwork = AndroidArtwork()
                        artwork.binaryData = update.bytes
                        artwork.mimeType = update.mimeType
                        artwork.pictureType = 3 // Standard value for "Front Cover"
                        
                        // Extract dimensions using Android native BitmapFactory to avoid ImageIO crash
                        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        BitmapFactory.decodeByteArray(update.bytes, 0, update.bytes.size, options)
                        artwork.width = options.outWidth
                        artwork.height = options.outHeight
                        
                        tag.setField(artwork)
                        Timber.tag(TAG)
                            .d("JAUDIOTAGGER: Embedded new cover art (${update.mimeType}, ${options.outWidth}x${options.outHeight})")
                    } catch (e: Exception) {
                        Timber.tag(TAG).e(e, "JAUDIOTAGGER: Failed to create artwork from bytes")
                    }
                } else {
                    Timber.tag(TAG).w("JAUDIOTAGGER: Ignoring invalid CoverArtUpdate with no bytes and no deletion flag")
                }
            }

            audioFile.commit()
            Timber.tag(TAG).d("JAUDIOTAGGER: SUCCESS - Updated file metadata: $filePath")
            true
        } catch (e: Exception) {
            Timber.tag(TAG).e("JAUDIOTAGGER ERROR: ${e.javaClass.simpleName}: ${e.message}")
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
        newTrackNumber: Int,
        newDiscNumber: Int?
    ): Boolean {
        val audioFile = File(filePath)
        val originalExtension = audioFile.extension
        var tempFile: File? = null
        var backupFile: File? = null
        
        return try {
            if (!audioFile.exists()) {
                Timber.tag(TAG).e("VORBISJAVA: Audio file does not exist: $filePath")
                return false
            }

            Timber.tag(TAG).e("VORBISJAVA: Reading Opus file: $filePath")
            
            // Read existing file
            val opusFile = OpusFile(audioFile)
            val tags = opusFile.tags ?: OpusTags()

            Timber.tag(TAG).e("VORBISJAVA: Existing tags: ${tags.allComments}")
            
            // Clear existing tags and set new ones
            tags.removeComments("TITLE")
            tags.removeComments("ARTIST")
            tags.removeComments("ALBUM")
            tags.removeComments("GENRE")
            tags.removeComments("LYRICS")
            tags.removeComments("TRACKNUMBER")
            tags.removeComments("DISCNUMBER")
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
            if (newDiscNumber != null && newDiscNumber > 0) tags.addComment("DISCNUMBER", newDiscNumber.toString())

            Timber.tag(TAG).e("VORBISJAVA: Updated tags: ${tags.allComments}")
            
            // Create temp file with same extension as original
            tempFile = File(audioFile.parentFile, "${audioFile.nameWithoutExtension}_temp.${originalExtension}")
            
            Timber.tag(TAG).e("VORBISJAVA: Writing to temp file: ${tempFile.path}")
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
                Timber.tag(TAG).e("VORBISJAVA: Temp file creation failed or is empty")
                return false
            }
            Timber.tag(TAG)
                .e("VORBISJAVA: Temp file size: ${tempFile.length()} bytes, original: ${audioFile.length()} bytes")
            
            // Create backup of original file before replacing
            backupFile = File(audioFile.parentFile, "${audioFile.nameWithoutExtension}_backup.${originalExtension}")
            if (!audioFile.renameTo(backupFile)) {
                Timber.tag(TAG).e("VORBISJAVA: Failed to create backup of original file")
                tempFile.delete()
                return false
            }
            Timber.tag(TAG).e("VORBISJAVA: Created backup: ${backupFile.path}")
            
            // Rename temp file to original name
            if (!tempFile.renameTo(audioFile)) {
                Timber.tag(TAG).e("VORBISJAVA: Failed to rename temp file to original")
                // Restore backup
                backupFile.renameTo(audioFile)
                return false
            }
            
            // Delete backup on success
            backupFile.delete()
            Timber.tag(TAG).e("VORBISJAVA: SUCCESS - Updated file metadata: ${audioFile.path}")
            true

        } catch (e: Exception) {
            Timber.tag(TAG).e("VORBISJAVA ERROR: ${e.javaClass.simpleName}: ${e.message}")
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

    @RequiresApi(Build.VERSION_CODES.R)
    private fun updateMediaStoreMetadata(
        songId: Long,
        title: String,
        artist: String,
        album: String,
        genre: String,
        trackNumber: Int,
        discNumber: Int?
    ): Boolean {
        return try {
            val uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, songId)

            val values = ContentValues().apply {
                put(MediaStore.Audio.Media.TITLE, title)
                put(MediaStore.Audio.Media.ARTIST, artist)
                put(MediaStore.Audio.Media.ALBUM, album)
                put(MediaStore.Audio.Media.GENRE, genre)
                val encodedTrack = ((discNumber ?: 0) * 1000) + trackNumber
                put(MediaStore.Audio.Media.TRACK, encodedTrack)
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
                Timber.tag(TAG).e("RESCAN: Starting MediaScanner for: $filePath")
                MediaScannerConnection.scanFile(
                    context,
                    arrayOf(filePath),
                    null
                ) { path, uri ->
                    Timber.tag(TAG).e("RESCAN: Completed for: $path, new URI: $uri")
                }
            } else {
                Timber.tag(TAG).e("RESCAN: File does not exist: $filePath")
            }
        } catch (e: Exception) {
            Timber.tag(TAG).e("RESCAN ERROR: ${e.message}")
        }
    }

    private fun getFilePathFromMediaStore(songId: Long): String? {
        Timber.tag(TAG).e("getFilePathFromMediaStore: Looking up songId: $songId")
        return try {
            context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.Audio.Media.DATA),
                "${MediaStore.Audio.Media._ID} = ?",
                arrayOf(songId.toString()),
                null
            )?.use { cursor ->
                Timber.tag(TAG).e("getFilePathFromMediaStore: Cursor count: ${cursor.count}")
                if (cursor.moveToFirst()) {
                    val path = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA))
                    Timber.tag(TAG).e("getFilePathFromMediaStore: Found path: $path")
                    path
                } else {
                    Timber.tag(TAG)
                        .e("getFilePathFromMediaStore: No file found for songId: $songId")
                    null
                }
            }
        } catch (e: Exception) {
            Timber.tag(TAG).e("getFilePathFromMediaStore: Error querying MediaStore: ${e.message}")
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

private fun MutableMap<String, Array<String>>.upsertOrRemove(key: String, value: String?) {
    if (value.isNullOrBlank()) {
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
    val bytes: ByteArray? = null,
    val mimeType: String = "image/jpeg",
    val isDeletion: Boolean = false
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
