package com.ritvyom.yashoraReelgenerator.data.remote

import android.util.Log
import com.ritvyom.yashoraReelgenerator.BuildConfig
import kotlinx.coroutines.flow.first
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Enhanced Media Selection & Sourcing Engine.
 * Handles:
 * 1. Scene Type Classification (Historical Person, Historical Place, Clock/Time, Nature, etc.)
 * 2. Critical Entity Extraction & Validation (e.g., Gandhi, Godse, Patel, Birla House)
 * 3. Structured Query Generation
 * 4. Multi-candidate fetching across sources (Wikimedia, Pexels, Pixabay, Unsplash, Archive.org, AI Generation)
 * 5. Semantic Relevance Scoring (0-100 pts)
 * 6. Hard Critical Entity Filter (Rejects unrelated people/places)
 * 7. Fallback Chain & Neutral Symbolic Fallback
 * 8. Thread-safe Caching & URL Validation
 */
object MediaSelectionService {

    private const val TAG = "MediaSelectionService"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    // In-memory cache for validated media URLs
    private val mediaCache = ConcurrentHashMap<String, String>()

    fun clearCache() {
        mediaCache.clear()
    }

    enum class SceneType {
        HISTORICAL_PERSON,
        HISTORICAL_PLACE,
        HISTORICAL_EVENT,
        HISTORICAL_OBJECT,
        GENERIC_PERSON,
        GENERIC_ACTION,
        NATURE,
        ANIMAL,
        CITY,
        TRAVEL,
        PRODUCT,
        FOOD,
        TECHNOLOGY,
        DOCUMENT,
        MAP,
        CLOCK_TIME,
        ABSTRACT,
        CINEMATIC_SYMBOLIC,
        OTHER
    }

    data class CandidateMedia(
        val url: String,
        val title: String = "",
        val description: String = "",
        val tags: String = "",
        val source: String = "",
        val mediaType: String = "IMAGE", // "IMAGE" or "VIDEO"
        val width: Int = 0,
        val height: Int = 0,
        val durationSeconds: Int = 0,
        var score: Int = 0
    )

    data class EvaluationContext(
        val visualPrompt: String,
        val narrationText: String,
        val subtitle: String,
        val keywords: List<String>,
        val sceneNum: Int,
        val style: String,
        val aspectRatio: String,
        val visualMedium: String,
        val sceneType: SceneType,
        val criticalEntity: String?,
        val mandatoryTokens: List<String>,
        val customPexelsKey: String = "",
        val customPixabayKey: String = "",
        val customUnsplashKey: String = "",
        val customSpoonacularKey: String = "",
        val alreadyUsedUrls: Set<String> = emptySet()
    )

    /**
     * Resolves the best semantic media URL for a scene.
     */
    fun resolveBestMediaForScene(
        visualPrompt: String,
        style: String,
        sceneNum: Int,
        customSearchQuery: String = "",
        aspectRatio: String = "9:16",
        imageSource: String = "Unsplash",
        customUnsplashKey: String = "",
        customPexelsKey: String = "",
        customPixabayKey: String = "",
        customSpoonacularKey: String = "",
        resolution: String? = null,
        visualMedium: String = "Image",
        narrationText: String = "",
        subtitle: String = "",
        alreadyUsedUrls: Set<String> = emptySet()
    ): String {
        val combinedText = "$visualPrompt $customSearchQuery $narrationText $subtitle"
        val sceneType = classifyScene(combinedText)
        val (criticalEntity, mandatoryTokens) = extractCriticalEntityAndTokens(combinedText)

        val effectiveUnsplashKey = if (customUnsplashKey.isNotBlank()) customUnsplashKey else {
            try {
                com.ritvyom.yashoraReelgenerator.YashoraApplication.getInstance()?.preferencesManager?.run {
                    kotlinx.coroutines.runBlocking { unsplashApiKeyFlow.first() }
                } ?: ""
            } catch (e: Exception) { "" }
        }
        val effectivePexelsKey = if (customPexelsKey.isNotBlank()) customPexelsKey else {
            try {
                com.ritvyom.yashoraReelgenerator.YashoraApplication.getInstance()?.preferencesManager?.run {
                    kotlinx.coroutines.runBlocking { pexelsApiKeyFlow.first() }
                } ?: ""
            } catch (e: Exception) { "" }
        }
        val effectivePixabayKey = if (customPixabayKey.isNotBlank()) customPixabayKey else {
            try {
                com.ritvyom.yashoraReelgenerator.YashoraApplication.getInstance()?.preferencesManager?.run {
                    kotlinx.coroutines.runBlocking { pixabayApiKeyFlow.first() }
                } ?: ""
            } catch (e: Exception) { "" }
        }
        val effectiveSpoonacularKey = if (customSpoonacularKey.isNotBlank()) customSpoonacularKey else {
            try {
                com.ritvyom.yashoraReelgenerator.YashoraApplication.getInstance()?.preferencesManager?.run {
                    kotlinx.coroutines.runBlocking { spoonacularApiKeyFlow.first() }
                } ?: ""
            } catch (e: Exception) { "" }
        }

        val evalContext = EvaluationContext(
            visualPrompt = visualPrompt,
            narrationText = narrationText,
            subtitle = subtitle,
            keywords = extractKeywordsFromText(combinedText),
            sceneNum = sceneNum,
            style = style,
            aspectRatio = aspectRatio,
            visualMedium = visualMedium,
            sceneType = sceneType,
            criticalEntity = criticalEntity,
            mandatoryTokens = mandatoryTokens,
            customPexelsKey = effectivePexelsKey,
            customPixabayKey = effectivePixabayKey,
            customUnsplashKey = effectiveUnsplashKey,
            customSpoonacularKey = effectiveSpoonacularKey,
            alreadyUsedUrls = alreadyUsedUrls
        )

        val cacheKey = buildCacheKey(evalContext)
        val cachedUrl = mediaCache[cacheKey]
        if (!cachedUrl.isNullOrEmpty() && isValidUrl(cachedUrl) && cachedUrl !in alreadyUsedUrls) {
            Log.d(TAG, "Cache HIT for scene #$sceneNum ($cacheKey): $cachedUrl")
            return cachedUrl
        }

        val structuredQuery = generateStructuredQuery(evalContext, customSearchQuery)
        Log.d(TAG, "Scene #$sceneNum Type=$sceneType, CriticalEntity='$criticalEntity', Query='$structuredQuery'")

        // Build source attempt order based on SceneType
        val sourcesToTry = determineSourcePriority(sceneType, imageSource, style, criticalEntity)

        for (source in sourcesToTry) {
            val candidate = fetchAndEvaluateBestCandidate(source, structuredQuery, evalContext, customUnsplashKey)
            if (candidate != null && candidate.score >= 15) {
                Log.d(TAG, "Accepted candidate from source '${candidate.source}' with score ${candidate.score}: ${candidate.url}")
                mediaCache[cacheKey] = candidate.url
                return candidate.url
            }
        }

        // Try secondary alternate simplified query if primary search failed
        val altQuery = generateAlternateQuery(evalContext)
        if (altQuery.isNotEmpty() && altQuery != structuredQuery) {
            Log.d(TAG, "Primary queries failed. Trying alternate query: '$altQuery'")
            for (source in sourcesToTry) {
                val candidate = fetchAndEvaluateBestCandidate(source, altQuery, evalContext, customUnsplashKey)
                if (candidate != null && candidate.score >= 15) {
                    Log.d(TAG, "Accepted candidate with alt query from '${candidate.source}' (Score ${candidate.score}): ${candidate.url}")
                    mediaCache[cacheKey] = candidate.url
                    return candidate.url
                }
            }
        }

        // If no real asset passed validation (e.g. for historical figures with no verified photo), trigger AI Generation Fallback
        Log.d(TAG, "No real asset passed semantic validation for scene #$sceneNum. Triggering AI Generation Fallback.")
        val aiGeneratedUrl = generateAiImageFallback(evalContext)
        if (isValidUrl(aiGeneratedUrl)) {
            mediaCache[cacheKey] = aiGeneratedUrl
            return aiGeneratedUrl
        }

        // Neutral Symbolic Fallback (guarantees safe, high quality visual, never broken or wrong person)
        val neutralUrl = generateNeutralSymbolicFallback(evalContext)
        mediaCache[cacheKey] = neutralUrl
        return neutralUrl
    }

    // --- SCENE CLASSIFICATION ---

