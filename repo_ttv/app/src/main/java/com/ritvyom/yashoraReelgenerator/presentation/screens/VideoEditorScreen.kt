package com.ritvyom.yashoraReelgenerator.presentation.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.viewinterop.AndroidView
import android.widget.VideoView
import android.util.Log
import android.net.Uri
import android.content.Context
import android.content.Intent
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import com.ritvyom.yashoraReelgenerator.domain.models.Scene
import com.ritvyom.yashoraReelgenerator.presentation.components.MockInterstitialAdDialog
import com.ritvyom.yashoraReelgenerator.presentation.components.*
import com.ritvyom.yashoraReelgenerator.presentation.utils.EditorConstants
import com.ritvyom.yashoraReelgenerator.presentation.utils.FilterManager
import com.ritvyom.yashoraReelgenerator.presentation.utils.EffectManager
import com.ritvyom.yashoraReelgenerator.presentation.utils.SoundSynth
import com.ritvyom.yashoraReelgenerator.presentation.utils.AudioManager
import com.ritvyom.yashoraReelgenerator.presentation.utils.RenderPipeline
import com.ritvyom.yashoraReelgenerator.presentation.utils.localize
import com.ritvyom.yashoraReelgenerator.presentation.viewmodels.MainViewModel
import com.ritvyom.yashoraReelgenerator.ui.theme.YashoraTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.drawscope.rotate

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun VideoEditorScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit,
    onNavigateToConfig: () -> Unit = {},
    onExportFinished: () -> Unit
) {
    val activeScenes by viewModel.activeScenes.collectAsState()
    val activeProject by viewModel.activeProject.collectAsState()
    val activeRatio by viewModel.selectedAspectRatio.collectAsState()
    val appLanguageState by viewModel.appLanguage.collectAsState()
    val availableSfx by AudioManager.availableSfxList.collectAsState()

    val selectedSceneIndex by viewModel.selectedSceneIndex.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()

    val isExporting by viewModel.isExporting.collectAsState()
    val isExportingMinimized by viewModel.isExportingMinimized.collectAsState()
    val exportProgress by viewModel.exportProgress.collectAsState()
    val exportStatus by viewModel.exportStatus.collectAsState()
    val compiledPreviewPath by viewModel.compiledPreviewPath.collectAsState()

    val isGenerating by viewModel.isGenerating.collectAsState()
    val generationProgress by viewModel.generationProgress.collectAsState()
    val generationStatus by viewModel.generationStatus.collectAsState()

    var showInterstitialAd by remember { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    val context = androidx.compose.ui.platform.LocalContext.current
    var showPermissionExplanationDialog by remember { mutableStateOf(false) }
    var pendingExportAction by remember { mutableStateOf(false) }
    var showExportQualityDialog by remember { mutableStateOf(false) }
    var showCancelExportConfirmDialog by remember { mutableStateOf(false) }
    var showConfirmExportDialog by remember { mutableStateOf(false) }
    var showExportSuccessDialog by remember { mutableStateOf(false) }
    var exportedPathForSuccess by remember { mutableStateOf("") }
    var showSuccessPlayerDialog by remember { mutableStateOf(false) }

    var activePreviewTab by remember { mutableStateOf(0) } // 0 = Canvas, 1 = Composed Video Preview
    var draggedIndex by remember { mutableStateOf<Int?>(null) }
    var dragOffsetX by remember { mutableStateOf(0f) }
    val exportHistory by viewModel.exportHistory.collectAsState()
    val activeProjectId = activeProject?.id ?: 0
    val exportedHistoryItem = remember(exportHistory, activeProjectId) {
        exportHistory.firstOrNull { it.projectId == activeProjectId }
    }
    val exportedPath = remember(exportedHistoryItem, exportedPathForSuccess) {
        if (exportedPathForSuccess.isNotEmpty()) exportedPathForSuccess else (exportedHistoryItem?.filePath ?: "")
    }

    val requiredPermissions = remember {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            listOf(
                android.Manifest.permission.READ_MEDIA_IMAGES,
                android.Manifest.permission.READ_MEDIA_VIDEO,
                android.Manifest.permission.POST_NOTIFICATIONS
            )
        } else {
            listOf(
                android.Manifest.permission.READ_EXTERNAL_STORAGE,
                android.Manifest.permission.WRITE_EXTERNAL_STORAGE
            )
        }
    }

    val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val allGranted = results.values.all { it }
        if (allGranted) {
            if (pendingExportAction) {
                pendingExportAction = false
                showInterstitialAd = true
            }
        } else {
            showPermissionExplanationDialog = true
        }
    }

    val checkAndRequestPermissions = remember {
        {
            val hasAll = requiredPermissions.all { perm ->
                androidx.core.content.ContextCompat.checkSelfPermission(context, perm) == android.content.pm.PackageManager.PERMISSION_GRANTED
            }
            if (hasAll) {
                showInterstitialAd = true
            } else {
                pendingExportAction = true
                permissionLauncher.launch(requiredPermissions.toTypedArray())
            }
        }
    }

    val openAppSettings = remember {
        {
            try {
                val intent = android.content.Intent(
                    android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    android.net.Uri.fromParts("package", context.packageName, null)
                ).apply {
                    addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (e: Exception) {
                Log.e("Permission", "Failed to open settings", e)
            }
        }
    }

    // Editor auxiliary settings
    var showStickerDrawer by remember { mutableStateOf(false) }
    var showMediaImportDrawer by remember { mutableStateOf(false) }
    var showFullScreenPlayer by remember { mutableStateOf(false) }
    var previewDragAmount by remember { mutableStateOf(0f) }
    var activeEditorSectionTab by remember { mutableIntStateOf(0) } // 0 = Subtitles, 1 = Filters, 2 = Font & Text, 3 = Transitions
    var isTimelineExpanded by remember { mutableStateOf(true) }
    var filterSubPanelSelectedTab by remember { mutableStateOf(0) } // 0: Filters, 1: Special FX
    var activeFilterCategory by remember { mutableStateOf("Trending") }
    var activeEffectCategory by remember { mutableStateOf("Basic") }

    var currentPlaybackTimeMs by remember { mutableStateOf(0L) }

    LaunchedEffect(isPlaying, selectedSceneIndex, activeScenes) {
        if (isPlaying) {
            val startTime = System.currentTimeMillis() - currentPlaybackTimeMs
            while (isPlaying) {
                val elapsed = System.currentTimeMillis() - startTime
                val currentMaxMs = (activeScenes.getOrNull(selectedSceneIndex)?.durationSeconds ?: 5) * 1000L
                if (elapsed >= currentMaxMs) {
                    currentPlaybackTimeMs = currentMaxMs
                    break
                } else {
                    currentPlaybackTimeMs = elapsed
                }
                delay(50L)
            }
        }
    }

    LaunchedEffect(selectedSceneIndex) {
        currentPlaybackTimeMs = 0L
    }

    val lastSelectedUriPath = remember { mutableStateOf<String?>(null) }
    var showImportChoiceDialog by remember { mutableStateOf(false) }

    val filePickerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (!uris.isNullOrEmpty()) {
            val importedScenes = mutableListOf<Scene>()
            var addedCount = 0
            for (uri in uris) {
                val pickedPath = uri.toString()
                val mimeType = context.contentResolver.getType(uri)
                val isVideo = (mimeType != null && mimeType.startsWith("video", ignoreCase = true)) ||
                              pickedPath.contains("video", ignoreCase = true) ||
                              pickedPath.contains(".mp4", ignoreCase = true) ||
                              pickedPath.contains(".3gp", ignoreCase = true) ||
                              pickedPath.contains(".3gpp", ignoreCase = true) ||
                              pickedPath.contains(".mkv", ignoreCase = true) ||
                              pickedPath.contains(".webm", ignoreCase = true) ||
                              pickedPath.contains(".mov", ignoreCase = true) ||
                              pickedPath.contains(".avi", ignoreCase = true)

                var durationSeconds = 4 // Default for photo is 4 seconds
                if (isVideo) {
                    val retriever = android.media.MediaMetadataRetriever()
                    try {
                        retriever.setDataSource(context, uri)
                        val timeStr = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)
                        if (!timeStr.isNullOrEmpty()) {
                            val durationMs = timeStr.toLong()
                            durationSeconds = (durationMs / 1000).toInt().coerceAtLeast(1)
                        }
                    } catch (e: Exception) {
                        Log.e("Import", "Failed to retrieve video duration", e)
                        durationSeconds = 5 // Fallback video duration
                    } finally {
                        try { retriever.release() } catch (e: Exception) {}
                    }
                }

                val newScene = Scene(
                    sceneNumber = activeScenes.size + importedScenes.size + 1,
                    narrationText = "Imported " + (if (isVideo) "video" else "photo") + " clip",
                    visualPrompt = "Custom User Imported Media Content",
                    subtitle = "Custom Clip".localize(appLanguageState),
                    mediaPath = pickedPath,
                    mediaType = if (isVideo) "VIDEO" else "IMAGE",
                    durationSeconds = durationSeconds
                )
                importedScenes.add(newScene)
                addedCount++
            }

            viewModel.appendScenes(importedScenes)
            viewModel.speakText("Imported $addedCount clips to your timeline".localize(appLanguageState))
            showMediaImportDrawer = false
        }
    }

    // Simulated movie rendering loop player
    LaunchedEffect(isPlaying, selectedSceneIndex, activeScenes) {
        if (isPlaying && activeScenes.isNotEmpty() && selectedSceneIndex in activeScenes.indices) {
            val duration = activeScenes[selectedSceneIndex].durationSeconds * 1000L
            delay(duration)
            if (isPlaying && selectedSceneIndex in activeScenes.indices) {
                // Next Scene
                if (selectedSceneIndex < activeScenes.lastIndex && (selectedSceneIndex + 1) in activeScenes.indices) {
                    viewModel.selectedSceneIndex.value = selectedSceneIndex + 1
                    // Synthesizes speaking voice during playback
                    val nextScene = activeScenes[selectedSceneIndex + 1]
                    viewModel.speakText(if (nextScene.narrationText.isNotEmpty()) nextScene.narrationText else nextScene.subtitle)
                } else {
                    viewModel.selectedSceneIndex.value = 0
                    viewModel.isPlaying.value = false
                    viewModel.stopSpeak()
                }
            }
        }
    }

    // Play synthesized sound effects & layered asset effects dynamically on scene changes/playback
    LaunchedEffect(selectedSceneIndex, isPlaying) {
        if (isPlaying) {
            if (selectedSceneIndex in activeScenes.indices) {
                val sfx = activeScenes[selectedSceneIndex].sfxName
                if (!sfx.isNullOrEmpty() && sfx != "None") {
                    SoundSynth.playSfx(sfx)
                }
                
                // Play custom layered asset SFX
                val assetSfx = activeScenes[selectedSceneIndex].layeredSfxName
                if (!assetSfx.isNullOrEmpty() && assetSfx != "None") {
                    AudioManager.playSfx(
                        context = context,
                        sfxName = assetSfx,
                        volume = activeScenes[selectedSceneIndex].layeredSfxVolume,
                        loop = activeScenes[selectedSceneIndex].layeredSfxLoop,
                        trackKey = "LayeredSfxTrack"
                    )
                } else {
                    AudioManager.stopSfx("LayeredSfxTrack")
                }
            }
        } else {
            AudioManager.stopSfx("LayeredSfxTrack")
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            AudioManager.stopAll()
        }
    }

    val currentScene: Scene? = if (selectedSceneIndex in activeScenes.indices) activeScenes[selectedSceneIndex] else null

    YashoraTheme(themeName = "Dark") {
        Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(activeProject?.title ?: "Visual Timeline Editor".localize(appLanguageState), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        val pubStyle = activeProject?.publishingStyle ?: "TikTok / Instagram Reels"
                        val visStyle = activeProject?.videoStyle ?: "Cinematic"
                        Text(
                            text = "$pubStyle • $visStyle".localize(appLanguageState),
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = Icons.AutoMirrored.Default.ArrowBack, contentDescription = "Back".localize(appLanguageState), tint = MaterialTheme.colorScheme.secondary)
                    }
                },
                actions = {
                    IconButton(
                        onClick = onNavigateToConfig
                    ) {
                        Icon(
                            imageVector = androidx.compose.material.icons.Icons.Default.Tune,
                            contentDescription = "Edit Script & Config / Regenerate".localize(appLanguageState),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    IconButton(
                        onClick = {
                            val isOnline = com.ritvyom.yashoraReelgenerator.presentation.utils.NetworkUtils.isInternetAvailable(context)
                            if (isOnline) {
                                viewModel.regenerateCurrentProjectVisuals()
                            } else {
                                android.widget.Toast.makeText(
                                    context,
                                    "No Internet Connection".localize(appLanguageState),
                                    android.widget.Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh Backdrops".localize(appLanguageState),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Button(
                        onClick = {
                            showExportQualityDialog = true
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(imageVector = Icons.Default.SaveAlt, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Export".localize(appLanguageState), fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Adapt Box sizing depending on aspect ratio selection
            val containerModifier = when(activeRatio) {
                "16:9" -> Modifier.fillMaxWidth().height(150.dp)
                "21:9" -> Modifier.fillMaxWidth().height(110.dp)
                "1:1" -> Modifier.size(170.dp)
                "4:5" -> Modifier.width(135.dp).height(170.dp)
                else -> Modifier.width(110.dp).height(200.dp) // 9:16, 3:4 portrait
            }

            // Segmented Toggles for Canvas vs. Composed Preview
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF030107))
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val canvasSelected = activePreviewTab == 0
                val composedSelected = activePreviewTab == 1
                
                Button(
                    onClick = { activePreviewTab = 0 },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (canvasSelected) MaterialTheme.colorScheme.primary else Color(0xFF151120),
                        contentColor = if (canvasSelected) MaterialTheme.colorScheme.onPrimary else Color.Gray
                    ),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.weight(1f).height(36.dp),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Icon(imageVector = Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Scene Canvas".localize(appLanguageState), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = { activePreviewTab = 1 },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (composedSelected) MaterialTheme.colorScheme.secondary else Color(0xFF151120),
                        contentColor = if (composedSelected) MaterialTheme.colorScheme.onSecondary else Color.Gray
                    ),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.weight(1f).height(36.dp),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Icon(imageVector = Icons.Default.MovieCreation, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (exportedPath.isNotEmpty()) "Composed Preview 🎬" else "Composed Preview (Draft)".localize(appLanguageState),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            if (activePreviewTab == 0) {
                // Part 1: High Fidelity Cinematic Canvas Video Player
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(210.dp)
                        .background(Color(0xFF030107))
                        .padding(8.dp),
                    contentAlignment = Alignment.Center
                ) {

                Box(
                    modifier = containerModifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF151120))
                        .border(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                        .draggable(
                            orientation = androidx.compose.foundation.gestures.Orientation.Horizontal,
                            state = androidx.compose.foundation.gestures.rememberDraggableState { delta ->
                                previewDragAmount += delta
                            },
                            onDragStopped = { velocity ->
                                if (previewDragAmount < -100f) { // Swipe Left -> Next
                                    if (selectedSceneIndex < activeScenes.size - 1) {
                                        viewModel.selectedSceneIndex.value = selectedSceneIndex + 1
                                        if (isPlaying) {
                                            viewModel.stopSpeak()
                                            activeScenes.getOrNull(selectedSceneIndex + 1)?.let {
                                                viewModel.speakText(if (it.narrationText.isNotEmpty()) it.narrationText else it.subtitle)
                                            }
                                        }
                                    }
                                } else if (previewDragAmount > 100f) { // Swipe Right -> Prev
                                    if (selectedSceneIndex > 0) {
                                        viewModel.selectedSceneIndex.value = selectedSceneIndex - 1
                                        if (isPlaying) {
                                            viewModel.stopSpeak()
                                            activeScenes.getOrNull(selectedSceneIndex - 1)?.let {
                                                viewModel.speakText(if (it.narrationText.isNotEmpty()) it.narrationText else it.subtitle)
                                            }
                                        }
                                    }
                                }
                                previewDragAmount = 0f
                            }
                        )
                        .drawBehind {
                            // Dark background backup
                            drawRect(color = Color(0xFF1E1E2C))
                        },
                    contentAlignment = Alignment.Center
                ) {
                    // Frame Background details
                    currentScene?.let { sc ->
                        val path = sc.mediaPath
                        if (!path.isNullOrEmpty()) {
                            val isVideo = path.contains(".mp4") || path.contains("video")
                            
                            val baseModifier = Modifier.fillMaxSize().graphicsLayer {
                                rotationZ = sc.rotationDegrees.toFloat()
                                scaleX = if (sc.isFlippedHorizontal) -1f else 1f
                                scaleY = if (sc.isFlippedVertical) -1f else 1f
                            }
                            
                            val transformModifier = when (sc.maskShape) {
                                "Circle" -> baseModifier.clip(androidx.compose.foundation.shape.CircleShape)
                                "Rectangle" -> baseModifier.clip(RoundedCornerShape(12.dp))
                                else -> baseModifier
                            }

                            val fxModifier = RenderPipeline.applyEffects(
                                effectName = sc.effectName,
                                intensity = sc.effectIntensity,
                                isPlaying = isPlaying,
                                modifier = transformModifier,
                                scene = sc
                            )

                            if (isVideo) {
                                androidx.compose.ui.viewinterop.AndroidView(
                                    factory = { ctx ->
                                        android.widget.VideoView(ctx).apply {
                                            setOnPreparedListener { mp ->
                                                mp.isLooping = true
                                                try {
                                                    val finalVolume = 0.1f * sc.volume
                                                    mp.setVolume(finalVolume, finalVolume)
                                                } catch (e: Exception) {}
                                            }
                                            setOnErrorListener { mp, what, extra ->
                                                Log.e("EditorScreen", "VideoView error what=$what extra=$extra")
                                                true // prevents default system dialog popup and keeps app crash-free
                                            }
                                        }
                                    },
                                    update = { videoView ->
                                        val safePath = if (path.startsWith("http://")) path.replace("http://", "https://") else path
                                        if (videoView.tag != safePath) {
                                            videoView.tag = safePath
                                            try {
                                                if (safePath.startsWith("/") || safePath.startsWith("file://")) {
                                                    val actualPath = safePath.replace("file://", "")
                                                    videoView.setVideoURI(android.net.Uri.fromFile(java.io.File(actualPath)))
                                                } else {
                                                    videoView.setVideoPath(safePath)
                                                }
                                                if (isPlaying) {
                                                    try { videoView.start() } catch (e: Exception) {}
                                                } else {
                                                    try { videoView.pause() } catch (e: Exception) {}
                                                }
                                            } catch (e: Exception) {
                                                Log.e("EditorScreen", "Failed launching VideoView stream", e)
                                            }
                                        } else {
                                            try {
                                                if (isPlaying) {
                                                    if (!videoView.isPlaying) {
                                                        videoView.start()
                                                    }
                                                } else {
                                                    if (videoView.isPlaying) {
                                                        videoView.pause()
                                                    }
                                                }
                                            } catch (e: Exception) {
                                                Log.e("EditorScreen", "Error video controls update", e)
                                            }
                                        }
                                    },
                                    modifier = fxModifier
                                )
                            } else {
                                // Render generated UHD image
                                val composeMatrix = androidx.compose.ui.graphics.ColorMatrix().apply {
                                    setToSaturation(sc.saturationValue)
                                }
                                coil.compose.AsyncImage(
                                    model = path,
                                    contentDescription = sc.visualPrompt,
                                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                    colorFilter = androidx.compose.ui.graphics.ColorFilter.colorMatrix(composeMatrix),
                                    modifier = fxModifier
                                )
                            }
                            
                            // Color grading overlay mapping (Backward compatibility + custom categorized filters + intensity support)
                            val catFilter = FilterManager.ALL_FILTERS.firstOrNull { it.name == sc.filterCategory }
                            val tintColor = catFilter?.tintOverlayColor ?: EditorConstants.FILTERS.getOrNull(sc.selectedFilterIndex)?.tintColor ?: Color.Transparent
                            val intensityFactor = sc.filterIntensity / 100f
                            if (tintColor != Color.Transparent && tintColor.alpha > 0f) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(tintColor.copy(alpha = tintColor.alpha * intensityFactor))
                                )
                            }

                            // Dynamic Brightness, Contrast & Warmth Color Grading simulation overlays (for perfect realtimer feedback!)
                            if (sc.brightnessValue != 0f || sc.warmthValue != 0f) {
                                val bColor = if (sc.brightnessValue > 0f) Color.White else Color.Black
                                val bAlpha = (kotlin.math.abs(sc.brightnessValue) / 100f) * 0.45f
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(bColor.copy(alpha = bAlpha))
                                )
                                
                                val wColor = if (sc.warmthValue > 0f) Color(0xFFFFCC00) else Color(0xFF00CCFF)
                                val wAlpha = (kotlin.math.abs(sc.warmthValue) / 100f) * 0.25f
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(wColor.copy(alpha = wAlpha))
                                )
                            }
                            
                            // Text backing gradient for maximum readability
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.verticalGradient(
                                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.35f), Color.Black.copy(alpha = 0.8f))
                                        )
                                    )
                            )
                        } else {
                            // Standard placeholder Column when mediaPath is missing
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                                modifier = Modifier.padding(10.dp)
                            ) {
                                Text(
                                    text = "SCENE ${sc.sceneNumber}",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Black,
                                    color = MaterialTheme.colorScheme.primary,
                                    letterSpacing = 2.sp
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    htmlTextSimulate(EditorConstants.VIDEO_STYLES.firstOrNull { it.name == activeProject?.videoStyle }?.icon ?: "🎬"),
                                    fontSize = 36.sp
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = "[ ${sc.selectedFilterName} Filter ]",
                                    fontSize = 8.sp,
                                    color = Color.Gray,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        // Text overlays / subtitles placed on top of player frame
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(12.dp),
                            contentAlignment = Alignment.BottomCenter
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                val curFontFamily = when (sc.captionFont) {
                                    "Sans-Serif" -> androidx.compose.ui.text.font.FontFamily.SansSerif
                                    "Serif" -> androidx.compose.ui.text.font.FontFamily.Serif
                                    "Monospace" -> androidx.compose.ui.text.font.FontFamily.Monospace
                                    else -> androidx.compose.ui.text.font.FontFamily.Default
                                }
                                val curFontWeight = when (sc.captionFont) {
                                    "Display Bold" -> FontWeight.Black
                                    "TikTok Style" -> FontWeight.ExtraBold
                                    "Insta Premium" -> FontWeight.SemiBold
                                    else -> FontWeight.Bold
                                }
                                val curFontStyle = if (sc.captionFont == "Insta Premium") {
                                    androidx.compose.ui.text.font.FontStyle.Italic
                                } else {
                                    androidx.compose.ui.text.font.FontStyle.Normal
                                }

                                sc.textOverlay?.let { over ->
                                    Text(
                                        text = over.uppercase(),
                                        fontWeight = curFontWeight,
                                        fontFamily = curFontFamily,
                                        fontStyle = curFontStyle,
                                        fontSize = 11.sp,
                                        color = parseHexColor(sc.overlayColor),
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier
                                            .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(4.dp))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                }

                                Text(
                                    text = sc.subtitle,
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = curFontWeight,
                                    fontFamily = curFontFamily,
                                    fontStyle = curFontStyle,
                                    textAlign = TextAlign.Center,
                                    lineHeight = 14.sp,
                                    modifier = Modifier
                                        .background(Color.Black.copy(alpha = 0.75f), RoundedCornerShape(4.dp))
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    } ?: Text("Empty Track Buffer".localize(appLanguageState), color = Color.Gray)
                }

                // Small Player Controls overlaying inside panel
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp),
                    contentAlignment = Alignment.BottomEnd
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FloatingActionButton(
                            onClick = { showFullScreenPlayer = true },
                            containerColor = Color.Black.copy(alpha = 0.65f),
                            contentColor = Color.White,
                            modifier = Modifier.size(36.dp),
                            shape = CircleShape
                        ) {
                            Icon(
                                imageVector = Icons.Default.Fullscreen,
                                contentDescription = "Full Screen Preview".localize(appLanguageState),
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        FloatingActionButton(
                            onClick = {
                                if (isPlaying) {
                                    viewModel.isPlaying.value = false
                                    viewModel.stopSpeak()
                                } else {
                                    viewModel.isPlaying.value = true
                                    currentScene?.let { viewModel.speakText(if (it.narrationText.isNotEmpty()) it.narrationText else it.subtitle) }
                                }
                            },
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(42.dp),
                            shape = CircleShape
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = "Play".localize(appLanguageState)
                            )
                        }
                    }
                }
            }
        } else {
            // Responsive Composed Video Preview Container
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(210.dp)
                    .background(Color(0xFF030107))
                    .padding(8.dp),
                contentAlignment = Alignment.Center
            ) {
                if (exportedPath.isNotEmpty()) {
                    val videoUri = getPlayableUri(context, exportedPath)
                    if (videoUri != null) {
                        ComposedVideoPlayer(
                            videoUri = videoUri,
                            appLanguage = appLanguageState,
                            modifier = containerModifier
                        )
                    } else {
                        ComposedVideoPlaceholder(
                            appLanguage = appLanguageState,
                            onExportClick = { showExportQualityDialog = true },
                            modifier = containerModifier
                        )
                    }
                } else {
                    ComposedVideoPlaceholder(
                        appLanguage = appLanguageState,
                        onExportClick = { showExportQualityDialog = true },
                        modifier = containerModifier
                    )
                }
            }
        }

            // Project Timeline Seek Bar for sliding/seeking through scenes and clip timestamps in real-time!
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                // 1. Real-time Sub-Scene Micro Scrubber Progress Track
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val durationMs = (currentScene?.durationSeconds ?: 5) * 1000f
                    val currentSecRaw = currentPlaybackTimeMs / 1000f
                    val durationSecRaw = durationMs / 1000f
                    Text(
                        text = String.format("%.2fs", currentSecRaw),
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                    )
                    Slider(
                        value = currentPlaybackTimeMs.toFloat().coerceIn(0f, durationMs),
                        onValueChange = { newValMs ->
                            currentPlaybackTimeMs = newValMs.toLong()
                        },
                        valueRange = 0f..durationMs,
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.secondary,
                            activeTrackColor = MaterialTheme.colorScheme.secondary,
                            inactiveTrackColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                        ),
                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                    )
                    Text(
                        text = String.format("%.1fs", durationSecRaw),
                        color = Color.LightGray,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                    )
                }

                Spacer(modifier = Modifier.height(2.dp))

                // 2. Macro Clip index selector (If there are multiple scenes)
                if (activeScenes.size > 1) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Scene".localize(appLanguageState),
                            color = Color.Gray,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Slider(
                            value = selectedSceneIndex.toFloat(),
                            onValueChange = { indexFloat ->
                                val targetIndex = indexFloat.toInt().coerceIn(activeScenes.indices)
                                if (targetIndex != selectedSceneIndex) {
                                    viewModel.selectedSceneIndex.value = targetIndex
                                    if (isPlaying) {
                                        viewModel.stopSpeak()
                                        activeScenes.getOrNull(targetIndex)?.let {
                                            viewModel.speakText(if (it.narrationText.isNotEmpty()) it.narrationText else it.subtitle)
                                        }
                                    }
                                }
                            },
                            valueRange = 0f..(activeScenes.size - 1).toFloat(),
                            steps = if (activeScenes.size > 2) activeScenes.size - 2 else 0,
                            colors = SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.primary,
                                activeTrackColor = MaterialTheme.colorScheme.primary,
                                inactiveTrackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                            ),
                            modifier = Modifier.weight(1f).padding(horizontal = 10.dp)
                        )
                        Text(
                            text = "Clip ${selectedSceneIndex + 1}/${activeScenes.size}",
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(scrollState)
            ) {
                // CapCut & YouCut Inspired Quick-Action Control Strip
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 2.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
                ),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Action 1: Play / Pause
                    IconButton(
                        onClick = {
                            if (isPlaying) {
                                viewModel.isPlaying.value = false
                                viewModel.stopSpeak()
                            } else {
                                viewModel.isPlaying.value = true
                                currentScene?.let { viewModel.speakText(if (it.narrationText.isNotEmpty()) it.narrationText else it.subtitle) }
                            }
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = "Toggle Play".localize(appLanguageState),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    VerticalDivider(modifier = Modifier.height(16.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))

                    // Action 2: Split
                    IconButton(
                        onClick = {
                            viewModel.splitScene(selectedSceneIndex, "[Split] New Segment")
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCut,
                            contentDescription = "Split Clip".localize(appLanguageState),
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    // Action 3: Delete Scene
                    IconButton(
                        onClick = {
                            viewModel.deleteScene(selectedSceneIndex)
                        },
                        enabled = activeScenes.size > 1,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete Scene".localize(appLanguageState),
                            tint = if (activeScenes.size > 1) MaterialTheme.colorScheme.error else Color.Gray.copy(alpha = 0.5f),
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    VerticalDivider(modifier = Modifier.height(16.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))

                    // Action 4: Quick Speed Toggle
                    IconButton(
                        onClick = {
                            currentScene?.let { sc ->
                                val nextSpeed = when (sc.speedMultiplier) {
                                    1.0f -> 2.0f
                                    2.0f -> 0.5f
                                    else -> 1.0f
                                }
                                viewModel.editSceneSpeedMultiplier(selectedSceneIndex, nextSpeed)
                            }
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "${currentScene?.speedMultiplier ?: 1.0f}x",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }

                    // Action 5: Add Captions Quick Button
                    IconButton(
                        onClick = { activeEditorSectionTab = 0 },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.TextFields,
                            contentDescription = "Add Captions".localize(appLanguageState),
                            tint = if (activeEditorSectionTab == 0) MaterialTheme.colorScheme.primary else Color.Gray,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    // Action 6: TTS Mic Quick Button
                    IconButton(
                        onClick = { activeEditorSectionTab = 5 },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = "Audio Voiceover".localize(appLanguageState),
                            tint = if (activeEditorSectionTab == 5) MaterialTheme.colorScheme.primary else Color.Gray,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    // Action 7: Transition Trigger Button
                    IconButton(
                        onClick = { activeEditorSectionTab = 3 },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Movie,
                            contentDescription = "Transitions".localize(appLanguageState),
                            tint = if (activeEditorSectionTab == 3) MaterialTheme.colorScheme.primary else Color.Gray,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    // Action 8: Script-to-Video Timeline Map & Reorder Button
                    IconButton(
                        onClick = { activeEditorSectionTab = 4 },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Sort,
                            contentDescription = "Timeline Editor".localize(appLanguageState),
                            tint = if (activeEditorSectionTab == 4) MaterialTheme.colorScheme.primary else Color.Gray,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    // Action 9: Media Swap Gallery Button
                    IconButton(
                        onClick = { activeEditorSectionTab = 6 },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Collections,
                            contentDescription = "Media Swap Gallery".localize(appLanguageState),
                            tint = if (activeEditorSectionTab == 6) MaterialTheme.colorScheme.primary else Color.Gray,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            // Part 2: Multitrack horizontal Storyboard Timeline track (Horizontal scroll)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                shape = RoundedCornerShape(24.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f)
                )
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text("Chronological Video Track Timeline".localize(appLanguageState), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            IconButton(
                                onClick = { isTimelineExpanded = !isTimelineExpanded },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = if (isTimelineExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                    contentDescription = "Toggle Timeline".localize(appLanguageState),
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            val isUndoEnabled by viewModel.canUndo.collectAsState()
                            val isRedoEnabled by viewModel.canRedo.collectAsState()
                            
                            IconButton(
                                onClick = { viewModel.undo() },
                                enabled = isUndoEnabled,
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Undo,
                                    contentDescription = "Undo".localize(appLanguageState),
                                    tint = if (isUndoEnabled) MaterialTheme.colorScheme.primary else Color.Gray,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            IconButton(
                                onClick = { viewModel.redo() },
                                enabled = isRedoEnabled,
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Redo,
                                    contentDescription = "Redo".localize(appLanguageState),
                                    tint = if (isRedoEnabled) MaterialTheme.colorScheme.primary else Color.Gray,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = "Long-press & drag any clip to reorder them instantly".localize(appLanguageState),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                        )
                    }

                    if (isTimelineExpanded) {
                        Spacer(modifier = Modifier.height(8.dp))

                        LazyRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Quick merge/insert visual draft item
                            item {
                                Box(
                                    modifier = Modifier
                                        .size(65.dp, 92.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                                        .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                                        .clickable { showMediaImportDrawer = true },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                        Text("Add Clip".localize(appLanguageState), fontSize = 9.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            }

                            itemsIndexed(activeScenes) { index, scene ->
                                val isSelected = index == selectedSceneIndex
                                var accumulatedScale by remember(index) { mutableStateOf(1.0f) }
                                val widthSize = 120.dp * (scene.durationSeconds / 4f).coerceIn(0.7f, 3.5f)
                                val isDraggingThis = draggedIndex == index
                                val density = LocalDensity.current

                                Column(
                                    modifier = Modifier
                                        .width(widthSize)
                                        .padding(vertical = 2.dp)
                                        .graphicsLayer {
                                            if (isDraggingThis) {
                                                translationX = dragOffsetX
                                                scaleX = 1.05f
                                                scaleY = 1.05f
                                                alpha = 0.85f
                                                shadowElevation = 8.dp.toPx()
                                            }
                                        }
                                        .pointerInput(index) {
                                            detectDragGesturesAfterLongPress(
                                                onDragStart = {
                                                    draggedIndex = index
                                                    dragOffsetX = 0f
                                                },
                                                onDrag = { change, dragAmount ->
                                                    change.consume()
                                                    dragOffsetX += dragAmount.x
                                                    
                                                    val currentDragIndex = draggedIndex
                                                    if (currentDragIndex != null && currentDragIndex == index) {
                                                        val currentWidthPx = with(density) { widthSize.toPx() }
                                                        
                                                        if (dragOffsetX > currentWidthPx * 0.6f && index < activeScenes.lastIndex) {
                                                            val nextWidthSize = 120.dp * (activeScenes[index + 1].durationSeconds / 4f).coerceIn(0.7f, 3.5f)
                                                            val nextWidthPx = with(density) { nextWidthSize.toPx() }
                                                            viewModel.reorderScenes(index, index + 1)
                                                            draggedIndex = index + 1
                                                            dragOffsetX -= nextWidthPx
                                                        }
                                                        else if (dragOffsetX < -currentWidthPx * 0.6f && index > 0) {
                                                            val prevWidthSize = 120.dp * (activeScenes[index - 1].durationSeconds / 4f).coerceIn(0.7f, 3.5f)
                                                            val prevWidthPx = with(density) { prevWidthSize.toPx() }
                                                            viewModel.reorderScenes(index, index - 1)
                                                            draggedIndex = index - 1
                                                            dragOffsetX += prevWidthPx
                                                        }
                                                    }
                                                },
                                                onDragEnd = {
                                                    draggedIndex = null
                                                    dragOffsetX = 0f
                                                },
                                                onDragCancel = {
                                                    draggedIndex = null
                                                    dragOffsetX = 0f
                                                }
                                            )
                                        }
                                ) {
                                    // Track 1: Video/Media Stream Track block
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(56.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(
                                                if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                            )
                                            .border(
                                                2.dp,
                                                if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                                RoundedCornerShape(8.dp)
                                            )
                                            .clickable {
                                                viewModel.selectedSceneIndex.value = index
                                                viewModel.speakText(if (scene.narrationText.isNotEmpty()) scene.narrationText else scene.subtitle)
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        // Background image thumbnail for a gorgeous real video editor look!
                                        if (!scene.mediaPath.isNullOrEmpty()) {
                                            coil.compose.AsyncImage(
                                                model = scene.mediaPath,
                                                contentDescription = null,
                                                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                                modifier = Modifier.fillMaxSize(),
                                                alpha = 0.5f
                                            )
                                            // Soft black veil to preserve text contrast
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .background(Color.Black.copy(alpha = 0.45f))
                                            )
                                        }
                                        Column(
                                            modifier = Modifier.padding(5.dp),
                                            verticalArrangement = Arrangement.SpaceBetween,
                                            horizontalAlignment = Alignment.Start
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text("#${scene.sceneNumber}", fontSize = 9.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
                                                Box(
                                                    modifier = Modifier
                                                        .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(4.dp))
                                                        .padding(horizontal = 4.dp, vertical = 2.dp)
                                                ) {
                                                    Text("${scene.durationSeconds}s", fontSize = 8.sp, color = Color.White)
                                                }
                                            }
                                            Text(
                                                text = scene.subtitle,
                                                fontSize = 9.5.sp,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis,
                                                lineHeight = 11.sp
                                            )
                                            // Transitions marker
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = scene.selectedFilterName,
                                                    fontSize = 8.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                Text(
                                                    text = "⤇ ${scene.transitionType}",
                                                    fontSize = 8.sp,
                                                    color = MaterialTheme.colorScheme.primary,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(4.dp))

                                    // Track 2: Subtitles/Texts overlay strip
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(14.dp)
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(
                                                if (scene.subtitle.isNotEmpty()) Color(0xFFFFC107).copy(alpha = 0.22f)
                                                else Color.DarkGray.copy(alpha = 0.1f)
                                            )
                                            .border(
                                                0.5.dp,
                                                if (scene.subtitle.isNotEmpty()) Color(0xFFFFC107).copy(alpha = 0.5f)
                                                else Color.Transparent,
                                                RoundedCornerShape(4.dp)
                                            )
                                            .padding(horizontal = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.TextFields,
                                            contentDescription = null,
                                            tint = if (scene.subtitle.isNotEmpty()) Color(0xFFFFC107) else Color.Gray,
                                            modifier = Modifier.size(8.dp)
                                        )
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Text(
                                            text = if (scene.subtitle.isNotEmpty()) scene.subtitle else "No Captions".localize(appLanguageState),
                                            fontSize = 7.sp,
                                            color = if (scene.subtitle.isNotEmpty()) Color(0xFFFFC107) else Color.Gray,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(4.dp))

                                    // Track 3: Audio (Narration/Voice/Synthesizer and Bg Music) wave representation
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(14.dp)
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(
                                                if (scene.narrationText.isNotEmpty()) Color(0xFF00E676).copy(alpha = 0.15f)
                                                else Color.DarkGray.copy(alpha = 0.1f)
                                            )
                                            .border(
                                                0.5.dp,
                                                if (scene.narrationText.isNotEmpty()) Color(0xFF00E676).copy(alpha = 0.4f)
                                                else Color.Transparent,
                                                RoundedCornerShape(4.dp)
                                            ),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(1.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Mic,
                                            contentDescription = null,
                                            tint = if (scene.narrationText.isNotEmpty()) Color(0xFF00E676) else Color.Gray,
                                            modifier = Modifier.size(8.dp).padding(start = 1.dp)
                                        )
                                        Spacer(modifier = Modifier.width(1.dp))
                                        
                                        val heights = if (scene.narrationText.isNotEmpty()) {
                                            listOf(4.dp, 6.dp, 10.dp, 5.dp, 8.dp, 11.dp, 6.dp, 9.dp, 4.dp, 7.dp, 10.dp, 5.dp)
                                        } else {
                                            listOf(2.dp, 3.dp, 2.dp, 3.dp, 2.dp, 3.dp, 2.dp, 3.dp, 2.dp, 3.dp, 2.dp, 3.dp)
                                        }
                                        
                                        heights.forEach { h ->
                                            Box(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .height(h)
                                                    .background(
                                                        if (scene.narrationText.isNotEmpty()) Color(0xFF00E676).copy(alpha = 0.8f)
                                                        else Color.Gray.copy(alpha = 0.4f),
                                                        RoundedCornerShape(1.dp)
                                                    )
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        // Collapsed Super Compact Scene Row (Utilizing whitespace, maximizing space efficiency!)
                        LazyRow(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                                        .clickable { showMediaImportDrawer = true }
                                        .padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(12.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Add Clip".localize(appLanguageState), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            }
                            
                            itemsIndexed(activeScenes) { index, scene ->
                                val isSelected = index == selectedSceneIndex
                                val isDraggingThis = draggedIndex == index
                                val density = LocalDensity.current
                                val widthSize = 85.dp

                                Box(
                                    modifier = Modifier
                                        .width(widthSize)
                                        .graphicsLayer {
                                            if (isDraggingThis) {
                                                translationX = dragOffsetX
                                                scaleX = 1.08f
                                                scaleY = 1.08f
                                                alpha = 0.85f
                                                shadowElevation = 6.dp.toPx()
                                            }
                                        }
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                                        .border(1.5.dp, if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent, RoundedCornerShape(8.dp))
                                        .clickable {
                                            viewModel.selectedSceneIndex.value = index
                                            viewModel.speakText(if (scene.narrationText.isNotEmpty()) scene.narrationText else scene.subtitle)
                                        }
                                        .pointerInput(index) {
                                            detectDragGesturesAfterLongPress(
                                                onDragStart = {
                                                    draggedIndex = index
                                                    dragOffsetX = 0f
                                                },
                                                onDrag = { change, dragAmount ->
                                                    change.consume()
                                                    dragOffsetX += dragAmount.x
                                                    
                                                    val currentDragIndex = draggedIndex
                                                    if (currentDragIndex != null && currentDragIndex == index) {
                                                        val currentWidthPx = with(density) { widthSize.toPx() }
                                                        
                                                        if (dragOffsetX > currentWidthPx * 0.6f && index < activeScenes.lastIndex) {
                                                            viewModel.reorderScenes(index, index + 1)
                                                            draggedIndex = index + 1
                                                            dragOffsetX -= currentWidthPx
                                                        }
                                                        else if (dragOffsetX < -currentWidthPx * 0.6f && index > 0) {
                                                            viewModel.reorderScenes(index, index - 1)
                                                            draggedIndex = index - 1
                                                            dragOffsetX += currentWidthPx
                                                        }
                                                    }
                                                },
                                                onDragEnd = {
                                                    draggedIndex = null
                                                    dragOffsetX = 0f
                                                },
                                                onDragCancel = {
                                                    draggedIndex = null
                                                    dragOffsetX = 0f
                                                }
                                            )
                                        }
                                        .padding(vertical = 6.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "Clip ${index + 1}",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else Color.LightGray
                                    )
                                }
                            }
                        }
                    }

                }
            }

            // Part 3: Mid Tab Editor Tools panel (Subtitles | Filters | Texts | Transitions)
            currentScene?.let { sc ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp)
                ) {
                    Spacer(modifier = Modifier.height(4.dp))

                    when (activeEditorSectionTab) {
                        4 -> {
                            Text("🗺️ " + "Script-to-Video Timeline Map".localize(appLanguageState), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            Text("Preview how the script paragraphs map to visuals and reorder them:".localize(appLanguageState), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        3 -> {
                            Text("🎬 " + "Scene Transition Settings".localize(appLanguageState), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            Text("Choose how this scene fades or slides into the next segment:".localize(appLanguageState), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        5 -> {
                            Text("🎙️ " + "Voiceover & Audio Narration".localize(appLanguageState), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            Text("Configure text-to-speech settings and narration script for this scene:".localize(appLanguageState), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        6 -> {
                            Text("🖼️ " + "Media Swap Gallery".localize(appLanguageState), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            Text("Swap out the current image or video clip for alternative suggestions:".localize(appLanguageState), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        else -> {
                            Text("✍️ " + "Caption & Storyboard Subtitle Designer".localize(appLanguageState), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            Text("Modify scene subtitles, voice text, and display styles below:".localize(appLanguageState), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                    ) {
                        when (activeEditorSectionTab) {
                            4 -> {
                                TimelineMappingAndReorderPanel(
                                    viewModel = viewModel,
                                    appLanguageState = appLanguageState,
                                    activeScenes = activeScenes,
                                    selectedSceneIndex = selectedSceneIndex
                                )
                            }
                            3 -> {
                                TransitionSelectionPanel(
                                    viewModel = viewModel,
                                    selectedSceneIndex = selectedSceneIndex,
                                    currentScene = sc,
                                    appLanguageState = appLanguageState
                                )
                            }
                            5 -> {
                                VoiceoverPanel(
                                    viewModel = viewModel,
                                    selectedSceneIndex = selectedSceneIndex,
                                    currentScene = sc,
                                    appLanguageState = appLanguageState
                                )
                            }
                            6 -> {
                                MediaSwapGalleryPanel(
                                    viewModel = viewModel,
                                    selectedSceneIndex = selectedSceneIndex,
                                    currentScene = sc,
                                    aspectRatio = activeRatio,
                                    appLanguageState = appLanguageState
                                )
                            }
                            else -> {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                ) {
                                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                        Text("Edit Subtitle Text".localize(appLanguageState), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        OutlinedTextField(
                                            value = sc.subtitle,
                                            onValueChange = { viewModel.editSceneSubtitle(selectedSceneIndex, it) },
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(10.dp)
                                        )

                                        Spacer(modifier = Modifier.height(4.dp))

                                        // Quick-Access Subtitle Style Presets utilizing whitespace and maximizing screen efficiency!
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            val quickPresets = listOf(
                                                Triple("✨ Gold Style", "#FFD700", "#99000000"),
                                                Triple("🔥 TikTok style", "#FFFF00", "#E6111111"),
                                                Triple("💻 Cyber Punk", "#00F0FF", "#00000000"),
                                                Triple("🌸 Neon Pink", "#FF007F", "#4D000000")
                                            )
                                            quickPresets.forEach { item ->
                                                Box(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(8.dp))
                                                        .background(Color.White.copy(alpha = 0.08f))
                                                        .clickable {
                                                            viewModel.editSceneSubtitleStyle(
                                                                selectedSceneIndex,
                                                                item.second,
                                                                item.third,
                                                                "Classic Box"
                                                            )
                                                        }
                                                        .border(0.5.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                                                        .padding(horizontal = 8.dp, vertical = 5.dp)
                                                ) {
                                                    Text(
                                                        text = item.first.localize(appLanguageState),
                                                        fontSize = 9.sp,
                                                        color = parseHexColor(item.second),
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(2.dp))

                                        Text("Caption Design Preset".localize(appLanguageState), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        androidx.compose.foundation.lazy.LazyRow(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            val presets = listOf(
                                                Triple("Classic Box", "Classic 📦", "#99000000"),
                                                Triple("TikTok Yellow", "TikTok 💛", "#CC111111"),
                                                Triple("Cyber Neon", "Neon 🌐", "#00F0FF"),
                                                Triple("Hot Pink Style", "Pink 🌸", "#FF007F"),
                                                Triple("Minimal Borderless", "Clean ✨", "#00000000"),
                                                Triple("Royal Gold", "Gold 🏆", "#E61A1502")
                                            )
                                            items(presets.size) { idx ->
                                                val item = presets[idx]
                                                val isSelected = sc.subtitleDesign == item.first || (sc.subtitleDesign.isNullOrEmpty() && item.first == "Classic Box")
                                                Box(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(14.dp))
                                                        .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                                                        .border(1.5.dp, if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent, RoundedCornerShape(14.dp))
                                                        .clickable {
                                                            viewModel.editSceneSubtitleStyle(selectedSceneIndex, sc.subtitleColor, sc.subtitleBgColor, item.first)
                                                        }
                                                        .padding(horizontal = 14.dp, vertical = 8.dp)
                                                ) {
                                                    Text(item.second.localize(appLanguageState), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                }
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(2.dp))

                                        Text("Select Subtitle Font".localize(appLanguageState), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        androidx.compose.foundation.lazy.LazyRow(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            val fontOptions = EditorConstants.FONTS
                                            items(fontOptions.size) { idx ->
                                                val fontName = fontOptions[idx]
                                                val isSelected = sc.captionFont == fontName || (sc.captionFont.isNullOrEmpty() && fontName == "TikTok Style")
                                                Box(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(14.dp))
                                                        .background(if (isSelected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                                                        .border(1.5.dp, if (isSelected) MaterialTheme.colorScheme.secondary else Color.Transparent, RoundedCornerShape(14.dp))
                                                        .clickable {
                                                            viewModel.editSceneFont(selectedSceneIndex, fontName)
                                                        }
                                                        .padding(horizontal = 14.dp, vertical = 8.dp)
                                                ) {
                                                    Text(fontName.localize(appLanguageState), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                }
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(2.dp))

                                        Text("Caption Text Color".localize(appLanguageState), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        LazyRow(
                                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            val textColors = listOf(
                                                "#FFFFFF", "#FFFF00", "#00F0FF", "#39FF14", "#FF007F", "#FFD700", "#FF5722", "#E0B0FF"
                                            )
                                            items(textColors) { hex ->
                                                val isSelected = sc.subtitleColor.lowercase() == hex.lowercase() || (sc.subtitleColor.isEmpty() && hex == "#FFFFFF")
                                                Box(
                                                    modifier = Modifier
                                                        .size(28.dp)
                                                        .clip(CircleShape)
                                                        .background(Color(android.graphics.Color.parseColor(hex)))
                                                        .border(
                                                            width = if (isSelected) 2.5.dp else 1.dp,
                                                            color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Gray.copy(alpha = 0.5f),
                                                            shape = CircleShape
                                                        )
                                                        .clickable {
                                                            viewModel.editSceneSubtitleStyle(selectedSceneIndex, hex, sc.subtitleBgColor, sc.subtitleDesign)
                                                        }
                                                ) {
                                                    if (isSelected) {
                                                        Icon(
                                                            imageVector = Icons.Default.Check,
                                                            contentDescription = null,
                                                            tint = if (hex == "#FFFFFF") Color.Black else Color.White,
                                                            modifier = Modifier.size(14.dp).align(Alignment.Center)
                                                        )
                                                    }
                                                }
                                            }
                                        }

                                        // Display Custom Bg choice only if design is Classic Box
                                        if (sc.subtitleDesign == "Classic Box" || sc.subtitleDesign.isNullOrEmpty()) {
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text("Caption Box Color & Transparency".localize(appLanguageState), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                            LazyRow(
                                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                val bgColors = listOf(
                                                    Pair("Translucent Black", "#99000000"),
                                                    Pair("Solid Black", "#FF000000"),
                                                    Pair("Translucent Purple", "#BF0F021B"),
                                                    Pair("Translucent Blue", "#AF001A4E"),
                                                    Pair("Translucent Crimson", "#B0800000")
                                                )
                                                items(bgColors) { pair ->
                                                    val isSelected = sc.subtitleBgColor.lowercase() == pair.second.lowercase() || (sc.subtitleBgColor.isEmpty() && pair.second == "#99000000")
                                                    Box(
                                                        modifier = Modifier
                                                            .clip(RoundedCornerShape(8.dp))
                                                            .background(if (isSelected) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                                                            .border(1.dp, if (isSelected) MaterialTheme.colorScheme.tertiary else Color.Transparent, RoundedCornerShape(8.dp))
                                                            .clickable {
                                                                viewModel.editSceneSubtitleStyle(selectedSceneIndex, sc.subtitleColor, pair.second, sc.subtitleDesign)
                                                            }
                                                            .padding(horizontal = 10.dp, vertical = 6.dp)
                                                    ) {
                                                        Text(pair.first.localize(appLanguageState), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                                    }
                                                }
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(4.dp))

                                        Text("Scene Clip Duration:".localize(appLanguageState) + " ${sc.durationSeconds} " + "Seconds".localize(appLanguageState), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.Schedule, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Slider(
                                                value = sc.durationSeconds.toFloat(),
                                                onValueChange = { viewModel.editSceneDuration(selectedSceneIndex, it.toInt()) },
                                                valueRange = 1f..15f,
                                                modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                                            )
                                            Text("${sc.durationSeconds}" + "s".localize(appLanguageState), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        }

                                        Spacer(modifier = Modifier.height(4.dp))

                                        // Timeline edit toolkit: Split / Cut / Stamp
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            // "Split Clip" action
                                            Button(
                                                onClick = {
                                                    viewModel.splitScene(selectedSceneIndex, "[Splitted Clip] New Segment")
                                                },
                                                shape = RoundedCornerShape(8.dp),
                                                modifier = Modifier.weight(1f),
                                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onSurfaceVariant)
                                            ) {
                                                Icon(Icons.Default.ContentCut, contentDescription = null, modifier = Modifier.size(14.dp))
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("Split Clip".localize(appLanguageState), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            }

                                            // "Add Sticker" action
                                            Button(
                                                onClick = { showStickerDrawer = true },
                                                shape = RoundedCornerShape(8.dp),
                                                modifier = Modifier.weight(1f),
                                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onSurfaceVariant)
                                            ) {
                                                Icon(Icons.Default.AddReaction, contentDescription = null, modifier = Modifier.size(14.dp))
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("Add Sticker".localize(appLanguageState), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            }

                                            // "Delete Scene" action
                                            Button(
                                                onClick = {
                                                    viewModel.deleteScene(selectedSceneIndex)
                                                },
                                                shape = RoundedCornerShape(8.dp),
                                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer),
                                                enabled = activeScenes.size > 1
                                            ) {
                                                Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(14.dp))
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(4.dp))

                                        // Timeline Reordering One-click Actions
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            Button(
                                                onClick = {
                                                    if (selectedSceneIndex > 0) {
                                                        viewModel.reorderScenes(selectedSceneIndex, selectedSceneIndex - 1)
                                                        viewModel.selectedSceneIndex.value = selectedSceneIndex - 1
                                                    }
                                                },
                                                shape = RoundedCornerShape(8.dp),
                                                modifier = Modifier.weight(1f),
                                                enabled = selectedSceneIndex > 0,
                                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer)
                                            ) {
                                                Icon(Icons.Default.ArrowBack, contentDescription = null, modifier = Modifier.size(14.dp))
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("Move Left".localize(appLanguageState), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            }

                                            Button(
                                                onClick = {
                                                    if (selectedSceneIndex < activeScenes.size - 1) {
                                                        viewModel.reorderScenes(selectedSceneIndex, selectedSceneIndex + 1)
                                                        viewModel.selectedSceneIndex.value = selectedSceneIndex + 1
                                                    }
                                                },
                                                shape = RoundedCornerShape(8.dp),
                                                modifier = Modifier.weight(1f),
                                                enabled = selectedSceneIndex < activeScenes.size - 1,
                                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer)
                                            ) {
                                                Text("Move Right".localize(appLanguageState), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Icon(Icons.Default.ArrowForward, contentDescription = null, modifier = Modifier.size(14.dp))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } ?: Text("Configure tracks details above".localize(appLanguageState), color = Color.Gray, modifier = Modifier.padding(20.dp))
            }
        }
    }

    // Modal Sticker Selection Sheet Drawer
    if (showStickerDrawer) {
        AlertDialog(
            onDismissRequest = { showStickerDrawer = false },
            title = { Text("Select Stickers overlay".localize(appLanguageState), fontWeight = FontWeight.Bold) },
            text = {
                Box(modifier = Modifier.height(200.dp)) {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(5),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(EditorConstants.STICKERS) { sticker ->
                            Box(
                                modifier = Modifier
                                    .size(45.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .clickable {
                                        currentScene?.let { sc ->
                                            viewModel.editSceneOverlayText(
                                                selectedSceneIndex,
                                                (sc.textOverlay ?: "") + " $sticker",
                                                sc.overlayColor
                                            )
                                        }
                                        showStickerDrawer = false
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(sticker, fontSize = 24.sp)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showStickerDrawer = false }) {
                    Text("Close".localize(appLanguageState))
                }
            }
        )
    }

    // Media Import presets simulator drawer
    if (showMediaImportDrawer) {
        val mediaPresets = listOf(
            Pair("Sci-Fi Nebula System", "🚀"),
            Pair("Vibrant Cyberpunk Street", "⚡"),
            Pair("Golden Tropical Sunset", "☀️"),
            Pair("Anime Dojo Practice", "🌸"),
            Pair("Monochrome Hand Sketches", "✏️"),
            Pair("Cinematic Drone Mountain", "⛰️")
        )

        AlertDialog(
            onDismissRequest = { showMediaImportDrawer = false },
            title = { Text("Import Photo / Video Media".localize(appLanguageState), fontWeight = FontWeight.Bold, fontSize = 16.sp) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = {
                            filePickerLauncher.launch("*/*")
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ),
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) {
                        Icon(imageVector = androidx.compose.material.icons.Icons.Default.Photo, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Choose from device gallery".localize(appLanguageState), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }

                    Text("Or Choose Cinematic Preset Backdrop:".localize(appLanguageState), fontSize = 11.sp, color = Color.Gray, fontWeight = FontWeight.Bold)

                    Box(modifier = Modifier.height(180.dp)) {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(2),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(mediaPresets) { preset ->
                                Card(
                                    onClick = {
                                        // Append imported media clip as scene inside projects storyboard
                                        val currentList = activeScenes
                                        val rawUrl = kotlinx.coroutines.runBlocking {
                                            viewModel.repository.getBestMatchingImage(
                                                preset.first,
                                                "Cinematic",
                                                currentList.size,
                                                imageSource = viewModel.selectedImageSource.value,
                                                visualMedium = viewModel.selectedVisualMedium.value
                                            )
                                        }
                                        val localPath = kotlinx.coroutines.runBlocking {
                                            viewModel.repository.downloadMediaToLocal(rawUrl)
                                        }
                                        val newScene = Scene(
                                            sceneNumber = currentList.size + 1,
                                            narrationText = "Imported visual matching ".localize(appLanguageState) + preset.first.localize(appLanguageState) + ".",
                                            visualPrompt = "Captivating epic cinematography overlay preset representing " + preset.first,
                                            subtitle = preset.first.localize(appLanguageState),
                                            durationSeconds = 5,
                                            mediaPath = localPath,
                                            remoteUrl = rawUrl
                                        )
                                        viewModel.insertScene(currentList.size, newScene)
                                        showMediaImportDrawer = false
                                        // Trigger small text narration
                                        viewModel.speakText("Imported".localize(appLanguageState) + " " + preset.first.localize(appLanguageState) + " " + "clip successfully.".localize(appLanguageState))
                                    },
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                ) {
                                    Column(
                                        modifier = Modifier.padding(10.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(preset.second, fontSize = 28.sp)
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(preset.first.localize(appLanguageState), fontSize = 10.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, lineHeight = 12.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showMediaImportDrawer = false }) {
                    Text("Close".localize(appLanguageState))
                }
            }
        )
    }

    // Interactive Placement Dialog for device media files
    if (showImportChoiceDialog) {
        val pickedPath = lastSelectedUriPath.value
        if (pickedPath != null) {
            AlertDialog(
                onDismissRequest = { showImportChoiceDialog = false },
                title = { Text("Choose Placement Layout".localize(appLanguageState), fontWeight = FontWeight.Bold, fontSize = 16.sp) },
                text = {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Where would you like to place this file in the timeline?".localize(appLanguageState), fontSize = 12.sp, color = Color.Gray)
                        Spacer(modifier = Modifier.height(6.dp))

                        Button(
                            onClick = {
                                if (selectedSceneIndex in activeScenes.indices) {
                                    val isVideo = pickedPath.contains("video", ignoreCase = true) || 
                                                  pickedPath.contains(".mp4", ignoreCase = true) || 
                                                  pickedPath.contains(".3gp", ignoreCase = true) || 
                                                  pickedPath.contains(".3gpp", ignoreCase = true) || 
                                                  pickedPath.contains(".mkv", ignoreCase = true) || 
                                                  pickedPath.contains(".webm", ignoreCase = true) || 
                                                  pickedPath.contains(".mov", ignoreCase = true) || 
                                                  pickedPath.contains(".avi", ignoreCase = true)
                                    viewModel.swapSceneMedia(selectedSceneIndex, pickedPath, if (isVideo) "VIDEO" else "IMAGE")
                                    viewModel.speakText("Replaced backdrop for clip ".localize(appLanguageState) + "${selectedSceneIndex + 1}")
                                }
                                showImportChoiceDialog = false
                                showMediaImportDrawer = false
                            },
                            modifier = Modifier.fillMaxWidth().height(48.dp)
                        ) {
                            Text("Replace Selected Scene Backdrop".localize(appLanguageState), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = {
                                val nextIndex = selectedSceneIndex + 1
                                val isVideo = pickedPath.contains("video", ignoreCase = true) || 
                                              pickedPath.contains(".mp4", ignoreCase = true) || 
                                              pickedPath.contains(".3gp", ignoreCase = true) || 
                                              pickedPath.contains(".3gpp", ignoreCase = true) || 
                                              pickedPath.contains(".mkv", ignoreCase = true) || 
                                              pickedPath.contains(".webm", ignoreCase = true) || 
                                              pickedPath.contains(".mov", ignoreCase = true) || 
                                              pickedPath.contains(".avi", ignoreCase = true)
                                val newScene = Scene(
                                    sceneNumber = nextIndex + 1,
                                    narrationText = "Imported mid-video clip".localize(appLanguageState),
                                    visualPrompt = "Custom User Imported Media Content",
                                    subtitle = "Custom Clip".localize(appLanguageState),
                                    mediaPath = pickedPath,
                                    mediaType = if (isVideo) "VIDEO" else "IMAGE",
                                    durationSeconds = 5
                                )
                                viewModel.insertScene(nextIndex, newScene)
                                viewModel.speakText("Inserted custom clip inside video layout".localize(appLanguageState))
                                showImportChoiceDialog = false
                                showMediaImportDrawer = false
                            },
                            modifier = Modifier.fillMaxWidth().height(48.dp)
                        ) {
                            Text("Insert in Between Video".localize(appLanguageState), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = {
                                val isVideo = pickedPath.contains("video", ignoreCase = true) || 
                                              pickedPath.contains(".mp4", ignoreCase = true) || 
                                              pickedPath.contains(".3gp", ignoreCase = true) || 
                                              pickedPath.contains(".3gpp", ignoreCase = true) || 
                                              pickedPath.contains(".mkv", ignoreCase = true) || 
                                              pickedPath.contains(".webm", ignoreCase = true) || 
                                              pickedPath.contains(".mov", ignoreCase = true) || 
                                              pickedPath.contains(".avi", ignoreCase = true)
                                val newScene = Scene(
                                    sceneNumber = activeScenes.size + 1,
                                    narrationText = "Imported timeline addition".localize(appLanguageState),
                                    visualPrompt = "Custom User Imported Media Content",
                                    subtitle = "Custom Clip".localize(appLanguageState),
                                    mediaPath = pickedPath,
                                    mediaType = if (isVideo) "VIDEO" else "IMAGE",
                                    durationSeconds = 5
                                )
                                viewModel.insertScene(activeScenes.size, newScene)
                                viewModel.speakText("Appended custom clip at end of timeline".localize(appLanguageState))
                                showImportChoiceDialog = false
                                showMediaImportDrawer = false
                            },
                            modifier = Modifier.fillMaxWidth().height(48.dp)
                        ) {
                            Text("Add to End of Timeline".localize(appLanguageState), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showImportChoiceDialog = false }) {
                        Text("Cancel".localize(appLanguageState))
                    }
                }
            )
        }
    }

    if (showCancelExportConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showCancelExportConfirmDialog = false },
            title = {
                Text(
                    text = "Cancel Export?".localize(appLanguageState),
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = Color.White
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to cancel exporting the video?".localize(appLanguageState),
                    fontSize = 14.sp,
                    color = Color.LightGray
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showCancelExportConfirmDialog = false
                        viewModel.cancelExport()
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
                    onClick = { showCancelExportConfirmDialog = false }
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

    // Modal Full Screen Compile Export Progress
    if (isExporting && !isExportingMinimized) {
        Dialog(
            onDismissRequest = {},
            properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.92f)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .background(Color(0xFF0F0922), RoundedCornerShape(24.dp))
                        .border(2.dp, Color(0xFF2B1C4E), RoundedCornerShape(24.dp))
                        .padding(26.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Elite Engine Active Badge
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                Brush.horizontalGradient(
                                    listOf(Color(0xFFFFD700), Color(0xFFFF5722))
                                )
                            )
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "👑 " + "EXCLUSIVE ULTRA-HD RENDER ENGINE".localize(appLanguageState),
                            color = Color.Black,
                            fontWeight = FontWeight.Black,
                            fontSize = 8.5.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    PremiumCircularLoader(sizeDp = 64)

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = "Rendering Final Output File".localize(appLanguageState),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = exportStatus.localize(appLanguageState),
                        color = Color.LightGray,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(top = 4.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        minLines = 2,
                        maxLines = 2
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    PremiumLinearShimmerProgress(
                        progress = exportProgress
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Progress Milestones card
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF16112C))
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val milestones = listOf(
                                "Pipeline Initializer" to 0.15f,
                                "Neural Render Grid" to 0.40f,
                                "Cinematic LUT Baker" to 0.65f,
                                "Subtitle Overlay Engine" to 0.85f,
                                "High-Definition Encoder" to 1.00f
                            )

                            milestones.forEachIndexed { idx, pair ->
                                val name = pair.first
                                val targetProg = pair.second
                                val prevTargetProg = if (idx == 0) 0f else milestones[idx - 1].second
                                val status = when {
                                    exportProgress >= targetProg -> "COMPLETED"
                                    exportProgress >= prevTargetProg -> "ACTIVE"
                                    else -> "PENDING"
                                }

                                val statusColor = when (status) {
                                    "COMPLETED" -> Color(0xFF39FF14) // Neon Green
                                    "ACTIVE" -> Color(0xFF00F0FF)    // Neon Cyan
                                    else -> Color.Gray
                                }

                                val icon = when (status) {
                                    "COMPLETED" -> Icons.Default.CheckCircle
                                    "ACTIVE" -> Icons.Default.Cached
                                    else -> Icons.Default.RadioButtonUnchecked
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = icon,
                                            contentDescription = null,
                                            tint = statusColor,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Text(
                                            text = name.localize(appLanguageState),
                                            color = if (status == "ACTIVE") Color.White else Color.LightGray,
                                            fontSize = 11.sp,
                                            fontWeight = if (status == "ACTIVE") FontWeight.Bold else FontWeight.Normal
                                        )
                                    }

                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(statusColor.copy(alpha = 0.15f))
                                            .border(0.5.dp, statusColor.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = status.localize(appLanguageState),
                                            color = statusColor,
                                            fontSize = 8.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${(exportProgress * 100).toInt()}% " + "Rendered".localize(appLanguageState),
                            color = Color(0xFF00F0FF),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Icon(
                            imageVector = Icons.Default.HourglassTop,
                            contentDescription = null,
                            tint = Color(0xFFFF007F),
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        TextButton(
                            onClick = { showCancelExportConfirmDialog = true },
                            modifier = Modifier
                                .weight(1f)
                                .background(Color(0xFFFF007F).copy(alpha = 0.08f), RoundedCornerShape(12.dp))
                                .border(1.dp, Color(0xFFFF007F).copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                        ) {
                            Text("Cancel".localize(appLanguageState), color = Color(0xFFFF007F), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }

                        Button(
                            onClick = { viewModel.isExportingMinimized.value = true },
                            modifier = Modifier.weight(1.2f),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF39FF14)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = "Run in Background".localize(appLanguageState),
                                color = Color.Black,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }

    // Modal Full Screen Media Generation Progress
    if (isGenerating) {
        Dialog(
            onDismissRequest = {},
            properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.92f)),
                contentAlignment = Alignment.Center
            ) {
                PremiumPipelineDialog(
                    title = "Refreshing Backdrops",
                    statusText = generationStatus,
                    progress = generationProgress,
                    appLanguageState = appLanguageState,
                    onCancel = { viewModel.cancelGeneration() }
                )
            }
        }
    }

    if (showExportQualityDialog) {
        var tempSelectedRes by remember { mutableStateOf(viewModel.selectedResolution.value) }
        AlertDialog(
            onDismissRequest = { showExportQualityDialog = false },
            title = {
                Column {
                    Text(
                        text = "Choose Export Quality".localize(appLanguageState),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Select output format & resolution for rendering video and media frames:".localize(appLanguageState),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            },
            text = {
                val scrollStateRes = rememberScrollState()
                val resolutions = listOf(
                    Triple("360p", "360p • Low SD", "Fastest speed, perfect for small screens & instant chat shares".localize(appLanguageState)),
                    Triple("480p", "480p • Standard SD", "Optimized standard definition for WhatsApp & web platforms".localize(appLanguageState)),
                    Triple("540p", "540p • Medium SD", "Good balance of processing speed and resolution storage size".localize(appLanguageState)),
                    Triple("720p", "720p • HD Ready", "Crisp high definition - standard for rapid vertical stories".localize(appLanguageState)),
                    Triple("1080p", "1080p • Full HD", "Highly recommended high-definition reel (Crisp on all networks)".localize(appLanguageState)),
                    Triple("2K", "2K • Cinematic QHD", "Exquisite premium detailing for tablets and professional projections".localize(appLanguageState)),
                    Triple("4K", "4K • Ultra HD", "Stunning cinematic output with extreme clarity (Requires modern hardware)".localize(appLanguageState)),
                    Triple("8K", "8K • Extreme UHD", "Breathtaking peak-fidelity extreme masterwork (Pro rendering workflow)".localize(appLanguageState))
                )
                Column(
                    modifier = Modifier
                        .heightIn(max = 280.dp)
                        .fillMaxWidth()
                        .verticalScroll(scrollStateRes),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    resolutions.forEach { (resKey, title, desc) ->
                        val isSelected = tempSelectedRes == resKey
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f) else Color.Transparent
                            ),
                            border = androidx.compose.foundation.BorderStroke(
                                width = if (isSelected) 2.dp else 1.dp,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { tempSelectedRes = resKey }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 12.dp, horizontal = 16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(20.dp)
                                        .background(
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                            shape = androidx.compose.foundation.shape.CircleShape
                                        )
                                        .border(
                                            width = 1.5.dp,
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                            shape = androidx.compose.foundation.shape.CircleShape
                                        ),
                                    contentAlignment = androidx.compose.ui.Alignment.Center
                                ) {
                                    if (isSelected) {
                                        Icon(
                                            imageVector = androidx.compose.material.icons.Icons.Default.Check,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onPrimary,
                                            modifier = Modifier.size(12.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = title,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = desc,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        lineHeight = 14.sp,
                                        modifier = Modifier.padding(top = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = androidx.compose.material.icons.Icons.Default.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "💡 Tip: Chhote/Purane device par video jaldi save karne ke liye 720p ya 480p select karein! (Selecting 720p/480p saves video up to 4x faster on older devices)".localize(appLanguageState),
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 15.sp
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.selectedResolution.value = tempSelectedRes
                        showExportQualityDialog = false
                        checkAndRequestPermissions()
                    },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Export Now".localize(appLanguageState), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showExportQualityDialog = false }
                ) {
                    Text("Cancel".localize(appLanguageState))
                }
            }
        )
    }

    // AdMob Interstitial Ad mob Dialog triggers after export completion which redirects to list!
    MockInterstitialAdDialog(
        show = showInterstitialAd,
        onDismiss = {
            showInterstitialAd = false
            // Call actual exports background thread compilation writing
            viewModel.exportVideo(
                onExportComplete = {
                    showConfirmExportDialog = true
                }
            )
        }
    )

    if (showConfirmExportDialog) {
        val previewUri = remember(compiledPreviewPath) {
            compiledPreviewPath?.let { getPlayableUri(context, it) }
        }
        var isSaving by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = {
                // Keep modal open unless discarded/confirmed
            },
            properties = DialogProperties(usePlatformDefaultWidth = false),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            confirmButton = {},
            dismissButton = {},
            title = null,
            text = {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentHeight(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0F0B1E)),
                    border = androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Confirm Export 🎬".localize(appLanguageState),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Please review the video preview. Would you like to approve and export, or discard and retry editing?".localize(appLanguageState),
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.LightGray,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )
                        
                        Spacer(modifier = Modifier.height(16.dp))

                        if (previewUri != null) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(280.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(Color.Black)
                            ) {
                                VideoPlayer(videoUri = previewUri, appLanguage = appLanguageState, modifier = Modifier.fillMaxSize())
                            }
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(180.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(Color.Black.copy(alpha = 0.3f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Loading preview player...".localize(appLanguageState),
                                    color = Color.Gray,
                                    fontSize = 12.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        if (isSaving) {
                            CircularProgressIndicator(
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Saving to Gallery & Cloud...".localize(appLanguageState),
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Button(
                                    onClick = {
                                        viewModel.discardPendingExport()
                                        showConfirmExportDialog = false
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .testTag("confirm_export_discard_button"),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(imageVector = Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Discard and Retry".localize(appLanguageState), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }

                                Button(
                                    onClick = {
                                        isSaving = true
                                        viewModel.finalizeExport(
                                            onSuccess = {
                                                val savedPath = viewModel.exportedFilePath.value ?: ""
                                                exportedPathForSuccess = savedPath
                                                activePreviewTab = 1
                                                isSaving = false
                                                showConfirmExportDialog = false
                                                showExportSuccessDialog = true
                                            }
                                        )
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                                    modifier = Modifier
                                        .weight(1.2f)
                                        .height(48.dp)
                                        .testTag("confirm_export_save_button"),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(imageVector = Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Approve and Export".localize(appLanguageState), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        )
    }

    if (showExportSuccessDialog) {
        ConfettiExplosion(show = true)
        AlertDialog(
            onDismissRequest = { 
                showExportSuccessDialog = false 
                onExportFinished()
            },
            shape = RoundedCornerShape(28.dp),
            containerColor = Color(0xFF0F0B1E),
            title = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF2E7D32).copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Success",
                            tint = Color(0xFF4CAF50),
                            modifier = Modifier.size(40.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Export Successful!".localize(appLanguageState),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        textAlign = TextAlign.Center
                    )
                }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Your professional high-fidelity Reel video has been compiled and saved with premium configurations.".localize(appLanguageState),
                        color = Color.LightGray,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(12.dp))
                            .padding(10.dp)
                    ) {
                        Column {
                            val fileName = if (exportedPathForSuccess.isNotEmpty()) exportedPathForSuccess.substringAfterLast("/") else "Yashora_Reel.mp4"
                            Text(
                                text = "File Name:".localize(appLanguageState) + " " + fileName,
                                fontSize = 11.sp,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Location:".localize(appLanguageState) + " Movies/Yashora",
                                fontSize = 10.sp,
                                color = Color.Gray
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            shareVideo(context, exportedPathForSuccess, appLanguageState)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("success_share_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Share Video Now".localize(appLanguageState), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { showSuccessPlayerDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Play Video".localize(appLanguageState), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = {
                                showExportSuccessDialog = false
                                onExportFinished()
                            },
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color.Gray),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Done".localize(appLanguageState), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    // Single Master Continuous Voiceover download/share for Kinemaster & external editors
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1B2E)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF4CAF50).copy(alpha = 0.4f))
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "🎙️ Single Voiceover Audio Track (.WAV)".localize(appLanguageState),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = "Export full script voiceover in 1 file for Kinemaster or InShot".localize(appLanguageState),
                                fontSize = 9.sp,
                                color = Color.LightGray,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                FilledTonalButton(
                                    onClick = { viewModel.saveMasterVoiceoverToDownloads(context) },
                                    colors = ButtonDefaults.filledTonalButtonColors(containerColor = Color(0xFF2E7D32)),
                                    modifier = Modifier.weight(1f).height(38.dp),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Icon(imageVector = Icons.Default.ArrowDownward, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Download Voiceover".localize(appLanguageState), fontSize = 10.sp, color = Color.White, fontWeight = FontWeight.Bold)
                                }

                                FilledTonalButton(
                                    onClick = { viewModel.shareMasterVoiceoverAudio(context) },
                                    colors = ButtonDefaults.filledTonalButtonColors(containerColor = Color(0xFF1565C0)),
                                    modifier = Modifier.weight(1f).height(38.dp),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Icon(imageVector = Icons.Default.Share, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Share to Kinemaster".localize(appLanguageState), fontSize = 10.sp, color = Color.White, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        )
    }

    if (showSuccessPlayerDialog) {
        val videoUri = remember(exportedPathForSuccess) {
            if (exportedPathForSuccess.isNotEmpty()) {
                getPlayableUri(context, exportedPathForSuccess)
            } else {
                null
            }
        }
        AlertDialog(
            onDismissRequest = { showSuccessPlayerDialog = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            confirmButton = {},
            dismissButton = {},
            title = null,
            text = {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentHeight(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0C0914)),
                    border = androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Reel Player".localize(appLanguageState),
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 16.sp
                            )
                            IconButton(onClick = { showSuccessPlayerDialog = false }) {
                                Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        if (videoUri != null) {
                            VideoPlayer(videoUri = videoUri, appLanguage = appLanguageState, modifier = Modifier.fillMaxWidth())
                        } else {
                            Text("Loading media player...".localize(appLanguageState), color = Color.Gray, fontSize = 12.sp)
                        }
                    }
                }
            }
        )
    }

    if (showPermissionExplanationDialog) {
        AlertDialog(
            onDismissRequest = { showPermissionExplanationDialog = false },
            title = {
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Icon(
                        imageVector = androidx.compose.material.icons.Icons.Default.Security,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Storage Permission Required".localize(appLanguageState),
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Yashora Reel Generator needs permission to access files & storage to save high-quality rendered videos directly into your gallery (Movies/Yashora) and to read imported files safely.".localize(appLanguageState),
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Please enable files/media access in Settings with just one click below.".localize(appLanguageState),
                        fontSize = 11.sp,
                        lineHeight = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showPermissionExplanationDialog = false
                        openAppSettings()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text("Open App Settings".localize(appLanguageState), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showPermissionExplanationDialog = false
                        pendingExportAction = false
                    }
                ) {
                    Text("Cancel".localize(appLanguageState), fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
                }
            }
        )
    }

    // Full Screen Reel Video Preview Dialog
    if (showFullScreenPlayer) {
        val currentScene = activeScenes.getOrNull(selectedSceneIndex)
        Dialog(
            onDismissRequest = { 
                showFullScreenPlayer = false 
                viewModel.stopSpeak()
            },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                // Background Frame Layout
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 90.dp), // make space for elegant physical bottom actions panel
                    contentAlignment = Alignment.Center
                ) {
                    currentScene?.let { sc ->
                        val path = sc.mediaPath
                        if (!path.isNullOrEmpty()) {
                            val isVideo = path.contains(".mp4") || path.contains("video")
                            if (isVideo) {
                                androidx.compose.ui.viewinterop.AndroidView(
                                    factory = { ctx ->
                                        android.widget.VideoView(ctx).apply {
                                            setOnPreparedListener { mp ->
                                                mp.isLooping = true
                                                try {
                                                    mp.setVolume(1.0f, 1.0f) // full volume in fullscreen option!
                                                } catch (e: Exception) {}
                                            }
                                            setOnErrorListener { mp, what, extra ->
                                                Log.e("EditorScreenFS", "VideoView full error what=$what extra=$extra")
                                                true // prevents default system dialog popup and keeps app crash-free
                                            }
                                        }
                                    },
                                    update = { videoView ->
                                        val safePath = if (path.startsWith("http://")) path.replace("http://", "https://") else path
                                        if (videoView.tag != safePath) {
                                            videoView.tag = safePath
                                            try {
                                                videoView.setVideoPath(safePath)
                                                if (isPlaying) {
                                                    try { videoView.start() } catch (e: Exception) {}
                                                } else {
                                                    try { videoView.pause() } catch (e: Exception) {}
                                                }
                                            } catch (e: Exception) {
                                                Log.e("EditorScreenFS", "Failed launching VideoView stream full", e)
                                            }
                                        } else {
                                            try {
                                                if (isPlaying) {
                                                    if (!videoView.isPlaying) {
                                                        videoView.start()
                                                    }
                                                } else {
                                                    if (videoView.isPlaying) {
                                                        videoView.pause()
                                                    }
                                                }
                                            } catch (e: Exception) {
                                                Log.e("EditorScreenFS", "Error fullscreen video controls update", e)
                                            }
                                        }
                                    },
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                coil.compose.AsyncImage(
                                    model = path,
                                    contentDescription = sc.visualPrompt,
                                    contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                            
                            // Color grading filter tint
                            val filter = EditorConstants.FILTERS[sc.selectedFilterIndex]
                            if (filter.tintColor.alpha > 0f) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(filter.tintColor)
                                )
                            }

                            // Readability overlay shadow
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.verticalGradient(
                                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.4f), Color.Black.copy(alpha = 0.85f))
                                        )
                                    )
                            )
                            
                            // Visual overlay texts (Upper Title Overlay)
                            sc.textOverlay?.let { over ->
                                if (over.isNotEmpty()) {
                                    val overlayThemeColor = parseHexColor(sc.overlayColor)
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopCenter)
                                            .padding(top = 100.dp, start = 30.dp, end = 30.dp)
                                            .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(8.dp))
                                            .padding(horizontal = 14.dp, vertical = 7.dp)
                                    ) {
                                        Text(
                                            text = over.uppercase(),
                                            color = overlayThemeColor,
                                            fontSize = 18.sp,
                                            fontWeight = FontWeight.Black,
                                            letterSpacing = 1.5.sp,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            }

                            // Dynamic screen subtitle text inside professional Bottom pill container
                            if (sc.subtitle.isNotEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                        .padding(bottom = 40.dp, start = 24.dp, end = 24.dp)
                                        .background(Color.Black.copy(alpha = 0.78f), RoundedCornerShape(14.dp))
                                        .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(14.dp))
                                        .padding(horizontal = 18.dp, vertical = 12.dp)
                                ) {
                                    Text(
                                        text = sc.subtitle,
                                        color = Color.White,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        textAlign = TextAlign.Center,
                                        lineHeight = 22.sp
                                    )
                                }
                            }
                        } else {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("SCENE ${sc.sceneNumber}", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Black, fontSize = 16.sp)
                                Spacer(modifier = Modifier.height(18.dp))
                                Text("Slide media not found or syncing...".localize(appLanguageState), color = Color.Gray, fontSize = 13.sp)
                            }
                        }
                    } ?: Text("Active track buffer empty".localize(appLanguageState), color = Color.Gray)
                }

                // Close Button Top-Left floating overlay
                IconButton(
                    onClick = { 
                        showFullScreenPlayer = false 
                        viewModel.stopSpeak()
                    },
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(top = 40.dp, start = 20.dp)
                        .background(Color.Black.copy(alpha = 0.62f), CircleShape)
                        .size(42.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close full preview",
                        tint = Color.White
                    )
                }

                // Physical controls and skip actions panel at the bottom center
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(Color(0xFF0C0912))
                        .padding(vertical = 16.dp, horizontal = 24.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Left Scene Indicator details
                        Column {
                            Text(
                                text = "Playing".localize(appLanguageState),
                                color = Color.Gray,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Scene ${selectedSceneIndex + 1}/${activeScenes.size}".localize(appLanguageState),
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Black
                            )
                        }

                        // Playback Control Group
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // Previous Slide button
                            IconButton(
                                onClick = {
                                    if (selectedSceneIndex > 0) {
                                        viewModel.selectedSceneIndex.value = selectedSceneIndex - 1
                                        if (isPlaying) {
                                            viewModel.stopSpeak()
                                            activeScenes.getOrNull(selectedSceneIndex - 1)?.let {
                                                viewModel.speakText(if (it.narrationText.isNotEmpty()) it.narrationText else it.subtitle)
                                            }
                                        }
                                    }
                                },
                                enabled = selectedSceneIndex > 0,
                                modifier = Modifier
                                    .background(
                                        if (selectedSceneIndex > 0) Color.White.copy(alpha = 0.12f)
                                        else Color.White.copy(alpha = 0.03f),
                                        CircleShape
                                    )
                                    .size(40.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SkipPrevious,
                                    contentDescription = "Prev Scene",
                                    tint = if (selectedSceneIndex > 0) Color.White else Color.Gray,
                                    modifier = Modifier.size(22.dp)
                                )
                            }

                            // Play/Pause button
                            FloatingActionButton(
                                onClick = {
                                    if (isPlaying) {
                                        viewModel.isPlaying.value = false
                                        viewModel.stopSpeak()
                                    } else {
                                        viewModel.isPlaying.value = true
                                        currentScene?.let { viewModel.speakText(if (it.narrationText.isNotEmpty()) it.narrationText else it.subtitle) }
                                    }
                                },
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(48.dp),
                                shape = CircleShape
                            ) {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = "Playback Speed Control",
                                    modifier = Modifier.size(24.dp)
                                )
                            }

                            // Next Slide button
                            IconButton(
                                onClick = {
                                    if (selectedSceneIndex < activeScenes.size - 1) {
                                        viewModel.selectedSceneIndex.value = selectedSceneIndex + 1
                                        if (isPlaying) {
                                            viewModel.stopSpeak()
                                            activeScenes.getOrNull(selectedSceneIndex + 1)?.let {
                                                viewModel.speakText(if (it.narrationText.isNotEmpty()) it.narrationText else it.subtitle)
                                            }
                                        }
                                    }
                                },
                                enabled = selectedSceneIndex < activeScenes.size - 1,
                                modifier = Modifier
                                    .background(
                                        if (selectedSceneIndex < activeScenes.size - 1) Color.White.copy(alpha = 0.12f)
                                        else Color.White.copy(alpha = 0.03f),
                                        CircleShape
                                    )
                                    .size(40.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SkipNext,
                                    contentDescription = "Next Scene",
                                    tint = if (selectedSceneIndex < activeScenes.size - 1) Color.White else Color.Gray,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
    }
}

fun htmlTextSimulate(str: String): String {
    return str
}

fun parseHexColor(hexString: String): Color {
    return try {
        Color(android.graphics.Color.parseColor(hexString))
    } catch (e: Exception) {
        Color.White
    }
}

@Composable
fun ComposedVideoPlayer(
    videoUri: android.net.Uri,
    appLanguage: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var isVideoPrepared by remember { mutableStateOf(false) }
    var isVideoError by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color.Black)
            .border(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f), RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center
    ) {
        if (isVideoError) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(16.dp)
            ) {
                Text("🎬", fontSize = 32.sp)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Streaming playback active... Tap to reload".localize(appLanguage),
                    color = Color.White,
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            androidx.compose.ui.viewinterop.AndroidView(
                factory = { ctx ->
                    android.widget.VideoView(ctx).apply {
                        try {
                            val mediaController = android.widget.MediaController(ctx)
                            mediaController.setAnchorView(this)
                            setMediaController(mediaController)
                        } catch (e: Exception) {
                            Log.e("EditorVideoPlayer", "Failed to set MediaController", e)
                        }
                        try {
                            setVideoURI(videoUri)
                        } catch (e: Exception) {
                            Log.e("EditorVideoPlayer", "Failed to set video URI", e)
                        }
                        
                        setOnPreparedListener { mp ->
                            try {
                                mp.isLooping = true
                                isVideoPrepared = true
                                start()
                            } catch (e: Exception) {
                                Log.e("EditorVideoPlayer", "Failed to start playback on prepared", e)
                            }
                        }
                        
                        setOnErrorListener { mp, what, extra ->
                            Log.e("EditorVideoPlayer", "VideoView error what=$what extra=$extra")
                            isVideoError = true
                            true
                        }
                    }
                },
                update = { videoView ->
                    try {
                        if (videoView.tag != videoUri.toString()) {
                            videoView.tag = videoUri.toString()
                            videoView.setVideoURI(videoUri)
                        }
                    } catch (e: Exception) {
                        Log.e("EditorVideoPlayer", "Error during VideoView update", e)
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
            
            if (!isVideoPrepared) {
                PremiumCircularLoader(sizeDp = 24)
            }
        }
    }
}

@Composable
fun ComposedVideoPlaceholder(
    appLanguage: String,
    onExportClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF151120))
            .border(1.5.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
            .padding(12.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            Icon(
                imageVector = androidx.compose.material.icons.Icons.Default.MovieCreation,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary.copy(alpha = 0.8f),
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Pipeline Status: Draft".localize(appLanguage),
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "No composed video preview is available yet.".localize(appLanguage),
                fontSize = 9.sp,
                color = Color.LightGray,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = onExportClick,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                modifier = Modifier.height(28.dp)
            ) {
                Text("Compose Video".localize(appLanguage), fontSize = 9.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun TimelineMappingAndReorderPanel(
    viewModel: com.ritvyom.yashoraReelgenerator.presentation.viewmodels.MainViewModel,
    appLanguageState: String,
    activeScenes: List<Scene>,
    selectedSceneIndex: Int
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${activeScenes.size} " + "Active Scenes Mapped".localize(appLanguageState),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Total Duration:".localize(appLanguageState) + " ${activeScenes.sumOf { it.durationSeconds }}s",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            itemsIndexed(activeScenes) { index, scene ->
                val isSelected = index == selectedSceneIndex
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable {
                            viewModel.selectedSceneIndex.value = index
                        }
                        .border(
                            1.5.dp,
                            if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                            RoundedCornerShape(12.dp)
                        ),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(54.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color.Black),
                                contentAlignment = Alignment.Center
                            ) {
                                if (!scene.mediaPath.isNullOrEmpty()) {
                                    coil.compose.AsyncImage(
                                        model = scene.mediaPath,
                                        contentDescription = null,
                                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.Movie,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Color.Black.copy(alpha = 0.2f))
                                )
                                Text(
                                    text = "#${scene.sceneNumber}",
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Black,
                                    modifier = Modifier.align(Alignment.TopStart).padding(2.dp)
                                )
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = "Scene".localize(appLanguageState) + " ${scene.sceneNumber}",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                    )
                                    Box(
                                        modifier = Modifier
                                            .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(4.dp))
                                            .padding(horizontal = 4.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = "${scene.durationSeconds}s",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(2.dp))

                                Text(
                                    text = "🗣️ Voiceover:".localize(appLanguageState) + " " + if (scene.narrationText.isNotEmpty()) scene.narrationText else "No speech narration text.".localize(appLanguageState),
                                    fontSize = 10.sp,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                Spacer(modifier = Modifier.height(1.dp))

                                Text(
                                    text = "🎬 Visuals:".localize(appLanguageState) + " " + scene.visualPrompt,
                                    fontSize = 9.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                        Spacer(modifier = Modifier.height(4.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                IconButton(
                                    onClick = {
                                        viewModel.reorderScenes(index, index - 1)
                                        viewModel.selectedSceneIndex.value = index - 1
                                    },
                                    enabled = index > 0,
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ArrowUpward,
                                        contentDescription = "Move Up".localize(appLanguageState),
                                        tint = if (index > 0) MaterialTheme.colorScheme.primary else Color.Gray.copy(alpha = 0.3f),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }

                                IconButton(
                                    onClick = {
                                        viewModel.reorderScenes(index, index + 1)
                                        viewModel.selectedSceneIndex.value = index + 1
                                    },
                                    enabled = index < activeScenes.size - 1,
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ArrowDownward,
                                        contentDescription = "Move Down".localize(appLanguageState),
                                        tint = if (index < activeScenes.size - 1) MaterialTheme.colorScheme.primary else Color.Gray.copy(alpha = 0.3f),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Button(
                                    onClick = {
                                        viewModel.selectedSceneIndex.value = index
                                        viewModel.speakText(if (scene.narrationText.isNotEmpty()) scene.narrationText else scene.subtitle)
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                    ),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                    modifier = Modifier.height(28.dp),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(12.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Preview".localize(appLanguageState), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                }

                                IconButton(
                                    onClick = {
                                        if (activeScenes.size > 1) {
                                            val copy = activeScenes.toMutableList()
                                            copy.removeAt(index)
                                            val reindexed = copy.mapIndexed { idx, item ->
                                                item.copy(sceneNumber = idx + 1)
                                            }
                                            viewModel.selectedSceneIndex.value = kotlin.math.max(0, index - 1)
                                            viewModel.activeScenes.value = reindexed
                                            viewModel.saveCurrentScenesToDb()
                                        }
                                    },
                                    enabled = activeScenes.size > 1,
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Delete".localize(appLanguageState),
                                        tint = if (activeScenes.size > 1) MaterialTheme.colorScheme.error else Color.Gray.copy(alpha = 0.3f),
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TransitionSelectionPanel(
    viewModel: com.ritvyom.yashoraReelgenerator.presentation.viewmodels.MainViewModel,
    selectedSceneIndex: Int,
    currentScene: Scene,
    appLanguageState: String
) {
    val transitions = listOf("None", "Fade", "Slide Left", "Slide Right", "Zoom", "Dissolve")
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("Select Transition Style for Scene #${currentScene.sceneNumber}".localize(appLanguageState), fontSize = 12.sp, fontWeight = FontWeight.Bold)
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp)
        ) {
            items(transitions) { transition ->
                val isSelected = currentScene.transitionType == transition || (currentScene.transitionType.isNullOrEmpty() && transition == "None")
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                        .border(1.5.dp, if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent, RoundedCornerShape(10.dp))
                        .clickable {
                            viewModel.editSceneTransition(selectedSceneIndex, transition)
                        }
                        .padding(12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(transition.localize(appLanguageState), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun VoiceoverPanel(
    viewModel: com.ritvyom.yashoraReelgenerator.presentation.viewmodels.MainViewModel,
    selectedSceneIndex: Int,
    currentScene: Scene,
    appLanguageState: String
) {
    val aiTtsEnabledState by viewModel.aiTtsEnabled.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // AI TTS Toggle
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "AI Text-to-Speech Voiceover".localize(appLanguageState),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Generate synthetic voice narrations for script segments in the selected language".localize(appLanguageState),
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilledTonalButton(
                        onClick = {
                            val textToSpeak = if (currentScene.narrationText.isNotEmpty()) {
                                currentScene.narrationText
                            } else {
                                currentScene.subtitle
                            }
                            viewModel.speakText(textToSpeak, force = true)
                        },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Preview Voiceover".localize(appLanguageState),
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Preview".localize(appLanguageState),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Switch(
                        checked = aiTtsEnabledState,
                        onCheckedChange = { viewModel.setAiTtsEnabled(it) }
                    )
                }
            }
        }

        HorizontalDivider(
            modifier = Modifier.padding(vertical = 4.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)
        )

        Text("Edit Scene Voiceover / Speech Narration Script".localize(appLanguageState), fontSize = 12.sp, fontWeight = FontWeight.Bold)
        OutlinedTextField(
            value = currentScene.narrationText,
            onValueChange = { viewModel.editSceneNarrationText(selectedSceneIndex, it) },
            modifier = Modifier
                .fillMaxWidth()
                .height(100.dp),
            shape = RoundedCornerShape(10.dp),
            placeholder = { Text("Enter narration text to speak for this scene...".localize(appLanguageState), fontSize = 11.sp) }
        )
        Spacer(modifier = Modifier.height(4.dp))
        Button(
            onClick = { viewModel.speakText(currentScene.narrationText, force = true) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp)
        ) {
            Icon(Icons.Default.Mic, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Preview Spoken Narration (TTS)".localize(appLanguageState), fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }

        val context = LocalContext.current
        // Card for Single Master Audio File export (for Kinemaster / InShot)
        Card(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.MusicNote, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Single Master Voiceover Audio (.WAV)".localize(appLanguageState),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Download full script narration in 1 single file for Kinemaster & external video editors".localize(appLanguageState),
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { viewModel.saveMasterVoiceoverToDownloads(context) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                        modifier = Modifier.weight(1f).height(38.dp),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.ArrowDownward, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Save Single Audio".localize(appLanguageState), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = { viewModel.shareMasterVoiceoverAudio(context) },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        modifier = Modifier.weight(1f).height(38.dp),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Share to Kinemaster".localize(appLanguageState), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

class ConfettiParticle(
    var x: Float,
    var y: Float,
    val vx: Float,
    val vy: Float,
    val color: Color,
    val size: Float,
    var rotation: Float,
    val rotationSpeed: Float,
    val shapeType: Int,
    var alpha: Float
)

@Composable
fun ConfettiExplosion(show: Boolean) {
    if (!show) return

    val particles = remember {
        List(100) {
            val angle = (0..360).random() * Math.PI / 180.0
            val speed = (5..30).random().toFloat()
            val color = listOf(
                Color(0xFF00F0FF), // Neon Cyan
                Color(0xFFFF007F), // Neon Magenta
                Color(0xFFFFD700), // Premium Gold
                Color(0xFF39FF14), // Neon Green
                Color(0xFF9D4EDD), // Royal Purple
                Color(0xFFFF5722)  // Electric Orange
            ).random()
            val size = (10..22).random().toFloat()
            val rotationSpeed = (-20..20).random().toFloat()
            val shapeType = (0..2).random() // 0 = Circle, 1 = Rectangle, 2 = Triangle

            ConfettiParticle(
                x = 0f,
                y = 0f,
                vx = (Math.cos(angle) * speed).toFloat(),
                vy = (Math.sin(angle) * speed).toFloat() - 15f, // Eject upwards slightly
                color = color,
                size = size,
                rotation = (0..360).random().toFloat(),
                rotationSpeed = rotationSpeed,
                shapeType = shapeType,
                alpha = 1f
            )
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "ConfettiTransition")
    val frame by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ConfettiFrame"
    )

    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(frame) {
        tick++
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {} // pass-through touch
    ) {
        androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
            val center = androidx.compose.ui.geometry.Offset(size.width / 2f, size.height / 3f) // Explode from middle-top (success icon area)

            particles.forEach { p ->
                val elapsedTicks = tick.coerceAtMost(120)
                if (elapsedTicks > 0) {
                    p.x = p.vx * elapsedTicks * 0.8f
                    p.y = p.vy * elapsedTicks * 0.8f + 0.12f * elapsedTicks * elapsedTicks // gravity
                    p.rotation += p.rotationSpeed
                    p.alpha = (1f - (elapsedTicks / 120f)).coerceIn(0f, 1f)
                }

                if (p.alpha > 0f) {
                    val finalX = center.x + p.x
                    val finalY = center.y + p.y

                    rotate(
                        degrees = p.rotation,
                        pivot = androidx.compose.ui.geometry.Offset(finalX, finalY)
                    ) {
                        val paintColor = p.color.copy(alpha = p.alpha)
                        when (p.shapeType) {
                            0 -> { // Circle
                                drawCircle(
                                    color = paintColor,
                                    radius = p.size / 2f,
                                    center = androidx.compose.ui.geometry.Offset(finalX, finalY)
                                )
                            }
                            1 -> { // Rectangle
                                drawRect(
                                    color = paintColor,
                                    topLeft = androidx.compose.ui.geometry.Offset(finalX - p.size / 2f, finalY - p.size / 2f),
                                    size = androidx.compose.ui.geometry.Size(p.size, p.size / 2f)
                                )
                            }
                            else -> { // Triangle
                                val path = androidx.compose.ui.graphics.Path().apply {
                                    moveTo(finalX, finalY - p.size / 2f)
                                    lineTo(finalX - p.size / 2f, finalY + p.size / 2f)
                                    lineTo(finalX + p.size / 2f, finalY + p.size / 2f)
                                    close()
                                }
                                drawPath(path = path, color = paintColor)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MediaSwapGalleryPanel(
    viewModel: com.ritvyom.yashoraReelgenerator.presentation.viewmodels.MainViewModel,
    selectedSceneIndex: Int,
    currentScene: Scene,
    aspectRatio: String,
    appLanguageState: String
) {
    var searchQuery by remember(currentScene.sceneNumber) { 
        mutableStateOf(currentScene.visualPrompt.substringBefore("(").trim().ifEmpty { "nature" }) 
    }
    var mediaType by remember(currentScene.sceneNumber) { 
        mutableStateOf(currentScene.mediaType.uppercase()) 
    }

    val suggestions by viewModel.alternativeSuggestions.collectAsState()
    val isSearching by viewModel.isSearchingAlternatives.collectAsState()
    val searchError by viewModel.alternativeSearchError.collectAsState()
    val context = LocalContext.current

    // Trigger initial search automatically for convenience!
    LaunchedEffect(currentScene.sceneNumber, mediaType) {
        viewModel.fetchAlternativeSuggestions(searchQuery, mediaType, aspectRatio)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "Swap Media for Scene #${currentScene.sceneNumber}".localize(appLanguageState),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(10.dp),
                textStyle = LocalTextStyle.current.copy(fontSize = 11.sp),
                singleLine = true,
                placeholder = { Text("Search keyword...".localize(appLanguageState), fontSize = 11.sp) }
            )

            // Image vs Video Toggle
            Button(
                onClick = {
                    mediaType = if (mediaType == "VIDEO") "IMAGE" else "VIDEO"
                },
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                ),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                modifier = Modifier.height(42.dp)
            ) {
                Text(
                    text = if (mediaType == "VIDEO") "📹 Video" else "🖼️ Photo",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Button(
                onClick = {
                    viewModel.fetchAlternativeSuggestions(searchQuery, mediaType, aspectRatio)
                },
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp),
                modifier = Modifier.height(42.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search Alternatives".localize(appLanguageState),
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        if (isSearching) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Fetching alternative media options...".localize(appLanguageState), fontSize = 10.sp, color = Color.Gray)
                }
            }
        } else if (searchError != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(12.dp)
                ) {
                    Icon(imageVector = Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = (searchError ?: "Search failed").localize(appLanguageState),
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            if (suggestions.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(130.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No options found. Try typing a simpler keyword like 'nature', 'city' or 'cat'.".localize(appLanguageState),
                        fontSize = 10.sp,
                        color = Color.Gray,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            } else {
                Text(
                    text = "Tap on an item to select it as the new backdrop:".localize(appLanguageState),
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
                    columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(3),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                ) {
                    items(suggestions.size) { index ->
                        val itemUrl = suggestions[index]
                        val isCurrent = currentScene.mediaPath == itemUrl
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .border(
                                    width = if (isCurrent) 2.5.dp else 1.dp,
                                    color = if (isCurrent) MaterialTheme.colorScheme.primary else Color.Transparent,
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .clickable {
                                    viewModel.swapSceneMedia(selectedSceneIndex, itemUrl, mediaType)
                                    Toast.makeText(context, "Media updated successfully!".localize(appLanguageState), Toast.LENGTH_SHORT).show()
                                }
                        ) {
                            if (mediaType == "VIDEO") {
                                // Video cover preview or thumbnail placeholder with play overlay
                                Box(modifier = Modifier.fillMaxSize()) {
                                    // Soft backdrop
                                    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF1C1B1F)))
                                    Icon(
                                        imageVector = Icons.Default.PlayCircle,
                                        contentDescription = "Video suggestion",
                                        tint = Color.White.copy(alpha = 0.8f),
                                        modifier = Modifier.size(32.dp).align(Alignment.Center)
                                    )
                                    Text(
                                        text = "Video Clip",
                                        fontSize = 8.sp,
                                        color = Color.LightGray,
                                        modifier = Modifier.align(Alignment.BottomCenter).padding(4.dp)
                                    )
                                }
                            } else {
                                coil.compose.AsyncImage(
                                    model = itemUrl,
                                    contentDescription = "Alternative image option",
                                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }

                            if (isCurrent) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Color.Black.copy(alpha = 0.45f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Selected",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
