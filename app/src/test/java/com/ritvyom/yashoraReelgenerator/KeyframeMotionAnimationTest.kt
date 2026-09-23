package com.ritvyom.yashoraReelgenerator

import com.ritvyom.yashoraReelgenerator.core.model.AnimatableProperty
import com.ritvyom.yashoraReelgenerator.core.model.BezierControlPoints
import com.ritvyom.yashoraReelgenerator.core.model.CanvasAspectRatio
import com.ritvyom.yashoraReelgenerator.core.model.CanvasConfig
import com.ritvyom.yashoraReelgenerator.core.model.CanonicalTimeline
import com.ritvyom.yashoraReelgenerator.core.model.Keyframe
import com.ritvyom.yashoraReelgenerator.core.model.KeyframeInterpolation
import com.ritvyom.yashoraReelgenerator.core.model.KeyframeTrack
import com.ritvyom.yashoraReelgenerator.core.model.NormalizedCropRect
import com.ritvyom.yashoraReelgenerator.core.model.TextClipItem
import com.ritvyom.yashoraReelgenerator.core.model.TimelineTrack
import com.ritvyom.yashoraReelgenerator.core.model.TrackType
import com.ritvyom.yashoraReelgenerator.core.model.VideoClipItem
import com.ritvyom.yashoraReelgenerator.core.rendering.FrameTime
import com.ritvyom.yashoraReelgenerator.core.rendering.RenderContext
import com.ritvyom.yashoraReelgenerator.engine.KeyframeEvaluator
import com.ritvyom.yashoraReelgenerator.engine.TimelineEvaluator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 4 Comprehensive Master Test Suite:
 * Advanced Keyframe & Motion Animation System.
 *
 * Verifies:
 * 1. Deterministic Easing & Interpolation (STEP, LINEAR, EASE_IN, EASE_OUT, EASE_IN_OUT, CUBIC_BEZIER)
 * 2. Clip-local time conversion with start offsets and speed
 * 3. Multi-property simultaneous animation (Position, Scale, Rotation, Opacity, Anchor, Zoom, Crop)
 * 4. Scrubbing sequence without accumulated drift
 * 5. Frame rate independence (24, 30, 60 FPS)
 * 6. Preview vs. Export numerical parity across multi-layer scene
 * 7. Keyframe edge cases (0 keyframes, 1 keyframe, duplicates, extreme rotations, scale=0, opacity=0)
 */
class KeyframeMotionAnimationTest {

