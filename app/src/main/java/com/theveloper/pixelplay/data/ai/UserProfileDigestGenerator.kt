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
    private val playlistDao: LocalPlaylistDao,
    private val preferencesRepo: com.theveloper.pixelplay.data.preferences.AiPreferencesRepository,
) {
    private val SAFE_TARGET_CHAR_LIMIT = 4000
    private val MAX_TARGET_CHAR_LIMIT = 32000

    private val SAFE_LISTENED_LIMIT = 15
    private val SAFE_DISCOVERY_LIMIT = 30
    private val FULL_LISTENED_LIMIT = 60
    private val FULL_DISCOVERY_LIMIT = 120

    suspend fun generateDigest(allSongs: List<Song>, isSafeLimit: Boolean = true): String {
        val digestMode = preferencesRepo.aiDigestMode.first()
        val useExtendedFields = preferencesRepo.aiIncludeExtendedFields.first()
        val isSafe = if (digestMode == "full") false else isSafeLimit

        val targetLimit = if (isSafe) SAFE_TARGET_CHAR_LIMIT else MAX_TARGET_CHAR_LIMIT
        val listenedLimit = if (isSafe) SAFE_LISTENED_LIMIT else FULL_LISTENED_LIMIT
        val discoveryLimit = if (isSafe) SAFE_DISCOVERY_LIMIT else FULL_DISCOVERY_LIMIT

        val summary = statsRepository.loadSummary(StatsTimeRange.ALL, allSongs)
        val playlists = playlistDao.observePlaylistsWithSongs().first()

        val sb = StringBuilder()
        sb.append("USER_PROFILE\n")

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

        val playlistLimit = if (isSafe) 5 else 20
        if (playlists.isNotEmpty()) {
            sb.append("PL: ${playlists.take(playlistLimit).joinToString(",") { it.playlist.name }}\n")
        }

        sb.append("\nLISTENED: id|p|d|f|meta\n")

        val songMap = allSongs.associateBy { it.id }
        val playedSongs = summary.songs.take(listenedLimit)

        playedSongs.forEach { s ->
            if (sb.length >= (targetLimit * 0.6).toInt()) return@forEach
            val song = songMap[s.songId]
            val fav = if (song?.isFavorite == true) "1" else "0"
            val mins = s.totalDurationMs / 60000
            val title = s.title.take(30)
            val artist = s.artist.take(20)
            if (useExtendedFields) {
                val album = song?.album?.take(20) ?: "?"
                val year = song?.year?.toString()?.take(4) ?: "?"
                sb.append("${s.songId}|${s.playCount}|$mins|$fav|$title-$artist|$album|$year\n")
            } else {
                sb.append("${s.songId}|${s.playCount}|$mins|$fav|$title-$artist\n")
            }
        }

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
                if (useExtendedFields) {
                    val genre = s.genre?.take(15) ?: "?"
                    sb.append("${s.id}|$title-$artist|$genre\n")
                } else {
                    sb.append("${s.id}|$title-$artist\n")
                }
            }
        }

        return sb.toString()
    }
}
