package com.ritvyom.yashoraReelgenerator.core.model

import java.io.Serializable

/**
 * High-precision Color Grading specification.
 * Values are normalized and calibrated for parity across Compose UI, OpenGL ES shaders, and Media3 Effects.
 */
data class ColorGradingConfig(
    val brightness: Float = 0.0f,     // [-1.0f .. 1.0f], default 0.0f
    val contrast: Float = 1.0f,       // [0.0f .. 2.0f], default 1.0f
    val saturation: Float = 1.0f,     // [0.0f .. 2.0f], default 1.0f
    val warmth: Float = 0.0f,         // [-1.0f .. 1.0f], default 0.0f (Cool to Warm)
    val exposure: Float = 0.0f,       // [-1.0f .. 1.0f], default 0.0f
    val tint: Float = 0.0f,           // [-1.0f .. 1.0f], default 0.0f (Green to Magenta)
    val vignette: Float = 0.0f,       // [0.0f .. 1.0f], default 0.0f
    val sharpen: Float = 0.0f,        // [0.0f .. 1.0f], default 0.0f
    val highlights: Float = 0.0f,     // [-1.0f .. 1.0f], default 0.0f
    val shadows: Float = 0.0f,        // [-1.0f .. 1.0f], default 0.0f
    val lutAssetPath: String? = null, // Path to 3D/2D LUT texture if loaded
    val lutIntensity: Float = 1.0f,
    val filterIntensity: Int = 100
) : Serializable {

    val isDefault: Boolean
        get() = brightness == 0f && contrast == 1f && saturation == 1f &&
                warmth == 0f && exposure == 0f && tint == 0f &&
                vignette == 0f && sharpen == 0f && highlights == 0f &&
                shadows == 0f && lutAssetPath == null
}

/**
 * Visual Shader / GPU Effect Specification.
 */
data class VisualEffectSpec(
    val effectId: String,
    val name: String,
    val category: String,
    val intensity: Float = 1.0f,
    val parameter1: Float = 0.0f,
    val parameter2: Float = 0.0f
) : Serializable
