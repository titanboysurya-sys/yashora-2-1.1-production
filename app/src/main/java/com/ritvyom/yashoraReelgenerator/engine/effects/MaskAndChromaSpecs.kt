package com.ritvyom.yashoraReelgenerator.engine.effects

import android.graphics.Color
import java.io.Serializable

/**
 * Geometric shape for mask attenuation.
 */
enum class MaskShape {
    RECTANGLE,
    ELLIPSE,
    LINEAR_SPLIT
}

/**
 * Canonical Mask Specification.
 * Evaluated on the GPU to generate shape masks with adjustable feather and inversion.
 */
data class MaskSpec(
    val isEnabled: Boolean = false,
    val shape: MaskShape = MaskShape.RECTANGLE,
    val centerX: Float = 0.5f,
    val centerY: Float = 0.5f,
    val width: Float = 0.6f,
    val height: Float = 0.6f,
    val rotationDegrees: Float = 0f,
    val feather: Float = 0.05f,
    val isInverted: Boolean = false
) : Serializable

/**
 * Canonical Chroma Key Specification.
 * Enables green-screen / blue-screen removal with adjustable key color, sensitivity, softness, and spill suppression.
 */
data class ChromaKeySpec(
    val isEnabled: Boolean = false,
    val keyColorR: Float = 0.0f,
    val keyColorG: Float = 1.0f,
    val keyColorB: Float = 0.0f,
    val sensitivity: Float = 0.45f,
    val softness: Float = 0.15f,
    val spillSuppression: Float = 0.5f
) : Serializable {
    companion object {
        fun fromColorString(colorName: String, sensitivity: Float = 0.45f, softness: Float = 0.15f): ChromaKeySpec {
            return when (colorName.lowercase()) {
                "blue" -> ChromaKeySpec(true, 0.0f, 0.2f, 1.0f, sensitivity, softness, 0.5f)
                "red" -> ChromaKeySpec(true, 1.0f, 0.0f, 0.0f, sensitivity, softness, 0.5f)
                else -> ChromaKeySpec(true, 0.0f, 1.0f, 0.0f, sensitivity, softness, 0.5f) // Default Green
            }
        }
    }
}
