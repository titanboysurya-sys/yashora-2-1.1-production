package com.ritvyom.yashoraReelgenerator.engine

import com.ritvyom.yashoraReelgenerator.core.model.AnimatableProperty
import com.ritvyom.yashoraReelgenerator.core.model.AudioClipItem
import com.ritvyom.yashoraReelgenerator.core.model.CanvasBackgroundStyle
import com.ritvyom.yashoraReelgenerator.core.model.CanvasConfig
import com.ritvyom.yashoraReelgenerator.core.model.CanonicalTimeline
import com.ritvyom.yashoraReelgenerator.core.model.ColorGradingConfig
import com.ritvyom.yashoraReelgenerator.core.model.KeyframeTrack
import com.ritvyom.yashoraReelgenerator.core.model.LayerBlendMode
import com.ritvyom.yashoraReelgenerator.core.model.NormalizedCropRect
import com.ritvyom.yashoraReelgenerator.core.model.StickerClipItem
import com.ritvyom.yashoraReelgenerator.core.model.TextClipItem
import com.ritvyom.yashoraReelgenerator.core.model.TimelineClipItem
import com.ritvyom.yashoraReelgenerator.core.model.TrackType
import com.ritvyom.yashoraReelgenerator.core.model.Transform2D
import com.ritvyom.yashoraReelgenerator.core.model.VideoClipItem
import com.ritvyom.yashoraReelgenerator.core.model.VisualEffectSpec
import com.ritvyom.yashoraReelgenerator.core.rendering.FrameTime
import com.ritvyom.yashoraReelgenerator.core.rendering.IRenderGraph
import com.ritvyom.yashoraReelgenerator.core.rendering.RenderContext
import com.ritvyom.yashoraReelgenerator.core.rendering.RenderNode
import com.ritvyom.yashoraReelgenerator.core.rendering.RenderPassType
import com.ritvyom.yashoraReelgenerator.engine.text.TextAnimationEvaluator
import com.ritvyom.yashoraReelgenerator.engine.text.TextAnimationType

/**
 * Deterministic Timeline Evaluator implementing [IRenderGraph].
 * Evaluates the CanonicalTimeline at any arbitrary [timeUs] and outputs ordered [RenderNode]s.
 * Both Preview and Export renderers consume this exact evaluation output.
 */
