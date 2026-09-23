package com.ritvyom.yashoraReelgenerator.presentation.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material.icons.filled.SlowMotionVideo
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ritvyom.yashoraReelgenerator.presentation.components.MockRewardedAdDialog
import com.ritvyom.yashoraReelgenerator.presentation.components.ProLivePipelineStatusCards
import com.ritvyom.yashoraReelgenerator.presentation.components.ProLiveStudioCanvas
import com.ritvyom.yashoraReelgenerator.presentation.components.ProcessFlowComponent
import com.ritvyom.yashoraReelgenerator.presentation.utils.localize
import com.ritvyom.yashoraReelgenerator.presentation.viewmodels.MainViewModel
import kotlinx.coroutines.delay

/**
 * Pro Live Studio Generation Screen.
 *
 * Replaces simple checklists with a real-time visual studio experience:
 * 1. Live video & image canvas with animated scene transitions (slide left/right) and HUD scanning overlay
 * 2. Visual aspect ratio & viewfinder brackets
 * 3. Mini scene timeline scrubber allowing users to inspect individual generated frames live
 * 4. Transparent 3-tier work status:
 *    - ⚡ In-Progress Live Engine with real-time prompt & active key tier
 *    - ✅ Completed Work milestones
 *    - ⏳ Remaining Pipeline Queue
 * 5. Live elapsed timer and estimated remaining time ticker
 */
