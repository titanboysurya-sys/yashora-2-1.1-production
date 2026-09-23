package com.ritvyom.yashoraReelgenerator.core.model

import java.io.Serializable

/**
 * Standard blend modes for layer compositing.
 */
enum class LayerBlendMode {
    NORMAL,
    SCREEN,
    MULTIPLY,
    OVERLAY,
    DARKEN,
    LIGHTEN,
    COLOR_DODGE,
    SOFT_LIGHT
}

/**
 * Normalized 2D Crop Rect.
 * Coordinates are normalized [0.0f..1.0f] relative to original source bounds.
 */
data class NormalizedCropRect(
    val left: Float = 0.0f,
    val top: Float = 0.0f,
    val right: Float = 1.0f,
    val bottom: Float = 1.0f
) : Serializable {
    val width: Float get() = (right - left).coerceAtLeast(0.01f)
    val height: Float get() = (bottom - top).coerceAtLeast(0.01f)
}

/**
 * Types of geometric clipping masks.
 */
enum class MaskType {
    NONE,
    RECTANGLE,
    ROUNDED_RECTANGLE,
    CIRCLE,
    OVAL,
    LINEAR_SPLIT,
    MIRROR
}

/**
 * Geometric clipping mask definition.
 */
data class MaskDefinition(
    val type: MaskType = MaskType.NONE,
    val centerX: Float = 0.5f,
    val centerY: Float = 0.5f,
    val width: Float = 1.0f,
    val height: Float = 1.0f,
    val cornerRadius: Float = 0.0f,
    val featherPx: Float = 0.0f,
    val rotationDegrees: Float = 0.0f,
    val isInverted: Boolean = false
) : Serializable

/**
 * Canonical 2D Spatial Transform applied uniformly across preview and export.
 */
data class Transform2D(
    val translationX: Float = 0.0f,
    val translationY: Float = 0.0f,
    val scaleX: Float = 1.0f,
    val scaleY: Float = 1.0f,
    val rotationDegrees: Float = 0.0f,
    val anchorX: Float = 0.5f,
    val anchorY: Float = 0.5f,
    val opacity: Float = 1.0f,
    val blendMode: LayerBlendMode = LayerBlendMode.NORMAL,
    val crop: NormalizedCropRect = NormalizedCropRect(),
    val mask: MaskDefinition = MaskDefinition()
) : Serializable
