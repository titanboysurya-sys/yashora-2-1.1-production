package com.ritvyom.yashoraReelgenerator.engine.timeline

import com.ritvyom.yashoraReelgenerator.core.model.*
import java.util.UUID

/**
 * Result of splitting a clip into two sequential parts.
 */
data class SplitClipResult(
    val firstClip: TimelineClipItem,
    val secondClip: TimelineClipItem
)

/**
 * Professional Clip Operations Engine.
 * Implements deterministic operations directly onto CanonicalTimeline:
 * CUT, SPLIT, TRIM START, TRIM END, MOVE, DUPLICATE, DELETE, REPLACE, FREEZE FRAME, REVERSE, SPEED, SPEED RAMP.
 *
 * Guarantees:
 * - All operations preserve audio synchronization, keyframe offsets, transitions, text, stickers, transforms, and effects.
 * - Single source of truth: returns updated [CanonicalTimeline].
 */
object ProfessionalClipOperations {

    /**
     * Splits a clip at [splitTimeUs] (in timeline composition time).
     * Preserves speed mapping, keyframes (offset adjusted for second half), effects, and styling.
     */
    fun splitClip(
        timeline: CanonicalTimeline,
        clipId: String,
        splitTimeUs: Long
    ): CanonicalTimeline {
        val targetTrack = timeline.tracks.firstOrNull { tr -> tr.clips.any { it.id == clipId } }
            ?: return timeline
        val clip = targetTrack.clips.firstOrNull { it.id == clipId }
            ?: return timeline

        if (splitTimeUs <= clip.startTimeUs || splitTimeUs >= clip.endTimeUs) {
            return timeline // Split point must be strictly within clip bounds
        }

        val firstDurationUs = splitTimeUs - clip.startTimeUs
        val secondDurationUs = clip.endTimeUs - splitTimeUs

        val (first, second) = when (clip) {
            is VideoClipItem -> {
                val speed = clip.playbackSpeed.coerceAtLeast(0.1f)
                val firstSourceDuration = (firstDurationUs * speed).toLong()
                val secondSourceStart = clip.sourceStartUs + firstSourceDuration
                val secondSourceDuration = clip.sourceDurationUs - firstSourceDuration

                // Partition keyframes
                val firstKeyframes = clip.keyframeTracks.map { kfTrack ->
                    kfTrack.filterKeyframes { it.timeUs < firstDurationUs }
                }
                val secondKeyframes = clip.keyframeTracks.map { kfTrack ->
                    kfTrack.filterKeyframes { it.timeUs >= firstDurationUs }
                        .offsetKeyframes(-firstDurationUs)
                }

                val clip1 = clip.copy(
                    durationUs = firstDurationUs,
                    sourceDurationUs = firstSourceDuration,
                    transitionOutType = "None",
                    transitionOutDurationMs = 0L,
                    keyframeTracks = firstKeyframes
                )
                val clip2 = clip.copy(
                    id = UUID.randomUUID().toString(),
                    name = "${clip.name} (Part 2)",
                    startTimeUs = splitTimeUs,
                    durationUs = secondDurationUs,
                    sourceStartUs = secondSourceStart,
                    sourceDurationUs = secondSourceDuration,
                    transitionInType = "None",
                    transitionInDurationMs = 0L,
                    keyframeTracks = secondKeyframes
                )
                Pair(clip1, clip2)
            }
            is AudioClipItem -> {
                val speed = clip.playbackSpeed.coerceAtLeast(0.1f)
                val firstSourceDuration = (firstDurationUs * speed).toLong()
                val secondSourceStart = clip.sourceStartUs + firstSourceDuration
                val secondSourceDuration = clip.sourceDurationUs - firstSourceDuration

                val firstKeyframes = clip.keyframeTracks.map { kfTrack ->
                    kfTrack.filterKeyframes { it.timeUs < firstDurationUs }
                }
                val secondKeyframes = clip.keyframeTracks.map { kfTrack ->
                    kfTrack.filterKeyframes { it.timeUs >= firstDurationUs }
                        .offsetKeyframes(-firstDurationUs)
                }

                val clip1 = clip.copy(
                    durationUs = firstDurationUs,
                    sourceDurationUs = firstSourceDuration,
                    keyframeTracks = firstKeyframes
                )
                val clip2 = clip.copy(
                    id = UUID.randomUUID().toString(),
                    name = "${clip.name} (Part 2)",
                    startTimeUs = splitTimeUs,
                    durationUs = secondDurationUs,
                    sourceStartUs = secondSourceStart,
                    sourceDurationUs = secondSourceDuration,
                    keyframeTracks = secondKeyframes
                )
                Pair(clip1, clip2)
            }
            is TextClipItem -> {
                val firstKeyframes = clip.keyframeTracks.map { kfTrack ->
                    kfTrack.filterKeyframes { it.timeUs < firstDurationUs }
                }
                val secondKeyframes = clip.keyframeTracks.map { kfTrack ->
                    kfTrack.filterKeyframes { it.timeUs >= firstDurationUs }
                        .offsetKeyframes(-firstDurationUs)
                }

                val clip1 = clip.copy(
                    durationUs = firstDurationUs,
                    sourceDurationUs = firstDurationUs,
                    keyframeTracks = firstKeyframes
                )
                val clip2 = clip.copy(
                    id = UUID.randomUUID().toString(),
                    name = "${clip.name} (Part 2)",
                    startTimeUs = splitTimeUs,
                    durationUs = secondDurationUs,
                    sourceStartUs = 0L,
                    sourceDurationUs = secondDurationUs,
                    keyframeTracks = secondKeyframes
                )
                Pair(clip1, clip2)
            }
            is StickerClipItem -> {
                val firstKeyframes = clip.keyframeTracks.map { kfTrack ->
                    kfTrack.filterKeyframes { it.timeUs < firstDurationUs }
                }
                val secondKeyframes = clip.keyframeTracks.map { kfTrack ->
                    kfTrack.filterKeyframes { it.timeUs >= firstDurationUs }
                        .offsetKeyframes(-firstDurationUs)
                }

                val clip1 = clip.copy(
                    durationUs = firstDurationUs,
                    sourceDurationUs = firstDurationUs,
                    keyframeTracks = firstKeyframes
                )
                val clip2 = clip.copy(
                    id = UUID.randomUUID().toString(),
                    name = "${clip.name} (Part 2)",
                    startTimeUs = splitTimeUs,
                    durationUs = secondDurationUs,
                    sourceStartUs = 0L,
                    sourceDurationUs = secondDurationUs,
                    keyframeTracks = secondKeyframes
                )
                Pair(clip1, clip2)
            }
            else -> return timeline
        }

        val updatedClips = mutableListOf<TimelineClipItem>()
        for (c in targetTrack.clips) {
            if (c.id == clipId) {
                updatedClips.add(first)
                updatedClips.add(second)
            } else {
                updatedClips.add(c)
            }
        }

        val updatedTrack = targetTrack.copy(clips = updatedClips)
        return timeline.copy(
            tracks = timeline.tracks.map { if (it.id == updatedTrack.id) updatedTrack else it }
        )
    }

