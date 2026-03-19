package com.theveloper.pixelplay.data.network.navidrome

import com.theveloper.pixelplay.data.navidrome.model.NavidromeCredentials
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import timber.log.Timber
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Navidrome/Subsonic API client.
 *
 * Implements the Subsonic API protocol with token-based authentication.
 * Compatible with Navidrome, Gonic, Airsonic, and other Subsonic-compatible servers.
 *
 * API Reference: http://www.subsonic.org/pages/api.jsp
 */
@Singleton
class NavidromeApiService @Inject constructor(
    // P1-3: Inject singleton OkHttpClient instead of creating a new one.
    // Use newBuilder() to apply Navidrome-specific timeouts while sharing the base
    // connection pool and dispatcher, saving ~2-4MB RAM.
    baseOkHttpClient: OkHttpClient
) {

    companion object {
        private const val TAG = "NavidromeApi"
        private const val API_VERSION = "1.16.1"
        private const val DEFAULT_CLIENT_ID = "PixelPlayer"
        private const val DEFAULT_FORMAT = "json"
    }

    // Current server credentials (can be updated at runtime)
    @Volatile
    private var credentials: NavidromeCredentials? = null

    private val okHttpClient: OkHttpClient = baseOkHttpClient.newBuilder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS) // Longer timeout for streaming
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    // ─── Credentials Management ─────────────────────────────────────────

    /**
     * Set the server credentials for API calls.
     */
    fun setCredentials(credentials: NavidromeCredentials) {
        this.credentials = credentials
        Timber.d("$TAG: Credentials set for server: ${credentials.normalizedServerUrl}, user: ${credentials.username}")
    }

    /**
     * Clear the stored credentials.
     */
    fun clearCredentials() {
        this.credentials = null
        Timber.d("$TAG: Credentials cleared")
    }

    /**
     * Check if credentials are configured.
     */
    fun hasCredentials(): Boolean = credentials?.isValid == true

    /**
     * Get the current server URL.
     */
    fun getServerUrl(): String? = credentials?.normalizedServerUrl

    // ─── Authentication ─────────────────────────────────────────────────

    /**
     * Generate authentication parameters using the token/salt method.
     * Token = md5(password + salt)
     *
     * @return Pair of (token, salt)
     */
    private fun generateAuthParams(password: String): Pair<String, String> {
        val salt = UUID.randomUUID().toString().take(6)
        val token = md5(password + salt)
        return Pair(token, salt)
    }

    /**
     * Compute MD5 hash of a string.
     */
    private fun md5(input: String): String {
        val md = MessageDigest.getInstance("MD5")
        val digest = md.digest(input.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    /**
     * Build a URL with authentication parameters for a Subsonic API endpoint.
     */
    private fun buildApiUrl(endpoint: String, extraParams: Map<String, String> = emptyMap()): String {
        val cred = credentials ?: throw IllegalStateException("No credentials configured")
        val (token, salt) = generateAuthParams(cred.password)

        val baseUrl = "${cred.normalizedServerUrl}/rest/$endpoint.view"

        val urlBuilder = baseUrl.toHttpUrl().newBuilder()
            .addQueryParameter("u", cred.username)
            .addQueryParameter("t", token)
            .addQueryParameter("s", salt)
            .addQueryParameter("v", API_VERSION)
            .addQueryParameter("c", cred.clientId.ifBlank { DEFAULT_CLIENT_ID })
            .addQueryParameter("f", DEFAULT_FORMAT)

        extraParams.forEach { (key, value) ->
            urlBuilder.addQueryParameter(key, value)
        }

        return urlBuilder.build().toString()
    }

    // ─── Core Request Method ─────────────────────────────────────────────

    /**
     * Make a GET request to a Subsonic API endpoint.
     *
     * @param endpoint The API endpoint name (without .view suffix)
     * @param params Additional query parameters
     * @return The raw JSON response as a string
     */
    private suspend fun request(endpoint: String, params: Map<String, String> = emptyMap()): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                val url = buildApiUrl(endpoint, params)
                Timber.d("$TAG: >>> GET $endpoint")

                val request = Request.Builder()
                    .url(url)
                    .header("Accept", "application/json")
                    .header("User-Agent", "PixelPlayer/${API_VERSION}")
                    .get()
                    .build()

                okHttpClient.newCall(request).execute().use { response ->
                    val code = response.code
                    val body = response.body?.string() ?: ""

                    if (!response.isSuccessful) {
                        Timber.w("$TAG: <<< HTTP $code for $endpoint")
                        return@withContext Result.failure(Exception("HTTP $code: ${response.message}"))
                    }

                    Timber.d("$TAG: <<< HTTP $code for $endpoint, body length: ${body.length}")
                    Result.success(body)
                }
            } catch (e: Exception) {
                Timber.e(e, "$TAG: !!! FAILED GET $endpoint")
                Result.failure(e)
            }
        }
    }

    /**
     * Parse and validate a Subsonic API JSON response.
     * Subsonic responses are wrapped in a "subsonic-response" object.
     *
     * @return The "subsonic-response" JSON object, or an error
     */
    private fun parseResponse(raw: String): Result<JSONObject> {
        return try {
            val root = JSONObject(raw)
            val subsonicResponse = root.optJSONObject("subsonic-response")
                ?: return Result.failure(Exception("Invalid response: missing subsonic-response"))

            val status = subsonicResponse.optString("status", "failed")
            if (status != "ok") {
                val error = subsonicResponse.optJSONObject("error")
                val code = error?.optInt("code", -1) ?: -1
                val message = error?.optString("message", "Unknown error") ?: "Unknown error"
                return Result.failure(Exception("API Error $code: $message"))
            }

            Result.success(subsonicResponse)
        } catch (e: Exception) {
            Timber.e(e, "$TAG: Failed to parse response")
            Result.failure(e)
        }
    }

    /**
     * Make a request and parse the response.
     */
    private suspend fun requestAndParse(endpoint: String, params: Map<String, String> = emptyMap()): Result<JSONObject> {
        return request(endpoint, params).fold(
            onSuccess = { parseResponse(it) },
            onFailure = { Result.failure(it) }
        )
    }

    // ─── System API ──────────────────────────────────────────────────────

    /**
     * Ping the server to test connectivity and authentication.
     */
    suspend fun ping(): Result<Boolean> {
        return requestAndParse("ping").map { true }
    }

    /**
     * Get server license information.
     */
    suspend fun getLicense(): Result<JSONObject> {
        return requestAndParse("getLicense")
    }

    // ─── Browsing API ────────────────────────────────────────────────────

    /**
     * Get all music folders (libraries) configured on the server.
     */
    suspend fun getMusicFolders(): Result<List<JSONObject>> {
        return requestAndParse("getMusicFolders").map { response ->
            val folders = response.optJSONObject("musicFolders")?.optJSONArray("musicFolder")
            (0 until (folders?.length() ?: 0)).mapNotNull { folders?.optJSONObject(it) }
        }
    }

    /**
     * Get all artists.
     * Uses getArtists (ID3 tags) for better metadata.
     */
    suspend fun getArtists(musicFolderId: String? = null): Result<List<JSONObject>> {
        val params = musicFolderId?.let { mapOf("musicFolderId" to it) } ?: emptyMap()
        return requestAndParse("getArtists", params).map { response ->
            val artists = response.optJSONObject("artists")?.optJSONArray("index")
            val result = mutableListOf<JSONObject>()
            (0 until (artists?.length() ?: 0)).forEach { i ->
                val index = artists?.optJSONObject(i)
                val artistArray = index?.optJSONArray("artist")
                (0 until (artistArray?.length() ?: 0)).forEach { j ->
                    artistArray?.optJSONObject(j)?.let { result.add(it) }
                }
            }
            result
        }
    }

    /**
     * Get albums by an artist.
     */
    suspend fun getArtist(id: String): Result<JSONObject> {
        return requestAndParse("getArtist", mapOf("id" to id))
    }

    /**
     * Get albums list.
     * Uses getAlbumList2 (ID3 tags) for better metadata.
     *
     * @param type One of: random, newest, highest, frequent, recent, alphabeticalByName,
     *             alphabeticalByArtist, starred, byGenre, byYear
     * @param size Number of albums to return (default 10, max 500)
     * @param offset Offset for pagination
     * @param musicFolderId Filter by music folder
     */
    suspend fun getAlbumList(
        type: String = "newest",
        size: Int = 50,
        offset: Int = 0,
        musicFolderId: String? = null
    ): Result<List<JSONObject>> {
        val params = mutableMapOf(
            "type" to type,
            "size" to size.toString(),
            "offset" to offset.toString()
        )
        musicFolderId?.let { params["musicFolderId"] = it }

        return requestAndParse("getAlbumList2", params).map { response ->
            val albums = response.optJSONObject("albumList2")?.optJSONArray("album")
            (0 until (albums?.length() ?: 0)).mapNotNull { albums?.optJSONObject(it) }
        }
    }

    /**
     * Get songs in an album.
     */
    suspend fun getAlbum(id: String): Result<List<JSONObject>> {
        return requestAndParse("getAlbum", mapOf("id" to id)).map { response ->
            val songs = response.optJSONObject("album")?.optJSONArray("song")
            (0 until (songs?.length() ?: 0)).mapNotNull { songs?.optJSONObject(it) }
        }
    }

    /**
     * Get song details.
     */
    suspend fun getSong(id: String): Result<JSONObject> {
        return requestAndParse("getSong", mapOf("id" to id))
    }

    // ─── Playlist API ────────────────────────────────────────────────────

    /**
     * Get all playlists.
     */
    suspend fun getPlaylists(): Result<List<JSONObject>> {
        return requestAndParse("getPlaylists").map { response ->
            val playlistsContainer = response.optJSONObject("playlists")
            val playlists = playlistsContainer?.optJSONArray("playlist")
            
            if (playlists == null) {
                // Fallback: Some versions might put the array directly under "subsonic-response" or another key
                val topLevelPlaylists = response.optJSONArray("playlist")
                if (topLevelPlaylists != null) {
                    return@map (0 until topLevelPlaylists.length()).mapNotNull { topLevelPlaylists.optJSONObject(it) }
                }
                return@map emptyList<JSONObject>()
            }
            
            (0 until (playlists.length())).mapNotNull { playlists.optJSONObject(it) }
        }
    }

    /**
     * Get playlist details with songs.
     */
    suspend fun getPlaylist(id: String): Result<Pair<JSONObject, List<JSONObject>>> {
        return requestAndParse("getPlaylist", mapOf("id" to id)).map { response ->
            val playlist = response.optJSONObject("playlist") ?: JSONObject()
            val songs = playlist.optJSONArray("entry")
            
            if (songs == null) {
                // Some older Subsonic servers might use "song" or return a different structure
                val altSongs = playlist.optJSONArray("song")
                if (altSongs != null) {
                    val songList = (0 until altSongs.length()).mapNotNull { altSongs.optJSONObject(it) }
                    return@map Pair(playlist, songList)
                }
                return@map Pair(playlist, emptyList<JSONObject>())
            }
            
            val songList = (0 until songs.length()).mapNotNull { songs.optJSONObject(it) }
            Pair(playlist, songList)
        }
    }

    // ─── Search API ──────────────────────────────────────────────────────

    /**
     * Search for songs, albums, and artists.
     * Uses search3 (ID3 tags) for better metadata.
     *
     * @param query Search query
     * @param artistCount Max artists to return
     * @param albumCount Max albums to return
     * @param songCount Max songs to return
     */
    suspend fun search3(
        query: String,
        artistCount: Int = 10,
        albumCount: Int = 20,
        songCount: Int = 30
    ): Result<JSONObject> {
        val params = mapOf(
            "query" to query,
            "artistCount" to artistCount.toString(),
            "albumCount" to albumCount.toString(),
            "songCount" to songCount.toString()
        )
        return requestAndParse("search3", params)
    }

    /**
     * Search for songs only.
     */
    suspend fun searchSongs(query: String, count: Int = 30): Result<List<JSONObject>> {
        return search3(query, artistCount = 0, albumCount = 0, songCount = count).map { response ->
            val searchResult = response.optJSONObject("searchResult3")
            val songs = searchResult?.optJSONArray("song")
            (0 until (songs?.length() ?: 0)).mapNotNull { songs?.optJSONObject(it) }
        }
    }

    /**
     * Search for albums only.
     */
    suspend fun searchAlbums(query: String, count: Int = 20): Result<List<JSONObject>> {
        return search3(query, artistCount = 0, albumCount = count, songCount = 0).map { response ->
            val searchResult = response.optJSONObject("searchResult3")
            val albums = searchResult?.optJSONArray("album")
            (0 until (albums?.length() ?: 0)).mapNotNull { albums?.optJSONObject(it) }
        }
    }

    /**
     * Search for artists only.
     */
    suspend fun searchArtists(query: String, count: Int = 10): Result<List<JSONObject>> {
        return search3(query, artistCount = count, albumCount = 0, songCount = 0).map { response ->
            val searchResult = response.optJSONObject("searchResult3")
            val artists = searchResult?.optJSONArray("artist")
            (0 until (artists?.length() ?: 0)).mapNotNull { artists?.optJSONObject(it) }
        }
    }

    // ─── Media Retrieval API ─────────────────────────────────────────────

    /**
     * Build a streaming URL for a song.
     * This returns a URL that can be passed directly to ExoPlayer.
     *
     * @param songId The song ID
     * @param maxBitRate Maximum bitrate in kbps (0 = no limit)
     * @param format Target format (e.g., "mp3", "raw")
     */
    fun getStreamUrl(songId: String, maxBitRate: Int = 0, format: String? = null): String {
        val cred = credentials ?: throw IllegalStateException("No credentials configured")
        val (token, salt) = generateAuthParams(cred.password)

        val urlBuilder = "${cred.normalizedServerUrl}/rest/stream.view".toHttpUrl().newBuilder()
            .addQueryParameter("u", cred.username)
            .addQueryParameter("t", token)
            .addQueryParameter("s", salt)
            .addQueryParameter("v", API_VERSION)
            .addQueryParameter("c", cred.clientId.ifBlank { DEFAULT_CLIENT_ID })
            .addQueryParameter("id", songId)

        if (maxBitRate > 0) {
            urlBuilder.addQueryParameter("maxBitRate", maxBitRate.toString())
        }
        if (format != null) {
            urlBuilder.addQueryParameter("format", format)
        }

        return urlBuilder.build().toString()
    }

    /**
     * Build a cover art URL.
     *
     * @param coverArtId The cover art ID (usually same as song/album ID)
     * @param size Desired size in pixels
     */
    fun getCoverArtUrl(coverArtId: String, size: Int = 500): String {
        val cred = credentials ?: throw IllegalStateException("No credentials configured")
        val (token, salt) = generateAuthParams(cred.password)

        return "${cred.normalizedServerUrl}/rest/getCoverArt.view"
            .toHttpUrl().newBuilder()
            .addQueryParameter("u", cred.username)
            .addQueryParameter("t", token)
            .addQueryParameter("s", salt)
            .addQueryParameter("v", API_VERSION)
            .addQueryParameter("c", cred.clientId.ifBlank { DEFAULT_CLIENT_ID })
            .addQueryParameter("id", coverArtId)
            .addQueryParameter("size", size.toString())
            .build()
            .toString()
    }

    // ─── Lyrics API ──────────────────────────────────────────────────────

    /**
     * Get lyrics for a song.
     * Note: Not all servers support this endpoint.
     */
    suspend fun getLyrics(artist: String? = null, title: String? = null): Result<String> {
        val params = mutableMapOf<String, String>()
        artist?.let { params["artist"] = it }
        title?.let { params["title"] = it }

        return requestAndParse("getLyrics", params).map { response ->
            response.optJSONObject("lyrics")?.optString("value", "") ?: ""
        }
    }

    /**
     * Get lyrics by song ID (OpenSubsonic extension, supported by Navidrome).
     */
    suspend fun getLyricsBySongId(songId: String): Result<String> {
        return requestAndParse("getLyricsBySongId", mapOf("id" to songId)).map { response ->
            val lyrics = response.optJSONObject("lyricsList")?.optJSONArray("structuredLyrics")
                ?.optJSONObject(0)?.optJSONArray("line")

            if (lyrics != null && lyrics.length() > 0) {
                (0 until lyrics.length()).mapNotNull { lyrics.optJSONObject(it)?.optString("value") }
                    .joinToString("\n")
            } else {
                ""
            }
        }
    }

    // ─── Star/Favorite API ───────────────────────────────────────────────

    /**
     * Star a song, album, or artist.
     */
    suspend fun star(id: String? = null, albumId: String? = null, artistId: String? = null): Result<Boolean> {
        val params = mutableMapOf<String, String>()
        id?.let { params["id"] = it }
        albumId?.let { params["albumId"] = it }
        artistId?.let { params["artistId"] = it }

        return requestAndParse("star", params).map { true }
    }

    /**
     * Unstar a song, album, or artist.
     */
    suspend fun unstar(id: String? = null, albumId: String? = null, artistId: String? = null): Result<Boolean> {
        val params = mutableMapOf<String, String>()
        id?.let { params["id"] = it }
        albumId?.let { params["albumId"] = it }
        artistId?.let { params["artistId"] = it }

        return requestAndParse("unstar", params).map { true }
    }
}
