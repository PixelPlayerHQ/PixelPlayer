package com.theveloper.pixelplay.data.network.jellyfin

import com.theveloper.pixelplay.data.jellyfin.model.JellyfinAlbum
import com.theveloper.pixelplay.data.jellyfin.model.JellyfinArtist
import com.theveloper.pixelplay.data.jellyfin.model.JellyfinPlaylist
import com.theveloper.pixelplay.data.jellyfin.model.JellyfinSong
import org.json.JSONObject
import timber.log.Timber

/**
 * Parser for Jellyfin API JSON responses.
 * Converts JSON objects to Jellyfin data models.
 */
object JellyfinResponseParser {

    private const val TAG = "JellyfinParser"

    fun parseSong(json: JSONObject): JellyfinSong {
        val artistNames = buildList {
            json.optJSONArray("Artists")?.let { arr ->
                for (i in 0 until arr.length()) {
                    arr.optString(i)?.takeIf { it.isNotBlank() }?.let { add(it) }
                }
            }
        }
        val artist = artistNames.joinToString(", ").ifBlank {
            json.optString("AlbumArtist", "Unknown Artist")
        }

        val artistIds = buildList {
            json.optJSONArray("ArtistItems")?.let { arr ->
                for (i in 0 until arr.length()) {
                    arr.optJSONObject(i)?.optString("Id")?.let { add(it) }
                }
            }
        }

        val genres = buildList {
            json.optJSONArray("Genres")?.let { arr ->
                for (i in 0 until arr.length()) {
                    arr.optString(i)?.let { add(it) }
                }
            }
        }

        val mediaSources = json.optJSONArray("MediaSources")
        val firstSource = mediaSources?.optJSONObject(0)

        return JellyfinSong(
            id = json.optString("Id", ""),
            title = json.optString("Name", "Unknown Title"),
            artist = artist,
            artistId = artistIds.firstOrNull(),
            album = json.optString("Album", "Unknown Album"),
            albumId = json.optString("AlbumId").takeIf { it.isNotBlank() },
            duration = (json.optLong("RunTimeTicks", 0L) / 10_000), // Ticks to milliseconds
            trackNumber = json.optInt("IndexNumber", 0),
            discNumber = json.optInt("ParentIndexNumber", 0),
            year = json.optInt("ProductionYear", 0),
            genre = genres.firstOrNull(),
            bitRate = firstSource?.optInt("Bitrate")?.let { it / 1000 }, // bps to kbps
            contentType = firstSource?.optString("Container")?.let { containerToMimeType(it) },
            path = firstSource?.optString("Path", "") ?: json.optString("Path", ""),
            size = firstSource?.optLong("Size"),
            playCount = json.optJSONObject("UserData")?.optInt("PlayCount", 0) ?: 0
        )
    }

    fun parseSongs(jsonArray: List<JSONObject>): List<JellyfinSong> {
        return jsonArray.mapNotNull { json ->
            try {
                parseSong(json)
            } catch (e: Exception) {
                Timber.w(e, "$TAG: Failed to parse song")
                null
            }
        }
    }

    fun parseAlbum(json: JSONObject): JellyfinAlbum {
        val genres = buildList {
            json.optJSONArray("Genres")?.let { arr ->
                for (i in 0 until arr.length()) {
                    arr.optString(i)?.let { add(it) }
                }
            }
        }

        return JellyfinAlbum(
            id = json.optString("Id", ""),
            name = json.optString("Name", "Unknown Album"),
            artist = json.optString("AlbumArtist", "Unknown Artist"),
            artistId = json.optJSONArray("ArtistItems")?.optJSONObject(0)?.optString("Id"),
            songCount = json.optInt("ChildCount", json.optInt("SongCount", 0)),
            duration = (json.optLong("RunTimeTicks", 0L) / 10_000),
            year = json.optInt("ProductionYear", 0),
            genre = genres.firstOrNull()
        )
    }

    fun parseAlbums(jsonArray: List<JSONObject>): List<JellyfinAlbum> {
        return jsonArray.map { parseAlbum(it) }
    }

    fun parseArtist(json: JSONObject): JellyfinArtist {
        return JellyfinArtist(
            id = json.optString("Id", ""),
            name = json.optString("Name", "Unknown Artist"),
            albumCount = json.optInt("AlbumCount", 0)
        )
    }

    fun parseArtists(jsonArray: List<JSONObject>): List<JellyfinArtist> {
        return jsonArray.map { parseArtist(it) }
    }

    fun parsePlaylist(json: JSONObject): JellyfinPlaylist {
        return JellyfinPlaylist(
            id = json.optString("Id", ""),
            name = json.optString("Name", "Unknown Playlist"),
            songCount = json.optInt("ChildCount", json.optInt("SongCount", 0)),
            duration = (json.optLong("RunTimeTicks", 0L) / 10_000),
            created = parseTimestamp(json.optString("DateCreated")),
            changed = parseTimestamp(json.optString("DateLastMediaAdded",
                json.optString("DateCreated")))
        )
    }

    fun parsePlaylists(jsonArray: List<JSONObject>): List<JellyfinPlaylist> {
        return jsonArray.map { parsePlaylist(it) }
    }

    private fun containerToMimeType(container: String?): String? {
        if (container.isNullOrBlank()) return null
        return when (container.lowercase()) {
            "mp3" -> "audio/mpeg"
            "flac" -> "audio/flac"
            "ogg", "oga" -> "audio/ogg"
            "m4a", "mp4", "aac" -> "audio/mp4"
            "wav" -> "audio/wav"
            "wma" -> "audio/x-ms-wma"
            "opus" -> "audio/opus"
            "webm" -> "audio/webm"
            else -> "audio/$container"
        }
    }

    private fun parseTimestamp(timestamp: String?): Long {
        if (timestamp.isNullOrBlank()) return 0L
        return try {
            java.time.OffsetDateTime.parse(timestamp).toInstant().toEpochMilli()
        } catch (e: Exception) {
            try {
                java.time.LocalDateTime.parse(timestamp.substringBefore('.'))
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
