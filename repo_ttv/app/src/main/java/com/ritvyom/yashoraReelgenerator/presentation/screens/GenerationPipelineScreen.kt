package com.ritvyom.yashoraReelgenerator.presentation.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ritvyom.yashoraReelgenerator.presentation.components.MockRewardedAdDialog
import com.ritvyom.yashoraReelgenerator.presentation.components.*
import com.ritvyom.yashoraReelgenerator.presentation.utils.localize
import com.ritvyom.yashoraReelgenerator.presentation.viewmodels.MainViewModel

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

    var showRewardedAd by remember { mutableStateOf(false) }
    var showCancelConfirmDialog by remember { mutableStateOf(false) }

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
                    text = "Are you sure you want to cancel the process? All progress will be lost.".localize(appLanguageState),
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

    // Start generation pipeline on enter
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
            .background(Color(0xFF07020F)),
        contentAlignment = Alignment.Center
    ) {
        // Ambient background glowing effects
        val infiniteTransition = rememberInfiniteTransition(label = "BackgroundGlow")
        val glowScale by infiniteTransition.animateFloat(
            initialValue = 0.5f,
            targetValue = 1.0f,
            animationSpec = infiniteRepeatable(
                animation = tween(4000, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "GlowAnimScale"
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawBehind {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(Color(0xFFFF007F).copy(alpha = 0.08f), Color.Transparent),
                            radius = size.minDimension * glowScale
                        ),
                        center = center
                    )
                }
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .background(Color(0xFF110B1E), RoundedCornerShape(24.dp))
                .border(2.dp, Color(0xFF22163E), RoundedCornerShape(24.dp))
                .padding(24.dp)
        ) {
            PremiumCircularLoader(sizeDp = 72)

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "Yashora Pipeline Engine".localize(appLanguageState),
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = "COMPILING AI ASSET PACKS".localize(appLanguageState),
                color = Color(0xFF00F0FF),
                fontSize = 10.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 2.sp,
                modifier = Modifier.padding(top = 4.dp)
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Subtitle state info
            Text(
                text = statusState.localize(appLanguageState),
                color = Color.LightGray,
                fontSize = 13.sp,
                minLines = 2,
                maxLines = 2,
                textAlign = TextAlign.Center,
                lineHeight = 18.sp
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Process indicator slider
            PremiumLinearShimmerProgress(
                progress = progressState,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${(progressState * 100).toInt()}% " + "Rendered".localize(appLanguageState),
                    color = Color(0xFF00F0FF),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "AdMob Rewards claims: Active".localize(appLanguageState),
                    color = Color(0xFF39FF14),
                    fontSize = 11.sp,
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Beautiful real-time pipeline tracker list
            PremiumPipelineStagesTracker(
                currentStatus = statusState,
                progress = progressState,
                appLanguageState = appLanguageState
            )

            Spacer(modifier = Modifier.height(20.dp))

            androidx.compose.material3.OutlinedButton(
                onClick = { showCancelConfirmDialog = true },
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFF007F).copy(alpha = 0.5f)),
                colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                    contentColor = Color(0xFFFF007F)
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Cancel Process".localize(appLanguageState),
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
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