class TimelineEvaluator(
    private val timeline: CanonicalTimeline
) : IRenderGraph {

    override fun evaluateNodesForTime(frameTime: FrameTime, renderContext: RenderContext): List<RenderNode> {
        val timeUs = frameTime.presentationTimeUs
        val nodes = mutableListOf<RenderNode>()

        // 1. Background Fill Node
        val activeMainUri = timeline.mainVideoTrack?.clips?.filterIsInstance<VideoClipItem>()
            ?.firstOrNull { timeUs in it.startTimeUs until it.endTimeUs }?.mediaUri

        nodes.add(
            RenderNode(
                id = "canvas_background",
                name = "Canvas Background",
                zIndex = 0,
                passType = RenderPassType.CANVAS_COMPOSITE,
                sourceUri = if (timeline.canvasConfig.backgroundStyle == CanvasBackgroundStyle.BLURRED_MEDIA) activeMainUri else null,
                isVideoSource = false,
                opacity = 1.0f
            )
        )

        // 2. Main Video / Primary Visual Tracks
        val mainTracks = timeline.tracks.filter { it.type == TrackType.MAIN_VIDEO && it.isVisible }
        for ((trackIdx, track) in mainTracks.withIndex()) {
            val activeClips = track.clips.filterIsInstance<VideoClipItem>()
                .filter { timeUs in it.startTimeUs until it.endTimeUs }

            for (activeClip in activeClips) {
                val localTimeUs = timeUs - activeClip.startTimeUs
                val localProgress = if (activeClip.durationUs > 0) (localTimeUs.toFloat() / activeClip.durationUs).coerceIn(0f, 1f) else 0f

                // Check for active transition
                var transitionType: String? = null
                var transitionProgress = 0f

                val transInDurationUs = activeClip.transitionInDurationMs * 1000L
                if (transInDurationUs > 0 && localTimeUs < transInDurationUs) {
                    transitionType = activeClip.transitionInType
                    transitionProgress = (localTimeUs.toFloat() / transInDurationUs).coerceIn(0f, 1f)
                }

                // Keyframe evaluation via deterministic KeyframeEvaluator
                val kfTracks = activeClip.keyframeTracks
                val posX = KeyframeEvaluator.evaluateProperty(kfTracks, AnimatableProperty.POSITION_X, activeClip.positionX, localTimeUs)
                val posY = KeyframeEvaluator.evaluateProperty(kfTracks, AnimatableProperty.POSITION_Y, activeClip.positionY, localTimeUs)
                val baseScaleX = KeyframeEvaluator.evaluateProperty(kfTracks, AnimatableProperty.SCALE_X, activeClip.scaleX, localTimeUs)
                val baseScaleY = KeyframeEvaluator.evaluateProperty(kfTracks, AnimatableProperty.SCALE_Y, activeClip.scaleY, localTimeUs)
                val zoom = KeyframeEvaluator.evaluateProperty(kfTracks, AnimatableProperty.ZOOM, 1.0f, localTimeUs)
                val scaleX = baseScaleX * zoom
                val scaleY = baseScaleY * zoom
                val rotDeg = KeyframeEvaluator.evaluateProperty(kfTracks, AnimatableProperty.ROTATION_DEGREES, activeClip.rotationDegrees, localTimeUs)
                val anchorX = KeyframeEvaluator.evaluateProperty(kfTracks, AnimatableProperty.ANCHOR_X, activeClip.anchorX, localTimeUs)
                val anchorY = KeyframeEvaluator.evaluateProperty(kfTracks, AnimatableProperty.ANCHOR_Y, activeClip.anchorY, localTimeUs)
                val opac = KeyframeEvaluator.evaluateProperty(kfTracks, AnimatableProperty.OPACITY, activeClip.opacity, localTimeUs).coerceIn(0f, 1f)

                val cropLeft = KeyframeEvaluator.evaluateProperty(kfTracks, AnimatableProperty.CROP_LEFT, activeClip.crop.left, localTimeUs).coerceIn(0f, 1f)
                val cropTop = KeyframeEvaluator.evaluateProperty(kfTracks, AnimatableProperty.CROP_TOP, activeClip.crop.top, localTimeUs).coerceIn(0f, 1f)
                val cropRight = KeyframeEvaluator.evaluateProperty(kfTracks, AnimatableProperty.CROP_RIGHT, activeClip.crop.right, localTimeUs).coerceIn(cropLeft + 0.01f, 1f)
                val cropBottom = KeyframeEvaluator.evaluateProperty(kfTracks, AnimatableProperty.CROP_BOTTOM, activeClip.crop.bottom, localTimeUs).coerceIn(cropTop + 0.01f, 1f)

                val bright = KeyframeEvaluator.evaluateProperty(kfTracks, AnimatableProperty.COLOR_BRIGHTNESS, activeClip.brightness, localTimeUs)
                val contr = KeyframeEvaluator.evaluateProperty(kfTracks, AnimatableProperty.COLOR_CONTRAST, activeClip.contrast, localTimeUs)
                val sat = KeyframeEvaluator.evaluateProperty(kfTracks, AnimatableProperty.COLOR_SATURATION, activeClip.saturation, localTimeUs)
                val warmth = KeyframeEvaluator.evaluateProperty(kfTracks, AnimatableProperty.COLOR_WARMTH, activeClip.warmth, localTimeUs)
                val exposure = KeyframeEvaluator.evaluateProperty(kfTracks, AnimatableProperty.COLOR_EXPOSURE, activeClip.exposure, localTimeUs)
                val tint = KeyframeEvaluator.evaluateProperty(kfTracks, AnimatableProperty.COLOR_TINT, activeClip.tint, localTimeUs)
                val vignette = KeyframeEvaluator.evaluateProperty(kfTracks, AnimatableProperty.COLOR_VIGNETTE, activeClip.vignette, localTimeUs)
                val effectIntensity = KeyframeEvaluator.evaluateProperty(kfTracks, AnimatableProperty.EFFECT_INTENSITY, activeClip.effectIntensity / 100f, localTimeUs)
                val blurRadius = KeyframeEvaluator.evaluateProperty(kfTracks, AnimatableProperty.BLUR_RADIUS, 0f, localTimeUs)

                val clipEffects = mutableListOf<VisualEffectSpec>()
                if (activeClip.effectName.isNotBlank() && activeClip.effectName != "None") {
                    clipEffects.add(VisualEffectSpec(activeClip.effectName, activeClip.effectName, "Stylize", effectIntensity))
                }
                if (blurRadius > 0.01f) {
                    clipEffects.add(VisualEffectSpec("blur", "Blur", "Blur", blurRadius))
                }

                val lutPath = if (activeClip.filterName.isNotBlank() && activeClip.filterName != "None" && activeClip.filterName != "Normal") {
                    if (activeClip.filterName.endsWith(".cube")) activeClip.filterName else "luts/${activeClip.filterName}.cube"
                } else null

                val chromaKey = if (activeClip.isChromaKeyEnabled) {
                    com.ritvyom.yashoraReelgenerator.engine.effects.ChromaKeySpec.fromColorString(
                        activeClip.chromaKeyColor,
                        activeClip.chromaKeySensitivity
                    )
                } else null

                nodes.add(
                    RenderNode(
                        id = activeClip.id,
                        name = activeClip.name,
                        zIndex = 10 + trackIdx + activeClip.zIndex,
                        passType = RenderPassType.SOURCE_DECODE,
                        sourceUri = activeClip.mediaUri,
                        isVideoSource = activeClip.isVideo,
                        transform = Transform2D(
                            translationX = (posX - 0.5f),
                            translationY = (posY - 0.5f),
                            scaleX = scaleX,
                            scaleY = scaleY,
                            rotationDegrees = rotDeg,
                            anchorX = anchorX,
                            anchorY = anchorY,
                            opacity = opac,
                            crop = NormalizedCropRect(cropLeft, cropTop, cropRight, cropBottom),
                            blendMode = activeClip.blendMode
                        ),
                        colorGrading = ColorGradingConfig(
                            brightness = bright,
                            contrast = contr,
                            saturation = sat,
                            warmth = warmth,
                            exposure = exposure,
                            tint = tint,
                            vignette = vignette,
                            sharpen = activeClip.sharpen,
                            highlights = activeClip.highlights,
                            shadows = activeClip.shadows,
                            lutAssetPath = lutPath,
                            lutIntensity = (activeClip.filterIntensity / 100f).coerceIn(0f, 1f),
                            filterIntensity = activeClip.filterIntensity
                        ),
                        effects = clipEffects,
                        opacity = opac,
                        blendMode = activeClip.blendMode,
                        transitionType = transitionType,
                        transitionProgress = transitionProgress,
                        chromaKeySpec = chromaKey
                    )
                )
            }
        }

        // 3. Multi-Layer PIP / Overlay Video & Image Tracks
        val overlayTracks = timeline.tracks.filter { it.type == TrackType.OVERLAY_VIDEO_PIP && it.isVisible }
        for ((pipTrackIdx, track) in overlayTracks.withIndex()) {
            val activeClips = track.clips.filterIsInstance<VideoClipItem>()
                .filter { timeUs in it.startTimeUs until it.endTimeUs }

            for (activePip in activeClips) {
                val localTimeUs = timeUs - activePip.startTimeUs
                val kfTracks = activePip.keyframeTracks
                val posX = KeyframeEvaluator.evaluateProperty(kfTracks, AnimatableProperty.POSITION_X, activePip.positionX, localTimeUs)
                val posY = KeyframeEvaluator.evaluateProperty(kfTracks, AnimatableProperty.POSITION_Y, activePip.positionY, localTimeUs)
                val baseScaleX = KeyframeEvaluator.evaluateProperty(kfTracks, AnimatableProperty.SCALE_X, activePip.scaleX, localTimeUs)
                val baseScaleY = KeyframeEvaluator.evaluateProperty(kfTracks, AnimatableProperty.SCALE_Y, activePip.scaleY, localTimeUs)
                val zoom = KeyframeEvaluator.evaluateProperty(kfTracks, AnimatableProperty.ZOOM, 1.0f, localTimeUs)
                val scaleX = baseScaleX * zoom
                val scaleY = baseScaleY * zoom
                val rotDeg = KeyframeEvaluator.evaluateProperty(kfTracks, AnimatableProperty.ROTATION_DEGREES, activePip.rotationDegrees, localTimeUs)
                val anchorX = KeyframeEvaluator.evaluateProperty(kfTracks, AnimatableProperty.ANCHOR_X, activePip.anchorX, localTimeUs)
                val anchorY = KeyframeEvaluator.evaluateProperty(kfTracks, AnimatableProperty.ANCHOR_Y, activePip.anchorY, localTimeUs)
                val opac = KeyframeEvaluator.evaluateProperty(kfTracks, AnimatableProperty.OPACITY, activePip.opacity, localTimeUs).coerceIn(0f, 1f)

                val cropLeft = KeyframeEvaluator.evaluateProperty(kfTracks, AnimatableProperty.CROP_LEFT, activePip.crop.left, localTimeUs).coerceIn(0f, 1f)
                val cropTop = KeyframeEvaluator.evaluateProperty(kfTracks, AnimatableProperty.CROP_TOP, activePip.crop.top, localTimeUs).coerceIn(0f, 1f)
                val cropRight = KeyframeEvaluator.evaluateProperty(kfTracks, AnimatableProperty.CROP_RIGHT, activePip.crop.right, localTimeUs).coerceIn(cropLeft + 0.01f, 1f)
                val cropBottom = KeyframeEvaluator.evaluateProperty(kfTracks, AnimatableProperty.CROP_BOTTOM, activePip.crop.bottom, localTimeUs).coerceIn(cropTop + 0.01f, 1f)

                val bright = KeyframeEvaluator.evaluateProperty(kfTracks, AnimatableProperty.COLOR_BRIGHTNESS, activePip.brightness, localTimeUs)
                val contr = KeyframeEvaluator.evaluateProperty(kfTracks, AnimatableProperty.COLOR_CONTRAST, activePip.contrast, localTimeUs)
                val sat = KeyframeEvaluator.evaluateProperty(kfTracks, AnimatableProperty.COLOR_SATURATION, activePip.saturation, localTimeUs)
                val warmth = KeyframeEvaluator.evaluateProperty(kfTracks, AnimatableProperty.COLOR_WARMTH, activePip.warmth, localTimeUs)
                val exposure = KeyframeEvaluator.evaluateProperty(kfTracks, AnimatableProperty.COLOR_EXPOSURE, activePip.exposure, localTimeUs)
                val tint = KeyframeEvaluator.evaluateProperty(kfTracks, AnimatableProperty.COLOR_TINT, activePip.tint, localTimeUs)
                val vignette = KeyframeEvaluator.evaluateProperty(kfTracks, AnimatableProperty.COLOR_VIGNETTE, activePip.vignette, localTimeUs)
                val effectIntensity = KeyframeEvaluator.evaluateProperty(kfTracks, AnimatableProperty.EFFECT_INTENSITY, activePip.effectIntensity / 100f, localTimeUs)
                val blurRadius = KeyframeEvaluator.evaluateProperty(kfTracks, AnimatableProperty.BLUR_RADIUS, 0f, localTimeUs)

                val pipEffects = mutableListOf<VisualEffectSpec>()
                if (activePip.effectName.isNotBlank() && activePip.effectName != "None") {
                    pipEffects.add(VisualEffectSpec(activePip.effectName, activePip.effectName, "Stylize", effectIntensity))
                }
                if (blurRadius > 0.01f) {
                    pipEffects.add(VisualEffectSpec("blur", "Blur", "Blur", blurRadius))
                }

                val pipLutPath = if (activePip.filterName.isNotBlank() && activePip.filterName != "None" && activePip.filterName != "Normal") {
                    if (activePip.filterName.endsWith(".cube")) activePip.filterName else "luts/${activePip.filterName}.cube"
                } else null

                val pipChromaKey = if (activePip.isChromaKeyEnabled) {
                    com.ritvyom.yashoraReelgenerator.engine.effects.ChromaKeySpec.fromColorString(
                        activePip.chromaKeyColor,
                        activePip.chromaKeySensitivity
                    )
                } else null

                nodes.add(
                    RenderNode(
                        id = activePip.id,
                        name = "PIP_${activePip.name}",
                        zIndex = 20 + pipTrackIdx + activePip.zIndex,
                        passType = RenderPassType.GEOMETRIC_TRANSFORM,
                        sourceUri = activePip.mediaUri,
                        isVideoSource = activePip.isVideo,
                        transform = Transform2D(
                            translationX = (posX - 0.5f),
                            translationY = (posY - 0.5f),
                            scaleX = scaleX,
                            scaleY = scaleY,
                            rotationDegrees = rotDeg,
                            anchorX = anchorX,
                            anchorY = anchorY,
                            opacity = opac,
                            crop = NormalizedCropRect(cropLeft, cropTop, cropRight, cropBottom),
                            blendMode = activePip.blendMode
                        ),
                        colorGrading = ColorGradingConfig(
                            brightness = bright,
                            contrast = contr,
                            saturation = sat,
                            warmth = warmth,
                            exposure = exposure,
                            tint = tint,
                            vignette = vignette,
                            sharpen = activePip.sharpen,
                            highlights = activePip.highlights,
                            shadows = activePip.shadows,
                            lutAssetPath = pipLutPath,
                            lutIntensity = (activePip.filterIntensity / 100f).coerceIn(0f, 1f),
                            filterIntensity = activePip.filterIntensity
                        ),
                        effects = pipEffects,
                        opacity = opac,
                        blendMode = activePip.blendMode,
                        chromaKeySpec = pipChromaKey
                    )
                )
            }
        }

        // 4. Sticker Tracks
        for (track in timeline.tracks.filter { it.type == TrackType.STICKER && it.isVisible }) {
            val activeSticker = track.clips.filterIsInstance<StickerClipItem>()
                .firstOrNull { timeUs in it.startTimeUs until it.endTimeUs }

            if (activeSticker != null) {
                val localTimeUs = timeUs - activeSticker.startTimeUs
                val kfTracks = activeSticker.keyframeTracks
                val posX = KeyframeEvaluator.evaluateProperty(kfTracks, AnimatableProperty.POSITION_X, activeSticker.positionX, localTimeUs)
                val posY = KeyframeEvaluator.evaluateProperty(kfTracks, AnimatableProperty.POSITION_Y, activeSticker.positionY, localTimeUs)
                val baseScaleX = KeyframeEvaluator.evaluateProperty(kfTracks, AnimatableProperty.SCALE_X, activeSticker.scale, localTimeUs)
                val baseScaleY = KeyframeEvaluator.evaluateProperty(kfTracks, AnimatableProperty.SCALE_Y, activeSticker.scale, localTimeUs)
                val zoom = KeyframeEvaluator.evaluateProperty(kfTracks, AnimatableProperty.ZOOM, 1.0f, localTimeUs)
                val rotDeg = KeyframeEvaluator.evaluateProperty(kfTracks, AnimatableProperty.ROTATION_DEGREES, activeSticker.rotationDegrees, localTimeUs)
                val anchorX = KeyframeEvaluator.evaluateProperty(kfTracks, AnimatableProperty.ANCHOR_X, activeSticker.anchorX, localTimeUs)
                val anchorY = KeyframeEvaluator.evaluateProperty(kfTracks, AnimatableProperty.ANCHOR_Y, activeSticker.anchorY, localTimeUs)
                val opac = KeyframeEvaluator.evaluateProperty(kfTracks, AnimatableProperty.OPACITY, activeSticker.opacity, localTimeUs).coerceIn(0f, 1f)

                nodes.add(
                    RenderNode(
                        id = activeSticker.id,
                        name = "Sticker_${activeSticker.stickerIdentifier}",
                        zIndex = 30,
                        passType = RenderPassType.OVERLAY_TEXT_AND_STICKER,
                        sourceUri = activeSticker.stickerIdentifier,
                        isVideoSource = false,
                        transform = Transform2D(
                            translationX = (posX - 0.5f),
                            translationY = (posY - 0.5f),
                            scaleX = baseScaleX * zoom,
                            scaleY = baseScaleY * zoom,
                            rotationDegrees = rotDeg,
                            anchorX = anchorX,
                            anchorY = anchorY,
                            opacity = opac
                        ),
                        opacity = opac
                    )
                )
            }
        }

        // 5. Text & Subtitle Tracks
        for (track in timeline.textTracks.filter { it.isVisible }) {
            val activeText = track.clips.filterIsInstance<TextClipItem>()
                .firstOrNull { timeUs in it.startTimeUs until it.endTimeUs }

            if (activeText != null && activeText.text.isNotBlank()) {
                val localTimeUs = timeUs - activeText.startTimeUs
                val kfTracks = activeText.keyframeTracks
                val posX = KeyframeEvaluator.evaluateProperty(kfTracks, AnimatableProperty.POSITION_X, activeText.positionX, localTimeUs)
                val posY = KeyframeEvaluator.evaluateProperty(kfTracks, AnimatableProperty.POSITION_Y, activeText.positionY, localTimeUs)
                val baseScaleX = KeyframeEvaluator.evaluateProperty(kfTracks, AnimatableProperty.SCALE_X, activeText.scaleX * activeText.scale, localTimeUs)
                val baseScaleY = KeyframeEvaluator.evaluateProperty(kfTracks, AnimatableProperty.SCALE_Y, activeText.scaleY * activeText.scale, localTimeUs)
                val zoom = KeyframeEvaluator.evaluateProperty(kfTracks, AnimatableProperty.ZOOM, 1.0f, localTimeUs)
                val rotDeg = KeyframeEvaluator.evaluateProperty(kfTracks, AnimatableProperty.ROTATION_DEGREES, activeText.rotationDegrees, localTimeUs)
                val anchorX = KeyframeEvaluator.evaluateProperty(kfTracks, AnimatableProperty.ANCHOR_X, activeText.anchorX, localTimeUs)
                val anchorY = KeyframeEvaluator.evaluateProperty(kfTracks, AnimatableProperty.ANCHOR_Y, activeText.anchorY, localTimeUs)
                val opac = KeyframeEvaluator.evaluateProperty(kfTracks, AnimatableProperty.OPACITY, activeText.opacity, localTimeUs).coerceIn(0f, 1f)

                // Procedural text entrance/exit animations
                val animType = TextAnimationType.fromString(activeText.animationType)
                val animResult = TextAnimationEvaluator.evaluate(
                    animationType = animType,
                    localTimeUs = localTimeUs,
                    clipDurationUs = activeText.durationUs,
                    animationDurationSec = activeText.animationDurationSec,
                    totalCharCount = activeText.text.length
                )

                val effectiveText = if (animResult.visibleCharacterCount < activeText.text.length) {
                    activeText.text.take(animResult.visibleCharacterCount)
                } else {
                    activeText.text
                }

                val finalPosX = posX + animResult.translationOffsetX
                val finalPosY = posY + animResult.translationOffsetY
                val finalScaleX = baseScaleX * zoom * animResult.scaleMultiplier
                val finalScaleY = baseScaleY * zoom * animResult.scaleMultiplier
                val finalOpacity = (opac * animResult.alphaMultiplier).coerceIn(0f, 1f)

                nodes.add(
                    RenderNode(
                        id = activeText.id,
                        name = "Text_${activeText.name}",
                        zIndex = activeText.zIndex,
                        passType = RenderPassType.OVERLAY_TEXT_AND_STICKER,
                        textPayload = effectiveText,
                        textStyleSpec = activeText.styleSpec,
                        transform = Transform2D(
                            translationX = (finalPosX - 0.5f),
                            translationY = (finalPosY - 0.5f),
                            scaleX = finalScaleX,
                            scaleY = finalScaleY,
                            rotationDegrees = rotDeg,
                            anchorX = anchorX,
                            anchorY = anchorY,
                            opacity = finalOpacity
                        ),
                        opacity = finalOpacity,
                        textStylePayload = mapOf(
                            "font" to activeText.fontName,
                            "fontSizeSp" to activeText.fontSizeSp,
                            "textColorHex" to activeText.textColorHex,
                            "backgroundColorHex" to activeText.backgroundColorHex,
                            "shadowColorHex" to (activeText.shadowColorHex ?: ""),
                            "alignment" to activeText.alignment
                        )
                    )
                )
            }
        }

        return nodes.sortedBy { it.zIndex }
    }

    private fun evaluateKeyframe(
        tracks: List<KeyframeTrack<*>>,
        property: AnimatableProperty,
        fallback: Float,
        localTimeUs: Long
    ): Float {
        return KeyframeEvaluator.evaluateProperty(tracks, property, fallback, localTimeUs)
    }

    /**
     * Evaluates active audio clips across all visible audio and video tracks at a specific timestamp [timeUs].
     * Computes clip-level volume, fades, track volume, mute states, and keyframed gain/pan.
     */
    fun evaluateAudioForTime(timeUs: Long): List<EvaluatedAudioNode> {
        val audioNodes = mutableListOf<EvaluatedAudioNode>()

        // 1. Audio from Main and Overlay Video Tracks (Original Audio)
        val videoTracks = timeline.tracks.filter {
            (it.type == TrackType.MAIN_VIDEO || it.type == TrackType.OVERLAY_VIDEO_PIP) && it.isVisible && !it.isMuted
        }
        for (track in videoTracks) {
            val activeClips = track.clips.filterIsInstance<VideoClipItem>()
                .filter { it.isVideo && it.mediaUri.isNotBlank() && timeUs in it.startTimeUs until it.endTimeUs && !it.isMuted && it.volume > 0f }

            for (clip in activeClips) {
                val localTimeUs = timeUs - clip.startTimeUs
                var gain = clip.volume * track.volume

                // Apply Fade In
                if (clip.audioFadeInDurationUs > 0L && localTimeUs < clip.audioFadeInDurationUs) {
                    gain *= (localTimeUs.toFloat() / clip.audioFadeInDurationUs).coerceIn(0f, 1f)
                }
                // Apply Fade Out
                val timeUntilEndUs = clip.durationUs - localTimeUs
                if (clip.audioFadeOutDurationUs > 0L && timeUntilEndUs < clip.audioFadeOutDurationUs) {
                    gain *= (timeUntilEndUs.toFloat() / clip.audioFadeOutDurationUs).coerceIn(0f, 1f)
                }

                // Apply Keyframe Volume
                gain = KeyframeEvaluator.evaluateProperty(clip.keyframeTracks, AnimatableProperty.VOLUME, gain, localTimeUs)
                val pan = KeyframeEvaluator.evaluateProperty(clip.keyframeTracks, AnimatableProperty.PAN, 0.0f, localTimeUs)

                val effectiveSourceTimeUs = clip.sourceStartUs + (localTimeUs * clip.playbackSpeed).toLong()

                audioNodes.add(
                    EvaluatedAudioNode(
                        clipId = clip.id,
                        trackId = track.id,
                        trackType = track.type,
                        audioUri = clip.mediaUri,
                        sourceTimeUs = effectiveSourceTimeUs,
                        playbackSpeed = clip.playbackSpeed,
                        effectiveVolume = gain.coerceIn(0f, 2f),
                        pan = pan.coerceIn(-1f, 1f),
                        voiceEffect = clip.voiceEffect
                    )
                )
            }
        }

        // 2. Dedicated Audio Tracks (BGM, Voiceover, SFX, Original Audio)
        for (track in timeline.audioTracks.filter { it.isVisible && !it.isMuted }) {
            val activeAudioClips = track.clips.filterIsInstance<AudioClipItem>()
                .filter { timeUs in it.startTimeUs until it.endTimeUs && !it.isMuted && it.volume > 0f }

            for (clip in activeAudioClips) {
                val localTimeUs = timeUs - clip.startTimeUs
                var gain = clip.volume * track.volume

                // Apply Fade In
                if (clip.fadeInDurationUs > 0L && localTimeUs < clip.fadeInDurationUs) {
                    gain *= (localTimeUs.toFloat() / clip.fadeInDurationUs).coerceIn(0f, 1f)
                }
                // Apply Fade Out
                val timeUntilEndUs = clip.durationUs - localTimeUs
                if (clip.fadeOutDurationUs > 0L && timeUntilEndUs < clip.fadeOutDurationUs) {
                    gain *= (timeUntilEndUs.toFloat() / clip.fadeOutDurationUs).coerceIn(0f, 1f)
                }

                // Apply Keyframe Volume & Pan
                gain = KeyframeEvaluator.evaluateProperty(clip.keyframeTracks, AnimatableProperty.VOLUME, gain, localTimeUs)
                val pan = KeyframeEvaluator.evaluateProperty(clip.keyframeTracks, AnimatableProperty.PAN, clip.pan, localTimeUs)

                val effectiveSourceTimeUs = clip.sourceStartUs + (localTimeUs * clip.playbackSpeed).toLong()

                audioNodes.add(
                    EvaluatedAudioNode(
                        clipId = clip.id,
                        trackId = track.id,
                        trackType = track.type,
                        audioUri = clip.audioUri,
                        sourceTimeUs = effectiveSourceTimeUs,
                        playbackSpeed = clip.playbackSpeed,
                        effectiveVolume = gain.coerceIn(0f, 2f),
                        pan = pan.coerceIn(-1f, 1f),
                        voiceEffect = clip.voiceEffect
                    )
                )
            }
        }

        return audioNodes
    }
}

/**
 * Result of audio graph evaluation at an instantaneous point in timeline time.
 */
data class EvaluatedAudioNode(
    val clipId: String,
    val trackId: String,
    val trackType: TrackType,
    val audioUri: String,
    val sourceTimeUs: Long,
    val playbackSpeed: Float = 1.0f,
    val effectiveVolume: Float = 1.0f,
    val pan: Float = 0.0f,
    val voiceEffect: String = "None"
)
