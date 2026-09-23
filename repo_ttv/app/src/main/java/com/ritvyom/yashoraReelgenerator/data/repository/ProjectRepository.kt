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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.io.File

import com.ritvyom.yashoraReelgenerator.data.local.dao.CachedImageDao
import com.ritvyom.yashoraReelgenerator.data.local.entities.CachedImageEntity
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

class ProjectRepository(
    private val context: android.content.Context,
    private val projectDao: ProjectDao,
    private val exportHistoryDao: ExportHistoryDao,
    private val cachedImageDao: CachedImageDao,
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

    suspend fun getCachedImage(keyword: String, style: String = "", visualMedium: String = "", visualPrompt: String = ""): String? {
        val compositeKey = buildCompositeKey(keyword, style, visualMedium, visualPrompt)
        return cachedImageDao.getCachedImageByKeyword(compositeKey)?.imageUrl
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
        force: Boolean = false
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
            val entity = CachedImageEntity(
                keyword = compositeKey,
                imageUrl = imageUrl
            )
            cachedImageDao.insertCachedImage(entity)

            if (saveGlobally) {
                // Auto-save the mapped URL globally in Firestore cloud cache
                com.ritvyom.yashoraReelgenerator.data.remote.FirebaseCloudManager.saveScriptMediaMapping(
                    query = keyword,
                    style = style,
                    aspectRatio = aspectRatio,
                    visualMedium = visualMedium,
                    mediaUrl = imageUrl
                )
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
            val remoteUrl = scene.remoteUrl
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
    ): String = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val savedUnsplash = try {
            preferencesManager.unsplashApiKeyFlow.first()
        } catch (e: Exception) {
            ""
        }
        val queryClean = query.trim().lowercase()
        if (queryClean.isEmpty()) return@withContext ""

        // 1. Check local cache first with original query and unique visualPrompt
        var cached = getCachedImage(queryClean, style, visualMedium, visualPrompt = visualPrompt)
        if (cached != null && cached.isNotEmpty()) {
            if (isRepetitiveOrLowQuality(cached, queryClean, alreadyUsedUrls, visualMedium)) {
                android.util.Log.i("ProjectRepository", "Local cache HIT for original query '$queryClean' was repetitive or low-quality. Discarding cache and forcing fresh search.")
                deleteCachedImage(queryClean, style, visualMedium, visualPrompt)
                cached = null
            } else {
                return@withContext cached
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
                // Save to local cache so next time it is even faster and offline-friendly!
                saveCachedImage(queryClean, cloudCached, style, visualMedium, visualPrompt = visualPrompt, aspectRatio = aspectRatio, force = true)
                return@withContext cloudCached
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

        // 2. Check cache under translated query just in case
        if (queryTranslated != queryClean) {
            var cachedTrans = getCachedImage(queryTranslated, style, visualMedium, visualPrompt = resolvedVisualPrompt)
            if (cachedTrans != null && cachedTrans.isNotEmpty()) {
                if (isRepetitiveOrLowQuality(cachedTrans, queryTranslated, alreadyUsedUrls, visualMedium)) {
                    android.util.Log.i("ProjectRepository", "Local cache HIT for translated query '$queryTranslated' was repetitive or low-quality. Discarding cache.")
                    deleteCachedImage(queryTranslated, style, visualMedium, resolvedVisualPrompt)
                    cachedTrans = null
                } else {
                    saveCachedImage(queryClean, cachedTrans, style, visualMedium, visualPrompt = resolvedVisualPrompt, aspectRatio = aspectRatio, force = true)
                    return@withContext cachedTrans
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
                    return@withContext cloudCachedTrans
                }
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
        val isWikimedia = srcLower.contains("wikimedia") || srcLower.contains("commons")
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
                } else {
                    MediaProviderHealthMonitor.recordFailure(resolvedImageSource)
                }
                res
            } catch (e: Exception) {
                android.util.Log.e("ProjectRepository", "Error fetching from custom direct media source: $resolvedImageSource", e)
                MediaProviderHealthMonitor.recordFailure(resolvedImageSource)
                ""
            }
            if (resolvedUrl.isNotEmpty()) {
                saveCachedImage(queryClean, resolvedUrl, style, visualMedium, visualPrompt = resolvedVisualPrompt, aspectRatio = aspectRatio)
                saveCachedImage(queryTranslated, resolvedUrl, style, visualMedium, visualPrompt = resolvedVisualPrompt, aspectRatio = aspectRatio)
                return@withContext resolvedUrl
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
                return@withContext generatedUrl
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
                return@withContext generatedUrl
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
                return@withContext generatedUrl
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
                queryClean.contains("delicious") || queryClean.contains("spice") || queryClean.contains("roast")

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
                visualPrompt.lowercase().contains("roast")

        val isFoodContent = isFoodQuery || isVisualPromptFood

        if (isFoodContent && savedSpoonacular.isNotEmpty() && MediaProviderHealthMonitor.isProviderHealthy("spoonacular")) {
            android.util.Log.i("ProjectRepository", "Food/Recipe context detected. Querying Spoonacular API first for '$queryClean'...")
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
                    return@withContext spoonacularUrl
                } else {
                    android.util.Log.w("ProjectRepository", "Spoonacular API returned empty result. Failing over to standard providers.")
                    MediaProviderHealthMonitor.recordFailure("spoonacular")
                }
            } catch (e: Exception) {
                android.util.Log.e("ProjectRepository", "Spoonacular API request failed", e)
                MediaProviderHealthMonitor.recordFailure("spoonacular")
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
        val pexelsKey = ApiLoadBalancerService.resolvePexelsApiKey(rawPexelsKey)

        val rawPixabayKey = if (!savedPixabay.isNullOrEmpty()) {
            savedPixabay
        } else if (!buildPixabay.isNullOrEmpty() && buildPixabay != "YOUR_PIXABAY_API_KEY" && buildPixabay != "YOUR_PIYABAY_API_KEY") {
            buildPixabay
        } else {
            "56218545-6d93003a94318db8a7f29dba1"
        }
        val pixabayKey = ApiLoadBalancerService.resolvePixabayApiKey(rawPixabayKey)

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

        val stockModifier = when (publishingStyle) {
            "TikTok / Instagram Reels" -> "vibrant action"
            "YouTube Short" -> "centered focus"
            "Cinematic Vlog / Trailer" -> "cinematic detail"
            "Short Documentary" -> "authentic archival"
            "Facebook Feed Video" -> "engaging human"
            "LinkedIn Professional" -> "professional corporate"
            "Ad Promo / Marketing" -> "studio promo"
            else -> ""
        }

        // Avoid adding photo-specific modifiers like "vibrant action" to stylized cartoon/anime searches
        val queryForStock = if (stockModifier.isNotEmpty() && !isArtisticStyle) {
            "$baseQueryStock $stockModifier".trim()
        } else {
            baseQueryStock
        }

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
        for (engine in sortedEngines) {
            try {
                when (engine) {
                    "pexels" -> {
                        if (visualMedium.equals("Video", ignoreCase = true)) {
                            android.util.Log.d("ProjectRepository", "Searching Pexels Video: $queryForStock")
                            resolvedUrl = geminiService.searchPexelsApi(queryForStock, pexelsKey, searchVideo = true, aspectRatio, index)
                            if (resolvedUrl.isEmpty() && queryForStock != queryKeywords) {
                                if (isArtisticStyle) {
                                    android.util.Log.d("ProjectRepository", "Pexels video empty for artistic style. Bypassing realistic fallback to allow Pollinations backup.")
                                } else {
                                    resolvedUrl = geminiService.searchPexelsApi(queryKeywords, pexelsKey, searchVideo = true, aspectRatio, index)
                                }
                            }
                        } else {
                            android.util.Log.d("ProjectRepository", "Searching Pexels Photo: $queryForStock")
                            resolvedUrl = geminiService.searchPexelsApi(queryForStock, pexelsKey, searchVideo = false, aspectRatio, index)
                            if (resolvedUrl.isEmpty() && queryForStock != queryKeywords) {
                                resolvedUrl = geminiService.searchPexelsApi(queryKeywords, pexelsKey, searchVideo = false, aspectRatio, index)
                            }
                        }
                    }
                    "pixabay" -> {
                        if (visualMedium.equals("Video", ignoreCase = true)) {
                            android.util.Log.d("ProjectRepository", "Searching Pixabay Video: $queryForStock")
                            resolvedUrl = geminiService.searchPixabayVideos(queryForStock, pixabayKey, aspectRatio, index)
                            if (resolvedUrl.isEmpty() && queryForStock != queryKeywords) {
                                if (isArtisticStyle) {
                                    android.util.Log.d("ProjectRepository", "Pixabay video empty for artistic style. Bypassing realistic fallback to allow Pollinations backup.")
                                } else {
                                    resolvedUrl = geminiService.searchPixabayVideos(queryKeywords, pixabayKey, aspectRatio, index)
                                }
                            }
                        } else {
                            android.util.Log.d("ProjectRepository", "Searching Pixabay Photo: $queryForStock")
                            resolvedUrl = geminiService.searchPixabayApi(queryForStock, pixabayKey, aspectRatio, index)
                            if (resolvedUrl.isEmpty() && queryForStock != queryKeywords) {
                                resolvedUrl = geminiService.searchPixabayApi(queryKeywords, pixabayKey, aspectRatio, index)
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
                            resolution = resolution
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
                            resolution = resolution
                        )
                    }
                }

                if (resolvedUrl.isNotEmpty()) {
                    if (isRepetitiveOrLowQuality(resolvedUrl, queryClean, alreadyUsedUrls, visualMedium)) {
                        android.util.Log.w("ProjectRepository", "Engine '$engine' returned a repetitive or low-quality URL: $resolvedUrl. Discarding and failing over to next engine.")
                        resolvedUrl = ""
                        MediaProviderHealthMonitor.recordFailure(engine)
                    } else {
                        android.util.Log.d("ProjectRepository", "Engine '$engine' successfully returned URL: $resolvedUrl")
                        MediaProviderHealthMonitor.recordSuccess(engine)
                        saveCachedImage(queryClean, resolvedUrl, style, visualMedium, visualPrompt = resolvedVisualPrompt, aspectRatio = aspectRatio)
                        saveCachedImage(queryTranslated, resolvedUrl, style, visualMedium, visualPrompt = resolvedVisualPrompt, aspectRatio = aspectRatio)
                        return@withContext resolvedUrl
                    }
                } else {
                    android.util.Log.w("ProjectRepository", "Engine '$engine' returned empty URL. Recording failure.")
                    MediaProviderHealthMonitor.recordFailure(engine)
                }
            } catch (e: Exception) {
                android.util.Log.e("ProjectRepository", "Exception on '$engine' engine process", e)
                MediaProviderHealthMonitor.recordFailure(engine)
            }
        }
        resolvedUrl
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
        publishingStyle: String = "TikTok / Instagram Reels"
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
        
        val alreadyUsedUrls = mutableSetOf<String>()
        val matchedScenes = rawScenes.mapIndexed { index, scene ->
            var resolvedPath: String? = null
            
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
                scene.copy(mediaPath = resolvedPath)
            } else {
                // Fetch using our integrated search engines
                var fetchedUrl = ""
                for (keyword in scene.keywords) {
                    if (keyword.isNotEmpty()) {
                        fetchedUrl = resolveVisualAsset(keyword, style, aspectRatio, index, visualMedium, imageSource, resolution, visualPrompt = scene.visualPrompt, publishingStyle = publishingStyle, alreadyUsedUrls = alreadyUsedUrls)
                        if (fetchedUrl.isNotEmpty()) {
                            alreadyUsedUrls.add(fetchedUrl)
                            break
                        }
                    }
                }
                
                if (fetchedUrl.isEmpty() && scene.visualPrompt.isNotEmpty()) {
                    fetchedUrl = resolveVisualAsset(scene.visualPrompt, style, aspectRatio, index, visualMedium, imageSource, resolution, visualPrompt = scene.visualPrompt, publishingStyle = publishingStyle, alreadyUsedUrls = alreadyUsedUrls)
                    if (fetchedUrl.isNotEmpty()) {
                        alreadyUsedUrls.add(fetchedUrl)
                    }
                }
                
                if (fetchedUrl.isEmpty()) {
                    fetchedUrl = scene.mediaPath ?: ""
                    if (fetchedUrl.isNotEmpty()) {
                        alreadyUsedUrls.add(fetchedUrl)
                    }
                }
                
                scene.copy(mediaPath = fetchedUrl)
            }
        }

        // Caching assets locally for offline robustness, utilizing sequential downloads to prevent network congestion/rate limits
        matchedScenes.mapIndexed { idx, scene ->
            android.util.Log.d("ProjectRepository", "Downloading local media for scene ${idx + 1}/${matchedScenes.size}: ${scene.mediaPath}")
            val localPath = downloadMediaToLocal(scene.mediaPath ?: "")
            if (localPath.isNotEmpty()) {
                scene.copy(mediaPath = localPath)
            } else {
                scene
            }
        }
    }

    suspend fun fallbackLocalParser(script: String, style: String, language: String = "English", aspectRatio: String = "9:16", imageSource: String = "Unsplash"): List<Scene> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val scenes = geminiService.fallbackLocalParser(script, style, language, aspectRatio, imageSource)
        
        // Caching assets locally for offline robustness, utilizing sequential downloads to prevent network congestion/rate limits
        scenes.mapIndexed { idx, scene ->
            android.util.Log.d("ProjectRepository", "Downloading local media for fallback scene ${idx + 1}/${scenes.size}: ${scene.mediaPath}")
            val localPath = downloadMediaToLocal(scene.mediaPath ?: "")
            if (localPath.isNotEmpty()) {
                scene.copy(mediaPath = localPath)
            } else {
                scene
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

    suspend fun downloadMediaToLocal(urlStr: String): String = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        if (urlStr.isEmpty()) return@withContext ""
        if (urlStr.startsWith("/") || urlStr.startsWith("file://") || urlStr.startsWith("content://")) {
            return@withContext urlStr
        }
        val safeUrlStr = if (urlStr.startsWith("http://")) urlStr.replace("http://", "https://") else urlStr
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
                    return@withContext existingMp4.absolutePath
                }
            }
            if (existingJpg.exists()) {
                if (existingJpg.length() < 10000) {
                    android.util.Log.w("ProjectRepository", "Existing local jpg is too small (${existingJpg.length()} bytes). Deleting.")
                    existingJpg.delete()
                } else {
                    return@withContext existingJpg.absolutePath
                }
            }

            // Migrate from legacy folder if exists there but not in the new external folder
            if (!existingMp4.exists() && legacyMp4.exists() && legacyMp4.length() > 10000) {
                try {
                    legacyMp4.copyTo(existingMp4, overwrite = true)
                    return@withContext existingMp4.absolutePath
                } catch (e: Exception) {}
            }
            if (!existingJpg.exists() && legacyJpg.exists() && legacyJpg.length() > 10000) {
                try {
                    legacyJpg.copyTo(existingJpg, overwrite = true)
                    return@withContext existingJpg.absolutePath
                } catch (e: Exception) {}
            }

            val isVid = safeUrlStr.contains(".mp4", ignoreCase = true) || safeUrlStr.contains("video", ignoreCase = true)
            val extension = if (isVid) ".mp4" else ".jpg"
            val targetFile = File(localDir, "$md5Hex$extension")

            val request = Request.Builder()
                .url(safeUrlStr)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
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
                        if (tempFile.renameTo(finalTargetFile)) {
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

    private fun md5(s: String): String {
        try {
            val digest = java.security.MessageDigest.getInstance("MD5")
            digest.update(s.toByteArray())
            val messageDigest = digest.digest()
            val hexString = StringBuilder()
            for (aMessageDigest in messageDigest) {
                var h = Integer.toHexString(0xFF and aMessageDigest.toInt())
                while (h.length < 2) h = "0$h"
                hexString.append(h)
            }
            return hexString.toString()
        } catch (e: Exception) {
            return s.hashCode().toString()
        }
    }

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
        val pexelsKey = ApiLoadBalancerService.resolvePexelsApiKey(rawPexelsKey)

        val rawPixabayKey = if (!savedPixabay.isNullOrEmpty()) {
            savedPixabay
        } else if (!buildPixabay.isNullOrEmpty() && buildPixabay != "YOUR_PIXABAY_API_KEY" && buildPixabay != "YOUR_PIYABAY_API_KEY") {
            buildPixabay
        } else {
            "56218545-6d93003a94318db8a7f29dba1"
        }
        val pixabayKey = ApiLoadBalancerService.resolvePixabayApiKey(rawPixabayKey)

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
            val queryText = if (scene.keywords.isNotEmpty() && scene.keywords[0].isNotEmpty()) {
                scene.keywords[0]
            } else if (scene.visualPrompt.isNotEmpty()) {
                scene.visualPrompt
            } else {
                "cinematic video background"
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

    suspend fun searchAlternativeSuggestions(
        query: String,
        mediaType: String,
        aspectRatio: String,
        unsplashKey: String,
        pexelsKey: String
    ): List<String> {
        return geminiService.searchAlternativeSuggestions(query, mediaType, aspectRatio, unsplashKey, pexelsKey)
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
        val clean = sentence.trim().lowercase()
        if (clean.isEmpty()) return ""
        
        // Split into words
        val parts = clean.split(Regex("\\s+"))
        if (parts.size <= 3) return clean

        val stopwords = setOf(
            "a", "an", "the", "and", "or", "but", "is", "are", "was", "were", "of", "to", "in", "on", "at", "by", "for", "with", "about",
            "this", "that", "it", "its", "you", "your", "my", "me", "we", "our", "us", "they", "them", "some", "any", "no", "not", "so",
            "can", "will", "show", "get", "make", "be", "have", "has", "had", "do", "does", "did", "from", "very", "scene", "style", "description",
            "captivating", "artistic", "representation", "masterpiece", "detail", "professional", "composition", "background", "photo", "image", "video",
            "showing", "depicting", "having", "with", "looking", "running", "walking", "sitting", "standing", "view", "concept", "concept of", "closeup",
            "close-up", "beautiful", "epic", "cinematic", "stunning", "high-quality", "high quality", "superb", "hd", "4k", "amazing", "incredible",
            "perfect", "excellent", "gorgeous", "vibrant", "dynamic", "highly", "detailed", "realistic", "photorealistic", "ultra", "realistic", "illustration",
            "drawing", "vector", "art", "graphic", "design", "subject", "ambient", "lighting", "shot", "portrait", "scenic", "view", "nature", "modern", "studio"
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
        return clean
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
