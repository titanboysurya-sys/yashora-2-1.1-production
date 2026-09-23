package com.ritvyom.yashoraReelgenerator.data.ai

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Generic OpenAI Compatible Adapter (Works for OpenAI and DeepSeek).
 */
open class OpenAiCompatibleAdapter(
    override val providerId: ProviderId,
    private val baseUrl: String,
    override val availableModels: List<String>,
    override val defaultModel: String,
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(25, TimeUnit.SECONDS)
        .readTimeout(40, TimeUnit.SECONDS)
        .build()
) : AiProviderAdapter {

    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }

    override val supportedCapabilities: Set<AiCapability> = setOf(
        AiCapability.TEXT_GENERATION,
        AiCapability.CHAT,
        AiCapability.SCRIPT_GENERATION,
        AiCapability.TRANSLATION,
        AiCapability.REWRITING,
        AiCapability.STORY_GENERATION,
        AiCapability.PROMPT_GENERATION
    )

    override suspend fun generate(
        request: AiGenerationRequest,
        apiKey: String,
        modelOverride: String?
    ): AiGenerationResult = withContext(Dispatchers.IO) {
        val model = if (!modelOverride.isNullOrBlank() && modelOverride != "auto") modelOverride else defaultModel
        val url = "$baseUrl/chat/completions"

        val rootJson = JSONObject()
        rootJson.put("model", model)
        rootJson.put("temperature", request.temperature)
        rootJson.put("max_tokens", request.maxTokens)

        val messages = JSONArray()
        if (!request.systemInstruction.isNullOrBlank()) {
            messages.put(JSONObject().apply {
                put("role", "system")
                put("content", request.systemInstruction)
            })
        }
        messages.put(JSONObject().apply {
            put("role", "user")
            put("content", request.prompt)
        })
        rootJson.put("messages", messages)

        if (request.responseMimeType == "application/json" || request.responseJsonSchema != null) {
            rootJson.put("response_format", JSONObject().put("type", "json_object"))
        }

        val reqBody = rootJson.toString().toRequestBody(JSON_MEDIA_TYPE)
        val httpRequest = Request.Builder()
            .url(url)
            .post(reqBody)
            .header("Authorization", "Bearer $apiKey")
            .header("Content-Type", "application/json")
            .header("User-Agent", "Yashora/2.0 (Android; BYOK)")
            .build()

        try {
            val response = httpClient.newCall(httpRequest).execute()
            val code = response.code
            val bodyString = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                val parsedErrorMsg = try {
                    val errJson = JSONObject(bodyString)
                    val errObj = errJson.optJSONObject("error")
                    errObj?.optString("message")
                        ?: errJson.optString("error").ifBlank { null }
                        ?: errJson.optString("message").ifBlank { null }
                } catch (_: Exception) {
                    null
                }

                val errorType = when (code) {
                    401, 403 -> AiErrorType.INVALID_API_KEY
                    429 -> AiErrorType.RATE_LIMITED
                    404 -> AiErrorType.MODEL_UNAVAILABLE
                    in 500..599 -> AiErrorType.PROVIDER_UNAVAILABLE
                    else -> AiErrorType.COMPATIBLE_API_FAILURE
                }

                val finalMessage = when {
                    code == 403 && providerId == ProviderId.XAI -> {
                        val base = parsedErrorMsg?.let { " ($it)" } ?: ""
                        "xAI (Grok) 403 Forbidden$base: 1) console.x.ai पर Billing में Prepaid Credits चेक करें (Twitter/X Premium API के लिए नहीं होता)। 2) API Key में 'All Endpoints/Models' अनुमति सक्षम करें।"
                    }
                    parsedErrorMsg != null -> "${providerId.displayName} ($code): $parsedErrorMsg"
                    else -> "${providerId.displayName} API error HTTP $code"
                }

                throw AiProviderException(errorType, finalMessage, providerId, code)
            }

            val json = JSONObject(bodyString)
            val choices = json.optJSONArray("choices")
            val firstChoice = choices?.optJSONObject(0)
            val message = firstChoice?.optJSONObject("message")
            val content = message?.optString("content", "") ?: ""

            AiGenerationResult(
                text = content,
                providerUsed = providerId,
                modelUsed = model,
                rawResponse = bodyString
            )
        } catch (e: Exception) {
            if (e is AiProviderException) throw e
            if (e is IOException) {
                throw AiProviderException(AiErrorType.NETWORK_ERROR, "Network error connecting to ${providerId.displayName}: ${e.message}", providerId, cause = e)
            }
            throw AiProviderException(AiErrorType.UNKNOWN_PROVIDER_ERROR, e.message ?: "Execution error", providerId, cause = e)
        }
    }

    override suspend fun testConnection(apiKey: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$baseUrl/models")
                .get()
                .header("Authorization", "Bearer $apiKey")
                .header("User-Agent", "Yashora/2.0 (Android; BYOK-Test)")
                .build()

            val response = httpClient.newCall(request).execute()
            val code = response.code
            val bodyString = response.body?.string() ?: ""
            if (response.isSuccessful) {
                Result.success("Connection Successful (${providerId.displayName} active)")
            } else {
                val parsedErrorMsg = try {
                    val errJson = JSONObject(bodyString)
                    val errObj = errJson.optJSONObject("error")
                    errObj?.optString("message")
                        ?: errJson.optString("error").ifBlank { null }
                        ?: errJson.optString("message").ifBlank { null }
                } catch (_: Exception) {
                    null
                }

                val msg = when {
                    code == 401 -> "Invalid API key"
                    code == 403 && providerId == ProviderId.XAI -> {
                        val base = parsedErrorMsg?.let { " ($it)" } ?: ""
                        "xAI 403 Forbidden$base: 1) console.x.ai पर Prepaid Credits चेक करें (Twitter/X Premium API के लिए नहीं होता)। 2) Key Permissions में 'All Endpoints & Models' चालू रखें।"
                    }
                    code == 403 -> "Forbidden (HTTP 403)${if (parsedErrorMsg != null) ": $parsedErrorMsg" else ". Check key permissions & credits."}"
                    parsedErrorMsg != null -> "HTTP $code: $parsedErrorMsg"
                    else -> "Returned HTTP $code"
                }
                Result.failure(AiProviderException(AiErrorType.AUTHENTICATION_ERROR, msg, providerId, code))
            }
        } catch (e: Exception) {
            Result.failure(AiProviderException(AiErrorType.NETWORK_ERROR, "Network error: ${e.localizedMessage}", providerId, cause = e))
        }
    }
}

