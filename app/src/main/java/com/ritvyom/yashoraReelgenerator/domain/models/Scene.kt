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
    var layeredSfxLoop: Boolean = false,
    var customVoiceAudioPath: String? = null,
    var pipMediaPath: String? = null,
    var pipScale: Float = 0.35f,
    var pipX: Float = 0.75f,
    var pipY: Float = 0.25f,
    var transitionDurationMs: Long = 700L,
    var textOverlayFontSize: Float = 24f,
    var textOverlayDurationSeconds: Float = 3f,
    var textOverlayStartTimeSeconds: Float = 0f,
    var textOverlayX: Float = 0.5f,
    var textOverlayY: Float = 0.25f,
    var textOverlayScale: Float = 1.0f,
    var textOverlayRotation: Float = 0f,
    var stickerRotation: Float = 0f,
    var layersJson: String = "",
    // CapCut & Filmora Pro Grading & Motion Extensions:
    var vignetteValue: Float = 0f,
    var exposureValue: Float = 0f,
    var sharpenValue: Float = 0f,
    var tintValue: Float = 0f,
    var highlightValue: Float = 0f,
    var shadowValue: Float = 0f,
    var inAnimation: String = "None",
    var outAnimation: String = "None",
    var comboAnimation: String = "None",
    var animationDuration: Float = 0.5f,
    var voiceEffect: String = "None",
    var audioFadeInDuration: Float = 0.0f,
    var audioFadeOutDuration: Float = 0.0f,
    var isReversed: Boolean = false,
    var isFrozen: Boolean = false,
    var freezeDurationSeconds: Float = 2.0f,
    var mediaSourceUsed: String? = null, // e.g. "Pexels", "Pixabay", "Unsplash", "AI Generated", "Wikipedia", "Local Fallback"
    var mediaFetchStatus: String = "SUCCESS", // "SUCCESS", "FAILED", "FETCHING"
    var mediaFetchError: String? = null
) : Serializable

/**
 * Represents an individual visual overlay layer (Text or Sticker) with position, scale,
 * rotation, and explicit depth in the scene preview and export pipeline.
 */
data class CanvasLayer(
    val id: String = java.util.UUID.randomUUID().toString(),
    val type: String, // "TEXT" or "STICKER"
    var content: String,
    var color: String = "#FFFFFF",
    var font: String = "TikTok Style",
    var animation: String = "Pop",
    var fontSize: Float = 24f,
    var x: Float = 0.5f,
    var y: Float = 0.5f,
    var scale: Float = 1.0f,
    var rotation: Float = 0f,
    var isVisible: Boolean = true
) : Serializable

fun Scene.getCanvasLayers(): List<CanvasLayer> {
    if (layersJson.isNotEmpty()) {
        try {
            val jsonArray = org.json.JSONArray(layersJson)
            val result = mutableListOf<CanvasLayer>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                result.add(
                    CanvasLayer(
                        id = obj.optString("id", java.util.UUID.randomUUID().toString()),
                        type = obj.optString("type", "TEXT"),
                        content = obj.optString("content", ""),
                        color = obj.optString("color", "#FFFFFF"),
                        font = obj.optString("font", "TikTok Style"),
                        animation = obj.optString("animation", "Pop"),
                        fontSize = obj.optDouble("fontSize", 24.0).toFloat(),
                        x = obj.optDouble("x", 0.5).toFloat(),
                        y = obj.optDouble("y", 0.5).toFloat(),
                        scale = obj.optDouble("scale", 1.0).toFloat(),
                        rotation = obj.optDouble("rotation", 0.0).toFloat(),
                        isVisible = obj.optBoolean("isVisible", true)
                    )
                )
            }
            if (result.isNotEmpty()) return result
        } catch (e: Exception) {
            // fallback to synthesis
        }
    }

    val defaultLayers = mutableListOf<CanvasLayer>()
    textOverlay?.takeIf { it.isNotEmpty() }?.let { text ->
        defaultLayers.add(
            CanvasLayer(
                id = "default_text",
                type = "TEXT",
                content = text,
                color = overlayColor.ifEmpty { "#FFFFFF" },
                font = captionFont.ifEmpty { "TikTok Style" },
                animation = textAnimation.ifEmpty { "Pop" },
                fontSize = textOverlayFontSize,
                x = textOverlayX,
                y = textOverlayY,
                scale = textOverlayScale,
                rotation = textOverlayRotation,
                isVisible = true
            )
        )
    }
    stickerName.takeIf { it.isNotEmpty() && it != "None" }?.let { stk ->
        defaultLayers.add(
            CanvasLayer(
                id = "default_sticker",
                type = "STICKER",
                content = stk,
                scale = stickerScale,
                rotation = stickerRotation,
                x = stickerX,
                y = stickerY,
                isVisible = true
            )
        )
    }
    return defaultLayers
}

fun Scene.saveCanvasLayers(layers: List<CanvasLayer>) {
    val jsonArray = org.json.JSONArray()
    for (layer in layers) {
        val obj = org.json.JSONObject().apply {
            put("id", layer.id)
            put("type", layer.type)
            put("content", layer.content)
            put("color", layer.color)
            put("font", layer.font)
            put("animation", layer.animation)
            put("fontSize", layer.fontSize.toDouble())
            put("x", layer.x.toDouble())
            put("y", layer.y.toDouble())
            put("scale", layer.scale.toDouble())
            put("rotation", layer.rotation.toDouble())
            put("isVisible", layer.isVisible)
        }
        jsonArray.put(obj)
    }
    layersJson = jsonArray.toString()

    val foremostText = layers.lastOrNull { it.type == "TEXT" && it.isVisible }
        ?: layers.lastOrNull { it.type == "TEXT" }
    if (foremostText != null) {
        textOverlay = foremostText.content
        overlayColor = foremostText.color
        captionFont = foremostText.font
        textAnimation = foremostText.animation
        textOverlayFontSize = foremostText.fontSize
        textOverlayX = foremostText.x
        textOverlayY = foremostText.y
        textOverlayScale = foremostText.scale
        textOverlayRotation = foremostText.rotation
    } else {
        textOverlay = null
    }

    val foremostSticker = layers.lastOrNull { it.type == "STICKER" && it.isVisible }
        ?: layers.lastOrNull { it.type == "STICKER" }
    if (foremostSticker != null) {
        stickerName = foremostSticker.content
        stickerX = foremostSticker.x
        stickerY = foremostSticker.y
        stickerScale = foremostSticker.scale
        stickerRotation = foremostSticker.rotation
    } else {
        stickerName = "None"
    }
}
