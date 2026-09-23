package com.ritvyom.yashoraReelgenerator.presentation.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ritvyom.yashoraReelgenerator.presentation.utils.SoundSynth
import com.ritvyom.yashoraReelgenerator.presentation.utils.localize
import kotlinx.coroutines.launch
import kotlin.math.min

private val GlowingPurple = Color(0xFF8A2BE2)
private val HotPink = Color(0xFFFF1493)
private val CardBg = Color(0xFF1E1B33)
private val BorderColor = Color(0xFF2E2A4D)

/**
 * Modern Jetpack Compose Pull-to-Refresh Wrapper for Community Pool Feed.
 * Supports both nested scrolling gesture pull-down and material pull refresh.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommunityPullToRefreshLayout(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    lastUpdatedTimestamp: Long = 0L,
    appLanguageState: String = "en",
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current
    var pullDistancePx by remember { mutableFloatStateOf(0f) }
    val maxPullPx = with(density) { 110.dp.toPx() }
    val triggerThresholdPx = with(density) { 75.dp.toPx() }
    var hasHapticTriggered by remember { mutableStateOf(false) }

    val pullProgress by remember {
        derivedStateOf {
            if (maxPullPx > 0) min(1f, pullDistancePx / triggerThresholdPx) else 0f
        }
    }

    // Reset pull distance when refreshing stops
    LaunchedEffect(isRefreshing) {
        if (!isRefreshing) {
            pullDistancePx = 0f
            hasHapticTriggered = false
        }
    }

    // Nested scroll connection for fluid, responsive pull gesture on any scrollable child
    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                // If user scrolls down while pulled, reduce pull
                if (source == NestedScrollSource.UserInput && pullDistancePx > 0 && available.y < 0) {
                    val consumed = available.y
                    pullDistancePx = (pullDistancePx + available.y).coerceAtLeast(0f)
                    return Offset(0f, consumed)
                }
                return Offset.Zero
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                if (source == NestedScrollSource.UserInput && available.y > 0 && !isRefreshing) {
                    val newDistance = (pullDistancePx + available.y * 0.5f).coerceAtMost(maxPullPx)
                    pullDistancePx = newDistance

                    if (newDistance >= triggerThresholdPx && !hasHapticTriggered) {
                        hasHapticTriggered = true
                        coroutineScope.launch {
                            SoundSynth.playSfx("Pop")
                        }
                    }
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (pullDistancePx >= triggerThresholdPx && !isRefreshing) {
                    onRefresh()
                }
                pullDistancePx = 0f
                hasHapticTriggered = false
                return Velocity.Zero
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(nestedScrollConnection)
            .testTag("community_pull_to_refresh_box")
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Animated Pull / Refresh Header Indicator
            val isIndicatorVisible = isRefreshing || pullDistancePx > 10f

            AnimatedVisibility(
                visible = isIndicatorVisible,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        shape = RoundedCornerShape(100.dp),
                        colors = CardDefaults.cardColors(containerColor = CardBg),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            Brush.linearGradient(listOf(GlowingPurple.copy(alpha = 0.8f), HotPink.copy(alpha = 0.6f)))
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            if (isRefreshing) {
                                val infiniteTransition = rememberInfiniteTransition(label = "spin")
                                val angle by infiniteTransition.animateFloat(
                                    initialValue = 0f,
                                    targetValue = 360f,
                                    animationSpec = infiniteRepeatable(
                                        animation = tween(900, easing = LinearEasing),
                                        repeatMode = RepeatMode.Restart
                                    ),
                                    label = "spin_angle"
                                )
                                Icon(
                                    imageVector = Icons.Default.Sync,
                                    contentDescription = "Syncing",
                                    tint = GlowingPurple,
                                    modifier = Modifier
                                        .size(18.dp)
                                        .rotate(angle)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Fetching latest shared reels...".localize(appLanguageState),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            } else {
                                val isReadyToRelease = pullDistancePx >= triggerThresholdPx
                                val iconRotation by animateFloatAsState(
                                    targetValue = if (isReadyToRelease) 180f else pullProgress * 180f,
                                    label = "arrow_rot"
                                )

                                Icon(
                                    imageVector = if (isReadyToRelease) Icons.Default.CloudDownload else Icons.Default.ArrowDownward,
                                    contentDescription = "Pull Indicator",
                                    tint = if (isReadyToRelease) HotPink else GlowingPurple,
                                    modifier = Modifier
                                        .size(16.dp)
                                        .rotate(if (isReadyToRelease) 0f else iconRotation)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (isReadyToRelease) {
                                        "Release to reload from Firestore".localize(appLanguageState)
                                    } else {
                                        "Pull down to refresh feed".localize(appLanguageState)
                                    },
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (isReadyToRelease) HotPink else Color.White
                                )
                            }
                        }
                    }
                }
            }

            // Main Feed Content
            Box(modifier = Modifier.weight(1f)) {
                content()
            }
        }
    }
}
