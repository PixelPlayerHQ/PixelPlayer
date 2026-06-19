package com.theveloper.pixelplay.data.ai.provider

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AiClientFactoryTest {

    private val factory = AiClientFactory()

    @Test
    fun `createClient returns GeminiAiClient for GEMINI`() {
        val client = factory.createClient(AiProvider.GEMINI, "test-key")
        assertThat(client).isInstanceOf(GeminiAiClient::class.java)
    }

    @Test
    fun `createClient returns DeepSeekAiClient for DEEPSEEK`() {
        val client = factory.createClient(AiProvider.DEEPSEEK, "test-key")
        assertThat(client).isInstanceOf(DeepSeekAiClient::class.java)
    }

    @Test
    fun `createClient returns GroqAiClient for GROQ`() {
        val client = factory.createClient(AiProvider.GROQ, "test-key")
        assertThat(client).isInstanceOf(GroqAiClient::class.java)
    }

    @Test
    fun `createClient returns MistralAiClient for MISTRAL`() {
        val client = factory.createClient(AiProvider.MISTRAL, "test-key")
        assertThat(client).isInstanceOf(MistralAiClient::class.java)
    }

    @Test
    fun `createClient returns GenericOpenAiClient for NVIDIA`() {
        val client = factory.createClient(AiProvider.NVIDIA, "test-key")
        assertThat(client).isInstanceOf(GenericOpenAiClient::class.java)
    }

    @Test
    fun `createClient returns GenericOpenAiClient for OPENAI`() {
        val client = factory.createClient(AiProvider.OPENAI, "test-key")
        assertThat(client).isInstanceOf(GenericOpenAiClient::class.java)
    }

    @Test
    fun `createClient returns GenericOpenAiClient for OPENROUTER`() {
        val client = factory.createClient(AiProvider.OPENROUTER, "test-key")
        assertThat(client).isInstanceOf(GenericOpenAiClient::class.java)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `createClient throws for blank API key`() {
        factory.createClient(AiProvider.GEMINI, "")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `createClient throws for whitespace-only API key`() {
        factory.createClient(AiProvider.DEEPSEEK, "   ")
    }

    @Test
    fun `all providers return correct default models`() {
        for (provider in AiProvider.entries) {
            val client = factory.createClient(provider, "test-key")
            val defaultModel = client.getDefaultModel()
            assertThat(defaultModel).isNotEmpty()
        }
    }

    @Test
    fun `OpenAI-compatible clients inherit from OpenAiCompatibleClient`() {
        val deepseek = factory.createClient(AiProvider.DEEPSEEK, "k")
        val groq = factory.createClient(AiProvider.GROQ, "k")
        val mistral = factory.createClient(AiProvider.MISTRAL, "k")
        val generic = factory.createClient(AiProvider.OPENAI, "k")

        assertThat(deepseek).isInstanceOf(OpenAiCompatibleClient::class.java)
        assertThat(groq).isInstanceOf(OpenAiCompatibleClient::class.java)
        assertThat(mistral).isInstanceOf(OpenAiCompatibleClient::class.java)
        assertThat(generic).isInstanceOf(OpenAiCompatibleClient::class.java)
    }
}
