package com.theveloper.pixelplay.data.repository

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import android.util.Log
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import com.theveloper.pixelplay.data.database.MusicDao
import com.theveloper.pixelplay.data.database.FavoritesDao
import com.theveloper.pixelplay.data.database.toSong
import com.theveloper.pixelplay.data.model.ArtistRef
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.observer.MediaStoreObserver
import com.theveloper.pixelplay.data.preferences.UserPreferencesRepository
import com.theveloper.pixelplay.utils.AlbumArtUtils
import com.theveloper.pixelplay.utils.DirectoryRuleResolver
import com.theveloper.pixelplay.utils.DirectoryFilterUtils
import com.theveloper.pixelplay.utils.LogUtils
import com.theveloper.pixelplay.utils.buildLocalAudioSelection
import com.theveloper.pixelplay.utils.normalizeMetadataText
import com.theveloper.pixelplay.utils.normalizeMetadataTextOrEmpty
import com.theveloper.pixelplay.utils.extractArtistsFromTitle
import com.theveloper.pixelplay.utils.splitArtistsByDelimiters
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

private data class SearchPrefs(
    val favoriteIds: Set<Long>,
    val allowedDirs: Set<String>,
    val blockedDirs: Set<String>,
    val artistDelimiters: List<String>,
    val wordDelims: List<String>,
    val extractFromTitle: Boolean,
    val minDuration: Int
)

