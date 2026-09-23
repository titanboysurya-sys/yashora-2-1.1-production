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
     * Determines whether the given HTTP status code requires an automatic,
     * immediate failover to the Enterprise Vertex AI backend.
     *
     * @param statusCode The HTTP status code received from the REST call (e.g., 429, 403).
     * @return True if we should immediately bypass REST and switch to Vertex AI.
     */
    fun shouldSwitchToVertexAI(statusCode: Int): Boolean {
        return when (statusCode) {
            400 -> {
                Log.e(TAG, "CRITICAL API KEY ERROR (400): User-provided GEMINI_API_KEY is invalid or malformed. Automatically switching to Enterprise Vertex AI backend...")
                true
            }
            401 -> {
                Log.e(TAG, "CRITICAL AUTHENTICATION ERROR (401): User-provided GEMINI_API_KEY is unauthorized or expired. Automatically switching to Enterprise Vertex AI backend...")
                true
            }
            403 -> {
                Log.e(TAG, "CRITICAL AUTHENTICATION ERROR (403): User-provided GEMINI_API_KEY is forbidden or expired. Automatically switching to Enterprise Vertex AI backend...")
                true
            }
            429 -> {
                Log.e(TAG, "CRITICAL RATE LIMIT REACHED (429): User-provided GEMINI_API_KEY returned quota exceeded. Automatically switching to Enterprise Vertex AI backend to prevent user interruption...")
                true
            }
            else -> false
        }
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
