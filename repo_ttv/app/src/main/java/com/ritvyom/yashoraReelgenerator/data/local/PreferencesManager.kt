package com.ritvyom.yashoraReelgenerator.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "yashora_reels_preferences")

private val Context.safePreferencesFlow: Flow<Preferences>
    get() = dataStore.data.catch { exception ->
        if (exception is IOException) {
            emit(emptyPreferences())
        } else {
            throw exception
        }
    }

class PreferencesManager(private val context: Context) {

    companion object {
        val APP_THEME = stringPreferencesKey("app_theme")
        val APP_LANGUAGE = stringPreferencesKey("app_language")
        val VIDEO_LANGUAGE = stringPreferencesKey("video_language")
        val PREFERRED_VOICE_NAME = stringPreferencesKey("preferred_voice_name")
        val PREFERRED_VOICE_CATEGORY = stringPreferencesKey("preferred_voice_category")
        val PREFERRED_VOICE_SPEED = floatPreferencesKey("preferred_voice_speed")
        val PREFERRED_VOICE_PITCH = floatPreferencesKey("preferred_voice_pitch")
        val PREFERRED_VOICE_EMOTION = stringPreferencesKey("preferred_voice_emotion")
        val PREFERRED_TTS_ENGINE = stringPreferencesKey("preferred_tts_engine")
        val PREFERRED_TTS_ENABLED = booleanPreferencesKey("preferred_tts_enabled")
        val AD_DISPLAY_COUNT = intPreferencesKey("ad_display_count")
        val APPLOVIN_MEDIATION_ENABLED = booleanPreferencesKey("applovin_mediation_enabled")
        val APPLOVIN_SDK_KEY = stringPreferencesKey("applovin_sdk_key")
        val APPLOVIN_ZONE_ID_REWARDED = stringPreferencesKey("applovin_zone_id_rewarded")
        val APPLOVIN_ZONE_ID_INTERSTITIAL = stringPreferencesKey("applovin_zone_id_interstitial")
        val MEDIATION_BIDDING_STRATEGY = stringPreferencesKey("mediation_bidding_strategy")
        val GOOGLE_BIDDING_APP_ID = stringPreferencesKey("google_bidding_app_id")
        val ADMOB_BANNER_AD_UNIT_ID = stringPreferencesKey("admob_banner_ad_unit_id")
        val ADMOB_INTERSTITIAL_AD_UNIT_ID = stringPreferencesKey("admob_interstitial_ad_unit_id")
        val ADMOB_REWARDED_AD_UNIT_ID = stringPreferencesKey("admob_rewarded_ad_unit_id")
        val UNITY_GAME_ID = stringPreferencesKey("unity_game_id")
        val META_PLACEMENT_ID = stringPreferencesKey("meta_placement_id")
        val PIXABAY_API_KEY = stringPreferencesKey("pixabay_api_key")
        val GEMINI_API_KEY = stringPreferencesKey("gemini_api_key")
        val UNSPLASH_API_KEY = stringPreferencesKey("unsplash_api_key")
        val PEXELS_API_KEY = stringPreferencesKey("pexels_api_key")
        val SPOONACULAR_API_KEY = stringPreferencesKey("spoonacular_api_key")
        val ACTIVE_PREMIUM_TTS_ENGINE = stringPreferencesKey("active_premium_tts_engine")
        val SCRIPT_LANGUAGE = stringPreferencesKey("script_language")
        val SCRIPT_PLATFORM = stringPreferencesKey("script_platform")
        val SCRIPT_DURATION = stringPreferencesKey("script_duration")
        val SCRIPT_TONE = stringPreferencesKey("script_tone")
        val AI_INTELLIGENT_MATCHMAKER = booleanPreferencesKey("ai_intelligent_matchmaker")
    }

    val appThemeFlow: Flow<String> = context.safePreferencesFlow.map { preferences ->
        preferences[APP_THEME] ?: "Bento Grid"
    }

