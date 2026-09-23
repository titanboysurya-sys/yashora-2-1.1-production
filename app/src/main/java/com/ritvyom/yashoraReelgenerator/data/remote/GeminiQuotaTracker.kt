package com.ritvyom.yashoraReelgenerator.data.remote

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import com.ritvyom.yashoraReelgenerator.YashoraApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Data model representing the real-time Gemini API Quota status.
 */
data class GeminiQuotaState(
    val dailyLimit: Int = 1500,               // Free Tier default daily request limit
    val minuteLimit: Int = 15,                // Free Tier default RPM limit
    val todayRequests: Int = 0,               // Requests consumed today
    val minuteRequests: Int = 0,              // Requests consumed in the active minute window
    val remainingDailyQuota: Int = 1500,      // Requests remaining today
    val remainingMinuteQuota: Int = 15,       // Requests remaining this minute
    val dailyUsedPercentage: Float = 0f,      // 0.0 to 1.0 fraction
    val minuteUsedPercentage: Float = 0f,     // 0.0 to 1.0 fraction
    val activeTierLabel: String = "Free Tier (Gemini Developer API)",
    val activeModel: String = "gemini-2.5-flash",
    val isRateLimited: Boolean = false,
    val rateLimitRemainingSeconds: Int = 0,
    val dailyResetCountdown: String = "--:--",
    val lastRequestTimestamp: Long = 0L,
    val lastHttpStatus: Int? = null,
    val statusSummary: String = "Healthy (Free Tier Active)"
)

/**
 * Real-time tracker for Google Gemini API Free-Tier usage & rate-limits.
 *
 * Tracks:
 * - Daily Requests (1,500 RPD on Google AI Free Tier)
 * - Minute Requests (15 RPM on Google AI Free Tier)
 * - Automatic reset at midnight UTC
 * - 429 Rate-limit cooldown detection & countdown
 */
object GeminiQuotaTracker {
    private const val TAG = "GeminiQuotaTracker"
    private const val PREFS_NAME = "gemini_quota_prefs"
    private const val KEY_LAST_DATE = "last_quota_date"
    private const val KEY_TODAY_COUNT = "today_request_count"
    private const val KEY_COOLDOWN_UNTIL = "quota_cooldown_until"

    const val DEFAULT_FREE_RPD = 1500
    const val DEFAULT_FREE_RPM = 15

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val minuteRequestTimestamps = ConcurrentLinkedQueue<Long>()

    private val _quotaState = MutableStateFlow(GeminiQuotaState())
    val quotaState: StateFlow<GeminiQuotaState> = _quotaState.asStateFlow()

    private var prefs: SharedPreferences? = null

    init {
        // Start background ticker for countdown updates & rolling minute window cleanup
        scope.launch {
            while (true) {
                try {
                    refreshState()
                } catch (e: Exception) {
                    Log.w(TAG, "Error refreshing quota state: ${e.message}")
                }
                delay(1000)
            }
        }
    }

    private fun getPrefs(): SharedPreferences? {
        if (prefs == null) {
            val ctx = YashoraApplication.getInstance()
            if (ctx != null) {
                prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            }
        }
        return prefs
    }

    private fun getTodayDateString(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        return sdf.format(Date())
    }

