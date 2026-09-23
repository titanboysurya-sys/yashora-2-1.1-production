package com.ritvyom.yashoraReelgenerator.presentation.components

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ritvyom.yashoraReelgenerator.data.ai.AiProviderConfig
import com.ritvyom.yashoraReelgenerator.data.ai.ProviderId
import com.ritvyom.yashoraReelgenerator.data.ai.ProviderStatus
import com.ritvyom.yashoraReelgenerator.data.ai.SecureAiCredentialStore
import com.ritvyom.yashoraReelgenerator.presentation.viewmodels.MainViewModel

/**
 * Modern, secure Bring-Your-Own-Key (BYOK) AI Provider Studio Dialog.
 *
 * Supports configuring and testing:
 * - Google Gemini (Google AI Studio)
 * - Groq (Ultra-fast Llama 3.3 inference)
 * - OpenAI (GPT-4o mini)
 * - DeepSeek (DeepSeek V3 / R1)
 *
 * Ensures:
 * - Passwords/keys masked by default
 * - No plain-text logs
 * - Local encrypted keystore storage
 * - Non-blocking connectivity test
 */
@Composable
fun AiProvidersStudioDialog(
    viewModel: MainViewModel,
    languageState: String,
    onDismiss: () -> Unit
) {
    val uriHandler = LocalUriHandler.current
    val isHindi = languageState.contains("hi", ignoreCase = true) || languageState.contains("hindi", ignoreCase = true)

    val providerConfigs by viewModel.unifiedAiRouter.providerConfigs.collectAsState()
    var selectedProvider by remember { mutableStateOf(ProviderId.GEMINI) }
    var inputKey by remember { mutableStateOf("") }
    var showKeyPlaintext by remember { mutableStateOf(false) }

    var isTesting by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<Pair<Boolean, String>?>(null) }

    // When provider changes, reset test and input state
    LaunchedEffect(selectedProvider) {
        val currentHasKey = viewModel.unifiedAiRouter.credentialStore.hasApiKey(selectedProvider)
        inputKey = if (currentHasKey) viewModel.unifiedAiRouter.credentialStore.getApiKey(selectedProvider) else ""
        testResult = null
        showKeyPlaintext = false
    }

    val currentConfig = providerConfigs[selectedProvider] ?: AiProviderConfig(selectedProvider)
    val userUsageMap by com.ritvyom.yashoraReelgenerator.data.ai.UserAiUsageTracker.usageMap.collectAsState()
    val currentUserUsage = userUsageMap[selectedProvider]

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.SmartToy,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isHindi) "AI इंजन और BYOK प्रोवाइडर्स" else "AI Studio & BYOK Providers",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Close")
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Provider Selection Chips (Flexibly wrapped or scrollable with full padding & no vertical truncation)
                androidx.compose.foundation.lazy.LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(horizontal = 2.dp, vertical = 4.dp)
                ) {
                    items(ProviderId.values()) { provider ->
                        val isSelected = provider == selectedProvider
                        val hasKey = providerConfigs[provider]?.hasValidKey == true
                        val chipTitle = when (provider) {
                            ProviderId.GEMINI -> "Gemini"
                            ProviderId.XAI -> "Grok (xAI)"
                            ProviderId.GROQ -> "Groq"
                            ProviderId.OPENAI -> "OpenAI"
                            ProviderId.DEEPSEEK -> "DeepSeek"
                            ProviderId.CLAUDE -> "Claude"
                        }

                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                            border = if (isSelected) null else androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                            modifier = Modifier.clickable { selectedProvider = provider }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                if (hasKey) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = "Active",
                                        tint = if (isSelected) MaterialTheme.colorScheme.onPrimary else Color(0xFF2E7D32),
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                                Text(
                                    text = chipTitle,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                // Architecture info banner
                Card(
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isHindi)
                                "ऑन-डिवाइस कीस्टोर: आपकी API Keys पूरी तरह से एन्क्रिप्टेड हैं। कभी लॉग या एक्सपोर्ट नहीं होतीं।"
                            else
                                "On-Device Keystore: Your API Keys stay encrypted on your device and are never shared or logged.",
                            fontSize = 11.sp,
                            lineHeight = 15.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }

                // Current Provider Status & Details
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "${selectedProvider.displayName} Key:",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (currentConfig.hasValidKey) {
                            Spacer(modifier = Modifier.width(6.dp))
                            val isUserKey = currentConfig.isUserProvided
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = if (isUserKey) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)
                            ) {
                                Text(
                                    text = if (isUserKey) {
                                        if (isHindi) "यूज़र की (Custom)" else "Custom Key"
                                    } else {
                                        if (isHindi) "डिफ़ॉल्ट की (App Default)" else "App Default"
                                    },
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isUserKey) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    // Get key link
                    TextButton(
                        onClick = {
                            try {
                                uriHandler.openUri(selectedProvider.websiteUrl)
                            } catch (_: Exception) {}
                        },
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = if (isHindi) "की प्राप्त करें ↗" else "Get Key ↗",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                // Key Input Field
                OutlinedTextField(
                    value = inputKey,
                    onValueChange = { inputKey = it.trim() },
                    placeholder = {
                        Text(
                            text = when (selectedProvider) {
                                ProviderId.GEMINI -> "AIzaSy..."
                                ProviderId.XAI -> "xai-..."
                                ProviderId.GROQ -> "gsk_..."
                                ProviderId.OPENAI -> "sk-proj-..."
                                ProviderId.DEEPSEEK -> "sk-..."
                                else -> "Enter API Key"
                            },
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    visualTransformation = if (showKeyPlaintext) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { showKeyPlaintext = !showKeyPlaintext }) {
                                Icon(
                                    imageVector = if (showKeyPlaintext) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = "Toggle Visibility",
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            if (inputKey.isNotEmpty()) {
                                IconButton(onClick = { inputKey = "" }) {
                                    Icon(
                                        imageVector = Icons.Default.Clear,
                                        contentDescription = "Clear",
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    },
                    shape = RoundedCornerShape(10.dp)
                )

                // Personal BYOK Usage & Quota Monitor Card (Only displays user's own data, exactly like ElevenLabs)
                if (currentConfig.hasValidKey) {
                    UserPersonalProviderUsageCard(
                        providerId = selectedProvider,
                        usage = currentUserUsage,
                        hasValidKey = currentConfig.hasValidKey,
                        appLanguage = languageState
                    )
                }

                // xAI Specific Help Banner for 403 / billing guidance
                if (selectedProvider == ProviderId.XAI) {
                    Card(
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.45f)
                        ),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.tertiary.copy(alpha = 0.4f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.tertiary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (isHindi) "403 Forbidden एरर से कैसे बचें?" else "Avoiding 403 Forbidden:",
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = if (isHindi)
                                    "• Key केवल https://console.x.ai से बनाएं (Twitter/X App का X Premium सब्सक्रिप्शन API के लिए अलग होता है)।\n• console.x.ai में 'Billing' में Prepaid Credits ($5+) जोड़ना अनिवार्य है (0 बैलेंस पर xAI 403 एरर देता है)।\n• Key बनाते समय Permissions में 'All Endpoints & Models' को अवश्य चालू रखें।"
                                else
                                    "• Generate key at https://console.x.ai (Twitter/X Premium app subscription does not include API access).\n• Prepaid credits ($5+) in console.x.ai Billing are required (0 balance causes 403 Forbidden).\n• In console.x.ai, grant 'All Endpoints & Models' permissions for the key.",
                                fontSize = 10.5.sp,
                                lineHeight = 14.sp,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                    }
                }

                // Test Connection Button & Status
                if (isTesting) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Text(
                            text = if (isHindi) "कनेक्शन की पुष्टि की जा रही है..." else "Testing connection...",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                } else if (testResult != null) {
                    val (isSuccess, message) = testResult!!
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = if (isSuccess) Icons.Default.CheckCircle else Icons.Default.Error,
                            contentDescription = null,
                            tint = if (isSuccess) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = message,
                            fontSize = 12.sp,
                            color = if (isSuccess) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error,
                            lineHeight = 15.sp
                        )
                    }
                }

                OutlinedButton(
                    onClick = {
                        isTesting = true
                        testResult = null
                        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).run {
                            // Temporarily test with inputKey
                            val adapter = when (selectedProvider) {
                                ProviderId.GEMINI -> com.ritvyom.yashoraReelgenerator.data.ai.GeminiProviderAdapter()
                                ProviderId.GROQ -> com.ritvyom.yashoraReelgenerator.data.ai.GroqProviderAdapter()
                                ProviderId.OPENAI -> com.ritvyom.yashoraReelgenerator.data.ai.OpenAiProviderAdapter()
                                ProviderId.DEEPSEEK -> com.ritvyom.yashoraReelgenerator.data.ai.DeepSeekProviderAdapter()
                                else -> com.ritvyom.yashoraReelgenerator.data.ai.GeminiProviderAdapter()
                            }
                            kotlinx.coroutines.GlobalScope.run {
                                kotlinx.coroutines.MainScope().run {
                                    // Use viewmodel coroutine scope via testing
                                }
                            }
                        }
                        // Non-blocking test
                        val targetKey = inputKey
                        val targetProvider = selectedProvider
                        viewModel.testAiProvider(targetProvider, targetKey) { success, msg ->
                            isTesting = false
                            testResult = Pair(success, msg)
                        }
                    },
                    enabled = inputKey.isNotBlank() && !isTesting,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isHindi) "कनेक्शन टेस्ट करें (Test Connection)" else "Test Connection",
                        fontSize = 13.sp
                    )
                }

                Text(
                    text = if (isHindi)
                        "प्राइमरी प्रोवाइडर विफल होने पर अन्य सक्रिय प्रोवाइडर बैकअप के रूप में स्वतः उपयोग किए जाएंगे।"
                    else
                        "Automatic failover: If the primary provider hits rate limits or downtime, remaining configured providers serve as instant backups.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    viewModel.setAiProviderApiKey(selectedProvider, inputKey)
                    onDismiss()
                },
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(if (isHindi) "सहेजें (Save)" else "Save")
            }
        },
        dismissButton = {
            if (currentConfig.hasValidKey) {
                TextButton(
                    onClick = {
                        viewModel.removeAiProviderApiKey(selectedProvider)
                        inputKey = ""
                        testResult = null
                    }
                ) {
                    Text(
                        text = if (isHindi) "हटाएं (Remove)" else "Remove",
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    )
}
