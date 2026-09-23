package com.ritvyom.yashoraReelgenerator.presentation.components

import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.ritvyom.yashoraReelgenerator.data.remote.FirebaseCloudManager
import com.ritvyom.yashoraReelgenerator.presentation.utils.localize
import com.google.firebase.auth.FirebaseUser
import com.google.android.gms.auth.api.identity.BeginSignInRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions

/**
 * Resolves the Google Web Client ID dynamically from google-services.json generated resources
 * with a reliable fallback to prevent initialization crashes.
 */
fun getGoogleWebClientId(context: Context): String {
    return try {
        val resId = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
        if (resId != 0) {
            context.getString(resId)
        } else {
            "259389653327-rnbqsmg7rk197tso7cmg2nra9rrfu10c.apps.googleusercontent.com"
        }
    } catch (e: Exception) {
        "259389653327-rnbqsmg7rk197tso7cmg2nra9rrfu10c.apps.googleusercontent.com"
    }
}

/**
 * Pixel-perfect Google 'G' vector icon matching official branding guidelines.
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
 * Google Identity Services (GIS) One-Tap + Firebase Auth 1-Click Sign-In Button component.
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
    val isHindi = appLanguage.lowercase() in listOf("hi", "hindi")

    val clientId = remember(context) { getGoogleWebClientId(context) }

    // Google Identity Services (One-Tap) Client & Request
    val oneTapClient = remember(context) { Identity.getSignInClient(context) }
    val signInRequest = remember(clientId) {
        BeginSignInRequest.builder()
            .setGoogleIdTokenRequestOptions(
                BeginSignInRequest.GoogleIdTokenRequestOptions.builder()
                    .setSupported(true)
                    .setServerClientId(clientId)
                    .setFilterByAuthorizedAccounts(false)
                    .build()
            )
            .setAutoSelectEnabled(true)
            .build()
    }

    // Fallback standard Google Sign-In options
    val gso = remember(clientId) {
        GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(clientId)
            .requestEmail()
            .build()
    }
    val googleSignInClient = remember(context, gso) {
        GoogleSignIn.getClient(context, gso)
    }

    val localOnFailure: (String) -> Unit = { error ->
        errorDialogText = error
        onFailure(error)
    }

    // Standard Sign-In Fallback Launcher
    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(com.google.android.gms.common.api.ApiException::class.java)
            val idToken = account?.idToken
            if (idToken != null) {
                isAuthenticating = true
                FirebaseCloudManager.signInWithGoogle(idToken) { success, err ->
                    isAuthenticating = false
                    if (success) {
                        Toast.makeText(context, "Successfully Signed In with Google!".localize(appLanguage), Toast.LENGTH_SHORT).show()
                        onSuccess()
                    } else {
                        localOnFailure(err ?: "Google authentication failed")
                    }
                }
            } else {
                localOnFailure("Google credentials ID token not obtained")
            }
        } catch (e: com.google.android.gms.common.api.ApiException) {
            val code = e.statusCode
            if (code == 12501 || code == 12502) {
                Toast.makeText(context, "Sign-in canceled".localize(appLanguage), Toast.LENGTH_SHORT).show()
            } else {
                android.util.Log.e("GoogleSignIn", "Google Sign-In ApiException code: $code")
                localOnFailure(getUserFriendlyAuthErrorMessage(appLanguage, code))
            }
        } catch (e: Exception) {
            android.util.Log.e("GoogleSignIn", "Google Sign-In Exception: ${e.message}", e)
            localOnFailure(getUserFriendlyAuthErrorMessage(appLanguage))
        }
    }

    // One-Tap GIS Launcher
    val oneTapLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        try {
            val credential = oneTapClient.getSignInCredentialFromIntent(result.data)
            val idToken = credential.googleIdToken
            if (idToken != null) {
                isAuthenticating = true
                FirebaseCloudManager.signInWithGoogle(idToken) { success, err ->
                    isAuthenticating = false
                    if (success) {
                        Toast.makeText(context, "Successfully Signed In with Google!".localize(appLanguage), Toast.LENGTH_SHORT).show()
                        onSuccess()
                    } else {
                        localOnFailure(getUserFriendlyAuthErrorMessage(appLanguage))
                    }
                }
            } else {
                // Fallback to standard Google Sign-In
                googleSignInLauncher.launch(googleSignInClient.signInIntent)
            }
        } catch (e: com.google.android.gms.common.api.ApiException) {
            val code = e.statusCode
            if (code == 16 || code == 12501 || code == 12502) { // Canceled
                Toast.makeText(context, "Sign-in canceled".localize(appLanguage), Toast.LENGTH_SHORT).show()
            } else {
                android.util.Log.e("GoogleSignIn", "One-Tap ApiException code: $code")
                // Seamless fallback to standard sign in or friendly message
                googleSignInLauncher.launch(googleSignInClient.signInIntent)
            }
        } catch (e: Exception) {
            googleSignInLauncher.launch(googleSignInClient.signInIntent)
        }
    }

    fun startOneTapLogin() {
        if (isAuthenticating) return
        isAuthenticating = true
        oneTapClient.beginSignIn(signInRequest)
            .addOnSuccessListener { result ->
                isAuthenticating = false
                val intentSenderRequest = IntentSenderRequest.Builder(result.pendingIntent.intentSender).build()
                oneTapLauncher.launch(intentSenderRequest)
            }
            .addOnFailureListener {
                isAuthenticating = false
                // Seamless fallback to standard Google Sign-In intent
                googleSignInLauncher.launch(googleSignInClient.signInIntent)
            }
    }

    if (errorDialogText != null) {
        AuthErrorDialog(
            errorMessage = errorDialogText ?: "",
            appLanguage = appLanguage,
            onRetry = {
                errorDialogText = null
                startOneTapLogin()
            },
            onDismiss = { errorDialogText = null }
        )
    }

    Surface(
        onClick = {
            startOneTapLogin()
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
 * A sleek profile avatar that displays user details or opens the 1-click Google account dialog.
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

/**
 * Google Identity Services One-Tap Account Dialog.
 * Zero manual inputs — instant 1-tap Google Account picker and Firebase authentication.
 */
