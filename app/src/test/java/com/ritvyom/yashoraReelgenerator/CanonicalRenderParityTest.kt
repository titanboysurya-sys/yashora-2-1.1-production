package com.ritvyom.yashoraReelgenerator

import com.ritvyom.yashoraReelgenerator.core.model.AnimatableProperty
import com.ritvyom.yashoraReelgenerator.core.model.CanvasAspectRatio
import com.ritvyom.yashoraReelgenerator.core.model.CanvasConfig
import com.ritvyom.yashoraReelgenerator.core.model.CanonicalTimeline
import com.ritvyom.yashoraReelgenerator.core.model.Keyframe
import com.ritvyom.yashoraReelgenerator.core.model.KeyframeInterpolation
import com.ritvyom.yashoraReelgenerator.core.model.KeyframeTrack
import com.ritvyom.yashoraReelgenerator.core.model.TimelineTrack
import com.ritvyom.yashoraReelgenerator.core.model.TrackType
import com.ritvyom.yashoraReelgenerator.core.model.VideoClipItem
import com.ritvyom.yashoraReelgenerator.core.rendering.FrameTime
import com.ritvyom.yashoraReelgenerator.core.rendering.RenderContext
import com.ritvyom.yashoraReelgenerator.engine.TimelineEvaluator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifies strict preview/export synchronization and keyframe interpolation parity.
 * Confirms that Preview Output == Exported Output for identical timeline timestamps.
 */
class CanonicalRenderParityTest {

