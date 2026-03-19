package com.theveloper.pixelplay.data.network.navidrome

import com.theveloper.pixelplay.data.navidrome.model.NavidromeAlbum
import com.theveloper.pixelplay.data.navidrome.model.NavidromeArtist
import com.theveloper.pixelplay.data.navidrome.model.NavidromeMusicFolder
import com.theveloper.pixelplay.data.navidrome.model.NavidromePlaylist
import com.theveloper.pixelplay.data.navidrome.model.NavidromeSong
import org.json.JSONObject
import timber.log.Timber

/**
 * Parser for Subsonic API JSON responses.
 * Converts JSON objects to Navidrome data models.
 */
object NavidromeResponseParser {

    private const val TAG = "NavidromeParser"

    // ─── Music Folder Parsing ────────────────────────────────────────────

    /**
     * Parse a music folder from JSON.
     */
    fun parseMusicFolder(json: JSONObject): NavidromeMusicFolder {
        return NavidromeMusicFolder(
            id = json.optString("id", ""),
            name = json.optString("name", "Unknown Folder")
        )
    }

    // ─── Artist Parsing ──────────────────────────────────────────────────

    /**
     * Parse an artist from JSON (ArtistID3 format).
     */
    fun parseArtist(json: JSONObject): NavidromeArtist {
        return NavidromeArtist(
            id = json.optString("id", ""),
            name = json.optString("name", "Unknown Artist"),
            coverArt = json.optString("coverArt").takeIf { it.isNotEmpty() },
            albumCount = json.optInt("albumCount", 0),
            artistImageUrl = json.optString("artistImageUrl").takeIf { it.isNotEmpty() }
        )
    }

    /**
     * Parse a list of artists from JSON array.
     */
    fun parseArtists(jsonArray: List<JSONObject>): List<NavidromeArtist> {
        return jsonArray.map { parseArtist(it) }
    }

    // ─── Album Parsing ───────────────────────────────────────────────────

    /**
     * Parse an album from JSON (AlbumID3 format).
     */
    fun parseAlbum(json: JSONObject): NavidromeAlbum {
        return NavidromeAlbum(
            id = json.optString("id", ""),
            name = json.optString("name", "Unknown Album"),
            artist = json.optString("artist", "Unknown Artist"),
            artistId = json.optString("artistId").takeIf { it.isNotEmpty() },
            coverArt = json.optString("coverArt").takeIf { it.isNotEmpty() },
            songCount = json.optInt("songCount", 0),
            duration = json.optLong("duration", 0L) * 1000L, // Convert seconds to milliseconds
            playCount = json.optInt("playCount", 0),
            year = json.optInt("year", 0),
            genre = json.optString("genre").takeIf { it.isNotEmpty() }
        )
    }

    /**
     * Parse a list of albums from JSON array.
     */
    fun parseAlbums(jsonArray: List<JSONObject>): List<NavidromeAlbum> {
        return jsonArray.map { parseAlbum(it) }
    }

    // ─── Song Parsing ────────────────────────────────────────────────────

    /**
     * Parse a song from JSON (Child format from Subsonic API).
     */
    fun parseSong(json: JSONObject): NavidromeSong {
        return NavidromeSong(
            id = json.optString("id", ""),
            title = json.optString("title", json.optString("name", "Unknown Title")),
            artist = json.optString("artist", "Unknown Artist"),
            artistId = json.optString("artistId").takeIf { it.isNotEmpty() },
            album = json.optString("album", "Unknown Album"),
            albumId = json.optString("albumId").takeIf { it.isNotEmpty() },
            coverArt = json.optString("coverArt").takeIf { it.isNotEmpty() },
            duration = json.optLong("duration", 0L) * 1000L, // Convert seconds to milliseconds
            trackNumber = json.optInt("track", 0),
            discNumber = json.optInt("discNumber", 0),
            year = json.optInt("year", 0),
            genre = json.optString("genre").takeIf { it.isNotEmpty() },
            bitRate = json.optInt("bitRate", 0).takeIf { it > 0 },
            contentType = json.optString("contentType").takeIf { it.isNotEmpty() },
            suffix = json.optString("suffix").takeIf { it.isNotEmpty() },
            path = json.optString("path", ""),
            size = json.optLong("size", 0).takeIf { it > 0 },
            playCount = json.optInt("playCount", 0)
        )
    }

    /**
     * Parse a list of songs from JSON array.
     */
    fun parseSongs(jsonArray: List<JSONObject>): List<NavidromeSong> {
        return jsonArray.map { parseSong(it) }
    }

