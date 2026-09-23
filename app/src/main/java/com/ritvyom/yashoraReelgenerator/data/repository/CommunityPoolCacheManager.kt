package com.ritvyom.yashoraReelgenerator.data.repository

import android.content.Context
import android.util.Log
import com.ritvyom.yashoraReelgenerator.data.remote.FirebaseCloudManager
import com.ritvyom.yashoraReelgenerator.data.remote.SharedProject
import com.ritvyom.yashoraReelgenerator.data.remote.SharedScene
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Local-First Cache Manager for Community Pool Feed.
 * Persists shared community reels to local device storage (JSON cache file)
 * so that navigating to the Community Pool tab does NOT trigger repeated Firestore reads.
 * Only reloads and fetches from Firestore when the user explicitly performs a Pull-to-Refresh.
 */
object CommunityPoolCacheManager {
    private const val TAG = "CommunityPoolCache"
    private const val CACHE_FILE_NAME = "yashora_community_feed_cache.json"
    private const val PREFS_NAME = "yashora_community_cache_prefs"
    private const val KEY_LAST_REFRESH_TIME = "key_last_refresh_time"

    private val _cachedProjects = MutableStateFlow<List<SharedProject>>(emptyList())
    val cachedProjects: StateFlow<List<SharedProject>> = _cachedProjects.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _lastRefreshTimestamp = MutableStateFlow(0L)
    val lastRefreshTimestamp: StateFlow<Long> = _lastRefreshTimestamp.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private var isInitialized = false