    @Test
    fun testPreviewAndExportEvaluationParity_withKeyframesAndTransforms() {
        val durationUs = 5_000_000L // 5 seconds

        // 1. Define Keyframe Tracks
        // Scale animating from 1.0f at t=0 to 2.0f at t=5s
        val scaleKeyframeTrack = KeyframeTrack(
            property = AnimatableProperty.SCALE_X,
            keyframes = listOf(
                Keyframe(timeUs = 0L, value = 1.0f, interpolation = KeyframeInterpolation.LINEAR),
                Keyframe(timeUs = durationUs, value = 2.0f, interpolation = KeyframeInterpolation.LINEAR)
            ),
            defaultValue = 1.0f
        )

        // Rotation animating from 0 deg at t=0 to 180 deg at t=5s
        val rotationKeyframeTrack = KeyframeTrack(
            property = AnimatableProperty.ROTATION_DEGREES,
            keyframes = listOf(
                Keyframe(timeUs = 0L, value = 0.0f, interpolation = KeyframeInterpolation.LINEAR),
                Keyframe(timeUs = durationUs, value = 180.0f, interpolation = KeyframeInterpolation.LINEAR)
            ),
            defaultValue = 0.0f
        )

        // Brightness animating from 0.0f at t=0 to 0.5f at t=5s
        val brightnessKeyframeTrack = KeyframeTrack(
            property = AnimatableProperty.COLOR_BRIGHTNESS,
            keyframes = listOf(
                Keyframe(timeUs = 0L, value = 0.0f, interpolation = KeyframeInterpolation.LINEAR),
                Keyframe(timeUs = durationUs, value = 0.5f, interpolation = KeyframeInterpolation.LINEAR)
            ),
            defaultValue = 0.0f
        )

        // 2. Create Single Video Clip with Position, Scale, Rotation, and Keyframes
        val testClip = VideoClipItem(
            id = "test_clip_001",
            trackId = "main_video",
            name = "Test Clip",
            mediaUri = "file:///sample_test.mp4",
            startTimeUs = 0L,
            durationUs = durationUs,
            sourceDurationUs = durationUs,
            positionX = 0.65f,
            positionY = 0.35f,
            scaleX = 1.0f,
            scaleY = 1.0f,
            rotationDegrees = 0.0f,
            opacity = 0.85f,
            brightness = 0.0f,
            contrast = 1.2f,
            saturation = 1.1f,
            keyframeTracks = listOf(scaleKeyframeTrack, rotationKeyframeTrack, brightnessKeyframeTrack)
        )

        val mainTrack = TimelineTrack(
            id = "main_video",
            name = "Main Video",
            type = TrackType.MAIN_VIDEO,
            clips = listOf(testClip)
        )

        val canvasConfig = CanvasConfig(
            aspectRatio = CanvasAspectRatio.RATIO_9_16,
            outputWidth = 1080,
            outputHeight = 1920
        )

        val timeline = CanonicalTimeline(
            tracks = listOf(mainTrack),
            canvasConfig = canvasConfig,
            timebaseFps = 30
        )

        val evaluator = TimelineEvaluator(timeline)

        val previewContext = RenderContext(
            canvasConfig = canvasConfig,
            viewportWidth = 1080,
            viewportHeight = 1920,
            isOfflineExport = false
        )

        val exportContext = RenderContext(
            canvasConfig = canvasConfig,
            viewportWidth = 1080,
            viewportHeight = 1920,
            isOfflineExport = true
        )

        // Test timestamps: t=0, t=1.25s (25%), t=2.5s (50%), t=3.75s (75%), t=5.0s (100%)
        val testTimesUs = listOf(0L, 1_250_000L, 2_500_000L, 3_750_000L, 4_999_999L)

        for (timeUs in testTimesUs) {
            val frameIndex = (timeUs / (1_000_000L / 30)).toInt()
            val frameTime = FrameTime(
                presentationTimeUs = timeUs,
                frameIndex = frameIndex,
                fps = 30,
                totalDurationUs = durationUs
            )

            val previewNodes = evaluator.evaluateNodesForTime(frameTime, previewContext)
            val exportNodes = evaluator.evaluateNodesForTime(frameTime, exportContext)

            // Assert Node Count Parity
            assertEquals("Preview and export must yield the exact same node count at ${timeUs}us",
                previewNodes.size, exportNodes.size)

            val previewVideoNode = previewNodes.firstOrNull { it.id == "test_clip_001" }
            val exportVideoNode = exportNodes.firstOrNull { it.id == "test_clip_001" }

            assertNotNull("Video node must be present in preview at ${timeUs}us", previewVideoNode)
            assertNotNull("Video node must be present in export at ${timeUs}us", exportVideoNode)

            val pNode = previewVideoNode!!
            val eNode = exportVideoNode!!

            // Assert Exact Geometric Parity
            assertEquals("TranslationX must match identically between preview and export",
                pNode.transform.translationX, eNode.transform.translationX, 1e-5f)
            assertEquals("TranslationY must match identically between preview and export",
                pNode.transform.translationY, eNode.transform.translationY, 1e-5f)
            assertEquals("ScaleX must match identically between preview and export",
                pNode.transform.scaleX, eNode.transform.scaleX, 1e-5f)
            assertEquals("RotationDegrees must match identically between preview and export",
                pNode.transform.rotationDegrees, eNode.transform.rotationDegrees, 1e-5f)
            assertEquals("Opacity must match identically between preview and export",
                pNode.transform.opacity, eNode.transform.opacity, 1e-5f)

            // Assert Color Grading Parity
            assertEquals("Brightness must match identically between preview and export",
                pNode.colorGrading.brightness, eNode.colorGrading.brightness, 1e-5f)
            assertEquals("Contrast must match identically between preview and export",
                pNode.colorGrading.contrast, eNode.colorGrading.contrast, 1e-5f)
            assertEquals("Saturation must match identically between preview and export",
                pNode.colorGrading.saturation, eNode.colorGrading.saturation, 1e-5f)

            // Assert Correct Dynamic Keyframe Interpolation Math
            val progress = timeUs.toFloat() / durationUs
            val expectedScale = 1.0f + progress * 1.0f
            val expectedRotation = progress * 180.0f
            val expectedBrightness = progress * 0.5f

            assertEquals("ScaleX must dynamically follow linear keyframe curve",
                expectedScale, pNode.transform.scaleX, 0.05f)
            assertEquals("Rotation must dynamically follow linear keyframe curve",
                expectedRotation, pNode.transform.rotationDegrees, 1.0f)
            assertEquals("Brightness must dynamically follow linear keyframe curve",
                expectedBrightness, pNode.colorGrading.brightness, 0.05f)
        }
    }

