package com.ritvyom.yashoraReelgenerator.engine

import com.ritvyom.yashoraReelgenerator.core.model.AnimatableProperty
import com.ritvyom.yashoraReelgenerator.core.model.BezierControlPoints
import com.ritvyom.yashoraReelgenerator.core.model.Keyframe
import com.ritvyom.yashoraReelgenerator.core.model.KeyframeInterpolation
import com.ritvyom.yashoraReelgenerator.core.model.KeyframeTrack
import kotlin.math.abs

/**
 * Deterministic Keyframe Evaluation Engine.
 *
 * Responsibilities:
 * - Single source of truth for keyframe interpolation across Preview and Export.
 * - Non-accumulative: Evaluates directly from absolute or local timeline timestamp without drift.
 * - Full easing support: STEP/HOLD, LINEAR, EASE_IN, EASE_OUT, EASE_IN_OUT, CUBIC_BEZIER (W3C standard Newton-Raphson solver).
 * - Edge case handling: 0 keyframes, 1 keyframe, duplicate timestamps, boundary clamping, negative values, extreme rotation.
 */
object KeyframeEvaluator {

    /**
     * Evaluates a single keyframe track at a specific time in microseconds.
     */
    fun <T : Number> evaluate(track: KeyframeTrack<T>, timeUs: Long): Float {
        val keyframes = track.getSortedKeyframes()
        if (keyframes.isEmpty()) return track.defaultValue.toFloat()
        if (keyframes.size == 1) return keyframes[0].value.toFloat()

        // Boundary Clamping
        if (keyframes.first().timeUs == keyframes.last().timeUs) {
            return if (timeUs >= keyframes.last().timeUs) keyframes.last().value.toFloat() else keyframes.first().value.toFloat()
        }
        if (timeUs < keyframes.first().timeUs) return keyframes.first().value.toFloat()
        if (timeUs >= keyframes.last().timeUs) return keyframes.last().value.toFloat()

        // Efficient lookup: Binary search or linear scan
        val count = keyframes.size
        var low = 0
        var high = count - 2
        var segmentIndex = 0

        while (low <= high) {
            val mid = (low + high) ushr 1
            val kMid = keyframes[mid]
            val kNext = keyframes[mid + 1]

            if (timeUs < kMid.timeUs) {
                high = mid - 1
            } else if (timeUs >= kNext.timeUs) {
                low = mid + 1
            } else {
                segmentIndex = mid
                break
            }
        }

        val k0 = keyframes[segmentIndex]
        val k1 = keyframes[segmentIndex + 1]

        val durationUs = k1.timeUs - k0.timeUs
        if (durationUs <= 0L) {
            // Duplicate timestamp or zero duration: instant step
            return if (timeUs >= k1.timeUs) k1.value.toFloat() else k0.value.toFloat()
        }

        val rawProgress = ((timeUs - k0.timeUs).toDouble() / durationUs.toDouble()).toFloat().coerceIn(0f, 1f)
        val curvedProgress = applyEasing(rawProgress, k0.interpolation, k0.bezierControlPoints)

        val v0 = k0.value.toFloat()
        val v1 = k1.value.toFloat()

        return v0 + (v1 - v0) * curvedProgress
    }

    /**
     * Multi-property extraction helper from an arbitrary collection of keyframe tracks.
     */
    fun evaluateProperty(
        tracks: List<KeyframeTrack<*>>,
        property: AnimatableProperty,
        fallback: Float,
        timeUs: Long
    ): Float {
        if (tracks.isEmpty()) return fallback
        val track = tracks.firstOrNull { it.property == property } ?: return fallback
        @Suppress("UNCHECKED_CAST")
        return (track as? KeyframeTrack<Number>)?.evaluate(timeUs) ?: fallback
    }

    /**
     * Maps timeline time to clip-local time, accounting for clip start time and speed multiplier.
     */
    fun timelineTimeToLocalClipTime(
        timelineTimeUs: Long,
        clipStartTimeUs: Long,
        speedMultiplier: Float = 1.0f
    ): Long {
        val rawOffset = timelineTimeUs - clipStartTimeUs
        val effectiveSpeed = if (speedMultiplier.isFinite() && speedMultiplier > 0.001f) speedMultiplier else 1.0f
        return (rawOffset * effectiveSpeed).toLong()
    }

