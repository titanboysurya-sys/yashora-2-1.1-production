package com.ritvyom.yashoraReelgenerator.data.remote

import android.util.Log
import com.ritvyom.yashoraReelgenerator.domain.models.Scene
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.Serializable

data class SharedProject(
    val id: String = "",
    val userId: String = "",
    val userEmail: String = "",
    val title: String = "",
    val topic: String = "",
    val scriptText: String = "",
    val style: String = "",
    val aspectRatio: String = "9:16",
    val visualMedium: String = "Video",
    val publishingStyle: String = "TikTok / Instagram Reels",
    val scenes: List<SharedScene> = emptyList(),
    val timestamp: Long = 0L,
    val likesCount: Int = 0,
    val likedUsers: List<String> = emptyList(),
    val isUserEdited: Boolean = false,
    val exportedVideoUrl: String = ""
) : Serializable

data class SharedScene(
    val sceneNumber: Int = 0,
    val narrationText: String = "",
    val visualPrompt: String = "",
    val durationSeconds: Int = 5,
    val subtitle: String = "",
    val mediaPath: String = "", // Holds the external URL (Pexels, Unsplash, Pixabay etc.)
    val keywords: List<String> = emptyList()
) : Serializable

object FirebaseCloudManager {

    private const val TAG = "FirebaseCloudManager"
    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    
    @Volatile
    private var preferDefaultDb = false

    private val firestore: FirebaseFirestore
        get() {
            if (preferDefaultDb) {
                return FirebaseFirestore.getInstance()
            }
            return try {
                FirebaseFirestore.getInstance("yashora")
            } catch (e: Exception) {
                preferDefaultDb = true
                FirebaseFirestore.getInstance()
            }
        }

    private fun markUseDefaultDb() {
        if (!preferDefaultDb) {
            Log.w(TAG, "Firestore 'yashora' database failed or unavailable. Switching automatically to default Firestore database instance.")
            preferDefaultDb = true
        }
    }

    private val _currentUserState = MutableStateFlow<FirebaseUser?>(auth.currentUser)
    val currentUserState: StateFlow<FirebaseUser?> = _currentUserState

    init {
        auth.addAuthStateListener { firebaseAuth ->
            _currentUserState.value = firebaseAuth.currentUser
            Log.d(TAG, "Auth state updated: user is ${firebaseAuth.currentUser?.email ?: "Logged Out"}")
        }
    }

    private val storage: com.google.firebase.storage.FirebaseStorage by lazy {
        com.google.firebase.storage.FirebaseStorage.getInstance()
    }

    suspend fun uploadLocalMediaToCloud(localPath: String, destinationPath: String): String? = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val file = java.io.File(localPath)
        if (!file.exists() || file.length() == 0L) return@withContext null

        val deferred = kotlinx.coroutines.CompletableDeferred<String?>()
        try {
            val ref = storage.reference.child(destinationPath)
            val uri = android.net.Uri.fromFile(file)
            ref.putFile(uri)
                .addOnSuccessListener {
                    ref.downloadUrl
                        .addOnSuccessListener { downloadUri ->
                            Log.i(TAG, "Uploaded local media to Firebase Storage: $downloadUri")
                            deferred.complete(downloadUri.toString())
                        }
                        .addOnFailureListener {
                            Log.w(TAG, "Failed getting downloadUrl from Firebase Storage", it)
                            deferred.complete(null)
                        }
                }
                .addOnFailureListener {
                    Log.w(TAG, "Failed to upload file to Firebase Storage", it)
                    deferred.complete(null)
                }
        } catch (e: Exception) {
            Log.e(TAG, "Exception uploading file to Firebase Storage", e)
            deferred.complete(null)
        }

