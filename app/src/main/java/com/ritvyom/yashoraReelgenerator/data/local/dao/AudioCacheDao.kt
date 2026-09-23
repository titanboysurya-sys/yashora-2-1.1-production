package com.ritvyom.yashoraReelgenerator.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ritvyom.yashoraReelgenerator.data.local.entities.AudioCache

@Dao
interface AudioCacheDao {
    @Query("SELECT * FROM audio_cache WHERE voiceId = :voiceId AND textHash = :textHash LIMIT 1")
    suspend fun getAudioCache(voiceId: String, textHash: String): AudioCache?

    @Query("SELECT * FROM audio_cache WHERE textHash = :textHash LIMIT 1")
    suspend fun getAudioCacheByHash(textHash: String): AudioCache?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAudioCache(audioCache: AudioCache): Long

    @Query("DELETE FROM audio_cache WHERE textHash = :textHash")
    suspend fun deleteAudioCacheByHash(textHash: String)

    @Query("DELETE FROM audio_cache WHERE timestamp < :expirationTimestamp")
    suspend fun deleteExpiredAudioCache(expirationTimestamp: Long): Int
}
