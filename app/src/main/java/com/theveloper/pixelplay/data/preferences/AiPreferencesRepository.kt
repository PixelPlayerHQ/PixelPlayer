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
        val DEFAULT_SYSTEM_PROMPT = """
            You are 'Vibe-Engine', a professional music curator.
            Analyze the user's request and listening profile to provide perfect music recommendations.
        """.trimIndent()

        val DEFAULT_DEEPSEEK_SYSTEM_PROMPT = DEFAULT_SYSTEM_PROMPT
        val DEFAULT_GROQ_SYSTEM_PROMPT = DEFAULT_SYSTEM_PROMPT
        val DEFAULT_MISTRAL_SYSTEM_PROMPT = DEFAULT_SYSTEM_PROMPT
        val DEFAULT_NVIDIA_SYSTEM_PROMPT = DEFAULT_SYSTEM_PROMPT
        val DEFAULT_KIMI_SYSTEM_PROMPT = DEFAULT_SYSTEM_PROMPT
        val DEFAULT_GLM_SYSTEM_PROMPT = DEFAULT_SYSTEM_PROMPT
        val DEFAULT_OPENAI_SYSTEM_PROMPT = DEFAULT_SYSTEM_PROMPT
        
        // Internal specialized prompts are now handled by AiSystemPromptEngine
    }

    private object Keys {
        val GEMINI_API_KEY = stringPreferencesKey("gemini_api_key")
        val GEMINI_MODEL = stringPreferencesKey("gemini_model")
        val GEMINI_SYSTEM_PROMPT = stringPreferencesKey("gemini_system_prompt")
        val AI_PROVIDER = stringPreferencesKey("ai_provider")
        val DEEPSEEK_API_KEY = stringPreferencesKey("deepseek_api_key")
        val DEEPSEEK_MODEL = stringPreferencesKey("deepseek_model")
        val DEEPSEEK_SYSTEM_PROMPT = stringPreferencesKey("deepseek_system_prompt")
        
        val GROQ_API_KEY = stringPreferencesKey("groq_api_key")
        val GROQ_MODEL = stringPreferencesKey("groq_model")
        val GROQ_SYSTEM_PROMPT = stringPreferencesKey("groq_system_prompt")
        
        val MISTRAL_API_KEY = stringPreferencesKey("mistral_api_key")
        val MISTRAL_MODEL = stringPreferencesKey("mistral_model")
        val MISTRAL_SYSTEM_PROMPT = stringPreferencesKey("mistral_system_prompt")

        val NVIDIA_API_KEY = stringPreferencesKey("nvidia_api_key")
        val NVIDIA_MODEL = stringPreferencesKey("nvidia_model")
        val NVIDIA_SYSTEM_PROMPT = stringPreferencesKey("nvidia_system_prompt")

        val KIMI_API_KEY = stringPreferencesKey("kimi_api_key")
        val KIMI_MODEL = stringPreferencesKey("kimi_model")
        val KIMI_SYSTEM_PROMPT = stringPreferencesKey("kimi_system_prompt")

        val GLM_API_KEY = stringPreferencesKey("glm_api_key")
        val GLM_MODEL = stringPreferencesKey("glm_model")
        val GLM_SYSTEM_PROMPT = stringPreferencesKey("glm_system_prompt")

        val OPENAI_API_KEY = stringPreferencesKey("openai_api_key")
        val OPENAI_MODEL = stringPreferencesKey("openai_model")
        val OPENAI_SYSTEM_PROMPT = stringPreferencesKey("openai_system_prompt")
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

    val groqApiKey: Flow<String> =
        dataStore.data.map { preferences -> preferences[Keys.GROQ_API_KEY] ?: "" }

    val groqModel: Flow<String> =
        dataStore.data.map { preferences -> preferences[Keys.GROQ_MODEL] ?: "" }

    val groqSystemPrompt: Flow<String> =
        dataStore.data.map { preferences ->
            preferences[Keys.GROQ_SYSTEM_PROMPT] ?: DEFAULT_GROQ_SYSTEM_PROMPT
        }

    val mistralApiKey: Flow<String> =
        dataStore.data.map { preferences -> preferences[Keys.MISTRAL_API_KEY] ?: "" }

    val mistralModel: Flow<String> =
        dataStore.data.map { preferences -> preferences[Keys.MISTRAL_MODEL] ?: "" }

    val mistralSystemPrompt: Flow<String> =
        dataStore.data.map { preferences ->
            preferences[Keys.MISTRAL_SYSTEM_PROMPT] ?: DEFAULT_MISTRAL_SYSTEM_PROMPT
        }

    val nvidiaApiKey: Flow<String> =
        dataStore.data.map { preferences -> preferences[Keys.NVIDIA_API_KEY] ?: "" }

    val nvidiaModel: Flow<String> =
        dataStore.data.map { preferences -> preferences[Keys.NVIDIA_MODEL] ?: "" }

    val nvidiaSystemPrompt: Flow<String> =
        dataStore.data.map { preferences ->
            preferences[Keys.NVIDIA_SYSTEM_PROMPT] ?: DEFAULT_NVIDIA_SYSTEM_PROMPT
        }

    val kimiApiKey: Flow<String> =
        dataStore.data.map { preferences -> preferences[Keys.KIMI_API_KEY] ?: "" }

    val kimiModel: Flow<String> =
        dataStore.data.map { preferences -> preferences[Keys.KIMI_MODEL] ?: "" }

    val kimiSystemPrompt: Flow<String> =
        dataStore.data.map { preferences ->
            preferences[Keys.KIMI_SYSTEM_PROMPT] ?: DEFAULT_KIMI_SYSTEM_PROMPT
        }

    val glmApiKey: Flow<String> =
        dataStore.data.map { preferences -> preferences[Keys.GLM_API_KEY] ?: "" }

    val glmModel: Flow<String> =
        dataStore.data.map { preferences -> preferences[Keys.GLM_MODEL] ?: "" }

    val glmSystemPrompt: Flow<String> =
        dataStore.data.map { preferences ->
            preferences[Keys.GLM_SYSTEM_PROMPT] ?: DEFAULT_GLM_SYSTEM_PROMPT
        }

    val openaiApiKey: Flow<String> =
        dataStore.data.map { preferences -> preferences[Keys.OPENAI_API_KEY] ?: "" }

    val openaiModel: Flow<String> =
        dataStore.data.map { preferences -> preferences[Keys.OPENAI_MODEL] ?: "" }

    val openaiSystemPrompt: Flow<String> =
        dataStore.data.map { preferences ->
            preferences[Keys.OPENAI_SYSTEM_PROMPT] ?: DEFAULT_OPENAI_SYSTEM_PROMPT
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

    suspend fun setGroqApiKey(apiKey: String) {
        dataStore.edit { preferences -> preferences[Keys.GROQ_API_KEY] = apiKey }
    }

    suspend fun setGroqModel(model: String) {
        dataStore.edit { preferences -> preferences[Keys.GROQ_MODEL] = model }
    }

    suspend fun setGroqSystemPrompt(prompt: String) {
        dataStore.edit { preferences -> preferences[Keys.GROQ_SYSTEM_PROMPT] = prompt }
    }

    suspend fun resetGroqSystemPrompt() {
        dataStore.edit { preferences ->
            preferences[Keys.GROQ_SYSTEM_PROMPT] = DEFAULT_GROQ_SYSTEM_PROMPT
        }
    }

    suspend fun setMistralApiKey(apiKey: String) {
        dataStore.edit { preferences -> preferences[Keys.MISTRAL_API_KEY] = apiKey }
    }

    suspend fun setMistralModel(model: String) {
        dataStore.edit { preferences -> preferences[Keys.MISTRAL_MODEL] = model }
    }

    suspend fun setMistralSystemPrompt(prompt: String) {
        dataStore.edit { preferences -> preferences[Keys.MISTRAL_SYSTEM_PROMPT] = prompt }
    }

    suspend fun resetMistralSystemPrompt() {
        dataStore.edit { preferences ->
            preferences[Keys.MISTRAL_SYSTEM_PROMPT] = DEFAULT_MISTRAL_SYSTEM_PROMPT
        }
    }

    suspend fun setNvidiaApiKey(apiKey: String) {
        dataStore.edit { preferences -> preferences[Keys.NVIDIA_API_KEY] = apiKey }
    }

    suspend fun setNvidiaModel(model: String) {
        dataStore.edit { preferences -> preferences[Keys.NVIDIA_MODEL] = model }
    }

    suspend fun setNvidiaSystemPrompt(prompt: String) {
        dataStore.edit { preferences -> preferences[Keys.NVIDIA_SYSTEM_PROMPT] = prompt }
    }

    suspend fun resetNvidiaSystemPrompt() {
        dataStore.edit { preferences ->
            preferences[Keys.NVIDIA_SYSTEM_PROMPT] = DEFAULT_NVIDIA_SYSTEM_PROMPT
        }
    }

    suspend fun setKimiApiKey(apiKey: String) {
        dataStore.edit { preferences -> preferences[Keys.KIMI_API_KEY] = apiKey }
    }

    suspend fun setKimiModel(model: String) {
        dataStore.edit { preferences -> preferences[Keys.KIMI_MODEL] = model }
    }

    suspend fun setKimiSystemPrompt(prompt: String) {
        dataStore.edit { preferences -> preferences[Keys.KIMI_SYSTEM_PROMPT] = prompt }
    }

    suspend fun resetKimiSystemPrompt() {
        dataStore.edit { preferences ->
            preferences[Keys.KIMI_SYSTEM_PROMPT] = DEFAULT_KIMI_SYSTEM_PROMPT
        }
    }

    suspend fun setGlmApiKey(apiKey: String) {
        dataStore.edit { preferences -> preferences[Keys.GLM_API_KEY] = apiKey }
    }

    suspend fun setGlmModel(model: String) {
        dataStore.edit { preferences -> preferences[Keys.GLM_MODEL] = model }
    }

    suspend fun setGlmSystemPrompt(prompt: String) {
        dataStore.edit { preferences -> preferences[Keys.GLM_SYSTEM_PROMPT] = prompt }
    }

    suspend fun resetGlmSystemPrompt() {
        dataStore.edit { preferences ->
            preferences[Keys.GLM_SYSTEM_PROMPT] = DEFAULT_GLM_SYSTEM_PROMPT
        }
    }

    suspend fun setOpenAiApiKey(apiKey: String) {
        dataStore.edit { preferences -> preferences[Keys.OPENAI_API_KEY] = apiKey }
    }

    suspend fun setOpenAiModel(model: String) {
        dataStore.edit { preferences -> preferences[Keys.OPENAI_MODEL] = model }
    }

    suspend fun setOpenAiSystemPrompt(prompt: String) {
        dataStore.edit { preferences -> preferences[Keys.OPENAI_SYSTEM_PROMPT] = prompt }
    }

    suspend fun resetOpenAiSystemPrompt() {
        dataStore.edit { preferences ->
            preferences[Keys.OPENAI_SYSTEM_PROMPT] = DEFAULT_OPENAI_SYSTEM_PROMPT
        }
    }
}
