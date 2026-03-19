package com.theveloper.pixelplay.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AiPreferencesRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    companion object {
        const val DEFAULT_SYSTEM_PROMPT =
            "You are a helpful AI assistant integrated into a music player app. You help users create perfect playlists based on their request."
        const val DEFAULT_DEEPSEEK_SYSTEM_PROMPT =
            "You are a helpful AI assistant integrated into a music player app. You help users create perfect playlists based on their request."
    }

    private object Keys {
        val GEMINI_API_KEY = stringPreferencesKey("gemini_api_key")
        val GEMINI_MODEL = stringPreferencesKey("gemini_model")
        val GEMINI_SYSTEM_PROMPT = stringPreferencesKey("gemini_system_prompt")
        val AI_PROVIDER = stringPreferencesKey("ai_provider")
        val DEEPSEEK_API_KEY = stringPreferencesKey("deepseek_api_key")
        val DEEPSEEK_MODEL = stringPreferencesKey("deepseek_model")
        val DEEPSEEK_SYSTEM_PROMPT = stringPreferencesKey("deepseek_system_prompt")
    }

    val geminiApiKey: Flow<String> =
        dataStore.data.map { preferences -> preferences[Keys.GEMINI_API_KEY] ?: "" }

    val geminiModel: Flow<String> =
        dataStore.data.map { preferences -> preferences[Keys.GEMINI_MODEL] ?: "" }

    val geminiSystemPrompt: Flow<String> =
        dataStore.data.map { preferences ->
            preferences[Keys.GEMINI_SYSTEM_PROMPT] ?: DEFAULT_SYSTEM_PROMPT
        }

    val aiProvider: Flow<String> =
        dataStore.data.map { preferences -> preferences[Keys.AI_PROVIDER] ?: "GEMINI" }

    val deepseekApiKey: Flow<String> =
        dataStore.data.map { preferences -> preferences[Keys.DEEPSEEK_API_KEY] ?: "" }

    val deepseekModel: Flow<String> =
        dataStore.data.map { preferences -> preferences[Keys.DEEPSEEK_MODEL] ?: "" }

    val deepseekSystemPrompt: Flow<String> =
        dataStore.data.map { preferences ->
            preferences[Keys.DEEPSEEK_SYSTEM_PROMPT] ?: DEFAULT_DEEPSEEK_SYSTEM_PROMPT
        }

    suspend fun setGeminiApiKey(apiKey: String) {
        dataStore.edit { preferences -> preferences[Keys.GEMINI_API_KEY] = apiKey }
    }

    suspend fun setGeminiModel(model: String) {
        dataStore.edit { preferences -> preferences[Keys.GEMINI_MODEL] = model }
    }

    suspend fun setGeminiSystemPrompt(prompt: String) {
        dataStore.edit { preferences -> preferences[Keys.GEMINI_SYSTEM_PROMPT] = prompt }
    }

    suspend fun resetGeminiSystemPrompt() {
        dataStore.edit { preferences ->
            preferences[Keys.GEMINI_SYSTEM_PROMPT] = DEFAULT_SYSTEM_PROMPT
        }
    }

    suspend fun setAiProvider(provider: String) {
        dataStore.edit { preferences -> preferences[Keys.AI_PROVIDER] = provider }
    }

    suspend fun setDeepseekApiKey(apiKey: String) {
        dataStore.edit { preferences -> preferences[Keys.DEEPSEEK_API_KEY] = apiKey }
    }

    suspend fun setDeepseekModel(model: String) {
        dataStore.edit { preferences -> preferences[Keys.DEEPSEEK_MODEL] = model }
    }

    suspend fun setDeepseekSystemPrompt(prompt: String) {
        dataStore.edit { preferences -> preferences[Keys.DEEPSEEK_SYSTEM_PROMPT] = prompt }
    }

    suspend fun resetDeepseekSystemPrompt() {
        dataStore.edit { preferences ->
            preferences[Keys.DEEPSEEK_SYSTEM_PROMPT] = DEFAULT_DEEPSEEK_SYSTEM_PROMPT
        }
    }
}
