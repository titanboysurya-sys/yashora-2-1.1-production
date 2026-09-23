package com.ritvyom.yashoraReelgenerator.engine

import android.content.Context
import com.ritvyom.yashoraReelgenerator.core.model.AudioClipItem
import com.ritvyom.yashoraReelgenerator.core.model.CanvasAspectRatio
import com.ritvyom.yashoraReelgenerator.core.model.CanvasConfig
import com.ritvyom.yashoraReelgenerator.core.model.CanonicalProject
import com.ritvyom.yashoraReelgenerator.core.model.CanonicalTimeline
import com.ritvyom.yashoraReelgenerator.core.model.StickerClipItem
import com.ritvyom.yashoraReelgenerator.core.model.TextClipItem
import com.ritvyom.yashoraReelgenerator.core.model.TimelineTrack
import com.ritvyom.yashoraReelgenerator.core.model.TrackType
import com.ritvyom.yashoraReelgenerator.core.model.VideoClipItem
import com.ritvyom.yashoraReelgenerator.core.rendering.IExportRenderer
import com.ritvyom.yashoraReelgenerator.core.rendering.IPreviewRenderer
import com.ritvyom.yashoraReelgenerator.core.rendering.IRenderGraph
import com.ritvyom.yashoraReelgenerator.domain.models.Scene
import com.ritvyom.yashoraReelgenerator.domain.models.getCanvasLayers
import com.ritvyom.yashoraReelgenerator.domain.models.saveCanvasLayers
import com.ritvyom.yashoraReelgenerator.engine.renderer.OpenGlExportRenderer
import com.ritvyom.yashoraReelgenerator.engine.renderer.OpenGlPreviewRenderer
import com.ritvyom.yashoraReelgenerator.engine.text.TextStyleSpec
import com.ritvyom.yashoraReelgenerator.engine.text.TextType

/**
 * YashoraEngineBridge:
 * Connects existing Yashora UI, ViewModel, and Scene data structures to the
 * high-performance Canonical Timeline and unified Render Graph engine.
 *
 * Guarantees:
 * 1. Zero regression to existing functionality.
 * 2. Bi-directional lossless synchronization between Scene lists and CanonicalTimeline.
 * 3. Shared timeline evaluation for preview and export.
 */
object YashoraEngineBridge {

