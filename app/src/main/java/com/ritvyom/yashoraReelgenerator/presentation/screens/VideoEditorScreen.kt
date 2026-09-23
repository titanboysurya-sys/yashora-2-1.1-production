package com.ritvyom.yashoraReelgenerator.presentation.screens

import androidx.activity.compose.BackHandler
import androidx.activity.result.PickVisualMediaRequest
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.BorderStroke
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
import com.ritvyom.yashoraReelgenerator.presentation.components.editor.*
import com.ritvyom.yashoraReelgenerator.presentation.components.AlignmentGuidesOverlay
import com.ritvyom.yashoraReelgenerator.presentation.components.ProfessionalTransformBoundingBox
import com.ritvyom.yashoraReelgenerator.engine.canvas.CanvasInteractionController
import com.ritvyom.yashoraReelgenerator.engine.canvas.AlignmentGuide
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
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
import com.ritvyom.yashoraReelgenerator.domain.models.CanvasLayer
import com.ritvyom.yashoraReelgenerator.domain.models.getCanvasLayers
import com.ritvyom.yashoraReelgenerator.presentation.components.editor.LayersStudioSheet
import com.ritvyom.yashoraReelgenerator.presentation.components.editor.AdjustStudioSheet
import com.ritvyom.yashoraReelgenerator.presentation.components.editor.AnimationStudioSheet
import com.ritvyom.yashoraReelgenerator.presentation.components.editor.VoiceFxStudioSheet
import com.ritvyom.yashoraReelgenerator.presentation.components.MockInterstitialAdDialog
import com.ritvyom.yashoraReelgenerator.presentation.components.*
import com.ritvyom.yashoraReelgenerator.presentation.utils.EditorConstants
import com.ritvyom.yashoraReelgenerator.presentation.utils.FilterManager
import com.ritvyom.yashoraReelgenerator.presentation.utils.EffectManager
import com.ritvyom.yashoraReelgenerator.data.repository.MediaFetchingStatusMonitor
import com.ritvyom.yashoraReelgenerator.presentation.utils.SoundSynth
import com.ritvyom.yashoraReelgenerator.presentation.utils.AudioManager
import com.ritvyom.yashoraReelgenerator.presentation.utils.RenderPipeline
import com.ritvyom.yashoraReelgenerator.presentation.utils.localize
import com.ritvyom.yashoraReelgenerator.presentation.viewmodels.MainViewModel
import com.ritvyom.yashoraReelgenerator.presentation.utils.VoiceRecorderManager
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.ritvyom.yashoraReelgenerator.ui.theme.YashoraTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.drawscope.rotate

data class MediaSourceCatalogItem(
    val id: String,
    val displayName: String,
    val emoji: String,
    val keywords: List<String>
)

val ALL_MEDIA_SOURCES_CATALOG = listOf(
    MediaSourceCatalogItem("ALL", "All Sources", "🌐", emptyList()),
    MediaSourceCatalogItem("Pexels", "Pexels Motion", "🎬", listOf("pexels")),
    MediaSourceCatalogItem("Pixabay", "Pixabay Cosmos", "🌌", listOf("pixabay")),
    MediaSourceCatalogItem("Unsplash", "VividStock Prime", "📸", listOf("unsplash", "vivid")),
    MediaSourceCatalogItem("Giphy Memes", "PopMotion GIF", "🎬", listOf("giphy", "meme", "popmotion")),
    MediaSourceCatalogItem("NASA", "Cosmos DeepSpace", "🚀", listOf("nasa", "cosmos", "deepspace", "space")),
    MediaSourceCatalogItem("Wikipedia", "Atlas Historical", "🏛️", listOf("wiki", "wikimedia", "wikipedia", "atlas")),
    MediaSourceCatalogItem("OmniShield Fallback", "OmniShield Fallback", "🛡️", listOf("omni", "shield", "fallback")),
    MediaSourceCatalogItem("NHTSA vPIC", "NHTSA vPIC", "🚗", listOf("nhtsa", "vpic", "vehicle", "car", "auto")),
    MediaSourceCatalogItem("OpenFDA", "OpenFDA", "💊", listOf("openfda", "fda", "pharma", "drug", "medicine")),
    MediaSourceCatalogItem("WHO GHO", "WHO GHO", "🇺🇳", listOf("who", "gho", "health", "global")),
    MediaSourceCatalogItem("Nekos.best", "Kawaii Art Vault", "🌸", listOf("neko", "anime", "kawaii")),
    MediaSourceCatalogItem("Jikan Anime", "NeoManga Catalog", "🏷️", listOf("jikan", "manga", "neomanga")),
    MediaSourceCatalogItem("Lorem Picsum", "Horizon Scenic", "🖼️", listOf("picsum", "horizon", "scenic")),
    MediaSourceCatalogItem("Archive.org", "Chronos Chronicle", "📦", listOf("archive", "chronos")),
    MediaSourceCatalogItem("LoremFlickr", "Lumina Snapshot", "⚡", listOf("flickr", "lumina")),
    MediaSourceCatalogItem("AI Generated", "Genesis Synthetix", "🤖", listOf("ai", "genesis", "synthetix", "pollinations")),
    MediaSourceCatalogItem("TheMealDB", "Gourmet Archive", "🍕", listOf("meal", "recipe", "food", "gourmet"))
)

fun getDisplaySourceName(rawSource: String): String {
    val rawLower = rawSource.lowercase().trim()
    val match = ALL_MEDIA_SOURCES_CATALOG.drop(1).firstOrNull { catalogItem ->
        catalogItem.id.equals(rawSource, ignoreCase = true) ||
        catalogItem.keywords.any { rawLower.contains(it) }
    }
    return if (match != null) "${match.emoji} ${match.displayName}" else rawSource
}