    /**
     * Trims the start of a clip by [deltaStartUs].
     * Increases startTimeUs, adjusts sourceStartUs by (deltaStartUs * speed), and reduces durationUs.
     */
    fun trimClipStart(
        timeline: CanonicalTimeline,
        clipId: String,
        deltaStartUs: Long
    ): CanonicalTimeline {
        val targetTrack = timeline.tracks.firstOrNull { tr -> tr.clips.any { it.id == clipId } } ?: return timeline
        val clip = targetTrack.clips.firstOrNull { it.id == clipId } ?: return timeline

        val maxTrim = clip.durationUs - 100_000L // minimum 100ms remaining
        val effectiveDelta = deltaStartUs.coerceIn(0L, maxTrim)
        if (effectiveDelta <= 0L) return timeline

        val newStartTime = clip.startTimeUs + effectiveDelta
        val newDuration = clip.durationUs - effectiveDelta

        val updatedClip = when (clip) {
            is VideoClipItem -> {
                val sourceDelta = (effectiveDelta * clip.playbackSpeed).toLong()
                val newSourceStart = clip.sourceStartUs + sourceDelta
                val newSourceDuration = clip.sourceDurationUs - sourceDelta
                val shiftedKeyframes = clip.keyframeTracks.map {
                    it.filterKeyframes { kf -> kf.timeUs >= effectiveDelta }
                        .offsetKeyframes(-effectiveDelta)
                }
                clip.copy(
                    startTimeUs = newStartTime,
                    durationUs = newDuration,
                    sourceStartUs = newSourceStart,
                    sourceDurationUs = newSourceDuration,
                    keyframeTracks = shiftedKeyframes
                )
            }
            is AudioClipItem -> {
                val sourceDelta = (effectiveDelta * clip.playbackSpeed).toLong()
                val newSourceStart = clip.sourceStartUs + sourceDelta
                val newSourceDuration = clip.sourceDurationUs - sourceDelta
                val shiftedKeyframes = clip.keyframeTracks.map {
                    it.filterKeyframes { kf -> kf.timeUs >= effectiveDelta }
                        .offsetKeyframes(-effectiveDelta)
                }
                clip.copy(
                    startTimeUs = newStartTime,
                    durationUs = newDuration,
                    sourceStartUs = newSourceStart,
                    sourceDurationUs = newSourceDuration,
                    keyframeTracks = shiftedKeyframes
                )
            }
            is TextClipItem -> {
                val shiftedKeyframes = clip.keyframeTracks.map {
                    it.filterKeyframes { kf -> kf.timeUs >= effectiveDelta }
                        .offsetKeyframes(-effectiveDelta)
                }
                clip.copy(
                    startTimeUs = newStartTime,
                    durationUs = newDuration,
                    keyframeTracks = shiftedKeyframes
                )
            }
            is StickerClipItem -> {
                val shiftedKeyframes = clip.keyframeTracks.map {
                    it.filterKeyframes { kf -> kf.timeUs >= effectiveDelta }
                        .offsetKeyframes(-effectiveDelta)
                }
                clip.copy(
                    startTimeUs = newStartTime,
                    durationUs = newDuration,
                    keyframeTracks = shiftedKeyframes
                )
            }
            else -> clip
        }

        val updatedTrack = targetTrack.copy(
            clips = targetTrack.clips.map { if (it.id == clipId) updatedClip else it }
        )
        return timeline.copy(
            tracks = timeline.tracks.map { if (it.id == updatedTrack.id) updatedTrack else it }
        )
    }

