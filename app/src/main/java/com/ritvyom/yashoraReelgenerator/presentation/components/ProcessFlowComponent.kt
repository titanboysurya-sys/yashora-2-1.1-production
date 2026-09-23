package com.ritvyom.yashoraReelgenerator.presentation.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ritvyom.yashoraReelgenerator.presentation.utils.localize

/**
 * The 4 core stages of the Video Generation Pipeline.
 */
enum class PipelineStageType(
    val titleRes: String,
    val subtitleRes: String,
    val startProgress: Float,
    val endProgress: Float,
    val icon: ImageVector,
    val accentColor: Color
) {
    SCRIPTING(
        titleRes = "Scripting",
        subtitleRes = "AI Script Architecture & Hook Formulation",
        startProgress = 0.0f,
        endProgress = 0.25f,
        icon = Icons.Default.EditNote,
        accentColor = Color(0xFF00F0FF)
    ),
    IMAGE_FETCHING(
        titleRes = "Image Fetching",
        subtitleRes = "Curating High-Res Cinematics & Visual Assets",
        startProgress = 0.25f,
        endProgress = 0.55f,
        icon = Icons.Default.CameraAlt,
        accentColor = Color(0xFFFF007F)
    ),
    VOICE_SYNTHESIS(
        titleRes = "Voice Synthesis",
        subtitleRes = "Neural Voiceover & Emotion Modulation",
        startProgress = 0.55f,
        endProgress = 0.80f,
        icon = Icons.Default.GraphicEq,
        accentColor = Color(0xFFFFB300)
    ),
    VIDEO_RENDERING(
        titleRes = "Video Rendering",
        subtitleRes = "Master Video Composition & 1080p Export",
        startProgress = 0.80f,
        endProgress = 1.0f,
        icon = Icons.Default.MovieFilter,
        accentColor = Color(0xFF00FF87)
    )
}

enum class StageState {
    COMPLETED,
    IN_PROGRESS,
    PENDING
}

/**
 * ProcessFlowComponent visualizes the video generation pipeline (Scripting, Image Fetching,
 * Voice Synthesis, Video Rendering) using animated Lottie icons and a step-by-step progress
 * indicator to provide live, professional feedback to the user.
 */
@Composable
fun ProcessFlowComponent(
    currentProgress: Float,
    currentStatusText: String,
    modifier: Modifier = Modifier,
    languageState: String = "en",
    activeStageOverride: PipelineStageType? = null
) {
    val stages = PipelineStageType.values()

    // Determine the active stage based on progress or override
    val activeStage = activeStageOverride ?: remember(currentProgress) {
        when {
            currentProgress < 0.25f -> PipelineStageType.SCRIPTING
            currentProgress < 0.55f -> PipelineStageType.IMAGE_FETCHING
            currentProgress < 0.80f -> PipelineStageType.VOICE_SYNTHESIS
            else -> PipelineStageType.VIDEO_RENDERING
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFF0C061A), RoundedCornerShape(20.dp))
            .border(
                BorderStroke(
                    1.dp,
                    Brush.horizontalGradient(
                        listOf(
                            Color(0xFF00F0FF).copy(alpha = 0.5f),
                            Color(0xFFFF007F).copy(alpha = 0.3f),
                            Color(0xFF00FF87).copy(alpha = 0.4f)
                        )
                    )
                ),
                RoundedCornerShape(20.dp)
            )
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // ========================================================
        // 1. HEADER: LIVE PIPELINE STATUS
        // ========================================================
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF00FF87))
                )
                Text(
                    text = "GENERATION PIPELINE FLOW".localize(languageState),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.2.sp,
                    color = Color(0xFF00F0FF)
                )
            }

            Surface(
                shape = RoundedCornerShape(8.dp),
                color = Color(0xFF1B1133),
                border = BorderStroke(0.6.dp, Color(0xFF3B2768))
            ) {
                Text(
                    text = "${(currentProgress * 100).toInt()}% " + "Complete".localize(languageState),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF00FF87),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
        }

        // ========================================================
        // 2. STEP-BY-STEP PROGRESS STEPPER (HORIZONTAL NODES)
        // ========================================================
        StepByStepProgressIndicator(
            stages = stages,
            activeStage = activeStage,
            currentProgress = currentProgress,
            languageState = languageState,
            modifier = Modifier.fillMaxWidth()
        )

        // ========================================================
        // 3. ACTIVE HERO STAGE WITH ANIMATED LOTTIE ICON
        // ========================================================
        ActiveStageHeroCard(
            stage = activeStage,
            statusText = currentStatusText,
            currentProgress = currentProgress,
            languageState = languageState,
            modifier = Modifier.fillMaxWidth()
        )

        // ========================================================
        // 4. DETAILED BREAKDOWN LIST OF ALL 4 STAGES
        // ========================================================
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            stages.forEachIndexed { index, stage ->
                val stageState = when {
                    currentProgress >= stage.endProgress || (stage == PipelineStageType.VIDEO_RENDERING && currentProgress >= 0.99f) -> StageState.COMPLETED
                    stage == activeStage -> StageState.IN_PROGRESS
                    else -> StageState.PENDING
                }

                PipelineStageRowItem(
                    index = index + 1,
                    stage = stage,
                    stageState = stageState,
                    currentProgress = currentProgress,
                    languageState = languageState
                )
            }
        }
    }
}

