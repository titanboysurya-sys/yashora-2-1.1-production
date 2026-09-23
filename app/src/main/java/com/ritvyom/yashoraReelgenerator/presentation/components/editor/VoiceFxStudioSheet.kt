package com.ritvyom.yashoraReelgenerator.presentation.components.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ritvyom.yashoraReelgenerator.domain.models.Scene
import com.ritvyom.yashoraReelgenerator.presentation.utils.localize

data class VoicePreset(
    val id: String,
    val name: String,
    val icon: ImageVector,
    val subtitle: String
)

/**
 * CapCut & InShot Signature Voice Changer & Audio Fade Effects Studio
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceFxStudioSheet(
    currentScene: Scene,
    appLanguage: String,
    onVoiceFxChanged: (voiceEffect: String, fadeIn: Float, fadeOut: Float) -> Unit,
    onVolumeChanged: (volume: Float) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedEffect by remember { mutableStateOf(currentScene.voiceEffect.ifEmpty { "None" }) }
    var fadeInDuration by remember { mutableFloatStateOf(currentScene.audioFadeInDuration) }
    var fadeOutDuration by remember { mutableFloatStateOf(currentScene.audioFadeOutDuration) }
    var clipVolume by remember { mutableFloatStateOf(currentScene.volume) }

    val voicePresets = remember {
        listOf(
            VoicePreset("None", "Original", Icons.Default.Mic, "Natural sound"),
            VoicePreset("Deep Male", "Deep Bass", Icons.Default.Hearing, "Rich low baritone"),
            VoicePreset("Chipmunk", "Chipmunk", Icons.Default.Mood, "High-pitched funny"),
            VoicePreset("Robot", "Synth Robot", Icons.Default.SmartToy, "Vocoder effect"),
            VoicePreset("Megaphone", "Megaphone", Icons.Default.Campaign, "Vintage announcer"),
            VoicePreset("Echo", "Cave Echo", Icons.Default.SurroundSound, "Cathedral reverb"),
            VoicePreset("Studio Mic", "Studio Clarity", Icons.Default.Headphones, "Vocal EQ boost"),
            VoicePreset("Telephone", "Lo-Fi Radio", Icons.Default.PhoneIphone, "Old telephone bandpass")
        )
    }

    fun applyChange() {
        onVoiceFxChanged(selectedEffect, fadeInDuration, fadeOutDuration)
    }

    val sheetScroll = rememberScrollState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF14141B),
        tonalElevation = 8.dp,
        modifier = Modifier.testTag("voice_fx_studio_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp)
                .verticalScroll(sheetScroll)
        ) {
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
                        tint = YouCutOrange,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Voice Effects & Audio Studio".localize(appLanguage),
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

            // Voice Effects Presets Grid
            Text(
                text = "Voice Changer Filter".localize(appLanguage),
                color = Color.LightGray,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(6.dp))

            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
            ) {
                items(voicePresets) { preset ->
                    val isSelected = selectedEffect == preset.id

                    Column(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) YouCutOrange.copy(alpha = 0.22f) else Color(0xFF1E1E26))
                            .border(
                                width = if (isSelected) 1.5.dp else 0.5.dp,
                                color = if (isSelected) YouCutOrange else Color.White.copy(alpha = 0.08f),
                                shape = RoundedCornerShape(8.dp)
                            )
                            .clickable {
                                selectedEffect = preset.id
                                applyChange()
                            }
                            .padding(vertical = 8.dp, horizontal = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(if (isSelected) YouCutOrange else Color.White.copy(alpha = 0.06f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = preset.icon,
                                contentDescription = preset.name,
                                tint = if (isSelected) Color.White else Color.LightGray,
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = preset.name.localize(appLanguage),
                            color = if (isSelected) YouCutOrange else Color.White,
                            fontSize = 10.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            textAlign = TextAlign.Center,
                            maxLines = 1
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Audio Fade In & Fade Out Controls (CapCut Signature feature)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Fade In Box
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF1E1E28))
                        .padding(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Fade In".localize(appLanguage), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        Text("%.1fs".format(fadeInDuration), color = YouCutOrange, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    Slider(
                        value = fadeInDuration,
                        onValueChange = {
                            fadeInDuration = it
                            applyChange()
                        },
                        valueRange = 0.0f..3.0f,
                        colors = SliderDefaults.colors(
                            thumbColor = YouCutOrange,
                            activeTrackColor = YouCutOrange,
                            inactiveTrackColor = Color.DarkGray
                        )
                    )
                }

                // Fade Out Box
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF1E1E28))
                        .padding(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Fade Out".localize(appLanguage), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        Text("%.1fs".format(fadeOutDuration), color = YouCutOrange, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    Slider(
                        value = fadeOutDuration,
                        onValueChange = {
                            fadeOutDuration = it
                            applyChange()
                        },
                        valueRange = 0.0f..3.0f,
                        colors = SliderDefaults.colors(
                            thumbColor = YouCutOrange,
                            activeTrackColor = YouCutOrange,
                            inactiveTrackColor = Color.DarkGray
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Volume Boost Slider (0% - 200%)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF1E1E28))
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Clip Volume & Boost".localize(appLanguage), color = Color.LightGray, fontSize = 12.sp)
                    Text("${(clipVolume * 100).toInt()}%", color = YouCutOrange, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                Slider(
                    value = clipVolume,
                    onValueChange = {
                        clipVolume = it
                        onVolumeChanged(it)
                    },
                    valueRange = 0f..2.0f,
                    colors = SliderDefaults.colors(
                        thumbColor = YouCutOrange,
                        activeTrackColor = YouCutOrange,
                        inactiveTrackColor = Color.DarkGray
                    )
                )
            }
        }
    }
}