    @Test
    fun testInterpolationTypes_mathematicalPrecision() {
        val durationUs = 1_000_000L // 1 second

        // 1. STEP / HOLD
        val stepTrack = KeyframeTrack(
            property = AnimatableProperty.POSITION_X,
            keyframes = listOf(
                Keyframe(0L, 0.0f, KeyframeInterpolation.STEP),
                Keyframe(durationUs, 100.0f, KeyframeInterpolation.STEP)
            ),
            defaultValue = 0.0f
        )
        assertEquals(0.0f, stepTrack.evaluate(0L), 1e-5f)
        assertEquals(0.0f, stepTrack.evaluate(500_000L), 1e-5f) // Stays at v0
        assertEquals(0.0f, stepTrack.evaluate(999_999L), 1e-5f) // Stays at v0
        assertEquals(100.0f, stepTrack.evaluate(durationUs), 1e-5f) // Jumps to v1

        // 2. LINEAR
        val linearTrack = KeyframeTrack(
            property = AnimatableProperty.SCALE_X,
            keyframes = listOf(
                Keyframe(0L, 1.0f, KeyframeInterpolation.LINEAR),
                Keyframe(durationUs, 2.0f, KeyframeInterpolation.LINEAR)
            ),
            defaultValue = 1.0f
        )
        assertEquals(1.0f, linearTrack.evaluate(0L), 1e-5f)
        assertEquals(1.5f, linearTrack.evaluate(500_000L), 1e-5f)
        assertEquals(1.75f, linearTrack.evaluate(750_000L), 1e-5f)
        assertEquals(2.0f, linearTrack.evaluate(durationUs), 1e-5f)

        // 3. EASE_IN (Quadratic: t^2)
        val easeInTrack = KeyframeTrack(
            property = AnimatableProperty.OPACITY,
            keyframes = listOf(
                Keyframe(0L, 0.0f, KeyframeInterpolation.EASE_IN),
                Keyframe(durationUs, 1.0f, KeyframeInterpolation.EASE_IN)
            ),
            defaultValue = 0.0f
        )
        // At t=0.5, t^2 = 0.25
        assertEquals(0.25f, easeInTrack.evaluate(500_000L), 1e-4f)

        // 4. EASE_OUT (Quadratic: t * (2 - t))
        val easeOutTrack = KeyframeTrack(
            property = AnimatableProperty.OPACITY,
            keyframes = listOf(
                Keyframe(0L, 0.0f, KeyframeInterpolation.EASE_OUT),
                Keyframe(durationUs, 1.0f, KeyframeInterpolation.EASE_OUT)
            ),
            defaultValue = 0.0f
        )
        // At t=0.5, 0.5 * 1.5 = 0.75
        assertEquals(0.75f, easeOutTrack.evaluate(500_000L), 1e-4f)

        // 5. EASE_IN_OUT
        val easeInOutTrack = KeyframeTrack(
            property = AnimatableProperty.ROTATION_DEGREES,
            keyframes = listOf(
                Keyframe(0L, 0.0f, KeyframeInterpolation.EASE_IN_OUT),
                Keyframe(durationUs, 100.0f, KeyframeInterpolation.EASE_IN_OUT)
            ),
            defaultValue = 0.0f
        )
        assertEquals(0.0f, easeInOutTrack.evaluate(0L), 1e-5f)
        assertEquals(50.0f, easeInOutTrack.evaluate(500_000L), 1e-4f) // Symmetric at mid-point
        assertEquals(100.0f, easeInOutTrack.evaluate(durationUs), 1e-5f)

        // 6. CUBIC_BEZIER (Newton-Raphson Solver)
        val bezierTrack = KeyframeTrack(
            property = AnimatableProperty.ZOOM,
            keyframes = listOf(
                Keyframe(0L, 1.0f, KeyframeInterpolation.CUBIC_BEZIER, BezierControlPoints.EASE),
                Keyframe(durationUs, 2.0f, KeyframeInterpolation.CUBIC_BEZIER, BezierControlPoints.EASE)
            ),
            defaultValue = 1.0f
        )
        assertEquals(1.0f, bezierTrack.evaluate(0L), 1e-4f)
        val midVal = bezierTrack.evaluate(500_000L)
        assertTrue("Ease curve midVal should be between 1.5 and 2.0", midVal in 1.5f..2.0f)
        assertEquals(2.0f, bezierTrack.evaluate(durationUs), 1e-4f)
    }

    @Test
    fun testClipLocalTimeConversion() {
        val clipStartTimelineUs = 5_000_000L // Clip starts at 5s on main timeline
        val speed1x = 1.0f
        val speed2x = 2.0f

        // 1x Speed
        assertEquals(0L, KeyframeEvaluator.timelineTimeToLocalClipTime(5_000_000L, clipStartTimelineUs, speed1x))
        assertEquals(2_000_000L, KeyframeEvaluator.timelineTimeToLocalClipTime(7_000_000L, clipStartTimelineUs, speed1x))
        assertEquals(7_000_000L, KeyframeEvaluator.localClipTimeToTimelineTime(2_000_000L, clipStartTimelineUs, speed1x))

        // 2x Speed: 2 seconds of local media time elapsed in 1 second of timeline time
        assertEquals(2_000_000L, KeyframeEvaluator.timelineTimeToLocalClipTime(6_000_000L, clipStartTimelineUs, speed2x))
        assertEquals(6_000_000L, KeyframeEvaluator.localClipTimeToTimelineTime(2_000_000L, clipStartTimelineUs, speed2x))
    }