    /**
     * Phase 3 Master Test:
     * Deterministic test with 2 video clips, 1 image overlay, 1 PIP video, keyframe animations,
     * transforms, crops, anchor points, blend modes, and multi-layer z-index compositing.
     * Verifies strict Preview vs. Export parity.
     */
    @Test
    fun testPhase3MultiLayerCompositingAndParity_withTwoClipsImagePipAndKeyframes() {
        val totalDurationUs = 6_000_000L // 6 seconds

        // 1. Clip 1: Main Video (0s - 3s) with scale and rotation keyframes
        val clip1ScaleTrack = KeyframeTrack(
            property = AnimatableProperty.SCALE_X,
            keyframes = listOf(
                Keyframe(0L, 1.0f, KeyframeInterpolation.LINEAR),
                Keyframe(3_000_000L, 1.5f, KeyframeInterpolation.LINEAR)
            ),
            defaultValue = 1.0f
        )
        val clip1 = VideoClipItem(
            id = "main_clip_01",
            trackId = "main_track",
            name = "Main Clip 1",
            mediaUri = "file:///video1.mp4",
            startTimeUs = 0L,
            durationUs = 3_000_000L,
            sourceDurationUs = 3_000_000L,
            positionX = 0.5f,
            positionY = 0.5f,
            scaleX = 1.0f,
            scaleY = 1.0f,
            anchorX = 0.5f,
            anchorY = 0.5f,
            opacity = 1.0f,
            zIndex = 0,
            keyframeTracks = listOf(clip1ScaleTrack)
        )

        // 2. Clip 2: Main Video (3s - 6s) with transition and color grading
        val clip2 = VideoClipItem(
            id = "main_clip_02",
            trackId = "main_track",
            name = "Main Clip 2",
            mediaUri = "file:///video2.mp4",
            startTimeUs = 3_000_000L,
            durationUs = 3_000_000L,
            sourceDurationUs = 3_000_000L,
            positionX = 0.5f,
            positionY = 0.5f,
            scaleX = 1.0f,
            scaleY = 1.0f,
            brightness = 0.2f,
            contrast = 1.3f,
            saturation = 1.2f,
            transitionInType = "Fade",
            transitionInDurationMs = 500,
            zIndex = 0
        )

        val mainTrack = TimelineTrack(
            id = "main_track",
            name = "Main Video Track",
            type = TrackType.MAIN_VIDEO,
            clips = listOf(clip1, clip2)
        )

        // 3. Clip 3: Image Overlay (1s - 5s) with opacity keyframes and custom anchor
        val imageOpacityTrack = KeyframeTrack(
            property = AnimatableProperty.OPACITY,
            keyframes = listOf(
                Keyframe(0L, 0.0f, KeyframeInterpolation.LINEAR),
                Keyframe(1_000_000L, 1.0f, KeyframeInterpolation.LINEAR),
                Keyframe(4_000_000L, 1.0f, KeyframeInterpolation.LINEAR)
            ),
            defaultValue = 1.0f
        )
        val imageOverlayClip = VideoClipItem(
            id = "image_overlay_01",
            trackId = "overlay_image_track",
            name = "Logo Overlay",
            mediaUri = "file:///watermark.png",
            isVideo = false,
            startTimeUs = 1_000_000L,
            durationUs = 4_000_000L,
            sourceDurationUs = 4_000_000L,
            positionX = 0.8f,
            positionY = 0.2f,
            scaleX = 0.25f,
            scaleY = 0.25f,
            anchorX = 1.0f,
            anchorY = 0.0f,
            zIndex = 1,
            keyframeTracks = listOf(imageOpacityTrack)
        )
        val imageTrack = TimelineTrack(
            id = "overlay_image_track",
            name = "Overlay Image Track",
            type = TrackType.OVERLAY_VIDEO_PIP,
            clips = listOf(imageOverlayClip)
        )

        // 4. Clip 4: PIP Video (2s - 4.5s) with custom transform and crop
        val pipClip = VideoClipItem(
            id = "pip_clip_01",
            trackId = "pip_track",
            name = "PIP Reaction",
            mediaUri = "file:///pip_reaction.mp4",
            isVideo = true,
            startTimeUs = 2_000_000L,
            durationUs = 2_500_000L,
            sourceDurationUs = 2_500_000L,
            positionX = 0.25f,
            positionY = 0.75f,
            scaleX = 0.4f,
            scaleY = 0.4f,
            rotationDegrees = 15.0f,
            anchorX = 0.5f,
            anchorY = 0.5f,
            opacity = 0.9f,
            zIndex = 2,
            crop = com.ritvyom.yashoraReelgenerator.core.model.NormalizedCropRect(0.1f, 0.1f, 0.9f, 0.9f)
        )
        val pipTrack = TimelineTrack(
            id = "pip_track",
            name = "PIP Video Track",
            type = TrackType.OVERLAY_VIDEO_PIP,
            clips = listOf(pipClip)
        )

        // Canvas Config with 9:16 aspect ratio
        val canvasConfig = CanvasConfig(
            aspectRatio = CanvasAspectRatio.RATIO_9_16,
            backgroundStyle = com.ritvyom.yashoraReelgenerator.core.model.CanvasBackgroundStyle.BLURRED_MEDIA,
            outputWidth = 1080,
            outputHeight = 1920
        )

        val timeline = CanonicalTimeline(
            tracks = listOf(mainTrack, imageTrack, pipTrack),
            canvasConfig = canvasConfig,
            timebaseFps = 30
        )

        val evaluator = TimelineEvaluator(timeline)

        val previewContext = RenderContext(
            canvasConfig = canvasConfig,
            viewportWidth = 1080,
            viewportHeight = 1920,
            isOfflineExport = false
        )

        val exportContext = RenderContext(
            canvasConfig = canvasConfig,
            viewportWidth = 1080,
            viewportHeight = 1920,
            isOfflineExport = true
        )

        val testTimestampsUs = listOf(500_000L, 1_500_000L, 2_500_000L, 3_250_000L, 4_200_000L, 5_500_000L)

        for (timeUs in testTimestampsUs) {
            val frameTime = FrameTime(
                presentationTimeUs = timeUs,
                frameIndex = (timeUs / (1_000_000L / 30)).toInt(),
                fps = 30,
                totalDurationUs = totalDurationUs
            )

            val previewNodes = evaluator.evaluateNodesForTime(frameTime, previewContext)
            val exportNodes = evaluator.evaluateNodesForTime(frameTime, exportContext)

            // 1. Verify strict parity in active node count
            assertEquals("Node count must match identically at ${timeUs}us",
                previewNodes.size, exportNodes.size)

            // 2. Verify all nodes appear in the identical zIndex order
            for (i in previewNodes.indices) {
                val pNode = previewNodes[i]
                val eNode = exportNodes[i]

                assertEquals("Node ID at index $i must match at ${timeUs}us", pNode.id, eNode.id)
                assertEquals("Z-Index at index $i must match at ${timeUs}us", pNode.zIndex, eNode.zIndex)
                assertEquals("PassType at index $i must match at ${timeUs}us", pNode.passType, eNode.passType)
                assertEquals("BlendMode at index $i must match at ${timeUs}us", pNode.blendMode, eNode.blendMode)

                // 3. Verify exact affine transform parity
                assertEquals("Transform translationX must match at ${timeUs}us for node ${pNode.id}",
                    pNode.transform.translationX, eNode.transform.translationX, 1e-5f)
                assertEquals("Transform translationY must match at ${timeUs}us for node ${pNode.id}",
                    pNode.transform.translationY, eNode.transform.translationY, 1e-5f)
                assertEquals("Transform scaleX must match at ${timeUs}us for node ${pNode.id}",
                    pNode.transform.scaleX, eNode.transform.scaleX, 1e-5f)
                assertEquals("Transform scaleY must match at ${timeUs}us for node ${pNode.id}",
                    pNode.transform.scaleY, eNode.transform.scaleY, 1e-5f)
                assertEquals("Transform rotationDegrees must match at ${timeUs}us for node ${pNode.id}",
                    pNode.transform.rotationDegrees, eNode.transform.rotationDegrees, 1e-5f)
                assertEquals("Transform anchorX must match at ${timeUs}us for node ${pNode.id}",
                    pNode.transform.anchorX, eNode.transform.anchorX, 1e-5f)
                assertEquals("Transform anchorY must match at ${timeUs}us for node ${pNode.id}",
                    pNode.transform.anchorY, eNode.transform.anchorY, 1e-5f)
                assertEquals("Transform opacity must match at ${timeUs}us for node ${pNode.id}",
                    pNode.transform.opacity, eNode.transform.opacity, 1e-5f)

                // 4. Verify crop rect parity
                assertEquals("Crop left must match at ${timeUs}us for node ${pNode.id}",
                    pNode.transform.crop.left, eNode.transform.crop.left, 1e-5f)
                assertEquals("Crop top must match at ${timeUs}us for node ${pNode.id}",
                    pNode.transform.crop.top, eNode.transform.crop.top, 1e-5f)
                assertEquals("Crop right must match at ${timeUs}us for node ${pNode.id}",
                    pNode.transform.crop.right, eNode.transform.crop.right, 1e-5f)
                assertEquals("Crop bottom must match at ${timeUs}us for node ${pNode.id}",
                    pNode.transform.crop.bottom, eNode.transform.crop.bottom, 1e-5f)

                // 5. Verify color grading parity
                assertEquals("Brightness must match at ${timeUs}us for node ${pNode.id}",
                    pNode.colorGrading.brightness, eNode.colorGrading.brightness, 1e-5f)
                assertEquals("Contrast must match at ${timeUs}us for node ${pNode.id}",
                    pNode.colorGrading.contrast, eNode.colorGrading.contrast, 1e-5f)
                assertEquals("Saturation must match at ${timeUs}us for node ${pNode.id}",
                    pNode.colorGrading.saturation, eNode.colorGrading.saturation, 1e-5f)

                // 6. Verify transition parity
                assertEquals("TransitionType must match at ${timeUs}us for node ${pNode.id}",
                    pNode.transitionType, eNode.transitionType)
                assertEquals("TransitionProgress must match at ${timeUs}us for node ${pNode.id}",
                    pNode.transitionProgress, eNode.transitionProgress, 1e-5f)
            }

            // At t = 2.5s, verify that background + Clip 1 + Image Overlay + PIP Video are all present
            if (timeUs == 2_500_000L) {
                assertEquals("At 2.5s, 4 layers (Background + Main + Image + PIP) must be active",
                    4, previewNodes.size)
                assertTrue("Background must be lowest layer (zIndex 0)", previewNodes[0].zIndex == 0)
                assertTrue("Main video must be zIndex 10", previewNodes[1].zIndex == 10)
                assertTrue("Image overlay must be zIndex >= 20", previewNodes[2].zIndex >= 20)
                assertTrue("PIP video must be highest layer", previewNodes[3].zIndex > previewNodes[2].zIndex)
            }
        }
    }
}
