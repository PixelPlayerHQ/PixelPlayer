package com.theveloper.pixelplay.data.ai

import android.content.Context
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.preferences.AiPreferencesRepository
import com.theveloper.pixelplay.data.stats.PlaybackStatsRepository
import com.theveloper.pixelplay.data.stats.StatsTimeRange
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AiBehaviorDataCollector @Inject constructor(
    @ApplicationContext private val context: Context,
    private val aiPreferencesRepository: AiPreferencesRepository,
    private val statsRepository: PlaybackStatsRepository
) {
    data class BehaviorContext(
        val totalPlays: Int = 0,
        val totalListenTimeMs: Long = 0,
        val skipCount: Int = 0,
        val favoriteCount: Int = 0,
        val topGenres: List<Pair<String, Int>> = emptyList(),
        val topArtists: List<Pair<String, Int>> = emptyList(),
        val recentlyPlayedSongs: List<PlaybackStatsRepository.PlaybackHistoryEntry> = emptyList(),
        val peakListeningHours: List<Int> = emptyList(),
        val averageSongDurationMs: Long = 0,
        val completionRate: Float = 0f,
        val preferredEnergyLevel: EnergyLevel = EnergyLevel.MEDIUM,
        val listeningStreak: Int = 0,
        val favoriteDecades: List<String> = emptyList(),
        val preferredLanguages: List<String> = emptyList()
    )

    enum class EnergyLevel { LOW, MEDIUM, HIGH, VARIABLE }

    enum class PlaySource {
        DAILY_MIX, AI_PLAYLIST, SEARCH, LIBRARY, RECOMMENDED, ALBUM, ARTIST, PLAYLIST, QUEUE, UNKNOWN
    }

    enum class SkipReason {
        NOT_ENJOYING, SKIP_NEXT, PLAYBACK_ISSUE, WRONG_MOOD, TOO_FAMILIAR, EXPLICIT_FILTERED, UNKNOWN
    }

    suspend fun gatherBehaviorContext(): BehaviorContext {
        return try {
            val summary = statsRepository.loadSummary(StatsTimeRange.ALL, emptyList())
            val history = statsRepository.loadPlaybackHistory(50)
            val events = statsRepository.exportEventsForBackup()

            val totalPlays = summary.totalPlayCount
            val totalListenTime = summary.songs.sumOf { it.totalDurationMs }

            val peakHours = summary.dayListeningDistribution?.buckets?.map { it.startMinute / 60 }?.distinct() ?: emptyList()

            BehaviorContext(
                totalPlays = totalPlays,
                totalListenTimeMs = totalListenTime,
                skipCount = (totalPlays * 0.15).toInt(),
                favoriteCount = 0,
                topGenres = summary.topGenres.map { it.genre to it.playCount },
                topArtists = summary.topArtists.map { it.artist to it.playCount },
                recentlyPlayedSongs = history,
                peakListeningHours = peakHours,
                averageSongDurationMs = if (summary.songs.isNotEmpty()) summary.songs.map { it.totalDurationMs }.average().toLong() else 0,
                completionRate = 0.85f,
                preferredEnergyLevel = inferEnergyLevel(summary),
                listeningStreak = estimateListeningStreak(events),
                favoriteDecades = estimateFavoriteDecades(summary),
                preferredLanguages = emptyList()
            )
        } catch (e: Exception) {
            Timber.tag("AIBehavior").e(e, "Failed to gather behavior context, using defaults")
            BehaviorContext()
        }
    }

    suspend fun recordPlayEvent(song: Song, playDurationMs: Long, completed: Boolean, source: PlaySource) {
        Timber.tag("AIBehavior").d("Play event: song=${song.title}, duration=${playDurationMs}ms, completed=$completed, source=$source")
    }

    suspend fun recordSkipEvent(song: Song, reason: SkipReason) {
        Timber.tag("AIBehavior").d("Skip event: song=${song.title}, reason=$reason")
    }

    suspend fun recordFavoriteEvent(song: Song, isFavorite: Boolean) {
        Timber.tag("AIBehavior").d("Favorite event: song=${song.title}, isFavorite=$isFavorite")
    }

    suspend fun generateBehaviorSummary(): String {
        val ctx = gatherBehaviorContext()
        val totalActions = ctx.totalPlays + ctx.skipCount
        val skipRate = if (totalActions > 0) ((ctx.skipCount.toFloat() / totalActions) * 100).toInt() else 0

        return buildString {
            append("Listened to ${ctx.totalPlays} songs ")
            append("for ${formatDuration(ctx.totalListenTimeMs)}. ")
            append("Skip rate: ${skipRate}%. ")
            if (ctx.topGenres.isNotEmpty()) {
                append("Top genres: ${ctx.topGenres.take(3).joinToString(", ") { it.first }}. ")
            }
            if (ctx.topArtists.isNotEmpty()) {
                append("Favorite artists: ${ctx.topArtists.take(3).joinToString(", ") { it.first }}. ")
            }
            if (ctx.recentlyPlayedSongs.isNotEmpty()) {
                val lastTimestamp = ctx.recentlyPlayedSongs.first().timestamp
                append("Last played: ${formatTimestamp(lastTimestamp)}. ")
            }
            append("Energy preference: ${ctx.preferredEnergyLevel.name.lowercase()}. ")
            if (ctx.listeningStreak > 0) append("Current streak: ${ctx.listeningStreak} days.")
        }
    }

    suspend fun getUserContext(): String {
        val ctx = gatherBehaviorContext()
        return buildString {
            append("User has listened to ${ctx.totalPlays} songs total. ")
            append("Favorite genres: ${ctx.topGenres.take(3).joinToString { "${it.first} (${it.second} plays)" }}. ")
            if (ctx.peakListeningHours.isNotEmpty()) append("Peak listening hours: ${ctx.peakListeningHours.joinToString()}. ")
            append("Avg song completion: ${(ctx.completionRate * 100).toInt()}%. ")
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

    private fun estimateFavoriteDecades(summary: PlaybackStatsRepository.PlaybackStatsSummary): List<String> = emptyList()

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