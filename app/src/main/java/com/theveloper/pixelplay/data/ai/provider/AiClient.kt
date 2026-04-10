package com.theveloper.pixelplay.data.ai.provider

/**
 * Abstract interface for AI providers
 * Defines common operations for text generation and metadata completion
 */
interface AiClient {
    
    /**
     * Generate text content based on a prompt
     * @param model The model identifier to use
     * @param systemPrompt The system prompt instructions
     * @param prompt The input prompt
     * @param temperature Creativity control (0.0 to 1.0)
     * @return Generated text response
     */
    suspend fun generateContent(
        model: String, 
        systemPrompt: String, 
        prompt: String,
        temperature: Float = 0.7f
    ): String
    
    /**
     * Estimate or count tokens for a given prompt
     */
    suspend fun countTokens(model: String, systemPrompt: String, prompt: String): Int
    
    /**
     * Get list of available models for this provider
     */
    suspend fun getAvailableModels(apiKey: String): List<String>
    
    /**
     * Validate the API key
     */
    suspend fun validateApiKey(apiKey: String): Boolean
    
    /**
     * Get the default model for this provider
     */
    fun getDefaultModel(): String
}
