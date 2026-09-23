package com.ritvyom.yashoraReelgenerator.presentation.components

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ritvyom.yashoraReelgenerator.presentation.utils.localize
import com.ritvyom.yashoraReelgenerator.presentation.viewmodels.MainViewModel

data class VoicePresetData(
    val id: String,
    val icon: String,
    val title: String,
    val subtitle: String,
    val stability: Float,
    val similarity: Float,
    val style: Float,
    val speakerBoost: Boolean = true,
    val modelId: String = "eleven_multilingual_v2"
)

val ELEVENLABS_VOICE_PRESETS = listOf(
    VoicePresetData(
        id = "dramatic",
        icon = "🎭",
        title = "Dramatic Story",
        subtitle = "Expressive & High Emotion",
        stability = 0.30f,
        similarity = 0.85f,
        style = 0.50f,
        speakerBoost = true
    ),
    VoicePresetData(
        id = "conversational",
        icon = "💬",
        title = "Conversational",
        subtitle = "Natural & Everyday Pacing",
        stability = 0.50f,
        similarity = 0.75f,
        style = 0.15f,
        speakerBoost = true
    ),
    VoicePresetData(
        id = "news",
        icon = "🎙️",
        title = "News & Podcast",
        subtitle = "Clean, Formal & Clear",
        stability = 0.75f,
        similarity = 0.75f,
        style = 0.0f,
        speakerBoost = true
    ),
    VoicePresetData(
        id = "viral_reel",
        icon = "⚡",
        title = "Viral Reel / Ads",
        subtitle = "High Energy & Personality",
        stability = 0.25f,
        similarity = 0.90f,
        style = 0.65f,
        speakerBoost = true
    ),
    VoicePresetData(
        id = "meditation",
        icon = "🧘",
        title = "Calm Meditation",
        subtitle = "Tranquil & Monotone",
        stability = 0.85f,
        similarity = 0.70f,
        style = 0.0f,
        speakerBoost = false
    ),
    VoicePresetData(
        id = "cinematic",
        icon = "🎬",
        title = "Cinematic Movie",
        subtitle = "Rich Tone & Atmosphere",
        stability = 0.35f,
        similarity = 0.80f,
        style = 0.35f,
        speakerBoost = true
    )
)

/**
 * Settings panel for ElevenLabs Voice Parameters:
 * - Stability (0% - 100%)
 * - Similarity / Clarity Boost (0% - 100%)
 * - Style Exaggeration (0% - 100%)
 * - Neural Speaker Boost (Toggle)
 * - Model ID Selection (Multilingual v2, Turbo v2.5, Flash v2.5, Monolingual v1)
 * - Presets Bar (Dramatic, Conversational, News, Viral Reel, Meditation, Cinematic)
 * - Interactive Live Test Playground
 */
