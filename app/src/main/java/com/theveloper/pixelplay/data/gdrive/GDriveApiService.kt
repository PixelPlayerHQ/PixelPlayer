package com.theveloper.pixelplay.data.gdrive

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Direct OkHttp-based client for Google Drive REST API v3.
 * No Google API Client Library — keeps the app lean and consistent with existing patterns.
 */
@Singleton
class GDriveApiService @Inject constructor(
    private val okHttpClient: OkHttpClient
) {
    @Volatile
    private var accessToken: String? = null

    fun setAccessToken(token: String) {
        accessToken = token
    }

    fun clearAccessToken() {
        accessToken = null
    }

    fun hasToken(): Boolean = !accessToken.isNullOrBlank()

    fun getAuthHeader(): String {
        val token = accessToken
        require(!token.isNullOrBlank()) { "GDrive access token not set" }
        return "Bearer $token"
    }

    fun getStreamUrl(fileId: String): String {
        return "${GDriveConstants.DRIVE_API_BASE}/files/$fileId?alt=media"
    }

    /**
     * List audio files in a specific Drive folder.
     * Returns raw JSON string from the Drive API.
     */
    suspend fun listAudioFiles(folderId: String, pageToken: String? = null): String {
        val mimeQuery = GDriveConstants.AUDIO_MIME_TYPES.joinToString(" or ") { "mimeType='$it'" }
        val query = "'$folderId' in parents and ($mimeQuery) and trashed=false"
        val fields = "nextPageToken,files(id,name,mimeType,size,modifiedTime,thumbnailLink,imageMediaMetadata)"

        val urlBuilder = StringBuilder("${GDriveConstants.DRIVE_API_BASE}/files")
            .append("?q=").append(java.net.URLEncoder.encode(query, "UTF-8"))
            .append("&fields=").append(java.net.URLEncoder.encode(fields, "UTF-8"))
            .append("&pageSize=100")
            .append("&orderBy=name")

        if (pageToken != null) {
            urlBuilder.append("&pageToken=").append(java.net.URLEncoder.encode(pageToken, "UTF-8"))
        }

        return executeGet(urlBuilder.toString())
    }

    /**
     * List folders in a specific parent folder. Used for the folder picker.
     */
    suspend fun listFolders(parentId: String = "root", pageToken: String? = null): String {
        val query = "'$parentId' in parents and mimeType='application/vnd.google-apps.folder' and trashed=false"
        val fields = "nextPageToken,files(id,name)"

        val urlBuilder = StringBuilder("${GDriveConstants.DRIVE_API_BASE}/files")
            .append("?q=").append(java.net.URLEncoder.encode(query, "UTF-8"))
            .append("&fields=").append(java.net.URLEncoder.encode(fields, "UTF-8"))
            .append("&pageSize=100")
            .append("&orderBy=name")

        if (pageToken != null) {
            urlBuilder.append("&pageToken=").append(java.net.URLEncoder.encode(pageToken, "UTF-8"))
        }

        return executeGet(urlBuilder.toString())
    }

    /**
     * Get metadata for a single file.
     */
    suspend fun getFileMetadata(fileId: String): String {
        val fields = "id,name,mimeType,size,modifiedTime,thumbnailLink"
        val url = "${GDriveConstants.DRIVE_API_BASE}/files/$fileId?fields=${java.net.URLEncoder.encode(fields, "UTF-8")}"
        return executeGet(url)
    }

    /**
     * Create a new folder in Google Drive.
     * Returns raw JSON with the created folder's metadata.
     */
    suspend fun createFolder(name: String, parentId: String = "root"): String {
        return withContext(Dispatchers.IO) {
            val jsonBody = JSONObject().apply {
                put("name", name)
                put("mimeType", "application/vnd.google-apps.folder")
                put("parents", JSONArray().put(parentId))
            }.toString()
            val body = jsonBody.toRequestBody("application/json".toMediaType())

            val request = Request.Builder()
                .url("${GDriveConstants.DRIVE_API_BASE}/files")
                .header("Authorization", getAuthHeader())
                .post(body)
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                val responseBody = response.body.string()
                Timber.d("GDriveApi createFolder: code=${response.code}, body=${responseBody.take(200)}")

                if (!response.isSuccessful) {
                    throw Exception("Drive API error ${response.code}: $responseBody")
                }
                responseBody
            }
        }
    }

    /**
     * Exchange a server auth code for access + refresh tokens.
     */
    suspend fun exchangeAuthCode(
        authCode: String,
        clientId: String,
        clientSecret: String
    ): String {
        return withContext(Dispatchers.IO) {
            val formBody = okhttp3.FormBody.Builder()
                .add("code", authCode)
                .add("client_id", clientId)
                .add("client_secret", clientSecret)
                .add("grant_type", "authorization_code")
                .add("redirect_uri", "")
                .build()

            val request = Request.Builder()
                .url(GDriveConstants.TOKEN_ENDPOINT)
                .post(formBody)
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                val responseBody = response.body.string()
                Timber.d("GDriveApi exchangeAuthCode: code=${response.code}")

                if (!response.isSuccessful) {
                    throw Exception("Token exchange failed ${response.code}: $responseBody")
                }
                responseBody
            }
        }
    }

    /**
     * Refresh an expired access token using a refresh token.
     */
    suspend fun refreshToken(
        refreshToken: String,
        clientId: String,
        clientSecret: String
    ): String {
        return withContext(Dispatchers.IO) {
            val formBody = okhttp3.FormBody.Builder()
                .add("refresh_token", refreshToken)
                .add("client_id", clientId)
                .add("client_secret", clientSecret)
                .add("grant_type", "refresh_token")
                .build()

            val request = Request.Builder()
                .url(GDriveConstants.TOKEN_ENDPOINT)
                .post(formBody)
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                val responseBody = response.body.string()
                Timber.d("GDriveApi refreshToken: code=${response.code}")

                if (!response.isSuccessful) {
                    throw Exception("Token refresh failed ${response.code}: $responseBody")
                }
                responseBody
            }
        }
    }

    /**
     * Get user info from Google.
     */
    suspend fun getUserInfo(): String {
        return executeGet("https://www.googleapis.com/oauth2/v2/userinfo")
    }

    private suspend fun executeGet(url: String): String {
        return withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url(url)
                .header("Authorization", getAuthHeader())
                .get()
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                val responseBody = response.body.string()
                Timber.d("GDriveApi GET ${url.take(80)}: code=${response.code}")

                if (!response.isSuccessful) {
                    throw Exception("Drive API error ${response.code}: $responseBody")
                }
                responseBody
            }
        }
    }
}
