@file:Suppress("DEPRECATION")
package com.theveloper.pixelplay.data.preferences

import android.content.Context
import android.content.SharedPreferences
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.theveloper.pixelplay.data.ai.provider.AiProvider
import com.theveloper.pixelplay.di.AppScope
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.EnumMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AiPreferencesRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    @ApplicationContext private val context: Context,
    @AppScope private val appScope: CoroutineScope
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

        private const val ENCRYPTED_PREFS_NAME = "ai_prefs"
        private const val MIGRATION_DONE_KEY = "__migration_done_v1"
    }

    private object Keys {
        val AI_PROVIDER = stringPreferencesKey("ai_provider")
        val SAFE_TOKEN_LIMIT = booleanPreferencesKey("safe_token_limit")

        // Legacy plain DataStore key — only read from during migration.
        fun getLegacyApiKey(provider: AiProvider) =
            stringPreferencesKey("${provider.name.lowercase()}_api_key")
        fun getModel(provider: AiProvider) = stringPreferencesKey("${provider.name.lowercase()}_model")
        fun getSystemPrompt(provider: AiProvider) = stringPreferencesKey("${provider.name.lowercase()}_system_prompt")
    }

    // AI provider API keys are bearer credentials with real billing exposure;
    // we move them out of plain DataStore into EncryptedSharedPreferences
    // (AES256-GCM, key in AndroidKeystore) to match the pattern used by the
    // Jellyfin/Navidrome/GDrive/NetEase/QQ Music repositories. The fallback
    // to a plain SharedPreferences file mirrors the same Keystore-failure
    // behavior; both that file and the encrypted one are excluded from
    // backup_rules.xml and data_extraction_rules.xml.
    private val encryptedPrefs: SharedPreferences = try {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            ENCRYPTED_PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Exception) {
        Timber.e(e, "AI keys EncryptedSharedPreferences unavailable; falling back to plain")
        context.getSharedPreferences("${ENCRYPTED_PREFS_NAME}_plain", Context.MODE_PRIVATE)
    }

    private val apiKeyFlows: Map<AiProvider, MutableStateFlow<String>> =
        AiProvider.entries.associateWithTo(EnumMap(AiProvider::class.java)) { provider ->
            MutableStateFlow(encryptedPrefs.getString(apiKeyPrefName(provider), null).orEmpty())
        }

    init {
        if (!encryptedPrefs.getBoolean(MIGRATION_DONE_KEY, false)) {
            appScope.launch {
                runCatching { migrateLegacyApiKeysFromDataStore() }
                    .onFailure { Timber.e(it, "AI keys migration failed; will retry next launch") }
            }
        }
    }

    private suspend fun migrateLegacyApiKeysFromDataStore() {
        val snapshot = dataStore.data.first()
        val editor = encryptedPrefs.edit()
        var migratedAny = false
        AiProvider.entries.forEach { provider ->
            val legacyValue = snapshot[Keys.getLegacyApiKey(provider)]
            if (!legacyValue.isNullOrBlank()) {
                editor.putString(apiKeyPrefName(provider), legacyValue)
                apiKeyFlows.getValue(provider).value = legacyValue
                migratedAny = true
            }
        }
        editor.putBoolean(MIGRATION_DONE_KEY, true)
        editor.apply()

        if (migratedAny) {
            dataStore.edit { preferences ->
                AiProvider.entries.forEach { provider ->
                    preferences.remove(Keys.getLegacyApiKey(provider))
                }
            }
            Timber.i("Migrated AI API keys from plain DataStore to encrypted prefs")
        }
    }

    private fun apiKeyPrefName(provider: AiProvider): String =
        "${provider.name.lowercase()}_api_key"

    fun getApiKey(provider: AiProvider): Flow<String> =
        apiKeyFlows.getValue(provider).asStateFlow()

    fun getModel(provider: AiProvider): Flow<String> =
        dataStore.data.map { preferences -> preferences[Keys.getModel(provider)] ?: "" }

    fun getSystemPrompt(provider: AiProvider): Flow<String> =
        dataStore.data.map { preferences ->
            preferences[Keys.getSystemPrompt(provider)] ?: DEFAULT_SYSTEM_PROMPT
        }

    suspend fun setApiKey(provider: AiProvider, apiKey: String) {
        encryptedPrefs.edit().putString(apiKeyPrefName(provider), apiKey).apply()
        apiKeyFlows.getValue(provider).value = apiKey
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

    // Convenience properties for legacy compatibility (e.g. PlayerViewModel)
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
    val glmModel: Flow<String> = getModel(AiProvider.GLM)
    val glmSystemPrompt: Flow<String> = getSystemPrompt(AiProvider.GLM)

    val openaiApiKey: Flow<String> = getApiKey(AiProvider.OPENAI)
    val openaiModel: Flow<String> = getModel(AiProvider.OPENAI)
    val openaiSystemPrompt: Flow<String> = getSystemPrompt(AiProvider.OPENAI)

    val openrouterApiKey: Flow<String> = getApiKey(AiProvider.OPENROUTER)
    val openrouterModel: Flow<String> = getModel(AiProvider.OPENROUTER)
    val openrouterSystemPrompt: Flow<String> = getSystemPrompt(AiProvider.OPENROUTER)

    val aiProvider: Flow<String> =
        dataStore.data.map { preferences -> preferences[Keys.AI_PROVIDER] ?: "GEMINI" }

    val isSafeTokenLimitEnabled: Flow<Boolean> =
        dataStore.data.map { preferences -> preferences[Keys.SAFE_TOKEN_LIMIT] ?: true }

    suspend fun setAiProvider(provider: String) {
        dataStore.edit { preferences -> preferences[Keys.AI_PROVIDER] = provider }
    }

    suspend fun setSafeTokenLimitEnabled(enabled: Boolean) {
        dataStore.edit { preferences -> preferences[Keys.SAFE_TOKEN_LIMIT] = enabled }
    }
}