/**
 * Step-by-Step progress indicator connecting the 4 milestones with glowing lines.
 */
@Composable
private fun StepByStepProgressIndicator(
    stages: Array<PipelineStageType>,
    activeStage: PipelineStageType,
    currentProgress: Float,
    languageState: String,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "NodePulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "PulseScale"
    )

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        stages.forEachIndexed { index, stage ->
            val isCompleted = currentProgress >= stage.endProgress || (stage == PipelineStageType.VIDEO_RENDERING && currentProgress >= 0.99f)
            val isActive = stage == activeStage

            // Stage Node Icon
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(36.dp)
                ) {
                    if (isActive) {
                        Box(
                            modifier = Modifier
                                .size(36.dp * pulseScale)
                                .clip(CircleShape)
                                .background(stage.accentColor.copy(alpha = 0.2f))
                        )
                    }

                    val nodeBackground = when {
                        isCompleted -> Color(0xFF00FF87)
                        isActive -> stage.accentColor
                        else -> Color(0xFF1E1335)
                    }

                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(nodeBackground)
                            .border(
                                width = if (isActive) 2.dp else 1.dp,
                                color = if (isActive) Color.White else Color(0xFF332055),
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        androidx.compose.animation.AnimatedVisibility(
                            visible = isCompleted,
                            enter = fadeIn(tween(250)) + scaleIn(initialScale = 0.5f, animationSpec = tween(250)),
                            exit = fadeOut(tween(150))
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = Color.Black,
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        androidx.compose.animation.AnimatedVisibility(
                            visible = !isCompleted,
                            enter = fadeIn(tween(250)),
                            exit = fadeOut(tween(150))
                        ) {
                            Text(
                                text = "${index + 1}",
                                color = if (isActive) Color.Black else Color.Gray,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Text(
                    text = stage.titleRes.localize(languageState),
                    fontSize = 10.sp,
                    fontWeight = if (isActive) FontWeight.ExtraBold else FontWeight.Medium,
                    color = when {
                        isCompleted -> Color(0xFF00FF87)
                        isActive -> stage.accentColor
                        else -> Color.Gray
                    },
                    maxLines = 1
                )
            }

            // Connecting Line to next stage
            if (index < stages.size - 1) {
                val nextStage = stages[index + 1]
                val targetLineProgress = ((currentProgress - stage.startProgress) / (stage.endProgress - stage.startProgress)).coerceIn(0f, 1f)
                val lineProgress by animateFloatAsState(
                    targetValue = targetLineProgress,
                    animationSpec = tween(350, easing = FastOutSlowInEasing),
                    label = "LineProgress_${stage.name}"
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(3.dp)
                        .padding(horizontal = 4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color(0xFF1B1133))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(lineProgress)
                            .fillMaxHeight()
                            .background(
                                Brush.horizontalGradient(
                                    listOf(stage.accentColor, nextStage.accentColor)
                                )
                            )
                    )
                }
            }
        }
    }
}

/**
 * Hero Card for the currently active stage with live animated Lottie icon and subtle transitions.
 */
