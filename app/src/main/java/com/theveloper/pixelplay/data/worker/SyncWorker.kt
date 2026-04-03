package com.theveloper.pixelplay.data.worker

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.media.MediaScannerConnection
import android.os.Environment
import android.os.Trace // Import Trace
import android.provider.MediaStore
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.theveloper.pixelplay.data.database.AlbumEntity
import com.theveloper.pixelplay.data.database.ArtistEntity
import com.theveloper.pixelplay.data.database.MusicDao
import com.theveloper.pixelplay.data.database.NeteaseDao
import com.theveloper.pixelplay.data.database.SongArtistCrossRef
import com.theveloper.pixelplay.data.database.SongEntity
import com.theveloper.pixelplay.data.database.SourceType
import com.theveloper.pixelplay.data.database.TelegramDao // Added
import com.theveloper.pixelplay.data.database.resolveAlbumArtUri
import com.theveloper.pixelplay.data.database.serializeArtistRefs
import com.theveloper.pixelplay.data.model.ArtistRef
import com.theveloper.pixelplay.data.navidrome.NavidromeRepository
import com.theveloper.pixelplay.data.media.AudioMetadataReader
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.preferences.UserPreferencesRepository
import com.theveloper.pixelplay.data.repository.LyricsRepository
import com.theveloper.pixelplay.utils.AlbumArtCacheManager
import com.theveloper.pixelplay.utils.AlbumArtUtils
import com.theveloper.pixelplay.utils.AudioMetaUtils.getAudioMetadata
import com.theveloper.pixelplay.utils.DirectoryRuleResolver
import com.theveloper.pixelplay.utils.LocalArtworkUri
import com.theveloper.pixelplay.utils.buildLocalAudioSelection
import com.theveloper.pixelplay.utils.normalizeMetadataTextOrEmpty
import com.theveloper.pixelplay.utils.splitArtistsByDelimiters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.absoluteValue // Added
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import timber.log.Timber

enum class SyncMode {
    INCREMENTAL,
    FULL,
    REBUILD
}

