package com.theveloper.pixelplay.data.stream

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

    /** Split a raw artist string like "A, B & C" into individual names. */
    fun parseArtistNames(rawArtist: String): List<String> {
        if (rawArtist.isBlank()) return listOf("Unknown Artist")
        val parsed = rawArtist.split(Regex("\\s*[,/&;+、]\\s*"))
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
        return if (parsed.isEmpty()) listOf("Unknown Artist") else parsed
    }
}
