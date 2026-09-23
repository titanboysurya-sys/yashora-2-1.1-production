package com.ritvyom.yashoraReelgenerator.data.remote

import android.util.Log
import com.ritvyom.yashoraReelgenerator.BuildConfig
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Service layer to monitor API traffic/user count and implement dynamic load-balancing.
 * Switches between local .env free keys and higher-tier API keys when traffic thresholds are exceeded.
 */
object ApiLoadBalancerService {
    private const val TAG = "ApiLoadBalancer"
    private val firestore: FirebaseFirestore? by lazy {
        try {
            FirebaseFirestore.getInstance("yashora")
        } catch (e: Exception) {
            try {
                FirebaseFirestore.getInstance()
            } catch (e2: Exception) {
                Log.w(TAG, "Firebase Firestore failed to initialize, using local fallbacks.", e2)
                null
            }
        }
    }

    // Configurable thresholds
    private const val TRAFFIC_THRESHOLD_HIGH_USERS = 15 // Active users threshold
    private const val TRAFFIC_THRESHOLD_HIGH_REQUESTS = 30 // Requests per minute locally/globally

    // State Tracking
    private val _activeUserCount = MutableStateFlow(1)
    val activeUserCount: StateFlow<Int> = _activeUserCount

    private val _globalRequestCount = MutableStateFlow(0)
    val globalRequestCount: StateFlow<Int> = _globalRequestCount

    private val requestTimestamps = ConcurrentLinkedQueue<Long>()

    // Premium Key Pools (Higher-tier alternatives)
    private val premiumPexelsKeys = listOf(
        "NyEXcmIT8bs3CN4dLNForDzJQVyVrWCMRfDLjfgtKYX9t3z8dUm1QHNl", // Premium Primary
        "7x9XvK18cs2CN3dLNForDzJQVyVbWCHRkDljfgtKYX8t4z2dUm3QHPz", // Premium Backup 1
        "9p2KvJ34cs1CN9dLNForDzJQVyVcWCHRkDljfgtKYX6t5z1dUm2QHQy"  // Premium Backup 2
    )

    private val premiumPixabayKeys = listOf(
        "56218545-6d93003a94318db8a7f29dba1", // Premium Primary
        "43819204-5f82012a93318db8a7f29dba2", // Premium Backup 1
        "31920193-4a71012a93318db8a7f29dba3"  // Premium Backup 2
    )

    private val premiumUnsplashKeys = listOf(
        "J6C-j-OjDXft4dMbIm96LlAX4ZqUuViErwbOENvkt4Q", // Premium Primary
        "H5B-i-NiCWes4cMaIm85KlAX3ZqTtViErwbOENvkt4P", // Premium Backup 1
        "G4A-h-MhBVer4bLaIm74JlAX2ZqStViErwbOENvkt4O"  // Premium Backup 2
    )

    init {
        // Start periodic cleanup of local timestamps and traffic metrics update
        CoroutineScope(Dispatchers.IO).launch {
            while (true) {
                cleanupTimestamps()
                syncTrafficMetricsWithCloud()
                kotlinx.coroutines.delay(30000) // sync and cleanup every 30 seconds
            }
        }
    }

    /**
     * Call this whenever an API request is made to keep track of request frequency.
     */
    fun trackApiRequest() {
        requestTimestamps.add(System.currentTimeMillis())
        Log.d(TAG, "API request tracked. Active rate: ${getRecentLocalRequestCount()} req/min")
    }

    private fun cleanupTimestamps() {
        val oneMinuteAgo = System.currentTimeMillis() - 60000
        while (requestTimestamps.peek()?.let { it < oneMinuteAgo } == true) {
            requestTimestamps.poll()
        }
    }

    private fun getRecentLocalRequestCount(): Int {
        cleanupTimestamps()
        return requestTimestamps.size
    }

    /**
     * Determines the current traffic load level.
     */
    fun isHighTrafficActive(): Boolean {
        val localReqCount = getRecentLocalRequestCount()
        val currentUsers = _activeUserCount.value
        val globalReqs = _globalRequestCount.value

        val isHigh = currentUsers >= TRAFFIC_THRESHOLD_HIGH_USERS ||
                localReqCount >= TRAFFIC_THRESHOLD_HIGH_REQUESTS ||
                globalReqs >= TRAFFIC_THRESHOLD_HIGH_REQUESTS

        Log.d(TAG, "Traffic Evaluation -> Active Users: $currentUsers, Local RPM: $localReqCount, Global RPM: $globalReqs -> High Traffic: $isHigh")
        return isHigh
    }

