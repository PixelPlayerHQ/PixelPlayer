package com.theveloper.pixelplay.data.ai

import android.content.Context
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.preferences.AiPreferencesRepository
import com.theveloper.pixelplay.presentation.viewmodel.ListeningStatsTracker
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Collects and structures behavioral data for AI recommendation engine.
 * Tracks listening patterns, preferences, and context for personalized AI features.
 */
@Singleton
class AiBehaviorDataCollector @Inject constructor(
    @ApplicationContext private val context: Context,
    private val aiPreferencesRepository: AiPreferencesRepository,
    private val listeningStatsTracker: ListeningStatsTracker
) {
    /**
     * Collected behavior data structure for AI context.
     */
    data class BehaviorContext(
        val totalPlays: Int,
        val totalListenTimeMs: Long,
        val skipCount: Int,
        val favoriteCount: Int,
        val topGenres: List<Pair<String, Int>>,
        val topArtists: List<Pair<String, Int>>,
        val recentlyPlayedSongs: List<Song>,
        val peakListeningHours: List<Int>,
        val averageSongDurationMs: Long,
        val completionRate: Float,
        val preferredEnergyLevel: EnergyLevel,
        val listeningStreak: Int,
        val favoriteDecades: List<String>,
        val preferredLanguages: List<String>
    )

    enum class EnergyLevel {
        LOW, MEDIUM, HIGH, VARIABLE
    }

    /**
     * Gathers complete behavioral context for AI prompts.
     */
    suspend fun gatherBehaviorContext(): BehaviorContext {
        val prefs = aiPreferencesRepository.getPreferences.first()

        return BehaviorContext(
            totalPlays = listeningStatsTracker.totalPlayCount,
            totalListenTimeMs = listeningStatsTracker.totalListenTimeMs,
            skipCount = listeningStatsTracker.totalSkipCount,
            favoriteCount = listeningStatsTracker.favoriteCount,
            topGenres = listeningStatsTracker.topGenres.take(5),
            topArtists = listeningStatsTracker.topArtists.take(5),
            recentlyPlayedSongs = emptyList(), // Will be populated from history
            peakListeningHours = listeningStatsTracker.peakHours,
            averageSongDurationMs = listeningStatsTracker.averageSongDurationMs,
            completionRate = listeningStatsTracker.completionRate,
            preferredEnergyLevel = inferEnergyLevel(),
            listeningStreak = listeningStatsTracker.currentStreak,
            favoriteDecades = emptyList(),
            preferredLanguages = emptyList()
        )
    }

    /**
     * Records a play event with full context for AI learning.
     */
    suspend fun recordPlayEvent(
        song: Song,
        playDurationMs: Long,
        completed: Boolean,
        source: PlaySource
    ) {
        Timber.tag("AIBehavior").d(
            "Play event: song=${song.title}, duration=${playDurationMs}ms, completed=$completed, source=$source"
        )
        listeningStatsTracker.recordPlay(song.id, playDurationMs, completed)
    }

    /**
     * Records a skip event.
     */
    suspend fun recordSkipEvent(song: Song, reason: SkipReason) {
        Timber.tag("AIBehavior").d("Skip event: song=${song.title}, reason=$reason")
        listeningStatsTracker.recordSkip(song.id)
    }

    /**
     * Records a favorite toggle event.
     */
    suspend fun recordFavoriteEvent(song: Song, isFavorite: Boolean) {
        Timber.tag("AIBehavior").d("Favorite event: song=${song.title}, isFavorite=$isFavorite")
        if (isFavorite) {
            listeningStatsTracker.recordFavorite(song.id)
        }
    }

    /**
     * Generates a behavior summary string for AI prompts.
     */
    suspend fun generateBehaviorSummary(): String {
        val context = gatherBehaviorContext()
        return buildString {
            append("Listened to ${context.totalPlays} songs ")
            append("for ${formatDuration(context.totalListenTimeMs)}. ")
            append("Skip rate: ${((context.skipCount.toFloat() / (context.totalPlays + context.skipCount)) * 100).toInt()}%. ")

            if (context.topGenres.isNotEmpty()) {
                append("Top genres: ${context.topGenres.take(3).joinToString(", ") { it.first }}. ")
            }

            if (context.topArtists.isNotEmpty()) {
                append("Favorite artists: ${context.topArtists.take(3).joinToString(", ") { it.first }}. ")
            }

            append("Energy preference: ${context.preferredEnergyLevel.name.lowercase()}. ")
            append("Current streak: ${context.listeningStreak} days.")
        }
    }

    private fun inferEnergyLevel(): EnergyLevel {
        // Simple heuristic based on average completion rate
        val completionRate = listeningStatsTracker.completionRate
        return when {
            completionRate > 0.8 -> EnergyLevel.HIGH
            completionRate > 0.5 -> EnergyLevel.MEDIUM
            completionRate > 0.3 -> EnergyLevel.LOW
            else -> EnergyLevel.VARIABLE
        }
    }

    private fun formatDuration(ms: Long): String {
        val hours = ms / (1000 * 60 * 60)
        val minutes = (ms % (1000 * 60 * 60)) / (1000 * 60)
        return when {
            hours > 0 -> "${hours}h ${minutes}m"
            else -> "${minutes}m"
        }
    }

    enum class PlaySource {
        DAILY_MIX, AI_PLAYLIST, SEARCH, LIBRARY, RECOMMENDED, ALBUM, ARTIST, PLAYLIST, QUEUE, UNKNOWN
    }

    enum class SkipReason {
        NOT_ENJOYING, SKIP_NEXT, PLAYBACK_ISSUE, WRONG_MOOD, TOO_FAMILIAR, EXPLICIT_FILTERED, UNKNOWN
    }
}