    @Test
    fun testMultiPropertySimultaneousAnimation() {
        val totalUs = 4_000_000L // 4 seconds

        // Position Track: 0s -> 0.5 (center), 2s -> 0.8 (right), 4s -> 0.5 (center)
        val posTrack = KeyframeTrack(
            property = AnimatableProperty.POSITION_X,
            keyframes = listOf(
                Keyframe(0L, 0.5f, KeyframeInterpolation.LINEAR),
                Keyframe(2_000_000L, 0.8f, KeyframeInterpolation.LINEAR),
                Keyframe(4_000_000L, 0.5f, KeyframeInterpolation.LINEAR)
            ),
            defaultValue = 0.5f
        )

        // Scale Track: 0s -> 1.0, 2s -> 1.4, 4s -> 1.0
        val scaleTrack = KeyframeTrack(
            property = AnimatableProperty.SCALE_X,
            keyframes = listOf(
                Keyframe(0L, 1.0f, KeyframeInterpolation.LINEAR),
                Keyframe(2_000_000L, 1.4f, KeyframeInterpolation.LINEAR),
                Keyframe(4_000_000L, 1.0f, KeyframeInterpolation.LINEAR)
            ),
            defaultValue = 1.0f
        )

        // Rotation Track: 0s -> 0 deg, 2s -> 15 deg, 4s -> 0 deg
        val rotTrack = KeyframeTrack(
            property = AnimatableProperty.ROTATION_DEGREES,
            keyframes = listOf(
                Keyframe(0L, 0.0f, KeyframeInterpolation.LINEAR),
                Keyframe(2_000_000L, 15.0f, KeyframeInterpolation.LINEAR),
                Keyframe(4_000_000L, 0.0f, KeyframeInterpolation.LINEAR)
            ),
            defaultValue = 0.0f
        )

        // Opacity Track: 0s -> 1.0, 2s -> 0.8, 4s -> 1.0
        val opacTrack = KeyframeTrack(
            property = AnimatableProperty.OPACITY,
            keyframes = listOf(
                Keyframe(0L, 1.0f, KeyframeInterpolation.LINEAR),
                Keyframe(2_000_000L, 0.8f, KeyframeInterpolation.LINEAR),
                Keyframe(4_000_000L, 1.0f, KeyframeInterpolation.LINEAR)
            ),
            defaultValue = 1.0f
        )

        val clip = VideoClipItem(
            id = "multi_prop_clip",
            trackId = "main_track",
            name = "Multi Prop Test Clip",
            mediaUri = "file:///sample.mp4",
            startTimeUs = 0L,
            durationUs = 5_000_000L,
            sourceDurationUs = 5_000_000L,
            keyframeTracks = listOf(posTrack, scaleTrack, rotTrack, opacTrack)
        )

        val timeline = CanonicalTimeline(
            tracks = listOf(TimelineTrack(id = "main_track", name = "Main Track", type = TrackType.MAIN_VIDEO, clips = listOf(clip)))
        )
        val evaluator = TimelineEvaluator(timeline)
        val renderCtx = RenderContext(canvasConfig = CanvasConfig(), viewportWidth = 1080, viewportHeight = 1920)

        // Evaluate at t = 0s
        val node0 = evaluator.evaluateNodesForTime(FrameTime(0L, 0, 30, totalUs), renderCtx).first { it.id == "multi_prop_clip" }
        assertEquals(0.0f, node0.transform.translationX, 1e-4f) // (0.5 - 0.5)
        assertEquals(1.0f, node0.transform.scaleX, 1e-4f)
        assertEquals(0.0f, node0.transform.rotationDegrees, 1e-4f)
        assertEquals(1.0f, node0.transform.opacity, 1e-4f)

        // Evaluate at t = 2s
        val node2 = evaluator.evaluateNodesForTime(FrameTime(2_000_000L, 60, 30, totalUs), renderCtx).first { it.id == "multi_prop_clip" }
        assertEquals(0.3f, node2.transform.translationX, 1e-4f) // (0.8 - 0.5)
        assertEquals(1.4f, node2.transform.scaleX, 1e-4f)
        assertEquals(15.0f, node2.transform.rotationDegrees, 1e-4f)
        assertEquals(0.8f, node2.transform.opacity, 1e-4f)

        // Evaluate at t = 4s
        val node4 = evaluator.evaluateNodesForTime(FrameTime(4_000_000L, 120, 30, totalUs), renderCtx).first { it.id == "multi_prop_clip" }
        assertEquals(0.0f, node4.transform.translationX, 1e-4f)
        assertEquals(1.0f, node4.transform.scaleX, 1e-4f)
        assertEquals(0.0f, node4.transform.rotationDegrees, 1e-4f)
        assertEquals(1.0f, node4.transform.opacity, 1e-4f)
    }

