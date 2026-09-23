package com.ritvyom.yashoraReelgenerator.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TtsHistoryDao {

    @Query("SELECT * FROM tts_history ORDER BY timestamp DESC")
    fun getAllHistory(): Flow<List<TtsHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(entity: TtsHistoryEntity): Long

    @Query("DELETE FROM tts_history WHERE id = :id")
    suspend fun deleteHistoryById(id: Int)

    @Query("DELETE FROM tts_history")
    suspend fun clearAllHistory()
}