    fun classifyScene(text: String): SceneType {
        val lower = text.lowercase()

        // Historical Person check
        val historicalPersons = listOf(
            "gandhi", "mahatma", "patel", "vallabhbhai", "godse", "nathuram",
            "nehru", "jawaharlal", "subhash", "bose", "ambedkar", "bhagat singh",
            "lincoln", "washington", "churchill", "napoleon", "einstein", "hitler",
            "stalin", "roosevelt", "savarkar", "tagore", "lajpat raj"
        )
        if (historicalPersons.any { lower.contains(it) }) {
            return SceneType.HISTORICAL_PERSON
        }

        // Historical Place check
        val historicalPlaces = listOf(
            "birla house", "birla bhavan", "sabarmati", "red fort", "lal quila",
            "parliament", "taj mahal", "colosseum", "kremlin", "white house",
            "bastille", "normandy", "waterloo", "jallianwala"
        )
        if (historicalPlaces.any { lower.contains(it) }) {
            return SceneType.HISTORICAL_PLACE
        }

        // Historical Event check
        val historicalEvents = listOf(
            "dandi march", "salt march", "independence 1947", "partition 1947", "world war",
            "assassination of gandhi", "quit india", "french revolution", "ancient battle", "civil war"
        )
        if (historicalEvents.any { lower.contains(it) }) {
            return SceneType.HISTORICAL_EVENT
        }

        // Legal, Crime, CBI, Police, Investigation & Court check
        val crimeLegalKeywords = listOf(
            "cbi", "ed office", "police", "court", "judge", "investigation", "inquiry", "detective",
            "closure report", "chargesheet", "evidence", "witness", "arrest", "jail", "crime scene",
            "murder case", "lawyer", "advocate", "courtroom", "gavel", "सीबीआई", "पुलिस", "कोर्ट", "अदालत", "जांच",
            "सबूत", "गवाह", "फैसला", "मुकदमा", "हथकड़ी", "जेल", "तफ्तीश", "न्यायालय", "क्लोजर रिपोर्ट"
        )
        if (crimeLegalKeywords.any { lower.contains(it) }) {
            return SceneType.DOCUMENT
        }

        // Historical Object check
        val historicalObjects = listOf(
            "charkha", "spinning wheel", "vintage document", "antique pistol",
            "antique gun", "beretta", "parchment", "old diary", "ancient manuscript"
        )
        if (historicalObjects.any { lower.contains(it) }) {
            return SceneType.HISTORICAL_OBJECT
        }

        // Clock / Time check
        val clockKeywords = listOf(
            "clock", "watch", "5:15", "5.15", "time", "hourglass", "sawa paanch",
            "घड़ी", "समय", "सवा पांच", "grandfather clock", "alarm clock"
        )
        if (clockKeywords.any { lower.contains(it) }) {
            return SceneType.CLOCK_TIME
        }

        if (listOf("technology", "robot", "computer", "phone", "cyber", "ai", "code").any { lower.contains(it) }) return SceneType.TECHNOLOGY

        // Food & Recipe check - High priority check before Nature/City to avoid mistaking water/ingredients/street food as nature/road
        val foodKeywords = listOf(
            "food", "dish", "recipe", "coffee", "meal", "burger", "cook", "cooking", "chef", "ingredient", "kitchen",
            "bake", "baking", "fry", "frying", "boil", "boiling", "roast", "roasting", "simmer", "sauté", "slice",
            "slicing", "chop", "chopping", "dice", "stir", "stirring", "whisk", "pan", "pot", "skillet", "stove", "oven",
            "spices", "seasoning", "salt", "pepper", "turmeric", "cumin", "coriander", "ginger", "garlic", "onion", "tomato",
            "vegetable", "veggie", "curry", "rice", "biryani", "pasta", "pizza", "noodles", "paneer", "chicken", "mutton",
            "fish", "soup", "salad", "dessert", "cake", "pastry", "cookie", "breakfast", "lunch", "dinner", "snack",
            "tasty", "delicious", "flavor", "taste", "dough", "batter", "tea", "chai", "platter", "culinary", "gourmet",
            "रेसिपी", "खाना", "भोजन", "पकवान", "रसोई", "शेफ", "पकाना", "पकाएं", "पकाते", "तलना", "तलें", "भूनना", "भूनें",
            "उबालना", "उबालें", "काटना", "काटें", "सामग्री", "तेल", "घी", "मक्खन", "मसाले", "मसाला", "नमक", "मिर्च", "हल्दी",
            "धनिया", "जीरा", "प्याज", "लहसुन", "अदरक", "टमाटर", "सब्जी", "दाल", "चावल", "रोटी", "पनीर", "बिरयानी", "पुलाव",
            "चिकन", "हलवा", "खीर", "मिठाई", "समोसा", "पकौड़ा", "डोसा", "इडली", "सूप", "सलाद", "कढ़ाई", "तवा", "कुकर",
            "आंच", "धीमी आंच", "गार्निश", "स्वाद", "स्वादिष्ट", "जायका", "नाश्ता"
        )
        if (foodKeywords.any { lower.contains(it) }) {
            return SceneType.FOOD
        }

        // Nature check
        if (listOf("mountain", "river", "ocean", "beach", "forest", "tree", "sky", "sunset", "nature", "flower", "rose", "jungle").any { lower.contains(it) }) {
            return SceneType.NATURE
        }

        // Animal check
        if (listOf("lion", "tiger", "dog", "cat", "bird", "elephant", "horse", "animal", "wildlife", "bird").any { lower.contains(it) }) {
            return SceneType.ANIMAL
        }

        // City / Travel check
        if (listOf("city", "street", "building", "skyscraper", "traffic", "travel", "flight", "road", "tokyo", "paris", "delhi", "mumbai", "york").any { lower.contains(it) }) {
            return SceneType.CITY
        }

        return SceneType.CINEMATIC_SYMBOLIC
    }

    // --- CRITICAL ENTITY EXTRACTION ---

    private fun extractCriticalEntityAndTokens(text: String): Pair<String?, List<String>> {
        val lower = text.lowercase()

        if (lower.contains("gandhi") || lower.contains("mahatma")) {
            return Pair("Mahatma Gandhi", listOf("gandhi", "mahatma"))
        }
        if (lower.contains("godse") || lower.contains("nathuram")) {
            return Pair("Nathuram Godse", listOf("godse", "nathuram"))
        }
        if (lower.contains("patel") || lower.contains("vallabhbhai")) {
            return Pair("Sardar Vallabhbhai Patel", listOf("patel", "vallabhbhai", "sardar"))
        }
        if (lower.contains("nehru") || lower.contains("jawaharlal")) {
            return Pair("Jawaharlal Nehru", listOf("nehru", "jawaharlal"))
        }
        if (lower.contains("bose") || lower.contains("subhash")) {
            return Pair("Subhash Chandra Bose", listOf("bose", "subhash"))
        }
        if (lower.contains("birla house") || lower.contains("birla bhavan")) {
            return Pair("Birla House", listOf("birla", "house", "bhavan"))
        }
        if (lower.contains("5:15") || lower.contains("sawa paanch") || lower.contains("5.15")) {
            return Pair("Clock 5:15", listOf("clock", "time", "watch"))
        }

        return Pair(null, emptyList())
    }

    // --- STRUCTURED QUERY GENERATOR ---

    fun generateStructuredQuery(evalContext: EvaluationContext, customQuery: String): String {
        if (customQuery.isNotEmpty() && customQuery.length > 3) {
            return customQuery.trim()
        }

        val entity = evalContext.criticalEntity
        if (!entity.isNullOrEmpty()) {
            return when (entity) {
                "Mahatma Gandhi" -> "Mahatma Gandhi walking prayer meeting Birla House 1948 historical"
                "Nathuram Godse" -> "Nathuram Godse historical documentary"
                "Sardar Vallabhbhai Patel" -> "Sardar Vallabhbhai Patel historical documentary"
                "Jawaharlal Nehru" -> "Jawaharlal Nehru historical documentary"
                "Subhash Chandra Bose" -> "Subhash Chandra Bose historical documentary"
                "Birla House" -> "Birla House New Delhi 1948 Gandhi memorial historical"
                "Clock 5:15" -> "vintage grandfather clock 5:15 PM antique"
                else -> "$entity historical documentary"
            }
        }

        return when (evalContext.sceneType) {
            SceneType.CLOCK_TIME -> "vintage grandfather clock 5:15 PM antique"
            SceneType.HISTORICAL_EVENT -> {
                val kw = evalContext.keywords.take(3).joinToString(" ")
                if (kw.isNotEmpty()) "$kw historical documentary archive" else "historical documentary 1948 archive"
            }
            SceneType.HISTORICAL_OBJECT -> "vintage antique historical object"
            SceneType.DOCUMENT -> {
                val kw = evalContext.keywords.filter { k ->
                    val l = k.lowercase().trim()
                    l.isNotEmpty() && l != "cinematic" && l != "scene" && l != "visual" && l != "cinematic visual" && l != "cinematic narrative scene"
                }.take(3).joinToString(" ")
                if (kw.isNotEmpty()) kw else "detective investigation court case files"
            }
            SceneType.FOOD -> {
                val kw = evalContext.keywords.take(3).joinToString(" ")
                if (kw.isNotEmpty()) "$kw cooking food recipe dish kitchen" else "delicious culinary recipe cooking food dish"
            }
            SceneType.NATURE -> {
                val kw = evalContext.keywords.take(3).joinToString(" ")
                if (kw.isNotEmpty()) "$kw nature background" else "scenic nature landscape"
            }
            SceneType.CITY -> {
                val kw = evalContext.keywords.take(3).joinToString(" ")
                if (kw.isNotEmpty()) "$kw city street" else "modern city urban street"
            }
            else -> {
                val words = evalContext.keywords.filter { kw ->
                    val l = kw.lowercase().trim()
                    l.isNotEmpty() && l != "cinematic" && l != "narrative" && l != "scene" && l != "visual" && l != "cinematic narrative scene" && l != "cinematic visual"
                }.take(3)
                if (words.isNotEmpty()) {
                    words.joinToString(" ")
                } else {
                    val fallback = GeminiService.extractSemiSemanticKeyword(
                        evalContext.narrationText.ifEmpty { evalContext.subtitle }
                    )
                    if (fallback.isNotEmpty() && !fallback.contains("cinematic narrative scene")) {
                        fallback
                    } else {
                        "documentary news investigation footage"
                    }
                }
            }
        }
    }

    private fun generateAlternateQuery(evalContext: EvaluationContext): String {
        val entity = evalContext.criticalEntity
        if (!entity.isNullOrEmpty()) {
            return "$entity historical"
        }
        if (evalContext.sceneType == SceneType.FOOD) {
            val kw = evalContext.keywords.take(2).joinToString(" ")
            return if (kw.isNotEmpty()) "$kw recipe food" else "gourmet recipe dish"
        }
        return evalContext.keywords.take(2).joinToString(" ")
    }

    // --- SOURCE PRIORITY ---

    private fun determineSourcePriority(
        sceneType: SceneType,
        userPreferredSource: String,
        style: String,
        criticalEntity: String? = null
    ): List<String> {
        val userSource = cleanSourceName(userPreferredSource)
        val isStylized = style.lowercase().let {
            it.contains("anime") || it.contains("cartoon") || it.contains("3d") ||
                    it.contains("painting") || it.contains("sketch") || it.contains("cyberpunk")
        }

        val baseList = if (isStylized) {
            mutableListOf("Nekos.best", "Jikan Anime", "AI Generated", "Pixabay", "Pexels", "Wikimedia Commons")
        } else {
            when (sceneType) {
                SceneType.FOOD -> {
                    mutableListOf("TheMealDB", "Foodish", "Pexels", "Pixabay", "Unsplash", "AI Generated")
                }
                SceneType.HISTORICAL_PERSON, SceneType.HISTORICAL_PLACE, SceneType.HISTORICAL_EVENT, SceneType.HISTORICAL_OBJECT -> {
                    if (!criticalEntity.isNullOrEmpty()) {
                        mutableListOf("Wikimedia Commons", "Archive.org", "AI Generated", "Pexels", "Pixabay", "Unsplash")
                    } else {
                        mutableListOf("Pexels", "Pixabay", "Unsplash", "Wikimedia Commons", "Archive.org", "AI Generated")
                    }
                }
                else -> {
                    mutableListOf("Pexels", "Pixabay", "Unsplash", "Wikimedia Commons", "AI Generated")
                }
            }
        }

        if (userSource.isNotEmpty()) {
            if (baseList.contains(userSource)) {
                baseList.remove(userSource)
            }
            baseList.add(0, userSource)
        }

        return baseList
    }

