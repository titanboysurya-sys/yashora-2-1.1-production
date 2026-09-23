package com.ritvyom.yashoraReelgenerator.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cached_images")
data class CachedImageEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val keyword: String,
    val imageUrl: String,
    val createdAt: Long = System.currentTimeMillis()
)
