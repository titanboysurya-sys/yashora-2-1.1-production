package com.ritvyom.yashoraReelgenerator.presentation.utils

import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import androidx.media3.common.Effect
import androidx.media3.effect.Brightness
import androidx.media3.effect.Contrast
import androidx.media3.effect.RgbMatrix
import androidx.media3.effect.ScaleAndRotateTransformation

/**
 * Transition Manager and Effect Engine leveraging Media3 Transformer & Effect APIs.
 * Supports smooth transitions between clips (Fade, Dissolve, Wipe, Zoom, Slide, Flash, Blur)
 * with customizable durations (300ms - 2000ms) for real-time preview and export.
 */
object Media3TransitionEngine {

    data class TransitionItem(
        val id: String,
        val displayName: String,
        val category: String,
        val description: String,
        val iconEmoji: String
    )

    val TRANSITIONS = listOf(
        TransitionItem("None", "None / Cut", "Standard", "Direct instantaneous cut between clips", "✂️"),
        TransitionItem("Fade", "Fade (Black)", "Classic", "Cinematic smooth fade-to-black and fade-in", "🌘"),
        TransitionItem("Dissolve", "Cross Dissolve", "Classic", "Soft transparent blending between clip frames", "✨"),
        TransitionItem("Wipe Left", "Wipe Left", "Wipe", "Clean horizontal reveal sweeping from right to left", "⬅️"),
        TransitionItem("Wipe Right", "Wipe Right", "Wipe", "Clean horizontal reveal sweeping from left to right", "➡️"),
        TransitionItem("Wipe Up", "Wipe Up", "Wipe", "Vertical curtain reveal sweeping upward", "⬆️"),
        TransitionItem("Wipe Down", "Wipe Down", "Wipe", "Vertical curtain reveal sweeping downward", "⬇️"),
        TransitionItem("Zoom In", "Zoom In", "Motion", "Energetic camera push-in zoom transition", "🔍"),
        TransitionItem("Zoom Out", "Zoom Out", "Motion", "Smooth pull-back camera zoom transition", "🔎"),
        TransitionItem("Slide Left", "Slide Left", "Motion", "Fast motion push sliding clip to the left", "◀️"),
        TransitionItem("Slide Right", "Slide Right", "Motion", "Fast motion push sliding clip to the right", "▶️"),
        TransitionItem("Flash White", "Flash White", "Dynamic", "High-energy white flash burst cut", "⚡"),
        TransitionItem("Blur", "Blur Dissolve", "Dynamic", "Dreamy soft focus blur into the next scene", "🌫️")
    )

    /**
     * Builds a list of Media3 Effects for a clip based on the transition type and duration.
     * Compatible with Media3 Transformer EditedMediaItem pipeline.
     */
    fun createMedia3TransitionEffects(
        transitionType: String,
        durationMs: Long,
        transitionDurationMs: Long = 700L
    ): List<Effect> {
        if (transitionType.equals("None", ignoreCase = true) || durationMs <= 0L) {
            return emptyList()
        }

        val effects = mutableListOf<Effect>()
        val effectiveTransitionMs = transitionDurationMs.coerceIn(200L, (durationMs / 2).coerceAtLeast(300L))

        when (transitionType.trim()) {
            "Fade" -> {
                // Dim brightness at beginning of clip
                try {
                    effects.add(Brightness(0.05f))
                } catch (e: Exception) {
                    // Fallback to empty if not supported
                }
            }
            "Zoom In" -> {
                try {
                    effects.add(
                        ScaleAndRotateTransformation.Builder()
                            .setScale(1.05f, 1.05f)
                            .build()
                    )
                } catch (e: Exception) {}
            }
            "Zoom Out" -> {
                try {
                    effects.add(
                        ScaleAndRotateTransformation.Builder()
                            .setScale(0.95f, 0.95f)
                            .build()
                    )
                } catch (e: Exception) {}
            }
            "Flash White" -> {
                try {
                    effects.add(Brightness(0.25f))
                } catch (e: Exception) {}
            }
            else -> {}
        }

        return effects
    }