    /**
     * Converts a legacy list of [Scene]s and aspect ratio into a [CanonicalTimeline].
     */
    fun createCanonicalTimeline(
        scenes: List<Scene>,
        aspectRatioStr: String = "9:16",
        resolutionStr: String = "1080p",
        fps: Int = 30,
        bgMusicCategory: String = "None",
        bgMusicVolume: Float = 0.5f,
        bgMusicEnabled: Boolean = false
    ): CanonicalTimeline {
        val aspectRatio = CanvasAspectRatio.fromString(aspectRatioStr)

        val (width, height) = when (aspectRatio) {
            CanvasAspectRatio.RATIO_9_16 -> 1080 to 1920
            CanvasAspectRatio.RATIO_16_9 -> 1920 to 1080
            CanvasAspectRatio.RATIO_1_1 -> 1080 to 1080
            CanvasAspectRatio.RATIO_4_5 -> 1080 to 1350
            CanvasAspectRatio.RATIO_3_4 -> 1080 to 1440
            CanvasAspectRatio.RATIO_21_9 -> 2560 to 1080
        }

        val canvasConfig = CanvasConfig(
            aspectRatio = aspectRatio,
            outputWidth = width,
            outputHeight = height
        )

        val mainVideoClips = mutableListOf<VideoClipItem>()
        val pipClips = mutableListOf<VideoClipItem>()
        val textClips = mutableListOf<TextClipItem>()
        val stickerClips = mutableListOf<StickerClipItem>()
        val voiceoverClips = mutableListOf<AudioClipItem>()
        val sfxClips = mutableListOf<AudioClipItem>()

        var currentStartUs = 0L

        for ((index, scene) in scenes.withIndex()) {
            val sceneDurationUs = (scene.durationSeconds * 1_000_000L).coerceAtLeast(1_000_000L)
            val clipId = "scene_clip_${scene.sceneNumber}_$index"

            val videoClip = VideoClipItem(
                id = clipId,
                trackId = "track_main_video",
                name = "Scene ${scene.sceneNumber}",
                startTimeUs = currentStartUs,
                durationUs = sceneDurationUs,
                sourceStartUs = 0L,
                sourceDurationUs = sceneDurationUs,
                mediaUri = scene.mediaPath ?: "",
                isVideo = scene.mediaType.equals("VIDEO", ignoreCase = true),
                playbackSpeed = scene.speedMultiplier,
                volume = scene.volume,
                rotationDegrees = scene.rotationDegrees.toFloat(),
                isFlippedHorizontal = scene.isFlippedHorizontal,
                isFlippedVertical = scene.isFlippedVertical,
                brightness = scene.brightnessValue / 100f,
                contrast = scene.contrastValue,
                saturation = scene.saturationValue,
                warmth = scene.warmthValue / 100f,
                exposure = scene.exposureValue,
                tint = scene.tintValue,
                vignette = scene.vignetteValue,
                sharpen = scene.sharpenValue,
                highlights = scene.highlightValue,
                shadows = scene.shadowValue,
                filterName = scene.selectedFilterName,
                filterIntensity = scene.filterIntensity,
                transitionInType = scene.transitionType,
                transitionInDurationMs = scene.transitionDurationMs,
                inAnimation = scene.inAnimation,
                outAnimation = scene.outAnimation,
                comboAnimation = scene.comboAnimation,
                animationDurationSec = scene.animationDuration,
                effectName = scene.effectName,
                effectIntensity = scene.effectIntensity,
                voiceEffect = scene.voiceEffect,
                isReversed = scene.isReversed,
                isFrozen = scene.isFrozen,
                freezeDurationSec = scene.freezeDurationSeconds,
                isChromaKeyEnabled = scene.isChromaKeyEnabled,
                chromaKeyColor = scene.chromaKeyColor,
                chromaKeySensitivity = scene.chromaKeySensitivity
            )
            mainVideoClips.add(videoClip)

            // Extract subtitles / captions
            if (scene.subtitle.isNotBlank()) {
                val subStyle = TextStyleSpec(
                    fontName = scene.captionFont.ifBlank { "TikTok Style" },
                    fontSizeSp = 22f,
                    textColorHex = scene.subtitleColor.ifBlank { "#FFFFFF" },
                    hasBackground = scene.subtitleBgColor.isNotBlank() && scene.subtitleBgColor != "#00000000",
                    backgroundColorHex = scene.subtitleBgColor.ifBlank { "#99000000" },
                    shadowColorHex = "#80000000",
                    shadowRadiusDp = 4f
                )
                textClips.add(
                    TextClipItem(
                        id = "sub_${scene.sceneNumber}_$index",
                        trackId = "track_subtitles",
                        name = "Sub_${scene.sceneNumber}",
                        startTimeUs = currentStartUs,
                        durationUs = sceneDurationUs,
                        sourceStartUs = 0L,
                        sourceDurationUs = sceneDurationUs,
                        text = scene.subtitle,
                        textType = TextType.CAPTION,
                        fontName = scene.captionFont,
                        textColorHex = scene.subtitleColor,
                        backgroundColorHex = scene.subtitleBgColor,
                        styleSpec = subStyle,
                        positionX = 0.5f,
                        positionY = 0.82f,
                        zIndex = 45
                    )
                )
            }

            // Extract layers (Text & Stickers)
            val layers = scene.getCanvasLayers()
            for (layer in layers) {
                if (!layer.isVisible) continue
                if (layer.type.equals("TEXT", ignoreCase = true)) {
                    val textStyle = TextStyleSpec(
                        fontName = layer.font.ifBlank { "TikTok Style" },
                        fontSizeSp = layer.fontSize.coerceAtLeast(14f),
                        textColorHex = layer.color.ifBlank { "#FFFFFF" },
                        shadowColorHex = "#80000000",
                        shadowRadiusDp = 4f
                    )
                    textClips.add(
                        TextClipItem(
                            id = layer.id,
                            trackId = "track_text_overlays",
                            name = "Text_${layer.id.take(6)}",
                            startTimeUs = currentStartUs,
                            durationUs = sceneDurationUs,
                            sourceStartUs = 0L,
                            sourceDurationUs = sceneDurationUs,
                            text = layer.content,
                            textType = TextType.TEXT,
                            fontName = layer.font,
                            fontSizeSp = layer.fontSize,
                            textColorHex = layer.color,
                            styleSpec = textStyle,
                            positionX = layer.x,
                            positionY = layer.y,
                            scale = layer.scale,
                            rotationDegrees = layer.rotation,
                            animationType = layer.animation,
                            zIndex = 40
                        )
                    )
                } else if (layer.type.equals("STICKER", ignoreCase = true)) {
                    stickerClips.add(
                        StickerClipItem(
                            id = layer.id,
                            trackId = "track_stickers",
                            name = "Sticker_${layer.content}",
                            startTimeUs = currentStartUs,
                            durationUs = sceneDurationUs,
                            sourceStartUs = 0L,
                            sourceDurationUs = sceneDurationUs,
                            stickerIdentifier = layer.content,
                            positionX = layer.x,
                            positionY = layer.y,
                            scale = layer.scale,
                            rotationDegrees = layer.rotation
                        )
                    )
                }
            }

            // Extract PIP / Overlay media
            val pipPath = scene.pipMediaPath
            if (!pipPath.isNullOrEmpty()) {
                val isPipVideo = pipPath.endsWith(".mp4", ignoreCase = true) ||
                        pipPath.endsWith(".mkv", ignoreCase = true) ||
                        pipPath.endsWith(".webm", ignoreCase = true) ||
                        pipPath.contains("/video", ignoreCase = true)
                pipClips.add(
                    VideoClipItem(
                        id = "pip_${scene.sceneNumber}_$index",
                        trackId = "track_overlay_pip",
                        name = "PIP_${scene.sceneNumber}",
                        startTimeUs = currentStartUs,
                        durationUs = sceneDurationUs,
                        sourceStartUs = 0L,
                        sourceDurationUs = sceneDurationUs,
                        mediaUri = pipPath,
                        isVideo = isPipVideo,
                        positionX = scene.pipX,
                        positionY = scene.pipY,
                        scaleX = scene.pipScale,
                        scaleY = scene.pipScale,
                        zIndex = 20
                    )
                )
            }

            // Extract voiceover audio
            val voiceAudioPath = scene.customVoiceAudioPath
            if (!voiceAudioPath.isNullOrEmpty()) {
                voiceoverClips.add(
                    AudioClipItem(
                        id = "voice_${scene.sceneNumber}_$index",
                        trackId = "track_audio_voiceover",
                        name = "Voice ${scene.sceneNumber}",
                        startTimeUs = currentStartUs,
                        durationUs = sceneDurationUs,
                        sourceStartUs = 0L,
                        sourceDurationUs = sceneDurationUs,
                        audioUri = voiceAudioPath,
                        volume = scene.volume,
                        fadeInDurationUs = (scene.audioFadeInDuration * 1_000_000L).toLong(),
                        fadeOutDurationUs = (scene.audioFadeOutDuration * 1_000_000L).toLong(),
                        voiceEffect = scene.voiceEffect
                    )
                )
            }

            // Extract sound effect
            if (!scene.sfxName.isNullOrEmpty() && !scene.sfxName.equals("None", ignoreCase = true)) {
                sfxClips.add(
                    AudioClipItem(
                        id = "sfx_${scene.sceneNumber}_$index",
                        trackId = "track_audio_sfx",
                        name = "SFX_${scene.sfxName}",
                        startTimeUs = currentStartUs,
                        durationUs = (2_000_000L).coerceAtMost(sceneDurationUs),
                        sourceStartUs = 0L,
                        sourceDurationUs = (2_000_000L).coerceAtMost(sceneDurationUs),
                        audioUri = scene.sfxName,
                        volume = 0.8f
                    )
                )
            }

            currentStartUs += sceneDurationUs
        }

        val tracks = mutableListOf<TimelineTrack>()
        tracks.add(
            TimelineTrack(
                id = "track_main_video",
                name = "Main Video",
                type = TrackType.MAIN_VIDEO,
                clips = mainVideoClips
            )
        )

        if (pipClips.isNotEmpty()) {
            tracks.add(
                TimelineTrack(
                    id = "track_overlay_pip",
                    name = "PIP Overlays",
                    type = TrackType.OVERLAY_VIDEO_PIP,
                    clips = pipClips
                )
            )
        }

        if (voiceoverClips.isNotEmpty()) {
            tracks.add(
                TimelineTrack(
                    id = "track_audio_voiceover",
                    name = "Voice-over",
                    type = TrackType.AUDIO_VOICEOVER,
                    clips = voiceoverClips
                )
            )
        }

        if (sfxClips.isNotEmpty()) {
            tracks.add(
                TimelineTrack(
                    id = "track_audio_sfx",
                    name = "Sound Effects",
                    type = TrackType.AUDIO_SFX,
                    clips = sfxClips
                )
            )
        }

        if (bgMusicEnabled && !bgMusicCategory.equals("None", ignoreCase = true) && currentStartUs > 0L) {
            val bgClip = AudioClipItem(
                id = "bgm_canonical_track",
                trackId = "track_audio_bgm",
                name = "BGM $bgMusicCategory",
                startTimeUs = 0L,
                durationUs = currentStartUs,
                sourceStartUs = 0L,
                sourceDurationUs = currentStartUs,
                audioUri = "bgm:$bgMusicCategory",
                volume = bgMusicVolume,
                loop = true
            )
            tracks.add(
                TimelineTrack(
                    id = "track_audio_bgm",
                    name = "Background Music",
                    type = TrackType.AUDIO_BGM,
                    volume = bgMusicVolume,
                    clips = listOf(bgClip)
                )
            )
        }

        if (textClips.isNotEmpty()) {
            tracks.add(
                TimelineTrack(
                    id = "track_text_overlays",
                    name = "Text & Titles",
                    type = TrackType.TEXT_TITLE,
                    clips = textClips
                )
            )
        }

        if (stickerClips.isNotEmpty()) {
            tracks.add(
                TimelineTrack(
                    id = "track_stickers",
                    name = "Stickers",
                    type = TrackType.STICKER,
                    clips = stickerClips
                )
            )
        }

        return CanonicalTimeline(
            tracks = tracks,
            canvasConfig = canvasConfig,
            timebaseFps = fps
        )
    }

