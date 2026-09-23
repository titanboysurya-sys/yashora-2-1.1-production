package com.ritvyom.yashoraReelgenerator.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import com.ritvyom.yashoraReelgenerator.domain.models.WatermarkConfig
import com.ritvyom.yashoraReelgenerator.domain.models.WatermarkPosition
import com.ritvyom.yashoraReelgenerator.domain.models.WatermarkFontFamily
import com.ritvyom.yashoraReelgenerator.domain.models.WatermarkStyle

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
        val ELEVENLABS_API_KEY = stringPreferencesKey("elevenlabs_api_key")
        val VOXELEVEN_SELECTED_VOICE_ID = stringPreferencesKey("voxeleven_selected_voice_id")
        val VOXELEVEN_SELECTED_VOICE_NAME = stringPreferencesKey("voxeleven_selected_voice_name")
        val VOXELEVEN_MODEL_ID = stringPreferencesKey("voxeleven_model_id")
        val VOXELEVEN_STABILITY = floatPreferencesKey("voxeleven_stability")
        val VOXELEVEN_SIMILARITY = floatPreferencesKey("voxeleven_similarity")
        val VOXELEVEN_STYLE = floatPreferencesKey("voxeleven_style")
        val VOXELEVEN_USE_SPEAKER_BOOST = booleanPreferencesKey("voxeleven_use_speaker_boost")
        val VOXELEVEN_FAVORITES = stringSetPreferencesKey("voxeleven_favorites")
        val DOWNLOADED_SHERPA_MODELS = stringPreferencesKey("downloaded_sherpa_models")
        val WATERMARK_ENABLED = booleanPreferencesKey("watermark_enabled")
        val WATERMARK_TEXT = stringPreferencesKey("watermark_text")
        val WATERMARK_POSITION = stringPreferencesKey("watermark_position")
        val WATERMARK_FONT_SIZE = intPreferencesKey("watermark_font_size")
        val WATERMARK_FONT_FAMILY = stringPreferencesKey("watermark_font_family")
        val WATERMARK_COLOR = stringPreferencesKey("watermark_color")
        val WATERMARK_OPACITY = floatPreferencesKey("watermark_opacity")
        val WATERMARK_STYLE = stringPreferencesKey("watermark_style")
    }

    val appThemeFlow: Flow<String> = context.safePreferencesFlow.map { preferences ->
        val theme = preferences[APP_THEME] ?: "Light"
        if (theme == "Bento Grid") "Light" else theme
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

    val elevenLabsApiKeyFlow: Flow<String> = context.safePreferencesFlow.map { preferences ->
        preferences[ELEVENLABS_API_KEY] ?: ""
    }

    suspend fun saveElevenLabsApiKey(key: String) {
        context.dataStore.edit { preferences ->
            preferences[ELEVENLABS_API_KEY] = key
        }
    }

    val voxElevenVoiceIdFlow: Flow<String> = context.safePreferencesFlow.map { preferences ->
        preferences[VOXELEVEN_SELECTED_VOICE_ID] ?: "21m00Tcm4TlvDq8ikWAM"
    }

    val voxElevenVoiceNameFlow: Flow<String> = context.safePreferencesFlow.map { preferences ->
        preferences[VOXELEVEN_SELECTED_VOICE_NAME] ?: "Rachel (US / Soft & Friendly)"
    }

    suspend fun saveVoxElevenVoice(voiceId: String, voiceName: String) {
        context.dataStore.edit { preferences ->
            preferences[VOXELEVEN_SELECTED_VOICE_ID] = voiceId
            preferences[VOXELEVEN_SELECTED_VOICE_NAME] = voiceName
        }
    }

    val voxElevenModelIdFlow: Flow<String> = context.safePreferencesFlow.map { preferences ->
        preferences[VOXELEVEN_MODEL_ID] ?: "eleven_multilingual_v2"
    }

    suspend fun saveVoxElevenModelId(modelId: String) {
        context.dataStore.edit { preferences ->
            preferences[VOXELEVEN_MODEL_ID] = modelId
        }
    }

    val voxElevenStabilityFlow: Flow<Float> = context.safePreferencesFlow.map { preferences ->
        preferences[VOXELEVEN_STABILITY] ?: 0.5f
    }

    suspend fun saveVoxElevenStability(stability: Float) {
        context.dataStore.edit { preferences ->
            preferences[VOXELEVEN_STABILITY] = stability
        }
    }

    val voxElevenSimilarityFlow: Flow<Float> = context.safePreferencesFlow.map { preferences ->
        preferences[VOXELEVEN_SIMILARITY] ?: 0.75f
    }

    suspend fun saveVoxElevenSimilarity(similarity: Float) {
        context.dataStore.edit { preferences ->
            preferences[VOXELEVEN_SIMILARITY] = similarity
        }
    }

    val voxElevenStyleFlow: Flow<Float> = context.safePreferencesFlow.map { preferences ->
        preferences[VOXELEVEN_STYLE] ?: 0.0f
    }

    suspend fun saveVoxElevenStyle(style: Float) {
        context.dataStore.edit { preferences ->
            preferences[VOXELEVEN_STYLE] = style
        }
    }

    val voxElevenSpeakerBoostFlow: Flow<Boolean> = context.safePreferencesFlow.map { preferences ->
        preferences[VOXELEVEN_USE_SPEAKER_BOOST] ?: true
    }

    suspend fun saveVoxElevenSpeakerBoost(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[VOXELEVEN_USE_SPEAKER_BOOST] = enabled
        }
    }

    val voxElevenFavoritesFlow: Flow<Set<String>> = context.safePreferencesFlow.map { preferences ->
        preferences[VOXELEVEN_FAVORITES] ?: emptySet()
    }

    suspend fun toggleVoxElevenFavorite(voiceId: String) {
        context.dataStore.edit { preferences ->
            val current = preferences[VOXELEVEN_FAVORITES] ?: emptySet()
            if (current.contains(voiceId)) {
                preferences[VOXELEVEN_FAVORITES] = current - voiceId
            } else {
                preferences[VOXELEVEN_FAVORITES] = current + voiceId
            }
        }
    }

    val downloadedSherpaModelsFlow: Flow<String> = context.safePreferencesFlow.map { preferences ->
        preferences[DOWNLOADED_SHERPA_MODELS] ?: "english"
    }

    val watermarkConfigFlow: Flow<WatermarkConfig> = context.safePreferencesFlow.map { preferences ->
        val isEnabled = preferences[WATERMARK_ENABLED] ?: false
        val text = preferences[WATERMARK_TEXT] ?: ""
        val posStr = preferences[WATERMARK_POSITION] ?: WatermarkPosition.BOTTOM_RIGHT.name
        val position = try { WatermarkPosition.valueOf(posStr) } catch (e: Exception) { WatermarkPosition.BOTTOM_RIGHT }
        val fontSize = preferences[WATERMARK_FONT_SIZE] ?: 14
        val fontStr = preferences[WATERMARK_FONT_FAMILY] ?: WatermarkFontFamily.SANS_SERIF.name
        val fontFamily = try { WatermarkFontFamily.valueOf(fontStr) } catch (e: Exception) { WatermarkFontFamily.SANS_SERIF }
        val color = preferences[WATERMARK_COLOR] ?: "#FFFFFF"
        val opacity = preferences[WATERMARK_OPACITY] ?: 0.75f
        val styleStr = preferences[WATERMARK_STYLE] ?: WatermarkStyle.SHADOW.name
        val style = try { WatermarkStyle.valueOf(styleStr) } catch (e: Exception) { WatermarkStyle.SHADOW }

        WatermarkConfig(
            isEnabled = isEnabled,
            text = text,
            position = position,
            fontSizeSp = fontSize,
            fontFamily = fontFamily,
            colorHex = color,
            opacity = opacity,
            style = style
        )
    }

    suspend fun saveWatermarkConfig(config: WatermarkConfig) {
        context.dataStore.edit { preferences ->
            preferences[WATERMARK_ENABLED] = config.isEnabled
            preferences[WATERMARK_TEXT] = config.text
            preferences[WATERMARK_POSITION] = config.position.name
            preferences[WATERMARK_FONT_SIZE] = config.fontSizeSp
            preferences[WATERMARK_FONT_FAMILY] = config.fontFamily.name
            preferences[WATERMARK_COLOR] = config.colorHex
            preferences[WATERMARK_OPACITY] = config.opacity
            preferences[WATERMARK_STYLE] = config.style.name
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
