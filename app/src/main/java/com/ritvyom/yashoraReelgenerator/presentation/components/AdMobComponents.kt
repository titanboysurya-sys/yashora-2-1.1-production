package com.ritvyom.yashoraReelgenerator.presentation.components

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import android.content.Context
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.rewarded.RewardedAd

/**
 * AdMobComponents - All ads completely disabled.
 * Provides no-op composables and immediate award callbacks so features work seamlessly with 0 ads.
 */

@Composable
fun AdMobBanner(modifier: Modifier = Modifier) {
    // Disabled: no view rendered, no network ad requested
}

@Composable
fun MockRewardedAdDialog(
    show: Boolean,
    onRewardEarned: () -> Unit,
    onDismiss: () -> Unit
) {
    LaunchedEffect(show) {
        if (show) {
            onRewardEarned()
            onDismiss()
        }
    }
}

@Composable
fun MockInterstitialAdDialog(
    show: Boolean,
    onDismiss: () -> Unit
) {
    LaunchedEffect(show) {
        if (show) {
            onDismiss()
        }
    }
}

// Global Ad Manager - Ads Disabled
object AdManager {
    fun loadAd(context: Context) {}
    fun isAdReady(): Boolean = false
    fun getAd(): InterstitialAd? = null
    fun clearAd() {}
}

// Global Rewarded Ad Manager - Ads Disabled
object RewardedAdManager {
    fun loadAd(context: Context) {}
    fun isAdReady(): Boolean = false
    fun getAd(): RewardedAd? = null
    fun clearAd() {}
}
