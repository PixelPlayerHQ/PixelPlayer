package com.theveloper.pixelplay.data.playlist

import android.content.Context
import android.net.Uri
import com.theveloper.pixelplay.data.model.Playlist
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.repository.MusicRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.BufferedReader
import java.io.InputStreamReader
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class M3uManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val musicRepository: MusicRepository
) {

    private companion object {
        // Hard cap on the number of lines parsed from a single M3U. A typical
        // user playlist has <10k entries; reject (truncate) anything past 1M
        // so a malformed or adversarial file cannot exhaust heap.
        const val MAX_M3U_LINES = 1_000_000
    }

    suspend fun parseM3u(uri: Uri): Pair<String, List<String>> {
        val songIds = mutableListOf<String>()
        var playlistName = "Imported Playlist"

        // Load a filtered one-shot snapshot so import respects the current library visibility rules.
        val allSongs = musicRepository.getAllSongsOnce()
        
        // Build lookup maps for fast matching
        val songsByPath = allSongs.associateBy { it.path }
        val songsByFileName = allSongs.groupBy { it.path.substringAfterLast("/") }
        val songsByContentUriFileName = allSongs.groupBy { it.contentUriString.substringAfterLast("/") }

        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            // M3U files are commonly UTF-8 or Windows-1252; default platform
            // charset on Android happens to be UTF-8 today, but pinning it
            // explicitly protects against future Locale/runtime drift and
            // makes the intent clear. Cap the line count so a malicious or
            // truncated multi-GB M3U cannot exhaust heap as we loop.
            BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8)).use { reader ->
                var line: String?
                var processed = 0
                while (reader.readLine().also { line = it } != null) {
                    processed++
                    if (processed > MAX_M3U_LINES) break
                    val trimmedLine = line?.trim() ?: continue
                    if (trimmedLine.isEmpty() || trimmedLine.startsWith("#")) {
                        // Handle metadata if needed, e.g., #EXTINF
                        continue
                    }

                    // Strip UTF-8 BOM if it leaked through readLine on line 1.
                    val payload = if (processed == 1) trimmedLine.removePrefix("\uFEFF") else trimmedLine

                    // payload is likely a file path or URI
                    // We need to find a song in our database that matches this path

                    // First try exact path match from pre-loaded map
                    val songByPath = songsByPath[payload]
                    if (songByPath != null) {
                        songIds.add(songByPath.id)
                    } else {
                        // Try to match by filename if path doesn't match exactly
                        val fileName = payload.substringAfterLast("/")
                        val matchedSong = songsByFileName[fileName]?.firstOrNull()
                            ?: songsByContentUriFileName[fileName]?.firstOrNull()
                        if (matchedSong != null) {
                            songIds.add(matchedSong.id)
                        }
                    }
                }
            }
        }

        // Try to get the filename as playlist name
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (nameIndex != -1 && cursor.moveToFirst()) {
                playlistName = cursor.getString(nameIndex).removeSuffix(".m3u").removeSuffix(".m3u8")
            }
        }

        return Pair(playlistName, songIds)
    }

    fun generateM3u(playlist: Playlist, songs: List<Song>): String {
        val sb = StringBuilder()
        sb.append("#EXTM3U\n")
        for (song in songs) {
            sb.append("#EXTINF:${song.duration / 1000},${song.artist} - ${song.title}\n")
            sb.append("${song.path}\n")
        }
        return sb.toString()
    }
}
