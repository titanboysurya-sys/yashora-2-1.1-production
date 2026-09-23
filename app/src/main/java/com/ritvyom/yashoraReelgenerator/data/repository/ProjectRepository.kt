package com.ritvyom.yashoraReelgenerator.data.repository

import com.ritvyom.yashoraReelgenerator.data.local.dao.ExportHistoryDao
import com.ritvyom.yashoraReelgenerator.data.local.dao.ProjectDao
import com.ritvyom.yashoraReelgenerator.data.local.entities.ExportHistoryEntity
import com.ritvyom.yashoraReelgenerator.data.local.entities.ProjectEntity
import com.ritvyom.yashoraReelgenerator.data.local.PreferencesManager
import com.ritvyom.yashoraReelgenerator.data.local.VoicePrefs
import com.ritvyom.yashoraReelgenerator.data.remote.GeminiService
import com.ritvyom.yashoraReelgenerator.data.remote.ApiLoadBalancerService
import com.ritvyom.yashoraReelgenerator.domain.models.Scene
import com.ritvyom.yashoraReelgenerator.domain.models.WatermarkConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.io.File
import android.content.Context

import com.ritvyom.yashoraReelgenerator.data.local.dao.CachedImageDao
import com.ritvyom.yashoraReelgenerator.data.local.dao.CommunityVideoCacheDao
import com.ritvyom.yashoraReelgenerator.data.local.entities.CachedImageEntity
import com.ritvyom.yashoraReelgenerator.data.local.entities.CommunityVideoCacheEntity
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

