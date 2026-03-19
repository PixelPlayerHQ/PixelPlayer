package com.theveloper.pixelplay.data.backup.module

import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.theveloper.pixelplay.data.backup.model.BackupSection
import com.theveloper.pixelplay.data.database.EngagementDao
import com.theveloper.pixelplay.data.database.SongEngagementEntity
import com.theveloper.pixelplay.di.BackupGson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EngagementStatsModuleHandler @Inject constructor(
    private val engagementDao: EngagementDao,
    @BackupGson private val gson: Gson
) : BackupModuleHandler {

    override val section = BackupSection.ENGAGEMENT_STATS

    override suspend fun export(): String = withContext(Dispatchers.IO) {
        gson.toJson(engagementDao.getAllEngagements())
    }

    override suspend fun countEntries(): Int = withContext(Dispatchers.IO) {
        engagementDao.getAllEngagements().size
    }

    override suspend fun snapshot(): String = export()

    override suspend fun restore(payload: String) = withContext(Dispatchers.IO) {
        val parsed = JsonParser.parseString(payload)
        require(parsed.isJsonArray) { "Engagement stats payload must be a JSON array." }

        val sourceEntries = parsed.asJsonArray.size()
        val stats = parseEntries(parsed.asJsonArray)
        if (sourceEntries > 0 && stats.isEmpty()) {
            throw IllegalArgumentException("Engagement stats backup does not contain any valid entries.")
        }

        engagementDao.replaceAll(stats)
    }

    override suspend fun rollback(snapshot: String) = restore(snapshot)

    private fun parseEntries(array: com.google.gson.JsonArray): List<SongEngagementEntity> {
        val merged = linkedMapOf<String, SongEngagementEntity>()
        array.forEach { element ->
            val entry = parseEntry(element) ?: return@forEach
            merged.merge(entry.songId, entry, ::mergeEntries)
        }
        return merged.values.toList()
    }

    private fun parseEntry(element: JsonElement): SongEngagementEntity? {
        if (!element.isJsonObject) return null

        val obj = element.asJsonObject
        val songId = readString(obj, "songId", "song_id")
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: return null

        return SongEngagementEntity(
            songId = songId,
            playCount = (readInt(obj, "playCount", "play_count", "score", "plays") ?: 0).coerceAtLeast(0),
            totalPlayDurationMs = (
                readLong(
                    obj,
                    "totalPlayDurationMs",
                    "total_play_duration_ms",
                    "totalDuration",
                    "total_duration",
                    "durationMs",
                    "duration_ms"
                ) ?: 0L
            ).coerceAtLeast(0L),
            lastPlayedTimestamp = (
                readLong(
                    obj,
                    "lastPlayedTimestamp",
                    "last_played_timestamp",
                    "lastPlayedAt",
                    "last_played_at",
                    "timestamp"
                ) ?: 0L
            ).coerceAtLeast(0L)
        )
    }

    private fun mergeEntries(
        existing: SongEngagementEntity,
        incoming: SongEngagementEntity
    ): SongEngagementEntity {
        return SongEngagementEntity(
            songId = existing.songId,
            playCount = maxOf(existing.playCount, incoming.playCount),
            totalPlayDurationMs = maxOf(existing.totalPlayDurationMs, incoming.totalPlayDurationMs),
            lastPlayedTimestamp = maxOf(existing.lastPlayedTimestamp, incoming.lastPlayedTimestamp)
        )
    }

    private fun readString(obj: JsonObject, vararg keys: String): String? {
        return keys.asSequence()
            .mapNotNull { key ->
                obj.get(key)
                    ?.takeIf { it.isJsonPrimitive }
                    ?.asString
            }
            .firstOrNull()
    }

    private fun readInt(obj: JsonObject, vararg keys: String): Int? {
        return keys.asSequence()
            .mapNotNull { key -> readLongValue(obj.get(key))?.toInt() }
            .firstOrNull()
    }

    private fun readLong(obj: JsonObject, vararg keys: String): Long? {
        return keys.asSequence()
            .mapNotNull { key -> readLongValue(obj.get(key)) }
            .firstOrNull()
    }

    private fun readLongValue(element: JsonElement?): Long? {
        val primitive = element?.takeIf { it.isJsonPrimitive }?.asJsonPrimitive ?: return null
        return when {
            primitive.isNumber -> primitive.asNumber.toLong()
            primitive.isString -> primitive.asString.toLongOrNull()
            else -> null
        }
    }
}