    val appLanguageFlow: Flow<String> = context.safePreferencesFlow.map { preferences ->
        preferences[APP_LANGUAGE] ?: "English"
    }

    val videoLanguageFlow: Flow<String> = context.safePreferencesFlow.map { preferences ->
        preferences[VIDEO_LANGUAGE] ?: "English"
    }

    val scriptLanguageFlow: Flow<String> = context.safePreferencesFlow.map { preferences ->
        preferences[SCRIPT_LANGUAGE] ?: "english"
    }

    val scriptPlatformFlow: Flow<String> = context.safePreferencesFlow.map { preferences ->
        preferences[SCRIPT_PLATFORM] ?: "YouTube Shorts / Reels"
    }

    val scriptDurationFlow: Flow<String> = context.safePreferencesFlow.map { preferences ->
        preferences[SCRIPT_DURATION] ?: "short"
    }

    val scriptToneFlow: Flow<String> = context.safePreferencesFlow.map { preferences ->
        preferences[SCRIPT_TONE] ?: "casual"
    }

    val voicePreferencesFlow: Flow<VoicePrefs> = context.safePreferencesFlow.map { preferences ->
        VoicePrefs(
            name = preferences[PREFERRED_VOICE_NAME] ?: "Professional Man",
            category = preferences[PREFERRED_VOICE_CATEGORY] ?: "Male",
            speed = preferences[PREFERRED_VOICE_SPEED] ?: 1.0f,
            pitch = preferences[PREFERRED_VOICE_PITCH] ?: 1.0f,
            emotion = preferences[PREFERRED_VOICE_EMOTION] ?: "Friendly",
            ttsEngine = preferences[PREFERRED_TTS_ENGINE] ?: "com.google.android.tts",
            isTtsEnabled = preferences[PREFERRED_TTS_ENABLED] ?: true
        )
    }

    suspend fun saveAppTheme(theme: String) {
        context.dataStore.edit { preferences ->
            preferences[APP_THEME] = theme
        }
    }

    suspend fun saveAppLanguage(language: String) {
        context.dataStore.edit { preferences ->
            preferences[APP_LANGUAGE] = language
        }
    }

    suspend fun saveVideoLanguage(language: String) {
        context.dataStore.edit { preferences ->
            preferences[VIDEO_LANGUAGE] = language
        }
    }

    suspend fun saveScriptLanguage(language: String) {
        context.dataStore.edit { preferences ->
            preferences[SCRIPT_LANGUAGE] = language
        }
    }

    suspend fun saveScriptPlatform(platform: String) {
        context.dataStore.edit { preferences ->
            preferences[SCRIPT_PLATFORM] = platform
        }
    }

    suspend fun saveScriptDuration(duration: String) {
        context.dataStore.edit { preferences ->
            preferences[SCRIPT_DURATION] = duration
        }
    }

    suspend fun saveScriptTone(tone: String) {
        context.dataStore.edit { preferences ->
            preferences[SCRIPT_TONE] = tone
        }
    }

    suspend fun saveVoicePreferences(prefs: VoicePrefs) {
        context.dataStore.edit { preferences ->
            preferences[PREFERRED_VOICE_NAME] = prefs.name
            preferences[PREFERRED_VOICE_CATEGORY] = prefs.category
            preferences[PREFERRED_VOICE_SPEED] = prefs.speed
            preferences[PREFERRED_VOICE_PITCH] = prefs.pitch
            preferences[PREFERRED_VOICE_EMOTION] = prefs.emotion
            preferences[PREFERRED_TTS_ENGINE] = prefs.ttsEngine
            preferences[PREFERRED_TTS_ENABLED] = prefs.isTtsEnabled
        }
    }

    val adDisplayCountFlow: Flow<Int> = context.safePreferencesFlow.map { preferences ->
        preferences[AD_DISPLAY_COUNT] ?: 0
    }

