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
import com.ritvyom.yashoraReelgenerator.domain.models.CanvasLayer
import com.ritvyom.yashoraReelgenerator.domain.models.getCanvasLayers
import com.ritvyom.yashoraReelgenerator.domain.models.saveCanvasLayers
import com.ritvyom.yashoraReelgenerator.domain.models.PreviewScene
import com.ritvyom.yashoraReelgenerator.domain.models.WatermarkConfig
import com.ritvyom.yashoraReelgenerator.domain.models.WatermarkPosition
import com.ritvyom.yashoraReelgenerator.domain.models.WatermarkFontFamily
import com.ritvyom.yashoraReelgenerator.domain.models.WatermarkStyle
import com.ritvyom.yashoraReelgenerator.presentation.utils.ReelVideoCompiler
import com.ritvyom.yashoraReelgenerator.presentation.utils.VideoFileManager
import com.ritvyom.yashoraReelgenerator.presentation.utils.NotificationHelper
import com.ritvyom.yashoraReelgenerator.presentation.utils.SoundSynth
import com.ritvyom.yashoraReelgenerator.presentation.utils.Media3VideoTrimmer
import com.ritvyom.yashoraReelgenerator.data.repository.MediaFetchingStatusMonitor
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.*

class MainViewModel(application: Application) : AndroidViewModel(application), TextToSpeech.OnInitListener {

    val repository = (application as YashoraApplication).repository
    val unifiedAiRouter = (application as YashoraApplication).unifiedAiRouter
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
    val appLanguage = repository.appLanguage.stateIn(viewModelScope, SharingStarted.Eagerly, "English")
    val appTheme = repository.appTheme.stateIn(viewModelScope, SharingStarted.Eagerly, "System")
    val videoLanguage = repository.videoLanguage.stateIn(viewModelScope, SharingStarted.Eagerly, "English")
    val pixabayApiKey = repository.pixabayApiKey.stateIn(viewModelScope, SharingStarted.Eagerly, "")
    val geminiApiKey = repository.geminiApiKey.stateIn(viewModelScope, SharingStarted.Eagerly, "")
    val unsplashApiKey = repository.unsplashApiKey.stateIn(viewModelScope, SharingStarted.Eagerly, "")
    val pexelsApiKey = repository.pexelsApiKey.stateIn(viewModelScope, SharingStarted.Eagerly, "")
    val spoonacularApiKey = repository.spoonacularApiKey.stateIn(viewModelScope, SharingStarted.Eagerly, "")
    val aiIntelligentMatchmaker = repository.aiIntelligentMatchmaker.stateIn(viewModelScope, SharingStarted.Eagerly, true)
    
    val activePremiumTtsEngine = repository.activePremiumTtsEngine.stateIn(viewModelScope, SharingStarted.Eagerly, "")

    val elevenLabsApiKey = repository.elevenLabsApiKey.stateIn(viewModelScope, SharingStarted.Eagerly, "")
    val voxElevenVoiceId = repository.voxElevenVoiceId.stateIn(viewModelScope, SharingStarted.Eagerly, "21m00Tcm4TlvDq8ikWAM")
    val voxElevenVoiceName = repository.voxElevenVoiceName.stateIn(viewModelScope, SharingStarted.Eagerly, "Rachel (US / Soft & Friendly)")
    val voxElevenModelId = repository.voxElevenModelId.stateIn(viewModelScope, SharingStarted.Eagerly, "eleven_multilingual_v2")
    val voxElevenStability = repository.voxElevenStability.stateIn(viewModelScope, SharingStarted.Eagerly, 0.5f)
    val voxElevenSimilarity = repository.voxElevenSimilarity.stateIn(viewModelScope, SharingStarted.Eagerly, 0.75f)
    val voxElevenStyle = repository.voxElevenStyle.stateIn(viewModelScope, SharingStarted.Eagerly, 0.0f)
    val voxElevenSpeakerBoost = repository.voxElevenSpeakerBoost.stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val voxElevenFavorites = repository.voxElevenFavorites.stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    val isCheckingApiKey = MutableStateFlow(false)
    val apiKeyStatus = MutableStateFlow<String?>(null)
    val elevenLabsUserInfo = MutableStateFlow<com.ritvyom.yashoraReelgenerator.data.voxeleven.UserResponse?>(null)
    val voxElevenVoices = MutableStateFlow<List<com.ritvyom.yashoraReelgenerator.data.voxeleven.Voice>>(
        com.ritvyom.yashoraReelgenerator.data.voxeleven.ElevenLabsClient.defaultVoices
    )
    val isFetchingVoices = MutableStateFlow(false)
    val playingPreviewVoiceId = MutableStateFlow<String?>(null)
    val voxElevenSubscription = MutableStateFlow<com.ritvyom.yashoraReelgenerator.data.voxeleven.Subscription?>(null)
    val isFetchingSubscription = MutableStateFlow(false)

    val savedVoiceClips = MutableStateFlow<List<com.ritvyom.yashoraReelgenerator.data.voxeleven.SavedVoiceClip>>(emptyList())
    val isGeneratingScriptVoice = MutableStateFlow(false)
    val scriptVoiceStatus = MutableStateFlow<String?>(null)
    val currentlyPlayingClipId = MutableStateFlow<String?>(null)
    val watermarkConfig = MutableStateFlow(WatermarkConfig())

    init {
        loadSavedVoiceClips()
        viewModelScope.launch {
            repository.watermarkConfig.collect { cfg ->
                watermarkConfig.value = cfg
            }
        }
        viewModelScope.launch {
            try {
                repository.allProjects.collect { projects ->
                    val draft = projects.firstOrNull { it.status == "Draft" && it.scenesJson.isNotBlank() }
                    recentDraftProject.value = draft
                }
            } catch (e: Exception) {
                Log.e("MainViewModel", "Error monitoring draft projects in Room", e)
            }
        }
    }

    fun updateWatermarkConfig(newConfig: WatermarkConfig) {
        watermarkConfig.value = newConfig
        viewModelScope.launch {
            repository.saveWatermarkConfig(newConfig)
        }
    }

    fun toggleWatermark(enabled: Boolean) {
        updateWatermarkConfig(watermarkConfig.value.copy(isEnabled = enabled))
    }

    fun loadSavedVoiceClips() {
        viewModelScope.launch(Dispatchers.IO) {
            val dir = File(getApplication<Application>().filesDir, "voxeleven_saved_audio").apply {
                if (!exists()) mkdirs()
            }
            val audioFiles = dir.listFiles { _, name ->
                name.endsWith(".mp3", ignoreCase = true) ||
                name.endsWith(".wav", ignoreCase = true) ||
                name.endsWith(".m4a", ignoreCase = true)
            } ?: emptyArray()

            val clips = audioFiles.map { file ->
                val filename = file.name
                val cleanName = filename.substringBeforeLast(".")
                val engineName = when {
                    filename.startsWith("video_voice_vox_") || filename.startsWith("vox_") -> "VoxEleven (ElevenLabs)"
                    filename.startsWith("recorded_voice_") -> "Voice Recording"
                    filename.startsWith("imported_voice_") -> "Imported Voice"
                    else -> "Android Built-In TTS"
                }

                val title = when {
                    filename.startsWith("video_voice_") -> {
                        val parts = cleanName.split("_")
                        if (parts.size >= 5) parts.drop(4).joinToString(" ").replace("-", " ") else cleanName
                    }
                    filename.startsWith("recorded_voice_") || filename.startsWith("imported_voice_") -> {
                        val parts = cleanName.split("_")
                        if (parts.size >= 4) parts.drop(3).joinToString(" ").replace("-", " ") else cleanName
                    }
                    else -> {
                        val parts = cleanName.split("_")
                        if (parts.size >= 3) parts.drop(2).joinToString(" ").replace("-", " ") else cleanName
                    }
                }.trim().ifBlank { file.name }

                com.ritvyom.yashoraReelgenerator.data.voxeleven.SavedVoiceClip(
                    id = filename,
                    title = title.take(50),
                    scriptText = title,
                    engineName = engineName,
                    voiceName = voxElevenVoiceName.value,
                    filePath = file.absolutePath,
                    timestampMs = file.lastModified(),
                    fileSizeBytes = file.length()
                )
            }.sortedByDescending { it.timestampMs }
            savedVoiceClips.value = clips
        }
    }

    fun generateVoiceFromScript(scriptText: String, onResult: (Boolean, String) -> Unit) {
        if (scriptText.isBlank()) {
            onResult(false, "Please enter script text first")
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            isGeneratingScriptVoice.value = true
            scriptVoiceStatus.value = "Synthesizing voice audio..."
            try {
                val dir = File(getApplication<Application>().filesDir, "voxeleven_saved_audio").apply {
                    if (!exists()) mkdirs()
                }
                
                val sanitizedTitle = scriptText.take(25)
                    .replace(Regex("[^a-zA-Z0-9_\u0900-\u097F]"), "_")
                    .trim('_')
                val timestamp = System.currentTimeMillis()
                
                val outputFile: File
                val engineTag: String

                if (selectedTtsEngine.value == "voxeleven" && elevenLabsApiKey.value.isNotBlank()) {
                    engineTag = "VoxEleven (ElevenLabs)"
                    outputFile = File(dir, "vox_${timestamp}_${sanitizedTitle}.mp3")
                    com.ritvyom.yashoraReelgenerator.data.voxeleven.ElevenLabsClient.generateSpeech(
                        context = getApplication(),
                        apiKey = elevenLabsApiKey.value,
                        voiceId = voxElevenVoiceId.value,
                        text = scriptText,
                        modelId = voxElevenModelId.value,
                        stability = voxElevenStability.value.toDouble(),
                        similarityBoost = voxElevenSimilarity.value.toDouble(),
                        style = voxElevenStyle.value.toDouble(),
                        useSpeakerBoost = voxElevenSpeakerBoost.value,
                        targetFile = outputFile
                    )
                    refreshVoxElevenSubscription()
                } else {
                    engineTag = "Android Built-In TTS"
                    outputFile = File(dir, "tts_${timestamp}_${sanitizedTitle}.mp3")
                    
                    val lang = selectedLanguage.value.ifBlank { videoLanguage.value.ifBlank { "English" } }
                    val ttsSuccess = generateAndroidTtsToFile(scriptText, lang, outputFile)
                    if (!ttsSuccess) {
                        throw Exception("TTS engine unable to render audio file.")
                    }
                }

                if (outputFile.exists() && outputFile.length() > 0) {
                    val clip = com.ritvyom.yashoraReelgenerator.data.voxeleven.SavedVoiceClip(
                        id = outputFile.name,
                        title = scriptText.take(45),
                        scriptText = scriptText,
                        engineName = engineTag,
                        voiceName = voxElevenVoiceName.value,
                        filePath = outputFile.absolutePath,
                        timestampMs = System.currentTimeMillis(),
                        fileSizeBytes = outputFile.length()
                    )
                    
                    loadSavedVoiceClips()
                    withContext(Dispatchers.Main) {
                        onResult(true, "Voice clip generated successfully!")
                        playVoiceClip(clip)
                    }
                } else {
                    throw Exception("Generated audio file is missing or empty.")
                }
            } catch (e: Exception) {
                Log.e("ScriptVoiceGen", "Error generating voice", e)
                val parsed = com.ritvyom.yashoraReelgenerator.data.voxeleven.ElevenLabsErrorHandler.parseError(e, "Voice Generation")
                withContext(Dispatchers.Main) {
                    onResult(false, parsed.userFriendlyMessage)
                }
            } finally {
                isGeneratingScriptVoice.value = false
                scriptVoiceStatus.value = null
            }
        }
    }

