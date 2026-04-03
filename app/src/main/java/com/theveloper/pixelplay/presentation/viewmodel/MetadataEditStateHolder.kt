package com.theveloper.pixelplay.presentation.viewmodel

import android.content.Context
import android.net.Uri
import android.util.Log
import com.theveloper.pixelplay.data.media.CoverArtUpdate
import com.theveloper.pixelplay.data.media.ImageCacheManager
import com.theveloper.pixelplay.data.media.MetadataEditError
import com.theveloper.pixelplay.data.media.SongMetadataEditor
import com.theveloper.pixelplay.data.model.Lyrics
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.repository.MusicRepository
import com.theveloper.pixelplay.utils.FileDeletionUtils
import com.theveloper.pixelplay.utils.LyricsUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject

class MetadataEditStateHolder @Inject constructor(
    private val songMetadataEditor: SongMetadataEditor,
    private val musicRepository: MusicRepository,
    private val imageCacheManager: ImageCacheManager,
    private val themeStateHolder: ThemeStateHolder,
    @ApplicationContext private val context: Context
) {

    data class MetadataEditResult(
        val success: Boolean,
        val updatedSong: Song? = null,
        val updatedAlbumArtUri: String? = null,
        val parsedLyrics: Lyrics? = null,
        val error: MetadataEditError? = null,
        val errorMessage: String? = null
    ) {
        /**
         * Returns a user-friendly error message based on the error type
         */
        fun getUserFriendlyErrorMessage(): String {
            return when (error) {
                MetadataEditError.FILE_NOT_FOUND -> "The song file could not be found. It may have been moved or deleted."
                MetadataEditError.NO_WRITE_PERMISSION -> "Cannot edit this file. You may need to grant additional permissions or the file is on read-only storage."
                MetadataEditError.INVALID_INPUT -> errorMessage ?: "Invalid input provided."
                MetadataEditError.UNSUPPORTED_FORMAT -> "This file format is not supported for editing."
                MetadataEditError.TAGLIB_ERROR -> "Failed to write metadata to the file. The file may be corrupted."
                MetadataEditError.TIMEOUT -> "The operation took too long and was cancelled."
                MetadataEditError.FILE_CORRUPTED -> "The file appears to be corrupted or in an unsupported format."
                MetadataEditError.IO_ERROR -> "An error occurred while accessing the file. Please try again."
                MetadataEditError.UNKNOWN, null -> errorMessage ?: "An unknown error occurred while editing metadata."
            }
        }
    }

    suspend fun saveMetadata(
        song: Song,
        newTitle: String,
        newArtist: String,
        newAlbum: String,
        newGenre: String,
        newLyrics: String,
        newTrackNumber: Int,
        newDiscNumber: Int?,
        coverArtUpdate: CoverArtUpdate?
    ): MetadataEditResult = withContext(Dispatchers.IO) {
        
        Log.d("MetadataEditStateHolder", "Starting saveMetadata for: ${song.title}")

        // CRITICAL FIX: Preserve existing embedded artwork if the user didn't provide a new one.
        // Editing text metadata might strip the artwork if the underlying tagging library
        // overwrites the file structure. Explicitly re-saving the existing artwork prevents this.
        val finalCoverArtUpdate = if (coverArtUpdate == null) {
            val existingMetadata = try {
                 com.theveloper.pixelplay.data.media.AudioMetadataReader.read(java.io.File(song.path))
            } catch (e: Exception) {
                null
            }
            if (existingMetadata?.artwork != null) {
                Log.d("MetadataEditStateHolder", "Preserving existing embedded artwork")
                CoverArtUpdate(existingMetadata.artwork.bytes, existingMetadata.artwork.mimeType ?: "image/jpeg")
            } else {
                null
            }
        } else if (coverArtUpdate.isDeletion) {
            Log.d("MetadataEditStateHolder", "Artwork deletion requested, skipping preservation")
            coverArtUpdate
        } else {
            coverArtUpdate
        }

        val trimmedLyrics = newLyrics.trim()
        val normalizedLyrics = trimmedLyrics.takeIf { it.isNotBlank() }
        // We parse lyrics here just to ensure they are valid or to have them ready, 
        // essentially mirroring logic in ViewModel
        val parsedLyrics = normalizedLyrics?.let { LyricsUtils.parseLyrics(it) }
        val resolvedSongId = resolveSongIdForMetadataEdit(song)

        if (resolvedSongId == null) {
            Log.w("MetadataEditStateHolder", "Cannot edit metadata for non-numeric song id: ${song.id}")
            return@withContext MetadataEditResult(
                success = false,
                error = MetadataEditError.INVALID_INPUT,
                errorMessage = "This song source does not support metadata editing."
            )
        }

        val result = songMetadataEditor.editSongMetadata(
            newTitle = newTitle,
            newArtist = newArtist,
            newAlbum = newAlbum,
            newGenre = newGenre,
            newLyrics = trimmedLyrics,
            newTrackNumber = newTrackNumber,
            newDiscNumber = newDiscNumber,
            coverArtUpdate = finalCoverArtUpdate,
            songId = resolvedSongId,
        )

        Log.d("MetadataEditStateHolder", "Editor result: success=${result.success}, error=${result.error}")

        if (result.success) {
            val refreshedAlbumArtUri = if (coverArtUpdate?.isDeletion == true) {
                null
            } else {
                result.updatedAlbumArtUri ?: song.albumArtUriString
            }
            
            // Update Repository (Lyrics)
            if (normalizedLyrics != null) {
                musicRepository.updateLyrics(resolvedSongId, normalizedLyrics)
            } else {
                musicRepository.resetLyrics(resolvedSongId)
            }

            val updatedSong = song.copy(
                title = newTitle,
                artist = newArtist,
                album = newAlbum,
                genre = newGenre,
                lyrics = normalizedLyrics,
                trackNumber = newTrackNumber,
                discNumber = newDiscNumber,
                albumArtUriString = refreshedAlbumArtUri,
            )

            // CRITICAL: Fetch the authoritative song object from the repository (MediaStore/DB).
            // When metadata changes (especially album/artist), MediaStore might re-index the song
            // and assign it a NEW album ID, resulting in a NEW albumArtUri.
            // Using the 'updatedSong' copy above might retain a STALE albumArtUri.
            val freshSongFromRepo = try {
                musicRepository.getSong(song.id).first() ?: updatedSong
            } catch (e: Exception) {
                updatedSong
            }

            // Ensure we use the refreshed artwork URI we just generated/cleared.
            // The repository emission may be stale for a split second.
            val freshSong = freshSongFromRepo.copy(
                albumArtUriString = refreshedAlbumArtUri
            )

            // Force cache invalidation if album art might have changed
            val uriToInvalidate = if (coverArtUpdate?.isDeletion == true) song.albumArtUriString else refreshedAlbumArtUri
            if (uriToInvalidate != null) {
                // Invalidate Coil/Glide caches for the affected URI (old or new)
                imageCacheManager.invalidateCoverArtCaches(uriToInvalidate)
            }
            
            // Force regenerate palette
            themeStateHolder.forceRegenerateColorScheme(refreshedAlbumArtUri)

            MetadataEditResult(
                success = true,
                updatedSong = freshSong,
                updatedAlbumArtUri = freshSong.albumArtUriString,
                parsedLyrics = parsedLyrics
            )
        } else {
            Log.w("MetadataEditStateHolder", "Metadata edit failed: ${result.error} - ${result.errorMessage}")
            MetadataEditResult(
                success = false,
                error = result.error,
                errorMessage = result.errorMessage
            )
        }
    }

    suspend fun deleteSong(song: Song): Boolean = withContext(Dispatchers.IO) {
        val fileInfo = FileDeletionUtils.getFileInfo(song.path)
        if (fileInfo.exists && fileInfo.canWrite) {
            val success = FileDeletionUtils.deleteFile(context, song.path)
            if (success) {
                // Remove from DB happens in ViewModel call logic or should happen here?
                // VM's deleteFromDevice calls removeSong -> toggleFavorite(false) -> updates lists.
                // It does NOT explicitly call repository.deleteSong() because MediaStore/FileObserver handles it?
                // Or maybe explicit deletion IS needed but VM logic (Line 3687) says "removeSong(song)".
                // removeSong(3698) toggles favorites and updates _masterAllSongs. It implies memory update.
                // FileDeletionUtils deletes the physical file. The MediaScanner should eventually pick it up, 
                // but for immediate UI responsiveness, manual update is good.
                // Also, MusicRepository.deleteById(id) exists.
                // ViewModel did NOT call musicRepository.deleteById(). It relied on "removeSong" which is UI state only? 
                // Wait, removeSong updates UI state. Does it update DB?
                // Line 3698: toggleFavoriteSpecificSong(song, true)?? Wait.
                
                // Let's stick to returning success and letting ViewModel handle UI updates for now, 
                // or if we want to be thorough, we call repository delete.
                // But if ViewModel wasn't doing it, I won't add it to change behavior.
                true
            } else {
                false
            }
        } else {
            false
        }
    }

    private fun resolveSongIdForMetadataEdit(song: Song): Long? {
        song.id.toLongOrNull()?.let { return it }

        val uriCandidates = buildList {
            if (song.contentUriString.isNotBlank()) add(song.contentUriString)
            if (song.id.startsWith("external:")) add(song.id.removePrefix("external:"))
        }

        for (rawUri in uriCandidates) {
            val parsedUri = runCatching { Uri.parse(rawUri) }.getOrNull() ?: continue
            if (parsedUri.scheme != "content") continue

            parsedUri.lastPathSegment?.toLongOrNull()?.let { return it }
        }

        return null
    }
}
