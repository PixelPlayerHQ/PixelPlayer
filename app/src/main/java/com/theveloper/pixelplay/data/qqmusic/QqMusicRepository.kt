package com.theveloper.pixelplay.data.qqmusic

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import com.theveloper.pixelplay.data.database.AlbumEntity
import com.theveloper.pixelplay.data.database.ArtistEntity
import com.theveloper.pixelplay.data.database.MusicDao
import com.theveloper.pixelplay.data.database.QqMusicDao
import com.theveloper.pixelplay.data.database.QqMusicPlaylistEntity
import com.theveloper.pixelplay.data.database.QqMusicSongEntity
import com.theveloper.pixelplay.data.database.SongArtistCrossRef
import com.theveloper.pixelplay.data.database.SongEntity
import com.theveloper.pixelplay.data.database.toSong
import com.theveloper.pixelplay.data.network.qqmusic.QqMusicApiService
import com.theveloper.pixelplay.data.preferences.PlaylistPreferencesRepository
import com.theveloper.pixelplay.data.stream.BulkSyncResult
import com.theveloper.pixelplay.data.stream.CloudMusicUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.absoluteValue

@Singleton
class QqMusicRepository @Inject constructor(
    private val api: QqMusicApiService,
    private val dao: QqMusicDao,
    private val musicDao: MusicDao,
    private val playlistPreferencesRepository: PlaylistPreferencesRepository,
    @ApplicationContext private val context: Context
) {

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

    data class BulkSyncResult(
        val playlistCount: Int,
        val syncedSongCount: Int,
        val failedPlaylistCount: Int
    )

    private val prefs: SharedPreferences =
        context.getSharedPreferences("qqmusic_prefs", Context.MODE_PRIVATE)

    private val _isLoggedInFlow = MutableStateFlow(false)
    val isLoggedInFlow: StateFlow<Boolean> = _isLoggedInFlow.asStateFlow()
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

    val isLoggedIn: Boolean
        get() = api.hasLogin()

    val userNickname: String?
        get() = prefs.getString("qqmusic_nickname", null)

    val userAvatarUrl: String?
        get() = prefs.getString("qqmusic_avatar_url", null)

    fun getPlaylists(): Flow<List<QqMusicPlaylistEntity>> = dao.getAllPlaylists()

    fun initFromSavedCookies() {
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

    suspend fun loginWithCookies(cookieJson: String): Result<String> {
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

    suspend fun logout() {
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
                    telegramFileId = null
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
