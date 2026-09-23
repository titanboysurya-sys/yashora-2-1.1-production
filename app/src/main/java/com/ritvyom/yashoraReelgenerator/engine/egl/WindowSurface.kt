package com.ritvyom.yashoraReelgenerator.engine.egl

import android.graphics.SurfaceTexture
import android.opengl.EGL14
import android.view.Surface

/**
 * Recordable or display EGL Surface backed by an on-screen [Surface] or [SurfaceTexture].
 */
class WindowSurface : EglSurfaceBase {

    private var surface: Surface? = null
    private var releaseSurface: Boolean = false

    constructor(eglCore: EglCore, surface: Surface, releaseSurface: Boolean = false) : super(eglCore) {
        this.surface = surface
        this.releaseSurface = releaseSurface
        eglSurface = eglCore.createWindowSurface(surface)
        queryDimensions()
    }

    constructor(eglCore: EglCore, surfaceTexture: SurfaceTexture) : super(eglCore) {
        eglSurface = eglCore.createWindowSurface(surfaceTexture)
        queryDimensions()
    }

    private fun queryDimensions() {
        val valArr = IntArray(1)
        EGL14.eglQuerySurface(eglCore.eglDisplay, eglSurface, EGL14.EGL_WIDTH, valArr, 0)
        width = valArr[0]
        EGL14.eglQuerySurface(eglCore.eglDisplay, eglSurface, EGL14.EGL_HEIGHT, valArr, 0)
        height = valArr[0]
    }

    fun release() {
        releaseEglSurface()
        if (releaseSurface) {
            surface?.release()
            surface = null
        }
    }
}
