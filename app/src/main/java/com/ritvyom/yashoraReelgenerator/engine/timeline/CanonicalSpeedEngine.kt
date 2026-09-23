package com.ritvyom.yashoraReelgenerator.engine.timeline

import java.io.Serializable
import kotlin.math.max
import kotlin.math.min

/**
 * Standard discrete playback speeds supported by professional video editors.
 */
enum class StandardSpeedPreset(val multiplier: Float, val label: String) {
    SPEED_0_25X(0.25f, "0.25x"),
    SPEED_0_5X(0.5f, "0.5x"),
    SPEED_0_75X(0.75f, "0.75x"),
    SPEED_1_0X(1.0f, "1x"),
    SPEED_1_25X(1.25f, "1.25x"),
    SPEED_1_5X(1.5f, "1.5x"),
    SPEED_2_0X(2.0f, "2x"),
    SPEED_3_0X(3.0f, "3x"),
    SPEED_4_0X(4.0f, "4x");

    companion object {
        fun fromMultiplier(speed: Float): StandardSpeedPreset {
            return entries.minByOrNull { kotlin.math.abs(it.multiplier - speed) } ?: SPEED_1_0X
        }
    }
}

/**
 * Speed curve anchor point for future non-linear time-ramping.
 */
data class SpeedRampPoint(
    val normalizedTimelinePos: Float, // 0.0 to 1.0 within clip
    val speedMultiplier: Float       // e.g. 0.5f, 2.0f
) : Serializable

/**
 * Deterministic Speed & Time-Remapping Engine.
 * Converts bidirectionally between Timeline Composition Time, Clip Local Time, and Source Media Presentation Time.
 * Ensures preview and export use 100% identical timestamp mappings.
 */
object CanonicalSpeedEngine {

    /**
     * Maps timeline composition time [timelineTimeUs] to the media source PTS [sourceTimeUs] for a clip.
     * Handles:
     * - Constant speed scaling (0.25x .. 10x)
     * - Reverse playback (plays backwards from clip end PTS)
     * - Freeze frame (holds source timestamp at freeze point)
     */
    fun mapTimelineToSourceTimeUs(
        timelineTimeUs: Long,
        clipStartTimeUs: Long,
        clipDurationUs: Long,
        sourceStartUs: Long,
        sourceDurationUs: Long,
        speedMultiplier: Float = 1.0f,
        isReversed: Boolean = false,
        isFrozen: Boolean = false
    ): Long {
        if (isFrozen) {
            return sourceStartUs
        }

        val localTimeUs = (timelineTimeUs - clipStartTimeUs).coerceIn(0L, clipDurationUs)
        val speed = speedMultiplier.coerceAtLeast(0.01f)

        return if (isReversed) {
            // Reverse mapping: starts from end of source range and plays backward
            val elapsedSourceUs = (localTimeUs * speed).toLong()
            val endSourceUs = sourceStartUs + sourceDurationUs
            (endSourceUs - elapsedSourceUs).coerceIn(sourceStartUs, endSourceUs)
        } else {
            // Forward mapping
            val sourceOffsetUs = (localTimeUs * speed).toLong()
            (sourceStartUs + sourceOffsetUs).coerceIn(sourceStartUs, sourceStartUs + sourceDurationUs)
        }
    }

    /**
     * Maps source media PTS [sourceTimeUs] back to timeline composition time [timelineTimeUs].
     */
    fun mapSourceToTimelineTimeUs(
        sourceTimeUs: Long,
        clipStartTimeUs: Long,
        sourceStartUs: Long,
        sourceDurationUs: Long,
        speedMultiplier: Float = 1.0f,
        isReversed: Boolean = false
    ): Long {
        val speed = speedMultiplier.coerceAtLeast(0.01f)
        return if (isReversed) {
            val endSourceUs = sourceStartUs + sourceDurationUs
            val diffFromEnd = (endSourceUs - sourceTimeUs).coerceAtLeast(0L)
            clipStartTimeUs + (diffFromEnd / speed).toLong()
        } else {
            val offsetUs = (sourceTimeUs - sourceStartUs).coerceAtLeast(0L)
            clipStartTimeUs + (offsetUs / speed).toLong()
        }
    }

    /**
     * Calculates the resulting timeline duration in microseconds when applying [newSpeed] to [sourceDurationUs].
     */
    fun calculateTimelineDurationUs(sourceDurationUs: Long, newSpeed: Float): Long {
        val speed = newSpeed.coerceIn(0.1f, 10.0f)
        return (sourceDurationUs / speed).toLong()
    }
}
