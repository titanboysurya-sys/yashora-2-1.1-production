package com.ritvyom.yashoraReelgenerator.presentation.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ritvyom.yashoraReelgenerator.presentation.utils.localize
import kotlinx.coroutines.launch

data class PolicySection(
    val id: Int,
    val title: String,
    val summary: String,
    val bullets: List<String> = emptyList(),
    val tag: String = "General"
)

val YASHORA_PRIVACY_SECTIONS = listOf(
    PolicySection(
        id = 1,
        title = "1. Developer & Brand Information",
        summary = "Yashora is an AI-powered reel and video creation application developed and operated by Ritvyom / Suryadev Nishad.",
        bullets = listOf(
            "Application Name: Yashora",
            "Developer / Brand: Ritvyom",
            "Lead Developer: Suryadev Nishad",
            "Contact Email: Ritvyom@gmail.com",
            "Operated as an independent creative development venture.",
            "For all privacy inquiries, data deletion or questions: Ritvyom@gmail.com"
        ),
        tag = "Developer"
    ),
    PolicySection(
        id = 2,
        title = "2. Information Yashora May Process",
        summary = "Yashora processes only what is required to synthesize scripts, generate voiceovers, render videos, and synchronize cloud projects.",
        bullets = listOf(
            "Google account authentication details (name, email, avatar, UID via Google One-Tap).",
            "AI Prompts, topics, story concepts, and video titles entered by the user.",
            "Locally generated scripts, subtitles, scene timings, and voiceover audio files.",
            "User-selected local device images & videos used in the editor.",
            "User-provided third-party API keys (e.g., ElevenLabs) stored exclusively on the device.",
            "Cloud project drafts and community-shared reels (only when explicitly uploaded by the user).",
            "Non-sensitive diagnostic and advertising parameters where applicable."
        ),
        tag = "Data"
    ),
    PolicySection(
        id = 3,
        title = "3. Google Sign-In & One-Tap Authentication",
        summary = "Yashora integrates Google Identity Services (GIS) One-Tap for instant, 1-click passwordless authentication.",
        bullets = listOf(
            "We receive your public name, email address, profile picture, and Google UID.",
            "Authentication is securely validated through Firebase Authentication.",
            "Yashora NEVER sees, requests, or stores your Google account password.",
            "Google's privacy practices govern information processed on Google's identity platform."
        ),
        tag = "Auth"
    ),
    PolicySection(
        id = 4,
        title = "4. Firebase Authentication",
        summary = "Firebase Auth manages secure user sessions, cloud project ownership, and account verification.",
        bullets = listOf(
            "Stores unique Firebase UID, linked provider info, account creation date, and last sign-in timestamp.",
            "Used to secure your personal cloud projects, community shares, and enable complete account deletion.",
            "No passwords or sensitive identity credentials are saved on Yashora servers."
        ),
        tag = "Auth"
    ),
    PolicySection(
        id = 5,
        title = "5. Local Storage on Your Device",
        summary = "Privacy-first on-device sandboxing. Most video generation and editing operations happen entirely on your Android device.",
        bullets = listOf(
            "Stored locally: Room database drafts, video projects, timelines, subtitles, audio tracks, downloaded media, and temporary MP4 render files.",
            "Local files are never automatically uploaded to any server simply because they exist on your device.",
            "Users retain complete control: clearing cache or app data immediately wipes local files."
        ),
        tag = "Local"
    ),
    PolicySection(
        id = 6,
        title = "6. User API Keys & Sandboxed Storage",
        summary = "Third-party API keys (e.g. ElevenLabs) remain strictly on your local device with zero cloud sync.",
        bullets = listOf(
            "User-entered API keys are stored in encrypted local device preferences.",
            "NEVER synchronized to Firebase, Firestore, backend databases, or community shared projects.",
            "Used solely to make direct client-side HTTPS calls to the provider (Device → ElevenLabs API → Audio).",
            "Users can clear or update their API keys anytime from App Settings."
        ),
        tag = "Security"
    ),
    PolicySection(
        id = 7,
        title = "7. AI Script Generation (Gemini AI)",
        summary = "Automated scriptwriting transmits only topic prompts and language preferences to generate story scenes.",
        bullets = listOf(
            "Transmitted info: Prompts, titles, genre, scene duration, and target language preference.",
            "Generated content includes hooks, scene narrations, visual prompts, and timestamps.",
            "Do not input confidential or sensitive personal information into creative AI prompts."
        ),
        tag = "AI"
    ),
    PolicySection(
        id = 8,
        title = "8. AI Voice Generation & TTS",
        summary = "High-fidelity voice synthesis using ElevenLabs API or Android's built-in offline Text-to-Speech.",
        bullets = listOf(
            "Voice text is sent to ElevenLabs only when using user-configured voice models.",
            "Generated MP3/WAV audio is saved locally to your device storage.",
            "Offline Android TTS works 100% locally on-device without internet transmission."
        ),
        tag = "Voice"
    ),
    PolicySection(
        id = 9,
        title = "9. Images, Videos & Device Media",
        summary = "You choose what media to include in your reels.",
        bullets = listOf(
            "Import photos and videos from your Android gallery with standard privacy-preserving pickers.",
            "User-selected media stays on-device unless you explicitly choose Cloud Backup or Community Share.",
            "Exported reels are rendered directly to your device storage in high-definition 1080p MP4."
        ),
        tag = "Media"
    ),
    PolicySection(
        id = 10,
        title = "10. Third-Party Stock Media Providers",
        summary = "Integration with verified stock media libraries (Pexels, Pixabay, Unsplash).",
        bullets = listOf(
            "Scene search keywords (e.g. 'space galaxy', 'nature waterfall') are sent to search high-res B-roll footage.",
            "No personal user data or identities are sent to stock media providers.",
            "Each stock provider operates under its own privacy policy and license terms."
        ),
        tag = "Media"
    ),
    PolicySection(
        id = 11,
        title = "11. Cloud Project Synchronization",
        summary = "Optional cloud sync allows saving project drafts to your account for cross-device access.",
        bullets = listOf(
            "Syncs script text, scene configuration, subtitle styles, and project metadata.",
            "Only active when you are logged in with Google and choose to sync.",
            "Never uploads unselected private device files."
        ),
        tag = "Cloud"
    ),
    PolicySection(
        id = 12,
        title = "12. Firebase Firestore Database",
        summary = "Secure cloud database used for account metadata, cloud projects, and community pool.",
        bullets = listOf(
            "Encrypted at rest and in transit with strict security rules tied to your Firebase UID.",
            "User API keys are NEVER stored in Firestore.",
            "Account deletion permanently wipes all associated Firestore documents."
        ),
        tag = "Cloud"
    ),
    PolicySection(
        id = 13,
        title = "13. Community Sharing Pool",
        summary = "Voluntarily publish creative scripts and reel ideas to inspire other creators.",
        bullets = listOf(
            "Shared content (titles, scripts, prompts) becomes visible to other Yashora creators.",
            "Do not share confidential, copyrighted, or private personally identifiable information.",
            "Users can delete their shared community scripts at any time."
        ),
        tag = "Community"
    ),
    PolicySection(
        id = 14,
        title = "14. Sharing to Instagram, YouTube & Other Apps",
        summary = "Direct Android system share integration for your rendered video reels.",
        bullets = listOf(
            "Initiated exclusively by the user via Android's native Share Sheet.",
            "Target platforms (Instagram, YouTube, WhatsApp, TikTok) handle media under their own privacy policies."
        ),
        tag = "Export"
    ),
    PolicySection(
        id = 15,
        title = "15. Video Rendering & Export Integrity",
        summary = "Hardware-accelerated rendering powered by Jetpack Media3 Transformer.",
        bullets = listOf(
            "Video encoding and subtitle burning occur directly on your device processor.",
            "Exported MP4 files belong 100% to you with full intellectual property ownership.",
            "No automatic server uploads occur upon exporting videos."
        ),
        tag = "Export"
    ),
    PolicySection(
        id = 16,
        title = "16. Android Device Permissions",
        summary = "We request only standard Android permissions essential for media creation.",
        bullets = listOf(
            "READ_MEDIA_VIDEO / READ_MEDIA_IMAGES: To select gallery clips and photos.",
            "RECORD_AUDIO: For optional live mic recording (if used).",
            "INTERNET & ACCESS_NETWORK_STATE: For AI generation, stock search, and cloud sync.",
            "POST_NOTIFICATIONS: For render completion alerts on Android 13+."
        ),
        tag = "Permissions"
    ),
    PolicySection(
        id = 17,
        title = "17. Advertising & Monetization (AdMob)",
        summary = "Non-personalized and personalized banner/rewarded ads via Google AdMob.",
        bullets = listOf(
            "Processes standard anonymized advertising IDs and diagnostic parameters.",
            "We DO NOT sell, rent, or trade your personal data to advertisers.",
            "Users can manage ad personalization via their Google Account Ad settings."
        ),
        tag = "Ads"
    ),
    PolicySection(
        id = 18,
        title = "18. Diagnostics, Performance & Crash Reporting",
        summary = "Anonymized diagnostic metrics to keep the rendering engine crash-free.",
        bullets = listOf(
            "Processes device model, Android OS version, and crash stack traces.",
            "Used solely to fix bugs, optimize Media3 rendering speeds, and enhance app stability."
        ),
        tag = "General"
    ),
    PolicySection(
        id = 19,
        title = "19. Purpose & Scope of Data Processing",
        summary = "Summary of legitimate processing purposes.",
        bullets = listOf(
            "1. Deliver AI script, voice, and video rendering features.",
            "2. Authenticate users and safeguard account security.",
            "3. Store optional cloud backups and community shared scripts.",
            "4. Provide responsive user support and bug resolution.",
            "5. Comply with applicable digital laws and security standards."
        ),
        tag = "Legal"
    ),
    PolicySection(
        id = 20,
        title = "20. Information We Never Collect",
        summary = "Clear boundaries on data privacy protection.",
        bullets = listOf(
            "❌ Google Account Passwords",
            "❌ User's ElevenLabs or third-party API keys on backend servers",
            "❌ Unrelated device files, private photo albums, or SMS/Contacts",
            "❌ Unselected microphone or camera recordings"
        ),
        tag = "Security"
    ),
    PolicySection(
        id = 21,
        title = "21. Third-Party Service Providers",
        summary = "Data is shared only with verified infrastructure providers strictly as required.",
        bullets = listOf(
            "Google Firebase (Authentication & Cloud Firestore)",
            "Google Gemini AI (Script & Storyboard Generation)",
            "ElevenLabs (Voice Synthesis API when configured)",
            "Pexels, Pixabay, Unsplash (Stock Media Search)",
            "Google AdMob (In-app Advertising)"
        ),
        tag = "General"
    ),
    PolicySection(
        id = 22,
        title = "22. Legal Compliance & Disclosure",
        summary = "Disclosures occur only when mandated by valid legal processes.",
        bullets = listOf(
            "To comply with applicable laws, court orders, or official governmental requests.",
            "To protect against fraud, security threats, or malicious misuse of the platform."
        ),
        tag = "Legal"
    ),
    PolicySection(
        id = 23,
        title = "23. Data Retention & Lifecycles",
        summary = "Clear timelines on how long data persists.",
        bullets = listOf(
            "Local Data: Retained on your device until deleted or app uninstalled.",
            "Cloud Drafts: Retained in Firestore until deleted by user or account deletion.",
            "Community Content: Retained until author removes it or purges account."
        ),
        tag = "Data"
    ),
    PolicySection(
        id = 24,
        title = "24. Complete Account & Cloud Data Deletion",
        summary = "Instant, 1-click permanent account deletion directly in the app.",
        bullets = listOf(
            "Under Profile / Account Settings, tap 'Delete Account & Data'.",
            "Permanently wipes your Firestore user profile, all cloud drafts, community shares, and Firebase Auth account.",
            "Irreversible action providing full GDPR / CCPA compliance right to erasure."
        ),
        tag = "Rights"
    ),
    PolicySection(
        id = 25,
        title = "25. User Control Over Cloud Storage",
        summary = "Full autonomy over what gets saved to the cloud.",
        bullets = listOf(
            "You decide whether to use Cloud Sync or keep everything local.",
            "Signing in does not automatically upload your private device files."
        ),
        tag = "Cloud"
    ),
    PolicySection(
        id = 26,
        title = "26. Security Architecture & Encryption",
        summary = "Industry-standard cryptographic protocols protecting user data.",
        bullets = listOf(
            "HTTPS / TLS 1.3 encryption for all network transmissions.",
            "Firebase security rules restricting cloud access strictly to authorized UIDs.",
            "Android local sandboxing isolating project databases."
        ),
        tag = "Security"
    ),
    PolicySection(
        id = 27,
        title = "27. Third-Party Service Links & Terms",
        summary = "External services operate under their independent policies.",
        bullets = listOf(
            "Google Services: https://policies.google.com/privacy",
            "ElevenLabs: https://elevenlabs.io/privacy",
            "Pexels: https://www.pexels.com/privacy-policy",
            "Unsplash: https://unsplash.com/privacy"
        ),
        tag = "General"
    ),
    PolicySection(
        id = 28,
        title = "28. Children's Privacy Protection",
        summary = "Yashora is designed for general audiences and creative professionals.",
        bullets = listOf(
            "We do not knowingly collect personal information from children under 13.",
            "If you believe a child has provided personal information, contact Ritvyom@gmail.com for immediate deletion."
        ),
        tag = "Legal"
    ),
    PolicySection(
        id = 29,
        title = "29. International Data Transfers",
        summary = "Global cloud infrastructure compliance.",
        bullets = listOf(
            "Cloud processing via Firebase and Google Cloud may utilize secure servers globally with standard contractual safeguards."
        ),
        tag = "Legal"
    ),
    PolicySection(
        id = 30,
        title = "30. Your Global Privacy Rights (GDPR / CCPA / DPDP)",
        summary = "Empowering you with complete authority over your information.",
        bullets = listOf(
            "Right to Access: View all stored cloud drafts and profile information.",
            "Right to Rectification: Edit or update your scripts and projects anytime.",
            "Right to Erasure: Permanent 1-click in-app account & data deletion.",
            "Right to Data Portability: Export your rendered reels directly as MP4 files.",
            "Right to Withdraw Consent: Disconnect Google account or revoke permissions anytime."
        ),
        tag = "Rights"
    ),
    PolicySection(
        id = 31,
        title = "31. Policy Updates & Notifications",
        summary = "Transparent notification of policy revisions.",
        bullets = listOf(
            "Material changes will be updated with a revised 'Last Updated' timestamp.",
            "Periodic review is recommended to stay informed."
        ),
        tag = "General"
    ),
    PolicySection(
        id = 32,
        title = "32. Contact Us & Privacy Support",
        summary = "Direct access to developer support.",
        bullets = listOf(
            "Brand / Developer: Ritvyom",
            "Lead Developer: Suryadev Nishad",
            "Direct Privacy Email: Ritvyom@gmail.com",
            "Fast response guaranteed for all privacy & deletion inquiries."
        ),
        tag = "Developer"
    ),
    PolicySection(
        id = 33,
        title = "33. Consent & Acknowledgement",
        summary = "By downloading, creating reels, or using Yashora, you acknowledge and agree to this Privacy Policy.",
        bullets = listOf(
            "Effective Date: August 20, 2026",
            "Last Updated: August 20, 2026",
            "Thank you for choosing Yashora for your AI Reel creation journey!"
        ),
        tag = "Legal"
    )
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyPolicyDialog(
    languageState: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    var searchQuery by remember { mutableStateOf("") }
    var selectedTag by remember { mutableStateOf("All") }

    val tags = remember {
        listOf("All", "Developer", "Auth", "Data", "Local", "Security", "AI", "Voice", "Media", "Cloud", "Rights", "Legal")
    }

    val filteredSections = remember(searchQuery, selectedTag) {
        YASHORA_PRIVACY_SECTIONS.filter { section ->
            val matchesTag = selectedTag == "All" || section.tag.equals(selectedTag, ignoreCase = true)
            val matchesSearch = searchQuery.isBlank() ||
                    section.title.contains(searchQuery, ignoreCase = true) ||
                    section.summary.contains(searchQuery, ignoreCase = true) ||
                    section.bullets.any { it.contains(searchQuery, ignoreCase = true) }
            matchesTag && matchesSearch
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.96f)
                .fillMaxHeight(0.94f)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .clip(RoundedCornerShape(24.dp)),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Header Banner
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.linearGradient(
                                        listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.tertiary)
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Security,
                                contentDescription = "Privacy Shield",
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Privacy Policy".localize(languageState),
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "Yashora • Ritvyom / Suryadev Nishad",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(34.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // Quick Metadata Badge Strip
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Effective & Updated: Aug 20, 2026",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text(
                                    text = "Official 33-Clause Policy",
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f)
                                )
                            }

                            // Copy Email Action Button
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        val clip = ClipData.newPlainText("Yashora Privacy Email", "Ritvyom@gmail.com")
                                        clipboard.setPrimaryClip(clip)
                                        Toast.makeText(context, "Copied Ritvyom@gmail.com", Toast.LENGTH_SHORT).show()
                                    }
                                    .padding(horizontal = 8.dp, vertical = 5.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.ContentCopy,
                                        contentDescription = "Copy Email",
                                        modifier = Modifier.size(13.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Copy Email",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }

                        // Prominent single-line email badge to prevent text cut-off
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Contact: Ritvyom@gmail.com",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1
                            )
                        }
                    }
                }

                // Search Bar
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    placeholder = {
                        Text(
                            "Search privacy terms (e.g. API keys, delete, cloud)...",
                            fontSize = 11.5.sp
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(
                                    imageVector = Icons.Default.Clear,
                                    contentDescription = "Clear",
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                        focusedIndicatorColor = MaterialTheme.colorScheme.primary,
                        unfocusedIndicatorColor = Color.Transparent
                    )
                )

                // Category Chips
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(tags) { tag ->
                        val isSelected = selectedTag == tag
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                selectedTag = tag
                                coroutineScope.launch { listState.scrollToItem(0) }
                            },
                            label = { Text(tag, fontSize = 11.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, if (isSelected) Color.Transparent else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        )
                    }
                }

                // Policy Cards List
                if (filteredSections.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No policy section matches '$searchQuery'",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(filteredSections, key = { it.id }) { section ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f)
                                ),
                                border = BorderStroke(
                                    0.8.dp,
                                    if (section.id == 1 || section.id == 6 || section.id == 24)
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
                                    else
                                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
                                )
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = section.title,
                                            fontSize = 13.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                        ) {
                                            Text(
                                                text = section.tag,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(4.dp))

                                    Text(
                                        text = section.summary,
                                        fontSize = 12.sp,
                                        lineHeight = 16.sp,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontWeight = FontWeight.Medium
                                    )

                                    if (section.bullets.isNotEmpty()) {
                                        Spacer(modifier = Modifier.height(6.dp))
                                        section.bullets.forEach { bullet ->
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(vertical = 2.dp),
                                                verticalAlignment = Alignment.Top
                                            ) {
                                                Text(
                                                    text = "• ",
                                                    fontSize = 12.5.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                                Text(
                                                    text = bullet,
                                                    fontSize = 11.5.sp,
                                                    lineHeight = 15.5.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Bottom Action Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = {
                            val emailIntent = Intent(Intent.ACTION_SENDTO).apply {
                                data = Uri.parse("mailto:Ritvyom@gmail.com")
                                putExtra(Intent.EXTRA_SUBJECT, "Yashora Privacy Inquiry")
                            }
                            try {
                                context.startActivity(Intent.createChooser(emailIntent, "Send Privacy Inquiry"))
                            } catch (e: Exception) {
                                Toast.makeText(context, "Contact: Ritvyom@gmail.com", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Email,
                            contentDescription = "Email",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Email Developer",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Button(
                        onClick = onDismiss,
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "I Understand",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}
