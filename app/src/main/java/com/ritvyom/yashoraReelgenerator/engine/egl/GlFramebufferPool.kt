package com.ritvyom.yashoraReelgenerator.engine.egl

import android.util.Log

/**
 * High-performance Framebuffer Pool for multi-pass visual effects, separable blur, and offscreen compositing.
 * Reuses intermediate FBO textures across frames to eliminate per-frame allocations and GPU memory leaks.
 */
class GlFramebufferPool(
    private val maxPoolSize: Int = 6
) {
    companion object {
        private const val TAG = "GlFramebufferPool"
    }

    private val availableFbos = mutableListOf<GlFramebuffer>()
    private val inUseFbos = mutableSetOf<GlFramebuffer>()
    private var isReleased = false

    /**
     * Acquires a Framebuffer of the exact requested dimensions.
     * If an existing FBO of matching dimensions is available, it is reused.
     */
    @Synchronized
    fun acquire(width: Int, height: Int): GlFramebuffer {
        if (isReleased) {
            throw IllegalStateException("GlFramebufferPool is released")
        }

        val matchIndex = availableFbos.indexOfFirst { it.width == width && it.height == height }
        val fbo = if (matchIndex != -1) {
            availableFbos.removeAt(matchIndex)
        } else {
            // Check if we need to purge an old unused FBO to stay under cap
            if (availableFbos.size + inUseFbos.size >= maxPoolSize && availableFbos.isNotEmpty()) {
                val toRemove = availableFbos.removeAt(0)
                toRemove.release()
            }
            GlFramebuffer(width, height)
        }

        inUseFbos.add(fbo)
        return fbo
    }

    /**
     * Returns an acquired FBO back to the pool for reuse.
     */
    @Synchronized
    fun recycle(fbo: GlFramebuffer) {
        if (isReleased) {
            fbo.release()
            return
        }

        if (inUseFbos.remove(fbo)) {
            if (availableFbos.size < maxPoolSize) {
                availableFbos.add(fbo)
            } else {
                fbo.release()
            }
        } else {
            // Unknown or already recycled FBO
            fbo.release()
        }
    }

    /**
     * Cleans up all cached OpenGL Framebuffers and textures deterministically.
     */
    @Synchronized
    fun releaseAll() {
        if (isReleased) return
        isReleased = true

        for (fbo in availableFbos) {
            fbo.release()
        }
        availableFbos.clear()

        for (fbo in inUseFbos) {
            fbo.release()
        }
        inUseFbos.clear()
        Log.i(TAG, "GlFramebufferPool released all resources")
    }
}