/**
 * Concrete adapter for OpenAI.
 */
class OpenAiProviderAdapter : OpenAiCompatibleAdapter(
    providerId = ProviderId.OPENAI,
    baseUrl = "https://api.openai.com/v1",
    availableModels = listOf("gpt-4o-mini", "gpt-4o", "gpt-3.5-turbo"),
    defaultModel = "gpt-4o-mini"
)

/**
 * Concrete adapter for DeepSeek.
 */
class DeepSeekProviderAdapter : OpenAiCompatibleAdapter(
    providerId = ProviderId.DEEPSEEK,
    baseUrl = "https://api.deepseek.com",
    availableModels = listOf("deepseek-chat", "deepseek-reasoner"),
    defaultModel = "deepseek-chat"
)

/**
 * Concrete adapter for xAI (Grok).
 * Official endpoint: https://api.x.ai/v1
 * Models: grok-3-mini, grok-3, grok-4-fast, grok-4, grok-4.5
 */
class XAiGrokProviderAdapter : OpenAiCompatibleAdapter(
    providerId = ProviderId.XAI,
    baseUrl = "https://api.x.ai/v1",
    availableModels = listOf("grok-3-mini", "grok-3", "grok-2-1212", "grok-2", "grok-beta", "grok-4-fast", "grok-4", "grok-4.5"),
    defaultModel = "grok-3-mini"
) {
    override suspend fun generate(
        request: AiGenerationRequest,
        apiKey: String,
        modelOverride: String?
    ): AiGenerationResult {
        val cleanKey = apiKey.trim().removeSurrounding("\"").removeSurrounding("'")
        return try {
            super.generate(request, cleanKey, modelOverride)
        } catch (e: AiProviderException) {
            // If requested model was retired or not found, try fallback models
            val candidateFallbacks = listOf("grok-3-mini", "grok-2-1212", "grok-2", "grok-beta")
            val nextModel = candidateFallbacks.firstOrNull { it != modelOverride }
            if ((e.message?.contains("not found", ignoreCase = true) == true || e.statusCode == 404) && nextModel != null) {
                super.generate(request, cleanKey, nextModel)
            } else {
                throw e
            }
        }
    }

    override suspend fun testConnection(apiKey: String): Result<String> = withContext(Dispatchers.IO) {
        val cleanKey = apiKey.trim().removeSurrounding("\"").removeSurrounding("'")
        // 1. First test via standard /models
        val baseTest = super.testConnection(cleanKey)
        if (baseTest.isSuccess) return@withContext baseTest

        // 2. If /models failed with 403/404, try lightweight chat completions probes across models.
        // Many xAI API keys are issued with ACL scoped specifically to chat completions rather than models.
        val testModels = listOf("grok-3-mini", "grok-2-1212", "grok-2", "grok-beta")
        var lastDiagnostic: String? = null
        var lastCode = 403

        val client = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()

        for (testModel in testModels) {
            try {
                val probeBody = JSONObject().apply {
                    put("model", testModel)
                    put("max_tokens", 1)
                    put("messages", JSONArray().apply {
                        put(JSONObject().apply {
                            put("role", "user")
                            put("content", "hi")
                        })
                    })
                }.toString().toRequestBody("application/json; charset=utf-8".toMediaType())

                val probeRequest = Request.Builder()
                    .url("https://api.x.ai/v1/chat/completions")
                    .post(probeBody)
                    .header("Authorization", "Bearer $cleanKey")
                    .header("Content-Type", "application/json")
                    .header("User-Agent", "Yashora/2.0 (Android; BYOK-Test)")
                    .build()

                val probeResp = client.newCall(probeRequest).execute()
                val probeCode = probeResp.code
                val probeBodyStr = probeResp.body?.string() ?: ""

                if (probeResp.isSuccessful) {
                    return@withContext Result.success("Connection Successful (xAI Grok active with $testModel)")
                }

                lastCode = probeCode
                val parsedProbeErr = try {
                    val j = JSONObject(probeBodyStr)
                    val errObj = j.optJSONObject("error")
                    errObj?.optString("message") ?: j.optString("error").ifBlank { null }
                } catch (_: Exception) { null }

                lastDiagnostic = when (probeCode) {
                    403 -> {
                        val detail = parsedProbeErr?.let { " ($it)" } ?: ""
                        "xAI 403 Forbidden$detail: 1) console.x.ai पर Prepaid Credits चेक करें (Twitter/X Premium ऐप का सब्सक्रिप्शन API के लिए अलग होता है)। 2) console.x.ai में API Key बनाते समय 'All Endpoints & Models' Permissions चालू रखें।"
                    }
                    401 -> "Invalid xAI API Key (Check key in console.x.ai)"
                    else -> parsedProbeErr ?: "xAI returned HTTP $probeCode"
                }

                // If error is 403 or 401, trying other models won't change auth/billing permissions
                if (probeCode == 403 || probeCode == 401) {
                    break
                }
            } catch (_: Exception) {
                // Try next candidate model
            }
        }

        val finalMsg = lastDiagnostic ?: baseTest.exceptionOrNull()?.message ?: "xAI connection failed (HTTP $lastCode)"
        Result.failure(AiProviderException(AiErrorType.AUTHENTICATION_ERROR, finalMsg, ProviderId.XAI, lastCode))
    }
}

