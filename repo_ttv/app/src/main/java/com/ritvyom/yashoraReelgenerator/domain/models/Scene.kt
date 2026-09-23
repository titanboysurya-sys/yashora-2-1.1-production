package com.ritvyom.yashoraReelgenerator.domain.models

import java.io.Serializable

data class Scene(
    val sceneNumber: Int,
    val narrationText: String,
    val visualPrompt: String,
    val durationSeconds: Int = 5,
    val subtitle: String,
    var keywords: List<String> = emptyList(),
    // Dynamic editing configurations post-generation:
    var mediaType: String = "IMAGE", // IMAGE, VIDEO
    var mediaPath: String? = null, // Path to visual asset
    var remoteUrl: String? = null, // Original remote URL from API (to preserve cloud mapping for sharing/exporting)
    var textOverlay: String? = null,
    var overlayColor: String = "#FFFFFF",
    var selectedFilterIndex: Int = 0,
    var selectedFilterName: String = "Normal",
    var transitionType: String = "Fade", // None, Fade, Zoom, Slide, Blur, Cinematic
    var durationMs: Long = 5000L,
    var captionFont: String = "TikTok Style",
    var subtitleColor: String = "#FFFFFF",
    var subtitleBgColor: String = "#99000000",
    var subtitleDesign: String = "Classic Box",
    var speedMultiplier: Float = 1.0f,
    var volume: Float = 1.0f,
    var rotationDegrees: Int = 0,
    var isFlippedHorizontal: Boolean = false,
    var isFlippedVertical: Boolean = false,
    var brightnessValue: Float = 0f,
    var contrastValue: Float = 1.0f,
    var saturationValue: Float = 1.0f,
    var warmthValue: Float = 0f,
    var maskShape: String = "None",
    var isChromaKeyEnabled: Boolean = false,
    var chromaKeyColor: String = "Green",
    var chromaKeySensitivity: Float = 0.5f,
    var textAnimation: String = "None",
    var filterCategory: String = "Normal",
    var filterIntensity: Int = 80,
    var effectName: String = "None",
    var effectCategory: String = "None",
    var effectIntensity: Int = 100,
    var sfxName: String = "None",
    var sfxCategory: String = "None",
    var stickerName: String = "None",
    var stickerX: Float = 0.5f,
    var stickerY: Float = 0.5f,
    var stickerScale: Float = 1.0f,
    var rainDropCount: Int = 50,
    var rainSpeed: Float = 1.0f,
    var rainWindDirection: Float = -0.2f,
    var snowFlakeCount: Int = 40,
    var snowSpeed: Float = 1.0f,
    var snowWindDirection: Float = 0.1f,
    var layeredSfxName: String = "None",
    var layeredSfxVolume: Float = 0.5f,
    var layeredSfxLoop: Boolean = false
) : Serializable
