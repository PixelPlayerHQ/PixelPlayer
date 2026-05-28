package com.theveloper.pixelplay.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import com.theveloper.pixelplay.data.ai.provider.AiProvider
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AiPreferencesRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    companion object {
        val DEFAULT_SYSTEM_PROMPT = """
            You are 'Vibe-Engine', a professional music curator.
            Analyze the user's request and listening profile to provide perfect music recommendations.
            Always prioritize flow, emotional resonance, and discovery.
        """.trimIndent()

        val DEFAULT_DEEPSEEK_SYSTEM_PROMPT = DEFAULT_SYSTEM_PROMPT
        val DEFAULT_GROQ_SYSTEM_PROMPT = DEFAULT_SYSTEM_PROMPT
        val DEFAULT_MISTRAL_SYSTEM_PROMPT = DEFAULT_SYSTEM_PROMPT
        val DEFAULT_NVIDIA_SYSTEM_PROMPT = DEFAULT_SYSTEM_PROMPT
        val DEFAULT_KIMI_SYSTEM_PROMPT = DEFAULT_SYSTEM_PROMPT
        val DEFAULT_GLM_SYSTEM_PROMPT = DEFAULT_SYSTEM_PROMPT
        val DEFAULT_OPENAI_SYSTEM_PROMPT = DEFAULT_SYSTEM_PROMPT
        val DEFAULT_OPENROUTER_SYSTEM_PROMPT = DEFAULT_SYSTEM_PROMPT
        val DEFAULT_ANTHROPIC_SYSTEM_PROMPT = DEFAULT_SYSTEM_PROMPT
        val DEFAULT_OLLAMA_SYSTEM_PROMPT = DEFAULT_SYSTEM_PROMPT

        const val DEFAULT_MAX_SONGS_FOR_CONTEXT = 50
        const val DEFAULT_LOCAL_MODEL_CONTEXT_SIZE = 100
    }

    private object Keys {
        val AI_PROVIDER = stringPreferencesKey("ai_provider")
        val SAFE_TOKEN_LIMIT = booleanPreferencesKey("safe_token_limit")

        // AI Preferences for data sharing
        val MAX_SONGS_FOR_CONTEXT = intPreferencesKey("max_songs_for_context")
        val INCLUDE_LIKED_SONGS = booleanPreferencesKey("include_liked_songs")
        val INCLUDE_DAILY_MIX_HISTORY = booleanPreferencesKey("include_daily_mix_history")
        val INCLUDE_USER_HABITS = booleanPreferencesKey("include_user_habits")

        // Local model configuration
        val LOCAL_ML_ENABLED = booleanPreferencesKey("local_ml_enabled")
        val LOCAL_ML_ACTIVE_MODEL_ID = stringPreferencesKey("local_ml_active_model_id")
        val LOCAL_ML_FALLBACK_TO_REMOTE = booleanPreferencesKey("local_ml_fallback_to_remote")
        val LOCAL_ML_USE_GPU = booleanPreferencesKey("local_ml_use_gpu")
        val LOCAL_ML_CONTEXT_SIZE = intPreferencesKey("local_ml_context_size")
        val LOCAL_ML_OLLAMA_URL = stringPreferencesKey("local_ml_ollama_url")
        val LOCAL_ML_HF_TOKEN = stringPreferencesKey("local_ml_hf_token")

        // Granular behavioral telemetry
        val TELEMETRY_INCLUDE_SKIP_COUNT = booleanPreferencesKey("telemetry_include_skip_count")
        val TELEMETRY_INCLUDE_COMPLETION_RATE = booleanPreferencesKey("telemetry_include_completion_rate")
        val TELEMETRY_INCLUDE_SESSION_DURATION = booleanPreferencesKey("telemetry_include_session_duration")
        val TELEMETRY_INCLUDE_TIME_OF_DAY = booleanPreferencesKey("telemetry_include_time_of_day")
        val TELEMETRY_INCLUDE_GENRE_AFFINITY = booleanPreferencesKey("telemetry_include_genre_affinity")
        val TELEMETRY_INCLUDE_ARTIST_AFFINITY = booleanPreferencesKey("telemetry_include_artist_affinity")
        val TELEMETRY_INCLUDE_REPLAY_COUNT = booleanPreferencesKey("telemetry_include_replay_count")
        val TELEMETRY_INCLUDE_QUEUE_PATTERNS = booleanPreferencesKey("telemetry_include_queue_patterns")

        // AI Cache
        val AI_CACHE_ENABLED = booleanPreferencesKey("ai_cache_enabled")
        val AI_CACHE_MAX_ENTRIES = intPreferencesKey("ai_cache_max_entries")
        val AI_CACHE_TTL_HOURS = intPreferencesKey("ai_cache_ttl_hours")
        val AI_CACHE_LAST_CLEAR_TS = longPreferencesKey("ai_cache_last_clear_ts")

        // Backup/export
        val AI_BACKUP_INCLUDE_USAGE_LOGS = booleanPreferencesKey("ai_backup_include_usage_logs")
        val AI_BACKUP_INCLUDE_CACHE = booleanPreferencesKey("ai_backup_include_cache")
        val AI_BACKUP_AUTO_EXPORT = booleanPreferencesKey("ai_backup_auto_export")
        val AI_BACKUP_LAST_EXPORT_TS = longPreferencesKey("ai_backup_last_export_ts")

        fun getApiKey(provider: AiProvider) = stringPreferencesKey("${provider.name.lowercase()}_api_key")
        fun getModel(provider: AiProvider) = stringPreferencesKey("${provider.name.lowercase()}_model")
        fun getSystemPrompt(provider: AiProvider) = stringPreferencesKey("${provider.name.lowercase()}_system_prompt")
    }

    // Generic accessors for AiHandler
    fun getApiKey(provider: AiProvider): Flow<String> =
        dataStore.data.map { preferences -> preferences[Keys.getApiKey(provider)]?.trim() ?: "" }

    fun getModel(provider: AiProvider): Flow<String> =
        dataStore.data.map { preferences -> preferences[Keys.getModel(provider)] ?: "" }

    fun getSystemPrompt(provider: AiProvider): Flow<String> =
        dataStore.data.map { preferences ->
            preferences[Keys.getSystemPrompt(provider)] ?: DEFAULT_SYSTEM_PROMPT
        }

    suspend fun setApiKey(provider: AiProvider, apiKey: String) {
        dataStore.edit { preferences -> preferences[Keys.getApiKey(provider)] = apiKey.trim() }
    }

    suspend fun setModel(provider: AiProvider, model: String) {
        dataStore.edit { preferences -> preferences[Keys.getModel(provider)] = model }
    }

    suspend fun setSystemPrompt(provider: AiProvider, prompt: String) {
        dataStore.edit { preferences -> preferences[Keys.getSystemPrompt(provider)] = prompt }
    }

    suspend fun resetSystemPrompt(provider: AiProvider) {
        dataStore.edit { preferences ->
            preferences[Keys.getSystemPrompt(provider)] = DEFAULT_SYSTEM_PROMPT
        }
    }

    // Convenience properties for legacy compatibility
    val geminiApiKey: Flow<String> = getApiKey(AiProvider.GEMINI)
    val geminiModel: Flow<String> = getModel(AiProvider.GEMINI)
    val geminiSystemPrompt: Flow<String> = getSystemPrompt(AiProvider.GEMINI)

    val deepseekApiKey: Flow<String> = getApiKey(AiProvider.DEEPSEEK)
    val deepseekModel: Flow<String> = getModel(AiProvider.DEEPSEEK)
    val deepseekSystemPrompt: Flow<String> = getSystemPrompt(AiProvider.DEEPSEEK)

    val groqApiKey: Flow<String> = getApiKey(AiProvider.GROQ)
    val groqModel: Flow<String> = getModel(AiProvider.GROQ)
    val groqSystemPrompt: Flow<String> = getSystemPrompt(AiProvider.GROQ)

    val mistralApiKey: Flow<String> = getApiKey(AiProvider.MISTRAL)
    val mistralModel: Flow<String> = getModel(AiProvider.MISTRAL)
    val mistralSystemPrompt: Flow<String> = getSystemPrompt(AiProvider.MISTRAL)

    val nvidiaApiKey: Flow<String> = getApiKey(AiProvider.NVIDIA)
    val nvidiaModel: Flow<String> = getModel(AiProvider.NVIDIA)
    val nvidiaSystemPrompt: Flow<String> = getSystemPrompt(AiProvider.NVIDIA)

    val kimiApiKey: Flow<String> = getApiKey(AiProvider.KIMI)
    val kimiModel: Flow<String> = getModel(AiProvider.KIMI)
    val kimiSystemPrompt: Flow<String> = getSystemPrompt(AiProvider.KIMI)

    val glmApiKey: Flow<String> = getApiKey(AiProvider.GLM)
    val glmModel: Flow<String> = getModel(AiProvider.KIMI)
    val glmSystemPrompt: Flow<String> = getSystemPrompt(AiProvider.GLM)

    val openaiApiKey: Flow<String> = getApiKey(AiProvider.OPENAI)
    val openaiModel: Flow<String> = getModel(AiProvider.OPENAI)
    val openaiSystemPrompt: Flow<String> = getSystemPrompt(AiProvider.OPENAI)

    val openrouterApiKey: Flow<String> = getApiKey(AiProvider.OPENROUTER)
    val openrouterModel: Flow<String> = getModel(AiProvider.OPENROUTER)
    val openrouterSystemPrompt: Flow<String> = getSystemPrompt(AiProvider.OPENROUTER)

    val anthropicApiKey: Flow<String> = getApiKey(AiProvider.ANTHROPIC)
    val anthropicModel: Flow<String> = getModel(AiProvider.ANTHROPIC)
    val anthropicSystemPrompt: Flow<String> = getSystemPrompt(AiProvider.ANTHROPIC)

    val ollamaApiKey: Flow<String> = getApiKey(AiProvider.OLLAMA)
    val ollamaModel: Flow<String> = getModel(AiProvider.OLLAMA)
    val ollamaSystemPrompt: Flow<String> = getSystemPrompt(AiProvider.OLLAMA)

    val aiProvider: Flow<String> =
        dataStore.data.map { preferences -> preferences[Keys.AI_PROVIDER] ?: "GEMINI" }

    val isSafeTokenLimitEnabled: Flow<Boolean> =
        dataStore.data.map { preferences -> preferences[Keys.SAFE_TOKEN_LIMIT] ?: true }

    // AI Data Preferences
    val maxSongsForContext: Flow<Int> =
        dataStore.data.map { preferences -> preferences[Keys.MAX_SONGS_FOR_CONTEXT] ?: DEFAULT_MAX_SONGS_FOR_CONTEXT }

    val includeLikedSongs: Flow<Boolean> =
        dataStore.data.map { preferences -> preferences[Keys.INCLUDE_LIKED_SONGS] ?: true }

    val includeDailyMixHistory: Flow<Boolean> =
        dataStore.data.map { preferences -> preferences[Keys.INCLUDE_DAILY_MIX_HISTORY] ?: true }

    val includeUserHabits: Flow<Boolean> =
        dataStore.data.map { preferences -> preferences[Keys.INCLUDE_USER_HABITS] ?: true }

    // ---- Local ML Model settings ----

    val localMlEnabled: Flow<Boolean> =
        dataStore.data.map { it[Keys.LOCAL_ML_ENABLED] ?: false }

    val localMlActiveModelId: Flow<String> =
        dataStore.data.map { it[Keys.LOCAL_ML_ACTIVE_MODEL_ID] ?: "" }

    val localMlFallbackToRemote: Flow<Boolean> =
        dataStore.data.map { it[Keys.LOCAL_ML_FALLBACK_TO_REMOTE] ?: true }

    val localMlUseGpu: Flow<Boolean> =
        dataStore.data.map { it[Keys.LOCAL_ML_USE_GPU] ?: false }

    val localMlContextSize: Flow<Int> =
        dataStore.data.map { it[Keys.LOCAL_ML_CONTEXT_SIZE] ?: DEFAULT_LOCAL_MODEL_CONTEXT_SIZE }

    val localMlOllamaUrl: Flow<String> =
        dataStore.data.map { it[Keys.LOCAL_ML_OLLAMA_URL] ?: "http://localhost:11434" }

    val localMlHfToken: Flow<String> =
        dataStore.data.map { it[Keys.LOCAL_ML_HF_TOKEN] ?: "" }

    // ---- Granular behavioral telemetry ----

    val telemetryIncludeSkipCount: Flow<Boolean> =
        dataStore.data.map { it[Keys.TELEMETRY_INCLUDE_SKIP_COUNT] ?: true }

    val telemetryIncludeCompletionRate: Flow<Boolean> =
        dataStore.data.map { it[Keys.TELEMETRY_INCLUDE_COMPLETION_RATE] ?: true }

    val telemetryIncludeSessionDuration: Flow<Boolean> =
        dataStore.data.map { it[Keys.TELEMETRY_INCLUDE_SESSION_DURATION] ?: true }

    val telemetryIncludeTimeOfDay: Flow<Boolean> =
        dataStore.data.map { it[Keys.TELEMETRY_INCLUDE_TIME_OF_DAY] ?: true }

    val telemetryIncludeGenreAffinity: Flow<Boolean> =
        dataStore.data.map { it[Keys.TELEMETRY_INCLUDE_GENRE_AFFINITY] ?: true }

    val telemetryIncludeArtistAffinity: Flow<Boolean> =
        dataStore.data.map { it[Keys.TELEMETRY_INCLUDE_ARTIST_AFFINITY] ?: true }

    val telemetryIncludeReplayCount: Flow<Boolean> =
        dataStore.data.map { it[Keys.TELEMETRY_INCLUDE_REPLAY_COUNT] ?: true }

    val telemetryIncludeQueuePatterns: Flow<Boolean> =
        dataStore.data.map { it[Keys.TELEMETRY_INCLUDE_QUEUE_PATTERNS] ?: false }

    // ---- AI Cache settings ----

    val aiCacheEnabled: Flow<Boolean> =
        dataStore.data.map { it[Keys.AI_CACHE_ENABLED] ?: true }

    val aiCacheMaxEntries: Flow<Int> =
        dataStore.data.map { it[Keys.AI_CACHE_MAX_ENTRIES] ?: 50 }

    val aiCacheTtlHours: Flow<Int> =
        dataStore.data.map { it[Keys.AI_CACHE_TTL_HOURS] ?: 24 }

    val aiCacheLastClearTs: Flow<Long> =
        dataStore.data.map { it[Keys.AI_CACHE_LAST_CLEAR_TS] ?: 0L }

    // ---- Backup settings ----

    val aiBackupIncludeUsageLogs: Flow<Boolean> =
        dataStore.data.map { it[Keys.AI_BACKUP_INCLUDE_USAGE_LOGS] ?: true }

    val aiBackupIncludeCache: Flow<Boolean> =
        dataStore.data.map { it[Keys.AI_BACKUP_INCLUDE_CACHE] ?: false }

    val aiBackupAutoExport: Flow<Boolean> =
        dataStore.data.map { it[Keys.AI_BACKUP_AUTO_EXPORT] ?: false }

    val aiBackupLastExportTs: Flow<Long> =
        dataStore.data.map { it[Keys.AI_BACKUP_LAST_EXPORT_TS] ?: 0L }

    // ---- Mutators ----

    suspend fun setAiProvider(provider: String) {
        dataStore.edit { it[Keys.AI_PROVIDER] = provider }
    }

    suspend fun setSafeTokenLimitEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.SAFE_TOKEN_LIMIT] = enabled }
    }

    suspend fun setMaxSongsForContext(maxSongs: Int) {
        dataStore.edit { it[Keys.MAX_SONGS_FOR_CONTEXT] = maxSongs }
    }

    suspend fun setIncludeLikedSongs(include: Boolean) {
        dataStore.edit { it[Keys.INCLUDE_LIKED_SONGS] = include }
    }

    suspend fun setIncludeDailyMixHistory(include: Boolean) {
        dataStore.edit { it[Keys.INCLUDE_DAILY_MIX_HISTORY] = include }
    }

    suspend fun setIncludeUserHabits(include: Boolean) {
        dataStore.edit { it[Keys.INCLUDE_USER_HABITS] = include }
    }

    // Local ML mutators
    suspend fun setLocalMlEnabled(enabled: Boolean) { dataStore.edit { it[Keys.LOCAL_ML_ENABLED] = enabled } }
    suspend fun setLocalMlActiveModelId(id: String) { dataStore.edit { it[Keys.LOCAL_ML_ACTIVE_MODEL_ID] = id } }
    suspend fun setLocalMlFallbackToRemote(fallback: Boolean) { dataStore.edit { it[Keys.LOCAL_ML_FALLBACK_TO_REMOTE] = fallback } }
    suspend fun setLocalMlUseGpu(useGpu: Boolean) { dataStore.edit { it[Keys.LOCAL_ML_USE_GPU] = useGpu } }
    suspend fun setLocalMlContextSize(size: Int) { dataStore.edit { it[Keys.LOCAL_ML_CONTEXT_SIZE] = size } }
    suspend fun setLocalMlOllamaUrl(url: String) { dataStore.edit { it[Keys.LOCAL_ML_OLLAMA_URL] = url } }
    suspend fun setLocalMlHfToken(token: String) { dataStore.edit { it[Keys.LOCAL_ML_HF_TOKEN] = token } }

    // Telemetry mutators
    suspend fun setTelemetryIncludeSkipCount(v: Boolean) { dataStore.edit { it[Keys.TELEMETRY_INCLUDE_SKIP_COUNT] = v } }
    suspend fun setTelemetryIncludeCompletionRate(v: Boolean) { dataStore.edit { it[Keys.TELEMETRY_INCLUDE_COMPLETION_RATE] = v } }
    suspend fun setTelemetryIncludeSessionDuration(v: Boolean) { dataStore.edit { it[Keys.TELEMETRY_INCLUDE_SESSION_DURATION] = v } }
    suspend fun setTelemetryIncludeTimeOfDay(v: Boolean) { dataStore.edit { it[Keys.TELEMETRY_INCLUDE_TIME_OF_DAY] = v } }
    suspend fun setTelemetryIncludeGenreAffinity(v: Boolean) { dataStore.edit { it[Keys.TELEMETRY_INCLUDE_GENRE_AFFINITY] = v } }
    suspend fun setTelemetryIncludeArtistAffinity(v: Boolean) { dataStore.edit { it[Keys.TELEMETRY_INCLUDE_ARTIST_AFFINITY] = v } }
    suspend fun setTelemetryIncludeReplayCount(v: Boolean) { dataStore.edit { it[Keys.TELEMETRY_INCLUDE_REPLAY_COUNT] = v } }
    suspend fun setTelemetryIncludeQueuePatterns(v: Boolean) { dataStore.edit { it[Keys.TELEMETRY_INCLUDE_QUEUE_PATTERNS] = v } }

    // Cache mutators
    suspend fun setAiCacheEnabled(v: Boolean) { dataStore.edit { it[Keys.AI_CACHE_ENABLED] = v } }
    suspend fun setAiCacheMaxEntries(v: Int) { dataStore.edit { it[Keys.AI_CACHE_MAX_ENTRIES] = v } }
    suspend fun setAiCacheTtlHours(v: Int) { dataStore.edit { it[Keys.AI_CACHE_TTL_HOURS] = v } }
    suspend fun recordAiCacheCleared() { dataStore.edit { it[Keys.AI_CACHE_LAST_CLEAR_TS] = System.currentTimeMillis() } }

    // Backup mutators
    suspend fun setAiBackupIncludeUsageLogs(v: Boolean) { dataStore.edit { it[Keys.AI_BACKUP_INCLUDE_USAGE_LOGS] = v } }
    suspend fun setAiBackupIncludeCache(v: Boolean) { dataStore.edit { it[Keys.AI_BACKUP_INCLUDE_CACHE] = v } }
    suspend fun setAiBackupAutoExport(v: Boolean) { dataStore.edit { it[Keys.AI_BACKUP_AUTO_EXPORT] = v } }
    suspend fun recordAiBackupExport() { dataStore.edit { it[Keys.AI_BACKUP_LAST_EXPORT_TS] = System.currentTimeMillis() } }
}

