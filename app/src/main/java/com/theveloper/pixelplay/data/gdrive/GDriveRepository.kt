package com.theveloper.pixelplay.data.gdrive

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.theveloper.pixelplay.data.database.AlbumEntity
import com.theveloper.pixelplay.data.database.ArtistEntity
import com.theveloper.pixelplay.data.database.GDriveDao
import com.theveloper.pixelplay.data.database.GDriveFolderEntity
import com.theveloper.pixelplay.data.database.GDriveSongEntity
import com.theveloper.pixelplay.data.database.MusicDao
import com.theveloper.pixelplay.data.database.SongArtistCrossRef
import com.theveloper.pixelplay.data.database.SongEntity
import com.theveloper.pixelplay.data.database.SourceType
import com.theveloper.pixelplay.data.database.toSong
import com.theveloper.pixelplay.data.model.Song
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.json.JSONObject
import timber.log.Timber
import kotlin.math.absoluteValue
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GDriveRepository @Inject constructor(
    private val api: GDriveApiService,
    private val dao: GDriveDao,
    private val musicDao: MusicDao,
    @ApplicationContext private val context: Context
) {
    data class BulkSyncResult(
        val folderCount: Int,
        val syncedSongCount: Int,
        val failedFolderCount: Int
    )

    data class DriveFolder(val id: String, val name: String)

    private companion object {
        const val GDRIVE_SONG_ID_OFFSET = 6_000_000_000_000L
        const val GDRIVE_ALBUM_ID_OFFSET = 7_000_000_000_000L
        const val GDRIVE_ARTIST_ID_OFFSET = 8_000_000_000_000L
        const val GDRIVE_PARENT_DIRECTORY = "/Cloud/GoogleDrive"
        const val GDRIVE_GENRE = "Google Drive"
    }

    private val prefs: SharedPreferences = try {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "gdrive_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Exception) {
        Timber.e(e, "GDriveRepository: Failed to create EncryptedSharedPreferences, falling back to plain")
        context.getSharedPreferences("gdrive_prefs_plain", Context.MODE_PRIVATE)
    }

    private val _isLoggedInFlow = MutableStateFlow(false)
    val isLoggedInFlow: StateFlow<Boolean> = _isLoggedInFlow.asStateFlow()

    init {
        initFromSavedTokens()
        _isLoggedInFlow.value = api.hasToken()
        Timber.d("GDriveRepository init: isLoggedIn=${api.hasToken()}, email=${userEmail}")
    }

    // --- Auth State ---

    val isLoggedIn: Boolean get() = api.hasToken()
    val userEmail: String? get() = prefs.getString("gdrive_email", null)
    val userDisplayName: String? get() = prefs.getString("gdrive_display_name", null)
    val userAvatar: String? get() = prefs.getString("gdrive_avatar", null)

    // --- Token Management ---

    private fun initFromSavedTokens() {
        val accessToken = prefs.getString("gdrive_access_token", null) ?: return
        val expiresAt = prefs.getLong("gdrive_token_expires_at", 0L)

        if (System.currentTimeMillis() < expiresAt) {
            api.setAccessToken(accessToken)
        } else {
            // Token expired, will refresh on next API call
            Timber.d("GDrive access token expired, will refresh on next use")
        }
    }

    /**
     * Login using a Google ID token from Credential Manager.
     * For server auth code flow: exchanges the auth code for access + refresh tokens.
     */
    suspend fun loginWithCredential(
        idToken: String,
        serverAuthCode: String?,
        email: String? = null,
        displayName: String? = null,
        profilePictureUri: String? = null
    ): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                // Save user info immediately
                prefs.edit()
                    .putString("gdrive_email", email)
                    .putString("gdrive_display_name", displayName)
                    .putString("gdrive_avatar", profilePictureUri)
                    .putString("gdrive_id_token", idToken)
                    .apply()

                if (serverAuthCode != null) {
                    // Exchange auth code for access + refresh tokens
                    val tokenResponse = api.exchangeAuthCode(
                        authCode = serverAuthCode,
                        clientId = GDriveConstants.WEB_CLIENT_ID,
                        clientSecret = "" // For Android apps using installed app flow
                    )

                    val tokenJson = JSONObject(tokenResponse)
                    val accessToken = tokenJson.optString("access_token")
                    val refreshToken = tokenJson.optString("refresh_token")
                    val expiresIn = tokenJson.optLong("expires_in", 3600L)

                    if (accessToken.isNotBlank()) {
                        saveTokens(accessToken, refreshToken, expiresIn)
                        api.setAccessToken(accessToken)
                    }
                } else {
                    // Use ID token directly as bearer token (limited but works for basic access)
                    api.setAccessToken(idToken)
                    prefs.edit()
                        .putString("gdrive_access_token", idToken)
                        .putLong("gdrive_token_expires_at", System.currentTimeMillis() + 3600_000L)
                        .apply()
                }

                _isLoggedInFlow.value = true
                Result.success(displayName ?: email ?: "User")
            } catch (e: Exception) {
                Timber.e(e, "GDrive login failed")
                Result.failure(e)
            }
        }
    }

    /**
     * Refresh the access token using the stored refresh token.
     */
    suspend fun refreshAccessToken(): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                val refreshToken = prefs.getString("gdrive_refresh_token", null)
                    ?: return@withContext Result.failure(Exception("No refresh token"))

                val tokenResponse = api.refreshToken(
                    refreshToken = refreshToken,
                    clientId = GDriveConstants.WEB_CLIENT_ID,
                    clientSecret = ""
                )

                val tokenJson = JSONObject(tokenResponse)
                val accessToken = tokenJson.optString("access_token")
                val expiresIn = tokenJson.optLong("expires_in", 3600L)

                if (accessToken.isNotBlank()) {
                    saveTokens(accessToken, null, expiresIn)
                    api.setAccessToken(accessToken)
                    Result.success(accessToken)
                } else {
                    Result.failure(Exception("Empty access token in refresh response"))
                }
            } catch (e: Exception) {
                Timber.e(e, "GDrive token refresh failed")
                Result.failure(e)
            }
        }
    }

    private fun saveTokens(accessToken: String, refreshToken: String?, expiresIn: Long) {
        val editor = prefs.edit()
            .putString("gdrive_access_token", accessToken)
            .putLong("gdrive_token_expires_at", System.currentTimeMillis() + (expiresIn * 1000))

        if (!refreshToken.isNullOrBlank()) {
            editor.putString("gdrive_refresh_token", refreshToken)
        }
        editor.apply()
    }

    /**
     * Ensure the access token is valid, refreshing if needed.
     */
    private suspend fun ensureValidToken() {
        val expiresAt = prefs.getLong("gdrive_token_expires_at", 0L)
        // Refresh 5 minutes before expiry
        if (System.currentTimeMillis() > expiresAt - 300_000L) {
            refreshAccessToken()
        }
    }

    suspend fun logout() {
        api.clearAccessToken()
        prefs.edit().clear().apply()
        musicDao.clearAllGDriveSongs()
        dao.clearAllSongs()
        dao.clearAllFolders()
        _isLoggedInFlow.value = false
    }

    // --- Folder Management ---

    fun getFolders(): Flow<List<GDriveFolderEntity>> = dao.getAllFolders()

    suspend fun listDriveFolders(parentId: String = "root"): Result<List<DriveFolder>> {
        return withContext(Dispatchers.IO) {
            try {
                ensureValidToken()
                val allFolders = mutableListOf<DriveFolder>()
                var pageToken: String? = null

                do {
                    val raw = api.listFolders(parentId, pageToken)
                    val root = JSONObject(raw)
                    val files = root.optJSONArray("files")

                    if (files != null) {
                        for (i in 0 until files.length()) {
                            val file = files.optJSONObject(i) ?: continue
                            allFolders.add(
                                DriveFolder(
                                    id = file.optString("id"),
                                    name = file.optString("name")
                                )
                            )
                        }
                    }
                    pageToken = root.optString("nextPageToken").takeIf { it.isNotBlank() }
                } while (pageToken != null)

                Result.success(allFolders)
            } catch (e: Exception) {
                Timber.e(e, "Failed to list Drive folders")
                Result.failure(e)
            }
        }
    }

    suspend fun createMusicFolder(parentId: String = "root"): Result<DriveFolder> {
        return withContext(Dispatchers.IO) {
            try {
                ensureValidToken()
                val raw = api.createFolder("PixelPlay Music", parentId)
                val json = JSONObject(raw)
                val folder = DriveFolder(
                    id = json.optString("id"),
                    name = json.optString("name")
                )
                Result.success(folder)
            } catch (e: Exception) {
                Timber.e(e, "Failed to create music folder")
                Result.failure(e)
            }
        }
    }

    suspend fun addFolder(folderId: String, name: String) {
        dao.insertFolder(
            GDriveFolderEntity(
                id = folderId,
                name = name,
                songCount = 0,
                lastSyncTime = 0
            )
        )
    }

    suspend fun removeFolder(folderId: String) {
        dao.deleteSongsByFolder(folderId)
        dao.deleteFolder(folderId)
        syncUnifiedLibrarySongsFromGDrive()
    }

    // --- Sync ---

    suspend fun syncFolderSongs(folderId: String): Result<Int> {
        return withContext(Dispatchers.IO) {
            try {
                ensureValidToken()
                val allEntities = mutableListOf<GDriveSongEntity>()
                var pageToken: String? = null

                do {
                    val raw = api.listAudioFiles(folderId, pageToken)
                    val root = JSONObject(raw)
                    val files = root.optJSONArray("files")

                    if (files != null) {
                        for (i in 0 until files.length()) {
                            val file = files.optJSONObject(i) ?: continue
                            allEntities.add(parseFileToEntity(file, folderId))
                        }
                    }
                    pageToken = root.optString("nextPageToken").takeIf { it.isNotBlank() }
                } while (pageToken != null)

                dao.deleteSongsByFolder(folderId)
                dao.insertSongs(allEntities)

                // Update folder with song count
                dao.insertFolder(
                    GDriveFolderEntity(
                        id = folderId,
                        name = dao.getAllFoldersList().find { it.id == folderId }?.name ?: "Drive Folder",
                        songCount = allEntities.size,
                        lastSyncTime = System.currentTimeMillis()
                    )
                )

                syncUnifiedLibrarySongsFromGDrive()

                Timber.d("Synced ${allEntities.size} songs for folder $folderId")
                Result.success(allEntities.size)
            } catch (e: Exception) {
                Timber.e(e, "Failed to sync folder $folderId")
                Result.failure(e)
            }
        }
    }

    suspend fun syncAllFoldersAndSongs(): Result<BulkSyncResult> {
        return withContext(Dispatchers.IO) {
            val folders = dao.getAllFoldersList()
            if (folders.isEmpty()) {
                return@withContext Result.success(BulkSyncResult(0, 0, 0))
            }

            var totalSongs = 0
            var failedCount = 0

            folders.forEach { folder ->
                val result = syncFolderSongs(folder.id)
                result.fold(
                    onSuccess = { count -> totalSongs += count },
                    onFailure = { failedCount++ }
                )
            }

            Result.success(BulkSyncResult(folders.size, totalSongs, failedCount))
        }
    }

    // --- Songs ---

    fun getAllSongs(): Flow<List<Song>> = dao.getAllGDriveSongs().map { list -> list.map { it.toSong() } }

    fun getFolderSongs(folderId: String): Flow<List<Song>> =
        dao.getSongsByFolder(folderId).map { list -> list.map { it.toSong() } }

    // --- Streaming ---

    fun getStreamUrl(fileId: String): String = api.getStreamUrl(fileId)
    fun getAuthHeader(): String = api.getAuthHeader()

    // --- Unified Library Sync ---

    private suspend fun syncUnifiedLibrarySongsFromGDrive() {
        val gdriveSongs = dao.getAllGDriveSongsList()
        val existingUnifiedGDriveIds = musicDao.getAllGDriveSongIds()

        if (gdriveSongs.isEmpty()) {
            if (existingUnifiedGDriveIds.isNotEmpty()) {
                musicDao.clearAllGDriveSongs()
            }
            return
        }

        val songs = ArrayList<SongEntity>(gdriveSongs.size)
        val artists = LinkedHashMap<Long, ArtistEntity>()
        val albums = LinkedHashMap<Long, AlbumEntity>()
        val crossRefs = mutableListOf<SongArtistCrossRef>()

        gdriveSongs.forEach { gdriveSong ->
            val songId = toUnifiedSongId(gdriveSong.driveFileId)
            val artistNames = parseArtistNames(gdriveSong.artist)
            val primaryArtistName = artistNames.firstOrNull() ?: "Unknown Artist"
            val primaryArtistId = toUnifiedArtistId(primaryArtistName)

            artistNames.forEachIndexed { index, artistName ->
                val artistId = toUnifiedArtistId(artistName)
                artists.putIfAbsent(
                    artistId,
                    ArtistEntity(
                        id = artistId,
                        name = artistName,
                        trackCount = 0,
                        imageUrl = null
                    )
                )
                crossRefs.add(
                    SongArtistCrossRef(
                        songId = songId,
                        artistId = artistId,
                        isPrimary = index == 0
                    )
                )
            }

            val albumId = toUnifiedAlbumId(gdriveSong.album)
            val albumName = gdriveSong.album.ifBlank { "Unknown Album" }
            albums.putIfAbsent(
                albumId,
                AlbumEntity(
                    id = albumId,
                    title = albumName,
                    artistName = primaryArtistName,
                    artistId = primaryArtistId,
                    songCount = 0,
                    dateAdded = gdriveSong.dateAdded,
                    year = 0,
                    albumArtUriString = gdriveSong.albumArtUrl
                )
            )

            songs.add(
                SongEntity(
                    id = songId,
                    title = gdriveSong.title,
                    artistName = gdriveSong.artist.ifBlank { primaryArtistName },
                    artistId = primaryArtistId,
                    albumArtist = null,
                    albumName = albumName,
                    albumId = albumId,
                    contentUriString = "gdrive://${gdriveSong.driveFileId}",
                    albumArtUriString = gdriveSong.albumArtUrl,
                    duration = gdriveSong.duration,
                    genre = GDRIVE_GENRE,
                    filePath = "",
                    parentDirectoryPath = GDRIVE_PARENT_DIRECTORY,
                    isFavorite = false,
                    lyrics = null,
                    trackNumber = 0,
                    year = 0,
                    dateAdded = gdriveSong.dateAdded.takeIf { it > 0 } ?: System.currentTimeMillis(),
                    mimeType = gdriveSong.mimeType,
                    bitrate = gdriveSong.bitrate,
                    sampleRate = null,
                    telegramChatId = null,
                    telegramFileId = null,
                    sourceType = SourceType.GDRIVE
                )
            )
        }

        val albumCounts = songs.groupingBy { it.albumId }.eachCount()
        val finalAlbums = albums.values.map { album ->
            album.copy(songCount = albumCounts[album.id] ?: 0)
        }

        val currentUnifiedSongIds = songs.map { it.id }.toSet()
        val deletedUnifiedSongIds = existingUnifiedGDriveIds.filter { it !in currentUnifiedSongIds }

        musicDao.incrementalSyncMusicData(
            songs = songs,
            albums = finalAlbums,
            artists = artists.values.toList(),
            crossRefs = crossRefs,
            deletedSongIds = deletedUnifiedSongIds
        )
    }

    // --- Parsing Helpers ---

    private fun parseFileToEntity(file: JSONObject, folderId: String): GDriveSongEntity {
        val fileId = file.optString("id")
        val fileName = file.optString("name", "Unknown")
        val mimeType = file.optString("mimeType", "audio/mpeg")
        val fileSize = file.optLong("size", 0L)
        val modifiedTime = file.optString("modifiedTime", "")
        val thumbnailLink = file.optString("thumbnailLink").takeIf { it.isNotBlank() }

        // Parse artist and title from filename: "Artist - Title.ext" or just "Title.ext"
        val nameWithoutExt = fileName.substringBeforeLast(".")
        val parts = nameWithoutExt.split(" - ", limit = 2)
        val (artist, title) = if (parts.size == 2) {
            parts[0].trim() to parts[1].trim()
        } else {
            "Unknown Artist" to nameWithoutExt.trim()
        }

        val dateModified = try {
            java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US)
                .apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }
                .parse(modifiedTime)?.time ?: System.currentTimeMillis()
        } catch (e: Exception) {
            System.currentTimeMillis()
        }

        return GDriveSongEntity(
            id = "${folderId}_${fileId}",
            driveFileId = fileId,
            folderId = folderId,
            title = title,
            artist = artist,
            album = "Google Drive",
            albumId = -1L,
            duration = 0L, // Duration not available from Drive API metadata
            albumArtUrl = thumbnailLink,
            mimeType = mimeType,
            bitrate = null,
            fileSize = fileSize,
            dateAdded = dateModified,
            dateModified = dateModified
        )
    }

    private fun parseArtistNames(rawArtist: String): List<String> {
        if (rawArtist.isBlank()) return listOf("Unknown Artist")
        val parsed = rawArtist.split(Regex("\\s*[,/&;+]\\s*"))
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
        return if (parsed.isEmpty()) listOf("Unknown Artist") else parsed
    }

    private fun toUnifiedSongId(driveFileId: String): Long {
        return -(GDRIVE_SONG_ID_OFFSET + driveFileId.hashCode().toLong().absoluteValue)
    }

    private fun toUnifiedAlbumId(albumName: String): Long {
        val normalized = albumName.lowercase().hashCode().toLong().absoluteValue
        return -(GDRIVE_ALBUM_ID_OFFSET + normalized)
    }

    private fun toUnifiedArtistId(artistName: String): Long {
        return -(GDRIVE_ARTIST_ID_OFFSET + artistName.lowercase().hashCode().toLong().absoluteValue)
    }
}