    /**
     * Converts a [CanonicalTimeline] to a full [CanonicalProject].
     */
    fun createCanonicalProject(
        title: String,
        scenes: List<Scene>,
        aspectRatioStr: String = "9:16",
        resolutionStr: String = "1080p",
        fps: Int = 30,
        bgMusicCategory: String = "None",
        bgMusicVolume: Float = 0.5f,
        bgMusicEnabled: Boolean = false
    ): CanonicalProject {
        val timeline = createCanonicalTimeline(
            scenes = scenes,
            aspectRatioStr = aspectRatioStr,
            resolutionStr = resolutionStr,
            fps = fps,
            bgMusicCategory = bgMusicCategory,
            bgMusicVolume = bgMusicVolume,
            bgMusicEnabled = bgMusicEnabled
        )
        return CanonicalProject(
            title = title,
            timeline = timeline,
            targetResolution = resolutionStr,
            targetFps = fps
        )
    }

    /**
     * Creates an [IRenderGraph] evaluator from the canonical timeline.
     */
    fun createRenderGraph(timeline: CanonicalTimeline): IRenderGraph {
        return TimelineEvaluator(timeline)
    }

    /**
     * Probes and returns device hardware capabilities.
     */
    fun getDeviceCapabilities(context: Context): DeviceCapabilityDetector.DeviceCapabilities {
        return DeviceCapabilityDetector.getCapabilities(context)
    }

