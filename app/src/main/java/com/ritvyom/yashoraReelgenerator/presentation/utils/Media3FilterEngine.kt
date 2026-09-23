package com.ritvyom.yashoraReelgenerator.presentation.utils

import android.graphics.Bitmap
import androidx.compose.ui.graphics.ColorMatrix
import androidx.media3.common.Effect
import androidx.media3.effect.Brightness
import androidx.media3.effect.Contrast
import androidx.media3.effect.HslAdjustment
import androidx.media3.effect.RgbFilter
import androidx.media3.effect.RgbMatrix

/**
 * High-performance Video Filter Engine leveraging AndroidX Media3 Effect API.
 * Provides preset visual grading filters (Grayscale, Sepia, Vivid, Saturation, etc.)
 * for both live real-time Compose preview and hardware-accelerated Media3 Transformer export pipelines.
 */
object Media3FilterEngine {

    data class FilterPreset(
        val id: String,
        val displayName: String,
        val category: String,
        val description: String
    )

    val PRESETS = listOf(
        FilterPreset("Normal", "Normal", "Original", "Source original color profile"),
        FilterPreset("Grayscale", "Grayscale", "Monochrome", "Clean monochrome B&W using Media3 RgbFilter"),
        FilterPreset("Sepia", "Sepia", "Vintage", "Warm golden nostalgic sepia tone"),
        FilterPreset("Vivid", "Vivid", "Enhance", "High saturation and vibrant color boost"),
        FilterPreset("Saturation", "Saturation", "Enhance", "Deep rich color saturation"),
        FilterPreset("Warm", "Warm Glow", "Cinematic", "Sun-drenched golden hour aesthetic"),
        FilterPreset("Cool", "Cool Breeze", "Cinematic", "Crisp cinematic blue and teal tones"),
        FilterPreset("Cyberpunk", "Cyberpunk", "Stylized", "High contrast neon magenta and cyan"),
        FilterPreset("Vintage", "Vintage 90s", "Retro", "Faded analog film with retro highlights"),
        FilterPreset("B&W Dramatic", "B&W Dramatic", "Monochrome", "High contrast black and white noir"),
        FilterPreset("HDR Pop", "HDR Pop", "Enhance", "Dynamic range boost with micro-contrast")
    )

    /**
     * Builds a list of Media3 Effects for Media3 Transformer / ExoPlayer pipeline.
     */
    fun createMedia3Effects(filterName: String, intensityPercent: Int = 100): List<Effect> {
        if (filterName.equals("Normal", ignoreCase = true) || intensityPercent <= 0) {
            return emptyList()
        }

        val factor = (intensityPercent / 100f).coerceIn(0f, 1f)
        val effects = mutableListOf<Effect>()

        when (filterName.lowercase().trim()) {
            "grayscale", "gray", "b&w" -> {
                effects.add(RgbFilter.createGrayscaleFilter())
            }

            "sepia" -> {
                // Media3 4x4 Sepia RgbMatrix
                val sepiaMatrix = floatArrayOf(
                    0.393f + 0.607f * (1f - factor), 0.349f * factor, 0.272f * factor, 0f,
                    0.769f * factor, 0.686f + 0.314f * (1f - factor), 0.534f * factor, 0f,
                    0.189f * factor, 0.168f * factor, 0.131f + 0.869f * (1f - factor), 0f,
                    0f, 0f, 0f, 1f
                )
                effects.add(RgbMatrix { _, _ -> sepiaMatrix })
            }

            "vivid" -> {
                val satAdjustment = (60f * factor).coerceIn(0f, 100f)
                effects.add(HslAdjustment.Builder().adjustSaturation(satAdjustment).build())
                effects.add(Contrast(0.2f * factor))
            }

            "saturation", "sat" -> {
                val satAdjustment = (80f * factor).coerceIn(-100f, 100f)
                effects.add(HslAdjustment.Builder().adjustSaturation(satAdjustment).build())
            }

            "warm" -> {
                val warmMatrix = floatArrayOf(
                    1f + (0.25f * factor), 0f, 0f, 0f,
                    0f, 1f + (0.1f * factor), 0f, 0f,
                    0f, 0f, 1f - (0.15f * factor), 0f,
                    0f, 0f, 0f, 1f
                )
                effects.add(RgbMatrix { _, _ -> warmMatrix })
                effects.add(Contrast(0.1f * factor))
            }

            "cool" -> {
                val coolMatrix = floatArrayOf(
                    1f - (0.15f * factor), 0f, 0f, 0f,
                    0f, 1f + (0.08f * factor), 0f, 0f,
                    0f, 0f, 1f + (0.25f * factor), 0f,
                    0f, 0f, 0f, 1f
                )
                effects.add(RgbMatrix { _, _ -> coolMatrix })
            }

            "cyberpunk" -> {
                val cyberMatrix = floatArrayOf(
                    1f + (0.3f * factor), 0f, 0f, 0f,
                    0f, 1f - (0.1f * factor), 0f, 0f,
                    0f, 0f, 1f + (0.4f * factor), 0f,
                    0f, 0f, 0f, 1f
                )
                effects.add(RgbMatrix { _, _ -> cyberMatrix })
                effects.add(Contrast(0.35f * factor))
            }

            "vintage" -> {
                val vintageMatrix = floatArrayOf(
                    0.9f * factor + (1f - factor), 0.1f * factor, 0.1f * factor, 0f,
                    0.1f * factor, 0.85f * factor + (1f - factor), 0.05f * factor, 0f,
                    0.05f * factor, 0.05f * factor, 0.7f * factor + (1f - factor), 0f,
                    0f, 0f, 0f, 1f
                )
                effects.add(RgbMatrix { _, _ -> vintageMatrix })
                effects.add(Brightness(0.05f * factor))
                effects.add(Contrast(0.15f * factor))
            }

            "b&w dramatic", "noir" -> {
                effects.add(RgbFilter.createGrayscaleFilter())
                effects.add(Contrast(0.45f * factor))
            }

            "hdr pop", "hdr" -> {
                effects.add(Contrast(0.3f * factor))
                effects.add(HslAdjustment.Builder().adjustSaturation(35f * factor).build())
                effects.add(Brightness(0.05f * factor))
            }

            else -> {
                // Return default adjustment based on factor if custom
                if (factor > 0f) {
                    effects.add(Contrast(0.15f * factor))
                }
            }
        }

        return effects
    }

