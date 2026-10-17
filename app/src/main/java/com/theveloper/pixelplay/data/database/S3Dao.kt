package com.theveloper.pixelplay.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface S3Dao {

    // --- Songs ---

    @Query("SELECT * FROM s3_songs ORDER BY date_added DESC")
    fun getAllS3Songs(): Flow<List<S3SongEntity>>

    @Query("SELECT * FROM s3_songs ORDER BY date_added DESC")
    suspend fun getAllS3SongsList(): List<S3SongEntity>

    @Query("SELECT * FROM s3_songs WHERE folder_id = :folderId ORDER BY title ASC")
    fun getSongsByFolder(folderId: String): Flow<List<S3SongEntity>>

    @Query("SELECT * FROM s3_songs WHERE metadata_probed = 0 LIMIT :limit")
    suspend fun getUnprobedBatch(limit: Int = 8): List<S3SongEntity>

    @Query("SELECT COUNT(*) FROM s3_songs WHERE metadata_probed = 0")
    fun countUnprobedFlow(): Flow<Int>

    @Query(
        """UPDATE s3_songs SET title = :title, artist = :artist, album = :album,
        duration = :duration, album_art_uri = :albumArtUri, metadata_probed = 1 WHERE id = :id"""
    )
    suspend fun applyProbeResult(
        id: String, title: String, artist: String, album: String,
        duration: Long, albumArtUri: String?
    )

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSongs(songs: List<S3SongEntity>)

    @Query("DELETE FROM s3_songs WHERE id IN (:ids)")
    suspend fun deleteSongsByIds(ids: List<String>)

    @Query("DELETE FROM s3_songs WHERE folder_id = :folderId")
    suspend fun deleteSongsByFolder(folderId: String)

    // --- Folders ---

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFolder(folder: S3FolderEntity)

    @Query("SELECT * FROM s3_folders ORDER BY name ASC")
    fun getAllFolders(): Flow<List<S3FolderEntity>>

    @Query("SELECT * FROM s3_folders")
    suspend fun getAllFoldersList(): List<S3FolderEntity>

    @Query("DELETE FROM s3_folders WHERE id = :folderId")
    suspend fun deleteFolder(folderId: String)

    // --- Clear All ---

    @Query("DELETE FROM s3_songs")
    suspend fun clearAllSongs()

    @Query("DELETE FROM s3_folders")
    suspend fun clearAllFolders()
}
