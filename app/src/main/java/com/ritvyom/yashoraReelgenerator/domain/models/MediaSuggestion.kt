package com.ritvyom.yashoraReelgenerator.domain.models

import java.io.Serializable

data class MediaSuggestion(
    val url: String,
    val thumbnailUrl: String = "",
    val title: String = "",
    val source: String = "Pexels",
    val mediaType: String = "VIDEO",
    val durationSeconds: Int = 0
) : Serializable