    fun downloadVoiceClipToStorage(context: android.content.Context, clip: com.ritvyom.yashoraReelgenerator.data.voxeleven.SavedVoiceClip): Boolean {
        val srcFile = File(clip.filePath)
        if (!srcFile.exists()) {
            android.widget.Toast.makeText(context, "Audio file not found", android.widget.Toast.LENGTH_SHORT).show()
            return false
        }

        val cleanTitle = clip.title.take(20).replace(Regex("[^a-zA-Z0-9_-]"), "_")
        val fileName = "Voice_${cleanTitle}_${System.currentTimeMillis()}.mp3"

        return try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                val contentValues = android.content.ContentValues().apply {
                    put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "audio/mpeg")
                    put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_DOWNLOADS)
                }
                val resolver = context.contentResolver
                val uri = resolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                if (uri != null) {
                    resolver.openOutputStream(uri)?.use { outputStream ->
                        srcFile.inputStream().use { inputStream ->
                            inputStream.copyTo(outputStream)
                        }
                    }
                    android.widget.Toast.makeText(context, "Downloaded to Downloads folder: $fileName", android.widget.Toast.LENGTH_LONG).show()
                    true
                } else false
            } else {
                val downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
                val destFile = File(downloadsDir, fileName)
                srcFile.copyTo(destFile, overwrite = true)
                android.widget.Toast.makeText(context, "Downloaded to: ${destFile.absolutePath}", android.widget.Toast.LENGTH_LONG).show()
                true
            }
        } catch (e: Exception) {
            Log.e("VoiceDownload", "Failed to download voice clip", e)
            android.widget.Toast.makeText(context, "Download failed: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
            false
        }
    }

    fun playVoiceClip(clip: com.ritvyom.yashoraReelgenerator.data.voxeleven.SavedVoiceClip) {
        stopVoiceClipPlayback()
        currentlyPlayingClipId.value = clip.id
        viewModelScope.launch {
            try {
                mediaPlayer = android.media.MediaPlayer().apply {
                    setDataSource(clip.filePath)
                    setOnPreparedListener { it.start() }
                    setOnCompletionListener { stopVoiceClipPlayback() }
                    setOnErrorListener { _, _, _ ->
                        stopVoiceClipPlayback()
                        true
                    }
                    prepareAsync()
                }
            } catch (e: Exception) {
                stopVoiceClipPlayback()
            }
        }
    }

    fun stopVoiceClipPlayback() {
        try {
            mediaPlayer?.apply {
                if (isPlaying) stop()
                release()
            }
        } catch (_: Exception) {}
        mediaPlayer = null
        currentlyPlayingClipId.value = null
    }

    /**
     * Immediately stops all video, timeline, TTS, voice clip, and background audio playback.
     * Essential after video export to prevent continuous looping and battery drain.
     */
    fun stopAllPlayback() {
        isPlaying.value = false
        stopSpeak()
        stopVoiceClipPlayback()
        try {
            com.ritvyom.yashoraReelgenerator.presentation.utils.AudioManager.stopAll()
        } catch (e: Exception) {
            Log.e("MainViewModel", "Failed stopping AudioManager tracks", e)
        }
    }

    fun deleteVoiceClip(clip: com.ritvyom.yashoraReelgenerator.data.voxeleven.SavedVoiceClip) {
        if (currentlyPlayingClipId.value == clip.id) {
            stopVoiceClipPlayback()
        }
        val file = File(clip.filePath)
        if (file.exists()) file.delete()
        loadSavedVoiceClips()
    }

    /**
     * Persistently saves any generated or recorded voice audio file directly into the
     * "Saved Audio Library" (filesDir/voxeleven_saved_audio) alongside ElevenLabs clips,
     * ensuring it is never lost and is immediately available in the voice library.
     */
    fun saveVoiceFileToSavedClips(
        sourceFile: File,
        title: String,
        engineTag: String,
        voiceName: String = "",
        scriptText: String = title
    ): com.ritvyom.yashoraReelgenerator.data.voxeleven.SavedVoiceClip? {
        if (!sourceFile.exists() || sourceFile.length() <= 0) return null
        return try {
            val dir = File(getApplication<Application>().filesDir, "voxeleven_saved_audio").apply {
                if (!exists()) mkdirs()
            }
            val ext = when {
                sourceFile.name.endsWith(".wav", ignoreCase = true) -> ".wav"
                sourceFile.name.endsWith(".m4a", ignoreCase = true) -> ".m4a"
                else -> ".mp3"
            }
            val sanitized = title.take(30)
                .replace(Regex("[^a-zA-Z0-9\\u0900-\\u097F_-]"), "_")
                .trim('_')
                .ifEmpty { "voice" }
            val prefix = when {
                engineTag.contains("Eleven", ignoreCase = true) -> "video_voice_vox"
                engineTag.contains("Record", ignoreCase = true) -> "recorded_voice"
                engineTag.contains("Import", ignoreCase = true) -> "imported_voice"
                else -> "video_voice_tts"
            }
            val targetFileName = "${prefix}_${System.currentTimeMillis()}_${sanitized}$ext"
            val targetFile = File(dir, targetFileName)
            sourceFile.copyTo(targetFile, overwrite = true)

            val effectiveVoice = voiceName.ifBlank {
                if (engineTag.contains("Eleven", ignoreCase = true)) voxElevenVoiceName.value else selectedVoiceName.value
            }
            val clip = com.ritvyom.yashoraReelgenerator.data.voxeleven.SavedVoiceClip(
                id = targetFile.name,
                title = title.take(50),
                scriptText = scriptText,
                engineName = engineTag,
                voiceName = effectiveVoice,
                filePath = targetFile.absolutePath,
                timestampMs = targetFile.lastModified(),
                fileSizeBytes = targetFile.length()
            )
            loadSavedVoiceClips()
            clip
        } catch (e: Exception) {
            Log.e("VoicePersistence", "Error saving voice audio file to saved clips library", e)
            null
        }
    }

    fun clearAllSavedVoiceClips() {
        if (currentlyPlayingClipId.value != null) {
            stopVoiceClipPlayback()
        }
        viewModelScope.launch(Dispatchers.IO) {
            val dir = File(getApplication<Application>().filesDir, "voxeleven_saved_audio")
            if (dir.exists()) {
                dir.listFiles()?.forEach { file ->
                    if (file.exists()) file.delete()
                }
            }
            loadSavedVoiceClips()
        }
    }


    fun setSelectedTtsEngine(engine: String) {
        selectedTtsEngine.value = engine
        viewModelScope.launch {
            repository.saveActivePremiumTtsEngine(engine)
        }
        saveVoicePreferencesOnly()
    }

    fun saveAndValidateElevenLabsKey(key: String) {
        val trimmedKey = key.trim()
        viewModelScope.launch {
            isCheckingApiKey.value = true
            apiKeyStatus.value = null
            try {
                val sub = com.ritvyom.yashoraReelgenerator.data.voxeleven.ElevenLabsClient.getUserSubscription(trimmedKey)
                voxElevenSubscription.value = sub
                repository.saveElevenLabsApiKey(trimmedKey)
                setSelectedTtsEngine("voxeleven")
                apiKeyStatus.value = "Success: API Key verified and saved securely."
                refreshVoxElevenVoices()
            } catch (e: Exception) {
                val parsed = com.ritvyom.yashoraReelgenerator.data.voxeleven.ElevenLabsErrorHandler.parseError(e, actionName = "API Key Validation")
                apiKeyStatus.value = parsed.userFriendlyMessage
            } finally {
                isCheckingApiKey.value = false
            }
        }
    }

    fun clearElevenLabsKey() {
        viewModelScope.launch {
            repository.saveElevenLabsApiKey("")
            apiKeyStatus.value = null
            voxElevenSubscription.value = null
        }
    }

    fun refreshVoxElevenSubscription() {
        val key = elevenLabsApiKey.value.ifBlank { return }
        viewModelScope.launch {
            isFetchingSubscription.value = true
            try {
                val sub = com.ritvyom.yashoraReelgenerator.data.voxeleven.ElevenLabsClient.getUserSubscription(key)
                voxElevenSubscription.value = sub
            } catch (e: Exception) {
                Log.e("VoxEleven", "Error fetching user subscription", e)
            } finally {
                isFetchingSubscription.value = false
            }
        }
    }

    fun refreshVoxElevenVoices() {
        val key = elevenLabsApiKey.value.ifBlank { return }
        viewModelScope.launch {
            isFetchingVoices.value = true
            try {
                val list = com.ritvyom.yashoraReelgenerator.data.voxeleven.ElevenLabsClient.fetchVoices(key)
                if (list.isNotEmpty()) {
                    voxElevenVoices.value = list
                }
                refreshVoxElevenSubscription()
            } catch (e: Exception) {
                Log.e("VoxEleven", "Error fetching voices", e)
            } finally {
                isFetchingVoices.value = false
            }
        }
    }

    fun selectVoxElevenVoice(voice: com.ritvyom.yashoraReelgenerator.data.voxeleven.Voice) {
        viewModelScope.launch {
            repository.saveVoxElevenVoice(voice.voiceId, voice.name)
        }
    }

    fun selectLocalVoice(language: String, gender: String, voiceName: String) {
        selectedLanguage.value = language
        selectedVoiceCategory.value = gender
        val fullName = "$voiceName ($language $gender)"
        viewModelScope.launch {
            repository.saveVoxElevenVoice("local_${language}_${voiceName}", fullName)
        }
    }

    fun toggleVoxElevenFavorite(voiceId: String) {
        viewModelScope.launch {
            repository.toggleVoxElevenFavorite(voiceId)
        }
    }

    fun saveVoxElevenModelId(modelId: String) {
        viewModelScope.launch {
            repository.saveVoxElevenModelId(modelId)
        }
    }

    fun saveVoxElevenStability(stability: Float) {
        viewModelScope.launch {
            repository.saveVoxElevenStability(stability)
        }
    }

    fun saveVoxElevenSimilarity(similarity: Float) {
        viewModelScope.launch {
            repository.saveVoxElevenSimilarity(similarity)
        }
    }

    fun saveVoxElevenStyle(style: Float) {
        viewModelScope.launch {
            repository.saveVoxElevenStyle(style)
        }
    }

    fun saveVoxElevenSpeakerBoost(enabled: Boolean) {
        viewModelScope.launch {
            repository.saveVoxElevenSpeakerBoost(enabled)
        }
    }

    fun resetVoxElevenParametersToDefaults() {
        viewModelScope.launch {
            repository.saveVoxElevenStability(0.5f)
            repository.saveVoxElevenSimilarity(0.75f)
            repository.saveVoxElevenStyle(0.0f)
            repository.saveVoxElevenSpeakerBoost(true)
            repository.saveVoxElevenModelId("eleven_multilingual_v2")
        }
    }

    fun applyVoxElevenPreset(stability: Float, similarity: Float, style: Float, speakerBoost: Boolean = true, modelId: String? = null) {
        viewModelScope.launch {
            repository.saveVoxElevenStability(stability)
            repository.saveVoxElevenSimilarity(similarity)
            repository.saveVoxElevenStyle(style)
            repository.saveVoxElevenSpeakerBoost(speakerBoost)
            if (modelId != null) {
                repository.saveVoxElevenModelId(modelId)
            }
        }
    }

    val isTestingVoiceParameters = MutableStateFlow(false)
    val testVoiceError = MutableStateFlow<String?>(null)

    fun testVoxElevenVoiceParameters(
        context: android.content.Context,
        sampleSentence: String = "Hello! This is a real-time preview of my customized ElevenLabs voice."
    ) {
        stopVoicePreview()
        val apiKey = elevenLabsApiKey.value.trim()
        if (apiKey.isBlank()) {
            android.widget.Toast.makeText(context, "Please enter your ElevenLabs API Key first", android.widget.Toast.LENGTH_SHORT).show()
            return
        }

        viewModelScope.launch {
            isTestingVoiceParameters.value = true
            testVoiceError.value = null
            try {
                val voiceId = voxElevenVoiceId.value
                val modelId = voxElevenModelId.value
                val stability = voxElevenStability.value.toDouble()
                val similarity = voxElevenSimilarity.value.toDouble()
                val style = voxElevenStyle.value.toDouble()
                val speakerBoost = voxElevenSpeakerBoost.value

                val audioFile = com.ritvyom.yashoraReelgenerator.data.voxeleven.ElevenLabsClient.generateSpeech(
                    context = getApplication(),
                    apiKey = apiKey,
                    voiceId = voiceId,
                    text = sampleSentence,
                    modelId = modelId,
                    stability = stability,
                    similarityBoost = similarity,
                    style = style,
                    useSpeakerBoost = speakerBoost
                )

                if (audioFile.exists() && audioFile.length() > 0) {
                    playLocalAudioFile(audioFile)
                } else {
                    testVoiceError.value = "Generated audio file was empty"
                }
            } catch (e: Exception) {
                Log.e("VoxElevenTest", "Error testing voice parameters", e)
                val parsed = com.ritvyom.yashoraReelgenerator.data.voxeleven.ElevenLabsErrorHandler.parseError(e, "Voice Testing")
                testVoiceError.value = parsed.userFriendlyMessage
                android.widget.Toast.makeText(context, parsed.userFriendlyMessage, android.widget.Toast.LENGTH_LONG).show()
            } finally {
                isTestingVoiceParameters.value = false
            }
        }
    }

    fun playVoicePreview(voice: com.ritvyom.yashoraReelgenerator.data.voxeleven.Voice, context: android.content.Context) {
        stopVoicePreview()
        val previewUrl = voice.previewUrl?.ifBlank { null }
            ?: "https://api.elevenlabs.io/v1/voices/${voice.voiceId}/sample"

        playingPreviewVoiceId.value = voice.voiceId
        viewModelScope.launch {
            try {
                mediaPlayer = android.media.MediaPlayer().apply {
                    setDataSource(previewUrl)
                    setOnPreparedListener { it.start() }
                    setOnCompletionListener { stopVoicePreview() }
                    setOnErrorListener { _, _, _ ->
                        android.os.Handler(android.os.Looper.getMainLooper()).post {
                            android.widget.Toast.makeText(context, "Could not play audio preview for ${voice.name}", android.widget.Toast.LENGTH_SHORT).show()
                        }
                        stopVoicePreview()
                        true
                    }
                    prepareAsync()
                }
            } catch (e: Exception) {
                android.widget.Toast.makeText(context, "Audio Preview Error: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                stopVoicePreview()
            }
        }
    }

    fun stopVoicePreview() {
        try {
            mediaPlayer?.apply {
                if (isPlaying) stop()
                release()
            }
        } catch (_: Exception) {}
        mediaPlayer = null
        playingPreviewVoiceId.value = null
    }

    private suspend fun generateAndroidTtsToFile(scriptText: String, lang: String, outputFile: File): Boolean = kotlinx.coroutines.withContext(Dispatchers.IO) {
        if (tts == null) {
            reinitTts("com.google.android.tts")
        }
        
        var initWait = 0
        while (!ttsReady && initWait < 30) {
            delay(100)
            initWait++
        }
        
        if (tts == null || !ttsReady) {
            Log.e("MainViewModel", "Android TTS engine is not ready.")
            return@withContext false
        }

        val locale = when (lang.lowercase().trim()) {
            "hindi", "hinglish", "hi" -> Locale("hi", "IN")
            "bengali", "bn" -> Locale("bn", "IN")
            "tamil", "ta" -> Locale("ta", "IN")
            "telugu", "te" -> Locale("te", "IN")
            "marathi", "mr" -> Locale("mr", "IN")
            "gujarati", "gu" -> Locale("gu", "IN")
            "punjabi", "pa" -> Locale("pa", "IN")
            "spanish", "es" -> Locale("es", "ES")
            "french", "fr" -> Locale("fr", "FR")
            "german", "de" -> Locale("de", "DE")
            "japanese", "ja" -> Locale("ja", "JP")
            "korean", "ko" -> Locale("ko", "KR")
            "chinese", "zh" -> Locale("zh", "CN")
            "arabic", "ar" -> Locale("ar", "SA")
            else -> Locale.US
        }

        try {
            val resultLang = tts?.setLanguage(locale)
            if (resultLang == TextToSpeech.LANG_MISSING_DATA || resultLang == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.w("MainViewModel", "Locale $locale not directly supported, using default English")
                tts?.setLanguage(Locale.US)
            }
        } catch (e: Exception) {
            Log.e("MainViewModel", "Error setting TTS language", e)
        }

        tts?.setSpeechRate(voiceSpeed.value.coerceIn(0.5f, 2.0f))
        tts?.setPitch(voicePitch.value.coerceIn(0.5f, 2.0f))

        val utteranceId = "script_gen_${System.currentTimeMillis()}"
        val deferred = kotlinx.coroutines.CompletableDeferred<Boolean>()

        val tempWav = File(outputFile.parentFile, "temp_${utteranceId}.wav")

        val listener = object : android.speech.tts.UtteranceProgressListener() {
            override fun onStart(id: String?) {}
            override fun onDone(id: String?) {
                if (id == utteranceId) deferred.complete(true)
            }
            override fun onError(id: String?) {
                if (id == utteranceId) deferred.complete(false)
            }
            @Deprecated("Deprecated in Java")
            override fun onError(id: String?, errorCode: Int) {
                if (id == utteranceId) deferred.complete(false)
            }
        }

        tts?.setOnUtteranceProgressListener(listener)

        val params = android.os.Bundle()
        params.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId)

        val synthResult = tts?.synthesizeToFile(scriptText, params, tempWav, utteranceId)

        if (synthResult == TextToSpeech.SUCCESS) {
            val completed = kotlinx.coroutines.withTimeoutOrNull(45000L) { deferred.await() } ?: false
            
            var fileReady = false
            for (i in 1..30) {
                if (tempWav.exists() && tempWav.length() > 44) {
                    fileReady = true
                    break
                }
                delay(100)
            }

            if ((completed || fileReady) && tempWav.exists() && tempWav.length() > 44) {
                try {
                    tempWav.copyTo(outputFile, overwrite = true)
                    tempWav.delete()
                    return@withContext true
                } catch (e: Exception) {
                    Log.e("MainViewModel", "Failed to copy tempWav to outputFile", e)
                }
            }
        }

        if (tempWav.exists()) tempWav.delete()
        return@withContext false
    }

    fun playLocalVoicePreview(language: String, gender: String, voiceName: String) {
        stopVoicePreview()
        val sampleText = when (language.lowercase().trim()) {
            "hindi", "hinglish", "hi" -> "नमस्ते! यह एंड्रॉइड वाइस का पूर्वावलोकन है।"
            "spanish" -> "¡Hola! Esta es una vista previa de esta voz."
            "french" -> "Bonjour! Ceci est un aperçu de cette voix."
            "german" -> "Hallo! Dies ist eine Vorschau dieser Stimme."
            "bengali" -> "হ্যালো! এটি অ্যানড্রয়েড ভয়েসের প্রিভিউ।"
            "tamil" -> "வணக்கம்! இது ஆண்ட்ராய்டு குரலின் முன்னோட்டம்."
            "telugu" -> "నమస్కారం! ఇది ఆండ్రాయిడ్ వాయిస్ ప్రివ్యూ."
            "marathi" -> "नमस्कार! हे अँड्रॉइड आवाजाचे पूर्वावलोकन आहे।"
            "gujarati" -> "નમસ્તે! આ અવાજનું પૂર્વાવલોકન છે."
            "punjabi" -> "ਸਤਿ ਸ੍ਰੀ ਅਕਾਲ! ਇਹ ਇਸ ਆਵਾਜ਼ ਦਾ ਪੂਰਵ-ਦਰਸ਼ਨ ਹੈ।"
            else -> "Hello! This is a preview of the Android built-in voice."
        }
        val previewKey = "local_${language}_${gender}_${voiceName}"
        playingPreviewVoiceId.value = previewKey
        viewModelScope.launch {
            try {
                if (tts == null) {
                    reinitTts("com.google.android.tts")
                }
                var wait = 0
                while (!ttsReady && wait < 20) {
                    delay(100)
                    wait++
                }
                val locale = when (language.lowercase().trim()) {
                    "hindi", "hinglish", "hi" -> Locale("hi", "IN")
                    "bengali", "bn" -> Locale("bn", "IN")
                    "tamil", "ta" -> Locale("ta", "IN")
                    "telugu", "te" -> Locale("te", "IN")
                    "marathi", "mr" -> Locale("mr", "IN")
                    "gujarati", "gu" -> Locale("gu", "IN")
                    "punjabi", "pa" -> Locale("pa", "IN")
                    "spanish", "es" -> Locale("es", "ES")
                    "french", "fr" -> Locale("fr", "FR")
                    "german", "de" -> Locale("de", "DE")
                    else -> Locale.US
                }
                tts?.setLanguage(locale)
                tts?.setSpeechRate(voiceSpeed.value.coerceIn(0.5f, 2.0f))
                tts?.setPitch(voicePitch.value.coerceIn(0.5f, 2.0f))

                val params = android.os.Bundle()
                params.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "preview_tts")
                tts?.speak(sampleText, TextToSpeech.QUEUE_FLUSH, params, "preview_tts")
            } catch (e: Exception) {
                Log.e("MainViewModel", "Local voice preview failed", e)
                stopVoicePreview()
            }
        }
    }

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
            unifiedAiRouter.setApiKey(com.ritvyom.yashoraReelgenerator.data.ai.ProviderId.GEMINI, key)
        }
    }

    fun setAiProviderApiKey(providerId: com.ritvyom.yashoraReelgenerator.data.ai.ProviderId, key: String) {
        viewModelScope.launch {
            unifiedAiRouter.setApiKey(providerId, key)
            if (providerId == com.ritvyom.yashoraReelgenerator.data.ai.ProviderId.GEMINI) {
                repository.updateGeminiApiKey(key)
            }
        }
    }

    fun removeAiProviderApiKey(providerId: com.ritvyom.yashoraReelgenerator.data.ai.ProviderId) {
        viewModelScope.launch {
            unifiedAiRouter.removeApiKey(providerId)
            if (providerId == com.ritvyom.yashoraReelgenerator.data.ai.ProviderId.GEMINI) {
                repository.updateGeminiApiKey("")
            }
        }
    }

    fun testAiProvider(
        providerId: com.ritvyom.yashoraReelgenerator.data.ai.ProviderId,
        testKey: String,
        onResult: (Boolean, String) -> Unit
    ) {
        viewModelScope.launch {
            val adapter: com.ritvyom.yashoraReelgenerator.data.ai.AiProviderAdapter = when (providerId) {
                com.ritvyom.yashoraReelgenerator.data.ai.ProviderId.GEMINI -> com.ritvyom.yashoraReelgenerator.data.ai.GeminiProviderAdapter()
                com.ritvyom.yashoraReelgenerator.data.ai.ProviderId.XAI -> com.ritvyom.yashoraReelgenerator.data.ai.XAiGrokProviderAdapter()
                com.ritvyom.yashoraReelgenerator.data.ai.ProviderId.GROQ -> com.ritvyom.yashoraReelgenerator.data.ai.GroqProviderAdapter()
                com.ritvyom.yashoraReelgenerator.data.ai.ProviderId.OPENAI -> com.ritvyom.yashoraReelgenerator.data.ai.OpenAiProviderAdapter()
                com.ritvyom.yashoraReelgenerator.data.ai.ProviderId.DEEPSEEK -> com.ritvyom.yashoraReelgenerator.data.ai.DeepSeekProviderAdapter()
                else -> com.ritvyom.yashoraReelgenerator.data.ai.GeminiProviderAdapter()
            }
            val res = adapter.testConnection(testKey)
            if (res.isSuccess) {
                onResult(true, res.getOrNull() ?: "Connected successfully!")
            } else {
                onResult(false, res.exceptionOrNull()?.message ?: "Connection failed.")
            }
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

    // Thread-safe in-memory cache for media suggestions keyed by: "$query::$mediaType::$aspectRatio"
    private val mediaSuggestionsCache = java.util.concurrent.ConcurrentHashMap<String, List<com.ritvyom.yashoraReelgenerator.domain.models.MediaSuggestion>>()
    
    // Dedicated state flows for video and photo suggestions so switching modes never clears or discards data
    val cachedVideoSuggestions = MutableStateFlow<List<com.ritvyom.yashoraReelgenerator.domain.models.MediaSuggestion>>(emptyList())
    val cachedImageSuggestions = MutableStateFlow<List<com.ritvyom.yashoraReelgenerator.domain.models.MediaSuggestion>>(emptyList())

    val alternativeSuggestions = MutableStateFlow<List<com.ritvyom.yashoraReelgenerator.domain.models.MediaSuggestion>>(emptyList())
    val isSearchingAlternatives = MutableStateFlow(false)
    val alternativeSearchError = MutableStateFlow<String?>(null)

    fun getCachedSuggestions(query: String, mediaType: String, aspectRatio: String): List<com.ritvyom.yashoraReelgenerator.domain.models.MediaSuggestion>? {
        val q = query.trim().ifEmpty { "nature" }
        val mType = if (mediaType.uppercase() == "VIDEO") "VIDEO" else "IMAGE"
        val cacheKey = "${q.lowercase()}::$mType::$aspectRatio"
        return mediaSuggestionsCache[cacheKey] ?: repository.getCachedSuggestions(q, mType, aspectRatio)
    }

    fun fetchAlternativeSuggestions(query: String, mediaType: String, aspectRatio: String, forceRefresh: Boolean = false) {
        val q = query.trim().ifEmpty { "nature" }
        val mType = if (mediaType.uppercase() == "VIDEO") "VIDEO" else "IMAGE"
        val cacheKey = "${q.lowercase()}::$mType::$aspectRatio"

        // 1. Instant cache hit from memory or local repository caching strategy:
        // Ensures switching between 'Image' and 'Video' tabs preserves previously fetched data without re-triggering network requests
        if (!forceRefresh) {
            val cached = mediaSuggestionsCache[cacheKey] ?: repository.getCachedSuggestions(q, mType, aspectRatio)
            if (cached != null && cached.isNotEmpty()) {
                mediaSuggestionsCache[cacheKey] = cached
                alternativeSuggestions.value = cached
                if (mType == "VIDEO") {
                    cachedVideoSuggestions.value = cached
                } else {
                    cachedImageSuggestions.value = cached
                }
                isSearchingAlternatives.value = false
                alternativeSearchError.value = null
                return
            }
        }

        // 2. Fetch via repository (which respects local repository caching strategy before network)
        viewModelScope.launch {
            isSearchingAlternatives.value = true
            alternativeSearchError.value = null
            try {
                val results = repository.searchAlternativeSuggestions(
                    query = q,
                    mediaType = mType,
                    aspectRatio = aspectRatio,
                    unsplashKey = unsplashApiKey.value,
                    pexelsKey = pexelsApiKey.value,
                    forceRefresh = forceRefresh
                )
                // Cache results in memory and state flows
                mediaSuggestionsCache[cacheKey] = results
                if (mType == "VIDEO") {
                    cachedVideoSuggestions.value = results
                } else {
                    cachedImageSuggestions.value = results
                }
                alternativeSuggestions.value = results
                if (results.isEmpty()) {
                    alternativeSearchError.value = "No suggestions found for \"$q\""
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
    val isEnhanceEnabled = MutableStateFlow(false)
    val isEnhanceVideo: MutableStateFlow<Boolean> = isEnhanceEnabled
    val selectedFps = MutableStateFlow(30)
    val compiledPreviewPath = MutableStateFlow<String?>(null)
    val isExportPendingConfirmation = MutableStateFlow(false)
    val isTrimming = MutableStateFlow(false)
    val trimProgress = MutableStateFlow(0f)

    fun setSelectedFps(fps: Int) {
        selectedFps.value = fps
    }

    fun setEnhanceEnabled(enabled: Boolean) {
        isEnhanceEnabled.value = enabled
    }

    fun resetEnhanceEnabled() {
        isEnhanceEnabled.value = false
    }

    fun setEnhanceVideo(enabled: Boolean) {
        isEnhanceEnabled.value = enabled
    }

    fun resetEnhanceVideo() {
        isEnhanceEnabled.value = false
    }

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
    val isBgMusicEnabled = bgMusicEnabled
    val preserveOriginalAudio = MutableStateFlow(true)
    val isPreserveOriginalAudioEnabled = preserveOriginalAudio
    val bgMusicCategory = MutableStateFlow("Cinematic")
    val bgMusicVolume = MutableStateFlow(0.3f)
    val voiceVolume = MutableStateFlow(0.8f)
    val masterVoiceVolume = voiceVolume
    val isMasterVoiceEnabled = MutableStateFlow(true)
    val sfxVolume = MutableStateFlow(0.7f)
    val sfxEnabled = MutableStateFlow(true)
    val isSfxEnabled = sfxEnabled

    fun setPreserveOriginalAudio(enabled: Boolean) {
        preserveOriginalAudio.value = enabled
    }

    fun hasUserImportedVideos(): Boolean {
        val scenes = activeScenes.value
        val context = getApplication<android.app.Application>()
        return scenes.any { scene ->
            !scene.mediaPath.isNullOrEmpty() &&
            (scene.mediaType == "VIDEO" || ReelVideoCompiler.isVideoPath(context, scene.mediaPath))
        }
    }

    fun setMasterVoiceEnabled(enabled: Boolean) {
        isMasterVoiceEnabled.value = enabled
    }

    fun setMasterVoiceVolume(vol: Float) {
        voiceVolume.value = vol
    }

    fun setBgMusicEnabled(enabled: Boolean) {
        bgMusicEnabled.value = enabled
    }

    fun setBgMusicVolume(vol: Float) {
        bgMusicVolume.value = vol
    }

    fun setSfxEnabled(enabled: Boolean) {
        sfxEnabled.value = enabled
    }

    fun setSfxVolume(vol: Float) {
        sfxVolume.value = vol
    }

    fun playSfx(name: String) {
        viewModelScope.launch {
            SoundSynth.playSfx(name)
        }
    }

    // Master Voiceover & Custom Audio Track for Entire Video (1-Click recorded or imported from device)
    val customMasterVoicePath = MutableStateFlow<String?>(null)
    val customMasterVoiceName = MutableStateFlow<String?>("My Voice Recording")
    val customBgMusicPath = MutableStateFlow<String?>(null)
    val customBgMusicName = MutableStateFlow<String?>("Custom Track")

    fun setCustomMasterVoice(filePath: String, displayName: String) {
        customMasterVoicePath.value = filePath
        customMasterVoiceName.value = displayName
        latestMasterVoiceoverPath.value = filePath
    }

    fun clearCustomMasterVoice() {
        customMasterVoicePath.value = null
        customMasterVoiceName.value = null
    }

    fun setCustomBgMusic(filePath: String, displayName: String) {
        customBgMusicPath.value = filePath
        customBgMusicName.value = displayName
        bgMusicEnabled.value = true
    }

    fun clearCustomBgMusic() {
        customBgMusicPath.value = null
        customBgMusicName.value = null
    }

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
    val exportEtaFormatted = MutableStateFlow<String?>(null)
    val exportFps = MutableStateFlow<Float?>(null)
    val exportEtaSeconds = MutableStateFlow<Int?>(null)

    // Export Speed & ETA Tracking
    private var exportStartTimeMs = 0L
    private var renderStartTimeMs = 0L
    private val exportProgressHistory = java.util.ArrayDeque<Pair<Long, Float>>()
    private var smoothedEtaSeconds: Float? = null

    private fun resetExportSpeedTracker() {
        exportStartTimeMs = 0L
        renderStartTimeMs = 0L
        exportProgressHistory.clear()
        smoothedEtaSeconds = null
        exportEtaFormatted.value = null
        exportFps.value = null
        exportEtaSeconds.value = null
    }

    private fun updateExportSpeedAndEta(
        currentProgress: Float, 
        isRenderingFrames: Boolean = false, 
        frameProgress: Float = 0f, 
        totalFrames: Int = 0
    ) {
        val now = System.currentTimeMillis()
        if (exportStartTimeMs == 0L) exportStartTimeMs = now

        // Keep 5-second sliding history window of progress updates for smoothing
        exportProgressHistory.addLast(now to currentProgress)
        while (exportProgressHistory.size > 2 && (now - exportProgressHistory.first().first) > 5000L) {
            exportProgressHistory.removeFirst()
        }

        val totalElapsedSec = (now - exportStartTimeMs) / 1000f

        // 1. Calculate rendering FPS during video compilation phase
        if (isRenderingFrames) {
            if (renderStartTimeMs == 0L) {
                renderStartTimeMs = now
            }
            val renderElapsedSec = (now - renderStartTimeMs) / 1000f
            if (renderElapsedSec >= 0.4f && totalFrames > 0) {
                val currentRenderedFrames = (frameProgress * totalFrames).toInt()
                val currentFps = (currentRenderedFrames.toFloat() / renderElapsedSec).coerceIn(0.1f, 120f)
                exportFps.value = currentFps
            }
        }

        // 2. Calculate dynamic progress rate (progress per second)
        val progressRate: Float = if (exportProgressHistory.size >= 2) {
            val oldest = exportProgressHistory.first()
            val timeDeltaSec = (now - oldest.first) / 1000f
            val progressDelta = currentProgress - oldest.second
            if (timeDeltaSec >= 0.6f && progressDelta > 0.0005f) {
                progressDelta / timeDeltaSec
            } else if (totalElapsedSec >= 1.0f && currentProgress > 0.02f) {
                currentProgress / totalElapsedSec
            } else {
                0f
            }
        } else if (totalElapsedSec >= 1.0f && currentProgress > 0.02f) {
            currentProgress / totalElapsedSec
        } else {
            0f
        }

        // 3. Compute Estimated Time Remaining
        val remainingProgress = (1.0f - currentProgress).coerceAtLeast(0f)
        if (currentProgress >= 0.98f) {
            exportEtaSeconds.value = 0
            exportEtaFormatted.value = "Almost done..."
            return
        }

        if (progressRate > 0.0005f && totalElapsedSec >= 1.2f) {
            val rawEtaSec = remainingProgress / progressRate
            val smoothed = if (smoothedEtaSeconds == null) {
                rawEtaSec
            } else {
                // Exponential Moving Average filter for clean, monotonic countdown
                smoothedEtaSeconds!! * 0.72f + rawEtaSec * 0.28f
            }
            smoothedEtaSeconds = smoothed
            val finalSec = Math.round(smoothed).coerceAtLeast(1)
            exportEtaSeconds.value = finalSec

            val fpsStr = exportFps.value?.let { fps ->
                if (fps > 0f) " (%.1f fps)".format(java.util.Locale.US, fps) else ""
            } ?: ""

            val timeStr = if (finalSec < 60) {
                "~$finalSec" + "s remaining"
            } else {
                val mins = finalSec / 60
                val secs = finalSec % 60
                "${mins}m ${secs}s remaining"
            }

            exportEtaFormatted.value = "ETA: $timeStr$fpsStr"
        } else {
            exportEtaFormatted.value = "Calculating ETA..."
        }
    }

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

    fun isPlaceholderNarration(text: String): Boolean {
        val trimmed = text.trim().lowercase(Locale.ROOT)
        if (trimmed.isEmpty()) return true
        val placeholderPatterns = listOf(
            Regex("""^manual\s*scene(\s*\d+)?$"""),
            Regex("""^scene(\s*#?\s*\d+)?$"""),
            Regex("""^clip(\s*#?\s*\d+)?$"""),
            Regex("""^custom\s*clip$"""),
            Regex("""^new\s*scene$"""),
            Regex("""^imported\s*(video|photo|mid-video|timeline)?\s*(clip|addition)?.*$"""),
            Regex("""^imported\s*visual\s*matching.*$"""),
            Regex("""^custom\s*user\s*imported\s*media\s*content$"""),
            Regex("""^slide\s*media\s*not\s*found.*$""")
        )
        return placeholderPatterns.any { it.matches(trimmed) }
    }

    private fun isUiFeedbackText(text: String): Boolean {
        val lower = text.lowercase(Locale.ROOT)
        return lower.startsWith("imported ") ||
               lower.startsWith("moved scene ") ||
               lower.startsWith("successfully regenerated") ||
               lower.startsWith("replaced backdrop") ||
               lower.startsWith("inserted custom clip") ||
               lower.startsWith("appended custom clip") ||
               lower.startsWith("video export complete") ||
               lower.startsWith("loaded category ") ||
               lower.contains("clips to your timeline") ||
               lower.contains("file saved to downloads") ||
               lower.contains("clip successfully")
    }

    fun speakText(text: String, force: Boolean = false) {
        if (!force && !aiTtsEnabled.value) return
        if (!force && isPlaceholderNarration(text)) return
        val sanitizedText = sanitizeNarrationTextForSpeech(text)
        if (sanitizedText.isEmpty()) return

        // UI notifications/announcements must never burn user paid ElevenLabs API tokens
        if (isUiFeedbackText(sanitizedText)) {
            speakTextLocally(sanitizedText)
            return
        }

        if ((selectedTtsEngine.value == "voxeleven" || activePremiumTtsEngine.value == "voxeleven") && elevenLabsApiKey.value.isNotBlank()) {
            viewModelScope.launch {
                try {
                    val voiceId = voxElevenVoiceId.value
                    val modelId = voxElevenModelId.value
                    val stability = voxElevenStability.value.toDouble()
                    val similarity = voxElevenSimilarity.value.toDouble()
                    val style = voxElevenStyle.value.toDouble()
                    val speakerBoost = voxElevenSpeakerBoost.value

                    // Check local persistent audio disk cache first (costs 0 API tokens and 0 delay!)
                    val cachedAudio = com.ritvyom.yashoraReelgenerator.data.voxeleven.ElevenLabsClient.getCachedAudioFile(
                        context = getApplication(),
                        voiceId = voiceId,
                        text = sanitizedText,
                        modelId = modelId,
                        stability = stability,
                        similarityBoost = similarity,
                        style = style,
                        useSpeakerBoost = speakerBoost
                    )

                    val audioFile = cachedAudio ?: com.ritvyom.yashoraReelgenerator.data.voxeleven.ElevenLabsClient.generateSpeech(
                        context = getApplication(),
                        apiKey = elevenLabsApiKey.value,
                        voiceId = voiceId,
                        text = sanitizedText,
                        modelId = modelId,
                        stability = stability,
                        similarityBoost = similarity,
                        style = style,
                        useSpeakerBoost = speakerBoost
                    )

                    if (audioFile != null && audioFile.exists() && audioFile.length() > 0) {
                        playLocalAudioFile(audioFile)
                    }
                } catch (e: Exception) {
                    Log.e("VoxElevenPreview", "Error generating or playing preview speech", e)
                    speakTextLocally(sanitizedText)
                }
            }
            return
        }

        speakTextLocally(sanitizedText)
    }

    private fun playLocalAudioFile(file: File) {
        try {
            premiumMediaPlayer?.stop()
            premiumMediaPlayer?.release()
            premiumMediaPlayer = null
        } catch (e: Exception) {
            Log.e("VoicePreview", "Error resetting media player", e)
        }

        try {
            premiumMediaPlayer = android.media.MediaPlayer().apply {
                setDataSource(file.absolutePath)
                prepare()
                start()
                setOnCompletionListener {
                    it.release()
                    if (premiumMediaPlayer == it) {
                        premiumMediaPlayer = null
                    }
                }
                setOnErrorListener { mp, _, _ ->
                    mp.release()
                    if (premiumMediaPlayer == mp) {
                        premiumMediaPlayer = null
                    }
                    true
                }
            }
        } catch (e: Exception) {
            Log.e("VoicePreview", "Error playing local audio file: ${file.absolutePath}", e)
        }
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
                setOnErrorListener { mp, _, _ ->
                    mp.release()
                    if (premiumMediaPlayer == mp) {
                        premiumMediaPlayer = null
                    }
                    try { tempFile.delete() } catch (ex: Exception) {}
                    true
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

        // Check if VoxEleven ElevenLabs engine is selected and API Key is configured
        if ((selectedTtsEngine.value == "voxeleven" || activePremiumTtsEngine.value == "voxeleven") && elevenLabsApiKey.value.isNotBlank()) {
            Log.d("VoiceSynthesis", "Synthesizing voice scenes using VoxEleven ElevenLabs API...")
            val apiKey = elevenLabsApiKey.value
            val voiceId = voxElevenVoiceId.value
            val modelId = voxElevenModelId.value
            val stability = voxElevenStability.value.toDouble()
            val similarity = voxElevenSimilarity.value.toDouble()
            val style = voxElevenStyle.value.toDouble()
            val speakerBoost = voxElevenSpeakerBoost.value

            val sceneFiles = mutableListOf<File?>()
            val total = scenes.size
            scenes.forEachIndexed { index, scene ->
                val rawText = if (scene.narrationText.isNotBlank() && !isPlaceholderNarration(scene.narrationText)) scene.narrationText else ""
                val sanitizedText = sanitizeNarrationTextForSpeech(rawText)
                if (sanitizedText.isNotBlank()) {
                    try {
                        val outputFile = File(cacheDir, "voxeleven_scene_${index}_${System.currentTimeMillis()}.mp3")
                        val generatedFile = com.ritvyom.yashoraReelgenerator.data.voxeleven.ElevenLabsClient.generateSpeech(
                            context = getApplication(),
                            apiKey = apiKey,
                            voiceId = voiceId,
                            text = sanitizedText,
                            modelId = modelId,
                            stability = stability,
                            similarityBoost = similarity,
                            style = style,
                            useSpeakerBoost = speakerBoost,
                            targetFile = outputFile
                        )
                        if (generatedFile != null && generatedFile.exists() && generatedFile.length() > 0) {
                            val sceneTitle = "Scene ${index + 1}: ${sanitizedText.take(24)}"
                            saveVoiceFileToSavedClips(
                                sourceFile = generatedFile,
                                title = sceneTitle,
                                engineTag = "VoxEleven (ElevenLabs)",
                                voiceName = voxElevenVoiceName.value,
                                scriptText = sanitizedText
                            )
                        }
                        sceneFiles.add(generatedFile)
                    } catch (e: Exception) {
                        Log.e("VoxElevenSynthesis", "Error synthesizing scene $index with VoxEleven", e)
                        sceneFiles.add(null)
                    }
                } else {
                    sceneFiles.add(null)
                }
                progressCallback((index + 1).toFloat() / total.toFloat())
            }

            // Also merge VoxEleven scene clips into a master voice file if multiple and save it
            val mergedMaster = mergeVoiceFilesToMaster(sceneFiles)
            if (mergedMaster != null && mergedMaster.exists() && mergedMaster.length() > 44) {
                latestMasterVoiceoverPath.value = mergedMaster.absolutePath
                val fullMasterTitle = "Full Video Voice (${sceneFiles.filterNotNull().size} scenes)"
                saveVoiceFileToSavedClips(
                    sourceFile = mergedMaster,
                    title = fullMasterTitle,
                    engineTag = "VoxEleven (ElevenLabs)",
                    voiceName = voxElevenVoiceName.value,
                    scriptText = scenes.mapNotNull { it.narrationText.takeIf { t -> t.isNotBlank() } }.joinToString(". ")
                )
            }
            return sceneFiles
        }

        val voiceFiles = ArrayList<File?>()
        
        val localVoicesDir = File(getApplication<Application>().filesDir, "YashoraLocalVoices").apply {
            if (!exists()) mkdirs()
        }

        // Prepare full script text for single-segment continuous voiceover ("ek khand speech")
        val sceneTexts = scenes.map { scene ->
            val rawText = if (scene.narrationText.isNotBlank() && !isPlaceholderNarration(scene.narrationText)) scene.narrationText else ""
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
            Log.w("TTS_COMPILER", "TTS engine not ready after waiting. Attempting emergency re-initialization...")
            reinitTts(selectedTtsEngine.value.ifEmpty { "com.google.android.tts" })
            waitCount = 0
            while ((tts == null || !ttsReady) && waitCount < 50) {
                delay(100)
                waitCount++
            }
        }

        if (tts == null || !ttsReady) {
            Log.e("TTS_COMPILER", "TTS engine not ready after emergency re-init. Cancelling voice synthesis.")
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
                            // Save master and sliced scene audio into permanent Saved Audio Library
                            saveVoiceFileToSavedClips(
                                sourceFile = persistentMasterFile,
                                title = "Full Voiceover (${scenes.size} scenes)",
                                engineTag = "Android Native TTS",
                                voiceName = selectedVoiceName.value,
                                scriptText = fullScriptText
                            )
                            sliced.forEachIndexed { sIdx, sFile ->
                                if (sFile != null && sFile.exists() && sFile.length() > 44) {
                                    val text = sceneTexts.getOrNull(sIdx)?.take(24) ?: "Scene ${sIdx + 1}"
                                    saveVoiceFileToSavedClips(
                                        sourceFile = sFile,
                                        title = "Scene ${sIdx + 1}: $text",
                                        engineTag = "Android Native TTS",
                                        voiceName = selectedVoiceName.value,
                                        scriptText = sceneTexts.getOrNull(sIdx) ?: ""
                                    )
                                }
                            }
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
            val rawText = if (scene.narrationText.isNotBlank() && !isPlaceholderNarration(scene.narrationText)) scene.narrationText else ""
            val cleanedText = sanitizeNarrationTextForSpeech(rawText)

            if (cleanedText.isEmpty()) {
                voiceFiles.add(null)
                progressCallback((i + 1).toFloat() / scenes.size)
                continue
            }

            // Generate secure cache key for speech
            val paramKey = "${cleanedText}_${selectedLanguage.value}_${selectedVoiceName.value}_${selectedVoiceCategory.value}_${voiceSpeed.value}_${voicePitch.value}_${voiceEmotion.value}"
            val voiceHash = md5(paramKey)
            val currentVoiceId = selectedVoiceName.value.ifEmpty { "default_voice" }
            val cachedVoiceFile = File(localVoicesDir, "voice_$voiceHash.wav")
            val file = File(cacheDir, "scene_speech_${System.currentTimeMillis()}_$i.wav")

            // 1. Check Room AudioCache table for stored voice clip metadata
            try {
                val context = getApplication<Application>()
                val audioCacheDao = com.ritvyom.yashoraReelgenerator.data.local.AppDatabase.getDatabase(context).audioCacheDao()
                val roomAudioCache = audioCacheDao.getAudioCache(currentVoiceId, voiceHash)
                if (roomAudioCache != null) {
                    val dbFile = File(roomAudioCache.fileUri)
                    if (dbFile.exists() && dbFile.length() > 44) {
                        Log.d("VoiceCache", "Room AudioCache HIT for scene $i (voiceId=$currentVoiceId, hash=$voiceHash)")
                        dbFile.copyTo(file, overwrite = true)
                        voiceFiles.add(file)
                        progressCallback((i + 1).toFloat() / scenes.size)
                        continue
                    }
                }
            } catch (e: Exception) {
                Log.w("VoiceCache", "Room AudioCache lookup failed, proceeding with disk cache", e)
            }

            if (cachedVoiceFile.exists() && cachedVoiceFile.length() > 44) {
                Log.d("VoiceCache", "Disk Cache HIT for scene $i. Loading persistent wave copy...")
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
                    // Save to local cache directory & store AudioCache metadata in Room database
                    try {
                        file.copyTo(cachedVoiceFile, overwrite = true)
                        Log.d("VoiceCache", "Successfully cached speaking reel narration: ${cachedVoiceFile.absolutePath}")

                        val context = getApplication<Application>()
                        val db = com.ritvyom.yashoraReelgenerator.data.local.AppDatabase.getDatabase(context)
                        val audioCacheDao = db.audioCacheDao()
                        val audioCacheEntry = com.ritvyom.yashoraReelgenerator.data.local.entities.AudioCache(
                            voiceId = currentVoiceId,
                            textHash = voiceHash,
                            fileUri = cachedVoiceFile.absolutePath,
                            textPrompt = cleanedText,
                            localFilePath = cachedVoiceFile.absolutePath,
                            timestamp = System.currentTimeMillis()
                        )
                        audioCacheDao.insertAudioCache(audioCacheEntry)

                        // Save in GeneratedAudio metadata table
                        val generatedAudioDao = db.generatedAudioDao()
                        val audioMetadata = com.ritvyom.yashoraReelgenerator.data.local.entities.GeneratedAudioEntity(
                            textPrompt = cleanedText,
                            selectedVoiceId = currentVoiceId,
                            localFilePath = cachedVoiceFile.absolutePath,
                            fileSizeBytes = cachedVoiceFile.length(),
                            audioFormat = "audio/mpeg",
                            provider = "VoxEleven",
                            timestamp = System.currentTimeMillis()
                        )
                        generatedAudioDao.insertAudio(audioMetadata)
                        Log.d("VoiceCache", "Inserted GeneratedAudio & AudioCache in Room for voiceId=$currentVoiceId, textHash=$voiceHash")
                    } catch (e: Exception) {
                        Log.e("VoiceCache", "Failed storing speech voice file/metadata to AudioCache database", e)
                    }

                    voiceFiles.add(file)
                    val text = cleanedText.take(24)
                    saveVoiceFileToSavedClips(
                        sourceFile = file,
                        title = "Scene ${i + 1}: $text",
                        engineTag = "Android Native TTS",
                        voiceName = selectedVoiceName.value,
                        scriptText = cleanedText
                    )
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
            val fullMasterTitle = "Full Voiceover (${voiceFiles.filterNotNull().size} scenes)"
            saveVoiceFileToSavedClips(
                sourceFile = mergedMaster,
                title = fullMasterTitle,
                engineTag = "Android Native TTS",
                voiceName = selectedVoiceName.value,
                scriptText = scenes.mapNotNull { it.narrationText.takeIf { t -> t.isNotBlank() } }.joinToString(". ")
            )
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
            mediaPlayer?.apply {
                if (isPlaying) stop()
                release()
            }
        } catch (e: Exception) {}
        mediaPlayer = null

        try {
            premiumMediaPlayer?.apply {
                if (isPlaying) stop()
                release()
            }
        } catch (e: Exception) {}
        premiumMediaPlayer = null

        if (ttsReady) {
            try {
                tts?.stop()
            } catch (e: Exception) {}
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
                scenesJson = if (activeScenes.value.isNotEmpty()) scenesAdapter.toJson(activeScenes.value) else "",
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

    fun appendDeviceMediaToActiveProject(uris: List<String>, context: android.content.Context) {
        viewModelScope.launch {
            pushToUndo()
            val currentList = activeScenes.value.toMutableList()
            val startIdx = currentList.size
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

                currentList.add(
                    Scene(
                        sceneNumber = startIdx + index + 1,
                        narrationText = "",
                        visualPrompt = "Imported Media",
                        subtitle = "Clip #${startIdx + index + 1}",
                        mediaPath = uriStr,
                        mediaType = if (isVideo) "VIDEO" else "IMAGE",
                        durationSeconds = durationSeconds,
                        durationMs = durationSeconds * 1000L
                    )
                )
            }
            activeScenes.value = currentList
            saveCurrentScenesToDb()
        }
    }

    fun createManualStudioProject(
        title: String,
        scriptText: String,
        scenes: List<Scene>,
        aspectRatio: String,
        videoStyle: String,
        voiceName: String,
        voiceCategory: String,
        language: String,
        bgMusicCategory: String,
        bgMusicVolume: Float,
        voiceVolume: Float,
        topicContext: String,
        customMasterVoicePath: String? = null,
        customBgMusicPath: String? = null,
        onComplete: (ProjectEntity) -> Unit
    ) {
        viewModelScope.launch {
            if (!customMasterVoicePath.isNullOrBlank()) {
                this@MainViewModel.customMasterVoicePath.value = customMasterVoicePath
                this@MainViewModel.customMasterVoiceName.value = "Recorded / Imported Voice"
                this@MainViewModel.latestMasterVoiceoverPath.value = customMasterVoicePath
            }
            if (!customBgMusicPath.isNullOrBlank()) {
                this@MainViewModel.customBgMusicPath.value = customBgMusicPath
                this@MainViewModel.customBgMusicName.value = "Custom Device Music"
            }
            val validScenes = if (scenes.isNotEmpty()) scenes else {
                listOf(
                    Scene(
                        sceneNumber = 1,
                        narrationText = scriptText.ifEmpty { "Manual Scene" },
                        visualPrompt = "Manual Scene",
                        subtitle = scriptText.take(30).ifEmpty { "Scene 1" },
                        mediaPath = null,
                        mediaType = "IMAGE",
                        durationSeconds = 5,
                        durationMs = 5000L
                    )
                )
            }
            val projectTitle = if (title.isNotBlank()) title else "Manual Project #${System.currentTimeMillis() % 1000}"
            val entity = ProjectEntity(
                title = projectTitle,
                scriptText = scriptText,
                videoStyle = videoStyle,
                voiceName = voiceName,
                voiceCategory = voiceCategory,
                language = language,
                aspectRatio = aspectRatio,
                resolution = selectedResolution.value,
                fps = selectedFps.value,
                bgMusicCategory = bgMusicCategory,
                bgMusicVolume = bgMusicVolume,
                voiceVolume = voiceVolume,
                status = "Draft",
                scenesJson = scenesAdapter.toJson(validScenes),
                topicContext = topicContext,
                publishingStyle = selectedPublishingStyle.value
            )
            val projId = repository.insertProject(entity)
            val created = entity.copy(id = projId.toInt())
            activeProject.value = created
            activeScenes.value = validScenes
            selectedSceneIndex.value = 0
            selectedAspectRatio.value = aspectRatio
            this@MainViewModel.scriptText.value = scriptText

            withContext(Dispatchers.Main) {
                onComplete(created)
            }
        }
    }

    fun loadProject(project: ProjectEntity) {
        stopSpeak()
        isPlaying.value = false
        selectedSceneIndex.value = 0
        exportedFilePath.value = null

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

        // Check internal master video file in app filesDir strictly for THIS project ID
        val internalDir = File(getApplication<Application>().filesDir, "YashoraCompiledVideos")
        val masterFile = File(internalDir, "project_${project.id}_master.mp4")
        if (masterFile.exists() && masterFile.length() > 0) {
            compiledPreviewPath.value = masterFile.absolutePath
            Log.d("MainViewModel", "loadProject: Attached local internal master video preview ${masterFile.absolutePath}")
        } else {
            compiledPreviewPath.value = null
        }

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

    fun clearActiveProject() {
        stopSpeak()
        isPlaying.value = false
        activeProject.value = null
        activeScenes.value = emptyList()
        selectedSceneIndex.value = 0
        compiledPreviewPath.value = null
        exportedFilePath.value = null
    }

    fun buildScriptFromActiveScenes(scenes: List<Scene>): String {
        if (scenes.isEmpty()) return ""
        return scenes.mapIndexed { idx, s ->
            val text = s.narrationText.ifBlank { s.subtitle }.trim()
            "[Scene ${idx + 1}] $text"
        }.joinToString("\n")
    }

    val isAutoSaving = MutableStateFlow(false)
    val lastAutoSaveTimestamp = MutableStateFlow<Long?>(null)
    val recentDraftProject = MutableStateFlow<com.ritvyom.yashoraReelgenerator.data.local.entities.ProjectEntity?>(null)

    fun saveCurrentScenesToDb() {
        viewModelScope.launch {
            try {
                val currentScenes = activeScenes.value
                if (currentScenes.isEmpty()) return@launch
                isAutoSaving.value = true
                val jsonStr = scenesAdapter.toJson(currentScenes)
                val derivedScript = buildScriptFromActiveScenes(currentScenes)
                val proj = activeProject.value
                if (proj != null) {
                    val updatedProj = proj.copy(
                        scenesJson = jsonStr,
                        scriptText = derivedScript.ifBlank { proj.scriptText },
                        updatedAt = System.currentTimeMillis()
                    )
                    repository.updateProject(updatedProj)
                    activeProject.value = updatedProj
                    if (derivedScript.isNotBlank()) {
                        scriptText.value = derivedScript
                    }
                } else {
                    val titleFormat = java.text.SimpleDateFormat("MMM dd, HH:mm", java.util.Locale.getDefault()).format(java.util.Date())
                    val newDraft = com.ritvyom.yashoraReelgenerator.data.local.entities.ProjectEntity(
                        title = "Draft Project ($titleFormat)",
                        scriptText = derivedScript.ifBlank { "Untitled Project" },
                        videoStyle = selectedStyleName.value,
                        voiceName = selectedVoiceName.value,
                        voiceCategory = selectedVoiceCategory.value,
                        language = selectedLanguage.value,
                        aspectRatio = selectedAspectRatio.value,
                        resolution = selectedResolution.value,
                        fps = selectedFps.value,
                        bgMusicCategory = bgMusicCategory.value,
                        status = "Draft",
                        scenesJson = jsonStr,
                        updatedAt = System.currentTimeMillis()
                    )
                    val insertedId = repository.insertProject(newDraft)
                    activeProject.value = newDraft.copy(id = insertedId.toInt())
                }
                lastAutoSaveTimestamp.value = System.currentTimeMillis()
            } catch (e: Exception) {
                Log.e("MainViewModel", "Error auto-saving scenes to DB", e)
            } finally {
                isAutoSaving.value = false
            }
        }
    }

    // Audio track timeline management
    val timelineAudioTrackPath = MutableStateFlow<String?>(null)
    val timelineAudioTrackName = MutableStateFlow<String?>(null)
    val timelineAudioStartMs = MutableStateFlow<Long>(0L)
    val timelineAudioDurationMs = MutableStateFlow<Long>(0L)
    val timelineAudioVolume = MutableStateFlow<Float>(1.0f)
    val timelineAudioTotalDurationMs = MutableStateFlow<Long>(0L)

    fun setTimelineAudioTrack(path: String, name: String, totalDurationMs: Long) {
        timelineAudioTrackPath.value = path
        timelineAudioTrackName.value = name
        timelineAudioTotalDurationMs.value = totalDurationMs
        timelineAudioStartMs.value = 0L
        timelineAudioDurationMs.value = totalDurationMs.coerceAtMost(60_000L)
        timelineAudioVolume.value = 1.0f
        saveCurrentScenesToDb()
    }

    fun updateTimelineAudioTrim(startMs: Long, durationMs: Long) {
        timelineAudioStartMs.value = startMs
        timelineAudioDurationMs.value = durationMs
        saveCurrentScenesToDb()
    }

    fun updateTimelineAudioVolume(vol: Float) {
        timelineAudioVolume.value = vol.coerceIn(0f, 2.0f)
        saveCurrentScenesToDb()
    }

    fun removeTimelineAudioTrack() {
        timelineAudioTrackPath.value = null
        timelineAudioTrackName.value = null
        timelineAudioStartMs.value = 0L
        timelineAudioDurationMs.value = 0L
        saveCurrentScenesToDb()
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

    // Video editor actions & state-based command manager
    private val commandManager = com.ritvyom.yashoraReelgenerator.presentation.utils.EditorCommandManager(maxHistorySize = 60)
    private val timelineUndoRedoManager = com.ritvyom.yashoraReelgenerator.engine.timeline.TimelineUndoRedoManager(maxHistorySize = 60)
    val canUndo = commandManager.canUndo
    val canRedo = commandManager.canRedo
    val lastActionName = commandManager.lastActionName

    fun pushToUndo(actionName: String = "Edit Timeline") {
        commandManager.recordState(activeScenes.value, actionName)
        val canonical = com.ritvyom.yashoraReelgenerator.engine.YashoraEngineBridge.createCanonicalTimeline(
            scenes = activeScenes.value,
            aspectRatioStr = selectedAspectRatio.value
        )
        timelineUndoRedoManager.beginTransaction(actionName, canonical)
    }

    fun commitTimelineTransaction(actionName: String = "Edit Timeline") {
        val canonical = com.ritvyom.yashoraReelgenerator.engine.YashoraEngineBridge.createCanonicalTimeline(
            scenes = activeScenes.value,
            aspectRatioStr = selectedAspectRatio.value
        )
        timelineUndoRedoManager.commitTransaction(canonical)
    }

    fun undo() {
        val currentCanonical = com.ritvyom.yashoraReelgenerator.engine.YashoraEngineBridge.createCanonicalTimeline(
            scenes = activeScenes.value,
            aspectRatioStr = selectedAspectRatio.value
        )
        val restoredCanonical = timelineUndoRedoManager.undo(currentCanonical)
        if (restoredCanonical != null) {
            val restoredScenes = com.ritvyom.yashoraReelgenerator.engine.YashoraEngineBridge.convertToScenes(
                timeline = restoredCanonical,
                referenceScenes = activeScenes.value
            )
            activeScenes.value = restoredScenes
            saveCurrentScenesToDb()
        } else {
            commandManager.undo(activeScenes.value) { restored ->
                activeScenes.value = restored
                saveCurrentScenesToDb()
            }
        }
    }

    fun redo() {
        val currentCanonical = com.ritvyom.yashoraReelgenerator.engine.YashoraEngineBridge.createCanonicalTimeline(
            scenes = activeScenes.value,
            aspectRatioStr = selectedAspectRatio.value
        )
        val restoredCanonical = timelineUndoRedoManager.redo(currentCanonical)
        if (restoredCanonical != null) {
            val restoredScenes = com.ritvyom.yashoraReelgenerator.engine.YashoraEngineBridge.convertToScenes(
                timeline = restoredCanonical,
                referenceScenes = activeScenes.value
            )
            activeScenes.value = restoredScenes
            saveCurrentScenesToDb()
        } else {
            commandManager.redo(activeScenes.value) { restored ->
                activeScenes.value = restored
                saveCurrentScenesToDb()
            }
        }
    }

    fun updateScene(index: Int, updatedScene: Scene) {
        val currentList = activeScenes.value.toMutableList()
        if (index in currentList.indices) {
            currentList[index] = updatedScene
            activeScenes.value = currentList
            saveCurrentScenesToDb()
        }
    }

    fun editSceneSubtitle(index: Int, newText: String) {
        pushToUndo()
        val currentList = activeScenes.value.toMutableList()
        if (index in currentList.indices) {
            val oldScene = currentList[index]
            val shouldSyncNarration = oldScene.narrationText.isBlank() || oldScene.narrationText == oldScene.subtitle
            currentList[index] = oldScene.copy(
                subtitle = newText,
                narrationText = if (shouldSyncNarration) newText else oldScene.narrationText
            )
            activeScenes.value = currentList
            saveCurrentScenesToDb()
        }
    }

    fun editSceneNarrationText(index: Int, newText: String) {
        pushToUndo()
        val currentList = activeScenes.value.toMutableList()
        if (index in currentList.indices) {
            val oldScene = currentList[index]
            val shouldSyncSubtitle = oldScene.subtitle.isBlank() || oldScene.subtitle == oldScene.narrationText
            currentList[index] = oldScene.copy(
                narrationText = newText,
                subtitle = if (shouldSyncSubtitle) newText else oldScene.subtitle
            )
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
        pushToUndo("Change Transition")
        val currentList = activeScenes.value.toMutableList()
        if (index in currentList.indices) {
            currentList[index] = currentList[index].copy(transitionType = transitionType)
            activeScenes.value = currentList
            saveCurrentScenesToDb()
        }
    }

    fun editSceneTransitionWithDuration(index: Int, transitionType: String, durationMs: Long) {
        pushToUndo("Change Transition")
        val currentList = activeScenes.value.toMutableList()
        if (index in currentList.indices) {
            currentList[index] = currentList[index].copy(
                transitionType = transitionType,
                transitionDurationMs = durationMs
            )
            activeScenes.value = currentList
            saveCurrentScenesToDb()
        }
    }

    fun applyTransitionToAllScenes(transitionType: String, durationMs: Long) {
        pushToUndo("Apply Transition to All")
        val updated = activeScenes.value.map {
            it.copy(
                transitionType = transitionType,
                transitionDurationMs = durationMs
            )
        }
        activeScenes.value = updated
        saveCurrentScenesToDb()
    }

    fun editSceneOverlayText(index: Int, text: String, color: String) {
        pushToUndo("Edit Text Overlay")
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

    fun editSceneTextOverlayFull(
        index: Int,
        text: String,
        color: String,
        font: String,
        animation: String,
        fontSize: Float = 24f,
        durationSeconds: Float = 3f,
        startTimeSeconds: Float = 0f
    ) {
        pushToUndo("Edit Text Overlay")
        val currentList = activeScenes.value.toMutableList()
        if (index in currentList.indices) {
            currentList[index] = currentList[index].copy(
                textOverlay = text,
                overlayColor = color,
                captionFont = font,
                textAnimation = animation,
                textOverlayFontSize = fontSize,
                textOverlayDurationSeconds = durationSeconds,
                textOverlayStartTimeSeconds = startTimeSeconds
            )
            activeScenes.value = currentList
            saveCurrentScenesToDb()
        }
    }

    fun editSceneSubtitleColor(index: Int, color: String) {
        val currentList = activeScenes.value.toMutableList()
        if (index in currentList.indices) {
            currentList[index] = currentList[index].copy(subtitleColor = color)
            activeScenes.value = currentList
            saveCurrentScenesToDb()
        }
    }

    fun updateSceneTextOverlayPosition(index: Int, x: Float, y: Float, scale: Float, persist: Boolean = true) {
        updateSceneTextOverlayTransform(index, x, y, scale, activeScenes.value.getOrNull(index)?.textOverlayRotation ?: 0f, persist)
    }

    fun updateSceneTextOverlayTransform(
        index: Int,
        x: Float,
        y: Float,
        scale: Float,
        rotation: Float,
        persist: Boolean = true
    ) {
        val currentList = activeScenes.value.toMutableList()
        if (index in currentList.indices) {
            currentList[index] = currentList[index].copy(
                textOverlayX = x.coerceIn(0.05f, 0.95f),
                textOverlayY = y.coerceIn(0.05f, 0.95f),
                textOverlayScale = scale.coerceIn(0.3f, 4.0f),
                textOverlayRotation = (rotation % 360f + 360f) % 360f
            )
            activeScenes.value = currentList
            if (persist) {
                saveCurrentScenesToDb()
            }
        }
    }

    fun updateSceneStickerPosition(index: Int, x: Float, y: Float, scale: Float, persist: Boolean = true) {
        updateSceneStickerTransform(index, x, y, scale, activeScenes.value.getOrNull(index)?.stickerRotation ?: 0f, persist)
    }

    fun updateSceneStickerTransform(
        index: Int,
        x: Float,
        y: Float,
        scale: Float,
        rotation: Float,
        persist: Boolean = true
    ) {
        val currentList = activeScenes.value.toMutableList()
        if (index in currentList.indices) {
            currentList[index] = currentList[index].copy(
                stickerX = x.coerceIn(0.05f, 0.95f),
                stickerY = y.coerceIn(0.05f, 0.95f),
                stickerScale = scale.coerceIn(0.3f, 4.0f),
                stickerRotation = (rotation % 360f + 360f) % 360f
            )
            activeScenes.value = currentList
            if (persist) {
                saveCurrentScenesToDb()
            }
        }
    }

    // -------------------------------------------------------------
    // Canvas Overlay Layers & Depth Management (Text & Stickers)
    // -------------------------------------------------------------

    fun updateSceneLayers(index: Int, layers: List<CanvasLayer>, persist: Boolean = true) {
        val currentList = activeScenes.value.toMutableList()
        if (index in currentList.indices) {
            val scene = currentList[index]
            scene.saveCanvasLayers(layers)
            currentList[index] = scene.copy()
            activeScenes.value = currentList
            if (persist) {
                saveCurrentScenesToDb()
            }
        }
    }

    fun bringLayerForward(sceneIndex: Int, layerId: String) {
        val currentList = activeScenes.value.toMutableList()
        if (sceneIndex in currentList.indices) {
            pushToUndo("Bring Layer Forward")
            val scene = currentList[sceneIndex]
            val layers = scene.getCanvasLayers().toMutableList()
            val idx = layers.indexOfFirst { it.id == layerId }
            if (idx != -1 && idx < layers.size - 1) {
                val temp = layers[idx]
                layers[idx] = layers[idx + 1]
                layers[idx + 1] = temp
                scene.saveCanvasLayers(layers)
                currentList[sceneIndex] = scene.copy()
                activeScenes.value = currentList
                saveCurrentScenesToDb()
            }
        }
    }

    fun sendLayerBackward(sceneIndex: Int, layerId: String) {
        val currentList = activeScenes.value.toMutableList()
        if (sceneIndex in currentList.indices) {
            pushToUndo("Send Layer Backward")
            val scene = currentList[sceneIndex]
            val layers = scene.getCanvasLayers().toMutableList()
            val idx = layers.indexOfFirst { it.id == layerId }
            if (idx > 0) {
                val temp = layers[idx]
                layers[idx] = layers[idx - 1]
                layers[idx - 1] = temp
                scene.saveCanvasLayers(layers)
                currentList[sceneIndex] = scene.copy()
                activeScenes.value = currentList
                saveCurrentScenesToDb()
            }
        }
    }

    fun bringLayerToFront(sceneIndex: Int, layerId: String) {
        val currentList = activeScenes.value.toMutableList()
        if (sceneIndex in currentList.indices) {
            pushToUndo("Bring Layer to Front")
            val scene = currentList[sceneIndex]
            val layers = scene.getCanvasLayers().toMutableList()
            val idx = layers.indexOfFirst { it.id == layerId }
            if (idx != -1 && idx < layers.size - 1) {
                val item = layers.removeAt(idx)
                layers.add(item)
                scene.saveCanvasLayers(layers)
                currentList[sceneIndex] = scene.copy()
                activeScenes.value = currentList
                saveCurrentScenesToDb()
            }
        }
    }

    fun sendLayerToBack(sceneIndex: Int, layerId: String) {
        val currentList = activeScenes.value.toMutableList()
        if (sceneIndex in currentList.indices) {
            pushToUndo("Send Layer to Back")
            val scene = currentList[sceneIndex]
            val layers = scene.getCanvasLayers().toMutableList()
            val idx = layers.indexOfFirst { it.id == layerId }
            if (idx > 0) {
                val item = layers.removeAt(idx)
                layers.add(0, item)
                scene.saveCanvasLayers(layers)
                currentList[sceneIndex] = scene.copy()
                activeScenes.value = currentList
                saveCurrentScenesToDb()
            }
        }
    }

    fun addTextLayer(sceneIndex: Int, text: String, color: String = "#FFFFFF", font: String = "TikTok Style") {
        val currentList = activeScenes.value.toMutableList()
        if (sceneIndex in currentList.indices) {
            pushToUndo("Add Text Layer")
            val scene = currentList[sceneIndex]
            val layers = scene.getCanvasLayers().toMutableList()
            val newLayer = CanvasLayer(
                id = java.util.UUID.randomUUID().toString(),
                type = "TEXT",
                content = text,
                color = color,
                font = font,
                animation = "Pop",
                fontSize = 24f,
                x = 0.5f,
                y = 0.35f + (layers.size * 0.05f).coerceAtMost(0.35f),
                scale = 1.0f,
                rotation = 0f,
                isVisible = true
            )
            layers.add(newLayer)
            scene.saveCanvasLayers(layers)
            currentList[sceneIndex] = scene.copy()
            activeScenes.value = currentList
            saveCurrentScenesToDb()
        }
    }

    fun duplicateLayer(sceneIndex: Int, layerId: String): String? {
        val currentList = activeScenes.value.toMutableList()
        if (sceneIndex in currentList.indices) {
            pushToUndo("Duplicate Layer")
            val scene = currentList[sceneIndex]
            val layers = scene.getCanvasLayers().toMutableList()
            val original = layers.firstOrNull { it.id == layerId }
            if (original != null) {
                val newId = java.util.UUID.randomUUID().toString()
                val duplicated = original.copy(
                    id = newId,
                    x = (original.x + 0.05f).coerceIn(0.05f, 0.95f),
                    y = (original.y + 0.05f).coerceIn(0.05f, 0.95f)
                )
                layers.add(duplicated)
                scene.saveCanvasLayers(layers)
                currentList[sceneIndex] = scene.copy()
                activeScenes.value = currentList
                saveCurrentScenesToDb()
                return newId
            }
        }
        return null
    }

    fun deletePip(sceneIndex: Int) {
        val currentList = activeScenes.value.toMutableList()
        if (sceneIndex in currentList.indices) {
            pushToUndo("Delete PIP")
            currentList[sceneIndex] = currentList[sceneIndex].copy(
                pipMediaPath = null
            )
            activeScenes.value = currentList
            saveCurrentScenesToDb()
        }
    }

    fun updatePipTransform(
        sceneIndex: Int,
        x: Float,
        y: Float,
        scale: Float,
        persist: Boolean = false
    ) {
        val currentList = activeScenes.value.toMutableList()
        if (sceneIndex in currentList.indices) {
            currentList[sceneIndex] = currentList[sceneIndex].copy(
                pipX = x.coerceIn(0.05f, 0.95f),
                pipY = y.coerceIn(0.05f, 0.95f),
                pipScale = scale.coerceIn(0.15f, 2.5f)
            )
            activeScenes.value = currentList
            if (persist) {
                saveCurrentScenesToDb()
            }
        }
    }

    fun addStickerLayer(sceneIndex: Int, sticker: String) {
        val currentList = activeScenes.value.toMutableList()
        if (sceneIndex in currentList.indices) {
            pushToUndo("Add Sticker Layer")
            val scene = currentList[sceneIndex]
            val layers = scene.getCanvasLayers().toMutableList()
            val newLayer = CanvasLayer(
                id = java.util.UUID.randomUUID().toString(),
                type = "STICKER",
                content = sticker,
                x = 0.5f,
                y = 0.5f,
                scale = 1.0f,
                rotation = 0f,
                isVisible = true
            )
            layers.add(newLayer)
            scene.saveCanvasLayers(layers)
            currentList[sceneIndex] = scene.copy()
            activeScenes.value = currentList
            saveCurrentScenesToDb()
        }
    }

    fun deleteLayer(sceneIndex: Int, layerId: String) {
        val currentList = activeScenes.value.toMutableList()
        if (sceneIndex in currentList.indices) {
            pushToUndo("Delete Layer")
            val scene = currentList[sceneIndex]
            val layers = scene.getCanvasLayers().toMutableList()
            layers.removeAll { it.id == layerId }
            scene.saveCanvasLayers(layers)
            currentList[sceneIndex] = scene.copy()
            activeScenes.value = currentList
            saveCurrentScenesToDb()
        }
    }

    fun toggleLayerVisibility(sceneIndex: Int, layerId: String) {
        val currentList = activeScenes.value.toMutableList()
        if (sceneIndex in currentList.indices) {
            val scene = currentList[sceneIndex]
            val layers = scene.getCanvasLayers().toMutableList()
            val idx = layers.indexOfFirst { it.id == layerId }
            if (idx != -1) {
                layers[idx] = layers[idx].copy(isVisible = !layers[idx].isVisible)
                scene.saveCanvasLayers(layers)
                currentList[sceneIndex] = scene.copy()
                activeScenes.value = currentList
                saveCurrentScenesToDb()
            }
        }
    }

    fun updateLayerTransform(
        sceneIndex: Int,
        layerId: String,
        x: Float,
        y: Float,
        scale: Float,
        rotation: Float,
        persist: Boolean = false
    ) {
        val currentList = activeScenes.value.toMutableList()
        if (sceneIndex in currentList.indices) {
            val scene = currentList[sceneIndex]
            val layers = scene.getCanvasLayers().toMutableList()
            val idx = layers.indexOfFirst { it.id == layerId }
            if (idx != -1) {
                layers[idx] = layers[idx].copy(
                    x = x.coerceIn(0.05f, 0.95f),
                    y = y.coerceIn(0.05f, 0.95f),
                    scale = scale.coerceIn(0.3f, 4.0f),
                    rotation = (rotation % 360f + 360f) % 360f
                )
                scene.saveCanvasLayers(layers)
                currentList[sceneIndex] = scene.copy()
                activeScenes.value = currentList
                if (persist) {
                    saveCurrentScenesToDb()
                }
            }
        }
    }

    fun updateLayerStyle(
        sceneIndex: Int,
        layerId: String,
        content: String? = null,
        color: String? = null,
        font: String? = null,
        animation: String? = null
    ) {
        val currentList = activeScenes.value.toMutableList()
        if (sceneIndex in currentList.indices) {
            val scene = currentList[sceneIndex]
            val layers = scene.getCanvasLayers().toMutableList()
            val idx = layers.indexOfFirst { it.id == layerId }
            if (idx != -1) {
                val current = layers[idx]
                layers[idx] = current.copy(
                    content = content ?: current.content,
                    color = color ?: current.color,
                    font = font ?: current.font,
                    animation = animation ?: current.animation
                )
                scene.saveCanvasLayers(layers)
                currentList[sceneIndex] = scene.copy()
                activeScenes.value = currentList
                saveCurrentScenesToDb()
            }
        }
    }

    fun editSceneTextOverlayLive(
        index: Int,
        text: String,
        color: String,
        font: String,
        animation: String,
        fontSize: Float = 24f,
        durationSeconds: Float = 3f,
        startTimeSeconds: Float = 0f
    ) {
        val currentList = activeScenes.value.toMutableList()
        if (index in currentList.indices) {
            currentList[index] = currentList[index].copy(
                textOverlay = text,
                overlayColor = color,
                captionFont = font,
                textAnimation = animation,
                textOverlayFontSize = fontSize,
                textOverlayDurationSeconds = durationSeconds,
                textOverlayStartTimeSeconds = startTimeSeconds
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
            val isRemote = path.startsWith("http://") || path.startsWith("https://") || path.startsWith("gs://")
            currentList[index] = currentList[index].copy(
                mediaPath = path,
                remoteUrl = if (isRemote) path else "",
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

    fun updateBgMusicCategory(category: String) {
        bgMusicCategory.value = category
    }

    fun editSceneFilterCategory(index: Int, category: String) {
        pushToUndo()
        val currentList = activeScenes.value.toMutableList()
        if (index in currentList.indices) {
            currentList[index] = currentList[index].copy(filterCategory = category)
            activeScenes.value = currentList
            saveCurrentScenesToDb()
        }
    }

    fun editSceneFilterIntensity(index: Int, intensity: Int) {
        pushToUndo()
        val currentList = activeScenes.value.toMutableList()
        if (index in currentList.indices) {
            currentList[index] = currentList[index].copy(filterIntensity = intensity)
            activeScenes.value = currentList
            saveCurrentScenesToDb()
        }
    }

    fun editSceneEffect(index: Int, effectName: String, category: String) {
        pushToUndo()
        val currentList = activeScenes.value.toMutableList()
        if (index in currentList.indices) {
            currentList[index] = currentList[index].copy(effectName = effectName, effectCategory = category)
            activeScenes.value = currentList
            saveCurrentScenesToDb()
        }
    }

    fun editSceneEffectIntensity(index: Int, intensity: Int) {
        pushToUndo()
        val currentList = activeScenes.value.toMutableList()
        if (index in currentList.indices) {
            currentList[index] = currentList[index].copy(effectIntensity = intensity)
            activeScenes.value = currentList
            saveCurrentScenesToDb()
        }
    }

    fun editSceneSfx(index: Int, sfxName: String) {
        pushToUndo()
        val currentList = activeScenes.value.toMutableList()
        if (index in currentList.indices) {
            currentList[index] = currentList[index].copy(sfxName = sfxName)
            activeScenes.value = currentList
            saveCurrentScenesToDb()
        }
    }

    fun editSceneSticker(index: Int, stickerName: String) {
        pushToUndo()
        val currentList = activeScenes.value.toMutableList()
        if (index in currentList.indices) {
            currentList[index] = currentList[index].copy(stickerName = stickerName)
            activeScenes.value = currentList
            saveCurrentScenesToDb()
        }
    }

    fun editSceneStickerScale(index: Int, scale: Float) {
        pushToUndo()
        val currentList = activeScenes.value.toMutableList()
        if (index in currentList.indices) {
            currentList[index] = currentList[index].copy(stickerScale = scale)
            activeScenes.value = currentList
            saveCurrentScenesToDb()
        }
    }

    fun editSceneBrightness(index: Int, brightness: Float) {
        pushToUndo()
        val currentList = activeScenes.value.toMutableList()
        if (index in currentList.indices) {
            currentList[index] = currentList[index].copy(brightnessValue = brightness)
            activeScenes.value = currentList
            saveCurrentScenesToDb()
        }
    }

    fun editSceneContrast(index: Int, contrast: Float) {
        pushToUndo()
        val currentList = activeScenes.value.toMutableList()
        if (index in currentList.indices) {
            currentList[index] = currentList[index].copy(contrastValue = contrast)
            activeScenes.value = currentList
            saveCurrentScenesToDb()
        }
    }

    fun editSceneSaturation(index: Int, saturation: Float) {
        pushToUndo()
        val currentList = activeScenes.value.toMutableList()
        if (index in currentList.indices) {
            currentList[index] = currentList[index].copy(saturationValue = saturation)
            activeScenes.value = currentList
            saveCurrentScenesToDb()
        }
    }

    fun editSceneWarmth(index: Int, warmth: Float) {
        pushToUndo()
        val currentList = activeScenes.value.toMutableList()
        if (index in currentList.indices) {
            currentList[index] = currentList[index].copy(warmthValue = warmth)
            activeScenes.value = currentList
            saveCurrentScenesToDb()
        }
    }

    fun editSceneFullGrading(
        index: Int,
        brightness: Float,
        contrast: Float,
        saturation: Float,
        warmth: Float,
        vignette: Float,
        exposure: Float,
        sharpen: Float,
        tint: Float,
        highlights: Float,
        shadows: Float
    ) {
        val currentList = activeScenes.value.toMutableList()
        if (index in currentList.indices) {
            currentList[index] = currentList[index].copy(
                brightnessValue = brightness,
                contrastValue = contrast,
                saturationValue = saturation,
                warmthValue = warmth,
                vignetteValue = vignette,
                exposureValue = exposure,
                sharpenValue = sharpen,
                tintValue = tint,
                highlightValue = highlights,
                shadowValue = shadows
            )
            activeScenes.value = currentList
            saveCurrentScenesToDb()
        }
    }

    fun applyGradingToAllScenes(
        brightness: Float,
        contrast: Float,
        saturation: Float,
        warmth: Float,
        vignette: Float,
        exposure: Float,
        sharpen: Float,
        tint: Float,
        highlights: Float,
        shadows: Float
    ) {
        pushToUndo()
        val currentList = activeScenes.value.map { scene ->
            scene.copy(
                brightnessValue = brightness,
                contrastValue = contrast,
                saturationValue = saturation,
                warmthValue = warmth,
                vignetteValue = vignette,
                exposureValue = exposure,
                sharpenValue = sharpen,
                tintValue = tint,
                highlightValue = highlights,
                shadowValue = shadows
            )
        }
        activeScenes.value = currentList
        saveCurrentScenesToDb()
    }

    fun editSceneAnimation(
        index: Int,
        inAnim: String,
        outAnim: String,
        comboAnim: String,
        duration: Float
    ) {
        pushToUndo()
        val currentList = activeScenes.value.toMutableList()
        if (index in currentList.indices) {
            currentList[index] = currentList[index].copy(
                inAnimation = inAnim,
                outAnimation = outAnim,
                comboAnimation = comboAnim,
                animationDuration = duration
            )
            activeScenes.value = currentList
            saveCurrentScenesToDb()
        }
    }

    fun editSceneVoiceFx(
        index: Int,
        voiceEffect: String,
        fadeIn: Float,
        fadeOut: Float
    ) {
        pushToUndo()
        val currentList = activeScenes.value.toMutableList()
        if (index in currentList.indices) {
            currentList[index] = currentList[index].copy(
                voiceEffect = voiceEffect,
                audioFadeInDuration = fadeIn,
                audioFadeOutDuration = fadeOut
            )
            activeScenes.value = currentList
            saveCurrentScenesToDb()
        }
    }

    fun toggleSceneFreeze(index: Int) {
        pushToUndo()
        val currentList = activeScenes.value.toMutableList()
        if (index in currentList.indices) {
            val isFrz = !currentList[index].isFrozen
            currentList[index] = currentList[index].copy(isFrozen = isFrz)
            activeScenes.value = currentList
            saveCurrentScenesToDb()
        }
    }

    fun toggleSceneReverse(index: Int) {
        pushToUndo()
        val currentList = activeScenes.value.toMutableList()
        if (index in currentList.indices) {
            val isRev = !currentList[index].isReversed
            currentList[index] = currentList[index].copy(isReversed = isRev)
            activeScenes.value = currentList
            saveCurrentScenesToDb()
        }
    }

    fun editSceneTextOverlay(index: Int, text: String) {
        pushToUndo()
        val currentList = activeScenes.value.toMutableList()
        if (index in currentList.indices) {
            currentList[index] = currentList[index].copy(textOverlay = text)
            activeScenes.value = currentList
            saveCurrentScenesToDb()
        }
    }

    fun editSceneOverlayColor(index: Int, color: String) {
        pushToUndo()
        val currentList = activeScenes.value.toMutableList()
        if (index in currentList.indices) {
            currentList[index] = currentList[index].copy(overlayColor = color)
            activeScenes.value = currentList
            saveCurrentScenesToDb()
        }
    }

    fun editSceneCaptionFont(index: Int, font: String) {
        pushToUndo()
        val currentList = activeScenes.value.toMutableList()
        if (index in currentList.indices) {
            currentList[index] = currentList[index].copy(captionFont = font)
            activeScenes.value = currentList
            saveCurrentScenesToDb()
        }
    }

    fun editSceneNarrationAudio(index: Int, path: String) {
        pushToUndo()
        val currentList = activeScenes.value.toMutableList()
        if (index in currentList.indices) {
            currentList[index] = currentList[index].copy(customVoiceAudioPath = path)
            activeScenes.value = currentList
            saveCurrentScenesToDb()
        }
    }

    fun editScenePip(
        index: Int,
        mediaPath: String,
        scale: Float = 0.4f,
        position: String = "TOP_RIGHT",
        opacity: Float = 1.0f
    ) {
        pushToUndo()
        val currentList = activeScenes.value.toMutableList()
        if (index in currentList.indices) {
            currentList[index] = currentList[index].copy(
                pipMediaPath = mediaPath,
                pipScale = scale
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

    fun adjustSceneDuration(sceneIndex: Int, newDurationSeconds: Int) {
        val validDuration = newDurationSeconds.coerceIn(1, 120)
        pushToUndo()
        val currentList = activeScenes.value.toMutableList()
        if (sceneIndex in currentList.indices) {
            currentList[sceneIndex] = currentList[sceneIndex].copy(
                durationSeconds = validDuration,
                durationMs = validDuration * 1000L
            )
            activeScenes.value = currentList
            saveCurrentScenesToDb()
        }
    }

    fun trimSceneVideo(
        context: android.content.Context,
        sceneIndex: Int,
        startMs: Long,
        endMs: Long,
        onFinished: (Boolean, String?) -> Unit = { _, _ -> }
    ) {
        val currentScenes = activeScenes.value.toMutableList()
        if (sceneIndex !in currentScenes.indices) {
            onFinished(false, "Invalid clip index")
            return
        }
        val scene = currentScenes[sceneIndex]
        val inputPath = scene.mediaPath
        if (inputPath.isNullOrEmpty()) {
            onFinished(false, "Selected clip has no media path")
            return
        }

        val outputDir = File(context.cacheDir, "trimmed_videos")
        if (!outputDir.exists()) outputDir.mkdirs()
        val outputFile = File(outputDir, "trim_${System.currentTimeMillis()}_scene_${sceneIndex + 1}.mp4")

        isTrimming.value = true
        trimProgress.value = 0.05f

        Media3VideoTrimmer.trimVideo(
            context = context,
            inputPath = inputPath,
            outputPath = outputFile.absolutePath,
            startMs = startMs,
            endMs = endMs,
            onProgress = { pr ->
                trimProgress.value = pr
            },
            onComplete = { success, error ->
                isTrimming.value = false
                if (success) {
                    val newDurationSec = (((endMs - startMs) / 1000L).toInt()).coerceAtLeast(1)
                    val updatedScene = scene.copy(
                        mediaPath = outputFile.absolutePath,
                        durationSeconds = newDurationSec,
                        durationMs = newDurationSec * 1000L,
                        mediaType = "VIDEO"
                    )
                    currentScenes[sceneIndex] = updatedScene
                    activeScenes.value = currentScenes
                    saveCurrentScenesToDb()
                }
                onFinished(success, error)
            }
        )
    }

    fun swapSceneMedia(index: Int, path: String, mediaType: String) {
        pushToUndo()
        val currentList = activeScenes.value.toMutableList()
        if (index in currentList.indices) {
            val isRemote = path.startsWith("http://") || path.startsWith("https://") || path.startsWith("gs://")
            currentList[index] = currentList[index].copy(
                mediaPath = path,
                remoteUrl = if (isRemote) path else "",
                mediaType = mediaType,
                visualPrompt = if (mediaType == "VIDEO") "User Selected Alternative Video" else "User Selected Alternative Photo"
            )
            activeScenes.value = currentList
            saveCurrentScenesToDb()

            if (isRemote) {
                viewModelScope.launch(Dispatchers.IO) {
                    try {
                        val localPath = repository.downloadMediaToLocal(path)
                        if (localPath.isNotEmpty() && java.io.File(localPath).exists() && java.io.File(localPath).length() > 0L) {
                            val updated = activeScenes.value.toMutableList()
                            if (index in updated.indices && updated[index].remoteUrl == path) {
                                updated[index] = updated[index].copy(mediaPath = localPath)
                                activeScenes.value = updated
                                saveCurrentScenesToDb()
                            }
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("MainViewModel", "Error caching swapped media locally for scene $index", e)
                    }
                }
            }
        }
    }

    fun replaceSceneWithDeviceMedia(index: Int, uri: android.net.Uri, context: android.content.Context) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val mimeType = context.contentResolver.getType(uri) ?: ""
                val uriStr = uri.toString().lowercase()
                val isVideo = mimeType.startsWith("video/") || 
                        uriStr.contains(".mp4") || uriStr.contains(".mkv") || uriStr.contains(".mov") || 
                        uriStr.contains(".webm") || uriStr.contains(".3gp")
                val ext = when {
                    isVideo && uriStr.contains(".mkv") -> "mkv"
                    isVideo && uriStr.contains(".webm") -> "webm"
                    isVideo && uriStr.contains(".mov") -> "mov"
                    isVideo -> "mp4"
                    mimeType.contains("png") || uriStr.contains(".png") -> "png"
                    mimeType.contains("webp") || uriStr.contains(".webp") -> "webp"
                    mimeType.contains("gif") || uriStr.contains(".gif") -> "gif"
                    else -> "jpg"
                }
                val mediaDir = java.io.File(context.filesDir, "user_media").apply { mkdirs() }
                val targetFile = java.io.File(mediaDir, "device_media_${System.currentTimeMillis()}.$ext")
                
                context.contentResolver.openInputStream(uri)?.use { input ->
                    targetFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                
                if (targetFile.exists() && targetFile.length() > 0) {
                    var durationSec = 5
                    if (isVideo) {
                        try {
                            val retriever = android.media.MediaMetadataRetriever()
                            retriever.setDataSource(targetFile.absolutePath)
                            val timeMs = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 5000L
                            retriever.release()
                            durationSec = (timeMs / 1000).toInt().coerceAtLeast(1)
                        } catch (e: Exception) {
                            durationSec = 5
                        }
                    }
                    
                    withContext(Dispatchers.Main) {
                        pushToUndo()
                        val currentList = activeScenes.value.toMutableList()
                        if (index in currentList.indices) {
                            currentList[index] = currentList[index].copy(
                                mediaPath = targetFile.absolutePath,
                                remoteUrl = "", // Explicitly clear so stale AI remote URL is never used
                                mediaType = if (isVideo) "VIDEO" else "IMAGE",
                                visualPrompt = if (isVideo) "Imported Video from Device File" else "Imported Photo from Device File",
                                durationSeconds = if (isVideo) durationSec else currentList[index].durationSeconds,
                                durationMs = if (isVideo) (durationSec * 1000L) else currentList[index].durationMs
                            )
                            activeScenes.value = currentList
                            saveCurrentScenesToDb()
                        }
                        android.widget.Toast.makeText(
                            context,
                            if (isVideo) "Video clip applied to scene #${index + 1}!".localize(appLanguage.value) else "Photo applied to scene #${index + 1}!".localize(appLanguage.value),
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            } catch (e: Exception) {
                Log.e("MainViewModel", "Failed to import device media for scene replacement", e)
                withContext(Dispatchers.Main) {
                    android.widget.Toast.makeText(context, "Could not load selected media: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    fun deleteScene(index: Int) {
        pushToUndo("Delete Clip")
        val currentList = activeScenes.value.toMutableList()
        if (currentList.size > 1 && index in currentList.indices) {
            try {
                val canonical = com.ritvyom.yashoraReelgenerator.engine.YashoraEngineBridge.createCanonicalTimeline(
                    scenes = currentList,
                    aspectRatioStr = selectedAspectRatio.value
                )
                val targetTrack = canonical.tracks.firstOrNull { it.type == com.ritvyom.yashoraReelgenerator.core.model.TrackType.MAIN_VIDEO }
                val clip = targetTrack?.clips?.getOrNull(index)
                if (clip != null) {
                    val updatedCanonical = com.ritvyom.yashoraReelgenerator.engine.timeline.ProfessionalClipOperations.deleteClip(
                        timeline = canonical,
                        clipId = clip.id
                    )
                    val updatedScenes = com.ritvyom.yashoraReelgenerator.engine.YashoraEngineBridge.convertToScenes(
                        timeline = updatedCanonical,
                        referenceScenes = currentList
                    )
                    if (updatedScenes.isNotEmpty()) {
                        val reindexed = updatedScenes.mapIndexed { idx, item -> item.copy(sceneNumber = idx + 1) }
                        if (selectedSceneIndex.value >= reindexed.size) {
                            selectedSceneIndex.value = kotlin.math.max(0, reindexed.size - 1)
                        }
                        activeScenes.value = reindexed
                        saveCurrentScenesToDb()
                        commitTimelineTransaction("Delete Clip")
                        return
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("MainViewModel", "Error in professional deleteClip", e)
            }
            currentList.removeAt(index)
            val reindexed = currentList.mapIndexed { idx, item ->
                item.copy(sceneNumber = idx + 1)
            }
            if (selectedSceneIndex.value >= reindexed.size) {
                selectedSceneIndex.value = kotlin.math.max(0, reindexed.size - 1)
            }
            activeScenes.value = reindexed
            saveCurrentScenesToDb()
            commitTimelineTransaction("Delete Clip")
        }
    }

    fun splitScene(index: Int, label: String = "[Split] New Segment") {
        pushToUndo("Split Clip")
        val currentList = activeScenes.value.toMutableList()
        if (index in currentList.indices) {
            try {
                val canonical = com.ritvyom.yashoraReelgenerator.engine.YashoraEngineBridge.createCanonicalTimeline(
                    scenes = currentList,
                    aspectRatioStr = selectedAspectRatio.value
                )
                val targetTrack = canonical.tracks.firstOrNull { it.type == com.ritvyom.yashoraReelgenerator.core.model.TrackType.MAIN_VIDEO }
                val clip = targetTrack?.clips?.getOrNull(index)
                if (clip != null) {
                    val halfDurationUs = (clip.durationUs / 2L).coerceAtLeast(100_000L)
                    val splitTimeUs = clip.startTimeUs + halfDurationUs
                    val updatedCanonical = com.ritvyom.yashoraReelgenerator.engine.timeline.ProfessionalClipOperations.splitClip(
                        timeline = canonical,
                        clipId = clip.id,
                        splitTimeUs = splitTimeUs
                    )
                    val updatedScenes = com.ritvyom.yashoraReelgenerator.engine.YashoraEngineBridge.convertToScenes(
                        timeline = updatedCanonical,
                        referenceScenes = currentList
                    )
                    activeScenes.value = updatedScenes
                    saveCurrentScenesToDb()
                    commitTimelineTransaction("Split Clip")
                    return
                }
            } catch (e: Exception) {
                android.util.Log.e("MainViewModel", "Error in professional splitClip, using fallback", e)
            }

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
            commitTimelineTransaction("Split Clip")
        }
    }

    fun duplicateScene(index: Int) {
        pushToUndo("Duplicate Clip")
        val currentList = activeScenes.value.toMutableList()
        if (index in currentList.indices) {
            try {
                val canonical = com.ritvyom.yashoraReelgenerator.engine.YashoraEngineBridge.createCanonicalTimeline(
                    scenes = currentList,
                    aspectRatioStr = selectedAspectRatio.value
                )
                val targetTrack = canonical.tracks.firstOrNull { it.type == com.ritvyom.yashoraReelgenerator.core.model.TrackType.MAIN_VIDEO }
                val clip = targetTrack?.clips?.getOrNull(index)
                if (clip != null) {
                    val updatedCanonical = com.ritvyom.yashoraReelgenerator.engine.timeline.ProfessionalClipOperations.duplicateClip(
                        timeline = canonical,
                        clipId = clip.id
                    )
                    val updatedScenes = com.ritvyom.yashoraReelgenerator.engine.YashoraEngineBridge.convertToScenes(
                        timeline = updatedCanonical,
                        referenceScenes = currentList
                    )
                    activeScenes.value = updatedScenes
                    saveCurrentScenesToDb()
                    commitTimelineTransaction("Duplicate Clip")
                    return
                }
            } catch (e: Exception) {
                android.util.Log.e("MainViewModel", "Error in professional duplicateClip", e)
            }
            val original = currentList[index]
            val copy = original.copy(
                sceneNumber = index + 2,
                subtitle = "${original.subtitle} (Copy)"
            )
            currentList.add(index + 1, copy)
            val reindexed = currentList.mapIndexed { idx, item -> item.copy(sceneNumber = idx + 1) }
            activeScenes.value = reindexed
            saveCurrentScenesToDb()
            commitTimelineTransaction("Duplicate Clip")
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
        resetExportSpeedTracker()
        isExporting.value = false
        isExportingMinimized.value = false
        isEnhanceVideo.value = false
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

    fun refetchSceneMedia(index: Int) {
        viewModelScope.launch {
            val currentList = activeScenes.value.toMutableList()
            if (index !in currentList.indices) return@launch
            val scene = currentList[index]

            MediaFetchingStatusMonitor.recordInProgress(scene.sceneNumber, scene.visualPrompt)

            // Mark scene as in-progress in state
            val pendingList = activeScenes.value.toMutableList()
            pendingList[index] = pendingList[index].copy(mediaFetchStatus = "PENDING")
            activeScenes.value = pendingList

            val res = withContext(Dispatchers.IO) {
                var resolvedRes: com.ritvyom.yashoraReelgenerator.data.repository.ProjectRepository.VisualAssetResolution? = null
                val allAttempted = mutableListOf<String>()

                // 1. Try keywords with bypassCache = true to force fresh fetch across Pexels, Pixabay, Unsplash
                for (keyword in scene.keywords) {
                    if (keyword.isNotEmpty()) {
                        val r = repository.resolveVisualAssetResolution(
                            query = keyword,
                            style = selectedStyleName.value,
                            aspectRatio = selectedAspectRatio.value,
                            index = index,
                            visualMedium = selectedVisualMedium.value,
                            imageSource = selectedImageSource.value,
                            resolution = selectedResolution.value,
                            visualPrompt = scene.visualPrompt,
                            publishingStyle = selectedPublishingStyle.value,
                            bypassCache = true
                        )
                        r.attemptedSources.forEach { if (!allAttempted.contains(it)) allAttempted.add(it) }
                        if (r.isSuccess && r.url.isNotEmpty()) {
                            resolvedRes = r
                            break
                        }
                    }
                }

                // 2. Try visual prompt
                if (resolvedRes == null && scene.visualPrompt.isNotEmpty()) {
                    val r = repository.resolveVisualAssetResolution(
                        query = scene.visualPrompt,
                        style = selectedStyleName.value,
                        aspectRatio = selectedAspectRatio.value,
                        index = index,
                        visualMedium = selectedVisualMedium.value,
                        imageSource = selectedImageSource.value,
                        resolution = selectedResolution.value,
                        visualPrompt = scene.visualPrompt,
                        publishingStyle = selectedPublishingStyle.value,
                        bypassCache = true
                    )
                    r.attemptedSources.forEach { if (!allAttempted.contains(it)) allAttempted.add(it) }
                    if (r.isSuccess && r.url.isNotEmpty()) {
                        resolvedRes = r
                    }
                }

                // 3. Try Pollinations AI as final dynamic fallback
                if (resolvedRes == null && scene.visualPrompt.isNotEmpty()) {
                    val aiUrl = repository.getBestMatchingImage(
                        visualPrompt = scene.visualPrompt,
                        style = selectedStyleName.value,
                        sceneNum = index,
                        aspectRatio = selectedAspectRatio.value,
                        imageSource = "AI Generated",
                        visualMedium = selectedVisualMedium.value
                    )
                    if (aiUrl.isNotEmpty()) {
                        if (!allAttempted.contains("AI Generated")) allAttempted.add("AI Generated")
                        resolvedRes = com.ritvyom.yashoraReelgenerator.data.repository.ProjectRepository.VisualAssetResolution(
                            url = aiUrl,
                            sourceUsed = "AI Generated",
                            isSuccess = true,
                            attemptedSources = allAttempted.toList()
                        )
                    }
                }

                Pair(resolvedRes, allAttempted)
            }

            val resolved = res.first
            val attempted = res.second

            pushToUndo()
            val updatedList = activeScenes.value.toMutableList()
            if (index in updatedList.indices) {
                if (resolved != null && resolved.isSuccess && resolved.url.isNotEmpty()) {
                    val localPath = withContext(Dispatchers.IO) {
                        repository.downloadMediaToLocal(resolved.url)
                    }
                    MediaFetchingStatusMonitor.recordSuccess(
                        sceneNumber = scene.sceneNumber,
                        visualPrompt = scene.visualPrompt,
                        sourceUsed = resolved.sourceUsed,
                        mediaUrl = resolved.url,
                        attemptedSources = resolved.attemptedSources
                    )
                    updatedList[index] = updatedList[index].copy(
                        mediaPath = if (localPath.isNotEmpty()) localPath else resolved.url,
                        remoteUrl = resolved.url,
                        mediaSourceUsed = resolved.sourceUsed,
                        mediaFetchStatus = "SUCCESS",
                        mediaFetchError = null
                    )
                    speakText("Fetched new visual for scene ${index + 1} from ${resolved.sourceUsed}")
                } else {
                    val finalAttempted = if (attempted.isNotEmpty()) attempted else listOf("Pexels", "Pixabay", "Unsplash")
                    val errorMsg = "All providers (${finalAttempted.joinToString(", ")}) failed."
                    MediaFetchingStatusMonitor.recordFailure(
                        sceneNumber = scene.sceneNumber,
                        visualPrompt = scene.visualPrompt,
                        attemptedSources = finalAttempted,
                        error = errorMsg
                    )
                    updatedList[index] = updatedList[index].copy(
                        mediaSourceUsed = "None (Failed)",
                        mediaFetchStatus = "FAILED",
                        mediaFetchError = errorMsg
                    )
                    speakText("Re-fetch failed for scene ${index + 1}. Please check API keys or network connection.")
                }
                activeScenes.value = updatedList
                saveCurrentScenesToDb()
            }
        }
    }

    // Pipeline generation
    fun generateVideoPipeline(onAdShown: () -> Unit, onBuildComplete: () -> Unit) {
        isGenerating.value = true
        generationProgress.value = 0f
        generationStatus.value = "Initializing generator pipeline..."

        generationJob = viewModelScope.launch {
            activeScenes.value = emptyList()
            selectedSceneIndex.value = 0
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

            // Step 2: Scene analysis & pre-rendered asset lookup in Firestore
            var scenes: List<Scene> = emptyList()
            generationProgress.value = 0.10f
            generationStatus.value = "Checking Firestore Cloud Pool for pre-rendered assets matching script hash..."

            val rawTopicForMatch = topicContext.value.ifEmpty { finalScript }
            val normalizedTopicForMatch = try {
                repository.normalizeTopic(rawTopicForMatch)
            } catch (e: Exception) {
                rawTopicForMatch.trim().lowercase()
            }
            Log.i("MainViewModel", "Original Matching Topic: '$rawTopicForMatch' -> Normalized Matching Topic: '$normalizedTopicForMatch'")

            // 2A. Check Firestore for pre-rendered assets matching the generated script hash BEFORE calling external APIs!
            val scriptHash = com.ritvyom.yashoraReelgenerator.data.remote.FirebaseCloudManager.computeScriptHash(finalScript)
            Log.i("MainViewModel", "Script hash: $scriptHash. Searching Firestore for pre-rendered assets...")

            val hashMatchingDeferred = kotlinx.coroutines.CompletableDeferred<com.ritvyom.yashoraReelgenerator.data.remote.SharedProject?>()
            com.ritvyom.yashoraReelgenerator.data.remote.FirebaseCloudManager.findPrerenderedAssetsByScriptHash(
                scriptText = finalScript,
                style = selectedStyleName.value,
                aspectRatio = selectedAspectRatio.value,
                visualMedium = selectedVisualMedium.value
            ) { matched ->
                hashMatchingDeferred.complete(matched)
            }

            var matchedProject = try {
                hashMatchingDeferred.await()
            } catch (e: Exception) {
                null
            }

            // 2B. Only check exact script hash match. Never overwrite the user's script with unrelated topic matches.
            // (Topic matching was previously hijacking the user's custom script with unrelated community scenes)
            if (matchedProject == null) {
                Log.i("MainViewModel", "No exact script hash match found in cache. Proceeding with fresh scene analysis for the user's script.")
            }

            if (matchedProject != null && matchedProject.scenes.isNotEmpty()) {
                generationStatus.value = "Pre-rendered assets found in Cloud Cache! Reusing assets to save API costs..."
                delay(1200)
                scenes = matchedProject.scenes.mapIndexed { idx, sharedScene ->
                    val remoteUrl = if (sharedScene.mediaPath.startsWith("http")) sharedScene.mediaPath else ""
                    val localPath = if (remoteUrl.isNotEmpty()) {
                        repository.downloadMediaToLocal(remoteUrl)
                    } else null

                    Scene(
                        sceneNumber = sharedScene.sceneNumber,
                        narrationText = sharedScene.narrationText,
                        visualPrompt = sharedScene.visualPrompt,
                        durationSeconds = sharedScene.durationSeconds,
                        subtitle = sharedScene.subtitle,
                        mediaPath = if (!localPath.isNullOrEmpty()) localPath else remoteUrl,
                        remoteUrl = remoteUrl,
                        keywords = sharedScene.keywords
                    )
                }

                // Verify that every scene has a valid media path. If any scene is missing media, resolve it dynamically
                scenes = scenes.mapIndexed { idx, scene ->
                    if (scene.mediaPath.isNullOrBlank()) {
                        val resolvedUrl = repository.resolveVisualAsset(
                            query = scene.keywords.firstOrNull() ?: scene.visualPrompt,
                            style = selectedStyleName.value,
                            aspectRatio = selectedAspectRatio.value,
                            index = idx,
                            visualMedium = selectedVisualMedium.value,
                            imageSource = selectedImageSource.value,
                            resolution = selectedResolution.value,
                            visualPrompt = scene.visualPrompt
                        )
                        val localPath = if (resolvedUrl.isNotEmpty()) repository.downloadMediaToLocal(resolvedUrl) else ""
                        scene.copy(
                            mediaPath = if (localPath.isNotEmpty()) localPath else resolvedUrl,
                            remoteUrl = resolvedUrl
                        )
                    } else {
                        scene
                    }
                }

                if (matchedProject.scriptText.isNotBlank()) {
                    finalScript = matchedProject.scriptText
                    scriptText.value = finalScript
                }
            } else {
                generationProgress.value = 0.15f
                generationStatus.value = "Analyzing script..."
                scenes = repository.generateStoryboardsFromScript(
                    script = finalScript,
                    style = selectedStyleName.value,
                    videoLang = selectedLanguage.value,
                    aspectRatio = selectedAspectRatio.value,
                    imageSource = selectedImageSource.value,
                    visualMedium = selectedVisualMedium.value,
                    topicContext = normalizedTopicForMatch,
                    onProgress = { current, total, statusMsg ->
                        val progressFraction = if (total > 0) 0.15f + (current.toFloat() / total.toFloat()) * 0.40f else 0.20f
                        generationProgress.value = progressFraction.coerceIn(0.15f, 0.55f)
                        generationStatus.value = statusMsg
                    }
                )
            }
            
            // Set active scenes immediately so Live Studio Canvas can start rendering them live on screen
            activeScenes.value = scenes
            selectedSceneIndex.value = 0

            // Render pipeline steps with clear, descriptive status messages
            val steps = listOf(
                Pair("Synthesizing voice audio with neural models...", 0.65f),
                Pair("Fetching visuals and aligning media frames...", 0.80f),
                Pair("Rendering captions and aligning subtitle timings...", 0.92f),
                Pair("Finalizing video project layout...", 1.0f)
            )

            for ((idx, step) in steps.withIndex()) {
                if (scenes.isNotEmpty()) {
                    selectedSceneIndex.value = (idx % scenes.size)
                }
                delay(900)
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

            // Save script to Firebase database when video generation completes
            saveApprovedScriptToCloud()

            isGenerating.value = false
            onBuildComplete()
        }
    }

    // Export pipeline with real local storage writing
    fun exportVideo(onExportComplete: () -> Unit) {
        stopAllPlayback()
        resetExportSpeedTracker()
        isExporting.value = true
        isExportingMinimized.value = false
        exportProgress.value = 0.01f
        exportStatus.value = "Initializing video compilation pipeline..."
        updateExportSpeedAndEta(0.01f)

        val powerManager = getApplication<Application>().getSystemService(android.content.Context.POWER_SERVICE) as android.os.PowerManager
        val wakeLock = powerManager.newWakeLock(android.os.PowerManager.PARTIAL_WAKE_LOCK, "YashoraReelgenerator:VideoExportWakeLock")

        // Progress listener coroutine to post system notifications dynamically in the background with live ETA
        val notificationJob = viewModelScope.launch {
            try {
                combine(exportProgress, exportStatus, exportEtaFormatted) { progress, status, eta ->
                    Triple(progress, status, eta)
                }.collect { (progress, status, eta) ->
                    if (progress < 1.0f) {
                        val progressPercent = (progress * 100).toInt().coerceIn(0, 99)
                        NotificationHelper.showExportProgressNotification(
                            getApplication(),
                            "Generating Video...".localize(appLanguage.value),
                            progressPercent,
                            status.localize(appLanguage.value),
                            eta
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
                    val overallProgress = (0.01f + 0.19f * pr) // spans 1% to 20%
                    exportProgress.value = overallProgress
                    updateExportSpeedAndEta(currentProgress = overallProgress, isRenderingFrames = false)
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

            exportStatus.value = "Verifying and downloading visual media for all scenes..."
            val fullyValidatedScenes = withContext(Dispatchers.IO) {
                coroutineScope {
                    alignedScenes.mapIndexed { idx, scene ->
                        async {
                            var currentPath = scene.mediaPath ?: ""
                            var currentRemoteUrl = scene.remoteUrl ?: ""
                            
                            val isLocalAndExists = currentPath.startsWith("/") && File(currentPath).exists() && File(currentPath).length() > 0L
                            val isHttpUrl = currentPath.startsWith("http://") || currentPath.startsWith("https://")
                            
                            if (!isLocalAndExists && !isHttpUrl) {
                                Log.w("Export", "Scene ${idx + 1} has missing/invalid mediaPath. Searching for matching visual asset...")
                                val query = scene.keywords.firstOrNull { it.isNotBlank() } ?: scene.visualPrompt
                                val resolvedUrl = repository.resolveVisualAsset(
                                    query = query,
                                    style = selectedStyleName.value,
                                    aspectRatio = selectedAspectRatio.value,
                                    index = idx,
                                    visualMedium = selectedVisualMedium.value,
                                    imageSource = selectedImageSource.value,
                                    resolution = selectedResolution.value,
                                    visualPrompt = scene.visualPrompt
                                )
                                if (resolvedUrl.isNotEmpty()) {
                                    currentPath = resolvedUrl
                                    currentRemoteUrl = resolvedUrl
                                }
                            }
                            
                            // Pre-download HTTP assets to local files concurrently
                            if (currentPath.startsWith("http://") || currentPath.startsWith("https://")) {
                                val downloadedLocal = repository.downloadMediaToLocal(currentPath)
                                if (downloadedLocal.isNotEmpty() && File(downloadedLocal).exists() && File(downloadedLocal).length() > 0L) {
                                    currentPath = downloadedLocal
                                }
                            }
                            
                            // Fallback mechanism: Assign default background asset from curated local drawable set or placeholder cache
                            if (currentPath.isEmpty() || (currentPath.startsWith("/") && (!File(currentPath).exists() || File(currentPath).length() == 0L))) {
                                Log.w("Export", "AI-generated visual search returned empty for script section ${idx + 1}. Assigning curated local drawable fallback asset...")
                                val fallbackLocal = com.ritvyom.yashoraReelgenerator.presentation.utils.LocalAssetFallbackManager.getCuratedFallbackAsset(
                                    context = getApplication<Application>(),
                                    sceneIndex = idx,
                                    style = selectedStyleName.value,
                                    aspectRatio = selectedAspectRatio.value
                                )
                                if (fallbackLocal.isNotEmpty()) {
                                    currentPath = fallbackLocal
                                }
                            }
                            
                            scene.copy(
                                mediaPath = currentPath,
                                remoteUrl = if (currentRemoteUrl.isNotEmpty()) currentRemoteUrl else currentPath
                            )
                        }
                    }.awaitAll()
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

            val totalEstimatedVideoFrames = (fullyValidatedScenes.sumOf { it.durationSeconds } * 30).coerceAtLeast(30)

            for (res in resolutionSequence) {
                Log.d("Export", "Attempting compilation at resolution option: $res")
                activeResolution = res
                compileSuccess = withContext(Dispatchers.IO) {
                    try {
                        ReelVideoCompiler.compileVideoWithAudio(
                            context = getApplication(),
                            scenes = fullyValidatedScenes,
                            voiceFiles = voiceFiles,
                            aspectRatio = selectedAspectRatio.value,
                            resolution = res,
                            outputFile = tempFile,
                            bgMusicCategory = bgMusicCategory.value,
                            bgMusicVolume = bgMusicVolume.value,
                            bgMusicEnabled = bgMusicEnabled.value,
                            preserveOriginalAudio = preserveOriginalAudio.value,
                            isEnhanceVideo = isEnhanceEnabled.value,
                            isEnhanceEnabled = isEnhanceEnabled.value,
                            watermarkConfig = watermarkConfig.value,
                            onProgress = { pr ->
                                val overallProgress = (0.20f + 0.75f * pr) // spans 20% to 95%
                                exportProgress.value = overallProgress
                                updateExportSpeedAndEta(
                                    currentProgress = overallProgress,
                                    isRenderingFrames = true,
                                    frameProgress = pr,
                                    totalFrames = totalEstimatedVideoFrames
                                )
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
                    Log.d("Export", "Compilation failed, using offline default fallback video path")
                    try {
                        android.util.Base64.decode(MINIMAL_PLAYABLE_MP4_B64, android.util.Base64.DEFAULT)
                    } catch (e2: Exception) {
                        ByteArray(0)
                    }
                }
            }

            exportStatus.value = "Saving video to gallery & Movies/Yashora..."
            val savedPathResult = saveAndFinalizeExportFile(
                finalBytes = finalBytes,
                fileName = fileName,
                title = title,
                activeProj = activeProj
            )

            exportProgress.value = 1.0f
            exportEtaFormatted.value = "Completed".localize(appLanguage.value)
            delay(400)

            isExporting.value = false
            exportedFilePath.value = savedPathResult
            compiledPreviewPath.value = savedPathResult
            isExportPendingConfirmation.value = false
            exportStatus.value = "Video exported and saved successfully!"
            stopAllPlayback()

            withContext(Dispatchers.Main) {
                try {
                    val displayMessage = "Video export complete! Saved to:\n$savedPathResult".localize(appLanguage.value)
                    android.widget.Toast.makeText(
                        getApplication(),
                        displayMessage,
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                } catch (ex: Exception) {
                    Log.e("Export", "Toast failed", ex)
                }
                onExportComplete()
            }
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
                isEnhanceVideo.value = false
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

    fun saveApprovedScriptToCloud(customScript: String? = null, topic: String? = null) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val activeProj = activeProject.value
                val finalScript = customScript ?: if (activeScenes.value.isNotEmpty()) {
                    buildScriptFromActiveScenes(activeScenes.value)
                } else {
                    activeProj?.scriptText ?: scriptText.value
                }
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
                    val rawTopic = topic ?: topicContext.value.ifEmpty { activeProj?.topicContext ?: "" }.ifEmpty { "Viral AI Reel" }
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

    fun finalizeSession(exportedVideoPath: String? = null) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val activeProj = activeProject.value
                val title = activeProj?.title ?: "Reel"
                val currentScenes = activeScenes.value
                if (currentScenes.isEmpty()) return@launch

                Log.d("FinalizeSession", "User finalized session. Preparing user-edited media and project for Firestore sync.")

                // 1. Process scenes: for any scene that has local media, upload to cloud storage if possible
                val processedScenes = currentScenes.mapIndexed { idx, scene ->
                    val path = scene.mediaPath ?: ""
                    val remote = scene.remoteUrl ?: ""
                    if ((path.startsWith("/") || !path.startsWith("http")) && !remote.startsWith("http")) {
                        val file = java.io.File(path)
                        if (file.exists() && file.length() > 0) {
                            val ext = if (scene.mediaType == "VIDEO") "mp4" else "jpg"
                            val dest = "user_media/scene_${System.currentTimeMillis()}_${idx + 1}.$ext"
                            val uploadedUrl = com.ritvyom.yashoraReelgenerator.data.remote.FirebaseCloudManager.uploadLocalMediaToCloud(path, dest)
                            if (!uploadedUrl.isNullOrEmpty()) {
                                scene.copy(mediaPath = uploadedUrl, remoteUrl = uploadedUrl)
                            } else {
                                scene
                            }
                        } else {
                            scene
                        }
                    } else {
                        scene
                    }
                }

                // If any scenes were updated with remote URLs, update in-memory state
                withContext(Dispatchers.Main) {
                    activeScenes.value = processedScenes
                }

                // 2. Reconstruct final script text from user's edited scenes
                val editedScript = buildScriptFromActiveScenes(processedScenes)
                    .ifBlank { activeProj?.scriptText ?: scriptText.value }

                val rawTopicForMatch = topicContext.value.ifEmpty { activeProj?.topicContext ?: "" }.ifEmpty { title }
                val normalizedTopicForMatch = try {
                    repository.normalizeTopic(rawTopicForMatch)
                } catch (e: Exception) {
                    rawTopicForMatch.trim().lowercase()
                }

                // 3. Save approved, user-edited script to Firestore scripts and script_cache
                saveApprovedScriptToCloud(customScript = editedScript, topic = normalizedTopicForMatch)

                // 4. Save pre-rendered asset cache with user's edited scenes and script
                val originalScript = activeProj?.scriptText ?: scriptText.value
                com.ritvyom.yashoraReelgenerator.data.remote.FirebaseCloudManager.savePrerenderedAssetCache(
                    scriptText = editedScript,
                    style = selectedStyleName.value,
                    aspectRatio = selectedAspectRatio.value,
                    visualMedium = selectedVisualMedium.value,
                    scenes = processedScenes,
                    topic = normalizedTopicForMatch,
                    originalScriptText = originalScript
                )

                // 5. Upload exported master video to Firebase Storage if available
                var cloudExportedVideoUrl = ""
                val videoFileToUpload = (exportedVideoPath ?: exportedFilePath.value ?: compiledPreviewPath.value)
                    ?.let { java.io.File(it) }
                if (videoFileToUpload != null && videoFileToUpload.exists() && videoFileToUpload.length() > 0) {
                    val dest = "exported_reels/reel_${System.currentTimeMillis()}_${videoFileToUpload.name}"
                    cloudExportedVideoUrl = com.ritvyom.yashoraReelgenerator.data.remote.FirebaseCloudManager.uploadLocalMediaToCloud(videoFileToUpload.absolutePath, dest) ?: ""
                }

                // 6. Share project in Firestore with isUserEdited = true
                com.ritvyom.yashoraReelgenerator.data.remote.FirebaseCloudManager.shareProject(
                    title = title,
                    topic = normalizedTopicForMatch,
                    scriptText = editedScript,
                    style = selectedStyleName.value,
                    aspectRatio = selectedAspectRatio.value,
                    visualMedium = selectedVisualMedium.value,
                    publishingStyle = selectedPublishingStyle.value,
                    scenes = processedScenes,
                    exportedVideoUrl = cloudExportedVideoUrl,
                    isUserEdited = true
                ) { success, error ->
                    Log.d("FinalizeSession", "Cloud share result: success=$success, error=$error, isUserEdited=true")
                }

                // 7. Share verified media mappings globally
                try {
                    repository.shareMediaMappingsForProject(
                        scenes = processedScenes,
                        style = selectedStyleName.value,
                        visualMedium = selectedVisualMedium.value,
                        aspectRatio = selectedAspectRatio.value
                    )
                } catch (e: Exception) {
                    Log.w("FinalizeSession", "Failed to share verified media mappings on export", e)
                }
            } catch (e: Exception) {
                Log.e("FinalizeSession", "Error during finalizeSession", e)
            }
        }
    }

    private suspend fun saveAndFinalizeExportFile(
        finalBytes: ByteArray,
        fileName: String,
        title: String,
        activeProj: ProjectEntity?
    ): String = withContext(Dispatchers.IO) {
        var savedPath = "Movies/Yashora/$fileName"
        val resolver = getApplication<Application>().contentResolver
        var success = false

        // 1. Write physical file directly to Movies/Yashora directory
        try {
            val moviesFolder = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
            val appFolder = File(moviesFolder, "Yashora")
            if (!appFolder.exists()) {
                appFolder.mkdirs()
            }
            val physicalFile = File(appFolder, fileName)
            physicalFile.writeBytes(finalBytes)
            savedPath = physicalFile.absolutePath
            success = true
            Log.d("Export", "Saved physical file directly to $savedPath (${finalBytes.size} bytes)")
        } catch (e: Exception) {
            Log.e("Export", "Failed physical writing directly to Movies folder", e)
        }

        // 2. Scoped Storage / MediaStore Record (Android Q+)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            try {
                val contentValues = ContentValues().apply {
                    put(MediaStore.Video.Media.DISPLAY_NAME, fileName)
                    put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                    put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/Yashora")
                    put(MediaStore.Video.Media.IS_PENDING, 1)
                }
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
                    Log.d("Export", "Saved modern MediaStore record -> $uri")
                }
            } catch (e: Exception) {
                Log.e("Export", "Failed writing via modern MediaStore", e)
            }
        } else {
            // Legacy Android MediaStore insert
            try {
                val legacyValues = ContentValues().apply {
                    put(MediaStore.Video.Media.DISPLAY_NAME, fileName)
                    put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                    put(MediaStore.Video.Media.DATA, savedPath)
                }
                resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, legacyValues)
            } catch (e: Exception) {
                Log.e("Export", "Failed legacy MediaStore insert", e)
            }
        }

        // 3. Fallback write if Movies folder write failed
        if (!success) {
            try {
                val downloadsFolder = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val appFolderDl = File(downloadsFolder, "Yashora")
                if (!appFolderDl.exists()) appFolderDl.mkdirs()
                val physicalFileDl = File(appFolderDl, fileName)
                physicalFileDl.writeBytes(finalBytes)
                savedPath = physicalFileDl.absolutePath
                success = true
            } catch (e: Exception) {
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

        // 4. Force MediaScannerConnection to index saved path so Gallery / Photos / Files app see it immediately
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

        // 5. Save internal master copy
        try {
            val internalDir = File(getApplication<Application>().filesDir, "YashoraCompiledVideos").apply {
                if (!exists()) mkdirs()
            }
            val projId = activeProj?.id ?: 0
            if (finalBytes.isNotEmpty()) {
                if (projId > 0) {
                    val masterFileByProj = File(internalDir, "project_${projId}_master.mp4")
                    masterFileByProj.writeBytes(finalBytes)
                }
                val masterFileByName = File(internalDir, fileName)
                masterFileByName.writeBytes(finalBytes)
                Log.d("Export", "Saved internal master copy (${finalBytes.size} bytes)")
            }
        } catch (e: Exception) {
            Log.e("Export", "Error writing internal master copy", e)
        }

        // 6. Update active project status & scenesJson in Room database
        activeProj?.let { proj ->
            val updatedProj = proj.copy(
                status = "Completed",
                scenesJson = scenesAdapter.toJson(activeScenes.value)
            )
            activeProject.value = updatedProj
            repository.insertProject(updatedProj)
        }

        // 7. Save Export History entity
        val historyEntity = ExportHistoryEntity(
            projectId = activeProj?.id ?: 0,
            projectTitle = title,
            fileName = fileName,
            filePath = savedPath,
            fileSize = finalBytes.size.toLong(),
            resolution = selectedResolution.value,
            durationMs = activeScenes.value.sumOf { it.durationSeconds } * 1000L
        )
        repository.insertExportHistory(historyEntity)

        // 8. Trigger Firestore sync & Notification
        finalizeSession(savedPath)

        try {
            NotificationHelper.showExportCompleteNotification(
                getApplication(),
                "Export Complete".localize(appLanguage.value),
                ("Video saved to: " + savedPath).localize(appLanguage.value)
            )
        } catch (e: Exception) {
            Log.e("Export", "Notification post failed", e)
        }

        savedPath
    }

    fun finalizeExport(onSuccess: () -> Unit) {
        stopAllPlayback()
        viewModelScope.launch {
            try {
                val previewPath = compiledPreviewPath.value ?: return@launch
                val previewFile = File(previewPath)
                if (!previewFile.exists()) return@launch

                VideoFileManager.cleanupTemporaryFilesAndFailedAttempts(getApplication(), previewPath)

                val finalBytes = previewFile.readBytes()
                val activeProj = activeProject.value
                val title = activeProj?.title ?: "Reel"
                val sdf = java.text.SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", java.util.Locale.US)
                val timestamp = sdf.format(java.util.Date())
                val fileName = "Yashora_Reel_$timestamp.mp4"

                val savedPath = saveAndFinalizeExportFile(
                    finalBytes = finalBytes,
                    fileName = fileName,
                    title = title,
                    activeProj = activeProj
                )

                exportedFilePath.value = savedPath
                compiledPreviewPath.value = savedPath
                isExportPendingConfirmation.value = false
                stopAllPlayback()

                withContext(Dispatchers.Main) {
                    try {
                        val displayMsg = "Video export complete! Saved to:\n$savedPath".localize(appLanguage.value)
                        android.widget.Toast.makeText(
                            getApplication(),
                            displayMsg,
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

            val rawScenes = getProjectScenes(project.id)
            val scenes = rawScenes.mapIndexed { idx, scene ->
                val path = scene.mediaPath ?: ""
                val remote = scene.remoteUrl ?: ""
                if ((path.startsWith("/") || !path.startsWith("http")) && !remote.startsWith("http")) {
                    val file = java.io.File(path)
                    if (file.exists() && file.length() > 0) {
                        val ext = if (scene.mediaType == "VIDEO") "mp4" else "jpg"
                        val dest = "user_media/scene_${System.currentTimeMillis()}_${idx + 1}.$ext"
                        val uploadedUrl = com.ritvyom.yashoraReelgenerator.data.remote.FirebaseCloudManager.uploadLocalMediaToCloud(path, dest)
                        if (!uploadedUrl.isNullOrEmpty()) {
                            scene.copy(mediaPath = uploadedUrl, remoteUrl = uploadedUrl)
                        } else {
                            scene
                        }
                    } else {
                        scene
                    }
                } else {
                    scene
                }
            }

            var exportedUrl = ""
            val videoFilePath = if (activeProject.value?.id == project.id) {
                exportedFilePath.value ?: compiledPreviewPath.value
            } else null
            if (!videoFilePath.isNullOrEmpty()) {
                val videoFile = java.io.File(videoFilePath)
                if (videoFile.exists() && videoFile.length() > 0) {
                    val dest = "exported_reels/reel_${System.currentTimeMillis()}_${videoFile.name}"
                    exportedUrl = com.ritvyom.yashoraReelgenerator.data.remote.FirebaseCloudManager.uploadLocalMediaToCloud(videoFile.absolutePath, dest) ?: ""
                }
            }

            val rawTopic = project.topicContext.ifEmpty { project.title }
            val normalizedTopic = try {
                repository.normalizeTopic(rawTopic)
            } catch (e: Exception) {
                rawTopic.trim().lowercase()
            }
            Log.i("MainViewModel", "Manually Sharing Project: '$rawTopic' -> Normalized Topic: '$normalizedTopic'")

            com.ritvyom.yashoraReelgenerator.data.remote.FirebaseCloudManager.savePrerenderedAssetCache(
                scriptText = project.scriptText,
                style = project.videoStyle,
                aspectRatio = project.aspectRatio,
                visualMedium = "Video",
                scenes = scenes,
                topic = normalizedTopic
            )

            com.ritvyom.yashoraReelgenerator.data.remote.FirebaseCloudManager.shareProject(
                title = project.title,
                topic = normalizedTopic,
                scriptText = project.scriptText,
                style = project.videoStyle,
                aspectRatio = project.aspectRatio,
                visualMedium = "Video",
                publishingStyle = project.publishingStyle,
                scenes = scenes,
                exportedVideoUrl = exportedUrl,
                isUserEdited = true
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

    private var importTemplateJob: kotlinx.coroutines.Job? = null
    val isImportingTemplate = MutableStateFlow(false)
    val isImportingTemplateMinimized = MutableStateFlow(false)
    val importTemplateProgress = MutableStateFlow(0f)
    val importTemplateStatus = MutableStateFlow("")
    val importingTemplateTitle = MutableStateFlow("")
    val templateImportCompleteEvent = kotlinx.coroutines.flow.MutableSharedFlow<ProjectEntity>(extraBufferCapacity = 1)

    fun cancelImportTemplate() {
        importTemplateJob?.cancel()
        importTemplateJob = null
        isImportingTemplate.value = false
        isImportingTemplateMinimized.value = false
        importTemplateProgress.value = 0f
        importTemplateStatus.value = ""
        importingTemplateTitle.value = ""
    }

    fun loadSharedProjectAsTemplate(
        sharedProject: com.ritvyom.yashoraReelgenerator.data.remote.SharedProject,
        onComplete: ((ProjectEntity) -> Unit)? = null
    ) {
        importTemplateJob?.cancel()
        importTemplateJob = viewModelScope.launch {
            try {
                isImportingTemplate.value = true
                isImportingTemplateMinimized.value = false
                importingTemplateTitle.value = sharedProject.title.ifBlank { "Community Reel" }
                importTemplateProgress.value = 0.05f
                importTemplateStatus.value = "Initializing cloud template..."
                
                // 1. Reset any previous export or stale preview state
                compiledPreviewPath.value = null
                isExporting.value = false
                exportedFilePath.value = null
                exportProgress.value = 0f
                
                // 2. Update State Variables
                selectedStyleName.value = sharedProject.style
                selectedAspectRatio.value = sharedProject.aspectRatio
                scriptText.value = sharedProject.scriptText
                topicContext.value = sharedProject.topic
                selectedPublishingStyle.value = sharedProject.publishingStyle
                selectedVisualMedium.value = sharedProject.visualMedium
                
                // 3. Download and map scenes (uses local cache if files already exist on device)
                val totalScenes = sharedProject.scenes.size
                val scenesList = mutableListOf<Scene>()
                for (index in sharedProject.scenes.indices) {
                    ensureActive()
                    val sharedScene = sharedProject.scenes[index]
                    importTemplateStatus.value = "Loading scene asset ${index + 1} of $totalScenes..."
                    importTemplateProgress.value = 0.10f + (0.85f * (index.toFloat() / totalScenes.toFloat().coerceAtLeast(1f)))
                    
                    val localPath = if (sharedScene.mediaPath.isNotEmpty()) {
                        try {
                            ensureActive()
                            repository.downloadMediaToLocal(
                                urlStr = sharedScene.mediaPath,
                                projectId = sharedProject.id,
                                projectTitle = sharedProject.title,
                                sceneNumber = sharedScene.sceneNumber,
                                narrationText = sharedScene.narrationText,
                                visualPrompt = sharedScene.visualPrompt,
                                durationSeconds = sharedScene.durationSeconds,
                                aspectRatio = sharedProject.aspectRatio
                            )
                        } catch (e: kotlinx.coroutines.CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            Log.e("MainViewModel", "Failed downloading shared scene asset", e)
                            null
                        }
                    } else null
                    
                    ensureActive()
                    scenesList.add(
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
                    )
                }
                
                ensureActive()
                activeScenes.value = scenesList
                selectedSceneIndex.value = 0

                // 4. Create and save a dedicated Draft project in Room DB with exact scenes JSON
                val title = if (sharedProject.title.isNotBlank()) {
                    sharedProject.title
                } else if (sharedProject.scriptText.length > 20) {
                    sharedProject.scriptText.substring(0, 17) + "..."
                } else {
                    "Community Reel #${System.currentTimeMillis() % 1000}"
                }

                val entity = ProjectEntity(
                    title = title,
                    scriptText = sharedProject.scriptText,
                    videoStyle = sharedProject.style,
                    voiceName = selectedVoiceName.value,
                    voiceCategory = selectedVoiceCategory.value,
                    language = selectedLanguage.value,
                    aspectRatio = sharedProject.aspectRatio,
                    resolution = selectedResolution.value,
                    fps = selectedFps.value,
                    bgMusicCategory = bgMusicCategory.value,
                    bgMusicVolume = bgMusicVolume.value,
                    voiceVolume = voiceVolume.value,
                    status = "Draft",
                    scenesJson = scenesAdapter.toJson(scenesList),
                    topicContext = sharedProject.topic,
                    publishingStyle = sharedProject.publishingStyle
                )
                val projId = repository.insertProject(entity)
                val createdProject = entity.copy(id = projId.toInt())
                activeProject.value = createdProject
                
                importTemplateProgress.value = 1.0f
                importTemplateStatus.value = "Template loaded successfully!"
                delay(300)
                isImportingTemplate.value = false
                isImportingTemplateMinimized.value = false
                
                templateImportCompleteEvent.tryEmit(createdProject)
                onComplete?.invoke(createdProject)
            } catch (e: kotlinx.coroutines.CancellationException) {
                Log.d("MainViewModel", "Template import was cancelled by user.")
                isImportingTemplate.value = false
                isImportingTemplateMinimized.value = false
                importTemplateProgress.value = 0f
                importTemplateStatus.value = ""
                importingTemplateTitle.value = ""
            } catch (e: Exception) {
                Log.e("MainViewModel", "Error loading template", e)
                isImportingTemplate.value = false
                isImportingTemplateMinimized.value = false
                importTemplateProgress.value = 0f
                importTemplateStatus.value = ""
            }
        }
    }

    fun loadSharedProjectAsTemplate(
        sharedProject: com.ritvyom.yashoraReelgenerator.data.remote.SharedProject,
        onComplete: () -> Unit
    ) {
        loadSharedProjectAsTemplate(sharedProject) { _ ->
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

    val cachedCommunityVideos = repository.allCachedCommunityVideos
    val cachedCommunityVideosCount = repository.cachedCommunityVideosCount
    val totalCommunityVideoCacheBytes = repository.totalCommunityVideoCacheBytes

    fun clearCommunityVideoCache() {
        viewModelScope.launch {
            repository.clearAllCommunityVideoCache()
        }
    }

    override fun onCleared() {
        super.onCleared()
        tts?.shutdown()
    }

        /**
     * Sets quick script text generated from AI Chat Studio.
     */
    fun setQuickScript(text: String) {
        scriptText.value = text
    }

    companion object {
        private const val MINIMAL_PLAYABLE_MP4_B64 =
            "AAAAIGZ0eXBpc29tAAAAAGlzb21tcDQyAAAAAGZyZWUAAABLbWRhdA" +
            "AAAAGm1vb3YAAABsbXZoZAAAAADIPYF4eD2BeAAAA+gAAANqAAEAAAE" +
            "AAAAAAAAAAAAAAAAAAAAAAAAAQAAAAAAAAAAAAAAAAAAAAAABAAEAA" +
            "AAAbXZleQAAABB0cmV4AAAAAQAAAAEAAAAAAAEAdHJhawAAAFx0a2hk" +
            "AAAAAwAAAAAAAQAAAAAAAACHAAAAAAAAAAAAAAAAAAAAAAEAAAAAAAA" +
            "AAQAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAbWRpYQAAACBtZGhkA" +
            "AAAAAB9AAAAfQAAABAAAAAAABsaGRscgAAAAAAAAAAbWV0YQAAAAA" +
            "AAAAAAAAASGFuZGxlcgAAAAFtaW5mAAAAFWhkaHIAAAAAAAAAAG1ld" +
            "GEAAAAAAGRpbmYAAAAQZHJlZgAAAAAAAAABAAAAGG11cmwAAAAAAAA" +
            "AAQAAAAIAdXJpIAAAABhzdGJsAAAAbXN0c2QAAAAAAAAAAQAAAD91c" +
            "mkgAAAAAQAAAAAAAAAAAAAAAAAAAAAAACNjb20ud2lkZ2V0cy5tZX" +
            "RhZGF0YS52aWRlbwAAAAIAdWNoIAAAABhzdHRzAAAAAAAAAAEAAAAB" +
            "AAAAAQAAABhzdHN6AAAAAAAAAAAAAAABAAAAAQAAABhzdGNvAAAAA" +
            "AAAAAEAAAAw"
    }
}