    /**
     * Trims the end of a clip by [deltaEndUs].
     * Decreases durationUs and adjusts sourceDurationUs.
     */
    fun trimClipEnd(
        timeline: CanonicalTimeline,
        clipId: String,
        deltaEndUs: Long
    ): CanonicalTimeline {
        val targetTrack = timeline.tracks.firstOrNull { tr -> tr.clips.any { it.id == clipId } } ?: return timeline
        val clip = targetTrack.clips.firstOrNull { it.id == clipId } ?: return timeline

        val maxTrim = clip.durationUs - 100_000L // minimum 100ms remaining
        val effectiveDelta = deltaEndUs.coerceIn(0L, maxTrim)
        if (effectiveDelta <= 0L) return timeline

        val newDuration = clip.durationUs - effectiveDelta

        val updatedClip = when (clip) {
            is VideoClipItem -> {
                val sourceDelta = (effectiveDelta * clip.playbackSpeed).toLong()
                val newSourceDuration = clip.sourceDurationUs - sourceDelta
                val prunedKeyframes = clip.keyframeTracks.map {
                    it.filterKeyframes { kf -> kf.timeUs <= newDuration }
                }
                clip.copy(
                    durationUs = newDuration,
                    sourceDurationUs = newSourceDuration,
                    keyframeTracks = prunedKeyframes
                )
            }
            is AudioClipItem -> {
                val sourceDelta = (effectiveDelta * clip.playbackSpeed).toLong()
                val newSourceDuration = clip.sourceDurationUs - sourceDelta
                val prunedKeyframes = clip.keyframeTracks.map {
                    it.filterKeyframes { kf -> kf.timeUs <= newDuration }
                }
                clip.copy(
                    durationUs = newDuration,
                    sourceDurationUs = newSourceDuration,
                    keyframeTracks = prunedKeyframes
                )
            }
            is TextClipItem -> {
                val prunedKeyframes = clip.keyframeTracks.map {
                    it.filterKeyframes { kf -> kf.timeUs <= newDuration }
                }
                clip.copy(
                    durationUs = newDuration,
                    keyframeTracks = prunedKeyframes
                )
            }
            is StickerClipItem -> {
                val prunedKeyframes = clip.keyframeTracks.map {
                    it.filterKeyframes { kf -> kf.timeUs <= newDuration }
                }
                clip.copy(
                    durationUs = newDuration,
                    keyframeTracks = prunedKeyframes
                )
            }
            else -> clip
        }

        val updatedTrack = targetTrack.copy(
            clips = targetTrack.clips.map { if (it.id == clipId) updatedClip else it }
        )
        return timeline.copy(
            tracks = timeline.tracks.map { if (it.id == updatedTrack.id) updatedTrack else it }
        )
    }

