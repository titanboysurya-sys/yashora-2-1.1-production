package com.ritvyom.yashoraReelgenerator.presentation.screens

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
    onBack: () -> Unit
) {
    val appThemeState by viewModel.appTheme.collectAsState()
    val appLanguageState by viewModel.appLanguage.collectAsState()
    val videoLanguageState by viewModel.videoLanguage.collectAsState()
    val globalFirebaseUser by com.ritvyom.yashoraReelgenerator.data.remote.FirebaseCloudManager.currentUserState.collectAsState()

    val matchmakerState by viewModel.aiIntelligentMatchmaker.collectAsState()

    var activeDialogSection by remember { mutableStateOf<String?>(null) } // "Privacy", "Terms", "About", "Theme", "Lang", "VideoLang", "SherpaModels", "ApiKeys"
    var showProfileDialog by remember { mutableStateOf(false) }

    val themeList = listOf(
        "Bento Grid", "Light", "Dark", "System", "Cyberpunk", "AMOLED Black", "Neon",
        "Purple", "Blue", "Green", "Orange", "Red"
    )

    val supportedLanguages = listOf(
        "English", "Hindi", "Arabic", "Urdu", "Bengali", "Tamil",
        "Telugu", "Japanese", "Chinese", "Korean", "German",
        "French", "Spanish", "Portuguese", "Russian", "Turkish"
    )

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
                SettingsItem(
                    title = "App Interface Language".localize(appLanguageState),
                    subtitle = appLanguageState.localize(appLanguageState),
                    icon = Icons.Default.Language,
                    onClick = { activeDialogSection = "Lang" }
                )
            }

            // Video default generation language Item
            item {
                SettingsItem(
                    title = "AI Video Generation Language".localize(appLanguageState),
                    subtitle = videoLanguageState.localize(appLanguageState),
                    icon = Icons.Default.Translate,
                    onClick = { activeDialogSection = "VideoLang" }
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
                        text = "YASHORA TECHNOLOGIES".localize(appLanguageState),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 2.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Version 1.0.4 (Production Ready)".localize(appLanguageState),
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
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

    // Modal Trigger Overlays
    when (activeDialogSection) {
        "Theme" -> {
            SelectionDialog(
                title = "Select Theme Selection".localize(appLanguageState),
                items = themeList,
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
            SelectionDialog(
                title = "App Interface Language".localize(appLanguageState),
                items = supportedLanguages,
                selectedValue = appLanguageState,
                languageState = appLanguageState,
                onDismiss = { activeDialogSection = null },
                onSelect = {
                    viewModel.setAppLanguage(it)
                    activeDialogSection = null
                }
            )
        }
        "VideoLang" -> {
            SelectionDialog(
                title = "AI Video Generation Language".localize(appLanguageState),
                items = supportedLanguages,
                selectedValue = videoLanguageState,
                languageState = appLanguageState,
                onDismiss = { activeDialogSection = null },
                onSelect = {
                    viewModel.setVideoLanguage(it)
                    activeDialogSection = null
                }
            )
        }
        "Privacy" -> {
            LegalDetailsScreen(
                title = "Privacy Policy".localize(appLanguageState),
                paragraphs = listOf(
                    "At Yashora Technologies, we are deeply committed to respecting and protecting the privacy of our global users. This privacy policy outlines our data preservation, security, and rendering pipeline protocols.",
                    "1. Local Storage Sandboxing: All projects, video drafts, sub-scenes, subtitles, audio characteristics, and customization preferences are persisted locally on your device using secure local storage engines. We do not transmit your projects or draft assets to external databases.",
                    "2. Secure Synthesis Processing: Script generation and semantic storyboard breakdowns utilize advanced, secure cloud-based linguistic architectures. Text segments are transmitted in-transit purely to construct narrative structures and visual prompts. Your data is processed dynamically and is never permanently retained or used for model training.",
                    "3. Media Rendering Integrity: The visual and auditory elements integrated during the compilation process are fetched via secure, high-speed, encrypted content distribution networks. Our media pipeline strictly abstracts and protects internal source mechanics to maintain service reliability and secure delivery.",
                    "4. AdMob Networks & Metrics: We integrate standard industry monetization networks to sustain our rendering infrastructure. These certified frameworks process non-sensitive, pseudo-anonymous device identifiers and advertising parameters. We do not access or collect any private contacts, messages, or location profiles.",
                    "5. Local Output Rights: Exported high-definition MP4 reels are generated directly inside your local storage directories. Total intellectual ownership of the rendered clips belongs solely to the user.",
                    "Contact our team at support@yashoratechnologies.com for further inquiries."
                ),
                onDismiss = { activeDialogSection = null }
            )
        }
        "Terms" -> {
            LegalDetailsScreen(
                title = "Terms & Conditions".localize(appLanguageState),
                paragraphs = listOf(
                    "Welcome to Yashora Reel Generator. By downloading, installing, or interacting with our mobile editing software, you agree to comply with the terms and covenants governing our high-performance rendering ecosystem.",
                    "1. License Scope: Users are granted a non-exclusive, non-transferable, and revocable license to utilize our built-in video editor, timeline tools, multi-dialect narrative generators, and rendering engine for personal or commercial video production.",
                    "2. Input Compliance: You agree not to submit, paste, or synthesize scripts that contain illegal, infringing, hateful, or prohibited content. All inputs processed through our dynamic editing engines must conform to standard fair-use laws.",
                    "3. Pipeline Security & Proprietary Rights: The application utilizes a highly optimized media synthesis engine. The database structures, rendering logic, timeline APIs, and network delivery interfaces are the exclusive intellectual property of Yashora Technologies. Any attempts to decompile, reverse-engineer, or intercept network resources are strictly prohibited.",
                    "4. Monetization Channels: High-fidelity rendering and advanced synthesis require viewing partner promotions and ads. Any attempt to exploit, bypass, block, or manipulate advertisement delivery mechanisms constitutes a breach of this software license.",
                    "5. Limitation of Liability: The software is provided 'as is' without warranty of any kind. Under no circumstances shall Yashora Technologies be liable for hardware performance bounds, local storage capacity constraints, or indirect project data losses."
                ),
                onDismiss = { activeDialogSection = null }
            )
        }
        "About" -> {
            LegalDetailsScreen(
                title = "About Yashora Reel Generator".localize(appLanguageState),
                paragraphs = listOf(
                    "Yashora Reel Generator is a professional, desktop-class mobile cinematography suite conceptualized and built by Yashora Technologies to empower digital content creators globally.",
                    "Designed in modern Kotlin DSL and structured on the Jetpack Compose framework, this application follows standard MVVM and Clean Architecture pipelines to ensure a fluid, high-performance video editing experience.",
                    "Key Technical Achievements:",
                    "• High-performance relational database engine for instant project draft checkpoints",
                    "• Lightweight localized preference engines for seamless multi-theme state caching",
                    "• Real-time secure cloud integration for rapid script synthesis and narrative analysis",
                    "• Direct localized text-to-speech engine with dynamic speed and dialect calibration",
                    "• Asynchronous, multi-threaded timeline rendering and media composition pipelines",
                    "Our mission is to democratize high-fidelity mobile digital cinematography completely."
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

@Composable
fun SelectionDialog(
    title: String,
    items: List<String>,
    selectedValue: String,
    languageState: String,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold, fontSize = 18.sp) },
        text = {
            Box(modifier = Modifier.heightIn(max = 250.dp)) {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(items) { item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onSelect(item) }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = item.localize(languageState),
                                fontSize = 14.sp,
                                color = if (item == selectedValue) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                fontWeight = if (item == selectedValue) FontWeight.Bold else FontWeight.Normal
                            )
                            if (item == selectedValue) {
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
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Close")
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
                Text("Close")
            }
        }
    )
}


