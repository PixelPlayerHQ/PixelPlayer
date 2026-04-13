package com.theveloper.pixelplay.data.ai.provider

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AiProviderSupportTest {

    @Test
    fun `provider chain keeps selected provider first and includes all providers`() {
        val chain = AiProviderSupport.buildProviderChain(AiProvider.OPENAI)

        assertThat(chain.first()).isEqualTo(AiProvider.OPENAI)
        assertThat(chain).containsExactlyElementsIn(AiProvider.entries)
    }

    @Test
    fun `select recovery model prefers supported default`() {
        val recovered = AiProviderSupport.selectRecoveryModel(
            currentModel = "llama3-8b-8192",
            defaultModel = "llama-3.1-8b-instant",
            availableModels = listOf("llama-3.1-8b-instant", "llama-3.3-70b-versatile")
        )

        assertThat(recovered).isEqualTo("llama-3.1-8b-instant")
    }

    @Test
    fun `select recovery model falls back to first available when default is absent`() {
        val recovered = AiProviderSupport.selectRecoveryModel(
            currentModel = "removed-model",
            defaultModel = "missing-default",
            availableModels = listOf("gemini-2.5-flash", "gemini-2.5-pro")
        )

        assertThat(recovered).isEqualTo("gemini-2.5-flash")
    }

    @Test
    fun `provider exception detects model and billing issues`() {
        val notFound = AiProviderSupport.createException(
            providerName = "Groq",
            statusCode = 404,
            transportMessage = "Not Found",
            responseBody = """{"error":{"message":"The model was not found","code":"model_not_found"}}""",
            requestedModel = "removed-model"
        )
        val billing = AiProviderSupport.createException(
            providerName = "Mistral",
            statusCode = 402,
            transportMessage = "Payment Required",
            responseBody = """{"error":{"message":"Insufficient credits"}}""",
            requestedModel = "mistral-large-latest"
        )

        assertThat(notFound.isModelUnavailable()).isTrue()
        assertThat(billing.isBillingIssue()).isTrue()
    }
}
