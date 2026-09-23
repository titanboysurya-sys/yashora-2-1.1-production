package com.ritvyom.yashoraReelgenerator.presentation.components

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.ritvyom.yashoraReelgenerator.data.remote.FirebaseCloudManager
import com.ritvyom.yashoraReelgenerator.presentation.utils.localize
import com.google.firebase.auth.FirebaseUser
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions

/**
 * Pixel-perfect Google 'G' vector icon matching the official branding guidelines.
 */
val GoogleIconVector: ImageVector
    get() = ImageVector.Builder(
        name = "Google",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        // Red (top arc)
        path(
            fill = SolidColor(Color(0xFFEA4335)),
            pathBuilder = {
                moveTo(12.0f, 5.04f)
                curveTo(13.86f, 5.04f, 15.54f, 5.68f, 16.85f, 6.94f)
                lineTo(20.44f, 3.35f)
                curveTo(18.23f, 1.28f, 15.35f, 0.0f, 12.0f, 0.0f)
                curveTo(7.33f, 0.0f, 3.31f, 2.68f, 1.39f, 6.6f)
                lineTo(5.48f, 9.77f)
                curveTo(6.44f, 7.02f, 9.0f, 5.04f, 12.0f, 5.04f)
                close()
            }
        )
        // Yellow (left arc)
        path(
            fill = SolidColor(Color(0xFFFBBC05)),
            pathBuilder = {
                moveTo(1.39f, 6.6f)
                curveTo(0.5f, 8.42f, 0.0f, 10.46f, 0.0f, 12.0f)
                curveTo(0.0f, 13.54f, 0.5f, 15.58f, 1.39f, 17.4f)
                lineTo(5.48f, 14.23f)
                curveTo(5.24f, 13.51f, 5.12f, 12.77f, 5.12f, 12.0f)
                curveTo(5.12f, 11.23f, 5.24f, 10.49f, 5.48f, 9.77f)
                lineTo(1.39f, 6.6f)
                close()
            }
        )
        // Green (bottom arc)
        path(
            fill = SolidColor(Color(0xFF34A853)),
            pathBuilder = {
                moveTo(12.0f, 18.96f)
                curveTo(9.0f, 18.96f, 6.44f, 16.98f, 5.48f, 14.23f)
                lineTo(1.39f, 17.4f)
                curveTo(3.31f, 21.32f, 7.33f, 24.0f, 12.0f, 24.0f)
                curveTo(15.22f, 24.0f, 18.15f, 22.84f, 20.31f, 20.89f)
                lineTo(16.51f, 17.94f)
                curveTo(15.28f, 18.67f, 13.73f, 18.96f, 12.0f, 18.96f)
                close()
            }
        )
        // Blue (right segment and horizontal arm)
        path(
            fill = SolidColor(Color(0xFF4285F4)),
            pathBuilder = {
                moveTo(24.0f, 12.0f)
                curveTo(24.0f, 11.16f, 23.92f, 10.32f, 23.77f, 9.5f)
                lineTo(12.0f, 9.5f)
                lineTo(12.0f, 14.5f)
                lineTo(18.73f, 14.5f)
                curveTo(18.44f, 16.03f, 17.58f, 17.19f, 16.51f, 17.94f)
                lineTo(20.31f, 20.89f)
                curveTo(22.53f, 18.84f, 24.0f, 15.7f, 24.0f, 12.0f)
                close()
            }
        )
    }.build()

/**
 * A beautiful, highly polished minimalist Google Sign-In button that handles
 * launcher registration, ID token generation, and Firebase sign-in.
 */
