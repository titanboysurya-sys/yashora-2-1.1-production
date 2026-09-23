package com.ritvyom.yashoraReelgenerator.presentation.screens

import androidx.activity.compose.BackHandler
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
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
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.ritvyom.yashoraReelgenerator.data.local.entities.ProjectEntity
import com.ritvyom.yashoraReelgenerator.domain.models.MediaSuggestion
import com.ritvyom.yashoraReelgenerator.domain.models.Scene
import com.ritvyom.yashoraReelgenerator.presentation.components.SearchableLanguageSelector
import com.ritvyom.yashoraReelgenerator.presentation.components.LanguageData
import com.ritvyom.yashoraReelgenerator.presentation.utils.VoiceRecorderManager
import com.ritvyom.yashoraReelgenerator.presentation.utils.localize
import com.ritvyom.yashoraReelgenerator.presentation.viewmodels.MainViewModel
import com.ritvyom.yashoraReelgenerator.presentation.viewmodels.ScriptViewModel
import com.ritvyom.yashoraReelgenerator.presentation.viewmodels.ScriptViewModelFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManualStudioScreen(
    viewModel: MainViewModel,
    appLanguageState: String,
    onBack: () -> Unit,
    onOpenEditor: (ProjectEntity) -> Unit,
    onNavigateToGeneration: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val scriptViewModel: ScriptViewModel = viewModel(
        factory = ScriptViewModelFactory(context.applicationContext as android.app.Application)
    )
    val generateUiState by scriptViewModel.generateUiState.collectAsState()

    // Project metadata states
    var projectTitle by remember { mutableStateOf("") }
    var scriptMode by remember { mutableIntStateOf(0) } // 0 = Write Custom Script, 1 = AI Prompt Assist
    var customScriptText by remember { mutableStateOf("") }
    var aiTopicText by remember { mutableStateOf("") }
    var aiTone by remember { mutableStateOf("Cinematic") }
    var selectedScriptLanguage by remember { mutableStateOf("HINDI") }
    var selectedScriptDuration by remember { mutableStateOf("short") } // "short", "medium", "long"
    var targetDurationSeconds by remember { mutableIntStateOf(30) }
    var isGeneratingScript by remember { mutableStateOf(false) }

    // Scenes list
    val scenesList = remember { mutableStateListOf<Scene>() }

    // Helper to split text into scenes
    fun splitTextIntoScenes(text: String) {
        val clean = text.trim()
        if (clean.isEmpty()) return
        val sentences = clean.split(Regex("(?<=[.!?\\n])\\s+")).filter { it.isNotBlank() }
        scenesList.clear()
        sentences.forEachIndexed { index, sentence ->
            scenesList.add(
                Scene(
                    sceneNumber = index + 1,
                    narrationText = sentence.trim(),
                    visualPrompt = sentence.trim().take(40),
                    subtitle = sentence.trim(),
                    mediaPath = null,
                    mediaType = "IMAGE",
                    durationSeconds = 5,
                    durationMs = 5000L
                )
            )
        }
        Toast.makeText(context, "Created ${scenesList.size} scenes from script!".localize(appLanguageState), Toast.LENGTH_SHORT).show()
    }

    LaunchedEffect(generateUiState) {
        when (val state = generateUiState) {
            is com.ritvyom.yashoraReelgenerator.presentation.viewmodels.GenerateUiState.Success -> {
                customScriptText = state.script.fullScript
                if (projectTitle.isBlank()) {
                    projectTitle = state.script.title
                }
                splitTextIntoScenes(state.script.fullScript)
                scriptMode = 0
                isGeneratingScript = false
                Toast.makeText(context, "AI Script generated successfully!".localize(appLanguageState), Toast.LENGTH_SHORT).show()
                scriptViewModel.resetGenerateState()
            }
            is com.ritvyom.yashoraReelgenerator.presentation.viewmodels.GenerateUiState.Error -> {
                isGeneratingScript = false
                Toast.makeText(context, state.message, Toast.LENGTH_SHORT).show()
                scriptViewModel.resetGenerateState()
            }
            is com.ritvyom.yashoraReelgenerator.presentation.viewmodels.GenerateUiState.Loading -> {
                isGeneratingScript = true
            }
            else -> {}
        }
    }

    // Canvas Aspect Ratio
    var selectedAspectRatio by remember { mutableStateOf("9:16") }
    val aspectRatios = listOf(
        Triple("9:16", "Reels / Shorts", "📱"),
        Triple("16:9", "YouTube / TV", "🖥️"),
        Triple("1:1", "Square Feed", "🔲"),
        Triple("4:5", "Portrait Post", "📸"),
        Triple("21:9", "Ultra Cinema", "🎬")
    )

    // Voice & Audio Tab: 0 = Record Voice, 1 = Device Music, 2 = AI Voice Presets
    var audioTabState by remember { mutableIntStateOf(0) }

    // Audio Recorder States
    val isRecording by VoiceRecorderManager.isRecording.collectAsState()
    val isPlayingRecording by VoiceRecorderManager.isPlaying.collectAsState()
    val recordingDuration by VoiceRecorderManager.recordingDurationSeconds.collectAsState()
    val recordedAudioFile by VoiceRecorderManager.recordedAudioFile.collectAsState()
    val audioAmplitude by VoiceRecorderManager.audioAmplitude.collectAsState()

    // Device Music State
    var customDeviceMusicUri by remember { mutableStateOf<Uri?>(null) }
    var customDeviceMusicName by remember { mutableStateOf("") }
    
    // Master Voiceover State (Recorded or Imported for all frames)
    var importedVoicePath by remember { mutableStateOf<String?>(null) }
    var importedVoiceName by remember { mutableStateOf<String?>(null) }
    var voiceSelectionMode by remember { mutableIntStateOf(0) } // 0 = In-App Mic Record, 1 = Pick Audio from Phone
    
    var musicVolume by remember { mutableFloatStateOf(0.4f) }
    var voiceVolume by remember { mutableFloatStateOf(0.9f) }
    var selectedBgMusicCategory by remember { mutableStateOf("Cinematic") }

    // Media Search & Library States
    var mediaTabState by remember { mutableIntStateOf(0) } // 0 = Device Media (Gallery), 1 = 16 Stock Media Sources, 2 = Scene Storyboard
    var stockSearchQuery by remember { mutableStateOf("cinematic nature landscape") }
    var stockMediaType by remember { mutableStateOf("VIDEO") } // VIDEO or IMAGE
    var selectedStockSource by remember { mutableStateOf("All Sources") }
    var isSearchingStock by remember { mutableStateOf(false) }
    val stockResults = remember { mutableStateListOf<MediaSuggestion>() }

    // Selected device gallery media URIs
    val selectedGalleryUris = remember { mutableStateListOf<String>() }

    // Recording permission launcher
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

    // Audio File Picker Launcher (BGM)
    val audioPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            customDeviceMusicUri = uri
            val fileName = try {
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    cursor.moveToFirst()
                    cursor.getString(nameIndex)
                } ?: "Custom_Track.mp3"
            } catch (e: Exception) {
                "Custom_Track.mp3"
            }
            customDeviceMusicName = fileName
            Toast.makeText(context, "Audio track selected: $fileName".localize(appLanguageState), Toast.LENGTH_SHORT).show()
        }
    }

    // Master Voice File Picker Launcher (User's pre-recorded voice from phone)
    val deviceVoicePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val fileName = try {
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    cursor.moveToFirst()
                    cursor.getString(nameIndex)
                } ?: "Recorded_Voice.m4a"
            } catch (e: Exception) {
                "Recorded_Voice.m4a"
            }
            val cachedFile = VoiceRecorderManager.copyUriToCache(context, uri, "master_voice")
            if (cachedFile != null) {
                importedVoicePath = cachedFile.absolutePath
                importedVoiceName = fileName
                Toast.makeText(context, "Voice attached for all frames: $fileName".localize(appLanguageState), Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Gallery Multiple Media Picker (Photos & Videos)
    val galleryMediaPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (!uris.isNullOrEmpty()) {
            val uriStrings = uris.map { it.toString() }
            selectedGalleryUris.addAll(uriStrings)
            // Automatically add to scene storyboard
            uriStrings.forEach { uriStr ->
                val uri = Uri.parse(uriStr)
                val isVideo = try {
                    val mime = context.contentResolver.getType(uri)
                    mime?.startsWith("video", ignoreCase = true) == true || uriStr.contains("video", ignoreCase = true)
                } catch (e: Exception) {
                    false
                }
                scenesList.add(
                    Scene(
                        sceneNumber = scenesList.size + 1,
                        narrationText = "",
                        visualPrompt = "Local Device Media",
                        subtitle = "Scene #${scenesList.size + 1}",
                        mediaPath = uriStr,
                        mediaType = if (isVideo) "VIDEO" else "IMAGE",
                        durationSeconds = 5,
                        durationMs = 5000L
                    )
                )
            }
            Toast.makeText(context, "Added ${uris.size} media items to Studio!".localize(appLanguageState), Toast.LENGTH_SHORT).show()
        }
    }

    // Function to search stock media
    fun executeStockSearch(forceRefresh: Boolean = false) {
        if (stockSearchQuery.isBlank()) return
        if (!forceRefresh) {
            val cached = viewModel.repository.getCachedSuggestions(
                query = stockSearchQuery,
                mediaType = stockMediaType,
                aspectRatio = selectedAspectRatio
            )
            if (cached != null && cached.isNotEmpty()) {
                stockResults.clear()
                stockResults.addAll(cached)
                isSearchingStock = false
                return
            }
        }
        isSearchingStock = true
        coroutineScope.launch(Dispatchers.IO) {
            try {
                val results = viewModel.repository.searchAlternativeSuggestions(
                    query = stockSearchQuery,
                    mediaType = stockMediaType,
                    aspectRatio = selectedAspectRatio,
                    unsplashKey = viewModel.unsplashApiKey.value,
                    pexelsKey = viewModel.pexelsApiKey.value,
                    forceRefresh = forceRefresh
                )
                withContext(Dispatchers.Main) {
                    stockResults.clear()
                    stockResults.addAll(results)
                    isSearchingStock = false
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    isSearchingStock = false
                    Toast.makeText(context, "Stock search failed. Using fallback catalog.".localize(appLanguageState), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // Helper to compile into Studio Project and Launch
    fun launchStudioProject(isAiGenerationFlow: Boolean = false) {
        val finalTitle = if (projectTitle.isNotBlank()) projectTitle else {
            if (customScriptText.isNotBlank()) customScriptText.take(20) + "..." else "Edit #${System.currentTimeMillis() % 1000}"
        }

        // If no scenes yet, create a default scene from script
        val finalScenes = if (scenesList.isNotEmpty()) {
            scenesList.toList()
        } else {
            listOf(
                Scene(
                    sceneNumber = 1,
                    narrationText = customScriptText.ifBlank { "" },
                    visualPrompt = if (customScriptText.isNotBlank()) customScriptText.take(50) else "Scene 1",
                    subtitle = if (customScriptText.isNotBlank()) customScriptText.take(30) else "",
                    mediaPath = selectedGalleryUris.firstOrNull(),
                    mediaType = if (selectedGalleryUris.firstOrNull()?.contains("video", true) == true) "VIDEO" else "IMAGE",
                    durationSeconds = 5,
                    durationMs = 5000L
                )
            )
        }

        val finalMasterVoice = recordedAudioFile?.absolutePath ?: importedVoicePath
        val finalBgMusic = customDeviceMusicUri?.let { uri ->
            VoiceRecorderManager.copyUriToCache(context, uri, "imported_bgm")?.absolutePath ?: uri.toString()
        }

        if (isAiGenerationFlow) {
            viewModel.scriptText.value = customScriptText
            viewModel.topicContext.value = aiTopicText.ifEmpty { finalTitle }
            viewModel.selectedAspectRatio.value = selectedAspectRatio
            viewModel.bgMusicCategory.value = selectedBgMusicCategory
            viewModel.bgMusicVolume.value = musicVolume
            viewModel.voiceVolume.value = voiceVolume
            if (!finalMasterVoice.isNullOrBlank()) {
                viewModel.setCustomMasterVoice(finalMasterVoice, "Master Voiceover")
            }
            if (!finalBgMusic.isNullOrBlank()) {
                viewModel.setCustomBgMusic(finalBgMusic, customDeviceMusicName.ifEmpty { "Custom Music" })
            }
            onNavigateToGeneration()
        } else {
            viewModel.createManualStudioProject(
                title = finalTitle,
                scriptText = customScriptText,
                scenes = finalScenes,
                aspectRatio = selectedAspectRatio,
                videoStyle = "Cinematic",
                voiceName = "Professional Man",
                voiceCategory = "Male",
                language = "Hindi",
                bgMusicCategory = selectedBgMusicCategory,
                bgMusicVolume = musicVolume,
                voiceVolume = voiceVolume,
                topicContext = "Edit",
                customMasterVoicePath = finalMasterVoice,
                customBgMusicPath = finalBgMusic,
                onComplete = { createdProject ->
                    Toast.makeText(context, "Studio Project Ready!".localize(appLanguageState), Toast.LENGTH_SHORT).show()
                    onOpenEditor(createdProject)
                }
            )
        }
    }

    // Initial search load
    LaunchedEffect(Unit) {
        if (stockResults.isEmpty()) {
            executeStockSearch()
        }
    }

    BackHandler(enabled = isRecording) {
        VoiceRecorderManager.stopRecording()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Default.ArrowBack,
                            contentDescription = "Back".localize(appLanguageState),
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                },
                title = {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Edit".localize(appLanguageState),
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Text(
                                    text = "STUDIO".localize(appLanguageState),
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Black,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Text(
                            text = "Multi-Track Timeline • 1-Click Voice • Music & Gallery".localize(appLanguageState),
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                actions = {
                    IconButton(onClick = {
                        customScriptText = ""
                        aiTopicText = ""
                        projectTitle = ""
                        scenesList.clear()
                        selectedGalleryUris.clear()
                        VoiceRecorderManager.clear()
                        Toast.makeText(context, "Studio reset to clean canvas".localize(appLanguageState), Toast.LENGTH_SHORT).show()
                    }) {
                        Icon(
                            imageVector = Icons.Outlined.RestartAlt,
                            contentDescription = "Reset",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        bottomBar = {
            Surface(
                tonalElevation = 8.dp,
                shadowElevation = 8.dp,
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = { launchStudioProject(isAiGenerationFlow = true) },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("AI Synthesis", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }

                    Button(
                        onClick = { launchStudioProject(isAiGenerationFlow = false) },
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        modifier = Modifier.weight(1.3f)
                    ) {
                        Icon(Icons.Default.MovieFilter, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Open Studio Editor", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // STEP 1: Project Title & Script
            item {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("1", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, fontSize = 14.sp)
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text("Script & Topic Engine".localize(appLanguageState), fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                Text("Write custom narration or generate with AI".localize(appLanguageState), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }

                        // Project Title TextField
                        OutlinedTextField(
                            value = projectTitle,
                            onValueChange = { projectTitle = it },
                            label = { Text("Project Title (Optional)".localize(appLanguageState)) },
                            placeholder = { Text("e.g. 5 Tech Secrets 2026") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp)
                        )

                        // Mode Selector: Manual vs AI
                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            SegmentedButton(
                                selected = scriptMode == 0,
                                onClick = { scriptMode = 0 },
                                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                            ) {
                                Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Write Custom Script", fontSize = 12.sp)
                            }
                            SegmentedButton(
                                selected = scriptMode == 1,
                                onClick = { scriptMode = 1 },
                                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                            ) {
                                Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("AI Topic Assist", fontSize = 12.sp)
                            }
                        }

                        // SCRIPT LANGUAGE SELECTOR (Available for both AI and Custom Script)
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Translate,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Script Language / भाषा".localize(appLanguageState),
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 13.sp
                                    )
                                }
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.secondaryContainer
                                ) {
                                    Text(
                                        text = selectedScriptLanguage,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            // Quick Popular Language Chips + Searchable Selector
                            val quickLangs = listOf(
                                "HINDI" to "🇮🇳 हिंदी",
                                "HINGLISH" to "🗣️ Hinglish",
                                "ENGLISH" to "🇬🇧 English",
                                "MARATHI" to "मराठी",
                                "BENGALI" to "বাংলা",
                                "TAMIL" to "தமிழ்",
                                "TELUGU" to "తెలుగు",
                                "GUJARATI" to "ગુજરાતી",
                                "URDU" to "اردو",
                                "PUNJABI" to "ਪੰਜਾਬੀ",
                                "SPANISH" to "Español",
                                "ARABIC" to "العربية"
                            )
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                items(quickLangs) { (code, label) ->
                                    FilterChip(
                                        selected = selectedScriptLanguage.equals(code, ignoreCase = true),
                                        onClick = { selectedScriptLanguage = code },
                                        label = { Text(label, fontSize = 11.sp, fontWeight = if (selectedScriptLanguage.equals(code, ignoreCase = true)) FontWeight.Bold else FontWeight.Normal) },
                                        shape = RoundedCornerShape(8.dp),
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    )
                                }
                            }

                            // Full Searchable Language Dialog Selector for 40+ languages
                            SearchableLanguageSelector(
                                selectedLanguage = selectedScriptLanguage,
                                onLanguageSelected = { lang -> selectedScriptLanguage = lang },
                                appLanguage = appLanguageState,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }

                        // SCRIPT LENGTH & TARGET DURATION SELECTOR
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Timer,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.secondary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Script Length / Duration (लम्बाई)".localize(appLanguageState),
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 13.sp
                                    )
                                }
                                Text(
                                    text = "~${targetDurationSeconds}s • ${if (targetDurationSeconds <= 30) "3-4 Scenes" else if (targetDurationSeconds <= 60) "5-7 Scenes" else "8-12 Scenes"}",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }

                            // 3 Preset Cards
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                val lengthPresets = listOf(
                                    Triple("short", "Short Reel", "~15-30s • 40-70 words"),
                                    Triple("medium", "Medium Story", "~30-60s • 80-140 words"),
                                    Triple("long", "Long Video", "~60-90s • 160-240 words")
                                )
                                lengthPresets.forEach { (key, title, subtitle) ->
                                    val isSel = selectedScriptDuration == key
                                    Surface(
                                        onClick = {
                                            selectedScriptDuration = key
                                            targetDurationSeconds = when (key) {
                                                "short" -> 30
                                                "medium" -> 60
                                                else -> 90
                                            }
                                        },
                                        shape = RoundedCornerShape(10.dp),
                                        color = if (isSel) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                        border = BorderStroke(
                                            if (isSel) 1.5.dp else 1.dp,
                                            if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                                        ),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Text(
                                                text = title,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = subtitle,
                                                fontSize = 9.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                textAlign = TextAlign.Center,
                                                lineHeight = 11.sp
                                            )
                                        }
                                    }
                                }
                            }

                            // Precision Length Slider
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f), RoundedCornerShape(8.dp))
                                    .padding(horizontal = 10.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("15s", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Slider(
                                    value = targetDurationSeconds.toFloat(),
                                    onValueChange = { newVal ->
                                        val rounded = (newVal / 5).toInt() * 5
                                        targetDurationSeconds = rounded.coerceIn(15, 90)
                                        selectedScriptDuration = when {
                                            targetDurationSeconds <= 35 -> "short"
                                            targetDurationSeconds <= 65 -> "medium"
                                            else -> "long"
                                        }
                                    },
                                    valueRange = 15f..90f,
                                    steps = 14,
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(horizontal = 6.dp)
                                )
                                Text("90s", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }

                        if (scriptMode == 0) {
                            // Manual Multi-line script field
                            OutlinedTextField(
                                value = customScriptText,
                                onValueChange = { customScriptText = it },
                                label = { Text("Your Custom Reel Script / Story".localize(appLanguageState)) },
                                placeholder = { Text("Write scene by scene narration here...\nExample: Namaste dosto! Aaj hum janenge 3 aisi baatein jo aapko success bana sakti hain.") },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 120.dp, max = 220.dp),
                                shape = RoundedCornerShape(12.dp)
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val wordCount = if (customScriptText.isBlank()) 0 else customScriptText.trim().split(Regex("\\s+")).size
                                val estSeconds = (wordCount * 0.45).toInt().coerceAtLeast(5)
                                Text(
                                    text = "$wordCount words • Est. ~${estSeconds}s",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                Button(
                                    onClick = { splitTextIntoScenes(customScriptText) },
                                    enabled = customScriptText.isNotBlank(),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Icon(Icons.Default.CallSplit, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Split into Scenes", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        } else {
                            // AI Topic input
                            OutlinedTextField(
                                value = aiTopicText,
                                onValueChange = { aiTopicText = it },
                                label = { Text("Topic or Idea".localize(appLanguageState)) },
                                placeholder = { Text("e.g. Mystery of Bermuda Triangle, Morning Routine") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp)
                            )

                            // Tone selection chips
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                val tones = listOf("Cinematic", "Motivational", "Educational", "Funny / Meme", "Dramatic", "Mystery")
                                items(tones) { tone ->
                                    FilterChip(
                                        selected = aiTone == tone,
                                        onClick = { aiTone = tone },
                                        label = { Text(tone, fontSize = 11.sp) },
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                }
                            }

                            Button(
                                onClick = {
                                    if (aiTopicText.isNotBlank()) {
                                        isGeneratingScript = true
                                        scriptViewModel.generateScript(
                                            topic = aiTopicText,
                                            duration = selectedScriptDuration,
                                            tone = aiTone.lowercase(),
                                            language = selectedScriptLanguage.lowercase(),
                                            platform = "YouTube Shorts / Reels",
                                            bypassCache = true
                                        )
                                    }
                                },
                                enabled = aiTopicText.isNotBlank() && !isGeneratingScript,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                if (isGeneratingScript) {
                                    CircularProgressIndicator(modifier = Modifier.size(18.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Generating Script with AI...", fontSize = 12.sp)
                                } else {
                                    Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Generate Full Script", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }
                            }
                        }
                    }
                }
            }

            // STEP 2: Aspect Ratio & Canvas Format
            item {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("2", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, fontSize = 14.sp)
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text("Canvas & Aspect Ratio".localize(appLanguageState), fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                Text("Choose frame format for your target platform".localize(appLanguageState), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }

                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(aspectRatios) { (ratio, label, icon) ->
                                val isSelected = selectedAspectRatio == ratio
                                Card(
                                    shape = RoundedCornerShape(14.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                    ),
                                    border = BorderStroke(
                                        if (isSelected) 2.dp else 1.dp,
                                        if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                                    ),
                                    modifier = Modifier
                                        .width(110.dp)
                                        .clickable { selectedAspectRatio = ratio }
                                ) {
                                    Column(
                                        modifier = Modifier.padding(12.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(icon, fontSize = 22.sp)
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(ratio, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                        Text(label, fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // STEP 3: Voice & Audio Studio (Mic Recording, Custom Device Music, AI Voice)
            item {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("3", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, fontSize = 14.sp)
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text("Voice & Audio Studio".localize(appLanguageState), fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                Text("Record your own voice, add custom music, or use AI TTS".localize(appLanguageState), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }

                        // Audio Options Tabs
                        TabRow(
                            selectedTabIndex = audioTabState,
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                            modifier = Modifier.clip(RoundedCornerShape(12.dp))
                        ) {
                            Tab(
                                selected = audioTabState == 0,
                                onClick = { audioTabState = 0 },
                                text = { Text("🎙️ Master Voice (All Frames)".localize(appLanguageState), fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                            )
                            Tab(
                                selected = audioTabState == 1,
                                onClick = { audioTabState = 1 },
                                text = { Text("🎵 Background Music".localize(appLanguageState), fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                            )
                            Tab(
                                selected = audioTabState == 2,
                                onClick = { audioTabState = 2 },
                                text = { Text("⚡ AI Voice & Volume".localize(appLanguageState), fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                            )
                        }

                        when (audioTabState) {
                            0 -> {
                                // Master Voiceover for all frames: Record or Pick from Phone
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                                        .padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    // Mode switcher: Mic Record vs Phone Audio Import
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(MaterialTheme.colorScheme.surface)
                                            .padding(4.dp),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Button(
                                            onClick = { voiceSelectionMode = 0 },
                                            modifier = Modifier.weight(1f).height(36.dp),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = if (voiceSelectionMode == 0) MaterialTheme.colorScheme.primary else Color.Transparent,
                                                contentColor = if (voiceSelectionMode == 0) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                                            ),
                                            shape = RoundedCornerShape(8.dp),
                                            contentPadding = PaddingValues(0.dp)
                                        ) {
                                            Icon(Icons.Default.Mic, contentDescription = null, modifier = Modifier.size(14.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Record Voice".localize(appLanguageState), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }

                                        Button(
                                            onClick = { voiceSelectionMode = 1 },
                                            modifier = Modifier.weight(1f).height(36.dp),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = if (voiceSelectionMode == 1) MaterialTheme.colorScheme.primary else Color.Transparent,
                                                contentColor = if (voiceSelectionMode == 1) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                                            ),
                                            shape = RoundedCornerShape(8.dp),
                                            contentPadding = PaddingValues(0.dp)
                                        ) {
                                            Icon(Icons.Default.Folder, contentDescription = null, modifier = Modifier.size(14.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Pick from Phone".localize(appLanguageState), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }

                                    if (voiceSelectionMode == 0) {
                                        // 🎙️ IN-APP LIVE VOICE RECORDER (Record full speech across all frames at once)
                                        Column(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            Text(
                                                text = if (isRecording) "Recording Full Voiceover in Progress...".localize(appLanguageState)
                                                else if (recordedAudioFile != null) "Full Voiceover Recording Ready!".localize(appLanguageState)
                                                else "Record your continuous voice for the whole video in 1 take:".localize(appLanguageState),
                                                fontSize = 11.5.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = if (isRecording) Color(0xFFFF5252) else MaterialTheme.colorScheme.onSurface
                                            )

                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                                            ) {
                                                IconButton(
                                                    onClick = {
                                                        if (isRecording) {
                                                            val file = VoiceRecorderManager.stopRecording()
                                                            if (file != null) {
                                                                importedVoicePath = null
                                                                importedVoiceName = null
                                                                Toast.makeText(context, "Full Voiceover recorded and saved!".localize(appLanguageState), Toast.LENGTH_SHORT).show()
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
                                                        contentDescription = "Record",
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
                                                            .size(46.dp)
                                                            .clip(CircleShape)
                                                            .background(MaterialTheme.colorScheme.secondaryContainer)
                                                    ) {
                                                        Icon(
                                                            imageVector = if (isPlayingRecording) Icons.Default.Pause else Icons.Default.PlayArrow,
                                                            contentDescription = "Play",
                                                            tint = MaterialTheme.colorScheme.onSecondaryContainer
                                                        )
                                                    }

                                                    IconButton(
                                                        onClick = {
                                                            VoiceRecorderManager.cancelRecording()
                                                            Toast.makeText(context, "Recording discarded".localize(appLanguageState), Toast.LENGTH_SHORT).show()
                                                        },
                                                        modifier = Modifier
                                                            .size(46.dp)
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

                                            // Timer and Waveform
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
                                    } else {
                                        // 📁 IMPORT PRE-RECORDED VOICE / AUDIO FROM PHONE
                                        Column(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            Text(
                                                text = "Select a pre-recorded speech or voice file from your phone storage:".localize(appLanguageState),
                                                fontSize = 11.5.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )

                                            Button(
                                                onClick = { deviceVoicePickerLauncher.launch("audio/*") },
                                                modifier = Modifier.fillMaxWidth(),
                                                shape = RoundedCornerShape(10.dp),
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                                )
                                            ) {
                                                Icon(Icons.Default.AudioFile, contentDescription = null, modifier = Modifier.size(18.dp))
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("Pick Pre-recorded Voice (.mp3 / .wav / .m4a)".localize(appLanguageState), fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                                            }

                                            if (!importedVoiceName.isNullOrBlank() && importedVoicePath != null) {
                                                Card(
                                                    shape = RoundedCornerShape(10.dp),
                                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                                    border = BorderStroke(1.dp, Color(0xFF00E676).copy(alpha = 0.6f)),
                                                    modifier = Modifier.fillMaxWidth()
                                                ) {
                                                    Row(
                                                        modifier = Modifier.padding(10.dp),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Icon(Icons.Default.Mic, contentDescription = null, tint = Color(0xFF00E676))
                                                        Spacer(modifier = Modifier.width(8.dp))
                                                        Column(modifier = Modifier.weight(1f)) {
                                                            Text(importedVoiceName ?: "Voice File", fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                                                            Text("Applied as Voiceover for all frames".localize(appLanguageState), fontSize = 10.sp, color = Color(0xFF00E676))
                                                        }
                                                        IconButton(onClick = {
                                                            importedVoicePath = null
                                                            importedVoiceName = null
                                                        }) {
                                                            Icon(Icons.Default.Close, contentDescription = "Remove", tint = MaterialTheme.colorScheme.error)
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            1 -> {
                                // Device Music File Picker
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                                        .padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Text("Pick Any Song / Music from Phone:".localize(appLanguageState), fontSize = 12.sp, fontWeight = FontWeight.Bold)

                                    Button(
                                        onClick = { audioPickerLauncher.launch("audio/*") },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(12.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer)
                                    ) {
                                        Icon(Icons.Default.AudioFile, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Browse Audio Files (.mp3 / .wav)", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }

                                    if (customDeviceMusicName.isNotEmpty()) {
                                        Card(
                                            shape = RoundedCornerShape(10.dp),
                                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(10.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(Icons.Default.MusicNote, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(customDeviceMusicName, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, modifier = Modifier.weight(1f))
                                                IconButton(onClick = {
                                                    customDeviceMusicUri = null
                                                    customDeviceMusicName = ""
                                                }) {
                                                    Icon(Icons.Default.Close, contentDescription = "Remove", tint = MaterialTheme.colorScheme.error)
                                                }
                                            }
                                        }
                                    }

                                    // Volume Slider
                                    Text("Music Volume: ${(musicVolume * 100).toInt()}%", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Slider(
                                        value = musicVolume,
                                        onValueChange = { musicVolume = it },
                                        valueRange = 0f..1f
                                    )
                                }
                            }

                            2 -> {
                                // AI Voice & Background Music Category
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Text("Select Background Music Soundtrack:".localize(appLanguageState), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        val categories = listOf("Cinematic", "Lo-Fi Beats", "Dramatic", "Upbeat Energy", "Acoustic Gentle", "Ambient Calm", "None")
                                        items(categories) { cat ->
                                            FilterChip(
                                                selected = selectedBgMusicCategory == cat,
                                                onClick = { selectedBgMusicCategory = cat },
                                                label = { Text(cat, fontSize = 11.sp) },
                                                shape = RoundedCornerShape(8.dp)
                                            )
                                        }
                                    }

                                    Text("Voice Track Volume: ${(voiceVolume * 100).toInt()}%", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Slider(
                                        value = voiceVolume,
                                        onValueChange = { voiceVolume = it },
                                        valueRange = 0f..1f
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // STEP 4: Visuals & Multi-Source Media (Device Gallery + 16 Live Media Engines)
            item {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("4", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, fontSize = 14.sp)
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text("Visuals & 16 Media Sources".localize(appLanguageState), fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                Text("Import gallery clips or search Pexels, Pixabay, NASA & 16 engines".localize(appLanguageState), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }

                        // Media Tab Bar
                        TabRow(
                            selectedTabIndex = mediaTabState,
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                            modifier = Modifier.clip(RoundedCornerShape(12.dp))
                        ) {
                            Tab(
                                selected = mediaTabState == 0,
                                onClick = { mediaTabState = 0 },
                                text = { Text("📱 Phone Gallery", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                            )
                            Tab(
                                selected = mediaTabState == 1,
                                onClick = { mediaTabState = 1 },
                                text = { Text("🌐 16+ Stock Sources", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                            )
                            Tab(
                                selected = mediaTabState == 2,
                                onClick = { mediaTabState = 2 },
                                text = { Text("🎬 Storyboard (${scenesList.size})", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                            )
                        }

                        when (mediaTabState) {
                            0 -> {
                                // Device Gallery Picker
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Button(
                                        onClick = { galleryMediaPickerLauncher.launch("image/*,video/*") },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Select Photos & Videos from Phone", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    }

                                    if (selectedGalleryUris.isNotEmpty()) {
                                        Text("Selected ${selectedGalleryUris.size} items from storage:", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            itemsIndexed(selectedGalleryUris) { index, uriStr ->
                                                Box(
                                                    modifier = Modifier
                                                        .size(80.dp)
                                                        .clip(RoundedCornerShape(10.dp))
                                                        .border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(10.dp))
                                                ) {
                                                    AsyncImage(
                                                        model = uriStr,
                                                        contentDescription = "Selected Media",
                                                        modifier = Modifier.fillMaxSize(),
                                                        contentScale = ContentScale.Crop
                                                    )
                                                    IconButton(
                                                        onClick = {
                                                            selectedGalleryUris.removeAt(index)
                                                        },
                                                        modifier = Modifier
                                                            .size(24.dp)
                                                            .align(Alignment.TopEnd)
                                                            .background(Color.Black.copy(alpha = 0.7f), CircleShape)
                                                    ) {
                                                        Icon(Icons.Default.Close, contentDescription = "Delete", tint = Color.White, modifier = Modifier.size(14.dp))
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            1 -> {
                                // 16 Stock Media Sources Search
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    // Search Bar & Filter Toggle
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        OutlinedTextField(
                                            value = stockSearchQuery,
                                            onValueChange = { stockSearchQuery = it },
                                            placeholder = { Text("Search 16 sources (e.g. coffee, rain, car)...", fontSize = 11.sp) },
                                            modifier = Modifier.weight(1f),
                                            singleLine = true,
                                            shape = RoundedCornerShape(12.dp),
                                            trailingIcon = {
                                                IconButton(onClick = { executeStockSearch() }) {
                                                    Icon(Icons.Default.Search, contentDescription = "Search", tint = MaterialTheme.colorScheme.primary)
                                                }
                                            }
                                        )

                                        Row(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                                .padding(3.dp),
                                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                                        ) {
                                            Surface(
                                                shape = RoundedCornerShape(9.dp),
                                                color = if (stockMediaType == "VIDEO") MaterialTheme.colorScheme.primary else Color.Transparent,
                                                modifier = Modifier.clickable {
                                                    if (stockMediaType != "VIDEO") {
                                                        stockMediaType = "VIDEO"
                                                        executeStockSearch(forceRefresh = false)
                                                    }
                                                }
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 7.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text("🎬", fontSize = 11.sp)
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text(
                                                        "Video".localize(appLanguageState),
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = if (stockMediaType == "VIDEO") MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }

                                            Surface(
                                                shape = RoundedCornerShape(9.dp),
                                                color = if (stockMediaType == "IMAGE") MaterialTheme.colorScheme.primary else Color.Transparent,
                                                modifier = Modifier.clickable {
                                                    if (stockMediaType != "IMAGE") {
                                                        stockMediaType = "IMAGE"
                                                        executeStockSearch(forceRefresh = false)
                                                    }
                                                }
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 7.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text("📸", fontSize = 11.sp)
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text(
                                                        "Image".localize(appLanguageState),
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = if (stockMediaType == "IMAGE") MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    // Source Filter Badges
                                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        val sources = listOf(
                                            "All Sources", "🎬 Pexels", "🌌 Pixabay", "📸 Unsplash",
                                            "🎬 PopMotion GIF", "🚀 NASA", "🏛️ Wikipedia", "🚗 NHTSA vPIC",
                                            "💊 OpenFDA", "🇺🇳 WHO GHO", "🌸 Kawaii Anime", "🏷️ NeoManga",
                                            "🖼️ Horizon Scenic", "📦 Archive.org", "⚡ Lumina Flickr",
                                            "🤖 AI Genesis", "🍕 Gourmet Food", "🛡️ OmniShield"
                                        )
                                        items(sources) { src ->
                                            FilterChip(
                                                selected = selectedStockSource == src,
                                                onClick = {
                                                    selectedStockSource = src
                                                    executeStockSearch()
                                                },
                                                label = { Text(src, fontSize = 10.sp) },
                                                shape = RoundedCornerShape(8.dp)
                                            )
                                        }
                                    }

                                    val filteredStockResults = remember(stockResults.toList(), selectedStockSource) {
                                        if (selectedStockSource == "All Sources") stockResults.toList()
                                        else stockResults.filter { item ->
                                            val src = item.source.lowercase()
                                            val filter = selectedStockSource.lowercase()
                                            when {
                                                filter.contains("pexels") -> src.contains("pexels")
                                                filter.contains("pixabay") -> src.contains("pixabay")
                                                filter.contains("unsplash") -> src.contains("unsplash") || src.contains("vivid")
                                                filter.contains("popmotion") || filter.contains("giphy") || filter.contains("gif") -> src.contains("giphy") || src.contains("meme") || src.contains("popmotion")
                                                filter.contains("wiki") -> src.contains("wiki") || src.contains("atlas")
                                                filter.contains("nasa") -> src.contains("nasa") || src.contains("cosmos")
                                                filter.contains("nhtsa") || filter.contains("vpic") -> src.contains("nhtsa") || src.contains("vpic")
                                                filter.contains("fda") -> src.contains("fda")
                                                filter.contains("who") || filter.contains("gho") -> src.contains("who") || src.contains("gho")
                                                filter.contains("neko") || filter.contains("kawaii") -> src.contains("neko") || src.contains("kawaii")
                                                filter.contains("jikan") || filter.contains("manga") || filter.contains("neomanga") -> src.contains("jikan") || src.contains("manga")
                                                filter.contains("picsum") || filter.contains("scenic") || filter.contains("horizon") -> src.contains("picsum") || src.contains("horizon")
                                                filter.contains("meal") || filter.contains("food") || filter.contains("gourmet") -> src.contains("meal") || src.contains("food")
                                                filter.contains("archive") -> src.contains("archive") || src.contains("chronos")
                                                filter.contains("flickr") || filter.contains("lumina") -> src.contains("flickr") || src.contains("lumina")
                                                filter.contains("genesis") || filter.contains("ai") -> src.contains("ai") || src.contains("genesis")
                                                filter.contains("omni") || filter.contains("shield") -> src.contains("omni") || src.contains("fallback")
                                                else -> src.contains(filter)
                                            }
                                        }
                                    }

                                    // Results Grid / Carousel
                                    if (isSearchingStock) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(140.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            CircularProgressIndicator()
                                        }
                                    } else if (filteredStockResults.isEmpty()) {
                                        Text("No media found for selected filter. Try selecting 'All Sources' or typing another query.".localize(appLanguageState), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    } else {
                                        LazyRow(
                                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            items(filteredStockResults) { item ->
                                                Card(
                                                    shape = RoundedCornerShape(12.dp),
                                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
                                                    modifier = Modifier
                                                        .width(130.dp)
                                                        .clickable {
                                                            scenesList.add(
                                                                Scene(
                                                                    sceneNumber = scenesList.size + 1,
                                                                    narrationText = "",
                                                                    visualPrompt = item.title,
                                                                    subtitle = item.title.take(30),
                                                                    mediaPath = item.url,
                                                                    remoteUrl = item.url,
                                                                    mediaType = item.mediaType,
                                                                    durationSeconds = if (item.durationSeconds > 0) item.durationSeconds.coerceIn(3, 15) else 5,
                                                                    durationMs = if (item.durationSeconds > 0) item.durationSeconds.coerceIn(3, 15) * 1000L else 5000L
                                                                )
                                                            )
                                                            Toast.makeText(context, "Added '${item.source}' clip to Storyboard!".localize(appLanguageState), Toast.LENGTH_SHORT).show()
                                                        }
                                                ) {
                                                    Column {
                                                        Box(modifier = Modifier.height(110.dp).fillMaxWidth()) {
                                                            AsyncImage(
                                                                model = item.thumbnailUrl.ifEmpty { item.url },
                                                                contentDescription = item.title,
                                                                modifier = Modifier.fillMaxSize(),
                                                                contentScale = ContentScale.Crop
                                                            )
                                                            Surface(
                                                                shape = RoundedCornerShape(4.dp),
                                                                color = Color.Black.copy(alpha = 0.7f),
                                                                modifier = Modifier.padding(4.dp).align(Alignment.TopStart)
                                                            ) {
                                                                Text(item.source, fontSize = 8.sp, color = Color.White, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
                                                            }

                                                            if (item.mediaType == "VIDEO") {
                                                                Surface(
                                                                    shape = RoundedCornerShape(4.dp),
                                                                    color = MaterialTheme.colorScheme.primary,
                                                                    modifier = Modifier.padding(4.dp).align(Alignment.BottomEnd)
                                                                ) {
                                                                    Text("▶ ${item.durationSeconds}s", fontSize = 8.sp, color = Color.White, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
                                                                }
                                                            }
                                                        }
                                                        Text(
                                                            text = item.title.ifEmpty { "Stock Asset" },
                                                            fontSize = 10.sp,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis,
                                                            modifier = Modifier.padding(6.dp)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            2 -> {
                                // Scene Storyboard Editor
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("Timeline Scenes (${scenesList.size}):", fontSize = 12.sp, fontWeight = FontWeight.Bold)

                                        TextButton(onClick = {
                                            scenesList.add(
                                                Scene(
                                                    sceneNumber = scenesList.size + 1,
                                                    narrationText = "",
                                                    visualPrompt = "New Scene",
                                                    subtitle = "Scene #${scenesList.size + 1}",
                                                    mediaPath = null,
                                                    mediaType = "IMAGE",
                                                    durationSeconds = 5,
                                                    durationMs = 5000L
                                                )
                                            )
                                        }) {
                                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Add Scene", fontSize = 11.sp)
                                        }
                                    }

                                    if (scenesList.isEmpty()) {
                                        Text("No scenes added yet. Type a script above or add photos/videos!".localize(appLanguageState), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    } else {
                                        scenesList.forEachIndexed { index, scene ->
                                            Card(
                                                shape = RoundedCornerShape(12.dp),
                                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(10.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                                ) {
                                                    // Scene index badge
                                                    Box(
                                                        modifier = Modifier
                                                            .size(28.dp)
                                                            .clip(CircleShape)
                                                            .background(MaterialTheme.colorScheme.primaryContainer),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Text("${index + 1}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                                    }

                                                    // Thumbnail preview if available
                                                    if (!scene.mediaPath.isNullOrEmpty()) {
                                                        AsyncImage(
                                                            model = scene.mediaPath,
                                                            contentDescription = null,
                                                            modifier = Modifier
                                                                .size(42.dp)
                                                                .clip(RoundedCornerShape(8.dp)),
                                                            contentScale = ContentScale.Crop
                                                        )
                                                    }

                                                    // Scene info
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Text(
                                                            text = scene.narrationText.ifEmpty { scene.subtitle.ifEmpty { "Scene ${index + 1}" } },
                                                            fontSize = 12.sp,
                                                            fontWeight = FontWeight.Medium,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                        Text(
                                                            text = "${scene.mediaType} • ${scene.durationSeconds}s duration",
                                                            fontSize = 10.sp,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
                                                    }

                                                    IconButton(onClick = { scenesList.removeAt(index) }) {
                                                        Icon(Icons.Default.DeleteOutline, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
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
            }

            // Bottom Spacing for FAB
            item {
                Spacer(modifier = Modifier.height(60.dp))
            }
        }
    }
}