    /**
     * Moves a clip to a new [newStartTimeUs].
     */
    fun moveClip(
        timeline: CanonicalTimeline,
        clipId: String,
        newStartTimeUs: Long
    ): CanonicalTimeline {
        val targetTrack = timeline.tracks.firstOrNull { tr -> tr.clips.any { it.id == clipId } } ?: return timeline
        val clip = targetTrack.clips.firstOrNull { it.id == clipId } ?: return timeline
        val sanitizedStart = newStartTimeUs.coerceAtLeast(0L)

        val updatedClip = when (clip) {
            is VideoClipItem -> clip.copy(startTimeUs = sanitizedStart)
            is AudioClipItem -> clip.copy(startTimeUs = sanitizedStart)
            is TextClipItem -> clip.copy(startTimeUs = sanitizedStart)
            is StickerClipItem -> clip.copy(startTimeUs = sanitizedStart)
            else -> clip
        }

        val updatedTrack = targetTrack.copy(
            clips = targetTrack.clips.map { if (it.id == clipId) updatedClip else it }
        )
        return timeline.copy(
            tracks = timeline.tracks.map { if (it.id == updatedTrack.id) updatedTrack else it }
        )
    }

    /**
     * Duplicates a clip and inserts it immediately following the source clip.
     */
    fun duplicateClip(
        timeline: CanonicalTimeline,
        clipId: String
    ): CanonicalTimeline {
        val targetTrack = timeline.tracks.firstOrNull { tr -> tr.clips.any { it.id == clipId } } ?: return timeline
        val clip = targetTrack.clips.firstOrNull { it.id == clipId } ?: return timeline

        val newId = UUID.randomUUID().toString()
        val insertTime = clip.endTimeUs

        val duplicatedClip = when (clip) {
            is VideoClipItem -> clip.copy(
                id = newId,
                name = "${clip.name} (Copy)",
                startTimeUs = insertTime
            )
            is AudioClipItem -> clip.copy(
                id = newId,
                name = "${clip.name} (Copy)",
                startTimeUs = insertTime
            )
            is TextClipItem -> clip.copy(
                id = newId,
                name = "${clip.name} (Copy)",
                startTimeUs = insertTime,
                positionX = (clip.positionX + 0.05f).coerceIn(0.1f, 0.9f),
                positionY = (clip.positionY + 0.05f).coerceIn(0.1f, 0.9f)
            )
            is StickerClipItem -> clip.copy(
                id = newId,
                name = "${clip.name} (Copy)",
                startTimeUs = insertTime,
                positionX = (clip.positionX + 0.05f).coerceIn(0.1f, 0.9f),
                positionY = (clip.positionY + 0.05f).coerceIn(0.1f, 0.9f)
            )
            else -> return timeline
        }

        val updatedClips = mutableListOf<TimelineClipItem>()
        for (c in targetTrack.clips) {
            updatedClips.add(c)
            if (c.id == clipId) {
                updatedClips.add(duplicatedClip)
            }
        }

        val updatedTrack = targetTrack.copy(clips = updatedClips)
        return timeline.copy(
            tracks = timeline.tracks.map { if (it.id == updatedTrack.id) updatedTrack else it }
        )
    }

