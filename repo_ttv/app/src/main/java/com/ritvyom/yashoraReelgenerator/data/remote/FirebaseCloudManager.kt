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
    val likesCount: Int = 0
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

    fun getCurrentUser(): FirebaseUser? {
        return auth.currentUser
    }

    fun isUserLoggedIn(): Boolean {
        return auth.currentUser != null
    }

    fun signUp(email: String, password: String, onResult: (Boolean, String?) -> Unit) {
        if (email.isBlank() || password.isBlank()) {
            onResult(false, "Email and Password cannot be empty.")
            return
        }
        auth.createUserWithEmailAndPassword(email.trim(), password)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    _currentUserState.value = auth.currentUser
                    onResult(true, null)
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
        onResult: (Boolean, String?) -> Unit
    ) {
        val user = auth.currentUser
        if (user == null) {
            onResult(false, "You must be signed in to share with the community.")
            return
        }

        val sharedScenes = scenes.map { scene ->
            SharedScene(
                sceneNumber = scene.sceneNumber,
                narrationText = scene.narrationText,
                visualPrompt = scene.visualPrompt,
                durationSeconds = scene.durationSeconds,
                subtitle = scene.subtitle,
                mediaPath = scene.mediaPath ?: "", // Remote fetched URL
                keywords = scene.keywords
            )
        }

        val documentData = hashMapOf(
            "userId" to user.uid,
            "userEmail" to (user.email ?: "Anonymous"),
            "title" to title.trim(),
            "topic" to topic.trim(),
            "scriptText" to scriptText.trim(),
            "style" to style,
            "aspectRatio" to aspectRatio,
            "visualMedium" to visualMedium,
            "publishingStyle" to publishingStyle,
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
            "timestamp" to System.currentTimeMillis(),
            "likesCount" to 0
        )

        fun doShare(db: FirebaseFirestore) {
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
                                    likesCount = likesCount
                                )
                            )
                        } catch (e: Exception) {
                            Log.e(TAG, "Error parsing shared project document", e)
                        }
                    }
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
                                        likesCount = likesCount
                                    )
                                )
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error parsing matching document", e)
                        }
                    }
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
                    for (doc in querySnapshot.documents) {
                        val dbTopic = doc.getString("topic") ?: ""
                        val dbTitle = doc.getString("title") ?: ""
                        
                        // Match if they have highly overlapping words or match exactly
                        val isMatch = dbTopic.lowercase().trim() == cleanTopic || 
                                      dbTitle.lowercase().trim() == cleanTopic ||
                                      (cleanTopic.length > 4 && dbTopic.lowercase().contains(cleanTopic)) ||
                                      (dbTopic.length > 4 && cleanTopic.contains(dbTopic.lowercase()))

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
                                        likesCount = likesCount
                                    )
                                )
                                return@addOnSuccessListener
                            } catch (e: Exception) {
                                Log.e(TAG, "Error parsing matched project", e)
                            }
                        }
                    }
                    onResult(null)
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
     * Community interactions: Like a project to increase popularity
     */
    fun likeProject(projectId: String) {
        fun doLike(db: FirebaseFirestore) {
            val docRef = db.collection("shared_scripts").document(projectId)
            db.runTransaction { transaction ->
                val snapshot = transaction.get(docRef)
                val currentLikes = snapshot.getLong("likesCount") ?: 0L
                transaction.update(docRef, "likesCount", currentLikes + 1)
            }.addOnSuccessListener {
                Log.d(TAG, "Project liked successfully")
            }.addOnFailureListener { e ->
                if (!preferDefaultDb) {
                    markUseDefaultDb()
                    doLike(firestore)
                } else {
                    Log.e(TAG, "Error liking project", e)
                }
            }
        }
        doLike(firestore)
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

        val currentTime = System.currentTimeMillis()
        val data = hashMapOf(
            "query" to cleanQuery,
            "style" to style,
            "aspectRatio" to aspectRatio,
            "visualMedium" to visualMedium,
            "mediaUrl" to mediaUrl,
            "media_urls" to listOf(mediaUrl),
            "timestamp" to currentTime,
            "timestamps" to listOf(currentTime)
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
                    for (doc in querySnapshot.documents) {
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

        val data = hashMapOf(
            "topic" to cleanTopic,
            "style" to style,
            "tone" to style,
            "language" to language,
            "durationOption" to durationOption,
            "duration" to durationOption,
            "scriptText" to scriptText,
            "fullScript" to scriptText,
            "timestamp" to System.currentTimeMillis()
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
