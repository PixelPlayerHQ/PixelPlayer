package com.theveloper.pixelplay.data.ai

import android.content.Context
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.preferences.AiPreferencesRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Collects and structures behavioral data for AI recommendation engine.
 * Tracks listening patterns, preferences, and context for personalized AI features.
 *
 * Note: This is a simplified implementation. Full behavioral tracking requires
 * integration with the playback stats system.
 */
@Singleton
class AiBehaviorDataCollector @Inject constructor(
    @ApplicationContext private val context: Context,
    private val aiPreferencesRepository: AiPreferencesRepository
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
            totalPlays = 0,
            totalListenTimeMs = 0,
            skipCount = 0,
            favoriteCount = 0,
            topGenres = emptyList(),
            topArtists = emptyList(),
            recentlyPlayedSongs = emptyList(),
            peakListeningHours = emptyList(),
            averageSongDurationMs = 0,
            completionRate = 0f,
            preferredEnergyLevel = EnergyLevel.MEDIUM,
            listeningStreak = 0,
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
    }

    /**
     * Records a skip event.
     */
    suspend fun recordSkipEvent(song: Song, reason: SkipReason) {
        Timber.tag("AIBehavior").d("Skip event: song=${song.title}, reason=$reason")
    }

    /**
     * Records a favorite toggle event.
     */
    suspend fun recordFavoriteEvent(song: Song, isFavorite: Boolean) {
        Timber.tag("AIBehavior").d("Favorite event: song=${song.title}, isFavorite=$isFavorite")
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
        return EnergyLevel.MEDIUM
    }

    private fun getFavoriteDecades(): List<String> {
        return listOf("2020s", "2010s", "2000s")
    }

    private fun getPreferredLanguages(): List<String> {
        return listOf("English")
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