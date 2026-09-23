package com.ritvyom.yashoraReelgenerator.presentation.components.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import com.ritvyom.yashoraReelgenerator.engine.timeline.IntelligentSnapEngine
import com.ritvyom.yashoraReelgenerator.engine.timeline.TimelineSnapPoint
import com.ritvyom.yashoraReelgenerator.engine.timeline.SnapPointType
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.ritvyom.yashoraReelgenerator.domain.models.Scene
import com.ritvyom.yashoraReelgenerator.presentation.utils.localize
import java.util.Locale

/**
 * YouCut-style multi-track Filmstrip Timeline Component:
 * - Big Round Orange (+) button to pick & import user's own videos/photos
 * - Unmute / Mute clip audio quick toggle
 * - Time readouts: current playhead time "0:00.0" and "Total 0:22.9"
 * - Chronological filmstrip track with thumbnails, duration badges, selected clip orange handles,
 *   and orange vertical playhead bar with top/bottom indicator tabs.
 */
@Composable
fun YouCutTimelineTrack(
    scenes: List<Scene>,
    selectedSceneIndex: Int,
    currentPlaybackTimeMs: Long,
    totalDurationSeconds: Int,
    isAudioMuted: Boolean,
    appLanguage: String,
    onSelectScene: (Int) -> Unit,
    onAddMediaClick: () -> Unit,
    onToggleMuteAudio: () -> Unit,
    onScrubTime: (Long) -> Unit,
    onSplitClip: (Int) -> Unit,
    onDeleteClip: (Int) -> Unit,
    audioTrackName: String? = null,
    audioTrackDurationMs: Long = 0L,
    audioTrackStartMs: Long = 0L,
    onAudioTrackClick: (() -> Unit)? = null,
    onAddAudioTrackClick: (() -> Unit)? = null,
    onOpenTransitionManager: ((clipIndex: Int) -> Unit)? = null,
    onReplaceClip: ((clipIndex: Int) -> Unit)? = null,
    onSpeedAdjustClick: ((clipIndex: Int) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()

    // Calculate candidate snap points from clip boundaries for magnetic timeline snapping
    val snapPoints = remember(scenes) {
        val points = mutableListOf<TimelineSnapPoint>()
        var accMs = 0L
        for ((idx, sc) in scenes.withIndex()) {
            val durMs = sc.durationSeconds * 1000L
            points.add(TimelineSnapPoint(accMs * 1000L, SnapPointType.CLIP_START, "Clip #" + (idx + 1) + " Start"))
            accMs += durMs
            points.add(TimelineSnapPoint(accMs * 1000L, SnapPointType.CLIP_END, "Clip #" + (idx + 1) + " End"))
        }
        points
    }
    var isSnappingActive by remember { mutableStateOf(false) }
    var activeSnapLabel by remember { mutableStateOf<String?>(null) }

    // Format time readouts like YouCut: "0:00.0" and "Total 0:22.9"
    val currentSec = currentPlaybackTimeMs / 1000f
    val currentFormatted = remember(currentPlaybackTimeMs) {
        val min = (currentSec / 60).toInt()
        val sec = currentSec % 60
        String.format(Locale.US, "%d:%04.1f", min, sec)
    }

    val totalFormatted = remember(totalDurationSeconds) {
        val min = totalDurationSeconds / 60
        val sec = (totalDurationSeconds % 60).toFloat()
        String.format(Locale.US, "Total %d:%04.1f", min, sec)
    }

    val configuration = LocalConfiguration.current
    val isSmallWidth = configuration.screenWidthDp <= 340
    val isCompactHeight = configuration.screenHeightDp < 600
    val isWideScreen = configuration.screenWidthDp >= 600

    val timelineBoxHeight = when {
        isCompactHeight -> 72.dp
        isSmallWidth -> 76.dp
        isWideScreen -> 96.dp
        else -> 84.dp
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFF101014))
            .padding(vertical = 4.dp)
            .testTag("youcut_timeline_track")
    ) {
        // Main Timeline Row: Left Actions (Big Plus & Audio Mute) + Right Filmstrip Track
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = if (isSmallWidth) 4.dp else 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 1. Big Orange Circle (+) Button (YouCut Signature)
            val leftColumnWidth = if (isSmallWidth) 46.dp else 58.dp
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier
                    .width(leftColumnWidth)
                    .padding(end = if (isSmallWidth) 4.dp else 8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(if (isSmallWidth) 40.dp else 46.dp)
                        .clip(CircleShape)
                        .background(YouCutOrange)
                        .clickable(onClick = onAddMediaClick)
                        .testTag("timeline_add_media_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Add Media to Timeline".localize(appLanguage),
                        tint = Color.White,
                        modifier = Modifier.size(if (isSmallWidth) 24.dp else 28.dp)
                    )
                }

                Spacer(modifier = Modifier.height(3.dp))

                // 2. Mute / Unmute Clip Audio Toggle Button
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable(onClick = onToggleMuteAudio)
                        .padding(horizontal = 2.dp, vertical = 2.dp)
                        .testTag("timeline_mute_audio_button"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (isAudioMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                        contentDescription = "Toggle Mute".localize(appLanguage),
                        tint = YouCutOrange,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Text(
                        text = if (isSmallWidth) (if (isAudioMuted) "Unmute" else "Mute")
                               else (if (isAudioMuted) "Unmute clip\naudio".localize(appLanguage) else "Mute clip\naudio".localize(appLanguage)),
                        color = YouCutOrange,
                        fontSize = if (isSmallWidth) 7.5.sp else 8.sp,
                        fontWeight = FontWeight.Bold,
                        lineHeight = 9.sp
                    )
                }
            }

            // 3. Filmstrip Timeline Area with orange playhead
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(timelineBoxHeight)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF1A1A22))
                    .border(1.dp, Color(0xFF2A2A38), RoundedCornerShape(8.dp))
                    .testTag("timeline_filmstrip_box")
            ) {
                LazyRow(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    itemsIndexed(scenes) { index, scene ->
                        val isSelected = index == selectedSceneIndex
                        val clipWidth = (80.dp * (scene.durationSeconds / 4f)).coerceIn(70.dp, 180.dp)

                        Box(
                            modifier = Modifier
                                .width(clipWidth)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFF242430))
                                .border(
                                    width = if (isSelected) 2.dp else 1.dp,
                                    color = if (isSelected) YouCutOrange else Color(0xFF383848),
                                    shape = RoundedCornerShape(6.dp)
                                )
                                .clickable { onSelectScene(index) }
                                .testTag("timeline_scene_$index")
                        ) {
                            // Thumbnail Preview
                            val path = scene.mediaPath
                            if (!path.isNullOrEmpty()) {
                                AsyncImage(
                                    model = path,
                                    contentDescription = scene.subtitle,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(
                                            Brush.verticalGradient(
                                                listOf(Color(0xFF2A2A3E), Color(0xFF1E1E2C))
                                            )
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = if (scene.mediaType == "VIDEO") Icons.Default.Movie else Icons.Default.Image,
                                        contentDescription = null,
                                        tint = Color.White.copy(alpha = 0.5f),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }

                            // Subtle gradient overlay for text readability
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.verticalGradient(
                                            listOf(
                                                Color.Black.copy(alpha = 0.35f),
                                                Color.Transparent,
                                                Color.Black.copy(alpha = 0.65f)
                                            )
                                        )
                                    )
                            )

                            // Top Info: Clip # and Type
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 4.dp, vertical = 2.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "#${index + 1}",
                                    color = Color.White,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold
                                )

                                if (scene.mediaType == "VIDEO") {
                                    Icon(
                                        imageVector = Icons.Default.Videocam,
                                        contentDescription = null,
                                        tint = YouCutOrange,
                                        modifier = Modifier.size(10.dp)
                                    )
                                }
                            }

                            // Bottom Left Info: Speed Badge (0.5x, 1.0x, 1.5x, 2.0x)
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(3.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(if (scene.speedMultiplier != 1.0f) YouCutOrange else Color.Black.copy(alpha = 0.75f))
                                    .clickable {
                                        onSelectScene(index)
                                        onSpeedAdjustClick?.invoke(index)
                                    }
                                    .padding(horizontal = 4.dp, vertical = 1.dp)
                            ) {
                                Text(
                                    text = "${scene.speedMultiplier}x",
                                    color = Color.White,
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }

                            // Bottom Info: Duration Badge (e.g. "5.0s")
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(3.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(Color.Black.copy(alpha = 0.75f))
                                    .padding(horizontal = 4.dp, vertical = 1.dp)
                            ) {
                                Text(
                                    text = "${scene.durationSeconds}.0s",
                                    color = Color.White,
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }

                            // Selected Clip Trim Handles (Signature YouCut orange left/right border tabs)
                            if (isSelected) {
                                // Left Trim Handle
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.CenterStart)
                                        .width(4.dp)
                                        .fillMaxHeight()
                                        .background(YouCutOrange)
                                )
                                // Right Trim Handle
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.CenterEnd)
                                        .width(4.dp)
                                        .fillMaxHeight()
                                        .background(YouCutOrange)
                                )

                                if (onReplaceClip != null) {
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = YouCutOrange.copy(alpha = 0.92f),
                                        modifier = Modifier
                                            .align(Alignment.Center)
                                            .clickable { onReplaceClip(index) }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(Icons.Default.SwapHoriz, contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp))
                                            Spacer(modifier = Modifier.width(2.dp))
                                            Text("Replace", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }

                        // Transition button between clips with accessible >=36dp touch target
                        if (index < scenes.size - 1) {
                            Box(
                                modifier = Modifier
                                    .sizeIn(minWidth = 36.dp, minHeight = 44.dp)
                                    .clickable { onOpenTransitionManager?.invoke(index) }
                                    .testTag("timeline_transition_button_$index"),
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF262634))
                                        .border(1.dp, YouCutOrange.copy(alpha = 0.7f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Animation,
                                        contentDescription = "Transition",
                                        tint = YouCutOrange,
                                        modifier = Modifier.size(13.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // Orange Playhead Bar running vertically through the timeline with drag & magnetic snapping
                val playheadColor = if (isSnappingActive) Color(0xFFFFD54F) else YouCutOrange
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .width(if (isSnappingActive) 3.dp else 2.dp)
                        .fillMaxHeight()
                        .background(playheadColor)
                        .pointerInput(totalDurationSeconds, snapPoints) {
                            detectDragGestures(
                                onDragStart = { isSnappingActive = false },
                                onDragEnd = { isSnappingActive = false; activeSnapLabel = null },
                                onDragCancel = { isSnappingActive = false; activeSnapLabel = null },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    val totalMs = (totalDurationSeconds * 1000L).coerceAtLeast(1000L)
                                    val deltaMs = (dragAmount.x * 20L).toLong()
                                    val candidateMs = (currentPlaybackTimeMs + deltaMs).coerceIn(0L, totalMs)
                                    val snapResult = IntelligentSnapEngine.findSnap(
                                        requestedTimeUs = candidateMs * 1000L,
                                        snapPoints = snapPoints,
                                        toleranceUs = 120_000L,
                                        enabled = true
                                    )
                                    if (snapResult.didSnap) {
                                        isSnappingActive = true
                                        activeSnapLabel = snapResult.snapPoint?.label
                                        onScrubTime(snapResult.snappedTimeUs / 1000L)
                                    } else {
                                        isSnappingActive = false
                                        activeSnapLabel = null
                                        onScrubTime(candidateMs)
                                    }
                                }
                            )
                        }
                ) {
                    // Top Handle Marker
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .size(if (isSnappingActive) 8.dp else 6.dp, if (isSnappingActive) 10.dp else 8.dp)
                            .background(playheadColor, RoundedCornerShape(2.dp))
                    )
                    // Bottom Handle Marker
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .size(if (isSnappingActive) 8.dp else 6.dp, if (isSnappingActive) 10.dp else 8.dp)
                            .background(playheadColor, RoundedCornerShape(2.dp))
                    )
                }

                // Snap indicator toast badge above playhead
                if (isSnappingActive && !activeSnapLabel.isNullOrEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = Color(0xFF2E2000),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFFD54F)),
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("🧲", fontSize = 9.sp)
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(
                                text = activeSnapLabel ?: "Snapped",
                                color = Color(0xFFFFD54F),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        // 4. Audio Track Management Lane
        if (!audioTrackName.isNullOrEmpty()) {
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp)
                    .height(28.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFF1B2838))
                    .border(1.dp, Color(0xFF29B6F6).copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                    .clickable { onAudioTrackClick?.invoke() }
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f, fill = false),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.MusicNote, contentDescription = null, tint = Color(0xFF29B6F6), modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = audioTrackName,
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = String.format(Locale.US, "%.1fs", audioTrackDurationMs / 1000f),
                        color = Color(0xFF29B6F6),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(Icons.Default.Tune, contentDescription = "Trim Audio", tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(12.dp))
                }
            }
        } else if (onAddAudioTrackClick != null) {
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp)
                    .height(26.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFF161622))
                    .border(1.dp, Color(0xFF2C2C3E), RoundedCornerShape(6.dp))
                    .clickable { onAddAudioTrackClick() }
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(Icons.Default.Add, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(13.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("+ Add Audio / Music Track".localize(appLanguage), color = Color.Gray, fontSize = 9.5.sp, fontWeight = FontWeight.Medium)
            }
        }

        Spacer(modifier = Modifier.height(3.dp))

        // Time Readout Row matching YouCut: Left = "0:00.0", Right = "Total 0:22.9"
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = currentFormatted,
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.Monospace
            )

            Text(
                text = totalFormatted,
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}