    @Test
    fun testArbitraryTimelineScrubbing_noAccumulatedDrift() {
        val totalUs = 8_000_000L

        val scaleTrack = KeyframeTrack(
            property = AnimatableProperty.SCALE_X,
            keyframes = listOf(
                Keyframe(0L, 1.0f, KeyframeInterpolation.LINEAR),
                Keyframe(totalUs, 3.0f, KeyframeInterpolation.LINEAR)
            ),
            defaultValue = 1.0f
        )
        val clip = VideoClipItem(
            id = "scrub_clip",
            trackId = "main",
            name = "Scrubbing Test Clip",
            mediaUri = "file:///scrub.mp4",
            startTimeUs = 0L,
            durationUs = totalUs,
            sourceDurationUs = totalUs,
            keyframeTracks = listOf(scaleTrack)
        )
        val evaluator = TimelineEvaluator(CanonicalTimeline(tracks = listOf(TimelineTrack(id = "main", name = "Main", type = TrackType.MAIN_VIDEO, clips = listOf(clip)))))
        val renderCtx = RenderContext(canvasConfig = CanvasConfig(), viewportWidth = 1080, viewportHeight = 1920)

        // Helper to evaluate at an exact timestamp
        fun evaluateScaleAt(timeUs: Long): Float {
            return evaluator.evaluateNodesForTime(FrameTime(timeUs, (timeUs / 33333L).toInt(), 30, totalUs), renderCtx)
                .first { it.id == "scrub_clip" }.transform.scaleX
        }

        // Theoretical values: 1.0 + (timeUs / 8s) * 2.0
        val expectedAt0s = 1.0f
        val expectedAt4s = 2.0f
        val expectedAt1s = 1.25f
        val expectedAt7s = 2.75f
        val expectedAt2_5s = 1.625f

        // Non-sequential jumping: 0s -> 4s -> 1s -> 7s -> 2.5s -> 0s
        assertEquals(expectedAt0s, evaluateScaleAt(0L), 1e-5f)
        assertEquals(expectedAt4s, evaluateScaleAt(4_000_000L), 1e-5f)
        assertEquals(expectedAt1s, evaluateScaleAt(1_000_000L), 1e-5f)
        assertEquals(expectedAt7s, evaluateScaleAt(7_000_000L), 1e-5f)
        assertEquals(expectedAt2_5s, evaluateScaleAt(2_500_000L), 1e-5f)
        assertEquals(expectedAt0s, evaluateScaleAt(0L), 1e-5f) // Absolute return to origin: zero drift
    }

    @Test
    fun testFrameRateIndependence_24_30_60_FPS() {
        val totalUs = 5_000_000L
        val rotTrack = KeyframeTrack(
            property = AnimatableProperty.ROTATION_DEGREES,
            keyframes = listOf(
                Keyframe(0L, 0.0f, KeyframeInterpolation.LINEAR),
                Keyframe(totalUs, 360.0f, KeyframeInterpolation.LINEAR)
            ),
            defaultValue = 0.0f
        )
        val clip = VideoClipItem(
            id = "fps_clip",
            trackId = "main",
            name = "FPS Test Clip",
            mediaUri = "file:///fps.mp4",
            startTimeUs = 0L,
            durationUs = totalUs,
            sourceDurationUs = totalUs,
            keyframeTracks = listOf(rotTrack)
        )
        val evaluator = TimelineEvaluator(CanonicalTimeline(tracks = listOf(TimelineTrack(id = "main", name = "Main", type = TrackType.MAIN_VIDEO, clips = listOf(clip)))))
        val renderCtx = RenderContext(canvasConfig = CanvasConfig(), viewportWidth = 1080, viewportHeight = 1920)

        // Target timestamp: 1.25s (1_250_000 us) -> Expected rotation: 90.0 degrees
        val targetUs = 1_250_000L
        val frame24 = FrameTime(targetUs, (targetUs / (1_000_000L / 24)).toInt(), 24, totalUs)
        val frame30 = FrameTime(targetUs, (targetUs / (1_000_000L / 30)).toInt(), 30, totalUs)
        val frame60 = FrameTime(targetUs, (targetUs / (1_000_000L / 60)).toInt(), 60, totalUs)

        val rot24 = evaluator.evaluateNodesForTime(frame24, renderCtx).first { it.id == "fps_clip" }.transform.rotationDegrees
        val rot30 = evaluator.evaluateNodesForTime(frame30, renderCtx).first { it.id == "fps_clip" }.transform.rotationDegrees
        val rot60 = evaluator.evaluateNodesForTime(frame60, renderCtx).first { it.id == "fps_clip" }.transform.rotationDegrees

        assertEquals(90.0f, rot24, 1e-4f)
        assertEquals(90.0f, rot30, 1e-4f)
        assertEquals(90.0f, rot60, 1e-4f)
        assertEquals(rot24, rot30, 1e-5f)
        assertEquals(rot30, rot60, 1e-5f)
    }

