package com.ritvyom.yashoraReelgenerator.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tts_history")
data class TtsHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val text: String,
    val voiceId: String,
    val voiceName: String,
    val modelId: String,
    val stability: Double,
    val similarityBoost: Double,
    val filePath: String,
    val timestamp: Long = System.currentTimeMillis()
)
