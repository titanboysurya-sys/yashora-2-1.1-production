package com.ritvyom.yashoraReelgenerator.presentation.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.ritvyom.yashoraReelgenerator.domain.models.Scene
import com.ritvyom.yashoraReelgenerator.presentation.utils.localize

/**
 * Pro Live Studio Visual Canvas & Pipeline Breakdown.
 *
 * Provides a futuristic, live-animated preview of:
 * 1. Live scene media frames swiping in/out with animated scanning laser HUD
 * 2. Visual framing aspect ratio bounds and tech indicators
 * 3. Mini scene scrubber to inspect individual scene assets in real time
 * 4. Distinct 3-stage breakdown:
 *    - ✅ Completed Work
 *    - ⚡ In-Progress Live Engine
 *    - ⏳ Remaining Pipeline Queue
 */
@OptIn(ExperimentalAnimationApi::class)
@Composable
fun ProLiveStudioCanvas(
    scenes: List<Scene>,
    activeSceneIndex: Int,
    onSelectScene: (Int) -> Unit,
    aspectRatio: String,
    resolution: String,
    styleName: String,
    languageState: String,
    progress: Float,
    modifier: Modifier = Modifier
) {
    val totalScenes = scenes.size.coerceAtLeast(1)
    val safeActiveIndex = activeSceneIndex.coerceIn(0, totalScenes - 1)
    val currentScene = scenes.getOrNull(safeActiveIndex)

    // Laser scanning animation for active video/image analysis
    val infiniteTransition = rememberInfiniteTransition(label = "LaserScan")
    val laserPosition by infiniteTransition.animateFloat(
        initialValue = 0.05f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "LaserY"
    )

    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "PulseAlpha"
    )

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // ========================================================
        // 1. LIVE MEDIA CANVAS (PHONE REEL VIEWFINDER)
        // ========================================================
        val canvasAspectRatio = when (aspectRatio) {
            "16:9" -> 16f / 9f
            "1:1" -> 1f
            else -> 9f / 14.5f // Responsive vertical reel frame that fits phone screens gracefully
        }

        Box(
            modifier = Modifier
                .fillMaxWidth(if (aspectRatio == "16:9") 0.98f else 0.82f)
                .aspectRatio(canvasAspectRatio)
                .clip(RoundedCornerShape(22.dp))
                .background(Color(0xFF0A0518))
                .border(
                    BorderStroke(
                        1.5.dp,
                        Brush.linearGradient(
                            colors = listOf(
                                Color(0xFF00F0FF).copy(alpha = 0.7f),
                                Color(0xFFFF007F).copy(alpha = 0.5f),
                                Color(0xFF00FF87).copy(alpha = 0.6f)
                            )
                        )
                    ),
                    RoundedCornerShape(22.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            // Animated slide/swipe between scenes as generation moves forward or user taps
            AnimatedContent(
                targetState = safeActiveIndex,
                transitionSpec = {
                    if (targetState >= initialState) {
                        (slideInHorizontally { width -> width } + fadeIn(tween(400)))
                            .togetherWith(slideOutHorizontally { width -> -width } + fadeOut(tween(350)))
                    } else {
                        (slideInHorizontally { width -> -width } + fadeIn(tween(400)))
                            .togetherWith(slideOutHorizontally { width -> width } + fadeOut(tween(350)))
                    }
                },
                label = "SceneSlideAnimation",
                modifier = Modifier.fillMaxSize()
            ) { sceneIdx ->
                val scene = scenes.getOrNull(sceneIdx)
                val mediaUrl = scene?.mediaPath?.takeIf { it.isNotBlank() }
                    ?: scene?.remoteUrl?.takeIf { it.isNotBlank() }

                Box(modifier = Modifier.fillMaxSize()) {
                    if (!mediaUrl.isNullOrBlank()) {
                        // Live Media Photo / Video Frame
                        AsyncImage(
                            model = mediaUrl,
                            contentDescription = "Scene Media Frame",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )

                        // Subtle dark gradient for high-contrast HUD legibility
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            Color.Black.copy(alpha = 0.6f),
                                            Color.Transparent,
                                            Color.Black.copy(alpha = 0.85f)
                                        )
                                    )
                                )
                        )
                    } else {
                        // Cybernetic Neural Scene Placeholder when media is loading/analyzing
                        CyberneticScenePlaceholder(
                            sceneIndex = sceneIdx,
                            totalScenes = totalScenes,
                            prompt = scene?.visualPrompt ?: "Drafting visual storyboard framing...",
                            languageState = languageState
                        )
                    }

                    // Futuristic Neon Laser Scanning Line (Moving vertically)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(0.015f)
                            .align(Alignment.TopCenter)
                            .offset(y = 280.dp * laserPosition)
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        Color(0xFF00F0FF).copy(alpha = 0.5f),
                                        Color.White,
                                        Color(0xFF00F0FF).copy(alpha = 0.5f),
                                        Color.Transparent
                                    )
                                )
                            )
                    )

                    // Laser Glow Reflection
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(0.06f)
                            .align(Alignment.TopCenter)
                            .offset(y = (280.dp * laserPosition) - 8.dp)
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        Color(0xFF00F0FF).copy(alpha = 0.22f),
                                        Color.Transparent
                                    )
                                )
                            )
                    )
                }
            }

            // Viewfinder Corner Brackets Overlay [  ]
            ViewfinderCornersOverlay(modifier = Modifier.fillMaxSize())

            // Top HUD Overlay (Live status & Resolution tag)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Live Analyzing Badge
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color.Black.copy(alpha = 0.65f),
                    border = BorderStroke(0.8.dp, Color(0xFF00F0FF).copy(alpha = 0.6f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF00FF87).copy(alpha = pulseAlpha))
                        )
                        Text(
                            text = "LIVE ANALYZING • S${safeActiveIndex + 1}/$totalScenes",
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                    }
                }

                // Aspect Ratio & Resolution Tag
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color.Black.copy(alpha = 0.65f),
                    border = BorderStroke(0.8.dp, Color(0xFFFF007F).copy(alpha = 0.6f))
                ) {
                    Text(
                        text = "$aspectRatio • $resolution",
                        color = Color(0xFFFF80BF),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            // Bottom Prompt & Narration Overlay Card
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Visual prompt pill
                val promptText = currentScene?.visualPrompt?.takeIf { it.isNotBlank() }
                    ?: "AI is curating viral visual aesthetics for this scene..."
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFF150E28).copy(alpha = 0.85f),
                    border = BorderStroke(0.8.dp, Color(0xFF33205E))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = Color(0xFF00F0FF),
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = promptText,
                            color = Color(0xFFE2E8F0),
                            fontSize = 11.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                // Subtitle narration preview snippet
                if (!currentScene?.narrationText.isNullOrBlank()) {
                    Text(
                        text = "\"${currentScene?.narrationText?.take(80)}...\"",
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        // ========================================================
        // 2. MINI SCENE SCRUBBER (LIVE STRIP OF ALL SCENES)
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
                    text = "AI SCENE TIMELINE".localize(languageState),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.sp,
                    color = Color(0xFF00F0FF)
                )

                Text(
                    text = "Tap to inspect frame".localize(languageState),
                    fontSize = 10.sp,
                    color = Color.Gray
                )
            }

            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 2.dp)
            ) {
                val displayCount = maxOf(scenes.size, 4)
                items(displayCount) { index ->
                    val scene = scenes.getOrNull(index)
                    val isSceneReady = scene != null && (!scene.mediaPath.isNullOrEmpty() || !scene.remoteUrl.isNullOrEmpty())
                    val isSelected = index == safeActiveIndex

                    MiniSceneThumbnailCard(
                        index = index,
                        scene = scene,
                        isReady = isSceneReady,
                        isSelected = isSelected,
                        onClick = {
                            if (index < scenes.size) {
                                onSelectScene(index)
                            }
                        }
                    )
                }
            }
        }
    }
}