@Singleton
class MediaStoreSongRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val mediaStoreObserver: MediaStoreObserver,
    private val favoritesDao: FavoritesDao,
    private val musicDao: MusicDao,
    private val userPreferencesRepository: UserPreferencesRepository
) : SongRepository {

    private suspend fun getFavoriteIds(): Set<Long> {
        return favoritesDao.getFavoriteSongIdsOnce().toSet()
    }

    private fun normalizePath(path: String): String = File(path).absolutePath

    private fun observeSongs(
        extraSelection: String? = null,
        extraSelectionArgs: Array<String>? = null
    ): Flow<List<Song>> {
        return combine(
            mediaStoreObserver.mediaStoreChanges.onStart { emit(Unit) },
            favoritesDao.getFavoriteSongIds().distinctUntilChanged(),
            userPreferencesRepository.allowedDirectoriesFlow.distinctUntilChanged(),
            userPreferencesRepository.blockedDirectoriesFlow.distinctUntilChanged(),
            userPreferencesRepository.artistDelimitersFlow.distinctUntilChanged(),
            userPreferencesRepository.minSongDurationFlow.distinctUntilChanged(),
            userPreferencesRepository.artistWordDelimitersFlow.distinctUntilChanged(),
            userPreferencesRepository.extractArtistsFromTitleFlow.distinctUntilChanged()
        ) { values ->
            val favoriteIds = @Suppress("UNCHECKED_CAST") (values[1] as List<Long>)
            val allowedDirs = @Suppress("UNCHECKED_CAST") (values[2] as Set<String>)
            val blockedDirs = @Suppress("UNCHECKED_CAST") (values[3] as Set<String>)
            val artistDelimiters = @Suppress("UNCHECKED_CAST") (values[4] as List<String>)
            val minDuration = values[5] as Int
            val wordDelimiters = @Suppress("UNCHECKED_CAST") (values[6] as List<String>)
            val extractFromTitle = values[7] as Boolean
            fetchSongsFromMediaStore(
                favoriteIds = favoriteIds.toSet(),
                allowedDirs = allowedDirs.toList(),
                blockedDirs = blockedDirs.toList(),
                artistDelimiters = artistDelimiters,
                wordDelimiters = wordDelimiters,
                extractFromTitle = extractFromTitle,
                minDurationMs = minDuration,
                extraSelection = extraSelection,
                extraSelectionArgs = extraSelectionArgs
            )
        }.distinctUntilChanged().flowOn(Dispatchers.IO)
    }

    override fun getSongs(): Flow<List<Song>> = observeSongs()

    private suspend fun fetchSongsFromMediaStore(
        favoriteIds: Set<Long>,
        allowedDirs: List<String>,
        blockedDirs: List<String>,
        artistDelimiters: List<String>,
        wordDelimiters: List<String> = emptyList(),
        extractFromTitle: Boolean = true,
        minDurationMs: Int = 10000,
        extraSelection: String? = null,
        extraSelectionArgs: Array<String>? = null
    ): List<Song> = withContext(Dispatchers.IO) {
        val songs = mutableListOf<Song>()
        val (baseSelection, baseSelectionArgs) = buildLocalAudioSelection(minDurationMs)
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ARTIST_ID,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.TRACK,
            MediaStore.Audio.Media.YEAR,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.DATE_MODIFIED,
            MediaStore.Audio.Media.MIME_TYPE,
            MediaStore.Audio.Media.ALBUM_ARTIST, // Valid on API 30+, fallback needed if minSdk < 30
            // Genre is difficult in MediaStore.Audio.Media, usually requires separate query.
            // keeping it simple for now, maybe null or fetch separately.
        )

        // Handling API version differences for columns if necessary
        // Assuming minSdk is high enough or columns exist (ALBUM_ARTIST is API 30+, need check if app supports lower)

        val selection = buildString {
            append(baseSelection)
            if (!extraSelection.isNullOrBlank()) {
                append(" AND (")
                append(extraSelection)
                append(")")
            }
        }
        val selectionArgs = if (extraSelectionArgs.isNullOrEmpty()) {
            baseSelectionArgs
        } else {
            baseSelectionArgs + extraSelectionArgs
        }

        val songIdToGenreMap = getSongIdToGenreMap(context.contentResolver)

        try {
            context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                "${MediaStore.Audio.Media.TITLE} ASC"
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val artistIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST_ID)
                val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val albumIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                val pathCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
                val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val trackCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK)
                val yearCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.YEAR)
                val dateAddedCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
                val dateModifiedCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_MODIFIED)
                val mimeTypeCol = cursor.getColumnIndex(MediaStore.Audio.Media.MIME_TYPE)
                val albumArtistCol = cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM_ARTIST) // Can be -1

                val resolver = DirectoryRuleResolver(
                    allowedDirs.map(::normalizePath).toSet(),
                    blockedDirs.map(::normalizePath).toSet()
                )
                val isFilterActive = blockedDirs.isNotEmpty()

                while (cursor.moveToNext()) {
                    val path = cursor.getString(pathCol)

                    // Directory Filtering
                    if (isFilterActive) {
                        val lastSlashIndex = path.lastIndexOf('/')
                        val parentPath = if (lastSlashIndex != -1) path.substring(0, lastSlashIndex) else ""
                        if (resolver.isBlocked(parentPath)) {
                            continue
                        }
                    }

                    val id = cursor.getLong(idCol)
                    val albumId = cursor.getLong(albumIdCol)

                    // Album art (individual / cached per song)
                    val albumArtUriString = AlbumArtUtils.getAlbumArtUri(
                        appContext = context,
                        path = path,
                        songId = id,
                        forceRefresh = false
                    )

                    // Artists parsing (supports character + word delimiters like "feat.", "ft.", "x")
                    val rawArtist = cursor.getString(artistCol).normalizeMetadataTextOrEmpty()
                    val rawTitle = cursor.getString(titleCol).normalizeMetadataTextOrEmpty()

                    // Split artist field by both character and word delimiters
                    val splitArtists = rawArtist.splitArtistsByDelimiters(artistDelimiters, wordDelimiters)
                    val allArtistNames = splitArtists.toMutableList()

                    // Extract featured artists from title (e.g., "Song (feat. Artist)")
                    val displayTitle: String
                    if (extractFromTitle) {
                        val (cleanedTitle, titleArtists) = rawTitle.extractArtistsFromTitle(artistDelimiters, wordDelimiters)
                        displayTitle = cleanedTitle
                        // Add title artists that aren't already in the artist field
                        titleArtists.forEach { ta ->
                            if (allArtistNames.none { it.equals(ta, ignoreCase = true) }) {
                                allArtistNames.add(ta)
                            }
                        }
                    } else {
                        displayTitle = rawTitle
                    }

                    val normalizedArtists = if (allArtistNames.isNotEmpty()) allArtistNames else listOf(rawArtist)
                    val primaryArtistName = normalizedArtists.firstOrNull().orEmpty()

                    val artistRefs = normalizedArtists.mapIndexed { index, name ->
                        ArtistRef(
                            id = if (index == 0) cursor.getLong(artistIdCol)
                            else (name.hashCode().toLong() * -1L) - 10_000L,
                            name = name,
                            isPrimary = index == 0
                        )
                    }

                    val song = Song(
                        id = id.toString(),
                        title = displayTitle,
                        artist = primaryArtistName,
                        artistId = cursor.getLong(artistIdCol),
                        artists = artistRefs,
                        album = cursor.getString(albumCol).normalizeMetadataTextOrEmpty(),
                        albumId = albumId,
                        albumArtist = if (albumArtistCol != -1) cursor.getString(albumArtistCol).normalizeMetadataText() else null,
                        path = path,
                        contentUriString = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id).toString(),
                        albumArtUriString = albumArtUriString,
                        duration = cursor.getLong(durationCol),
                        genre = songIdToGenreMap[id],
                        lyrics = null,
                        isFavorite = favoriteIds.contains(id),
                        trackNumber = cursor.getInt(trackCol) % 1000,
                        year = cursor.getInt(yearCol),
                        dateAdded = cursor.getLong(dateAddedCol),
                        dateModified = cursor.getLong(dateModifiedCol),
                        mimeType = if (mimeTypeCol != -1) cursor.getString(mimeTypeCol) else null,
                        bitrate = null,
                        sampleRate = null
                    )
                    songs.add(song)
                }
            }
        } catch (e: Exception) {
            Log.e("MediaStoreSongRepository", "Error querying MediaStore", e)
        }
        songs
    }

    private fun getSongIdToGenreMap(contentResolver: android.content.ContentResolver): Map<Long, String> {
        val genreMap = mutableMapOf<Long, String>()
        try {
            val genresUri = MediaStore.Audio.Genres.EXTERNAL_CONTENT_URI
            val genresProjection = arrayOf(
                MediaStore.Audio.Genres._ID,
                MediaStore.Audio.Genres.NAME
            )

            contentResolver.query(genresUri, genresProjection, null, null, null)?.use { genreCursor ->
                val genreIdCol = genreCursor.getColumnIndexOrThrow(MediaStore.Audio.Genres._ID)
                val genreNameCol = genreCursor.getColumnIndexOrThrow(MediaStore.Audio.Genres.NAME)

                while (genreCursor.moveToNext()) {
                    val genreId = genreCursor.getLong(genreIdCol)
                    val genreName = genreCursor.getString(genreNameCol).normalizeMetadataTextOrEmpty()

                    if (genreName.isNotBlank() && genreName != "<unknown>") {
                        val membersUri = MediaStore.Audio.Genres.Members.getContentUri("external", genreId)
                        val membersProjection = arrayOf(MediaStore.Audio.Genres.Members.AUDIO_ID)

                        try {
                            contentResolver.query(membersUri, membersProjection, null, null, null)?.use { membersCursor ->
                                val audioIdCol = membersCursor.getColumnIndex(MediaStore.Audio.Genres.Members.AUDIO_ID)
                                if (audioIdCol != -1) {
                                    while (membersCursor.moveToNext()) {
                                        val songId = membersCursor.getLong(audioIdCol)
                                        // If a song has multiple genres, this simple map keeps the last one found.
                                        // Could be improved to join them if needed.
                                        genreMap[songId] = genreName
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            Log.w("MediaStoreSongRepository", "Error querying members for genreId=$genreId", e)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("MediaStoreSongRepository", "Error querying Genres", e)
        }
        return genreMap
    }

    override fun getSongsByAlbum(albumId: Long): Flow<List<Song>> {
        return observeSongs(
            extraSelection = "${MediaStore.Audio.Media.ALBUM_ID} = ?",
            extraSelectionArgs = arrayOf(albumId.toString())
        )
    }

    override fun getSongsByArtist(artistId: Long): Flow<List<Song>> {
        return observeSongs(
            extraSelection = "${MediaStore.Audio.Media.ARTIST_ID} = ?",
            extraSelectionArgs = arrayOf(artistId.toString())
        )
    }

    override suspend fun searchSongs(query: String): List<Song> {
        if (query.isBlank()) return emptyList()
        val (favoriteIds, allowedDirs, blockedDirs, artistDelimiters, wordDelims, extractFromTitle, minDuration) = coroutineScope {
            val fav = async { getFavoriteIds() }
            val allowed = async { userPreferencesRepository.allowedDirectoriesFlow.first() }
            val blocked = async { userPreferencesRepository.blockedDirectoriesFlow.first() }
            val delims = async { userPreferencesRepository.artistDelimitersFlow.first() }
            val wordD = async { userPreferencesRepository.artistWordDelimitersFlow.first() }
            val extract = async { userPreferencesRepository.extractArtistsFromTitleFlow.first() }
            val minDur = async { userPreferencesRepository.minSongDurationFlow.first() }
            SearchPrefs(fav.await(), allowed.await(), blocked.await(), delims.await(), wordD.await(), extract.await(), minDur.await())
        }
        val queryTerm = "%${query.trim()}%"
        return fetchSongsFromMediaStore(
            favoriteIds = favoriteIds,
            allowedDirs = allowedDirs.toList(),
            blockedDirs = blockedDirs.toList(),
            artistDelimiters = artistDelimiters,
            wordDelimiters = wordDelims,
            extractFromTitle = extractFromTitle,
            minDurationMs = minDuration,
            extraSelection = "${MediaStore.Audio.Media.TITLE} LIKE ? COLLATE NOCASE OR ${MediaStore.Audio.Media.ARTIST} LIKE ? COLLATE NOCASE",
            extraSelectionArgs = arrayOf(queryTerm, queryTerm)
        )
    }

    override fun getSongById(songId: Long): Flow<Song?> {
        return observeSongs(
            extraSelection = "${MediaStore.Audio.Media._ID} = ?",
            extraSelectionArgs = arrayOf(songId.toString())
        ).map { songs ->
            songs.firstOrNull()
        }.distinctUntilChanged()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun getPaginatedSongs(): Flow<PagingData<Song>> {
        return combine(
            mediaStoreObserver.mediaStoreChanges.onStart { emit(Unit) },
            userPreferencesRepository.allowedDirectoriesFlow,
            userPreferencesRepository.blockedDirectoriesFlow,
            userPreferencesRepository.minSongDurationFlow
        ) { values ->
            val allowedDirs = @Suppress("UNCHECKED_CAST") (values[1] as Set<String>)
            val blockedDirs = @Suppress("UNCHECKED_CAST") (values[2] as Set<String>)
            val minDuration = values[3] as Int
            Triple(allowedDirs, blockedDirs, minDuration)
        }.flatMapLatest { (allowedDirs, blockedDirs, minDuration) ->
            val minDurationMs = minDuration as Int
            val musicIds = getFilteredSongIds(allowedDirs.toList(), blockedDirs.toList(), minDurationMs)
            val genreMap = getSongIdToGenreMap(context.contentResolver)

            androidx.paging.Pager(
                config = androidx.paging.PagingConfig(
                    pageSize = 50,
                    enablePlaceholders = true,
                    initialLoadSize = 50
                ),
                pagingSourceFactory = {
                    com.theveloper.pixelplay.data.paging.MediaStorePagingSource(context, musicIds, genreMap)
                }
            ).flow
        }.flowOn(Dispatchers.IO)
    }

    private suspend fun getFilteredSongIds(allowedDirs: List<String>, blockedDirs: List<String>, minDurationMs: Int = 10000): List<Long> = withContext(Dispatchers.IO) {
        val ids = mutableListOf<Long>()
        val projection = arrayOf(MediaStore.Audio.Media._ID, MediaStore.Audio.Media.DATA)
        val (selection, selectionArgs) = buildLocalAudioSelection(minDurationMs)

        try {
            context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                "${MediaStore.Audio.Media.TITLE} ASC"
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val pathCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)

                val resolver = DirectoryRuleResolver(
                    allowedDirs.map(::normalizePath).toSet(),
                    blockedDirs.map(::normalizePath).toSet()
                )
                val isFilterActive = blockedDirs.isNotEmpty()

                while (cursor.moveToNext()) {
                    val path = cursor.getString(pathCol)
                    if (isFilterActive) {
                        if (isFilterActive) {
                            val lastSlashIndex = path.lastIndexOf('/')
                            val parentPath = if (lastSlashIndex != -1) path.substring(0, lastSlashIndex) else ""
                            if (resolver.isBlocked(parentPath)) {
                                continue
                            }
                        }
                    }
                    ids.add(cursor.getLong(idCol))
                }
            }
        } catch (e: Exception) {
            Log.e("MediaStoreSongRepository", "Error getting IDs", e)
        }
        ids
    }
    /**
     * Computes allowed parent directories by filtering out blocked directories.
     * Returns Pair(allowedDirs, applyFilter).
     */
    private suspend fun computeAllowedDirs(
        allowedDirs: Set<String>,
        blockedDirs: Set<String>
    ): Pair<List<String>, Boolean> {
        return DirectoryFilterUtils.computeAllowedParentDirs(
            allowedDirs = allowedDirs,
            blockedDirs = blockedDirs,
            getAllParentDirs = { musicDao.getDistinctParentDirectories() },
            normalizePath = ::normalizePath
        )
    }

    private val defaultPagingConfig = androidx.paging.PagingConfig(
        pageSize = 50,
        enablePlaceholders = true,
        maxSize = 250
    )

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun getPaginatedSongs(sortOption: com.theveloper.pixelplay.data.model.SortOption, storageFilter: com.theveloper.pixelplay.data.model.StorageFilter): Flow<PagingData<Song>> {
        return combine(
            userPreferencesRepository.allowedDirectoriesFlow,
            userPreferencesRepository.blockedDirectoriesFlow
        ) { allowedDirs, blockedDirs ->
            allowedDirs to blockedDirs
        }.flatMapLatest { (allowedDirs, blockedDirs) ->
            kotlinx.coroutines.flow.flow {
                val (allowedParentDirs, applyDirectoryFilter) =
                    computeAllowedDirs(allowedDirs, blockedDirs)
                emit(
                    androidx.paging.Pager(
                        config = defaultPagingConfig,
                        pagingSourceFactory = {
                            musicDao.getSongsPaginated(
                                allowedParentDirs = allowedParentDirs,
                                applyDirectoryFilter = applyDirectoryFilter,
                                sortOrder = sortOption.storageKey,
                                filterMode = storageFilter.value
                            )
                        }
                    ).flow
                )
            }.flatMapLatest { it }
        }.map { pagingData ->
            pagingData.map { entity -> entity.toSong() }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun getPaginatedFavoriteSongs(
        sortOption: com.theveloper.pixelplay.data.model.SortOption,
        storageFilter: com.theveloper.pixelplay.data.model.StorageFilter
    ): Flow<PagingData<Song>> {
        return combine(
            userPreferencesRepository.allowedDirectoriesFlow,
            userPreferencesRepository.blockedDirectoriesFlow
        ) { allowedDirs, blockedDirs ->
            allowedDirs to blockedDirs
        }.flatMapLatest { (allowedDirs, blockedDirs) ->
            kotlinx.coroutines.flow.flow {
                val (allowedParentDirs, applyDirectoryFilter) =
                    computeAllowedDirs(allowedDirs, blockedDirs)
                emit(
                    androidx.paging.Pager(
                        config = defaultPagingConfig,
                        pagingSourceFactory = {
                            musicDao.getFavoriteSongsPaginated(
                                allowedParentDirs = allowedParentDirs,
                                applyDirectoryFilter = applyDirectoryFilter,
                                sortOrder = sortOption.storageKey,
                                filterMode = storageFilter.value
                            )
                        }
                    ).flow
                )
            }.flatMapLatest { it }
        }.map { pagingData ->
            pagingData.map { entity -> entity.toSong().copy(isFavorite = true) }
        }
    }

    override suspend fun getFavoriteSongsOnce(
        storageFilter: com.theveloper.pixelplay.data.model.StorageFilter
    ): List<Song> = withContext(Dispatchers.IO) {
        val allowedDirs = userPreferencesRepository.allowedDirectoriesFlow.first()
        val blockedDirs = userPreferencesRepository.blockedDirectoriesFlow.first()
        val (allowedParentDirs, applyDirectoryFilter) =
            computeAllowedDirs(allowedDirs, blockedDirs)
        musicDao.getFavoriteSongsList(
            allowedParentDirs = allowedParentDirs,
            applyDirectoryFilter = applyDirectoryFilter,
            filterMode = storageFilter.value
        )
            .map { entity -> entity.toSong().copy(isFavorite = true) }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun getFavoriteSongCountFlow(
        storageFilter: com.theveloper.pixelplay.data.model.StorageFilter
    ): Flow<Int> {
        return combine(
            userPreferencesRepository.allowedDirectoriesFlow,
            userPreferencesRepository.blockedDirectoriesFlow
        ) { allowedDirs, blockedDirs ->
            allowedDirs to blockedDirs
        }.flatMapLatest { (allowedDirs, blockedDirs) ->
            kotlinx.coroutines.flow.flow {
                val (allowedParentDirs, applyDirectoryFilter) =
                    computeAllowedDirs(allowedDirs, blockedDirs)
                emit(Pair(allowedParentDirs, applyDirectoryFilter))
            }.flatMapLatest { (allowedDirs, applyFilter) ->
                musicDao.getFavoriteSongCount(
                    allowedParentDirs = allowedDirs,
                    applyDirectoryFilter = applyFilter,
                    filterMode = storageFilter.value
                )
            }
        }
    }
}