    @Test
    fun testPreviewAndExportNumericalParity_Phase4Master() {
        val durationUs = 6_000_000L // 6 seconds

        // 1. Main Video with Zoom and Crop Animation
        val zoomTrack = KeyframeTrack(
            property = AnimatableProperty.ZOOM,
            keyframes = listOf(
                Keyframe(0L, 1.0f, KeyframeInterpolation.EASE_IN_OUT),
                Keyframe(3_000_000L, 1.5f, KeyframeInterpolation.EASE_IN_OUT),
                Keyframe(6_000_000L, 1.0f, KeyframeInterpolation.EASE_IN_OUT)
            ),
            defaultValue = 1.0f
        )
        val cropLeftTrack = KeyframeTrack(
            property = AnimatableProperty.CROP_LEFT,
            keyframes = listOf(
                Keyframe(0L, 0.0f, KeyframeInterpolation.LINEAR),
                Keyframe(3_000_000L, 0.1f, KeyframeInterpolation.LINEAR)
            ),
            defaultValue = 0.0f
        )
        val mainClip = VideoClipItem(
            id = "main_clip_p4",
            trackId = "main",
            name = "Main Video",
            mediaUri = "file:///main.mp4",
            startTimeUs = 0L,
            durationUs = durationUs,
            sourceDurationUs = durationUs,
            keyframeTracks = listOf(zoomTrack, cropLeftTrack)
        )

        // 2. PIP Video with Animated Position, Rotation, Anchor, and Opacity
        val pipPosTrack = KeyframeTrack(
            property = AnimatableProperty.POSITION_X,
            keyframes = listOf(
                Keyframe(0L, 0.2f, KeyframeInterpolation.CUBIC_BEZIER, BezierControlPoints.EASE),
                Keyframe(3_000_000L, 0.8f, KeyframeInterpolation.CUBIC_BEZIER, BezierControlPoints.EASE)
            ),
            defaultValue = 0.2f
        )
        val pipRotTrack = KeyframeTrack(
            property = AnimatableProperty.ROTATION_DEGREES,
            keyframes = listOf(
                Keyframe(0L, -15.0f, KeyframeInterpolation.LINEAR),
                Keyframe(3_000_000L, 45.0f, KeyframeInterpolation.LINEAR)
            ),
            defaultValue = 0.0f
        )
        val pipAnchorTrack = KeyframeTrack(
            property = AnimatableProperty.ANCHOR_X,
            keyframes = listOf(
                Keyframe(0L, 0.0f, KeyframeInterpolation.LINEAR),
                Keyframe(3_000_000L, 1.0f, KeyframeInterpolation.LINEAR)
            ),
            defaultValue = 0.5f
        )
        val pipClip = VideoClipItem(
            id = "pip_clip_p4",
            trackId = "pip",
            name = "PIP Reaction",
            mediaUri = "file:///pip.mp4",
            startTimeUs = 1_000_000L,
            durationUs = 4_000_000L,
            sourceDurationUs = 4_000_000L,
            zIndex = 2,
            keyframeTracks = listOf(pipPosTrack, pipRotTrack, pipAnchorTrack)
        )

        // 3. Text Clip with Animated Opacity
        val textOpacTrack = KeyframeTrack(
            property = AnimatableProperty.OPACITY,
            keyframes = listOf(
                Keyframe(0L, 0.0f, KeyframeInterpolation.LINEAR),
                Keyframe(500_000L, 1.0f, KeyframeInterpolation.LINEAR),
                Keyframe(2_500_000L, 1.0f, KeyframeInterpolation.LINEAR),
                Keyframe(3_000_000L, 0.0f, KeyframeInterpolation.LINEAR)
            ),
            defaultValue = 1.0f
        )
        val textClip = TextClipItem(
            id = "text_clip_p4",
            trackId = "text",
            name = "Title Text",
            text = "Yashora Pro",
            startTimeUs = 500_000L,
            durationUs = 3_000_000L,
            keyframeTracks = listOf(textOpacTrack)
        )

        val canvasConfig = CanvasConfig(
            aspectRatio = CanvasAspectRatio.RATIO_9_16,
            outputWidth = 1080,
            outputHeight = 1920
        )
        val timeline = CanonicalTimeline(
            tracks = listOf(
                TimelineTrack(id = "main", name = "Main Track", type = TrackType.MAIN_VIDEO, clips = listOf(mainClip)),
                TimelineTrack(id = "pip", name = "PIP Track", type = TrackType.OVERLAY_VIDEO_PIP, clips = listOf(pipClip)),
                TimelineTrack(id = "text", name = "Text Track", type = TrackType.TEXT_TITLE, clips = listOf(textClip))
            ),
            canvasConfig = canvasConfig,
            timebaseFps = 30
        )

        val evaluator = TimelineEvaluator(timeline)
        val previewCtx = RenderContext(canvasConfig = canvasConfig, viewportWidth = 1080, viewportHeight = 1920, isOfflineExport = false)
        val exportCtx = RenderContext(canvasConfig = canvasConfig, viewportWidth = 1080, viewportHeight = 1920, isOfflineExport = true)

        // Mandatory timestamps: 0s, 0.25s, 0.5s, 1s, 1.5s, 2s, 3s, 4s, 5s
        val checkTimestampsUs = listOf(
            0L,
            250_000L,
            500_000L,
            1_000_000L,
            1_500_000L,
            2_000_000L,
            3_000_000L,
            4_000_000L,
            5_000_000L
        )

        for (timeUs in checkTimestampsUs) {
            val frameTime = FrameTime(timeUs, (timeUs / (1_000_000L / 30)).toInt(), 30, durationUs)
            val pNodes = evaluator.evaluateNodesForTime(frameTime, previewCtx)
            val eNodes = evaluator.evaluateNodesForTime(frameTime, exportCtx)

            assertEquals("Node count must match at ${timeUs}us", pNodes.size, eNodes.size)

            for (i in pNodes.indices) {
                val pn = pNodes[i]
                val en = eNodes[i]

                assertEquals("Node id must match at index $i", pn.id, en.id)
                assertEquals("Z-Index must match at index $i", pn.zIndex, en.zIndex)

                // Numerical Parity Verifications:
                assertEquals("TranslationX parity at ${timeUs}us for ${pn.id}", pn.transform.translationX, en.transform.translationX, 1e-5f)
                assertEquals("TranslationY parity at ${timeUs}us for ${pn.id}", pn.transform.translationY, en.transform.translationY, 1e-5f)
                assertEquals("ScaleX parity at ${timeUs}us for ${pn.id}", pn.transform.scaleX, en.transform.scaleX, 1e-5f)
                assertEquals("ScaleY parity at ${timeUs}us for ${pn.id}", pn.transform.scaleY, en.transform.scaleY, 1e-5f)
                assertEquals("Rotation parity at ${timeUs}us for ${pn.id}", pn.transform.rotationDegrees, en.transform.rotationDegrees, 1e-5f)
                assertEquals("AnchorX parity at ${timeUs}us for ${pn.id}", pn.transform.anchorX, en.transform.anchorX, 1e-5f)
                assertEquals("AnchorY parity at ${timeUs}us for ${pn.id}", pn.transform.anchorY, en.transform.anchorY, 1e-5f)
                assertEquals("Opacity parity at ${timeUs}us for ${pn.id}", pn.transform.opacity, en.transform.opacity, 1e-5f)
                assertEquals("CropLeft parity at ${timeUs}us for ${pn.id}", pn.transform.crop.left, en.transform.crop.left, 1e-5f)
            }
        }
    }