/**
 * Viewfinder Corner brackets drawn via Canvas.
 */
@Composable
private fun ViewfinderCornersOverlay(modifier: Modifier = Modifier) {
    androidx.compose.foundation.Canvas(modifier = modifier.padding(14.dp)) {
        val stroke = 2.dp.toPx()
        val len = 16.dp.toPx()
        val cornerColor = Color(0xFF00F0FF).copy(alpha = 0.6f)

        // Top-Left
        drawLine(cornerColor, Offset(0f, 0f), Offset(len, 0f), stroke)
        drawLine(cornerColor, Offset(0f, 0f), Offset(0f, len), stroke)

        // Top-Right
        drawLine(cornerColor, Offset(size.width - len, 0f), Offset(size.width, 0f), stroke)
        drawLine(cornerColor, Offset(size.width, 0f), Offset(size.width, len), stroke)

        // Bottom-Left
        drawLine(cornerColor, Offset(0f, size.height - len), Offset(0f, size.height), stroke)
        drawLine(cornerColor, Offset(0f, size.height), Offset(len, size.height), stroke)

        // Bottom-Right
        drawLine(cornerColor, Offset(size.width - len, size.height), Offset(size.width, size.height), stroke)
        drawLine(cornerColor, Offset(size.width, size.height - len), Offset(size.width, size.height), stroke)
    }
}

