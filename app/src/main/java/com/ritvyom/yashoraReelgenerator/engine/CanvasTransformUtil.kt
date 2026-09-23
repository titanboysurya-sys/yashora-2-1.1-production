package com.ritvyom.yashoraReelgenerator.engine

import android.opengl.Matrix
import com.ritvyom.yashoraReelgenerator.core.model.CanvasAspectRatio
import com.ritvyom.yashoraReelgenerator.core.model.CanvasScaleMode
import com.ritvyom.yashoraReelgenerator.core.model.Transform2D

/**
 * CanvasTransformUtil:
 * Deterministic canvas transformation and coordinate projection mathematics shared
 * identically between Preview Rendering and Export Rendering.
 *
 * Coordinate System Documentation:
 * 1. Screen / Normalized Canvas Space:
 *    - Top-Left: (0.0, 0.0)
 *    - Center: (0.5, 0.5)
 *    - Bottom-Right: (1.0, 1.0)
 *    - Normalized displacement: dx = (x - 0.5), dy = (y - 0.5)
 *
 * 2. OpenGL Normalized Device Coordinates (NDC):
 *    - Center: (0.0, 0.0)
 *    - X spans [-1.0 .. 1.0], pointing Right
 *    - Y spans [-1.0 .. 1.0], pointing Up (inverted from screen space)
 *    - Conversion:
 *      X_ndc = (x - 0.5) * 2.0
 *      Y_ndc = -(y - 0.5) * 2.0
 *
 * 3. Transformation Order & Matrix Multiplication:
 *    M = T(translation) * R(rotation) * S(scale) * T(-anchor_offset)
 *    Where anchor_offset = ((anchorX - 0.5) * 2.0, -(anchorY - 0.5) * 2.0)
 *    Rotation and scaling are evaluated around the defined anchor point.
 */
object CanvasTransformUtil {

    data class AspectFitting(
        val scaleX: Float,
        val scaleY: Float,
        val contentAspect: Float,
        val canvasAspect: Float
    )

    /**
     * Computes the base scale factor for fitting or filling content inside a canvas of given aspect ratio.
     */
    fun calculateAspectFitting(
        sourceWidth: Int,
        sourceHeight: Int,
        canvasWidth: Int,
        canvasHeight: Int,
        scaleMode: CanvasScaleMode
    ): AspectFitting {
        val srcW = sourceWidth.coerceAtLeast(1).toFloat()
        val srcH = sourceHeight.coerceAtLeast(1).toFloat()
        val cnvW = canvasWidth.coerceAtLeast(1).toFloat()
        val cnvH = canvasHeight.coerceAtLeast(1).toFloat()

        val contentAspect = srcW / srcH
        val canvasAspect = cnvW / cnvH

        return when (scaleMode) {
            CanvasScaleMode.CONTAIN -> {
                // Content fits fully inside canvas; letterboxing/pillarboxing occurs
                if (contentAspect > canvasAspect) {
                    // Content is wider than canvas -> pillarbox (fit width)
                    val fitScaleY = canvasAspect / contentAspect
                    AspectFitting(1.0f, fitScaleY, contentAspect, canvasAspect)
                } else {
                    // Content is taller than canvas -> letterbox (fit height)
                    val fitScaleX = contentAspect / canvasAspect
                    AspectFitting(fitScaleX, 1.0f, contentAspect, canvasAspect)
                }
            }
            CanvasScaleMode.COVER -> {
                // Content completely covers canvas; overflow edges are cropped
                if (contentAspect > canvasAspect) {
                    // Content is wider -> scale up height to 1.0, scaleX expands
                    val coverScaleX = contentAspect / canvasAspect
                    AspectFitting(coverScaleX, 1.0f, contentAspect, canvasAspect)
                } else {
                    // Content is taller -> scale up width to 1.0, scaleY expands
                    val coverScaleY = canvasAspect / contentAspect
                    AspectFitting(1.0f, coverScaleY, contentAspect, canvasAspect)
                }
            }
            CanvasScaleMode.CROP -> {
                // Strict 1.0 scale, relying on GPU crop rect
                AspectFitting(1.0f, 1.0f, contentAspect, canvasAspect)
            }
            CanvasScaleMode.CUSTOM -> {
                AspectFitting(1.0f, 1.0f, contentAspect, canvasAspect)
            }
        }
    }

    /**
     * Builds the final 4x4 MVP transformation matrix applying translation, rotation, scale, and anchor.
     * Output matrix is stored into [outMatrix].
     */
    fun buildTransformMatrix(
        transform: Transform2D,
        baseFitting: AspectFitting,
        outMatrix: FloatArray
    ) {
        Matrix.setIdentityM(outMatrix, 0)

        // 1. Translation: normalized [-0.5 .. 0.5] mapped to NDC [-1.0 .. 1.0]
        val ndcTransX = transform.translationX * 2.0f
        val ndcTransY = -transform.translationY * 2.0f
        Matrix.translateM(outMatrix, 0, ndcTransX, ndcTransY, 0.0f)

        // 2. Rotation around Z axis
        if (transform.rotationDegrees != 0f) {
            Matrix.rotateM(outMatrix, 0, -transform.rotationDegrees, 0.0f, 0.0f, 1.0f)
        }

        // 3. Scaling: combine layer transform scale with base aspect fitting
        val finalScaleX = transform.scaleX * baseFitting.scaleX
        val finalScaleY = transform.scaleY * baseFitting.scaleY
        Matrix.scaleM(outMatrix, 0, finalScaleX, finalScaleY, 1.0f)

        // 4. Anchor Point Pivot Offset
        val anchorOffsetX = (transform.anchorX - 0.5f) * 2.0f
        val anchorOffsetY = -(transform.anchorY - 0.5f) * 2.0f
        if (anchorOffsetX != 0f || anchorOffsetY != 0f) {
            Matrix.translateM(outMatrix, 0, -anchorOffsetX, -anchorOffsetY, 0.0f)
        }
    }

    /**
     * Resolves standard pixel dimensions for a given canvas aspect ratio.
     */
    fun getCanonicalDimensions(aspectRatio: CanvasAspectRatio, targetResolution: Int = 1080): Pair<Int, Int> {
        return when (aspectRatio) {
            CanvasAspectRatio.RATIO_9_16 -> Pair(targetResolution, (targetResolution * 16) / 9)
            CanvasAspectRatio.RATIO_16_9 -> Pair((targetResolution * 16) / 9, targetResolution)
            CanvasAspectRatio.RATIO_1_1 -> Pair(targetResolution, targetResolution)
            CanvasAspectRatio.RATIO_4_5 -> Pair(targetResolution, (targetResolution * 5) / 4)
            CanvasAspectRatio.RATIO_3_4 -> Pair(targetResolution, (targetResolution * 4) / 3)
            CanvasAspectRatio.RATIO_21_9 -> Pair((targetResolution * 21) / 9, targetResolution)
        }
    }
}
