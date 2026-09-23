package com.ritvyom.yashoraReelgenerator.presentation.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke

/**
 * Procedural Vector & Shader implementation of the official Yashora geometric "Y" brand monogram.
 * Perfectly reproduces the clean geometric angles, twin outer wings, inner V trough, and central trunk.
 */
@Composable
fun YashoraLogo(
    modifier: Modifier = Modifier,
    primaryColor: Color = Color.White,
    glowColor: Color = Color(0xFF00F0FF),
    secondaryGlowColor: Color = Color(0xFF9D4EDD),
    enableAnimation: Boolean = true
) {
    val infiniteTransition = rememberInfiniteTransition(label = "YashoraLogoGlow")
    
    val glowPulse by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "GlowPulse"
    )

    val sweepAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(6000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "SweepAngle"
    )

    Box(modifier = modifier.aspectRatio(1f)) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val center = Offset(w / 2f, h / 2f)

            // Dynamic scale factor mapping 108 reference units to canvas dimensions
            val sx = w / 108f
            val sy = h / 108f

            // 1. Ambient Background Radial Glow
            val glowAlpha = if (enableAnimation) glowPulse else 0.6f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        glowColor.copy(alpha = 0.35f * glowAlpha),
                        secondaryGlowColor.copy(alpha = 0.18f * glowAlpha),
                        Color.Transparent
                    ),
                    center = Offset(w * 0.5f, h * 0.42f),
                    radius = w * 0.55f
                ),
                center = center,
                radius = w * 0.55f
            )

            // 2. Subtle Outer Neon Orbit Ring (for cinematic polish)
            if (enableAnimation) {
                drawCircle(
                    brush = Brush.sweepGradient(
                        colors = listOf(
                            Color.Transparent,
                            glowColor.copy(alpha = 0.35f),
                            secondaryGlowColor.copy(alpha = 0.45f),
                            Color.Transparent
                        ),
                        center = center
                    ),
                    center = center,
                    radius = w * 0.47f,
                    style = Stroke(width = 2.5f * sx, cap = StrokeCap.Round)
                )
            }

            // 3. Construct the Geometric Yashora Paths (Scaled with comfortable top padding)
            // Left Outer Wing
            val leftWingPath = Path().apply {
                moveTo(24.5f * sx, 29.5f * sy)
                lineTo(31f * sx, 29.5f * sy)
                lineTo(47.4f * sx, 54.8f * sy)
                lineTo(40.9f * sx, 54.8f * sy)
                close()
            }

            // Right Outer Wing
            val rightWingPath = Path().apply {
                moveTo(77f * sx, 29.5f * sy)
                lineTo(83.5f * sx, 29.5f * sy)
                lineTo(67.1f * sx, 54.8f * sy)
                lineTo(60.6f * sx, 54.8f * sy)
                close()
            }

            // Central Trunk & Inner V
            val centerTrunkPath = Path().apply {
                moveTo(35.1f * sx, 29.5f * sy)
                lineTo(41.7f * sx, 29.5f * sy)
                lineTo(54f * sx, 40.9f * sy)
                lineTo(66.3f * sx, 29.5f * sy)
                lineTo(72.9f * sx, 29.5f * sy)
                lineTo(59.7f * sx, 54f * sy)
                lineTo(59.7f * sx, 78.6f * sy)
                lineTo(48.3f * sx, 78.6f * sy)
                lineTo(48.3f * sx, 54f * sy)
                close()
            }

            // Draw subtle glow shadows behind paths
            val shadowColor = glowColor.copy(alpha = 0.25f * glowAlpha)
            drawPath(leftWingPath, color = shadowColor)
            drawPath(rightWingPath, color = shadowColor)
            drawPath(centerTrunkPath, color = shadowColor)

            // Fill geometric paths with clean high-contrast primary color / subtle vertical sheen
            val mainFillBrush = Brush.verticalGradient(
                colors = listOf(
                    primaryColor,
                    primaryColor.copy(alpha = 0.95f),
                    Color(0xFFE2E8F0)
                ),
                startY = 29.5f * sy,
                endY = 78.6f * sy
            )

            drawPath(leftWingPath, brush = mainFillBrush)
            drawPath(rightWingPath, brush = mainFillBrush)
            drawPath(centerTrunkPath, brush = mainFillBrush)

            // Outer crisp stroke accent
            val strokeColor = Color.White.copy(alpha = 0.4f)
            val strokeWidth = 1.2f * sx
            drawPath(leftWingPath, color = strokeColor, style = Stroke(width = strokeWidth))
            drawPath(rightWingPath, color = strokeColor, style = Stroke(width = strokeWidth))
            drawPath(centerTrunkPath, color = strokeColor, style = Stroke(width = strokeWidth))
        }
    }
}
