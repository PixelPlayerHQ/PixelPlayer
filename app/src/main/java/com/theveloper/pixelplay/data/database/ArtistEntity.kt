package com.theveloper.pixelplay.data.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.theveloper.pixelplay.data.model.Artist
import com.theveloper.pixelplay.utils.normalizeMetadataTextOrEmpty

@Entity(
    tableName = "artists",
    indices = [
        Index(value = ["name"], unique = false),
        Index(value = ["extension_id"], unique = false)
    ] // Índice en el nombre para búsquedas rápidas
)
data class ArtistEntity(
    @PrimaryKey val id: Long,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "track_count") val trackCount: Int,
    @ColumnInfo(name = "image_url") val imageUrl: String? = null,
    @ColumnInfo(name = "custom_image_uri") val customImageUri: String? = null,
    @ColumnInfo(name = "extension_id") val extensionId: String? = null
)

fun ArtistEntity.toArtist(): Artist {
    return Artist(
        id = this.id,
        name = this.name.normalizeMetadataTextOrEmpty(),
        songCount = this.trackCount, // El modelo Artist usa songCount, MediaStore usa NUMBER_OF_TRACKS
        imageUrl = this.imageUrl,
        customImageUri = this.customImageUri,
        extensionId = this.extensionId
    )
}

fun List<ArtistEntity>.toArtists(): List<Artist> {
    return this.map { it.toArtist() }
}

fun Artist.toEntity(): ArtistEntity {
    return ArtistEntity(
        id = this.id,
        name = this.name,
        trackCount = this.songCount,
        imageUrl = this.imageUrl,
        customImageUri = this.customImageUri,
        extensionId = this.extensionId
    )
}
