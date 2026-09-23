package com.ritvyom.yashoraReelgenerator.presentation.utils

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap

object AudioManager {
    private const val TAG = "AudioManager"
    private val activePlayers = ConcurrentHashMap<String, MediaPlayer>()
    
    private val _availableSfxList = MutableStateFlow<List<String>>(emptyList())
    val availableSfxList = _availableSfxList.asStateFlow()

    fun initialize(context: Context) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val assetList = context.assets.list("sfx") ?: emptyArray()
                val list = assetList.map { sfxFile ->
                    // Return human-readable names
                    sfxFile.substringBefore(".wav.b64")
                        .replace("_", " ")
                        .split(" ")
                        .joinToString(" ") { it.replaceFirstChar { char -> char.uppercase() } }
                }
                _availableSfxList.value = list
                Log.d(TAG, "Audio manager initialized with assets: $list")
            } catch (e: Exception) {
                Log.e(TAG, "Error listing sound effects inside assets/sfx/", e)
            }
        }
    }

    private suspend fun getCachedFile(context: Context, readableName: String): File? = withContext(Dispatchers.IO) {
        val sfxDir = File(context.cacheDir, "sfx").apply { if (!exists()) mkdirs() }
        val fileName = readableName.lowercase().replace(" ", "_")
        val cacheFile = File(sfxDir, "$fileName.wav")
        if (cacheFile.exists() && cacheFile.length() > 100) {
            return@withContext cacheFile
        }

        // Need to load from assets and decode content
        try {
            val assetName = "$fileName.wav.b64"
            val input = context.assets.open("sfx/$assetName")
            val b64String = input.bufferedReader().use { it.readText() }.trim()
            val decodedBytes = Base64.decode(b64String, Base64.DEFAULT)
            cacheFile.writeBytes(decodedBytes)
            Log.d(TAG, "Decoded and cached sfx to $cacheFile")
            return@withContext cacheFile
        } catch (e: Exception) {
            Log.e(TAG, "Failed decoding or caching asset sfx $readableName", e)
            return@withContext null
        }
    }

    fun playSfx(context: Context, sfxName: String, volume: Float = 0.8f, loop: Boolean = false, trackKey: String = sfxName) {
        if (sfxName == "None" || sfxName.isEmpty()) return

        CoroutineScope(Dispatchers.Main).launch {
            try {
                val cachedFile = getCachedFile(context, sfxName) ?: return@launch
                
                // Stop any existing sound with the same layer key
                stopSfx(trackKey)

                val mp = MediaPlayer().apply {
                    setDataSource(context, Uri.fromFile(cachedFile))
                    isLooping = loop
                    setVolume(volume, volume)
                    setOnPreparedListener { 
                        it.start() 
                    }
                    setOnCompletionListener {
                        if (!loop) {
                            it.release()
                            activePlayers.remove(trackKey)
                        }
                    }
                    setOnErrorListener { _, _, _ ->
                        release()
                        activePlayers.remove(trackKey)
                        true
                    }
                    prepareAsync()
                }
                activePlayers[trackKey] = mp
                Log.d(TAG, "Starting layered sfx playback of $sfxName under key $trackKey (volume: $volume, loop: $loop)")
            } catch (e: Exception) {
                Log.e(TAG, "Error preparing MediaPlayer for $sfxName", e)
            }
        }
    }

    fun stopSfx(trackKey: String) {
        activePlayers[trackKey]?.let { mp ->
            try {
                if (mp.isPlaying) {
                    mp.stop()
                }
            } catch (e: Exception) {
                // Ignore isPlaying or state exceptions
            } finally {
                mp.release()
                activePlayers.remove(trackKey)
            }
        }
    }

    fun setVolume(trackKey: String, volume: Float) {
        activePlayers[trackKey]?.let { mp ->
            try {
                mp.setVolume(volume, volume)
            } catch (e: Exception) {
                Log.e(TAG, "Error setting volume on $trackKey", e)
            }
        }
    }

    fun stopAll() {
        activePlayers.keys.forEach { key ->
            stopSfx(key)
        }
    }
}