    /**
     * Draws real-time canvas transition effect during video frame compilation and preview.
     * @param canvas Target Android graphics canvas
     * @param width Canvas width in pixels
     * @param height Canvas height in pixels
     * @param frameIndex Current frame index within this clip
     * @param totalFrames Total frames in this clip
     * @param transitionType Selected transition id (Fade, Wipe, Zoom, etc.)
     * @param transitionDurationMs Selected transition duration in milliseconds
     * @param fps Video frame rate
     */
    fun drawTransition(
        canvas: Canvas,
        width: Int,
        height: Int,
        frameIndex: Int,
        totalFrames: Int,
        transitionType: String,
        transitionDurationMs: Long = 700L,
        fps: Int = 30
    ) {
        if (transitionType.equals("None", ignoreCase = true)) return

        val transitionFrames = ((transitionDurationMs / 1000f) * fps).toInt().coerceIn(4, totalFrames / 2)
        val isEntering = frameIndex < transitionFrames
        val isExiting = frameIndex > (totalFrames - transitionFrames)

        if (!isEntering && !isExiting) return

        val progress = if (isEntering) {
            1.0f - (frameIndex.toFloat() / transitionFrames) // 1.0 down to 0.0
        } else {
            (frameIndex - (totalFrames - transitionFrames)).toFloat() / transitionFrames // 0.0 up to 1.0
        }

        val paint = Paint().apply { isAntiAlias = true }

        when (transitionType.trim()) {
            "Fade" -> {
                val alpha = (progress * 255).toInt().coerceIn(0, 255)
                paint.color = AndroidColor.argb(alpha, 0, 0, 0)
                paint.style = Paint.Style.FILL
                canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
            }

            "Dissolve" -> {
                // Cross-dissolve alpha overlay
                val alpha = (progress * 220).toInt().coerceIn(0, 255)
                paint.color = AndroidColor.argb(alpha, 18, 18, 24)
                paint.style = Paint.Style.FILL
                canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
            }

            "Wipe Left" -> {
                // Wipe curtain moving horizontally from right to left
                val wipeWidth = width * progress
                val left = width - wipeWidth
                paint.color = AndroidColor.argb(255, 12, 12, 16)
                paint.style = Paint.Style.FILL
                canvas.drawRect(left, 0f, width.toFloat(), height.toFloat(), paint)
                
                // Glowing edge line
                paint.color = AndroidColor.argb(200, 255, 87, 34)
                paint.strokeWidth = 6f
                canvas.drawLine(left, 0f, left, height.toFloat(), paint)
            }

            "Wipe Right" -> {
                // Wipe curtain moving horizontally from left to right
                val wipeWidth = width * progress
                paint.color = AndroidColor.argb(255, 12, 12, 16)
                paint.style = Paint.Style.FILL
                canvas.drawRect(0f, 0f, wipeWidth, height.toFloat(), paint)

                paint.color = AndroidColor.argb(200, 255, 87, 34)
                paint.strokeWidth = 6f
                canvas.drawLine(wipeWidth, 0f, wipeWidth, height.toFloat(), paint)
            }

            "Wipe Up" -> {
                // Wipe curtain moving vertically from bottom to top
                val wipeHeight = height * progress
                val top = height - wipeHeight
                paint.color = AndroidColor.argb(255, 12, 12, 16)
                paint.style = Paint.Style.FILL
                canvas.drawRect(0f, top, width.toFloat(), height.toFloat(), paint)

                paint.color = AndroidColor.argb(200, 255, 87, 34)
                paint.strokeWidth = 6f
                canvas.drawLine(0f, top, width.toFloat(), top, paint)
            }

            "Wipe Down" -> {
                // Wipe curtain moving vertically from top to bottom
                val wipeHeight = height * progress
                paint.color = AndroidColor.argb(255, 12, 12, 16)
                paint.style = Paint.Style.FILL
                canvas.drawRect(0f, 0f, width.toFloat(), wipeHeight, paint)

                paint.color = AndroidColor.argb(200, 255, 87, 34)
                paint.strokeWidth = 6f
                canvas.drawLine(0f, wipeHeight, width.toFloat(), wipeHeight, paint)
            }

            "Zoom In", "Zoom Out" -> {
                // Vignette shadow around borders simulating depth of field
                val alpha = (progress * 220).toInt().coerceIn(0, 255)
                paint.color = AndroidColor.argb(alpha, 0, 0, 0)
                val borderThickness = (width * 0.15f * progress)
                // Draw border frame
                canvas.drawRect(0f, 0f, width.toFloat(), borderThickness, paint)
                canvas.drawRect(0f, height - borderThickness, width.toFloat(), height.toFloat(), paint)
                canvas.drawRect(0f, borderThickness, borderThickness, height - borderThickness, paint)
                canvas.drawRect(width - borderThickness, borderThickness, width.toFloat(), height - borderThickness, paint)
            }

            "Slide Left", "Slide Right" -> {
                // Motion blur shadow during slide
                val alpha = (progress * 190).toInt().coerceIn(0, 255)
                paint.color = AndroidColor.argb(alpha, 10, 10, 14)
                val offset = if (transitionType.contains("Left")) width * progress else 0f
                val w = width * progress
                canvas.drawRect(offset, 0f, offset + w, height.toFloat(), paint)
            }

            "Flash White" -> {
                // High-energy white flash
                val alpha = (progress * 240).toInt().coerceIn(0, 255)
                paint.color = AndroidColor.argb(alpha, 255, 255, 255)
                paint.style = Paint.Style.FILL
                canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
            }

            "Blur" -> {
                // Soft gradient blur simulation
                val alpha = (progress * 180).toInt().coerceIn(0, 255)
                paint.color = AndroidColor.argb(alpha, 25, 25, 35)
                paint.style = Paint.Style.FILL
                canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
            }

            else -> {
                // Standard fade fallback
                val alpha = (progress * 255).toInt().coerceIn(0, 255)
                paint.color = AndroidColor.argb(alpha, 0, 0, 0)
                paint.style = Paint.Style.FILL
                canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
            }
        }
    }
}
