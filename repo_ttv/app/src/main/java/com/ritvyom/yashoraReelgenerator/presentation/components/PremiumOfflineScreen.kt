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
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ritvyom.yashoraReelgenerator.presentation.utils.NetworkUtils
import com.ritvyom.yashoraReelgenerator.presentation.utils.localize
import kotlinx.coroutines.delay

/**
 * A highly polished, premium offline screen inspired by creative studio empty states.
 * Replaces raw 550 or network error pages with a beautiful interactive illustration,
 * real-time check, and reconnection alerts.
 */
@Composable
fun PremiumOfflineScreen(
    appLanguage: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var isChecking by remember { mutableStateOf(false) }

    val infiniteTransition = rememberInfiniteTransition(label = "OfflineGlow")
    val pulseGlow by infiniteTransition.animateFloat(
        initialValue = 0.7f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2500, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "PulseGlow"
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF070412))
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // 1. Top tag
        Text(
            text = "Offline Mode | Creativity Awaits".localize(appLanguage).uppercase(),
            color = Color(0xFF00F0FF),
            fontSize = 11.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 1.5.sp,
            modifier = Modifier
                .background(Color(0xFF00F0FF).copy(alpha = 0.08f), CircleShape)
                .border(1.dp, Color(0xFF00F0FF).copy(alpha = 0.2f), CircleShape)
                .padding(horizontal = 14.dp, vertical = 6.dp)
        )

        Spacer(modifier = Modifier.height(32.dp))

        // 2. Beautiful Procedural Canvas Illustration matching the female creator / disconnected vibes
        Box(
            modifier = Modifier
                .size(240.dp)
                .drawBehind {
                    // Cosmic radial background glowing
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color(0xFFFF007F).copy(alpha = 0.15f),
                                Color(0xFF7B2CBF).copy(alpha = 0.05f),
                                Color.Transparent
                            ),
                            center = center,
                            radius = size.width * 0.5f * pulseGlow
                        ),
                        center = center,
                        radius = size.width * 0.5f * pulseGlow
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val centerOffset = Offset(size.width / 2f, size.height / 2f)
                val width = size.width
                val height = size.height

                // Draw decorative ambient grid lines
                drawLine(
                    color = Color(0xFF1F113D).copy(alpha = 0.4f),
                    start = Offset(0f, height * 0.5f),
                    end = Offset(width, height * 0.5f),
                    strokeWidth = 2f
                )
                drawLine(
                    color = Color(0xFF1F113D).copy(alpha = 0.4f),
                    start = Offset(width * 0.5f, 0f),
                    end = Offset(width * 0.5f, height),
                    strokeWidth = 2f
                )

                // Beautiful outer orbit rings
                drawCircle(
                    color = Color(0xFF00F0FF).copy(alpha = 0.15f),
                    center = centerOffset,
                    radius = width * 0.38f,
                    style = Stroke(width = 1.5f, pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(8f, 12f), 0f))
                )

                drawCircle(
                    color = Color(0xFFFF007F).copy(alpha = 0.12f),
                    center = centerOffset,
                    radius = width * 0.28f,
                    style = Stroke(width = 2f)
                )

                // Draw decorative solid circles representing floaty ideas / video reels
                drawCircle(
                    brush = Brush.linearGradient(listOf(Color(0xFF00F0FF), Color(0xFF7B2CBF))),
                    center = Offset(width * 0.2f, height * 0.3f),
                    radius = 8f
                )
                drawCircle(
                    brush = Brush.linearGradient(listOf(Color(0xFFFF007F), Color(0xFF7B2CBF))),
                    center = Offset(width * 0.82f, height * 0.4f),
                    radius = 12f
                )
                drawCircle(
                    color = Color(0xFF39FF14).copy(alpha = 0.3f),
                    center = Offset(width * 0.72f, height * 0.22f),
                    radius = 6f
                )
            }

            // Overlay Icons representing offline status & creativity
            Box(contentAlignment = Alignment.Center) {
                // Central Neon Cloud Off Backdrop
                Icon(
                    imageVector = Icons.Default.CloudOff,
                    contentDescription = null,
                    tint = Color(0xFFFF007F).copy(alpha = 0.15f),
                    modifier = Modifier.size(150.dp)
                )

                // High-contrast foreground WiFi Off inside a glassmorphic card
                Box(
                    modifier = Modifier
                        .size(90.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                listOf(Color(0xFF1A1230), Color(0xFF0D061F))
                            )
                        )
                        .border(
                            2.dp,
                            Brush.linearGradient(
                                listOf(Color(0xFFFF007F), Color(0xFF00F0FF).copy(alpha = 0.3f))
                            ),
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    val rotationAnim = rememberInfiniteTransition(label = "checkingRotate")
                    val checkRotation by rotationAnim.animateFloat(
                        initialValue = 0f,
                        targetValue = 360f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(2000, easing = LinearEasing)
                        ),
                        label = "checkingRotation"
                    )

                    if (isChecking) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            tint = Color(0xFF00F0FF),
                            modifier = Modifier
                                .size(48.dp)
                                .drawBehind {
                                    drawCircle(
                                        color = Color(0xFF00F0FF).copy(alpha = 0.1f),
                                        radius = size.width * 0.7f
                                    )
                                }
                                .drawBehind {
                                    // Rotate canvas manually via rotation modifiers or use Canvas rotation
                                }
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.WifiOff,
                            contentDescription = "No Internet Connection",
                            tint = Color(0xFF00F0FF),
                            modifier = Modifier.size(44.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        // 3. Informational Text Headers
        Text(
            text = "No Internet Connection detected.".localize(appLanguage),
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = "We couldn't generate your script or video right now. Please connect to Wi-Fi or cellular data.".localize(appLanguage),
            color = Color.LightGray.copy(alpha = 0.8f),
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            lineHeight = 18.sp,
            modifier = Modifier.padding(horizontal = 8.dp)
        )

        Spacer(modifier = Modifier.height(32.dp))

        // 4. Premium "Retry Connection" Gradient Button
        Button(
            onClick = {
                if (isChecking) return@Button
                isChecking = true
                // Check connectivity
                val isOnline = NetworkUtils.isInternetAvailable(context)
                if (isOnline) {
                    onRetry()
                } else {
                    // Let the user feel the check happening with a brief delay
                    isChecking = false
                }
            },
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .height(54.dp)
                .clip(RoundedCornerShape(27.dp))
                .border(
                    1.dp,
                    Brush.horizontalGradient(listOf(Color(0xFFD4AF37), Color(0xFF00F0FF))),
                    RoundedCornerShape(27.dp)
                ),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.Transparent
            ),
            contentPadding = PaddingValues()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                Color(0xFF9E7E1D).copy(alpha = 0.8f),
                                Color(0xFF006D75).copy(alpha = 0.8f)
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    if (isChecking) {
                        CircularProgressIndicator(
                            color = Color.White,
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Retry Connection".localize(appLanguage),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // 5. Status Footer details
        Text(
            text = "Your work is saved locally and will resume when online.".localize(appLanguage),
            color = Color.Gray,
            fontSize = 11.sp,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "[Status: Connection Offline, Latency: High]".localize(appLanguage),
            color = Color(0xFFFF007F).copy(alpha = 0.8f),
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * A beautiful, YouTube-style overlay banner that informs the user they are back online.
 * Slides down from the top and fades out.
 */
@Composable
fun BackOnlineBanner(
    appLanguage: String,
    visible: Boolean,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
        modifier = modifier
    ) {
        Surface(
            color = Color(0xFF1DB954), // Spotify green
            contentColor = Color.White,
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.NetworkCheck,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "You are back online. Resuming operations...".localize(appLanguage),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }
    }
}