@Composable
private fun ActiveStageHeroCard(
    stage: PipelineStageType,
    statusText: String,
    currentProgress: Float,
    languageState: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.animateContentSize(animationSpec = tween(350, easing = FastOutSlowInEasing)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF140A28)),
        border = BorderStroke(1.2.dp, stage.accentColor.copy(alpha = 0.7f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Animated Lottie Icon for the active stage with AnimatedVisibility
            Box(
                modifier = Modifier
                    .size(68.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(stage.accentColor.copy(alpha = 0.12f))
                    .border(1.dp, stage.accentColor.copy(alpha = 0.4f), RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center
            ) {
                PipelineStageType.values().forEach { stg ->
                    androidx.compose.animation.AnimatedVisibility(
                        visible = stg == stage,
                        enter = fadeIn(tween(350)) + scaleIn(initialScale = 0.8f, animationSpec = tween(350)),
                        exit = fadeOut(tween(150)) + scaleOut(targetScale = 0.8f, animationSpec = tween(150))
                    ) {
                        when (stg) {
                            PipelineStageType.SCRIPTING -> {
                                LottieApiProcessingAnimation(sizeDp = 58.dp)
                            }
                            PipelineStageType.IMAGE_FETCHING -> {
                                LottieImageFetchingAnimation(sizeDp = 58.dp)
                            }
                            PipelineStageType.VOICE_SYNTHESIS -> {
                                LottieVoiceSynthesisAnimation(sizeDp = 50.dp)
                            }
                            PipelineStageType.VIDEO_RENDERING -> {
                                LottieVideoSynthesisAnimation(sizeDp = 58.dp)
                            }
                        }
                    }
                }
            }

            // Stage details
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(stage.accentColor)
                    )
                    Text(
                        text = "LIVE ACTIVE STAGE".localize(languageState),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.sp,
                        color = stage.accentColor
                    )
                }

                // Subtle transition for title
                PipelineStageType.values().forEach { stg ->
                    androidx.compose.animation.AnimatedVisibility(
                        visible = stg == stage,
                        enter = fadeIn(tween(300)) + slideInVertically(initialOffsetY = { 10 }, animationSpec = tween(300)),
                        exit = fadeOut(tween(150))
                    ) {
                        Text(
                            text = stg.titleRes.localize(languageState),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }

                Text(
                    text = statusText.ifEmpty { stage.subtitleRes }.localize(languageState),
                    fontSize = 11.sp,
                    color = Color(0xFFCBD5E1),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 15.sp
                )
            }
        }
    }
}

/**
 * Detailed Row Item for each stage in the breakdown list with AnimatedVisibility transitions.
 */
