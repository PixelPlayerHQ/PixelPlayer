package com.theveloper.pixelplay.data.database

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.sqlite.db.SimpleSQLiteQuery
import com.theveloper.pixelplay.utils.AudioMeta
import kotlinx.coroutines.flow.Flow

private const val SONG_DETAIL_PROJECTION = """
    songs.id AS id,
    songs.title AS title,
    songs.artist_name AS artist_name,
    songs.artist_id AS artist_id,
    songs.album_artist AS album_artist,
    songs.album_name AS album_name,
    songs.album_id AS album_id,
    songs.content_uri_string AS content_uri_string,
    songs.album_art_uri_string AS album_art_uri_string,
    songs.duration AS duration,
    songs.genre AS genre,
    songs.file_path AS file_path,
    songs.parent_directory_path AS parent_directory_path,
    songs.is_favorite AS is_favorite,
    COALESCE(song_lyrics.content, songs.lyrics) AS lyrics,
    songs.track_number AS track_number,
    songs.year AS year,
    songs.date_added AS date_added,
    songs.mime_type AS mime_type,
    songs.bitrate AS bitrate,
    songs.sample_rate AS sample_rate,
    songs.telegram_chat_id AS telegram_chat_id,
    songs.telegram_file_id AS telegram_file_id
"""

@Dao
interface MusicDao {

    // --- Insert Operations ---
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSongsIgnoreConflicts(songs: List<SongEntity>): List<Long>

