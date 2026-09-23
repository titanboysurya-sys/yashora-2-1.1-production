package com.ritvyom.yashoraReelgenerator.data.api

import org.json.JSONObject
import retrofit2.HttpException
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

data class ElevenLabsErrorResult(
    val headline: String = "API Error",
    val userFriendlyMessage: String,
    val isModelOrLanguageMismatch: Boolean = false,
    val isApiKeyInvalid: Boolean = false,
    val isQuotaExceeded: Boolean = false,
    val isNetworkError: Boolean = false,
    val httpStatusCode: Int? = null,
    val fallbackModelId: String? = null
)

object ElevenLabsErrorHandler {

    /**
     * Safely executes an ElevenLabs API block and returns Kotlin Result.
     */
    suspend fun <T> safeApiCall(
        block: suspend () -> T
    ): Result<T> {
        return try {
            Result.success(block())
        } catch (e: Throwable) {
            Result.failure(e)
        }
    }

    /**
     * Parses any Exception into a user-friendly error result with explicit model and language mismatch explanations.
     */
    fun parseError(
        e: Throwable,
        currentModelId: String? = null,
        actionName: String = "ElevenLabs API Request"
    ): ElevenLabsErrorResult {
        return when (e) {
            is HttpException -> {
                val code = e.code()
                val rawBody = try { e.response()?.errorBody()?.string() } catch (_: Exception) { null }
                val detailMsg = parseJsonDetail(rawBody)

                when (code) {
                    401 -> ElevenLabsErrorResult(
                        headline = "Invalid API Key",
                        userFriendlyMessage = "Authentication Failed (401): The provided ElevenLabs API key is invalid or expired. Please check your key in settings.",
                        isApiKeyInvalid = true,
                        httpStatusCode = code
                    )
                    403 -> ElevenLabsErrorResult(
                        headline = "Permission Denied",
                        userFriendlyMessage = "Access Forbidden (403): Your subscription tier or API key lacks permissions for $actionName.",
                        httpStatusCode = code
                    )
                    404 -> ElevenLabsErrorResult(
                        headline = "Model / Voice Not Found",
                        userFriendlyMessage = "Model/Voice Error (404): The selected model '${currentModelId ?: "unknown"}' or voice ID was not found or is deprecated. Auto-switched to 'Multilingual v2'.",
                        isModelOrLanguageMismatch = true,
                        fallbackModelId = "eleven_multilingual_v2",
                        httpStatusCode = code
                    )
                    422 -> ElevenLabsErrorResult(
                        headline = "Model & Language Mismatch",
                        userFriendlyMessage = "Model/Language Error (422): ${detailMsg ?: "The selected model '${currentModelId ?: "this model"}' cannot process this text or language option. Auto-switched to 'Multilingual v2' (Hindi & English supported)."}",
                        isModelOrLanguageMismatch = true,
                        fallbackModelId = "eleven_multilingual_v2",
                        httpStatusCode = code
                    )
                    429 -> ElevenLabsErrorResult(
                        headline = "Quota Exceeded",
                        userFriendlyMessage = "Character Quota Reached (429): You have exceeded your ElevenLabs character limit or rate limits. Please check your account usage.",
                        isQuotaExceeded = true,
                        httpStatusCode = code
                    )
                    500, 502, 503, 504 -> ElevenLabsErrorResult(
                        headline = "Server Error",
                        userFriendlyMessage = "ElevenLabs Server Error ($code): Service is temporarily unavailable. Please try again in a moment.",
                        httpStatusCode = code
                    )
                    else -> ElevenLabsErrorResult(
                        headline = "API Error ($code)",
                        userFriendlyMessage = "ElevenLabs Error ($code): ${detailMsg ?: e.message() ?: "Request failed."}",
                        httpStatusCode = code
                    )
                }
            }
            is UnknownHostException, is SocketTimeoutException, is IOException -> {
                ElevenLabsErrorResult(
                    headline = "Connection Error",
                    userFriendlyMessage = "Network Connection Failed: Unable to reach ElevenLabs servers. Please check your internet connection.",
                    isNetworkError = true
                )
            }
            else -> {
                ElevenLabsErrorResult(
                    headline = "$actionName Failed",
                    userFriendlyMessage = e.localizedMessage ?: "An unexpected error occurred during $actionName."
                )
            }
        }
    }

    private fun parseJsonDetail(jsonStr: String?): String? {
        if (jsonStr.isNullOrBlank()) return null
        return try {
            val json = JSONObject(jsonStr)
            if (json.has("detail")) {
                val detail = json.get("detail")
                if (detail is JSONObject && detail.has("message")) {
                    detail.getString("message")
                } else if (detail is String) {
                    detail
                } else {
                    jsonStr
                }
            } else if (json.has("message")) {
                json.getString("message")
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }
}
