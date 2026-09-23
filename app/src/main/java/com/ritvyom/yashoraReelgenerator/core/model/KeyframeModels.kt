package com.ritvyom.yashoraReelgenerator.core.model

import com.ritvyom.yashoraReelgenerator.engine.KeyframeEvaluator
import java.io.Serializable

/**
 * Types of mathematical interpolation supported between keyframes.
 */
enum class KeyframeInterpolation {
    HOLD,
    STEP,
    LINEAR,
    EASE_IN,
    EASE_OUT,
    EASE_IN_OUT,
    CUBIC_BEZIER,
    CUSTOM_BEZIER
}

/**
 * Control points for cubic bezier curves (CSS-style cubic-bezier(x1, y1, x2, y2)).
 */
data class BezierControlPoints(
    val x1: Float = 0.42f,
    val y1: Float = 0.0f,
    val x2: Float = 0.58f,
    val y2: Float = 1.0f
) : Serializable {
    companion object {
        val EASE = BezierControlPoints(0.25f, 0.1f, 0.25f, 1.0f)
        val EASE_IN = BezierControlPoints(0.42f, 0.0f, 1.0f, 1.0f)
        val EASE_OUT = BezierControlPoints(0.0f, 0.0f, 0.58f, 1.0f)
        val EASE_IN_OUT = BezierControlPoints(0.42f, 0.0f, 0.58f, 1.0f)
    }
}

/**
 * Animatable property types on clips and visual layers.
 */
enum class AnimatableProperty {
    POSITION_X,
    POSITION_Y,
    SCALE_X,
    SCALE_Y,
    ROTATION_DEGREES,
    OPACITY,
    ANCHOR_X,
    ANCHOR_Y,
    CROP_LEFT,
    CROP_TOP,
    CROP_RIGHT,
    CROP_BOTTOM,
    ZOOM,
    BLUR_RADIUS,
    VOLUME,
    PAN,
    COLOR_BRIGHTNESS,
    COLOR_CONTRAST,
    COLOR_SATURATION,
    COLOR_WARMTH,
    COLOR_EXPOSURE,
    COLOR_TINT,
    COLOR_VIGNETTE,
    EFFECT_INTENSITY
}

/**
 * An individual keyframe in timeline or clip-local time.
 */
data class Keyframe<T : Number>(
    val timeUs: Long,
    val value: T,
    val interpolation: KeyframeInterpolation = KeyframeInterpolation.LINEAR,
    val bezierControlPoints: BezierControlPoints? = null
) : Serializable, Comparable<Keyframe<T>> {
    override fun compareTo(other: Keyframe<T>): Int = this.timeUs.compareTo(other.timeUs)
}

/**
 * A track of keyframes dedicated to animating a single numeric property.
 */
data class KeyframeTrack<T : Number>(
    val property: AnimatableProperty,
    val keyframes: List<Keyframe<T>> = emptyList(),
    val defaultValue: T
) : Serializable {

    // Stable pre-sorted list to eliminate per-frame allocations during playback/export
    private val sortedKeyframes: List<Keyframe<T>> = keyframes.sorted()

    fun getSortedKeyframes(): List<Keyframe<T>> = sortedKeyframes

    /**
     * Deterministically evaluates the property value at a specific timestamp [timeUs].
     */
    fun evaluate(timeUs: Long): Float {
        return KeyframeEvaluator.evaluate(this, timeUs)
    }
}

