package com.ritvyom.yashoraReelgenerator.data.local.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room Entity to store metadata for generated audio files.
 * Stores the text prompt, selected voice ID, local file storage path,
 * file size, duration, format, provider and generation timestamp.
 */
@Entity(
    tableName = "generated_audio_metadata",
    indices = [
        Index(value = ["selectedVoiceId"]),
        Index(value = ["timestamp"]),
        Index(value = ["localFilePath"])
    ]
)
data class GeneratedAudioEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val textPrompt: String,
    val selectedVoiceId: String,
    val localFilePath: String,
    val durationMs: Long = 0L,
    val fileSizeBytes: Long = 0L,
    val audioFormat: String = "audio/mpeg",
    val provider: String = "VoxEleven",
    val timestamp: Long = System.currentTimeMillis()
)
