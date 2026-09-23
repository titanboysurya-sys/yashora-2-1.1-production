package com.ritvyom.yashoraReelgenerator.presentation.components.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ritvyom.yashoraReelgenerator.domain.models.CanvasLayer
import com.ritvyom.yashoraReelgenerator.domain.models.Scene
import com.ritvyom.yashoraReelgenerator.domain.models.getCanvasLayers
import com.ritvyom.yashoraReelgenerator.presentation.utils.localize

/**
 * Layers Studio Sheet:
 * Lists all added text and sticker elements in the active scene,
 * displaying depth hierarchy (Front to Back) and enabling real-time depth reordering
 * (Bring Forward, Send Backward, Bring to Front, Send to Back) on the preview surface.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LayersStudioSheet(
    currentScene: Scene,
    selectedLayerId: String?,
    appLanguage: String,
    onSelectLayer: (String) -> Unit,
    onBringForward: (String) -> Unit,
    onSendBackward: (String) -> Unit,
    onBringToFront: (String) -> Unit,
    onSendToBack: (String) -> Unit,
    onToggleVisibility: (String) -> Unit,
    onDeleteLayer: (String) -> Unit,
    onAddTextLayer: (String) -> Unit,
    onAddStickerLayer: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val layers = remember(currentScene.layersJson, currentScene.textOverlay, currentScene.stickerName) {
        currentScene.getCanvasLayers()
    }

    var showAddTextDialog by remember { mutableStateOf(false) }
    var newTextContent by remember { mutableStateOf("") }
    var showAddStickerDialog by remember { mutableStateOf(false) }

    val quickStickers = remember {
        listOf(
            "🔥", "❤️", "⭐", "🎬", "🚀", "💯", "💥", "✨",
            "👑", "🎯", "🎉", "👏", "🤩", "💖", "⚡", "🔔"
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF161622),
        tonalElevation = 8.dp,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 10.dp, bottom = 6.dp)
                    .width(40.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color.White.copy(alpha = 0.25f))
            )
        }
    ) {
        val configuration = androidx.compose.ui.platform.LocalConfiguration.current
        val isCompactHeight = configuration.screenHeightDp < 600

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp)
        ) {
            // Header Row: Title & Done button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(YouCutOrange.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Layers,
                            contentDescription = null,
                            tint = YouCutOrange,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Layers & Depth".localize(appLanguage),
                            color = Color.White,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${layers.size} " + "elements (Front at top, Back at bottom)".localize(appLanguage),
                            color = Color.Gray,
                            fontSize = 11.sp
                        )
                    }
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("close_layers_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close".localize(appLanguage),
                        tint = Color.White.copy(alpha = 0.7f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Action Row: Add Text Layer & Add Sticker Layer
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { showAddTextDialog = true },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF242436),
                        contentColor = Color.White
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(38.dp)
                        .testTag("add_text_layer_button"),
                    contentPadding = PaddingValues(horizontal = 8.dp)
                ) {
                    Icon(Icons.Default.TextFields, contentDescription = null, modifier = Modifier.size(16.dp), tint = YouCutOrange)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("+ Text Layer".localize(appLanguage), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }

                Button(
                    onClick = { showAddStickerDialog = true },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF242436),
                        contentColor = Color.White
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(38.dp)
                        .testTag("add_sticker_layer_button"),
                    contentPadding = PaddingValues(horizontal = 8.dp)
                ) {
                    Icon(Icons.Default.SentimentSatisfiedAlt, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color(0xFFFFD700))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("+ Sticker Layer".localize(appLanguage), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Layers List or Empty State
            if (layers.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF1E1E2C))
                        .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.LayersClear,
                            contentDescription = null,
                            tint = Color.Gray.copy(alpha = 0.6f),
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "No Overlay Layers".localize(appLanguage),
                            color = Color.White.copy(alpha = 0.9f),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Add text titles or sticker emojis above to control depth".localize(appLanguage),
                            color = Color.Gray,
                            fontSize = 11.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                // In canvas drawing order:
                // layers[0] is BACKMOST (lowest z-index, drawn first)
                // layers[layers.size - 1] is FRONTMOST (highest z-index, drawn on top)
                // In the layer panel list, we display from FRONT (top of list) to BACK (bottom of list):
                val reversedIndices = remember(layers) { layers.indices.reversed().toList() }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(reversedIndices) { listPosition, actualIndex ->
                        val layer = layers[actualIndex]
                        val isSelected = layer.id == selectedLayerId
                        val isFrontmost = actualIndex == layers.size - 1
                        val isBackmost = actualIndex == 0

                        LayerItemCard(
                            layer = layer,
                            isSelected = isSelected,
                            isFrontmost = isFrontmost,
                            isBackmost = isBackmost,
                            depthRank = listPosition + 1,
                            totalLayers = layers.size,
                            appLanguage = appLanguage,
                            onSelect = { onSelectLayer(layer.id) },
                            onBringForward = { onBringForward(layer.id) },
                            onSendBackward = { onSendBackward(layer.id) },
                            onBringToFront = { onBringToFront(layer.id) },
                            onSendToBack = { onSendToBack(layer.id) },
                            onToggleVisibility = { onToggleVisibility(layer.id) },
                            onDelete = { onDeleteLayer(layer.id) }
                        )
                    }
                }
            }
        }
    }

    // Add Text Layer Dialog
    if (showAddTextDialog) {
        AlertDialog(
            onDismissRequest = { showAddTextDialog = false },
            containerColor = Color(0xFF20202E),
            title = {
                Text(
                    text = "Add Text Layer".localize(appLanguage),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            },
            text = {
                Column {
                    Text(
                        text = "Enter overlay text caption:".localize(appLanguage),
                        color = Color.LightGray,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = newTextContent,
                        onValueChange = { newTextContent = it },
                        placeholder = { Text("e.g. BREAKING NEWS", color = Color.Gray) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = YouCutOrange,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.2f)
                        ),
                        modifier = Modifier.fillMaxWidth().testTag("new_text_layer_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val txt = newTextContent.trim().ifEmpty { "NEW TEXT" }
                        onAddTextLayer(txt)
                        newTextContent = ""
                        showAddTextDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = YouCutOrange)
                ) {
                    Text("Add Layer".localize(appLanguage))
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddTextDialog = false }) {
                    Text("Cancel".localize(appLanguage), color = Color.Gray)
                }
            }
        )
    }

    // Add Sticker Layer Dialog
    if (showAddStickerDialog) {
        AlertDialog(
            onDismissRequest = { showAddStickerDialog = false },
            containerColor = Color(0xFF20202E),
            title = {
                Text(
                    text = "Pick Sticker Layer".localize(appLanguage),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            },
            text = {
                Column {
                    Text(
                        text = "Select an emoji sticker to add as a new layer:".localize(appLanguage),
                        color = Color.LightGray,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        quickStickers.take(4).forEach { stk ->
                            StickerChoiceBox(stk) {
                                onAddStickerLayer(stk)
                                showAddStickerDialog = false
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        quickStickers.drop(4).take(4).forEach { stk ->
                            StickerChoiceBox(stk) {
                                onAddStickerLayer(stk)
                                showAddStickerDialog = false
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        quickStickers.drop(8).take(4).forEach { stk ->
                            StickerChoiceBox(stk) {
                                onAddStickerLayer(stk)
                                showAddStickerDialog = false
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        quickStickers.drop(12).take(4).forEach { stk ->
                            StickerChoiceBox(stk) {
                                onAddStickerLayer(stk)
                                showAddStickerDialog = false
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showAddStickerDialog = false }) {
                    Text("Cancel".localize(appLanguage), color = Color.Gray)
                }
            }
        )
    }
}

@Composable
private fun StickerChoiceBox(sticker: String, onSelect: () -> Unit) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF2B2B3E))
            .clickable { onSelect() },
        contentAlignment = Alignment.Center
    ) {
        Text(sticker, fontSize = 22.sp)
    }
}

@Composable
private fun LayerItemCard(
    layer: CanvasLayer,
    isSelected: Boolean,
    isFrontmost: Boolean,
    isBackmost: Boolean,
    depthRank: Int,
    totalLayers: Int,
    appLanguage: String,
    onSelect: () -> Unit,
    onBringForward: () -> Unit,
    onSendBackward: () -> Unit,
    onBringToFront: () -> Unit,
    onSendToBack: () -> Unit,
    onToggleVisibility: () -> Unit,
    onDelete: () -> Unit
) {
    val borderColor = if (isSelected) YouCutOrange else Color.White.copy(alpha = 0.12f)
    val bgColor = if (isSelected) Color(0xFF26263A) else Color(0xFF1C1C2A)

    Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = bgColor),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, borderColor, RoundedCornerShape(10.dp))
            .clickable { onSelect() }
            .testTag("layer_item_${layer.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Depth Rank indicator chip
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(
                        if (isFrontmost) YouCutOrange.copy(alpha = 0.25f)
                        else if (isBackmost) Color(0xFF333348)
                        else Color(0xFF262638)
                    )
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (isFrontmost) "FRONT".localize(appLanguage)
                    else if (isBackmost) "BACK".localize(appLanguage)
                    else "#$depthRank",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isFrontmost) YouCutOrange else Color.LightGray
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            // Thumbnail / Type representation
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF2E2E42)),
                contentAlignment = Alignment.Center
            ) {
                if (layer.type == "TEXT") {
                    Icon(
                        imageVector = Icons.Default.TextFields,
                        contentDescription = "Text",
                        tint = try {
                            Color(android.graphics.Color.parseColor(layer.color))
                        } catch (e: Exception) {
                            Color.White
                        },
                        modifier = Modifier.size(20.dp)
                    )
                } else {
                    Text(
                        text = layer.content.ifEmpty { "★" },
                        fontSize = 18.sp
                    )
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            // Content & Info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (layer.type == "TEXT") layer.content.ifEmpty { "Empty Text" } else "${layer.content} Sticker",
                    color = if (layer.isVisible) Color.White else Color.Gray,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = if (layer.type == "TEXT") layer.font else "Sticker",
                        color = Color.Gray,
                        fontSize = 10.sp
                    )
                    Text(
                        text = "• ${(layer.scale * 100).toInt()}% scale",
                        color = Color.Gray,
                        fontSize = 10.sp
                    )
                    if (layer.rotation != 0f) {
                        Text(
                            text = "• ${layer.rotation.toInt()}°",
                            color = YouCutOrange.copy(alpha = 0.8f),
                            fontSize = 10.sp
                        )
                    }
                }
            }

            // Depth Reorder Action Buttons: Bring Forward (🔼) & Send Backward (🔽)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Bring Forward (Up in depth / closer to front)
                IconButton(
                    onClick = onBringForward,
                    enabled = !isFrontmost,
                    modifier = Modifier
                        .size(32.dp)
                        .testTag("bring_forward_${layer.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowUpward,
                        contentDescription = "Bring Forward".localize(appLanguage),
                        tint = if (!isFrontmost) Color.White else Color.Gray.copy(alpha = 0.3f),
                        modifier = Modifier.size(16.dp)
                    )
                }

                // Send Backward (Down in depth / closer to back)
                IconButton(
                    onClick = onSendBackward,
                    enabled = !isBackmost,
                    modifier = Modifier
                        .size(32.dp)
                        .testTag("send_backward_${layer.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowDownward,
                        contentDescription = "Send Backward".localize(appLanguage),
                        tint = if (!isBackmost) Color.White else Color.Gray.copy(alpha = 0.3f),
                        modifier = Modifier.size(16.dp)
                    )
                }

                // Visibility Toggle
                IconButton(
                    onClick = onToggleVisibility,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = if (layer.isVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                        contentDescription = "Toggle Visibility".localize(appLanguage),
                        tint = if (layer.isVisible) Color.White.copy(alpha = 0.85f) else Color.Gray,
                        modifier = Modifier.size(16.dp)
                    )
                }

                // Delete Layer
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = "Delete".localize(appLanguage),
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}
