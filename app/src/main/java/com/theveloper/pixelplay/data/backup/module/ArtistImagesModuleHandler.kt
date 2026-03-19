package com.theveloper.pixelplay.data.backup.module

import android.content.Context
import android.util.Base64
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.theveloper.pixelplay.data.backup.model.ArtistImageBackupEntry
import com.theveloper.pixelplay.data.backup.model.BackupSection
import com.theveloper.pixelplay.data.database.MusicDao
import com.theveloper.pixelplay.di.BackupGson
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ArtistImagesModuleHandler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val musicDao: MusicDao,
    @BackupGson private val gson: Gson
) : BackupModuleHandler {

    override val section = BackupSection.ARTIST_IMAGES

    override suspend fun export(): String = withContext(Dispatchers.IO) {
        val artists = musicDao.getAllArtistsListRaw()
        val entries = artists
            .mapNotNull { artist ->
                val imageUrl = artist.imageUrl?.takeIf { it.isNotBlank() }
                val customImageBase64 = artist.customImageUri
                    ?.takeIf { it.isNotBlank() }
                    ?.let { readFileAsBase64(it) }
                // Skip artists with neither a Deezer URL nor a custom image
                if (imageUrl == null && customImageBase64 == null) return@mapNotNull null
                ArtistImageBackupEntry(
                    artistName = artist.name,
                    imageUrl = imageUrl ?: "",
                    customImageBase64 = customImageBase64
                )
            }
        gson.toJson(entries)
    }

    override suspend fun countEntries(): Int = withContext(Dispatchers.IO) {
        musicDao.getAllArtistsListRaw().count {
            !it.imageUrl.isNullOrEmpty() || !it.customImageUri.isNullOrEmpty()
        }
    }

    override suspend fun snapshot(): String = export()

    override suspend fun restore(payload: String) = withContext(Dispatchers.IO) {
        val type = TypeToken.getParameterized(List::class.java, ArtistImageBackupEntry::class.java).type
        val entries: List<ArtistImageBackupEntry> = gson.fromJson(payload, type)
        entries.forEach { entry ->
            val artistId = musicDao.getArtistIdByName(entry.artistName) ?: return@forEach
            // Restore Deezer URL
            if (entry.imageUrl.isNotBlank()) {
                musicDao.updateArtistImageUrl(artistId, entry.imageUrl)
            }
            // Restore custom image file
            val customBase64 = entry.customImageBase64
            if (customBase64 != null) {
                try {
                    val bytes = Base64.decode(customBase64, Base64.NO_WRAP)
                    val file = File(context.filesDir, "artist_art_${artistId}.jpg")
                    file.writeBytes(bytes)
                    musicDao.updateArtistCustomImage(artistId, file.absolutePath)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to restore custom image for artist: ${entry.artistName}", e)
                }
            }
        }
    }

    override suspend fun rollback(snapshot: String) = restore(snapshot)

    private fun readFileAsBase64(path: String): String? {
        return try {
            val file = File(path)
            if (!file.exists() || file.length() == 0L) return null
            val bytes = file.readBytes()
            Base64.encodeToString(bytes, Base64.NO_WRAP)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read artist image: $path", e)
            null
        }
    }

    companion object {
        private const val TAG = "ArtistImagesHandler"
    }
}
