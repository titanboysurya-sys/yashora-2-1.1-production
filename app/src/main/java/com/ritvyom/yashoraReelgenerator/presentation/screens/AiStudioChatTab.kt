package com.ritvyom.yashoraReelgenerator.presentation.screens

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.ritvyom.yashoraReelgenerator.data.ai.AiCapability
import com.ritvyom.yashoraReelgenerator.data.ai.AiGenerationRequest
import com.ritvyom.yashoraReelgenerator.data.ai.ProviderId
import com.ritvyom.yashoraReelgenerator.data.ai.generateOpenAiImage
import com.ritvyom.yashoraReelgenerator.presentation.viewmodels.MainViewModel
import kotlinx.coroutines.launch

/**
 * Message model for the AI Studio Chat & Media Generation screen.
 */
data class AiChatMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val isUser: Boolean,
    val text: String,
    val imageUrl: String? = null,
    val providerUsed: ProviderId? = null,
    val modelUsed: String? = null,
    val isError: Boolean = false,
    val failedPrompt: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Interactive AI Studio Chat Tab.
 * Appears dynamically when the user configures at least one BYOK API Key (OpenAI, Grok, Gemini, Groq, DeepSeek).
 * Supports:
 * - Conversational AI & Script Generation
 * - Direct Image Generation via DALL-E (OpenAI)
 * - Video prompt engineering & storyboard suggestions
 * - Quick prompt presets (Viral Hook, Reel Script, DALL-E Image, Cinematic Video Prompt)
 */