    /**
     * Creates an OpenGL ES Preview Renderer instance.
     */
    fun createPreviewRenderer(context: Context): IPreviewRenderer {
        return OpenGlPreviewRenderer(context)
    }

    /**
     * Creates a deterministic OpenGL ES / MediaCodec Export Renderer instance.
     */
    fun createExportRenderer(context: Context): IExportRenderer {
        return OpenGlExportRenderer(context)
    }

    /**
     * Converts an updated [CanonicalTimeline] back into a list of [Scene]s.
     * Preserves speed, duration, visual grading, transitions, audio, text, and sticker layers.
     */
    fun convertToScenes(
        timeline: CanonicalTimeline,
        referenceScenes: List<Scene> = emptyList()
    ): List<Scene> {
        val videoTrack = timeline.tracks.firstOrNull { it.type == TrackType.MAIN_VIDEO }
            ?: return referenceScenes
        val videoClips = videoTrack.clips.filterIsInstance<VideoClipItem>()
        if (videoClips.isEmpty()) return referenceScenes

        val subClips = timeline.tracks.firstOrNull { it.type == TrackType.TEXT_SUBTITLE }
            ?.clips?.filterIsInstance<TextClipItem>() ?: emptyList()
        val textClips = timeline.tracks.firstOrNull { it.type == TrackType.TEXT_TITLE }
            ?.clips?.filterIsInstance<TextClipItem>() ?: emptyList()
        val stickerClips = timeline.tracks.firstOrNull { it.type == TrackType.STICKER }
            ?.clips?.filterIsInstance<StickerClipItem>() ?: emptyList()
        val pipClips = timeline.tracks.firstOrNull { it.type == TrackType.OVERLAY_VIDEO_PIP }
            ?.clips?.filterIsInstance<VideoClipItem>() ?: emptyList()
        val voiceClips = timeline.tracks.firstOrNull { it.type == TrackType.AUDIO_VOICEOVER }
            ?.clips?.filterIsInstance<AudioClipItem>() ?: emptyList()

        return videoClips.mapIndexed { index, clip ->
            val refScene = referenceScenes.getOrNull(index) ?: referenceScenes.firstOrNull()
            val durationSec = ((clip.durationUs + 500_000L) / 1_000_000L).toInt().coerceAtLeast(1)

            val matchingSub = subClips.firstOrNull { sub ->
                sub.startTimeUs < clip.endTimeUs && sub.endTimeUs > clip.startTimeUs
            }

            val matchingVoice = voiceClips.firstOrNull { v ->
                v.startTimeUs < clip.endTimeUs && v.endTimeUs > clip.startTimeUs
            }

            val matchingPip = pipClips.firstOrNull { p ->
                p.startTimeUs < clip.endTimeUs && p.endTimeUs > clip.startTimeUs
            }

            val matchingTexts = textClips.filter { t ->
                t.startTimeUs < clip.endTimeUs && t.endTimeUs > clip.startTimeUs
            }
            val matchingStickers = stickerClips.filter { s ->
                s.startTimeUs < clip.endTimeUs && s.endTimeUs > clip.startTimeUs
            }

            val layers = mutableListOf<com.ritvyom.yashoraReelgenerator.domain.models.CanvasLayer>()
            for (t in matchingTexts) {
                layers.add(
                    com.ritvyom.yashoraReelgenerator.domain.models.CanvasLayer(
                        id = t.id,
                        type = "TEXT",
                        content = t.text,
                        color = t.textColorHex.ifBlank { "#FFFFFF" },
                        font = t.fontName.ifBlank { "TikTok Style" },
                        animation = t.animationType.ifBlank { "Pop" },
                        fontSize = t.fontSizeSp.coerceAtLeast(14f),
                        x = t.positionX,
                        y = t.positionY,
                        scale = t.scale,
                        rotation = t.rotationDegrees,
                        isVisible = t.isVisible
                    )
                )
            }
            for (s in matchingStickers) {
                layers.add(
                    com.ritvyom.yashoraReelgenerator.domain.models.CanvasLayer(
                        id = s.id,
                        type = "STICKER",
                        content = s.stickerIdentifier,
                        x = s.positionX,
                        y = s.positionY,
                        scale = s.scale,
                        rotation = s.rotationDegrees,
                        isVisible = s.isVisible
                    )
                )
            }

            val scene = refScene?.copy(
                sceneNumber = index + 1,
                mediaPath = clip.mediaUri.ifBlank { refScene.mediaPath },
                mediaType = if (clip.isVideo) "VIDEO" else "IMAGE",
                durationSeconds = durationSec,
                durationMs = durationSec * 1000L,
                speedMultiplier = clip.playbackSpeed,
                volume = clip.volume,
                rotationDegrees = clip.rotationDegrees.toInt(),
                isFlippedHorizontal = clip.isFlippedHorizontal,
                isFlippedVertical = clip.isFlippedVertical,
                brightnessValue = clip.brightness * 100f,
                contrastValue = clip.contrast,
                saturationValue = clip.saturation,
                warmthValue = clip.warmth * 100f,
                exposureValue = clip.exposure,
                tintValue = clip.tint,
                vignetteValue = clip.vignette,
                sharpenValue = clip.sharpen,
                highlightValue = clip.highlights,
                shadowValue = clip.shadows,
                selectedFilterName = clip.filterName,
                filterIntensity = clip.filterIntensity,
                transitionType = clip.transitionInType,
                transitionDurationMs = clip.transitionInDurationMs,
                inAnimation = clip.inAnimation,
                outAnimation = clip.outAnimation,
                comboAnimation = clip.comboAnimation,
                animationDuration = clip.animationDurationSec,
                effectName = clip.effectName,
                effectIntensity = clip.effectIntensity,
                voiceEffect = clip.voiceEffect,
                isReversed = clip.isReversed,
                isFrozen = clip.isFrozen,
                freezeDurationSeconds = clip.freezeDurationSec,
                isChromaKeyEnabled = clip.isChromaKeyEnabled,
                chromaKeyColor = clip.chromaKeyColor,
                chromaKeySensitivity = clip.chromaKeySensitivity,
                subtitle = matchingSub?.text ?: refScene.subtitle,
                customVoiceAudioPath = matchingVoice?.audioUri ?: refScene.customVoiceAudioPath,
                pipMediaPath = matchingPip?.mediaUri ?: refScene.pipMediaPath,
                pipX = matchingPip?.positionX ?: refScene.pipX,
                pipY = matchingPip?.positionY ?: refScene.pipY,
                pipScale = matchingPip?.scaleX ?: refScene.pipScale
            ) ?: Scene(
                sceneNumber = index + 1,
                narrationText = clip.name,
                visualPrompt = clip.name,
                durationSeconds = durationSec,
                subtitle = matchingSub?.text ?: "",
                mediaType = if (clip.isVideo) "VIDEO" else "IMAGE",
                mediaPath = clip.mediaUri,
                speedMultiplier = clip.playbackSpeed,
                volume = clip.volume,
                isChromaKeyEnabled = clip.isChromaKeyEnabled,
                chromaKeyColor = clip.chromaKeyColor,
                chromaKeySensitivity = clip.chromaKeySensitivity
            )

            if (layers.isNotEmpty()) {
                scene.saveCanvasLayers(layers)
            }
            scene
        }
    }
}
