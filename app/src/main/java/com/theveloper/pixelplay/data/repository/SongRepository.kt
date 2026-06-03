package com.theveloper.pixelplay.data.repository

import androidx.paging.PagingData
import com.theveloper.pixelplay.data.model.Song
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow

interface SongRepository {
    fun getSongs(): Flow<List<Song>>
    fun getSongsByAlbum(albumId: Long): Flow<List<Song>>
    fun getSongsByArtist(artistId: Long): Flow<List<Song>>
    suspend fun searchSongs(query: String): List<Song>
    fun getSongById(songId: Long): Flow<Song?>
    fun getPaginatedSongs(sortOption: com.theveloper.pixelplay.data.model.SortOption, storageFilter: com.theveloper.pixelplay.data.model.SourceScope): Flow<PagingData<Song>>
    @OptIn(ExperimentalCoroutinesApi::class)
    fun getPaginatedSongs(): Flow<PagingData<Song>>
    fun getPaginatedFavoriteSongs(
        sortOption: com.theveloper.pixelplay.data.model.SortOption,
        storageFilter: com.theveloper.pixelplay.data.model.SourceScope
    ): Flow<PagingData<Song>>
    suspend fun getFavoriteSongsOnce(
        storageFilter: com.theveloper.pixelplay.data.model.SourceScope
    ): List<Song>
    fun getFavoriteSongCountFlow(
        storageFilter: com.theveloper.pixelplay.data.model.SourceScope
    ): Flow<Int>
}
