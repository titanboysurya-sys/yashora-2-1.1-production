package com.ritvyom.yashoraReelgenerator.core.model

import com.ritvyom.yashoraReelgenerator.engine.text.TextStyleSpec
import com.ritvyom.yashoraReelgenerator.engine.text.TextType
import java.io.Serializable
import java.util.UUID

/**
 * Aspect Ratio enumeration defining canonical coordinate frames and dimensions.
 */
enum class CanvasAspectRatio(val ratioString: String, val widthRatio: Float, val heightRatio: Float) {
    RATIO_9_16("9:16", 9f, 16f),
    RATIO_16_9("16:9", 16f, 9f),
    RATIO_1_1("1:1", 1f, 1f),
    RATIO_4_5("4:5", 4f, 5f),
    RATIO_3_4("3:4", 3f, 4f),
    RATIO_21_9("21:9", 21f, 9f);

    companion object {
        fun fromString(str: String): CanvasAspectRatio {
            return entries.firstOrNull { it.ratioString.equals(str.trim(), ignoreCase = true) } ?: RATIO_9_16
        }
    }
}

/**
 * Canvas scaling behavior relative to source media.
 */
enum class CanvasScaleMode {
    CONTAIN,
    COVER,
    CROP,
    CUSTOM
}

/**
 * Canvas background fill style.
 */
enum class CanvasBackgroundStyle {
    SOLID_COLOR,
    BLURRED_MEDIA,
    GRADIENT
}

/**
 * Canvas configuration for canonical viewport and projection.
 */
data class CanvasConfig(
    val aspectRatio: CanvasAspectRatio = CanvasAspectRatio.RATIO_9_16,
    val scaleMode: CanvasScaleMode = CanvasScaleMode.CONTAIN,
    val backgroundStyle: CanvasBackgroundStyle = CanvasBackgroundStyle.BLURRED_MEDIA,
    val backgroundColorHex: String = "#0C0914",
    val gradientColorsHex: List<String> = listOf("#0C0914", "#1E1830"),
    val blurRadiusDp: Float = 24f,
    val outputWidth: Int = 1080,
    val outputHeight: Int = 1920
) : Serializable

/**
 * Track category within the multi-track timeline hierarchy.
 */
enum class TrackType {
    MAIN_VIDEO,
    OVERLAY_VIDEO_PIP,
    AUDIO_ORIGINAL,
    AUDIO_BGM,
    AUDIO_VOICEOVER,
    AUDIO_SFX,
    TEXT_TITLE,
    TEXT_SUBTITLE,
    STICKER,
    EFFECT_ADJUSTMENT
}

/**
 * Base interface representing any temporal element placed on a track.
 */
interface TimelineClipItem : Serializable {
    val id: String
    val trackId: String
    val name: String
    val startTimeUs: Long
    val durationUs: Long
    val endTimeUs: Long get() = startTimeUs + durationUs
    val sourceStartUs: Long
    val sourceDurationUs: Long
    val isMuted: Boolean
    val isLocked: Boolean
    val isVisible: Boolean
}

/**
 * High-definition Video / Image Clip placed on a Video track.
 */
