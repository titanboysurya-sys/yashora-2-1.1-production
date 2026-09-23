package com.ritvyom.yashoraReelgenerator.presentation.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ritvyom.yashoraReelgenerator.presentation.utils.localize

/**
 * A highly polished, custom-animated rotating lens loader with neon glowing rings.
 */
@Composable
fun PremiumCircularLoader(
    modifier: Modifier = Modifier,
    sizeDp: Int = 80
) {
    val infiniteTransition = rememberInfiniteTransition(label = "PremiumLoaderTransition")

    // Infinite rotation for the lens arc
    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "RotationAngle"
    )

    // Pulsing core glowing scale
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "PulseScale"
    )

    // Color definitions
    val neonCyan = Color(0xFF00F0FF)
    val electricBlue = Color(0xFF0072FF)
    val deepMagenta = Color(0xFFFF007F)
    val royalPurple = Color(0xFF7B2CBF)

    Box(
        modifier = modifier
            .size(sizeDp.dp)
            .aspectRatio(1f),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val minSize = minOf(size.width, size.height)
            val outerGlowRadius = minSize * 0.46f * pulseScale
            val progressRingRadius = minSize * 0.38f
            val coreGlassRadius = minSize * 0.22f

            // 1. Radial ambient background glow
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        deepMagenta.copy(alpha = 0.22f),
                        electricBlue.copy(alpha = 0.08f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = outerGlowRadius
                ),
                center = center,
                radius = outerGlowRadius
            )

            // 2. Central Glossy Dark Glass Core
            drawCircle(
                color = Color(0xFF0C061A),
                center = center,
                radius = coreGlassRadius
            )
            drawCircle(
                color = neonCyan.copy(alpha = 0.3f),
                center = center,
                radius = coreGlassRadius,
                style = Stroke(width = 2f)
            )

            // 3. Rotating Gradient Outer Lens Rings
            val sweepGradient = Brush.sweepGradient(
                colors = listOf(neonCyan, electricBlue, deepMagenta, royalPurple, neonCyan),
                center = center
            )

            // Draw a spinning active segment arc
            drawArc(
                brush = sweepGradient,
                startAngle = rotationAngle,
                sweepAngle = 100f,
                useCenter = false,
                topLeft = Offset(center.x - progressRingRadius, center.y - progressRingRadius),
                size = androidx.compose.ui.geometry.Size(progressRingRadius * 2, progressRingRadius * 2),
                style = Stroke(width = minSize * 0.06f, cap = StrokeCap.Round)
            )

            // Draw another counter-rotating thin accent ring
            drawArc(
                color = neonCyan.copy(alpha = 0.4f),
                startAngle = -rotationAngle * 1.5f,
                sweepAngle = 60f,
                useCenter = false,
                topLeft = Offset(center.x - progressRingRadius * 0.8f, center.y - progressRingRadius * 0.8f),
                size = androidx.compose.ui.geometry.Size(progressRingRadius * 1.6f, progressRingRadius * 1.6f),
                style = Stroke(width = minSize * 0.02f, cap = StrokeCap.Round)
            )
        }
    }
}

/**
 * A beautiful modern Linear Progress bar with an integrated glowing light-sweep shimmer.
 */
@Composable
fun PremiumLinearShimmerProgress(
    progress: Float,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "ShimmerTransition")
    val shimmerOffset by infiniteTransition.animateFloat(
        initialValue = -1f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = EaseInOutQuad),
            repeatMode = RepeatMode.Restart
        ),
        label = "ShimmerOffset"
    )

    val neonPink = Color(0xFFFF007F)
    val neonCyan = Color(0xFF00F0FF)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(14.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(Color(0xFF1E1335))
                .border(1.dp, Color(0xFF332056), RoundedCornerShape(5.dp))
        ) {
            // Main progress fill with gradient
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(progress.coerceIn(0f, 1f))
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(Color(0xFF7B2CBF), neonPink, neonCyan)
                        )
                    )
                    .drawBehind {
                        // Moving highlight shimmer
                        val width = size.width
                        val highlightStart = width * shimmerOffset
                        val highlightWidth = width * 0.4f
                        drawRect(
                            brush = Brush.horizontalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color.White.copy(alpha = 0.4f),
                                    Color.White.copy(alpha = 0.8f),
                                    Color.White.copy(alpha = 0.4f),
                                    Color.Transparent
                                ),
                                startX = highlightStart,
                                endX = highlightStart + highlightWidth
                            )
                        )
                    }
            )
        }

        // Animated pulse spark at the progress head
        if (progress > 0.05f) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress.coerceIn(0f, 1f))
                    .wrapContentWidth(Alignment.End)
            ) {
                LottiePulseLoader(
                    sizeDp = 16.dp,
                    modifier = Modifier.offset(x = 4.dp)
                )
            }
        }
    }
}

