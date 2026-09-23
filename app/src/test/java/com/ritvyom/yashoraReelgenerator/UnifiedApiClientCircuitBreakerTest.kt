package com.ritvyom.yashoraReelgenerator

import com.ritvyom.yashoraReelgenerator.data.remote.ApiProvider
import com.ritvyom.yashoraReelgenerator.data.remote.CircuitState
import com.ritvyom.yashoraReelgenerator.data.remote.UnifiedApiClient
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException
import java.net.SocketTimeoutException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class UnifiedApiClientCircuitBreakerTest {

    @Before
    fun setup() {
        UnifiedApiClient.resetCircuitBreakers()
    }

    @Test
    fun testInitialCircuitStateIsClosed() {
        val metrics = UnifiedApiClient.getMetrics(ApiProvider.GEMINI)
        assertEquals(CircuitState.CLOSED, metrics.state)
        assertEquals(0, metrics.consecutiveFailures)
    }

    @Test
    fun testPrimaryCallSuccessKeepsCircuitClosed() = runBlocking {
        var primaryAttempts = 0
        var secondaryAttempts = 0

        val result = UnifiedApiClient.executeWithCircuitBreaker(
            provider = ApiProvider.GEMINI,
            operationName = "Test Success",
            primaryCall = {
                primaryAttempts++
                "Primary Result"
            },
            secondaryCall = {
                secondaryAttempts++
                "Secondary Result"
            }
        )

        assertEquals("Primary Result", result)
        assertEquals(1, primaryAttempts)
        assertEquals(0, secondaryAttempts)

        val metrics = UnifiedApiClient.getMetrics(ApiProvider.GEMINI)
        assertEquals(CircuitState.CLOSED, metrics.state)
        assertEquals(0, metrics.consecutiveFailures)
    }

    @Test
    fun testAutomaticRetryOnFirstFailureRecoversSuccessfully() = runBlocking {
        var primaryAttempts = 0
        var secondaryAttempts = 0

        val result = UnifiedApiClient.executeWithCircuitBreaker(
            provider = ApiProvider.GEMINI,
            operationName = "Test 1x Retry Recovery",
            primaryCall = {
                primaryAttempts++
                if (primaryAttempts == 1) {
                    throw SocketTimeoutException("Simulated primary timeout")
                }
                "Recovered on Retry"
            },
            secondaryCall = {
                secondaryAttempts++
                "Secondary Result"
            }
        )

        assertEquals("Recovered on Retry", result)
        assertEquals(2, primaryAttempts) // 1 initial + 1 automatic retry
        assertEquals(0, secondaryAttempts)

        val metrics = UnifiedApiClient.getMetrics(ApiProvider.GEMINI)
        assertEquals(CircuitState.CLOSED, metrics.state)
    }

    @Test
    fun testFailureAfterRetryTripsCircuitToOpenAndSwitchesToSecondary() = runBlocking {
        var primaryAttempts = 0
        var secondaryAttempts = 0

        val result = UnifiedApiClient.executeWithCircuitBreaker(
            provider = ApiProvider.GEMINI,
            operationName = "Test Trip to OPEN",
            primaryCall = {
                primaryAttempts++
                throw IOException("429 Resource Exhausted / Rate limit exceeded")
            },
            secondaryCall = {
                secondaryAttempts++
                "Secondary Vertex AI Result"
            }
        )

        // Verifies failover returns secondary result safely
        assertEquals("Secondary Vertex AI Result", result)
        // 1 initial attempt + 1 automatic retry = 2 attempts total before trip
        assertEquals(2, primaryAttempts)
        assertEquals(1, secondaryAttempts)

        // Verifies circuit breaker is now OPEN
        val metrics = UnifiedApiClient.getMetrics(ApiProvider.GEMINI)
        assertEquals(CircuitState.OPEN, metrics.state)
        assertTrue(metrics.lastTripReason?.contains("429") == true || metrics.lastTripReason?.contains("Rate limit") == true || metrics.lastTripReason?.contains("Retry failed") == true)
    }

    @Test
    fun testOpenCircuitBypassesPrimaryAndRoutesDirectlyToSecondary() = runBlocking {
        // 1. First trigger trip to OPEN
        UnifiedApiClient.executeWithCircuitBreaker(
            provider = ApiProvider.GEMINI,
            operationName = "Force Trip",
            primaryCall = {
                throw IOException("401 Unauthorized API key")
            },
            secondaryCall = {
                "Vertex AI Fallback 1"
            }
        )

        val stateAfterTrip = UnifiedApiClient.getMetrics(ApiProvider.GEMINI)
        assertEquals(CircuitState.OPEN, stateAfterTrip.state)

        // 2. Next call should bypass primary completely without waiting or retrying
        var primaryCalled = false
        var secondaryCalled = false

        val secondResult = UnifiedApiClient.executeWithCircuitBreaker(
            provider = ApiProvider.GEMINI,
            operationName = "Bypassed Call",
            primaryCall = {
                primaryCalled = true
                "Should not be called"
            },
            secondaryCall = {
                secondaryCalled = true
                "Vertex AI Fallback 2"
            }
        )

        assertEquals("Vertex AI Fallback 2", secondResult)
        assertTrue("Primary should be bypassed when circuit is OPEN", !primaryCalled)
        assertTrue("Secondary must be invoked immediately", secondaryCalled)
    }

    @Test
    fun testElevenLabsCircuitBreakerTripsAndFailsOver() = runBlocking {
        var primaryAttempts = 0
        var secondaryAttempts = 0

        val result = UnifiedApiClient.executeWithCircuitBreaker(
            provider = ApiProvider.ELEVEN_LABS,
            operationName = "ElevenLabs Speech Gen",
            primaryCall = {
                primaryAttempts++
                throw IOException("401 Invalid ElevenLabs API Key")
            },
            secondaryCall = {
                secondaryAttempts++
                "Local High-Fidelity TTS Synthesis"
            }
        )

        assertEquals("Local High-Fidelity TTS Synthesis", result)
        assertEquals(2, primaryAttempts) // 1 try + 1 retry
        assertEquals(1, secondaryAttempts)

        val metrics = UnifiedApiClient.getMetrics(ApiProvider.ELEVEN_LABS)
        assertEquals(CircuitState.OPEN, metrics.state)
    }
}
