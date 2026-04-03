package com.theveloper.pixelplay.data.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.theveloper.pixelplay.data.model.Song
import java.io.File
import kotlin.math.absoluteValue

@Entity(
    tableName = "telegram_songs",
    indices = [
        Index(value = ["chat_id"]),
        Index(value = ["message_id"]),
        Index(value = ["file_id"]),
        Index(value = ["chat_id", "message_id"]),
        Index(value = ["thread_id"])  // NEW: index for topic queries
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
    @ColumnInfo(name = "album_art_uri") val albumArtUriString: String? = null,

    // NEW: null means the message is not in a topic / channel is not a forum
    @ColumnInfo(name = "thread_id") val threadId: Long? = null
)

fun TelegramSongEntity.resolveAlbumArtUri(): String? {
    val localFile = filePath.takeIf { it.isNotBlank() }?.let(::File)
    val hasLocalFile = localFile?.exists() == true
    val baseUri = albumArtUriString?.substringBefore('?')?.takeIf { it.isNotBlank() }
        ?: if (hasLocalFile) "telegram_art://$chatId/$messageId" else null

    if (baseUri == null) return null

    val version = localFile?.lastModified()?.takeIf { hasLocalFile && it > 0L } ?: return baseUri
    return "$baseUri?v=$version"
}

fun TelegramSongEntity.toSong(channelTitle: String? = null, topicName: String? = null): Song {
    val resolvedPath = if (this.filePath.isNotEmpty()) {
        this.filePath
    } else {
        val folder = topicName?.let { "$channelTitle/$it" } ?: channelTitle ?: "Telegram Stream"
        "/storage/emulated/0/Telegram Stream/$folder/${this.title}.mp3"
    }

    val syntheticArtistId = -(this.artist.hashCode().toLong().absoluteValue)
    val albumLabel = topicName ?: channelTitle ?: "Telegram Stream"
    val syntheticAlbumId = -((albumLabel).hashCode().toLong().absoluteValue)

    return Song(
        id = this.id,
        title = this.title,
        artist = this.artist,
        artistId = syntheticArtistId,
        artists = emptyList(),
        album = albumLabel,
        albumId = syntheticAlbumId,
        albumArtist = channelTitle ?: "Telegram",
        path = resolvedPath,
        contentUriString = this.filePath.ifEmpty { "telegram://${this.chatId}/${this.messageId}" },
        albumArtUriString = resolveAlbumArtUri(),
        duration = this.duration,
        genre = null,
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
        id = this.id,
        chatId = this.telegramChatId,
        messageId = this.id.substringAfterLast("_").toLongOrNull() ?: 0L,
        fileId = this.telegramFileId,
        title = this.title,
        artist = this.artist,
        duration = this.duration,
        filePath = this.path,
        mimeType = this.mimeType ?: "audio/mpeg",
        dateAdded = this.dateAdded,
        albumArtUriString = this.albumArtUriString,
        threadId = null // Filled explicitly when syncing via topic
    )
}

fun Song.toTelegramEntityWithThread(threadId: Long?): TelegramSongEntity? {
    return toTelegramEntity()?.copy(threadId = threadId)
}
