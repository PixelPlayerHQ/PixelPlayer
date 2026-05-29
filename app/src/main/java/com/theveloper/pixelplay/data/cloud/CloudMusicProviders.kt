@file:Suppress("DEPRECATION")

package com.theveloper.pixelplay.data.cloud

/**
 * Consolidated home for every remote/cloud music source in the app:
 * the shared [RemoteMusicProvider] seam + registry, and the five provider
 * stacks (repository + local stream proxy) for Jellyfin, Navidrome, Netease,
 * QQ Music and Telegram.
 *
 * Support types stay in their own packages: API services in `data.network.*`,
 * DAOs in `data.database`, DTOs/models in `data.*.model`, and Telegram
 * client/cache managers in `data.telegram`.
 */
import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.util.Base64
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.theveloper.pixelplay.data.database.AlbumEntity
import com.theveloper.pixelplay.data.database.ArtistEntity
import com.theveloper.pixelplay.data.database.JellyfinDao
import com.theveloper.pixelplay.data.database.JellyfinPlaylistEntity
import com.theveloper.pixelplay.data.database.JellyfinSongEntity
import com.theveloper.pixelplay.data.database.MusicDao
import com.theveloper.pixelplay.data.database.NavidromeDao
import com.theveloper.pixelplay.data.database.NavidromePlaylistEntity
import com.theveloper.pixelplay.data.database.NavidromeSongEntity
import com.theveloper.pixelplay.data.database.NeteaseDao
import com.theveloper.pixelplay.data.database.NeteasePlaylistEntity
import com.theveloper.pixelplay.data.database.NeteaseSongEntity
import com.theveloper.pixelplay.data.database.QqMusicDao
import com.theveloper.pixelplay.data.database.QqMusicPlaylistEntity
import com.theveloper.pixelplay.data.database.QqMusicSongEntity
import com.theveloper.pixelplay.data.database.SongArtistCrossRef
import com.theveloper.pixelplay.data.database.SongEntity
import com.theveloper.pixelplay.data.database.SourceType
import com.theveloper.pixelplay.data.database.TelegramDao
import com.theveloper.pixelplay.data.database.TelegramSongEntity
import com.theveloper.pixelplay.data.database.TelegramTopicEntity
import com.theveloper.pixelplay.data.database.toEntity
import com.theveloper.pixelplay.data.database.toSong
import com.theveloper.pixelplay.data.jellyfin.model.JellyfinCredentials
import com.theveloper.pixelplay.data.jellyfin.model.JellyfinSong
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.navidrome.model.NavidromeCredentials
import com.theveloper.pixelplay.data.navidrome.model.NavidromeSong
import com.theveloper.pixelplay.data.network.jellyfin.JellyfinApiService
import com.theveloper.pixelplay.data.network.jellyfin.JellyfinResponseParser
import com.theveloper.pixelplay.data.network.navidrome.NavidromeApiService
import com.theveloper.pixelplay.data.network.navidrome.NavidromeResponseParser
import com.theveloper.pixelplay.data.network.netease.NeteaseApiService
import com.theveloper.pixelplay.data.network.qqmusic.QqMusicApiService
import com.theveloper.pixelplay.data.preferences.PlaylistPreferencesRepository
import com.theveloper.pixelplay.data.preferences.UserPreferencesRepository
import com.theveloper.pixelplay.data.stream.BulkSyncResult
import com.theveloper.pixelplay.data.stream.CloudMusicUtils
import com.theveloper.pixelplay.data.stream.CloudStreamProxy
import com.theveloper.pixelplay.data.stream.CloudStreamSecurity
import com.theveloper.pixelplay.data.telegram.TelegramClientManager
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.utils.LogUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.cio.CIO
import io.ktor.server.cio.CIOApplicationEngine
import io.ktor.server.engine.embeddedServer
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.EngineConnectorBuilder
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytesWriter
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.utils.io.writeFully
import java.io.File
import java.io.RandomAccessFile
import java.net.ServerSocket
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.absoluteValue
import kotlin.math.min
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.isActive
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import org.drinkless.tdlib.TdApi
import org.json.JSONObject
import timber.log.Timber


// ═══════════════════════════════════════════════════════════════════════════
// RemoteMusicProvider.kt
// ═══════════════════════════════════════════════════════════════════════════


/**
 * Shared seam over the cloud/remote music sources (Jellyfin, Navidrome, Netease,
 * QQ Music, Telegram).
 *
 * Each source historically grew its own repository with an almost-identical
 * "auth → fetch → map → stream" shape but no common type. These interfaces make
 * that shared shape explicit *where it is genuinely shared* and nowhere else.
 *
 * The design is intentionally **composed capabilities** rather than one fat
 * interface: a provider implements [RemoteMusicProvider] (so it can be discovered
 * by scheme) plus only the capability interfaces it can honestly satisfy. There
 * are no no-op / `UnsupportedOperationException` methods.
 *
 * Capability matrix:
 *
 * | Capability                 | Jellyfin | Navidrome | Netease | QQ Music | Telegram |
 * |----------------------------|:--------:|:---------:|:-------:|:--------:|:--------:|
 * | [RemoteMusicProvider]      |    ✓     |     ✓     |    ✓    |    ✓     |    ✓     |
 * | [RemoteAuthState]          |    ✓     |     ✓     |    ✓    |    ✓     |          |
 * | [CredentialAuthProvider]   |    ✓     |     ✓     |         |          |          |
 * | [CookieAuthProvider]       |          |           |    ✓    |    ✓     |          |
 * | [SyncableLibraryProvider]  |    ✓     |     ✓     |    ✓    |    ✓     |          |
 * | [RemoteSongSearch]         |    ✓     |     ✓     |    ✓    |          |          |
 *
 * Telegram joins the family only through the base marker: its auth (TDLib phone
 * flow), library (per-chat message scans) and streaming (fileId-based file
 * download, not an HTTP URL) are too divergent to share honestly.
 *
 * Stream-URL resolution deliberately lives **one layer down** in
 * [com.theveloper.pixelplay.data.stream.CloudStreamProxy], which already abstracts
 * it for the URL-based providers; the repository-side shapes (sync `String` vs
 * suspend `Result<String>` vs Telegram's fileId download) are too different to
 * unify here.
 */
interface RemoteMusicProvider {
    /**
     * The URI scheme this provider owns (e.g. `"jellyfin"`, `"telegram"`).
     *
     * Matches the scheme used in `Song.contentUriString` and the scheme dispatch
     * in `DualPlayerEngine`. Used as the registry key in
     * [RemoteMusicProviderRegistry].
     */
    val scheme: String

    /** Human-readable name for account/settings UI (e.g. `"QQ Music"`). */
    val displayName: String
}

/**
 * A provider that maintains an observable logged-in / connected state and can be
 * signed out. Implemented by the four credential/cookie HTTP providers; Telegram
 * tracks its own TDLib authorization state instead.
 */
interface RemoteAuthState {
    val isLoggedInFlow: StateFlow<Boolean>
    val isLoggedIn: Boolean
    suspend fun logout()
}

/** Username/password-against-a-server authentication (Jellyfin, Navidrome). */
interface CredentialAuthProvider {
    val serverUrl: String?
    val username: String?
    suspend fun login(serverUrl: String, username: String, password: String): Result<String>
}

/** Cookie-jar authentication imported from a browser session (Netease, QQ Music). */
interface CookieAuthProvider {
    val userNickname: String?
    fun initFromSavedCookies()

    /** @return the resolved account display name on success. */
    suspend fun loginWithCookies(cookieJson: String): Result<String>
}

/**
 * A provider whose entire library (playlists + their songs) can be synced into the
 * local database in one bulk operation.
 */
interface SyncableLibraryProvider {
    suspend fun syncAllPlaylistsAndSongs(): Result<BulkSyncResult>
}

/** A provider that supports online (server-side) song search. */
interface RemoteSongSearch {
    suspend fun searchSongs(query: String, limit: Int = 30): Result<List<Song>>
}

// ═══════════════════════════════════════════════════════════════════════════
// RemoteMusicProviderRegistry.kt
// ═══════════════════════════════════════════════════════════════════════════


/**
 * Single point of access to every [RemoteMusicProvider] in the app, keyed by its
 * [scheme][RemoteMusicProvider.scheme].
 *
 * Providers are contributed via Hilt multibindings (see
 * `di.RemoteMusicProviderModule`), so adding a new remote source is a one-line
 * `@Binds @IntoSet` change with no edits here.
 *
 * Callers that only need a specific capability filter for it, e.g.
 * `registry.capable<SyncableLibraryProvider>()` to sync every account, or
 * `registry.authState(scheme)` to drive a logout button.
 */
@Singleton
class RemoteMusicProviderRegistry @Inject constructor(
    providers: Set<@JvmSuppressWildcards RemoteMusicProvider>
) {
    private val byScheme: Map<String, RemoteMusicProvider> =
        providers.associateBy { it.scheme }

    /** Every registered provider. */
    val all: List<RemoteMusicProvider> = byScheme.values.toList()

    /** The provider that owns [scheme] (e.g. from a `Song.contentUriString`), or null. */
    fun forScheme(scheme: String?): RemoteMusicProvider? = scheme?.let { byScheme[it] }

    /** All providers that implement capability [T]. */
    inline fun <reified T> capable(): List<T> = all.filterIsInstance<T>()

    /** The provider for [scheme], if it exposes login/logout state. */
    fun authState(scheme: String?): RemoteAuthState? = forScheme(scheme) as? RemoteAuthState
}

// ═══════════════════════════════════════════════════════════════════════════
// JellyfinRepository.kt
// ═══════════════════════════════════════════════════════════════════════════