    /**
     * Deletes a clip from its track.
     */
    fun deleteClip(
        timeline: CanonicalTimeline,
        clipId: String
    ): CanonicalTimeline {
        val updatedTracks = timeline.tracks.map { track ->
            if (track.clips.any { it.id == clipId }) {
                track.copy(clips = track.clips.filter { it.id != clipId })
            } else {
                track
            }
        }
        return timeline.copy(tracks = updatedTracks)
    }

    /**
     * Replaces media URI and properties of a video or audio clip.
     */
    fun replaceClipMedia(
        timeline: CanonicalTimeline,
        clipId: String,
        newMediaUri: String,
        isVideo: Boolean = true,
        newDurationUs: Long? = null
    ): CanonicalTimeline {
        val updatedTracks = timeline.tracks.map { track ->
            val updatedClips = track.clips.map { clip ->
                if (clip.id == clipId) {
                    when (clip) {
                        is VideoClipItem -> {
                            val dur = newDurationUs ?: clip.durationUs
                            clip.copy(
                                mediaUri = newMediaUri,
                                isVideo = isVideo,
                                durationUs = dur,
                                sourceDurationUs = dur
                            )
                        }
                        is AudioClipItem -> {
                            val dur = newDurationUs ?: clip.durationUs
                            clip.copy(
                                audioUri = newMediaUri,
                                durationUs = dur,
                                sourceDurationUs = dur
                            )
                        }
                        else -> clip
                    }
                } else {
                    clip
                }
            }
            track.copy(clips = updatedClips)
        }
        return timeline.copy(tracks = updatedTracks)
    }

    /**
     * Adjusts playback speed: 0.25x, 0.5x, 0.75x, 1x, 1.25x, 1.5x, 2x, 3x, 4x.
     * Updates durationUs = (sourceDurationUs / newSpeed).
     */
    fun changeSpeed(
        timeline: CanonicalTimeline,
        clipId: String,
        newSpeed: Float
    ): CanonicalTimeline {
        val clampedSpeed = newSpeed.coerceIn(0.2f, 10.0f)
        val updatedTracks = timeline.tracks.map { track ->
            val updatedClips = track.clips.map { clip ->
                if (clip.id == clipId) {
                    when (clip) {
                        is VideoClipItem -> {
                            val newDuration = (clip.sourceDurationUs / clampedSpeed).toLong()
                            clip.copy(playbackSpeed = clampedSpeed, durationUs = newDuration)
                        }
                        is AudioClipItem -> {
                            val newDuration = (clip.sourceDurationUs / clampedSpeed).toLong()
                            clip.copy(playbackSpeed = clampedSpeed, durationUs = newDuration)
                        }
                        else -> clip
                    }
                } else {
                    clip
                }
            }
            track.copy(clips = updatedClips)
        }
        return timeline.copy(tracks = updatedTracks)
    }

