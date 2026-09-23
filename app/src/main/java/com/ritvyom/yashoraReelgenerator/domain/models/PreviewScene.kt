package com.ritvyom.yashoraReelgenerator.domain.models

import java.io.Serializable

data class PreviewScene(
    val sceneNumber: Int,
    val narrationText: String,
    val visualPrompt: String,
    val durationSeconds: Int = 5,
    val unsplashUrl: String,
    val pexelsUrl: String,
    val pixabayUrl: String,
    var selectedUrl: String
) : Serializable
