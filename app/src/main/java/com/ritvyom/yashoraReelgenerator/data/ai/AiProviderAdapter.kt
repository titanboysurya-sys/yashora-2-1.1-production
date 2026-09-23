package com.ritvyom.yashoraReelgenerator.data.ai

/**
 * Interface defining an AI Provider adapter.
 * Each provider (Gemini, Groq, OpenAI, DeepSeek, Claude) implements this interface.
 */
interface AiProviderAdapter {
    val providerId: ProviderId
    val supportedCapabilities: Set<AiCapability>
    val availableModels: List<String>
    val defaultModel: String

    /**
     * Executes a generation request with the provider.
     * Throws [AiProviderException] on error with classified [AiErrorType].
     */
    suspend fun generate(
        request: AiGenerationRequest,
        apiKey: String,
        modelOverride: String? = null
    ): AiGenerationResult

    /**
     * Minimal, lightweight connectivity test that avoids expensive generation requests.
     */
    suspend fun testConnection(apiKey: String): Result<String>
}
