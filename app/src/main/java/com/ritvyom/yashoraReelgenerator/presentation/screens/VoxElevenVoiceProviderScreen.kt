package com.ritvyom.yashoraReelgenerator.presentation.screens

import androidx.activity.compose.BackHandler
import android.media.MediaPlayer
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ritvyom.yashoraReelgenerator.data.voxeleven.ElevenLabsClient
import com.ritvyom.yashoraReelgenerator.data.voxeleven.ElevenLabsErrorHandler
import com.ritvyom.yashoraReelgenerator.data.voxeleven.Voice
import com.ritvyom.yashoraReelgenerator.presentation.components.LottieVoiceSynthesisAnimation
import com.ritvyom.yashoraReelgenerator.presentation.components.LottiePulseLoader
import com.ritvyom.yashoraReelgenerator.presentation.utils.localize
import com.ritvyom.yashoraReelgenerator.presentation.viewmodels.MainViewModel
import kotlinx.coroutines.launch

data class LocalVoicePresetItem(
    val name: String,
    val language: String,
    val gender: String, // "Male", "Female", "Child"
    val description: String
)

val localVoiceCatalog = listOf(
    // Hindi
    LocalVoicePresetItem("Swara", "Hindi", "Female", "Hindi Natural Standard Female"),
    LocalVoicePresetItem("Madhur", "Hindi", "Male", "Hindi Standard Male Voice"),
    LocalVoicePresetItem("Kavya", "Hindi", "Female", "Hindi Soft Storyteller Voice"),
    LocalVoicePresetItem("Manish", "Hindi", "Male", "Hindi Deep Narrator Voice"),
    LocalVoicePresetItem("Aarav", "Hindi", "Male", "Hindi Bold Dynamic Voice"),
    LocalVoicePresetItem("Ananya", "Hindi", "Female", "Hindi Energetic Female Voice"),

    // English
    LocalVoicePresetItem("Jenny", "English", "Female", "US Natural Clear Female"),
    LocalVoicePresetItem("Guy", "English", "Male", "US Standard Clear Male"),
    LocalVoicePresetItem("Brian", "English", "Male", "US Deep Narrator Voice"),
    LocalVoicePresetItem("Steffan", "English", "Male", "US Dynamic Bold Male"),
    LocalVoicePresetItem("Emma", "English", "Female", "US Soft Ambient Female"),
    LocalVoicePresetItem("Michelle", "English", "Female", "US Energetic Female"),
    LocalVoicePresetItem("Aria", "English", "Female", "US Classic Storyteller"),
    LocalVoicePresetItem("Eric", "English", "Child", "US Playful Kid Voice"),
    LocalVoicePresetItem("Ana", "English", "Child", "US Sweet Child Voice"),

    // Bengali
    LocalVoicePresetItem("Tanisha", "Bengali", "Female", "Bengali Natural Female Voice"),
    LocalVoicePresetItem("Bashkar", "Bengali", "Male", "Bengali Standard Male Voice"),

    // Tamil
    LocalVoicePresetItem("Pallavi", "Tamil", "Female", "Tamil Expressive Female Voice"),
    LocalVoicePresetItem("Valluvar", "Tamil", "Male", "Tamil Classical Male Voice"),

    // Telugu
    LocalVoicePresetItem("Shruti", "Telugu", "Female", "Telugu Natural Female Voice"),
    LocalVoicePresetItem("Mohan", "Telugu", "Male", "Telugu Standard Male Voice"),

    // Gujarati
    LocalVoicePresetItem("Dhwani", "Gujarati", "Female", "Gujarati Clear Female Voice"),
    LocalVoicePresetItem("Niranjan", "Gujarati", "Male", "Gujarati Standard Male Voice"),

    // Marathi
    LocalVoicePresetItem("Aarohi", "Marathi", "Female", "Marathi Expressive Female Voice"),
    LocalVoicePresetItem("Manohar", "Marathi", "Male", "Marathi Standard Male Voice"),

    // Punjabi
    LocalVoicePresetItem("Gurpreet", "Punjabi", "Female", "Punjabi Natural Female Voice"),
    LocalVoicePresetItem("Harpreet", "Punjabi", "Male", "Punjabi Standard Male Voice"),

    // Spanish
    LocalVoicePresetItem("Elvira", "Spanish", "Female", "Spanish Natural Female Voice"),
    LocalVoicePresetItem("Alvaro", "Spanish", "Male", "Spanish Standard Male Voice"),

    // French
    LocalVoicePresetItem("Denise", "French", "Female", "French Natural Female Voice"),
    LocalVoicePresetItem("Henri", "French", "Male", "French Clear Male Voice"),

    // German
    LocalVoicePresetItem("Amala", "German", "Female", "German Expressive Female Voice"),
    LocalVoicePresetItem("Conrad", "German", "Male", "German Deep Male Voice"),

    // Japanese
    LocalVoicePresetItem("Nanami", "Japanese", "Female", "Japanese Soft Female Voice"),
    LocalVoicePresetItem("Keita", "Japanese", "Male", "Japanese Clear Male Voice"),

    // Chinese
    LocalVoicePresetItem("Xiaoxiao", "Chinese", "Female", "Mandarin Natural Female Voice"),
    LocalVoicePresetItem("Yunxi", "Chinese", "Male", "Mandarin Standard Male Voice"),

    // Korean
    LocalVoicePresetItem("SunHi", "Korean", "Female", "Korean Soft Female Voice"),
    LocalVoicePresetItem("InJoon", "Korean", "Male", "Korean Clear Male Voice"),

    // Arabic
    LocalVoicePresetItem("Zariyah", "Arabic", "Female", "Arabic Natural Female Voice"),
    LocalVoicePresetItem("Hamed", "Arabic", "Male", "Arabic Standard Male Voice"),

    // Urdu
    LocalVoicePresetItem("Uzma", "Urdu", "Female", "Urdu Expressive Female Voice"),
    LocalVoicePresetItem("Asad", "Urdu", "Male", "Urdu Standard Male Voice"),

    // Russian
    LocalVoicePresetItem("Svetlana", "Russian", "Female", "Russian Clear Female Voice"),
    LocalVoicePresetItem("Dmitry", "Russian", "Male", "Russian Deep Male Voice"),

    // Portuguese
    LocalVoicePresetItem("Francisca", "Portuguese", "Female", "Portuguese Natural Female Voice"),
    LocalVoicePresetItem("Antonio", "Portuguese", "Male", "Portuguese Clear Male Voice"),

    // Turkish
    LocalVoicePresetItem("Emel", "Turkish", "Female", "Turkish Natural Female Voice"),
    LocalVoicePresetItem("Ahmet", "Turkish", "Male", "Turkish Standard Male Voice"),

    // Indonesian
    LocalVoicePresetItem("Gadis", "Indonesian", "Female", "Indonesian Female Voice"),
    LocalVoicePresetItem("Ardi", "Indonesian", "Male", "Indonesian Male Voice"),

    // Thai
    LocalVoicePresetItem("Achara", "Thai", "Female", "Thai Female Voice"),
    LocalVoicePresetItem("Niwat", "Thai", "Male", "Thai Male Voice"),

    // Vietnamese
    LocalVoicePresetItem("HoaiMy", "Vietnamese", "Female", "Vietnamese Female Voice"),
    LocalVoicePresetItem("NamMinh", "Vietnamese", "Male", "Vietnamese Male Voice")
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoxElevenVoiceProviderScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val appLanguageState by viewModel.appLanguage.collectAsState()

    val selectedEngine by viewModel.selectedTtsEngine.collectAsState()
    val savedApiKey by viewModel.elevenLabsApiKey.collectAsState()
    val selectedVoiceId by viewModel.voxElevenVoiceId.collectAsState()
    val selectedVoiceName by viewModel.voxElevenVoiceName.collectAsState()
    val modelIdState by viewModel.voxElevenModelId.collectAsState()
    val stabilityState by viewModel.voxElevenStability.collectAsState()
    val similarityState by viewModel.voxElevenSimilarity.collectAsState()
    val favoritesState by viewModel.voxElevenFavorites.collectAsState()

    val isCheckingApiKey by viewModel.isCheckingApiKey.collectAsState()
    val apiKeyStatusMessage by viewModel.apiKeyStatus.collectAsState()
    val voxElevenSubscription by viewModel.voxElevenSubscription.collectAsState()
    val isFetchingSubscription by viewModel.isFetchingSubscription.collectAsState()
    val voicesList by viewModel.voxElevenVoices.collectAsState()
    val isFetchingVoices by viewModel.isFetchingVoices.collectAsState()
    val playingPreviewVoiceId by viewModel.playingPreviewVoiceId.collectAsState()

    LaunchedEffect(savedApiKey) {
        if (savedApiKey.isNotBlank()) {
            viewModel.refreshVoxElevenSubscription()
        }
    }

    val savedVoiceClips by viewModel.savedVoiceClips.collectAsState()
    val isGeneratingScriptVoice by viewModel.isGeneratingScriptVoice.collectAsState()
    val scriptVoiceStatus by viewModel.scriptVoiceStatus.collectAsState()
    val currentlyPlayingClipId by viewModel.currentlyPlayingClipId.collectAsState()

    val selectedLanguage by viewModel.selectedLanguage.collectAsState()
    val selectedVoiceCategory by viewModel.selectedVoiceCategory.collectAsState()

    var apiKeyInput by remember(savedApiKey) { mutableStateOf(savedApiKey) }
    var isKeyVisible by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var showOnlyFavorites by remember { mutableStateOf(false) }
    var showVoicePickerDialog by remember { mutableStateOf(false) }
    var showClearAllConfirmDialog by remember { mutableStateOf(false) }
    var clipToDelete by remember { mutableStateOf<com.ritvyom.yashoraReelgenerator.data.voxeleven.SavedVoiceClip?>(null) }

    // ElevenLabs Filter States
    var selectedGenderFilterEleven by remember { mutableStateOf("All") }
    var showAdvancedSettings by remember { mutableStateOf(false) }

    // Local TTS Filter States
    var selectedLocalLang by remember(selectedLanguage) { mutableStateOf(if (selectedLanguage.isBlank()) "Hindi" else selectedLanguage) }
    var selectedLocalGenderFilter by remember(selectedVoiceCategory) { mutableStateOf(if (selectedVoiceCategory.isBlank()) "All" else selectedVoiceCategory) }

    var textPromptInput by remember { mutableStateOf("") }

    val languagesList = listOf("Hindi", "English", "Bengali", "Tamil", "Telugu", "Gujarati", "Marathi", "Punjabi", "Spanish", "French", "German", "Japanese", "Chinese", "Korean", "Arabic", "Urdu", "Russian", "Portuguese", "Turkish", "Indonesian", "Thai", "Vietnamese")

    val hasActiveVoxOverlay = showVoicePickerDialog || showClearAllConfirmDialog
    BackHandler(enabled = hasActiveVoxOverlay) {
        when {
            showVoicePickerDialog -> showVoicePickerDialog = false
            showClearAllConfirmDialog -> showClearAllConfirmDialog = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Voice Provider Settings".localize(appLanguageState), fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text("Android TTS & VoxEleven ElevenLabs Integration".localize(appLanguageState), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = Icons.AutoMirrored.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (selectedEngine == "voxeleven" && savedApiKey.isNotBlank()) {
                        IconButton(onClick = { viewModel.refreshVoxElevenVoices() }) {
                            Icon(imageVector = Icons.Default.Refresh, contentDescription = "Refresh Voices")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            // Section 1: Provider Engine Selection
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text("Select Voice Provider Engine".localize(appLanguageState), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(6.dp))

                        // Android TTS Option
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (selectedEngine == "android") MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else Color.Transparent)
                                .clickable { viewModel.setSelectedTtsEngine("android") }
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedEngine == "android",
                                onClick = { viewModel.setSelectedTtsEngine("android") },
                                modifier = Modifier.size(32.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Android Built-In TTS".localize(appLanguageState), fontWeight = FontWeight.SemiBold, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("Offline & Multi-language voices (Free / Zero Config)".localize(appLanguageState), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Icon(Icons.Default.RecordVoiceOver, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        // VoxEleven Option
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (selectedEngine == "voxeleven") MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else Color.Transparent)
                                .clickable { viewModel.setSelectedTtsEngine("voxeleven") }
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedEngine == "voxeleven",
                                onClick = { viewModel.setSelectedTtsEngine("voxeleven") },
                                modifier = Modifier.size(32.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = "VoxEleven (ElevenLabs)".localize(appLanguageState),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = MaterialTheme.colorScheme.primary
                                    ) {
                                        Text(
                                            text = "HD PRO",
                                            fontSize = 8.sp,
                                            color = MaterialTheme.colorScheme.onPrimary,
                                            fontWeight = FontWeight.Bold,
                                            softWrap = false,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = "Ultra-realistic AI voice synthesis with custom API key".localize(appLanguageState),
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }

            // Section 2: Android Built-In TTS Settings Card
            if (selectedEngine == "android") {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Translate, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Android Built-In TTS Settings".localize(appLanguageState), fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            }
                            Text(
                                "Uses your device's built-in Text-To-Speech engine. Fast, offline, and free without API limits.".localize(appLanguageState),
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 2.dp, bottom = 10.dp)
                            )

                            // Language Selector Row
                            Text("Speech Language:".localize(appLanguageState), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(4.dp))
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                items(languagesList) { lang ->
                                    val isSelected = selectedLocalLang.equals(lang, ignoreCase = true)
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = {
                                            selectedLocalLang = lang
                                            viewModel.selectedLanguage.value = lang
                                        },
                                        label = { Text(lang, fontSize = 11.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) }
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Active Engine Info & Voice Test Card
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = "$selectedLocalLang Speech Voice".localize(appLanguageState),
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Surface(
                                                shape = RoundedCornerShape(4.dp),
                                                color = MaterialTheme.colorScheme.primaryContainer
                                            ) {
                                                Text(
                                                    text = "System Default".localize(appLanguageState),
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = "Standard Android $selectedLocalLang offline speech synthesizer".localize(appLanguageState),
                                            fontSize = 10.5.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    val previewKey = "local_${selectedLocalLang}_Default_Default"
                                    val isPreviewPlaying = playingPreviewVoiceId == previewKey

                                    IconButton(
                                        onClick = {
                                            if (isPreviewPlaying) {
                                                viewModel.stopVoicePreview()
                                            } else {
                                                viewModel.playLocalVoicePreview(selectedLocalLang, "Default", "Default")
                                            }
                                        }
                                    ) {
                                        Icon(
                                            imageVector = if (isPreviewPlaying) Icons.Default.StopCircle else Icons.Default.VolumeUp,
                                            contentDescription = "Test Voice",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(28.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Section 3: VoxEleven API Key & Connection Panel
            if (selectedEngine == "voxeleven") {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.VpnKey, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("ElevenLabs API Key Security".localize(appLanguageState), fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            }
                            Text(
                                "Your API key is stored strictly on your local device with encrypted security and connects directly to ElevenLabs API to load original voices.".localize(appLanguageState),
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                            )

                            OutlinedTextField(
                                value = apiKeyInput,
                                onValueChange = { apiKeyInput = it },
                                label = { Text("ElevenLabs API Key (xi-api-key)".localize(appLanguageState)) },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                visualTransformation = if (isKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                trailingIcon = {
                                    IconButton(onClick = { isKeyVisible = !isKeyVisible }) {
                                        Icon(
                                            imageVector = if (isKeyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                            contentDescription = "Toggle Key Visibility"
                                        )
                                    }
                                },
                                shape = RoundedCornerShape(12.dp)
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = {
                                        viewModel.saveAndValidateElevenLabsKey(apiKeyInput.trim())
                                    },
                                    modifier = Modifier.weight(1f),
                                    enabled = !isCheckingApiKey && apiKeyInput.isNotBlank(),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    if (isCheckingApiKey) {
                                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Validating...".localize(appLanguageState), fontSize = 12.sp)
                                    } else {
                                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Save & Connect".localize(appLanguageState), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }

                                if (savedApiKey.isNotBlank()) {
                                    OutlinedButton(
                                        onClick = {
                                            viewModel.clearElevenLabsKey()
                                            apiKeyInput = ""
                                        },
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error)
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Clear".localize(appLanguageState), fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }

                            // Connection Status Indicator
                            if (!apiKeyStatusMessage.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(10.dp))
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (apiKeyStatusMessage?.contains("Success", ignoreCase = true) == true) Color(0xFFE8F5E9) else Color(0xFFFFEBEE),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = if (apiKeyStatusMessage?.contains("Success", ignoreCase = true) == true) Icons.Default.CheckCircle else Icons.Default.Error,
                                            contentDescription = null,
                                            tint = if (apiKeyStatusMessage?.contains("Success", ignoreCase = true) == true) Color(0xFF2E7D32) else Color(0xFFC62828),
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = apiKeyStatusMessage ?: "",
                                            fontSize = 11.sp,
                                            color = if (apiKeyStatusMessage?.contains("Success", ignoreCase = true) == true) Color(0xFF1B5E20) else Color(0xFFB71C1C),
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Character Usage Card (matching VoxEleven)
                if (savedApiKey.isNotBlank()) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.Analytics,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "Character Usage (Hindi & Multi-lang ready)".localize(appLanguageState),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp
                                        )
                                    }

                                    IconButton(
                                        onClick = { viewModel.refreshVoxElevenSubscription() },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        if (isFetchingSubscription) {
                                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                        } else {
                                            Icon(
                                                imageVector = Icons.Default.Refresh,
                                                contentDescription = "Refresh Usage",
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                val usedCount = voxElevenSubscription?.characterCount ?: 0
                                val limitCount = voxElevenSubscription?.characterLimit ?: 10000
                                val remainingCount = (limitCount - usedCount).coerceAtLeast(0)
                                val tierName = (voxElevenSubscription?.tier ?: "Free").uppercase()
                                val progress = if (limitCount > 0) (usedCount.toFloat() / limitCount.toFloat()).coerceIn(0f, 1f) else 0f

                                val formattedUsed = java.text.NumberFormat.getInstance().format(usedCount)
                                val formattedLimit = java.text.NumberFormat.getInstance().format(limitCount)
                                val formattedRemaining = java.text.NumberFormat.getInstance().format(remainingCount)

                                LinearProgressIndicator(
                                    progress = { progress },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(10.dp)
                                        .clip(RoundedCornerShape(5.dp)),
                                    color = if (progress > 0.9f) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                                )

                                Spacer(modifier = Modifier.height(10.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = "$formattedUsed Used".localize(appLanguageState),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "$formattedRemaining Remaining".localize(appLanguageState),
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }

                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = MaterialTheme.colorScheme.primaryContainer
                                    ) {
                                        Text(
                                            text = "$tierName Tier",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }

                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            text = "$formattedLimit Limit".localize(appLanguageState),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = "${(progress * 100).toInt()}% Used",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Section 4: Advanced Voice Settings Accordion
                item {
                    com.ritvyom.yashoraReelgenerator.presentation.components.ElevenLabsVoiceParametersPanel(
                        viewModel = viewModel,
                        isCollapsible = true,
                        initiallyExpanded = true
                    )
                }

                // Section 5: Selected Voice Profile Card & Voice Picker Trigger
                item {
                    val currentVoice = voicesList.find { it.voiceId == selectedVoiceId }
                    val displayName = currentVoice?.name ?: selectedVoiceName.ifBlank { "Rachel" }
                    val currentGender = currentVoice?.labels?.get("gender")?.replaceFirstChar { it.uppercase() } ?: "Female"
                    val currentAccent = currentVoice?.labels?.get("accent")?.replaceFirstChar { it.uppercase() } ?: "Natural"
                    val isPlayingSelected = playingPreviewVoiceId == (currentVoice?.voiceId ?: selectedVoiceId)

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { showVoicePickerDialog = true },
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                        ),
                        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            // Header Row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.RecordVoiceOver,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Selected Voice Profile".localize(appLanguageState),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }

                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.primary
                                ) {
                                    Text(
                                        text = "ACTIVE",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Voice Profile Details Row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Mic,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = displayName,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        modifier = Modifier.padding(top = 2.dp)
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = MaterialTheme.colorScheme.secondaryContainer
                                        ) {
                                            Text(
                                                text = currentGender,
                                                fontSize = 9.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                                            )
                                        }
                                        if (currentAccent.isNotBlank()) {
                                            Surface(
                                                shape = RoundedCornerShape(4.dp),
                                                color = MaterialTheme.colorScheme.surfaceVariant
                                            ) {
                                                Text(
                                                    text = currentAccent,
                                                    fontSize = 9.5.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                                                )
                                            }
                                        }
                                    }
                                }

                                // Audio Preview Button for Active Voice
                                IconButton(
                                    onClick = {
                                        if (isPlayingSelected) {
                                            viewModel.stopVoicePreview()
                                        } else if (currentVoice != null) {
                                            viewModel.playVoicePreview(currentVoice, context)
                                        } else {
                                            val fallbackVoice = com.ritvyom.yashoraReelgenerator.data.voxeleven.Voice(
                                                voiceId = selectedVoiceId,
                                                name = displayName,
                                                previewUrl = null
                                            )
                                            viewModel.playVoicePreview(fallbackVoice, context)
                                        }
                                    },
                                    modifier = Modifier
                                        .size(38.dp)
                                        .background(
                                            if (isPlayingSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                            CircleShape
                                        )
                                ) {
                                    Icon(
                                        imageVector = if (isPlayingSelected) Icons.Default.StopCircle else Icons.Default.VolumeUp,
                                        contentDescription = "Test Audio",
                                        tint = if (isPlayingSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Clickable Tab/Button to Browse All Voices
                            Button(
                                onClick = { showVoicePickerDialog = true },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(42.dp),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Tune,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (voicesList.isNotEmpty()) "Select / Change Voice (${voicesList.size} Voices)".localize(appLanguageState) else "Select / Change Voice".localize(appLanguageState),
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            // Section 6: Text Prompt & 1-Click Voice Generation
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Text Prompt".localize(appLanguageState), fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = Color(0xFF4CAF50).copy(alpha = 0.15f)
                                ) {
                                    Text(
                                        text = "✓ Saved".localize(appLanguageState),
                                        fontSize = 9.sp,
                                        color = Color(0xFF2E7D32),
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Text(
                                text = "${textPromptInput.length} chars",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = textPromptInput,
                            onValueChange = { textPromptInput = it },
                            placeholder = {
                                Text(
                                    "Apna text likhein (English, Hindi, etc.)...\nउदाहरण: नमस्ते! आपका स्वागत है।".localize(appLanguageState),
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 100.dp, max = 200.dp),
                            shape = RoundedCornerShape(12.dp)
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp, bottom = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            val words = if (textPromptInput.isBlank()) 0 else textPromptInput.trim().split("\\s+".toRegex()).size
                            Text("$words words", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("${textPromptInput.length} characters", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }

                        // Provider Info Note
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Mic,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (selectedEngine == "voxeleven")
                                        "ElevenLabs Multilingual v2 supports high-fidelity Hindi, English, and 28+ language synthesis perfectly.".localize(appLanguageState)
                                    else
                                        "Android Built-In & Edge TTS engine supports free multi-language speech generation in $selectedLanguage ($selectedVoiceCategory).".localize(appLanguageState),
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // 1-Click Generate Voice Clip Button
                        Button(
                            onClick = {
                                viewModel.generateVoiceFromScript(textPromptInput) { success, msg ->
                                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            enabled = !isGeneratingScriptVoice && textPromptInput.isNotBlank(),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            if (isGeneratingScriptVoice) {
                                LottiePulseLoader(
                                    sizeDp = 22.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = scriptVoiceStatus ?: "Generating Voice Clip...".localize(appLanguageState),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.GraphicEq,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Generate Voice Clip".localize(appLanguageState),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            // Section 7: Saved Audio Library
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp, bottom = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Saved Audio Library".localize(appLanguageState),
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Your offline voice generations stored permanently on device".localize(appLanguageState),
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (savedVoiceClips.isNotEmpty()) {
                        OutlinedButton(
                            onClick = { showClearAllConfirmDialog = true },
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            ),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                            modifier = Modifier.height(30.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "All Clear".localize(appLanguageState),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            if (savedVoiceClips.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f))
                    ) {
                        Column(
                            modifier = Modifier
                                .padding(24.dp)
                                .fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.MusicNote,
                                contentDescription = null,
                                modifier = Modifier.size(32.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "No saved voice clips yet".localize(appLanguageState),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Type your script in the text prompt above and click Generate Voice Clip to create audio.".localize(appLanguageState),
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }
            } else {
                items(savedVoiceClips) { clip ->
                    val isClipPlaying = currentlyPlayingClipId == clip.id

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isClipPlaying) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                        ),
                        border = BorderStroke(
                            width = if (isClipPlaying) 1.5.dp else 1.dp,
                            color = if (isClipPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Compact Circular Play/Pause button
                            IconButton(
                                onClick = {
                                    if (isClipPlaying) {
                                        viewModel.stopVoiceClipPlayback()
                                    } else {
                                        viewModel.playVoiceClip(clip)
                                    }
                                },
                                modifier = Modifier
                                    .size(34.dp)
                                    .background(
                                        color = if (isClipPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer,
                                        shape = CircleShape
                                    )
                            ) {
                                Icon(
                                    imageVector = if (isClipPlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
                                    contentDescription = "Play/Stop Clip",
                                    tint = if (isClipPlaying) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(10.dp))

                            // Compact Title + Details Column
                            Column(
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = clip.title,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 12.5.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    modifier = Modifier.padding(top = 1.dp)
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(3.dp),
                                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)
                                    ) {
                                        Text(
                                            text = clip.engineName,
                                            fontSize = 8.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                        )
                                    }
                                    val kbSize = clip.fileSizeBytes / 1024
                                    Text(
                                        text = "• ${kbSize} KB",
                                        fontSize = 9.5.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(4.dp))

                            // Action Icons Row
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                IconButton(
                                    onClick = {
                                        viewModel.setCustomMasterVoice(clip.filePath, clip.title)
                                        Toast.makeText(context, "Applied as Master Voice for all scenes in Video!".localize(appLanguageState), Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.size(30.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Use in Video",
                                        tint = Color(0xFF00E676),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }

                                IconButton(
                                    onClick = {
                                        viewModel.downloadVoiceClipToStorage(context, clip)
                                    },
                                    modifier = Modifier.size(30.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Download,
                                        contentDescription = "Download to Files",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }

                                IconButton(
                                    onClick = {
                                        try {
                                            val file = java.io.File(clip.filePath)
                                            val uri = androidx.core.content.FileProvider.getUriForFile(
                                                context,
                                                "${context.packageName}.fileprovider",
                                                file
                                            )
                                            val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                                type = "audio/*"
                                                putExtra(android.content.Intent.EXTRA_STREAM, uri)
                                                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                            }
                                            context.startActivity(android.content.Intent.createChooser(shareIntent, "Share Voice Clip"))
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "Error sharing file: ${e.message}", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    modifier = Modifier.size(30.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Share,
                                        contentDescription = "Share Clip",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }

                                IconButton(
                                    onClick = {
                                        clipToDelete = clip
                                    },
                                    modifier = Modifier.size(30.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Delete Clip",
                                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }

    // Single Clip Delete Confirmation Dialog
    clipToDelete?.let { clip ->
        AlertDialog(
            onDismissRequest = { clipToDelete = null },
            icon = {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = {
                Text(
                    text = "Delete Voice Clip?".localize(appLanguageState),
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to delete".localize(appLanguageState) + " '${clip.title}' " + "from saved audio clips? This action cannot be undone.".localize(appLanguageState),
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteVoiceClip(clip)
                        clipToDelete = null
                        Toast.makeText(context, "Deleted voice clip", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Delete".localize(appLanguageState))
                }
            },
            dismissButton = {
                TextButton(onClick = { clipToDelete = null }) {
                    Text("Cancel".localize(appLanguageState))
                }
            },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(20.dp)
        )
    }

    if (showClearAllConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showClearAllConfirmDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = {
                Text(
                    text = "Clear All Audio Clips?".localize(appLanguageState),
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to delete all saved voice clips permanently from device storage?".localize(appLanguageState),
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.clearAllSavedVoiceClips()
                        showClearAllConfirmDialog = false
                        Toast.makeText(context, "All voice clips cleared", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(text = "Clear All".localize(appLanguageState), color = MaterialTheme.colorScheme.onError)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearAllConfirmDialog = false }) {
                    Text(text = "Cancel".localize(appLanguageState))
                }
            }
        )
    }

    // ElevenLabs Voice Selection Dialog Modal
    if (showVoicePickerDialog) {
        Dialog(
            onDismissRequest = { showVoicePickerDialog = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.95f)
                    .fillMaxHeight(0.88f)
                    .clip(RoundedCornerShape(24.dp)),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    // Dialog Header
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
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.RecordVoiceOver,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Select Voice (${voicesList.size})".localize(appLanguageState),
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Tap to choose, preview, or favorite".localize(appLanguageState),
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        IconButton(
                            onClick = { showVoicePickerDialog = false },
                            modifier = Modifier
                                .size(32.dp)
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Search Field
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = {
                            Text("Search voice name, accent...".localize(appLanguageState), fontSize = 12.sp)
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(
                                        imageVector = Icons.Default.Clear,
                                        contentDescription = "Clear",
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f),
                            focusedIndicatorColor = MaterialTheme.colorScheme.primary,
                            unfocusedIndicatorColor = Color.Transparent
                        )
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Gender & Favorites Filter Chips - Clean Horizontal LazyRow
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val genderOptions = listOf("All", "Male", "Female")
                        items(genderOptions) { gender ->
                            val isSel = selectedGenderFilterEleven.equals(gender, ignoreCase = true) && !showOnlyFavorites
                            FilterChip(
                                selected = isSel,
                                onClick = {
                                    selectedGenderFilterEleven = gender
                                    showOnlyFavorites = false
                                },
                                label = {
                                    Text(
                                        gender,
                                        fontSize = 11.sp,
                                        fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                shape = RoundedCornerShape(8.dp)
                            )
                        }

                        item {
                            FilterChip(
                                selected = showOnlyFavorites,
                                onClick = { showOnlyFavorites = !showOnlyFavorites },
                                label = {
                                    Text(
                                        "Favorites (${favoritesState.size})".localize(appLanguageState),
                                        fontSize = 11.sp,
                                        fontWeight = if (showOnlyFavorites) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Star,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp),
                                        tint = if (showOnlyFavorites) Color(0xFFFFB300) else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                },
                                shape = RoundedCornerShape(8.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Filtered voices logic
                    val filteredVoices = voicesList.filter { voice ->
                        val matchesSearch = searchQuery.isBlank() ||
                                voice.name.contains(searchQuery, ignoreCase = true) ||
                                (voice.category?.contains(searchQuery, ignoreCase = true) == true) ||
                                (voice.description?.contains(searchQuery, ignoreCase = true) == true) ||
                                (voice.labels?.values?.any { it.contains(searchQuery, ignoreCase = true) } == true)

                        val voiceGender = voice.labels?.get("gender") ?: ""
                        val matchesGender = selectedGenderFilterEleven == "All" || voiceGender.equals(selectedGenderFilterEleven, ignoreCase = true)

                        val matchesFav = !showOnlyFavorites || favoritesState.contains(voice.voiceId)

                        matchesSearch && matchesGender && matchesFav
                    }

                    if (isFetchingVoices) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(modifier = Modifier.size(32.dp))
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    "Loading voices from ElevenLabs...".localize(appLanguageState),
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else if (filteredVoices.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Default.MicOff,
                                    contentDescription = null,
                                    modifier = Modifier.size(36.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "No voices found".localize(appLanguageState),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Try adjusting search or gender filter".localize(appLanguageState),
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(filteredVoices, key = { it.voiceId }) { voice ->
                                val isSelected = selectedVoiceId == voice.voiceId
                                val isFavorite = favoritesState.contains(voice.voiceId)
                                val isPlaying = playingPreviewVoiceId == voice.voiceId
                                val gender = voice.labels?.get("gender")?.replaceFirstChar { it.uppercase() } ?: ""
                                val accent = voice.labels?.get("accent")?.replaceFirstChar { it.uppercase() } ?: ""

                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .border(
                                            border = BorderStroke(
                                                width = if (isSelected) 2.dp else 1.dp,
                                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                                            ),
                                            shape = RoundedCornerShape(12.dp)
                                        )
                                        .clickable {
                                            viewModel.selectVoxElevenVoice(voice)
                                        },
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                                    )
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            RadioButton(
                                                selected = isSelected,
                                                onClick = { viewModel.selectVoxElevenVoice(voice) }
                                            )

                                            Spacer(modifier = Modifier.width(6.dp))

                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = voice.name,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 14.sp,
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )

                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                                    modifier = Modifier.padding(top = 2.dp)
                                                ) {
                                                    if (gender.isNotBlank()) {
                                                        Surface(
                                                            shape = RoundedCornerShape(4.dp),
                                                            color = MaterialTheme.colorScheme.secondaryContainer
                                                        ) {
                                                            Text(
                                                                text = gender,
                                                                fontSize = 9.5.sp,
                                                                fontWeight = FontWeight.SemiBold,
                                                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                                            )
                                                        }
                                                    }
                                                    if (accent.isNotBlank()) {
                                                        Text(
                                                            text = "• $accent",
                                                            fontSize = 10.sp,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
                                                    }
                                                }

                                                if (!voice.description.isNullOrBlank()) {
                                                    Text(
                                                        text = voice.description ?: "",
                                                        fontSize = 10.sp,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis,
                                                        modifier = Modifier.padding(top = 2.dp)
                                                    )
                                                }
                                            }

                                            // Favorite Star
                                            IconButton(
                                                onClick = { viewModel.toggleVoxElevenFavorite(voice.voiceId) },
                                                modifier = Modifier.size(36.dp)
                                            ) {
                                                Icon(
                                                    imageVector = if (isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                                                    contentDescription = "Favorite",
                                                    tint = if (isFavorite) Color(0xFFFFB300) else MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(6.dp))

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            // Audio Preview Button
                                            OutlinedButton(
                                                onClick = {
                                                    if (isPlaying) {
                                                        viewModel.stopVoicePreview()
                                                    } else {
                                                        viewModel.playVoicePreview(voice, context)
                                                    }
                                                },
                                                modifier = Modifier.height(32.dp),
                                                shape = RoundedCornerShape(16.dp),
                                                border = BorderStroke(
                                                    1.dp,
                                                    if (isPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                                                ),
                                                colors = ButtonDefaults.outlinedButtonColors(
                                                    containerColor = if (isPlaying) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                                                    contentColor = if (isPlaying) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.primary
                                                ),
                                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                                            ) {
                                                Icon(
                                                    imageVector = if (isPlaying) Icons.Default.StopCircle else Icons.Default.VolumeUp,
                                                    contentDescription = "Preview",
                                                    modifier = Modifier.size(14.dp)
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    text = if (isPlaying) "Playing...".localize(appLanguageState) else "Audio Preview".localize(appLanguageState),
                                                    fontSize = 10.5.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }

                                            if (isSelected) {
                                                Surface(
                                                    shape = RoundedCornerShape(8.dp),
                                                    color = MaterialTheme.colorScheme.primary
                                                ) {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.Check,
                                                            contentDescription = null,
                                                            tint = MaterialTheme.colorScheme.onPrimary,
                                                            modifier = Modifier.size(12.dp)
                                                        )
                                                        Spacer(modifier = Modifier.width(4.dp))
                                                        Text(
                                                            text = "Selected".localize(appLanguageState),
                                                            fontSize = 10.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = MaterialTheme.colorScheme.onPrimary
                                                        )
                                                    }
                                                }
                                            } else {
                                                Button(
                                                    onClick = {
                                                        viewModel.selectVoxElevenVoice(voice)
                                                        showVoicePickerDialog = false
                                                    },
                                                    modifier = Modifier.height(32.dp),
                                                    shape = RoundedCornerShape(8.dp),
                                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                                                ) {
                                                    Text(
                                                        text = "Select".localize(appLanguageState),
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Bottom Done Button
                    Button(
                        onClick = { showVoicePickerDialog = false },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = "Done".localize(appLanguageState),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
