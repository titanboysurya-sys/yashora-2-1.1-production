package com.ritvyom.yashoraReelgenerator.presentation.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import kotlin.math.cos
import kotlin.math.sin

/**
 * A highly polished, dynamic cinematic lens & glowing play button logo
 * drawn procedurally using Jetpack Compose Canvas to present a premium
 * and modern video production brand aesthetic.
 */
@Composable
fun FilmReelLogo(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.aspectRatio(1f)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height
            val center = Offset(width / 2f, height / 2f)
            val minSize = minOf(width, height)

            // Dynamic Radii
            val outerGlowRadius = minSize * 0.48f
            val apertureRingRadius = minSize * 0.40f
            val filmTrackRadius = minSize * 0.32f
            val lensCoreRadius = minSize * 0.22f
            val playButtonSize = minSize * 0.11f

            // Premium Video Production Brand Palette
            val neonCyan = Color(0xFF00F0FF)
            val electricBlue = Color(0xFF0072FF)
            val deepMagenta = Color(0xFFFF007F)
            val royalPurple = Color(0xFF7B2CBF)
            val midnightVoilet = Color(0xFF1E0338)
            val premiumDarkCore = Color(0xFF05010B)
            val pureWhite = Color.White

            // 1. Draw soft ambient background neon glow
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        deepMagenta.copy(alpha = 0.30f),
                        electricBlue.copy(alpha = 0.12f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = outerGlowRadius
                ),
                center = center,
                radius = outerGlowRadius
            )

            // 2. Beautiful Camera Aperture / Outer Precision Ring
            drawCircle(
                brush = Brush.sweepGradient(
                    colors = listOf(neonCyan, electricBlue, deepMagenta, royalPurple, neonCyan),
                    center = center
                ),
                center = center,
                radius = apertureRingRadius,
                style = Stroke(width = minSize * 0.025f)
            )

            // Draw precision millimeter tick-marks along the lens ring
            val totalTicks = 16
            val tickStartRadius = apertureRingRadius + (minSize * 0.015f)
            val tickEndRadius = apertureRingRadius + (minSize * 0.04f)
            for (i in 0 until totalTicks) {
                val angleDeg = i * (360f / totalTicks)
                val angleRad = Math.toRadians(angleDeg.toDouble())
                val cosA = cos(angleRad).toFloat()
                val sinA = sin(angleRad).toFloat()

                val startPoint = Offset(center.x + tickStartRadius * cosA, center.y + tickStartRadius * sinA)
                val endPoint = Offset(center.x + tickEndRadius * cosA, center.y + tickEndRadius * sinA)

                drawLine(
                    color = if (i % 4 == 0) neonCyan.copy(alpha = 0.85f) else Color.White.copy(alpha = 0.35f),
                    start = startPoint,
                    end = endPoint,
                    strokeWidth = if (i % 4 == 0) minSize * 0.008f else minSize * 0.004f,
                    cap = StrokeCap.Round
                )
            }

            // 3. Cinematic Film Spin Track
            drawCircle(
                brush = Brush.linearGradient(
                    colors = listOf(royalPurple.copy(alpha = 0.6f), deepMagenta.copy(alpha = 0.8f)),
                    start = Offset(0f, 0f),
                    end = Offset(width, height)
                ),
                center = center,
                radius = filmTrackRadius,
                style = Stroke(width = minSize * 0.07f)
            )

            // Orbiting stylish sprocket holes along the film track (Reel flow)
            val sprocketCount = 12
            val sprocketWidth = minSize * 0.038f
            val sprocketHeight = minSize * 0.02f
            for (i in 0 until sprocketCount) {
                val angleDegrees = i * (360f / sprocketCount) + 15f
                val angleRad = Math.toRadians(angleDegrees.toDouble())
                val sprocketX = center.x + (filmTrackRadius * cos(angleRad)).toFloat()
                val sprocketY = center.y + (filmTrackRadius * sin(angleRad)).toFloat()

                withTransform({
                    rotate(
                        degrees = angleDegrees.toFloat() + 90f,
                        pivot = Offset(sprocketX, sprocketY)
                    )
                }) {
                    drawRoundRect(
                        color = pureWhite.copy(alpha = 0.9f),
                        topLeft = Offset(sprocketX - sprocketWidth / 2f, sprocketY - sprocketHeight / 2f),
                        size = Size(sprocketWidth, sprocketHeight),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(sprocketHeight * 0.4f, sprocketHeight * 0.4f)
                    )
                }
            }

            // 4. Central Premium Camera Lens Core (Dark Glass Plate)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(midnightVoilet, premiumDarkCore),
                    center = center,
                    radius = lensCoreRadius
                ),
                center = center,
                radius = lensCoreRadius
            )

            // Double inner neon rim to create 3D camera depth
            drawCircle(
                color = neonCyan.copy(alpha = 0.7f),
                center = center,
                radius = lensCoreRadius - minSize * 0.012f,
                style = Stroke(width = minSize * 0.008f)
            )

            // 5. Stylized Glassmorphic Play Button / Film Editor Core symbol
            val playPath = Path().apply {
                // Place perfect equilateral play triangle points
                val offsetTip = playButtonSize * 1.1f
                val offsetBaseX = playButtonSize * 0.65f
                val offsetBaseY = playButtonSize * 0.95f

                moveTo(center.x + offsetTip, center.y) // Peak tip
                lineTo(center.x - offsetBaseX, center.y - offsetBaseY) // Top-left base
                lineTo(center.x - offsetBaseX, center.y + offsetBaseY) // Bottom-left base
                close()
            }

            // Draw shadow/glow behind the play symbol
            drawPath(
                path = playPath,
                color = deepMagenta.copy(alpha = 0.4f)
            )

            // Fill play symbol with elegant cyan-to-white glass sheen gradient
            drawPath(
                path = playPath,
                brush = Brush.linearGradient(
                    colors = listOf(pureWhite, neonCyan, electricBlue),
                    start = Offset(center.x - playButtonSize, center.y - playButtonSize),
                    end = Offset(center.x + playButtonSize, center.y + playButtonSize)
                )
            )

            // Semi-transparent high-contrast inner glossy rim
            drawPath(
                path = playPath,
                color = pureWhite.copy(alpha = 0.75f),
                style = Stroke(width = minSize * 0.012f)
            )
        }
    }
}

