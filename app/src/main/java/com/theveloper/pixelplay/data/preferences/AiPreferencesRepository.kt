package com.theveloper.pixelplay.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
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
            You are Vibe-Engine, an expert music curator and audio DNA analyst for PixelPlayer.
            Your purpose is to analyze the user's listening profile, decode their musical DNA, and curate track sequences that resonate emotionally, flow naturally, and reveal new sonic territory.

            ## CORE PERSONA
            - You are equal parts data scientist and poet — you read numbers and feel the music behind them.
            - You speak to the listener's tastes through their own data: play counts, skip patterns, genre affinities, listening hours.
            - Your tone is sophisticated, warm, and deeply empathetic. You understand that music is personal.
            - You never recommend generically — every choice must be justified by the user's unique fingerprint.

            ## STRATEGY LAYERS

            ### 1. LISTENER SIGNAL DECODING
            - Parse the USER_PROFILE section to understand the listener's core DNA.
            - STATS: total plays vs unique songs = exploration depth. Low unique-to-play ratio = creature of habit. High = omnivorous explorer.
            - GENRES/ARTISTS: surface affinities. The top 3 genres + 5 artists = the listener's comfort zone.
            - PHASE: morning/afternoon/evening/night = when they listen most. Match energy to time-of-day context.
            - VAR (variety score): 0.0-1.0. Low (<0.3) = needs gentle discovery. High (>0.7) = ready for deep cuts.
            - LISTENED tracks: play_count (p), total_duration_mins (d), is_favorite (f). High p + f=1 = treasured. Low p = needs re-evaluation.

            ### 2. CURATION STRATEGY PER REQUEST TYPE
            For playlist/daily-mix requests, apply these heuristics:

            - "discovery/new/surprise me": prioritize the DISCOVERY_POOL (unplayed tracks). Pull from the user's blind spots — genres they listen to but specific songs/artists they haven't reached.
            - "favorites/best of/classics": heavily weight the LISTENED pool. Prioritize high-play-count tracks, favorites (f=1), and songs from top genres/artists.
            - "mood/vibe/energy" (e.g., "chill", "workout", "focus", "party"): cross-reference the user's phase and variety score. Morning commute = energetic but not overwhelming. Late night = atmospheric, introspective.
            - "genre/artist specific": dive deep into the requested genre/artist within the LIBRARY. If the user has limited material in that genre, blend in adjacent genres from their top affinities.
            - "mixed/eclectic/surprise": blend LISTENED and DISCOVERY intelligently. Create a journey with natural transitions — place familiar anchors between discovery tracks.

            ### 3. SEQUENCE ARCHITECTURE
            A great playlist is a journey, not a list:
            - OPENING (tracks 1-3): Establish the vibe. Familiar, high-energy or highly atmospheric tracks that set the tone.
            - BODY (tracks 4-~end-3): The narrative arc. Mix of familiar and discovery. Natural energy flow (build, peak, recover).
            - CLOSING (last 2-3): Resolution. Wind down energy or end on a memorable note. If the mood is "party", end strong. If "chill", fade gently.

            ### 4. OUTPUT RULES
            - You MUST respond with valid JSON — a flat array of song ID strings representing the playlist sequence.
            - DO NOT wrap the JSON in markdown code fences (```json).
            - DO NOT include ANY explanatory text before or after the JSON array.
            - Example valid response: ["song_abc123","song_def456","song_ghi789"]
            - If no songs match the request, return an empty array: []
            - Respect the target_length request. If the user asks for 10-15 tracks, the array should contain 10-15 IDs.
            - Songs may repeat across multiple playlists, but within a single playlist, each ID should appear at most once.
        """.trimIndent()

        const val DEFAULT_MAX_SONGS_FOR_CONTEXT = 50
        const val MIN_SONGS_FOR_CONTEXT = 5
        const val MAX_SONGS_FOR_CONTEXT = 500

        const val DEFAULT_LOCAL_MODEL_CONTEXT_SIZE = 100

        const val DEFAULT_CACHE_MAX_ENTRIES = 50
        const val MIN_CACHE_MAX_ENTRIES = 10
        const val MAX_CACHE_MAX_ENTRIES = 500

        const val DEFAULT_CACHE_TTL_HOURS = 24
        const val MIN_CACHE_TTL_HOURS = 1
        const val MAX_CACHE_TTL_HOURS = 720

        const val DEFAULT_LOCAL_MODEL_DOWNLOAD_TIMEOUT_MS = 300000
        const val DEFAULT_TEMPERATURE_MIN = 1
        const val DEFAULT_TEMPERATURE_MAX = 200
        const val DEFAULT_MAX_TOKENS_MIN = 128
        const val DEFAULT_MAX_TOKENS_MAX = 16000

        const val DEFAULT_TOP_K = 40
        const val DEFAULT_TOP_P = 95
        const val DEFAULT_REPETITION_PENALTY = 100
        const val DEFAULT_FREQUENCY_PENALTY = 0
        const val DEFAULT_PRESENCE_PENALTY = 0

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
        val LOCAL_ML_SELECTED_MODEL_ID = stringPreferencesKey("local_ml_selected_model_id")
        val LOCAL_ML_FALLBACK_TO_REMOTE = booleanPreferencesKey("local_ml_fallback_to_remote")
        val LOCAL_ML_USE_GPU = booleanPreferencesKey("local_ml_use_gpu")
        val LOCAL_ML_CONTEXT_SIZE = intPreferencesKey("local_ml_context_size")
        val LOCAL_ML_OLLAMA_URL = stringPreferencesKey("local_ml_ollama_url")
        val LOCAL_ML_HF_TOKEN = stringPreferencesKey("local_ml_hf_token")
        val LOCAL_MODEL_DOWNLOAD_TIMEOUT_MS = longPreferencesKey("local_model_download_timeout_ms")

        val AI_TEMPERATURE = intPreferencesKey("ai_temperature")
        val AI_MAX_TOKENS = intPreferencesKey("ai_max_tokens")
        val AI_ENABLE_STREAMING = booleanPreferencesKey("ai_enable_streaming")
        val AI_INCLUDE_CONTEXT = booleanPreferencesKey("ai_include_context")

        val AI_TOP_K = intPreferencesKey("ai_top_k")
        val AI_TOP_P = intPreferencesKey("ai_top_p")
        val AI_REPETITION_PENALTY = intPreferencesKey("ai_repetition_penalty")
        val AI_FREQUENCY_PENALTY = intPreferencesKey("ai_frequency_penalty")
        val AI_PRESENCE_PENALTY = intPreferencesKey("ai_presence_penalty")

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

        // Usage analytics
        val AI_USAGE_TOTAL_INPUT_TOKENS = longPreferencesKey("ai_usage_total_input_tokens")
        val AI_USAGE_TOTAL_OUTPUT_TOKENS = longPreferencesKey("ai_usage_total_output_tokens")
        val AI_USAGE_TOTAL_API_CALLS = longPreferencesKey("ai_usage_total_api_calls")
        val AI_USAGE_ESTIMATED_COST = stringPreferencesKey("ai_usage_estimated_cost")

        fun getApiKey(provider: AiProvider) = stringPreferencesKey("${provider.name.lowercase()}_api_key")
        fun getModel(provider: AiProvider) = stringPreferencesKey("${provider.name.lowercase()}_model")
        fun getSystemPrompt(provider: AiProvider) = stringPreferencesKey("${provider.name.lowercase()}_system_prompt")
        fun getProviderTimeout(provider: AiProvider) = longPreferencesKey("${provider.name.lowercase()}_timeout_ms")
        fun getPerModelTemperature(modelName: String) = intPreferencesKey("model_temp_${modelName.replace(" ", "_")}")
        fun getPerModelMaxTokens(modelName: String) = intPreferencesKey("model_tokens_${modelName.replace(" ", "_")}")
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
        dataStore.data.map { it[Keys.LOCAL_ML_OLLAMA_URL] ?: "https://ollama.ai/api" }

    val localMlHfToken: Flow<String> =
        dataStore.data.map { it[Keys.LOCAL_ML_HF_TOKEN] ?: "" }

    val aiTemperature: Flow<Int> =
        dataStore.data.map { it[Keys.AI_TEMPERATURE] ?: 70 }

    val aiMaxTokens: Flow<Int> =
        dataStore.data.map { it[Keys.AI_MAX_TOKENS] ?: 2048 }

    val aiEnableStreaming: Flow<Boolean> =
        dataStore.data.map { it[Keys.AI_ENABLE_STREAMING] ?: true }

    val aiIncludeContext: Flow<Boolean> =
        dataStore.data.map { it[Keys.AI_INCLUDE_CONTEXT] ?: true }

    val aiTopK: Flow<Int> =
        dataStore.data.map { it[Keys.AI_TOP_K] ?: DEFAULT_TOP_K }

    val aiTopP: Flow<Int> =
        dataStore.data.map { it[Keys.AI_TOP_P] ?: DEFAULT_TOP_P }

    val aiRepetitionPenalty: Flow<Int> =
        dataStore.data.map { it[Keys.AI_REPETITION_PENALTY] ?: DEFAULT_REPETITION_PENALTY }

    val aiFrequencyPenalty: Flow<Int> =
        dataStore.data.map { it[Keys.AI_FREQUENCY_PENALTY] ?: DEFAULT_FREQUENCY_PENALTY }

    val aiPresencePenalty: Flow<Int> =
        dataStore.data.map { it[Keys.AI_PRESENCE_PENALTY] ?: DEFAULT_PRESENCE_PENALTY }

    // ---- Granular behavioral telemetry ----

    val telemetryIncludeSkipCount: Flow<Boolean> =
        dataStore.data.map { it[Keys.TELEMETRY_INCLUDE_SKIP_COUNT] ?: false }

    val telemetryIncludeCompletionRate: Flow<Boolean> =
        dataStore.data.map { it[Keys.TELEMETRY_INCLUDE_COMPLETION_RATE] ?: false }

    val telemetryIncludeSessionDuration: Flow<Boolean> =
        dataStore.data.map { it[Keys.TELEMETRY_INCLUDE_SESSION_DURATION] ?: false }

    val telemetryIncludeTimeOfDay: Flow<Boolean> =
        dataStore.data.map { it[Keys.TELEMETRY_INCLUDE_TIME_OF_DAY] ?: false }

    val telemetryIncludeGenreAffinity: Flow<Boolean> =
        dataStore.data.map { it[Keys.TELEMETRY_INCLUDE_GENRE_AFFINITY] ?: false }

    val telemetryIncludeArtistAffinity: Flow<Boolean> =
        dataStore.data.map { it[Keys.TELEMETRY_INCLUDE_ARTIST_AFFINITY] ?: false }

    val telemetryIncludeReplayCount: Flow<Boolean> =
        dataStore.data.map { it[Keys.TELEMETRY_INCLUDE_REPLAY_COUNT] ?: false }

    val telemetryIncludeQueuePatterns: Flow<Boolean> =
        dataStore.data.map { it[Keys.TELEMETRY_INCLUDE_QUEUE_PATTERNS] ?: false }

    // ---- AI Cache settings ----

    val aiCacheEnabled: Flow<Boolean> =
        dataStore.data.map { it[Keys.AI_CACHE_ENABLED] ?: true }

    val aiCacheMaxEntries: Flow<Int> =
        dataStore.data.map { it[Keys.AI_CACHE_MAX_ENTRIES] ?: DEFAULT_CACHE_MAX_ENTRIES }

    val aiCacheTtlHours: Flow<Int> =
        dataStore.data.map { it[Keys.AI_CACHE_TTL_HOURS] ?: DEFAULT_CACHE_TTL_HOURS }

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

    val localModelDownloadTimeoutMs: Flow<Long> =
        dataStore.data.map { it[Keys.LOCAL_MODEL_DOWNLOAD_TIMEOUT_MS] ?: DEFAULT_LOCAL_MODEL_DOWNLOAD_TIMEOUT_MS.toLong() }

    val localMlSelectedModelId: Flow<String> =
        dataStore.data.map { it[Keys.LOCAL_ML_SELECTED_MODEL_ID] ?: "" }

    val aiUsageTotalInputTokens: Flow<Long> =
        dataStore.data.map { it[Keys.AI_USAGE_TOTAL_INPUT_TOKENS] ?: 0L }

    val aiUsageTotalOutputTokens: Flow<Long> =
        dataStore.data.map { it[Keys.AI_USAGE_TOTAL_OUTPUT_TOKENS] ?: 0L }

    val aiUsageTotalApiCalls: Flow<Long> =
        dataStore.data.map { it[Keys.AI_USAGE_TOTAL_API_CALLS] ?: 0L }

    val aiUsageEstimatedCost: Flow<String> =
        dataStore.data.map { it[Keys.AI_USAGE_ESTIMATED_COST] ?: "0.00" }

    fun getProviderTimeout(provider: AiProvider): Flow<Long> =
        dataStore.data.map { it[Keys.getProviderTimeout(provider)] ?: 60000L }

    fun getPerModelTemperature(modelName: String): Flow<Int?> =
        dataStore.data.map { it[Keys.getPerModelTemperature(modelName)] }

    fun getPerModelMaxTokens(modelName: String): Flow<Int?> =
        dataStore.data.map { it[Keys.getPerModelMaxTokens(modelName)] }

    // ---- Mutators ----

    suspend fun setAiProvider(provider: String) {
        dataStore.edit { it[Keys.AI_PROVIDER] = provider }
    }

    suspend fun setSafeTokenLimitEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.SAFE_TOKEN_LIMIT] = enabled }
    }

    suspend fun setMaxSongsForContext(maxSongs: Int) {
        dataStore.edit { it[Keys.MAX_SONGS_FOR_CONTEXT] = maxSongs.coerceIn(MIN_SONGS_FOR_CONTEXT, MAX_SONGS_FOR_CONTEXT) }
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

    suspend fun setAiTemperature(value: Int) { dataStore.edit { it[Keys.AI_TEMPERATURE] = value.coerceIn(1, 200) } }
    suspend fun setAiMaxTokens(value: Int) { dataStore.edit { it[Keys.AI_MAX_TOKENS] = value.coerceIn(128, 16000) } }
    suspend fun setAiEnableStreaming(enabled: Boolean) { dataStore.edit { it[Keys.AI_ENABLE_STREAMING] = enabled } }
    suspend fun setAiIncludeContext(enabled: Boolean) { dataStore.edit { it[Keys.AI_INCLUDE_CONTEXT] = enabled } }

    suspend fun setAiTopK(value: Int) { dataStore.edit { it[Keys.AI_TOP_K] = value.coerceIn(1, 100) } }
    suspend fun setAiTopP(value: Int) { dataStore.edit { it[Keys.AI_TOP_P] = value.coerceIn(1, 100) } }
    suspend fun setAiRepetitionPenalty(value: Int) { dataStore.edit { it[Keys.AI_REPETITION_PENALTY] = value.coerceIn(100, 200) } }
    suspend fun setAiFrequencyPenalty(value: Int) { dataStore.edit { it[Keys.AI_FREQUENCY_PENALTY] = value.coerceIn(-200, 200) } }
    suspend fun setAiPresencePenalty(value: Int) { dataStore.edit { it[Keys.AI_PRESENCE_PENALTY] = value.coerceIn(-200, 200) } }

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
    suspend fun setAiCacheMaxEntries(v: Int) { dataStore.edit { it[Keys.AI_CACHE_MAX_ENTRIES] = v.coerceIn(MIN_CACHE_MAX_ENTRIES, MAX_CACHE_MAX_ENTRIES) } }
    suspend fun setAiCacheTtlHours(v: Int) { dataStore.edit { it[Keys.AI_CACHE_TTL_HOURS] = v.coerceIn(MIN_CACHE_TTL_HOURS, MAX_CACHE_TTL_HOURS) } }
    suspend fun recordAiCacheCleared() { dataStore.edit { it[Keys.AI_CACHE_LAST_CLEAR_TS] = System.currentTimeMillis() } }

    // Backup mutators
    suspend fun setAiBackupIncludeUsageLogs(v: Boolean) { dataStore.edit { it[Keys.AI_BACKUP_INCLUDE_USAGE_LOGS] = v } }
    suspend fun setAiBackupIncludeCache(v: Boolean) { dataStore.edit { it[Keys.AI_BACKUP_INCLUDE_CACHE] = v } }
    suspend fun setAiBackupAutoExport(v: Boolean) { dataStore.edit { it[Keys.AI_BACKUP_AUTO_EXPORT] = v } }
    suspend fun recordAiBackupExport() { dataStore.edit { it[Keys.AI_BACKUP_LAST_EXPORT_TS] = System.currentTimeMillis() } }

    suspend fun setLocalModelDownloadTimeoutMs(timeoutMs: Long) {
        dataStore.edit { it[Keys.LOCAL_MODEL_DOWNLOAD_TIMEOUT_MS] = timeoutMs.coerceIn(10000, 3600000) }
    }

    suspend fun setLocalMlSelectedModelId(modelId: String) {
        dataStore.edit { it[Keys.LOCAL_ML_SELECTED_MODEL_ID] = modelId }
    }

    suspend fun setAiUsageTotalInputTokens(tokens: Long) {
        dataStore.edit { it[Keys.AI_USAGE_TOTAL_INPUT_TOKENS] = tokens }
    }

    suspend fun setAiUsageTotalOutputTokens(tokens: Long) {
        dataStore.edit { it[Keys.AI_USAGE_TOTAL_OUTPUT_TOKENS] = tokens }
    }

    suspend fun setAiUsageTotalApiCalls(calls: Long) {
        dataStore.edit { it[Keys.AI_USAGE_TOTAL_API_CALLS] = calls }
    }

    suspend fun incrementAiUsageMetrics(inputTokens: Int, outputTokens: Int) {
        dataStore.edit { prefs ->
            val currentInput = prefs[Keys.AI_USAGE_TOTAL_INPUT_TOKENS] ?: 0L
            val currentOutput = prefs[Keys.AI_USAGE_TOTAL_OUTPUT_TOKENS] ?: 0L
            val currentCalls = prefs[Keys.AI_USAGE_TOTAL_API_CALLS] ?: 0L
            prefs[Keys.AI_USAGE_TOTAL_INPUT_TOKENS] = currentInput + inputTokens
            prefs[Keys.AI_USAGE_TOTAL_OUTPUT_TOKENS] = currentOutput + outputTokens
            prefs[Keys.AI_USAGE_TOTAL_API_CALLS] = currentCalls + 1
        }
    }

    suspend fun setAiUsageEstimatedCost(cost: String) {
        dataStore.edit { it[Keys.AI_USAGE_ESTIMATED_COST] = cost }
    }

    suspend fun getAiProviderOnce(): String = aiProvider.first()
    suspend fun getAiTemperatureOnce(): Int = aiTemperature.first()
    suspend fun getAiMaxTokensOnce(): Int = aiMaxTokens.first()
    suspend fun getAiEnableStreamingOnce(): Boolean = aiEnableStreaming.first()
    suspend fun getAiIncludeContextOnce(): Boolean = aiIncludeContext.first()
    suspend fun getAiTopKOnce(): Int = aiTopK.first()
    suspend fun getAiTopPOnce(): Int = aiTopP.first()
    suspend fun getAiRepetitionPenaltyOnce(): Int = aiRepetitionPenalty.first()
    suspend fun getAiFrequencyPenaltyOnce(): Int = aiFrequencyPenalty.first()
    suspend fun getAiPresencePenaltyOnce(): Int = aiPresencePenalty.first()
    suspend fun getMaxSongsForContextOnce(): Int = maxSongsForContext.first()
    suspend fun getIncludeLikedSongsOnce(): Boolean = includeLikedSongs.first()
    suspend fun getIncludeDailyMixHistoryOnce(): Boolean = includeDailyMixHistory.first()
    suspend fun getIncludeUserHabitsOnce(): Boolean = includeUserHabits.first()
    suspend fun getAiCacheEnabledOnce(): Boolean = aiCacheEnabled.first()
    suspend fun getAiCacheMaxEntriesOnce(): Int = aiCacheMaxEntries.first()
    suspend fun getAiCacheTtlHoursOnce(): Int = aiCacheTtlHours.first()
    suspend fun getLocalMlEnabledOnce(): Boolean = localMlEnabled.first()
    suspend fun getLocalMlUseGpuOnce(): Boolean = localMlUseGpu.first()
    suspend fun getLocalMlFallbackToRemoteOnce(): Boolean = localMlFallbackToRemote.first()
    suspend fun getLocalMlContextSizeOnce(): Int = localMlContextSize.first()
    suspend fun getSafeTokenLimitOnce(): Boolean = isSafeTokenLimitEnabled.first()

    suspend fun clearAiUsageMetrics() {
        dataStore.edit { prefs ->
            prefs[Keys.AI_USAGE_TOTAL_INPUT_TOKENS] = 0L
            prefs[Keys.AI_USAGE_TOTAL_OUTPUT_TOKENS] = 0L
            prefs[Keys.AI_USAGE_TOTAL_API_CALLS] = 0L
            prefs[Keys.AI_USAGE_ESTIMATED_COST] = "0.00"
        }
    }

    suspend fun setProviderTimeout(provider: AiProvider, timeoutMs: Long) {
        dataStore.edit { it[Keys.getProviderTimeout(provider)] = timeoutMs.coerceIn(5000, 300000) }
    }

    suspend fun setPerModelTemperature(modelName: String, temperature: Int) {
        dataStore.edit { it[Keys.getPerModelTemperature(modelName)] = temperature.coerceIn(1, 200) }
    }

    suspend fun clearPerModelTemperature(modelName: String) {
        dataStore.edit { it.remove(Keys.getPerModelTemperature(modelName)) }
    }

    suspend fun setPerModelMaxTokens(modelName: String, maxTokens: Int) {
        dataStore.edit { it[Keys.getPerModelMaxTokens(modelName)] = maxTokens.coerceIn(128, 16000) }
    }

    suspend fun clearPerModelMaxTokens(modelName: String) {
        dataStore.edit { it.remove(Keys.getPerModelMaxTokens(modelName)) }
    }
}

