package com.ritvyom.yashoraReelgenerator.presentation.utils

import android.app.Activity
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * AdManager - Master switch turned OFF.
 * All banner, interstitial, and rewarded ads are disabled.
 */
object AdManager {
    const val BANNER_AD_UNIT_ID = ""
    const val INTERSTITIAL_AD_UNIT_ID = ""
    const val REWARDED_AD_UNIT_ID = ""

    var adsEnabled = false

    fun init(context: Context) {
        // Disabled
    }

    fun isConsentGiven(context: Context): Boolean = true
    fun isConsentSet(context: Context): Boolean = true
    fun setConsent(context: Context, given: Boolean) {}
    fun shouldShowConsentDialog(context: Context): Boolean = false
    fun updateConsent(context: Context, consented: Boolean) {}
    fun loadInterstitial(context: Context) {}
    fun loadRewarded(context: Context) {}

    fun showInterstitial(activity: Activity, onDismiss: () -> Unit) {
        onDismiss()
    }

    fun showRewarded(activity: Activity, onRewardEarned: () -> Unit, onDismiss: () -> Unit) {
        onRewardEarned()
        onDismiss()
    }

    @Composable
    fun BannerAd(modifier: Modifier = Modifier) {
        // Ads are completely disabled - render nothing
    }
}
