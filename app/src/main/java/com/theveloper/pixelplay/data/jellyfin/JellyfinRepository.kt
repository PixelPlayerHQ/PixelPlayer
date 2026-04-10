package com.theveloper.pixelplay.data.jellyfin

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.theveloper.pixelplay.data.database.AlbumEntity
import com.theveloper.pixelplay.data.database.ArtistEntity
import com.theveloper.pixelplay.data.database.JellyfinDao
import com.theveloper.pixelplay.data.database.JellyfinPlaylistEntity
import com.theveloper.pixelplay.data.database.JellyfinSongEntity
import com.theveloper.pixelplay.data.database.MusicDao
import com.theveloper.pixelplay.data.database.SongArtistCrossRef
import com.theveloper.pixelplay.data.database.SongEntity
import com.theveloper.pixelplay.data.database.SourceType
import com.theveloper.pixelplay.data.database.toEntity
import com.theveloper.pixelplay.data.database.toSong
import com.theveloper.pixelplay.data.jellyfin.model.JellyfinCredentials
import com.theveloper.pixelplay.data.jellyfin.model.JellyfinSong
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.network.jellyfin.JellyfinApiService
import com.theveloper.pixelplay.data.network.jellyfin.JellyfinResponseParser
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
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.absoluteValue

@Singleton
class JellyfinRepository @Inject constructor(
    private val api: JellyfinApiService,
    private val dao: JellyfinDao,
    private val musicDao: MusicDao,
    private val playlistPreferencesRepository: PlaylistPreferencesRepository,
    @ApplicationContext private val context: Context
) {
    private companion object {
        private const val TAG = "JellyfinRepo"
        private const val PREFS_NAME = "jellyfin_prefs"
        private const val KEY_SERVER_URL = "server_url"
        private const val KEY_USERNAME = "username"
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_USER_ID = "user_id"

        private const val JELLYFIN_SONG_ID_OFFSET = 12_000_000_000_000L
        private const val JELLYFIN_ALBUM_ID_OFFSET = 13_000_000_000_000L
        private const val JELLYFIN_ARTIST_ID_OFFSET = 14_000_000_000_000L
        private const val JELLYFIN_PARENT_DIRECTORY = "/Cloud/Jellyfin"
        private const val JELLYFIN_GENRE = "Jellyfin"
        private const val JELLYFIN_PLAYLIST_PREFIX = "jellyfin_playlist:"
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

    private fun initFromSavedCredentials() {
        val serverUrl = prefs.getString(KEY_SERVER_URL, null)
        val username = prefs.getString(KEY_USERNAME, null)
        val accessToken = prefs.getString(KEY_ACCESS_TOKEN, null)
        val userId = prefs.getString(KEY_USER_ID, null)

        if (!serverUrl.isNullOrBlank() && !username.isNullOrBlank() &&
            !accessToken.isNullOrBlank() && !userId.isNullOrBlank()) {
            val credentials = JellyfinCredentials(serverUrl, username, "", accessToken, userId)
            val validationError = credentials.connectionValidationError()
            if (validationError != null) {
                Timber.w("$TAG: Ignoring invalid saved Jellyfin server URL: $validationError")
                api.clearCredentials()
                _isLoggedInFlow.value = false
                return
            }
            api.setCredentials(credentials)
            _isLoggedInFlow.value = true
            Timber.d("$TAG: Restored credentials for $username@${credentials.normalizedServerUrl}")
        }
    }

    val isLoggedIn: Boolean
        get() = _isLoggedInFlow.value

    val serverUrl: String?
        get() = prefs.getString(KEY_SERVER_URL, null)

    fun getAuthorizationHeader(): String? = api.getAuthorizationHeader()

    val username: String?
        get() = prefs.getString(KEY_USERNAME, null)

    suspend fun login(serverUrl: String, username: String, password: String): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                Timber.d("$TAG: Attempting login to $serverUrl as $username")

                val credentials = JellyfinCredentials(serverUrl, username, password)
                val validationError = credentials.connectionValidationError()
                if (validationError != null) {
                    api.clearCredentials()
                    return@withContext Result.failure(IllegalArgumentException(validationError))
                }

                // Authenticate and get token
                val authResult = api.authenticateByName(
                    credentials.normalizedServerUrl, username, password
                )
                if (authResult.isFailure) {
                    api.clearCredentials()
                    return@withContext Result.failure(
                        authResult.exceptionOrNull() ?: Exception("Authentication failed")
                    )
                }

                val (accessToken, userId) = authResult.getOrThrow()
                val fullCredentials = credentials.copy(accessToken = accessToken, userId = userId)
                api.setCredentials(fullCredentials)

                // Save credentials (password is never persisted — token is sufficient)
                prefs.edit()
                    .putString(KEY_SERVER_URL, fullCredentials.normalizedServerUrl)
                    .putString(KEY_USERNAME, username)
                    .putString(KEY_ACCESS_TOKEN, accessToken)
                    .putString(KEY_USER_ID, userId)
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

    suspend fun logout() {
        Timber.d("$TAG: Logging out")
        api.clearCredentials()
        prefs.edit().clear().apply()

        val playlistsToDelete = dao.getAllPlaylistsList()
        playlistsToDelete.forEach { playlist ->
            dao.deleteSongsByPlaylist(playlist.id)
            deleteAppPlaylistForJellyfinPlaylist(playlist.id)
        }

        musicDao.clearAllJellyfinSongs()
        dao.clearAllPlaylists()
        _isLoggedInFlow.value = false
    }

    // ─── Playlists ────────────────────────────────────────────────────────

    suspend fun syncPlaylists(): Result<List<JellyfinPlaylistEntity>> {
        if (!isLoggedIn) return Result.failure(Exception("Not logged in"))

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
                val playlists = JellyfinResponseParser.parsePlaylists(jsonObjects)

                if (playlists.isEmpty() && jsonObjects.isNotEmpty()) {
                    Timber.w("$TAG: Parser returned empty playlists but JSON had items. Aborting.")
                    return@withContext Result.failure(Exception("Playlist parsing error"))
                }

                if (playlists.isEmpty()) {
                    val localCount = dao.getPlaylistCount()
                    if (localCount > 0) {
                        Timber.w("$TAG: Server returned empty playlists but we have $localCount locally. Aborting sync.")
                        return@withContext Result.success(emptyList())
                    }
                }

                val entities = playlists.map { playlist ->
                    JellyfinPlaylistEntity(
                        id = playlist.id,
                        name = playlist.name,
                        songCount = playlist.songCount,
                        duration = playlist.duration,
                        lastSyncTime = System.currentTimeMillis()
                    )
                }

                val localPlaylists = dao.getAllPlaylistsList()
                val remoteIds = entities.map { it.id }.toSet()
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
                        deleteAppPlaylistForJellyfinPlaylist(stale.id)
                    }
                }

                entities.forEach { dao.insertPlaylist(it) }

                if (stalePlaylists.isNotEmpty()) {
                    syncUnifiedLibrarySongsFromJellyfin()
                }

                Timber.d("$TAG: Synced ${entities.size} playlists")
                Result.success(entities)
            } catch (e: Exception) {
                Timber.e(e, "$TAG: Failed to sync playlists")
                Result.failure(e)
            }
        }
    }

    suspend fun syncPlaylistSongs(playlistId: String): Result<Int> {
        return withContext(Dispatchers.IO) {
            try {
                Timber.d("$TAG: Syncing songs for playlist $playlistId")
                val result = api.getPlaylistItems(playlistId)
                if (result.isFailure) {
                    return@withContext Result.failure(
                        result.exceptionOrNull() ?: Exception("Failed to get playlist")
                    )
                }

                val songJsons = result.getOrThrow()
                val songs = JellyfinResponseParser.parseSongs(songJsons)

                if (songs.isEmpty() && songJsons.isNotEmpty()) {
                    Timber.w("$TAG: FAILED to parse songs for playlist $playlistId. Aborting.")
                    return@withContext Result.failure(Exception("Parsing error"))
                }

                val entities = songs.map { song: JellyfinSong ->
                    song.toEntity(playlistId)
                }

                if (entities.isNotEmpty()) {
                    dao.deleteSongsByPlaylist(playlistId)
                    dao.insertSongs(entities)
                    val playlistName = dao.getPlaylistById(playlistId)?.name ?: "Playlist"
                    updateAppPlaylistForJellyfinPlaylist(playlistId, playlistName, entities)
                } else if (songJsons.isEmpty()) {
                    dao.deleteSongsByPlaylist(playlistId)
                    val playlistName = dao.getPlaylistById(playlistId)?.name ?: "Playlist"
                    updateAppPlaylistForJellyfinPlaylist(playlistId, playlistName, emptyList())
                }

                Timber.d("$TAG: Synced ${entities.size} songs for playlist $playlistId")
                Result.success(entities.size)
            } catch (e: Exception) {
                Timber.e(e, "$TAG: Failed to sync playlist songs")
                Result.failure(e)
            }
        }
    }

    suspend fun syncLibrarySongs(): Result<Int> {
        if (!isLoggedIn) return Result.failure(Exception("Not logged in"))

        return withContext(Dispatchers.IO) {
            try {
                Timber.d("$TAG: Syncing library songs from server")
                val allSongs = mutableListOf<JellyfinSong>()
                val pageSize = 500
                var startIndex = 0

                while (true) {
                    val result = api.getMusicItems(startIndex = startIndex, limit = pageSize)
                    val (_, items) = result.getOrNull() ?: break
                    if (items.isEmpty()) break

                    val songs = JellyfinResponseParser.parseSongs(items)
                    allSongs.addAll(songs)
                    startIndex += items.size
                    if (items.size < pageSize) break
                }

                if (allSongs.isEmpty()) {
                    Timber.d("$TAG: No library songs found on server")
                    return@withContext Result.success(0)
                }

                val uniqueSongs = allSongs.distinctBy { it.id }
                val entities = uniqueSongs.map { song -> song.toEntity(LIBRARY_PLAYLIST_ID) }

                dao.clearLibrarySongs()
                dao.insertSongs(entities)

                Timber.d("$TAG: Synced ${entities.size} library songs")
                Result.success(entities.size)
            } catch (e: Exception) {
                Timber.e(e, "$TAG: Failed to sync library songs")
                Result.failure(e)
            }
        }
    }

    suspend fun syncAllPlaylistsAndSongs(): Result<BulkSyncResult> {
        return withContext(Dispatchers.IO) {
            var syncedSongCount = 0
            var failedPlaylistCount = 0

            val libResult = syncLibrarySongs()
            libResult.fold(
                onSuccess = { count -> syncedSongCount += count },
                onFailure = { Timber.w(it, "$TAG: Failed syncing library songs") }
            )

            val playlistResult = syncPlaylists().getOrElse {
                try {
                    syncUnifiedLibrarySongsFromJellyfin()
                } catch (e: Exception) {
                    Timber.e(e, "$TAG: Failed to sync unified library after playlist fetch failure")
                }
                return@withContext Result.success(
                    BulkSyncResult(playlistCount = 0, syncedSongCount = syncedSongCount, failedPlaylistCount = 0)
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

            try {
                syncUnifiedLibrarySongsFromJellyfin()
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

    fun getPlaylists(): Flow<List<JellyfinPlaylistEntity>> = dao.getAllPlaylists()

    fun getPlaylistSongs(playlistId: String): Flow<List<Song>> {
        return dao.getSongsByPlaylist(playlistId).map { entities ->
            entities.map { it.toSong() }
        }
    }

    suspend fun deletePlaylist(playlistId: String) {
        dao.clearSongsByPlaylist(playlistId)
        dao.deletePlaylist(playlistId)
        syncUnifiedLibrarySongsFromJellyfin()
    }

    fun getAllSongs(): Flow<List<Song>> {
        return dao.getAllJellyfinSongs().map { entities ->
            entities.map { it.toSong() }
        }
    }

    // ─── Search ────────────────────────────────────────────────────────────

    suspend fun searchSongs(query: String, limit: Int = 30): Result<List<Song>> {
        if (!isLoggedIn) return Result.failure(Exception("Not logged in"))

        return withContext(Dispatchers.IO) {
            try {
                val result = api.searchSongs(query, limit)
                if (result.isFailure) {
                    return@withContext Result.failure(
                        result.exceptionOrNull() ?: Exception("Search failed")
                    )
                }
                val jellyfinSongs = JellyfinResponseParser.parseSongs(result.getOrThrow())
                Result.success(jellyfinSongs.map { it.toDisplaySong() })
            } catch (e: Exception) {
                Timber.e(e, "$TAG: Search failed")
                Result.failure(e)
            }
        }
    }

    fun searchLocalSongs(query: String): Flow<List<Song>> {
        return dao.searchSongs(query).map { entities ->
            entities.map { it.toSong() }
        }
    }

    // ─── Media URLs ────────────────────────────────────────────────────────

    fun getStreamUrl(songId: String, maxBitRate: Int = 0): String {
        return api.getStreamUrl(songId, maxBitRate)
    }

    fun getImageUrl(itemId: String?, size: Int = 500): String? {
        if (itemId.isNullOrBlank()) return null
        return api.getImageUrl(itemId, maxWidth = size)
    }

    // ─── Lyrics ────────────────────────────────────────────────────────────

    suspend fun getLyrics(songId: String): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                val result = api.getLyrics(songId)
                if (result.isSuccess && !result.getOrNull().isNullOrBlank()) {
                    return@withContext result
                }
                Result.failure(Exception("No lyrics found"))
            } catch (e: Exception) {
                Timber.e(e, "$TAG: Failed to get lyrics for song $songId")
                Result.failure(e)
            }
        }
    }

    // ─── Unified Library Sync ──────────────────────────────────────────────

    suspend fun syncUnifiedLibrarySongsFromJellyfin() {
        val jellyfinSongs = dao.getAllJellyfinSongsList()
        val existingUnifiedIds = musicDao.getAllJellyfinSongIds()

        if (jellyfinSongs.isEmpty()) {
            if (existingUnifiedIds.isNotEmpty()) {
                musicDao.clearAllJellyfinSongs()
            }
            return
        }

        val songs = ArrayList<SongEntity>(jellyfinSongs.size)
        val artists = LinkedHashMap<Long, ArtistEntity>()
        val albums = LinkedHashMap<Long, AlbumEntity>()
        val crossRefs = mutableListOf<SongArtistCrossRef>()

        jellyfinSongs.forEach { jellyfinSong ->
            val songId = toUnifiedSongId(jellyfinSong.jellyfinId)
            val artistNames = parseArtistNames(jellyfinSong.artist)
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

            val albumId = toUnifiedAlbumId(jellyfinSong.albumId, jellyfinSong.album)
            val albumName = jellyfinSong.album.ifBlank { "Unknown Album" }
            albums.putIfAbsent(
                albumId,
                AlbumEntity(
                    id = albumId,
                    title = albumName,
                    artistName = primaryArtistName,
                    artistId = primaryArtistId,
                    songCount = 0,
                    dateAdded = jellyfinSong.dateAdded,
                    year = jellyfinSong.year,
                    albumArtUriString = "jellyfin_cover://${jellyfinSong.jellyfinId}"
                )
            )

            songs.add(
                SongEntity(
                    id = songId,
                    title = jellyfinSong.title,
                    artistName = jellyfinSong.artist.ifBlank { primaryArtistName },
                    artistId = primaryArtistId,
                    albumArtist = null,
                    albumName = albumName,
                    albumId = albumId,
                    contentUriString = "jellyfin://${jellyfinSong.jellyfinId}",
                    albumArtUriString = "jellyfin_cover://${jellyfinSong.jellyfinId}",
                    duration = jellyfinSong.duration,
                    genre = jellyfinSong.genre ?: JELLYFIN_GENRE,
                    filePath = jellyfinSong.path,
                    parentDirectoryPath = JELLYFIN_PARENT_DIRECTORY,
                    isFavorite = false,
                    lyrics = null,
                    trackNumber = jellyfinSong.trackNumber,
                    year = jellyfinSong.year,
                    dateAdded = jellyfinSong.dateAdded.takeIf { it > 0 }
                        ?: System.currentTimeMillis(),
                    mimeType = jellyfinSong.mimeType,
                    bitrate = jellyfinSong.bitRate,
                    sampleRate = null,
                    telegramChatId = null,
                    telegramFileId = null,
                    sourceType = SourceType.JELLYFIN
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

    private fun toUnifiedSongId(jellyfinId: String): Long {
        return -(JELLYFIN_SONG_ID_OFFSET + jellyfinId.hashCode().toLong().absoluteValue)
    }

    private fun toUnifiedAlbumId(albumId: String?, albumName: String): Long {
        val normalized = if (!albumId.isNullOrBlank()) {
            albumId.hashCode().toLong().absoluteValue
        } else {
            albumName.lowercase().hashCode().toLong().absoluteValue
        }
        return -(JELLYFIN_ALBUM_ID_OFFSET + normalized)
    }

    private fun toUnifiedArtistId(artistName: String): Long {
        return -(JELLYFIN_ARTIST_ID_OFFSET + artistName.lowercase().hashCode().toLong().absoluteValue)
    }

    // ─── App Playlist Management ───────────────────────────────────────────

    private suspend fun updateAppPlaylistForJellyfinPlaylist(
        jellyfinPlaylistId: String,
        playlistName: String,
        songs: List<JellyfinSongEntity>
    ) {
        val appPlaylistId = "$JELLYFIN_PLAYLIST_PREFIX$jellyfinPlaylistId"
        val songIds = songs.map { toUnifiedSongId(it.jellyfinId).toString() }

        val existingPlaylist = withContext(Dispatchers.IO) {
            playlistPreferencesRepository.userPlaylistsFlow.map { playlists ->
                playlists.find { it.id == appPlaylistId }
            }.first()
        }

        if (existingPlaylist != null) {
            playlistPreferencesRepository.updatePlaylist(
                existingPlaylist.copy(
                    name = playlistName,
                    songIds = songIds,
                    lastModified = System.currentTimeMillis(),
                    source = "JELLYFIN"
                )
            )
        } else {
            playlistPreferencesRepository.createPlaylist(
                name = playlistName,
                songIds = songIds,
                customId = appPlaylistId,
                source = "JELLYFIN"
            )
        }
    }

    private suspend fun deleteAppPlaylistForJellyfinPlaylist(jellyfinPlaylistId: String) {
        val appPlaylistId = "$JELLYFIN_PLAYLIST_PREFIX$jellyfinPlaylistId"
        playlistPreferencesRepository.deletePlaylist(appPlaylistId)
    }

    private fun JellyfinSong.toDisplaySong(): Song {
        return Song(
            id = "jellyfin_search_$id",
            title = title,
            artist = artist,
            artistId = -1L,
            album = album,
            albumId = -1L,
            path = path,
            contentUriString = "jellyfin://$id",
            albumArtUriString = "jellyfin_cover://$id",
            duration = duration,
            genre = genre,
            mimeType = resolvedMimeType,
            bitrate = bitRate,
            sampleRate = null,
            year = year,
            trackNumber = trackNumber,
            dateAdded = 0,
            isFavorite = false
        )
    }
}