    /**
     * Parse songs from an album response.
     */
    fun parseSongsFromAlbumResponse(response: JSONObject): List<NavidromeSong> {
        val album = response.optJSONObject("album") ?: return emptyList()
        val songs = album.optJSONArray("song") ?: return emptyList()

        return (0 until songs.length()).mapNotNull { index ->
            try {
                parseSong(songs.getJSONObject(index))
            } catch (e: Exception) {
                Timber.w(e, "$TAG: Failed to parse song at index $index")
                null
            }
        }
    }

    // ─── Playlist Parsing ────────────────────────────────────────────────

    /**
     * Parse a playlist from JSON.
     */
    fun parsePlaylist(json: JSONObject): NavidromePlaylist {
        return NavidromePlaylist(
            id = json.optString("id", ""),
            name = json.optString("name", json.optString("title", "Unknown Playlist")),
            comment = json.optString("comment").takeIf { it.isNotEmpty() },
            owner = json.optString("owner").takeIf { it.isNotEmpty() },
            songCount = json.optInt("songCount", json.optInt("entryCount", 0)),
            duration = json.optLong("duration", 0L) * 1000L, // Convert seconds to milliseconds
            coverArt = json.optString("coverArt").takeIf { it.isNotEmpty() },
            public = json.optBoolean("public", false),
            created = parseTimestamp(json.optString("created")),
            changed = parseTimestamp(json.optString("changed"))
        )
    }

    /**
     * Parse a list of playlists from JSON array.
     */
    fun parsePlaylists(jsonArray: List<JSONObject>): List<NavidromePlaylist> {
        return jsonArray.map { parsePlaylist(it) }
    }

    /**
     * Parse songs from a playlist response.
     */
    fun parseSongsFromPlaylistResponse(response: JSONObject): Pair<NavidromePlaylist?, List<NavidromeSong>> {
        val playlistJson = response.optJSONObject("playlist")
        val playlist = playlistJson?.let { parsePlaylist(it) }

        val entries = playlistJson?.optJSONArray("entry") ?: return Pair(playlist, emptyList())
        val songs = (0 until entries.length()).mapNotNull { index ->
            try {
                parseSong(entries.getJSONObject(index))
            } catch (e: Exception) {
                Timber.w(e, "$TAG: Failed to parse playlist entry at index $index")
                null
            }
        }

        return Pair(playlist, songs)
    }

    // ─── Search Result Parsing ───────────────────────────────────────────

    /**
     * Parse search results from search3 response.
     */
    fun parseSearchResults(response: JSONObject): SearchResults {
        val searchResult = response.optJSONObject("searchResult3") ?: return SearchResults()

        val artists = searchResult.optJSONArray("artist")?.let { array ->
            (0 until array.length()).mapNotNull { index ->
                try {
                    parseArtist(array.getJSONObject(index))
                } catch (e: Exception) {
                    Timber.w(e, "$TAG: Failed to parse search artist at index $index")
                    null
                }
            }
        } ?: emptyList()

        val albums = searchResult.optJSONArray("album")?.let { array ->
            (0 until array.length()).mapNotNull { index ->
                try {
                    parseAlbum(array.getJSONObject(index))
                } catch (e: Exception) {
                    Timber.w(e, "$TAG: Failed to parse search album at index $index")
                    null
                }
            }
        } ?: emptyList()

        val songs = searchResult.optJSONArray("song")?.let { array ->
            (0 until array.length()).mapNotNull { index ->
                try {
                    parseSong(array.getJSONObject(index))
                } catch (e: Exception) {
                    Timber.w(e, "$TAG: Failed to parse search song at index $index")
                    null
                }
            }
        } ?: emptyList()

        return SearchResults(artists, albums, songs)
    }

    // ─── Utility Methods ─────────────────────────────────────────────────

    /**
     * Parse ISO 8601 timestamp string to epoch milliseconds.
     */
    private fun parseTimestamp(timestamp: String?): Long {
        if (timestamp.isNullOrBlank()) return 0L
        return try {
            // Subsonic API returns ISO 8601 format: "2023-01-15T10:30:00"
            // Try parsing as ISO 8601
            java.time.OffsetDateTime.parse(timestamp).toInstant().toEpochMilli()
        } catch (e: Exception) {
            try {
                // Fallback: try without timezone
                java.time.LocalDateTime.parse(timestamp)
                    .atZone(java.time.ZoneId.systemDefault())
                    .toInstant()
                    .toEpochMilli()
            } catch (e2: Exception) {
                Timber.w(e2, "$TAG: Failed to parse timestamp: $timestamp")
                0L
            }
        }
    }
}

/**
 * Container for search results.
 */
data class SearchResults(
    val artists: List<NavidromeArtist> = emptyList(),
    val albums: List<NavidromeAlbum> = emptyList(),
    val songs: List<NavidromeSong> = emptyList()
)
