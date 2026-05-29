package com.theveloper.pixelplay.data.ai


import com.theveloper.pixelplay.data.database.LocalPlaylistDao
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.stats.PlaybackStatsRepository
import com.theveloper.pixelplay.data.stats.StatsTimeRange
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserProfileDigestGenerator @Inject constructor(
    private val statsRepository: PlaybackStatsRepository,
    private val playlistDao: LocalPlaylistDao
) {
    private val SAFE_TARGET_CHAR_LIMIT = 4000
    private val MAX_TARGET_CHAR_LIMIT = 32000

    private val SAFE_LISTENED_LIMIT = 15
    private val SAFE_DISCOVERY_LIMIT = 30
    private val FULL_LISTENED_LIMIT = 60
    private val FULL_DISCOVERY_LIMIT = 120

    suspend fun generateDigest(
        allSongs: List<Song>,
        isSafeLimit: Boolean = true,
        maxSongsForContext: Int = 50
    ): String {
        val targetLimit = if (isSafeLimit) SAFE_TARGET_CHAR_LIMIT else MAX_TARGET_CHAR_LIMIT
        val listenedLimit = if (isSafeLimit) {
            (maxSongsForContext * 0.3).toInt().coerceIn(SAFE_LISTENED_LIMIT, FULL_LISTENED_LIMIT)
        } else {
            (maxSongsForContext * 0.5).toInt().coerceIn(FULL_LISTENED_LIMIT, 200)
        }
        val discoveryLimit = if (isSafeLimit) {
            (maxSongsForContext * 0.6).toInt().coerceIn(SAFE_DISCOVERY_LIMIT, FULL_DISCOVERY_LIMIT)
        } else {
            maxSongsForContext.coerceIn(FULL_DISCOVERY_LIMIT, 400)
        }
        val recentLimit = if (isSafeLimit) 5 else 15

        val summary = statsRepository.loadSummary(StatsTimeRange.ALL, allSongs)
        val history = statsRepository.loadPlaybackHistory(50)
        val events = statsRepository.exportEventsForBackup()
        val playlists = playlistDao.observePlaylistsWithSongs().first()
        
        val sb = StringBuilder()
        sb.append("USER_PROFILE\n")
        
        // --- 1. Behavioral & Pattern Metrics ---
        val totalSkips = estimateTotalSkips(summary)
        val totalCompletions = summary.totalPlayCount - totalSkips
        val completionRate = if (summary.totalPlayCount > 0) ((totalCompletions.toFloat() / summary.totalPlayCount) * 100).toInt() else 85
        sb.append("STATS: plays=${summary.totalPlayCount}, uniq=${summary.uniqueSongs}, skip=${totalSkips}, comp=${completionRate}%\n")
        sb.append("GENRES: ${summary.topGenres.take(3).joinToString(",") { it.genre }}\n")
        sb.append("ARTISTS: ${summary.topArtists.take(5).joinToString(",") { it.artist }}\n")
        
        summary.dayListeningDistribution?.let { dist ->
            val phases = dist.buckets.groupBy { bucket ->
                val hour = bucket.startMinute / 60
                when (hour) {
                    in 5..10 -> "Morning"
                    in 11..16 -> "Afternoon"
                    in 17..22 -> "Evening"
                    else -> "Night"
                }
            }.mapValues { it.value.sumOf { b -> b.totalDurationMs } }
            sb.append("PHASE: ${phases.maxByOrNull { it.value }?.key ?: "Unknown"}\n")
        }
        
        val variety = if (summary.totalPlayCount > 0) summary.uniqueSongs.toDouble() / summary.totalPlayCount else 0.0
        sb.append("VAR: ${"%.2f".format(variety)}\n")

        val genreCompletion = summary.topGenres.take(3).map { g ->
            val genreSongs = summary.songs.filter { s -> allSongs.find { it.id == s.songId }?.genre == g.genre }
            val genrePlays = genreSongs.sumOf { it.playCount }
            val genreSkips = estimateSkipCountForSongs(genreSongs)
            val rate = if (genrePlays > 0) ((genrePlays - genreSkips).toFloat() / genrePlays * 100).toInt() else 0
            "${g.genre}:${rate}%"
        }
        if (genreCompletion.isNotEmpty()) {
            sb.append("GENRE_COMP: ${genreCompletion.joinToString(",")}\n")
        }

        val playlistLimit = if (isSafeLimit) 5 else 20
        if (playlists.isNotEmpty()) {
            sb.append("PL: ${playlists.take(playlistLimit).joinToString(",") { it.playlist.name }}\n")
        }

        // --- 1b. Recently Played (compact, with timestamp) ---
        val songMap = allSongs.associateBy { it.id }
        val recentIds = history.map { it.songId }.distinct().take(recentLimit)
        if (recentIds.isNotEmpty()) {
            sb.append("\nRECENT: id|hrs_ago|p\n")
            val now = System.currentTimeMillis()
            recentIds.forEach { id ->
                if (sb.length >= (targetLimit * 0.5).toInt()) return@forEach
                val lastEvent = events.filter { it.songId == id }.maxByOrNull { it.timestamp }
                val stats = summary.songs.find { it.songId == id }
                val hrsAgo = if (lastEvent != null) ((now - lastEvent.timestamp) / 3600000).toInt() else 999
                sb.append("$id|${hrsAgo}h|${stats?.playCount ?: 1}\n")
            }
        }
        
        // --- 2. Listened Tracks (capped) ---
        sb.append("\nLISTENED: id|p|d_s|f|alb|dur|g|meta\n")
        
        val playedSongs = summary.songs.take(listenedLimit)
        
        playedSongs.forEach { s ->
            if (sb.length >= (targetLimit * 0.6).toInt()) return@forEach
            val song = songMap[s.songId]
            val fav = if (song?.isFavorite == true) "1" else "0"
            val mins = s.totalDurationMs / 60000
            val album = song?.album?.take(20)?.replace("|", "/") ?: "?"
            val durationSec = if (song != null) song.duration / 1000 else 0
            val genre = song?.genre?.take(12)?.replace("|", "/") ?: "?"
            val title = s.title.take(30)
            val artist = s.artist.take(20)
            sb.append("${s.songId}|${s.playCount}|$mins|$fav|$album|$durationSec|$genre|$title-$artist\n")
        }
        
        // --- 3. Discovery Pool (strictly capped) ---
        val playedIds = summary.songs.map { it.songId }.toSet()
        val unplayed = allSongs.filter { it.id !in playedIds }
            .shuffled()
            .take(discoveryLimit)
        
        if (unplayed.isNotEmpty()) {
            sb.append("\nDISCOVERY_POOL: id|alb|dur|g|meta\n")
            unplayed.forEach { s ->
                if (sb.length >= targetLimit) return@forEach
                val title = s.title.take(30)
                val artist = s.displayArtist.take(20)
                val album = s.album?.take(20)?.replace("|", "/") ?: "?"
                val durationSec = s.duration / 1000
                val genre = s.genre?.take(12)?.replace("|", "/") ?: "?"
                sb.append("${s.id}|$album|$durationSec|$genre|$title-$artist\n")
            }
        }
        
        return sb.toString()
    }

    private fun estimateTotalSkips(summary: PlaybackStatsRepository.PlaybackStatsSummary): Int {
        return summary.songs.sumOf { s ->
            val expectedAvg = if (s.playCount > 0) s.totalDurationMs / maxOf(s.playCount, 1).toFloat() else 0f
            if (expectedAvg > 30_000f && s.playCount > 1) {
                (s.playCount * 0.15f).toInt().coerceAtMost(s.playCount - 1)
            } else 0
        }
    }

    private fun estimateSkipCountForSongs(songs: List<PlaybackStatsRepository.SongPlaybackSummary>): Int {
        return songs.sumOf { s ->
            val expectedAvg = if (s.playCount > 0) s.totalDurationMs / maxOf(s.playCount, 1).toFloat() else 0f
            if (expectedAvg > 30_000f && s.playCount > 1) {
                (s.playCount * 0.15f).toInt().coerceAtMost(s.playCount - 1)
            } else 0
        }
    }
}
