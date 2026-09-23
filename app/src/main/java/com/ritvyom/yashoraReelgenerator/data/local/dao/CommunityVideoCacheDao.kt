package com.ritvyom.yashoraReelgenerator.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.ritvyom.yashoraReelgenerator.data.local.entities.CommunityVideoCacheEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CommunityVideoCacheDao {

    @Query("SELECT * FROM community_video_cache WHERE mediaUrl = :url LIMIT 1")
    suspend fun getByMediaUrl(url: String): CommunityVideoCacheEntity?

    @Query("SELECT * FROM community_video_cache WHERE localFilePath = :localPath LIMIT 1")
    suspend fun getByLocalPath(localPath: String): CommunityVideoCacheEntity?

    @Query("SELECT * FROM community_video_cache WHERE projectId = :projectId ORDER BY sceneNumber ASC")
    fun getVideosForProjectFlow(projectId: String): Flow<List<CommunityVideoCacheEntity>>

    @Query("SELECT * FROM community_video_cache WHERE projectId = :projectId ORDER BY sceneNumber ASC")
    suspend fun getVideosForProject(projectId: String): List<CommunityVideoCacheEntity>

    @Query("SELECT * FROM community_video_cache ORDER BY lastAccessedAt DESC")
    fun getAllCachedVideosFlow(): Flow<List<CommunityVideoCacheEntity>>

    @Query("SELECT * FROM community_video_cache ORDER BY lastAccessedAt DESC")
    suspend fun getAllCachedVideos(): List<CommunityVideoCacheEntity>

    @Query("SELECT COUNT(*) FROM community_video_cache")
    fun getCachedCountFlow(): Flow<Int>

    @Query("SELECT SUM(fileSizeBytes) FROM community_video_cache")
    fun getTotalCacheSizeBytesFlow(): Flow<Long?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: CommunityVideoCacheEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<CommunityVideoCacheEntity>)

    @Update
    suspend fun update(entity: CommunityVideoCacheEntity)

    @Query("UPDATE community_video_cache SET lastAccessedAt = :timestamp WHERE mediaUrl = :url")
    suspend fun updateLastAccessed(url: String, timestamp: Long = System.currentTimeMillis())

    @Query("DELETE FROM community_video_cache WHERE mediaUrl = :url")
    suspend fun deleteByMediaUrl(url: String): Int

    @Query("DELETE FROM community_video_cache WHERE projectId = :projectId")
    suspend fun deleteByProjectId(projectId: String): Int

    @Query("DELETE FROM community_video_cache WHERE lastAccessedAt < :olderThanTimestamp")
    suspend fun deleteOlderThan(olderThanTimestamp: Long): Int

    @Query("DELETE FROM community_video_cache")
    suspend fun clearAll()
}
