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
    // Token Budget Tiers:
    // SAFE: ~1000 tokens (4000 chars) — fast, cheap, still gives good results
    // FULL: ~8000 tokens (32000 chars) — deep context for maximum personalization
    private val SAFE_TARGET_CHAR_LIMIT = 4000
    private val MAX_TARGET_CHAR_LIMIT = 32000

    // Track limits per tier — prevents runaway context size
    private val SAFE_LISTENED_LIMIT = 15
    private val SAFE_DISCOVERY_LIMIT = 30
    private val FULL_LISTENED_LIMIT = 60
    private val FULL_DISCOVERY_LIMIT = 120

    /**
     * Computes a highly condensed representation of the user's listening profile.
     * Uses a compact key-value format to minimize token consumption while maximizing signal.
     *
     * Safe mode aggressively caps all sections to stay under ~1000 tokens.
     * Full mode provides deep context for maximum personalization quality.
     */
    suspend fun generateDigest(allSongs: List<Song>, isSafeLimit: Boolean = true): String {
        val targetLimit = if (isSafeLimit) SAFE_TARGET_CHAR_LIMIT else MAX_TARGET_CHAR_LIMIT
        val listenedLimit = if (isSafeLimit) SAFE_LISTENED_LIMIT else FULL_LISTENED_LIMIT
        val discoveryLimit = if (isSafeLimit) SAFE_DISCOVERY_LIMIT else FULL_DISCOVERY_LIMIT

        val summary = statsRepository.loadSummary(StatsTimeRange.ALL, allSongs)
        val playlists = playlistDao.observePlaylistsWithSongs().first()
        
        val sb = StringBuilder()
        sb.append("USER_PROFILE\n")
        
        // --- 1. Behavioral & Pattern Metrics (compact) ---
        sb.append("STATS: plays=${summary.totalPlayCount}, uniq=${summary.uniqueSongs}\n")
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
        
        val playlistLimit = if (isSafeLimit) 5 else 20
        if (playlists.isNotEmpty()) {
            sb.append("PL: ${playlists.take(playlistLimit).joinToString(",") { it.playlist.name }}\n")
        }
        
        // --- 2. Listened Tracks (capped) ---
        // Compact format: ID|plays|mins|fav|title-artist
        sb.append("\nLISTENED: id|p|d|f|meta\n")
        
        val songMap = allSongs.associateBy { it.id }
        val playedSongs = summary.songs.take(listenedLimit)
        
        playedSongs.forEach { s ->
            if (sb.length >= (targetLimit * 0.6).toInt()) return@forEach
            val song = songMap[s.songId]
            val fav = if (song?.isFavorite == true) "1" else "0"
            val mins = s.totalDurationMs / 60000
            // Truncate long titles to save tokens
            val title = s.title.take(30)
            val artist = s.artist.take(20)
            sb.append("${s.songId}|${s.playCount}|$mins|$fav|$title-$artist\n")
        }
        
        // --- 3. Discovery Pool (strictly capped) ---
        // AI needs to know what's available but unplayed
        val playedIds = summary.songs.map { it.songId }.toSet()
        val unplayed = allSongs.filter { it.id !in playedIds }
            .shuffled()
            .take(discoveryLimit)
        
        if (unplayed.isNotEmpty()) {
            sb.append("\nDISCOVERY_POOL:\n")
            unplayed.forEach { s ->
                if (sb.length >= targetLimit) return@forEach
                val title = s.title.take(30)
                val artist = s.displayArtist.take(20)
                sb.append("${s.id}|$title-$artist\n")
            }
        }
        
        return sb.toString()
    }
}
