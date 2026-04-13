package com.theveloper.pixelplay.data.ai


import com.theveloper.pixelplay.data.ai.provider.AiClientFactory
import com.theveloper.pixelplay.data.ai.provider.AiProvider
import com.theveloper.pixelplay.data.database.AiCacheDao
import com.theveloper.pixelplay.data.database.AiCacheEntity
import com.theveloper.pixelplay.data.preferences.AiPreferencesRepository
import com.theveloper.pixelplay.data.database.AiUsageDao
import com.theveloper.pixelplay.data.database.AiUsageEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.launch
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Dispatchers
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AiOrchestrator @Inject constructor(
    private val preferencesRepo: AiPreferencesRepository,
    private val clientFactory: AiClientFactory,
    private val cacheDao: AiCacheDao,
    private val usageDao: AiUsageDao,
    private val promptEngine: AiSystemPromptEngine
) {
    // Cooldown timer: Provider -> Expiry Timestamp
    private val providerCooldowns = mutableMapOf<AiProvider, Long>()
    private val COOLDOWN_DURATION_MS = 1000L * 60 * 5 // 5 minutes

    // Cache TTL: 30 minutes — prevents stale results from being served indefinitely
    private val CACHE_TTL_MS = 1000L * 60 * 30

    // Request timeout: 60 seconds max per provider attempt
    private val REQUEST_TIMEOUT_MS = 60_000L

    private fun String.sha256(): String {
        return MessageDigest.getInstance("SHA-256")
            .digest(this.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }

    private suspend fun getBasePersona(provider: AiProvider): String {
        return preferencesRepo.getSystemPrompt(provider).first()
            .ifBlank { AiPreferencesRepository.DEFAULT_SYSTEM_PROMPT }
    }

    private suspend fun getApiKey(provider: AiProvider): String {
        return preferencesRepo.getApiKey(provider).first()
    }

    private suspend fun getModel(provider: AiProvider): String {
        return preferencesRepo.getModel(provider).first()
    }

    private suspend fun setModel(provider: AiProvider, model: String) {
        preferencesRepo.setModel(provider, model)
    }

    private suspend fun generateWithRecovery(
        provider: AiProvider,
        apiKey: String,
        systemPrompt: String,
        prompt: String,
        temperature: Float
    ): String {
        val client = clientFactory.createClient(provider, apiKey)
        val requestedModel = getModel(provider).ifBlank { client.getDefaultModel() }

        return try {
            // Wrap in timeout to prevent hanging requests
            withTimeout(REQUEST_TIMEOUT_MS) {
                client.generateContent(
                    requestedModel,
                    systemPrompt,
                    prompt,
                    temperature
                )
            }
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            throw com.theveloper.pixelplay.data.ai.provider.AiProviderSupport.createException(
                providerName = provider.displayName,
                statusCode = null,
                transportMessage = "Request timed out after ${REQUEST_TIMEOUT_MS / 1000}s. The model may be overloaded.",
                responseBody = null,
                requestedModel = requestedModel
            )
        } catch (e: Exception) {
            val failure = com.theveloper.pixelplay.data.ai.provider.AiProviderSupport.wrapThrowable(
                provider.displayName,
                e,
                requestedModel
            )

            val recoveredModel = recoverModelIfNeeded(
                provider = provider,
                apiKey = apiKey,
                requestedModel = requestedModel,
                client = client,
                failure = failure
            ) ?: throw failure

            // Retry with recovered model (also with timeout)
            withTimeout(REQUEST_TIMEOUT_MS) {
                client.generateContent(
                    recoveredModel,
                    systemPrompt,
                    prompt,
                    temperature
                )
            }
        }
    }

    private suspend fun recoverModelIfNeeded(
        provider: AiProvider,
        apiKey: String,
        requestedModel: String,
        client: com.theveloper.pixelplay.data.ai.provider.AiClient,
        failure: com.theveloper.pixelplay.data.ai.provider.AiProviderException
    ): String? {
        if (!failure.isModelUnavailable()) return null

        val availableModels = runCatching { client.getAvailableModels(apiKey) }.getOrDefault(emptyList())
        val recoveredModel = com.theveloper.pixelplay.data.ai.provider.AiProviderSupport.selectRecoveryModel(
            currentModel = requestedModel,
            defaultModel = client.getDefaultModel(),
            availableModels = availableModels
        ) ?: return null

        setModel(provider, recoveredModel)
        return recoveredModel
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
        val hash = (userProvider.name + combinedSystemPrompt + prompt).sha256()

        // Check cache with TTL — don't serve stale results
        cacheDao.getCache(hash)?.let { cached ->
            val age = System.currentTimeMillis() - cached.timestamp
            if (age < CACHE_TTL_MS) {
                return cached.responseJson
            }
            // Cache expired — proceed with fresh generation
        }

        val providersToTry = com.theveloper.pixelplay.data.ai.provider.AiProviderSupport.buildProviderChain(userProvider)
        val failedProviders = mutableListOf<String>()
        val now = System.currentTimeMillis()
        
        for (provider in providersToTry) {
            // Skip if in cooldown
            val cooldownExpiry = providerCooldowns[provider] ?: 0L
            if (now < cooldownExpiry) {
                failedProviders.add("${provider.name}: on cooldown (${((cooldownExpiry - now) / 1000)}s remaining)")
                continue
            }

            try {
                val apiKey = getApiKey(provider)
                if (apiKey.isBlank()) {
                    failedProviders.add("${provider.name}: no API key configured")
                    continue
                }

                // Use the shared base persona but specialized type rules for each provider in the chain
                val providerPersona = getBasePersona(provider)
                val finalSystemPrompt = promptEngine.buildPrompt(providerPersona, type, context)

                val response = generateWithRecovery(
                    provider = provider,
                    apiKey = apiKey,
                    systemPrompt = finalSystemPrompt,
                    prompt = prompt,
                    temperature = resolvedTemperature
                )

                // Validate response is not empty
                if (response.isBlank()) {
                    failedProviders.add("${provider.name}: returned empty response")
                    continue
                }

                // Low-maintenance usage tracking using highly accurate proportional estimation bounds (4 chars ~ 1 token)
                // Models with "thinking" or "reasoning" generally output 2-3x internal tokens for complex generation
                val isThinkingModel = finalSystemPrompt.contains("think", true) || provider.name.contains("reasoning", true)
                val estimatedPromptTokens = (finalSystemPrompt.length + prompt.length) / 4
                val estimatedOutputTokens = response.length / 4
                val estimatedThoughtTokens = if (isThinkingModel) (estimatedOutputTokens * 1.5).toInt() else 0

                kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                    usageDao.insertUsage(
                        AiUsageEntity(
                            timestamp = now,
                            provider = provider.displayName,
                            model = provider.name,
                            promptType = type.name,
                            promptTokens = estimatedPromptTokens,
                            outputTokens = estimatedOutputTokens,
                            thoughtTokens = estimatedThoughtTokens
                        )
                    )
                }

                cacheDao.insert(AiCacheEntity(promptHash = hash, responseJson = response, timestamp = System.currentTimeMillis()))
                return response
            } catch (e: Exception) {
                // AI Optimization: Robust failover logic—if one provider fails, we log and try the next in the chain
                val failure = com.theveloper.pixelplay.data.ai.provider.AiProviderSupport.wrapThrowable(provider.displayName, e)
                failedProviders.add("${provider.name}: ${failure.message ?: "Unknown error"}")
                // Trigger cooldown only on provider-level outages and account problems.
                if (failure.shouldCooldown()) {
                    providerCooldowns[provider] = now + COOLDOWN_DURATION_MS
                }
            }
        }
        
        // AI Integration: Bubble up a detailed, user-friendly error if all providers fail
        val errorMessage = when {
            failedProviders.all { it.contains("no API key") } ->
                "No API key configured. Go to Settings → AI Integration to set up your API key."
            
            failedProviders.all { it.contains("cooldown") } ->
                "All AI providers are on cooldown after recent errors. Wait a few minutes and try again."
            
            failedProviders.size == 1 ->
                "AI generation failed: ${failedProviders.first()}"
            
            else ->
                "AI generation failed after trying ${failedProviders.size} providers:\n${failedProviders.joinToString("\n• ", prefix = "• ")}"
        }
        
        throw Exception(errorMessage)
    }
}
