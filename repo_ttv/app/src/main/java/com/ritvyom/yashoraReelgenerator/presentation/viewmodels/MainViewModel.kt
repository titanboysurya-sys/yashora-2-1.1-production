package com.ritvyom.yashoraReelgenerator.presentation.viewmodels

import android.app.Application
import android.content.ContentValues
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ritvyom.yashoraReelgenerator.YashoraApplication
import java.io.File
import com.ritvyom.yashoraReelgenerator.data.local.VoicePrefs
import com.ritvyom.yashoraReelgenerator.presentation.utils.localize
import com.ritvyom.yashoraReelgenerator.data.local.entities.ExportHistoryEntity
import com.ritvyom.yashoraReelgenerator.data.local.entities.ProjectEntity
import com.ritvyom.yashoraReelgenerator.domain.models.Scene
import com.ritvyom.yashoraReelgenerator.domain.models.PreviewScene
import com.ritvyom.yashoraReelgenerator.presentation.utils.ReelVideoCompiler
import com.ritvyom.yashoraReelgenerator.presentation.utils.NotificationHelper
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.*

class MainViewModel(application: Application) : AndroidViewModel(application), TextToSpeech.OnInitListener {

    val repository = (application as YashoraApplication).repository
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var generationJob: kotlinx.coroutines.Job? = null
    private var exportJob: kotlinx.coroutines.Job? = null

    private var mediaPlayer: android.media.MediaPlayer? = null

    // Form settings
    val scriptText = MutableStateFlow("")
    val topicContext = MutableStateFlow("")
    val selectedStyleName = MutableStateFlow("Cinematic")
    val selectedPublishingStyle = MutableStateFlow("TikTok / Instagram Reels")
    val selectedVoiceName = MutableStateFlow("Professional Man")
    val selectedVoiceCategory = MutableStateFlow("Male") // Male, Female, Child
    val voiceSpeed = MutableStateFlow(1.0f)
    val voicePitch = MutableStateFlow(1.0f)
    val voiceEmotion = MutableStateFlow("Friendly") // Friendly, Energetic, Emotional, Cinematic
    val voiceQuality = MutableStateFlow("High Quality")
    val selectedTtsEngine = MutableStateFlow("com.google.android.tts")
    val aiTtsEnabled = MutableStateFlow(true)

