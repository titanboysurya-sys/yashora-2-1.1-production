package com.ritvyom.yashoraReelgenerator.presentation.components.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ritvyom.yashoraReelgenerator.presentation.utils.localize

/**
 * Signature YouCut vibrant orange accent color: #FF5722 / #FF4500
 */
val YouCutOrange = Color(0xFFFF5722)
val YouCutOrangeDark = Color(0xFFE64A19)
val YouCutOrangeLight = Color(0xFFFFCCBC)
val YouCutBarBg = Color(0xFF16161A)

/**
 * Functional categories for clean organization and small-screen discovery
 */
enum class ToolCategory(val title: String, val icon: ImageVector) {
    ALL("All", Icons.Default.Apps),
    EDIT("Edit", Icons.Default.ContentCut),
    TRANSFORM("Transform", Icons.Default.Crop),
    ANIMATION("Animation", Icons.Default.MotionPhotosAuto),
    EFFECTS("Effects", Icons.Default.AutoFixHigh),
    TEXT("Text", Icons.Default.TextFields),
    AUDIO("Audio", Icons.Default.MusicNote),
    LAYERS("Layers", Icons.Default.Layers)
}

enum class EditorTool(
    val title: String,
    val icon: ImageVector,
    val category: ToolCategory = ToolCategory.EDIT,
    val description: String = ""
) {
    TRIM("TRIM", Icons.Default.ContentCut, ToolCategory.EDIT, "Trim, cut, split and manage clip lengths"),
    TRANSITION("TRANSITION", Icons.Default.Animation, ToolCategory.EDIT, "Add smooth cinematic transitions between clips"),
    FILTER("FILTER", Icons.Default.ColorLens, ToolCategory.EFFECTS, "Preset cinematic filters and grading styles"),
    ADJUST("ADJUST", Icons.Default.Tune, ToolCategory.EFFECTS, "Full color grading: brightness, contrast, HSL"),
    EFFECT("EFFECT", Icons.Default.AutoFixHigh, ToolCategory.EFFECTS, "Dynamic visual effects, glitch, and camera shake"),
    ANIMATION("ANIMATION", Icons.Default.MotionPhotosAuto, ToolCategory.ANIMATION, "IN, OUT, and COMBO clip motion keyframes"),
    TEXT("TEXT", Icons.Default.TextFields, ToolCategory.TEXT, "Add customizable titles and text overlays"),
    STICKER("STICKER", Icons.Default.SentimentSatisfiedAlt, ToolCategory.LAYERS, "Overlay stickers and emoji elements"),
    LAYERS("LAYERS", Icons.Default.Layers, ToolCategory.LAYERS, "Manage front-to-back z-index depth hierarchy"),
    PIP("PIP", Icons.Default.PictureInPicture, ToolCategory.LAYERS, "Picture-in-picture video and image overlays"),
    SPEED("SPEED", Icons.Default.Speed, ToolCategory.EDIT, "Fast motion, slow motion (0.2x to 5.0x) speed curve"),
    VOICE_FX("VOICE FX", Icons.Default.RecordVoiceOver, ToolCategory.AUDIO, "Vocal effects: robot, chipmunk, deep bass"),
    MUSIC("MUSIC", Icons.Default.MusicNote, ToolCategory.AUDIO, "Soundtrack library, background audio & SFX"),
    RECORD("RECORD", Icons.Default.Mic, ToolCategory.AUDIO, "Live studio microphone voiceover recorder"),
    VOLUME("VOLUME", Icons.Default.VolumeUp, ToolCategory.AUDIO, "Clip audio level, gain boost and mute controls"),
    FREEZE("FREEZE", Icons.Default.AcUnit, ToolCategory.EDIT, "Freeze frame momentary pause effect"),
    REVERSE("REVERSE", Icons.Default.FastRewind, ToolCategory.EDIT, "Reverse playback direction"),
    TEMPLATE("TEMPLATE", Icons.Default.MovieCreation, ToolCategory.LAYERS, "Quick social templates and canvas layouts"),
    ENHANCE("ENHANCE", Icons.Default.AutoAwesome, ToolCategory.EFFECTS, "AI video enhance and instant vibrancy"),
    CAPTIONS("CAPTIONS", Icons.Default.Subtitles, ToolCategory.TEXT, "Automatic subtitles and animated captions"),
    BG("BG", Icons.Default.Wallpaper, ToolCategory.TRANSFORM, "Background colors, blur canvas and aspect ratios"),
    CROP("CROP", Icons.Default.Crop, ToolCategory.TRANSFORM, "Crop framing and custom aspect ratios"),
    ROTATE("ROTATE", Icons.Default.RotateRight, ToolCategory.TRANSFORM, "Rotate video orientation by 90 degrees"),
    FLIP("FLIP", Icons.Default.Flip, ToolCategory.TRANSFORM, "Horizontal mirror flip"),
    CHROMA("CHROMA", Icons.Default.Palette, ToolCategory.EFFECTS, "Green screen, blue screen and color keying cutout"),
    MASK("MASK", Icons.Default.FilterFrames, ToolCategory.EFFECTS, "Linear, mirror, circle, rectangle and heart shape masks"),
    REPLACE("REPLACE", Icons.Default.SwapHoriz, ToolCategory.EDIT, "Swap active clip with another video or photo")
}