    suspend fun incrementAdDisplayCount() {
        context.dataStore.edit { preferences ->
            val current = preferences[AD_DISPLAY_COUNT] ?: 0
            preferences[AD_DISPLAY_COUNT] = current + 1
        }
    }

    val appLovinMediationEnabledFlow: Flow<Boolean> = context.safePreferencesFlow.map { preferences ->
        preferences[APPLOVIN_MEDIATION_ENABLED] ?: true
    }

    val appLovinSdkKeyFlow: Flow<String> = context.safePreferencesFlow.map { preferences ->
        preferences[APPLOVIN_SDK_KEY] ?: "applovin_max_sdk_premium_high_rpm_active_2026"
    }

    val appLovinZoneIdRewardedFlow: Flow<String> = context.safePreferencesFlow.map { preferences ->
        preferences[APPLOVIN_ZONE_ID_REWARDED] ?: "rewarded_google_bidding_high_rpm"
    }

    val appLovinZoneIdInterstitialFlow: Flow<String> = context.safePreferencesFlow.map { preferences ->
        preferences[APPLOVIN_ZONE_ID_INTERSTITIAL] ?: "interstitial_google_bidding_high_rpm"
    }

    val mediationBiddingStrategyFlow: Flow<String> = context.safePreferencesFlow.map { preferences ->
        preferences[MEDIATION_BIDDING_STRATEGY] ?: "Dynamic High-RPM (Bidding-First)"
    }

    val googleBiddingAppIdFlow: Flow<String> = context.safePreferencesFlow.map { preferences ->
        preferences[GOOGLE_BIDDING_APP_ID] ?: "ca-app-pub-3940256099942544~3347511713"
    }

    val admobBannerAdUnitIdFlow: Flow<String> = context.safePreferencesFlow.map { preferences ->
        preferences[ADMOB_BANNER_AD_UNIT_ID] ?: "ca-app-pub-3940256099942544/6300978111"
    }

    val admobInterstitialAdUnitIdFlow: Flow<String> = context.safePreferencesFlow.map { preferences ->
        preferences[ADMOB_INTERSTITIAL_AD_UNIT_ID] ?: "ca-app-pub-3940256099942544/1033173712"
    }

    val admobRewardedAdUnitIdFlow: Flow<String> = context.safePreferencesFlow.map { preferences ->
        preferences[ADMOB_REWARDED_AD_UNIT_ID] ?: "ca-app-pub-3940256099942544/5224354917"
    }

    val unityGameIdFlow: Flow<String> = context.safePreferencesFlow.map { preferences ->
        preferences[UNITY_GAME_ID] ?: "5123456"
    }

    val metaPlacementIdFlow: Flow<String> = context.safePreferencesFlow.map { preferences ->
        preferences[META_PLACEMENT_ID] ?: "meta_placement_id_default_2026"
    }

