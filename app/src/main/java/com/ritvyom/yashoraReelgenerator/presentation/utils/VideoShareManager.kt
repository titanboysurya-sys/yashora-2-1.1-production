package com.ritvyom.yashoraReelgenerator.presentation.utils

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.core.content.FileProvider
import com.ritvyom.yashoraReelgenerator.presentation.utils.localize
import java.io.File

/**
 * Robust Video Sharing Manager utilizing Android FileProvider for secure,
 * high-performance sharing of generated reels to Instagram, WhatsApp, Telegram, YouTube, and all external apps.
 */
object VideoShareManager {
    private const val TAG = "VideoShareManager"

    enum class SocialApp(
        val displayName: String,
        val packageNames: List<String>,
        val colorHex: Long,
        val emoji: String
    ) {
        WHATSAPP(
            displayName = "WhatsApp",
            packageNames = listOf("com.whatsapp", "com.whatsapp.w4b"),
            colorHex = 0xFF25D366,
            emoji = "💬"
        ),
        INSTAGRAM(
            displayName = "Instagram",
            packageNames = listOf("com.instagram.android"),
            colorHex = 0xFFE1306C,
            emoji = "📸"
        ),
        TELEGRAM(
            displayName = "Telegram",
            packageNames = listOf("org.telegram.messenger", "org.telegram.messenger.web"),
            colorHex = 0xFF229ED9,
            emoji = "✈️"
        ),
        YOUTUBE(
            displayName = "YouTube / Shorts",
            packageNames = listOf("com.google.android.youtube"),
            colorHex = 0xFFFF0000,
            emoji = "▶️"
        ),
        FACEBOOK(
            displayName = "Facebook",
            packageNames = listOf("com.facebook.katana", "com.facebook.lite"),
            colorHex = 0xFF1877F2,
            emoji = "📘"
        ),
        X_TWITTER(
            displayName = "X (Twitter)",
            packageNames = listOf("com.twitter.android", "com.twitter.android.lite"),
            colorHex = 0xFF1DA1F2,
            emoji = "✖️"
        ),
        SYSTEM_CHOOSER(
            displayName = "All Apps",
            packageNames = emptyList(),
            colorHex = 0xFF9C27B0,
            emoji = "🌐"
        )
    }

    /**
     * Resolves a secure content URI for the given local video file using FileProvider.
     */
    fun getShareableUri(context: Context, filePath: String): Uri? {
        if (filePath.isBlank()) return null
        val authority = "${context.packageName}.fileprovider"

        // 1. Try resolving physical file via VideoFileManager or direct path
        val resolvedFile = VideoFileManager.resolvePlayableFile(context, filePath)
            ?: if (filePath.startsWith("/")) File(filePath).takeIf { it.exists() && it.length() > 0 } else null

        if (resolvedFile != null && resolvedFile.exists() && resolvedFile.length() > 0) {
            try {
                val uri = FileProvider.getUriForFile(context, authority, resolvedFile)
                Log.d(TAG, "Successfully generated FileProvider URI: $uri for file: ${resolvedFile.absolutePath}")
                return uri
            } catch (e: Exception) {
                Log.e(TAG, "FileProvider getUriForFile failed for ${resolvedFile.absolutePath}", e)
            }
        }

        // 2. If it's already a content URI
        if (filePath.startsWith("content://")) {
            return try { Uri.parse(filePath) } catch (e: Exception) { null }
        }

        // 3. MediaStore check as fallback
        return try {
            VideoFileManager.getPlayableUri(context, filePath)
        } catch (e: Exception) {
            Log.e(TAG, "MediaStore fallback URI failed", e)
            null
        }
    }

    /**
     * Checks if a target app is installed on the user's device.
     */
    fun isAppInstalled(context: Context, socialApp: SocialApp): Boolean {
        if (socialApp == SocialApp.SYSTEM_CHOOSER) return true
        val pm = context.packageManager
        for (pkg in socialApp.packageNames) {
            try {
                pm.getPackageInfo(pkg, PackageManager.GET_ACTIVITIES)
                return true
            } catch (e: PackageManager.NameNotFoundException) {
                // Not found, check next package
            } catch (e: Exception) {
                // Ignore
            }
        }
        return false
    }

    /**
     * Shares a video file using Android FileProvider to a specific social app or the system chooser.
     */
    fun shareVideo(
        context: Context,
        filePath: String,
        title: String? = null,
        appLanguageState: String = "en",
        targetApp: SocialApp = SocialApp.SYSTEM_CHOOSER
    ): Boolean {
        if (filePath.isBlank()) {
            Toast.makeText(context, "Video file path is empty".localize(appLanguageState), Toast.LENGTH_SHORT).show()
            return false
        }

        val shareUri = getShareableUri(context, filePath)
        if (shareUri == null) {
            Toast.makeText(context, "Could not locate video file for sharing".localize(appLanguageState), Toast.LENGTH_SHORT).show()
            Log.e(TAG, "Failed resolving shareable URI for path: $filePath")
            return false
        }

        val shareTitle = title ?: "Shared Reel via Yashora Reel Generator"
        val shareCaption = "Created with Yashora AI Reel Generator! #YashoraReels #AI"

        try {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "video/mp4"
                putExtra(Intent.EXTRA_STREAM, shareUri)
                putExtra(Intent.EXTRA_SUBJECT, shareTitle)
                putExtra(Intent.EXTRA_TEXT, shareCaption)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            // Target specific social app if requested and installed
            var launchedDirect = false
            if (targetApp != SocialApp.SYSTEM_CHOOSER) {
                val installedPkg = targetApp.packageNames.firstOrNull { pkg ->
                    try {
                        context.packageManager.getPackageInfo(pkg, PackageManager.GET_ACTIVITIES)
                        true
                    } catch (e: Exception) {
                        false
                    }
                }

                if (installedPkg != null) {
                    intent.setPackage(installedPkg)
                    try {
                        context.grantUriPermission(
                            installedPkg,
                            shareUri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                        )
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed pre-granting URI permission to $installedPkg", e)
                    }

                    try {
                        context.startActivity(intent)
                        launchedDirect = true
                        Log.d(TAG, "Launched direct share to ${targetApp.displayName} ($installedPkg)")
                    } catch (e: Exception) {
                        Log.e(TAG, "Direct launch failed for ${targetApp.displayName}, falling back to chooser", e)
                        intent.setPackage(null)
                    }
                } else {
                    Toast.makeText(
                        context,
                        "${targetApp.displayName} is not installed on this device. Opening share menu...".localize(appLanguageState),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }

            if (!launchedDirect) {
                // Grant permission to all available intent handlers
                try {
                    val resolvedActivities = context.packageManager.queryIntentActivities(
                        intent,
                        PackageManager.MATCH_DEFAULT_ONLY
                    )
                    for (resolveInfo in resolvedActivities) {
                        val targetPackage = resolveInfo.activityInfo.packageName
                        try {
                            context.grantUriPermission(
                                targetPackage,
                                shareUri,
                                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                            )
                        } catch (e: Exception) {
                            Log.w(TAG, "Grant permission error for $targetPackage", e)
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Broadcasting URI grants failed", e)
                }

                val chooserIntent = Intent.createChooser(intent, "Share Reel via".localize(appLanguageState)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(chooserIntent)
            }
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Critical sharing failure", e)
            Toast.makeText(context, "Sharing error: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            return false
        }
    }
}
