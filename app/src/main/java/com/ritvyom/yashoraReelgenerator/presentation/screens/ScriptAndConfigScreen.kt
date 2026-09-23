package com.ritvyom.yashoraReelgenerator.presentation.screens

import androidx.activity.compose.BackHandler
import kotlinx.coroutines.delay
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ritvyom.yashoraReelgenerator.presentation.utils.EditorConstants
import com.ritvyom.yashoraReelgenerator.presentation.utils.localize
import com.ritvyom.yashoraReelgenerator.presentation.viewmodels.MainViewModel
import com.ritvyom.yashoraReelgenerator.domain.models.PreviewScene
import com.ritvyom.yashoraReelgenerator.presentation.components.*
import coil.compose.AsyncImage

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ScriptAndConfigScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit,
    onNavigateToGeneration: () -> Unit,
    onNavigateToVoiceLibrary: (() -> Unit)? = null,
    onOpenScriptGenerator: (() -> Unit)? = null,
    onOpenVoiceProvider: (() -> Unit)? = null
) {
    val scriptTextState by viewModel.scriptText.collectAsState()
    val topicContextState by viewModel.topicContext.collectAsState()
    val appLanguageState by viewModel.appLanguage.collectAsState()
    val selectedStyleState by viewModel.selectedStyleName.collectAsState()
    val selectedPublishingStyleState by viewModel.selectedPublishingStyle.collectAsState()
    val selectedVoiceState by viewModel.selectedVoiceName.collectAsState()
    val selectedCategoryState by viewModel.selectedVoiceCategory.collectAsState()
    val speedState by viewModel.voiceSpeed.collectAsState()
    val pitchState by viewModel.voicePitch.collectAsState()
    val emotionState by viewModel.voiceEmotion.collectAsState()
    val qualityState by viewModel.voiceQuality.collectAsState()
    val languageState by viewModel.selectedLanguage.collectAsState()

    val ratioState by viewModel.selectedAspectRatio.collectAsState()
    val resolutionState by viewModel.selectedResolution.collectAsState()
    val fpsState by viewModel.selectedFps.collectAsState()

    val bgMusicEnabled by viewModel.bgMusicEnabled.collectAsState()
    val musicCategoryState by viewModel.bgMusicCategory.collectAsState()
    val musicVolumeState by viewModel.bgMusicVolume.collectAsState()
    val voiceVolumeState by viewModel.voiceVolume.collectAsState()

    val scrollState = rememberScrollState()

    var activeVoiceTabState by remember { mutableIntStateOf(0) } // 0 = Male, 1 = Female, 2 = Child

    val context = androidx.compose.ui.platform.LocalContext.current
    var showOfflineDialog by remember { mutableStateOf(false) }
    var showBackOnlineBanner by remember { mutableStateOf(false) }

    LaunchedEffect(showBackOnlineBanner) {
        if (showBackOnlineBanner) {
            delay(3000)
            showBackOnlineBanner = false
        }
    }

    var scriptTopicInput by remember { mutableStateOf("") }
    var selectedDurationOption by remember { mutableStateOf("Medium") }
    val isGeneratingScriptState by viewModel.isGeneratingScript.collectAsState()
    val scriptGenErrorState by viewModel.scriptGenerationError.collectAsState()

    val isGeneratingPreview by viewModel.isGeneratingPreview.collectAsState()
    val previewProgress by viewModel.previewProgress.collectAsState()
    val previewStatus by viewModel.previewStatus.collectAsState()
    val previewScenes by viewModel.previewScenesList.collectAsState()
    val previewError by viewModel.previewError.collectAsState()

    BackHandler(enabled = showOfflineDialog) {
        showOfflineDialog = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Configure Reel Pipeline".localize(appLanguageState),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = Icons.AutoMirrored.Default.ArrowBack, contentDescription = "Back".localize(appLanguageState), tint = MaterialTheme.colorScheme.secondary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(scrollState)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Section 1: Write / Paste Reel Script
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CardSectionHeader(index = 1, title = "Write / Paste Reel Script".localize(appLanguageState))
                    
                    // Small tab-style button at the top right to open full AI script writer
                    AssistChip(
                        onClick = {
                            onOpenScriptGenerator?.invoke()
                        },
                        label = {
                            Text(
                                text = "Generate with AI".localize(appLanguageState),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(14.dp)
                            )
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f),
                            labelColor = MaterialTheme.colorScheme.primary
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                    )
                }

                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = scriptTextState,
                        onValueChange = { text ->
                            viewModel.scriptText.value = text
                        },
                        placeholder = { Text("Enter or paste your script here. The AI will segment it into scenes, narrate compiling voices, and generate visual prompts automatically...".localize(appLanguageState)) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 90.dp, max = 160.dp),
                        shape = RoundedCornerShape(20.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = Color(0xFFF8F9FF),
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline
                        ),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default)
                    )

                    val wordCount = remember(scriptTextState) {
                        if (scriptTextState.trim().isEmpty()) 0 else scriptTextState.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.size
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "💡 Leave it blank to auto-generate a viral script on the next step!".localize(appLanguageState),
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "$wordCount " + "words".localize(appLanguageState),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }

                    // Quick prompts helpers
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val quickPrompts = listOf("Space Adventure", "Finance Tips", "Anime Story", "Tech News")
                        quickPrompts.forEach { prompt ->
                            SuggestionChip(
                                onClick = {
                                    val script = when(prompt) {
                                        "Space Adventure" -> "Deep in the dark space galaxy, an explorer spacecraft discovers a strange pulsing wormhole. As they enter, glowing violet particles surround the ship."
                                        "Finance Tips" -> "Here are three secrets wealthy people never tell you. First, pay yourself first using auto-invest. Second, never buy consumables on debt. Third, compound interest is real magic."
                                        "Anime Story" -> "In a village protected by steam barriers, a legendary swordsman draws his lightning blade. Dark clouds swirl as dragons gather on mountains."
                                        else -> "Top technology trends for this week. Local Android systems are adopting fully isolated local AI pipelines. Developer productivity has scaled up by four times."
                                    }
                                    viewModel.scriptText.value = script
                                },
                                label = {
                                    Text(
                                        text = prompt.localize(appLanguageState),
                                        fontSize = 11.sp,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            )
                        }
                    }
                }

                if (scriptTextState.trim().isNotEmpty()) {
                    // --- Storyboard Slideshow Pre-viewer Section ---
                    Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Default.Slideshow,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Interactive Slideshow Mockup".localize(appLanguageState),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Text(
                            text = "Extract script segments and fetch real-time stock media matching Prime Vivid, Aura Cinematics, and Lumina Stock to simulate a live slideshow!".localize(appLanguageState),
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 15.sp,
                            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                        )

                        if (isGeneratingPreview) {
                            ScriptAnalysisSkeleton(
                                statusText = previewStatus,
                                progress = previewProgress,
                                appLanguageState = appLanguageState,
                                modifier = Modifier.fillMaxWidth()
                            )
                        } else if (!previewError.isNullOrEmpty()) {
                            Text(
                                text = "Error: $previewError",
                                color = MaterialTheme.colorScheme.error,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }

                        if (previewScenes.isNotEmpty() && !isGeneratingPreview) {
                            // Render Slideshow Player!
                            var previewIndex by remember { mutableStateOf(0) }
                            if (previewIndex >= previewScenes.size) previewIndex = 0
                            val currentPreviewScene = previewScenes[previewIndex]

                            var isSlideshowPlaying by remember { mutableStateOf(false) }

                            LaunchedEffect(isSlideshowPlaying, previewIndex) {
                                if (isSlideshowPlaying) {
                                    delay(4000L) // Auto advance every 4 seconds
                                    previewIndex = (previewIndex + 1) % previewScenes.size
                                }
                            }

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFF0F0B18), RoundedCornerShape(16.dp))
                                    .padding(12.dp)
                            ) {
                                // 1. Relative Ratio Frame Container (Main Slide preview)
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(260.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color.Black),
                                    contentAlignment = Alignment.Center
                                ) {
                                    AsyncImage(
                                        model = currentPreviewScene.selectedUrl,
                                        contentDescription = currentPreviewScene.visualPrompt,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                    )

                                    // Top Info Overlays (Scene Number & Source Badge)
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .align(Alignment.TopCenter)
                                            .padding(10.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(12.dp))
                                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                        ) {
                                            Text(
                                                text = "Scene ${currentPreviewScene.sceneNumber} / ${previewScenes.size}".localize(appLanguageState),
                                                color = Color.White,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }

                                        Box(
                                            modifier = Modifier
                                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.85f), RoundedCornerShape(12.dp))
                                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                        ) {
                                            val sourceName = when (currentPreviewScene.selectedUrl) {
                                                currentPreviewScene.unsplashUrl -> "Prime Vivid"
                                                currentPreviewScene.pexelsUrl -> "Aura Cinematics"
                                                currentPreviewScene.pixabayUrl -> "Lumina Stock"
                                                else -> "Direct Link"
                                            }
                                            Text(
                                                text = sourceName.localize(appLanguageState),
                                                color = Color.White,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.ExtraBold
                                            )
                                        }
                                    }

                                    // Bottom Subtitle Narration Overlay
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .align(Alignment.BottomCenter)
                                            .background(
                                                Brush.verticalGradient(
                                                    colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f))
                                                )
                                            )
                                            .padding(16.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(
                                            text = currentPreviewScene.narrationText,
                                            color = Color.White,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium,
                                            textAlign = TextAlign.Center,
                                            lineHeight = 16.sp
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                // 2. Media Source Selection row to pull live from each integrated database
                                Text(
                                    text = "Inspect Media Sources:".localize(appLanguageState),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White.copy(alpha = 0.7f),
                                    modifier = Modifier.padding(bottom = 6.dp)
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    val sourcesList = listOf(
                                        Triple("Prime Vivid", currentPreviewScene.unsplashUrl, Color(0xFFE25B5B)),
                                        Triple("Aura Cinematics", currentPreviewScene.pexelsUrl, Color(0xFF07A081)),
                                        Triple("Lumina Stock", currentPreviewScene.pixabayUrl, Color(0xFF1E88E5))
                                    )

                                    sourcesList.forEach { (name, link, activeBgColor) ->
                                        val isActive = currentPreviewScene.selectedUrl == link
                                        Card(
                                            onClick = {
                                                currentPreviewScene.selectedUrl = link
                                                // Trigger state refresh by incrementing a simple counter or resetting preview scenes
                                                viewModel.previewScenesList.value = previewScenes.toList()
                                            },
                                            modifier = Modifier.weight(1f),
                                            shape = RoundedCornerShape(12.dp),
                                            colors = CardDefaults.cardColors(
                                                containerColor = if (isActive) activeBgColor.copy(alpha = 0.25f) else Color(0xFF1C132E)
                                            ),
                                            border = BorderStroke(1.2.dp, if (isActive) activeBgColor else Color.Transparent)
                                        ) {
                                            Box(
                                                modifier = Modifier.padding(8.dp).fillMaxWidth(),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = name,
                                                    fontSize = 10.sp,
                                                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                                                    color = if (isActive) activeBgColor else Color.LightGray
                                                )
                                            }
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                // 3. Controls Bar: Play/Pause, Next, Prev, Prompt description
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    IconButton(
                                        onClick = {
                                            if (previewIndex > 0) previewIndex-- else previewIndex = previewScenes.size - 1
                                        }
                                    ) {
                                        Icon(imageVector = Icons.Default.ChevronLeft, contentDescription = "Prev", tint = Color.White)
                                    }

                                    Button(
                                        onClick = { isSlideshowPlaying = !isSlideshowPlaying },
                                        shape = RoundedCornerShape(20.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (isSlideshowPlaying) Color(0xFFFF007F) else MaterialTheme.colorScheme.primary
                                        )
                                    ) {
                                        Icon(
                                            imageVector = if (isSlideshowPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = if (isSlideshowPlaying) "Pause".localize(appLanguageState) else "Auto Play".localize(appLanguageState),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }

                                    IconButton(
                                        onClick = {
                                            previewIndex = (previewIndex + 1) % previewScenes.size
                                        }
                                    ) {
                                        Icon(imageVector = Icons.Default.ChevronRight, contentDescription = "Next", tint = Color.White)
                                    }
                                }

                                // Visual Direction Prompt text
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "${"Visual Prompt:".localize(appLanguageState)} ${currentPreviewScene.visualPrompt}",
                                    color = Color.LightGray.copy(alpha = 0.8f),
                                    fontSize = 10.sp,
                                    lineHeight = 14.sp,
                                    maxLines = 2,
                                    modifier = Modifier.padding(horizontal = 4.dp)
                                )
                            }
                        }

                        // Bottom action strip
                        if (previewScenes.isEmpty() && !isGeneratingPreview) {
                            Button(
                                onClick = {
                                    if (scriptTextState.trim().isNotEmpty()) {
                                        viewModel.generatePreviewSlideshow(
                                            scriptTextState,
                                            selectedStyleState,
                                            languageState,
                                            topicContextState
                                        )
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(38.dp)
                                    .testTag("preview_slideshow_button"),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.secondary,
                                    contentColor = MaterialTheme.colorScheme.onSecondary
                                ),
                                enabled = scriptTextState.trim().isNotEmpty()
                            ) {
                                Icon(Icons.Default.RemoveRedEye, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Preview Storyboard Slideshow".localize(appLanguageState), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        } else if (previewScenes.isNotEmpty() && !isGeneratingPreview) {
                            Button(
                                onClick = {
                                    viewModel.generatePreviewSlideshow(
                                        scriptTextState,
                                        selectedStyleState,
                                        languageState,
                                        topicContextState
                                    )
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 10.dp)
                                    .height(34.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                    contentColor = MaterialTheme.colorScheme.onSurface
                                )
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Re-Analyze and Refresh Preview".localize(appLanguageState), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Custom context / Video Topic specification field
                Text(
                    text = "Video Topic / Visual Context (Optional)".localize(appLanguageState),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )

                Text(
                    text = "Specify keywords (e.g. 'social media, facebook, page suspended, laptop screen, cyber security') so the AI matches your script with precise visual highlights instead of generic clips!".localize(appLanguageState),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 15.sp,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )

                OutlinedTextField(
                    value = topicContextState,
                    onValueChange = { text ->
                        viewModel.topicContext.value = text
                    },
                    placeholder = { Text("E.g. social media, facebook monetization, page block, warning alerts...".localize(appLanguageState), fontSize = 13.sp) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(84.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = Color(0xFFF8F9FF),
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                    ),
                    leadingIcon = {
                        Icon(imageVector = Icons.Default.Label, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default)
                )

                // Section 2: Choose Video Style (16 items)
                CardSectionHeader(index = 2, title = "Select Cinematic Visual Style".localize(appLanguageState))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(EditorConstants.VIDEO_STYLES) { style ->
                        val isSelected = style.name == selectedStyleState
                        Card(
                            modifier = Modifier
                                .width(135.dp)
                                .clickable { viewModel.selectedStyleName.value = style.name },
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                            ),
                            border = if (isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp)
                            ) {
                                Text(style.icon, fontSize = 24.sp)
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(style.name.localize(appLanguageState), fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1)
                                Text(style.description.localize(appLanguageState), fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 12.sp, modifier = Modifier.padding(top = 2.dp))
                            }
                        }
                    }
                }

                // Section 2B: Choose Cinematic / Social-Media Publishing Style
                CardSectionHeader(index = 3, title = "Cinematic & Social-Media Styles".localize(appLanguageState))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(EditorConstants.SOCIAL_AND_CINEMATIC_PUBLISHING_STYLES) { pubStyle ->
                        val isSelected = pubStyle.name == selectedPublishingStyleState
                        Card(
                            modifier = Modifier
                                .width(135.dp)
                                .clickable { viewModel.selectedPublishingStyle.value = pubStyle.name },
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                            ),
                            border = if (isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp)
                            ) {
                                PublishingStyleIcon(styleName = pubStyle.name)
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(pubStyle.name.localize(appLanguageState), fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1)
                                Text(pubStyle.description.localize(appLanguageState), fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 12.sp, modifier = Modifier.padding(top = 2.dp))
                            }
                        }
                    }
                }

                // Section 2B-1: Choose Visual Background Medium (Video vs Image)
                val visualMediumState by viewModel.selectedVisualMedium.collectAsState()
                CardSectionHeader(index = 4, title = "Visual Background Medium".localize(appLanguageState))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val isVideoSelected = visualMediumState == "Video"
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { viewModel.selectedVisualMedium.value = "Video" },
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isVideoSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                        ),
                        border = if (isVideoSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp)
                        ) {
                            Text("🎥", fontSize = 24.sp)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Cinematic Videos".localize(appLanguageState), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text("Pulls professional stock video scenes. Falls back to images if clips are rare.".localize(appLanguageState), fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 11.sp, modifier = Modifier.padding(top = 2.dp))
                        }
                    }

                    val isImgPrefSelected = visualMediumState == "Image"
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { viewModel.selectedVisualMedium.value = "Image" },
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isImgPrefSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                        ),
                        border = if (isImgPrefSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp)
                        ) {
                            Text("📸", fontSize = 24.sp)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("High-Res Images".localize(appLanguageState), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text("Uses professional photography and scenic backdrops matching your style.".localize(appLanguageState), fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 11.sp, modifier = Modifier.padding(top = 2.dp))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Section 2B-2: Choose Visual Background Source
                val imageSourceState by viewModel.selectedImageSource.collectAsState()
                CardSectionHeader(index = 5, title = "Choose Visual Background Source".localize(appLanguageState))
                
                // Row 1: Unsplash & AI Generated
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Unsplash option
                    val isUnsplashSelected = imageSourceState == "Unsplash"
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { viewModel.selectedImageSource.value = "Unsplash" },
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isUnsplashSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                        ),
                        border = if (isUnsplashSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp)
                        ) {
                            Text("📸", fontSize = 22.sp)
                            Spacer(modifier = Modifier.height(6.dp))
                            Text("VividStock Prime".localize(appLanguageState), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Text("Professional-grade realistic stock imagery engine".localize(appLanguageState), fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 11.sp, modifier = Modifier.padding(top = 2.dp))
                        }
                    }

                    // Pollinations AI option
                    val isAiSelected = imageSourceState == "AI Generated"
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { viewModel.selectedImageSource.value = "AI Generated" },
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isAiSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                        ),
                        border = if (isAiSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp)
                        ) {
                            Text("🤖", fontSize = 22.sp)
                            Spacer(modifier = Modifier.height(6.dp))
                            Text("Genesis Synthetix".localize(appLanguageState), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Text("Neural AI design and styled custom vector asset creator".localize(appLanguageState), fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 11.sp, modifier = Modifier.padding(top = 2.dp))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Row: Pexels & Pixabay
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Pexels option
                    val isPexelsSelected = imageSourceState == "Pexels"
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { viewModel.selectedImageSource.value = "Pexels" },
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isPexelsSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                        ),
                        border = if (isPexelsSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp)
                        ) {
                            Text("🎬", fontSize = 22.sp)
                            Spacer(modifier = Modifier.height(6.dp))
                            Text("Pexels Motion".localize(appLanguageState), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Text("Stunning realistic stock photos and HD video clips".localize(appLanguageState), fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 11.sp, modifier = Modifier.padding(top = 2.dp))
                        }
                    }

                    // Pixabay option
                    val isPixabaySelected = imageSourceState == "Pixabay"
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { viewModel.selectedImageSource.value = "Pixabay" },
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isPixabaySelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                        ),
                        border = if (isPixabaySelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp)
                        ) {
                            Text("🌌", fontSize = 22.sp)
                            Spacer(modifier = Modifier.height(6.dp))
                            Text("Pixabay Cosmos".localize(appLanguageState), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Text("Vibrant, high-resolution stock graphics and creative clips".localize(appLanguageState), fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 11.sp, modifier = Modifier.padding(top = 2.dp))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Row 2: Wikimedia Commons & Nekos Anime
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Wikimedia Commons option
                    val isWikimediaSelected = imageSourceState == "Wikimedia Commons"
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { viewModel.selectedImageSource.value = "Wikimedia Commons" },
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isWikimediaSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                        ),
                        border = if (isWikimediaSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp)
                        ) {
                            Text("🏛️", fontSize = 22.sp)
                            Spacer(modifier = Modifier.height(6.dp))
                            Text("Atlas Historical".localize(appLanguageState), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Text("Elite curated cultural archives and historical visual database".localize(appLanguageState), fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 11.sp, modifier = Modifier.padding(top = 2.dp))
                        }
                    }

                    // Nekos.best option
                    val isNekosSelected = imageSourceState == "Nekos.best"
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { viewModel.selectedImageSource.value = "Nekos.best" },
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isNekosSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                        ),
                        border = if (isNekosSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp)
                        ) {
                            Text("🌸", fontSize = 22.sp)
                            Spacer(modifier = Modifier.height(6.dp))
                            Text("Kawaii Art Vault".localize(appLanguageState), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Text("Expressive hand-drawn animated concept artwork and motion segments".localize(appLanguageState), fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 11.sp, modifier = Modifier.padding(top = 2.dp))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Row 3: Jikan Anime Database
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val isJikanSelected = imageSourceState == "Jikan Anime"
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { viewModel.selectedImageSource.value = "Jikan Anime" },
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isJikanSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                        ),
                        border = if (isJikanSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp)
                        ) {
                            Text("🏷️", fontSize = 22.sp)
                            Spacer(modifier = Modifier.height(6.dp))
                            Text("NeoManga Catalog".localize(appLanguageState), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Text("Curation database for thematic illustrated character references".localize(appLanguageState), fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 11.sp, modifier = Modifier.padding(top = 2.dp))
                        }
                    }

                    // Lorem Picsum option
                    val isPicsumSelected = imageSourceState == "Lorem Picsum"
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { viewModel.selectedImageSource.value = "Lorem Picsum" },
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isPicsumSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                        ),
                        border = if (isPicsumSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp)
                        ) {
                            Text("🖼️", fontSize = 22.sp)
                            Spacer(modifier = Modifier.height(6.dp))
                            Text("Horizon Scenic".localize(appLanguageState), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Text("Ultra-fast delivery engine for natural backgrounds and scenery backdrops".localize(appLanguageState), fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 11.sp, modifier = Modifier.padding(top = 2.dp))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Row 4: Archive.org & LoremFlickr
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Archive.org option
                    val isArchiveSelected = imageSourceState == "Archive.org"
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { viewModel.selectedImageSource.value = "Archive.org" },
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isArchiveSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                        ),
                        border = if (isArchiveSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp)
                        ) {
                            Text("📦", fontSize = 22.sp)
                            Spacer(modifier = Modifier.height(6.dp))
                            Text("Chronos Chronicle".localize(appLanguageState), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Text("Search millions of verified open-source historical documents, vintage media, and archives".localize(appLanguageState), fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 11.sp, modifier = Modifier.padding(top = 2.dp))
                        }
                    }

                    // LoremFlickr option
                    val isFlickrSelected = imageSourceState == "LoremFlickr"
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { viewModel.selectedImageSource.value = "LoremFlickr" },
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isFlickrSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                        ),
                        border = if (isFlickrSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp)
                        ) {
                            Text("⚡", fontSize = 22.sp)
                            Spacer(modifier = Modifier.height(6.dp))
                            Text("Lumina Snapshot".localize(appLanguageState), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Text("Dynamic tag-tailored premium photography database for creative layouts".localize(appLanguageState), fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 11.sp, modifier = Modifier.padding(top = 2.dp))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Row 5: Giphy Memes & 11-Stage Fallback Enabled
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Giphy Memes option
                    val isGiphySelected = imageSourceState == "Giphy Memes"
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { viewModel.selectedImageSource.value = "Giphy Memes" },
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isGiphySelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                        ),
                        border = if (isGiphySelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp)
                        ) {
                            Text("🎬", fontSize = 22.sp)
                            Spacer(modifier = Modifier.height(6.dp))
                            Text("PopMotion GIF".localize(appLanguageState), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Text("Instant search of popular pop-culture loops and animated segments".localize(appLanguageState), fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 11.sp, modifier = Modifier.padding(top = 2.dp))
                        }
                    }

                    // 11-Stage Fallback Enabled option
                    val isFallbackSelected = imageSourceState == "11-Stage Fallback"
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { viewModel.selectedImageSource.value = "11-Stage Fallback" },
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isFallbackSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                        ),
                        border = if (isFallbackSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp)
                        ) {
                            Text("🛡️", fontSize = 22.sp)
                            Spacer(modifier = Modifier.height(6.dp))
                            Text("OmniShield Fallback".localize(appLanguageState), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Text("Ultra-secure fallback system, automatically escalating visual delivery".localize(appLanguageState), fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 11.sp, modifier = Modifier.padding(top = 2.dp))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Row 6: NASA Library & NHTSA vPIC
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // NASA Library option
                    val isNasaSelected = imageSourceState == "NASA Library"
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { viewModel.selectedImageSource.value = "NASA Library" },
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isNasaSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                        ),
                        border = if (isNasaSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp)
                        ) {
                            Text("🚀", fontSize = 22.sp)
                            Spacer(modifier = Modifier.height(6.dp))
                            Text("Cosmos DeepSpace".localize(appLanguageState), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Text("Deep-space cosmic cinematic imagery, science archives, and cosmology footage".localize(appLanguageState), fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 11.sp, modifier = Modifier.padding(top = 2.dp))
                        }
                    }

                    // NHTSA vPIC option
                    val isNhtsaSelected = imageSourceState == "NHTSA vPIC"
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { viewModel.selectedImageSource.value = "NHTSA vPIC" },
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isNhtsaSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                        ),
                        border = if (isNhtsaSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp)
                        ) {
                            Text("🚗", fontSize = 22.sp)
                            Spacer(modifier = Modifier.height(6.dp))
                            Text("NHTSA vPIC".localize(appLanguageState), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Text("Automotive catalog search mapping to specialized vehicle showcases".localize(appLanguageState), fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 11.sp, modifier = Modifier.padding(top = 2.dp))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Row 7: OpenFDA & WHO
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // OpenFDA option
                    val isOpenFdaSelected = imageSourceState == "OpenFDA"
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { viewModel.selectedImageSource.value = "OpenFDA" },
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isOpenFdaSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                        ),
                        border = if (isOpenFdaSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp)
                        ) {
                            Text("💊", fontSize = 22.sp)
                            Spacer(modifier = Modifier.height(6.dp))
                            Text("OpenFDA".localize(appLanguageState), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Text("Pharmaceutical labels catalog for specialized medical visualizations".localize(appLanguageState), fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 11.sp, modifier = Modifier.padding(top = 2.dp))
                        }
                    }

                    // WHO option
                    val isWhoSelected = imageSourceState == "WHO"
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { viewModel.selectedImageSource.value = "WHO" },
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isWhoSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                        ),
                        border = if (isWhoSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp)
                        ) {
                            Text("🇺🇳", fontSize = 22.sp)
                            Spacer(modifier = Modifier.height(6.dp))
                            Text("WHO GHO".localize(appLanguageState), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Text("World Health Organization Global Health Observatory indicators visuals".localize(appLanguageState), fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 11.sp, modifier = Modifier.padding(top = 2.dp))
                        }
                    }
                }

                // Section 3: TTS Neural Voice Settings (80+ voices)
                CardSectionHeader(index = 6, title = "Neural Speech & Language Engine".localize(appLanguageState))
                
                // TTS Engine selector Card
                val ttsEngineState by viewModel.selectedTtsEngine.collectAsState()
                val activePremiumEngineState by viewModel.activePremiumTtsEngine.collectAsState()
                
                OutlinedCard(
                    shape = RoundedCornerShape(24.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.outlinedCardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Settings,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text("TTS Speech Engine".localize(appLanguageState), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    Text("Select TTS compiler engine provider".localize(appLanguageState), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            
                            var engineMenuExpanded by remember { mutableStateOf(false) }
                            Box {
                                Button(
                                    onClick = { engineMenuExpanded = true },
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                ) {
                                    val engineDisplayName = when {
                                        ttsEngineState == "voxeleven" || activePremiumEngineState == "voxeleven" -> "VoxEleven AI"
                                        ttsEngineState == "com.google.android.tts" -> "Google Speech"
                                        else -> "System Default"
                                    }
                                    Text(engineDisplayName.localize(appLanguageState), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.padding(start = 2.dp))
                                }
                                DropdownMenu(
                                    expanded = engineMenuExpanded,
                                    onDismissRequest = { engineMenuExpanded = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("VoxEleven (ElevenLabs Ultra HD AI)".localize(appLanguageState)) },
                                        onClick = {
                                            viewModel.setSelectedTtsEngine("voxeleven")
                                            viewModel.setActivePremiumTtsEngine("voxeleven")
                                            engineMenuExpanded = false
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Google Speech Services (Offline)".localize(appLanguageState)) },
                                        onClick = {
                                            viewModel.setSelectedTtsEngine("com.google.android.tts")
                                            viewModel.setActivePremiumTtsEngine("")
                                            viewModel.changeTtsEngine("com.google.android.tts")
                                            engineMenuExpanded = false
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("System Default Engine (Offline)".localize(appLanguageState)) },
                                        onClick = {
                                            viewModel.setSelectedTtsEngine("system_default")
                                            viewModel.setActivePremiumTtsEngine("")
                                            viewModel.changeTtsEngine("system_default")
                                            engineMenuExpanded = false
                                        }
                                    )
                                }
                            }
                        }

                        Divider(modifier = Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        
                        val engineDescription = when {
                            ttsEngineState == "voxeleven" || activePremiumEngineState == "voxeleven" -> "This engine utilizes ElevenLabs VoxEleven high-definition neural AI speech synthesis for ultra-realistic human narrations."
                            ttsEngineState == "com.google.android.tts" -> "This engine utilizes your device's built-in advanced Google Speech engine and high-fidelity local voices to generate pristine quality narrator audio instantly."
                            else -> "This engine uses your system default text-to-speech engine to generate narrator audio locally on your device."
                        }
                        Text(engineDescription.localize(appLanguageState), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                
                Spacer(modifier = Modifier.height(10.dp))
                
                // Voice Language dropdown
                OutlinedCard(
                    shape = RoundedCornerShape(24.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(imageVector = Icons.Default.Translate, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text("Speech & Video Language".localize(appLanguageState), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text("This controls the spoken accents & subtitles".localize(appLanguageState), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(10.dp))
                        
                        com.ritvyom.yashoraReelgenerator.presentation.components.SearchableLanguageSelector(
                            selectedLanguage = languageState,
                            onLanguageSelected = { lang: String ->
                                viewModel.setVideoLanguage(lang)
                            },
                            appLanguage = appLanguageState,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                // Voice Selection layout with tabs
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        // REELS CATEGORY PRESETS SECTION
                        Text("Category / Voice Presets".localize(appLanguageState), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Pick a category to auto-tune perfect matching voice & backing tracks:".localize(appLanguageState), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(10.dp))
                        
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                        ) {
                            items(EditorConstants.VOICE_PRESETS) { preset ->
                                val isMatchingPreset = selectedVoiceState == preset.voiceName && 
                                                      selectedCategoryState == preset.voiceCategory && 
                                                      kotlin.math.abs(speedState - preset.speed) < 0.05f
                                
                                Card(
                                    modifier = Modifier
                                        .width(135.dp)
                                        .height(72.dp)
                                        .clickable { viewModel.applyVoicePreset(preset) },
                                    shape = RoundedCornerShape(8.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isMatchingPreset) 
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                        else 
                                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                    ),
                                    border = BorderStroke(
                                        width = if (isMatchingPreset) 1.5.dp else 1.dp,
                                        color = if (isMatchingPreset) 
                                            MaterialTheme.colorScheme.primary 
                                        else 
                                            MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                                    )
                                ) {
                                    Column(
                                        modifier = Modifier.padding(8.dp),
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Text(preset.icon, fontSize = 14.sp)
                                            Text(
                                                preset.name.localize(appLanguageState), 
                                                fontSize = 11.sp, 
                                                fontWeight = FontWeight.Bold, 
                                                maxLines = 1, 
                                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            preset.subtitle.localize(appLanguageState), 
                                            fontSize = 8.sp, 
                                            color = MaterialTheme.colorScheme.onSurfaceVariant, 
                                            maxLines = 2, 
                                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, 
                                            lineHeight = 10.sp
                                        )
                                    }
                                }
                            }
                        }
                        
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f), 
                            modifier = Modifier.padding(vertical = 10.dp)
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Primary Narrator Voice".localize(appLanguageState),
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Box(
                                modifier = Modifier
                                    .background(Color(0xFF39FF14).copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Text("Selected:".localize(appLanguageState) + " $selectedVoiceState", fontSize = 10.sp, color = Color(0xFF39FF14), fontWeight = FontWeight.Bold)
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Quick professional tip Box instructions
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
                                .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f), RoundedCornerShape(10.dp))
                                .padding(10.dp)
                        ) {
                            Text(
                                text = "💡 Professional Tip: For highly natural, high-fidelity voices, please enable 'Speech Services by Google' and download 'High-quality speech voice data packs' in your phone system settings (Settings -> Accessibility -> Text-to-speech output -> Preferred engine).".localize(appLanguageState),
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                lineHeight = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Category tabs (Male, Female, Child)
                        TabRow(
                            selectedTabIndex = activeVoiceTabState,
                            containerColor = Color.Transparent,
                            modifier = Modifier.height(38.dp)
                        ) {
                            Tab(selected = activeVoiceTabState == 0, onClick = { activeVoiceTabState = 0 }) {
                                Text("Male".localize(appLanguageState) + " (5)", fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 6.dp))
                            }
                            Tab(selected = activeVoiceTabState == 1, onClick = { activeVoiceTabState = 1 }) {
                                Text("Female".localize(appLanguageState) + " (5)", fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 6.dp))
                            }
                            Tab(selected = activeVoiceTabState == 2, onClick = { activeVoiceTabState = 2 }) {
                                Text("Child".localize(appLanguageState) + " (4)", fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 6.dp))
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // List of voices
                        val voiceList = when (activeVoiceTabState) {
                            0 -> EditorConstants.MALE_VOICES
                            1 -> EditorConstants.FEMALE_VOICES
                            else -> EditorConstants.CHILD_VOICES
                        }
                        val category = when (activeVoiceTabState) {
                            0 -> "Male"
                            1 -> "Female"
                            else -> "Child"
                        }

                        Box(modifier = Modifier.height(115.dp)) {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(voiceList) { voiceUnit ->
                                    val isSelected = voiceUnit == selectedVoiceState
                                    Box(
                                        modifier = Modifier
                                            .width(85.dp)
                                            .fillMaxHeight()
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(
                                                if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                            )
                                            .border(
                                                1.dp,
                                                if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                                RoundedCornerShape(8.dp)
                                            )
                                            .clickable { viewModel.selectVoice(voiceUnit, category) }
                                            .padding(10.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text(if (category == "Male") "🧔" else if (category == "Female") "👩" else "🧒", fontSize = 20.sp)
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Text(voiceUnit, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Voice Modulation Parameters sliders
                        // Speed
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Speech Speed:".localize(appLanguageState) + " ${"%.1fx".format(speedState)}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(110.dp))
                            Slider(
                                value = speedState,
                                onValueChange = { viewModel.voiceSpeed.value = it },
                                valueRange = 0.5f..2.0f,
                                onValueChangeFinished = { viewModel.saveVoicePreferencesOnly() },
                                modifier = Modifier.weight(1f)
                            )
                        }

                        // Pitch
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Speech Pitch:".localize(appLanguageState) + " ${"%.1fx".format(pitchState)}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(110.dp))
                            Slider(
                                value = pitchState,
                                onValueChange = { viewModel.voicePitch.value = it },
                                valueRange = 0.5f..2.0f,
                                onValueChangeFinished = { viewModel.saveVoicePreferencesOnly() },
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Emotion and Quality
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            var emotionExpanded by remember { mutableStateOf(false) }
                            Box(modifier = Modifier.weight(1f)) {
                                OutlinedButton(
                                    onClick = { emotionExpanded = true },
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Emotion:".localize(appLanguageState) + " " + emotionState.localize(appLanguageState), fontSize = 11.sp)
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                                }
                                DropdownMenu(expanded = emotionExpanded, onDismissRequest = { emotionExpanded = false }) {
                                    listOf("Friendly", "Energetic", "Emotional", "Cinematic", "Scary", "Whispering").forEach { em ->
                                        DropdownMenuItem(text = { Text(em.localize(appLanguageState)) }, onClick = {
                                            viewModel.voiceEmotion.value = em
                                            viewModel.saveVoicePreferencesOnly()
                                            emotionExpanded = false
                                        })
                                    }
                                }
                            }

                            var qualityExpanded by remember { mutableStateOf(false) }
                            Box(modifier = Modifier.weight(1f)) {
                                OutlinedButton(
                                    onClick = { qualityExpanded = true },
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Format:".localize(appLanguageState) + " " + qualityState.localize(appLanguageState), fontSize = 11.sp)
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                                }
                                DropdownMenu(expanded = qualityExpanded, onDismissRequest = { qualityExpanded = false }) {
                                    listOf("High Quality", "Low Latency (Standard)", "Lossless Ultra").forEach { ql ->
                                        DropdownMenuItem(text = { Text(ql.localize(appLanguageState)) }, onClick = {
                                            viewModel.voiceQuality.value = ql
                                            qualityExpanded = false
                                        })
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Voice preview trigger button
                        Button(
                            onClick = { viewModel.speakText("Hi, this is a live preview audio track of $selectedVoiceState at speed rate ${"%.1f".format(speedState)} times. Sound waves are compiled completely.".localize(appLanguageState)) },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f), contentColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Icon(imageVector = Icons.Default.VolumeUp, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Preview Voice Synths".localize(appLanguageState), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // VoxEleven Voice Provider Config Button
                        OutlinedButton(
                            onClick = { onOpenVoiceProvider?.invoke() },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth(),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                        ) {
                            Icon(imageVector = Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("VoxEleven / ElevenLabs Voice Provider".localize(appLanguageState), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }

                        if (ttsEngineState == "voxeleven" || activePremiumEngineState == "voxeleven") {
                            Spacer(modifier = Modifier.height(10.dp))
                            com.ritvyom.yashoraReelgenerator.presentation.components.ElevenLabsVoiceParametersPanel(
                                viewModel = viewModel,
                                isCollapsible = true,
                                initiallyExpanded = false
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.25f)
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "Pro Tip: On your physical device, ensure 'Speech Services by Google' is selected under System Settings -> 'Text-to-speech output' to unlock natural premium high-definition voices.".localize(appLanguageState),
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    lineHeight = 13.sp
                                )
                            }
                        }
                    }
                }

                // Section 4: Aspect Ratio & Canvas Layout (6 choices)
                CardSectionHeader(index = 7, title = "Reel Layout Canvas Properties".localize(appLanguageState))
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val ratios = listOf("9:16", "16:9", "1:1", "4:5", "3:4", "21:9")
                    ratios.forEach { r ->
                        val isSelected = r == ratioState
                        Box(
                            modifier = Modifier
                                .weight(1f, fill = false)
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (isSelected) MaterialTheme.colorScheme.primaryContainer
                                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                )
                                .border(
                                    1.dp,
                                    if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                    RoundedCornerShape(8.dp)
                                )
                                .clickable { viewModel.selectedAspectRatio.value = r }
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                // Draw ratio rectangle outline symbol matching size
                                Box(
                                    modifier = Modifier
                                        .size(if (r == "9:16" || r == "3:4" || r == "4:5") 10.dp else 16.dp, if (r == "9:16") 16.dp else 10.dp)
                                        .border(1.dp, if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(r, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                // Resolution and FPS
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    var resolutionExpanded by remember { mutableStateOf(false) }
                    Box(modifier = Modifier.weight(1f)) {
                        OutlinedCard(
                            onClick = { resolutionExpanded = true },
                            shape = RoundedCornerShape(24.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Resolution".localize(appLanguageState), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(resolutionState, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(Icons.Default.Hd, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                        DropdownMenu(expanded = resolutionExpanded, onDismissRequest = { resolutionExpanded = false }) {
                            listOf("360p", "480p", "540p", "720p", "1080p", "2K", "4K", "8K").forEach { res ->
                                DropdownMenuItem(text = { Text(res) }, onClick = {
                                    viewModel.selectedResolution.value = res
                                    resolutionExpanded = false
                                })
                            }
                        }
                    }

                    var fpsExpanded by remember { mutableStateOf(false) }
                    Box(modifier = Modifier.weight(1f)) {
                        OutlinedCard(
                            onClick = { fpsExpanded = true },
                            shape = RoundedCornerShape(24.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Video Framerate".localize(appLanguageState), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text("$fpsState FPS", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(Icons.Default.Speed, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                        DropdownMenu(expanded = fpsExpanded, onDismissRequest = { fpsExpanded = false }) {
                            listOf(24, 30, 60).forEach { item ->
                                DropdownMenuItem(text = { Text("$item FPS") }, onClick = {
                                    viewModel.selectedFps.value = item
                                    fpsExpanded = false
                                })
                            }
                        }
                    }
                }

                // Section 5: Background Music & Audio overlay
                CardSectionHeader(index = 8, title = "Soundtrack & Audio Controls".localize(appLanguageState))
                OutlinedCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.MusicNote, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text("Background Soundtrack Overlay".localize(appLanguageState), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    Text("Add smart rhythmic backing beats".localize(appLanguageState), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Switch(
                                checked = bgMusicEnabled,
                                onCheckedChange = { viewModel.bgMusicEnabled.value = it }
                            )
                        }

                        if (bgMusicEnabled) {
                            Spacer(modifier = Modifier.height(14.dp))
                            
                            // Music Category selector
                            var musicMenuExpanded by remember { mutableStateOf(false) }
                            Box {
                                OutlinedButton(
                                    onClick = { musicMenuExpanded = true },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    val currentMusic = EditorConstants.MUSIC_CATEGORIES.firstOrNull { it.name == musicCategoryState }
                                    val musicIcon = currentMusic?.icon ?: "🎵"
                                    Text("$musicIcon " + "Genre:".localize(appLanguageState) + " " + musicCategoryState.localize(appLanguageState), fontSize = 12.sp)
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                                }
                                DropdownMenu(expanded = musicMenuExpanded, onDismissRequest = { musicMenuExpanded = false }) {
                                    EditorConstants.MUSIC_CATEGORIES.forEach { mc ->
                                        DropdownMenuItem(
                                            text = {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Text(mc.icon, modifier = Modifier.padding(end = 8.dp))
                                                    Column {
                                                        Text(mc.name.localize(appLanguageState), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                                        Text(mc.description.localize(appLanguageState), fontSize = 9.sp, color = Color.Gray)
                                                    }
                                                }
                                            },
                                            onClick = {
                                                viewModel.bgMusicCategory.value = mc.name
                                                musicMenuExpanded = false
                                            }
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Dual slider volumes controllers
                            // Music Volume
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.MusicNote, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("Music Level:".localize(appLanguageState) + " ${(musicVolumeState * 100).toInt()}%", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(100.dp).padding(start = 6.dp))
                                Slider(
                                    value = musicVolumeState,
                                    onValueChange = { viewModel.bgMusicVolume.value = it },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        // Voice volume slider
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                            Icon(Icons.Default.RecordVoiceOver, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("Voice Level:".localize(appLanguageState) + " ${(voiceVolumeState * 100).toInt()}%", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(100.dp).padding(start = 6.dp))
                            Slider(
                                value = voiceVolumeState,
                                onValueChange = { viewModel.voiceVolume.value = it },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }

            // Bottom CTA Generate buttons
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background)
                    .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Button(
                    onClick = {
                        val isOnline = com.ritvyom.yashoraReelgenerator.presentation.utils.NetworkUtils.isInternetAvailable(context)
                        if (isOnline) {
                            viewModel.createProjectFromCurrent()
                            onNavigateToGeneration()
                        } else {
                            showOfflineDialog = true
                        }
                    },
                    enabled = true,
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                    )
                ) {
                    Icon(imageVector = Icons.Default.FlashOn, contentDescription = null, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = if (scriptTextState.trim().isEmpty()) "Skip & Generate Video".localize(appLanguageState) else "Generate Video".localize(appLanguageState),
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Watch 1 Reward Ad to Start Generation".localize(appLanguageState),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.secondary,
                    letterSpacing = 0.5.sp
                )
            }
        }

        com.ritvyom.yashoraReelgenerator.presentation.components.BackOnlineBanner(
            appLanguage = appLanguageState,
            visible = showBackOnlineBanner,
            modifier = Modifier.align(Alignment.TopCenter)
        )
    }
}

    if (showOfflineDialog) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { showOfflineDialog = false },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
        ) {
            com.ritvyom.yashoraReelgenerator.presentation.components.PremiumOfflineScreen(
                appLanguage = appLanguageState,
                onRetry = {
                    showOfflineDialog = false
                    showBackOnlineBanner = true
                    viewModel.createProjectFromCurrent()
                    onNavigateToGeneration()
                }
            )
        }
    }
}

@Composable
fun CardSectionHeader(
    index: Int,
    title: String
) {
    val icon = when (index) {
        1 -> Icons.Default.Edit
        2 -> Icons.Default.Palette
        3 -> Icons.Default.Movie
        4 -> Icons.Default.Image
        5 -> Icons.Default.Folder
        6 -> Icons.Default.Translate
        7 -> Icons.Default.AspectRatio
        8 -> Icons.Default.MusicNote
        else -> Icons.Default.Star
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(26.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(15.dp)
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = title.uppercase(),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.secondary,
            letterSpacing = 1.sp
        )
    }
}

@Composable
fun PublishingStyleIcon(
    styleName: String,
    modifier: Modifier = Modifier
) {
    when (styleName) {
        "TikTok / Instagram Reels" -> {
            Box(
                modifier = modifier
                    .size(32.dp)
                    .background(Color(0xFF0F0F0F), shape = RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                androidx.compose.foundation.Canvas(modifier = Modifier.size(16.dp)) {
                    // Draw a sleek mobile screen outline with dynamic neon side borders
                    drawRoundRect(
                        color = Color(0xFF00F0FF).copy(alpha = 0.4f),
                        topLeft = Offset(size.width * 0.15f, size.height * 0.05f),
                        size = Size(size.width * 0.7f, size.height * 0.9f),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(3f, 3f),
                        style = Stroke(width = 1.5f)
                    )
                    // Neon Reels logo lookalike overlaps
                    val playPath = Path().apply {
                        moveTo(size.width * 0.4f, size.height * 0.35f)
                        lineTo(size.width * 0.65f, size.height * 0.5f)
                        lineTo(size.width * 0.4f, size.height * 0.65f)
                        close()
                    }
                    drawPath(playPath, Color(0xFFFF007F))
                    drawPath(playPath, Color(0xFF00F0FF), style = Stroke(width = 1f))
                }
            }
        }
        "YouTube Short" -> {
            Box(
                modifier = modifier
                    .size(32.dp)
                    .background(Color(0xFFFF0000), shape = RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                androidx.compose.foundation.Canvas(modifier = Modifier.size(14.dp)) {
                    val trianglePath = Path().apply {
                        moveTo(size.width * 0.35f, size.height * 0.25f)
                        lineTo(size.width * 0.75f, size.height * 0.5f)
                        lineTo(size.width * 0.35f, size.height * 0.75f)
                        close()
                    }
                    drawPath(trianglePath, Color.White)
                }
            }
        }
        "Cinematic Vlog / Trailer" -> {
            Box(
                modifier = modifier
                    .size(32.dp)
                    .background(Color(0xFF2C2415), shape = RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                androidx.compose.foundation.Canvas(modifier = Modifier.size(16.dp)) {
                    // Draw widescreen bounding boxes (21:9 format)
                    drawRect(
                        color = Color(0xFFFFC107).copy(alpha = 0.3f),
                        topLeft = Offset(0f, size.height * 0.2f),
                        size = Size(size.width, size.height * 0.6f)
                    )
                    drawRect(
                        color = Color(0xFFFFC107),
                        topLeft = Offset(0f, size.height * 0.2f),
                        size = Size(size.width, size.height * 0.6f),
                        style = Stroke(width = 1.5f)
                    )
                    // Draw gold star/sparkle in the center represent flare
                    val crossPath = Path().apply {
                        moveTo(size.width * 0.5f, size.height * 0.35f)
                        lineTo(size.width * 0.5f, size.height * 0.65f)
                        moveTo(size.width * 0.35f, size.height * 0.5f)
                        lineTo(size.width * 0.65f, size.height * 0.5f)
                    }
                    drawPath(crossPath, Color(0xFFFFC107), style = Stroke(width = 1.5f))
                }
            }
        }
        "Short Documentary" -> {
            Box(
                modifier = modifier
                    .size(32.dp)
                    .background(Color(0xFF1E3A34), shape = RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                androidx.compose.foundation.Canvas(modifier = Modifier.size(16.dp)) {
                    // Draw historical columns
                    // Floor
                    drawLine(
                        color = Color(0xFFE0F2F1),
                        start = Offset(size.width * 0.1f, size.height * 0.85f),
                        end = Offset(size.width * 0.9f, size.height * 0.85f),
                        strokeWidth = 2f
                    )
                    // Roof Triangle
                    val roof = Path().apply {
                        moveTo(size.width * 0.5f, size.height * 0.15f)
                        lineTo(size.width * 0.1f, size.height * 0.35f)
                        lineTo(size.width * 0.9f, size.height * 0.35f)
                        close()
                    }
                    drawPath(roof, Color(0xFFE0F2F1))
                    // Columns
                    drawRect(Color(0xFF80CBC4), topLeft = Offset(size.width * 0.2f, size.height * 0.37f), size = Size(size.width * 0.12f, size.height * 0.45f))
                    drawRect(Color(0xFF80CBC4), topLeft = Offset(size.width * 0.44f, size.height * 0.37f), size = Size(size.width * 0.12f, size.height * 0.45f))
                    drawRect(Color(0xFF80CBC4), topLeft = Offset(size.width * 0.68f, size.height * 0.37f), size = Size(size.width * 0.12f, size.height * 0.45f))
                }
            }
        }
        "Facebook Feed Video" -> {
            Box(
                modifier = modifier
                    .size(32.dp)
                    .background(Color(0xFF1877F2), shape = RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                androidx.compose.foundation.Canvas(modifier = Modifier.size(16.dp)) {
                    // Newsfeed card look with a tiny sidebar and content play symbol
                    drawRoundRect(
                        color = Color.White.copy(alpha = 0.3f),
                        topLeft = Offset(0f, 0f),
                        size = Size(size.width, size.height),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(2f, 2f)
                    )
                    // Avatar circle
                    drawCircle(
                        color = Color.White,
                        radius = size.width * 0.15f,
                        center = Offset(size.width * 0.25f, size.height * 0.3f)
                    )
                    // Horizontal lines
                    drawLine(Color.White, start = Offset(size.width * 0.5f, size.height * 0.2f), end = Offset(size.width * 0.9f, size.height * 0.2f), strokeWidth = 1.5f)
                    drawLine(Color.White, start = Offset(size.width * 0.5f, size.height * 0.4f), end = Offset(size.width * 0.8f, size.height * 0.4f), strokeWidth = 1.5f)
                    // Play symbol bottom right
                    val playPath = Path().apply {
                        moveTo(size.width * 0.45f, size.height * 0.6f)
                        lineTo(size.width * 0.7f, size.height * 0.75f)
                        lineTo(size.width * 0.45f, size.height * 0.9f)
                        close()
                    }
                    drawPath(playPath, Color.White)
                }
            }
        }
        "LinkedIn Professional" -> {
            Box(
                modifier = modifier
                    .size(32.dp)
                    .background(Color(0xFF0A66C2), shape = RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                androidx.compose.foundation.Canvas(modifier = Modifier.size(16.dp)) {
                    // Let's draw 3 neat ascending bar chart columns (professional analytics/growth)
                    // Bar 1
                    drawRect(
                        color = Color.White.copy(alpha = 0.6f),
                        topLeft = Offset(size.width * 0.15f, size.height * 0.55f),
                        size = Size(size.width * 0.18f, size.height * 0.35f)
                    )
                    // Bar 2
                    drawRect(
                        color = Color.White.copy(alpha = 0.8f),
                        topLeft = Offset(size.width * 0.41f, size.height * 0.35f),
                        size = Size(size.width * 0.18f, size.height * 0.55f)
                    )
                    // Bar 3
                    drawRect(
                        color = Color.White,
                        topLeft = Offset(size.width * 0.67f, size.height * 0.15f),
                        size = Size(size.width * 0.18f, size.height * 0.75f)
                    )
                }
            }
        }
        "Ad Promo / Marketing" -> {
            Box(
                modifier = modifier
                    .size(32.dp)
                    .background(Color(0xFFFF6D00), shape = RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                androidx.compose.foundation.Canvas(modifier = Modifier.size(16.dp)) {
                    // Target bullseye + arrow
                    drawCircle(
                        color = Color.White,
                        radius = size.width * 0.45f,
                        center = Offset(size.width / 2f, size.height / 2f),
                        style = Stroke(width = 2f)
                    )
                    drawCircle(
                        color = Color.White,
                        radius = size.width * 0.25f,
                        center = Offset(size.width / 2f, size.height / 2f),
                        style = Stroke(width = 1.5f)
                    )
                    drawCircle(
                        color = Color(0xFFFFD600),
                        radius = size.width * 0.12f,
                        center = Offset(size.width / 2f, size.height / 2f)
                    )
                }
            }
        }
        else -> {
            Box(
                modifier = modifier
                    .size(32.dp)
                    .background(Color.Gray.copy(alpha = 0.2f), shape = RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Movie,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
