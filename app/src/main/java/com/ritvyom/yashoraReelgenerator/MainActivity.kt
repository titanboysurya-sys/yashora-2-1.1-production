package com.ritvyom.yashoraReelgenerator

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.draw.clip
import com.ritvyom.yashoraReelgenerator.presentation.screens.*
import com.ritvyom.yashoraReelgenerator.presentation.components.LottieVideoSynthesisAnimation
import com.ritvyom.yashoraReelgenerator.presentation.components.LottieApiProcessingAnimation
import com.ritvyom.yashoraReelgenerator.presentation.components.LottieLinearProgressBar
import com.ritvyom.yashoraReelgenerator.presentation.viewmodels.MainViewModel
import com.ritvyom.yashoraReelgenerator.presentation.utils.localize
import com.ritvyom.yashoraReelgenerator.ui.theme.YashoraTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Audio Manager initialization
        com.ritvyom.yashoraReelgenerator.presentation.utils.AudioManager.initialize(this)

        setContent {
            val appThemeState by viewModel.appTheme.collectAsState()
            val appLanguageState by viewModel.appLanguage.collectAsState()

            val isExporting by viewModel.isExporting.collectAsState()
            val isExportingMinimized by viewModel.isExportingMinimized.collectAsState()
            val exportProgress by viewModel.exportProgress.collectAsState()
            val exportStatus by viewModel.exportStatus.collectAsState()
            val exportEtaFormatted by viewModel.exportEtaFormatted.collectAsState()

            val isImportingTemplate by viewModel.isImportingTemplate.collectAsState()
            val isImportingTemplateMinimized by viewModel.isImportingTemplateMinimized.collectAsState()
            val importTemplateProgress by viewModel.importTemplateProgress.collectAsState()
            val importTemplateStatus by viewModel.importTemplateStatus.collectAsState()
            val importingTemplateTitle by viewModel.importingTemplateTitle.collectAsState()

            LaunchedEffect(Unit) {
                viewModel.templateImportCompleteEvent.collect { createdProject ->
                    Toast.makeText(this@MainActivity, "Template loaded into Studio!".localize(appLanguageState), Toast.LENGTH_SHORT).show()
                }
            }

            val activity = androidx.activity.compose.LocalActivity.current
            DisposableEffect(isExporting) {
                if (isExporting) {
                    activity?.window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                } else {
                    activity?.window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
                onDispose {
                    activity?.window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
            }

            YashoraTheme(themeName = appThemeState) {
                // Reactive Backstack Navigation Controller
                var currentScreen by remember { mutableStateOf("Splash") }
                
                // Track backstack for nested routes
                val backstack = remember { mutableStateListOf("Splash") }
                val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
                val keyboardController = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current

                fun navigateTo(screen: String) {
                    focusManager.clearFocus()
                    keyboardController?.hide()
                    if (backstack.lastOrNull() == screen) return
                    backstack.add(screen)
                    currentScreen = screen
                }

                fun navigateBack() {
                    focusManager.clearFocus()
                    keyboardController?.hide()
                    if (backstack.size > 1) {
                        backstack.removeAt(backstack.lastIndex)
                        currentScreen = backstack.last()
                    }
                }

                // Intercept back presses on all screens deeper than Splash or Home
                BackHandler(enabled = currentScreen != "Splash" && currentScreen != "Home") {
                    navigateBack()
                }

                // Intercept back presses on Home screen to return to Home tab if on another tab
                val bottomTabSelected by viewModel.bottomTabSelected.collectAsState()
                BackHandler(enabled = currentScreen == "Home" && bottomTabSelected != "Home") {
                    if (!viewModel.popTabHistory()) {
                        viewModel.changeTab("Home")
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .windowInsetsPadding(WindowInsets.systemBars)
                        .imePadding()
                ) {
                    Crossfade(targetState = currentScreen, label = "ScreenTransition") { screen ->
                        when (screen) {
                            "Splash" -> {
                                SplashPage(
                                    onSplashFinished = {
                                        currentScreen = "Home"
                                        backstack.clear()
                                        backstack.add("Home")
                                    }
                                )
                            }
                            "Home" -> {
                                HomeScreen(
                                    viewModel = viewModel,
                                    onCreateNewProject = {
                                        viewModel.clearActiveProject()
                                        navigateTo("ScriptConfig")
                                    },
                                    onOpenProject = { project ->
                                        viewModel.loadProject(project)
                                        navigateTo("Editor")
                                    },
                                    onOpenSettings = {
                                        navigateTo("Settings")
                                    },
                                    onDirectEditProjectCreated = {
                                        navigateTo("Editor")
                                    },
                                    onOpenScriptGenerator = {
                                        navigateTo("ScriptGenerator")
                                    },
                                    onOpenVoiceProvider = {
                                        navigateTo("VoxElevenVoiceProvider")
                                    },
                                    onOpenManualStudio = {
                                        navigateTo("ManualStudio")
                                    }
                                )
                            }
                            "ManualStudio" -> {
                                ManualStudioScreen(
                                    viewModel = viewModel,
                                    appLanguageState = appLanguageState,
                                    onBack = {
                                        navigateBack()
                                    },
                                    onOpenEditor = { project ->
                                        navigateTo("Editor")
                                    },
                                    onNavigateToGeneration = {
                                        navigateTo("Generation")
                                    }
                                )
                            }
                            "ScriptGenerator" -> {
                                ScriptGeneratorScreen(
                                    appLanguageState = appLanguageState,
                                    onBack = {
                                        navigateBack()
                                    },
                                    onSendToVideoPipeline = { scriptText, topic ->
                                        viewModel.scriptText.value = scriptText
                                        viewModel.topicContext.value = topic
                                        navigateTo("ScriptConfig")
                                    }
                                )
                            }
                            "ScriptConfig" -> {
                                ScriptAndConfigScreen(
                                    viewModel = viewModel,
                                    onBack = {
                                        navigateBack()
                                    },
                                    onNavigateToGeneration = {
                                        navigateTo("Generation")
                                    },
                                    onOpenScriptGenerator = {
                                        navigateTo("ScriptGenerator")
                                    },
                                    onOpenVoiceProvider = {
                                        navigateTo("VoxElevenVoiceProvider")
                                    }
                                )
                            }
                            "Generation" -> {
                                GenerationPipelineScreen(
                                    viewModel = viewModel,
                                    onComplete = {
                                        // Remove "Generation" and "ScriptConfig" from stack to return to Home on Editor backpress
                                        backstack.clear()
                                        backstack.add("Home")
                                        navigateTo("Editor")
                                    },
                                    onCancel = {
                                        viewModel.cancelGeneration()
                                        navigateBack()
                                    }
                                )
                            }
                            "Editor" -> {
                                VideoEditorScreen(
                                    viewModel = viewModel,
                                    onBack = {
                                        navigateBack()
                                    },
                                    onNavigateToConfig = {
                                        navigateTo("ScriptConfig")
                                    },
                                    onNavigateToPreview = {
                                        navigateTo("VideoPlayerPreview")
                                    },
                                    onExportFinished = {
                                        // Trigger small TTS confirmation
                                        viewModel.speakText("Video export complete. File saved to downloads directory.".localize(appLanguageState))
                                        currentScreen = "Home"
                                        backstack.clear()
                                        backstack.add("Home")
                                    }
                                )
                            }
                            "VideoPlayerPreview" -> {
                                VideoPlayerPreviewScreen(
                                    viewModel = viewModel,
                                    appLanguageState = appLanguageState,
                                    onBack = {
                                        navigateBack()
                                    },
                                    onProceedToExport = {
                                        viewModel.exportVideo(
                                            onExportComplete = {
                                                viewModel.speakText("Video export complete. File saved to downloads directory.".localize(appLanguageState))
                                                currentScreen = "Home"
                                                backstack.clear()
                                                backstack.add("Home")
                                            }
                                        )
                                    }
                                )
                            }
                            "Settings" -> {
                                SettingsScreen(
                                    viewModel = viewModel,
                                    onBack = {
                                        navigateBack()
                                    },
                                    onOpenVoiceProvider = {
                                        navigateTo("VoxElevenVoiceProvider")
                                    }
                                )
                            }
                            "VoxElevenVoiceProvider" -> {
                                VoxElevenVoiceProviderScreen(
                                    viewModel = viewModel,
                                    onBack = {
                                        navigateBack()
                                    }
                                )
                            }
                        }
                    }

                    var showCancelExportConfirmDialog by remember { mutableStateOf(false) }

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

                    // Floating background progress overlay bar
                    if (isExporting && isExportingMinimized) {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = Color(0xFF0F0922).copy(alpha = 0.95f)
                            ),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                                .padding(bottom = 24.dp)
                                .navigationBarsPadding()
                                .border(
                                    width = 1.5.dp,
                                    color = Color(0xFF39FF14).copy(alpha = 0.5f),
                                    shape = RoundedCornerShape(16.dp)
                                )
                                .clickable {
                                    viewModel.isExportingMinimized.value = false
                                    if (currentScreen != "Editor") {
                                        navigateTo("Editor")
                                    }
                                }
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        LottieVideoSynthesisAnimation(
                                            sizeDp = 34.dp,
                                            speed = 1.3f
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column {
                                            Text(
                                                text = "Saving Video in Background...".localize(appLanguageState),
                                                color = Color.White,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp
                                            )
                                            val etaText = exportEtaFormatted?.let { " • " + it.localize(appLanguageState) } ?: ""
                                            Text(
                                                text = "${(exportProgress * 100).toInt()}%$etaText • " + exportStatus.localize(appLanguageState),
                                                color = Color(0xFF00F0FF),
                                                fontSize = 11.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                    
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // Maximize button
                                        IconButton(
                                            onClick = {
                                                viewModel.isExportingMinimized.value = false
                                                if (currentScreen != "Editor") {
                                                    navigateTo("Editor")
                                                }
                                            },
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Fullscreen,
                                                contentDescription = "Maximize",
                                                tint = Color(0xFF00F0FF)
                                            )
                                        }
                                        
                                        // Cancel button
                                        IconButton(
                                            onClick = { showCancelExportConfirmDialog = true },
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "Cancel",
                                                tint = Color(0xFFFF007F)
                                            )
                                        }
                                    }
                                }
                                
                                Spacer(modifier = Modifier.height(10.dp))
                                
                                // Lottie-enhanced linear progress bar
                                LottieLinearProgressBar(
                                    progress = exportProgress,
                                    height = 5.dp,
                                    brushColors = listOf(Color(0xFF39FF14), Color(0xFF00F0FF))
                                )
                            }
                        }
                    }

                    // Floating background template downloading overlay bar
                    if (isImportingTemplate && isImportingTemplateMinimized) {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = Color(0xFF130926).copy(alpha = 0.96f)
                            ),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                                .padding(bottom = if (isExporting && isExportingMinimized) 105.dp else 24.dp)
                                .navigationBarsPadding()
                                .border(
                                    width = 1.5.dp,
                                    color = Color(0xFF9D00FF).copy(alpha = 0.6f),
                                    shape = RoundedCornerShape(16.dp)
                                )
                                .clickable {
                                    viewModel.isImportingTemplateMinimized.value = false
                                }
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        LottieApiProcessingAnimation(
                                            sizeDp = 34.dp,
                                            speed = 1.2f
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column {
                                            Text(
                                                text = if (importingTemplateTitle.isNotBlank()) importingTemplateTitle else "Downloading Template...".localize(appLanguageState),
                                                color = Color.White,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = "${(importTemplateProgress * 100).toInt()}% • " + importTemplateStatus.localize(appLanguageState),
                                                color = Color(0xFF00F0FF),
                                                fontSize = 11.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                    
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // Maximize button
                                        IconButton(
                                            onClick = {
                                                viewModel.isImportingTemplateMinimized.value = false
                                            },
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Fullscreen,
                                                contentDescription = "Maximize",
                                                tint = Color(0xFF00F0FF)
                                            )
                                        }
                                        
                                        // Cancel button
                                        IconButton(
                                            onClick = {
                                                viewModel.cancelImportTemplate()
                                                Toast.makeText(this@MainActivity, "Template download cancelled".localize(appLanguageState), Toast.LENGTH_SHORT).show()
                                            },
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "Cancel",
                                                tint = Color(0xFFFF007F)
                                            )
                                        }
                                    }
                                }
                                
                                Spacer(modifier = Modifier.height(10.dp))
                                
                                // Lottie-enhanced linear progress bar
                                LottieLinearProgressBar(
                                    progress = importTemplateProgress,
                                    height = 5.dp,
                                    brushColors = listOf(Color(0xFF9D00FF), Color(0xFF00F0FF))
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
