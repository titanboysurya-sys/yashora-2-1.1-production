package com.ritvyom.yashoraReelgenerator.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.ritvyom.yashoraReelgenerator.data.local.entities.GeneratedAudioEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for generated audio metadata table.
 * Supports reactive queries (Flow) and suspend operations for Room database.
 */
@Dao
interface GeneratedAudioDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAudio(audio: GeneratedAudioEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(audios: List<GeneratedAudioEntity>): List<Long>

    @Update
    suspend fun updateAudio(audio: GeneratedAudioEntity): Int

    @Delete
    suspend fun deleteAudio(audio: GeneratedAudioEntity): Int

    @Query("SELECT * FROM generated_audio_metadata ORDER BY timestamp DESC")
    fun getAllGeneratedAudios(): Flow<List<GeneratedAudioEntity>>

    @Query("SELECT * FROM generated_audio_metadata WHERE id = :id LIMIT 1")
    suspend fun getAudioById(id: Long): GeneratedAudioEntity?

    @Query("SELECT * FROM generated_audio_metadata WHERE selectedVoiceId = :voiceId ORDER BY timestamp DESC")
    fun getAudiosByVoiceId(voiceId: String): Flow<List<GeneratedAudioEntity>>

    @Query("SELECT * FROM generated_audio_metadata WHERE selectedVoiceId = :voiceId AND textPrompt = :textPrompt ORDER BY timestamp DESC LIMIT 1")
    suspend fun getAudioByVoiceAndPrompt(voiceId: String, textPrompt: String): GeneratedAudioEntity?

    @Query("SELECT * FROM generated_audio_metadata WHERE localFilePath = :filePath LIMIT 1")
    suspend fun getAudioByFilePath(filePath: String): GeneratedAudioEntity?

    @Query("SELECT * FROM generated_audio_metadata ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentAudios(limit: Int = 20): Flow<List<GeneratedAudioEntity>>

    @Query("SELECT * FROM generated_audio_metadata WHERE textPrompt LIKE '%' || :query || '%' ORDER BY timestamp DESC")
    fun searchByPrompt(query: String): Flow<List<GeneratedAudioEntity>>

    @Query("DELETE FROM generated_audio_metadata WHERE id = :id")
    suspend fun deleteAudioById(id: Long): Int

    @Query("DELETE FROM generated_audio_metadata WHERE localFilePath = :filePath")
    suspend fun deleteAudioByFilePath(filePath: String): Int

    @Query("DELETE FROM generated_audio_metadata")
    suspend fun clearAllAudios(): Int
}
