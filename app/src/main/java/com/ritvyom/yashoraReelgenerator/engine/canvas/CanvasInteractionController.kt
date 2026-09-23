package com.ritvyom.yashoraReelgenerator.engine.canvas

import kotlin.math.abs
import kotlin.math.round

/**
 * Handle type for the professional transform bounding box.
 */
enum class BoundingBoxHandle {
    NONE,
    TOP_LEFT_RESIZE,
    TOP_RIGHT_RESIZE,
    BOTTOM_LEFT_RESIZE,
    BOTTOM_RIGHT_RESIZE,
    ROTATE,
    DELETE,
    DUPLICATE,
    EDIT_STYLE,
    LAYERS
}

/**
 * Snap guideline representation for WYSIWYG canvas alignment.
 */
data class AlignmentGuide(
    val isVertical: Boolean,
    val positionFraction: Float, // 0.0 to 1.0 (e.g. 0.5 for center line)
    val label: String = ""
)

/**
 * Result of snapping calculations.
 */
data class SnapResult(
    val snappedX: Float,
    val snappedY: Float,
    val snappedRotation: Float,
    val activeGuides: List<AlignmentGuide>
)

/**
 * CanvasInteractionController:
 * High-performance state controller and mathematical engine for direct manipulation,
 * snapping to alignment guides, bounding box handles, and live WYSIWYG parity.
 */
class CanvasInteractionController(
    val snapToleranceFraction: Float = 0.025f,
    val rotationSnapToleranceDegrees: Float = 4.0f
) {
    // Standard alignment snap lines across the viewport
    private val horizontalSnapLines = listOf(
        0.15f to "Top Safe",
        0.50f to "Center Y",
        0.82f to "Subtitle / Lower Third",
        0.85f to "Bottom Safe"
    )

    private val verticalSnapLines = listOf(
        0.10f to "Left Margin",
        0.50f to "Center X",
        0.90f to "Right Margin"
    )

    private val rotationSnapAngles = listOf(0f, 45f, 90f, 135f, 180f, 225f, 270f, 315f, 360f)

    /**
     * Calculates snapped position and rotation, outputting any active alignment guidelines.
     */
    fun computeSnap(
        rawX: Float,
        rawY: Float,
        rawRotation: Float,
        enableSnapping: Boolean = true
    ): SnapResult {
        if (!enableSnapping) {
            return SnapResult(
                snappedX = rawX.coerceIn(0.02f, 0.98f),
                snappedY = rawY.coerceIn(0.02f, 0.98f),
                snappedRotation = (rawRotation % 360f + 360f) % 360f,
                activeGuides = emptyList()
            )
        }

        var finalX = rawX
        var finalY = rawY
        var finalRot = (rawRotation % 360f + 360f) % 360f
        val activeGuides = mutableListOf<AlignmentGuide>()

        // 1. Horizontal center / safe lines
        for ((lineY, label) in horizontalSnapLines) {
            if (abs(rawY - lineY) <= snapToleranceFraction) {
                finalY = lineY
                activeGuides.add(AlignmentGuide(isVertical = false, positionFraction = lineY, label = label))
                break
            }
        }

        // 2. Vertical center / margin lines
        for ((lineX, label) in verticalSnapLines) {
            if (abs(rawX - lineX) <= snapToleranceFraction) {
                finalX = lineX
                activeGuides.add(AlignmentGuide(isVertical = true, positionFraction = lineX, label = label))
                break
            }
        }

        // 3. Rotation snapping (0°, 90°, 180°, 270°)
        for (targetAngle in rotationSnapAngles) {
            val diff = abs(finalRot - targetAngle)
            if (diff <= rotationSnapToleranceDegrees || abs(diff - 360f) <= rotationSnapToleranceDegrees) {
                finalRot = if (targetAngle == 360f) 0f else targetAngle
                break
            }
        }

        return SnapResult(
            snappedX = finalX.coerceIn(0.02f, 0.98f),
            snappedY = finalY.coerceIn(0.02f, 0.98f),
            snappedRotation = finalRot,
            activeGuides = activeGuides
        )
    }

    /**
     * Direct pinch/zoom resize scaling constraint
     */
    fun computeScale(currentScale: Float, zoomDelta: Float, minScale: Float = 0.25f, maxScale: Float = 4.5f): Float {
        return (currentScale * zoomDelta).coerceIn(minScale, maxScale)
    }
}