    @Update
    suspend fun updateSongs(songs: List<SongEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAlbumsIgnoreConflicts(albums: List<AlbumEntity>): List<Long>

    @Update
    suspend fun updateAlbums(albums: List<AlbumEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertArtistsIgnoreConflicts(artists: List<ArtistEntity>): List<Long>

    @Update
    suspend fun updateArtists(artists: List<ArtistEntity>)

    @Transaction
    suspend fun insertSongs(songs: List<SongEntity>) {
        if (songs.isEmpty()) return
        val insertResults = insertSongsIgnoreConflicts(songs)
        val songsToUpdate = mutableListOf<SongEntity>()
        insertResults.forEachIndexed { index, rowId ->
            if (rowId == -1L) songsToUpdate.add(songs[index])
        }
        if (songsToUpdate.isNotEmpty()) {
            updateSongs(songsToUpdate)
        }
    }

    @Transaction
    suspend fun insertAlbums(albums: List<AlbumEntity>) {
        if (albums.isEmpty()) return
        val insertResults = insertAlbumsIgnoreConflicts(albums)
        val albumsToUpdate = mutableListOf<AlbumEntity>()
        insertResults.forEachIndexed { index, rowId ->
            if (rowId == -1L) albumsToUpdate.add(albums[index])
        }
        if (albumsToUpdate.isNotEmpty()) {
            updateAlbums(albumsToUpdate)
        }
    }

    @Transaction
    suspend fun insertArtists(artists: List<ArtistEntity>) {
        if (artists.isEmpty()) return
        val insertResults = insertArtistsIgnoreConflicts(artists)
        val artistsToUpdate = mutableListOf<ArtistEntity>()
        insertResults.forEachIndexed { index, rowId ->
            if (rowId == -1L) artistsToUpdate.add(artists[index])
        }
        if (artistsToUpdate.isNotEmpty()) {
            updateArtists(artistsToUpdate)
        }
    }



    @Transaction
    suspend fun insertMusicData(songs: List<SongEntity>, albums: List<AlbumEntity>, artists: List<ArtistEntity>) {
        insertArtists(artists)
        insertAlbums(albums)
        insertSongs(songs)
    }

    @Transaction
    suspend fun clearAllMusicData() {
        clearAllSongs()
        clearAllAlbums()
        clearAllArtists()
    }

    // --- Clear Operations ---
    @Query("DELETE FROM songs")
    suspend fun clearAllSongs()

    @Query("DELETE FROM albums")
    suspend fun clearAllAlbums()

    @Query("DELETE FROM artists")
    suspend fun clearAllArtists()

    // --- Incremental Sync Operations ---
    @Query("SELECT id FROM songs")
    suspend fun getAllSongIds(): List<Long>

    @Query("""
        SELECT id FROM songs
        WHERE content_uri_string NOT LIKE 'telegram://%'
        AND content_uri_string NOT LIKE 'netease://%'
        AND content_uri_string NOT LIKE 'qqmusic://%'
        AND content_uri_string NOT LIKE 'navidrome://%'
        AND content_uri_string NOT LIKE 'gdrive://%'
    """)
    suspend fun getAllMediaStoreSongIds(): List<Long>

    @Query("DELETE FROM songs WHERE id IN (:songIds)")
    suspend fun deleteSongsByIds(songIds: List<Long>)

    @Query("DELETE FROM song_artist_cross_ref WHERE song_id IN (:songIds)")
    suspend fun deleteCrossRefsBySongIds(songIds: List<Long>)

    @Query("DELETE FROM favorites WHERE songId IN (:songIds)")
    suspend fun deleteFavoritesBySongIds(songIds: List<Long>)

    @Query("DELETE FROM lyrics WHERE songId IN (:songIds)")
    suspend fun deleteLyricsBySongIds(songIds: List<Long>)

    @Query("SELECT id FROM songs WHERE content_uri_string LIKE 'telegram://%'")
    suspend fun getAllTelegramSongIds(): List<Long>

    @Query("""
        SELECT id FROM songs
        WHERE telegram_chat_id = :chatId
        OR content_uri_string LIKE 'telegram://' || :chatId || '/%'
    """)
    suspend fun getTelegramSongIdsByChatId(chatId: Long): List<Long>

    @Query("SELECT id FROM songs WHERE content_uri_string LIKE 'netease://%'")
    suspend fun getAllNeteaseSongIds(): List<Long>

    @Query("SELECT id FROM songs WHERE content_uri_string LIKE 'gdrive://%'")
    suspend fun getAllGDriveSongIds(): List<Long>

    @Query("SELECT id FROM songs WHERE content_uri_string LIKE 'qqmusic://%'")
    suspend fun getAllQqMusicSongIds(): List<Long>

    @Query("SELECT id FROM songs WHERE content_uri_string LIKE 'navidrome://%'")
    suspend fun getAllNavidromeSongIds(): List<Long>

    @Transaction
    suspend fun deleteSongsAndRelatedData(songIds: List<Long>) {
        if (songIds.isEmpty()) return
        songIds.chunked(CROSS_REF_BATCH_SIZE).forEach { chunk ->
            deleteCrossRefsBySongIds(chunk)
            deleteFavoritesBySongIds(chunk)
            deleteLyricsBySongIds(chunk)
            deleteSongsByIds(chunk)
        }
        deleteOrphanedAlbums()
        deleteOrphanedArtists()
    }

    @Transaction
    suspend fun clearAllNeteaseSongs() {
        val neteaseSongIds = getAllNeteaseSongIds()
        if (neteaseSongIds.isEmpty()) return
        deleteSongsAndRelatedData(neteaseSongIds)
    }

    @Transaction
    suspend fun clearAllGDriveSongs() {
        val gdriveSongIds = getAllGDriveSongIds()
        if (gdriveSongIds.isEmpty()) return
        deleteSongsAndRelatedData(gdriveSongIds)
    }

    @Transaction
    suspend fun clearAllQqMusicSongs() {
        val qqMusicSongIds = getAllQqMusicSongIds()
        if (qqMusicSongIds.isEmpty()) return
        deleteSongsAndRelatedData(qqMusicSongIds)
    }

    @Transaction
    suspend fun clearAllNavidromeSongs() {
        val navidromeSongIds = getAllNavidromeSongIds()
        if (navidromeSongIds.isEmpty()) return
        deleteSongsAndRelatedData(navidromeSongIds)
    }

    @Transaction
    suspend fun clearAllTelegramSongs() {
        val telegramSongIds = getAllTelegramSongIds()
        if (telegramSongIds.isEmpty()) return
        deleteSongsAndRelatedData(telegramSongIds)
    }

    @Transaction
    suspend fun clearTelegramSongsForChat(chatId: Long) {
        val telegramSongIds = getTelegramSongIdsByChatId(chatId)
        if (telegramSongIds.isEmpty()) return
        deleteSongsAndRelatedData(telegramSongIds)
    }

    /**
     * Incrementally sync music data: upsert new/modified songs and remove deleted ones.
     * More efficient than clear-and-replace for large libraries with few changes.
     */
    @Transaction
    suspend fun incrementalSyncMusicData(
        songs: List<SongEntity>,
        albums: List<AlbumEntity>,
        artists: List<ArtistEntity>,
        crossRefs: List<SongArtistCrossRef>,
        deletedSongIds: List<Long>
    ) {
        // Protect cloud songs from deletion during generic media scan
        // Only allow explicit deletions if the list is non-empty.
        // During general refresh, deletedSongIds strictly contains local MediaStore IDs only.
        if (deletedSongIds.isNotEmpty()) {
            deletedSongIds.chunked(CROSS_REF_BATCH_SIZE).forEach { chunk ->
                deleteCrossRefsBySongIds(chunk)
                deleteFavoritesBySongIds(chunk)
                deleteLyricsBySongIds(chunk)
                deleteSongsByIds(chunk)
            }
        }

        // Upsert artists, albums, and songs.
        insertArtists(artists)
        insertAlbums(albums)

        // Insert songs in chunks to allow concurrent reads
        songs.chunked(SONG_BATCH_SIZE).forEach { chunk ->
            insertSongs(chunk)
        }

        // Delete old cross-refs for updated songs and insert new ones
        val updatedSongIds = songs.map { it.id }
        updatedSongIds.chunked(CROSS_REF_BATCH_SIZE).forEach { chunk ->
            deleteCrossRefsBySongIds(chunk)
        }
        crossRefs.chunked(CROSS_REF_BATCH_SIZE).forEach { chunk ->
            insertSongArtistCrossRefs(chunk)
        }

        // Clean up orphaned albums and artists
        deleteOrphanedAlbums()
        deleteOrphanedArtists()
    }

    // --- Directory Helper ---
    @Query("SELECT DISTINCT parent_directory_path FROM songs")
    suspend fun getDistinctParentDirectories(): List<String>

    // --- Song Queries ---
    // Updated getSongs to include Telegram songs (negative IDs) regardless of directory filter
    @Query("""
        SELECT * FROM songs
        WHERE (:applyDirectoryFilter = 0 OR id < 0 OR parent_directory_path IN (:allowedParentDirs))
        ORDER BY title ASC
    """)
    fun getSongs(
        allowedParentDirs: List<String>,
        applyDirectoryFilter: Boolean
    ): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE id IN (:songIds)")
    suspend fun getSongsByIdsListSimple(songIds: List<Long>): List<SongEntity>

    @Query(
        "SELECT " + SONG_DETAIL_PROJECTION + """
        FROM songs
        LEFT JOIN lyrics AS song_lyrics ON song_lyrics.songId = songs.id
        WHERE songs.id = :songId
        """
    )
    fun getSongById(songId: Long): Flow<SongEntity?>

    @Query(
        "SELECT " + SONG_DETAIL_PROJECTION + """
        FROM songs
        LEFT JOIN lyrics AS song_lyrics ON song_lyrics.songId = songs.id
        WHERE songs.id = :songId
        """
    )
    suspend fun getSongByIdOnce(songId: Long): SongEntity?
    
    @Query(
        "SELECT " + SONG_DETAIL_PROJECTION + """
        FROM songs
        LEFT JOIN lyrics AS song_lyrics ON song_lyrics.songId = songs.id
        WHERE songs.file_path = :path
        LIMIT 1
        """
    )
    suspend fun getSongByPath(path: String): SongEntity?

    //@Query("SELECT * FROM songs WHERE id IN (:songIds)")
    @Query("""
        SELECT * FROM songs
        WHERE id IN (:songIds)
        AND (:applyDirectoryFilter = 0 OR id < 0 OR parent_directory_path IN (:allowedParentDirs))
    """)
    fun getSongsByIds(
        songIds: List<Long>,
        allowedParentDirs: List<String>,
        applyDirectoryFilter: Boolean
    ): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE album_id = :albumId ORDER BY title ASC")
    fun getSongsByAlbumId(albumId: Long): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE artist_id = :artistId ORDER BY title ASC")
    fun getSongsByArtistId(artistId: Long): Flow<List<SongEntity>>

    @Query("""
        SELECT * FROM songs
        WHERE (:applyDirectoryFilter = 0 OR id < 0 OR parent_directory_path IN (:allowedParentDirs))
        AND (title LIKE '%' || :query || '%' OR artist_name LIKE '%' || :query || '%')
        ORDER BY title ASC
    """)
    fun searchSongs(
        query: String,
        allowedParentDirs: List<String>,
        applyDirectoryFilter: Boolean
    ): Flow<List<SongEntity>>

    @Query("SELECT COUNT(*) FROM songs")
    fun getSongCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM songs")
    suspend fun getSongCountOnce(): Int

    /**
     * Returns random songs for efficient shuffle without loading all songs into memory.
     * Uses SQLite RANDOM() for true randomness.
     */
    @Query("""
        SELECT * FROM songs
        WHERE (:applyDirectoryFilter = 0 OR id < 0 OR parent_directory_path IN (:allowedParentDirs))
        ORDER BY RANDOM()
        LIMIT :limit
    """)
    suspend fun getRandomSongs(
        limit: Int,
        allowedParentDirs: List<String> = emptyList(),
        applyDirectoryFilter: Boolean = false
    ): List<SongEntity>

    @Query("""
        SELECT * FROM songs
        WHERE (:applyDirectoryFilter = 0 OR id < 0 OR parent_directory_path IN (:allowedParentDirs))
    """)
    fun getAllSongs(
        allowedParentDirs: List<String> = emptyList(),
        applyDirectoryFilter: Boolean = false
    ): Flow<List<SongEntity>>

    @Query("""
        SELECT id, parent_directory_path, title, album_art_uri_string FROM songs
        WHERE (:applyDirectoryFilter = 0 OR id < 0 OR parent_directory_path IN (:allowedParentDirs))
        AND (
            :filterMode = 0
            OR (
                :filterMode = 1
                AND content_uri_string NOT LIKE 'telegram://%'
                AND content_uri_string NOT LIKE 'netease://%'
                AND content_uri_string NOT LIKE 'gdrive://%'
                AND content_uri_string NOT LIKE 'qqmusic://%'
                AND content_uri_string NOT LIKE 'navidrome://%'
            )
            OR (
                :filterMode = 2
                AND (
                    content_uri_string LIKE 'telegram://%'
                    OR content_uri_string LIKE 'netease://%'
                    OR content_uri_string LIKE 'gdrive://%'
                    OR content_uri_string LIKE 'qqmusic://%'
                    OR content_uri_string LIKE 'navidrome://%'
                )
            )
        )
        ORDER BY parent_directory_path ASC, title ASC
    """)
    fun getFolderSongs(
        allowedParentDirs: List<String> = emptyList(),
        applyDirectoryFilter: Boolean = false,
        filterMode: Int
    ): Flow<List<FolderSongRow>>

    @Query("""
        SELECT id FROM songs
        WHERE (:applyDirectoryFilter = 0 OR parent_directory_path IN (:allowedParentDirs))
        AND (
            :filterMode = 0
            OR (
                :filterMode = 1
                AND content_uri_string NOT LIKE 'telegram://%'
                AND content_uri_string NOT LIKE 'netease://%'
                AND content_uri_string NOT LIKE 'gdrive://%'
                AND content_uri_string NOT LIKE 'qqmusic://%'
                AND content_uri_string NOT LIKE 'navidrome://%'
            )
            OR (
                :filterMode = 2
                AND (
                    content_uri_string LIKE 'telegram://%'
                    OR content_uri_string LIKE 'netease://%'
                    OR content_uri_string LIKE 'gdrive://%'
                    OR content_uri_string LIKE 'qqmusic://%'
                    OR content_uri_string LIKE 'navidrome://%'
                )
            )
        )
        ORDER BY
            CASE WHEN :sortOrder = 'song_default_order' THEN track_number END ASC,
            CASE WHEN :sortOrder = 'song_title_az' THEN title END ASC,
            CASE WHEN :sortOrder = 'song_title_za' THEN title END DESC,
            CASE WHEN :sortOrder = 'song_artist' THEN artist_name END ASC,
            CASE WHEN :sortOrder = 'song_album' THEN album_name END ASC,
            CASE WHEN :sortOrder = 'song_date_added' THEN date_added END DESC,
            CASE WHEN :sortOrder = 'song_duration' THEN duration END DESC,
            
            title ASC
    """)
    suspend fun getSongIdsSorted(
        allowedParentDirs: List<String>,
        applyDirectoryFilter: Boolean,
        sortOrder: String,
        filterMode: Int
    ): List<Long>

    // --- Paginated Queries for Large Libraries ---
    /**
     * Returns a PagingSource for songs, enabling efficient pagination for large libraries.
     * Room auto-generates the PagingSource implementation.
     */
    @Query("""
        SELECT * FROM songs
        WHERE (:applyDirectoryFilter = 0 OR parent_directory_path IN (:allowedParentDirs))
        AND (
            :filterMode = 0
            OR (
                :filterMode = 1
                AND content_uri_string NOT LIKE 'telegram://%'
                AND content_uri_string NOT LIKE 'netease://%'
                AND content_uri_string NOT LIKE 'gdrive://%'
                AND content_uri_string NOT LIKE 'qqmusic://%'
                AND content_uri_string NOT LIKE 'navidrome://%'
            )
            OR (
                :filterMode = 2
                AND (
                    content_uri_string LIKE 'telegram://%'
                    OR content_uri_string LIKE 'netease://%'
                    OR content_uri_string LIKE 'gdrive://%'
                    OR content_uri_string LIKE 'qqmusic://%'
                    OR content_uri_string LIKE 'navidrome://%'
                )
            )
        )
        ORDER BY
            CASE WHEN :sortOrder = 'song_default_order' THEN track_number END ASC,
            CASE WHEN :sortOrder = 'song_title_az' THEN title END ASC,
            CASE WHEN :sortOrder = 'song_title_za' THEN title END DESC,
            CASE WHEN :sortOrder = 'song_artist' THEN artist_name END ASC,
            CASE WHEN :sortOrder = 'song_album' THEN album_name END ASC,
            CASE WHEN :sortOrder = 'song_date_added' THEN date_added END DESC,
            CASE WHEN :sortOrder = 'song_duration' THEN duration END DESC,
            
            -- Secondary sort falls back to title for consistency
            title ASC
    """)
    fun getSongsPaginated(
        allowedParentDirs: List<String>,
        applyDirectoryFilter: Boolean,
        sortOrder: String,
        filterMode: Int
    ): PagingSource<Int, SongEntity>

    // --- Paginated Favorites Queries ---
    /**
     * Returns a PagingSource for favorite songs, enabling efficient pagination.
     * Joins songs with favorites table and supports multi-sort.
     */
    @Query("""
        SELECT songs.* FROM songs
        INNER JOIN favorites ON songs.id = favorites.songId AND favorites.isFavorite = 1
        WHERE (:applyDirectoryFilter = 0 OR songs.parent_directory_path IN (:allowedParentDirs))
        AND (
            :filterMode = 0
            OR (
                :filterMode = 1
                AND songs.content_uri_string NOT LIKE 'telegram://%'
                AND songs.content_uri_string NOT LIKE 'netease://%'
                AND songs.content_uri_string NOT LIKE 'gdrive://%'
                AND songs.content_uri_string NOT LIKE 'qqmusic://%'
                AND songs.content_uri_string NOT LIKE 'navidrome://%'
            )
            OR (
                :filterMode = 2
                AND (
                    songs.content_uri_string LIKE 'telegram://%'
                    OR songs.content_uri_string LIKE 'netease://%'
                    OR songs.content_uri_string LIKE 'gdrive://%'
                    OR songs.content_uri_string LIKE 'qqmusic://%'
                    OR songs.content_uri_string LIKE 'navidrome://%'
                )
            )
        )
        ORDER BY
            CASE WHEN :sortOrder = 'liked_title_az' THEN songs.title END ASC,
            CASE WHEN :sortOrder = 'liked_title_za' THEN songs.title END DESC,
            CASE WHEN :sortOrder = 'liked_artist' THEN songs.artist_name END ASC,
            CASE WHEN :sortOrder = 'liked_album' THEN songs.album_name END ASC,
            CASE WHEN :sortOrder = 'liked_date_liked' THEN favorites.timestamp END DESC,
            songs.title ASC
    """)
    fun getFavoriteSongsPaginated(
        allowedParentDirs: List<String>,
        applyDirectoryFilter: Boolean,
        sortOrder: String,
        filterMode: Int
    ): PagingSource<Int, SongEntity>

    /**
     * Returns all favorite songs as a list (for playback queue when shuffling).
     */
    @Query("""
        SELECT songs.* FROM songs
        INNER JOIN favorites ON songs.id = favorites.songId AND favorites.isFavorite = 1
        WHERE (:applyDirectoryFilter = 0 OR songs.parent_directory_path IN (:allowedParentDirs))
        AND (
            :filterMode = 0
            OR (
                :filterMode = 1
                AND songs.content_uri_string NOT LIKE 'telegram://%'
                AND songs.content_uri_string NOT LIKE 'netease://%'
                AND songs.content_uri_string NOT LIKE 'gdrive://%'
                AND songs.content_uri_string NOT LIKE 'qqmusic://%'
                AND songs.content_uri_string NOT LIKE 'navidrome://%'
            )
            OR (
                :filterMode = 2
                AND (
                    songs.content_uri_string LIKE 'telegram://%'
                    OR songs.content_uri_string LIKE 'netease://%'
                    OR songs.content_uri_string LIKE 'gdrive://%'
                    OR songs.content_uri_string LIKE 'qqmusic://%'
                    OR songs.content_uri_string LIKE 'navidrome://%'
                )
            )
        )
        ORDER BY songs.title ASC
    """)
    suspend fun getFavoriteSongsList(
        allowedParentDirs: List<String>,
        applyDirectoryFilter: Boolean,
        filterMode: Int
    ): List<SongEntity>

    /**
     * Returns the count of favorite songs (reactive).
     */
    @Query("""
        SELECT COUNT(*) FROM songs
        INNER JOIN favorites ON songs.id = favorites.songId AND favorites.isFavorite = 1
        WHERE (:applyDirectoryFilter = 0 OR songs.parent_directory_path IN (:allowedParentDirs))
        AND (
            :filterMode = 0
            OR (
                :filterMode = 1
                AND songs.content_uri_string NOT LIKE 'telegram://%'
                AND songs.content_uri_string NOT LIKE 'netease://%'
                AND songs.content_uri_string NOT LIKE 'gdrive://%'
                AND songs.content_uri_string NOT LIKE 'qqmusic://%'
                AND songs.content_uri_string NOT LIKE 'navidrome://%'
            )
            OR (
                :filterMode = 2
                AND (
                    songs.content_uri_string LIKE 'telegram://%'
                    OR songs.content_uri_string LIKE 'netease://%'
                    OR songs.content_uri_string LIKE 'gdrive://%'
                    OR songs.content_uri_string LIKE 'qqmusic://%'
                    OR songs.content_uri_string LIKE 'navidrome://%'
                )
            )
        )
    """)
    fun getFavoriteSongCount(
        allowedParentDirs: List<String>,
        applyDirectoryFilter: Boolean,
        filterMode: Int
    ): Flow<Int>

    // --- Paginated Search Query ---
    /**
     * Returns a PagingSource for search results, enabling efficient pagination for large result sets.
     */
    @Query("""
        SELECT * FROM songs
        WHERE (:applyDirectoryFilter = 0 OR parent_directory_path IN (:allowedParentDirs))
        AND (title LIKE '%' || :query || '%' OR artist_name LIKE '%' || :query || '%')
        ORDER BY title ASC
    """)
    fun searchSongsPaginated(
        query: String,
        allowedParentDirs: List<String>,
        applyDirectoryFilter: Boolean
    ): PagingSource<Int, SongEntity>

    /**
     * Search songs with a result limit for non-paginated contexts.
     */
    @Query("""
        SELECT * FROM songs
        WHERE (:applyDirectoryFilter = 0 OR parent_directory_path IN (:allowedParentDirs))
        AND (title LIKE '%' || :query || '%' OR artist_name LIKE '%' || :query || '%')
        ORDER BY title ASC
        LIMIT :limit
    """)
    fun searchSongsLimited(
        query: String,
        allowedParentDirs: List<String>,
        applyDirectoryFilter: Boolean,
        limit: Int
    ): Flow<List<SongEntity>>

    // --- Paginated Genre Query ---
    /**
     * Returns a PagingSource for songs in a specific genre.
     */
    @Query("""
        SELECT * FROM songs
        WHERE (:applyDirectoryFilter = 0 OR parent_directory_path IN (:allowedParentDirs))
        AND genre LIKE :genreName
        ORDER BY title ASC
    """)
    fun getSongsByGenrePaginated(
        genreName: String,
        allowedParentDirs: List<String>,
        applyDirectoryFilter: Boolean
    ): PagingSource<Int, SongEntity>

    // --- Album Queries ---
    @Query("""
        SELECT
            albums.id AS id,
            albums.title AS title,
            albums.artist_name AS artist_name,
            albums.artist_id AS artist_id,
            albums.album_art_uri_string AS album_art_uri_string,
            COUNT(songs.id) AS song_count,
            albums.year AS year
        FROM albums
        INNER JOIN songs ON albums.id = songs.album_id
        WHERE (:applyDirectoryFilter = 0 OR songs.id < 0 OR songs.parent_directory_path IN (:allowedParentDirs))
        AND (
            :filterMode = 0
            OR (
                :filterMode = 1
                AND songs.content_uri_string NOT LIKE 'telegram://%'
                AND songs.content_uri_string NOT LIKE 'netease://%'
                AND songs.content_uri_string NOT LIKE 'gdrive://%'
                AND songs.content_uri_string NOT LIKE 'qqmusic://%'
                AND songs.content_uri_string NOT LIKE 'navidrome://%'
            )
            OR (
                :filterMode = 2
                AND (
                    songs.content_uri_string LIKE 'telegram://%'
                    OR songs.content_uri_string LIKE 'netease://%'
                    OR songs.content_uri_string LIKE 'gdrive://%'
                    OR songs.content_uri_string LIKE 'qqmusic://%'
                    OR songs.content_uri_string LIKE 'navidrome://%'
                )
            )
        )
        GROUP BY
            albums.id,
            albums.title,
            albums.artist_name,
            albums.artist_id,
            albums.album_art_uri_string,
            albums.year
        ORDER BY albums.title ASC
    """)
    fun getAlbums(
        allowedParentDirs: List<String>,
        applyDirectoryFilter: Boolean,
        filterMode: Int
    ): Flow<List<AlbumEntity>>

    @Query("""
        SELECT
            albums.id AS id,
            albums.title AS title,
            albums.artist_name AS artist_name,
            albums.artist_id AS artist_id,
            albums.album_art_uri_string AS album_art_uri_string,
            (
                SELECT COUNT(*)
                FROM songs
                WHERE songs.album_id = albums.id
            ) AS song_count,
            albums.year AS year
        FROM albums
        WHERE albums.id = :albumId
        LIMIT 1
    """)
    fun getAlbumById(albumId: Long): Flow<AlbumEntity?>

    @Query("""
        SELECT
            albums.id AS id,
            albums.title AS title,
            albums.artist_name AS artist_name,
            albums.artist_id AS artist_id,
            albums.album_art_uri_string AS album_art_uri_string,
            (
                SELECT COUNT(*)
                FROM songs
                WHERE songs.album_id = albums.id
            ) AS song_count,
            albums.year AS year
        FROM albums
        WHERE albums.title LIKE '%' || :query || '%'
        ORDER BY albums.title ASC
    """)
    fun searchAlbums(query: String): Flow<List<AlbumEntity>>

    @Query("SELECT COUNT(*) FROM albums")
    fun getAlbumCount(): Flow<Int>

    // Version of getAlbums that returns a List for one-shot reads
    @Query("""
        SELECT
            albums.id AS id,
            albums.title AS title,
            albums.artist_name AS artist_name,
            albums.artist_id AS artist_id,
            albums.album_art_uri_string AS album_art_uri_string,
            COUNT(songs.id) AS song_count,
            albums.year AS year
        FROM albums
        INNER JOIN songs ON albums.id = songs.album_id
        WHERE (:applyDirectoryFilter = 0 OR songs.id < 0 OR songs.parent_directory_path IN (:allowedParentDirs))
        GROUP BY
            albums.id,
            albums.title,
            albums.artist_name,
            albums.artist_id,
            albums.album_art_uri_string,
            albums.year
        ORDER BY albums.title ASC
    """)
    suspend fun getAllAlbumsList(
        allowedParentDirs: List<String>,
        applyDirectoryFilter: Boolean
    ): List<AlbumEntity>

    @Query("""
        SELECT
            albums.id AS id,
            albums.title AS title,
            albums.artist_name AS artist_name,
            albums.artist_id AS artist_id,
            albums.album_art_uri_string AS album_art_uri_string,
            COUNT(songs.id) AS song_count,
            albums.year AS year
        FROM albums
        LEFT JOIN songs ON albums.id = songs.album_id
        WHERE albums.artist_id = :artistId
        GROUP BY
            albums.id,
            albums.title,
            albums.artist_name,
            albums.artist_id,
            albums.album_art_uri_string,
            albums.year
        ORDER BY albums.title ASC
    """)
    fun getAlbumsByArtistId(artistId: Long): Flow<List<AlbumEntity>>

    @Query("""
        SELECT
            albums.id AS id,
            albums.title AS title,
            albums.artist_name AS artist_name,
            albums.artist_id AS artist_id,
            albums.album_art_uri_string AS album_art_uri_string,
            COUNT(songs.id) AS song_count,
            albums.year AS year
        FROM albums
        INNER JOIN songs ON albums.id = songs.album_id
        WHERE (:applyDirectoryFilter = 0 OR songs.id < 0 OR songs.parent_directory_path IN (:allowedParentDirs))
        AND (albums.title LIKE '%' || :query || '%' OR albums.artist_name LIKE '%' || :query || '%')
        GROUP BY
            albums.id,
            albums.title,
            albums.artist_name,
            albums.artist_id,
            albums.album_art_uri_string,
            albums.year
        ORDER BY albums.title ASC
    """)
    fun searchAlbums(
        query: String,
        allowedParentDirs: List<String>,
        applyDirectoryFilter: Boolean
    ): Flow<List<AlbumEntity>>

    // --- Artist Queries ---
    @Query("""
        SELECT DISTINCT artists.* FROM artists
        INNER JOIN songs ON artists.id = songs.artist_id
        WHERE (:applyDirectoryFilter = 0 OR songs.id < 0 OR songs.parent_directory_path IN (:allowedParentDirs))
        ORDER BY artists.name ASC
    """)
    fun getArtists(
        allowedParentDirs: List<String>,
        applyDirectoryFilter: Boolean
    ): Flow<List<ArtistEntity>>

    /**
     * Unfiltered list of all artists (including those only reachable via cross-refs).
     */
    @Query("SELECT * FROM artists ORDER BY name ASC")
    fun getAllArtistsRaw(): Flow<List<ArtistEntity>>

    @Query("SELECT * FROM artists WHERE id = :artistId")
    fun getArtistById(artistId: Long): Flow<ArtistEntity?>

    @Query("SELECT * FROM artists WHERE name LIKE '%' || :query || '%' ORDER BY name ASC")
    fun searchArtists(query: String): Flow<List<ArtistEntity>>

    @Query("SELECT COUNT(*) FROM artists")
    fun getArtistCount(): Flow<Int>

    // Version of getArtists that returns a List for one-shot reads
    @Query("""
        SELECT DISTINCT artists.* FROM artists
        INNER JOIN songs ON artists.id = songs.artist_id
        WHERE (:applyDirectoryFilter = 0 OR songs.id < 0 OR songs.parent_directory_path IN (:allowedParentDirs))
        ORDER BY artists.name ASC
    """)
    suspend fun getAllArtistsList(
        allowedParentDirs: List<String>,
        applyDirectoryFilter: Boolean
    ): List<ArtistEntity>

    /**
     * Unfiltered list of all artists (one-shot).
     */
    @Query("SELECT * FROM artists ORDER BY name ASC")
    suspend fun getAllArtistsListRaw(): List<ArtistEntity>

    @Query("""
        SELECT artists.id, artists.name, artists.image_url, artists.custom_image_uri,
               COUNT(DISTINCT songs.id) AS track_count
        FROM artists
        INNER JOIN song_artist_cross_ref ON artists.id = song_artist_cross_ref.artist_id
        INNER JOIN songs ON song_artist_cross_ref.song_id = songs.id
        WHERE (:applyDirectoryFilter = 0 OR songs.id < 0 OR songs.parent_directory_path IN (:allowedParentDirs))
        AND artists.name LIKE '%' || :query || '%'
        GROUP BY artists.id
        ORDER BY artists.name ASC
    """)
    fun searchArtists(
        query: String,
        allowedParentDirs: List<String>,
        applyDirectoryFilter: Boolean
    ): Flow<List<ArtistEntity>>

    // --- Artist Image Operations ---
    @Query("SELECT image_url FROM artists WHERE id = :artistId")
    suspend fun getArtistImageUrl(artistId: Long): String?

    @Query("SELECT image_url FROM artists WHERE LOWER(TRIM(name)) = LOWER(TRIM(:name)) LIMIT 1")
    suspend fun getArtistImageUrlByNormalizedName(name: String): String?

    @Query("UPDATE artists SET image_url = :imageUrl WHERE id = :artistId")
    suspend fun updateArtistImageUrl(artistId: Long, imageUrl: String)

    @Query("SELECT id FROM artists WHERE name = :name LIMIT 1")
    suspend fun getArtistIdByName(name: String): Long?

    @Query("SELECT id FROM artists WHERE LOWER(TRIM(name)) = LOWER(TRIM(:name)) LIMIT 1")
    suspend fun getArtistIdByNormalizedName(name: String): Long?

    @Query("SELECT MAX(id) FROM artists")
    suspend fun getMaxArtistId(): Long?

    // --- Artist Custom Image Operations ---
    @Query("UPDATE artists SET custom_image_uri = :uri WHERE id = :artistId")
    suspend fun updateArtistCustomImage(artistId: Long, uri: String?)

    @Query("SELECT custom_image_uri FROM artists WHERE id = :artistId")
    suspend fun getArtistCustomImage(artistId: Long): String?

    // --- Genre Queries ---
    // Example: Get all songs for a specific genre
    @Query("""
        SELECT * FROM songs
        WHERE (:applyDirectoryFilter = 0 OR id < 0 OR parent_directory_path IN (:allowedParentDirs))
        AND genre LIKE :genreName
        ORDER BY title ASC
    """)
    fun getSongsByGenre(
        genreName: String,
        allowedParentDirs: List<String>,
        applyDirectoryFilter: Boolean
    ): Flow<List<SongEntity>>

    @Query("""
        SELECT * FROM songs
        WHERE (:applyDirectoryFilter = 0 OR id < 0 OR parent_directory_path IN (:allowedParentDirs))
        AND (genre IS NULL OR genre = '')
        ORDER BY title ASC
    """)
    fun getSongsWithNullGenre(
        allowedParentDirs: List<String>,
        applyDirectoryFilter: Boolean
    ): Flow<List<SongEntity>>

    // Example: Get all unique genre names
    @Query("SELECT DISTINCT genre FROM songs WHERE genre IS NOT NULL AND genre != '' ORDER BY genre ASC")
    fun getUniqueGenres(): Flow<List<String>>

    @Query("""
        SELECT DISTINCT genre FROM songs
        WHERE genre IS NOT NULL AND genre != ''
        AND (:applyDirectoryFilter = 0 OR id < 0 OR parent_directory_path IN (:allowedParentDirs))
        ORDER BY genre ASC
    """)
    fun getUniqueGenres(
        allowedParentDirs: List<String>,
        applyDirectoryFilter: Boolean
    ): Flow<List<String>>

    @Query("""
        SELECT EXISTS(
            SELECT 1 FROM songs
            WHERE (:applyDirectoryFilter = 0 OR id < 0 OR parent_directory_path IN (:allowedParentDirs))
            AND (genre IS NULL OR genre = '')
        )
    """)
    fun hasUnknownGenre(
        allowedParentDirs: List<String>,
        applyDirectoryFilter: Boolean
    ): Flow<Boolean>

    // --- Combined Queries (Potentially useful for more complex scenarios) ---
    // E.g., Get all album art URIs from songs (could be useful for theme preloading from SSoT)
    @Query("SELECT DISTINCT album_art_uri_string FROM songs WHERE album_art_uri_string IS NOT NULL")
    fun getAllUniqueAlbumArtUrisFromSongs(): Flow<List<String>>

    @Query("DELETE FROM albums WHERE id NOT IN (SELECT DISTINCT album_id FROM songs)")
    suspend fun deleteOrphanedAlbums()

    @Query("DELETE FROM artists WHERE id NOT IN (SELECT DISTINCT artist_id FROM songs)")
    suspend fun deleteOrphanedArtists()

    // --- Favorite Operations ---
    @Query("UPDATE songs SET is_favorite = :isFavorite WHERE id = :songId")
    suspend fun setFavoriteStatus(songId: Long, isFavorite: Boolean)

    @Query("SELECT is_favorite FROM songs WHERE id = :songId")
    suspend fun getFavoriteStatus(songId: Long): Boolean?

    // Transaction to toggle favorite status
    @Transaction
    suspend fun toggleFavoriteStatus(songId: Long): Boolean {
        val currentStatus = getFavoriteStatus(songId) ?: false // Default to false if not found (should not happen for existing song)
        val newStatus = !currentStatus
        setFavoriteStatus(songId, newStatus)
        return newStatus
    }

    @Query("UPDATE songs SET title = :title, artist_name = :artist, album_name = :album, genre = :genre, track_number = :trackNumber WHERE id = :songId")
    suspend fun updateSongMetadata(
        songId: Long,
        title: String,
        artist: String,
        album: String,
        genre: String?,
        trackNumber: Int
    )

    @Query("UPDATE songs SET album_art_uri_string = :albumArtUri WHERE id = :songId")
    suspend fun updateSongAlbumArt(songId: Long, albumArtUri: String?)

    @Query("UPDATE songs SET lyrics = :lyrics WHERE id = :songId")
    suspend fun updateLyrics(songId: Long, lyrics: String)

    @Query("UPDATE songs SET lyrics = NULL WHERE id = :songId")
    suspend fun resetLyrics(songId: Long)

    @Query("UPDATE songs SET lyrics = NULL")
    suspend fun resetAllLyrics()

    @Query("SELECT * FROM songs")
    suspend fun getAllSongsList(): List<SongEntity>

    @Query("""
        SELECT id, title, artist_name, album_name, duration
        FROM songs
        WHERE content_uri_string NOT LIKE 'telegram://%'
        AND content_uri_string NOT LIKE 'netease://%'
        AND content_uri_string NOT LIKE 'gdrive://%'
        AND content_uri_string NOT LIKE 'qqmusic://%'
    """)
    suspend fun getAllLocalSongSummaries(): List<SongSummary>

    @Query("SELECT album_art_uri_string FROM songs WHERE id=:id")
    suspend fun getAlbumArtUriById(id: Long) : String?

    @Query("DELETE FROM songs WHERE id=:id")
    suspend fun deleteById(id: Long)

    @Query("""
    SELECT mime_type AS mimeType,
           bitrate,
           sample_rate AS sampleRate
    FROM songs
    WHERE id = :id
    """)
    suspend fun getAudioMetadataById(id: Long): AudioMeta?

    // ===== Song-Artist Cross Reference (Junction Table) Operations =====

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSongArtistCrossRefs(crossRefs: List<SongArtistCrossRef>)

    @Query("SELECT * FROM song_artist_cross_ref")
    fun getAllSongArtistCrossRefs(): Flow<List<SongArtistCrossRef>>

    @Query("SELECT * FROM song_artist_cross_ref")
    suspend fun getAllSongArtistCrossRefsList(): List<SongArtistCrossRef>

    @Query("DELETE FROM song_artist_cross_ref")
    suspend fun clearAllSongArtistCrossRefs()

    @Query("DELETE FROM song_artist_cross_ref WHERE song_id = :songId")
    suspend fun deleteCrossRefsForSong(songId: Long)

    @Query("DELETE FROM song_artist_cross_ref WHERE artist_id = :artistId")
    suspend fun deleteCrossRefsForArtist(artistId: Long)

    /**
     * Get all artists for a specific song using the junction table.
     */
    @Query("""
        SELECT artists.* FROM artists
        INNER JOIN song_artist_cross_ref ON artists.id = song_artist_cross_ref.artist_id
        WHERE song_artist_cross_ref.song_id = :songId
        ORDER BY song_artist_cross_ref.is_primary DESC, artists.name ASC
    """)
    fun getArtistsForSong(songId: Long): Flow<List<ArtistEntity>>

    /**
     * Get all artists for a specific song (one-shot).
     */
    @Query("""
        SELECT artists.* FROM artists
        INNER JOIN song_artist_cross_ref ON artists.id = song_artist_cross_ref.artist_id
        WHERE song_artist_cross_ref.song_id = :songId
        ORDER BY song_artist_cross_ref.is_primary DESC, artists.name ASC
    """)
    suspend fun getArtistsForSongList(songId: Long): List<ArtistEntity>

    /**
     * Get all songs for a specific artist using the junction table.
     */
    @Query("""
        SELECT songs.* FROM songs
        INNER JOIN song_artist_cross_ref ON songs.id = song_artist_cross_ref.song_id
        WHERE song_artist_cross_ref.artist_id = :artistId
        ORDER BY songs.title ASC
    """)
    fun getSongsForArtist(artistId: Long): Flow<List<SongEntity>>

    /**
     * Get all songs for a specific artist (one-shot).
     */
    @Query("""
        SELECT songs.* FROM songs
        INNER JOIN song_artist_cross_ref ON songs.id = song_artist_cross_ref.song_id
        WHERE song_artist_cross_ref.artist_id = :artistId
        ORDER BY songs.title ASC
    """)
    suspend fun getSongsForArtistList(artistId: Long): List<SongEntity>

    /**
     * Get the cross-references for a specific song.
     */
    @Query("SELECT * FROM song_artist_cross_ref WHERE song_id = :songId")
    suspend fun getCrossRefsForSong(songId: Long): List<SongArtistCrossRef>

    /**
     * Get the primary artist for a song.
     */
    @Query("""
        SELECT artists.id AS artist_id, artists.name FROM artists
        INNER JOIN song_artist_cross_ref ON artists.id = song_artist_cross_ref.artist_id
        WHERE song_artist_cross_ref.song_id = :songId AND song_artist_cross_ref.is_primary = 1
        LIMIT 1
    """)
    suspend fun getPrimaryArtistForSong(songId: Long): PrimaryArtistInfo?

    /**
     * Get song count for an artist from the junction table.
     */
    @Query("SELECT COUNT(*) FROM song_artist_cross_ref WHERE artist_id = :artistId")
    suspend fun getSongCountForArtist(artistId: Long): Int

    /**
     * Get all artists with their song counts computed from the junction table.
     */
    @Query("""
        SELECT artists.id, artists.name, artists.image_url, artists.custom_image_uri,
               COUNT(DISTINCT song_artist_cross_ref.song_id) AS track_count
        FROM artists
        LEFT JOIN song_artist_cross_ref ON artists.id = song_artist_cross_ref.artist_id
        GROUP BY artists.id
        ORDER BY artists.name ASC
    """)
    fun getArtistsWithSongCounts(): Flow<List<ArtistEntity>>

    /**
     * Get all artists with song counts, filtered by allowed directories.
     */
    @Query("""
        SELECT artists.id, artists.name, artists.image_url, artists.custom_image_uri,
               COUNT(DISTINCT songs.id) AS track_count
        FROM artists
        INNER JOIN song_artist_cross_ref ON artists.id = song_artist_cross_ref.artist_id
        INNER JOIN songs ON song_artist_cross_ref.song_id = songs.id
        WHERE (:applyDirectoryFilter = 0 OR songs.id < 0 OR songs.parent_directory_path IN (:allowedParentDirs))
        AND (
            :filterMode = 0
            OR (
                :filterMode = 1
                AND songs.content_uri_string NOT LIKE 'telegram://%'
                AND songs.content_uri_string NOT LIKE 'netease://%'
                AND songs.content_uri_string NOT LIKE 'gdrive://%'
                AND songs.content_uri_string NOT LIKE 'qqmusic://%'
                AND songs.content_uri_string NOT LIKE 'navidrome://%'
            )
            OR (
                :filterMode = 2
                AND (
                    songs.content_uri_string LIKE 'telegram://%'
                    OR songs.content_uri_string LIKE 'netease://%'
                    OR songs.content_uri_string LIKE 'gdrive://%'
                    OR songs.content_uri_string LIKE 'qqmusic://%'
                    OR songs.content_uri_string LIKE 'navidrome://%'
                )
            )
        )
        GROUP BY artists.id
        ORDER BY artists.name ASC
    """)
    fun getArtistsWithSongCountsFiltered(
        allowedParentDirs: List<String>,
        applyDirectoryFilter: Boolean,
        filterMode: Int
    ): Flow<List<ArtistEntity>>

    /**
     * Clear all music data including cross-references.
     */
    @Transaction
    suspend fun clearAllMusicDataWithCrossRefs() {
        clearAllSongArtistCrossRefs()
        clearAllSongs()
        clearAllAlbums()
        clearAllArtists()
    }

    /**
     * Insert music data with cross-references in a single transaction.
     * Uses chunked inserts for cross-refs to avoid SQLite variable limits.
     */
    @Transaction
    suspend fun insertMusicDataWithCrossRefs(
        songs: List<SongEntity>,
        albums: List<AlbumEntity>,
        artists: List<ArtistEntity>,
        crossRefs: List<SongArtistCrossRef>
    ) {
        insertArtists(artists)
        insertAlbums(albums)
        insertSongs(songs)
        // Insert cross-refs in chunks to avoid SQLite variable limit.
        // Each SongArtistCrossRef has 3 fields, so batch size is calculated accordingly.
        crossRefs.chunked(CROSS_REF_BATCH_SIZE).forEach { chunk ->
            insertSongArtistCrossRefs(chunk)
        }
    }

    @Transaction
    suspend fun rebuildMusicDataWithCrossRefs(
        songs: List<SongEntity>,
        albums: List<AlbumEntity>,
        artists: List<ArtistEntity>,
        crossRefs: List<SongArtistCrossRef>
    ) {
        // Save current cloud songs before clearing to prevent accidental data loss
        // Only clear if we have new songs to insert, or we are explicitly asked to REBUILD everything.
        // We handle this logic at the worker/repository level to be more precise.
        
        clearAllSongArtistCrossRefs()
        clearAllSongs()
        clearAllAlbums()
        clearAllArtists()

        insertArtists(artists)
        insertAlbums(albums)
        insertSongs(songs)
        crossRefs.chunked(CROSS_REF_BATCH_SIZE).forEach { chunk ->
            insertSongArtistCrossRefs(chunk)
        }
    }

    companion object {
        /**
         * SQLite has a limit on the number of variables per statement (default 999, higher in newer versions).
         * Each SongArtistCrossRef insert uses 3 variables (songId, artistId, isPrimary).
         * The batch size is calculated so that batchSize * 3 <= SQLITE_MAX_VARIABLE_NUMBER.
         */
        private const val SQLITE_MAX_VARIABLE_NUMBER = 999 // Increase if you know your SQLite version supports more
        private const val CROSS_REF_FIELDS_PER_OBJECT = 3
        val CROSS_REF_BATCH_SIZE: Int = SQLITE_MAX_VARIABLE_NUMBER / CROSS_REF_FIELDS_PER_OBJECT

        /**
         * Batch size for song inserts during incremental sync.
         * Allows database reads to interleave with writes for better UX.
         */
        const val SONG_BATCH_SIZE = 500
    }
}
