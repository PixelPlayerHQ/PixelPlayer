package com.theveloper.pixelplay.data.backup.format

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import timber.log.Timber
import com.theveloper.pixelplay.data.backup.model.BackupManifest
import com.theveloper.pixelplay.data.backup.model.BackupModuleInfo
import com.theveloper.pixelplay.data.backup.model.DeviceInfo
import com.theveloper.pixelplay.data.backup.module.PlaylistsModuleHandler
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Converts v1/v2 AppDataBackupPayload JSON into v3-compatible manifest + module map.
 */
@Singleton
class LegacyPayloadAdapter @Inject constructor() {

    /**
     * Adapts a legacy backup JSON (v1 or v2) into a v3 manifest + per-module JSON map.
     */
    fun adapt(legacyJson: String, gson: Gson): Pair<BackupManifest, Map<String, String>> {
        val root = JsonParser.parseString(legacyJson).asJsonObject
        val formatVersion = root.get("formatVersion")?.asInt ?: 1
        val exportedAt = root.get("exportedAtEpochMs")?.asLong ?: 0L
        val availableSections = root.getAsJsonArray("availableSections")
            ?.map { it.asString }?.toSet() ?: emptySet()

        val modules = mutableMapOf<String, String>()
        val modulesInfo = mutableMapOf<String, BackupModuleInfo>()

        // Handle preferences split for v1 (combined) vs v2 (separate)
        if (formatVersion == 1) {
            val preferences = root.getAsJsonArray("preferences")
            if (preferences != null && preferences.size() > 0) {
                val (playlistEntries, globalEntries) = splitLegacyPreferences(preferences)
                if (playlistEntries.size() > 0 && "playlists" in availableSections) {
                    val json = gson.toJson(playlistEntries)
                    modules["playlists"] = json
                    modulesInfo["playlists"] = buildModuleInfo(json)
                }
                if (globalEntries.size() > 0 && "global_settings" in availableSections) {
                    val json = gson.toJson(globalEntries)
                    modules["global_settings"] = json
                    modulesInfo["global_settings"] = buildModuleInfo(json)
                }
            }
        } else {
            extractJsonArrayModule(root, "playlists", "playlists", availableSections, modules, modulesInfo, gson)
            extractJsonArrayModule(root, "globalSettings", "global_settings", availableSections, modules, modulesInfo, gson)
        }

        extractJsonArrayModule(root, "favorites", "favorites", availableSections, modules, modulesInfo, gson)
        extractJsonArrayModule(root, "lyrics", "lyrics", availableSections, modules, modulesInfo, gson)
        extractJsonArrayModule(root, "searchHistory", "search_history", availableSections, modules, modulesInfo, gson)
        extractJsonArrayModule(root, "transitions", "transitions", availableSections, modules, modulesInfo, gson)
        extractJsonArrayModule(root, "engagementStats", "engagement_stats", availableSections, modules, modulesInfo, gson)
        extractJsonArrayModule(root, "playbackHistory", "playback_history", availableSections, modules, modulesInfo, gson)

        val manifest = BackupManifest(
            schemaVersion = formatVersion,
            appVersion = "legacy",
            appVersionCode = 0,
            createdAt = exportedAt,
            deviceInfo = DeviceInfo(),
            modules = modulesInfo
        )

        return manifest to modules
    }

    private fun splitLegacyPreferences(preferences: JsonArray): Pair<JsonArray, JsonArray> {
        val playlistEntries = JsonArray()
        val globalEntries = JsonArray()
        preferences.forEach { element ->
            val obj = element.asJsonObject
            val key = obj.get("key")?.asString ?: ""
            if (key in PlaylistsModuleHandler.PLAYLIST_KEYS) {
                playlistEntries.add(element)
            } else {
                globalEntries.add(element)
            }
        }
        return playlistEntries to globalEntries
    }

    private fun extractJsonArrayModule(
        root: JsonObject,
        jsonField: String,
        moduleKey: String,
        availableSections: Set<String>,
        modules: MutableMap<String, String>,
        modulesInfo: MutableMap<String, BackupModuleInfo>,
        gson: Gson
    ) {
        val array = root.getAsJsonArray(jsonField)
        if (array != null && array.size() > 0 && moduleKey in availableSections) {
            val json = gson.toJson(array)
            modules[moduleKey] = json
            modulesInfo[moduleKey] = buildModuleInfo(json)
        }
    }

    private fun buildModuleInfo(json: String): BackupModuleInfo {
        val bytes = json.toByteArray(Charsets.UTF_8)
        return BackupModuleInfo(
            checksum = "sha256:${sha256(bytes)}",
            entryCount = countEntries(json),
            sizeBytes = bytes.size.toLong()
        )
    }

    private fun countEntries(json: String): Int {
        return try {
            val trimmed = json.trim()
            if (trimmed.startsWith("[")) {
                JsonParser.parseString(trimmed).asJsonArray.size()
            } else {
                1
            }
        } catch (e: Exception) { Timber.w(e, "Failed to count legacy backup entries"); 0 }
    }

    private fun sha256(data: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(data).joinToString("") { "%02x".format(it) }
    }
}
