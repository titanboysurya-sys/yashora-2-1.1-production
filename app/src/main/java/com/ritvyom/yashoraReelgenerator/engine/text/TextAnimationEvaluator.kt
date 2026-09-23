package com.ritvyom.yashoraReelgenerator.engine.text

import java.io.Serializable

/**
 * Evaluated dynamic animation transforms for a text element at time T.
 */
data class TextAnimationResult(
    val translationOffsetX: Float = 0f,
    val translationOffsetY: Float = 0f,
    val scaleMultiplier: Float = 1.0f,
    val alphaMultiplier: Float = 1.0f,
    val visibleCharacterCount: Int = Int.MAX_VALUE
) : Serializable

/**
 * Text Animation Evaluator.
 * Computes procedural In/Out animations (Pop, Fade, Slide Up/Down/Left/Right, Typewriter, Bounce)
 * deterministically based on clip local time, without hardcoding logic inside UI components.
 */
object TextAnimationEvaluator {

    /**
     * Evaluates the animation offsets, scaling, opacity, and visible characters at [localTimeUs].
     *
     * @param animationType The requested animation (e.g. FADE, POP, SLIDE_UP, TYPEWRITER)
     * @param localTimeUs Current elapsed time since clip start in microseconds
     * @param clipDurationUs Total duration of the text clip in microseconds
     * @param animationDurationSec Duration of the In/Out transition in seconds (default 0.5s)
     * @param totalCharCount Total length/grapheme count of the text string
     */
    fun evaluate(
        animationType: TextAnimationType,
        localTimeUs: Long,
        clipDurationUs: Long,
        animationDurationSec: Float = 0.5f,
        totalCharCount: Int = 0
    ): TextAnimationResult {
        if (animationType == TextAnimationType.NONE || clipDurationUs <= 0L) {
            return TextAnimationResult(visibleCharacterCount = totalCharCount)
        }

        val animDurationUs = (animationDurationSec * 1_000_000L).toLong().coerceIn(100_000L, clipDurationUs / 2)
        val timeUntilEndUs = clipDurationUs - localTimeUs

        // 1. Entrance Animation Phase
        if (localTimeUs < animDurationUs) {
            val progress = (localTimeUs.toFloat() / animDurationUs).coerceIn(0f, 1f)
            return computeEntrance(animationType, progress, totalCharCount)
        }

        // 2. Exit Animation Phase
        if (timeUntilEndUs < animDurationUs) {
            val exitProgress = 1.0f - (timeUntilEndUs.toFloat() / animDurationUs).coerceIn(0f, 1f)
            return computeExit(animationType, exitProgress, totalCharCount)
        }

        // 3. Steady State Phase
        return TextAnimationResult(visibleCharacterCount = totalCharCount)
    }

    private fun computeEntrance(
        type: TextAnimationType,
        progress: Float,
        totalCharCount: Int
    ): TextAnimationResult {
        return when (type) {
            TextAnimationType.FADE -> {
                TextAnimationResult(alphaMultiplier = progress, visibleCharacterCount = totalCharCount)
            }
            TextAnimationType.POP, TextAnimationType.SCALE_POP -> {
                // Overshoot spring-like curve: f(p) = sin(p * PI / 2) * 1.15
                val scale = if (progress < 0.7f) {
                    (progress / 0.7f) * 1.15f
                } else {
                    1.15f - ((progress - 0.7f) / 0.3f) * 0.15f
                }
                TextAnimationResult(scaleMultiplier = scale.coerceAtLeast(0f), alphaMultiplier = progress, visibleCharacterCount = totalCharCount)
            }
            TextAnimationType.SLIDE_UP -> {
                val offsetY = (1.0f - progress) * 0.25f // Slide from 25% below
                TextAnimationResult(translationOffsetY = offsetY, alphaMultiplier = progress, visibleCharacterCount = totalCharCount)
            }
            TextAnimationType.SLIDE_DOWN -> {
                val offsetY = -(1.0f - progress) * 0.25f // Slide from 25% above
                TextAnimationResult(translationOffsetY = offsetY, alphaMultiplier = progress, visibleCharacterCount = totalCharCount)
            }
            TextAnimationType.SLIDE_LEFT -> {
                val offsetX = (1.0f - progress) * 0.35f
                TextAnimationResult(translationOffsetX = offsetX, alphaMultiplier = progress, visibleCharacterCount = totalCharCount)
            }
            TextAnimationType.SLIDE_RIGHT -> {
                val offsetX = -(1.0f - progress) * 0.35f
                TextAnimationResult(translationOffsetX = offsetX, alphaMultiplier = progress, visibleCharacterCount = totalCharCount)
            }
            TextAnimationType.BOUNCE -> {
                // Damped bounce
                val bounce = kotlin.math.abs(kotlin.math.sin(progress * Math.PI * 2.5)).toFloat() * (1f - progress)
                TextAnimationResult(translationOffsetY = -bounce * 0.1f, scaleMultiplier = 1f + bounce * 0.2f, alphaMultiplier = progress, visibleCharacterCount = totalCharCount)
            }
            TextAnimationType.TYPEWRITER -> {
                val visibleChars = (progress * totalCharCount).toInt().coerceIn(1, totalCharCount)
                TextAnimationResult(visibleCharacterCount = visibleChars)
            }
            TextAnimationType.WORD_POP, TextAnimationType.WORD_FADE -> {
                val scale = 0.8f + (progress * 0.2f)
                TextAnimationResult(scaleMultiplier = scale, alphaMultiplier = progress, visibleCharacterCount = totalCharCount)
            }
            TextAnimationType.NONE -> {
                TextAnimationResult(visibleCharacterCount = totalCharCount)
            }
        }
    }

    private fun computeExit(
        type: TextAnimationType,
        exitProgress: Float, // 0 -> just starting exit, 1 -> fully exited
        totalCharCount: Int
    ): TextAnimationResult {
        val remainingAlpha = (1.0f - exitProgress).coerceIn(0f, 1f)
        return when (type) {
            TextAnimationType.FADE, TextAnimationType.POP, TextAnimationType.SCALE_POP -> {
                TextAnimationResult(
                    scaleMultiplier = (1.0f - exitProgress * 0.2f),
                    alphaMultiplier = remainingAlpha,
                    visibleCharacterCount = totalCharCount
                )
            }
            TextAnimationType.SLIDE_UP -> {
                TextAnimationResult(
                    translationOffsetY = -exitProgress * 0.25f,
                    alphaMultiplier = remainingAlpha,
                    visibleCharacterCount = totalCharCount
                )
            }
            TextAnimationType.SLIDE_DOWN -> {
                TextAnimationResult(
                    translationOffsetY = exitProgress * 0.25f,
                    alphaMultiplier = remainingAlpha,
                    visibleCharacterCount = totalCharCount
                )
            }
            TextAnimationType.SLIDE_LEFT -> {
                TextAnimationResult(
                    translationOffsetX = -exitProgress * 0.35f,
                    alphaMultiplier = remainingAlpha,
                    visibleCharacterCount = totalCharCount
                )
            }
            TextAnimationType.SLIDE_RIGHT -> {
                TextAnimationResult(
                    translationOffsetX = exitProgress * 0.35f,
                    alphaMultiplier = remainingAlpha,
                    visibleCharacterCount = totalCharCount
                )
            }
            else -> {
                TextAnimationResult(
                    alphaMultiplier = remainingAlpha,
                    visibleCharacterCount = totalCharCount
                )
            }
        }
    }
}