    /**
     * Initializes cache from local device storage if not already loaded.
     */
    suspend fun initialize(context: Context) {
        if (isInitialized && _cachedProjects.value.isNotEmpty()) return
        withContext(Dispatchers.IO) {
            try {
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                _lastRefreshTimestamp.value = prefs.getLong(KEY_LAST_REFRESH_TIME, 0L)

                val file = File(context.filesDir, CACHE_FILE_NAME)
                if (file.exists() && file.length() > 0) {
                    val jsonStr = file.readText()
                    val parsedList = deserializeProjects(jsonStr)
                    if (parsedList.isNotEmpty()) {
                        _cachedProjects.value = parsedList
                        Log.d(TAG, "Loaded ${parsedList.size} community projects from local device cache.")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed reading local community cache", e)
            } finally {
                isInitialized = true
            }

            // If local cache is completely empty (first install), load from Firestore once
            if (_cachedProjects.value.isEmpty()) {
                refreshFromFirestore(context, isUserTriggered = false)
            }
        }
    }

    /**
     * Explicit Pull-to-Refresh: Fetches latest shared reels from Firestore and saves to local device storage.
     */
    fun refreshFromFirestore(
        context: Context,
        isUserTriggered: Boolean = true,
        onComplete: (Boolean, String?) -> Unit = { _, _ -> }
    ) {
        _isRefreshing.value = true
        _errorMessage.value = null

        FirebaseCloudManager.fetchSharedProjects { list, error ->
            _isRefreshing.value = false
            if (error != null && list.isEmpty()) {
                _errorMessage.value = error
                Log.w(TAG, "Community refresh failed: $error")
                onComplete(false, error)
            } else {
                _errorMessage.value = null
                _cachedProjects.value = list
                val now = System.currentTimeMillis()
                _lastRefreshTimestamp.value = now

                // Save to local device file asynchronously
                saveToLocalDeviceCache(context, list, now)
                Log.i(TAG, "Successfully refreshed community feed with ${list.size} projects. Saved to local storage.")
                onComplete(true, null)
            }
        }
    }

    /**
     * Searches within cached community projects locally for instant search response without network calls.
     */
    fun filterCachedProjects(query: String): List<SharedProject> {
        if (query.isBlank()) return _cachedProjects.value
        val lower = query.lowercase().trim()
        return _cachedProjects.value.filter { project ->
            project.title.lowercase().contains(lower) ||
            project.topic.lowercase().contains(lower) ||
            project.style.lowercase().contains(lower) ||
            project.publishingStyle.lowercase().contains(lower) ||
            project.scriptText.lowercase().contains(lower)
        }
    }

    /**
     * Updates like count and user like state in the local cache immediately.
     */
    fun updateProjectLikeLocally(context: Context, projectId: String, newLikeCount: Int, isLiked: Boolean, userId: String) {
        val currentList = _cachedProjects.value.toMutableList()
        val index = currentList.indexOfFirst { it.id == projectId }
        if (index != -1) {
            val old = currentList[index]
            val updatedLikedUsers = if (isLiked) {
                if (!old.likedUsers.contains(userId)) old.likedUsers + userId else old.likedUsers
            } else {
                old.likedUsers - userId
            }
            val updatedProject = old.copy(
                likesCount = newLikeCount,
                likedUsers = updatedLikedUsers
            )
            currentList[index] = updatedProject
            _cachedProjects.value = currentList
            saveToLocalDeviceCache(context, currentList, _lastRefreshTimestamp.value)
        }
    }

    private fun saveToLocalDeviceCache(context: Context, list: List<SharedProject>, timestamp: Long) {
        try {
            val jsonStr = serializeProjects(list)
            val file = File(context.filesDir, CACHE_FILE_NAME)
            file.writeText(jsonStr)

            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putLong(KEY_LAST_REFRESH_TIME, timestamp).apply()
        } catch (e: Exception) {
            Log.e(TAG, "Failed writing community cache to local storage", e)
        }
    }

    private fun serializeProjects(list: List<SharedProject>): String {
        val jsonArray = JSONArray()
        for (p in list) {
            val obj = JSONObject().apply {
                put("id", p.id)
                put("userId", p.userId)
                put("userEmail", p.userEmail)
                put("title", p.title)
                put("topic", p.topic)
                put("scriptText", p.scriptText)
                put("style", p.style)
                put("aspectRatio", p.aspectRatio)
                put("visualMedium", p.visualMedium)
                put("publishingStyle", p.publishingStyle)
                put("timestamp", p.timestamp)
                put("likesCount", p.likesCount)
                put("isUserEdited", p.isUserEdited)
                put("exportedVideoUrl", p.exportedVideoUrl)

                val likedUsersArray = JSONArray()
                p.likedUsers.forEach { likedUsersArray.put(it) }
                put("likedUsers", likedUsersArray)

                val scenesArray = JSONArray()
                for (s in p.scenes) {
                    val sObj = JSONObject().apply {
                        put("sceneNumber", s.sceneNumber)
                        put("narrationText", s.narrationText)
                        put("visualPrompt", s.visualPrompt)
                        put("durationSeconds", s.durationSeconds)
                        put("subtitle", s.subtitle)
                        put("mediaPath", s.mediaPath)
                        val kwArray = JSONArray()
                        s.keywords.forEach { kwArray.put(it) }
                        put("keywords", kwArray)
                    }
                    scenesArray.put(sObj)
                }
                put("scenes", scenesArray)
            }
            jsonArray.put(obj)
        }
        return jsonArray.toString()
    }

    private fun deserializeProjects(jsonStr: String): List<SharedProject> {
        val result = mutableListOf<SharedProject>()
        val jsonArray = JSONArray(jsonStr)
        for (i in 0 until jsonArray.length()) {
            val obj = jsonArray.getJSONObject(i)
            val scenesList = mutableListOf<SharedScene>()
            val scenesArray = obj.optJSONArray("scenes")
            if (scenesArray != null) {
                for (j in 0 until scenesArray.length()) {
                    val sObj = scenesArray.getJSONObject(j)
                    val kwList = mutableListOf<String>()
                    val kwArray = sObj.optJSONArray("keywords")
                    if (kwArray != null) {
                        for (k in 0 until kwArray.length()) {
                            kwList.add(kwArray.getString(k))
                        }
                    }
                    scenesList.add(
                        SharedScene(
                            sceneNumber = sObj.optInt("sceneNumber", j + 1),
                            narrationText = sObj.optString("narrationText", ""),
                            visualPrompt = sObj.optString("visualPrompt", ""),
                            durationSeconds = sObj.optInt("durationSeconds", 5),
                            subtitle = sObj.optString("subtitle", ""),
                            mediaPath = sObj.optString("mediaPath", ""),
                            keywords = kwList
                        )
                    )
                }
            }

            val likedUsersList = mutableListOf<String>()
            val likedUsersArray = obj.optJSONArray("likedUsers")
            if (likedUsersArray != null) {
                for (u in 0 until likedUsersArray.length()) {
                    likedUsersList.add(likedUsersArray.getString(u))
                }
            }

            result.add(
                SharedProject(
                    id = obj.optString("id", ""),
                    userId = obj.optString("userId", ""),
                    userEmail = obj.optString("userEmail", ""),
                    title = obj.optString("title", ""),
                    topic = obj.optString("topic", ""),
                    scriptText = obj.optString("scriptText", ""),
                    style = obj.optString("style", ""),
                    aspectRatio = obj.optString("aspectRatio", "9:16"),
                    visualMedium = obj.optString("visualMedium", "Video"),
                    publishingStyle = obj.optString("publishingStyle", "TikTok / Instagram Reels"),
                    scenes = scenesList,
                    timestamp = obj.optLong("timestamp", 0L),
                    likesCount = obj.optInt("likesCount", 0),
                    likedUsers = likedUsersList,
                    isUserEdited = obj.optBoolean("isUserEdited", false),
                    exportedVideoUrl = obj.optString("exportedVideoUrl", "")
                )
            )
        }
        return result
    }
}
