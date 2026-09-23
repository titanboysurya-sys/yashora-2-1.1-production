package com.ritvyom.yashoraReelgenerator.data.ai

import android.content.Context
import android.util.Log
import com.ritvyom.yashoraReelgenerator.data.local.PreferencesManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Unified Multi-Provider BYOK Router & Fallback Coordinator.
 *
 * ARCHITECTURAL MANDATES:
 * 1. ZERO-DATA-LEAK: Raw API keys stay exclusively in SecureAiCredentialStore on-device.
 * 2. ORDERED FALLBACK: If Primary fails due to eligible error (Auth, Quota, Rate Limit, Network),
 *    smoothly switches to Secondary configured providers without dropping user workflows.
 * 3. GRACEFUL RECOVERY: Automatically respects cooldowns and circuit breaker rules.
 */
class UnifiedAiRouter(
    private val context: Context,
    val credentialStore: SecureAiCredentialStore,
    private val preferencesManager: PreferencesManager
) {

    companion object {
        private const val TAG = "UnifiedAiRouter"
        private const val COOLDOWN_DURATION_MS = 60_000L // 1 minute cooldown on quota trip
    }

    private val adapters: Map<ProviderId, AiProviderAdapter> = mapOf(
        ProviderId.GEMINI to GeminiProviderAdapter(),
        ProviderId.XAI to XAiGrokProviderAdapter(),
        ProviderId.GROQ to GroqProviderAdapter(),
        ProviderId.OPENAI to OpenAiProviderAdapter(),
        ProviderId.DEEPSEEK to DeepSeekProviderAdapter()
    )

    // Observable states for UI
    private val _providerConfigs = MutableStateFlow<Map<ProviderId, AiProviderConfig>>(emptyMap())
    val providerConfigs: StateFlow<Map<ProviderId, AiProviderConfig>> = _providerConfigs.asStateFlow()

    private val providerCooldowns = ConcurrentHashMap<ProviderId, Long>()
    private val isInitialized = AtomicBoolean(false)

    init {
        loadConfigs()
    }

    /**
     * Initializes and loads configs, including migrating any legacy Gemini key from PreferencesManager.
     */
    fun loadConfigs() {
        // Sync Gemini key from BuildConfig only if user has NOT explicitly removed it
        // and no key currently exists in secure store
        try {
            if (!credentialStore.hasApiKey(ProviderId.GEMINI) && !credentialStore.isRemovedByUser(ProviderId.GEMINI)) {
                val appGeminiKey = com.ritvyom.yashoraReelgenerator.BuildConfig.GEMINI_API_KEY.trim()
                if (appGeminiKey.isNotBlank()) {
                    credentialStore.saveApiKey(ProviderId.GEMINI, appGeminiKey, isUserProvided = false)
                }
            }
        } catch (_: Exception) {}

        val configs = mutableMapOf<ProviderId, AiProviderConfig>()
        ProviderId.values().forEachIndexed { index, providerId ->
            val hasKey = credentialStore.hasApiKey(providerId)
            val isUserKey = credentialStore.isUserProvided(providerId)
            val cooldown = providerCooldowns[providerId] ?: 0L
            val isCooling = cooldown > System.currentTimeMillis()

            val status = when {
                !hasKey -> ProviderStatus.NOT_CONNECTED
                isCooling -> ProviderStatus.QUOTA_LIMITED
                else -> ProviderStatus.READY
            }

            configs[providerId] = AiProviderConfig(
                providerId = providerId,
                isEnabled = true,
                priorityIndex = index,
                status = status,
                hasValidKey = hasKey,
                isUserProvided = isUserKey,
                cooldownUntilTimestamp = cooldown
            )
        }
        _providerConfigs.value = configs
    }

    /**
     * Updates an API key securely for a provider and refreshes configs.
     * Always treats user inputs as user-provided BYOK overrides.
     */
    fun setApiKey(providerId: ProviderId, apiKey: String) {
        if (apiKey.isBlank()) {
            credentialStore.removeApiKey(providerId)
            UserAiUsageTracker.resetUserUsage(providerId)
        } else {
            val wasUser = credentialStore.isUserProvided(providerId)
            credentialStore.saveApiKey(providerId, apiKey.trim(), isUserProvided = true)
            if (!wasUser) {
                UserAiUsageTracker.resetUserUsage(providerId)
            }
        }
        // Reset cooldown
        providerCooldowns.remove(providerId)
        loadConfigs()
    }

    /**
     * Removes an API key.
     */
    fun removeApiKey(providerId: ProviderId) {
        credentialStore.removeApiKey(providerId)
        UserAiUsageTracker.resetUserUsage(providerId)
        providerCooldowns.remove(providerId)
        loadConfigs()
    }

    /**
     * Quick connection test for a provider.
     */
    suspend fun testProvider(providerId: ProviderId): Result<String> = withContext(Dispatchers.IO) {
        val adapter = adapters[providerId] ?: return@withContext Result.failure(Exception("Adapter not found"))
        val key = credentialStore.getApiKey(providerId)
        if (key.isBlank()) {
            return@withContext Result.failure(Exception("No API key entered for ${providerId.displayName}"))
        }

        val result = adapter.testConnection(key)
        if (result.isSuccess) {
            updateProviderStatus(providerId, ProviderStatus.CONNECTED)
        } else {
            updateProviderStatus(providerId, ProviderStatus.INVALID_KEY, result.exceptionOrNull()?.message)
        }
        result
    }

    private fun updateProviderStatus(providerId: ProviderId, status: ProviderStatus, reason: String? = null) {
        val current = _providerConfigs.value.toMutableMap()
        val old = current[providerId] ?: AiProviderConfig(providerId)
        current[providerId] = old.copy(
            status = status,
            lastFailureReason = reason,
            lastTestedTimestamp = System.currentTimeMillis()
        )
        _providerConfigs.value = current
    }

    /**
     * Executes generation with intelligent fallback across all configured providers.
     * Guarantees that if any provider fails for ANY reason, the chain never stops
     * and continues through all available candidates until output is produced.
     */
     suspend fun executeWithFallback(
        request: AiGenerationRequest,
        preferredProvider: ProviderId? = null,
        allowUnifiedApiFailover: Boolean = true
    ): AiGenerationResult = withContext(Dispatchers.IO) {
        // Build candidate provider chain
        val candidates = mutableListOf<ProviderId>()
        if (preferredProvider != null && credentialStore.hasApiKey(preferredProvider)) {
            candidates.add(preferredProvider)
        }

        // Add remaining configured providers in prioritized failover order
        val priorityOrder = listOf(
            ProviderId.GROQ,
            ProviderId.OPENAI,
            ProviderId.DEEPSEEK,
            ProviderId.XAI,
            ProviderId.GEMINI
        )
        priorityOrder.forEach { p ->
            if (p != preferredProvider && credentialStore.hasApiKey(p)) {
                candidates.add(p)
            }
        }
        ProviderId.values().forEach { p ->
            if (!candidates.contains(p) && credentialStore.hasApiKey(p)) {
                candidates.add(p)
            }
        }

        var lastException: Exception? = null

        for ((index, providerId) in candidates.withIndex() ) {
            // Check cooldown
            val cooldown = providerCooldowns[providerId] ?: 0L
            if (cooldown > System.currentTimeMillis()) {
                Log.i(TAG, "Skipping $providerId (In cooldown until $cooldown)")
                continue
            }

            val adapter = adapters[providerId] ?: continue
            val key = credentialStore.getApiKey(providerId)
            if (key.isBlank()) continue

            try {
                Log.i(TAG, "Executing AI request with $providerId (attempt ${index + 1}/${candidates.size})...")
                val result = adapter.generate(request, key)
                // Success: mark provider as ready and clear cooldown
                providerCooldowns.remove(providerId)
                updateProviderStatus(providerId, ProviderStatus.READY)

                // Track strictly for user's personal BYOK key (Never track global/vertex for user usage)
                if (credentialStore.isUserProvided(providerId)) {
                    val estTokens = (request.prompt.length + result.text.length) / 4
                    UserAiUsageTracker.recordUserRequest(
                        providerId = providerId,
                        isSuccess = true,
                        model = result.modelUsed.ifBlank { adapter.defaultModel },
                        estimatedTokens = estTokens
                    )
                }

                return@withContext result.copy(isFallbackUsed = index > 0)
            } catch (e: Exception) {
                lastException = e
                Log.w(TAG, "Provider $providerId failed (${e.message}). Proceeding seamlessly to next provider in fallback chain...")

                if (credentialStore.isUserProvided(providerId)) {
                    UserAiUsageTracker.recordUserRequest(
                        providerId = providerId,
                        isSuccess = false,
                        model = adapter.defaultModel,
                        errorMessage = e.message
                    )
                }

                if (e is AiProviderException) {
                    if (e.errorType == AiErrorType.RATE_LIMITED || e.errorType == AiErrorType.QUOTA_EXCEEDED) {
                        providerCooldowns[providerId] = System.currentTimeMillis() + 15_000L // 15 sec transient backoff
                        updateProviderStatus(providerId, ProviderStatus.QUOTA_LIMITED, e.message)
                    } else if (e.errorType == AiErrorType.INVALID_API_KEY || e.errorType == AiErrorType.AUTHENTICATION_ERROR) {
                        updateProviderStatus(providerId, ProviderStatus.INVALID_KEY, e.message)
                    }
                }
                // Under NO circumstance stop here! Proceed to next candidate provider so workflow NEVER stops!
                continue
            }
        }

        // If all configured BYOK providers failed or none configured, attempt secondary failover via UnifiedApiClient
        if (allowUnifiedApiFailover) {
            try {
                Log.i(TAG, "Attempting UnifiedApiClient resilient failover for AI generation...")
                val preferredKey = if (preferredProvider != null) credentialStore.getApiKey(preferredProvider) else ""
                val failoverText = com.ritvyom.yashoraReelgenerator.data.remote.UnifiedApiClient.generateContent(
                    prompt = request.prompt,
                    systemInstruction = request.systemInstruction,
                    customApiKey = preferredKey
                )
                if (failoverText.isNotBlank()) {
                    Log.i(TAG, "Resilient UnifiedApiClient failover succeeded!")
                    return@withContext AiGenerationResult(
                        text = failoverText,
                        providerUsed = ProviderId.GEMINI,
                        modelUsed = "gemini-failover",
                        isFallbackUsed = true
                    )
                }
            } catch (failoverEx: Exception) {
                Log.w(TAG, "UnifiedApiClient failover attempt also encountered issue: ${failoverEx.message}")
            }
        }

        // If all configured providers and failovers were exhausted, throw lastException
        throw lastException ?: AiProviderException(
            errorType = AiErrorType.PROVIDER_UNAVAILABLE,
            message = "No configured AI provider was able to fulfill this request."
        )
    }

    /**
     * Checks if at least one AI provider has an active key configured.
     */
    fun hasAnyActiveProvider(): Boolean {
        return ProviderId.values().any { credentialStore.hasApiKey(it) } ||
               com.ritvyom.yashoraReelgenerator.BuildConfig.GEMINI_API_KEY.isNotBlank()
    }
}
