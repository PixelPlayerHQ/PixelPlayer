package com.theveloper.pixelplay.youtube

import com.theveloper.pixelplay.data.ai.AiHandler
import com.theveloper.pixelplay.data.ai.AiResponseCleaner
import com.theveloper.pixelplay.data.ai.AiSystemPromptType
import javax.inject.Inject
import javax.inject.Singleton
import timber.log.Timber

internal data class StatsCreditGuess(
    val songId: String,
    val artist: String,
    val album: String,
)

internal fun statsCreditPrompt(gaps: List<CatalogCreditGap>): String = buildString {
    appendLine("Name the artist and album for these played songs.")
    gaps.forEach { gap ->
        append("id=").append(gap.songId)
        append(" title=\"").append(gap.title.replace("\"", "'"))
        append("\" artist=\"").append(gap.artist.replace("\"", "'"))
        append("\" album=\"").append(gap.album.replace("\"", "'"))
        appendLine("\"")
    }
}

internal fun parseStatsCredits(raw: String): List<StatsCreditGuess> {
    val body = AiResponseCleaner.extractJsonArray(raw) ?: return emptyList()
    val guesses = mutableListOf<StatsCreditGuess>()
    var index = 0
    while (index < body.length) {
        val start = body.indexOf('{', index)
        if (start < 0) break
        val end = matchingBrace(body, start)
        if (end < 0) break
        val obj = body.substring(start, end + 1)
        val songId = jsonField(obj, "id").ifBlank { jsonField(obj, "songId") }
        if (songId.isNotBlank()) {
            guesses += StatsCreditGuess(
                songId = songId,
                artist = cleanStatsCredit(jsonField(obj, "artist"), album = false),
                album = cleanStatsCredit(jsonField(obj, "album"), album = true),
            )
        }
        index = end + 1
    }
    return guesses
}

internal fun cleanStatsCredit(value: String, album: Boolean): String {
    val trimmed = value.trim().removeSuffix(" - Topic").trim()
    if (trimmed.isBlank()) return ""
    if (trimmed.equals("Unknown Artist", ignoreCase = true)) return ""
    if (trimmed.equals("Unknown Album", ignoreCase = true)) return ""
    if (trimmed.equals("YouTube Music", ignoreCase = true)) return ""
    if (album && trimmed.contains("view", ignoreCase = true)) return ""
    return trimmed
}

private fun jsonField(obj: String, name: String): String {
    val pattern = Regex(""""$name"\s*:\s*"((?:\\.|[^"\\])*)"""")
    val raw = pattern.find(obj)?.groupValues?.getOrNull(1) ?: return ""
    return raw.replace("\\\"", "\"").replace("\\\\", "\\")
}

private fun matchingBrace(text: String, start: Int): Int {
    var depth = 0
    var inString = false
    var escaped = false
    for (index in start until text.length) {
        val char = text[index]
        if (escaped) {
            escaped = false
            continue
        }
        if (inString) {
            if (char == '\\') escaped = true
            else if (char == '"') inString = false
            continue
        }
        when (char) {
            '\\' -> escaped = true
            '"' -> inString = true
            '{' -> depth++
            '}' -> {
                depth--
                if (depth == 0) return index
            }
        }
    }
    return -1
}

/**
 * Fills artist and album names that YouTube did not provide, using the AI
 * provider the user already configured. Songs are asked once.
 */
@Singleton
class YoutubeStatsCreditAi @Inject constructor(
    private val aiHandler: AiHandler,
) {
    suspend fun fill(store: YoutubeMusicStore, playedSongIds: Set<String>, limit: Int = 20) {
        val gaps = store.creditGapsForAi(playedSongIds, limit)
        if (gaps.isEmpty()) return
        val response = runCatching {
            aiHandler.generateContent(
                prompt = statsCreditPrompt(gaps),
                type = AiSystemPromptType.STATS_CREDITS,
                temperature = 0.1f,
            )
        }.getOrElse { error ->
            store.releaseAiCreditGaps(gaps.map { it.songId }.toSet())
            val message = error.message.orEmpty()
            if (!message.contains("No API key", ignoreCase = true)) {
                Timber.w(error, "AI credits lookup failed")
            }
            return
        }
        val byId = parseStatsCredits(response).associateBy { it.songId }
        gaps.forEach { gap ->
            val guess = byId[gap.songId] ?: return@forEach
            val artist = gap.artist.ifBlank { guess.artist }
            val album = gap.album.ifBlank { guess.album }
            if (artist == gap.artist && album == gap.album) return@forEach
            store.rememberPlayback(
                songId = gap.songId,
                title = gap.title,
                artist = artist,
                album = album,
                artworkUrl = gap.artworkUrl,
                videoId = gap.videoId,
            )
        }
    }
}
