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
import kotlinx.coroutines.coroutineScope
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
        .connectTimeout(120, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .build()

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private fun getCleanApiKey(customKey: String): String {
        val rawKey = if (customKey.isNotEmpty()) {
            customKey
        } else {
            try {
                BuildConfig.GEMINI_API_KEY
            } catch (e: Exception) {
                ""
            }
        }
        return rawKey.trim().removeSurrounding("\"").removeSurrounding("'")
    }

    private fun isApiKeyValid(key: String): Boolean {
        val cleaned = key.trim().removeSurrounding("\"").removeSurrounding("'")
        return cleaned.isNotEmpty() && 
               cleaned.length >= 10 &&
               cleaned != "MY_GEMINI_API_KEY" && 
               cleaned != "YOUR_GEMINI_API_KEY" && 
               !cleaned.startsWith("YOUR_") && 
               !cleaned.startsWith("MY_") &&
               !cleaned.equals("placeholder", ignoreCase = true)
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
            
            CRITICAL COMPLETE SCRIPT COVERAGE MANDATE (NEVER OMIT ANY PART OF SCRIPT):
            - You MUST process and break down 100% of the provided script from the VERY FIRST SENTENCE to the VERY LAST SENTENCE.
            - DO NOT summarize, condense, skip, or truncate any portion of the script narration text!
            - DYNAMIC SCENE DENSITY MANDATE:
              * For short scripts (<75 words): Generate 8 to 15 distinct scenes.
              * For medium scripts (75 to 200 words / ~1 minute): Generate 15 to 30 distinct scenes.
              * For long scripts (200 to 500+ words / 2 to 3+ minutes): Generate 30 to 60+ distinct scenes!
              * Ensure every 5 to 10 words of narration has its own distinct visual scene (lasting 3 to 6 seconds per scene).
            - Ensure that the concatenation of all "narrationText" fields across all generated scenes EXACTLY covers the entire provided script text from beginning to end!
            
            Break down the script sentence-by-sentence, or phrase-by-phrase so that the scenes update dynamically every 3 to 6 seconds. Do not keep the screen stagnant.
            
            CRITICAL LANGUAGE & VOICE SYNTHESIS REQUIREMENT:
            - If the Target Language is "$language" and the input Script is NOT written in that language, you MUST translate the "narrationText" and "subtitle" fields into $language (using the native alphabet/script of $language, e.g., Devanagari script for Hindi, Cyrillic for Russian,, etc.).
            - Ensure the translations are elegant, high-impact, natural, and perfectly rhythmic for narration in a professional short video.
            - Ensure the "visualPrompt" remains in descriptive, highly colorful ENGLISH so it can map to professional background photographs (include style hints corresponding to $style).
            - VERY IMPORTANT: Do NOT include any decorative symbols, emojis, hashtags, markdown formatting (such as asterisks `*` or underscores `_`), or consecutive dots / ellipses (such as `...` or `......`) inside the "narrationText" or "subtitle" fields. Always use clean, native language letters and words with standard periods/commas for natural pauses. Never use symbols or dots that would confuse the Text-to-Speech synthesizer or be read aloud as "dot dot dot".

            CRITICAL CELEBRITY & PUBLIC FIGURE DETECTION:
            - Scan the script carefully for any mention of real-world popular figures, leaders, politicians, celebrities, or sports stars (e.g. "Modi ji", "Narendra Modi", "Yogi ji", "Elon Musk", "MS Dhoni", "Virat Kohli", "Donald Trump", etc.).
            - If any such figure is mentioned or implied, you MUST explicitly generate a highly descriptive visual prompt featuring them in the "visualPrompt" field (e.g., "A photorealistic, highly detailed portrait of Prime Minister Narendra Modi smiling in front of the Indian flag").
            - The "imageSearchQuery" for that specific scene must be set to their official standard English name (e.g., "Narendra Modi" or "Elon Musk") to trigger downstream generative AI synthesis.
            - Do not substitute them with generic people; represent the celebrity as named in the script directly in both "visualPrompt" and "imageSearchQuery".
            
            For each scene, you must provide:
            1. Scene Number (1-indexed)
            2. Narration Text (the words to be spoken for this specific scene, in the native script of $language)
            3. Visual Prompt (a highly descriptive, vivid English prompt describing the background scene, subject, angles, lighting, aligned with $style style)
            4. Duration in seconds (approximate narrative time, between 3 to 8 seconds)
            5. Subtitle Text (the text overlay / subtitle, matching narration / spoken phrases in $language)
            6. Image Search Query (strictly 1 to 2 clean, concrete English nouns of real-world subjects representing the primary visual element of the scene, e.g. "lion", "coding computer", "money cash", "cozy coffee", "sad programmer", "forest trail", "gym workout". This is the SINGLE MOST RELEVANT, high-probability stock keyword tag in English of 1-3 simple, clean nouns. Select a tag that is highly likely to exist as a tag on stock databases like Pexels, Pixabay, or Unsplash. Absolutely no adjectives like "beautiful", "epic", and no styling/technical terms.)
            7. Keywords (strictly 3 to 5 highly descriptive visual search keywords or phrases in English describing the scene visually, e.g. ["astronaut floating in space", "outer space vacuum", "astronaut using radio in space", "stars space void"])
            
            Respond strictly with a JSON array where each item has the exact fields: "sceneNumber", "narrationText", "visualPrompt", "durationSeconds", "subtitle", "imageSearchQuery", "keywords".
            Do not enclose in markdown blocks unless they are "```json ... ```". Output raw valid JSON.
        """.trimIndent()

        var text = ""

        // 1. Try REST API first with developer's direct key (highly preferred for cost savings on low usage)
        if (isApiKeyValid(apiKey)) {
            try {
                Log.i("GeminiService", "Attempting direct REST API for analyzeScript...")
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

                var forceVertexAI = false
                val modelsToTry = listOf(
                    "gemini-3.5-flash",
                    "gemini-2.0-flash",
                    "gemini-1.5-flash",
                    "gemini-2.0-flash-lite",
                    "gemini-1.5-flash-8b"
                )

                for (modelName in modelsToTry) {
                    if (forceVertexAI) {
                        Log.w("GeminiService", "Bypassing remaining REST models for analyzeScript due to Priority API Gateway trigger.")
                        break
                    }
                    try {
                        Log.i("GeminiService", "Attempting direct REST API for analyzeScript with model: $modelName")
                        val url = "https://generativelanguage.googleapis.com/v1beta/models/$modelName:generateContent?key=$apiKey"
                        val request = Request.Builder()
                            .url(url)
                            .post(requestBody)
                            .build()

                        val response = httpClient.newCall(request).execute()
                        if (response.isSuccessful) {
                            val responseBodyString = response.body?.string() ?: ""
                            Log.d("GeminiService", "Gemini REST raw response: $responseBodyString")

                            val responseJson = JSONObject(responseBodyString)
                            val candidates = responseJson.optJSONArray("candidates")
                            val firstCandidate = candidates?.optJSONObject(0)
                            val content = firstCandidate?.optJSONObject("content")
                            val parts = content?.optJSONArray("parts")
                            val textResult = parts?.optJSONObject(0)?.optString("text") ?: ""
                            if (textResult.isNotEmpty()) {
                                text = textResult
                                Log.i("GeminiService", "Direct REST API analyzeScript completed successfully with model: $modelName")
                                break
                            }
                        } else {
                            Log.w("GeminiService", "REST API call failed for $modelName with response code: ${response.code}")
                            if (PriorityApiGateway.shouldSwitchToVertexAI(response.code)) {
                                forceVertexAI = true
                                break
                            }
                        }
                    } catch (e: Exception) {
                        Log.w("GeminiService", "REST API call failed for $modelName with exception: ${e.message}")
                    }
                }
            } catch (e: Exception) {
                Log.w("GeminiService", "REST API preparation failed: ${e.message}")
            }
        } else {
            Log.w("GeminiService", "No direct REST API key is available or key is default placeholder.")
        }

        // 2. Try Firebase Vertex AI SDK next if REST API failed or returned empty text
        if (text.isEmpty()) {
            val firebaseModels = listOf(
                "gemini-3.5-flash",
                "gemini-2.0-flash",
                "gemini-1.5-flash",
                "gemini-2.0-flash-lite",
                "gemini-1.5-flash-8b"
            )
            Log.i("GeminiService", "Direct REST failed or not configured, attempting Firebase AI Logic SDK fallback...")
            for (firebaseModel in firebaseModels) {
                try {
                    Log.i("GeminiService", "Attempting Firebase AI with model: $firebaseModel")
                    val model = Firebase.ai(backend = GenerativeBackend.vertexAI(location = "global"))
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
                        text = result
                        PriorityApiGateway.logSuccessfulFailover("Storyboard Analysis", firebaseModel)
                        Log.i("GeminiService", "Firebase AI Logic SDK analyzeScript completed successfully using model: $firebaseModel")
                        break
                    }
                } catch (fEx: Exception) {
                    Log.w("GeminiService", "Firebase Vertex AI analyzeScript failed with model $firebaseModel: ${fEx.message}")
                }
            }
        }

        if (text.isEmpty()) {
            Log.w("GeminiService", "Both REST and Firebase Vertex AI failed. Falling back to local parser.")
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
                            
                            val mediaUrl = getBestMatchingImage(visPrompt, style, i, imgSearchQuery, aspectRatio, imageSource, customUnsplashKey)
                            
                            val sc = Scene(
                                sceneNumber = obj.optInt("sceneNumber", i + 1),
                                narrationText = obj.optString("narrationText", ""),
                                visualPrompt = visPrompt,
                                durationSeconds = obj.optInt("durationSeconds", 5),
                                subtitle = obj.optString("subtitle", ""),
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
                    Log.d("GeminiService", "Script total words: ${scriptWords.size}, Parsed scenes total words: $parsedWordsCount across ${list.size} scenes")
                    
                    // If the original script is a long script (>=70 words) but Gemini returned scenes covering less than 50% of the word count,
                    // fallback to fallbackLocalParser to guarantee 100% full script coverage and generate scenes for the entire speech!
                    if (scriptWords.size >= 70 && parsedWordsCount < (scriptWords.size * 0.5)) {
                        Log.w("GeminiService", "Gemini response covered only $parsedWordsCount of ${scriptWords.size} words. Using fallbackLocalParser for complete script coverage.")
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

        // 14. Food, Cooking, Delicious, Cafe, Coffee, Drink
        val foodTerms = listOf(
            "food", "cooking", "recipe", "delicious", "eat", "cafe", "coffee", "restaurant", "tea", "baking", "chef", "kitchen",
            "खाना", "भोजन", "चाय", "कॉफ़ी", "रसोई", "पकाना", "स्वादिष्ट"
        )
        if (foodTerms.any { lower.contains(it) }) {
            keywords.add("gourmet culinary food platter")
            if (lower.contains("coffee") || lower.contains("tea") || lower.contains("चाय") || lower.contains("कॉफ़ी")) keywords.add("steaming warm coffee cup art")
            if (lower.contains("kitchen") || lower.contains("cooking") || lower.contains("पकाना") || lower.contains("रसोई")) keywords.add("professional chef kitchen restaurant cooking")
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
        resolution: String? = null,
        visualMedium: String = "Image"
    ): String {
        val styleLower = style.lowercase().trim()
        val finalQuery = if (customSearchQuery.isNotEmpty()) {
            customSearchQuery.trim()
        } else {
            val stopwords = setOf(
                "a", "an", "the", "and", "or", "but", "is", "are", "was", "were", "of", "to", "in", "on", "at", "by", "for", "with", "about",
                "this", "that", "it", "its", "you", "your", "my", "me", "we", "our", "us", "they", "them", "some", "any", "no", "not", "so",
                "can", "will", "show", "get", "make", "be", "have", "has", "had", "do", "does", "did", "from", "very", "scene", "style", "description",
                "captivating", "artistic", "representation", "masterpiece", "detail", "professional", "composition", "background", "photo", "image", "video"
            )
            val words = visualPrompt.lowercase()
                                .replace(Regex("[^\\p{L}\\s]"), "")
                                .split(Regex("\\s+"))
                .filter { it.length >= 2 && !stopwords.contains(it) }
                .distinct()
                .take(3)
            if (words.isNotEmpty()) words.joinToString(" ") else styleLower
        }

        val styleDetail = getStyleDescriptors(styleLower)
        val fullPrompt = if (customSearchQuery.isNotEmpty() && customSearchQuery != visualPrompt) {
            "$customSearchQuery, $visualPrompt, $styleDetail"
        } else {
            "$visualPrompt, $styleDetail"
        }

        val scale = when {
            resolution != null && resolution.contains("360") -> 0.5f
            resolution != null && resolution.contains("480") -> 0.67f
            resolution != null && resolution.contains("540") -> 0.75f
            resolution != null && resolution.contains("720") -> 1.0f
            resolution != null && (resolution.contains("2K") || resolution.contains("1440")) -> 2.0f
            resolution != null && (resolution.contains("4K") || resolution.contains("2160")) -> 3.0f
            resolution != null && (resolution.contains("8K") || resolution.contains("4320")) -> 6.0f
            else -> 1.5f // Defaults to 1080p
        }

        val baseW = when (aspectRatio) {
            "9:16" -> 720
            "16:9" -> 1280
            "1:1" -> 1024
            "4:5" -> 800
            "3:4" -> 768
            "21:9" -> 1280
            else -> 720
        }
        val baseH = when (aspectRatio) {
            "9:16" -> 1280
            "16:9" -> 720
            "1:1" -> 1024
            "4:5" -> 1000
            "3:4" -> 1024
            "21:9" -> 540
            else -> 1280
        }
        val width = (baseW * scale).toInt()
        val height = (baseH * scale).toInt()

        val seed = kotlin.math.abs(visualPrompt.hashCode() + sceneNum)

        val srcLower = imageSource.lowercase().trim()

        // Helper to check if style is a non-photographic stylized type
        val isStylized = styleLower == "anime" || styleLower == "cartoon" || styleLower == "oil painting" ||
                styleLower == "watercolor" || styleLower == "3d animation" || styleLower == "pixar style" ||
                styleLower == "cyberpunk" || styleLower == "sci-fi" || styleLower == "fantasy" ||
                styleLower == "sketch art" || styleLower == "storytelling"

        // Build the optimized priority list including Pexels and Pixabay as powerful fallbacks
        val priorityList = mutableListOf<String>()
        if (isStylized) {
            // For stylized/artistic queries (cartoon, anime etc.), AI Generation is the absolute best match
            priorityList.add("AI Generated")
        }

        val allSources = listOf(
            "Pexels",
            "Pixabay",
            "Unsplash",
            "AI Generated",
            "NASA Library",
            "Wikimedia Commons",
            "Archive.org",
            "Giphy Memes",
            "Jikan Anime",
            "Nekos.best",
            "LoremFlickr",
            "Lorem Picsum",
            "NHTSA vPIC",
            "OpenFDA",
            "WHO"
        )

        val selectedSource = when {
            srcLower.contains("fallback") -> ""
            srcLower.contains("unsplash") -> "Unsplash"
            srcLower.contains("pexels") -> "Pexels"
            srcLower.contains("pixabay") -> "Pixabay"
            srcLower.contains("nasa") -> "NASA Library"
            srcLower.contains("wikimedia") || srcLower.contains("commons") -> "Wikimedia Commons"
            srcLower.contains("archive") -> "Archive.org"
            srcLower.contains("giphy") -> "Giphy Memes"
            srcLower.contains("jikan") || srcLower.contains("anime") || srcLower.contains("mal") -> "Jikan Anime"
            srcLower.contains("neko") -> "Nekos.best"
            srcLower.contains("flickr") -> "LoremFlickr"
            srcLower.contains("picsum") || srcLower.contains("lorem") -> "Lorem Picsum"
            srcLower.contains("ai") || srcLower.contains("generated") || srcLower.contains("pollinations") -> "AI Generated"
            srcLower.contains("nhtsa") || srcLower.contains("vpic") -> "NHTSA vPIC"
            srcLower.contains("fda") || srcLower.contains("openfda") -> "OpenFDA"
            srcLower.contains("who") -> "WHO"
            else -> ""
        }

        val sourcesToTry = mutableListOf<String>()
        if (selectedSource.isNotEmpty()) {
            sourcesToTry.add(selectedSource)
        }

        // Add the priority items first (including AI Generated if stylized)
        for (source in priorityList) {
            if (!sourcesToTry.contains(source)) {
                sourcesToTry.add(source)
            }
        }

        // Fill with all other available sources to ensure absolute bulletproof fallback coverage
        for (source in allSources) {
            if (!sourcesToTry.contains(source)) {
                sourcesToTry.add(source)
            }
        }

        for (source in sourcesToTry) {
            val currentLower = source.lowercase().trim()
            try {
                // If style is non-photographic, we append the style keyword to make sure photos/videos retrieved match cartoon/anime style!
                val queryToUse = if (isStylized && !finalQuery.lowercase().contains(styleLower)) {
                    val styleKeyword = when (styleLower) {
                        "cartoon" -> "cartoon animation"
                        "anime" -> "anime illustration"
                        "3d animation", "pixar style" -> "3d animation"
                        "sci-fi" -> "sci-fi futuristic illustration"
                        "cyberpunk" -> "cyberpunk neon graphic"
                        "fantasy" -> "fantasy digital painting"
                        "sketch art" -> "pencil sketch illustration"
                        else -> "$styleLower style"
                    }
                    "$finalQuery $styleKeyword"
                } else {
                    finalQuery
                }

                if (currentLower.contains("wikimedia") || currentLower.contains("commons")) {
                    val wikimediaUrl = searchWikimediaCommons(queryToUse, sceneNum)
                    if (wikimediaUrl.isNotEmpty()) {
                        Log.d("GeminiService", "Hierarchical Fallback: Resolved via Wikimedia Commons for '$queryToUse': $wikimediaUrl")
                        return wikimediaUrl
                    }
                }
                else if (currentLower.contains("neko")) {
                    val nekosUrl = searchNekosBest(queryToUse, sceneNum)
                    if (nekosUrl.isNotEmpty()) {
                        Log.d("GeminiService", "Hierarchical Fallback: Resolved via Nekos.best for '$queryToUse': $nekosUrl")
                        return nekosUrl
                    }
                }
                else if (currentLower.contains("jikan") || currentLower.contains("anime") || currentLower.contains("mal")) {
                    val jikanUrl = searchJikanAnime(queryToUse, sceneNum)
                    if (jikanUrl.isNotEmpty()) {
                        Log.d("GeminiService", "Hierarchical Fallback: Resolved via Jikan Anime for '$queryToUse': $jikanUrl")
                        return jikanUrl
                    }
                }
                else if (currentLower.contains("picsum") || currentLower.contains("lorem") && !currentLower.contains("flickr")) {
                    val picsumUrl = "https://picsum.photos/seed/$seed/$width/$height"
                    Log.d("GeminiService", "Hierarchical Fallback: Resolved via Lorem Picsum: $picsumUrl")
                    return picsumUrl
                }
                else if (currentLower.contains("archive")) {
                    val archiveUrl = searchArchiveOrg(queryToUse, sceneNum)
                    if (archiveUrl.isNotEmpty()) {
                        Log.d("GeminiService", "Hierarchical Fallback: Resolved via Archive.org for '$queryToUse': $archiveUrl")
                        return archiveUrl
                    }
                }
                else if (currentLower.contains("nasa")) {
                    val isVideo = visualMedium.equals("Video", ignoreCase = true)
                    val nasaUrl = searchNasaLibrary(queryToUse, sceneNum, isVideo)
                    if (nasaUrl.isNotEmpty()) {
                        Log.d("GeminiService", "Hierarchical Fallback: Resolved via NASA Library for '$queryToUse': $nasaUrl")
                        return nasaUrl
                    }
                }
                else if (currentLower.contains("flickr")) {
                    val qClean = queryToUse.replace(Regex("[^a-zA-Z0-9\\s]"), "").trim().replace("\\s+".toRegex(), ",")
                    val flickrUrl = "https://loremflickr.com/$width/$height/$qClean?lock=$seed"
                    Log.d("GeminiService", "Hierarchical Fallback: Resolved via LoremFlickr for '$queryToUse': $flickrUrl")
                    return flickrUrl
                }
                else if (currentLower.contains("giphy")) {
                    val giphyUrl = searchGiphyMemes(queryToUse, sceneNum)
                    if (giphyUrl.isNotEmpty()) {
                        Log.d("GeminiService", "Hierarchical Fallback: Resolved via Giphy Memes for '$queryToUse': $giphyUrl")
                        return giphyUrl
                    }
                }
                else if (currentLower.contains("nhtsa") || currentLower.contains("vpic")) {
                    val nhtsaUrl = searchNhtsaVpicApi(queryToUse, sceneNum)
                    if (nhtsaUrl.isNotEmpty()) {
                        Log.d("GeminiService", "Hierarchical Fallback: Resolved via NHTSA vPIC for '$queryToUse': $nhtsaUrl")
                        return nhtsaUrl
                    }
                }
                else if (currentLower.contains("fda") || currentLower.contains("openfda")) {
                    val fdaUrl = searchOpenFdaApi(queryToUse, sceneNum)
                    if (fdaUrl.isNotEmpty()) {
                        Log.d("GeminiService", "Hierarchical Fallback: Resolved via OpenFDA for '$queryToUse': $fdaUrl")
                        return fdaUrl
                    }
                }
                else if (currentLower.contains("who")) {
                    val whoUrl = searchWhoApi(queryToUse, sceneNum)
                    if (whoUrl.isNotEmpty()) {
                        Log.d("GeminiService", "Hierarchical Fallback: Resolved via WHO for '$queryToUse': $whoUrl")
                        return whoUrl
                    }
                }
                else if (currentLower.contains("pexels")) {
                    val pexelsKey = try {
                        ApiLoadBalancerService.resolvePexelsApiKey(BuildConfig.PEXELS_API_KEY)
                    } catch (e: Exception) {
                        ApiLoadBalancerService.resolvePexelsApiKey("")
                    }
                    if (pexelsKey.isNotEmpty()) {
                        val isVideo = visualMedium.equals("Video", ignoreCase = true)
                        val pexelsUrl = searchPexelsApi(queryToUse, pexelsKey, isVideo, aspectRatio, sceneNum)
                        if (pexelsUrl.isNotEmpty()) {
                            Log.d("GeminiService", "Hierarchical Fallback: Resolved via Pexels ($visualMedium) for '$queryToUse': $pexelsUrl")
                            return pexelsUrl
                        }
                    }
                }
                else if (currentLower.contains("pixabay")) {
                    val pixabayKey = try {
                        ApiLoadBalancerService.resolvePixabayApiKey(BuildConfig.PIXABAY_API_KEY)
                    } catch (e: Exception) {
                        ApiLoadBalancerService.resolvePixabayApiKey("")
                    }
                    if (pixabayKey.isNotEmpty()) {
                        val isVideo = visualMedium.equals("Video", ignoreCase = true)
                        val pixabayUrl = if (isVideo) {
                            searchPixabayVideos(queryToUse, pixabayKey, aspectRatio, sceneNum)
                        } else {
                            searchPixabayApi(queryToUse, pixabayKey, aspectRatio, sceneNum)
                        }
                        if (pixabayUrl.isNotEmpty()) {
                            Log.d("GeminiService", "Hierarchical Fallback: Resolved via Pixabay ($visualMedium) for '$queryToUse': $pixabayUrl")
                            return pixabayUrl
                        }
                    }
                }
                else if (currentLower.contains("unsplash")) {
                    val accessKey = if (!customUnsplashKey.isNullOrEmpty()) {
                        customUnsplashKey
                    } else {
                        try {
                            BuildConfig.UNSPLASH_ACCESS_KEY
                        } catch (e: Exception) {
                            ""
                        }
                    }
                    val rawAccessKey = if (accessKey.isNotEmpty() && accessKey != "YOUR_UNSPLASH_ACCESS_KEY") {
                        accessKey
                    } else {
                        "J6C-j-OjDXft4dMbIm96LlAX4ZqUuViErwbOENvkt4Q"
                    }
                    val finalAccessKey = ApiLoadBalancerService.resolveUnsplashApiKey(rawAccessKey)

                    try {
                        val orientation = when (aspectRatio) {
                            "9:16", "4:5", "3:4" -> "portrait"
                            "16:9", "21:9" -> "landscape"
                            else -> "squarish"
                        }
                        
                        val queryText = queryToUse.replace(Regex("[^\\p{L}\\p{N}\\s]"), "")
                        val encodedQuery = java.net.URLEncoder.encode(queryText, "UTF-8")
                        val url = "https://api.unsplash.com/search/photos?query=$encodedQuery&client_id=$finalAccessKey&per_page=15&orientation=$orientation"
                        
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
                                val resultIndex = sceneNum % results.length()
                                val photoObj = results.optJSONObject(resultIndex)
                                val urlsObj = photoObj?.optJSONObject("urls")
                                val dynamicUrl = urlsObj?.optString("full") ?: urlsObj?.optString("regular") ?: urlsObj?.optString("small")
                                if (!dynamicUrl.isNullOrEmpty()) {
                                    Log.d("GeminiService", "Hierarchical Fallback: Resolved via Unsplash for '$queryText': $dynamicUrl")
                                    return dynamicUrl
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("GeminiService", "Failed to search Unsplash in hierarchical fallback", e)
                    }
                }
                else if (currentLower.contains("ai") || currentLower.contains("generated") || currentLower.contains("pollinations")) {
                    val encodedPrompt = java.net.URLEncoder.encode(fullPrompt, "UTF-8")
                    val generatedUrl = "https://image.pollinations.ai/p/$encodedPrompt?width=$width&height=$height&seed=$seed&nologo=true"
                    Log.d("GeminiService", "Hierarchical Fallback: Resolved via Pollinations AI: $generatedUrl")
                    return generatedUrl
                }
            } catch (e: Exception) {
                Log.e("GeminiService", "Error in hierarchical fallback for source $source", e)
            }
            Log.w("GeminiService", "Source $source returned zero results for query '$finalQuery'. Retrying next fallback...")
        }

        return try {
            val encodedPrompt = java.net.URLEncoder.encode(fullPrompt, "UTF-8")
            val generatedUrl = "https://image.pollinations.ai/p/$encodedPrompt?width=$width&height=$height&seed=$seed&nologo=true"
            Log.d("GeminiService", "Generated Pollinations AI Image URL: $generatedUrl")
            generatedUrl
        } catch (e: Exception) {
            Log.e("GeminiService", "Failed to encode visual prompt for Pollinations AI", e)
            "https://picsum.photos/seed/$seed/$width/$height"
        }
    }

    fun extractSemiSemanticKeyword(sentence: String): String {
        val lower = sentence.lowercase().trim()
        val matches = mutableListOf<String>()
        if (lower.contains("चिड़िया") || lower.contains("चिड़ियां") || lower.contains("bird")) matches.add("bird")
        if (lower.contains("कुत्ता") || lower.contains("dog")) matches.add("dog")
        if (lower.contains("बिल्ली") || lower.contains("cat")) matches.add("cat")
        if (lower.contains("शेर") || lower.contains("lion")) matches.add("lion")
        if (lower.contains("फूल") || lower.contains("गुलाब") || lower.contains("flower") || lower.contains("rose")) matches.add("flower rose")
        if (lower.contains("समुद्र") || lower.contains("सागर") || lower.contains("लहर") || lower.contains("beach") || lower.contains("ocean")) matches.add("ocean beach")
        if (lower.contains("पहाड़") || lower.contains("पर्वत") || lower.contains("mountain") || lower.contains("hills")) matches.add("mountains")
        if (lower.contains("नदी") || lower.contains("नदियां") || lower.contains("river") || lower.contains("lake")) matches.add("river")
        if (lower.contains("जंगल") || lower.contains("वन") || lower.contains("forest") || lower.contains("jungle")) matches.add("forest tree")
        if (lower.contains("पेड़") || lower.contains("पेड़") || lower.contains("tree")) matches.add("green tree")
        if (lower.contains("चाय") || lower.contains("कॉफी") || lower.contains("tea") || lower.contains("coffee")) matches.add("coffee cup")
        if (lower.contains("किताब") || lower.contains("किताबें") || lower.contains("पढ़ना") || lower.contains("book") || lower.contains("read")) matches.add("books reading")
        if (lower.contains("सोना") || lower.contains("अमीर") || lower.contains("gold") || lower.contains("rich")) matches.add("gold wealth")
        if (lower.contains("पैसा") || lower.contains("रुपया") || lower.contains("धन") || lower.contains("money") || lower.contains("cash")) matches.add("money cash")
        if (lower.contains("कार") || lower.contains("गाड़ी") || lower.contains("गाड़ी") || lower.contains("car")) matches.add("luxury car")
        if (lower.contains("घर") || lower.contains("महल") || lower.contains("house") || lower.contains("home")) matches.add("cozy home")
        if (lower.contains("लैपटॉप") || lower.contains("कंप्यूटर") || lower.contains("computer") || lower.contains("laptop") || lower.contains("coding") || lower.contains("code")) matches.add("computer laptop")
        if (lower.contains("लोग") || lower.contains("भीड़") || lower.contains("people") || lower.contains("crowd") || lower.contains("group")) matches.add("happy people")
        if (lower.contains("आग") || lower.contains("अग्नि") || lower.contains("fire") || lower.contains("flame")) matches.add("fire flame")
        if (lower.contains("सूरज") || lower.contains("धूप") || lower.contains("sun")) matches.add("sun")
        if (lower.contains("सूर्यास्त") || lower.contains("sunset")) matches.add("sunset")
        if (lower.contains("सूर्योदय") || lower.contains("sunrise")) matches.add("sunrise")
        if (lower.contains("चांद") || lower.contains("चंद्रमा") || lower.contains("moon")) matches.add("moon")
        if (lower.contains("तारा") || lower.contains("तारे") || lower.contains("stars")) matches.add("starry sky")
        if (lower.contains("बादल") || lower.contains("आसमान") || lower.contains("आकाश") || lower.contains("sky") || lower.contains("clouds")) matches.add("sky clouds")
        if (lower.contains("अंतरिक्ष") || lower.contains("ब्रह्मांड") || lower.contains("space") || lower.contains("galaxy") || lower.contains("universe")) matches.add("outer space")
        if (lower.contains("एआई") || lower.contains("रोबोट") || lower.contains("ai") || lower.contains("robot")) matches.add("ai robot")
        if (lower.contains("सड़क") || lower.contains("रास्ता") || lower.contains("सड़क") || lower.contains("road") || lower.contains("highway")) matches.add("scenic road")
        if (lower.contains("मंदिर") || lower.contains("temple")) matches.add("temple")
        if (lower.contains("भारत") || lower.contains("भारतीय") || lower.contains("india")) matches.add("india")
        if (lower.contains("खाना") || lower.contains("स्वादिष्ट") || lower.contains("food") || lower.contains("eat")) matches.add("delicious food")
        
        if (matches.isNotEmpty()) {
            return matches.distinct().take(2).joinToString(" ")
        }
        
        // Transliterate clean English nouns if no direct match
        val stopwords = setOf(
            "a", "an", "the", "and", "or", "but", "is", "are", "was", "were", "of", "to", "in", "on", "at", "by", "for", "with", "about",
            "this", "that", "it", "its", "you", "your", "my", "me", "we", "our", "us", "they", "them", "some", "any", "no", "not", "so"
        )
        val words = lower.replace(Regex("[^a-zA-Z\\s]"), "")
            .split(Regex("\\s+"))
            .filter { it.isNotEmpty() && !stopwords.contains(it) }
        
        return if (words.isNotEmpty()) {
            words.take(2).joinToString(" ")
        } else {
            "beautiful background scenery"
        }
    }

    fun fallbackLocalParser(script: String, style: String, language: String = "English", aspectRatio: String = "9:16", imageSource: String = "Unsplash"): List<Scene> {
        val trimmed = script.trim()
        if (trimmed.isEmpty()) {
            val defaultPrompt = "A beautiful and inspiring scenery in $style style"
            return listOf(
                Scene(
                    sceneNumber = 1,
                    narrationText = if (language.equals("Hindi", ignoreCase = true)) "हमारी रचनात्मक यात्रा की शुरुआत हो रही है।" else "Starting our creative journey.",
                    visualPrompt = defaultPrompt,
                    durationSeconds = 5,
                    subtitle = if (language.equals("Hindi", ignoreCase = true)) "हमारी रचनात्मक यात्रा शुरू हो रही है..." else "Starting our journey...",
                    mediaPath = getBestMatchingImage(defaultPrompt, style, 0, "", aspectRatio, imageSource).also { url ->
                        // Store the URL
                    },
                    remoteUrl = getBestMatchingImage(defaultPrompt, style, 0, "", aspectRatio, imageSource)
                )
            )
        }

        // Split intelligently by English or Hindi punctuation, symbols, and double newlines
        // Protect web entities (domain.com), decimals (3.5), and abbreviations which lack following whitespace & capital/letter context
        val initialSentences = trimmed.split(Regex("(?<=[?!।|\n])\\s+|(?<=\\.)\\s+(?=[A-Za-z\\u0900-\\u097F]|\\$)"))
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        // Expand/split further any sentence that is too long (over 10 words or 60 characters) to get active scenes updates
        val sentences = mutableListOf<String>()
        for (item in initialSentences) {
            val words = item.split("\\s+".toRegex()).filter { it.isNotBlank() }
            if (words.size > 10) {
                // Split by commas, semicolons, native punctuation or key conjunction markers (Hindi "और", "तो", English "and", "or", "but")
                val subParts = item.split(Regex("(?<=[,;，、])\\s+|\\s+(और|तो|तथा|लेकिन|and|or|but)\\s+"))
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                if (subParts.size > 1) {
                    sentences.addAll(subParts)
                } else {
                    // Fallback to chunk every 8-10 words
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

        return sentences.mapIndexed { index, sentence ->
            val sceneNum = index + 1
            val searchQuery = extractSemiSemanticKeyword(sentence)
            val visPrompt = "Captivating artistic representation of '$searchQuery', $style style"
            
            val processedSentence = translateLocalToLanguage(sentence, language)
            
            val resolvedUrl = getBestMatchingImage(visPrompt, style, index, searchQuery, aspectRatio, imageSource)
            val sc = Scene(
                sceneNumber = sceneNum,
                narrationText = processedSentence,
                visualPrompt = visPrompt,
                durationSeconds = kotlin.math.max(4, processedSentence.length / 10),
                subtitle = processedSentence,
                mediaPath = resolvedUrl,
                remoteUrl = resolvedUrl
            )
            sc.keywords = listOf(searchQuery)
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
        sceneNum: Int = 0
    ): String {
        val apiKey = pixabayApiKey.trim()
        if (apiKey.isEmpty()) return ""

        try {
            val orientation = when (aspectRatio) {
                "9:16", "4:5", "3:4" -> "vertical"
                "16:9", "21:9" -> "horizontal"
                else -> "all"
            }
            val queryText = query.replace(Regex("[^\\p{L}\\p{N}\\s]"), "")
            val encodedQuery = java.net.URLEncoder.encode(queryText, "UTF-8")
            val url = "https://pixabay.com/api/?key=$apiKey&q=$encodedQuery&image_type=photo&orientation=$orientation&per_page=15"
            
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
                    val resultIndex = sceneNum % hits.length()
                    val hitObj = hits.optJSONObject(resultIndex)
                    val imageUrl = hitObj?.optString("largeImageURL") ?: hitObj?.optString("webformatURL")
                    if (!imageUrl.isNullOrEmpty()) {
                        val safeImageUrl = if (imageUrl.startsWith("http://")) imageUrl.replace("http://", "https://") else imageUrl
                        Log.d("GeminiService", "Official Pixabay search success for '$queryText': $safeImageUrl")
                        return safeImageUrl
                    }
                }
            } else {
                Log.w("GeminiService", "Pixabay API query failed with response code ${response.code}")
            }
        } catch (e: Exception) {
            Log.e("GeminiService", "Failed to search official Pixabay API for '$query'", e)
        }
        return ""
    }

    fun searchSpoonacularApi(
        query: String,
        spoonacularApiKey: String,
        searchVideo: Boolean,
        sceneNum: Int = 0
    ): String {
        val apiKey = spoonacularApiKey.trim()
        if (apiKey.isEmpty()) {
            Log.w("GeminiService", "[SPOONACULAR API SKIPPED] API key is empty.")
            return ""
        }

        try {
            val queryText = query.replace(Regex("[^\\p{L}\\p{N}\\s]"), "")
            val encodedQuery = java.net.URLEncoder.encode(queryText, "UTF-8")
            
            val url = if (searchVideo) {
                "https://api.spoonacular.com/food/videos/search?apiKey=$apiKey&query=$encodedQuery&number=10"
            } else {
                "https://api.spoonacular.com/recipes/complexSearch?apiKey=$apiKey&query=$encodedQuery&number=10"
            }
            
            Log.d("GeminiService", "[SPOONACULAR API REQUEST] query='$query' -> queryText='$queryText', searchVideo=$searchVideo, url=$url")
            
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0")
                .build()
            
            val response = httpClient.newCall(request).execute()
            Log.d("GeminiService", "[SPOONACULAR API RESPONSE CODE] code=${response.code}")
            
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
                            if (!thumbnail.isNullOrEmpty()) {
                                return thumbnail
                            }
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
                            Log.d("GeminiService", "Spoonacular recipe photo success: $safeImageUrl")
                            return safeImageUrl
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("GeminiService", "Failed to search Spoonacular API for '$query'", e)
        }
        return ""
    }

    fun searchPexelsApi(
        query: String,
        pexelsApiKey: String,
        searchVideo: Boolean,
        aspectRatio: String = "9:16",
        sceneNum: Int = 0
    ): String {
        val apiKey = pexelsApiKey.trim()
        if (apiKey.isEmpty()) {
            Log.w("GeminiService", "[PEXELS API SKIPPED] API key is empty.")
            return ""
        }

        try {
            val orientation = when (aspectRatio) {
                "9:16", "4:5", "3:4" -> "portrait"
                "16:9", "21:9" -> "landscape"
                else -> "square"
            }
            val queryText = query.replace(Regex("[^\\p{L}\\p{N}\\s]"), "")
            val encodedQuery = java.net.URLEncoder.encode(queryText, "UTF-8")
            
            val url = if (searchVideo) {
                "https://api.pexels.com/videos/search?query=$encodedQuery&orientation=$orientation&per_page=15"
            } else {
                "https://api.pexels.com/v1/search?query=$encodedQuery&orientation=$orientation&per_page=15"
            }
            
            Log.d("GeminiService", "[PEXELS API REQUEST] query='$query' -> queryText='$queryText', searchVideo=$searchVideo, orientation=$orientation, sceneNum=$sceneNum, keyLength=${apiKey.length}, url=$url")
            
            val request = Request.Builder()
                .url(url)
                .header("Authorization", apiKey)
                .header("User-Agent", "Mozilla/5.0")
                .build()
            
            val response = httpClient.newCall(request).execute()
            Log.d("GeminiService", "[PEXELS API RESPONSE CODE] code=${response.code}, message=${response.message}")
            
            if (response.isSuccessful) {
                val bodyString = response.body?.string() ?: ""
                Log.d("GeminiService", "[PEXELS API RESPONSE BODY LENGTH] length=${bodyString.length}")
                val json = JSONObject(bodyString)
                if (searchVideo) {
                    val videos = json.optJSONArray("videos")
                    val totalResults = json.optInt("total_results", -1)
                    Log.d("GeminiService", "[PEXELS VIDEO RESULTS] total_results=$totalResults, arrayLength=${videos?.length() ?: 0}")
                    
                    if (videos != null && videos.length() > 0) {
                        val resultIndex = sceneNum % videos.length()
                        val videoObj = videos.optJSONObject(resultIndex)
                        Log.d("GeminiService", "[PEXELS SELECTED VIDEO OBJECT] index=$resultIndex out of ${videos.length()} items. Video ID: ${videoObj?.optLong("id")}")
                        
                        val videoFiles = videoObj?.optJSONArray("video_files")
                        var mp4Url = ""
                        Log.d("GeminiService", "[PEXELS VIDEO FILES COUNT] Number of files found: ${videoFiles?.length() ?: 0}")
                        
                        if (videoFiles != null && videoFiles.length() > 0) {
                            for (vIdx in 0 until videoFiles.length()) {
                                val fileObj = videoFiles.optJSONObject(vIdx) ?: continue
                                val link = fileObj.optString("link")
                                val quality = fileObj.optString("quality")
                                val fileType = fileObj.optString("file_type")
                                Log.d("GeminiService", "[PEXELS FILE #$vIdx] quality=$quality, type=$fileType, link=$link")
                                if (!link.isNullOrEmpty() && (link.contains(".mp4") || fileType == "video/mp4")) {
                                    val safeLink = if (link.startsWith("http://")) link.replace("http://", "https://") else link
                                    mp4Url = safeLink
                                    Log.d("GeminiService", "[PEXELS SELECTED MP4] Matched link: $safeLink")
                                    break
                                }
                            }
                        }
                        if (mp4Url.isNotEmpty()) {
                            Log.d("GeminiService", "[PEXELS VIDEO MATCH SUCCESS] for '$queryText': $mp4Url")
                            return mp4Url
                        } else {
                            val coverUrl = videoObj?.optString("image") ?: ""
                            Log.d("GeminiService", "[PEXELS VIDEO NO MP4 MATCH] Attempting cover fallback. Raw cover: $coverUrl")
                            if (coverUrl.isNotEmpty()) {
                                val safeCoverUrl = if (coverUrl.startsWith("http://")) coverUrl.replace("http://", "https://") else coverUrl
                                Log.d("GeminiService", "Pexels video files empty, fallback to cover: $safeCoverUrl")
                                return safeCoverUrl
                            }
                        }
                    } else {
                        val words = queryText.split(Regex("\\s+")).filter { it.length > 2 }
                        Log.d("GeminiService", "[PEXELS NO VIDEOS FOUND] query text: '$queryText'. Split words: $words")
                        if (words.size > 2) {
                            val simplified = words.take(2).joinToString(" ")
                            Log.d("GeminiService", "Pexels video returned empty for '$queryText'. Retrying with simplified: '$simplified'")
                            return searchPexelsApi(simplified, pexelsApiKey, searchVideo, aspectRatio, sceneNum)
                        }
                    }
                } else {
                    val photos = json.optJSONArray("photos")
                    val totalResults = json.optInt("total_results", -1)
                    Log.d("GeminiService", "[PEXELS PHOTO RESULTS] total_results=$totalResults, arrayLength=${photos?.length() ?: 0}")
                    
                    if (photos != null && photos.length() > 0) {
                        val resultIndex = sceneNum % photos.length()
                        val photoObj = photos.optJSONObject(resultIndex)
                        val srcObj = photoObj?.optJSONObject("src")
                        val imageUrl = srcObj?.optString("original") ?: srcObj?.optString("large")
                        Log.d("GeminiService", "[PEXELS SELECTED PHOTO OBJECT] index=$resultIndex. Image URL: $imageUrl")
                        
                        if (!imageUrl.isNullOrEmpty()) {
                            val safeImageUrl = if (imageUrl.startsWith("http://")) imageUrl.replace("http://", "https://") else imageUrl
                            Log.d("GeminiService", "Pexels photo search success for '$queryText': $safeImageUrl")
                            return safeImageUrl
                        }
                    } else {
                        val words = queryText.split(Regex("\\s+")).filter { it.length > 2 }
                        Log.d("GeminiService", "[PEXELS NO PHOTOS FOUND] query text: '$queryText'. Split words: $words")
                        if (words.size > 2) {
                            val simplified = words.take(2).joinToString(" ")
                            Log.d("GeminiService", "Pexels photo returned empty for '$queryText'. Retrying with simplified: '$simplified'")
                            return searchPexelsApi(simplified, pexelsApiKey, searchVideo = false, aspectRatio, sceneNum)
                        }
                    }
                }
            } else {
                Log.w("GeminiService", "[PEXELS API FAILED] Pexels API query failed with response code ${response.code} and body: ${response.body?.string()}")
            }
        } catch (e: Exception) {
            Log.e("GeminiService", "[PEXELS API EXCEPTION] Failed to search official Pexels API for '$query'", e)
        }
        return ""
    }

    fun searchPixabayVideos(
        query: String,
        pixabayApiKey: String,
        aspectRatio: String = "9:16",
        sceneNum: Int = 0
    ): String {
        val apiKey = pixabayApiKey.trim()
        if (apiKey.isEmpty()) {
            Log.w("GeminiService", "[PIXABAY VIDEO SKIPPED] API key is empty.")
            return ""
        }

        try {
            val queryText = query.replace(Regex("[^\\p{L}\\p{N}\\s]"), "")
            val encodedQuery = java.net.URLEncoder.encode(queryText, "UTF-8")
            val url = "https://pixabay.com/api/videos/?key=$apiKey&q=$encodedQuery&per_page=15"
            
            Log.d("GeminiService", "[PIXABAY VIDEO REQUEST] query='$query' -> queryText='$queryText', sceneNum=$sceneNum, keyLength=${apiKey.length}, url=https://pixabay.com/api/videos/?key=***&q=$encodedQuery&per_page=15")
            
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0")
                .build()
            
            val response = httpClient.newCall(request).execute()
            Log.d("GeminiService", "[PIXABAY VIDEO RESPONSE CODE] code=${response.code}, message=${response.message}")
            
            if (response.isSuccessful) {
                val bodyString = response.body?.string() ?: ""
                Log.d("GeminiService", "[PIXABAY VIDEO RESPONSE BODY LENGTH] length=${bodyString.length}")
                val json = JSONObject(bodyString)
                val hits = json.optJSONArray("hits")
                val totalHits = json.optInt("totalHits", -1)
                Log.d("GeminiService", "[PIXABAY VIDEO RESULTS] totalHits=$totalHits, arrayLength=${hits?.length() ?: 0}")
                
                if (hits != null && hits.length() > 0) {
                    val resultIndex = sceneNum % hits.length()
                    val hitObj = hits.optJSONObject(resultIndex)
                    Log.d("GeminiService", "[PIXABAY SELECTED VIDEO OBJECT] index=$resultIndex out of ${hits.length()} hits. Hit ID: ${hitObj?.optLong("id")}")
                    
                    val videosObj = hitObj?.optJSONObject("videos")
                    if (videosObj != null) {
                        val mediumObj = videosObj.optJSONObject("medium")
                        val smallObj = videosObj.optJSONObject("small")
                        val largeObj = videosObj.optJSONObject("large")
                        val tinyObj = videosObj.optJSONObject("tiny")
                        
                        Log.d("GeminiService", "[PIXABAY VIDEO SIZES AVAILABLE] mediumUrl=${mediumObj?.optString("url")}, smallUrl=${smallObj?.optString("url")}, largeUrl=${largeObj?.optString("url")}, tinyUrl=${tinyObj?.optString("url")}")
                        
                        val rawVideoUrl = mediumObj?.optString("url")
                            ?: smallObj?.optString("url")
                            ?: largeObj?.optString("url")
                            ?: tinyObj?.optString("url")
                            ?: ""
                            
                        if (rawVideoUrl.isNotEmpty()) {
                            val videoUrl = if (rawVideoUrl.startsWith("http://")) rawVideoUrl.replace("http://", "https://") else rawVideoUrl
                            Log.d("GeminiService", "[PIXABAY VIDEO MATCH SUCCESS] url: $videoUrl")
                            return videoUrl
                        }
                    }
                    val pictureId = hitObj?.optString("picture_id")
                    Log.d("GeminiService", "[PIXABAY NO VIDEO MATCH IN OBJECT] Attempting picture cover fallback. Picture ID: $pictureId")
                    if (!pictureId.isNullOrEmpty()) {
                        val videoCoverUrl = "https://i.vimeocdn.com/video/${pictureId}_960x540.jpg"
                        Log.d("GeminiService", "Pixabay video files empty, fallback to cover: $videoCoverUrl")
                        return videoCoverUrl
                    }
                } else {
                    val words = queryText.split(Regex("\\s+")).filter { it.length > 2 }
                    Log.d("GeminiService", "[PIXABAY NO VIDEOS FOUND] query text: '$queryText'. Split words: $words")
                    if (words.size > 2) {
                        val simplified = words.take(2).joinToString(" ")
                        Log.d("GeminiService", "Pixabay video returned empty for '$queryText'. Retrying with simplified: '$simplified'")
                        return searchPixabayVideos(simplified, pixabayApiKey, aspectRatio, sceneNum)
                    }
                }
            } else {
                Log.w("GeminiService", "[PIXABAY VIDEO API FAILED] Pixabay Video API query failed with response code ${response.code} and body: ${response.body?.string()}")
            }
        } catch (e: Exception) {
            Log.e("GeminiService", "[PIXABAY VIDEO API EXCEPTION] Failed to search official Pixabay Video API for '$query'", e)
        }
        return ""
    }

    suspend fun translateNonEnglishToEnglish(query: String, customGeminiKey: String = ""): String = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return@withContext ""
        
        val hasNonAscii = trimmed.any { it.code > 127 }
        if (!hasNonAscii) return@withContext trimmed

        val lower = trimmed.lowercase()
        val localMatch = when {
            lower.contains("पहाड़") || lower.contains("पर्वत") || lower.contains("पहाड़") -> "mountains"
            lower.contains("नदी") || lower.contains("नदियां") -> "river"
            lower.contains("जंगल") || lower.contains("वन") -> "forest"
            lower.contains("पेड़") || lower.contains("पौधे") || lower.contains("पेड़") -> "trees"
            lower.contains("समुद्र") || lower.contains("सागर") || lower.contains("लहर") -> "ocean beach"
            lower.contains("सूर्यास्त") || lower.contains("शाम") -> "sunset"
            lower.contains("सूर्योदय") || lower.contains("सुबह") -> "sunrise"
            lower.contains("चांद") || lower.contains("चंद्रमा") -> "moon"
            lower.contains("सूरज") || lower.contains("धूप") -> "sun sunbeams"
            lower.contains("बादल") || lower.contains("आसमान") || lower.contains("आकाश") -> "clouds sky"
            lower.contains("अंतरिक्ष") || lower.contains("ब्रह्मांड") -> "outer space"
            lower.contains("पैसा") || lower.contains("रुपया") || lower.contains("धन") -> "money cash"
            lower.contains("कार") || lower.contains("गाड़ी") || lower.contains("गाड़ी") -> "luxury car driving"
            lower.contains("घर") || lower.contains("महल") -> "beautiful house cozy"
            lower.contains("कम्प्यूटर") || lower.contains("लैपटॉप") || lower.contains("कंप्यूटर") -> "computer laptop"
            lower.contains("लोग") || lower.contains("भीड़") || lower.contains("भीड़") -> "group of happy people"
            lower.contains("लड़का") || lower.contains("पुरुष") -> "man young"
            lower.contains("लड़की") || lower.contains("महिला") || lower.contains("लड़की") -> "woman young"
            lower.contains("बच्चा") || lower.contains("बच्चे") -> "happy children"
            lower.contains("सोना") || lower.contains("अमीर") -> "gold rich wealth"
            lower.contains("किताब") || lower.contains("किताबें") || lower.contains("पढ़ना") -> "books study reading"
            lower.contains("शेर") || lower.contains("जानवर") -> "wild lion predator"
            lower.contains("खाना") || lower.contains("स्वादिष्ट") -> "delicious food gourmet"
            lower.contains("चाय") || lower.contains("कॉफी") -> "hot tea coffee cup"
            lower.contains("मंदिर") -> "ancient sacred temple"
            lower.contains("भारत") || lower.contains("भारतीय") -> "india majestic"
            lower.contains("फूल") || lower.contains("गुलाब") -> "beautiful flowers garden"
            lower.contains("अग्नि") || lower.contains("आग") -> "bonfire dramatic flame"
            lower.contains("पानी") || lower.contains("जल") -> "clean water refresh"
            lower.contains("हवा") || lower.contains("तूफान") -> "wind storm dramatic"
            lower.contains("सड़क") || lower.contains("रास्ता") || lower.contains("सड़क") -> "road highway scenic"
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

        // 1. Try REST API first with developer's direct key (highly preferred for cost savings on low usage)
        if (isApiKeyValid(apiKey)) {
            val modelsToTry = listOf(
                "gemini-3.5-flash",
                "gemini-2.0-flash",
                "gemini-1.5-flash",
                "gemini-2.0-flash-lite",
                "gemini-1.5-flash-8b"
            )

            var forceVertexAI = false
            for (modelName in modelsToTry) {
                if (forceVertexAI) {
                    Log.w("GeminiService", "Bypassing remaining REST models for translateNonEnglishToEnglish due to Priority API Gateway trigger.")
                    break
                }
                try {
                    Log.i("GeminiService", "Attempting direct REST API for translateNonEnglishToEnglish with model: $modelName")
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

                    val url = "https://generativelanguage.googleapis.com/v1beta/models/$modelName:generateContent?key=$apiKey"
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
                                translatedText = parts.getJSONObject(0).optString("text", "").trim()
                                Log.i("GeminiService", "Direct REST API translateNonEnglishToEnglish completed successfully with model $modelName: '$translatedText'")
                                break
                            }
                        }
                    } else {
                        Log.w("GeminiService", "REST translation failed for model $modelName with response code: ${response.code}")
                        if (PriorityApiGateway.shouldSwitchToVertexAI(response.code)) {
                            forceVertexAI = true
                            break
                        }
                    }
                } catch (e: Exception) {
                    Log.w("GeminiService", "REST translation failed for model $modelName: ${e.message}")
                }
            }
        } else {
            Log.w("GeminiService", "No direct REST API key is available or key is default placeholder.")
        }

        // 2. Try Firebase Vertex AI SDK next if REST API failed or returned empty text
        if (translatedText.isEmpty()) {
            val firebaseModels = listOf(
                "gemini-3.5-flash",
                "gemini-2.0-flash",
                "gemini-1.5-flash",
                "gemini-2.0-flash-lite",
                "gemini-1.5-flash-8b"
            )
            Log.i("GeminiService", "Attempting Firebase AI Logic SDK fallback for translateNonEnglishToEnglish...")
            for (firebaseModel in firebaseModels) {
                try {
                    Log.i("GeminiService", "Attempting Firebase AI translation with model: $firebaseModel")
                    val model = Firebase.ai(backend = GenerativeBackend.vertexAI(location = "global"))
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
                        translatedText = result
                        PriorityApiGateway.logSuccessfulFailover("Keyword Translation", firebaseModel)
                        Log.i("GeminiService", "Firebase AI Logic SDK translateNonEnglishToEnglish completed successfully with model: $firebaseModel")
                        break
                    }
                } catch (fEx: Exception) {
                    Log.w("GeminiService", "Firebase translation failed with model $firebaseModel: ${fEx.message}")
                }
            }
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
        if (localExtracted.isNotEmpty() && localExtracted != "beautiful background scenery") {
            return@withContext localExtracted
        }
        val englishOnly = trimmed.replace(Regex("[^a-zA-Z0-9\\s]"), "").trim()
        if (englishOnly.isNotEmpty()) {
            return@withContext englishOnly
        }
        return@withContext "beautiful background scenery"
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
            
            CRITICAL COMPLETION REQUIREMENT (NEVER CUT OFF):
            - The script MUST have a complete, cohesive, and satisfying ending. 
            - It MUST NOT end abruptly, freeze in the middle, or cut off mid-sentence.
            - Ensure that you plan your writing so the entire topic is perfectly covered and brought to a natural conclusion within the specified target length limit.
            - The final line MUST be a fully complete and rounded closing sentence.
        """.trimIndent()

        val apiKey = getCleanApiKey(customGeminiKey)

        var resultText = ""

        // 1. Try REST API first with developer's direct key (highly preferred for cost savings on low usage)
        if (isApiKeyValid(apiKey)) {
            val modelsToTry = listOf(
                "gemini-3.5-flash",
                "gemini-2.0-flash",
                "gemini-1.5-flash",
                "gemini-2.0-flash-lite",
                "gemini-1.5-flash-8b"
            )

            var forceVertexAI = false
            for (modelName in modelsToTry) {
                if (forceVertexAI) {
                    Log.w("GeminiService", "Bypassing remaining REST models for script generation due to Priority API Gateway trigger.")
                    break
                }
                try {
                    Log.i("GeminiService", "Attempting direct REST API for generateScriptFromTopic with model: $modelName")
                    val systemInstructionJson = JSONObject().apply {
                        put("parts", JSONArray().put(JSONObject().apply {
                            put("text", "You are an expert video content scriptwriter. Your job is to write a cohesive, fully finished script of the requested length with a complete beginning, middle, and end, with no cut-offs.")
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
                            put("temperature", 0.7)
                            put("maxOutputTokens", 2048)
                        })
                    }

                    val mediaType = "application/json; charset=utf-8".toMediaType()
                    val requestBody = requestBodyJson.toString().toRequestBody(mediaType)

                    val url = "https://generativelanguage.googleapis.com/v1beta/models/$modelName:generateContent?key=$apiKey"
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
                                val generatedText = parts.getJSONObject(0).optString("text", "").trim()
                                if (generatedText.isNotEmpty()) {
                                    var cleanedText = generatedText
                                    if (cleanedText.startsWith("```")) {
                                        val lines = cleanedText.lines()
                                        if (lines.size >= 2) {
                                            cleanedText = lines.subList(1, lines.size - 1).joinToString("\n")
                                        }
                                    }
                                    resultText = cleanedText.trim()
                                    Log.i("GeminiService", "Direct REST API generateScriptFromTopic completed successfully with model: $modelName")
                                    break
                                }
                            }
                        }
                    } else {
                        Log.w("GeminiService", "REST script generation failed for model $modelName with response code: ${response.code}")
                        if (PriorityApiGateway.shouldSwitchToVertexAI(response.code)) {
                            forceVertexAI = true
                            break
                        }
                    }
                } catch (e: Exception) {
                    Log.w("GeminiService", "REST script generation failed for model $modelName: ${e.message}")
                }
            }
        } else {
            Log.w("GeminiService", "No direct REST API key is available or key is default placeholder.")
        }

        // 2. Try Firebase Vertex AI SDK next if REST API failed or was not configured
        if (resultText.isEmpty()) {
            val firebaseModels = listOf(
                "gemini-3.5-flash",
                "gemini-2.0-flash",
                "gemini-1.5-flash",
                "gemini-2.0-flash-lite",
                "gemini-1.5-flash-8b"
            )
            Log.i("GeminiService", "Attempting Firebase AI Logic SDK fallback for generateScriptFromTopic...")
            for (firebaseModel in firebaseModels) {
                try {
                    Log.i("GeminiService", "Attempting Firebase AI script generation with model: $firebaseModel")
                    val model = Firebase.ai(backend = GenerativeBackend.vertexAI(location = "global"))
                        .generativeModel(
                            modelName = firebaseModel,
                            generationConfig = aiGenerationConfig {
                                temperature = 0.7f
                                maxOutputTokens = 2048
                            },
                            systemInstruction = aiContent {
                                text("You are an expert video content scriptwriter. Your job is to write a cohesive, fully finished script of the requested length with a complete beginning, middle, and end, with no cut-offs.")
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
                        resultText = cleanedText.trim()
                        PriorityApiGateway.logSuccessfulFailover("Script Generation", firebaseModel)
                        Log.i("GeminiService", "Firebase AI Logic SDK script generation completed successfully with model: $firebaseModel")
                        break
                    }
                } catch (fEx: Exception) {
                    Log.w("GeminiService", "Firebase script generation failed with model $firebaseModel: ${fEx.message}")
                }
            }
        }

        if (resultText.isNotEmpty()) {
            return@withContext resultText
        }

        // 3. Dynamic Local fallback if both failed completely
        Log.w("GeminiService", "Both REST and Firebase script generation failed. Using local script dynamic fallback.")
        return@withContext "Failed to generate script via AI. Please check your internet connection or configure your API Key. As a graceful fallback, here is a starting narration for your topic: '$topicDescription'. This topic is highly interesting. We hope you can explore and expand this topic further into amazing content. Keep creating and sharing with your audience!"
    }

    fun searchWikimediaCommons(query: String, sceneNum: Int): String {
        try {
            val qClean = query.replace(Regex("[^a-zA-Z0-9\\s]"), "")
            val encoded = java.net.URLEncoder.encode(qClean, "UTF-8")
            val url = "https://commons.wikimedia.org/w/api.php?action=query&format=json&generator=search&gsrsearch=$encoded&gsrnamespace=6&prop=imageinfo&iiprop=url&gsrlimit=15&origin=*"
            
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0")
                .build()
            
            val response = httpClient.newCall(request).execute()
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
                        val pageObj = pages.optJSONObject(key)
                        val imageinfo = pageObj?.optJSONArray("imageinfo")
                        if (imageinfo != null && imageinfo.length() > 0) {
                            val directUrl = imageinfo.optJSONObject(0)?.optString("url")
                            if (!directUrl.isNullOrEmpty()) {
                                urlsList.add(directUrl)
                            }
                        }
                    }
                    if (urlsList.isNotEmpty()) {
                        val index = sceneNum % urlsList.size
                        val selectedUrl = urlsList[index]
                        Log.d("GeminiService", "Wikimedia Commons API search success for '$qClean': $selectedUrl")
                        return selectedUrl
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("GeminiService", "Wikimedia Commons search exception for query '$query'", e)
        }
        return ""
    }

    fun searchNekosBest(query: String, sceneNum: Int): String {
        try {
            val q = query.lowercase()
            // Map keywords to Nekos.best categories/actions for context-aware search matches
            val category = when {
                q.contains("cry") || q.contains("sad") || q.contains("tear") -> "cry"
                q.contains("smile") || q.contains("grin") || q.contains("laugh") || q.contains("smile") -> "smile"
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

            val url = "https://nekos.best/api/v2/$category?amount=15"
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
                    val item = results.optJSONObject(index)
                    val imgUrl = item?.optString("url")
                    if (!imgUrl.isNullOrEmpty()) {
                        Log.d("GeminiService", "Nekos.best search success for category '$category': $imgUrl")
                        return imgUrl
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("GeminiService", "Nekos.best API search exception", e)
        }
        return ""
    }

    fun searchJikanAnime(query: String, sceneNum: Int): String {
        try {
            val qClean = query.replace(Regex("[^a-zA-Z0-9\\s]"), "").trim()
            if (qClean.isEmpty()) return ""
            val encoded = java.net.URLEncoder.encode(qClean, "UTF-8")
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
                        Log.d("GeminiService", "Jikan MyAnimeList DB search success for query '$qClean': $imageUrl")
                        return imageUrl
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("GeminiService", "Jikan MyAnimeList API search exception for query '$query'", e)
        }
        return ""
    }

    fun searchArchiveOrg(query: String, sceneNum: Int): String {
        try {
            val qClean = query.replace(Regex("[^a-zA-Z0-9\\s]"), "").trim()
            if (qClean.isEmpty()) return ""
            val encoded = java.net.URLEncoder.encode(qClean, "UTF-8")
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
                        Log.d("GeminiService", "Archive.org search success for query '$qClean': $imgUrl")
                        return imgUrl
                    }
                }
            }
            // Fallback search with simplified query if no results
            val urlFallback = "https://archive.org/advancedsearch.php?q=$encoded&fl[]=identifier&rows=10&output=json"
            val reqFallback = Request.Builder()
                .url(urlFallback)
                .header("User-Agent", "Mozilla/5.0")
                .build()
            val respFallback = httpClient.newCall(reqFallback).execute()
            if (respFallback.isSuccessful) {
                val bodyString = respFallback.body?.string() ?: ""
                val json = JSONObject(bodyString)
                val responseObj = json.optJSONObject("response")
                val docs = responseObj?.optJSONArray("docs")
                if (docs != null && docs.length() > 0) {
                    val index = sceneNum % docs.length()
                    val doc = docs.optJSONObject(index)
                    val identifier = doc?.optString("identifier")
                    if (!identifier.isNullOrEmpty()) {
                        val imgUrl = "https://archive.org/services/img/$identifier"
                        Log.d("GeminiService", "Archive.org fallback search success for query '$qClean': $imgUrl")
                        return imgUrl
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("GeminiService", "Archive.org API search exception for query '$query'", e)
        }
        return ""
    }

    fun searchNasaLibrary(query: String, sceneNum: Int, isVideo: Boolean): String {
        try {
            val qClean = query.replace(Regex("[^a-zA-Z0-9\\s]"), "").trim()
            if (qClean.isEmpty()) return ""
            val encoded = java.net.URLEncoder.encode(qClean, "UTF-8")
            val mediaType = if (isVideo) "video" else "image"
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
                                    Log.d("GeminiService", "NASA image URL resolved: $finalUrl")
                                    return finalUrl
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("GeminiService", "NASA API search exception for query '$query'", e)
        }
        return ""
    }

    fun searchGiphyMemes(query: String, sceneNum: Int): String {
        try {
            val qClean = query.replace(Regex("[^a-zA-Z0-9\\s]"), "").trim()
            if (qClean.isEmpty()) return ""
            val encoded = java.net.URLEncoder.encode(qClean, "UTF-8")
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
                        Log.d("GeminiService", "Giphy search success for '$qClean': $gifUrl")
                        return gifUrl
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("GeminiService", "Giphy search exception for query '$query'", e)
        }
        return ""
    }

    fun searchNhtsaVpicApi(query: String, sceneNum: Int): String {
        try {
            val qClean = query.replace(Regex("[^a-zA-Z0-9\\s]"), "").trim()
            if (qClean.isEmpty()) return ""
            val words = qClean.split("\\s+".toRegex())
            val defaultMakes = listOf("tesla", "toyota", "ford", "bmw", "ferrari", "chevrolet", "audi", "honda", "nissan", "mercedes")
            var make = defaultMakes[sceneNum % defaultMakes.size]
            
            for (w in words) {
                if (defaultMakes.contains(w.lowercase()) || w.length >= 3) {
                    make = w.lowercase()
                    break
                }
            }
            
            val encodedMake = java.net.URLEncoder.encode(make, "UTF-8")
            val url = "https://vpic.nhtsa.dot.gov/api/vehicles/getmodelsformake/$encodedMake?format=json"
            Log.d("GeminiService", "[NHTSA API REQUEST] make='$make' -> url=$url")
            
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
                        Log.d("GeminiService", "NHTSA success: found vehicle model '$modelName' for make '$make'")
                        val prompt = "A premium luxury cinematic photographic showcase of a $make $modelName, modern automotive design, sunset background, stunning reflection, hyper-detailed, 8k resolution"
                        val encodedPrompt = java.net.URLEncoder.encode(prompt, "UTF-8")
                        return "https://image.pollinations.ai/p/$encodedPrompt?width=1080&height=1920&seed=${modelName.hashCode()}&nologo=true"
                    }
                }
            } else {
                PriorityApiGateway.handleMediaApiError("NHTSA vPIC", response.code, response.message)
            }
        } catch (e: Exception) {
            Log.e("GeminiService", "NHTSA vPIC API search exception for query '$query'", e)
            PriorityApiGateway.handleMediaApiError("NHTSA vPIC", 500, e.message)
        }
        return ""
    }

    fun searchOpenFdaApi(query: String, sceneNum: Int): String {
        try {
            val qClean = query.replace(Regex("[^a-zA-Z0-9\\s]"), "").trim()
            if (qClean.isEmpty()) return ""
            val encodedQuery = java.net.URLEncoder.encode(qClean, "UTF-8")
            val url = "https://api.fda.gov/drug/label.json?search=description:$encodedQuery&limit=10"
            Log.d("GeminiService", "[OpenFDA API REQUEST] query='$qClean' -> url=$url")
            
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
                        else -> qClean
                    }
                    
                    Log.d("GeminiService", "OpenFDA success: found drug name '$nameToUse'")
                    val prompt = "A professional high-end studio shot of clinical pharmaceutical packaging of $nameToUse medicine bottle, modern minimal healthcare design, bright soft studio light, white background, ultra-premium medical product photograph"
                    val encodedPrompt = java.net.URLEncoder.encode(prompt, "UTF-8")
                    return "https://image.pollinations.ai/p/$encodedPrompt?width=1080&height=1920&seed=${nameToUse.hashCode()}&nologo=true"
                }
            } else {
                PriorityApiGateway.handleMediaApiError("OpenFDA", response.code, response.message)
            }
        } catch (e: Exception) {
            Log.e("GeminiService", "OpenFDA API search exception for query '$query'", e)
            PriorityApiGateway.handleMediaApiError("OpenFDA", 500, e.message)
        }
        return ""
    }

    fun searchWhoApi(query: String, sceneNum: Int): String {
        try {
            val qClean = query.replace(Regex("[^a-zA-Z0-9\\s]"), "").trim()
            val url = if (qClean.isNotEmpty()) {
                val encodedQuery = java.net.URLEncoder.encode(qClean, "UTF-8")
                "https://ghoapi.azureedge.net/api/Indicator?\$filter=contains(IndicatorName,%20'$encodedQuery')"
            } else {
                "https://ghoapi.azureedge.net/api/Indicator"
            }
            Log.d("GeminiService", "[WHO API REQUEST] query='$qClean' -> url=$url")
            
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
            } else {
                PriorityApiGateway.handleMediaApiError("WHO", response.code, response.message)
            }
        } catch (e: Exception) {
            Log.e("GeminiService", "WHO API search exception for query '$query'", e)
            PriorityApiGateway.handleMediaApiError("WHO", 500, e.message)
        }
        return ""
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

        val apiKey = getCleanApiKey(customGeminiKey)

        var normalizedText = ""

        if (isApiKeyValid(apiKey)) {
            val modelsToTry = listOf(
                "gemini-3.5-flash",
                "gemini-2.0-flash",
                "gemini-1.5-flash",
                "gemini-2.0-flash-lite",
                "gemini-1.5-flash-8b"
            )

            var forceVertexAI = false
            for (modelName in modelsToTry) {
                if (forceVertexAI) {
                    break
                }
                try {
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

                    val url = "https://generativelanguage.googleapis.com/v1beta/models/$modelName:generateContent?key=$apiKey"
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
                            normalizedText = parts.getJSONObject(0).optString("text", "").trim()
                            if (normalizedText.isNotEmpty()) {
                                Log.i("GeminiService", "normalizeTopicToEnglish completed successfully via REST model $modelName: '$normalizedText'")
                                break
                            }
                        }
                    } else {
                        val code = response.code
                        if (PriorityApiGateway.shouldSwitchToVertexAI(code)) {
                            forceVertexAI = true
                            break
                        }
                    }
                } catch (e: Exception) {
                    Log.w("GeminiService", "normalizeTopicToEnglish REST Model $modelName failed: ${e.message}")
                }
            }
        }

        if (normalizedText.isEmpty()) {
            val firebaseModels = listOf(
                "gemini-3.5-flash",
                "gemini-2.0-flash",
                "gemini-1.5-flash",
                "gemini-2.0-flash-lite",
                "gemini-1.5-flash-8b"
            )
            for (firebaseModel in firebaseModels) {
                try {
                    val model = com.google.firebase.Firebase.ai(backend = com.google.firebase.ai.type.GenerativeBackend.vertexAI(location = "global"))
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
                        break
                    }
                } catch (fEx: Exception) {
                    Log.w("GeminiService", "normalizeTopicToEnglish Firebase SDK model $firebaseModel failed: ${fEx.message}")
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

        val apiKey = getCleanApiKey(customGeminiKey)
        var decision = ""

        if (isApiKeyValid(apiKey)) {
            val modelsToTry = listOf(
                "gemini-3.5-flash",
                "gemini-2.0-flash",
                "gemini-1.5-flash",
                "gemini-2.0-flash-lite",
                "gemini-1.5-flash-8b"
            )

            var forceVertexAI = false
            for (modelName in modelsToTry) {
                if (forceVertexAI) break
                try {
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

                    val url = "https://generativelanguage.googleapis.com/v1beta/models/$modelName:generateContent?key=$apiKey"
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
                            val text = parts.getJSONObject(0).optString("text", "").trim()
                            if (text.isNotEmpty()) {
                                decision = text
                                Log.i("GeminiService", "shouldReuseCachedScript completed successfully via REST model $modelName: '$decision'")
                                break
                            }
                        }
                    } else {
                        val code = response.code
                        if (PriorityApiGateway.shouldSwitchToVertexAI(code)) {
                            forceVertexAI = true
                            break
                        }
                    }
                } catch (e: Exception) {
                    Log.w("GeminiService", "shouldReuseCachedScript REST Model $modelName failed: ${e.message}")
                }
            }
        }

        if (decision.isEmpty()) {
            val firebaseModels = listOf(
                "gemini-3.5-flash",
                "gemini-2.0-flash",
                "gemini-1.5-flash",
                "gemini-2.0-flash-lite",
                "gemini-1.5-flash-8b"
            )
            for (firebaseModel in firebaseModels) {
                try {
                    val model = com.google.firebase.Firebase.ai(backend = com.google.firebase.ai.type.GenerativeBackend.vertexAI(location = "global"))
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
                        break
                    }
                } catch (fEx: Exception) {
                    Log.w("GeminiService", "shouldReuseCachedScript Firebase SDK model $firebaseModel failed: ${fEx.message}")
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

    suspend fun searchAlternativeSuggestions(
        query: String,
        mediaType: String,
        aspectRatio: String,
        unsplashKey: String,
        pexelsKey: String
    ): List<String> = withContext(Dispatchers.IO) {
        val urls = mutableListOf<String>()
        val queryText = query.replace(Regex("[^\\p{L}\\p{N}\\s]"), "").trim()
        if (queryText.isEmpty()) return@withContext emptyList<String>()
        
        val encodedQuery = try {
            java.net.URLEncoder.encode(queryText, "UTF-8")
        } catch (e: Exception) {
            queryText
        }

        val orientation = when (aspectRatio) {
            "9:16", "4:5", "3:4" -> "portrait"
            "16:9", "21:9" -> "landscape"
            else -> "square"
        }

        // 1. If mediaType is VIDEO, search Pexels Videos
        if (mediaType.uppercase() == "VIDEO") {
            val key = pexelsKey.trim()
            if (key.isNotEmpty()) {
                try {
                    val url = "https://api.pexels.com/videos/search?query=$encodedQuery&orientation=$orientation&per_page=15"
                    val request = Request.Builder()
                        .url(url)
                        .header("Authorization", key)
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
                                val videoFiles = videoObj.optJSONArray("video_files")
                                if (videoFiles != null && videoFiles.length() > 0) {
                                    for (vIdx in 0 until videoFiles.length()) {
                                        val fileObj = videoFiles.optJSONObject(vIdx) ?: continue
                                        val link = fileObj.optString("link")
                                        val fileType = fileObj.optString("file_type")
                                        if (!link.isNullOrEmpty() && (link.contains(".mp4") || fileType == "video/mp4")) {
                                            val safeLink = if (link.startsWith("http://")) link.replace("http://", "https://") else link
                                            urls.add(safeLink)
                                            break
                                        }
                                    }
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e("GeminiService", "Alternative Pexels Video search failed", e)
                }
            }
        } else {
            // It is an IMAGE - Search Unsplash first
            val rawAccessKey = unsplashKey.trim().ifEmpty {
                try { BuildConfig.UNSPLASH_ACCESS_KEY } catch (e: Exception) { "" }
            }.ifEmpty { "J6C-j-OjDXft4dMbIm96LlAX4ZqUuViErwbOENvkt4Q" }
            val accessKey = ApiLoadBalancerService.resolveUnsplashApiKey(rawAccessKey)

            try {
                val url = "https://api.unsplash.com/search/photos?query=$encodedQuery&client_id=$accessKey&per_page=15&orientation=$orientation"
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
                        for (i in 0 until results.length()) {
                            val photoObj = results.optJSONObject(i) ?: continue
                            val urlsObj = photoObj.optJSONObject("urls")
                            val dynamicUrl = urlsObj?.optString("regular") ?: urlsObj?.optString("full") ?: urlsObj?.optString("small")
                            if (!dynamicUrl.isNullOrEmpty()) {
                                urls.add(dynamicUrl)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("GeminiService", "Alternative Unsplash search failed", e)
            }

            // Fallback/additional search: Pexels Images
            val key = pexelsKey.trim()
            if (key.isNotEmpty()) {
                try {
                    val url = "https://api.pexels.com/v1/search?query=$encodedQuery&orientation=$orientation&per_page=15"
                    val request = Request.Builder()
                        .url(url)
                        .header("Authorization", key)
                        .header("User-Agent", "Mozilla/5.0")
                        .build()
                    val response = httpClient.newCall(request).execute()
                    if (response.isSuccessful) {
                        val bodyString = response.body?.string() ?: ""
                        val json = JSONObject(bodyString)
                        val photos = json.optJSONArray("photos")
                        if (photos != null && photos.length() > 0) {
                            for (i in 0 until photos.length()) {
                                val photoObj = photos.optJSONObject(i) ?: continue
                                val srcObj = photoObj.optJSONObject("src")
                                val imageUrl = srcObj?.optString("large") ?: srcObj?.optString("original")
                                if (!imageUrl.isNullOrEmpty()) {
                                    val safeImageUrl = if (imageUrl.startsWith("http://")) imageUrl.replace("http://", "https://") else imageUrl
                                    urls.add(safeImageUrl)
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e("GeminiService", "Alternative Pexels Photo search failed", e)
                }
            }
        }
        return@withContext urls.distinct()
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
            // Also clean inline markdown blocks if present
            cleanedText = cleanedText.replace("```json", "").replace("```", "").trim()
        }
        
        cleanedText = cleanedText.trim()
        if (cleanedText.isEmpty()) return ""
        
        // 2. Fix dangling colons (e.g., "imageSearchQuery": at the end of input or before a brace/bracket/comma)
        cleanedText = cleanedText.replace(Regex("(\"[^\"]+\"\\s*:\\s*)(?=[,\\]\\}]|\\s*$)"), "$1\"\"")
        
        // 3. Remove trailing commas before closing braces/brackets
        cleanedText = cleanedText.replace(Regex(",\\s*(?=[\\]\\}])"), "")
        
        // 4. Auto-close unclosed strings and brackets/braces
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
            // Remove any trailing comma before appending closing brace/bracket
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
}
