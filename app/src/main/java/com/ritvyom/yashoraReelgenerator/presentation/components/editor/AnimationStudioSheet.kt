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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ritvyom.yashoraReelgenerator.domain.models.Scene
import com.ritvyom.yashoraReelgenerator.presentation.utils.localize

data class MotionPreset(
    val id: String,
    val name: String,
    val icon: ImageVector,
    val description: String
)

/**
 * CapCut Signature Clip & Overlay Motion Animation Studio
 * Supports IN animations, OUT animations, and COMBO loop effects with duration scaling.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnimationStudioSheet(
    currentScene: Scene,
    appLanguage: String,
    onAnimationChanged: (inAnim: String, outAnim: String, comboAnim: String, duration: Float) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) } // 0: IN, 1: OUT, 2: COMBO
    var inAnimation by remember { mutableStateOf(currentScene.inAnimation.ifEmpty { "None" }) }
    var outAnimation by remember { mutableStateOf(currentScene.outAnimation.ifEmpty { "None" }) }
    var comboAnimation by remember { mutableStateOf(currentScene.comboAnimation.ifEmpty { "None" }) }
    var animDuration by remember { mutableFloatStateOf(if (currentScene.animationDuration > 0f) currentScene.animationDuration else 0.5f) }

    val inPresets = remember {
        listOf(
            MotionPreset("None", "None", Icons.Default.Block, "No motion"),
            MotionPreset("Fade In", "Fade In", Icons.Default.Opacity, "Smooth intro dissolve"),
            MotionPreset("Zoom In", "Zoom In", Icons.Default.ZoomIn, "High-impact forward zoom"),
            MotionPreset("Zoom Out", "Zoom Out", Icons.Default.ZoomOut, "Dramatic reverse scale"),
            MotionPreset("Slide Up", "Slide Up", Icons.Default.ArrowUpward, "Glides from bottom"),
            MotionPreset("Slide Right", "Slide Right", Icons.Default.ArrowForward, "Dynamic lateral entry"),
            MotionPreset("Slide Left", "Slide Left", Icons.Default.ArrowBack, "Glides from right"),
            MotionPreset("Bounce In", "Bounce In", Icons.Default.SportsBasketball, "Playful physics bounce"),
            MotionPreset("Pop Up", "Pop Up", Icons.Default.FlashOn, "Fast snappy pop"),
            MotionPreset("Spin In", "Spin In", Icons.Default.RotateRight, "Kinetic 360 rotation"),
            MotionPreset("Flip In", "Flip In", Icons.Default.Flip, "Card 3D flip arrival")
        )
    }

    val outPresets = remember {
        listOf(
            MotionPreset("None", "None", Icons.Default.Block, "No exit"),
            MotionPreset("Fade Out", "Fade Out", Icons.Default.Opacity, "Soft exit dissolve"),
            MotionPreset("Zoom Out", "Zoom Out", Icons.Default.ZoomOut, "Shrinks away"),
            MotionPreset("Zoom In", "Zoom In", Icons.Default.ZoomIn, "Expands past camera"),
            MotionPreset("Slide Down", "Slide Down", Icons.Default.ArrowDownward, "Drops through bottom"),
            MotionPreset("Slide Left", "Slide Left", Icons.Default.ArrowBack, "Exits to the left"),
            MotionPreset("Slide Right", "Slide Right", Icons.Default.ArrowForward, "Exits to the right"),
            MotionPreset("Dissolve", "Dissolve", Icons.Default.BlurOn, "Cinematic blur exit"),
            MotionPreset("Shrink", "Shrink", Icons.Default.Compress, "Folds into dot"),
            MotionPreset("Spin Out", "Spin Out", Icons.Default.RotateLeft, "Vortex twist exit")
        )
    }

    val comboPresets = remember {
        listOf(
            MotionPreset("None", "None", Icons.Default.Block, "Static"),
            MotionPreset("Pulse", "Pulse", Icons.Default.Favorite, "Rhythmic heartbeat scaling"),
            MotionPreset("Shake", "Shake", Icons.Default.Vibration, "High energy camera shake"),
            MotionPreset("Swing", "Swing", Icons.Default.Loop, "Pendulum sway back and forth"),
            MotionPreset("Flash", "Flash", Icons.Default.Bolt, "Strobe lighting pulse"),
            MotionPreset("Wobble", "Wobble", Icons.Default.Waves, "Liquid elastic wobble"),
            MotionPreset("Zoom & Pan", "Zoom Pan", Icons.Default.CropFree, "Ken Burns slow documentary pan")
        )
    }

    fun applyChange() {
        onAnimationChanged(inAnimation, outAnimation, comboAnimation, animDuration)
    }

    val configuration = LocalConfiguration.current
    val isCompactHeight = configuration.screenHeightDp < 600
    val sheetScroll = rememberScrollState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF14141B),
        tonalElevation = 8.dp,
        modifier = Modifier.testTag("animation_studio_sheet")
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
                        imageVector = Icons.Default.MotionPhotosAuto,
                        contentDescription = null,
                        tint = YouCutOrange,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Animation Studio".localize(appLanguage),
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (inAnimation != "None" || outAnimation != "None" || comboAnimation != "None") {
                        TextButton(
                            onClick = {
                                inAnimation = "None"
                                outAnimation = "None"
                                comboAnimation = "None"
                                applyChange()
                            },
                            colors = ButtonDefaults.textButtonColors(contentColor = Color.Gray)
                        ) {
                            Text("Clear All".localize(appLanguage), fontSize = 12.sp)
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Check, contentDescription = "Done", tint = YouCutOrange)
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // CapCut Signature 3-Tab Selector: IN | OUT | COMBO
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF1E1E28))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                listOf("IN Motion", "OUT Motion", "COMBO Loop").forEachIndexed { idx, tabTitle ->
                    val isTabSelected = selectedTab == idx
                    val activePresetName = when (idx) {
                        0 -> inAnimation
                        1 -> outAnimation
                        else -> comboAnimation
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isTabSelected) YouCutOrange else Color.Transparent)
                            .clickable { selectedTab = idx }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = tabTitle.localize(appLanguage),
                                color = if (isTabSelected) Color.White else Color.LightGray,
                                fontSize = 12.sp,
                                fontWeight = if (isTabSelected) FontWeight.Bold else FontWeight.Normal
                            )
                            if (activePresetName != "None") {
                                Text(
                                    text = activePresetName,
                                    color = if (isTabSelected) Color.White.copy(alpha = 0.9f) else YouCutOrange,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Duration Slider
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF1B1B24))
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Animation Duration".localize(appLanguage),
                        color = Color.LightGray,
                        fontSize = 12.sp
                    )
                    Text(
                        text = "%.1fs".format(animDuration),
                        color = YouCutOrange,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Slider(
                    value = animDuration,
                    onValueChange = {
                        animDuration = it
                        applyChange()
                    },
                    valueRange = 0.1f..3.0f,
                    steps = 28,
                    colors = SliderDefaults.colors(
                        thumbColor = YouCutOrange,
                        activeTrackColor = YouCutOrange,
                        inactiveTrackColor = Color.DarkGray
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Preset Grid
            val currentList = when (selectedTab) {
                0 -> inPresets
                1 -> outPresets
                else -> comboPresets
            }

            val currentSelection = when (selectedTab) {
                0 -> inAnimation
                1 -> outAnimation
                else -> comboAnimation
            }

            val gridHeight = if (isCompactHeight) 170.dp else 210.dp

            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(gridHeight)
            ) {
                items(currentList) { preset ->
                    val isSelected = currentSelection == preset.id

                    Column(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isSelected) YouCutOrange.copy(alpha = 0.22f) else Color(0xFF1E1E26))
                            .border(
                                width = if (isSelected) 1.5.dp else 0.5.dp,
                                color = if (isSelected) YouCutOrange else Color.White.copy(alpha = 0.08f),
                                shape = RoundedCornerShape(10.dp)
                            )
                            .clickable {
                                when (selectedTab) {
                                    0 -> inAnimation = preset.id
                                    1 -> outAnimation = preset.id
                                    else -> comboAnimation = preset.id
                                }
                                applyChange()
                            }
                            .padding(vertical = 10.dp, horizontal = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(if (isSelected) YouCutOrange else Color.White.copy(alpha = 0.06f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = preset.icon,
                                contentDescription = preset.name,
                                tint = if (isSelected) Color.White else Color.LightGray,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = preset.name.localize(appLanguage),
                            color = if (isSelected) YouCutOrange else Color.White,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            textAlign = TextAlign.Center,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}
