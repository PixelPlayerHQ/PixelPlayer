package com.theveloper.pixelplay.data.s3

import android.content.Context
import android.content.SharedPreferences
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.theveloper.pixelplay.data.database.AlbumEntity
import com.theveloper.pixelplay.data.database.ArtistEntity
import com.theveloper.pixelplay.data.database.MusicDao
import com.theveloper.pixelplay.data.database.S3Dao
import com.theveloper.pixelplay.data.database.S3FolderEntity
import com.theveloper.pixelplay.data.database.S3SongEntity
import com.theveloper.pixelplay.data.database.SongArtistCrossRef
import com.theveloper.pixelplay.data.database.SongEntity
import com.theveloper.pixelplay.data.database.SourceType
import com.theveloper.pixelplay.data.stream.CloudMusicUtils
import com.theveloper.pixelplay.data.worker.S3MetadataWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import timber.log.Timber
import kotlin.math.absoluteValue

/** Data for one configured S3-compatible endpoint. */
data class S3Connection(
    val endpoint: String,
    val region: String,
    val bucket: String,
    val accessKey: String,
    val secretKey: String,
    val prefix: String = ""
) {
    val endpointHost: String get() = endpoint.removePrefix("https://").removePrefix("http://").trimEnd('/')
}

@Singleton
class S3Repository @Inject constructor(
    private val api: S3ApiService,
    private val dao: S3Dao,
    private val musicDao: MusicDao,
    @ApplicationContext private val context: Context
) {
    data class BulkSyncResult(
        val folderCount: Int,
        val syncedSongCount: Int,
        val failedFolderCount: Int
    )

    private val prefs: SharedPreferences = createPrefs(context)
    private val syncMutex = Mutex()

    private val _isLoggedInFlow = MutableStateFlow(false)
    val isLoggedInFlow: StateFlow<Boolean> = _isLoggedInFlow.asStateFlow()

    init {
        _isLoggedInFlow.value = getConnection() != null
    }

    private fun createPrefs(context: Context): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            context,
            "s3_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun getConnection(): S3Connection? {
        val endpoint = prefs.getString(KEY_ENDPOINT, null)?.takeIf { it.isNotBlank() } ?: return null
        return S3Connection(
            endpoint = endpoint.trimEnd('/'),
            region = prefs.getString(KEY_REGION, null).orEmpty().ifBlank { "us-east-1" },
            bucket = prefs.getString(KEY_BUCKET, null).orEmpty(),
            accessKey = prefs.getString(KEY_ACCESS_KEY, null).orEmpty(),
            secretKey = prefs.getString(KEY_SECRET_KEY, null).orEmpty(),
            prefix = prefs.getString(KEY_PREFIX, null).orEmpty()
        )
    }

    suspend fun connect(
        endpoint: String,
        region: String,
        bucket: String,
        accessKey: String,
        secretKey: String,
        prefix: String = ""
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val normalizedEndpoint = endpoint.trim().trimEnd('/')
            require(normalizedEndpoint.startsWith("https://")) {
                "Endpoint must use https:// (http is only reachable on plain-text-allowed networks)"
            }
            require(bucket.isNotBlank()) { "Bucket is required" }
            require(accessKey.isNotBlank() && secretKey.isNotBlank()) { "Access keys are required" }

            val connection = S3Connection(
                endpoint = normalizedEndpoint,
                region = region.trim().ifBlank { "us-east-1" },
                bucket = bucket.trim(),
                accessKey = accessKey.trim(),
                secretKey = secretKey.trim(),
                prefix = prefix.trim().trim('/').let { if (it.isEmpty()) "" else "$it/" }
            )
            // Fail fast on bad credentials / wrong bucket before persisting anything.
            api.ping(
                endpoint = connection.endpoint,
                bucket = connection.bucket,
                accessKey = connection.accessKey,
                secretKey = connection.secretKey,
                region = connection.region
            )
            prefs.edit()
                .putString(KEY_ENDPOINT, connection.endpoint)
                .putString(KEY_REGION, connection.region)
                .putString(KEY_BUCKET, connection.bucket)
                .putString(KEY_ACCESS_KEY, connection.accessKey)
                .putString(KEY_SECRET_KEY, connection.secretKey)
                .putString(KEY_PREFIX, connection.prefix)
                .apply()
            _isLoggedInFlow.value = true
        }.onFailure { Timber.e(it, "S3 connect failed") }
    }

    suspend fun logout() = withContext(Dispatchers.IO) {
        prefs.edit().clear().apply()
        _isLoggedInFlow.value = false
        dao.clearAllSongs()
        dao.clearAllFolders()
        musicDao.clearAllS3Songs()
    }

    fun getFolders(): Flow<List<S3FolderEntity>> = dao.getAllFolders()

    fun userBucketLabel(): String? = getConnection()?.let { "${it.bucket}/${it.prefix.trimEnd('/')}".trimEnd('/') }

    /** Lists immediate sub-folders of [prefix] (plus the objects at that level). */
    suspend fun browseFolders(prefix: String): Result<List<S3FolderEntry>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val conn = requireNotNull(getConnection()) { "Not connected" }
                val page = api.listObjects(
                    endpoint = conn.endpoint, bucket = conn.bucket,
                    accessKey = conn.accessKey, secretKey = conn.secretKey, region = conn.region,
                    prefix = prefix, delimiter = "/"
                )
                page.folders
            }.onFailure { Timber.e(it, "S3 browse failed") }
        }

    /** Picks a folder (by prefix) and syncs every audio object under it. */
    suspend fun addFolder(prefix: String): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val conn = requireNotNull(getConnection()) { "Not connected" }
            val normalized = prefix.trim('/').let { if (it.isEmpty()) "" else "$it/" }
            syncFolderInternal(conn, normalized)
            countSongsInFolder(normalized)
        }.onFailure { Timber.e(it, "S3 addFolder failed") }
    }

    suspend fun syncFolder(folderId: String): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val conn = requireNotNull(getConnection()) { "Not connected" }
            syncFolderInternal(conn, folderId)
            countSongsInFolder(folderId)
        }.onFailure { Timber.e(it, "S3 syncFolder failed") }
    }

    suspend fun syncAllFoldersAndSongs(): BulkSyncResult = withContext(Dispatchers.IO) {
        val conn = getConnection() ?: return@withContext BulkSyncResult(0, 0, 0)
        syncMutex.withLock {
            val folders = dao.getAllFoldersList()
            var synced = 0
            var failed = 0
            folders.forEach { folder ->
                runCatching {
                    syncFolderInternal(conn, folder.id)
                    synced += 1
                }.onFailure {
                    failed += 1
                    Timber.e(it, "S3 sync failed for folder ${folder.id}")
                }
            }
            BulkSyncResult(folders.size, synced, failed)
        }
    }

    suspend fun removeFolder(folderId: String) = withContext(Dispatchers.IO) {
        dao.deleteSongsByFolder(folderId)
        dao.deleteFolder(folderId)
        resyncUnifiedLibrary()
    }

    suspend fun presignGetUrl(objectKey: String, expiresInSeconds: Long = 3600): String =
        withContext(Dispatchers.IO) {
            val conn = requireNotNull(getConnection()) { "Not connected" }
            api.presignGetUrl(
                endpoint = conn.endpoint, bucket = conn.bucket, objectKey = objectKey,
                accessKey = conn.accessKey, secretKey = conn.secretKey,
                region = conn.region, expiresInSeconds = expiresInSeconds
            )
        }

    // --- Internals ---

    private suspend fun countSongsInFolder(folderId: String): Int {
        val existing = dao.getAllS3SongsList().filter { it.folderId == folderId }
        return existing.size
    }

    /** Lists every audio object under [folderId], replaces the folder's song set, refreshes folder row. */
    private suspend fun syncFolderInternal(conn: S3Connection, folderId: String) {
        val objects = api.listAllObjects(
            endpoint = conn.endpoint, bucket = conn.bucket,
            accessKey = conn.accessKey, secretKey = conn.secretKey, region = conn.region,
            prefix = folderId
        ).filter { it.key.isAudioFile() }

        val now = System.currentTimeMillis()
        // Tags are probed later in the background (S3MetadataWorker) — sync stays fast.
        val entities = objects.mapNotNull { obj -> parseFileToEntity(obj, folderId) }

        val oldSongs = dao.getAllS3SongsList().filter { it.folderId == folderId }
        val newKeys = entities.map { it.objectKey }.toSet()
        val removedIds = oldSongs.filter { it.objectKey !in newKeys }.map { it.id }

        dao.insertSongs(entities)
        scheduleMetadataProbe()
        if (removedIds.isNotEmpty()) dao.deleteSongsByIds(removedIds)
        // Removed objects disappear from the unified library via the ID diff in resyncUnifiedLibrary().

        val folderName = folderId.trimEnd('/').substringAfterLast('/').ifBlank { conn.bucket }
        dao.insertFolder(
            S3FolderEntity(
                id = folderId,
                name = folderName,
                songCount = entities.size,
                lastSyncTime = now
            )
        )
        resyncUnifiedLibrary()
    }

    /** Live state of the background tag-probe queue. */
    data class ProbeQueueState(val isRunning: Boolean, val remaining: Int)

    val probeQueueFlow: Flow<ProbeQueueState> = combine(
        WorkManager.getInstance(context).getWorkInfosForUniqueWorkFlow("s3_metadata_probe"),
        dao.countUnprobedFlow()
    ) { infos, remaining ->
        ProbeQueueState(
            isRunning = infos.any {
                it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED
            },
            remaining = remaining
        )
    }

    /** Enqueues the background tag-probe queue (unique, network-constrained). */
    fun scheduleMetadataProbe() {
        val request = OneTimeWorkRequestBuilder<S3MetadataWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            "s3_metadata_probe",
            ExistingWorkPolicy.KEEP,
            request
        )
    }

    /** Mirrors all s3_songs into the unified `songs` table. */
    suspend fun refreshUnifiedLibrary() = resyncUnifiedLibrary()

    private suspend fun resyncUnifiedLibrary() {
        val s3Songs = dao.getAllS3SongsList()
        val existingUnifiedIds = musicDao.getAllS3SongIds()

        if (s3Songs.isEmpty()) {
            if (existingUnifiedIds.isNotEmpty()) musicDao.clearAllS3Songs()
            return
        }

        val songs = ArrayList<SongEntity>(s3Songs.size)
        val artists = LinkedHashMap<Long, ArtistEntity>()
        val albums = LinkedHashMap<Long, AlbumEntity>()
        val crossRefs = mutableListOf<SongArtistCrossRef>()

        s3Songs.forEach { s3Song ->
            val songId = s3Song.objectKey.toUnifiedSongId()
            val artistNames = CloudMusicUtils.parseArtistNames(s3Song.artist)
            val primaryArtistName = artistNames.firstOrNull() ?: "Unknown Artist"
            val primaryArtistId = primaryArtistName.toUnifiedArtistId()

            artistNames.forEachIndexed { index, artistName ->
                val artistId = artistName.toUnifiedArtistId()
                artists.putIfAbsent(
                    artistId,
                    ArtistEntity(id = artistId, name = artistName, trackCount = 0, imageUrl = null)
                )
                crossRefs.add(SongArtistCrossRef(songId = songId, artistId = artistId, isPrimary = index == 0))
            }

            val albumName = s3Song.album.ifBlank { "Unknown Album" }
            val albumId = albumName.toUnifiedAlbumId()
            albums.putIfAbsent(
                albumId,
                AlbumEntity(
                    id = albumId,
                    title = albumName,
                    artistName = primaryArtistName,
                    artistId = primaryArtistId,
                    songCount = 0,
                    dateAdded = s3Song.dateAdded,
                    year = 0,
                    albumArtUriString = s3Song.albumArtUri
                )
            )

            songs.add(
                SongEntity(
                    id = songId,
                    title = s3Song.title,
                    artistName = s3Song.artist.ifBlank { primaryArtistName },
                    artistId = primaryArtistId,
                    albumArtist = null,
                    albumName = albumName,
                    albumId = albumId,
                    contentUriString = "s3://${s3Song.objectKey}",
                    albumArtUriString = s3Song.albumArtUri,
                    duration = s3Song.duration,
                    genre = S3_GENRE,
                    filePath = "",
                    parentDirectoryPath = S3_PARENT_DIRECTORY,
                    isFavorite = false,
                    lyrics = null,
                    trackNumber = 0,
                    year = 0,
                    dateAdded = s3Song.dateAdded.takeIf { it > 0 } ?: System.currentTimeMillis(),
                    mimeType = s3Song.mimeType,
                    bitrate = s3Song.bitrate,
                    sampleRate = null,
                    telegramChatId = null,
                    telegramFileId = null,
                    sourceType = SourceType.S3
                )
            )
        }

        val albumCounts = songs.groupingBy { it.albumId }.eachCount()
        val finalAlbums = albums.values.map { it.copy(songCount = albumCounts[it.id] ?: 0) }

        val currentIds = songs.map { it.id }.toSet()
        val deletedIds = existingUnifiedIds.filter { it !in currentIds }

        musicDao.incrementalSyncMusicData(
            songs = songs,
            albums = finalAlbums,
            artists = artists.values.toList(),
            crossRefs = crossRefs,
            deletedSongIds = deletedIds
        )
    }

    private class ProbedMeta(
        val title: String?,
        val artist: String?,
        val album: String?,
        val durationMs: Long?,
        val albumArtUri: String?
    )

    /** Probes one batch of un-probed songs; returns true when more work likely remains. */
    suspend fun probeUnprobedBatch(batchSize: Int = 8): Boolean = withContext(Dispatchers.IO) {
        val batch = dao.getUnprobedBatch(batchSize)
        if (batch.isEmpty()) return@withContext false
        val limiter = Semaphore(permits = 4)
        coroutineScope {
            batch.map { entity ->
                async {
                    limiter.withPermit {
                        val meta = probeMetadata(entity.objectKey)
                        dao.applyProbeResult(
                            id = entity.id,
                            title = meta?.title ?: entity.title,
                            artist = meta?.artist ?: entity.artist,
                            album = meta?.album ?: entity.album,
                            duration = meta?.durationMs ?: entity.duration,
                            albumArtUri = meta?.albumArtUri
                        )
                    }
                }
            }.awaitAll()
        }
        true
    }

    /** Reads embedded tags/artwork over a presigned partial fetch. Null on any failure. */
    private suspend fun probeMetadata(objectKey: String): ProbedMeta? = withContext(Dispatchers.IO) {
        runCatching {
            val url = presignGetUrl(objectKey, expiresInSeconds = 600L)
            val mmr = MediaMetadataRetriever()
            try {
                mmr.setDataSource(url, emptyMap())
                fun meta(key: Int) = mmr.extractMetadata(key)?.trim()?.takeIf { it.isNotEmpty() }
                val title = meta(MediaMetadataRetriever.METADATA_KEY_TITLE)
                val artist = meta(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                    ?: meta(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST)
                val album = meta(MediaMetadataRetriever.METADATA_KEY_ALBUM)
                val duration = meta(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    ?.toLongOrNull()?.takeIf { it > 0L }
                val artUri = mmr.embeddedPicture
                    ?.takeIf { it.isNotEmpty() }
                    ?.let { bytes -> saveAlbumArt(album, bytes) }
                ProbedMeta(title, artist, album, duration, artUri)
            } finally {
                mmr.release()
            }
        }.onFailure { Timber.tag("S3Repo").d(it, "Tag probe failed for %s", objectKey) }
            .getOrNull()
    }

    /** One artwork file per album (hash-named) under cacheDir/s3_artwork. */
    private fun saveAlbumArt(album: String?, bytes: ByteArray): String {
        val dir = File(context.cacheDir, "s3_artwork").apply { mkdirs() }
        val name = (album ?: "").hashCode().absoluteValue.toString(16)
        val file = File(dir, "$name.img")
        if (!file.exists() || file.length() == 0L) {
            file.writeBytes(bytes)
        }
        return Uri.fromFile(file).toString()
    }

    private fun parseFileToEntity(obj: S3ObjectEntry, folderId: String): S3SongEntity? {
        val fileName = obj.key.substringAfterLast('/')
        if (fileName.isBlank()) return null
        val ext = fileName.substringAfterLast('.', "").lowercase()
        val nameWithoutExt = fileName.substringBeforeLast('.')

        // "Artist - Title.ext" or just "Title.ext"
        val parts = nameWithoutExt.split(" - ", limit = 2)
        val (artist, title) = if (parts.size == 2) {
            parts[0].trim() to parts[1].trim()
        } else {
            "Unknown Artist" to nameWithoutExt.trim()
        }

        // Album = first path segment under the synced folder, else "S3".
        val relativeDir = obj.key.removePrefix(folderId).substringBeforeLast('/')
        val album = relativeDir.substringBefore('/').ifBlank { "S3" }

        return S3SongEntity(
            id = "${folderId}_${obj.key}",
            objectKey = obj.key,
            folderId = folderId,
            title = title,
            artist = artist,
            album = album,
            albumId = album.toUnifiedAlbumId(),
            duration = 0L, // Not available from object metadata
            mimeType = mimeTypeForExtension(ext),
            bitrate = null,
            fileSize = obj.sizeBytes,
            dateAdded = System.currentTimeMillis(),
            dateModified = System.currentTimeMillis()
        )
    }

    private fun mimeTypeForExtension(ext: String): String = when (ext) {
        "mp3" -> "audio/mpeg"
        "flac" -> "audio/flac"
        "wav" -> "audio/wav"
        "ogg", "oga" -> "audio/ogg"
        "opus" -> "audio/opus"
        "m4a", "m4b" -> "audio/mp4"
        "aac" -> "audio/aac"
        "wma" -> "audio/x-ms-wma"
        "aiff", "aif" -> "audio/aiff"
        "ape" -> "audio/x-ape"
        "dsf" -> "audio/dsf"
        "wv" -> "audio/x-wavpack"
        else -> "audio/mpeg"
    }

    private fun String.isAudioFile(): Boolean {
        val ext = substringAfterLast('.', "").lowercase()
        return ext in AUDIO_EXTENSIONS
    }

    private fun String.toUnifiedSongId(): Long =
        -(S3_SONG_ID_OFFSET + hashCode().toLong().absoluteValue)

    private fun String.toUnifiedAlbumId(): Long =
        -(S3_ALBUM_ID_OFFSET + lowercase().hashCode().toLong().absoluteValue)

    private fun String.toUnifiedArtistId(): Long =
        -(S3_ARTIST_ID_OFFSET + lowercase().hashCode().toLong().absoluteValue)

    companion object {
        const val S3_SONG_ID_OFFSET = 15_000_000_000_000L
        const val S3_ALBUM_ID_OFFSET = 16_000_000_000_000L
        const val S3_ARTIST_ID_OFFSET = 17_000_000_000_000L
        const val S3_PARENT_DIRECTORY = "/Cloud/S3"
        const val S3_GENRE = "S3"

        private val AUDIO_EXTENSIONS = setOf(
            "mp3", "flac", "wav", "ogg", "oga", "opus", "m4a", "m4b",
            "aac", "wma", "aiff", "aif", "ape", "dsf", "wv"
        )

        private const val KEY_ENDPOINT = "s3_endpoint"
        private const val KEY_REGION = "s3_region"
        private const val KEY_BUCKET = "s3_bucket"
        private const val KEY_ACCESS_KEY = "s3_access_key"
        private const val KEY_SECRET_KEY = "s3_secret_key"
        private const val KEY_PREFIX = "s3_prefix"
    }
}
