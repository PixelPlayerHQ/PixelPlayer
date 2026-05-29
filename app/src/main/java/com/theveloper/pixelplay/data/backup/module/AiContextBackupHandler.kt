package com.theveloper.pixelplay.data.backup.module

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.theveloper.pixelplay.data.backup.model.BackupSection
import com.theveloper.pixelplay.data.preferences.AiPreferencesRepository
import com.theveloper.pixelplay.di.BackupGson
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AiContextBackupHandler @Inject constructor(
    private val aiPreferencesRepository: AiPreferencesRepository,
    @BackupGson private val gson: Gson
) : BackupModuleHandler {
    override val section: BackupSection = BackupSection.AI_CONTEXT

    override suspend fun export(): String {
        val context = AiContextData(
            provider = aiPreferencesRepository.getAiProviderOnce(),
            temperature = aiPreferencesRepository.getAiTemperatureOnce(),
            maxTokens = aiPreferencesRepository.getAiMaxTokensOnce(),
            enableStreaming = aiPreferencesRepository.getAiEnableStreamingOnce(),
            includeContext = aiPreferencesRepository.getAiIncludeContextOnce(),
            maxSongsForContext = aiPreferencesRepository.getMaxSongsForContextOnce(),
            includeLikedSongs = aiPreferencesRepository.getIncludeLikedSongsOnce(),
            includeDailyMixHistory = aiPreferencesRepository.getIncludeDailyMixHistoryOnce(),
            includeUserHabits = aiPreferencesRepository.getIncludeUserHabitsOnce(),
            cacheEnabled = aiPreferencesRepository.getAiCacheEnabledOnce(),
            cacheMaxEntries = aiPreferencesRepository.getAiCacheMaxEntriesOnce(),
            cacheTtlHours = aiPreferencesRepository.getAiCacheTtlHoursOnce(),
            localMlEnabled = aiPreferencesRepository.getLocalMlEnabledOnce(),
            localMlUseGpu = aiPreferencesRepository.getLocalMlUseGpuOnce(),
            localMlFallbackToRemote = aiPreferencesRepository.getLocalMlFallbackToRemoteOnce(),
            localMlContextSize = aiPreferencesRepository.getLocalMlContextSizeOnce(),
            safeTokenLimit = aiPreferencesRepository.getSafeTokenLimitOnce()
        )
        return gson.toJson(context)
    }

    override suspend fun countEntries(): Int = 1

    override suspend fun snapshot(): String = export()

    override suspend fun restore(payload: String) {
        val type = object : TypeToken<AiContextData>() {}.type
        val context: AiContextData = gson.fromJson(payload, type)
        context.restore(aiPreferencesRepository)
    }

    override suspend fun rollback(snapshot: String) {
        restore(snapshot)
    }

    data class AiContextData(
        val provider: String? = null,
        val temperature: Int? = null,
        val maxTokens: Int? = null,
        val enableStreaming: Boolean? = null,
        val includeContext: Boolean? = null,
        val maxSongsForContext: Int? = null,
        val includeLikedSongs: Boolean? = null,
        val includeDailyMixHistory: Boolean? = null,
        val includeUserHabits: Boolean? = null,
        val cacheEnabled: Boolean? = null,
        val cacheMaxEntries: Int? = null,
        val cacheTtlHours: Int? = null,
        val localMlEnabled: Boolean? = null,
        val localMlUseGpu: Boolean? = null,
        val localMlFallbackToRemote: Boolean? = null,
        val localMlContextSize: Int? = null,
        val safeTokenLimit: Boolean? = null
    ) {
        suspend fun restore(repo: AiPreferencesRepository) {
            provider?.let { repo.setAiProvider(it) }
            temperature?.let { repo.setAiTemperature(it) }
            maxTokens?.let { repo.setAiMaxTokens(it) }
            enableStreaming?.let { repo.setAiEnableStreaming(it) }
            includeContext?.let { repo.setAiIncludeContext(it) }
            maxSongsForContext?.let { repo.setMaxSongsForContext(it) }
            includeLikedSongs?.let { repo.setIncludeLikedSongs(it) }
            includeDailyMixHistory?.let { repo.setIncludeDailyMixHistory(it) }
            includeUserHabits?.let { repo.setIncludeUserHabits(it) }
            cacheEnabled?.let { repo.setAiCacheEnabled(it) }
            cacheMaxEntries?.let { repo.setAiCacheMaxEntries(it) }
            cacheTtlHours?.let { repo.setAiCacheTtlHours(it) }
            localMlEnabled?.let { repo.setLocalMlEnabled(it) }
            localMlUseGpu?.let { repo.setLocalMlUseGpu(it) }
            localMlFallbackToRemote?.let { repo.setLocalMlFallbackToRemote(it) }
            localMlContextSize?.let { repo.setLocalMlContextSize(it) }
            safeTokenLimit?.let { repo.setSafeTokenLimitEnabled(it) }
        }
    }
}