@Suppress("DEPRECATION")
@Singleton
class JellyfinRepository @Inject constructor(
    private val api: JellyfinApiService,
    private val dao: JellyfinDao,
    private val musicDao: MusicDao,
    private val playlistPreferencesRepository: PlaylistPreferencesRepository,
    @ApplicationContext private val context: Context
) : RemoteMusicProvider,
    RemoteAuthState,
    CredentialAuthProvider,
    SyncableLibraryProvider,
    RemoteSongSearch {
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

    override val scheme: String = "jellyfin"
    override val displayName: String = "Jellyfin"

    private val _isLoggedInFlow = MutableStateFlow(false)
    override val isLoggedInFlow: StateFlow<Boolean> = _isLoggedInFlow.asStateFlow()

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

    override val isLoggedIn: Boolean
        get() = _isLoggedInFlow.value

    override val serverUrl: String?
        get() = prefs.getString(KEY_SERVER_URL, null)

    fun getAuthorizationHeader(): String? = api.getAuthorizationHeader()

    override val username: String?
        get() = prefs.getString(KEY_USERNAME, null)

    override suspend fun login(serverUrl: String, username: String, password: String): Result<String> {
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

    override suspend fun logout() {
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

    override suspend fun syncAllPlaylistsAndSongs(): Result<BulkSyncResult> {
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

    override suspend fun searchSongs(query: String, limit: Int): Result<List<Song>> {
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
                    bitrate = jellyfinSong.bitRate?.let { it * 1000 },
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
            bitrate = bitRate?.let { it * 1000 },
            sampleRate = null,
            year = year,
            trackNumber = trackNumber,
            dateAdded = 0,
            isFavorite = false
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// JellyfinStreamProxy.kt
// ═══════════════════════════════════════════════════════════════════════════


@Singleton
class JellyfinStreamProxy @Inject constructor(
    private val repository: JellyfinRepository,
    okHttpClient: OkHttpClient
) : CloudStreamProxy<String>(okHttpClient) {

    override val allowedHostSuffixes: Set<String>
        get() = repository.serverUrl?.toHttpUrlOrNull()?.host?.let { setOf(it) } ?: emptySet()

    override val cacheExpirationMs = 30L * 60 * 1000

    override val proxyTag = "JellyfinStreamProxy"
    override val routePath = "/jellyfin/{itemId}"
    override val routeParamName = "itemId"
    override val uriScheme = "jellyfin"
    override val routePrefix = "/jellyfin"

    override fun parseRouteParam(value: String): String? =
        value.takeIf { it.isNotBlank() }

    override fun validateId(id: String): Boolean =
        CloudStreamSecurity.validateJellyfinItemId(id)

    override fun formatIdForUrl(id: String): String = id

    override suspend fun resolveStreamUrl(id: String): String? {
        return try {
            repository.getStreamUrl(id)
        } catch (e: Exception) {
            Timber.w(e, "JellyfinStreamProxy: Failed to resolve stream URL for item $id")
            null
        }
    }

    override fun extractIdFromUri(uri: Uri): String? =
        uri.host ?: uri.path?.removePrefix("/")

    fun resolveJellyfinUri(uriString: String): String? = resolveUri(uriString)

    suspend fun warmUpStreamUrl(uriString: String) {
        val uri = Uri.parse(uriString)
        if (uri.scheme != "jellyfin") return
        val itemId = uri.host ?: uri.path?.removePrefix("/") ?: return
        if (!CloudStreamSecurity.validateJellyfinItemId(itemId)) return
        try {
            getOrFetchStreamUrl(itemId)
        } catch (e: Exception) {
            Timber.w(e, "warmUpStreamUrl failed for $itemId")
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// NavidromeRepository.kt
// ═══════════════════════════════════════════════════════════════════════════


/**
 * Repository for Navidrome/Subsonic music service.
 *
 * Manages authentication, playlist synchronization, and song caching.
 */
@Suppress("DEPRECATION")
@Singleton
class NavidromeRepository @Inject constructor(
    private val api: NavidromeApiService,
    private val dao: NavidromeDao,
    private val musicDao: MusicDao,
    private val playlistPreferencesRepository: PlaylistPreferencesRepository,
    @ApplicationContext private val context: Context
) : RemoteMusicProvider,
    RemoteAuthState,
    CredentialAuthProvider,
    SyncableLibraryProvider,
    RemoteSongSearch {
    companion object {
        const val SYNC_THRESHOLD_MS = 24 * 60 * 60 * 1000L // 24 hours
        private const val TAG = "NavidromeRepo"
        private const val PREFS_NAME = "navidrome_prefs"
        private const val KEY_SERVER_URL = "server_url"
        private const val KEY_USERNAME = "username"
        private const val KEY_PASSWORD = "password"
        private const val KEY_LAST_FULL_SYNC = "last_full_sync"

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

    override val scheme: String = "navidrome"
    override val displayName: String = "Navidrome"

    private val _isLoggedInFlow = MutableStateFlow(false)
    override val isLoggedInFlow: StateFlow<Boolean> = _isLoggedInFlow.asStateFlow()

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
    override val isLoggedIn: Boolean
        get() = _isLoggedInFlow.value

    /**
     * Get the current server URL.
     */
    override val serverUrl: String?
        get() = prefs.getString(KEY_SERVER_URL, null)

    /**
     * Get the current username.
     */
    override val username: String?
        get() = prefs.getString(KEY_USERNAME, null)

    var lastFullSyncTime: Long
        get() = prefs.getLong(KEY_LAST_FULL_SYNC, 0L)
        set(value) = prefs.edit { putLong(KEY_LAST_FULL_SYNC, value) }

    /**
     * Login to Navidrome server with credentials.
     *
     * @param serverUrl The server URL (e.g., "https://music.example.com")
     * @param username The username
     * @param password The password
     * @return Result with username on success, error on failure
     */
    override suspend fun login(serverUrl: String, username: String, password: String): Result<String> {
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
                prefs.edit {
                    putString(KEY_SERVER_URL, credentials.normalizedServerUrl)
                        .putString(KEY_USERNAME, username)
                        .putString(KEY_PASSWORD, password)
                }

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
    override suspend fun logout() {
        Timber.d("$TAG: Logging out")
        api.clearCredentials()
        prefs.edit { clear() }

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
    suspend fun syncLibrarySongs(
        onProgress: ((Float, String) -> Unit)? = null
    ): Result<Int> {
        if (!isLoggedIn) {
            return Result.failure(Exception("Not logged in"))
        }

        return withContext(Dispatchers.IO) {
            try {
                Timber.d("$TAG: Syncing library songs from server")
                val allSongs = mutableListOf<NavidromeSong>()
                val pageSize = 500
                
                onProgress?.invoke(0.1f, context.getString(R.string.dash_status_fetching_albums))
                val fetchedAlbums = fetchAllAlbums(pageSize)

                // Fetch songs for each album in parallel
                val totalAlbums = fetchedAlbums.size
                val concurrencyLimit = 5
                val semaphore = Semaphore(concurrencyLimit)
                val processedCount = AtomicInteger(0)

                val albumSongLists = coroutineScope {
                    fetchedAlbums.map { albumJson ->
                        async {
                            semaphore.withPermit {
                                val albumId = albumJson.optString("id", "")
                                val albumTitle = albumJson.optString("title", "Unknown Album")
                                if (albumId.isBlank()) return@withPermit emptyList()

                                val songsResult = api.getAlbum(albumId)
                                val currentProcessed = processedCount.incrementAndGet()
                                
                                val progress = 0.1f + (currentProcessed.toFloat() / totalAlbums.coerceAtLeast(1) * 0.8f)
                                onProgress?.invoke(
                                    progress, 
                                    context.getString(R.string.dash_status_fetching_songs_from_format, albumTitle)
                                )

                                songsResult.fold(
                                    onSuccess = { songJsons ->
                                        NavidromeResponseParser.parseSongs(songJsons)
                                    },
                                    onFailure = {
                                        Timber.w(it, "$TAG: Failed to fetch songs for album $albumId")
                                        emptyList()
                                    }
                                )
                            }
                        }
                    }.awaitAll()
                }

                allSongs.addAll(albumSongLists.flatten())

                if (allSongs.isEmpty()) {
                    Timber.d("$TAG: No library songs found on server")
                    onProgress?.invoke(1f, context.getString(R.string.dash_status_no_songs_found))
                    return@withContext Result.success(0)
                }

                onProgress?.invoke(
                    0.95f, 
                    context.getString(R.string.dash_status_saving_songs_format, allSongs.size)
                )
                // Deduplicate by song ID
                val uniqueSongs = allSongs.distinctBy { it.id }

                val entities = uniqueSongs.map { song ->
                    song.toEntity(LIBRARY_PLAYLIST_ID)
                }

                // Replace all library songs
                dao.clearLibrarySongs()
                dao.insertSongs(entities)

                Timber.d("$TAG: Synced ${entities.size} library songs from ${fetchedAlbums.size} albums")
                onProgress?.invoke(1f, context.getString(R.string.dash_status_library_sync_complete))
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

    /** [SyncableLibraryProvider] entry point — full sync without progress reporting. */
    override suspend fun syncAllPlaylistsAndSongs(): Result<BulkSyncResult> =
        syncAllPlaylistsAndSongs(onProgress = null)

    /**
     * Sync all playlists and their songs, plus library songs.
     */
    suspend fun syncAllPlaylistsAndSongs(
        onProgress: ((Float, String) -> Unit)? = null
    ): Result<BulkSyncResult> {
        return withContext(Dispatchers.IO) {
            var syncedSongCount = 0
            var failedPlaylistCount = 0

            onProgress?.invoke(0.05f, context.getString(R.string.dash_status_syncing_library))
            // Sync library songs (all albums)
            val libResult = syncLibrarySongs { progress, message ->
                // Map library sync progress (0-1) to 0.05-0.4 range
                onProgress?.invoke(0.05f + (progress * 0.35f), message)
            }
            libResult.fold(
                onSuccess = { count -> syncedSongCount += count },
                onFailure = { Timber.w(it, "$TAG: Failed syncing library songs") }
            )

            onProgress?.invoke(0.4f, context.getString(R.string.dash_status_fetching_playlists))
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

            val totalPlaylists = playlistResult.size
            playlistResult.forEachIndexed { index, playlist ->
                val progressBase = 0.4f
                val progressStep = 0.5f / totalPlaylists.coerceAtLeast(1)
                val currentProgress = progressBase + (index * progressStep)
                
                onProgress?.invoke(
                    currentProgress, 
                    context.getString(R.string.dash_status_syncing_playlist_format, playlist.name)
                )
                
                val songSyncResult = syncPlaylistSongs(playlist.id)
                songSyncResult.fold(
                    onSuccess = { count -> syncedSongCount += count },
                    onFailure = {
                        failedPlaylistCount += 1
                        Timber.w(it, "$TAG: Failed syncing playlist ${playlist.id}")
                    }
                )
            }

            onProgress?.invoke(0.95f, context.getString(R.string.dash_status_updating_local))
            // Sync to unified library once after everything is synced
            try {
                syncUnifiedLibrarySongsFromNavidrome()
            } catch (e: Exception) {
                Timber.e(e, "$TAG: Failed to sync unified library")
            }

            onProgress?.invoke(1f, context.getString(R.string.dash_status_sync_complete))

            if (failedPlaylistCount == 0) {
                lastFullSyncTime = System.currentTimeMillis()
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
    override suspend fun searchSongs(query: String, limit: Int): Result<List<Song>> {
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
                    bitrate = navidromeSong.bitRate?.let { it * 1000 },
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

    private fun getAppPlaylistIdForNavidrome(navidromePlaylistId: String): String {
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

    // ─── Playback Reporting ──────────────────────────────────────────────

    suspend fun reportPlayback(
        navidromeId: String,
        positionMs: Long,
        state: String,
        playbackRate: Float = 1.0f,
        ignoreScrobble: Boolean = false
    ): Result<Unit> {
        if (!isLoggedIn) return Result.failure(Exception("Not logged in"))
        val result = api.reportPlayback(
            mediaId = navidromeId,
            positionMs = positionMs,
            state = state,
            playbackRate = playbackRate,
            ignoreScrobble = ignoreScrobble
        )
        // Fallback to standard scrobble if reportPlayback is not supported.
        // PS: The latest release of Navidrome currently doesn't support the
        // standard OpenSubsonic API (reportPlayback) at the time of writing
        // See: (https://github.com/navidrome/navidrome/pull/5442), so this is required.
        if (result.isFailure && result.exceptionOrNull()?.message?.contains("404") == true) {
            if (state == "playing" || state == "starting") {
                return api.scrobble(id = navidromeId, submission = false)
            }
        }
        return result
    }

    suspend fun scrobble(navidromeId: String, submission: Boolean = true): Result<Unit> {
        if (!isLoggedIn) return Result.failure(Exception("Not logged in"))
        return api.scrobble(id = navidromeId, submission = submission)
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
        bitrate = bitRate?.let { it * 1000 },
        sampleRate = null,
        year = year,
        trackNumber = trackNumber,
        dateAdded = System.currentTimeMillis(),
        isFavorite = false,
        navidromeId = id
    )
}

// ═══════════════════════════════════════════════════════════════════════════
// NavidromeStreamProxy.kt
// ═══════════════════════════════════════════════════════════════════════════


/**
 * Local HTTP proxy server for streaming Navidrome/Subsonic audio.
 *
 * Resolves `navidrome://{songId}` URIs by generating authenticated streaming URLs
 * from the Navidrome server and proxying the audio data to ExoPlayer.
 */
@Singleton
class NavidromeStreamProxy @Inject constructor(
    private val repository: NavidromeRepository,
    okHttpClient: OkHttpClient
) : CloudStreamProxy<String>(okHttpClient) {

    // Dynamically determine allowed hosts from the configured server URL.
    // We allow both HTTP and HTTPS for self-hosted servers.
    override val allowedHostSuffixes: Set<String>
        get() = repository.serverUrl?.toHttpUrlOrNull()?.host?.let { setOf(it) } ?: emptySet()

    // Stream URLs with authentication tokens are valid for a limited time.
    // Set cache expiration to 30 minutes to match typical token validity.
    override val cacheExpirationMs = 30L * 60 * 1000

    override val proxyTag = "NavidromeStreamProxy"
    override val routePath = "/navidrome/{songId}"
    override val routeParamName = "songId"
    override val uriScheme = "navidrome"
    override val routePrefix = "/navidrome"

    override fun parseRouteParam(value: String): String? =
        value.takeIf { it.isNotBlank() }

    override fun validateId(id: String): Boolean =
        CloudStreamSecurity.validateNavidromeSongId(id)

    override fun formatIdForUrl(id: String): String = id

    override suspend fun resolveStreamUrl(id: String): String? {
        return try {
            repository.getStreamUrl(id)
        } catch (e: Exception) {
            Timber.w(e, "NavidromeStreamProxy: Failed to resolve stream URL for song $id")
            null
        }
    }

    // Navidrome URIs may use host or path: navidrome://songId or navidrome:///songId
    override fun extractIdFromUri(uri: Uri): String? =
        uri.host ?: uri.path?.removePrefix("/")

    fun resolveNavidromeUri(uriString: String): String? = resolveUri(uriString)

    /**
     * Pre-fetches and caches the real stream URL for a song so the proxy can
     * serve it instantly when ExoPlayer makes its HTTP request.
     */
    suspend fun warmUpStreamUrl(uriString: String) {
        val uri = Uri.parse(uriString)
        if (uri.scheme != "navidrome") return
        val songId = uri.host ?: uri.path?.removePrefix("/") ?: return
        if (!CloudStreamSecurity.validateNavidromeSongId(songId)) return
        try {
            getOrFetchStreamUrl(songId)
        } catch (e: Exception) {
            Timber.w(e, "warmUpStreamUrl failed for $songId")
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// NeteaseRepository.kt
// ═══════════════════════════════════════════════════════════════════════════


@Suppress("DEPRECATION")
@Singleton
class NeteaseRepository @Inject constructor(
    private val api: NeteaseApiService,
    private val dao: NeteaseDao,
    private val musicDao: MusicDao,
    private val playlistPreferencesRepository: PlaylistPreferencesRepository,
    @ApplicationContext private val context: Context
) : RemoteMusicProvider,
    RemoteAuthState,
    CookieAuthProvider,
    SyncableLibraryProvider,
    RemoteSongSearch {
    private companion object {
        private const val NETEASE_SONG_ID_OFFSET = 3_000_000_000_000L
        private const val NETEASE_ALBUM_ID_OFFSET = 4_000_000_000_000L
        private const val NETEASE_ARTIST_ID_OFFSET = 5_000_000_000_000L
        private const val NETEASE_PARENT_DIRECTORY = "/Cloud/Netease"
        private const val NETEASE_GENRE = "Netease Cloud"
        private const val NETEASE_PLAYLIST_PREFIX = "netease_playlist:"
        private const val NETEASE_PLAYLIST_PAGE_SIZE = 50
        private const val NETEASE_SONG_DETAIL_BATCH_SIZE = 500
        private const val NETEASE_MAX_PLAYLIST_PAGES = 200
    }

    private val prefs: SharedPreferences = try {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "netease_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Exception) {
        Timber.e(e, "NeteaseRepository: Failed to create EncryptedSharedPreferences, falling back to plain")
        context.getSharedPreferences("netease_prefs_plain", Context.MODE_PRIVATE)
    }

    override val scheme: String = "netease"
    override val displayName: String = "Netease Cloud Music"

    private val _isLoggedInFlow = MutableStateFlow(false)
    override val isLoggedInFlow: StateFlow<Boolean> = _isLoggedInFlow.asStateFlow()

    private val inFlightSongUrlRequests = java.util.concurrent.ConcurrentHashMap<Long, CompletableDeferred<Result<String>>>()
    private val lastSongUrlAttemptAtMs = java.util.concurrent.ConcurrentHashMap<Long, Long>()
    private val songUrlRequestCooldownMs = 1500L
    private val neteaseSongUrlRequestMutex = Mutex()
    @Volatile
    private var lastGlobalSongUrlRequestAtMs = 0L
    private val globalSongUrlRequestIntervalMs = 1100L

    init {
        // Auto-load saved cookies on creation so API client is ready
        initFromSavedCookies()
        _isLoggedInFlow.value = api.hasLogin()
        Timber.d("NeteaseRepository init: isLoggedIn=${api.hasLogin()}")
    }

    // ─── Auth State ────────────────────────────────────────────────────

    override val isLoggedIn: Boolean
        get() = api.hasLogin()

    val userId: Long
        get() = prefs.getLong("netease_user_id", -1L)

    override val userNickname: String?
        get() = prefs.getString("netease_nickname", null)

    val userAvatar: String?
        get() = prefs.getString("netease_avatar", null)

    // ─── Cookie-Based Authentication ──────────────────────────────────

    /**
     * Initialize from saved cookies on app start.
     */
    override fun initFromSavedCookies() {
        val cookieJson = prefs.getString("netease_cookies", null) ?: return
        try {
            val map = jsonToMap(cookieJson)
            if (map.isNotEmpty()) {
                api.setPersistedCookies(map)
            }
        } catch (e: Exception) {
            Timber.w(e, "Failed to restore Netease cookies")
        }
    }

    /**
     * Save cookies from WebView login result and initialize the API client.
     * Returns the user's nickname on success.
     */
    override suspend fun loginWithCookies(cookieJson: String): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                val cookies = jsonToMap(cookieJson)

                if (!cookies.containsKey("MUSIC_U")) {
                    Timber.w("loginWithCookies: required session cookie not found")
                    return@withContext Result.failure(Exception("MUSIC_U cookie not found"))
                }

                // Persist cookies
                prefs.edit().putString("netease_cookies", cookieJson).apply()

                // Initialize API client with cookies
                api.setPersistedCookies(cookies)

                // Fetch user info
                val userAccountRaw = api.getCurrentUserAccount()
                val root = JSONObject(userAccountRaw)
                val code = root.optInt("code", -1)
                val profile = root.optJSONObject("profile")

                if (profile != null) {
                    val uid = profile.optLong("userId")
                    val nickname = profile.optString("nickname", "User")
                    val avatarUrl = profile.optString("avatarUrl", "")
                    Timber.d("loginWithCookies: login successful")

                    saveUserInfo(uid, nickname, avatarUrl)
                    _isLoggedInFlow.value = true
                    Result.success(nickname)
                } else {
                    Timber.w("loginWithCookies: No profile in response (code=$code)")
                    Result.failure(Exception("Failed to fetch user profile (code=$code)"))
                }
            } catch (e: Exception) {
                Timber.e(e, "loginWithCookies: failed")
                Result.failure(e)
            }
        }
    }

    override suspend fun logout() {
        api.logout()
        clearLoginState()
        
        // Delete all Netease playlists from the database
        val neteasePlaylistsToDelete = dao.getAllPlaylistsList()
        neteasePlaylistsToDelete.forEach { playlist ->
            dao.deleteSongsByPlaylist(playlist.id)
            dao.deletePlaylist(playlist.id)
            deleteAppPlaylistForNeteasePlaylist(playlist.id)
        }
        
        musicDao.clearAllNeteaseSongs()
        _isLoggedInFlow.value = false
    }

    private fun saveUserInfo(userId: Long, nickname: String, avatarUrl: String?) {
        prefs.edit()
            .putLong("netease_user_id", userId)
            .putString("netease_nickname", nickname)
            .putString("netease_avatar", avatarUrl)
            .apply()
    }

    private fun clearLoginState() {
        prefs.edit().clear().apply()
    }

    // ─── Content ───────────────────────────────────────────────────────

    suspend fun syncUserPlaylists(): Result<List<NeteasePlaylistEntity>> {
        Timber.d("syncUserPlaylists called, isLoggedIn=${isLoggedIn}, userId=$userId")
        if (!isLoggedIn) {
            Timber.w("syncUserPlaylists: Not logged in, aborting")
            return Result.failure(Exception("Not logged in"))
        }
        return withContext(Dispatchers.IO) {
            try {
                val uid = if (userId != -1L) userId else api.getCurrentUserId()
                Timber.d("syncUserPlaylists: fetching playlists for uid=$uid")
                val entitiesById = LinkedHashMap<Long, NeteasePlaylistEntity>()
                var offset = 0
                var page = 0
                var hasMore = true

                while (hasMore) {
                    val raw = api.getUserPlaylists(
                        userId = uid,
                        offset = offset,
                        limit = NETEASE_PLAYLIST_PAGE_SIZE
                    )
                    Timber.d("syncUserPlaylists: page=$page offset=$offset response length=${raw.length}")
                    val root = JSONObject(raw)

                    if (root.optInt("code", -1) != 200) {
                        Timber.e("syncUserPlaylists: API error code=${root.optInt("code")}")
                        return@withContext Result.failure(Exception("API error: code ${root.optInt("code")}"))
                    }

                    val playlistArray = root.optJSONArray("playlist") ?: break
                    val fetchedCount = playlistArray.length()
                    if (fetchedCount == 0) break

                    for (i in 0 until fetchedCount) {
                        val pl = playlistArray.optJSONObject(i) ?: continue
                        val id = pl.optLong("id")
                        if (id <= 0L) continue
                        entitiesById[id] = NeteasePlaylistEntity(
                            id = id,
                            name = pl.optString("name", ""),
                            coverUrl = pl.optString("coverImgUrl", ""),
                            songCount = pl.optInt("trackCount", 0),
                            lastSyncTime = System.currentTimeMillis()
                        )
                    }

                    offset += fetchedCount
                    val totalCount = root.optInt("count", -1)
                    val moreFlag = root.optBoolean("more", false)
                    hasMore = when {
                        moreFlag -> true
                        totalCount > 0 -> offset < totalCount
                        else -> fetchedCount >= NETEASE_PLAYLIST_PAGE_SIZE
                    }

                    page += 1
                    if (page >= NETEASE_MAX_PLAYLIST_PAGES) {
                        Timber.w("syncUserPlaylists: reached max page guard ($NETEASE_MAX_PLAYLIST_PAGES), stopping pagination")
                        hasMore = false
                    }
                }

                val entities = entitiesById.values.toList()

                val localPlaylists = dao.getAllPlaylistsList()
                val remoteIds = entities.map { it.id }.toSet()
                val stalePlaylists = localPlaylists.filter { it.id !in remoteIds }

                stalePlaylists.forEach { stale ->
                    dao.deleteSongsByPlaylist(stale.id)
                    dao.deletePlaylist(stale.id)
                    // Delete corresponding app playlists for removed Netease playlists
                    deleteAppPlaylistForNeteasePlaylist(stale.id)
                }

                entities.forEach { dao.insertPlaylist(it) }
                if (stalePlaylists.isNotEmpty()) {
                    syncUnifiedLibrarySongsFromNetease()
                }
                Result.success(entities)
            } catch (e: Exception) {
                Timber.e(e, "Failed to sync user playlists")
                Result.failure(e)
            }
        }
    }

    suspend fun syncPlaylistSongs(playlistId: Long): Result<Int> {
        return syncPlaylistSongs(playlistId, syncUnifiedLibrary = true)
    }

    suspend fun syncPlaylistSongs(
        playlistId: Long,
        syncUnifiedLibrary: Boolean
    ): Result<Int> {
        return withContext(Dispatchers.IO) {
            try {
                val raw = api.getPlaylistDetail(playlistId)
                val root = JSONObject(raw)

                if (root.optInt("code", -1) != 200) {
                    return@withContext Result.failure(Exception("API error"))
                }

                val playlist = root.optJSONObject("playlist")
                    ?: return@withContext Result.failure(Exception("No playlist data"))
                val embeddedTracks = playlist.optJSONArray("tracks")
                val trackIds = playlist.optJSONArray("trackIds")
                val playlistName = playlist.optString("name", "")

                val entitiesBySongId = LinkedHashMap<Long, NeteaseSongEntity>()
                val orderedTrackIds = mutableListOf<Long>()

                for (i in 0 until (embeddedTracks?.length() ?: 0)) {
                    val track = embeddedTracks?.optJSONObject(i) ?: continue
                    val entity = parseTrackToEntity(track, playlistId)
                    entitiesBySongId[entity.neteaseId] = entity
                }

                for (i in 0 until (trackIds?.length() ?: 0)) {
                    val id = trackIds?.optJSONObject(i)?.optLong("id") ?: 0L
                    if (id > 0L) {
                        orderedTrackIds.add(id)
                    }
                }

                val existingTrackIds = entitiesBySongId.keys.toSet()
                val missingTrackIds = if (orderedTrackIds.isNotEmpty()) {
                    orderedTrackIds.filterNot(existingTrackIds::contains)
                } else {
                    emptyList()
                }

                if (missingTrackIds.isNotEmpty()) {
                    Timber.d(
                        "syncPlaylistSongs: playlistId=$playlistId needs ${missingTrackIds.size} additional tracks beyond embedded detail"
                    )
                    missingTrackIds.chunked(NETEASE_SONG_DETAIL_BATCH_SIZE).forEach { chunk ->
                        val detailRaw = api.getSongDetails(chunk)
                        val detailRoot = JSONObject(detailRaw)
                        if (detailRoot.optInt("code", -1) != 200) {
                            Timber.w(
                                "syncPlaylistSongs: getSongDetails failed for chunk size=${chunk.size}, code=${detailRoot.optInt("code", -1)}"
                            )
                            return@forEach
                        }
                        val detailSongs = detailRoot.optJSONArray("songs") ?: return@forEach
                        for (i in 0 until detailSongs.length()) {
                            val track = detailSongs.optJSONObject(i) ?: continue
                            val entity = parseTrackToEntity(track, playlistId)
                            entitiesBySongId[entity.neteaseId] = entity
                        }
                    }
                }

                val entities = if (orderedTrackIds.isNotEmpty()) {
                    val ordered = orderedTrackIds.mapNotNull { entitiesBySongId[it] }
                    if (ordered.size < entitiesBySongId.size) {
                        val orderedSet = orderedTrackIds.toSet()
                        ordered + entitiesBySongId.values.filterNot { it.neteaseId in orderedSet }
                    } else {
                        ordered
                    }
                } else {
                    entitiesBySongId.values.toList()
                }

                val expectedTrackCount = playlist.optInt("trackCount", orderedTrackIds.size)
                if (expectedTrackCount > 0 && entities.size < expectedTrackCount) {
                    Timber.w(
                        "syncPlaylistSongs: playlistId=$playlistId expected=$expectedTrackCount synced=${entities.size} (API may still be limiting some tracks)"
                    )
                }

                dao.deleteSongsByPlaylist(playlistId)
                dao.insertSongs(entities)
                
                // Create or update the corresponding app playlist
                updateAppPlaylistForNeteasePlaylist(playlistId, playlistName, entities)
                
                if (syncUnifiedLibrary) {
                    syncUnifiedLibrarySongsFromNetease()
                }

                Timber.d("Synced ${entities.size} songs for playlist $playlistId")
                Result.success(entities.size)
            } catch (e: Exception) {
                Timber.e(e, "Failed to sync playlist $playlistId")
                Result.failure(e)
            }
        }
    }

    override suspend fun syncAllPlaylistsAndSongs(): Result<BulkSyncResult> {
        return withContext(Dispatchers.IO) {
            val playlistResult = syncUserPlaylists().getOrElse { return@withContext Result.failure(it) }
            if (playlistResult.isEmpty()) {
                syncUnifiedLibrarySongsFromNetease()
                return@withContext Result.success(
                    BulkSyncResult(
                        playlistCount = 0,
                        syncedSongCount = 0,
                        failedPlaylistCount = 0
                    )
                )
            }

            var syncedSongCount = 0
            var failedPlaylistCount = 0

            playlistResult.forEach { playlist ->
                val songSyncResult = syncPlaylistSongs(
                    playlistId = playlist.id,
                    syncUnifiedLibrary = false
                )
                songSyncResult.fold(
                    onSuccess = { count -> syncedSongCount += count },
                    onFailure = {
                        failedPlaylistCount += 1
                        Timber.w(it, "Failed syncing playlistId=${playlist.id}")
                    }
                )
            }

            syncUnifiedLibrarySongsFromNetease()

            Result.success(
                BulkSyncResult(
                    playlistCount = playlistResult.size,
                    syncedSongCount = syncedSongCount,
                    failedPlaylistCount = failedPlaylistCount
                )
            )
        }
    }

    fun getPlaylists(): Flow<List<NeteasePlaylistEntity>> = dao.getAllPlaylists()

    fun getPlaylistSongs(playlistId: Long): Flow<List<Song>> {
        return dao.getSongsByPlaylist(playlistId).map { entities ->
            entities.map { it.toSong() }
        }
    }

    fun getAllSongs(): Flow<List<Song>> {
        return dao.getAllNeteaseSongs().map { entities ->
            entities.map { it.toSong() }
        }
    }

    fun searchLocalSongs(query: String): Flow<List<Song>> {
        return dao.searchSongs(query).map { entities ->
            entities.map { it.toSong() }
        }
    }

    // ─── Online Search ─────────────────────────────────────────────────

    /** [RemoteSongSearch] entry point; delegates to [searchOnline]. */
    override suspend fun searchSongs(query: String, limit: Int): Result<List<Song>> =
        searchOnline(query, limit)

    suspend fun searchOnline(keywords: String, limit: Int = 30): Result<List<Song>> {
        return withContext(Dispatchers.IO) {
            try {
                val raw = api.searchSongs(keywords, limit)
                val root = JSONObject(raw)
                val result = root.optJSONObject("result")
                val songs = result?.optJSONArray("songs")

                if (songs != null) {
                    val songList = mutableListOf<Song>()
                    for (i in 0 until songs.length()) {
                        val track = songs.optJSONObject(i) ?: continue
                        songList.add(parseTrackToSong(track))
                    }
                    Result.success(songList)
                } else {
                    Result.success(emptyList())
                }
            } catch (e: Exception) {
                Timber.e(e, "Online search failed for: $keywords")
                Result.failure(e)
            }
        }
    }

    // ─── Song URL Resolution ───────────────────────────────────────────

    suspend fun getSongUrl(songId: Long, quality: String = "exhigh"): Result<String> {
        val now = System.currentTimeMillis()
        val lastAttempt = lastSongUrlAttemptAtMs[songId]
        if (lastAttempt != null && now - lastAttempt < songUrlRequestCooldownMs) {
            Timber.d("Skip Netease song URL retry due to cooldown: songId=$songId")
            return Result.failure(IllegalStateException("Netease song URL request throttled"))
        }
        lastSongUrlAttemptAtMs[songId] = now

        inFlightSongUrlRequests[songId]?.let {
            return it.await()
        }

        val requestDeferred = CompletableDeferred<Result<String>>()
        val existing = inFlightSongUrlRequests.putIfAbsent(songId, requestDeferred)
        if (existing != null) {
            return existing.await()
        }

        val result = withContext(Dispatchers.IO) {
            runCatching {
                val qualityFallbacks = linkedSetOf(quality, "higher", "standard")
                var lastFailure: String? = null

                for (level in qualityFallbacks) {
                    val raw = requestSongUrl(songId, level)
                    val root = JSONObject(raw)
                    val code = root.optInt("code", -1)
                    if (code != 200) {
                        lastFailure = "API code=$code for level=$level"
                        continue
                    }

                    val data = root.optJSONArray("data")
                    val urlObj = data?.optJSONObject(0)
                    val url = urlObj?.optString("url", "")
                    if (!url.isNullOrBlank() && url != "null") {
                        Timber.d("Resolved Netease URL for songId=$songId with level=$level")
                        return@runCatching url
                    }

                    val freeTrialInfo = urlObj?.opt("freeTrialInfo")
                    lastFailure = "Empty URL at level=$level, freeTrialInfo=$freeTrialInfo"
                }

                throw Exception("No URL available for song $songId ($lastFailure)")
            }
        }

        requestDeferred.complete(result)
        inFlightSongUrlRequests.remove(songId, requestDeferred)
        return result
    }

    /**
     * Make a single song URL request with global rate-limit guard.
     */
    private suspend fun requestSongUrl(songId: Long, level: String): String {
        neteaseSongUrlRequestMutex.withLock {
            val now = System.currentTimeMillis()
            val waitMs = globalSongUrlRequestIntervalMs - (now - lastGlobalSongUrlRequestAtMs)
            if (waitMs > 0) delay(waitMs)
            lastGlobalSongUrlRequestAtMs = System.currentTimeMillis()
        }
        return api.getSongDownloadUrl(songId, level)
    }

    // ─── Lyrics ────────────────────────────────────────────────────────

    suspend fun getLyrics(songId: Long): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                val raw = api.getLyrics(songId)
                val root = JSONObject(raw)
                val lyric = root.optJSONObject("lrc")?.optString("lyric", "")

                if (!lyric.isNullOrBlank()) {
                    Result.success(lyric)
                } else {
                    Result.failure(Exception("No lyrics for song $songId"))
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to get lyrics for song $songId")
                Result.failure(e)
            }
        }
    }

    // ─── JSON Parsing Helpers ──────────────────────────────────────────

    private fun parseTrackToEntity(track: JSONObject, playlistId: Long): NeteaseSongEntity {
        val artists = track.optJSONArray("ar")
        val artistName = buildString {
            if (artists != null) {
                for (j in 0 until artists.length()) {
                    if (j > 0) append(", ")
                    append(artists.optJSONObject(j)?.optString("name", "") ?: "")
                }
            } else {
                append("Unknown Artist")
            }
        }
        val album = track.optJSONObject("al")

        return NeteaseSongEntity(
            id = "${playlistId}_${track.optLong("id")}",
            neteaseId = track.optLong("id"),
            playlistId = playlistId,
            title = track.optString("name", ""),
            artist = artistName,
            album = album?.optString("name", "Unknown Album") ?: "Unknown Album",
            albumId = album?.optLong("id") ?: -1L,
            duration = track.optLong("dt"),
            albumArtUrl = album?.optString("picUrl"),
            mimeType = "audio/mpeg",
            bitrate = null,
            dateAdded = track.optLong("publishTime", System.currentTimeMillis())
        )
    }

    private fun parseTrackToSong(track: JSONObject): Song {
        val artists = track.optJSONArray("ar")
        val artistName = buildString {
            if (artists != null) {
                for (j in 0 until artists.length()) {
                    if (j > 0) append(", ")
                    append(artists.optJSONObject(j)?.optString("name", "") ?: "")
                }
            } else {
                append("Unknown Artist")
            }
        }
        val album = track.optJSONObject("al")
        val neteaseId = track.optLong("id")

        return Song(
            id = "netease_$neteaseId",
            title = track.optString("name", ""),
            artist = artistName,
            artistId = artists?.optJSONObject(0)?.optLong("id") ?: -1L,
            album = album?.optString("name", "Unknown Album") ?: "Unknown Album",
            albumId = album?.optLong("id") ?: -1L,
            path = "",
            contentUriString = "netease://$neteaseId",
            albumArtUriString = album?.optString("picUrl"),
            duration = track.optLong("dt"),
            mimeType = "audio/mpeg",
            bitrate = null,
            sampleRate = null,
            year = 0,
            trackNumber = 0,
            dateAdded = track.optLong("publishTime", System.currentTimeMillis()),
            isFavorite = false,
            neteaseId = neteaseId
        )
    }

    private suspend fun syncUnifiedLibrarySongsFromNetease() {
        val neteaseSongs = dao.getAllNeteaseSongsList()
        val existingUnifiedNeteaseIds = musicDao.getAllNeteaseSongIds()

        if (neteaseSongs.isEmpty()) {
            if (existingUnifiedNeteaseIds.isNotEmpty()) {
                musicDao.clearAllNeteaseSongs()
            }
            return
        }

        val songs = ArrayList<SongEntity>(neteaseSongs.size)
        val artists = LinkedHashMap<Long, ArtistEntity>()
        val albums = LinkedHashMap<Long, AlbumEntity>()
        val crossRefs = mutableListOf<SongArtistCrossRef>()

        neteaseSongs.forEach { neteaseSong ->
            val songId = toUnifiedSongId(neteaseSong.neteaseId)
            val artistNames = parseArtistNames(neteaseSong.artist)
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

            val albumId = toUnifiedAlbumId(neteaseSong.albumId, neteaseSong.album)
            val albumName = neteaseSong.album.ifBlank { "Unknown Album" }
            albums.putIfAbsent(
                albumId,
                AlbumEntity(
                    id = albumId,
                    title = albumName,
                    artistName = primaryArtistName,
                    artistId = primaryArtistId,
                    songCount = 0,
                    dateAdded = neteaseSong.dateAdded,
                    year = 0,
                    albumArtUriString = neteaseSong.albumArtUrl
                )
            )

            songs.add(
                SongEntity(
                    id = songId,
                    title = neteaseSong.title,
                    artistName = neteaseSong.artist.ifBlank { primaryArtistName },
                    artistId = primaryArtistId,
                    albumArtist = null,
                    albumName = albumName,
                    albumId = albumId,
                    contentUriString = "netease://${neteaseSong.neteaseId}",
                    albumArtUriString = neteaseSong.albumArtUrl,
                    duration = neteaseSong.duration,
                    genre = NETEASE_GENRE,
                    filePath = "",
                    parentDirectoryPath = NETEASE_PARENT_DIRECTORY,
                    isFavorite = false,
                    lyrics = null,
                    trackNumber = 0,
                    year = 0,
                    dateAdded = neteaseSong.dateAdded.takeIf { it > 0 } ?: System.currentTimeMillis(),
                    mimeType = neteaseSong.mimeType,
                    bitrate = neteaseSong.bitrate,
                    sampleRate = null,
                    telegramChatId = null,
                    telegramFileId = null,
                    sourceType = SourceType.NETEASE
                )
            )
        }

        val albumCounts = songs.groupingBy { it.albumId }.eachCount()
        val finalAlbums = albums.values.map { album ->
            album.copy(songCount = albumCounts[album.id] ?: 0)
        }

        val currentUnifiedSongIds = songs.map { it.id }.toSet()
        val deletedUnifiedSongIds = existingUnifiedNeteaseIds.filter { it !in currentUnifiedSongIds }

        musicDao.incrementalSyncMusicData(
            songs = songs,
            albums = finalAlbums,
            artists = artists.values.toList(),
            crossRefs = crossRefs,
            deletedSongIds = deletedUnifiedSongIds
        )
    }

    private fun parseArtistNames(rawArtist: String): List<String> =
        CloudMusicUtils.parseArtistNames(rawArtist)

    private fun toUnifiedSongId(neteaseId: Long): Long {
        return -(NETEASE_SONG_ID_OFFSET + neteaseId.absoluteValue)
    }

    private fun toUnifiedAlbumId(albumId: Long, albumName: String): Long {
        val normalized = if (albumId > 0L) albumId.absoluteValue else albumName.lowercase().hashCode().toLong().absoluteValue
        return -(NETEASE_ALBUM_ID_OFFSET + normalized)
    }

    private fun toUnifiedArtistId(artistName: String): Long {
        return -(NETEASE_ARTIST_ID_OFFSET + artistName.lowercase().hashCode().toLong().absoluteValue)
    }

    // ─── Delete ────────────────────────────────────────────────────────

    suspend fun deletePlaylist(playlistId: Long) {
        dao.deleteSongsByPlaylist(playlistId)
        dao.deletePlaylist(playlistId)
        deleteAppPlaylistForNeteasePlaylist(playlistId)
        syncUnifiedLibrarySongsFromNetease()
    }

    // ─── App Playlist Management ────────────────────────────────────────

    private suspend fun getAppPlaylistIdForNetease(neteasePlaylistId: Long): String {
        return "$NETEASE_PLAYLIST_PREFIX$neteasePlaylistId"
    }

    private suspend fun updateAppPlaylistForNeteasePlaylist(
        neteasePlaylistId: Long,
        playlistName: String,
        neteaseEntities: List<NeteaseSongEntity>
    ) {
        try {
            // Convert Netease song entities to unified song IDs (Long format, stored as String)
            // These must match the IDs generated in syncUnifiedLibrarySongsFromNetease
            val unifiedSongIds = neteaseEntities.map { entity ->
                toUnifiedSongId(entity.neteaseId).toString()
            }

            val appPlaylistId = getAppPlaylistIdForNetease(neteasePlaylistId)
            
            // Get all current app playlists
            val allPlaylists = playlistPreferencesRepository.userPlaylistsFlow
            val existingPlaylist = withContext(Dispatchers.IO) {
                allPlaylists.map { playlists ->
                    playlists.find { it.id == appPlaylistId }
                }.first()
            }

            if (existingPlaylist != null) {
                // Update the existing playlist
                playlistPreferencesRepository.updatePlaylist(
                    existingPlaylist.copy(
                        name = playlistName,
                        songIds = unifiedSongIds,
                        lastModified = System.currentTimeMillis(),
                        source = "NETEASE" // Mark as NetEase source
                    )
                )
                Timber.d("Updated app playlist for Netease playlist $neteasePlaylistId: $playlistName")
            } else {
                // Create a new playlist with custom ID to prevent duplicates
                playlistPreferencesRepository.createPlaylist(
                    name = playlistName,
                    songIds = unifiedSongIds,
                    customId = appPlaylistId,  // Use NetEase prefix ID for matching on next sync
                    source = "NETEASE"         // Mark as NetEase source
                )
                Timber.d("Created new app playlist for Netease playlist $neteasePlaylistId: $playlistName with ID: $appPlaylistId")
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to update/create app playlist for Netease playlist $neteasePlaylistId")
        }
    }

    private suspend fun deleteAppPlaylistForNeteasePlaylist(neteasePlaylistId: Long) {
        try {
            val appPlaylistId = getAppPlaylistIdForNetease(neteasePlaylistId)
            playlistPreferencesRepository.deletePlaylist(appPlaylistId)
            Timber.d("Deleted app playlist for Netease playlist $neteasePlaylistId")
        } catch (e: Exception) {
            Timber.w(e, "Failed to delete app playlist for Netease playlist $neteasePlaylistId")
        }
    }

    // ─── Utility ───────────────────────────────────────────────────────

    private fun jsonToMap(json: String): Map<String, String> =
        CloudMusicUtils.jsonToMap(json)
}

// ═══════════════════════════════════════════════════════════════════════════
// NeteaseStreamProxy.kt
// ═══════════════════════════════════════════════════════════════════════════


/**
 * Local HTTP proxy server for streaming Netease Cloud Music audio.
 *
 * Resolves `netease://{songId}` URIs by fetching temporary streaming URLs
 * from the Netease API and proxying the audio data to ExoPlayer.
 */
@Singleton
class NeteaseStreamProxy @Inject constructor(
    private val repository: NeteaseRepository,
    okHttpClient: OkHttpClient
) : CloudStreamProxy<Long>(okHttpClient) {

    override val allowedHostSuffixes = setOf(
        "music.126.net",
        "music.163.com",
        "126.net",
        "163.com"
    )
    override val cacheExpirationMs = 15L * 60 * 1000
    override val proxyTag = "NeteaseStreamProxy"
    override val routePath = "/netease/{songId}"
    override val routeParamName = "songId"
    override val uriScheme = "netease"
    override val routePrefix = "/netease"

    override fun parseRouteParam(value: String): Long? = value.toLongOrNull()

    override fun validateId(id: Long): Boolean =
        CloudStreamSecurity.validateNeteaseSongId(id)

    override fun formatIdForUrl(id: Long): String = id.toString()

    override suspend fun resolveStreamUrl(id: Long): String? =
        repository.getSongUrl(id).getOrNull()

    fun resolveNeteaseUri(uriString: String): String? = resolveUri(uriString)
}

// ═══════════════════════════════════════════════════════════════════════════
// QqMusicRepository.kt
// ═══════════════════════════════════════════════════════════════════════════


@Suppress("DEPRECATION")
@Singleton
class QqMusicRepository @Inject constructor(
    private val api: QqMusicApiService,
    private val dao: QqMusicDao,
    private val musicDao: MusicDao,
    private val playlistPreferencesRepository: PlaylistPreferencesRepository,
    @ApplicationContext private val context: Context
) : RemoteMusicProvider,
    RemoteAuthState,
    CookieAuthProvider,
    SyncableLibraryProvider {

    private companion object {
        private const val QQ_MUSIC_SONG_ID_OFFSET = 6_000_000_000_000L
        private const val QQ_MUSIC_ALBUM_ID_OFFSET = 7_000_000_000_000L
        private const val QQ_MUSIC_ARTIST_ID_OFFSET = 8_000_000_000_000L
        private const val QQ_MUSIC_PARENT_DIRECTORY = "/Cloud/QQMusic"
        private const val QQ_MUSIC_GENRE = "QQ Music"
        private const val QQ_MUSIC_PLAYLIST_PREFIX = "qqmusic_playlist:"
        private const val QQ_USER_PLAYLIST_PAGE_SIZE = 100
        private const val QQ_PLAYLIST_SONG_PAGE_SIZE = 1000
        private const val QQ_MAX_PLAYLIST_PAGES = 200
    }

    private val prefs: SharedPreferences = try {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "qqmusic_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Exception) {
        Timber.e(e, "QqMusicRepository: Failed to create EncryptedSharedPreferences, falling back to plain")
        context.getSharedPreferences("qqmusic_prefs_plain", Context.MODE_PRIVATE)
    }

    override val scheme: String = "qqmusic"
    override val displayName: String = "QQ Music"

    private val _isLoggedInFlow = MutableStateFlow(false)
    override val isLoggedInFlow: StateFlow<Boolean> = _isLoggedInFlow.asStateFlow()
    private val inFlightSongUrlRequests = java.util.concurrent.ConcurrentHashMap<String, CompletableDeferred<Result<String>>>()
    private val lastSongUrlAttemptAtMs = java.util.concurrent.ConcurrentHashMap<String, Long>()
    private val songUrlRequestCooldownMs = 1500L
    private val qqSongUrlRequestMutex = Mutex()
    @Volatile
    private var lastGlobalSongUrlRequestAtMs = 0L
    private val globalSongUrlRequestIntervalMs = 1100L

    init {
        initFromSavedCookies()
        _isLoggedInFlow.value = api.hasLogin()
    }

    override val isLoggedIn: Boolean
        get() = api.hasLogin()

    override val userNickname: String?
        get() = prefs.getString("qqmusic_nickname", null)

    val userAvatarUrl: String?
        get() = prefs.getString("qqmusic_avatar_url", null)

    fun getPlaylists(): Flow<List<QqMusicPlaylistEntity>> = dao.getAllPlaylists()

    override fun initFromSavedCookies() {
        val cookieJson = prefs.getString("qqmusic_cookies", null) ?: return
        runCatching {
            val map = jsonToMap(cookieJson)
            if (map.isNotEmpty()) {
                api.setPersistedCookies(map)
            }
        }.onFailure {
            Timber.w(it, "Failed to restore QQ Music cookies")
        }
    }

    override suspend fun loginWithCookies(cookieJson: String): Result<String> {
        return withContext(Dispatchers.IO) {
            runCatching {
                val cookies = jsonToMap(cookieJson)
                if (cookies.isEmpty()) {
                    throw IllegalStateException("No cookies captured")
                }
                prefs.edit().putString("qqmusic_cookies", cookieJson).apply()
                api.setPersistedCookies(cookies)
                if (!api.hasLogin()) {
                    throw IllegalStateException("QQ Music login cookie not detected")
                }

                // Fetch user profile to get real nickname
                var nickname = "QQ Music User"
                try {
                    // Try to get nickname from created playlists API
                    val createdPlaylistsRaw = api.getUserCreatedPlaylists(start = 0, size = 1)
                    Timber.d("QQ Music getUserCreatedPlaylists response: ${createdPlaylistsRaw.take(500)}")

                    if (createdPlaylistsRaw.isNotBlank()) {
                        val json = JSONObject(createdPlaylistsRaw)
                        val code = json.optInt("code", -1)
                        if (code == 0) {
                            val data = json.optJSONObject("data")
                            // The nickname is in "hostname" field
                            val hostname = data?.optString("hostname")
                            if (!hostname.isNullOrBlank()) {
                                nickname = hostname
                                Timber.d("QQ Music login: got nickname=$nickname")
                            }
                        }
                    }
                } catch (e: Exception) {
                    Timber.w(e, "Failed to fetch QQ Music user info, using default nickname")
                }

                val avatarUrl = api.getUserAvatarUrl()

                prefs.edit()
                    .putString("qqmusic_nickname", nickname)
                    .putString("qqmusic_avatar_url", avatarUrl)
                    .apply()

                _isLoggedInFlow.value = true
                nickname
            }
        }
    }

    suspend fun getSongUrl(songMid: String): Result<String> {
        val now = System.currentTimeMillis()
        val lastAttempt = lastSongUrlAttemptAtMs[songMid]
        if (lastAttempt != null && now - lastAttempt < songUrlRequestCooldownMs) {
            Timber.d("Skip QQ song URL retry due to cooldown: songMid=$songMid")
            return Result.failure(IllegalStateException("QQ song URL request throttled"))
        }
        lastSongUrlAttemptAtMs[songMid] = now

        inFlightSongUrlRequests[songMid]?.let {
            return it.await()
        }

        val requestDeferred = CompletableDeferred<Result<String>>()
        val existing = inFlightSongUrlRequests.putIfAbsent(songMid, requestDeferred)
        if (existing != null) {
            return existing.await()
        }

        val result = withContext(Dispatchers.IO) {
            runCatching {
                // Phase 1: Request without filename to get default M4A URL and discover media_mid.
                val defaultPurl = requestPurl(songMid)

                if (defaultPurl.isBlank()) {
                    throw IllegalStateException("No playable URL for songMid=$songMid (empty purl)")
                }

                val defaultUrl = if (defaultPurl.startsWith("http")) defaultPurl
                    else "https://ws.stream.qqmusic.qq.com/$defaultPurl"

                // Extract media_mid from purl (format: "C400{mediaMid}.m4a?vkey=...")
                val mediaMid = defaultPurl.substringBefore("?").drop(4).substringBefore(".")
                if (mediaMid.isNotBlank()) {
                    // Phase 2: Try MP3 320kbps with discovered media_mid.
                    val mp3Filename = "M500${mediaMid}.mp3"
                    val mp3Purl = requestPurl(songMid, filename = mp3Filename)
                    if (mp3Purl.isNotBlank()) {
                        Timber.d("Resolved QQ Music MP3 URL for songMid=$songMid")
                        return@runCatching if (mp3Purl.startsWith("http")) mp3Purl
                            else "https://ws.stream.qqmusic.qq.com/$mp3Purl"
                    }
                    Timber.d("MP3 unavailable for songMid=$songMid, falling back to M4A")
                }

                // Fallback: use the default M4A URL.
                Timber.d("Resolved QQ Music M4A URL for songMid=$songMid")
                defaultUrl
            }
        }

        requestDeferred.complete(result)
        inFlightSongUrlRequests.remove(songMid, requestDeferred)
        return result
    }

    /**
     * Make a single vkey request and return the purl string (empty if unavailable).
     * Respects the global rate-limit mutex.
     */
    private suspend fun requestPurl(songMid: String, filename: String? = null): String {
        qqSongUrlRequestMutex.withLock {
            val now = System.currentTimeMillis()
            val waitMs = globalSongUrlRequestIntervalMs - (now - lastGlobalSongUrlRequestAtMs)
            if (waitMs > 0) delay(waitMs)
            lastGlobalSongUrlRequestAtMs = System.currentTimeMillis()
        }

        val raw = api.getSongDownloadUrl(songMid, filename = filename)
        val root = JSONObject(raw)
        return root
            .optJSONObject("req_0")
            ?.optJSONObject("data")
            ?.optJSONArray("midurlinfo")
            ?.optJSONObject(0)
            ?.optString("purl", "")
            .orEmpty()
    }

    override suspend fun logout() {
        api.logout()
        prefs.edit().clear().apply()
        
        // Delete all QQ Music playlists from the database
        val playlistsToDelete = dao.getAllPlaylistsList()
        playlistsToDelete.forEach { playlist ->
            dao.deleteSongsByPlaylist(playlist.id)
            dao.deletePlaylist(playlist.id)
            deleteAppPlaylistForQqMusicPlaylist(playlist.id)
        }
        
        musicDao.clearAllQqMusicSongs()
        _isLoggedInFlow.value = false
    }

    suspend fun deletePlaylistById(playlistId: Long) {
        withContext(Dispatchers.IO) {
            dao.deleteSongsByPlaylist(playlistId)
            dao.deletePlaylist(playlistId)
            deleteAppPlaylistForQqMusicPlaylist(playlistId)
            syncUnifiedLibrarySongsFromQqMusic()
        }
    }

    // ─── Content Sync ──────────────────────────────────────────────────

    enum class PlaylistSyncType {
        CREATED,    // 我创建的歌单
        COLLECTED,  // 我收藏的歌单
        ALL         // 全部
    }

    suspend fun syncUserPlaylists(syncType: PlaylistSyncType = PlaylistSyncType.ALL): Result<List<QqMusicPlaylistEntity>> {
        Timber.d("syncUserPlaylists called, isLoggedIn=$isLoggedIn")
        if (!isLoggedIn) {
            Timber.w("syncUserPlaylists: Not logged in, aborting")
            return Result.failure(Exception("Not logged in"))
        }
        return withContext(Dispatchers.IO) {
            runCatching {
                val entitiesById = LinkedHashMap<Long, QqMusicPlaylistEntity>()
                var start = 0
                var page = 0

                // First, fetch user created playlists
                if (syncType == PlaylistSyncType.CREATED || syncType == PlaylistSyncType.ALL) {
                    Timber.d("syncUserPlaylists: fetching user created playlists")
                    var createdStart = 0
                    var createdPage = 0
                    while (true) {
                        val raw = api.getUserCreatedPlaylists(start = createdStart, size = QQ_USER_PLAYLIST_PAGE_SIZE)
                        Timber.d("syncUserPlaylists: created page=$createdPage start=$createdStart response length=${raw.length}")
                        val root = JSONObject(raw)
                        val code = root.optInt("code", -1)
                        if (code != 0) {
                            Timber.w("getUserCreatedPlaylists API error: code=$code")
                            break
                        }

                        val data = root.optJSONObject("data") ?: break
                        val disslist = data.optJSONArray("disslist") ?: break
                        val fetchedCount = disslist.length()
                        if (fetchedCount == 0) break

                        var newInPage = 0
                        for (i in 0 until fetchedCount) {
                            val pl = disslist.optJSONObject(i) ?: continue
                            val songCount = pl.optInt("song_cnt", 0)
                            if (songCount == 0) continue
                            val id = pl.optLong("tid", 0L)
                            if (id <= 0L) continue
                            val name = pl.optString("diss_name", "")
                            if (name.isBlank()) continue
                            val previous = entitiesById.put(
                                id,
                                QqMusicPlaylistEntity(
                                    id = id,
                                    name = name,
                                    coverUrl = pl.optString("diss_cover", ""),
                                    songCount = songCount,
                                    lastSyncTime = System.currentTimeMillis()
                                )
                            )
                            if (previous == null) newInPage += 1
                        }

                        createdStart += fetchedCount
                        val total = data.optInt("total_cnt", -1)
                        val reachedTotal = total > 0 && createdStart >= total
                        val hasLikelyMore = fetchedCount >= QQ_USER_PLAYLIST_PAGE_SIZE

                        if (reachedTotal || !hasLikelyMore) break
                        if (newInPage == 0 && createdPage > 0) {
                            Timber.w("syncUserPlaylists: created playlists pagination returned no new items at page=$createdPage, stopping")
                            break
                        }

                        createdPage += 1
                        if (createdPage >= QQ_MAX_PLAYLIST_PAGES) {
                            Timber.w("syncUserPlaylists: reached max page guard for created playlists, stopping")
                            break
                        }
                    }
                }

                // Then, fetch collected playlists
                if (syncType == PlaylistSyncType.COLLECTED || syncType == PlaylistSyncType.ALL) {
                    Timber.d("syncUserPlaylists: fetching collected playlists")
                    while (true) {
                        val raw = api.getUserPlaylists(start = start, count = QQ_USER_PLAYLIST_PAGE_SIZE)
                        Timber.d("syncUserPlaylists: page=$page start=$start response length=${raw.length}")
                        val root = JSONObject(raw)
                        val code = root.optInt("code", -1)
                        if (code != 0) {
                            throw Exception("QQ Music API Error: code=$code. Check your login.")
                        }

                        val data = root.optJSONObject("data") ?: break
                        val cdlist = data.optJSONArray("cdlist") ?: break
                        val fetchedCount = cdlist.length()
                        if (fetchedCount == 0) break

                        var newInPage = 0
                        for (i in 0 until fetchedCount) {
                            val pl = cdlist.optJSONObject(i) ?: continue
                            val songCount = pl.optInt("songnum", 0)
                            if (songCount == 0) continue
                            val id = pl.optLong("dissid", 0L)
                            if (id <= 0L) continue
                            val name = decodeBase64IfNeeded(pl.optString("dissname", ""))
                            if (name.isBlank()) continue
                            val previous = entitiesById.put(
                                id,
                                QqMusicPlaylistEntity(
                                    id = id,
                                    name = name,
                                    coverUrl = pl.optString("logo", ""),
                                    songCount = songCount,
                                    lastSyncTime = System.currentTimeMillis()
                                )
                            )
                            if (previous == null) newInPage += 1
                        }

                        start += fetchedCount

                        val totalCandidates = listOf(
                            data.optInt("total", -1),
                            data.optInt("dissnum", -1),
                            data.optInt("totaldissnum", -1),
                            data.optInt("cdnum", -1),
                            data.optInt("dirnum", -1)
                        )
                        val total = totalCandidates.firstOrNull { it > 0 } ?: -1
                        val reachedTotal = total > 0 && start >= total
                        val hasLikelyMore = fetchedCount >= QQ_USER_PLAYLIST_PAGE_SIZE

                        if (reachedTotal || !hasLikelyMore) break
                        if (newInPage == 0 && page > 0) {
                            Timber.w("syncUserPlaylists: pagination returned no new playlists at page=$page, stopping")
                            break
                        }

                        page += 1
                        if (page >= QQ_MAX_PLAYLIST_PAGES) {
                            Timber.w("syncUserPlaylists: reached max page guard ($QQ_MAX_PLAYLIST_PAGES), stopping pagination")
                            break
                        }
                    }
                }

                val entities = entitiesById.values.toList()

                val localPlaylists = dao.getAllPlaylistsList()
                val remoteIds = entities.map { it.id }.toSet()
                val stalePlaylists = localPlaylists.filter { it.id !in remoteIds }

                stalePlaylists.forEach { stale ->
                    dao.deleteSongsByPlaylist(stale.id)
                    dao.deletePlaylist(stale.id)
                    deleteAppPlaylistForQqMusicPlaylist(stale.id)
                }

                entities.forEach { dao.insertPlaylist(it) }
                
                if (stalePlaylists.isNotEmpty()) {
                    syncUnifiedLibrarySongsFromQqMusic()
                }
                
                entities
            }
        }
    }

    suspend fun syncPlaylistSongs(playlistId: Long): Result<Int> {
        return withContext(Dispatchers.IO) {
            runCatching {
                val entitiesById = LinkedHashMap<String, QqMusicSongEntity>()
                var songBegin = 0
                var page = 0
                var expectedSongCount = -1

                while (true) {
                    val raw = api.getPlaylistDetail(
                        playlistId = playlistId,
                        songBegin = songBegin,
                        songNum = QQ_PLAYLIST_SONG_PAGE_SIZE
                    )
                    val root = JSONObject(raw)

                    val code = root.optInt("code", -1)
                    if (code != 0) {
                        throw Exception("API error code=$code")
                    }

                    val cdlist = root.optJSONArray("cdlist") ?: throw Exception("No cdlist")
                    val firstCd = cdlist.optJSONObject(0) ?: throw Exception("Empty cdlist")
                    val songlist = firstCd.optJSONArray("songlist") ?: break
                    val fetchedCount = songlist.length()
                    if (fetchedCount == 0) break

                    if (expectedSongCount < 0) {
                        expectedSongCount = firstCd.optInt("songnum", firstCd.optInt("total_song_num", -1))
                    }

                    var newInPage = 0
                    for (i in 0 until fetchedCount) {
                        val track = songlist.optJSONObject(i) ?: continue
                        val entity = parseTrackToEntity(track, playlistId)
                        if (entity.songMid.isBlank()) continue
                        val previous = entitiesById.put(entity.id, entity)
                        if (previous == null) newInPage += 1
                    }

                    songBegin += fetchedCount
                    val reachedExpected = expectedSongCount > 0 && songBegin >= expectedSongCount
                    val hasLikelyMore = fetchedCount >= QQ_PLAYLIST_SONG_PAGE_SIZE

                    if (reachedExpected || !hasLikelyMore) break
                    if (newInPage == 0) {
                        Timber.w("syncPlaylistSongs: pagination returned no new songs for playlistId=$playlistId at page=$page, stopping")
                        break
                    }

                    page += 1
                    if (page >= QQ_MAX_PLAYLIST_PAGES) {
                        Timber.w("syncPlaylistSongs: reached max page guard ($QQ_MAX_PLAYLIST_PAGES) for playlistId=$playlistId")
                        break
                    }
                }

                val entities = entitiesById.values.toList()
                if (expectedSongCount > 0 && entities.size < expectedSongCount) {
                    Timber.w(
                        "syncPlaylistSongs: playlistId=$playlistId expected=$expectedSongCount synced=${entities.size} (API may still be limiting)"
                    )
                }

                dao.deleteSongsByPlaylist(playlistId)
                dao.insertSongs(entities)

                // Update app playlist
                val playlistName = entities.firstOrNull()?.let { "QQ Music Playlist" } ?: "Playlist $playlistId"
                // Ideally we should get the actual name from the list, but for now we search
                val name = dao.getAllPlaylistsList().find { it.id == playlistId }?.name ?: playlistName
                updateAppPlaylistForQqMusicPlaylist(playlistId, name, entities)

                syncUnifiedLibrarySongsFromQqMusic()

                Timber.d("Synced ${entities.size} songs for QQ Music playlist $playlistId")
                entities.size
            }
        }
    }

    /** [SyncableLibraryProvider] entry point — syncs all playlist types. */
    override suspend fun syncAllPlaylistsAndSongs(): Result<BulkSyncResult> =
        syncAllPlaylistsAndSongs(PlaylistSyncType.ALL)

    suspend fun syncAllPlaylistsAndSongs(syncType: PlaylistSyncType = PlaylistSyncType.ALL): Result<BulkSyncResult> {
        return withContext(Dispatchers.IO) {
            runCatching {
                val playlistResult = syncUserPlaylists(syncType).getOrElse { return@runCatching BulkSyncResult(0, 0, 0) }
                if (playlistResult.isEmpty()) {
                    return@runCatching BulkSyncResult(0, 0, 0)
                }

                var syncedSongCount = 0
                var failedPlaylistCount = 0

                playlistResult.forEach { playlist ->
                    val songSyncResult = syncPlaylistSongs(playlist.id)
                    songSyncResult.fold(
                        onSuccess = { count -> syncedSongCount += count },
                        onFailure = {
                            failedPlaylistCount += 1
                            Timber.w(it, "Failed syncing QQ playlist ${playlist.id}")
                        }
                    )
                }

                syncUnifiedLibrarySongsFromQqMusic()

                BulkSyncResult(
                    playlistCount = playlistResult.size,
                    syncedSongCount = syncedSongCount,
                    failedPlaylistCount = failedPlaylistCount
                )
            }
        }
    }

    private fun parseTrackToEntity(track: JSONObject, playlistId: Long): QqMusicSongEntity {
        // Handle both older and newer field names
        val mid = track.optString("songmid", track.optString("mid", ""))
        val title = decodeBase64IfNeeded(track.optString("songname", track.optString("title", "Unknown")))
        
        val singerArray = track.optJSONArray("singer")
        val artist = if (singerArray != null && singerArray.length() > 0) {
            val names = mutableListOf<String>()
            for (i in 0 until singerArray.length()) {
                names.add(decodeBase64IfNeeded(singerArray.optJSONObject(i)?.optString("name", "") ?: ""))
            }
            names.filter { it.isNotBlank() }.joinToString(", ")
        } else {
            "Unknown Artist"
        }

        val album = decodeBase64IfNeeded(track.optString("albumname", track.optJSONObject("album")?.optString("name", "") ?: ""))
        val albumMid = track.optString("albummid", track.optJSONObject("album")?.optString("mid", "") ?: "")
        val duration = track.optLong("interval", 0) * 1000L  // Convert to milliseconds

        return QqMusicSongEntity(
            id = "${playlistId}_$mid",
            songMid = mid,
            playlistId = playlistId,
            title = title,
            artist = artist,
            album = album,
            albumMid = albumMid,
            duration = duration,
            albumArtUrl = if (albumMid.isNotBlank()) {
                "https://y.qq.com/music/photo_new/T002R300x300M000$albumMid.jpg"
            } else null,
            mimeType = "audio/mpeg",
            bitrate = null,
            dateAdded = System.currentTimeMillis()
        )
    }

    /**
     * Decode Base64-encoded string if it looks like Base64.
     * QQ Music FCG endpoints return some fields as Base64 after Zlib decompression.
     */
    private fun decodeBase64IfNeeded(input: String): String {
        if (input.isBlank()) return input
        // Check if it looks like Base64: only contains A-Za-z0-9+/= and no Chinese/special chars
        val base64Pattern = Regex("^[A-Za-z0-9+/=]+$")
        if (!base64Pattern.matches(input)) return input
        // Must be at least 4 chars and valid length for Base64
        if (input.length < 4) return input
        return try {
            val decoded = Base64.decode(input, Base64.DEFAULT)
            val result = String(decoded, Charsets.UTF_8)
            // Verify the decoded result contains actual readable text
            if (result.isNotBlank() && !result.contains('\u0000')) result else input
        } catch (_: Exception) {
            input
        }
    }

    private fun jsonToMap(json: String): Map<String, String> =
        CloudMusicUtils.jsonToMap(json)

    // ─── Unified Library Sync ──────────────────────────────────────────

    private suspend fun syncUnifiedLibrarySongsFromQqMusic() {
        val qqMusicSongs = dao.getAllQqMusicSongsList()
        val existingUnifiedIds = musicDao.getAllQqMusicSongIds()

        if (qqMusicSongs.isEmpty()) {
            if (existingUnifiedIds.isNotEmpty()) {
                musicDao.clearAllQqMusicSongs()
            }
            return
        }

        val songs = ArrayList<SongEntity>(qqMusicSongs.size)
        val artists = LinkedHashMap<Long, ArtistEntity>()
        val albums = LinkedHashMap<Long, AlbumEntity>()
        val crossRefs = mutableListOf<SongArtistCrossRef>()

        qqMusicSongs.forEach { qqSong ->
            val songId = toUnifiedSongId(qqSong.songMid)
            val artistNames = parseArtistNames(qqSong.artist)
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

            val albumId = toUnifiedAlbumId(qqSong.albumMid ?: "", qqSong.album)
            val albumName = qqSong.album.ifBlank { "Unknown Album" }
            albums.putIfAbsent(
                albumId,
                AlbumEntity(
                    id = albumId,
                    title = albumName,
                    artistName = primaryArtistName,
                    artistId = primaryArtistId,
                    songCount = 0,
                    dateAdded = qqSong.dateAdded,
                    year = 0,
                    albumArtUriString = qqSong.albumArtUrl
                )
            )

            songs.add(
                SongEntity(
                    id = songId,
                    title = qqSong.title,
                    artistName = qqSong.artist.ifBlank { primaryArtistName },
                    artistId = primaryArtistId,
                    albumArtist = null,
                    albumName = albumName,
                    albumId = albumId,
                    contentUriString = "qqmusic://${qqSong.songMid}",
                    albumArtUriString = qqSong.albumArtUrl,
                    duration = qqSong.duration,
                    genre = QQ_MUSIC_GENRE,
                    filePath = "",
                    parentDirectoryPath = QQ_MUSIC_PARENT_DIRECTORY,
                    isFavorite = false,
                    lyrics = null,
                    trackNumber = 0,
                    year = 0,
                    dateAdded = qqSong.dateAdded.takeIf { it > 0 } ?: System.currentTimeMillis(),
                    mimeType = qqSong.mimeType,
                    bitrate = qqSong.bitrate,
                    sampleRate = null,
                    telegramChatId = null,
                    telegramFileId = null,
                    sourceType = SourceType.QQMUSIC
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

    private fun parseArtistNames(rawArtist: String): List<String> =
        CloudMusicUtils.parseArtistNames(rawArtist)

    private fun toUnifiedSongId(mid: String): Long {
        val hash = mid.hashCode().toLong().absoluteValue
        return -(QQ_MUSIC_SONG_ID_OFFSET + hash)
    }

    private fun toUnifiedAlbumId(albumMid: String, albumName: String): Long {
        val normalized = if (albumMid.isNotBlank()) albumMid.hashCode().toLong().absoluteValue 
                         else albumName.lowercase().hashCode().toLong().absoluteValue
        return -(QQ_MUSIC_ALBUM_ID_OFFSET + normalized)
    }

    private fun toUnifiedArtistId(artistName: String): Long {
        return -(QQ_MUSIC_ARTIST_ID_OFFSET + artistName.lowercase().hashCode().toLong().absoluteValue)
    }

    // ─── App Playlist Management ────────────────────────────────────────

    private suspend fun getAppPlaylistIdForQqMusic(qqPlaylistId: Long): String {
        return "$QQ_MUSIC_PLAYLIST_PREFIX$qqPlaylistId"
    }

    private suspend fun updateAppPlaylistForQqMusicPlaylist(
        qqPlaylistId: Long,
        playlistName: String,
        qqEntities: List<QqMusicSongEntity>
    ) {
        try {
            val unifiedSongIds = qqEntities.map { entity ->
                toUnifiedSongId(entity.songMid).toString()
            }

            val appPlaylistId = getAppPlaylistIdForQqMusic(qqPlaylistId)
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
                        source = "QQMUSIC"
                    )
                )
                Timber.d("Updated app playlist for QQ Music playlist $qqPlaylistId: $playlistName")
            } else {
                playlistPreferencesRepository.createPlaylist(
                    name = playlistName,
                    songIds = unifiedSongIds,
                    customId = appPlaylistId,
                    source = "QQMUSIC"
                )
                Timber.d("Created new app playlist for QQ Music playlist $qqPlaylistId: $playlistName")
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to update/create app playlist for QQ Music playlist $qqPlaylistId")
        }
    }

    private suspend fun deleteAppPlaylistForQqMusicPlaylist(qqPlaylistId: Long) {
        try {
            val appPlaylistId = getAppPlaylistIdForQqMusic(qqPlaylistId)
            playlistPreferencesRepository.deletePlaylist(appPlaylistId)
            Timber.d("Deleted app playlist for QQ Music playlist $qqPlaylistId")
        } catch (e: Exception) {
            Timber.w(e, "Failed to delete app playlist for QQ Music playlist $qqPlaylistId")
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// QqMusicStreamProxy.kt
// ═══════════════════════════════════════════════════════════════════════════


@Singleton
class QqMusicStreamProxy @Inject constructor(
    private val repository: QqMusicRepository,
    okHttpClient: OkHttpClient
) : CloudStreamProxy<String>(okHttpClient) {

    override val allowedHostSuffixes = setOf(
        "qqmusic.qq.com",
        "stream.qqmusic.qq.com",
        "dl.stream.qqmusic.qq.com",
        "qq.com"
    )
    override val cacheExpirationMs = 10L * 60 * 1000
    override val proxyTag = "QqMusicStreamProxy"
    override val routePath = "/qqmusic/{songMid}"
    override val routeParamName = "songMid"
    override val uriScheme = "qqmusic"
    override val routePrefix = "/qqmusic"

    override fun parseRouteParam(value: String): String? =
        value.takeIf { it.isNotBlank() }

    override fun validateId(id: String): Boolean =
        CloudStreamSecurity.validateQqMusicSongMid(id)

    override fun formatIdForUrl(id: String): String = id

    override suspend fun resolveStreamUrl(id: String): String? =
        repository.getSongUrl(id).getOrNull()

    // QQ Music URIs may use host or path: qqmusic://songMid or qqmusic:///songMid
    override fun extractIdFromUri(uri: Uri): String? =
        uri.host ?: uri.path?.removePrefix("/")

    fun resolveQqMusicUri(uriString: String): String? = resolveUri(uriString)

    /**
     * Pre-fetches and caches the real stream URL for a song so the proxy can
     * serve it instantly when ExoPlayer makes its HTTP request.
     */
    suspend fun warmUpStreamUrl(uriString: String) {
        val uri = Uri.parse(uriString)
        if (uri.scheme != "qqmusic") return
        val songMid = uri.host ?: uri.path?.removePrefix("/") ?: return
        if (!CloudStreamSecurity.validateQqMusicSongMid(songMid)) return
        try {
            getOrFetchStreamUrl(songMid)
        } catch (e: Exception) {
            Timber.w(e, "warmUpStreamUrl failed for $songMid")
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// TelegramRepository.kt
// ═══════════════════════════════════════════════════════════════════════════



@Singleton
class TelegramRepository @Inject constructor(
    private val clientManager: TelegramClientManager,
    private val dao: TelegramDao,
    private val playlistPreferencesRepository: PlaylistPreferencesRepository
) : RemoteMusicProvider {
    private companion object {
        private const val AUTH_REQUEST_TIMEOUT_MS = 20_000L
        private const val TELEGRAM_PLAYLIST_PREFIX = "telegram_channel:"
        private const val TELEGRAM_TOPIC_PLAYLIST_PREFIX = "telegram_topic:"
    }

    override val scheme: String = "telegram"
    override val displayName: String = "Telegram"

    val authorizationState: Flow<TdApi.AuthorizationState?> = clientManager.authorizationState
    val authErrors: SharedFlow<TdApi.Error> = clientManager.errors

    fun clearMemoryCache() {
        resolvedPathCache.clear()
        Timber.d("TelegramRepository: Memory cache cleared")
    }

    fun isReady(): Boolean = clientManager.isReady()

    suspend fun awaitReady(timeoutMs: Long = 30_000L): Boolean =
        clientManager.awaitReady(timeoutMs)

    fun sendPhoneNumber(phoneNumber: String) {
        clientManager.sendPhoneNumber(phoneNumber)
    }

    suspend fun sendPhoneNumberAwait(
        phoneNumber: String,
        timeoutMs: Long = AUTH_REQUEST_TIMEOUT_MS
    ): Result<Unit> = runAuthRequest(timeoutMs) {
        val settings = TdApi.PhoneNumberAuthenticationSettings()
        clientManager.sendRequest<TdApi.Ok>(
            TdApi.SetAuthenticationPhoneNumber(phoneNumber, settings)
        )
    }

    fun checkAuthenticationCode(code: String) {
        clientManager.checkAuthenticationCode(code)
    }

    suspend fun checkAuthenticationCodeAwait(
        code: String,
        timeoutMs: Long = AUTH_REQUEST_TIMEOUT_MS
    ): Result<Unit> = runAuthRequest(timeoutMs) {
        clientManager.sendRequest<TdApi.Ok>(TdApi.CheckAuthenticationCode(code))
    }

    fun checkAuthenticationPassword(password: String) {
        clientManager.checkAuthenticationPassword(password)
    }

    suspend fun checkAuthenticationPasswordAwait(
        password: String,
        timeoutMs: Long = AUTH_REQUEST_TIMEOUT_MS
    ): Result<Unit> = runAuthRequest(timeoutMs) {
        clientManager.sendRequest<TdApi.Ok>(TdApi.CheckAuthenticationPassword(password))
    }

    fun logout() {
        clientManager.logout()
    }

    private suspend fun runAuthRequest(
        timeoutMs: Long,
        block: suspend () -> TdApi.Object
    ): Result<Unit> {
        return try {
            withTimeout(timeoutMs) { block() }
            Result.success(Unit)
        } catch (timeout: TimeoutCancellationException) {
            Result.failure(IllegalStateException("Telegram did not respond in ${timeoutMs / 1000}s.", timeout))
        } catch (error: Throwable) {
            Result.failure(error)
        }
    }

    suspend fun searchPublicChat(username: String): TdApi.Chat? {
        return try {
            clientManager.sendRequest(TdApi.SearchPublicChat(username))
        } catch (e: Exception) {
            Timber.e(e, "Error searching public chat: $username")
            null
        }
    }

    // ─── Forum Topic Support ──────────────────────────────────────────────────

    /**
     * Returns true if [chatId] is a supergroup with Forum mode enabled.
     * Regular broadcast channels always return false.
     */
    suspend fun isForum(chatId: Long): Boolean {
        return try {
            val chat = clientManager.sendRequest<TdApi.Chat>(TdApi.GetChat(chatId))
            val type = chat.type
            if (type !is TdApi.ChatTypeSupergroup) return false
            val supergroup = clientManager.sendRequest<TdApi.Supergroup>(
                TdApi.GetSupergroup(type.supergroupId)
            )
            supergroup.isForum
        } catch (e: Exception) {
            Timber.w(e, "isForum check failed for chatId=$chatId")
            false
        }
    }

    /**
     * Fetches all forum topics for a supergroup.
     * Returns empty list for non-forum chats.
     */

    suspend fun getForumTopics(chatId: Long): List<TelegramTopicEntity> {
        val topics = mutableListOf<TelegramTopicEntity>()
        try {
            var offsetDate = 0
            var offsetMessageId = 0L          // int53 → Long ✓
            var offsetForumTopicId = 0        // int32 → Int ✓

            while (true) {
                val request = TdApi.GetForumTopics().apply {
                    this.chatId = chatId
                    this.query = ""
                    this.offsetDate = offsetDate              // Int ✓
                    this.offsetMessageId = offsetMessageId   // Long ✓
                    this.offsetForumTopicId = offsetForumTopicId  // Int ✓
                    this.limit = 100
                }
                val result = clientManager.sendRequest<TdApi.ForumTopics>(request)

                if (result.topics.isEmpty()) break

                for (topic in result.topics) {
                    val info = topic.info
                    val emojiId = info.icon.customEmojiId

                    // Resolve the thread ID from ForumTopicInfo via reflection.
                    // We only accept Long/Int fields with a non-zero value whose name
                    // looks like a thread/message identifier. We skip fields named
                    // exactly "id" because in many TDLib Java builds that field is a
                    // String composite key, not the numeric thread ID.
                    val threadId: Long = run {
                        // Log all fields once so we can confirm the correct name in Logcat
                        val allFields = info.javaClass.declaredFields
                        Timber.d("ForumTopicInfo fields: ${allFields.map { "${it.name}:${it.type.simpleName}" }}")

                        var resolved = 0L
                        // Prefer the most specific name first, skip bare "id" (likely String)
                        val preferredNames = listOf(
                            "messageThreadId", "message_thread_id",
                            "threadId", "thread_id",
                            "topicId", "topic_id",
                            "forumTopicId", "forum_topic_id"
                        )
                        for (name in preferredNames) {
                            try {
                                val f = info.javaClass.getDeclaredField(name)
                                f.isAccessible = true
                                val v = f.get(info)
                                val candidate = when (v) {
                                    is Long -> v
                                    is Int  -> v.toLong()
                                    else    -> 0L
                                }
                                if (candidate != 0L) {
                                    Timber.d("ForumTopicInfo: resolved threadId via field '$name' = $candidate")
                                    resolved = candidate
                                    break
                                }
                            } catch (_: NoSuchFieldException) { }
                        }

                        // Last resort: scan ALL Long/Int fields for the first non-zero value
                        // that isn't a known non-thread field
                        if (resolved == 0L) {
                            val skipNames = setOf("chatId", "chat_id", "creatorUserId",
                                "creator_user_id", "customEmojiId", "custom_emoji_id",
                                "editDate", "edit_date", "date")
                            for (f in allFields) {
                                if (f.name in skipNames) continue
                                if (f.type != Long::class.java && f.type != Int::class.java) continue
                                try {
                                    f.isAccessible = true
                                    val candidate = when (val v = f.get(info)) {
                                        is Long -> v
                                        is Int  -> v.toLong()
                                        else    -> 0L
                                    }
                                    if (candidate != 0L) {
                                        Timber.w("ForumTopicInfo: fallback threadId via field '${f.name}' = $candidate")
                                        resolved = candidate
                                        break
                                    }
                                } catch (_: Exception) { }
                            }
                        }

                        if (resolved == 0L) {
                            Timber.e("ForumTopicInfo: could not resolve threadId for topic '${info.name}'")
                        }
                        resolved
                    }

                    // Only add topics where we successfully resolved a thread ID
                    if (threadId != 0L) {
                        topics.add(
                            TelegramTopicEntity(
                                id = "${chatId}_${threadId}",
                                chatId = chatId,
                                threadId = threadId,
                                name = info.name,
                                iconEmoji = if (emojiId != 0L) emojiId.toString() else null
                            )
                        )
                    }
                }

                if (result.nextOffsetDate == 0) break

                offsetDate         = result.nextOffsetDate           // Int ✓
                offsetMessageId    = result.nextOffsetMessageId      // Long ✓
                offsetForumTopicId = result.nextOffsetForumTopicId   // Int ✓
            }

            Timber.d("Fetched ${topics.size} forum topics for chat $chatId")
        } catch (e: Exception) {
            Timber.e(e, "Error fetching forum topics for chat $chatId")
        }
        return topics
    }

    suspend fun getAudioMessagesByTopic(chatId: Long, threadId: Long): List<Song> {
        Timber.d("Fetching audio for topic threadId=$threadId in chat=$chatId")
        try {
            clientManager.sendRequest<TdApi.Ok>(TdApi.OpenChat(chatId))
        } catch (e: Exception) {
            Timber.w("Failed to open chat: $chatId")
        }

        val allSongs = mutableListOf<Song>()
        var nextFromMessageId = 0L
        val batchSize = 100

        try {
            while (true) {
                val request = TdApi.SearchChatMessages().apply {
                    this.chatId = chatId
                    this.query = ""
                    this.senderId = null
                    this.fromMessageId = nextFromMessageId
                    this.offset = 0
                    this.limit = batchSize
                    this.filter = TdApi.SearchMessagesFilterAudio()

                    // Set the topic/thread filter via reflection to handle different TDLib builds.
                    // In newer builds the field is 'topicId' (MessageTopic object).
                    // In older builds it was 'messageThreadId' (Long).
                    val scFields = this.javaClass.declaredFields
                    Timber.d("SearchChatMessages fields: ${scFields.map { "${it.name}:${it.type.simpleName}" }}")

                    var topicSet = false

                    // Try 'topicId' field (newer TDLib — expects a MessageTopic object)
                    try {
                        val f = this.javaClass.getDeclaredField("topicId")
                        f.isAccessible = true
                        // MessageTopicForum wraps the thread ID as Int
                        f.set(this, TdApi.MessageTopicForum(threadId.toInt()))
                        Timber.d("SearchChatMessages: set topicId = MessageTopicForum($threadId)")
                        topicSet = true
                    } catch (_: NoSuchFieldException) { }

                    // Fallback: try 'messageThreadId' field (older TDLib — Long)
                    if (!topicSet) {
                        try {
                            val f = this.javaClass.getDeclaredField("messageThreadId")
                            f.isAccessible = true
                            f.set(this, threadId)
                            Timber.d("SearchChatMessages: set messageThreadId = $threadId")
                            topicSet = true
                        } catch (_: NoSuchFieldException) { }
                    }

                    if (!topicSet) {
                        Timber.e("SearchChatMessages: could not set topic filter — results will be unfiltered")
                    }
                }

                val response = clientManager.sendRequest<TdApi.FoundChatMessages>(request)

                if (response.messages.isEmpty()) break

                response.messages.forEach { message ->
                    mapMessageToSong(message)?.let { allSongs.add(it) }
                }

                nextFromMessageId = response.nextFromMessageId
                if (nextFromMessageId == 0L) break
            }
            Timber.d("Topic $threadId: fetched ${allSongs.size} songs")
        } catch (e: Exception) {
            Timber.e(e, "Error fetching audio for topic $threadId in chat $chatId")
        }
        return allSongs
    }


    // ─── Full-channel fetch

    suspend fun getAudioMessages(chatId: Long): List<Song> {
        Timber.d("Fetching chat history for chat: $chatId")
        try {
            clientManager.sendRequest<TdApi.Ok>(TdApi.OpenChat(chatId))
        } catch (e: Exception) {
            Timber.w("Failed to open chat: $chatId")
        }

        val allSongs = mutableListOf<Song>()
        var nextFromMessageId = 0L
        val batchSize = 100

        try {
            while (true) {
                val request = TdApi.SearchChatMessages().apply {
                    this.chatId = chatId
                    this.query = ""
                    this.senderId = null
                    this.fromMessageId = nextFromMessageId
                    this.offset = 0
                    this.limit = batchSize
                    this.filter = TdApi.SearchMessagesFilterAudio()
                }

                val response = clientManager.sendRequest<TdApi.FoundChatMessages>(request)

                if (response.messages.isEmpty()) break

                response.messages.forEach { message ->
                    mapMessageToSong(message)?.let { allSongs.add(it) }
                }

                nextFromMessageId = response.nextFromMessageId
                if (nextFromMessageId == 0L) break
            }
            Timber.d("Total mapped audio songs: ${allSongs.size}")
            return allSongs
        } catch (e: Exception) {
            Timber.e(e, "Error fetching chat history for chat $chatId")
            return allSongs
        }
    }

    private suspend fun mapMessageToSong(message: TdApi.Message): Song? {
        val content = message.content

        return when (content) {
            is TdApi.MessageAudio -> {
                val audio = content.audio

                var albumArtPath: String? = null
                var thumbnail = audio.albumCoverThumbnail

                if (thumbnail == null && audio.externalAlbumCovers?.isNotEmpty() == true) {
                    thumbnail = audio.externalAlbumCovers.maxByOrNull { it.width * it.height }
                }

                if (thumbnail != null) {
                    albumArtPath = "telegram_art://${message.chatId}/${message.id}"
                    if (thumbnail.file.local.isDownloadingCompleted && thumbnail.file.local.path.isNotEmpty()) {
                        resolvedPathCache[thumbnail.file.id] = thumbnail.file.local.path
                    }
                }

                Song(
                    id = "${message.chatId}_${message.id}",
                    title = audio.title.takeIf { it.isNotEmpty() }
                        ?: audio.fileName.substringBeforeLast('.').ifEmpty { "Unknown Title" },
                    artist = audio.performer.takeIf { it.isNotEmpty() } ?: "Unknown Artist",
                    artistId = -1,
                    album = "Telegram Stream",
                    albumId = -1,
                    path = "",
                    contentUriString = "telegram://${message.chatId}/${message.id}",
                    albumArtUriString = albumArtPath,
                    duration = audio.duration * 1000L,
                    telegramFileId = audio.audio.id,
                    telegramChatId = message.chatId,
                    mimeType = audio.mimeType,
                    bitrate = 0,
                    sampleRate = 0,
                    year = 0,
                    trackNumber = 0,
                    dateAdded = message.date.toLong(),
                    isFavorite = false
                )
            }
            is TdApi.MessageDocument -> {
                val document = content.document

                val isAudioMime = document.mimeType.startsWith("audio/") || document.mimeType == "application/ogg"
                val isAudioExtension = document.fileName.lowercase().run {
                    endsWith(".mp3") || endsWith(".flac") || endsWith(".wav") ||
                            endsWith(".m4a") || endsWith(".ogg") || endsWith(".aac")
                }

                if (isAudioMime || isAudioExtension) {
                    var albumArtPath: String? = null
                    val thumbnail = document.thumbnail
                    if (thumbnail != null) {
                        albumArtPath = "telegram_art://${message.chatId}/${message.id}"
                        if (thumbnail.file.local.isDownloadingCompleted && thumbnail.file.local.path.isNotEmpty()) {
                            resolvedPathCache[thumbnail.file.id] = thumbnail.file.local.path
                        }
                    }

                    Song(
                        id = "${message.chatId}_${message.id}",
                        title = document.fileName.substringBeforeLast('.').ifEmpty { "Unknown Track" },
                        artist = "Telegram Audio",
                        artistId = -1,
                        album = "Telegram Stream",
                        albumId = -1,
                        path = "",
                        contentUriString = "telegram://${message.chatId}/${message.id}",
                        albumArtUriString = albumArtPath,
                        duration = 0L,
                        telegramFileId = document.document.id,
                        telegramChatId = message.chatId,
                        mimeType = document.mimeType,
                        bitrate = 0,
                        sampleRate = 0,
                        year = 0,
                        trackNumber = 0,
                        dateAdded = message.date.toLong(),
                        isFavorite = false
                    )
                } else null
            }
            else -> null
        }
    }

    suspend fun downloadFile(fileId: Int, priority: Int = 1): TdApi.File? {
        return try {
            clientManager.sendRequest(TdApi.DownloadFile(fileId, priority, 0, 0, false))
        } catch (e: Exception) {
            Timber.e(e, "Error evaluating DownloadFile for fileId: $fileId")
            null
        }
    }

    suspend fun getFile(fileId: Int): TdApi.File? {
        return try {
            clientManager.sendRequest(TdApi.GetFile(fileId))
        } catch (e: Exception) {
            null
        }
    }

    suspend fun getMessage(chatId: Long, messageId: Long): TdApi.Message? {
        return try {
            clientManager.sendRequest(TdApi.GetMessage(chatId, messageId))
        } catch (e: Exception) {
            Timber.e(e, "Error fetching message: $chatId / $messageId")
            null
        }
    }

    suspend fun isFileCached(fileId: Int): Boolean {
        resolvedPathCache[fileId]?.let { path ->
            if (java.io.File(path).exists()) return true
            resolvedPathCache.remove(fileId)
        }
        val file = getFile(fileId)
        return file?.local?.isDownloadingCompleted == true &&
                file.local.path.isNotEmpty() &&
                java.io.File(file.local.path).exists()
    }

    suspend fun resolveTelegramUri(uriString: String): Pair<Int, Long>? {
        uriResolutionCache[uriString]?.let { return it }

        val uri = android.net.Uri.parse(uriString)
        if (uri.scheme != "telegram") return null

        val chatId = uri.host?.toLongOrNull()
        val messageId = uri.pathSegments.firstOrNull()?.toLongOrNull()
        if (chatId == null || messageId == null) return null

        val message = getMessage(chatId, messageId) ?: return null

        val result = when (val content = message.content) {
            is TdApi.MessageAudio -> Pair(content.audio.audio.id, content.audio.audio.size)
            is TdApi.MessageDocument -> Pair(content.document.document.id, content.document.document.size)
            else -> null
        }

        if (result != null) uriResolutionCache[uriString] = result
        return result
    }

    fun preResolveTelegramUri(uriString: String) {
        if (uriResolutionCache.containsKey(uriString)) return
        repositoryScope.launch {
            try { resolveTelegramUri(uriString) } catch (e: Exception) { /* ignore */ }
        }
    }

    suspend fun refreshMessage(chatId: Long, messageId: Long): TdApi.Message? {
        return try {
            val history = clientManager.sendRequest<TdApi.Messages>(
                TdApi.GetChatHistory(chatId, messageId, 0, 1, false)
            )
            history.messages.firstOrNull { it.id == messageId }
                ?: clientManager.sendRequest(TdApi.GetMessage(chatId, messageId))
        } catch (e: Exception) {
            Timber.e(e, "Error refreshing message: $messageId")
            null
        }
    }

    private val resolvedPathCache = java.util.concurrent.ConcurrentHashMap<Int, String>()
    private val uriResolutionCache = java.util.concurrent.ConcurrentHashMap<String, Pair<Int, Long>>()
    private val repositoryScope = kotlinx.coroutines.CoroutineScope(
        kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO
    )
    private val activeDownloads = java.util.concurrent.ConcurrentHashMap<Int, kotlinx.coroutines.Deferred<String?>>()
    private val downloadSemaphore = kotlinx.coroutines.sync.Semaphore(4)

    private val _downloadCompleted = MutableSharedFlow<Int>(extraBufferCapacity = 16)
    val downloadCompleted: SharedFlow<Int> = _downloadCompleted.asSharedFlow()
    private val _songFileUpdated = MutableSharedFlow<String>(extraBufferCapacity = 16)
    val songFileUpdated: SharedFlow<String> = _songFileUpdated.asSharedFlow()

    fun warmUpArtworkForSongs(
        songs: List<TelegramSongEntity>,
        maxSongs: Int = 24
    ) {
        val targets = songs.asSequence()
            .map { it.chatId to it.messageId }
            .distinct()
            .take(maxSongs)
            .toList()

        if (targets.isEmpty()) return

        repositoryScope.launch {
            targets.forEach { (chatId, messageId) ->
                try {
                    warmUpArtwork(chatId, messageId)
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Timber.v(e, "Artwork warm-up failed for $chatId/$messageId")
                }
            }
        }
    }

    private suspend fun warmUpArtwork(chatId: Long, messageId: Long) {
        val message = getMessage(chatId, messageId) ?: return
        val fileId = extractArtworkFileId(message.content) ?: return
        val existingFile = getFile(fileId)
        if (existingFile?.local?.isDownloadingCompleted == true && existingFile.local.path.isNotEmpty()) {
            resolvedPathCache[fileId] = existingFile.local.path
            return
        }
        downloadFileAwait(fileId, priority = 1)
    }

    private fun extractArtworkFileId(content: TdApi.MessageContent?): Int? {
        return when (content) {
            is TdApi.MessageAudio -> {
                val thumbnail = content.audio.albumCoverThumbnail
                    ?: content.audio.externalAlbumCovers?.maxByOrNull { it.width * it.height }
                thumbnail?.file?.id
            }
            is TdApi.MessageDocument -> content.document.thumbnail?.file?.id
            else -> null
        }
    }

    private suspend fun persistSongFilePathIfNeeded(fileId: Int, path: String?) {
        if (path.isNullOrBlank()) return

        val existingSong = dao.getSongByFileId(fileId) ?: return
        if (existingSong.filePath == path) return

        dao.insertSongs(listOf(existingSong.copy(filePath = path)))
        _songFileUpdated.tryEmit(existingSong.id)
    }

    suspend fun downloadFileAwait(fileId: Int, priority: Int = 1): String? {
        resolvedPathCache[fileId]?.let { path ->
            if (java.io.File(path).exists()) return path
            resolvedPathCache.remove(fileId)
        }

        val existingJob = activeDownloads[fileId]
        if (existingJob != null && existingJob.isActive) return existingJob.await()

        val newJob = repositoryScope.async(start = kotlinx.coroutines.CoroutineStart.LAZY) {
            try {
                downloadSemaphore.withPermit {
                    val currentFile = getFile(fileId)
                    if (currentFile?.local?.isDownloadingCompleted == true) {
                        currentFile.local.path.takeIf { it.isNotEmpty() }?.let {
                            resolvedPathCache[fileId] = it
                            persistSongFilePathIfNeeded(fileId, it)
                            _downloadCompleted.tryEmit(fileId)
                            return@withPermit it
                        }
                    }

                    val initialFile = getFile(fileId)
                    val isSmallFile = initialFile?.size == 0L || (initialFile?.size ?: 0) < 1024 * 1024

                    if (isSmallFile) {
                        return@withPermit try {
                            val resultFile = withTimeout(15_000L) {
                                clientManager.sendRequest<TdApi.File>(TdApi.DownloadFile(fileId, priority, 0, 0, true))
                            }
                            if (resultFile.local.isDownloadingCompleted && resultFile.local.path.isNotEmpty()) {
                                resolvedPathCache[fileId] = resultFile.local.path
                                persistSongFilePathIfNeeded(fileId, resultFile.local.path)
                                _downloadCompleted.tryEmit(fileId)
                                resultFile.local.path
                            } else null
                        } catch (e: kotlinx.coroutines.CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            if (e.message?.contains("canceled") != true && e.message?.contains("has failed") != true) {
                                Timber.w("Sync download failed for $fileId: ${e.message}")
                            }
                            null
                        }
                    }

                    try {
                        clientManager.sendRequest<TdApi.File>(TdApi.DownloadFile(fileId, priority, 0, 0, false))
                    } catch (e: Exception) {
                        Timber.w("Async download request failed for $fileId: ${e.message}")
                        return@withPermit null
                    }

                    val completedPath = withTimeoutOrNull(60_000L) {
                        clientManager.updates
                            .filterIsInstance<TdApi.UpdateFile>()
                            .filter { it.file.id == fileId }
                            .first { update ->
                                val file = update.file
                                when {
                                    file.local.isDownloadingCompleted && file.local.path.isNotEmpty() -> true
                                    !file.local.canBeDownloaded -> throw Exception("File cannot be downloaded")
                                    else -> false
                                }
                            }
                            .file.local.path
                    }

                    if (completedPath != null) {
                        resolvedPathCache[fileId] = completedPath
                        persistSongFilePathIfNeeded(fileId, completedPath)
                        _downloadCompleted.tryEmit(fileId)
                        return@withPermit completedPath
                    }

                    val finalFile = getFile(fileId)
                    return@withPermit if (finalFile?.local?.isDownloadingCompleted == true && finalFile.local.path.isNotEmpty()) {
                        persistSongFilePathIfNeeded(fileId, finalFile.local.path)
                        _downloadCompleted.tryEmit(fileId)
                        finalFile.local.path
                    } else null
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w("downloadFileAwait error for $fileId: ${e.message}")
                throw e
            } finally {
                activeDownloads.remove(fileId)
            }
        }

        activeDownloads[fileId] = newJob
        return try {
            newJob.start()
            newJob.await()
        } catch (e: kotlinx.coroutines.CancellationException) {
            newJob.cancel(e)
            throw e
        }
    }

    // ─── App Playlist Management ──────────────────────────────────────────────

    private fun getAppPlaylistIdForChannel(chatId: Long) = "$TELEGRAM_PLAYLIST_PREFIX$chatId"
    private fun getAppPlaylistIdForTopic(chatId: Long, threadId: Long) =
        "$TELEGRAM_TOPIC_PLAYLIST_PREFIX${chatId}_$threadId"

    private fun toUnifiedTelegramSongId(telegramSongId: String): Long {
        val songId = -(telegramSongId.hashCode().toLong().absoluteValue)
        return if (songId == 0L) -1L else songId
    }

    /** Creates/updates the whole-channel playlist (used for non-forum channels). */
    suspend fun updateAppPlaylistForTelegramChannel(
        chatId: Long,
        channelTitle: String,
        telegramEntities: List<TelegramSongEntity>
    ) {
        try {
            val unifiedSongIds = telegramEntities.map { toUnifiedTelegramSongId(it.id).toString() }
            upsertPlaylist(getAppPlaylistIdForChannel(chatId), channelTitle, unifiedSongIds, "TELEGRAM")
        } catch (e: Exception) {
            Timber.e(e, "Failed to update app playlist for Telegram channel $chatId")
        }
    }

    /** Creates/updates a per-topic playlist. */
    suspend fun updateAppPlaylistForTopic(
        chatId: Long,
        threadId: Long,
        topicName: String,
        telegramEntities: List<TelegramSongEntity>
    ) {
        try {
            val unifiedSongIds = telegramEntities.map { toUnifiedTelegramSongId(it.id).toString() }
            upsertPlaylist(
                getAppPlaylistIdForTopic(chatId, threadId),
                topicName,
                unifiedSongIds,
                "TELEGRAM_TOPIC"
            )
        } catch (e: Exception) {
            Timber.e(e, "Failed to update app playlist for topic $threadId in chat $chatId")
        }
    }

    private suspend fun upsertPlaylist(
        playlistId: String,
        name: String,
        songIds: List<String>,
        source: String
    ) {
        val existing = withContext(Dispatchers.IO) {
            playlistPreferencesRepository.userPlaylistsFlow
                .map { it.find { p -> p.id == playlistId } }
                .first()
        }

        if (existing != null) {
            playlistPreferencesRepository.updatePlaylist(
                existing.copy(
                    name = name,
                    songIds = songIds,
                    lastModified = System.currentTimeMillis(),
                    source = source
                )
            )
        } else {
            playlistPreferencesRepository.createPlaylist(
                name = name,
                songIds = songIds,
                customId = playlistId,
                source = source
            )
        }
    }

    suspend fun deleteAppPlaylistForTelegramChannel(chatId: Long) {
        try {
            playlistPreferencesRepository.deletePlaylist(getAppPlaylistIdForChannel(chatId))
        } catch (e: Exception) {
            Timber.w(e, "Failed to delete app playlist for Telegram channel $chatId")
        }
    }

    suspend fun deleteAppPlaylistForTopic(chatId: Long, threadId: Long) {
        try {
            playlistPreferencesRepository.deletePlaylist(getAppPlaylistIdForTopic(chatId, threadId))
        } catch (e: Exception) {
            Timber.w(e, "Failed to delete app playlist for topic $threadId in chat $chatId")
        }
    }

    /** Deletes all topic playlists for a given channel (used when removing a channel). */
    suspend fun deleteAllTopicPlaylistsForChannel(chatId: Long) {
        try {
            val all = withContext(Dispatchers.IO) {
                playlistPreferencesRepository.userPlaylistsFlow.first()
            }
            val prefix = "$TELEGRAM_TOPIC_PLAYLIST_PREFIX${chatId}_"
            all.filter { it.id.startsWith(prefix) }.forEach {
                playlistPreferencesRepository.deletePlaylist(it.id)
            }
        } catch (e: Exception) {
            Timber.w(e, "Failed to delete topic playlists for channel $chatId")
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// TelegramStreamProxy.kt
// ═══════════════════════════════════════════════════════════════════════════


@Singleton
class TelegramStreamProxy @Inject constructor(
    private val telegramRepository: TelegramRepository
) {
    private var server: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>? = null
    private val proxyScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var startJob: Job? = null

    private fun createServer(port: Int): EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration> {
        return embeddedServer(CIO, port = port, host = "127.0.0.1") {
            routing {
                get("/stream/{fileId}") {
                    val fileId = call.parameters["fileId"]?.toIntOrNull()
                    if (fileId == null || !CloudStreamSecurity.validateTelegramFileId(fileId)) {
                        call.respond(HttpStatusCode.BadRequest, "Invalid File ID")
                        return@get
                    }

                    LogUtils.d("StreamProxy", "Request for fileId: $fileId")
                    
                    // Wait for TDLib to be ready before attempting download
                    // This fixes playback failures after app restart
                    if (!telegramRepository.isReady()) {
                        LogUtils.w("StreamProxy", "TDLib not ready, waiting...")
                        val ready = telegramRepository.awaitReady(10_000L) // 10 second timeout
                        if (!ready) {
                            LogUtils.e("StreamProxy", null, "TDLib not ready after timeout")
                            call.respond(HttpStatusCode.ServiceUnavailable, "Telegram client not ready")
                            return@get
                        }
                        LogUtils.d("StreamProxy", "TDLib ready, proceeding with request")
                    }
                    
                    // 1. Ensure download is started/active
                    var fileInfo = telegramRepository.downloadFile(fileId, 1)
                    
                    // 2. Wait for path to be assigned (TDLib might take a moment to allocate the file path)
                    var pathWaitCount = 0
                    while (fileInfo?.local?.path.isNullOrEmpty() && pathWaitCount < 50) { // Wait up to 2.5s
                        delay(50)
                        fileInfo = telegramRepository.getFile(fileId)
                        pathWaitCount++
                    }

                    if (fileInfo?.local?.path.isNullOrEmpty()) {
                        LogUtils.e("StreamProxy", null, "downloadFile returned null/empty path for fileId: $fileId after waiting")
                        call.respond(HttpStatusCode.InternalServerError, "Could not get file path")
                        return@get
                    }
                    
                    val path = fileInfo.local.path
                    var expectedSize = fileInfo.expectedSize

                    // Optional hint from caller; only trusted within sane limits.
                    val knownSize = call.parameters["size"]?.toLongOrNull()?.takeIf {
                        it in 1..CloudStreamSecurity.MAX_STREAM_CONTENT_LENGTH_BYTES
                    } ?: 0L
                    if (knownSize > 0 && expectedSize <= 0L) {
                        expectedSize = knownSize
                    }

                    val file = File(path)

                    // Wait for file to be created by TDLib
                    var waitCount = 0
                    // Wait up to 15 seconds (300 * 50ms) for file to appear
                    while (!file.exists() && waitCount < 300) {
                         delay(50)
                         waitCount++
                         if (waitCount % 20 == 0) {
                             LogUtils.d("StreamProxy", "Waiting for file creation: $path ($waitCount/300)")
                         }
                    }
                    
                    if (!file.exists()) {
                         LogUtils.e("StreamProxy", null, "File not created by TDLib after 15s timeout: $path")
                         call.respond(HttpStatusCode.InternalServerError, "File not created by TDLib")
                         return@get
                    }

                    if (!file.isFile) {
                        call.respond(HttpStatusCode.NotFound, "Invalid file")
                        return@get
                    }

                    if (expectedSize <= 0L) {
                        expectedSize = file.length()
                    }
                    if (expectedSize > CloudStreamSecurity.MAX_STREAM_CONTENT_LENGTH_BYTES) {
                        call.respond(HttpStatusCode(413, "Payload Too Large"), "File too large for proxy streaming")
                        return@get
                    }

                    // Range Handling
                    val rangeValidation = CloudStreamSecurity.validateRangeHeader(call.request.headers["Range"])
                    if (!rangeValidation.isValid) {
                        call.respond(HttpStatusCode(416, "Range Not Satisfiable"), "Invalid range header")
                        return@get
                    }

                    val isRangeRequest = rangeValidation.normalizedHeader != null
                    var start = 0L
                    var end = if (expectedSize > 0) expectedSize - 1 else Long.MAX_VALUE - 1

                    if (isRangeRequest) {
                        if (rangeValidation.isSuffixRange) {
                            if (expectedSize <= 0L) {
                                call.respond(HttpStatusCode(416, "Range Not Satisfiable"), "Suffix range requires known size")
                                return@get
                            }
                            val suffixLength = rangeValidation.endInclusive ?: 0L
                            if (suffixLength <= 0L) {
                                call.respond(HttpStatusCode(416, "Range Not Satisfiable"), "Invalid suffix range")
                                return@get
                            }
                            start = (expectedSize - suffixLength).coerceAtLeast(0L)
                            end = expectedSize - 1
                        } else {
                            start = rangeValidation.startInclusive ?: 0L
                            end = rangeValidation.endInclusive ?: if (expectedSize > 0) expectedSize - 1 else Long.MAX_VALUE - 1
                        }
                    }

                    // Cap end at expectedSize if known
                    if (expectedSize > 0 && end >= expectedSize) {
                        end = expectedSize - 1
                    }

                    if (expectedSize > 0 && start >= expectedSize) {
                        call.respond(HttpStatusCode(416, "Range Not Satisfiable"))
                        return@get
                    }

                    if (start > end || start < 0 || end < 0) {
                         call.respond(HttpStatusCode(416, "Range Not Satisfiable"))
                         return@get
                    }

                    val contentLength = end - start + 1

                    call.response.header("Accept-Ranges", "bytes")
                    if (isRangeRequest) {
                        if (expectedSize > 0) {
                            call.response.header("Content-Range", "bytes $start-$end/$expectedSize")
                        }
                        call.response.header("Content-Length", contentLength.toString())
                        call.response.status(HttpStatusCode.PartialContent)
                    } else if (expectedSize > 0) {
                        call.response.header("Content-Length", expectedSize.toString())
                        call.response.status(HttpStatusCode.OK)
                    }

                    // Stream the file
                    call.respondBytesWriter(contentType = ContentType.Audio.Any) {
                        val raf = RandomAccessFile(file, "r")
                        try {
                            var currentPos = start
                            val buffer = ByteArray(64 * 1024) // Increased to 64KB for smoother streaming
                            var noDataCount = 0
                            // Exponential backoff while the reader is waiting for TDLib to
                            // deliver more bytes. The previous fixed 50ms delay combined with
                            // a per-iteration getFile() call kept the IO thread and TDLib
                            // database churning during any stall, which showed up as sustained
                            // CPU heat on weaker devices during cloud playback.
                            var stallDelayMs = 50L
                            val maxStallDelayMs = 400L

                            raf.seek(currentPos)

                            var cachedDownloadedPrefixSize = fileInfo.local.downloadedPrefixSize

                            while (true) {
                                // 1. Check if we've reached the end of the requested range
                                val remaining = end - currentPos + 1
                                if (remaining <= 0) break

                                // 2. Check strict limit based on valid downloaded bytes
                                if (currentPos >= cachedDownloadedPrefixSize) {
                                    // We reached the limit of what we know is downloaded. Refresh info.
                                    val updatedInfo = telegramRepository.getFile(fileId)
                                    cachedDownloadedPrefixSize = updatedInfo?.local?.downloadedPrefixSize ?: 0L

                                    // If still no new data, wait or check completion
                                    if (currentPos >= cachedDownloadedPrefixSize) {
                                        if (updatedInfo?.local?.isDownloadingCompleted == true) {
                                            // Download completed. If we are at/past expectation, we are done.
                                            // If size is different than expected, we still stop because we can't get more.
                                            break
                                        }

                                        // Verify cancellation/failure
                                        if (updatedInfo?.local?.isDownloadingCompleted == false && !updatedInfo.local.canBeDownloaded) {
                                             break // Failed/Cancelled
                                        }

                                        delay(stallDelayMs)
                                        stallDelayMs = (stallDelayMs * 2).coerceAtMost(maxStallDelayMs)
                                        continue
                                    } else {
                                        // New data arrived — reset the backoff so we stay
                                        // responsive once the download catches up.
                                        stallDelayMs = 50L
                                    }
                                }

                                // 3. Determine safe read amount
                                // Read min of: buffer size, remaining in range, remaining valid bytes
                                val remainingValid = cachedDownloadedPrefixSize - currentPos
                                val toRead = min(buffer.size.toLong(), min(remaining, remainingValid)).toInt()

                                val read = raf.read(buffer, 0, toRead)
                                if (read > 0) {
                                    writeFully(buffer, 0, read)
                                    currentPos += read
                                    noDataCount = 0
                                } else {
                                    // Should not happen if logic matches, but safety check
                                    delay(10)
                                }
                            }
                        } catch (e: Exception) {
                            // Check for common specific errors to avoid noise
                            val msg = e.toString()
                            if (msg.contains("ChannelWriteException") || 
                                msg.contains("ClosedChannelException") || 
                                msg.contains("Broken pipe") ||
                                msg.contains("WriteTimeoutException") ||
                                msg.contains("JobCancellationException")) {
                                 // Client disconnected, normal behavior
                            } else {
                                 LogUtils.e("StreamProxy", e, "Streaming error")
                            }
                        } finally {
                            raf.close()
                        }
                    }
                }
            }
        }
    }

    private fun connector(builder: EngineConnectorBuilder.() -> Unit) {}

    private var actualPort: Int = 0

    fun startIfNeeded() {
        if (isReady() || startJob?.isActive == true) return
        start()
    }

    fun start() {
        startJob?.cancel()
        startJob = proxyScope.launch {
            try {
                // Pre-resolve a free port since CIO doesn't support port 0 with resolvedConnectors()
                val freePort = ServerSocket(0).use { it.localPort }
                val createdServer = createServer(freePort)
                createdServer.start(wait = false)
                server = createdServer
                actualPort = freePort
                LogUtils.d("StreamProxy", "Started on port $actualPort")
            } catch (e: CancellationException) {
                LogUtils.d("StreamProxy", "Start cancelled")
            } catch (e: Exception) {
                LogUtils.e("StreamProxy", e, "Failed to start server")
            }
        }
    }

    fun stop() {
        startJob?.cancel()
        startJob = null
        proxyScope.coroutineContext.cancelChildren()
        server?.stop(1000, 2000)
        server = null
        actualPort = 0
        LogUtils.d("StreamProxy", "Stopped")
    }
    
    fun getProxyUrl(fileId: Int, knownSize: Long = 0): String {
        if (actualPort == 0) {
            LogUtils.w("StreamProxy", "getProxyUrl called but actualPort is 0")
            return ""
        }
        if (!CloudStreamSecurity.validateTelegramFileId(fileId)) {
            LogUtils.w("StreamProxy", "getProxyUrl rejected invalid fileId: $fileId")
            return ""
        }
        val safeKnownSize = knownSize.takeIf { it in 0..CloudStreamSecurity.MAX_STREAM_CONTENT_LENGTH_BYTES } ?: 0L
        val url = "http://127.0.0.1:$actualPort/stream/$fileId?size=$safeKnownSize"
        LogUtils.d("StreamProxy", "Generated Proxy URL: $url")
        return url
    }
    
    /**
     * Quick check if the proxy server is ready (port is bound).
     */
    fun isReady(): Boolean = actualPort > 0
    
    /**
     * Suspends until the proxy server is ready (port bound).
     * @param timeoutMs Maximum time to wait
     * @return true if ready, false if timed out
     */
    suspend fun awaitReady(timeoutMs: Long = 10_000L): Boolean {
        if (isReady()) return true
        
        val stepMs = 50L
        var elapsed = 0L
        while (elapsed < timeoutMs) {
            if (isReady()) {
                LogUtils.d("StreamProxy", "awaitReady: Server ready after ${elapsed}ms")
                return true
            }
            delay(stepMs)
            elapsed += stepMs
        }
        LogUtils.e("StreamProxy", null, "awaitReady: Timeout after ${timeoutMs}ms")
        return false
    }

    suspend fun ensureReady(timeoutMs: Long = 10_000L): Boolean {
        startIfNeeded()
        return awaitReady(timeoutMs)
    }
    
}
