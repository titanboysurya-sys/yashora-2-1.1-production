package com.ritvyom.yashoraReelgenerator.presentation.components.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
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
import kotlin.math.roundToInt

enum class AdjustParam(val label: String, val icon: ImageVector, val minVal: Float, val maxVal: Float, val defaultVal: Float) {
    BRIGHTNESS("Brightness", Icons.Default.WbSunny, -100f, 100f, 0f),
    CONTRAST("Contrast", Icons.Default.Contrast, -100f, 100f, 0f),
    SATURATION("Saturation", Icons.Default.Palette, -100f, 100f, 0f),
    EXPOSURE("Exposure", Icons.Default.Exposure, -100f, 100f, 0f),
    WARMTH("Warmth", Icons.Default.DeviceThermostat, -100f, 100f, 0f),
    TINT("Tint", Icons.Default.ColorLens, -100f, 100f, 0f),
    VIGNETTE("Vignette", Icons.Default.Vignette, 0f, 100f, 0f),
    SHARPEN("Sharpen", Icons.Default.Details, 0f, 100f, 0f),
    HIGHLIGHTS("Highlights", Icons.Default.LightMode, -100f, 100f, 0f),
    SHADOWS("Shadows", Icons.Default.DarkMode, -100f, 100f, 0f)
}