/**
 * Cybernetic scene placeholder when media is being analyzed / generated.
 */
@Composable
private fun CyberneticScenePlaceholder(
    sceneIndex: Int,
    totalScenes: Int,
    prompt: String,
    languageState: String
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF261247),
                        Color(0xFF0E071D)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(24.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF00F0FF).copy(alpha = 0.12f))
                    .border(1.dp, Color(0xFF00F0FF).copy(alpha = 0.4f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Psychology,
                    contentDescription = null,
                    tint = Color(0xFF00F0FF),
                    modifier = Modifier.size(30.dp)
                )
            }

            Text(
                text = "Scene ${sceneIndex + 1} of $totalScenes",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            Text(
                text = "Resolving & framing optimal visuals via Multi-Source Stock Engine...".localize(languageState),
                fontSize = 11.sp,
                color = Color.LightGray,
                textAlign = TextAlign.Center,
                lineHeight = 16.sp
            )
        }
    }
}

/**
 * Mini scene thumbnail card in the horizontal strip.
 */
@Composable
private fun MiniSceneThumbnailCard(
    index: Int,
    scene: Scene?,
    isReady: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val borderColor = when {
        isSelected -> Color(0xFF00F0FF)
        isReady -> Color(0xFF00FF87).copy(alpha = 0.6f)
        else -> Color(0xFF22163E)
    }

    Box(
        modifier = Modifier
            .size(width = 62.dp, height = 76.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF140D26))
            .border(if (isSelected) 2.dp else 1.dp, borderColor, RoundedCornerShape(12.dp))
            .clickable { onClick() }
    ) {
        val mediaUrl = scene?.mediaPath?.takeIf { it.isNotBlank() } ?: scene?.remoteUrl
        if (!mediaUrl.isNullOrBlank()) {
            AsyncImage(
                model = mediaUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            // Overlay gradient
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f))
                        )
                    )
            )
        } else {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.MovieFilter,
                    contentDescription = null,
                    tint = if (isSelected) Color(0xFF00F0FF) else Color.Gray.copy(alpha = 0.5f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // Scene number tag at top-left
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(4.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Color.Black.copy(alpha = 0.7f))
                .padding(horizontal = 4.dp, vertical = 1.dp)
        ) {
            Text(
                text = "S${index + 1}",
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = if (isSelected) Color(0xFF00F0FF) else Color.White
            )
        }

        // Status badge at bottom-right
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(4.dp)
        ) {
            if (isReady) {
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF00FF87)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = Color.Black,
                        modifier = Modifier.size(11.dp)
                    )
                }
            } else if (isSelected) {
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF00F0FF)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Bolt,
                        contentDescription = null,
                        tint = Color.Black,
                        modifier = Modifier.size(11.dp)
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF22163E)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.HourglassEmpty,
                        contentDescription = null,
                        tint = Color.Gray,
                        modifier = Modifier.size(10.dp)
                    )
                }
            }
        }
    }
}

/**
 * Detailed 3-Tier Live Pipeline Task Status:
 * 1. ⚡ In-Progress Live Engine
 * 2. ✅ Completed Tasks
 * 3. ⏳ Remaining Pipeline Queue
 */
