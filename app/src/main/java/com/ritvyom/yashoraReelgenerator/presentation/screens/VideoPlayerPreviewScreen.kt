@file:kotlin.OptIn(
    androidx.media3.common.util.UnstableApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class
)

package com.ritvyom.yashoraReelgenerator.presentation.screens

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.ritvyom.yashoraReelgenerator.domain.models.Scene
import com.ritvyom.yashoraReelgenerator.presentation.utils.Media3VideoTrimmer
import com.ritvyom.yashoraReelgenerator.presentation.utils.VideoFileManager
import com.ritvyom.yashoraReelgenerator.presentation.utils.localize
import com.ritvyom.yashoraReelgenerator.presentation.viewmodels.MainViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.io.File
import java.util.Locale

private const val TAG = "VideoPlayerPreview"

/**
 * High-fidelity Video Player Preview Screen utilizing AndroidX Media3 (ExoPlayer & Media3VideoTrimmer).
 * Allows users to:
 * 1. Seamlessly preview individual scenes or the entire sequence with hardware-accelerated Media3 playback.
 * 2. Trim video scenes with real-time in/out scrubbing and apply lossless trims via Media3 Transformer.
 * 3. Reorder scenes in the timeline sequence before committing to the final export process.
 */
@OptIn(UnstableApi::class)
@Composable
fun VideoPlayerPreviewScreen(
    viewModel: MainViewModel,
    appLanguageState: String,
    onBack: () -> Unit,
    onProceedToExport: () -> Unit
) {
    val context = LocalContext.current
    val activeScenes by viewModel.activeScenes.collectAsState()
    val isTrimming by viewModel.isTrimming.collectAsState()
    val trimProgress by viewModel.trimProgress.collectAsState()
    val isExporting by viewModel.isExporting.collectAsState()
    val exportProgress by viewModel.exportProgress.collectAsState()
    val exportStatus by viewModel.exportStatus.collectAsState()

    var selectedIndex by remember { mutableIntStateOf(0) }
    val currentScene = activeScenes.getOrNull(selectedIndex)

    // Playback mode: false = Loop Current Scene, true = Continuous Full Reel Playback
    var playContinuousSequence by remember { mutableStateOf(false) }
    var isPlaying by remember { mutableStateOf(true) }
    var isMuted by remember { mutableStateOf(false) }

    // Position tracking
    var currentPlaybackMs by remember { mutableLongStateOf(0L) }
    var totalSceneDurationMs by remember { mutableLongStateOf(5000L) }

    // Trimming range state for current scene
    var rawClipDurationMs by remember { mutableLongStateOf(5000L) }
    var trimStartMs by remember { mutableLongStateOf(0L) }
    var trimEndMs by remember { mutableLongStateOf(5000L) }

    // Export Quality Dialog
    var showExportDialog by remember { mutableStateOf(false) }
    var selectedRes by remember { mutableStateOf("1080p") }
    var isEnhanceChecked by remember { mutableStateOf(false) }
    var trimSuccessNotification by remember { mutableStateOf<String?>(null) }

    // Ensure selectedIndex remains in bounds if scenes change
    LaunchedEffect(activeScenes.size) {
        if (selectedIndex >= activeScenes.size && activeScenes.isNotEmpty()) {
            selectedIndex = activeScenes.size - 1
        }
    }

    // Load media duration and initialize trim range whenever selected scene changes
    LaunchedEffect(selectedIndex, currentScene?.mediaPath) {
        val path = currentScene?.mediaPath
        if (!path.isNullOrEmpty() && (currentScene?.mediaType == "VIDEO" || path.endsWith(".mp4", ignoreCase = true))) {
            val detectedDur = Media3VideoTrimmer.getVideoDurationMs(context, path)
            rawClipDurationMs = if (detectedDur > 0L) detectedDur else (currentScene?.durationSeconds?.toLong() ?: 5L) * 1000L
        } else {
            rawClipDurationMs = (currentScene?.durationSeconds?.toLong() ?: 5L) * 1000L
        }
        totalSceneDurationMs = rawClipDurationMs
        trimStartMs = 0L
        trimEndMs = rawClipDurationMs
        currentPlaybackMs = 0L
    }

    // Compute total sequence duration
    val totalReelDurationSec = remember(activeScenes) {
        activeScenes.sumOf { it.durationSeconds }
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .testTag("video_player_preview_screen"),
        containerColor = Color(0xFF07040D),
        topBar = {
            PreviewTopBar(
                sceneCount = activeScenes.size,
                totalDurationSec = totalReelDurationSec,
                appLanguage = appLanguageState,
                onBack = onBack,
                onExportClick = { showExportDialog = true }
            )
        },
        bottomBar = {
            PreviewBottomBar(
                appLanguage = appLanguageState,
                totalDurationSec = totalReelDurationSec,
                sceneCount = activeScenes.size,
                onBack = onBack,
                onProceedToExport = { showExportDialog = true }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // 1. Media3 ExoPlayer Video Player Area
            if (activeScenes.isNotEmpty() && currentScene != null) {
                Media3PlayerContainer(
                    scene = currentScene,
                    sceneIndex = selectedIndex,
                    totalScenes = activeScenes.size,
                    appLanguage = appLanguageState,
                    isPlaying = isPlaying,
                    isMuted = isMuted,
                    playContinuousSequence = playContinuousSequence,
                    trimStartMs = trimStartMs,
                    trimEndMs = trimEndMs,
                    onPlayPauseToggle = { isPlaying = !isPlaying },
                    onMuteToggle = { isMuted = !isMuted },
                    onToggleSequenceMode = { playContinuousSequence = !playContinuousSequence },
                    onPlaybackEnded = {
                        if (playContinuousSequence) {
                            if (selectedIndex < activeScenes.size - 1) {
                                selectedIndex++
                            } else {
                                selectedIndex = 0 // Loop full reel
                            }
                        } else {
                            // Loop current scene
                            currentPlaybackMs = trimStartMs
                        }
                    },
                    onPositionChanged = { curMs, durMs ->
                        currentPlaybackMs = curMs
                        if (durMs > 0L) totalSceneDurationMs = durMs
                    },
                    onPreviousScene = {
                        if (selectedIndex > 0) {
                            selectedIndex--
                        }
                    },
                    onNextScene = {
                        if (selectedIndex < activeScenes.size - 1) {
                            selectedIndex++
                        }
                    },
                    onSeekTo = { targetMs ->
                        currentPlaybackMs = targetMs
                    }
                )
            } else {
                EmptyScenesCard(appLanguage = appLanguageState)
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 2. Timeline Sequence & Scene Reordering
            TimelineReorderSection(
                scenes = activeScenes,
                selectedIndex = selectedIndex,
                appLanguage = appLanguageState,
                onSelectScene = { idx ->
                    selectedIndex = idx
                    isPlaying = true
                },
                onMoveSceneLeft = { fromIdx ->
                    if (fromIdx > 0) {
                        viewModel.reorderScenes(fromIdx, fromIdx - 1)
                        selectedIndex = fromIdx - 1
                    }
                },
                onMoveSceneRight = { fromIdx ->
                    if (fromIdx < activeScenes.size - 1) {
                        viewModel.reorderScenes(fromIdx, fromIdx + 1)
                        selectedIndex = fromIdx + 1
                    }
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 3. Trimming Studio (Media3-Powered)
            if (currentScene != null) {
                TrimmingStudioSection(
                    scene = currentScene,
                    sceneIndex = selectedIndex,
                    rawDurationMs = rawClipDurationMs,
                    trimStartMs = trimStartMs,
                    trimEndMs = trimEndMs,
                    appLanguage = appLanguageState,
                    onTrimChange = { start, end ->
                        trimStartMs = start
                        trimEndMs = end
                    },
                    onApplyMedia3Trim = { start, end ->
                        val path = currentScene.mediaPath
                        if (!path.isNullOrEmpty()) {
                            viewModel.trimSceneVideo(
                                context = context,
                                sceneIndex = selectedIndex,
                                startMs = start,
                                endMs = end,
                                onFinished = { success, err ->
                                    if (success) {
                                        trimSuccessNotification = "Scene #${selectedIndex + 1} trimmed successfully!".localize(appLanguageState)
                                    } else {
                                        trimSuccessNotification = (err ?: "Trim failed").localize(appLanguageState)
                                    }
                                }
                            )
                        } else {
                            val newDurationSec = (((end - start) / 1000L).toInt()).coerceAtLeast(1)
                            viewModel.adjustSceneDuration(selectedIndex, newDurationSec)
                            trimSuccessNotification = "Scene duration updated to ${newDurationSec}s".localize(appLanguageState)
                        }
                    },
                    onDurationAdjusted = { newSecs ->
                        viewModel.adjustSceneDuration(selectedIndex, newSecs)
                    }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    // Media3 Trimming Progress Dialog
    if (isTrimming) {
        AlertDialog(
            onDismissRequest = {},
            containerColor = Color(0xFF140D24),
            shape = RoundedCornerShape(20.dp),
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        progress = { trimProgress },
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 3.dp
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Media3 Transformer ✂️".localize(appLanguageState),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            },
            text = {
                Column {
                    Text(
                        text = "Slicing scene clip to exact milliseconds...".localize(appLanguageState),
                        color = Color.LightGray,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    LinearProgressIndicator(
                        progress = { trimProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(CircleShape),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = Color(0xFF2A1C47)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "${(trimProgress * 100).toInt()}%",
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.align(Alignment.End)
                    )
                }
            },
            confirmButton = {}
        )
    }

    // Trim Success/Notification Toast Dialog
    trimSuccessNotification?.let { msg ->
        AlertDialog(
            onDismissRequest = { trimSuccessNotification = null },
            containerColor = Color(0xFF160F29),
            shape = RoundedCornerShape(16.dp),
            icon = {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = Color(0xFF4CAF50),
                    modifier = Modifier.size(36.dp)
                )
            },
            title = {
                Text("Media3 Update".localize(appLanguageState), color = Color.White, fontWeight = FontWeight.Bold)
            },
            text = {
                Text(msg, color = Color.LightGray, fontSize = 13.sp)
            },
            confirmButton = {
                Button(
                    onClick = { trimSuccessNotification = null },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("OK", fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    // Export Confirmation & Quality Selector Dialog
    if (showExportDialog) {
        ExportConfirmationDialog(
            appLanguage = appLanguageState,
            totalDurationSec = totalReelDurationSec,
            sceneCount = activeScenes.size,
            selectedRes = selectedRes,
            isEnhanceChecked = isEnhanceChecked,
            onResChange = { selectedRes = it },
            onEnhanceToggle = { isEnhanceChecked = it },
            onDismiss = { showExportDialog = false },
            onConfirmExport = {
                showExportDialog = false
                viewModel.selectedResolution.value = selectedRes
                viewModel.setEnhanceEnabled(isEnhanceChecked)
                onProceedToExport()
            }
        )
    }

    // Active Exporting Overlay if export was triggered
    if (isExporting) {
        ExportProgressOverlay(
            progress = exportProgress,
            status = exportStatus,
            appLanguage = appLanguageState
        )
    }
}

// -----------------------------------------------------------------------------
// TOP BAR
// -----------------------------------------------------------------------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PreviewTopBar(
    sceneCount: Int,
    totalDurationSec: Int,
    appLanguage: String,
    onBack: () -> Unit,
    onExportClick: () -> Unit
) {
    TopAppBar(
        title = {
            Column {
                Text(
                    text = "Video Preview & Cut 🎬".localize(appLanguage),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = "$sceneCount scenes • ${totalDurationSec}s total".localize(appLanguage),
                    fontSize = 11.sp,
                    color = Color(0xFFB3A8C9)
                )
            }
        },
        navigationIcon = {
            IconButton(
                onClick = onBack,
                modifier = Modifier.testTag("preview_back_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back".localize(appLanguage),
                    tint = Color.White
                )
            }
        },
        actions = {
            Button(
                onClick = onExportClick,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .padding(end = 8.dp)
                    .testTag("preview_top_export_button")
            ) {
                Icon(
                    imageVector = Icons.Default.RocketLaunch,
                    contentDescription = null,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Export".localize(appLanguage),
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color(0xFF0D0819)
        )
    )
}

// -----------------------------------------------------------------------------
// MEDIA3 PLAYER CONTAINER
// -----------------------------------------------------------------------------
@OptIn(UnstableApi::class)
@Composable
private fun Media3PlayerContainer(
    scene: Scene,
    sceneIndex: Int,
    totalScenes: Int,
    appLanguage: String,
    isPlaying: Boolean,
    isMuted: Boolean,
    playContinuousSequence: Boolean,
    trimStartMs: Long,
    trimEndMs: Long,
    onPlayPauseToggle: () -> Unit,
    onMuteToggle: () -> Unit,
    onToggleSequenceMode: () -> Unit,
    onPlaybackEnded: () -> Unit,
    onPositionChanged: (Long, Long) -> Unit,
    onPreviousScene: () -> Unit,
    onNextScene: () -> Unit,
    onSeekTo: (Long) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val isVideoClip = remember(scene.mediaType, scene.mediaPath) {
        val path = scene.mediaPath ?: ""
        scene.mediaType == "VIDEO" || path.endsWith(".mp4", ignoreCase = true)
    }

    // Resolve playable Uri
    val mediaUri = remember(scene.mediaPath) {
        val path = scene.mediaPath ?: ""
        when {
            path.startsWith("content://") || path.startsWith("http://") || path.startsWith("https://") -> Uri.parse(path)
            path.isNotEmpty() -> {
                val f = File(path)
                if (f.exists()) Uri.fromFile(f) else VideoFileManager.getPlayableUri(context, path)
            }
            else -> null
        }
    }

    // Media3 ExoPlayer Instance
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            repeatMode = Player.REPEAT_MODE_OFF
            volume = if (isMuted) 0f else 1f
        }
    }

    // Release player on dispose
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) {
                exoPlayer.pause()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            exoPlayer.release()
        }
    }

    // Update volume
    LaunchedEffect(isMuted) {
        exoPlayer.volume = if (isMuted) 0f else 1f
    }

    // Set MediaItem with ClippingConfiguration for lossless instant preview of trim!
    LaunchedEffect(mediaUri, trimStartMs, trimEndMs, isVideoClip) {
        if (isVideoClip && mediaUri != null) {
            try {
                val clippingConfig = if (trimEndMs > trimStartMs && trimEndMs > 0L) {
                    MediaItem.ClippingConfiguration.Builder()
                        .setStartPositionMs(trimStartMs)
                        .setEndPositionMs(trimEndMs)
                        .setStartsAtKeyFrame(false)
                        .build()
                } else {
                    MediaItem.ClippingConfiguration.UNSET
                }

                val item = MediaItem.Builder()
                    .setUri(mediaUri)
                    .setClippingConfiguration(clippingConfig)
                    .build()

                exoPlayer.setMediaItem(item)
                exoPlayer.prepare()
                if (isPlaying) exoPlayer.play()
            } catch (e: Exception) {
                Log.e(TAG, "Failed preparing Media3 ExoPlayer", e)
            }
        } else {
            exoPlayer.stop()
        }
    }

    // Handle isPlaying
    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            if (exoPlayer.playbackState == Player.STATE_ENDED) {
                exoPlayer.seekTo(0)
            }
            exoPlayer.play()
        } else {
            exoPlayer.pause()
        }
    }

    // Listener for state ended
    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    onPlaybackEnded()
                }
            }
        }
        exoPlayer.addListener(listener)
        onDispose { exoPlayer.removeListener(listener) }
    }

    // Progress polling loop
    var currentProgressMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(5000L) }

    LaunchedEffect(isVideoClip, isPlaying, exoPlayer) {
        while (isActive) {
            if (isVideoClip && isPlaying) {
                val pos = exoPlayer.currentPosition
                val dur = exoPlayer.duration
                currentProgressMs = pos.coerceAtLeast(0L)
                if (dur > 0L) {
                    durationMs = dur
                    onPositionChanged(pos, dur)
                }
            } else if (!isVideoClip && isPlaying) {
                // Image timer simulation
                val targetMs = scene.durationSeconds * 1000L
                durationMs = targetMs
                if (currentProgressMs < targetMs) {
                    currentProgressMs += 100L
                    onPositionChanged(currentProgressMs, targetMs)
                } else {
                    currentProgressMs = 0L
                    onPlaybackEnded()
                }
            }
            delay(100)
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth(0.94f)
            .clip(RoundedCornerShape(20.dp))
            .border(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f), RoundedCornerShape(20.dp)),
        colors = CardDefaults.cardColors(containerColor = Color.Black),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // Player View Area (9:16 Aspect Box)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(360.dp)
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                if (isVideoClip && mediaUri != null) {
                    AndroidView(
                        factory = { ctx ->
                            PlayerView(ctx).apply {
                                player = exoPlayer
                                useController = false
                                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                            }
                        },
                        update = { pv ->
                            pv.player = exoPlayer
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    // Image Scene Preview
                    val imagePath = scene.mediaPath
                    if (!imagePath.isNullOrEmpty()) {
                        AsyncImage(
                            model = imagePath,
                            contentDescription = "Scene Image Preview",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit
                        )
                    } else {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Default.Movie,
                                    contentDescription = null,
                                    tint = Color.DarkGray,
                                    modifier = Modifier.size(48.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Generating visual backdrop...".localize(appLanguage),
                                    color = Color.Gray,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }

                // Subtitle / Narration overlay if present
                if (scene.subtitle.isNotBlank() || scene.narrationText.isNotBlank()) {
                    val overlayText = if (scene.subtitle.isNotBlank()) scene.subtitle else scene.narrationText
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 16.dp, start = 12.dp, end = 12.dp)
                            .background(Color(0xBB000000), RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = overlayText,
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Top Badge: Scene Indicator & Sequence Switcher
                Row(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = Color(0xCC110B22),
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x44FFFFFF))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Scene ${sceneIndex + 1}/$totalScenes",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                color = if (isVideoClip) MaterialTheme.colorScheme.primary else Color(0xFF2A8C82),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = if (isVideoClip) "VIDEO" else "IMAGE",
                                    color = Color.White,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                        }
                    }

                    // Mode Toggle: Loop Single Scene vs Play Continuous Reel
                    Surface(
                        color = if (playContinuousSequence) MaterialTheme.colorScheme.primary else Color(0xCC1B142E),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.clickable { onToggleSequenceMode() }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (playContinuousSequence) Icons.Default.PlaylistPlay else Icons.Default.RepeatOne,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (playContinuousSequence) "Full Reel".localize(appLanguage) else "Loop Scene".localize(appLanguage),
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            // Player Scrubber Bar & Controls
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF0F0B1C))
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                // Scrubber Slider
                val sliderPos = if (durationMs > 0) (currentProgressMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f
                Slider(
                    value = sliderPos,
                    onValueChange = { frac ->
                        val target = (frac * durationMs).toLong()
                        onSeekTo(target)
                        if (isVideoClip) exoPlayer.seekTo(target)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(24.dp)
                        .testTag("preview_scrubber_slider"),
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                        inactiveTrackColor = Color(0xFF2E2245)
                    )
                )

                // Time Labels & Playback Controls Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    val currentSec = (currentProgressMs / 1000f)
                    val totalSec = (durationMs / 1000f)
                    Text(
                        text = String.format(Locale.US, "%04.1fs / %04.1fs", currentSec, totalSec),
                        color = Color.LightGray,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )

                    // Control buttons
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = onPreviousScene,
                            enabled = sceneIndex > 0,
                            modifier = Modifier.size(36.dp).testTag("preview_prev_scene_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.SkipPrevious,
                                contentDescription = "Previous Scene",
                                tint = if (sceneIndex > 0) Color.White else Color.DarkGray
                            )
                        }

                        IconButton(
                            onClick = onPlayPauseToggle,
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary)
                                .testTag("preview_play_pause_button")
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (isPlaying) "Pause" else "Play",
                                tint = Color.White,
                                modifier = Modifier.size(26.dp)
                            )
                        }

                        IconButton(
                            onClick = onNextScene,
                            enabled = sceneIndex < totalScenes - 1,
                            modifier = Modifier.size(36.dp).testTag("preview_next_scene_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.SkipNext,
                                contentDescription = "Next Scene",
                                tint = if (sceneIndex < totalScenes - 1) Color.White else Color.DarkGray
                            )
                        }

                        Spacer(modifier = Modifier.width(4.dp))

                        IconButton(
                            onClick = onMuteToggle,
                            modifier = Modifier.size(36.dp).testTag("preview_mute_button")
                        ) {
                            Icon(
                                imageVector = if (isMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                                contentDescription = if (isMuted) "Unmute" else "Mute",
                                tint = if (isMuted) Color(0xFFFF5252) else Color.LightGray,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

// -----------------------------------------------------------------------------
// TIMELINE & SCENE REORDERING SECTION
// -----------------------------------------------------------------------------
@Composable
private fun TimelineReorderSection(
    scenes: List<Scene>,
    selectedIndex: Int,
    appLanguage: String,
    onSelectScene: (Int) -> Unit,
    onMoveSceneLeft: (Int) -> Unit,
    onMoveSceneRight: (Int) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.ViewCarousel,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Timeline & Scene Order".localize(appLanguage),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
            Text(
                text = "Use < > to reorder".localize(appLanguage),
                fontSize = 11.sp,
                color = Color.Gray
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Horizontal timeline row
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("timeline_scenes_row"),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(vertical = 4.dp)
        ) {
            itemsIndexed(scenes) { index, scene ->
                val isSelected = index == selectedIndex
                TimelineSceneCard(
                    scene = scene,
                    index = index,
                    totalCount = scenes.size,
                    isSelected = isSelected,
                    appLanguage = appLanguage,
                    onClick = { onSelectScene(index) },
                    onMoveLeft = { onMoveSceneLeft(index) },
                    onMoveRight = { onMoveSceneRight(index) }
                )
            }
        }
    }
}

@Composable
private fun TimelineSceneCard(
    scene: Scene,
    index: Int,
    totalCount: Int,
    isSelected: Boolean,
    appLanguage: String,
    onClick: () -> Unit,
    onMoveLeft: () -> Unit,
    onMoveRight: () -> Unit
) {
    val borderColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.primary else Color(0xFF251A3B),
        animationSpec = tween(200),
        label = "sceneCardBorder"
    )

    Card(
        modifier = Modifier
            .width(115.dp)
            .clip(RoundedCornerShape(14.dp))
            .border(if (isSelected) 2.dp else 1.dp, borderColor, RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .testTag("timeline_scene_card_$index"),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF130E22)),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 6.dp else 2.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // Thumbnail container
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp)
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                if (!scene.mediaPath.isNullOrEmpty()) {
                    AsyncImage(
                        model = scene.mediaPath,
                        contentDescription = "Scene $index",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Movie,
                        contentDescription = null,
                        tint = Color.DarkGray,
                        modifier = Modifier.size(28.dp)
                    )
                }

                // Index badge
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(4.dp)
                        .background(Color(0xDD000000), RoundedCornerShape(4.dp))
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "#${index + 1}",
                        color = Color.White,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Duration badge
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(4.dp)
                        .background(Color(0xCC6200EE), RoundedCornerShape(4.dp))
                        .padding(horizontal = 4.dp, vertical = 1.dp)
                ) {
                    Text(
                        text = "${scene.durationSeconds}s",
                        color = Color.White,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Reorder Controls Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF19122C))
                    .padding(horizontal = 2.dp, vertical = 3.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Move Left button
                IconButton(
                    onClick = onMoveLeft,
                    enabled = index > 0,
                    modifier = Modifier
                        .size(28.dp)
                        .testTag("scene_reorder_left_$index")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Move earlier",
                        tint = if (index > 0) Color.White else Color(0x33FFFFFF),
                        modifier = Modifier.size(14.dp)
                    )
                }

                Text(
                    text = "Pos ${index + 1}",
                    color = Color.LightGray,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium
                )

                // Move Right button
                IconButton(
                    onClick = onMoveRight,
                    enabled = index < totalCount - 1,
                    modifier = Modifier
                        .size(28.dp)
                        .testTag("scene_reorder_right_$index")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "Move later",
                        tint = if (index < totalCount - 1) Color.White else Color(0x33FFFFFF),
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}

// -----------------------------------------------------------------------------
// TRIMMING STUDIO (MEDIA3-POWERED)
// -----------------------------------------------------------------------------
@Composable
private fun TrimmingStudioSection(
    scene: Scene,
    sceneIndex: Int,
    rawDurationMs: Long,
    trimStartMs: Long,
    trimEndMs: Long,
    appLanguage: String,
    onTrimChange: (Long, Long) -> Unit,
    onApplyMedia3Trim: (Long, Long) -> Unit,
    onDurationAdjusted: (Int) -> Unit
) {
    val isVideo = remember(scene.mediaType, scene.mediaPath) {
        val path = scene.mediaPath ?: ""
        scene.mediaType == "VIDEO" || path.endsWith(".mp4", ignoreCase = true)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth(0.94f)
            .clip(RoundedCornerShape(18.dp))
            .border(1.dp, Color(0xFF2E2248), RoundedCornerShape(18.dp))
            .testTag("trimming_studio_card"),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF110B22))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.ContentCut,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Trim Scene #${sceneIndex + 1}".localize(appLanguage),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                Surface(
                    color = Color(0xFF24183E),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = if (isVideo) "Media3 Lossless Cut".localize(appLanguage) else "Duration Adjust".localize(appLanguage),
                        color = Color(0xFFD0BCFF),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (isVideo) {
                // Video Trimming Details & Controls
                val validRawDur = rawDurationMs.coerceAtLeast(1000L)
                val currentTrimDurationSec = ((trimEndMs - trimStartMs) / 1000f).coerceAtLeast(0.5f)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = String.format(Locale.US, "Start: %04.1fs", trimStartMs / 1000f),
                        color = Color(0xFF80D8FF),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = String.format(Locale.US, "Length: %04.1fs", currentTrimDurationSec),
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = String.format(Locale.US, "End: %04.1fs", trimEndMs / 1000f),
                        color = Color(0xFFFF80AB),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Dual Range Slider for Start/End In-Out Trimming
                val startFrac = (trimStartMs.toFloat() / validRawDur.toFloat()).coerceIn(0f, 1f)
                val endFrac = (trimEndMs.toFloat() / validRawDur.toFloat()).coerceIn(0f, 1f)

                RangeSlider(
                    value = startFrac..endFrac,
                    onValueChange = { range ->
                        val newStart = (range.start * validRawDur).toLong()
                        val newEnd = (range.endInclusive * validRawDur).toLong().coerceAtLeast(newStart + 500L)
                        onTrimChange(newStart, newEnd)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("trim_range_slider"),
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                        inactiveTrackColor = Color(0xFF2E2248)
                    )
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Micro Adjustment Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Button(
                            onClick = {
                                val newStart = (trimStartMs - 500L).coerceAtLeast(0L)
                                onTrimChange(newStart, trimEndMs)
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.height(28.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E1438))
                        ) {
                            Text("-0.5s", fontSize = 10.sp, color = Color.White)
                        }
                        Button(
                            onClick = {
                                val newStart = (trimStartMs + 500L).coerceAtMost(trimEndMs - 500L)
                                onTrimChange(newStart, trimEndMs)
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.height(28.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E1438))
                        ) {
                            Text("+0.5s", fontSize = 10.sp, color = Color.White)
                        }
                    }

                    // Quick Presets
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        PresetChip(label = "First 3s") {
                            val newEnd = 3000L.coerceAtMost(validRawDur)
                            onTrimChange(0L, newEnd)
                        }
                        PresetChip(label = "Center 5s") {
                            val center = validRawDur / 2L
                            val newStart = (center - 2500L).coerceAtLeast(0L)
                            val newEnd = (center + 2500L).coerceAtMost(validRawDur)
                            onTrimChange(newStart, newEnd)
                        }
                        PresetChip(label = "Reset") {
                            onTrimChange(0L, validRawDur)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Apply Media3 Trim Button
                Button(
                    onClick = { onApplyMedia3Trim(trimStartMs, trimEndMs) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(42.dp)
                        .testTag("apply_trim_media3_button"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = Color.White
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCut,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Apply Media3 Trim ✂️".localize(appLanguage),
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            } else {
                // Static / Image Scene Duration Control
                Text(
                    text = "Display Duration: ${scene.durationSeconds}s".localize(appLanguage),
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(6.dp))

                Slider(
                    value = scene.durationSeconds.toFloat(),
                    onValueChange = { onDurationAdjusted(it.toInt().coerceIn(1, 30)) },
                    valueRange = 1f..30f,
                    steps = 28,
                    modifier = Modifier.fillMaxWidth().testTag("image_duration_slider"),
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                        inactiveTrackColor = Color(0xFF2E2248)
                    )
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(3, 5, 8, 10).forEach { sec ->
                        OutlinedButton(
                            onClick = { onDurationAdjusted(sec) },
                            modifier = Modifier.weight(1f).height(32.dp),
                            contentPadding = PaddingValues(0.dp),
                            shape = RoundedCornerShape(8.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, if (scene.durationSeconds == sec) MaterialTheme.colorScheme.primary else Color.DarkGray)
                        ) {
                            Text("${sec}s", fontSize = 11.sp, color = Color.White)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PresetChip(label: String, onClick: () -> Unit) {
    Surface(
        color = Color(0xFF21163A),
        shape = RoundedCornerShape(6.dp),
        modifier = Modifier.clickable { onClick() }
    ) {
        Text(
            text = label,
            color = Color.LightGray,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
        )
    }
}

// -----------------------------------------------------------------------------
// BOTTOM BAR
// -----------------------------------------------------------------------------
@Composable
private fun PreviewBottomBar(
    appLanguage: String,
    totalDurationSec: Int,
    sceneCount: Int,
    onBack: () -> Unit,
    onProceedToExport: () -> Unit
) {
    Surface(
        color = Color(0xFF0D0819),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E1433)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            OutlinedButton(
                onClick = onBack,
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF372757)),
                modifier = Modifier
                    .weight(0.8f)
                    .height(44.dp)
                    .testTag("preview_back_editor_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = null,
                    modifier = Modifier.size(15.dp),
                    tint = Color.LightGray
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Edit".localize(appLanguage),
                    color = Color.LightGray,
                    fontSize = 12.sp
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Button(
                onClick = onProceedToExport,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = Color.White
                ),
                modifier = Modifier
                    .weight(1.4f)
                    .height(44.dp)
                    .testTag("preview_proceed_export_button")
            ) {
                Icon(
                    imageVector = Icons.Default.RocketLaunch,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Proceed to Export".localize(appLanguage),
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            }
        }
    }
}

// -----------------------------------------------------------------------------
// EXPORT CONFIRMATION DIALOG
// -----------------------------------------------------------------------------
@Composable
private fun ExportConfirmationDialog(
    appLanguage: String,
    totalDurationSec: Int,
    sceneCount: Int,
    selectedRes: String,
    isEnhanceChecked: Boolean,
    onResChange: (String) -> Unit,
    onEnhanceToggle: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    onConfirmExport: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF140E26),
        shape = RoundedCornerShape(22.dp),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Movie,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Final Reel Export 🚀".localize(appLanguage),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp
                )
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Your scenes have been trimmed and arranged in sequence. Ready to compile final high-res video:".localize(appLanguage),
                    color = Color.LightGray,
                    fontSize = 12.sp
                )

                Spacer(modifier = Modifier.height(12.dp))

                Surface(
                    color = Color(0xFF1F1539),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Scenes".localize(appLanguage), fontSize = 10.sp, color = Color.Gray)
                            Text("$sceneCount", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Total Runtime".localize(appLanguage), fontSize = 10.sp, color = Color.Gray)
                            Text("${totalDurationSec}s", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Quality".localize(appLanguage), fontSize = 10.sp, color = Color.Gray)
                            Text(selectedRes, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF80D8FF))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Resolution Selector
                Text(
                    text = "Export Resolution:".localize(appLanguage),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("720p", "1080p", "4K").forEach { res ->
                        val isSelected = selectedRes == res
                        FilterChip(
                            selected = isSelected,
                            onClick = { onResChange(res) },
                            label = { Text(res, fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = Color.White,
                                containerColor = Color(0xFF22183D),
                                labelColor = Color.LightGray
                            ),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirmExport,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                modifier = Modifier.testTag("preview_confirm_export_button")
            ) {
                Text("Start Export".localize(appLanguage), fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel".localize(appLanguage), color = Color.Gray)
            }
        }
    )
}

// -----------------------------------------------------------------------------
// EXPORT PROGRESS OVERLAY
// -----------------------------------------------------------------------------
@Composable
private fun ExportProgressOverlay(
    progress: Float,
    status: String,
    appLanguage: String
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xEE06030C)),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.86f)
                .padding(16.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF140D26))
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.size(54.dp),
                    color = MaterialTheme.colorScheme.primary,
                    strokeWidth = 5.dp
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Compiling Reel...".localize(appLanguage),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = status.localize(appLanguage),
                    fontSize = 12.sp,
                    color = Color.LightGray,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(12.dp))
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = Color(0xFF281C44)
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "${(progress * 100).toInt()}%",
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

// -----------------------------------------------------------------------------
// EMPTY STATE
// -----------------------------------------------------------------------------
@Composable
private fun EmptyScenesCard(appLanguage: String) {
    Card(
        modifier = Modifier
            .fillMaxWidth(0.92f)
            .height(200.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF140E24))
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.Movie,
                contentDescription = null,
                tint = Color.DarkGray,
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "No scenes available to preview".localize(appLanguage),
                color = Color.LightGray,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
