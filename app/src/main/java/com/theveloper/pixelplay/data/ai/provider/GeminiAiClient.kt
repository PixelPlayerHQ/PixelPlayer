package com.theveloper.pixelplay.data.ai.provider

import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.generationConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class GeminiAiClient(private val apiKey: String) : AiClient {
    
    companion object {
        private const val DEFAULT_GEMINI_MODEL = "gemini-3.1-flash-lite"
    }
    
    private fun createModel(modelName: String, systemPrompt: String, temp: Float = 0.7f): GenerativeModel {
        return GenerativeModel(
            modelName = modelName.ifBlank { DEFAULT_GEMINI_MODEL },
            apiKey = apiKey,
            generationConfig = generationConfig {
                temperature = temp
                topK = 64
                topP = 0.95f
            },
            systemInstruction = if (systemPrompt.isNotBlank()) {
                com.google.ai.client.generativeai.type.content { text(systemPrompt) }
            } else {
                null
            }
        )
    }
    
    override suspend fun generateContent(
        model: String, 
        systemPrompt: String, 
        prompt: String,
        temperature: Float
    ): String {
        return withContext(Dispatchers.IO) {
            val resolvedModel = model.ifBlank { DEFAULT_GEMINI_MODEL }
    
            try {
                val generativeModel = createModel(resolvedModel, systemPrompt, temperature)
                val response = generativeModel.generateContent(prompt)
                response.text ?: throw AiProviderSupport.createException(
                    providerName = "Gemini",
                    statusCode = null,
                    transportMessage = "Gemini returned an empty response. The model may have filtered the content.",
                    responseBody = null,
                    requestedModel = resolvedModel
                )
            } catch (e: Exception) {
                throw AiProviderSupport.wrapThrowable("Gemini", e, resolvedModel)
            }
        }
    }
    
    override suspend fun countTokens(model: String, systemPrompt: String, prompt: String): Int {
        return withContext(Dispatchers.IO) {
            try {
                val generativeModel = createModel(model, systemPrompt)
                val response = generativeModel.countTokens(prompt)
                response.totalTokens
            } catch (e: Exception) {
                (prompt.length / 4) + (systemPrompt.length / 4)
            }
        }
    }
    
    override suspend fun getAvailableModels(apiKey: String): List<String> {
        return withContext(Dispatchers.IO) {
            try {
                val url = "https://generativelanguage.googleapis.com/v1beta/models?key=$apiKey"
                val connection = java.net.URL(url).openConnection() as java.net.HttpURLConnection
                
                connection.requestMethod = "GET"
                connection.connectTimeout = 10000
                connection.readTimeout = 10000
                
                val responseCode = connection.responseCode
                if (responseCode == 200) {
                    val response = connection.inputStream.bufferedReader().use { it.readText() }
                    parseModelsFromResponse(response)
                } else {
                    getDefaultModels()
                }
            } catch (e: Exception) {
                getDefaultModels()
            }
        }
    }
    
    override suspend fun validateApiKey(apiKey: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val generativeModel = GenerativeModel(
                    modelName = DEFAULT_GEMINI_MODEL,
                    apiKey = apiKey
                )
                val response = generativeModel.generateContent("test")
                response.text != null
            } catch (e: Exception) {
                false
            }
        }
    }
    
    override fun getDefaultModel(): String = DEFAULT_GEMINI_MODEL
    
    private fun parseModelsFromResponse(jsonResponse: String): List<String> {
        try {
            val models = mutableListOf<String>()
            val modelPattern = """"name":\s*"(models/[^"]+)"""".toRegex()
            val matches = modelPattern.findAll(jsonResponse)
            
            val blacklist = listOf("-2.0", "-2.5", "-preview", "customtools", "search", "tuning", "-001", "-002")
            val whitelist = listOf("gemini-3.1-pro-preview")
            
            for (match in matches) {
                val fullName = match.groupValues[1]
                val modelName = fullName.removePrefix("models/")
                
                val isWhitelisted = whitelist.any { modelName == it }
                val hasForbiddenSuffix = blacklist.any { modelName.contains(it) }
                val isBlacklisted = hasForbiddenSuffix && !isWhitelisted
                
                if (!isBlacklisted && 
                    (modelName.startsWith("gemini", ignoreCase = true) || 
                     modelName.startsWith("gemma", ignoreCase = true)) &&
                    !modelName.contains("embedding", ignoreCase = true)) {
                    models.add(modelName)
                }
            }
            
            val defaults = getDefaultModels()
            return (models + defaults).distinct().sorted()
        } catch (e: Exception) {
            return getDefaultModels()
        }
    }
    
    private fun getDefaultModels(): List<String> {
        return listOf(
            "gemini-3.1-flash-lite",
            "gemini-3.5-flash",
            "gemini-3.1-pro-preview",
            "gemini-flash-latest"
        )
    }
}
