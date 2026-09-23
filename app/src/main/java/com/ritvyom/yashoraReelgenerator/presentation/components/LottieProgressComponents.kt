package com.ritvyom.yashoraReelgenerator.presentation.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.airbnb.lottie.compose.*
import com.ritvyom.yashoraReelgenerator.presentation.utils.localize

/**
 * Lottie-based animated synthesis indicator for video rendering, Media3 export, and pipeline compilation.
 */
@Composable
fun LottieVideoSynthesisAnimation(
    modifier: Modifier = Modifier,
    sizeDp: Dp = 80.dp,
    speed: Float = 1.0f
) {
    val composition by rememberLottieComposition(LottieCompositionSpec.Asset("lottie/video_synthesis.json"))
    val progress by animateLottieCompositionAsState(
        composition = composition,
        iterations = LottieConstants.IterateForever,
        speed = speed
    )

    Box(
        modifier = modifier.size(sizeDp),
        contentAlignment = Alignment.Center
    ) {
        if (composition != null) {
            LottieAnimation(
                composition = composition,
                progress = { progress },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            // Fallback while asset composition is loading
            PremiumCircularLoader(sizeDp = sizeDp.value.toInt())
        }
    }
}

/**
 * Lottie-based animated indicator for API processing tasks (Gemini AI Script, Wikipedia/Pexels search, Cloud sync).
 */
@Composable
fun LottieApiProcessingAnimation(
    modifier: Modifier = Modifier,
    sizeDp: Dp = 80.dp,
    speed: Float = 1.0f
) {
    val composition by rememberLottieComposition(LottieCompositionSpec.Asset("lottie/api_processing.json"))
    val progress by animateLottieCompositionAsState(
        composition = composition,
        iterations = LottieConstants.IterateForever,
        speed = speed
    )

    Box(
        modifier = modifier.size(sizeDp),
        contentAlignment = Alignment.Center
    ) {
        if (composition != null) {
            LottieAnimation(
                composition = composition,
                progress = { progress },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            PremiumCircularLoader(sizeDp = sizeDp.value.toInt())
        }
    }
}

/**
 * Lottie-based animated camera and media curator indicator for Image/Video asset fetching.
 */
@Composable
fun LottieImageFetchingAnimation(
    modifier: Modifier = Modifier,
    sizeDp: Dp = 80.dp,
    speed: Float = 1.0f
) {
    val composition by rememberLottieComposition(LottieCompositionSpec.Asset("lottie/image_fetching.json"))
    val progress by animateLottieCompositionAsState(
        composition = composition,
        iterations = LottieConstants.IterateForever,
        speed = speed
    )

    Box(
        modifier = modifier.size(sizeDp),
        contentAlignment = Alignment.Center
    ) {
        if (composition != null) {
            LottieAnimation(
                composition = composition,
                progress = { progress },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            PremiumCircularLoader(sizeDp = sizeDp.value.toInt())
        }
    }
}

/**
 * Lottie-based animated audio waveform indicator for ElevenLabs, Edge TTS, and voice synthesis tasks.
 */
@Composable
fun LottieVoiceSynthesisAnimation(
    modifier: Modifier = Modifier,
    sizeDp: Dp = 60.dp,
    speed: Float = 1.2f
) {
    val composition by rememberLottieComposition(LottieCompositionSpec.Asset("lottie/voice_synthesis.json"))
    val progress by animateLottieCompositionAsState(
        composition = composition,
        iterations = LottieConstants.IterateForever,
        speed = speed
    )

    Box(
        modifier = modifier.size(width = sizeDp * 1.5f, height = sizeDp),
        contentAlignment = Alignment.Center
    ) {
        if (composition != null) {
            LottieAnimation(
                composition = composition,
                progress = { progress },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            PremiumCircularLoader(sizeDp = (sizeDp.value * 0.7f).toInt())
        }
    }
}

/**
 * Lottie-based pulse indicator for compact actions, buttons, and micro status chips.
 */
@Composable
fun LottiePulseLoader(
    modifier: Modifier = Modifier,
    sizeDp: Dp = 28.dp
) {
    val composition by rememberLottieComposition(LottieCompositionSpec.Asset("lottie/pulse_loader.json"))
    val progress by animateLottieCompositionAsState(
        composition = composition,
        iterations = LottieConstants.IterateForever,
        speed = 1.0f
    )

    Box(
        modifier = modifier.size(sizeDp),
        contentAlignment = Alignment.Center
    ) {
        if (composition != null) {
            LottieAnimation(
                composition = composition,
                progress = { progress },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            CircularProgressIndicator(
                modifier = Modifier.size(sizeDp * 0.8f),
                strokeWidth = 2.dp,
                color = Color(0xFF00F0FF)
            )
        }
    }
}

/**
 * High-craft progress bar with animated neon gradient, traveling glow shimmer, and an embedded Lottie spark head.
 */
@Composable
fun LottieLinearProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    height: Dp = 8.dp,
    showHeadSpark: Boolean = true,
    trackColor: Color = Color(0xFF1E1438),
    brushColors: List<Color> = listOf(
        Color(0xFF00F0FF), // Neon Cyan
        Color(0xFF39FF14), // Electric Lime
        Color(0xFFFF007F)  // Cyber Magenta
    )
) {
    val clampedProgress = progress.coerceIn(0f, 1f)

    // Animated smooth progress transition
    val animatedProgress by animateFloatAsState(
        targetValue = clampedProgress,
        animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing),
        label = "LottieProgressAnimation"
    )

    // Shimmer wave translation
    val transition = rememberInfiniteTransition(label = "ProgressShimmer")
    val shimmerTranslate by transition.animateFloat(
        initialValue = -1f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ShimmerTranslate"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(if (showHeadSpark) height + 10.dp else height),
        contentAlignment = Alignment.CenterStart
    ) {
        // Background track
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(height)
                .clip(RoundedCornerShape(height / 2))
                .background(trackColor)
                .border(0.5.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(height / 2))
        )

        // Filled active progress gradient with traveling shimmer
        Box(
            modifier = Modifier
                .fillMaxWidth(animatedProgress)
                .height(height)
                .clip(RoundedCornerShape(height / 2))
                .background(
                    Brush.horizontalGradient(
                        colors = brushColors
                    )
                )
                .drawBehind {
                    if (size.width > 0) {
                        val shimmerX = size.width * shimmerTranslate
                        drawRect(
                            brush = Brush.horizontalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color.White.copy(alpha = 0.45f),
                                    Color.Transparent
                                ),
                                startX = shimmerX - 40f,
                                endX = shimmerX + 40f
                            )
                        )
                    }
                }
        )

        // Pinned Lottie spark at the active head
        if (showHeadSpark && animatedProgress > 0.04f) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(animatedProgress)
                    .wrapContentWidth(Alignment.End)
            ) {
                LottiePulseLoader(
                    sizeDp = height + 10.dp,
                    modifier = Modifier.offset(x = 4.dp)
                )
            }
        }
    }
}

