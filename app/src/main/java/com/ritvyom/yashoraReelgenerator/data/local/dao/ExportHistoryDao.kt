package com.ritvyom.yashoraReelgenerator.data.local.dao

import androidx.room.*
import com.ritvyom.yashoraReelgenerator.data.local.entities.ExportHistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ExportHistoryDao {
    @Query("SELECT * FROM export_history ORDER BY createdAt DESC")
    fun getAllHistoryFlow(): Flow<List<ExportHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(history: ExportHistoryEntity): Long

    @Query("DELETE FROM export_history WHERE id = :id")
    suspend fun deleteHistoryById(id: Int)

    @Query("DELETE FROM export_history")
    suspend fun clearAllHistory()
}
