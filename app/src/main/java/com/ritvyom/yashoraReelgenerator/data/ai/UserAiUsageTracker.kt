package com.ritvyom.yashoraReelgenerator.data.ai

import android.content.Context
import android.content.SharedPreferences
import com.ritvyom.yashoraReelgenerator.YashoraApplication
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Real-time usage and quota metrics for a single user-configured AI Provider key.
 */
data class UserProviderUsageState(
    val providerId: ProviderId,
    val totalRequests: Int = 0,
    val todayRequests: Int = 0,
    val successfulRequests: Int = 0,
    val failedRequests: Int = 0,
    val estimatedTokensUsed: Long = 0L,
    val lastUsedTimestamp: Long = 0L,
    val dailyLimit: Int = 0,            // 0 means unmetered / pay-as-you-go
    val minuteLimit: Int = 0,
    val tierLabel: String = "User Custom API Key (BYOK)",
    val lastModelUsed: String = "",
    val lastError: String? = null
) {
    val remainingDailyQuota: Int? = if (dailyLimit > 0) (dailyLimit - todayRequests).coerceAtLeast(0) else null
    val dailyProgressFraction: Float = if (dailyLimit > 0) (todayRequests.toFloat() / dailyLimit.toFloat()).coerceIn(0f, 1f) else 0f
}

/**
 * Tracks strictly the user's OWN API key usage per provider (Gemini, Groq, OpenAI, xAI Grok, DeepSeek).
 * Does NOT track or show global app/system/vertex keys to the user.
 * Exactly mirrors how ElevenLabs shows remaining quota and character usage.
 */
object UserAiUsageTracker {
    private const val PREFS_NAME = "user_byok_ai_usage_prefs"
    private const val KEY_PREFIX_TOTAL = "total_req_"
    private const val KEY_PREFIX_TODAY = "today_req_"
    private const val KEY_PREFIX_SUCCESS = "success_req_"
    private const val KEY_PREFIX_FAILED = "failed_req_"
    private const val KEY_PREFIX_TOKENS = "tokens_"
    private const val KEY_PREFIX_LAST_TIME = "last_time_"
    private const val KEY_PREFIX_LAST_DATE = "last_date_"
    private const val KEY_PREFIX_LAST_MODEL = "last_model_"
    private const val KEY_PREFIX_LAST_ERROR = "last_error_"

    private val _usageMap = MutableStateFlow<Map<ProviderId, UserProviderUsageState>>(emptyMap())
    val usageMap: StateFlow<Map<ProviderId, UserProviderUsageState>> = _usageMap.asStateFlow()

    private var prefs: SharedPreferences? = null

