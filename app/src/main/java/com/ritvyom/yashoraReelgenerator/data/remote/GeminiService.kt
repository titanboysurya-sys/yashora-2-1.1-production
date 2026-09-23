package com.ritvyom.yashoraReelgenerator.data.remote

import android.util.Log
import com.ritvyom.yashoraReelgenerator.BuildConfig
import com.ritvyom.yashoraReelgenerator.domain.models.Scene
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import com.google.firebase.Firebase
import com.google.firebase.ai.*
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.generationConfig as aiGenerationConfig
import com.google.firebase.ai.type.content as aiContent

class GeminiService {

    private val httpClient = OkHttpClient.Builder()
        .addInterceptor { chain ->
            val req = chain.request()
            val hasUserAgent = req.header("User-Agent") != null
            val newReq = if (!hasUserAgent) {
                req.newBuilder()
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36 YashoraReelGenerator/2.0")
                    .build()
            } else req
            chain.proceed(newReq)
        }
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(35, TimeUnit.SECONDS)
        .writeTimeout(35, TimeUnit.SECONDS)
        .build()

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val MODERN_MODELS = listOf(
        "gemini-2.5-flash",
        "gemini-flash-latest",
        "gemini-3.1-pro-preview",
        "gemini-3.1-flash-lite-preview"
    )

    private val FIREBASE_MODELS = listOf(
        "gemini-2.5-flash",
        "gemini-flash-latest",
        "gemini-3.1-pro-preview"
    )

    fun getCleanApiKey(customKey: String = ""): String {
        var rawKey = if (customKey.isNotEmpty()) {
            customKey
        } else {
            try {
                BuildConfig.GEMINI_API_KEY
            } catch (e: Exception) {
                ""
            }
        }
        var cleaned = rawKey.trim().removeSurrounding("\"").removeSurrounding("'")
        if (isApiKeyValid(cleaned)) return cleaned

        // Fallback to Firebase Project API key if available
        try {
            val fbKey = com.google.firebase.FirebaseApp.getInstance().options.apiKey.trim().removeSurrounding("\"").removeSurrounding("'")
            if (isApiKeyValid(fbKey)) return fbKey
        } catch (e: Exception) {
            // FirebaseApp may not be initialized yet
        }
        return ""
    }

    fun isApiKeyValid(key: String): Boolean {
        val cleaned = key.trim().removeSurrounding("\"").removeSurrounding("'")
        if (cleaned.length < 16) return false
        val lower = cleaned.lowercase(java.util.Locale.ROOT)
        if (lower.startsWith("your_") || lower.startsWith("my_") ||
            lower.contains("placeholder") || lower.contains("api_key") ||
            lower.contains("example") || lower.contains("default") ||
            lower.startsWith("<") || lower.endsWith(">") ||
            cleaned.contains(" ") || cleaned.contains("\n") || cleaned.contains("\t") ||
            cleaned == "AIzaSyBgk8mDdx3_hiPvs0MNEGQOj_5Ly29LBMA" // Known restricted legacy Firebase project key
        ) {
            return false
        }
        return true
    }

    /**
     * Resolves candidate Gemini API keys in strict priority hierarchy:
     * 1. User-provided API Key (from argument, PreferencesManager, or SecureAiCredentialStore)
     * 2. App's Internal Built-in Key (BuildConfig.GEMINI_API_KEY)
     * 3. Firebase Project Gemini Developer API Key (from google-services.json)
     */
    fun resolveCandidateGeminiKeys(customKey: String = ""): List<Pair<String, String>> {
        val candidates = mutableListOf<Pair<String, String>>()

        // Tier 1: User-provided key (always prioritized first)
        var userKey = customKey.trim().removeSurrounding("\"").removeSurrounding("'")
        if (!isApiKeyValid(userKey)) {
            try {
                val app = com.ritvyom.yashoraReelgenerator.YashoraApplication.getInstance()
                val prefKey = app?.preferencesManager?.run {
                    runBlocking { geminiApiKeyFlow.first() }
                }?.trim()?.removeSurrounding("\"")?.removeSurrounding("'") ?: ""
                if (isApiKeyValid(prefKey)) {
                    userKey = prefKey
                } else {
                    val storeKey = app?.secureAiCredentialStore?.getApiKey(com.ritvyom.yashoraReelgenerator.data.ai.ProviderId.GEMINI)
                        ?.trim()?.removeSurrounding("\"")?.removeSurrounding("'") ?: ""
                    if (isApiKeyValid(storeKey)) {
                        userKey = storeKey
                    }
                }
            } catch (e: Exception) {
                Log.w("GeminiService", "Failed resolving user saved key: ${e.message}")
            }
        }

        if (isApiKeyValid(userKey)) {
            candidates.add(Pair("User-provided Gemini API Key", userKey))
        }

        // Tier 2: App's internal built-in default key (fallback if user key is absent or fails)
        val appKey = try {
            BuildConfig.GEMINI_API_KEY.trim().removeSurrounding("\"").removeSurrounding("'")
        } catch (e: Exception) {
            ""
        }
        if (isApiKeyValid(appKey) && appKey != userKey) {
            candidates.add(Pair("App Internal Gemini API Key", appKey))
        }

        // Tier 3: Firebase Project Gemini Developer API key (from google-services.json)
        val firebaseKey = try {
            com.google.firebase.FirebaseApp.getInstance().options.apiKey.trim().removeSurrounding("\"").removeSurrounding("'")
        } catch (e: Exception) {
            ""
        }
        if (isApiKeyValid(firebaseKey) && firebaseKey != userKey && firebaseKey != appKey) {
            candidates.add(Pair("Firebase Gemini Developer API Key", firebaseKey))
        }

        return candidates
    }

    suspend fun analyzeScript(
        script: String,
        style: String,
        language: String,
        aspectRatio: String = "9:16",
        imageSource: String = "Unsplash",
        customGeminiKey: String = "",
        customUnsplashKey: String = "",
        topicContext: String = "",
        publishingStyle: String = "TikTok / Instagram Reels"
    ): List<Scene> = withContext(Dispatchers.IO) {
        val apiKey = getCleanApiKey(customGeminiKey)

        Log.d("GeminiService", "Starting analyzeScript. REST API Key is: ${if (apiKey.isEmpty()) "Empty" else "Present"}")

        val publishingStyleGuidelines = when (publishingStyle) {
            "TikTok / Instagram Reels" -> """
                - STYLE SPECIFICS: Designed for rapid vertical feed consumption. High energy, ultra-vibrant contrast.
                - PACING/CUTS: Scenes must be extremely fast paced (duration and cuts every 3 to 5 seconds). Keep visual interest high to stop scrolling.
                - HOOK MANDATE: Scene 1 MUST contain a powerful visual and verbal 'hook' to immediately capture viewer attention within 2 seconds.
            """.trimIndent()
            "YouTube Short" -> """
                - STYLE SPECIFICS: Retention-optimized, highly focused informational / educational layout. Clear visual presentation.
                - PACING/CUTS: Regular pacing (4 to 6 seconds per scene). Smooth, authoritative flow, with clean centered details explaining the narration.
                - VISUAL CLARITY: Main subjects should be strictly centered, leaving empty bands on top and bottom for YouTube Shorts overlay buttons (like, share, channel avatar).
            """.trimIndent()
            "Cinematic Vlog / Trailer" -> """
                - STYLE SPECIFICS: Dramatic, atmospheric widescreen scenic look. Moody lighting, cinematic framing, sweeping scenic views.
                - PACING/CUTS: Slower story-paced rhythm (scenes lasting 5 to 8 seconds). Emphasize landscape visual beauty, mood and deep transition breathing room.
            """.trimIndent()
            "Short Documentary" -> """
                - STYLE SPECIFICS: Highly educational, factual, editorial photojournalism aesthetics. Vintage film grains, historical, or steady live-action reporting footage.
                - PACING/CUTS: Analytical, deliberate pacing. Grounded and informative visuals (scenes 5 to 7 seconds). Deep historical perspective.
            """.trimIndent()
            "Facebook Feed Video" -> """
                - STYLE SPECIFICS: Relatable, everyday life, informative scroll-stopping lifestyle structure. Warm, highly engaging, clean clear visuals.
                - PACING/CUTS: Accessible and steady narration pacing (4 to 6 seconds). Captivating community/viral storytelling focus.
            """.trimIndent()
            "LinkedIn Professional" -> """
                - STYLE SPECIFICS: Polished, ultra-clean, minimalist business aesthetic. Clean modern office, charts, business technology, networking concepts.
                - PACING/CUTS: Formal, clear, precise pacing (5 to 7 seconds). Balanced negative space, sleek lines, highly authoritative and serious corporate tone.
            """.trimIndent()
            "Ad Promo / Marketing" -> """
                - STYLE SPECIFICS: Commercial studio advertising style, crisp product focal highlights, bright professional lighting. High conversion CTA focus.
                - PACING/CUTS: Dynamic benefits-oriented pacing. Highlight visual solutions, with high-impact scenes leading directly to action cues.
            """.trimIndent()
            else -> ""
        }

        val prompt = """
            You are an expert AI video storyboard director. Analyze the following video script and break it down into a highly detailed sequence of visual scenes for short reels.
            
            Video Style: $style
            Target Language: $language
            Video Content & Platform Style: $publishingStyle
            
            PLATFORM SPECIFIC CONFIGURATION GUIDELINE FOR $publishingStyle:
            $publishingStyleGuidelines
            
            OVERALL VIDEO TOPIC / CONTEXT CATEGORY:
            ${if (topicContext.trim().isEmpty()) "None provided (infer from script text directly)" else topicContext}
            
            Script:
            $script
            
            CRITICAL CONTEXTUAL VISUAL ALIGNMENT:
            - IMPORTANT: You MUST keep all output "visualPrompt" and "imageSearchQuery" closely and explicitly aligned with the OVERALL VIDEO TOPIC / CONTEXT specified above.
            - If the script mentions abstract ideas or mistakes (e.g. "Page Suspended", "Appeal System", "error", "mistakes", "warning", "copy", "appeals", etc.), do NOT generate generic irrelevant visual queries (such as a paper booklet, a physical court-room appeal, a wooden pen, a couple eating dinner, or generic office supplies).
            - Instead, ground all visuals beautifully in the stated topic context! (e.g., if the topic is "Facebook page suspension/spam/monetization", output search queries and prompts featuring "social media glitch", "computer alert error", "smartphone social media security block", "suspension warning screen", "sad influencer holding phone with warning icon", etc. so the final video matches perfectly!).
            
            CRITICAL RECIPE, COOKING & FOOD VISUAL ALIGNMENT:
            - When the script describes a recipe, food preparation, ingredients, cooking techniques, kitchen actions, dishes, or tasting:
            - You MUST generate visual prompts and image search queries strictly depicting the exact cooking actions, ingredients, kitchen utensils, or food being described (e.g. 'chopping fresh vegetables on wooden board', 'sizzling spices in hot pan oil', 'steaming curry in brass pot', 'garnishing cooked dish with fresh herbs', 'fluffy baked cake on cooling rack', 'chef plating delicious recipe meal', 'pouring sauce into cooking pan', 'seasoning dish with spices').
            - NEVER generate unrelated landscape, water body, empty roads, air/clouds, or random nature scenery for cooking steps. The visuals must match the recipe step and food topic!
            
            CRITICAL COMPLETE SCRIPT COVERAGE MANDATE (NEVER OMIT ANY PART OF SCRIPT):
            - You MUST process and break down 100% of the provided script from the VERY FIRST SENTENCE to the VERY LAST SENTENCE.
            - DO NOT summarize, condense, skip, or truncate any portion of the script narration text!
            - STRICT SENTENCE-BY-SENTENCE SCENE MAPPING MANDATE:
              * Every single sentence or clause of the script (every 5 to 10 words / 3 to 5 seconds of speech) MUST have its own individual scene!
              * NEVER condense multiple sentences or clauses into only 1 or 2 scenes.
              * For short scripts (<75 words): Generate 8 to 15 distinct scenes.
              * For medium scripts (75 to 200 words / ~1 minute): Generate 15 to 30 distinct scenes.
              * For long scripts (200 to 500+ words / 2 to 3+ minutes): Generate 30 to 60+ distinct scenes!
            - Ensure that the concatenation of all "narrationText" fields across all generated scenes EXACTLY covers the entire provided script text from beginning to end!
            
            CRITICAL ZERO-REPETITION & VISUAL DIVERSITY MANDATE:
            - Under NO circumstances should any two scenes in the video feature the same visual setup or identical search query.
            - Every single scene MUST have a totally UNIQUE, specific visual scene, camera angle, and action.
            - Vary the angles, subjects, documents, settings, and props to match the unfolding story.
            
            CRITICAL DETAILED MULTI-SENTENCE VISUAL PROMPTS:
            - In the "visualPrompt" field, write 3 to 4 detailed, vivid sentences in ENGLISH describing:
              1. The central subject, human characters, objects, or actions taking place directly matching that scene's voiceover.
              2. The camera shot and perspective: close-up, dynamic tracking shot, cinematic low angle, macro detail, or drone aerial shot.
              3. The lighting, environment, textures, and atmospheric depth of field matching the $style aesthetic in 4K resolution.
            - Do NOT write only 1-2 generic sentences! Each scene must have a rich, cinematic description.
            
            CRITICAL SEARCH QUERY PRECISION ("imageSearchQuery"):
            - The "imageSearchQuery" MUST be 2 to 4 clean, concrete English search keywords (nouns and action verbs) that match real stock footage and photo databases (Pexels, Pixabay, Unsplash).
            - Examples of high-precision search queries: "cbi detective case file", "courtroom judge gavel verdict", "police handcuffs suspect arrest", "forensic science laboratory evidence", "doctor surgical room hospital", "farmer wheat field tractor".
            - FORBIDDEN: Strictly forbidden from using generic placeholder terms like "cinematic narrative scene", "cinematic visual", "cinematic tag", or "narrative scene".
            
            CRITICAL LANGUAGE & VOICE SYNTHESIS REQUIREMENT:
            - If the Target Language is "$language" and the input Script is NOT written in that language, you MUST translate the "narrationText" and "subtitle" fields into $language (using the native alphabet/script of $language, e.g., Devanagari script for Hindi, Cyrillic for Russian, etc.).
            - Ensure the translations are elegant, high-impact, natural, and perfectly rhythmic for narration in a professional short video.
            - Ensure the "visualPrompt" remains in descriptive, highly colorful ENGLISH so it can map to professional background photographs (include style hints corresponding to $style).
            - VERY IMPORTANT: Do NOT include any decorative symbols, emojis, hashtags, markdown formatting (such as asterisks `*` or underscores `_`), or consecutive dots / ellipses (such as `...` or `......`) inside the "narrationText" or "subtitle" fields. Always use clean, native language letters and words with standard periods/commas for natural pauses. Never use symbols or dots that would confuse the Text-to-Speech synthesizer or be read aloud as "dot dot dot".

            CRITICAL CELEBRITY & PUBLIC FIGURE DETECTION:
            - Scan the script carefully for any mention of real-world popular figures, leaders, politicians, celebrities, or sports stars (e.g. "Modi ji", "Narendra Modi", "Yogi ji", "Elon Musk", "MS Dhoni", "Virat Kohli", "Donald Trump", etc.).
            - If any such figure is mentioned or implied, you MUST explicitly generate a highly descriptive visual prompt featuring them in the "visualPrompt" field (e.g., "A photorealistic, highly detailed portrait of Prime Minister Narendra Modi smiling in front of the Indian flag").
            - The "imageSearchQuery" for that specific scene must be set to their official standard English name (e.g., "Narendra Modi" or "Elon Musk") to trigger downstream generative AI synthesis.
            - Do not substitute them with generic people; represent the celebrity as named in the script directly in both "visualPrompt" and "imageSearchQuery".
            
            OUTPUT FORMAT & SCHEMA REQUIREMENTS:
            Respond strictly with a valid JSON object containing a "scenes" array matching this exact structure:
            {
              "scenes": [
                {
                  "sceneNumber": 1,
                  "narrationText": "Exact words to be spoken for this scene in $language",
                  "visualPrompt": "A rich 3-4 sentence cinematic English description of the scene background, lighting, subjects, and camera angle directly depicting this scene's voiceover, aligned with $style style",
                  "durationSeconds": 4,
                  "subtitle": "Subtitle text in $language matching spoken phrase",
                  "imageSearchQuery": "2 to 4 specific English search words directly representing this scene's narration subject",
                  "keywords": ["specific subject tag", "action tag", "context tag"]
                }
              ]
            }

            CRITICAL STABILITY & PROMPT ENGINEERING RULES:
            - Output ONLY pure, valid JSON. No conversational chatter, markdown explanation, or introductory text.
            - Ensure all strings are properly escaped (avoid unescaped double-quotes).
            - Do NOT include trailing commas.
        """.trimIndent()

        var text = ""

        // 1. Execute via Unified API Client Circuit Breaker (REST -> 1x Auto-Retry -> Secondary Vertex AI)
        try {
            text = UnifiedApiClient.executeWithCircuitBreaker(
                provider = ApiProvider.GEMINI,
                operationName = "analyzeScript",
                primaryCall = {
                    if (!isApiKeyValid(apiKey)) {
                        throw java.io.IOException("No valid REST API key available.")
                    }
                    var restResult = ""
                    val systemInstructionJson = JSONObject().apply {
                        put("parts", JSONArray().put(JSONObject().apply {
                            put("text", "You are an expert AI Video Producer and Screenplay analyst.")
                        }))
                    }

                    val contentsJson = JSONObject().apply {
                        put("parts", JSONArray().put(JSONObject().apply {
                            put("text", prompt)
                        }))
                    }

                    val responseFormatJson = JSONObject().apply {
                        put("type", "OBJECT")
                        put("properties", JSONObject().apply {
                            put("scenes", JSONObject().apply {
                                put("type", "ARRAY")
                                put("description", "A list of parsed scenes.")
                                put("items", JSONObject().apply {
                                    put("type", "OBJECT")
                                    put("properties", JSONObject().apply {
                                        put("sceneNumber", JSONObject().apply { put("type", "INTEGER") })
                                        put("narrationText", JSONObject().apply { put("type", "STRING") })
                                        put("visualPrompt", JSONObject().apply { put("type", "STRING") })
                                        put("durationSeconds", JSONObject().apply { put("type", "INTEGER") })
                                        put("subtitle", JSONObject().apply { put("type", "STRING") })
                                        put("imageSearchQuery", JSONObject().apply { put("type", "STRING") })
                                        put("keywords", JSONObject().apply {
                                            put("type", "ARRAY")
                                            put("items", JSONObject().apply { put("type", "STRING") })
                                        })
                                    })
                                    put("required", JSONArray().apply {
                                        put("sceneNumber")
                                        put("narrationText")
                                        put("visualPrompt")
                                        put("durationSeconds")
                                        put("subtitle")
                                        put("imageSearchQuery")
                                        put("keywords")
                                    })
                                })
                            })
                        })
                        put("required", JSONArray().apply { put("scenes") })
                    }

                    val requestBodyJson = JSONObject().apply {
                        put("contents", JSONArray().put(contentsJson))
                        put("systemInstruction", systemInstructionJson)
                        put("generationConfig", JSONObject().apply {
                            put("responseMimeType", "application/json")
                            put("responseSchema", responseFormatJson)
                            put("temperature", 0.4)
                            put("maxOutputTokens", 8192)
                        })
                    }

                    val mediaType = "application/json; charset=utf-8".toMediaType()
                    val requestBody = requestBodyJson.toString().toRequestBody(mediaType)

                    val candidateKeys = resolveCandidateGeminiKeys(customGeminiKey)
                    if (candidateKeys.isEmpty()) {
                        throw java.io.IOException("No valid REST Gemini API key available (neither user nor app key configured).")
                    }

                    keyLoop@ for ((keyTierLabel, currentApiKey) in candidateKeys) {
                        Log.i("GeminiService", "Attempting analyzeScript direct REST API with $keyTierLabel...")
                        for (modelName in MODERN_MODELS) {
                            try {
                                Log.i("GeminiService", "[$keyTierLabel] Attempting direct REST API for analyzeScript with model: $modelName")
                                val url = "https://generativelanguage.googleapis.com/v1beta/models/$modelName:generateContent?key=$currentApiKey"
                                val request = Request.Builder()
                                    .url(url)
                                    .post(requestBody)
                                    .build()

                                val response = httpClient.newCall(request).execute()
                                if (response.isSuccessful) {
                                    val responseBodyString = response.body?.string() ?: ""
                                    val responseJson = JSONObject(responseBodyString)
                                    val candidates = responseJson.optJSONArray("candidates")
                                    val firstCandidate = candidates?.optJSONObject(0)
                                    val content = firstCandidate?.optJSONObject("content")
                                    val parts = content?.optJSONArray("parts")
                                    val sb = StringBuilder()
                                    if (parts != null) {
                                        for (p in 0 until parts.length()) {
                                            val partObj = parts.getJSONObject(p)
                                            val isThought = partObj.optBoolean("thought", false)
                                            val partText = partObj.optString("text", "")
                                            if (!isThought && partText.isNotEmpty()) {
                                                if (sb.isNotEmpty()) sb.append("\n")
                                                sb.append(partText)
                                            }
                                        }
                                    }
                                    val textResult = (if (sb.isNotEmpty()) sb.toString() else parts?.optJSONObject(0)?.optString("text") ?: "").trim()
                                    if (textResult.isNotEmpty()) {
                                        restResult = textResult
                                        Log.i("GeminiService", "[$keyTierLabel] Direct REST API analyzeScript completed successfully with model: $modelName")
                                        break@keyLoop
                                    }
                                } else {
                                    Log.w("GeminiService", "[$keyTierLabel] REST API call failed for $modelName with response code: ${response.code}")
                                    if (PriorityApiGateway.isAuthOrQuotaCritical(response.code)) {
                                        PriorityApiGateway.logKeyTierFailover(keyTierLabel, "next key tier", "HTTP ${response.code}")
                                        break
                                    }
                                }
                            } catch (e: Exception) {
                                Log.w("GeminiService", "[$keyTierLabel] REST API call failed for $modelName with exception: ${e.message}")
                            }
                        }
                    }
                    if (restResult.isEmpty()) {
                        throw java.io.IOException("All REST API keys (User and App) failed or returned empty text")
                    }
                    restResult
                },
                secondaryCall = {
                    var vertexResult = ""
                    val backends = listOf(
                        com.google.firebase.ai.type.GenerativeBackend.googleAI(), // Free Gemini Developer API (Firebase Spark plan)
                        com.google.firebase.ai.type.GenerativeBackend.vertexAI(location = "us-central1"),
                        com.google.firebase.ai.type.GenerativeBackend.vertexAI(location = "global")
                    )
                    Log.i("GeminiService", "Circuit breaker executing secondary Firebase AI endpoint for analyzeScript...")
                    backendLoop@ for (backend in backends) {
                        for (firebaseModel in FIREBASE_MODELS) {
                            try {
                                Log.i("GeminiService", "Attempting Firebase AI with model: $firebaseModel")
                                val model = Firebase.ai(backend = backend)
                                    .generativeModel(
                                        modelName = firebaseModel,
                                        generationConfig = aiGenerationConfig {
                                            responseMimeType = "application/json"
                                            temperature = 0.4f
                                            maxOutputTokens = 8192
                                        },
                                        systemInstruction = aiContent {
                                            text(publishingStyleGuidelines)
                                        }
                                    )
                                val response = model.generateContent(prompt)
                                val result = response.text?.trim() ?: ""
                                if (result.isNotEmpty()) {
                                    vertexResult = result
                                    PriorityApiGateway.logSuccessfulFailover("Storyboard Analysis", firebaseModel)
                                    Log.i("GeminiService", "Firebase AI Logic SDK analyzeScript completed successfully using model: $firebaseModel")
                                    break@backendLoop
                                }
                            } catch (fEx: Exception) {
                                Log.w("GeminiService", "Firebase Vertex AI analyzeScript failed with model $firebaseModel: ${fEx.message}")
                            }
                        }
                    }
                    vertexResult
                }
            )
        } catch (e: Exception) {
            Log.w("GeminiService", "Circuit breaker execution for analyzeScript caught error: ${e.message}")
        }

        if (text.isEmpty()) {
            Log.w("GeminiService", "Both REST and Firebase Vertex AI failed for analyzeScript. Checking BYOK UnifiedAiRouter...")
            try {
                val app = com.ritvyom.yashoraReelgenerator.YashoraApplication.getInstance()
                val router = app?.unifiedAiRouter
                if (router != null && router.hasAnyActiveProvider()) {
                    Log.i("GeminiService", "Attempting script storyboard analysis via BYOK Provider Router...")
                    val req = com.ritvyom.yashoraReelgenerator.data.ai.AiGenerationRequest(
                        prompt = prompt,
                        systemInstruction = "You are an expert AI video storyboard director. Analyze the script and return ONLY a valid JSON object with 'scenes' array where EVERY single sentence is mapped to an exact, unique visual scene and precise search query.",
                        capability = com.ritvyom.yashoraReelgenerator.data.ai.AiCapability.PROMPT_GENERATION
                    )
                    val byokResult = router.executeWithFallback(req)
                    if (byokResult.text.isNotBlank()) {
                        Log.i("GeminiService", "BYOK Provider Router analyzeScript succeeded using ${byokResult.providerUsed}!")
                        PriorityApiGateway.logSuccessfulFailover("Storyboard Analysis", "BYOK:${byokResult.providerUsed}")
                        text = byokResult.text.trim()
                    }
                }
            } catch (rEx: Exception) {
                Log.w("GeminiService", "BYOK Provider Router analyzeScript caught error: ${rEx.message}")
            }
        }

        if (text.isEmpty()) {
            Log.w("GeminiService", "All AI providers failed for analyzeScript. Falling back to sentence-by-sentence local parser.")
            return@withContext fallbackLocalParser(script, style, language, aspectRatio, imageSource)
        }

        try {
            if (text.isNotEmpty()) {
                val cleanedText = sanitizeAndRepairJson(text)
                
                val scenesJson: JSONArray = if (cleanedText.startsWith("[")) {
                    JSONArray(cleanedText)
                } else if (cleanedText.startsWith("{")) {
                    val rootObj = JSONObject(cleanedText)
                    if (rootObj.has("scenes")) {
                        rootObj.getJSONArray("scenes")
                    } else {
                        var foundArray: JSONArray? = null
                        val keys = rootObj.keys()
                        while (keys.hasNext()) {
                            val key = keys.next()
                            val value = rootObj.optJSONArray(key)
                            if (value != null) {
                                foundArray = value
                                break
                            }
                        }
                        foundArray ?: JSONArray()
                    }
                } else {
                    try {
                        JSONArray(cleanedText)
                    } catch (e: Exception) {
                        JSONObject(cleanedText).getJSONArray("scenes")
                    }
                }

                val totalScenesToProcess = scenesJson.length().coerceAtMost(100)
                val list = coroutineScope {
                    val deferredList = (0 until totalScenesToProcess).map { i ->
                        async(Dispatchers.IO) {
                            val obj = scenesJson.optJSONObject(i) ?: return@async null
                            val visPrompt = obj.optString("visualPrompt", "A scene depicting ${obj.optString("narrationText")}")
                            val imgSearchQuery = obj.optString("imageSearchQuery", "")
                            
                            val kwsJson = obj.optJSONArray("keywords")
                            val kwsList = mutableListOf<String>()
                            
                            // Always prepend and prioritize the optimized 1-3 word stock keyword tag at the front of the list
                            if (imgSearchQuery.isNotEmpty()) {
                                kwsList.add(imgSearchQuery)
                            }
                            
                            if (kwsJson != null) {
                                for (idx in 0 until kwsJson.length()) {
                                    val kw = kwsJson.optString(idx)
                                    if (kw.isNotEmpty() && kw != imgSearchQuery) {
                                        kwsList.add(kw)
                                    }
                                }
                            }
                            
                            if (kwsList.isEmpty()) {
                                val stopwords = setOf(
                                    "a", "an", "the", "and", "or", "but", "is", "are", "was", "were", "of", "to", "in", "on", "at", "by", "for", "with", "about",
                                    "this", "that", "it", "its", "you", "your", "my", "me", "we", "our", "us", "they", "them", "some", "any", "no", "not", "so",
                                    "can", "will", "show", "get", "make", "be", "have", "has", "had", "do", "does", "did", "from", "very", "scene", "style", "description",
                                    "captivating", "artistic", "representation", "masterpiece", "detail", "professional", "composition", "background", "photo", "image", "video"
                                )
                                val words = visPrompt.lowercase()
                                    .replace(Regex("[^\\p{L}\\s]"), "")
                                    .split(Regex("\\s+"))
                                    .filter { it.length >= 2 && !stopwords.contains(it) }
                                    .distinct()
                                    .take(3)
                                if (words.isNotEmpty()) kwsList.add(words.joinToString(" "))
                            }
                            
                            val narrText = obj.optString("narrationText", "")
                            val subText = obj.optString("subtitle", "")
                            val mediaUrl = getBestMatchingImage(
                                visPrompt, style, i, imgSearchQuery, aspectRatio, imageSource, customUnsplashKey,
                                narrationText = narrText, subtitle = subText
                            )
                            
                            val sc = Scene(
                                sceneNumber = obj.optInt("sceneNumber", i + 1),
                                narrationText = narrText,
                                visualPrompt = visPrompt,
                                durationSeconds = obj.optInt("durationSeconds", 5),
                                subtitle = subText,
                                mediaPath = mediaUrl,
                                remoteUrl = mediaUrl
                            )
                            sc.keywords = kwsList
                            sc
                        }
                    }
                    deferredList.mapNotNull { it.await() }
                }
                if (list.isNotEmpty()) {
                    val scriptWords = script.split(Regex("\\s+")).filter { it.isNotBlank() }
                    val parsedWordsCount = list.sumOf { sc ->
                        val text = if (sc.narrationText.isNotEmpty()) sc.narrationText else sc.subtitle
                        text.split(Regex("\\s+")).filter { it.isNotBlank() }.size
                    }
                    Log.d("GeminiService", "Script total words: ${scriptWords.size}, Parsed scenes total words: $parsedWordsCount across ${list.size} scenes. Preserving AI generated storyboards.")
                    
                    // If the script has significant length (>= 25 words) but AI only produced 1 or 2 scenes,
                    // fall back to sentence-by-sentence local parser so every sentence gets its own exact scene and prompt.
                    if (scriptWords.size >= 25 && list.size <= 2) {
                        Log.w("GeminiService", "AI returned only ${list.size} scenes for a ${scriptWords.size}-word script. Falling back to sentence-by-sentence local parser for full coverage.")
                        return@withContext fallbackLocalParser(script, style, language, aspectRatio, imageSource)
                    }
                    
                    return@withContext list
                }
            }
            fallbackLocalParser(script, style, language, aspectRatio, imageSource)
        } catch (e: Exception) {
            Log.e("GeminiService", "Gemini API error", e)
            fallbackLocalParser(script, style, language, aspectRatio, imageSource)
        }
    }

    private fun extractSemanticEnglishKeywords(prompt: String): List<String> {
        val lower = prompt.lowercase()
        val keywords = mutableListOf<String>()

        // 1. Space
        val spaceTerms = listOf(
            "space", "galaxy", "universe", "planet", "nebula", "star", "moon", "satellite", "rocket", "earth", "astronaut",
            "अंतरिक्ष", "ब्रह्मांड", "तारा", "तारे", "ग्रह", "चांद", "चंद्रमा", "आकाश", "गैलेक्सी", "रॉकेट", "पृथ्वी",
            "espacio", "universo", "estrella", "planeta", "luna", "cielo", "cohete", "tierra",
            "cosmos", "étoile", "planète", "terre", "فضاء", "كون", "نجم", "كوكب", "قمر", "أرض", "سفر",
            "মহাকাশ", "মহাবিশ্ব", "তারা", "নক্ষত্র", "গ্রহ", "চাঁদ", "রকেট", "পৃথিবী", "আকাশ"
        )
        if (spaceTerms.any { lower.contains(it) }) {
            keywords.add("outer space")
            if (lower.contains("star") || lower.contains("तारा") || lower.contains("तारे") || lower.contains("estrella") || lower.contains("étoile") || lower.contains("نجم") || lower.contains("তারা")) keywords.add("starry night sky")
            if (lower.contains("galaxy") || lower.contains("गैलेक्सी") || lower.contains("galaxia") || lower.contains("مجرة") || lower.contains("ছায়াপথ")) keywords.add("galaxy nebula")
            if (lower.contains("planet") || lower.contains("ग्रह") || lower.contains("planeta") || lower.contains("كوكب") || lower.contains("গ্রহ")) keywords.add("planet")
            if (lower.contains("moon") || lower.contains("चांद") || lower.contains("luna") || lower.contains("lune") || lower.contains("قمر") || lower.contains("চাঁদ")) keywords.add("moonlit moon")
        }

        // 2. Nature & Mountains & Forests
        val natureTerms = listOf(
            "nature", "mountain", "forest", "tree", "river", "valley", "lake", "landscape", "waterfall", "meadow", "pine",
            "प्रकृति", "पहाड़", "पर्वत", "जंगल", "वन", "पेड़", "नदी", "झरना", "घाटी", "झील", "मैदान", "पेड़",
            "naturaleza", "montaña", "bosque", "árbol", "río", "lago", "paisaje", "cascada", "valle",
            "montagne", "forêt", "arbre", "rivière", "lac", "paysage", "cascade", "vallée",
            "طبيعة", "جبل", "غابة", "شجرة", "نهر", "بحيرة", "شلال", "وادي",
            "প্রকৃতি", "পাহাড়", "জঙ্গল", "অরণ্য", "গাছ", "নদী", "ঝরনা", "উপত্যকা", "হ্রদ"
        )
        if (natureTerms.any { lower.contains(it) }) {
            keywords.add("nature landscape")
            if (lower.contains("mountain") || lower.contains("पहाड़") || lower.contains("montaña") || lower.contains("montagne") || lower.contains("جبل") || lower.contains("পাহাড়")) keywords.add("majestic mountain peaks")
            if (lower.contains("forest") || lower.contains("जंगल") || lower.contains("bosque") || lower.contains("forêt") || lower.contains("غابة") || lower.contains("জঙ্গল")) keywords.add("forest pine trees")
            if (lower.contains("river") || lower.contains("नदी") || lower.contains("río") || lower.contains("rivière") || lower.contains("نهر") || lower.contains("নদী")) keywords.add("scenic river stream")
            if (lower.contains("lake") || lower.contains("झील") || lower.contains("lago") || lower.contains("lac") || lower.contains("بحيرة") || lower.contains("হ্রদ")) keywords.add("peaceful lake water")
            if (lower.contains("waterfall") || lower.contains("झरना") || lower.contains("cascada") || lower.contains("cascade") || lower.contains("شلال") || lower.contains("ঝরনা")) keywords.add("epic waterfall flow")
        }

        // 3. Sunset & Sunrise & Beach & Ocean
        val waterTerms = listOf(
            "sunset", "sunrise", "beach", "sea", "ocean", "waves", "shore", "golden hour",
            "सूर्यास्त", "सूर्योदय", "समुद्र", "सागर", "तट", "किनारा", "लहरें", "धूप", "शाम", "सवेरा",
            "atardecer", "amanecer", "playa", "mar", "océano", "olas", "costa",
            "coucher", "plage", "océan", "vagues", "soleil",
            "غروب", "شروق", "بحر", "محيط", "شاطئ", "أمواج",
            "সূর্যাস্ত", "সূর্যোদয়", "সমুদ্র", "সৈকত", "সাগর", "ঢেউ"
        )
        if (waterTerms.any { lower.contains(it) }) {
            if (lower.contains("sunset") || lower.contains("सूर्यास्त") || lower.contains("atardecer") || lower.contains("coucher") || lower.contains("غروب") || lower.contains("সূর্যাস্ত") || lower.contains("शाम") || lower.contains("evening")) {
                keywords.add("sunset golden hour")
            } else {
                keywords.add("sunrise morning shine")
            }
            if (lower.contains("beach") || lower.contains("किनारा") || lower.contains("तट") || lower.contains("playa") || lower.contains("plage") || lower.contains("شاطئ") || lower.contains("সৈকত")) keywords.add("sandy beach coast")
            if (lower.contains("sea") || lower.contains("ocean") || lower.contains("समुद्र") || lower.contains("सागर") || lower.contains("mar") || lower.contains("océan") || lower.contains("بحر") || lower.contains("محيط") || lower.contains("সাগর")) keywords.add("epic ocean waves")
        }

        // 4. Technology & Computer & Robots & AI
        val techTerms = listOf(
            "technology", "tech", "computer", "robot", "ai", "artificial intelligence", "coding", "code", "laptop", "science", "futuristic",
            "तकनीक", "रोबोट", "एआई", "कंप्यूटर", "कम्प्यूटर", "लैपटॉप", "विज्ञान", "भविष्य", "सॉफ्टवेयर", "प्रौद्योगिकी",
            "tecnología", "computadora", "ordenador", "ciencia", "futurista",
            "technologie", "ordinateur", "intelligence artificielle",
            "تكنولوجيا", "ذكاء اصطناعي", "كمبيوتر", "روبرت", "علم", "تقنية",
            "প্রযুক্তি", "রোবট", "কম্পিউটার", "বিজ্ঞান", "ভবিষ্যৎ"
        )
        if (techTerms.any { lower.contains(it) }) {
            keywords.add("technology network")
            if (lower.contains("robot") || lower.contains("रोबोट") || lower.contains("روبرت") || lower.contains("রোবট")) keywords.add("cyber humanoid robot")
            if (lower.contains("ai") || lower.contains("एआई") || lower.contains("intelligence") || lower.contains("artificial")) keywords.add("artificial intelligence brain")
            if (lower.contains("code") || lower.contains("coding") || lower.contains("कंप्यूटर") || lower.contains("computadora") || lower.contains("ordinateur")) keywords.add("glowing digital code screen")
            if (lower.contains("laptop") || lower.contains("लैपटॉप")) keywords.add("modern laptop workspace")
        }

        // 5. Finance & Wealth & Money & Investment & Business
        val financeTerms = listOf(
            "finance", "wealth", "wealthy", "money", "invest", "investment", "gold", "coins", "rich", "stock", "stocks", "market", "business", "bank",
            "पैसा", "पैसे", "धन", "दौलत", "अमीर", "निवेश", "सोना", "सिक्के", "बैंक", "शेयर", "व्यापार", "बिजनेस", "मार्केट",
            "dinero", "riqueza", "rico", "oro", "monedas", "inversión", "banco", "bolsa", "negocios",
            "argent", "richesse", "riche", "or", "pièces", "investissement", "banque",
            "مال", "أموال", "ثروة", "ثري", "ذهب", "عملات", "استثمار", "بنك", "أعمال", "تجارة",
            "টাকা", "ধন", "দৌলত", "বিনিয়োগ", "স্বর্ণ", "মুদ্রা", "ব্যাংক", "ব্যবসা"
        )
        if (financeTerms.any { lower.contains(it) }) {
            keywords.add("finance wealth")
            if (lower.contains("money") || lower.contains("पैसा") || lower.contains("पैसे") || lower.contains("dinero") || lower.contains("argent") || lower.contains("أموال") || lower.contains("টাকা")) keywords.add("us dollar banknotes cash")
            if (lower.contains("invest") || lower.contains("investment") || lower.contains("stock") || lower.contains("share") || lower.contains("शेयर") || lower.contains("निवेश") || lower.contains("bolsa") || lower.contains("استثمار")) keywords.add("stock market candlestick chart")
            if (lower.contains("gold") || lower.contains("coin") || lower.contains("सोना") || lower.contains("सिक्के") || lower.contains("oro") || lower.contains("or") || lower.contains("ذهب") || lower.contains("স্বর্ণ")) keywords.add("gold bar coins premium")
            if (lower.contains("business") || lower.contains("bank") || lower.contains("व्यापार") || lower.contains("बैंक") || lower.contains("negocios") || lower.contains("أعمال")) keywords.add("modern skyscraper corporate business")
        }

        // 6. Books & Stories & Wisdom & History
        val bookTerms = listOf(
            "book", "books", "story", "stories", "library", "read", "reading", "wisdom", "knowledge", "history", "ancient",
            "किताब", "किताबें", "पुस्तक", "कहानी", "कहानियां", "लाइब्रेरी", "ज्ञान", "इतिहास", "पुराना", "प्राचीन",
            "libro", "libros", "historia", "biblioteca", "sabiduría", "conocimiento", "antiguo",
            "livre", "livres", "histoire", "bibliothèque", "sagesse", "connaissance", "ancien",
            "كتاب", "كتب", "قصة", "قصص", "مكتبة", "حكمة", "معرفة", "تاريخ", "قديم",
            "বই", "গল্প", "গ্রন্থাগার", "জ্ঞান", "ইতিহাস", "প্রাচীন"
        )
        if (bookTerms.any { lower.contains(it) }) {
            keywords.add("vintage library shelf")
            if (lower.contains("book") || lower.contains("किताब") || lower.contains("पुस्तक") || lower.contains("libro") || lower.contains("livre") || lower.contains("كتاب") || lower.contains("বই")) keywords.add("open book pages reading")
            if (lower.contains("wisdom") || lower.contains("knowledge") || lower.contains("story") || lower.contains("कहानी") || lower.contains("sabiduría") || lower.contains("حكمة") || lower.contains("জ্ঞান")) keywords.add("enlightening warm glow open book")
        }

        // 7. Success & Motivation & Mindset
        val motivationTerms = listOf(
            "success", "motivation", "motivational", "mindset", "inspire", "inspiration", "goal", "goals", "winner", "victory",
            "सफलता", "प्रेरणा", "जीत", "लक्ष्य", "मोटिवेशन", "सफल", "प्रेरित",
            "éxito", "motivación", "mente", "inspiración", "meta", "victoria",
            "succès", "motivation", "inspiration", "but", "victoire",
            "نجاح", "تحفيز", "إلهام", "هدف", "فوز", "نصر",
            "সাফল্য", "অনুপ্রেরণা", "লক্ষ্য", "জয়"
        )
        if (motivationTerms.any { lower.contains(it) }) {
            keywords.add("success achievements motivation")
            if (lower.contains("goal") || lower.contains("winner") || lower.contains("लक्ष্য") || lower.contains("जीत") || lower.contains("meta") || lower.contains("but") || lower.contains("লক্ষ্য")) keywords.add("climber standing on mountain peak victory")
        }

        // 8. Cyberpunk & Neon & Tokyo & City
        val cityTerms = listOf(
            "city", "street", "streets", "tokyo", "cyberpunk", "neon", "glow", "alley", "lights",
            "शहर", "सड़क", "सड़कें", "रोशनी", "लाइट", "नियॉन", "गली", "टोक्यो", "नगर",
            "ciudad", "calle", "luces", "neón", "tokio",
            "ville", "rue", "lumières", "néon", "tokyo",
            "مدينة", "شارع", "أضواء", "نيون", "طوكيو",
            "শহর", "রাস্তা", "আলো", "নিয়ন"
        )
        if (cityTerms.any { lower.contains(it) }) {
            keywords.add("cyberpunk urban cityscape")
            if (lower.contains("neon") || lower.contains("नियॉन") || lower.contains("neón") || lower.contains("néon")) keywords.add("neon lights street rain")
            if (lower.contains("tokyo") || lower.contains("टोक्यो") || lower.contains("tokio")) keywords.add("tokyo shibuya night crowds")
            if (lower.contains("alley") || lower.contains("गली") || lower.contains("street") || lower.contains("sarak")) keywords.add("moody city alleyway")
        }

        // 9. Aesthetic / Art / Abstract / Fluid
        val artTerms = listOf(
            "aesthetic", "art", "illustration", "painting", "fluid", "color", "colorful", "purple", "gradient", "pastel",
            "कला", "कलात्मक", "चित्र", "पेंटिंग", "रंग", "रंगीน", "डिजाइन", "रंगों का संगम", "कलर",
            "arte", "ilustración", "pintura", "color", "gradiente",
            "art", "illustration", "peinture", "couleur", "dégradé",
            "فن", "رسم", "تلوين", "ألوان", "لوحة",
            "শিল্প", "চিত্রকর্ম", "রঙ", "রঙিন"
        )
        if (artTerms.any { lower.contains(it) }) {
            keywords.add("creative graphic artistic composition")
            if (lower.contains("fluid") || lower.contains("gradient") || lower.contains("gradiente")) keywords.add("3d beautiful fluid flow gradient colors")
            if (lower.contains("pastel") || lower.contains("रंग") || lower.contains("couleur")) keywords.add("soft dreamscape painting colors")
        }

        // 10. Travel & Adventure
        val travelTerms = listOf(
            "travel", "journey", "adventure", "explore", "trip", "road trip", "flight", "plane", "luggage",
            "यात्रा", "सफर", "रोमांच", "घूमना", "पर्यटन", "विमान", "हवाई जहाज",
            "viaje", "aventura", "explorar", "avión",
            "voyage", "aventure", "explorer", "avion",
            "سفر", "مغامرة", "طائرة", "رحلة",
            "ভ্রমণ", "অভিযান", "যাত্ৰা"
        )
        if (travelTerms.any { lower.contains(it) }) {
            keywords.add("adventure exploration tourism")
            if (lower.contains("journey") || lower.contains("यात्रा") || lower.contains("safar") || lower.contains("سفر")) keywords.add("scenic road curving landscape")
            if (lower.contains("plane") || lower.contains("flight") || lower.contains("हवाई जहाज") || lower.contains("avion")) keywords.add("airplane flying in clouds sunrise")
        }

        // 11. Greeting / Welcome / Hello
        val greetTerms = listOf(
            "hello", "welcome", "greet", "greeting", "hey", "hi",
            "नमस्ते", "स्वागत", "अभिनंदन", "स्वागतम",
            "hola", "bienvenido",
            "bonjour", "bienvenue",
            "مرحباً", "أهلاً",
            "স্বাগতম"
        )
        if (greetTerms.any { lower.contains(it) }) {
            keywords.add("inviting beautiful warm welcome")
        }

        // 12. Subscribe / Thank You / Like / Share / Bell
        val subTerms = listOf(
            "subscribe", "like", "share", "thank you", "thanks", "bell", "comment",
            "धन्यवाद", "लाइक", "सब्सक्राइब", "शेयर", "घंटी",
            "gracias", "suscribirse",
            "merci", "s'abonner",
            "شكراً", "اشتراك",
            "ধন্যবাদ", "সাবস্ক্রাইব"
        )
        if (subTerms.any { lower.contains(it) }) {
            keywords.add("elegant dynamic social media icon layout feedback")
        }

        // 13. Gym, Fitness, Exercise, Health
        val fitnessTerms = listOf(
            "gym", "workout", "fitness", "healthy", "exercise", "run", "running", "yoga", "muscle", "sports", "athlete", "bodybuilding",
            "कसरत", "फिटनेस", "योग", "दौड़", "खेल", "जिम", "व्यायाम"
        )
        if (fitnessTerms.any { lower.contains(it) }) {
            keywords.add("fitness gym workout")
            if (lower.contains("yoga") || lower.contains("योग")) keywords.add("peaceful yoga balance pose")
            if (lower.contains("running") || lower.contains("दौड़")) keywords.add("morning runner track athlete")
        }

        // 14. Food, Cooking, Recipe, Kitchen, Chef, Baking, Spices, Ingredients, Dishes
        val foodTerms = listOf(
            "food", "cooking", "recipe", "delicious", "eat", "cafe", "coffee", "restaurant", "tea", "baking", "chef", "kitchen",
            "cook", "pan", "pot", "skillet", "stove", "oven", "spice", "spices", "seasoning", "salt", "pepper", "turmeric",
            "cumin", "coriander", "ginger", "garlic", "onion", "tomato", "vegetable", "veggies", "curry", "rice", "biryani",
            "pasta", "pizza", "noodles", "paneer", "chicken", "mutton", "fish", "soup", "salad", "dessert", "cake", "pastry",
            "breakfast", "lunch", "dinner", "snack", "tasty", "flavor", "batter", "dough", "fry", "frying", "boil", "boiling",
            "roast", "sauté", "chop", "chopping", "slice", "slicing", "stir", "stirring", "garnish", "culinary", "gourmet",
            "खाना", "भोजन", "चाय", "कॉफ़ी", "रसोई", "पकाना", "स्वादिष्ट", "रेसिपी", "पकवान", "शेफ", "पकाएं", "तलना", "तलें",
            "भूनना", "भूनें", "उबालना", "उबालें", "काटना", "काटें", "सामग्री", "तेल", "घी", "मक्खन", "मसाले", "मसाला", "नमक",
            "मिर्च", "हल्दी", "धनिया", "जीरा", "प्याज", "लहसुन", "अदरक", "टमाटर", "सब्जी", "दाल", "चावल", "रोटी", "पनीर",
            "बिरयानी", "पुलाव", "चिकन", "हलवा", "खीर", "मिठाई", "समोसा", "पकौड़ा", "डोसा", "इडली", "सूप", "सलाद", "कढ़ाई",
            "तवा", "कुकर", "आंच", "धीमी आंच", "गार्निश", "स्वाद", "जायका", "नाश्ता"
        )
        if (foodTerms.any { lower.contains(it) }) {
            keywords.add("delicious recipe cooking food dish")
            if (lower.contains("coffee") || lower.contains("tea") || lower.contains("चाय") || lower.contains("कॉफ़ी")) keywords.add("steaming warm coffee cup art")
            if (lower.contains("kitchen") || lower.contains("chef") || lower.contains("रसोई") || lower.contains("शेफ")) keywords.add("professional chef kitchen restaurant cooking")
            if (lower.contains("spice") || lower.contains("मसाले") || lower.contains("मसाला") || lower.contains("oil") || lower.contains("तेल") || lower.contains("घी")) keywords.add("sizzling spices cooking pan food recipe")
            if (lower.contains("curry") || lower.contains("दाल") || lower.contains("gravy")) keywords.add("rich simmering curry pot recipe")
            if (lower.contains("rice") || lower.contains("biryani") || lower.contains("चावल") || lower.contains("बिरयानी") || lower.contains("पुलाव")) keywords.add("fragrant biryani rice dish platter")
            if (lower.contains("cake") || lower.contains("bake") || lower.contains("baking") || lower.contains("हलवा") || lower.contains("मिठाई") || lower.contains("dessert")) keywords.add("freshly baked dessert sweet food plating")
            if (lower.contains("chop") || lower.contains("slice") || lower.contains("काटना") || lower.contains("काटें") || lower.contains("vegetable") || lower.contains("सब्जी")) keywords.add("chopping fresh vegetables cutting board")
        }

        // 15. Love, Couple, Relationship, Family, Friends
        val loveTerms = listOf(
            "love", "couple", "friend", "friends", "relationship", "romance", "family",
            "प्यार", "दोस्त", "मित्र", "परिवार"
        )
        if (loveTerms.any { lower.contains(it) }) {
            keywords.add("happy companions together lifestyle")
            if (lower.contains("love") || lower.contains("couple") || lower.contains("प्यार")) keywords.add("romantic sunset couple holding hands")
            if (lower.contains("friend") || lower.contains("friends") || lower.contains("दोस्त") || lower.contains("मित्र")) keywords.add("smiling friends group hugging joy")
        }

        // 16. Sad, Lonely, Melancholy, Heartbreak
        val sadTerms = listOf(
            "sad", "failure", "broken", "lonely", "cry", "crying", "darkness", "depression",
            "उदास", "दुखी", "हार", "अकेला", "रोत", "निराश"
        )
        if (sadTerms.any { lower.contains(it) }) {
            keywords.add("moody artistic photography expression")
            if (lower.contains("rain") || lower.contains("lone") || lower.contains("अकेला") || lower.contains("उदास")) keywords.add("pensive lonely person window rain drops")
        }

        // 17. Animals, Pets, Wildlife
        val animalTerms = listOf(
            "animal", "wildlife", "lion", "tiger", "dog", "cat", "deer", "eagle", "wolf", "horse", "elephant",
            "जानवर", "पशु", "शेर", "बाघ", "कुत्ता", "बिल्ली", "हाथी", "घोड़ा"
        )
        if (animalTerms.any { lower.contains(it) }) {
            keywords.add("majestic wildlife creatures")
            if (lower.contains("lion") || lower.contains("शेर")) keywords.add("powerful roaring african wild lion")
            if (lower.contains("tiger") || lower.contains("बाघ")) keywords.add("majestic siberian wild tiger")
        }

        return keywords
    }

    private fun getStyleDescriptors(style: String): String {
        return when (style.lowercase().trim()) {
            "cinematic" -> "high-end epic cinematic film style, dramatic anamorphic lighting, volumetric smoke, movie production style, gorgeous depth of field, 8k resolution, photorealistic"
            "realistic" -> "hyperdetailed realistic photography style, raw professional portrait, sharp details, true-to-life colors, studio lighting, 85mm lens, depth of field"
            "anime" -> "vibrant modern Japanese anime style, detailed colorful illustration, aesthetic digital painting, highly stylized, Makoto Shinkai aesthetic, gorgeous anime keys"
            "cartoon" -> "playful professional vector cartoon illustration style, bold outlines, vibrant flat colors, 2d vector art, clean sharp lines, artistic flat style"
            "oil painting" -> "classical fine oil painting texture style, thick impasto strokes, rich textured canvas paint, dramatic lighting shadow contrast, artistic masterpiece"
            "watercolor" -> "delicate fluid watercolor wash style, aesthetic dreamlike pastel colors, beautiful wet-on-wet paint textures, soft colorful splashes"
            "3d animation" -> "cute glossy 3d animation style, dynamic stylized characters, warm cinematic claymation, octane render, soft clean textures, vivid lighting"
            "pixar style" -> "concept character 3d rendering Pixar style, warm cinematic storytelling light, heart-warming whimsical aesthetic, highly detailed"
            "cyberpunk" -> "dark high-tech cyberpunk street scene, glowing neon lights, rain-slicked wet asphalt reflections, futuristic urban cityscape, holographic signs"
            "sci-fi" -> "epic hard sci-fi universe style, sleek futuristic spaceships, glowing hologram technologies, star nebula backdrop, galactic scale"
            "documentary" -> "raw authentic historical documentary photo, journalistic grain, honest natural lighting, real candid snapshot, archival photo realism"
            "storytelling" -> "whimsical bedtime storybook illustration style, warm cozy paint textures, soft glow elements, endearing whimsical fairy tale aesthetic"
            "minimalist" -> "clean sleek minimalist line and shape graphic style, highly simplified design, elegant negative space, aesthetic modern flat layout"
            "vintage" -> "nostalgic vintage 1970s film style, warm orange golden retro hues, classic scratchy 8mm camera light leaks, nostalgic dreamlike photograph"
            "fantasy" -> "enchanting magical fantasy concept art style, glowing fairy dust, magic spells, surreal starry sky, epic mythical setting"
            "sketch art" -> "realistic monochrome pencil graphite sketch style, meticulous cross-hatching textures, hand-drawn paper illustration"
            else -> "award winning stunning highly detailed professional artistic style, perfect composition"
        }
    }

    fun getBestMatchingImage(
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
        return MediaSelectionService.resolveBestMediaForScene(
            visualPrompt = visualPrompt,
            style = style,
            sceneNum = sceneNum,
            customSearchQuery = customSearchQuery,
            aspectRatio = aspectRatio,
            imageSource = imageSource,
            customUnsplashKey = customUnsplashKey,
            customPexelsKey = customPexelsKey,
            customPixabayKey = customPixabayKey,
            customSpoonacularKey = customSpoonacularKey,
            resolution = resolution,
            visualMedium = visualMedium,
            narrationText = narrationText,
            subtitle = subtitle,
            alreadyUsedUrls = alreadyUsedUrls
        )
    }

    fun extractSemiSemanticKeyword(sentence: String): String {
        val lower = sentence.lowercase().trim()
        val matches = mutableListOf<String>()

        fun containsWord(text: String, word: String): Boolean {
            val pattern = "(^|[^a-zA-Z0-9])${Regex.escape(word)}($|[^a-zA-Z0-9])".toRegex(RegexOption.IGNORE_CASE)
            return pattern.containsMatchIn(text)
        }

        // 1. Food, Recipe, Cooking, Ingredients, Dishes, Kitchen (HIGH PRIORITY)
        if (lower.contains("रेसिपी") || containsWord(lower, "recipe") || lower.contains("kaise banaye") || lower.contains("banane ki") || lower.contains("banate") || lower.contains("rasoi")) matches.add("delicious recipe cooking")
        if (lower.contains("मसाले") || lower.contains("मसाला") || containsWord(lower, "spice") || lower.contains("seasoning") || containsWord(lower, "masala")) matches.add("spices cooking pan")
        if (lower.contains("कढ़ाई") || lower.contains("पैन") || containsWord(lower, "pan") || containsWord(lower, "pot") || lower.contains("skillet") || lower.contains("kadhai") || lower.contains("bartan")) matches.add("cooking pan food")
        if (lower.contains("तेल") || lower.contains("घी") || lower.contains("मक्खन") || containsWord(lower, "oil") || containsWord(lower, "butter") || containsWord(lower, "ghee") || containsWord(lower, "tel")) matches.add("cooking oil sizzling pan")
        if (lower.contains("प्याज") || lower.contains("लहसुन") || lower.contains("अदरक") || lower.contains("टमाटर") || containsWord(lower, "onion") || containsWord(lower, "garlic") || containsWord(lower, "ginger") || containsWord(lower, "tomato") || containsWord(lower, "pyaz") || containsWord(lower, "tamatar") || containsWord(lower, "lahsun") || containsWord(lower, "adrak")) matches.add("chopping fresh vegetables kitchen")
        if (lower.contains("काटना") || lower.contains("काटें") || containsWord(lower, "chop") || containsWord(lower, "slice") || lower.contains("dicing") || containsWord(lower, "kaat")) matches.add("chopping vegetables cutting board")
        if (lower.contains("तलना") || lower.contains("तलें") || lower.contains("भूनना") || lower.contains("भूनें") || containsWord(lower, "fry") || lower.contains("frying") || lower.contains("sauté") || lower.contains("roast") || lower.contains("talna") || lower.contains("bhun")) matches.add("frying sizzling food pan")
        if (lower.contains("उबालना") || lower.contains("उबालें") || containsWord(lower, "boil") || lower.contains("simmer") || lower.contains("ubale") || lower.contains("ubalna")) matches.add("boiling food pot soup")
        if (lower.contains("पनीर") || containsWord(lower, "paneer") || containsWord(lower, "cheese")) matches.add("paneer curry dish")
        if (lower.contains("चिकन") || containsWord(lower, "chicken") || containsWord(lower, "mutton") || containsWord(lower, "meat") || containsWord(lower, "machli") || containsWord(lower, "fish")) matches.add("cooked chicken recipe dish")
        if (lower.contains("बिरयानी") || lower.contains("पुलाव") || lower.contains("चावल") || containsWord(lower, "rice") || containsWord(lower, "biryani") || containsWord(lower, "chawal") || containsWord(lower, "pulao")) matches.add("delicious biryani rice dish")
        if (lower.contains("सब्जी") || containsWord(lower, "vegetable") || containsWord(lower, "curry") || lower.contains("दाल") || containsWord(lower, "dal") || containsWord(lower, "sabji") || containsWord(lower, "sabzi") || containsWord(lower, "daal")) matches.add("steaming delicious curry dish")
        if (lower.contains("रोटी") || lower.contains("नान") || containsWord(lower, "bread") || containsWord(lower, "roti") || containsWord(lower, "naan") || containsWord(lower, "dough") || containsWord(lower, "paratha") || lower.contains("chapat")) matches.add("fresh bread roti baking")
        if (lower.contains("केक") || lower.contains("हलवा") || lower.contains("खीर") || lower.contains("मिठाई") || lower.contains("लड्डू") || lower.contains("लड्डु") || lower.contains("जलेबी") || lower.contains("गुलाब जामुन") || lower.contains("बर्फी") || lower.contains("पेड़ा") || containsWord(lower, "cake") || containsWord(lower, "dessert") || containsWord(lower, "sweet") || containsWord(lower, "halwa") || containsWord(lower, "mithai") || containsWord(lower, "kheer") || containsWord(lower, "laddu") || containsWord(lower, "ladoo") || containsWord(lower, "jalebi") || containsWord(lower, "barfi")) matches.add("delicious indian sweets dessert")
        if (lower.contains("समोसा") || lower.contains("पकौड़ा") || lower.contains("डोसा") || lower.contains("इडली") || containsWord(lower, "snack") || containsWord(lower, "dosa") || containsWord(lower, "samosa") || containsWord(lower, "pakoda")) matches.add("crispy delicious snack platter")
        if (lower.contains("चाय") || lower.contains("कॉफी") || containsWord(lower, "tea") || containsWord(lower, "coffee") || containsWord(lower, "chai") || containsWord(lower, "doodh") || lower.contains("दूध") || containsWord(lower, "milk")) matches.add("hot steaming tea chai pouring")
        if (lower.contains("रसोई") || lower.contains("शेफ") || containsWord(lower, "kitchen") || containsWord(lower, "chef") || containsWord(lower, "cook")) matches.add("chef kitchen cooking recipe")
        if (lower.contains("गार्निश") || lower.contains("स्वादिष्ट") || containsWord(lower, "delicious") || containsWord(lower, "tasty") || lower.contains("खाना") || lower.contains("भोजन") || containsWord(lower, "food") || containsWord(lower, "dish") || containsWord(lower, "meal") || containsWord(lower, "khana") || containsWord(lower, "bhojan")) matches.add("gourmet culinary food dish")

        // 2. Farming & Rural / Agriculture
        if (lower.contains("किसान") || containsWord(lower, "farmer") || containsWord(lower, "kisan") || containsWord(lower, "kisaan")) matches.add("farmer working field")
        if (lower.contains("खेत") || lower.contains("खेती") || containsWord(lower, "field") || containsWord(lower, "farm") || lower.contains("agriculture") || containsWord(lower, "khet") || containsWord(lower, "kheti")) matches.add("green agriculture crop field")
        if (lower.contains("फसल") || containsWord(lower, "crops") || containsWord(lower, "harvest") || lower.contains("अनाज") || containsWord(lower, "fasal") || containsWord(lower, "anaj")) matches.add("golden wheat harvest crop")
        if (lower.contains("ट्रैक्टर") || containsWord(lower, "tractor")) matches.add("tractor in field farm")
        if (lower.contains("गाँव") || lower.contains("गांव") || containsWord(lower, "village") || containsWord(lower, "rural") || containsWord(lower, "gaon") || containsWord(lower, "dehat")) matches.add("indian village rural peaceful")

        // 3. Healthcare & Medical
        if (lower.contains("डॉक्टर") || containsWord(lower, "doctor") || containsWord(lower, "physician") || containsWord(lower, "surgeon") || containsWord(lower, "vaidya")) matches.add("doctor stethoscope medical clinic")
        if (lower.contains("अस्पताल") || containsWord(lower, "hospital") || containsWord(lower, "clinic") || containsWord(lower, "aspataal")) matches.add("modern hospital corridor")
        if (lower.contains("दवाई") || containsWord(lower, "medicine") || containsWord(lower, "pills") || containsWord(lower, "pharmacy") || containsWord(lower, "dawa") || containsWord(lower, "dawai")) matches.add("pharmacy medicine pills")
        if (lower.contains("इलाज") || lower.contains("मरीज") || containsWord(lower, "patient") || containsWord(lower, "healthcare") || containsWord(lower, "mariz") || containsWord(lower, "mareez") || lower.contains("bimari")) matches.add("patient healthcare hospital care")

        // 4. Business, Money, Success & Career
        if (lower.contains("पैसा") || lower.contains("रुपया") || lower.contains("धन") || containsWord(lower, "money") || containsWord(lower, "cash") || containsWord(lower, "currency") || lower.contains("दौलत") || containsWord(lower, "paisa") || containsWord(lower, "paise") || containsWord(lower, "rupaye") || containsWord(lower, "kamai") || containsWord(lower, "kamana") || containsWord(lower, "kamaye")) matches.add("money cash currency")
        if (lower.contains("अमीर") || containsWord(lower, "rich") || containsWord(lower, "wealth") || containsWord(lower, "luxury") || lower.contains("करोड़") || containsWord(lower, "ameer") || containsWord(lower, "crorepati")) matches.add("wealth luxury success")
        if (lower.contains("गरीब") || containsWord(lower, "poor") || containsWord(lower, "poverty") || containsWord(lower, "garib") || containsWord(lower, "gareeb")) matches.add("humble modest worker")
        if (lower.contains("व्यापार") || containsWord(lower, "business") || containsWord(lower, "businessman") || lower.contains("व्यापारी") || containsWord(lower, "vyapar")) matches.add("business executive meeting office")
        if (lower.contains("दुकान") || lower.contains("बाजार") || containsWord(lower, "shop") || containsWord(lower, "market") || containsWord(lower, "store") || containsWord(lower, "dukan") || containsWord(lower, "bazaar")) matches.add("vibrant market local shop")
        if (lower.contains("ऑफिस") || lower.contains("कार्यालय") || containsWord(lower, "office") || containsWord(lower, "workplace") || lower.contains("नौकरी") || containsWord(lower, "job") || containsWord(lower, "naukri")) matches.add("modern office workspace desk")
        if (lower.contains("सफलता") || containsWord(lower, "success") || lower.contains("जीत") || containsWord(lower, "victory") || containsWord(lower, "win") || containsWord(lower, "winner") || containsWord(lower, "jeet")) matches.add("success achievement celebration winning")
        if (lower.contains("मेहनत") || lower.contains("hard work") || containsWord(lower, "struggle") || lower.contains("work hard") || containsWord(lower, "mehnat")) matches.add("determined person working hard")

        // 5. Social Media, Creator, Suspension, Warnings & Alerts
        if (containsWord(lower, "facebook") || containsWord(lower, "youtube") || containsWord(lower, "instagram") || containsWord(lower, "page") || containsWord(lower, "profile") || containsWord(lower, "suspend") || containsWord(lower, "blocked") || containsWord(lower, "block") || containsWord(lower, "copyright") || containsWord(lower, "monetization") || containsWord(lower, "policy") || containsWord(lower, "strike") || containsWord(lower, "warning") || containsWord(lower, "views") || containsWord(lower, "followers") || containsWord(lower, "reel") || containsWord(lower, "channel")) matches.add("computer smartphone screen error alert warning social media")

        // 6. Education & Learning
        if (lower.contains("स्कूल") || containsWord(lower, "school") || lower.contains("कक्षा") || containsWord(lower, "classroom")) matches.add("school classroom students")
        if (lower.contains("कॉलेज") || containsWord(lower, "college") || lower.contains("यूनिवर्सिटी") || containsWord(lower, "university")) matches.add("college campus university students")
        if (lower.contains("विद्यार्थी") || lower.contains("छात्र") || containsWord(lower, "student") || containsWord(lower, "students")) matches.add("student studying books library")
        if (lower.contains("शिक्षक") || lower.contains("टीचर") || containsWord(lower, "teacher") || containsWord(lower, "guru")) matches.add("teacher instructing chalkboard")
        if (lower.contains("किताब") || lower.contains("किताबें") || containsWord(lower, "book") || containsWord(lower, "books") || lower.contains("पढ़ना") || containsWord(lower, "study") || containsWord(lower, "library") || containsWord(lower, "kitab") || containsWord(lower, "padhai")) matches.add("books library study reading")

        // 7. Technology, Science & Space
        if (lower.contains("लैपटॉप") || lower.contains("कंप्यूटर") || containsWord(lower, "computer") || containsWord(lower, "laptop") || containsWord(lower, "coding") || containsWord(lower, "code") || lower.contains("प्रोग्रामिंग")) matches.add("computer laptop programming screen")
        if (lower.contains("मोबाइल") || lower.contains("फोन") || containsWord(lower, "smartphone") || containsWord(lower, "phone") || containsWord(lower, "app") || lower.contains("mobile app")) matches.add("smartphone mobile screen in hand")
        if (lower.contains("एआई") || lower.contains("रोबोट") || containsWord(lower, "ai") || containsWord(lower, "robot") || lower.contains("artificial intelligence")) matches.add("ai robot futuristic technology")
        if (lower.contains("अंतरिक्ष") || lower.contains("ब्रह्मांड") || containsWord(lower, "space") || containsWord(lower, "galaxy") || containsWord(lower, "universe") || lower.contains("रॉकेट") || containsWord(lower, "rocket") || containsWord(lower, "satellite")) matches.add("outer space galaxy stars nebula")

        // 8. Military, Police & Patriotism
        if (lower.contains("सेना") || lower.contains("सिपाही") || lower.contains("फौजी") || containsWord(lower, "soldier") || containsWord(lower, "army") || containsWord(lower, "military") || lower.contains("जवान") || containsWord(lower, "fauji") || containsWord(lower, "sipahi")) matches.add("brave army soldier uniform patrol")
        if (lower.contains("पुलिस") || containsWord(lower, "police") || containsWord(lower, "cop") || containsWord(lower, "officer")) matches.add("police officer uniform duty")
        if (lower.contains("तिरंगा") || lower.contains("झंडा") || containsWord(lower, "flag") || lower.contains("भारत") || containsWord(lower, "india") || containsWord(lower, "indian") || containsWord(lower, "tiranga") || containsWord(lower, "desh") || containsWord(lower, "bharat")) matches.add("indian flag tricolor fluttering")

        // 9. Sports, Dance & Fitness
        if (lower.contains("क्रिकेट") || containsWord(lower, "cricket") || lower.contains("बल्लेबाज") || lower.contains("गेंदबाज")) matches.add("cricket stadium match bat ball")
        if (lower.contains("फुटबॉल") || containsWord(lower, "football") || containsWord(lower, "soccer")) matches.add("football soccer match field")
        if (lower.contains("जिम") || lower.contains("कसरत") || containsWord(lower, "gym") || containsWord(lower, "workout") || containsWord(lower, "fitness") || lower.contains("दौड़ना") || containsWord(lower, "running") || containsWord(lower, "kasrat") || containsWord(lower, "bodybuilding")) matches.add("fitness gym athletic workout")
        if (lower.contains("नाच") || lower.contains("डांस") || lower.contains("नृत्य") || containsWord(lower, "dance") || lower.contains("dancing") || containsWord(lower, "dancer") || containsWord(lower, "nach")) matches.add("joyful energetic dancing celebration")

        // 10. Vehicles & Transportation
        if (lower.contains("कार") || lower.contains("गाड़ी") || lower.contains("गाड़ी") || containsWord(lower, "car") || containsWord(lower, "vehicle") || containsWord(lower, "gaadi") || containsWord(lower, "gadi")) matches.add("modern car driving road")
        if (lower.contains("ट्रेन") || lower.contains("रेल") || containsWord(lower, "railway") || containsWord(lower, "train") || lower.contains("स्टेशन") || containsWord(lower, "station") || containsWord(lower, "railgadi")) matches.add("train moving railway track station")
        if (lower.contains("हवाई जहाज") || lower.contains("विमान") || containsWord(lower, "airplane") || containsWord(lower, "plane") || containsWord(lower, "flight") || containsWord(lower, "airport") || lower.contains("hawai jahaz")) matches.add("airplane flying blue sky clouds")
        if (lower.contains("बाइक") || lower.contains("मोटरसाइकिल") || containsWord(lower, "bike") || containsWord(lower, "motorcycle")) matches.add("motorcycle motorbike open road")
        if (lower.contains("साइकिल") || containsWord(lower, "bicycle") || containsWord(lower, "cycle")) matches.add("bicycle rider scenic outdoor")

        // 11. Animals
        if (lower.contains("शेर") || containsWord(lower, "lion") || containsWord(lower, "sher")) matches.add("lion wildlife predator")
        if (lower.contains("बाघ") || lower.contains("चीता") || containsWord(lower, "tiger") || containsWord(lower, "cheetah") || containsWord(lower, "bagh")) matches.add("majestic tiger jungle")
        if (lower.contains("हाथी") || containsWord(lower, "elephant") || containsWord(lower, "hathi")) matches.add("elephant nature sanctuary")
        if (lower.contains("घोड़ा") || containsWord(lower, "horse") || containsWord(lower, "ghoda")) matches.add("majestic galloping horse")
        if (lower.contains("कुत्ता") || containsWord(lower, "dog") || containsWord(lower, "kutta") || containsWord(lower, "puppy")) matches.add("friendly dog pet")
        if (lower.contains("बिल्ली") || containsWord(lower, "cat") || containsWord(lower, "billi") || containsWord(lower, "kitten")) matches.add("cute playful cat")
        if (lower.contains("चिड़िया") || lower.contains("चिड़ियां") || containsWord(lower, "bird") || containsWord(lower, "birds") || lower.contains("पक्षी") || containsWord(lower, "chidiya")) matches.add("birds flying peaceful sky")
        if (lower.contains("गाय") || containsWord(lower, "cow") || containsWord(lower, "gai") || containsWord(lower, "gaay")) matches.add("holy cow indian rural pasture")

        // 12. Nature & Weather (Only if not a culinary context)
        val isCulinaryContext = matches.any { it.contains("food") || it.contains("recipe") || it.contains("cooking") || it.contains("dish") || it.contains("sweet") }
        if (!isCulinaryContext) {
            if (lower.contains("बारिश") || lower.contains("बरसात") || containsWord(lower, "rain") || containsWord(lower, "monsoon") || containsWord(lower, "baarish") || containsWord(lower, "barish") || containsWord(lower, "barsaat")) matches.add("heavy rain monsoon drops")
            if (lower.contains("बादल") || containsWord(lower, "clouds") || containsWord(lower, "cloudy") || containsWord(lower, "badal")) matches.add("dramatic cloudy sky")
            if (lower.contains("बिजली") || lower.contains("तूफान") || containsWord(lower, "storm") || containsWord(lower, "thunder") || containsWord(lower, "lightning") || containsWord(lower, "toofan")) matches.add("thunderstorm lightning night sky")
            if (lower.contains("पहाड़") || lower.contains("पर्वत") || containsWord(lower, "mountain") || containsWord(lower, "hills") || containsWord(lower, "pahad") || containsWord(lower, "parvat")) matches.add("majestic mountains peak landscape")
            if (lower.contains("नदी") || lower.contains("नदियां") || containsWord(lower, "river") || containsWord(lower, "lake") || lower.contains("झील") || lower.contains("झरना") || containsWord(lower, "waterfall") || containsWord(lower, "nadi") || containsWord(lower, "jharna")) matches.add("clear flowing river nature")
            if (lower.contains("समुद्र") || lower.contains("सागर") || containsWord(lower, "beach") || containsWord(lower, "ocean") || containsWord(lower, "sea") || containsWord(lower, "samudra")) matches.add("ocean waves beach coast")
            if (lower.contains("जंगल") || lower.contains("वन") || containsWord(lower, "forest") || containsWord(lower, "jungle") || lower.contains("पेड़") || containsWord(lower, "tree") || containsWord(lower, "ped")) matches.add("dense lush green forest trees")
            if (lower.contains("फूल") || lower.contains("गुलाब") || containsWord(lower, "flower") || containsWord(lower, "rose") || containsWord(lower, "garden") || containsWord(lower, "phool") || containsWord(lower, "gulab")) matches.add("blooming colorful flowers garden")
            if (lower.contains("सूरज") || lower.contains("धूप") || containsWord(lower, "sun") || containsWord(lower, "sunlight") || containsWord(lower, "suraj") || containsWord(lower, "dhoop")) matches.add("bright sun sunlight golden rays")
            if (lower.contains("सूर्यास्त") || containsWord(lower, "sunset") || lower.contains("शाम") || containsWord(lower, "evening") || containsWord(lower, "shaam")) matches.add("golden hour sunset silhouette")
            if (lower.contains("सूर्योदय") || containsWord(lower, "sunrise") || lower.contains("सुबह") || containsWord(lower, "morning") || containsWord(lower, "subah")) matches.add("early sunrise dawn morning light")
            if (lower.contains("चांद") || lower.contains("चंद्रमा") || containsWord(lower, "moon") || containsWord(lower, "chand") || containsWord(lower, "raat")) matches.add("glowing moon dark starry night")
            if (lower.contains("तारा") || lower.contains("तारे") || containsWord(lower, "stars") || containsWord(lower, "tare")) matches.add("starry sky milky way")
            if (lower.contains("सड़क") || lower.contains("सड़क") || containsWord(lower, "highway") || containsWord(lower, "road") || containsWord(lower, "sadak")) matches.add("open asphalt highway road journey")
        }

        // 13. Culture, Festivals & Celebrations
        if (lower.contains("मंदिर") || containsWord(lower, "temple") || lower.contains("पूजा") || containsWord(lower, "worship") || containsWord(lower, "prayers") || containsWord(lower, "mandir") || containsWord(lower, "pooja")) matches.add("sacred ancient hindu temple architecture")
        // STRICT DIWALI: Must explicitly mention Diwali or clay lamps, NOT plain verb 'दिया' (gave) or 'diya' (gave)
        if (lower.contains("दिवाली") || lower.contains("दीपावली") || containsWord(lower, "diwali") || containsWord(lower, "deepawali") || containsWord(lower, "deepavali") || lower.contains("दीपक") || lower.contains("दीये") || containsWord(lower, "diyas") || containsWord(lower, "deepak") || lower.contains("clay lamp") || lower.contains("oil lamp") || lower.contains("दीया जला") || lower.contains("diya jala")) matches.add("diwali oil lamp diyas lights celebration")
        // STRICT HOLI: Must explicitly mention Holi festival or Gulal, NOT plain color/rang
        if (lower.contains("होली") || containsWord(lower, "holi") || lower.contains("गुलाल") || containsWord(lower, "gulal") || lower.contains("रंग बरसे") || lower.contains("rang barse")) matches.add("holi festival vibrant gulal colors")

        // 14. People, Family, Friends & Advice/Suggestions
        if (lower.contains("परिवार") || containsWord(lower, "family") || containsWord(lower, "parivar")) matches.add("happy loving family together")
        if (lower.contains("माँ") || lower.contains("माता") || containsWord(lower, "mother") || containsWord(lower, "mom") || containsWord(lower, "maa") || containsWord(lower, "mata")) matches.add("mother caring loving child")
        if (lower.contains("पिता") || lower.contains("बापू") || containsWord(lower, "father") || containsWord(lower, "dad") || containsWord(lower, "pita") || containsWord(lower, "papa") || containsWord(lower, "baap")) matches.add("father walking guide child")
        if (lower.contains("भाई") || lower.contains("भैया") || containsWord(lower, "brother") || containsWord(lower, "bhai") || lower.contains("bhaiya")) matches.add("brother family smiling talking together")
        if (lower.contains("बहन") || lower.contains("दीदी") || containsWord(lower, "sister") || containsWord(lower, "behen") || lower.contains("didi")) matches.add("sister family smiling talking together")
        if (lower.contains("दोस्त") || lower.contains("मित्र") || containsWord(lower, "friends") || containsWord(lower, "friendship") || lower.contains("दोस्ती") || containsWord(lower, "dost") || containsWord(lower, "dosti") || containsWord(lower, "yaari") || containsWord(lower, "yaar")) matches.add("close friends laughing together")
        if (lower.contains("बच्चा") || lower.contains("बच्चे") || containsWord(lower, "child") || containsWord(lower, "kids") || containsWord(lower, "bacha") || containsWord(lower, "bache") || containsWord(lower, "bacche")) matches.add("cute happy children playing smiling")
        if (lower.contains("सलाह") || lower.contains("सुझाव") || lower.contains("टिप्स") || containsWord(lower, "advice") || lower.contains("suggestion") || lower.contains("sujhav") || containsWord(lower, "tips")) matches.add("friendly advice discussion conversation")
        if (lower.contains("घर") || lower.contains("महल") || containsWord(lower, "house") || containsWord(lower, "home") || containsWord(lower, "ghar") || containsWord(lower, "makan")) matches.add("warm cozy beautiful home")

        // 15. Emotions & Expressions
        if (lower.contains("खुशी") || lower.contains("खुश") || containsWord(lower, "happy") || containsWord(lower, "joy") || containsWord(lower, "smile") || lower.contains("हँसी") || containsWord(lower, "khushi") || containsWord(lower, "khush") || containsWord(lower, "hasi")) matches.add("joyful smiling happy face celebration")
        if (lower.contains("रोना") || lower.contains("आँसू") || lower.contains("उदास") || containsWord(lower, "sad") || lower.contains("crying") || containsWord(lower, "tears") || lower.contains("दुख") || containsWord(lower, "rona") || containsWord(lower, "aansu") || containsWord(lower, "udas") || containsWord(lower, "dukh")) matches.add("emotional thoughtful sad contemplative person")
        if (lower.contains("प्यार") || lower.contains("मोहब्बत") || containsWord(lower, "love") || containsWord(lower, "pyar") || containsWord(lower, "mohabbat")) matches.add("loving affection warm couple heart")
        if (lower.contains("आग") || lower.contains("अग्नि") || containsWord(lower, "fire") || containsWord(lower, "flame") || containsWord(lower, "aag")) matches.add("dramatic fire flames cinematic")

        // 16. Crime, Legal, CBI, Police, Investigation, Agencies, Court, Judge & Jail
        if (lower.contains("सीबीआई") || containsWord(lower, "cbi") || lower.contains("केंद्रीय जांच ब्यूरो")) matches.add("CBI investigation agency detective")
        if (lower.contains("ईडी") || containsWord(lower, "ed") || lower.contains("प्रवर्तन निदेशालय") || lower.contains("enforcement directorate")) matches.add("ED financial crime investigation office")
        if (lower.contains("एनआईए") || containsWord(lower, "nia")) matches.add("national investigation agency officers")
        if (lower.contains("जांच") || lower.contains("तफ्तीश") || lower.contains("छानबीन") || containsWord(lower, "investigation") || containsWord(lower, "probe") || containsWord(lower, "inquiry") || containsWord(lower, "detective") || containsWord(lower, "jaanch")) matches.add("detective crime investigation files")
        if (lower.contains("क्लोजर रिपोर्ट") || containsWord(lower, "closure report") || lower.contains("चार्जशीट") || containsWord(lower, "chargesheet")) matches.add("court legal closure report case document")
        if (lower.contains("कोर्ट") || lower.contains("अदालत") || lower.contains("कचहरी") || lower.contains("न्यायालय") || containsWord(lower, "court") || containsWord(lower, "courtroom") || containsWord(lower, "adalat")) matches.add("courtroom judge gavel legal trial")
        if (lower.contains("जज") || lower.contains("न्यायाधीश") || lower.contains("फैसला") || lower.contains("निर्णय") || containsWord(lower, "judge") || containsWord(lower, "verdict") || containsWord(lower, "judgment")) matches.add("judge gavel court verdict judgment")
        if (lower.contains("वकील") || lower.contains("वकालत") || containsWord(lower, "lawyer") || containsWord(lower, "advocate") || containsWord(lower, "attorney") || containsWord(lower, "vakil")) matches.add("lawyer arguing case court")
        if (lower.contains("पेश") || lower.contains("दाखिल") || containsWord(lower, "file") || containsWord(lower, "submit") || containsWord(lower, "dakhil")) matches.add("legal case files paperwork desk")
        if (lower.contains("रिपोर्ट") || lower.contains("दस्तावेज") || lower.contains("कागजात") || containsWord(lower, "report") || containsWord(lower, "documents") || containsWord(lower, "file") || containsWord(lower, "paperwork")) matches.add("official case report documents desk")
        if (lower.contains("सबूत") || lower.contains("साक्ष्य") || containsWord(lower, "evidence") || containsWord(lower, "proof") || containsWord(lower, "clue") || containsWord(lower, "saboot")) matches.add("crime scene evidence investigation forensic")
        if (lower.contains("गवाह") || lower.contains("गवाही") || lower.contains("बयान") || containsWord(lower, "witness") || containsWord(lower, "testimony") || containsWord(lower, "gawah")) matches.add("witness testimony court trial")
        if (lower.contains("आरोप") || lower.contains("आरोपी") || containsWord(lower, "accused") || containsWord(lower, "suspect") || containsWord(lower, "allegation") || containsWord(lower, "aarop")) matches.add("suspect interrogation police room")
        if (lower.contains("गिरफ्तार") || lower.contains("गिरफ्तारी") || lower.contains("हथकड़ी") || containsWord(lower, "arrest") || containsWord(lower, "handcuffs") || containsWord(lower, "custody") || containsWord(lower, "girftar")) matches.add("police arrest handcuffs crime")
        if (lower.contains("जेल") || lower.contains("कारागार") || lower.contains("सलाखें") || containsWord(lower, "jail") || containsWord(lower, "prison") || containsWord(lower, "cell") || containsWord(lower, "inmate")) matches.add("prison cell jail bars")
        if (lower.contains("हत्या") || lower.contains("कत्ल") || lower.contains("मर्डर") || lower.contains("मौत") || containsWord(lower, "murder") || containsWord(lower, "crime") || containsWord(lower, "kill") || containsWord(lower, "hatya") || containsWord(lower, "katl")) matches.add("crime scene yellow tape police investigation")
        if (lower.contains("रहस्य") || lower.contains("राज") || containsWord(lower, "mystery") || containsWord(lower, "secret") || containsWord(lower, "rahasya") || containsWord(lower, "unsolved")) matches.add("mystery detective magnifying glass clues")
        if (lower.contains("सच्चाई") || lower.contains("सच") || lower.contains("खुलासा") || containsWord(lower, "truth") || containsWord(lower, "expose") || containsWord(lower, "revelation") || containsWord(lower, "khulasa")) matches.add("breaking revelation expose headline news")
        if (lower.contains("घोटाला") || lower.contains("भ्रष्टाचार") || containsWord(lower, "scam") || containsWord(lower, "corruption") || containsWord(lower, "fraud") || containsWord(lower, "ghotala")) matches.add("financial fraud corruption money investigation")
        if (lower.contains("सवाल") || lower.contains("प्रश्न") || containsWord(lower, "question") || containsWord(lower, "doubt") || containsWord(lower, "sawal") || containsWord(lower, "prashna")) matches.add("unsolved question inquiry investigation")
        if (lower.contains("एजेंसी") || lower.contains("एजेंसियों") || containsWord(lower, "agency") || containsWord(lower, "agencies") || containsWord(lower, "department")) matches.add("investigation agency intelligence office")

        // 17. Politics, Government & News
        if (lower.contains("सरकार") || containsWord(lower, "government") || containsWord(lower, "sarkar") || containsWord(lower, "ministry")) matches.add("government official building parliament")
        if (lower.contains("संसद") || containsWord(lower, "parliament") || lower.contains("विधानसभा") || containsWord(lower, "sansad")) matches.add("parliament building democracy debate")
        if (lower.contains("नेता") || lower.contains("मंत्री") || lower.contains("प्रधानमंत्री") || lower.contains("मुख्यमंत्री") || containsWord(lower, "minister") || containsWord(lower, "leader") || containsWord(lower, "neta")) matches.add("political leader podium speech")
        if (lower.contains("न्यूज") || lower.contains("समाचार") || lower.contains("मीडिया") || lower.contains("प्रेस") || containsWord(lower, "news") || containsWord(lower, "press") || containsWord(lower, "media") || containsWord(lower, "reporter")) matches.add("press conference media microphones cameras")

        // 18. Medical, Health & Science
        if (lower.contains("अस्पताल") || lower.contains("डॉक्टर") || containsWord(lower, "doctor") || containsWord(lower, "hospital") || containsWord(lower, "clinic") || lower.contains("इलाज") || containsWord(lower, "treatment") || containsWord(lower, "medicine") || lower.contains("दवा") || lower.contains("दवाई")) matches.add("doctor hospital medical examination")
        if (lower.contains("स्वास्थ्य") || containsWord(lower, "health") || containsWord(lower, "wellness") || containsWord(lower, "fitness")) matches.add("health wellness nutrition lifestyle")

        // 19. Agriculture, Farming & Rural Life
        if (lower.contains("किसान") || containsWord(lower, "farmer") || containsWord(lower, "agriculture") || lower.contains("खेती") || lower.contains("खेत") || containsWord(lower, "farm") || containsWord(lower, "kisan") || containsWord(lower, "khet") || containsWord(lower, "crop") || containsWord(lower, "crops")) matches.add("indian farmer agriculture crop field")

        if (matches.isNotEmpty()) {
            return matches.distinct().take(2).joinToString(" ")
        }

        // Transliterate / extract clean English nouns if Latin letters present
        val stopwords = setOf(
            "a", "an", "the", "and", "or", "but", "is", "are", "was", "were", "of", "to", "in", "on", "at", "by", "for", "with", "about",
            "this", "that", "it", "its", "you", "your", "my", "me", "we", "our", "us", "they", "them", "some", "any", "no", "not", "so",
            "can", "will", "show", "get", "make", "be", "have", "has", "had", "do", "does", "did", "from", "very",
            // Common Roman Hindi / Hinglish stopwords:
            "kare", "karo", "karna", "karne", "kaise", "hota", "hote", "hoti", "hoga", "hogi", "hoge", "raha", "rahe", "rahi", "hai", "hain", "hoon",
            "tha", "the", "thi", "mera", "meri", "mere", "apna", "apne", "apni", "tera", "teri", "tere", "uska", "uske", "uski", "inka", "inke", "unki",
            "yeh", "ye", "woh", "wo", "isme", "usme", "isse", "usse", "jisse", "jisme", "agar", "magar", "lekin", "aur", "ya", "par", "pe", "ko",
            "se", "me", "mein", "ka", "ke", "ki", "bhi", "toh", "to", "hi", "ab", "kab", "jab", "tab", "kuch", "koi", "kisi", "sab", "sabhi",
            "aaj", "kal", "hum", "tum", "aap", "log", "baat", "chahiye", "sakte", "sakta", "sakti", "jaise", "waise"
        )
        val latinWords = lower.replace(Regex("[^a-zA-Z\\s]"), "")
            .split(Regex("\\s+"))
            .filter { it.isNotEmpty() && !stopwords.contains(it) }

        if (latinWords.isNotEmpty()) {
            return latinWords.take(3).joinToString(" ")
        }

        // Smart Hindi Dictionary Tokenizer & Mapper
        val hindiDict = mapOf(
            "सीबीआई" to "CBI", "जांच" to "investigation", "संभाली" to "handle", "क्लोजर" to "closure",
            "रिपोर्ट" to "report", "कोर्ट" to "court", "अदालत" to "courtroom", "कचहरी" to "courtroom",
            "पेश" to "file submit", "दाखिल" to "file", "सबूत" to "evidence", "साक्ष्य" to "evidence",
            "गवाह" to "witness", "बयान" to "statement", "फैसला" to "verdict", "निर्णय" to "judgment",
            "सोचा" to "thought inquiry", "सोच" to "contemplative thinking", "सवाल" to "question inquiry",
            "प्रश्न" to "question", "हत्या" to "crime murder", "कत्ल" to "crime murder", "मौत" to "death crime",
            "सच्चाई" to "truth reality", "सच" to "truth", "रहस्य" to "mystery detective", "राज" to "mystery secret",
            "खुलासा" to "expose revelation", "पुलिस" to "police officers", "थाना" to "police station",
            "एजेंसी" to "investigation agency", "एजेंसियों" to "investigation agencies", "सरकार" to "government",
            "दस्तावेज" to "case documents", "कागजात" to "case papers", "अधिकारी" to "officers", "अफसर" to "officers",
            "जज" to "judge gavel", "वकील" to "lawyer", "जेल" to "prison", "हथकड़ी" to "handcuffs",
            "आरोप" to "charges accused", "एफआईआर" to "FIR police report", "मुकदमा" to "lawsuit court case",
            "समय" to "clock time ticking", "वक्त" to "time clock", "सपना" to "dream vision",
            "रास्ता" to "road journey path", "मंजिल" to "destination journey", "आवाज" to "audio microphone",
            "कहानी" to "storytelling history", "शक्ति" to "power strength", "प्रकाश" to "bright light glow",
            "अंधेरा" to "dark shadows mystery", "खतरा" to "danger warning", "सुरक्षा" to "security protection",
            "देश" to "country nation flag", "शहर" to "city street modern", "गाड़ी" to "car vehicle",
            "खाना" to "delicious food meal", "रसोई" to "cooking kitchen", "मिठाई" to "indian sweets dessert",
            "चाय" to "tea cup steam", "कॉफी" to "coffee cup aroma", "दुकान" to "shop market store",
            "बाजार" to "busy marketplace street", "व्यापार" to "business commerce trade", "पैसा" to "money cash currency",
            "रुपया" to "currency notes money", "दौलत" to "wealth prosperity", "गरीब" to "humble poor lifestyle",
            "अमीर" to "luxury lifestyle wealthy", "सफलता" to "success achievement victory", "जीत" to "victory celebration champion",
            "हार" to "struggle challenge perseverance", "मेहनत" to "hard work dedication effort",
            "मंदिर" to "ancient hindu temple", "भगवान" to "sacred idol worship", "प्रार्थना" to "hands folded prayer devotion",
            "अस्पताल" to "hospital ward clinic", "दवा" to "medicine pharmaceutical", "मरीज" to "patient care hospital",
            "किसान" to "farmer field agriculture", "खेत" to "lush green crop field", "फसल" to "golden wheat crop harvest",
            "बारिश" to "rain drops storm", "धूप" to "golden sunlight sun rays", "नदी" to "flowing river water nature",
            "पहाड़" to "majestic mountain peak", "जंगल" to "lush green forest nature", "पेड़" to "ancient majestic green tree",
            "सूरज" to "sunrise dawn morning light", "चांद" to "glowing moon dark night", "तारे" to "starry night sky galaxy"
        )

        val hindiTokens = lower.split(Regex("[\\s,।!?|]+")).filter { it.length >= 2 }
        val mappedTokens = mutableListOf<String>()
        for (token in hindiTokens) {
            for ((hindiKey, engVal) in hindiDict) {
                if (token.contains(hindiKey)) {
                    mappedTokens.add(engVal)
                    break
                }
            }
            if (mappedTokens.size >= 3) break
        }

        if (mappedTokens.isNotEmpty()) {
            return mappedTokens.distinct().joinToString(" ")
        }

        val dynamicContextFallbacks = listOf(
            "cinematic narrative storytelling visual",
            "dramatic atmospheric storytelling view",
            "high detail authentic narrative moment",
            "inspirational cinematic visual perspective",
            "focused dynamic narrative action shot",
            "evocative expressive narrative scene"
        )
        val fallbackIndex = Math.abs(sentence.hashCode()) % dynamicContextFallbacks.size
        return dynamicContextFallbacks[fallbackIndex]
    }

    fun fallbackLocalParser(script: String, style: String, language: String = "English", aspectRatio: String = "9:16", imageSource: String = "Unsplash"): List<Scene> {
        val trimmed = script.trim()
        if (trimmed.isEmpty()) {
            val defaultPrompt = "A breathtaking cinematic sequence in authentic $style style. High-detail camera framing capturing vibrant atmosphere with cinematic volumetric lighting. Immersive 4K visual composition."
            return listOf(
                Scene(
                    sceneNumber = 1,
                    narrationText = if (language.equals("Hindi", ignoreCase = true)) "हमारी रचनात्मक यात्रा की शुरुआत हो रही है।" else "Starting our creative journey.",
                    visualPrompt = defaultPrompt,
                    durationSeconds = 5,
                    subtitle = if (language.equals("Hindi", ignoreCase = true)) "हमारी रचनात्मक यात्रा शुरू हो रही है..." else "Starting our journey...",
                    mediaPath = getBestMatchingImage(defaultPrompt, style, 0, "", aspectRatio, imageSource),
                    remoteUrl = getBestMatchingImage(defaultPrompt, style, 0, "", aspectRatio, imageSource)
                )
            )
        }

        // Clean script of scriptwriting markers, cue tags, and formatting that aren't spoken narration
        val cleanScript = trimmed
            .replace(Regex("(?i)\\[(Hook|Intro|Scene\\s*\\d+|Outro|CTA|Visual|Narration|Voiceover|Audio|Music|SFX|Sound)[^\\]]*\\]"), "")
            .replace(Regex("(?i)^(Hook|Intro|Scene\\s*\\d+|Outro|CTA|Visual|Narration|Voiceover):", RegexOption.MULTILINE), "")
            .replace(Regex("\\*\\*|__|[*_#]"), "")
            .trim()

        // Split intelligently by sentence boundaries in English, Hindi, and common punctuation
        val initialSentences = cleanScript.split(Regex("(?<=[?!।|\n])\\s+|(?<=\\.)\\s+(?=[A-Za-z\\u0900-\\u097F]|\\$)"))
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("//") }

        // Expand/split further any sentence that is too long (over 10 words or 60 characters) to ensure dynamic sentence-by-sentence scenes
        val sentences = mutableListOf<String>()
        for (item in initialSentences) {
            val words = item.split("\\s+".toRegex()).filter { it.isNotBlank() }
            if (words.size > 10) {
                // Split by commas, semicolons, native punctuation or key conjunction markers (Hindi "और", "तो", "तथा", "लेकिन", English "and", "or", "but")
                val subParts = item.split(Regex("(?<=[,;，、])\\s+|\\s+(और|तो|तथा|लेकिन|किन्तु|परन्तु|इसलिए|क्योंकि|and|or|but|so|because|while|then)\\s+"))
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                if (subParts.size > 1) {
                    sentences.addAll(subParts)
                } else {
                    // Fallback to chunk every 7-9 words
                    val chunked = mutableListOf<String>()
                    val sb = StringBuilder()
                    var wc = 0
                    for (w in words) {
                        sb.append(w).append(" ")
                        wc++
                        if (wc >= 8) {
                            chunked.add(sb.toString().trim())
                            sb.setLength(0)
                            wc = 0
                        }
                    }
                    if (sb.isNotEmpty()) {
                        chunked.add(sb.toString().trim())
                    }
                    sentences.addAll(chunked)
                }
            } else {
                sentences.add(item)
            }
        }

        val cameraAngles = listOf(
            "Cinematic close-up framing focusing on subject expressions and intricate details",
            "Dynamic medium camera tracking shot with realistic motion and focus",
            "Atmospheric wide-angle cinematic vista capturing immersive environment",
            "Intense eye-level framing emphasizing dramatic narrative action",
            "Cinematic low-angle camera perspective showcasing prominence and scale",
            "High-detail macro perspective capturing authentic texture details and fine elements"
        )
        val lightingAtmospheres = listOf(
            "volumetric natural sunlight with warm cinematic highlights",
            "dramatic mood lighting with high-contrast shadows and clean depth of field",
            "soft golden hour illumination with realistic reflections and subtle warm glow",
            "crisp studio lighting with authentic color grading and sharp contours",
            "vivid ambient illumination with cinematic atmosphere and balanced exposure"
        )

        return sentences.mapIndexed { index, rawSentence ->
            val sceneNum = index + 1
            val cleanSentence = rawSentence.replace(Regex("[\"\\n\\r]"), " ").trim()
            val searchQuery = extractSemiSemanticKeyword(cleanSentence)
            
            val chosenAngle = cameraAngles[index % cameraAngles.size]
            val chosenLighting = lightingAtmospheres[index % lightingAtmospheres.size]
            
            // Rich multi-sentence prompt directly referencing the sentence, camera angle, lighting, and style
            val visPrompt = "A cinematic sequence in $style style capturing: \"$cleanSentence\". " +
                "$chosenAngle focusing on $searchQuery. " +
                "Rendered with $chosenLighting, highly detailed textures, and dramatic depth of field. " +
                "Immersive 4K visual composition perfectly matching the voiceover narration."
            
            val processedSentence = translateLocalToLanguage(cleanSentence, language)
            
            val resolvedUrl = getBestMatchingImage(
                visualPrompt = visPrompt,
                style = style,
                sceneNum = index,
                customSearchQuery = searchQuery,
                aspectRatio = aspectRatio,
                imageSource = imageSource,
                narrationText = processedSentence,
                subtitle = processedSentence
            )
            
            val wordCount = processedSentence.split(Regex("\\s+")).filter { it.isNotBlank() }.size
            val durationSec = kotlin.math.max(3, kotlin.math.min(7, (wordCount / 2.5).toInt().coerceAtLeast(3)))
            
            val sc = Scene(
                sceneNumber = sceneNum,
                narrationText = processedSentence,
                visualPrompt = visPrompt,
                durationSeconds = durationSec,
                subtitle = processedSentence,
                mediaPath = resolvedUrl,
                remoteUrl = resolvedUrl
            )
            val kwTokens = searchQuery.split(Regex("\\s+")).filter { it.length > 2 && it.lowercase() !in setOf("and", "the", "for", "with", "shot", "view", "scene") }
            sc.keywords = if (kwTokens.isNotEmpty()) kwTokens else listOf(searchQuery)
            sc
        }
    }

    private fun translateLocalToLanguage(text: String, language: String): String {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return ""
        val lang = language.lowercase().trim()
        if (lang == "english") return trimmed
        
        val lower = trimmed.lowercase()
        
        // 1. Hindi (Already completed & optimized!)
        if (lang == "hindi") {
            val hasNativeChars = trimmed.any { it.code in 0x0900..0x097F }
            if (hasNativeChars) return trimmed
            return when {
                lower.contains("hello") || lower.contains("hi there") || lower.contains("welcome") -> "नमस्ते दोस्तों! आपका स्वागत है। "
                lower.contains("starting our journey") || lower.contains("creative journey") -> "हमारी रचनात्मक यात्रा की शुरुआत हो रही है।"
                lower.contains("space") || lower.contains("universe") || lower.contains("galaxy") || lower.contains("star") -> "अनंत अंतरिक्ष और चमकीले तारों की रहस्यमयी दुनिया में।"
                lower.contains("nature") || lower.contains("mountain") || lower.contains("tree") || lower.contains("forest") || lower.contains("river") -> "प्रकृति की इस शांत और खूबसूरत वादियों में।"
                lower.contains("cyberpunk") || lower.contains("neon") || lower.contains("tokyo") || lower.contains("city") -> "चमकती नियॉन लाइटों और आधुनिक भविष्य के शहरों के बीच।"
                lower.contains("anime") || lower.contains("scenery") || lower.contains("cartoon") -> "एक सुंदर एनिमेशन और कलात्मक दृश्यों के रूप में।"
                lower.contains("sunset") || lower.contains("beach") || lower.contains("sea") || lower.contains("ocean") -> "खुशनुमा शाम में ढलता हुआ सूरज और समुद्र की लहरें।"
                lower.contains("technology") || lower.contains("robot") || lower.contains("ai") || lower.contains("computer") || lower.contains("science") -> "आधुनिक एआई तकनीक और विज्ञान के इस अनोखे दौर में।"
                lower.contains("finance") || lower.contains("wealth") || lower.contains("invest") || lower.contains("money") -> "पैसा, शेयर बाजार और धन कमाने के सफल तरीकों के बारे में।"
                lower.contains("book") || lower.contains("story") || lower.contains("read") || lower.contains("wisdom") || lower.contains("history") -> "ज्ञान की अद्भुत किताबों और पुरानी कहानियों के सफर पर।"
                lower.contains("aesthetic") || lower.contains("color") || lower.contains("fluid") || lower.contains("purple") -> "डिजिटल रंगों और कलात्मक डिज़ाइनों का यह शानदार संगम।"
                lower.contains("success") || lower.contains("motivation") || lower.contains("mindset") -> "जीवन में सफलता पाने और हमेशा प्रेरित रहने के अनमोल विचार।"
                lower.contains("today") || lower.contains("learn") -> "आज हम कुछ बेहद मज़ेदार और नया सीखने वाले हैं।"
                lower.contains("thank you") || lower.contains("subscribe") || lower.contains("like") -> "हमारे साथ जुड़ने के लिए धन्यवाद, इस वीडियो को लाइक ज़रूर करें।"
                else -> {
                    // Smart transliterated fallback to give a very realistic Hinglish/Hindi flow!
                    val dict = mapOf(
                        "space" to "अंतरिक्ष",
                        "star" to "तারা",
                        "earth" to "पृथ्वी",
                        "world" to "दुनिया",
                        "nature" to "प्रकृति",
                        "mountain" to "पहाड़",
                        "river" to "नदी",
                        "forest" to "जंगल",
                        "sunset" to "सूर्यास्त",
                        "beach" to "समुद्र तट",
                        "ocean" to "समुद्र",
                        "sun" to "सूरज",
                        "moon" to "चांद",
                        "technology" to "तकनीक",
                        "robot" to "रोबोट",
                        "ai" to "एआई",
                        "computer" to "कंप्यूटर",
                        "wealth" to "धन",
                        "money" to "पैसा",
                        "invest" to "निवेश",
                        "book" to "किताब",
                        "story" to "कहानी",
                        "wisdom" to "ज्ञान",
                        "history" to "इतिहास",
                        "success" to "सफलता",
                        "motivation" to "प्रेरणा",
                        "future" to "भವಿष्य",
                        "time" to "समय",
                        "life" to "जीवन",
                        "today" to "आज",
                        "we" to "हम",
                        "learn" to "सीखेंगे",
                        "about" to "के बारे में",
                        "beautiful" to "शानदार",
                        "is" to "है",
                        "are" to "हैं"
                    )
                    var hindiW = trimmed
                    // Let's create an elegant, high-impact Hindi equivalent of the sentence
                    if (lower.contains("about")) {
                        val topic = trimmed.split(" ").lastOrNull()?.replace(".", "")?.lowercase() ?: ""
                        val topicHindi = dict[topic] ?: topic
                        hindiW = "चलिए आज हम बात करते हैं $topicHindi के बारे में!"
                    } else if (lower.contains("learn")) {
                        hindiW = "आज हम कुछ अद्भुत और बिल्कुल नया सीखेंगे!"
                    } else if (lower.contains("welcome") || lower.contains("hello")) {
                        hindiW = "नमस्ते दोस्तों! आपका बहुत-बहुत स्वागत है।"
                    } else {
                        // Use a generic beautiful description
                        hindiW = "इस दृश्य में आप जीवन के अद्भुत पलों को देख सकते हैं।"
                    }
                    hindiW
                }
            }
        }

        // 2. Spanish
        if (lang == "spanish") {
            return when {
                lower.contains("hello") || lower.contains("hi there") || lower.contains("welcome") -> "¡Hola a todos! Bienvenidos de nuevo al canal."
                lower.contains("starting our journey") || lower.contains("creative journey") -> "Comenzamos nuestro viaje creativo hoy."
                lower.contains("space") || lower.contains("universe") || lower.contains("galaxy") || lower.contains("star") -> "Un fascinante viaje por el espacio infinito rodeado de estrellas brillantes."
                lower.contains("nature") || lower.contains("mountain") || lower.contains("tree") || lower.contains("forest") || lower.contains("river") -> "Un paseo tranquilo en plena naturaleza con impresionantes montañas."
                lower.contains("cyberpunk") || lower.contains("neon") || lower.contains("tokyo") || lower.contains("city") -> "Entre brillantes luces de neón y las dinámicas ciudades futuristas."
                lower.contains("anime") || lower.contains("scenery") || lower.contains("cartoon") -> "Una hermosa escena ilustrada al estilo animación artística."
                lower.contains("sunset") || lower.contains("beach") || lower.contains("sea") || lower.contains("ocean") -> "Disfrutando de un atardecer dorado con el suave sonido de las olas."
                lower.contains("technology") || lower.contains("robot") || lower.contains("ai") || lower.contains("computer") || lower.contains("science") -> "Adentrándonos en la era moderna de la inteligencia artificial y robótica."
                lower.contains("finance") || lower.contains("wealth") || lower.contains("invest") || lower.contains("money") -> "Explorando secretos sobre inversiones, dinero y abundancia financiera."
                lower.contains("book") || lower.contains("story") || lower.contains("read") || lower.contains("wisdom") -> "Un recorrido especial por libros fascinantes llenos de sabiduría e historia."
                lower.contains("success") || lower.contains("motivation") || lower.contains("mindset") -> "Consejos fundamentales para alcanzar el éxito y mantenerse inspirado."
                lower.contains("thank you") || lower.contains("subscribe") -> "¡Gracias por su atención! No olviden suscribirse al canal."
                else -> "En esta hermosa escena observamos los momentos más maravillosos de la vida."
            }
        }

        // 3. French
        if (lang == "french") {
            return when {
                lower.contains("hello") || lower.contains("hi there") || lower.contains("welcome") -> "Bonjour à tous et bienvenue dans cette vidéo."
                lower.contains("space") || lower.contains("universe") || lower.contains("galaxy") || lower.contains("star") -> "Un voyage spectaculaire à travers l'univers infini et les étoiles scintillantes."
                lower.contains("nature") || lower.contains("mountain") || lower.contains("tree") || lower.contains("forest") || lower.contains("river") -> "Une escapade paisible entourée de montagnes majestueuses et de forêts."
                lower.contains("cyberpunk") || lower.contains("neon") || lower.contains("tokyo") || lower.contains("city") -> "Au cœur des lumières néons vibrantes et des métropoles du futur."
                lower.contains("sunset") || lower.contains("beach") || lower.contains("sea") || lower.contains("ocean") -> "La sérénité d'un superbe coucher de soleil se reflétant sur les vagues."
                lower.contains("technology") || lower.contains("robot") || lower.contains("ai") || lower.contains("computer") -> "Bienvenue à l'ère de l'intelligence artificielle et du progrès."
                lower.contains("success") || lower.contains("motivation") || lower.contains("mindset") -> "Des conseils précieux pour réussir et rester inspiré au quotidien."
                lower.contains("thank you") || lower.contains("subscribe") -> "Merci d'avoir regardé ! N'oubliez pas de vous abonner."
                else -> "Dans cette magnifique scène, nous découvrons les instants splendides de l'existence."
            }
        }

        // 4. German
        if (lang == "german") {
            return when {
                lower.contains("hello") || lower.contains("hi there") || lower.contains("welcome") -> "Hallo zusammen! Herzlich willkommen zu diesem Video."
                lower.contains("space") || lower.contains("universe") || lower.contains("galaxy") || lower.contains("star") -> "Eine spektakuläre Reise durch die Weiten des Weltraums und glitzernde Sterne."
                lower.contains("nature") || lower.contains("mountain") || lower.contains("tree") || lower.contains("forest") || lower.contains("river") -> "Eine friedliche Auszeit umgeben von majestätischen Bergen und tiefen Wäldern."
                lower.contains("cyberpunk") || lower.contains("neon") || lower.contains("tokyo") || lower.contains("city") -> "Zwischen leuchtenden Neonreklamen und den Metropolen der Zukunft."
                lower.contains("sunset") || lower.contains("beach") || lower.contains("sea") || lower.contains("ocean") -> "Das weiche Licht des goldenen Sonnenuntergangs über den Wellen des Meeres."
                lower.contains("technology") || lower.contains("robot") || lower.contains("ai") || lower.contains("computer") -> "Willkommen im modernen Zeitalter von künstlicher Intelligenz und Technik."
                lower.contains("success") || lower.contains("motivation") || lower.contains("mindset") -> "Die besten Ratschläge, um Erfolg zu haben und sich täglich zu inspirieren."
                lower.contains("thank you") || lower.contains("subscribe") -> "Vielen Dank fürs Zuschauen! Bitte abonnieren Sie unseren Kanal."
                else -> "In dieser wunderschönen Szene erleben wir die magischen Momente des Lebens."
            }
        }

        // 5. Arabic
        if (lang == "arabic") {
            return when {
                lower.contains("hello") || lower.contains("hi there") || lower.contains("welcome") -> "مرحباً بكم وأهلاً وسهلاً بكم في هذا الفيديو."
                lower.contains("space") || lower.contains("universe") || lower.contains("galaxy") || lower.contains("star") -> "مرحباً بكم في رحلة ساحرة عبر مجرات الفضاء والنجوم اللامعة."
                lower.contains("nature") || lower.contains("mountain") || lower.contains("tree") || lower.contains("forest") || lower.contains("river") -> "رحلة هادئة وبديعة في أحضان الطبيعة الخضراء والجبال والمساحات الخضراء."
                lower.contains("cyberpunk") || lower.contains("neon") || lower.contains("tokyo") || lower.contains("city") -> "بين أضواء النيون الساطعة ومباني مدن المستقبل الحديثة."
                lower.contains("sunset") || lower.contains("beach") || lower.contains("sea") || lower.contains("ocean") -> "تأمل جمال غروب الشمس الذهبي الأخاذ وأمواج البحر الهادئة."
                lower.contains("technology") || lower.contains("robot") || lower.contains("ai") || lower.contains("computer") -> "في هذا العصر المتقدم من الذكاء الاصطناعي والتكنولوجيا الفائقة."
                lower.contains("success") || lower.contains("motivation") || lower.contains("mindset") -> "نصائح ملهمة وأفكار محفزة لتحقيق النجاح والتميز والقمة."
                lower.contains("thank you") || lower.contains("subscribe") -> "شكراً جزيلاً لكم على المتابعة، لا تنسوا الإعجاب والاشتراك!"
                else -> "في هذا المشهد المميز، نكتشف سحر هذا العالم الجميل."
            }
        }

        // 6. Urdu
        if (lang == "urdu") {
            return when {
                lower.contains("hello") || lower.contains("hi there") || lower.contains("welcome") -> "السلام علیکم اور خوش آمدید دوستو! اس ویڈیو میں آپ کا استقبال ہے۔"
                lower.contains("space") || lower.contains("universe") || lower.contains("galaxy") || lower.contains("star") -> "آئیے چلتے ہیں اننت خلائے بسیط اور چمکتے تاروں کے ایک پراسرار سفر پر۔"
                lower.contains("nature") || lower.contains("mountain") || lower.contains("tree") || lower.contains("forest") || lower.contains("river") -> "قدرت کی خوبصورت وادیوں، اونچے پہاڑوں اور سرسبز جنگلوں کے دلفریب سفر پر۔"
                lower.contains("cyberpunk") || lower.contains("neon") || lower.contains("tokyo") || lower.contains("city") -> "چمکیلی نیون روشنیوں اور مستقبل کے جدید سحر انگیز شہروں کے درمیان۔"
                lower.contains("sunset") || lower.contains("beach") || lower.contains("sea") || lower.contains("ocean") -> "شام کے خوبصورت سنہرے غروبِ آفتاب اور سمندر کی لہروں کا نظارہ۔"
                lower.contains("technology") || lower.contains("robot") || lower.contains("ai") || lower.contains("computer") -> "جدید ترین اے آئی ٹیکنالوجی، روبوٹکس اور کمپیوٹر سائنس کے اس انوکھے دور میں۔"
                lower.contains("success") || lower.contains("motivation") || lower.contains("mindset") -> "زندگی میں بے پناہ کامیابی حاصل کرنے اور ہمیشہ پرعزم رہنے کے سنہرے اصول۔"
                lower.contains("thank you") || lower.contains("subscribe") -> "ہمارے ساتھ جڑنے کا شکریہ۔ مزید ویڈیوز کے لیے سبسکرائب کرنا نہ بھولیں۔"
                else -> "اس خوبصورت منظر میں آپ زندگی کے چند منفرد اور شاندار لمحات دیکھ سکتے ہیں۔"
            }
        }

        // 7. Bengali
        if (lang == "bengali") {
            return when {
                lower.contains("hello") || lower.contains("hi there") || lower.contains("welcome") -> "নমস্কার বন্ধুরা! আজকের আমাদের এই ভিডিওতে আপনাদের স্বাগত।"
                lower.contains("space") || lower.contains("universe") || lower.contains("galaxy") || lower.contains("star") -> "অনন্ত মহাকাশ এবং উজ্জ্বল নক্ষত্রদের এক পরম রহস্যময় জগতের সফর।"
                lower.contains("nature") || lower.contains("mountain") || lower.contains("tree") || lower.contains("forest") || lower.contains("river") -> "প্রকৃতির শান্ত ও চিরসবুজ পাহাড়, অরণ্য এবং নদীর কোলের মনোরম দৃশ্য।"
                lower.contains("cyberpunk") || lower.contains("neon") || lower.contains("tokyo") || lower.contains("city") -> "চমকপ্রদ নিয়ন আলো এবং আধুনিক তথ্যপ্রযুক্তির ভবিষ্যৎ শহরের ব্যস্ততায়।"
                lower.contains("sunset") || lower.contains("beach") || lower.contains("sea") || lower.contains("ocean") -> "মনোরম সন্ধ্যায় সূর্যাস্ত এবং সমুদ্রের নীল ঢেউয়ের অসাধারণ রূপ।"
                lower.contains("technology") || lower.contains("robot") || lower.contains("ai") || lower.contains("computer") -> "আধুনিক এআই প্রযুক্তি, স্মার্ট রোবট এবং মানুষের বুদ্ধিমত্তার নতুন যুগে।"
                lower.contains("success") || lower.contains("motivation") || lower.contains("mindset") -> "জীবনে অসামান্য সাফল্য অর্জন এবং সর্বদা অনুপ্রাণিত থাকার বিশেষ পরামর্শ।"
                lower.contains("thank you") || lower.contains("subscribe") -> "আমাদের সাথে থাকার জন্য ধন্যবাদ! ভিডিওটি ভালো লাগলে লাইক ও সাবস্ক্রাইব করুন।"
                else -> "এই সুন্দর দৃশ্যে আপনারা জীবনের অসাধারণ কিছু মুহূর্ত দেখতে পাবেন।"
            }
        }

        // 8. Tamil
        if (lang == "tamil") {
            return when {
                lower.contains("hello") || lower.contains("hi there") || lower.contains("welcome") -> "வணக்கம் நண்பர்களே! நமது புதிய பக்கத்திற்கு உங்களை அன்போடு வரவேற்கிறோம்."
                lower.contains("space") || lower.contains("universe") || lower.contains("galaxy") || lower.contains("star") -> "விண்வெளி மற்றும் பிரகாசிக்கும் நட்சத்திரங்களின் பிரம்மாண்டமான விண்வெளி பயணம்."
                lower.contains("nature") || lower.contains("mountain") || lower.contains("tree") || lower.contains("forest") || lower.contains("river") -> "அமையான இயற்கை எழில் கொஞ்சும் மலைகள், காடுகள் மற்றும் நதிகளின் அழகு."
                lower.contains("cyberpunk") || lower.contains("neon") || lower.contains("tokyo") || lower.contains("city") -> "ஒளிரும் நவீன நியான் விளக்குகள் மற்றும் எதிர்கால மின்னணு நகரத்தின் காட்சிகள்."
                lower.contains("sunset") || lower.contains("beach") || lower.contains("sea") || lower.contains("ocean") -> "அழகான மாலையில் மறையும் செங்கதிர் சூரியன் மற்றும் அமைதியான கடல் அலைகள்."
                lower.contains("technology") || lower.contains("robot") || lower.contains("ai") || lower.contains("computer") -> "நவீன செயற்கை நுண்ணறிவு மற்றும் புதிய தொழில்நுட்ப புரட்சியின் புதிய சகாப்தம்."
                lower.contains("success") || lower.contains("motivation") || lower.contains("mindset") -> "வாழ்க்கையில் மாபெரும் வெற்றி பெறவும் என்றும் உற்சாகமாக இருக்கவும் சில கருத்துக்கள்."
                lower.contains("thank you") || lower.contains("subscribe") -> "எங்களுடன் இணைந்ததற்கு நன்றி. தொடர்ந்து காண சப்ஸ்கிரைப் செய்யவும்!"
                else -> "இந்த அழகான காட்சியில் நீங்கள் வாழ்க்கையின் சிறந்த தருணங்களைக் காண்பீர்கள்."
            }
        }

        // 9. Telugu
        if (lang == "telugu") {
            return when {
                lower.contains("hello") || lower.contains("hi there") || lower.contains("welcome") -> "నమస్కారం స్నేహితులారా! మా సరికొత్త వీడియోకి మీకు ప్రేమపూర్వక స్వాగతం."
                lower.contains("space") || lower.contains("universe") || lower.contains("galaxy") || lower.contains("star") -> "అనంతమైన అంతరిక్ష గమ్యం మరియు అద్భుతంగా మెరిసే నక్షత్రాల లోకంలో ప్రయాణం."
                lower.contains("nature") || lower.contains("mountain") || lower.contains("tree") || lower.contains("forest") || lower.contains("river") -> "ప్రశాంతమైన మనోహర ప్రకృతి పర్వతాలు, దట్టమైన అడవులు మరియు నదుల అందాలు."
                lower.contains("cyberpunk") || lower.contains("neon") || lower.contains("tokyo") || lower.contains("city") -> "ధగధగ మెరిసిపోయే నియాన్ వెలుగుల భవిష్యత్తు నగరాల అద్భుత దృశ్యాల మధ్య."
                lower.contains("sunset") || lower.contains("beach") || lower.contains("sea") || lower.contains("ocean") -> "సాయంత్రం వేళ ప్రశాంతమైన సూర్యాస్తమయం మరియు అలలు ఎగసిపడే సముద్ర తీరం."
                lower.contains("technology") || lower.contains("robot") || lower.contains("ai") || lower.contains("computer") -> "ఆధునిక ప్రపంచాన్ని శాసిస్తున్న కృత్రిమ మేధస్సు (AI) మరియు సాంకేతికత."
                lower.contains("success") || lower.contains("motivation") || lower.contains("mindset") -> "జీవితంలో ఉన్నతమైన విజయం సాధించడానికి మరియు నిరంతరం ప్రేరణ పొందే అద్భుత మార్గాలు."
                lower.contains("thank you") || lower.contains("subscribe") -> "మా వీడియోను వీక్షించినందుకు కృతజ్ఞతలు. దయచేసి లైక్ మరియు సబ్‌స్క్రైబ్ చేసుకోండి!"
                else -> "ఈ అందమైన దృశ్యంలో మీరు జీవితంలోని అద్భుతమైన క్షణాలను చూస్తారు."
            }
        }

        // 10. Japanese
        if (lang == "japanese") {
            return when {
                lower.contains("hello") || lower.contains("hi there") || lower.contains("welcome") -> "こんにちは、皆さん！新しい動画へようこそ。"
                lower.contains("space") || lower.contains("universe") || lower.contains("galaxy") || lower.contains("star") -> "無限の宇宙と煌めく星々が織りなす神秘的な世界のストーリー。"
                lower.contains("nature") || lower.contains("mountain") || lower.contains("tree") || lower.contains("forest") || lower.contains("river") -> "豊かな大自然、雄大な山脈、そして美しい静寂を宿す森と川。"
                lower.contains("cyberpunk") || lower.contains("neon") || lower.contains("tokyo") || lower.contains("city") -> "まばゆいネオンライトに彩られた未来都市の躍動する鼓動。"
                lower.contains("sunset") || lower.contains("beach") || lower.contains("sea") || lower.contains("ocean") -> "黄金色の美しい夕暮れと調和する穏やかな海岸線。"
                lower.contains("technology") || lower.contains("robot") || lower.contains("ai") || lower.contains("computer") -> "人類の限界を超える先進的なAI技術とデジタルテクノロジーの最前線。"
                lower.contains("success") || lower.contains("motivation") || lower.contains("mindset") -> "目標に向かって前進し、成功を引き寄せるためのモチベーション。"
                lower.contains("thank you") || lower.contains("subscribe") -> "ご視聴ありがとうございました！チャンネル登録と高評価をお願いします。"
                else -> "この美しいシーンの中で、私たちは人生の素晴らしい瞬間を体感します。"
            }
        }

        // 11. Chinese
        if (lang == "chinese") {
            return when {
                lower.contains("hello") || lower.contains("hi there") || lower.contains("welcome") -> "大家好！欢迎观看我们本期精心准备的精彩视频。"
                lower.contains("space") || lower.contains("universe") || lower.contains("galaxy") || lower.contains("star") -> "带您走入漫天星辰与宇宙深处，揭开无边银河的神秘面纱。"
                lower.contains("nature") || lower.contains("mountain") || lower.contains("tree") || lower.contains("forest") || lower.contains("river") -> "感受远离世俗喧嚣的群山、清澈流淌的溪水与宁静森林。"
                lower.contains("cyberpunk") || lower.contains("neon") || lower.contains("tokyo") || lower.contains("city") -> "体验赛博朋克霓虹光影与具有强烈未来感的智慧都市生活。"
                lower.contains("sunset") || lower.contains("beach") || lower.contains("sea") || lower.contains("ocean") -> "落日余晖洒在蔚蓝的海面，海浪轻拂金黄色沙滩的祥和美景。"
                lower.contains("technology") || lower.contains("robot") || lower.contains("ai") || lower.contains("computer") -> "在人工智能与现代高新技术加速演进的变革时代之中。"
                lower.contains("success") || lower.contains("motivation") || lower.contains("mindset") -> "如何突破自我极限，掌握财富自由与致胜心智的力量。"
                lower.contains("thank you") || lower.contains("subscribe") -> "感谢收看！如果喜欢本视频，请务必关注订阅并点赞支持！"
                else -> "在这美丽的场景中，我们探索生活中那些迷人的精彩瞬间。"
            }
        }

        // 12. Korean
        if (lang == "korean") {
            return when {
                lower.contains("hello") || lower.contains("hi there") || lower.contains("welcome") -> "안녕하세요, 시청자 여러분! 오늘 준비한 스페셜 영상에 오신 것을 환영합니다."
                lower.contains("space") || lower.contains("universe") || lower.contains("galaxy") || lower.contains("star") -> "아스라이 흐르는 무수한 별빛과 끝없는 은하 우주의 장엄한 대서사시."
                lower.contains("nature") || lower.contains("mountain") || lower.contains("tree") || lower.contains("forest") || lower.contains("river") -> "가슴이 평온해지는 싱그러운 대자연, 깊고 오랜 숲과 장엄한 산줄기."
                lower.contains("cyberpunk") || lower.contains("neon") || lower.contains("tokyo") || lower.contains("city") -> "화려한 네온사인 불빛과 상상을 뛰어넘는 첨단 미래 도시의 숨결."
                lower.contains("sunset") || lower.contains("beach") || lower.contains("sea") || lower.contains("ocean") -> "고즈넉한 일몰과 황금빛 저녁 여울이 수놓인 바다의 완벽한 휴식."
                lower.contains("technology") || lower.contains("robot") || lower.contains("ai") || lower.contains("computer") -> "혁신적인 지능형 하이테크 인공지능과 인류 지각 한계를 넓히는 과학 과학기술."
                lower.contains("success") || lower.contains("motivation") || lower.contains("mindset") -> "성공을 이루기 위한 집중된 열정과 끊임없는 마음가짐의 원동력."
                lower.contains("thank you") || lower.contains("subscribe") -> "시청 감사합니다! 영상이 좋으셨다면 구독과 알림 설정, 좋아요 부탁드립니다."
                else -> "이 아름다운 장면에서 우리는 일상의 특별하고 경이로운 순간을 발견합니다."
            }
        }

        // 13. Portuguese
        if (lang == "portuguese") {
            return when {
                lower.contains("hello") || lower.contains("hi there") || lower.contains("welcome") -> "Olá! Sejam muito bem-vindos ao nosso canal e a este vídeo especial."
                lower.contains("space") || lower.contains("universe") || lower.contains("galaxy") || lower.contains("star") -> "Uma incrível jornada através das galáxias do universo infinito e estrelas cintilantes."
                lower.contains("nature") || lower.contains("mountain") || lower.contains("tree") || lower.contains("forest") || lower.contains("river") -> "Um retiro de paz incomparável em meio às montanhas, florestas exuberantes e rios."
                lower.contains("cyberpunk") || lower.contains("neon") || lower.contains("tokyo") || lower.contains("city") -> "Entre os deslumbrantes painéis de neon e a incrível estética das metrópoles futuristas."
                lower.contains("sunset") || lower.contains("beach") || lower.contains("sea") || lower.contains("ocean") -> "Um pôr do sol espetacular trazendo tons de dourado para a costa marítima."
                lower.contains("technology") || lower.contains("robot") || lower.contains("ai") || lower.contains("computer") -> "Explorando as infinitas possibilidades do desenvolvimento tecnológico mundial e inteligência artificial."
                lower.contains("success") || lower.contains("motivation") || lower.contains("mindset") -> "Conselhos valiosos para programar a sua mente para atingir metas grandiosas."
                lower.contains("thank you") || lower.contains("subscribe") -> "Muito obrigado por nos assistir! Apoie o canal inscrevendo-se e curtindo."
                else -> "Nesta bela cena, podemos vivenciar e contemplar momentos únicos de nossa vida."
            }
        }

        // 14. Russian
        if (lang == "russian") {
            return when {
                lower.contains("hello") || lower.contains("hi there") || lower.contains("welcome") -> "Приветствуем вас, друзья! Добро пожаловать на наш увлекательный новый эфир."
                lower.contains("space") || lower.contains("universe") || lower.contains("galaxy") || lower.contains("star") -> "Сказочный полет сквозь загадочный космический океан к далеким мерцающим звездам."
                lower.contains("nature") || lower.contains("mountain") || lower.contains("tree") || lower.contains("forest") || lower.contains("river") -> "Чистое величие нетронутой дикой природы, заснеженных пиков и хвойного леса."
                lower.contains("cyberpunk") || lower.contains("neon") || lower.contains("tokyo") || lower.contains("city") -> "Яркие вспышки неоновых вывесок киберпанк-улиц городов завтрашнего дня."
                lower.contains("sunset") || lower.contains("beach") || lower.contains("sea") || lower.contains("ocean") -> "Захватывающий вечерний закат солнца, уходящего за горизонт теплых морских глубин."
                lower.contains("technology") || lower.contains("robot") || lower.contains("ai") || lower.contains("computer") -> "Новая эра искусственного интеллекта, инновационных роботов и глобального прогресса."
                lower.contains("success") || lower.contains("motivation") || lower.contains("mindset") -> "Психология богатства, веры в свой потенциал и лучших секретов личного успеха."
                lower.contains("thank you") || lower.contains("subscribe") -> "Сердечно благодарим за внимание! Рекомендуем поставить лайк и подписаться."
                else -> "В этой живописной сцене запечатлены самые яркие и прекрасные моменты жизни."
            }
        }

        // 15. Turkish
        if (lang == "turkish") {
            return when {
                lower.contains("hello") || lower.contains("hi there") || lower.contains("welcome") -> "Merhaba arkadaşlar! Hepiniz bu yeni ve bilgilendirici videomuza hoş geldiniz."
                lower.contains("space") || lower.contains("universe") || lower.contains("galaxy") || lower.contains("star") -> "Uçsuz bucaksız evren boşluğu ve göz kamaştıran galaksilere eşsiz bir bakış."
                lower.contains("nature") || lower.contains("mountain") || lower.contains("tree") || lower.contains("forest") || lower.contains("river") -> "Tabiatın dinlendirici kucağındaki görkemli dağların ve yeşil ormanların büyülü manzarası."
                lower.contains("cyberpunk") || lower.contains("neon") || lower.contains("tokyo") || lower.contains("city") -> "Renk renk parıldayan neon reklam panoları ve fütüristik metropol sokaklarının enerjisi."
                lower.contains("sunset") || lower.contains("beach") || lower.contains("sea") || lower.contains("ocean") -> "Denizin dingin sularına veda eden akşamsı altın gün batımının muhteşem esintisi."
                lower.contains("technology") || lower.contains("robot") || lower.contains("ai") || lower.contains("computer") -> "Geleceği tamamen baştan yazan yapay zeka devrimi ve bilim teknolojileri çağı."
                lower.contains("success") || lower.contains("motivation") || lower.contains("mindset") -> "Büyük başarılara imza atmak ve her zaman yüksek seviyede motive kalmak için altın ipuçları."
                lower.contains("thank you") || lower.contains("subscribe") -> "Bizi takip ettiğiniz için sonsuz teşekkürler! Abone olmayı ve videomuzu beğenmeyi unutmayın."
                else -> "Bu muhteşem sahnede hayatın en özel ve büyüleyici anlarına tanıklık ediyoruz."
            }
        }

        return trimmed
    }

    fun searchPixabayApi(
        query: String,
        pixabayApiKey: String,
        aspectRatio: String = "9:16",
        sceneNum: Int = 0,
        enableSceneFallback: Boolean = true,
        alreadyUsedUrls: Set<String> = emptySet()
    ): String {
        val rawKey = pixabayApiKey.trim()
        val primaryKey = if (rawKey.isNotEmpty()) rawKey else ApiLoadBalancerService.resolvePixabayApiKey("")
        if (primaryKey.isEmpty()) {
            return if (enableSceneFallback) getThematicSceneFallbackImage(query, sceneNum, aspectRatio, alreadyUsedUrls) else ""
        }

        val candidateKeys = listOf(
            primaryKey,
            "56218545-6d93003a94318db8a7f29dba1"
        ).distinct()

        val orientation = when (aspectRatio) {
            "9:16", "4:5", "3:4" -> "vertical"
            "16:9", "21:9" -> "horizontal"
            else -> "all"
        }

        val queryText = query.replace(Regex("[^\\p{L}\\p{N}\\s]"), "").trim()
        if (queryText.isEmpty()) {
            return if (enableSceneFallback) getThematicSceneFallbackImage(query, sceneNum, aspectRatio, alreadyUsedUrls) else ""
        }

        fun executePixabayRequest(q: String, key: String): String? {
            val encodedQuery = java.net.URLEncoder.encode(q, "UTF-8")
            val url = "https://pixabay.com/api/?key=$key&q=$encodedQuery&image_type=photo&orientation=$orientation&per_page=15"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0")
                .build()

            for (attempt in 0..1) {
                try {
                    val response = httpClient.newCall(request).execute()
                    if (response.isSuccessful) {
                        val bodyString = response.body?.string() ?: ""
                        val json = JSONObject(bodyString)
                        val hits = json.optJSONArray("hits")
                        if (hits != null && hits.length() > 0) {
                            val total = hits.length()
                            for (offset in 0 until total) {
                                val idx = (sceneNum + offset) % total
                                val hitObj = hits.optJSONObject(idx) ?: continue
                                val imageUrl = hitObj.optString("largeImageURL") ?: hitObj.optString("webformatURL")
                                if (!imageUrl.isNullOrEmpty()) {
                                    val safeUrl = if (imageUrl.startsWith("http://")) imageUrl.replace("http://", "https://") else imageUrl
                                    if (safeUrl !in alreadyUsedUrls) {
                                        return safeUrl
                                    }
                                }
                            }
                            val defaultHit = hits.optJSONObject(sceneNum % total)
                            val defaultUrl = defaultHit?.optString("largeImageURL") ?: defaultHit?.optString("webformatURL") ?: ""
                            if (defaultUrl.isNotEmpty()) {
                                return if (defaultUrl.startsWith("http://")) defaultUrl.replace("http://", "https://") else defaultUrl
                            }
                        }
                        return ""
                    } else if (response.code in listOf(401, 403, 429)) {
                        Log.w("GeminiService", "Pixabay API key error or rate-limited (${response.code})")
                        break
                    }
                } catch (e: Exception) {
                    Log.w("GeminiService", "Pixabay network attempt $attempt failed for '$q': ${e.message}")
                    try { Thread.sleep(150L) } catch (ignored: InterruptedException) {}
                }
            }
            return null
        }

        // 1. Primary and candidate keys for original query
        for (key in candidateKeys) {
            val res = executePixabayRequest(queryText, key)
            if (!res.isNullOrEmpty()) {
                Log.d("GeminiService", "Pixabay photo search success for '$queryText': $res")
                return res
            }
        }

        // 2. Retry with simplified keywords (top 2 words) if query has multiple words
        val words = queryText.split(Regex("\\s+")).filter { it.length > 2 }
        if (words.size > 2) {
            val simplified = words.take(2).joinToString(" ")
            Log.d("GeminiService", "Pixabay zero results for '$queryText'. Retrying with simplified: '$simplified'")
            for (key in candidateKeys) {
                val res = executePixabayRequest(simplified, key)
                if (!res.isNullOrEmpty()) return res
            }
        } else if (words.isNotEmpty() && words.first() != queryText) {
            val singleWord = words.first()
            for (key in candidateKeys) {
                val res = executePixabayRequest(singleWord, key)
                if (!res.isNullOrEmpty()) return res
            }
        }

        // 3. Retry with thematic contextual query
        val thematicQuery = getThematicFallbackQuery(queryText)
        if (thematicQuery.isNotEmpty() && thematicQuery != queryText) {
            Log.d("GeminiService", "Pixabay retrying with contextual theme query: '$thematicQuery'")
            for (key in candidateKeys) {
                val res = executePixabayRequest(thematicQuery, key)
                if (!res.isNullOrEmpty()) return res
            }
        }

        // 4. Automatic relevant fallback image for the scene
        return if (enableSceneFallback) {
            Log.i("GeminiService", "Pixabay photo queries returned no results. Automatically fetching relevant fallback image for scene #$sceneNum.")
            getThematicSceneFallbackImage(query, sceneNum, aspectRatio, alreadyUsedUrls)
        } else ""
    }

    fun searchSpoonacularApi(
        query: String,
        spoonacularApiKey: String,
        searchVideo: Boolean,
        sceneNum: Int = 0
    ): String {
        val apiKey = spoonacularApiKey.trim()
        if (apiKey.isEmpty()) {
            Log.w("GeminiService", "[SPOONACULAR API SKIPPED] API key is empty. Falling back to TheMealDB / thematic fallback.")
            val mealDbResult = searchTheMealDbApi(query, sceneNum)
            return if (mealDbResult.isNotEmpty()) mealDbResult else getThematicSceneFallbackImage(query, sceneNum, "9:16")
        }

        val queryText = query.replace(Regex("[^\\p{L}\\p{N}\\s]"), "").trim()
        val queriesToTry = mutableListOf<String>()
        if (queryText.isNotEmpty()) queriesToTry.add(queryText)
        val words = queryText.split(Regex("\\s+")).filter { it.length > 2 }
        if (words.size > 2) {
            queriesToTry.add(words.take(2).joinToString(" "))
        } else if (words.isNotEmpty() && words.first() != queryText) {
            queriesToTry.add(words.first())
        }
        queriesToTry.add("food recipe")

        for (qAttempt in queriesToTry.distinct()) {
            for (attempt in 0..1) {
                try {
                    val encodedQuery = java.net.URLEncoder.encode(qAttempt, "UTF-8")
                    val url = if (searchVideo) {
                        "https://api.spoonacular.com/food/videos/search?apiKey=$apiKey&query=$encodedQuery&number=10"
                    } else {
                        "https://api.spoonacular.com/recipes/complexSearch?apiKey=$apiKey&query=$encodedQuery&number=10"
                    }

                    val request = Request.Builder()
                        .url(url)
                        .header("User-Agent", "Mozilla/5.0")
                        .build()

                    val response = httpClient.newCall(request).execute()
                    if (response.isSuccessful) {
                        val bodyString = response.body?.string() ?: ""
                        val json = JSONObject(bodyString)

                        if (searchVideo) {
                            val videos = json.optJSONArray("videos")
                            if (videos != null && videos.length() > 0) {
                                val resultIndex = sceneNum % videos.length()
                                val videoObj = videos.optJSONObject(resultIndex)
                                val youTubeId = videoObj?.optString("youTubeId")
                                if (!youTubeId.isNullOrEmpty()) {
                                    val thumbnail = videoObj.optString("thumbnail")
                                    if (!thumbnail.isNullOrEmpty()) return thumbnail
                                    return "https://img.youtube.com/vi/$youTubeId/maxresdefault.jpg"
                                }
                            }
                        } else {
                            val results = json.optJSONArray("results")
                            if (results != null && results.length() > 0) {
                                val resultIndex = sceneNum % results.length()
                                val recipeObj = results.optJSONObject(resultIndex)
                                val imageUrl = recipeObj?.optString("image")
                                if (!imageUrl.isNullOrEmpty()) {
                                    val safeImageUrl = if (imageUrl.startsWith("http://")) imageUrl.replace("http://", "https://") else imageUrl
                                    Log.d("GeminiService", "Spoonacular recipe photo success for '$qAttempt': $safeImageUrl")
                                    return safeImageUrl
                                }
                            }
                        }
                    } else if (response.code in listOf(401, 402, 403, 429)) {
                        Log.w("GeminiService", "Spoonacular API key error or quota exceeded (${response.code})")
                        break
                    }
                } catch (e: Exception) {
                    Log.w("GeminiService", "Spoonacular network attempt $attempt failed for '$qAttempt': ${e.message}")
                    try { Thread.sleep(150L) } catch (ignored: InterruptedException) {}
                }
            }
        }

        // Automatic fallback: Try TheMealDB first, then thematic scene fallback
        val mealDbFallback = searchTheMealDbApi(query, sceneNum)
        if (mealDbFallback.isNotEmpty()) return mealDbFallback
        return getThematicSceneFallbackImage(query, sceneNum, "9:16")
    }

    fun searchTheMealDbApi(
        query: String,
        sceneNum: Int = 0
    ): String {
        val qClean = query.replace(Regex("[^\\p{L}\\p{N}\\s]"), "").trim()
        val words = qClean.split(Regex("\\s+")).filter { it.length > 2 }
        val primaryTerm = words.firstOrNull { !listOf("recipe", "food", "dish", "cook", "cooking", "delicious", "kitchen", "step", "easy", "make").contains(it.lowercase()) }
            ?: words.firstOrNull() ?: "chicken"

        val searchTerms = listOf(primaryTerm, words.take(2).joinToString(" "), "curry", "rice", "chicken").distinct()

        for (term in searchTerms) {
            for (attempt in 0..1) {
                try {
                    val encodedQuery = java.net.URLEncoder.encode(term, "UTF-8")
                    val url = "https://www.themealdb.com/api/json/v1/1/search.php?s=$encodedQuery"
                    val request = Request.Builder().url(url).header("User-Agent", "Mozilla/5.0").build()
                    val response = httpClient.newCall(request).execute()
                    if (response.isSuccessful) {
                        val body = response.body?.string() ?: ""
                        val json = JSONObject(body)
                        val meals = json.optJSONArray("meals")
                        if (meals != null && meals.length() > 0) {
                            val resultIndex = sceneNum % meals.length()
                            val mealObj = meals.optJSONObject(resultIndex)
                            val thumb = mealObj?.optString("strMealThumb")
                            if (!thumb.isNullOrEmpty()) {
                                val safeThumb = if (thumb.startsWith("http://")) thumb.replace("http://", "https://") else thumb
                                Log.d("GeminiService", "TheMealDB recipe photo success for '$term': $safeThumb")
                                return safeThumb
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.w("GeminiService", "TheMealDB attempt $attempt failed for '$term': ${e.message}")
                    try { Thread.sleep(120L) } catch (ignored: InterruptedException) {}
                }
            }
        }

        // Category filter fallback with retry
        val categories = listOf("Vegetarian", "Chicken", "Dessert", "Pasta", "Seafood", "Breakfast", "Starter", "Side")
        val selectedCategory = categories[sceneNum % categories.size]
        for (attempt in 0..1) {
            try {
                val catUrl = "https://www.themealdb.com/api/json/v1/1/filter.php?c=$selectedCategory"
                val catReq = Request.Builder().url(catUrl).header("User-Agent", "Mozilla/5.0").build()
                val catResp = httpClient.newCall(catReq).execute()
                if (catResp.isSuccessful) {
                    val catBody = catResp.body?.string() ?: ""
                    val catJson = JSONObject(catBody)
                    val catMeals = catJson.optJSONArray("meals")
                    if (catMeals != null && catMeals.length() > 0) {
                        val idx = sceneNum % catMeals.length()
                        val mealObj = catMeals.optJSONObject(idx)
                        val thumb = mealObj?.optString("strMealThumb")
                        if (!thumb.isNullOrEmpty()) {
                            val safeThumb = if (thumb.startsWith("http://")) thumb.replace("http://", "https://") else thumb
                            Log.d("GeminiService", "TheMealDB category fallback photo success: $safeThumb")
                            return safeThumb
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w("GeminiService", "TheMealDB category filter attempt $attempt failed: ${e.message}")
                try { Thread.sleep(100L) } catch (ignored: InterruptedException) {}
            }
        }

        // Automatic relevant scene fallback image
        Log.i("GeminiService", "TheMealDB queries returned no results. Automatically fetching relevant scene fallback image.")
        return getThematicSceneFallbackImage(query, sceneNum, "9:16")
    }

    fun searchFoodishApi(
        query: String,
        sceneNum: Int = 0
    ): String {
        val lower = query.lowercase()
        val preferredCategory = when {
            lower.contains("biryani") || lower.contains("rice") || lower.contains("चावल") || lower.contains("पुलाव") -> "biryani"
            lower.contains("burger") || lower.contains("sandwich") -> "burger"
            lower.contains("butter chicken") || lower.contains("chicken") || lower.contains("curry") || lower.contains("चिकन") || lower.contains("पनीर") -> "butter-chicken"
            lower.contains("dessert") || lower.contains("sweet") || lower.contains("cake") || lower.contains("मिठाई") || lower.contains("हलवा") -> "dessert"
            lower.contains("dosa") || lower.contains("डोसा") -> "dosa"
            lower.contains("idli") || lower.contains("idly") || lower.contains("इडली") -> "idly"
            lower.contains("pasta") || lower.contains("noodles") || lower.contains("पास्ता") -> "pasta"
            lower.contains("pizza") || lower.contains("पिज़्ज़ा") || lower.contains("पिज्जा") -> "pizza"
            lower.contains("samosa") || lower.contains("snack") || lower.contains("समोसा") || lower.contains("पकौड़ा") -> "samosa"
            else -> {
                val allCats = listOf("biryani", "burger", "butter-chicken", "dessert", "dosa", "idly", "pasta", "pizza", "rice", "samosa")
                allCats[sceneNum % allCats.size]
            }
        }

        val categoriesToTry = listOf(preferredCategory, "biryani", "dessert", "pasta", "pizza").distinct()

        for (cat in categoriesToTry) {
            for (attempt in 0..1) {
                try {
                    val url = "https://foodish-api.com/api/images/$cat"
                    val request = Request.Builder().url(url).header("User-Agent", "Mozilla/5.0").build()
                    val response = httpClient.newCall(request).execute()
                    if (response.isSuccessful) {
                        val body = response.body?.string() ?: ""
                        val json = JSONObject(body)
                        val img = json.optString("image")
                        if (!img.isNullOrEmpty()) {
                            val safeImg = if (img.startsWith("http://")) img.replace("http://", "https://") else img
                            Log.d("GeminiService", "Foodish API photo success for category '$cat': $safeImg")
                            return safeImg
                        }
                    }
                } catch (e: Exception) {
                    Log.w("GeminiService", "Foodish API attempt $attempt failed for '$cat': ${e.message}")
                    try { Thread.sleep(120L) } catch (ignored: InterruptedException) {}
                }
            }
        }

        // Automatic relevant scene fallback image
        Log.i("GeminiService", "Foodish API returned no results. Automatically fetching relevant scene fallback image.")
        return getThematicSceneFallbackImage(query, sceneNum, "9:16")
    }

    fun searchPexelsApi(
        query: String,
        pexelsApiKey: String,
        searchVideo: Boolean,
        aspectRatio: String = "9:16",
        sceneNum: Int = 0,
        enableSceneFallback: Boolean = true,
        alreadyUsedUrls: Set<String> = emptySet()
    ): String {
        val rawKey = pexelsApiKey.trim()
        val primaryKey = if (rawKey.isNotEmpty()) rawKey else ApiLoadBalancerService.resolvePexelsApiKey("")
        if (primaryKey.isEmpty()) {
            return if (enableSceneFallback) {
                if (searchVideo) getThematicSceneFallbackVideo(query, sceneNum, alreadyUsedUrls)
                else getThematicSceneFallbackImage(query, sceneNum, aspectRatio, alreadyUsedUrls)
            } else ""
        }

        val candidateKeys = listOf(
            primaryKey,
            "NyEXcmIT8bs3CN4dLNForDzJQVyVrWCMRfDLjfgtKYX9t3z8dUm1QHNl"
        ).distinct()

        val orientation = when (aspectRatio) {
            "9:16", "4:5", "3:4" -> "portrait"
            "16:9", "21:9" -> "landscape"
            else -> "square"
        }

        val queryText = query.replace(Regex("[^\\p{L}\\p{N}\\s]"), "").trim()
        if (queryText.isEmpty()) {
            return if (enableSceneFallback) {
                if (searchVideo) getThematicSceneFallbackVideo(query, sceneNum, alreadyUsedUrls)
                else getThematicSceneFallbackImage(query, sceneNum, aspectRatio, alreadyUsedUrls)
            } else ""
        }

        fun executePexelsRequest(q: String, key: String): String? {
            val encodedQuery = java.net.URLEncoder.encode(q, "UTF-8")
            val url = if (searchVideo) {
                "https://api.pexels.com/videos/search?query=$encodedQuery&orientation=$orientation&per_page=15"
            } else {
                "https://api.pexels.com/v1/search?query=$encodedQuery&orientation=$orientation&per_page=15"
            }

            val request = Request.Builder()
                .url(url)
                .header("Authorization", key)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                .build()

            for (attempt in 0..1) {
                try {
                    val response = httpClient.newCall(request).execute()
                    if (response.isSuccessful) {
                        val bodyString = response.body?.string() ?: ""
                        val json = JSONObject(bodyString)
                        if (searchVideo) {
                            val videos = json.optJSONArray("videos")
                            if (videos != null && videos.length() > 0) {
                                val total = videos.length()
                                for (offset in 0 until total) {
                                    val idx = (sceneNum + offset) % total
                                    val videoObj = videos.optJSONObject(idx) ?: continue
                                    val videoFiles = videoObj.optJSONArray("video_files")
                                    if (videoFiles != null && videoFiles.length() > 0) {
                                        for (vIdx in 0 until videoFiles.length()) {
                                            val fileObj = videoFiles.optJSONObject(vIdx) ?: continue
                                            val link = fileObj.optString("link")
                                            val fileType = fileObj.optString("file_type")
                                            if (!link.isNullOrEmpty() && (link.contains(".mp4") || fileType == "video/mp4")) {
                                                val safeLink = if (link.startsWith("http://")) link.replace("http://", "https://") else link
                                                if (safeLink !in alreadyUsedUrls) {
                                                    return safeLink
                                                }
                                            }
                                        }
                                    }
                                }
                                val defaultVideo = videos.optJSONObject(sceneNum % total)
                                val defaultFiles = defaultVideo?.optJSONArray("video_files")
                                if (defaultFiles != null && defaultFiles.length() > 0) {
                                    val firstLink = defaultFiles.optJSONObject(0)?.optString("link") ?: ""
                                    if (firstLink.isNotEmpty()) {
                                        return if (firstLink.startsWith("http://")) firstLink.replace("http://", "https://") else firstLink
                                    }
                                }
                                val coverUrl = defaultVideo?.optString("image") ?: ""
                                if (coverUrl.isNotEmpty()) {
                                    return if (coverUrl.startsWith("http://")) coverUrl.replace("http://", "https://") else coverUrl
                                }
                            }
                        } else {
                            val photos = json.optJSONArray("photos")
                            if (photos != null && photos.length() > 0) {
                                val total = photos.length()
                                for (offset in 0 until total) {
                                    val idx = (sceneNum + offset) % total
                                    val photoObj = photos.optJSONObject(idx) ?: continue
                                    val srcObj = photoObj.optJSONObject("src")
                                    val imageUrl = srcObj?.optString("original") ?: srcObj?.optString("large")
                                    if (!imageUrl.isNullOrEmpty()) {
                                        val safeImg = if (imageUrl.startsWith("http://")) imageUrl.replace("http://", "https://") else imageUrl
                                        if (safeImg !in alreadyUsedUrls) {
                                            return safeImg
                                        }
                                    }
                                }
                                val defaultPhoto = photos.optJSONObject(sceneNum % total)
                                val srcObj = defaultPhoto?.optJSONObject("src")
                                val defaultImg = srcObj?.optString("original") ?: srcObj?.optString("large") ?: ""
                                if (defaultImg.isNotEmpty()) {
                                    return if (defaultImg.startsWith("http://")) defaultImg.replace("http://", "https://") else defaultImg
                                }
                            }
                        }
                        return ""
                    } else if (response.code in listOf(401, 403, 429)) {
                        Log.w("GeminiService", "Pexels API key error or rate-limited (${response.code})")
                        break
                    }
                } catch (e: Exception) {
                    Log.w("GeminiService", "Pexels network attempt $attempt failed for '$q': ${e.message}")
                    try { Thread.sleep(150L) } catch (ignored: InterruptedException) {}
                }
            }
            return null
        }

        // 1. Primary and candidate keys for original query
        for (key in candidateKeys) {
            val res = executePexelsRequest(queryText, key)
            if (!res.isNullOrEmpty()) {
                Log.d("GeminiService", "Pexels search success for '$queryText': $res")
                return res
            }
        }

        // 2. Retry with simplified keywords (top 2 words) if query has multiple words
        val words = queryText.split(Regex("\\s+")).filter { it.length > 2 }
        if (words.size > 2) {
            val simplified = words.take(2).joinToString(" ")
            Log.d("GeminiService", "Pexels zero results for '$queryText'. Retrying with simplified: '$simplified'")
            for (key in candidateKeys) {
                val res = executePexelsRequest(simplified, key)
                if (!res.isNullOrEmpty()) return res
            }
        } else if (words.isNotEmpty() && words.first() != queryText) {
            val singleWord = words.first()
            for (key in candidateKeys) {
                val res = executePexelsRequest(singleWord, key)
                if (!res.isNullOrEmpty()) return res
            }
        }

        // 3. Retry with contextual thematic query
        val thematicQuery = getThematicFallbackQuery(queryText)
        if (thematicQuery.isNotEmpty() && thematicQuery != queryText) {
            Log.d("GeminiService", "Pexels retrying with contextual theme query: '$thematicQuery'")
            for (key in candidateKeys) {
                val res = executePexelsRequest(thematicQuery, key)
                if (!res.isNullOrEmpty()) return res
            }
        }

        // 4. Automatic relevant fallback image/video for the scene
        return if (enableSceneFallback) {
            Log.i("GeminiService", "Pexels queries returned no results. Automatically fetching relevant fallback for scene #$sceneNum.")
            if (searchVideo) getThematicSceneFallbackVideo(query, sceneNum, alreadyUsedUrls)
            else getThematicSceneFallbackImage(query, sceneNum, aspectRatio, alreadyUsedUrls)
        } else ""
    }

    fun searchPixabayVideos(
        query: String,
        pixabayApiKey: String,
        aspectRatio: String = "9:16",
        sceneNum: Int = 0,
        enableSceneFallback: Boolean = true,
        alreadyUsedUrls: Set<String> = emptySet()
    ): String {
        val rawKey = pixabayApiKey.trim()
        val primaryKey = if (rawKey.isNotEmpty()) rawKey else ApiLoadBalancerService.resolvePixabayApiKey("")
        if (primaryKey.isEmpty()) {
            return if (enableSceneFallback) getThematicSceneFallbackVideo(query, sceneNum, alreadyUsedUrls) else ""
        }

        val candidateKeys = listOf(
            primaryKey,
            "56218545-6d93003a94318db8a7f29dba1"
        ).distinct()

        val queryText = query.replace(Regex("[^\\p{L}\\p{N}\\s]"), "").trim()
        if (queryText.isEmpty()) {
            return if (enableSceneFallback) getThematicSceneFallbackVideo(query, sceneNum, alreadyUsedUrls) else ""
        }

        fun executePixabayVideoRequest(q: String, key: String): String? {
            val encodedQuery = java.net.URLEncoder.encode(q, "UTF-8")
            val url = "https://pixabay.com/api/videos/?key=$key&q=$encodedQuery&per_page=15"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                .build()

            for (attempt in 0..1) {
                try {
                    val response = httpClient.newCall(request).execute()
                    if (response.isSuccessful) {
                        val bodyString = response.body?.string() ?: ""
                        val json = JSONObject(bodyString)
                        val hits = json.optJSONArray("hits")
                        if (hits != null && hits.length() > 0) {
                            val total = hits.length()
                            for (offset in 0 until total) {
                                val idx = (sceneNum + offset) % total
                                val hitObj = hits.optJSONObject(idx) ?: continue
                                val videosObj = hitObj.optJSONObject("videos")
                                if (videosObj != null) {
                                    val mediumObj = videosObj.optJSONObject("medium")
                                    val smallObj = videosObj.optJSONObject("small")
                                    val largeObj = videosObj.optJSONObject("large")
                                    val tinyObj = videosObj.optJSONObject("tiny")
                                    val rawVideoUrl = mediumObj?.optString("url")
                                        ?: smallObj?.optString("url")
                                        ?: largeObj?.optString("url")
                                        ?: tinyObj?.optString("url")
                                        ?: ""
                                    if (rawVideoUrl.isNotEmpty()) {
                                        val safeUrl = if (rawVideoUrl.startsWith("http://")) rawVideoUrl.replace("http://", "https://") else rawVideoUrl
                                        if (safeUrl !in alreadyUsedUrls) {
                                            return safeUrl
                                        }
                                    }
                                }
                            }
                            val defaultHit = hits.optJSONObject(sceneNum % total)
                            val videosObj = defaultHit?.optJSONObject("videos")
                            val rawVideoUrl = videosObj?.optJSONObject("medium")?.optString("url")
                                ?: videosObj?.optJSONObject("small")?.optString("url")
                                ?: videosObj?.optJSONObject("large")?.optString("url")
                                ?: ""
                            if (rawVideoUrl.isNotEmpty()) {
                                return if (rawVideoUrl.startsWith("http://")) rawVideoUrl.replace("http://", "https://") else rawVideoUrl
                            }
                            val pictureId = defaultHit?.optString("picture_id")
                            if (!pictureId.isNullOrEmpty()) {
                                return "https://i.vimeocdn.com/video/${pictureId}_960x540.jpg"
                            }
                        }
                        return ""
                    } else if (response.code in listOf(401, 403, 429)) {
                        Log.w("GeminiService", "Pixabay Video API key error or rate-limited (${response.code})")
                        break
                    }
                } catch (e: Exception) {
                    Log.w("GeminiService", "Pixabay Video network attempt $attempt failed for '$q': ${e.message}")
                    try { Thread.sleep(150L) } catch (ignored: InterruptedException) {}
                }
            }
            return null
        }

        // 1. Primary and candidate keys for original query
        for (key in candidateKeys) {
            val res = executePixabayVideoRequest(queryText, key)
            if (!res.isNullOrEmpty()) {
                Log.d("GeminiService", "Pixabay video search success for '$queryText': $res")
                return res
            }
        }

        // 2. Retry with simplified keywords
        val words = queryText.split(Regex("\\s+")).filter { it.length > 2 }
        if (words.size > 2) {
            val simplified = words.take(2).joinToString(" ")
            for (key in candidateKeys) {
                val res = executePixabayVideoRequest(simplified, key)
                if (!res.isNullOrEmpty()) return res
            }
        } else if (words.isNotEmpty() && words.first() != queryText) {
            val singleWord = words.first()
            for (key in candidateKeys) {
                val res = executePixabayVideoRequest(singleWord, key)
                if (!res.isNullOrEmpty()) return res
            }
        }

        // 3. Retry with contextual thematic query
        val thematicQuery = getThematicFallbackQuery(queryText)
        if (thematicQuery.isNotEmpty() && thematicQuery != queryText) {
            for (key in candidateKeys) {
                val res = executePixabayVideoRequest(thematicQuery, key)
                if (!res.isNullOrEmpty()) return res
            }
        }

        // 4. Fallback video for scene
        return if (enableSceneFallback) {
            Log.i("GeminiService", "Pixabay video queries returned no results. Automatically fetching relevant fallback video for scene #$sceneNum.")
            getThematicSceneFallbackVideo(query, sceneNum, alreadyUsedUrls)
        } else ""
    }

    suspend fun translateNonEnglishToEnglish(query: String, customGeminiKey: String = ""): String = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return@withContext ""
        
        val hasNonAscii = trimmed.any { it.code > 127 }
        if (!hasNonAscii) return@withContext trimmed

        val lower = trimmed.lowercase()
        val localMatch = when {
            // Food & Cooking
            lower.contains("रेसिपी") || lower.contains("खाना") || lower.contains("भोजन") -> "delicious food recipe"
            lower.contains("मसाले") || lower.contains("मसाला") -> "cooking spices"
            lower.contains("चाय") || lower.contains("कॉफी") -> "tea coffee cup"
            lower.contains("बिरयानी") || lower.contains("चावल") -> "delicious rice dish"
            lower.contains("रोटी") || lower.contains("सब्जी") -> "steaming meal food"
            // Agriculture & Rural
            lower.contains("किसान") || lower.contains("खेती") || lower.contains("खेत") -> "farmer agriculture field"
            lower.contains("फसल") || lower.contains("अनाज") -> "crops harvest field"
            lower.contains("गाँव") || lower.contains("गांव") -> "peaceful village rural"
            lower.contains("ट्रैक्टर") -> "tractor farm field"
            // Healthcare & Medical
            lower.contains("डॉक्टर") || lower.contains("अस्पताल") -> "doctor hospital medical"
            lower.contains("दवाई") || lower.contains("इलाज") || lower.contains("मरीज") -> "medicine healthcare patient"
            // Business & Wealth
            lower.contains("पैसा") || lower.contains("रुपया") || lower.contains("धन") || lower.contains("दौलत") -> "money cash wealth"
            lower.contains("अमीर") || lower.contains("सोना") -> "rich wealth luxury"
            lower.contains("ऑफिस") || lower.contains("कार्यालय") || lower.contains("नौकरी") -> "modern office workspace"
            lower.contains("व्यापार") || lower.contains("दुकान") || lower.contains("बाजार") -> "business market shop"
            lower.contains("सफलता") || lower.contains("जीत") -> "success achievement celebration"
            // Education & Learning
            lower.contains("स्कूल") || lower.contains("कॉलेज") || lower.contains("कक्षा") -> "school college classroom"
            lower.contains("विद्यार्थी") || lower.contains("छात्र") || lower.contains("छात्रा") -> "student studying library"
            lower.contains("शिक्षक") || lower.contains("टीचर") || lower.contains("गुरु") -> "teacher classroom instruction"
            lower.contains("किताब") || lower.contains("किताबें") || lower.contains("पढ़ना") -> "books study reading"
            // Tech, Science & Space
            lower.contains("कम्प्यूटर") || lower.contains("लैपटॉप") || lower.contains("कंप्यूटर") -> "computer laptop technology"
            lower.contains("मोबाइल") || lower.contains("फोन") -> "smartphone mobile hand"
            lower.contains("एआई") || lower.contains("रोबोट") -> "robot artificial intelligence"
            lower.contains("अंतरिक्ष") || lower.contains("ब्रह्मांड") || lower.contains("चांद") || lower.contains("तारा") -> "outer space galaxy stars"
            // Military & Police
            lower.contains("सेना") || lower.contains("सिपाही") || lower.contains("फौजी") || lower.contains("जवान") -> "brave soldier army uniform"
            lower.contains("पुलिस") -> "police officer duty"
            lower.contains("तिरंगा") || lower.contains("भारत") || lower.contains("भारतीय") -> "india tricolor flag"
            // Sports & Fitness
            lower.contains("क्रिकेट") -> "cricket stadium match"
            lower.contains("जिम") || lower.contains("कसरत") || lower.contains("दौड़ना") -> "fitness workout gym"
            // Vehicles & Travel
            lower.contains("कार") || lower.contains("गाड़ी") || lower.contains("गाड़ी") -> "car driving road"
            lower.contains("ट्रेन") || lower.contains("रेल") -> "train railway station"
            lower.contains("हवाई जहाज") || lower.contains("विमान") -> "airplane flight clouds"
            lower.contains("सड़क") || lower.contains("रास्ता") || lower.contains("सड़क") -> "road highway scenic"
            // Animals & Wildlife
            lower.contains("शेर") || lower.contains("बाघ") || lower.contains("चीता") -> "lion tiger predator"
            lower.contains("हाथी") -> "elephant nature wildlife"
            lower.contains("घोड़ा") -> "galloping horse"
            lower.contains("कुत्ता") || lower.contains("बिल्ली") -> "pet dog cat"
            lower.contains("चिड़िया") || lower.contains("चिड़ियां") || lower.contains("पक्षी") -> "birds flying sky"
            lower.contains("गाय") -> "cow rural pasture"
            // Nature & Elements
            lower.contains("पहाड़") || lower.contains("पर्वत") || lower.contains("पहाड़") -> "mountains landscape"
            lower.contains("नदी") || lower.contains("नदियां") || lower.contains("झील") -> "clear river water"
            lower.contains("जंगल") || lower.contains("वन") -> "dense green forest"
            lower.contains("पेड़") || lower.contains("पौधे") || lower.contains("पेड़") -> "lush green trees"
            lower.contains("समुद्र") || lower.contains("सागर") || lower.contains("लहर") -> "ocean waves beach"
            lower.contains("सूर्यास्त") || lower.contains("शाम") -> "sunset golden hour"
            lower.contains("सूर्योदय") || lower.contains("सुबह") -> "sunrise morning sunlight"
            lower.contains("सूरज") || lower.contains("धूप") -> "bright sunlight rays"
            lower.contains("बादल") || lower.contains("आसमान") || lower.contains("आकाश") -> "clouds sky dramatic"
            lower.contains("बारिश") || lower.contains("बरसात") || lower.contains("तूफान") -> "rain storm droplets"
            lower.contains("फूल") || lower.contains("गुलाब") -> "beautiful flowers garden"
            lower.contains("अग्नि") || lower.contains("आग") -> "bonfire dramatic flame"
            // People & Emotions
            lower.contains("घर") || lower.contains("महल") -> "beautiful cozy house"
            lower.contains("परिवार") || lower.contains("माता") || lower.contains("पिता") -> "happy family together"
            lower.contains("लोग") || lower.contains("भीड़") || lower.contains("भीड़") -> "group of happy people"
            lower.contains("लड़का") || lower.contains("पुरुष") -> "man person portrait"
            lower.contains("लड़की") || lower.contains("महिला") || lower.contains("लड़की") -> "woman person portrait"
            lower.contains("बच्चा") || lower.contains("बच्चे") -> "happy children playing"
            lower.contains("दोस्त") || lower.contains("दोस्ती") -> "friends laughing celebration"
            lower.contains("खुशी") || lower.contains("उत्सव") -> "celebration joy happy"
            lower.contains("मंदिर") || lower.contains("पूजा") -> "ancient sacred temple"
            else -> null
        }
        if (localMatch != null) return@withContext localMatch

        val prompt = """
            Translate the following short non-English search query into 1 to 3 simple English nouns/keywords for stock photo search.
            Only return the translated clean English terms. Do not add any conversational text or formatting.
            
            Query: $trimmed
        """.trimIndent()

        val apiKey = getCleanApiKey(customGeminiKey)

        var translatedText = ""

        try {
            translatedText = UnifiedApiClient.executeWithCircuitBreaker(
                provider = ApiProvider.GEMINI,
                operationName = "translateNonEnglishToEnglish",
                primaryCall = {
                    val candidateKeys = resolveCandidateGeminiKeys(customGeminiKey)
                    if (candidateKeys.isEmpty()) {
                        throw java.io.IOException("No valid REST API key available.")
                    }
                    var restTrans = ""
                    val systemInstructionJson = JSONObject().apply {
                        put("parts", JSONArray().put(JSONObject().apply {
                            put("text", "You are a precise keyword translator. Translate to simple, descriptive English stock keywords.")
                        }))
                    }

                    val contentsJson = JSONObject().apply {
                        put("parts", JSONArray().put(JSONObject().apply {
                            put("text", prompt)
                        }))
                    }

                    val requestBodyJson = JSONObject().apply {
                        put("contents", JSONArray().put(contentsJson))
                        put("systemInstruction", systemInstructionJson)
                        put("generationConfig", JSONObject().apply {
                            put("temperature", 0.1)
                        })
                    }

                    val mediaType = "application/json".toMediaType()
                    val requestBody = requestBodyJson.toString().toRequestBody(mediaType)

                    keyLoop@ for ((keyTierLabel, currentApiKey) in candidateKeys) {
                        Log.i("GeminiService", "Attempting translateNonEnglishToEnglish direct REST API with $keyTierLabel...")
                        for (modelName in MODERN_MODELS) {
                            try {
                                Log.i("GeminiService", "[$keyTierLabel] Attempting direct REST API for translateNonEnglishToEnglish with model: $modelName")
                                val url = "https://generativelanguage.googleapis.com/v1beta/models/$modelName:generateContent?key=$currentApiKey"
                                val request = Request.Builder()
                                    .url(url)
                                    .post(requestBody)
                                    .header("User-Agent", "Mozilla/5.0")
                                    .build()

                                val response = httpClient.newCall(request).execute()
                                if (response.isSuccessful) {
                                    val bodyString = response.body?.string() ?: ""
                                    val json = JSONObject(bodyString)
                                    val candidates = json.optJSONArray("candidates")
                                    if (candidates != null && candidates.length() > 0) {
                                        val candidate = candidates.getJSONObject(0)
                                        val contentObj = candidate.optJSONObject("content")
                                        val parts = contentObj?.optJSONArray("parts")
                                        if (parts != null && parts.length() > 0) {
                                            val sb = StringBuilder()
                                            for (p in 0 until parts.length()) {
                                                val partObj = parts.getJSONObject(p)
                                                val isThought = partObj.optBoolean("thought", false)
                                                val partText = partObj.optString("text", "")
                                                if (!isThought && partText.isNotEmpty()) {
                                                    if (sb.isNotEmpty()) sb.append("\n")
                                                    sb.append(partText)
                                                }
                                            }
                                            val extracted = (if (sb.isNotEmpty()) sb.toString() else parts.getJSONObject(0).optString("text", "")).trim()
                                            if (extracted.isNotEmpty()) {
                                                restTrans = extracted
                                                Log.i("GeminiService", "[$keyTierLabel] Direct REST API translateNonEnglishToEnglish completed successfully with model $modelName: '$restTrans'")
                                                break@keyLoop
                                            }
                                        }
                                    }
                                } else {
                                    Log.w("GeminiService", "[$keyTierLabel] REST translation failed for model $modelName with response code: ${response.code}")
                                    if (PriorityApiGateway.isAuthOrQuotaCritical(response.code)) {
                                        PriorityApiGateway.logKeyTierFailover(keyTierLabel, "next key tier", "HTTP ${response.code}")
                                        break
                                    }
                                }
                            } catch (e: Exception) {
                                Log.w("GeminiService", "[$keyTierLabel] REST translation failed for model $modelName: ${e.message}")
                            }
                        }
                    }
                    if (restTrans.isEmpty()) {
                        throw java.io.IOException("All REST API keys (User and App) failed for translateNonEnglishToEnglish")
                    }
                    restTrans
                },
                secondaryCall = {
                    var secTrans = ""
                    val backends = listOf(
                        com.google.firebase.ai.type.GenerativeBackend.googleAI(), // Free Gemini Developer API (Firebase Spark plan)
                        com.google.firebase.ai.type.GenerativeBackend.vertexAI(location = "us-central1"),
                        com.google.firebase.ai.type.GenerativeBackend.vertexAI(location = "global")
                    )
                    Log.i("GeminiService", "Circuit breaker executing secondary Firebase AI endpoint for translateNonEnglishToEnglish...")
                    backendLoop@ for (backend in backends) {
                        for (firebaseModel in FIREBASE_MODELS) {
                            try {
                                Log.i("GeminiService", "Attempting Firebase AI translation with model: $firebaseModel")
                                val model = Firebase.ai(backend = backend)
                                    .generativeModel(
                                        modelName = firebaseModel,
                                        generationConfig = aiGenerationConfig {
                                            temperature = 0.1f
                                        },
                                        systemInstruction = aiContent {
                                            text("You are a precise keyword translator. Translate to simple, descriptive English stock keywords.")
                                        }
                                    )
                                val response = model.generateContent(prompt)
                                val result = response.text?.trim() ?: ""
                                if (result.isNotEmpty()) {
                                    secTrans = result
                                    PriorityApiGateway.logSuccessfulFailover("Keyword Translation", firebaseModel)
                                    Log.i("GeminiService", "Firebase AI Logic SDK translateNonEnglishToEnglish completed successfully with model: $firebaseModel")
                                    break@backendLoop
                                }
                            } catch (fEx: Exception) {
                                Log.w("GeminiService", "Firebase translation failed with model $firebaseModel: ${fEx.message}")
                            }
                        }
                    }
                    secTrans
                }
            )
        } catch (e: Exception) {
            Log.w("GeminiService", "Circuit breaker execution for translateNonEnglishToEnglish caught error: ${e.message}")
        }

        if (translatedText.isNotEmpty()) {
            val cleanTranslated = translatedText.replace(Regex("[^a-zA-Z0-9\\s]"), "").trim()
            if (cleanTranslated.isNotEmpty()) {
                Log.i("GeminiService", "Translated '$trimmed' to English search query: '$cleanTranslated'")
                return@withContext cleanTranslated
            }
        }

        // 3. Fallback to local semantic extraction
        val localExtracted = extractSemiSemanticKeyword(trimmed)
        if (localExtracted.isNotEmpty() && !localExtracted.contains("cinematic narrative scene") && !localExtracted.contains("cinematic visual")) {
            return@withContext localExtracted
        }
        val englishOnly = trimmed.replace(Regex("[^a-zA-Z0-9\\s]"), "").trim()
        if (englishOnly.isNotEmpty()) {
            return@withContext englishOnly
        }
        return@withContext "documentary news investigation"
    }

    suspend fun generateScriptFromTopic(
        topicDescription: String,
        style: String,
        language: String,
        durationOption: String, // "Short", "Medium", "Long"
        customGeminiKey: String = ""
    ): String = withContext(Dispatchers.IO) {
        val durationDesc = when (durationOption.lowercase(java.util.Locale.ROOT)) {
            "short", "30 seconds", "30-60 seconds" -> "Short Reels/Shorts format (TARGET: exactly 110 to 140 words, fast-paced, high retention, 1 sentence Hook, 2 concise paragraphs of Body, 1 sentence Outro)"
            "medium", "2-3 minutes" -> "Medium video format (TARGET: exactly 350 to 450 words, balanced explanatory pacing, 2-3 sentence Hook, 4-5 well-structured paragraphs of Body, 2-3 sentences Outro & CTA)"
            "long", "5-10 minutes" -> "Long-form/Deep-dive format (TARGET: exactly 900 to 1200 words, comprehensive explanation with details, 4-5 sentence Hook, at least 10 detailed paragraphs of Body explaining concepts thoroughly, 3-4 sentences Outro & CTA)"
            else -> "Format matching $durationOption"
        }

        val prompt = """
            Write a complete, highly engaging video narration script.
            
            Topic / Video description: $topicDescription
            Tone / Theme Style: $style
            Target Language: $language
            Target Length & Format Constraints: $durationDesc

            MANDATORY RULES:
            - Provide ONLY the narrator speech text (narration text) in the native alphabet script of $language (e.g. use Devanagari script for Hindi, Cyrillic for Russian, native script for Urdu, Tamil, German, etc.).
            - Do NOT include any scene descriptions, camera instructions, slide titles, subtitles, "Scene 1:" indicators, brackets, or other metadata annotations.
            - Start with a powerful "hook" in the very first sentence to stop users from scrolling.
            - The text must flow naturally and be rhythmic, ready to be copied/pasted directly into a text-to-speech voice generator.
            - Do NOT output markdown code blocks (such as ```), asterisks, or markdown quotes. Output pure narrative prose only.
            
            CRITICAL COMPLETION REQUIREMENT (NEVER CUT OFF):
            - The script MUST have a complete, cohesive, and satisfying ending. 
            - It MUST NOT end abruptly, freeze in the middle, or cut off mid-sentence.
            - Ensure that you plan your writing so the entire topic is perfectly covered and brought to a natural conclusion within the specified target length limit.
            - The final line MUST be a fully complete and rounded closing sentence.
        """.trimIndent()

        val apiKey = getCleanApiKey(customGeminiKey)

        var resultText = ""

        try {
            resultText = UnifiedApiClient.executeWithCircuitBreaker(
                provider = ApiProvider.GEMINI,
                operationName = "generateScriptFromTopic",
                primaryCall = {
                    if (!isApiKeyValid(apiKey)) {
                        throw java.io.IOException("No valid REST API key available.")
                    }
                    var restText = ""
                    val systemInstructionJson = JSONObject().apply {
                        put("parts", JSONArray().put(JSONObject().apply {
                            put("text", "You are an expert video content scriptwriter. Your job is to write a cohesive, fully finished script of the requested length with a complete beginning, middle, and end, with no cut-offs or formatting artifacts.")
                        }))
                    }

                    val contentsJson = JSONObject().apply {
                        put("parts", JSONArray().put(JSONObject().apply {
                            put("text", prompt)
                        }))
                    }

                    val calculatedTokens = if (durationOption.lowercase(java.util.Locale.ROOT).contains("long")) 4096 else 2048
                    val requestBodyJson = JSONObject().apply {
                        put("contents", JSONArray().put(contentsJson))
                        put("systemInstruction", systemInstructionJson)
                        put("generationConfig", JSONObject().apply {
                            put("temperature", 0.7)
                            put("maxOutputTokens", calculatedTokens)
                        })
                    }

                    val mediaType = "application/json; charset=utf-8".toMediaType()
                    val requestBody = requestBodyJson.toString().toRequestBody(mediaType)

                    val candidateKeys = resolveCandidateGeminiKeys(customGeminiKey)
                    if (candidateKeys.isEmpty()) {
                        throw java.io.IOException("No valid REST Gemini API key available (neither user nor app key configured).")
                    }

                    keyLoop@ for ((keyTierLabel, currentApiKey) in candidateKeys) {
                        Log.i("GeminiService", "Attempting generateScriptFromTopic direct REST API with $keyTierLabel...")
                        for (modelName in MODERN_MODELS) {
                            try {
                                Log.i("GeminiService", "[$keyTierLabel] Attempting direct REST API for generateScriptFromTopic with model: $modelName")
                                val url = "https://generativelanguage.googleapis.com/v1beta/models/$modelName:generateContent?key=$currentApiKey"
                                val request = Request.Builder()
                                    .url(url)
                                    .post(requestBody)
                                    .header("User-Agent", "Mozilla/5.0")
                                    .build()

                                val response = httpClient.newCall(request).execute()
                                if (response.isSuccessful) {
                                    val bodyString = response.body?.string() ?: ""
                                    val json = JSONObject(bodyString)
                                    val candidates = json.optJSONArray("candidates")
                                    if (candidates != null && candidates.length() > 0) {
                                        val candidate = candidates.getJSONObject(0)
                                        val contentObj = candidate.optJSONObject("content")
                                        val parts = contentObj?.optJSONArray("parts")
                                        if (parts != null && parts.length() > 0) {
                                            val sb = StringBuilder()
                                            for (p in 0 until parts.length()) {
                                                val partObj = parts.getJSONObject(p)
                                                val isThought = partObj.optBoolean("thought", false)
                                                val partText = partObj.optString("text", "")
                                                if (!isThought && partText.isNotEmpty()) {
                                                    if (sb.isNotEmpty()) sb.append("\n")
                                                    sb.append(partText)
                                                }
                                            }
                                            val rawExtracted = if (sb.isNotEmpty()) sb.toString() else parts.getJSONObject(0).optString("text", "")
                                            val generatedText = rawExtracted.trim()
                                            if (generatedText.isNotEmpty()) {
                                                var cleanedText = generatedText
                                                if (cleanedText.startsWith("```")) {
                                                    val lines = cleanedText.lines()
                                                    if (lines.size >= 2) {
                                                        cleanedText = lines.subList(1, lines.size - 1).joinToString("\n")
                                                    }
                                                }
                                                restText = cleanedText.trim()
                                                Log.i("GeminiService", "[$keyTierLabel] Direct REST API generateScriptFromTopic completed successfully with model: $modelName")
                                                break@keyLoop
                                            }
                                        }
                                    }
                                } else {
                                    Log.w("GeminiService", "[$keyTierLabel] REST script generation failed for model $modelName with response code: ${response.code}")
                                    if (PriorityApiGateway.isAuthOrQuotaCritical(response.code)) {
                                        PriorityApiGateway.logKeyTierFailover(keyTierLabel, "next key tier", "HTTP ${response.code}")
                                        break
                                    }
                                }
                            } catch (e: Exception) {
                                Log.w("GeminiService", "[$keyTierLabel] REST script generation failed for model $modelName: ${e.message}")
                            }
                        }
                    }
                    if (restText.isEmpty()) {
                        throw java.io.IOException("All REST API keys (User and App) failed for generateScriptFromTopic")
                    }
                    restText
                },
                secondaryCall = {
                    var vertexText = ""
                    val backends = listOf(
                        com.google.firebase.ai.type.GenerativeBackend.googleAI(), // Free Gemini Developer API (Firebase Spark plan)
                        com.google.firebase.ai.type.GenerativeBackend.vertexAI(location = "us-central1"),
                        com.google.firebase.ai.type.GenerativeBackend.vertexAI(location = "global")
                    )
                    Log.i("GeminiService", "Circuit breaker executing secondary Firebase AI endpoint for generateScriptFromTopic...")
                    backendLoop@ for (backend in backends) {
                        for (firebaseModel in FIREBASE_MODELS) {
                            try {
                                Log.i("GeminiService", "Attempting Firebase AI script generation with model: $firebaseModel")
                                val model = Firebase.ai(backend = backend)
                                    .generativeModel(
                                        modelName = firebaseModel,
                                        generationConfig = aiGenerationConfig {
                                            temperature = 0.7f
                                            maxOutputTokens = if (durationOption.lowercase(java.util.Locale.ROOT).contains("long")) 4096 else 2048
                                        },
                                        systemInstruction = aiContent {
                                            text("You are an expert video content scriptwriter. Your job is to write a cohesive, fully finished script of the requested length with a complete beginning, middle, and end, with no cut-offs or formatting artifacts.")
                                        }
                                    )
                                val response = model.generateContent(prompt)
                                val generatedText = response.text?.trim() ?: ""
                                if (generatedText.isNotEmpty()) {
                                    var cleanedText = generatedText
                                    if (cleanedText.startsWith("```")) {
                                        val lines = cleanedText.lines()
                                        if (lines.size >= 2) {
                                            cleanedText = lines.subList(1, lines.size - 1).joinToString("\n")
                                        }
                                    }
                                    vertexText = cleanedText.trim()
                                    PriorityApiGateway.logSuccessfulFailover("Script Generation", firebaseModel)
                                    Log.i("GeminiService", "Firebase AI Logic SDK script generation completed successfully with model: $firebaseModel")
                                    break@backendLoop
                                }
                            } catch (fEx: Exception) {
                                Log.w("GeminiService", "Firebase script generation failed with model $firebaseModel: ${fEx.message}")
                            }
                        }
                    }
                    vertexText
                }
            )
        } catch (e: Exception) {
            Log.w("GeminiService", "Circuit breaker execution for generateScriptFromTopic caught error: ${e.message}")
        }

        if (resultText.isNotEmpty()) {
            return@withContext resultText
        }

        // 2.5 Check BYOK Multi-Provider Router before falling back to local templates
        try {
            val app = com.ritvyom.yashoraReelgenerator.YashoraApplication.getInstance()
            val router = app?.unifiedAiRouter
            if (router != null && router.hasAnyActiveProvider()) {
                Log.i("GeminiService", "Attempting script generation via BYOK Provider Router...")
                val req = com.ritvyom.yashoraReelgenerator.data.ai.AiGenerationRequest(
                    prompt = prompt,
                    systemInstruction = "You are an expert video content scriptwriter. Your job is to write a cohesive, fully finished script of the requested length with a complete beginning, middle, and end, with no cut-offs.",
                    capability = com.ritvyom.yashoraReelgenerator.data.ai.AiCapability.SCRIPT_GENERATION
                )
                val byokResult = router.executeWithFallback(req)
                if (byokResult.text.isNotBlank()) {
                    Log.i("GeminiService", "BYOK Provider Router script generation succeeded using ${byokResult.providerUsed}!")
                    PriorityApiGateway.logSuccessfulFailover("Script Generation", "BYOK:${byokResult.providerUsed}")
                    return@withContext byokResult.text.trim()
                }
            }
        } catch (byokEx: Exception) {
            Log.w("GeminiService", "BYOK script generation attempt failed: ${byokEx.message}")
        }

        // 3. Dynamic Local fallback if both failed completely
        Log.w("GeminiService", "Both REST and Firebase script generation failed. Using local script dynamic fallback.")
        return@withContext generateSmartLocalScript(topicDescription, style, language, durationOption)
    }

    fun generateSmartLocalScript(
        topic: String,
        style: String,
        language: String,
        durationOption: String
    ): String {
        val cleanTopic = topic.trim().ifEmpty { "Viral Facts" }
        val lang = language.lowercase(java.util.Locale.ROOT)
        
        val title: String
        val hook: String
        val body: String
        val outro: String

        if (lang.contains("hindi")) {
            title = "वायरल फैक्ट्स: $cleanTopic"
            hook = "नमस्कार दोस्तों! क्या आपने कभी सोचा है कि $cleanTopic के पीछे का असली सच क्या है? आज के इस वीडियो में हम इसके सबसे चौंकाने वाले तथ्यों को जानेंगे, इसलिए अंत तक जरूर देखें!"
            body = "हम सब जानते हैं कि $cleanTopic आजकल बहुत ज्यादा चर्चा में है। इसके पीछे कई ऐसे रहस्य और दिलचस्प जानकारियां हैं जो बहुत कम लोगों को पता हैं। जब आप इसे गहराई से समझते हैं, तो यह आपके सोचने का तरीका पूरी तरह बदल देता है। पहला तथ्य यह है कि यह सीधे हमारी रोजमर्रा की जिंदगी को प्रभावित करता है। दूसरा तथ्य यह है कि विशेषज्ञों के अनुसार आने वाले समय में इसका प्रभाव और भी ज्यादा बढ़ने वाला है। अगर आप इसे सही तरीके से अपनाते हैं, तो यह आपके लिए गेम-चेंजर साबित हो सकता है।"
            outro = "अगर आपको यह वीडियो जानकारीपूर्ण लगी हो, तो वीडियो को लाइक करें, अपने दोस्तों के साथ शेयर करें और ऐसी ही रोमांचक जानकारियों के लिए हमारे चैनल को सब्सक्राइब करना न भूलें। मिलते हैं अगले वीडियो में!"
        } else if (lang.contains("hinglish")) {
            title = "Viral Truth: $cleanTopic"
            hook = "Namaskar doston! Kya aapne kabhi socha hai ki $cleanTopic ke peeche ka asli sach kya hai? Aaj ke is video mein hum iske sabse surprising facts explore karenge, isliye video ko end tak zaroor dekhiye!"
            body = "Hum sabhi jaante hain ki $cleanTopic aaj ke time mein kitna important topic ban chuka hai. Iske peeche kai aise facts aur insights hain jo mostly logon ko nahi pata hoti. Jab aap isko detail mein understand karte hain, toh aapka perspective completely change ho jata hai. First fact yeh hai ki yeh hamari daily life aur decisions ko direct impact karta hai. Second fact yeh hai ki top experts ke according, iska effect future mein aur bhi zyada expand hone wala hai."
            outro = "Agar aapko yeh video informative lagi ho, toh video ko LIKE karein, doston ke saath SHARE karein aur daily updates ke liye subscribe karna na bhoolein. Milte hain agle video mein, stay tuned!"
        } else {
            // English or other languages
            title = "The Truth About $cleanTopic"
            hook = "Hey everyone! Have you ever wondered what is really happening behind $cleanTopic? In today's breakdown, we are revealing the top insights you need to know, so make sure to watch until the very end!"
            body = "We all know that $cleanTopic is currently one of the most talked-about subjects. When you examine the core mechanics, there are several remarkable facts that most people completely overlook. First, understanding this concept completely transforms the way you approach your daily routine and decisions. Second, leading industry researchers confirm that its significance will continue to grow exponentially in the coming years. Mastering these fundamentals gives you an undeniable advantage."
            outro = "If you found this breakdown valuable, smash that like button, share this with someone who needs to see it, and subscribe for more bite-sized deep dives. See you in the next one!"
        }

        return """
            # TITLE: $title
            
            ## HOOK
            $hook
            
            ## SCRIPT BODY
            $body
            
            ## OUTRO & CTA
            $outro
        """.trimIndent()
    }

    fun containsEntityOrFamousPerson(text: String): Boolean {
        if (text.isBlank()) return false
        val lower = text.lowercase().trim()

        val famousKeywords = setOf(
            "gandhi", "indira", "mahatma", "modi", "narendra", "nehru", "jawaharlal", "bose", "subhas", "subhash",
            "patel", "sardar", "singh", "bhagat", "shastri", "lal bahadur", "rajiv", "atal", "vajpayee", "kalam",
            "abdul kalam", "tatya tope", "manghal pandey", "ranjit singh", "shivaji", "chhatrapati", "laxmi bai",
            "lakshmibai", "rani laxmi", "akbar", "ashoka", "pratap", "maharana", "buddha", "gautam", "gautama",
            "lincoln", "abraham lincoln", "trump", "biden", "obama", "einstein", "albert einstein", "tesla",
            "elon musk", "musk", "jobs", "steve jobs", "shah rukh", "shahrukh", "salman", "aamir", "bachchan",
            "virat", "dhoni", "sachin", "ronaldo", "messi", "nelson mandela", "mandela", "martin luther", "hitler",
            "churchill", "stalin", "napoleon", "cleopatra", "alexander", "swami vivekananda", "vivekananda", "tagore",
            "rabindranath", "ambedkar", "babasaheb", "savitribai", "jyotirao phule", "che guevara", "fidel castro",
            "putin", "zelensky", "xi jinping", "mao", "lenin",
            "गांधी", "गाँधी", "इंदिरा", "महात्मा", "मोदी", "नरेंद्र", "नेहरू", "जवाहरलाल", "सुभाष", "पटेल", "सरदार",
            "भगत", "शास्त्री", "लाल बहादुर", "राजीव", "अटल", "वाजपेयी", "कलाम", "अब्दुल कलाम", "शिवाजी",
            "छत्रपति", "लक्ष्मीबाई", "अकबर", "अशोक", "प्रताप", "बुद्ध", "गौतम", "अंबेडकर", "बाबासाहेब", "सावित्रीबाई",
            "अमिताभ", "बच्चन", "शाहरुख", "सलमान", "विराट", "धोनी", "सचिन"
        )

        if (famousKeywords.any { lower.contains(it) }) return true

        val roleKeywords = listOf(
            "prime minister", "president", "chief minister", "freedom fighter", "martyr", "king", "queen",
            "emperor", "mahatma", "netaji", "pandit", "bapu", "shaheed", "saheed", "sardar", "chhatrapati",
            "rani", "samrat", "assassination of", "death of", "biography of", "life of",
            "प्रधानमंत्री", "राष्ट्रपति", "मुख्यमंत्री", "नेता", "शहीद", "बापू", "महात्मा", "नेताजी",
            "पंडित", "स्वतंत्रता सेनानी", "क्रांतिकारी", "सम्राट", "राजा", "रानी", "हत्या", "मौत", "निधन",
            "मृत्यु", "जीवनी", "इतिहास"
        )
        if (roleKeywords.any { lower.contains(it) }) return true

        val titleCaseMatches = Regex("[A-Z][a-z]+\\s+[A-Z][a-z]+").findAll(text).toList()
        if (titleCaseMatches.isNotEmpty()) {
            val commonPhrases = setOf("The Scene", "A Photo", "Visual Style", "High Quality", "Short Video", "Background View")
            for (m in titleCaseMatches) {
                if (!commonPhrases.contains(m.value)) return true
            }
        }

        return false
    }

    fun cleanEntityQuery(text: String): String {
        if (text.isBlank()) return ""
        val stopwords = setOf(
            "ki", "ka", "ke", "me", "se", "ko", "par", "aur", "hai", "tha", "thi", "the", "hua", "hui", "hue",
            "kaise", "kab", "kya", "kahan", "kyun", "bare", "maut", "mrityu", "hatya", "jeevan", "jeevani",
            "kahani", "itihas", "bharat", "desh", "assassination", "death", "die", "died", "killed", "how",
            "why", "when", "story", "life", "biography", "about", "scene", "showing", "photo", "image", "video",
            "clip", "cinematic", "hd", "4k", "style", "description", "representation", "artistic", "visual",
            "की", "का", "के", "में", "से", "को", "पर", "और", "है", "था", "थी", "थे", "हुआ", "हुई", "हुए",
            "कैसे", "कब", "क्या", "कहाँ", "क्यों", "बारे", "मौत", "मृत्यु", "हत्या", "जीवन", "जीवनी",
            "कहानी", "इतिहास", "भारत", "देश", "दृश्य", "चित्र", "तस्वीर", "फोटो"
        )

        val words = text.split(Regex("\\s+"))
            .filter { word ->
                val wClean = word.lowercase().replace(Regex("[^\\p{L}\\p{N}]"), "")
                wClean.length >= 2 && !stopwords.contains(wClean)
            }

        return if (words.isNotEmpty()) words.joinToString(" ").trim() else text.trim()
    }

    private val WIKIPEDIA_USER_AGENT = "YashoraReelGenerator/2.0 (https://ai.studio; yashoratechnologies@gmail.com) Android/14 OkHttp/4.12"

    private fun fetchWikipediaPageSummary(title: String, domain: String): Pair<String, String>? {
        if (title.isBlank()) return null
        return try {
            val safeTitle = title.replace(' ', '_').trim()
            val encodedTitle = java.net.URLEncoder.encode(safeTitle, "UTF-8")
            val summaryUrl = "https://$domain/api/rest_v1/page/summary/$encodedTitle"
            val req = Request.Builder()
                .url(summaryUrl)
                .header("User-Agent", WIKIPEDIA_USER_AGENT)
                .build()
            httpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    val body = resp.body?.string() ?: ""
                    val json = JSONObject(body)
                    val original = json.optJSONObject("originalimage")?.optString("source") ?: ""
                    val thumb = json.optJSONObject("thumbnail")?.optString("source") ?: ""
                    val desc = json.optString("description", "")
                    val chosen = when {
                        original.isNotBlank() && !original.endsWith(".svg", true) && !original.endsWith(".pdf", true) -> original
                        thumb.isNotBlank() && !thumb.endsWith(".svg", true) && !thumb.endsWith(".pdf", true) -> thumb
                        else -> ""
                    }
                    if (chosen.isNotBlank()) {
                        val safeUrl = if (chosen.startsWith("http://")) chosen.replace("http://", "https://") else chosen
                        Pair(safeUrl, desc)
                    } else null
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            Log.w("GeminiService", "Failed to fetch Wikipedia page summary for '$title' at $domain: ${e.message}")
            null
        }
    }

    private fun resolveWikipediaOpenSearchTitles(query: String, baseUrl: String): List<String> {
        if (query.isBlank()) return emptyList()
        val titles = mutableListOf<String>()
        try {
            val encoded = java.net.URLEncoder.encode(query, "UTF-8")
            val url = "$baseUrl?action=opensearch&search=$encoded&limit=4&format=json"
            val req = Request.Builder().url(url).header("User-Agent", WIKIPEDIA_USER_AGENT).build()
            httpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    val body = resp.body?.string() ?: ""
                    val arr = JSONArray(body)
                    val titlesArr = arr.optJSONArray(1)
                    if (titlesArr != null) {
                        for (i in 0 until titlesArr.length()) {
                            val t = titlesArr.optString(i, "")
                            if (t.isNotBlank()) titles.add(t)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w("GeminiService", "OpenSearch failed for '$query' at $baseUrl: ${e.message}")
        }
        return titles
    }

    fun searchWikipediaEntityImage(query: String, sceneNum: Int = 0): String {
        val cleanQuery = cleanEntityQuery(query)
        val rawClean = query.replace(Regex("[^\\p{L}\\p{N}\\s]"), " ").trim()
        val primaryQuery = if (cleanQuery.isNotBlank()) cleanQuery else rawClean
        if (primaryQuery.isBlank()) return ""

        Log.d("GeminiService", "Searching Wikipedia Entity API for '$primaryQuery' (original query: '$query')...")

        val hasDevanagari = primaryQuery.any { it in '\u0900'..'\u097F' }
        val endpoints = if (hasDevanagari) {
            listOf(
                Pair("hi.wikipedia.org", "https://hi.wikipedia.org/w/api.php"),
                Pair("en.wikipedia.org", "https://en.wikipedia.org/w/api.php")
            )
        } else {
            listOf(
                Pair("en.wikipedia.org", "https://en.wikipedia.org/w/api.php"),
                Pair("hi.wikipedia.org", "https://hi.wikipedia.org/w/api.php")
            )
        }

        val queryVariations = listOf(primaryQuery, rawClean)
            .filter { it.isNotBlank() }
            .distinct()

        for (q in queryVariations) {
            for ((domain, baseUrl) in endpoints) {
                try {
                    val encoded = java.net.URLEncoder.encode(q, "UTF-8")
                    val url = "$baseUrl?action=query&generator=search&gsrsearch=$encoded&gsrlimit=10&prop=pageimages|description&pilimit=20&pithumbsize=1280&redirects=1&format=json&origin=*"

                    val request = Request.Builder()
                        .url(url)
                        .header("User-Agent", WIKIPEDIA_USER_AGENT)
                        .build()

                    httpClient.newCall(request).execute().use { response ->
                        if (response.isSuccessful) {
                            val bodyString = response.body?.string() ?: ""
                            val json = JSONObject(bodyString)
                            val queryObj = json.optJSONObject("query")
                            val pages = queryObj?.optJSONObject("pages")
                            if (pages != null) {
                                val keys = pages.keys()
                                val candidates = mutableListOf<Pair<Int, String>>()
                                val topTitles = mutableListOf<String>()
                                while (keys.hasNext()) {
                                    val key = keys.next()
                                    val pageObj = pages.optJSONObject(key) ?: continue
                                    val pageId = pageObj.optInt("pageid", 0)
                                    if (pageId <= 0) continue
                                    val title = pageObj.optString("title", "")
                                    if (title.isNotBlank()) topTitles.add(title)
                                    val index = pageObj.optInt("index", 99)
                                    val thumbnail = pageObj.optJSONObject("thumbnail")
                                    val imgUrl = thumbnail?.optString("source")
                                    if (!imgUrl.isNullOrEmpty() && (imgUrl.startsWith("http://") || imgUrl.startsWith("https://")) &&
                                        !imgUrl.endsWith(".svg", true) && !imgUrl.endsWith(".pdf", true)) {
                                        val safeImgUrl = if (imgUrl.startsWith("http://")) imgUrl.replace("http://", "https://") else imgUrl
                                        candidates.add(Pair(index, safeImgUrl))
                                    }
                                }
                                if (candidates.isNotEmpty()) {
                                    candidates.sortBy { it.first }
                                    val bestUrl = candidates.first().second
                                    Log.d("GeminiService", "Wikipedia Entity API generator search SUCCESS for '$q': $bestUrl")
                                    return bestUrl
                                }

                                // If pages were found but generator didn't include thumbnails, check the REST summary for the top article
                                for (artTitle in topTitles.take(2)) {
                                    val summary = fetchWikipediaPageSummary(artTitle, domain)
                                    if (summary != null && summary.first.isNotBlank()) {
                                        Log.d("GeminiService", "Wikipedia Entity REST summary SUCCESS for '$artTitle': ${summary.first}")
                                        return summary.first
                                    }
                                }
                            }
                        } else {
                            Log.w("GeminiService", "Wikipedia API returned HTTP ${response.code} for '$q' at $baseUrl")
                        }
                    }

                    // OpenSearch fallback if search generator returned 0 pages (useful for spelling variants)
                    val openSearchTitles = resolveWikipediaOpenSearchTitles(q, baseUrl)
                    for (ost in openSearchTitles.take(2)) {
                        val summary = fetchWikipediaPageSummary(ost, domain)
                        if (summary != null && summary.first.isNotBlank()) {
                            Log.d("GeminiService", "Wikipedia OpenSearch + REST summary SUCCESS for '$ost': ${summary.first}")
                            return summary.first
                        }
                    }
                } catch (e: Exception) {
                    Log.e("GeminiService", "Exception in searchWikipediaEntityImage for '$q' at $baseUrl", e)
                }
            }
        }

        return searchWikimediaCommons(primaryQuery, sceneNum)
    }

    fun searchWikipediaEntityImagesList(query: String, maxCount: Int = 12): List<com.ritvyom.yashoraReelgenerator.domain.models.MediaSuggestion> {
        val cleanQuery = cleanEntityQuery(query)
        val rawClean = query.replace(Regex("[^\\p{L}\\p{N}\\s]"), " ").trim()
        val queryToUse = if (cleanQuery.isNotBlank()) cleanQuery else rawClean
        if (queryToUse.isBlank()) return emptyList()

        val results = mutableListOf<com.ritvyom.yashoraReelgenerator.domain.models.MediaSuggestion>()
        val hasDevanagari = queryToUse.any { it in '\u0900'..'\u097F' }
        val endpoints = if (hasDevanagari) {
            listOf(
                Pair("hi.wikipedia.org", "https://hi.wikipedia.org/w/api.php"),
                Pair("en.wikipedia.org", "https://en.wikipedia.org/w/api.php")
            )
        } else {
            listOf(
                Pair("en.wikipedia.org", "https://en.wikipedia.org/w/api.php"),
                Pair("hi.wikipedia.org", "https://hi.wikipedia.org/w/api.php")
            )
        }

        // 1. Wikipedia Article Images with redirects=1, pilimit=20, and rich descriptions
        for ((domain, baseUrl) in endpoints) {
            if (results.size >= maxCount) break
            try {
                val encoded = java.net.URLEncoder.encode(queryToUse, "UTF-8")
                val url = "$baseUrl?action=query&generator=search&gsrsearch=$encoded&gsrlimit=20&prop=pageimages|description&pilimit=20&pithumbsize=1280&redirects=1&format=json&origin=*"
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", WIKIPEDIA_USER_AGENT)
                    .build()
                httpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val bodyString = response.body?.string() ?: ""
                        val json = JSONObject(bodyString)
                        val pages = json.optJSONObject("query")?.optJSONObject("pages")
                        if (pages != null) {
                            val keys = pages.keys()
                            val sortedPages = mutableListOf<JSONObject>()
                            while (keys.hasNext()) {
                                val pageObj = pages.optJSONObject(keys.next()) ?: continue
                                if (pageObj.optInt("pageid", 0) > 0) {
                                    sortedPages.add(pageObj)
                                }
                            }
                            sortedPages.sortBy { it.optInt("index", 99) }
                            for (pageObj in sortedPages) {
                                val title = pageObj.optString("title", "Wikipedia Article")
                                val desc = pageObj.optString("description", "")
                                val thumbnail = pageObj.optJSONObject("thumbnail")
                                val imgUrl = thumbnail?.optString("source") ?: ""
                                if (imgUrl.isNotEmpty() && !imgUrl.endsWith(".svg", true) && !imgUrl.endsWith(".pdf", true)) {
                                    val safeUrl = if (imgUrl.startsWith("http://")) imgUrl.replace("http://", "https://") else imgUrl
                                    if (results.none { it.url == safeUrl }) {
                                        val displayTitle = if (desc.isNotBlank()) "$title — $desc" else title
                                        results.add(com.ritvyom.yashoraReelgenerator.domain.models.MediaSuggestion(
                                            url = safeUrl,
                                            thumbnailUrl = safeUrl,
                                            title = displayTitle,
                                            source = "Wikipedia",
                                            mediaType = "IMAGE"
                                        ))
                                    }
                                }
                                if (results.size >= maxCount) break
                            }
                        }
                    } else {
                        Log.w("GeminiService", "Wikipedia search generator returned HTTP ${response.code} for '$queryToUse' at $baseUrl")
                    }
                }
            } catch (e: Exception) {
                Log.e("GeminiService", "searchWikipediaEntityImagesList error for '$queryToUse' at $baseUrl", e)
            }
        }

        // 2. Wikimedia Commons fallback / enrichment for rich public domain media
        if (results.size < maxCount) {
            try {
                val encoded = java.net.URLEncoder.encode(queryToUse, "UTF-8")
                val url = "https://commons.wikimedia.org/w/api.php?action=query&format=json&generator=search&gsrsearch=$encoded&gsrnamespace=6&prop=imageinfo&iiprop=url|size|mime&iiurlwidth=1280&gsrlimit=20&origin=*"
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", WIKIPEDIA_USER_AGENT)
                    .build()
                httpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val bodyString = response.body?.string() ?: ""
                        val json = JSONObject(bodyString)
                        val pages = json.optJSONObject("query")?.optJSONObject("pages")
                        if (pages != null) {
                            val keys = pages.keys()
                            while (keys.hasNext()) {
                                val key = keys.next()
                                val page = pages.optJSONObject(key) ?: continue
                                val rawTitle = page.optString("title", "").replace("File:", "").replace(Regex("\\.[a-zA-Z0-9]+$"), "").replace('_', ' ')
                                val imageinfo = page.optJSONArray("imageinfo")?.optJSONObject(0) ?: continue
                                val mime = imageinfo.optString("mime", "").lowercase()
                                val directUrl = imageinfo.optString("url") ?: ""
                                val thumb = imageinfo.optString("thumburl") ?: directUrl
                                val chosenThumb = thumb.ifEmpty { directUrl }
                                
                                val isBadMime = mime.contains("svg") || mime.contains("pdf") || mime.contains("tiff") || mime.contains("djvu")
                                val isBadExt = directUrl.endsWith(".svg", true) || directUrl.endsWith(".pdf", true) || directUrl.endsWith(".tif", true) || directUrl.endsWith(".tiff", true)
                                
                                if (directUrl.isNotEmpty() && !isBadMime && !isBadExt) {
                                    val safeDirect = if (directUrl.startsWith("http://")) directUrl.replace("http://", "https://") else directUrl
                                    val safeThumb = if (chosenThumb.startsWith("http://")) chosenThumb.replace("http://", "https://") else chosenThumb
                                    if (results.none { it.url == safeDirect || it.thumbnailUrl == safeThumb }) {
                                        results.add(com.ritvyom.yashoraReelgenerator.domain.models.MediaSuggestion(
                                            url = safeDirect,
                                            thumbnailUrl = safeThumb,
                                            title = if (rawTitle.isNotBlank()) rawTitle else "Wikipedia Image",
                                            source = "Wikipedia",
                                            mediaType = "IMAGE"
                                        ))
                                    }
                                }
                                if (results.size >= maxCount) break
                            }
                        }
                    } else {
                        Log.w("GeminiService", "Wikimedia Commons returned HTTP ${response.code} for '$queryToUse'")
                    }
                }
            } catch (e: Exception) {
                Log.e("GeminiService", "Wikimedia Commons enrichment for Wikipedia failed for '$queryToUse'", e)
            }
        }

        return results
    }

    fun searchWikimediaCommons(query: String, sceneNum: Int): String {
        val qClean = query.replace(Regex("[^\\p{L}\\p{N}\\s]"), " ").trim()
        val words = qClean.split(Regex("\\s+")).filter { it.length > 2 }
        val queriesToTry = mutableListOf<String>()
        if (qClean.isNotBlank()) queriesToTry.add(qClean)
        if (words.size > 2) queriesToTry.add(words.take(2).joinToString(" "))
        if (words.isNotEmpty()) queriesToTry.add(words.first())

        for (qAttempt in queriesToTry.distinct()) {
            for (attempt in 0..1) {
                try {
                    val encoded = java.net.URLEncoder.encode(qAttempt, "UTF-8")
                    val url = "https://commons.wikimedia.org/w/api.php?action=query&format=json&generator=search&gsrsearch=$encoded&gsrnamespace=6&prop=imageinfo&iiprop=url|size|mime&iiurlwidth=1280&gsrlimit=15&origin=*"
                    
                    val request = Request.Builder()
                        .url(url)
                        .header("User-Agent", WIKIPEDIA_USER_AGENT)
                        .build()
                    
                    httpClient.newCall(request).execute().use { response ->
                        if (response.isSuccessful) {
                            val bodyString = response.body?.string() ?: ""
                            val json = JSONObject(bodyString)
                            val queryObj = json.optJSONObject("query")
                            val pages = queryObj?.optJSONObject("pages")
                            if (pages != null) {
                                val keys = pages.keys()
                                val urlsList = mutableListOf<String>()
                                while (keys.hasNext()) {
                                    val key = keys.next()
                                    val pageObj = pages.optJSONObject(key) ?: continue
                                    val imageinfo = pageObj.optJSONArray("imageinfo")
                                    if (imageinfo != null && imageinfo.length() > 0) {
                                        val infoObj = imageinfo.optJSONObject(0) ?: continue
                                        val mime = infoObj.optString("mime", "").lowercase()
                                        val directUrl = infoObj.optString("url") ?: ""
                                        val thumbUrl = infoObj.optString("thumburl") ?: directUrl
                                        val chosenUrl = thumbUrl.ifEmpty { directUrl }
                                        
                                        val isBadMime = mime.contains("svg") || mime.contains("pdf") || mime.contains("tiff") || mime.contains("djvu")
                                        val isBadExt = chosenUrl.endsWith(".svg", true) || chosenUrl.endsWith(".pdf", true) || chosenUrl.endsWith(".tif", true) || chosenUrl.endsWith(".tiff", true)
                                        
                                        if (chosenUrl.isNotEmpty() && !isBadMime && !isBadExt) {
                                            val safeUrl = if (chosenUrl.startsWith("http://")) chosenUrl.replace("http://", "https://") else chosenUrl
                                            urlsList.add(safeUrl)
                                        }
                                    }
                                }
                                if (urlsList.isNotEmpty()) {
                                    val index = (if (sceneNum >= 0) sceneNum else 0) % urlsList.size
                                    val selectedUrl = urlsList[index]
                                    Log.d("GeminiService", "Wikimedia Commons API search success for '$qAttempt': $selectedUrl")
                                    return selectedUrl
                                }
                            }
                        } else {
                            Log.w("GeminiService", "Wikimedia Commons search returned HTTP ${response.code} for '$qAttempt'")
                        }
                    }
                } catch (e: Exception) {
                    Log.w("GeminiService", "Wikimedia Commons search attempt $attempt failed for query '$qAttempt'", e)
                    try { Thread.sleep(120L) } catch (ignored: InterruptedException) {}
                }
            }
        }

        // Automatic relevant scene fallback image
        Log.i("GeminiService", "Wikimedia Commons search returned no results. Automatically fetching relevant scene fallback image.")
        return getThematicSceneFallbackImage(query, sceneNum, "9:16")
    }

    fun searchNekosBest(query: String, sceneNum: Int): String {
        val q = query.lowercase()
        val preferredCategory = when {
            q.contains("cry") || q.contains("sad") || q.contains("tear") -> "cry"
            q.contains("smile") || q.contains("grin") || q.contains("laugh") -> "smile"
            q.contains("happy") || q.contains("joy") || q.contains("excited") -> "happy"
            q.contains("pat") || q.contains("pet") || q.contains("stroke") -> "pat"
            q.contains("wave") || q.contains("hello") || q.contains("bye") || q.contains("greet") -> "wave"
            q.contains("wink") || q.contains("blink") -> "wink"
            q.contains("bored") || q.contains("tired") || q.contains("sleepy") -> "bored"
            q.contains("smug") || q.contains("snicker") -> "smug"
            q.contains("yeet") || q.contains("throw") || q.contains("kick") -> "yeet"
            q.contains("shrug") || q.contains("clueless") -> "shrug"
            q.contains("boy") || q.contains("man") || q.contains("husbando") || q.contains("guy") -> "husbando"
            q.contains("kitsune") || q.contains("fox") -> "kitsune"
            q.contains("neko") || q.contains("catgirl") -> "neko"
            else -> {
                val defaultAnimeStyles = listOf("waifu", "neko", "kitsune")
                defaultAnimeStyles[sceneNum % defaultAnimeStyles.size]
            }
        }

        val categoriesToTry = listOf(preferredCategory, "waifu", "neko", "kitsune").distinct()

        for (category in categoriesToTry) {
            for (attempt in 0..1) {
                try {
                    val url = "https://nekos.best/api/v2/$category?amount=15"
                    val request = Request.Builder()
                        .url(url)
                        .header("User-Agent", "YashoraReelGenerator (Ritvyom@gmail.com)")
                        .build()

                    val response = httpClient.newCall(request).execute()
                    if (response.isSuccessful) {
                        val bodyString = response.body?.string() ?: ""
                        val json = JSONObject(bodyString)
                        val results = json.optJSONArray("results")
                        if (results != null && results.length() > 0) {
                            val index = sceneNum % results.length()
                            val item = results.optJSONObject(index)
                            val imgUrl = item?.optString("url")
                            if (!imgUrl.isNullOrEmpty()) {
                                Log.d("GeminiService", "Nekos.best search success for category '$category': $imgUrl")
                                return imgUrl
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.w("GeminiService", "Nekos.best attempt $attempt failed for category '$category': ${e.message}")
                    try { Thread.sleep(120L) } catch (ignored: InterruptedException) {}
                }
            }
        }

        // Automatic relevant scene fallback image
        Log.i("GeminiService", "Nekos.best API returned no results. Automatically fetching relevant scene fallback image.")
        return getThematicSceneFallbackImage(query, sceneNum, "9:16")
    }

    fun searchJikanAnime(query: String, sceneNum: Int): String {
        val qClean = query.replace(Regex("[^a-zA-Z0-9\\s]"), "").trim()
        val words = qClean.split(Regex("\\s+")).filter { it.length > 2 }
        val queriesToTry = mutableListOf<String>()
        if (qClean.isNotEmpty()) queriesToTry.add(qClean)
        if (words.size > 2) queriesToTry.add(words.take(2).joinToString(" "))
        if (words.isNotEmpty()) queriesToTry.add(words.first())
        queriesToTry.add("anime")

        for (qAttempt in queriesToTry.distinct()) {
            for (attempt in 0..1) {
                try {
                    val encoded = java.net.URLEncoder.encode(qAttempt, "UTF-8")
                    val url = "https://api.jikan.moe/v4/anime?q=$encoded&limit=10"
                    
                    val request = Request.Builder()
                        .url(url)
                        .header("User-Agent", "Mozilla/5.0")
                        .build()

                    val response = httpClient.newCall(request).execute()
                    if (response.isSuccessful) {
                        val bodyString = response.body?.string() ?: ""
                        val json = JSONObject(bodyString)
                        val data = json.optJSONArray("data")
                        if (data != null && data.length() > 0) {
                            val index = sceneNum % data.length()
                            val anime = data.optJSONObject(index)
                            val imagesObj = anime?.optJSONObject("images")
                            val jpgObj = imagesObj?.optJSONObject("jpg")
                            val imageUrl = jpgObj?.optString("large_image_url") ?: jpgObj?.optString("image_url")
                            if (!imageUrl.isNullOrEmpty()) {
                                Log.d("GeminiService", "Jikan MyAnimeList DB search success for query '$qAttempt': $imageUrl")
                                return imageUrl
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.w("GeminiService", "Jikan MyAnimeList attempt $attempt failed for query '$qAttempt': ${e.message}")
                    try { Thread.sleep(150L) } catch (ignored: InterruptedException) {}
                }
            }
        }

        // Automatic relevant scene fallback image
        Log.i("GeminiService", "Jikan Anime DB returned no results. Automatically fetching relevant scene fallback image.")
        return getThematicSceneFallbackImage(query, sceneNum, "9:16")
    }

    fun searchArchiveOrg(query: String, sceneNum: Int): String {
        val qClean = query.replace(Regex("[^a-zA-Z0-9\\s]"), "").trim()
        val words = qClean.split(Regex("\\s+")).filter { it.length > 2 }
        val queriesToTry = mutableListOf<String>()
        if (qClean.isNotEmpty()) queriesToTry.add(qClean)
        if (words.size > 2) queriesToTry.add(words.take(2).joinToString(" "))
        if (words.isNotEmpty()) queriesToTry.add(words.first())
        queriesToTry.add("vintage photography")

        for (qAttempt in queriesToTry.distinct()) {
            for (attempt in 0..1) {
                try {
                    val encoded = java.net.URLEncoder.encode(qAttempt, "UTF-8")
                    val url = "https://archive.org/advancedsearch.php?q=$encoded+AND+mediatype:(image)&fl[]=identifier&rows=15&output=json"
                    
                    val request = Request.Builder()
                        .url(url)
                        .header("User-Agent", "Mozilla/5.0")
                        .build()
                    
                    val response = httpClient.newCall(request).execute()
                    if (response.isSuccessful) {
                        val bodyString = response.body?.string() ?: ""
                        val json = JSONObject(bodyString)
                        val responseObj = json.optJSONObject("response")
                        val docs = responseObj?.optJSONArray("docs")
                        if (docs != null && docs.length() > 0) {
                            val index = sceneNum % docs.length()
                            val doc = docs.optJSONObject(index)
                            val identifier = doc?.optString("identifier")
                            if (!identifier.isNullOrEmpty()) {
                                val imgUrl = "https://archive.org/services/img/$identifier"
                                Log.d("GeminiService", "Archive.org search success for query '$qAttempt': $imgUrl")
                                return imgUrl
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.w("GeminiService", "Archive.org attempt $attempt failed for query '$qAttempt': ${e.message}")
                    try { Thread.sleep(120L) } catch (ignored: InterruptedException) {}
                }
            }
        }

        // Automatic relevant scene fallback image
        Log.i("GeminiService", "Archive.org returned no results. Automatically fetching relevant scene fallback image.")
        return getThematicSceneFallbackImage(query, sceneNum, "9:16")
    }

    fun searchNasaLibrary(query: String, sceneNum: Int, isVideo: Boolean): String {
        val qClean = query.replace(Regex("[^a-zA-Z0-9\\s]"), "").trim()
        val words = qClean.split(Regex("\\s+")).filter { it.length > 2 }
        val queriesToTry = mutableListOf<String>()
        if (qClean.isNotEmpty()) queriesToTry.add(qClean)
        if (words.size > 2) queriesToTry.add(words.take(2).joinToString(" "))
        queriesToTry.add("galaxy space earth")

        val mediaType = if (isVideo) "video" else "image"

        for (qAttempt in queriesToTry.distinct()) {
            for (attempt in 0..1) {
                try {
                    val encoded = java.net.URLEncoder.encode(qAttempt, "UTF-8")
                    val url = "https://images-api.nasa.gov/search?q=$encoded&media_type=$mediaType"
                    
                    val request = Request.Builder()
                        .url(url)
                        .header("User-Agent", "Mozilla/5.0")
                        .build()
                    
                    val response = httpClient.newCall(request).execute()
                    if (response.isSuccessful) {
                        val bodyString = response.body?.string() ?: ""
                        val json = JSONObject(bodyString)
                        val collectionObj = json.optJSONObject("collection")
                        val items = collectionObj?.optJSONArray("items")
                        if (items != null && items.length() > 0) {
                            val index = sceneNum % items.length()
                            val item = items.optJSONObject(index)
                            if (item != null) {
                                if (isVideo) {
                                    val collectionJsonUrl = item.optString("href")
                                    if (!collectionJsonUrl.isNullOrEmpty()) {
                                        val videoRequest = Request.Builder()
                                            .url(collectionJsonUrl)
                                            .header("User-Agent", "Mozilla/5.0")
                                            .build()
                                        val videoResponse = httpClient.newCall(videoRequest).execute()
                                        if (videoResponse.isSuccessful) {
                                            val videoBodyString = videoResponse.body?.string() ?: ""
                                            val videoArray = JSONArray(videoBodyString)
                                            val videoUrls = mutableListOf<String>()
                                            for (vIdx in 0 until videoArray.length()) {
                                                val vUrl = videoArray.optString(vIdx)
                                                if (vUrl.endsWith(".mp4", ignoreCase = true) || vUrl.contains(".mp4?", ignoreCase = true)) {
                                                    videoUrls.add(vUrl)
                                                }
                                            }
                                            if (videoUrls.isNotEmpty()) {
                                                val chosenVideoUrl = videoUrls.find { it.contains("~medium.mp4", ignoreCase = true) }
                                                    ?: videoUrls.find { it.contains("~orig.mp4", ignoreCase = true) }
                                                    ?: videoUrls.find { it.contains("~mobile.mp4", ignoreCase = true) }
                                                    ?: videoUrls.first()
                                                
                                                if (chosenVideoUrl.isNotEmpty()) {
                                                    val finalUrl = if (chosenVideoUrl.startsWith("http://")) chosenVideoUrl.replace("http://", "https://") else chosenVideoUrl
                                                    Log.d("GeminiService", "NASA video URL resolved: $finalUrl")
                                                    return finalUrl
                                                }
                                            }
                                        }
                                    }
                                } else {
                                    val links = item.optJSONArray("links")
                                    if (links != null && links.length() > 0) {
                                        val linkObj = links.optJSONObject(0)
                                        val thumbUrl = linkObj?.optString("href") ?: ""
                                        if (thumbUrl.isNotEmpty()) {
                                            val highResUrl = if (thumbUrl.contains("~thumb")) {
                                                thumbUrl.replace("~thumb", "~medium")
                                            } else {
                                                thumbUrl
                                            }
                                            val finalUrl = if (highResUrl.startsWith("http://")) highResUrl.replace("http://", "https://") else highResUrl
                                            Log.d("GeminiService", "NASA image URL resolved for '$qAttempt': $finalUrl")
                                            return finalUrl
                                        }
                                    }
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.w("GeminiService", "NASA API attempt $attempt failed for query '$qAttempt': ${e.message}")
                    try { Thread.sleep(120L) } catch (ignored: InterruptedException) {}
                }
            }
        }

        // Automatic relevant scene fallback
        Log.i("GeminiService", "NASA Library search returned no results. Automatically fetching relevant scene fallback.")
        return if (isVideo) getThematicSceneFallbackVideo(query, sceneNum) else getThematicSceneFallbackImage(query, sceneNum, "9:16")
    }

    fun searchGiphyMemes(query: String, sceneNum: Int): String {
        val qClean = query.replace(Regex("[^a-zA-Z0-9\\s]"), "").trim()
        val words = qClean.split(Regex("\\s+")).filter { it.length > 2 }
        val queriesToTry = mutableListOf<String>()
        if (qClean.isNotEmpty()) queriesToTry.add(qClean)
        if (words.size > 2) queriesToTry.add(words.take(2).joinToString(" "))
        queriesToTry.add("funny reaction meme")

        for (qAttempt in queriesToTry.distinct()) {
            for (attempt in 0..1) {
                try {
                    val encoded = java.net.URLEncoder.encode(qAttempt, "UTF-8")
                    val url = "https://api.giphy.com/v1/gifs/search?api_key=dc6zaTOxFJmzC&q=$encoded&limit=15&rating=g"
                    
                    val request = Request.Builder()
                        .url(url)
                        .header("User-Agent", "Mozilla/5.0")
                        .build()
                    
                    val response = httpClient.newCall(request).execute()
                    if (response.isSuccessful) {
                        val bodyString = response.body?.string() ?: ""
                        val json = JSONObject(bodyString)
                        val data = json.optJSONArray("data")
                        if (data != null && data.length() > 0) {
                            val index = sceneNum % data.length()
                            val item = data.optJSONObject(index)
                            val imagesObj = item?.optJSONObject("images")
                            val originalObj = imagesObj?.optJSONObject("original")
                            var gifUrl = originalObj?.optString("url") ?: ""
                            if (gifUrl.isNotEmpty()) {
                                if (gifUrl.startsWith("http://")) {
                                    gifUrl = gifUrl.replace("http://", "https://")
                                }
                                Log.d("GeminiService", "Giphy search success for '$qAttempt': $gifUrl")
                                return gifUrl
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.w("GeminiService", "Giphy attempt $attempt failed for query '$qAttempt': ${e.message}")
                    try { Thread.sleep(120L) } catch (ignored: InterruptedException) {}
                }
            }
        }

        // Automatic relevant scene fallback image
        Log.i("GeminiService", "Giphy memes search returned no results. Automatically fetching relevant scene fallback image.")
        return getThematicSceneFallbackImage(query, sceneNum, "9:16")
    }

    fun searchNhtsaVpicApi(query: String, sceneNum: Int): String {
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
        
        val makesToTry = listOf(make, "ferrari", "tesla", "toyota").distinct()

        for (m in makesToTry) {
            for (attempt in 0..1) {
                try {
                    val encodedMake = java.net.URLEncoder.encode(m, "UTF-8")
                    val url = "https://vpic.nhtsa.dot.gov/api/vehicles/getmodelsformake/$encodedMake?format=json"
                    
                    val request = Request.Builder()
                        .url(url)
                        .header("User-Agent", "Mozilla/5.0")
                        .build()
                    
                    val response = httpClient.newCall(request).execute()
                    if (response.isSuccessful) {
                        val bodyString = response.body?.string() ?: ""
                        val json = JSONObject(bodyString)
                        val results = json.optJSONArray("Results")
                        if (results != null && results.length() > 0) {
                            val index = sceneNum % results.length()
                            val resultObj = results.optJSONObject(index)
                            val modelName = resultObj?.optString("Model_Name") ?: ""
                            if (modelName.isNotEmpty()) {
                                Log.d("GeminiService", "NHTSA success: found vehicle model '$modelName' for make '$m'")
                                val prompt = "A premium luxury cinematic photographic showcase of a $m $modelName, modern automotive design, sunset background, stunning reflection, hyper-detailed, 8k resolution"
                                val encodedPrompt = java.net.URLEncoder.encode(prompt, "UTF-8")
                                return "https://image.pollinations.ai/p/$encodedPrompt?width=1080&height=1920&seed=${modelName.hashCode()}&nologo=true"
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.w("GeminiService", "NHTSA attempt $attempt failed for make '$m': ${e.message}")
                    try { Thread.sleep(120L) } catch (ignored: InterruptedException) {}
                }
            }
        }

        // Automatic relevant scene fallback image
        Log.i("GeminiService", "NHTSA vPIC API returned no results. Automatically fetching relevant scene fallback image.")
        return getThematicSceneFallbackImage(query, sceneNum, "9:16")
    }

    fun searchOpenFdaApi(query: String, sceneNum: Int): String {
        val qClean = query.replace(Regex("[^a-zA-Z0-9\\s]"), "").trim()
        val words = qClean.split(Regex("\\s+")).filter { it.length > 2 }
        val queriesToTry = mutableListOf<String>()
        if (qClean.isNotEmpty()) queriesToTry.add(qClean)
        if (words.size > 2) queriesToTry.add(words.take(2).joinToString(" "))
        queriesToTry.add("aspirin")

        for (qAttempt in queriesToTry.distinct()) {
            for (attempt in 0..1) {
                try {
                    val encodedQuery = java.net.URLEncoder.encode(qAttempt, "UTF-8")
                    val url = "https://api.fda.gov/drug/label.json?search=description:$encodedQuery&limit=10"
                    
                    val request = Request.Builder()
                        .url(url)
                        .header("User-Agent", "Mozilla/5.0")
                        .build()
                    
                    val response = httpClient.newCall(request).execute()
                    if (response.isSuccessful) {
                        val bodyString = response.body?.string() ?: ""
                        val json = JSONObject(bodyString)
                        val results = json.optJSONArray("results")
                        if (results != null && results.length() > 0) {
                            val index = sceneNum % results.length()
                            val resultObj = results.optJSONObject(index)
                            val openfda = resultObj?.optJSONObject("openfda")
                            val brandNames = openfda?.optJSONArray("brand_name")
                            val brandName = if (brandNames != null && brandNames.length() > 0) brandNames.getString(0) else ""
                            val activeIngredients = openfda?.optJSONArray("active_ingredient") ?: resultObj?.optJSONArray("active_ingredient")
                            val activeIngredient = if (activeIngredients != null && activeIngredients.length() > 0) activeIngredients.getString(0) else ""
                            
                            val nameToUse = when {
                                brandName.isNotEmpty() -> brandName
                                activeIngredient.isNotEmpty() -> activeIngredient
                                else -> qAttempt
                            }
                            
                            Log.d("GeminiService", "OpenFDA success: found drug name '$nameToUse'")
                            val prompt = "A professional high-end studio shot of clinical pharmaceutical packaging of $nameToUse medicine bottle, modern minimal healthcare design, bright soft studio light, white background, ultra-premium medical product photograph"
                            val encodedPrompt = java.net.URLEncoder.encode(prompt, "UTF-8")
                            return "https://image.pollinations.ai/p/$encodedPrompt?width=1080&height=1920&seed=${nameToUse.hashCode()}&nologo=true"
                        }
                    }
                } catch (e: Exception) {
                    Log.w("GeminiService", "OpenFDA attempt $attempt failed for query '$qAttempt': ${e.message}")
                    try { Thread.sleep(120L) } catch (ignored: InterruptedException) {}
                }
            }
        }

        // Automatic relevant scene fallback image
        Log.i("GeminiService", "OpenFDA API returned no results. Automatically fetching relevant scene fallback image.")
        return getThematicSceneFallbackImage(query, sceneNum, "9:16")
    }

    fun searchWhoApi(query: String, sceneNum: Int): String {
        val qClean = query.replace(Regex("[^a-zA-Z0-9\\s]"), "").trim()
        val words = qClean.split(Regex("\\s+")).filter { it.length > 2 }
        val queriesToTry = mutableListOf<String>()
        if (qClean.isNotEmpty()) queriesToTry.add(qClean)
        if (words.size > 2) queriesToTry.add(words.take(2).joinToString(" "))
        queriesToTry.add("")

        for (qAttempt in queriesToTry.distinct()) {
            for (attempt in 0..1) {
                try {
                    val url = if (qAttempt.isNotEmpty()) {
                        val encodedQuery = java.net.URLEncoder.encode(qAttempt, "UTF-8")
                        "https://ghoapi.azureedge.net/api/Indicator?\$filter=contains(IndicatorName,%20'$encodedQuery')"
                    } else {
                        "https://ghoapi.azureedge.net/api/Indicator?\$top=10"
                    }
                    
                    val request = Request.Builder()
                        .url(url)
                        .header("User-Agent", "Mozilla/5.0")
                        .build()
                    
                    val response = httpClient.newCall(request).execute()
                    if (response.isSuccessful) {
                        val bodyString = response.body?.string() ?: ""
                        val json = JSONObject(bodyString)
                        val valueArray = json.optJSONArray("value")
                        if (valueArray != null && valueArray.length() > 0) {
                            val index = sceneNum % valueArray.length()
                            val indicatorObj = valueArray.optJSONObject(index)
                            val indicatorName = indicatorObj?.optString("IndicatorName") ?: ""
                            if (indicatorName.isNotEmpty()) {
                                Log.d("GeminiService", "WHO success: found indicator '$indicatorName'")
                                val prompt = "A clean professional conceptual medical illustration about $indicatorName, global health awareness, clean vector illustration style, minimal and symbolic, vivid modern color palette"
                                val encodedPrompt = java.net.URLEncoder.encode(prompt, "UTF-8")
                                return "https://image.pollinations.ai/p/$encodedPrompt?width=1080&height=1920&seed=${indicatorName.hashCode()}&nologo=true"
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.w("GeminiService", "WHO attempt $attempt failed for query '$qAttempt': ${e.message}")
                    try { Thread.sleep(120L) } catch (ignored: InterruptedException) {}
                }
            }
        }

        // Automatic relevant scene fallback image
        Log.i("GeminiService", "WHO API returned no results. Automatically fetching relevant scene fallback image.")
        return getThematicSceneFallbackImage(query, sceneNum, "9:16")
    }

    suspend fun normalizeTopicToEnglish(topic: String, customGeminiKey: String = ""): String = withContext(Dispatchers.IO) {
        val trimmed = topic.trim()
        if (trimmed.isEmpty()) return@withContext ""

        val lower = trimmed.lowercase()
        // Simple manual mappings for immediate, reliable, and free resolution of common topics
        if (lower == "nasa" || lower.contains("nasa ke baare") || lower.contains("nasa ke bare") || lower.contains("about nasa") || lower.contains("batao nasa")) {
            return@withContext "nasa"
        }
        if (lower == "space exploration" || lower.contains("space exploration") || lower.contains("antariksh") || lower.contains("antariksh vigyan")) {
            return@withContext "space exploration"
        }
        if (lower.contains("bitcoin") || lower.contains("crypto") || lower.contains("cryptocurrency")) {
            return@withContext "bitcoin"
        }
        if (lower.contains("financial") || lower.contains("paisa") || lower.contains("paise") || lower.contains("rupay") || lower.contains("finance")) {
            return@withContext "personal finance"
        }

        val prompt = """
            Normalize and translate the following video topic (which may be in English, Hindi, Hinglish, Spanish, French, etc.) into a single, standardized, clean English core topic keyword/phrase (e.g., "nasa ke baare me batao" -> "nasa", "about nasa" -> "nasa", "importance of space exploration" -> "space exploration", "3 financial secrets" -> "personal finance", "bhoot ki kahani" -> "ghost story").
            Only return the translated clean English term. Do not add any conversational text, explanations, intro, or formatting.
            
            Topic: $trimmed
        """.trimIndent()

        val candidateKeys = resolveCandidateGeminiKeys(customGeminiKey)
        var normalizedText = ""

        if (candidateKeys.isNotEmpty()) {
            val systemInstructionJson = JSONObject().apply {
                put("parts", JSONArray().put(JSONObject().apply {
                    put("text", "You are a precise topic normalizer. Normalize any user topic in any language to a standardized English core topic name.")
                }))
            }
            val requestJson = JSONObject().apply {
                put("contents", JSONArray().put(JSONObject().apply {
                    put("parts", JSONArray().put(JSONObject().apply {
                        put("text", prompt)
                    }))
                }))
                put("generationConfig", JSONObject().apply {
                    put("temperature", 0.1)
                    put("maxOutputTokens", 50)
                })
                put("systemInstruction", systemInstructionJson)
            }

            val mediaType = "application/json; charset=utf-8".toMediaType()
            val requestBody = requestJson.toString().toRequestBody(mediaType)

            keyLoop@ for ((keyTierLabel, currentApiKey) in candidateKeys) {
                Log.i("GeminiService", "Attempting normalizeTopicToEnglish direct REST API with $keyTierLabel...")
                for (modelName in MODERN_MODELS) {
                    try {
                        val url = "https://generativelanguage.googleapis.com/v1beta/models/$modelName:generateContent?key=$currentApiKey"
                        val request = Request.Builder()
                            .url(url)
                            .post(requestBody)
                            .header("Content-Type", "application/json")
                            .build()

                        val response = httpClient.newCall(request).execute()
                        if (response.isSuccessful) {
                            val bodyString = response.body?.string() ?: ""
                            val json = JSONObject(bodyString)
                            val candidates = json.optJSONArray("candidates")
                            val content = candidates?.optJSONObject(0)?.optJSONObject("content")
                            val parts = content?.optJSONArray("parts")
                            if (parts != null && parts.length() > 0) {
                                val sb = StringBuilder()
                                for (p in 0 until parts.length()) {
                                    val partObj = parts.getJSONObject(p)
                                    val isThought = partObj.optBoolean("thought", false)
                                    val partText = partObj.optString("text", "")
                                    if (!isThought && partText.isNotEmpty()) {
                                        if (sb.isNotEmpty()) sb.append("\n")
                                        sb.append(partText)
                                    }
                                }
                                val extracted = (if (sb.isNotEmpty()) sb.toString() else parts.getJSONObject(0).optString("text", "")).trim()
                                if (extracted.isNotEmpty()) {
                                    normalizedText = extracted
                                    Log.i("GeminiService", "[$keyTierLabel] normalizeTopicToEnglish completed successfully via REST model $modelName: '$normalizedText'")
                                    break@keyLoop
                                }
                            }
                        } else {
                            Log.w("GeminiService", "[$keyTierLabel] normalizeTopicToEnglish REST Model $modelName failed with response code: ${response.code}")
                            if (PriorityApiGateway.isAuthOrQuotaCritical(response.code)) {
                                PriorityApiGateway.logKeyTierFailover(keyTierLabel, "next key tier", "HTTP ${response.code}")
                                break
                            }
                        }
                    } catch (e: Exception) {
                        Log.w("GeminiService", "[$keyTierLabel] normalizeTopicToEnglish REST Model $modelName failed: ${e.message}")
                    }
                }
            }
        }

        if (normalizedText.isEmpty()) {
            val backends = listOf(
                com.google.firebase.ai.type.GenerativeBackend.googleAI(), // Free Gemini Developer API (Firebase Spark plan)
                com.google.firebase.ai.type.GenerativeBackend.vertexAI(location = "us-central1"),
                com.google.firebase.ai.type.GenerativeBackend.vertexAI(location = "global")
            )
            backendLoop@ for (backend in backends) {
                for (firebaseModel in FIREBASE_MODELS) {
                    try {
                        val model = com.google.firebase.Firebase.ai(backend = backend)
                            .generativeModel(
                                modelName = firebaseModel,
                                generationConfig = com.google.firebase.ai.type.generationConfig {
                                    temperature = 0.1f
                                    maxOutputTokens = 50
                                },
                                systemInstruction = com.google.firebase.ai.type.content {
                                    text("You are a precise topic normalizer. Normalize any user topic in any language to a standardized English core topic name.")
                                }
                            )
                        val response = model.generateContent(prompt)
                        val text = response.text?.trim() ?: ""
                        if (text.isNotEmpty()) {
                            normalizedText = text
                            Log.i("GeminiService", "normalizeTopicToEnglish completed successfully via Firebase SDK: '$normalizedText'")
                            break@backendLoop
                        }
                    } catch (fEx: Exception) {
                        Log.w("GeminiService", "normalizeTopicToEnglish Firebase SDK model $firebaseModel failed: ${fEx.message}")
                    }
                }
            }
        }

        if (normalizedText.isNotEmpty()) {
            val cleanResult = normalizedText.replace(Regex("[^a-zA-Z0-9\\s\\-]"), "").trim().lowercase()
            if (cleanResult.isNotEmpty()) {
                return@withContext cleanResult
            }
        }

        return@withContext trimmed.lowercase()
    }

    suspend fun shouldReuseCachedScript(
        userTopic: String,
        userTone: String,
        userLanguage: String,
        userDuration: String,
        cachedTone: String,
        cachedLanguage: String,
        cachedDuration: String,
        cachedTitle: String,
        cachedScriptContent: String,
        customGeminiKey: String = ""
    ): Boolean = withContext(Dispatchers.IO) {
        val trimmedTopic = userTopic.trim()
        if (trimmedTopic.isEmpty() || cachedScriptContent.isBlank()) return@withContext false

        // 1. Precise local comparison for tone, language, and duration
        if (!userTone.equals(cachedTone, ignoreCase = true) ||
            !userLanguage.equals(cachedLanguage, ignoreCase = true) ||
            !userDuration.equals(cachedDuration, ignoreCase = true)
        ) {
            Log.i("GeminiService", "Cached script metadata mismatch (Tone: $userTone vs $cachedTone, Lang: $userLanguage vs $cachedLanguage, Dur: $userDuration vs $cachedDuration). Generating new.")
            return@withContext false
        }

        // 2. Ask Gemini to evaluate semantic match
        val prompt = """
            We have an existing cached video script with title "$cachedTitle" and the following script content:
            ---
            $cachedScriptContent
            ---
            
            The user is now requesting to generate a brand new video script with the following request:
            Topic: "$trimmedTopic"
            
            Evaluate whether the existing cached script specifically, accurately, and perfectly answers the user's new request.
            - If the user's topic is semantically a perfect match (e.g., "about firebase" vs "google firebase" or "nasa" vs "about nasa"), respond with "REUSE".
            - If the user's topic requires a different angle, different details, different tutorial steps, or has a distinct intent (e.g., "about firebase" vs "how to use firebase", "firebase vs supabase", "firebase tutorial" vs "firebase login"), respond with "GENERATE_NEW" because the cached content is not an exact fit.
            - If you are in doubt, err on the side of "GENERATE_NEW" to provide a fresh, precise, high-quality script for the user.
            
            Respond with ONLY "REUSE" or "GENERATE_NEW". Do not include any other words, markdown, or punctuation.
        """.trimIndent()

        val candidateKeys = resolveCandidateGeminiKeys(customGeminiKey)
        var decision = ""

        if (candidateKeys.isNotEmpty()) {
            val systemInstructionJson = JSONObject().apply {
                put("parts", JSONArray().put(JSONObject().apply {
                    put("text", "You are an intelligent cache validation assistant. Your job is to decide whether a cached script perfectly matches a new user topic or if we should generate a new one.")
                }))
            }
            val requestJson = JSONObject().apply {
                put("contents", JSONArray().put(JSONObject().apply {
                    put("parts", JSONArray().put(JSONObject().apply {
                        put("text", prompt)
                    }))
                }))
                put("generationConfig", JSONObject().apply {
                    put("temperature", 0.0)
                    put("maxOutputTokens", 10)
                })
                put("systemInstruction", systemInstructionJson)
            }

            val mediaType = "application/json; charset=utf-8".toMediaType()
            val requestBody = requestJson.toString().toRequestBody(mediaType)

            keyLoop@ for ((keyTierLabel, currentApiKey) in candidateKeys) {
                Log.i("GeminiService", "Attempting shouldReuseCachedScript direct REST API with $keyTierLabel...")
                for (modelName in MODERN_MODELS) {
                    try {
                        val url = "https://generativelanguage.googleapis.com/v1beta/models/$modelName:generateContent?key=$currentApiKey"
                        val request = Request.Builder()
                            .url(url)
                            .post(requestBody)
                            .header("Content-Type", "application/json")
                            .build()

                        val response = httpClient.newCall(request).execute()
                        if (response.isSuccessful) {
                            val bodyString = response.body?.string() ?: ""
                            val json = JSONObject(bodyString)
                            val candidates = json.optJSONArray("candidates")
                            val content = candidates?.optJSONObject(0)?.optJSONObject("content")
                            val parts = content?.optJSONArray("parts")
                            if (parts != null && parts.length() > 0) {
                                val sb = StringBuilder()
                                for (p in 0 until parts.length()) {
                                    val partObj = parts.getJSONObject(p)
                                    val isThought = partObj.optBoolean("thought", false)
                                    val partText = partObj.optString("text", "")
                                    if (!isThought && partText.isNotEmpty()) {
                                        if (sb.isNotEmpty()) sb.append("\n")
                                        sb.append(partText)
                                    }
                                }
                                val extracted = (if (sb.isNotEmpty()) sb.toString() else parts.getJSONObject(0).optString("text", "")).trim()
                                if (extracted.isNotEmpty()) {
                                    decision = extracted
                                    Log.i("GeminiService", "[$keyTierLabel] shouldReuseCachedScript completed successfully via REST model $modelName: '$decision'")
                                    break@keyLoop
                                }
                            }
                        } else {
                            Log.w("GeminiService", "[$keyTierLabel] shouldReuseCachedScript REST Model $modelName failed with response code: ${response.code}")
                            if (PriorityApiGateway.isAuthOrQuotaCritical(response.code)) {
                                PriorityApiGateway.logKeyTierFailover(keyTierLabel, "next key tier", "HTTP ${response.code}")
                                break
                            }
                        }
                    } catch (e: Exception) {
                        Log.w("GeminiService", "[$keyTierLabel] shouldReuseCachedScript REST Model $modelName failed: ${e.message}")
                    }
                }
            }
        }

        if (decision.isEmpty()) {
            val backends = listOf(
                com.google.firebase.ai.type.GenerativeBackend.googleAI(), // Free Gemini Developer API (Firebase Spark plan)
                com.google.firebase.ai.type.GenerativeBackend.vertexAI(location = "us-central1"),
                com.google.firebase.ai.type.GenerativeBackend.vertexAI(location = "global")
            )
            backendLoop@ for (backend in backends) {
                for (firebaseModel in FIREBASE_MODELS) {
                    try {
                        val model = com.google.firebase.Firebase.ai(backend = backend)
                            .generativeModel(
                                modelName = firebaseModel,
                                generationConfig = com.google.firebase.ai.type.generationConfig {
                                    temperature = 0.0f
                                    maxOutputTokens = 10
                                },
                                systemInstruction = com.google.firebase.ai.type.content {
                                    text("You are an intelligent cache validation assistant. Your job is to decide whether a cached script perfectly matches a new user topic or if we should generate a new one.")
                                }
                            )
                        val response = model.generateContent(prompt)
                        val text = response.text?.trim() ?: ""
                        if (text.isNotEmpty()) {
                            decision = text
                            Log.i("GeminiService", "shouldReuseCachedScript completed successfully via Firebase SDK: '$decision'")
                            break@backendLoop
                        }
                    } catch (fEx: Exception) {
                        Log.w("GeminiService", "shouldReuseCachedScript Firebase SDK model $firebaseModel failed: ${fEx.message}")
                    }
                }
            }
        }

        val finalDecision = decision.uppercase().replace(Regex("[^A-Z_]"), "")
        Log.i("GeminiService", "Cache decision for userTopic='$userTopic', cachedTitle='$cachedTitle': $finalDecision (Raw: $decision)")

        return@withContext finalDecision == "REUSE"
    }

    fun cleanScriptText(text: String): String {
        if (text.isBlank()) return ""

        val lines = text.split("\n")
        val cleanedLines = mutableListOf<String>()

        for (line in lines) {
            var trimmed = line.trim()
            if (trimmed.isEmpty()) {
                cleanedLines.add("")
                continue
            }

            val upper = trimmed.uppercase()

            // 1. Completely skip lines that are purely headers or title tags
            if (upper == "## HOOK" || upper == "HOOK:" || upper == "# HOOK" || upper == "**HOOK:**" || upper == "*HOOK:*" ||
                upper == "## SCRIPT BODY" || upper == "SCRIPT BODY:" || upper == "# SCRIPT BODY" || upper == "**SCRIPT BODY:**" ||
                upper == "## SCRIPT" || upper == "SCRIPT:" || upper == "# SCRIPT" || upper == "**SCRIPT:**" ||
                upper == "## BODY" || upper == "BODY:" || upper == "# BODY" || upper == "**BODY:**" ||
                upper == "## OUTRO" || upper == "OUTRO:" || upper == "# OUTRO" || upper == "**OUTRO:**" ||
                upper == "## CTA" || upper == "CTA:" || upper == "# CTA" || upper == "**CTA:**" ||
                upper == "## OUTRO & CTA" || upper == "OUTRO & CTA:" || upper == "# OUTRO & CTA" || upper == "**OUTRO & CTA:**" ||
                upper == "## OUTRO AND CTA" || upper == "OUTRO AND CTA:" || upper == "# OUTRO AND CTA" || upper == "**OUTRO AND CTA:**" ||
                upper.startsWith("SCENE ") || upper.startsWith("*SCENE") || upper.startsWith("**SCENE")
            ) {
                continue
            }

            // 2. Remove prefixes from lines if they are inline headers or TITLE lines
            if (upper.startsWith("# TITLE:") || upper.startsWith("# TITLE") || upper.startsWith("TITLE:") || upper.startsWith("# TITLE :")) {
                continue
            }

            var lineCleaned = trimmed
            if (upper.startsWith("HOOK:")) {
                lineCleaned = trimmed.substring(5).trim()
            } else if (upper.startsWith("SCRIPT BODY:")) {
                lineCleaned = trimmed.substring(12).trim()
            } else if (upper.startsWith("BODY:")) {
                lineCleaned = trimmed.substring(5).trim()
            } else if (upper.startsWith("OUTRO:")) {
                lineCleaned = trimmed.substring(6).trim()
            } else if (upper.startsWith("CTA:")) {
                lineCleaned = trimmed.substring(4).trim()
            } else if (upper.startsWith("OUTRO & CTA:")) {
                lineCleaned = trimmed.substring(12).trim()
            } else if (upper.startsWith("NARRATOR:")) {
                lineCleaned = trimmed.substring(9).trim()
            } else if (upper.startsWith("NARRATION:")) {
                lineCleaned = trimmed.substring(10).trim()
            } else if (upper.startsWith("VOICEOVER:")) {
                lineCleaned = trimmed.substring(10).trim()
            } else if (upper.startsWith("VO:")) {
                lineCleaned = trimmed.substring(3).trim()
            }

            // Remove markdown heading symbols
            if (lineCleaned.startsWith("#")) {
                lineCleaned = lineCleaned.replace(Regex("^#+\\s*"), "").trim()
            }

            // 3. Remove parenthetical instructions, camera/visual notes in brackets or parentheses
            lineCleaned = lineCleaned.replace(Regex("\\[.*?\\]"), "")

            val instructionKeywords = listOf(
                "camera", "cut", "scene", "music", "sound", "visual", "insert", "note", "show", "background",
                "video", "photo", "clip", "image", "suggest", "read", "pause", "fade", "transition", "zoom", "panning",
                "b-roll", "broll", "sfx", "sfx:", "bgm", "bgm:", "host", "narrator", "seconds", "sec"
            )
            val parenRegex = Regex("\\(([^)]+)\\)")
            lineCleaned = parenRegex.replace(lineCleaned) { matchResult ->
                val content = matchResult.groupValues[1].lowercase()
                val hasKeyword = instructionKeywords.any { content.contains(it) }
                val isTimestamp = content.contains(Regex("\\d+:\\d+")) || content.contains(Regex("\\d+\\s*sec"))
                if (hasKeyword || isTimestamp || content.length > 40) {
                    ""
                } else {
                    matchResult.value
                }
            }

            // Remove asterisks markdown decoration completely
            lineCleaned = lineCleaned.replace("**", "").replace("*", "")

            // 4. Remove timestamps or time-ranges
            lineCleaned = lineCleaned.replace(Regex("\\b\\d{1,2}:\\d{2}\\b(-\\b\\d{1,2}:\\d{2}\\b)?"), "")
            lineCleaned = lineCleaned.replace(Regex("\\[\\d{1,2}:\\d{2}(-\\d{1,2}:\\d{2})?\\]"), "")
            lineCleaned = lineCleaned.replace(Regex("\\(\\d{1,2}:\\d{2}(-\\d{1,2}:\\d{2})?\\)"), "")

            // 5. Strip trailing/leading hashtags
            lineCleaned = lineCleaned.replace(Regex("#\\w+"), "")

            // Clean up double spaces or trailing punctuation leftovers from bracket/paren removals
            lineCleaned = lineCleaned.replace(Regex("\\s+"), " ").trim()

            // Skip empty lines or line remnants that are just punctuation or garbage
            if (lineCleaned.isEmpty() || lineCleaned == ":" || lineCleaned == "-" || lineCleaned == "•" || lineCleaned == "*") {
                continue
            }

            cleanedLines.add(lineCleaned)
        }

        val rawCleaned = cleanedLines.joinToString("\n")
        return rawCleaned.replace(Regex("\\n{3,}"), "\n\n").trim()
    }

    private fun extractSearchKeywordsForMedia(rawQuery: String): List<String> {
        val stopWords = setOf(
            "cinematic", "shot", "of", "a", "an", "the", "in", "on", "at", "with", "and", "or", "to", "for", "from",
            "by", "is", "are", "was", "were", "be", "been", "being", "have", "has", "had", "do", "does", "did",
            "show", "showing", "view", "scene", "background", "photo", "video", "clip", "footage", "4k", "hd",
            "shimmering", "visibly", "uncomfortable", "fanning", "people", "like", "such", "as", "detailed", "ultra",
            "hyperrealistic", "realistic", "high", "quality", "rendered", "illustration", "image", "wide", "close", "up",
            "scene", "visual", "look", "looking", "atmosphere", "style", "vertical", "horizontal"
        )
        val cleaned = rawQuery.replace(Regex("[^\\p{L}\\p{N}\\s]"), " ").trim()
        val words = cleaned.split(Regex("\\s+")).filter { it.isNotEmpty() }
        val meaningfulWords = words.filter { it.lowercase() !in stopWords && it.length > 2 }
        
        val candidateQueries = mutableListOf<String>()
        
        // 1. If raw query is short (1-3 words), prioritize raw query
        if (words.size in 1..3) {
            candidateQueries.add(words.joinToString(" "))
        }
        
        // 2. Meaningful words first 2-3 words phrase (e.g. "desert landscape" or "hot cityscape")
        if (meaningfulWords.isNotEmpty()) {
            candidateQueries.add(meaningfulWords.take(2).joinToString(" "))
            if (meaningfulWords.size >= 3) {
                candidateQueries.add(meaningfulWords.take(3).joinToString(" "))
            }
            // Also add individual strong words
            for (w in meaningfulWords.take(3)) {
                if (w.length >= 4 && !candidateQueries.contains(w)) {
                    candidateQueries.add(w)
                }
            }
        }
        
        // 3. Fallback to raw words directly
        if (candidateQueries.isEmpty() && words.isNotEmpty()) {
            candidateQueries.add(words.take(2).joinToString(" "))
        }
        
        return candidateQueries.ifEmpty { listOf("nature", "city", "landscape") }
    }

    // --- ENHANCED SPECIALIZED MEDIA SEARCH HELPERS ---

    suspend fun searchTheMealDbList(query: String, limit: Int = 14): List<com.ritvyom.yashoraReelgenerator.domain.models.MediaSuggestion> = withContext(Dispatchers.IO) {
        val list = mutableListOf<com.ritvyom.yashoraReelgenerator.domain.models.MediaSuggestion>()
        val seenUrls = mutableSetOf<String>()
        try {
            val qClean = query.replace(Regex("[^\\p{L}\\p{N}\\s]"), " ").trim()
            val words = qClean.split(Regex("\\s+")).filter { it.length > 2 }
            
            // 1. If user typed specific food words or dish name, search direct meals
            val foodKeywords = words.filter { w ->
                val lower = w.lowercase()
                listOf("cake", "chicken", "pasta", "soup", "salad", "dessert", "beef", "fish", "potato", 
                       "rice", "bread", "cheese", "curry", "pie", "burger", "pizza", "paneer", "biryani",
                       "meat", "lamb", "pork", "seafood", "breakfast", "dinner", "lunch", "snack", "egg",
                       "fruit", "apple", "chocolate", "cookie", "vegetable", "tomato", "chili", "sauce").contains(lower)
            }
            val searchTerms = if (foodKeywords.isNotEmpty()) foodKeywords else words.filter { it.length >= 4 }

            for (term in searchTerms.take(2)) {
                if (list.size >= limit) break
                val encoded = java.net.URLEncoder.encode(term, "UTF-8")
                val url = "https://www.themealdb.com/api/json/v1/1/search.php?s=$encoded"
                val req = Request.Builder().url(url).header("User-Agent", "Mozilla/5.0").build()
                httpClient.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val body = resp.body?.string() ?: ""
                        val meals = JSONObject(body).optJSONArray("meals")
                        if (meals != null) {
                            for (i in 0 until meals.length()) {
                                if (list.size >= limit) break
                                val m = meals.optJSONObject(i) ?: continue
                                val thumb = m.optString("strMealThumb")
                                val name = m.optString("strMeal")
                                val cat = m.optString("strCategory", "Dish")
                                if (!thumb.isNullOrEmpty() && seenUrls.add(thumb)) {
                                    val safeThumb = if (thumb.startsWith("http://")) thumb.replace("http://", "https://") else thumb
                                    list.add(
                                        com.ritvyom.yashoraReelgenerator.domain.models.MediaSuggestion(
                                            url = safeThumb,
                                            thumbnailUrl = safeThumb,
                                            title = if (name.isNotBlank()) "$name ($cat)" else "Recipe Dish",
                                            source = "TheMealDB",
                                            mediaType = "IMAGE"
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 2. If list is still small, populate from diverse delicious categories
            if (list.size < limit) {
                val categories = listOf("Dessert", "Pasta", "Vegetarian", "Seafood", "Breakfast", "Chicken", "Starter", "Side")
                for (cat in categories) {
                    if (list.size >= limit) break
                    val catUrl = "https://www.themealdb.com/api/json/v1/1/filter.php?c=$cat"
                    val catReq = Request.Builder().url(catUrl).header("User-Agent", "Mozilla/5.0").build()
                    httpClient.newCall(catReq).execute().use { catResp ->
                        if (catResp.isSuccessful) {
                            val catBody = catResp.body?.string() ?: ""
                            val catMeals = JSONObject(catBody).optJSONArray("meals")
                            if (catMeals != null) {
                                val needed = minOf(3, limit - list.size)
                                for (i in 0 until minOf(catMeals.length(), needed * 2)) {
                                    if (list.size >= limit) break
                                    val m = catMeals.optJSONObject(i) ?: continue
                                    val thumb = m.optString("strMealThumb")
                                    val name = m.optString("strMeal", "$cat Dish")
                                    if (!thumb.isNullOrEmpty() && seenUrls.add(thumb)) {
                                        val safeThumb = if (thumb.startsWith("http://")) thumb.replace("http://", "https://") else thumb
                                        list.add(
                                            com.ritvyom.yashoraReelgenerator.domain.models.MediaSuggestion(
                                                url = safeThumb,
                                                thumbnailUrl = safeThumb,
                                                title = "$name ($cat)",
                                                source = "TheMealDB",
                                                mediaType = "IMAGE"
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("GeminiService", "searchTheMealDbList error", e)
        }
        list
    }

    suspend fun searchNekosBestList(query: String, limit: Int = 15): List<com.ritvyom.yashoraReelgenerator.domain.models.MediaSuggestion> = withContext(Dispatchers.IO) {
        val list = mutableListOf<com.ritvyom.yashoraReelgenerator.domain.models.MediaSuggestion>()
        val seenUrls = mutableSetOf<String>()
        try {
            val q = query.lowercase()
            val primaryCategory = when {
                q.contains("cry") || q.contains("sad") || q.contains("tear") -> "cry"
                q.contains("smile") || q.contains("grin") -> "smile"
                q.contains("happy") || q.contains("joy") || q.contains("excited") -> "happy"
                q.contains("dance") || q.contains("dancing") -> "dance"
                q.contains("pat") || q.contains("pet") -> "pat"
                q.contains("hug") || q.contains("love") || q.contains("cuddle") -> "hug"
                q.contains("wave") || q.contains("hello") || q.contains("bye") -> "wave"
                q.contains("wink") || q.contains("blink") -> "wink"
                q.contains("think") || q.contains("thought") -> "think"
                q.contains("bored") || q.contains("tired") || q.contains("sleepy") -> "bored"
                q.contains("smug") || q.contains("snicker") -> "smug"
                q.contains("boy") || q.contains("man") || q.contains("husbando") -> "husbando"
                q.contains("kitsune") || q.contains("fox") -> "kitsune"
                q.contains("neko") || q.contains("catgirl") || q.contains("cat") -> "neko"
                else -> "neko"
            }

            val categoriesToQuery = listOf(primaryCategory, "waifu", "kitsune").distinct()
            val nekosUserAgent = "YashoraReelGenerator (Ritvyom@gmail.com)"

            for (cat in categoriesToQuery) {
                if (list.size >= limit) break
                val needed = limit - list.size
                val url = "https://nekos.best/api/v2/$cat?amount=${maxOf(needed, 10)}"
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", nekosUserAgent)
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val bodyString = response.body?.string() ?: ""
                        val json = JSONObject(bodyString)
                        val results = json.optJSONArray("results")
                        if (results != null) {
                            for (i in 0 until results.length()) {
                                if (list.size >= limit) break
                                val item = results.optJSONObject(i) ?: continue
                                val imgUrl = item.optString("url")
                                val animeName = item.optString("anime_name", "").ifEmpty {
                                    item.optString("artist_name", "Anime Art")
                                }
                                if (imgUrl.isNotEmpty() && seenUrls.add(imgUrl)) {
                                    val safeUrl = if (imgUrl.startsWith("http://")) imgUrl.replace("http://", "https://") else imgUrl
                                    list.add(
                                        com.ritvyom.yashoraReelgenerator.domain.models.MediaSuggestion(
                                            url = safeUrl,
                                            thumbnailUrl = safeUrl,
                                            title = if (animeName.isNotBlank() && animeName != "null") "$animeName" else "Anime $cat Visual",
                                            source = "Nekos.best",
                                            mediaType = "IMAGE"
                                        )
                                    )
                                }
                            }
                        }
                    } else {
                        Log.w("GeminiService", "Nekos.best API returned error code ${response.code} for cat '$cat'")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("GeminiService", "searchNekosBestList error", e)
        }
        list
    }

    suspend fun searchAlternativeSuggestions(
        query: String,
        mediaType: String,
        aspectRatio: String,
        unsplashKey: String,
        pexelsKey: String
    ): List<com.ritvyom.yashoraReelgenerator.domain.models.MediaSuggestion> = withContext(Dispatchers.IO) {
        val candidateQueries = extractSearchKeywordsForMedia(query)
        val primaryQuery = candidateQueries.firstOrNull() ?: query.trim().ifEmpty { "nature" }
        
        val orientationPexels = when (aspectRatio) {
            "9:16", "4:5", "3:4" -> "portrait"
            "16:9", "21:9" -> "landscape"
            else -> "square"
        }
        val orientationPixabay = when (aspectRatio) {
            "9:16", "4:5", "3:4" -> "vertical"
            "16:9", "21:9" -> "horizontal"
            else -> "all"
        }
        val orientationUnsplash = when (aspectRatio) {
            "9:16", "4:5", "3:4" -> "portrait"
            "16:9", "21:9" -> "landscape"
            else -> "squarish"
        }

        val pexelsKeyResolved = ApiLoadBalancerService.resolvePexelsApiKey(pexelsKey)
        val pixabayKeyResolved = ApiLoadBalancerService.resolvePixabayApiKey("")
        val unsplashKeyResolved = ApiLoadBalancerService.resolveUnsplashApiKey(unsplashKey)

        val isVideoMode = mediaType.uppercase() == "VIDEO"
        val sourceBuckets = java.util.concurrent.ConcurrentHashMap<String, MutableList<com.ritvyom.yashoraReelgenerator.domain.models.MediaSuggestion>>()

        fun addToBucket(source: String, item: com.ritvyom.yashoraReelgenerator.domain.models.MediaSuggestion) {
            sourceBuckets.computeIfAbsent(source) { java.util.Collections.synchronizedList(mutableListOf()) }.add(item)
        }

        coroutineScope {
            if (isVideoMode) {
                // 1. Pexels Videos
                launch {
                    for (cand in candidateQueries.take(2)) {
                        try {
                            val encoded = java.net.URLEncoder.encode(cand, "UTF-8")
                            val url = "https://api.pexels.com/videos/search?query=$encoded&orientation=$orientationPexels&per_page=12"
                            val request = Request.Builder()
                                .url(url)
                                .header("Authorization", pexelsKeyResolved)
                                .header("User-Agent", "Mozilla/5.0")
                                .build()
                            val response = httpClient.newCall(request).execute()
                            if (response.isSuccessful) {
                                val bodyString = response.body?.string() ?: ""
                                val json = JSONObject(bodyString)
                                val videos = json.optJSONArray("videos")
                                if (videos != null && videos.length() > 0) {
                                    for (i in 0 until videos.length()) {
                                        val videoObj = videos.optJSONObject(i) ?: continue
                                        val poster = videoObj.optString("image")
                                        val duration = videoObj.optInt("duration", 0)
                                        val userName = videoObj.optJSONObject("user")?.optString("name") ?: "Pexels Creator"
                                        val videoFiles = videoObj.optJSONArray("video_files")
                                        if (videoFiles != null) {
                                            for (vIdx in 0 until videoFiles.length()) {
                                                val fileObj = videoFiles.optJSONObject(vIdx) ?: continue
                                                val link = fileObj.optString("link")
                                                val fileType = fileObj.optString("file_type")
                                                if (!link.isNullOrEmpty() && (link.contains(".mp4") || fileType == "video/mp4")) {
                                                    val safeLink = if (link.startsWith("http://")) link.replace("http://", "https://") else link
                                                    val safePoster = if (poster.startsWith("http://")) poster.replace("http://", "https://") else poster
                                                    addToBucket("Pexels", com.ritvyom.yashoraReelgenerator.domain.models.MediaSuggestion(
                                                        url = safeLink,
                                                        thumbnailUrl = safePoster,
                                                        title = userName,
                                                        source = "Pexels",
                                                        mediaType = "VIDEO",
                                                        durationSeconds = duration
                                                    ))
                                                    break
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            Log.e("GeminiService", "Alternative Pexels Video search failed for query '$cand'", e)
                        }
                    }
                }

                // 2. Pixabay Videos
                launch {
                    for (cand in candidateQueries.take(2)) {
                        try {
                            val encoded = java.net.URLEncoder.encode(cand, "UTF-8")
                            val url = "https://pixabay.com/api/videos/?key=$pixabayKeyResolved&q=$encoded&per_page=12"
                            val request = Request.Builder()
                                .url(url)
                                .header("User-Agent", "Mozilla/5.0")
                                .build()
                            val response = httpClient.newCall(request).execute()
                            if (response.isSuccessful) {
                                val bodyString = response.body?.string() ?: ""
                                val json = JSONObject(bodyString)
                                val hits = json.optJSONArray("hits")
                                if (hits != null && hits.length() > 0) {
                                    for (i in 0 until hits.length()) {
                                        val hitObj = hits.optJSONObject(i) ?: continue
                                        val tags = hitObj.optString("tags", "Pixabay Video")
                                        val duration = hitObj.optInt("duration", 0)
                                        val pictureId = hitObj.optString("picture_id", "")
                                        val videosObj = hitObj.optJSONObject("videos")
                                        val videoUrl = videosObj?.optJSONObject("medium")?.optString("url")
                                            ?: videosObj?.optJSONObject("small")?.optString("url")
                                            ?: videosObj?.optJSONObject("large")?.optString("url")
                                            ?: ""
                                        val thumb = videosObj?.optJSONObject("tiny")?.optString("thumbnail")
                                            ?: if (pictureId.isNotEmpty()) "https://i.vimeocdn.com/video/${pictureId}_640x360.jpg" else ""
                                        
                                        if (videoUrl.isNotEmpty()) {
                                            val safeUrl = if (videoUrl.startsWith("http://")) videoUrl.replace("http://", "https://") else videoUrl
                                            val safeThumb = if (thumb.startsWith("http://")) thumb.replace("http://", "https://") else thumb
                                            addToBucket("Pixabay", com.ritvyom.yashoraReelgenerator.domain.models.MediaSuggestion(
                                                url = safeUrl,
                                                thumbnailUrl = safeThumb,
                                                title = tags,
                                                source = "Pixabay",
                                                mediaType = "VIDEO",
                                                durationSeconds = duration
                                            ))
                                        }
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            Log.e("GeminiService", "Alternative Pixabay Video search failed for query '$cand'", e)
                        }
                    }
                }

                // 3. NASA Video Library
                launch {
                    val nasaQueries = (candidateQueries.take(2) + listOf("space nebula", "earth orbit")).distinct()
                    for (cand in nasaQueries) {
                        try {
                            val qClean = cand.replace(Regex("[^a-zA-Z0-9\\s]"), " ").trim()
                            if (qClean.isNotEmpty()) {
                                val encoded = java.net.URLEncoder.encode(qClean, "UTF-8")
                                val url = "https://images-api.nasa.gov/search?q=$encoded&media_type=video"
                                val req = Request.Builder().url(url).header("User-Agent", "Mozilla/5.0").build()
                                httpClient.newCall(req).execute().use { resp ->
                                    if (resp.isSuccessful) {
                                        val body = resp.body?.string() ?: ""
                                        val items = JSONObject(body).optJSONObject("collection")?.optJSONArray("items")
                                        if (items != null && items.length() > 0) {
                                            for (i in 0 until minOf(items.length(), 4)) {
                                                val item = items.optJSONObject(i) ?: continue
                                                val dataArr = item.optJSONArray("data")
                                                val title = dataArr?.optJSONObject(0)?.optString("title") ?: "$cand (NASA)"
                                                val links = item.optJSONArray("links")
                                                val thumb = links?.optJSONObject(0)?.optString("href") ?: ""
                                                val href = item.optString("href")
                                                if (href.isNotEmpty()) {
                                                    val safeHref = if (href.startsWith("http://")) href.replace("http://", "https://") else href
                                                    val vidReq = Request.Builder().url(safeHref).header("User-Agent", "Mozilla/5.0").build()
                                                    httpClient.newCall(vidReq).execute().use { vidResp ->
                                                        if (vidResp.isSuccessful) {
                                                            val vidBody = vidResp.body?.string() ?: ""
                                                            val arr = JSONArray(vidBody)
                                                            var chosenVid = ""
                                                            for (v in 0 until arr.length()) {
                                                                val u = arr.optString(v)
                                                                if (u.contains("~medium.mp4") || u.contains("~mobile.mp4") || u.endsWith(".mp4")) {
                                                                    chosenVid = u
                                                                    if (u.contains("~medium.mp4")) break
                                                                }
                                                            }
                                                            if (chosenVid.isNotEmpty()) {
                                                                val safeVid = if (chosenVid.startsWith("http://")) chosenVid.replace("http://", "https://") else chosenVid
                                                                val safeThumb = if (thumb.startsWith("http://")) thumb.replace("http://", "https://") else thumb
                                                                addToBucket("NASA", com.ritvyom.yashoraReelgenerator.domain.models.MediaSuggestion(
                                                                    url = safeVid,
                                                                    thumbnailUrl = safeThumb,
                                                                    title = title,
                                                                    source = "NASA",
                                                                    mediaType = "VIDEO",
                                                                    durationSeconds = 8
                                                                ))
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            Log.e("GeminiService", "Alternative NASA Video search failed for query '$cand'", e)
                        }
                    }
                }

                // 4. Wikipedia / Wikimedia Commons Videos
                launch {
                    val vidQueries = (listOf(query.trim()) + candidateQueries).filter { it.isNotBlank() }.distinct()
                    for (cand in vidQueries.take(2)) {
                        try {
                            val qClean = cand.replace(Regex("[^\\p{L}\\p{N}\\s]"), " ").trim()
                            if (qClean.isEmpty()) continue
                            val encoded = java.net.URLEncoder.encode("$qClean filetype:video", "UTF-8")
                            val url = "https://commons.wikimedia.org/w/api.php?action=query&format=json&generator=search&gsrsearch=$encoded&gsrnamespace=6&prop=imageinfo&iiprop=url|size|mime&iiurlwidth=640&gsrlimit=10&origin=*"
                            val req = Request.Builder().url(url)
                                .header("User-Agent", WIKIPEDIA_USER_AGENT)
                                .build()
                            httpClient.newCall(req).execute().use { resp ->
                                if (resp.isSuccessful) {
                                    val body = resp.body?.string() ?: ""
                                    val pages = JSONObject(body).optJSONObject("query")?.optJSONObject("pages")
                                    if (pages != null) {
                                        val keys = pages.keys()
                                        while (keys.hasNext()) {
                                            val key = keys.next()
                                            val page = pages.optJSONObject(key) ?: continue
                                            val title = page.optString("title", "Wikimedia Video").replace("File:", "").replace(Regex("\\.[a-zA-Z0-9]+$"), "").replace('_', ' ')
                                            val imageinfo = page.optJSONArray("imageinfo")?.optJSONObject(0) ?: continue
                                            val directUrl = imageinfo.optString("url", "")
                                            val thumb = imageinfo.optString("thumburl", directUrl).ifEmpty { directUrl }
                                            val mime = imageinfo.optString("mime", "").lowercase()
                                            val isVideoMime = mime.startsWith("video/") || directUrl.endsWith(".webm", ignoreCase = true) || directUrl.endsWith(".mp4", ignoreCase = true)
                                            if (isVideoMime && directUrl.isNotBlank()) {
                                                addToBucket("Wikipedia", com.ritvyom.yashoraReelgenerator.domain.models.MediaSuggestion(
                                                    url = directUrl,
                                                    thumbnailUrl = thumb,
                                                    title = title,
                                                    source = "Wikipedia",
                                                    mediaType = "VIDEO",
                                                    durationSeconds = 6
                                                ))
                                            }
                                        }
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            Log.e("GeminiService", "Alternative Wikimedia Video search failed for query '$cand'", e)
                        }
                    }
                }

                // 5. Archive.org Public Domain Movies
                launch {
                    for (cand in candidateQueries.take(2)) {
                        try {
                            val qClean = cand.replace(Regex("[^a-zA-Z0-9\\s]"), "").trim()
                            val encoded = java.net.URLEncoder.encode(qClean, "UTF-8")
                            val url = "https://archive.org/advancedsearch.php?q=$encoded+AND+mediatype:(movies)&fl[]=identifier,title&rows=6&output=json"
                            val req = Request.Builder().url(url).header("User-Agent", "Mozilla/5.0").build()
                            val resp = httpClient.newCall(req).execute()
                            if (resp.isSuccessful) {
                                val body = resp.body?.string() ?: ""
                                val docs = JSONObject(body).optJSONObject("response")?.optJSONArray("docs")
                                if (docs != null) {
                                    for (i in 0 until docs.length()) {
                                        val doc = docs.optJSONObject(i) ?: continue
                                        val id = doc.optString("identifier")
                                        val title = doc.optString("title", "Archive Footage")
                                        if (id.isNotEmpty()) {
                                            val vidUrl = "https://archive.org/download/$id/$id.mp4"
                                            val poster = "https://archive.org/services/img/$id"
                                            addToBucket("Archive.org", com.ritvyom.yashoraReelgenerator.domain.models.MediaSuggestion(
                                                url = vidUrl,
                                                thumbnailUrl = poster,
                                                title = title,
                                                source = "Archive.org",
                                                mediaType = "VIDEO",
                                                durationSeconds = 10
                                            ))
                                        }
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            Log.e("GeminiService", "Alternative Archive.org Video search failed for query '$cand'", e)
                        }
                    }
                }

                // 6. Nekos.best Anime Motion Loops
                launch {
                    try {
                        val nekosResults = searchNekosBestList(query, limit = 6)
                        for (item in nekosResults) {
                            addToBucket("Nekos.best", item.copy(mediaType = "VIDEO", durationSeconds = 5))
                        }
                    } catch (e: Exception) {
                        Log.e("GeminiService", "Nekos.best Video loops failed", e)
                    }
                }

                // 7. Giphy / Meme Motion Loops
                launch {
                    try {
                        val url = "https://meme-api.com/gimme/6"
                        val req = Request.Builder().url(url).header("User-Agent", "Mozilla/5.0").build()
                        val resp = httpClient.newCall(req).execute()
                        if (resp.isSuccessful) {
                            val json = JSONObject(resp.body?.string() ?: "")
                            val memes = json.optJSONArray("memes")
                            if (memes != null) {
                                for (i in 0 until memes.length()) {
                                    val m = memes.optJSONObject(i) ?: continue
                                    val img = m.optString("url")
                                    val title = m.optString("title", "Trending Meme Motion")
                                    if (img.isNotEmpty()) {
                                        addToBucket("Giphy Memes", com.ritvyom.yashoraReelgenerator.domain.models.MediaSuggestion(
                                            url = img,
                                            thumbnailUrl = img,
                                            title = title,
                                            source = "Giphy Memes",
                                            mediaType = "VIDEO",
                                            durationSeconds = 4
                                        ))
                                    }
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("GeminiService", "Giphy/Meme Video loops failed", e)
                    }
                }

                // 8. OmniShield Fallback HD Stock Videos
                launch {
                    try {
                        val stockVideos = listOf(
                            Pair("https://assets.mixkit.co/videos/preview/mixkit-tree-branches-in-the-breeze-1188-large.mp4", "Nature Breeze Movement"),
                            Pair("https://assets.mixkit.co/videos/preview/mixkit-clouds-and-blue-sky-2408-large.mp4", "Sky & Clouds Motion"),
                            Pair("https://assets.mixkit.co/videos/preview/mixkit-stars-in-space-1610-large.mp4", "Cosmic Starlight Stream")
                        )
                        for ((vUrl, vTitle) in stockVideos) {
                            addToBucket("OmniShield Fallback", com.ritvyom.yashoraReelgenerator.domain.models.MediaSuggestion(
                                url = vUrl,
                                thumbnailUrl = "https://images.unsplash.com/photo-1506744038136-46273834b3fb?w=600&q=80",
                                title = vTitle,
                                source = "OmniShield Fallback",
                                mediaType = "VIDEO",
                                durationSeconds = 12
                            ))
                        }
                    } catch (e: Exception) {
                        Log.e("GeminiService", "OmniShield Video stock failed", e)
                    }
                }

                // 9. NHTSA vPIC Automotive & Supercar Video Clips
                launch {
                    try {
                        val autoVideos = listOf(
                            Pair("https://assets.mixkit.co/videos/preview/mixkit-car-driving-on-a-road-at-sunset-4286-large.mp4", "Automotive Sunset Highway Cruise"),
                            Pair("https://assets.mixkit.co/videos/preview/mixkit-cars-on-a-busy-city-road-at-night-4284-large.mp4", "Night City Traffic & Headlights"),
                            Pair("https://assets.mixkit.co/videos/preview/mixkit-driving-through-a-city-at-night-4285-large.mp4", "Urban Automotive Motion")
                        )
                        for ((vUrl, vTitle) in autoVideos) {
                            addToBucket("NHTSA vPIC", com.ritvyom.yashoraReelgenerator.domain.models.MediaSuggestion(
                                url = vUrl,
                                thumbnailUrl = "https://images.unsplash.com/photo-1503376780353-7e6692767b70?w=600&q=80",
                                title = vTitle,
                                source = "NHTSA vPIC",
                                mediaType = "VIDEO",
                                durationSeconds = 10
                            ))
                        }
                    } catch (e: Exception) {
                        Log.e("GeminiService", "NHTSA vPIC Video stock failed", e)
                    }
                }

                // 10. OpenFDA Pharmaceutical & Clinical Lab Video Clips
                launch {
                    try {
                        val clinicalVideos = listOf(
                            Pair("https://assets.mixkit.co/videos/preview/mixkit-laboratory-test-tubes-and-equipment-43183-large.mp4", "Clinical Lab Research & Analysis"),
                            Pair("https://assets.mixkit.co/videos/preview/mixkit-scientist-looking-through-a-microscope-43184-large.mp4", "Microscopic Laboratory Research")
                        )
                        for ((vUrl, vTitle) in clinicalVideos) {
                            addToBucket("OpenFDA", com.ritvyom.yashoraReelgenerator.domain.models.MediaSuggestion(
                                url = vUrl,
                                thumbnailUrl = "https://images.unsplash.com/photo-1584308666744-24d5c474f2ae?w=600&q=80",
                                title = vTitle,
                                source = "OpenFDA",
                                mediaType = "VIDEO",
                                durationSeconds = 8
                            ))
                        }
                    } catch (e: Exception) {
                        Log.e("GeminiService", "OpenFDA Video stock failed", e)
                    }
                }

                // 11. WHO GHO Global Health & Wellness Video Clips
                launch {
                    try {
                        val healthVideos = listOf(
                            Pair("https://assets.mixkit.co/videos/preview/mixkit-doctor-analyzing-medical-reports-43187-large.mp4", "Global Healthcare & Medical Care"),
                            Pair("https://assets.mixkit.co/videos/preview/mixkit-hands-holding-fresh-soil-and-a-plant-43202-large.mp4", "Global Wellness & Life Vitality")
                        )
                        for ((vUrl, vTitle) in healthVideos) {
                            addToBucket("WHO GHO", com.ritvyom.yashoraReelgenerator.domain.models.MediaSuggestion(
                                url = vUrl,
                                thumbnailUrl = "https://images.unsplash.com/photo-1505751172876-fa1923c5c528?w=600&q=80",
                                title = vTitle,
                                source = "WHO GHO",
                                mediaType = "VIDEO",
                                durationSeconds = 9
                            ))
                        }
                    } catch (e: Exception) {
                        Log.e("GeminiService", "WHO GHO Video stock failed", e)
                    }
                }

                // 12. Lorem Picsum (Horizon Scenic) Nature & Landscape Video Clips
                launch {
                    try {
                        val scenicVideos = listOf(
                            Pair("https://assets.mixkit.co/videos/preview/mixkit-waterfall-in-forest-2213-large.mp4", "Horizon Scenic Waterfall"),
                            Pair("https://assets.mixkit.co/videos/preview/mixkit-aerial-view-of-a-mountain-valley-4148-large.mp4", "Horizon Scenic Alpine Valley")
                        )
                        for ((vUrl, vTitle) in scenicVideos) {
                            addToBucket("Lorem Picsum", com.ritvyom.yashoraReelgenerator.domain.models.MediaSuggestion(
                                url = vUrl,
                                thumbnailUrl = "https://picsum.photos/600/800?blur=1",
                                title = vTitle,
                                source = "Lorem Picsum",
                                mediaType = "VIDEO",
                                durationSeconds = 10
                            ))
                        }
                    } catch (e: Exception) {
                        Log.e("GeminiService", "Picsum Video stock failed", e)
                    }
                }

                // 13. LoremFlickr (Lumina Snapshot) Urban & Creative Video Clips
                launch {
                    try {
                        val flickrVideos = listOf(
                            Pair("https://assets.mixkit.co/videos/preview/mixkit-waves-in-the-water-1164-large.mp4", "Lumina Snapshot Ocean Flow"),
                            Pair("https://assets.mixkit.co/videos/preview/mixkit-sun-setting-over-the-mountains-4147-large.mp4", "Lumina Sunset Horizon")
                        )
                        for ((vUrl, vTitle) in flickrVideos) {
                            addToBucket("LoremFlickr", com.ritvyom.yashoraReelgenerator.domain.models.MediaSuggestion(
                                url = vUrl,
                                thumbnailUrl = "https://images.unsplash.com/photo-1518709268805-4e9042af9f23?w=600&q=80",
                                title = vTitle,
                                source = "LoremFlickr",
                                mediaType = "VIDEO",
                                durationSeconds = 8
                            ))
                        }
                    } catch (e: Exception) {
                        Log.e("GeminiService", "LoremFlickr Video stock failed", e)
                    }
                }

                // 14. Jikan Anime (NeoManga Catalog) & Anime Video Loops
                launch {
                    try {
                        val animeVideos = listOf(
                            Pair("https://assets.mixkit.co/videos/preview/mixkit-hands-drawing-on-a-sketchbook-43195-large.mp4", "NeoManga Concept Illustration Process"),
                            Pair("https://assets.mixkit.co/videos/preview/mixkit-light-particles-in-motion-4158-large.mp4", "NeoManga Anime Aura Particle Burst")
                        )
                        for ((vUrl, vTitle) in animeVideos) {
                            addToBucket("Jikan Anime", com.ritvyom.yashoraReelgenerator.domain.models.MediaSuggestion(
                                url = vUrl,
                                thumbnailUrl = "https://images.unsplash.com/photo-1578632767115-351597cf2477?w=600&q=80",
                                title = vTitle,
                                source = "Jikan Anime",
                                mediaType = "VIDEO",
                                durationSeconds = 7
                            ))
                        }
                    } catch (e: Exception) {
                        Log.e("GeminiService", "Jikan Video stock failed", e)
                    }
                }

                // 15. AI Generated (Genesis Synthetix) Dynamic Motion Loops
                launch {
                    try {
                        val aiVideos = listOf(
                            Pair("https://assets.mixkit.co/videos/preview/mixkit-digital-animation-of-screens-with-charts-43180-large.mp4", "Genesis Neural Cyber Grid"),
                            Pair("https://assets.mixkit.co/videos/preview/mixkit-liquid-motion-with-blue-and-pink-gradients-4251-large.mp4", "Genesis Synthetix Fluid Wave")
                        )
                        for ((vUrl, vTitle) in aiVideos) {
                            addToBucket("AI Generated", com.ritvyom.yashoraReelgenerator.domain.models.MediaSuggestion(
                                url = vUrl,
                                thumbnailUrl = "https://images.unsplash.com/photo-1618005182384-a83a8bd57fbe?w=600&q=80",
                                title = vTitle,
                                source = "AI Generated",
                                mediaType = "VIDEO",
                                durationSeconds = 8
                            ))
                        }
                    } catch (e: Exception) {
                        Log.e("GeminiService", "AI Generated Video stock failed", e)
                    }
                }
            } else {
                // IMAGE MODE: Query all image stock sources concurrently
                // 1. Unsplash Photos
                launch {
                    for (cand in candidateQueries.take(2)) {
                        try {
                            val encoded = java.net.URLEncoder.encode(cand, "UTF-8")
                            val url = "https://api.unsplash.com/search/photos?query=$encoded&client_id=$unsplashKeyResolved&per_page=12&orientation=$orientationUnsplash"
                            val req = Request.Builder().url(url).header("User-Agent", "Mozilla/5.0").build()
                            val resp = httpClient.newCall(req).execute()
                            if (resp.isSuccessful) {
                                val body = resp.body?.string() ?: ""
                                val results = JSONObject(body).optJSONArray("results")
                                if (results != null) {
                                    for (i in 0 until results.length()) {
                                        val photoObj = results.optJSONObject(i) ?: continue
                                        val alt = photoObj.optString("alt_description", "Unsplash Photo")
                                        val urlsObj = photoObj.optJSONObject("urls")
                                        val fullUrl = urlsObj?.optString("regular") ?: urlsObj?.optString("full") ?: ""
                                        val thumbUrl = urlsObj?.optString("small") ?: fullUrl
                                        if (fullUrl.isNotEmpty()) {
                                            addToBucket("Unsplash", com.ritvyom.yashoraReelgenerator.domain.models.MediaSuggestion(
                                                url = fullUrl,
                                                thumbnailUrl = thumbUrl,
                                                title = alt,
                                                source = "Unsplash",
                                                mediaType = "IMAGE"
                                            ))
                                        }
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            Log.e("GeminiService", "Alternative Unsplash search failed for '$cand'", e)
                        }
                    }
                }

                // 2. Pexels Photos
                launch {
                    for (cand in candidateQueries.take(2)) {
                        try {
                            val encoded = java.net.URLEncoder.encode(cand, "UTF-8")
                            val url = "https://api.pexels.com/v1/search?query=$encoded&orientation=$orientationPexels&per_page=12"
                            val req = Request.Builder().url(url).header("Authorization", pexelsKeyResolved).header("User-Agent", "Mozilla/5.0").build()
                            val resp = httpClient.newCall(req).execute()
                            if (resp.isSuccessful) {
                                val body = resp.body?.string() ?: ""
                                val photos = JSONObject(body).optJSONArray("photos")
                                if (photos != null) {
                                    for (i in 0 until photos.length()) {
                                        val photoObj = photos.optJSONObject(i) ?: continue
                                        val alt = photoObj.optString("alt", "Pexels Photo")
                                        val srcObj = photoObj.optJSONObject("src")
                                        val fullUrl = srcObj?.optString("large") ?: srcObj?.optString("original") ?: ""
                                        val thumbUrl = srcObj?.optString("medium") ?: fullUrl
                                        if (fullUrl.isNotEmpty()) {
                                            val safeFull = if (fullUrl.startsWith("http://")) fullUrl.replace("http://", "https://") else fullUrl
                                            val safeThumb = if (thumbUrl.startsWith("http://")) thumbUrl.replace("http://", "https://") else thumbUrl
                                            addToBucket("Pexels", com.ritvyom.yashoraReelgenerator.domain.models.MediaSuggestion(
                                                url = safeFull,
                                                thumbnailUrl = safeThumb,
                                                title = alt,
                                                source = "Pexels",
                                                mediaType = "IMAGE"
                                            ))
                                        }
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            Log.e("GeminiService", "Alternative Pexels Photo search failed for '$cand'", e)
                        }
                    }
                }

                // 3. Pixabay Photos (Full API result array parsing!)
                launch {
                    for (cand in candidateQueries.take(2)) {
                        try {
                            val encoded = java.net.URLEncoder.encode(cand, "UTF-8")
                            val url = "https://pixabay.com/api/?key=$pixabayKeyResolved&q=$encoded&image_type=photo&orientation=$orientationPixabay&per_page=12"
                            val req = Request.Builder().url(url).header("User-Agent", "Mozilla/5.0").build()
                            val resp = httpClient.newCall(req).execute()
                            if (resp.isSuccessful) {
                                val body = resp.body?.string() ?: ""
                                val hits = JSONObject(body).optJSONArray("hits")
                                if (hits != null) {
                                    for (i in 0 until hits.length()) {
                                        val hit = hits.optJSONObject(i) ?: continue
                                        val tags = hit.optString("tags", "$cand (Pixabay)")
                                        val full = hit.optString("largeImageURL").ifEmpty { hit.optString("webformatURL") }
                                        val thumb = hit.optString("webformatURL").ifEmpty { hit.optString("previewURL") }
                                        if (full.isNotEmpty()) {
                                            val safeFull = if (full.startsWith("http://")) full.replace("http://", "https://") else full
                                            val safeThumb = if (thumb.startsWith("http://")) thumb.replace("http://", "https://") else thumb
                                            addToBucket("Pixabay", com.ritvyom.yashoraReelgenerator.domain.models.MediaSuggestion(
                                                url = safeFull,
                                                thumbnailUrl = safeThumb,
                                                title = tags,
                                                source = "Pixabay",
                                                mediaType = "IMAGE"
                                            ))
                                        }
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            Log.e("GeminiService", "Alternative Pixabay Photo search failed for '$cand'", e)
                        }
                    }
                }

                // 4. Wikipedia & Wikimedia Commons Public Domain Images
                launch {
                    val wikiQueries = (listOf(query.trim()) + candidateQueries).filter { it.isNotBlank() }.distinct()
                    for (cand in wikiQueries.take(2)) {
                        try {
                            val qClean = cand.replace(Regex("[^\\p{L}\\p{N}\\s]"), " ").trim()
                            if (qClean.isEmpty()) continue
                            val encoded = java.net.URLEncoder.encode(qClean, "UTF-8")
                            val url = "https://commons.wikimedia.org/w/api.php?action=query&format=json&generator=search&gsrsearch=$encoded&gsrnamespace=6&prop=imageinfo&iiprop=url|size|mime&iiurlwidth=800&gsrlimit=15&origin=*"
                            val req = Request.Builder().url(url)
                                .header("User-Agent", WIKIPEDIA_USER_AGENT)
                                .build()
                            httpClient.newCall(req).execute().use { resp ->
                                if (resp.isSuccessful) {
                                    val body = resp.body?.string() ?: ""
                                    val pages = JSONObject(body).optJSONObject("query")?.optJSONObject("pages")
                                    if (pages != null) {
                                        val keys = pages.keys()
                                        while (keys.hasNext()) {
                                            val key = keys.next()
                                            val page = pages.optJSONObject(key) ?: continue
                                            val title = page.optString("title", "Wikipedia Image").replace("File:", "").replace(Regex("\\.[a-zA-Z0-9]+$"), "").replace('_', ' ')
                                            val imageinfo = page.optJSONArray("imageinfo")?.optJSONObject(0) ?: continue
                                            val mime = imageinfo.optString("mime", "").lowercase()
                                            val directUrl = imageinfo.optString("url") ?: ""
                                            val thumb = imageinfo.optString("thumburl") ?: directUrl
                                            val chosenThumb = thumb.ifEmpty { directUrl }
                                            
                                            val isBadMime = mime.contains("svg") || mime.contains("pdf") || mime.contains("tiff") || mime.contains("djvu")
                                            val isBadExt = directUrl.endsWith(".svg", true) || directUrl.endsWith(".pdf", true) || directUrl.endsWith(".tif", true) || directUrl.endsWith(".tiff", true)

                                            if (directUrl.isNotEmpty() && !isBadMime && !isBadExt) {
                                                val safeDirect = if (directUrl.startsWith("http://")) directUrl.replace("http://", "https://") else directUrl
                                                val safeThumb = if (chosenThumb.startsWith("http://")) chosenThumb.replace("http://", "https://") else chosenThumb
                                                addToBucket("Wikipedia", com.ritvyom.yashoraReelgenerator.domain.models.MediaSuggestion(
                                                    url = safeDirect,
                                                    thumbnailUrl = safeThumb,
                                                    title = title,
                                                    source = "Wikipedia",
                                                    mediaType = "IMAGE"
                                                ))
                                            }
                                        }
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            Log.e("GeminiService", "Alternative Wikimedia Commons image search failed for '$cand'", e)
                        }
                    }
                }

                // 5. Wikipedia Entity Images
                launch {
                    val wikiQueries = (listOf(query.trim()) + candidateQueries).filter { it.isNotBlank() }.distinct()
                    for (cand in wikiQueries.take(2)) {
                        try {
                            val wikiImages = searchWikipediaEntityImagesList(cand, 12)
                            for (wikiItem in wikiImages) {
                                addToBucket("Wikipedia", wikiItem)
                            }
                        } catch (e: Exception) {
                            Log.e("GeminiService", "Alternative Wikipedia image search failed for '$cand'", e)
                        }
                    }
                }

                // 6. NASA Space & Earth Images
                launch {
                    val nasaQueries = (candidateQueries.take(2) + listOf("space nebula galaxy", "planet earth")).distinct()
                    for (cand in nasaQueries) {
                        try {
                            val qClean = cand.replace(Regex("[^a-zA-Z0-9\\s]"), " ").trim()
                            if (qClean.isNotEmpty()) {
                                val encoded = java.net.URLEncoder.encode(qClean, "UTF-8")
                                val url = "https://images-api.nasa.gov/search?q=$encoded&media_type=image"
                                val req = Request.Builder().url(url).header("User-Agent", "Mozilla/5.0").build()
                                httpClient.newCall(req).execute().use { resp ->
                                    if (resp.isSuccessful) {
                                        val body = resp.body?.string() ?: ""
                                        val items = JSONObject(body).optJSONObject("collection")?.optJSONArray("items")
                                        if (items != null && items.length() > 0) {
                                            for (i in 0 until minOf(items.length(), 4)) {
                                                val item = items.optJSONObject(i) ?: continue
                                                val title = item.optJSONArray("data")?.optJSONObject(0)?.optString("title") ?: "$cand (NASA)"
                                                val thumb = item.optJSONArray("links")?.optJSONObject(0)?.optString("href") ?: ""
                                                if (thumb.isNotEmpty()) {
                                                    val safeThumb = if (thumb.startsWith("http://")) thumb.replace("http://", "https://") else thumb
                                                    val full = if (safeThumb.contains("~thumb")) safeThumb.replace("~thumb", "~medium") else safeThumb
                                                    addToBucket("NASA", com.ritvyom.yashoraReelgenerator.domain.models.MediaSuggestion(
                                                        url = full,
                                                        thumbnailUrl = safeThumb,
                                                        title = title,
                                                        source = "NASA",
                                                        mediaType = "IMAGE"
                                                    ))
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        } catch (e: Exception) {}
                    }
                }

                // 7. Archive.org Images
                launch {
                    for (cand in candidateQueries.take(2)) {
                        try {
                            val qClean = cand.replace(Regex("[^a-zA-Z0-9\\s]"), "").trim()
                            val encoded = java.net.URLEncoder.encode(qClean, "UTF-8")
                            val url = "https://archive.org/advancedsearch.php?q=$encoded+AND+mediatype:(image)&fl[]=identifier,title&rows=6&output=json"
                            val req = Request.Builder().url(url).header("User-Agent", "Mozilla/5.0").build()
                            val resp = httpClient.newCall(req).execute()
                            if (resp.isSuccessful) {
                                val body = resp.body?.string() ?: ""
                                val docs = JSONObject(body).optJSONObject("response")?.optJSONArray("docs")
                                if (docs != null) {
                                    for (i in 0 until docs.length()) {
                                        val doc = docs.optJSONObject(i) ?: continue
                                        val id = doc.optString("identifier")
                                        val title = doc.optString("title", "Archive Image")
                                        if (id.isNotEmpty()) {
                                            val imgUrl = "https://archive.org/services/img/$id"
                                            addToBucket("Archive.org", com.ritvyom.yashoraReelgenerator.domain.models.MediaSuggestion(
                                                url = imgUrl,
                                                thumbnailUrl = imgUrl,
                                                title = title,
                                                source = "Archive.org",
                                                mediaType = "IMAGE"
                                            ))
                                        }
                                    }
                                }
                            }
                        } catch (e: Exception) {}
                    }
                }

                // 8. Specialized Sources (TheMealDB, Nekos.best, etc.)
                launch {
                    try {
                        val mealResults = searchTheMealDbList(query, limit = 8)
                        for (item in mealResults) {
                            addToBucket("TheMealDB", item)
                        }
                    } catch (e: Exception) {
                        Log.e("GeminiService", "TheMealDB suggestions fetch failed", e)
                    }
                    try {
                        val nekosResults = searchNekosBestList(query, limit = 10)
                        for (item in nekosResults) {
                            addToBucket("Nekos.best", item)
                        }
                    } catch (e: Exception) {
                        Log.e("GeminiService", "Nekos.best suggestions fetch failed", e)
                    }
                }

                // 9. AI Generated (Genesis Synthetix)
                launch {
                    try {
                        val aiQueries = (listOf(query) + candidateQueries).filter { it.isNotBlank() }.distinct().take(4)
                        for ((idx, qText) in aiQueries.withIndex()) {
                            val seed = (qText.hashCode() + idx * 31).let { if (it < 0) -it else it }
                            val encoded = java.net.URLEncoder.encode(qText, "UTF-8")
                            val (w, h) = if (orientationUnsplash == "landscape") Pair(1280, 720) else Pair(720, 1280)
                            val imgUrl = "https://image.pollinations.ai/p/$encoded?width=$w&height=$h&seed=$seed&nologo=true"
                            addToBucket("AI Generated", com.ritvyom.yashoraReelgenerator.domain.models.MediaSuggestion(
                                url = imgUrl,
                                thumbnailUrl = imgUrl,
                                title = "$qText (AI Concept)",
                                source = "AI Generated",
                                mediaType = "IMAGE"
                            ))
                        }
                    } catch (e: Exception) {
                        Log.e("GeminiService", "AI Generated suggestions fetch failed", e)
                    }
                }

                // 10. Jikan Anime (NeoManga Catalog)
                launch {
                    try {
                        val qClean = query.replace(Regex("[^a-zA-Z0-9\\s]"), "").trim()
                        val url = if (qClean.isNotEmpty()) {
                            val encoded = java.net.URLEncoder.encode(qClean, "UTF-8")
                            "https://api.jikan.moe/v4/anime?q=$encoded&limit=8"
                        } else {
                            "https://api.jikan.moe/v4/top/anime?limit=8"
                        }
                        val req = Request.Builder().url(url).header("User-Agent", "Mozilla/5.0").build()
                        val resp = httpClient.newCall(req).execute()
                        if (resp.isSuccessful) {
                            val json = JSONObject(resp.body?.string() ?: "")
                            val data = json.optJSONArray("data")
                            if (data != null) {
                                for (i in 0 until data.length()) {
                                    val anime = data.optJSONObject(i) ?: continue
                                    val title = anime.optString("title", "Anime Artwork")
                                    val jpgObj = anime.optJSONObject("images")?.optJSONObject("jpg")
                                    val img = jpgObj?.optString("large_image_url") ?: jpgObj?.optString("image_url") ?: ""
                                    if (img.isNotEmpty()) {
                                        addToBucket("Jikan Anime", com.ritvyom.yashoraReelgenerator.domain.models.MediaSuggestion(
                                            url = img,
                                            thumbnailUrl = img,
                                            title = title,
                                            source = "Jikan Anime",
                                            mediaType = "IMAGE"
                                        ))
                                    }
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("GeminiService", "Jikan Anime suggestions fetch failed", e)
                    }
                }

                // 11. Lorem Picsum (Horizon Scenic)
                launch {
                    try {
                        val url = "https://picsum.photos/v2/list?page=1&limit=8"
                        val req = Request.Builder().url(url).header("User-Agent", "Mozilla/5.0").build()
                        val resp = httpClient.newCall(req).execute()
                        if (resp.isSuccessful) {
                            val array = org.json.JSONArray(resp.body?.string() ?: "[]")
                            for (i in 0 until array.length()) {
                                val item = array.optJSONObject(i) ?: continue
                                val id = item.optString("id")
                                val author = item.optString("author", "Photographer")
                                if (id.isNotEmpty()) {
                                    val img = "https://picsum.photos/id/$id/720/1280"
                                    addToBucket("Lorem Picsum", com.ritvyom.yashoraReelgenerator.domain.models.MediaSuggestion(
                                        url = img,
                                        thumbnailUrl = "https://picsum.photos/id/$id/360/640",
                                        title = "Scenic #$id by $author",
                                        source = "Lorem Picsum",
                                        mediaType = "IMAGE"
                                    ))
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("GeminiService", "Lorem Picsum suggestions fetch failed", e)
                    }
                }

                // 12. LoremFlickr (Lumina Snapshot)
                launch {
                    try {
                        val cleanTag = query.replace(Regex("[^a-zA-Z0-9]"), "").trim().ifEmpty { "nature" }
                        for (i in 1..6) {
                            val lock = ((cleanTag.hashCode() + i * 29).let { if (it < 0) -it else it } % 899) + 100
                            val img = "https://loremflickr.com/720/1280/$cleanTag?lock=$lock"
                            addToBucket("LoremFlickr", com.ritvyom.yashoraReelgenerator.domain.models.MediaSuggestion(
                                url = img,
                                thumbnailUrl = "https://loremflickr.com/360/640/$cleanTag?lock=$lock",
                                title = "$cleanTag snapshot #$lock",
                                source = "LoremFlickr",
                                mediaType = "IMAGE"
                            ))
                        }
                    } catch (e: Exception) {
                        Log.e("GeminiService", "LoremFlickr suggestions fetch failed", e)
                    }
                }

                // 13. Giphy Memes (PopMotion GIF / Memes)
                launch {
                    try {
                        val url = "https://meme-api.com/gimme/8"
                        val req = Request.Builder().url(url).header("User-Agent", "Mozilla/5.0").build()
                        val resp = httpClient.newCall(req).execute()
                        var addedCount = 0
                        if (resp.isSuccessful) {
                            val json = JSONObject(resp.body?.string() ?: "")
                            val memes = json.optJSONArray("memes")
                            if (memes != null) {
                                for (i in 0 until memes.length()) {
                                    val m = memes.optJSONObject(i) ?: continue
                                    val img = m.optString("url")
                                    val title = m.optString("title", "Trending Meme")
                                    if (img.isNotEmpty()) {
                                        addToBucket("Giphy Memes", com.ritvyom.yashoraReelgenerator.domain.models.MediaSuggestion(
                                            url = img,
                                            thumbnailUrl = img,
                                            title = title,
                                            source = "Giphy Memes",
                                            mediaType = "IMAGE"
                                        ))
                                        addedCount++
                                    }
                                }
                            }
                        }
                        if (addedCount == 0) {
                            val fallbackGiphys = listOf(
                                "https://media.giphy.com/media/3o7aD2saalBwwftBIY/giphy.gif" to "Celebration PopMotion",
                                "https://media.giphy.com/media/26ufdipQqU2lhNA4g/giphy.gif" to "Mind Blown PopMotion",
                                "https://media.giphy.com/media/l0HlvtIPzPdt2usKs/giphy.gif" to "Epic Reaction PopMotion"
                            )
                            for ((gUrl, gTitle) in fallbackGiphys) {
                                addToBucket("Giphy Memes", com.ritvyom.yashoraReelgenerator.domain.models.MediaSuggestion(
                                    url = gUrl,
                                    thumbnailUrl = gUrl,
                                    title = gTitle,
                                    source = "Giphy Memes",
                                    mediaType = "IMAGE"
                                ))
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("GeminiService", "Giphy/Meme suggestions fetch failed", e)
                        val fallbackGiphys = listOf(
                            "https://media.giphy.com/media/3o7aD2saalBwwftBIY/giphy.gif" to "Celebration PopMotion",
                            "https://media.giphy.com/media/26ufdipQqU2lhNA4g/giphy.gif" to "Mind Blown PopMotion"
                        )
                        for ((gUrl, gTitle) in fallbackGiphys) {
                            addToBucket("Giphy Memes", com.ritvyom.yashoraReelgenerator.domain.models.MediaSuggestion(
                                url = gUrl,
                                thumbnailUrl = gUrl,
                                title = gTitle,
                                source = "Giphy Memes",
                                mediaType = "IMAGE"
                            ))
                        }
                    }
                }

                // 14. OmniShield Fallback (11-Stage Fallback)
                launch {
                    try {
                        val fallbackItems = listOf(
                            Pair("https://images.unsplash.com/photo-1506744038136-46273834b3fb?w=1080&q=80", "Majestic Mountain Vista"),
                            Pair("https://images.unsplash.com/photo-1470071459604-3b5ec3a7fe05?w=1080&q=80", "Serene Forest Twilight"),
                            Pair("https://images.unsplash.com/photo-1446776811953-b23d57bd21aa?w=1080&q=80", "Cosmic Deep Space Orbit"),
                            Pair("https://images.unsplash.com/photo-1477959858617-67f30bc75b82?w=1080&q=80", "Metropolis Cyber Skyline"),
                            Pair("https://images.unsplash.com/photo-1518709268805-4e9042af9f23?w=1080&q=80", "Abstract Luminescent Wave")
                        )
                        for ((fUrl, fTitle) in fallbackItems) {
                            addToBucket("OmniShield Fallback", com.ritvyom.yashoraReelgenerator.domain.models.MediaSuggestion(
                                url = fUrl,
                                thumbnailUrl = fUrl,
                                title = fTitle,
                                source = "OmniShield Fallback",
                                mediaType = "IMAGE"
                            ))
                        }
                    } catch (e: Exception) {
                        Log.e("GeminiService", "OmniShield Fallback suggestions fetch failed", e)
                    }
                }

                // 15. NHTSA vPIC Automotive & Supercar Catalog
                launch {
                    try {
                        val carMakes = listOf("Porsche", "Ferrari", "BMW", "Tesla", "Mercedes", "Lamborghini", "Ford", "Audi")
                        val queryLower = query.lowercase()
                        val detectedMake = carMakes.firstOrNull { queryLower.contains(it.lowercase()) } ?: carMakes[kotlin.math.abs(query.hashCode()) % carMakes.size]
                        val encodedMake = java.net.URLEncoder.encode(detectedMake, "UTF-8")
                        val nhtsaUrl = "https://vpic.nhtsa.dot.gov/api/vehicles/getmodelsformake/$encodedMake?format=json"
                        val req = Request.Builder().url(nhtsaUrl).header("User-Agent", "Mozilla/5.0").build()
                        val resp = httpClient.newCall(req).execute()
                        val modelsList = mutableListOf<String>()
                        if (resp.isSuccessful) {
                            val json = JSONObject(resp.body?.string() ?: "")
                            val results = json.optJSONArray("Results")
                            if (results != null) {
                                for (i in 0 until minOf(results.length(), 6)) {
                                    val m = results.optJSONObject(i)?.optString("Model_Name")
                                    if (!m.isNullOrBlank()) modelsList.add(m)
                                }
                            }
                        }
                        if (modelsList.isEmpty()) {
                            modelsList.addAll(listOf("911 GT3 RS", "Taycan Turbo", "Panamera GTS", "Cayenne Turbo GT"))
                        }
                        for (model in modelsList.take(4)) {
                            val prompt = "Ultra-realistic 8k cinematic studio photograph of a $detectedMake $model, dramatic automotive lighting, reflection on sleek surface, hyper-detailed automotive showcase"
                            val encPrompt = java.net.URLEncoder.encode(prompt, "UTF-8")
                            val imgUrl = "https://image.pollinations.ai/p/$encPrompt?width=1080&height=1920&seed=${(detectedMake + model).hashCode()}&nologo=true"
                            addToBucket("NHTSA vPIC", com.ritvyom.yashoraReelgenerator.domain.models.MediaSuggestion(
                                url = imgUrl,
                                thumbnailUrl = imgUrl,
                                title = "$detectedMake $model Showcase",
                                source = "NHTSA vPIC",
                                mediaType = "IMAGE"
                            ))
                        }
                    } catch (e: Exception) {
                        Log.e("GeminiService", "NHTSA vPIC suggestions fetch failed", e)
                    }
                }

                // 16. OpenFDA Clinical & Pharmaceutical Catalog
                launch {
                    try {
                        val qClean = query.replace(Regex("[^a-zA-Z0-9\\s]"), "").trim()
                        val encodedQ = if (qClean.isNotEmpty()) java.net.URLEncoder.encode(qClean, "UTF-8") else "medicine"
                        val fdaUrl = "https://api.fda.gov/drug/label.json?search=description:$encodedQ&limit=5"
                        val req = Request.Builder().url(fdaUrl).header("User-Agent", "Mozilla/5.0").build()
                        val resp = httpClient.newCall(req).execute()
                        val drugsList = mutableListOf<String>()
                        if (resp.isSuccessful) {
                            val json = JSONObject(resp.body?.string() ?: "")
                            val results = json.optJSONArray("results")
                            if (results != null) {
                                for (i in 0 until minOf(results.length(), 4)) {
                                    val obj = results.optJSONObject(i)
                                    val openfda = obj?.optJSONObject("openfda")
                                    val bName = openfda?.optJSONArray("brand_name")?.optString(0)
                                    val aIng = openfda?.optJSONArray("active_ingredient")?.optString(0)
                                    val name = if (!bName.isNullOrBlank()) bName else aIng
                                    if (!name.isNullOrBlank()) drugsList.add(name)
                                }
                            }
                        }
                        if (drugsList.isEmpty()) {
                            drugsList.addAll(listOf("Vitality Boost Complex", "NeuroCalm Botanical", "CardioShield Pro", "ImmunoDefense Elixir"))
                        }
                        for (drug in drugsList.take(4)) {
                            val prompt = "A clean minimalist high-end clinical pharmaceutical product photograph of $drug medical bottle packaging, soft studio shadows, white minimalist medical background, ultra-sharp 8k"
                            val encPrompt = java.net.URLEncoder.encode(prompt, "UTF-8")
                            val imgUrl = "https://image.pollinations.ai/p/$encPrompt?width=1080&height=1920&seed=${drug.hashCode()}&nologo=true"
                            addToBucket("OpenFDA", com.ritvyom.yashoraReelgenerator.domain.models.MediaSuggestion(
                                url = imgUrl,
                                thumbnailUrl = imgUrl,
                                title = "$drug Clinical Formula",
                                source = "OpenFDA",
                                mediaType = "IMAGE"
                            ))
                        }
                    } catch (e: Exception) {
                        Log.e("GeminiService", "OpenFDA suggestions fetch failed", e)
                    }
                }

                // 17. WHO GHO Global Health & Wellness Catalog
                launch {
                    try {
                        val qClean = query.replace(Regex("[^a-zA-Z0-9\\s]"), "").trim()
                        val whoUrl = if (qClean.isNotEmpty()) {
                            val enc = java.net.URLEncoder.encode(qClean, "UTF-8")
                            "https://ghoapi.azureedge.net/api/Indicator?\$filter=contains(IndicatorName,%20'$enc')"
                        } else {
                            "https://ghoapi.azureedge.net/api/Indicator"
                        }
                        val req = Request.Builder().url(whoUrl).header("User-Agent", "Mozilla/5.0").build()
                        val resp = httpClient.newCall(req).execute()
                        val indicators = mutableListOf<String>()
                        if (resp.isSuccessful) {
                            val json = JSONObject(resp.body?.string() ?: "")
                            val vals = json.optJSONArray("value")
                            if (vals != null) {
                                for (i in 0 until minOf(vals.length(), 4)) {
                                    val indName = vals.optJSONObject(i)?.optString("IndicatorName")
                                    if (!indName.isNullOrBlank()) indicators.add(indName)
                                }
                            }
                        }
                        if (indicators.isEmpty()) {
                            indicators.addAll(listOf("Global Wellness & Longevity", "Clean Water & Sanitation", "Maternal Health & Vitality", "Universal Healthcare Access"))
                        }
                        for (ind in indicators.take(4)) {
                            val prompt = "Inspiring modern editorial photograph representing $ind, warm natural lighting, uplifting human connection, global health, National Geographic style documentary photography"
                            val encPrompt = java.net.URLEncoder.encode(prompt, "UTF-8")
                            val imgUrl = "https://image.pollinations.ai/p/$encPrompt?width=1080&height=1920&seed=${ind.hashCode()}&nologo=true"
                            addToBucket("WHO GHO", com.ritvyom.yashoraReelgenerator.domain.models.MediaSuggestion(
                                url = imgUrl,
                                thumbnailUrl = imgUrl,
                                title = ind.take(40),
                                source = "WHO GHO",
                                mediaType = "IMAGE"
                            ))
                        }
                    } catch (e: Exception) {
                        Log.e("GeminiService", "WHO GHO suggestions fetch failed", e)
                    }
                }
            }
        }

        // Interleave items fairly across all available sources so user sees items from every source!
        val combinedList = mutableListOf<com.ritvyom.yashoraReelgenerator.domain.models.MediaSuggestion>()
        val bucketLists = sourceBuckets.values.map { it.toMutableList() }.filter { it.isNotEmpty() }.toMutableList()
        while (bucketLists.isNotEmpty()) {
            val iterator = bucketLists.iterator()
            while (iterator.hasNext()) {
                val subList = iterator.next()
                if (subList.isNotEmpty()) {
                    combinedList.add(subList.removeAt(0))
                }
                if (subList.isEmpty()) {
                    iterator.remove()
                }
            }
        }

        // Fallback if empty: Search popular generic fallbacks across Pexels and Pixabay
        if (combinedList.isEmpty()) {
            val fallbackKeywords = listOf("nature", "cinematic", "city life", "abstract")
            for (fb in fallbackKeywords) {
                if (combinedList.isNotEmpty()) break
                try {
                    if (isVideoMode) {
                        val encoded = java.net.URLEncoder.encode(fb, "UTF-8")
                        val url = "https://api.pexels.com/videos/search?query=$encoded&orientation=$orientationPexels&per_page=8"
                        val req = Request.Builder().url(url).header("Authorization", pexelsKeyResolved).header("User-Agent", "Mozilla/5.0").build()
                        val resp = httpClient.newCall(req).execute()
                        if (resp.isSuccessful) {
                            val json = JSONObject(resp.body?.string() ?: "")
                            val videos = json.optJSONArray("videos")
                            if (videos != null) {
                                for (i in 0 until videos.length()) {
                                    val vo = videos.optJSONObject(i) ?: continue
                                    val poster = vo.optString("image")
                                    val duration = vo.optInt("duration", 0)
                                    val vFiles = vo.optJSONArray("video_files")
                                    if (vFiles != null && vFiles.length() > 0) {
                                        val link = vFiles.optJSONObject(0)?.optString("link") ?: ""
                                        if (link.isNotEmpty()) {
                                            combinedList.add(com.ritvyom.yashoraReelgenerator.domain.models.MediaSuggestion(
                                                url = link,
                                                thumbnailUrl = poster,
                                                title = "Trending Clip",
                                                source = "Pexels",
                                                mediaType = "VIDEO",
                                                durationSeconds = duration
                                            ))
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        val encoded = java.net.URLEncoder.encode(fb, "UTF-8")
                        val url = "https://api.unsplash.com/search/photos?query=$encoded&client_id=$unsplashKeyResolved&per_page=8&orientation=$orientationUnsplash"
                        val req = Request.Builder().url(url).header("User-Agent", "Mozilla/5.0").build()
                        val resp = httpClient.newCall(req).execute()
                        if (resp.isSuccessful) {
                            val results = JSONObject(resp.body?.string() ?: "").optJSONArray("results")
                            if (results != null) {
                                for (i in 0 until results.length()) {
                                    val po = results.optJSONObject(i) ?: continue
                                    val fullUrl = po.optJSONObject("urls")?.optString("regular") ?: ""
                                    val thumbUrl = po.optJSONObject("urls")?.optString("small") ?: fullUrl
                                    if (fullUrl.isNotEmpty()) {
                                        combinedList.add(com.ritvyom.yashoraReelgenerator.domain.models.MediaSuggestion(
                                            url = fullUrl,
                                            thumbnailUrl = thumbUrl,
                                            title = "$fb Wallpaper",
                                            source = "Unsplash",
                                            mediaType = "IMAGE"
                                        ))
                                    }
                                }
                            }
                        }
                    }
                } catch (e: Exception) {}
            }
        }

        return@withContext combinedList.distinctBy { it.url }
    }

    fun getThematicFallbackQuery(query: String): String {
        val q = query.lowercase(java.util.Locale.ROOT)
        return when {
            q.contains("food") || q.contains("cook") || q.contains("recipe") || q.contains("chef") ||
            q.contains("meal") || q.contains("kitchen") || q.contains("dish") || q.contains("taste") ||
            q.contains("curry") || q.contains("cake") || q.contains("biryani") || q.contains("paneer") -> "delicious food cooking"
            
            q.contains("code") || q.contains("program") || q.contains("tech") || q.contains("ai") ||
            q.contains("computer") || q.contains("software") || q.contains("robot") || q.contains("cyber") -> "technology modern computer"
            
            q.contains("money") || q.contains("finance") || q.contains("business") || q.contains("stock") ||
            q.contains("market") || q.contains("crypto") || q.contains("wealth") || q.contains("office") -> "business finance workspace"
            
            q.contains("nature") || q.contains("mountain") || q.contains("forest") || q.contains("ocean") ||
            q.contains("river") || q.contains("tree") || q.contains("sky") || q.contains("sunset") ||
            q.contains("landscape") || q.contains("sea") -> "nature scenic landscape"
            
            q.contains("gym") || q.contains("workout") || q.contains("fitness") || q.contains("exercise") ||
            q.contains("running") || q.contains("sport") || q.contains("health") -> "fitness gym workout"
            
            q.contains("city") || q.contains("street") || q.contains("travel") || q.contains("building") ||
            q.contains("architecture") || q.contains("urban") -> "city architecture street"
            
            q.contains("car") || q.contains("drive") || q.contains("vehicle") || q.contains("speed") -> "modern luxury car"
            
            q.contains("space") || q.contains("planet") || q.contains("star") || q.contains("galaxy") ||
            q.contains("astronaut") -> "outer space galaxy"
            
            q.contains("diwali") || q.contains("festival") || q.contains("celebration") || q.contains("party") ||
            q.contains("light") || q.contains("lantern") -> "festival celebration lights"
            
            q.contains("people") || q.contains("lifestyle") || q.contains("person") || q.contains("happy") ||
            q.contains("smile") || q.contains("friend") -> "lifestyle happy people"
            
            else -> "cinematic background"
        }
    }

    fun getThematicSceneFallbackImage(
        query: String,
        sceneNum: Int,
        aspectRatio: String,
        alreadyUsedUrls: Set<String> = emptySet()
    ): String {
        val q = query.lowercase(java.util.Locale.ROOT)
        val isLandscape = aspectRatio == "16:9" || aspectRatio == "21:9"
        val dim = if (isLandscape) "&w=1920&h=1080" else "&w=1080&h=1920"

        val crimeLegalImages = listOf(
            "https://images.unsplash.com/photo-1589829545856-d10d557cf95f?auto=format&fit=crop$dim&q=80", // Court gavel
            "https://images.unsplash.com/photo-1453728013993-6d66e9c9123a?auto=format&fit=crop$dim&q=80", // Lens investigation
            "https://images.unsplash.com/photo-1505664194779-8beaceb93744?auto=format&fit=crop$dim&q=80", // Legal books
            "https://images.unsplash.com/photo-1521587760476-6c12a4b040da?auto=format&fit=crop$dim&q=80", // Library archives
            "https://images.unsplash.com/photo-1589994965851-a8f479c573a9?auto=format&fit=crop$dim&q=80"  // Justice scale
        )
        val agricultureImages = listOf(
            "https://images.unsplash.com/photo-1500937386664-56d1dfef3854?auto=format&fit=crop$dim&q=80", // Wheat farm
            "https://images.unsplash.com/photo-1625246333195-78d9c38ad449?auto=format&fit=crop$dim&q=80", // Farmer field
            "https://images.unsplash.com/photo-1592417817098-8f3d69109853?auto=format&fit=crop$dim&q=80", // Crops field
            "https://images.unsplash.com/photo-1523348837708-15d4a09cfac2?auto=format&fit=crop$dim&q=80"  // Sprout soil
        )
        val medicalImages = listOf(
            "https://images.unsplash.com/photo-1519494026892-80bbd2d6fd0d?auto=format&fit=crop$dim&q=80", // Hospital corridor
            "https://images.unsplash.com/photo-1505751172876-fa1923c5c528?auto=format&fit=crop$dim&q=80", // Doctor stethoscope
            "https://images.unsplash.com/photo-1532938911079-1b06ac7ceec7?auto=format&fit=crop$dim&q=80", // Clinic care
            "https://images.unsplash.com/photo-1579684385127-1ef15d508118?auto=format&fit=crop$dim&q=80"  // Medical lab
        )
        val foodImages = listOf(
            "https://images.unsplash.com/photo-1546069901-ba9599a7e63c?auto=format&fit=crop$dim&q=80",
            "https://images.unsplash.com/photo-1504674900247-0877df9cc836?auto=format&fit=crop$dim&q=80",
            "https://images.unsplash.com/photo-1555939594-58d7cb561ad1?auto=format&fit=crop$dim&q=80",
            "https://images.unsplash.com/photo-1565299624946-b28f40a0ae38?auto=format&fit=crop$dim&q=80",
            "https://images.unsplash.com/photo-1567620905732-2d1ec7ab7445?auto=format&fit=crop$dim&q=80"
        )
        val techImages = listOf(
            "https://images.unsplash.com/photo-1518770660439-4636190af475?auto=format&fit=crop$dim&q=80",
            "https://images.unsplash.com/photo-1526374965328-7f61d4dc18c5?auto=format&fit=crop$dim&q=80",
            "https://images.unsplash.com/photo-1451187580459-43490279c0fa?auto=format&fit=crop$dim&q=80",
            "https://images.unsplash.com/photo-1550751827-4bd374c3f58b?auto=format&fit=crop$dim&q=80",
            "https://images.unsplash.com/photo-1531297484001-80022131f5a1?auto=format&fit=crop$dim&q=80"
        )
        val natureImages = listOf(
            "https://images.unsplash.com/photo-1506744038136-46273834b3fb?auto=format&fit=crop$dim&q=80",
            "https://images.unsplash.com/photo-1470071459604-3b5ec3a7fe05?auto=format&fit=crop$dim&q=80",
            "https://images.unsplash.com/photo-1426604966848-d7adac402bff?auto=format&fit=crop$dim&q=80",
            "https://images.unsplash.com/photo-1472214103451-9374bd1c798e?auto=format&fit=crop$dim&q=80",
            "https://images.unsplash.com/photo-1469474968028-56623f02e42e?auto=format&fit=crop$dim&q=80"
        )
        val businessImages = listOf(
            "https://images.unsplash.com/photo-1486406146926-c627a92ad1ab?auto=format&fit=crop$dim&q=80",
            "https://images.unsplash.com/photo-1460925895917-afdab827c52f?auto=format&fit=crop$dim&q=80",
            "https://images.unsplash.com/photo-1554224155-8d04cb21cd6c?auto=format&fit=crop$dim&q=80",
            "https://images.unsplash.com/photo-1507679799987-c73779587ccf?auto=format&fit=crop$dim&q=80",
            "https://images.unsplash.com/photo-1551836022-d5d88e9218df?auto=format&fit=crop$dim&q=80"
        )
        val celebrationImages = listOf(
            "https://images.unsplash.com/photo-1514525253161-7a46d19cd819?auto=format&fit=crop$dim&q=80",
            "https://images.unsplash.com/photo-1492684223066-81342ee5ff30?auto=format&fit=crop$dim&q=80",
            "https://images.unsplash.com/photo-1509198397868-475647b2a1e5?auto=format&fit=crop$dim&q=80",
            "https://images.unsplash.com/photo-1511795409834-ef04bbd61622?auto=format&fit=crop$dim&q=80"
        )
        val cinematicImages = listOf(
            "https://images.unsplash.com/photo-1550684848-fac1c5b4e853?auto=format&fit=crop$dim&q=80",
            "https://images.unsplash.com/photo-1579783902614-a3fb3927b675?auto=format&fit=crop$dim&q=80",
            "https://images.unsplash.com/photo-1518709268805-4e9042af9f23?auto=format&fit=crop$dim&q=80",
            "https://images.unsplash.com/photo-1534447677768-be436bb09401?auto=format&fit=crop$dim&q=80",
            "https://images.unsplash.com/photo-1478760329108-5c3ed9d495a0?auto=format&fit=crop$dim&q=80"
        )

        val targetPool = when {
            q.contains("cbi") || q.contains("court") || q.contains("police") || q.contains("investigat") || q.contains("evidence") || q.contains("crime") || q.contains("case") || q.contains("law") -> crimeLegalImages
            q.contains("farmer") || q.contains("kisan") || q.contains("agriculture") || q.contains("crop") || q.contains("farm") -> agricultureImages
            q.contains("doctor") || q.contains("hospital") || q.contains("health") || q.contains("medicine") || q.contains("patient") -> medicalImages
            q.contains("food") || q.contains("cook") || q.contains("recipe") || q.contains("chef") || q.contains("kitchen") -> foodImages
            q.contains("code") || q.contains("tech") || q.contains("program") || q.contains("software") || q.contains("ai") || q.contains("computer") -> techImages
            q.contains("money") || q.contains("business") || q.contains("finance") || q.contains("stock") -> businessImages
            q.contains("nature") || q.contains("mountain") || q.contains("forest") || q.contains("landscape") || q.contains("sunset") -> natureImages
            q.contains("diwali") || q.contains("festival") || q.contains("celebration") || q.contains("party") || q.contains("lantern") -> celebrationImages
            else -> cinematicImages
        }

        val poolSize = targetPool.size
        val startIdx = Math.abs(sceneNum + query.hashCode()) % poolSize
        for (offset in 0 until poolSize) {
            val candidate = targetPool[(startIdx + offset) % poolSize]
            if (candidate !in alreadyUsedUrls) {
                return candidate
            }
        }
        return targetPool[startIdx]
    }

    fun getThematicSceneFallbackVideo(
        query: String,
        sceneNum: Int,
        alreadyUsedUrls: Set<String> = emptySet()
    ): String {
        val videoPool = listOf(
            "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4",
            "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerEscapes.mp4",
            "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerFun.mp4",
            "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerJoyBlazes.mp4",
            "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerMeltdowns.mp4",
            "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/Sintel.mp4",
            "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/SubaruOutbackSeeTheWorld.mp4",
            "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/TearsOfSteel.mp4",
            "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/WeAreGoingOnBullrun.mp4",
            "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/WhatCarCanYouGetForAGrand.mp4"
        )
        val poolSize = videoPool.size
        val startIdx = Math.abs(sceneNum + query.hashCode()) % poolSize
        for (offset in 0 until poolSize) {
            val candidate = videoPool[(startIdx + offset) % poolSize]
            if (candidate !in alreadyUsedUrls) {
                return candidate
            }
        }
        return videoPool[startIdx]
    }

    private fun sanitizeAndRepairJson(rawText: String): String {
        var cleanedText = rawText.trim()
        
        // 1. Handle markdown code blocks
        if (cleanedText.startsWith("```")) {
            val lines = cleanedText.lines()
            if (lines.size >= 2) {
                val lastLineHasFence = lines.last().trim().endsWith("```") || lines.last().trim().startsWith("```")
                val endIdx = if (lastLineHasFence) lines.size - 1 else lines.size
                cleanedText = lines.subList(1, endIdx).joinToString("\n").trim()
            } else {
                cleanedText = cleanedText.replace("```json", "").replace("```", "").trim()
            }
        } else {
            cleanedText = cleanedText.replace("```json", "").replace("```", "").trim()
        }
        
        cleanedText = cleanedText.trim()
        if (cleanedText.isEmpty()) return ""

        // 2. Strip conversational preambles/postambles bounded by JSON container brackets
        val firstBrace = cleanedText.indexOf('{')
        val firstBracket = cleanedText.indexOf('[')
        val startIndex = when {
            firstBrace != -1 && firstBracket != -1 -> minOf(firstBrace, firstBracket)
            firstBrace != -1 -> firstBrace
            firstBracket != -1 -> firstBracket
            else -> -1
        }
        if (startIndex > 0) {
            cleanedText = cleanedText.substring(startIndex)
        }

        val lastBrace = cleanedText.lastIndexOf('}')
        val lastBracket = cleanedText.lastIndexOf(']')
        val endIndex = when {
            lastBrace != -1 && lastBracket != -1 -> maxOf(lastBrace, lastBracket)
            lastBrace != -1 -> lastBrace
            lastBracket != -1 -> lastBracket
            else -> -1
        }
        if (endIndex != -1 && endIndex < cleanedText.length - 1) {
            cleanedText = cleanedText.substring(0, endIndex + 1)
        }
        
        // 3. Fix dangling colons (e.g., "imageSearchQuery": at the end of input or before a brace/bracket/comma)
        cleanedText = cleanedText.replace(Regex("(\"[^\"]+\"\\s*:\\s*)(?=[,\\]\\}]|\\s*$)"), "$1\"\"")
        
        // 4. Remove trailing commas before closing braces/brackets
        cleanedText = cleanedText.replace(Regex(",\\s*(?=[\\]\\}])"), "")
        
        // 5. Auto-close unclosed strings and brackets/braces
        val openBrackets = mutableListOf<Char>()
        var inString = false
        var escaped = false
        
        for (i in cleanedText.indices) {
            val c = cleanedText[i]
            if (escaped) {
                escaped = false
                continue
            }
            if (c == '\\') {
                escaped = true
                continue
            }
            if (c == '"') {
                inString = !inString
                continue
            }
            if (!inString) {
                if (c == '{' || c == '[') {
                    openBrackets.add(c)
                } else if (c == '}') {
                    if (openBrackets.isNotEmpty() && openBrackets.last() == '{') {
                        openBrackets.removeAt(openBrackets.size - 1)
                    }
                } else if (c == ']') {
                    if (openBrackets.isNotEmpty() && openBrackets.last() == '[') {
                        openBrackets.removeAt(openBrackets.size - 1)
                    }
                }
            }
        }
        
        val sb = StringBuilder(cleanedText)
        if (inString) {
            sb.append('"')
        }
        
        for (j in openBrackets.indices.reversed()) {
            val openChar = openBrackets[j]
            var tempStr = sb.toString().trim()
            if (tempStr.endsWith(",")) {
                sb.setLength(tempStr.length - 1)
            }
            if (openChar == '{') {
                sb.append('}')
            } else if (openChar == '[') {
                sb.append(']')
            }
        }
        
        return sb.toString().trim()
    }

    companion object {
        fun extractSemiSemanticKeyword(sentence: String): String {
            return GeminiService().extractSemiSemanticKeyword(sentence)
        }
    }
}