@Composable
fun GoogleSignInButton(
    modifier: Modifier = Modifier,
    appLanguage: String = "en",
    onSuccess: () -> Unit = {},
    onFailure: (String) -> Unit = {}
) {
    val context = LocalContext.current
    var isAuthenticating by remember { mutableStateOf(false) }
    var errorDialogText by remember { mutableStateOf<String?>(null) }

    val gso = remember {
        com.google.android.gms.auth.api.signin.GoogleSignInOptions.Builder(
            com.google.android.gms.auth.api.signin.GoogleSignInOptions.DEFAULT_SIGN_IN
        )
        .requestIdToken("720068421416-9hjjstf34i93hrkfvva41j23u4cvaes6.apps.googleusercontent.com")
        .requestEmail()
        .build()
    }

    val googleSignInClient = remember(context) {
        com.google.android.gms.auth.api.signin.GoogleSignIn.getClient(context, gso)
    }

    val localOnFailure: (String) -> Unit = { error ->
        errorDialogText = error
        onFailure(error)
    }

    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = com.google.android.gms.auth.api.signin.GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(com.google.android.gms.common.api.ApiException::class.java)
            val idToken = account?.idToken
            if (idToken != null) {
                isAuthenticating = true
                FirebaseCloudManager.signInWithGoogle(idToken) { success, err ->
                    isAuthenticating = false
                    if (success) {
                        Toast.makeText(context, "Successfully Signed in with Google!".localize(appLanguage), Toast.LENGTH_SHORT).show()
                        onSuccess()
                    } else {
                        val errorText = err ?: "Google authentication failed"
                        val appCheckInstructions = getAppCheckErrorInstructions(errorText, appLanguage)
                        if (appCheckInstructions != null) {
                            localOnFailure(appCheckInstructions)
                        } else {
                            localOnFailure(errorText)
                        }
                    }
                }
            } else {
                localOnFailure("Google credentials ID token not obtained")
            }
        } catch (e: com.google.android.gms.common.api.ApiException) {
            val code = e.statusCode
            if (code == 12501 || code == 12502) {
                // User cancelled or closed the prompt, handle gracefully with a simple Toast and no scary popup
                Toast.makeText(context, if (appLanguage.lowercase() == "hi" || appLanguage.lowercase() == "hindi") "साइन-इन रद्द कर दिया गया" else "Sign-in canceled", Toast.LENGTH_SHORT).show()
            } else if (code == 10) {
                val instructions = if (appLanguage.lowercase() == "hi" || appLanguage.lowercase() == "hindi") {
                    "गूगल साइन-इन त्रुटि हुई है (DEVELOPER_ERROR)। कृपया बाद में प्रयास करें या सहायता टीम से संपर्क करें।"
                } else {
                    "Google Sign-In Error occurred (DEVELOPER_ERROR). Please try again later or contact our support team."
                }
                localOnFailure(instructions)
            } else {
                localOnFailure(if (appLanguage.lowercase() == "hi" || appLanguage.lowercase() == "hindi") "गूगल साइन-इन त्रुटि (कोड $code)" else "Google Sign-In Error (code $code)")
            }
        } catch (e: Exception) {
            localOnFailure("Google sign-in error: ${e.localizedMessage ?: "Unknown error"}")
        }
    }

    if (errorDialogText != null) {
        AlertDialog(
            onDismissRequest = { errorDialogText = null },
            title = {
                Text(
                    text = if (appLanguage.lowercase() == "hi" || appLanguage.lowercase() == "hindi") "साइन-इन त्रुटि" else "Sign-In Error",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error
                )
            },
            text = {
                Text(
                    text = errorDialogText ?: "",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            confirmButton = {
                TextButton(onClick = { errorDialogText = null }) {
                    Text(if (appLanguage.lowercase() == "hi" || appLanguage.lowercase() == "hindi") "ठीक है" else "OK")
                }
            }
        )
    }

    // Modern glassmorphic button style with custom interactive transitions
    Surface(
        onClick = {
            if (!isAuthenticating) {
                googleSignInLauncher.launch(googleSignInClient.signInIntent)
            }
        },
        modifier = modifier
            .fillMaxWidth()
            .height(50.dp),
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFF160B24),
        border = BorderStroke(1.2.dp, Brush.linearGradient(listOf(Color(0xFF6200EE), Color(0xFFFF0266)))),
        tonalElevation = 8.dp,
        shadowElevation = 2.dp,
        enabled = !isAuthenticating
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (isAuthenticating) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = Color.White,
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Authenticating...".localize(appLanguage),
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            } else {
                Icon(
                    imageVector = GoogleIconVector,
                    contentDescription = "Google Logo",
                    tint = Color.Unspecified,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Sign in with Google".localize(appLanguage),
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.3.sp
                )
            }
        }
    }
}

/**
 * A sleek, high-fidelity profile avatar that shows the user's real Google photo.
 * Clicking on the profile avatar displays a beautiful popup card containing their account details.
 */