        try {
            deferred.await()
        } catch (e: Exception) {
            null
        }
    }

    fun extractRemoteMediaUrl(scene: Scene): String {
        // Priority 1: User's selected mediaPath if it is a remote URL
        val path = scene.mediaPath
        if (!path.isNullOrBlank() && (path.startsWith("http://") || path.startsWith("https://") || path.startsWith("gs://"))) {
            return path
        }
        // Priority 2: scene remoteUrl if valid
        val remote = scene.remoteUrl
        if (!remote.isNullOrBlank() && (remote.startsWith("http://") || remote.startsWith("https://") || remote.startsWith("gs://"))) {
            return remote
        }
        return ""
    }

    fun extractCoreKeywords(text: String): List<String> {
        val stopwords = setOf(
            "a", "an", "the", "and", "or", "but", "is", "are", "was", "were", "of", "to", "in", "on", "at", "by", "for", "with", "about",
            "this", "that", "it", "its", "you", "your", "my", "me", "we", "our", "us", "they", "them", "some", "any", "no", "not", "so",
            "can", "will", "show", "get", "make", "be", "have", "has", "had", "do", "does", "did", "from", "very", "scene", "style", "description",
            "video", "reel", "script", "create", "generate", "short", "shorts", "topic", "give", "like", "in", "about", "for", "par", "pe"
        )
        return text.lowercase()
            .replace(Regex("[^\\p{L}\\p{N}\\s]"), " ")
            .split(Regex("\\s+"))
            .filter { it.length >= 2 && !stopwords.contains(it) }
            .distinct()
    }

    fun calculateSemanticMatchScore(query: String, target: String): Double {
        val queryTokens = extractCoreKeywords(query)
        val targetTokens = extractCoreKeywords(target)
        if (queryTokens.isEmpty() || targetTokens.isEmpty()) return 0.0
        val matches = queryTokens.count { qToken ->
            targetTokens.any { tToken ->
                tToken == qToken || tToken.contains(qToken) || qToken.contains(tToken)
            }
        }
        return matches.toDouble() / queryTokens.size.toDouble()
    }

    fun getCurrentUser(): FirebaseUser? {
        return auth.currentUser
    }

    fun isUserLoggedIn(): Boolean {
        return auth.currentUser != null
    }

    fun signUp(email: String, password: String, displayName: String = "", onResult: (Boolean, String?) -> Unit) {
        if (email.isBlank() || password.isBlank()) {
            onResult(false, "Email and Password cannot be empty.")
            return
        }
        if (password.length < 6) {
            onResult(false, "Password must be at least 6 characters.")
            return
        }
        auth.createUserWithEmailAndPassword(email.trim(), password)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val user = auth.currentUser
                    if (displayName.isNotBlank() && user != null) {
                        val profileUpdates = com.google.firebase.auth.UserProfileChangeRequest.Builder()
                            .setDisplayName(displayName.trim())
                            .build()
                        user.updateProfile(profileUpdates).addOnCompleteListener {
                            _currentUserState.value = auth.currentUser
                            onResult(true, null)
                        }
                    } else {
                        _currentUserState.value = auth.currentUser
                        onResult(true, null)
                    }
                } else {
                    onResult(false, task.exception?.localizedMessage ?: "Signup failed.")
                }
            }
    }

    fun signIn(email: String, password: String, onResult: (Boolean, String?) -> Unit) {
        if (email.isBlank() || password.isBlank()) {
            onResult(false, "Email and Password cannot be empty.")
            return
        }
        auth.signInWithEmailAndPassword(email.trim(), password)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    _currentUserState.value = auth.currentUser
                    onResult(true, null)
                } else {
                    onResult(false, task.exception?.localizedMessage ?: "Sign-in failed.")
                }
            }
    }

    fun resetPassword(email: String, onResult: (Boolean, String?) -> Unit) {
        if (email.isBlank()) {
            onResult(false, "Please enter your email.")
            return
        }
        auth.sendPasswordResetEmail(email.trim())
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    onResult(true, null)
                } else {
                    onResult(false, task.exception?.localizedMessage ?: "Password reset failed.")
                }
            }
    }

    fun signInWithGoogle(idToken: String, onResult: (Boolean, String?) -> Unit) {
        val credential = com.google.firebase.auth.GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    _currentUserState.value = auth.currentUser
                    onResult(true, null)
                } else {
                    onResult(false, task.exception?.localizedMessage ?: "Google Sign-In failed.")
                }
            }
    }

    fun signOut() {
        auth.signOut()
        _currentUserState.value = null
    }

    /**
     * Completely deletes the user account and all associated cloud data from the database.
     * Deletes user's project subcollection, user profile document, shared projects, and Firebase Auth record.
     */
    fun deleteAccount(onResult: (Boolean, String?) -> Unit) {
        val user = auth.currentUser
        if (user == null) {
            onResult(false, "No active user account found.")
            return
        }
        val userId = user.uid

        // 1. Delete user-specific cloud projects from Firestore
        firestore.collection("users").document(userId).collection("projects").get()
            .addOnSuccessListener { querySnapshot ->
                val batch = firestore.batch()
                for (doc in querySnapshot.documents) {
                    batch.delete(doc.reference)
                }
                batch.delete(firestore.collection("users").document(userId))

                batch.commit().addOnCompleteListener {
                    // 2. Also clean up any user-shared community scripts
                    firestore.collection("shared_scripts")
                        .whereEqualTo("userId", userId)
                        .get()
                        .addOnSuccessListener { sharedDocs ->
                            val sharedBatch = firestore.batch()
                            for (doc in sharedDocs.documents) {
                                sharedBatch.delete(doc.reference)
                            }
                            sharedBatch.commit().addOnCompleteListener {
                                // 3. Delete Firebase Auth User Record
                                user.delete().addOnCompleteListener { deleteTask ->
                                    if (deleteTask.isSuccessful) {
                                        _currentUserState.value = null
                                        onResult(true, null)
                                    } else {
                                        val ex = deleteTask.exception
                                        if (ex is com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException) {
                                            onResult(false, "REQUIRES_RECENT_LOGIN")
                                        } else {
                                            onResult(false, ex?.localizedMessage ?: "Failed to delete Firebase Auth account.")
                                        }
                                    }
                                }
                            }
                        }
                        .addOnFailureListener {
                            // If shared_scripts query fails, proceed to delete auth user
                            user.delete().addOnCompleteListener { deleteTask ->
                                if (deleteTask.isSuccessful) {
                                    _currentUserState.value = null
                                    onResult(true, null)
                                } else {
                                    val ex = deleteTask.exception
                                    if (ex is com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException) {
                                        onResult(false, "REQUIRES_RECENT_LOGIN")
                                    } else {
                                        onResult(false, ex?.localizedMessage ?: "Failed to delete Firebase Auth account.")
                                    }
                                }
                            }
                        }
                }
            }
            .addOnFailureListener {
                // If firestore read fails, still attempt to delete user auth account
                user.delete().addOnCompleteListener { deleteTask ->
                    if (deleteTask.isSuccessful) {
                        _currentUserState.value = null
                        onResult(true, null)
                    } else {
                        val ex = deleteTask.exception
                        if (ex is com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException) {
                            onResult(false, "REQUIRES_RECENT_LOGIN")
                        } else {
                            onResult(false, ex?.localizedMessage ?: "Failed to delete Firebase Auth account.")
                        }
                    }
                }
            }
    }

    /**
     * Share a newly generated script and small media metadata (URLs) with the community pool.
     */
    fun shareProject(
        title: String,
        topic: String,
        scriptText: String,
        style: String,
        aspectRatio: String,
        visualMedium: String,
        publishingStyle: String,
        scenes: List<Scene>,
        exportedVideoUrl: String = "",
        isUserEdited: Boolean = false,
        onResult: (Boolean, String?) -> Unit
    ) {
        val user = auth.currentUser
        val userId = user?.uid ?: "community_creator_${System.currentTimeMillis()}"
        val userEmail = user?.email ?: "creator@yashora.app"

        val sharedScenes = scenes.map { scene ->
            SharedScene(
                sceneNumber = scene.sceneNumber,
                narrationText = scene.narrationText,
                visualPrompt = scene.visualPrompt,
                durationSeconds = scene.durationSeconds,
                subtitle = scene.subtitle,
                mediaPath = extractRemoteMediaUrl(scene),
                keywords = scene.keywords
            )
        }

        val cleanScript = scriptText.trim()
        val scriptHash = computeScriptHash(cleanScript)
        val scriptMd5 = computeScriptMd5(cleanScript)

        val now = java.util.Date()
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
        val isoSdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US).apply {
            timeZone = java.util.TimeZone.getTimeZone("UTC")
        }
        val dateFormatted = sdf.format(now)
        val isoFormatted = isoSdf.format(now)

        val documentData = hashMapOf(
            "userId" to userId,
            "userEmail" to userEmail,
            "title" to title.trim(),
            "topic" to topic.trim(),
            "scriptText" to cleanScript,
            "scriptHash" to scriptHash,
            "scriptMd5" to scriptMd5,
            "style" to style,
            "aspectRatio" to aspectRatio,
            "visualMedium" to visualMedium,
            "publishingStyle" to publishingStyle,
            "isUserEdited" to isUserEdited,
            "isApproved" to true,
            "exportedVideoUrl" to exportedVideoUrl,
            "scenes" to sharedScenes.map {
                hashMapOf(
                    "sceneNumber" to it.sceneNumber,
                    "narrationText" to it.narrationText,
                    "visualPrompt" to it.visualPrompt,
                    "durationSeconds" to it.durationSeconds,
                    "subtitle" to it.subtitle,
                    "mediaPath" to it.mediaPath,
                    "keywords" to it.keywords
                )
            },
            "timestamp" to now.time,
            "createdAt" to dateFormatted,
            "formattedDate" to dateFormatted,
            "isoDate" to isoFormatted,
            "likesCount" to 0
        )

        fun doShare(db: FirebaseFirestore) {
            // 1. Save to user-specific subcollection if userId is available
            if (userId.isNotBlank()) {
                try {
                    db.collection("users")
                        .document(userId)
                        .collection("projects")
                        .add(documentData)
                        .addOnSuccessListener { docRef ->
                            Log.i(TAG, "Successfully persisted project under users/$userId/projects/${docRef.id}")
                        }
                        .addOnFailureListener { ex ->
                            Log.w(TAG, "Failed writing to users/$userId/projects", ex)
                        }
                } catch (e: Exception) {
                    Log.e(TAG, "Exception saving to user projects collection", e)
                }
            }

            // 2. Save to global shared_scripts pool
            // If this is a user-edited version, find and update any existing document for this user & topic or scriptHash
            if (isUserEdited && userId.isNotBlank()) {
                db.collection("shared_scripts")
                    .whereEqualTo("userId", userId)
                    .whereEqualTo("topic", topic.trim())
                    .limit(1)
                    .get()
                    .addOnSuccessListener { querySnap ->
                        if (!querySnap.isEmpty) {
                            val existingDocId = querySnap.documents[0].id
                            db.collection("shared_scripts").document(existingDocId).set(documentData)
                                .addOnSuccessListener {
                                    Log.i(TAG, "Updated existing shared_scripts document $existingDocId with user edits!")
                                    onResult(true, null)
                                }
                                .addOnFailureListener {
                                    db.collection("shared_scripts").add(documentData)
                                        .addOnSuccessListener { onResult(true, null) }
                                        .addOnFailureListener { err -> onResult(false, err.localizedMessage) }
                                }
                        } else {
                            db.collection("shared_scripts").add(documentData)
                                .addOnSuccessListener {
                                    Log.i(TAG, "Project successfully shared in Firestore.")
                                    onResult(true, null)
                                }
                                .addOnFailureListener { ex ->
                                    onResult(false, ex.localizedMessage ?: "Failed to upload to community pool.")
                                }
                        }
                    }
                    .addOnFailureListener {
                        db.collection("shared_scripts").add(documentData)
                            .addOnSuccessListener { onResult(true, null) }
                            .addOnFailureListener { ex -> onResult(false, ex.localizedMessage) }
                    }
            } else {
                db.collection("shared_scripts")
                    .add(documentData)
                    .addOnSuccessListener {
                        Log.i(TAG, "Project successfully shared in Firestore.")
                        onResult(true, null)
                    }
                    .addOnFailureListener { ex ->
                        if (!preferDefaultDb) {
                            markUseDefaultDb()
                            doShare(firestore)
                        } else {
                            Log.e(TAG, "Failed to share project in Firestore", ex)
                            onResult(false, ex.localizedMessage ?: "Failed to upload to community pool.")
                        }
                    }
            }
        }
        doShare(firestore)
    }

    /**
     * Fetch community shared reels / scripts
     */
    fun fetchSharedProjects(onResult: (List<SharedProject>, String?) -> Unit) {
        fun doFetch(db: FirebaseFirestore) {
            db.collection("shared_scripts")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(50)
                .get()
                .addOnSuccessListener { querySnapshot ->
                    val list = mutableListOf<SharedProject>()
                    for (doc in querySnapshot.documents) {
                        try {
                            val id = doc.id
                            val userId = doc.getString("userId") ?: ""
                            val userEmail = doc.getString("userEmail") ?: ""
                            val title = doc.getString("title") ?: ""
                            val topic = doc.getString("topic") ?: ""
                            val scriptText = doc.getString("scriptText") ?: ""
                            val style = doc.getString("style") ?: ""
                            val aspectRatio = doc.getString("aspectRatio") ?: "9:16"
                            val visualMedium = doc.getString("visualMedium") ?: "Video"
                            val publishingStyle = doc.getString("publishingStyle") ?: "TikTok / Instagram Reels"
                            val likesCount = doc.getLong("likesCount")?.toInt() ?: 0
                            val likedUsers = (doc.get("likedUsers") as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList()
                            val timestamp = doc.getLong("timestamp") ?: 0L

                            val rawScenes = doc.get("scenes") as? List<Map<String, Any>> ?: emptyList()
                            val scenes = rawScenes.map { map ->
                                SharedScene(
                                    sceneNumber = (map["sceneNumber"] as? Long)?.toInt() ?: 0,
                                    narrationText = map["narrationText"] as? String ?: "",
                                    visualPrompt = map["visualPrompt"] as? String ?: "",
                                    durationSeconds = (map["durationSeconds"] as? Long)?.toInt() ?: 5,
                                    subtitle = map["subtitle"] as? String ?: "",
                                    mediaPath = map["mediaPath"] as? String ?: "",
                                    keywords = (map["keywords"] as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList()
                                )
                            }

                            val isUserEdited = doc.getBoolean("isUserEdited") == true
                            val exportedVideoUrl = doc.getString("exportedVideoUrl") ?: ""

                            list.add(
                                SharedProject(
                                    id = id,
                                    userId = userId,
                                    userEmail = userEmail,
                                    title = title,
                                    topic = topic,
                                    scriptText = scriptText,
                                    style = style,
                                    aspectRatio = aspectRatio,
                                    visualMedium = visualMedium,
                                    publishingStyle = publishingStyle,
                                    scenes = scenes,
                                    timestamp = timestamp,
                                    likesCount = likesCount,
                                    likedUsers = likedUsers,
                                    isUserEdited = isUserEdited,
                                    exportedVideoUrl = exportedVideoUrl
                                )
                            )
                        } catch (e: Exception) {
                            Log.e(TAG, "Error parsing shared project document", e)
                        }
                    }
                    list.sortWith(compareByDescending<SharedProject> { it.isUserEdited }.thenByDescending { it.timestamp })
                    onResult(list, null)
                }
                .addOnFailureListener { ex ->
                    if (!preferDefaultDb) {
                        markUseDefaultDb()
                        doFetch(firestore)
                    } else {
                        Log.e(TAG, "Error fetching shared projects", ex)
                        onResult(emptyList(), ex.localizedMessage ?: "Failed to load community pool.")
                    }
                }
        }
        doFetch(firestore)
    }

    /**
     * Search community scripts by topic or title keyword
     */
    fun searchSharedProjects(query: String, onResult: (List<SharedProject>, String?) -> Unit) {
        if (query.isBlank()) {
            fetchSharedProjects(onResult)
            return
        }
        val lowercaseQuery = query.lowercase().trim()
        fun doSearch(db: FirebaseFirestore) {
            db.collection("shared_scripts")
                .limit(100)
                .get()
                .addOnSuccessListener { querySnapshot ->
                    val list = mutableListOf<SharedProject>()
                    for (doc in querySnapshot.documents) {
                        try {
                            val title = doc.getString("title") ?: ""
                            val topic = doc.getString("topic") ?: ""
                            if (title.lowercase().contains(lowercaseQuery) || topic.lowercase().contains(lowercaseQuery)) {
                                val id = doc.id
                                val userId = doc.getString("userId") ?: ""
                                val userEmail = doc.getString("userEmail") ?: ""
                                val scriptText = doc.getString("scriptText") ?: ""
                                val style = doc.getString("style") ?: ""
                                val aspectRatio = doc.getString("aspectRatio") ?: "9:16"
                                val visualMedium = doc.getString("visualMedium") ?: "Video"
                                val publishingStyle = doc.getString("publishingStyle") ?: "TikTok / Instagram Reels"
                                val likesCount = doc.getLong("likesCount")?.toInt() ?: 0
                                val likedUsers = (doc.get("likedUsers") as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList()
                                val timestamp = doc.getLong("timestamp") ?: 0L

                                val rawScenes = doc.get("scenes") as? List<Map<String, Any>> ?: emptyList()
                                val scenes = rawScenes.map { map ->
                                    SharedScene(
                                        sceneNumber = (map["sceneNumber"] as? Long)?.toInt() ?: 0,
                                        narrationText = map["narrationText"] as? String ?: "",
                                        visualPrompt = map["visualPrompt"] as? String ?: "",
                                        durationSeconds = (map["durationSeconds"] as? Long)?.toInt() ?: 5,
                                        subtitle = map["subtitle"] as? String ?: "",
                                        mediaPath = map["mediaPath"] as? String ?: "",
                                        keywords = (map["keywords"] as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList()
                                    )
                                }

                                val isUserEdited = doc.getBoolean("isUserEdited") == true
                                val exportedVideoUrl = doc.getString("exportedVideoUrl") ?: ""

                                list.add(
                                    SharedProject(
                                        id = id,
                                        userId = userId,
                                        userEmail = userEmail,
                                        title = title,
                                        topic = topic,
                                        scriptText = scriptText,
                                        style = style,
                                        aspectRatio = aspectRatio,
                                        visualMedium = visualMedium,
                                        publishingStyle = publishingStyle,
                                        scenes = scenes,
                                        timestamp = timestamp,
                                        likesCount = likesCount,
                                        likedUsers = likedUsers,
                                        isUserEdited = isUserEdited,
                                        exportedVideoUrl = exportedVideoUrl
                                    )
                                )
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error parsing matching document", e)
                        }
                    }
                    list.sortWith(compareByDescending<SharedProject> { it.isUserEdited }.thenByDescending { it.timestamp })
                    onResult(list, null)
                }
                .addOnFailureListener { ex ->
                    if (!preferDefaultDb) {
                        markUseDefaultDb()
                        doSearch(firestore)
                    } else {
                        Log.e(TAG, "Error searching shared projects", ex)
                        onResult(emptyList(), ex.localizedMessage ?: "Failed to perform search.")
                    }
                }
        }
        doSearch(firestore)
    }

    fun computeScriptHash(script: String): String {
        val normalized = script.trim().lowercase().replace("\\s+".toRegex(), " ")
        if (normalized.isEmpty()) return ""
        return try {
            val md = java.security.MessageDigest.getInstance("SHA-256")
            val digest = md.digest(normalized.toByteArray(Charsets.UTF_8))
            digest.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            normalized.hashCode().toString()
        }
    }

    fun computeScriptMd5(script: String): String {
        val normalized = script.trim().lowercase().replace("\\s+".toRegex(), " ")
        if (normalized.isEmpty()) return ""
        return try {
            val md = java.security.MessageDigest.getInstance("MD5")
            val digest = md.digest(normalized.toByteArray(Charsets.UTF_8))
            digest.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            normalized.hashCode().toString()
        }
    }

    private fun parseSharedProjectDocument(
        doc: com.google.firebase.firestore.DocumentSnapshot,
        fallbackScript: String = "",
        fallbackStyle: String = "",
        fallbackAspectRatio: String = "9:16",
        fallbackVisualMedium: String = "Video"
    ): SharedProject? {
        try {
            val id = doc.id
            val userId = doc.getString("userId") ?: "system"
            val userEmail = doc.getString("userEmail") ?: "cache@system"
            val title = doc.getString("title") ?: doc.getString("topic") ?: "Pre-rendered Reel"
            val dbTopic = doc.getString("topic") ?: ""
            val docScriptText = doc.getString("scriptText") ?: fallbackScript
            val docStyle = doc.getString("style") ?: fallbackStyle
            val docAspectRatio = doc.getString("aspectRatio") ?: fallbackAspectRatio
            val docVisualMedium = doc.getString("visualMedium") ?: fallbackVisualMedium
            val publishingStyle = doc.getString("publishingStyle") ?: "TikTok / Instagram Reels"
            val likesCount = doc.getLong("likesCount")?.toInt() ?: 0
            val timestamp = doc.getLong("timestamp") ?: System.currentTimeMillis()

            val rawScenes = doc.get("scenes") as? List<Map<String, Any>> ?: emptyList()
            val scenes = rawScenes.map { map ->
                SharedScene(
                    sceneNumber = (map["sceneNumber"] as? Long)?.toInt() ?: 0,
                    narrationText = map["narrationText"] as? String ?: "",
                    visualPrompt = map["visualPrompt"] as? String ?: "",
                    durationSeconds = (map["durationSeconds"] as? Long)?.toInt() ?: 5,
                    subtitle = map["subtitle"] as? String ?: "",
                    mediaPath = map["mediaPath"] as? String ?: "",
                    keywords = (map["keywords"] as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList()
                )
            }

            if (scenes.isNotEmpty()) {
                val isUserEdited = doc.getBoolean("isUserEdited") == true
                val exportedVideoUrl = doc.getString("exportedVideoUrl") ?: ""
                return SharedProject(
                    id = id,
                    userId = userId,
                    userEmail = userEmail,
                    title = title,
                    topic = dbTopic,
                    scriptText = docScriptText,
                    style = docStyle,
                    aspectRatio = docAspectRatio,
                    visualMedium = docVisualMedium,
                    publishingStyle = publishingStyle,
                    scenes = scenes,
                    timestamp = timestamp,
                    likesCount = likesCount,
                    likedUsers = emptyList(),
                    isUserEdited = isUserEdited,
                    exportedVideoUrl = exportedVideoUrl
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing pre-rendered asset document", e)
        }
        return null
    }

    /**
     * Checks Firestore for pre-rendered assets matching the generated script hash before calling external APIs.
     * Effectively implements a content-caching strategy to save API costs.
     */
    fun findPrerenderedAssetsByScriptHash(
        scriptText: String,
        style: String,
        aspectRatio: String,
        visualMedium: String,
        onResult: (SharedProject?) -> Unit
    ) {
        val cleanScript = scriptText.trim()
        if (cleanScript.isBlank()) {
            onResult(null)
            return
        }

        val targetHash = computeScriptHash(cleanScript)
        val targetMd5 = computeScriptMd5(cleanScript)
        val docId = "${targetHash}_${style}_${aspectRatio}_${visualMedium}"

        fun doSearch(db: FirebaseFirestore) {
            // 1. Direct document ID lookup in 'prerendered_assets'
            db.collection("prerendered_assets")
                .document(docId)
                .get()
                .addOnSuccessListener { doc ->
                    if (doc.exists()) {
                        val proj = parseSharedProjectDocument(doc, cleanScript, style, aspectRatio, visualMedium)
                        if (proj != null) {
                            Log.i(TAG, "Direct pre-rendered asset cache HIT in prerendered_assets for docId $docId")
                            onResult(proj)
                            return@addOnSuccessListener
                        }
                    }

                    // 2. Query 'prerendered_assets' by scriptHash or scriptMd5
                    db.collection("prerendered_assets")
                        .whereEqualTo("scriptHash", targetHash)
                        .limit(5)
                        .get()
                        .addOnSuccessListener { querySnapshot ->
                            val sortedPrerendered = querySnapshot.documents.sortedWith(
                                compareByDescending<com.google.firebase.firestore.DocumentSnapshot> { 
                                    it.getBoolean("isUserEdited") == true 
                                }.thenByDescending { 
                                    it.getLong("timestamp") ?: 0L 
                                }
                            )
                            for (d in sortedPrerendered) {
                                val proj = parseSharedProjectDocument(d, cleanScript, style, aspectRatio, visualMedium)
                                if (proj != null) {
                                    Log.i(TAG, "Pre-rendered asset cache HIT by scriptHash $targetHash (isUserEdited=${d.getBoolean("isUserEdited")})")
                                    onResult(proj)
                                    return@addOnSuccessListener
                                }
                            }

                            // 3. Search 'shared_scripts' collection by scriptHash or exact scriptText match
                            db.collection("shared_scripts")
                                .get()
                                .addOnSuccessListener { sharedSnapshot ->
                                    val sortedShared = sharedSnapshot.documents.sortedWith(
                                        compareByDescending<com.google.firebase.firestore.DocumentSnapshot> { 
                                            it.getBoolean("isUserEdited") == true 
                                        }.thenByDescending { 
                                            it.getLong("timestamp") ?: 0L 
                                        }
                                    )
                                    for (d in sortedShared) {
                                        val docHash = d.getString("scriptHash") ?: ""
                                        val docMd5 = d.getString("scriptMd5") ?: ""
                                        val dbScript = d.getString("scriptText") ?: ""

                                        val scriptMatches = (docHash.isNotEmpty() && docHash == targetHash) ||
                                                (docMd5.isNotEmpty() && docMd5 == targetMd5) ||
                                                (dbScript.isNotBlank() && computeScriptHash(dbScript) == targetHash) ||
                                                (dbScript.isNotBlank() && dbScript.trim().lowercase() == cleanScript.lowercase())

                                        if (scriptMatches) {
                                            val proj = parseSharedProjectDocument(d, cleanScript, style, aspectRatio, visualMedium)
                                            if (proj != null) {
                                                Log.i(TAG, "Pre-rendered asset HIT in shared_scripts matching script text/hash! (isUserEdited=${d.getBoolean("isUserEdited")})")
                                                onResult(proj)
                                                return@addOnSuccessListener
                                            }
                                        }
                                    }
                                    onResult(null)
                                }
                                .addOnFailureListener {
                                    onResult(null)
                                }
                        }
                        .addOnFailureListener {
                            onResult(null)
                        }
                }
                .addOnFailureListener { ex ->
                    if (!preferDefaultDb) {
                        markUseDefaultDb()
                        doSearch(firestore)
                    } else {
                        Log.w(TAG, "Failed pre-rendered asset lookup in Firestore", ex)
                        onResult(null)
                    }
                }
        }
        doSearch(firestore)
    }

    /**
     * Save pre-rendered asset cache to Firestore to eliminate future external API calls for identical script text.
     */
    fun savePrerenderedAssetCache(
        scriptText: String,
        style: String,
        aspectRatio: String,
        visualMedium: String,
        scenes: List<Scene>,
        topic: String = "",
        originalScriptText: String = ""
    ) {
        val cleanScript = scriptText.trim()
        if (cleanScript.isEmpty() || scenes.isEmpty()) return

        val scriptHash = computeScriptHash(cleanScript)
        val scriptMd5 = computeScriptMd5(cleanScript)
        val docId = "${scriptHash}_${style}_${aspectRatio}_${visualMedium}"

        val sharedScenes = scenes.map { scene ->
            hashMapOf(
                "sceneNumber" to scene.sceneNumber,
                "narrationText" to scene.narrationText,
                "visualPrompt" to scene.visualPrompt,
                "durationSeconds" to scene.durationSeconds,
                "subtitle" to scene.subtitle,
                "mediaPath" to extractRemoteMediaUrl(scene),
                "keywords" to scene.keywords
            )
        }

        val now = java.util.Date()
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
        val isoSdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US).apply {
            timeZone = java.util.TimeZone.getTimeZone("UTC")
        }
        val dateFormatted = sdf.format(now)
        val isoFormatted = isoSdf.format(now)

        val data = hashMapOf(
            "scriptHash" to scriptHash,
            "scriptMd5" to scriptMd5,
            "scriptText" to cleanScript,
            "topic" to topic,
            "style" to style,
            "aspectRatio" to aspectRatio,
            "visualMedium" to visualMedium,
            "scenes" to sharedScenes,
            "isUserEdited" to true,
            "isApproved" to true,
            "timestamp" to now.time,
            "createdAt" to dateFormatted,
            "formattedDate" to dateFormatted,
            "isoDate" to isoFormatted
        )

        fun doSave(db: FirebaseFirestore) {
            db.collection("prerendered_assets")
                .document(docId)
                .set(data)
                .addOnSuccessListener {
                    Log.i(TAG, "Successfully saved pre-rendered asset cache to Firestore for scriptHash: $scriptHash")
                }
                .addOnFailureListener { ex ->
                    if (!preferDefaultDb) {
                        markUseDefaultDb()
                        doSave(firestore)
                    } else {
                        Log.w(TAG, "Failed to save pre-rendered asset cache", ex)
                    }
                }

            db.collection("project_cache")
                .document(docId)
                .set(data)
                .addOnFailureListener { ex ->
                    Log.w(TAG, "Failed to save pre-rendered asset cache to project_cache", ex)
                }

            // Also alias under originalScriptText if user edited the script from original AI prompt
            if (originalScriptText.isNotBlank() && originalScriptText.trim() != cleanScript) {
                val origClean = originalScriptText.trim()
                val origHash = computeScriptHash(origClean)
                val origDocId = "${origHash}_${style}_${aspectRatio}_${visualMedium}"
                val aliasData = HashMap(data).apply {
                    put("scriptHash", origHash)
                    put("originalScriptHash", origHash)
                    put("userEditedScriptHash", scriptHash)
                }
                db.collection("prerendered_assets").document(origDocId).set(aliasData)
                db.collection("project_cache").document(origDocId).set(aliasData)
            }
        }
        doSave(firestore)
    }

    /**
     * Crucial Feature: Check if an existing shared script/media matches the user's requested topic & specifications.
     * If yes, we can serve it immediately from Firebase to save costs and skip LLM generation entirely!
     */
    fun findMatchingProject(
        topic: String,
        style: String,
        aspectRatio: String,
        visualMedium: String,
        onResult: (SharedProject?) -> Unit
    ) {
        val cleanTopic = topic.trim().lowercase()
        if (cleanTopic.isBlank()) {
            onResult(null)
            return
        }

        fun doMatch(db: FirebaseFirestore) {
            db.collection("shared_scripts")
                .whereEqualTo("style", style)
                .whereEqualTo("aspectRatio", aspectRatio)
                .whereEqualTo("visualMedium", visualMedium)
                .get()
                .addOnSuccessListener { querySnapshot ->
                    val sortedDocs = querySnapshot.documents.sortedWith(
                        compareByDescending<com.google.firebase.firestore.DocumentSnapshot> { 
                            it.getBoolean("isUserEdited") == true 
                        }.thenByDescending { 
                            it.getLong("timestamp") ?: 0L 
                        }
                    )
                    for (doc in sortedDocs) {
                        val dbTopic = doc.getString("topic") ?: ""
                        val dbTitle = doc.getString("title") ?: ""
                        
                        // Smart semantic match check: require exact topic/title or >= 75% core keyword overlap
                        val topicScore = calculateSemanticMatchScore(cleanTopic, dbTopic)
                        val titleScore = calculateSemanticMatchScore(cleanTopic, dbTitle)
                        val isMatch = dbTopic.lowercase().trim() == cleanTopic || 
                                      dbTitle.lowercase().trim() == cleanTopic ||
                                      topicScore >= 0.75 ||
                                      titleScore >= 0.75

                        if (isMatch) {
                            try {
                                val id = doc.id
                                val userId = doc.getString("userId") ?: ""
                                val userEmail = doc.getString("userEmail") ?: ""
                                val title = doc.getString("title") ?: ""
                                val scriptText = doc.getString("scriptText") ?: ""
                                val publishingStyle = doc.getString("publishingStyle") ?: "TikTok / Instagram Reels"
                                val likesCount = doc.getLong("likesCount")?.toInt() ?: 0
                                val timestamp = doc.getLong("timestamp") ?: 0L
                                val isUserEdited = doc.getBoolean("isUserEdited") == true
                                val exportedVideoUrl = doc.getString("exportedVideoUrl") ?: ""

                                val rawScenes = doc.get("scenes") as? List<Map<String, Any>> ?: emptyList()
                                val scenes = rawScenes.map { map ->
                                    SharedScene(
                                        sceneNumber = (map["sceneNumber"] as? Long)?.toInt() ?: 0,
                                        narrationText = map["narrationText"] as? String ?: "",
                                        visualPrompt = map["visualPrompt"] as? String ?: "",
                                        durationSeconds = (map["durationSeconds"] as? Long)?.toInt() ?: 5,
                                        subtitle = map["subtitle"] as? String ?: "",
                                        mediaPath = map["mediaPath"] as? String ?: "",
                                        keywords = (map["keywords"] as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList()
                                    )
                                }

                                if (scenes.isNotEmpty() && scriptText.isNotBlank()) {
                                    Log.i(TAG, "findMatchingProject matched! isUserEdited=$isUserEdited, scenes=${scenes.size}")
                                    onResult(
                                        SharedProject(
                                            id = id,
                                            userId = userId,
                                            userEmail = userEmail,
                                            title = title,
                                            topic = dbTopic,
                                            scriptText = scriptText,
                                            style = style,
                                            aspectRatio = aspectRatio,
                                            visualMedium = visualMedium,
                                            publishingStyle = publishingStyle,
                                            scenes = scenes,
                                            timestamp = timestamp,
                                            likesCount = likesCount,
                                            isUserEdited = isUserEdited,
                                            exportedVideoUrl = exportedVideoUrl
                                        )
                                    )
                                    return@addOnSuccessListener
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "Error parsing matched project", e)
                            }
                        }
                    }

                    // Secondary fallback: search broader shared_scripts collection for topic match if strict filters had no hit
                    db.collection("shared_scripts")
                        .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
                        .limit(50)
                        .get()
                        .addOnSuccessListener { broadSnapshot ->
                            val sortedBroadDocs = broadSnapshot.documents.sortedWith(
                                compareByDescending<com.google.firebase.firestore.DocumentSnapshot> { 
                                    it.getBoolean("isUserEdited") == true 
                                }.thenByDescending { 
                                    it.getLong("timestamp") ?: 0L 
                                }
                            )
                            for (doc in sortedBroadDocs) {
                                val dbTopic = doc.getString("topic") ?: ""
                                val dbTitle = doc.getString("title") ?: ""
                                val topicScore = calculateSemanticMatchScore(cleanTopic, dbTopic)
                                val titleScore = calculateSemanticMatchScore(cleanTopic, dbTitle)
                                val isMatch = dbTopic.lowercase().trim() == cleanTopic ||
                                              dbTitle.lowercase().trim() == cleanTopic ||
                                              topicScore >= 0.80 ||
                                              titleScore >= 0.80

                                if (isMatch) {
                                    try {
                                        val id = doc.id
                                        val userId = doc.getString("userId") ?: ""
                                        val userEmail = doc.getString("userEmail") ?: ""
                                        val title = doc.getString("title") ?: ""
                                        val scriptText = doc.getString("scriptText") ?: ""
                                        val docStyle = doc.getString("style") ?: style
                                        val docAspectRatio = doc.getString("aspectRatio") ?: aspectRatio
                                        val docVisualMedium = doc.getString("visualMedium") ?: visualMedium
                                        val publishingStyle = doc.getString("publishingStyle") ?: "TikTok / Instagram Reels"
                                        val likesCount = doc.getLong("likesCount")?.toInt() ?: 0
                                        val timestamp = doc.getLong("timestamp") ?: 0L
                                        val isUserEdited = doc.getBoolean("isUserEdited") == true
                                        val exportedVideoUrl = doc.getString("exportedVideoUrl") ?: ""

                                        val rawScenes = doc.get("scenes") as? List<Map<String, Any>> ?: emptyList()
                                        val scenes = rawScenes.map { map ->
                                            SharedScene(
                                                sceneNumber = (map["sceneNumber"] as? Long)?.toInt() ?: 0,
                                                narrationText = map["narrationText"] as? String ?: "",
                                                visualPrompt = map["visualPrompt"] as? String ?: "",
                                                durationSeconds = (map["durationSeconds"] as? Long)?.toInt() ?: 5,
                                                subtitle = map["subtitle"] as? String ?: "",
                                                mediaPath = map["mediaPath"] as? String ?: "",
                                                keywords = (map["keywords"] as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList()
                                            )
                                        }

                                        if (scenes.isNotEmpty() && scriptText.isNotBlank()) {
                                            Log.d(TAG, "Broader Cloud Pool HIT for topic '$cleanTopic' (isUserEdited=$isUserEdited)!")
                                            onResult(
                                                SharedProject(
                                                    id = id,
                                                    userId = userId,
                                                    userEmail = userEmail,
                                                    title = title,
                                                    topic = dbTopic,
                                                    scriptText = scriptText,
                                                    style = docStyle,
                                                    aspectRatio = docAspectRatio,
                                                    visualMedium = docVisualMedium,
                                                    publishingStyle = publishingStyle,
                                                    scenes = scenes,
                                                    timestamp = timestamp,
                                                    likesCount = likesCount,
                                                    isUserEdited = isUserEdited,
                                                    exportedVideoUrl = exportedVideoUrl
                                                )
                                            )
                                            return@addOnSuccessListener
                                        }
                                    } catch (e: Exception) {
                                        Log.e(TAG, "Error parsing broader matched project", e)
                                    }
                                }
                            }

                            // Step 3: Search 'prerendered_assets' collection for matching topic
                            db.collection("prerendered_assets")
                                .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
                                .limit(50)
                                .get()
                                .addOnSuccessListener { prerenderedSnapshot ->
                                    val sortedPrerenderedDocs = prerenderedSnapshot.documents.sortedWith(
                                        compareByDescending<com.google.firebase.firestore.DocumentSnapshot> { 
                                            it.getBoolean("isUserEdited") == true 
                                        }.thenByDescending { 
                                            it.getLong("timestamp") ?: 0L 
                                        }
                                    )
                                    for (doc in sortedPrerenderedDocs) {
                                        val dbTopic = doc.getString("topic") ?: ""
                                        val topicScore = calculateSemanticMatchScore(cleanTopic, dbTopic)
                                        if (dbTopic.lowercase().trim() == cleanTopic || topicScore >= 0.75) {
                                            val proj = parseSharedProjectDocument(doc, "", style, aspectRatio, visualMedium)
                                            if (proj != null) {
                                                Log.d(TAG, "Pre-rendered assets collection HIT for topic '$cleanTopic' (isUserEdited=${doc.getBoolean("isUserEdited")})!")
                                                onResult(proj)
                                                return@addOnSuccessListener
                                            }
                                        }
                                    }
                                    onResult(null)
                                }
                                .addOnFailureListener {
                                    onResult(null)
                                }
                        }
                        .addOnFailureListener {
                            onResult(null)
                        }
                }
                .addOnFailureListener {
                    if (!preferDefaultDb) {
                        markUseDefaultDb()
                        doMatch(firestore)
                    } else {
                        Log.w(TAG, "Firestore matching search failed")
                        onResult(null)
                    }
                }
        }
        doMatch(firestore)
    }

    /**
     * Community interactions: Toggle like on a project.
     * Restricts each user or device to exactly one like. Toggling again removes the like.
     */
    fun toggleLikeProject(projectId: String, userId: String, onResult: ((newCount: Int, isLiked: Boolean) -> Unit)? = null) {
        val targetUserId = userId.ifBlank { auth.currentUser?.uid ?: "anonymous_user" }
        fun doToggle(db: FirebaseFirestore) {
            val docRef = db.collection("shared_scripts").document(projectId)
            db.runTransaction { transaction ->
                val snapshot = transaction.get(docRef)
                val currentLikes = (snapshot.getLong("likesCount") ?: 0L).toInt()
                val rawLikedUsers = (snapshot.get("likedUsers") as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList()
                val userAlreadyLiked = rawLikedUsers.contains(targetUserId)

                val updatedLikedUsers = if (userAlreadyLiked) {
                    rawLikedUsers.filter { it != targetUserId }
                } else {
                    rawLikedUsers + targetUserId
                }
                val newLikesCount = if (userAlreadyLiked) {
                    (currentLikes - 1).coerceAtLeast(0)
                } else {
                    currentLikes + 1
                }

                transaction.update(docRef, mapOf(
                    "likesCount" to newLikesCount,
                    "likedUsers" to updatedLikedUsers
                ))
                Pair(newLikesCount, !userAlreadyLiked)
            }.addOnSuccessListener { result ->
                Log.d(TAG, "Project like toggled successfully: newCount=${result.first}, isLiked=${result.second}")
                onResult?.invoke(result.first, result.second)
            }.addOnFailureListener { e ->
                if (!preferDefaultDb) {
                    markUseDefaultDb()
                    doToggle(firestore)
                } else {
                    Log.e(TAG, "Error toggling like on project $projectId", e)
                    onResult?.invoke(0, false)
                }
            }
        }
        doToggle(firestore)
    }

    fun likeProject(projectId: String) {
        val uid = auth.currentUser?.uid ?: "anonymous_device"
        toggleLikeProject(projectId, uid)
    }

    /**
     * Checks Firestore for an existing script-media query mapping before calling external APIs.
     * Reuses previously fetched URLs to save on API usage and ensure consistent matching.
     */
    suspend fun getScriptMediaMapping(
        query: String,
        style: String,
        aspectRatio: String,
        visualMedium: String
    ): String? = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val cleanQuery = query.trim().lowercase()
        if (cleanQuery.isEmpty()) return@withContext null

        val deferred = kotlinx.coroutines.CompletableDeferred<String?>()
        firestore.collection("project_cache")
            .whereEqualTo("query", cleanQuery)
            .whereEqualTo("style", style)
            .whereEqualTo("aspectRatio", aspectRatio)
            .whereEqualTo("visualMedium", visualMedium)
            .limit(1)
            .get()
            .addOnSuccessListener { querySnapshot ->
                if (!querySnapshot.isEmpty) {
                    val doc = querySnapshot.documents[0]
                    val urlsList = doc.get("media_urls") as? List<*>
                    val url = if (urlsList != null && urlsList.isNotEmpty()) {
                        urlsList[0]?.toString()
                    } else {
                        doc.getString("mediaUrl")
                    }
                    Log.d(TAG, "Cloud cache HIT in project_cache for '$cleanQuery' ($style, $aspectRatio, $visualMedium): $url")
                    deferred.complete(url)
                } else {
                    // Fallback to legacy script_media_mappings if exists
                    firestore.collection("script_media_mappings")
                        .whereEqualTo("query", cleanQuery)
                        .whereEqualTo("style", style)
                        .whereEqualTo("aspectRatio", aspectRatio)
                        .whereEqualTo("visualMedium", visualMedium)
                        .limit(1)
                        .get()
                        .addOnSuccessListener { legacySnapshot ->
                            if (!legacySnapshot.isEmpty) {
                                val url = legacySnapshot.documents[0].getString("mediaUrl")
                                Log.d(TAG, "Legacy Cloud cache HIT for '$cleanQuery': $url")
                                deferred.complete(url)
                            } else {
                                deferred.complete(null)
                            }
                        }
                        .addOnFailureListener {
                            deferred.complete(null)
                        }
                }
            }
            .addOnFailureListener { ex ->
                Log.w(TAG, "Cloud cache lookup failed in project_cache for '$cleanQuery', checking legacy fallback...", ex)
                // Fallback to legacy script_media_mappings on failure
                firestore.collection("script_media_mappings")
                    .whereEqualTo("query", cleanQuery)
                    .whereEqualTo("style", style)
                    .whereEqualTo("aspectRatio", aspectRatio)
                    .whereEqualTo("visualMedium", visualMedium)
                    .limit(1)
                    .get()
                    .addOnSuccessListener { legacySnapshot ->
                        if (!legacySnapshot.isEmpty) {
                            val url = legacySnapshot.documents[0].getString("mediaUrl")
                            deferred.complete(url)
                        } else {
                            deferred.complete(null)
                        }
                    }
                    .addOnFailureListener {
                        deferred.complete(null)
                    }
            }

        try {
            deferred.await()
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Saves a newly resolved query-to-media-URL mapping to Firestore to build a shared cache.
     * Local device file paths are explicitly ignored.
     */
    fun saveScriptMediaMapping(
        query: String,
        style: String,
        aspectRatio: String,
        visualMedium: String,
        mediaUrl: String
    ) {
        val cleanQuery = query.trim().lowercase()
        if (cleanQuery.isEmpty() || mediaUrl.isEmpty()) return

        // Skip saving if it is a local path
        if (mediaUrl.startsWith("/") || mediaUrl.startsWith("file:") || mediaUrl.startsWith("content:")) {
            return
        }

        val now = java.util.Date()
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
        val isoSdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US).apply {
            timeZone = java.util.TimeZone.getTimeZone("UTC")
        }
        val dateFormatted = sdf.format(now)
        val isoFormatted = isoSdf.format(now)

        val currentTime = now.time
        val data = hashMapOf(
            "query" to cleanQuery,
            "keywords" to extractCoreKeywords(cleanQuery),
            "style" to style,
            "aspectRatio" to aspectRatio,
            "visualMedium" to visualMedium,
            "mediaUrl" to mediaUrl,
            "media_urls" to listOf(mediaUrl),
            "timestamp" to currentTime,
            "timestamps" to listOf(currentTime),
            "createdAt" to dateFormatted,
            "formattedDate" to dateFormatted,
            "isoDate" to isoFormatted
        )

        // Generate a clean, deterministic doc ID to avoid duplicates
        val hashKey = "${cleanQuery}_${style}_${aspectRatio}_${visualMedium}"
        val docId = java.util.UUID.nameUUIDFromBytes(hashKey.toByteArray()).toString()

        fun doSaveMapping(db: FirebaseFirestore) {
            // Save to modern project_cache
            db.collection("project_cache")
                .document(docId)
                .set(data)
                .addOnSuccessListener {
                    Log.d(TAG, "Cloud cache saved successfully in project_cache for '$cleanQuery' -> $mediaUrl")
                }
                .addOnFailureListener { ex ->
                    if (!preferDefaultDb) {
                        markUseDefaultDb()
                        doSaveMapping(firestore)
                    } else {
                        Log.w(TAG, "Failed to save cloud cache mapping in project_cache", ex)
                    }
                }

            // Also update legacy script_media_mappings for backward compatibility
            db.collection("script_media_mappings")
                .document(docId)
                .set(data)
                .addOnFailureListener { ex ->
                    Log.w(TAG, "Failed to save legacy cloud cache mapping", ex)
                }
        }
        doSaveMapping(firestore)
    }

    suspend fun getCachedScript(
        topicDescription: String,
        style: String,
        language: String,
        durationOption: String
    ): CloudScript? = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val cleanTopic = topicDescription.trim().lowercase()
        if (cleanTopic.isEmpty()) return@withContext null

        val cleanStyle = style.trim().lowercase()
        val cleanLanguage = language.trim().lowercase()
        val cleanDuration = durationOption.trim().lowercase()

        // 1. Check in primary 'scripts' collection by topic field first (to find duplicate topic scripts)
        val scriptsDeferred = kotlinx.coroutines.CompletableDeferred<CloudScript?>()
        firestore.collection("scripts")
            .whereEqualTo("topic", cleanTopic)
            .get()
            .addOnSuccessListener { querySnapshot ->
                if (querySnapshot != null && !querySnapshot.isEmpty) {
                    var matchedScript: CloudScript? = null
                    val sortedDocs = querySnapshot.documents.sortedWith(
                        compareByDescending<com.google.firebase.firestore.DocumentSnapshot> {
                            it.getBoolean("isUserEdited") == true
                        }.thenByDescending {
                            it.getLong("timestamp") ?: 0L
                        }
                    )
                    for (doc in sortedDocs) {
                        val docStyle = doc.getString("style")?.trim() ?: doc.getString("tone")?.trim() ?: ""
                        val docLang = doc.getString("language")?.trim() ?: ""
                        val docDur = doc.getString("durationOption")?.trim() ?: doc.getString("duration")?.trim() ?: ""

                        val scriptText = doc.getString("scriptText")
                            ?: doc.getString("fullScript")
                            ?: doc.getString("script")

                        if (!scriptText.isNullOrBlank()) {
                            val cleanDocStyle = docStyle.lowercase()
                            val cleanDocLang = docLang.lowercase()
                            val cleanDocDur = docDur.lowercase()

                            // Force language, style, and duration to match perfectly!
                            if (cleanDocLang == cleanLanguage && cleanDocStyle == cleanStyle && cleanDocDur == cleanDuration) {
                                matchedScript = CloudScript(scriptText, docStyle, docLang, docDur)
                                break
                            }
                        }
                    }
                    if (matchedScript != null) {
                        Log.d(TAG, "Cloud 'scripts' collection HIT for topic '$cleanTopic'")
                    }
                    scriptsDeferred.complete(matchedScript)
                } else {
                    scriptsDeferred.complete(null)
                }
            }
            .addOnFailureListener { ex ->
                Log.w(TAG, "Cloud 'scripts' collection query failed for topic '$cleanTopic'", ex)
                scriptsDeferred.complete(null)
            }

        val scriptsResult = try {
            scriptsDeferred.await()
        } catch (e: Exception) {
            null
        }

        if (scriptsResult != null) {
            return@withContext scriptsResult
        }

        // 1B. Broader topic search using semantic matching score in 'scripts' collection
        val broadDeferred = kotlinx.coroutines.CompletableDeferred<CloudScript?>()
        firestore.collection("scripts")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(50)
            .get()
            .addOnSuccessListener { broadSnapshot ->
                var semanticMatch: CloudScript? = null
                val sortedBroadDocs = broadSnapshot.documents.sortedWith(
                    compareByDescending<com.google.firebase.firestore.DocumentSnapshot> {
                        it.getBoolean("isUserEdited") == true
                    }.thenByDescending {
                        it.getLong("timestamp") ?: 0L
                    }
                )
                for (doc in sortedBroadDocs) {
                    val dbTopic = doc.getString("topic") ?: ""
                    val score = calculateSemanticMatchScore(cleanTopic, dbTopic)
                    if (score >= 0.75 || dbTopic.lowercase().trim() == cleanTopic) {
                        val docStyle = doc.getString("style")?.trim() ?: doc.getString("tone")?.trim() ?: ""
                        val docLang = doc.getString("language")?.trim() ?: ""
                        val docDur = doc.getString("durationOption")?.trim() ?: doc.getString("duration")?.trim() ?: ""
                        val scriptText = doc.getString("scriptText")
                            ?: doc.getString("fullScript")
                            ?: doc.getString("script")

                        if (!scriptText.isNullOrBlank()) {
                            val cleanDocStyle = docStyle.lowercase()
                            val cleanDocLang = docLang.lowercase()
                            val cleanDocDur = docDur.lowercase()

                            if (cleanDocLang == cleanLanguage && cleanDocStyle == cleanStyle && cleanDocDur == cleanDuration) {
                                semanticMatch = CloudScript(scriptText, docStyle, docLang, docDur)
                                Log.d(TAG, "Cloud 'scripts' collection SEMANTIC HIT for topic '$cleanTopic' (matched '$dbTopic', score=$score, isUserEdited=${doc.getBoolean("isUserEdited")})")
                                break
                            }
                        }
                    }
                }
                broadDeferred.complete(semanticMatch)
            }
            .addOnFailureListener {
                broadDeferred.complete(null)
            }

        val broadResult = try {
            broadDeferred.await()
        } catch (e: Exception) {
            null
        }

        if (broadResult != null) {
            return@withContext broadResult
        }

        // 2. Fallback check in 'script_cache' collection using docId hash
        val hashKey = "${cleanTopic}_${cleanStyle}_${cleanLanguage}_${cleanDuration}"
        val docId = java.util.UUID.nameUUIDFromBytes(hashKey.toByteArray()).toString()

        val deferred = kotlinx.coroutines.CompletableDeferred<CloudScript?>()
        firestore.collection("script_cache")
            .document(docId)
            .get()
            .addOnSuccessListener { documentSnapshot ->
                if (documentSnapshot.exists()) {
                    val script = documentSnapshot.getString("scriptText")
                        ?: documentSnapshot.getString("fullScript")
                        ?: documentSnapshot.getString("script")
                    val docStyle = documentSnapshot.getString("style") ?: documentSnapshot.getString("tone") ?: style
                    val docLang = documentSnapshot.getString("language") ?: language
                    val docDur = documentSnapshot.getString("durationOption") ?: documentSnapshot.getString("duration") ?: durationOption

                    if (!script.isNullOrBlank()) {
                        val cleanDocStyle = docStyle.trim().lowercase()
                        val cleanDocLang = docLang.trim().lowercase()
                        val cleanDocDur = docDur.trim().lowercase()
                        if (cleanDocLang == cleanLanguage && cleanDocStyle == cleanStyle && cleanDocDur == cleanDuration) {
                            Log.d(TAG, "Cloud 'script_cache' collection HIT for '$cleanTopic'")
                            deferred.complete(CloudScript(script, docStyle, docLang, docDur))
                        } else {
                            deferred.complete(null)
                        }
                    } else {
                        deferred.complete(null)
                    }
                } else {
                    deferred.complete(null)
                }
            }
            .addOnFailureListener { ex ->
                Log.w(TAG, "Cloud 'script_cache' lookup failed for '$cleanTopic'", ex)
                deferred.complete(null)
            }

        try {
            deferred.await()
        } catch (e: Exception) {
            null
        }
    }

    fun saveCachedScript(
        topicDescription: String,
        style: String,
        language: String,
        durationOption: String,
        scriptText: String
    ) {
        val cleanTopic = topicDescription.trim().lowercase()
        if (cleanTopic.isEmpty() || scriptText.isEmpty()) return

        val cleanStyle = style.trim().lowercase()
        val cleanLanguage = language.trim().lowercase()
        val cleanDuration = durationOption.trim().lowercase()

        val hashKey = "${cleanTopic}_${cleanStyle}_${cleanLanguage}_${cleanDuration}"
        val docId = java.util.UUID.nameUUIDFromBytes(hashKey.toByteArray()).toString()

        val now = java.util.Date()
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
        val isoSdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US).apply {
            timeZone = java.util.TimeZone.getTimeZone("UTC")
        }
        val dateFormatted = sdf.format(now)
        val isoFormatted = isoSdf.format(now)

        val data = hashMapOf(
            "topic" to cleanTopic,
            "keywords" to extractCoreKeywords(cleanTopic),
            "style" to style,
            "tone" to style,
            "language" to language,
            "durationOption" to durationOption,
            "duration" to durationOption,
            "scriptText" to scriptText,
            "fullScript" to scriptText,
            "isUserEdited" to true,
            "isApproved" to true,
            "timestamp" to now.time,
            "createdAt" to dateFormatted,
            "formattedDate" to dateFormatted,
            "isoDate" to isoFormatted
        )

        fun doSaveCachedScript(db: FirebaseFirestore) {
            // 1. Save to 'scripts' collection with deterministic docId to prevent duplicates
            db.collection("scripts")
                .document(docId)
                .set(data)
                .addOnSuccessListener {
                    Log.d(TAG, "Successfully saved to 'scripts' collection for '$cleanTopic'")
                }
                .addOnFailureListener { ex ->
                    if (!preferDefaultDb) {
                        markUseDefaultDb()
                        doSaveCachedScript(firestore)
                    } else {
                        Log.w(TAG, "Failed to save to 'scripts' collection for '$cleanTopic'", ex)
                    }
                }

            // 2. Save to 'script_cache' collection with deterministic docId for backwards compatibility
            db.collection("script_cache")
                .document(docId)
                .set(data)
                .addOnSuccessListener {
                    Log.d(TAG, "Successfully saved to 'script_cache' collection for '$cleanTopic'")
                }
                .addOnFailureListener { ex ->
                    Log.w(TAG, "Failed to save to 'script_cache' collection for '$cleanTopic'", ex)
                }
        }
        doSaveCachedScript(firestore)
    }
}

data class CloudScript(
    val scriptText: String,
    val style: String,
    val language: String,
    val duration: String
)
