package com.ritvyom.yashoraReelgenerator.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "video_scripts")
data class VideoScript(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val topic: String,
    val duration: String,
    val tone: String,
    val language: String,
    val platform: String,
    val title: String,
    val fullScript: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isFavorite: Boolean = false
)
