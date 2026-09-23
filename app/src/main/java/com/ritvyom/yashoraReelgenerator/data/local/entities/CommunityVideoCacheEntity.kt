package com.ritvyom.yashoraReelgenerator.data.local.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "community_video_cache",
    indices = [
        Index(value = ["mediaUrl"], unique = true),
        Index(value = ["projectId"]),
        Index(value = ["lastAccessedAt"])
    ]
)
data class CommunityVideoCacheEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val mediaUrl: String, // Remote media URL (Firebase storage / Pexels / CDN)
    val localFilePath: String, // Local persistent file path on device storage
    val projectId: String = "", // Community shared project ID
    val projectTitle: String = "", // Community project title
    val sceneNumber: Int = 0,
    val narrationText: String = "",
    val visualPrompt: String = "",
    val durationSeconds: Int = 5,
    val mimeType: String = "video/mp4",
    val isVideo: Boolean = true,
    val fileSizeBytes: Long = 0L,
    val aspectRatio: String = "9:16",
    val cachedAt: Long = System.currentTimeMillis(),
    val lastAccessedAt: Long = System.currentTimeMillis()
)
