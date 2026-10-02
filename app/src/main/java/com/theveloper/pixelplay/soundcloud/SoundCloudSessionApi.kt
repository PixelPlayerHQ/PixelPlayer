package com.theveloper.pixelplay.soundcloud

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Authenticated SoundCloud api-v2 calls using a web-session OAuth token + cookies.
 *
 * The web client uses `/users/{id}/…` paths (not `/me/likes`, `/me/tracks`, … which 404).
 */
@Singleton
class SoundCloudSessionApi @Inject constructor() {
    private val http = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val oauthToken = AtomicReference("")
    private val cookieHeader = AtomicReference("")
    private val clientId = AtomicReference("")
    private val cachedUserId = AtomicReference<Long?>(null)
    private val cachedPermalink = AtomicReference("")

    fun updateSession(token: String?, cookies: String?, clientIdValue: String?) {
        oauthToken.set(token?.trim().orEmpty())
        cookieHeader.set(cookies?.trim().orEmpty())
        clientId.set(clientIdValue?.trim().orEmpty())
        // Force re-resolve of /me when session credentials change.
        cachedUserId.set(null)
        cachedPermalink.set("")
    }

    val hasSession: Boolean get() = oauthToken.get().isNotBlank()

    val permalink: String get() = cachedPermalink.get()

    @Throws(Exception::class)
    fun fetchMe(): Triple<String, String, Long> {
        val json = getJson("https://api-v2.soundcloud.com/me", requireAuth = true)
        val userId = json.optLong("id", -1L)
        require(userId > 0) { "SoundCloud /me did not return a user id" }
        val displayName = json.optString("username")
            .ifBlank { json.optString("full_name") }
            .ifBlank { "SoundCloud" }
        val permalink = json.optString("permalink").ifBlank { displayName }
        cachedUserId.set(userId)
        cachedPermalink.set(permalink)
        return Triple(displayName, permalink, userId)
    }

    @Throws(Exception::class)
    fun loadFeed(limit: Int = 40): List<SoundCloudSearchHit> {
        val json = getJson(
            "https://api-v2.soundcloud.com/stream?limit=${limit * 2}&linked_partitioning=1",
            requireAuth = true,
        )
        return parseCollection(json.optJSONArray("collection")).take(limit)
    }

    @Throws(Exception::class)
    fun loadRecentlyPlayed(limit: Int = 20): List<SoundCloudSearchHit> {
        return getFirstCollection(
            limit,
            "https://api-v2.soundcloud.com/me/play-history/tracks?limit=${limit * 2}&linked_partitioning=1",
            "https://api-v2.soundcloud.com/me/play-history?limit=${limit * 2}&linked_partitioning=1",
        )
    }

    @Throws(Exception::class)
    fun loadMyLikes(limit: Int = 40): List<SoundCloudSearchHit> {
        val userId = requireUserId()
        return getFirstCollection(
            limit,
            "https://api-v2.soundcloud.com/users/$userId/likes/tracks?limit=${limit * 2}&linked_partitioning=1",
            "https://api-v2.soundcloud.com/users/$userId/track_likes?limit=${limit * 2}&linked_partitioning=1",
        )
    }

    @Throws(Exception::class)
    fun loadMyTracks(limit: Int = 40): List<SoundCloudSearchHit> {
        val userId = requireUserId()
        val json = getJson(
            "https://api-v2.soundcloud.com/users/$userId/tracks?limit=${limit * 2}&linked_partitioning=1",
            requireAuth = true,
        )
        return parseTrackArray(json.optJSONArray("collection")).take(limit)
    }

    @Throws(Exception::class)
    fun loadMyPlaylists(limit: Int = 40): List<SoundCloudSearchHit> {
        val userId = requireUserId()
        return getFirstPlaylistCollection(
            limit,
            "https://api-v2.soundcloud.com/users/$userId/playlists_without_albums?limit=${limit * 2}&linked_partitioning=1",
            "https://api-v2.soundcloud.com/users/$userId/playlists?limit=${limit * 2}&linked_partitioning=1",
        )
    }

    @Throws(Exception::class)
    fun setTrackLiked(trackUrl: String, liked: Boolean) {
        val encoded = java.net.URLEncoder.encode(trackUrl.trim(), Charsets.UTF_8.name())
        val track = getJson(
            "https://api-v2.soundcloud.com/resolve?url=$encoded",
            requireAuth = true,
        )
        val trackId = track.optLong("id", -1L)
        require(trackId > 0) { "SoundCloud could not resolve this track for liking" }
        val userId = requireUserId()
        mutate(
            url = "https://api-v2.soundcloud.com/users/$userId/track_likes/$trackId",
            method = if (liked) "PUT" else "DELETE",
        )
    }

    private fun requireUserId(): Long {
        cachedUserId.get()?.let { return it }
        return fetchMe().third
    }

    private fun getFirstCollection(limit: Int, vararg urls: String): List<SoundCloudSearchHit> {
        var lastError: Throwable? = null
        for (url in urls) {
            try {
                val json = getJson(url, requireAuth = true)
                val hits = parseCollection(json.optJSONArray("collection"))
                if (hits.isNotEmpty() || json.has("collection")) return hits.take(limit)
            } catch (t: Throwable) {
                lastError = t
            }
        }
        throw lastError ?: IllegalStateException("No SoundCloud likes endpoint succeeded")
    }