data class VideoClipItem(
    override val id: String = UUID.randomUUID().toString(),
    override val trackId: String,
    override val name: String = "Clip",
    override val startTimeUs: Long = 0L,
    override val durationUs: Long = 5_000_000L,
    override val sourceStartUs: Long = 0L,
    override val sourceDurationUs: Long = 5_000_000L,
    override val isMuted: Boolean = false,
    override val isLocked: Boolean = false,
    override val isVisible: Boolean = true,

    val mediaUri: String,
    val isVideo: Boolean = true,
    val playbackSpeed: Float = 1.0f,
    val volume: Float = 1.0f,
    val audioFadeInDurationUs: Long = 0L,
    val audioFadeOutDurationUs: Long = 0L,

    // Transforms & Geometry
    val positionX: Float = 0.5f,
    val positionY: Float = 0.5f,
    val scaleX: Float = 1.0f,
    val scaleY: Float = 1.0f,
    val rotationDegrees: Float = 0f,
    val anchorX: Float = 0.5f,
    val anchorY: Float = 0.5f,
    val opacity: Float = 1.0f,
    val zIndex: Int = 0,
    val crop: NormalizedCropRect = NormalizedCropRect(),
    val blendMode: LayerBlendMode = LayerBlendMode.NORMAL,
    val isFlippedHorizontal: Boolean = false,
    val isFlippedVertical: Boolean = false,

    // Color Grading
    val brightness: Float = 0.0f,
    val contrast: Float = 1.0f,
    val saturation: Float = 1.0f,
    val warmth: Float = 0.0f,
    val exposure: Float = 0.0f,
    val tint: Float = 0.0f,
    val vignette: Float = 0.0f,
    val sharpen: Float = 0.0f,
    val highlights: Float = 0.0f,
    val shadows: Float = 0.0f,
    val filterName: String = "Normal",
    val filterIntensity: Int = 100,

    // Transitions
    val transitionInType: String = "None",
    val transitionInDurationMs: Long = 0L,
    val transitionOutType: String = "None",
    val transitionOutDurationMs: Long = 0L,

    // Motion & Effects
    val inAnimation: String = "None",
    val outAnimation: String = "None",
    val comboAnimation: String = "None",
    val animationDurationSec: Float = 0.5f,
    val effectName: String = "None",
    val effectIntensity: Int = 100,

    // Voice / Audio FX
    val voiceEffect: String = "None",

    // Time Remapping
    val isReversed: Boolean = false,
    val isFrozen: Boolean = false,
    val freezeDurationSec: Float = 2.0f,

    // Chroma Key
    val isChromaKeyEnabled: Boolean = false,
    val chromaKeyColor: String = "Green",
    val chromaKeySensitivity: Float = 0.5f,

    // Keyframes
    val keyframeTracks: List<KeyframeTrack<*>> = emptyList()
) : TimelineClipItem

/**
 * Audio Clip for BGM, narration, or sound effects.
 */
data class AudioClipItem(
    override val id: String = UUID.randomUUID().toString(),
    override val trackId: String,
    override val name: String = "Audio",
    override val startTimeUs: Long = 0L,
    override val durationUs: Long = 5_000_000L,
    override val sourceStartUs: Long = 0L,
    override val sourceDurationUs: Long = 5_000_000L,
    override val isMuted: Boolean = false,
    override val isLocked: Boolean = false,
    override val isVisible: Boolean = true,

    val audioUri: String,
    val volume: Float = 1.0f,
    val pan: Float = 0.0f,
    val playbackSpeed: Float = 1.0f,
    val fadeInDurationUs: Long = 0L,
    val fadeOutDurationUs: Long = 0L,
    val loop: Boolean = false,
    val voiceEffect: String = "None",
    val keyframeTracks: List<KeyframeTrack<*>> = emptyList()
) : TimelineClipItem

/**
 * Text / Caption Clip.
 */
data class TextClipItem(
    override val id: String = UUID.randomUUID().toString(),
    override val trackId: String,
    override val name: String = "Text",
    override val startTimeUs: Long = 0L,
    override val durationUs: Long = 3_000_000L,
    override val sourceStartUs: Long = 0L,
    override val sourceDurationUs: Long = 3_000_000L,
    override val isMuted: Boolean = false,
    override val isLocked: Boolean = false,
    override val isVisible: Boolean = true,

    val text: String,
    val textType: TextType = TextType.TEXT,
    val fontName: String = "TikTok Style",
    val fontSizeSp: Float = 24f,
    val textColorHex: String = "#FFFFFF",
    val backgroundColorHex: String = "#99000000",
    val strokeColorHex: String? = null,
    val strokeWidth: Float = 0f,
    val shadowColorHex: String? = "#80000000",
    val shadowRadius: Float = 4f,
    val alignment: String = "Center",

    // Extended Style Specification
    val styleSpec: TextStyleSpec = TextStyleSpec(
        fontName = fontName,
        fontSizeSp = fontSizeSp,
        textColorHex = textColorHex,
        backgroundColorHex = backgroundColorHex,
        strokeColorHex = strokeColorHex,
        strokeWidthDp = strokeWidth,
        shadowColorHex = shadowColorHex,
        shadowRadiusDp = shadowRadius
    ),

    // Transform
    val positionX: Float = 0.5f,
    val positionY: Float = 0.8f,
    val scale: Float = 1.0f,
    val scaleX: Float = 1.0f,
    val scaleY: Float = 1.0f,
    val rotationDegrees: Float = 0f,
    val opacity: Float = 1.0f,
    val anchorX: Float = 0.5f,
    val anchorY: Float = 0.5f,
    val zIndex: Int = 40,

    // Animation
    val animationType: String = "Pop",
    val animationDurationSec: Float = 0.5f,
    val keyframeTracks: List<KeyframeTrack<*>> = emptyList()
) : TimelineClipItem

