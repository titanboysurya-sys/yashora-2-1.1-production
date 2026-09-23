package com.ritvyom.yashoraReelgenerator.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "audio_cache")
data class AudioCache(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val voiceId: String,
    val textHash: String,
    val fileUri: String,
    val textPrompt: String = "",
    val localFilePath: String = fileUri,
    val timestamp: Long = System.currentTimeMillis()
)
