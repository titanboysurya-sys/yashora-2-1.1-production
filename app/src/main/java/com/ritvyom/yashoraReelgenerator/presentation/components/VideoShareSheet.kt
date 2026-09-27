package com.ritvyom.yashoraReelgenerator.presentation.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.ritvyom.yashoraReelgenerator.presentation.utils.SoundSynth
import com.ritvyom.yashoraReelgenerator.presentation.utils.VideoFileManager
import com.ritvyom.yashoraReelgenerator.presentation.utils.VideoShareManager
import com.ritvyom.yashoraReelgenerator.presentation.utils.localize
import kotlinx.coroutines.launch
import java.io.File

private val DarkSheetBg = Color(0xFF131022)
private val CardBg = Color(0xFF1E1B33)
private val NeonPurple = Color(0xFF8A2BE2)
private val NeonPink = Color(0xFFFF1493)
private val TextMuted = Color(0xFFA09CC0)
private val BorderColor = Color(0xFF2E2A4D)

/**
 * Modern Material 3 Dialog for direct social sharing via Android FileProvider.
 * Offers 1-tap shortcuts to Instagram, WhatsApp, Telegram, YouTube, and the System Chooser.
 */
@Composable
fun VideoShareDialog(
    filePath: String,
    projectTitle: String = "Yashora Reel",
    aspectRatio: String = "9:16",
    appLanguageState: String = "en",
    onDismiss: () -> Unit,
    onPlayVideo: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val file = remember(filePath) {
        VideoFileManager.resolvePlayableFile(context, filePath) ?: if (filePath.startsWith("/")) File(filePath) else null
    }
    val fileSizeBytes = remember(file) { file?.length() ?: 0L }
    val formattedSize = remember(fileSizeBytes) { VideoFileManager.formatSizeBytes(fileSizeBytes) }
    val fileName = remember(filePath) { filePath.substringAfterLast("/").ifEmpty { "Yashora_Reel.mp4" } }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 16.dp)
                .testTag("video_share_dialog"),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = DarkSheetBg),
            border = BorderStroke(1.dp, Brush.linearGradient(listOf(NeonPurple.copy(alpha = 0.6f), NeonPink.copy(alpha = 0.4f))))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .background(
                                    Brush.linearGradient(listOf(NeonPurple, NeonPink)),
                                    CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = "Share",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Share Reel".localize(appLanguageState),
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = "FileProvider Offline Direct Share".localize(appLanguageState),
                                fontSize = 11.sp,
                                color = NeonPurple,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(32.dp)
                            .background(Color.White.copy(alpha = 0.08f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color.White.copy(alpha = 0.8f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Video Meta Card
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(CardBg)
                        .border(1.dp, BorderColor, RoundedCornerShape(14.dp))
                        .padding(12.dp)
                ) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = projectTitle,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Box(
                                modifier = Modifier
                                    .background(Color(0xFF4CAF50).copy(alpha = 0.15f), RoundedCornerShape(100.dp))
                                    .border(1.dp, Color(0xFF4CAF50).copy(alpha = 0.4f), RoundedCornerShape(100.dp))
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = formattedSize,
                                    fontSize = 10.sp,
                                    color = Color(0xFF81C784),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .background(NeonPurple.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "📐 $aspectRatio",
                                    fontSize = 9.5.sp,
                                    color = Color.White,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            Text(
                                text = fileName,
                                fontSize = 10.sp,
                                color = TextMuted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Section: 1-Tap Direct App Sharing
                Text(
                    text = "DIRECT APP SHARE".localize(appLanguageState),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextMuted,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Start
                )

                Spacer(modifier = Modifier.height(10.dp))

                val socialApps = remember {
                    listOf(
                        VideoShareManager.SocialApp.WHATSAPP,
                        VideoShareManager.SocialApp.INSTAGRAM,
                        VideoShareManager.SocialApp.TELEGRAM,
                        VideoShareManager.SocialApp.YOUTUBE,
                        VideoShareManager.SocialApp.FACEBOOK,
                        VideoShareManager.SocialApp.X_TWITTER
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    socialApps.forEach { app ->
                        val isInstalled = remember(app) { VideoShareManager.isAppInstalled(context, app) }
                        SocialAppItem(
                            app = app,
                            isInstalled = isInstalled,
                            onClick = {
                                coroutineScope.launch { SoundSynth.playSfx("Pop") }
                                VideoShareManager.shareVideo(
                                    context = context,
                                    filePath = filePath,
                                    title = projectTitle,
                                    appLanguageState = appLanguageState,
                                    targetApp = app
                                )
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // System Chooser Full Button
                Button(
                    onClick = {
                        coroutineScope.launch { SoundSynth.playSfx("Pop") }
                        VideoShareManager.shareVideo(
                            context = context,
                            filePath = filePath,
                            title = projectTitle,
                            appLanguageState = appLanguageState,
                            targetApp = VideoShareManager.SocialApp.SYSTEM_CHOOSER
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("share_system_chooser_button"),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = NeonPurple)
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "Share",
                        modifier = Modifier.size(18.dp),
                        tint = Color.White
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "All Apps (System Share Chooser)".localize(appLanguageState),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Action Buttons Row (Play / Copy Path)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (onPlayVideo != null) {
                        OutlinedButton(
                            onClick = {
                                onDismiss()
                                onPlayVideo()
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(40.dp),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, BorderColor),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Play",
                                modifier = Modifier.size(16.dp),
                                tint = NeonPink
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Preview".localize(appLanguageState),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    OutlinedButton(
                        onClick = {
                            coroutineScope.launch { SoundSynth.playSfx("Pop") }
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                            val clip = ClipData.newPlainText("Reel File Path", filePath)
                            clipboard?.setPrimaryClip(clip)
                            Toast.makeText(context, "Video file path copied to clipboard".localize(appLanguageState), Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, BorderColor),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy Path",
                            modifier = Modifier.size(15.dp),
                            tint = Color.White.copy(alpha = 0.8f)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Copy Path".localize(appLanguageState),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Security Note
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = "Security",
                        tint = Color(0xFF4CAF50),
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Secured with Android FileProvider (No external server upload)".localize(appLanguageState),
                        fontSize = 9.sp,
                        color = Color.White.copy(alpha = 0.5f),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
private fun SocialAppItem(
    app: VideoShareManager.SocialApp,
    isInstalled: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(68.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .background(Color(app.colorHex).copy(alpha = 0.18f), CircleShape)
                .border(1.5.dp, Color(app.colorHex).copy(alpha = if (isInstalled) 0.8f else 0.35f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = app.emoji,
                fontSize = 22.sp
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = app.displayName,
            fontSize = 10.sp,
            color = if (isInstalled) Color.White else TextMuted,
            fontWeight = if (isInstalled) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )

        if (isInstalled) {
            Text(
                text = "Installed",
                fontSize = 8.sp,
                color = Color(0xFF4CAF50),
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}
