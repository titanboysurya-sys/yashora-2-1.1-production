package com.ritvyom.yashoraReelgenerator.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val scriptText: String,
    val videoStyle: String,
    val voiceName: String,
    val voiceCategory: String, // Male, Female, Child
    val language: String,
    val aspectRatio: String, // "9:16", "16:9", etc.
    val resolution: String, // "1080p", etc.
    val fps: Int, // 30, 60, etc.
    val bgMusicCategory: String, // "Cinematic", etc.
    val bgMusicVolume: Float = 0.5f,
    val voiceVolume: Float = 0.8f,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val status: String = "Draft", // Draft, Completed
    val subtitleStyle: String = "TikTok Style",
    val scenesJson: String = "",
    val topicContext: String = "",
    val publishingStyle: String = "TikTok / Instagram Reels"
)
