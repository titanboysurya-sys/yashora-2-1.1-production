package com.ritvyom.yashoraReelgenerator.ui

import android.media.MediaPlayer
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ritvyom.yashoraReelgenerator.data.api.ElevenLabsErrorHandler
import com.ritvyom.yashoraReelgenerator.data.db.TtsHistoryEntity
import com.ritvyom.yashoraReelgenerator.data.model.Voice
import com.ritvyom.yashoraReelgenerator.data.repository.TtsRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.io.File

data class PlaybackState(
    val playingHistoryId: Int? = null,
    val isPlaying: Boolean = false,
    val progress: Float = 0.0f,
    val duration: Int = 0,
    val currentPosition: Int = 0
)

data class VoxElevenUiState(
    val apiKey: String = "",
    val isApiKeySaved: Boolean = false,
    val isApiKeyValid: Boolean = false,
    val isCheckingApiKey: Boolean = false,
    val apiKeyError: String? = null,
    
    // User info
    val userTier: String? = null,
    val characterCount: Int = 0,
    val characterLimit: Int = 0,
    
    // Voice List
    val voices: List<Voice> = DEFAULT_VOICES,
    val isFetchingVoices: Boolean = false,
    val selectedVoice: Voice? = DEFAULT_VOICES.firstOrNull(),
    
    // TTS Parameters
    val textToGenerate: String = "",
    val selectedModelId: String = "eleven_multilingual_v2",
    val stability: Float = 0.5f,
    val similarityBoost: Float = 0.75f,
    
    // Generation progress
    val isGenerating: Boolean = false,
    val generationError: String? = null,
    
    // History
    val historyList: List<TtsHistoryEntity> = emptyList(),
    
    // Playback state
    val playbackState: PlaybackState = PlaybackState(),
    val playingPreviewVoiceId: String? = null,
    val isPreviewLoading: Boolean = false,
    
    // Theme Preference
    val isDarkMode: Boolean = false,
    
    // Global Snackbar notification
    val snackbarMessage: String? = null
)

val DEFAULT_VOICES = listOf(
    Voice(voiceId = "21m00Tcm4TlvDq8ikWAM", name = "Rachel (US / Soft & Friendly)", category = "premade", labels = mapOf("gender" to "female", "accent" to "american")),
    Voice(voiceId = "pNInz6obpg7j8YtMUIa9", name = "Adam (US / Deep & Narrator)", category = "premade", labels = mapOf("gender" to "male", "accent" to "american")),
    Voice(voiceId = "EXAVITQu4vr4xnSDOCMa", name = "Bella (US / Crisp & Clear)", category = "premade", labels = mapOf("gender" to "female", "accent" to "american")),
    Voice(voiceId = "IKne3meq5aSn9XLyUdCD", name = "Charlie (UK / Casual Conversational)", category = "premade", labels = mapOf("gender" to "male", "accent" to "british")),
    Voice(voiceId = "cgSgspJ2msm6clMC924e", name = "Glinda (US / Warm Accent)", category = "premade", labels = mapOf("gender" to "female", "accent" to "american")),
    Voice(voiceId = "N2lVS1w92zCOyYr99Gg4", name = "Callum (UK / Friendly Guy)", category = "premade", labels = mapOf("gender" to "male", "accent" to "british"))
)

