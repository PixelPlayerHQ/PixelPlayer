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
        // Core stats
        val totalPlays: Int = 0,
        val totalListenTimeMs: Long = 0,
        val skipCount: Int = 0,
        val favoriteCount: Int = 0,
        
        // Preferences
        val topGenres: List<Pair<String, Int>> = emptyList(),
        val topArtists: List<Pair<String, Int>> = emptyList(),
        val recentlyPlayedSongs: List<Song> = emptyList(),
        
        // Listening patterns
        val peakListeningHours: List<Int> = emptyList(),
        val averageSongDurationMs: Long = 0,
        val completionRate: Float = 0f,
        
        // User characteristics
        val preferredEnergyLevel: EnergyLevel = EnergyLevel.MEDIUM,
        val listeningStreak: Int = 0,
        val favoriteDecades: List<String> = emptyList(),
        val preferredLanguages: List<String> = emptyList()
    )

    enum class EnergyLevel {
        LOW, MEDIUM, HIGH, VARIABLE
    }

    enum class PlaySource {
        DAILY_MIX, AI_PLAYLIST, SEARCH, LIBRARY, RECOMMENDED, ALBUM, ARTIST, PLAYLIST, QUEUE, UNKNOWN
    }

    enum class SkipReason {
        NOT_ENJOYING, SKIP_NEXT, PLAYBACK_ISSUE, WRONG_MOOD, TOO_FAMILIAR, EXPLICIT_FILTERED, UNKNOWN
    }

    /**
     * Gathers complete behavioral context for AI prompts.
     */
    suspend fun gatherBehaviorContext(): BehaviorContext {
        return BehaviorContext(
            totalPlays = listeningStatsTracker.totalPlayCount,
            totalListenTimeMs = listeningStatsTracker.totalListenTimeMs,
            skipCount = listeningStatsTracker.totalSkipCount,
            favoriteCount = listeningStatsTracker.favoriteCount,
            topGenres = listeningStatsTracker.topGenres.take(5),
            topArtists = listeningStatsTracker.topArtists.take(5),
            recentlyPlayedSongs = listeningStatsTracker.getRecentlyPlayedSongs(20),
            peakListeningHours = listeningStatsTracker.peakHours,
            averageSongDurationMs = listeningStatsTracker.averageSongDurationMs,
            completionRate = listeningStatsTracker.completionRate,
            preferredEnergyLevel = inferEnergyLevel(),
            listeningStreak = listeningStatsTracker.currentStreak,
            favoriteDecades = getFavoriteDecades(),
            preferredLanguages = getPreferredLanguages()
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
        val totalActions = context.totalPlays + context.skipCount
        val skipRate = if (totalActions > 0) {
            ((context.skipCount.toFloat() / totalActions) * 100).toInt()
        } else 0
        
        return buildString {
            append("Listened to ${context.totalPlays} songs ")
            append("for ${formatDuration(context.totalListenTimeMs)}. ")
            append("Skip rate: ${skipRate}%. ")

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

    /**
     * Gets the user's current context for AI prompts.
     */
    suspend fun getUserContext(): String {
        val context = gatherBehaviorContext()
        return buildString {
            append("User has listened to ${context.totalPlays} songs total. ")
            append("Favorite genres: ${context.topGenres.take(3).joinToString { "${it.first} (${it.second} plays)" }}. ")
            append("Peak listening hours: ${context.peakListeningHours.joinToString()}. ")
            append("Average song completion: ${(context.completionRate * 100).toInt()}%. ")
        }
    }

    private fun inferEnergyLevel(): EnergyLevel {
        // Simple heuristic based on average completion rate
        val completionRate = listeningStatsTracker.completionRate
        val skipRate = if (listeningStatsTracker.totalPlayCount > 0) {
            listeningStatsTracker.totalSkipCount.toFloat() / listeningStatsTracker.totalPlayCount
        } else 0f
        
        return when {
            completionRate > 0.8 && skipRate < 0.2 -> EnergyLevel.HIGH
            completionRate > 0.6 -> EnergyLevel.MEDIUM
            skipRate > 0.5 -> EnergyLevel.LOW
            else -> EnergyLevel.VARIABLE
        }
    }

    private fun getFavoriteDecades(): List<String> {
        // Analyze songs to find favorite decades
        // This would need to check release years from song metadata
        return emptyList() // TODO: Implement based on song release years
    }

    private fun getPreferredLanguages(): List<String> {
        // Analyze song metadata for language tags
        return emptyList() // TODO: Implement based on song language metadata
    }

    private fun formatDuration(ms: Long): String {
        val hours = ms / (1000 * 60 * 60)
        val minutes = (ms % (1000 * 60 * 60)) / (1000 * 60)
        return when {
            hours > 0 -> "${hours}h ${minutes}m"
            else -> "${minutes}m"
        }
    }
}