fun matchesSourceCatalogFilter(itemSource: String, filterId: String): Boolean {
    if (filterId == "ALL") return true
    val catalogItem = ALL_MEDIA_SOURCES_CATALOG.firstOrNull { it.id == filterId } ?: return itemSource.equals(filterId, ignoreCase = true)
    val rawLower = itemSource.lowercase().trim()
    return catalogItem.id.equals(itemSource, ignoreCase = true) ||
           catalogItem.keywords.any { rawLower.contains(it) }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun VideoEditorScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit,
    onNavigateToConfig: () -> Unit = {},
    onNavigateToPreview: () -> Unit = {},
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
    val exportEtaFormatted by viewModel.exportEtaFormatted.collectAsState()
    val exportFps by viewModel.exportFps.collectAsState()
    val compiledPreviewPath by viewModel.compiledPreviewPath.collectAsState()

    val isGenerating by viewModel.isGenerating.collectAsState()
    val generationProgress by viewModel.generationProgress.collectAsState()
    val generationStatus by viewModel.generationStatus.collectAsState()

    val customMasterVoicePath by viewModel.customMasterVoicePath.collectAsState()
    val customBgMusicPath by viewModel.customBgMusicPath.collectAsState()
    val bgMusicCategory by viewModel.bgMusicCategory.collectAsState()

    var showInterstitialAd by remember { mutableStateOf(false) }
    var pendingAdAction by remember { mutableStateOf<(() -> Unit)?>(null) }

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
    var showShareDialogForSuccess by remember { mutableStateOf(false) }

    var showTrimmerDialog by remember { mutableStateOf(false) }
    var videoToTrimPath by remember { mutableStateOf<String?>(null) }
    var showWatermarkSheet by remember { mutableStateOf(false) }
    val watermarkConfigState by viewModel.watermarkConfig.collectAsState()
    var showTopBarOverflowMenu by remember { mutableStateOf(false) }

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
    var showDeleteSceneConfirmDialog by remember { mutableStateOf(false) }
    var activeToolSheet by remember { mutableStateOf<EditorTool?>(null) }
    var selectedLayerId by remember { mutableStateOf<String?>(null) }

    val youCutMediaPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris ->
        if (uris.isNotEmpty()) {
            val uriStrings = uris.map { it.toString() }
            viewModel.appendDeviceMediaToActiveProject(uriStrings, context)
            Toast.makeText(context, "Added ${uris.size} clips to timeline", Toast.LENGTH_SHORT).show()
        }
    }

    val youCutAudioPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            viewModel.editSceneNarrationAudio(selectedSceneIndex, it.toString())
            Toast.makeText(context, "Custom audio attached", Toast.LENGTH_SHORT).show()
        }
    }

    val youCutPipPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri?.let {
            viewModel.editScenePip(
                index = selectedSceneIndex,
                mediaPath = it.toString(),
                scale = 0.4f,
                position = "TOP_RIGHT",
                opacity = 1.0f
            )
            Toast.makeText(context, "PIP overlay added", Toast.LENGTH_SHORT).show()
        }
    }

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

    var sceneToReplaceIndex by remember { mutableStateOf<Int?>(null) }
    val timelineAssetPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        uri?.let { pickedUri ->
            val targetIdx = sceneToReplaceIndex ?: selectedSceneIndex
            viewModel.replaceSceneWithDeviceMedia(targetIdx, pickedUri, context)
            sceneToReplaceIndex = null
        }
    }

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
                    narrationText = "",
                    visualPrompt = "Custom User Imported Media Content",
                    subtitle = "",
                    mediaPath = pickedPath,
                    mediaType = if (isVideo) "VIDEO" else "IMAGE",
                    durationSeconds = durationSeconds
                )
                importedScenes.add(newScene)
                addedCount++
            }

            viewModel.appendScenes(importedScenes)
            android.widget.Toast.makeText(context, "Added $addedCount media items to timeline".localize(appLanguageState), android.widget.Toast.LENGTH_SHORT).show()
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
                    // Synthesizes speaking voice during playback only if scene has custom narration
                    val nextScene = activeScenes[selectedSceneIndex + 1]
                    if (nextScene.narrationText.isNotBlank() && !viewModel.isPlaceholderNarration(nextScene.narrationText)) {
                        viewModel.speakText(nextScene.narrationText)
                    }
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

    LaunchedEffect(isPlaying) {
        if (!isPlaying) {
            viewModel.stopSpeak()
            AudioManager.stopSfx("LayeredSfxTrack")
        }
    }

    DisposableEffect(activeProject?.id) {
        onDispose {
            viewModel.isPlaying.value = false
            viewModel.stopSpeak()
            AudioManager.stopAll()
        }
    }

    val currentScene: Scene? = if (selectedSceneIndex in activeScenes.indices) activeScenes[selectedSceneIndex] else null

    val hasActiveEditorOverlay = showFullScreenPlayer || showStickerDrawer || showMediaImportDrawer ||
            showTrimmerDialog || showShareDialogForSuccess || showSuccessPlayerDialog ||
            showExportSuccessDialog || showConfirmExportDialog || showCancelExportConfirmDialog ||
            showExportQualityDialog || showDeleteSceneConfirmDialog || showImportChoiceDialog ||
            showPermissionExplanationDialog || showWatermarkSheet

    BackHandler(enabled = hasActiveEditorOverlay) {
        when {
            showWatermarkSheet -> showWatermarkSheet = false
            showFullScreenPlayer -> showFullScreenPlayer = false
            showStickerDrawer -> showStickerDrawer = false
            showMediaImportDrawer -> showMediaImportDrawer = false
            showTrimmerDialog -> showTrimmerDialog = false
            showShareDialogForSuccess -> showShareDialogForSuccess = false
            showSuccessPlayerDialog -> showSuccessPlayerDialog = false
            showExportSuccessDialog -> showExportSuccessDialog = false
            showConfirmExportDialog -> showConfirmExportDialog = false
            showCancelExportConfirmDialog -> showCancelExportConfirmDialog = false
            showExportQualityDialog -> showExportQualityDialog = false
            showDeleteSceneConfirmDialog -> showDeleteSceneConfirmDialog = false
            showImportChoiceDialog -> showImportChoiceDialog = false
            showPermissionExplanationDialog -> showPermissionExplanationDialog = false
        }
    }

    YashoraTheme(themeName = "Dark") {
        Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 2.dp, end = 4.dp)
                    ) {
                        Text(
                            text = activeProject?.title ?: "Visual Timeline Editor".localize(appLanguageState),
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        val pubStyle = activeProject?.publishingStyle ?: "TikTok / Instagram Reels"
                        val visStyle = activeProject?.videoStyle ?: "Cinematic"
                        val isAutoSaving by viewModel.isAutoSaving.collectAsState()
                        val lastSaveTime by viewModel.lastAutoSaveTimestamp.collectAsState()
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "$pubStyle • $visStyle".localize(appLanguageState),
                                fontSize = 10.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (isAutoSaving) {
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "• Saving...",
                                    fontSize = 9.sp,
                                    color = YouCutOrange,
                                    fontWeight = FontWeight.Bold
                                )
                            } else if ((lastSaveTime ?: 0L) > 0L) {
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "• Auto-saved",
                                    fontSize = 9.sp,
                                    color = Color(0xFF4CAF50),
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("editor_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Default.ArrowBack,
                            contentDescription = "Back".localize(appLanguageState),
                            tint = MaterialTheme.colorScheme.secondary
                        )
                    }
                },
                actions = {
                    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
                    val isTabletOrWide = configuration.screenWidthDp >= 600
                    val isCompactPhone = configuration.screenWidthDp < 410

                    // 1. Dedicated Tune / Mix Button (Script & Config Pipeline)
                    // On wide screens and standard phones with sufficient space, show directly.
                    // On compact phones, it is cleanly accessible in the 3-dot overflow menu to prevent button overlaps.
                    if (!isCompactPhone) {
                        IconButton(
                            onClick = onNavigateToConfig,
                            modifier = Modifier
                                .size(36.dp)
                                .testTag("editor_tune_config_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = "Edit Script & Config / Mix Settings".localize(appLanguageState),
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    // On wide screens (tablets), show Refresh and Watermark directly; on phones, neatly housed in Overflow Menu
                    if (isTabletOrWide) {
                        IconButton(
                            onClick = {
                                val isOnline = com.ritvyom.yashoraReelgenerator.presentation.utils.NetworkUtils.isInternetAvailable(context)
                                if (isOnline) {
                                    pendingAdAction = { viewModel.regenerateCurrentProjectVisuals() }
                                    showInterstitialAd = true
                                } else {
                                    android.widget.Toast.makeText(context, "No Internet Connection".localize(appLanguageState), android.widget.Toast.LENGTH_LONG).show()
                                }
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Refresh Backdrops".localize(appLanguageState),
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        IconButton(
                            onClick = { showWatermarkSheet = true },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.BrandingWatermark,
                                contentDescription = "Watermark Settings".localize(appLanguageState),
                                tint = if (watermarkConfigState.isEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    // 2. Preview & Cut Button (Adaptive sizing without layout collision)
                    if (isCompactPhone) {
                        IconButton(
                            onClick = { onNavigateToPreview() },
                            modifier = Modifier
                                .size(36.dp)
                                .testTag("editor_preview_cut_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayCircle,
                                contentDescription = "Preview & Cut".localize(appLanguageState),
                                tint = Color(0xFFD0BCFF),
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    } else {
                        Surface(
                            onClick = { onNavigateToPreview() },
                            shape = RoundedCornerShape(16.dp),
                            color = Color(0xFF231A3B),
                            contentColor = Color(0xFFD0BCFF),
                            modifier = Modifier
                                .height(32.dp)
                                .testTag("editor_preview_cut_button")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayCircle,
                                    contentDescription = null,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Preview".localize(appLanguageState),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(2.dp))

                    // 3. Export Button (High-visibility Call-To-Action)
                    Button(
                        onClick = { showExportQualityDialog = true },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        shape = RoundedCornerShape(16.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                        modifier = Modifier
                            .height(32.dp)
                            .testTag("editor_export_button")
                    ) {
                        Icon(imageVector = Icons.Default.SaveAlt, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(3.dp))
                        Text("Export".localize(appLanguageState), fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }

                    // 4. More Options Overflow Menu (Clean 3-dot dropdown with zero overlap)
                    Box {
                        IconButton(
                            onClick = { showTopBarOverflowMenu = true },
                            modifier = Modifier
                                .size(36.dp)
                                .testTag("topbar_overflow_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "More Options".localize(appLanguageState),
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        DropdownMenu(
                            expanded = showTopBarOverflowMenu,
                            onDismissRequest = { showTopBarOverflowMenu = false }
                        ) {
                            // Mix / Tune option is always available in the menu for quick access
                            DropdownMenuItem(
                                text = { Text("Edit Script & Config (Mix)".localize(appLanguageState)) },
                                leadingIcon = { Icon(Icons.Default.Tune, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                                onClick = {
                                    showTopBarOverflowMenu = false
                                    onNavigateToConfig()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Refresh Backdrops".localize(appLanguageState)) },
                                leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                                onClick = {
                                    showTopBarOverflowMenu = false
                                    val isOnline = com.ritvyom.yashoraReelgenerator.presentation.utils.NetworkUtils.isInternetAvailable(context)
                                    if (isOnline) {
                                        pendingAdAction = { viewModel.regenerateCurrentProjectVisuals() }
                                        showInterstitialAd = true
                                    } else {
                                        android.widget.Toast.makeText(context, "No Internet Connection".localize(appLanguageState), android.widget.Toast.LENGTH_LONG).show()
                                    }
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Watermark Settings".localize(appLanguageState)) },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.BrandingWatermark,
                                        contentDescription = null,
                                        tint = if (watermarkConfigState.isEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                },
                                onClick = {
                                    showTopBarOverflowMenu = false
                                    showWatermarkSheet = true
                                }
                            )
                        }
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
            val configuration = androidx.compose.ui.platform.LocalConfiguration.current
            val previewHeight = when {
                configuration.screenHeightDp < 600 -> when (activeRatio) {
                    "16:9" -> 110.dp
                    "21:9" -> 85.dp
                    "1:1" -> 120.dp
                    "4:5" -> 130.dp
                    else -> 140.dp
                }
                configuration.screenWidthDp <= 340 -> when (activeRatio) {
                    "16:9" -> 130.dp
                    "21:9" -> 95.dp
                    "1:1" -> 140.dp
                    "4:5" -> 150.dp
                    else -> 160.dp
                }
                else -> when (activeRatio) {
                    "16:9" -> 150.dp
                    "21:9" -> 110.dp
                    "1:1" -> 170.dp
                    "4:5" -> 170.dp
                    else -> 200.dp
                }
            }

            // Adapt Box sizing depending on aspect ratio selection
            val containerModifier = when(activeRatio) {
                "16:9" -> Modifier.fillMaxWidth().height(previewHeight)
                "21:9" -> Modifier.fillMaxWidth().height(previewHeight)
                "1:1" -> Modifier.size(previewHeight)
                "4:5" -> Modifier.width(previewHeight * 0.8f).height(previewHeight)
                "3:4" -> Modifier.width(previewHeight * 0.75f).height(previewHeight)
                else -> Modifier.width(previewHeight * 0.5625f).height(previewHeight) // 9:16 portrait
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
                                                if (it.narrationText.isNotBlank() && !viewModel.isPlaceholderNarration(it.narrationText)) {
                                                    viewModel.speakText(it.narrationText)
                                                }
                                            }
                                        }
                                    }
                                } else if (previewDragAmount > 100f) { // Swipe Right -> Prev
                                    if (selectedSceneIndex > 0) {
                                        viewModel.selectedSceneIndex.value = selectedSceneIndex - 1
                                        if (isPlaying) {
                                            viewModel.stopSpeak()
                                            activeScenes.getOrNull(selectedSceneIndex - 1)?.let {
                                                if (it.narrationText.isNotBlank() && !viewModel.isPlaceholderNarration(it.narrationText)) {
                                                    viewModel.speakText(it.narrationText)
                                                }
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
                                                mp.isLooping = false
                                                try {
                                                    val finalVolume = 0.1f * sc.volume
                                                    mp.setVolume(finalVolume, finalVolume)
                                                } catch (e: Exception) {}
                                            }
                                            setOnCompletionListener { mp ->
                                                try {
                                                    mp.pause()
                                                    mp.seekTo(0)
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
                                // Render generated UHD image cleanly with background fill + fit foreground
                                val composeMatrix = androidx.compose.ui.graphics.ColorMatrix().apply {
                                    setToSaturation(sc.saturationValue)
                                }
                                Box(modifier = fxModifier) {
                                    coil.compose.AsyncImage(
                                        model = path,
                                        contentDescription = null,
                                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                        colorFilter = androidx.compose.ui.graphics.ColorFilter.colorMatrix(composeMatrix),
                                        modifier = Modifier.fillMaxSize().blur(20.dp)
                                    )
                                    Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f)))
                                    coil.compose.AsyncImage(
                                        model = path,
                                        contentDescription = sc.visualPrompt,
                                        contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                                        colorFilter = androidx.compose.ui.graphics.ColorFilter.colorMatrix(composeMatrix),
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
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

                            // Dynamic Brightness, Contrast & Warmth Color Grading simulation overlays (for perfect realtime feedback!)
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

                            // Dynamic Exposure simulation overlay
                            if (sc.exposureValue != 0f) {
                                val expColor = if (sc.exposureValue > 0f) Color.White else Color.Black
                                val expAlpha = (kotlin.math.abs(sc.exposureValue) * 0.4f).coerceIn(0f, 0.6f)
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(expColor.copy(alpha = expAlpha))
                                )
                            }

                            // Dynamic Tint simulation overlay (Green to Magenta)
                            if (sc.tintValue != 0f) {
                                val tintC = if (sc.tintValue > 0f) Color(0xFFFF007F) else Color(0xFF00FF66)
                                val tintA = (kotlin.math.abs(sc.tintValue) * 0.2f).coerceIn(0f, 0.35f)
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(tintC.copy(alpha = tintA))
                                )
                            }

                            // Dynamic Vignette (CapCut & Filmora cinema vignette)
                            if (sc.vignetteValue > 0f) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(
                                            Brush.radialGradient(
                                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = (sc.vignetteValue * 0.85f).coerceIn(0f, 0.95f))),
                                                radius = 900f
                                            )
                                        )
                                )
                            }

                            // Freeze Frame badge indicator
                            if (sc.isFrozen) {
                                Box(
                                    modifier = Modifier
                                        .padding(8.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Color(0xFF0288D1).copy(alpha = 0.85f))
                                        .padding(horizontal = 8.dp, vertical = 3.dp)
                                ) {
                                    Text("❄️ FREEZE (${sc.freezeDurationSeconds.toInt()}s)", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            }

                            // Reverse Playback badge indicator
                            if (sc.isReversed) {
                                Box(
                                    modifier = Modifier
                                        .padding(top = if (sc.isFrozen) 34.dp else 8.dp, start = 8.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Color(0xFFE64A19).copy(alpha = 0.85f))
                                        .padding(horizontal = 8.dp, vertical = 3.dp)
                                ) {
                                    Text("⏪ REVERSED", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
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

                        // Interactive Text, Sticker & Subtitle overlays on top of video canvas
                        BoxWithConstraints(
                            modifier = Modifier
                                .fillMaxSize()
                        ) {
                            val containerWidthPx = constraints.maxWidth.toFloat().coerceAtLeast(1f)
                            val containerHeightPx = constraints.maxHeight.toFloat().coerceAtLeast(1f)
                            val canvasController = remember { CanvasInteractionController() }
                            var activeGuidelines by remember { mutableStateOf<List<AlignmentGuide>>(emptyList()) }
                            var showLiveColorPicker by remember(sc.sceneNumber) { mutableStateOf(false) }

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

                            val canvasLayers = remember(sc.layersJson, sc.textOverlay, sc.stickerName) {
                                sc.getCanvasLayers()
                            }

                            // Render all Canvas Layers in depth order (0 = back, last = front)
                            canvasLayers.forEachIndexed { layerIndex, layer ->
                                if (layer.isVisible) {
                                    val isLayerSelected = (selectedLayerId == layer.id)
                                    val isFrontmost = (layerIndex == canvasLayers.size - 1)
                                    val isBackmost = (layerIndex == 0)

                                    if (layer.type == "TEXT" && layer.content.isNotEmpty()) {
                                        val textOffsetX = (maxWidth * layer.x - 50.dp).coerceIn(0.dp, (maxWidth - 80.dp).coerceAtLeast(0.dp))
                                        val textOffsetY = (maxHeight * layer.y - 18.dp).coerceIn(0.dp, (maxHeight - 40.dp).coerceAtLeast(0.dp))
                                        val layerFontWeight = when (layer.font) {
                                            "Display Bold" -> FontWeight.Black
                                            "TikTok Style" -> FontWeight.ExtraBold
                                            "Insta Premium" -> FontWeight.SemiBold
                                            else -> FontWeight.Bold
                                        }
                                        val layerFontStyle = if (layer.font == "Insta Premium") {
                                            androidx.compose.ui.text.font.FontStyle.Italic
                                        } else {
                                            androidx.compose.ui.text.font.FontStyle.Normal
                                        }
                                        val layerFontFamily = when (layer.font) {
                                            "Sans-Serif" -> androidx.compose.ui.text.font.FontFamily.SansSerif
                                            "Serif" -> androidx.compose.ui.text.font.FontFamily.Serif
                                            "Monospace" -> androidx.compose.ui.text.font.FontFamily.Monospace
                                            else -> androidx.compose.ui.text.font.FontFamily.Default
                                        }

                                        Column(
                                            modifier = Modifier
                                                .align(Alignment.TopStart)
                                                .offset(x = textOffsetX, y = textOffsetY)
                                                .graphicsLayer(
                                                    scaleX = layer.scale,
                                                    scaleY = layer.scale,
                                                    rotationZ = layer.rotation
                                                ),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            // Action Bar when selected (Reorder depth, Color, Zoom, Rotate, Delete)
                                            if (isLayerSelected) {
                                                Row(
                                                    modifier = Modifier
                                                        .background(Color(0xE614141E), RoundedCornerShape(16.dp))
                                                        .border(0.5.dp, YouCutOrange.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
                                                        .padding(horizontal = 6.dp, vertical = 2.dp),
                                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    // Palette trigger for Live Color Picker Overlay
                                                    Box(
                                                        modifier = Modifier
                                                            .size(20.dp)
                                                            .clip(CircleShape)
                                                            .background(parseHexColor(layer.color))
                                                            .border(1.2.dp, Color.White, CircleShape)
                                                            .clickable { showLiveColorPicker = !showLiveColorPicker },
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.Palette,
                                                            contentDescription = "Live Color Picker",
                                                            tint = if (layer.color.equals("#FFFFFF", ignoreCase = true)) Color.Black else Color.White,
                                                            modifier = Modifier.size(12.dp)
                                                        )
                                                    }

                                                    listOf("#FFFFFF", "#FFFF00", "#FF5722", "#00E5FF", "#FF007F", "#00E676", "#9C27B0").forEach { hex ->
                                                        val c = try { Color(android.graphics.Color.parseColor(hex)) } catch (e: Exception) { Color.White }
                                                        Box(
                                                            modifier = Modifier
                                                                .size(15.dp)
                                                                .clip(CircleShape)
                                                                .background(c)
                                                                .border(
                                                                    1.dp,
                                                                    if (layer.color.equals(hex, ignoreCase = true)) YouCutOrange else Color.Gray.copy(alpha = 0.5f),
                                                                    CircleShape
                                                                )
                                                                .clickable {
                                                                    viewModel.updateLayerStyle(selectedSceneIndex, layer.id, color = hex)
                                                                    viewModel.editSceneOverlayColor(selectedSceneIndex, hex)
                                                                }
                                                        )
                                                    }
                                                    Spacer(modifier = Modifier.width(2.dp))

                                                    // Bring Forward in Depth (🔼)
                                                    Box(
                                                        modifier = Modifier
                                                            .size(18.dp)
                                                            .clip(CircleShape)
                                                            .background(if (!isFrontmost) Color(0xFF2E2E3E) else Color(0xFF1E1E28))
                                                            .clickable(enabled = !isFrontmost) {
                                                                viewModel.bringLayerForward(selectedSceneIndex, layer.id)
                                                            },
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Icon(Icons.Default.ArrowUpward, contentDescription = "Bring Forward", tint = if (!isFrontmost) Color.White else Color.Gray, modifier = Modifier.size(11.dp))
                                                    }

                                                    // Send Backward in Depth (🔽)
                                                    Box(
                                                        modifier = Modifier
                                                            .size(18.dp)
                                                            .clip(CircleShape)
                                                            .background(if (!isBackmost) Color(0xFF2E2E3E) else Color(0xFF1E1E28))
                                                            .clickable(enabled = !isBackmost) {
                                                                viewModel.sendLayerBackward(selectedSceneIndex, layer.id)
                                                            },
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Icon(Icons.Default.ArrowDownward, contentDescription = "Send Backward", tint = if (!isBackmost) Color.White else Color.Gray, modifier = Modifier.size(11.dp))
                                                    }

                                                    // Open Layers Panel
                                                    Box(
                                                        modifier = Modifier
                                                            .size(18.dp)
                                                            .clip(CircleShape)
                                                            .background(YouCutOrange.copy(alpha = 0.8f))
                                                            .clickable { activeToolSheet = EditorTool.LAYERS },
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Icon(Icons.Default.Layers, contentDescription = "Layers Panel", tint = Color.White, modifier = Modifier.size(11.dp))
                                                    }

                                                    // Zoom +
                                                    Box(
                                                        modifier = Modifier
                                                            .size(18.dp)
                                                            .clip(CircleShape)
                                                            .background(Color(0xFF2E2E3E))
                                                            .clickable {
                                                                val ns = (layer.scale * 1.15f).coerceAtMost(4.0f)
                                                                viewModel.updateLayerTransform(selectedSceneIndex, layer.id, layer.x, layer.y, ns, layer.rotation, persist = true)
                                                            },
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Text("+", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                    }
                                                    // Zoom -
                                                    Box(
                                                        modifier = Modifier
                                                            .size(18.dp)
                                                            .clip(CircleShape)
                                                            .background(Color(0xFF2E2E3E))
                                                            .clickable {
                                                                val ns = (layer.scale * 0.85f).coerceAtLeast(0.3f)
                                                                viewModel.updateLayerTransform(selectedSceneIndex, layer.id, layer.x, layer.y, ns, layer.rotation, persist = true)
                                                            },
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Text("-", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                    }
                                                    // Rotate Counter-Clockwise ⟲ (-15°)
                                                    Box(
                                                        modifier = Modifier
                                                            .size(18.dp)
                                                            .clip(CircleShape)
                                                            .background(Color(0xFF2E2E3E))
                                                            .clickable {
                                                                viewModel.updateLayerTransform(selectedSceneIndex, layer.id, layer.x, layer.y, layer.scale, layer.rotation - 15f, persist = true)
                                                            },
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Text("⟲", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                                    }
                                                    // Rotate Clockwise ⟳ (+15°)
                                                    Box(
                                                        modifier = Modifier
                                                            .size(18.dp)
                                                            .clip(CircleShape)
                                                            .background(Color(0xFF2E2E3E))
                                                            .clickable {
                                                                viewModel.updateLayerTransform(selectedSceneIndex, layer.id, layer.x, layer.y, layer.scale, layer.rotation + 15f, persist = true)
                                                            },
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Text("⟳", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                                    }
                                                    // Duplicate layer (⧉)
                                                    Box(
                                                        modifier = Modifier
                                                            .size(18.dp)
                                                            .clip(CircleShape)
                                                            .background(Color(0xFF2E2E3E))
                                                            .clickable {
                                                                viewModel.duplicateLayer(selectedSceneIndex, layer.id)
                                                            },
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Icon(Icons.Default.ContentCopy, contentDescription = "Duplicate", tint = Color.White, modifier = Modifier.size(10.dp))
                                                    }
                                                    // Delete layer
                                                    Box(
                                                        modifier = Modifier
                                                            .size(18.dp)
                                                            .clip(CircleShape)
                                                            .background(Color.Red.copy(alpha = 0.8f))
                                                            .clickable {
                                                                viewModel.deleteLayer(selectedSceneIndex, layer.id)
                                                                selectedLayerId = null
                                                            },
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Icon(Icons.Default.Close, contentDescription = "Delete", tint = Color.White, modifier = Modifier.size(11.dp))
                                                    }
                                                }
                                                Spacer(modifier = Modifier.height(3.dp))
                                            }

                                            // The draggable, pinchable, rotatable text pill
                                            Box(
                                                modifier = Modifier
                                                    .pointerInput(selectedSceneIndex, sc.sceneNumber, layer.id) {
                                                        detectTransformGestures { _, pan, zoom, rotation ->
                                                            val rawX = layer.x + pan.x / containerWidthPx
                                                            val rawY = layer.y + pan.y / containerHeightPx
                                                            val snap = canvasController.computeSnap(rawX, rawY, layer.rotation + rotation)
                                                            activeGuidelines = snap.activeGuides
                                                            val newScale = canvasController.computeScale(layer.scale, zoom)
                                                            viewModel.updateLayerTransform(selectedSceneIndex, layer.id, snap.snappedX, snap.snappedY, newScale, snap.snappedRotation, persist = false)
                                                            selectedLayerId = layer.id
                                                        }
                                                    }
                                                    .clickable {
                                                        selectedLayerId = if (selectedLayerId == layer.id) null else layer.id
                                                    }
                                                    .then(
                                                        if (isLayerSelected) Modifier.border(1.5.dp, YouCutOrange, RoundedCornerShape(6.dp))
                                                        else Modifier
                                                    )
                                                    .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(6.dp))
                                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                                            ) {
                                                Text(
                                                    text = layer.content.uppercase(),
                                                    fontWeight = layerFontWeight,
                                                    fontFamily = layerFontFamily,
                                                    fontStyle = layerFontStyle,
                                                    fontSize = 12.sp,
                                                    color = parseHexColor(layer.color),
                                                    textAlign = TextAlign.Center
                                                )
                                            }
                                        }
                                    } else if (layer.type == "STICKER" && layer.content.isNotEmpty() && layer.content != "None") {
                                        val stOffsetX = (maxWidth * layer.x - 25.dp).coerceIn(0.dp, (maxWidth - 50.dp).coerceAtLeast(0.dp))
                                        val stOffsetY = (maxHeight * layer.y - 25.dp).coerceIn(0.dp, (maxHeight - 50.dp).coerceAtLeast(0.dp))

                                        Column(
                                            modifier = Modifier
                                                .align(Alignment.TopStart)
                                                .offset(x = stOffsetX, y = stOffsetY)
                                                .graphicsLayer(
                                                    scaleX = layer.scale,
                                                    scaleY = layer.scale,
                                                    rotationZ = layer.rotation
                                                ),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            if (isLayerSelected) {
                                                Row(
                                                    modifier = Modifier
                                                        .background(Color(0xE614141E), RoundedCornerShape(12.dp))
                                                        .border(0.5.dp, YouCutOrange.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                                                        .padding(horizontal = 4.dp, vertical = 2.dp),
                                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    // Bring Forward (🔼)
                                                    Box(
                                                        modifier = Modifier
                                                            .size(18.dp)
                                                            .clip(CircleShape)
                                                            .background(if (!isFrontmost) Color(0xFF2E2E3E) else Color(0xFF1E1E28))
                                                            .clickable(enabled = !isFrontmost) {
                                                                viewModel.bringLayerForward(selectedSceneIndex, layer.id)
                                                            },
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Icon(Icons.Default.ArrowUpward, contentDescription = "Bring Forward", tint = if (!isFrontmost) Color.White else Color.Gray, modifier = Modifier.size(11.dp))
                                                    }

                                                    // Send Backward (🔽)
                                                    Box(
                                                        modifier = Modifier
                                                            .size(18.dp)
                                                            .clip(CircleShape)
                                                            .background(if (!isBackmost) Color(0xFF2E2E3E) else Color(0xFF1E1E28))
                                                            .clickable(enabled = !isBackmost) {
                                                                viewModel.sendLayerBackward(selectedSceneIndex, layer.id)
                                                            },
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Icon(Icons.Default.ArrowDownward, contentDescription = "Send Backward", tint = if (!isBackmost) Color.White else Color.Gray, modifier = Modifier.size(11.dp))
                                                    }

                                                    // Open Layers Panel
                                                    Box(
                                                        modifier = Modifier
                                                            .size(18.dp)
                                                            .clip(CircleShape)
                                                            .background(YouCutOrange.copy(alpha = 0.8f))
                                                            .clickable { activeToolSheet = EditorTool.LAYERS },
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Icon(Icons.Default.Layers, contentDescription = "Layers Panel", tint = Color.White, modifier = Modifier.size(11.dp))
                                                    }

                                                    // Zoom +
                                                    Box(
                                                        modifier = Modifier
                                                            .size(18.dp)
                                                            .clip(CircleShape)
                                                            .background(Color(0xFF2E2E3E))
                                                            .clickable {
                                                                val ns = (layer.scale * 1.15f).coerceAtMost(4.0f)
                                                                viewModel.updateLayerTransform(selectedSceneIndex, layer.id, layer.x, layer.y, ns, layer.rotation, persist = true)
                                                            },
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Text("+", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                    }
                                                    // Zoom -
                                                    Box(
                                                        modifier = Modifier
                                                            .size(18.dp)
                                                            .clip(CircleShape)
                                                            .background(Color(0xFF2E2E3E))
                                                            .clickable {
                                                                val ns = (layer.scale * 0.85f).coerceAtLeast(0.3f)
                                                                viewModel.updateLayerTransform(selectedSceneIndex, layer.id, layer.x, layer.y, ns, layer.rotation, persist = true)
                                                            },
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Text("-", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                    }
                                                    // Rotate Counter-Clockwise ⟲ (-15°)
                                                    Box(
                                                        modifier = Modifier
                                                            .size(18.dp)
                                                            .clip(CircleShape)
                                                            .background(Color(0xFF2E2E3E))
                                                            .clickable {
                                                                viewModel.updateLayerTransform(selectedSceneIndex, layer.id, layer.x, layer.y, layer.scale, layer.rotation - 15f, persist = true)
                                                            },
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Text("⟲", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                                    }
                                                    // Rotate Clockwise ⟳ (+15°)
                                                    Box(
                                                        modifier = Modifier
                                                            .size(18.dp)
                                                            .clip(CircleShape)
                                                            .background(Color(0xFF2E2E3E))
                                                            .clickable {
                                                                viewModel.updateLayerTransform(selectedSceneIndex, layer.id, layer.x, layer.y, layer.scale, layer.rotation + 15f, persist = true)
                                                            },
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Text("⟳", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                                    }
                                                    // Duplicate sticker (⧉)
                                                    Box(
                                                        modifier = Modifier
                                                            .size(18.dp)
                                                            .clip(CircleShape)
                                                            .background(Color(0xFF2E2E3E))
                                                            .clickable {
                                                                viewModel.duplicateLayer(selectedSceneIndex, layer.id)
                                                            },
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Icon(Icons.Default.ContentCopy, contentDescription = "Duplicate", tint = Color.White, modifier = Modifier.size(10.dp))
                                                    }
                                                    // Delete sticker
                                                    Box(
                                                        modifier = Modifier
                                                            .size(18.dp)
                                                            .clip(CircleShape)
                                                            .background(Color.Red.copy(alpha = 0.8f))
                                                            .clickable {
                                                                viewModel.deleteLayer(selectedSceneIndex, layer.id)
                                                                selectedLayerId = null
                                                            },
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Icon(Icons.Default.Close, contentDescription = "Delete", tint = Color.White, modifier = Modifier.size(11.dp))
                                                    }
                                                }
                                                Spacer(modifier = Modifier.height(2.dp))
                                            }

                                            Box(
                                                modifier = Modifier
                                                    .pointerInput(selectedSceneIndex, sc.sceneNumber, layer.id) {
                                                        detectTransformGestures { _, pan, zoom, rotation ->
                                                            val rawX = layer.x + pan.x / containerWidthPx
                                                            val rawY = layer.y + pan.y / containerHeightPx
                                                            val snap = canvasController.computeSnap(rawX, rawY, layer.rotation + rotation)
                                                            activeGuidelines = snap.activeGuides
                                                            val newScale = canvasController.computeScale(layer.scale, zoom)
                                                            viewModel.updateLayerTransform(selectedSceneIndex, layer.id, snap.snappedX, snap.snappedY, newScale, snap.snappedRotation, persist = false)
                                                            selectedLayerId = layer.id
                                                        }
                                                    }
                                                    .clickable {
                                                        selectedLayerId = if (selectedLayerId == layer.id) null else layer.id
                                                    }
                                                    .then(
                                                        if (isLayerSelected) Modifier.border(1.5.dp, YouCutOrange, RoundedCornerShape(8.dp)).padding(2.dp)
                                                        else Modifier.padding(2.dp)
                                                    )
                                            ) {
                                                Text(
                                                    text = layer.content,
                                                    fontSize = 28.sp,
                                                    textAlign = TextAlign.Center
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // Quick Floating Layers Chip on Video Canvas
                            if (canvasLayers.isNotEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(8.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color.Black.copy(alpha = 0.65f))
                                        .border(0.8.dp, YouCutOrange.copy(alpha = 0.7f), RoundedCornerShape(12.dp))
                                        .clickable { activeToolSheet = EditorTool.LAYERS }
                                        .padding(horizontal = 8.dp, vertical = 4.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(Icons.Default.Layers, contentDescription = "Layers", tint = YouCutOrange, modifier = Modifier.size(13.dp))
                                        Text("${canvasLayers.size} Layers", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }

                            // Interactive PIP Overlay on Canvas
                            val pipPath = sc.pipMediaPath
                            if (!pipPath.isNullOrEmpty()) {
                                val isPipSelected = (selectedLayerId == "layer_pip_${sc.sceneNumber}")
                                val pipOffsetX = (maxWidth * sc.pipX - 60.dp).coerceIn(0.dp, (maxWidth - 80.dp).coerceAtLeast(0.dp))
                                val pipOffsetY = (maxHeight * sc.pipY - 40.dp).coerceIn(0.dp, (maxHeight - 60.dp).coerceAtLeast(0.dp))

                                Column(
                                    modifier = Modifier
                                        .align(Alignment.TopStart)
                                        .offset(x = pipOffsetX, y = pipOffsetY)
                                        .graphicsLayer(
                                            scaleX = sc.pipScale,
                                            scaleY = sc.pipScale
                                        ),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    ProfessionalTransformBoundingBox(
                                        isSelected = isPipSelected,
                                        scale = 1.0f,
                                        rotation = 0f,
                                        boxWidth = 120.dp,
                                        boxHeight = 80.dp,
                                        onScaleDelta = { scaleDelta ->
                                            val newScale = (sc.pipScale * scaleDelta).coerceIn(0.2f, 2.5f)
                                            viewModel.updatePipTransform(selectedSceneIndex, sc.pipX, sc.pipY, newScale, persist = true)
                                        },
                                        onDelete = {
                                            viewModel.deletePip(selectedSceneIndex)
                                            selectedLayerId = null
                                        },
                                        onDuplicate = {
                                            Toast.makeText(context, "PIP duplicated to next scene", Toast.LENGTH_SHORT).show()
                                        }
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(120.dp, 80.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(Color(0xFF1B1B26))
                                                .pointerInput(selectedSceneIndex, sc.sceneNumber) {
                                                    detectTransformGestures { _, pan, zoom, _ ->
                                                        val rawX = sc.pipX + pan.x / containerWidthPx
                                                        val rawY = sc.pipY + pan.y / containerHeightPx
                                                        val snap = canvasController.computeSnap(rawX, rawY, 0f)
                                                        activeGuidelines = snap.activeGuides
                                                        val newScale = (sc.pipScale * zoom).coerceIn(0.2f, 2.5f)
                                                        viewModel.updatePipTransform(selectedSceneIndex, snap.snappedX, snap.snappedY, newScale, persist = false)
                                                        selectedLayerId = "layer_pip_${sc.sceneNumber}"
                                                    }
                                                }
                                                .clickable {
                                                    selectedLayerId = if (isPipSelected) null else "layer_pip_${sc.sceneNumber}"
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                Icon(
                                                    imageVector = Icons.Default.PictureInPicture,
                                                    contentDescription = "PIP Media",
                                                    tint = YouCutOrange,
                                                    modifier = Modifier.size(24.dp)
                                                )
                                                Text(
                                                    text = "PIP Overlay",
                                                    color = Color.White,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // Alignment snap guides overlay (crosshairs and safe lines)
                            AlignmentGuidesOverlay(
                                activeGuides = activeGuidelines,
                                containerWidthPx = containerWidthPx,
                                containerHeightPx = containerHeightPx
                            )

                            // 3. Subtitles (Dynamic live styling, color & background)
                            if (sc.subtitle.isNotEmpty()) {
                                var isSubtitleActive by remember(sc.sceneNumber) { mutableStateOf(false) }
                                val subTextColor = parseHexColor(sc.subtitleColor.ifEmpty { "#FFFFFF" })
                                val subBgColor = parseHexColor(sc.subtitleBgColor.ifEmpty { "#B3000000" })

                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                        .padding(bottom = 8.dp, start = 12.dp, end = 12.dp)
                                ) {
                                    if (isSubtitleActive) {
                                        Row(
                                            modifier = Modifier
                                                .background(Color(0xE614141E), RoundedCornerShape(16.dp))
                                                .border(0.5.dp, YouCutOrange.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
                                                .padding(horizontal = 6.dp, vertical = 2.dp),
                                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            listOf("#FFFFFF", "#FFFF00", "#FF5722", "#00E5FF", "#FF007F", "#00E676", "#FFD700").forEach { hex ->
                                                val c = try { Color(android.graphics.Color.parseColor(hex)) } catch (e: Exception) { Color.White }
                                                Box(
                                                    modifier = Modifier
                                                        .size(15.dp)
                                                        .clip(CircleShape)
                                                        .background(c)
                                                        .border(
                                                            1.dp,
                                                            if (sc.subtitleColor.equals(hex, ignoreCase = true)) YouCutOrange else Color.Gray.copy(alpha = 0.5f),
                                                            CircleShape
                                                        )
                                                        .clickable {
                                                            viewModel.editSceneSubtitleColor(selectedSceneIndex, hex)
                                                        }
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(3.dp))
                                    }

                                    Text(
                                        text = sc.subtitle,
                                        color = subTextColor,
                                        fontSize = 11.sp,
                                        fontWeight = curFontWeight,
                                        fontFamily = curFontFamily,
                                        fontStyle = curFontStyle,
                                        textAlign = TextAlign.Center,
                                        lineHeight = 15.sp,
                                        modifier = Modifier
                                            .clickable { isSubtitleActive = !isSubtitleActive }
                                            .background(subBgColor, RoundedCornerShape(6.dp))
                                            .padding(horizontal = 10.dp, vertical = 5.dp)
                                    )
                                }
                            }

                            // Live watermark overlay on video canvas
                            WatermarkOverlay(
                                config = watermarkConfigState,
                                modifier = Modifier.fillMaxSize()
                            )

                            // Live Color Picker Overlay docked on video canvas for real-time adjustments
                            if (showLiveColorPicker) {
                                val activeLayerColor = canvasLayers.find { it.id == selectedLayerId }?.color ?: sc.overlayColor.ifEmpty { "#FFFFFF" }
                                LiveColorPickerOverlay(
                                    currentColorHex = activeLayerColor,
                                    onColorChanged = { newHex ->
                                        viewModel.editSceneOverlayColor(selectedSceneIndex, newHex)
                                        selectedLayerId?.let { id ->
                                            viewModel.updateLayerStyle(selectedSceneIndex, id, color = newHex)
                                        }
                                    },
                                    onClose = { showLiveColorPicker = false },
                                    appLanguage = appLanguageState,
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                        .padding(bottom = 6.dp)
                                )
                            }
                        }
                    } ?: Text("Empty Track Buffer".localize(appLanguageState), color = Color.Gray)
                }

                // Small Player Controls overlaying inside panel
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(10.dp)
                ) {
                    // Media Fetching Status Pill on Top-Start corner of Canvas
                    val activeLiveStatus = currentScene?.let { MediaFetchingStatusMonitor.statusMap.value[it.sceneNumber] }
                    val activeCurrentStatus = activeLiveStatus?.status ?: currentScene?.mediaFetchStatus ?: if (!currentScene?.mediaPath.isNullOrEmpty()) "SUCCESS" else "PENDING"
                    val activeSource = activeLiveStatus?.sourceUsed ?: currentScene?.mediaSourceUsed
                    val isActiveFailed = activeCurrentStatus == "FAILED"
                    val isActivePending = activeCurrentStatus == "PENDING"

                    Surface(
                        onClick = {
                            if (isActiveFailed) {
                                viewModel.refetchSceneMedia(selectedSceneIndex)
                            }
                        },
                        shape = RoundedCornerShape(20.dp),
                        color = if (isActiveFailed) Color(0xFFB71C1C).copy(alpha = 0.88f) else Color.Black.copy(alpha = 0.82f),
                        border = BorderStroke(1.2.dp, if (isActiveFailed) Color(0xFFFF5252) else if (isActivePending) MaterialTheme.colorScheme.tertiary else Color.White.copy(alpha = 0.25f)),
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .testTag("canvas_media_fetching_status_pill")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            if (isActiveFailed) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = "Failed",
                                    tint = Color(0xFFFF5252),
                                    modifier = Modifier.size(13.dp)
                                )
                                Text(
                                    text = "Fetch Failed • Retry".localize(appLanguageState),
                                    color = Color.White,
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Retry",
                                    tint = Color.White,
                                    modifier = Modifier.size(12.dp)
                                )
                            } else if (isActivePending) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(10.dp),
                                    strokeWidth = 1.5.dp,
                                    color = MaterialTheme.colorScheme.tertiary
                                )
                                Text(
                                    text = "Fetching...".localize(appLanguageState),
                                    color = Color.White,
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "Source",
                                    tint = Color(0xFF69F0AE),
                                    modifier = Modifier.size(12.dp)
                                )
                                Text(
                                    text = activeSource ?: "Online Stock",
                                    color = Color.White,
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }

                    // Floating Quick Replace Button on Top-End corner of Canvas
                    Surface(
                        onClick = { activeToolSheet = EditorTool.REPLACE },
                        shape = RoundedCornerShape(20.dp),
                        color = Color.Black.copy(alpha = 0.82f),
                        border = BorderStroke(1.2.dp, YouCutOrange),
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .testTag("canvas_replace_clip_pill")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.SwapHoriz,
                                contentDescription = "Replace Clip".localize(appLanguageState),
                                tint = YouCutOrange,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Replace Clip".localize(appLanguageState),
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.align(Alignment.BottomEnd),
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
                                    currentScene?.let {
                                        if (it.narrationText.isNotBlank() && !viewModel.isPlaceholderNarration(it.narrationText)) {
                                            viewModel.speakText(it.narrationText)
                                        }
                                    }
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
                val compiledPreviewPath by viewModel.compiledPreviewPath.collectAsState()
                val activeVideoPath = remember(exportedPath, compiledPreviewPath, activeProject) {
                    val projId = activeProject?.id
                    val resolvedFile = com.ritvyom.yashoraReelgenerator.presentation.utils.VideoFileManager.resolvePlayableFile(
                        context = context,
                        filePath = if (!compiledPreviewPath.isNullOrEmpty()) compiledPreviewPath else exportedPath,
                        projectId = projId
                    )
                    resolvedFile?.absolutePath ?: exportedPath
                }
                if (activeVideoPath.isNotEmpty()) {
                    val videoUri = getPlayableUri(context, activeVideoPath)
                    if (videoUri != null) {
                        Box(modifier = containerModifier) {
                            ComposedVideoPlayer(
                                videoUri = videoUri,
                                appLanguage = appLanguageState,
                                modifier = Modifier.fillMaxSize(),
                                filePath = activeVideoPath,
                                autoPlay = false
                            )
                            Surface(
                                onClick = {
                                    videoToTrimPath = activeVideoPath
                                    showTrimmerDialog = true
                                },
                                shape = RoundedCornerShape(20.dp),
                                color = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(8.dp)
                                    .testTag("composed_trim_button")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ContentCut,
                                        contentDescription = "Trim",
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Trim ✂️".localize(appLanguageState),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            // Full Media3 Preview, Trim & Reorder button
                            Surface(
                                onClick = { onNavigateToPreview() },
                                shape = RoundedCornerShape(20.dp),
                                color = Color(0xFF231A3B),
                                contentColor = Color(0xFFD0BCFF),
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .padding(8.dp)
                                    .testTag("composed_open_full_preview_button")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PlayCircle,
                                        contentDescription = "Preview & Cut",
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Media3 Cut 🎬".localize(appLanguageState),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    } else {
                        ComposedVideoPlaceholder(
                            appLanguage = appLanguageState,
                            onExportClick = { showExportQualityDialog = true },
                            onPreviewCutClick = { onNavigateToPreview() },
                            modifier = containerModifier
                        )
                    }
                } else {
                    ComposedVideoPlaceholder(
                        appLanguage = appLanguageState,
                        onExportClick = { showExportQualityDialog = true },
                        onPreviewCutClick = { onNavigateToPreview() },
                        modifier = containerModifier
                    )
                }
            }
        }

            // Quick Access Bar to Replace Current Scene Visual
            Surface(
                onClick = { activeToolSheet = EditorTool.REPLACE },
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF1B1428),
                border = BorderStroke(1.dp, YouCutOrange.copy(alpha = 0.65f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 3.dp)
                    .testTag("quick_replace_scene_card")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(YouCutOrange.copy(alpha = 0.22f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.SwapHoriz,
                                contentDescription = null,
                                tint = YouCutOrange,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "${"Replace Scene #".localize(appLanguageState)}${selectedSceneIndex + 1} Visual",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Search online footage or pick from device files".localize(appLanguageState),
                                color = Color.LightGray.copy(alpha = 0.85f),
                                fontSize = 9.5.sp
                            )
                        }
                    }
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = YouCutOrange,
                        modifier = Modifier.size(18.dp)
                    )
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
                                            if (it.narrationText.isNotBlank() && !viewModel.isPlaceholderNarration(it.narrationText)) {
                                                viewModel.speakText(it.narrationText)
                                            }
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

            val isUndoEnabled by viewModel.canUndo.collectAsState()
            val isRedoEnabled by viewModel.canRedo.collectAsState()

            // 1. YouCut Signature Transport Controls
            YouCutTransportBar(
                isPlaying = isPlaying,
                canUndo = isUndoEnabled,
                canRedo = isRedoEnabled,
                appLanguage = appLanguageState,
                onUndoClick = { viewModel.undo() },
                onRedoClick = { viewModel.redo() },
                onSeekStartClick = {
                    viewModel.selectedSceneIndex.value = 0
                    currentPlaybackTimeMs = 0L
                },
                onPlayPauseClick = {
                    if (isPlaying) {
                        viewModel.isPlaying.value = false
                        viewModel.stopSpeak()
                    } else {
                        viewModel.isPlaying.value = true
                        currentScene?.let {
                            if (it.narrationText.isNotBlank() && !viewModel.isPlaceholderNarration(it.narrationText)) {
                                viewModel.speakText(it.narrationText)
                            }
                        }
                    }
                },
                onFullscreenClick = { showFullScreenPlayer = true }
            )

            // 2. YouCut Signature Feature Tool Bar
            YouCutToolActionRow(
                activeTool = activeToolSheet,
                appLanguage = appLanguageState,
                onToolClick = { tool ->
                    when (tool) {
                        EditorTool.ROTATE -> {
                            val cur = currentScene?.rotationDegrees ?: 0
                            val next = (cur + 90) % 360
                            viewModel.editSceneRotation(selectedSceneIndex, next)
                            Toast.makeText(context, "Rotated $next°", Toast.LENGTH_SHORT).show()
                        }
                        EditorTool.FLIP -> {
                            val flipped = currentScene?.isFlippedHorizontal ?: false
                            viewModel.editSceneFlippedHorizontal(selectedSceneIndex, !flipped)
                            Toast.makeText(context, if (!flipped) "Flipped Horizontally" else "Normal Orientation", Toast.LENGTH_SHORT).show()
                        }
                        EditorTool.PIP -> {
                            youCutPipPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                            )
                        }
                        else -> {
                            activeToolSheet = tool
                        }
                    }
                }
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(scrollState)
            ) {

            val timelineAudioName by viewModel.timelineAudioTrackName.collectAsState()
            val timelineAudioDuration by viewModel.timelineAudioDurationMs.collectAsState()
            val timelineAudioStart by viewModel.timelineAudioStartMs.collectAsState()
            val timelineAudioTotalDuration by viewModel.timelineAudioTotalDurationMs.collectAsState()

            // 3. YouCut Signature Filmstrip Timeline Track
            YouCutTimelineTrack(
                scenes = activeScenes,
                selectedSceneIndex = selectedSceneIndex,
                currentPlaybackTimeMs = currentPlaybackTimeMs,
                totalDurationSeconds = activeScenes.sumOf { it.durationSeconds },
                isAudioMuted = (currentScene?.volume ?: 1f) == 0f,
                appLanguage = appLanguageState,
                onSelectScene = { idx ->
                    viewModel.selectedSceneIndex.value = idx
                    currentPlaybackTimeMs = 0L
                },
                onAddMediaClick = {
                    youCutMediaPickerLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                    )
                },
                onToggleMuteAudio = {
                    val curVol = currentScene?.volume ?: 1f
                    val newVol = if (curVol > 0f) 0f else 1f
                    viewModel.editSceneVolume(selectedSceneIndex, newVol)
                    Toast.makeText(context, if (newVol == 0f) "Audio Muted" else "Audio Unmuted", Toast.LENGTH_SHORT).show()
                },
                onScrubTime = { newMs ->
                    currentPlaybackTimeMs = newMs
                },
                onSplitClip = { idx ->
                    viewModel.splitScene(idx, "[Split] New Segment")
                },
                onDeleteClip = { idx ->
                    viewModel.deleteScene(idx)
                },
                audioTrackName = timelineAudioName,
                audioTrackDurationMs = timelineAudioDuration,
                audioTrackStartMs = timelineAudioStart,
                onAudioTrackClick = {
                    activeToolSheet = EditorTool.MUSIC
                },
                onAddAudioTrackClick = {
                    activeToolSheet = EditorTool.MUSIC
                },
                onOpenTransitionManager = { idx ->
                    viewModel.selectedSceneIndex.value = idx
                    activeToolSheet = EditorTool.TRANSITION
                },
                onReplaceClip = { idx ->
                    viewModel.selectedSceneIndex.value = idx
                    activeToolSheet = EditorTool.REPLACE
                },
                onSpeedAdjustClick = { idx ->
                    viewModel.selectedSceneIndex.value = idx
                    activeToolSheet = EditorTool.SPEED
                }
            )

            // 4. Interactive Studio Tool Sheets
            when (activeToolSheet) {
                EditorTool.FILTER -> {
                    currentScene?.let { sc ->
                        FilterStudioSheet(
                            currentScene = sc,
                            appLanguage = appLanguageState,
                            onFilterSelected = { name, cat, idx ->
                                viewModel.editSceneFilter(selectedSceneIndex, name, idx)
                                viewModel.editSceneFilterCategory(selectedSceneIndex, cat)
                            },
                            onIntensityChanged = { intensity ->
                                viewModel.editSceneFilterIntensity(selectedSceneIndex, intensity)
                            },
                            onApplyToAll = { name, cat, idx, intensity ->
                                activeScenes.indices.forEach { i ->
                                    viewModel.editSceneFilter(i, name, idx)
                                    viewModel.editSceneFilterCategory(i, cat)
                                    viewModel.editSceneFilterIntensity(i, intensity)
                                }
                                Toast.makeText(context, "Filter applied to all clips", Toast.LENGTH_SHORT).show()
                            },
                            onDismiss = { activeToolSheet = null }
                        )
                    }
                }
                EditorTool.ADJUST -> {
                    currentScene?.let { sc ->
                        AdjustStudioSheet(
                            currentScene = sc,
                            appLanguage = appLanguageState,
                            onAdjustChanged = { b, c, s, w, vig, exp, shp, tnt, hl, sh ->
                                viewModel.editSceneFullGrading(selectedSceneIndex, b, c, s, w, vig, exp, shp, tnt, hl, sh)
                            },
                            onApplyToAll = { b, c, s, w, vig, exp, shp, tnt, hl, sh ->
                                viewModel.applyGradingToAllScenes(b, c, s, w, vig, exp, shp, tnt, hl, sh)
                                Toast.makeText(context, "Color grading applied to all clips".localize(appLanguageState), Toast.LENGTH_SHORT).show()
                            },
                            onDismiss = { activeToolSheet = null }
                        )
                    }
                }
                EditorTool.ANIMATION -> {
                    currentScene?.let { sc ->
                        AnimationStudioSheet(
                            currentScene = sc,
                            appLanguage = appLanguageState,
                            onAnimationChanged = { inA, outA, comboA, dur ->
                                viewModel.editSceneAnimation(selectedSceneIndex, inA, outA, comboA, dur)
                            },
                            onDismiss = { activeToolSheet = null }
                        )
                    }
                }
                EditorTool.EFFECT -> {
                    currentScene?.let { sc ->
                        EffectStudioSheet(
                            currentScene = sc,
                            appLanguage = appLanguageState,
                            onEffectSelected = { name, cat ->
                                viewModel.editSceneEffect(selectedSceneIndex, name, cat)
                            },
                            onIntensityChanged = { intensity ->
                                viewModel.editSceneEffectIntensity(selectedSceneIndex, intensity)
                            },
                            onDismiss = { activeToolSheet = null }
                        )
                    }
                }
                EditorTool.TRIM -> {
                    currentScene?.let { sc ->
                        TrimStudioSheet(
                            currentScene = sc,
                            selectedSceneIndex = selectedSceneIndex,
                            totalScenes = activeScenes.size,
                            currentPlaybackTimeMs = currentPlaybackTimeMs,
                            appLanguage = appLanguageState,
                            onDurationChanged = { newSec ->
                                viewModel.editSceneDuration(selectedSceneIndex, newSec)
                            },
                            onSplitClip = {
                                viewModel.splitScene(selectedSceneIndex, "[Split] Segment")
                            },
                            onDeleteClip = {
                                viewModel.deleteScene(selectedSceneIndex)
                            },
                            onDismiss = { activeToolSheet = null }
                        )
                    }
                }
                EditorTool.MUSIC -> {
                    currentScene?.let { sc ->
                        MusicStudioSheet(
                            currentScene = sc,
                            appLanguage = appLanguageState,
                            onSelectBgCategory = { genre ->
                                viewModel.updateBgMusicCategory(genre)
                                Toast.makeText(context, "Selected music: $genre", Toast.LENGTH_SHORT).show()
                            },
                            onPickCustomAudio = {
                                youCutAudioPickerLauncher.launch("audio/*")
                            },
                            onSelectSfx = { sfx ->
                                viewModel.editSceneSfx(selectedSceneIndex, sfx)
                            },
                            onVolumeChanged = { vol ->
                                viewModel.editSceneVolume(selectedSceneIndex, vol)
                            },
                            onToggleMuteClipAudio = {
                                val curVol = sc.volume
                                val newVol = if (curVol > 0f) 0f else 1f
                                viewModel.editSceneVolume(selectedSceneIndex, newVol)
                            },
                            isClipAudioMuted = sc.volume == 0f,
                            customAudioTrackName = timelineAudioName,
                            customAudioDurationMs = timelineAudioTotalDuration,
                            audioTrimStartMs = timelineAudioStart,
                            audioTrimDurationMs = timelineAudioDuration,
                            onAudioTrimChanged = { startMs, durMs ->
                                viewModel.updateTimelineAudioTrim(startMs, durMs)
                            },
                            onRemoveAudioTrack = {
                                viewModel.removeTimelineAudioTrack()
                                Toast.makeText(context, "Audio track removed", Toast.LENGTH_SHORT).show()
                            },
                            onDismiss = { activeToolSheet = null }
                        )
                    }
                }
                EditorTool.TEXT -> {
                    currentScene?.let { sc ->
                        TextStudioSheet(
                            currentScene = sc,
                            appLanguage = appLanguageState,
                            onLiveUpdate = { txt, color, font, anim, fontSize, durSec, startSec ->
                                viewModel.editSceneTextOverlayLive(
                                    index = selectedSceneIndex,
                                    text = txt,
                                    color = color,
                                    font = font,
                                    animation = anim,
                                    fontSize = fontSize,
                                    durationSeconds = durSec,
                                    startTimeSeconds = startSec
                                )
                            },
                            onTextSaveFull = { txt, color, font, anim, fontSize, durSec, startSec ->
                                viewModel.editSceneTextOverlayFull(
                                    index = selectedSceneIndex,
                                    text = txt,
                                    color = color,
                                    font = font,
                                    animation = anim,
                                    fontSize = fontSize,
                                    durationSeconds = durSec,
                                    startTimeSeconds = startSec
                                )
                            },
                            onTextSave = { txt, color, font, anim ->
                                viewModel.editSceneTextOverlay(selectedSceneIndex, txt)
                                viewModel.editSceneOverlayColor(selectedSceneIndex, color)
                                viewModel.editSceneCaptionFont(selectedSceneIndex, font)
                                viewModel.editSceneTextAnimation(selectedSceneIndex, anim)
                            },
                            onDismiss = { activeToolSheet = null }
                        )
                    }
                }
                EditorTool.TRANSITION -> {
                    currentScene?.let { sc ->
                        TransitionStudioSheet(
                            currentScene = sc,
                            allScenesCount = activeScenes.size,
                            appLanguage = appLanguageState,
                            onTransitionSelected = { transType, durMs ->
                                viewModel.editSceneTransitionWithDuration(selectedSceneIndex, transType, durMs)
                            },
                            onApplyToAll = { transType, durMs ->
                                viewModel.applyTransitionToAllScenes(transType, durMs)
                                Toast.makeText(context, "Transition applied to all clips", Toast.LENGTH_SHORT).show()
                            },
                            onDismiss = { activeToolSheet = null }
                        )
                    }
                }
                EditorTool.REPLACE -> {
                    currentScene?.let { sc ->
                        ModalBottomSheet(
                            onDismissRequest = { activeToolSheet = null },
                            containerColor = Color(0xFF14141E),
                            tonalElevation = 8.dp
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .fillMaxHeight(0.85f)
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.SwapHoriz, contentDescription = null, tint = YouCutOrange)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            "Replace Clip #${selectedSceneIndex + 1}".localize(appLanguageState),
                                            color = Color.White,
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    IconButton(onClick = { activeToolSheet = null }) {
                                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.LightGray)
                                    }
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                MediaSwapGalleryPanel(
                                    viewModel = viewModel,
                                    selectedSceneIndex = selectedSceneIndex,
                                    currentScene = sc,
                                    aspectRatio = activeRatio,
                                    appLanguageState = appLanguageState,
                                    onDismiss = { activeToolSheet = null }
                                )
                            }
                        }
                    }
                }
                EditorTool.STICKER -> {
                    currentScene?.let { sc ->
                        StickerStudioSheet(
                            currentScene = sc,
                            appLanguage = appLanguageState,
                            onSelectSticker = { stk ->
                                viewModel.editSceneSticker(selectedSceneIndex, stk)
                            },
                            onScaleChanged = { scale ->
                                viewModel.editSceneStickerScale(selectedSceneIndex, scale)
                            },
                            onDismiss = { activeToolSheet = null }
                        )
                    }
                }
                EditorTool.LAYERS -> {
                    currentScene?.let { sc ->
                        LayersStudioSheet(
                            currentScene = sc,
                            selectedLayerId = selectedLayerId,
                            appLanguage = appLanguageState,
                            onSelectLayer = { layerId ->
                                selectedLayerId = layerId
                            },
                            onBringForward = { layerId ->
                                viewModel.bringLayerForward(selectedSceneIndex, layerId)
                            },
                            onSendBackward = { layerId ->
                                viewModel.sendLayerBackward(selectedSceneIndex, layerId)
                            },
                            onBringToFront = { layerId ->
                                viewModel.bringLayerToFront(selectedSceneIndex, layerId)
                            },
                            onSendToBack = { layerId ->
                                viewModel.sendLayerToBack(selectedSceneIndex, layerId)
                            },
                            onToggleVisibility = { layerId ->
                                viewModel.toggleLayerVisibility(selectedSceneIndex, layerId)
                            },
                            onDeleteLayer = { layerId ->
                                viewModel.deleteLayer(selectedSceneIndex, layerId)
                                if (selectedLayerId == layerId) selectedLayerId = null
                            },
                            onAddTextLayer = { txt ->
                                viewModel.addTextLayer(selectedSceneIndex, txt)
                            },
                            onAddStickerLayer = { stk ->
                                viewModel.addStickerLayer(selectedSceneIndex, stk)
                            },
                            onDismiss = { activeToolSheet = null }
                        )
                    }
                }
                EditorTool.SPEED -> {
                    currentScene?.let { sc ->
                        SpeedStudioSheet(
                            currentScene = sc,
                            appLanguage = appLanguageState,
                            onSpeedChanged = { spd ->
                                viewModel.editSceneSpeedMultiplier(selectedSceneIndex, spd)
                            },
                            onDismiss = { activeToolSheet = null }
                        )
                    }
                }
                EditorTool.ENHANCE -> {
                    currentScene?.let { sc ->
                        EnhanceStudioSheet(
                            currentScene = sc,
                            appLanguage = appLanguageState,
                            onAdjustmentsChanged = { b, c, s, w ->
                                viewModel.editSceneBrightness(selectedSceneIndex, b)
                                viewModel.editSceneContrast(selectedSceneIndex, c)
                                viewModel.editSceneSaturation(selectedSceneIndex, s)
                                viewModel.editSceneWarmth(selectedSceneIndex, w)
                            },
                            onDismiss = { activeToolSheet = null }
                        )
                    }
                }
                EditorTool.CAPTIONS -> {
                    currentScene?.let { sc ->
                        TextStudioSheet(
                            currentScene = sc,
                            appLanguage = appLanguageState,
                            onLiveUpdate = { txt, color, font, anim, _, _, _ ->
                                viewModel.editSceneSubtitle(selectedSceneIndex, txt)
                                viewModel.editSceneSubtitleColor(selectedSceneIndex, color)
                                viewModel.editSceneCaptionFont(selectedSceneIndex, font)
                                viewModel.editSceneTextAnimation(selectedSceneIndex, anim)
                            },
                            onTextSave = { txt, color, font, anim ->
                                viewModel.editSceneSubtitle(selectedSceneIndex, txt)
                                viewModel.editSceneSubtitleColor(selectedSceneIndex, color)
                                viewModel.editSceneCaptionFont(selectedSceneIndex, font)
                                viewModel.editSceneTextAnimation(selectedSceneIndex, anim)
                            },
                            onDismiss = { activeToolSheet = null }
                        )
                    }
                }
                EditorTool.TEMPLATE, EditorTool.BG -> {
                    val currentRatio = viewModel.selectedAspectRatio.collectAsState().value
                    BgStudioSheet(
                        activeAspectRatio = currentRatio,
                        appLanguage = appLanguageState,
                        onRatioSelected = { r ->
                            viewModel.selectedAspectRatio.value = r
                        },
                        onDismiss = { activeToolSheet = null }
                    )
                }
                EditorTool.RECORD -> {
                    currentScene?.let { sc ->
                        VoiceRecorderSheet(
                            currentScene = sc,
                            selectedSceneIndex = selectedSceneIndex,
                            appLanguage = appLanguageState,
                            onVoiceRecorded = { path ->
                                viewModel.editSceneNarrationAudio(selectedSceneIndex, path)
                                Toast.makeText(context, "Voiceover saved to clip #${selectedSceneIndex + 1}", Toast.LENGTH_SHORT).show()
                            },
                            onDismiss = { activeToolSheet = null }
                        )
                    }
                }
                EditorTool.VOLUME -> {
                    currentScene?.let { sc ->
                        VolumeStudioSheet(
                            currentScene = sc,
                            appLanguage = appLanguageState,
                            onVolumeChanged = { vol ->
                                viewModel.editSceneVolume(selectedSceneIndex, vol)
                            },
                            onDismiss = { activeToolSheet = null }
                        )
                    }
                }
                EditorTool.VOICE_FX -> {
                    currentScene?.let { sc ->
                        VoiceFxStudioSheet(
                            currentScene = sc,
                            appLanguage = appLanguageState,
                            onVoiceFxChanged = { fx, inFade, outFade ->
                                viewModel.editSceneVoiceFx(selectedSceneIndex, fx, inFade, outFade)
                            },
                            onVolumeChanged = { vol ->
                                viewModel.editSceneVolume(selectedSceneIndex, vol)
                            },
                            onDismiss = { activeToolSheet = null }
                        )
                    }
                }
                EditorTool.FREEZE -> {
                    val wasFrozen = currentScene?.isFrozen == true
                    viewModel.toggleSceneFreeze(selectedSceneIndex)
                    activeToolSheet = null
                    Toast.makeText(
                        context,
                        if (wasFrozen) "Freeze frame disabled".localize(appLanguageState)
                        else "❄️ Dramatic Freeze applied to Clip #${selectedSceneIndex + 1}!".localize(appLanguageState),
                        Toast.LENGTH_SHORT
                    ).show()
                }
                EditorTool.REVERSE -> {
                    val wasReversed = currentScene?.isReversed == true
                    viewModel.toggleSceneReverse(selectedSceneIndex)
                    activeToolSheet = null
                    Toast.makeText(
                        context,
                        if (wasReversed) "Reverse playback disabled".localize(appLanguageState)
                        else "⏪ Reverse Playback enabled for Clip #${selectedSceneIndex + 1}!".localize(appLanguageState),
                        Toast.LENGTH_SHORT
                    ).show()
                }
                EditorTool.CROP -> {
                    CropStudioSheet(
                        appLanguage = appLanguageState,
                        onDismiss = { activeToolSheet = null }
                    )
                }
                EditorTool.CHROMA -> {
                    currentScene?.let { sc ->
                        ChromaKeyStudioSheet(
                            currentScene = sc,
                            appLanguage = appLanguageState,
                            onChromaChanged = { isEnabled, color, sensitivity ->
                                viewModel.pushToUndo("Chroma Key")
                                val updated = sc.copy(
                                    isChromaKeyEnabled = isEnabled,
                                    chromaKeyColor = color,
                                    chromaKeySensitivity = sensitivity
                                )
                                viewModel.updateScene(selectedSceneIndex, updated)
                            },
                            onDismiss = { activeToolSheet = null }
                        )
                    }
                }
                EditorTool.MASK -> {
                    currentScene?.let { sc ->
                        MaskStudioSheet(
                            currentScene = sc,
                            appLanguage = appLanguageState,
                            onMaskChanged = { maskShape ->
                                viewModel.pushToUndo("Mask")
                                val updated = sc.copy(maskShape = maskShape)
                                viewModel.updateScene(selectedSceneIndex, updated)
                            },
                            onDismiss = { activeToolSheet = null }
                        )
                    }
                }
                else -> {}
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
                                    selectedSceneIndex = selectedSceneIndex,
                                    onReplaceAsset = { idx ->
                                        sceneToReplaceIndex = idx
                                        timelineAssetPickerLauncher.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                                        )
                                    }
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

                                        // Timeline edit toolkit: Replace Asset / Split / Sticker / Delete
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            // "Replace Asset" action (Opens system photo/video picker to override auto-generated media)
                                            Button(
                                                onClick = {
                                                    activeToolSheet = EditorTool.REPLACE
                                                },
                                                shape = RoundedCornerShape(8.dp),
                                                modifier = Modifier.weight(1.3f),
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = MaterialTheme.colorScheme.primary,
                                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                                ),
                                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp)
                                            ) {
                                                Icon(Icons.Default.SwapHoriz, contentDescription = null, modifier = Modifier.size(15.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("Replace Asset".localize(appLanguageState), fontSize = 10.5.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                                            }

                                            // "Split Clip" action
                                            Button(
                                                onClick = {
                                                    viewModel.splitScene(selectedSceneIndex, "[Splitted Clip] New Segment")
                                                },
                                                shape = RoundedCornerShape(8.dp),
                                                modifier = Modifier.weight(0.9f),
                                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onSurfaceVariant),
                                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp)
                                            ) {
                                                Icon(Icons.Default.ContentCut, contentDescription = null, modifier = Modifier.size(14.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("Split".localize(appLanguageState), fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                                            }

                                            // "Add Sticker" action
                                            Button(
                                                onClick = { showStickerDrawer = true },
                                                shape = RoundedCornerShape(8.dp),
                                                modifier = Modifier.weight(0.9f),
                                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onSurfaceVariant),
                                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp)
                                            ) {
                                                Icon(Icons.Default.AddReaction, contentDescription = null, modifier = Modifier.size(14.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("Sticker".localize(appLanguageState), fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                                            }

                                            // "Delete Scene" action
                                            Button(
                                                onClick = {
                                                    showDeleteSceneConfirmDialog = true
                                                },
                                                shape = RoundedCornerShape(8.dp),
                                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer),
                                                enabled = activeScenes.size > 1,
                                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp)
                                            ) {
                                                Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(14.dp))
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(4.dp))

                                        // Watermark Quick Action Button
                                        Button(
                                            onClick = { showWatermarkSheet = true },
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier.fillMaxWidth(),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = if (watermarkConfigState.isEnabled) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                                contentColor = if (watermarkConfigState.isEnabled) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        ) {
                                            Icon(Icons.Default.BrandingWatermark, contentDescription = null, modifier = Modifier.size(15.dp))
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = if (watermarkConfigState.isEnabled) {
                                                    val wmText = watermarkConfigState.text.trim().ifEmpty { "Yashora AI Reel Engine" }
                                                    "Watermark: \"$wmText\" (${watermarkConfigState.position.displayName})".localize(appLanguageState)
                                                } else {
                                                    "Watermark: Disabled (Clean Video)".localize(appLanguageState)
                                                },
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Spacer(modifier = Modifier.weight(1f))
                                            Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(14.dp))
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

    // Delete Scene Confirmation Dialog
    if (showDeleteSceneConfirmDialog) {
        val sceneNum = selectedSceneIndex + 1
        AlertDialog(
            onDismissRequest = { showDeleteSceneConfirmDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = {
                Text(
                    text = "Delete Scene $sceneNum?".localize(appLanguageState),
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to delete this scene? This will remove its video media clip, text overlay, and visual effects permanently.".localize(appLanguageState),
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteScene(selectedSceneIndex)
                        showDeleteSceneConfirmDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Delete".localize(appLanguageState))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteSceneConfirmDialog = false }) {
                    Text("Cancel".localize(appLanguageState))
                }
            },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(20.dp)
        )
    }

    // Interactive Add Media / Scene Clip modal
    if (showMediaImportDrawer) {
        val suggestions by viewModel.alternativeSuggestions.collectAsState()
        val isSearching by viewModel.isSearchingAlternatives.collectAsState()
        val searchError by viewModel.alternativeSearchError.collectAsState()

        var addClipTab by remember { mutableIntStateOf(0) } // 0: App Sources (16+ Libraries), 1: Device Storage
        var appSourcesQuery by remember { mutableStateOf("trending") }
        var appSourcesMediaType by remember { mutableStateOf("VIDEO") }
        var isDownloadingClip by remember { mutableStateOf(false) }
        var selectedDrawerSourceFilter by remember { mutableStateOf("ALL") }

        val displayDrawerSuggestions = remember(suggestions, selectedDrawerSourceFilter) {
            if (selectedDrawerSourceFilter == "ALL") suggestions
            else suggestions.filter { matchesSourceCatalogFilter(it.source, selectedDrawerSourceFilter) }
        }

        val quickCategories = remember {
            listOf(
                "🔥 Trending" to "trending",
                "🎬 Cinematic" to "cinematic",
                "🌿 Nature" to "nature",
                "🏙️ City" to "city",
                "🚀 Space / NASA" to "space nebula",
                "🍕 Food & Cooking" to "food",
                "🌸 Anime Art" to "anime",
                "🏎️ Cars & Speed" to "supercar",
                "✨ Motivational" to "motivation success",
                "😂 Memes / Fun" to "funny memes"
            )
        }

        LaunchedEffect(addClipTab, appSourcesMediaType) {
            if (addClipTab == 0) {
                viewModel.fetchAlternativeSuggestions(appSourcesQuery, appSourcesMediaType, activeProject?.aspectRatio ?: "9:16")
            }
        }

        AlertDialog(
            onDismissRequest = { 
                if (!isDownloadingClip) {
                    showMediaImportDrawer = false 
                }
            },
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Add Media Clip".localize(appLanguageState),
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    IconButton(
                        onClick = { if (!isDownloadingClip) showMediaImportDrawer = false },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close", modifier = Modifier.size(18.dp))
                    }
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 480.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Tab Selector: App Sources vs Device Storage
                    TabRow(
                        selectedTabIndex = addClipTab,
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        contentColor = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                    ) {
                        Tab(
                            selected = addClipTab == 0,
                            onClick = { addClipTab = 0 },
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("🌐 " + "App Sources".localize(appLanguageState), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        )
                        Tab(
                            selected = addClipTab == 1,
                            onClick = { addClipTab = 1 },
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("📱 " + "Device Gallery".localize(appLanguageState), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        )
                    }

                    if (addClipTab == 0) {
                        // APP SOURCES TAB
                        // Search bar & Mode toggle
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = appSourcesQuery,
                                onValueChange = { appSourcesQuery = it },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp),
                                textStyle = LocalTextStyle.current.copy(fontSize = 11.sp),
                                singleLine = true,
                                placeholder = { Text("Search 16+ app sources...".localize(appLanguageState), fontSize = 10.sp) }
                            )

                            Button(
                                onClick = {
                                    appSourcesMediaType = if (appSourcesMediaType == "VIDEO") "IMAGE" else "VIDEO"
                                    viewModel.fetchAlternativeSuggestions(appSourcesQuery, appSourcesMediaType, activeProject?.aspectRatio ?: "9:16")
                                },
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (appSourcesMediaType == "VIDEO") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer,
                                    contentColor = if (appSourcesMediaType == "VIDEO") MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer
                                ),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = Modifier.height(44.dp)
                            ) {
                                Text(
                                    text = if (appSourcesMediaType == "VIDEO") "📹 Videos" else "🖼️ Photos",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Button(
                                onClick = {
                                    viewModel.fetchAlternativeSuggestions(appSourcesQuery, appSourcesMediaType, activeProject?.aspectRatio ?: "9:16")
                                },
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                ),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                modifier = Modifier.height(44.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = "Search",
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        // Quick category filter chips
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(quickCategories.size) { i ->
                                val (label, queryVal) = quickCategories[i]
                                val isSelected = appSourcesQuery.equals(queryVal, ignoreCase = true)
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    border = BorderStroke(1.dp, if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent),
                                    modifier = Modifier.clickable {
                                        appSourcesQuery = queryVal
                                        viewModel.fetchAlternativeSuggestions(queryVal, appSourcesMediaType, activeProject?.aspectRatio ?: "9:16")
                                    }
                                ) {
                                    Text(
                                        text = label.localize(appLanguageState),
                                        fontSize = 10.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }

                        // Source Filter Chips (All, Pexels, Pixabay, Unsplash, Wikimedia, Jikan, Nekos, MealDB, etc.)
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(ALL_MEDIA_SOURCES_CATALOG.size) { idx ->
                                val catalogItem = ALL_MEDIA_SOURCES_CATALOG[idx]
                                val isSel = selectedDrawerSourceFilter == catalogItem.id
                                val count = if (catalogItem.id == "ALL") suggestions.size else suggestions.count { matchesSourceCatalogFilter(it.source, catalogItem.id) }
                                val label = if (catalogItem.id == "ALL") {
                                    "🌐 All Sources ($count)"
                                } else if (count > 0) {
                                    "${catalogItem.emoji} ${catalogItem.displayName} ($count)"
                                } else {
                                    "${catalogItem.emoji} ${catalogItem.displayName}"
                                }
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (isSel) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
                                    border = BorderStroke(1.dp, if (isSel) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
                                    modifier = Modifier.clickable {
                                        selectedDrawerSourceFilter = catalogItem.id
                                    }
                                ) {
                                    Text(
                                        text = label,
                                        fontSize = 9.sp,
                                        fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSel) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                            }
                        }

                        if (isDownloadingClip) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(200.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(36.dp))
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Text("Adding clip from App Sources...".localize(appLanguageState), fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                }
                            }
                        } else if (isSearching) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(200.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp), strokeWidth = 3.dp)
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text("Fetching from 16+ App Sources...".localize(appLanguageState), fontSize = 11.sp, color = Color.Gray)
                                }
                            }
                        } else if (displayDrawerSuggestions.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(180.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("No items found for this source. Try another query or source.".localize(appLanguageState), fontSize = 11.sp, color = Color.Gray)
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Button(
                                        onClick = {
                                            selectedDrawerSourceFilter = "ALL"
                                            appSourcesQuery = "nature"
                                            viewModel.fetchAlternativeSuggestions("nature", appSourcesMediaType, activeProject?.aspectRatio ?: "9:16")
                                        },
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                        modifier = Modifier.height(34.dp)
                                    ) {
                                        Text("Browse 'Nature'".localize(appLanguageState), fontSize = 10.sp)
                                    }
                                }
                            }
                        } else {
                            Text(
                                text = "Tap any item to add to timeline:".localize(appLanguageState),
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            LazyVerticalGrid(
                                columns = GridCells.Fixed(3),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(210.dp)
                            ) {
                                items(displayDrawerSuggestions.size) { index ->
                                    val item = displayDrawerSuggestions[index]
                                    Card(
                                        shape = RoundedCornerShape(8.dp),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .aspectRatio(0.85f)
                                            .clickable {
                                                isDownloadingClip = true
                                                coroutineScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                                    try {
                                                        val localPath = viewModel.repository.downloadMediaToLocal(item.url)
                                                        val duration = if (item.durationSeconds > 0) item.durationSeconds.coerceIn(3, 20) else 5
                                                        val newScene = Scene(
                                                            sceneNumber = activeScenes.size + 1,
                                                            narrationText = "", // No unwanted voice-over!
                                                            visualPrompt = item.title.ifBlank { "App Source Media" },
                                                            subtitle = "",
                                                            mediaPath = localPath,
                                                            remoteUrl = item.url,
                                                            mediaType = item.mediaType,
                                                            durationSeconds = duration,
                                                            durationMs = duration * 1000L
                                                        )
                                                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                                            viewModel.insertScene(activeScenes.size, newScene)
                                                            isDownloadingClip = false
                                                            showMediaImportDrawer = false
                                                            android.widget.Toast.makeText(context, "Added clip from App Sources!".localize(appLanguageState), android.widget.Toast.LENGTH_SHORT).show()
                                                        }
                                                    } catch (e: Exception) {
                                                        Log.e("AddClip", "Failed adding app source clip", e)
                                                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                                            isDownloadingClip = false
                                                            android.widget.Toast.makeText(context, "Failed to load clip".localize(appLanguageState), android.widget.Toast.LENGTH_SHORT).show()
                                                        }
                                                    }
                                                }
                                            }
                                    ) {
                                        Box(modifier = Modifier.fillMaxSize()) {
                                            coil.compose.AsyncImage(
                                                model = item.thumbnailUrl.ifEmpty { item.url },
                                                contentDescription = item.title,
                                                modifier = Modifier.fillMaxSize(),
                                                contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                            )

                                            // Source badge
                                            Surface(
                                                shape = RoundedCornerShape(3.dp),
                                                color = Color.Black.copy(alpha = 0.7f),
                                                modifier = Modifier.padding(3.dp).align(Alignment.TopStart)
                                            ) {
                                                Text(
                                                    text = getDisplaySourceName(item.source),
                                                    fontSize = 7.sp,
                                                    color = Color.White,
                                                    modifier = Modifier.padding(horizontal = 3.dp, vertical = 1.dp)
                                                )
                                            }

                                            // Video indicator
                                            if (item.mediaType == "VIDEO") {
                                                Surface(
                                                    shape = RoundedCornerShape(3.dp),
                                                    color = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.padding(3.dp).align(Alignment.BottomEnd)
                                                ) {
                                                    Text(
                                                        text = "▶ ${if (item.durationSeconds > 0) "${item.durationSeconds}s" else "Video"}",
                                                        fontSize = 7.sp,
                                                        color = Color.White,
                                                        fontWeight = FontWeight.Bold,
                                                        modifier = Modifier.padding(horizontal = 3.dp, vertical = 1.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        // DEVICE GALLERY TAB
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PhotoLibrary,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(32.dp)
                                )
                            }

                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "Import from Your Device".localize(appLanguageState),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Select any Video (MP4, MOV, MKV) or Image (JPG, PNG) from your gallery or files without any voiceover generation.".localize(appLanguageState),
                                    fontSize = 11.sp,
                                    color = Color.Gray,
                                    textAlign = TextAlign.Center,
                                    lineHeight = 15.sp,
                                    modifier = Modifier.padding(horizontal = 12.dp)
                                )
                            }

                            Button(
                                onClick = {
                                    filePickerLauncher.launch("*/*")
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                            ) {
                                Icon(imageVector = Icons.Default.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Open Gallery / File Picker".localize(appLanguageState), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { 
                        if (!isDownloadingClip) showMediaImportDrawer = false 
                    }
                ) {
                    Text("Close".localize(appLanguageState))
                }
            },
            shape = RoundedCornerShape(20.dp)
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
                                    android.widget.Toast.makeText(context, "Replaced backdrop for Clip ${selectedSceneIndex + 1}".localize(appLanguageState), android.widget.Toast.LENGTH_SHORT).show()
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
                                    narrationText = "",
                                    visualPrompt = "Custom Device Media",
                                    subtitle = "",
                                    mediaPath = pickedPath,
                                    mediaType = if (isVideo) "VIDEO" else "IMAGE",
                                    durationSeconds = 5
                                )
                                viewModel.insertScene(nextIndex, newScene)
                                android.widget.Toast.makeText(context, "Inserted clip in timeline".localize(appLanguageState), android.widget.Toast.LENGTH_SHORT).show()
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
                                    narrationText = "",
                                    visualPrompt = "Custom Device Media",
                                    subtitle = "",
                                    mediaPath = pickedPath,
                                    mediaType = if (isVideo) "VIDEO" else "IMAGE",
                                    durationSeconds = 5
                                )
                                viewModel.insertScene(activeScenes.size, newScene)
                                android.widget.Toast.makeText(context, "Added clip to timeline".localize(appLanguageState), android.widget.Toast.LENGTH_SHORT).show()
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

                    LottieVideoSynthesisAnimation(sizeDp = 90.dp)

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

                    LottieLinearProgressBar(
                        progress = exportProgress,
                        height = 8.dp
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

                        exportEtaFormatted?.let { eta ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color(0xFF1E1438))
                                    .border(1.dp, Color(0xFF00F0FF).copy(alpha = 0.35f), RoundedCornerShape(6.dp))
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Timer,
                                    contentDescription = null,
                                    tint = Color(0xFF39FF14),
                                    modifier = Modifier.size(12.dp)
                                )
                                Text(
                                    text = eta.localize(appLanguageState),
                                    color = Color(0xFF39FF14),
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        } ?: Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.HourglassTop,
                                contentDescription = null,
                                tint = Color(0xFFFF007F),
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "Calculating ETA...".localize(appLanguageState),
                                color = Color.LightGray,
                                fontSize = 10.5.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

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
        val configuration = androidx.compose.ui.platform.LocalConfiguration.current
        val dialogMaxHeight = (configuration.screenHeightDp * 0.45f).dp.coerceIn(160.dp, 340.dp)
        var tempSelectedRes by remember { mutableStateOf(viewModel.selectedResolution.value) }
        var isEnhanceEnabled by remember(showExportQualityDialog) { mutableStateOf(false) }
        AlertDialog(
            modifier = Modifier.testTag("export_quality_dialog"),
            onDismissRequest = {
                viewModel.resetEnhanceEnabled()
                showExportQualityDialog = false
            },
            title = {
                Column {
                    Text(
                        text = "Export Video".localize(appLanguageState),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Video Quality".localize(appLanguageState),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
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
                        .heightIn(max = dialogMaxHeight)
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

                    Spacer(modifier = Modifier.height(4.dp))

                    // ✨ Enhance Video Toggle (Exclusive to Export screen/dialog, default OFF, no manual sliders)
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isEnhanceEnabled) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                        ),
                        border = androidx.compose.foundation.BorderStroke(
                            width = if (isEnhanceEnabled) 1.5.dp else 1.dp,
                            color = if (isEnhanceEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("enhance_video_toggle")
                            .clickable { isEnhanceEnabled = !isEnhanceEnabled }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp, horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f).padding(end = 8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    tint = if (isEnhanceEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = "✨ Enhance Video".localize(appLanguageState),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                            color = if (isEnhanceEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Surface(
                                            color = if (isEnhanceEnabled) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant,
                                            shape = RoundedCornerShape(4.dp)
                                        ) {
                                            Text(
                                                text = if (isEnhanceEnabled) "ON" else "OFF",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isEnhanceEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                            )
                                        }
                                    }
                                    Text(
                                        text = if (isEnhanceEnabled)
                                            "Auto color vibrance, HDR contrast & crisp clarity".localize(appLanguageState)
                                        else
                                            "Standard original source rendering".localize(appLanguageState),
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        lineHeight = 14.sp,
                                        modifier = Modifier.padding(top = 2.dp)
                                    )
                                }
                            }
                            Switch(
                                checked = isEnhanceEnabled,
                                onCheckedChange = { isEnhanceEnabled = it },
                                modifier = Modifier.testTag("enhance_video_switch")
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Watermark Quick Configure Card in Export Dialog
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (watermarkConfigState.isEnabled)
                                MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f)
                            else
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ),
                        border = androidx.compose.foundation.BorderStroke(
                            width = 1.dp,
                            color = if (watermarkConfigState.isEnabled) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outlineVariant
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showWatermarkSheet = true }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.BrandingWatermark,
                                    contentDescription = null,
                                    tint = if (watermarkConfigState.isEnabled) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = "Video Watermark".localize(appLanguageState),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Surface(
                                            color = if (watermarkConfigState.isEnabled) MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant,
                                            shape = RoundedCornerShape(4.dp)
                                        ) {
                                            Text(
                                                text = if (watermarkConfigState.isEnabled) "ON" else "OFF",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (watermarkConfigState.isEnabled) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                            )
                                        }
                                    }
                                    Text(
                                        text = if (watermarkConfigState.isEnabled) {
                                            val wmTxt = watermarkConfigState.text.trim().ifEmpty { "Yashora AI Reel Engine" }
                                            "\"$wmTxt\" • ${watermarkConfigState.position.displayName}".localize(appLanguageState)
                                        } else {
                                            "Disabled (Clean video will be exported)".localize(appLanguageState)
                                        },
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        lineHeight = 14.sp,
                                        modifier = Modifier.padding(top = 2.dp)
                                    )
                                }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = { showWatermarkSheet = true },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Tune,
                                        contentDescription = "Configure Watermark".localize(appLanguageState),
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Switch(
                                    checked = watermarkConfigState.isEnabled,
                                    onCheckedChange = { viewModel.toggleWatermark(it) }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))
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

                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = {
                            showExportQualityDialog = false
                            onNavigateToPreview()
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("export_dialog_preview_cut_button"),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(Icons.Default.ContentCut, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Trim or Reorder Scenes First 🎬".localize(appLanguageState), fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.selectedResolution.value = tempSelectedRes
                        viewModel.setEnhanceEnabled(isEnhanceEnabled)
                        showExportQualityDialog = false
                        checkAndRequestPermissions()
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.testTag("export_video_confirm_button")
                ) {
                    Text("Export Video".localize(appLanguageState), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        viewModel.resetEnhanceEnabled()
                        showExportQualityDialog = false
                    }
                ) {
                    Text("Cancel".localize(appLanguageState))
                }
            }
        )
    }

    // Watermark Customization Sheet
    if (showWatermarkSheet) {
        WatermarkSettingsSheet(
            config = watermarkConfigState,
            appLanguage = appLanguageState,
            onConfigChange = { viewModel.updateWatermarkConfig(it) },
            onDismiss = { showWatermarkSheet = false }
        )
    }

    // AdMob Interstitial Ad mob Dialog triggers after export completion which redirects to list!
    MockInterstitialAdDialog(
        show = showInterstitialAd,
        onDismiss = {
            showInterstitialAd = false
            val action = pendingAdAction
            if (action != null) {
                pendingAdAction = null
                action()
            } else {
                // Call actual exports background thread compilation writing
                viewModel.exportVideo(
                    onExportComplete = {
                        val savedPath = viewModel.exportedFilePath.value ?: ""
                        exportedPathForSuccess = savedPath
                        viewModel.stopAllPlayback()
                        activePreviewTab = 1
                        showExportSuccessDialog = true
                    }
                )
            }
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
                            text = "Save & Export to Cloud Account 🎬".localize(appLanguageState),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Please review your video preview. Approving will save the final trimmed video and script with real-time date & time to your Firestore account and export it to your device gallery. Unapproved drafts remain private on your device.".localize(appLanguageState),
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
                                VideoPlayer(videoUri = previewUri, appLanguage = appLanguageState, modifier = Modifier.fillMaxSize(), filePath = compiledPreviewPath)
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
                            if (compiledPreviewPath != null) {
                                OutlinedButton(
                                    onClick = {
                                        videoToTrimPath = compiledPreviewPath
                                        showTrimmerDialog = true
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(42.dp)
                                        .testTag("confirm_export_trim_button"),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(imageVector = Icons.Default.ContentCut, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Trim Footage Before Export ✂️".localize(appLanguageState), color = MaterialTheme.colorScheme.primary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                            }

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
                                                viewModel.stopAllPlayback()
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
                            showShareDialogForSuccess = true
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
                        Text("Share Video (Instagram / WhatsApp / Apps)".localize(appLanguageState), fontWeight = FontWeight.Bold, fontSize = 12.sp)
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

    if (showShareDialogForSuccess && exportedPathForSuccess.isNotEmpty()) {
        VideoShareDialog(
            filePath = exportedPathForSuccess,
            projectTitle = activeProject?.title?.ifEmpty { "Yashora Reel" } ?: "Yashora Reel",
            aspectRatio = activeRatio,
            appLanguageState = appLanguageState,
            onDismiss = { showShareDialogForSuccess = false },
            onPlayVideo = {
                showShareDialogForSuccess = false
                showSuccessPlayerDialog = true
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
                            VideoPlayer(videoUri = videoUri, appLanguage = appLanguageState, modifier = Modifier.fillMaxWidth(), filePath = exportedPathForSuccess, autoPlay = true)
                            Spacer(modifier = Modifier.height(10.dp))
                            OutlinedButton(
                                onClick = {
                                    videoToTrimPath = exportedPathForSuccess
                                    showTrimmerDialog = true
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(42.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.ContentCut, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Trim Video ✂️".localize(appLanguageState), color = MaterialTheme.colorScheme.primary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        } else {
                            Text("Loading media player...".localize(appLanguageState), color = Color.Gray, fontSize = 12.sp)
                        }
                    }
                }
            }
        )
    }

    if (showTrimmerDialog && !videoToTrimPath.isNullOrEmpty()) {
        VideoTrimmerDialog(
            videoPath = videoToTrimPath!!,
            appLanguage = appLanguageState,
            onDismiss = {
                showTrimmerDialog = false
                videoToTrimPath = null
            },
            onTrimSuccess = { newTrimmedPath ->
                showTrimmerDialog = false
                videoToTrimPath = null
                viewModel.compiledPreviewPath.value = newTrimmedPath
                exportedPathForSuccess = newTrimmedPath
                Toast.makeText(context, "Video trimmed successfully! ✂️".localize(appLanguageState), Toast.LENGTH_SHORT).show()
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
                                                mp.isLooping = false
                                                try {
                                                    mp.setVolume(1.0f, 1.0f) // full volume in fullscreen option!
                                                } catch (e: Exception) {}
                                            }
                                            setOnCompletionListener { mp ->
                                                try {
                                                    mp.pause()
                                                    mp.seekTo(0)
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
                            
                            // Overlays with custom positions, scale, and live colors
                            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                                // Visual overlay texts (Custom position, scale and live color)
                                sc.textOverlay?.takeIf { it.isNotEmpty() }?.let { over ->
                                    val overlayThemeColor = parseHexColor(sc.overlayColor)
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopStart)
                                            .offset(
                                                x = (maxWidth * sc.textOverlayX - 50.dp).coerceIn(0.dp, (maxWidth - 100.dp).coerceAtLeast(0.dp)),
                                                y = (maxHeight * sc.textOverlayY - 20.dp).coerceIn(0.dp, (maxHeight - 40.dp).coerceAtLeast(0.dp))
                                            )
                                            .graphicsLayer(
                                                scaleX = sc.textOverlayScale,
                                                scaleY = sc.textOverlayScale
                                            )
                                            .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(8.dp))
                                            .padding(horizontal = 14.dp, vertical = 7.dp)
                                    ) {
                                        Text(
                                            text = over.uppercase(),
                                            color = overlayThemeColor,
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Black,
                                            letterSpacing = 1.2.sp,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }

                                // Visual sticker overlay (Custom position & scale)
                                sc.stickerName.takeIf { it.isNotEmpty() && it != "None" }?.let { stName ->
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopStart)
                                            .offset(
                                                x = (maxWidth * sc.stickerX - 25.dp).coerceIn(0.dp, (maxWidth - 50.dp).coerceAtLeast(0.dp)),
                                                y = (maxHeight * sc.stickerY - 25.dp).coerceIn(0.dp, (maxHeight - 50.dp).coerceAtLeast(0.dp))
                                            )
                                            .graphicsLayer(
                                                scaleX = sc.stickerScale,
                                                scaleY = sc.stickerScale
                                            )
                                    ) {
                                        Text(
                                            text = stName,
                                            fontSize = 32.sp,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }

                                // Dynamic screen subtitle text inside professional Bottom pill container
                                if (sc.subtitle.isNotEmpty()) {
                                    val subColor = parseHexColor(sc.subtitleColor.ifEmpty { "#FFFFFF" })
                                    val subBg = parseHexColor(sc.subtitleBgColor.ifEmpty { "#C7000000" })
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.BottomCenter)
                                            .padding(bottom = 36.dp, start = 20.dp, end = 20.dp)
                                            .background(subBg, RoundedCornerShape(12.dp))
                                            .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                                            .padding(horizontal = 16.dp, vertical = 10.dp)
                                    ) {
                                        Text(
                                            text = sc.subtitle,
                                            color = subColor,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            textAlign = TextAlign.Center,
                                            lineHeight = 20.sp
                                        )
                                    }
                                }
                            }
                            // Live watermark overlay on full screen preview
                            WatermarkOverlay(
                                config = watermarkConfigState,
                                modifier = Modifier.fillMaxSize()
                            )
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
                                                if (it.narrationText.isNotBlank() && !viewModel.isPlaceholderNarration(it.narrationText)) {
                                                    viewModel.speakText(it.narrationText)
                                                }
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
                                        currentScene?.let {
                                            if (it.narrationText.isNotBlank() && !viewModel.isPlaceholderNarration(it.narrationText)) {
                                                viewModel.speakText(it.narrationText)
                                            }
                                        }
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
                                                if (it.narrationText.isNotBlank() && !viewModel.isPlaceholderNarration(it.narrationText)) {
                                                    viewModel.speakText(it.narrationText)
                                                }
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
    modifier: Modifier = Modifier,
    filePath: String? = null,
    autoPlay: Boolean = false
) {
    val context = LocalContext.current
    var isVideoPrepared by remember { mutableStateOf(false) }
    var isVideoError by remember { mutableStateOf(false) }
    var isPlayingState by remember { mutableStateOf(autoPlay) }
    var videoViewRef by remember { mutableStateOf<android.widget.VideoView?>(null) }
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_PAUSE || event == androidx.lifecycle.Lifecycle.Event.ON_STOP) {
                isPlayingState = false
                try { videoViewRef?.pause() } catch (e: Exception) {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            try { videoViewRef?.stopPlayback() } catch (e: Exception) {}
        }
    }

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
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(16.dp)
            ) {
                Text("🎬", fontSize = 32.sp)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Streaming playback active... Tap to retry".localize(appLanguage),
                    color = Color.White,
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = { isVideoError = false },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("Retry".localize(appLanguage), fontSize = 11.sp, color = Color.White)
                }
            }
        } else {
            androidx.compose.ui.viewinterop.AndroidView(
                factory = { ctx ->
                    android.widget.VideoView(ctx).apply {
                        videoViewRef = this
                        try {
                            val mediaController = android.widget.MediaController(ctx)
                            mediaController.setAnchorView(this)
                            setMediaController(mediaController)
                        } catch (e: Exception) {
                            Log.e("EditorVideoPlayer", "Failed to set MediaController", e)
                        }
                        
                        try {
                            if (!filePath.isNullOrEmpty() && java.io.File(filePath).exists()) {
                                setVideoPath(filePath)
                            } else {
                                setVideoURI(videoUri)
                            }
                        } catch (e: Exception) {
                            Log.e("EditorVideoPlayer", "Failed to set video URI/path", e)
                            try { setVideoURI(videoUri) } catch (ex: Exception) {}
                        }
                        
                        setOnPreparedListener { mp ->
                            try {
                                mp.isLooping = false // NEVER repeat infinitely, protects battery life
                                isVideoPrepared = true
                                if (isPlayingState) {
                                    start()
                                } else {
                                    pause()
                                    seekTo(1)
                                }
                            } catch (e: Exception) {
                                Log.e("EditorVideoPlayer", "Failed to prepare VideoView", e)
                            }
                        }

                        setOnCompletionListener {
                            isPlayingState = false
                            try {
                                pause()
                                seekTo(0)
                            } catch (e: Exception) {}
                        }
                        
                        setOnErrorListener { mp, what, extra ->
                            Log.e("EditorVideoPlayer", "VideoView error what=$what extra=$extra")
                            try {
                                setVideoURI(videoUri)
                                if (isPlayingState) start()
                                false
                            } catch (e: Exception) {
                                isVideoError = true
                                true
                            }
                        }
                    }
                },
                update = { videoView ->
                    videoViewRef = videoView
                    try {
                        val currentTag = filePath ?: videoUri.toString()
                        if (videoView.tag != currentTag) {
                            videoView.tag = currentTag
                            if (!filePath.isNullOrEmpty() && java.io.File(filePath).exists()) {
                                videoView.setVideoPath(filePath)
                            } else {
                                videoView.setVideoURI(videoUri)
                            }
                            if (isPlayingState) {
                                videoView.start()
                            } else {
                                videoView.pause()
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("EditorVideoPlayer", "Error during VideoView update", e)
                    }
                },
                modifier = Modifier
                    .fillMaxSize()
                    .clickable {
                        isPlayingState = !isPlayingState
                        try {
                            if (isPlayingState) {
                                videoViewRef?.start()
                            } else {
                                videoViewRef?.pause()
                            }
                        } catch (e: Exception) {}
                    }
            )
            
            if (!isVideoPrepared) {
                PremiumCircularLoader(sizeDp = 24)
            } else if (!isPlayingState) {
                Surface(
                    onClick = {
                        isPlayingState = true
                        try { videoViewRef?.start() } catch (e: Exception) {}
                    },
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.65f),
                    border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary),
                    modifier = Modifier.size(52.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Play",
                            tint = Color.White,
                            modifier = Modifier.size(30.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ComposedVideoPlaceholder(
    appLanguage: String,
    onExportClick: () -> Unit,
    onPreviewCutClick: () -> Unit = {},
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
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Button(
                    onClick = onPreviewCutClick,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF261D3A)),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    modifier = Modifier.height(28.dp).testTag("composed_placeholder_preview_cut_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayCircle,
                        contentDescription = null,
                        modifier = Modifier.size(12.dp),
                        tint = Color(0xFFD0BCFF)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text("Preview & Cut".localize(appLanguage), fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFFD0BCFF))
                }
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
}

@Composable
fun TimelineMappingAndReorderPanel(
    viewModel: com.ritvyom.yashoraReelgenerator.presentation.viewmodels.MainViewModel,
    appLanguageState: String,
    activeScenes: List<Scene>,
    selectedSceneIndex: Int,
    onReplaceAsset: (Int) -> Unit = {}
) {
    var sceneToDeleteIndex by remember { mutableStateOf<Int?>(null) }
    val mediaStatusMap by MediaFetchingStatusMonitor.statusMap.collectAsState()

    val successCount = activeScenes.count { sc ->
        val st = mediaStatusMap[sc.sceneNumber]?.status ?: sc.mediaFetchStatus ?: if (!sc.mediaPath.isNullOrEmpty()) "SUCCESS" else ""
        st == "SUCCESS"
    }
    val failedScenes = activeScenes.mapIndexedNotNull { idx, sc ->
        val st = mediaStatusMap[sc.sceneNumber]?.status ?: sc.mediaFetchStatus
        if (st == "FAILED") idx to sc else null
    }
    val sourcesBreakdown = activeScenes.mapNotNull { sc ->
        mediaStatusMap[sc.sceneNumber]?.sourceUsed ?: sc.mediaSourceUsed
    }.filter { it.isNotBlank() && !it.contains("Failed", ignoreCase = true) }
     .groupingBy { it }
     .eachCount()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Media Fetching Status Monitor Card
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
            border = BorderStroke(1.dp, if (failedScenes.isNotEmpty()) MaterialTheme.colorScheme.error.copy(alpha = 0.4f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
        ) {
            Column(modifier = Modifier.padding(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(
                            imageVector = Icons.Default.Analytics,
                            contentDescription = null,
                            tint = if (failedScenes.isNotEmpty()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Media Fetching Status Monitor".localize(appLanguageState),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Box(
                        modifier = Modifier
                            .background(
                                if (failedScenes.isEmpty()) Color(0xFF2E7D32).copy(alpha = 0.2f) else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f),
                                RoundedCornerShape(10.dp)
                            )
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "$successCount/${activeScenes.size} Mapped",
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (failedScenes.isEmpty()) Color(0xFF81C784) else MaterialTheme.colorScheme.error
                        )
                    }
                }

                // Sources Breakdown row
                if (sourcesBreakdown.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Active Sources:".localize(appLanguageState),
                            fontSize = 9.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        sourcesBreakdown.forEach { (src, count) ->
                            Box(
                                modifier = Modifier
                                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 5.dp, vertical = 1.dp)
                            ) {
                                Text(
                                    text = "$src: $count",
                                    fontSize = 8.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }
                }

                // If any scenes failed: show warning alert + Retry All Failed button
                if (failedScenes.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
                            .padding(6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(13.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "${failedScenes.size} " + "scene(s) failed all media sources".localize(appLanguageState),
                                fontSize = 9.sp,
                                color = MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Button(
                            onClick = {
                                failedScenes.forEach { (idx, _) ->
                                    viewModel.refetchSceneMedia(idx)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.onError
                            ),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.height(24.dp),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(10.dp))
                            Spacer(modifier = Modifier.width(3.dp))
                            Text("Retry All Failed".localize(appLanguageState), fontSize = 8.5.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

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
                val liveStatus = mediaStatusMap[scene.sceneNumber]
                val currentStatus = liveStatus?.status ?: scene.mediaFetchStatus ?: if (!scene.mediaPath.isNullOrEmpty()) "SUCCESS" else "PENDING"
                val sourceUsed = liveStatus?.sourceUsed ?: scene.mediaSourceUsed
                val isFailed = currentStatus == "FAILED"
                val isPending = currentStatus == "PENDING"
                val attempted = liveStatus?.attemptedSources ?: emptyList()

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable {
                            viewModel.selectedSceneIndex.value = index
                        }
                        .border(
                            1.5.dp,
                            if (isFailed) MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                            else if (isSelected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                            RoundedCornerShape(12.dp)
                        ),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)
                        else if (isFailed) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.1f)
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
                                        imageVector = if (isFailed) Icons.Default.Warning else Icons.Default.Movie,
                                        contentDescription = null,
                                        tint = if (isFailed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
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

                                    // Media Fetching Status Chip
                                    if (isFailed) {
                                        Box(
                                            modifier = Modifier
                                                .background(MaterialTheme.colorScheme.errorContainer, RoundedCornerShape(4.dp))
                                                .padding(horizontal = 4.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = "⚠️ " + "Fetch Failed".localize(appLanguageState),
                                                fontSize = 8.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onErrorContainer
                                            )
                                        }
                                    } else if (isPending) {
                                        Box(
                                            modifier = Modifier
                                                .background(MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                                                .padding(horizontal = 4.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = "⏳ Fetching...",
                                                fontSize = 8.5.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.onTertiaryContainer
                                            )
                                        }
                                    } else if (!sourceUsed.isNullOrBlank()) {
                                        Box(
                                            modifier = Modifier
                                                .background(Color(0xFF1B5E20).copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                                                .border(0.8.dp, Color(0xFF81C784).copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                                                .padding(horizontal = 4.dp, vertical = 1.dp)
                                        ) {
                                            Text(
                                                text = "✓ $sourceUsed",
                                                fontSize = 8.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF81C784)
                                            )
                                        }
                                    }
                                }

                                if (isFailed && attempted.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(1.dp))
                                    Text(
                                        text = "Tried: ${attempted.joinToString(", ")}",
                                        fontSize = 8.sp,
                                        color = MaterialTheme.colorScheme.error
                                    )
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

                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                // Manual Re-fetch Button
                                Button(
                                    onClick = {
                                        viewModel.selectedSceneIndex.value = index
                                        viewModel.refetchSceneMedia(index)
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (isFailed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.surfaceVariant,
                                        contentColor = if (isFailed) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onSurfaceVariant
                                    ),
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                    modifier = Modifier.height(28.dp),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(12.dp))
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(
                                        text = if (isFailed) "Retry".localize(appLanguageState) else "Re-fetch".localize(appLanguageState),
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                Button(
                                    onClick = {
                                        viewModel.selectedSceneIndex.value = index
                                        onReplaceAsset(index)
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                    ),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                    modifier = Modifier.height(28.dp),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(12.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Replace".localize(appLanguageState), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                }

                                Button(
                                    onClick = {
                                        viewModel.selectedSceneIndex.value = index
                                        val text = scene.narrationText
                                        if (text.isNotBlank() && !viewModel.isPlaceholderNarration(text)) {
                                            viewModel.speakText(text, force = true)
                                        }
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
                                            sceneToDeleteIndex = index
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

    // Confirmation dialog for deleting scene from timeline mapping
    sceneToDeleteIndex?.let { idx ->
        val sceneNum = idx + 1
        AlertDialog(
            onDismissRequest = { sceneToDeleteIndex = null },
            icon = {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = {
                Text(
                    text = "Delete Scene $sceneNum?".localize(appLanguageState),
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to delete Scene $sceneNum? This will permanently remove its video clip and assets from the timeline.".localize(appLanguageState),
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (activeScenes.size > 1) {
                            val copy = activeScenes.toMutableList()
                            if (idx in copy.indices) {
                                copy.removeAt(idx)
                                val reindexed = copy.mapIndexed { i, item ->
                                    item.copy(sceneNumber = i + 1)
                                }
                                viewModel.selectedSceneIndex.value = kotlin.math.max(0, idx - 1)
                                viewModel.activeScenes.value = reindexed
                                viewModel.saveCurrentScenesToDb()
                            }
                        }
                        sceneToDeleteIndex = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Delete".localize(appLanguageState))
                }
            },
            dismissButton = {
                TextButton(onClick = { sceneToDeleteIndex = null }) {
                    Text("Cancel".localize(appLanguageState))
                }
            },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(20.dp)
        )
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
    val context = LocalContext.current
    val aiTtsEnabledState by viewModel.aiTtsEnabled.collectAsState()
    val customMasterVoicePath by viewModel.customMasterVoicePath.collectAsState()
    val customMasterVoiceName by viewModel.customMasterVoiceName.collectAsState()
    val savedVoiceClips by viewModel.savedVoiceClips.collectAsState()
    val isMasterVoiceEnabled by viewModel.isMasterVoiceEnabled.collectAsState()
    val masterVoiceVolume by viewModel.masterVoiceVolume.collectAsState()

    val customBgMusicPath by viewModel.customBgMusicPath.collectAsState()
    val customBgMusicName by viewModel.customBgMusicName.collectAsState()
    val isBgMusicEnabled by viewModel.isBgMusicEnabled.collectAsState()
    val bgMusicVolume by viewModel.bgMusicVolume.collectAsState()
    val bgMusicCategory by viewModel.bgMusicCategory.collectAsState()

    val isSfxEnabled by viewModel.isSfxEnabled.collectAsState()
    val sfxVolume by viewModel.sfxVolume.collectAsState()

    // Recording states from VoiceRecorderManager
    val isRecording by VoiceRecorderManager.isRecording.collectAsState()
    val isPlayingRecording by VoiceRecorderManager.isPlaying.collectAsState()
    val recordingDuration by VoiceRecorderManager.recordingDurationSeconds.collectAsState()
    val recordedAudioFile by VoiceRecorderManager.recordedAudioFile.collectAsState()
    val audioAmplitude by VoiceRecorderManager.audioAmplitude.collectAsState()

    var activeAudioSubTab by remember { mutableIntStateOf(0) } // 0 = Master Voice (Record / Phone), 1 = Multi-Track Mixer, 2 = AI TTS & Export

    var hasRecordPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        )
    }

    val recordPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasRecordPermission = isGranted
        if (isGranted) {
            VoiceRecorderManager.startRecording(context)
        } else {
            Toast.makeText(context, "Microphone permission required for voice recording".localize(appLanguageState), Toast.LENGTH_SHORT).show()
        }
    }

    // Phone Storage Voice Picker Launcher
    val deviceVoicePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val fileName = try {
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    cursor.moveToFirst()
                    cursor.getString(nameIndex)
                } ?: "My_Voice.m4a"
            } catch (e: Exception) {
                "My_Voice.m4a"
            }
            val cachedFile = VoiceRecorderManager.copyUriToCache(context, uri, "master_voice")
            if (cachedFile != null) {
                viewModel.setCustomMasterVoice(cachedFile.absolutePath, fileName)
                viewModel.saveVoiceFileToSavedClips(
                    sourceFile = cachedFile,
                    title = fileName.substringBeforeLast(".").take(30),
                    engineTag = "Imported Voice",
                    voiceName = "Device Audio",
                    scriptText = fileName
                )
                Toast.makeText(context, "Voice attached & saved to Library: $fileName".localize(appLanguageState), Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Phone Storage BGM Picker Launcher
    val deviceMusicPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val fileName = try {
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    cursor.moveToFirst()
                    cursor.getString(nameIndex)
                } ?: "My_Music.mp3"
            } catch (e: Exception) {
                "My_Music.mp3"
            }
            val cachedFile = VoiceRecorderManager.copyUriToCache(context, uri, "imported_bgm")
            if (cachedFile != null) {
                viewModel.setCustomBgMusic(cachedFile.absolutePath, fileName)
                Toast.makeText(context, "Music added: $fileName".localize(appLanguageState), Toast.LENGTH_SHORT).show()
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(4.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Audio Mode Selector Tabs
        TabRow(
            selectedTabIndex = activeAudioSubTab,
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
            modifier = Modifier.clip(RoundedCornerShape(12.dp))
        ) {
            Tab(
                selected = activeAudioSubTab == 0,
                onClick = { activeAudioSubTab = 0 },
                text = { Text("🎙️ Master Voice", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
            )
            Tab(
                selected = activeAudioSubTab == 1,
                onClick = { activeAudioSubTab = 1 },
                text = { Text("🎚️ Multi-Layers", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
            )
            Tab(
                selected = activeAudioSubTab == 2,
                onClick = { activeAudioSubTab = 2 },
                text = { Text("🤖 TTS & Export", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
            )
        }

        when (activeAudioSubTab) {
            0 -> {
                // ==================== TAB 0: MASTER VOICEOVER (SINGLE RECORD OR IMPORT FOR ALL FRAMES) ====================
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    // Active Status Card
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (!customMasterVoicePath.isNullOrBlank()) Color(0xFF00E676).copy(alpha = 0.12f)
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                        ),
                        border = BorderStroke(
                            1.dp,
                            if (!customMasterVoicePath.isNullOrBlank()) Color(0xFF00E676).copy(alpha = 0.5f)
                            else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                        )
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (!customMasterVoicePath.isNullOrBlank()) Icons.Default.CheckCircle else Icons.Default.RecordVoiceOver,
                                contentDescription = null,
                                tint = if (!customMasterVoicePath.isNullOrBlank()) Color(0xFF00E676) else MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (!customMasterVoicePath.isNullOrBlank()) "Active Master Voice: ${customMasterVoiceName ?: "Custom Recording"}"
                                    else "No Master Voice attached yet (Optional)".localize(appLanguageState),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = if (!customMasterVoicePath.isNullOrBlank()) "Playing continuous single voiceover across all scenes/frames".localize(appLanguageState)
                                    else "Record once or import audio to cover all frames simultaneously:".localize(appLanguageState),
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (!customMasterVoicePath.isNullOrBlank()) {
                                IconButton(onClick = { viewModel.clearCustomMasterVoice() }) {
                                    Icon(Icons.Default.Close, contentDescription = "Clear", tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }

                    // Section A: 🎙️ Live Voice Recording (1-Click for full video)
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(14.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "1. Record Your Voice for All Frames (एक बार में पूरा वॉयस रिकॉर्ड करें)".localize(appLanguageState),
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                IconButton(
                                    onClick = {
                                        if (isRecording) {
                                            val file = VoiceRecorderManager.stopRecording()
                                            if (file != null) {
                                                viewModel.setCustomMasterVoice(file.absolutePath, "In-App Recording.m4a")
                                                viewModel.saveVoiceFileToSavedClips(
                                                    sourceFile = file,
                                                    title = "Live Recording ${java.text.SimpleDateFormat("dd MMM HH:mm", java.util.Locale.getDefault()).format(java.util.Date())}",
                                                    engineTag = "Voice Recording",
                                                    voiceName = "User Voice",
                                                    scriptText = "In-App Recorded Narration"
                                                )
                                                Toast.makeText(context, "Recording applied & saved to Voice Library!".localize(appLanguageState), Toast.LENGTH_SHORT).show()
                                            }
                                        } else {
                                            if (hasRecordPermission) {
                                                VoiceRecorderManager.startRecording(context)
                                            } else {
                                                recordPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                            }
                                        }
                                    },
                                    modifier = Modifier
                                        .size(60.dp)
                                        .clip(CircleShape)
                                        .background(if (isRecording) Color(0xFFFF1744) else MaterialTheme.colorScheme.primary)
                                ) {
                                    Icon(
                                        imageVector = if (isRecording) Icons.Default.Stop else Icons.Default.Mic,
                                        contentDescription = "Record Voice",
                                        tint = Color.White,
                                        modifier = Modifier.size(30.dp)
                                    )
                                }

                                if (recordedAudioFile != null && !isRecording) {
                                    IconButton(
                                        onClick = {
                                            if (isPlayingRecording) {
                                                VoiceRecorderManager.stopPlayback()
                                            } else {
                                                VoiceRecorderManager.playRecordedAudio(context)
                                            }
                                        },
                                        modifier = Modifier
                                            .size(44.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.secondaryContainer)
                                    ) {
                                        Icon(
                                            imageVector = if (isPlayingRecording) Icons.Default.Pause else Icons.Default.PlayArrow,
                                            contentDescription = "Play Preview",
                                            tint = MaterialTheme.colorScheme.onSecondaryContainer
                                        )
                                    }

                                    IconButton(
                                        onClick = {
                                            VoiceRecorderManager.cancelRecording()
                                            viewModel.clearCustomMasterVoice()
                                            Toast.makeText(context, "Recording cleared".localize(appLanguageState), Toast.LENGTH_SHORT).show()
                                        },
                                        modifier = Modifier
                                            .size(44.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.errorContainer)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Delete",
                                            tint = MaterialTheme.colorScheme.onErrorContainer
                                        )
                                    }
                                }
                            }

                            // Live Timer & Waveform
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = String.format("%02d:%02d", recordingDuration / 60, recordingDuration % 60),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isRecording) Color(0xFFFF1744) else MaterialTheme.colorScheme.onSurface
                                )
                                if (isRecording) {
                                    LinearProgressIndicator(
                                        progress = { audioAmplitude },
                                        modifier = Modifier
                                            .width(130.dp)
                                            .height(8.dp)
                                            .clip(RoundedCornerShape(4.dp)),
                                        color = Color(0xFFFF1744),
                                        trackColor = Color(0xFFFFCDD2)
                                    )
                                }
                            }
                        }
                    }

                    // Section B: 📁 Pick Pre-recorded Voice from Phone
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "2. Pick Recorded Audio / Voice from Phone (फ़ोन स्टोरेज से ऑडियो लाएं)".localize(appLanguageState),
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )

                            Button(
                                onClick = { deviceVoicePicker.launch("audio/*") },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            ) {
                                Icon(Icons.Default.AudioFile, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Browse Phone Audio (.mp3 / .wav / .m4a)".localize(appLanguageState), fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    // Section C: 📚 Select from Saved Voice & ElevenLabs Library
                    if (savedVoiceClips.isNotEmpty()) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "3. Choose from Saved Voice Library (${savedVoiceClips.size})".localize(appLanguageState),
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Text(
                                    text = "All voice clips generated for video or ElevenLabs are saved here:".localize(appLanguageState),
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                                ) {
                                    items(savedVoiceClips.take(10)) { clip ->
                                        val isSelected = customMasterVoicePath == clip.filePath
                                        Surface(
                                            shape = RoundedCornerShape(10.dp),
                                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                            border = BorderStroke(
                                                1.dp,
                                                if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                                            ),
                                            modifier = Modifier.clickable {
                                                if (isSelected) {
                                                    viewModel.clearCustomMasterVoice()
                                                } else {
                                                    viewModel.setCustomMasterVoice(clip.filePath, clip.title)
                                                    Toast.makeText(context, "Voice attached: ${clip.title}".localize(appLanguageState), Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    imageVector = if (isSelected) Icons.Default.CheckCircle else Icons.Default.MusicNote,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(16.dp),
                                                    tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Column {
                                                    Text(
                                                        text = clip.title.take(20),
                                                        fontSize = 10.5.sp,
                                                        fontWeight = FontWeight.SemiBold
                                                    )
                                                    Text(
                                                        text = clip.engineName,
                                                        fontSize = 8.5.sp,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
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
            }

            1 -> {
                // ==================== TAB 1: MULTI-LAYER TRACK MIXER ====================
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    // Track 1: Master Voice Volume & Mute
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Mic, contentDescription = null, tint = Color(0xFFFF5252), modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Layer 1: Voiceover Track".localize(appLanguageState), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("${(masterVoiceVolume.toFloat() * 100).toInt()}%", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Switch(
                                        checked = isMasterVoiceEnabled,
                                        onCheckedChange = { viewModel.setMasterVoiceEnabled(it) }
                                    )
                                }
                            }
                            Slider(
                                value = masterVoiceVolume.toFloat(),
                                onValueChange = { viewModel.setMasterVoiceVolume(it) },
                                valueRange = 0f..2f,
                                enabled = isMasterVoiceEnabled
                            )
                        }
                    }

                    // Track 2: Background Music Volume & Mood Selector
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.MusicNote, contentDescription = null, tint = Color(0xFF00E5FF), modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Layer 2: Background Music Track".localize(appLanguageState), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("${(bgMusicVolume.toFloat() * 100).toInt()}%", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Switch(
                                        checked = isBgMusicEnabled,
                                        onCheckedChange = { viewModel.setBgMusicEnabled(it) }
                                    )
                                }
                            }
                            Slider(
                                value = bgMusicVolume.toFloat(),
                                onValueChange = { viewModel.setBgMusicVolume(it) },
                                valueRange = 0f..1f,
                                enabled = isBgMusicEnabled
                            )

                            // Quick Mood & Device BGM Picker
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Button(
                                    onClick = { deviceMusicPicker.launch("audio/*") },
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.weight(1f).height(34.dp),
                                    contentPadding = PaddingValues(horizontal = 6.dp)
                                ) {
                                    Icon(Icons.Default.Folder, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Phone Music".localize(appLanguageState), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }

                                val categories = listOf("Cinematic", "Lo-Fi Beats", "Dramatic", "Upbeat Energy", "None")
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.weight(2f)) {
                                    items(categories) { cat ->
                                        FilterChip(
                                            selected = bgMusicCategory == cat,
                                            onClick = { viewModel.bgMusicCategory.value = cat },
                                            label = { Text(cat, fontSize = 10.sp) },
                                            shape = RoundedCornerShape(6.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Track 3: Sound Effects (SFX) Track
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.GraphicEq, contentDescription = null, tint = Color(0xFFFFD700), modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Layer 3: Sound Effects (SFX)".localize(appLanguageState), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("${(sfxVolume.toFloat() * 100).toInt()}%", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Switch(
                                        checked = isSfxEnabled,
                                        onCheckedChange = { viewModel.setSfxEnabled(it) }
                                    )
                                }
                            }
                            Slider(
                                value = sfxVolume.toFloat(),
                                onValueChange = { viewModel.setSfxVolume(it) },
                                valueRange = 0f..1f,
                                enabled = isSfxEnabled
                            )

                            // SFX Quick Sound Triggers
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                val sfxList = listOf("Whoosh", "Boom", "Pop", "Sci-Fi Laser", "Digital Hit")
                                items(sfxList) { sfx ->
                                    FilledTonalButton(
                                        onClick = { viewModel.playSfx(sfx) },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                        modifier = Modifier.height(28.dp),
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text("🔊 $sfx", fontSize = 9.5.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            2 -> {
                // ==================== TAB 2: OPTIONAL SCENE TTS & SINGLE MASTER EXPORT ====================
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Optional AI Text-to-Speech".localize(appLanguageState), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                Text("Generate voice from narration text if no custom voice is uploaded".localize(appLanguageState), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(
                                checked = aiTtsEnabledState,
                                onCheckedChange = { viewModel.setAiTtsEnabled(it) }
                            )
                        }
                    }

                    Text("Edit Narration Script for Selected Scene #${selectedSceneIndex + 1}:".localize(appLanguageState), fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        value = currentScene.narrationText,
                        onValueChange = { viewModel.editSceneNarrationText(selectedSceneIndex, it) },
                        modifier = Modifier.fillMaxWidth().height(90.dp),
                        shape = RoundedCornerShape(10.dp),
                        placeholder = { Text("Enter narration text...".localize(appLanguageState), fontSize = 11.sp) }
                    )

                    // Card for Single Master Audio File export
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                    ) {
                        Column(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.MusicNote, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Single Master Voiceover Audio (.WAV)".localize(appLanguageState), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    Text("Download or share continuous voice narration for Kinemaster & external video editors".localize(appLanguageState), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
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
    appLanguageState: String,
    onDismiss: () -> Unit = {}
) {
    var activeTab by remember { mutableStateOf(0) } // 0: Search Online Stock, 1: Device Files & Gallery

    val initialQuery = remember(currentScene.sceneNumber) {
        val stopWords = setOf(
            "cinematic", "shot", "of", "a", "an", "the", "in", "on", "at", "with", "and", "or", "to", "for", "from",
            "by", "is", "are", "was", "were", "be", "view", "scene", "background", "photo", "video", "footage", "people", "like", "detailed",
            "narrative", "captivating", "artistic", "representation", "cinematic narrative scene", "cinematic visual"
        )
        val validKeywords = currentScene.keywords.filter { kw ->
            val l = kw.lowercase().trim()
            l.isNotEmpty() && !stopWords.contains(l) && l != "cinematic narrative scene" && l != "cinematic visual"
        }
        if (validKeywords.isNotEmpty()) {
            validKeywords.take(2).joinToString(" ")
        } else {
            val narration = currentScene.narrationText.ifEmpty { currentScene.subtitle }
            val extracted = if (narration.isNotEmpty()) {
                com.ritvyom.yashoraReelgenerator.data.remote.GeminiService.extractSemiSemanticKeyword(narration)
            } else ""
            if (extracted.isNotEmpty() && !extracted.contains("cinematic narrative scene")) {
                extracted
            } else {
                val raw = currentScene.visualPrompt.substringBefore("(").trim()
                val words = raw.replace(Regex("[^\\p{L}\\p{N}\\s]"), " ").split(Regex("\\s+")).filter { it.isNotEmpty() }
                val meaningful = words.filter { it.lowercase() !in stopWords && it.length > 2 }
                if (meaningful.isNotEmpty()) meaningful.take(3).joinToString(" ") else "investigation crime news"
            }
        }
    }

    var searchQuery by remember(currentScene.sceneNumber) { 
        mutableStateOf(initialQuery) 
    }
    var mediaType by remember(currentScene.sceneNumber) { 
        mutableStateOf(if (currentScene.mediaType.uppercase() == "VIDEO") "VIDEO" else "IMAGE") 
    }
    var selectedSourceFilter by remember { mutableStateOf("ALL") }

    val suggestions by viewModel.alternativeSuggestions.collectAsState()
    val isSearching by viewModel.isSearchingAlternatives.collectAsState()
    val searchError by viewModel.alternativeSearchError.collectAsState()
    val context = LocalContext.current

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        uri?.let {
            viewModel.replaceSceneWithDeviceMedia(selectedSceneIndex, it, context)
            onDismiss()
        }
    }

    val storageFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            viewModel.replaceSceneWithDeviceMedia(selectedSceneIndex, it, context)
            onDismiss()
        }
    }

    val displaySuggestions = remember(suggestions, selectedSourceFilter) {
        if (selectedSourceFilter == "ALL") suggestions
        else suggestions.filter { matchesSourceCatalogFilter(it.source, selectedSourceFilter) }
    }

    val quickTopics = remember {
        listOf(
            "🔥 Trending" to "trending",
            "🎬 Cinematic" to "cinematic",
            "🏙️ City" to "city",
            "🌿 Nature" to "nature",
            "☀️ Summer" to "summer",
            "🌧️ Rain" to "rain",
            "🚗 Travel" to "travel",
            "🌌 Abstract" to "abstract"
        )
    }

    // Trigger initial search automatically for convenience (cached hits are instant!)
    LaunchedEffect(currentScene.sceneNumber, mediaType) {
        viewModel.fetchAlternativeSuggestions(searchQuery, mediaType, aspectRatio, forceRefresh = false)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Primary Mode Switcher: Online Stock Search vs Device Files
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF1B1428))
                .padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Surface(
                onClick = { activeTab = 0 },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(9.dp),
                color = if (activeTab == 0) YouCutOrange else Color.Transparent
            ) {
                Row(
                    modifier = Modifier.padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = if (activeTab == 0) Color.White else Color.Gray,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Search Online Stock".localize(appLanguageState),
                        fontSize = 11.sp,
                        fontWeight = if (activeTab == 0) FontWeight.Bold else FontWeight.Medium,
                        color = if (activeTab == 0) Color.White else Color.Gray
                    )
                }
            }

            Surface(
                onClick = { activeTab = 1 },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(9.dp),
                color = if (activeTab == 1) YouCutOrange else Color.Transparent
            ) {
                Row(
                    modifier = Modifier.padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.FolderOpen,
                        contentDescription = null,
                        tint = if (activeTab == 1) Color.White else Color.Gray,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Device Files & Gallery".localize(appLanguageState),
                        fontSize = 11.sp,
                        fontWeight = if (activeTab == 1) FontWeight.Bold else FontWeight.Medium,
                        color = if (activeTab == 1) Color.White else Color.Gray
                    )
                }
            }
        }

        if (activeTab == 0) {
        // Header with Scene Info + Quick Gallery Shortcut
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Swap Media for Scene #${currentScene.sceneNumber}".localize(appLanguageState),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = if (mediaType == "VIDEO") "📹 Video Clips Mode".localize(appLanguageState) else "🖼️ Photos & Backdrops Mode".localize(appLanguageState),
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.secondary,
                    fontWeight = FontWeight.Medium
                )
            }

            // Direct Replace From Device Gallery Button (Instant, Offline)
            Button(
                onClick = {
                    galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
                },
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.tertiary,
                    contentColor = MaterialTheme.colorScheme.onTertiary
                ),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                modifier = Modifier.height(34.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.AddPhotoAlternate,
                    contentDescription = null,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Replace (Gallery)".localize(appLanguageState),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Primary 'Video' and 'Image' Tabs with instant repository cache preservation
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                .padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // 'Video' Tab
            Surface(
                shape = RoundedCornerShape(9.dp),
                color = if (mediaType == "VIDEO") MaterialTheme.colorScheme.primary else Color.Transparent,
                modifier = Modifier
                    .weight(1f)
                    .clickable {
                        if (mediaType != "VIDEO") {
                            mediaType = "VIDEO"
                            selectedSourceFilter = "ALL"
                            viewModel.fetchAlternativeSuggestions(searchQuery, "VIDEO", aspectRatio, forceRefresh = false)
                        }
                    }
            ) {
                Row(
                    modifier = Modifier.padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Videocam,
                        contentDescription = null,
                        tint = if (mediaType == "VIDEO") MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Video".localize(appLanguageState),
                        fontSize = 12.sp,
                        fontWeight = if (mediaType == "VIDEO") FontWeight.Bold else FontWeight.Medium,
                        color = if (mediaType == "VIDEO") MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // 'Image' Tab
            Surface(
                shape = RoundedCornerShape(9.dp),
                color = if (mediaType == "IMAGE") MaterialTheme.colorScheme.primary else Color.Transparent,
                modifier = Modifier
                    .weight(1f)
                    .clickable {
                        if (mediaType != "IMAGE") {
                            mediaType = "IMAGE"
                            selectedSourceFilter = "ALL"
                            viewModel.fetchAlternativeSuggestions(searchQuery, "IMAGE", aspectRatio, forceRefresh = false)
                        }
                    }
            ) {
                Row(
                    modifier = Modifier.padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Image,
                        contentDescription = null,
                        tint = if (mediaType == "IMAGE") MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Image".localize(appLanguageState),
                        fontSize = 12.sp,
                        fontWeight = if (mediaType == "IMAGE") FontWeight.Bold else FontWeight.Medium,
                        color = if (mediaType == "IMAGE") MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Search bar + Search button
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
                placeholder = { Text("Search ${if (mediaType == "VIDEO") "videos" else "photos"}...".localize(appLanguageState), fontSize = 11.sp) }
            )

            Button(
                onClick = {
                    viewModel.fetchAlternativeSuggestions(searchQuery, mediaType, aspectRatio, forceRefresh = true)
                },
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp),
                modifier = Modifier.height(44.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search Alternatives".localize(appLanguageState),
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        // Quick Category Filter Chips
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(quickTopics.size) { i ->
                val (label, queryVal) = quickTopics[i]
                val isSelected = searchQuery.equals(queryVal, ignoreCase = true)
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    border = BorderStroke(1.dp, if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent),
                    modifier = Modifier.clickable {
                        searchQuery = queryVal
                        viewModel.fetchAlternativeSuggestions(queryVal, mediaType, aspectRatio, forceRefresh = false)
                    }
                ) {
                    Text(
                        text = label.localize(appLanguageState),
                        fontSize = 10.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }
        }

        // Source Filter Chips (Pexels, Pixabay, Unsplash, Wikimedia, NASA, Archive.org, NHTSA, FDA, WHO, etc.)
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(ALL_MEDIA_SOURCES_CATALOG.size) { idx ->
                val catalogItem = ALL_MEDIA_SOURCES_CATALOG[idx]
                val isSel = selectedSourceFilter == catalogItem.id
                val count = if (catalogItem.id == "ALL") suggestions.size else suggestions.count { matchesSourceCatalogFilter(it.source, catalogItem.id) }
                val label = if (catalogItem.id == "ALL") {
                    "🌐 All Sources ($count)"
                } else if (count > 0) {
                    "${catalogItem.emoji} ${catalogItem.displayName} ($count)"
                } else {
                    "${catalogItem.emoji} ${catalogItem.displayName}"
                }
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (isSel) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, if (isSel) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
                    modifier = Modifier.clickable {
                        selectedSourceFilter = catalogItem.id
                    }
                ) {
                    Text(
                        text = label,
                        fontSize = 9.sp,
                        fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSel) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }
        }

        if (isSearching && suggestions.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp), strokeWidth = 3.dp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        if (mediaType == "VIDEO") "Searching Pexels, Pixabay, NASA & Wikimedia videos...".localize(appLanguageState) else "Fetching Pexels, Unsplash, Pixabay & Wikimedia photos...".localize(appLanguageState),
                        fontSize = 11.sp,
                        color = Color.Gray
                    )
                }
            }
        } else if (displaySuggestions.isEmpty()) {
            // Never leave an empty or broken section! Provide direct Replace from Gallery & Fallbacks
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(16.dp).fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.Collections,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = (searchError ?: "No media clips found for \"$searchQuery\"").localize(appLanguageState),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = {
                                galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
                            },
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Pick from Phone Gallery".localize(appLanguageState), fontSize = 10.sp)
                        }

                        OutlinedButton(
                            onClick = {
                                searchQuery = "cinematic"
                                selectedSourceFilter = "ALL"
                                viewModel.fetchAlternativeSuggestions("cinematic", mediaType, aspectRatio, forceRefresh = true)
                            },
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Text("Try 'Cinematic'".localize(appLanguageState), fontSize = 10.sp)
                        }
                    }
                }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Tap any ${if (mediaType == "VIDEO") "video" else "photo"} to apply to Scene #${currentScene.sceneNumber}:".localize(appLanguageState),
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "${displaySuggestions.size} results".localize(appLanguageState),
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.secondary,
                    fontWeight = FontWeight.Bold
                )
            }
            
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
            ) {
                items(displaySuggestions.size) { index ->
                    val item = displaySuggestions[index]
                    val isCurrent = currentScene.mediaPath == item.url
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .border(
                                width = if (isCurrent) 2.5.dp else 1.dp,
                                color = if (isCurrent) MaterialTheme.colorScheme.primary else Color.Transparent,
                                shape = RoundedCornerShape(10.dp)
                            )
                            .clickable {
                                viewModel.swapSceneMedia(selectedSceneIndex, item.url, item.mediaType)
                                val msg = if (item.mediaType == "VIDEO") "Video applied to scene #${currentScene.sceneNumber}!" else "Photo applied to scene #${currentScene.sceneNumber}!"
                                Toast.makeText(context, msg.localize(appLanguageState), Toast.LENGTH_SHORT).show()
                            }
                    ) {
                        // Thumbnail representation
                        val imageSource = item.thumbnailUrl.ifEmpty { item.url }
                        val itemUa = remember(imageSource) {
                            when {
                                imageSource.contains("nekos.best", ignoreCase = true) ->
                                    "YashoraReelGenerator (Ritvyom@gmail.com)"
                                imageSource.contains("wikimedia.org", ignoreCase = true) || imageSource.contains("wikipedia.org", ignoreCase = true) ->
                                    "YashoraReelGenerator/2.0 (https://ai.studio; yashoratechnologies@gmail.com) Android/14 OkHttp/4.12"
                                else ->
                                    "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
                            }
                        }
                        val imageRequest = remember(imageSource, itemUa) {
                            coil.request.ImageRequest.Builder(context)
                                .data(imageSource)
                                .setHeader("User-Agent", itemUa)
                                .crossfade(true)
                                .build()
                        }
                        coil.compose.AsyncImage(
                            model = imageRequest,
                            contentDescription = item.title.ifEmpty { "Alternative suggestion" },
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )

                        // Video badge and overlay
                        if (item.mediaType == "VIDEO") {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.verticalGradient(
                                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.65f))
                                        )
                                    )
                            )

                            // Play icon badge
                            Box(
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                                    .padding(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = "Video clip",
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                            }

                            // Bottom details: Duration + Source
                            Row(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .fillMaxWidth()
                                    .padding(horizontal = 4.dp, vertical = 2.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = getDisplaySourceName(item.source),
                                    fontSize = 7.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.LightGray
                                )
                                if (item.durationSeconds > 0) {
                                    Text(
                                        text = "${item.durationSeconds}s",
                                        fontSize = 7.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                            }
                        } else {
                            // Image source tag
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(topStart = 4.dp))
                                    .padding(horizontal = 4.dp, vertical = 1.dp)
                                ) {
                                    Text(
                                        text = getDisplaySourceName(item.source),
                                        fontSize = 7.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                            }

                            // Active Selection Overlay
                            if (isCurrent) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(YouCutOrange.copy(alpha = 0.45f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = "Selected",
                                        tint = Color.White,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        } else {
            // TAB 1: Device Files & Phone Gallery (Local & Offline)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Card 1: System Photo & Video Gallery
                Surface(
                    onClick = {
                        galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
                    },
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFF1F1730),
                    border = BorderStroke(1.2.dp, YouCutOrange.copy(alpha = 0.7f)),
                    modifier = Modifier.fillMaxWidth().testTag("gallery_picker_button")
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(YouCutOrange.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Collections,
                                contentDescription = null,
                                tint = YouCutOrange,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Pick from Phone Gallery".localize(appLanguageState),
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Select any video clip or photo from your device gallery".localize(appLanguageState),
                                fontSize = 10.sp,
                                color = Color.LightGray
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = YouCutOrange,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // Card 2: Device File Explorer / Storage
                Surface(
                    onClick = {
                        storageFileLauncher.launch("*/*")
                    },
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFF171B30),
                    border = BorderStroke(1.2.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.7f)),
                    modifier = Modifier.fillMaxWidth().testTag("file_storage_picker_button")
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.FolderOpen,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Browse Device Files & Storage".localize(appLanguageState),
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Pick MP4, MKV, WebM, MOV, JPG, PNG from Downloads or storage".localize(appLanguageState),
                                fontSize = 10.sp,
                                color = Color.LightGray
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // Scene Media Info Box
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF14131A),
                    border = BorderStroke(1.dp, Color(0xFF282436)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "Current Scene #${currentScene.sceneNumber} Info".localize(appLanguageState),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Format: ${currentScene.mediaType}  •  Duration: ${currentScene.durationSeconds}s",
                            fontSize = 10.sp,
                            color = Color.Gray
                        )
                        if (!currentScene.mediaPath.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(2.dp))
                            val pathStr = currentScene.mediaPath ?: ""
                            Text(
                                text = "Source: ${pathStr.takeLast(45)}",
                                fontSize = 9.sp,
                                color = Color.DarkGray,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }

        // Bottom Done / Close Button
        Button(
            onClick = { onDismiss() },
            colors = ButtonDefaults.buttonColors(
                containerColor = YouCutOrange,
                contentColor = Color.White
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(42.dp)
                .testTag("replace_panel_close_button")
        ) {
            Icon(Icons.Default.Done, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Done / Close".localize(appLanguageState), fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}
