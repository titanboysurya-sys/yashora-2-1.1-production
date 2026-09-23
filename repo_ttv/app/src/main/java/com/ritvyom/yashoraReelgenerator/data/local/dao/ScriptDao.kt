package com.ritvyom.yashoraReelgenerator.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.ritvyom.yashoraReelgenerator.data.model.VideoScript
import kotlinx.coroutines.flow.Flow

@Dao
interface ScriptDao {
    @Query("SELECT * FROM video_scripts ORDER BY timestamp DESC")
    fun getAllScripts(): Flow<List<VideoScript>>

    @Query("SELECT * FROM video_scripts WHERE id = :id LIMIT 1")
    fun getScriptById(id: Int): Flow<VideoScript?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertScript(script: VideoScript): Long

    @Query("SELECT * FROM video_scripts WHERE LOWER(topic) = LOWER(:topic) AND LOWER(tone) = LOWER(:tone) AND LOWER(language) = LOWER(:language) AND LOWER(duration) = LOWER(:duration) LIMIT 1")
    suspend fun getScriptByParams(topic: String, tone: String, language: String, duration: String): VideoScript?

    @Query("SELECT * FROM video_scripts WHERE LOWER(topic) = LOWER(:topic) AND LOWER(language) = LOWER(:language) LIMIT 1")
    suspend fun getScriptByTopicAndLanguage(topic: String, language: String): VideoScript?

    @Query("SELECT * FROM video_scripts WHERE LOWER(topic) = LOWER(:topic) LIMIT 1")
    suspend fun getScriptByTopic(topic: String): VideoScript?

    @Update
    suspend fun updateScript(script: VideoScript)

    @Query("UPDATE video_scripts SET isFavorite = :isFavorite WHERE id = :id")
    suspend fun updateFavoriteStatus(id: Int, isFavorite: Boolean)

    @Delete
    suspend fun deleteScript(script: VideoScript)

    @Query("DELETE FROM video_scripts WHERE id = :id")
    suspend fun deleteScriptById(id: Int)
}
