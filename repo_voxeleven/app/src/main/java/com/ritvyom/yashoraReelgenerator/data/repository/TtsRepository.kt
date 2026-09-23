package com.ritvyom.yashoraReelgenerator.data.repository

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.ritvyom.yashoraReelgenerator.data.api.RetrofitClient
import com.ritvyom.yashoraReelgenerator.data.db.TtsHistoryDao
import com.ritvyom.yashoraReelgenerator.data.db.TtsHistoryEntity
import com.ritvyom.yashoraReelgenerator.data.model.TtsRequest
import com.ritvyom.yashoraReelgenerator.data.model.UserResponse
import com.ritvyom.yashoraReelgenerator.data.model.Voice
import com.ritvyom.yashoraReelgenerator.data.model.VoiceSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class TtsRepository(
    private val context: Context,
    private val dao: TtsHistoryDao
) {
    private val securePreferences: SharedPreferences by lazy {
        try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            EncryptedSharedPreferences.create(
                context,
                "voxeleven_secure_prefs",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            context.getSharedPreferences("voxeleven_prefs_fallback", Context.MODE_PRIVATE)
        }
    }

    companion object {
        private const val KEY_API_KEY = "elevenlabs_api_key"
        private const val KEY_DARK_MODE = "is_dark_mode"
        private const val KEY_DRAFT_TEXT = "draft_text"
        private const val KEY_DRAFT_VOICE_ID = "draft_voice_id"
        private const val KEY_DRAFT_MODEL_ID = "draft_model_id"
        private const val KEY_DRAFT_STABILITY = "draft_stability"
        private const val KEY_DRAFT_SIMILARITY = "draft_similarity"
    }

    val allHistory: Flow<List<TtsHistoryEntity>> = dao.getAllHistory()

    fun getSavedApiKey(): String? {
        return securePreferences.getString(KEY_API_KEY, null)
    }

    fun saveApiKey(apiKey: String) {
        securePreferences.edit().putString(KEY_API_KEY, apiKey).apply()
    }

    fun clearApiKey() {
        securePreferences.edit().remove(KEY_API_KEY).apply()
    }

    // --- Dark Mode Preference ---
    fun getDarkModePreference(): Boolean? {
        return if (securePreferences.contains(KEY_DARK_MODE)) {
            securePreferences.getBoolean(KEY_DARK_MODE, false)
        } else {
            null
        }
    }

    fun saveDarkModePreference(isDark: Boolean) {
        securePreferences.edit().putBoolean(KEY_DARK_MODE, isDark).apply()
    }

    // --- Auto-Save Draft Preferences ---
    fun getDraftText(): String? = securePreferences.getString(KEY_DRAFT_TEXT, null)
    fun saveDraftText(text: String) = securePreferences.edit().putString(KEY_DRAFT_TEXT, text).apply()

    fun getDraftVoiceId(): String? = securePreferences.getString(KEY_DRAFT_VOICE_ID, null)
    fun saveDraftVoiceId(voiceId: String) = securePreferences.edit().putString(KEY_DRAFT_VOICE_ID, voiceId).apply()

    fun getDraftModelId(): String? = securePreferences.getString(KEY_DRAFT_MODEL_ID, null)
    fun saveDraftModelId(modelId: String) = securePreferences.edit().putString(KEY_DRAFT_MODEL_ID, modelId).apply()

    fun getDraftStability(): Float? {
        return if (securePreferences.contains(KEY_DRAFT_STABILITY)) {
            securePreferences.getFloat(KEY_DRAFT_STABILITY, 0.5f)
        } else null
    }
    fun saveDraftStability(stability: Float) = securePreferences.edit().putFloat(KEY_DRAFT_STABILITY, stability).apply()

    fun getDraftSimilarity(): Float? {
        return if (securePreferences.contains(KEY_DRAFT_SIMILARITY)) {
            securePreferences.getFloat(KEY_DRAFT_SIMILARITY, 0.75f)
        } else null
    }
    fun saveDraftSimilarity(similarity: Float) = securePreferences.edit().putFloat(KEY_DRAFT_SIMILARITY, similarity).apply()

    suspend fun validateApiKey(apiKey: String): UserResponse = withContext(Dispatchers.IO) {
        RetrofitClient.apiService.getUserInfo(apiKey)
    }

    suspend fun fetchVoices(apiKey: String): List<Voice> = withContext(Dispatchers.IO) {
        val response = RetrofitClient.apiService.getVoices(apiKey)
        response.voices
    }

    /**
     * Generates Speech and saves it to a local file.
     * Returns the File object on success, or throws an exception.
     */
    suspend fun generateSpeech(
        apiKey: String,
        voiceId: String,
        text: String,
        modelId: String,
        stability: Double,
        similarityBoost: Double
    ): File = withContext(Dispatchers.IO) {
        val request = TtsRequest(
            text = text,
            modelId = modelId,
            voiceSettings = VoiceSettings(
                stability = stability,
                similarityBoost = similarityBoost
            )
        )

        val responseBody = RetrofitClient.apiService.textToSpeech(
            voiceId = voiceId,
            apiKey = apiKey,
            request = request
        )

        // Create directory for TTS audios if it doesn't exist
        val dir = File(context.filesDir, "tts_audios")
        if (!dir.exists()) {
            dir.mkdirs()
        }

        val file = File(dir, "tts_${System.currentTimeMillis()}.mp3")
        responseBody.byteStream().use { inputStream ->
            FileOutputStream(file).use { outputStream ->
                inputStream.copyTo(outputStream)
            }
        }

        file
    }

    suspend fun insertHistoryItem(entity: TtsHistoryEntity) = withContext(Dispatchers.IO) {
        dao.insertHistory(entity)
    }

    suspend fun deleteHistoryItem(entity: TtsHistoryEntity) = withContext(Dispatchers.IO) {
        // First delete the physical file
        val file = File(entity.filePath)
        if (file.exists()) {
            file.delete()
        }
        dao.deleteHistoryById(entity.id)
    }

    suspend fun clearHistory() = withContext(Dispatchers.IO) {
        // Delete all cached files
        val dir = File(context.filesDir, "tts_audios")
        if (dir.exists()) {
            dir.deleteRecursively()
        }
        dao.clearAllHistory()
    }
}