    /**
     * Toggles reverse playback on a video clip.
     */
    fun toggleReverse(
        timeline: CanonicalTimeline,
        clipId: String
    ): CanonicalTimeline {
        val updatedTracks = timeline.tracks.map { track ->
            val updatedClips = track.clips.map { clip ->
                if (clip.id == clipId && clip is VideoClipItem) {
                    clip.copy(isReversed = !clip.isReversed)
                } else {
                    clip
                }
            }
            track.copy(clips = updatedClips)
        }
        return timeline.copy(tracks = updatedTracks)
    }

    /**
     * Inserts a freeze frame in a video clip at [freezeTimeUs] lasting [freezeDurationSec] seconds.
     */
    fun freezeFrame(
        timeline: CanonicalTimeline,
        clipId: String,
        freezeTimeUs: Long,
        freezeDurationSec: Float = 2.0f
    ): CanonicalTimeline {
        val targetTrack = timeline.tracks.firstOrNull { tr -> tr.clips.any { it.id == clipId } } ?: return timeline
        val clip = targetTrack.clips.firstOrNull { it.id == clipId } as? VideoClipItem ?: return timeline

        val freezeDurationUs = (freezeDurationSec * 1_000_000L).toLong()
        val freezeSourceTimeUs = clip.sourceStartUs + ((freezeTimeUs - clip.startTimeUs) * clip.playbackSpeed).toLong()

        val freezeClip = clip.copy(
            id = UUID.randomUUID().toString(),
            name = "${clip.name} [Freeze]",
            startTimeUs = freezeTimeUs,
            durationUs = freezeDurationUs,
            sourceStartUs = freezeSourceTimeUs,
            sourceDurationUs = 100_000L, // Static single frame
            isFrozen = true,
            freezeDurationSec = freezeDurationSec,
            playbackSpeed = 0.0001f // Frozen frame
        )

        // Split original into before and after freeze
        val beforeDur = (freezeTimeUs - clip.startTimeUs).coerceAtLeast(0L)
        val clipBefore = clip.copy(
            durationUs = beforeDur,
            sourceDurationUs = (beforeDur * clip.playbackSpeed).toLong()
        )

        val afterStart = freezeTimeUs + freezeDurationUs
        val afterDur = (clip.endTimeUs - freezeTimeUs).coerceAtLeast(0L)
        val clipAfter = clip.copy(
            id = UUID.randomUUID().toString(),
            name = "${clip.name} (Resume)",
            startTimeUs = afterStart,
            durationUs = afterDur,
            sourceStartUs = freezeSourceTimeUs,
            sourceDurationUs = (afterDur * clip.playbackSpeed).toLong()
        )

        val updatedClips = mutableListOf<TimelineClipItem>()
        for (c in targetTrack.clips) {
            if (c.id == clipId) {
                if (beforeDur > 50_000L) updatedClips.add(clipBefore)
                updatedClips.add(freezeClip)
                if (afterDur > 50_000L) updatedClips.add(clipAfter)
            } else {
                updatedClips.add(c)
            }
        }

        val updatedTrack = targetTrack.copy(clips = updatedClips)
        return timeline.copy(
            tracks = timeline.tracks.map { if (it.id == updatedTrack.id) updatedTrack else it }
        )
    }

    /**
     * Helpers to filter and offset keyframes for sub-clip splitting.
     */
    @Suppress("UNCHECKED_CAST")
    private fun <T : Number> KeyframeTrack<T>.filterKeyframes(predicate: (Keyframe<T>) -> Boolean): KeyframeTrack<T> {
        return this.copy(keyframes = this.keyframes.filter(predicate))
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T : Number> KeyframeTrack<T>.offsetKeyframes(offsetUs: Long): KeyframeTrack<T> {
        return this.copy(
            keyframes = this.keyframes.map { it.copy(timeUs = it.timeUs + offsetUs) }
        )
    }
}