@Composable
fun ProLivePipelineStatusCards(
    currentStatus: String,
    progress: Float,
    scenes: List<Scene>,
    activeSceneIndex: Int,
    scriptText: String,
    voiceName: String,
    hasUserKey: Boolean,
    languageState: String,
    modifier: Modifier = Modifier
) {
    val completedScenesCount = scenes.count { !it.mediaPath.isNullOrBlank() || !it.remoteUrl.isNullOrBlank() }
    val wordCount = remember(scriptText) {
        scriptText.split("\\s+".toRegex()).count { it.isNotBlank() }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // ========================================================
        // SECTION 1: ⚡ CURRENTLY IN-PROGRESS (HERO CARD)
        // ========================================================
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF130A24)
            ),
            border = BorderStroke(
                1.2.dp,
                Brush.horizontalGradient(
                    listOf(
                        Color(0xFF00F0FF).copy(alpha = 0.8f),
                        Color(0xFFFF007F).copy(alpha = 0.7f)
                    )
                )
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF00F0FF))
                        )
                        Text(
                            text = "CURRENTLY IN-PROGRESS".localize(languageState),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 1.sp,
                            color = Color(0xFF00F0FF)
                        )
                    }

                    // Engine Priority Chip
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (hasUserKey) Color(0xFF00FF87).copy(alpha = 0.15f) else Color(0xFF2979FF).copy(alpha = 0.15f),
                        border = BorderStroke(
                            0.7.dp,
                            if (hasUserKey) Color(0xFF00FF87).copy(alpha = 0.4f) else Color(0xFF2979FF).copy(alpha = 0.4f)
                        )
                    ) {
                        Text(
                            text = if (hasUserKey) "Personal API (Tier 1)" else "Yashora System Engine",
                            color = if (hasUserKey) Color(0xFF00FF87) else Color(0xFF82B1FF),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                // Live status title
                Text(
                    text = currentStatus.localize(languageState),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    lineHeight = 18.sp
                )

                // Current Scene Context
                val safeScene = scenes.getOrNull(activeSceneIndex)
                if (safeScene != null && safeScene.visualPrompt.isNotBlank()) {
                    Text(
                        text = "Scene ${activeSceneIndex + 1}: ${safeScene.visualPrompt}",
                        fontSize = 11.sp,
                        color = Color(0xFFCBD5E1),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        // ========================================================
        // SECTION 2: ✅ COMPLETED WORK (DONE TASKS)
        // ========================================================
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF0D071B)
            ),
            border = BorderStroke(1.dp, Color(0xFF22163E))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "COMPLETED WORK".localize(languageState),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.sp,
                    color = Color(0xFF00FF87)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Script Generated
                    CompletedWorkChip(
                        label = if (wordCount > 0) "Script ($wordCount words)" else "Script Generated",
                        isDone = progress >= 0.20f
                    )

                    // Neural Voice
                    CompletedWorkChip(
                        label = if (voiceName.isNotBlank()) "Voice: $voiceName" else "Voice Synced",
                        isDone = progress >= 0.50f
                    )

                    // Scenes Framed
                    CompletedWorkChip(
                        label = "Scenes ($completedScenesCount/${scenes.size.coerceAtLeast(1)})",
                        isDone = completedScenesCount > 0 || progress >= 0.75f
                    )
                }
            }
        }

        // ========================================================
        // SECTION 3: ⏳ REMAINING PIPELINE QUEUE (UPCOMING TASKS)
        // ========================================================
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF0A0515)
            ),
            border = BorderStroke(1.dp, Color(0xFF1E1335))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "REMAINING QUEUE".localize(languageState),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.sp,
                    color = Color.Gray
                )

                val queueItems = listOf(
                    Triple("Audio Equalization & SFX Mix", 0.70f, Icons.Default.GraphicEq),
                    Triple("Subtitles Timing & Karaoke Alignment", 0.85f, Icons.Default.Subtitles),
                    Triple("Final 1080p MP4 Composition", 0.98f, Icons.Default.CheckCircleOutline)
                )

                queueItems.forEach { (taskName, threshold, icon) ->
                    val isPending = progress < threshold
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = if (isPending) Color.Gray.copy(alpha = 0.5f) else Color(0xFF00FF87),
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = taskName.localize(languageState),
                            fontSize = 11.sp,
                            color = if (isPending) Color.Gray.copy(alpha = 0.8f) else Color(0xFF00FF87),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CompletedWorkChip(label: String, isDone: Boolean) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (isDone) Color(0xFF00FF87).copy(alpha = 0.12f) else Color(0xFF1E1335).copy(alpha = 0.5f),
        border = BorderStroke(
            0.7.dp,
            if (isDone) Color(0xFF00FF87).copy(alpha = 0.35f) else Color(0xFF2E1C50)
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = if (isDone) Icons.Default.CheckCircle else Icons.Default.HourglassTop,
                contentDescription = null,
                tint = if (isDone) Color(0xFF00FF87) else Color.Gray,
                modifier = Modifier.size(11.dp)
            )
            Text(
                text = label,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (isDone) Color(0xFF00FF87) else Color.Gray,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
