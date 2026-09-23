package com.ritvyom.yashoraReelgenerator.data.local

import android.content.Context
import android.util.Log

class LocalStorageScriptCache(context: Context) {
    private val sharedPrefs = context.getSharedPreferences("localStorage_script_cache", Context.MODE_PRIVATE)

    /**
     * Attempts to retrieve a cached script based on the specific topic, tone, language, and duration.
     */
    fun getCachedScript(topic: String, tone: String, language: String, duration: String): String? {
        val cacheKey = buildCacheKey(topic, tone, language, duration)
        val cachedValue = sharedPrefs.getString(cacheKey, null)
        if (cachedValue != null) {
            Log.i("LocalStorageScriptCache", "localStorage cache HIT for key '$cacheKey'")
            return cachedValue
        }
        
        Log.d("LocalStorageScriptCache", "localStorage cache MISS for key '$cacheKey'")
        return null
    }

    /**
     * Saves a successfully generated script into local storage cache.
     */
    fun saveCachedScript(topic: String, tone: String, language: String, duration: String, scriptText: String) {
        val cacheKey = buildCacheKey(topic, tone, language, duration)
        
        sharedPrefs.edit()
            .putString(cacheKey, scriptText)
            .apply()
            
        Log.i("LocalStorageScriptCache", "localStorage cache SAVED for key '$cacheKey'")
    }

    private fun buildCacheKey(topic: String, tone: String, language: String, duration: String): String {
        val normalizedTopic = topic.trim().lowercase()
        val normalizedTone = tone.trim().lowercase()
        val normalizedLanguage = language.trim().lowercase()
        val normalizedDuration = duration.trim().lowercase()
        return "script_${normalizedTopic}_${normalizedTone}_${normalizedLanguage}_${normalizedDuration}"
    }

    private fun buildTopicOnlyKey(topic: String): String {
        return "script_topic_${topic.trim().lowercase()}"
    }
}
