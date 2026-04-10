package com.theveloper.pixelplay.data.navidrome

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.theveloper.pixelplay.data.database.AlbumEntity
import com.theveloper.pixelplay.data.database.ArtistEntity
import com.theveloper.pixelplay.data.database.MusicDao
import com.theveloper.pixelplay.data.database.NavidromeDao
import com.theveloper.pixelplay.data.database.NavidromePlaylistEntity
import com.theveloper.pixelplay.data.database.NavidromeSongEntity
import com.theveloper.pixelplay.data.database.toEntity
import com.theveloper.pixelplay.data.database.SongArtistCrossRef
import com.theveloper.pixelplay.data.database.SongEntity
import com.theveloper.pixelplay.data.database.SourceType
import com.theveloper.pixelplay.data.database.toSong
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.navidrome.model.NavidromeCredentials
import com.theveloper.pixelplay.data.navidrome.model.NavidromeSong
import com.theveloper.pixelplay.data.network.navidrome.NavidromeApiService
import com.theveloper.pixelplay.data.network.navidrome.NavidromeResponseParser
import com.theveloper.pixelplay.data.preferences.PlaylistPreferencesRepository
import com.theveloper.pixelplay.data.stream.BulkSyncResult
import com.theveloper.pixelplay.data.stream.CloudMusicUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.json.JSONObject
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.absoluteValue

/**
 * Repository for Navidrome/Subsonic music service.
 *
 * Manages authentication, playlist synchronization, and song caching.
 */
