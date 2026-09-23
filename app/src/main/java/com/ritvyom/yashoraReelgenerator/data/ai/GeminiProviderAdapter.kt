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
 * Adapter for user-provided Google Gemini API.
 */
class GeminiProviderAdapter(
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(25, TimeUnit.SECONDS)
        .readTimeout(35, TimeUnit.SECONDS)
        .writeTimeout(35, TimeUnit.SECONDS)
        .build()
) : AiProviderAdapter {

    companion object {
        private const val TAG = "GeminiAdapter"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }

    override val providerId: ProviderId = ProviderId.GEMINI

    override val supportedCapabilities: Set<AiCapability> = setOf(
        AiCapability.TEXT_GENERATION,
        AiCapability.CHAT,
        AiCapability.SCRIPT_GENERATION,
        AiCapability.TRANSLATION,
        AiCapability.REWRITING,
        AiCapability.STORY_GENERATION,
        AiCapability.PROMPT_GENERATION,
        AiCapability.MULTIMODAL
    )

    override val availableModels: List<String> = listOf(
        "gemini-2.5-flash",
        "gemini-flash-latest",
        "gemini-3.1-pro-preview",
        "gemini-3.1-flash-lite-preview"
    )

    override val defaultModel: String = "gemini-2.5-flash"

    override suspend fun generate(
        request: AiGenerationRequest,
        apiKey: String,
        modelOverride: String?
    ): AiGenerationResult = withContext(Dispatchers.IO) {
        val requestedModel = if (!modelOverride.isNullOrBlank() && modelOverride != "auto") modelOverride else defaultModel
        val modelsToTry = mutableListOf(requestedModel)
        for (m in availableModels) {
            if (!modelsToTry.contains(m)) {
                modelsToTry.add(m)
            }
        }

        val rootJson = JSONObject()
        val contentsArray = JSONArray()
        val contentObj = JSONObject()
        val partsArray = JSONArray()
        partsArray.put(JSONObject().put("text", request.prompt))
        contentObj.put("parts", partsArray)
        contentsArray.put(contentObj)
        rootJson.put("contents", contentsArray)

        if (!request.systemInstruction.isNullOrBlank()) {
            val sysInstructionObj = JSONObject()
            val sysParts = JSONArray()
            sysParts.put(JSONObject().put("text", request.systemInstruction))
            sysInstructionObj.put("parts", sysParts)
            rootJson.put("systemInstruction", sysInstructionObj)
        }

        val genConfig = JSONObject()
        genConfig.put("temperature", request.temperature)
        genConfig.put("maxOutputTokens", request.maxTokens)

        if (!request.responseMimeType.isNullOrBlank()) {
            genConfig.put("responseMimeType", request.responseMimeType)
        }
        if (!request.responseJsonSchema.isNullOrBlank()) {
            try {
                genConfig.put("responseSchema", JSONObject(request.responseJsonSchema))
            } catch (_: Exception) {}
        }
        rootJson.put("generationConfig", genConfig)

        val reqBody = rootJson.toString().toRequestBody(JSON_MEDIA_TYPE)

        var lastException: AiProviderException? = null

        for ((attemptIndex, model) in modelsToTry.withIndex()) {
            val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey"
            val httpRequest = Request.Builder()
                .url(url)
                .post(reqBody)
                .header("User-Agent", "Yashora/2.0 (Android; BYOK)")
                .build()

            val maxRetries = 1
            for (retry in 0..maxRetries) {
                try {
                    val response = httpClient.newCall(httpRequest).execute()
                    val responseCode = response.code
                    val bodyString = response.body?.string() ?: ""

                    if (response.isSuccessful) {
                        val json = JSONObject(bodyString)
                        val candidates = json.optJSONArray("candidates")
                        val firstCandidate = candidates?.optJSONObject(0)
                        val finishReason = firstCandidate?.optString("finishReason", "")
                        if (finishReason.equals("SAFETY", ignoreCase = true) || finishReason.equals("BLOCKED", ignoreCase = true)) {
                            throw AiProviderException(
                                errorType = AiErrorType.CONTENT_REJECTED,
                                message = "Prompt was flagged by provider safety policies",
                                providerId = providerId
                            )
                        }

                        val content = firstCandidate?.optJSONObject("content")
                        val parts = content?.optJSONArray("parts")
                        val sb = StringBuilder()
                        if (parts != null) {
                            for (i in 0 until parts.length()) {
                                sb.append(parts.optJSONObject(i)?.optString("text") ?: "")
                            }
                        }

                        val generatedText = sb.toString()
                        if (generatedText.isNotBlank()) {
                            Log.i(TAG, "Gemini generation succeeded with model $model (attempt $attemptIndex, retry $retry)")
                            return@withContext AiGenerationResult(
                                text = generatedText,
                                providerUsed = providerId,
                                modelUsed = model,
                                rawResponse = bodyString
                            )
                        }
                    }

                    // Extract detailed message from Google API error payload if present
                    val googleErrorMessage = try {
                        val errObj = JSONObject(bodyString).optJSONObject("error")
                        errObj?.optString("message")?.takeIf { it.isNotBlank() }
                    } catch (_: Exception) { null }

                    val errorType = when (responseCode) {
                        400 -> if (bodyString.contains("API_KEY_INVALID", ignoreCase = true) || bodyString.contains("key invalid", ignoreCase = true)) {
                            AiErrorType.INVALID_API_KEY
                        } else if (bodyString.contains("SAFETY", ignoreCase = true) || bodyString.contains("blocked", ignoreCase = true)) {
                            AiErrorType.CONTENT_REJECTED
                        } else {
                            AiErrorType.INVALID_REQUEST
                        }
                        401, 403 -> AiErrorType.AUTHENTICATION_ERROR
                        429 -> AiErrorType.RATE_LIMITED
                        404 -> AiErrorType.MODEL_UNAVAILABLE
                        in 500..599 -> AiErrorType.PROVIDER_UNAVAILABLE
                        else -> AiErrorType.UNKNOWN_PROVIDER_ERROR
                    }

                    val friendlyMessage = when {
                        responseCode == 503 -> googleErrorMessage ?: "Google Gemini servers are temporarily overloaded (HTTP 503)."
                        responseCode == 429 -> googleErrorMessage ?: "Gemini API rate limit reached (HTTP 429)."
                        responseCode in 401..403 -> "Invalid or unauthorized Gemini API key. Please check in Settings."
                        !googleErrorMessage.isNullOrBlank() -> googleErrorMessage
                        else -> "Gemini API failed with HTTP $responseCode"
                    }

                    lastException = AiProviderException(
                        errorType = errorType,
                        message = friendlyMessage,
                        providerId = providerId,
                        statusCode = responseCode
                    )

                    // If fatal authentication or content rejection, do not retry or cycle models
                    if (!errorType.canFallback) {
                        throw lastException
                    }

                    // If transient 503 / 429 or 5xx, back off and retry same model once
                    if (responseCode == 503 || responseCode == 429 || responseCode in 500..599) {
                        Log.w(TAG, "Gemini model $model returned HTTP $responseCode on retry $retry: $friendlyMessage")
                        if (retry < maxRetries) {
                            kotlinx.coroutines.delay(1000L * (retry + 1))
                            continue
                        }
                    }

                    // Model returned 404 or persistent 503; break retry loop to try next model
                    break
                } catch (e: Exception) {
                    if (e is AiProviderException && !e.errorType.canFallback) throw e
                    if (e is IOException) {
                        lastException = AiProviderException(AiErrorType.NETWORK_ERROR, "Network connection error: ${e.message}", providerId, cause = e)
                        if (retry < maxRetries) {
                            kotlinx.coroutines.delay(1000L)
                            continue
                        }
                    } else if (e is AiProviderException) {
                        lastException = e
                    } else {
                        lastException = AiProviderException(AiErrorType.UNKNOWN_PROVIDER_ERROR, e.message ?: "Gemini execution error", providerId, cause = e)
                    }
                    break
                }
            }
        }

        throw lastException ?: AiProviderException(
            errorType = AiErrorType.PROVIDER_UNAVAILABLE,
            message = "Gemini API was temporarily unavailable. Please try again in a few seconds.",
            providerId = providerId
        )
    }

    override suspend fun testConnection(apiKey: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val url = "https://generativelanguage.googleapis.com/v1beta/models?key=$apiKey"
            val request = Request.Builder()
                .url(url)
                .get()
                .header("User-Agent", "Yashora/2.0 (Android; BYOK-Test)")
                .build()

            val response = httpClient.newCall(request).execute()
            val code = response.code
            val body = response.body?.string() ?: ""

            if (response.isSuccessful) {
                Result.success("Connection Successful (Google Gemini API ready)")
            } else {
                val errorMsg = when (code) {
                    400, 403 -> "Invalid API Key. Please verify in Google AI Studio."
                    429 -> "Quota exceeded or rate limited."
                    else -> "Connection test returned HTTP $code"
                }
                Result.failure(AiProviderException(AiErrorType.AUTHENTICATION_ERROR, errorMsg, providerId, code))
            }
        } catch (e: Exception) {
            Result.failure(AiProviderException(AiErrorType.NETWORK_ERROR, "Network error: ${e.localizedMessage}", providerId, cause = e))
        }
    }
}
