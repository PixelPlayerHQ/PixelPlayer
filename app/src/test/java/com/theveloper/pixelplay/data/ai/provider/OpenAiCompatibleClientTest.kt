package com.theveloper.pixelplay.data.ai.provider

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class OpenAiCompatibleClientTest {

    @Test
    fun `DeepSeek has correct provider config`() {
        val client = DeepSeekAiClient("key")
        assertThat(client.getDefaultModel()).isEqualTo("deepseek-chat")
    }

    @Test
    fun `Groq has correct provider config`() {
        val client = GroqAiClient("key")
        assertThat(client.getDefaultModel()).isEqualTo("llama-3.1-8b-instant")
    }

    @Test
    fun `Groq filters out whisper models`() {
        val client = GroqAiClient("key")
        val filtered = client.filterModels(
            listOf("llama-3.1-8b-instant", "whisper-large-v3", "mixtral-8x7b-32768")
        )
        assertThat(filtered).containsExactly("llama-3.1-8b-instant", "mixtral-8x7b-32768")
    }

    @Test
    fun `Mistral has correct provider config`() {
        val client = MistralAiClient("key")
        assertThat(client.getDefaultModel()).isEqualTo("mistral-large-latest")
    }

    @Test
    fun `GenericOpenAiClient uses provided default model`() {
        val client = GenericOpenAiClient(
            apiKey = "key",
            baseUrl = "https://api.example.com/v1",
            defaultModelId = "custom-model",
            providerName = "TestProvider"
        )
        assertThat(client.getDefaultModel()).isEqualTo("custom-model")
    }

    @Test
    fun `GenericOpenAiClient filters embed and tts models`() {
        val client = GenericOpenAiClient(
            apiKey = "key",
            baseUrl = "https://api.example.com/v1",
            defaultModelId = "gpt-4o",
            providerName = "TestProvider"
        )
        val filtered = client.filterModels(
            listOf("gpt-4o", "text-embedding-3-small", "tts-1", "whisper-1", "gpt-4o-mini")
        )
        assertThat(filtered).containsExactly("gpt-4o", "gpt-4o-mini")
    }

    @Test
    fun `countTokens returns approximate token count`() {
        val client = DeepSeekAiClient("key")
        // 8 chars system + 12 chars prompt = 20 chars / 4 = 5 tokens
        val tokens = kotlinx.coroutines.runBlocking {
            client.countTokens("model", "12345678", "123456789012")
        }
        assertThat(tokens).isEqualTo(5)
    }
}