@Composable
fun UserProfileIcon(
    user: FirebaseUser,
    modifier: Modifier = Modifier,
    appLanguage: String = "en"
) {
    var showDetailsDialog by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .size(40.dp)
            .clip(CircleShape)
            .border(
                border = BorderStroke(
                    width = 1.5.dp,
                    brush = Brush.sweepGradient(
                        colors = listOf(
                            Color(0xFF6200EE),
                            Color(0xFFFF0266),
                            Color(0xFF03DAC6),
                            Color(0xFF6200EE)
                        )
                    )
                ),
                shape = CircleShape
            )
            .clickable { showDetailsDialog = true },
        contentAlignment = Alignment.Center
    ) {
        if (user.photoUrl != null) {
            AsyncImage(
                model = user.photoUrl.toString(),
                contentDescription = "Google Profile Picture",
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape),
                contentScale = ContentScale.Crop
            )
        } else {
            // Elegant fallback character avatar with dark velvet-purple gradient
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0xFF311B92), Color(0xFF120338))
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                val initial = (user.displayName ?: user.email ?: "?")
                    .take(1)
                    .uppercase()
                Text(
                    text = initial,
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Black
                )
            }
        }
    }

    if (showDetailsDialog) {
        UserProfileDetailsDialog(
            user = user,
            appLanguage = appLanguage,
            onDismiss = { showDetailsDialog = false }
        )
    }
}

val SwitchAccountIcon: ImageVector
    get() = ImageVector.Builder(
        name = "SwitchAccount",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(
            fill = SolidColor(Color.White),
            pathBuilder = {
                moveTo(19f, 8f)
                lineTo(15f, 4f)
                lineTo(15f, 7f)
                lineTo(7f, 7f)
                lineTo(7f, 9f)
                lineTo(15f, 9f)
                lineTo(15f, 12f)
                close()
            }
        )
        path(
            fill = SolidColor(Color.White),
            pathBuilder = {
                moveTo(5f, 16f)
                lineTo(9f, 20f)
                lineTo(9f, 17f)
                lineTo(17f, 17f)
                lineTo(17f, 15f)
                lineTo(9f, 15f)
                lineTo(9f, 12f)
                close()
            }
        )
    }.build()

