package com.ritvyom.yashoraReelgenerator.presentation.components.editor

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ritvyom.yashoraReelgenerator.domain.models.Scene
import com.ritvyom.yashoraReelgenerator.presentation.utils.AudioManager
import com.ritvyom.yashoraReelgenerator.presentation.utils.EffectManager
import com.ritvyom.yashoraReelgenerator.presentation.utils.FilterManager
import com.ritvyom.yashoraReelgenerator.presentation.utils.SoundSynth
import com.ritvyom.yashoraReelgenerator.presentation.utils.VoiceRecorderManager
import com.ritvyom.yashoraReelgenerator.presentation.utils.localize
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Filter Studio Sheet:
 * - Categories: Trending, Cinematic, Retro, Vintage, B&W, Pastel, Film, HDR, Warm, Cool
 * - Visual filter cards with color preview
 * - Filter Intensity Slider (0% - 100%)
 * - Reset to Original
 * - Apply to all clips option
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterStudioSheet(
    currentScene: Scene,
    appLanguage: String,
    onFilterSelected: (name: String, category: String, index: Int) -> Unit,
    onIntensityChanged: (Int) -> Unit,
    onApplyToAll: (name: String, category: String, index: Int, intensity: Int) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedCategory by remember { mutableStateOf(currentScene.filterCategory.ifEmpty { "Trending" }) }
    var currentIntensity by remember { mutableFloatStateOf(currentScene.filterIntensity.toFloat()) }
    var applyToAllClips by remember { mutableStateOf(false) }

    val categories = remember { FilterManager.CATEGORIES }
    val availableFilters = remember(selectedCategory) {
        FilterManager.getFiltersByCategory(selectedCategory)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF181820),
        tonalElevation = 8.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp)
        ) {
            // Sheet Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.ColorLens,
                        contentDescription = null,
                        tint = YouCutOrange,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Filter Studio".localize(appLanguage),
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(
                        onClick = {
                            onFilterSelected("Normal", "Normal", 0)
                            onIntensityChanged(80)
                        }
                    ) {
                        Text("Reset / Original".localize(appLanguage), color = YouCutOrange, fontSize = 12.sp)
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Check, contentDescription = "Done", tint = YouCutOrange)
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Filter Category Tabs
            val catScrollState = rememberScrollState()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(catScrollState),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                categories.forEach { category ->
                    val isCatSelected = selectedCategory.equals(category, ignoreCase = true)
                    FilterChip(
                        selected = isCatSelected,
                        onClick = { selectedCategory = category },
                        label = {
                            Text(
                                text = category.localize(appLanguage),
                                fontSize = 11.sp,
                                fontWeight = if (isCatSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = YouCutOrange,
                            selectedLabelColor = Color.White,
                            containerColor = Color(0xFF242430),
                            labelColor = Color.LightGray
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Filter Thumbnails Horizontal List
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Original / Normal Option
                item {
                    val isNoneSelected = currentScene.filterCategory == "Normal" || currentScene.selectedFilterName == "Normal"
                    Column(
                        modifier = Modifier
                            .width(68.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                onFilterSelected("Normal", "Normal", 0)
                            },
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF2E2E3E))
                                .border(
                                    width = if (isNoneSelected) 2.dp else 1.dp,
                                    color = if (isNoneSelected) YouCutOrange else Color(0xFF444458),
                                    shape = RoundedCornerShape(8.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Block, contentDescription = null, tint = Color.LightGray)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Original".localize(appLanguage),
                            color = if (isNoneSelected) YouCutOrange else Color.LightGray,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Filter Items
                items(availableFilters) { filterData ->
                    val isSelected = currentScene.filterCategory == filterData.name || currentScene.selectedFilterName == filterData.name
                    Column(
                        modifier = Modifier
                            .width(68.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                val idx = FilterManager.ALL_FILTERS.indexOf(filterData).coerceAtLeast(0)
                                onFilterSelected(filterData.name, filterData.name, idx)
                                if (applyToAllClips) {
                                    onApplyToAll(filterData.name, filterData.name, idx, currentIntensity.toInt())
                                }
                            },
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    Brush.linearGradient(
                                        listOf(
                                            filterData.tintOverlayColor.copy(alpha = 0.8f),
                                            Color(0xFF1E1E2C)
                                        )
                                    )
                                )
                                .border(
                                    width = if (isSelected) 2.dp else 1.dp,
                                    color = if (isSelected) YouCutOrange else Color(0xFF383848),
                                    shape = RoundedCornerShape(8.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = filterData.name.take(3).uppercase(),
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = filterData.name,
                            color = if (isSelected) YouCutOrange else Color.LightGray,
                            fontSize = 10.sp,
                            fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Intensity Slider
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Filter Intensity".localize(appLanguage),
                    color = Color.LightGray,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "${currentIntensity.toInt()}%",
                    color = YouCutOrange,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }

            Slider(
                value = currentIntensity,
                onValueChange = {
                    currentIntensity = it
                    onIntensityChanged(it.toInt())
                },
                valueRange = 0f..100f,
                colors = SliderDefaults.colors(
                    thumbColor = YouCutOrange,
                    activeTrackColor = YouCutOrange,
                    inactiveTrackColor = Color(0xFF383848)
                )
            )

            // Apply to all clips checkbox
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { applyToAllClips = !applyToAllClips }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = applyToAllClips,
                    onCheckedChange = { applyToAllClips = it },
                    colors = CheckboxDefaults.colors(
                        checkedColor = YouCutOrange,
                        checkmarkColor = Color.White
                    )
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Apply filter to all scenes".localize(appLanguage),
                    color = Color.LightGray,
                    fontSize = 12.sp
                )
            }
        }
    }
}

/**
 * Visual Effects Studio Sheet:
 * - Categories: Basic, Party, Glitch, Nature, Cinematic, Spark, Fire
 * - Cards for effects (Glitch, VHS, Shake, Snow, Rain, Sparkle, Disco, Flash, Zoom, Blur, etc.)
 * - Intensity slider
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EffectStudioSheet(
    currentScene: Scene,
    appLanguage: String,
    onEffectSelected: (name: String, category: String) -> Unit,
    onIntensityChanged: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedCategory by remember { mutableStateOf(currentScene.effectCategory.ifEmpty { "Glitch" }) }
    var currentIntensity by remember { mutableFloatStateOf(currentScene.effectIntensity.toFloat()) }

    val categories = remember { EffectManager.CATEGORIES }
    val availableEffects = remember(selectedCategory) {
        EffectManager.getEffectsByCategory(selectedCategory)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF181820),
        tonalElevation = 8.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.AutoFixHigh,
                        contentDescription = null,
                        tint = YouCutOrange,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Video Effects".localize(appLanguage),
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                TextButton(
                    onClick = {
                        onEffectSelected("None", "None")
                    }
                ) {
                    Text("None / Remove".localize(appLanguage), color = YouCutOrange, fontSize = 12.sp)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Category Tabs
            val catScrollState = rememberScrollState()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(catScrollState),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                categories.forEach { category ->
                    val isCatSelected = selectedCategory.equals(category, ignoreCase = true)
                    FilterChip(
                        selected = isCatSelected,
                        onClick = { selectedCategory = category },
                        label = {
                            Text(
                                text = category.localize(appLanguage),
                                fontSize = 11.sp,
                                fontWeight = if (isCatSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = YouCutOrange,
                            selectedLabelColor = Color.White,
                            containerColor = Color(0xFF242430),
                            labelColor = Color.LightGray
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Effect Items
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                items(availableEffects) { effectData ->
                    val isSelected = currentScene.effectName == effectData.name
                    Column(
                        modifier = Modifier
                            .width(74.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                onEffectSelected(effectData.name, effectData.category)
                            },
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(58.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) YouCutOrange.copy(alpha = 0.25f) else Color(0xFF222230))
                                .border(
                                    width = if (isSelected) 2.dp else 1.dp,
                                    color = if (isSelected) YouCutOrange else Color(0xFF383848),
                                    shape = RoundedCornerShape(8.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    tint = if (isSelected) YouCutOrange else Color.LightGray,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = effectData.name,
                                    color = Color.White,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Effect Intensity
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Effect Intensity".localize(appLanguage),
                    color = Color.LightGray,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "${currentIntensity.toInt()}%",
                    color = YouCutOrange,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Slider(
                value = currentIntensity,
                onValueChange = {
                    currentIntensity = it
                    onIntensityChanged(it.toInt())
                },
                valueRange = 0f..100f,
                colors = SliderDefaults.colors(
                    thumbColor = YouCutOrange,
                    activeTrackColor = YouCutOrange,
                    inactiveTrackColor = Color(0xFF383848)
                )
            )
        }
    }
}

/**
 * Trim & Split Studio Sheet:
 * - Start & End time adjustment
 * - Split at playhead
 * - Delete clip
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrimStudioSheet(
    currentScene: Scene,
    selectedSceneIndex: Int,
    totalScenes: Int,
    currentPlaybackTimeMs: Long,
    appLanguage: String,
    onDurationChanged: (Int) -> Unit,
    onSplitClip: () -> Unit,
    onDeleteClip: () -> Unit,
    onDismiss: () -> Unit
) {
    var clipDuration by remember { mutableFloatStateOf(currentScene.durationSeconds.toFloat()) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF181820),
        tonalElevation = 8.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.ContentCut,
                        contentDescription = null,
                        tint = YouCutOrange,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Trim & Cut Clip #${selectedSceneIndex + 1}".localize(appLanguage),
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Check, contentDescription = "Done", tint = YouCutOrange)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Duration Slider
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Clip Duration".localize(appLanguage),
                    color = Color.LightGray,
                    fontSize = 13.sp
                )
                Text(
                    text = "${clipDuration.toInt()}s",
                    color = YouCutOrange,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }

            Slider(
                value = clipDuration,
                onValueChange = {
                    clipDuration = it
                    onDurationChanged(it.toInt())
                },
                valueRange = 1f..60f,
                steps = 59,
                colors = SliderDefaults.colors(
                    thumbColor = YouCutOrange,
                    activeTrackColor = YouCutOrange,
                    inactiveTrackColor = Color(0xFF383848)
                )
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Action Buttons: Split at Playhead & Delete Clip
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = {
                        onSplitClip()
                        onDismiss()
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = YouCutOrange),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.ContentCut, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Split at Playhead".localize(appLanguage), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = {
                        onDeleteClip()
                        onDismiss()
                    },
                    enabled = totalScenes > 1,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.6f)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Delete Clip".localize(appLanguage), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/**
 * Music & Sound Effects Studio Sheet:
 * - Library of Beats & Genres
 * - Import Local Audio from device (.mp3, .wav, .m4a)
 * - Sound FX (Whoosh, Pop, Camera Click, Ding, etc.)
 * - Music Volume control
 * - Mute original clip audio
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicStudioSheet(
    currentScene: Scene,
    appLanguage: String,
    onSelectBgCategory: (String) -> Unit,
    onPickCustomAudio: () -> Unit,
    onSelectSfx: (String) -> Unit,
    onVolumeChanged: (Float) -> Unit,
    onToggleMuteClipAudio: () -> Unit,
    isClipAudioMuted: Boolean,
    customAudioTrackName: String? = null,
    customAudioDurationMs: Long = 0L,
    audioTrimStartMs: Long = 0L,
    audioTrimDurationMs: Long = 0L,
    onAudioTrimChanged: ((startMs: Long, durationMs: Long) -> Unit)? = null,
    onRemoveAudioTrack: (() -> Unit)? = null,
    onDismiss: () -> Unit
) {
    var activeMusicTab by remember { mutableIntStateOf(if (!customAudioTrackName.isNullOrEmpty()) 2 else 0) } // 0 = Background Music, 1 = Sound FX, 2 = Audio Adjust / Trim
    val genres = remember { listOf("Upbeat", "Cinematic", "Lofi Chill", "Motivational", "Acoustic", "Hip Hop", "Ambient") }
    val sfxList = remember { listOf("Whoosh", "Pop", "Camera Click", "Ding", "Glitch Swish", "Sub Drop", "Magic Chime", "Drum Roll") }
    var musicVolume by remember { mutableFloatStateOf(100f) }
    var trimStartSec by remember(audioTrimStartMs) { mutableFloatStateOf(audioTrimStartMs / 1000f) }
    val audioDurationSec = remember(customAudioDurationMs) { (customAudioDurationMs / 1000f).coerceAtLeast(10f) }
    var trimDurationSec by remember(audioTrimDurationMs, audioDurationSec) { 
        mutableFloatStateOf(if (audioTrimDurationMs > 0L) (audioTrimDurationMs / 1000f) else audioDurationSec.coerceAtMost(30f)) 
    }
    val coroutineScope = rememberCoroutineScope()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF181820),
        tonalElevation = 8.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.MusicNote,
                        contentDescription = null,
                        tint = YouCutOrange,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Music & Sound Effects".localize(appLanguage),
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Check, contentDescription = "Done", tint = YouCutOrange)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Tabs: Background Music / Sound FX / Volume
            TabRow(
                selectedTabIndex = activeMusicTab,
                containerColor = Color(0xFF222230),
                contentColor = YouCutOrange
            ) {
                Tab(
                    selected = activeMusicTab == 0,
                    onClick = { activeMusicTab = 0 },
                    text = { Text("Music Tracks".localize(appLanguage), fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                )
                Tab(
                    selected = activeMusicTab == 1,
                    onClick = { activeMusicTab = 1 },
                    text = { Text("Sound Effects".localize(appLanguage), fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                )
                Tab(
                    selected = activeMusicTab == 2,
                    onClick = { activeMusicTab = 2 },
                    text = { Text("Audio Settings".localize(appLanguage), fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            when (activeMusicTab) {
                0 -> {
                    // Custom Audio Import Button
                    Button(
                        onClick = onPickCustomAudio,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = YouCutOrange),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Import Music From Phone".localize(appLanguage), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text("Curated Background Tracks:".localize(appLanguage), color = Color.LightGray, fontSize = 11.sp)
                    Spacer(modifier = Modifier.height(6.dp))

                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(genres) { genre ->
                            Card(
                                modifier = Modifier
                                    .width(110.dp)
                                    .height(70.dp)
                                    .clickable {
                                        onSelectBgCategory(genre)
                                    },
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF282838)),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF3E3E50)),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(8.dp),
                                    verticalArrangement = Arrangement.Center,
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(Icons.Default.GraphicEq, contentDescription = null, tint = YouCutOrange, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(genre, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                1 -> {
                    // Sound FX list
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.height(160.dp)
                    ) {
                        items(sfxList) { sfxName ->
                            val isSelected = currentScene.sfxName == sfxName
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onSelectSfx(sfxName)
                                        coroutineScope.launch {
                                            SoundSynth.playSfx(sfxName)
                                        }
                                    },
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isSelected) YouCutOrange.copy(alpha = 0.25f) else Color(0xFF252535)
                                ),
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    if (isSelected) YouCutOrange else Color(0xFF383848)
                                ),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.VolumeUp, contentDescription = null, tint = YouCutOrange, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(sfxName, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                                }
                            }
                        }
                    }
                }

                2 -> {
                    // Audio Settings: Volume & Mute
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Music Volume".localize(appLanguage), color = Color.LightGray, fontSize = 12.sp)
                        Text("${musicVolume.toInt()}%", color = YouCutOrange, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    Slider(
                        value = musicVolume,
                        onValueChange = {
                            musicVolume = it
                            onVolumeChanged(it / 100f)
                        },
                        valueRange = 0f..200f,
                        colors = SliderDefaults.colors(
                            thumbColor = YouCutOrange,
                            activeTrackColor = YouCutOrange,
                            inactiveTrackColor = Color(0xFF383848)
                        )
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onToggleMuteClipAudio)
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.VolumeOff, contentDescription = null, tint = YouCutOrange, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Mute Original Video Sound".localize(appLanguage), color = Color.White, fontSize = 12.sp)
                        }
                        Switch(
                            checked = isClipAudioMuted,
                            onCheckedChange = { onToggleMuteClipAudio() },
                            colors = SwitchDefaults.colors(checkedThumbColor = YouCutOrange, checkedTrackColor = YouCutOrange.copy(alpha = 0.5f))
                        )
                    }

                    // Custom Audio Track Trimming & Management
                    if (!customAudioTrackName.isNullOrEmpty()) {
                        Spacer(modifier = Modifier.height(16.dp))
                        HorizontalDivider(color = Color(0xFF2E2E40))
                        Spacer(modifier = Modifier.height(12.dp))

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF1F2937)),
                            shape = RoundedCornerShape(10.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF29B6F6).copy(alpha = 0.5f))
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.MusicNote, contentDescription = null, tint = Color(0xFF29B6F6), modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column {
                                            Text(
                                                text = customAudioTrackName,
                                                color = Color.White,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = "Audio Overlay Active".localize(appLanguage),
                                                color = Color(0xFF29B6F6),
                                                fontSize = 10.sp
                                            )
                                        }
                                    }

                                    if (onRemoveAudioTrack != null) {
                                        IconButton(onClick = onRemoveAudioTrack, modifier = Modifier.size(28.dp)) {
                                            Icon(Icons.Default.Delete, contentDescription = "Remove Audio", tint = Color.Red.copy(alpha = 0.8f), modifier = Modifier.size(16.dp))
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                // Audio Trim Start Offset
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Audio Start Offset".localize(appLanguage), color = Color.LightGray, fontSize = 11.sp)
                                    Text(String.format(Locale.US, "%.1fs", trimStartSec), color = Color(0xFF29B6F6), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }

                                Slider(
                                    value = trimStartSec,
                                    onValueChange = {
                                        trimStartSec = it
                                        onAudioTrimChanged?.invoke((it * 1000).toLong(), (trimDurationSec * 1000).toLong())
                                    },
                                    valueRange = 0f..(audioDurationSec - 1f).coerceAtLeast(0f),
                                    colors = SliderDefaults.colors(
                                        thumbColor = Color(0xFF29B6F6),
                                        activeTrackColor = Color(0xFF29B6F6),
                                        inactiveTrackColor = Color(0xFF383848)
                                    )
                                )

                                Spacer(modifier = Modifier.height(6.dp))

                                // Audio Trim Duration
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Trim Playback Duration".localize(appLanguage), color = Color.LightGray, fontSize = 11.sp)
                                    Text(String.format(Locale.US, "%.1fs", trimDurationSec), color = Color(0xFF29B6F6), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }

                                Slider(
                                    value = trimDurationSec,
                                    onValueChange = {
                                        trimDurationSec = it
                                        onAudioTrimChanged?.invoke((trimStartSec * 1000).toLong(), (it * 1000).toLong())
                                    },
                                    valueRange = 1f..audioDurationSec,
                                    colors = SliderDefaults.colors(
                                        thumbColor = Color(0xFF29B6F6),
                                        activeTrackColor = Color(0xFF29B6F6),
                                        inactiveTrackColor = Color(0xFF383848)
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Text & Typography Studio Sheet:
 * - Text overlay editor
 * - Color picker
 * - Font selection
 * - Text animation
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TextStudioSheet(
    currentScene: Scene,
    appLanguage: String,
    onLiveUpdate: ((text: String, color: String, font: String, animation: String, fontSize: Float, durationSec: Float, startSec: Float) -> Unit)? = null,
    onTextSaveFull: ((text: String, color: String, font: String, animation: String, fontSize: Float, durationSec: Float, startSec: Float) -> Unit)? = null,
    onTextSave: ((text: String, color: String, font: String, animation: String) -> Unit)? = null,
    onDismiss: () -> Unit
) {
    var textInput by remember { mutableStateOf(currentScene.textOverlay ?: "") }
    var selectedColor by remember { mutableStateOf(currentScene.overlayColor.ifEmpty { "#FFFFFF" }) }
    var selectedFont by remember { mutableStateOf(currentScene.captionFont.ifEmpty { "TikTok Style" }) }
    var selectedAnimation by remember { mutableStateOf(currentScene.textAnimation.ifEmpty { "Pop" }) }
    var fontSize by remember { mutableFloatStateOf(currentScene.textOverlayFontSize.coerceIn(12f, 54f)) }
    val maxDuration = currentScene.durationSeconds.toFloat().coerceAtLeast(1f)
    var durationSec by remember { mutableFloatStateOf(currentScene.textOverlayDurationSeconds.coerceIn(0.5f, maxDuration)) }
    var startSec by remember { mutableFloatStateOf(currentScene.textOverlayStartTimeSeconds.coerceIn(0f, (maxDuration - 0.5f).coerceAtLeast(0f))) }

    fun notifyLive() {
        onLiveUpdate?.invoke(textInput, selectedColor, selectedFont, selectedAnimation, fontSize, durationSec, startSec)
    }

    val colors = remember {
        listOf(
            "#FFFFFF", "#FFD700", "#FF5722", "#00E5FF", "#FF007F",
            "#00E676", "#FFEB3B", "#E040FB", "#000000"
        )
    }

    val fonts = remember {
        listOf("TikTok Style", "Modern Bold", "Cyberpunk", "Serif Elegant", "Handwritten", "Display Heavy")
    }

    val animations = remember {
        listOf("None", "Pop", "Fade", "Slide Up", "Slide Down", "Slide Left", "Slide Right", "Typewriter", "Bounce")
    }

    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Text & Font, 1 = Color & Stroke, 2 = Animation & Timing
    var selectedAlignment by remember { mutableStateOf("Center") }
    var hasBackground by remember { mutableStateOf(false) }
    var selectedBgColor by remember { mutableStateOf("#99000000") }
    var strokeWidth by remember { mutableFloatStateOf(0f) }
    var selectedStrokeColor by remember { mutableStateOf("#000000") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF181820),
        tonalElevation = 8.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.TextFields, contentDescription = null, tint = YouCutOrange, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Text & Titles Overlay".localize(appLanguage), color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = {
                        if (onTextSaveFull != null) {
                            onTextSaveFull(textInput, selectedColor, selectedFont, selectedAnimation, fontSize, durationSec, startSec)
                        } else {
                            onTextSave?.invoke(textInput, selectedColor, selectedFont, selectedAnimation)
                        }
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = YouCutOrange),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Text("Apply".localize(appLanguage), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Sub-tabs for clean mobile ergonomics
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = Color(0xFF22222E),
                contentColor = YouCutOrange,
                modifier = Modifier.clip(RoundedCornerShape(8.dp))
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Text & Font".localize(appLanguage), fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Color & Style".localize(appLanguage), fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = { Text("Motion & Time".localize(appLanguage), fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (selectedTab == 0) {
                // TAB 0: Text Input, Font Size, Alignment, Font Family
                OutlinedTextField(
                    value = textInput,
                    onValueChange = {
                        textInput = it
                        notifyLive()
                    },
                    placeholder = { Text("Enter title or caption overlay...".localize(appLanguage), color = Color.Gray, fontSize = 12.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = YouCutOrange,
                        unfocusedBorderColor = Color(0xFF383848)
                    )
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Style Presets Shortcut
                Text("Style Presets".localize(appLanguage), color = Color.LightGray, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(6.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        FilterChip(
                            selected = selectedColor == "#FFFFFF" && !hasBackground,
                            onClick = {
                                selectedColor = "#FFFFFF"
                                hasBackground = false
                                strokeWidth = 0f
                                notifyLive()
                            },
                            label = { Text("Classic White", fontSize = 10.sp) }
                        )
                    }
                    item {
                        FilterChip(
                            selected = selectedColor == "#FFEB3B" && strokeWidth > 0f,
                            onClick = {
                                selectedColor = "#FFEB3B"
                                strokeWidth = 2.5f
                                selectedStrokeColor = "#000000"
                                notifyLive()
                            },
                            label = { Text("Bold Pop", fontSize = 10.sp) }
                        )
                    }
                    item {
                        FilterChip(
                            selected = hasBackground,
                            onClick = {
                                hasBackground = true
                                selectedBgColor = "#99000000"
                                selectedColor = "#FFFFFF"
                                notifyLive()
                            },
                            label = { Text("Box Banner", fontSize = 10.sp) }
                        )
                    }
                    item {
                        FilterChip(
                            selected = selectedColor == "#00E5FF",
                            onClick = {
                                selectedColor = "#00E5FF"
                                selectedFont = "Cyberpunk"
                                strokeWidth = 1.5f
                                selectedStrokeColor = "#FF007F"
                                notifyLive()
                            },
                            label = { Text("Cyberpunk", fontSize = 10.sp) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Font Size Controls
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Font Size: ${fontSize.toInt()}sp".localize(appLanguage), color = Color.LightGray, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(16f to "S", 24f to "M", 32f to "L", 42f to "XL").forEach { (sz, label) ->
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (fontSize == sz) YouCutOrange else Color(0xFF282834),
                                modifier = Modifier.clickable {
                                    fontSize = sz
                                    notifyLive()
                                }
                            ) {
                                Text(label, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
                            }
                        }
                    }
                }
                Slider(
                    value = fontSize,
                    onValueChange = {
                        fontSize = it
                        notifyLive()
                    },
                    valueRange = 12f..54f,
                    colors = SliderDefaults.colors(thumbColor = YouCutOrange, activeTrackColor = YouCutOrange, inactiveTrackColor = Color(0xFF383848))
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Fonts
                Text("Font Family".localize(appLanguage), color = Color.LightGray, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(6.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(fonts) { fontName ->
                        val isSelected = selectedFont == fontName
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                selectedFont = fontName
                                notifyLive()
                            },
                            label = { Text(fontName, fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = YouCutOrange,
                                selectedLabelColor = Color.White,
                                containerColor = Color(0xFF242430),
                                labelColor = Color.LightGray
                            )
                        )
                    }
                }
            } else if (selectedTab == 1) {
                // TAB 1: Text Color, Background Pill, Stroke
                Text("Text Color".localize(appLanguage), color = Color.LightGray, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(6.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(colors) { hex ->
                        val isSelected = selectedColor.equals(hex, ignoreCase = true)
                        val parsedColor = try { Color(android.graphics.Color.parseColor(hex)) } catch (e: Exception) { Color.White }
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(parsedColor)
                                .border(
                                    width = if (isSelected) 3.dp else 1.dp,
                                    color = if (isSelected) YouCutOrange else Color.Gray.copy(alpha = 0.5f),
                                    shape = CircleShape
                                )
                                .clickable {
                                    selectedColor = hex
                                    notifyLive()
                                }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text("Fine-Tune Spectrum (Live Drag)".localize(appLanguage), color = Color.Gray, fontSize = 10.sp)
                Spacer(modifier = Modifier.height(4.dp))
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(26.dp)
                        .clip(RoundedCornerShape(13.dp))
                        .border(1.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(13.dp))
                ) {
                    val barWidthPx = constraints.maxWidth.toFloat().coerceAtLeast(1f)
                    val rainbowColors = remember {
                        listOf(
                            Color(0xFFFF0000), Color(0xFFFF7F00), Color(0xFFFFFF00),
                            Color(0xFF00FF00), Color(0xFF00FFFF), Color(0xFF0000FF),
                            Color(0xFF8B00FF), Color(0xFFFF00FF), Color(0xFFFF0000)
                        )
                    }
                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(barWidthPx) {
                                detectTapGestures { offset ->
                                    val frac = (offset.x / barWidthPx).coerceIn(0f, 1f)
                                    val hsv = floatArrayOf(frac * 360f, 1f, 1f)
                                    val c = android.graphics.Color.HSVToColor(hsv)
                                    selectedColor = String.format("#%06X", 0xFFFFFF and c)
                                    notifyLive()
                                }
                            }
                            .pointerInput(barWidthPx) {
                                detectDragGestures { change, _ ->
                                    change.consume()
                                    val frac = (change.position.x / barWidthPx).coerceIn(0f, 1f)
                                    val hsv = floatArrayOf(frac * 360f, 1f, 1f)
                                    val c = android.graphics.Color.HSVToColor(hsv)
                                    selectedColor = String.format("#%06X", 0xFFFFFF and c)
                                    notifyLive()
                                }
                            }
                    ) {
                        drawRect(brush = Brush.horizontalGradient(rainbowColors))
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Background Container Pill Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Background Container Box".localize(appLanguage), color = Color.LightGray, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Switch(
                        checked = hasBackground,
                        onCheckedChange = {
                            hasBackground = it
                            notifyLive()
                        },
                        colors = SwitchDefaults.colors(checkedThumbColor = YouCutOrange, checkedTrackColor = YouCutOrange.copy(alpha = 0.5f))
                    )
                }

                if (hasBackground) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("Background Box Color".localize(appLanguage), color = Color.Gray, fontSize = 10.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(listOf("#99000000", "#E6000000", "#99FFFFFF", "#E6FF5722", "#E600E5FF")) { bgHex ->
                            val isSel = selectedBgColor.equals(bgHex, ignoreCase = true)
                            val c = try { Color(android.graphics.Color.parseColor(bgHex)) } catch (_: Exception) { Color.Black }
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(c)
                                    .border(if (isSel) 2.dp else 1.dp, if (isSel) YouCutOrange else Color.Gray, RoundedCornerShape(6.dp))
                                    .clickable {
                                        selectedBgColor = bgHex
                                        notifyLive()
                                    }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Stroke / Outline
                Text("Stroke / Outline Width: ${String.format(java.util.Locale.US, "%.1f", strokeWidth)}dp".localize(appLanguage), color = Color.LightGray, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Slider(
                    value = strokeWidth,
                    onValueChange = {
                        strokeWidth = it
                        notifyLive()
                    },
                    valueRange = 0f..6f,
                    colors = SliderDefaults.colors(thumbColor = YouCutOrange, activeTrackColor = YouCutOrange, inactiveTrackColor = Color(0xFF383848))
                )
            } else {
                // TAB 2: Animation & Timing
                Text("Animation".localize(appLanguage), color = Color.LightGray, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(6.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(animations) { anim ->
                        val isSelected = selectedAnimation == anim
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                selectedAnimation = anim
                                notifyLive()
                            },
                            label = { Text(anim.localize(appLanguage), fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = YouCutOrange,
                                selectedLabelColor = Color.White,
                                containerColor = Color(0xFF242430),
                                labelColor = Color.LightGray
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Timeline Duration & Start Time Controls
                Text("Duration & Timeline Placement".localize(appLanguage), color = Color.LightGray, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF222230),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Start: ${String.format(java.util.Locale.US, "%.1fs", startSec)}", color = Color.White, fontSize = 11.sp)
                        Text("Duration: ${String.format(java.util.Locale.US, "%.1fs", durationSec)}", color = YouCutOrange, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Text("End: ${String.format(java.util.Locale.US, "%.1fs", (startSec + durationSec).coerceAtMost(maxDuration))}", color = Color.White, fontSize = 11.sp)
                    }
                }

                Text("Start Offset (s)".localize(appLanguage), color = Color.Gray, fontSize = 10.sp)
                Slider(
                    value = startSec,
                    onValueChange = {
                        startSec = it
                        if (startSec + durationSec > maxDuration) {
                            durationSec = (maxDuration - startSec).coerceAtLeast(0.5f)
                        }
                        notifyLive()
                    },
                    valueRange = 0f..(maxDuration - 0.5f).coerceAtLeast(0f),
                    colors = SliderDefaults.colors(thumbColor = YouCutOrange, activeTrackColor = YouCutOrange, inactiveTrackColor = Color(0xFF383848))
                )

                Text("Display Duration (s)".localize(appLanguage), color = Color.Gray, fontSize = 10.sp)
                Slider(
                    value = durationSec,
                    onValueChange = {
                        durationSec = it
                        notifyLive()
                    },
                    valueRange = 0.5f..(maxDuration - startSec).coerceAtLeast(0.5f),
                    colors = SliderDefaults.colors(thumbColor = YouCutOrange, activeTrackColor = YouCutOrange, inactiveTrackColor = Color(0xFF383848))
                )
            }
        }
    }
}

/**
 * Transition Manager Studio Sheet leveraging Media3 Transition Engine:
 * - Library of transitions: None, Fade, Dissolve, Wipe Left/Right/Up/Down, Zoom In/Out, Slide, Flash White, Blur
 * - Customizable transition duration slider (0.3s - 2.0s)
 * - "Apply to All Clips" shortcut
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransitionStudioSheet(
    currentScene: Scene,
    allScenesCount: Int,
    appLanguage: String,
    onTransitionSelected: (transitionType: String, durationMs: Long) -> Unit,
    onApplyToAll: (transitionType: String, durationMs: Long) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedTransition by remember { mutableStateOf(currentScene.transitionType.ifEmpty { "Fade" }) }
    var transitionDurationMs by remember { mutableLongStateOf(currentScene.transitionDurationMs.coerceIn(200L, 2000L)) }
    val transitions = remember { com.ritvyom.yashoraReelgenerator.presentation.utils.Media3TransitionEngine.TRANSITIONS }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF181820),
        tonalElevation = 8.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 28.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Animation,
                        contentDescription = null,
                        tint = YouCutOrange,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "Transition Manager (Media3)".localize(appLanguage),
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Clip #${currentScene.sceneNumber} Transition".localize(appLanguage),
                            color = Color.Gray,
                            fontSize = 11.sp
                        )
                    }
                }

                Button(
                    onClick = {
                        onTransitionSelected(selectedTransition, transitionDurationMs)
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = YouCutOrange),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp)
                ) {
                    Text("Apply".localize(appLanguage), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Duration Slider
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Transition Duration: ${String.format(java.util.Locale.US, "%.1fs", transitionDurationMs / 1000f)}".localize(appLanguage),
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(500L to "0.5s", 700L to "0.7s", 1000L to "1.0s", 1500L to "1.5s").forEach { (ms, label) ->
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (transitionDurationMs == ms) YouCutOrange else Color(0xFF282834),
                            modifier = Modifier.clickable { transitionDurationMs = ms }
                        ) {
                            Text(label, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp))
                        }
                    }
                }
            }

            Slider(
                value = transitionDurationMs.toFloat(),
                onValueChange = { transitionDurationMs = it.toLong() },
                valueRange = 300f..2000f,
                colors = SliderDefaults.colors(thumbColor = YouCutOrange, activeTrackColor = YouCutOrange, inactiveTrackColor = Color(0xFF383848))
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Apply to All Clips shortcut
            if (allScenesCount > 1) {
                OutlinedButton(
                    onClick = {
                        onApplyToAll(selectedTransition, transitionDurationMs)
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = YouCutOrange),
                    border = ButtonDefaults.outlinedButtonBorder.copy(brush = Brush.horizontalGradient(listOf(YouCutOrange, YouCutOrangeDark)))
                ) {
                    Icon(Icons.Default.DoneAll, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Apply '${selectedTransition}' to All $allScenesCount Clips".localize(appLanguage), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            Text("Transition Style Library".localize(appLanguage), color = Color.LightGray, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))

            // Grid of Transitions
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp)
            ) {
                items(transitions) { item ->
                    val isSelected = selectedTransition.equals(item.id, ignoreCase = true)
                    Card(
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) YouCutOrange.copy(alpha = 0.25f) else Color(0xFF222230)
                        ),
                        border = if (isSelected) ButtonDefaults.outlinedButtonBorder.copy(width = 2.dp, brush = Brush.horizontalGradient(listOf(YouCutOrange, YouCutOrangeDark))) else null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedTransition = item.id }
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(text = item.iconEmoji, fontSize = 24.sp)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = item.displayName.localize(appLanguage),
                                color = if (isSelected) YouCutOrange else Color.White,
                                fontSize = 10.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = item.category,
                                color = Color.Gray,
                                fontSize = 8.5.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Sticker & Emoji Studio Sheet:
 * - Rich stickers: 🔥, ❤️, ⭐, 🎬, 🚀, 💯, 💥, ✨, 👑, 🎯, 🎉, 👏, 🤩, 💖, ⚡, 🔔, 🏆, 🌟, etc.
 * - Sticker Scale slider
 * - Remove sticker
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StickerStudioSheet(
    currentScene: Scene,
    appLanguage: String,
    onSelectSticker: (String) -> Unit,
    onScaleChanged: (Float) -> Unit,
    onDismiss: () -> Unit
) {
    val stickers = remember {
        listOf(
            "🔥", "❤️", "⭐", "🎬", "🚀", "💯", "💥", "✨",
            "👑", "🎯", "🎉", "👏", "🤩", "💖", "⚡", "🔔",
            "🏆", "🌟", "💡", "🎵", "🕺", "💃", "🍕", "☕"
        )
    }
    var currentScale by remember { mutableFloatStateOf(currentScene.stickerScale) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF181820),
        tonalElevation = 8.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.SentimentSatisfiedAlt, contentDescription = null, tint = YouCutOrange, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Stickers & Emojis".localize(appLanguage), color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }

                TextButton(onClick = { onSelectSticker("None") }) {
                    Text("Remove".localize(appLanguage), color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            LazyVerticalGrid(
                columns = GridCells.Fixed(6),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.height(140.dp)
            ) {
                items(stickers) { sticker ->
                    val isSelected = currentScene.stickerName == sticker
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) YouCutOrange.copy(alpha = 0.25f) else Color(0xFF242430))
                            .border(
                                width = if (isSelected) 2.dp else 1.dp,
                                color = if (isSelected) YouCutOrange else Color(0xFF383848),
                                shape = RoundedCornerShape(8.dp)
                            )
                            .clickable { onSelectSticker(sticker) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(sticker, fontSize = 20.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Scale slider
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Sticker Size".localize(appLanguage), color = Color.LightGray, fontSize = 12.sp)
                Text("${(currentScale * 100).toInt()}%", color = YouCutOrange, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }

            Slider(
                value = currentScale,
                onValueChange = {
                    currentScale = it
                    onScaleChanged(it)
                },
                valueRange = 0.5f..2.5f,
                colors = SliderDefaults.colors(
                    thumbColor = YouCutOrange,
                    activeTrackColor = YouCutOrange,
                    inactiveTrackColor = Color(0xFF383848)
                )
            )
        }
    }
}

/**
 * Speed Control Studio Sheet:
 * - Presets: 0.2x, 0.5x, 0.75x, 1.0x, 1.25x, 1.5x, 2.0x, 3.0x
 * - Precision slider
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpeedStudioSheet(
    currentScene: Scene,
    appLanguage: String,
    onSpeedChanged: (Float) -> Unit,
    onDismiss: () -> Unit
) {
    var speedMultiplier by remember { mutableFloatStateOf(currentScene.speedMultiplier) }
    val presets = remember { listOf(0.2f, 0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f, 3.0f) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF181820),
        tonalElevation = 8.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Speed, contentDescription = null, tint = YouCutOrange, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Clip Speed".localize(appLanguage), color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }

                Text("${speedMultiplier}x", color = YouCutOrange, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Presets row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                presets.forEach { preset ->
                    val isSelected = kotlin.math.abs(speedMultiplier - preset) < 0.05f
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isSelected) YouCutOrange else Color(0xFF262636))
                            .clickable {
                                speedMultiplier = preset
                                onSpeedChanged(preset)
                            }
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "${preset}x",
                            color = if (isSelected) Color.White else Color.LightGray,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Slider(
                value = speedMultiplier,
                onValueChange = {
                    speedMultiplier = it
                    onSpeedChanged(it)
                },
                valueRange = 0.2f..3.0f,
                colors = SliderDefaults.colors(
                    thumbColor = YouCutOrange,
                    activeTrackColor = YouCutOrange,
                    inactiveTrackColor = Color(0xFF383848)
                )
            )
        }
    }
}

/**
 * Enhance & Color Grading Sheet:
 * - Brightness, Contrast, Saturation, Warmth
 * - Auto-enhance & Reset
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EnhanceStudioSheet(
    currentScene: Scene,
    appLanguage: String,
    onAdjustmentsChanged: (brightness: Float, contrast: Float, saturation: Float, warmth: Float) -> Unit,
    onDismiss: () -> Unit
) {
    var brightness by remember { mutableFloatStateOf(currentScene.brightnessValue) }
    var contrast by remember { mutableFloatStateOf(currentScene.contrastValue) }
    var saturation by remember { mutableFloatStateOf(currentScene.saturationValue) }
    var warmth by remember { mutableFloatStateOf(currentScene.warmthValue) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF181820),
        tonalElevation = 8.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = YouCutOrange, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Enhance & Color Grading".localize(appLanguage), color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }

                TextButton(
                    onClick = {
                        brightness = 0f
                        contrast = 1.0f
                        saturation = 1.0f
                        warmth = 0f
                        onAdjustmentsChanged(0f, 1f, 1f, 0f)
                    }
                ) {
                    Text("Reset".localize(appLanguage), color = YouCutOrange, fontSize = 12.sp)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Brightness
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Brightness".localize(appLanguage), color = Color.LightGray, fontSize = 11.sp)
                Text("${brightness.toInt()}", color = YouCutOrange, fontSize = 11.sp)
            }
            Slider(
                value = brightness,
                onValueChange = {
                    brightness = it
                    onAdjustmentsChanged(brightness, contrast, saturation, warmth)
                },
                valueRange = -50f..50f,
                colors = SliderDefaults.colors(thumbColor = YouCutOrange, activeTrackColor = YouCutOrange, inactiveTrackColor = Color(0xFF383848))
            )

            // Contrast
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Contrast".localize(appLanguage), color = Color.LightGray, fontSize = 11.sp)
                Text(String.format("%.1f", contrast), color = YouCutOrange, fontSize = 11.sp)
            }
            Slider(
                value = contrast,
                onValueChange = {
                    contrast = it
                    onAdjustmentsChanged(brightness, contrast, saturation, warmth)
                },
                valueRange = 0.5f..2.0f,
                colors = SliderDefaults.colors(thumbColor = YouCutOrange, activeTrackColor = YouCutOrange, inactiveTrackColor = Color(0xFF383848))
            )

            // Saturation
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Saturation".localize(appLanguage), color = Color.LightGray, fontSize = 11.sp)
                Text(String.format("%.1f", saturation), color = YouCutOrange, fontSize = 11.sp)
            }
            Slider(
                value = saturation,
                onValueChange = {
                    saturation = it
                    onAdjustmentsChanged(brightness, contrast, saturation, warmth)
                },
                valueRange = 0.0f..2.0f,
                colors = SliderDefaults.colors(thumbColor = YouCutOrange, activeTrackColor = YouCutOrange, inactiveTrackColor = Color(0xFF383848))
            )
        }
    }
}

/**
 * Background & Canvas Aspect Ratio Studio Sheet:
 * - 9:16 (Reels/TikTok), 16:9 (YouTube), 1:1 (Instagram), 4:5, 3:4
 * - Blur video background, Solid colors
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BgStudioSheet(
    activeAspectRatio: String,
    appLanguage: String,
    onRatioSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val ratios = remember {
        listOf(
            Triple("9:16", "Reels / TikTok", Icons.Default.StayCurrentPortrait),
            Triple("16:9", "YouTube Landscape", Icons.Default.StayCurrentLandscape),
            Triple("1:1", "Square Feed", Icons.Default.CropSquare),
            Triple("4:5", "Portrait Post", Icons.Default.AspectRatio),
            Triple("3:4", "Standard Frame", Icons.Default.ViewCompact)
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF181820),
        tonalElevation = 8.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Wallpaper, contentDescription = null, tint = YouCutOrange, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Canvas Background & Ratio".localize(appLanguage), color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }

                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Check, contentDescription = "Done", tint = YouCutOrange)
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text("Select Aspect Ratio:".localize(appLanguage), color = Color.LightGray, fontSize = 12.sp)
            Spacer(modifier = Modifier.height(8.dp))

            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(ratios) { (ratio, label, icon) ->
                    val isSelected = activeAspectRatio == ratio
                    Card(
                        modifier = Modifier
                            .width(100.dp)
                            .height(85.dp)
                            .clickable { onRatioSelected(ratio) },
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) YouCutOrange.copy(alpha = 0.25f) else Color(0xFF262636)
                        ),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (isSelected) YouCutOrange else Color(0xFF383848)
                        ),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(icon, contentDescription = null, tint = if (isSelected) YouCutOrange else Color.White, modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(ratio, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Text(label, color = Color.Gray, fontSize = 8.sp, maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Voiceover Recording Studio Sheet:
 * - Real-time Voiceover recorder with live mic waveform
 * - Save directly to scene narration!
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceRecorderSheet(
    currentScene: Scene,
    selectedSceneIndex: Int,
    appLanguage: String,
    onVoiceRecorded: (audioFilePath: String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val isRecording by VoiceRecorderManager.isRecording.collectAsState()
    val recordingDuration by VoiceRecorderManager.recordingDurationSeconds.collectAsState()
    val recordedFile by VoiceRecorderManager.recordedAudioFile.collectAsState()
    val isPlaying by VoiceRecorderManager.isPlaying.collectAsState()

    ModalBottomSheet(
        onDismissRequest = {
            if (isRecording) {
                VoiceRecorderManager.stopRecording(discard = true)
            }
            onDismiss()
        },
        containerColor = Color(0xFF181820),
        tonalElevation = 8.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Mic, contentDescription = null, tint = YouCutOrange, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Record Voiceover".localize(appLanguage), color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }

                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.Gray)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Recording Timer
            Text(
                text = String.format("%02d:%02d", recordingDuration / 60, recordingDuration % 60),
                color = if (isRecording) YouCutOrange else Color.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Record Button
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(if (isRecording) MaterialTheme.colorScheme.error else YouCutOrange)
                    .clickable {
                        if (isRecording) {
                            VoiceRecorderManager.stopRecording(discard = false)
                        } else {
                            VoiceRecorderManager.startRecording(context)
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isRecording) Icons.Default.Stop else Icons.Default.Mic,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(36.dp)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = if (isRecording) "Recording... Tap to stop".localize(appLanguage) else "Tap mic to start recording voiceover".localize(appLanguage),
                color = Color.LightGray,
                fontSize = 11.sp
            )

            // Playback & Save
            if (recordedFile != null && !isRecording) {
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            if (isPlaying) {
                                VoiceRecorderManager.stopPlayback()
                            } else {
                                recordedFile?.let { VoiceRecorderManager.playRecordedAudio(context, it) }
                            }
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.Gray)
                    ) {
                        Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (isPlaying) "Pause" else "Preview", fontSize = 12.sp)
                    }

                    Button(
                        onClick = {
                            recordedFile?.let { file ->
                                onVoiceRecorded(file.absolutePath)
                                onDismiss()
                            }
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = YouCutOrange)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Apply to Scene".localize(appLanguage), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

/**
 * Volume Control Studio Sheet:
 * - 0% to 200% volume slider
 * - Mute switch
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VolumeStudioSheet(
    currentScene: Scene,
    appLanguage: String,
    onVolumeChanged: (Float) -> Unit,
    onDismiss: () -> Unit
) {
    var volumePercent by remember { mutableFloatStateOf((currentScene.volume * 100f).coerceIn(0f, 200f)) }
    val sheetScroll = rememberScrollState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF181820),
        tonalElevation = 8.dp,
        modifier = Modifier.testTag("volume_studio_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp)
                .verticalScroll(sheetScroll)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.VolumeUp, contentDescription = null, tint = YouCutOrange, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Clip Volume".localize(appLanguage), color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }

                Text("${volumePercent.toInt()}%", color = YouCutOrange, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(16.dp))

            Slider(
                value = volumePercent,
                onValueChange = {
                    volumePercent = it
                    onVolumeChanged(it / 100f)
                },
                valueRange = 0f..200f,
                colors = SliderDefaults.colors(
                    thumbColor = YouCutOrange,
                    activeTrackColor = YouCutOrange,
                    inactiveTrackColor = Color(0xFF383848)
                ),
                modifier = Modifier.testTag("volume_slider")
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        volumePercent = 0f
                        onVolumeChanged(0f)
                    },
                    modifier = Modifier
                        .weight(1f)
                        .defaultMinSize(minHeight = 48.dp)
                        .testTag("volume_mute_button"),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.LightGray)
                ) {
                    Text("Mute (0%)".localize(appLanguage), fontSize = 11.sp)
                }

                OutlinedButton(
                    onClick = {
                        volumePercent = 100f
                        onVolumeChanged(1f)
                    },
                    modifier = Modifier
                        .weight(1f)
                        .defaultMinSize(minHeight = 48.dp)
                        .testTag("volume_default_button"),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = YouCutOrange)
                ) {
                    Text("Default (100%)".localize(appLanguage), fontSize = 11.sp)
                }

                OutlinedButton(
                    onClick = {
                        volumePercent = 200f
                        onVolumeChanged(2f)
                    },
                    modifier = Modifier
                        .weight(1f)
                        .defaultMinSize(minHeight = 48.dp)
                        .testTag("volume_boost_button"),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = YouCutOrange)
                ) {
                    Text("Boost (200%)".localize(appLanguage), fontSize = 11.sp)
                }
            }
        }
    }
}

/**
 * Crop / Zoom Framing Studio Sheet:
 * - Scale factor from 1.0x to 2.5x
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CropStudioSheet(
    appLanguage: String,
    onDismiss: () -> Unit
) {
    var zoomFactor by remember { mutableFloatStateOf(1.0f) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF181820),
        tonalElevation = 8.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Crop, contentDescription = null, tint = YouCutOrange, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Crop & Zoom Framing".localize(appLanguage), color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }

                Text(String.format("%.1fx", zoomFactor), color = YouCutOrange, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(16.dp))

            Slider(
                value = zoomFactor,
                onValueChange = { zoomFactor = it },
                valueRange = 1.0f..2.5f,
                colors = SliderDefaults.colors(
                    thumbColor = YouCutOrange,
                    activeTrackColor = YouCutOrange,
                    inactiveTrackColor = Color(0xFF383848)
                )
            )

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = YouCutOrange),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Confirm Framing".localize(appLanguage), fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        }
    }
}

/**
 * Chroma Key Studio Sheet:
 * Real-time color keying cutout (Green screen, Blue screen, Custom color, Sensitivity & Spill suppression).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChromaKeyStudioSheet(
    currentScene: Scene,
    appLanguage: String,
    onChromaChanged: (isEnabled: Boolean, color: String, sensitivity: Float) -> Unit,
    onDismiss: () -> Unit
) {
    var isEnabled by remember { mutableStateOf(currentScene.isChromaKeyEnabled) }
    var selectedColor by remember { mutableStateOf(if (currentScene.chromaKeyColor.isBlank()) "#00FF00" else currentScene.chromaKeyColor) }
    var sensitivity by remember { mutableFloatStateOf(if (currentScene.chromaKeySensitivity <= 0f) 0.4f else currentScene.chromaKeySensitivity) }

    val keyPresets = remember {
        listOf(
            Triple("Green Screen", "#00FF00", Color(0xFF00FF00)),
            Triple("Blue Screen", "#0000FF", Color(0xFF2979FF)),
            Triple("Cyan Screen", "#00FFFF", Color(0xFF00E5FF)),
            Triple("Magenta", "#FF00FF", Color(0xFFFF00FF)),
            Triple("Black", "#000000", Color(0xFF111111)),
            Triple("White", "#FFFFFF", Color(0xFFEEEEEE))
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF181820),
        tonalElevation = 8.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 28.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Palette, contentDescription = null, tint = YouCutOrange, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Chroma Key Cutout".localize(appLanguage), color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }

                Switch(
                    checked = isEnabled,
                    onCheckedChange = {
                        isEnabled = it
                        onChromaChanged(isEnabled, selectedColor, sensitivity)
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = YouCutOrange
                    )
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text("Key Color Preset".localize(appLanguage), color = Color.LightGray, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                keyPresets.forEach { (label, hex, col) ->
                    val isCur = selectedColor.equals(hex, ignoreCase = true)
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(col)
                            .border(
                                width = if (isCur) 3.dp else 1.dp,
                                color = if (isCur) YouCutOrange else Color(0xFF444455),
                                shape = CircleShape
                            )
                            .clickable {
                                selectedColor = hex
                                isEnabled = true
                                onChromaChanged(true, hex, sensitivity)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (isCur) {
                            Icon(Icons.Default.Check, contentDescription = null, tint = if (hex == "#FFFFFF") Color.Black else Color.White, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Sensitivity Threshold".localize(appLanguage), color = Color.White, fontSize = 13.sp)
                Text(String.format("%.0f%%", sensitivity * 100f), color = YouCutOrange, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }

            Slider(
                value = sensitivity,
                onValueChange = {
                    sensitivity = it
                    if (isEnabled) {
                        onChromaChanged(true, selectedColor, it)
                    }
                },
                valueRange = 0.05f..0.95f,
                colors = SliderDefaults.colors(
                    thumbColor = YouCutOrange,
                    activeTrackColor = YouCutOrange,
                    inactiveTrackColor = Color(0xFF383848)
                )
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = YouCutOrange),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Apply Chroma Key".localize(appLanguage), fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }
    }
}

/**
 * Mask Studio Sheet:
 * Shape masking (None, Linear, Mirror, Radial Circle, Rectangle, Heart) with feather and invert.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MaskStudioSheet(
    currentScene: Scene,
    appLanguage: String,
    onMaskChanged: (maskShape: String) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedShape by remember { mutableStateOf(if (currentScene.maskShape.isBlank()) "None" else currentScene.maskShape) }

    val maskOptions = remember {
        listOf(
            Pair("None", Icons.Default.Block),
            Pair("Linear", Icons.Default.Splitscreen),
            Pair("Mirror", Icons.Default.ViewAgenda),
            Pair("Circle", Icons.Default.Circle),
            Pair("Rectangle", Icons.Default.CropLandscape),
            Pair("Heart", Icons.Default.Favorite)
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF181820),
        tonalElevation = 8.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 28.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.FilterFrames, contentDescription = null, tint = YouCutOrange, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Mask Shapes".localize(appLanguage), color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }

                Text(selectedShape, color = YouCutOrange, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                maskOptions.forEach { (shape, icon) ->
                    val isCur = selectedShape.equals(shape, ignoreCase = true)
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isCur) YouCutOrange else Color(0xFF242434))
                            .clickable {
                                selectedShape = shape
                                onMaskChanged(shape)
                            }
                            .padding(horizontal = 8.dp, vertical = 8.dp)
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = shape,
                            tint = if (isCur) Color.White else Color.LightGray,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = shape,
                            color = if (isCur) Color.White else Color.Gray,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = YouCutOrange),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Confirm Mask".localize(appLanguage), fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }
    }
}
