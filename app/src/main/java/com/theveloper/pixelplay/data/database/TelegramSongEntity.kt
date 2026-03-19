package com.theveloper.pixelplay.data.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.theveloper.pixelplay.data.model.Song
import kotlin.math.absoluteValue

@Entity(
    tableName = "telegram_songs",
    indices = [
        Index(value = ["chat_id"]),
        Index(value = ["message_id"]),
        Index(value = ["file_id"]),
        Index(value = ["chat_id", "message_id"])
    ]
)
data class TelegramSongEntity(
    @PrimaryKey
    @ColumnInfo(name = "id") val id: String, // format: "chatId_messageId"
    
    @ColumnInfo(name = "chat_id") val chatId: Long,
    @ColumnInfo(name = "message_id") val messageId: Long,
    @ColumnInfo(name = "file_id") val fileId: Int,
    
    @ColumnInfo(name = "title") val title: String,
    @ColumnInfo(name = "artist") val artist: String,
    @ColumnInfo(name = "duration") val duration: Long,
    
    @ColumnInfo(name = "file_path") val filePath: String, // Empty if not downloaded
    @ColumnInfo(name = "mime_type") val mimeType: String,
    @ColumnInfo(name = "date_added") val dateAdded: Long,
    @ColumnInfo(name = "album_art_uri") val albumArtUriString: String? = null
)

fun TelegramSongEntity.toSong(channelTitle: String? = null): Song {
    // Construct a virtual path for the "Folders" view if the file isn't downloaded.
    // We use a path structure that mimics standard storage so the app's folder builder handles it correctly.
    // "/storage/emulated/0" is the standard external storage root.
    val resolvedPath = if (this.filePath.isNotEmpty()) {
        this.filePath 
    } else {
        "/storage/emulated/0/Telegram Stream/${channelTitle ?: "Unknown Channel"}/${this.title}.mp3"
    }

    val syntheticArtistId = -(this.artist.hashCode().toLong().absoluteValue)
    val syntheticAlbumId = -((channelTitle ?: "Telegram Stream").hashCode().toLong().absoluteValue)

    return Song(
        id = this.id, // String ID
        title = this.title,
        artist = this.artist,
        artistId = syntheticArtistId, 
        artists = emptyList(),
        album = channelTitle ?: "Telegram Stream",
        albumId = syntheticAlbumId,
        albumArtist = channelTitle ?: "Telegram",
        path = resolvedPath,
        contentUriString = this.filePath.ifEmpty { "telegram://${this.chatId}/${this.messageId}" }, // Persistent URI scheme
        albumArtUriString = this.albumArtUriString,
        duration = this.duration,
        genre = "Telegram",
        lyrics = null,
        isFavorite = false,
        trackNumber = 0,
        year = 0,
        dateAdded = this.dateAdded,
        mimeType = this.mimeType,
        bitrate = 0,
        sampleRate = 0,
        telegramFileId = this.fileId,
        telegramChatId = this.chatId
    )
}

fun Song.toTelegramEntity(): TelegramSongEntity? {
    if (this.telegramChatId == null || this.telegramFileId == null) return null
    return TelegramSongEntity(
        id = this.id, // Must be the "chatId_messageId" format
        chatId = this.telegramChatId,
        messageId = this.id.substringAfterLast("_").toLongOrNull() ?: 0L, 
        fileId = this.telegramFileId,
        title = this.title,
        artist = this.artist,
        duration = this.duration,
        filePath = this.path,
        mimeType = this.mimeType ?: "audio/mpeg",
        dateAdded = this.dateAdded,
        albumArtUriString = this.albumArtUriString
    )
}
