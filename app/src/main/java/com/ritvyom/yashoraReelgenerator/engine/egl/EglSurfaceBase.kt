package com.ritvyom.yashoraReelgenerator.engine.egl

import android.opengl.EGL14
import android.opengl.EGLSurface

/**
 * Base class for EGL surfaces (window or offscreen).
 */
abstract class EglSurfaceBase(protected val eglCore: EglCore) {

    protected var eglSurface: EGLSurface = EGL14.EGL_NO_SURFACE
    var width: Int = -1
        protected set
    var height: Int = -1
        protected set

    fun makeCurrent() {
        eglCore.makeCurrent(eglSurface)
    }

    fun swapBuffers(): Boolean {
        return eglCore.swapBuffers(eglSurface)
    }

    fun setPresentationTime(nsecs: Long) {
        eglCore.setPresentationTime(eglSurface, nsecs)
    }

    fun releaseEglSurface() {
        eglCore.releaseSurface(eglSurface)
        eglSurface = EGL14.EGL_NO_SURFACE
        width = -1
        height = -1
    }
}
