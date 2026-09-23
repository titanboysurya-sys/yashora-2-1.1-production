package com.ritvyom.yashoraReelgenerator.presentation.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ritvyom.yashoraReelgenerator.engine.canvas.AlignmentGuide
import com.ritvyom.yashoraReelgenerator.presentation.components.editor.YouCutOrange
import kotlin.math.roundToInt

/**
 * AlignmentGuidesOverlay:
 * Renders cyan/orange dashed magnetic snap lines and center crosshairs when direct manipulation
 * aligns with center axes or safe margins.
 */
@Composable
fun AlignmentGuidesOverlay(
    activeGuides: List<AlignmentGuide>,
    containerWidthPx: Float,
    containerHeightPx: Float,
    modifier: Modifier = Modifier
) {
    if (activeGuides.isEmpty()) return

    Canvas(modifier = modifier.fillMaxSize()) {
        val dashEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f), 0f)

        for (guide in activeGuides) {
            val isCenter = guide.positionFraction in 0.48f..0.52f
            val lineColor = if (isCenter) YouCutOrange else Color(0xFF00E5FF)

            if (guide.isVertical) {
                val x = guide.positionFraction * size.width
                drawLine(
                    color = lineColor,
                    start = Offset(x, 0f),
                    end = Offset(x, size.height),
                    strokeWidth = 2.dp.toPx(),
                    pathEffect = dashEffect
                )
            } else {
                val y = guide.positionFraction * size.height
                drawLine(
                    color = lineColor,
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 2.dp.toPx(),
                    pathEffect = dashEffect
                )
            }
        }
    }
}

/**
 * ProfessionalTransformBoundingBox:
 * Wraps any canvas element (Text, Sticker, PIP, Watermark) with a high-visibility
 * animated bounding border and handles for:
 * - Direct drag translation
 * - Corner scale handles
 * - Rotation handle (⟳)
 * - Quick action delete (✕), duplicate, layers, and style options
 */
@Composable
fun ProfessionalTransformBoundingBox(
    isSelected: Boolean,
    scale: Float,
    rotation: Float,
    boxWidth: Dp,
    boxHeight: Dp,
    modifier: Modifier = Modifier,
    onRotateDelta: (Float) -> Unit = {},
    onScaleDelta: (Float) -> Unit = {},
    onDelete: () -> Unit = {},
    onDuplicate: () -> Unit = {},
    onOpenLayers: () -> Unit = {},
    onOpenStyle: () -> Unit = {},
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .size(boxWidth, boxHeight)
            .graphicsLayer(
                scaleX = scale,
                scaleY = scale,
                rotationZ = rotation
            ),
        contentAlignment = Alignment.Center
    ) {
        // Inner editable element
        content()

        // Bounding box frame and handles when selected
        if (isSelected) {
            // Outline border
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .border(1.5.dp, YouCutOrange, RoundedCornerShape(4.dp))
            )

            // 1. Top-Left Delete handle (✕)
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset((-11).dp, (-11).dp)
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFE53935))
                    .border(1.dp, Color.White, CircleShape)
                    .pointerInput(Unit) {
                        detectTapGestures { onDelete() }
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Delete Layer",
                    tint = Color.White,
                    modifier = Modifier.size(14.dp)
                )
            }

            // 2. Top-Right Duplicate handle (⧉)
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(11.dp, (-11).dp)
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF1E88E5))
                    .border(1.dp, Color.White, CircleShape)
                    .pointerInput(Unit) {
                        detectTapGestures { onDuplicate() }
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = "Duplicate Layer",
                    tint = Color.White,
                    modifier = Modifier.size(13.dp)
                )
            }

            // 3. Bottom-Right Scale & Resize corner handle
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(11.dp, 11.dp)
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(YouCutOrange)
                    .border(1.dp, Color.White, CircleShape)
                    .pointerInput(Unit) {
                        detectDragGestures { _, dragAmount ->
                            val delta = (dragAmount.x + dragAmount.y) * 0.005f
                            onScaleDelta(1.0f + delta)
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Text("⤢", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Black)
            }

            // 4. Bottom-Left Rotate handle (⟳)
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .offset((-11).dp, 11.dp)
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF43A047))
                    .border(1.dp, Color.White, CircleShape)
                    .pointerInput(Unit) {
                        detectDragGestures { _, dragAmount ->
                            val rotDelta = (dragAmount.x - dragAmount.y) * 0.5f
                            onRotateDelta(rotDelta)
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Rotate Layer",
                    tint = Color.White,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}
