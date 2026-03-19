package com.theveloper.pixelplay.data.database

import androidx.room.Embedded
import androidx.room.Relation

data class PlaylistWithSongsEntity(
    @Embedded
    val playlist: PlaylistEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "playlist_id",
        entity = PlaylistSongEntity::class
    )
    val songs: List<PlaylistSongEntity>
)
