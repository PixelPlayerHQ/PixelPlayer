package com.theveloper.pixelplay.data.ai.provider

import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.generationConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Gemini AI provider implementation using the official Android SDK
 */
class GeminiAiClient(private val apiKey: String) : AiClient {
    
    companion object {
        private const val DEFAULT_GEMINI_MODEL = "gemini-2.5-flash"
    }
    
    private fun createModel(modelName: String, systemPrompt: String, temp: Float = 0.7f): GenerativeModel {
        return GenerativeModel(
            modelName = modelName.ifBlank { DEFAULT_GEMINI_MODEL },
            apiKey = apiKey,
            generationConfig = generationConfig {
                temperature = temp
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
            val generativeModel = createModel(model, systemPrompt, temperature)
            val response = generativeModel.generateContent(prompt)
            response.text ?: throw Exception("Gemini returned an empty response")
        }
    }
    
    override suspend fun countTokens(model: String, systemPrompt: String, prompt: String): Int {
        return withContext(Dispatchers.IO) {
            try {
                val generativeModel = createModel(model, systemPrompt)
                // Combine system instruction if possible, or just estimate
                val response = generativeModel.countTokens(prompt)
                response.totalTokens
            } catch (e: Exception) {
                // Return estimation if SDK fails
                (prompt.length / 4) + (systemPrompt.length / 4)
            }
        }
    }
    
    override suspend fun getAvailableModels(apiKey: String): List<String> {
        // Models are usually fetched via HTTP as the SDK doesn't expose a listing method
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
                // Try a very small generation to validate key
                val generativeModel = GenerativeModel(
                    modelName = "gemini-1.5-flash",
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
            
            for (match in matches) {
                val fullName = match.groupValues[1]
                val modelName = fullName.removePrefix("models/")
                
                if (modelName.startsWith("gemini", ignoreCase = true) &&
                    !modelName.contains("embedding", ignoreCase = true)) {
                    models.add(modelName)
                }
            }
            
            return if (models.isNotEmpty()) models else getDefaultModels()
        } catch (e: Exception) {
            return getDefaultModels()
        }
    }
    
    private fun getDefaultModels(): List<String> {
        return listOf(
            "gemini-2.5-flash",
            "gemini-2.5-pro",
            "gemini-1.5-flash",
            "gemini-1.5-pro"
        )
    }
}