@Composable
fun UserProfileDetailsDialog(
    user: FirebaseUser?,
    appLanguage: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var isOperating by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var isDeletingAccount by remember { mutableStateOf(false) }
    var showPrivacyDialog by remember { mutableStateOf(false) }
    var errorDialogText by remember { mutableStateOf<String?>(null) }
    val isHindi = appLanguage.lowercase() in listOf("hi", "hindi")

    val clientId = remember(context) { getGoogleWebClientId(context) }

    // Google Identity Services (One-Tap) Client
    val oneTapClient = remember(context) { Identity.getSignInClient(context) }
    val signInRequest = remember(clientId) {
        BeginSignInRequest.builder()
            .setGoogleIdTokenRequestOptions(
                BeginSignInRequest.GoogleIdTokenRequestOptions.builder()
                    .setSupported(true)
                    .setServerClientId(clientId)
                    .setFilterByAuthorizedAccounts(false)
                    .build()
            )
            .setAutoSelectEnabled(true)
            .build()
    }

    val gso = remember(clientId) {
        GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(clientId)
            .requestEmail()
            .build()
    }
    val googleSignInClient = remember(context, gso) {
        GoogleSignIn.getClient(context, gso)
    }

    val googleSignInLauncher = rememberLauncherForActivityResult(
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
                        Toast.makeText(context, "Successfully Logged In with Google!".localize(appLanguage), Toast.LENGTH_SHORT).show()
                        onDismiss()
                    } else {
                        errorDialogText = err ?: "Authentication failed"
                    }
                }
            } else {
                isOperating = false
            }
        } catch (e: com.google.android.gms.common.api.ApiException) {
            isOperating = false
            val code = e.statusCode
            if (code == 12501 || code == 12502) {
                Toast.makeText(context, "Sign-in canceled".localize(appLanguage), Toast.LENGTH_SHORT).show()
            } else {
                android.util.Log.e("GoogleSignIn", "Dialog Google Sign-In ApiException code: $code")
                errorDialogText = getUserFriendlyAuthErrorMessage(appLanguage, code)
            }
        } catch (e: Exception) {
            isOperating = false
            android.util.Log.e("GoogleSignIn", "Dialog Google Sign-In Exception: ${e.message}", e)
            errorDialogText = getUserFriendlyAuthErrorMessage(appLanguage)
        }
    }

    val oneTapLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        try {
            val credential = oneTapClient.getSignInCredentialFromIntent(result.data)
            val idToken = credential.googleIdToken
            if (idToken != null) {
                isOperating = true
                FirebaseCloudManager.signInWithGoogle(idToken) { success, err ->
                    isOperating = false
                    if (success) {
                        Toast.makeText(context, "Successfully Logged In with Google!".localize(appLanguage), Toast.LENGTH_SHORT).show()
                        onDismiss()
                    } else {
                        errorDialogText = getUserFriendlyAuthErrorMessage(appLanguage)
                    }
                }
            } else {
                googleSignInLauncher.launch(googleSignInClient.signInIntent)
            }
        } catch (e: com.google.android.gms.common.api.ApiException) {
            val code = e.statusCode
            if (code == 16 || code == 12501 || code == 12502) {
                Toast.makeText(context, "Sign-in canceled".localize(appLanguage), Toast.LENGTH_SHORT).show()
            } else {
                android.util.Log.e("GoogleSignIn", "Dialog One-Tap ApiException code: $code")
                googleSignInLauncher.launch(googleSignInClient.signInIntent)
            }
        } catch (e: Exception) {
            googleSignInLauncher.launch(googleSignInClient.signInIntent)
        }
    }

    fun startOneTapDialogLogin() {
        if (isOperating || isDeletingAccount) return
        isOperating = true
        oneTapClient.beginSignIn(signInRequest)
            .addOnSuccessListener { result ->
                isOperating = false
                val intentSenderRequest = IntentSenderRequest.Builder(result.pendingIntent.intentSender).build()
                oneTapLauncher.launch(intentSenderRequest)
            }
            .addOnFailureListener {
                isOperating = false
                googleSignInLauncher.launch(googleSignInClient.signInIntent)
            }
    }

    fun executeAccountDeletion() {
        if (isDeletingAccount) return
        isDeletingAccount = true
        FirebaseCloudManager.deleteAccount { success, error ->
            if (success) {
                oneTapClient.signOut().addOnCompleteListener {
                    googleSignInClient.signOut().addOnCompleteListener {
                        isDeletingAccount = false
                        showDeleteConfirmDialog = false
                        Toast.makeText(
                            context,
                            "Your account and all cloud database records have been permanently deleted.".localize(appLanguage),
                            Toast.LENGTH_LONG
                        ).show()
                        onDismiss()
                    }
                }
            } else {
                isDeletingAccount = false
                showDeleteConfirmDialog = false
                if (error == "REQUIRES_RECENT_LOGIN") {
                    errorDialogText = "For security reasons, account deletion requires a recent sign-in. Please tap 'Switch Google Account' to re-authenticate, then retry deletion.".localize(appLanguage)
                } else {
                    errorDialogText = ("Failed to delete account: " + (error ?: "Unknown error")).localize(appLanguage)
                }
            }
        }
    }

    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { if (!isDeletingAccount) showDeleteConfirmDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = "Warning",
                    tint = Color(0xFFFF5252),
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text(
                    text = "Delete Account Permanently?".localize(appLanguage),
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFFF5252)
                )
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Are you sure you want to delete your account?\n\n• Your Google login link will be removed.\n• All your cloud drafts, shared community scripts, and database records will be permanently erased.\n• This action is irreversible.".localize(appLanguage),
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (isDeletingAccount) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                color = Color(0xFFFF5252),
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "Deleting from database...".localize(appLanguage),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFFFF5252)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { executeAccountDeletion() },
                    enabled = !isDeletingAccount,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFFF5252),
                        contentColor = Color.White
                    )
                ) {
                    Text("Delete Permanently".localize(appLanguage), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDeleteConfirmDialog = false },
                    enabled = !isDeletingAccount
                ) {
                    Text("Cancel".localize(appLanguage))
                }
            }
        )
    }

    if (errorDialogText != null) {
        AuthErrorDialog(
            errorMessage = errorDialogText ?: "",
            appLanguage = appLanguage,
            onRetry = {
                errorDialogText = null
                startOneTapDialogLogin()
            },
            onDismiss = { errorDialogText = null }
        )
    }

    Dialog(onDismissRequest = { if (!isOperating) onDismiss() }) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF140D22)),
            border = BorderStroke(1.dp, Color(0xFF332050))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(22.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (user != null) {
                    // Logged In Profile View
                    Box(
                        modifier = Modifier
                            .size(76.dp)
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
                                contentDescription = "Profile Picture",
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
                                    fontSize = 28.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = user.displayName?.ifBlank { null } ?: "Verified Creator".localize(appLanguage),
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = user.email ?: "Google Account Active",
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 4.dp)
                    )

                    Spacer(modifier = Modifier.height(14.dp))

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

                    Spacer(modifier = Modifier.height(22.dp))

                    if (isOperating) {
                        CircularProgressIndicator(
                            color = Color(0xFFE040FB),
                            modifier = Modifier.size(28.dp)
                        )
                    } else {
                        // 1-Click Switch Google Account Button
                        Button(
                            onClick = {
                                isOperating = true
                                oneTapClient.signOut().addOnCompleteListener {
                                    googleSignInClient.signOut().addOnCompleteListener {
                                        FirebaseCloudManager.signOut()
                                        isOperating = false
                                        startOneTapDialogLogin()
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth().height(46.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF6200EE).copy(alpha = 0.2f),
                                contentColor = Color(0xFF9E82F0)
                            ),
                            border = BorderStroke(1.dp, Color(0xFF6200EE).copy(alpha = 0.4f))
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
                                    text = "Switch Google Account".localize(appLanguage),
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
                                oneTapClient.signOut().addOnCompleteListener {
                                    googleSignInClient.signOut().addOnCompleteListener {
                                        FirebaseCloudManager.signOut()
                                        isOperating = false
                                        Toast.makeText(context, "Logged out successfully.".localize(appLanguage), Toast.LENGTH_SHORT).show()
                                        onDismiss()
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth().height(46.dp),
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

                        Spacer(modifier = Modifier.height(10.dp))

                        // Delete Account & Database Data Button
                        OutlinedButton(
                            onClick = {
                                showDeleteConfirmDialog = true
                            },
                            modifier = Modifier.fillMaxWidth().height(46.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = Color(0xFFFF5252)
                            ),
                            border = BorderStroke(1.dp, Color(0xFFFF5252).copy(alpha = 0.45f))
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DeleteForever,
                                    contentDescription = "Delete Account",
                                    tint = Color(0xFFFF5252),
                                    modifier = Modifier.size(17.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Delete Account & Data".localize(appLanguage),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFFF5252)
                                )
                            }
                        }
                    }
                } else {
                    // 1-Click Google Identity Services (One-Tap) Hub
                    Box(
                        modifier = Modifier
                            .size(68.dp)
                            .padding(2.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        YashoraLogo(modifier = Modifier.fillMaxSize())
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "Yashora Pro Studio",
                        color = Color.White,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.ExtraBold,
                        textAlign = TextAlign.Center
                    )

                    Text(
                        text = "1-TAP GOOGLE SIGN-IN".localize(appLanguage),
                        color = Color(0xFFE040FB),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 3.dp, bottom = 16.dp)
                    )

                    // Features highlight
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OnboardingFeatureItem(
                            icon = Icons.Default.Cloud,
                            title = "Cloud Sync & Project Backup".localize(appLanguage),
                            description = "Save templates and generations safely across devices.".localize(appLanguage)
                        )
                        OnboardingFeatureItem(
                            icon = Icons.Default.FlashOn,
                            title = "High Priority AI Synthesis".localize(appLanguage),
                            description = "Fast generation queuing for high quality voice and scenes.".localize(appLanguage)
                        )
                        OnboardingFeatureItem(
                            icon = Icons.Default.WorkspacePremium,
                            title = "Pro Creator Access".localize(appLanguage),
                            description = "Unlock all cinematography tools and community reels.".localize(appLanguage)
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    if (isOperating) {
                        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = Color(0xFFE040FB), modifier = Modifier.size(32.dp))
                        }
                    } else {
                        // Single 1-Click Google Identity Services (One-Tap) Button
                        Surface(
                            onClick = {
                                startOneTapDialogLogin()
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp),
                            shape = RoundedCornerShape(14.dp),
                            color = Color(0xFF6200EE),
                            border = BorderStroke(1.2.dp, Color(0xFF9E82F0)),
                            shadowElevation = 4.dp
                        ) {
                            Row(
                                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = GoogleIconVector,
                                    contentDescription = "Google Logo",
                                    tint = Color.Unspecified,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = "Continue with Google".localize(appLanguage),
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Privacy Policy Link
                TextButton(
                    onClick = { showPrivacyDialog = true },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "Read Yashora Privacy Policy".localize(appLanguage),
                        color = Color(0xFF90CAF9),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                // Dismiss Button
                TextButton(onClick = onDismiss) {
                    Text(
                        text = "Maybe Later".localize(appLanguage),
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }

    if (showPrivacyDialog) {
        PrivacyPolicyDialog(
            languageState = appLanguage,
            onDismiss = { showPrivacyDialog = false }
        )
    }
}

/**
 * Returns a polite, professional, user-centric error message instead of raw developer logs.
 */
fun getUserFriendlyAuthErrorMessage(appLanguage: String, errorCode: Int? = null): String {
    val norm = com.ritvyom.yashoraReelgenerator.presentation.utils.Localization.normalizeLanguageKey(appLanguage)
    return when (norm) {
        "HINDI" -> {
            "Google साइन-इन वर्तमान में कनेक्ट नहीं हो सका।\n\n" +
            "• आप बिना साइन-इन के भी वीडियो जनरेशन, ड्राफ्ट्स और एडिटिंग टूल्स का इस्तेमाल जारी रख सकते हैं।\n" +
            "• कृपया अपना इंटरनेट कनेक्शन जांचें या कुछ समय बाद पुनः प्रयास करें।"
        }
        "SPANISH" -> {
            "No se pudo completar el inicio de sesión con Google en este momento.\n\n" +
            "• Puedes seguir usando la generación de video y los borradores sin iniciar sesión.\n" +
            "• Por favor revisa tu conexión a internet o intenta nuevamente en unos momentos."
        }
        else -> {
            "Unable to connect to Google Sign-In services at this moment.\n\n" +
            "• You can continue using offline reel generation, drafts, and video tools normally.\n" +
            "• Please check your internet connection or try again in a few moments."
        }
    }
}

/**
 * Modern, beautifully designed Auth Error Dialog with friendly messaging,
 * helpful recovery options, and a retry action button.
 */
@Composable
fun AuthErrorDialog(
    errorMessage: String,
    appLanguage: String,
    onRetry: () -> Unit,
    onDismiss: () -> Unit
) {
    val norm = com.ritvyom.yashoraReelgenerator.presentation.utils.Localization.normalizeLanguageKey(appLanguage)
    val dialogTitle = when (norm) {
        "HINDI" -> "साइन-इन कनेक्ट नहीं हो सका"
        "SPANISH" -> "Inicio de Sesión No Disponible"
        else -> "Sign-In Temporarily Unavailable"
    }
    val retryText = when (norm) {
        "HINDI" -> "पुनः प्रयास करें"
        "SPANISH" -> "Reintentar"
        else -> "Try Again"
    }
    val continueText = when (norm) {
        "HINDI" -> "बिना साइन-इन जारी रखें"
        "SPANISH" -> "Continuar como invitado"
        else -> "Continue as Guest"
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .wrapContentHeight(),
            shape = RoundedCornerShape(24.dp),
            color = Color(0xFF140E20),
            border = BorderStroke(1.2.dp, Color(0xFF3B2554)),
            shadowElevation = 12.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Gradient Icon Badge
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                colors = listOf(Color(0xFF9C27B0), Color(0xFFE040FB))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.CloudOff,
                        contentDescription = "Connection Notice",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Title
                Text(
                    text = dialogTitle,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Error / Explanatory Card
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFF1E1430),
                    border = BorderStroke(1.dp, Color(0xFF4A3368).copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = errorMessage,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 12.5.sp,
                            lineHeight = 17.5.sp
                        ),
                        color = Color(0xFFE1BEE7),
                        modifier = Modifier.padding(14.dp)
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Actions: Retry (Primary) + Continue (Secondary)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Color(0xFF5E35B1).copy(alpha = 0.6f)),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = Color(0xFF1B122C),
                            contentColor = Color(0xFFD1C4E9)
                        ),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = continueText,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1
                        )
                    }

                    Button(
                        onClick = {
                            onDismiss()
                            onRetry()
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF9C27B0),
                            contentColor = Color.White
                        ),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Retry",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = retryText,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}