class VoxElevenViewModel(private val repository: TtsRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(VoxElevenUiState())
    val uiState: StateFlow<VoxElevenUiState> = _uiState.asStateFlow()

    private var mediaPlayer: MediaPlayer? = null
    private var progressJob: Job? = null

    init {
        // Load Dark Mode Preference
        val savedDarkMode = repository.getDarkModePreference() ?: false

        // Load Draft Inputs
        val savedDraftText = repository.getDraftText() ?: ""
        val savedDraftVoiceId = repository.getDraftVoiceId()
        val rawDraftModel = repository.getDraftModelId() ?: "eleven_multilingual_v2"
        val savedDraftModelId = if (rawDraftModel == "eleven_monolingual_v1") "eleven_turbo_v2_5" else rawDraftModel
        val savedStability = repository.getDraftStability() ?: 0.5f
        val savedSimilarity = repository.getDraftSimilarity() ?: 0.75f

        val initialVoice = DEFAULT_VOICES.firstOrNull { it.voiceId == savedDraftVoiceId } ?: DEFAULT_VOICES.firstOrNull()

        _uiState.update {
            it.copy(
                isDarkMode = savedDarkMode,
                textToGenerate = savedDraftText,
                selectedVoice = initialVoice,
                selectedModelId = savedDraftModelId,
                stability = savedStability,
                similarityBoost = savedSimilarity
            )
        }

        // Load API Key and start validation if present
        val savedKey = repository.getSavedApiKey()
        if (!savedKey.isNullOrEmpty()) {
            _uiState.update { 
                it.copy(
                    apiKey = savedKey,
                    isApiKeySaved = true
                )
            }
            validateAndLoadApiKey(savedKey)
        }

        // Observe local database history
        viewModelScope.launch {
            repository.allHistory.collect { history ->
                _uiState.update { it.copy(historyList = history) }
            }
        }
    }

    fun toggleDarkMode(isDark: Boolean) {
        repository.saveDarkModePreference(isDark)
        _uiState.update { it.copy(isDarkMode = isDark) }
    }

    fun onApiKeyChanged(newKey: String) {
        _uiState.update { it.copy(apiKey = newKey, apiKeyError = null) }
    }

    fun saveAndValidateKey() {
        val key = _uiState.value.apiKey.trim()
        if (key.isEmpty()) {
            _uiState.update { it.copy(apiKeyError = "API key cannot be empty") }
            return
        }
        repository.saveApiKey(key)
        _uiState.update { it.copy(isApiKeySaved = true, apiKeyError = null) }
        validateAndLoadApiKey(key)
    }

    fun clearSavedApiKey() {
        repository.clearApiKey()
        stopPlayback()
        _uiState.update { 
            it.copy(
                apiKey = "",
                isApiKeySaved = false,
                isApiKeyValid = false,
                userTier = null,
                characterCount = 0,
                characterLimit = 0,
                voices = DEFAULT_VOICES,
                selectedVoice = DEFAULT_VOICES.firstOrNull(),
                apiKeyError = null
            )
        }
    }

    fun clearSnackbarMessage() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }

    private fun validateAndLoadApiKey(key: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isCheckingApiKey = true, apiKeyError = null) }
            try {
                // Fetch User Details to validate API Key
                val userInfo = repository.validateApiKey(key)
                
                _uiState.update {
                    it.copy(
                        isApiKeyValid = true,
                        userTier = userInfo.subscription.tier,
                        characterCount = userInfo.subscription.characterCount,
                        characterLimit = userInfo.subscription.characterLimit
                    )
                }

                // If valid, fetch official voice list
                fetchVoicesList(key)

            } catch (e: Exception) {
                val parsed = ElevenLabsErrorHandler.parseError(e, actionName = "API Key Validation")
                _uiState.update { 
                    it.copy(
                        isApiKeyValid = false,
                        apiKeyError = parsed.userFriendlyMessage,
                        snackbarMessage = parsed.userFriendlyMessage
                    )
                }
            } finally {
                _uiState.update { it.copy(isCheckingApiKey = false) }
            }
        }
    }

    private fun fetchVoicesList(key: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isFetchingVoices = true) }
            try {
                val fetchedVoices = repository.fetchVoices(key)
                if (fetchedVoices.isNotEmpty()) {
                    val savedDraftVoiceId = repository.getDraftVoiceId()
                    val targetVoice = fetchedVoices.firstOrNull { v -> v.voiceId == _uiState.value.selectedVoice?.voiceId }
                        ?: fetchedVoices.firstOrNull { v -> v.voiceId == savedDraftVoiceId }
                        ?: fetchedVoices.first()

                    _uiState.update {
                        it.copy(
                            voices = fetchedVoices,
                            selectedVoice = targetVoice
                        )
                    }
                    repository.saveDraftVoiceId(targetVoice.voiceId)
                }
            } catch (e: Exception) {
                val parsed = ElevenLabsErrorHandler.parseError(e, actionName = "Fetch Voices")
                _uiState.update {
                    it.copy(
                        apiKeyError = "Key verified, but could not load custom voices. Using default voices.",
                        snackbarMessage = "Voice List Warning: ${parsed.userFriendlyMessage}"
                    )
                }
            } finally {
                _uiState.update { it.copy(isFetchingVoices = false) }
            }
        }
    }

    fun refreshVoices() {
        val key = _uiState.value.apiKey.trim()
        if (key.isNotEmpty()) {
            fetchVoicesList(key)
        }
    }

    fun seekTo(positionMs: Int) {
        mediaPlayer?.let { mp ->
            try {
                mp.seekTo(positionMs)
                val dur = mp.duration
                val pct = if (dur > 0) positionMs.toFloat() / dur.toFloat() else 0.0f
                _uiState.update { state ->
                    state.copy(
                        playbackState = state.playbackState.copy(
                            currentPosition = positionMs,
                            progress = pct
                        )
                    )
                }
            } catch (e: Exception) {
                // Ignore seek failure gracefully
            }
        }
    }

    fun onTextToGenerateChanged(newText: String) {
        repository.saveDraftText(newText)
        _uiState.update { it.copy(textToGenerate = newText, generationError = null) }
    }

    fun onVoiceSelected(voice: Voice) {
        repository.saveDraftVoiceId(voice.voiceId)
        _uiState.update { it.copy(selectedVoice = voice) }
    }

    fun onModelSelected(modelId: String) {
        repository.saveDraftModelId(modelId)
        _uiState.update { it.copy(selectedModelId = modelId) }
    }

    fun onStabilityChanged(value: Float) {
        repository.saveDraftStability(value)
        _uiState.update { it.copy(stability = value) }
    }

    fun onSimilarityBoostChanged(value: Float) {
        repository.saveDraftSimilarity(value)
        _uiState.update { it.copy(similarityBoost = value) }
    }

    fun generateVoice() {
        val currentState = _uiState.value
        val text = currentState.textToGenerate.trim()
        val apiKey = currentState.apiKey.trim()
        val voice = currentState.selectedVoice

        if (apiKey.isEmpty()) {
            _uiState.update { it.copy(generationError = "Please enter and save your API Key first") }
            return
        }
        if (text.isEmpty()) {
            _uiState.update { it.copy(generationError = "Please type some text to generate speech") }
            return
        }
        if (voice == null) {
            _uiState.update { it.copy(generationError = "Please select a voice") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isGenerating = true, generationError = null) }
            try {
                val audioFile = repository.generateSpeech(
                    apiKey = apiKey,
                    voiceId = voice.voiceId,
                    text = text,
                    modelId = currentState.selectedModelId,
                    stability = currentState.stability.toDouble(),
                    similarityBoost = currentState.similarityBoost.toDouble()
                )

                // Save inside database history
                val entity = TtsHistoryEntity(
                    text = text,
                    voiceId = voice.voiceId,
                    voiceName = voice.name,
                    modelId = currentState.selectedModelId,
                    stability = currentState.stability.toDouble(),
                    similarityBoost = currentState.similarityBoost.toDouble(),
                    filePath = audioFile.absolutePath
                )
                repository.insertHistoryItem(entity)

                // Refresh character usage metrics after generation
                refreshUserInfo(apiKey)

                // Clear input text on success
                repository.saveDraftText("")
                _uiState.update { it.copy(textToGenerate = "") }

            } catch (e: Exception) {
                val parsed = ElevenLabsErrorHandler.parseError(
                    e = e,
                    currentModelId = currentState.selectedModelId,
                    actionName = "Speech Generation"
                )

                if (parsed.isModelOrLanguageMismatch || parsed.fallbackModelId != null) {
                    val fallback = parsed.fallbackModelId ?: "eleven_multilingual_v2"
                    _uiState.update { it.copy(selectedModelId = fallback) }
                    repository.saveDraftModelId(fallback)
                }

                _uiState.update { 
                    it.copy(
                        generationError = parsed.userFriendlyMessage,
                        snackbarMessage = parsed.userFriendlyMessage
                    )
                }
            } finally {
                _uiState.update { it.copy(isGenerating = false) }
            }
        }
    }

    private suspend fun refreshUserInfo(key: String) {
        try {
            val userInfo = repository.validateApiKey(key)
            _uiState.update {
                it.copy(
                    characterCount = userInfo.subscription.characterCount,
                    characterLimit = userInfo.subscription.characterLimit,
                    userTier = userInfo.subscription.tier
                )
            }
        } catch (e: Exception) {
            // Silently ignore character usage refresh errors
        }
    }

    fun deleteHistoryItem(entity: TtsHistoryEntity) {
        viewModelScope.launch {
            // If deleting the currently playing file, stop it first
            if (_uiState.value.playbackState.playingHistoryId == entity.id) {
                stopPlayback()
            }
            repository.deleteHistoryItem(entity)
        }
    }

    fun clearAllHistory() {
        viewModelScope.launch {
            stopPlayback()
            repository.clearHistory()
        }
    }

    // --- Audio Playback Logic ---

    fun togglePlayback(entity: TtsHistoryEntity) {
        val currentPlayback = _uiState.value.playbackState
        
        if (currentPlayback.playingHistoryId == entity.id) {
            if (currentPlayback.isPlaying) {
                pausePlayback()
            } else {
                resumePlayback()
            }
        } else {
            startNewPlayback(entity)
        }
    }

    private fun startNewPlayback(entity: TtsHistoryEntity) {
        stopPlayback()
        val file = File(entity.filePath)
        if (!file.exists()) {
            _uiState.update { it.copy(generationError = "Audio file no longer exists locally.") }
            return
        }

        try {
            mediaPlayer = MediaPlayer().apply {
                setDataSource(file.absolutePath)
                prepare()
                setOnCompletionListener {
                    stopPlayback()
                }
                start()
            }

            _uiState.update {
                it.copy(
                    playbackState = PlaybackState(
                        playingHistoryId = entity.id,
                        isPlaying = true,
                        duration = mediaPlayer?.duration ?: 0,
                        currentPosition = 0,
                        progress = 0.0f
                    )
                )
            }

            startProgressTracker()

        } catch (e: Exception) {
            _uiState.update { it.copy(generationError = "Could not play audio: ${e.localizedMessage}") }
        }
    }

    private fun pausePlayback() {
        mediaPlayer?.let {
            if (it.isPlaying) {
                it.pause()
                stopProgressTracker()
                _uiState.update { state ->
                    state.copy(
                        playbackState = state.playbackState.copy(isPlaying = false)
                    )
                }
            }
        }
    }

    private fun resumePlayback() {
        mediaPlayer?.let {
            it.start()
            _uiState.update { state ->
                state.copy(
                    playbackState = state.playbackState.copy(isPlaying = true)
                )
            }
            startProgressTracker()
        }
    }

    // --- Voice Sample Preview Logic (0 Coins / Free) ---

    fun toggleVoicePreview(voice: Voice) {
        val currentPlayingId = _uiState.value.playingPreviewVoiceId
        if (currentPlayingId == voice.voiceId) {
            stopVoicePreview()
            return
        }

        stopPlayback()
        stopVoicePreview()

        val sampleUrl = voice.previewUrl?.ifBlank { null }
            ?: "https://api.elevenlabs.io/v1/voices/${voice.voiceId}/sample"

        _uiState.update { 
            it.copy(
                playingPreviewVoiceId = voice.voiceId,
                isPreviewLoading = true
            )
        }

        viewModelScope.launch {
            try {
                mediaPlayer = MediaPlayer().apply {
                    setDataSource(sampleUrl)
                    setOnPreparedListener { mp ->
                        _uiState.update { it.copy(isPreviewLoading = false) }
                        mp.start()
                    }
                    setOnCompletionListener {
                        stopVoicePreview()
                    }
                    setOnErrorListener { _, _, _ ->
                        stopVoicePreview()
                        true
                    }
                    prepareAsync()
                }
            } catch (e: Exception) {
                stopVoicePreview()
            }
        }
    }

    fun stopVoicePreview() {
        try {
            mediaPlayer?.apply {
                if (isPlaying) {
                    stop()
                }
                release()
            }
        } catch (e: Exception) {
            // Ignore
        }
        mediaPlayer = null
        _uiState.update {
            it.copy(
                playingPreviewVoiceId = null,
                isPreviewLoading = false
            )
        }
    }

    private fun stopPlayback() {
        stopProgressTracker()
        try {
            mediaPlayer?.apply {
                if (isPlaying) {
                    stop()
                }
                release()
            }
        } catch (e: Exception) {
            // Silently swallow errors on release
        }
        mediaPlayer = null
        _uiState.update {
            it.copy(
                playbackState = PlaybackState(),
                playingPreviewVoiceId = null,
                isPreviewLoading = false
            )
        }
    }

    private fun startProgressTracker() {
        progressJob?.cancel()
        progressJob = viewModelScope.launch {
            while (true) {
                delay(100)
                mediaPlayer?.let { mp ->
                    if (mp.isPlaying) {
                        val pos = mp.currentPosition
                        val dur = mp.duration
                        val pct = if (dur > 0) pos.toFloat() / dur.toFloat() else 0.0f
                        _uiState.update { state ->
                            state.copy(
                                playbackState = state.playbackState.copy(
                                    currentPosition = pos,
                                    progress = pct
                                )
                            )
                        }
                    }
                } ?: break
            }
        }
    }

    private fun stopProgressTracker() {
        progressJob?.cancel()
        progressJob = null
    }

    override fun onCleared() {
        super.onCleared()
        stopPlayback()
    }
}

class VoxElevenViewModelFactory(private val repository: TtsRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(VoxElevenViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return VoxElevenViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