    @Test
    fun testKeyframeEdgeCases() {
        // 1. No keyframes -> fallback defaultValue
        val emptyTrack = KeyframeTrack(property = AnimatableProperty.OPACITY, keyframes = emptyList(), defaultValue = 0.77f)
        assertEquals(0.77f, emptyTrack.evaluate(500_000L), 1e-5f)

        // 2. Exactly 1 keyframe -> returns that value everywhere
        val singleTrack = KeyframeTrack(
            property = AnimatableProperty.ROTATION_DEGREES,
            keyframes = listOf(Keyframe(1_000_000L, 45.0f)),
            defaultValue = 0.0f
        )
        assertEquals(45.0f, singleTrack.evaluate(0L), 1e-5f)
        assertEquals(45.0f, singleTrack.evaluate(1_000_000L), 1e-5f)
        assertEquals(45.0f, singleTrack.evaluate(9_000_000L), 1e-5f)

        // 3. Duplicate timestamps at exactly the same microsecond -> instant step without NaN
        val dupTrack = KeyframeTrack(
            property = AnimatableProperty.POSITION_X,
            keyframes = listOf(
                Keyframe(1_000_000L, 10.0f),
                Keyframe(1_000_000L, 90.0f)
            ),
            defaultValue = 0.0f
        )
        assertEquals(10.0f, dupTrack.evaluate(999_999L), 1e-5f)
        assertEquals(90.0f, dupTrack.evaluate(1_000_000L), 1e-5f)
        assertEquals(90.0f, dupTrack.evaluate(1_000_001L), 1e-5f)

        // 4. Clamping outside boundary ranges
        val boundTrack = KeyframeTrack(
            property = AnimatableProperty.SCALE_X,
            keyframes = listOf(
                Keyframe(1_000_000L, 1.0f),
                Keyframe(3_000_000L, 2.0f)
            ),
            defaultValue = 0.5f
        )
        assertEquals(1.0f, boundTrack.evaluate(0L), 1e-5f) // Clamped to first
        assertEquals(2.0f, boundTrack.evaluate(10_000_000L), 1e-5f) // Clamped to last

        // 5. Extreme rotation beyond 360 degrees and negative values
        val extremeRotTrack = KeyframeTrack(
            property = AnimatableProperty.ROTATION_DEGREES,
            keyframes = listOf(
                Keyframe(0L, -720.0f),
                Keyframe(2_000_000L, 1080.0f)
            ),
            defaultValue = 0.0f
        )
        assertEquals(-720.0f, extremeRotTrack.evaluate(0L), 1e-5f)
        assertEquals(180.0f, extremeRotTrack.evaluate(1_000_000L), 1e-4f) // (-720 + 1080)/2 = 180
        assertEquals(1080.0f, extremeRotTrack.evaluate(2_000_000L), 1e-5f)

        // 6. Scale = 0 mathematically safe
        val zeroScaleTrack = KeyframeTrack(
            property = AnimatableProperty.SCALE_X,
            keyframes = listOf(Keyframe(0L, 0.0f), Keyframe(1_000_000L, 1.0f)),
            defaultValue = 1.0f
        )
        assertEquals(0.0f, zeroScaleTrack.evaluate(0L), 1e-5f)

        // 7. Opacity = 0 and Opacity clamped
        val zeroOpacTrack = KeyframeTrack(
            property = AnimatableProperty.OPACITY,
            keyframes = listOf(Keyframe(0L, 0.0f), Keyframe(1_000_000L, 1.0f)),
            defaultValue = 1.0f
        )
        assertEquals(0.0f, zeroOpacTrack.evaluate(0L), 1e-5f)

        // 8. Backward compatibility: Clip with no keyframes preserves static properties
        val staticClip = VideoClipItem(
            id = "static_clip",
            trackId = "main",
            name = "Static Clip",
            mediaUri = "file:///static.mp4",
            startTimeUs = 0L,
            durationUs = 3_000_000L,
            sourceDurationUs = 3_000_000L,
            positionX = 0.65f,
            positionY = 0.35f,
            scaleX = 1.2f,
            rotationDegrees = 25.0f,
            opacity = 0.85f,
            keyframeTracks = emptyList()
        )
        val timeline = CanonicalTimeline(tracks = listOf(TimelineTrack(id = "main", name = "Main", type = TrackType.MAIN_VIDEO, clips = listOf(staticClip))))
        val evaluator = TimelineEvaluator(timeline)
        val node = evaluator.evaluateNodesForTime(FrameTime(1_500_000L, 45, 30, 3_000_000L), RenderContext(canvasConfig = CanvasConfig(), viewportWidth = 1080, viewportHeight = 1920)).first { it.id == "static_clip" }
        assertEquals(0.15f, node.transform.translationX, 1e-5f) // (0.65 - 0.5)
        assertEquals(-0.15f, node.transform.translationY, 1e-5f) // (0.35 - 0.5)
        assertEquals(1.2f, node.transform.scaleX, 1e-5f)
        assertEquals(25.0f, node.transform.rotationDegrees, 1e-5f)
        assertEquals(0.85f, node.transform.opacity, 1e-5f)
    }
}
