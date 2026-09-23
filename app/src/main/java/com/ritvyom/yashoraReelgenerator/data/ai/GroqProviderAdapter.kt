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
 * Adapter for Groq (Ultra-fast LLM inference, OpenAI-compatible API).
 */
class GroqProviderAdapter(
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
) : AiProviderAdapter {

    companion object {
        private const val TAG = "GroqAdapter"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }

    override val providerId: ProviderId = ProviderId.GROQ

    override val supportedCapabilities: Set<AiCapability> = setOf(
        AiCapability.TEXT_GENERATION,
        AiCapability.CHAT,
        AiCapability.SCRIPT_GENERATION,
        AiCapability.TRANSLATION,
        AiCapability.REWRITING,
        AiCapability.STORY_GENERATION,
        AiCapability.PROMPT_GENERATION
    )

    override val availableModels: List<String> = listOf(
        "llama-3.3-70b-versatile",
        "llama-3.1-8b-instant",
        "mixtral-8x7b-32768",
        "gemma2-9b-it"
    )

    override val defaultModel: String = "llama-3.3-70b-versatile"

    override suspend fun generate(
        request: AiGenerationRequest,
        apiKey: String,
        modelOverride: String?
    ): AiGenerationResult = withContext(Dispatchers.IO) {
        val model = if (!modelOverride.isNullOrBlank() && modelOverride != "auto") modelOverride else defaultModel
        val url = "https://api.groq.com/openai/v1/chat/completions"

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
                val errorType = when (code) {
                    401, 403 -> AiErrorType.INVALID_API_KEY
                    429 -> AiErrorType.RATE_LIMITED
                    404 -> AiErrorType.MODEL_UNAVAILABLE
                    in 500..599 -> AiErrorType.PROVIDER_UNAVAILABLE
                    else -> AiErrorType.COMPATIBLE_API_FAILURE
                }
                throw AiProviderException(errorType, "Groq API error HTTP $code", providerId, code)
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
                throw AiProviderException(AiErrorType.NETWORK_ERROR, "Network error connecting to Groq: ${e.message}", providerId, cause = e)
            }
            throw AiProviderException(AiErrorType.UNKNOWN_PROVIDER_ERROR, e.message ?: "Groq error", providerId, cause = e)
        }
    }

    override suspend fun testConnection(apiKey: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("https://api.groq.com/openai/v1/models")
                .get()
                .header("Authorization", "Bearer $apiKey")
                .header("User-Agent", "Yashora/2.0 (Android; BYOK-Test)")
                .build()

            val response = httpClient.newCall(request).execute()
            val code = response.code
            if (response.isSuccessful) {
                Result.success("Connection Successful (Groq Cloud active)")
            } else {
                val msg = if (code == 401) "Invalid API key" else "Returned HTTP $code"
                Result.failure(AiProviderException(AiErrorType.AUTHENTICATION_ERROR, msg, providerId, code))
            }
        } catch (e: Exception) {
            Result.failure(AiProviderException(AiErrorType.NETWORK_ERROR, "Network error: ${e.localizedMessage}", providerId, cause = e))
        }
    }
}