    private fun getFirstPlaylistCollection(limit: Int, vararg urls: String): List<SoundCloudSearchHit> {
        var lastError: Throwable? = null
        for (url in urls) {
            try {
                val json = getJson(url, requireAuth = true)
                val hits = parsePlaylistCollection(json.optJSONArray("collection"))
                if (hits.isNotEmpty() || json.has("collection")) return hits.take(limit)
            } catch (t: Throwable) {
                lastError = t
            }
        }
        throw lastError ?: IllegalStateException("No SoundCloud playlists endpoint succeeded")
    }

    private fun parseCollection(array: JSONArray?): List<SoundCloudSearchHit> {
        if (array == null) return emptyList()
        val out = ArrayList<SoundCloudSearchHit>(array.length())
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            val track = item.optJSONObject("track")
                ?: item.takeIf { it.has("permalink_url") && it.has("title") }
            if (track != null) {
                trackToHit(track)?.let { out.add(it) }
                continue
            }
            val playlist = item.optJSONObject("playlist")
            if (playlist != null) {
                playlistToHit(playlist)?.let { out.add(it) }
            }
        }
        return out
    }

    private fun parseTrackArray(array: JSONArray?): List<SoundCloudSearchHit> {
        if (array == null) return emptyList()
        val out = ArrayList<SoundCloudSearchHit>(array.length())
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            val track = item.optJSONObject("track") ?: item
            trackToHit(track)?.let { out.add(it) }
        }
        return out
    }

    private fun parsePlaylistCollection(array: JSONArray?): List<SoundCloudSearchHit> {
        if (array == null) return emptyList()
        val out = ArrayList<SoundCloudSearchHit>(array.length())
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            val playlist = item.optJSONObject("playlist") ?: item
            playlistToHit(playlist)?.let { out.add(it) }
        }
        return out
    }

    private fun trackToHit(track: JSONObject): SoundCloudSearchHit? {
        if (isUnplayableTrack(track)) return null
        val url = track.optString("permalink_url").ifBlank { return null }
        val user = track.optJSONObject("user")
        val durationMs = track.optLong("duration", 0L)
        return SoundCloudSearchHit(
            url = url,
            title = track.optString("title").ifBlank { "Unknown title" },
            artist = user?.optString("username").orEmpty().ifBlank { "Unknown artist" },
            durationSec = (durationMs / 1000L).coerceAtLeast(0L),
            thumbnailUrl = track.optString("artwork_url")
                .ifBlank { user?.optString("avatar_url").orEmpty() }
                .let { if (it.isBlank()) null else upgradeArtworkUrl(it) },
            kind = SoundCloudSearchHit.Kind.TRACK,
            availability = SoundCloudSearchHit.Availability.AVAILABLE,
        )
    }

    private fun playlistToHit(playlist: JSONObject): SoundCloudSearchHit? {
        val url = playlist.optString("permalink_url").ifBlank { return null }
        val user = playlist.optJSONObject("user")
        val firstTrackArt = playlist.optJSONArray("tracks")
            ?.optJSONObject(0)
            ?.optString("artwork_url")
            .orEmpty()
        val art = sequenceOf(
            playlist.optString("artwork_url"),
            playlist.optString("calculated_artwork_url"),
            firstTrackArt,
            user?.optString("avatar_url").orEmpty(),
        )
            .map { it.trim() }
            .firstOrNull { it.isNotBlank() }
            ?.let { upgradeArtworkUrl(it) }
        return SoundCloudSearchHit(
            url = url,
            title = playlist.optString("title").ifBlank { "Playlist" },
            artist = user?.optString("username").orEmpty().ifBlank { "SoundCloud" },
            durationSec = 0,
            thumbnailUrl = art,
            kind = SoundCloudSearchHit.Kind.PLAYLIST,
            streamCount = playlist.optLong("track_count", 0L).coerceAtLeast(0L),
        )
    }

    private fun upgradeArtworkUrl(url: String): String {
        return url
            .replace("-large.", "-t500x500.")
            .replace("-t200x200.", "-t500x500.")
            .replace("-t67x67.", "-t500x500.")
            .replace("-badge.", "-t500x500.")
    }

    /**
     * True when the track JSON itself says it cannot be streamed: blocked, not
     * streamable, or every audio transcoding is DRM. Usable when the payload is
     * already in memory, so list loading does not gain extra requests.
     */
    internal fun isUnplayableTrack(track: JSONObject): Boolean {
        if (track.has("streamable") && !track.optBoolean("streamable", true)) return true
        val policy = track.optString("policy").lowercase()
        if (policy == "block" || policy == "blocked") return true
        return isDrmOnlyTrack(track)
    }

    /**
     * Returns true when every known audio transcoding is DRM encrypted-hls
     * (cbc-/ctr-encrypted-hls). Tracks with progressive or plain HLS stay visible.
     */
    internal fun isDrmOnlyTrack(track: JSONObject): Boolean {
        val transcodings = track.optJSONObject("media")?.optJSONArray("transcodings")
            ?: return false
        if (transcodings.length() == 0) return false
        var sawAny = false
        var sawUsable = false
        for (i in 0 until transcodings.length()) {
            val item = transcodings.optJSONObject(i) ?: continue
            val protocol = item.optJSONObject("format")?.optString("protocol").orEmpty()
                .ifBlank { item.optString("protocol") }
                .lowercase()
            if (protocol.isBlank()) continue
            sawAny = true
            if (!protocol.contains("encrypted")) {
                sawUsable = true
                break
            }
        }
        return sawAny && !sawUsable
    }

    /**
     * True when the permalink can stream, false when the track is blocked or DRM-only.
     * Null when the lookup itself failed — callers must not treat that as unplayable.
     */
    fun playbackAvailability(trackUrl: String): Boolean? {
        return try {
            resolvedTrack(trackUrl)?.let { !isUnplayableTrack(it) }
        } catch (_: Throwable) {
            null
        }
    }

    /** Title, artist, album, genre, and artwork from api-v2. Null when the lookup fails. */
    fun loadFileTags(trackUrl: String): SoundCloudFileTags? {
        val track = try {
            resolvedTrack(trackUrl)
        } catch (_: Throwable) {
            null
        } ?: return null
        val user = track.optJSONObject("user")
        val publisher = track.optJSONObject("publisher_metadata")
        val title = track.optString("title").trim()
        if (title.isEmpty()) return null
        val artist = publisher?.optString("artist").orEmpty().trim()
            .ifBlank { user?.optString("username").orEmpty().trim() }
            .ifBlank { return null }
        val album = publisher?.optString("album_title").orEmpty().trim()
            .ifBlank { publisher?.optString("release_title").orEmpty().trim() }
            .ifBlank { artist }
        val genre = track.optString("genre").trim().ifBlank { null }
        val artwork = track.optString("artwork_url")
            .ifBlank { user?.optString("avatar_url").orEmpty() }
            .trim()
            .ifBlank { null }
            ?.let { upgradeArtworkUrl(it) }
        return SoundCloudFileTags(
            title = title,
            artist = artist,
            album = album,
            albumArtist = artist,
            genre = genre?.takeUnless { it.isSoundCloudPlaceholder() },
            artworkUrl = artwork,
        )
    }

    private fun resolvedTrack(trackUrl: String): JSONObject? {
        val encoded = java.net.URLEncoder.encode(trackUrl.trim(), Charsets.UTF_8.name())
        val json = getJson(
            "https://api-v2.soundcloud.com/resolve?url=$encoded",
            requireAuth = false,
        )
        return when {
            json.has("media") || (json.has("permalink_url") && json.has("title")) -> json
            else -> json.optJSONObject("track")
        }
    }

    private fun getJson(url: String, requireAuth: Boolean): JSONObject =
        JSONObject(getRaw(url, requireAuth))

    private fun getRaw(url: String, requireAuth: Boolean): String {
        val token = oauthToken.get()
        if (requireAuth) {
            require(token.isNotBlank()) { "Sign in to SoundCloud to load your personal library" }
        }
        val id = clientId.get()
        val finalUrl = if (id.isNotBlank() && !url.contains("client_id=")) {
            url + (if (url.contains("?")) "&" else "?") + "client_id=$id"
        } else {
            url
        }
        val builder = Request.Builder()
            .url(finalUrl)
            .get()
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/json, text/javascript, */*; q=0.01")
            .header("Origin", "https://soundcloud.com")
            .header("Referer", "https://soundcloud.com/")
        if (token.isNotBlank()) {
            builder.header("Authorization", "OAuth $token")
        }
        val cookies = cookieHeader.get()
        if (cookies.isNotBlank()) {
            builder.header("Cookie", cookies)
        }
        http.newCall(builder.build()).execute().use { response ->
            val body = response.body.string()
            if (!response.isSuccessful) {
                throw IllegalStateException("SoundCloud API ${response.code}: ${body.take(180)}")
            }
            return body
        }
    }

    private fun mutate(url: String, method: String) {
        val token = oauthToken.get()
        require(token.isNotBlank()) { "Sign in to SoundCloud to like tracks" }
        val id = clientId.get()
        val finalUrl = if (id.isNotBlank() && !url.contains("client_id=")) {
            "$url?client_id=$id"
        } else {
            url
        }
        val builder = Request.Builder()
            .url(finalUrl)
            .method(method, ByteArray(0).toRequestBody(null))
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/json, text/javascript, */*; q=0.01")
            .header("Authorization", "OAuth $token")
            .header("Origin", "https://soundcloud.com")
            .header("Referer", "https://soundcloud.com/")
        cookieHeader.get().takeIf { it.isNotBlank() }?.let { builder.header("Cookie", it) }
        http.newCall(builder.build()).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException(
                    "SoundCloud like request failed (${response.code}): ${response.body.string().take(180)}",
                )
            }
        }
    }

    companion object {
        private const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:128.0) Gecko/20100101 Firefox/128.0"
    }
}