    private fun getMidnightUtcRemaining(): String {
        val now = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        val midnight = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            add(Calendar.DAY_OF_YEAR, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val diffMs = midnight.timeInMillis - now.timeInMillis
        val hours = (diffMs / (1000 * 60 * 60)).coerceAtLeast(0)
        val minutes = ((diffMs / (1000 * 60)) % 60).coerceAtLeast(0)
        val seconds = ((diffMs / 1000) % 60).coerceAtLeast(0)
        return String.format(Locale.US, "%02dh %02dm %02ds", hours, minutes, seconds)
    }

    @Synchronized
    fun recordRequest(statusCode: Int? = null, model: String = "gemini-2.5-flash", isError: Boolean = false) {
        val p = getPrefs() ?: return
        val today = getTodayDateString()
        val lastDate = p.getString(KEY_LAST_DATE, "")

        var count = if (lastDate == today) p.getInt(KEY_TODAY_COUNT, 0) else 0
        count++

        p.edit()
            .putString(KEY_LAST_DATE, today)
            .putInt(KEY_TODAY_COUNT, count)
            .apply()

        val now = System.currentTimeMillis()
        minuteRequestTimestamps.add(now)

        if (statusCode == 429) {
            // Set 60-second cooldown
            val cooldownUntil = now + 60_000L
            p.edit().putLong(KEY_COOLDOWN_UNTIL, cooldownUntil).apply()
        }

        refreshState(explicitModel = model, explicitStatus = statusCode)
    }

    fun recordRateLimited(retryAfterSeconds: Int = 60) {
        val p = getPrefs() ?: return
        val cooldownUntil = System.currentTimeMillis() + (retryAfterSeconds * 1000L)
        p.edit().putLong(KEY_COOLDOWN_UNTIL, cooldownUntil).apply()
        refreshState(explicitStatus = 429)
    }

    fun refreshState(explicitModel: String? = null, explicitStatus: Int? = null) {
        val p = getPrefs()
        val today = getTodayDateString()
        val lastDate = p?.getString(KEY_LAST_DATE, "") ?: today
        val todayCount = if (lastDate == today) p?.getInt(KEY_TODAY_COUNT, 0) ?: 0 else 0

        val now = System.currentTimeMillis()
        val cutoff = now - 60_000L
        while (minuteRequestTimestamps.peek()?.let { it < cutoff } == true) {
            minuteRequestTimestamps.poll()
        }
        val minuteCount = minuteRequestTimestamps.size

        val cooldownUntil = p?.getLong(KEY_COOLDOWN_UNTIL, 0L) ?: 0L
        val isCoolingDown = cooldownUntil > now
        val remainingCooldownSec = if (isCoolingDown) ((cooldownUntil - now) / 1000).toInt() + 1 else 0

        val remainingDaily = maxOf(0, DEFAULT_FREE_RPD - todayCount)
        val remainingMinute = maxOf(0, DEFAULT_FREE_RPM - minuteCount)

        val dailyPct = (todayCount.toFloat() / DEFAULT_FREE_RPD.toFloat()).coerceIn(0f, 1f)
        val minutePct = (minuteCount.toFloat() / DEFAULT_FREE_RPM.toFloat()).coerceIn(0f, 1f)

        val summary = when {
            isCoolingDown -> "Rate Limited (Wait ${remainingCooldownSec}s)"
            todayCount >= DEFAULT_FREE_RPD -> "Daily Limit Reached (Resets UTC Midnight)"
            minuteCount >= DEFAULT_FREE_RPM -> "Minute RPM Reached (Wait for window)"
            dailyPct >= 0.85f -> "Approaching Daily Free Quota"
            else -> "Healthy (Free Tier Active)"
        }

        _quotaState.value = _quotaState.value.copy(
            dailyLimit = DEFAULT_FREE_RPD,
            minuteLimit = DEFAULT_FREE_RPM,
            todayRequests = todayCount,
            minuteRequests = minuteCount,
            remainingDailyQuota = remainingDaily,
            remainingMinuteQuota = remainingMinute,
            dailyUsedPercentage = dailyPct,
            minuteUsedPercentage = minutePct,
            activeModel = explicitModel ?: _quotaState.value.activeModel,
            isRateLimited = isCoolingDown,
            rateLimitRemainingSeconds = remainingCooldownSec,
            dailyResetCountdown = getMidnightUtcRemaining(),
            lastRequestTimestamp = now,
            lastHttpStatus = explicitStatus ?: _quotaState.value.lastHttpStatus,
            statusSummary = summary
        )
    }
}

/**
 * Helper to dynamically extract the Android App Signing SHA-1 Certificate
 * and package name to authenticate with Google Cloud API keys having Android restrictions.
 */
object SigningCertHelper {
    private var cachedSha1: String? = null

    fun getCertSha1(context: Context?): String {
        if (!cachedSha1.isNullOrBlank()) return cachedSha1!!
        val ctx = context ?: YashoraApplication.getInstance()
        if (ctx == null) {
            // Default keystore fingerprint from debug.keystore
            return "1928148269B287D5AC9D1C7E5CFC0A293AE6B316"
        }

        try {
            val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                ctx.packageManager.getPackageInfo(
                    ctx.packageName,
                    PackageManager.GET_SIGNING_CERTIFICATES
                )
            } else {
                @Suppress("DEPRECATION")
                ctx.packageManager.getPackageInfo(
                    ctx.packageName,
                    PackageManager.GET_SIGNATURES
                )
            }

            val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.signingInfo?.apkContentsSigners
            } else {
                @Suppress("DEPRECATION")
                packageInfo.signatures
            }

            val cert = signatures?.firstOrNull()?.toByteArray()
            if (cert != null) {
                val md = MessageDigest.getInstance("SHA-1")
                val digest = md.digest(cert)
                val hex = digest.joinToString("") { "%02X".format(it) }
                cachedSha1 = hex
                return hex
            }
        } catch (e: Exception) {
            Log.w("SigningCertHelper", "Could not query signing cert from packageManager: ${e.message}")
        }

        return "1928148269B287D5AC9D1C7E5CFC0A293AE6B316"
    }
}
