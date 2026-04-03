package com.theveloper.pixelplay.data.model

import androidx.compose.runtime.Immutable

@Immutable
enum class SortDirection {
    Ascending,
    Descending
}

// Sealed class for Sort Options
@Immutable
sealed class SortOption(
    val storageKey: String,
    val displayName: String,
    val methodLabel: String = displayName,
    val methodKey: String = storageKey,
    val direction: SortDirection? = null
) {
    // Song Sort Options
    object SongDefaultOrder : SortOption("song_default_order", "Default Order")
    object SongTitleAZ : SortOption(
        storageKey = "song_title_az",
        displayName = "Title (A-Z)",
        methodLabel = "Title",
        methodKey = "song_title",
        direction = SortDirection.Ascending
    )
    object SongTitleZA : SortOption(
        storageKey = "song_title_za",
        displayName = "Title (Z-A)",
        methodLabel = "Title",
        methodKey = "song_title",
        direction = SortDirection.Descending
    )
    object SongArtist : SortOption(
        storageKey = "song_artist",
        displayName = "Artist",
        methodLabel = "Artist",
        methodKey = "song_artist",
        direction = SortDirection.Ascending
    )
    object SongArtistDesc : SortOption(
        storageKey = "song_artist_desc",
        displayName = "Artist (Z-A)",
        methodLabel = "Artist",
        methodKey = "song_artist",
        direction = SortDirection.Descending
    )
    object SongAlbum : SortOption(
        storageKey = "song_album",
        displayName = "Album",
        methodLabel = "Album",
        methodKey = "song_album",
        direction = SortDirection.Ascending
    )
    object SongAlbumDesc : SortOption(
        storageKey = "song_album_desc",
        displayName = "Album (Z-A)",
        methodLabel = "Album",
        methodKey = "song_album",
        direction = SortDirection.Descending
    )
    object SongDateAdded : SortOption(
        storageKey = "song_date_added",
        displayName = "Date Added",
        methodLabel = "Date Added",
        methodKey = "song_date_added",
        direction = SortDirection.Descending
    )
    object SongDateAddedAsc : SortOption(
        storageKey = "song_date_added_asc",
        displayName = "Date Added (Oldest First)",
        methodLabel = "Date Added",
        methodKey = "song_date_added",
        direction = SortDirection.Ascending
    )
    object SongDuration : SortOption(
        storageKey = "song_duration",
        displayName = "Duration",
        methodLabel = "Duration",
        methodKey = "song_duration",
        direction = SortDirection.Descending
    )
    object SongDurationAsc : SortOption(
        storageKey = "song_duration_asc",
        displayName = "Duration (Shortest First)",
        methodLabel = "Duration",
        methodKey = "song_duration",
        direction = SortDirection.Ascending
    )

    // Album Sort Options
    object AlbumTitleAZ : SortOption(
        storageKey = "album_title_az",
        displayName = "Title (A-Z)",
        methodLabel = "Title",
        methodKey = "album_title",
        direction = SortDirection.Ascending
    )
    object AlbumTitleZA : SortOption(
        storageKey = "album_title_za",
        displayName = "Title (Z-A)",
        methodLabel = "Title",
        methodKey = "album_title",
        direction = SortDirection.Descending
    )
    object AlbumArtist : SortOption(
        storageKey = "album_artist",
        displayName = "Artist",
        methodLabel = "Artist",
        methodKey = "album_artist",
        direction = SortDirection.Ascending
    )
    object AlbumArtistDesc : SortOption(
        storageKey = "album_artist_desc",
        displayName = "Artist (Z-A)",
        methodLabel = "Artist",
        methodKey = "album_artist",
        direction = SortDirection.Descending
    )
    object AlbumReleaseYear : SortOption(
        storageKey = "album_release_year",
        displayName = "Release Year",
        methodLabel = "Release Year",
        methodKey = "album_release_year",
        direction = SortDirection.Descending
    )
    object AlbumReleaseYearAsc : SortOption(
        storageKey = "album_release_year_asc",
        displayName = "Release Year (Oldest First)",
        methodLabel = "Release Year",
        methodKey = "album_release_year",
        direction = SortDirection.Ascending
    )
    object AlbumDateAdded : SortOption(
        storageKey = "album_date_added",
        displayName = "Date Added",
        methodLabel = "Date Added",
        methodKey = "album_date_added",
        direction = SortDirection.Descending
    )
    object AlbumSizeAsc : SortOption(
        storageKey = "album_size_asc",
        displayName = "Fewest Songs",
        methodLabel = "Song Count",
        methodKey = "album_size",
        direction = SortDirection.Ascending
    )
    object AlbumSizeDesc : SortOption(
        storageKey = "album_size_desc",
        displayName = "Most Songs",
        methodLabel = "Song Count",
        methodKey = "album_size",
        direction = SortDirection.Descending
    )

    // Artist Sort Options
    object ArtistNameAZ : SortOption(
        storageKey = "artist_name_az",
        displayName = "Name (A-Z)",
        methodLabel = "Name",
        methodKey = "artist_name",
        direction = SortDirection.Ascending
    )
    object ArtistNameZA : SortOption(
        storageKey = "artist_name_za",
        displayName = "Name (Z-A)",
        methodLabel = "Name",
        methodKey = "artist_name",
        direction = SortDirection.Descending
    )
    object ArtistNumSongs : SortOption(
        storageKey = "artist_num_songs",
        displayName = "Number of Songs",
        methodLabel = "Number of Songs",
        methodKey = "artist_num_songs",
        direction = SortDirection.Descending
    )

    // Playlist Sort Options
    object PlaylistNameAZ : SortOption(
        storageKey = "playlist_name_az",
        displayName = "Name (A-Z)",
        methodLabel = "Name",
        methodKey = "playlist_name",
        direction = SortDirection.Ascending
    )
    object PlaylistNameZA : SortOption(
        storageKey = "playlist_name_za",
        displayName = "Name (Z-A)",
        methodLabel = "Name",
        methodKey = "playlist_name",
        direction = SortDirection.Descending
    )
    object PlaylistDateCreated : SortOption(
        storageKey = "playlist_date_created",
        displayName = "Date Created",
        methodLabel = "Date Created",
        methodKey = "playlist_date_created",
        direction = SortDirection.Descending
    )
    object PlaylistDateCreatedAsc : SortOption(
        storageKey = "playlist_date_created_asc",
        displayName = "Date Created (Oldest First)",
        methodLabel = "Date Created",
        methodKey = "playlist_date_created",
        direction = SortDirection.Ascending
    )

    // Liked Sort Options (similar to Songs)
    object LikedSongTitleAZ : SortOption(
        storageKey = "liked_title_az",
        displayName = "Title (A-Z)",
        methodLabel = "Title",
        methodKey = "liked_title",
        direction = SortDirection.Ascending
    )
    object LikedSongTitleZA : SortOption(
        storageKey = "liked_title_za",
        displayName = "Title (Z-A)",
        methodLabel = "Title",
        methodKey = "liked_title",
        direction = SortDirection.Descending
    )
    object LikedSongArtist : SortOption(
        storageKey = "liked_artist",
        displayName = "Artist",
        methodLabel = "Artist",
        methodKey = "liked_artist",
        direction = SortDirection.Ascending
    )
    object LikedSongArtistDesc : SortOption(
        storageKey = "liked_artist_desc",
        displayName = "Artist (Z-A)",
        methodLabel = "Artist",
        methodKey = "liked_artist",
        direction = SortDirection.Descending
    )
    object LikedSongAlbum : SortOption(
        storageKey = "liked_album",
        displayName = "Album",
        methodLabel = "Album",
        methodKey = "liked_album",
        direction = SortDirection.Ascending
    )
    object LikedSongAlbumDesc : SortOption(
        storageKey = "liked_album_desc",
        displayName = "Album (Z-A)",
        methodLabel = "Album",
        methodKey = "liked_album",
        direction = SortDirection.Descending
    )
    object LikedSongDateLiked : SortOption(
        storageKey = "liked_date_liked",
        displayName = "Date Liked",
        methodLabel = "Date Liked",
        methodKey = "liked_date_liked",
        direction = SortDirection.Descending
    )
    object LikedSongDateLikedAsc : SortOption(
        storageKey = "liked_date_liked_asc",
        displayName = "Date Liked (Oldest First)",
        methodLabel = "Date Liked",
        methodKey = "liked_date_liked",
        direction = SortDirection.Ascending
    )

    // Folder Sort Options
    object FolderNameAZ : SortOption(
        storageKey = "folder_name_az",
        displayName = "Name (A-Z)",
        methodLabel = "Name",
        methodKey = "folder_name",
        direction = SortDirection.Ascending
    )
    object FolderNameZA : SortOption(
        storageKey = "folder_name_za",
        displayName = "Name (Z-A)",
        methodLabel = "Name",
        methodKey = "folder_name",
        direction = SortDirection.Descending
    )
    object FolderSongCountAsc : SortOption(
        storageKey = "folder_song_count_asc",
        displayName = "Fewest Songs",
        methodLabel = "Song Count",
        methodKey = "folder_song_count",
        direction = SortDirection.Ascending
    )
    object FolderSongCountDesc : SortOption(
        storageKey = "folder_song_count_desc",
        displayName = "Most Songs",
        methodLabel = "Song Count",
        methodKey = "folder_song_count",
        direction = SortDirection.Descending
    )
    object FolderSubdirCountAsc : SortOption(
        storageKey = "folder_subdir_count_asc",
        displayName = "Fewest Subfolders",
        methodLabel = "Subfolder Count",
        methodKey = "folder_subdir_count",
        direction = SortDirection.Ascending
    )
    object FolderSubdirCountDesc : SortOption(
        storageKey = "folder_subdir_count_desc",
        displayName = "Most Subfolders",
        methodLabel = "Subfolder Count",
        methodKey = "folder_subdir_count",
        direction = SortDirection.Descending
    )

    val canFlipDirection: Boolean
        get() = direction != null && flipDirection().storageKey != storageKey

    fun methodOption(): SortOption = defaultOptionByMethodKey[methodKey] ?: this

    fun resolveForDirection(targetDirection: SortDirection?): SortOption {
        if (targetDirection == null) {
            return methodOption()
        }
        return optionByMethodAndDirection[methodKey to targetDirection] ?: methodOption()
    }

    fun flipDirection(): SortOption {
        val currentDirection = direction ?: return this
        val targetDirection = when (currentDirection) {
            SortDirection.Ascending -> SortDirection.Descending
            SortDirection.Descending -> SortDirection.Ascending
        }
        return resolveForDirection(targetDirection)
    }

    companion object {

        val SONGS: List<SortOption> by lazy {
            listOf(
                SongDefaultOrder,
                SongTitleAZ,
                SongTitleZA,
                SongArtist,
                SongArtistDesc,
                SongAlbum,
                SongAlbumDesc,
                SongDateAdded,
                SongDateAddedAsc,
                SongDuration,
                SongDurationAsc
            )
        }

        val ALBUMS: List<SortOption> by lazy {
            listOf(
                AlbumTitleAZ,
                AlbumTitleZA,
                AlbumArtist,
                AlbumArtistDesc,
                AlbumReleaseYear,
                AlbumReleaseYearAsc,
                AlbumDateAdded,
                AlbumSizeAsc,
                AlbumSizeDesc
            )
        }

        val ARTISTS: List<SortOption> by lazy {
            listOf(
                ArtistNameAZ,
                ArtistNameZA,
                ArtistNumSongs
            )
        }

        val PLAYLISTS: List<SortOption> by lazy {
            listOf(
                PlaylistNameAZ,
                PlaylistNameZA,
                PlaylistDateCreated,
                PlaylistDateCreatedAsc
            )
        }

        val FOLDERS: List<SortOption> by lazy {
            listOf(
                FolderNameAZ,
                FolderNameZA,
                FolderSongCountAsc,
                FolderSongCountDesc,
                FolderSubdirCountAsc,
                FolderSubdirCountDesc
            )
        }

        val LIKED: List<SortOption> by lazy {
            listOf(
                LikedSongTitleAZ,
                LikedSongTitleZA,
                LikedSongArtist,
                LikedSongArtistDesc,
                LikedSongAlbum,
                LikedSongAlbumDesc,
                LikedSongDateLiked,
                LikedSongDateLikedAsc
            )
        }

        private val ALL: List<SortOption> by lazy {
            SONGS + ALBUMS + ARTISTS + PLAYLISTS + FOLDERS + LIKED
        }

        private val defaultOptionByMethodKey: Map<String, SortOption> by lazy {
            ALL.groupBy { it.methodKey }
                .mapValues { (_, options) -> options.first() }
        }

        private val optionByMethodAndDirection: Map<Pair<String, SortDirection>, SortOption> by lazy {
            ALL.asSequence()
                .filter { it.direction != null }
                .associateBy { it.methodKey to requireNotNull(it.direction) }
        }

        fun fromStorageKey(
            rawValue: String?,
            allowed: Collection<SortOption>,
            fallback: SortOption
        ): SortOption {
            if (rawValue.isNullOrBlank()) {
                return fallback
            }

            val sanitized = allowed.filterIsInstance<SortOption>()
            if (sanitized.isEmpty()) {
                return fallback
            }

            sanitized.firstOrNull { option -> option.storageKey == rawValue }?.let { matched ->
                return matched
            }

            // Legacy values used display names; fall back to matching within the allowed group.
            return sanitized.firstOrNull { option -> option.displayName == rawValue } ?: fallback
        }
    }
}