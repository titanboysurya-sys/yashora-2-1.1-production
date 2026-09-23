package com.ritvyom.yashoraReelgenerator.presentation.components

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ritvyom.yashoraReelgenerator.domain.models.*
import com.ritvyom.yashoraReelgenerator.presentation.utils.localize

/**
 * Live Watermark Overlay rendered on top of the video player or preview container.
 */
@Composable
fun WatermarkOverlay(
    config: WatermarkConfig,
    modifier: Modifier = Modifier
) {
    if (!config.isEnabled) return

    val rawText = config.text.trim()
    val displayText = if (rawText.isNotEmpty()) rawText else "Yashora AI Reel Engine"

    val alignment = when (config.position) {
        WatermarkPosition.BOTTOM_LEFT -> Alignment.BottomStart
        WatermarkPosition.BOTTOM_RIGHT -> Alignment.BottomEnd
        WatermarkPosition.BOTTOM_CENTER -> Alignment.BottomCenter
        WatermarkPosition.TOP_LEFT -> Alignment.TopStart
        WatermarkPosition.TOP_RIGHT -> Alignment.TopEnd
        WatermarkPosition.TOP_CENTER -> Alignment.TopCenter
        WatermarkPosition.CENTER -> Alignment.Center
    }

    val textAlign = when (config.position) {
        WatermarkPosition.BOTTOM_LEFT, WatermarkPosition.TOP_LEFT -> TextAlign.Start
        WatermarkPosition.BOTTOM_RIGHT, WatermarkPosition.TOP_RIGHT -> TextAlign.End
        else -> TextAlign.Center
    }

    val fontFamily = when (config.fontFamily) {
        WatermarkFontFamily.SANS_SERIF -> FontFamily.SansSerif
        WatermarkFontFamily.BOLD_MODERN -> FontFamily.SansSerif
        WatermarkFontFamily.SERIF -> FontFamily.Serif
        WatermarkFontFamily.MONOSPACE -> FontFamily.Monospace
        WatermarkFontFamily.CURSIVE -> FontFamily.Cursive
    }

    val fontWeight = when (config.fontFamily) {
        WatermarkFontFamily.BOLD_MODERN -> FontWeight.Black
        WatermarkFontFamily.SERIF -> FontWeight.Normal
        WatermarkFontFamily.MONOSPACE -> FontWeight.Medium
        else -> FontWeight.SemiBold
    }

    val baseColor = try {
        Color(android.graphics.Color.parseColor(config.colorHex))
    } catch (e: Exception) {
        Color.White
    }

    val textColor = baseColor.copy(alpha = config.opacity.coerceIn(0.1f, 1.0f))

    val shadowStyle = when (config.style) {
        WatermarkStyle.SHADOW -> Shadow(
            color = Color.Black.copy(alpha = 0.85f),
            offset = Offset(2f, 2f),
            blurRadius = 4f
        )
        WatermarkStyle.GLOW -> Shadow(
            color = baseColor.copy(alpha = 0.9f),
            offset = Offset(0f, 0f),
            blurRadius = 12f
        )
        else -> Shadow.None
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = alignment
    ) {
        val padX = 14.dp
        val padY = 14.dp

        val contentModifier = when (config.style) {
            WatermarkStyle.PILL_BADGE -> {
                Modifier
                    .padding(horizontal = padX, vertical = padY)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black.copy(alpha = (config.opacity * 0.65f).coerceIn(0.25f, 0.85f)))
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            }
            else -> {
                Modifier.padding(horizontal = padX, vertical = padY)
            }
        }

        Text(
            text = displayText,
            color = textColor,
            fontSize = config.fontSizeSp.sp,
            fontFamily = fontFamily,
            fontWeight = fontWeight,
            fontStyle = if (config.fontFamily == WatermarkFontFamily.CURSIVE) FontStyle.Italic else FontStyle.Normal,
            textAlign = textAlign,
            style = LocalTextStyle.current.copy(shadow = shadowStyle),
            modifier = contentModifier
        )
    }
}