@Composable
fun GenerationPipelineScreen(
    viewModel: MainViewModel,
    onComplete: () -> Unit,
    onCancel: () -> Unit = {}
) {
    val progressState by viewModel.generationProgress.collectAsState()
    val statusState by viewModel.generationStatus.collectAsState()
    val isGenerating by viewModel.isGenerating.collectAsState()
    val appLanguageState by viewModel.appLanguage.collectAsState()

    val activeScenes by viewModel.activeScenes.collectAsState()
    val selectedSceneIndex by viewModel.selectedSceneIndex.collectAsState()
    val scriptText by viewModel.scriptText.collectAsState()
    val selectedVoiceName by viewModel.selectedVoiceName.collectAsState()
    val selectedAspectRatio by viewModel.selectedAspectRatio.collectAsState()
    val selectedResolution by viewModel.selectedResolution.collectAsState()
    val selectedStyleName by viewModel.selectedStyleName.collectAsState()
    val geminiApiKey by viewModel.geminiApiKey.collectAsState()

    val hasUserKey = geminiApiKey.isNotBlank() && geminiApiKey.trim().length >= 16

    var showRewardedAd by remember { mutableStateOf(false) }
    var showCancelConfirmDialog by remember { mutableStateOf(false) }

    // User manual scene selection override to inspect any scene in the scrubber
    var userInspectingSceneIndex by remember { mutableStateOf<Int?>(null) }
    val currentSceneIndex = userInspectingSceneIndex ?: selectedSceneIndex

    // Elapsed timer & estimated time remaining
    var elapsedSeconds by remember { mutableStateOf(0) }
    LaunchedEffect(isGenerating) {
        if (isGenerating) {
            while (true) {
                delay(1000)
                elapsedSeconds++
            }
        }
    }

    val estimatedRemainingSec = remember(elapsedSeconds, progressState) {
        if (progressState > 0.08f) {
            val totalEst = (elapsedSeconds / progressState).toLong()
            maxOf(2L, totalEst - elapsedSeconds)
        } else {
            16L
        }
    }

    BackHandler {
        showCancelConfirmDialog = true
    }

    if (showCancelConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showCancelConfirmDialog = false },
            title = {
                Text(
                    text = "Cancel Generation?".localize(appLanguageState),
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = Color.White
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to cancel the generation? All rendered pipeline assets will be discarded.".localize(appLanguageState),
                    fontSize = 14.sp,
                    color = Color.LightGray
                )
            },
            confirmButton = {
                TextButton(
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
                TextButton(
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

    // Trigger generation pipeline on screen entry
    LaunchedEffect(Unit) {
        viewModel.generateVideoPipeline(
            onAdShown = {
                showRewardedAd = true
            },
            onBuildComplete = {
                onComplete()
            }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF06020E))
    ) {
        // Ambient background glow
        val infiniteTransition = rememberInfiniteTransition(label = "BackgroundGlow")
        val glowScale by infiniteTransition.animateFloat(
            initialValue = 0.55f,
            targetValue = 0.95f,
            animationSpec = infiniteRepeatable(
                animation = tween(4000, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "GlowScale"
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawBehind {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(Color(0xFF8A2BE2).copy(alpha = 0.12f), Color.Transparent),
                            radius = size.minDimension * glowScale
                        ),
                        center = center
                    )
                }
        )

        // Main Scrollable Studio Layout
        val scrollState = rememberScrollState()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ========================================================
            // TOP HEADER BAR (LIVE ENGINE STATUS & TIME METRICS)
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
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF00F0FF).copy(alpha = 0.15f))
                            .border(1.dp, Color(0xFF00F0FF).copy(alpha = 0.5f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.SlowMotionVideo,
                            contentDescription = null,
                            tint = Color(0xFF00F0FF),
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "Live AI Reel Studio".localize(appLanguageState),
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Color(0xFF00FF87).copy(alpha = 0.15f),
                                border = BorderStroke(0.6.dp, Color(0xFF00FF87).copy(alpha = 0.4f))
                            ) {
                                Text(
                                    text = "PRO",
                                    color = Color(0xFF00FF87),
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Black,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                )
                            }
                        }

                        // Time Elapsed & Remaining
                        val elapsedFormatted = String.format(java.util.Locale.US, "%02d:%02d", elapsedSeconds / 60, elapsedSeconds % 60)
                        Text(
                            text = "${"Elapsed".localize(appLanguageState)}: $elapsedFormatted • ~${estimatedRemainingSec}s ${"Remaining".localize(appLanguageState)}",
                            color = Color.LightGray,
                            fontSize = 11.sp
                        )
                    }
                }

                IconButton(
                    onClick = { showCancelConfirmDialog = true },
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF1E1335))
                        .border(1.dp, Color(0xFFFF007F).copy(alpha = 0.4f), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Cancel".localize(appLanguageState),
                        tint = Color(0xFFFF007F),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // ========================================================
            // 1. LIVE VISUAL CANVAS & MINI SCENE SCRUBBER
            // ========================================================
            ProLiveStudioCanvas(
                scenes = activeScenes,
                activeSceneIndex = currentSceneIndex,
                onSelectScene = { tappedIndex ->
                    userInspectingSceneIndex = tappedIndex
                },
                aspectRatio = selectedAspectRatio,
                resolution = selectedResolution,
                styleName = selectedStyleName,
                languageState = appLanguageState,
                progress = progressState
            )

            // ========================================================
            // PROGRESS BAR WITH PERCENTAGE & GLOW
            // ========================================================
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${(progressState * 100).toInt()}% " + "Rendered".localize(appLanguageState),
                        color = Color(0xFF00F0FF),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.ExtraBold
                    )

                    val scenesCount = activeScenes.size.coerceAtLeast(1)
                    val activeDisplayNum = (currentSceneIndex + 1).coerceAtMost(scenesCount)
                    Text(
                        text = "Scene $activeDisplayNum of $scenesCount",
                        color = Color(0xFF00FF87),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Custom Neon Progress Bar
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(Color(0xFF1C1333))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progressState.coerceIn(0.02f, 1.0f))
                            .fillMaxHeight()
                            .background(
                                Brush.horizontalGradient(
                                    listOf(
                                        Color(0xFF00F0FF),
                                        Color(0xFFFF007F),
                                        Color(0xFF00FF87)
                                    )
                                )
                            )
                    )
                }

                // Descriptive Status Message Box
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF130B24))
                        .border(1.dp, Color(0xFF00F0FF).copy(alpha = 0.25f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = Color(0xFF00F0FF)
                    )
                    Text(
                        text = statusState.ifEmpty { "Analyzing script..." },
                        color = Color.White.copy(alpha = 0.95f),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // ========================================================
            // 2. PROCESS FLOW PIPELINE (LOTTIE ANIMATED 4-STAGE FLOW)
            // ========================================================
            ProcessFlowComponent(
                currentProgress = progressState,
                currentStatusText = statusState,
                languageState = appLanguageState,
                modifier = Modifier.fillMaxWidth()
            )

            // ========================================================
            // 3. DETAILED 3-TIER PROGRESS STATUS
            // (⚡ IN-PROGRESS, ✅ COMPLETED, ⏳ REMAINING QUEUE)
            // ========================================================
            ProLivePipelineStatusCards(
                currentStatus = statusState,
                progress = progressState,
                scenes = activeScenes,
                activeSceneIndex = currentSceneIndex,
                scriptText = scriptText,
                voiceName = selectedVoiceName,
                hasUserKey = hasUserKey,
                languageState = appLanguageState
            )

            // ========================================================
            // CANCEL BUTTON
            // ========================================================
            OutlinedButton(
                onClick = { showCancelConfirmDialog = true },
                border = BorderStroke(1.dp, Color(0xFFFF007F).copy(alpha = 0.5f)),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = Color(0xFFFF007F)
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp, bottom = 12.dp)
            ) {
                Text(
                    text = "Cancel Generation".localize(appLanguageState),
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            }
        }
    }

    // Modal Rewarded ad overlay
    MockRewardedAdDialog(
        show = showRewardedAd,
        onRewardEarned = {
            // Callback handles claim confirmation
        },
        onDismiss = {
            showRewardedAd = false
        }
    )
}