/**
 * Generates an image using OpenAI DALL-E 3 / DALL-E 2.
 */
suspend fun generateOpenAiImage(
    prompt: String,
    apiKey: String,
    size: String = "1024x1024",
    httpClient: OkHttpClient = OkHttpClient()
): Result<String> = withContext(Dispatchers.IO) {
    try {
        val rootJson = JSONObject().apply {
            put("model", "dall-e-3")
            put("prompt", prompt)
            put("n", 1)
            put("size", size)
        }
        val mediaType = "application/json; charset=utf-8".toMediaType()
        val reqBody = rootJson.toString().toRequestBody(mediaType)
        val httpRequest = Request.Builder()
            .url("https://api.openai.com/v1/images/generations")
            .post(reqBody)
            .header("Authorization", "Bearer $apiKey")
            .header("Content-Type", "application/json")
            .build()
        val response = httpClient.newCall(httpRequest).execute()
        val bodyString = response.body?.string() ?: ""
        if (!response.isSuccessful) {
            val errObj = try { JSONObject(bodyString).optJSONObject("error") } catch (e: Exception) { null }
            val errMsg = errObj?.optString("message") ?: "HTTP ${response.code}"
            return@withContext Result.failure(Exception(errMsg))
        }
        val json = JSONObject(bodyString)
        val dataArr = json.optJSONArray("data")
        val first = dataArr?.optJSONObject(0)
        val url = first?.optString("url") ?: ""
        if (url.isNotEmpty()) {
            Result.success(url)
        } else {
            Result.failure(Exception("No image URL returned"))
        }
    } catch (e: Exception) {
        Result.failure(e)
    }
}
