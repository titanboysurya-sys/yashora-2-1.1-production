package com.ritvyom.yashoraReelgenerator.presentation.screens

import androidx.activity.compose.BackHandler
import android.app.Application
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ritvyom.yashoraReelgenerator.data.model.VideoScript
import com.ritvyom.yashoraReelgenerator.presentation.components.*
import com.ritvyom.yashoraReelgenerator.presentation.utils.localize
import com.ritvyom.yashoraReelgenerator.presentation.viewmodels.GenerateUiState
import com.ritvyom.yashoraReelgenerator.presentation.viewmodels.ScriptViewModel
import com.ritvyom.yashoraReelgenerator.presentation.viewmodels.ScriptViewModelFactory
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ScriptGeneratorScreen(
    appLanguageState: String,
    onBack: () -> Unit,
    onSendToVideoPipeline: (String, String) -> Unit, // passes scriptText and topic
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val app = context.applicationContext as Application
    val scriptViewModel: ScriptViewModel = viewModel(factory = ScriptViewModelFactory(app))

    val generateUiState by scriptViewModel.generateUiState.collectAsState()
    val historyState by scriptViewModel.historyState.collectAsState()
    val selectedScript by scriptViewModel.selectedScript.collectAsState()

    val savedLanguage by scriptViewModel.scriptLanguage.collectAsState()
    val savedPlatform by scriptViewModel.scriptPlatform.collectAsState()
    val savedDuration by scriptViewModel.scriptDuration.collectAsState()
    val savedTone by scriptViewModel.scriptTone.collectAsState()

    // Form inputs
    var topic by remember { mutableStateOf("") }
    var selectedPlatform by remember { mutableStateOf("YouTube Shorts / Reels") }
    var selectedDuration by remember { mutableStateOf("short") } // short, medium, long
    var selectedTone by remember { mutableStateOf("casual") } // professional, casual, energetic, educational, entertaining, tech, dramatic, comedy
    var selectedLanguage by remember { mutableStateOf("english") } // english, hindi, hinglish, spanish, french, german, arabic, japanese, portuguese
    var targetLanguage by remember { mutableStateOf("english") }

    LaunchedEffect(savedLanguage) {
        selectedLanguage = savedLanguage
        targetLanguage = savedLanguage
    }
    LaunchedEffect(savedPlatform) {
        selectedPlatform = savedPlatform
    }
    LaunchedEffect(savedDuration) {
        selectedDuration = savedDuration
    }
    LaunchedEffect(savedTone) {
        selectedTone = savedTone
    }

    // Trigger fresh script generation when targetLanguage changes (if a script is currently selected/active)
    var previousTargetLanguage by remember { mutableStateOf(targetLanguage) }
    LaunchedEffect(targetLanguage) {
        val currentScript = selectedScript
        if (currentScript != null && targetLanguage.trim().lowercase() != currentScript.language.trim().lowercase() && targetLanguage != previousTargetLanguage) {
            previousTargetLanguage = targetLanguage
            scriptViewModel.generateScript(
                topic = currentScript.topic,
                duration = currentScript.duration,
                tone = currentScript.tone,
                language = targetLanguage,
                platform = currentScript.platform
            )
        }
    }

    // View state
    var showHistoryOnly by remember { mutableStateOf(false) }
    var showScriptAd by remember { mutableStateOf(false) }
    var isOfflineMode by remember { mutableStateOf(false) }
    var showBackOnlineBanner by remember { mutableStateOf(false) }

    LaunchedEffect(showBackOnlineBanner) {
        if (showBackOnlineBanner) {
            delay(3000)
            showBackOnlineBanner = false
        }
    }

    // Loading rotation messages
    var loadingMessageIndex by remember { mutableStateOf(0) }
    val loadingMessages = listOf(
        "Synthesizing creative hook variants...",
        "Structuring story body narrative paragraphs...",
        "Reviewing viral click-worthy titles...",
        "Formulating optimal Outro call to actions..."
    )

    LaunchedEffect(generateUiState) {
        if (generateUiState is GenerateUiState.Loading) {
            loadingMessageIndex = 0
            while (true) {
                delay(3000)
                loadingMessageIndex = (loadingMessageIndex + 1) % loadingMessages.size
            }
        } else if (generateUiState is GenerateUiState.Success) {
            val script = (generateUiState as GenerateUiState.Success).script
            scriptViewModel.selectScript(script)
            scriptViewModel.resetGenerateState()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "AI Script Generator".localize(appLanguageState),
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (selectedScript != null) {
                            scriptViewModel.selectScript(null)
                        } else {
                            onBack()
                        }
                    }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Default.ArrowBack,
                            contentDescription = "Back".localize(appLanguageState),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showHistoryOnly = !showHistoryOnly },
                        modifier = Modifier.testTag("history_toggle_btn")
                    ) {
                        Icon(
                            imageVector = if (showHistoryOnly) Icons.Default.Edit else Icons.Default.History,
                            contentDescription = "Toggle History".localize(appLanguageState),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        modifier = modifier
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(
                    Brush.verticalGradient(
                        listOf(
                            MaterialTheme.colorScheme.background,
                            MaterialTheme.colorScheme.surfaceVariant
                        )
                    )
                )
        ) {
            MockInterstitialAdDialog(
                show = showScriptAd,
                onDismiss = {
                    showScriptAd = false
                    scriptViewModel.generateScript(
                        topic = topic,
                        duration = selectedDuration,
                        tone = selectedTone,
                        language = targetLanguage,
                        platform = selectedPlatform
                    )
                }
            )

            if (isOfflineMode) {
                PremiumOfflineScreen(
                    appLanguage = appLanguageState,
                    onRetry = {
                        isOfflineMode = false
                        showBackOnlineBanner = true
                        if (topic.trim().isNotBlank()) {
                            showScriptAd = true
                        } else {
                            scriptViewModel.generateScript(
                                topic = topic,
                                duration = selectedDuration,
                                tone = selectedTone,
                                language = targetLanguage,
                                platform = selectedPlatform
                            )
                        }
                    }
                )
            } else if (generateUiState is GenerateUiState.Loading && selectedScript == null) {
                // Loading Overlay
                var showCancelScriptConfirmDialog by remember { mutableStateOf(false) }

                BackHandler {
                    showCancelScriptConfirmDialog = true
                }

                if (showCancelScriptConfirmDialog) {
                    AlertDialog(
                        onDismissRequest = { showCancelScriptConfirmDialog = false },
                        title = {
                            Text(
                                text = "Cancel Script Generation?".localize(appLanguageState),
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        },
                        text = {
                            Text(
                                text = "Are you sure you want to cancel script generation?".localize(appLanguageState),
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    showCancelScriptConfirmDialog = false
                                    scriptViewModel.resetGenerateState()
                                }
                            ) {
                                Text(
                                    text = "Yes, Cancel".localize(appLanguageState),
                                    color = MaterialTheme.colorScheme.error,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        },
                        dismissButton = {
                            TextButton(
                                onClick = { showCancelScriptConfirmDialog = false }
                            ) {
                                Text(
                                    text = "No, Continue".localize(appLanguageState),
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    )
                }

                val simulatedProgress = remember { androidx.compose.animation.core.Animatable(0f) }
                LaunchedEffect(Unit) {
                    simulatedProgress.animateTo(
                        targetValue = 0.96f,
                        animationSpec = tween(durationMillis = 15000, easing = EaseOutQuad)
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    ScriptGenerationLoadingSkeleton(
                        title = "Writing Your AI Script with Gemini...",
                        appLanguageState = appLanguageState,
                        showSteps = true,
                        showCancelButton = true,
                        onCancel = { showCancelScriptConfirmDialog = true }
                    )
                }
            } else if (showHistoryOnly) {
                // History List View
                if (historyState.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.FolderOpen,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "No saved scripts found.".localize(appLanguageState),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        item {
                            Text(
                                text = "Saved Scripts History".localize(appLanguageState),
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 16.sp,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                        }
                        items(historyState) { script ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        scriptViewModel.selectScript(script)
                                        showHistoryOnly = false
                                    },
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surface
                                )
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = script.title,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                            maxLines = 1,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Row {
                                            IconButton(
                                                onClick = {
                                                    scriptViewModel.selectScript(script)
                                                    showHistoryOnly = false
                                                    scriptViewModel.generateScript(
                                                        topic = script.topic,
                                                        duration = script.duration,
                                                        tone = script.tone,
                                                        language = script.language,
                                                        platform = script.platform,
                                                        bypassCache = true
                                                    )
                                                },
                                                modifier = Modifier.testTag("history_regenerate_btn_${script.id}")
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Refresh,
                                                    contentDescription = "Regenerate Variant".localize(appLanguageState),
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                            IconButton(onClick = { scriptViewModel.toggleFavorite(script) }) {
                                                Icon(
                                                    imageVector = if (script.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                                    contentDescription = "Favorite",
                                                    tint = if (script.isFavorite) Color.Red else MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            IconButton(onClick = { scriptViewModel.deleteScript(script) }) {
                                                Icon(
                                                    imageVector = Icons.Default.Delete,
                                                    contentDescription = "Delete",
                                                    tint = MaterialTheme.colorScheme.error
                                                )
                                            }
                                        }
                                    }
                                    Text(
                                        text = "Topic: ${script.topic}".localize(appLanguageState),
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Text(
                                            text = "Tone: ${script.tone.uppercase()}".localize(appLanguageState),
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            text = "Lang: ${script.language.uppercase()}".localize(appLanguageState),
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.secondary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            } else if (selectedScript != null) {
                // Script Details Editor View
                val script = selectedScript!!
                var editableTitle by remember(script.id) { mutableStateOf(script.title) }
                var editableContent by remember(script.id) { mutableStateOf(script.fullScript) }
                val clipboardManager = LocalClipboardManager.current

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Your AI Generated Script".localize(appLanguageState),
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 18.sp,
                            color = MaterialTheme.colorScheme.onBackground
                        )

                        FilledTonalButton(
                            onClick = {
                                if (generateUiState !is GenerateUiState.Loading) {
                                    Toast.makeText(
                                        context,
                                        "Regenerating script variant...".localize(appLanguageState),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    scriptViewModel.generateScript(
                                        topic = script.topic,
                                        duration = script.duration,
                                        tone = script.tone,
                                        language = targetLanguage,
                                        platform = script.platform,
                                        bypassCache = true
                                    )
                                }
                            },
                            enabled = generateUiState !is GenerateUiState.Loading,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            shape = RoundedCornerShape(20.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.primary
                            ),
                            modifier = Modifier.testTag("regenerate_header_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Regenerate".localize(appLanguageState),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Regenerate".localize(appLanguageState),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Target Language Selector within Script Details
                    Text(
                        text = "Change Script Language".localize(appLanguageState),
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    var langDetailsExpanded by remember { mutableStateOf(false) }
                    val languages = listOf("english", "hindi", "hinglish", "spanish", "french", "german", "arabic", "japanese", "portuguese")
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = targetLanguage.uppercase().localize(appLanguageState),
                            onValueChange = {},
                            readOnly = true,
                            modifier = Modifier.fillMaxWidth(),
                            trailingIcon = {
                                IconButton(onClick = { langDetailsExpanded = true }) {
                                    Icon(
                                        imageVector = Icons.Default.ArrowDropDown,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline
                            )
                        )
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .clickable { langDetailsExpanded = true }
                        )
                        DropdownMenu(
                            expanded = langDetailsExpanded,
                            onDismissRequest = { langDetailsExpanded = false },
                            modifier = Modifier.background(MaterialTheme.colorScheme.surface)
                        ) {
                            languages.forEach { langName ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = langName.uppercase().localize(appLanguageState),
                                            color = MaterialTheme.colorScheme.onSurface,
                                            fontWeight = FontWeight.Bold
                                        )
                                    },
                                    onClick = {
                                        targetLanguage = langName
                                        langDetailsExpanded = false
                                        scriptViewModel.saveScriptLanguage(langName)
                                    }
                                )
                            }
                        }
                    }

                    if (generateUiState is GenerateUiState.Loading) {
                        ScriptGenerationLoadingSkeleton(
                            title = "Generating script in ${targetLanguage.uppercase()}...",
                            appLanguageState = appLanguageState,
                            showSteps = true,
                            showCancelButton = true,
                            onCancel = { scriptViewModel.resetGenerateState() },
                            modifier = Modifier.testTag("script_skeleton_container")
                        )
                    } else {
                        OutlinedTextField(
                            value = editableTitle,
                            onValueChange = { editableTitle = it },
                            label = { Text("Script Title".localize(appLanguageState)) },
                            modifier = Modifier.fillMaxWidth().testTag("script_title_field"),
                            shape = RoundedCornerShape(12.dp),
                            textStyle = androidx.compose.ui.text.TextStyle(
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 14.sp
                            ),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f),
                                focusedLabelColor = MaterialTheme.colorScheme.primary,
                                unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )

                        OutlinedTextField(
                            value = editableContent,
                            onValueChange = { editableContent = it },
                            label = { Text("Script Body".localize(appLanguageState)) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 250.dp, max = 450.dp)
                                .testTag("script_body_field"),
                            shape = RoundedCornerShape(12.dp),
                            textStyle = androidx.compose.ui.text.TextStyle(
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 14.sp
                            ),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f),
                                focusedLabelColor = MaterialTheme.colorScheme.primary,
                                unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = {
                                clipboardManager.setText(AnnotatedString(editableContent))
                                Toast.makeText(context, "Script copied to clipboard!".localize(appLanguageState), Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                        ) {
                            Icon(imageVector = Icons.Default.ContentCopy, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Copy".localize(appLanguageState))
                        }

                        Button(
                            onClick = {
                                scriptViewModel.updateScriptContent(script.id, editableTitle, editableContent)
                                Toast.makeText(context, "Script updates saved!".localize(appLanguageState), Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)
                        ) {
                            Icon(imageVector = Icons.Default.Save, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Save".localize(appLanguageState))
                        }
                    }

                    OutlinedButton(
                        onClick = {
                            if (generateUiState !is GenerateUiState.Loading) {
                                Toast.makeText(
                                    context,
                                    "Generating different script variant...".localize(appLanguageState),
                                    Toast.LENGTH_SHORT
                                ).show()
                                scriptViewModel.generateScript(
                                    topic = script.topic,
                                    duration = script.duration,
                                    tone = script.tone,
                                    language = targetLanguage,
                                    platform = script.platform,
                                    bypassCache = true
                                )
                            }
                        },
                        enabled = generateUiState !is GenerateUiState.Loading,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("regenerate_script_variant_btn"),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Regenerate Script Variant".localize(appLanguageState),
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }

                    Button(
                        onClick = {
                            // Automatically populate existing Text-To-Video inputs
                            onSendToVideoPipeline(editableContent, script.topic)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .testTag("send_to_video_pipeline_btn"),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(imageVector = Icons.Default.MovieCreation, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Send to Video Generator".localize(appLanguageState),
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }

                    OutlinedButton(
                        onClick = { scriptViewModel.selectScript(null) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Write Another Script".localize(appLanguageState))
                    }
                }
            } else {
                // Form View
                var isTopicFocused by remember { mutableStateOf(false) }
                var langExpanded by remember { mutableStateOf(false) }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Premium Sparkle Banner
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                BorderStroke(
                                    1.dp,
                                    Brush.horizontalGradient(
                                        listOf(
                                            MaterialTheme.colorScheme.primary,
                                            MaterialTheme.colorScheme.secondary,
                                            MaterialTheme.colorScheme.tertiary
                                        )
                                    )
                                ),
                                shape = RoundedCornerShape(16.dp)
                            ),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    Brush.linearGradient(
                                        listOf(
                                            MaterialTheme.colorScheme.primaryContainer,
                                            MaterialTheme.colorScheme.surfaceVariant
                                        )
                                    )
                                )
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "🪄",
                                    fontSize = 24.sp
                                )
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text(
                                    text = buildAnnotatedString {
                                        append("Write ")
                                        withStyle(style = SpanStyle(color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)) {
                                            append("Viral")
                                        }
                                        append(" Hooks Fast")
                                    },
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 15.sp,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Enter any topic description below and let the AI generate 3 high-retention script structures.".localize(appLanguageState),
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    // Topic Input
                    Text(
                        text = "Describe Video Topic".localize(appLanguageState),
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                width = if (isTopicFocused) 1.5.dp else 1.dp,
                                brush = if (isTopicFocused) {
                                    Brush.horizontalGradient(
                                        listOf(
                                            MaterialTheme.colorScheme.primary,
                                            MaterialTheme.colorScheme.secondary,
                                            MaterialTheme.colorScheme.tertiary
                                        )
                                    )
                                } else {
                                    SolidColor(MaterialTheme.colorScheme.outline)
                                },
                                shape = RoundedCornerShape(14.dp)
                            )
                    ) {
                        OutlinedTextField(
                            value = topic,
                            onValueChange = { topic = it },
                            placeholder = {
                                Text(
                                    text = "e.g. Why space exploration is vital, or 3 financial secrets...".localize(appLanguageState),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                    fontSize = 13.sp
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(110.dp)
                                .testTag("script_topic_input")
                                .onFocusChanged { isTopicFocused = it.isFocused },
                            textStyle = androidx.compose.ui.text.TextStyle(
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 14.sp
                            ),
                            shape = RoundedCornerShape(14.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                focusedBorderColor = Color.Transparent,
                                unfocusedBorderColor = Color.Transparent
                            )
                        )
                    }

                    // Platform Selector
                    Text(
                        text = "Target Video Platform".localize(appLanguageState),
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val platforms = listOf(
                            Triple("YouTube Shorts / Reels", "YouTube Shorts / Reels", "PREMIUM"),
                            Triple("YouTube Long-form", "YouTube Long-form", ""),
                            Triple("General Video", "General Video", "")
                        )
                        platforms.forEach { (platformName, display, badge) ->
                            val isSelected = selectedPlatform == platformName
                            val isPremium = badge.isNotEmpty()
                            
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(96.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                    )
                                    .border(
                                        BorderStroke(
                                            width = if (isSelected) 1.5.dp else 1.dp,
                                            brush = if (isSelected) {
                                                Brush.linearGradient(
                                                    listOf(
                                                        MaterialTheme.colorScheme.primary,
                                                        MaterialTheme.colorScheme.secondary,
                                                        MaterialTheme.colorScheme.tertiary
                                                    )
                                                )
                                            } else {
                                                SolidColor(MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                                            }
                                        ),
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    .clickable {
                                        selectedPlatform = platformName
                                        scriptViewModel.saveScriptPlatform(platformName)
                                    }
                                    .padding(8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center,
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    when (platformName) {
                                        "YouTube Shorts / Reels" -> {
                                            Icon(
                                                imageVector = Icons.Default.PlayCircle,
                                                contentDescription = null,
                                                tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }
                                        "YouTube Long-form" -> {
                                            Icon(
                                                imageVector = Icons.Default.VideoCall,
                                                contentDescription = null,
                                                tint = if (isSelected) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }
                                        else -> {
                                            Icon(
                                                imageVector = Icons.Default.Videocam,
                                                contentDescription = null,
                                                tint = if (isSelected) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = display.localize(appLanguageState),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.Center,
                                        lineHeight = 13.sp,
                                        maxLines = 2
                                    )
                                    if (isPremium) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "PREMIUM",
                                            fontSize = 8.sp,
                                            fontWeight = FontWeight.Black,
                                            color = MaterialTheme.colorScheme.primary,
                                            letterSpacing = 0.5.sp
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Duration Selector (Slider Track style)
                    Text(
                        text = "Script Target Duration".localize(appLanguageState),
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            listOf(
                                "short" to "Short (~45s)",
                                "medium" to "Medium (~2m)",
                                "long" to "Long (~5m)"
                            ).forEach { (key, display) ->
                                val isSel = selectedDuration == key
                                Text(
                                    text = display.localize(appLanguageState),
                                    fontSize = 11.sp,
                                    fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(6.dp))
                        
                        var sliderValue by remember(selectedDuration) {
                            mutableStateOf(
                                when (selectedDuration) {
                                    "medium" -> 1f
                                    "long" -> 2f
                                    else -> 0f
                                }
                            )
                        }
                        
                        Slider(
                            value = sliderValue,
                            onValueChange = { newValue ->
                                sliderValue = newValue
                                val mappedKey = if (newValue < 0.5f) "short"
                                                else if (newValue < 1.5f) "medium"
                                                else "long"
                                if (selectedDuration != mappedKey) {
                                    selectedDuration = mappedKey
                                    scriptViewModel.saveScriptDuration(mappedKey)
                                }
                            },
                            valueRange = 0f..2f,
                            steps = 1,
                            colors = SliderDefaults.colors(
                                activeTrackColor = MaterialTheme.colorScheme.primary,
                                inactiveTrackColor = MaterialTheme.colorScheme.outlineVariant,
                                activeTickColor = MaterialTheme.colorScheme.onPrimary,
                                inactiveTickColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                thumbColor = MaterialTheme.colorScheme.primary
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    // Aesthetic Tone Semicircle/Dial Selector
                    val toneList = listOf("casual", "professional", "energetic", "educational", "entertaining", "tech", "dramatic", "comedy")
                    val currentIndex = toneList.indexOf(selectedTone).coerceAtLeast(0)

                    val prevIndex = (currentIndex - 1 + toneList.size) % toneList.size
                    val nextIndex = (currentIndex + 1) % toneList.size

                    val prevTone = toneList[prevIndex]
                    val nextTone = toneList[nextIndex]

                    fun getToneDisplayName(tone: String): String = when(tone) {
                        "casual" -> "CASUAL"
                        "professional" -> "PRO"
                        "energetic" -> "ENERGY"
                        "educational" -> "EDU"
                        "entertaining" -> "FUN"
                        "tech" -> "TECH"
                        "dramatic" -> "DRAMA"
                        "comedy" -> "COMEDY"
                        else -> tone.uppercase()
                    }

                    fun getToneEmoji(tone: String): String = when(tone) {
                        "casual" -> "💬"
                        "professional" -> "💼"
                        "energetic" -> "⚡"
                        "educational" -> "🎓"
                        "entertaining" -> "🎭"
                        "tech" -> "💻"
                        "dramatic" -> "🎬"
                        "comedy" -> "😆"
                        else -> "✨"
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Aesthetic Script Tone".localize(appLanguageState),
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = "💡 Swipe to Slide".localize(appLanguageState),
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    
                    // Drag & swipe state for Tactile Tone dial
                    var toneDragAmount by remember { mutableStateOf(0f) }
                    val animatedDragAmount by animateFloatAsState(
                        targetValue = toneDragAmount,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessLow
                        ),
                        label = "tone_drag_amount"
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                            .draggable(
                                orientation = Orientation.Horizontal,
                                state = rememberDraggableState { delta ->
                                    // Coerce maximum drag offset for a natural tactile feel
                                    toneDragAmount = (toneDragAmount + delta).coerceIn(-180f, 180f)
                                },
                                onDragStopped = { velocity ->
                                    val threshold = 70f
                                    val targetTone = when {
                                        toneDragAmount < -threshold -> nextTone
                                        toneDragAmount > threshold -> prevTone
                                        else -> null
                                    }
                                    if (targetTone != null) {
                                        selectedTone = targetTone
                                        scriptViewModel.saveScriptTone(targetTone)
                                        // Give a nice visual feedback: snap to the opposite side and spring animate in!
                                        val oppositeSideOffset = if (toneDragAmount < -threshold) 120f else -120f
                                        toneDragAmount = oppositeSideOffset
                                        coroutineScope.launch {
                                            kotlinx.coroutines.delay(16)
                                            toneDragAmount = 0f
                                        }
                                    } else {
                                        toneDragAmount = 0f
                                    }
                                }
                            )
                            .padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .graphicsLayer {
                                    translationX = animatedDragAmount
                                },
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Left Option
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(RoundedCornerShape(32.dp))
                                    .background(MaterialTheme.colorScheme.surface)
                                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), RoundedCornerShape(32.dp))
                                    .clickable {
                                        selectedTone = prevTone
                                        scriptViewModel.saveScriptTone(prevTone)
                                    }
                                    .padding(4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(getToneEmoji(prevTone), fontSize = 16.sp)
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = getToneDisplayName(prevTone),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            
                            Spacer(modifier = Modifier.width(16.dp))
                            
                            // Center / Selected Option
                            Box(
                                modifier = Modifier
                                    .size(96.dp)
                                    .clip(RoundedCornerShape(48.dp))
                                    .background(
                                        Brush.radialGradient(
                                            listOf(
                                                MaterialTheme.colorScheme.primaryContainer,
                                                MaterialTheme.colorScheme.surface
                                            )
                                        )
                                    )
                                    .border(
                                        BorderStroke(
                                            2.dp,
                                            Brush.sweepGradient(
                                                listOf(
                                                    MaterialTheme.colorScheme.primary,
                                                    MaterialTheme.colorScheme.secondary,
                                                    MaterialTheme.colorScheme.tertiary,
                                                    MaterialTheme.colorScheme.primary
                                                )
                                            )
                                        ),
                                        shape = RoundedCornerShape(48.dp)
                                    )
                                    .padding(6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Text(getToneEmoji(selectedTone), fontSize = 28.sp)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = getToneDisplayName(selectedTone),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Black,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        letterSpacing = 0.5.sp
                                    )
                                }
                            }
                            
                            Spacer(modifier = Modifier.width(16.dp))
                            
                            // Right Option
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(RoundedCornerShape(32.dp))
                                    .background(MaterialTheme.colorScheme.surface)
                                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), RoundedCornerShape(32.dp))
                                    .clickable {
                                        selectedTone = nextTone
                                        scriptViewModel.saveScriptTone(nextTone)
                                    }
                                    .padding(4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(getToneEmoji(nextTone), fontSize = 16.sp)
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = getToneDisplayName(nextTone),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    // Language Dropdown
                    Text(
                        text = "Target Audio Language".localize(appLanguageState),
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    val languages = listOf("english", "hindi", "hinglish", "spanish", "french", "german", "arabic", "japanese", "portuguese")
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = selectedLanguage.uppercase().localize(appLanguageState),
                            onValueChange = {},
                            readOnly = true,
                            modifier = Modifier.fillMaxWidth(),
                            trailingIcon = {
                                IconButton(onClick = { langExpanded = true }) {
                                    Icon(
                                        imageVector = Icons.Default.ArrowDropDown,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline
                            )
                        )
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .clickable { langExpanded = true }
                        )
                        DropdownMenu(
                            expanded = langExpanded,
                            onDismissRequest = { langExpanded = false },
                            modifier = Modifier.background(MaterialTheme.colorScheme.surface)
                        ) {
                            languages.forEach { langName ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = langName.uppercase().localize(appLanguageState),
                                            color = MaterialTheme.colorScheme.onSurface,
                                            fontWeight = FontWeight.Bold
                                        )
                                    },
                                    onClick = {
                                        selectedLanguage = langName
                                        targetLanguage = langName
                                        scriptViewModel.saveScriptLanguage(langName)
                                        langExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    // Error Box
                    AnimatedVisibility(visible = generateUiState is GenerateUiState.Error) {
                        val state = generateUiState
                        if (state is GenerateUiState.Error) {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Error,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = state.message.localize(appLanguageState),
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                        fontSize = 13.sp
                                    )
                                }
                            }
                        }
                    }

                    // Generate Button with shimmering premium visual
                    Button(
                        onClick = {
                            val isOnline = com.ritvyom.yashoraReelgenerator.presentation.utils.NetworkUtils.isInternetAvailable(context)
                            if (!isOnline) {
                                isOfflineMode = true
                            } else {
                                if (topic.trim().isNotBlank()) {
                                    showScriptAd = true
                                } else {
                                    scriptViewModel.generateScript(
                                        topic = topic,
                                        duration = selectedDuration,
                                        tone = selectedTone,
                                        language = targetLanguage,
                                        platform = selectedPlatform
                                    )
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(58.dp)
                            .testTag("trigger_script_generation_btn")
                            .border(
                                BorderStroke(1.5.dp, MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.4f)),
                                shape = RoundedCornerShape(29.dp)
                            ),
                        shape = RoundedCornerShape(29.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                        contentPadding = PaddingValues()
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(
                                            MaterialTheme.colorScheme.primary,
                                            MaterialTheme.colorScheme.secondary,
                                            MaterialTheme.colorScheme.tertiary
                                        )
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Generate Script ✨".localize(appLanguageState),
                                    fontWeight = FontWeight.Black,
                                    fontSize = 16.sp,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    letterSpacing = 1.sp
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Recent History List Preview in Form
                    if (historyState.isNotEmpty()) {
                        Divider(modifier = Modifier.padding(vertical = 8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Recently Generated Scripts".localize(appLanguageState),
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "See All".localize(appLanguageState),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.clickable { showHistoryOnly = true }
                            )
                        }

                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(historyState.take(5)) { script ->
                                Card(
                                    modifier = Modifier
                                        .width(180.dp)
                                        .clickable { scriptViewModel.selectScript(script) },
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                    )
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Text(
                                            text = script.title,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp,
                                            maxLines = 1
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = script.topic,
                                            fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 2,
                                            lineHeight = 14.sp
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                }
            }

            BackOnlineBanner(
                appLanguage = appLanguageState,
                visible = showBackOnlineBanner,
                modifier = Modifier.align(Alignment.TopCenter)
            )
        }
    }
}
