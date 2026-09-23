package com.ritvyom.yashoraReelgenerator.presentation.components.editor

import android.graphics.Color as AndroidColor
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ritvyom.yashoraReelgenerator.presentation.utils.localize

/**
 * Live Color Picker Overlay for real-time video text & sticker color changes.
 * Updates colors instantly on the video canvas as the user drags along the rainbow spectrum
 * or taps quick presets.
 */
@Composable
fun LiveColorPickerOverlay(
    currentColorHex: String,
    onColorChanged: (String) -> Unit,
    onClose: () -> Unit,
    appLanguage: String = "English",
    modifier: Modifier = Modifier
) {
    val activeColor = remember(currentColorHex) {
        try {
            Color(AndroidColor.parseColor(currentColorHex))
        } catch (e: Exception) {
            Color.White
        }
    }

    var currentHue by remember(currentColorHex) {
        val hsv = FloatArray(3)
        try {
            AndroidColor.colorToHSV(AndroidColor.parseColor(currentColorHex), hsv)
            mutableFloatStateOf(hsv[0])
        } catch (e: Exception) {
            mutableFloatStateOf(0f)
        }
    }

    var currentSat by remember(currentColorHex) {
        val hsv = FloatArray(3)
        try {
            AndroidColor.colorToHSV(AndroidColor.parseColor(currentColorHex), hsv)
            mutableFloatStateOf(if (hsv[1] == 0f) 1f else hsv[1])
        } catch (e: Exception) {
            mutableFloatStateOf(1f)
        }
    }

    var currentVal by remember(currentColorHex) {
        val hsv = FloatArray(3)
        try {
            AndroidColor.colorToHSV(AndroidColor.parseColor(currentColorHex), hsv)
            mutableFloatStateOf(if (hsv[2] == 0f) 1f else hsv[2])
        } catch (e: Exception) {
            mutableFloatStateOf(1f)
        }
    }

    val rainbowColors = remember {
        listOf(
            Color(0xFFFF0000), // Red
            Color(0xFFFF7F00), // Orange
            Color(0xFFFFFF00), // Yellow
            Color(0xFF00FF00), // Green
            Color(0xFF00FFFF), // Cyan
            Color(0xFF0000FF), // Blue
            Color(0xFF8B00FF), // Indigo
            Color(0xFFFF00FF), // Magenta
            Color(0xFFFF0000)  // Red loop
        )
    }

    val presetColors = remember {
        listOf(
            "#FFFFFF", "#FFFF00", "#FF5722", "#00E5FF",
            "#FF007F", "#00E676", "#9C27B0", "#000000",
            "#FFD700", "#2979FF", "#E040FB", "#00B0FF",
            "#76FF03", "#FF1744", "#F50057", "#FF9100"
        )
    }

    Card(
        modifier = modifier
            .fillMaxWidth(0.94f)
            .wrapContentHeight(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xF214141E)),
        border = BorderStroke(1.5.dp, YouCutOrange.copy(alpha = 0.85f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 10.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Header: Live Color Badge, Hex Code and Dismiss
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(activeColor)
                            .border(1.5.dp, Color.White, CircleShape)
                    )
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "Live Color".localize(appLanguage),
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = YouCutOrange.copy(alpha = 0.25f)
                            ) {
                                Text(
                                    text = "LIVE",
                                    fontSize = 8.5.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = YouCutOrange,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                        }
                        Text(
                            text = currentColorHex.uppercase(),
                            color = Color(0xFFB0B0C0),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onClose,
                        modifier = Modifier
                            .size(28.dp)
                            .background(YouCutOrange, CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Done".localize(appLanguage),
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            // Real-Time Hue Rainbow Spectrum Slider
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = "Drag finger to pick exact live shade:".localize(appLanguage),
                    color = Color(0xFFC0C0D0),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium
                )
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(28.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .border(1.dp, Color.White.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
                ) {
                    val barWidthPx = constraints.maxWidth.toFloat().coerceAtLeast(1f)

                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(barWidthPx) {
                                detectTapGestures { offset ->
                                    val frac = (offset.x / barWidthPx).coerceIn(0f, 1f)
                                    currentHue = frac * 360f
                                    val hsv = floatArrayOf(currentHue, currentSat, currentVal)
                                    val c = AndroidColor.HSVToColor(hsv)
                                    val hex = String.format("#%06X", 0xFFFFFF and c)
                                    onColorChanged(hex)
                                }
                            }
                            .pointerInput(barWidthPx) {
                                detectDragGestures { change, _ ->
                                    change.consume()
                                    val frac = (change.position.x / barWidthPx).coerceIn(0f, 1f)
                                    currentHue = frac * 360f
                                    val hsv = floatArrayOf(currentHue, currentSat, currentVal)
                                    val c = AndroidColor.HSVToColor(hsv)
                                    val hex = String.format("#%06X", 0xFFFFFF and c)
                                    onColorChanged(hex)
                                }
                            }
                    ) {
                        drawRect(brush = Brush.horizontalGradient(rainbowColors))
                        val thumbX = (currentHue / 360f).coerceIn(0f, 1f) * size.width
                        // Draw outer glow and inner ring for thumb
                        drawCircle(
                            color = Color.Black.copy(alpha = 0.5f),
                            radius = 12f,
                            center = Offset(thumbX, size.height / 2f)
                        )
                        drawCircle(
                            color = Color.White,
                            radius = 10f,
                            center = Offset(thumbX, size.height / 2f)
                        )
                        drawCircle(
                            color = activeColor,
                            radius = 6.5f,
                            center = Offset(thumbX, size.height / 2f)
                        )
                    }
                }
            }

            // Quick Preset Vivid Chips
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Presets:".localize(appLanguage),
                    color = Color(0xFFC0C0D0),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium
                )
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(presetColors) { hex ->
                        val isSelected = currentColorHex.equals(hex, ignoreCase = true)
                        val chipColor = try {
                            Color(AndroidColor.parseColor(hex))
                        } catch (e: Exception) {
                            Color.White
                        }

                        Box(
                            modifier = Modifier
                                .size(26.dp)
                                .clip(CircleShape)
                                .background(chipColor)
                                .border(
                                    width = if (isSelected) 2.2.dp else 0.8.dp,
                                    color = if (isSelected) YouCutOrange else Color.White.copy(alpha = 0.4f),
                                    shape = CircleShape
                                )
                                .clickable {
                                    val hsv = FloatArray(3)
                                    try {
                                        AndroidColor.colorToHSV(AndroidColor.parseColor(hex), hsv)
                                        currentHue = hsv[0]
                                        currentSat = if (hsv[1] == 0f) 1f else hsv[1]
                                        currentVal = if (hsv[2] == 0f) 1f else hsv[2]
                                    } catch (e: Exception) {
                                        // Ignore
                                    }
                                    onColorChanged(hex)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSelected) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(if (hex.equals("#FFFFFF", ignoreCase = true)) Color.Black else Color.White)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
