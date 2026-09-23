package com.ritvyom.yashoraReelgenerator.data.remote

import android.util.Log

/**
 * Priority API Gateway Service.
 * Centralizes monitoring of user-provided GEMINI_API_KEY performance,
 * detecting 429 rate limits or 403 authorization failures, and
 * automatically authorizing seamless failover to the Enterprise Vertex AI backend.
 */
object PriorityApiGateway {
    private const val TAG = "PriorityApiGateway"

    /**
     * Determines whether the given HTTP status code indicates an invalid, expired, unauthorized,
     * forbidden, or quota-exceeded key that requires immediate failover to the next API key tier
     * (e.g., from User API Key -> App Internal API Key -> Vertex AI).
     */
    fun isAuthOrQuotaCritical(statusCode: Int): Boolean {
        return when (statusCode) {
            400, 401, 403, 429 -> true
            else -> false
        }
    }

    /**
     * Determines whether the given HTTP status code requires an automatic,
     * immediate failover to the Enterprise Vertex AI backend.
     *
     * @param statusCode The HTTP status code received from the REST call (e.g., 429, 403).
     * @return True if we should immediately bypass REST and switch to Vertex AI.
     */
    fun shouldSwitchToVertexAI(statusCode: Int): Boolean {
        return when (statusCode) {
            400 -> {
                Log.e(TAG, "CRITICAL API KEY ERROR (400): API key is invalid or malformed.")
                true
            }
            401 -> {
                Log.e(TAG, "CRITICAL AUTHENTICATION ERROR (401): API key is unauthorized or expired. Switching to backup key tier or Vertex AI...")
                true
            }
            403 -> {
                Log.e(TAG, "CRITICAL AUTHENTICATION ERROR (403): API key is forbidden or blocked. Switching to backup key tier or Vertex AI...")
                true
            }
            // Note: 429 is a quota limit. It allows trying next model or next key tier.
            else -> false
        }
    }

    fun isPerModelQuotaExceeded(statusCode: Int): Boolean = statusCode == 429

    /**
     * Helper to log details of successful key tier failovers.
     */
    fun logKeyTierFailover(fromTier: String, toTier: String, reason: String) {
        Log.w(TAG, "KEY TIER FAILOVER: [$fromTier] failed ($reason). Seamlessly switching to [$toTier]...")
    }

    /**
     * Helper to log details of successful Enterprise Vertex AI failovers.
     */
    fun logSuccessfulFailover(operation: String, modelName: String) {
        Log.i(TAG, "SUCCESSFUL FAILOVER: Priority API Gateway safely handled the exception for '$operation' using enterprise backend model '$modelName'.")
    }

    /**
     * Centralized validation and error-handling middleware for the newly integrated specialized media providers:
     * NHTSA vPIC, OpenFDA, and WHO GHO APIs.
     */
    fun handleMediaApiError(provider: String, statusCode: Int, errorMessage: String? = null) {
        val norm = provider.uppercase().trim()
        val errorDetail = errorMessage ?: "No detail provided"
        Log.e(TAG, "MIDDLEWARE ALERT [$norm]: API request failed with status code $statusCode. Error details: $errorDetail")
        
        when {
            norm.contains("NHTSA") || norm.contains("VPIC") -> {
                Log.w(TAG, "NHTSA vPIC API Error Handler: Status $statusCode. Triggering health monitor downgrade & checking query params.")
            }
            norm.contains("FDA") || norm.contains("OPENFDA") -> {
                Log.w(TAG, "OpenFDA API Error Handler: Status $statusCode. Tracking potential key limits or filter mismatch.")
            }
            norm.contains("WHO") -> {
                Log.w(TAG, "WHO GHO API Error Handler: Status $statusCode. Analyzing OData filtering or service availability.")
            }
        }
    }
}
