package com.theveloper.pixelplay.data.stream

import com.theveloper.pixelplay.data.preferences.UserPreferencesRepository
import com.theveloper.pixelplay.utils.splitArtistsByDelimiters
import org.json.JSONObject

/**
 * Shared data class for bulk sync operations across cloud music repositories.
 */
data class BulkSyncResult(
    val playlistCount: Int,
    val syncedSongCount: Int,
    val failedPlaylistCount: Int
)

/**
 * Shared utility functions for cloud music repositories.
 */
object CloudMusicUtils {

    /** Parse a JSON string of key-value pairs into a Map (used for cookie persistence). */
    fun jsonToMap(json: String): Map<String, String> {
        val obj = JSONObject(json)
        val result = mutableMapOf<String, String>()
        for (key in obj.keys()) {
            result[key] = obj.optString(key, "")
        }
        return result
    }

    /** Split a raw artist string using the same conservative defaults as local library sync. */
    fun parseArtistNames(rawArtist: String): List<String> {
        if (rawArtist.isBlank()) return listOf("Unknown Artist")
        val parsed = rawArtist.splitArtistsByDelimiters(
            delimiters = UserPreferencesRepository.DEFAULT_ARTIST_DELIMITERS,
            wordDelimiters = UserPreferencesRepository.DEFAULT_ARTIST_WORD_DELIMITERS
        )
        return if (parsed.isEmpty()) listOf("Unknown Artist") else parsed
    }
}