@Composable
fun ElevenLabsVoiceParametersPanel(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier,
    isCollapsible: Boolean = true,
    initiallyExpanded: Boolean = true
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val appLanguageState by viewModel.appLanguage.collectAsState()

    val modelIdState by viewModel.voxElevenModelId.collectAsState()
    val stabilityState by viewModel.voxElevenStability.collectAsState()
    val similarityState by viewModel.voxElevenSimilarity.collectAsState()
    val styleState by viewModel.voxElevenStyle.collectAsState()
    val speakerBoostState by viewModel.voxElevenSpeakerBoost.collectAsState()
    val selectedVoiceName by viewModel.voxElevenVoiceName.collectAsState()
    val isTestingVoice by viewModel.isTestingVoiceParameters.collectAsState()
    val testVoiceError by viewModel.testVoiceError.collectAsState()

    var isExpanded by remember { mutableStateOf(initiallyExpanded) }
    var testSentence by remember { mutableStateOf("Experience ultra-realistic AI voice synthesis with customizable style and stability!") }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header Row (Clickable to collapse if enabled)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (isCollapsible) Modifier.clickable { isExpanded = !isExpanded } else Modifier
                    ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "ElevenLabs Voice Tuning Parameters".localize(appLanguageState),
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Fine-tune stability, clarity, & style exaggeration".localize(appLanguageState),
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (isCollapsible) {
                    IconButton(
                        onClick = { isExpanded = !isExpanded },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = if (isExpanded) "Collapse" else "Expand",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = isExpanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column(modifier = Modifier.padding(top = 16.dp)) {
                    // 1. Quick Presets Section
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Quick Tuning Presets:".localize(appLanguageState),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        TextButton(
                            onClick = { viewModel.resetVoxElevenParametersToDefaults() },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.height(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.RestartAlt,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Reset Defaults".localize(appLanguageState), fontSize = 10.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ELEVENLABS_VOICE_PRESETS.forEach { preset ->
                            val isSelected = (Math.abs(stabilityState - preset.stability) < 0.05f) &&
                                    (Math.abs(similarityState - preset.similarity) < 0.05f) &&
                                    (Math.abs(styleState - preset.style) < 0.05f) &&
                                    (speakerBoostState == preset.speakerBoost)

                            ElevatedFilterChip(
                                selected = isSelected,
                                onClick = {
                                    viewModel.applyVoxElevenPreset(
                                        stability = preset.stability,
                                        similarity = preset.similarity,
                                        style = preset.style,
                                        speakerBoost = preset.speakerBoost,
                                        modelId = preset.modelId
                                    )
                                },
                                label = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(preset.icon, fontSize = 12.sp)
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Column {
                                            Text(preset.title.localize(appLanguageState), fontSize = 11.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                                        }
                                    }
                                },
                                colors = FilterChipDefaults.elevatedFilterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            )
                        }
                    }

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 14.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                    )

                    // 2. AI Model Selection
                    Text(
                        text = "AI Model Engine:".localize(appLanguageState),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val models = listOf(
                            "eleven_multilingual_v2" to "Multilingual v2 (29+ langs)",
                            "eleven_turbo_v2_5" to "Turbo v2.5 (Low Latency)",
                            "eleven_flash_v2_5" to "Flash v2.5 (Fast)",
                            "eleven_monolingual_v1" to "Monolingual v1 (English)"
                        )
                        models.forEach { (id, label) ->
                            FilterChip(
                                selected = modelIdState == id,
                                onClick = { viewModel.saveVoxElevenModelId(id) },
                                label = { Text(label, fontSize = 10.5.sp) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // 3. Stability Parameter Slider
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Equalizer,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Stability: ${(stabilityState * 100).toInt()}%".localize(appLanguageState),
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = when {
                                        stabilityState < 0.35f -> MaterialTheme.colorScheme.tertiaryContainer
                                        stabilityState > 0.65f -> MaterialTheme.colorScheme.secondaryContainer
                                        else -> MaterialTheme.colorScheme.primaryContainer
                                    }
                                ) {
                                    Text(
                                        text = when {
                                            stabilityState < 0.35f -> "More Dynamic & Emotional".localize(appLanguageState)
                                            stabilityState > 0.65f -> "More Stable & Consistent".localize(appLanguageState)
                                            else -> "Natural Conversational".localize(appLanguageState)
                                        },
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            Text(
                                text = "Lower values produce higher emotional expressiveness and intonation variation. Higher values make the delivery calmer and more consistent.".localize(appLanguageState),
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp)
                            )

                            Slider(
                                value = stabilityState,
                                onValueChange = { viewModel.saveVoxElevenStability(it) },
                                valueRange = 0.0f..1.0f,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // 4. Similarity / Clarity Boost Slider
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.GraphicEq,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Similarity Boost: ${(similarityState * 100).toInt()}%".localize(appLanguageState),
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = when {
                                        similarityState > 0.80f -> MaterialTheme.colorScheme.primaryContainer
                                        similarityState < 0.50f -> MaterialTheme.colorScheme.surfaceVariant
                                        else -> MaterialTheme.colorScheme.secondaryContainer
                                    }
                                ) {
                                    Text(
                                        text = when {
                                            similarityState > 0.80f -> "High Timbre Match".localize(appLanguageState)
                                            similarityState < 0.50f -> "Loose Imitation".localize(appLanguageState)
                                            else -> "Balanced Clarity (Recommended)".localize(appLanguageState)
                                        },
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            Text(
                                text = "Controls how strictly the AI adheres to the original speaker's timbre and vocal signature. 75% is the recommended default.".localize(appLanguageState),
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp)
                            )

                            Slider(
                                value = similarityState,
                                onValueChange = { viewModel.saveVoxElevenSimilarity(it) },
                                valueRange = 0.0f..1.0f,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // 5. Style Exaggeration Slider
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.AutoAwesome,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Style Exaggeration: ${(styleState * 100).toInt()}%".localize(appLanguageState),
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = when {
                                        styleState > 0.50f -> MaterialTheme.colorScheme.errorContainer
                                        styleState > 0.15f -> MaterialTheme.colorScheme.tertiaryContainer
                                        else -> MaterialTheme.colorScheme.surfaceVariant
                                    }
                                ) {
                                    Text(
                                        text = when {
                                            styleState == 0.0f -> "Off (0%) - Clean Standard".localize(appLanguageState)
                                            styleState <= 0.30f -> "Subtle Personality".localize(appLanguageState)
                                            styleState <= 0.60f -> "Expressive Flavor".localize(appLanguageState)
                                            else -> "High Exaggeration (Reels & Ads)".localize(appLanguageState)
                                        },
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            Text(
                                text = "Amplifies the speaker's vocal characteristics and emotional punch. Great for dramatic videos, viral reels, and energetic commercials.".localize(appLanguageState),
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp)
                            )

                            Slider(
                                value = styleState,
                                onValueChange = { viewModel.saveVoxElevenStyle(it) },
                                valueRange = 0.0f..1.0f,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // 6. Neural Speaker Boost Switch
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.VolumeUp,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Neural Speaker Boost".localize(appLanguageState),
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 12.5.sp
                                    )
                                }
                                Text(
                                    text = "Enhances overall volume presence and crispness across smartphone speakers.".localize(appLanguageState),
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Switch(
                                checked = speakerBoostState,
                                onCheckedChange = { viewModel.saveVoxElevenSpeakerBoost(it) }
                            )
                        }
                    }

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 14.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                    )

                    // 7. Interactive Live Voice Test Playground
                    Text(
                        text = "Live Voice Parameters Preview:".localize(appLanguageState),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    OutlinedTextField(
                        value = testSentence,
                        onValueChange = { testSentence = it },
                        label = { Text("Sample Text to Test".localize(appLanguageState), fontSize = 11.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 2,
                        textStyle = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() })
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = {
                            focusManager.clearFocus()
                            viewModel.testVoxElevenVoiceParameters(context, testSentence)
                        },
                        enabled = !isTestingVoice && testSentence.isNotBlank(),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (isTestingVoice) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Synthesizing with Current Parameters...".localize(appLanguageState), fontSize = 12.sp)
                        } else {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "Test Current Voice Settings (${selectedVoiceName.substringBefore('(').trim()})".localize(appLanguageState),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    if (!testVoiceError.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = testVoiceError ?: "",
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 10.5.sp
                        )
                    }
                }
            }
        }
    }
}
