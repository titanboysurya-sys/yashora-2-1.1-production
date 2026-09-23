package com.ritvyom.yashoraReelgenerator.data.repository

import android.util.Log
import java.util.concurrent.ConcurrentHashMap

/**
 * Robust middleware to track the health of media providers (Pexels, Pixabay, Unsplash, Pollinations, etc.).
 * If a provider fails, is rate-limited, or times out repeatedly, it enters a cooldown phase.
 * During cooldown, it is deprioritized to prevent long rendering/compilation pipeline delays,
 * and the system automatically switches to the next available healthy provider.
 */
object MediaProviderHealthMonitor {
    private const val TAG = "MediaHealthMonitor"
    private val failureCounts = ConcurrentHashMap<String, Int>()
    private val cooldownEndTimes = ConcurrentHashMap<String, Long>()
    
    private const val MAX_FAILURES_BEFORE_COOLDOWN = 2
    private const val COOLDOWN_DURATION_MS = 3 * 60 * 1000L // 3 minutes

    /**
     * Call this when a media provider successfully returns a valid media URL.
     */
    fun recordSuccess(provider: String) {
        val normalized = provider.lowercase().trim()
        val failures = failureCounts[normalized] ?: 0
        if (failures > 0) {
            Log.i(TAG, "Provider '$normalized' recovered successfully. Resetting failure count.")
        }
        failureCounts[normalized] = 0
        cooldownEndTimes.remove(normalized)
    }

    /**
     * Call this when a media provider request fails (e.g. timeout, empty response, or exception).
     */
    fun recordFailure(provider: String) {
        val normalized = provider.lowercase().trim()
        val count = (failureCounts[normalized] ?: 0) + 1
        failureCounts[normalized] = count
        Log.w(TAG, "Provider '$normalized' request failed. Consecutive failure count: $count")
        
        if (count >= MAX_FAILURES_BEFORE_COOLDOWN) {
            val cooldownUntil = System.currentTimeMillis() + COOLDOWN_DURATION_MS
            cooldownEndTimes[normalized] = cooldownUntil
            Log.e(TAG, "Provider '$normalized' is now degraded. Cool down active until: $cooldownUntil ms")
        }
    }

    /**
     * Checks if a specific provider is currently healthy and not in cooldown.
     */
    fun isProviderHealthy(provider: String): Boolean {
        val normalized = provider.lowercase().trim()
        val cooldownEnd = cooldownEndTimes[normalized] ?: return true
        if (System.currentTimeMillis() > cooldownEnd) {
            // Cooldown expired, restore health and reset counters
            cooldownEndTimes.remove(normalized)
            failureCounts[normalized] = 0
            Log.i(TAG, "Provider '$normalized' cooldown expired. Restoring to healthy pool.")
            return true
        }
        return false
    }

    /**
     * Prioritizes and filters engines list based on health status.
     * Moves unhealthy/degraded engines to the end of the search sequence,
     * so healthy ones are tried first, avoiding unnecessary delays.
     */
    fun filterAndSortEngines(engines: List<String>): List<String> {
        val healthy = mutableListOf<String>()
        val degraded = mutableListOf<String>()
        for (engine in engines) {
            if (isProviderHealthy(engine)) {
                healthy.add(engine)
            } else {
                degraded.add(engine)
            }
        }
        val sorted = healthy + degraded
        if (degraded.isNotEmpty()) {
            Log.d(TAG, "Filtered engines. Healthy: $healthy, Degraded (moved to backup): $degraded")
        }
        return sorted
    }
}