@Composable
fun OnboardingFeatureItem(
    icon: ImageVector,
    title: String,
    description: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF2E1949)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color(0xFFE040FB),
                modifier = Modifier.size(16.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(
                text = title,
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = description,
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 10.sp,
                lineHeight = 13.sp,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserProfileDetailsDialog(
    user: FirebaseUser?,
    appLanguage: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var isOperating by remember { mutableStateOf(false) }
    var errorDialogText by remember { mutableStateOf<String?>(null) }

    val gso = remember {
        GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken("720068421416-9hjjstf34i93hrkfvva41j23u4cvaes6.apps.googleusercontent.com")
            .requestEmail()
            .build()
    }

    val googleSignInClient = remember(context) {
        GoogleSignIn.getClient(context, gso)
    }

    val switchAccountLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(com.google.android.gms.common.api.ApiException::class.java)
            val idToken = account?.idToken
            if (idToken != null) {
                isOperating = true
                FirebaseCloudManager.signInWithGoogle(idToken) { success, err ->
                    isOperating = false
                    if (success) {
                        Toast.makeText(context, "Successfully Logged In!".localize(appLanguage), Toast.LENGTH_SHORT).show()
                        onDismiss()
                    } else {
                        val errorText = err ?: "Authentication failed"
                        val appCheckInstructions = getAppCheckErrorInstructions(errorText, appLanguage)
                        if (appCheckInstructions != null) {
                            errorDialogText = appCheckInstructions
                        } else {
                            Toast.makeText(context, errorText, Toast.LENGTH_LONG).show()
                        }
                    }
                }
            } else {
                isOperating = false
            }
        } catch (e: com.google.android.gms.common.api.ApiException) {
            isOperating = false
            val code = e.statusCode
            if (code == 12501 || code == 12502) {
                // User cancelled or closed the prompt, handle gracefully with a simple Toast and no scary popup
                Toast.makeText(context, if (appLanguage.lowercase() == "hi" || appLanguage.lowercase() == "hindi") "साइन-इन रद्द कर दिया गया" else "Sign-in canceled", Toast.LENGTH_SHORT).show()
            } else if (code == 10) {
                errorDialogText = if (appLanguage.lowercase() == "hi" || appLanguage.lowercase() == "hindi") {
                    "गूगल साइन-इन त्रुटि हुई है (DEVELOPER_ERROR)। कृपया बाद में प्रयास करें या सहायता टीम से संपर्क करें।"
                } else {
                    "Google Sign-In Error occurred (DEVELOPER_ERROR). Please try again later or contact our support team."
                }
            } else {
                errorDialogText = if (appLanguage.lowercase() == "hi" || appLanguage.lowercase() == "hindi") "साइन-इन त्रुटि (कोड $code)" else "Google Sign-In Error (code $code)"
            }
        } catch (e: Exception) {
            isOperating = false
            Toast.makeText(context, "Sign In: ${e.localizedMessage ?: "Canceled"}", Toast.LENGTH_LONG).show()
        }
    }

    if (errorDialogText != null) {
        AlertDialog(
            onDismissRequest = { errorDialogText = null },
            title = {
                Text(
                    text = if (appLanguage.lowercase() == "hi" || appLanguage.lowercase() == "hindi") "साइन-इन त्रुटि" else "Sign-In Error",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error
                )
            },
            text = {
                Text(
                    text = errorDialogText ?: "",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            confirmButton = {
                TextButton(onClick = { errorDialogText = null }) {
                    Text(if (appLanguage.lowercase() == "hi" || appLanguage.lowercase() == "hindi") "ठीक है" else "OK")
                }
            }
        )
    }

    Dialog(onDismissRequest = { if (!isOperating) onDismiss() }) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF140D22)),
            border = BorderStroke(1.dp, Color(0xFF332050))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (user != null) {
                    // Large styled avatar
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .clip(CircleShape)
                            .border(
                                border = BorderStroke(
                                    width = 2.5.dp,
                                    brush = Brush.sweepGradient(
                                        colors = listOf(
                                            Color(0xFF6200EE),
                                            Color(0xFFFF0266),
                                            Color(0xFF03DAC6),
                                            Color(0xFF6200EE)
                                        )
                                    )
                                ),
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (user.photoUrl != null) {
                            AsyncImage(
                                model = user.photoUrl.toString(),
                                contentDescription = "Google Profile Picture",
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(CircleShape),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.verticalGradient(
                                            listOf(Color(0xFF311B92), Color(0xFF120338))
                                        )
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                val initial = (user.displayName ?: user.email ?: "?")
                                    .take(1)
                                    .uppercase()
                                Text(
                                    text = initial,
                                    color = Color.White,
                                    fontSize = 32.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // User Display Name / Email
                    Text(
                        text = user.displayName ?: "Verified Creator".localize(appLanguage),
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = user.email ?: "Anonymous Creator",
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 4.dp)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Optional creator tag badge
                    Surface(
                        shape = RoundedCornerShape(100.dp),
                        color = Color(0xFF2E1949),
                        border = BorderStroke(1.dp, Color(0xFFE040FB).copy(alpha = 0.3f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.WorkspacePremium,
                                contentDescription = null,
                                tint = Color(0xFFFFD700),
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Reel Creator Club".localize(appLanguage),
                                color = Color(0xFFE040FB),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black
                            )
                        }
                    }
                } else {
                    // Premium Studio Logo & Brand Onboarding
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .padding(4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        FilmReelLogo(modifier = Modifier.fillMaxSize())
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Yashora Pro Studio".localize(appLanguage),
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold,
                        textAlign = TextAlign.Center
                    )

                    Text(
                        text = "JOIN CREATOR STUDIO CLUB".localize(appLanguage),
                        color = Color(0xFFE040FB),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 2.dp, bottom = 18.dp)
                    )

                    // Unified benefits list
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OnboardingFeatureItem(
                            icon = Icons.Default.Cloud,
                            title = "Instant Cloud Sync & Backup".localize(appLanguage),
                            description = "Never lose your scripts or reels. Save layouts, templates and generations safely.".localize(appLanguage)
                        )
                        OnboardingFeatureItem(
                            icon = Icons.Default.Share,
                            title = "Community Showcase Pool".localize(appLanguage),
                            description = "Share your creations instantly with the active public creator pool.".localize(appLanguage)
                        )
                        OnboardingFeatureItem(
                            icon = Icons.Default.FlashOn,
                            title = "High Priority Synthesis".localize(appLanguage),
                            description = "Enjoy elevated queuing status with high performance server speeds.".localize(appLanguage)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                if (isOperating) {
                    CircularProgressIndicator(
                        color = Color(0xFFE040FB),
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Processing...".localize(appLanguage),
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 12.sp
                    )
                } else {
                    if (user != null) {
                        // Switch Account Button (YouTube Style)
                        Button(
                            onClick = {
                                isOperating = true
                                googleSignInClient.signOut().addOnCompleteListener {
                                    FirebaseCloudManager.signOut()
                                    isOperating = false
                                    switchAccountLauncher.launch(googleSignInClient.signInIntent)
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF6200EE).copy(alpha = 0.15f),
                                contentColor = Color(0xFF9E82F0)
                            ),
                            border = BorderStroke(1.dp, Color(0xFF6200EE).copy(alpha = 0.3f))
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = SwitchAccountIcon,
                                    contentDescription = "Switch Account",
                                    tint = Color(0xFF9E82F0),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Switch Account".localize(appLanguage),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Sign Out Button
                        Button(
                            onClick = {
                                isOperating = true
                                googleSignInClient.signOut().addOnCompleteListener {
                                    FirebaseCloudManager.signOut()
                                    isOperating = false
                                    Toast.makeText(context, "Logged out successfully.".localize(appLanguage), Toast.LENGTH_SHORT).show()
                                    onDismiss()
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFFF0266).copy(alpha = 0.15f),
                                contentColor = Color(0xFFFF0266)
                            ),
                            border = BorderStroke(1.dp, Color(0xFFFF0266).copy(alpha = 0.3f))
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Logout,
                                    contentDescription = "Sign Out",
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Sign Out".localize(appLanguage),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Close Button
                        TextButton(onClick = onDismiss) {
                            Text(
                                text = "Dismiss".localize(appLanguage),
                                color = Color.White.copy(alpha = 0.5f),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    } else {
                        // Premium Google Sign-In Pill for Guests
                        Button(
                            onClick = {
                                isOperating = true
                                googleSignInClient.signOut().addOnCompleteListener {
                                    isOperating = false
                                    switchAccountLauncher.launch(googleSignInClient.signInIntent)
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF6200EE),
                                contentColor = Color.White
                            ),
                            border = BorderStroke(1.dp, Color(0xFF9E82F0).copy(alpha = 0.5f)),
                            elevation = ButtonDefaults.buttonElevation(defaultElevation = 6.dp)
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = GoogleIconVector,
                                    contentDescription = "Google Logo",
                                    tint = Color.Unspecified,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "Continue with Google".localize(appLanguage),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    letterSpacing = 0.2.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Maybe Later Close Button
                        TextButton(onClick = onDismiss) {
                            Text(
                                text = "Maybe Later".localize(appLanguage),
                                color = Color.White.copy(alpha = 0.5f),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun getAppCheckErrorInstructions(errorText: String, appLanguage: String): String? {
    if (errorText.contains("App Check", ignoreCase = true) || errorText.contains("AppCheck", ignoreCase = true)) {
        return if (appLanguage.lowercase() == "hi" || appLanguage.lowercase() == "hindi") {
            "फ़ायरबेस ऐप चेक (Firebase App Check) त्रुटि आई है:\n\"$errorText\"\n\n" +
            "इसका मतलब है कि आपके Firebase प्रोजेक्ट में App Check चालू है, लेकिन यह डिवाइस पंजीकृत नहीं है।\n\n" +
            "इसे ठीक करने के दो तरीके हैं:\n\n" +
            "तरीका 1 (सबसे आसान और तेज़):\n" +
            "1. Firebase Console -> App Check -> APIs में जाएं।\n" +
            "2. 'Identity Platform' या 'Authentication' के सामने 'Unenforce' या 'Disable' बटन दबाएं ताकि टेस्टिंग में बाधा न आए।\n\n" +
            "तरीका 2 (सुरक्षित तरीका):\n" +
            "1. अपने Android Device / Emulator को कनेक्ट करें और Logcat में 'AppCheck' या 'DebugAppCheck' खोजें।\n" +
            "2. वहां आपको एक Debug Token मिलेगा (जैसे: `XXXXXX-XXXX-XXXX-XXXX-XXXXXXXXXXXX`).\n" +
            "3. Firebase Console -> App Check -> Apps -> your App -> Manage Debug Tokens में जाकर वह टोकन जोड़ें।"
        } else {
            "Firebase App Check error occurred:\n\"$errorText\"\n\n" +
            "This means Firebase App Check is enabled in your console, but this development device is not registered.\n\n" +
            "There are two ways to solve this:\n\n" +
            "Method 1 (Easiest & Fastest for Testing):\n" +
            "1. Go to Firebase Console -> App Check -> APIs.\n" +
            "2. Click 'Unenforce' or 'Disable' next to 'Identity Platform' or 'Authentication' to bypass App Check during development.\n\n" +
            "Method 2 (Secure Debug Registration):\n" +
            "1. Connect your Android Device/Emulator, run the app, and search for 'AppCheck' or 'DebugAppCheck' in Logcat.\n" +
            "2. Copy the printed App Check Debug Token (looks like: `XXXXXX-XXXX-XXXX-XXXX-XXXXXXXXXXXX`).\n" +
            "3. Go to Firebase Console -> App Check -> Apps -> your App -> Manage Debug Tokens, and add this token."
        }
    }
    return null
}