class ProjectRepository(
    private val context: android.content.Context,
    private val projectDao: ProjectDao,
    private val exportHistoryDao: ExportHistoryDao,
    private val cachedImageDao: CachedImageDao,
    private val communityVideoCacheDao: CommunityVideoCacheDao,
    private val preferencesManager: PreferencesManager,
    private val geminiService: GeminiService
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .writeTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    // Flow getters
    val allProjects: Flow<List<ProjectEntity>> = projectDao.getAllProjectsFlow()
    val allHistory: Flow<List<ExportHistoryEntity>> = exportHistoryDao.getAllHistoryFlow()
    val allCachedCommunityVideos: Flow<List<CommunityVideoCacheEntity>> = communityVideoCacheDao.getAllCachedVideosFlow()
    val cachedCommunityVideosCount: Flow<Int> = communityVideoCacheDao.getCachedCountFlow()
    val totalCommunityVideoCacheBytes: Flow<Long?> = communityVideoCacheDao.getTotalCacheSizeBytesFlow()
    val appTheme: Flow<String> = preferencesManager.appThemeFlow
    val appLanguage: Flow<String> = preferencesManager.appLanguageFlow
    val videoLanguage: Flow<String> = preferencesManager.videoLanguageFlow
    val voicePreferences: Flow<VoicePrefs> = preferencesManager.voicePreferencesFlow
    val adDisplayCount: Flow<Int> = preferencesManager.adDisplayCountFlow
    val appLovinMediationEnabled: Flow<Boolean> = preferencesManager.appLovinMediationEnabledFlow
    val appLovinSdkKey: Flow<String> = preferencesManager.appLovinSdkKeyFlow
    val appLovinZoneIdRewarded: Flow<String> = preferencesManager.appLovinZoneIdRewardedFlow
    val appLovinZoneIdInterstitial: Flow<String> = preferencesManager.appLovinZoneIdInterstitialFlow
    val mediationBiddingStrategy: Flow<String> = preferencesManager.mediationBiddingStrategyFlow
    val googleBiddingAppId: Flow<String> = preferencesManager.googleBiddingAppIdFlow
    val admobBannerAdUnitId: Flow<String> = preferencesManager.admobBannerAdUnitIdFlow
    val admobInterstitialAdUnitId: Flow<String> = preferencesManager.admobInterstitialAdUnitIdFlow
    val admobRewardedAdUnitId: Flow<String> = preferencesManager.admobRewardedAdUnitIdFlow
    val unityGameId: Flow<String> = preferencesManager.unityGameIdFlow
    val metaPlacementId: Flow<String> = preferencesManager.metaPlacementIdFlow
    val pixabayApiKey: Flow<String> = preferencesManager.pixabayApiKeyFlow
    val geminiApiKey: Flow<String> = preferencesManager.geminiApiKeyFlow
    val unsplashApiKey: Flow<String> = preferencesManager.unsplashApiKeyFlow
    val pexelsApiKey: Flow<String> = preferencesManager.pexelsApiKeyFlow
    val spoonacularApiKey: Flow<String> = preferencesManager.spoonacularApiKeyFlow
    val activePremiumTtsEngine: Flow<String> = preferencesManager.activePremiumTtsEngineFlow
    val aiIntelligentMatchmaker: Flow<Boolean> = preferencesManager.aiIntelligentMatchmakerFlow
    val elevenLabsApiKey: Flow<String> = preferencesManager.elevenLabsApiKeyFlow
    val voxElevenVoiceId: Flow<String> = preferencesManager.voxElevenVoiceIdFlow
    val voxElevenVoiceName: Flow<String> = preferencesManager.voxElevenVoiceNameFlow
    val voxElevenModelId: Flow<String> = preferencesManager.voxElevenModelIdFlow
    val voxElevenStability: Flow<Float> = preferencesManager.voxElevenStabilityFlow
    val voxElevenSimilarity: Flow<Float> = preferencesManager.voxElevenSimilarityFlow
    val voxElevenStyle: Flow<Float> = preferencesManager.voxElevenStyleFlow
    val voxElevenSpeakerBoost: Flow<Boolean> = preferencesManager.voxElevenSpeakerBoostFlow
    val voxElevenFavorites: Flow<Set<String>> = preferencesManager.voxElevenFavoritesFlow
    val watermarkConfig: Flow<WatermarkConfig> = preferencesManager.watermarkConfigFlow

    suspend fun saveWatermarkConfig(config: WatermarkConfig) {
        preferencesManager.saveWatermarkConfig(config)
    }

    suspend fun saveElevenLabsApiKey(key: String) {
        preferencesManager.saveElevenLabsApiKey(key)
    }

    suspend fun saveVoxElevenVoice(voiceId: String, voiceName: String) {
        preferencesManager.saveVoxElevenVoice(voiceId, voiceName)
    }

    suspend fun saveVoxElevenModelId(modelId: String) {
        preferencesManager.saveVoxElevenModelId(modelId)
    }

    suspend fun saveVoxElevenStability(stability: Float) {
        preferencesManager.saveVoxElevenStability(stability)
    }

    suspend fun saveVoxElevenSimilarity(similarity: Float) {
        preferencesManager.saveVoxElevenSimilarity(similarity)
    }

    suspend fun saveVoxElevenStyle(style: Float) {
        preferencesManager.saveVoxElevenStyle(style)
    }

    suspend fun saveVoxElevenSpeakerBoost(enabled: Boolean) {
        preferencesManager.saveVoxElevenSpeakerBoost(enabled)
    }

    suspend fun toggleVoxElevenFavorite(voiceId: String) {
        preferencesManager.toggleVoxElevenFavorite(voiceId)
    }

    suspend fun saveSpoonacularApiKey(key: String) {
        preferencesManager.saveSpoonacularApiKey(key)
    }

    suspend fun saveActivePremiumTtsEngine(engine: String) {
        preferencesManager.saveActivePremiumTtsEngine(engine)
    }

    // DB Operations - Cached Images
    private fun buildCompositeKey(keyword: String, style: String, visualMedium: String, visualPrompt: String = ""): String {
        val k = keyword.trim().lowercase()
        val s = style.trim().lowercase()
        val m = visualMedium.trim().lowercase()
        val p = visualPrompt.trim().lowercase().hashCode().toString()
        return if (visualPrompt.isNotEmpty()) {
            "$k|$s|$m|$p"
        } else if (s.isNotEmpty() || m.isNotEmpty()) {
            "$k|$s|$m"
        } else {
            k
        }
    }

    suspend fun getCachedImage(
        keyword: String,
        style: String = "",
        visualMedium: String = "",
        visualPrompt: String = "",
        maxAgeMs: Long = 30 * 24 * 60 * 60 * 1000L // 30-day validity verification threshold
    ): String? {
        val compositeKey = buildCompositeKey(keyword, style, visualMedium, visualPrompt)
        val cached = cachedImageDao.getCachedImageByKeyword(compositeKey) ?: return null

        // Verify validity of asset metadata before reusing cached clip
        val now = System.currentTimeMillis()
        val assetAge = now - cached.timestamp
        if (assetAge > maxAgeMs) {
            android.util.Log.i("ProjectRepository", "Cached asset metadata for '$keyword' expired (age: ${assetAge / 1000}s). Deleting invalid cache entry.")
            cachedImageDao.deleteCachedImageByKeyword(compositeKey)
            return null
        }

        val validUrl = if (cached.imageUrl.isNotBlank()) cached.imageUrl else cached.sourceUrl
        return if (validUrl.isNotBlank()) validUrl else null
    }

    suspend fun deleteCachedImage(keyword: String, style: String = "", visualMedium: String = "", visualPrompt: String = "") {
        val compositeKey = buildCompositeKey(keyword, style, visualMedium, visualPrompt)
        cachedImageDao.deleteCachedImageByKeyword(compositeKey)
    }

    suspend fun normalizeTopic(topic: String): String {
        val customGeminiKey = try {
            preferencesManager.geminiApiKeyFlow.first()
        } catch (e: Exception) {
            ""
        }
        return try {
            geminiService.normalizeTopicToEnglish(topic, customGeminiKey)
        } catch (e: Exception) {
            topic.trim().lowercase()
        }
    }

    suspend fun saveCachedImage(
        keyword: String,
        imageUrl: String,
        style: String = "",
        visualMedium: String = "",
        visualPrompt: String = "",
        aspectRatio: String = "9:16",
        saveGlobally: Boolean = false,
        force: Boolean = false,
        sourceUrl: String = "",
        entityName: String = ""
    ) {
        if (!force) {
            // Skip caching during draft/generation phase to avoid storing bad/unapproved visual assets.
            // These will be cached locally and globally on successful export/approval.
            return
        }
        if (isRepetitiveOrLowQuality(imageUrl, keyword, emptySet(), visualMedium)) {
            android.util.Log.d("ProjectRepository", "saveCachedImage: Skipped caching low-quality/placeholder/repetitive asset for keyword: $keyword, URL: $imageUrl")
            return
        }
        if (keyword.isNotEmpty() && imageUrl.isNotEmpty()) {
            val compositeKey = buildCompositeKey(keyword, style, visualMedium, visualPrompt)
            val now = System.currentTimeMillis()
            val entity = CachedImageEntity(
                keyword = compositeKey,
                imageUrl = imageUrl,
                sourceUrl = if (sourceUrl.isNotBlank()) sourceUrl else imageUrl,
                entityName = if (entityName.isNotBlank()) entityName else keyword,
                timestamp = now,
                createdAt = now
            )
            cachedImageDao.insertCachedImage(entity)

            if (saveGlobally) {
                // Note: Firestore cloud cache mapping is saved exclusively upon user export confirmation.
            }
        }
    }

    suspend fun shareMediaMappingsForProject(
        scenes: List<com.ritvyom.yashoraReelgenerator.domain.models.Scene>,
        style: String,
        visualMedium: String,
        aspectRatio: String
    ) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        for (scene in scenes) {
            val remoteUrl = if (!scene.mediaPath.isNullOrEmpty() && scene.mediaPath!!.startsWith("http")) {
                scene.mediaPath
            } else if (!scene.remoteUrl.isNullOrEmpty() && scene.remoteUrl!!.startsWith("http")) {
                scene.remoteUrl
            } else null

            if (remoteUrl != null && remoteUrl.isNotEmpty() && !remoteUrl.startsWith("/") && !remoteUrl.startsWith("file:") && !remoteUrl.startsWith("content:")) {
                // Save to local cache now because this is a verified/exported asset!
                for (keyword in scene.keywords) {
                    if (keyword.isNotEmpty()) {
                        saveCachedImage(
                            keyword = keyword.trim().lowercase(),
                            imageUrl = remoteUrl,
                            style = style,
                            visualMedium = visualMedium,
                            visualPrompt = scene.visualPrompt,
                            aspectRatio = aspectRatio,
                            force = true
                        )
                        android.util.Log.d("ProjectRepository", "Sharing verified media mapping to cloud cache on successful export: '$keyword' -> $remoteUrl")
                        com.ritvyom.yashoraReelgenerator.data.remote.FirebaseCloudManager.saveScriptMediaMapping(
                            query = keyword.trim().lowercase(),
                            style = style,
                            aspectRatio = aspectRatio,
                            visualMedium = visualMedium,
                            mediaUrl = remoteUrl
                        )
                    }
                }

                // Also save under visual prompt
                if (scene.visualPrompt.isNotEmpty()) {
                    saveCachedImage(
                        keyword = scene.visualPrompt.trim().lowercase(),
                        imageUrl = remoteUrl,
                        style = style,
                        visualMedium = visualMedium,
                        visualPrompt = scene.visualPrompt,
                        aspectRatio = aspectRatio,
                        force = true
                    )
                    android.util.Log.d("ProjectRepository", "Sharing verified media mapping to cloud cache for visualPrompt on export -> $remoteUrl")
                    com.ritvyom.yashoraReelgenerator.data.remote.FirebaseCloudManager.saveScriptMediaMapping(
                        query = scene.visualPrompt.trim().lowercase(),
                        style = style,
                        aspectRatio = aspectRatio,
                        visualMedium = visualMedium,
                        mediaUrl = remoteUrl
                    )
                }
            }
        }
    }

    // DB Operations - Projects
    suspend fun getProjectById(id: Int): ProjectEntity? {
        return projectDao.getProjectById(id)
    }

    suspend fun insertProject(project: ProjectEntity): Long {
        return projectDao.insertProject(project)
    }

    suspend fun updateProject(project: ProjectEntity) {
        projectDao.updateProject(project)
    }

    suspend fun deleteProject(project: ProjectEntity) {
        projectDao.deleteProject(project)
    }

    suspend fun deleteProjectById(id: Int) {
        projectDao.deleteProjectById(id)
    }

    // DB Operations - Export History
    suspend fun insertExportHistory(history: ExportHistoryEntity): Long {
        return exportHistoryDao.insertHistory(history)
    }

    suspend fun deleteHistoryById(id: Int) {
        exportHistoryDao.deleteHistoryById(id)
    }

    suspend fun clearHistory() {
        exportHistoryDao.clearAllHistory()
    }

    // Preferences Operations
    suspend fun updateTheme(themeStr: String) {
        preferencesManager.saveAppTheme(themeStr)
    }

    suspend fun updateAppLanguage(lang: String) {
        preferencesManager.saveAppLanguage(lang)
    }

    suspend fun updateVideoLanguage(lang: String) {
        preferencesManager.saveVideoLanguage(lang)
    }

    suspend fun updateVoicePreferences(prefs: VoicePrefs) {
        preferencesManager.saveVoicePreferences(prefs)
    }

    suspend fun incrementAdDisplay() {
        preferencesManager.incrementAdDisplayCount()
    }

    suspend fun updateAppLovinMediationEnabled(enabled: Boolean) {
        preferencesManager.saveAppLovinMediationEnabled(enabled)
    }

    suspend fun updateAppLovinSdkKey(key: String) {
        preferencesManager.saveAppLovinSdkKey(key)
    }

    suspend fun updateAppLovinZoneIdRewarded(id: String) {
        preferencesManager.saveAppLovinZoneIdRewarded(id)
    }

    suspend fun updateAppLovinZoneIdInterstitial(id: String) {
        preferencesManager.saveAppLovinZoneIdInterstitial(id)
    }

    suspend fun updateMediationBiddingStrategy(strategy: String) {
        preferencesManager.saveMediationBiddingStrategy(strategy)
    }

    suspend fun updateGoogleBiddingAppId(appId: String) {
        preferencesManager.saveGoogleBiddingAppId(appId)
    }

    suspend fun updateAdmobBannerAdUnitId(adUnitId: String) {
        preferencesManager.saveAdmobBannerAdUnitId(adUnitId)
    }

    suspend fun updateAdmobInterstitialAdUnitId(adUnitId: String) {
        preferencesManager.saveAdmobInterstitialAdUnitId(adUnitId)
    }

    suspend fun updateAdmobRewardedAdUnitId(adUnitId: String) {
        preferencesManager.saveAdmobRewardedAdUnitId(adUnitId)
    }

    suspend fun updateUnityGameId(id: String) {
        preferencesManager.saveUnityGameId(id)
    }

    suspend fun updateMetaPlacementId(id: String) {
        preferencesManager.saveMetaPlacementId(id)
    }

    suspend fun updatePixabayApiKey(key: String) {
        preferencesManager.savePixabayApiKey(key)
    }

    suspend fun updateGeminiApiKey(key: String) {
        preferencesManager.saveGeminiApiKey(key)
    }

    suspend fun updateUnsplashApiKey(key: String) {
        preferencesManager.saveUnsplashApiKey(key)
    }

    suspend fun updatePexelsApiKey(key: String) {
        preferencesManager.savePexelsApiKey(key)
    }

    suspend fun updateSpoonacularApiKey(key: String) {
        preferencesManager.saveSpoonacularApiKey(key)
    }

    suspend fun updateAiIntelligentMatchmaker(enabled: Boolean) {
        preferencesManager.saveAiIntelligentMatchmaker(enabled)
    }

    fun isRepetitiveOrLowQuality(
        url: String,
        query: String,
        alreadyUsedUrls: Set<String>,
        visualMedium: String = "Video"
    ): Boolean {
        if (url.isEmpty()) return true

        // 1. Check for repetitive/duplicate URL in the current batch
        if (alreadyUsedUrls.contains(url)) {
            android.util.Log.d("ProjectRepository", "isRepetitiveOrLowQuality: URL already used in this batch: $url")
            return true
        }

        // 2. Check for common low-quality static fallback thumbnails and placeholders
        val lowerUrl = url.lowercase()
        val hasLowQualityPattern = lowerUrl.contains("vimeocdn.com/video") ||
                lowerUrl.contains("youtube.com/vi") ||
                lowerUrl.contains("img.youtube.com") ||
                lowerUrl.contains("placeholder") ||
                lowerUrl.contains("default_video") ||
                lowerUrl.contains("/default") ||
                lowerUrl.contains("blank") ||
                lowerUrl.contains("pixel") ||
                lowerUrl.contains("empty") ||
                lowerUrl.contains("error")

        if (hasLowQualityPattern) {
            android.util.Log.d("ProjectRepository", "isRepetitiveOrLowQuality: URL matches a known low-quality/placeholder pattern: $url")
            return true
        }

        // 3. If it's a locally downloaded file, check if the file is extremely small (<10KB)
        if (url.startsWith("/") && !url.contains("content:")) {
            try {
                val file = java.io.File(url)
                if (file.exists() && file.isFile && file.length() < 10000) {
                    android.util.Log.d("ProjectRepository", "isRepetitiveOrLowQuality: Local file size is too small (${file.length()} bytes): $url")
                    return true
                }
            } catch (e: Exception) {
                // ignore
            }
        }

        return false
    }

    data class VisualAssetResolution(
        val url: String,
        val sourceUsed: String,
        val isSuccess: Boolean,
        val attemptedSources: List<String> = emptyList(),
        val errorMessage: String? = null
    )

    // AI API Operation
    suspend fun resolveVisualAsset(
        query: String,
        style: String,
        aspectRatio: String,
        index: Int,
        visualMedium: String, // "Video" or "Image"
        imageSource: String = "Unsplash",
        resolution: String? = null,
        visualPrompt: String = "",
        publishingStyle: String = "TikTok / Instagram Reels",
        alreadyUsedUrls: Set<String> = emptySet()
    ): String {
        return resolveVisualAssetResolution(
            query = query,
            style = style,
            aspectRatio = aspectRatio,
            index = index,
            visualMedium = visualMedium,
            imageSource = imageSource,
            resolution = resolution,
            visualPrompt = visualPrompt,
            publishingStyle = publishingStyle,
            alreadyUsedUrls = alreadyUsedUrls,
            bypassCache = false
        ).url
    }

    suspend fun resolveVisualAssetResolution(
        query: String,
        style: String,
        aspectRatio: String,
        index: Int,
        visualMedium: String, // "Video" or "Image"
        imageSource: String = "Unsplash",
        resolution: String? = null,
        visualPrompt: String = "",
        publishingStyle: String = "TikTok / Instagram Reels",
        alreadyUsedUrls: Set<String> = emptySet(),
        bypassCache: Boolean = false
    ): VisualAssetResolution = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val savedUnsplash = try {
            preferencesManager.unsplashApiKeyFlow.first()
        } catch (e: Exception) {
            ""
        }
        val queryClean = query.trim().lowercase()
        if (queryClean.isEmpty()) return@withContext VisualAssetResolution("", "", false, emptyList(), "Empty search query")

        // 1. Check local cache first with original query and unique visualPrompt (unless explicitly bypassed)
        if (!bypassCache) {
            var cached = getCachedImage(queryClean, style, visualMedium, visualPrompt = visualPrompt)
            if (cached != null && cached.isNotEmpty()) {
                if (isRepetitiveOrLowQuality(cached, queryClean, alreadyUsedUrls, visualMedium)) {
                    android.util.Log.i("ProjectRepository", "Local cache HIT for original query '$queryClean' was repetitive or low-quality. Discarding cache and forcing fresh search.")
                    deleteCachedImage(queryClean, style, visualMedium, visualPrompt)
                    cached = null
                } else {
                    return@withContext VisualAssetResolution(cached, "Cache", true, listOf("Cache"))
                }
            }

            // 1a. Check Firestore cloud cache before making external API requests
            val cloudCached = com.ritvyom.yashoraReelgenerator.data.remote.FirebaseCloudManager.getScriptMediaMapping(
                query = queryClean,
                style = style,
                aspectRatio = aspectRatio,
                visualMedium = visualMedium
            )
            if (cloudCached != null && cloudCached.isNotEmpty()) {
                if (isRepetitiveOrLowQuality(cloudCached, queryClean, alreadyUsedUrls, visualMedium)) {
                    android.util.Log.i("ProjectRepository", "Cloud cache for original query '$queryClean' was repetitive or low-quality. Skipping.")
                } else {
                    saveCachedImage(queryClean, cloudCached, style, visualMedium, visualPrompt = visualPrompt, aspectRatio = aspectRatio, force = true)
                    return@withContext VisualAssetResolution(cloudCached, "Cloud Cache", true, listOf("Cloud Cache"))
                }
            }
        }

        // Check if query contains any non-English characters and translate to English
        val queryTranslated = if (queryClean.any { it.code > 127 }) {
            val geminiKey = try {
                preferencesManager.geminiApiKeyFlow.first()
            } catch (e: Exception) {
                ""
            }
            val result = geminiService.translateNonEnglishToEnglish(queryClean, geminiKey)
            android.util.Log.i("ProjectRepository", "Translated search term '$queryClean' to English: '$result'")
            if (result.isNotEmpty()) result else queryClean
        } else {
            queryClean
        }

        val publishingStyleKeyword = when (publishingStyle) {
            "TikTok / Instagram Reels" -> "vibrant TikTok dynamic angle, short-form visual reel"
            "YouTube Short" -> "YouTube short high retention clear focal point, centered detail"
            "Cinematic Vlog / Trailer" -> "cinematic vlog trailer moody anamorphic sweeping vista landscape"
            "Short Documentary" -> "photojournalistic documentary archival, authentic slice of life"
            "Facebook Feed Video" -> "attention grabbing viral lifestyle, engaging facebook feed style"
            "LinkedIn Professional" -> "professional high class corporate business, LinkedIn networking"
            "Ad Promo / Marketing" -> "commercial product advertising showcase, bright advertising studio setup"
            else -> ""
        }

        val baseVisualPrompt = if (visualPrompt.isNotEmpty()) visualPrompt else queryTranslated
        val resolvedVisualPrompt = if (publishingStyleKeyword.isNotEmpty()) "$baseVisualPrompt, $publishingStyleKeyword" else baseVisualPrompt

        // 2. Check cache under translated query just in case (unless bypassed)
        if (queryTranslated != queryClean && !bypassCache) {
            var cachedTrans = getCachedImage(queryTranslated, style, visualMedium, visualPrompt = resolvedVisualPrompt)
            if (cachedTrans != null && cachedTrans.isNotEmpty()) {
                if (isRepetitiveOrLowQuality(cachedTrans, queryTranslated, alreadyUsedUrls, visualMedium)) {
                    android.util.Log.i("ProjectRepository", "Local cache HIT for translated query '$queryTranslated' was repetitive or low-quality. Discarding cache.")
                    deleteCachedImage(queryTranslated, style, visualMedium, resolvedVisualPrompt)
                    cachedTrans = null
                } else {
                    saveCachedImage(queryClean, cachedTrans, style, visualMedium, visualPrompt = resolvedVisualPrompt, aspectRatio = aspectRatio, force = true)
                    return@withContext VisualAssetResolution(cachedTrans, "Cache", true, listOf("Cache"))
                }
            }

            // Check Firestore cloud cache for the translated query
            val cloudCachedTrans = com.ritvyom.yashoraReelgenerator.data.remote.FirebaseCloudManager.getScriptMediaMapping(
                query = queryTranslated,
                style = style,
                aspectRatio = aspectRatio,
                visualMedium = visualMedium
            )
            if (cloudCachedTrans != null && cloudCachedTrans.isNotEmpty()) {
                if (isRepetitiveOrLowQuality(cloudCachedTrans, queryTranslated, alreadyUsedUrls, visualMedium)) {
                    android.util.Log.i("ProjectRepository", "Cloud cache for translated query '$queryTranslated' was repetitive or low-quality. Skipping.")
                } else {
                    saveCachedImage(queryClean, cloudCachedTrans, style, visualMedium, visualPrompt = resolvedVisualPrompt, aspectRatio = aspectRatio, force = true)
                    saveCachedImage(queryTranslated, cloudCachedTrans, style, visualMedium, visualPrompt = resolvedVisualPrompt, aspectRatio = aspectRatio, force = true)
                    return@withContext VisualAssetResolution(cloudCachedTrans, "Cloud Cache", true, listOf("Cloud Cache"))
                }
            }
        }

        // Direct Famous Entity / Historical Personality / Celebrity lookup via Wikipedia & Wikimedia Commons
        val isFamousEntity = geminiService.containsEntityOrFamousPerson(queryClean) || geminiService.containsEntityOrFamousPerson(resolvedVisualPrompt)
        if (isFamousEntity) {
            android.util.Log.i("ProjectRepository", "Famous Entity / Historical Personality detected ('$queryClean'). Querying Wikipedia Entity API first...")
            val wikiUrl = geminiService.searchWikipediaEntityImage(queryClean, index)
            if (wikiUrl.isNotEmpty()) {
                android.util.Log.d("ProjectRepository", "Wikipedia Entity API successfully resolved authentic photo: $wikiUrl")
                saveCachedImage(queryClean, wikiUrl, style, visualMedium, visualPrompt = resolvedVisualPrompt, aspectRatio = aspectRatio)
                saveCachedImage(queryTranslated, wikiUrl, style, visualMedium, visualPrompt = resolvedVisualPrompt, aspectRatio = aspectRatio)
                return@withContext VisualAssetResolution(wikiUrl, "Wikipedia", true, listOf("Wikipedia"))
            }
        }

        // Direct media source bypasses (Wikimedia Commons, Nekos.best, Jikan Anime, Lorem Picsum, NASA, LoremFlickr, Giphy Memes, 11-Stage Fallback)
        val isMatchmakerEnabled = try {
            preferencesManager.aiIntelligentMatchmakerFlow.first()
        } catch (e: Exception) {
            true
        }

        val resolvedImageSource = if (isMatchmakerEnabled) {
            getCompatibleImageSource(
                originalSource = imageSource,
                query = queryTranslated,
                style = style,
                visualPrompt = resolvedVisualPrompt,
                visualMedium = visualMedium
            )
        } else {
            imageSource
        }

        val srcLower = resolvedImageSource.lowercase().trim()
        val isWikimedia = srcLower.contains("wikimedia") || srcLower.contains("commons") || srcLower.contains("wikipedia") || srcLower.contains("wiki")
        val isNekos = srcLower.contains("neko")
        val isJikan = srcLower.contains("jikan") || srcLower.contains("anime") || srcLower.contains("mal")
        val isPicsum = srcLower.contains("picsum") || srcLower.contains("lorem") && !srcLower.contains("flickr")
        val isArchive = srcLower.contains("archive")
        val isNasa = srcLower.contains("nasa")
        val isFlickr = srcLower.contains("flickr")
        val isGiphy = srcLower.contains("giphy")
        val isFallback = srcLower.contains("fallback")
        val isNhtsa = srcLower.contains("nhtsa") || srcLower.contains("vpic")
        val isOpenFda = srcLower.contains("fda") || srcLower.contains("openfda")
        val isWho = srcLower.contains("who")
        
        val isCustomDirectSource = isWikimedia || isNekos || isJikan || isPicsum || isArchive || isNasa || isFlickr || isGiphy || isFallback || isNhtsa || isOpenFda || isWho
        if (isCustomDirectSource && MediaProviderHealthMonitor.isProviderHealthy(resolvedImageSource)) {
            android.util.Log.i("ProjectRepository", "Custom direct media source selected: $resolvedImageSource. Fetching directly from source...")
            val resolvedUrl = try {
                val res = geminiService.getBestMatchingImage(
                    visualPrompt = resolvedVisualPrompt,
                    style = style,
                    sceneNum = index,
                    customSearchQuery = queryTranslated,
                    aspectRatio = aspectRatio,
                    imageSource = resolvedImageSource,
                    customUnsplashKey = savedUnsplash,
                    resolution = resolution,
                    visualMedium = visualMedium
                )
                if (res.isNotEmpty()) {
                    MediaProviderHealthMonitor.recordSuccess(resolvedImageSource)
                }
                res
            } catch (e: Exception) {
                android.util.Log.w("ProjectRepository", "Error fetching from custom direct media source: $resolvedImageSource: ${e.message}")
                MediaProviderHealthMonitor.recordFailure(resolvedImageSource)
                ""
            }
            if (resolvedUrl.isNotEmpty()) {
                saveCachedImage(queryClean, resolvedUrl, style, visualMedium, visualPrompt = resolvedVisualPrompt, aspectRatio = aspectRatio)
                saveCachedImage(queryTranslated, resolvedUrl, style, visualMedium, visualPrompt = resolvedVisualPrompt, aspectRatio = aspectRatio)
                return@withContext VisualAssetResolution(resolvedUrl, MediaFetchingStatusMonitor.normalizeSourceName(resolvedImageSource), true, listOf(resolvedImageSource))
            }
        }

        // Direct AI-Generator bypass/priority check
        val isAiGenerated = (resolvedImageSource.equals("AI Generated", ignoreCase = true) || resolvedImageSource.equals("AI_Generated", ignoreCase = true)) && !visualMedium.equals("Video", ignoreCase = true)
        if (isAiGenerated) {
            android.util.Log.i("ProjectRepository", "AI Generated source selected. Directly generating themed AI illustration via Pollinations AI...")
            val generatedUrl = try {
                geminiService.getBestMatchingImage(
                    visualPrompt = resolvedVisualPrompt,
                    style = style,
                    sceneNum = index,
                    customSearchQuery = queryTranslated,
                    aspectRatio = aspectRatio,
                    imageSource = "AI Generated",
                    customUnsplashKey = savedUnsplash,
                    resolution = resolution
                )
            } catch (e: Exception) {
                android.util.Log.e("ProjectRepository", "Error generating themed AI illustration via Pollinations AI", e)
                ""
            }
            if (generatedUrl.isNotEmpty()) {
                saveCachedImage(queryClean, generatedUrl, style, visualMedium, visualPrompt = resolvedVisualPrompt, aspectRatio = aspectRatio)
                saveCachedImage(queryTranslated, generatedUrl, style, visualMedium, visualPrompt = resolvedVisualPrompt, aspectRatio = aspectRatio)
                return@withContext VisualAssetResolution(generatedUrl, "AI Generated", true, listOf("AI Generated"))
            }
        }

        // 1b. Check if query mentions a popular celebrity/politician/leader to bypass stock media APIs
        val isCeleb = (isCelebrityQuery(queryClean) || isCelebrityQuery(queryTranslated)) && !visualMedium.equals("Video", ignoreCase = true)
        if (isCeleb) {
            val targetCelebQuery = if (isCelebrityQuery(queryClean)) queryClean else queryTranslated
            android.util.Log.i("ProjectRepository", "Celebrity detected in query. Generating photorealistic likeness via Pollinations AI...")
            val generatedUrl = try {
                geminiService.getBestMatchingImage(
                    visualPrompt = targetCelebQuery,
                    style = style,
                    sceneNum = index,
                    customSearchQuery = targetCelebQuery,
                    aspectRatio = aspectRatio,
                    imageSource = "AI Generated",
                    customUnsplashKey = savedUnsplash,
                    resolution = resolution
                )
            } catch (e: Exception) {
                android.util.Log.e("ProjectRepository", "Error generating celebrity photorealistic likeness", e)
                ""
            }
            if (generatedUrl.isNotEmpty()) {
                saveCachedImage(queryClean, generatedUrl, style, visualMedium, visualPrompt = resolvedVisualPrompt, aspectRatio = aspectRatio)
                saveCachedImage(queryTranslated, generatedUrl, style, visualMedium, visualPrompt = resolvedVisualPrompt, aspectRatio = aspectRatio)
                return@withContext VisualAssetResolution(generatedUrl, "AI Generated", true, listOf("AI Generated"))
            }
        }

        // 1c. Artistic/Style Bypass Filter (CRITICAL INTENT EXTRACTION):
        val isArtisticStyle = if (style.isNotEmpty()) {
            val s = style.lowercase().trim()
            s != "realistic" && s != "cinematic" && s != "documentary" && s != "news"
        } else {
            false
        }

        val isImageAndArtisticStyle = isArtisticStyle && !visualMedium.equals("Video", ignoreCase = true)

        if (isImageAndArtisticStyle) {
            android.util.Log.i("ProjectRepository", "Artistic Style '$style' detected. Directly generating themed AI illustration via Pollinations AI...")
            val generatedUrl = try {
                geminiService.getBestMatchingImage(
                    visualPrompt = resolvedVisualPrompt,
                    style = style,
                    sceneNum = index,
                    customSearchQuery = queryTranslated,
                    aspectRatio = aspectRatio,
                    imageSource = "AI Generated",
                    customUnsplashKey = savedUnsplash,
                    resolution = resolution
                )
            } catch (e: Exception) {
                android.util.Log.e("ProjectRepository", "Error generating themed illustration for artistic style", e)
                ""
            }
            if (generatedUrl.isNotEmpty()) {
                saveCachedImage(queryClean, generatedUrl, style, visualMedium, visualPrompt = resolvedVisualPrompt, aspectRatio = aspectRatio)
                saveCachedImage(queryTranslated, generatedUrl, style, visualMedium, visualPrompt = resolvedVisualPrompt, aspectRatio = aspectRatio)
                return@withContext VisualAssetResolution(generatedUrl, "AI Generated", true, listOf("AI Generated"))
            }
        }

        // Retrieve Spoonacular API key
        val savedSpoonacular = try {
            preferencesManager.spoonacularApiKeyFlow.first()
        } catch (e: Exception) {
            ""
        }

        // If recipe/food related query, try high-quality Spoonacular API first with robust fallback
        val isFoodQuery = queryClean.contains("recipe") || queryClean.contains("cook") || queryClean.contains("food") || 
                queryClean.contains("dish") || queryClean.contains("bake") || queryClean.contains("fry") || 
                queryClean.contains("kitchen") || queryClean.contains("ingredient") || queryClean.contains("meal") || 
                queryClean.contains("salad") || queryClean.contains("pizza") || queryClean.contains("curry") || 
                queryClean.contains("soup") || queryClean.contains("paneer") || queryClean.contains("vegetable") || 
                queryClean.contains("chicken") || queryClean.contains("chef") || queryClean.contains("taste") || 
                queryClean.contains("delicious") || queryClean.contains("spice") || queryClean.contains("roast") || 
                queryClean.contains("boil") || queryClean.contains("rice") || queryClean.contains("biryani") || 
                queryClean.contains("cake") || queryClean.contains("dessert") || queryClean.contains("breakfast")

        val isVisualPromptFood = visualPrompt.lowercase().contains("recipe") || visualPrompt.lowercase().contains("cook") || 
                visualPrompt.lowercase().contains("food") || visualPrompt.lowercase().contains("dish") || 
                visualPrompt.lowercase().contains("bake") || visualPrompt.lowercase().contains("fry") || 
                visualPrompt.lowercase().contains("kitchen") || visualPrompt.lowercase().contains("ingredient") || 
                visualPrompt.lowercase().contains("meal") || visualPrompt.lowercase().contains("salad") || 
                visualPrompt.lowercase().contains("pizza") || visualPrompt.lowercase().contains("curry") || 
                visualPrompt.lowercase().contains("soup") || visualPrompt.lowercase().contains("paneer") || 
                visualPrompt.lowercase().contains("vegetable") || visualPrompt.lowercase().contains("chicken") || 
                visualPrompt.lowercase().contains("chef") || visualPrompt.lowercase().contains("taste") || 
                visualPrompt.lowercase().contains("delicious") || visualPrompt.lowercase().contains("spice") || 
                visualPrompt.lowercase().contains("roast") || visualPrompt.lowercase().contains("culinary")

        val isFoodContent = isFoodQuery || isVisualPromptFood

        if (isFoodContent) {
            // 1. Try Spoonacular if key is provided
            if (savedSpoonacular.isNotEmpty() && MediaProviderHealthMonitor.isProviderHealthy("spoonacular")) {
                android.util.Log.i("ProjectRepository", "Food/Recipe context detected. Querying Spoonacular API for '$queryClean'...")
                try {
                    val searchVideo = visualMedium.equals("Video", ignoreCase = true)
                    var spoonacularUrl = geminiService.searchSpoonacularApi(queryClean, savedSpoonacular, searchVideo, index)
                    if (spoonacularUrl.isEmpty() && queryTranslated.isNotEmpty() && queryTranslated != queryClean) {
                        spoonacularUrl = geminiService.searchSpoonacularApi(queryTranslated, savedSpoonacular, searchVideo, index)
                    }
                    
                    if (spoonacularUrl.isNotEmpty()) {
                        android.util.Log.d("ProjectRepository", "Spoonacular API successfully resolved food asset: $spoonacularUrl")
                        MediaProviderHealthMonitor.recordSuccess("spoonacular")
                        saveCachedImage(queryClean, spoonacularUrl, style, visualMedium, visualPrompt = resolvedVisualPrompt, aspectRatio = aspectRatio)
                        saveCachedImage(queryTranslated, spoonacularUrl, style, visualMedium, visualPrompt = resolvedVisualPrompt, aspectRatio = aspectRatio)
                        return@withContext VisualAssetResolution(spoonacularUrl, "Spoonacular", true, listOf("Spoonacular"))
                    } else {
                        android.util.Log.d("ProjectRepository", "Spoonacular API returned empty result for query.")
                    }
                } catch (e: Exception) {
                    android.util.Log.w("ProjectRepository", "Spoonacular API request failed: ${e.message}")
                    MediaProviderHealthMonitor.recordFailure("spoonacular")
                }
            }

            // 2. Query Dedicated Free Recipe & Food DB APIs (TheMealDB & Foodish)
            if (!visualMedium.equals("Video", ignoreCase = true)) {
                try {
                    val mealDbUrl = geminiService.searchTheMealDbApi(queryClean, index)
                    if (mealDbUrl.isNotEmpty()) {
                        android.util.Log.d("ProjectRepository", "TheMealDB API successfully resolved recipe photo: $mealDbUrl")
                        saveCachedImage(queryClean, mealDbUrl, style, visualMedium, visualPrompt = resolvedVisualPrompt, aspectRatio = aspectRatio)
                        return@withContext VisualAssetResolution(mealDbUrl, "TheMealDB", true, listOf("TheMealDB"))
                    }

                    val foodishUrl = geminiService.searchFoodishApi(queryClean, index)
                    if (foodishUrl.isNotEmpty()) {
                        android.util.Log.d("ProjectRepository", "Foodish API successfully resolved food photo: $foodishUrl")
                        saveCachedImage(queryClean, foodishUrl, style, visualMedium, visualPrompt = resolvedVisualPrompt, aspectRatio = aspectRatio)
                        return@withContext VisualAssetResolution(foodishUrl, "Foodish", true, listOf("Foodish"))
                    }
                } catch (e: Exception) {
                    android.util.Log.e("ProjectRepository", "Dedicated Food DB API search failed", e)
                }
            }
        }

        val savedPexels = try {
            preferencesManager.pexelsApiKeyFlow.first()
        } catch (e: Exception) {
            ""
        }
        val savedPixabay = try {
            preferencesManager.pixabayApiKeyFlow.first()
        } catch (e: Exception) {
            ""
        }

        val buildPexels = try {
            com.ritvyom.yashoraReelgenerator.BuildConfig.PEXELS_API_KEY
        } catch (e: Exception) {
            ""
        }
        val buildPixabay = try {
            com.ritvyom.yashoraReelgenerator.BuildConfig.PIXABAY_API_KEY
        } catch (e: Exception) {
            ""
        }

        val rawPexelsKey = if (!savedPexels.isNullOrEmpty()) {
            savedPexels
        } else if (!buildPexels.isNullOrEmpty() && buildPexels != "YOUR_PEXELS_API_KEY") {
            buildPexels
        } else {
            "NyEXcmIT8bs3CN4dLNForDzJQVyVrWCMRfDLjfgtKYX9t3z8dUm1QHNl"
        }
        val pexelsKey = if (!savedPexels.isNullOrEmpty()) savedPexels.trim() else ApiLoadBalancerService.resolvePexelsApiKey(rawPexelsKey)

        val rawPixabayKey = if (!savedPixabay.isNullOrEmpty()) {
            savedPixabay
        } else if (!buildPixabay.isNullOrEmpty() && buildPixabay != "YOUR_PIXABAY_API_KEY" && buildPixabay != "YOUR_PIYABAY_API_KEY") {
            buildPixabay
        } else {
            "56218545-6d93003a94318db8a7f29dba1"
        }
        val pixabayKey = if (!savedPixabay.isNullOrEmpty()) savedPixabay.trim() else ApiLoadBalancerService.resolvePixabayApiKey(rawPixabayKey)

        var resolvedUrl = ""
        
        val queryKeywords = extractKeywordsFromSentence(queryTranslated)
        
        // Define style-enhanced queries for stock libraries
        val baseQueryStock = if (isArtisticStyle) {
            val styleLower = style.lowercase().trim()
            val styleKeyword = when (styleLower) {
                "cartoon" -> "cartoon animation"
                "anime" -> "anime illustration"
                "3d animation", "pixar style" -> "3d animation"
                "sci-fi" -> "sci-fi futuristic animation"
                "cyberpunk" -> "cyberpunk neon animation"
                "fantasy" -> "fantasy magical animation"
                "sketch art" -> "pencil sketch illustration"
                else -> "$styleLower style"
            }
            if (visualMedium.equals("Video", ignoreCase = true)) {
                "$queryKeywords $styleKeyword"
            } else {
                "$queryKeywords $style style"
            }
        } else {
            queryKeywords
        }

        // Use clean baseQueryStock directly for accurate stock API indexing (Pexels/Pixabay match best on pure subject tags)
        val queryForStock = baseQueryStock.trim()

        // Determine priority query engines to reduce latency
        val engines = mutableListOf<String>()
        if (visualMedium.equals("Video", ignoreCase = true)) {
            val preferredEngine = resolvedImageSource.lowercase().trim()
            if (preferredEngine == "pixabay") {
                engines.add("pixabay")
                engines.add("pexels")
            } else {
                engines.add("pexels")
                engines.add("pixabay")
            }
            if (isArtisticStyle) {
                // For stylized videos (cartoon/anime), use Pollinations AI first as backup instead of photorealistic Unsplash
                engines.add("pollinations")
                engines.add("unsplash")
            } else {
                engines.add("unsplash")
                engines.add("pollinations")
            }
        } else {
            val preferredEngine = resolvedImageSource.lowercase().trim()
            if (preferredEngine == "unsplash") {
                engines.add("unsplash")
                engines.add("pexels")
                engines.add("pixabay")
            } else if (preferredEngine == "pixabay") {
                engines.add("pixabay")
                engines.add("pexels")
                engines.add("unsplash")
            } else {
                engines.add("pexels")
                engines.add("pixabay")
                engines.add("unsplash")
            }
            if (isArtisticStyle) {
                // Prioritize Pollinations for image artistic styles
                engines.add(0, "pollinations")
            } else {
                engines.add("pollinations")
            }
        }

        // Sequence engines by user choice to prevent timeout delays
        val sortedEngines = MediaProviderHealthMonitor.filterAndSortEngines(engines)
        val attemptedSources = mutableListOf<String>()

        for (engine in sortedEngines) {
            val sourceTitle = when (engine) {
                "pexels" -> "Pexels"
                "pixabay" -> "Pixabay"
                "unsplash" -> "Unsplash"
                "pollinations" -> "AI Generated"
                else -> MediaFetchingStatusMonitor.normalizeSourceName(engine)
            }
            if (!attemptedSources.contains(sourceTitle)) {
                attemptedSources.add(sourceTitle)
            }

            try {
                when (engine) {
                    "pexels" -> {
                        if (visualMedium.equals("Video", ignoreCase = true)) {
                            android.util.Log.d("ProjectRepository", "Searching Pexels Video: $queryForStock")
                            resolvedUrl = geminiService.searchPexelsApi(queryForStock, pexelsKey, searchVideo = true, aspectRatio, index, alreadyUsedUrls = alreadyUsedUrls)
                            if (resolvedUrl.isEmpty() && queryForStock != queryKeywords) {
                                if (isArtisticStyle) {
                                    android.util.Log.d("ProjectRepository", "Pexels video empty for artistic style. Bypassing realistic fallback to allow Pollinations backup.")
                                } else {
                                    resolvedUrl = geminiService.searchPexelsApi(queryKeywords, pexelsKey, searchVideo = true, aspectRatio, index, alreadyUsedUrls = alreadyUsedUrls)
                                }
                            }
                        } else {
                            android.util.Log.d("ProjectRepository", "Searching Pexels Photo: $queryForStock")
                            resolvedUrl = geminiService.searchPexelsApi(queryForStock, pexelsKey, searchVideo = false, aspectRatio, index, alreadyUsedUrls = alreadyUsedUrls)
                            if (resolvedUrl.isEmpty() && queryForStock != queryKeywords) {
                                resolvedUrl = geminiService.searchPexelsApi(queryKeywords, pexelsKey, searchVideo = false, aspectRatio, index, alreadyUsedUrls = alreadyUsedUrls)
                            }
                        }
                    }
                    "pixabay" -> {
                        if (visualMedium.equals("Video", ignoreCase = true)) {
                            android.util.Log.d("ProjectRepository", "Searching Pixabay Video: $queryForStock")
                            resolvedUrl = geminiService.searchPixabayVideos(queryForStock, pixabayKey, aspectRatio, index, alreadyUsedUrls = alreadyUsedUrls)
                            if (resolvedUrl.isEmpty() && queryForStock != queryKeywords) {
                                if (isArtisticStyle) {
                                    android.util.Log.d("ProjectRepository", "Pixabay video empty for artistic style. Bypassing realistic fallback to allow Pollinations backup.")
                                } else {
                                    resolvedUrl = geminiService.searchPixabayVideos(queryKeywords, pixabayKey, aspectRatio, index, alreadyUsedUrls = alreadyUsedUrls)
                                }
                            }
                        } else {
                            android.util.Log.d("ProjectRepository", "Searching Pixabay Photo: $queryForStock")
                            resolvedUrl = geminiService.searchPixabayApi(queryForStock, pixabayKey, aspectRatio, index, alreadyUsedUrls = alreadyUsedUrls)
                            if (resolvedUrl.isEmpty() && queryForStock != queryKeywords) {
                                resolvedUrl = geminiService.searchPixabayApi(queryKeywords, pixabayKey, aspectRatio, index, alreadyUsedUrls = alreadyUsedUrls)
                            }
                        }
                    }
                    "unsplash" -> {
                        android.util.Log.d("ProjectRepository", "Searching Unsplash Photo: $queryForStock")
                        resolvedUrl = geminiService.getBestMatchingImage(
                            visualPrompt = queryTranslated,
                            style = style,
                            sceneNum = index,
                            customSearchQuery = queryForStock,
                            aspectRatio = aspectRatio,
                            imageSource = "Unsplash",
                            customUnsplashKey = savedUnsplash,
                            resolution = resolution,
                            alreadyUsedUrls = alreadyUsedUrls
                        )
                    }
                    "pollinations" -> {
                        android.util.Log.d("ProjectRepository", "Pollinations AI backup generation: $queryTranslated")
                        resolvedUrl = geminiService.getBestMatchingImage(
                            visualPrompt = queryTranslated,
                            style = style,
                            sceneNum = index,
                            customSearchQuery = queryTranslated,
                            aspectRatio = aspectRatio,
                            imageSource = "AI Generated",
                            customUnsplashKey = savedUnsplash,
                            resolution = resolution,
                            alreadyUsedUrls = alreadyUsedUrls
                        )
                    }
                }

                if (resolvedUrl.isNotEmpty()) {
                    if (isRepetitiveOrLowQuality(resolvedUrl, queryClean, alreadyUsedUrls, visualMedium)) {
                        android.util.Log.w("ProjectRepository", "Engine '$engine' returned a repetitive or low-quality URL: $resolvedUrl. Discarding and failing over to next engine.")
                        resolvedUrl = ""
                    } else {
                        android.util.Log.d("ProjectRepository", "Engine '$engine' successfully returned URL: $resolvedUrl")
                        MediaProviderHealthMonitor.recordSuccess(engine)
                        saveCachedImage(queryClean, resolvedUrl, style, visualMedium, visualPrompt = resolvedVisualPrompt, aspectRatio = aspectRatio)
                        saveCachedImage(queryTranslated, resolvedUrl, style, visualMedium, visualPrompt = resolvedVisualPrompt, aspectRatio = aspectRatio)
                        return@withContext VisualAssetResolution(
                            url = resolvedUrl,
                            sourceUsed = sourceTitle,
                            isSuccess = true,
                            attemptedSources = attemptedSources
                        )
                    }
                } else {
                    android.util.Log.d("ProjectRepository", "Engine '$engine' returned empty URL for query: $queryForStock. Proceeding to fallback engine.")
                }
            } catch (e: Exception) {
                android.util.Log.w("ProjectRepository", "Exception on '$engine' engine process: ${e.message}")
                MediaProviderHealthMonitor.recordFailure(engine)
            }
        }

        // Automatic relevant scene fallback if stock provider queries failed or returned no results
        val sceneFallback = if (visualMedium.equals("Video", ignoreCase = true)) {
            geminiService.getThematicSceneFallbackVideo(queryTranslated.ifEmpty { queryClean }, index)
        } else {
            geminiService.getThematicSceneFallbackImage(queryTranslated.ifEmpty { queryClean }, index, aspectRatio)
        }

        if (sceneFallback.isNotEmpty()) {
            android.util.Log.i("ProjectRepository", "Stock queries returned no results. Automatically resolved scene fallback asset: $sceneFallback")
            saveCachedImage(queryClean, sceneFallback, style, visualMedium, visualPrompt = resolvedVisualPrompt, aspectRatio = aspectRatio)
            saveCachedImage(queryTranslated, sceneFallback, style, visualMedium, visualPrompt = resolvedVisualPrompt, aspectRatio = aspectRatio)
            return@withContext VisualAssetResolution(
                url = sceneFallback,
                sourceUsed = "Relevant Fallback",
                isSuccess = true,
                attemptedSources = attemptedSources + "Relevant Fallback"
            )
        }

        VisualAssetResolution(
            url = "",
            sourceUsed = "",
            isSuccess = false,
            attemptedSources = attemptedSources.ifEmpty { listOf("Pexels", "Pixabay", "Unsplash") },
            errorMessage = "All attempted stock media providers (${attemptedSources.joinToString(", ")}) returned no matching footage."
        )
    }

    suspend fun generateStoryboardsFromScript(
        script: String,
        style: String,
        videoLang: String,
        aspectRatio: String = "9:16",
        imageSource: String = "Unsplash",
        visualMedium: String = "Video",
        resolution: String? = null,
        topicContext: String = "",
        publishingStyle: String = "TikTok / Instagram Reels",
        onProgress: ((current: Int, total: Int, status: String) -> Unit)? = null
    ): List<com.ritvyom.yashoraReelgenerator.domain.models.Scene> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val savedGemini = try {
            preferencesManager.geminiApiKeyFlow.first()
        } catch (e: Exception) {
            ""
        }
        val savedUnsplash = try {
            preferencesManager.unsplashApiKeyFlow.first()
        } catch (e: Exception) {
            ""
        }
        onProgress?.invoke(0, 0, "Analyzing script...")
        val rawScenes = geminiService.analyzeScript(
            script = script,
            style = style,
            language = videoLang,
            aspectRatio = aspectRatio,
            imageSource = imageSource,
            customGeminiKey = savedGemini,
            customUnsplashKey = savedUnsplash,
            topicContext = topicContext,
            publishingStyle = publishingStyle
        )
        onProgress?.invoke(0, rawScenes.size, "Breaking script into ${rawScenes.size} scene storyboards...")
        
        val alreadyUsedUrls = mutableSetOf<String>()
        val matchedScenes = rawScenes.mapIndexed { index, scene ->
            onProgress?.invoke(index + 1, rawScenes.size, "Fetching visuals for scene ${index + 1} of ${rawScenes.size}...")
            var resolvedPath: String? = null
            
            // Sanitize scene keywords to ensure they directly depict voiceover and prevent generic placeholders
            val genericKeywords = setOf("cinematic narrative scene", "cinematic visual", "cinematic tag", "cinematic", "narrative", "scene")
            val sanitizedKeywords = scene.keywords.filter { kw ->
                kw.trim().lowercase() !in genericKeywords && kw.trim().isNotEmpty()
            }
            val effectiveKeywords = if (sanitizedKeywords.isNotEmpty()) {
                sanitizedKeywords
            } else {
                val extracted = geminiService.extractSemiSemanticKeyword(
                    scene.narrationText.ifEmpty { scene.subtitle.ifEmpty { scene.visualPrompt } }
                )
                val parts = extracted.split(Regex("\\s+")).filter { it.length > 2 }
                if (parts.isNotEmpty()) parts else listOf(extracted)
            }
            scene.keywords = effectiveKeywords
            
            // A. Search local cached image library first using keywords with appropriate style & medium
            for (keyword in scene.keywords) {
                if (keyword.isNotEmpty()) {
                    val cached = getCachedImage(keyword, style, visualMedium, visualPrompt = scene.visualPrompt)
                    if (cached != null && cached.isNotEmpty()) {
                        if (isRepetitiveOrLowQuality(cached, keyword, alreadyUsedUrls, visualMedium)) {
                            android.util.Log.i("ProjectRepository", "Local cache HIT for keyword '$keyword' is repetitive or low-quality. Discarding cache.")
                            deleteCachedImage(keyword, style, visualMedium, scene.visualPrompt)
                        } else {
                            resolvedPath = cached
                            alreadyUsedUrls.add(cached)
                            android.util.Log.d("ProjectRepository", "Cache HIT for keyword '$keyword': $resolvedPath")
                            break
                        }
                    }
                }
            }
            
            // Fallback: search by visualPrompt if keywords failed
            if (resolvedPath == null && scene.visualPrompt.isNotEmpty()) {
                val cached = getCachedImage(scene.visualPrompt, style, visualMedium, visualPrompt = scene.visualPrompt)
                if (cached != null && cached.isNotEmpty()) {
                    if (isRepetitiveOrLowQuality(cached, scene.visualPrompt, alreadyUsedUrls, visualMedium)) {
                        android.util.Log.i("ProjectRepository", "Local cache HIT for visualPrompt is repetitive or low-quality. Discarding cache.")
                        deleteCachedImage(scene.visualPrompt, style, visualMedium, scene.visualPrompt)
                    } else {
                        resolvedPath = cached
                        alreadyUsedUrls.add(cached)
                        android.util.Log.d("ProjectRepository", "Cache HIT for visualPrompt: $resolvedPath")
                    }
                }
            }
            
            if (resolvedPath != null) {
                val remoteUrl = if (resolvedPath.startsWith("http")) resolvedPath else scene.remoteUrl
                MediaFetchingStatusMonitor.recordSuccess(
                    sceneNumber = scene.sceneNumber,
                    visualPrompt = scene.visualPrompt,
                    sourceUsed = "Cache",
                    mediaUrl = resolvedPath,
                    attemptedSources = listOf("Cache")
                )
                scene.copy(
                    mediaPath = resolvedPath,
                    remoteUrl = remoteUrl,
                    mediaSourceUsed = "Cache",
                    mediaFetchStatus = "SUCCESS",
                    mediaFetchError = null
                )
            } else {
                MediaFetchingStatusMonitor.recordInProgress(
                    sceneNumber = scene.sceneNumber,
                    visualPrompt = scene.visualPrompt
                )
                var resolvedRes: VisualAssetResolution? = null
                val allAttempted = mutableListOf<String>()

                // Fetch using our integrated search engines
                for (keyword in scene.keywords) {
                    if (keyword.isNotEmpty()) {
                        val res = resolveVisualAssetResolution(
                            query = keyword,
                            style = style,
                            aspectRatio = aspectRatio,
                            index = index,
                            visualMedium = visualMedium,
                            imageSource = imageSource,
                            resolution = resolution,
                            visualPrompt = scene.visualPrompt,
                            publishingStyle = publishingStyle,
                            alreadyUsedUrls = alreadyUsedUrls
                        )
                        res.attemptedSources.forEach { if (!allAttempted.contains(it)) allAttempted.add(it) }
                        if (res.isSuccess && res.url.isNotEmpty()) {
                            resolvedRes = res
                            alreadyUsedUrls.add(res.url)
                            break
                        }
                    }
                }
                
                if (resolvedRes == null && scene.visualPrompt.isNotEmpty()) {
                    val res = resolveVisualAssetResolution(
                        query = scene.visualPrompt,
                        style = style,
                        aspectRatio = aspectRatio,
                        index = index,
                        visualMedium = visualMedium,
                        imageSource = imageSource,
                        resolution = resolution,
                        visualPrompt = scene.visualPrompt,
                        publishingStyle = publishingStyle,
                        alreadyUsedUrls = alreadyUsedUrls
                    )
                    res.attemptedSources.forEach { if (!allAttempted.contains(it)) allAttempted.add(it) }
                    if (res.isSuccess && res.url.isNotEmpty()) {
                        resolvedRes = res
                        alreadyUsedUrls.add(res.url)
                    }
                }
                
                if (resolvedRes == null) {
                    val wikiUrl = geminiService.searchWikipediaEntityImage(scene.visualPrompt, index)
                    if (wikiUrl.isNotEmpty()) {
                        alreadyUsedUrls.add(wikiUrl)
                        if (!allAttempted.contains("Wikipedia")) allAttempted.add("Wikipedia")
                        resolvedRes = VisualAssetResolution(
                            url = wikiUrl,
                            sourceUsed = "Wikipedia",
                            isSuccess = true,
                            attemptedSources = allAttempted.toList()
                        )
                    }
                }

                if (resolvedRes == null) {
                    val aiUrl = geminiService.getBestMatchingImage(
                        scene.visualPrompt,
                        style,
                        index,
                        customSearchQuery = scene.visualPrompt,
                        aspectRatio = aspectRatio,
                        imageSource = "AI Generated",
                        resolution = resolution,
                        visualMedium = visualMedium,
                        alreadyUsedUrls = alreadyUsedUrls
                    )
                    if (aiUrl.isNotEmpty()) {
                        alreadyUsedUrls.add(aiUrl)
                        if (!allAttempted.contains("AI Generated")) allAttempted.add("AI Generated")
                        resolvedRes = VisualAssetResolution(
                            url = aiUrl,
                            sourceUsed = "AI Generated",
                            isSuccess = true,
                            attemptedSources = allAttempted.toList()
                        )
                    }
                }

                if (resolvedRes == null && !scene.mediaPath.isNullOrEmpty()) {
                    val existingPath = scene.mediaPath!!
                    alreadyUsedUrls.add(existingPath)
                    resolvedRes = VisualAssetResolution(
                        url = existingPath,
                        sourceUsed = "Existing Media",
                        isSuccess = true,
                        attemptedSources = allAttempted.toList()
                    )
                }

                if (resolvedRes != null && resolvedRes.isSuccess && resolvedRes.url.isNotEmpty()) {
                    MediaFetchingStatusMonitor.recordSuccess(
                        sceneNumber = scene.sceneNumber,
                        visualPrompt = scene.visualPrompt,
                        sourceUsed = resolvedRes.sourceUsed,
                        mediaUrl = resolvedRes.url,
                        attemptedSources = resolvedRes.attemptedSources
                    )
                    scene.copy(
                        mediaPath = resolvedRes.url,
                        remoteUrl = resolvedRes.url,
                        mediaSourceUsed = resolvedRes.sourceUsed,
                        mediaFetchStatus = "SUCCESS",
                        mediaFetchError = null
                    )
                } else {
                    val finalAttempted = if (allAttempted.isNotEmpty()) allAttempted else listOf("Pexels", "Pixabay", "Unsplash")
                    val errorMsg = "All providers (${finalAttempted.joinToString(", ")}) failed."
                    MediaFetchingStatusMonitor.recordFailure(
                        sceneNumber = scene.sceneNumber,
                        visualPrompt = scene.visualPrompt,
                        attemptedSources = finalAttempted,
                        error = errorMsg
                    )

                    val fallbackLocal = com.ritvyom.yashoraReelgenerator.presentation.utils.LocalAssetFallbackManager.getCuratedFallbackAsset(
                        context = context,
                        sceneIndex = index,
                        style = style,
                        aspectRatio = aspectRatio
                    )
                    val fallbackUrl = if (fallbackLocal.isNotEmpty()) fallbackLocal else {
                        val seed = kotlin.math.abs(scene.visualPrompt.hashCode() + index)
                        val width = if (aspectRatio == "16:9") 1280 else 720
                        val height = if (aspectRatio == "16:9") 720 else 1280
                        "https://picsum.photos/seed/$seed/$width/$height"
                    }
                    alreadyUsedUrls.add(fallbackUrl)

                    scene.copy(
                        mediaPath = fallbackUrl,
                        remoteUrl = fallbackUrl,
                        mediaSourceUsed = "None (Failed)",
                        mediaFetchStatus = "FAILED",
                        mediaFetchError = errorMsg
                    )
                }
            }
        }

        // Caching assets locally for offline robustness, utilizing sequential downloads to prevent network congestion/rate limits
        matchedScenes.mapIndexed { idx, scene ->
            android.util.Log.d("ProjectRepository", "Downloading local media for scene ${idx + 1}/${matchedScenes.size}: ${scene.mediaPath}")
            val remoteUrlToPreserve = if (!scene.remoteUrl.isNullOrEmpty() && scene.remoteUrl!!.startsWith("http")) {
                scene.remoteUrl
            } else if (!scene.mediaPath.isNullOrEmpty() && scene.mediaPath!!.startsWith("http")) {
                scene.mediaPath
            } else null

            val localPath = downloadMediaToLocal(scene.mediaPath ?: "")
            if (localPath.isNotEmpty()) {
                scene.copy(mediaPath = localPath, remoteUrl = remoteUrlToPreserve ?: scene.remoteUrl)
            } else {
                scene.copy(remoteUrl = remoteUrlToPreserve ?: scene.remoteUrl)
            }
        }
    }

    suspend fun fallbackLocalParser(script: String, style: String, language: String = "English", aspectRatio: String = "9:16", imageSource: String = "Unsplash"): List<Scene> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val scenes = geminiService.fallbackLocalParser(script, style, language, aspectRatio, imageSource)
        
        // Caching assets locally for offline robustness, utilizing sequential downloads to prevent network congestion/rate limits
        scenes.mapIndexed { idx, scene ->
            android.util.Log.d("ProjectRepository", "Downloading local media for fallback scene ${idx + 1}/${scenes.size}: ${scene.mediaPath}")
            val remoteUrlToPreserve = if (!scene.remoteUrl.isNullOrEmpty() && scene.remoteUrl!!.startsWith("http")) {
                scene.remoteUrl
            } else if (!scene.mediaPath.isNullOrEmpty() && scene.mediaPath!!.startsWith("http")) {
                scene.mediaPath
            } else null

            val localPath = downloadMediaToLocal(scene.mediaPath ?: "")
            if (localPath.isNotEmpty()) {
                scene.copy(mediaPath = localPath, remoteUrl = remoteUrlToPreserve ?: scene.remoteUrl)
            } else {
                scene.copy(remoteUrl = remoteUrlToPreserve ?: scene.remoteUrl)
            }
        }
    }

    suspend fun getBestMatchingImage(
        visualPrompt: String,
        style: String,
        sceneNum: Int,
        customSearchQuery: String = "",
        aspectRatio: String = "9:16",
        imageSource: String = "Unsplash",
        visualMedium: String = "Video"
    ): String {
        val queryForCache = if (customSearchQuery.isNotEmpty()) customSearchQuery else visualPrompt
        return resolveVisualAsset(queryForCache, style, aspectRatio, sceneNum, visualMedium, imageSource = imageSource)
    }

    suspend fun getCachedCommunityVideo(url: String): CommunityVideoCacheEntity? =
        communityVideoCacheDao.getByMediaUrl(url)

    suspend fun getCachedVideosForProject(projectId: String): List<CommunityVideoCacheEntity> =
        communityVideoCacheDao.getVideosForProject(projectId)

    suspend fun persistCommunityVideoMetadata(entity: CommunityVideoCacheEntity): Long =
        communityVideoCacheDao.insert(entity)

    suspend fun deleteCommunityVideoCache(url: String): Int =
        communityVideoCacheDao.deleteByMediaUrl(url)

    suspend fun clearAllCommunityVideoCache() =
        communityVideoCacheDao.clearAll()

    suspend fun downloadMediaToLocal(
        urlStr: String,
        projectId: String = "",
        projectTitle: String = "",
        sceneNumber: Int = 0,
        narrationText: String = "",
        visualPrompt: String = "",
        durationSeconds: Int = 5,
        aspectRatio: String = "9:16"
    ): String = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val targetUrl = if (urlStr.isBlank()) {
            "https://picsum.photos/seed/${System.currentTimeMillis()}/720/1280"
        } else urlStr

        // 0. Check Room Database Community Video Cache First
        try {
            val cachedEntry = communityVideoCacheDao.getByMediaUrl(targetUrl)
            if (cachedEntry != null) {
                val localFile = File(cachedEntry.localFilePath)
                if (localFile.exists() && localFile.length() > 0) {
                    android.util.Log.i("ProjectRepository", "Room Video Cache HIT for '$targetUrl' -> '${cachedEntry.localFilePath}' (${localFile.length()} bytes)")
                    communityVideoCacheDao.updateLastAccessed(targetUrl, System.currentTimeMillis())
                    return@withContext cachedEntry.localFilePath
                }
            }
        } catch (e: Exception) {
            android.util.Log.w("ProjectRepository", "Failed querying Room community video cache", e)
        }

        // 1. Check if the video/image file already exists in local app storage before starting any download
        val existingLocal = findExistingLocalMediaFile(context, targetUrl)
        if (existingLocal != null && existingLocal.exists() && existingLocal.length() > 0) {
            android.util.Log.d("ProjectRepository", "Local file found in storage for '$targetUrl', skipping download: ${existingLocal.absolutePath} (${existingLocal.length()} bytes)")
            try {
                val isVid = targetUrl.contains(".mp4", ignoreCase = true) || targetUrl.contains("video", ignoreCase = true) || existingLocal.name.endsWith(".mp4", ignoreCase = true)
                communityVideoCacheDao.insert(
                    CommunityVideoCacheEntity(
                        mediaUrl = targetUrl,
                        localFilePath = existingLocal.absolutePath,
                        projectId = projectId,
                        projectTitle = projectTitle,
                        sceneNumber = sceneNumber,
                        narrationText = narrationText,
                        visualPrompt = visualPrompt,
                        durationSeconds = durationSeconds,
                        mimeType = if (isVid) "video/mp4" else "image/jpeg",
                        isVideo = isVid,
                        fileSizeBytes = existingLocal.length(),
                        aspectRatio = aspectRatio,
                        cachedAt = System.currentTimeMillis(),
                        lastAccessedAt = System.currentTimeMillis()
                    )
                )
            } catch (e: Exception) {
                android.util.Log.w("ProjectRepository", "Failed saving existing file to Room video cache", e)
            }
            return@withContext existingLocal.absolutePath
        }

        if (targetUrl.startsWith("/") || targetUrl.startsWith("file:") || targetUrl.startsWith("content:")) {
            val cleanPath = targetUrl.removePrefix("file://")
            if (File(cleanPath).exists() && File(cleanPath).length() > 0) {
                return@withContext targetUrl
            } else {
                android.util.Log.w("ProjectRepository", "Local path '$targetUrl' does not exist on this device! Returning empty string.")
                return@withContext ""
            }
        }
        val safeUrlStr = if (targetUrl.startsWith("http://")) targetUrl.replace("http://", "https://") else targetUrl
        try {
            val md5Hex = md5(safeUrlStr)
            
            // Primary readable directory (External Files sandbox) for error-free system MediaPlayer (VideoView) access
            val localDir = context.getExternalFilesDir("YashoraLocalMedia") ?: File(context.filesDir, "YashoraLocalMedia")
            if (!localDir.exists()) localDir.mkdirs()

            // Legacy folder for backward compatibility
            val legacyDir = File(context.filesDir, "YashoraLocalMedia")

            // Smart cross-extension caching check: some URLs return video content-type but URL extension is missing/diff
            val existingMp4 = File(localDir, "$md5Hex.mp4")
            val existingJpg = File(localDir, "$md5Hex.jpg")
            val legacyMp4 = File(legacyDir, "$md5Hex.mp4")
            val legacyJpg = File(legacyDir, "$md5Hex.jpg")

            if (existingMp4.exists()) {
                if (existingMp4.length() < 10000) {
                    android.util.Log.w("ProjectRepository", "Existing local mp4 is too small (${existingMp4.length()} bytes). Deleting.")
                    existingMp4.delete()
                } else {
                    persistToRoomCache(safeUrlStr, existingMp4.absolutePath, projectId, projectTitle, sceneNumber, narrationText, visualPrompt, durationSeconds, aspectRatio, isVideo = true, sizeBytes = existingMp4.length())
                    return@withContext existingMp4.absolutePath
                }
            }
            if (existingJpg.exists()) {
                if (existingJpg.length() < 10000) {
                    android.util.Log.w("ProjectRepository", "Existing local jpg is too small (${existingJpg.length()} bytes). Deleting.")
                    existingJpg.delete()
                } else {
                    persistToRoomCache(safeUrlStr, existingJpg.absolutePath, projectId, projectTitle, sceneNumber, narrationText, visualPrompt, durationSeconds, aspectRatio, isVideo = false, sizeBytes = existingJpg.length())
                    return@withContext existingJpg.absolutePath
                }
            }

            // Migrate from legacy folder if exists there but not in the new external folder
            if (!existingMp4.exists() && legacyMp4.exists() && legacyMp4.length() > 10000) {
                try {
                    legacyMp4.copyTo(existingMp4, overwrite = true)
                    persistToRoomCache(safeUrlStr, existingMp4.absolutePath, projectId, projectTitle, sceneNumber, narrationText, visualPrompt, durationSeconds, aspectRatio, isVideo = true, sizeBytes = existingMp4.length())
                    return@withContext existingMp4.absolutePath
                } catch (e: Exception) {}
            }
            if (!existingJpg.exists() && legacyJpg.exists() && legacyJpg.length() > 10000) {
                try {
                    legacyJpg.copyTo(existingJpg, overwrite = true)
                    persistToRoomCache(safeUrlStr, existingJpg.absolutePath, projectId, projectTitle, sceneNumber, narrationText, visualPrompt, durationSeconds, aspectRatio, isVideo = false, sizeBytes = existingJpg.length())
                    return@withContext existingJpg.absolutePath
                } catch (e: Exception) {}
            }

            val isVid = safeUrlStr.contains(".mp4", ignoreCase = true) || safeUrlStr.contains("video", ignoreCase = true)
            val extension = if (isVid) ".mp4" else ".jpg"
            val targetFile = File(localDir, "$md5Hex$extension")

            val userAgent = when {
                safeUrlStr.contains("nekos.best", ignoreCase = true) ->
                    "YashoraReelGenerator (Ritvyom@gmail.com)"
                safeUrlStr.contains("wikimedia.org", ignoreCase = true) || safeUrlStr.contains("wikipedia.org", ignoreCase = true) ->
                    "YashoraReelGenerator/2.0 (https://ai.studio; yashoratechnologies@gmail.com) Android/14 OkHttp/4.12"
                else ->
                    "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
            }

            val request = Request.Builder()
                .url(safeUrlStr)
                .header("User-Agent", userAgent)
                .header("Accept", "*/*")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    android.util.Log.e("ProjectRepository", "Bad response ${response.code} for $safeUrlStr")
                    return@withContext safeUrlStr
                }

                val body = response.body ?: return@withContext safeUrlStr
                val contentType = body.contentType()?.toString() ?: ""
                val isActuallyVideo = isVid || contentType.contains("video", ignoreCase = true)
                val finalExtension = if (isActuallyVideo) ".mp4" else ".jpg"
                val finalTargetFile = File(localDir, "$md5Hex$finalExtension")
                val finalLegacyFile = File(legacyDir, "$md5Hex$finalExtension")

                // Same migration check for content type derived extension
                if (!finalTargetFile.exists() && finalLegacyFile.exists() && finalLegacyFile.length() > 10000) {
                    try {
                        finalLegacyFile.copyTo(finalTargetFile, overwrite = true)
                    } catch (e: Exception) {}
                }

                if (finalTargetFile.exists()) {
                    if (finalTargetFile.length() < 10000) {
                        android.util.Log.w("ProjectRepository", "Final target file is too small (${finalTargetFile.length()} bytes). Deleting.")
                        finalTargetFile.delete()
                    } else {
                        persistToRoomCache(safeUrlStr, finalTargetFile.absolutePath, projectId, projectTitle, sceneNumber, narrationText, visualPrompt, durationSeconds, aspectRatio, isVideo = isActuallyVideo, sizeBytes = finalTargetFile.length())
                        return@withContext finalTargetFile.absolutePath
                    }
                }

                val tempFile = File(context.cacheDir, "dl_$md5Hex.tmp")
                body.byteStream().use { input ->
                    java.io.FileOutputStream(tempFile).use { output ->
                        input.copyTo(output)
                    }
                }

                if (tempFile.exists()) {
                    if (tempFile.length() < 10000) {
                        android.util.Log.w("ProjectRepository", "Downloaded temp file is too small (${tempFile.length()} bytes). Deleting.")
                        tempFile.delete()
                        safeUrlStr
                    } else {
                        val savedPath = if (tempFile.renameTo(finalTargetFile)) {
                            finalTargetFile.absolutePath
                        } else {
                            try {
                                tempFile.copyTo(finalTargetFile, overwrite = true)
                                tempFile.delete()
                                if (finalTargetFile.length() < 10000) {
                                    finalTargetFile.delete()
                                    safeUrlStr
                                } else {
                                    finalTargetFile.absolutePath
                                }
                            } catch (ex: Exception) {
                                if (tempFile.length() < 10000) {
                                    tempFile.delete()
                                    safeUrlStr
                                } else {
                                    tempFile.absolutePath
                                }
                            }
                        }
                        if (savedPath.isNotEmpty() && !savedPath.startsWith("http")) {
                            val savedFile = File(savedPath)
                            persistToRoomCache(safeUrlStr, savedPath, projectId, projectTitle, sceneNumber, narrationText, visualPrompt, durationSeconds, aspectRatio, isVideo = isActuallyVideo, sizeBytes = savedFile.length())
                        }
                        savedPath
                    }
                } else {
                    safeUrlStr
                }
            }
        } catch (e: java.net.SocketTimeoutException) {
            android.util.Log.w("ProjectRepository", "Timeout downloading media URL $urlStr locally. Falling back to online URL.")
            safeUrlStr
        } catch (e: Exception) {
            android.util.Log.e("ProjectRepository", "Failed to cache media url locally: $urlStr (${e.message})")
            safeUrlStr
        }
    }

    private suspend fun persistToRoomCache(
        mediaUrl: String,
        localFilePath: String,
        projectId: String,
        projectTitle: String,
        sceneNumber: Int,
        narrationText: String,
        visualPrompt: String,
        durationSeconds: Int,
        aspectRatio: String,
        isVideo: Boolean,
        sizeBytes: Long
    ) {
        try {
            communityVideoCacheDao.insert(
                CommunityVideoCacheEntity(
                    mediaUrl = mediaUrl,
                    localFilePath = localFilePath,
                    projectId = projectId,
                    projectTitle = projectTitle,
                    sceneNumber = sceneNumber,
                    narrationText = narrationText,
                    visualPrompt = visualPrompt,
                    durationSeconds = durationSeconds,
                    mimeType = if (isVideo) "video/mp4" else "image/jpeg",
                    isVideo = isVideo,
                    fileSizeBytes = sizeBytes,
                    aspectRatio = aspectRatio,
                    cachedAt = System.currentTimeMillis(),
                    lastAccessedAt = System.currentTimeMillis()
                )
            )
            android.util.Log.d("ProjectRepository", "Successfully persisted video metadata into Room DB: $localFilePath ($sizeBytes bytes)")
        } catch (e: Exception) {
            android.util.Log.w("ProjectRepository", "Failed writing to Room CommunityVideoCacheDao", e)
        }
    }

    companion object {
        /**
         * Checks if a video or image file corresponding to the given URL or path already exists
         * in any local storage location (cache directory, internal files, external files sandbox).
         * If found and valid, returns the local File so network downloads can be completely bypassed.
         */
        fun findExistingLocalMediaFile(context: Context, urlOrPath: String): File? {
            if (urlOrPath.isBlank()) return null
            
            // 1. Direct file path check
            if (urlOrPath.startsWith("/") || urlOrPath.startsWith("file://") || urlOrPath.startsWith("content://")) {
                val clean = urlOrPath.removePrefix("file://")
                val f = File(clean)
                if (f.exists() && f.isFile && f.length() > 0) {
                    return f
                }
            }

            val safeUrl = if (urlOrPath.startsWith("http://")) urlOrPath.replace("http://", "https://") else urlOrPath
            val baseUrl = safeUrl.substringBefore('?')

            // Hashes
            val md5Full = computeMd5(safeUrl)
            val md5Base = computeMd5(baseUrl)
            val sha256Full = computeSha256(safeUrl)
            val sha256Base = computeSha256(baseUrl)

            // Extracted raw filename from URL (e.g. video_name.mp4 or encoded Firebase path)
            val rawName = try {
                java.net.URLDecoder.decode(baseUrl.substringAfterLast('/'), "UTF-8")
            } catch (e: Exception) {
                baseUrl.substringAfterLast('/')
            }

            val candidateDirectories = listOfNotNull(
                context.getExternalFilesDir("YashoraLocalMedia"),
                File(context.filesDir, "YashoraLocalMedia"),
                File(context.cacheDir, "YashoraMediaCache"),
                File(context.cacheDir, "YashoraLocalMedia"),
                context.cacheDir,
                context.externalCacheDir,
                File(context.filesDir, "videos"),
                File(context.filesDir, "media"),
                context.filesDir,
                context.getExternalFilesDir(null)
            )

            val candidateFileNames = mutableListOf<String>().apply {
                // MD5 variations
                add("$md5Full.mp4")
                add("$md5Full.jpg")
                add("$md5Full.jpeg")
                add("$md5Full.png")
                add("$md5Full.webp")
                add(md5Full)
                if (md5Base != md5Full) {
                    add("$md5Base.mp4")
                    add("$md5Base.jpg")
                    add("$md5Base.jpeg")
                    add("$md5Base.png")
                    add("$md5Base.webp")
                    add(md5Base)
                }
                // SHA-256 variations (ReelVideoCompiler format)
                add("media_$sha256Full.mp4")
                add("media_$sha256Full.jpg")
                if (sha256Base != sha256Full) {
                    add("media_$sha256Base.mp4")
                    add("media_$sha256Base.jpg")
                }
                // Temp files that finished writing
                add("dl_$md5Full.tmp")
                if (rawName.isNotBlank() && rawName.length > 3 && rawName.contains(".")) {
                    add(rawName)
                }
            }

            for (dir in candidateDirectories) {
                if (!dir.exists() || !dir.isDirectory) continue
                for (name in candidateFileNames) {
                    val candidateFile = File(dir, name)
                    if (candidateFile.exists() && candidateFile.isFile) {
                        val len = candidateFile.length()
                        val isVideo = name.endsWith(".mp4", ignoreCase = true) || safeUrl.contains("video", ignoreCase = true) || safeUrl.contains(".mp4", ignoreCase = true)
                        val minLength = if (isVideo) 8000L else 1000L
                        if (len >= minLength) {
                            return candidateFile
                        }
                    }
                }
            }
            return null
        }

        private fun computeMd5(s: String): String {
            return try {
                val digest = java.security.MessageDigest.getInstance("MD5")
                digest.update(s.toByteArray(Charsets.UTF_8))
                val messageDigest = digest.digest()
                val hexString = StringBuilder()
                for (b in messageDigest) {
                    var h = Integer.toHexString(0xFF and b.toInt())
                    while (h.length < 2) h = "0$h"
                    hexString.append(h)
                }
                hexString.toString()
            } catch (e: Exception) {
                s.hashCode().toString()
            }
        }

        private fun computeSha256(s: String): String {
            return try {
                val digest = java.security.MessageDigest.getInstance("SHA-256")
                val hash = digest.digest(s.toByteArray(Charsets.UTF_8))
                hash.joinToString("") { "%02x".format(it) }
            } catch (e: Exception) {
                s.hashCode().toString()
            }
        }
    }

    private fun md5(s: String): String = computeMd5(s)

    private fun isCelebrityQuery(query: String): Boolean {
        val lower = query.lowercase().trim()
        val celebs = listOf(
            "modi", "narendra", "yogi", "adityanath", "kejriwal", "gandhi", "amit shah", "rahul gandhi",
            "musk", "elon", "trump", "donald trump", "biden", "joe biden", "putin", "obama",
            "dhoni", "kohli", "virat", "rohit sharma", "sachin", "tendulkar", "ronaldo", "messi", "neymar",
            "bachchan", "srk", "shah rukh", "salman khan", "akshay", "deepika", "alia bhatt",
            "taylor swift", "bill gates", "steve jobs", "zuckerberg", "bezos",
            "मोदी", "मोदीजी", "नरेंद्र", "योगी", "आदित्यनाथ", "केजरीवाल", "गांधी", "राहुल", "शाह",
            "एलोन", "मस्क", "ट्रम्प", "डोनाल्ड", "बाइडन", "पुतिन", "कोहली", "धोनी"
        )
        return celebs.any { lower.contains(it) }
    }

    suspend fun generateScriptFromTopic(
        topicDescription: String,
        style: String,
        language: String,
        durationOption: String
    ): String {
        val customGeminiKey = try {
            preferencesManager.geminiApiKeyFlow.first()
        } catch (e: Exception) {
            ""
        }

        // Normalize topic description to standard English key to enable semantic, language-agnostic caching
        val normalizedTopic = try {
            geminiService.normalizeTopicToEnglish(topicDescription, customGeminiKey)
        } catch (e: Exception) {
            android.util.Log.w("ProjectRepository", "Failed to normalize topic to English, using original topic", e)
            topicDescription.trim().lowercase()
        }

        android.util.Log.i("ProjectRepository", "Original script topic: '$topicDescription' -> Normalized topic: '$normalizedTopic'")

        // 0. Try local Room database cache first (offline support & duplicate prevention)
        try {
            val scriptDao = com.ritvyom.yashoraReelgenerator.data.local.AppDatabase.getDatabase(context).scriptDao()
            val cachedLocalScript = scriptDao.getScriptByParams(
                topic = normalizedTopic,
                tone = style,
                language = language,
                duration = durationOption
            ) ?: scriptDao.getScriptByParams(
                topic = topicDescription,
                tone = style,
                language = language,
                duration = durationOption
            ) ?: scriptDao.getScriptByTopicAndLanguage(normalizedTopic, language)
              ?: scriptDao.getScriptByTopicAndLanguage(topicDescription, language)

            if (cachedLocalScript != null) {
                android.util.Log.i("ProjectRepository", "Local Room Cache HIT for topic '$normalizedTopic'. Validating semantically...")
                val isValid = geminiService.shouldReuseCachedScript(
                    userTopic = topicDescription,
                    userTone = style,
                    userLanguage = language,
                    userDuration = durationOption,
                    cachedTone = cachedLocalScript.tone,
                    cachedLanguage = cachedLocalScript.language,
                    cachedDuration = cachedLocalScript.duration,
                    cachedTitle = cachedLocalScript.title,
                    cachedScriptContent = cachedLocalScript.fullScript,
                    customGeminiKey = customGeminiKey
                )
                if (isValid) {
                    android.util.Log.i("ProjectRepository", "Local Room Cache VALIDATED for topic '$topicDescription'")
                    val rawScript = cachedLocalScript.fullScript
                    val cleanScript = geminiService.cleanScriptText(rawScript).ifEmpty { rawScript }
                    return cleanScript
                } else {
                    android.util.Log.i("ProjectRepository", "Local Room Cache INVALIDATED semantically for topic '$topicDescription'. Bypassing.")
                }
            }
        } catch (e: Exception) {
            android.util.Log.w("ProjectRepository", "Failed to retrieve cached script from local Room db", e)
        }

        // Try to fetch from Firestore Cloud Script Cache first
        try {
            val cachedCloudScriptResult = com.ritvyom.yashoraReelgenerator.data.remote.FirebaseCloudManager.getCachedScript(
                topicDescription = normalizedTopic,
                style = style,
                language = language,
                durationOption = durationOption
            ) ?: com.ritvyom.yashoraReelgenerator.data.remote.FirebaseCloudManager.getCachedScript(
                topicDescription = topicDescription,
                style = style,
                language = language,
                durationOption = durationOption
            )
            if (cachedCloudScriptResult != null) {
                android.util.Log.i("ProjectRepository", "Cloud Script Cache HIT for topic '$normalizedTopic'. Validating semantically...")
                val cleanScript = geminiService.cleanScriptText(cachedCloudScriptResult.scriptText).ifEmpty { cachedCloudScriptResult.scriptText }
                val title = if (cleanScript.contains("\n")) cleanScript.substringBefore("\n").trim() else topicDescription
                val isValid = geminiService.shouldReuseCachedScript(
                    userTopic = topicDescription,
                    userTone = style,
                    userLanguage = language,
                    userDuration = durationOption,
                    cachedTone = cachedCloudScriptResult.style,
                    cachedLanguage = cachedCloudScriptResult.language,
                    cachedDuration = cachedCloudScriptResult.duration,
                    cachedTitle = title,
                    cachedScriptContent = cleanScript,
                    customGeminiKey = customGeminiKey
                )
                if (isValid) {
                    android.util.Log.i("ProjectRepository", "Cloud Script Cache VALIDATED for topic '$topicDescription'")
                    return cleanScript
                } else {
                    android.util.Log.i("ProjectRepository", "Cloud Script Cache INVALIDATED semantically for topic '$topicDescription'. Bypassing.")
                }
            }
        } catch (e: Exception) {
            android.util.Log.w("ProjectRepository", "Failed to retrieve cached script from cloud", e)
        }

        val generated = geminiService.generateScriptFromTopic(
            topicDescription = topicDescription,
            style = style,
            language = language,
            durationOption = durationOption,
            customGeminiKey = customGeminiKey
        )

        // Save valid generated script to cloud cache to build the database for other users (ONLY if truly AI-generated)
        val isFallback = generated.contains("Failed to generate script via AI", ignoreCase = true) ||
                generated.contains("graceful fallback", ignoreCase = true) ||
                generated.contains("Namskar doston! Kya aapne kabhi socha hai", ignoreCase = true) ||
                generated.contains("नमस्कार दोस्तों! क्या आपने कभी सोचा है", ignoreCase = true) ||
                generated.contains("Hey everyone! Have you ever wondered", ignoreCase = true)

        if (generated.isNotEmpty() &&
            !isFallback &&
            !generated.contains("failed or is not configured yet", ignoreCase = true) &&
            !generated.contains("Firebase Vertex AI failed", ignoreCase = true)
        ) {
            val lines = generated.lines()
            var title = ""
            for (line in lines) {
                val clean = line.trim()
                if (clean.startsWith("# TITLE:", ignoreCase = true)) {
                    title = clean.removePrefix("# TITLE:").removePrefix("# Title:").trim()
                    break
                } else if (clean.startsWith("# Title:", ignoreCase = true)) {
                    title = clean.removePrefix("# Title:").trim()
                    break
                } else if (clean.startsWith("Title:", ignoreCase = true)) {
                    title = clean.removePrefix("Title:").trim()
                    break
                } else if (clean.startsWith("# ")) {
                    title = clean.removePrefix("# ").trim()
                    break
                }
            }
            if (title.isEmpty()) {
                title = "Video Script: $topicDescription"
            }

            val cleanedGenerated = geminiService.cleanScriptText(generated).ifEmpty { generated }

            try {
                val scriptDao = com.ritvyom.yashoraReelgenerator.data.local.AppDatabase.getDatabase(context).scriptDao()
                scriptDao.insertScript(
                    com.ritvyom.yashoraReelgenerator.data.model.VideoScript(
                        topic = normalizedTopic,
                        duration = durationOption,
                        tone = style,
                        language = language,
                        platform = "YouTube Shorts / Reels",
                        title = title,
                        fullScript = cleanedGenerated
                    )
                )
                android.util.Log.i("ProjectRepository", "Saved successfully generated script to local Room cache under '$normalizedTopic'")

                // Note: Scripts are saved to Firestore Cloud Pool ONLY when the user exports the finalized video.
            } catch (e: Exception) {
                android.util.Log.w("ProjectRepository", "Failed to save generated script to local Room cache", e)
            }

            return cleanedGenerated
        }

        return geminiService.cleanScriptText(generated).ifEmpty { generated }
    }

    suspend fun generateStoryboardsForPreview(
        script: String,
        style: String,
        videoLang: String,
        aspectRatio: String = "9:16",
        topicContext: String = "",
        imageSource: String = "Unsplash"
    ): List<com.ritvyom.yashoraReelgenerator.domain.models.PreviewScene> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val savedGemini = try {
            preferencesManager.geminiApiKeyFlow.first()
        } catch (e: Exception) {
            ""
        }
        val savedUnsplash = try {
            preferencesManager.unsplashApiKeyFlow.first()
        } catch (e: Exception) {
            ""
        }
        val savedPexels = try {
            preferencesManager.pexelsApiKeyFlow.first()
        } catch (e: Exception) {
            ""
        }
        val savedPixabay = try {
            preferencesManager.pixabayApiKeyFlow.first()
        } catch (e: Exception) {
            ""
        }

        val buildPexels = try {
            com.ritvyom.yashoraReelgenerator.BuildConfig.PEXELS_API_KEY
        } catch (e: Exception) {
            ""
        }
        val buildPixabay = try {
            com.ritvyom.yashoraReelgenerator.BuildConfig.PIXABAY_API_KEY
        } catch (e: Exception) {
            ""
        }

        val rawPexelsKey = if (!savedPexels.isNullOrEmpty()) {
            savedPexels
        } else if (!buildPexels.isNullOrEmpty() && buildPexels != "YOUR_PEXELS_API_KEY") {
            buildPexels
        } else {
            "NyEXcmIT8bs3CN4dLNForDzJQVyVrWCMRfDLjfgtKYX9t3z8dUm1QHNl"
        }
        val pexelsKey = if (!savedPexels.isNullOrEmpty()) savedPexels.trim() else ApiLoadBalancerService.resolvePexelsApiKey(rawPexelsKey)

        val rawPixabayKey = if (!savedPixabay.isNullOrEmpty()) {
            savedPixabay
        } else if (!buildPixabay.isNullOrEmpty() && buildPixabay != "YOUR_PIXABAY_API_KEY" && buildPixabay != "YOUR_PIYABAY_API_KEY") {
            buildPixabay
        } else {
            "56218545-6d93003a94318db8a7f29dba1"
        }
        val pixabayKey = if (!savedPixabay.isNullOrEmpty()) savedPixabay.trim() else ApiLoadBalancerService.resolvePixabayApiKey(rawPixabayKey)

        val rawScenes = geminiService.analyzeScript(
            script = script,
            style = style,
            language = videoLang,
            aspectRatio = aspectRatio,
            imageSource = imageSource,
            customGeminiKey = savedGemini,
            customUnsplashKey = savedUnsplash,
            topicContext = topicContext,
            publishingStyle = "TikTok / Instagram Reels"
        )

        rawScenes.mapIndexed { index, scene ->
            val genericKeywords = setOf("cinematic narrative scene", "cinematic visual", "cinematic tag", "cinematic", "narrative", "scene")
            val sanitized = scene.keywords.filter { it.trim().lowercase() !in genericKeywords && it.trim().isNotEmpty() }
            val queryText = if (sanitized.isNotEmpty()) {
                sanitized[0]
            } else if (scene.visualPrompt.isNotEmpty() && !scene.visualPrompt.lowercase().contains("cinematic narrative scene")) {
                scene.visualPrompt
            } else {
                val fromNarration = geminiService.extractSemiSemanticKeyword(scene.narrationText.ifEmpty { scene.subtitle })
                if (fromNarration.isNotEmpty() && !fromNarration.contains("cinematic narrative scene")) fromNarration else "documentary news investigation"
            }

            val queryKeywords = extractKeywordsFromSentence(queryText)

            val fWidth = if (aspectRatio == "9:16") 720 else if (aspectRatio == "16:9") 1280 else 1024
            val fHeight = if (aspectRatio == "9:16") 1280 else if (aspectRatio == "16:9") 720 else 1024
            val fSeed = Math.abs(queryText.hashCode() + index)
            val dynamicFallbackUrl = try {
                val encodedPrompt = java.net.URLEncoder.encode("$queryText, cinematic $style style", "UTF-8")
                "https://image.pollinations.ai/p/$encodedPrompt?width=$fWidth&height=$fHeight&seed=$fSeed&nologo=true"
            } catch (e: Exception) {
                "https://images.unsplash.com/photo-1541701494587-cb58502866ab?auto=format"
            }

            // Unsplash
            val unsplashUrl = try {
                geminiService.getBestMatchingImage(
                    visualPrompt = scene.visualPrompt,
                    style = style,
                    sceneNum = index,
                    customSearchQuery = queryKeywords,
                    aspectRatio = aspectRatio,
                    imageSource = "Unsplash",
                    customUnsplashKey = savedUnsplash
                )
            } catch (e: java.lang.Exception) {
                ""
            }

            // Pexels (Image)
            val pexelsUrl = try {
                geminiService.searchPexelsApi(queryKeywords, pexelsKey, searchVideo = false, aspectRatio = aspectRatio, sceneNum = index)
            } catch (e: java.lang.Exception) {
                ""
            }

            // Pixabay (Image)
            val pixabayUrl = try {
                geminiService.searchPixabayApi(queryKeywords, pixabayKey, aspectRatio = aspectRatio, sceneNum = index)
            } catch (e: java.lang.Exception) {
                ""
            }

            // Custom direct media source resolution if selected source is not Unsplash/Pexels/Pixabay
            val customUrl = if (imageSource != "Unsplash" && imageSource != "Pexels" && imageSource != "Pixabay") {
                try {
                    resolveVisualAsset(
                        query = queryText,
                        style = style,
                        aspectRatio = aspectRatio,
                        index = index,
                        visualMedium = "Image",
                        imageSource = imageSource
                    )
                } catch (e: Exception) {
                    ""
                }
            } else {
                ""
            }

            val defaultUrl = when (imageSource.lowercase().trim()) {
                "pexels" -> if (pexelsUrl.isNotEmpty()) pexelsUrl else if (pixabayUrl.isNotEmpty()) pixabayUrl else if (unsplashUrl.isNotEmpty()) unsplashUrl else dynamicFallbackUrl
                "pixabay" -> if (pixabayUrl.isNotEmpty()) pixabayUrl else if (unsplashUrl.isNotEmpty()) unsplashUrl else if (pexelsUrl.isNotEmpty()) pexelsUrl else dynamicFallbackUrl
                "unsplash" -> if (unsplashUrl.isNotEmpty() && !unsplashUrl.startsWith("http://image.pollinations.ai") && !unsplashUrl.contains("picsum")) unsplashUrl else if (pexelsUrl.isNotEmpty()) pexelsUrl else if (pixabayUrl.isNotEmpty()) pixabayUrl else dynamicFallbackUrl
                else -> {
                    if (customUrl.isNotEmpty()) {
                        customUrl
                    } else if (unsplashUrl.isNotEmpty() && !unsplashUrl.startsWith("http://image.pollinations.ai") && !unsplashUrl.contains("picsum")) {
                        unsplashUrl
                    } else if (pexelsUrl.isNotEmpty()) {
                        pexelsUrl
                    } else if (pixabayUrl.isNotEmpty()) {
                        pixabayUrl
                    } else {
                        dynamicFallbackUrl
                    }
                }
            }

            if (defaultUrl.isNotEmpty() && !defaultUrl.contains("picsum")) {
                try {
                    saveCachedImage(
                        keyword = queryText.trim().lowercase(),
                        imageUrl = defaultUrl,
                        style = style,
                        visualMedium = "Image",
                        visualPrompt = scene.visualPrompt,
                        aspectRatio = aspectRatio
                    )
                } catch (e: Exception) {
                    android.util.Log.e("ProjectRepository", "Failed to cache preview URL globally", e)
                }
            }

            com.ritvyom.yashoraReelgenerator.domain.models.PreviewScene(
                sceneNumber = scene.sceneNumber,
                narrationText = scene.narrationText,
                visualPrompt = scene.visualPrompt,
                durationSeconds = scene.durationSeconds,
                unsplashUrl = if (customUrl.isNotEmpty()) customUrl else if (unsplashUrl.isNotEmpty()) unsplashUrl else "https://images.unsplash.com/photo-1541701494587-cb58502866ab?auto=format",
                pexelsUrl = if (pexelsUrl.isNotEmpty()) pexelsUrl else "https://images.pexels.com/photos/1103970/pexels-photo-1103970.jpeg?auto=compress",
                pixabayUrl = if (pixabayUrl.isNotEmpty()) pixabayUrl else "https://images.unsplash.com/photo-1541701494587-cb58502866ab?auto=format",
                selectedUrl = defaultUrl
            )
        }
    }

    suspend fun validateApiKey(apiType: String, key: String, extraParam1: String = ""): Pair<Boolean, String> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val client = okhttp3.OkHttpClient.Builder()
            .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .build()
        
        try {
            when (apiType.lowercase()) {
                "gemini" -> {
                    if (key.isBlank()) return@withContext Pair(false, "API Key is empty")
                    val request = okhttp3.Request.Builder()
                        .url("https://generativelanguage.googleapis.com/v1beta/models?key=$key")
                        .get()
                        .build()
                    client.newCall(request).execute().use { response ->
                        if (response.isSuccessful) {
                            Pair(true, "Successfully connected! Gemini API services are online.")
                        } else {
                            Pair(false, "Connection Failed (HTTP ${response.code}). Ensure your Gemini API Key is valid.")
                        }
                    }
                }
                "unsplash" -> {
                    if (key.isBlank()) return@withContext Pair(false, "API Key is empty")
                    val request = okhttp3.Request.Builder()
                        .url("https://api.unsplash.com/photos?per_page=1")
                        .addHeader("Authorization", "Client-ID $key")
                        .get()
                        .build()
                    client.newCall(request).execute().use { response ->
                        if (response.isSuccessful) {
                            Pair(true, "Successfully connected! Unsplash credentials verified.")
                        } else {
                            Pair(false, "Connection Failed (HTTP ${response.code}). Check client ID credentials.")
                        }
                    }
                }
                "pexels" -> {
                    if (key.isBlank()) return@withContext Pair(false, "API Key is empty")
                    val request = okhttp3.Request.Builder()
                        .url("https://api.pexels.com/v1/search?query=nature&per_page=1")
                        .addHeader("Authorization", key)
                        .get()
                        .build()
                    client.newCall(request).execute().use { response ->
                        if (response.isSuccessful) {
                            Pair(true, "Successfully connected! Pexels API key is working.")
                        } else {
                            Pair(false, "Connection Failed (HTTP ${response.code}). Verify your auth key.")
                        }
                    }
                }
                "pixabay" -> {
                    if (key.isBlank()) return@withContext Pair(false, "API Key is empty")
                    val request = okhttp3.Request.Builder()
                        .url("https://pixabay.com/api/?key=$key&q=yellow+flower&image_type=photo")
                        .get()
                        .build()
                    client.newCall(request).execute().use { response ->
                        if (response.isSuccessful) {
                            Pair(true, "Successfully connected! Pixabay validated.")
                        } else {
                            Pair(false, "Connection Failed (HTTP ${response.code}). Inspect key.")
                        }
                    }
                }
                else -> {
                    Pair(false, "Unknown API Service type to validate")
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("ProjectRepository", "API validation error to $apiType", e)
            Pair(false, "Network Error: ${e.localizedMessage ?: "Connection Timeout. Check internet connection."}")
        }
    }

    suspend fun synthesizePremiumTts(
        text: String,
        apiType: String,
        key: String,
        language: String,
        gender: String,
        extraParam: String = "",
        voiceName: String = "",
        speedMultiplier: Float = 1.0f,
        pitchMultiplier: Float = 1.0f
    ): ByteArray? = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        // Edge TTS and Sherpa ONNX have been removed. Voice synthesis uses native Android TTS engine.
        null
    }

    data class CachedSearchResult(
        val timestamp: Long = System.currentTimeMillis(),
        val suggestions: List<com.ritvyom.yashoraReelgenerator.domain.models.MediaSuggestion>
    )

    // Local repository caching strategy for media search results (Image vs Video)
    private val searchCache = java.util.concurrent.ConcurrentHashMap<String, CachedSearchResult>()
    private val SEARCH_CACHE_TTL_MS = 60 * 60 * 1000L // 1 hour validity

    fun getSearchCacheKey(query: String, mediaType: String, aspectRatio: String): String {
        val q = query.trim().lowercase().ifEmpty { "nature" }
        val mType = if (mediaType.trim().uppercase() == "VIDEO") "VIDEO" else "IMAGE"
        val ar = aspectRatio.trim().ifEmpty { "9:16" }
        return "$q::$mType::$ar"
    }

    fun getCachedSuggestions(query: String, mediaType: String, aspectRatio: String): List<com.ritvyom.yashoraReelgenerator.domain.models.MediaSuggestion>? {
        val key = getSearchCacheKey(query, mediaType, aspectRatio)
        val cached = searchCache[key] ?: return null
        if (System.currentTimeMillis() - cached.timestamp > SEARCH_CACHE_TTL_MS) {
            searchCache.remove(key)
            return null
        }
        return cached.suggestions
    }

    fun hasCachedSuggestions(query: String, mediaType: String, aspectRatio: String): Boolean {
        return getCachedSuggestions(query, mediaType, aspectRatio)?.isNotEmpty() == true
    }

    fun clearSearchCache() {
        searchCache.clear()
    }

    suspend fun searchAlternativeSuggestions(
        query: String,
        mediaType: String,
        aspectRatio: String,
        unsplashKey: String,
        pexelsKey: String,
        forceRefresh: Boolean = false
    ): List<com.ritvyom.yashoraReelgenerator.domain.models.MediaSuggestion> {
        val key = getSearchCacheKey(query, mediaType, aspectRatio)
        if (!forceRefresh) {
            val cached = getCachedSuggestions(query, mediaType, aspectRatio)
            if (cached != null && cached.isNotEmpty()) {
                android.util.Log.d("ProjectRepository", "Local repository cache HIT for search key: $key (${cached.size} items)")
                return cached
            }
        }
        val freshResults = geminiService.searchAlternativeSuggestions(query, mediaType, aspectRatio, unsplashKey, pexelsKey)
        if (freshResults.isNotEmpty()) {
            searchCache[key] = CachedSearchResult(suggestions = freshResults)
        }
        return freshResults
    }

    private fun escapeJsonString(s: String): String {
        val builder = StringBuilder()
        builder.append("\"")
        for (element in s) {
            when (element) {
                '"' -> builder.append("\\\"")
                '\\' -> builder.append("\\\\")
                '\t' -> builder.append("\\t")
                '\b' -> builder.append("\\b")
                '\n' -> builder.append("\\n")
                '\r' -> builder.append("\\r")
                else -> {
                    if (element < ' ') {
                        val code = "000${Integer.toHexString(element.code)}"
                        builder.append("\\u" + code.substring(code.length - 4))
                    } else {
                        builder.append(element)
                    }
                }
            }
        }
        builder.append("\"")
        return builder.toString()
    }

    private fun escapeXmlString(s: String): String {
        return s.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }

    private fun extractKeywordsFromSentence(sentence: String): String {
        val clean = sentence.trim()
        if (clean.isEmpty()) return ""

        // 1. First check if semi-semantic mapping identifies concrete visual concepts (Food, Farming, Medical, Tech, Social Media, etc.)
        val semiSemantic = geminiService.extractSemiSemanticKeyword(clean)
        if (semiSemantic.isNotEmpty() && !semiSemantic.equals(clean, ignoreCase = true)) {
            android.util.Log.d("ProjectRepository", "extractKeywordsFromSentence: Mapped '$clean' to semantic keywords: '$semiSemantic'")
            return semiSemantic
        }

        val lowerClean = clean.lowercase()
        // Split into words
        val parts = lowerClean.split(Regex("\\s+"))
        if (parts.size <= 2) return lowerClean

        val stopwords = setOf(
            "a", "an", "the", "and", "or", "but", "is", "are", "was", "were", "of", "to", "in", "on", "at", "by", "for", "with", "about",
            "this", "that", "it", "its", "you", "your", "my", "me", "we", "our", "us", "they", "them", "some", "any", "no", "not", "so",
            "can", "will", "show", "get", "make", "be", "have", "has", "had", "do", "does", "did", "from", "very", "scene", "style", "description",
            "captivating", "artistic", "representation", "masterpiece", "detail", "professional", "composition", "background", "photo", "image", "video",
            "showing", "depicting", "having", "concept", "closeup", "close-up", "high-quality", "hd", "4k", "amazing",
            "highly", "detailed", "realistic", "photorealistic", "ultra", "illustration", "drawing", "vector", "art", "graphic", "design", "subject",
            // Hinglish / Roman Hindi stopwords
            "kare", "karo", "karna", "karne", "kaise", "hota", "hote", "hoti", "hoga", "hogi", "hoge", "raha", "rahe", "rahi", "hai", "hain", "hoon",
            "tha", "the", "thi", "mera", "meri", "mere", "apna", "apne", "apni", "tera", "teri", "tere", "uska", "uske", "uski", "inka", "inke", "unki",
            "yeh", "ye", "woh", "wo", "isme", "usme", "isse", "usse", "jisse", "jisme", "agar", "magar", "lekin", "aur", "ya", "par", "pe", "ko",
            "se", "me", "mein", "ka", "ke", "ki", "bhi", "toh", "to", "hi", "ab", "kab", "jab", "tab", "kuch", "koi", "kisi", "sab", "sabhi",
            "aaj", "kal", "hum", "tum", "aap", "log", "baat", "chahiye", "sakte", "sakta", "sakti", "jaise", "waise"
        )

        // Filter out stopwords and non-alphabetic characters
        val filteredWords = parts.map { it.replace(Regex("[^a-zA-Z0-9]"), "") }
            .filter { it.length >= 2 && !stopwords.contains(it) }
            .distinct()

        // If we have some filtered words, take the first 4 representing key visual elements
        if (filteredWords.isNotEmpty()) {
            return filteredWords.take(4).joinToString(" ")
        }
        
        // Fallback if everything got filtered out
        return lowerClean
    }

    private fun getCompatibleImageSource(
        originalSource: String,
        query: String,
        style: String,
        visualPrompt: String,
        visualMedium: String
    ): String {
        val srcLower = originalSource.lowercase().trim()
        val queryLower = query.lowercase().trim()
        val promptLower = visualPrompt.lowercase().trim()
        val styleLower = style.lowercase().trim()

        // 1. STYLE ANALYSIS: Is it an artistic/animation/cartoon style?
        val isArtisticStyle = styleLower != "realistic" && styleLower != "cinematic" && styleLower != "documentary" && styleLower != "news" && styleLower.isNotEmpty()

        // 1B. Famous Entity / Historical Figure / Public Leader detection -> Wikimedia Commons
        if (geminiService.containsEntityOrFamousPerson(query) || geminiService.containsEntityOrFamousPerson(visualPrompt)) {
            android.util.Log.i("ProjectRepository", "Famous entity or historical personality detected in query/prompt ('$query'). Directing to Wikimedia Commons / Wikipedia Entity Search!")
            return "Wikimedia Commons"
        }

        // 2. KEYWORD ANALYSIS
        val containsSpace = queryLower.contains("space") || queryLower.contains("star") || queryLower.contains("planet") || 
                queryLower.contains("galaxy") || queryLower.contains("universe") || queryLower.contains("nebula") || 
                queryLower.contains("rocket") || queryLower.contains("spacecraft") || queryLower.contains("astronaut") || 
                queryLower.contains("moon") || queryLower.contains("earth") || queryLower.contains("cosmic") || 
                queryLower.contains("nasa") || queryLower.contains("hubble") || queryLower.contains("webb") || 
                queryLower.contains("satellite") || queryLower.contains("alien") || queryLower.contains("astronomy") ||
                promptLower.contains("space") || promptLower.contains("star") || promptLower.contains("planet") || 
                promptLower.contains("galaxy") || promptLower.contains("nebula") || promptLower.contains("astronaut")

        val containsAnimeCartoon = queryLower.contains("cartoon") || queryLower.contains("anime") || queryLower.contains("manga") || 
                queryLower.contains("drawing") || queryLower.contains("illustration") || queryLower.contains("sketch") || 
                queryLower.contains("comic") || queryLower.contains("animated") || queryLower.contains("chibi") || 
                queryLower.contains("disney") || queryLower.contains("pixar") || queryLower.contains("animation") ||
                promptLower.contains("cartoon") || promptLower.contains("anime") || promptLower.contains("illustration") ||
                isArtisticStyle

        val containsFood = queryLower.contains("recipe") || queryLower.contains("cook") || queryLower.contains("food") || 
                queryLower.contains("dish") || queryLower.contains("bake") || queryLower.contains("fry") || 
                queryLower.contains("kitchen") || queryLower.contains("ingredient") || queryLower.contains("meal") || 
                queryLower.contains("salad") || queryLower.contains("pizza") || queryLower.contains("curry") || 
                queryLower.contains("soup") || queryLower.contains("paneer") || queryLower.contains("vegetable") || 
                queryLower.contains("chicken") || queryLower.contains("chef") || queryLower.contains("taste") || 
                queryLower.contains("delicious") || queryLower.contains("spice") || queryLower.contains("roast") ||
                promptLower.contains("food") || promptLower.contains("recipe") || promptLower.contains("cook")

        val containsMeme = queryLower.contains("meme") || queryLower.contains("joke") || queryLower.contains("funny") || 
                queryLower.contains("humor") || queryLower.contains("laugh") || queryLower.contains("comedy") || 
                queryLower.contains("hilarious") || queryLower.contains("fun") ||
                promptLower.contains("meme") || promptLower.contains("funny")

        val containsVehicle = queryLower.contains("car") || queryLower.contains("vehicle") || queryLower.contains("automobile") || 
                queryLower.contains("truck") || queryLower.contains("motorcycle") || queryLower.contains("tesla") || 
                queryLower.contains("toyota") || queryLower.contains("ford") || queryLower.contains("chevrolet") || 
                queryLower.contains("bmw") || queryLower.contains("mercedes") || queryLower.contains("audi") || 
                queryLower.contains("honda") || queryLower.contains("nissan") || queryLower.contains("hyundai") ||
                promptLower.contains("car") || promptLower.contains("vehicle") || promptLower.contains("automobile") || 
                promptLower.contains("automotive")

        val containsMedicalDrug = queryLower.contains("drug") || queryLower.contains("medicine") || queryLower.contains("pharma") || 
                queryLower.contains("pill") || queryLower.contains("capsule") || queryLower.contains("prescription") || 
                queryLower.contains("ibuprofen") || queryLower.contains("aspirin") || queryLower.contains("clinical") || 
                queryLower.contains("treatment") || queryLower.contains("dose") || queryLower.contains("medication") ||
                promptLower.contains("medicine") || promptLower.contains("drug") || promptLower.contains("medication")

        val containsHealthIndicator = queryLower.contains("hiv") || queryLower.contains("malaria") || queryLower.contains("disease") || 
                queryLower.contains("health indicator") || queryLower.contains("vaccine") || queryLower.contains("epidemic") || 
                queryLower.contains("global health") || queryLower.contains("mortality") || queryLower.contains("nutrition") || 
                queryLower.contains("sanitation") || queryLower.contains("infection") || queryLower.contains("who") ||
                promptLower.contains("global health") || promptLower.contains("disease") || promptLower.contains("indicator")

        // 3. CHECK SPECIFIC MISMATCHES & CORRECT THEM

        // A. NASA LIBRARY MISMATCH:
        if (srcLower.contains("nasa")) {
            if (!containsSpace) {
                val newSource = if (containsAnimeCartoon) {
                    "AI Generated"
                } else if (visualMedium.equals("Video", ignoreCase = true)) {
                    "Pexels"
                } else {
                    "Unsplash"
                }
                android.util.Log.i("ProjectRepository", "[AI MATCHMAKER] Detected NASA MISMATCH for query '$queryLower'. NASA only has deep-space material. Overriding source to '$newSource'.")
                return newSource
            }
        }

        // B. ANIME SOURCES (Nekos, Jikan) MISMATCH:
        if (srcLower.contains("neko") || srcLower.contains("jikan") || srcLower.contains("anime")) {
            val isRealisticStyle = styleLower == "realistic" || styleLower == "cinematic" || styleLower == "documentary" || styleLower == "news"
            if (isRealisticStyle && !containsAnimeCartoon) {
                val newSource = if (containsSpace) {
                    "NASA Library"
                } else if (visualMedium.equals("Video", ignoreCase = true)) {
                    "Pexels"
                } else {
                    "Unsplash"
                }
                android.util.Log.i("ProjectRepository", "[AI MATCHMAKER] Detected ANIME SOURCE MISMATCH for query '$queryLower'. Overriding source from '$originalSource' to '$newSource'.")
                return newSource
            }
        }

        // C. FOOD SOURCE (Spoonacular) MISMATCH:
        if (srcLower.contains("spoonacular")) {
            if (!containsFood) {
                val newSource = if (containsAnimeCartoon) {
                    "AI Generated"
                } else if (visualMedium.equals("Video", ignoreCase = true)) {
                    "Pexels"
                } else {
                    "Unsplash"
                }
                android.util.Log.i("ProjectRepository", "[AI MATCHMAKER] Detected SPOONACULAR MISMATCH for non-food query '$queryLower'. Overriding to '$newSource'.")
                return newSource
            }
        }

        // D. WIKIMEDIA COMMONS & ARCHIVE.ORG MISMATCH:
        if (srcLower.contains("wikimedia") || srcLower.contains("commons") || srcLower.contains("archive")) {
            if (containsAnimeCartoon) {
                val newSource = if (visualMedium.equals("Video", ignoreCase = true)) {
                    "Pexels"
                } else {
                    "AI Generated"
                }
                android.util.Log.i("ProjectRepository", "[AI MATCHMAKER] Detected WIKIMEDIA/ARCHIVE MISMATCH for artistic style query '$queryLower'. Overriding source to '$newSource' to produce animated/illustration scenes.")
                return newSource
            }
        }

        // E. GIPHY MISMATCH:
        if (srcLower.contains("giphy")) {
            val isSerious = styleLower == "cinematic" || styleLower == "documentary" || styleLower == "news" || 
                    queryLower.contains("business") || queryLower.contains("corporate") || queryLower.contains("finance") || queryLower.contains("health")
            if (isSerious && !containsMeme) {
                val newSource = if (visualMedium.equals("Video", ignoreCase = true)) "Pexels" else "Unsplash"
                android.util.Log.i("ProjectRepository", "[AI MATCHMAKER] Detected GIPHY MEME MISMATCH for professional/serious query '$queryLower'. Overriding to '$newSource'.")
                return newSource
            }
        }

        // F. NHTSA vPIC MISMATCH:
        if (srcLower.contains("nhtsa") || srcLower.contains("vpic")) {
            if (!containsVehicle) {
                val newSource = if (visualMedium.equals("Video", ignoreCase = true)) "Pexels" else "Unsplash"
                android.util.Log.i("ProjectRepository", "[AI MATCHMAKER] Detected NHTSA vPIC MISMATCH for non-vehicle query '$queryLower'. Overriding to '$newSource'.")
                return newSource
            }
        }

        // G. OpenFDA MISMATCH:
        if (srcLower.contains("fda") || srcLower.contains("openfda")) {
            if (!containsMedicalDrug) {
                val newSource = if (visualMedium.equals("Video", ignoreCase = true)) "Pexels" else "Unsplash"
                android.util.Log.i("ProjectRepository", "[AI MATCHMAKER] Detected OpenFDA MISMATCH for non-medical/drug query '$queryLower'. Overriding to '$newSource'.")
                return newSource
            }
        }

        // H. WHO MISMATCH:
        if (srcLower.contains("who")) {
            if (!containsHealthIndicator) {
                val newSource = if (visualMedium.equals("Video", ignoreCase = true)) "Pexels" else "Unsplash"
                android.util.Log.i("ProjectRepository", "[AI MATCHMAKER] Detected WHO MISMATCH for non-health query '$queryLower'. Overriding to '$newSource'.")
                return newSource
            }
        }

        // PROACTIVE MAPPING RULES:
        if (srcLower.isEmpty() || srcLower == "fallback" || srcLower == "unsplash" || srcLower == "pexels") {
            if (containsVehicle && !visualMedium.equals("Video", ignoreCase = true)) {
                android.util.Log.i("ProjectRepository", "[AI MATCHMAKER] Proactively matching vehicle-themed query '$queryLower' to 'NHTSA vPIC'.")
                return "NHTSA vPIC"
            }
            if (containsMedicalDrug && !visualMedium.equals("Video", ignoreCase = true)) {
                android.util.Log.i("ProjectRepository", "[AI MATCHMAKER] Proactively matching medical/drug-themed query '$queryLower' to 'OpenFDA'.")
                return "OpenFDA"
            }
            if (containsHealthIndicator && !visualMedium.equals("Video", ignoreCase = true)) {
                android.util.Log.i("ProjectRepository", "[AI MATCHMAKER] Proactively matching global health/WHO-themed query '$queryLower' to 'WHO'.")
                return "WHO"
            }
        }

        return originalSource
    }
}