    /**
     * Maps clip-local time back to timeline time.
     */
    fun localClipTimeToTimelineTime(
        localClipTimeUs: Long,
        clipStartTimeUs: Long,
        speedMultiplier: Float = 1.0f
    ): Long {
        val effectiveSpeed = if (speedMultiplier.isFinite() && speedMultiplier > 0.001f) speedMultiplier else 1.0f
        return clipStartTimeUs + (localClipTimeUs / effectiveSpeed).toLong()
    }

    /**
     * Applies easing curves based on interpolation type.
     */
    fun applyEasing(
        t: Float,
        interpolation: KeyframeInterpolation,
        controlPoints: BezierControlPoints?
    ): Float {
        return when (interpolation) {
            KeyframeInterpolation.HOLD, KeyframeInterpolation.STEP -> {
                if (t >= 1.0f) 1.0f else 0.0f
            }
            KeyframeInterpolation.LINEAR -> t
            KeyframeInterpolation.EASE_IN -> t * t
            KeyframeInterpolation.EASE_OUT -> t * (2f - t)
            KeyframeInterpolation.EASE_IN_OUT -> {
                if (t < 0.5f) {
                    2f * t * t
                } else {
                    -1f + (4f - 2f * t) * t
                }
            }
            KeyframeInterpolation.CUBIC_BEZIER, KeyframeInterpolation.CUSTOM_BEZIER -> {
                val pts = controlPoints ?: BezierControlPoints.EASE
                solveCubicBezier(pts.x1, pts.y1, pts.x2, pts.y2, t)
            }
        }
    }

    /**
     * Deterministic W3C CSS-compliant Cubic Bézier solver.
     * Solves for parameter u such that X(u) = targetX, then computes Y(u).
     * Uses Newton-Raphson iterations with bisection fallback for guaranteed convergence.
     */
    fun solveCubicBezier(x1: Float, y1: Float, x2: Float, y2: Float, targetX: Float): Float {
        if (targetX <= 0f) return 0f
        if (targetX >= 1f) return 1f

        // Newton-Raphson root finding for u where sampleCurveX(u) == targetX
        var u = targetX
        for (i in 0 until 8) {
            val currentX = sampleCurveX(x1, x2, u) - targetX
            if (abs(currentX) < 1e-6f) {
                return sampleCurveY(y1, y2, u)
            }
            val dX = sampleCurveDerivativeX(x1, x2, u)
            if (abs(dX) < 1e-6f) break
            u -= currentX / dX
        }

        // Bisection fallback if Newton-Raphson diverges outside [0, 1]
        var low = 0f
        var high = 1f
        u = targetX
        while (low < high) {
            val currentX = sampleCurveX(x1, x2, u)
            if (abs(currentX - targetX) < 1e-5f) {
                return sampleCurveY(y1, y2, u)
            }
            if (targetX > currentX) {
                low = u
            } else {
                high = u
            }
            u = (high + low) * 0.5f
        }

        return sampleCurveY(y1, y2, u)
    }

    private fun sampleCurveX(x1: Float, x2: Float, t: Float): Float {
        // (1-t)^3 * 0 + 3*(1-t)^2 * t * x1 + 3*(1-t) * t^2 * x2 + t^3 * 1
        val u = 1f - t
        return 3f * u * u * t * x1 + 3f * u * t * t * x2 + t * t * t
    }

    private fun sampleCurveY(y1: Float, y2: Float, t: Float): Float {
        val u = 1f - t
        return 3f * u * u * t * y1 + 3f * u * t * t * y2 + t * t * t
    }

    private fun sampleCurveDerivativeX(x1: Float, x2: Float, t: Float): Float {
        val u = 1f - t
        return 3f * u * u * x1 + 6f * u * t * (x2 - x1) + 3f * t * t * (1f - x2)
    }
}
