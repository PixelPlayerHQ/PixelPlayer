package com.theveloper.pixelplay.data.service.auto

import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.theveloper.pixelplay.data.database.EngagementDao
import com.theveloper.pixelplay.data.model.Album
import com.theveloper.pixelplay.data.model.Artist
import com.theveloper.pixelplay.data.model.Playlist
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.preferences.PlaylistPreferencesRepository
import com.theveloper.pixelplay.data.repository.MusicRepository
import com.theveloper.pixelplay.utils.MediaItemBuilder
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AutoMediaBrowseTree @Inject constructor(
    @ApplicationContext private val context: Context,
    private val musicRepository: MusicRepository,
    private val playlistPreferencesRepository: PlaylistPreferencesRepository,
    private val engagementDao: EngagementDao
) {

    companion object {
        const val ROOT_ID = "ROOT"
        const val RECENT_ID = "RECENT"
        const val FAVORITES_ID = "FAVORITES"
        const val PLAYLISTS_ID = "PLAYLISTS"
        const val ALBUMS_ID = "ALBUMS"
        const val ARTISTS_ID = "ARTISTS"
        const val SONGS_ID = "SONGS"

        const val ALBUM_PREFIX = "ALBUM_"
        const val ARTIST_PREFIX = "ARTIST_"
        const val PLAYLIST_PREFIX = "PLAYLIST_"

        const val CONTEXT_TYPE_EXTRA = "com.theveloper.pixelplay.auto.extra.CONTEXT_TYPE"
        const val CONTEXT_ID_EXTRA = "com.theveloper.pixelplay.auto.extra.CONTEXT_ID"
        const val CONTEXT_PARENT_ID_EXTRA = "com.theveloper.pixelplay.auto.extra.CONTEXT_PARENT_ID"

        private const val CONTEXT_TYPE_RECENT = "recent"
        private const val CONTEXT_TYPE_FAVORITES = "favorites"
        private const val CONTEXT_TYPE_ALL_SONGS = "all_songs"
        private const val CONTEXT_TYPE_ALBUM = "album"
        private const val CONTEXT_TYPE_ARTIST = "artist"
        private const val CONTEXT_TYPE_PLAYLIST = "playlist"

        private const val MAX_RECENT_SONGS = 50
        private const val MAX_SEARCH_RESULTS = 30
    }

    fun getRootItems(): List<MediaItem> {
        return listOf(
            buildBrowsableItem(RECENT_ID, "Recently Played", null, MediaMetadata.MEDIA_TYPE_FOLDER_MIXED),
            buildBrowsableItem(FAVORITES_ID, "Favorites", null, MediaMetadata.MEDIA_TYPE_FOLDER_MIXED),
            buildBrowsableItem(PLAYLISTS_ID, "Playlists", null, MediaMetadata.MEDIA_TYPE_FOLDER_PLAYLISTS),
            buildBrowsableItem(ALBUMS_ID, "Albums", null, MediaMetadata.MEDIA_TYPE_FOLDER_ALBUMS),
            buildBrowsableItem(ARTISTS_ID, "Artists", null, MediaMetadata.MEDIA_TYPE_FOLDER_ARTISTS),
            buildBrowsableItem(SONGS_ID, "All Songs", null, MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
        )
    }

    suspend fun getChildren(parentId: String, page: Int, pageSize: Int): List<MediaItem> {
        val effectivePage = page.coerceAtLeast(0)
        val effectivePageSize = if (pageSize > 0) pageSize else Int.MAX_VALUE
        val offset = (effectivePage.toLong() * effectivePageSize.toLong())
            .coerceAtMost(Int.MAX_VALUE.toLong())
            .toInt()

        return when (parentId) {
            ROOT_ID -> getRootItems()
            RECENT_ID -> getRecentSongs(offset, effectivePageSize)
            FAVORITES_ID -> getFavoriteSongs(offset, effectivePageSize)
            PLAYLISTS_ID -> getPlaylists(offset, effectivePageSize)
            ALBUMS_ID -> getAlbums(offset, effectivePageSize)
            ARTISTS_ID -> getArtists(offset, effectivePageSize)
            SONGS_ID -> getAllSongs(offset, effectivePageSize)
            else -> getChildrenForPrefix(parentId, offset, effectivePageSize)
        }
    }

    suspend fun getItem(mediaId: String): MediaItem? {
        return when {
            mediaId == ROOT_ID -> buildBrowsableItem(ROOT_ID, "PixelPlay", null, MediaMetadata.MEDIA_TYPE_MUSIC)
            mediaId == RECENT_ID || mediaId == FAVORITES_ID || mediaId == PLAYLISTS_ID ||
                    mediaId == ALBUMS_ID || mediaId == ARTISTS_ID || mediaId == SONGS_ID -> {
                getRootItems().find { it.mediaId == mediaId }
            }
            mediaId.startsWith(ALBUM_PREFIX) -> {
                val albumId = mediaId.removePrefix(ALBUM_PREFIX).toLongOrNull() ?: return null
                val album = musicRepository.getAllAlbumsOnce().find { it.id == albumId } ?: return null
                buildBrowsableAlbumItem(album)
            }
            mediaId.startsWith(ARTIST_PREFIX) -> {
                val artistId = mediaId.removePrefix(ARTIST_PREFIX).toLongOrNull() ?: return null
                val artist = musicRepository.getAllArtistsOnce().find { it.id == artistId } ?: return null
                buildBrowsableArtistItem(artist)
            }
            mediaId.startsWith(PLAYLIST_PREFIX) -> {
                val playlistId = mediaId.removePrefix(PLAYLIST_PREFIX)
                val playlist = playlistPreferencesRepository.userPlaylistsFlow.first()
                    .find { it.id == playlistId } ?: return null
                buildBrowsablePlaylistItem(playlist)
            }
            else -> {
                // Song item
                val song = musicRepository.getSong(mediaId).first() ?: return null
                buildPlayableSongItem(song)
            }
        }
    }

    suspend fun search(query: String): List<MediaItem> {
        if (query.isBlank()) return emptyList()

        val results = mutableListOf<MediaItem>()
        val trimmedQuery = query.trim()

        // Search songs
        val songs = musicRepository.searchSongs(trimmedQuery).first()
        results.addAll(songs.take(MAX_SEARCH_RESULTS).map { buildPlayableSongItem(it) })

        // Search albums
        val albums = musicRepository.searchAlbums(trimmedQuery).first()
        results.addAll(albums.take(10).map { buildBrowsableAlbumItem(it) })

        // Search artists
        val artists = musicRepository.searchArtists(trimmedQuery).first()
        results.addAll(artists.take(10).map { buildBrowsableArtistItem(it) })

        return results.take(MAX_SEARCH_RESULTS)
    }

    // --- Private helpers ---

    private suspend fun getRecentSongs(offset: Int, limit: Int): List<MediaItem> {
        return getRecentSongList()
            .drop(offset)
            .take(limit)
            .map { buildPlayableSongItem(it, CONTEXT_TYPE_RECENT, null, RECENT_ID) }
    }

    private suspend fun getFavoriteSongs(offset: Int, limit: Int): List<MediaItem> {
        val songs = musicRepository.getFavoriteSongsOnce()
        return songs
            .drop(offset)
            .take(limit)
            .map { buildPlayableSongItem(it, CONTEXT_TYPE_FAVORITES, null, FAVORITES_ID) }
    }

    private suspend fun getPlaylists(offset: Int, limit: Int): List<MediaItem> {
        val playlists = playlistPreferencesRepository.userPlaylistsFlow.first()
        return playlists
            .drop(offset)
            .take(limit)
            .map { buildBrowsablePlaylistItem(it) }
    }

    private suspend fun getAlbums(offset: Int, limit: Int): List<MediaItem> {
        val albums = musicRepository.getAllAlbumsOnce()
        return albums
            .drop(offset)
            .take(limit)
            .map { buildBrowsableAlbumItem(it) }
    }

    private suspend fun getArtists(offset: Int, limit: Int): List<MediaItem> {
        val artists = musicRepository.getAllArtistsOnce()
        return artists
            .drop(offset)
            .take(limit)
            .map { buildBrowsableArtistItem(it) }
    }

    private suspend fun getAllSongs(offset: Int, limit: Int): List<MediaItem> {
        val songs = musicRepository.getAllSongsOnce()
        return songs
            .drop(offset)
            .take(limit)
            .map { buildPlayableSongItem(it, CONTEXT_TYPE_ALL_SONGS, null, SONGS_ID) }
    }

    private suspend fun getChildrenForPrefix(parentId: String, offset: Int, limit: Int): List<MediaItem> {
        val context = resolveContextFromParent(parentId) ?: return emptyList()
        val songs = getSongsForContext(context.first, context.second)
        return songs.drop(offset)
            .take(limit)
            .map { song ->
                buildPlayableSongItem(
                    song = song,
                    contextType = context.first,
                    contextId = context.second,
                    parentId = parentId
                )
            }
    }

    suspend fun getSongsForContext(contextType: String, contextId: String?): List<Song> {
        return when (contextType) {
            CONTEXT_TYPE_RECENT -> getRecentSongList()
            CONTEXT_TYPE_FAVORITES -> musicRepository.getFavoriteSongsOnce()
            CONTEXT_TYPE_ALL_SONGS -> musicRepository.getAllSongsOnce()
            CONTEXT_TYPE_ALBUM -> {
                val albumId = contextId?.toLongOrNull() ?: return emptyList()
                musicRepository.getSongsForAlbum(albumId).first()
            }
            CONTEXT_TYPE_ARTIST -> {
                val artistId = contextId?.toLongOrNull() ?: return emptyList()
                musicRepository.getSongsForArtist(artistId).first()
            }
            CONTEXT_TYPE_PLAYLIST -> {
                val playlistId = contextId ?: return emptyList()
                val playlist = playlistPreferencesRepository.userPlaylistsFlow.first()
                    .find { it.id == playlistId } ?: return emptyList()
                val songs = musicRepository.getSongsByIds(playlist.songIds).first()
                val songsById = songs.associateBy { it.id }
                playlist.songIds.mapNotNull { id -> songsById[id] }
            }
            else -> emptyList()
        }
    }

    private fun resolveContextFromParent(parentId: String): Pair<String, String?>? {
        return when {
            parentId.startsWith(ALBUM_PREFIX) -> {
                CONTEXT_TYPE_ALBUM to parentId.removePrefix(ALBUM_PREFIX)
            }
            parentId.startsWith(ARTIST_PREFIX) -> {
                CONTEXT_TYPE_ARTIST to parentId.removePrefix(ARTIST_PREFIX)
            }
            parentId.startsWith(PLAYLIST_PREFIX) -> {
                CONTEXT_TYPE_PLAYLIST to parentId.removePrefix(PLAYLIST_PREFIX)
            }
            else -> null
        }
    }

    private suspend fun getRecentSongList(): List<Song> {
        val engagements = engagementDao.getRecentlyPlayedSongs(MAX_RECENT_SONGS)
        if (engagements.isEmpty()) return emptyList()

        val songIds = engagements.map { it.songId }
        val songs = musicRepository.getSongsByIds(songIds).first()
        val songsById = songs.associateBy { it.id }
        return songIds.mapNotNull { id -> songsById[id] }
    }

    // --- MediaItem builders ---

    private fun buildBrowsableItem(
        mediaId: String,
        title: String,
        artworkUri: Uri?,
        mediaType: Int
    ): MediaItem {
        val metadata = MediaMetadata.Builder()
            .setTitle(title)
            .setIsBrowsable(true)
            .setIsPlayable(false)
            .setMediaType(mediaType)
        artworkUri?.let { metadata.setArtworkUri(it) }

        return MediaItem.Builder()
            .setMediaId(mediaId)
            .setMediaMetadata(metadata.build())
            .build()
    }

    private fun buildPlayableSongItem(
        song: Song,
        contextType: String? = null,
        contextId: String? = null,
        parentId: String? = null
    ): MediaItem {
        val contextExtras = Bundle().apply {
            contextType?.let { putString(CONTEXT_TYPE_EXTRA, it) }
            contextId?.let { putString(CONTEXT_ID_EXTRA, it) }
            parentId?.let { putString(CONTEXT_PARENT_ID_EXTRA, it) }
        }
        val metadata = MediaMetadata.Builder()
            .setTitle(song.title)
            .setArtist(song.displayArtist)
            .setAlbumTitle(song.album)
            .setIsBrowsable(false)
            .setIsPlayable(true)
            .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
        if (!contextExtras.isEmpty) {
            metadata.setExtras(contextExtras)
        }
        MediaItemBuilder.externalControllerArtworkUri(context, song.albumArtUriString)
            ?.let { metadata.setArtworkUri(it) }

        return MediaItem.Builder()
            .setMediaId(song.id)
            .setMediaMetadata(metadata.build())
            .build()
    }

    private fun buildBrowsableAlbumItem(album: Album): MediaItem {
        val metadata = MediaMetadata.Builder()
            .setTitle(album.title)
            .setArtist(album.artist)
            .setIsBrowsable(true)
            .setIsPlayable(false)
            .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_ALBUMS)
        MediaItemBuilder.externalControllerArtworkUri(context, album.albumArtUriString)
            ?.let { metadata.setArtworkUri(it) }

        return MediaItem.Builder()
            .setMediaId(ALBUM_PREFIX + album.id)
            .setMediaMetadata(metadata.build())
            .build()
    }

    private fun buildBrowsableArtistItem(artist: Artist): MediaItem {
        val metadata = MediaMetadata.Builder()
            .setTitle(artist.name)
            .setIsBrowsable(true)
            .setIsPlayable(false)
            .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_ARTISTS)
        MediaItemBuilder.externalControllerArtworkUri(context, artist.effectiveImageUrl)
            ?.let { metadata.setArtworkUri(it) }

        return MediaItem.Builder()
            .setMediaId(ARTIST_PREFIX + artist.id)
            .setMediaMetadata(metadata.build())
            .build()
    }

    private fun buildBrowsablePlaylistItem(playlist: Playlist): MediaItem {
        val metadata = MediaMetadata.Builder()
            .setTitle(playlist.name)
            .setIsBrowsable(true)
            .setIsPlayable(false)
            .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_PLAYLISTS)
        MediaItemBuilder.externalControllerArtworkUri(context, playlist.coverImageUri)
            ?.let { metadata.setArtworkUri(it) }

        return MediaItem.Builder()
            .setMediaId(PLAYLIST_PREFIX + playlist.id)
            .setMediaMetadata(metadata.build())
            .build()
    }
}
