package com.theveloper.pixelplay.extensions.core

import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.model.ArtistRef
import com.theveloper.pixelplay.data.model.Album as AppAlbum
import com.theveloper.pixelplay.data.model.Playlist as AppPlaylist
import dev.brahmkshatriya.echo.common.models.Lyrics as EchoLyrics
import com.theveloper.pixelplay.data.model.Lyrics as AppLyrics
import com.theveloper.pixelplay.data.model.SyncedLine
import com.theveloper.pixelplay.data.model.SyncedWord
import dev.brahmkshatriya.echo.common.models.Track
import dev.brahmkshatriya.echo.common.models.Album
import dev.brahmkshatriya.echo.common.models.Playlist
import dev.brahmkshatriya.echo.common.models.Feed

suspend fun <T : Any> Feed<T>.loadAll(): List<T> {
    return getPagedData(tabs.firstOrNull()).pagedData.loadAll()
}

fun Track.toSong(extensionId: String, streamUrl: String? = null): Song {
    return Song(
        id = "extension:$extensionId:$id",
        title = title,
        artist = artists.joinToString(", ") { it.name },
        artistId = -2L, // Magic ID for extension artists
        artists = artists.map { ArtistRef(id = -2L, name = it.name, isPrimary = true) },
        album = album?.title ?: "Unknown Album",
        albumId = -2L, // Magic ID for extension albums
        path = streamUrl ?: "",
        contentUriString = streamUrl ?: "",
        albumArtUriString = (cover as? dev.brahmkshatriya.echo.common.models.ImageHolder.NetworkRequestImageHolder)?.request?.url,
        duration = duration ?: 0L,
        mimeType = "audio/mpeg", // Default, might need to be dynamic
        bitrate = 0,
        sampleRate = 0,
        extensionId = extensionId
    )
}

fun Album.toAppAlbum(extensionId: String): AppAlbum {
    return AppAlbum(
        id = -2L, // Magic ID for extension albums
        title = title,
        artist = artists.joinToString(", ") { it.name },
        albumArtUriString = (cover as? dev.brahmkshatriya.echo.common.models.ImageHolder.NetworkRequestImageHolder)?.request?.url,
        year = 0,
        dateAdded = System.currentTimeMillis(),
        songCount = trackCount?.toInt() ?: 0,
        extensionId = extensionId
    )
}

fun Playlist.toAppPlaylist(extensionId: String): AppPlaylist {
    return AppPlaylist(
        id = "extension:$extensionId:$id",
        name = title,
        songIds = emptyList(),
        source = "EXTENSION",
        extensionId = extensionId
    )
}

fun EchoLyrics.toAppLyrics(): AppLyrics {
    val lyric = this.lyrics
    return when (lyric) {
        null -> AppLyrics(plain = null, synced = null, areFromRemote = true)
        is EchoLyrics.Simple -> {
            val lines = lyric.text.lines().map { it.trim() }.filter { it.isNotEmpty() }
            AppLyrics(plain = if (lines.isEmpty()) null else lines, synced = null, areFromRemote = true)
        }
        is EchoLyrics.Timed -> {
            val synced = lyric.list.map { item ->
                SyncedLine(
                    time = item.startTime.toInt(),
                    line = item.text,
                    words = null,
                    translation = null,
                    romanization = null
                )
            }
            AppLyrics(plain = null, synced = if (synced.isEmpty()) null else synced, areFromRemote = true)
        }
        is EchoLyrics.WordByWord -> {
            val synced = lyric.list.map { wordList ->
                val words = wordList.map { itItem ->
                    SyncedWord(time = itItem.startTime.toInt(), word = itItem.text, startsNewWord = true)
                }
                val lineText = wordList.joinToString(" ") { it.text }
                SyncedLine(time = wordList.firstOrNull()?.startTime?.toInt() ?: 0, line = lineText, words = words)
            }
            AppLyrics(plain = null, synced = if (synced.isEmpty()) null else synced, areFromRemote = true)
        }
        else -> AppLyrics(plain = null, synced = null, areFromRemote = true)
    }
}