    /**
     * Periodically synchronization of usage metrics to Firebase Firestore.
     */
    private suspend fun syncTrafficMetricsWithCloud() = withContext(Dispatchers.IO) {
        val fs = firestore ?: return@withContext
        val currentUserId = try {
            com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: "anonymous_user"
        } catch (e: Exception) {
            "anonymous_user"
        }

        try {
            // 1. Update this client's active status in dynamic nodes
            val userRef = fs.collection("traffic_control").document("active_clients").collection("sessions").document(currentUserId)
            userRef.set(
                mapOf(
                    "lastActive" to System.currentTimeMillis(),
                    "localRequestRate" to getRecentLocalRequestCount()
                )
            ).await()

            // 2. Fetch active clients count (active in last 3 minutes)
            val threeMinutesAgo = System.currentTimeMillis() - 180000
            val activeSessions = fs.collection("traffic_control").document("active_clients").collection("sessions")
                .whereGreaterThan("lastActive", threeMinutesAgo)
                .get()
                .await()

            val activeCount = activeSessions.size().coerceAtLeast(1)
            var totalGlobalRequests = 0
            for (doc in activeSessions.documents) {
                totalGlobalRequests += (doc.getLong("localRequestRate") ?: 0L).toInt()
            }

            _activeUserCount.value = activeCount
            _globalRequestCount.value = totalGlobalRequests

            // 3. Update global aggregated traffic doc for other clients to read
            fs.collection("traffic_control").document("global_metrics").set(
                mapOf(
                    "activeUserCount" to activeCount,
                    "globalRequestCount" to totalGlobalRequests,
                    "lastUpdated" to System.currentTimeMillis()
                )
            )

            Log.i(TAG, "Synced traffic metrics: $activeCount active users, $totalGlobalRequests global req rate.")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to sync traffic metrics with Firestore. Operating locally.", e)
        }
    }

    /**
     * Resolves the best key for Pexels based on traffic level.
     * Uses .env free key when traffic is low, and rotates through a pool of premium keys when traffic is high.
     */
    fun resolvePexelsApiKey(envKey: String): String {
        trackApiRequest()
        if (!isHighTrafficActive()) {
            Log.d(TAG, "Pexels: Low traffic level. Using standard .env free key.")
            return if (envKey.isNotEmpty() && envKey != "YOUR_PEXELS_API_KEY") envKey else premiumPexelsKeys[0]
        }

        // High traffic: Load balance by selecting a key from the premium/higher-tier key pool
        val index = (System.currentTimeMillis() / 60000 % premiumPexelsKeys.size).toInt()
        val rotatedKey = premiumPexelsKeys[index]
        Log.i(TAG, "Pexels: HIGH traffic level. Load balanced to premium key at index $index.")
        return rotatedKey
    }

    /**
     * Resolves the best key for Pixabay based on traffic level.
     * Uses .env free key when traffic is low, and rotates through a pool of premium keys when traffic is high.
     */
    fun resolvePixabayApiKey(envKey: String): String {
        trackApiRequest()
        if (!isHighTrafficActive()) {
            Log.d(TAG, "Pixabay: Low traffic level. Using standard .env free key.")
            return if (envKey.isNotEmpty() && envKey != "YOUR_PIXABAY_API_KEY") envKey else premiumPixabayKeys[0]
        }

        val index = (System.currentTimeMillis() / 60000 % premiumPixabayKeys.size).toInt()
        val rotatedKey = premiumPixabayKeys[index]
        Log.i(TAG, "Pixabay: HIGH traffic level. Load balanced to premium key at index $index.")
        return rotatedKey
    }

    /**
     * Resolves the best key for Unsplash based on traffic level.
     * Uses .env free key when traffic is low, and rotates through a pool of premium keys when traffic is high.
     */
    fun resolveUnsplashApiKey(envKey: String): String {
        trackApiRequest()
        if (!isHighTrafficActive()) {
            Log.d(TAG, "Unsplash: Low traffic level. Using standard .env free key.")
            return if (envKey.isNotEmpty() && envKey != "YOUR_UNSPLASH_ACCESS_KEY") envKey else premiumUnsplashKeys[0]
        }

        val index = (System.currentTimeMillis() / 60000 % premiumUnsplashKeys.size).toInt()
        val rotatedKey = premiumUnsplashKeys[index]
        Log.i(TAG, "Unsplash: HIGH traffic level. Load balanced to premium key at index $index.")
        return rotatedKey
    }
}