@Composable
private fun PipelineStageRowItem(
    index: Int,
    stage: PipelineStageType,
    stageState: StageState,
    currentProgress: Float,
    languageState: String,
    modifier: Modifier = Modifier
) {
    val backgroundColor = when (stageState) {
        StageState.IN_PROGRESS -> Color(0xFF1B0E33)
        StageState.COMPLETED -> Color(0xFF0F081E)
        StageState.PENDING -> Color(0xFF090412)
    }

    val borderColor = when (stageState) {
        StageState.IN_PROGRESS -> stage.accentColor.copy(alpha = 0.6f)
        StageState.COMPLETED -> Color(0xFF00FF87).copy(alpha = 0.3f)
        StageState.PENDING -> Color(0xFF1E1335)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(backgroundColor)
            .border(1.dp, borderColor, RoundedCornerShape(12.dp))
            .animateContentSize(animationSpec = tween(350, easing = FastOutSlowInEasing))
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Stage Icon / Mini Lottie with AnimatedVisibility
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(
                        when (stageState) {
                            StageState.COMPLETED -> Color(0xFF00FF87).copy(alpha = 0.15f)
                            StageState.IN_PROGRESS -> stage.accentColor.copy(alpha = 0.2f)
                            StageState.PENDING -> Color(0xFF160E26)
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                androidx.compose.animation.AnimatedVisibility(
                    visible = stageState == StageState.COMPLETED,
                    enter = fadeIn(tween(300)) + scaleIn(initialScale = 0.6f, animationSpec = tween(300)),
                    exit = fadeOut(tween(150))
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = Color(0xFF00FF87),
                        modifier = Modifier.size(18.dp)
                    )
                }

                androidx.compose.animation.AnimatedVisibility(
                    visible = stageState == StageState.IN_PROGRESS,
                    enter = fadeIn(tween(300)) + scaleIn(initialScale = 0.6f, animationSpec = tween(300)),
                    exit = fadeOut(tween(150))
                ) {
                    LottiePulseLoader(sizeDp = 22.dp)
                }

                androidx.compose.animation.AnimatedVisibility(
                    visible = stageState == StageState.PENDING,
                    enter = fadeIn(tween(200)),
                    exit = fadeOut(tween(150))
                ) {
                    Icon(
                        imageVector = stage.icon,
                        contentDescription = null,
                        tint = Color.Gray.copy(alpha = 0.6f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            // Title and description
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = "${index}. ${stage.titleRes}".localize(languageState),
                    fontSize = 12.sp,
                    fontWeight = if (stageState == StageState.IN_PROGRESS) FontWeight.Bold else FontWeight.SemiBold,
                    color = when (stageState) {
                        StageState.COMPLETED -> Color.White
                        StageState.IN_PROGRESS -> stage.accentColor
                        StageState.PENDING -> Color.Gray
                    }
                )

                Text(
                    text = stage.subtitleRes.localize(languageState),
                    fontSize = 10.sp,
                    color = if (stageState == StageState.PENDING) Color.DarkGray else Color.LightGray,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // State Badge with AnimatedVisibility
            Box(contentAlignment = Alignment.Center) {
                androidx.compose.animation.AnimatedVisibility(
                    visible = stageState == StageState.COMPLETED,
                    enter = fadeIn(tween(300)) + scaleIn(initialScale = 0.75f, animationSpec = tween(300)),
                    exit = fadeOut(tween(150))
                ) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color(0xFF00FF87).copy(alpha = 0.15f),
                        border = BorderStroke(0.6.dp, Color(0xFF00FF87).copy(alpha = 0.4f))
                    ) {
                        Text(
                            text = "DONE".localize(languageState),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFF00FF87),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                androidx.compose.animation.AnimatedVisibility(
                    visible = stageState == StageState.IN_PROGRESS,
                    enter = fadeIn(tween(300)) + scaleIn(initialScale = 0.75f, animationSpec = tween(300)),
                    exit = fadeOut(tween(150))
                ) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = stage.accentColor.copy(alpha = 0.15f),
                        border = BorderStroke(0.6.dp, stage.accentColor.copy(alpha = 0.5f))
                    ) {
                        Text(
                            text = "ACTIVE".localize(languageState),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = stage.accentColor,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                androidx.compose.animation.AnimatedVisibility(
                    visible = stageState == StageState.PENDING,
                    enter = fadeIn(tween(200)),
                    exit = fadeOut(tween(150))
                ) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color(0xFF191029),
                        border = BorderStroke(0.6.dp, Color(0xFF261842))
                    ) {
                        Text(
                            text = "QUEUED".localize(languageState),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.Gray,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        }

        // ========================================================
        // SUBTLE EXPANDED REAL-TIME PROGRESS BAR FOR ACTIVE STAGE
        // ========================================================
        androidx.compose.animation.AnimatedVisibility(
            visible = stageState == StageState.IN_PROGRESS,
            enter = fadeIn(tween(350)) + expandVertically(animationSpec = tween(350, easing = FastOutSlowInEasing)),
            exit = fadeOut(tween(200)) + shrinkVertically(animationSpec = tween(200, easing = FastOutSlowInEasing))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, start = 42.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Processing in real-time...".localize(languageState),
                        fontSize = 9.sp,
                        color = stage.accentColor.copy(alpha = 0.9f),
                        fontWeight = FontWeight.Medium
                    )

                    val stageProgressPct = (((currentProgress - stage.startProgress) / (stage.endProgress - stage.startProgress)).coerceIn(0f, 1f) * 100).toInt()
                    Text(
                        text = "$stageProgressPct%",
                        fontSize = 9.sp,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                val stageLocalTarget = ((currentProgress - stage.startProgress) / (stage.endProgress - stage.startProgress)).coerceIn(0.04f, 1f)
                val animatedLocalProgress by animateFloatAsState(
                    targetValue = stageLocalTarget,
                    animationSpec = tween(300, easing = LinearOutSlowInEasing),
                    label = "StageLocalProgress_${stage.name}"
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color(0xFF261545))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(animatedLocalProgress)
                            .fillMaxHeight()
                            .background(
                                Brush.horizontalGradient(
                                    listOf(stage.accentColor, Color.White)
                                )
                            )
                    )
                }
            }
        }
    }
}