@HiltWorker
class SyncWorker
@AssistedInject
constructor(
        @Assisted appContext: Context,
        @Assisted workerParams: WorkerParameters,
        private val musicDao: MusicDao,
        private val userPreferencesRepository: UserPreferencesRepository,
        private val lyricsRepository: LyricsRepository,
        private val telegramDao: TelegramDao,
        private val neteaseDao: NeteaseDao,
        private val navidromeRepository: NavidromeRepository
) : CoroutineWorker(appContext, workerParams) {

    private val contentResolver: ContentResolver = appContext.contentResolver
    private var minSongDurationMs: Int = 10000

    override suspend fun doWork(): Result =
            withContext(Dispatchers.IO) {
                Trace.beginSection("SyncWorker.doWork")
                try {
                    val syncModeName =
                            inputData.getString(INPUT_SYNC_MODE) ?: SyncMode.INCREMENTAL.name
                    val syncMode = SyncMode.valueOf(syncModeName)
                    val forceMetadata = inputData.getBoolean(INPUT_FORCE_METADATA, false)

                    Timber.tag(TAG)
                        .i("Starting MediaStore synchronization (Mode: $syncMode, ForceMetadata: $forceMetadata)...")
                    val startTime = System.currentTimeMillis()

                    val artistDelimiters = userPreferencesRepository.artistDelimitersFlow.first()
                    val artistWordDelimiters = userPreferencesRepository.artistWordDelimitersFlow.first()
                    val extractArtistsFromTitle = userPreferencesRepository.extractArtistsFromTitleFlow.first()
                    val groupByAlbumArtist =
                            userPreferencesRepository.groupByAlbumArtistFlow.first()
                    val rescanRequired =
                            userPreferencesRepository.artistSettingsRescanRequiredFlow.first()
                    val directoryRulesVersion =
                        userPreferencesRepository.getDirectoryRulesVersion()
                    val lastAppliedDirectoryRulesVersion =
                        userPreferencesRepository.getLastAppliedDirectoryRulesVersion()
                    val directoryRulesChanged =
                        directoryRulesVersion != lastAppliedDirectoryRulesVersion

                    // Feature: Directory Filtering
                    val allowedDirs = userPreferencesRepository.allowedDirectoriesFlow.first()
                    val blockedDirs = userPreferencesRepository.blockedDirectoriesFlow.first()
                    val directoryResolver = DirectoryRuleResolver(allowedDirs, blockedDirs)
                    
                    var lastSyncTimestamp = userPreferencesRepository.getLastSyncTimestamp()

                    // Smart Duration Filtering
                    minSongDurationMs = userPreferencesRepository.getMinSongDuration()

                    Timber.tag(TAG)
                        .d(
                            "Artist delimiters=$artistDelimiters, groupByAlbumArtist=$groupByAlbumArtist, " +
                                "rescanRequired=$rescanRequired, directoryRulesChanged=$directoryRulesChanged " +
                                "(current=$directoryRulesVersion, applied=$lastAppliedDirectoryRulesVersion)"
                        )

                    // --- MEDIA SCAN PHASE ---
                    // OPT #8: Filesystem walk cooldown.
                    // triggerMediaScanForNewFiles() calls File.walkTopDown() on the external storage
                    // root, which can touch thousands of inodes and cause noticeable I/O.
                    // For INCREMENTAL syncs we skip it if we already ran within the last hour
                    // (the MediaStore daemon itself picks up new files quickly via inotify).
                    // FULL and REBUILD always run it unconditionally.
                    val mediaScanCooldownMs = 60L * 60L * 1000L // 1 hour
                    val timeSinceLastScan = System.currentTimeMillis() - lastSyncTimestamp
                    val shouldRunMediaScan = syncMode != SyncMode.INCREMENTAL ||
                            lastSyncTimestamp == 0L ||
                            timeSinceLastScan >= mediaScanCooldownMs
                    if (shouldRunMediaScan) {
                        triggerMediaScanForNewFiles(directoryResolver)
                    } else {
                        Timber.tag(TAG).d(
                            "Skipping filesystem walk — last scan was ${timeSinceLastScan / 1000}s ago (cooldown: ${mediaScanCooldownMs / 1000}s)"
                        )
                    }

                    // --- DELETION PHASE ---
                    // Detect and remove deleted songs efficiently using ID comparison
                    // We do this for INCREMENTAL and FULL modes. REBUILD clears everything anyway.
                    if (syncMode != SyncMode.REBUILD) {
                        // Only compare MediaStore-backed songs; cloud sources are excluded.
                        val localSongIds = musicDao.getAllMediaStoreSongIds().toHashSet()
                        val mediaStoreIds = fetchMediaStoreIds(directoryResolver)

                        // Identify IDs that are in local DB but not in MediaStore
                        val deletedIds = localSongIds - mediaStoreIds

                        if (deletedIds.isNotEmpty()) {
                            Timber.tag(TAG)
                                .i("Found ${deletedIds.size} deleted songs. Removing from database...")
                            // Chunk deletions to avoid SQLite variable limit (default 999)
                            val batchSize = 500
                            deletedIds.chunked(batchSize).forEach { chunk ->
                                musicDao.deleteSongsByIds(chunk.toList())
                                musicDao.deleteCrossRefsBySongIds(chunk.toList())
                            }
                        } else {
                            Timber.tag(TAG).d("No deleted songs found.")
                        }
                    }

                    // --- FETCH PHASE ---
                    // Determine what to fetch based on mode
                    val isFreshInstall = musicDao.getSongCount().first() == 0

                    // If REBUILD or FULL or RescanRequired or Fresh Install -> Fetch EVERYTHING
                    // (timestamp = 0)
                    // If INCREMENTAL -> Fetch only changes since lastSyncTimestamp
                    val fetchTimestamp =
                            if (syncMode == SyncMode.INCREMENTAL &&
                                            !rescanRequired &&
                                            !directoryRulesChanged &&
                                            !isFreshInstall
                            ) {
                                lastSyncTimestamp /
                                        1000 // Convert to seconds for MediaStore comparison
                            } else {
                                0L
                            }

                    Timber.tag(TAG)
                        .i("Fetching music from MediaStore (since: $fetchTimestamp seconds)...")

                    // Update every 50 songs or ~5% of library
                    val progressBatchSize = 50

                    val songsToInsert =
                            fetchMusicFromMediaStore(
                                    fetchTimestamp,
                                    forceMetadata,
                                    directoryResolver,
                                    syncMode == SyncMode.REBUILD,
                                    progressBatchSize
                            ) { current, total, phaseOrdinal ->
                                setProgress(
                                        workDataOf(
                                                PROGRESS_CURRENT to current,
                                                PROGRESS_TOTAL to total,
                                                PROGRESS_PHASE to phaseOrdinal
                                        )
                                )
                            }

                    Timber.tag(TAG)
                        .i("Fetched ${songsToInsert.size} new/modified songs from MediaStore.")

                    // --- PROCESSING PHASE ---
                    val anySongsFetched = songsToInsert.isNotEmpty()
                    if (anySongsFetched) {

                        val allExistingArtists =
                                if (syncMode == SyncMode.REBUILD) {
                                    emptyList()
                                } else {
                                    musicDao.getAllArtistsListRaw()
                                }
                        val allExistingAlbums =
                                if (syncMode == SyncMode.REBUILD) {
                                    emptyList()
                                } else {
                                    musicDao.getAllAlbumsList(emptyList(), false)
                                }

                        val existingArtistMetadata =
                                allExistingArtists.associate { it.id to (it.imageUrl to it.customImageUri) }
                        
                        // Load all existing artist IDs to ensure stability across incremental syncs
                        val existingArtistIdMap = allExistingArtists.associate { it.name to it.id }.toMutableMap()
                        val maxArtistId = musicDao.getMaxArtistId() ?: 0L

                        Timber.tag(TAG)
                            .i("Processing ${songsToInsert.size} songs for upsert. Hash: ${songsToInsert.hashCode()}")

                        val (correctedSongs, albums, artists, crossRefs) =
                                preProcessAndDeduplicateWithMultiArtist(
                                        songs = songsToInsert,
                                        artistDelimiters = artistDelimiters,
                                        wordDelimiters = artistWordDelimiters,
                                        extractFromTitle = extractArtistsFromTitle,
                                        groupByAlbumArtist = groupByAlbumArtist,
                                        existingArtistMetadata = existingArtistMetadata,
                                        existingAlbums = allExistingAlbums,
                                        existingArtistIdMap = existingArtistIdMap,
                                        initialMaxArtistId = maxArtistId
                                )

                        // Use incrementalSyncMusicData for all modes except REBUILD
                        // Even for FULL sync, we can just upsert the values
                        if (syncMode == SyncMode.REBUILD) {
                            // Keep clear + insert in one transaction to avoid partial clears
                            // if this worker gets cancelled/replaced mid-rebuild.
                            musicDao.rebuildMusicDataWithCrossRefs(
                                    correctedSongs,
                                    albums,
                                    artists,
                                    crossRefs
                            )
                        } else {
                            // incrementalSyncMusicData handles upserts efficiently
                            // processing deleted songs was already handled at the start
                            musicDao.incrementalSyncMusicData(
                                    songs = correctedSongs,
                                    albums = albums,
                                    artists = artists,
                                    crossRefs = crossRefs,
                                    deletedSongIds = emptyList() // Already handled
                            )
                        }

                        // Clear the rescan required flag
                        userPreferencesRepository.clearArtistSettingsRescanRequired()

                        val endTime = System.currentTimeMillis()
                        Timber.tag(TAG)
                            .i("Synchronization finished successfully in ${endTime - startTime}ms.")
                        userPreferencesRepository.setLastSyncTimestamp(System.currentTimeMillis())
                        userPreferencesRepository.markDirectoryRulesVersionApplied(
                            directoryRulesVersion
                        )
                    }

                    // Count total songs for the output
                    val totalSongs = musicDao.getSongCount().first()

                    // --- LRC SCANNING PHASE ---
                    val autoScanLrc = userPreferencesRepository.autoScanLrcFilesFlow.first()
                    if (autoScanLrc) {
                        Timber.tag(TAG)
                            .i("Auto-scan LRC files enabled. Starting scan phase in chunks...")

                        // Get ALL media store song IDs to scan in manageable chunks
                        val mediaStoreSongIds = musicDao.getAllMediaStoreSongIds()
                        val totalToScan = mediaStoreSongIds.size
                        var totalScannedCount = 0

                        mediaStoreSongIds.chunked(1000).forEach { idBatch ->
                            val batchEntities = musicDao.getSongsByIdsListSimple(idBatch)
                            val batchSongs =
                                    batchEntities.map { entity ->
                                        Song(
                                                id = entity.id.toString(),
                                                title = entity.title,
                                                artist = entity.artistName,
                                                artistId = entity.artistId,
                                                album = entity.albumName,
                                                albumId = entity.albumId,
                                                path = entity.filePath,
                                                contentUriString = entity.contentUriString,
                                                albumArtUriString = entity.albumArtUriString,
                                                duration = entity.duration,
                                                lyrics = entity.lyrics,
                                                dateAdded = entity.dateAdded,
                                                trackNumber = entity.trackNumber,
                                                year = entity.year,
                                                mimeType = entity.mimeType,
                                                bitrate = entity.bitrate,
                                                sampleRate = entity.sampleRate
                                        )
                                    }

                            val batchScannedCount =
                                    lyricsRepository.scanAndAssignLocalLrcFiles(batchSongs) {
                                            current,
                                            total ->
                                        // Progress within the current batch
                                        val overallCurrent = totalScannedCount + current
                                        setProgress(
                                                workDataOf(
                                                        PROGRESS_CURRENT to overallCurrent,
                                                        PROGRESS_TOTAL to totalToScan,
                                                        PROGRESS_PHASE to
                                                                SyncProgress.SyncPhase.SCANNING_LRC
                                                                        .ordinal
                                                )
                                        )
                                    }
                            totalScannedCount += idBatch.size
                            Log.d(
                                    TAG,
                                    "LRC Scan: Processed batch of ${idBatch.size}, total assigned so far: $batchScannedCount"
                            )
                        }

                        Log.i(TAG, "LRC Scan finished for $totalToScan songs.")
                    }

                    // Clean orphaned album art cache files
                    val allSongIds = musicDao.getAllSongIds().toSet()
                    AlbumArtCacheManager.cleanOrphanedCacheFiles(applicationContext, allSongIds)

                    // Sync cloud songs into the unified songs table.
                    // OPT #7: Guard each source to avoid opening Room transactions when
                    // the user hasn't configured that cloud provider.
                    val hasTelegramChannels = telegramDao.getAllChannels().first().isNotEmpty()
                    if (hasTelegramChannels) {
                        syncTelegramData()
                    } else {
                        Log.d(TAG, "Skipping Telegram sync — no channels configured.")
                    }

                    // syncNeteaseData already has an internal isEmpty guard; a lightweight
                    // count check here avoids even calling into the function.
                    val neteaseCount = neteaseDao.getNeteaseCount()
                    if (neteaseCount > 0) {
                        syncNeteaseData()
                    } else {
                        Log.d(TAG, "Skipping Netease sync — no songs in local cache.")
                    }

                    if (navidromeRepository.isLoggedIn) {
                        syncNavidromeData()
                    } else {
                        Log.d(TAG, "Skipping Navidrome sync — not logged in.")
                    }

                    // Recalculate total
                    val finalTotalSongs = musicDao.getSongCount().first()

                    Result.success(workDataOf(OUTPUT_TOTAL_SONGS to finalTotalSongs.toLong()))
                } catch (e: Exception) {
                    Log.e(TAG, "Error during MediaStore synchronization", e)
                    Result.failure()
                } finally {
                    Trace.endSection() // End SyncWorker.doWork
                }
            }

    /** Data class to hold the result of multi-artist preprocessing. */
    private data class MultiArtistProcessResult(
        val songs: List<SongEntity>,
        val albums: List<AlbumEntity>,
        val artists: List<ArtistEntity>,
        val crossRefs: List<SongArtistCrossRef>
    )

    /**
     * Process songs with multi-artist support. Splits artist names by delimiters and creates proper
     * cross-references.
     */
    private fun preProcessAndDeduplicateWithMultiArtist(
            songs: List<SongEntity>,
            artistDelimiters: List<String>,
            wordDelimiters: List<String> = emptyList(),
            extractFromTitle: Boolean = true,
            groupByAlbumArtist: Boolean,
            existingArtistMetadata: Map<Long, Pair<String?, String?>>,
            existingAlbums: List<AlbumEntity>,
            existingArtistIdMap: MutableMap<String, Long>,
            initialMaxArtistId: Long
    ): MultiArtistProcessResult {
        
        val nextArtistId = AtomicLong(initialMaxArtistId + 1)
        val artistNameToId = existingArtistIdMap // Re-use the map passed in
        
        val allCrossRefs = mutableListOf<SongArtistCrossRef>()
        val artistTrackCounts = mutableMapOf<Long, Int>()
        val albumMap = mutableMapOf<AlbumGroupingKey, Long>()
        val artistSplitCache = mutableMapOf<String, List<String>>()
        val correctedSongs = ArrayList<SongEntity>(songs.size)

        existingAlbums
            .sortedBy { it.id }
            .forEach { album ->
                buildAlbumGroupingKeys(album).forEach { key ->
                    albumMap.putIfAbsent(key, album.id)
                }
            }

        songs.forEach { song ->
            val rawArtistName = song.artistName
            val songArtistNameTrimmed = rawArtistName.trim()

            // Split artist field by character + word delimiters
            val allArtistsForSong =
                    artistSplitCache.getOrPut("$rawArtistName\u0000${song.title}\u0000$extractFromTitle") {
                        collectArtistNames(
                            rawArtistName = rawArtistName,
                            title = song.title,
                            artistDelimiters = artistDelimiters,
                            wordDelimiters = wordDelimiters,
                            extractFromTitle = extractFromTitle
                        )
                    }

            allArtistsForSong.forEach { artistName ->
                val normalizedName = artistName.trim()
                if (normalizedName.isNotEmpty() && !artistNameToId.containsKey(normalizedName)) {
                     val id = nextArtistId.getAndIncrement()
                     artistNameToId[normalizedName] = id
                }
            }

            val primaryArtistName =
                    allArtistsForSong.firstOrNull()?.trim()?.takeIf { it.isNotEmpty() }
                            ?: songArtistNameTrimmed
            val primaryArtistId = artistNameToId[primaryArtistName] ?: song.artistId

            allArtistsForSong.forEachIndexed { index, artistName ->
                val normalizedName = artistName.trim()
                val artistId = artistNameToId[normalizedName]
                if (artistId != null) {
                    val isPrimary = (index == 0) // First artist is primary
                    allCrossRefs.add(
                            SongArtistCrossRef(
                                    songId = song.id,
                                    artistId = artistId,
                                    isPrimary = isPrimary
                            )
                    )
                    artistTrackCounts[artistId] = (artistTrackCounts[artistId] ?: 0) + 1
                }
            }

            // --- Album Logic ---
            val albumKey = buildAlbumGroupingKey(song)
            val finalAlbumId = albumMap.getOrPut(albumKey) { song.albumId }

            // Build serialized artists JSON for efficient loading without JOINs
            val artistRefsForJson = allArtistsForSong.mapIndexed { index, name ->
                val normalizedName = name.trim()
                val artistId = artistNameToId[normalizedName] ?: 0L
                ArtistRef(id = artistId, name = normalizedName, isPrimary = index == 0)
            }.filter { it.name.isNotEmpty() }

            correctedSongs.add(
                    song.copy(
                            artistId = primaryArtistId,
                            artistName = rawArtistName, // Preserving full artist string for display
                            albumId = finalAlbumId,
                            artistsJson = serializeArtistRefs(artistRefsForJson)
                    )
            )
        }

        // Build Entities
        val artistEntities = artistNameToId.map { (name, id) ->
            val count = artistTrackCounts[id] ?: 0
            val metadata = existingArtistMetadata[id]
            ArtistEntity(
                id = id,
                name = name,
                trackCount = count,
                imageUrl = metadata?.first,
                customImageUri = metadata?.second
            )
        }
        
        // Re-calculate Album Entities from the corrected songs to ensure we have valid metadata (Art, Year)
        // which isn't available in the simple albumMap (which only has ID)
        val albumEntities = correctedSongs.groupBy { it.albumId }.map { (catAlbumId, songsInAlbum) ->
             val firstSong = songsInAlbum.first()
             val representativeAlbumArt = songsInAlbum.firstNotNullOfOrNull { it.albumArtUriString }
             val determinedAlbumArtist = chooseAlbumDisplayArtist(
                 songs = songsInAlbum,
                 preferAlbumArtist = groupByAlbumArtist
             )
             val determinedAlbumArtistId = resolveAlbumDisplayArtistId(
                 displayArtist = determinedAlbumArtist,
                 songs = songsInAlbum,
                 artistNameToId = artistNameToId,
                 artistDelimiters = artistDelimiters,
                 wordDelimiters = wordDelimiters
             )

             AlbumEntity(
                 id = catAlbumId,
                 title = firstSong.albumName,
                 artistName = determinedAlbumArtist,
                 artistId = determinedAlbumArtistId,
                 albumArtUriString = representativeAlbumArt,
                 songCount = songsInAlbum.size,
                 dateAdded = firstSong.dateAdded,
                 year = firstSong.year
             )
        }

        return MultiArtistProcessResult(
                songs = correctedSongs, // Corrected songs have the right Album IDs now
                albums = albumEntities,
                artists = artistEntities,
                crossRefs = allCrossRefs
        )
    }

    /**
     * Fetches a map of Song ID -> Genre Name using the MediaStore.Audio.Genres table. This is
     * necessary because the GENRE column in MediaStore.Audio.Media is not reliably available or
     * populated on all Android versions (especially pre-API 30).
     * 
     * Optimized: 
     * 1. Caches results for 1 hour to avoid refetching on incremental syncs
     * 2. Fetches all genres first, then queries members in parallel with controlled concurrency
     */
    private suspend fun fetchGenreMap(forceRefresh: Boolean = false): Map<Long, String> = coroutineScope {
        // Check cache first (valid for 1 hour)
        val now = System.currentTimeMillis()
        val cacheAge = now - genreMapCacheTimestamp
        if (!forceRefresh && genreMapCache.isNotEmpty() && cacheAge < GENRE_CACHE_TTL_MS) {
            Log.d(TAG, "Using cached genre map (${genreMapCache.size} entries, age: ${cacheAge/1000}s)")
            return@coroutineScope genreMapCache
        }
        
        val genreMap = ConcurrentHashMap<Long, String>()
        val genreProjection = arrayOf(MediaStore.Audio.Genres._ID, MediaStore.Audio.Genres.NAME)
        
        // Semaphore to limit concurrent queries (avoid overwhelming ContentResolver)
        val querySemaphore = Semaphore(4)

        try {
            // Step 1: Fetch all genres (single query)
            val genres = mutableListOf<Pair<Long, String>>()
            
            contentResolver.query(
                            MediaStore.Audio.Genres.EXTERNAL_CONTENT_URI,
                            genreProjection,
                            null,
                            null,
                            null
                    )
                    ?.use { cursor ->
                        val idCol = cursor.getColumnIndex(MediaStore.Audio.Genres._ID)
                        val nameCol = cursor.getColumnIndex(MediaStore.Audio.Genres.NAME)

                        if (idCol >= 0 && nameCol >= 0) {
                            while (cursor.moveToNext()) {
                                val genreId = cursor.getLong(idCol)
                                val genreName = cursor.getString(nameCol)

                                if (!genreName.isNullOrBlank() &&
                                                !genreName.equals("unknown", ignoreCase = true)
                                ) {
                                    genres.add(genreId to genreName)
                                }
                            }
                        }
                    }
            
            // Step 2: Fetch members for each genre in parallel (controlled concurrency)
            genres.map { (genreId, genreName) ->
                async(Dispatchers.IO) {
                    querySemaphore.withPermit {
                        val membersUri =
                                MediaStore.Audio.Genres.Members.getContentUri(
                                        "external",
                                        genreId
                                )
                        val membersProjection =
                                arrayOf(MediaStore.Audio.Genres.Members.AUDIO_ID)

                        contentResolver.query(
                                        membersUri,
                                        membersProjection,
                                        null,
                                        null,
                                        null
                                )
                                ?.use { membersCursor ->
                                    val audioIdCol =
                                            membersCursor.getColumnIndex(
                                                    MediaStore.Audio.Genres.Members.AUDIO_ID
                                            )
                                    if (audioIdCol >= 0) {
                                        while (membersCursor.moveToNext()) {
                                            val audioId = membersCursor.getLong(audioIdCol)
                                            // If a song has multiple genres, the last one processed wins.
                                            // This is acceptable as a primary genre for display.
                                            genreMap[audioId] = genreName
                                        }
                                    }
                                }
                    }
                }
            }.awaitAll()
            
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching genre map", e)
        }
        
        // Update cache
        if (genreMap.isNotEmpty()) {
            genreMapCache = genreMap.toMap()
            genreMapCacheTimestamp = System.currentTimeMillis()
            Log.d(TAG, "Genre map cache updated with ${genreMap.size} entries")
        }
        
        genreMap
    }

    /** Raw data extracted from cursor - lightweight class for fast iteration */
    private data class RawSongData(
            val id: Long,
            val albumId: Long,
            val artistId: Long,
            val filePath: String,
            val mimeType: String?,
            val title: String,
            val artist: String,
            val album: String,
            val albumArtist: String?,
            val duration: Long,
            val trackNumber: Int,
            val discNumber: Int?,
            val year: Int,
            val dateModified: Long
    )

    private fun isSongUnchanged(raw: RawSongData, existing: SongEntity?): Boolean {
        if (existing == null) return false

        val parentDir = File(raw.filePath).parent ?: ""
        val existingDateModifiedSeconds = TimeUnit.MILLISECONDS.toSeconds(existing.dateAdded)

        return existing.filePath == raw.filePath &&
            existing.parentDirectoryPath == parentDir &&
            existing.title == raw.title &&
            existing.artistName == raw.artist &&
            existing.albumName == raw.album &&
            existing.albumId == raw.albumId &&
            existing.artistId == raw.artistId &&
            existing.duration == raw.duration &&
            existing.trackNumber == raw.trackNumber &&
            existing.discNumber == raw.discNumber &&
            existing.year == raw.year &&
            existingDateModifiedSeconds == raw.dateModified
    }

    private suspend fun fetchMusicFromMediaStore(
            sinceTimestamp: Long, // Seconds
            forceMetadata: Boolean,
            directoryResolver: DirectoryRuleResolver,
            isRebuild: Boolean,
            progressBatchSize: Int,
            onProgress: suspend (current: Int, total: Int, phaseOrdinal: Int) -> Unit
    ): List<SongEntity> {
        Trace.beginSection("SyncWorker.fetchMusicFromMediaStore")

        val deepScan = forceMetadata
        val genreMap = fetchGenreMap() // Load genres upfront

        val projection =
                arrayOf(
                        MediaStore.Audio.Media._ID,
                        MediaStore.Audio.Media.TITLE,
                        MediaStore.Audio.Media.ARTIST,
                        MediaStore.Audio.Media.ARTIST_ID,
                        MediaStore.Audio.Media.ALBUM,
                        MediaStore.Audio.Media.ALBUM_ID,
                        MediaStore.Audio.Media.ALBUM_ARTIST,
                        MediaStore.Audio.Media.DURATION,
                        MediaStore.Audio.Media.DATA,
                        MediaStore.Audio.Media.MIME_TYPE,
                        MediaStore.Audio.Media.TRACK,
                        MediaStore.Audio.Media.YEAR,
                        MediaStore.Audio.Media.DATE_MODIFIED
                )

        val (baseSelection, baseArgs) = buildLocalAudioSelection(minSongDurationMs)
        val selectionBuilder = StringBuilder(baseSelection)
        val selectionArgsList = baseArgs.toMutableList()

        // Incremental selection
        if (sinceTimestamp > 0) {
            selectionBuilder.append(
                    " AND (${MediaStore.Audio.Media.DATE_MODIFIED} > ? OR ${MediaStore.Audio.Media.DATE_ADDED} > ?)"
            )
            selectionArgsList.add(sinceTimestamp.toString())
            selectionArgsList.add(sinceTimestamp.toString())
        }

        val selection = selectionBuilder.toString()
        val selectionArgs = selectionArgsList.toTypedArray()

        // Phase 1: Fast cursor iteration to collect raw data
        val rawDataList = mutableListOf<RawSongData>()

        contentResolver.query(
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                        projection,
                        selection,
                        selectionArgs,
                        null
                )
                ?.use { cursor ->
                    val totalCount = cursor.count
                    onProgress(0, totalCount, SyncProgress.SyncPhase.FETCHING_MEDIASTORE.ordinal)

                    val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                    val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                    val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                    val artistIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST_ID)
                    val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                    val albumIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                    val albumArtistCol = cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM_ARTIST)
                    val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                    val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
                    val mimeTypeCol = cursor.getColumnIndex(MediaStore.Audio.Media.MIME_TYPE)
                    val trackCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK)
                    val yearCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.YEAR)
                    val dateModifiedCol =
                            cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_MODIFIED)

                    while (cursor.moveToNext()) {
                        try {
                            val data = cursor.getString(dataCol)
                            val parentPath = File(data).parent
                            if (parentPath != null) {
                                val normalizedParent = File(parentPath).absolutePath
                                if (directoryResolver.isBlocked(normalizedParent)) {
                                    continue
                                }
                            }
                        } catch (e: Exception) {
                            // Proceed on error
                        }

                        rawDataList.add(
                                RawSongData(
                                        id = cursor.getLong(idCol),
                                        albumId = cursor.getLong(albumIdCol),
                                        artistId = cursor.getLong(artistIdCol),
                                        filePath = cursor.getString(dataCol) ?: "",
                                        mimeType = if (mimeTypeCol >= 0) cursor.getString(mimeTypeCol) else null,
                                        title =
                                                cursor.getString(titleCol)
                                                        .normalizeMetadataTextOrEmpty()
                                                        .ifEmpty { "Unknown Title" },
                                        artist =
                                                cursor.getString(artistCol)
                                                        .normalizeMetadataTextOrEmpty()
                                                        .ifEmpty { "Unknown Artist" },
                                        album =
                                                cursor.getString(albumCol)
                                                        .normalizeMetadataTextOrEmpty()
                                                        .ifEmpty { "Unknown Album" },
                                        albumArtist =
                                                if (albumArtistCol >= 0)
                                                        cursor.getString(albumArtistCol)
                                                                ?.normalizeMetadataTextOrEmpty()
                                                                ?.takeIf { it.isNotBlank() }
                                                else null,
                                        duration = cursor.getLong(durationCol),
                                        trackNumber = cursor.getInt(trackCol) % 1000,
                                        discNumber = (cursor.getInt(trackCol) / 1000).takeIf { it > 0 },
                                        year = cursor.getInt(yearCol),
                                        dateModified = cursor.getLong(dateModifiedCol)
                                )
                        )
                    }
                }

        if (rawDataList.isEmpty()) {
            Trace.endSection()
            return emptyList()
        }

        // Phase 2: Identify changed songs and merge with existing data in chunks
        val artistDelimiters = userPreferencesRepository.artistDelimitersFlow.first()
        val artistWordDelimiters = userPreferencesRepository.artistWordDelimitersFlow.first()
        val songsToProcess = if (isRebuild) {
             rawDataList
        } else {
            // Find existing data for these songs to avoid unnecessary reprocessing
            // and to preserve user edits.
            val results = mutableListOf<RawSongData>()
            
            rawDataList.chunked(500).forEach { batch ->
                val ids = batch.map { it.id }
                val existingMap = musicDao.getSongsByIdsListSimple(ids).associateBy { it.id }
                
                batch.forEach { raw ->
                    val existing = existingMap[raw.id]
                    if (!isSongUnchanged(raw, existing)) {
                        results.add(raw)
                    }
                }
            }
            results
        }

        // rawDataList is no longer needed — release its memory before the processing phase,
        // which may allocate large existingMap objects and metadata ByteArrays.
        @Suppress("UNUSED_VALUE")
        rawDataList.clear()

        val totalCount = songsToProcess.size
        if (totalCount == 0) {
            Trace.endSection()
            return emptyList()
        }

        // Phase 3: Parallel processing of songs with metadata merging
        onProgress(0, totalCount, SyncProgress.SyncPhase.PROCESSING_FILES.ordinal)
        val processedCount = AtomicInteger(0)
        val concurrencyLimit = 4 // Reduced concurrency to save memory
        val semaphore = Semaphore(concurrencyLimit)

        // Process batches sequentially so each batch's existingMap can be GC'd before the next
        // batch is loaded. The semaphore still limits concurrency within each batch.
        val songs = mutableListOf<SongEntity>()
        for (batch in songsToProcess.chunked(200)) {
            val ids = batch.map { it.id }
            val existingMap = if (isRebuild) emptyMap() else musicDao.getSongsByIdsListSimple(ids).associateBy { it.id }
            val batchResults = coroutineScope {
                batch.map { raw ->
                    async {
                        semaphore.withPermit {
                            val localSong = existingMap[raw.id]
                            val mediaStoreSong =
                                processSongData(
                                    raw = raw,
                                    genreMap = genreMap,
                                    deepScan = deepScan,
                                    forceAlbumArtRefresh = deepScan || localSong != null
                                )

                            val song = if (localSong != null) {
                                // Preserve user-edited fields
                                mediaStoreSong.copy(
                                    dateAdded = localSong.dateAdded,
                                    lyrics = localSong.lyrics,
                                    title = if (localSong.title.isNotBlank() && localSong.title != mediaStoreSong.title) localSong.title else mediaStoreSong.title,
                                    artistName = choosePreferredArtistName(
                                        localArtistName = localSong.artistName,
                                        mediaStoreArtistName = mediaStoreSong.artistName,
                                        artistDelimiters = artistDelimiters,
                                        wordDelimiters = artistWordDelimiters
                                    ),
                                    albumName = if (localSong.albumName.isNotBlank() && localSong.albumName != mediaStoreSong.albumName) localSong.albumName else mediaStoreSong.albumName,
                                    genre = localSong.genre ?: mediaStoreSong.genre,
                                    trackNumber = if (localSong.trackNumber != 0) localSong.trackNumber else mediaStoreSong.trackNumber,
                                    discNumber = localSong.discNumber ?: mediaStoreSong.discNumber,
                                    albumArtUriString = mediaStoreSong.albumArtUriString
                                )
                            } else {
                                mediaStoreSong
                            }

                            val count = processedCount.incrementAndGet()
                            if (count % progressBatchSize == 0 || count == totalCount) {
                                onProgress(count, totalCount, SyncProgress.SyncPhase.PROCESSING_FILES.ordinal)
                            }
                            song
                        }
                    }
                }.awaitAll()
            }
            songs.addAll(batchResults)
        }

        Trace.endSection()
        return songs
    }

    /**
     * Checks if a metadata field from MediaStore is a default/unknown placeholder.
     * MediaStore uses `<unknown>` for unreadable fields, and our normalization
     * may fall back to `"Unknown Artist"` / `"Unknown Album"` etc.
     */
    private fun isDefaultMetadata(value: String): Boolean {
        val lower = value.trim().lowercase()
        return lower.isEmpty() ||
            lower == "<unknown>" ||
            lower == "unknown" ||
            lower == "unknown artist" ||
            lower == "unknown album"
    }

    /**
     * Process a single song's raw data into a SongEntity. This is the CPU/IO intensive work that
     * benefits from parallelization.
     */
    private suspend fun processSongData(
            raw: RawSongData,
            genreMap: Map<Long, String>,
            deepScan: Boolean,
            forceAlbumArtRefresh: Boolean
    ): SongEntity {
        val parentDir = java.io.File(raw.filePath).parent ?: ""
        val contentUriString =
                ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, raw.id)
                        .toString()

        var albumArtUriString =
                AlbumArtUtils.getAlbumArtUri(
                        applicationContext,
                        raw.filePath,
                        raw.id,
                        forceAlbumArtRefresh
                )
        val audioMetadata =
                if (deepScan) getAudioMetadata(musicDao, raw.id, raw.filePath, true) else null

        var title = raw.title
        var artist = raw.artist
        var album = raw.album
        var albumArtist = resolveAlbumArtist(
            rawAlbumArtist = raw.albumArtist,
            metadataAlbumArtist = null
        )
        var trackNumber = raw.trackNumber
        var discNumber = raw.discNumber
        var year = raw.year
        var genre: String? = genreMap[raw.id] // Use mapped genre as default

        val shouldAugmentMetadata =
                deepScan ||
                        raw.filePath.endsWith(".wav", true) ||
                        raw.filePath.endsWith(".opus", true) ||
                        raw.filePath.endsWith(".ogg", true) ||
                        raw.filePath.endsWith(".oga", true) ||
                        raw.filePath.endsWith(".aiff", true) ||
                        // Fallback: if MediaStore returned default/missing metadata,
                        // try TagLib+JAudioTagger to read actual tags from the file.
                        // MediaStore uses "<unknown>" for unreadable fields;
                        // our normalization may produce "Unknown Artist"/"Unknown Album".
                        isDefaultMetadata(raw.artist) ||
                        isDefaultMetadata(raw.album)

        if (shouldAugmentMetadata) {
            val file = java.io.File(raw.filePath)
            if (file.exists()) {
                try {
                    AudioMetadataReader.read(file)?.let { meta ->
                        if (!meta.title.isNullOrBlank()) title = meta.title
                        if (!meta.artist.isNullOrBlank()) artist = meta.artist
                        if (!meta.album.isNullOrBlank()) album = meta.album
                        albumArtist = resolveAlbumArtist(
                            rawAlbumArtist = albumArtist,
                            metadataAlbumArtist = meta.albumArtist
                        )
                        if (!meta.genre.isNullOrBlank()) genre = meta.genre
                        if (meta.trackNumber != null) trackNumber = meta.trackNumber
                        if (meta.discNumber != null) discNumber = meta.discNumber
                        if (meta.year != null) year = meta.year

                        meta.artwork?.let { art ->
                            albumArtUriString = LocalArtworkUri.buildSongUri(raw.id)
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to read metadata via TagLib for ${raw.filePath}", e)
                }
            }
        }

        return SongEntity(
                id = raw.id,
                title = title,
                artistName = artist,
                artistId = raw.artistId,
                albumArtist = albumArtist,
                albumName = album,
                albumId = raw.albumId,
                contentUriString = contentUriString,
                albumArtUriString = albumArtUriString,
                duration = raw.duration,
                genre = genre,
                filePath = raw.filePath,
                parentDirectoryPath = parentDir,
                trackNumber = trackNumber,
                discNumber = discNumber,
                year = year,
                dateAdded =
                        raw.dateModified.let { seconds ->
                            if (seconds > 0) TimeUnit.SECONDS.toMillis(seconds)
                            else System.currentTimeMillis()
                        },
                mimeType = audioMetadata?.mimeType ?: raw.mimeType,
                sampleRate = audioMetadata?.sampleRate,
                bitrate = audioMetadata?.bitrate,
                sourceType = SourceType.LOCAL
        )
    }

    /**
     * Triggers a media scan ONLY for new files that are not yet in MediaStore.
     * This is a fast, incremental scan optimized for pull-to-refresh.
     * It compares filesystem files with MediaStore entries and only scans the difference.
     */
    private suspend fun triggerMediaScanForNewFiles(directoryResolver: DirectoryRuleResolver) {
        withContext(Dispatchers.IO) {
            val externalRoot = Environment.getExternalStorageDirectory()
            val allowedSet =
                userPreferencesRepository.allowedDirectoriesFlow.first().map { File(it) }.toSet()
            Log.i(TAG, "Starting media scan for new files. Explicit includes: ${allowedSet.size}")

            // Get all file paths currently in MediaStore
            val mediaStorePaths = fetchMediaStoreFilePaths()
            Log.d(TAG, "MediaStore has ${mediaStorePaths.size} known files")

            val scanRoots =
                collectPreferredScanRoots(
                    externalRoot = externalRoot,
                    mediaStorePaths = mediaStorePaths,
                    explicitAllowedRoots = allowedSet,
                    directoryResolver = directoryResolver
                )

            if (scanRoots.isEmpty()) {
                Log.d(TAG, "No eligible roots found for media scan")
                return@withContext
            }

            // Collect audio files from filesystem that are NOT in MediaStore
            val audioExtensions =
                    setOf("mp3", "flac", "m4a", "wav", "ogg", "opus", "aac", "wma", "aiff")
            val newFilesToScan = linkedSetOf<String>()

            scanRoots.forEach { root ->
                root.walkTopDown()
                    .onEnter { dir ->
                        val name = dir.name
                        if (dir.isHidden || name.startsWith(".")) return@onEnter false
                        val path = dir.absolutePath
                        if (directoryResolver.isBlocked(path)) return@onEnter false

                        // Default Skip Rules (System Folders)
                        val isSystemFolder = (name == "Android" || name == "data" || name == "obb")
                        if (isSystemFolder) {
                            // Check if this specific folder is Explicitly Allowed or is a Parent of an allowed folder
                            // e.g. if Allowed is "Android/media", we MUST enter "Android".
                            // e.g. if Allowed is "Android" (root), we MUST enter "Android".
                            val isAllowed = allowedSet.any { allowed -> 
                                allowed.absolutePath == path || allowed.absolutePath.startsWith("$path/")
                            }
                            
                            if (!isAllowed) {
                                // Apply strict skipping for Android/data and Android/obb if not allowed
                                val parent = dir.parentFile
                                if (name == "Android" && parent?.absolutePath == Environment.getExternalStorageDirectory().absolutePath) return@onEnter false
                                if (parent?.name == "Android" && (name == "data" || name == "obb")) return@onEnter false
                            }
                        }
                        true
                    }
                    .filter { it.isFile && it.extension.lowercase() in audioExtensions }
                    .filter { it.absolutePath !in mediaStorePaths } // Only new files
                    .forEach { newFilesToScan.add(it.absolutePath) }
            }

            if (newFilesToScan.isEmpty()) {
                Log.d(TAG, "No new audio files found - MediaStore is up to date")
                return@withContext
            }

            Log.i(TAG, "Found ${newFilesToScan.size} NEW audio files to scan")

            // Scan only the new files
            val latch = CountDownLatch(1)
            var scannedCount = 0

            MediaScannerConnection.scanFile(
                applicationContext, 
                newFilesToScan.toTypedArray(),
                null
            ) { _, _ ->
                scannedCount++
                if (scannedCount >= newFilesToScan.size) {
                    latch.countDown()
                }
            }

            // Wait for scan to complete (max 15 seconds)
            val completed = latch.await(15, TimeUnit.SECONDS)
            if (!completed) {
                Log.w(TAG, "Media scan timeout after scanning $scannedCount/${newFilesToScan.size} files")
            } else {
                Log.i(TAG, "Media scan completed for ${newFilesToScan.size} new files")
            }
        }
    }

    private fun collectPreferredScanRoots(
        externalRoot: File,
        mediaStorePaths: Set<String>,
        explicitAllowedRoots: Set<File>,
        directoryResolver: DirectoryRuleResolver
    ): List<File> {
        val candidates = linkedSetOf<File>()

        // 1) Explicitly allowed roots always get priority.
        explicitAllowedRoots.forEach { candidates.add(it) }

        // 2) Common user-facing media directories.
        listOf(
            Environment.DIRECTORY_MUSIC,
            Environment.DIRECTORY_DOWNLOADS,
            Environment.DIRECTORY_PODCASTS,
            Environment.DIRECTORY_AUDIOBOOKS,
            Environment.DIRECTORY_RINGTONES,
            Environment.DIRECTORY_NOTIFICATIONS
        ).forEach { dirName ->
            candidates.add(Environment.getExternalStoragePublicDirectory(dirName))
        }

        // 3) Top-level buckets already used by current MediaStore entries.
        mediaStorePaths.forEach { mediaPath ->
            val parent = File(mediaPath).parentFile ?: return@forEach
            val parentPath = parent.absolutePath
            if (parentPath.startsWith("${externalRoot.absolutePath}/")) {
                val relative = parentPath.removePrefix(externalRoot.absolutePath).trimStart('/')
                if (relative.isNotEmpty()) {
                    val topLevel = relative.substringBefore('/')
                    if (topLevel.isNotEmpty()) {
                        candidates.add(File(externalRoot, topLevel))
                    }
                }
            } else {
                candidates.add(parent)
            }
        }

        val existingRoots =
            candidates
                .map { it.absoluteFile }
                .distinctBy { it.absolutePath }
                .filter { it.exists() && it.isDirectory }
                .filterNot { directoryResolver.isBlocked(it.absolutePath) }

        if (existingRoots.isEmpty()) return emptyList()
        return pruneNestedRoots(existingRoots)
    }

    private fun pruneNestedRoots(roots: List<File>): List<File> {
        val normalizedRoots = roots.distinctBy { it.absolutePath.trimEnd('/') }
        val kept = mutableListOf<File>()
        normalizedRoots
            .sortedBy { it.absolutePath.length }
            .forEach { candidate ->
                val candidatePath = candidate.absolutePath.trimEnd('/')
                val coveredByParent = kept.any { root ->
                    val rootPath = root.absolutePath.trimEnd('/')
                    candidatePath == rootPath || candidatePath.startsWith("$rootPath/")
                }
                if (!coveredByParent) {
                    kept.add(candidate)
                }
            }
        return kept
    }

    /**
     * Fetches all IDs currently available in MediaStore to identify deleted songs.
     */
    private fun fetchMediaStoreIds(directoryResolver: DirectoryRuleResolver): Set<Long> {
        val ids = mutableSetOf<Long>()
        val projection = arrayOf(MediaStore.Audio.Media._ID, MediaStore.Audio.Media.DATA)
        val (selection, selectionArgs) = buildLocalAudioSelection(minSongDurationMs)

        contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            null
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
            while (cursor.moveToNext()) {
                val data = cursor.getString(dataCol)
                if (data != null) {
                    val parentPath = File(data).parent
                    if (parentPath != null && directoryResolver.isBlocked(File(parentPath).absolutePath)) {
                        continue
                    }
                }
                ids.add(cursor.getLong(idCol))
            }
        }
        return ids
    }

    /**
     * Fetches all file paths currently known to MediaStore.
     * Used to identify new files that need scanning.
     */
    private fun fetchMediaStoreFilePaths(): Set<String> {
        val paths = HashSet<String>()
        val projection = arrayOf(MediaStore.Audio.Media.DATA)
        val (selection, selectionArgs) = buildLocalAudioSelection(minSongDurationMs)
        
        contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            null
        )?.use { cursor ->
            val dataCol = cursor.getColumnIndex(MediaStore.Audio.Media.DATA)
            if (dataCol >= 0) {
                while (cursor.moveToNext()) {
                    cursor.getString(dataCol)?.let { paths.add(it) }
                }
            }
        }
        return paths
    }

    companion object {
        const val WORK_NAME = "com.theveloper.pixelplay.data.worker.SyncWorker"
        private const val TAG = "SyncWorker"
        const val INPUT_FORCE_METADATA = "input_force_metadata"
        const val INPUT_SYNC_MODE = "input_sync_mode"

        // Progress reporting constants
        const val PROGRESS_CURRENT = "progress_current"
        const val PROGRESS_TOTAL = "progress_total"
        const val PROGRESS_PHASE = "progress_phase"
        const val OUTPUT_TOTAL_SONGS = "output_total_songs"
        private const val NETEASE_SONG_ID_OFFSET = 3_000_000_000_000L
        private const val NETEASE_ALBUM_ID_OFFSET = 4_000_000_000_000L
        private const val NETEASE_ARTIST_ID_OFFSET = 5_000_000_000_000L
        private const val NETEASE_PARENT_DIRECTORY = "/Cloud/Netease"
        private const val NETEASE_GENRE = "Netease Cloud"

        // Genre cache - shared across worker instances to avoid refetching on incremental syncs
        private const val GENRE_CACHE_TTL_MS = 60 * 60 * 1000L // 1 hour
        @Volatile private var genreMapCache: Map<Long, String> = emptyMap()
        @Volatile private var genreMapCacheTimestamp: Long = 0L
        
        fun invalidateGenreCache() {
            genreMapCache = emptyMap()
            genreMapCacheTimestamp = 0L
            Log.d(TAG, "Genre cache invalidated")
        }

        fun startUpSyncWork(deepScan: Boolean = false) =
                OneTimeWorkRequestBuilder<SyncWorker>()
                        .setInputData(
                                workDataOf(
                                        INPUT_FORCE_METADATA to deepScan,
                                        INPUT_SYNC_MODE to SyncMode.INCREMENTAL.name
                                )
                        )
                        .build()

        fun incrementalSyncWork() =
                OneTimeWorkRequestBuilder<SyncWorker>()
                        .setInputData(workDataOf(INPUT_SYNC_MODE to SyncMode.INCREMENTAL.name))
                        .build()

        fun fullSyncWork(deepScan: Boolean = false) =
                OneTimeWorkRequestBuilder<SyncWorker>()
                        .setInputData(
                                workDataOf(
                                        INPUT_SYNC_MODE to SyncMode.FULL.name,
                                        INPUT_FORCE_METADATA to deepScan
                                )
                        )
                        .build()

        fun rebuildDatabaseWork() =
                OneTimeWorkRequestBuilder<SyncWorker>()
                        .setInputData(workDataOf(INPUT_SYNC_MODE to SyncMode.REBUILD.name))
                        .build()
    }
    
    // Logic to sync Telegram songs into main DB with Unified Library Support
    private suspend fun syncTelegramData() {
        Log.i(TAG, "Syncing Telegram songs to main database (Unified Mode)...")
        try {
            val telegramSongs = telegramDao.getAllTelegramSongs().first()
            val channels = telegramDao.getAllChannels().first().associateBy { it.chatId }
            val existingUnifiedTelegramIds = musicDao.getAllTelegramSongIds()
            
            if (telegramSongs.isEmpty()) { 
                if (existingUnifiedTelegramIds.isNotEmpty()) {
                    musicDao.clearAllTelegramSongs()
                }
                Log.d(TAG, "No Telegram songs to sync.")
                return 
            }

            // 1. Pre-load Local Data for Merging
            val existingArtists = musicDao.getAllArtistsListRaw().associate { it.name.trim().lowercase() to it.id }
            val existingAlbums = musicDao.getAllAlbumsList(emptyList(), false).associate { "${it.title.trim().lowercase()}_${it.artistName?.trim()?.lowercase()}" to it.id }
            val existingArtistImageUrls = musicDao.getAllArtistsListRaw().associate { it.id to it.imageUrl }
            val nextArtistId = AtomicLong((musicDao.getMaxArtistId() ?: 0L) + 1)
            val delimiters = userPreferencesRepository.artistDelimitersFlow.first()
            val wordDelims = userPreferencesRepository.artistWordDelimitersFlow.first()

            val songsToInsert = mutableListOf<SongEntity>()
            val artistsToInsert = mutableMapOf<Long, ArtistEntity>() // Map to dedup by ID
            val albumsToInsert = mutableMapOf<Long, AlbumEntity>()   // Map to dedup by ID
            val crossRefsToInsert = mutableListOf<SongArtistCrossRef>()
            
            telegramSongs.forEach { tSong ->
                val channelName = channels[tSong.chatId]?.title ?: "Telegram Stream"
                // Synthetic negative ID for Song to check existence, but we want to merge metadata
                // We use negative IDs for songs to definitively identify them as Telegram-sourced in the DB
                // This prevents collision with MediaStore numeric IDs.
                val songId = -(tSong.id.hashCode().toLong().absoluteValue)
                val finalSongId = if (songId == 0L) -1L else songId
                
                // 2. Metadata Refinement (ID3 for Downloaded Files)
                var realTitle = tSong.title
                var realArtistName = tSong.artist
                var realAlbumName = channelName
                var realDateAdded = tSong.dateAdded
                var realYear = 0
                var realTrackNumber = 0
                var realDiscNumber: Int? = null
                var realAlbumArtist = "Telegram"
                var realGenre: String? = null
                var realLyrics: String? = null
                var realDuration = tSong.duration
                var realBitrate: Int? = null
                var realSampleRate: Int? = null
                var resolvedAlbumArtUri = tSong.resolveAlbumArtUri()
                
                val file = java.io.File(tSong.filePath)
                if (tSong.filePath.isNotEmpty() && file.exists()) {
                     try {
                        AudioMetadataReader.read(file, readArtwork = false)?.let { meta ->
                            if (!meta.title.isNullOrBlank()) realTitle = meta.title
                            if (!meta.artist.isNullOrBlank()) realArtistName = meta.artist
                            if (!meta.album.isNullOrBlank()) realAlbumName = meta.album
                            if (!meta.albumArtist.isNullOrBlank()) {
                                realAlbumArtist = meta.albumArtist
                            } else if (!realArtistName.isBlank()) {
                                realAlbumArtist = realArtistName
                            }
                            if (!meta.genre.isNullOrBlank()) realGenre = meta.genre
                            if (!meta.lyrics.isNullOrBlank()) realLyrics = meta.lyrics
                            if (meta.trackNumber != null) realTrackNumber = meta.trackNumber
                            if (meta.discNumber != null) realDiscNumber = meta.discNumber
                            if (meta.year != null) realYear = meta.year
                            if (meta.durationMs != null && meta.durationMs > 0L) realDuration = meta.durationMs
                            if (meta.bitrate != null && meta.bitrate > 0) realBitrate = meta.bitrate
                            if (meta.sampleRate != null && meta.sampleRate > 0) realSampleRate = meta.sampleRate
                        }
                        resolvedAlbumArtUri = tSong.resolveAlbumArtUri()
                    } catch (e: Exception) {
                        // Ignore read errors, fall back to TdApi metadata
                    }
                }
                
                // 3. Multi-Artist Processing
                val rawArtistName = if (realArtistName.isBlank()) "Unknown Artist" else realArtistName
                val splitArtists = rawArtistName.splitArtistsByDelimiters(delimiters, wordDelims)
                
                // Process Primary Artist (First in list)
                val primaryArtistName = splitArtists.firstOrNull()?.trim() ?: "Unknown Artist"
                
                var primaryArtistId = -1L
                
                splitArtists.forEachIndexed { index, individualArtistName ->
                    val cleanName = individualArtistName.trim()
                    val lowerName = cleanName.lowercase()
                    
                    // Check if artist exists locally (Merge logic)
                    val existingId = existingArtists[lowerName]
                    
                    val finalArtistId = if (existingId != null) {
                        existingId // Use Positive MediaStore ID
                    } else {
                        // Generate consistent negative ID for Telegram-only artist
                        val synthId = -(cleanName.hashCode().toLong().absoluteValue)
                        if (synthId == 0L) -1L else synthId
                    }

                    if (index == 0) primaryArtistId = finalArtistId

                    // Add to Artist Insert Map
                    if (!artistsToInsert.containsKey(finalArtistId)) {
                        artistsToInsert[finalArtistId] = ArtistEntity(
                            id = finalArtistId,
                            name = cleanName,
                            trackCount = 0, // Will be recalculated by Room or logic
                            imageUrl = existingArtistImageUrls[finalArtistId] // Keep existing image if merging
                        )
                    }

                    // Add Cross Ref
                    crossRefsToInsert.add(SongArtistCrossRef(
                        songId = finalSongId,
                        artistId = finalArtistId,
                        isPrimary = (index == 0)
                    ))
                }

                // 4. Album Logic
                // Try to match existing album by Name + Album Artist
                val albumKey = "${realAlbumName.trim().lowercase()}_${realAlbumArtist.trim().lowercase()}"
                val existingAlbumId = existingAlbums[albumKey]
                
                val finalAlbumId = if (existingAlbumId != null) {
                    existingAlbumId // Merge with local album
                } else {
                    // Synthetic negative ID
                    val synthId = -(realAlbumName.hashCode().toLong().absoluteValue)
                    if (synthId == 0L) -1L else synthId
                }
                
                if (!albumsToInsert.containsKey(finalAlbumId)) {
                     albumsToInsert[finalAlbumId] = AlbumEntity(
                        id = finalAlbumId,
                        title = realAlbumName,
                        artistName = realAlbumArtist, 
                        artistId = primaryArtistId, // Link to primary song artist (or album artist if we resolved it properly)
                        songCount = 0,
                        dateAdded = realDateAdded,
                        year = realYear,
                        albumArtUriString = resolvedAlbumArtUri
                    )
                }

                // 5. Build Final Song Entity
                // Build artists JSON from the split artists and their resolved IDs
                val telegramArtistRefs = splitArtists.mapIndexed { idx, name ->
                    val cleanName = name.trim()
                    val lowerName = cleanName.lowercase()
                    val artId = existingArtists[lowerName]
                        ?: artistsToInsert.values.find { it.name.equals(cleanName, ignoreCase = true) }?.id
                        ?: 0L
                    ArtistRef(id = artId, name = cleanName, isPrimary = idx == 0)
                }.filter { it.name.isNotEmpty() }

                val songEntity = SongEntity(
                    id = finalSongId,
                    title = realTitle,
                    artistName = rawArtistName, // Store full string for display
                    artistId = primaryArtistId,
                    albumName = realAlbumName,
                    albumId = finalAlbumId,
                    albumArtist = realAlbumArtist,
                    duration = realDuration,
                    contentUriString = "telegram://${tSong.chatId}/${tSong.messageId}",
                    albumArtUriString = resolvedAlbumArtUri,
                    filePath = tSong.filePath,
                    parentDirectoryPath = File(tSong.filePath).parent ?: "/Telegram/$channelName",
                    dateAdded = tSong.dateAdded,
                    genre = realGenre,
                    trackNumber = realTrackNumber,
                    discNumber = realDiscNumber,
                    year = realYear,
                    isFavorite = false,
                    lyrics = realLyrics,
                    mimeType = tSong.mimeType,
                    bitrate = realBitrate,
                    sampleRate = realSampleRate,
                    telegramChatId = tSong.chatId,
                    telegramFileId = tSong.fileId,
                    artistsJson = serializeArtistRefs(telegramArtistRefs),
                    sourceType = SourceType.TELEGRAM
                )
                songsToInsert.add(songEntity)
            }
            
            // Calculate song counts for the albums we are inserting
            val albumCounts = songsToInsert.groupingBy { it.albumId }.eachCount()

            val finalAlbums = albumsToInsert.values.map { album ->
                album.copy(songCount = albumCounts[album.id] ?: 0)
            }
            val syncedTelegramSongIds = songsToInsert.map { it.id }.toHashSet()
            val deletedUnifiedSongIds = existingUnifiedTelegramIds.filterNot { it in syncedTelegramSongIds }

            // Upsert into MusicDao
            musicDao.incrementalSyncMusicData(
                songs = songsToInsert,
                albums = finalAlbums,
                artists = artistsToInsert.values.toList(),
                crossRefs = crossRefsToInsert,
                deletedSongIds = deletedUnifiedSongIds
            )
            Log.i(TAG, "Synced ${songsToInsert.size} Telegram songs with Unified Metadata.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to sync Telegram data", e)
        }
    }

    private suspend fun syncNeteaseData() {
        Log.i(TAG, "Syncing Netease songs to main database (Unified Mode)...")
        try {
            val neteaseSongs = neteaseDao.getAllNeteaseSongsList()
            val existingUnifiedNeteaseIds = musicDao.getAllNeteaseSongIds()

            if (neteaseSongs.isEmpty()) {
                if (existingUnifiedNeteaseIds.isNotEmpty()) {
                    musicDao.clearAllNeteaseSongs()
                }
                Log.d(TAG, "No Netease songs to sync.")
                return
            }

            val songsToInsert = ArrayList<SongEntity>(neteaseSongs.size)
            val artistsToInsert = LinkedHashMap<Long, ArtistEntity>()
            val albumsToInsert = LinkedHashMap<Long, AlbumEntity>()
            val crossRefsToInsert = mutableListOf<SongArtistCrossRef>()

            neteaseSongs.forEach { nSong ->
                val songId = toUnifiedNeteaseSongId(nSong.neteaseId)
                val artistNames = parseNeteaseArtistNames(nSong.artist)
                val primaryArtistName = artistNames.firstOrNull() ?: "Unknown Artist"
                val primaryArtistId = toUnifiedNeteaseArtistId(primaryArtistName)

                artistNames.forEachIndexed { index, artistName ->
                    val artistId = toUnifiedNeteaseArtistId(artistName)
                    artistsToInsert.putIfAbsent(
                        artistId,
                        ArtistEntity(
                            id = artistId,
                            name = artistName,
                            trackCount = 0,
                            imageUrl = null
                        )
                    )
                    crossRefsToInsert.add(
                        SongArtistCrossRef(
                            songId = songId,
                            artistId = artistId,
                            isPrimary = index == 0
                        )
                    )
                }

                val albumId = toUnifiedNeteaseAlbumId(nSong.albumId, nSong.album)
                val albumName = nSong.album.ifBlank { "Unknown Album" }
                albumsToInsert.putIfAbsent(
                    albumId,
                    AlbumEntity(
                        id = albumId,
                        title = albumName,
                        artistName = primaryArtistName,
                        artistId = primaryArtistId,
                        songCount = 0,
                        dateAdded = nSong.dateAdded,
                        year = 0,
                        albumArtUriString = nSong.albumArtUrl
                    )
                )

                // Build artists JSON
                val neteaseArtistRefs = artistNames.mapIndexed { idx, name ->
                    ArtistRef(
                        id = toUnifiedNeteaseArtistId(name),
                        name = name,
                        isPrimary = idx == 0
                    )
                }

                songsToInsert.add(
                    SongEntity(
                        id = songId,
                        title = nSong.title,
                        artistName = nSong.artist.ifBlank { primaryArtistName },
                        artistId = primaryArtistId,
                        albumArtist = null,
                        albumName = albumName,
                        albumId = albumId,
                        contentUriString = "netease://${nSong.neteaseId}",
                        albumArtUriString = nSong.albumArtUrl,
                        duration = nSong.duration,
                        genre = NETEASE_GENRE,
                        filePath = "",
                        parentDirectoryPath = NETEASE_PARENT_DIRECTORY,
                        isFavorite = false,
                        lyrics = null,
                        trackNumber = 0,
                        year = 0,
                        dateAdded = nSong.dateAdded.takeIf { it > 0 } ?: System.currentTimeMillis(),
                        mimeType = nSong.mimeType,
                        bitrate = nSong.bitrate,
                        sampleRate = null,
                        telegramChatId = null,
                        telegramFileId = null,
                        artistsJson = serializeArtistRefs(neteaseArtistRefs),
                        sourceType = SourceType.NETEASE
                    )
                )
            }

            val albumCounts = songsToInsert.groupingBy { it.albumId }.eachCount()
            val finalAlbums = albumsToInsert.values.map { album ->
                album.copy(songCount = albumCounts[album.id] ?: 0)
            }

            val currentUnifiedSongIds = songsToInsert.map { it.id }.toSet()
            val deletedUnifiedSongIds = existingUnifiedNeteaseIds.filter { it !in currentUnifiedSongIds }

            musicDao.incrementalSyncMusicData(
                songs = songsToInsert,
                albums = finalAlbums,
                artists = artistsToInsert.values.toList(),
                crossRefs = crossRefsToInsert,
                deletedSongIds = deletedUnifiedSongIds
            )
            Log.i(TAG, "Synced ${songsToInsert.size} Netease songs with Unified Metadata.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to sync Netease data", e)
        }
    }

    private fun parseNeteaseArtistNames(rawArtist: String): List<String> {
        if (rawArtist.isBlank()) return listOf("Unknown Artist")
        val parsed = rawArtist.split(Regex("\\s*[,/&;+、]\\s*"))
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
        return if (parsed.isEmpty()) listOf("Unknown Artist") else parsed
    }

    private fun toUnifiedNeteaseSongId(neteaseId: Long): Long {
        return -(NETEASE_SONG_ID_OFFSET + neteaseId.absoluteValue)
    }

    private fun toUnifiedNeteaseAlbumId(albumId: Long, albumName: String): Long {
        val normalized = if (albumId > 0L) {
            albumId.absoluteValue
        } else {
            albumName.lowercase().hashCode().toLong().absoluteValue
        }
        return -(NETEASE_ALBUM_ID_OFFSET + normalized)
    }

    private fun toUnifiedNeteaseArtistId(artistName: String): Long {
        return -(NETEASE_ARTIST_ID_OFFSET + artistName.lowercase().hashCode().toLong().absoluteValue)
    }

    private suspend fun syncNavidromeData() {
        Log.i(TAG, "Syncing Navidrome data from server...")
        try {
            // Fetch playlists and songs from the Navidrome server, then sync to unified library
            val result = navidromeRepository.syncAllPlaylistsAndSongs()
            result.fold(
                onSuccess = { summary ->
                    Log.i(TAG, "Navidrome sync complete: ${summary.playlistCount} playlists, ${summary.syncedSongCount} songs synced (${summary.failedPlaylistCount} failed)")
                },
                onFailure = { e ->
                    Log.w(TAG, "Navidrome server sync failed, falling back to local cache sync", e)
                    // Fallback: at least sync what we already have cached
                    navidromeRepository.syncUnifiedLibrarySongsFromNavidrome()
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to sync Navidrome data", e)
        }
    }
}