    init {
        refreshAll()
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

    /**
     * Standard free/developer tier limits per provider if user is on free tier.
     * Gemini: 1500 RPD, 15 RPM
     * Groq: 14400 RPD, 30 RPM
     * OpenAI / DeepSeek / xAI: Pay-as-you-go (0 indicates unmetered requests, tracked by usage & tokens)
     */
    fun getDefaultDailyLimit(providerId: ProviderId): Int {
        return when (providerId) {
            ProviderId.GEMINI -> 1500
            ProviderId.GROQ -> 14400
            ProviderId.OPENAI -> 0      // Pay-as-you-go balance / tokens
            ProviderId.XAI -> 0         // Prepaid balance
            ProviderId.DEEPSEEK -> 0    // Prepaid balance
            ProviderId.CLAUDE -> 0
        }
    }

    fun getDefaultMinuteLimit(providerId: ProviderId): Int {
        return when (providerId) {
            ProviderId.GEMINI -> 15
            ProviderId.GROQ -> 30
            ProviderId.OPENAI -> 60
            ProviderId.XAI -> 60
            ProviderId.DEEPSEEK -> 60
            ProviderId.CLAUDE -> 60
        }
    }

    /**
     * Records a request made strictly with the USER'S custom API key.
     */
    @Synchronized
    fun recordUserRequest(
        providerId: ProviderId,
        isSuccess: Boolean,
        model: String = "",
        estimatedTokens: Int = 0,
        errorMessage: String? = null
    ) {
        val p = getPrefs() ?: return
        val today = getTodayDateString()
        val lastDate = p.getString(KEY_PREFIX_LAST_DATE + providerId.name, "")

        val todayReq = if (lastDate == today) p.getInt(KEY_PREFIX_TODAY + providerId.name, 0) + 1 else 1
        val totalReq = p.getInt(KEY_PREFIX_TOTAL + providerId.name, 0) + 1
        val successReq = p.getInt(KEY_PREFIX_SUCCESS + providerId.name, 0) + (if (isSuccess) 1 else 0)
        val failedReq = p.getInt(KEY_PREFIX_FAILED + providerId.name, 0) + (if (!isSuccess) 1 else 0)
        val tokens = p.getLong(KEY_PREFIX_TOKENS + providerId.name, 0L) + estimatedTokens.coerceAtLeast(0)
        val now = System.currentTimeMillis()

        p.edit()
            .putString(KEY_PREFIX_LAST_DATE + providerId.name, today)
            .putInt(KEY_PREFIX_TODAY + providerId.name, todayReq)
            .putInt(KEY_PREFIX_TOTAL + providerId.name, totalReq)
            .putInt(KEY_PREFIX_SUCCESS + providerId.name, successReq)
            .putInt(KEY_PREFIX_FAILED + providerId.name, failedReq)
            .putLong(KEY_PREFIX_TOKENS + providerId.name, tokens)
            .putLong(KEY_PREFIX_LAST_TIME + providerId.name, now)
            .putString(KEY_PREFIX_LAST_MODEL + providerId.name, model)
            .putString(KEY_PREFIX_LAST_ERROR + providerId.name, if (!isSuccess) (errorMessage ?: "Failed") else null)
            .apply()

        refreshAll()
    }

    /**
     * Resets counters for a provider if user clears or changes their key.
     */
    @Synchronized
    fun resetUserUsage(providerId: ProviderId) {
        val p = getPrefs() ?: return
        p.edit()
            .remove(KEY_PREFIX_LAST_DATE + providerId.name)
            .remove(KEY_PREFIX_TODAY + providerId.name)
            .remove(KEY_PREFIX_TOTAL + providerId.name)
            .remove(KEY_PREFIX_SUCCESS + providerId.name)
            .remove(KEY_PREFIX_FAILED + providerId.name)
            .remove(KEY_PREFIX_TOKENS + providerId.name)
            .remove(KEY_PREFIX_LAST_TIME + providerId.name)
            .remove(KEY_PREFIX_LAST_MODEL + providerId.name)
            .remove(KEY_PREFIX_LAST_ERROR + providerId.name)
            .apply()

        refreshAll()
    }

    @Synchronized
    fun refreshAll() {
        val p = getPrefs()
        val today = getTodayDateString()
        val newMap = mutableMapOf<ProviderId, UserProviderUsageState>()

        for (provider in ProviderId.values()) {
            val lastDate = p?.getString(KEY_PREFIX_LAST_DATE + provider.name, "") ?: ""
            val todayReq = if (lastDate == today) p?.getInt(KEY_PREFIX_TODAY + provider.name, 0) ?: 0 else 0
            val totalReq = p?.getInt(KEY_PREFIX_TOTAL + provider.name, 0) ?: 0
            val successReq = p?.getInt(KEY_PREFIX_SUCCESS + provider.name, 0) ?: 0
            val failedReq = p?.getInt(KEY_PREFIX_FAILED + provider.name, 0) ?: 0
            val tokens = p?.getLong(KEY_PREFIX_TOKENS + provider.name, 0L) ?: 0L
            val lastTime = p?.getLong(KEY_PREFIX_LAST_TIME + provider.name, 0L) ?: 0L
            val lastModel = p?.getString(KEY_PREFIX_LAST_MODEL + provider.name, "") ?: ""
            val lastError = p?.getString(KEY_PREFIX_LAST_ERROR + provider.name, null)

            val dailyLimit = getDefaultDailyLimit(provider)
            val minuteLimit = getDefaultMinuteLimit(provider)

            newMap[provider] = UserProviderUsageState(
                providerId = provider,
                totalRequests = totalReq,
                todayRequests = todayReq,
                successfulRequests = successReq,
                failedRequests = failedReq,
                estimatedTokensUsed = tokens,
                lastUsedTimestamp = lastTime,
                dailyLimit = dailyLimit,
                minuteLimit = minuteLimit,
                tierLabel = when (provider) {
                    ProviderId.GEMINI -> "Gemini API Free Tier (Personal Key)"
                    ProviderId.GROQ -> "Groq Cloud (Ultra-Fast Free Tier)"
                    ProviderId.OPENAI -> "OpenAI Platform (Personal BYOK)"
                    ProviderId.XAI -> "xAI Grok Console (Personal BYOK)"
                    ProviderId.DEEPSEEK -> "DeepSeek Platform (Personal BYOK)"
                    ProviderId.CLAUDE -> "Anthropic Claude (Personal BYOK)"
                },
                lastModelUsed = lastModel,
                lastError = lastError
            )
        }

        _usageMap.value = newMap
    }
}
