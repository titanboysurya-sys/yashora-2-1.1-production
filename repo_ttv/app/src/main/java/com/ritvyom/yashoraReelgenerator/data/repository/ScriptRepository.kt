package com.ritvyom.yashoraReelgenerator.data.repository

import android.content.Context
import com.ritvyom.yashoraReelgenerator.BuildConfig
import com.ritvyom.yashoraReelgenerator.data.local.dao.ScriptDao
import com.ritvyom.yashoraReelgenerator.data.local.PreferencesManager
import com.ritvyom.yashoraReelgenerator.data.local.LocalStorageScriptCache
import com.ritvyom.yashoraReelgenerator.data.remote.PriorityApiGateway
import com.ritvyom.yashoraReelgenerator.data.model.VideoScript
import com.ritvyom.yashoraReelgenerator.data.network.Content
import com.ritvyom.yashoraReelgenerator.data.network.GeminiApiClient
import com.ritvyom.yashoraReelgenerator.data.network.GeminiRequest
import com.ritvyom.yashoraReelgenerator.data.network.GenerationConfig
import com.ritvyom.yashoraReelgenerator.data.network.Part
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.util.Locale
import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import retrofit2.HttpException

class ScriptRepository(
    private val context: Context,
    private val scriptDao: ScriptDao,
    private val preferencesManager: PreferencesManager,
    private val geminiService: com.ritvyom.yashoraReelgenerator.data.remote.GeminiService
) {

    private val localStorageCache = LocalStorageScriptCache(context)

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

    val allScripts: Flow<List<VideoScript>> = scriptDao.getAllScripts()

    val scriptLanguage: Flow<String> = preferencesManager.scriptLanguageFlow
    val scriptPlatform: Flow<String> = preferencesManager.scriptPlatformFlow
    val scriptDuration: Flow<String> = preferencesManager.scriptDurationFlow
    val scriptTone: Flow<String> = preferencesManager.scriptToneFlow

    suspend fun saveScriptLanguage(language: String) = preferencesManager.saveScriptLanguage(language)
    suspend fun saveScriptPlatform(platform: String) = preferencesManager.saveScriptPlatform(platform)
    suspend fun saveScriptDuration(duration: String) = preferencesManager.saveScriptDuration(duration)
    suspend fun saveScriptTone(tone: String) = preferencesManager.saveScriptTone(tone)

    fun getScriptById(id: Int): Flow<VideoScript?> = scriptDao.getScriptById(id)

    suspend fun insertScript(script: VideoScript): Long = scriptDao.insertScript(script)

    suspend fun deleteScript(script: VideoScript) = scriptDao.deleteScript(script)

    suspend fun deleteScriptById(id: Int) = scriptDao.deleteScriptById(id)

    suspend fun updateFavoriteStatus(id: Int, isFavorite: Boolean) = 
        scriptDao.updateFavoriteStatus(id, isFavorite)

    suspend fun generateVideoScript(
        topic: String,
        duration: String,
        tone: String,
        language: String,
        platform: String,
        bypassCache: Boolean = false
    ): Result<GeneratedScriptResult> {
        val customKey = try {
            preferencesManager.geminiApiKeyFlow.first()
        } catch (e: Exception) {
            ""
        }

        // Normalize topic to english standard key to enable semantic, language-agnostic caching
        val normalizedTopic = try {
            geminiService.normalizeTopicToEnglish(topic, customKey)
        } catch (e: Exception) {
            Log.w("ScriptRepository", "Failed to normalize topic to English, using original topic", e)
            topic.trim().lowercase()
        }

        Log.i("ScriptRepository", "Original topic: '$topic' -> Normalized topic: '$normalizedTopic'")

        if (!bypassCache) {
            // 0a. Try localStorage (SharedPreferences) cache first using normalized topic
            try {
                val cachedLocalStorageScript = localStorageCache.getCachedScript(
                    topic = normalizedTopic,
                    tone = tone,
                    language = language,
                    duration = duration
                ) ?: localStorageCache.getCachedScript(
                    topic = topic,
                    tone = tone,
                    language = language,
                    duration = duration
                )
                if (cachedLocalStorageScript != null) {
                    Log.i("ScriptRepository", "localStorage Cache HIT for topic '$normalizedTopic'. Validating semantically...")
                    val parsedResult = parseScriptOutput(cachedLocalStorageScript, topic)
                    val isValid = geminiService.shouldReuseCachedScript(
                        userTopic = topic,
                        userTone = tone,
                        userLanguage = language,
                        userDuration = duration,
                        cachedTone = tone,
                        cachedLanguage = language,
                        cachedDuration = duration,
                        cachedTitle = parsedResult.title,
                        cachedScriptContent = parsedResult.fullScript,
                        customGeminiKey = customKey
                    )
                    if (isValid) {
                        Log.i("ScriptRepository", "localStorage Cache VALIDATED for topic '$topic'")
                        return Result.success(parsedResult)
                    } else {
                        Log.i("ScriptRepository", "localStorage Cache INVALIDATED semantically for topic '$topic'. Bypassing.")
                    }
                }
            } catch (e: Exception) {
                Log.w("ScriptRepository", "Failed to retrieve cached script from localStorage", e)
            }

            // 0b. Try local Room database cache next (offline support & duplicate prevention) using normalized topic
            try {
                val cachedLocalScript = scriptDao.getScriptByParams(
                    topic = normalizedTopic,
                    tone = tone,
                    language = language,
                    duration = duration
                ) ?: scriptDao.getScriptByParams(
                    topic = topic,
                    tone = tone,
                    language = language,
                    duration = duration
                ) ?: scriptDao.getScriptByTopicAndLanguage(normalizedTopic, language)
                  ?: scriptDao.getScriptByTopicAndLanguage(topic, language)

                if (cachedLocalScript != null) {
                    Log.i("ScriptRepository", "Local Room Cache HIT for topic '$normalizedTopic'. Validating semantically...")
                    val isValid = geminiService.shouldReuseCachedScript(
                        userTopic = topic,
                        userTone = tone,
                        userLanguage = language,
                        userDuration = duration,
                        cachedTone = cachedLocalScript.tone,
                        cachedLanguage = cachedLocalScript.language,
                        cachedDuration = cachedLocalScript.duration,
                        cachedTitle = cachedLocalScript.title,
                        cachedScriptContent = cachedLocalScript.fullScript,
                        customGeminiKey = customKey
                    )
                    if (isValid) {
                        Log.i("ScriptRepository", "Local Room Cache VALIDATED for topic '$topic'")
                        // Populate localStorage as well for faster subsequent access
                        try {
                            localStorageCache.saveCachedScript(normalizedTopic, tone, language, duration, cachedLocalScript.fullScript)
                        } catch (ignore: Exception) {}
                        
                        return Result.success(
                            GeneratedScriptResult(
                                title = cachedLocalScript.title,
                                fullScript = cachedLocalScript.fullScript
                            )
                        )
                    } else {
                        Log.i("ScriptRepository", "Local Room Cache INVALIDATED semantically for topic '$topic'. Bypassing.")
                    }
                }
            } catch (e: Exception) {
                Log.w("ScriptRepository", "Failed to retrieve cached script from local Room db", e)
            }

            // 1. Try to fetch from Firestore Cloud Script Cache using normalized topic
            try {
                val cachedCloudScriptResult = com.ritvyom.yashoraReelgenerator.data.remote.FirebaseCloudManager.getCachedScript(
                    topicDescription = normalizedTopic,
                    style = tone,
                    language = language,
                    durationOption = duration
                ) ?: com.ritvyom.yashoraReelgenerator.data.remote.FirebaseCloudManager.getCachedScript(
                    topicDescription = topic,
                    style = tone,
                    language = language,
                    durationOption = duration
                )
                if (cachedCloudScriptResult != null) {
                    Log.i("ScriptRepository", "Cloud Script Cache HIT for topic '$normalizedTopic'. Validating semantically...")
                    val parsedResult = parseScriptOutput(cachedCloudScriptResult.scriptText, topic)
                    val isValid = geminiService.shouldReuseCachedScript(
                        userTopic = topic,
                        userTone = tone,
                        userLanguage = language,
                        userDuration = duration,
                        cachedTone = cachedCloudScriptResult.style,
                        cachedLanguage = cachedCloudScriptResult.language,
                        cachedDuration = cachedCloudScriptResult.duration,
                        cachedTitle = parsedResult.title,
                        cachedScriptContent = parsedResult.fullScript,
                        customGeminiKey = customKey
                    )
                    if (isValid) {
                        Log.i("ScriptRepository", "Cloud Script Cache VALIDATED for topic '$topic'")
                        // Cache it locally too under both keys
                        try {
                            localStorageCache.saveCachedScript(normalizedTopic, tone, language, duration, cachedCloudScriptResult.scriptText)
                            localStorageCache.saveCachedScript(topic, tone, language, duration, cachedCloudScriptResult.scriptText)
                        } catch (ignore: Exception) {}

                        return Result.success(parsedResult)
                    } else {
                        Log.i("ScriptRepository", "Cloud Script Cache INVALIDATED semantically for topic '$topic'. Bypassing.")
                    }
                }
            } catch (e: Exception) {
                Log.w("ScriptRepository", "Failed to retrieve cached script from cloud", e)
            }
        }

        val prompt = buildPrompt(topic, duration, tone, language, platform)
        var fullText = ""
        var lastException: Exception? = null
        var forceVertexAI = false

        // 1. Try REST API first with developer's direct key (highly preferred for cost savings on low usage)
        val apiKey = getCleanApiKey(customKey)

        if (isApiKeyValid(apiKey)) {
            val request = GeminiRequest(
                contents = listOf(Content(parts = listOf(Part(text = prompt)))),
                generationConfig = GenerationConfig(
                    temperature = 0.7f,
                    topP = 0.95f,
                    maxOutputTokens = 2048
                ),
                systemInstruction = Content(
                    parts = listOf(
                        Part(
                            text = "You are an expert video scriptwriter. Your job is to generate highly engaging, production-ready spoken scripts (narration and speech text only) without any visual or audio cue annotations, scene markings, or technical instructions. Standardize formatting with clean headers (Hook, Script Body, Outro) so it is extremely readable and ready for direct voice recording."
                        )
                    )
                )
            )

            val modelsToTry = listOf(
                "gemini-3.5-flash",
                "gemini-2.0-flash",
                "gemini-1.5-flash",
                "gemini-2.0-flash-lite",
                "gemini-1.5-flash-8b"
            )

            for (model in modelsToTry) {
                if (forceVertexAI) {
                    Log.w("ScriptRepository", "Bypassing remaining REST models due to Priority API Gateway trigger.")
                    break
                }
                try {
                    Log.d("ScriptRepository", "Attempting script generation with REST model: $model")
                    val response = GeminiApiClient.service.generateContent(model, apiKey, request)
                    val text = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                    if (text != null && text.isNotBlank()) {
                        fullText = text
                        Log.i("ScriptRepository", "Successfully generated script with REST model: $model")
                        break
                    }
                } catch (e: Exception) {
                    Log.w("ScriptRepository", "REST Model $model failed with exception: ${e.message}")
                    lastException = e
                    if (e is retrofit2.HttpException) {
                        val code = e.code()
                        if (PriorityApiGateway.shouldSwitchToVertexAI(code)) {
                            forceVertexAI = true
                            break
                        }
                    }
                    kotlinx.coroutines.delay(1000)
                }
            }
        } else {
            Log.w("ScriptRepository", "No direct REST API key is available or key is default placeholder.")
        }

        // 2. Fallback to Firebase Vertex AI SDK if direct REST API failed, was rate-limited (429), unauthorized (403), or not configured
        if (fullText.isEmpty()) {
            val firebaseModels = listOf(
                "gemini-3.5-flash",
                "gemini-2.0-flash",
                "gemini-1.5-flash",
                "gemini-2.0-flash-lite",
                "gemini-1.5-flash-8b"
            )
            Log.i("ScriptRepository", "REST failed, triggered fallback, or not configured. Falling back to Firebase AI Vertex SDK...")
            for (firebaseModel in firebaseModels) {
                try {
                    Log.i("ScriptRepository", "Attempting Firebase AI Vertex SDK with model: $firebaseModel")
                    val model = com.google.firebase.Firebase.ai(backend = com.google.firebase.ai.type.GenerativeBackend.vertexAI(location = "global"))
                        .generativeModel(
                            modelName = firebaseModel,
                            generationConfig = com.google.firebase.ai.type.generationConfig {
                                temperature = 0.7f
                                maxOutputTokens = 2048
                            },
                            systemInstruction = com.google.firebase.ai.type.content {
                                text("You are an expert video scriptwriter. Your job is to generate highly engaging, production-ready spoken scripts (narration and speech text only) without any visual or audio cue annotations, scene markings, or technical instructions. Standardize formatting with clean headers (Hook, Script Body, Outro) so it is extremely readable and ready for direct voice recording.")
                            }
                        )
                    val response = model.generateContent(prompt)
                    val text = response.text?.trim() ?: ""
                    if (text.isNotEmpty()) {
                        fullText = text
                        PriorityApiGateway.logSuccessfulFailover("Script Generation", firebaseModel)
                        break
                    }
                } catch (fEx: Exception) {
                    Log.w("ScriptRepository", "Firebase Vertex AI generateVideoScript failed with model $firebaseModel: ${fEx.message}")
                    lastException = fEx
                }
            }
        }

        var isTrulyAiGenerated = true
        // 3. Fallback to a smart locally generated template script as a graceful rescue mechanism if both fail
        if (fullText.isEmpty()) {
            Log.e("ScriptRepository", "All AI script generation attempts failed. Rescuing with local dynamic template generator.")
            fullText = generateLocalGracefulFallbackScript(topic, duration, tone, language, platform)
            isTrulyAiGenerated = false
        }

        // 4. Save successfully generated script (either AI or rescued) to all cache layers to prevent redundant API calls
        if (fullText.isNotEmpty()) {
            val parsedResultBeforeCleaning = parseScriptOutput(fullText, topic)
            val cleanedFullText = geminiService.cleanScriptText(fullText)
            val finalFullScript = cleanedFullText.ifEmpty { fullText }

            // 4a. Save to localStorage (SharedPreferences) cache under both normalized and original topic
            try {
                localStorageCache.saveCachedScript(normalizedTopic, tone, language, duration, finalFullScript)
                localStorageCache.saveCachedScript(topic, tone, language, duration, finalFullScript)
            } catch (e: Exception) {
                Log.w("ScriptRepository", "Failed to save generated script to localStorage cache", e)
            }

            // 4b. Save to local Room database cache
            try {
                val newScript = VideoScript(
                    topic = normalizedTopic,
                    duration = duration,
                    tone = tone,
                    language = language,
                    platform = platform,
                    title = parsedResultBeforeCleaning.title,
                    fullScript = finalFullScript
                )
                scriptDao.insertScript(newScript)
                Log.i("ScriptRepository", "Successfully saved generated script to local Room db under '$normalizedTopic'")
            } catch (e: Exception) {
                Log.w("ScriptRepository", "Failed to save script to local Room db", e)
            }

            return Result.success(GeneratedScriptResult(title = parsedResultBeforeCleaning.title, fullScript = finalFullScript))
        }

        val parsedResult = parseScriptOutput(fullText, topic)
        return Result.success(parsedResult)
    }

    private fun generateLocalGracefulFallbackScript(
        topic: String,
        duration: String,
        tone: String,
        language: String,
        platform: String
    ): String {
        val uppercaseLanguage = language.uppercase(Locale.ROOT)
        
        val titleText: String
        val hookText: String
        val bodyText: String
        val outroText: String

        if (uppercaseLanguage == "HINDI") {
            titleText = "वायरल वीडियो: $topic"
            hookText = "नमस्कार दोस्तों! क्या आपने कभी सोचा है कि $topic के बारे में सबसे बड़ा रहस्य क्या है? आज की इस छोटी सी वीडियो में हम इसी के बारे में बात करेंगे, इसलिए अंत तक बने रहिए!"
            bodyText = "हम सभी जानते हैं कि $topic आज के समय में कितना महत्वपूर्ण है। जब हम इसके गहरे पहलुओं को देखते हैं, तो समझ आता है कि यह हमारी दैनिक जिंदगी को कैसे प्रभावित करता है। इसके पीछे कई रोचक तथ्य हैं जो लोगों को हैरान कर देते हैं। पहली बात तो यह है कि यह आपके देखने के नज़रिए को बदल देता है। दूसरी बात, इस पर सही जानकारी होना आपके लिए बहुत फायदेमंद साबित हो सकता है। इसीलिए एक्सपर्ट्स हमेशा इस पर ध्यान देने की सलाह देते हैं।"
            outroText = "अगर आपको यह वीडियो पसंद आई हो, तो इसे लाइक करें, शेयर करें और हमारे चैनल को सब्सक्राइब करना न भूलें। मिलते हैं अगले वीडियो में, धन्यवाद!"
        } else if (uppercaseLanguage == "HINGLISH") {
            titleText = "Viral Video: $topic"
            hookText = "Namskar doston! Kya aapne kabhi socha hai ki $topic ke baare mein sabse bada sach kya hai? Aaj ke is short video mein hum isi ke baare mein baat karenge, isliye video ko end tak zaroor dekhiye!"
            bodyText = "Hum sabhi jaante hain ki $topic aaj ke time mein kitna important hai. Jab hum iske details ko explore karte hain, toh samajh aata hai ki yeh hamari daily life ko kaise impact karta hai. Iske peeche kai saare interesting facts hain jo logon ko hairan kar dete hain. Pehli baat toh yeh hai ki yeh aapke dekhne ka nazariya change kar deta hai. Aur dusri baat, iski sahi knowledge hona aapke liye bahut beneficial ho sakta hai."
            outroText = "Agar aapko yeh video pasand aayi ho, toh video ko like karein, doston ke saath share karein aur channel ko subscribe karna na bhoolein. Milte hain agle video mein, bye bye!"
        } else {
            // Default to English
            titleText = "Secrets of $topic Exposed!"
            hookText = "Hey everyone! Have you ever wondered what the real truth behind $topic is? In today's video, we are breaking down everything you need to know, so make sure to stick around until the very end!"
            bodyText = "We all know that $topic plays a massive role in today's world. When we dive deeper into the core concepts, it becomes clear how much of an impact it has on our daily decisions. There are several fascinating facts about this that most people completely overlook. First, it completely transforms your perspective on the subject. Second, having a solid understanding of this can give you a massive advantage in your career or daily routine. This is why top industry experts emphasize its value."
            outroText = "If you found this video helpful, make sure to hit that like button, share it with your friends, and subscribe for more bite-sized insights. See you in the next one!"
        }

        return """
            # TITLE: $titleText
            
            ## HOOK
            $hookText
            
            ## SCRIPT BODY
            $bodyText
            
            ## OUTRO & CTA
            $outroText
        """.trimIndent()
    }

    private fun buildPrompt(
        topic: String,
        duration: String,
        tone: String,
        language: String,
        platform: String
    ): String {
        val durationDesc = when (duration.lowercase(Locale.ROOT)) {
            "short" -> "Short Reels/Shorts format (TARGET: exactly 110 to 140 words, fast-paced, high retention, 1 sentence Hook, 2 concise paragraphs of Body, 1 sentence Outro)"
            "medium" -> "Medium video format (TARGET: exactly 350 to 450 words, balanced explanatory pacing, 2-3 sentence Hook, 4-5 well-structured paragraphs of Body, 2-3 sentences Outro & CTA)"
            "long" -> "Long-form/Deep-dive format (TARGET: exactly 900 to 1200 words, comprehensive explanation with details, 4-5 sentence Hook, at least 10 detailed paragraphs of Body explaining concepts thoroughly, 3-4 sentences Outro & CTA)"
            else -> "Format matching $duration"
        }

        val toneDesc = when (tone.lowercase(Locale.ROOT)) {
            "professional" -> "Professional: Formal, structured, precise, authoritative, and business-oriented tone."
            "casual" -> "Casual: Conversational, easygoing, friendly, informal, and relaxed tone."
            "energetic" -> "Energetic: High energy, passionate, fast-paced, highly motivating, and enthusiastic tone."
            "educational" -> "Educational/Informative: Clear explanations, educational facts, engaging delivery."
            "entertaining" -> "Entertaining/Fun: High energy, funny jokes, interactive gestures, relatable style."
            "tech" -> "Tech Explainer: Fact-focused, geeky but clear, simple analogies for complex engineering."
            "dramatic" -> "Dramatic/Storytelling: Suspenseful, storytelling framework, emotional hooks, dramatic pauses."
            "comedy" -> "Humorous/Comedy: Lighthearted, jokes, fun style, extremely casual."
            else -> tone
        }

        val langInstructions = when (language.lowercase(Locale.ROOT)) {
            "hindi" -> "Write entirely in Hindi (Devanagari script), using modern, simple, natural-sounding conversational words. Avoid overly heavy Sanskritized Hindi; use words common in daily conversation."
            "hinglish" -> "Write in Hinglish (Hindi speech written using the English/Latin alphabet). Example: 'Doston, kya aapne kabhi socha hai ki...', 'Motor kaise ghumta hai...'. This is extremely important: use Latin characters (A-Z) but speak conversational Hindi."
            "english" -> "Write entirely in English (standard conversational or technical, depending on the tone)."
            else -> "Write in the requested language ($language)."
        }

        return """
            Write a complete, highly engaging video script on the topic: "$topic"
            
            Platform: $platform
            Target Length & Format Constraints: $durationDesc
            Tone/Style: $toneDesc
            Language Rules: $langInstructions

            CRITICAL VALUE PROPOSITION:
            - The script MUST contain ONLY the actual spoken script text (narration, speech, voiceover, dialogue, host talk) that the creator will read in front of the camera or record as a voiceover.
            
            STRICT RULES - NO TECHNICAL SUGGESTIONS OR SCENES:
            - DO NOT include any Scene markings (e.g. NO [Scene 1], [Scene 2], etc.).
            - DO NOT suggest any Visual cues, camera angles, or on-screen text overlays (e.g. NO "Visuals (दृश्य)", NO "Camera moves", etc.).
            - DO NOT suggest any Audio cues, sound effects, or background music adjustments (e.g. NO "Audio (संगीत)", NO "SFX", etc.).
            - DO NOT include any technical instructions, director suggestions, or editor notes.
            - Just write the raw spoken script content flow directly as continuous paragraphs or readable bullet points, organized logically with simple markdown headings (such as Hook, SCRIPT BODY, Outro).

            CRITICAL COMPLETION REQUIREMENT (NEVER CUT OFF):
            - The script MUST have a complete, cohesive, and satisfying ending. 
            - It MUST NOT end abruptly, freeze in the middle, or cut off mid-sentence.
            - Ensure that you plan your writing so the entire topic is perfectly covered and brought to a natural conclusion within the specified target length limit.
            - The final line under OUTRO & CTA must be a fully complete and rounded closing sentence.

            Please format the output in Markdown EXACTLY as follows:

            # TITLE: [Catchy click-worthy video title]

            ## HOOK
            [Enter the spoken opening lines that will grab user attention immediately. Make it extremely conversational and direct.]

            ## SCRIPT BODY
            [Enter the main body of the spoken script. This should be a direct, continuous stream of spoken paragraphs or lines that the host speaks. Do not add any scenes, visual instructions or cue tags here. Just write the spoken script directly, flowing naturally from point to point.]

            ## OUTRO & CTA
            [Enter the spoken closing remarks and a call to action (like, subscribe, comment).]

            Ensure all text is written out in full conversational sentences so the user can read them directly to record their video. Keep the tone natural, energetic, and friendly! Do not add any extra meta-explanations like "Sure, here is your script" or "Let me know if you need changes." Start directly with the title.
        """.trimIndent()
    }

    private fun parseScriptOutput(fullText: String, topic: String): GeneratedScriptResult {
        val lines = fullText.lines()
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
            title = "Video Script: $topic"
        }

        return GeneratedScriptResult(
            title = title,
            fullScript = fullText
        )
    }
}

data class GeneratedScriptResult(
    val title: String,
    val fullScript: String
)
