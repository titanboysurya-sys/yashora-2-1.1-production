package com.ritvyom.yashoraReelgenerator.core.rendering

import com.ritvyom.yashoraReelgenerator.core.model.CanvasConfig
import com.ritvyom.yashoraReelgenerator.core.model.ColorGradingConfig
import com.ritvyom.yashoraReelgenerator.core.model.LayerBlendMode
import com.ritvyom.yashoraReelgenerator.core.model.Transform2D
import com.ritvyom.yashoraReelgenerator.core.model.VisualEffectSpec
import com.ritvyom.yashoraReelgenerator.engine.text.TextStyleSpec
import java.io.Serializable

/**
 * Encapsulates the exact temporal state for rendering a frame.
 */
data class FrameTime(
    val presentationTimeUs: Long,
    val frameIndex: Int,
    val fps: Int = 30,
    val totalDurationUs: Long = 0L
) : Serializable {
    val progress: Float
        get() = if (totalDurationUs > 0L) (presentationTimeUs.toFloat() / totalDurationUs).coerceIn(0f, 1f) else 0f
}

/**
 * Execution passes in the Render Graph pipeline.
 */
enum class RenderPassType {
    SOURCE_DECODE,
    GEOMETRIC_TRANSFORM,
    CROP_AND_MASK,
    COLOR_GRADING,
    GPU_EFFECT,
    OVERLAY_TEXT_AND_STICKER,
    SCENE_TRANSITION,
    CANVAS_COMPOSITE,
    OUTPUT_WRITE
}

/**
 * Individual Node in the Render Graph representing a layer or processing pass.
 */
data class RenderNode(
    val id: String,
    val name: String,
    val zIndex: Int,
    val passType: RenderPassType,
    val sourceUri: String? = null,
    val isVideoSource: Boolean = true,
    val transform: Transform2D = Transform2D(),
    val colorGrading: ColorGradingConfig = ColorGradingConfig(),
    val effects: List<VisualEffectSpec> = emptyList(),
    val blendMode: LayerBlendMode = LayerBlendMode.NORMAL,
    val opacity: Float = 1.0f,
    val isVisible: Boolean = true,
    val textPayload: String? = null,
    val textStylePayload: Map<String, Any>? = null,
    val textStyleSpec: TextStyleSpec? = null,
    val transitionType: String? = null,
    val transitionProgress: Float = 0.0f,
    val maskSpec: com.ritvyom.yashoraReelgenerator.engine.effects.MaskSpec? = null,
    val chromaKeySpec: com.ritvyom.yashoraReelgenerator.engine.effects.ChromaKeySpec? = null
) : Serializable

/**
 * Contextual state provided to each render pass.
 */
data class RenderContext(
    val canvasConfig: CanvasConfig,
    val viewportWidth: Int,
    val viewportHeight: Int,
    val isOfflineExport: Boolean = false,
    val hardwareAccelerated: Boolean = true
)

/**
 * The unified Render Graph interface.
 * Builds the deterministic topological execution graph for any given [frameTime].
 */
interface IRenderGraph {
    fun evaluateNodesForTime(frameTime: FrameTime, renderContext: RenderContext): List<RenderNode>
}
