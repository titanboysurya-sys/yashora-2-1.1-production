package com.ritvyom.yashoraReelgenerator.engine

import android.app.ActivityManager
import android.content.Context
import android.media.MediaCodecList
import android.media.MediaFormat
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.opengl.GLES20
import android.util.Log

/**
 * Probes the device's hardware media codecs, OpenGL ES capabilities, and memory limits.
 * Enables automatic fallback selection (e.g. HEVC -> AVC, 4K -> 1080p -> 720p).
 */
object DeviceCapabilityDetector {

    private const val TAG = "DeviceCapabilities"

    data class DeviceCapabilities(
        val supportsHardwareAvcEncoder: Boolean,
        val supportsHardwareHevcEncoder: Boolean,
        val maxAvcWidth: Int,
        val maxAvcHeight: Int,
        val supports4KExport: Boolean,
        val supports1080pExport: Boolean,
        val maxMemoryMb: Int,
        val isLowRamDevice: Boolean,
        val openGlVersionMajor: Int,
        val openGlRenderer: String,
        val openGlVendor: String
    )

    private var cachedCapabilities: DeviceCapabilities? = null

    fun getCapabilities(context: Context): DeviceCapabilities {
        cachedCapabilities?.let { return it }

        var hasAvcEnc = false
        var hasHevcEnc = false
        var maxAvcW = 1920
        var maxAvcH = 1080

        try {
            val codecList = MediaCodecList(MediaCodecList.ALL_CODECS)
            for (info in codecList.codecInfos) {
                if (!info.isEncoder) continue
                for (type in info.supportedTypes) {
                    if (type.equals(MediaFormat.MIMETYPE_VIDEO_AVC, ignoreCase = true)) {
                        hasAvcEnc = true
                        try {
                            val caps = info.getCapabilitiesForType(type)?.videoCapabilities
                            val maxWidth = caps?.supportedWidths?.upper ?: 1920
                            val maxHeight = caps?.supportedHeights?.upper ?: 1080
                            if (maxWidth > maxAvcW) maxAvcW = maxWidth
                            if (maxHeight > maxAvcH) maxAvcH = maxHeight
                        } catch (_: Exception) {}
                    }
                    if (type.equals(MediaFormat.MIMETYPE_VIDEO_HEVC, ignoreCase = true)) {
                        hasHevcEnc = true
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error probing MediaCodecList", e)
            hasAvcEnc = true
        }

        // Memory probe
        val actManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val maxMemMb = actManager?.memoryClass ?: 256
        val isLowRam = actManager?.isLowRamDevice ?: false

        // OpenGL probe
        val (glMajor, glRenderer, glVendor) = probeOpenGlContext()

        val supports4K = (maxAvcW >= 3840 && maxAvcH >= 2160) || (maxAvcW >= 2160 && maxAvcH >= 3840)
        val supports1080p = (maxAvcW >= 1920 && maxAvcH >= 1080) || (maxAvcW >= 1080 && maxAvcH >= 1920)

        val result = DeviceCapabilities(
            supportsHardwareAvcEncoder = hasAvcEnc,
            supportsHardwareHevcEncoder = hasHevcEnc,
            maxAvcWidth = maxAvcW,
            maxAvcHeight = maxAvcH,
            supports4KExport = supports4K && !isLowRam,
            supports1080pExport = supports1080p,
            maxMemoryMb = maxMemMb,
            isLowRamDevice = isLowRam,
            openGlVersionMajor = glMajor,
            openGlRenderer = glRenderer,
            openGlVendor = glVendor
        )
        cachedCapabilities = result
        Log.i(TAG, "Probed capabilities: AVC Enc=$hasAvcEnc, HEVC Enc=$hasHevcEnc, Max Dim=${maxAvcW}x$maxAvcH, 4K=$supports4K, Mem=${maxMemMb}MB, GL=$glMajor")
        return result
    }

    private fun probeOpenGlContext(): Triple<Int, String, String> {
        try {
            val dpy: EGLDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
            val vers = IntArray(2)
            EGL14.eglInitialize(dpy, vers, 0, vers, 1)

            val configAttribs = intArrayOf(
                EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT,
                EGL14.EGL_NONE
            )
            val configs = arrayOfNulls<EGLConfig>(1)
            val numConfigs = IntArray(1)
            EGL14.eglChooseConfig(dpy, configAttribs, 0, configs, 0, 1, numConfigs, 0)

            val pbufferAttribs = intArrayOf(
                EGL14.EGL_WIDTH, 1,
                EGL14.EGL_HEIGHT, 1,
                EGL14.EGL_NONE
            )
            val surf: EGLSurface = EGL14.eglCreatePbufferSurface(dpy, configs[0], pbufferAttribs, 0)
            val contextAttribs = intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE)
            val ctx: EGLContext = EGL14.eglCreateContext(dpy, configs[0], EGL14.EGL_NO_CONTEXT, contextAttribs, 0)

            EGL14.eglMakeCurrent(dpy, surf, surf, ctx)
            val renderer = GLES20.glGetString(GLES20.GL_RENDERER) ?: "Generic GPU"
            val vendor = GLES20.glGetString(GLES20.GL_VENDOR) ?: "Android OpenGLES"

            EGL14.eglMakeCurrent(dpy, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
            EGL14.eglDestroySurface(dpy, surf)
            EGL14.eglDestroyContext(dpy, ctx)
            EGL14.eglTerminate(dpy)

            return Triple(vers[0], renderer, vendor)
        } catch (e: Exception) {
            Log.w(TAG, "Could not probe EGL context directly", e)
            return Triple(2, "Default GPU", "Android")
        }
    }
}
