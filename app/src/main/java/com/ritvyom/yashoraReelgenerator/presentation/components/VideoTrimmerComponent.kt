package com.ritvyom.yashoraReelgenerator.presentation.components

import android.net.Uri
import android.util.Log
import android.widget.VideoView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.ritvyom.yashoraReelgenerator.presentation.utils.Media3VideoTrimmer
import com.ritvyom.yashoraReelgenerator.presentation.utils.localize
import java.io.File
import java.util.Locale

@Composable
fun VideoTrimmerDialog(
    videoPath: String,
    appLanguage: String,
    onDismiss: () -> Unit,
    onTrimSuccess: (String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier
            .fillMaxWidth(0.95f)
            .padding(12.dp),
        shape = RoundedCornerShape(24.dp),
        containerColor = Color(0xFF0F0B1E),
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCut,
                            contentDescription = "Trim Video",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Media3 Video Trimmer ✂️".localize(appLanguage),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "Clip excess start/end footage before exporting".localize(appLanguage),
                            fontSize = 11.sp,
                            color = Color.LightGray
                        )
                    }
                }
            }
        },
        text = {
            VideoTrimmerComponent(
                videoPath = videoPath,
                appLanguage = appLanguage,
                onTrimCompleted = { trimmedPath ->
                    onTrimSuccess(trimmedPath)
                    onDismiss()
                },
                onCancel = onDismiss
            )
        },
        confirmButton = {}
    )
}

