package com.theveloper.pixelplay.data.ai.provider

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * DeepSeek AI provider implementation
 * Uses OpenAI-compatible API
 */
class DeepSeekAiClient(private val apiKey: String) : AiClient {
    
    companion object {
        private const val DEFAULT_DEEPSEEK_MODEL = "deepseek-chat"
        private const val BASE_URL = "https://api.deepseek.com"
    }
    
    @Serializable
    data class ChatMessage(val role: String, val content: String)
    
    @Serializable
    data class ChatRequest(
        val model: String,
        val messages: List<ChatMessage>,
        val temperature: Double = 0.7
    )
    
    @Serializable
    data class ChatChoice(val message: ChatMessage)
    
    @Serializable
    data class ChatResponse(val choices: List<ChatChoice>)
    
    @Serializable
    data class ModelItem(val id: String)
    
    @Serializable
    data class ModelsResponse(val data: List<ModelItem>)
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
    
    private val json = Json { 
        ignoreUnknownKeys = true
        isLenient = true
    }
    
    override suspend fun generateContent(
        model: String, 
        systemPrompt: String, 
        prompt: String,
        temperature: Float
    ): String {
        return withContext(Dispatchers.IO) {
            val messagesList = mutableListOf<ChatMessage>()
            if (systemPrompt.isNotBlank()) {
                messagesList.add(ChatMessage(role = "system", content = systemPrompt))
            }
            messagesList.add(ChatMessage(role = "user", content = prompt))

            val requestBody = ChatRequest(
                model = model.ifBlank { DEFAULT_DEEPSEEK_MODEL },
                messages = messagesList,
                temperature = temperature.toDouble()
            )
            
            val jsonBody = json.encodeToString(ChatRequest.serializer(), requestBody)
            val body = jsonBody.toRequestBody("application/json".toMediaType())
            
            val request = Request.Builder()
                .url("$BASE_URL/chat/completions")
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .post(body)
                .build()
            
            val response = client.newCall(request).execute()
            
            if (!response.isSuccessful) {
                throw Exception("DeepSeek API error: ${response.code} ${response.message}")
            }
            
            val responseBody = response.body?.string() 
                ?: throw Exception("DeepSeek returned empty response")
            
            val chatResponse = json.decodeFromString<ChatResponse>(responseBody)
            chatResponse.choices.firstOrNull()?.message?.content 
                ?: throw Exception("DeepSeek response has no content")
        }
    }
    
    override suspend fun countTokens(model: String, systemPrompt: String, prompt: String): Int {
        // DeepSeek estimation
        return (systemPrompt.length + prompt.length) / 4
    }
    
    override suspend fun getAvailableModels(apiKey: String): List<String> {
        return withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url("$BASE_URL/models")
                    .addHeader("Authorization", "Bearer $apiKey")
                    .get()
                    .build()
                
                val response = client.newCall(request).execute()
                
                if (!response.isSuccessful) {
                    return@withContext getDefaultModels()
                }
                
                val responseBody = response.body?.string() ?: return@withContext getDefaultModels()
                val modelsResponse = json.decodeFromString<ModelsResponse>(responseBody)
                modelsResponse.data.map { it.id }
            } catch (e: Exception) {
                getDefaultModels()
            }
        }
    }
    
    override suspend fun validateApiKey(apiKey: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url("$BASE_URL/models")
                    .addHeader("Authorization", "Bearer $apiKey")
                    .get()
                    .build()
                
                val response = client.newCall(request).execute()
                response.isSuccessful
            } catch (e: Exception) {
                false
            }
        }
    }
    
    override fun getDefaultModel(): String = DEFAULT_DEEPSEEK_MODEL
    
    private fun getDefaultModels(): List<String> {
        return listOf(
            "deepseek-chat",
            "deepseek-reasoner"
        )
    }
}
