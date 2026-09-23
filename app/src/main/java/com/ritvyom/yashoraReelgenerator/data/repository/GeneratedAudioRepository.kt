package com.ritvyom.yashoraReelgenerator.data.repository

import com.ritvyom.yashoraReelgenerator.data.local.dao.GeneratedAudioDao
import com.ritvyom.yashoraReelgenerator.data.local.entities.GeneratedAudioEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Repository adhering to Clean Architecture / Repository pattern for generated audio persistence.
 * Abstracts DAO operations and manages audio file metadata queries.
 */
class GeneratedAudioRepository(
    private val generatedAudioDao: GeneratedAudioDao
) {
    /**
     * Flow of all generated audio records ordered by newest first.
     */
    val allGeneratedAudios: Flow<List<GeneratedAudioEntity>> =
        generatedAudioDao.getAllGeneratedAudios().flowOn(Dispatchers.IO)

    /**
     * Flow of recent audio records with limit.
     */
    fun getRecentAudios(limit: Int = 20): Flow<List<GeneratedAudioEntity>> =
        generatedAudioDao.getRecentAudios(limit).flowOn(Dispatchers.IO)

    /**
     * Flow of generated audios for a specific voice ID.
     */
    fun getAudiosByVoiceId(voiceId: String): Flow<List<GeneratedAudioEntity>> =
        generatedAudioDao.getAudiosByVoiceId(voiceId).flowOn(Dispatchers.IO)

    /**
     * Search audio files by prompt snippet.
     */
    fun searchByPrompt(query: String): Flow<List<GeneratedAudioEntity>> =
        generatedAudioDao.searchByPrompt(query).flowOn(Dispatchers.IO)

    /**
     * Save generated audio metadata to Room database.
     */
    suspend fun saveAudioMetadata(
        textPrompt: String,
        selectedVoiceId: String,
        localFilePath: String,
        durationMs: Long = 0L,
        fileSizeBytes: Long = 0L,
        audioFormat: String = "audio/mpeg",
        provider: String = "VoxEleven"
    ): Long = withContext(Dispatchers.IO) {
        val calculatedSize = if (fileSizeBytes > 0L) {
            fileSizeBytes
        } else {
            val file = File(localFilePath)
            if (file.exists()) file.length() else 0L
        }

        val entity = GeneratedAudioEntity(
            textPrompt = textPrompt,
            selectedVoiceId = selectedVoiceId,
            localFilePath = localFilePath,
            durationMs = durationMs,
            fileSizeBytes = calculatedSize,
            audioFormat = audioFormat,
            provider = provider,
            timestamp = System.currentTimeMillis()
        )
        generatedAudioDao.insertAudio(entity)
    }

    /**
     * Insert a complete GeneratedAudioEntity.
     */
    suspend fun insert(audio: GeneratedAudioEntity): Long = withContext(Dispatchers.IO) {
        generatedAudioDao.insertAudio(audio)
    }

    /**
     * Find single audio matching voice ID and prompt.
     */
    suspend fun findAudioByVoiceAndPrompt(voiceId: String, prompt: String): GeneratedAudioEntity? = withContext(Dispatchers.IO) {
        generatedAudioDao.getAudioByVoiceAndPrompt(voiceId, prompt)
    }

    /**
     * Find audio by local file path.
     */
    suspend fun findAudioByFilePath(filePath: String): GeneratedAudioEntity? = withContext(Dispatchers.IO) {
        generatedAudioDao.getAudioByFilePath(filePath)
    }

    /**
     * Find audio by ID.
     */
    suspend fun getAudioById(id: Long): GeneratedAudioEntity? = withContext(Dispatchers.IO) {
        generatedAudioDao.getAudioById(id)
    }

    /**
     * Delete audio by ID.
     */
    suspend fun deleteAudioById(id: Long, deletePhysicalFile: Boolean = false): Int = withContext(Dispatchers.IO) {
        if (deletePhysicalFile) {
            val audio = generatedAudioDao.getAudioById(id)
            if (audio != null) {
                try {
                    val file = File(audio.localFilePath)
                    if (file.exists()) file.delete()
                } catch (_: Exception) {}
            }
        }
        generatedAudioDao.deleteAudioById(id)
    }

    /**
     * Delete audio entity record.
     */
    suspend fun deleteAudio(audio: GeneratedAudioEntity, deletePhysicalFile: Boolean = false): Int = withContext(Dispatchers.IO) {
        if (deletePhysicalFile) {
            try {
                val file = File(audio.localFilePath)
                if (file.exists()) file.delete()
            } catch (_: Exception) {}
        }
        generatedAudioDao.deleteAudio(audio)
    }

    /**
     * Clear all records.
     */
    suspend fun clearAll(): Int = withContext(Dispatchers.IO) {
        generatedAudioDao.clearAllAudios()
    }
}