@Composable
fun AiStudioChatTab(
    viewModel: MainViewModel,
    appLanguageState: String,
    onNavigateToScriptConfig: ((String) -> Unit)? = null
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current
    val isHindi = appLanguageState.contains("hi", ignoreCase = true) || appLanguageState.contains("hindi", ignoreCase = true)

    val providerConfigs by viewModel.unifiedAiRouter.providerConfigs.collectAsState()
    val activeProviders = remember(providerConfigs) {
        providerConfigs.filter { it.value.hasValidKey }.keys.toList()
    }

    var selectedProvider by remember(activeProviders) {
        mutableStateOf(activeProviders.firstOrNull() ?: ProviderId.OPENAI)
    }

    // Ensure selectedProvider stays valid if keys change
    LaunchedEffect(activeProviders) {
        if (!activeProviders.contains(selectedProvider) && activeProviders.isNotEmpty()) {
            selectedProvider = activeProviders.first()
        }
    }

    var inputText by remember { mutableStateOf("") }
    var isGenerating by remember { mutableStateOf(false) }
    var generateMode by remember { mutableStateOf("Chat") } // "Chat" or "Image"
    var showQuotaCard by remember { mutableStateOf(false) }

    val userUsageMap by com.ritvyom.yashoraReelgenerator.data.ai.UserAiUsageTracker.usageMap.collectAsState()
    val currentUserUsage = userUsageMap[selectedProvider]

    val chatMessages = remember {
        mutableStateListOf(
            AiChatMessage(
                isUser = false,
                text = if (isHindi)
                    "नमस्ते! आपका AI असिस्टेंट तैयार है। आप मुझसे रील स्क्रिप्ट लिखवा सकते हैं, आइडिया मांग सकते हैं, या इमेज जनरेट करने का प्रॉम्प्ट दे सकते हैं।"
                else
                    "Hello! Your AI Studio is active and connected with your BYOK API Key. Ask me to write viral reel scripts, brainstorm ideas, or generate images!"
            )
        )
    }

    val listState = rememberLazyListState()

    LaunchedEffect(chatMessages.size) {
        if (chatMessages.isNotEmpty()) {
            listState.animateScrollToItem(chatMessages.size - 1)
        }
    }

    fun sendMessage(userText: String) {
        if (userText.isBlank() || isGenerating) return
        val prompt = userText.trim()
        inputText = ""

        chatMessages.add(AiChatMessage(isUser = true, text = prompt))
        isGenerating = true

        coroutineScope.launch {
            try {
                if (generateMode == "Image" || prompt.startsWith("/image", ignoreCase = true) || prompt.startsWith("image:", ignoreCase = true)) {
                    val cleanImgPrompt = prompt.removePrefix("/image").removePrefix("image:").trim()
                    val openAiKey = viewModel.unifiedAiRouter.credentialStore.getApiKey(ProviderId.OPENAI)
                    if (openAiKey.isNotBlank()) {
                        val imgResult = generateOpenAiImage(cleanImgPrompt, openAiKey)
                        if (imgResult.isSuccess) {
                            val url = imgResult.getOrThrow()
                            chatMessages.add(
                                AiChatMessage(
                                    isUser = false,
                                    text = "Generated Image for: \"$cleanImgPrompt\"",
                                    imageUrl = url,
                                    providerUsed = ProviderId.OPENAI,
                                    modelUsed = "dall-e-3"
                                )
                            )
                        } else {
                            chatMessages.add(
                                AiChatMessage(
                                    isUser = false,
                                    text = "Image generation error: ${imgResult.exceptionOrNull()?.message ?: "Unknown error"}"
                                )
                            )
                        }
                    } else {
                        // Fallback: AI describe or prompt recommendation
                        val req = AiGenerationRequest(
                            prompt = "Generate a highly detailed digital art prompt for video scene: $cleanImgPrompt",
                            capability = AiCapability.PROMPT_GENERATION
                        )
                        val res = viewModel.unifiedAiRouter.executeWithFallback(req, preferredProvider = selectedProvider)
                        chatMessages.add(
                            AiChatMessage(
                                isUser = false,
                                text = "DALL-E image generation requires an OpenAI API Key. In the meantime, here is an optimized cinematic scene visual prompt:\n\n${res.text}",
                                providerUsed = res.providerUsed,
                                modelUsed = res.modelUsed
                            )
                        )
                    }
                } else {
                    // Conversational Chat & Script Generation
                    val req = AiGenerationRequest(
                        prompt = prompt,
                        systemInstruction = "You are Yashora AI, a world-class viral video director, creative screenwriter, and digital content strategist. Give actionable, charismatic, and concise responses. Format scripts cleanly with Hook, Body, and Call To Action.",
                        capability = AiCapability.CHAT
                    )
                    val res = viewModel.unifiedAiRouter.executeWithFallback(req, preferredProvider = selectedProvider)
                    chatMessages.add(
                        AiChatMessage(
                            isUser = false,
                            text = res.text.trim(),
                            providerUsed = res.providerUsed,
                            modelUsed = res.modelUsed
                        )
                    )
                }
            } catch (e: Exception) {
                val errorMsg = e.message ?: "Failed to generate response. Please check your API key."
                val is503 = errorMsg.contains("503", ignoreCase = true) || errorMsg.contains("overloaded", ignoreCase = true)
                val userHelp = if (is503) {
                    if (isHindi)
                        "गूगल जेमिनी सर्वर अभी व्यस्त (Overloaded) था। ऑटोमैटिक मॉडल बैकअप से प्रयास किया गया। कृपया नीचे 'फिर से कोशिश करें' पर टैप करें।"
                    else
                        "Google Gemini server was temporarily overloaded (HTTP 503). Automatic model failovers were attempted. Please tap 'Retry' below."
                } else {
                    errorMsg
                }
                chatMessages.add(
                    AiChatMessage(
                        isUser = false,
                        text = userHelp,
                        isError = true,
                        failedPrompt = prompt
                    )
                )
            } finally {
                isGenerating = false
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Top Provider Switcher & Status Bar
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 2.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF2E7D32))
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isHindi) "AI स्टूडियो चैट (BYOK एक्टिव)" else "AI Studio Chat (BYOK Active)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    // Mode Toggle (Chat / Image)
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            .padding(2.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = if (generateMode == "Chat") MaterialTheme.colorScheme.primary else Color.Transparent,
                            modifier = Modifier.clickable { generateMode = "Chat" }
                        ) {
                            Text(
                                text = if (isHindi) "चैट" else "Chat",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (generateMode == "Chat") MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = if (generateMode == "Image") MaterialTheme.colorScheme.primary else Color.Transparent,
                            modifier = Modifier.clickable { generateMode = "Image" }
                        ) {
                            Text(
                                text = if (isHindi) "इमेज" else "Image",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (generateMode == "Image") MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                // Horizontal Active Provider Chips
                if (activeProviders.size > 1) {
                    Spacer(modifier = Modifier.height(6.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(activeProviders) { provider ->
                            val isSelected = provider == selectedProvider
                            val label = when (provider) {
                                ProviderId.OPENAI -> "OpenAI"
                                ProviderId.XAI -> "Grok (xAI)"
                                ProviderId.GEMINI -> "Gemini"
                                ProviderId.GROQ -> "Groq"
                                ProviderId.DEEPSEEK -> "DeepSeek"
                                else -> provider.displayName
                            }
                            FilterChip(
                                selected = isSelected,
                                onClick = { selectedProvider = provider },
                                label = { Text(label, fontSize = 11.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            )
                        }
                    }
                }

                // Personal Quota & Usage Quick Action Bar
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val quotaSummary = if (currentUserUsage != null && currentUserUsage.dailyLimit > 0) {
                        val rem = (currentUserUsage.dailyLimit - currentUserUsage.todayRequests).coerceAtLeast(0)
                        if (isHindi) "${selectedProvider.displayName} कोटा: $rem बचा हुआ (${currentUserUsage.todayRequests}/${currentUserUsage.dailyLimit})"
                        else "${selectedProvider.displayName} Quota: $rem left (${currentUserUsage.todayRequests}/${currentUserUsage.dailyLimit})"
                    } else if (currentUserUsage != null) {
                        if (isHindi) "${selectedProvider.displayName}: ${currentUserUsage.todayRequests} कॉल (BYOK)"
                        else "${selectedProvider.displayName}: ${currentUserUsage.todayRequests} calls (BYOK)"
                    } else {
                        if (isHindi) "${selectedProvider.displayName} कोटा" else "${selectedProvider.displayName} Quota"
                    }

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.clickable { showQuotaCard = !showQuotaCard }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.DataUsage,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = quotaSummary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Icon(
                                imageVector = if (showQuotaCard) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }

                AnimatedVisibility(visible = showQuotaCard) {
                    Column(modifier = Modifier.padding(top = 8.dp)) {
                        com.ritvyom.yashoraReelgenerator.presentation.components.UserPersonalProviderUsageCard(
                            providerId = selectedProvider,
                            usage = currentUserUsage,
                            hasValidKey = true,
                            appLanguage = appLanguageState
                        )
                    }
                }
            }
        }

        // Messages List
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 14.dp),
            contentPadding = PaddingValues(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(chatMessages, key = { it.id }) { msg ->
                ChatBubble(
                    message = msg,
                    isHindi = isHindi,
                    onCopy = {
                        clipboardManager.setText(AnnotatedString(msg.text))
                        Toast.makeText(context, if (isHindi) "टेक्स्ट कॉपी हो गया" else "Copied to clipboard", Toast.LENGTH_SHORT).show()
                    },
                    onUseInReel = {
                        onNavigateToScriptConfig?.invoke(msg.text)
                    },
                    onRetry = { retryPrompt ->
                        sendMessage(retryPrompt)
                    }
                )
            }

            if (isGenerating) {
                item {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(8.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Text(
                            text = if (generateMode == "Image")
                                (if (isHindi) "DALL-E इमेज तैयार हो रही है..." else "Generating DALL-E image...")
                            else
                                (if (isHindi) "AI सोच रहा है..." else "AI is thinking..."),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }

        // Suggested Prompt Chips
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val suggestions = if (isHindi) {
                listOf(
                    "🔥 वायरल 30-सेकंड रील स्क्रिप्ट",
                    "🎨 DALL-E सिनेमैटिक इमेज प्रॉम्प्ट",
                    "💡 5 ट्रेंडिंग वीडियो टॉपिक्स",
                    "🎙️ आकर्षक हुक और आउट्रो"
                )
            } else {
                listOf(
                    "🔥 Viral 30s Reel Script",
                    "🎨 DALL-E Cinematic Image",
                    "💡 5 Trending Video Ideas",
                    "🎙️ High-Retention Hook"
                )
            }
            items(suggestions) { s ->
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                    modifier = Modifier.clickable {
                        val clean = s.substringAfter(" ")
                        if (clean.contains("DALL-E", ignoreCase = true) || clean.contains("इमेज", ignoreCase = true)) {
                            generateMode = "Image"
                            inputText = "Cinematic futuristic Mumbai cyberpunk neon skyline 4k"
                        } else {
                            sendMessage(clean)
                        }
                    }
                ) {
                    Text(
                        text = s,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }
        }

        // Input Bar
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 6.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    placeholder = {
                        Text(
                            text = if (generateMode == "Image")
                                (if (isHindi) "इमेज का विवरण लिखें..." else "Describe image to generate...")
                            else
                                (if (isHindi) "पूछें या रील स्क्रिप्ट लिखवाएं..." else "Ask or request a reel script..."),
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    },
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp),
                    maxLines = 4,
                    shape = RoundedCornerShape(22.dp)
                )

                FilledIconButton(
                    onClick = { sendMessage(inputText) },
                    enabled = inputText.isNotBlank() && !isGenerating,
                    shape = CircleShape,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ChatBubble(
    message: AiChatMessage,
    isHindi: Boolean,
    onCopy: () -> Unit,
    onUseInReel: () -> Unit,
    onRetry: ((String) -> Unit)? = null
) {
    val isUser = message.isUser
    val isError = message.isError
    val alignment = if (isUser) Alignment.End else Alignment.Start

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = alignment
    ) {
        // Model tag if AI (and not error)
        if (!isUser && message.providerUsed != null && !isError) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 2.dp, start = 4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.SmartToy,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(12.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "${message.providerUsed.displayName}${if (message.modelUsed != null) " • ${message.modelUsed}" else ""}",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
            }
        } else if (isError) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 2.dp, start = 4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ErrorOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(13.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = if (isHindi) "त्रुटि (Error)" else "Generation Notice",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Surface(
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isUser) 16.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 16.dp
            ),
            color = when {
                isUser -> MaterialTheme.colorScheme.primary
                isError -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f)
                else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
            },
            border = if (isError) androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f)) else null,
            shadowElevation = 1.dp,
            modifier = Modifier.widthIn(max = 320.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                // Generated Image if present
                if (message.imageUrl != null) {
                    AsyncImage(
                        model = message.imageUrl,
                        contentDescription = "Generated AI Image",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .clip(RoundedCornerShape(10.dp))
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                SelectionContainer {
                    Text(
                        text = message.text,
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        color = when {
                            isUser -> MaterialTheme.colorScheme.onPrimary
                            isError -> MaterialTheme.colorScheme.onErrorContainer
                            else -> MaterialTheme.colorScheme.onSurface
                        }
                    )
                }

                // Error Action: Retry
                if (isError && !message.failedPrompt.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = { onRetry?.invoke(message.failedPrompt) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onError)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isHindi) "फिर से कोशिश करें (Retry)" else "Retry Request",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onError
                        )
                    }
                }

                // AI Action row: Copy & Use as Reel Script (Only for non-error messages)
                if (!isUser && !isError && message.text.length > 20) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = onCopy,
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(if (isHindi) "कॉपी" else "Copy", fontSize = 11.sp)
                        }

                        FilledTonalButton(
                            onClick = onUseInReel,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Movie, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(if (isHindi) "रील बनाएं" else "Create Reel", fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}
