package com.ritvyom.yashoraReelgenerator.presentation.utils

import android.content.Context
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

object VoiceRecorderManager {
    private const val TAG = "VoiceRecorderManager"
    private var mediaRecorder: MediaRecorder? = null
    private var currentOutputFile: File? = null
    private var mediaPlayer: MediaPlayer? = null

    private val _isRecording = MutableStateFlow(false)
    val isRecording = _isRecording.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying = _isPlaying.asStateFlow()

    private val _recordingDurationSeconds = MutableStateFlow(0)
    val recordingDurationSeconds = _recordingDurationSeconds.asStateFlow()

    private val _recordedAudioFile = MutableStateFlow<File?>(null)
    val recordedAudioFile = _recordedAudioFile.asStateFlow()

    private val _audioAmplitude = MutableStateFlow(0f)
    val audioAmplitude = _audioAmplitude.asStateFlow()

    private var recordingJob: Job? = null

    @Suppress("DEPRECATION")
    fun startRecording(context: Context): Boolean {
        try {
            stopPlayback()
            stopRecording(discard = true)

            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val audioDir = File(context.cacheDir, "recordings").apply { if (!exists()) mkdirs() }
            val outputFile = File(audioDir, "REC_$timeStamp.m4a")
            currentOutputFile = outputFile

            val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                MediaRecorder()
            }

            recorder.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(128000)
                setAudioSamplingRate(44100)
                setOutputFile(outputFile.absolutePath)
                prepare()
                start()
            }

            mediaRecorder = recorder
            _isRecording.value = true
            _recordingDurationSeconds.value = 0
            _recordedAudioFile.value = null

            // Start timer & amplitude poll loop
            recordingJob?.cancel()
            recordingJob = CoroutineScope(Dispatchers.IO).launch {
                var seconds = 0
                while (isActive && _isRecording.value) {
                    delay(100)
                    try {
                        val amp = mediaRecorder?.maxAmplitude ?: 0
                        _audioAmplitude.value = (amp / 32767f).coerceIn(0f, 1f)
                    } catch (e: Exception) {
                        _audioAmplitude.value = 0f
                    }
                    if (System.currentTimeMillis() % 1000 < 150) {
                        seconds++
                        _recordingDurationSeconds.value = seconds
                    }
                }
            }

            Log.d(TAG, "Recording started -> ${outputFile.absolutePath}")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error starting voice recording", e)
            cancelRecording()
            return false
        }
    }

    fun stopRecording(discard: Boolean = false): File? {
        recordingJob?.cancel()
        recordingJob = null
        _isRecording.value = false
        _audioAmplitude.value = 0f

        try {
            mediaRecorder?.apply {
                try {
                    stop()
                } catch (e: Exception) {
                    Log.w(TAG, "MediaRecorder stop failed", e)
                }
                release()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing MediaRecorder", e)
        } finally {
            mediaRecorder = null
        }

        val file = currentOutputFile
        if (discard) {
            file?.delete()
            currentOutputFile = null
            _recordedAudioFile.value = null
            _recordingDurationSeconds.value = 0
            return null
        } else {
            if (file != null && file.exists() && file.length() > 500) {
                _recordedAudioFile.value = file
                Log.d(TAG, "Recording saved successfully: ${file.absolutePath} (${file.length()} bytes)")
                return file
            } else {
                file?.delete()
                _recordedAudioFile.value = null
                return null
            }
        }
    }

    fun cancelRecording() {
        stopRecording(discard = true)
    }

    fun playRecordedAudio(context: Context, file: File? = _recordedAudioFile.value, onComplete: (() -> Unit)? = null) {
        val targetFile = file ?: return
        if (!targetFile.exists()) return

        stopPlayback()

        try {
            val player = MediaPlayer().apply {
                setDataSource(context, Uri.fromFile(targetFile))
                setOnPreparedListener {
                    _isPlaying.value = true
                    it.start()
                }
                setOnCompletionListener {
                    _isPlaying.value = false
                    it.release()
                    mediaPlayer = null
                    onComplete?.invoke()
                }
                setOnErrorListener { _, _, _ ->
                    _isPlaying.value = false
                    release()
                    mediaPlayer = null
                    true
                }
                prepareAsync()
            }
            mediaPlayer = player
        } catch (e: Exception) {
            Log.e(TAG, "Error playing recorded audio", e)
            _isPlaying.value = false
        }
    }

    fun stopPlayback() {
        try {
            mediaPlayer?.apply {
                if (isPlaying) {
                    stop()
                }
                release()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping audio player", e)
        } finally {
            mediaPlayer = null
            _isPlaying.value = false
        }
    }

    fun copyUriToCache(context: Context, uri: Uri, prefix: String = "imported_audio"): File? {
        return try {
            val extension = when (context.contentResolver.getType(uri)) {
                "audio/mpeg", "audio/mp3" -> "mp3"
                "audio/wav", "audio/x-wav" -> "wav"
                "audio/aac" -> "aac"
                "audio/mp4", "audio/m4a" -> "m4a"
                else -> "m4a"
            }
            val audioDir = File(context.cacheDir, "recordings").apply { if (!exists()) mkdirs() }
            val destFile = File(audioDir, "${prefix}_${System.currentTimeMillis()}.$extension")
            context.contentResolver.openInputStream(uri)?.use { input ->
                destFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            if (destFile.exists() && destFile.length() > 0) destFile else null
        } catch (e: Exception) {
            Log.e(TAG, "Error copying uri audio to cache", e)
            null
        }
    }

    fun clear() {
        stopPlayback()
        stopRecording(discard = true)
    }
}
