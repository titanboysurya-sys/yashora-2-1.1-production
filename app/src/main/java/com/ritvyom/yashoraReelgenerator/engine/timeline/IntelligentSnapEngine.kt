package com.ritvyom.yashoraReelgenerator.engine.timeline

import com.ritvyom.yashoraReelgenerator.core.model.*
import java.io.Serializable
import java.util.UUID
import kotlin.math.abs

/**
 * Snap target types representing magnetic snap points on the professional timeline.
 */
enum class SnapPointType {
    CLIP_START,
    CLIP_END,
    PLAYHEAD,
    TRANSITION_BOUNDARY,
    KEYFRAME,
    MARKER,
    SUBTITLE_BOUNDARY,
    TEXT_BOUNDARY,
    AUDIO_BEAT
}

/**
 * An individual snap point candidate evaluated during scrubbing/trimming.
 */
data class TimelineSnapPoint(
    val timeUs: Long,
    val type: SnapPointType,
    val label: String = ""
) : Serializable

/**
 * Result of timeline snapping calculation.
 */
data class TimelineSnapResult(
    val snappedTimeUs: Long,
    val didSnap: Boolean,
    val snapPoint: TimelineSnapPoint? = null
) : Serializable

/**
 * Intelligent timeline snap engine.
 * Computes non-aggressive magnetic snapping to clip boundaries, playhead, keyframes, markers, and transitions.
 */
object IntelligentSnapEngine {

    /**
     * Calculates snapping for a requested timestamp against the composition's snap points.
     * Snaps if [requestedTimeUs] is within [toleranceUs].
     */
    fun findSnap(
        requestedTimeUs: Long,
        snapPoints: List<TimelineSnapPoint>,
        toleranceUs: Long = 100_000L, // 100ms tolerance by default
        enabled: Boolean = true
    ): TimelineSnapResult {
        if (!enabled || snapPoints.isEmpty()) {
            return TimelineSnapResult(requestedTimeUs, false, null)
        }

        var closestPoint: TimelineSnapPoint? = null
        var minDiff = Long.MAX_VALUE

        for (pt in snapPoints) {
            val diff = abs(pt.timeUs - requestedTimeUs)
            if (diff <= toleranceUs && diff < minDiff) {
                minDiff = diff
                closestPoint = pt
            }
        }

        return if (closestPoint != null) {
            TimelineSnapResult(closestPoint.timeUs, true, closestPoint)
        } else {
            TimelineSnapResult(requestedTimeUs, false, null)
        }
    }

    /**
     * Gathers all candidate snap points from a [CanonicalTimeline].
     */
    fun collectSnapPoints(
        timeline: CanonicalTimeline,
        currentPlayheadUs: Long? = null
    ): List<TimelineSnapPoint> {
        val points = mutableListOf<TimelineSnapPoint>()

        // 1. Playhead
        if (currentPlayheadUs != null && currentPlayheadUs >= 0L) {
            points.add(TimelineSnapPoint(currentPlayheadUs, SnapPointType.PLAYHEAD, "Playhead"))
        }

        // 2. Timeline Markers
        for (marker in timeline.markers) {
            points.add(TimelineSnapPoint(marker.timeUs, SnapPointType.MARKER, marker.label))
        }

        // 3. Clip boundaries and transitions across all tracks
        for (track in timeline.tracks) {
            for (clip in track.clips) {
                points.add(TimelineSnapPoint(clip.startTimeUs, SnapPointType.CLIP_START, "${clip.name} Start"))
                points.add(TimelineSnapPoint(clip.endTimeUs, SnapPointType.CLIP_END, "${clip.name} End"))

                // Transition boundaries for video clips
                if (clip is VideoClipItem && clip.transitionInDurationMs > 0L) {
                    val transEndUs = clip.startTimeUs + clip.transitionInDurationMs * 1000L
                    points.add(TimelineSnapPoint(transEndUs, SnapPointType.TRANSITION_BOUNDARY, "Transition In End"))
                }

                // Keyframe snap points
                when (clip) {
                    is VideoClipItem -> {
                        for (kfTrack in clip.keyframeTracks) {
                            for (kf in kfTrack.keyframes) {
                                points.add(TimelineSnapPoint(clip.startTimeUs + kf.timeUs, SnapPointType.KEYFRAME, "Keyframe"))
                            }
                        }
                    }
                    is TextClipItem -> {
                        for (kfTrack in clip.keyframeTracks) {
                            for (kf in kfTrack.keyframes) {
                                points.add(TimelineSnapPoint(clip.startTimeUs + kf.timeUs, SnapPointType.KEYFRAME, "Keyframe"))
                            }
                        }
                    }
                    is StickerClipItem -> {
                        for (kfTrack in clip.keyframeTracks) {
                            for (kf in kfTrack.keyframes) {
                                points.add(TimelineSnapPoint(clip.startTimeUs + kf.timeUs, SnapPointType.KEYFRAME, "Keyframe"))
                            }
                        }
                    }
                    is AudioClipItem -> {
                        for (kfTrack in clip.keyframeTracks) {
                            for (kf in kfTrack.keyframes) {
                                points.add(TimelineSnapPoint(clip.startTimeUs + kf.timeUs, SnapPointType.KEYFRAME, "Keyframe"))
                            }
                        }
                    }
                }
            }
        }

        return points.sortedBy { it.timeUs }
    }
}
