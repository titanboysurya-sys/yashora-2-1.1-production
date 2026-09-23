package com.ritvyom.yashoraReelgenerator.engine.renderer

import android.content.Context
import android.util.Log
import android.view.Surface
import com.ritvyom.yashoraReelgenerator.core.model.CanonicalTimeline
import com.ritvyom.yashoraReelgenerator.core.rendering.FrameTime
import com.ritvyom.yashoraReelgenerator.core.rendering.IPreviewRenderer
import com.ritvyom.yashoraReelgenerator.core.rendering.RenderContext
import com.ritvyom.yashoraReelgenerator.engine.TimelineEvaluator
import com.ritvyom.yashoraReelgenerator.engine.audio.MultiTrackAudioPreviewPlayer
import com.ritvyom.yashoraReelgenerator.engine.egl.EglCore
import com.ritvyom.yashoraReelgenerator.engine.egl.WindowSurface
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

/**
 * High-performance OpenGL ES Preview Renderer.
 * Renders the deterministic Render Graph to a live display Surface at 30 FPS,
 * synchronized with real-time multi-track audio playback from CanonicalTimeline.
 */
class OpenGlPreviewRenderer(
    private val context: Context
) : IPreviewRenderer {

    companion object {
        private const val TAG = "OpenGlPreviewRenderer"
        private const val FRAME_INTERVAL_MS = 33L // ~30 FPS
    }

    private val _isPlaying = MutableStateFlow(false)
    override val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentPositionUs = MutableStateFlow(0L)
    override val currentPositionUs: StateFlow<Long> = _currentPositionUs.asStateFlow()

    private val _durationUs = MutableStateFlow(0L)
    override val durationUs: StateFlow<Long> = _durationUs.asStateFlow()

    private var currentTimeline: CanonicalTimeline? = null
    private var timelineEvaluator: TimelineEvaluator? = null
    private val audioPreviewPlayer = MultiTrackAudioPreviewPlayer(context)

    // Dedicated single thread for OpenGL ES operations
    private val glExecutor = Executors.newSingleThreadExecutor()
    private val glDispatcher = glExecutor.asCoroutineDispatcher()
    private val glScope = CoroutineScope(glDispatcher)

    private var eglCore: EglCore? = null
    private var displayWindowSurface: WindowSurface? = null
    private var renderEngine: UnifiedRenderEngine? = null

    private var surfaceWidth = 1080
    private var surfaceHeight = 1920
    private var playbackJob: Job? = null

    override fun attachSurface(surface: Surface, width: Int, height: Int) {
        surfaceWidth = width
        surfaceHeight = height

        glScope.launch {
            try {
                // Initialize EGL on GL thread
                val core = EglCore(flags = 0)
                eglCore = core
                val winSurface = WindowSurface(core, surface)
                displayWindowSurface = winSurface
                winSurface.makeCurrent()

                val engine = UnifiedRenderEngine(context)
                engine.initialize()
                renderEngine = engine

                // Draw initial frame
                renderCurrentFrame()
                Log.i(TAG, "Surface attached (${width}x${height})")
            } catch (e: Exception) {
                Log.e(TAG, "Error attaching surface", e)
            }
        }
    }

    override fun detachSurface() {
        glScope.launch {
            playbackJob?.cancel()
            playbackJob = null
            _isPlaying.value = false

            renderEngine?.release()
            renderEngine = null

            displayWindowSurface?.release()
            displayWindowSurface = null

            eglCore?.release()
            eglCore = null
            Log.i(TAG, "Surface detached")
        }
    }

    override fun setTimeline(timeline: CanonicalTimeline) {
        currentTimeline = timeline
        val evaluator = TimelineEvaluator(timeline)
        timelineEvaluator = evaluator
        _durationUs.value = timeline.totalDurationUs
        audioPreviewPlayer.setTimeline(timeline)

        glScope.launch {
            renderCurrentFrame()
        }
    }

    override fun play() {
        if (_isPlaying.value) return
        _isPlaying.value = true
        audioPreviewPlayer.seekTo(_currentPositionUs.value)
        audioPreviewPlayer.play()

        playbackJob?.cancel()
        playbackJob = glScope.launch {
            var lastTime = System.nanoTime()
            val totalDur = _durationUs.value

            while (isActive && _isPlaying.value) {
                val now = System.nanoTime()
                val deltaUs = (now - lastTime) / 1000L
                lastTime = now

                var nextPos = _currentPositionUs.value + deltaUs
                if (totalDur > 0 && nextPos >= totalDur) {
                    nextPos = 0L // Loop
                    audioPreviewPlayer.seekTo(0L)
                }
                _currentPositionUs.value = nextPos

                renderCurrentFrame()
                delay(FRAME_INTERVAL_MS)
            }
        }
    }

    override fun pause() {
        _isPlaying.value = false
        audioPreviewPlayer.pause()
        playbackJob?.cancel()
        playbackJob = null
    }

    override fun seekTo(timeUs: Long) {
        val totalDur = _durationUs.value
        val clamped = timeUs.coerceIn(0L, totalDur.coerceAtLeast(0L))
        _currentPositionUs.value = clamped
        audioPreviewPlayer.seekTo(clamped)

        glScope.launch {
            renderCurrentFrame()
        }
    }

    private fun renderCurrentFrame() {
        val evaluator = timelineEvaluator ?: return
        val engine = renderEngine ?: return
        val winSurface = displayWindowSurface ?: return
        val timeline = currentTimeline ?: return

        try {
            winSurface.makeCurrent()

            val timeUs = _currentPositionUs.value
            val frameIndex = (timeUs / (1_000_000L / 30)).toInt()
            val frameTime = FrameTime(
                presentationTimeUs = timeUs,
                frameIndex = frameIndex,
                fps = 30,
                totalDurationUs = timeline.totalDurationUs
            )

            val renderContext = RenderContext(
                canvasConfig = timeline.canvasConfig,
                viewportWidth = surfaceWidth,
                viewportHeight = surfaceHeight,
                isOfflineExport = false
            )

            val nodes = evaluator.evaluateNodesForTime(frameTime, renderContext)
            engine.renderFrame(nodes, renderContext)
            winSurface.swapBuffers()
        } catch (e: Exception) {
            Log.e(TAG, "Error rendering preview frame", e)
        }
    }

    override fun release() {
        audioPreviewPlayer.release()
        detachSurface()
        glExecutor.shutdown()
    }
}
