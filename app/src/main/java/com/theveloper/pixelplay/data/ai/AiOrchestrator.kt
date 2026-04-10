package com.theveloper.pixelplay.data.ai


import com.theveloper.pixelplay.data.ai.provider.AiClientFactory
import com.theveloper.pixelplay.data.ai.provider.AiProvider
import com.theveloper.pixelplay.data.database.AiCacheDao
import com.theveloper.pixelplay.data.database.AiCacheEntity
import com.theveloper.pixelplay.data.preferences.AiPreferencesRepository
import kotlinx.coroutines.flow.first
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AiOrchestrator @Inject constructor(
    private val preferencesRepo: AiPreferencesRepository,
    private val clientFactory: AiClientFactory,
    private val cacheDao: AiCacheDao,
    private val promptEngine: AiSystemPromptEngine
) {
    // Cooldown timer: Provider -> Expiry Timestamp
    private val providerCooldowns = mutableMapOf<AiProvider, Long>()
    private val COOLDOWN_DURATION_MS = 1000L * 60 * 5 // 5 minutes

    private fun String.sha256(): String {
        return MessageDigest.getInstance("SHA-256")
            .digest(this.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }

    private suspend fun getBasePersona(provider: AiProvider): String {
        val prompt = when (provider) {
            AiProvider.GEMINI -> preferencesRepo.geminiSystemPrompt.first()
            AiProvider.DEEPSEEK -> preferencesRepo.deepseekSystemPrompt.first()
            AiProvider.GROQ -> preferencesRepo.groqSystemPrompt.first()
            AiProvider.MISTRAL -> preferencesRepo.mistralSystemPrompt.first()
            AiProvider.NVIDIA -> preferencesRepo.nvidiaSystemPrompt.first()
            AiProvider.KIMI -> preferencesRepo.kimiSystemPrompt.first()
            AiProvider.GLM -> preferencesRepo.glmSystemPrompt.first()
            AiProvider.OPENAI -> preferencesRepo.openaiSystemPrompt.first()
        }
        return prompt.ifBlank { AiPreferencesRepository.DEFAULT_SYSTEM_PROMPT }
    }

    private suspend fun getApiKey(provider: AiProvider): String {
        return when (provider) {
            AiProvider.GEMINI -> preferencesRepo.geminiApiKey.first()
            AiProvider.DEEPSEEK -> preferencesRepo.deepseekApiKey.first()
            AiProvider.GROQ -> preferencesRepo.groqApiKey.first()
            AiProvider.MISTRAL -> preferencesRepo.mistralApiKey.first()
            AiProvider.NVIDIA -> preferencesRepo.nvidiaApiKey.first()
            AiProvider.KIMI -> preferencesRepo.kimiApiKey.first()
            AiProvider.GLM -> preferencesRepo.glmApiKey.first()
            AiProvider.OPENAI -> preferencesRepo.openaiApiKey.first()
        }
    }

    private suspend fun getModel(provider: AiProvider): String {
        return when (provider) {
            AiProvider.GEMINI -> preferencesRepo.geminiModel.first()
            AiProvider.DEEPSEEK -> preferencesRepo.deepseekModel.first()
            AiProvider.GROQ -> preferencesRepo.groqModel.first()
            AiProvider.MISTRAL -> preferencesRepo.mistralModel.first()
            AiProvider.NVIDIA -> preferencesRepo.nvidiaModel.first()
            AiProvider.KIMI -> preferencesRepo.kimiModel.first()
            AiProvider.GLM -> preferencesRepo.glmModel.first()
            AiProvider.OPENAI -> preferencesRepo.openaiModel.first()
        }
    }

    suspend fun generateContent(
        prompt: String,
        type: AiSystemPromptType = AiSystemPromptType.GENERAL,
        temperature: Float = 0.7f,
        context: String = ""
    ): String {
        // Dynamic temperature adjustment if default value is used
        val resolvedTemperature = if (temperature == 0.7f) {
            when (type) {
                // AI Optimization: Use low temperature for high-precision metadata to prevent hallucinations
                AiSystemPromptType.METADATA -> 0.1f
                AiSystemPromptType.MOOD_ANALYSIS -> 0.2f
                // AI Optimization: Moderate temperature for tags to allow creative yet relevant descriptors
                AiSystemPromptType.TAGGING -> 0.4f
                // AI Optimization: Balanced temperature for playlists to ensure variety without losing cohesion
                AiSystemPromptType.PLAYLIST -> 0.6f
                // AI Optimization: High temperature for persona-based responses to increase flair and engagement
                AiSystemPromptType.PERSONA -> 0.85f
                AiSystemPromptType.GENERAL -> 0.7f
            }
        } else temperature

        // Determine chain based on user preference
        val userProviderStr = preferencesRepo.aiProvider.first()
        val userProvider = AiProvider.fromString(userProviderStr)

        // Generate combined prompt for hashing and execution
        val basePersona = getBasePersona(userProvider)
        val combinedSystemPrompt = promptEngine.buildPrompt(basePersona, type, context)
        
        // Cache entry is valid for a specific prompt + system instruction + provider
        val hash = (combinedSystemPrompt + prompt).sha256()

        cacheDao.getCache(hash)?.responseJson?.let { return it }

        val providersToTry = mutableListOf<AiProvider>()
        providersToTry.add(userProvider)
        
        // Setup failover list prioritizing fast/free models
        if (userProvider != AiProvider.GROQ) providersToTry.add(AiProvider.GROQ)
        if (userProvider != AiProvider.MISTRAL) providersToTry.add(AiProvider.MISTRAL)
        if (userProvider != AiProvider.GEMINI) providersToTry.add(AiProvider.GEMINI)
        if (userProvider != AiProvider.DEEPSEEK) providersToTry.add(AiProvider.DEEPSEEK)
        
        val failedProviders = mutableListOf<String>()
        val now = System.currentTimeMillis()
        
        for (provider in providersToTry) {
            // Skip if in cooldown
            val cooldownExpiry = providerCooldowns[provider] ?: 0L
            if (now < cooldownExpiry) continue

            try {
                val apiKey = getApiKey(provider)
                if (apiKey.isBlank()) continue
                
                val model = getModel(provider)
                // Use the shared base persona but specialized type rules for each provider in the chain
                val providerPersona = getBasePersona(provider)
                val finalSystemPrompt = promptEngine.buildPrompt(providerPersona, type, context)
                
                val client = clientFactory.createClient(provider, apiKey)
                val response = client.generateContent(
                    model.ifBlank { client.getDefaultModel() }, 
                    finalSystemPrompt,
                    prompt,
                    resolvedTemperature
                )
                
                cacheDao.insert(AiCacheEntity(promptHash = hash, responseJson = response, timestamp = System.currentTimeMillis()))
                return response
            } catch (e: Exception) {
                // AI Optimization: Robust failover logic—if one provider fails, we log and try the next in the chain
                failedProviders.add("${provider.name}: ${e.message}")
                // Trigger cooldown on critical failures (auth, network) to prevent repeated stalls
                providerCooldowns[provider] = now + COOLDOWN_DURATION_MS
            }
        }
        
        // AI Integration: Bubble up a detailed error if all providers in the chain fail
        throw Exception("AI Generation failed. Tried ${failedProviders.size} providers: ${failedProviders.joinToString(" | ")}")
    }
}
