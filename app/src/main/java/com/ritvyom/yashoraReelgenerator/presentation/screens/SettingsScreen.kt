package com.ritvyom.yashoraReelgenerator.presentation.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import android.util.Log
import android.widget.Toast
import com.ritvyom.yashoraReelgenerator.presentation.viewmodels.MainViewModel
import com.ritvyom.yashoraReelgenerator.presentation.utils.localize
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import com.ritvyom.yashoraReelgenerator.presentation.components.GoogleSignInButton
import com.ritvyom.yashoraReelgenerator.presentation.components.UserProfileDetailsDialog
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit,
    onOpenVoiceProvider: () -> Unit = {}
) {
    val appThemeState by viewModel.appTheme.collectAsState()
    val appLanguageState by viewModel.appLanguage.collectAsState()
    val videoLanguageState by viewModel.videoLanguage.collectAsState()
    val globalFirebaseUser by com.ritvyom.yashoraReelgenerator.data.remote.FirebaseCloudManager.currentUserState.collectAsState()

    val matchmakerState by viewModel.aiIntelligentMatchmaker.collectAsState()

    val context = LocalContext.current
    var cacheSizeText by remember {
        mutableStateOf(com.ritvyom.yashoraReelgenerator.presentation.utils.VideoFileManager.formatSizeBytes(
            com.ritvyom.yashoraReelgenerator.presentation.utils.VideoFileManager.getCacheSizeBytes(context)
        ))
    }
    var masterSizeText by remember {
        mutableStateOf(com.ritvyom.yashoraReelgenerator.presentation.utils.VideoFileManager.formatSizeBytes(
            com.ritvyom.yashoraReelgenerator.presentation.utils.VideoFileManager.getMasterVideosSizeBytes(context)
        ))
    }
    var showClearCacheDialog by remember { mutableStateOf(false) }
    var clearMastersOption by remember { mutableStateOf(false) }

    var activeDialogSection by remember { mutableStateOf<String?>(null) } // "Privacy", "Terms", "About", "Theme", "Lang", "VideoLang", "SherpaModels", "ApiKeys"
    var showProfileDialog by remember { mutableStateOf(false) }

    val themeList = listOf(
        "Light", "Dark", "System", "Cyberpunk", "AMOLED Black", "Neon",
        "Purple", "Blue", "Green", "Orange", "Red"
    )

    val supportedLanguages = com.ritvyom.yashoraReelgenerator.presentation.components.LanguageData.getLanguageCodes()

    val hasActiveSettingsOverlay = activeDialogSection != null || showProfileDialog || showClearCacheDialog
    BackHandler(enabled = hasActiveSettingsOverlay) {
        when {
            activeDialogSection != null -> activeDialogSection = null
            showProfileDialog -> showProfileDialog = false
            showClearCacheDialog -> showClearCacheDialog = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("App Settings".localize(appLanguageState), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = Icons.AutoMirrored.Default.ArrowBack, contentDescription = "Back".localize(appLanguageState))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
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

            item {
                Text(
                    text = "Profile & Account".localize(appLanguageState),
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            item {
                val user = globalFirebaseUser
                if (user != null) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showProfileDialog = true },
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(54.dp)
                                    .clip(CircleShape)
                                    .border(
                                        border = BorderStroke(
                                            width = 1.5.dp,
                                            brush = Brush.sweepGradient(
                                                colors = listOf(
                                                    Color(0xFF6200EE),
                                                    Color(0xFFFF0266),
                                                    Color(0xFF03DAC6),
                                                    Color(0xFF6200EE)
                                                )
                                            )
                                        ),
                                        shape = CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (user.photoUrl != null) {
                                    AsyncImage(
                                        model = user.photoUrl.toString(),
                                        contentDescription = "Profile Photo",
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clip(CircleShape),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(
                                                Brush.verticalGradient(
                                                    listOf(Color(0xFF311B92), Color(0xFF120338))
                                                )
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        val initial = (user.displayName ?: user.email ?: "?")
                                            .take(1)
                                            .uppercase()
                                        Text(
                                            text = initial,
                                            color = Color.White,
                                            fontSize = 20.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.width(16.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = user.displayName ?: "Creator".localize(appLanguageState),
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = user.email ?: "",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Tap to manage account".localize(appLanguageState),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }

                            Icon(
                                imageVector = Icons.AutoMirrored.Default.KeyboardArrowRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Sign in to backup and sync".localize(appLanguageState),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = "Sync your creations, templates and share videos with the creator community pool securely!".localize(appLanguageState),
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
                            )

                            GoogleSignInButton(
                                appLanguage = appLanguageState,
                                modifier = Modifier.fillMaxWidth(0.9f)
                            )
                        }
                    }
                }
            }

            item {
                Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            }

            item {
                Text(
                    text = "Preferences".localize(appLanguageState),
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            // Theme Item
            item {
                SettingsItem(
                    title = "App Theme".localize(appLanguageState),
                    subtitle = appThemeState.localize(appLanguageState),
                    icon = Icons.Default.Palette,
                    onClick = { activeDialogSection = "Theme" }
                )
            }

            // App language Item
            item {
                val currentLangItem = com.ritvyom.yashoraReelgenerator.presentation.components.LanguageData.ALL_LANGUAGES.find { it.code.equals(appLanguageState, ignoreCase = true) }
                val displayAppLang = if (currentLangItem != null && currentLangItem.nativeName != currentLangItem.displayName) {
                    "${currentLangItem.displayName} (${currentLangItem.nativeName})"
                } else {
                    appLanguageState.localize(appLanguageState)
                }
                SettingsItem(
                    title = "App Interface Language".localize(appLanguageState),
                    subtitle = displayAppLang,
                    icon = Icons.Default.Language,
                    onClick = { activeDialogSection = "Lang" }
                )
            }

            // Video default generation language Item
            item {
                val currentVideoLangItem = com.ritvyom.yashoraReelgenerator.presentation.components.LanguageData.ALL_LANGUAGES.find { it.code.equals(videoLanguageState, ignoreCase = true) }
                val displayVideoLang = if (currentVideoLangItem != null && currentVideoLangItem.nativeName != currentVideoLangItem.displayName) {
                    "${currentVideoLangItem.displayName} (${currentVideoLangItem.nativeName})"
                } else {
                    videoLanguageState.localize(appLanguageState)
                }
                SettingsItem(
                    title = "AI Video Generation Language".localize(appLanguageState),
                    subtitle = displayVideoLang,
                    icon = Icons.Default.Translate,
                    onClick = { activeDialogSection = "VideoLang" }
                )
            }

            // VoxEleven ElevenLabs Voice Engine Item
            item {
                val ttsEngine by viewModel.selectedTtsEngine.collectAsState()
                val voxVoiceName by viewModel.voxElevenVoiceName.collectAsState()
                SettingsItem(
                    title = "Voice Engine & ElevenLabs (VoxEleven)".localize(appLanguageState),
                    subtitle = if (ttsEngine == "voxeleven") "VoxEleven Active: $voxVoiceName" else "Android TTS Active (Tap to configure ElevenLabs)".localize(appLanguageState),
                    icon = Icons.Default.AutoAwesome,
                    onClick = { onOpenVoiceProvider() }
                )
            }



            // AI Intelligent Source Matchmaker
            item {
                SettingsSwitchItem(
                    title = "AI Smart Source Matchmaker".localize(appLanguageState),
                    subtitle = "Intelligently auto-corrects mismatched media sources based on script context".localize(appLanguageState),
                    icon = Icons.Default.Psychology,
                    checked = matchmakerState,
                    onCheckedChange = { viewModel.setAiIntelligentMatchmaker(it) }
                )
            }

            item {
                Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            }

            // AI Engine & API Connectivity
            item {
                Text(
                    text = "AI Engine & API Connectivity".localize(appLanguageState),
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            item {
                val geminiKey by viewModel.geminiApiKey.collectAsState()
                val providerConfigs by viewModel.unifiedAiRouter.providerConfigs.collectAsState()
                val userKeyCount = providerConfigs.values.count { it.hasValidKey && it.isUserProvided }
                val totalActiveCount = providerConfigs.values.count { it.hasValidKey }
                val isConnected = totalActiveCount > 0 || (geminiKey.isNotBlank() && geminiKey.length >= 16)
                val subtitle = if (userKeyCount > 0) {
                    "$userKeyCount Custom BYOK Key(s) Active • AI Chat Tab Unlocked".localize(appLanguageState)
                } else if (isConnected) {
                    "App Built-in AI Active • Tap to add custom key & unlock AI Chat tab".localize(appLanguageState)
                } else {
                    "Tap to configure Gemini, Groq, OpenAI, DeepSeek & unlock AI Chat".localize(appLanguageState)
                }
                SettingsItem(
                    title = "AI Engine & BYOK Providers Studio".localize(appLanguageState),
                    subtitle = subtitle,
                    icon = Icons.Default.SmartToy,
                    onClick = { activeDialogSection = "AiProvidersStudio" }
                )
            }

            item {
                Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            }

            item {
                Text(
                    text = "Storage & Local Cache".localize(appLanguageState),
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            item {
                SettingsItem(
                    title = "Clear Local Cache & Storage".localize(appLanguageState),
                    subtitle = "Manage & delete stored temporary render files ($cacheSizeText cached)".localize(appLanguageState),
                    icon = Icons.Default.CleaningServices,
                    onClick = { showClearCacheDialog = true }
                )
            }

            item {
                Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            }

            item {
                Text(
                    text = "Legal & About".localize(appLanguageState),
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }

            // Privacy Policy
            item {
                SettingsItem(
                    title = "Privacy Policy".localize(appLanguageState),
                    subtitle = "How we protect and manage your settings".localize(appLanguageState),
                    icon = Icons.Default.Security,
                    onClick = { activeDialogSection = "Privacy" }
                )
            }

            // Terms and Conditions
            item {
                SettingsItem(
                    title = "Terms & Conditions".localize(appLanguageState),
                    subtitle = "Agreement governing AI reel pipeline software usage".localize(appLanguageState),
                    icon = Icons.Default.Description,
                    onClick = { activeDialogSection = "Terms" }
                )
            }

            // About App
            item {
                SettingsItem(
                    title = "About Yashora Reel Generator".localize(appLanguageState),
                    subtitle = "App version, framework and technologies used".localize(appLanguageState),
                    icon = Icons.Default.Info,
                    onClick = { activeDialogSection = "About" }
                )
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "RITVYOM",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 3.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Version 1.0.4",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Normal,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
    }

    if (showProfileDialog) {
        globalFirebaseUser?.let { user ->
            UserProfileDetailsDialog(
                user = user,
                appLanguage = appLanguageState,
                onDismiss = { showProfileDialog = false }
            )
        }
    }

    if (showClearCacheDialog) {
        AlertDialog(
            onDismissRequest = { showClearCacheDialog = false },
            title = {
                Text(
                    text = "Clear Local Storage & Cache".localize(appLanguageState),
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Temporary Cache Size: $cacheSizeText\nInternal Offline Backup Videos: $masterSizeText".localize(appLanguageState),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Clearing temporary cache removes cached voice clips, generated thumbnails, and temporary audio render chunks. Exported videos saved in your Gallery will remain safe.".localize(appLanguageState),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { clearMastersOption = !clearMastersOption }
                            .padding(vertical = 4.dp)
                    ) {
                        Checkbox(
                            checked = clearMastersOption,
                            onCheckedChange = { clearMastersOption = it }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Also delete internal offline master backup videos".localize(appLanguageState),
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val freed = com.ritvyom.yashoraReelgenerator.presentation.utils.VideoFileManager.clearLocalCache(context, clearMastersOption)
                        val freedStr = com.ritvyom.yashoraReelgenerator.presentation.utils.VideoFileManager.formatSizeBytes(freed)
                        Toast.makeText(context, "Freed $freedStr of storage!".localize(appLanguageState), Toast.LENGTH_LONG).show()
                        cacheSizeText = com.ritvyom.yashoraReelgenerator.presentation.utils.VideoFileManager.formatSizeBytes(
                            com.ritvyom.yashoraReelgenerator.presentation.utils.VideoFileManager.getCacheSizeBytes(context)
                        )
                        masterSizeText = com.ritvyom.yashoraReelgenerator.presentation.utils.VideoFileManager.formatSizeBytes(
                            com.ritvyom.yashoraReelgenerator.presentation.utils.VideoFileManager.getMasterVideosSizeBytes(context)
                        )
                        showClearCacheDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Clear Storage".localize(appLanguageState))
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearCacheDialog = false }) {
                    Text("Cancel".localize(appLanguageState))
                }
            }
        )
    }

    // Modal Trigger Overlays
    when (activeDialogSection) {
        "AiProvidersStudio", "GeminiApi" -> {
            com.ritvyom.yashoraReelgenerator.presentation.components.AiProvidersStudioDialog(
                viewModel = viewModel,
                languageState = appLanguageState,
                onDismiss = { activeDialogSection = null }
            )
        }
        "Theme" -> {
            ThemeSelectionDialog(
                selectedValue = appThemeState,
                languageState = appLanguageState,
                onDismiss = { activeDialogSection = null },
                onSelect = {
                    viewModel.setAppTheme(it)
                    activeDialogSection = null
                }
            )
        }
        "Lang" -> {
            com.ritvyom.yashoraReelgenerator.presentation.components.SearchableLanguageDialog(
                selectedLanguage = appLanguageState,
                onLanguageSelected = {
                    viewModel.setAppLanguage(it)
                    activeDialogSection = null
                },
                onDismissRequest = { activeDialogSection = null },
                appLanguage = appLanguageState,
                availableLanguages = supportedLanguages
            )
        }
        "VideoLang" -> {
            com.ritvyom.yashoraReelgenerator.presentation.components.SearchableLanguageDialog(
                selectedLanguage = videoLanguageState,
                onLanguageSelected = {
                    viewModel.setVideoLanguage(it)
                    activeDialogSection = null
                },
                onDismissRequest = { activeDialogSection = null },
                appLanguage = appLanguageState,
                availableLanguages = supportedLanguages
            )
        }
        "Privacy" -> {
            com.ritvyom.yashoraReelgenerator.presentation.components.PrivacyPolicyDialog(
                languageState = appLanguageState,
                onDismiss = { activeDialogSection = null }
            )
        }
        "Terms" -> {
            LegalDetailsScreen(
                title = "Terms & Conditions".localize(appLanguageState),
                languageState = appLanguageState,
                paragraphs = listOf(
                    "Welcome to Yashora Reel Generator. By downloading, installing, or interacting with our mobile editing software, you agree to comply with the terms and covenants governing our high-performance rendering ecosystem.".localize(appLanguageState),
                    "1. License Scope: Users are granted a non-exclusive, non-transferable, and revocable license to utilize our built-in video editor, timeline tools, multi-dialect narrative generators, and rendering engine for personal or commercial video production.".localize(appLanguageState),
                    "2. Input Compliance: You agree not to submit, paste, or synthesize scripts that contain illegal, infringing, hateful, or prohibited content. All inputs processed through our dynamic editing engines must conform to standard fair-use laws.".localize(appLanguageState),
                    "3. Pipeline Security & Proprietary Rights: The application utilizes a highly optimized media synthesis engine. The database structures, rendering logic, timeline APIs, and network delivery interfaces are the intellectual property of Ritvyom (Suryadev Nishad). Any attempts to decompile, reverse-engineer, or intercept network resources are strictly prohibited.".localize(appLanguageState),
                    "4. Monetization Channels: High-fidelity rendering and advanced synthesis require viewing partner promotions and ads. Any attempt to exploit, bypass, block, or manipulate advertisement delivery mechanisms constitutes a breach of this software license.".localize(appLanguageState),
                    "5. Limitation of Liability: The software is provided 'as is' without warranty of any kind. Under no circumstances shall Ritvyom be liable for hardware performance bounds, local storage capacity constraints, or indirect project data losses.".localize(appLanguageState)
                ),
                onDismiss = { activeDialogSection = null }
            )
        }
        "About" -> {
            LegalDetailsScreen(
                title = "About Yashora Reel Generator".localize(appLanguageState),
                languageState = appLanguageState,
                paragraphs = listOf(
                    "Yashora Reel Generator is an AI-powered creative video suite developed and operated by Ritvyom / Suryadev Nishad to empower digital content creators globally.".localize(appLanguageState),
                    "Designed in modern Kotlin DSL and structured on the Jetpack Compose framework, this application follows standard MVVM and Clean Architecture pipelines to ensure a fluid, high-performance video editing experience.".localize(appLanguageState),
                    "Key Technical Achievements:".localize(appLanguageState),
                    "• High-performance relational database engine for instant project draft checkpoints".localize(appLanguageState),
                    "• Lightweight localized preference engines for seamless multi-theme state caching".localize(appLanguageState),
                    "• Real-time secure cloud integration for rapid script synthesis and narrative analysis".localize(appLanguageState),
                    "• Direct localized text-to-speech engine with dynamic speed and dialect calibration".localize(appLanguageState),
                    "• Asynchronous, multi-threaded timeline rendering and media composition pipelines".localize(appLanguageState),
                    "Contact: Ritvyom@gmail.com"
                ),
                onDismiss = { activeDialogSection = null }
            )
        }
    }
}

@Composable
fun SettingsItem(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        text = title,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = subtitle,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Icon(
                imageVector = Icons.AutoMirrored.Default.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
fun SettingsSwitchItem(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        text = title,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = subtitle,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                    checkedTrackColor = MaterialTheme.colorScheme.primary,
                    uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                    uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )
        }
    }
}

private data class ThemeOptionItem(
    val name: String,
    val description: String,
    val previewBg: Color,
    val previewPrimary: Color
)

@Composable
fun ThemeSelectionDialog(
    selectedValue: String,
    languageState: String,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit
) {
    val themeOptions = listOf(
        ThemeOptionItem("Light", "Clean sapphire blue & pure white theme".localize(languageState), Color(0xFFFFFFFF), Color(0xFF0F52BA)),
        ThemeOptionItem("Dark", "Classic neutral charcoal dark theme".localize(languageState), Color(0xFF121212), Color(0xFF38BDF8)),
        ThemeOptionItem("System", "Adapts automatically to device light/dark mode".localize(languageState), Color(0xFF27272A), Color(0xFF90CAF9)),
        ThemeOptionItem("Blue", "Deep oceanic navy & electric blue theme".localize(languageState), Color(0xFF0A192F), Color(0xFF3B82F6)),
        ThemeOptionItem("AMOLED Black", "Pure #000000 OLED pitch-black theme".localize(languageState), Color(0xFF000000), Color(0xFF38BDF8)),
        ThemeOptionItem("Cyberpunk", "Hot pink & neon cyan cyberpunk theme".localize(languageState), Color(0xFF120824), Color(0xFFFF007F)),
        ThemeOptionItem("Neon", "Radioactive neon green & teal matrix theme".localize(languageState), Color(0xFF0A0F0A), Color(0xFF39FF14)),
        ThemeOptionItem("Purple", "Royal velvet purple & electric magenta theme".localize(languageState), Color(0xFF120024), Color(0xFFE040FB)),
        ThemeOptionItem("Green", "Deep emerald forest & mint green theme".localize(languageState), Color(0xFF062319), Color(0xFF4ADE80)),
        ThemeOptionItem("Orange", "Sunset ember & warm amber theme".localize(languageState), Color(0xFF1E0F0A), Color(0xFFFB923C)),
        ThemeOptionItem("Red", "Crimson ruby & dark rose theme".localize(languageState), Color(0xFF1C0A0A), Color(0xFFF87171))
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Select App Theme".localize(languageState), fontWeight = FontWeight.Bold, fontSize = 18.sp)
        },
        text = {
            Box(modifier = Modifier.heightIn(max = 380.dp)) {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(themeOptions) { option ->
                        val isSelected = option.name == selectedValue
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onSelect(option.name) },
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                            border = BorderStroke(
                                1.dp,
                                if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Theme Color Preview Box
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(option.previewBg)
                                        .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), RoundedCornerShape(8.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(16.dp)
                                            .clip(CircleShape)
                                            .background(option.previewPrimary)
                                    )
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = option.name.localize(languageState),
                                        fontSize = 14.sp,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = option.description,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                if (isSelected) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Selected".localize(languageState),
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel".localize(languageState))
            }
        }
    )
}

@Composable
fun SelectionDialog(
    title: String,
    items: List<String>,
    selectedValue: String,
    languageState: String,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val filteredItems = remember(searchQuery, items) {
        if (searchQuery.isBlank()) items
        else items.filter { code ->
            val langItem = com.ritvyom.yashoraReelgenerator.presentation.components.LanguageData.ALL_LANGUAGES.find { it.code.equals(code, ignoreCase = true) }
            code.contains(searchQuery, ignoreCase = true) ||
            code.localize(languageState).contains(searchQuery, ignoreCase = true) ||
            (langItem != null && (langItem.displayName.contains(searchQuery, ignoreCase = true) || langItem.nativeName.contains(searchQuery, ignoreCase = true)))
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold, fontSize = 18.sp) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (items.size > 5) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                        placeholder = { Text("Search...".localize(languageState), fontSize = 13.sp) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = null)
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )
                }

                Box(modifier = Modifier.heightIn(max = 280.dp)) {
                    if (filteredItems.isEmpty()) {
                        Text(
                            "No results found".localize(languageState),
                            modifier = Modifier.padding(16.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(filteredItems) { item ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { onSelect(item) }
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    val langItem = com.ritvyom.yashoraReelgenerator.presentation.components.LanguageData.ALL_LANGUAGES.find { it.code.equals(item, ignoreCase = true) }
                                    val itemLabel = if (langItem != null && langItem.nativeName != langItem.displayName) {
                                        "${langItem.displayName} (${langItem.nativeName})"
                                    } else {
                                        item.localize(languageState)
                                    }
                                    Text(
                                        text = itemLabel,
                                        fontSize = 14.sp,
                                        color = if (item.equals(selectedValue, ignoreCase = true)) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                        fontWeight = if (item.equals(selectedValue, ignoreCase = true)) FontWeight.Bold else FontWeight.Normal
                                    )
                                    if (item.equals(selectedValue, ignoreCase = true)) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = "Selected".localize(languageState),
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel".localize(languageState))
            }
        }
    )
}

@Composable
fun LegalDetailsScreen(
    title: String,
    paragraphs: List<String>,
    languageState: String = "English",
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = MaterialTheme.colorScheme.primary)
                IconButton(onClick = onDismiss) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Close".localize(languageState))
                }
            }
        },
        text = {
            LazyColumn(
                modifier = Modifier.heightIn(max = 400.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(paragraphs) { para ->
                    Text(
                        text = para,
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Done".localize(languageState))
            }
        }
    )
}

@Composable
fun GeminiApiConfigDialog(
    currentApiKey: String,
    languageState: String,
    apiValidationLoading: Boolean,
    apiValidationResult: Pair<Boolean, String>?,
    onTestConnection: (String) -> Unit,
    onSaveKey: (String) -> Unit,
    onClearKey: () -> Unit,
    onDismiss: () -> Unit
) {
    var keyInput by remember(currentApiKey) { mutableStateOf(currentApiKey) }
    val isHindi = languageState.contains("hi", ignoreCase = true) || languageState.contains("hindi", ignoreCase = true)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.SmartToy,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isHindi) "AI व API कनेक्टिविटी" else "AI & API Connection",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Close")
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Failover Architecture Status Card
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isHindi)
                                "ऑटोमैटिक फेलओवर सक्रिय: यदि REST API में कोई समस्या आती है, तो ऐप तुरंत Vertex AI और बैकअप इंजन पर शिफ्ट हो जाता है ताकि आपका काम न रुके।"
                            else
                                "Zero-Interruption Failover: If the REST API encounters any restriction or error, the app automatically fails over to Vertex AI & the resilient backup engine so your work never stops.",
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }

                Text(
                    text = if (isHindi) "Google Gemini API Key (वैकल्पिक / Optional):" else "Gemini API Key (Optional):",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                OutlinedTextField(
                    value = keyInput,
                    onValueChange = { keyInput = it.trim() },
                    placeholder = {
                        Text(
                            "AIzaSy...",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    trailingIcon = {
                        if (keyInput.isNotEmpty()) {
                            IconButton(onClick = { keyInput = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear")
                            }
                        }
                    }
                )

                // Validation Status Feedback
                if (apiValidationLoading) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Text(
                            text = if (isHindi) "कनेक्शन की जांच की जा रही है..." else "Testing API connectivity...",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                } else if (apiValidationResult != null) {
                    val (isSuccess, message) = apiValidationResult
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = if (isSuccess) Icons.Default.CheckCircle else Icons.Default.Error,
                            contentDescription = null,
                            tint = if (isSuccess) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = message,
                            fontSize = 12.sp,
                            color = if (isSuccess) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error,
                            lineHeight = 15.sp
                        )
                    }
                }

                // Test Connection Button
                OutlinedButton(
                    onClick = { onTestConnection(keyInput) },
                    enabled = keyInput.isNotBlank() && !apiValidationLoading,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isHindi) "कनेक्शन टेस्ट करें (Test Connection)" else "Test Connection",
                        fontSize = 13.sp
                    )
                }

                Text(
                    text = if (isHindi)
                        "आप aistudio.google.com से मुफ़्त Gemini API Key प्राप्त कर सकते हैं।"
                    else
                        "You can obtain a free Gemini API key from Google AI Studio (aistudio.google.com).",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSaveKey(keyInput)
                    onDismiss()
                },
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(if (isHindi) "सहेजें (Save)" else "Save")
            }
        },
        dismissButton = {
            if (currentApiKey.isNotEmpty()) {
                TextButton(
                    onClick = {
                        onClearKey()
                        keyInput = ""
                    }
                ) {
                    Text(
                        text = if (isHindi) "हटाएं (Remove)" else "Remove",
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    )
}