@Singleton
class NavidromeRepository @Inject constructor(
    private val api: NavidromeApiService,
    private val dao: NavidromeDao,
    private val musicDao: MusicDao,
    private val playlistPreferencesRepository: PlaylistPreferencesRepository,
    @ApplicationContext private val context: Context
) {
    private companion object {
        private const val TAG = "NavidromeRepo"
        private const val PREFS_NAME = "navidrome_prefs"
        private const val KEY_SERVER_URL = "server_url"
        private const val KEY_USERNAME = "username"
        private const val KEY_PASSWORD = "password"

        // ID offsets for unified library (following Netease: 3-5, QQ: 6-8)
        // Using negative offsets to prevent collisions with MediaStore IDs
        private const val NAVIDROME_SONG_ID_OFFSET = 9_000_000_000_000L
        private const val NAVIDROME_ALBUM_ID_OFFSET = 10_000_000_000_000L
        private const val NAVIDROME_ARTIST_ID_OFFSET = 11_000_000_000_000L
        private const val NAVIDROME_PARENT_DIRECTORY = "/Cloud/Navidrome"
        private const val NAVIDROME_GENRE = "Navidrome"
        private const val NAVIDROME_PLAYLIST_PREFIX = "navidrome_playlist:"
        private const val LIBRARY_PLAYLIST_ID = "__library__"
    }

    private val prefs: SharedPreferences = try {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Exception) {
        Timber.e(e, "$TAG: Failed to create EncryptedSharedPreferences, falling back to plain")
        context.getSharedPreferences("${PREFS_NAME}_plain", Context.MODE_PRIVATE)
    }

    private val _isLoggedInFlow = MutableStateFlow(false)
    val isLoggedInFlow: StateFlow<Boolean> = _isLoggedInFlow.asStateFlow()

    init {
        initFromSavedCredentials()
    }

    // ─── Authentication ──────────────────────────────────────────────────

    /**
     * Initialize API from saved credentials.
     */
    private fun initFromSavedCredentials() {
        val serverUrl = prefs.getString(KEY_SERVER_URL, null)
        val username = prefs.getString(KEY_USERNAME, null)
        val password = prefs.getString(KEY_PASSWORD, null)

        if (!serverUrl.isNullOrBlank() && !username.isNullOrBlank() && !password.isNullOrBlank()) {
            val credentials = NavidromeCredentials(serverUrl, username, password)
            val validationError = credentials.connectionValidationError()
            if (validationError != null) {
                Timber.w("$TAG: Ignoring insecure or invalid saved Navidrome server URL: $validationError")
                api.clearCredentials()
                _isLoggedInFlow.value = false
                return
            }
            api.setCredentials(credentials)
            _isLoggedInFlow.value = true
            Timber.d("$TAG: Restored credentials for $username@${credentials.normalizedServerUrl}")
        }
    }

    /**
     * Check if user is logged in.
     */
    val isLoggedIn: Boolean
        get() = _isLoggedInFlow.value

    /**
     * Get the current server URL.
     */
    val serverUrl: String?
        get() = prefs.getString(KEY_SERVER_URL, null)

    /**
     * Get the current username.
     */
    val username: String?
        get() = prefs.getString(KEY_USERNAME, null)

    /**
     * Login to Navidrome server with credentials.
     *
     * @param serverUrl The server URL (e.g., "https://music.example.com")
     * @param username The username
     * @param password The password
     * @return Result with username on success, error on failure
     */
    suspend fun login(serverUrl: String, username: String, password: String): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                Timber.d("$TAG: Attempting login to $serverUrl as $username")

                val credentials = NavidromeCredentials(serverUrl, username, password)
                val validationError = credentials.connectionValidationError()
                if (validationError != null) {
                    api.clearCredentials()
                    return@withContext Result.failure(IllegalArgumentException(validationError))
                }
                api.setCredentials(credentials)

                // Test connection
                val pingResult = api.ping()
                if (pingResult.isFailure) {
                    api.clearCredentials()
                    return@withContext Result.failure(
                        pingResult.exceptionOrNull() ?: Exception("Connection failed")
                    )
                }

                // Save credentials
                prefs.edit()
                    .putString(KEY_SERVER_URL, credentials.normalizedServerUrl)
                    .putString(KEY_USERNAME, username)
                    .putString(KEY_PASSWORD, password)
                    .apply()

                _isLoggedInFlow.value = true
                Timber.d("$TAG: Login successful for $username@$serverUrl")
                Result.success(username)
            } catch (e: Exception) {
                Timber.e(e, "$TAG: Login failed")
                api.clearCredentials()
                _isLoggedInFlow.value = false
                Result.failure(e)
            }
        }
    }

    /**
     * Logout and clear all cached data.
     */
    suspend fun logout() {
        Timber.d("$TAG: Logging out")
        api.clearCredentials()
        prefs.edit().clear().apply()

        // Delete all Navidrome playlists from database
        val playlistsToDelete = dao.getAllPlaylistsList()
        playlistsToDelete.forEach { playlist ->
            dao.deleteSongsByPlaylist(playlist.id)
            deleteAppPlaylistForNavidromePlaylist(playlist.id)
        }

        musicDao.clearAllNavidromeSongs()
        dao.clearAllPlaylists()
        _isLoggedInFlow.value = false
    }

    // ─── Playlists ────────────────────────────────────────────────────────

    /**
     * Sync user playlists from server.
     */
    suspend fun syncPlaylists(): Result<List<NavidromePlaylistEntity>> {
        if (!isLoggedIn) {
            return Result.failure(Exception("Not logged in"))
        }

        return withContext(Dispatchers.IO) {
            try {
                Timber.d("$TAG: Syncing playlists")
                val result = api.getPlaylists()

                if (result.isFailure) {
                    return@withContext Result.failure(
                        result.exceptionOrNull() ?: Exception("Failed to get playlists")
                    )
                }

                val jsonObjects = result.getOrThrow()
                val playlists = NavidromeResponseParser.parsePlaylists(jsonObjects)

                // CRITICAL BUG FIX: If we have local playlists but the server returns an empty list,
                // do NOT proceed with syncing or deleting. This is likely a transient error or empty response.
                // We only delete stale playlists if we actually got some data back to compare with.
                if (playlists.isEmpty() && jsonObjects.isNotEmpty()) {
                    Timber.w("$TAG: Parser returned empty playlists but JSON response had items. Parsing error suspected. Aborting.")
                    return@withContext Result.failure(Exception("Playlist parsing error"))
                }

                if (playlists.isEmpty()) {
                    val localCount = dao.getPlaylistCount()
                    if (localCount > 0) {
                        Timber.w("$TAG: Server returned empty playlists but we have $localCount locally. Aborting sync to prevent data loss.")
                        return@withContext Result.success(emptyList()) 
                    }
                }

                val entities = playlists.map { playlist ->
                    NavidromePlaylistEntity(
                        id = playlist.id,
                        name = playlist.name,
                        comment = playlist.comment,
                        owner = playlist.owner,
                        coverArtId = playlist.coverArt,
                        songCount = playlist.songCount,
                        duration = playlist.duration,
                        public = playlist.public,
                        lastSyncTime = System.currentTimeMillis()
                    )
                }

                // Remove stale playlists
                // CRITICAL: Only remove if we successfully fetched at least one playlist OR the fetch was a success but the user has none.
                // Avoid clearing all if it's a transient network error that wasn't caught.
                val localPlaylists = dao.getAllPlaylistsList()
                val remoteIds = entities.map { it.id }.toSet()
                
                // FIXED: If entities is empty, we already handled the protection (localCount > 0) above.
                // However, we must ensure we ONLY delete playlists if the API response was TRULY empty (jsonObjects is empty).
                val stalePlaylists = if (entities.isNotEmpty() || jsonObjects.isEmpty()) {
                    localPlaylists.filter { it.id !in remoteIds }
                } else {
                    emptyList()
                }

                if (stalePlaylists.isNotEmpty()) {
                    Timber.d("$TAG: Removing ${stalePlaylists.size} stale playlists")
                    stalePlaylists.forEach { stale ->
                        dao.deleteSongsByPlaylist(stale.id)
                        dao.deletePlaylist(stale.id)
                        deleteAppPlaylistForNavidromePlaylist(stale.id)
                    }
                }

                // Insert updated playlists
                entities.forEach { dao.insertPlaylist(it) }

                if (stalePlaylists.isNotEmpty()) {
                    syncUnifiedLibrarySongsFromNavidrome()
                }

                Timber.d("$TAG: Synced ${entities.size} playlists")
                Result.success(entities)
            } catch (e: Exception) {
                Timber.e(e, "$TAG: Failed to sync playlists")
                Result.failure(e)
            }
        }
    }

    /**
     * Sync songs in a specific playlist.
     */
    suspend fun syncPlaylistSongs(playlistId: String): Result<Int> {
        return withContext(Dispatchers.IO) {
            try {
                Timber.d("$TAG: Syncing songs for playlist $playlistId")

                val result = api.getPlaylist(playlistId)
                if (result.isFailure) {
                    return@withContext Result.failure(
                        result.exceptionOrNull() ?: Exception("Failed to get playlist")
                    )
                }

                val res: Pair<JSONObject, List<JSONObject>> = result.getOrThrow()
                val songJsons = res.second
                val songs = NavidromeResponseParser.parseSongs(songJsons)

                // CRITICAL BUG FIX: If the server returns empty songs (e.g. failure to parse or server error)
                // but counts are positive, we do NOT empty our local cache.
                if (songs.isEmpty() && songJsons.isNotEmpty()) {
                    Timber.w("$TAG: FAILED to parse songs for playlist $playlistId even though JSON has data. Aborting.")
                    return@withContext Result.failure(Exception("Parsing error"))
                }

                val entities = songs.map { song: NavidromeSong ->
                    song.toEntity(playlistId)
                }

                if (entities.isNotEmpty()) {
                    Timber.d("$TAG: Playlist $playlistId - Deleting old songs, inserting ${entities.size} new songs")
                    dao.deleteSongsByPlaylist(playlistId)
                    dao.insertSongs(entities)
                    
                    // Update app playlist only if we have data
                    val playlistName = dao.getPlaylistById(playlistId)?.name ?: "Playlist"
                    updateAppPlaylistForNavidromePlaylist(playlistId, playlistName, entities)
                } else if (songJsons.isEmpty()) {
                    // This is a TRULY empty playlist on the server.
                    // We should ONLY clear it if we actually got a successful empty list response,
                    // not a parse error.
                    Timber.d("$TAG: Playlist $playlistId is empty on server, clearing local cache")
                    dao.deleteSongsByPlaylist(playlistId)
                    val playlistName = dao.getPlaylistById(playlistId)?.name ?: "Playlist"
                    updateAppPlaylistForNavidromePlaylist(playlistId, playlistName, emptyList())
                } else {
                    Timber.w("$TAG: songJsons was not empty (${songJsons.size}) but entities was empty. Parsing issue?")
                }

                // NOTE: Unified library sync is now handled by the caller (e.g., syncAllPlaylistsAndSongs)
                // to avoid multiple redundant syncs. If you need immediate sync for single playlist,
                // call syncUnifiedLibrarySongsFromNavidrome() after this method.

                Timber.d("$TAG: Synced ${entities.size} songs for playlist $playlistId")
                Result.success(entities.size)
            } catch (e: Exception) {
                Timber.e(e, "$TAG: Failed to sync playlist songs")
                Result.failure(e)
            }
        }
    }

    /**
     * Sync all songs from the server library by fetching all albums.
     */
    suspend fun syncLibrarySongs(): Result<Int> {
        if (!isLoggedIn) {
            return Result.failure(Exception("Not logged in"))
        }

        return withContext(Dispatchers.IO) {
            try {
                Timber.d("$TAG: Syncing library songs from server")
                val allSongs = mutableListOf<NavidromeSong>()
                val pageSize = 500
                val fetchedAlbums = fetchAllAlbums(pageSize)

                // Fetch songs for each album
                for (albumJson in fetchedAlbums) {
                    val albumId = albumJson.optString("id", "")
                    if (albumId.isBlank()) continue

                    val songsResult = api.getAlbum(albumId)
                    songsResult.fold(
                        onSuccess = { songJsons ->
                            val songs = NavidromeResponseParser.parseSongs(songJsons)
                            allSongs.addAll(songs)
                        },
                        onFailure = {
                            Timber.w(it, "$TAG: Failed to fetch songs for album $albumId")
                        }
                    )
                }

                if (allSongs.isEmpty()) {
                    Timber.d("$TAG: No library songs found on server")
                    return@withContext Result.success(0)
                }

                // Deduplicate by song ID
                val uniqueSongs = allSongs.distinctBy { it.id }

                val entities = uniqueSongs.map { song ->
                    song.toEntity(LIBRARY_PLAYLIST_ID)
                }

                // Replace all library songs
                dao.clearLibrarySongs()
                dao.insertSongs(entities)

                Timber.d("$TAG: Synced ${entities.size} library songs from ${fetchedAlbums.size} albums")
                Result.success(entities.size)
            } catch (e: Exception) {
                Timber.e(e, "$TAG: Failed to sync library songs")
                Result.failure(e)
            }
        }
    }

    /**
     * Fetch all albums from server with pagination.
     */
    private suspend fun fetchAllAlbums(pageSize: Int): List<JSONObject> {
        val allAlbums = mutableListOf<JSONObject>()
        var offset = 0

        while (true) {
            val albumsResult = api.getAlbumList(
                type = "alphabeticalByName",
                size = pageSize,
                offset = offset
            )

            val albumJsons = albumsResult.getOrNull()
            if (albumJsons.isNullOrEmpty()) break

            allAlbums.addAll(albumJsons)
            offset += albumJsons.size
            if (albumJsons.size < pageSize) break
        }

        return allAlbums
    }

    /**
     * Sync all playlists and their songs, plus library songs.
     */
    suspend fun syncAllPlaylistsAndSongs(): Result<BulkSyncResult> {
        return withContext(Dispatchers.IO) {
            var syncedSongCount = 0
            var failedPlaylistCount = 0

            // Sync library songs (all albums)
            val libResult = syncLibrarySongs()
            libResult.fold(
                onSuccess = { count -> syncedSongCount += count },
                onFailure = { Timber.w(it, "$TAG: Failed syncing library songs") }
            )

            // Sync playlists
            val playlistResult = syncPlaylists().getOrElse {
                // Playlists failed but library songs may have synced
                try {
                    syncUnifiedLibrarySongsFromNavidrome()
                } catch (e: Exception) {
                    Timber.e(e, "$TAG: Failed to sync unified library after playlist fetch failure")
                }
                return@withContext Result.success(
                    BulkSyncResult(
                        playlistCount = 0,
                        syncedSongCount = syncedSongCount,
                        failedPlaylistCount = 0
                    )
                )
            }

            playlistResult.forEach { playlist ->
                val songSyncResult = syncPlaylistSongs(playlist.id)
                songSyncResult.fold(
                    onSuccess = { count -> syncedSongCount += count },
                    onFailure = {
                        failedPlaylistCount += 1
                        Timber.w(it, "$TAG: Failed syncing playlist ${playlist.id}")
                    }
                )
            }

            // Sync to unified library once after everything is synced
            try {
                syncUnifiedLibrarySongsFromNavidrome()
            } catch (e: Exception) {
                Timber.e(e, "$TAG: Failed to sync unified library")
            }

            Result.success(
                BulkSyncResult(
                    playlistCount = playlistResult.size,
                    syncedSongCount = syncedSongCount,
                    failedPlaylistCount = failedPlaylistCount
                )
            )
        }
    }

    /**
     * Get all playlists as Flow.
     */
    fun getPlaylists(): Flow<List<NavidromePlaylistEntity>> = dao.getAllPlaylists()

    /**
     * Get songs in a playlist as Flow of Song.
     */
    fun getPlaylistSongs(playlistId: String): Flow<List<Song>> {
        return dao.getSongsByPlaylist(playlistId).map { entities ->
            entities.map { it.toSong() }
        }
    }

    /**
     * Get all Navidrome songs as Flow.
     */
    fun getAllSongs(): Flow<List<Song>> {
        return dao.getAllNavidromeSongs().map { entities ->
            entities.map { it.toSong() }
        }
    }

    // ─── Search ────────────────────────────────────────────────────────────

    /**
     * Search for songs on the server.
     */
    suspend fun searchSongs(query: String, limit: Int = 30): Result<List<Song>> {
        if (!isLoggedIn) {
            return Result.failure(Exception("Not logged in"))
        }

        return withContext(Dispatchers.IO) {
            try {
                val result = api.searchSongs(query, count = limit)
                if (result.isFailure) {
                    return@withContext Result.failure(
                        result.exceptionOrNull() ?: Exception("Search failed")
                    )
                }

                val jsonObjects = result.getOrThrow()
                val navidromeSongs = NavidromeResponseParser.parseSongs(jsonObjects)
                val songs = navidromeSongs.map { it.toSong() }

                Result.success(songs)
            } catch (e: Exception) {
                Timber.e(e, "$TAG: Search failed")
                Result.failure(e)
            }
        }
    }

    /**
     * Search local cached songs.
     */
    fun searchLocalSongs(query: String): Flow<List<Song>> {
        return dao.searchSongs(query).map { entities ->
            entities.map { it.toSong() }
        }
    }

    // ─── Media URLs ────────────────────────────────────────────────────────

    /**
     * Get the streaming URL for a song.
     *
     * @param songId The Navidrome song ID
     * @param maxBitRate Maximum bitrate (0 = no limit)
     * @return The streaming URL
     */
    fun getStreamUrl(songId: String, maxBitRate: Int = 0): String {
        return api.getStreamUrl(songId, maxBitRate)
    }

    /**
     * Get the cover art URL for a song/album/artist.
     *
     * @param coverArtId The cover art ID
     * @param size Desired size in pixels
     * @return The cover art URL
     */
    fun getCoverArtUrl(coverArtId: String?, size: Int = 500): String? {
        if (coverArtId.isNullOrBlank()) return null
        return api.getCoverArtUrl(coverArtId, size)
    }

    // ─── Lyrics ────────────────────────────────────────────────────────────

    /**
     * Get lyrics for a song.
     */
    suspend fun getLyrics(songId: String): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                // Try OpenSubsonic extension first
                var result = api.getLyricsBySongId(songId)
                if (result.isSuccess && !result.getOrNull().isNullOrBlank()) {
                    return@withContext result
                }

                // Fallback to standard lyrics API
                val songEntity = dao.getSongByNavidromeId(songId)
                if (songEntity != null) {
                    result = api.getLyrics(songEntity.artist, songEntity.title)
                    if (result.isSuccess && !result.getOrNull().isNullOrBlank()) {
                        return@withContext result
                    }
                }

                Result.failure(Exception("No lyrics found"))
            } catch (e: Exception) {
                Timber.e(e, "$TAG: Failed to get lyrics for song $songId")
                Result.failure(e)
            }
        }
    }

    // ─── Unified Library Sync ──────────────────────────────────────────────

    /**
     * Sync Navidrome songs to the unified music library.
     */
    suspend fun syncUnifiedLibrarySongsFromNavidrome() {
        val navidromeSongs = dao.getAllNavidromeSongsList()
        val existingUnifiedIds = musicDao.getAllNavidromeSongIds()

        if (navidromeSongs.isEmpty()) {
            if (existingUnifiedIds.isNotEmpty()) {
                musicDao.clearAllNavidromeSongs()
            }
            return
        }

        val songs = ArrayList<SongEntity>(navidromeSongs.size)
        val artists = LinkedHashMap<Long, ArtistEntity>()
        val albums = LinkedHashMap<Long, AlbumEntity>()
        val crossRefs = mutableListOf<SongArtistCrossRef>()

        navidromeSongs.forEach { navidromeSong ->
            val songId = toUnifiedSongId(navidromeSong.navidromeId)
            val artistNames = parseArtistNames(navidromeSong.artist)
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

            val albumId = toUnifiedAlbumId(navidromeSong.albumId, navidromeSong.album)
            val albumName = navidromeSong.album.ifBlank { "Unknown Album" }
            albums.putIfAbsent(
                albumId,
                AlbumEntity(
                    id = albumId,
                    title = albumName,
                    artistName = primaryArtistName,
                    artistId = primaryArtistId,
                    songCount = 0,
                    dateAdded = navidromeSong.dateAdded,
                    year = navidromeSong.year,
                    albumArtUriString = navidromeSong.coverArtId?.takeIf { it.isNotBlank() }
                        ?.let { "navidrome_cover://$it" }
                )
            )

            songs.add(
                SongEntity(
                    id = songId,
                    title = navidromeSong.title,
                    artistName = navidromeSong.artist.ifBlank { primaryArtistName },
                    artistId = primaryArtistId,
                    albumArtist = null,
                    albumName = albumName,
                    albumId = albumId,
                    contentUriString = "navidrome://${navidromeSong.navidromeId}",
                    albumArtUriString = navidromeSong.coverArtId?.takeIf { it.isNotBlank() }
                        ?.let { "navidrome_cover://$it" },
                    duration = navidromeSong.duration,
                    genre = navidromeSong.genre ?: NAVIDROME_GENRE,
                    filePath = navidromeSong.path,
                    parentDirectoryPath = NAVIDROME_PARENT_DIRECTORY,
                    isFavorite = false,
                    lyrics = null,
                    trackNumber = navidromeSong.trackNumber,
                    year = navidromeSong.year,
                    dateAdded = navidromeSong.dateAdded.takeIf { it > 0 }
                        ?: System.currentTimeMillis(),
                    mimeType = navidromeSong.mimeType,
                    bitrate = navidromeSong.bitRate,
                    sampleRate = null,
                    telegramChatId = null,
                    telegramFileId = null,
                    sourceType = SourceType.NAVIDROME
                )
            )
        }

        val albumCounts = songs.groupingBy { it.albumId }.eachCount()
        val finalAlbums = albums.values.map { album ->
            album.copy(songCount = albumCounts[album.id] ?: 0)
        }

        val currentUnifiedIds = songs.map { it.id }.toSet()
        val deletedUnifiedIds = existingUnifiedIds.filter { it !in currentUnifiedIds }

        musicDao.incrementalSyncMusicData(
            songs = songs,
            albums = finalAlbums,
            artists = artists.values.toList(),
            crossRefs = crossRefs,
            deletedSongIds = deletedUnifiedIds
        )
    }

    // ─── Utility Methods ───────────────────────────────────────────────────

    private fun parseArtistNames(rawArtist: String): List<String> =
        CloudMusicUtils.parseArtistNames(rawArtist)

    private fun toUnifiedSongId(navidromeId: String): Long {
        return -(NAVIDROME_SONG_ID_OFFSET + navidromeId.hashCode().toLong().absoluteValue)
    }

    private fun toUnifiedAlbumId(albumId: String?, albumName: String): Long {
        val normalized = if (!albumId.isNullOrBlank()) {
            albumId.hashCode().toLong().absoluteValue
        } else {
            albumName.lowercase().hashCode().toLong().absoluteValue
        }
        return -(NAVIDROME_ALBUM_ID_OFFSET + normalized)
    }

    private fun toUnifiedArtistId(artistName: String): Long {
        return -(NAVIDROME_ARTIST_ID_OFFSET + artistName.lowercase().hashCode().toLong().absoluteValue)
    }

    // ─── App Playlist Management ───────────────────────────────────────────

    private suspend fun getAppPlaylistIdForNavidrome(navidromePlaylistId: String): String {
        return "$NAVIDROME_PLAYLIST_PREFIX$navidromePlaylistId"
    }

    private suspend fun updateAppPlaylistForNavidromePlaylist(
        navidromePlaylistId: String,
        playlistName: String,
        navidromeEntities: List<NavidromeSongEntity>
    ) {
        try {
            val unifiedSongIds = navidromeEntities.map { entity ->
                toUnifiedSongId(entity.navidromeId).toString()
            }

            val appPlaylistId = getAppPlaylistIdForNavidrome(navidromePlaylistId)
            val allPlaylists = playlistPreferencesRepository.userPlaylistsFlow
            val existingPlaylist = withContext(Dispatchers.IO) {
                allPlaylists.map { playlists ->
                    playlists.find { it.id == appPlaylistId }
                }.first()
            }

            if (existingPlaylist != null) {
                playlistPreferencesRepository.updatePlaylist(
                    existingPlaylist.copy(
                        name = playlistName,
                        songIds = unifiedSongIds,
                        lastModified = System.currentTimeMillis(),
                        source = "NAVIDROME"
                    )
                )
                Timber.d("$TAG: Updated app playlist for Navidrome playlist $navidromePlaylistId")
            } else {
                playlistPreferencesRepository.createPlaylist(
                    name = playlistName,
                    songIds = unifiedSongIds,
                    customId = appPlaylistId,
                    source = "NAVIDROME"
                )
                Timber.d("$TAG: Created app playlist for Navidrome playlist $navidromePlaylistId")
            }
        } catch (e: Exception) {
            Timber.e(e, "$TAG: Failed to update app playlist for Navidrome playlist $navidromePlaylistId")
        }
    }

    private suspend fun deleteAppPlaylistForNavidromePlaylist(navidromePlaylistId: String) {
        try {
            val appPlaylistId = getAppPlaylistIdForNavidrome(navidromePlaylistId)
            playlistPreferencesRepository.deletePlaylist(appPlaylistId)
            Timber.d("$TAG: Deleted app playlist for Navidrome playlist $navidromePlaylistId")
        } catch (e: Exception) {
            Timber.w(e, "$TAG: Failed to delete app playlist for Navidrome playlist $navidromePlaylistId")
        }
    }

    // ─── Delete ────────────────────────────────────────────────────────────

    suspend fun deletePlaylist(playlistId: String) {
        dao.deleteSongsByPlaylist(playlistId)
        dao.deletePlaylist(playlistId)
        deleteAppPlaylistForNavidromePlaylist(playlistId)
        syncUnifiedLibrarySongsFromNavidrome()
    }
}

// ─── Extension Functions ────────────────────────────────────────────────────

/**
 * Convert a NavidromeSong to a Song model.
 */
fun NavidromeSong.toSong(): Song {
    return Song(
        id = "navidrome_$id",
        title = title,
        artist = artist,
        artistId = -1L,
        album = album,
        albumId = -1L,
        path = path,
        contentUriString = "navidrome://$id",
        albumArtUriString = coverArt?.let { "navidrome_cover://$it" },
        duration = duration,
        genre = genre,
        mimeType = resolvedMimeType,
        bitrate = bitRate,
        sampleRate = null,
        year = year,
        trackNumber = trackNumber,
        dateAdded = System.currentTimeMillis(),
        isFavorite = false
    )
}