    suspend fun saveAppLovinMediationEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[APPLOVIN_MEDIATION_ENABLED] = enabled
        }
    }

    suspend fun saveAppLovinSdkKey(key: String) {
        context.dataStore.edit { preferences ->
            preferences[APPLOVIN_SDK_KEY] = key
        }
    }

    suspend fun saveAppLovinZoneIdRewarded(id: String) {
        context.dataStore.edit { preferences ->
            preferences[APPLOVIN_ZONE_ID_REWARDED] = id
        }
    }

    suspend fun saveAppLovinZoneIdInterstitial(id: String) {
        context.dataStore.edit { preferences ->
            preferences[APPLOVIN_ZONE_ID_INTERSTITIAL] = id
        }
    }

    suspend fun saveMediationBiddingStrategy(strategy: String) {
        context.dataStore.edit { preferences ->
            preferences[MEDIATION_BIDDING_STRATEGY] = strategy
        }
    }

    suspend fun saveGoogleBiddingAppId(appId: String) {
        context.dataStore.edit { preferences ->
            preferences[GOOGLE_BIDDING_APP_ID] = appId
        }
    }

    suspend fun saveAdmobBannerAdUnitId(adUnitId: String) {
        context.dataStore.edit { preferences ->
            preferences[ADMOB_BANNER_AD_UNIT_ID] = adUnitId
        }
    }

    suspend fun saveAdmobInterstitialAdUnitId(adUnitId: String) {
        context.dataStore.edit { preferences ->
            preferences[ADMOB_INTERSTITIAL_AD_UNIT_ID] = adUnitId
        }
    }

    suspend fun saveAdmobRewardedAdUnitId(adUnitId: String) {
        context.dataStore.edit { preferences ->
            preferences[ADMOB_REWARDED_AD_UNIT_ID] = adUnitId
        }
    }

    suspend fun saveUnityGameId(id: String) {
        context.dataStore.edit { preferences ->
            preferences[UNITY_GAME_ID] = id
        }
    }

    suspend fun saveMetaPlacementId(id: String) {
        context.dataStore.edit { preferences ->
            preferences[META_PLACEMENT_ID] = id
        }
    }

    val pixabayApiKeyFlow: Flow<String> = context.safePreferencesFlow.map { preferences ->
        preferences[PIXABAY_API_KEY] ?: ""
    }

    suspend fun savePixabayApiKey(key: String) {
        context.dataStore.edit { preferences ->
            preferences[PIXABAY_API_KEY] = key
        }
    }

    val geminiApiKeyFlow: Flow<String> = context.safePreferencesFlow.map { preferences ->
        preferences[GEMINI_API_KEY] ?: ""
    }

    suspend fun saveGeminiApiKey(key: String) {
        context.dataStore.edit { preferences ->
            preferences[GEMINI_API_KEY] = key
        }
    }

    val unsplashApiKeyFlow: Flow<String> = context.safePreferencesFlow.map { preferences ->
        preferences[UNSPLASH_API_KEY] ?: ""
    }

    suspend fun saveUnsplashApiKey(key: String) {
        context.dataStore.edit { preferences ->
            preferences[UNSPLASH_API_KEY] = key
        }
    }

    val pexelsApiKeyFlow: Flow<String> = context.safePreferencesFlow.map { preferences ->
        preferences[PEXELS_API_KEY] ?: ""
    }

    suspend fun savePexelsApiKey(key: String) {
        context.dataStore.edit { preferences ->
            preferences[PEXELS_API_KEY] = key
        }
    }

    val spoonacularApiKeyFlow: Flow<String> = context.safePreferencesFlow.map { preferences ->
        preferences[SPOONACULAR_API_KEY] ?: ""
    }

    suspend fun saveSpoonacularApiKey(key: String) {
        context.dataStore.edit { preferences ->
            preferences[SPOONACULAR_API_KEY] = key
        }
    }

    val activePremiumTtsEngineFlow: Flow<String> = context.safePreferencesFlow.map { preferences ->
        preferences[ACTIVE_PREMIUM_TTS_ENGINE] ?: "system_default"
    }

    suspend fun saveActivePremiumTtsEngine(engine: String) {
        context.dataStore.edit { preferences ->
            preferences[ACTIVE_PREMIUM_TTS_ENGINE] = engine
        }
    }

    val aiIntelligentMatchmakerFlow: Flow<Boolean> = context.safePreferencesFlow.map { preferences ->
        preferences[AI_INTELLIGENT_MATCHMAKER] ?: true
    }

    suspend fun saveAiIntelligentMatchmaker(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[AI_INTELLIGENT_MATCHMAKER] = enabled
        }
    }
}

data class VoicePrefs(
    val name: String,
    val category: String,
    val speed: Float,
    val pitch: Float,
    val emotion: String,
    val ttsEngine: String = "com.google.android.tts",
    val isTtsEnabled: Boolean = true
)
