package com.ritvyom.yashoraReelgenerator.presentation.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ritvyom.yashoraReelgenerator.YashoraApplication
import com.ritvyom.yashoraReelgenerator.data.model.VideoScript
import com.ritvyom.yashoraReelgenerator.data.repository.ScriptRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface GenerateUiState {
    object Idle : GenerateUiState
    object Loading : GenerateUiState
    data class Success(val script: VideoScript) : GenerateUiState
    data class Error(val message: String) : GenerateUiState
}

class ScriptViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: ScriptRepository = (application as YashoraApplication).scriptRepository

    // Observe all saved scripts in history
    val historyState: StateFlow<List<VideoScript>> = repository.allScripts
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Observe saved script settings
    val scriptLanguage: StateFlow<String> = repository.scriptLanguage
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = "english"
        )

    val scriptPlatform: StateFlow<String> = repository.scriptPlatform
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = "YouTube Shorts / Reels"
        )

    val scriptDuration: StateFlow<String> = repository.scriptDuration
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = "short"
        )

    val scriptTone: StateFlow<String> = repository.scriptTone
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = "casual"
        )

    fun saveScriptLanguage(language: String) {
        viewModelScope.launch {
            repository.saveScriptLanguage(language)
        }
    }

    fun saveScriptPlatform(platform: String) {
        viewModelScope.launch {
            repository.saveScriptPlatform(platform)
        }
    }

    fun saveScriptDuration(duration: String) {
        viewModelScope.launch {
            repository.saveScriptDuration(duration)
        }
    }

    fun saveScriptTone(tone: String) {
        viewModelScope.launch {
            repository.saveScriptTone(tone)
        }
    }

    // Current generation state
    private val _generateUiState = MutableStateFlow<GenerateUiState>(GenerateUiState.Idle)
    val generateUiState: StateFlow<GenerateUiState> = _generateUiState.asStateFlow()

    // App UI Language State
    private val _uiLanguage = MutableStateFlow("English")
    val uiLanguage: StateFlow<String> = _uiLanguage.asStateFlow()

    fun setUiLanguage(language: String) {
        _uiLanguage.value = language
    }

    // Currently selected script for detail view
    private val _selectedScript = MutableStateFlow<VideoScript?>(null)
    val selectedScript: StateFlow<VideoScript?> = _selectedScript.asStateFlow()

    fun resetGenerateState() {
        _generateUiState.value = GenerateUiState.Idle
    }

    fun selectScript(script: VideoScript?) {
        _selectedScript.value = script
    }

    fun generateScript(
        topic: String,
        duration: String,
        tone: String,
        language: String,
        platform: String,
        bypassCache: Boolean = false
    ) {
        if (topic.isBlank()) {
            _generateUiState.value = GenerateUiState.Error("Please enter a topic description.")
            return
        }

        viewModelScope.launch {
            _generateUiState.value = GenerateUiState.Loading
            
            repository.generateVideoScript(topic, duration, tone, language, platform, bypassCache)
                .onSuccess { result ->
                    val newScript = VideoScript(
                        topic = topic,
                        duration = duration,
                        tone = tone,
                        language = language,
                        platform = platform,
                        title = result.title,
                        fullScript = result.fullScript
                    )
                    
                    // Insert into Room history database
                    val id = repository.insertScript(newScript)
                    val savedScript = newScript.copy(id = id.toInt())
                    
                    _selectedScript.value = savedScript
                    _generateUiState.value = GenerateUiState.Success(savedScript)
                }
                .onFailure { exception ->
                    _generateUiState.value = GenerateUiState.Error(
                        exception.message ?: "Failed to generate script. Please try again."
                    )
                }
        }
    }

    fun deleteScript(script: VideoScript) {
        viewModelScope.launch {
            if (_selectedScript.value?.id == script.id) {
                _selectedScript.value = null
            }
            repository.deleteScript(script)
        }
    }

    fun toggleFavorite(script: VideoScript) {
        viewModelScope.launch {
            val updatedFavorite = !script.isFavorite
            repository.updateFavoriteStatus(script.id, updatedFavorite)
            if (_selectedScript.value?.id == script.id) {
                _selectedScript.value = _selectedScript.value?.copy(isFavorite = updatedFavorite)
            }
        }
    }
    
    fun updateScriptContent(scriptId: Int, newTitle: String, newContent: String) {
        viewModelScope.launch {
            _selectedScript.value?.let { current ->
                if (current.id == scriptId) {
                    val updated = current.copy(title = newTitle, fullScript = newContent)
                    repository.insertScript(updated) // REPLACE inserts or updates
                    _selectedScript.value = updated
                }
            }
        }
    }
}

class ScriptViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ScriptViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ScriptViewModel(application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
