package com.ritvyom.yashoraReelgenerator.data.local.dao

import androidx.room.*
import com.ritvyom.yashoraReelgenerator.data.local.entities.CachedImageEntity

@Dao
interface CachedImageDao {
    @Query("SELECT * FROM cached_images WHERE keyword = :keyword LIMIT 1")
    suspend fun getCachedImageByKeyword(keyword: String): CachedImageEntity?

    @Query("DELETE FROM cached_images WHERE keyword = :keyword")
    suspend fun deleteCachedImageByKeyword(keyword: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCachedImage(cachedImage: CachedImageEntity): Long

    @Query("DELETE FROM cached_images WHERE timestamp < :expirationTimestamp")
    suspend fun deleteExpiredAssets(expirationTimestamp: Long): Int
}