/**
 * CapCut & Filmora Pro-Grade Color Adjust Studio Sheet
 * Full HSL, Contrast, Exposure, Warmth, Vignette, and Sharpening controls.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdjustStudioSheet(
    currentScene: Scene,
    appLanguage: String,
    onAdjustChanged: (
        brightness: Float,
        contrast: Float,
        saturation: Float,
        warmth: Float,
        vignette: Float,
        exposure: Float,
        sharpen: Float,
        tint: Float,
        highlights: Float,
        shadows: Float
    ) -> Unit,
    onApplyToAll: (
        brightness: Float,
        contrast: Float,
        saturation: Float,
        warmth: Float,
        vignette: Float,
        exposure: Float,
        sharpen: Float,
        tint: Float,
        highlights: Float,
        shadows: Float
    ) -> Unit,
    onDismiss: () -> Unit
) {
    var activeParam by remember { mutableStateOf(AdjustParam.BRIGHTNESS) }

    // Map existing normalized values to -100..+100 display values
    var brightness by remember { mutableFloatStateOf(currentScene.brightnessValue) }
    var contrast by remember { mutableFloatStateOf((currentScene.contrastValue - 1f) * 100f) }
    var saturation by remember { mutableFloatStateOf((currentScene.saturationValue - 1f) * 100f) }
    var warmth by remember { mutableFloatStateOf(currentScene.warmthValue) }
    var vignette by remember { mutableFloatStateOf(currentScene.vignetteValue * 100f) }
    var exposure by remember { mutableFloatStateOf(currentScene.exposureValue * 100f) }
    var sharpen by remember { mutableFloatStateOf(currentScene.sharpenValue * 100f) }
    var tint by remember { mutableFloatStateOf(currentScene.tintValue * 100f) }
    var highlights by remember { mutableFloatStateOf(currentScene.highlightValue * 100f) }
    var shadows by remember { mutableFloatStateOf(currentScene.shadowValue * 100f) }

    fun notifyChange() {
        onAdjustChanged(
            brightness,
            (contrast / 100f) + 1f,
            (saturation / 100f) + 1f,
            warmth,
            (vignette / 100f).coerceIn(0f, 1f),
            (exposure / 100f).coerceIn(-1f, 1f),
            (sharpen / 100f).coerceIn(0f, 1f),
            (tint / 100f).coerceIn(-1f, 1f),
            (highlights / 100f).coerceIn(-1f, 1f),
            (shadows / 100f).coerceIn(-1f, 1f)
        )
    }

    fun getCurrentVal(param: AdjustParam): Float = when (param) {
        AdjustParam.BRIGHTNESS -> brightness
        AdjustParam.CONTRAST -> contrast
        AdjustParam.SATURATION -> saturation
        AdjustParam.EXPOSURE -> exposure
        AdjustParam.WARMTH -> warmth
        AdjustParam.TINT -> tint
        AdjustParam.VIGNETTE -> vignette
        AdjustParam.SHARPEN -> sharpen
        AdjustParam.HIGHLIGHTS -> highlights
        AdjustParam.SHADOWS -> shadows
    }

    fun setCurrentVal(param: AdjustParam, value: Float) {
        when (param) {
            AdjustParam.BRIGHTNESS -> brightness = value.coerceIn(param.minVal, param.maxVal)
            AdjustParam.CONTRAST -> contrast = value.coerceIn(param.minVal, param.maxVal)
            AdjustParam.SATURATION -> saturation = value.coerceIn(param.minVal, param.maxVal)
            AdjustParam.EXPOSURE -> exposure = value.coerceIn(param.minVal, param.maxVal)
            AdjustParam.WARMTH -> warmth = value.coerceIn(param.minVal, param.maxVal)
            AdjustParam.TINT -> tint = value.coerceIn(param.minVal, param.maxVal)
            AdjustParam.VIGNETTE -> vignette = value.coerceIn(param.minVal, param.maxVal)
            AdjustParam.SHARPEN -> sharpen = value.coerceIn(param.minVal, param.maxVal)
            AdjustParam.HIGHLIGHTS -> highlights = value.coerceIn(param.minVal, param.maxVal)
            AdjustParam.SHADOWS -> shadows = value.coerceIn(param.minVal, param.maxVal)
        }
        notifyChange()
    }

    fun resetAll() {
        brightness = 0f
        contrast = 0f
        saturation = 0f
        exposure = 0f
        warmth = 0f
        tint = 0f
        vignette = 0f
        sharpen = 0f
        highlights = 0f
        shadows = 0f
        notifyChange()
    }

    val sheetScrollState = rememberScrollState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF14141B),
        tonalElevation = 8.dp,
        modifier = Modifier.testTag("adjust_studio_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp)
                .verticalScroll(sheetScrollState)
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = null,
                        tint = YouCutOrange,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Adjust Studio".localize(appLanguage),
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(
                        onClick = { resetAll() },
                        colors = ButtonDefaults.textButtonColors(contentColor = Color.Gray)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Reset All".localize(appLanguage), fontSize = 12.sp)
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Check, contentDescription = "Done", tint = YouCutOrange)
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Active Parameter Value Card
            val currentVal = getCurrentVal(activeParam)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF1E1E28))
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = activeParam.icon,
                            contentDescription = null,
                            tint = YouCutOrange,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = activeParam.label.localize(appLanguage),
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "${if (currentVal > 0) "+" else ""}${currentVal.roundToInt()}",
                            color = if (currentVal != 0f) YouCutOrange else Color.Gray,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Black
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        if (currentVal != 0f) {
                            Text(
                                text = "RESET",
                                color = Color(0xFFFF5252),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color(0xFFFF5252).copy(alpha = 0.15f))
                                    .clickable { setCurrentVal(activeParam, activeParam.defaultVal) }
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Interactive Precision Slider with - / + buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { setCurrentVal(activeParam, currentVal - 5f) },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(Icons.Default.Remove, contentDescription = "Decrease", tint = Color.LightGray)
                }

                Slider(
                    value = currentVal,
                    onValueChange = { setCurrentVal(activeParam, it) },
                    valueRange = activeParam.minVal..activeParam.maxVal,
                    colors = SliderDefaults.colors(
                        thumbColor = YouCutOrange,
                        activeTrackColor = YouCutOrange,
                        inactiveTrackColor = Color.DarkGray
                    ),
                    modifier = Modifier.weight(1f)
                )

                IconButton(
                    onClick = { setCurrentVal(activeParam, currentVal + 5f) },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Increase", tint = Color.LightGray)
                }
            }

            // Quick Preset Chips for active parameter
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                val presets = if (activeParam.minVal < 0) {
                    listOf(-50f, -25f, 0f, 25f, 50f)
                } else {
                    listOf(0f, 25f, 50f, 75f, 100f)
                }

                presets.forEach { presetVal ->
                    val isCurrent = currentVal.roundToInt() == presetVal.toInt()
                    Text(
                        text = "${if (presetVal > 0) "+" else ""}${presetVal.toInt()}",
                        color = if (isCurrent) YouCutOrange else Color.Gray,
                        fontSize = 10.sp,
                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isCurrent) YouCutOrange.copy(alpha = 0.2f) else Color.Transparent)
                            .clickable { setCurrentVal(activeParam, presetVal) }
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Horizontal Category Tool Chips
            val scrollState = rememberScrollState()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(scrollState),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AdjustParam.values().forEach { param ->
                    val isSelected = activeParam == param
                    val paramVal = getCurrentVal(param)
                    val hasModification = paramVal != param.defaultVal

                    Column(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                if (isSelected) YouCutOrange.copy(alpha = 0.25f)
                                else if (hasModification) Color(0xFF262238)
                                else Color(0xFF1E1E26)
                            )
                            .border(
                                width = if (isSelected) 1.5.dp else if (hasModification) 1.dp else 0.5.dp,
                                color = if (isSelected) YouCutOrange
                                else if (hasModification) YouCutOrange.copy(alpha = 0.6f)
                                else Color.White.copy(alpha = 0.08f),
                                shape = RoundedCornerShape(10.dp)
                            )
                            .clickable { activeParam = param }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(contentAlignment = Alignment.TopEnd) {
                            Icon(
                                imageVector = param.icon,
                                contentDescription = param.label,
                                tint = if (isSelected) YouCutOrange else if (hasModification) Color.White else Color.Gray,
                                modifier = Modifier.size(20.dp)
                            )
                            if (hasModification) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(YouCutOrange)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = param.label.localize(appLanguage),
                            color = if (isSelected) Color.White else Color.LightGray,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                        if (hasModification) {
                            Text(
                                text = "${if (paramVal > 0) "+" else ""}${paramVal.roundToInt()}",
                                color = YouCutOrange,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Apply to All Clips Button (Signature CapCut / Filmora feature)
            Button(
                onClick = {
                    onApplyToAll(
                        brightness,
                        (contrast / 100f) + 1f,
                        (saturation / 100f) + 1f,
                        warmth,
                        (vignette / 100f).coerceIn(0f, 1f),
                        (exposure / 100f).coerceIn(-1f, 1f),
                        (sharpen / 100f).coerceIn(0f, 1f),
                        (tint / 100f).coerceIn(-1f, 1f),
                        (highlights / 100f).coerceIn(-1f, 1f),
                        (shadows / 100f).coerceIn(-1f, 1f)
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2C2C3E)),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.DoneAll, contentDescription = null, tint = YouCutOrange, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Apply Color Grading to All Clips".localize(appLanguage),
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}
