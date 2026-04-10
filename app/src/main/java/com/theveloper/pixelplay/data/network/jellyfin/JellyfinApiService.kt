package com.theveloper.pixelplay.data.network.jellyfin

import com.theveloper.pixelplay.data.jellyfin.model.JellyfinCredentials
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Jellyfin API client.
 *
 * Implements the Jellyfin REST API with token-based authentication.
 * API Reference: https://api.jellyfin.org/
 */
@Singleton
class JellyfinApiService @Inject constructor(
    baseOkHttpClient: OkHttpClient
) {

    companion object {
        private const val TAG = "JellyfinApi"
        private const val CLIENT_NAME = "PixelPlayer"
        private const val CLIENT_VERSION = "1.0"
        private const val DEVICE_NAME = "Android"
        private const val DEVICE_ID = "PixelPlayer-Android"
    }

    @Volatile
    private var credentials: JellyfinCredentials? = null

    private val okHttpClient: OkHttpClient = baseOkHttpClient.newBuilder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    // ─── Credentials Management ─────────────────────────────────────────

    fun setCredentials(credentials: JellyfinCredentials) {
        this.credentials = credentials
        Timber.d("$TAG: Credentials set for server: ${credentials.normalizedServerUrl}, user: ${credentials.username}")
    }

    fun clearCredentials() {
        this.credentials = null
        Timber.d("$TAG: Credentials cleared")
    }

    fun hasCredentials(): Boolean = credentials?.hasToken == true

    fun getServerUrl(): String? = credentials?.normalizedServerUrl

    fun getAuthorizationHeader(): String? = credentials?.let { buildAuthorizationHeader() }

    // ─── Authentication ─────────────────────────────────────────────────

    private fun buildAuthorizationHeader(): String {
        val cred = credentials
        val tokenPart = if (cred?.accessToken != null) ", Token=\"${cred.accessToken}\"" else ""
        return "MediaBrowser Client=\"$CLIENT_NAME\", Device=\"$DEVICE_NAME\", DeviceId=\"$DEVICE_ID\", Version=\"$CLIENT_VERSION\"$tokenPart"
    }

    /**
     * Authenticate with username and password. Returns access token and user ID.
     */
    suspend fun authenticateByName(serverUrl: String, username: String, password: String): Result<Pair<String, String>> {
        return withContext(Dispatchers.IO) {
            try {
                val url = "${serverUrl.trimEnd('/')}/Users/AuthenticateByName"

                val body = JSONObject().apply {
                    put("Username", username)
                    put("Pw", password)
                }.toString().toRequestBody("application/json".toMediaType())

                val authHeader = "MediaBrowser Client=\"$CLIENT_NAME\", Device=\"$DEVICE_NAME\", DeviceId=\"$DEVICE_ID\", Version=\"$CLIENT_VERSION\""

                val request = Request.Builder()
                    .url(url)
                    .header("Authorization", authHeader)
                    .header("Content-Type", "application/json")
                    .post(body)
                    .build()

                okHttpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        return@withContext Result.failure(Exception("HTTP ${response.code}: ${response.message}"))
                    }

                    val responseBody = response.body?.string() ?: ""
                    val json = JSONObject(responseBody)
                    val accessToken = json.optString("AccessToken", "")
                    val userId = json.optJSONObject("User")?.optString("Id", "") ?: ""

                    if (accessToken.isBlank() || userId.isBlank()) {
                        return@withContext Result.failure(Exception("Invalid authentication response"))
                    }

                    Timber.d("$TAG: Authentication successful for user $username")
                    Result.success(Pair(accessToken, userId))
                }
            } catch (e: Exception) {
                Timber.e(e, "$TAG: Authentication failed")
                Result.failure(e)
            }
        }
    }

    // ─── Core Request Method ─────────────────────────────────────────────

    private suspend fun request(path: String, params: Map<String, String> = emptyMap()): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                val cred = credentials ?: throw IllegalStateException("No credentials configured")
                val baseUrl = "${cred.normalizedServerUrl}$path"

                val urlBuilder = baseUrl.toHttpUrl().newBuilder()
                params.forEach { (key, value) ->
                    urlBuilder.addQueryParameter(key, value)
                }

                val request = Request.Builder()
                    .url(urlBuilder.build())
                    .header("Authorization", buildAuthorizationHeader())
                    .header("Accept", "application/json")
                    .get()
                    .build()

                Timber.d("$TAG: >>> GET $path")

                okHttpClient.newCall(request).execute().use { response ->
                    val code = response.code
                    val body = response.body?.string() ?: ""

                    if (!response.isSuccessful) {
                        Timber.w("$TAG: <<< HTTP $code for $path")
                        return@withContext Result.failure(Exception("HTTP $code: ${response.message}"))
                    }

                    Timber.d("$TAG: <<< HTTP $code for $path, body length: ${body.length}")
                    Result.success(body)
                }
            } catch (e: Exception) {
                Timber.e(e, "$TAG: !!! FAILED GET $path")
                Result.failure(e)
            }
        }
    }

    private suspend fun requestJson(path: String, params: Map<String, String> = emptyMap()): Result<JSONObject> {
        return request(path, params).map { JSONObject(it) }
    }

    // ─── System API ──────────────────────────────────────────────────────

    suspend fun ping(): Result<Boolean> {
        return request("/System/Ping").map { true }
    }

    // ─── Library / Items API ─────────────────────────────────────────────

    /**
     * Get all music items (songs) from the user's library.
     */
    suspend fun getMusicItems(
        startIndex: Int = 0,
        limit: Int = 500,
        parentId: String? = null
    ): Result<Pair<Int, List<JSONObject>>> {
        val cred = credentials ?: return Result.failure(Exception("No credentials"))
        val params = mutableMapOf(
            "IncludeItemTypes" to "Audio",
            "Recursive" to "true",
            "Fields" to "MediaSources,Genres,Studios,Path",
            "StartIndex" to startIndex.toString(),
            "Limit" to limit.toString(),
            "SortBy" to "SortName",
            "SortOrder" to "Ascending"
        )
        parentId?.let { params["ParentId"] = it }

        return requestJson("/Users/${cred.userId}/Items", params).map { response ->
            val totalCount = response.optInt("TotalRecordCount", 0)
            val items = response.optJSONArray("Items")
            val list = (0 until (items?.length() ?: 0)).mapNotNull { items?.optJSONObject(it) }
            Pair(totalCount, list)
        }
    }

    /**
     * Get all albums from the user's library.
     */
    suspend fun getAlbums(
        startIndex: Int = 0,
        limit: Int = 500
    ): Result<Pair<Int, List<JSONObject>>> {
        val cred = credentials ?: return Result.failure(Exception("No credentials"))
        val params = mapOf(
            "IncludeItemTypes" to "MusicAlbum",
            "Recursive" to "true",
            "Fields" to "PrimaryImageAspectRatio,Genres",
            "StartIndex" to startIndex.toString(),
            "Limit" to limit.toString(),
            "SortBy" to "SortName",
            "SortOrder" to "Ascending"
        )

        return requestJson("/Users/${cred.userId}/Items", params).map { response ->
            val totalCount = response.optInt("TotalRecordCount", 0)
            val items = response.optJSONArray("Items")
            val list = (0 until (items?.length() ?: 0)).mapNotNull { items?.optJSONObject(it) }
            Pair(totalCount, list)
        }
    }

    /**
     * Get songs in a specific album.
     */
    suspend fun getAlbumItems(albumId: String): Result<List<JSONObject>> {
        val cred = credentials ?: return Result.failure(Exception("No credentials"))
        val params = mapOf(
            "ParentId" to albumId,
            "IncludeItemTypes" to "Audio",
            "Fields" to "MediaSources,Genres,Path",
            "SortBy" to "ParentIndexNumber,IndexNumber,SortName",
            "SortOrder" to "Ascending"
        )

        return requestJson("/Users/${cred.userId}/Items", params).map { response ->
            val items = response.optJSONArray("Items")
            (0 until (items?.length() ?: 0)).mapNotNull { items?.optJSONObject(it) }
        }
    }

    /**
     * Get all artists from the user's library.
     */
    suspend fun getArtists(): Result<List<JSONObject>> {
        val cred = credentials ?: return Result.failure(Exception("No credentials"))
        val params = mapOf(
            "Fields" to "PrimaryImageAspectRatio",
            "UserId" to cred.userId!!
        )

        return requestJson("/Artists", params).map { response ->
            val items = response.optJSONArray("Items")
            (0 until (items?.length() ?: 0)).mapNotNull { items?.optJSONObject(it) }
        }
    }

    // ─── Playlist API ────────────────────────────────────────────────────

    suspend fun getPlaylists(): Result<List<JSONObject>> {
        val cred = credentials ?: return Result.failure(Exception("No credentials"))
        val params = mapOf(
            "IncludeItemTypes" to "Playlist",
            "Recursive" to "true",
            "Fields" to "ChildCount",
            "MediaTypes" to "Audio"
        )

        return requestJson("/Users/${cred.userId}/Items", params).map { response ->
            val items = response.optJSONArray("Items")
            (0 until (items?.length() ?: 0)).mapNotNull { items?.optJSONObject(it) }
        }
    }

    suspend fun getPlaylistItems(playlistId: String): Result<List<JSONObject>> {
        val cred = credentials ?: return Result.failure(Exception("No credentials"))
        val params = mapOf(
            "Fields" to "MediaSources,Genres,Path",
            "UserId" to cred.userId!!
        )

        return requestJson("/Playlists/$playlistId/Items", params).map { response ->
            val items = response.optJSONArray("Items")
            (0 until (items?.length() ?: 0)).mapNotNull { items?.optJSONObject(it) }
        }
    }

    // ─── Search API ──────────────────────────────────────────────────────

    suspend fun searchSongs(query: String, limit: Int = 30): Result<List<JSONObject>> {
        val cred = credentials ?: return Result.failure(Exception("No credentials"))
        val params = mapOf(
            "SearchTerm" to query,
            "IncludeItemTypes" to "Audio",
            "Recursive" to "true",
            "Fields" to "MediaSources,Genres,Path",
            "Limit" to limit.toString()
        )

        return requestJson("/Users/${cred.userId}/Items", params).map { response ->
            val items = response.optJSONArray("Items")
            (0 until (items?.length() ?: 0)).mapNotNull { items?.optJSONObject(it) }
        }
    }

    // ─── Media URLs ──────────────────────────────────────────────────────

    /**
     * Build a streaming URL for a song.
     */
    fun getStreamUrl(itemId: String, maxBitRate: Int = 0): String {
        val cred = credentials ?: throw IllegalStateException("No credentials configured")

        val urlBuilder = "${cred.normalizedServerUrl}/Audio/$itemId/universal".toHttpUrl().newBuilder()
            .addQueryParameter("UserId", cred.userId)
            .addQueryParameter("DeviceId", DEVICE_ID)
            .addQueryParameter("Container", "mp3,flac,m4a,ogg,wav,aac,opus,webm")
            .addQueryParameter("AudioCodec", "mp3,flac,aac,opus")
            .addQueryParameter("api_key", cred.accessToken)

        if (maxBitRate > 0) {
            urlBuilder.addQueryParameter("MaxStreamingBitrate", (maxBitRate * 1000).toString())
        }

        return urlBuilder.build().toString()
    }

    /**
     * Build a cover art (primary image) URL for an item.
     */
    fun getImageUrl(itemId: String, imageType: String = "Primary", maxWidth: Int = 500): String {
        val cred = credentials ?: throw IllegalStateException("No credentials configured")
        return "${cred.normalizedServerUrl}/Items/$itemId/Images/$imageType" +
                "?maxWidth=$maxWidth&quality=90"
    }

    // ─── Lyrics API ──────────────────────────────────────────────────────

    suspend fun getLyrics(itemId: String): Result<String> {
        val cred = credentials ?: return Result.failure(Exception("No credentials"))
        return requestJson("/Audio/$itemId/Lyrics").map { response ->
            val lyrics = response.optJSONArray("Lyrics")
            if (lyrics != null && lyrics.length() > 0) {
                val sb = StringBuilder()
                for (i in 0 until lyrics.length()) {
                    val line = lyrics.optJSONObject(i)
                    val text = line?.optString("Text", "") ?: ""
                    if (text.isNotBlank()) sb.appendLine(text)
                }
                sb.toString().trim()
            } else {
                ""
            }
        }
    }
}