/**
 * Watermark Settings Modal Bottom Sheet allowing full customization of:
 * - Enable / Disable watermark
 * - Custom brand / channel name text
 * - Screen position (Corners, Center, Top, Bottom)
 * - Font styling & Typography
 * - Palette colors
 * - Opacity & Size
 * - Badge & Glow visual styles
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WatermarkSettingsSheet(
    config: WatermarkConfig,
    appLanguage: String,
    onConfigChange: (WatermarkConfig) -> Unit,
    onDismiss: () -> Unit
) {
    var tempConfig by remember(config) { mutableStateOf(config) }
    val focusManager = LocalFocusManager.current
    val scrollState = rememberScrollState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
                .verticalScroll(scrollState)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.BrandingWatermark,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Watermark Settings".localize(appLanguage),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Personalize your reel branding & credits".localize(appLanguage),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close".localize(appLanguage))
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Master Enable/Disable Card
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (tempConfig.isEnabled)
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                    else
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = if (tempConfig.isEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = if (tempConfig.isEnabled) Icons.Default.Verified else Icons.Default.VisibilityOff,
                                contentDescription = null,
                                tint = if (tempConfig.isEnabled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Show Watermark on Video".localize(appLanguage),
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (tempConfig.isEnabled)
                                    "Watermark is visible on preview and final export".localize(appLanguage)
                                else
                                    "Clean video with no watermark will be rendered".localize(appLanguage),
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Switch(
                        checked = tempConfig.isEnabled,
                        onCheckedChange = {
                            tempConfig = tempConfig.copy(isEnabled = it)
                            onConfigChange(tempConfig)
                        }
                    )
                }
            }

            // Live Interactive Preview Box
            AnimatedVisibility(visible = tempConfig.isEnabled) {
                Column {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Live Preview".localize(appLanguage),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(110.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0xFF1E182A))
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp))
                    ) {
                        // Simulated subtle video gradient
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    androidx.compose.ui.graphics.Brush.verticalGradient(
                                        listOf(Color(0xFF2A1C3C), Color(0xFF0F0B18))
                                    )
                                )
                        )
                        Row(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.PlayCircle, contentDescription = null, tint = Color.White.copy(alpha = 0.35f), modifier = Modifier.size(28.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Sample Video Background".localize(appLanguage),
                                color = Color.White.copy(alpha = 0.35f),
                                fontSize = 11.sp
                            )
                        }

                        // The actual watermark rendered live
                        WatermarkOverlay(
                            config = tempConfig,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    // 1. Watermark Text Input
                    Text(
                        text = "Watermark Text / Channel Handle".localize(appLanguage),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = tempConfig.text,
                        onValueChange = {
                            tempConfig = tempConfig.copy(text = it)
                            onConfigChange(tempConfig)
                        },
                        placeholder = { Text("e.g., @MyChannel, Yashora, TechShorts") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        leadingIcon = {
                            Icon(Icons.Default.Edit, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        },
                        trailingIcon = {
                            if (tempConfig.text.isNotEmpty()) {
                                IconButton(onClick = {
                                    tempConfig = tempConfig.copy(text = "")
                                    onConfigChange(tempConfig)
                                }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear")
                                }
                            }
                        },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Quick Text Suggestion Chips
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val presets = listOf(
                            "My Channel",
                            "@YourHandle",
                            "Yashora AI Reel Engine",
                            "Official Reel",
                            "Created with Yashora"
                        )
                        items(presets) { preset ->
                            AssistChip(
                                onClick = {
                                    tempConfig = tempConfig.copy(text = preset)
                                    onConfigChange(tempConfig)
                                },
                                label = { Text(preset, fontSize = 11.sp) },
                                shape = RoundedCornerShape(8.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    // 2. Position Selection
                    Text(
                        text = "Position on Screen".localize(appLanguage),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        val posRows = listOf(
                            listOf(WatermarkPosition.TOP_LEFT, WatermarkPosition.TOP_CENTER, WatermarkPosition.TOP_RIGHT),
                            listOf(WatermarkPosition.CENTER),
                            listOf(WatermarkPosition.BOTTOM_LEFT, WatermarkPosition.BOTTOM_CENTER, WatermarkPosition.BOTTOM_RIGHT)
                        )
                        posRows.forEach { rowItems ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                rowItems.forEach { pos ->
                                    val isSelected = tempConfig.position == pos
                                    val icon = when (pos) {
                                        WatermarkPosition.TOP_LEFT -> Icons.Default.NorthWest
                                        WatermarkPosition.TOP_CENTER -> Icons.Default.North
                                        WatermarkPosition.TOP_RIGHT -> Icons.Default.NorthEast
                                        WatermarkPosition.CENTER -> Icons.Default.FilterCenterFocus
                                        WatermarkPosition.BOTTOM_LEFT -> Icons.Default.SouthWest
                                        WatermarkPosition.BOTTOM_CENTER -> Icons.Default.South
                                        WatermarkPosition.BOTTOM_RIGHT -> Icons.Default.SouthEast
                                    }
                                    Card(
                                        shape = RoundedCornerShape(10.dp),
                                        colors = CardDefaults.cardColors(
                                            containerColor = if (isSelected)
                                                MaterialTheme.colorScheme.primaryContainer
                                            else
                                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                        ),
                                        border = if (isSelected)
                                            androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
                                        else null,
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable {
                                                tempConfig = tempConfig.copy(position = pos)
                                                onConfigChange(tempConfig)
                                            }
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 10.dp, horizontal = 6.dp),
                                            horizontalArrangement = Arrangement.Center,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = icon,
                                                contentDescription = null,
                                                tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = pos.displayName.localize(appLanguage),
                                                fontSize = 11.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    // 3. Color Selection
                    Text(
                        text = "Watermark Color".localize(appLanguage),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val colorPalette = listOf(
                            Pair("#FFFFFF", "White"),
                            Pair("#FFFF00", "Yellow"),
                            Pair("#00F0FF", "Cyan"),
                            Pair("#39FF14", "Neon Green"),
                            Pair("#FF007F", "Hot Pink"),
                            Pair("#FFD700", "Gold"),
                            Pair("#FF5722", "Orange"),
                            Pair("#E0B0FF", "Lavender"),
                            Pair("#000000", "Black")
                        )
                        items(colorPalette) { (hex, _) ->
                            val isSelected = tempConfig.colorHex.equals(hex, ignoreCase = true)
                            val color = try { Color(android.graphics.Color.parseColor(hex)) } catch (e: Exception) { Color.White }
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(color)
                                    .border(
                                        width = if (isSelected) 3.dp else 1.dp,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Gray.copy(alpha = 0.5f),
                                        shape = CircleShape
                                    )
                                    .clickable {
                                        tempConfig = tempConfig.copy(colorHex = hex)
                                        onConfigChange(tempConfig)
                                    }
                            ) {
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = if (hex == "#FFFFFF" || hex == "#FFFF00" || hex == "#00F0FF" || hex == "#39FF14" || hex == "#FFD700" || hex == "#E0B0FF") Color.Black else Color.White,
                                        modifier = Modifier
                                            .size(18.dp)
                                            .align(Alignment.Center)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    // 4. Font Family Style
                    Text(
                        text = "Font Family & Typography".localize(appLanguage),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(WatermarkFontFamily.values()) { fontItem ->
                            val isSelected = tempConfig.fontFamily == fontItem
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    tempConfig = tempConfig.copy(fontFamily = fontItem)
                                    onConfigChange(tempConfig)
                                },
                                label = {
                                    Text(
                                        text = fontItem.displayName.localize(appLanguage),
                                        fontSize = 12.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                shape = RoundedCornerShape(8.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    // 5. Visual Decoration Style
                    Text(
                        text = "Watermark Visual Effect".localize(appLanguage),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(WatermarkStyle.values()) { styleItem ->
                            val isSelected = tempConfig.style == styleItem
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    tempConfig = tempConfig.copy(style = styleItem)
                                    onConfigChange(tempConfig)
                                },
                                label = {
                                    Text(
                                        text = styleItem.displayName.localize(appLanguage),
                                        fontSize = 12.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                shape = RoundedCornerShape(8.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    // 6. Opacity & Transparency Slider
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Opacity / Transparency".localize(appLanguage),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "${(tempConfig.opacity * 100).toInt()}%",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Slider(
                        value = tempConfig.opacity,
                        onValueChange = {
                            tempConfig = tempConfig.copy(opacity = it)
                            onConfigChange(tempConfig)
                        },
                        valueRange = 0.2f..1.0f,
                        steps = 8,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // 7. Font Size Slider
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Watermark Size".localize(appLanguage),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "${tempConfig.fontSizeSp} sp",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Slider(
                        value = tempConfig.fontSizeSp.toFloat(),
                        onValueChange = {
                            tempConfig = tempConfig.copy(fontSizeSp = it.toInt())
                            onConfigChange(tempConfig)
                        },
                        valueRange = 10f..28f,
                        steps = 8,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        val reset = WatermarkConfig(
                            isEnabled = false,
                            text = "",
                            position = WatermarkPosition.BOTTOM_RIGHT,
                            fontSizeSp = 14,
                            fontFamily = WatermarkFontFamily.SANS_SERIF,
                            colorHex = "#FFFFFF",
                            opacity = 0.75f,
                            style = WatermarkStyle.SHADOW
                        )
                        tempConfig = reset
                        onConfigChange(reset)
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Disable".localize(appLanguage), fontSize = 12.sp)
                }

                Button(
                    onClick = {
                        onConfigChange(tempConfig)
                        onDismiss()
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1.5f)
                ) {
                    Icon(Icons.Default.Done, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Apply Changes".localize(appLanguage), fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