    private fun cleanSourceName(src: String): String {
        val lower = src.lowercase().trim()
        return when {
            lower.contains("themealdb") || lower.contains("mealdb") || lower.contains("recipe") -> "TheMealDB"
            lower.contains("foodish") || lower.contains("food") -> "Foodish"
            lower.contains("pexels") -> "Pexels"
            lower.contains("pixabay") -> "Pixabay"
            lower.contains("unsplash") -> "Unsplash"
            lower.contains("wikimedia") || lower.contains("commons") || lower.contains("wikipedia") || lower.contains("wiki") -> "Wikimedia Commons"
            lower.contains("archive") -> "Archive.org"
            lower.contains("nasa") || lower.contains("space") -> "NASA Library"
            lower.contains("spoonacular") || lower.contains("culinary") -> "Spoonacular"
            lower.contains("jikan") || lower.contains("mal") -> "Jikan Anime"
            lower.contains("nekos") || lower.contains("anime") -> "Nekos.best"
            lower.contains("giphy") || lower.contains("gif") || lower.contains("meme") -> "Giphy Memes"
            lower.contains("picsum") || (lower.contains("lorem") && !lower.contains("flickr")) || lower.contains("scenic") -> "Lorem Picsum"
            lower.contains("flickr") || lower.contains("lumina") -> "LoremFlickr"
            lower.contains("fallback") || lower.contains("omnishield") -> "11-Stage Fallback"
            lower.contains("nhtsa") || lower.contains("car") || lower.contains("vehicle") -> "NHTSA vPIC"
            lower.contains("openfda") || lower.contains("medicine") || lower.contains("drug") -> "OpenFDA"
            lower.contains("who") || lower.contains("health") -> "WHO"
            lower.contains("ai") || lower.contains("generated") || lower.contains("pollinations") || lower.contains("synthetix") -> "AI Generated"
            else -> ""
        }
    }

    // --- MULTI-CANDIDATE FETCHING & EVALUATION ---

    private fun fetchAndEvaluateBestCandidate(
        source: String,
        query: String,
        evalContext: EvaluationContext,
        customUnsplashKey: String
    ): CandidateMedia? {
        val candidates = when (source) {
            "TheMealDB" -> fetchTheMealDbCandidates(query, evalContext.sceneNum)
            "Foodish" -> fetchFoodishCandidates(query, evalContext.sceneNum)
            "Wikimedia Commons", "Wikipedia", "Wikimedia" -> fetchWikimediaCandidates(query, evalContext.sceneNum)
            "Pexels" -> fetchPexelsCandidates(query, evalContext)
            "Pixabay" -> fetchPixabayCandidates(query, evalContext)
            "Unsplash" -> fetchUnsplashCandidates(query, customUnsplashKey, evalContext)
            "Archive.org" -> fetchArchiveOrgCandidates(query, evalContext.sceneNum)
            "NASA Library" -> fetchNasaCandidates(query, evalContext)
            "Spoonacular" -> fetchSpoonacularCandidates(query, evalContext)
            "Jikan Anime" -> fetchJikanAnimeCandidates(query, evalContext.sceneNum)
            "Nekos.best" -> fetchNekosBestCandidates(query, evalContext.sceneNum)
            "Giphy", "Giphy Memes" -> fetchGiphyCandidates(query, evalContext.sceneNum)
            "Lorem Picsum" -> fetchLoremPicsumCandidates(query, evalContext)
            "LoremFlickr" -> fetchLoremFlickrCandidates(query, evalContext)
            "11-Stage Fallback", "OmniShield Fallback" -> fetchOmniShieldFallbackCandidates(query, evalContext)
            "NHTSA vPIC" -> fetchNhtsaCandidates(query, evalContext.sceneNum)
            "OpenFDA" -> fetchOpenFdaCandidates(query, evalContext.sceneNum)
            "WHO" -> fetchWhoCandidates(query, evalContext.sceneNum)
            "AI Generated" -> {
                val url = generateAiImageFallback(evalContext)
                if (isValidUrl(url)) {
                    return CandidateMedia(
                        url = url,
                        title = "AI Generated Visual",
                        source = "AI Generated",
                        score = 85
                    )
                } else null
            }
            else -> emptyList()
        }

        if (candidates.isNullOrEmpty()) return null

        // Evaluate all candidates and score them
        var bestCandidate: CandidateMedia? = null
        var maxScore = -1

        for (candidate in candidates) {
            val score = evaluateCandidate(candidate, evalContext)
            candidate.score = score
            val isDuplicate = evalContext.alreadyUsedUrls.contains(candidate.url)
            val effectiveScore = if (isDuplicate) score - 200 else score
            Log.d(TAG, "Source '$source' Candidate '${candidate.title}' Score: $score (effective: $effectiveScore, dup=$isDuplicate, URL: ${candidate.url.take(50)}...)")
            if (effectiveScore > maxScore) {
                maxScore = effectiveScore
                bestCandidate = candidate
            }
        }

        return if (maxScore >= 15) bestCandidate else null
    }

    // --- CANDIDATE EVALUATION & SCORING ---

    fun evaluateCandidate(candidate: CandidateMedia, context: EvaluationContext): Int {
        val metaText = "${candidate.title} ${candidate.description} ${candidate.tags} ${candidate.url}".lowercase()

        // 1. HARD CRITICAL ENTITY FILTER (Instant rejection if violated for historical entities)
        if (!context.criticalEntity.isNullOrEmpty() && context.mandatoryTokens.isNotEmpty()) {
            val matchesMandatory = context.mandatoryTokens.any { metaText.contains(it) }
            if (!matchesMandatory) {
                return 0
            }
        }

        var score = 0

        // Subject Match (35 pts)
        if (!context.criticalEntity.isNullOrEmpty()) {
            val matchCount = context.mandatoryTokens.count { metaText.contains(it) }
            val subjectPoints = ((matchCount.toFloat() / context.mandatoryTokens.size) * 35).toInt()
            score += subjectPoints.coerceIn(0, 35)
        } else {
            val searchTokens = context.keywords.flatMap { it.lowercase().split(Regex("\\s+")) }
                .filter { it.length > 2 && it !in setOf("and", "the", "for", "with", "this", "that") }
                .distinct()
            if (searchTokens.isNotEmpty()) {
                val matches = searchTokens.count { metaText.contains(it) }
                if (matches > 0) {
                    val ratio = (matches.toFloat() / searchTokens.size.coerceAtMost(3)).coerceIn(0.4f, 1.0f)
                    score += (ratio * 35).toInt()
                } else {
                    score += 15 // Candidate returned by API for this query
                }
            } else {
                score += 25
            }
        }

        // Visual Prompt Match (30 pts)
        val promptWords = context.visualPrompt.lowercase()
            .replace(Regex("[^\\p{L}\\p{N}\\s]"), " ")
            .split(Regex("\\s+"))
            .filter { it.length > 3 && it !in setOf("captivating", "artistic", "representation", "style", "cinematic", "scenery", "beautiful") }
            .distinct()
        if (promptWords.isNotEmpty()) {
            val matchedPromptWords = promptWords.count { metaText.contains(it) }
            if (matchedPromptWords > 0) {
                val promptRatio = (matchedPromptWords.toFloat() / promptWords.size.coerceAtMost(3)).coerceIn(0.5f, 1.0f)
                score += (promptRatio * 30).toInt()
            } else {
                score += 15
            }
        } else {
            score += 20
        }

        // Scene Context Match (15 pts)
        val contextWords = "${context.narrationText} ${context.subtitle}".lowercase()
            .replace(Regex("[^\\p{L}\\p{N}\\s]"), " ")
            .split(Regex("\\s+"))
            .filter { it.length > 3 }
            .distinct()
        if (contextWords.isNotEmpty()) {
            val matchedContextWords = contextWords.count { metaText.contains(it) }
            if (matchedContextWords > 0) {
                val contextRatio = (matchedContextWords.toFloat() / contextWords.size.coerceAtMost(3)).coerceIn(0.3f, 1.0f)
                score += (contextRatio * 15).toInt()
            } else {
                score += 8
            }
        } else {
            score += 10
        }

        // Media Type Match (10 pts)
        val isVideoRequested = context.visualMedium.equals("Video", ignoreCase = true)
        if (isVideoRequested && candidate.mediaType == "VIDEO") {
            score += 10
        } else if (!isVideoRequested && candidate.mediaType == "IMAGE") {
            score += 10
        } else {
            score += 5 // acceptable fallback
        }

        // Quality / Format Specs (5 pts)
        if (isValidUrl(candidate.url)) {
            score += 5
        }

        // Source Credibility / Duration match (5 pts)
        if ((candidate.source == "Wikimedia Commons" || candidate.source == "Wikipedia") &&
            (context.sceneType == SceneType.HISTORICAL_PERSON || context.sceneType == SceneType.HISTORICAL_PLACE ||
             context.sceneType == SceneType.HISTORICAL_EVENT || context.sceneType == SceneType.HISTORICAL_OBJECT)) {
            score += 15
        } else if ((candidate.source == "TheMealDB" || candidate.source == "Foodish") && context.sceneType == SceneType.FOOD) {
            score += 15
        } else {
            score += 5
        }

        // Food & Culinary context bonus (10 pts)
        if (context.sceneType == SceneType.FOOD) {
            val foodTokens = listOf("food", "recipe", "dish", "cook", "kitchen", "meal", "tasty", "delicious", "culinary", "ingredient", "spice", "curry", "rice", "cake", "salad", "soup")
            if (foodTokens.any { metaText.contains(it) }) {
                score += 10
            }
        }

        return score.coerceIn(0, 100)
    }

    // --- SOURCE CANDIDATE FETCHERS ---