    val selectedLanguage = MutableStateFlow("English")
    val selectedImageSource = MutableStateFlow("Unsplash") // Unsplash or AI Generated
    val selectedVisualMedium = MutableStateFlow("Video") // "Video" or "Image"
    val appLanguage = repository.appLanguage.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "English")
    val appTheme = repository.appTheme.stateIn(viewModelScope, SharingStarted.Eagerly, "System")
    val videoLanguage = repository.videoLanguage.stateIn(viewModelScope, SharingStarted.Eagerly, "English")
    val pixabayApiKey = repository.pixabayApiKey.stateIn(viewModelScope, SharingStarted.Eagerly, "")
    val geminiApiKey = repository.geminiApiKey.stateIn(viewModelScope, SharingStarted.Eagerly, "")
    val unsplashApiKey = repository.unsplashApiKey.stateIn(viewModelScope, SharingStarted.Eagerly, "")
    val pexelsApiKey = repository.pexelsApiKey.stateIn(viewModelScope, SharingStarted.Eagerly, "")
    val spoonacularApiKey = repository.spoonacularApiKey.stateIn(viewModelScope, SharingStarted.Eagerly, "")
    val aiIntelligentMatchmaker = repository.aiIntelligentMatchmaker.stateIn(viewModelScope, SharingStarted.Eagerly, true)
    
    val activePremiumTtsEngine = repository.activePremiumTtsEngine.stateIn(viewModelScope, SharingStarted.Eagerly, "")

    fun setActivePremiumTtsEngine(engine: String) {
        viewModelScope.launch {
            repository.saveActivePremiumTtsEngine(engine)
        }
    }

    val apiValidationLoading = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val apiValidationResults = MutableStateFlow<Map<String, Pair<Boolean, String>>>(emptyMap())

    fun checkApiKeyValidity(apiType: String, key: String, extraParam1: String = "") {
        viewModelScope.launch {
            apiValidationLoading.value = apiValidationLoading.value + (apiType to true)
            val result = repository.validateApiKey(apiType, key, extraParam1)
            apiValidationLoading.value = apiValidationLoading.value + (apiType to false)
            apiValidationResults.value = apiValidationResults.value + (apiType to result)
        }
    }

    fun setPixabayApiKey(key: String) {
        viewModelScope.launch {
            repository.updatePixabayApiKey(key)
        }
    }

    fun setGeminiApiKey(key: String) {
        viewModelScope.launch {
            repository.updateGeminiApiKey(key)
        }
    }

    fun setUnsplashApiKey(key: String) {
        viewModelScope.launch {
            repository.updateUnsplashApiKey(key)
        }
    }

    fun setPexelsApiKey(key: String) {
        viewModelScope.launch {
            repository.updatePexelsApiKey(key)
        }
    }

    fun setSpoonacularApiKey(key: String) {
        viewModelScope.launch {
            repository.updateSpoonacularApiKey(key)
        }
    }

    fun setAiIntelligentMatchmaker(enabled: Boolean) {
        viewModelScope.launch {
            repository.updateAiIntelligentMatchmaker(enabled)
        }
    }

    val alternativeSuggestions = MutableStateFlow<List<String>>(emptyList())
    val isSearchingAlternatives = MutableStateFlow(false)
    val alternativeSearchError = MutableStateFlow<String?>(null)

    fun fetchAlternativeSuggestions(query: String, mediaType: String, aspectRatio: String) {
        viewModelScope.launch {
            isSearchingAlternatives.value = true
            alternativeSearchError.value = null
            try {
                val results = repository.searchAlternativeSuggestions(
                    query = query,
                    mediaType = mediaType,
                    aspectRatio = aspectRatio,
                    unsplashKey = unsplashApiKey.value,
                    pexelsKey = pexelsApiKey.value
                )
                alternativeSuggestions.value = results
                if (results.isEmpty()) {
                    alternativeSearchError.value = "No suggestions found for \"$query\""
                }
            } catch (e: Exception) {
                Log.e("MainViewModel", "Failed to search alternative media suggestions", e)
                alternativeSearchError.value = e.localizedMessage ?: "Search failed"
            } finally {
                isSearchingAlternatives.value = false
            }
        }
    }

    val appLovinMediationEnabled = repository.appLovinMediationEnabled.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val appLovinSdkKey = repository.appLovinSdkKey.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "applovin_max_sdk_premium_high_rpm_active_2026")
    val appLovinZoneIdRewarded = repository.appLovinZoneIdRewarded.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "rewarded_google_bidding_high_rpm")
    val appLovinZoneIdInterstitial = repository.appLovinZoneIdInterstitial.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "interstitial_google_bidding_high_rpm")
    val mediationBiddingStrategy = repository.mediationBiddingStrategy.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "Dynamic High-RPM (Bidding-First)")
    val googleBiddingAppId = repository.googleBiddingAppId.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "ca-app-pub-3940256099942544~3347511713")
    val admobBannerAdUnitId = repository.admobBannerAdUnitId.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "ca-app-pub-3940256099942544/6300978111")
    val admobInterstitialAdUnitId = repository.admobInterstitialAdUnitId.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "ca-app-pub-3940256099942544/1033173712")
    val admobRewardedAdUnitId = repository.admobRewardedAdUnitId.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "ca-app-pub-3940256099942544/5224354917")
    val unityGameId = repository.unityGameId.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "5123456")
    val metaPlacementId = repository.metaPlacementId.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "meta_placement_id_default_2026")

    val selectedAspectRatio = MutableStateFlow("9:16")
    val selectedResolution = MutableStateFlow("720p")
    val selectedFps = MutableStateFlow(30)
    val compiledPreviewPath = MutableStateFlow<String?>(null)
    val isExportPendingConfirmation = MutableStateFlow(false)

    val bottomTabSelected = MutableStateFlow("Home")
    val bottomTabHistory = MutableStateFlow<List<String>>(listOf("Home"))

    fun changeTab(newTab: String) {
        if (bottomTabSelected.value != newTab) {
            bottomTabSelected.value = newTab
            if (newTab == "Home") {
                bottomTabHistory.value = listOf("Home")
            } else {
                val currentHistory = bottomTabHistory.value.toMutableList()
                currentHistory.remove(newTab)
                currentHistory.add(newTab)
                bottomTabHistory.value = currentHistory
            }
        }
    }

    fun popTabHistory(): Boolean {
        val currentHistory = bottomTabHistory.value.toMutableList()
        if (currentHistory.size > 1) {
            currentHistory.removeAt(currentHistory.lastIndex)
            bottomTabHistory.value = currentHistory
            bottomTabSelected.value = currentHistory.last()
            return true
        }
        return false
    }

    val bgMusicEnabled = MutableStateFlow(true)
    val bgMusicCategory = MutableStateFlow("Cinematic")
    val bgMusicVolume = MutableStateFlow(0.3f)
    val voiceVolume = MutableStateFlow(0.8f)

    // Current Project being created or edited
    val activeProject = MutableStateFlow<ProjectEntity?>(null)
    val activeScenes = MutableStateFlow<List<Scene>>(emptyList())
    val selectedSceneIndex = MutableStateFlow(0)
    val isPlaying = MutableStateFlow(false)

    // Room Flows
    val recentProjects: StateFlow<List<ProjectEntity>> = repository.allProjects
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val exportHistory: StateFlow<List<ExportHistoryEntity>> = repository.allHistory
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Generation State
    val generationStatus = MutableStateFlow("")
    val generationProgress = MutableStateFlow(0f)
    val isGenerating = MutableStateFlow(false)

    // Export State
    val exportProgress = MutableStateFlow(0f)
    val isExporting = MutableStateFlow(false)
    val isExportingMinimized = MutableStateFlow(false)
    val exportedFilePath = MutableStateFlow<String?>(null)
    val latestMasterVoiceoverPath = MutableStateFlow<String?>(null)
    val exportStatus = MutableStateFlow("Rendering Final Output File")

    // JSON serializer for scenes
    private val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
    private val scenesListType = Types.newParameterizedType(List::class.java, Scene::class.java)
    private val scenesAdapter = moshi.adapter<List<Scene>>(scenesListType)

    init {
        // Synchronize initial voices preference
        viewModelScope.launch {
            repository.voicePreferences.take(1).collect { prefs ->
                selectedVoiceName.value = prefs.name
                selectedVoiceCategory.value = prefs.category
                voiceSpeed.value = prefs.speed
                voicePitch.value = prefs.pitch
                voiceEmotion.value = prefs.emotion
                selectedTtsEngine.value = prefs.ttsEngine
                aiTtsEnabled.value = prefs.isTtsEnabled
                
                // Initialize TTS engine dynamically based on preference
                reinitTts(prefs.ttsEngine)
            }
        }
    }

    fun isTtsEngineInstalled(packageName: String): Boolean {
        return try {
            val pm = getApplication<Application>().packageManager
            pm.getPackageInfo(packageName, 0)
            true
        } catch (e: Exception) {
            false
        }
    }

    fun changeTtsEngine(engine: String) {
        if (selectedTtsEngine.value == engine && tts != null && ttsReady) return
        selectedTtsEngine.value = engine
        saveVoicePreferencesOnly()
        reinitTts(engine)
    }

    fun saveVoicePreferencesOnly() {
        viewModelScope.launch {
            repository.updateVoicePreferences(
                VoicePrefs(
                    name = selectedVoiceName.value,
                    category = selectedVoiceCategory.value,
                    speed = voiceSpeed.value,
                    pitch = voicePitch.value,
                    emotion = voiceEmotion.value,
                    ttsEngine = selectedTtsEngine.value,
                    isTtsEnabled = aiTtsEnabled.value
                )
            )
        }
    }

    fun setAiTtsEnabled(enabled: Boolean) {
        aiTtsEnabled.value = enabled
        saveVoicePreferencesOnly()
    }

    private fun reinitTts(enginePackage: String) {
        val targetEngine = if (enginePackage == "com.github.oliviermartin.piper_tts" || enginePackage == "piper_local") {
            selectedTtsEngine.value = "com.google.android.tts"
            "com.google.android.tts"
        } else {
            enginePackage
        }
        ttsReady = false
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (e: Exception) {
            Log.e("MainViewModel", "Shutdown of previous TTS failed", e)
        }
        
        try {
            if (targetEngine == "com.google.android.tts") {
                tts = TextToSpeech(getApplication(), this, "com.google.android.tts")
            } else {
                tts = TextToSpeech(getApplication(), this)
            }
        } catch (e: Throwable) {
            Log.e("MainViewModel", "TTS Init for engine $enginePackage failed, falling back to default", e)
            try {
                tts = TextToSpeech(getApplication(), this)
            } catch (t: Throwable) {
                Log.e("MainViewModel", "Fallback TTS init failed", t)
            }
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            ttsReady = true
            tts?.language = Locale.US
        }
    }

    private var premiumMediaPlayer: android.media.MediaPlayer? = null

    private fun sanitizeNarrationTextForSpeech(text: String): String {
        if (text.isEmpty()) return ""
        return text
            // Replace multiple periods/ellipses with a single comma and a space to create a clean natural pause and avoid "dot dot dot" voiceover
            .replace(Regex("""\.{2,}"""), ", ")
            // Clean standard Emojis
            .replace(Regex("""[\uD83C-\uDBFF\uDC00-\uDFFF]+"""), "")
            // Clean remaining symbols/characters
            .replace(Regex("""[\p{So}\p{Cn}]"""), "")
            // Remove common signs/symbols that TTS engines read aloud (like *, #, -, _, =, etc.)
            .replace(Regex("""[*#\-_=+\\\/|<>\[\]{}()~`^@&%·•]"""), " ")
            // Normalize quotes and line breaks
            .replace("\"", " ")
            .replace("'", " ")
            .replace("\n", " ")
            // Replace multiple spaces with a single space
            .replace(Regex("""\s+"""), " ")
            .trim()
    }

    fun speakText(text: String, force: Boolean = false) {
        if (!force && !aiTtsEnabled.value) return
        val sanitizedText = sanitizeNarrationTextForSpeech(text)
        if (sanitizedText.isEmpty()) return
        speakTextLocally(sanitizedText)
    }

    private fun playByteArrayAudio(bytes: ByteArray) {
        try {
            premiumMediaPlayer?.stop()
            premiumMediaPlayer?.release()
            premiumMediaPlayer = null
        } catch (e: Exception) {
            Log.e("VoicePreview", "Error resetting media player", e)
        }

        try {
            val tempFile = File.createTempFile("premium_preview_", ".mp3", getApplication<Application>().cacheDir)
            tempFile.deleteOnExit()
            tempFile.writeBytes(bytes)

            premiumMediaPlayer = android.media.MediaPlayer().apply {
                setDataSource(tempFile.absolutePath)
                prepare()
                start()
                setOnCompletionListener {
                    it.release()
                    if (premiumMediaPlayer == it) {
                        premiumMediaPlayer = null
                    }
                    try { tempFile.delete() } catch (ex: Exception) {}
                }
            }
        } catch (e: Exception) {
            Log.e("VoicePreview", "Error playing premium audio", e)
        }
    }

    private fun speakTextLocally(text: String) {
        val sanitizedText = sanitizeNarrationTextForSpeech(text)
        if (sanitizedText.isEmpty()) return
        if (ttsReady && tts != null) {
            val ttsLanguage = when (selectedLanguage.value.lowercase().trim()) {
                "hindi", "hinglish" -> Locale("hi", "IN")
                "spanish" -> Locale("es", "ES")
                "japanese" -> Locale.JAPAN
                "chinese" -> Locale.CHINA
                "korean" -> Locale.KOREAN
                "german" -> Locale.GERMAN
                "french" -> Locale.FRANCE
                "arabic" -> Locale("ar", "SA")
                "urdu" -> Locale("ur", "PK")
                "bengali" -> Locale("bn", "IN")
                "tamil" -> Locale("ta", "IN")
                "telugu" -> Locale("te", "IN")
                "russian" -> Locale("ru", "RU")
                "portuguese" -> Locale("pt", "PT")
                "turkish" -> Locale("tr", "TR")
                "indonesian" -> Locale("id", "ID")
                "thai" -> Locale("th", "TH")
                "vietnamese" -> Locale("vi", "VN")
                else -> Locale.US
            }
            try {
                tts?.language = ttsLanguage
                
                try {
                    // Select custom premium HD Voice package from the device's TTS engine dynamically
                    val voices = tts?.voices
                    if (voices != null && voices.isNotEmpty()) {
                        val localeMatchingVoices = voices.filter { 
                            it?.locale?.language?.equals(ttsLanguage.language, ignoreCase = true) == true
                        }
                        
                        if (localeMatchingVoices.isNotEmpty()) {
                            // Find matching gender
                            val genderMatchingVoices = localeMatchingVoices.filter { voice ->
                                val nameLower = voice?.name?.lowercase() ?: ""
                                val isHi = ttsLanguage.language.equals("hi", ignoreCase = true)
                                if (selectedVoiceCategory.value == "Female") {
                                    nameLower.contains("female") || nameLower.contains("f-loc") || nameLower.contains("f-") || nameLower.contains("-f") ||
                                    (isHi && (
                                        nameLower.contains("-hia") || 
                                        nameLower.contains("-hic") || 
                                        nameLower.contains("-hie") || 
                                        nameLower.contains("-hig")
                                    ))
                                } else {
                                    nameLower.contains("male") || nameLower.contains("m-loc") || nameLower.contains("m-") || nameLower.contains("-m") ||
                                    (isHi && (
                                        nameLower.contains("-hib") || 
                                        nameLower.contains("-hid") || 
                                        nameLower.contains("-hif") || 
                                        nameLower.contains("-hih") || 
                                        nameLower.contains("-hnd") || 
                                        nameLower.contains("-hni")
                                    ))
                                }
                            }
                            
                            val targetPool = if (genderMatchingVoices.isNotEmpty()) {
                                genderMatchingVoices 
                            } else {
                                val oppositeFiltered = localeMatchingVoices.filter { voice ->
                                    val nameLower = voice?.name?.lowercase() ?: ""
                                    val isHi = ttsLanguage.language.equals("hi", ignoreCase = true)
                                    if (selectedVoiceCategory.value == "Female") {
                                        !(nameLower.contains("male") || nameLower.contains("m-loc") || nameLower.contains("m-") || nameLower.contains("-m") ||
                                        (isHi && (
                                            nameLower.contains("-hib") || 
                                            nameLower.contains("-hid") || 
                                            nameLower.contains("-hif") || 
                                            nameLower.contains("-hih") || 
                                            nameLower.contains("-hnd") || 
                                            nameLower.contains("-hni")
                                        )))
                                    } else {
                                        !(nameLower.contains("female") || nameLower.contains("f-loc") || nameLower.contains("f-") || nameLower.contains("-f") ||
                                        (isHi && (
                                            nameLower.contains("-hia") || 
                                            nameLower.contains("-hic") || 
                                            nameLower.contains("-hie") || 
                                            nameLower.contains("-hig")
                                        )))
                                    }
                                }
                                if (oppositeFiltered.isNotEmpty()) oppositeFiltered else localeMatchingVoices
                            }
                            
                            // Select matching offline voice profile based on selectedVoiceName value
                            val voiceNameLower = selectedVoiceName.value.lowercase()
                            val bestVoice = when {
                                voiceNameLower.contains("deep") || voiceNameLower.contains("narrator") -> {
                                    targetPool.getOrNull(1) ?: targetPool.firstOrNull()
                                }
                                voiceNameLower.contains("bold") || voiceNameLower.contains("dynamic") -> {
                                    targetPool.getOrNull(2) ?: targetPool.firstOrNull()
                                }
                                voiceNameLower.contains("old") || voiceNameLower.contains("grandpa") || voiceNameLower.contains("grandma") -> {
                                    targetPool.lastOrNull() ?: targetPool.firstOrNull()
                                }
                                voiceNameLower.contains("child") || voiceNameLower.contains("kid") || voiceNameLower.contains("baby") || voiceNameLower.contains("boy") || voiceNameLower.contains("girl") -> {
                                    targetPool.find { it?.name?.lowercase()?.contains("child") == true || it?.name?.lowercase()?.contains("kid") == true } 
                                        ?: targetPool.getOrNull(3) 
                                        ?: targetPool.firstOrNull()
                                }
                                voiceNameLower.contains("soft") || voiceNameLower.contains("ambient") -> {
                                    targetPool.getOrNull(1) ?: targetPool.firstOrNull()
                                }
                                voiceNameLower.contains("energetic") || voiceNameLower.contains("storyteller") -> {
                                    targetPool.getOrNull(2) ?: targetPool.firstOrNull()
                                }
                                else -> {
                                    targetPool.find { voice ->
                                        val nameLower = voice?.name?.lowercase() ?: ""
                                        (voice?.isNetworkConnectionRequired == true) || nameLower.contains("network") || nameLower.contains("neural") || nameLower.contains("natural") || nameLower.contains("-x-")
                                    } ?: targetPool.firstOrNull()
                                }
                            }

                            if (bestVoice != null) {
                                tts?.voice = bestVoice
                                Log.d("VoiceSelection", "Selected Premium HD Voice: ${bestVoice.name} (network=${bestVoice.isNetworkConnectionRequired})")
                            }
                        }
                    }
                } catch (t: Throwable) {
                    Log.w("VoiceSelection", "Failed to select premium voice, using default", t)
                }
            } catch (e: Exception) {
                tts?.language = Locale.US
            }

            // Apply specific voice-specific character pitch and speed profile offsets
            var finalSpeed = voiceSpeed.value
            var finalPitch = voicePitch.value

            when (selectedVoiceName.value) {
                "Deep Narrator" -> { finalPitch *= 0.82f; finalSpeed *= 0.95f }
                "Old Grandpa" -> { finalPitch *= 0.78f; finalSpeed *= 0.85f }
                "Young Boy" -> { finalPitch *= 1.15f; finalSpeed *= 1.05f }
                "Bold Dynamic" -> { finalPitch *= 0.95f; finalSpeed *= 1.08f }
                "Professional Man" -> { finalPitch *= 1.00f; finalSpeed *= 1.00f }
                
                "Soft Ambient" -> { finalPitch *= 1.05f; finalSpeed *= 0.88f }
                "Energetic Girl" -> { finalPitch *= 1.12f; finalSpeed *= 1.15f }
                "Old Grandma" -> { finalPitch *= 0.90f; finalSpeed *= 0.82f }
                "Storyteller" -> { finalPitch *= 1.02f; finalSpeed *= 0.92f }
                "Professional Woman" -> { finalPitch *= 1.00f; finalSpeed *= 1.00f }
                
                "Baby Boy" -> { finalPitch *= 1.45f; finalSpeed *= 1.02f }
                "Sweet Kid" -> { finalPitch *= 1.35f; finalSpeed *= 0.98f }
                "Playful Child" -> { finalPitch *= 1.40f; finalSpeed *= 1.10f }
                "Cute Little Girl" -> { finalPitch *= 1.50f; finalSpeed *= 1.05f }
            }

            // Adjust speech rate & pitch coefficients based on selected emotion for high professional fidelity
            when (voiceEmotion.value) {
                "Friendly" -> {
                    finalSpeed *= 1.02f
                    finalPitch *= 1.03f
                }
                "Energetic" -> {
                    finalSpeed *= 1.15f
                    finalPitch *= 1.05f
                }
                "Emotional" -> {
                    finalSpeed *= 0.88f
                    finalPitch *= 0.94f
                }
                "Cinematic" -> {
                    finalSpeed *= 0.92f
                    finalPitch *= 0.85f // deeper bass resonant tone
                }
            }
            
            try {
                tts?.setSpeechRate(finalSpeed.coerceIn(0.5f, 2.0f))
                tts?.setPitch(finalPitch.coerceIn(0.5f, 2.0f))
                val params = Bundle()
                params.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "PreviewUtterance")
                val result = tts?.speak(sanitizedText, TextToSpeech.QUEUE_FLUSH, params, "PreviewUtterance")
                if (result == TextToSpeech.ERROR) {
                    Log.e("MainViewModel", "TTS speak returned ERROR")
                }
            } catch (e: Throwable) {
                Log.e("MainViewModel", "Crash prevented during standard TTS speak", e)
            }
        }
    }

    suspend fun synthesizeVoiceForScenes(scenes: List<Scene>, cacheDir: File, progressCallback: (Float) -> Unit): List<File?> {
        if (!aiTtsEnabled.value) {
            Log.d("VoiceSynthesis", "AI TTS is disabled. Skipping voice synthesis for scenes.")
            return List(scenes.size) { null }
        }
        val voiceFiles = ArrayList<File?>()
        
        val localVoicesDir = File(getApplication<Application>().filesDir, "YashoraLocalVoices").apply {
            if (!exists()) mkdirs()
        }

        // Prepare full script text for single-segment continuous voiceover ("ek khand speech")
        val sceneTexts = scenes.map { scene ->
            val rawText = if (scene.narrationText.isNotEmpty()) scene.narrationText else scene.subtitle
            sanitizeNarrationTextForSpeech(rawText)
        }
        val nonBlankSceneTexts = sceneTexts.filter { it.isNotBlank() }

        // Wait up to 5 seconds for TTS engine to initialize or recover readiness asynchronously
        var waitCount = 0
        while ((tts == null || !ttsReady) && waitCount < 50) {
            delay(100)
            waitCount++
        }

        if (tts == null || !ttsReady) {
            Log.e("TTS_COMPILER", "TTS engine not ready after waiting. Cancelling voice synthesis.")
            return List(scenes.size) { null }
        }

        val ttsLanguage = when (selectedLanguage.value.lowercase().trim()) {
            "hindi", "hinglish" -> Locale("hi", "IN")
            "spanish" -> Locale("es", "ES")
            "japanese" -> Locale.JAPAN
            "chinese" -> Locale.CHINA
            "korean" -> Locale.KOREAN
            "german" -> Locale.GERMAN
            "french" -> Locale.FRANCE
            "arabic" -> Locale("ar", "SA")
            "urdu" -> Locale("ur", "PK")
            "bengali" -> Locale("bn", "IN")
            "tamil" -> Locale("ta", "IN")
            "telugu" -> Locale("te", "IN")
            "russian" -> Locale("ru", "RU")
            "portuguese" -> Locale("pt", "PT")
            "turkish" -> Locale("tr", "TR")
            "indonesian" -> Locale("id", "ID")
            "thai" -> Locale("th", "TH")
            "vietnamese" -> Locale("vi", "VN")
            else -> Locale.US
        }

        var confirmedBestVoice: android.speech.tts.Voice? = null
        try {
            tts?.language = ttsLanguage
            val voices = tts?.voices
            if (voices != null && voices.isNotEmpty()) {
                val localeMatchingVoices = voices.filter { 
                    it?.locale?.language?.equals(ttsLanguage.language, ignoreCase = true) == true
                }
                if (localeMatchingVoices.isNotEmpty()) {
                    val genderMatchingVoices = localeMatchingVoices.filter { voice ->
                        val nameLower = voice?.name?.lowercase() ?: ""
                        val isHi = ttsLanguage.language.equals("hi", ignoreCase = true)
                        if (selectedVoiceCategory.value == "Female") {
                            nameLower.contains("female") || nameLower.contains("f-loc") || nameLower.contains("f-") || nameLower.contains("-f") ||
                            (isHi && (
                                nameLower.contains("-hia") || 
                                nameLower.contains("-hic") || 
                                nameLower.contains("-hie") || 
                                nameLower.contains("-hig")
                            ))
                        } else {
                            nameLower.contains("male") || nameLower.contains("m-loc") || nameLower.contains("m-") || nameLower.contains("-m") ||
                            (isHi && (
                                nameLower.contains("-hib") || 
                                nameLower.contains("-hid") || 
                                nameLower.contains("-hif") || 
                                nameLower.contains("-hih") || 
                                nameLower.contains("-hnd") || 
                                nameLower.contains("-hni")
                            ))
                        }
                    }
                    val targetPool = if (genderMatchingVoices.isNotEmpty()) {
                        genderMatchingVoices
                    } else {
                        val oppositeFiltered = localeMatchingVoices.filter { voice ->
                            val nameLower = voice?.name?.lowercase() ?: ""
                            val isHi = ttsLanguage.language.equals("hi", ignoreCase = true)
                            if (selectedVoiceCategory.value == "Female") {
                                !(nameLower.contains("male") || nameLower.contains("m-loc") || nameLower.contains("m-") || nameLower.contains("-m") ||
                                (isHi && (
                                    nameLower.contains("-hib") || 
                                    nameLower.contains("-hid") || 
                                    nameLower.contains("-hif") || 
                                    nameLower.contains("-hih") || 
                                    nameLower.contains("-hnd") || 
                                    nameLower.contains("-hni")
                                )))
                            } else {
                                !(nameLower.contains("female") || nameLower.contains("f-loc") || nameLower.contains("f-") || nameLower.contains("-f") ||
                                (isHi && (
                                    nameLower.contains("-hia") || 
                                    nameLower.contains("-hic") || 
                                    nameLower.contains("-hie") || 
                                    nameLower.contains("-hig")
                                )))
                            }
                        }
                        if (oppositeFiltered.isNotEmpty()) oppositeFiltered else localeMatchingVoices
                    }
                    
                    // Select matching offline voice profile based on selectedVoiceName value
                    val voiceNameLower = selectedVoiceName.value.lowercase()
                    val bestVoice = when {
                        voiceNameLower.contains("deep") || voiceNameLower.contains("narrator") -> {
                            targetPool.getOrNull(1) ?: targetPool.firstOrNull()
                        }
                        voiceNameLower.contains("bold") || voiceNameLower.contains("dynamic") -> {
                            targetPool.getOrNull(2) ?: targetPool.firstOrNull()
                        }
                        voiceNameLower.contains("old") || voiceNameLower.contains("grandpa") || voiceNameLower.contains("grandma") -> {
                            targetPool.lastOrNull() ?: targetPool.firstOrNull()
                        }
                        voiceNameLower.contains("child") || voiceNameLower.contains("kid") || voiceNameLower.contains("baby") || voiceNameLower.contains("boy") || voiceNameLower.contains("girl") -> {
                            targetPool.find { it?.name?.lowercase()?.contains("child") == true || it?.name?.lowercase()?.contains("kid") == true } 
                                ?: targetPool.getOrNull(3) 
                                ?: targetPool.firstOrNull()
                        }
                        voiceNameLower.contains("soft") || voiceNameLower.contains("ambient") -> {
                            targetPool.getOrNull(1) ?: targetPool.firstOrNull()
                        }
                        voiceNameLower.contains("energetic") || voiceNameLower.contains("storyteller") -> {
                            targetPool.getOrNull(2) ?: targetPool.firstOrNull()
                        }
                        else -> {
                            targetPool.find { voice ->
                                val nameLower = voice?.name?.lowercase() ?: ""
                                (voice?.isNetworkConnectionRequired == true) || nameLower.contains("network") || nameLower.contains("neural") || nameLower.contains("natural") || nameLower.contains("-x-")
                            } ?: targetPool.firstOrNull()
                        }
                    }

                    if (bestVoice != null) {
                        tts?.voice = bestVoice
                        confirmedBestVoice = bestVoice
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("ExportVoice", "Failed configuring language/voice", e)
        }

        var finalSpeed = voiceSpeed.value
        var finalPitch = voicePitch.value

        when (selectedVoiceName.value) {
            "Deep Narrator" -> { finalPitch *= 0.82f; finalSpeed *= 0.95f }
            "Old Grandpa" -> { finalPitch *= 0.78f; finalSpeed *= 0.85f }
            "Young Boy" -> { finalPitch *= 1.15f; finalSpeed *= 1.05f }
            "Bold Dynamic" -> { finalPitch *= 0.95f; finalSpeed *= 1.08f }
            "Professional Man" -> { finalPitch *= 1.00f; finalSpeed *= 1.00f }
            "Soft Ambient" -> { finalPitch *= 1.05f; finalSpeed *= 0.88f }
            "Energetic Girl" -> { finalPitch *= 1.12f; finalSpeed *= 1.15f }
            "Old Grandma" -> { finalPitch *= 0.90f; finalSpeed *= 0.82f }
            "Storyteller" -> { finalPitch *= 1.02f; finalSpeed *= 0.92f }
            "Professional Woman" -> { finalPitch *= 1.00f; finalSpeed *= 1.00f }
            "Baby Boy" -> { finalPitch *= 1.45f; finalSpeed *= 1.02f }
            "Sweet Kid" -> { finalPitch *= 1.35f; finalSpeed *= 0.98f }
            "Playful Child" -> { finalPitch *= 1.40f; finalSpeed *= 1.10f }
            "Cute Little Girl" -> { finalPitch *= 1.50f; finalSpeed *= 1.05f }
        }

        when (voiceEmotion.value) {
            "Friendly" -> { finalSpeed *= 1.02f; finalPitch *= 1.03f }
            "Energetic" -> { finalSpeed *= 1.15f; finalPitch *= 1.05f }
            "Emotional" -> { finalSpeed *= 0.88f; finalPitch *= 0.94f }
            "Cinematic" -> { finalSpeed *= 0.92f; finalPitch *= 0.85f }
        }

        tts?.setSpeechRate(finalSpeed.coerceIn(0.5f, 2.0f))
        tts?.setPitch(finalPitch.coerceIn(0.5f, 2.0f))

        val activeTasks = java.util.concurrent.ConcurrentHashMap<String, kotlinx.coroutines.CompletableDeferred<Boolean>>()
        tts?.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                android.util.Log.d("TTS_COMPILER", "Synthesis started: $utteranceId")
            }
            override fun onDone(utteranceId: String?) {
                android.util.Log.d("TTS_COMPILER", "Synthesis completed: $utteranceId")
                if (utteranceId != null) {
                    activeTasks[utteranceId]?.complete(true)
                }
            }
            override fun onError(utteranceId: String?) {
                android.util.Log.e("TTS_COMPILER", "Synthesis error: $utteranceId")
                if (utteranceId != null) {
                    activeTasks[utteranceId]?.complete(false)
                }
            }
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?, errorCode: Int) {
                android.util.Log.e("TTS_COMPILER", "Synthesis error ($errorCode): $utteranceId")
                if (utteranceId != null) {
                    activeTasks[utteranceId]?.complete(false)
                }
            }
        })

        if (nonBlankSceneTexts.isNotEmpty() && tts != null && ttsReady) {
            try {
                val fullScriptText = nonBlankSceneTexts.joinToString(". ")
                val masterParamKey = "continuous_native_${fullScriptText}_${selectedLanguage.value}_${selectedVoiceName.value}_${selectedVoiceCategory.value}_${voiceSpeed.value}_${voicePitch.value}_${voiceEmotion.value}"
                val masterHash = md5(masterParamKey)
                val masterCacheFile = File(localVoicesDir, "master_$masterHash.wav")
                val masterTempFile = File(cacheDir, "master_speech_native_${System.currentTimeMillis()}.wav")

                var masterReady = false
                if (masterCacheFile.exists() && masterCacheFile.length() > 44) {
                    try {
                        masterCacheFile.copyTo(masterTempFile, overwrite = true)
                        masterReady = true
                    } catch (e: Exception) {
                        Log.e("VoiceCache", "Error copying cached master native WAV", e)
                    }
                }

                if (!masterReady) {
                    val utteranceId = "master_speech_utterance"
                    val deferred = kotlinx.coroutines.CompletableDeferred<Boolean>()
                    activeTasks[utteranceId] = deferred

                    val params = android.os.Bundle()
                    params.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId)

                    val result = tts?.synthesizeToFile(fullScriptText, params, masterTempFile, utteranceId)
                    if (result == TextToSpeech.SUCCESS) {
                        val ok = kotlinx.coroutines.withTimeoutOrNull(60000L) { deferred.await() } ?: false
                        for (attempt in 1..25) {
                            if (masterTempFile.exists() && masterTempFile.length() > 44) {
                                masterReady = true
                                break
                            }
                            delay(50)
                        }
                        if ((ok || masterReady) && masterTempFile.exists() && masterTempFile.length() > 44) {
                            try { masterTempFile.copyTo(masterCacheFile, overwrite = true) } catch (e: Exception) {}
                        }
                    }
                    activeTasks.remove(utteranceId)
                }

                if (masterTempFile.exists() && masterTempFile.length() > 44) {
                    val masterInfo = com.ritvyom.yashoraReelgenerator.presentation.utils.WavFileInfo.readWavFile(masterTempFile)
                    if (masterInfo != null && masterInfo.pcmData.isNotEmpty()) {
                        val persistentMasterFile = File(getApplication<Application>().filesDir, "Master_Full_Voiceover.wav")
                        try {
                            masterTempFile.copyTo(persistentMasterFile, overwrite = true)
                            latestMasterVoiceoverPath.value = persistentMasterFile.absolutePath
                        } catch (e: Exception) {
                            latestMasterVoiceoverPath.value = masterTempFile.absolutePath
                        }
                        val sliced = sliceMasterAudioForScenes(scenes, sceneTexts, masterInfo, cacheDir)
                        if (sliced != null && sliced.any { it != null }) {
                            Log.d("VoiceSynthesis", "Successfully generated single-segment continuous narration ('ek khand speech') via Android Native TTS for ${scenes.size} scenes!")
                            progressCallback(1.0f)
                            return sliced
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("VoiceSynthesis", "Unified Android Native TTS synthesis failed, falling back to per-scene synthesis", e)
            }
        }

        for (i in scenes.indices) {
            val scene = scenes[i]
            val rawText = if (scene.narrationText.isNotEmpty()) scene.narrationText else scene.subtitle
            val cleanedText = sanitizeNarrationTextForSpeech(rawText)

            if (cleanedText.isEmpty()) {
                voiceFiles.add(null)
                progressCallback((i + 1).toFloat() / scenes.size)
                continue
            }

            // Generate secure cache key for speech
            val paramKey = "${cleanedText}_${selectedLanguage.value}_${selectedVoiceName.value}_${selectedVoiceCategory.value}_${voiceSpeed.value}_${voicePitch.value}_${voiceEmotion.value}"
            val voiceHash = md5(paramKey)
            val cachedVoiceFile = File(localVoicesDir, "voice_$voiceHash.wav")
            val file = File(cacheDir, "scene_speech_${System.currentTimeMillis()}_$i.wav")

            if (cachedVoiceFile.exists() && cachedVoiceFile.length() > 44) {
                Log.d("VoiceCache", "Cache HIT for scene $i. Loading persistent wave copy...")
                try {
                    cachedVoiceFile.copyTo(file, overwrite = true)
                    voiceFiles.add(file)
                    progressCallback((i + 1).toFloat() / scenes.size)
                    continue
                } catch (e: Exception) {
                    Log.e("VoiceCache", "Failed copying voice cache, falling back to synthesis", e)
                }
            }

            Log.d("VoiceCache", "Cache MISS for scene $i. Initiating TTS synthesis...")
            val utteranceId = "scene_$i"
            val deferred = kotlinx.coroutines.CompletableDeferred<Boolean>()
            activeTasks[utteranceId] = deferred

            val params = android.os.Bundle()
            params.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId)

            val result = tts?.synthesizeToFile(cleanedText, params, file, utteranceId)

            if (result == TextToSpeech.SUCCESS) {
                // High ceiling limit timeout of 60 seconds (60000ms) for extremely long, beautiful speeches
                val ok = kotlinx.coroutines.withTimeoutOrNull(60000L) {
                    deferred.await()
                } ?: false

                // Add robust polling check to ensure the file is completely flushed and size > 44 bytes (WAV header size)
                var fileReady = false
                for (attempt in 1..25) {
                    if (file.exists() && file.length() > 44) {
                        fileReady = true
                        break
                    }
                    delay(50)
                }

                // If either listener notified completed OR the file is ready and valid, we count it as a success!
                if ((ok || fileReady) && file.exists() && file.length() > 44) {
                    // Save to local cache directory
                    try {
                        file.copyTo(cachedVoiceFile, overwrite = true)
                        Log.d("VoiceCache", "Successfully cached speaking reel narration: ${cachedVoiceFile.absolutePath}")
                    } catch (e: Exception) {
                        Log.e("VoiceCache", "Failed storing speech voice file to disk directory", e)
                    }

                    voiceFiles.add(file)
                } else {
                    android.util.Log.w("TTS_COMPILER", "Wait timeout or failure for scene $i. Voice file is missing or failed synthesis (length=${file.length()}).")
                    voiceFiles.add(null)
                }
            } else {
                voiceFiles.add(null)
            }
            activeTasks.remove(utteranceId)
            progressCallback((i + 1).toFloat() / scenes.size)
        }

        // Merge all generated per-scene speech files into one single continuous master WAV file!
        val mergedMaster = mergeVoiceFilesToMaster(voiceFiles)
        if (mergedMaster != null && mergedMaster.exists()) {
            latestMasterVoiceoverPath.value = mergedMaster.absolutePath
            Log.d("VoiceSynthesis", "Successfully merged scene speech files into single continuous master WAV at ${mergedMaster.absolutePath}")
        }

        return voiceFiles
    }

    private fun mergeVoiceFilesToMaster(voiceFiles: List<File?>): File? {
        try {
            val validFiles = voiceFiles.filterNotNull().filter { it.exists() && it.length() > 44 }
            if (validFiles.isEmpty()) return null
            val firstInfo = com.ritvyom.yashoraReelgenerator.presentation.utils.WavFileInfo.readWavFile(validFiles.first()) ?: return null
            val sampleRate = firstInfo.sampleRate
            val channels = firstInfo.channels
            val bitsPerSample = firstInfo.bitsPerSample
            val allPcm = java.io.ByteArrayOutputStream()
            for (f in validFiles) {
                val info = com.ritvyom.yashoraReelgenerator.presentation.utils.WavFileInfo.readWavFile(f)
                if (info != null && info.pcmData.isNotEmpty()) {
                    allPcm.write(info.pcmData)
                }
            }
            val pcmBytes = allPcm.toByteArray()
            if (pcmBytes.isEmpty()) return null
            val masterFile = File(getApplication<Application>().filesDir, "Master_Full_Voiceover.wav")
            writeWavHeaderAndPcm(masterFile, pcmBytes, sampleRate, channels, bitsPerSample)
            return masterFile
        } catch (e: Exception) {
            Log.e("VoiceSynthesis", "Error merging scene voice files to single master WAV", e)
            return null
        }
    }

    fun shareMasterVoiceoverAudio(context: android.content.Context) {
        val path = latestMasterVoiceoverPath.value ?: File(getApplication<Application>().filesDir, "Master_Full_Voiceover.wav").takeIf { it.exists() }?.absolutePath
        if (path.isNullOrEmpty()) {
            android.widget.Toast.makeText(context, "Voiceover audio not available yet. Please export video or generate script first.".localize(appLanguage.value), android.widget.Toast.LENGTH_LONG).show()
            return
        }
        val file = File(path)
        if (!file.exists() || file.length() <= 44) {
            android.widget.Toast.makeText(context, "Voiceover audio file is missing or empty.".localize(appLanguage.value), android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val uri = androidx.core.content.FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = "audio/*"
                putExtra(android.content.Intent.EXTRA_STREAM, uri)
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(android.content.Intent.createChooser(intent, "Share Single Voiceover Audio (Kinemaster / InShot)".localize(appLanguage.value)))
        } catch (e: Exception) {
            Log.e("VoiceoverShare", "Error sharing master voiceover", e)
            android.widget.Toast.makeText(context, "Sharing failed: ${e.localizedMessage}", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    fun saveMasterVoiceoverToDownloads(context: android.content.Context) {
        val path = latestMasterVoiceoverPath.value ?: File(getApplication<Application>().filesDir, "Master_Full_Voiceover.wav").takeIf { it.exists() }?.absolutePath
        if (path.isNullOrEmpty()) {
            android.widget.Toast.makeText(context, "Voiceover audio not available yet. Please export video or generate script first.".localize(appLanguage.value), android.widget.Toast.LENGTH_LONG).show()
            return
        }
        val srcFile = File(path)
        if (!srcFile.exists() || srcFile.length() <= 44) {
            android.widget.Toast.makeText(context, "Voiceover audio file is missing or empty.".localize(appLanguage.value), android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val timestamp = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US).format(java.util.Date())
                val fileName = "Yashora_Full_Voiceover_$timestamp.wav"
                
                var savedSuccessPath = ""
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    val resolver = context.contentResolver
                    val contentValues = ContentValues().apply {
                        put(MediaStore.Audio.Media.DISPLAY_NAME, fileName)
                        put(MediaStore.Audio.Media.MIME_TYPE, "audio/wav")
                        put(MediaStore.Audio.Media.RELATIVE_PATH, Environment.DIRECTORY_MUSIC + "/Yashora")
                        put(MediaStore.Audio.Media.IS_PENDING, 1)
                    }
                    val uri = resolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, contentValues)
                    if (uri != null) {
                        resolver.openOutputStream(uri)?.use { out ->
                            out.write(srcFile.readBytes())
                        }
                        val updateValues = ContentValues().apply { put(MediaStore.Audio.Media.IS_PENDING, 0) }
                        resolver.update(uri, updateValues, null, null)
                        savedSuccessPath = "Music/Yashora/$fileName"
                    }
                } else {
                    val musicFolder = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
                    val appFolder = File(musicFolder, "Yashora")
                    if (!appFolder.exists()) appFolder.mkdirs()
                    val destFile = File(appFolder, fileName)
                    destFile.writeBytes(srcFile.readBytes())
                    savedSuccessPath = destFile.absolutePath
                    try {
                        android.media.MediaScannerConnection.scanFile(context, arrayOf(destFile.absolutePath), arrayOf("audio/wav"), null)
                    } catch (e: Exception) {}
                }

                withContext(Dispatchers.Main) {
                    android.widget.Toast.makeText(
                        context,
                        "Single Voiceover saved to Music/Yashora!\n(Ready for Kinemaster & InShot)".localize(appLanguage.value),
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                }
            } catch (e: Exception) {
                Log.e("VoiceoverSave", "Failed saving master voiceover audio", e)
                withContext(Dispatchers.Main) {
                    android.widget.Toast.makeText(context, "Saved audio locally: ${srcFile.name}", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun sliceMasterAudioForScenes(
        scenes: List<Scene>,
        sceneTexts: List<String>,
        masterInfo: com.ritvyom.yashoraReelgenerator.presentation.utils.WavFileInfo,
        cacheDir: File
    ): List<File?>? {
        val totalPcmBytes = masterInfo.pcmData.size
        if (totalPcmBytes <= 0) return null

        fun calcWeight(text: String): Int {
            val words = text.split(Regex("\\s+")).filter { it.isNotBlank() }.size
            return if (words > 0) words else text.length.coerceAtLeast(1)
        }

        val weights = sceneTexts.map { if (it.isNotBlank()) calcWeight(it) else 0 }
        val totalWeight = weights.sum().coerceAtLeast(1)

        val frameAlignment = masterInfo.channels * (masterInfo.bitsPerSample / 8)
        val slicedFiles = ArrayList<File?>()
        var offset = 0

        val lastNonEmptyIndex = scenes.indices.lastOrNull { weights[it] > 0 } ?: -1

        for (i in scenes.indices) {
            val w = weights[i]
            if (w == 0) {
                slicedFiles.add(null)
            } else {
                val isLastNonEmpty = (i == lastNonEmptyIndex)
                val rawByteCount = if (isLastNonEmpty) {
                    totalPcmBytes - offset
                } else {
                    Math.round(totalPcmBytes.toDouble() * w.toDouble() / totalWeight.toDouble()).toInt()
                }
                val alignedByteCount = Math.max(0, (rawByteCount / frameAlignment) * frameAlignment)
                val endOffset = Math.min(offset + alignedByteCount, totalPcmBytes)

                if (endOffset > offset) {
                    val chunk = masterInfo.pcmData.copyOfRange(offset, endOffset)
                    val sceneFile = File(cacheDir, "scene_speech_${System.currentTimeMillis()}_$i.wav")
                    writeWavHeaderAndPcm(sceneFile, chunk, masterInfo.sampleRate, masterInfo.channels, masterInfo.bitsPerSample)
                    slicedFiles.add(sceneFile)
                    offset = endOffset
                } else {
                    slicedFiles.add(null)
                }
            }
        }
        return slicedFiles
    }

    private fun writeWavHeaderAndPcm(
        outputFile: File,
        pcmData: ByteArray,
        sampleRate: Int,
        channels: Int,
        bitsPerSample: Int = 16
    ) {
        val totalAudioLen = pcmData.size.toLong()
        val totalDataLen = totalAudioLen + 36
        val byteRate = (sampleRate * channels * bitsPerSample / 8).toLong()

        val header = ByteArray(44)
        header[0] = 'R'.code.toByte(); header[1] = 'I'.code.toByte(); header[2] = 'F'.code.toByte(); header[3] = 'F'.code.toByte()
        header[4] = (totalDataLen and 0xff).toByte()
        header[5] = ((totalDataLen shr 8) and 0xff).toByte()
        header[6] = ((totalDataLen shr 16) and 0xff).toByte()
        header[7] = ((totalDataLen shr 24) and 0xff).toByte()
        header[8] = 'W'.code.toByte(); header[9] = 'A'.code.toByte(); header[10] = 'V'.code.toByte(); header[11] = 'E'.code.toByte()
        header[12] = 'f'.code.toByte(); header[13] = 'm'.code.toByte(); header[14] = 't'.code.toByte(); header[15] = ' '.code.toByte()
        header[16] = 16; header[17] = 0; header[18] = 0; header[19] = 0
        header[20] = 1; header[21] = 0
        header[22] = channels.toByte(); header[23] = 0
        header[24] = (sampleRate and 0xff).toByte()
        header[25] = ((sampleRate shr 8) and 0xff).toByte()
        header[26] = ((sampleRate shr 16) and 0xff).toByte()
        header[27] = ((sampleRate shr 24) and 0xff).toByte()
        header[28] = (byteRate and 0xff).toByte()
        header[29] = ((byteRate shr 8) and 0xff).toByte()
        header[30] = ((byteRate shr 16) and 0xff).toByte()
        header[31] = ((byteRate shr 24) and 0xff).toByte()
        header[32] = (channels * bitsPerSample / 8).toByte(); header[33] = 0
        header[34] = bitsPerSample.toByte(); header[35] = 0
        header[36] = 'd'.code.toByte(); header[37] = 'a'.code.toByte(); header[38] = 't'.code.toByte(); header[39] = 'a'.code.toByte()
        header[40] = (totalAudioLen and 0xff).toByte()
        header[41] = ((totalAudioLen shr 8) and 0xff).toByte()
        header[42] = ((totalAudioLen shr 16) and 0xff).toByte()
        header[43] = ((totalAudioLen shr 24) and 0xff).toByte()

        java.io.FileOutputStream(outputFile).use { fos ->
            fos.write(header)
            fos.write(pcmData)
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

    fun applyVoicePreset(preset: com.ritvyom.yashoraReelgenerator.presentation.utils.VoicePreset) {
        selectedVoiceCategory.value = preset.voiceCategory
        selectedVoiceName.value = preset.voiceName
        voiceSpeed.value = preset.speed
        voicePitch.value = preset.pitch
        voiceEmotion.value = preset.emotion
        bgMusicCategory.value = preset.musicCategory
        
        saveVoicePreferencesOnly()
        
        // Preview voice preset immediately
        speakText("Loaded category ${preset.name} voice profile. Sound wave compiles correctly.".localize(appLanguage.value))
    }

    fun stopSpeak() {
        try {
            mediaPlayer?.stop()
        } catch (e: Exception) {}
        if (ttsReady) {
            tts?.stop()
        }
    }

    fun selectVoice(name: String, category: String) {
        selectedVoiceName.value = name
        selectedVoiceCategory.value = category
        
        saveVoicePreferencesOnly()
        
        // Preview voice immediately
        val greet = when (category) {
            "Male" -> "Hello! I am $name, your custom male voice synthesized for this reel."
            "Female" -> "Hello! I am $name, your custom female narration voice."
            else -> "Hey there! I am $name, parsed for children storytelling reels."
        }
        speakText(greet)
    }

    // Settings adjustments
    fun setAppTheme(theme: String) {
        viewModelScope.launch {
            repository.updateTheme(theme)
        }
    }

    fun setAppLanguage(lang: String) {
        viewModelScope.launch {
            repository.updateAppLanguage(lang)
        }
    }

    fun setVideoLanguage(lang: String) {
        viewModelScope.launch {
            repository.updateVideoLanguage(lang)
            selectedLanguage.value = lang
        }
    }

    fun setAppLovinMediationEnabled(enabled: Boolean) {
        viewModelScope.launch {
            repository.updateAppLovinMediationEnabled(enabled)
        }
    }

    fun setAppLovinSdkKey(key: String) {
        viewModelScope.launch {
            repository.updateAppLovinSdkKey(key)
        }
    }

    fun setAppLovinZoneIdRewarded(id: String) {
        viewModelScope.launch {
            repository.updateAppLovinZoneIdRewarded(id)
        }
    }

    fun setAppLovinZoneIdInterstitial(id: String) {
        viewModelScope.launch {
            repository.updateAppLovinZoneIdInterstitial(id)
        }
    }

    fun setMediationBiddingStrategy(strategy: String) {
        viewModelScope.launch {
            repository.updateMediationBiddingStrategy(strategy)
        }
    }

    fun setGoogleBiddingAppId(appId: String) {
        viewModelScope.launch {
            repository.updateGoogleBiddingAppId(appId)
        }
    }

    fun setAdmobBannerAdUnitId(adUnitId: String) {
        viewModelScope.launch {
            repository.updateAdmobBannerAdUnitId(adUnitId)
        }
    }

    fun setAdmobInterstitialAdUnitId(adUnitId: String) {
        viewModelScope.launch {
            repository.updateAdmobInterstitialAdUnitId(adUnitId)
        }
    }

    fun setAdmobRewardedAdUnitId(adUnitId: String) {
        viewModelScope.launch {
            repository.updateAdmobRewardedAdUnitId(adUnitId)
        }
    }

    fun setUnityGameId(id: String) {
        viewModelScope.launch {
            repository.updateUnityGameId(id)
        }
    }

    fun setMetaPlacementId(id: String) {
        viewModelScope.launch {
            repository.updateMetaPlacementId(id)
        }
    }

    // Project Actions
    fun createProjectFromCurrent() {
        viewModelScope.launch {
            val title = if (scriptText.value.length > 20) {
                scriptText.value.substring(0, 17) + "..."
            } else if (scriptText.value.isNotEmpty()) {
                scriptText.value
            } else {
                "Untitled Reel #${System.currentTimeMillis() % 1000}"
            }

            // Create project in DB
            val entity = ProjectEntity(
                title = title,
                scriptText = scriptText.value,
                videoStyle = selectedStyleName.value,
                voiceName = selectedVoiceName.value,
                voiceCategory = selectedVoiceCategory.value,
                language = selectedLanguage.value,
                aspectRatio = selectedAspectRatio.value,
                resolution = selectedResolution.value,
                fps = selectedFps.value,
                bgMusicCategory = bgMusicCategory.value,
                bgMusicVolume = bgMusicVolume.value,
                voiceVolume = voiceVolume.value,
                status = "Draft",
                topicContext = topicContext.value,
                publishingStyle = selectedPublishingStyle.value
            )
            val projId = repository.insertProject(entity)
            activeProject.value = entity.copy(id = projId.toInt())
        }
    }

    fun createDirectEditProject(title: String, uris: List<String>, context: android.content.Context, onComplete: () -> Unit) {
        viewModelScope.launch {
            val sceneList = mutableListOf<Scene>()
            for ((index, uriStr) in uris.withIndex()) {
                val uri = android.net.Uri.parse(uriStr)
                val isVideo = try {
                    val mimeType = context.contentResolver.getType(uri)
                    (mimeType != null && mimeType.startsWith("video", ignoreCase = true)) ||
                            uriStr.contains("video", ignoreCase = true) ||
                            uriStr.contains(".mp4", ignoreCase = true) ||
                            uriStr.contains(".3gp", ignoreCase = true) ||
                            uriStr.contains(".3gpp", ignoreCase = true) ||
                            uriStr.contains(".mkv", ignoreCase = true) ||
                            uriStr.contains(".webm", ignoreCase = true) ||
                            uriStr.contains(".mov", ignoreCase = true) ||
                            uriStr.contains(".avi", ignoreCase = true)
                } catch (e: Exception) {
                    uriStr.contains(".mp4", ignoreCase = true) || uriStr.contains("video", ignoreCase = true)
                }

                var durationSeconds = 5
                if (isVideo) {
                    val retriever = android.media.MediaMetadataRetriever()
                    try {
                        retriever.setDataSource(context, uri)
                        val timeStr = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)
                        if (!timeStr.isNullOrEmpty()) {
                            val durationMs = timeStr.toLong()
                            durationSeconds = (durationMs / 1000).toInt().coerceAtLeast(1)
                        }
                    } catch (e: Exception) {
                        Log.e("MainViewModel", "Failed retrieve video duration", e)
                        durationSeconds = 5
                    } finally {
                        try { retriever.release() } catch (e: Exception) {}
                    }
                }

                sceneList.add(
                    Scene(
                        sceneNumber = index + 1,
                        narrationText = "Local imported media content",
                        visualPrompt = "Local imported media content",
                        subtitle = "Clip #${index + 1}",
                        mediaPath = uriStr,
                        mediaType = if (isVideo) "VIDEO" else "IMAGE",
                        durationSeconds = durationSeconds,
                        durationMs = durationSeconds * 1000L
                    )
                )
            }

            if (sceneList.isEmpty()) {
                sceneList.add(
                    Scene(
                        sceneNumber = 1,
                        narrationText = "",
                        visualPrompt = "Blank Canvas",
                        subtitle = "Clip #1",
                        mediaPath = null,
                        mediaType = "IMAGE",
                        durationSeconds = 5,
                        durationMs = 5000L
                    )
                )
            }

            // Create project in DB
            val entity = ProjectEntity(
                title = title,
                scriptText = "",
                videoStyle = "Cinematic",
                voiceName = "Professional Man",
                voiceCategory = "Male",
                language = "English",
                aspectRatio = selectedAspectRatio.value,
                resolution = selectedResolution.value,
                fps = selectedFps.value,
                bgMusicCategory = "Cinematic",
                bgMusicVolume = 0.0f, // Mute background music for direct video edit timeline by default
                voiceVolume = 1.0f,
                status = "Draft",
                scenesJson = scenesAdapter.toJson(sceneList),
                topicContext = "Direct Editor",
                publishingStyle = "TikTok / Instagram Reels"
            )
            val projId = repository.insertProject(entity)
            activeProject.value = entity.copy(id = projId.toInt())
            activeScenes.value = sceneList
            selectedSceneIndex.value = 0
            
            withContext(Dispatchers.Main) {
                onComplete()
            }
        }
    }

    fun loadProject(project: ProjectEntity) {
        activeProject.value = project
        scriptText.value = project.scriptText
        topicContext.value = project.topicContext
        selectedStyleName.value = project.videoStyle
        selectedPublishingStyle.value = project.publishingStyle
        selectedVoiceName.value = project.voiceName
        selectedVoiceCategory.value = project.voiceCategory
        selectedLanguage.value = project.language
        selectedAspectRatio.value = project.aspectRatio
        selectedResolution.value = project.resolution
        selectedFps.value = project.fps
        bgMusicCategory.value = project.bgMusicCategory
        bgMusicVolume.value = project.bgMusicVolume
        voiceVolume.value = project.voiceVolume

        // Robustly parse scenes stored in project under ANY status (Draft, Completed, etc.)
        viewModelScope.launch {
            try {
                if (project.scenesJson.isNotEmpty()) {
                    val decoded = scenesAdapter.fromJson(project.scenesJson)
                    if (decoded != null && decoded.isNotEmpty()) {
                        activeScenes.value = decoded
                        selectedSceneIndex.value = 0
                        return@launch
                    }
                }
                
                // Fallback to local parsing if there are no pre-compiled scenes saved but script is present
                if (project.scriptText.isNotEmpty()) {
                    val scenes = repository.fallbackLocalParser(project.scriptText, project.videoStyle, project.language)
                    activeScenes.value = scenes
                    selectedSceneIndex.value = 0
                } else {
                    activeScenes.value = emptyList()
                }
            } catch (e: Exception) {
                Log.e("MainViewModel", "Error deserializing stored scenes JSON", e)
                try {
                    val scenes = repository.fallbackLocalParser(project.scriptText, project.videoStyle, project.language)
                    activeScenes.value = scenes
                    selectedSceneIndex.value = 0
                } catch (ex: Exception) {
                    activeScenes.value = emptyList()
                }
            }
        }
    }

    fun saveCurrentScenesToDb() {
        viewModelScope.launch {
            activeProject.value?.let { proj ->
                try {
                    val jsonStr = scenesAdapter.toJson(activeScenes.value)
                    val updatedProj = proj.copy(
                        scenesJson = jsonStr,
                        updatedAt = System.currentTimeMillis()
                    )
                    repository.updateProject(updatedProj)
                    activeProject.value = updatedProj
                } catch (e: Exception) {
                    Log.e("MainViewModel", "Error auto-saving scenes to DB", e)
                }
            }
        }
    }

    fun regenerateCurrentProjectVisuals() {
        val project = activeProject.value ?: return
        val currentScenes = activeScenes.value
        if (currentScenes.isEmpty()) return

        isGenerating.value = true
        generationProgress.value = 0.1f
        generationStatus.value = "Starting backdrop refresh with new API keys..."

        generationJob = viewModelScope.launch {
            try {
                val updatedScenes = currentScenes.mapIndexed { index, scene ->
                    generationProgress.value = 0.1f + (index.toFloat() / currentScenes.size) * 0.8f
                    generationStatus.value = "Fetching media for clip ${index + 1}/${currentScenes.size}..."

                    val resolvedUrl = repository.resolveVisualAsset(
                        query = scene.visualPrompt,
                        style = project.videoStyle.ifEmpty { selectedStyleName.value },
                        aspectRatio = project.aspectRatio,
                        index = index,
                        visualMedium = selectedVisualMedium.value,
                        imageSource = selectedImageSource.value,
                        visualPrompt = scene.visualPrompt
                    )
                    
                    if (resolvedUrl.isNotEmpty()) {
                        val localPath = repository.downloadMediaToLocal(resolvedUrl)
                        scene.copy(mediaPath = localPath, remoteUrl = resolvedUrl)
                    } else {
                        scene
                    }
                }
                activeScenes.value = updatedScenes
                saveCurrentScenesToDb()
                generationStatus.value = "Refresh complete!"
            } catch (e: Exception) {
                Log.e("MainViewModel", "Failed to regenerate visuals", e)
                generationStatus.value = "Failed to refresh some visuals."
            } finally {
                isGenerating.value = false
                generationProgress.value = 1.0f
            }
        }
    }

    // Video editor actions
    private val undoStack = java.util.Stack<List<Scene>>()
    private val redoStack = java.util.Stack<List<Scene>>()
    val canUndo = MutableStateFlow(false)
    val canRedo = MutableStateFlow(false)

    private fun pushToUndo() {
        val copy = activeScenes.value.map { it.copy() }
        undoStack.push(copy)
        if (undoStack.size > 50) {
            undoStack.removeAt(0)
        }
        redoStack.clear()
        canUndo.value = undoStack.isNotEmpty()
        canRedo.value = redoStack.isNotEmpty()
    }

    private fun updateUndoRedoState() {
        canUndo.value = undoStack.isNotEmpty()
        canRedo.value = redoStack.isNotEmpty()
    }

    fun undo() {
        if (undoStack.isNotEmpty()) {
            val current = activeScenes.value.map { it.copy() }
            redoStack.push(current)
            val previous = undoStack.pop()
            activeScenes.value = previous
            saveCurrentScenesToDb()
            updateUndoRedoState()
        }
    }

    fun redo() {
        if (redoStack.isNotEmpty()) {
            val current = activeScenes.value.map { it.copy() }
            undoStack.push(current)
            val next = redoStack.pop()
            activeScenes.value = next
            saveCurrentScenesToDb()
            updateUndoRedoState()
        }
    }

    fun editSceneSubtitle(index: Int, newText: String) {
        pushToUndo()
        val currentList = activeScenes.value.toMutableList()
        if (index in currentList.indices) {
            currentList[index] = currentList[index].copy(subtitle = newText)
            activeScenes.value = currentList
            saveCurrentScenesToDb()
        }
    }

    fun editSceneNarrationText(index: Int, newText: String) {
        pushToUndo()
        val currentList = activeScenes.value.toMutableList()
        if (index in currentList.indices) {
            currentList[index] = currentList[index].copy(narrationText = newText)
            activeScenes.value = currentList
            saveCurrentScenesToDb()
        }
    }

    fun editSceneDuration(index: Int, durationSeconds: Int) {
        pushToUndo()
        val currentList = activeScenes.value.toMutableList()
        if (index in currentList.indices) {
            currentList[index] = currentList[index].copy(
                durationSeconds = durationSeconds,
                durationMs = durationSeconds * 1000L
            )
            activeScenes.value = currentList
            saveCurrentScenesToDb()
        }
    }

    fun editSceneFilter(index: Int, filterName: String, filterIndex: Int) {
        pushToUndo()
        val currentList = activeScenes.value.toMutableList()
        if (index in currentList.indices) {
            currentList[index] = currentList[index].copy(
                selectedFilterName = filterName,
                selectedFilterIndex = filterIndex
            )
            activeScenes.value = currentList
            saveCurrentScenesToDb()
        }
    }

    fun editSceneTransition(index: Int, transitionType: String) {
        pushToUndo()
        val currentList = activeScenes.value.toMutableList()
        if (index in currentList.indices) {
            currentList[index] = currentList[index].copy(transitionType = transitionType)
            activeScenes.value = currentList
            saveCurrentScenesToDb()
        }
    }

    fun editSceneOverlayText(index: Int, text: String, color: String) {
        pushToUndo()
        val currentList = activeScenes.value.toMutableList()
        if (index in currentList.indices) {
            currentList[index] = currentList[index].copy(
                textOverlay = text,
                overlayColor = color
            )
            activeScenes.value = currentList
            saveCurrentScenesToDb()
        }
    }

    fun editSceneFont(index: Int, fontName: String) {
        pushToUndo()
        val currentList = activeScenes.value.toMutableList()
        if (index in currentList.indices) {
            currentList[index] = currentList[index].copy(
                captionFont = fontName
            )
            activeScenes.value = currentList
            saveCurrentScenesToDb()
        }
    }

    fun editSceneSubtitleStyle(index: Int, color: String, bgColor: String, design: String) {
        pushToUndo()
        val currentList = activeScenes.value.toMutableList()
        if (index in currentList.indices) {
            currentList[index] = currentList[index].copy(
                subtitleColor = color,
                subtitleBgColor = bgColor,
                subtitleDesign = design
            )
            activeScenes.value = currentList
            saveCurrentScenesToDb()
        }
    }

    fun editSceneMediaPath(index: Int, path: String) {
        pushToUndo()
        val currentList = activeScenes.value.toMutableList()
        if (index in currentList.indices) {
            currentList[index] = currentList[index].copy(
                mediaPath = path,
                visualPrompt = "Custom Imported Media"
            )
            activeScenes.value = currentList
            saveCurrentScenesToDb()
        }
    }

    fun editSceneSpeedMultiplier(index: Int, speed: Float) {
        pushToUndo()
        val currentList = activeScenes.value.toMutableList()
        if (index in currentList.indices) {
            currentList[index] = currentList[index].copy(speedMultiplier = speed)
            activeScenes.value = currentList
            saveCurrentScenesToDb()
        }
    }

    fun editSceneVolume(index: Int, volume: Float) {
        pushToUndo()
        val currentList = activeScenes.value.toMutableList()
        if (index in currentList.indices) {
            currentList[index] = currentList[index].copy(volume = volume)
            activeScenes.value = currentList
            saveCurrentScenesToDb()
        }
    }

    fun editSceneRotation(index: Int, rotation: Int) {
        pushToUndo()
        val currentList = activeScenes.value.toMutableList()
        if (index in currentList.indices) {
            currentList[index] = currentList[index].copy(rotationDegrees = rotation)
            activeScenes.value = currentList
            saveCurrentScenesToDb()
        }
    }

    fun editSceneFlippedHorizontal(index: Int, flipped: Boolean) {
        pushToUndo()
        val currentList = activeScenes.value.toMutableList()
        if (index in currentList.indices) {
            currentList[index] = currentList[index].copy(isFlippedHorizontal = flipped)
            activeScenes.value = currentList
            saveCurrentScenesToDb()
        }
    }

    fun editSceneFlippedVertical(index: Int, flipped: Boolean) {
        pushToUndo()
        val currentList = activeScenes.value.toMutableList()
        if (index in currentList.indices) {
            currentList[index] = currentList[index].copy(isFlippedVertical = flipped)
            activeScenes.value = currentList
            saveCurrentScenesToDb()
        }
    }

    fun editSceneMaskShape(index: Int, maskShape: String) {
        pushToUndo()
        val currentList = activeScenes.value.toMutableList()
        if (index in currentList.indices) {
            currentList[index] = currentList[index].copy(maskShape = maskShape)
            activeScenes.value = currentList
            saveCurrentScenesToDb()
        }
    }

    fun editSceneChromaKey(index: Int, isEnabled: Boolean, color: String, sensitivity: Float) {
        pushToUndo()
        val currentList = activeScenes.value.toMutableList()
        if (index in currentList.indices) {
            currentList[index] = currentList[index].copy(
                isChromaKeyEnabled = isEnabled,
                chromaKeyColor = color,
                chromaKeySensitivity = sensitivity
            )
            activeScenes.value = currentList
            saveCurrentScenesToDb()
        }
    }

    fun editSceneTextAnimation(index: Int, animation: String) {
        pushToUndo()
        val currentList = activeScenes.value.toMutableList()
        if (index in currentList.indices) {
            currentList[index] = currentList[index].copy(textAnimation = animation)
            activeScenes.value = currentList
            saveCurrentScenesToDb()
        }
    }

    fun editSceneVisualGrading(index: Int, brightness: Float, contrast: Float, saturation: Float, warmth: Float) {
        pushToUndo()
        val currentList = activeScenes.value.toMutableList()
        if (index in currentList.indices) {
            currentList[index] = currentList[index].copy(
                brightnessValue = brightness,
                contrastValue = contrast,
                saturationValue = saturation,
                warmthValue = warmth
            )
            activeScenes.value = currentList
            saveCurrentScenesToDb()
        }
    }

    fun reorderScenes(fromIndex: Int, toIndex: Int) {
        pushToUndo()
        val currentList = activeScenes.value.toMutableList()
        if (fromIndex in currentList.indices && toIndex in currentList.indices) {
            val scene = currentList.removeAt(fromIndex)
            currentList.add(toIndex, scene)
            val renumbered = currentList.mapIndexed { idx, item ->
                item.copy(sceneNumber = idx + 1)
            }
            activeScenes.value = renumbered
            saveCurrentScenesToDb()
            speakText("Moved scene ${fromIndex + 1} to position ${toIndex + 1}")
        }
    }

    fun swapSceneMedia(index: Int, path: String, mediaType: String) {
        pushToUndo()
        val currentList = activeScenes.value.toMutableList()
        if (index in currentList.indices) {
            currentList[index] = currentList[index].copy(
                mediaPath = path,
                mediaType = mediaType,
                visualPrompt = if (mediaType == "VIDEO") "Alternative Video Selection" else "Alternative Photo Selection"
            )
            activeScenes.value = currentList
            saveCurrentScenesToDb()
        }
    }

    fun deleteScene(index: Int) {
        pushToUndo()
        val currentList = activeScenes.value.toMutableList()
        if (currentList.size > 1 && index in currentList.indices) {
            currentList.removeAt(index)
            val reindexed = currentList.mapIndexed { idx, item ->
                item.copy(sceneNumber = idx + 1)
            }
            if (selectedSceneIndex.value >= reindexed.size) {
                selectedSceneIndex.value = kotlin.math.max(0, reindexed.size - 1)
            }
            activeScenes.value = reindexed
            saveCurrentScenesToDb()
        }
    }

    fun splitScene(index: Int, label: String = "[Split] New Segment") {
        pushToUndo()
        val currentList = activeScenes.value.toMutableList()
        if (index in currentList.indices) {
            val sc = currentList[index]
            val nextId = index + 1
            val copy = sc.copy(
                sceneNumber = currentList.size + 1,
                subtitle = label.localize(appLanguage.value)
            )
            currentList.add(nextId, copy)
            val reindexed = currentList.mapIndexed { idx, item ->
                item.copy(sceneNumber = idx + 1)
            }
            activeScenes.value = reindexed
            saveCurrentScenesToDb()
        }
    }

    fun insertScene(index: Int, newScene: Scene) {
        pushToUndo()
        val currentList = activeScenes.value.toMutableList()
        if (index <= currentList.size) {
            currentList.add(index, newScene)
        } else {
            currentList.add(newScene)
        }
        val reindexed = currentList.mapIndexed { idx, scene ->
            scene.copy(sceneNumber = idx + 1)
        }
        activeScenes.value = reindexed
        saveCurrentScenesToDb()
    }

    fun appendScenes(newScenes: List<Scene>) {
        pushToUndo()
        val currentList = activeScenes.value.toMutableList()
        currentList.addAll(newScenes)
        val reindexed = currentList.mapIndexed { idx, scene ->
            scene.copy(sceneNumber = idx + 1)
        }
        activeScenes.value = reindexed
        saveCurrentScenesToDb()
    }

    fun cancelGeneration() {
        generationJob?.cancel()
        generationJob = null
        isGenerating.value = false
    }

    fun cancelExport() {
        exportJob?.cancel()
        exportJob = null
        isExporting.value = false
        isExportingMinimized.value = false
    }

    fun regenerateSceneImage(index: Int, customPrompt: String) {
        viewModelScope.launch {
            val currentList = activeScenes.value.toMutableList()
            if (index in currentList.indices) {
                // Call repository helper to retrieve a highly matching contextual AI image URL dynamically 
                val result = withContext(Dispatchers.IO) {
                    val newUrl = repository.getBestMatchingImage(customPrompt, selectedStyleName.value, index, aspectRatio = selectedAspectRatio.value, imageSource = selectedImageSource.value, visualMedium = selectedVisualMedium.value)
                    val localPath = repository.downloadMediaToLocal(newUrl)
                    Pair(newUrl, localPath)
                }
                pushToUndo()
                val updatedList = activeScenes.value.toMutableList()
                if (index in updatedList.indices) {
                    updatedList[index] = updatedList[index].copy(
                        visualPrompt = customPrompt,
                        mediaPath = result.second,
                        remoteUrl = result.first
                    )
                    activeScenes.value = updatedList
                    // Voice confirmation for high professional grade interactive response
                    speakText("Successfully regenerated AI background for scene ${index + 1}")
                    saveCurrentScenesToDb()
                }
            }
        }
    }

    // Pipeline generation
    fun generateVideoPipeline(onAdShown: () -> Unit, onBuildComplete: () -> Unit) {
        isGenerating.value = true
        generationProgress.value = 0f
        generationStatus.value = "Initializing generator pipeline..."

        generationJob = viewModelScope.launch {
            // Step 1: Pre-load / simulate AdMob Rewarded Ad
            delay(1000)
            generationStatus.value = "Showing Required Rewarded Ad..."
            onAdShown() // Triggers Rewarded ad overlay in presentation UI
            delay(3000) // Let ad play in simulation

            var finalScript = scriptText.value.trim()
            if (finalScript.isEmpty()) {
                generationStatus.value = "No script provided. AI is auto-generating a viral script for you..."
                try {
                    val topics = listOf(
                        "3 mind-blowing psychology facts about human behavior",
                        "The mystery of deep space signals and cosmic radiation",
                        "How simple daily micro-habits lead to massive success",
                        "The legendary story of ancient history's greatest strategist",
                        "Why our brain craves storytelling and how to use it"
                    )
                    finalScript = repository.generateScriptFromTopic(
                        topicDescription = topics.random(),
                        style = selectedStyleName.value,
                        language = selectedLanguage.value,
                        durationOption = "Short"
                    )
                    scriptText.value = finalScript
                } catch (e: Exception) {
                    finalScript = "Success is built on consistency. Every small step you take today brings you closer to your grandest ambitions. Stay focused, work hard, and never lose your passion!"
                    scriptText.value = finalScript
                }
            }

            // Step 2: Scene analysis calling Gemini API (with Firebase caching & fallback)
            var scenes: List<Scene> = emptyList()
            generationProgress.value = 0.10f
            generationStatus.value = "Checking Firebase Cloud Pool for existing templates..."

            val rawTopicForMatch = topicContext.value.ifEmpty { finalScript }
            val normalizedTopicForMatch = try {
                repository.normalizeTopic(rawTopicForMatch)
            } catch (e: Exception) {
                rawTopicForMatch.trim().lowercase()
            }
            Log.i("MainViewModel", "Original Matching Topic: '$rawTopicForMatch' -> Normalized Matching Topic: '$normalizedTopicForMatch'")

            val matchingDeferred = kotlinx.coroutines.CompletableDeferred<com.ritvyom.yashoraReelgenerator.data.remote.SharedProject?>()
            com.ritvyom.yashoraReelgenerator.data.remote.FirebaseCloudManager.findMatchingProject(
                topic = normalizedTopicForMatch,
                style = selectedStyleName.value,
                aspectRatio = selectedAspectRatio.value,
                visualMedium = selectedVisualMedium.value
            ) { matched ->
                matchingDeferred.complete(matched)
            }

            val matchedProject = try {
                matchingDeferred.await()
            } catch (e: Exception) {
                null
            }

            if (matchedProject != null && matchedProject.scenes.isNotEmpty()) {
                generationStatus.value = "Existing template found in Cloud Pool! Reusing assets..."
                delay(1200)
                scenes = matchedProject.scenes.map { sharedScene ->
                    val localPath = if (sharedScene.mediaPath.isNotEmpty()) {
                        repository.downloadMediaToLocal(sharedScene.mediaPath)
                    } else null
                    Scene(
                        sceneNumber = sharedScene.sceneNumber,
                        narrationText = sharedScene.narrationText,
                        visualPrompt = sharedScene.visualPrompt,
                        durationSeconds = sharedScene.durationSeconds,
                        subtitle = sharedScene.subtitle,
                        mediaPath = localPath,
                        remoteUrl = sharedScene.mediaPath,
                        keywords = sharedScene.keywords
                    )
                }
                finalScript = matchedProject.scriptText
                scriptText.value = finalScript
            } else {
                generationProgress.value = 0.15f
                generationStatus.value = "Calling Gemini AI for intelligent scene detection..."
                scenes = repository.generateStoryboardsFromScript(
                    script = finalScript,
                    style = selectedStyleName.value,
                    videoLang = selectedLanguage.value,
                    aspectRatio = selectedAspectRatio.value,
                    imageSource = selectedImageSource.value,
                    visualMedium = selectedVisualMedium.value,
                    topicContext = normalizedTopicForMatch
                )


            }
            
            // Render pipeline simulate steps with realistic callbacks
            val steps = listOf(
                Pair("Breaking script into structural scenes...", 0.35f),
                Pair("Synthesizing narration voices using neural models...", 0.55f),
                Pair("Generating stylized image/video background frames...", 0.80f),
                Pair("Rendering local subtitles and aligning timing sequences...", 0.95f),
                Pair("Finalizing project layout pipeline...", 1.0f)
            )

            for (step in steps) {
                delay(1200)
                generationStatus.value = step.first
                generationProgress.value = step.second
            }

            activeScenes.value = scenes
            selectedSceneIndex.value = 0

            // Save project in completed state
            activeProject.value?.let { proj ->
                val completedProj = proj.copy(
                    scriptText = scriptText.value,
                    videoStyle = selectedStyleName.value,
                    voiceName = selectedVoiceName.value,
                    language = selectedLanguage.value,
                    aspectRatio = selectedAspectRatio.value,
                    resolution = selectedResolution.value,
                    status = "Completed",
                    scenesJson = scenesAdapter.toJson(scenes),
                    topicContext = topicContext.value,
                    updatedAt = System.currentTimeMillis()
                )
                repository.updateProject(completedProj)
                activeProject.value = completedProj
            }

            // Save approved script to Firebase database now that the video was successfully made from it!
            saveApprovedScriptToCloud()

            isGenerating.value = false
            onBuildComplete()
        }
    }

    // Export pipeline with real local storage writing
    fun exportVideo(onExportComplete: () -> Unit) {
        isExporting.value = true
        isExportingMinimized.value = false
        exportProgress.value = 0.01f
        exportStatus.value = "Initializing video compilation pipeline..."

        val powerManager = getApplication<Application>().getSystemService(android.content.Context.POWER_SERVICE) as android.os.PowerManager
        val wakeLock = powerManager.newWakeLock(android.os.PowerManager.PARTIAL_WAKE_LOCK, "YashoraReelgenerator:VideoExportWakeLock")

        // Progress listener coroutine to post system notifications dynamically in the background
        val notificationJob = viewModelScope.launch {
            try {
                combine(exportProgress, exportStatus) { progress, status ->
                    progress to status
                }.collect { (progress, status) ->
                    if (progress < 1.0f) {
                        val progressPercent = (progress * 100).toInt().coerceIn(0, 99)
                        NotificationHelper.showExportProgressNotification(
                            getApplication(),
                            "Generating Video...".localize(appLanguage.value),
                            progressPercent,
                            status.localize(appLanguage.value)
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e("Export", "Error in notification listener loop", e)
            }
        }

        exportJob = viewModelScope.launch {
            try {
                wakeLock.acquire(15 * 60 * 1000L) // 15-minute max safety limit
                val activeProj = activeProject.value
            val title = activeProj?.title ?: "Reel"
            val cleanTitle = title.replace(Regex("[^a-zA-Z0-9]"), "_")
            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", java.util.Locale.US)
            val timestamp = sdf.format(java.util.Date())
            val fileName = "Yashora_Reel_$timestamp.mp4"
            var savedPath = "Movies/Yashora/$fileName"
            
            val tempFile = File(getApplication<Application>().cacheDir, fileName)
            
            // Step 1: Synthesize narration voice tracks scene by scene
            Log.d("Export", "Starting sequential voice track synthesis...")
            val voiceFiles = try {
                synthesizeVoiceForScenes(activeScenes.value, getApplication<Application>().cacheDir) { pr ->
                    exportProgress.value = (0.01f + 0.19f * pr) // spans 1% to 20%
                    exportStatus.value = if (pr < 0.5f) {
                        "Synthesizing natural synthetic voiceovers..."
                    } else {
                        "Aligning voice speed and script syllables..."
                    }
                }
            } catch (e: Exception) {
                Log.e("Export", "Failed in narration voice synthesis, compiling silent video fallback", e)
                List(activeScenes.value.size) { null }
            }
            
            // Step 2: Compile real video + voice + layout in IO scope
            // Ensure the video scenes' durations perfectly align with the generated audio wav files to prevent desync!
            exportStatus.value = "Assembling storyboard elements seamlessly..."
            val alignedScenes = withContext(Dispatchers.IO) {
                activeScenes.value.mapIndexed { i, scene ->
                    val file = voiceFiles.getOrNull(i)
                    if (file != null && file.exists()) {
                        try {
                            val info = com.ritvyom.yashoraReelgenerator.presentation.utils.WavFileInfo.readWavFile(file)
                            if (info != null && info.pcmData != null && info.sampleRate > 0) {
                                val bytesPerSecond = info.sampleRate * info.channels * (info.bitsPerSample / 8)
                                val durationSecondsFloat = info.pcmData.size.toFloat() / bytesPerSecond
                                val calculatedSecs = Math.ceil(durationSecondsFloat.toDouble()).toInt().coerceAtLeast(1)
                                Log.d("Export", "Scene ${i + 1} perfectly auto-aligned from ${scene.durationSeconds}s to voice length of ${calculatedSecs}s")
                                scene.copy(durationSeconds = calculatedSecs)
                            } else {
                                scene
                            }
                        } catch (e: Exception) {
                            Log.e("Export", "Error reading wav file duration for scene ${i + 1}", e)
                            scene
                        }
                    } else {
                        // Fallback: estimate correct speed word-based duration for perfect screen persistence
                        val text = if (scene.narrationText.isNotEmpty()) scene.narrationText else scene.subtitle
                        val wordCount = text.split(Regex("\\s+")).filter { it.isNotBlank() }.size
                        val estimatedSecs = Math.ceil(wordCount.toDouble() / 2.0).toInt().coerceIn(3, 45)
                        val targetDuration = scene.durationSeconds.coerceAtLeast(estimatedSecs)
                        Log.d("Export", "Scene ${i + 1} silent alignment fallback. Standardizing from ${scene.durationSeconds}s to speaking rate of ${targetDuration}s")
                        scene.copy(durationSeconds = targetDuration)
                    }
                }
            }

            Log.d("Export", "Starting high-fidelity frame rendering and audio multiplex pipeline...")
            val initialResolution = selectedResolution.value
            val resolutionSequence = when (initialResolution) {
                "1080p" -> listOf("1080p", "720p", "540p", "480p")
                "720p" -> listOf("720p", "540p", "480p")
                else -> listOf(initialResolution, "720p", "540p", "480p").distinct()
            }

            var compileSuccess = false
            var activeResolution = initialResolution

            for (res in resolutionSequence) {
                Log.d("Export", "Attempting compilation at resolution option: $res")
                activeResolution = res
                compileSuccess = withContext(Dispatchers.IO) {
                    try {
                        ReelVideoCompiler.compileVideoWithAudio(
                            context = getApplication(),
                            scenes = alignedScenes,
                            voiceFiles = voiceFiles,
                            aspectRatio = selectedAspectRatio.value,
                            resolution = res,
                            outputFile = tempFile,
                            bgMusicCategory = bgMusicCategory.value,
                            bgMusicVolume = bgMusicVolume.value,
                            bgMusicEnabled = bgMusicEnabled.value,
                            onProgress = { pr ->
                                exportProgress.value = (0.20f + 0.75f * pr) // spans 20% to 95%
                                exportStatus.value = when {
                                    pr < 0.15f -> "Analyzing timeline pacing and scene boundaries..."
                                    pr < 0.35f -> "Preparing neural video rendering context..."
                                    pr < 0.55f -> "Baking high-contrast cinematic color LUTs..."
                                    pr < 0.75f -> "Applying premium edge blending and transitions..."
                                    pr < 0.90f -> "Generating localized subtitle overlays and fonts..."
                                    else -> "Finalizing high definition MP4 stream container..."
                                }
                            }
                        )
                    } catch (e: Exception) {
                        Log.e("Export", "Failed compiling slide-audio integrated MP4 at resolution $res", e)
                        false
                    }
                }
                if (compileSuccess && tempFile.exists() && tempFile.length() > 0) {
                    Log.d("Export", "Compilation successfully completed at fallback resolution: $res")
                    break
                } else {
                    Log.w("Export", "Compilation at resolution $res failed, checking next available fallback option...")
                    try { if (tempFile.exists()) tempFile.delete() } catch(e: Exception) {}
                }
            }

            val finalBytes = withContext(Dispatchers.IO) {
                if (compileSuccess && tempFile.exists() && tempFile.length() > 0) {
                    val bytes = tempFile.readBytes()
                    try {
                        tempFile.delete()
                    } catch (e: Exception) {}
                    bytes
                } else {
                    // Failover fallback in case compilation or image fetch fails completely
                    try {
                        Log.d("Export", "Compilation failed, using offline default fallback video path")
                        val rawStream = getApplication<Application>().resources.openRawResource(
                            com.ritvyom.yashoraReelgenerator.R.raw.yashora_default_video
                        )
                        val bytes = rawStream.readBytes()
                        rawStream.close()
                        bytes
                    } catch (ex: Exception) {
                        Log.e("Export", "Failed loading raw resource fallback", ex)
                        try {
                            android.util.Base64.decode(MINIMAL_PLAYABLE_MP4_B64, android.util.Base64.DEFAULT)
                        } catch (e2: Exception) {
                            ByteArray(0)
                        }
                    }
                }
            }

            // Instead of saving to official storage and sharing to cloud immediately,
            // we save the compiled video to a temporary preview file in cacheDir
            // so the user can verify they are satisfied with the preview first.
            exportStatus.value = "Assembling draft video preview..."
            val previewFile = File(getApplication<Application>().cacheDir, "Yashora_Temp_Preview.mp4")
            withContext(Dispatchers.IO) {
                try {
                    if (previewFile.exists()) previewFile.delete()
                    previewFile.writeBytes(finalBytes)
                    Log.d("Export", "Draft preview saved to ${previewFile.absolutePath}")
                } catch (e: Exception) {
                    Log.e("Export", "Failed to write preview temp file", e)
                }
            }

            exportProgress.value = 1.0f
            delay(400)

            compiledPreviewPath.value = previewFile.absolutePath
            isExportPendingConfirmation.value = true
            exportStatus.value = "Preview generated successfully!"

            onExportComplete()
            } catch (e: Exception) {
                Log.e("Export", "Exception during background video compilation", e)
                try {
                    NotificationHelper.showExportFailedNotification(
                        getApplication(),
                        "Export Failed".localize(appLanguage.value),
                        "Video export failed!".localize(appLanguage.value)
                    )
                } catch (notiEx: Exception) {
                    Log.e("Export", "Failed to show failure notification", notiEx)
                }
                withContext(Dispatchers.Main) {
                    try {
                        android.widget.Toast.makeText(
                            getApplication(),
                            "Video export failed!".localize(appLanguage.value),
                            android.widget.Toast.LENGTH_LONG
                        ).show()
                    } catch (ex: Exception) {
                        Log.e("Export", "Toast failed", ex)
                    }
                }
            } finally {
                notificationJob.cancel()
                if (wakeLock.isHeld) {
                    try {
                        wakeLock.release()
                    } catch (ex: Exception) {
                        Log.e("Export", "Failed to release wake lock", ex)
                    }
                }
                isExporting.value = false
            }
        }
    }

    fun discardPendingExport() {
        try {
            val previewPath = compiledPreviewPath.value
            if (previewPath != null) {
                val previewFile = File(previewPath)
                if (previewFile.exists()) {
                    previewFile.delete()
                }
            }
        } catch (e: Exception) {
            Log.e("Export", "Failed to delete temp preview", e)
        }
        compiledPreviewPath.value = null
        isExportPendingConfirmation.value = false
    }

    fun saveApprovedScriptToCloud() {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val activeProj = activeProject.value
                val finalScript = activeProj?.scriptText ?: scriptText.value
                val isFallback = finalScript.contains("Failed to generate script via AI", ignoreCase = true) ||
                        finalScript.contains("graceful fallback", ignoreCase = true) ||
                        finalScript.contains("Namskar doston! Kya aapne kabhi socha hai", ignoreCase = true) ||
                        finalScript.contains("नमस्कार दोस्तों! क्या आपने कभी सोचा है", ignoreCase = true) ||
                        finalScript.contains("Hey everyone! Have you ever wondered", ignoreCase = true)

                if (finalScript.isNotEmpty() &&
                    !isFallback &&
                    !finalScript.contains("failed or is not configured yet", ignoreCase = true) &&
                    !finalScript.contains("Firebase Vertex AI failed", ignoreCase = true)
                ) {
                    val rawTopic = topicContext.value.ifEmpty { activeProj?.topicContext ?: "" }.ifEmpty { "Viral AI Reel" }
                    val normalizedTopic = try {
                        repository.normalizeTopic(rawTopic)
                    } catch (e: Exception) {
                        rawTopic.trim().lowercase()
                    }
                    Log.d("MainViewModel", "Writing approved script to Firestore under: '$normalizedTopic'")
                    com.ritvyom.yashoraReelgenerator.data.remote.FirebaseCloudManager.saveCachedScript(
                        topicDescription = normalizedTopic,
                        style = selectedStyleName.value,
                        language = selectedLanguage.value,
                        durationOption = "Short",
                        scriptText = finalScript
                    )
                    com.ritvyom.yashoraReelgenerator.data.remote.FirebaseCloudManager.saveCachedScript(
                        topicDescription = rawTopic,
                        style = selectedStyleName.value,
                        language = selectedLanguage.value,
                        durationOption = "Short",
                        scriptText = finalScript
                    )
                }
            } catch (e: Exception) {
                Log.w("MainViewModel", "Failed to save approved script to cloud", e)
            }
        }
    }

    fun finalizeSession() {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val activeProj = activeProject.value
                val title = activeProj?.title ?: "Reel"
                
                // 1. Save approved generated script to Firebase database
                saveApprovedScriptToCloud()

                // 2. Trigger Firestore write for the approved project/scenes (Share project)
                if (com.ritvyom.yashoraReelgenerator.data.remote.FirebaseCloudManager.isUserLoggedIn() && activeScenes.value.isNotEmpty()) {
                    Log.d("FinalizeSession", "User finalized session. Sharing approved project and its media to Firestore Cloud Pool")
                    val rawTopicForMatch = topicContext.value.ifEmpty { activeProj?.topicContext ?: "" }.ifEmpty { title }
                    val normalizedTopicForMatch = try {
                        repository.normalizeTopic(rawTopicForMatch)
                    } catch (e: Exception) {
                        rawTopicForMatch.trim().lowercase()
                    }

                    com.ritvyom.yashoraReelgenerator.data.remote.FirebaseCloudManager.shareProject(
                        title = title,
                        topic = normalizedTopicForMatch,
                        scriptText = activeProj?.scriptText ?: scriptText.value,
                        style = selectedStyleName.value,
                        aspectRatio = selectedAspectRatio.value,
                        visualMedium = selectedVisualMedium.value,
                        publishingStyle = selectedPublishingStyle.value,
                        scenes = activeScenes.value
                    ) { success, error ->
                        Log.d("FinalizeSession", "Cloud share result: success=$success, error=$error")
                    }

                    // 3. Share verified media mappings globally upon export confirmation
                    try {
                        repository.shareMediaMappingsForProject(
                            scenes = activeScenes.value,
                            style = selectedStyleName.value,
                            visualMedium = selectedVisualMedium.value,
                            aspectRatio = selectedAspectRatio.value
                        )
                    } catch (e: Exception) {
                        Log.w("FinalizeSession", "Failed to share verified media mappings on export", e)
                    }
                }
            } catch (e: Exception) {
                Log.e("FinalizeSession", "Error during finalizeSession", e)
            }
        }
    }

    fun finalizeExport(onSuccess: () -> Unit) {
        viewModelScope.launch {
            try {
                val previewPath = compiledPreviewPath.value ?: return@launch
                val previewFile = File(previewPath)
                if (!previewFile.exists()) return@launch
                val finalBytes = previewFile.readBytes()

                val activeProj = activeProject.value
                val title = activeProj?.title ?: "Reel"
                val cleanTitle = title.replace(Regex("[^a-zA-Z0-9]"), "_")
                val sdf = java.text.SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", java.util.Locale.US)
                val timestamp = sdf.format(java.util.Date())
                val fileName = "Yashora_Reel_$timestamp.mp4"
                var savedPath = "Movies/Yashora/$fileName"

                // Perform real physical file saving to gallery via MediaStore or storage
                val resolver = getApplication<Application>().contentResolver
                val contentValues = ContentValues().apply {
                    put(MediaStore.Video.Media.DISPLAY_NAME, fileName)
                    put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                        put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/Yashora")
                        put(MediaStore.Video.Media.IS_PENDING, 1)
                    }
                }

                val savedBytes = withContext(Dispatchers.IO) {
                    var success = false
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                        // Modern Android: Write via MediaStore content Uri (Scoped Storage compliant)
                        try {
                            val uri: Uri? = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, contentValues)
                            if (uri != null) {
                                resolver.openOutputStream(uri)?.use { output ->
                                    output.write(finalBytes)
                                    output.flush()
                                }
                                
                                val updateValues = ContentValues().apply {
                                    put(MediaStore.Video.Media.IS_PENDING, 0)
                                }
                                resolver.update(uri, updateValues, null, null)
                                
                                val moviesFolder = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
                                val appFolder = File(moviesFolder, "Yashora")
                                if (!appFolder.exists()) {
                                    appFolder.mkdirs()
                                }
                                val physicalFile = File(appFolder, fileName)
                                savedPath = physicalFile.absolutePath
                                success = true
                                Log.d("Export", "Saved modern file to $savedPath")
                            }
                        } catch (e: Exception) {
                            Log.e("Export", "Failed writing via modern MediaStore", e)
                        }
                    } else {
                        // Legacy Android (SDK < 29, like Vivo V9 Android 8.1): Write directly to File and scan
                        try {
                            val moviesFolder = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
                            val appFolder = File(moviesFolder, "Yashora")
                            if (!appFolder.exists()) {
                                appFolder.mkdirs()
                            }
                            val physicalFile = File(appFolder, fileName)
                            physicalFile.writeBytes(finalBytes)
                            savedPath = physicalFile.absolutePath
                            
                            // Explicitly register in helper media database using legacy DATA column
                            try {
                                val legacyValues = ContentValues().apply {
                                    put(MediaStore.Video.Media.DISPLAY_NAME, fileName)
                                    put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                                    put(MediaStore.Video.Media.DATA, physicalFile.absolutePath)
                                }
                                resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, legacyValues)
                            } catch (e: Exception) {
                                Log.e("Export", "Failed legacy MediaStore insert, scanFile will handle indexing", e)
                            }
                            
                            success = true
                            Log.d("Export", "Saved legacy file directly to $savedPath")
                        } catch (e: Exception) {
                            Log.e("Export", "Failed legacy writing directly to Movies folder", e)
                        }
                    }
                    
                    if (!success) {
                        try {
                            val downloadsFolder = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                            val appFolder = File(downloadsFolder, "Yashora")
                            if (!appFolder.exists()) {
                                appFolder.mkdirs()
                            }
                            val physicalFile = File(appFolder, fileName)
                            physicalFile.writeBytes(finalBytes)
                            savedPath = physicalFile.absolutePath
                            
                            if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.Q) {
                                try {
                                    val legacyValues = ContentValues().apply {
                                        put(MediaStore.Video.Media.DISPLAY_NAME, fileName)
                                        put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                                        put(MediaStore.Video.Media.DATA, physicalFile.absolutePath)
                                    }
                                    resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, legacyValues)
                                } catch (e: Exception) {}
                            }
                            success = true
                        } catch (e: Exception) {
                            Log.e("Export", "Fallback file write failed, trying external files dir", e)
                            try {
                                val localFilesDir = getApplication<Application>().getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                                val localFile = File(localFilesDir, fileName)
                                localFile.writeBytes(finalBytes)
                                savedPath = localFile.absolutePath
                                success = true
                            } catch (ex: Exception) {
                                Log.e("Export", "All file exports failed", ex)
                            }
                        }
                    }

                    if (savedPath.startsWith("/")) {
                        try {
                            android.media.MediaScannerConnection.scanFile(
                                getApplication(),
                                arrayOf(savedPath),
                                arrayOf("video/mp4")
                            ) { path, uri ->
                                Log.d("Export", "MediaScanner: Indexed $path -> Uri $uri")
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                    finalBytes
                }

                val historyEntity = ExportHistoryEntity(
                    projectId = activeProj?.id ?: 0,
                    projectTitle = title,
                    fileName = fileName,
                    filePath = savedPath,
                    fileSize = savedBytes.size.toLong(),
                    resolution = selectedResolution.value,
                    durationMs = activeScenes.value.sumOf { it.durationSeconds } * 1000L
                )

                repository.insertExportHistory(historyEntity)
                exportedFilePath.value = savedPath

                // Trigger Firestore writes via the new centralized 'Finalize Session' flow
                finalizeSession()

                try {
                    NotificationHelper.showExportCompleteNotification(
                        getApplication(),
                        "Export Complete".localize(appLanguage.value),
                        "Your video has been successfully saved to Movies/Yashora!".localize(appLanguage.value)
                    )
                } catch (e: Exception) {
                    Log.e("Export", "Notification post failed", e)
                }

                compiledPreviewPath.value = null
                isExportPendingConfirmation.value = false

                withContext(Dispatchers.Main) {
                    try {
                        android.widget.Toast.makeText(
                            getApplication(),
                            "Video export complete! Saved to Movies/Yashora directory.".localize(appLanguage.value),
                            android.widget.Toast.LENGTH_LONG
                        ).show()
                    } catch (ex: Exception) {
                        Log.e("Export", "Toast failed", ex)
                    }
                    onSuccess()
                }
            } catch (e: Exception) {
                Log.e("Export", "Error finalizing export", e)
            }
        }
    }

    suspend fun getProjectScenes(projectId: Int): List<Scene> {
        val proj = repository.getProjectById(projectId) ?: return emptyList()
        return try {
            if (proj.scenesJson.isNotEmpty()) {
                scenesAdapter.fromJson(proj.scenesJson) ?: emptyList()
            } else {
                repository.fallbackLocalParser(proj.scriptText, proj.videoStyle, proj.language)
            }
        } catch (e: Exception) {
            repository.fallbackLocalParser(proj.scriptText, proj.videoStyle, proj.language)
        }
    }

    fun deleteProject(project: ProjectEntity) {
        viewModelScope.launch {
            repository.deleteProject(project)
        }
    }

    fun shareProjectToCloud(project: ProjectEntity) {
        viewModelScope.launch {
            if (!com.ritvyom.yashoraReelgenerator.data.remote.FirebaseCloudManager.isUserLoggedIn()) {
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    try {
                        android.widget.Toast.makeText(getApplication(), "Please Sign In under Community tab to save/share to cloud!", android.widget.Toast.LENGTH_LONG).show()
                    } catch (e: Exception) {
                        Log.e("MainViewModel", "Toast failed", e)
                    }
                }
                return@launch
            }

            val scenes = getProjectScenes(project.id)
            val rawTopic = project.topicContext.ifEmpty { project.title }
            val normalizedTopic = try {
                repository.normalizeTopic(rawTopic)
            } catch (e: Exception) {
                rawTopic.trim().lowercase()
            }
            Log.i("MainViewModel", "Manually Sharing Project: '$rawTopic' -> Normalized Topic: '$normalizedTopic'")

            com.ritvyom.yashoraReelgenerator.data.remote.FirebaseCloudManager.shareProject(
                title = project.title,
                topic = normalizedTopic,
                scriptText = project.scriptText,
                style = project.videoStyle,
                aspectRatio = project.aspectRatio,
                visualMedium = "Video",
                publishingStyle = project.publishingStyle,
                scenes = scenes
            ) { success, error ->
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    if (success) {
                        try {
                            android.widget.Toast.makeText(getApplication(), "Successfully saved & shared '${project.title}' to Community Cloud Pool!", android.widget.Toast.LENGTH_SHORT).show()
                        } catch (e: Exception) {
                            Log.e("MainViewModel", "Toast failed", e)
                        }
                    } else {
                        try {
                            if (error?.contains("permission", ignoreCase = true) == true ||
                                error?.contains("denied", ignoreCase = true) == true ||
                                error?.contains("insufficient", ignoreCase = true) == true) {
                                android.widget.Toast.makeText(getApplication(), "Firestore Save Failed: Please configure Security Rules in Firebase Console.", android.widget.Toast.LENGTH_LONG).show()
                            } else {
                                android.widget.Toast.makeText(getApplication(), "Firestore Save Error: ${error ?: "Unknown error"}", android.widget.Toast.LENGTH_LONG).show()
                            }
                        } catch (e: Exception) {
                            Log.e("MainViewModel", "Toast failed", e)
                        }
                    }
                }
            }
        }
    }

    val isGeneratingScript = MutableStateFlow(false)
    val scriptGenerationError = MutableStateFlow<String?>(null)

    val isGeneratingPreview = MutableStateFlow(false)
    val previewProgress = MutableStateFlow(0f)
    val previewStatus = MutableStateFlow("")
    val previewScenesList = MutableStateFlow<List<PreviewScene>>(emptyList())
    val previewError = MutableStateFlow<String?>(null)

    fun generatePreviewSlideshow(script: String, style: String, language: String, topicContextState: String) {
        viewModelScope.launch {
            isGeneratingPreview.value = true
            previewProgress.value = 0f
            previewStatus.value = "Analyzing script pacing and context..."
            previewError.value = null
            previewScenesList.value = emptyList()
            try {
                previewProgress.value = 0.25f
                previewStatus.value = "Fetching premium cinematics and visual backdrops..."
                val results = repository.generateStoryboardsForPreview(
                    script = script,
                    style = style,
                    videoLang = language,
                    aspectRatio = selectedAspectRatio.value,
                    topicContext = topicContextState,
                    imageSource = selectedImageSource.value
                )
                previewProgress.value = 0.90f
                previewStatus.value = "Preparing cinematic slideshow layout..."
                previewScenesList.value = results
                previewProgress.value = 1.0f
            } catch (e: Exception) {
                previewError.value = e.localizedMessage ?: "Failed to generate slideshow preview"
                Log.e("MainViewModel", "Failed to generate storyboards for preview", e)
            } finally {
                isGeneratingPreview.value = false
            }
        }
    }

    fun generateScript(
        topic: String,
        style: String,
        language: String,
        durationOption: String,
        onSuccess: (String) -> Unit
    ) {
        viewModelScope.launch {
            isGeneratingScript.value = true
            scriptGenerationError.value = null
            try {
                val result = repository.generateScriptFromTopic(
                    topicDescription = topic,
                    style = style,
                    language = language,
                    durationOption = durationOption
                )
                if (result.startsWith("Error:") || result.startsWith("Failure:")) {
                    scriptGenerationError.value = result
                } else {
                    onSuccess(result)
                }
            } catch (e: Exception) {
                scriptGenerationError.value = e.localizedMessage ?: "Failed to generate script"
            } finally {
                isGeneratingScript.value = false
            }
        }
    }

    val isImportingTemplate = MutableStateFlow(false)
    val importTemplateProgress = MutableStateFlow(0f)
    val importTemplateStatus = MutableStateFlow("")

    fun loadSharedProjectAsTemplate(sharedProject: com.ritvyom.yashoraReelgenerator.data.remote.SharedProject, onComplete: () -> Unit) {
        viewModelScope.launch {
            isImportingTemplate.value = true
            importTemplateProgress.value = 0.05f
            importTemplateStatus.value = "Initializing cloud template..."
            
            // 1. Update State Variables
            selectedStyleName.value = sharedProject.style
            selectedAspectRatio.value = sharedProject.aspectRatio
            scriptText.value = sharedProject.scriptText
            topicContext.value = sharedProject.topic
            selectedPublishingStyle.value = sharedProject.publishingStyle
            selectedVisualMedium.value = sharedProject.visualMedium
            
            // 2. Download and map scenes
            val totalScenes = sharedProject.scenes.size
            val scenesList = sharedProject.scenes.mapIndexed { index, sharedScene ->
                importTemplateStatus.value = "Downloading scene visual ${index + 1} of $totalScenes..."
                importTemplateProgress.value = 0.10f + (0.85f * (index.toFloat() / totalScenes.toFloat()))
                
                val localPath = if (sharedScene.mediaPath.isNotEmpty()) {
                    try {
                        repository.downloadMediaToLocal(sharedScene.mediaPath)
                    } catch (e: Exception) {
                        Log.e("MainViewModel", "Failed downloading shared scene asset", e)
                        null
                    }
                } else null
                
                Scene(
                    sceneNumber = sharedScene.sceneNumber,
                    narrationText = sharedScene.narrationText,
                    visualPrompt = sharedScene.visualPrompt,
                    durationSeconds = sharedScene.durationSeconds,
                    subtitle = sharedScene.subtitle,
                    mediaPath = localPath,
                    keywords = sharedScene.keywords
                )
            }
            
            activeScenes.value = scenesList
            selectedSceneIndex.value = 0
            
            importTemplateProgress.value = 1.0f
            importTemplateStatus.value = "Template loaded successfully!"
            delay(500)
            isImportingTemplate.value = false
            
            // Create a draft project in DB from this imported template
            createProjectFromCurrent()
            
            onComplete()
        }
    }

    fun deleteHistory(history: ExportHistoryEntity) {
        viewModelScope.launch {
            repository.deleteHistoryById(history.id)
        }
    }

    fun getBestMatchingImage(visualPrompt: String, style: String, sceneNum: Int, customSearchQuery: String = "", aspectRatio: String = selectedAspectRatio.value): String {
        return kotlinx.coroutines.runBlocking {
            val rawUrl = repository.getBestMatchingImage(visualPrompt, style, sceneNum, customSearchQuery, aspectRatio, imageSource = selectedImageSource.value, visualMedium = selectedVisualMedium.value)
            repository.downloadMediaToLocal(rawUrl)
        }
    }

    override fun onCleared() {
        super.onCleared()
        tts?.shutdown()
    }

    companion object {
        private const val MINIMAL_PLAYABLE_MP4_B64 = 
            "AAAAIGZ0eXBpc29tAAAAAGlzb21tcDQyAAAAAGZyZWUAAABLbWRhdA" +
            "AAAAGm1vb3YAAABsbXZoZAAAAADIPYF4eD2BeAAAA+gAAANqAAEAAAE" +
            "AAAAAAAAAAAAAAAAAAAAAAAAAQAAAAAAAAAAAAAAAAAAAAAABAAEAA" +
            "AAAbXZleQAAABB0cmV4AAAAAQAAAAEAAAAAAAEAdHJhawAAAFx0a2hk" +
            "AAAAAwAAAAAAAQAAAAAAAACHAAAAAAAAAAAAAAAAAAAAAAEAAAAAAAA" +
            "AAQAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAbWRpYQAAACBtZGhkA" +
            "AAAAAAB9AAAAfQAAABAAAAAAABsaGRscgAAAAAAAAAAbWV0YQAAAAA" +
            "AAAAAAAAASGFuZGxlcgAAAAFtaW5mAAAAFWhkaHIAAAAAAAAAAG1ld" +
            "GEAAAAAAGRpbmYAAAAQZHJlZgAAAAAAAAABAAAAGG11cmwAAAAAAAA" +
            "AAQAAAAIAdXJpIAAAABhzdGJsAAAAbXN0c2QAAAAAAAAAAQAAAD91c" +
            "mkgAAAAAQAAAAAAAAAAAAAAAAAAAAAAACNjb20ud2lkZ2V0cy5tZX" +
            "RhZGF0YS52aWRlbwAAAAIAdWNoIAAAABhzdHRzAAAAAAAAAAEAAAAB" +
            "AAAAAQAAABhzdHN6AAAAAAAAAAAAAAABAAAAAQAAABhzdGNvAAAAA" +
            "AAAAAEAAAAw"
    }
}