/**
 * Transport controls row matching YouCut directly below the video canvas:
 * Undo (↺), Redo (↻), Seek to Start (⏮), Big Play/Pause (▶/⏸), Fullscreen (⛶)
 * Dynamically adapts spacing on small screens (<360dp) to prevent crowding.
 */
@Composable
fun YouCutTransportBar(
    isPlaying: Boolean,
    canUndo: Boolean,
    canRedo: Boolean,
    appLanguage: String,
    onUndoClick: () -> Unit,
    onRedoClick: () -> Unit,
    onSeekStartClick: () -> Unit,
    onPlayPauseClick: () -> Unit,
    onFullscreenClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val configuration = LocalConfiguration.current
    val isVerySmall = configuration.screenWidthDp <= 340
    val horizontalPadding = if (isVerySmall) 8.dp else 16.dp
    val spacingSmall = if (isVerySmall) 6.dp else 12.dp

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = horizontalPadding, vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left: Undo / Redo
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacingSmall)
        ) {
            IconButton(
                onClick = onUndoClick,
                enabled = canUndo,
                modifier = Modifier
                    .sizeIn(minWidth = 36.dp, minHeight = 36.dp)
                    .testTag("transport_undo_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Undo,
                    contentDescription = "Undo".localize(appLanguage),
                    tint = if (canUndo) Color.White.copy(alpha = 0.9f) else Color.Gray.copy(alpha = 0.4f),
                    modifier = Modifier.size(20.dp)
                )
            }

            IconButton(
                onClick = onRedoClick,
                enabled = canRedo,
                modifier = Modifier
                    .sizeIn(minWidth = 36.dp, minHeight = 36.dp)
                    .testTag("transport_redo_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Redo,
                    contentDescription = "Redo".localize(appLanguage),
                    tint = if (canRedo) Color.White.copy(alpha = 0.9f) else Color.Gray.copy(alpha = 0.4f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // Center: Seek to start & Play / Pause
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(if (isVerySmall) 10.dp else 16.dp)
        ) {
            IconButton(
                onClick = onSeekStartClick,
                modifier = Modifier
                    .sizeIn(minWidth = 36.dp, minHeight = 36.dp)
                    .testTag("transport_seek_start_button")
            ) {
                Icon(
                    imageVector = Icons.Default.SkipPrevious,
                    contentDescription = "Seek to Start".localize(appLanguage),
                    tint = Color.White.copy(alpha = 0.9f),
                    modifier = Modifier.size(24.dp)
                )
            }

            IconButton(
                onClick = onPlayPauseClick,
                modifier = Modifier
                    .size(if (isVerySmall) 38.dp else 42.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.08f))
                    .testTag("transport_play_pause_button")
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Pause".localize(appLanguage) else "Play".localize(appLanguage),
                    tint = Color.White,
                    modifier = Modifier.size(if (isVerySmall) 24.dp else 28.dp)
                )
            }
        }

        // Right: Fullscreen preview button
        IconButton(
            onClick = onFullscreenClick,
            modifier = Modifier
                .sizeIn(minWidth = 36.dp, minHeight = 36.dp)
                .testTag("transport_fullscreen_button")
        ) {
            Icon(
                imageVector = Icons.Default.Fullscreen,
                contentDescription = "Fullscreen".localize(appLanguage),
                tint = Color.White.copy(alpha = 0.9f),
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

/**
 * Signature YouCut scrollable horizontal tool action bar with orange icons
 * and bold uppercase labels underneath.
 *
 * Adaptive features:
 * 1. Includes a prominent "ALL TOOLS / MORE" hub button for 1-tap discovery on small screens.
 * 2. Guaranteed 48dp touch targets on every single button.
 * 3. Never clips or drops any tool regardless of screen size.
 */
@Composable
fun YouCutToolActionRow(
    activeTool: EditorTool?,
    appLanguage: String,
    onToolClick: (EditorTool) -> Unit,
    modifier: Modifier = Modifier
) {
    var showAllToolsHub by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(YouCutBarBg)
            .horizontalScroll(scrollState)
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .testTag("youcut_tool_action_row"),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Prominent "ALL TOOLS" Hub Button
        Column(
            modifier = Modifier
                .width(58.dp)
                .sizeIn(minHeight = 48.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF232330))
                .clickable { showAllToolsHub = true }
                .padding(vertical = 4.dp, horizontal = 2.dp)
                .testTag("tool_more_hub_button"),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(YouCutOrange.copy(alpha = 0.25f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Apps,
                    contentDescription = "All Tools".localize(appLanguage),
                    tint = YouCutOrange,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = "MORE".localize(appLanguage),
                color = YouCutOrange,
                fontSize = 9.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                textAlign = TextAlign.Center
            )
        }

        // Complete set of 25 Editor Tools
        EditorTool.values().forEach { tool ->
            val isSelected = activeTool == tool
            YouCutToolButton(
                tool = tool,
                isSelected = isSelected,
                appLanguage = appLanguage,
                onClick = { onToolClick(tool) }
            )
        }
    }

    // Modal Hub Sheet when user taps "MORE"
    if (showAllToolsHub) {
        AllToolsHubBottomSheet(
            activeTool = activeTool,
            appLanguage = appLanguage,
            onToolSelected = { selected ->
                showAllToolsHub = false
                onToolClick(selected)
            },
            onDismiss = { showAllToolsHub = false }
        )
    }
}

@Composable
fun YouCutToolButton(
    tool: EditorTool,
    isSelected: Boolean,
    appLanguage: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val localizedTitle = tool.title.localize(appLanguage)
    val dynamicFontSize = when {
        localizedTitle.length >= 10 -> 7.5.sp
        localizedTitle.length >= 8 -> 8.sp
        else -> 9.sp
    }

    Column(
        modifier = modifier
            .widthIn(min = 58.dp, max = 66.dp)
            .sizeIn(minHeight = 48.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp, horizontal = 2.dp)
            .testTag("tool_button_${tool.name.lowercase()}"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(if (isSelected) YouCutOrange.copy(alpha = 0.22f) else Color.Transparent),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = tool.icon,
                contentDescription = localizedTitle,
                tint = if (isSelected) YouCutOrange else YouCutOrange.copy(alpha = 0.92f),
                modifier = Modifier.size(24.dp)
            )
        }

        Spacer(modifier = Modifier.height(2.dp))

        Text(
            text = localizedTitle,
            color = if (isSelected) Color.White else YouCutOrange.copy(alpha = 0.95f),
            fontSize = dynamicFontSize,
            fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * Categorized All Tools & Features Hub Sheet
 * Organizes every feature into standard creative suites:
 * EDIT, TRANSFORM, ANIMATION, EFFECTS, TEXT, AUDIO, LAYERS
 *
 * Guarantees zero feature loss on small devices, providing instant categorized access.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AllToolsHubBottomSheet(
    activeTool: EditorTool?,
    appLanguage: String,
    onToolSelected: (EditorTool) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedCategory by remember { mutableStateOf(ToolCategory.ALL) }
    val configuration = LocalConfiguration.current
    val isCompactHeight = configuration.screenHeightDp < 600

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF14141E),
        tonalElevation = 8.dp,
        modifier = Modifier.testTag("all_tools_hub_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp)
                .padding(bottom = 20.dp)
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Dashboard,
                        contentDescription = null,
                        tint = YouCutOrange,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "All Tools & Features".localize(appLanguage),
                        color = Color.White,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("close_tools_hub_button")
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.LightGray)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Category Filter Pills
            val categoryScroll = rememberScrollState()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(categoryScroll),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                ToolCategory.values().forEach { cat ->
                    val isCatSelected = selectedCategory == cat
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = if (isCatSelected) YouCutOrange else Color(0xFF222230),
                        modifier = Modifier
                            .clickable { selectedCategory = cat }
                            .testTag("category_chip_${cat.name.lowercase()}")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = cat.icon,
                                contentDescription = null,
                                tint = if (isCatSelected) Color.White else Color.Gray,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = cat.title.localize(appLanguage),
                                color = if (isCatSelected) Color.White else Color.LightGray,
                                fontSize = 11.sp,
                                fontWeight = if (isCatSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Filtered Tools Grid
            val filteredTools = remember(selectedCategory) {
                if (selectedCategory == ToolCategory.ALL) {
                    EditorTool.values().toList()
                } else {
                    EditorTool.values().filter { it.category == selectedCategory }
                }
            }

            val gridHeight = if (isCompactHeight) 240.dp else 320.dp
            val scrollGridState = rememberScrollState()

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = gridHeight)
                    .verticalScroll(scrollGridState),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Display tools categorized or listed
                val categoriesToShow = if (selectedCategory == ToolCategory.ALL) {
                    ToolCategory.values().filter { it != ToolCategory.ALL }
                } else {
                    listOf(selectedCategory)
                }

                categoriesToShow.forEach { cat ->
                    val toolsInCat = EditorTool.values().filter { it.category == cat }
                    if (toolsInCat.isNotEmpty()) {
                        Text(
                            text = cat.title.uppercase(),
                            color = YouCutOrange,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )

                        toolsInCat.chunked(3).forEach { rowTools ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                rowTools.forEach { tool ->
                                    val isCurrent = activeTool == tool
                                    Card(
                                        shape = RoundedCornerShape(10.dp),
                                        colors = CardDefaults.cardColors(
                                            containerColor = if (isCurrent) YouCutOrange.copy(alpha = 0.25f) else Color(0xFF1F1F2C)
                                        ),
                                        border = androidx.compose.foundation.BorderStroke(
                                            width = if (isCurrent) 1.5.dp else 0.5.dp,
                                            color = if (isCurrent) YouCutOrange else Color.White.copy(alpha = 0.08f)
                                        ),
                                        modifier = Modifier
                                            .weight(1f)
                                            .sizeIn(minHeight = 56.dp)
                                            .clickable { onToolSelected(tool) }
                                            .testTag("hub_item_${tool.name.lowercase()}")
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(28.dp)
                                                    .clip(CircleShape)
                                                    .background(if (isCurrent) YouCutOrange else Color.White.copy(alpha = 0.06f)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = tool.icon,
                                                    contentDescription = tool.title,
                                                    tint = if (isCurrent) Color.White else YouCutOrange,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Column {
                                                Text(
                                                    text = tool.title.localize(appLanguage),
                                                    color = Color.White,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = tool.category.title,
                                                    color = Color.Gray,
                                                    fontSize = 9.sp,
                                                    maxLines = 1
                                                )
                                            }
                                        }
                                    }
                                }
                                // Fill remaining space if row is incomplete
                                if (rowTools.size < 3) {
                                    repeat(3 - rowTools.size) {
                                        Spacer(modifier = Modifier.weight(1f))
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
