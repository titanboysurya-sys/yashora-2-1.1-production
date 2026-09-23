package com.ritvyom.yashoraReelgenerator.data.ai

/**
 * Common AI capabilities supported across providers.
 */
enum class AiCapability {
    TEXT_GENERATION,
    CHAT,
    SCRIPT_GENERATION,
    TRANSLATION,
    IMAGE_GENERATION,
    VIDEO_GENERATION,
    MULTIMODAL,
    REWRITING,
    STORY_GENERATION,
    PROMPT_GENERATION
}

/**
 * Supported AI Provider IDs.
 */
enum class ProviderId(val displayName: String, val websiteUrl: String) {
    GEMINI("Google Gemini", "https://aistudio.google.com"),
    XAI("xAI (Grok)", "https://console.x.ai"),
    GROQ("Groq", "https://console.groq.com/keys"),
    OPENAI("OpenAI API", "https://platform.openai.com/api-keys"),
    DEEPSEEK("DeepSeek", "https://platform.deepseek.com"),
    CLAUDE("Anthropic Claude", "https://console.anthropic.com")
}

/**
 * Health and authentication state of an AI Provider.
 */
enum class ProviderStatus(val label: String) {
    NOT_CONNECTED("Not Connected"),
    CONNECTED("Connected"),
    TESTING("Testing..."),
    READY("Ready"),
    INVALID_KEY("Invalid API Key"),
    QUOTA_LIMITED("Quota Limited"),
    TEMPORARILY_UNAVAILABLE("Temporarily Unavailable"),
    DISABLED("Disabled")
}

/**
 * Standardized AI error categories to distinguish provider failures (eligible for fallback)
 * from content/user errors (which should NOT trigger endless fallback).
 */
enum class AiErrorType(val canFallback: Boolean) {
    AUTHENTICATION_ERROR(canFallback = true),
    INVALID_API_KEY(canFallback = true),
    QUOTA_EXCEEDED(canFallback = true),
    RATE_LIMITED(canFallback = true),
    MODEL_UNAVAILABLE(canFallback = true),
    NETWORK_ERROR(canFallback = true),
    TIMEOUT(canFallback = true),
    PROVIDER_UNAVAILABLE(canFallback = true),
    COMPATIBLE_API_FAILURE(canFallback = true),
    INVALID_REQUEST(canFallback = false),
    CONTENT_REJECTED(canFallback = false),
    SAFETY_POLICY_VIOLATION(canFallback = false),
    UNSUPPORTED_CAPABILITY(canFallback = false),
    UNKNOWN_PROVIDER_ERROR(canFallback = true)
}

/**
 * Structured exception for AI provider operations.
 */
class AiProviderException(
    val errorType: AiErrorType,
    message: String,
    val providerId: ProviderId? = null,
    val statusCode: Int? = null,
    cause: Throwable? = null
) : Exception(message, cause)

/**
 * Common AI generation request model.
 */
data class AiGenerationRequest(
    val prompt: String,
    val capability: AiCapability = AiCapability.TEXT_GENERATION,
    val systemInstruction: String? = null,
    val temperature: Float = 0.7f,
    val maxTokens: Int = 2048,
    val responseJsonSchema: String? = null,
    val responseMimeType: String? = null,
    val imageWidth: Int = 1080,
    val imageHeight: Int = 1920,
    val customContext: Map<String, Any> = emptyMap()
)

/**
 * Common AI generation result model.
 */
data class AiGenerationResult(
    val text: String = "",
    val imageUrls: List<String> = emptyList(),
    val videoUrl: String? = null,
    val providerUsed: ProviderId? = null,
    val modelUsed: String = "",
    val isFallbackUsed: Boolean = false,
    val rawResponse: String? = null
)

/**
 * Provider-specific configuration stored locally.
 * Note: Raw API keys are stored separately in Android Keystore / EncryptedSharedPreferences!
 */
data class AiProviderConfig(
    val providerId: ProviderId,
    val isEnabled: Boolean = true,
    val selectedModel: String = "auto",
    val priorityIndex: Int = 0,
    val status: ProviderStatus = ProviderStatus.NOT_CONNECTED,
    val lastTestedTimestamp: Long = 0L,
    val lastFailureReason: String? = null,
    val cooldownUntilTimestamp: Long = 0L,
    val hasValidKey: Boolean = false,
    val isUserProvided: Boolean = false
) {
    fun isAvailable(): Boolean {
        if (!isEnabled || !hasValidKey) return false
        if (status == ProviderStatus.INVALID_KEY || status == ProviderStatus.DISABLED) return false
        if (cooldownUntilTimestamp > System.currentTimeMillis()) return false
        return true
    }
}