/**
 * Sticker / Graphic overlay Clip.
 */
data class StickerClipItem(
    override val id: String = UUID.randomUUID().toString(),
    override val trackId: String,
    override val name: String = "Sticker",
    override val startTimeUs: Long = 0L,
    override val durationUs: Long = 3_000_000L,
    override val sourceStartUs: Long = 0L,
    override val sourceDurationUs: Long = 3_000_000L,
    override val isMuted: Boolean = false,
    override val isLocked: Boolean = false,
    override val isVisible: Boolean = true,

    val stickerIdentifier: String,
    val positionX: Float = 0.5f,
    val positionY: Float = 0.5f,
    val scale: Float = 1.0f,
    val rotationDegrees: Float = 0f,
    val opacity: Float = 1.0f,
    val anchorX: Float = 0.5f,
    val anchorY: Float = 0.5f,
    val keyframeTracks: List<KeyframeTrack<*>> = emptyList()
) : TimelineClipItem

/**
 * A Track holds a sequence of Clips belonging to a specific TrackType.
 */
data class TimelineTrack(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val type: TrackType,
    val isMuted: Boolean = false,
    val isLocked: Boolean = false,
    val isVisible: Boolean = true,
    val volume: Float = 1.0f,
    val clips: List<TimelineClipItem> = emptyList()
) : Serializable {
    val totalDurationUs: Long
        get() = clips.maxOfOrNull { it.endTimeUs } ?: 0L
}

/**
 * Canonical timeline marker representing an editorial point, beat, or chapter.
 */
data class TimelineMarker(
    val id: String = UUID.randomUUID().toString(),
    val timeUs: Long,
    val label: String,
    val category: String = "General",
    val colorHex: String = "#FF9800"
) : Serializable

/**
 * The Canonical Timeline: Single Source of Truth for the entire composition.
 */
data class CanonicalTimeline(
    val tracks: List<TimelineTrack> = emptyList(),
    val canvasConfig: CanvasConfig = CanvasConfig(),
    val timebaseFps: Int = 30,
    val markers: List<TimelineMarker> = emptyList()
) : Serializable {

    val totalDurationUs: Long
        get() = tracks.maxOfOrNull { it.totalDurationUs } ?: 0L

    val mainVideoTrack: TimelineTrack?
        get() = tracks.firstOrNull { it.type == TrackType.MAIN_VIDEO }

    val audioTracks: List<TimelineTrack>
        get() = tracks.filter { it.type == TrackType.AUDIO_ORIGINAL || it.type == TrackType.AUDIO_BGM || it.type == TrackType.AUDIO_VOICEOVER || it.type == TrackType.AUDIO_SFX }

    val overlayTracks: List<TimelineTrack>
        get() = tracks.filter { it.type == TrackType.OVERLAY_VIDEO_PIP || it.type == TrackType.STICKER }

    val textTracks: List<TimelineTrack>
        get() = tracks.filter { it.type == TrackType.TEXT_TITLE || it.type == TrackType.TEXT_SUBTITLE }

    fun findClipById(clipId: String): TimelineClipItem? {
        for (track in tracks) {
            val found = track.clips.firstOrNull { it.id == clipId }
            if (found != null) return found
        }
        return null
    }
}

/**
 * Canonical Project: Root container encapsulating metadata, timeline, and export preferences.
 */
data class CanonicalProject(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "Untitled Reel",
    val createdAtMs: Long = System.currentTimeMillis(),
    val updatedAtMs: Long = System.currentTimeMillis(),
    val timeline: CanonicalTimeline = CanonicalTimeline(),
    val targetResolution: String = "1080p",
    val targetFps: Int = 30,
    val targetBitrateBps: Int = 8_000_000,
    val authorNotes: String = ""
) : Serializable
