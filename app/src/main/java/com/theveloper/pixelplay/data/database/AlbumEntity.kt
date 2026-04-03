package com.theveloper.pixelplay.data.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.theveloper.pixelplay.data.model.Album
import com.theveloper.pixelplay.utils.LocalArtworkUri
import com.theveloper.pixelplay.utils.normalizeMetadataTextOrEmpty

@Entity(
    tableName = "albums",
    indices = [
        Index(value = ["title"], unique = false),
        Index(value = ["artist_id"], unique = false), // Para buscar álbumes por artista
        Index(value = ["artist_name"], unique = false) // Nuevo índice para búsquedas por nombre de artista del álbum
    ]
)
data class AlbumEntity(
    @PrimaryKey val id: Long,
    @ColumnInfo(name = "title") val title: String,
    @ColumnInfo(name = "artist_name") val artistName: String, // Nombre del artista del álbum
    @ColumnInfo(name = "artist_id") val artistId: Long, // ID del artista principal del álbum (si aplica)
    @ColumnInfo(name = "album_art_uri_string") val albumArtUriString: String?,
    @ColumnInfo(name = "song_count") val songCount: Int,
    @ColumnInfo(name = "date_added") val dateAdded: Long,
    @ColumnInfo(name = "year") val year: Int
)

fun AlbumEntity.toAlbum(): Album {
    val effectiveAlbumArtUri = when {
        this.albumArtUriString.isNullOrBlank() -> null
        LocalArtworkUri.looksLikeVolatileArtworkUri(this.albumArtUriString) -> null
        else -> this.albumArtUriString
    }

    return Album(
        id = this.id,
        title = this.title.normalizeMetadataTextOrEmpty(),
        artist = this.artistName.normalizeMetadataTextOrEmpty(),
        albumArtUriString = effectiveAlbumArtUri,
        songCount = this.songCount,
        dateAdded = this.dateAdded,
        year = this.year
    )
}

fun List<AlbumEntity>.toAlbums(): List<Album> {
    return this.map { it.toAlbum() }
}

fun Album.toEntity(artistIdForAlbum: Long): AlbumEntity { // Necesitamos pasar el artistId si el modelo Album no lo tiene directamente
    return AlbumEntity(
        id = this.id,
        title = this.title,
        artistName = this.artist,
        artistId = artistIdForAlbum, // Asignar el ID del artista
        albumArtUriString = this.albumArtUriString,
        songCount = this.songCount,
        dateAdded = this.dateAdded,
        year = this.year
    )
}