@Composable
fun VideoTrimmerComponent(
    videoPath: String,
    appLanguage: String,
    onTrimCompleted: (String) -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    var totalDurationMs by remember { mutableLongStateOf(0L) }

    var startMs by remember { mutableLongStateOf(0L) }
    var endMs by remember { mutableLongStateOf(0L) }

    var isTrimming by remember { mutableStateOf(false) }
    var trimProgress by remember { mutableFloatStateOf(0f) }
    var trimErrorMessage by remember { mutableStateOf<String?>(null) }

    var isPlaying by remember { mutableStateOf(false) }
    var currentPlaybackMs by remember { mutableLongStateOf(0L) }

    var videoViewInstance by remember { mutableStateOf<VideoView?>(null) }

    // Load video duration
    LaunchedEffect(videoPath) {
        val dur = Media3VideoTrimmer.getVideoDurationMs(context, videoPath)
        totalDurationMs = if (dur > 0L) dur else 10000L
        startMs = 0L
        endMs = totalDurationMs
    }

    // Loop clamping within startMs and endMs
    LaunchedEffect(startMs, endMs, isPlaying) {
        while (true) {
            kotlinx.coroutines.delay(100)
            val vv = videoViewInstance
            if (vv != null && vv.isPlaying) {
                val pos = vv.currentPosition.toLong()
                currentPlaybackMs = pos
                isPlaying = true

                if (pos >= endMs || pos < startMs) {
                    vv.seekTo(startMs.toInt())
                }
            } else if (vv != null && !vv.isPlaying) {
                isPlaying = false
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            try {
                videoViewInstance?.stopPlayback()
            } catch (e: Exception) {
                Log.e("VideoTrimmer", "Error stopping VideoView", e)
            }
        }
    }

    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(scrollState),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Video Preview Player Box
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(210.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color.Black)
                .border(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f), RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center
        ) {
            AndroidView(
                factory = { ctx ->
                    VideoView(ctx).apply {
                        videoViewInstance = this
                        val file = File(videoPath)
                        if (file.exists()) {
                            setVideoPath(videoPath)
                        } else {
                            setVideoURI(Uri.parse(videoPath))
                        }
                        setOnPreparedListener { mp ->
                            mp.isLooping = false
                            seekTo(startMs.toInt())
                            start()
                            isPlaying = true
                        }
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            // Play / Pause Overlay Button
            IconButton(
                onClick = {
                    val vv = videoViewInstance ?: return@IconButton
                    if (vv.isPlaying) {
                        vv.pause()
                        isPlaying = false
                    } else {
                        if (vv.currentPosition >= endMs || vv.currentPosition < startMs) {
                            vv.seekTo(startMs.toInt())
                        }
                        vv.start()
                        isPlaying = true
                    }
                },
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.55f))
                    .align(Alignment.Center)
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = "Play/Pause",
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }

            // Live Time Indicator Overlay
            Surface(
                color = Color.Black.copy(alpha = 0.7f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
            ) {
                Text(
                    text = "${formatTimeMs(currentPlaybackMs)} / ${formatTimeMs(totalDurationMs)}",
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Duration Summary Banner
        val trimmedDurationMs = (endMs - startMs).coerceAtLeast(0L)
        val savedDurationMs = (totalDurationMs - trimmedDurationMs).coerceAtLeast(0L)

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E192E)),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Original: ${formatTimeMs(totalDurationMs)}".localize(appLanguage),
                        color = Color.Gray,
                        fontSize = 11.sp
                    )
                    Text(
                        text = "Trimmed: ${formatTimeMs(trimmedDurationMs)}".localize(appLanguage),
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (savedDurationMs > 0L) {
                    Surface(
                        color = Color(0xFF2E7D32).copy(alpha = 0.25f),
                        shape = RoundedCornerShape(20.dp),
                        border = BorderStroke(1.dp, Color(0xFF4CAF50).copy(alpha = 0.5f))
                    ) {
                        Text(
                            text = "-${formatTimeMs(savedDurationMs)} clipped".localize(appLanguage),
                            color = Color(0xFF81C784),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Start & End Handles Dual Range Controls
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF161224), RoundedCornerShape(16.dp))
                .padding(12.dp)
        ) {
            Text(
                text = "Trim Boundaries ✂️".localize(appLanguage),
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))

            // Range Slider for visual timeline handles
            val maxRange = totalDurationMs.toFloat().coerceAtLeast(1000f)
            var rangeState by remember(startMs, endMs, totalDurationMs) {
                mutableStateOf(startMs.toFloat()..endMs.toFloat())
            }

            RangeSlider(
                value = rangeState,
                onValueChange = { newRange ->
                    val newStart = newRange.start.toLong().coerceIn(0L, totalDurationMs - 500L)
                    val newEnd = newRange.endInclusive.toLong().coerceIn(newStart + 500L, totalDurationMs)
                    rangeState = newStart.toFloat()..newEnd.toFloat()
                    startMs = newStart
                    endMs = newEnd
                },
                valueRange = 0f..maxRange,
                onValueChangeFinished = {
                    videoViewInstance?.seekTo(startMs.toInt())
                },
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                    inactiveTrackColor = Color.Gray.copy(alpha = 0.3f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("trimmer_range_slider")
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Start: ${formatTimeMs(startMs)}",
                    fontSize = 11.sp,
                    color = Color.LightGray,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "End: ${formatTimeMs(endMs)}",
                    fontSize = 11.sp,
                    color = Color.LightGray,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Fine-tune Nudge Buttons for Start Time
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Start Point:".localize(appLanguage), fontSize = 11.sp, color = Color.Gray)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    NudgeButton("-0.5s") {
                        startMs = (startMs - 500L).coerceAtLeast(0L)
                        videoViewInstance?.seekTo(startMs.toInt())
                    }
                    NudgeButton("+0.5s") {
                        startMs = (startMs + 500L).coerceAtMost(endMs - 500L)
                        videoViewInstance?.seekTo(startMs.toInt())
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Fine-tune Nudge Buttons for End Time
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("End Point:".localize(appLanguage), fontSize = 11.sp, color = Color.Gray)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    NudgeButton("-0.5s") {
                        endMs = (endMs - 500L).coerceAtLeast(startMs + 500L)
                        videoViewInstance?.seekTo(((endMs - 1000L).coerceAtLeast(startMs)).toInt())
                    }
                    NudgeButton("+0.5s") {
                        endMs = (endMs + 500L).coerceAtMost(totalDurationMs)
                        videoViewInstance?.seekTo(((endMs - 1000L).coerceAtLeast(startMs)).toInt())
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Quick Preset Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            PresetChip(
                label = "Cut Start 1s".localize(appLanguage),
                onClick = {
                    startMs = (startMs + 1000L).coerceAtMost(endMs - 500L)
                    videoViewInstance?.seekTo(startMs.toInt())
                }
            )
            PresetChip(
                label = "Cut End 1s".localize(appLanguage),
                onClick = {
                    endMs = (endMs - 1000L).coerceAtLeast(startMs + 500L)
                    videoViewInstance?.seekTo(((endMs - 1000L).coerceAtLeast(startMs)).toInt())
                }
            )
            PresetChip(
                label = "Reset".localize(appLanguage),
                onClick = {
                    startMs = 0L
                    endMs = totalDurationMs
                    videoViewInstance?.seekTo(0)
                }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Error message display
        trimErrorMessage?.let { err ->
            Text(
                text = err,
                color = MaterialTheme.colorScheme.error,
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        // Trimming Progress Indicator
        if (isTrimming) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                LinearProgressIndicator(
                    progress = { trimProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = Color.White.copy(alpha = 0.1f)
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Clipping video with Media3... ${(trimProgress * 100).toInt()}%".localize(appLanguage),
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
        } else {
            // Action Buttons: Cancel vs Apply Trim
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onCancel,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                    border = BorderStroke(1.dp, Color.Gray),
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Cancel".localize(appLanguage), fontSize = 12.sp)
                }

                Button(
                    onClick = {
                        isTrimming = true
                        trimErrorMessage = null

                        val outputFile = File(
                            context.cacheDir,
                            "Yashora_Trimmed_${System.currentTimeMillis()}.mp4"
                        )

                        Media3VideoTrimmer.trimVideo(
                            context = context,
                            inputPath = videoPath,
                            outputPath = outputFile.absolutePath,
                            startMs = startMs,
                            endMs = endMs,
                            onProgress = { pr ->
                                trimProgress = pr
                            },
                            onComplete = { success, error ->
                                isTrimming = false
                                if (success && outputFile.exists() && outputFile.length() > 0L) {
                                    Log.d("VideoTrimmer", "Video successfully trimmed: ${outputFile.absolutePath}")
                                    onTrimCompleted(outputFile.absolutePath)
                                } else {
                                    trimErrorMessage = error ?: "Failed to trim video".localize(appLanguage)
                                }
                            }
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    modifier = Modifier
                        .weight(1.3f)
                        .height(44.dp)
                        .testTag("apply_trim_button"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(imageVector = Icons.Default.ContentCut, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Apply Trim ✂️".localize(appLanguage), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun NudgeButton(label: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(6.dp),
        color = Color.White.copy(alpha = 0.08f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f))
    ) {
        Text(
            text = label,
            fontSize = 10.sp,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@Composable
private fun PresetChip(label: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
        )
    }
}

private fun formatTimeMs(ms: Long): String {
    val totalSeconds = (ms / 1000L).coerceAtLeast(0L)
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    val tenths = (ms % 1000L) / 100L
    return String.format(Locale.US, "%02d:%02d.%d", minutes, seconds, tenths)
}