    /**
     * Produces a matching Compose ColorMatrix for real-time live preview rendering on Canvas / VideoView.
     */
    fun getComposeColorMatrix(filterName: String, intensityPercent: Int = 100): ColorMatrix {
        if (filterName.equals("Normal", ignoreCase = true) || intensityPercent <= 0) {
            return ColorMatrix()
        }

        val factor = (intensityPercent / 100f).coerceIn(0f, 1f)

        return when (filterName.lowercase().trim()) {
            "grayscale", "gray", "b&w" -> {
                ColorMatrix().apply { setToSaturation(1f - factor) }
            }

            "sepia" -> {
                val rR = 0.393f * factor + (1f - factor)
                val rG = 0.769f * factor
                val rB = 0.189f * factor
                val gR = 0.349f * factor
                val gG = 0.686f * factor + (1f - factor)
                val gB = 0.168f * factor
                val bR = 0.272f * factor
                val bG = 0.534f * factor
                val bB = 0.131f * factor + (1f - factor)

                ColorMatrix(
                    floatArrayOf(
                        rR, rG, rB, 0f, 0f,
                        gR, gG, gB, 0f, 0f,
                        bR, bG, bB, 0f, 0f,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
            }

            "vivid" -> {
                ColorMatrix().apply { setToSaturation(1f + 0.8f * factor) }
            }

            "saturation", "sat" -> {
                ColorMatrix().apply { setToSaturation(1f + 1.2f * factor) }
            }

            "warm" -> {
                ColorMatrix(
                    floatArrayOf(
                        1f + (0.25f * factor), 0f, 0f, 0f, 15f * factor,
                        0f, 1f + (0.1f * factor), 0f, 0f, 8f * factor,
                        0f, 0f, 1f - (0.15f * factor), 0f, -10f * factor,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
            }

            "cool" -> {
                ColorMatrix(
                    floatArrayOf(
                        1f - (0.15f * factor), 0f, 0f, 0f, -10f * factor,
                        0f, 1f + (0.05f * factor), 0f, 0f, 5f * factor,
                        0f, 0f, 1f + (0.3f * factor), 0f, 20f * factor,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
            }

            "cyberpunk" -> {
                ColorMatrix(
                    floatArrayOf(
                        1.3f * factor + (1f - factor), 0f, 0f, 0f, 25f * factor,
                        0f, 0.85f * factor + (1f - factor), 0f, 0f, -15f * factor,
                        0f, 0f, 1.45f * factor + (1f - factor), 0f, 35f * factor,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
            }

            "vintage" -> {
                ColorMatrix(
                    floatArrayOf(
                        0.9f * factor + (1f - factor), 0.1f * factor, 0.1f * factor, 0f, 15f * factor,
                        0.1f * factor, 0.85f * factor + (1f - factor), 0.05f * factor, 0f, 10f * factor,
                        0.05f * factor, 0.05f * factor, 0.7f * factor + (1f - factor), 0f, -5f * factor,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
            }

            "b&w dramatic", "noir" -> {
                ColorMatrix().apply { setToSaturation(0f) }
            }

            "hdr pop", "hdr" -> {
                ColorMatrix().apply { setToSaturation(1f + 0.5f * factor) }
            }

            else -> ColorMatrix()
        }
    }
}
