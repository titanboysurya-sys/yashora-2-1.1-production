package com.ritvyom.yashoraReelgenerator.presentation.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ritvyom.yashoraReelgenerator.presentation.components.YashoraLogo
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin

/**
 * High-end, cinematic animated splash screen featuring the official Yashora geometric "Y" brand mark,
 * animated particle field, neon backlights, and smooth fluid entrance.
 */
@Composable
fun SplashPage(onSplashFinished: () -> Unit) {
    var startAnimation by remember { mutableStateOf(false) }

    // Logo entrance animation
    val logoScale by animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0.4f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "LogoScale"
    )

    val contentAlpha by animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0f,
        animationSpec = tween(durationMillis = 900, delayMillis = 200),
        label = "ContentAlpha"
    )

    // Continuous ambient animations
    val infiniteTransition = rememberInfiniteTransition(label = "SplashAmbient")

    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "PulseScale"
    )

    val progressAnim by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ProgressAnim"
    )

    val particlePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 6.28318f,
        animationSpec = infiniteRepeatable(
            animation = tween(8000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ParticlePhase"
    )

    LaunchedEffect(Unit) {
        startAnimation = true
        delay(2600)
        onSplashFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF0F172A), // Deep obsidian navy glow center
                        Color(0xFF070B14), // Midnight dark transition
                        Color(0xFF020408)  // Pitch black borders
                    ),
                    radius = 1200f
                )
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                // Tap to skip splash instantly
                onSplashFinished()
            },
        contentAlignment = Alignment.Center
    ) {
        // 1. Futuristic ambient floating light particles & grid mesh
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            
            // Fixed particle positions with continuous harmonic floating
            val particles = listOf(
                Triple(0.2f, 0.25f, 0.008f),
                Triple(0.8f, 0.20f, 0.012f),
                Triple(0.15f, 0.70f, 0.010f),
                Triple(0.85f, 0.65f, 0.009f),
                Triple(0.3f, 0.85f, 0.014f),
                Triple(0.7f, 0.80f, 0.011f),
                Triple(0.5f, 0.15f, 0.007f),
                Triple(0.1f, 0.45f, 0.013f),
                Triple(0.9f, 0.40f, 0.010f)
            )

            particles.forEachIndexed { index, (relX, relY, relRadius) ->
                val offsetX = (sin(particlePhase + index.toFloat()) * 24f)
                val offsetY = (cos(particlePhase * 0.8f + index.toFloat()) * 24f)
                val px = relX * w + offsetX
                val py = relY * h + offsetY
                val pRadius = relRadius * w
                
                val pColor = when (index % 3) {
                    0 -> Color(0xFF00F0FF).copy(alpha = 0.35f)
                    1 -> Color(0xFF9D4EDD).copy(alpha = 0.30f)
                    else -> Color(0xFF38BDF8).copy(alpha = 0.40f)
                }

                drawCircle(
                    color = pColor,
                    center = Offset(px, py),
                    radius = pRadius
                )
            }
        }

        // 2. Central Hero Branding
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .padding(horizontal = 32.dp)
                .alpha(contentAlpha)
        ) {
            // Yashora Geometric Logo Mark with animated scale & glow
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(160.dp)
                    .scale(logoScale * pulseScale)
            ) {
                YashoraLogo(
                    modifier = Modifier.fillMaxSize(),
                    primaryColor = Color.White,
                    glowColor = Color(0xFF00F0FF),
                    secondaryGlowColor = Color(0xFF7B2CBF),
                    enableAnimation = true
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Brand Title: YASHORA
            Text(
                text = "YASHORA",
                color = Color.White,
                fontSize = 32.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 8.sp,
                fontFamily = FontFamily.SansSerif
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Glowing Cyber Accent Tagline
            Text(
                text = "AI REEL & VIDEO GENERATOR",
                color = Color(0xFF00F0FF),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 3.5.sp
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Subtitle Description
            Text(
                text = "Turn Ideas into Viral Reels in Seconds",
                color = Color(0xFF94A3B8),
                fontSize = 13.sp,
                fontWeight = FontWeight.Normal,
                letterSpacing = 0.5.sp
            )

            Spacer(modifier = Modifier.height(36.dp))

            // Sleek Minimalist Progress Capsule Bar
            Box(
                modifier = Modifier
                    .width(140.dp)
                    .height(3.5.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color(0xFF1E293B))
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val barWidth = size.width * 0.45f
                    val startX = (size.width + barWidth) * progressAnim - barWidth
                    
                    drawLine(
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color(0xFF00F0FF),
                                Color(0xFF9D4EDD),
                                Color.Transparent
                            ),
                            startX = startX,
                            endX = startX + barWidth
                        ),
                        start = Offset(startX, size.height / 2f),
                        end = Offset(startX + barWidth, size.height / 2f),
                        strokeWidth = size.height,
                        cap = StrokeCap.Round
                    )
                }
            }
        }

        // 3. Footer Edition Tag
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 28.dp)
                .alpha(contentAlpha * 0.7f)
        ) {
            Text(
                text = "v2.5 Ultra Pro • Powered by Yashora AI",
                color = Color(0xFF64748B),
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 1.sp
            )
        }
    }
}
