package com.ritvyom.yashoraReelgenerator.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "export_history")
data class ExportHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val projectId: Int,
    val projectTitle: String,
    val fileName: String,
    val filePath: String,
    val fileSize: Long,
    val resolution: String,
    val durationMs: Long,
    val createdAt: Long = System.currentTimeMillis()
)