    private fun fetchTheMealDbCandidates(query: String, sceneNum: Int): List<CandidateMedia> {
        val list = mutableListOf<CandidateMedia>()
        try {
            val qClean = query.replace(Regex("[^\\p{L}\\p{N}\\s]"), "").trim()
            val words = qClean.split(Regex("\\s+")).filter { it.length > 2 }
            val searchTerm = words.firstOrNull { !listOf("recipe", "food", "dish", "cook", "cooking", "delicious", "kitchen", "step", "easy", "make").contains(it.lowercase()) }
                ?: words.firstOrNull() ?: "curry"

            val encodedQuery = URLEncoder.encode(searchTerm, "UTF-8")
            val url = "https://www.themealdb.com/api/json/v1/1/search.php?s=$encodedQuery"
            val request = Request.Builder().url(url).header("User-Agent", "Mozilla/5.0").build()
            val response = httpClient.newCall(request).execute()
            if (response.isSuccessful) {
                val body = response.body?.string() ?: ""
                val json = JSONObject(body)
                val meals = json.optJSONArray("meals")
                if (meals != null && meals.length() > 0) {
                    for (i in 0 until meals.length()) {
                        val mealObj = meals.optJSONObject(i) ?: continue
                        val thumb = mealObj.optString("strMealThumb")
                        val mealName = mealObj.optString("strMeal")
                        val category = mealObj.optString("strCategory")
                        if (!thumb.isNullOrEmpty()) {
                            val safeThumb = if (thumb.startsWith("http://")) thumb.replace("http://", "https://") else thumb
                            list.add(
                                CandidateMedia(
                                    url = safeThumb,
                                    title = "$mealName ($category)",
                                    description = "TheMealDB recipe photo for $mealName",
                                    tags = "$mealName $category recipe food cooking dish culinary meal",
                                    source = "TheMealDB",
                                    mediaType = "IMAGE"
                                )
                            )
                        }
                    }
                }
            }

            if (list.isEmpty()) {
                val categories = listOf("Vegetarian", "Chicken", "Dessert", "Pasta", "Seafood", "Breakfast", "Starter", "Side")
                val selectedCategory = categories[sceneNum % categories.size]
                val catUrl = "https://www.themealdb.com/api/json/v1/1/filter.php?c=$selectedCategory"
                val catReq = Request.Builder().url(catUrl).header("User-Agent", "Mozilla/5.0").build()
                val catResp = httpClient.newCall(catReq).execute()
                if (catResp.isSuccessful) {
                    val catBody = catResp.body?.string() ?: ""
                    val catJson = JSONObject(catBody)
                    val catMeals = catJson.optJSONArray("meals")
                    if (catMeals != null && catMeals.length() > 0) {
                        val maxItems = minOf(catMeals.length(), 8)
                        for (idx in 0 until maxItems) {
                            val mealObj = catMeals.optJSONObject(idx) ?: continue
                            val thumb = mealObj.optString("strMealThumb")
                            val mealName = mealObj.optString("strMeal") ?: "Recipe Dish"
                            if (!thumb.isNullOrEmpty()) {
                                val safeThumb = if (thumb.startsWith("http://")) thumb.replace("http://", "https://") else thumb
                                list.add(
                                    CandidateMedia(
                                        url = safeThumb,
                                        title = "$mealName ($selectedCategory)",
                                        description = "TheMealDB $selectedCategory recipe photo",
                                        tags = "$mealName $selectedCategory recipe food cooking dish culinary",
                                        source = "TheMealDB",
                                        mediaType = "IMAGE"
                                    )
                                )
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching TheMealDB candidates", e)
        }
        return list
    }

    private fun fetchFoodishCandidates(query: String, sceneNum: Int): List<CandidateMedia> {
        val list = mutableListOf<CandidateMedia>()
        try {
            val lower = query.lowercase()
            // Map keywords to reliable TheMealDB categories to ensure high quality culinary photos
            val category = when {
                lower.contains("dessert") || lower.contains("sweet") || lower.contains("cake") || lower.contains("मिठाई") || lower.contains("हलवा") -> "Dessert"
                lower.contains("pasta") || lower.contains("noodles") || lower.contains("पास्ता") -> "Pasta"
                lower.contains("seafood") || lower.contains("fish") || lower.contains("मछली") -> "Seafood"
                lower.contains("vegan") || lower.contains("vegetarian") || lower.contains("paneer") || lower.contains("सब्जी") || lower.contains("पनीर") -> "Vegetarian"
                lower.contains("breakfast") || lower.contains("snack") || lower.contains("नाश्ता") -> "Breakfast"
                lower.contains("chicken") || lower.contains("चिकन") || lower.contains("biryani") || lower.contains("बिरयानी") -> "Chicken"
                else -> "Miscellaneous"
            }
            val url = "https://www.themealdb.com/api/json/v1/1/filter.php?c=$category"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "YashoraReelGenerator/2.0 (Ritvyom; Suryadev Nishad; Ritvyom@gmail.com) Android/14 OkHttp/4.12")
                .build()
            val response = httpClient.newCall(request).execute()
            if (response.isSuccessful) {
                val body = response.body?.string() ?: ""
                val json = JSONObject(body)
                val meals = json.optJSONArray("meals")
                if (meals != null && meals.length() > 0) {
                    for (i in 0 until minOf(meals.length(), 6)) {
                        val mealObj = meals.optJSONObject(i) ?: continue
                        val thumb = mealObj.optString("strMealThumb")
                        val mealName = mealObj.optString("strMeal", "Food Dish")
                        if (thumb.isNotEmpty()) {
                            val safeThumb = if (thumb.startsWith("http://")) thumb.replace("http://", "https://") else thumb
                            list.add(
                                CandidateMedia(
                                    url = safeThumb,
                                    title = mealName,
                                    description = "Authentic recipe photo of $mealName",
                                    tags = "$category food recipe dish culinary cooking gourmet meal",
                                    source = "Foodish",
                                    mediaType = "IMAGE",
                                    score = 80
                                )
                            )
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching Foodish/MealDB candidates", e)
        }
        return list
    }

    private fun fetchWikimediaCandidates(query: String, sceneNum: Int): List<CandidateMedia> {
        val list = mutableListOf<CandidateMedia>()
        val userAgent = "YashoraReelGenerator/2.0 (https://ai.studio; yashoratechnologies@gmail.com) Android/14 OkHttp/4.12"
        val qClean = query.replace(Regex("[^\\p{L}\\p{N}\\s]"), " ").trim()
        if (qClean.isEmpty()) return list

        // 1. Wikipedia Entity Article Images (High-quality, verified portraits and historical photos)
        try {
            val hasDevanagari = qClean.any { it in '\u0900'..'\u097F' }
            val endpoints = if (hasDevanagari) {
                listOf("https://hi.wikipedia.org/w/api.php", "https://en.wikipedia.org/w/api.php")
            } else {
                listOf("https://en.wikipedia.org/w/api.php", "https://hi.wikipedia.org/w/api.php")
            }
            val encoded = URLEncoder.encode(qClean, "UTF-8")
            for (wikiBase in endpoints) {
                if (list.size >= 6) break
                val wikiUrl = "$wikiBase?action=query&generator=search&gsrsearch=$encoded&gsrlimit=10&prop=pageimages|description&pilimit=20&pithumbsize=1280&redirects=1&format=json&origin=*"
                val wikiReq = Request.Builder().url(wikiUrl).header("User-Agent", userAgent).build()
                httpClient.newCall(wikiReq).execute().use { wikiResp ->
                    if (wikiResp.isSuccessful) {
                        val body = wikiResp.body?.string() ?: ""
                        val pages = JSONObject(body).optJSONObject("query")?.optJSONObject("pages")
                        if (pages != null) {
                            val keys = pages.keys()
                            val pageList = mutableListOf<JSONObject>()
                            while (keys.hasNext()) {
                                val pageObj = pages.optJSONObject(keys.next()) ?: continue
                                if (pageObj.optInt("pageid", 0) > 0) {
                                    pageList.add(pageObj)
                                }
                            }
                            pageList.sortBy { it.optInt("index", 99) }
                            for (page in pageList) {
                                val title = page.optString("title", "Wikipedia Article")
                                val desc = page.optString("description", "")
                                val thumb = page.optJSONObject("thumbnail")?.optString("source") ?: ""
                                if (thumb.isNotEmpty() && !thumb.endsWith(".svg", true) && !thumb.endsWith(".pdf", true)) {
                                    val safeThumb = if (thumb.startsWith("http://")) thumb.replace("http://", "https://") else thumb
                                    if (list.none { it.url == safeThumb }) {
                                        val displayDesc = if (desc.isNotBlank()) desc else "Wikipedia historical article photo of $title"
                                        list.add(
                                            CandidateMedia(
                                                url = safeThumb,
                                                title = title,
                                                description = displayDesc,
                                                tags = "wikipedia historical document biography archive $title $qClean",
                                                source = "Wikipedia",
                                                mediaType = "IMAGE",
                                                score = 88
                                            )
                                        )
                                    }
                                }
                                if (list.size >= 6) break
                            }
                        }
                    } else {
                        Log.w(TAG, "Wikipedia Entity API returned HTTP ${wikiResp.code} for '$qClean' at $wikiBase")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error querying Wikipedia Entity API in fetchWikimediaCandidates for '$qClean'", e)
        }

        // 2. Wikimedia Commons Public Domain & Creative Commons Media
        try {
            val encoded = URLEncoder.encode(qClean, "UTF-8")
            val url = "https://commons.wikimedia.org/w/api.php?action=query&format=json&generator=search&gsrsearch=$encoded&gsrnamespace=6&prop=imageinfo&iiprop=url|size|mime&iiurlwidth=1280&gsrlimit=15&origin=*"
            val request = Request.Builder().url(url).header("User-Agent", userAgent).build()
            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val bodyString = response.body?.string() ?: ""
                    val json = JSONObject(bodyString)
                    val pages = json.optJSONObject("query")?.optJSONObject("pages")
                    if (pages != null) {
                        val keys = pages.keys()
                        while (keys.hasNext()) {
                            val key = keys.next()
                            val pageObj = pages.optJSONObject(key) ?: continue
                            val title = pageObj.optString("title", "").replace("File:", "").replace(Regex("\\.[a-zA-Z0-9]+$"), "").replace('_', ' ')
                            val imageinfo = pageObj.optJSONArray("imageinfo")
                            if (imageinfo != null && imageinfo.length() > 0) {
                                val infoObj = imageinfo.optJSONObject(0) ?: continue
                                val mime = infoObj.optString("mime", "").lowercase()
                                val directUrl = infoObj.optString("url") ?: ""
                                val thumbUrl = infoObj.optString("thumburl") ?: directUrl
                                val chosenUrl = thumbUrl.ifEmpty { directUrl }
                                
                                val isBadMime = mime.contains("svg") || mime.contains("pdf") || mime.contains("tiff") || mime.contains("djvu")
                                val isBadExt = chosenUrl.endsWith(".svg", true) || chosenUrl.endsWith(".pdf", true) || chosenUrl.endsWith(".tif", true) || chosenUrl.endsWith(".tiff", true)
                                
                                if (chosenUrl.isNotEmpty() && isValidUrl(chosenUrl) && !isBadMime && !isBadExt) {
                                    val safeUrl = if (chosenUrl.startsWith("http://")) chosenUrl.replace("http://", "https://") else chosenUrl
                                    if (list.none { it.url == safeUrl }) {
                                        list.add(
                                            CandidateMedia(
                                                url = safeUrl,
                                                title = title.ifBlank { "Wikimedia Commons Media" },
                                                description = "Wikimedia Commons historical archive: $title",
                                                tags = "wikimedia historical public domain archive $title $qClean",
                                                source = "Wikimedia Commons",
                                                mediaType = "IMAGE",
                                                score = 78
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else {
                    Log.w(TAG, "Wikimedia Commons returned HTTP ${response.code} for '$qClean'")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching Wikimedia Commons candidates for '$qClean'", e)
        }
        return list
    }

    private fun fetchPexelsCandidates(query: String, context: EvaluationContext): List<CandidateMedia> {
        val list = mutableListOf<CandidateMedia>()
        val userKey = context.customPexelsKey.trim()
        val defaultBackupKey = try {
            ApiLoadBalancerService.resolvePexelsApiKey(BuildConfig.PEXELS_API_KEY)
        } catch (e: Exception) {
            ApiLoadBalancerService.resolvePexelsApiKey("")
        }

        val keysToTry = mutableListOf<String>()
        if (userKey.isNotEmpty() && userKey.length >= 16) {
            keysToTry.add(userKey)
        }
        if (defaultBackupKey.isNotEmpty() && !keysToTry.contains(defaultBackupKey)) {
            keysToTry.add(defaultBackupKey)
        }
        if (keysToTry.isEmpty()) return list

        val isVideo = context.visualMedium.equals("Video", ignoreCase = true)
        val orientation = when (context.aspectRatio) {
            "9:16", "4:5", "3:4" -> "portrait"
            "16:9", "21:9" -> "landscape"
            else -> "square"
        }
        val queryText = query.replace(Regex("[^\\p{L}\\p{N}\\s]"), "")
        val encodedQuery = URLEncoder.encode(queryText, "UTF-8")

        for (pexelsKey in keysToTry) {
            try {
                val url = if (isVideo) {
                    "https://api.pexels.com/videos/search?query=$encodedQuery&orientation=$orientation&per_page=10"
                } else {
                    "https://api.pexels.com/v1/search?query=$encodedQuery&orientation=$orientation&per_page=10"
                }

                val request = Request.Builder()
                    .url(url)
                    .header("Authorization", pexelsKey)
                    .header("User-Agent", "Mozilla/5.0")
                    .build()

                val response = httpClient.newCall(request).execute()
                if (response.isSuccessful) {
                    val bodyString = response.body?.string() ?: ""
                    val json = JSONObject(bodyString)

                    if (isVideo) {
                        val videos = json.optJSONArray("videos")
                        if (videos != null) {
                            for (i in 0 until videos.length()) {
                                val videoObj = videos.optJSONObject(i) ?: continue
                                val userObj = videoObj.optJSONObject("user")
                                val userName = userObj?.optString("name") ?: ""
                                val videoFiles = videoObj.optJSONArray("video_files")
                                var mp4Url = ""
                                if (videoFiles != null) {
                                    for (vIdx in 0 until videoFiles.length()) {
                                        val fileObj = videoFiles.optJSONObject(vIdx) ?: continue
                                        val link = fileObj.optString("link")
                                        if (!link.isNullOrEmpty() && link.contains(".mp4")) {
                                            mp4Url = if (link.startsWith("http://")) link.replace("http://", "https://") else link
                                            break
                                        }
                                    }
                                }
                                if (mp4Url.isEmpty()) {
                                    mp4Url = videoObj.optString("image")
                                }
                                if (mp4Url.isNotEmpty()) {
                                    list.add(
                                        CandidateMedia(
                                            url = mp4Url,
                                            title = "Pexels Video by $userName",
                                            tags = queryText,
                                            source = "Pexels",
                                            mediaType = "VIDEO"
                                        )
                                    )
                                }
                            }
                        }
                    } else {
                        val photos = json.optJSONArray("photos")
                        if (photos != null) {
                            for (i in 0 until photos.length()) {
                                val photoObj = photos.optJSONObject(i) ?: continue
                                val alt = photoObj.optString("alt", "")
                                val srcObj = photoObj.optJSONObject("src")
                                val imageUrl = srcObj?.optString("original") ?: srcObj?.optString("large")
                                if (!imageUrl.isNullOrEmpty()) {
                                    val safeUrl = if (imageUrl.startsWith("http://")) imageUrl.replace("http://", "https://") else imageUrl
                                    list.add(
                                        CandidateMedia(
                                            url = safeUrl,
                                            title = alt,
                                            description = alt,
                                            tags = queryText,
                                            source = "Pexels",
                                            mediaType = "IMAGE"
                                        )
                                    )
                                }
                            }
                        }
                    }
                    if (list.isNotEmpty()) {
                        return list
                    }
                } else {
                    Log.w(TAG, "Pexels key failed with code ${response.code}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching Pexels candidates with key", e)
            }
        }
        return list
    }

    private fun fetchPixabayCandidates(query: String, context: EvaluationContext): List<CandidateMedia> {
        val list = mutableListOf<CandidateMedia>()
        val userKey = context.customPixabayKey.trim()
        val defaultBackupKey = try {
            ApiLoadBalancerService.resolvePixabayApiKey(BuildConfig.PIXABAY_API_KEY)
        } catch (e: Exception) {
            ApiLoadBalancerService.resolvePixabayApiKey("")
        }

        val keysToTry = mutableListOf<String>()
        if (userKey.isNotEmpty() && userKey.length >= 16) {
            keysToTry.add(userKey)
        }
        if (defaultBackupKey.isNotEmpty() && !keysToTry.contains(defaultBackupKey)) {
            keysToTry.add(defaultBackupKey)
        }
        if (keysToTry.isEmpty()) return list

        val isVideo = context.visualMedium.equals("Video", ignoreCase = true)
        val orientation = when (context.aspectRatio) {
            "9:16", "4:5", "3:4" -> "vertical"
            "16:9", "21:9" -> "horizontal"
            else -> "all"
        }
        val queryText = query.replace(Regex("[^\\p{L}\\p{N}\\s]"), "")
        val encodedQuery = URLEncoder.encode(queryText, "UTF-8")

        for (pixabayKey in keysToTry) {
            try {
                val url = if (isVideo) {
                    "https://pixabay.com/api/videos/?key=$pixabayKey&q=$encodedQuery&orientation=$orientation&per_page=10"
                } else {
                    "https://pixabay.com/api/?key=$pixabayKey&q=$encodedQuery&image_type=photo&orientation=$orientation&per_page=10"
                }

                val request = Request.Builder().url(url).header("User-Agent", "Mozilla/5.0").build()
                val response = httpClient.newCall(request).execute()
                if (response.isSuccessful) {
                    val bodyString = response.body?.string() ?: ""
                    val json = JSONObject(bodyString)
                    val hits = json.optJSONArray("hits")
                    if (hits != null) {
                        for (i in 0 until hits.length()) {
                            val hitObj = hits.optJSONObject(i) ?: continue
                            val tags = hitObj.optString("tags", "")
                            val imageUrl = hitObj.optString("largeImageURL") ?: hitObj.optString("webformatURL")
                            if (!imageUrl.isNullOrEmpty()) {
                                val safeUrl = if (imageUrl.startsWith("http://")) imageUrl.replace("http://", "https://") else imageUrl
                                list.add(
                                    CandidateMedia(
                                        url = safeUrl,
                                        title = tags,
                                        tags = tags,
                                        source = "Pixabay",
                                        mediaType = if (isVideo) "VIDEO" else "IMAGE"
                                    )
                                )
                            }
                        }
                    }
                    if (list.isNotEmpty()) {
                        return list
                    }
                } else {
                    Log.w(TAG, "Pixabay key failed with code ${response.code}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching Pixabay candidates with key", e)
            }
        }
        return list
    }

    private fun fetchUnsplashCandidates(query: String, customUnsplashKey: String, context: EvaluationContext): List<CandidateMedia> {
        val list = mutableListOf<CandidateMedia>()
        val userKey = (if (customUnsplashKey.isNotBlank()) customUnsplashKey else context.customUnsplashKey).trim()
        val defaultBackupKey = ApiLoadBalancerService.resolveUnsplashApiKey("J6C-j-OjDXft4dMbIm96LlAX4ZqUuViErwbOENvkt4Q")

        val keysToTry = mutableListOf<String>()
        if (userKey.isNotEmpty() && userKey.length >= 16) {
            keysToTry.add(userKey)
        }
        if (defaultBackupKey.isNotEmpty() && !keysToTry.contains(defaultBackupKey)) {
            keysToTry.add(defaultBackupKey)
        }
        if (keysToTry.isEmpty()) return list

        val orientation = when (context.aspectRatio) {
            "9:16", "4:5", "3:4" -> "portrait"
            "16:9", "21:9" -> "landscape"
            else -> "squarish"
        }
        val queryText = query.replace(Regex("[^\\p{L}\\p{N}\\s]"), "")
        val encodedQuery = URLEncoder.encode(queryText, "UTF-8")

        for (accessKey in keysToTry) {
            try {
                val url = "https://api.unsplash.com/search/photos?query=$encodedQuery&client_id=$accessKey&per_page=10&orientation=$orientation"
                val request = Request.Builder().url(url).header("User-Agent", "Mozilla/5.0").build()
                val response = httpClient.newCall(request).execute()
                if (response.isSuccessful) {
                    val bodyString = response.body?.string() ?: ""
                    val json = JSONObject(bodyString)
                    val results = json.optJSONArray("results")
                    if (results != null) {
                        for (i in 0 until results.length()) {
                            val photoObj = results.optJSONObject(i) ?: continue
                            val alt = photoObj.optString("alt_description", photoObj.optString("description", ""))
                            val urlsObj = photoObj.optJSONObject("urls")
                            val imageUrl = urlsObj?.optString("full") ?: urlsObj?.optString("regular") ?: urlsObj?.optString("small")
                            if (!imageUrl.isNullOrEmpty()) {
                                list.add(
                                    CandidateMedia(
                                        url = imageUrl,
                                        title = alt,
                                        description = alt,
                                        tags = queryText,
                                        source = "Unsplash",
                                        mediaType = "IMAGE"
                                    )
                                )
                            }
                        }
                    }
                    if (list.isNotEmpty()) {
                        return list
                    }
                } else {
                    Log.w(TAG, "Unsplash key failed with code ${response.code}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching Unsplash candidates with key", e)
            }
        }
        return list
    }

    private fun fetchArchiveOrgCandidates(query: String, sceneNum: Int): List<CandidateMedia> {
        val list = mutableListOf<CandidateMedia>()
        try {
            val qClean = query.replace(Regex("[^a-zA-Z0-9\\s]"), "")
            val encoded = URLEncoder.encode("$qClean mediatype:image", "UTF-8")
            val url = "https://archive.org/advancedsearch.php?q=$encoded&fl[]=identifier,title,description&rows=10&page=1&output=json"

            val request = Request.Builder().url(url).header("User-Agent", "Mozilla/5.0").build()
            val response = httpClient.newCall(request).execute()
            if (response.isSuccessful) {
                val bodyString = response.body?.string() ?: ""
                val json = JSONObject(bodyString)
                val docs = json.optJSONObject("response")?.optJSONArray("docs")
                if (docs != null) {
                    for (i in 0 until docs.length()) {
                        val doc = docs.optJSONObject(i) ?: continue
                        val id = doc.optString("identifier")
                        val title = doc.optString("title")
                        val desc = doc.optString("description")
                        if (!id.isNullOrEmpty()) {
                            val directUrl = "https://archive.org/services/img/$id"
                            list.add(
                                CandidateMedia(
                                    url = directUrl,
                                    title = title,
                                    description = desc,
                                    tags = "archive.org historical public domain",
                                    source = "Archive.org",
                                    mediaType = "IMAGE"
                                )
                            )
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching Archive.org candidates", e)
        }
        return list
    }

    private fun fetchNasaCandidates(query: String, context: EvaluationContext): List<CandidateMedia> {
        val list = mutableListOf<CandidateMedia>()
        try {
            val qClean = query.replace(Regex("[^a-zA-Z0-9\\s]"), "").trim()
            if (qClean.isEmpty()) return list
            val encoded = URLEncoder.encode(qClean, "UTF-8")
            val isVideo = context.visualMedium.equals("Video", ignoreCase = true)
            val mediaType = if (isVideo) "video" else "image"
            val url = "https://images-api.nasa.gov/search?q=$encoded&media_type=$mediaType"
            
            val request = Request.Builder().url(url).header("User-Agent", "Mozilla/5.0").build()
            val response = httpClient.newCall(request).execute()
            if (response.isSuccessful) {
                val bodyString = response.body?.string() ?: ""
                val json = JSONObject(bodyString)
                val items = json.optJSONObject("collection")?.optJSONArray("items")
                if (items != null) {
                    for (i in 0 until items.length()) {
                        val item = items.optJSONObject(i) ?: continue
                        val dataObj = item.optJSONArray("data")?.optJSONObject(0)
                        val title = dataObj?.optString("title", "NASA Media") ?: "NASA Media"
                        val desc = dataObj?.optString("description", "") ?: ""
                        
                        if (isVideo) {
                            val collectionJsonUrl = item.optString("href")
                            if (!collectionJsonUrl.isNullOrEmpty()) {
                                val videoRequest = Request.Builder().url(collectionJsonUrl).header("User-Agent", "Mozilla/5.0").build()
                                val videoResponse = httpClient.newCall(videoRequest).execute()
                                if (videoResponse.isSuccessful) {
                                    val videoBody = videoResponse.body?.string() ?: ""
                                    val videoArray = JSONArray(videoBody)
                                    val videoUrls = mutableListOf<String>()
                                    for (vIdx in 0 until videoArray.length()) {
                                        val vUrl = videoArray.optString(vIdx)
                                        if (vUrl.endsWith(".mp4", ignoreCase = true) || vUrl.contains(".mp4?", ignoreCase = true)) {
                                            videoUrls.add(vUrl)
                                        }
                                    }
                                    val chosenUrl = videoUrls.find { it.contains("~medium.mp4", true) } ?: videoUrls.firstOrNull()
                                    if (!chosenUrl.isNullOrEmpty()) {
                                        val safeUrl = if (chosenUrl.startsWith("http://")) chosenUrl.replace("http://", "https://") else chosenUrl
                                        list.add(CandidateMedia(url = safeUrl, title = title, description = desc, tags = "nasa space astronomy", source = "NASA Library", mediaType = "VIDEO"))
                                    }
                                }
                            }
                        } else {
                            val links = item.optJSONArray("links")
                            val thumbUrl = links?.optJSONObject(0)?.optString("href") ?: ""
                            if (thumbUrl.isNotEmpty()) {
                                val highRes = if (thumbUrl.contains("~thumb")) thumbUrl.replace("~thumb", "~medium") else thumbUrl
                                val safeUrl = if (highRes.startsWith("http://")) highRes.replace("http://", "https://") else highRes
                                list.add(CandidateMedia(url = safeUrl, title = title, description = desc, tags = "nasa space astronomy telescope universe", source = "NASA Library", mediaType = "IMAGE"))
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching NASA candidates", e)
        }
        return list
    }

    private fun fetchSpoonacularCandidates(query: String, context: EvaluationContext): List<CandidateMedia> {
        val list = mutableListOf<CandidateMedia>()
        val userKey = context.customSpoonacularKey.trim()
        val defaultBackupKey = try {
            BuildConfig.SPOONACULAR_API_KEY
        } catch (e: Exception) {
            ""
        }

        val keysToTry = mutableListOf<String>()
        if (userKey.isNotEmpty() && userKey.length >= 10) {
            keysToTry.add(userKey)
        }
        if (defaultBackupKey.isNotBlank() && !keysToTry.contains(defaultBackupKey)) {
            keysToTry.add(defaultBackupKey)
        }
        if (keysToTry.isEmpty()) return list

        val qClean = query.replace(Regex("[^a-zA-Z0-9\\s]"), "").trim()
        val encoded = URLEncoder.encode(qClean, "UTF-8")
        val isVideo = context.visualMedium.equals("Video", ignoreCase = true)

        for (spoonacularKey in keysToTry) {
            try {
                val url = if (isVideo) {
                    "https://api.spoonacular.com/food/videos/search?apiKey=$spoonacularKey&query=$encoded&number=10"
                } else {
                    "https://api.spoonacular.com/recipes/complexSearch?apiKey=$spoonacularKey&query=$encoded&number=10"
                }
                val request = Request.Builder().url(url).header("User-Agent", "Mozilla/5.0").build()
                val response = httpClient.newCall(request).execute()
                if (response.isSuccessful) {
                    val json = JSONObject(response.body?.string() ?: "")
                    if (isVideo) {
                        val videos = json.optJSONArray("videos")
                        if (videos != null) {
                            for (i in 0 until videos.length()) {
                                val vObj = videos.optJSONObject(i) ?: continue
                                val thumb = vObj.optString("thumbnail")
                                val title = vObj.optString("title", "Food Video")
                                if (thumb.isNotEmpty()) {
                                    list.add(CandidateMedia(url = thumb, title = title, description = "Spoonacular video recipe", tags = "food recipe cooking dish", source = "Spoonacular", mediaType = "VIDEO"))
                                }
                            }
                        }
                    } else {
                        val results = json.optJSONArray("results")
                        if (results != null) {
                            for (i in 0 until results.length()) {
                                val rObj = results.optJSONObject(i) ?: continue
                                val img = rObj.optString("image")
                                val title = rObj.optString("title", "Food Recipe")
                                if (img.isNotEmpty()) {
                                    val safeImg = if (img.startsWith("http://")) img.replace("http://", "https://") else img
                                    list.add(CandidateMedia(url = safeImg, title = title, description = "Spoonacular recipe photo", tags = "food recipe culinary dish cooking", source = "Spoonacular", mediaType = "IMAGE"))
                                }
                            }
                        }
                    }
                    if (list.isNotEmpty()) {
                        return list
                    }
                } else {
                    Log.w(TAG, "Spoonacular key failed with code ${response.code}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching Spoonacular candidates with key", e)
            }
        }
        return list
    }

    private fun fetchJikanAnimeCandidates(query: String, sceneNum: Int): List<CandidateMedia> {
        val list = mutableListOf<CandidateMedia>()
        try {
            val qClean = query.replace(Regex("[^a-zA-Z0-9\\s]"), "").trim()
            val url = if (qClean.isNotEmpty()) {
                val encoded = URLEncoder.encode(qClean, "UTF-8")
                "https://api.jikan.moe/v4/anime?q=$encoded&limit=10"
            } else {
                "https://api.jikan.moe/v4/top/anime?limit=10"
            }
            val request = Request.Builder().url(url).header("User-Agent", "Mozilla/5.0").build()
            val response = httpClient.newCall(request).execute()
            if (response.isSuccessful) {
                val json = JSONObject(response.body?.string() ?: "")
                val data = json.optJSONArray("data")
                if (data != null) {
                    for (i in 0 until data.length()) {
                        val anime = data.optJSONObject(i) ?: continue
                        val title = anime.optString("title", "Anime")
                        val jpgObj = anime.optJSONObject("images")?.optJSONObject("jpg")
                        val imgUrl = jpgObj?.optString("large_image_url") ?: jpgObj?.optString("image_url")
                        if (!imgUrl.isNullOrEmpty()) {
                            list.add(CandidateMedia(url = imgUrl, title = title, description = "MyAnimeList anime artwork", tags = "anime manga artwork illustration", source = "Jikan Anime", mediaType = "IMAGE"))
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching Jikan Anime candidates", e)
        }
        if (list.isEmpty()) {
            try {
                val topUrl = "https://api.jikan.moe/v4/top/anime?limit=10"
                val topReq = Request.Builder().url(topUrl).header("User-Agent", "Mozilla/5.0").build()
                val topResp = httpClient.newCall(topReq).execute()
                if (topResp.isSuccessful) {
                    val json = JSONObject(topResp.body?.string() ?: "")
                    val data = json.optJSONArray("data")
                    if (data != null) {
                        for (i in 0 until data.length()) {
                            val anime = data.optJSONObject(i) ?: continue
                            val title = anime.optString("title", "Anime")
                            val jpgObj = anime.optJSONObject("images")?.optJSONObject("jpg")
                            val imgUrl = jpgObj?.optString("large_image_url") ?: jpgObj?.optString("image_url")
                            if (!imgUrl.isNullOrEmpty()) {
                                list.add(CandidateMedia(url = imgUrl, title = title, description = "MyAnimeList top anime artwork", tags = "anime manga artwork illustration top", source = "Jikan Anime", mediaType = "IMAGE"))
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching Jikan Top Anime fallback", e)
            }
        }
        return list
    }

    private fun fetchNekosBestCandidates(query: String, sceneNum: Int): List<CandidateMedia> {
        val list = mutableListOf<CandidateMedia>()
        try {
            val q = query.lowercase()
            val category = when {
                q.contains("cry") || q.contains("sad") -> "cry"
                q.contains("smile") || q.contains("laugh") || q.contains("happy") -> "smile"
                q.contains("pat") || q.contains("pet") -> "pat"
                q.contains("wave") || q.contains("hello") || q.contains("bye") -> "wave"
                q.contains("bored") || q.contains("tired") -> "bored"
                q.contains("boy") || q.contains("man") -> "husbando"
                q.contains("kitsune") || q.contains("fox") -> "kitsune"
                else -> "neko"
            }
            val nekosUa = "YashoraReelGenerator (Ritvyom@gmail.com)"
            val url = "https://nekos.best/api/v2/$category?amount=10"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", nekosUa)
                .build()
            val response = httpClient.newCall(request).execute()
            if (response.isSuccessful) {
                val json = JSONObject(response.body?.string() ?: "")
                val results = json.optJSONArray("results")
                if (results != null) {
                    for (i in 0 until results.length()) {
                        val item = results.optJSONObject(i) ?: continue
                        val imgUrl = item.optString("url")
                        val animeName = item.optString("anime_name", "Anime Character")
                        if (imgUrl.isNotEmpty()) {
                            list.add(CandidateMedia(url = imgUrl, title = animeName, description = "Nekos.best anime visual ($category)", tags = "anime character expression art $category", source = "Nekos.best", mediaType = "IMAGE"))
                        }
                    }
                }
            }
            if (list.isEmpty()) {
                val fallbackCat = if (category == "neko") "waifu" else "neko"
                val fbUrl = "https://nekos.best/api/v2/$fallbackCat?amount=8"
                val fbReq = Request.Builder().url(fbUrl).header("User-Agent", nekosUa).build()
                val fbResp = httpClient.newCall(fbReq).execute()
                if (fbResp.isSuccessful) {
                    val fbJson = JSONObject(fbResp.body?.string() ?: "")
                    val fbResults = fbJson.optJSONArray("results")
                    if (fbResults != null) {
                        for (i in 0 until fbResults.length()) {
                            val item = fbResults.optJSONObject(i) ?: continue
                            val imgUrl = item.optString("url")
                            val animeName = item.optString("anime_name", "Anime Character")
                            if (imgUrl.isNotEmpty()) {
                                list.add(CandidateMedia(url = imgUrl, title = animeName, description = "Nekos.best anime visual ($fallbackCat)", tags = "anime character expression art $fallbackCat", source = "Nekos.best", mediaType = "IMAGE"))
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching Nekos.best candidates", e)
        }
        return list
    }

    private fun fetchGiphyCandidates(query: String, sceneNum: Int): List<CandidateMedia> {
        val list = mutableListOf<CandidateMedia>()
        val userAgent = "YashoraReelGenerator/2.0 (Ritvyom; Suryadev Nishad; Ritvyom@gmail.com) Android/14 OkHttp/4.12"
        try {
            val qClean = query.replace(Regex("[^\\p{L}\\p{N}\\s]"), " ").trim()
            // 1. Fetch Reddit popular humorous memes via meme-api.com
            val memeUrl = "https://meme-api.com/gimme/5"
            val memeReq = Request.Builder().url(memeUrl).header("User-Agent", userAgent).build()
            val memeResp = httpClient.newCall(memeReq).execute()
            if (memeResp.isSuccessful) {
                val body = memeResp.body?.string() ?: ""
                val json = JSONObject(body)
                val memes = json.optJSONArray("memes")
                if (memes != null) {
                    for (i in 0 until memes.length()) {
                        val m = memes.optJSONObject(i) ?: continue
                        val mUrl = m.optString("url")
                        val mTitle = m.optString("title", "Meme")
                        if (mUrl.isNotEmpty()) {
                            list.add(
                                CandidateMedia(
                                    url = mUrl,
                                    title = mTitle,
                                    description = "Humorous meme: $mTitle",
                                    tags = "meme funny humor viral $qClean",
                                    source = "Giphy",
                                    mediaType = "IMAGE",
                                    score = 75
                                )
                            )
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching meme/giphy candidates", e)
        }
        return list
    }

    private fun fetchNhtsaCandidates(query: String, sceneNum: Int): List<CandidateMedia> {
        val list = mutableListOf<CandidateMedia>()
        try {
            val qClean = query.replace(Regex("[^a-zA-Z0-9\\s]"), "").trim()
            val words = qClean.split("\\s+".toRegex())
            val defaultMakes = listOf("tesla", "toyota", "ford", "bmw", "ferrari", "chevrolet", "audi", "honda", "nissan", "mercedes")
            var make = defaultMakes[sceneNum % defaultMakes.size]
            for (w in words) {
                if (defaultMakes.contains(w.lowercase()) || w.length >= 3) {
                    make = w.lowercase()
                    break
                }
            }
            val encodedMake = URLEncoder.encode(make, "UTF-8")
            val url = "https://vpic.nhtsa.dot.gov/api/vehicles/getmodelsformake/$encodedMake?format=json"
            val request = Request.Builder().url(url).header("User-Agent", "Mozilla/5.0").build()
            val response = httpClient.newCall(request).execute()
            if (response.isSuccessful) {
                val json = JSONObject(response.body?.string() ?: "")
                val results = json.optJSONArray("Results")
                if (results != null && results.length() > 0) {
                    val count = minOf(results.length(), 5)
                    for (i in 0 until count) {
                        val modelName = results.optJSONObject(i)?.optString("Model_Name") ?: ""
                        if (modelName.isNotEmpty()) {
                            val prompt = "A premium luxury cinematic photographic showcase of a $make $modelName car, modern automotive design, sunset background, hyper-detailed, 8k resolution"
                            val encodedPrompt = URLEncoder.encode(prompt, "UTF-8")
                            val imgUrl = "https://image.pollinations.ai/p/$encodedPrompt?width=1080&height=1920&seed=${modelName.hashCode()}&nologo=true"
                            list.add(CandidateMedia(url = imgUrl, title = "$make $modelName", description = "NHTSA vehicle specification photo", tags = "$make $modelName car automobile luxury vehicle automotive", source = "NHTSA vPIC", mediaType = "IMAGE"))
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching NHTSA candidates", e)
        }
        return list
    }

    private fun fetchOpenFdaCandidates(query: String, sceneNum: Int): List<CandidateMedia> {
        val list = mutableListOf<CandidateMedia>()
        try {
            val qClean = query.replace(Regex("[^a-zA-Z0-9\\s]"), "").trim()
            if (qClean.isEmpty()) return list
            val encoded = URLEncoder.encode(qClean, "UTF-8")
            val url = "https://api.fda.gov/drug/label.json?search=description:$encoded&limit=5"
            val request = Request.Builder().url(url).header("User-Agent", "Mozilla/5.0").build()
            val response = httpClient.newCall(request).execute()
            if (response.isSuccessful) {
                val json = JSONObject(response.body?.string() ?: "")
                val results = json.optJSONArray("results")
                if (results != null) {
                    for (i in 0 until results.length()) {
                        val res = results.optJSONObject(i) ?: continue
                        val openfda = res.optJSONObject("openfda")
                        val brandName = openfda?.optJSONArray("brand_name")?.optString(0) ?: qClean
                        val prompt = "A professional high-end studio shot of clinical pharmaceutical packaging of $brandName medicine bottle, modern minimal healthcare design, bright soft studio light, white background, ultra-premium medical product photograph"
                        val encodedPrompt = URLEncoder.encode(prompt, "UTF-8")
                        val imgUrl = "https://image.pollinations.ai/p/$encodedPrompt?width=1080&height=1920&seed=${brandName.hashCode()}&nologo=true"
                        list.add(CandidateMedia(url = imgUrl, title = brandName, description = "OpenFDA pharmaceutical studio visual", tags = "$brandName medicine pharmacy health doctor clinical pharma", source = "OpenFDA", mediaType = "IMAGE"))
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching OpenFDA candidates", e)
        }
        if (list.isEmpty()) {
            try {
                val url = "https://api.fda.gov/drug/label.json?limit=5"
                val request = Request.Builder().url(url).header("User-Agent", "Mozilla/5.0").build()
                val response = httpClient.newCall(request).execute()
                if (response.isSuccessful) {
                    val json = JSONObject(response.body?.string() ?: "")
                    val results = json.optJSONArray("results")
                    if (results != null) {
                        for (i in 0 until results.length()) {
                            val res = results.optJSONObject(i) ?: continue
                            val brandName = res.optJSONObject("openfda")?.optJSONArray("brand_name")?.optString(0) ?: "Healthcare Medicine"
                            val prompt = "A professional high-end studio shot of clinical pharmaceutical packaging of $brandName medicine bottle, modern minimal healthcare design, bright soft studio light, white background, ultra-premium medical product photograph"
                            val encodedPrompt = URLEncoder.encode(prompt, "UTF-8")
                            val imgUrl = "https://image.pollinations.ai/p/$encodedPrompt?width=1080&height=1920&seed=${brandName.hashCode()}&nologo=true"
                            list.add(CandidateMedia(url = imgUrl, title = brandName, description = "OpenFDA pharmaceutical studio visual", tags = "$brandName medicine pharmacy health doctor clinical pharma", source = "OpenFDA", mediaType = "IMAGE"))
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching OpenFDA fallback candidates", e)
            }
        }
        return list
    }

    private fun fetchWhoCandidates(query: String, sceneNum: Int): List<CandidateMedia> {
        val list = mutableListOf<CandidateMedia>()
        try {
            val qClean = query.replace(Regex("[^a-zA-Z0-9\\s]"), "").trim()
            val url = if (qClean.isNotEmpty()) {
                val encoded = URLEncoder.encode(qClean, "UTF-8")
                "https://ghoapi.azureedge.net/api/Indicator?\$filter=contains(IndicatorName,%20'$encoded')"
            } else {
                "https://ghoapi.azureedge.net/api/Indicator?\$top=5"
            }
            val request = Request.Builder().url(url).header("User-Agent", "Mozilla/5.0").build()
            val response = httpClient.newCall(request).execute()
            if (response.isSuccessful) {
                val json = JSONObject(response.body?.string() ?: "")
                val valueArray = json.optJSONArray("value")
                if (valueArray != null) {
                    val count = minOf(valueArray.length(), 5)
                    for (i in 0 until count) {
                        val indObj = valueArray.optJSONObject(i) ?: continue
                        val indicatorName = indObj.optString("IndicatorName", "")
                        if (indicatorName.isNotEmpty()) {
                            val prompt = "A clean professional conceptual medical illustration about $indicatorName, global health awareness, clean vector illustration style, minimal and symbolic, vivid modern color palette"
                            val encodedPrompt = URLEncoder.encode(prompt, "UTF-8")
                            val imgUrl = "https://image.pollinations.ai/p/$encodedPrompt?width=1080&height=1920&seed=${indicatorName.hashCode()}&nologo=true"
                            list.add(CandidateMedia(url = imgUrl, title = indicatorName, description = "World Health Organization global indicator visual", tags = "$indicatorName who global health medicine doctor awareness", source = "WHO", mediaType = "IMAGE"))
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching WHO candidates", e)
        }
        if (list.isEmpty()) {
            try {
                val url = "https://ghoapi.azureedge.net/api/Indicator?\$top=5"
                val request = Request.Builder().url(url).header("User-Agent", "Mozilla/5.0").build()
                val response = httpClient.newCall(request).execute()
                if (response.isSuccessful) {
                    val json = JSONObject(response.body?.string() ?: "")
                    val valueArray = json.optJSONArray("value")
                    if (valueArray != null) {
                        for (i in 0 until minOf(valueArray.length(), 5)) {
                            val indObj = valueArray.optJSONObject(i) ?: continue
                            val indicatorName = indObj.optString("IndicatorName", "Global Healthcare")
                            val prompt = "A clean professional conceptual medical illustration about $indicatorName, global health awareness, clean vector illustration style, minimal and symbolic, vivid modern color palette"
                            val encodedPrompt = URLEncoder.encode(prompt, "UTF-8")
                            val imgUrl = "https://image.pollinations.ai/p/$encodedPrompt?width=1080&height=1920&seed=${indicatorName.hashCode()}&nologo=true"
                            list.add(CandidateMedia(url = imgUrl, title = indicatorName, description = "World Health Organization global indicator visual", tags = "$indicatorName who global health medicine doctor awareness", source = "WHO", mediaType = "IMAGE"))
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching WHO fallback candidates", e)
            }
        }
        return list
    }

    private fun fetchLoremPicsumCandidates(query: String, evalContext: EvaluationContext): List<CandidateMedia> {
        val list = mutableListOf<CandidateMedia>()
        try {
            val page = (evalContext.sceneNum % 5) + 1
            val url = "https://picsum.photos/v2/list?page=$page&limit=10"
            val request = Request.Builder().url(url).header("User-Agent", "Mozilla/5.0").build()
            val response = httpClient.newCall(request).execute()
            if (response.isSuccessful) {
                val array = org.json.JSONArray(response.body?.string() ?: "[]")
                for (i in 0 until array.length()) {
                    val item = array.optJSONObject(i) ?: continue
                    val id = item.optString("id")
                    val author = item.optString("author", "Photographer")
                    if (id.isNotEmpty()) {
                        val imgUrl = "https://picsum.photos/id/$id/1080/1920"
                        list.add(CandidateMedia(
                            url = imgUrl,
                            title = "Scenic #$id by $author",
                            description = "Horizon Scenic landscape photography",
                            tags = "scenic nature landscape photography wallpaper $author",
                            source = "Lorem Picsum",
                            mediaType = "IMAGE",
                            score = 80
                        ))
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching Lorem Picsum candidates", e)
        }
        if (list.isEmpty()) {
            val seed = (query.hashCode() + evalContext.sceneNum * 31).let { if (it < 0) -it else it }
            val fallbackUrl = "https://picsum.photos/seed/$seed/1080/1920"
            list.add(CandidateMedia(
                url = fallbackUrl,
                title = "Scenic Backdrop",
                description = "Horizon Scenic landscape photography",
                tags = "scenic nature landscape wallpaper",
                source = "Lorem Picsum",
                mediaType = "IMAGE",
                score = 75
            ))
        }
        return list
    }

    private fun fetchLoremFlickrCandidates(query: String, evalContext: EvaluationContext): List<CandidateMedia> {
        val list = mutableListOf<CandidateMedia>()
        val cleanTag = query.replace(Regex("[^a-zA-Z0-9]"), "").trim().ifEmpty { "nature" }
        for (i in 0..4) {
            val lock = ((cleanTag.hashCode() + evalContext.sceneNum * 13 + i).let { if (it < 0) -it else it } % 899) + 100
            val url = "https://loremflickr.com/1080/1920/$cleanTag?lock=$lock"
            list.add(CandidateMedia(
                url = url,
                title = "$cleanTag snapshot #$lock",
                description = "Lumina Snapshot tag-tailored premium photography",
                tags = "$cleanTag snapshot lumina creative photography",
                source = "LoremFlickr",
                mediaType = "IMAGE",
                score = 78
            ))
        }
        return list
    }

    private fun fetchOmniShieldFallbackCandidates(query: String, evalContext: EvaluationContext): List<CandidateMedia> {
        val list = mutableListOf<CandidateMedia>()
        val fallbackPool = listOf(
            "https://images.unsplash.com/photo-1506744038136-46273834b3fb?w=1080&q=80" to "Majestic Mountain Vista",
            "https://images.unsplash.com/photo-1470071459604-3b5ec3a7fe05?w=1080&q=80" to "Serene Forest Twilight",
            "https://images.unsplash.com/photo-1446776811953-b23d57bd21aa?w=1080&q=80" to "Cosmic Deep Space Orbit",
            "https://images.unsplash.com/photo-1477959858617-67f30bc75b82?w=1080&q=80" to "Metropolis Cyber Skyline",
            "https://images.unsplash.com/photo-1518709268805-4e9042af9f23?w=1080&q=80" to "Abstract Luminescent Wave"
        )
        for ((url, title) in fallbackPool) {
            list.add(CandidateMedia(
                url = url,
                title = title,
                description = "OmniShield ultra-secure resilient visual delivery",
                tags = "omnishield resilient cinematic 4k stock",
                source = "11-Stage Fallback",
                mediaType = "IMAGE",
                score = 85
            ))
        }
        return list
    }

    // --- AI IMAGE GENERATION FALLBACK ---

    private fun generateAiImageFallback(context: EvaluationContext): String {
        return try {
            val (w, h) = calculateDimensions(context.aspectRatio)
            val seed = kotlin.math.abs(context.visualPrompt.hashCode() * 31 + context.sceneNum * 997 + 1)

            val promptText = when {
                context.visualPrompt.isNotBlank() -> context.visualPrompt
                context.keywords.isNotEmpty() -> context.keywords.joinToString(" ")
                context.narrationText.isNotBlank() -> context.narrationText
                else -> context.subtitle
            }

            val basePrompt = if (!context.criticalEntity.isNullOrEmpty()) {
                "Historical reconstruction documentary style, period-accurate environment, period-accurate clothing, photorealistic 8k, no modern objects: ${context.criticalEntity}, $promptText"
            } else if (context.sceneType == SceneType.FOOD) {
                "Gourmet culinary food photography, appetizing delicious recipe dish, professional kitchen presentation, fresh ingredients, warm appetizing restaurant lighting, photorealistic 8k: $promptText"
            } else if (context.sceneType == SceneType.HISTORICAL_EVENT || context.sceneType == SceneType.HISTORICAL_PLACE) {
                "Historical archival documentary style, detailed period reconstruction, photorealistic 8k: $promptText"
            } else {
                "Cinematic professional artistic visual, $promptText, ${context.style} style"
            }

            val encodedPrompt = URLEncoder.encode(basePrompt, "UTF-8")
            "https://image.pollinations.ai/p/$encodedPrompt?width=$w&height=$h&seed=$seed&nologo=true"
        } catch (e: Exception) {
            Log.e(TAG, "Error generating AI Image fallback", e)
            ""
        }
    }

    // --- NEUTRAL SYMBOLIC FALLBACK ---

    private fun generateNeutralSymbolicFallback(context: EvaluationContext): String {
        val (w, h) = calculateDimensions(context.aspectRatio)
        val seed = kotlin.math.abs(context.visualPrompt.hashCode() * 31 + context.sceneNum * 997 + 1)

        val query = when {
            context.visualPrompt.isNotBlank() -> context.visualPrompt
            context.keywords.isNotEmpty() -> "${context.keywords.joinToString(" ")}, ${context.style} style"
            context.sceneType == SceneType.CLOCK_TIME -> "vintage grandfather clock ticking"
            context.sceneType == SceneType.FOOD -> "delicious gourmet food dish plating"
            context.sceneType in listOf(SceneType.HISTORICAL_PERSON, SceneType.HISTORICAL_PLACE, SceneType.HISTORICAL_EVENT, SceneType.HISTORICAL_OBJECT) ->
                "historical parchment archival scene"
            else -> "cinematic ${context.style} scene"
        }

        val encoded = URLEncoder.encode(query, "UTF-8")
        return "https://image.pollinations.ai/p/$encoded?width=$w&height=$h&seed=$seed&nologo=true"
    }

    // --- UTILITIES ---

    private fun isValidUrl(url: String): Boolean {
        if (url.isBlank()) return false
        if (!url.startsWith("http://") && !url.startsWith("https://")) return false
        if (url.contains("example.com") || url.contains("null")) return false
        return true
    }

    private fun buildCacheKey(context: EvaluationContext): String {
        val entityStr = context.criticalEntity ?: ""
        return "${context.sceneType}_${entityStr}_${context.visualPrompt.hashCode()}_${context.aspectRatio}_${context.visualMedium}_#${context.sceneNum}"
    }

    private fun calculateDimensions(aspectRatio: String): Pair<Int, Int> {
        return when (aspectRatio) {
            "9:16" -> Pair(720, 1280)
            "16:9" -> Pair(1280, 720)
            "1:1" -> Pair(1024, 1024)
            "4:5" -> Pair(800, 1000)
            "3:4" -> Pair(768, 1024)
            "21:9" -> Pair(1280, 540)
            else -> Pair(720, 1280)
        }
    }

    private fun extractKeywordsFromText(text: String): List<String> {
        val stopwords = setOf(
            "a", "an", "the", "and", "or", "but", "is", "are", "was", "were", "of", "to", "in", "on", "at", "by", "for", "with", "about",
            "this", "that", "it", "its", "you", "your", "my", "me", "we", "our", "us", "they", "them", "some", "any", "no", "not", "so",
            "can", "will", "show", "get", "make", "be", "have", "has", "had", "do", "does", "did", "from", "very", "scene", "style", "description",
            "cinematic", "narrative", "visual", "representation", "captivating", "artistic", "detailed",
            // Hinglish / Roman Hindi stopwords
            "kare", "karo", "karna", "karne", "kaise", "hota", "hote", "hoti", "hoga", "hogi", "hoge", "raha", "rahe", "rahi", "hai", "hain", "hoon",
            "tha", "the", "thi", "mera", "meri", "mere", "apna", "apne", "apni", "tera", "teri", "tere", "uska", "uske", "uski", "inka", "inke", "unki",
            "yeh", "ye", "woh", "wo", "isme", "usme", "isse", "usse", "jisse", "jisme", "agar", "magar", "lekin", "aur", "ya", "par", "pe", "ko",
            "se", "me", "mein", "ka", "ke", "ki", "bhi", "toh", "to", "hi", "ab", "kab", "jab", "tab", "kuch", "koi", "kisi", "sab", "sabhi"
        )
        val hasDevanagari = text.any { it in '\u0900'..'\u097F' }
        val effectiveText = if (hasDevanagari) {
            val sem = GeminiService.extractSemiSemanticKeyword(text)
            "$text $sem"
        } else {
            text
        }
        return effectiveText.lowercase()
            .replace(Regex("[^\\p{L}\\s]"), "")
            .split(Regex("\\s+"))
            .filter { it.length >= 3 && !stopwords.contains(it) }
            .distinct()
            .take(6)
    }
}
