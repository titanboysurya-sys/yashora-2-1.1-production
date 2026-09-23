package com.ritvyom.yashoraReelgenerator.data.repository

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class AdNetwork(val displayName: String, val averageeCPM: Double) {
    ADMOB("Google AdMob", 48.33),
    APPLOVIN("AppLovin MAX", 42.15),
    UNITY("Unity Ads", 32.50),
    META("Meta Audience Network", 22.80)
}

object AdMediationService {
    private const val TAG = "AdMediationService"

    private val _currentActiveNetwork = MutableStateFlow<AdNetwork>(AdNetwork.ADMOB)
    val currentActiveNetwork: StateFlow<AdNetwork> = _currentActiveNetwork.asStateFlow()

    private val _mediationTrace = MutableStateFlow<List<String>>(emptyList())
    val mediationTrace: StateFlow<List<String>> = _mediationTrace.asStateFlow()

    private val networkStatuses = mutableMapOf<AdNetwork, Boolean>()

    init {
        // Default statuses for networks
        AdNetwork.values().forEach { network ->
            networkStatuses[network] = true
        }
        addTraceLog("AdMediationService initialized with Waterfall hierarchy: AdMob -> AppLovin MAX -> Unity Ads -> Meta Audience Network")
    }

    private fun addTraceLog(message: String) {
        Log.d(TAG, message)
        val current = _mediationTrace.value.toMutableList()
        current.add("[${System.currentTimeMillis()}] $message")
        _mediationTrace.value = current
    }

    fun setNetworkAvailable(network: AdNetwork, available: Boolean) {
        networkStatuses[network] = available
        addTraceLog("Network status updated: ${network.displayName} is ${if (available) "AVAILABLE" else "UNAVAILABLE"}")
    }

    fun getWaterfall(): List<AdNetwork> {
        return listOf(AdNetwork.ADMOB, AdNetwork.APPLOVIN, AdNetwork.UNITY, AdNetwork.META)
    }

    /**
     * Executes the waterfall mediation search for Interstitial and Rewarded slots.
     * Prioritizes: AdMob -> AppLovin MAX -> Unity Ads -> Meta Audience Network.
     * Returns the selected network that succeeded in loading.
     */
    fun selectNetworkForAd(context: Context, adType: String): AdNetwork {
        addTraceLog("=== Executing $adType Waterfall Mediation Strategy ===")
        val waterfall = getWaterfall()
        
        for (network in waterfall) {
            val isAvailable = networkStatuses[network] ?: false
            if (isAvailable) {
                _currentActiveNetwork.value = network
                addTraceLog("Waterfall Match Success: Selected ${network.displayName} with average eCPM $${network.averageeCPM}")
                return network
            } else {
                addTraceLog("Waterfall Skip: ${network.displayName} returned No-Fill or Offline. Moving to next fallback network...")
            }
        }

        // Default to Meta Audience Network as final fallback if all other networks fail or are empty
        addTraceLog("Waterfall Fallback: All networks returned No-Fill. Routing traffic to Meta Audience Network configured final fallback")
        _currentActiveNetwork.value = AdNetwork.META
        return AdNetwork.META
    }

    /**
     * Logs ad request and display metrics
     */
    fun logAdDisplay(network: AdNetwork, adType: String) {
        addTraceLog("Impression Logged: $adType served via ${network.displayName} (eCPM: $${network.averageeCPM})")
    }
}