/**
 * A beautiful stage-by-stage pipeline checklist that shows which step is active.
 * Perceived wait feels much shorter when the user is presented with distinct milestones.
 */
@Composable
fun PremiumPipelineStagesTracker(
    currentStatus: String,
    progress: Float,
    appLanguageState: String,
    modifier: Modifier = Modifier
) {
    // Define the sequence of milestones in the generation pipeline
    val stages = listOf(
        PipelineStage("script_writing", "Writing & Optimizing Script Content", 0.0f..0.30f),
        PipelineStage("voice_synth", "Synthesizing Neural Narration Tracks", 0.30f..0.60f),
        PipelineStage("media_curation", "Curating High-Res Cinematics & Aesthetics", 0.60f..0.85f),
        PipelineStage("timing_align", "Aligning Words with Subtitle Framings", 0.85f..0.98f),
        PipelineStage("compiling", "Structuring Studio Master Copy", 0.98f..1.0f)
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFF0C0718).copy(alpha = 0.6f), RoundedCornerShape(16.dp))
            .border(1.dp, Color(0xFF22163E), RoundedCornerShape(16.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = "YASHORA ENGINE STAGES".localize(appLanguageState),
            color = Color(0xFF00F0FF),
            fontSize = 10.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 1.5.sp,
            modifier = Modifier.padding(bottom = 4.dp)
        )

        stages.forEach { stage ->
            val isCompleted = progress > stage.range.endInclusive || (progress >= 0.99f)
            val isActive = progress >= stage.range.start && progress <= stage.range.endInclusive && !isCompleted

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Animated stage indicator icon
                val iconColor = when {
                    isCompleted -> Color(0xFF00FF87)
                    isActive -> Color(0xFFFF007F)
                    else -> Color.Gray.copy(alpha = 0.4f)
                }

                if (isActive) {
                    val infiniteTransition = rememberInfiniteTransition(label = "StageSync")
                    val rotation by infiniteTransition.animateFloat(
                        initialValue = 0f,
                        targetValue = 360f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(1200, easing = LinearEasing)
                        ),
                        label = "Rotation"
                    )
                    Icon(
                        imageVector = Icons.Default.Sync,
                        contentDescription = null,
                        tint = iconColor,
                        modifier = Modifier
                            .size(16.dp)
                            .drawBehind {
                                drawCircle(
                                    color = iconColor.copy(alpha = 0.2f),
                                    radius = size.minDimension * 0.9f
                                )
                            }
                    )
                } else if (isCompleted) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = iconColor,
                        modifier = Modifier.size(16.dp)
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.RadioButtonUnchecked,
                        contentDescription = null,
                        tint = iconColor,
                        modifier = Modifier.size(16.dp)
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Text(
                    text = stage.label.localize(appLanguageState),
                    color = when {
                        isCompleted -> Color.LightGray.copy(alpha = 0.7f)
                        isActive -> Color.White
                        else -> Color.Gray.copy(alpha = 0.6f)
                    },
                    fontSize = 12.sp,
                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                    modifier = Modifier.weight(1f)
                )

                if (isActive) {
                    Text(
                        text = "Active".localize(appLanguageState),
                        color = Color(0xFFFF007F),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .background(Color(0xFFFF007F).copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                } else if (isCompleted) {
                    Text(
                        text = "Done".localize(appLanguageState),
                        color = Color(0xFF00FF87),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .background(Color(0xFF00FF87).copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
}

data class PipelineStage(
    val id: String,
    val label: String,
    val range: ClosedRange<Float>
)

/**
 * A beautiful, modern full-screen loading dialog containing progress checkers and spinners
 */
@Composable
fun PremiumPipelineDialog(
    title: String,
    statusText: String,
    progress: Float,
    appLanguageState: String,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showCancelConfirmDialog by remember { mutableStateOf(false) }

    if (showCancelConfirmDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showCancelConfirmDialog = false },
            title = {
                Text(
                    text = "Cancel Process?".localize(appLanguageState),
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = Color.White
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to cancel the process? All progress will be lost.".localize(appLanguageState),
                    fontSize = 14.sp,
                    color = Color.LightGray
                )
            },
            confirmButton = {
                androidx.compose.material3.TextButton(
                    onClick = {
                        showCancelConfirmDialog = false
                        onCancel()
                    }
                ) {
                    Text(
                        text = "Yes, Cancel".localize(appLanguageState),
                        color = Color(0xFFFF007F),
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(
                    onClick = { showCancelConfirmDialog = false }
                ) {
                    Text(
                        text = "No, Continue".localize(appLanguageState),
                        color = Color(0xFF00F0FF),
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            containerColor = Color(0xFF150D2A),
            shape = RoundedCornerShape(20.dp)
        )
    }

    Column(
        modifier = modifier
            .fillMaxWidth(0.9f)
            .background(Color(0xFF0F0922), RoundedCornerShape(24.dp))
            .border(2.dp, Color(0xFF2B1C4E), RoundedCornerShape(24.dp))
            .padding(26.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val isVoice = title.contains("Voice", ignoreCase = true) || title.contains("Audio", ignoreCase = true) || statusText.contains("Voice", ignoreCase = true)
        val isVideo = title.contains("Video", ignoreCase = true) || title.contains("Render", ignoreCase = true) || title.contains("Backdrop", ignoreCase = true)
        if (isVoice) {
            LottieVoiceSynthesisAnimation(sizeDp = 70.dp)
        } else if (isVideo) {
            LottieVideoSynthesisAnimation(sizeDp = 84.dp)
        } else {
            LottieApiProcessingAnimation(sizeDp = 84.dp)
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = title.localize(appLanguageState),
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = statusText.localize(appLanguageState),
            color = Color.LightGray,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            minLines = 2,
            maxLines = 2,
            modifier = Modifier.padding(horizontal = 8.dp)
        )

        Spacer(modifier = Modifier.height(22.dp))

        PremiumLinearShimmerProgress(progress = progress)

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${(progress * 100).toInt()}% " + "Completed".localize(appLanguageState),
                color = Color(0xFF00F0FF),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
            Icon(
                imageVector = Icons.Default.HourglassTop,
                contentDescription = null,
                tint = Color(0xFFFF007F),
                modifier = Modifier.size(16.dp)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        PremiumPipelineStagesTracker(
            currentStatus = statusText,
            progress = progress,
            appLanguageState = appLanguageState
        )

        Spacer(modifier = Modifier.height(24.dp))

        androidx.compose.material3.TextButton(
            onClick = { showCancelConfirmDialog = true },
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFFFF007F).copy(alpha = 0.08f), RoundedCornerShape(12.dp))
                .border(1.dp, Color(0xFFFF007F).copy(alpha = 0.3f), RoundedCornerShape(12.dp))
        ) {
            Text(
                text = "Cancel Process".localize(appLanguageState),
                color = Color(0xFFFF007F),
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
            )
        }
    }
}

/**
 * Creates a beautiful neon glowing linear gradient animation brush for loading skeletons.
 */
@Composable
fun rememberShimmerBrush(
    shimmerColors: List<Color> = listOf(
        Color(0xFF150D2A),
        Color(0xFF321A5E),
        Color(0xFF150D2A)
    )
): Brush {
    val transition = rememberInfiniteTransition(label = "Shimmer")
    val translateAnim by transition.animateFloat(
        initialValue = -1000f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "TranslateAnim"
    )

    return Brush.linearGradient(
        colors = shimmerColors,
        start = Offset(x = translateAnim, y = translateAnim),
        end = Offset(x = translateAnim + 500f, y = translateAnim + 500f)
    )
}

/**
 * A beautiful, highly professional, simulated storyboard timeline skeleton for script analysis phases.
 */
@Composable
fun ScriptAnalysisSkeleton(
    modifier: Modifier = Modifier,
    statusText: String = "",
    progress: Float = 0f,
    appLanguageState: String = ""
) {
    val shimmerBrush = rememberShimmerBrush()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFF0F0922), RoundedCornerShape(16.dp))
            .border(1.dp, Color(0xFF2B1C4E), RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            PremiumCircularLoader(sizeDp = 36)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = statusText.localize(appLanguageState),
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.height(4.dp))
                // Shimmer text bar
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.6f)
                        .height(10.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(shimmerBrush)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Linear Progress bar
        PremiumLinearShimmerProgress(
            progress = progress,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(6.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${(progress * 100).toInt()}% " + "Parsed".localize(appLanguageState),
                color = Color(0xFF00F0FF),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Analyzing Layers...".localize(appLanguageState),
                color = Color(0xFFFF007F),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Futuristic Skeleton Preview Blocks (Storyboard items)
        Text(
            text = "AI STORYBOARD TIMELINE DECONSTRUCTION".localize(appLanguageState),
            color = Color(0xFF00F0FF).copy(alpha = 0.6f),
            fontSize = 9.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 1.2.sp,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Simulated Scene 1
            Column(
                modifier = Modifier
                    .weight(1f)
                    .background(Color(0xFF170F2C), RoundedCornerShape(10.dp))
                    .padding(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(55.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(shimmerBrush),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Sync,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.15f),
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Box(modifier = Modifier.fillMaxWidth(0.8f).height(6.dp).clip(RoundedCornerShape(3.dp)).background(shimmerBrush))
                Spacer(modifier = Modifier.height(4.dp))
                Box(modifier = Modifier.fillMaxWidth(0.5f).height(6.dp).clip(RoundedCornerShape(3.dp)).background(shimmerBrush))
            }

            // Simulated Scene 2
            Column(
                modifier = Modifier
                    .weight(1f)
                    .background(Color(0xFF170F2C), RoundedCornerShape(10.dp))
                    .padding(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(55.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(shimmerBrush),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Sync,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.15f),
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Box(modifier = Modifier.fillMaxWidth(0.7f).height(6.dp).clip(RoundedCornerShape(3.dp)).background(shimmerBrush))
                Spacer(modifier = Modifier.height(4.dp))
                Box(modifier = Modifier.fillMaxWidth(0.4f).height(6.dp).clip(RoundedCornerShape(3.dp)).background(shimmerBrush))
            }

            // Simulated Scene 3
            Column(
                modifier = Modifier
                    .weight(1f)
                    .background(Color(0xFF170F2C), RoundedCornerShape(10.dp))
                    .padding(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(55.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(shimmerBrush),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Sync,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.15f),
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Box(modifier = Modifier.fillMaxWidth(0.9f).height(6.dp).clip(RoundedCornerShape(3.dp)).background(shimmerBrush))
                Spacer(modifier = Modifier.height(4.dp))
                Box(modifier = Modifier.fillMaxWidth(0.6f).height(6.dp).clip(RoundedCornerShape(3.dp)).background(shimmerBrush))
            }
        }
    }
}

/**
 * Specialized loading view displaying animated shimmer skeletons, step indicators,
 * and a linear progress bar while Gemini API processes AI script generation.
 */
@Composable
fun ScriptGenerationLoadingSkeleton(
    modifier: Modifier = Modifier,
    title: String = "Generating AI Script with Yashora AI Engine...",
    appLanguageState: String = "",
    showSteps: Boolean = true,
    showCancelButton: Boolean = false,
    onCancel: (() -> Unit)? = null
) {
    val shimmerBrush = rememberShimmerBrush()

    // Smooth progress simulation from 0% up to 95% over 12 seconds
    val simulatedProgress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        simulatedProgress.animateTo(
            targetValue = 0.95f,
            animationSpec = tween(durationMillis = 12000, easing = EaseOutQuad)
        )
    }

    val progressValue = simulatedProgress.value

    // Dynamic steps based on progress
    val steps = remember(appLanguageState) {
        listOf(
            "Connecting to Yashora Neural Core v2.5",
            "Analyzing Topic & Style Context",
            "Drafting Hook & Scene Narration",
            "Formatting Scene Timestamps & Visual Prompts"
        )
    }

    val activeStepIndex = when {
        progressValue < 0.25f -> 0
        progressValue < 0.55f -> 1
        progressValue < 0.82f -> 2
        else -> 3
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF160D2E),
                        Color(0xFF0F0821)
                    )
                ),
                shape = RoundedCornerShape(20.dp)
            )
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color(0xFF00F0FF).copy(alpha = 0.4f),
                        Color(0xFFFF007F).copy(alpha = 0.4f)
                    )
                ),
                shape = RoundedCornerShape(20.dp)
            )
            .padding(18.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Header with AI Sparkle & Lottie API Processing animation
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            LottieApiProcessingAnimation(sizeDp = 44.dp)
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = Color(0xFF00F0FF),
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "YASHORA NEURAL CORE AI",
                        color = Color(0xFF00F0FF),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.2.sp
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = title.localize(appLanguageState),
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Linear Shimmer Progress Bar with Lottie Spark Head
        LottieLinearProgressBar(
            progress = progressValue,
            height = 6.dp,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Progress percentage & status string
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${(progressValue * 100).toInt()}% " + "Generated".localize(appLanguageState),
                color = Color(0xFF00F0FF),
                fontSize = 12.sp,
                fontWeight = FontWeight.ExtraBold
            )
            Text(
                text = steps[activeStepIndex].localize(appLanguageState),
                color = Color(0xFFFF007F),
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1
            )
        }

        if (showSteps) {
            Spacer(modifier = Modifier.height(16.dp))
            // Step checklist
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF0B0616).copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                steps.forEachIndexed { idx, stepLabel ->
                    val isDone = idx < activeStepIndex
                    val isCurrent = idx == activeStepIndex

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = when {
                                isDone -> "✓"
                                isCurrent -> "⚡"
                                else -> "•"
                            },
                            color = when {
                                isDone -> Color(0xFF00FF87)
                                isCurrent -> Color(0xFFFF007F)
                                else -> Color.Gray
                            },
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.width(18.dp)
                        )
                        Text(
                            text = stepLabel.localize(appLanguageState),
                            color = when {
                                isDone -> Color.LightGray.copy(alpha = 0.8f)
                                isCurrent -> Color.White
                                else -> Color.Gray
                            },
                            fontSize = 11.sp,
                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Script Deconstruction Skeleton Preview Box
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF130B27), RoundedCornerShape(12.dp))
                .border(1.dp, Color(0xFF231644), RoundedCornerShape(12.dp))
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Title skeleton
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.5f)
                        .height(14.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(shimmerBrush)
                )
                // Tag skeleton
                Box(
                    modifier = Modifier
                        .width(60.dp)
                        .height(14.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(shimmerBrush)
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Scene 1 Skeleton Block
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .width(70.dp)
                        .height(10.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(shimmerBrush)
                )
            }
            Box(modifier = Modifier.fillMaxWidth(0.95f).height(8.dp).clip(RoundedCornerShape(4.dp)).background(shimmerBrush))
            Box(modifier = Modifier.fillMaxWidth(0.85f).height(8.dp).clip(RoundedCornerShape(4.dp)).background(shimmerBrush))

            Spacer(modifier = Modifier.height(2.dp))

            // Scene 2 Skeleton Block
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .width(85.dp)
                        .height(10.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(shimmerBrush)
                )
            }
            Box(modifier = Modifier.fillMaxWidth(0.9f).height(8.dp).clip(RoundedCornerShape(4.dp)).background(shimmerBrush))
            Box(modifier = Modifier.fillMaxWidth(0.7f).height(8.dp).clip(RoundedCornerShape(4.dp)).background(shimmerBrush))
        }

        if (showCancelButton && onCancel != null) {
            Spacer(modifier = Modifier.height(16.dp))
            androidx.compose.material3.TextButton(
                onClick = onCancel,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFFF007F).copy(alpha = 0.08f), RoundedCornerShape(12.dp))
            ) {
                Text(
                    text = "Cancel Process".localize(appLanguageState),
                    color = Color(0xFFFF007F),
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            }
        }
    }
}