/**
 * Floating or bottom-anchored card indicating background video synthesis in real-time with Lottie animation.
 */
@Composable
fun LottieBackgroundSynthesisCard(
    progress: Float,
    statusText: String,
    etaText: String?,
    appLanguage: String,
    onMaximize: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF0F0922).copy(alpha = 0.96f)
        ),
        shape = RoundedCornerShape(20.dp),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 20.dp)
            .navigationBarsPadding()
            .border(
                width = 1.5.dp,
                brush = Brush.horizontalGradient(
                    listOf(
                        Color(0xFF39FF14).copy(alpha = 0.7f),
                        Color(0xFF00F0FF).copy(alpha = 0.7f)
                    )
                ),
                shape = RoundedCornerShape(20.dp)
            )
            .clickable { onMaximize() }
    ) {
        Column(
            modifier = Modifier.padding(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    // Lottie rendering animation in the card
                    LottieVideoSynthesisAnimation(
                        sizeDp = 40.dp,
                        speed = 1.2f
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Synthesizing Video in Background".localize(appLanguage),
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }

                        val etaDisplay = etaText?.let { " • " + it.localize(appLanguage) } ?: ""
                        Text(
                            text = "${(progress * 100).toInt()}%$etaDisplay • " + statusText.localize(appLanguage),
                            color = Color(0xFF00F0FF),
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Maximize action
                    IconButton(
                        onClick = onMaximize,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Fullscreen,
                            contentDescription = "Maximize",
                            tint = Color(0xFF00F0FF)
                        )
                    }

                    // Cancel action
                    IconButton(
                        onClick = onCancel,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Cancel",
                            tint = Color(0xFFFF007F)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Dynamic Lottie progress bar
            LottieLinearProgressBar(
                progress = progress,
                height = 6.dp,
                brushColors = listOf(Color(0xFF39FF14), Color(0xFF00F0FF))
            )
        }
    }
}

/**
 * Premium full-screen modal or dialog for API processing tasks (Gemini Script, Wikipedia Backdrops, AI Voice).
 */
@Composable
fun LottieApiProcessingDialog(
    title: String,
    statusText: String,
    appLanguage: String,
    isVoiceTask: Boolean = false,
    progress: Float? = null,
    onCancel: (() -> Unit)? = null
) {
    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .background(Color(0xFF100A1F), RoundedCornerShape(24.dp))
                .border(
                    width = 1.5.dp,
                    brush = Brush.linearGradient(
                        listOf(Color(0xFF00F0FF).copy(alpha = 0.5f), Color(0xFFFF007F).copy(alpha = 0.5f))
                    ),
                    shape = RoundedCornerShape(24.dp)
                )
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Appropriate Lottie animation
                if (isVoiceTask) {
                    LottieVoiceSynthesisAnimation(sizeDp = 64.dp)
                } else {
                    LottieApiProcessingAnimation(sizeDp = 76.dp)
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = title.localize(appLanguage),
                    color = Color.White,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = statusText.localize(appLanguage),
                    color = Color(0xFF00F0FF),
                    fontSize = 12.5.sp,
                    textAlign = TextAlign.Center,
                    minLines = 2,
                    maxLines = 3,
                    lineHeight = 17.sp
                )

                if (progress != null) {
                    Spacer(modifier = Modifier.height(16.dp))
                    LottieLinearProgressBar(
                        progress = progress,
                        height = 6.dp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "${(progress * 100).toInt()}%",
                        color = Color.LightGray,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                if (onCancel != null) {
                    Spacer(modifier = Modifier.height(20.dp))
                    OutlinedButton(
                        onClick = onCancel,
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFF007F).copy(alpha = 0.4f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFF007F)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Cancel".localize(appLanguage),
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }
}
