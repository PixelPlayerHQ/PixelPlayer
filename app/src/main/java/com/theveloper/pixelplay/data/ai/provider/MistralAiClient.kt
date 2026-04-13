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

class MistralAiClient(private val apiKey: String) : AiClient {
    
    companion object {
        private const val DEFAULT_MODEL = "mistral-large-latest"
        private const val BASE_URL = "https://api.mistral.ai/v1"
    }
    
    @Serializable
    private data class ChatMessage(val role: String, val content: String)
    
    @Serializable
    private data class ChatRequest(
        val model: String,
        val messages: List<ChatMessage>,
        val temperature: Double = 0.7
    )
    
    @Serializable
    private data class ChatChoice(val message: ChatMessage)
    
    @Serializable
    private data class ChatResponse(val choices: List<ChatChoice>)
    
    @Serializable
    private data class ModelItem(val id: String)
    
    @Serializable
    private data class ModelsResponse(val data: List<ModelItem>)
    
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
            val resolvedModel = model.ifBlank { DEFAULT_MODEL }
            val messagesList = mutableListOf<ChatMessage>()
            if (systemPrompt.isNotBlank()) {
                messagesList.add(ChatMessage(role = "system", content = systemPrompt))
            }
            messagesList.add(ChatMessage(role = "user", content = prompt))

            val requestBody = ChatRequest(
                model = resolvedModel,
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

            try {
                client.newCall(request).execute().use { response ->
                    val responseBody = response.body?.string()

                    if (!response.isSuccessful) {
                        throw AiProviderSupport.createException(
                            providerName = "Mistral",
                            statusCode = response.code,
                            transportMessage = response.message,
                            responseBody = responseBody,
                            requestedModel = resolvedModel
                        )
                    }

                    val nonEmptyBody = responseBody
                        ?: throw AiProviderSupport.createException(
                            providerName = "Mistral",
                            statusCode = response.code,
                            transportMessage = "Empty response body",
                            responseBody = null,
                            requestedModel = resolvedModel
                        )

                    val chatResponse = json.decodeFromString<ChatResponse>(nonEmptyBody)
                    chatResponse.choices.firstOrNull()?.message?.content
                        ?: throw AiProviderSupport.createException(
                            providerName = "Mistral",
                            statusCode = response.code,
                            transportMessage = "Response had no content",
                            responseBody = nonEmptyBody,
                            requestedModel = resolvedModel
                        )
                }
            } catch (e: Exception) {
                throw AiProviderSupport.wrapThrowable("Mistral", e, resolvedModel)
            }
        }
    }
    
    override suspend fun countTokens(model: String, systemPrompt: String, prompt: String): Int {
        // Mistral estimation
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
    
    override fun getDefaultModel(): String = DEFAULT_MODEL
    
    private fun getDefaultModels(): List<String> {
        return listOf(
            "mistral-large-latest",
            "mistral-small-latest",
            "open-mixtral-8x22b",
            "open-mixtral-8x7b"
        )
    }
}
