package com.theveloper.pixelplay.data.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Represents a playlist cached from a Navidrome/Subsonic server.
 *
 * @property id The unique playlist ID from the Subsonic server
 * @property name The playlist name
 * @property comment The playlist description/comment
 * @property owner The owner username
 * @property coverArtId The cover art ID used to construct the cover art URL
 * @property songCount The number of songs in the playlist
 * @property duration The total duration in milliseconds
 * @property public Whether the playlist is public
 * @property lastSyncTime The timestamp of the last sync
 */
@Entity(tableName = "navidrome_playlists")
data class NavidromePlaylistEntity(
    @PrimaryKey val id: String,
    val name: String,
    val comment: String?,
    val owner: String?,
    @ColumnInfo(name = "cover_art_id") val coverArtId: String?,
    @ColumnInfo(name = "song_count") val songCount: Int,
    val duration: Long,
    val public: Boolean,
    @ColumnInfo(name = "last_sync_time") val lastSyncTime: Long
)
