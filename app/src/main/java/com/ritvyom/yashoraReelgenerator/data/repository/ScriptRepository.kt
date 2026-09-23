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

    private fun isGenericOrFallbackScript(text: String): Boolean {
        if (text.isBlank()) return true
        val genericMarkers = listOf(
            "The secret to mastering this topic is simpler than most people think",
            "हम सभी जानते हैं कि",
            "Hum sabhi jaante hain ki",
            "बुनियादी बातों को नजरअंदाज",
            "buniyaadi baaton",
            "सफलता की असली कुंजी है",
            "इंटरनेट पर सबसे ज्यादा चर्चा क्यों हो रही है",
            "सनातन धर्म में मिलने पर दो बार",
            "Deep spiritual logic jaan kar aap hairan",
            "Sanatan dharama mein 108 ko poori mala",
            "ke peeche ka sabse bada secret",
            "Did you know that mastering",
            "मुख्य पहलुओं को अगर गहराई से समझा जाए",
            "सबसे जरूरी बात क्या है जो अक्सर लोग नजरअंदाज कर देते हैं"
        )
        return genericMarkers.any { text.contains(it, ignoreCase = true) }
    }

    private suspend fun getCleanApiKey(customKey: String): String {
        if (isApiKeyValid(customKey)) return customKey.trim().removeSurrounding("\"").removeSurrounding("'")
        val savedKey = try {
            preferencesManager.geminiApiKeyFlow.first()
        } catch (e: Exception) {
            ""
        }
        if (isApiKeyValid(savedKey)) return savedKey.trim().removeSurrounding("\"").removeSurrounding("'")
        val buildKey = try {
            BuildConfig.GEMINI_API_KEY
        } catch (e: Exception) {
            ""
        }
        if (isApiKeyValid(buildKey)) return buildKey.trim().removeSurrounding("\"").removeSurrounding("'")
        return ""
    }

    private fun isApiKeyValid(key: String): Boolean {
        val cleaned = key.trim().removeSurrounding("\"").removeSurrounding("'")
        if (cleaned.length < 16) return false
        val lower = cleaned.lowercase(Locale.ROOT)
        if (lower.startsWith("your_") || lower.startsWith("my_") || 
            lower.contains("placeholder") || lower.contains("api_key") ||
            lower.contains("example") || lower.contains("default") ||
            lower.startsWith("<") || lower.endsWith(">") ||
            cleaned.contains(" ") || cleaned.contains("\n") || cleaned.contains("\t") ||
            cleaned == "AIzaSyBgk8mDdx3_hiPvs0MNEGQOj_5Ly29LBMA"
        ) {
            return false
        }
        return true
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
                    val isGeneric = isGenericOrFallbackScript(cachedLocalStorageScript)
                    if (!isGeneric) {
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
                    val isGeneric = isGenericOrFallbackScript(cachedLocalScript.fullScript)
                    if (!isGeneric) {
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

        // 1. Unified API Client with Circuit Breaker (Primary REST -> 1x Auto-Retry -> Secondary Vertex AI)
        val apiKey = getCleanApiKey(customKey)
        val systemInstructionText = "You are an expert video scriptwriter. Your job is to generate highly engaging, production-ready spoken scripts (narration and speech text only) without any visual or audio cue annotations, scene markings, or technical instructions. Standardize formatting with clean headers (Hook, Script Body, Outro) so it is extremely readable and ready for direct voice recording."

        try {
            val generated = com.ritvyom.yashoraReelgenerator.data.remote.UnifiedApiClient.generateContent(
                prompt = prompt,
                systemInstruction = systemInstructionText,
                customApiKey = apiKey
            )
            if (generated.isNotBlank() && !isGenericOrFallbackScript(generated)) {
                fullText = generated
            }
        } catch (e: Exception) {
            Log.w("ScriptRepository", "UnifiedApiClient generateContent threw exception: ${e.message}")
            lastException = e
        }

        // 2. Multi-Provider BYOK Fallback Tier (Groq -> OpenAI -> DeepSeek -> xAI -> Gemini)
        if (fullText.isEmpty() || isGenericOrFallbackScript(fullText)) {
            try {
                val app = com.ritvyom.yashoraReelgenerator.YashoraApplication.getInstance()
                val router = app?.unifiedAiRouter
                if (router != null) {
                    Log.i("ScriptRepository", "Triggering UnifiedAiRouter multi-provider cascade failover...")
                    val req = com.ritvyom.yashoraReelgenerator.data.ai.AiGenerationRequest(
                        prompt = prompt,
                        systemInstruction = systemInstructionText,
                        capability = com.ritvyom.yashoraReelgenerator.data.ai.AiCapability.SCRIPT_GENERATION
                    )
                    val result = router.executeWithFallback(req, allowUnifiedApiFailover = false)
                    if (result.text.isNotBlank() && !isGenericOrFallbackScript(result.text)) {
                        fullText = result.text
                        Log.i("ScriptRepository", "Multi-provider AI cascade succeeded with provider ${result.providerUsed} (${result.modelUsed})!")
                    }
                }
            } catch (routerEx: Exception) {
                Log.w("ScriptRepository", "Multi-provider AI cascade error: ${routerEx.message}")
            }
        }

        val upperLang = language.uppercase(java.util.Locale.ROOT)
        val hasDevanagari = fullText.any { it in '\u0900'..'\u097F' }
        val isGenericFallback = isGenericOrFallbackScript(fullText)
        val isLanguageMismatch = (upperLang == "HINDI" && !hasDevanagari) ||
                                 (upperLang == "HINGLISH" && isGenericFallback) ||
                                 isGenericFallback

        var isTrulyAiGenerated = !isGenericFallback && fullText.isNotEmpty() && !isLanguageMismatch
        // 3. Fallback to smart locally generated template script as graceful rescue mechanism if all APIs fail or output is generic/mismatched
        if (fullText.isEmpty() || isLanguageMismatch) {
            Log.w("ScriptRepository", "AI script generation empty or language mismatched ($language, devanagari=$hasDevanagari, genericFallback=$isGenericFallback). Rescuing with local dynamic template generator.")
            fullText = generateLocalGracefulFallbackScript(topic, duration, tone, language, platform)
            isTrulyAiGenerated = false
        }

        // 4. Save successfully generated script ONLY if truly AI generated (do not pollute cache with fallbacks)
        if (fullText.isNotEmpty()) {
            val parsedResultBeforeCleaning = parseScriptOutput(fullText, topic)
            val cleanedFullText = geminiService.cleanScriptText(fullText)
            val finalFullScript = cleanedFullText.ifEmpty { fullText }

            if (isTrulyAiGenerated) {
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
            } else {
                Log.i("ScriptRepository", "Rescued script provided to user without permanent cache insertion.")
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
        val lowerTopic = topic.lowercase(Locale.ROOT)

        val isSpiritual = lowerTopic.contains("ram") || lowerTopic.contains("राम") ||
                lowerTopic.contains("krishna") || lowerTopic.contains("कृष्ण") ||
                lowerTopic.contains("hanuman") || lowerTopic.contains("हनुमान") ||
                lowerTopic.contains("sanatan") || lowerTopic.contains("सनातन") ||
                lowerTopic.contains("shiva") || lowerTopic.contains("शिव") ||
                lowerTopic.contains("bhagwan") || lowerTopic.contains("god") ||
                lowerTopic.contains("matlab") || lowerTopic.contains("mandir")

        val isMotivation = !isSpiritual && (lowerTopic.contains("motivat") || lowerTopic.contains("success") ||
                lowerTopic.contains("habit") || lowerTopic.contains("goal") || lowerTopic.contains("discipline") ||
                lowerTopic.contains("सफलता") || lowerTopic.contains("आदत"))

        val isFinance = !isSpiritual && !isMotivation && (lowerTopic.contains("money") || lowerTopic.contains("earn") ||
                lowerTopic.contains("invest") || lowerTopic.contains("crypto") || lowerTopic.contains("पैसे") || lowerTopic.contains("कमाई"))

        val titleText: String
        val hookText: String
        val bodyText: String
        val outroText: String

        if (uppercaseLanguage == "HINDI") {
            titleText = topic
            when {
                isSpiritual -> {
                    hookText = "क्या आप जानते हैं कि हमारे सनातन धर्म में मिलने पर दो बार 'राम-राम' ही क्यों बोलते हैं? एक बार या तीन बार क्यों नहीं? इसके पीछे का दिव्य आध्यात्मिक और वैज्ञानिक रहस्य आज जान लीजिए!"
                    bodyText = "हिंदी वर्णमाला के अनुसार 'र' 27वां अक्षर है, 'आ' की मात्रा दूसरा अक्षर है और 'म' 25वां अक्षर है। जब आप 27 + 2 + 25 को जोड़ते हैं, तो योग बनता है 54। और जब हम दो बार प्रेम से 'राम-राम' कहते हैं, तो 54 + 54 मिलकर बनता है 108! हमारे शास्त्रों में 108 की संख्या को अत्यंत पवित्र और पूर्ण माला का प्रतीक माना गया है। यानी सिर्फ दो बार 'राम-राम' कहने से संपूर्ण 108 मंत्र जाप का पुण्य फल प्राप्त हो जाता है। यह केवल एक अभिवादन नहीं, बल्कि सकारात्मक ऊर्जा का दिव्य महामंत्र है।"
                    outroText = "अगर यह सुंदर ज्ञान आपको पसंद आया हो, तो कमेंट में 'जय श्री राम' जरूर लिखें और इसे अपनों के साथ साझा करें!"
                }
                isMotivation -> {
                    hookText = "अगर आप जिंदगी में बड़ा मुकाम हासिल करना चाहते हैं, तो $topic का यह नियम आज ही समझ लीजिए!"
                    bodyText = "असली सफलता किसी एक दिन के चमत्कार से नहीं मिलती, बल्कि हर दिन के छोटे-छोटे फैसलों और निरंतर अभ्यास से बनती है। जब आप अपने लक्ष्य पर पूरी एकाग्रता के साथ काम करते हैं, तो समय के साथ आपके परिणाम असाधारण होने लगते हैं। अपनी ऊर्जा सही दिशा में लगाइए और अपनी मेहनत पर अटूट भरोसा रखिए।"
                    outroText = "अगर बात दिल को छूई हो तो वीडियो को लाइक करें, शेयर करें और अपने लक्ष्यों पर डटे रहें!"
                }
                isFinance -> {
                    hookText = "$topic को लेकर सबसे बड़ा वित्तीय नियम जो आपकी सोचने का तरीका हमेशा के लिए बदल देगा!"
                    bodyText = "धनवान बनने का असली रहस्य सिर्फ पैसा कमाना नहीं, बल्कि कमाए हुए पैसे को सही तरीके से सुरक्षित रखना और निवेश करना है। जब आप फिजूलखर्ची को नियंत्रित करके अपनी संपत्ति को सही जगह लगाते हैं, तो कंपाउंडिंग की शक्ति समय के साथ आपके भविष्य को मजबूत बना देती है।"
                    outroText = "ऐसी ही उपयोगी और सच्ची वित्तीय जानकारियों के लिए हमारे चैनल को फॉलो और सब्सक्राइब करें!"
                }
                else -> {
                    hookText = "क्या आप जानते हैं कि $topic के बारे में सबसे जरूरी बात क्या है जो अक्सर लोग नजरअंदाज कर देते हैं?"
                    bodyText = "जब हम $topic के मुख्य बिंदुओं को समझते हैं, तो यह साफ हो जाता है कि सही जानकारी और स्पष्ट नजरिया ही इंसान को आगे बढ़ाता है। जो लोग समय के साथ सीखते हैं और अपने ज्ञान को अमल में लाते हैं, वे हमेशा दूसरों से एक कदम आगे रहते हैं।"
                    outroText = "अगर आपको यह वीडियो रोचक और उपयोगी लगी, तो लाइक करें, शेयर करें और ऐसी ही जानकारियों के लिए जुड़े रहें!"
                }
            }
        } else if (uppercaseLanguage == "HINGLISH") {
            titleText = topic
            when {
                isSpiritual -> {
                    hookText = "Kya aap jante hain ki milne par hum do baar 'Ram-Ram' hi kyun bolte hain? Iske peeche ka spiritual aur scientific logic sunkar aap dang reh jayenge!"
                    bodyText = "Hindi varnamala mein 'R' 27th letter hai, 'Aa' 2nd letter hai, aur 'M' 25th letter hai. Jab aap 27 + 2 + 25 ko add karte hain toh total banta hai 54. Aur do baar 'Ram-Ram' bolne par 54 + 54 = 108! Sanatan dharama mein 108 ko poori mala ka divya sankhya maana gaya hai. Yani sirf do baar Ram-Ram kehne se poori 108 manko ki mala ka punya prapt ho jata hai!"
                    outroText = "Agar ye jankari aapko pasand aayi toh comment mein 'Jai Shree Ram' likhein aur sabhi doston ke saath share karein!"
                }
                else -> {
                    hookText = "Kya aap jante hain ki $topic ke peeche ka sabse bada secret kya hai? Chaliye is short video mein samajhte hain!"
                    bodyText = "Jab hum $topic par focus karte hain, toh sabse crucial factor hota hai clarity aur consistency. Bina kisi bhatkav ke agar aap fundamentals par kaam karein, toh results automatically follow karte hain."
                    outroText = "Video pasand aayi ho toh like karein, share karein aur aise hi content ke liye follow karna na bhoolein!"
                }
            }
        } else {
            // Default to English
            titleText = "The Truth About $topic"
            hookText = "Did you know that mastering $topic is much more strategic than most people think? Here is what you need to know today."
            bodyText = "When you break down $topic into its core fundamentals, success becomes a direct result of consistent application. When you focus deliberately and execute every single day, the compounding growth will surprise you."
            outroText = "If you found this insight valuable, hit the like button, share with a friend, and subscribe for more actionable insights!"
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
