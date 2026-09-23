package com.ritvyom.yashoraReelgenerator.data.repository

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.ritvyom.yashoraReelgenerator.domain.models.Scene

enum class SceneFetchStatus {
    IDLE,
    FETCHING,
    SUCCESS,
    FAILED
}

data class SceneMediaStatus(
    val sceneNumber: Int,
    val visualPrompt: String,
    val status: SceneFetchStatus = SceneFetchStatus.IDLE,
    val sourceUsed: String? = null, // "Pexels", "Pixabay", "Unsplash", "AI Generated", "Wikipedia", "Local Fallback", etc.
    val mediaUrl: String? = null,
    val attemptedSources: List<String> = emptyList(),
    val errorMessage: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * 'Media Fetching Status' monitor that logs which source (Pexels, Pixabay, Unsplash, etc.)
 * was successfully used for each specific scene, allowing the UI to show an error state
 * or a manual re-fetch button for scenes that failed all attempts.
 */
object MediaFetchingStatusMonitor {
    private const val TAG = "MediaFetchingStatusMonitor"

    private val _statusMap = MutableStateFlow<Map<Int, SceneMediaStatus>>(emptyMap())
    val statusMap: StateFlow<Map<Int, SceneMediaStatus>> = _statusMap.asStateFlow()

    fun reset() {
        _statusMap.value = emptyMap()
    }

    fun recordInProgress(sceneNumber: Int, visualPrompt: String) {
        recordFetching(sceneNumber, visualPrompt)
    }

    fun recordFetching(sceneNumber: Int, visualPrompt: String) {
        val current = _statusMap.value.toMutableMap()
        current[sceneNumber] = SceneMediaStatus(
            sceneNumber = sceneNumber,
            visualPrompt = visualPrompt,
            status = SceneFetchStatus.FETCHING,
            sourceUsed = null,
            mediaUrl = null,
            attemptedSources = listOf("Pexels", "Pixabay", "Unsplash"),
            errorMessage = null,
            timestamp = System.currentTimeMillis()
        )
        _statusMap.value = current
        android.util.Log.d(TAG, "Scene #$sceneNumber: Fetching media...")
    }

    fun recordSuccess(
        sceneNumber: Int,
        visualPrompt: String,
        sourceUsed: String,
        mediaUrl: String? = null,
        attemptedSources: List<String> = emptyList()
    ) {
        val current = _statusMap.value.toMutableMap()
        current[sceneNumber] = SceneMediaStatus(
            sceneNumber = sceneNumber,
            visualPrompt = visualPrompt,
            status = SceneFetchStatus.SUCCESS,
            sourceUsed = normalizeSourceName(sourceUsed),
            mediaUrl = mediaUrl,
            attemptedSources = attemptedSources,
            errorMessage = null,
            timestamp = System.currentTimeMillis()
        )
        _statusMap.value = current
        android.util.Log.i(TAG, "Scene #$sceneNumber: Successfully fetched using source '$sourceUsed'")
    }

    fun recordFailure(
        sceneNumber: Int,
        visualPrompt: String,
        attemptedSources: List<String> = emptyList(),
        error: String = "All media sources (Pexels, Pixabay, Unsplash) returned no footage"
    ) {
        val current = _statusMap.value.toMutableMap()
        val sources = if (attemptedSources.isNotEmpty()) attemptedSources else listOf("Pexels", "Pixabay", "Unsplash")
        current[sceneNumber] = SceneMediaStatus(
            sceneNumber = sceneNumber,
            visualPrompt = visualPrompt,
            status = SceneFetchStatus.FAILED,
            sourceUsed = null,
            mediaUrl = null,
            attemptedSources = sources,
            errorMessage = error,
            timestamp = System.currentTimeMillis()
        )
        _statusMap.value = current
        android.util.Log.w(TAG, "Scene #$sceneNumber: All media fetching attempts failed! Sources tried: ${sources.joinToString(", ")}")
    }

    fun getStatusForScene(sceneNumber: Int): SceneMediaStatus? {
        return _statusMap.value[sceneNumber]
    }

    fun syncFromScenes(scenes: List<Scene>) {
        val map = mutableMapOf<Int, SceneMediaStatus>()
        scenes.forEach { scene ->
            val status = when (scene.mediaFetchStatus.uppercase()) {
                "FAILED" -> SceneFetchStatus.FAILED
                "FETCHING" -> SceneFetchStatus.FETCHING
                "SUCCESS" -> SceneFetchStatus.SUCCESS
                else -> if (!scene.mediaPath.isNullOrEmpty()) SceneFetchStatus.SUCCESS else SceneFetchStatus.IDLE
            }
            map[scene.sceneNumber] = SceneMediaStatus(
                sceneNumber = scene.sceneNumber,
                visualPrompt = scene.visualPrompt,
                status = status,
                sourceUsed = scene.mediaSourceUsed?.let { normalizeSourceName(it) } ?: if (!scene.mediaPath.isNullOrEmpty()) "Pexels" else null,
                mediaUrl = scene.mediaPath,
                attemptedSources = listOf("Pexels", "Pixabay", "Unsplash"),
                errorMessage = scene.mediaFetchError
            )
        }
        _statusMap.value = map
    }

    fun normalizeSourceName(raw: String): String {
        val lower = raw.trim().lowercase()
        return when {
            lower.contains("pexels") -> "Pexels"
            lower.contains("pixabay") -> "Pixabay"
            lower.contains("unsplash") -> "Unsplash"
            lower.contains("ai") || lower.contains("pollinations") -> "AI Generated"
            lower.contains("wiki") -> "Wikipedia"
            lower.contains("cache") -> "Cache"
            lower.contains("fallback") -> "Local Fallback"
            lower.contains("failed") -> "Failed"
            else -> raw.trim().replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        }
    }

    data class MonitorSummary(
        val total: Int,
        val success: Int,
        val failed: Int,
        val fetching: Int,
        val pexelsCount: Int,
        val pixabayCount: Int,
        val unsplashCount: Int,
        val otherCount: Int
    )

    fun getSummary(): MonitorSummary {
        val all = _statusMap.value.values
        val total = all.size
        val success = all.count { it.status == SceneFetchStatus.SUCCESS }
        val failed = all.count { it.status == SceneFetchStatus.FAILED }
        val fetching = all.count { it.status == SceneFetchStatus.FETCHING }
        
        var pexels = 0
        var pixabay = 0
        var unsplash = 0
        var other = 0

        for (item in all) {
            if (item.status == SceneFetchStatus.SUCCESS && item.sourceUsed != null) {
                when (item.sourceUsed) {
                    "Pexels" -> pexels++
                    "Pixabay" -> pixabay++
                    "Unsplash" -> unsplash++
                    else -> other++
                }
            }
        }

        return MonitorSummary(
            total = total,
            success = success,
            failed = failed,
            fetching = fetching,
            pexelsCount = pexels,
            pixabayCount = pixabay,
            unsplashCount = unsplash,
            otherCount = other
        )
    }
}
