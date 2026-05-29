package com.theveloper.pixelplay.data.ai

import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.stats.PlaybackStatsRepository
import com.theveloper.pixelplay.data.stats.StatsTimeRange
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AiBehaviorDataCollector @Inject constructor(
    private val statsRepository: PlaybackStatsRepository
) {
    data class BehaviorContext(
        val totalPlays: Int = 0,
        val totalListenTimeMs: Long = 0,
        val skipCount: Int = 0,
        val completionRate: Float = 0.85f,
        val favoriteCount: Int = 0,
        val topGenres: List<GenreStat> = emptyList(),
        val topArtists: List<ArtistStat> = emptyList(),
        val recentlyPlayedSongs: List<SongPlayStat> = emptyList(),
        val peakListeningHours: List<Int> = emptyList(),
        val averageSongDurationMs: Long = 0,
        val preferredEnergyLevel: EnergyLevel = EnergyLevel.MEDIUM,
        val listeningStreak: Int = 0,
        val favoriteDecades: List<String> = emptyList(),
        val preferredLanguages: List<String> = emptyList(),
        val topPlayedSongIds: List<String> = emptyList(),
        val recentlyCompletedSongIds: List<String> = emptyList(),
        val frequentSkipPatterns: Map<String, Int> = emptyMap()
    )

    data class GenreStat(val genre: String, val playCount: Int, val skipRate: Float = 0f)
    data class ArtistStat(val artist: String, val playCount: Int, val skipRate: Float = 0f)
    data class SongPlayStat(
        val songId: String,
        val title: String,
        val artist: String,
        val playCount: Int,
        val totalDurationMs: Long,
        val lastPlayedMs: Long,
        val estimatedSkips: Int = 0
    )

    enum class EnergyLevel { LOW, MEDIUM, HIGH, VARIABLE }
    enum class PlaySource { DAILY_MIX, AI_PLAYLIST, SEARCH, LIBRARY, RECOMMENDED, ALBUM, ARTIST, PLAYLIST, QUEUE, UNKNOWN }
    enum class SkipReason { NOT_ENJOYING, SKIP_NEXT, PLAYBACK_ISSUE, WRONG_MOOD, TOO_FAMILIAR, EXPLICIT_FILTERED, UNKNOWN }

    suspend fun gatherBehaviorContext(allSongs: List<Song> = emptyList()): BehaviorContext {
        return try {
            val summary = statsRepository.loadSummary(StatsTimeRange.ALL, allSongs)
            val history = statsRepository.loadPlaybackHistory(50)
            val events = statsRepository.exportEventsForBackup()

            val totalPlays = summary.totalPlayCount
            val totalListenTime = summary.songs.sumOf { it.totalDurationMs }
            val peakHours = summary.dayListeningDistribution?.buckets?.map { it.startMinute / 60 }?.distinct() ?: emptyList()

            val songMap = allSongs.associateBy { it.id }

            val allSongInfo = summary.songs.associate { s ->
                val expectedDuration = s.totalDurationMs / maxOf(s.playCount, 1).toFloat()
                val estimatedSkips = if (expectedDuration > 30_000f && s.playCount > 1) {
                    (s.playCount * 0.15f).toInt().coerceAtMost(s.playCount - 1)
                } else 0
                s.songId to (s.playCount to estimatedSkips)
            }

            val totalSkips = allSongInfo.values.sumOf { it.second }
            val totalCompletions = totalPlays - totalSkips
            val completionRate = if (totalPlays > 0) totalCompletions.toFloat() / totalPlays else 0.85f

            val topPlayedIds = summary.songs
                .sortedByDescending { it.playCount }
                .take(20)
                .map { it.songId }

            val recentlyCompleted = history
                .take(10)
                .map { it.songId }

            val recentSongIds = history.map { it.songId }.distinct().take(30)

            val recentlyPlayed = recentSongIds.mapNotNull { id ->
                val song = songMap[id]
                val stats = summary.songs.find { it.songId == id }
                val lastEvent = events.filter { it.songId == id }.maxByOrNull { it.timestamp }
                if (song != null && stats != null) {
                    val info = allSongInfo[id]
                    SongPlayStat(
                        songId = id,
                        title = song.title,
                        artist = song.displayArtist,
                        playCount = stats.playCount,
                        totalDurationMs = stats.totalDurationMs,
                        lastPlayedMs = lastEvent?.timestamp ?: 0L,
                        estimatedSkips = info?.second ?: 0
                    )
                } else null
            }

            val skipPatterns = summary.songs
                .filter { s ->
                    val expectedDuration = s.totalDurationMs / maxOf(s.playCount, 1).toFloat()
                    expectedDuration > 30_000f && s.playCount > 3 &&
                    (s.totalDurationMs.toFloat() / maxOf(s.playCount, 1)) < (30_000f * s.playCount)
                }
                .associate { it.songId to (it.playCount * 0.15f).toInt().coerceAtMost(it.playCount - 1) }

            BehaviorContext(
                totalPlays = totalPlays,
                totalListenTimeMs = totalListenTime,
                skipCount = totalSkips,
                completionRate = completionRate,
                favoriteCount = summary.songs.count { songMap[it.songId]?.isFavorite == true },
                topGenres = summary.topGenres.map { g ->
                    val genreSongs = summary.songs.filter { s -> songMap[s.songId]?.genre == g.genre }
                    val genreSkips = genreSongs.sumOf { allSongInfo[it.songId]?.second ?: 0 }
                    val genreRate = if (g.playCount > 0) genreSkips.toFloat() / g.playCount else 0f
                    GenreStat(g.genre, g.playCount, genreRate)
                },
                topArtists = summary.topArtists.map { a ->
                    val artistSongs = summary.songs.filter { s -> s.artist == a.artist }
                    val artistSkips = artistSongs.sumOf { allSongInfo[it.songId]?.second ?: 0 }
                    val artistRate = if (a.playCount > 0) artistSkips.toFloat() / a.playCount else 0f
                    ArtistStat(a.artist, a.playCount, artistRate)
                },
                recentlyPlayedSongs = recentlyPlayed,
                peakListeningHours = peakHours,
                averageSongDurationMs = if (summary.songs.isNotEmpty()) summary.songs.map { it.totalDurationMs }.average().toLong() else 0,
                preferredEnergyLevel = inferEnergyLevel(summary),
                listeningStreak = estimateListeningStreak(events),
                favoriteDecades = estimateFavoriteDecades(summary, allSongs),
                topPlayedSongIds = topPlayedIds,
                recentlyCompletedSongIds = recentlyCompleted,
                frequentSkipPatterns = skipPatterns
            )
        } catch (e: Exception) {
            Timber.tag("AIBehavior").e(e, "Failed to gather behavior context, using defaults")
            BehaviorContext()
        }
    }

    suspend fun recordPlayEvent(song: Song, playDurationMs: Long, completed: Boolean, source: PlaySource) {
        songPlayCounts[song.id] = (songPlayCounts[song.id] ?: 0) + 1
        songLastPlayed[song.id] = System.currentTimeMillis()
        if (completed) songCompletions[song.id] = (songCompletions[song.id] ?: 0) + 1
        Timber.tag("AIBehavior").d("Play: ${song.title}, dur=${playDurationMs}ms, compl=$completed, src=$source")
    }

    suspend fun recordSkipEvent(song: Song, reason: SkipReason) {
        songSkipCounts[song.id] = (songSkipCounts[song.id] ?: 0) + 1
        Timber.tag("AIBehavior").d("Skip: ${song.title}, reason=$reason")
    }

    suspend fun recordFavoriteEvent(song: Song, isFavorite: Boolean) {
        Timber.tag("AIBehavior").d("Favorite: ${song.title}, isFavorite=$isFavorite")
    }

    suspend fun getPerSongStats(songId: String): SongStats {
        return SongStats(
            playCount = (songPlayCounts[songId] ?: 0),
            skipCount = (songSkipCounts[songId] ?: 0),
            lastPlayedMs = (songLastPlayed[songId] ?: 0L),
            completionCount = (songCompletions[songId] ?: 0)
        )
    }

    suspend fun getPerSongStatsFromSummary(songId: String, allSongs: List<Song>): SongStats {
        val summary = statsRepository.loadSummary(StatsTimeRange.ALL, allSongs)
        val songPlay = summary.songs.find { it.songId == songId }
        return SongStats(
            playCount = songPlay?.playCount ?: 0,
            skipCount = 0,
            lastPlayedMs = 0L,
            completionCount = songPlay?.playCount ?: 0
        )
    }

    data class SongStats(
        val playCount: Int = 0,
        val skipCount: Int = 0,
        val lastPlayedMs: Long = 0,
        val completionCount: Int = 0
    )

    suspend fun generateBehaviorSummary(allSongs: List<Song> = emptyList()): String {
        val ctx = gatherBehaviorContext(allSongs)
        val totalActions = ctx.totalPlays + ctx.skipCount
        val skipRate = if (totalActions > 0) ((ctx.skipCount.toFloat() / totalActions) * 100).toInt() else 0

        return buildString {
            append("Listened to ${ctx.totalPlays} songs ")
            append("for ${formatDuration(ctx.totalListenTimeMs)}. ")
            append("Skip rate: ${skipRate}%. ")
            if (ctx.topGenres.isNotEmpty()) {
                append("Top genres: ${ctx.topGenres.take(3).joinToString(", ") { it.genre }}. ")
            }
            if (ctx.topArtists.isNotEmpty()) {
                append("Favorite artists: ${ctx.topArtists.take(3).joinToString(", ") { it.artist }}. ")
            }
            if (ctx.recentlyPlayedSongs.isNotEmpty()) {
                val lastTimestamp = ctx.recentlyPlayedSongs.first().lastPlayedMs
                append("Last played: ${formatTimestamp(lastTimestamp)}. ")
            }
            append("Energy preference: ${ctx.preferredEnergyLevel.name.lowercase()}. ")
            if (ctx.listeningStreak > 0) append("Current streak: ${ctx.listeningStreak} days.")
        }
    }

    suspend fun getUserContext(allSongs: List<Song> = emptyList()): String {
        val ctx = gatherBehaviorContext(allSongs)
        return buildString {
            append("User has listened to ${ctx.totalPlays} songs total. ")
            append("Favorite genres: ${ctx.topGenres.take(3).joinToString { "${it.genre} (${it.playCount} plays)" }}. ")
            if (ctx.peakListeningHours.isNotEmpty()) append("Peak listening hours: ${ctx.peakListeningHours.joinToString()}. ")
            append("Avg song completion: ${(ctx.completionRate * 100).toInt()}%. ")
            if (ctx.frequentSkipPatterns.isNotEmpty()) {
                append("Tends to skip: ${ctx.frequentSkipPatterns.size} songs frequently. ")
            }
        }
    }

    private fun inferEnergyLevel(summary: PlaybackStatsRepository.PlaybackStatsSummary): EnergyLevel {
        val g = summary.topGenres.take(3).map { it.genre.lowercase() }
        val high = listOf("rock", "metal", "punk", "electronic", "dance", "edm", "hip-hop", "rap", "drill", "trap")
        val low = listOf("ambient", "classical", "jazz", "acoustic", "lo-fi", "chill", "folk")
        val hc = g.count { gen -> high.any { it in gen } }
        val lc = g.count { gen -> low.any { it in gen } }
        return if (hc > lc) EnergyLevel.HIGH else if (lc > hc) EnergyLevel.LOW else EnergyLevel.MEDIUM
    }

    private fun estimateListeningStreak(events: List<PlaybackStatsRepository.PlaybackEvent>): Int {
        if (events.size < 2) return 0
        val sortedTimestamps = events.map { it.timestamp }.sortedDescending()
        var streak = 1
        val dayMs = 86400000L
        for (i in 0 until sortedTimestamps.size - 1) {
            if (sortedTimestamps[i] - sortedTimestamps[i + 1] <= dayMs * 2) streak++
            else break
        }
        return streak
    }

    private fun estimateFavoriteDecades(
        summary: PlaybackStatsRepository.PlaybackStatsSummary,
        allSongs: List<Song>
    ): List<String> {
        val songMap = allSongs.associateBy { it.id }
        val decadeCounts = mutableMapOf<String, Int>()
        summary.songs.forEach { s ->
            val year = songMap[s.songId]?.year
            if (year != null && year > 0) {
                val decade = "${(year / 10) * 10}s"
                decadeCounts[decade] = (decadeCounts[decade] ?: 0) + s.playCount
            }
        }
        return decadeCounts.entries.sortedByDescending { it.value }.take(3).map { it.key }
    }

    private fun formatDuration(ms: Long): String {
        val hours = ms / (1000 * 60 * 60)
        val minutes = (ms % (1000 * 60 * 60)) / (1000 * 60)
        return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
    }

    private fun formatTimestamp(epochMs: Long): String {
        val cal = java.util.Calendar.getInstance().apply { timeInMillis = epochMs }
        val now = java.util.Calendar.getInstance()
        return when {
            cal.get(java.util.Calendar.DAY_OF_YEAR) == now.get(java.util.Calendar.DAY_OF_YEAR) &&
            cal.get(java.util.Calendar.YEAR) == now.get(java.util.Calendar.YEAR) -> "today"
            cal.get(java.util.Calendar.DAY_OF_YEAR) == now.get(java.util.Calendar.DAY_OF_YEAR) - 1 &&
            cal.get(java.util.Calendar.YEAR) == now.get(java.util.Calendar.YEAR) -> "yesterday"
            else -> "${cal.get(java.util.Calendar.DAY_OF_MONTH)}/${cal.get(java.util.Calendar.MONTH) + 1}"
        }
    }
}
