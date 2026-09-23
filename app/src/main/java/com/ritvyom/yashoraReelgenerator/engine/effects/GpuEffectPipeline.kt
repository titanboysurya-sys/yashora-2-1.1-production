package com.ritvyom.yashoraReelgenerator.engine.effects

import android.content.Context
import android.opengl.GLES20
import android.util.Log
import com.ritvyom.yashoraReelgenerator.engine.egl.GlFramebuffer
import com.ritvyom.yashoraReelgenerator.engine.egl.GlFramebufferPool
import com.ritvyom.yashoraReelgenerator.engine.egl.GlShaderUtil
import java.io.File
import java.nio.FloatBuffer

/**
 * GPU Effect Pipeline executing modular shader passes:
 * Separable Gaussian Blur, 3D LUT texture lookup, and multi-pass creative effects.
 * Shared between OpenGlPreviewRenderer and OpenGlExportRenderer.
 */
class GpuEffectPipeline(
    private val context: Context,
    private val fboPool: GlFramebufferPool
) {
    companion object {
        private const val TAG = "GpuEffectPipeline"

        private const val VERTEX_SHADER = """
            attribute vec4 aPosition;
            attribute vec4 aTextureCoord;
            varying vec2 vTextureCoord;
            void main() {
                gl_Position = aPosition;
                vTextureCoord = aTextureCoord.xy;
            }
        """

        // Separable 1D Horizontal Gaussian Blur (9-tap symmetric kernel)
        private const val BLUR_H_FRAGMENT_SHADER = """
            precision mediump float;
            varying vec2 vTextureCoord;
            uniform sampler2D uTexture;
            uniform float uTexelOffset;
            void main() {
                vec4 sum = vec4(0.0);
                sum += texture2D(uTexture, vec2(vTextureCoord.x - 4.0 * uTexelOffset, vTextureCoord.y)) * 0.05;
                sum += texture2D(uTexture, vec2(vTextureCoord.x - 3.0 * uTexelOffset, vTextureCoord.y)) * 0.09;
                sum += texture2D(uTexture, vec2(vTextureCoord.x - 2.0 * uTexelOffset, vTextureCoord.y)) * 0.12;
                sum += texture2D(uTexture, vec2(vTextureCoord.x - 1.0 * uTexelOffset, vTextureCoord.y)) * 0.15;
                sum += texture2D(uTexture, vTextureCoord) * 0.18;
                sum += texture2D(uTexture, vec2(vTextureCoord.x + 1.0 * uTexelOffset, vTextureCoord.y)) * 0.15;
                sum += texture2D(uTexture, vec2(vTextureCoord.x + 2.0 * uTexelOffset, vTextureCoord.y)) * 0.12;
                sum += texture2D(uTexture, vec2(vTextureCoord.x + 3.0 * uTexelOffset, vTextureCoord.y)) * 0.09;
                sum += texture2D(uTexture, vec2(vTextureCoord.x + 4.0 * uTexelOffset, vTextureCoord.y)) * 0.05;
                gl_FragColor = sum;
            }
        """

        // Separable 1D Vertical Gaussian Blur (9-tap symmetric kernel)
        private const val BLUR_V_FRAGMENT_SHADER = """
            precision mediump float;
            varying vec2 vTextureCoord;
            uniform sampler2D uTexture;
            uniform float uTexelOffset;
            void main() {
                vec4 sum = vec4(0.0);
                sum += texture2D(uTexture, vec2(vTextureCoord.x, vTextureCoord.y - 4.0 * uTexelOffset)) * 0.05;
                sum += texture2D(uTexture, vec2(vTextureCoord.x, vTextureCoord.y - 3.0 * uTexelOffset)) * 0.09;
                sum += texture2D(uTexture, vec2(vTextureCoord.x, vTextureCoord.y - 2.0 * uTexelOffset)) * 0.12;
                sum += texture2D(uTexture, vec2(vTextureCoord.x, vTextureCoord.y - 1.0 * uTexelOffset)) * 0.15;
                sum += texture2D(uTexture, vTextureCoord) * 0.18;
                sum += texture2D(uTexture, vec2(vTextureCoord.x, vTextureCoord.y + 1.0 * uTexelOffset)) * 0.15;
                sum += texture2D(uTexture, vec2(vTextureCoord.x, vTextureCoord.y + 2.0 * uTexelOffset)) * 0.12;
                sum += texture2D(uTexture, vec2(vTextureCoord.x, vTextureCoord.y + 3.0 * uTexelOffset)) * 0.09;
                sum += texture2D(uTexture, vec2(vTextureCoord.x, vTextureCoord.y + 4.0 * uTexelOffset)) * 0.05;
                gl_FragColor = sum;
            }
        """

        private val QUAD_COORDS = floatArrayOf(
            -1.0f, -1.0f, 0.0f,
             1.0f, -1.0f, 0.0f,
            -1.0f,  1.0f, 0.0f,
             1.0f,  1.0f, 0.0f
        )

        private val TEX_COORDS = floatArrayOf(
            0.0f, 0.0f,
            1.0f, 0.0f,
            0.0f, 1.0f,
            1.0f, 1.0f
        )
    }

    private val quadVertexBuffer: FloatBuffer = GlShaderUtil.createFloatBuffer(QUAD_COORDS)
    private val quadTexBuffer: FloatBuffer = GlShaderUtil.createFloatBuffer(TEX_COORDS)

    private var blurHProgramId = 0
    private var blurVProgramId = 0

    private var aPosHLoc = 0
    private var aTexHLoc = 0
    private var uTexHLoc = 0
    private var uOffsetHLoc = 0

    private var aPosVLoc = 0
    private var aTexVLoc = 0
    private var uTexVLoc = 0
    private var uOffsetVLoc = 0

    // LUT Texture Cache
    private val lutTextureCache = mutableMapOf<String, Int>()

    init {
        try {
            blurHProgramId = GlShaderUtil.createProgram(VERTEX_SHADER, BLUR_H_FRAGMENT_SHADER)
            aPosHLoc = GLES20.glGetAttribLocation(blurHProgramId, "aPosition")
            aTexHLoc = GLES20.glGetAttribLocation(blurHProgramId, "aTextureCoord")
            uTexHLoc = GLES20.glGetUniformLocation(blurHProgramId, "uTexture")
            uOffsetHLoc = GLES20.glGetUniformLocation(blurHProgramId, "uTexelOffset")

            blurVProgramId = GlShaderUtil.createProgram(VERTEX_SHADER, BLUR_V_FRAGMENT_SHADER)
            aPosVLoc = GLES20.glGetAttribLocation(blurVProgramId, "aPosition")
            aTexVLoc = GLES20.glGetAttribLocation(blurVProgramId, "aTextureCoord")
            uTexVLoc = GLES20.glGetUniformLocation(blurVProgramId, "uTexture")
            uOffsetVLoc = GLES20.glGetUniformLocation(blurVProgramId, "uTexelOffset")
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing GpuEffectPipeline shaders", e)
        }
    }

    /**
     * Applies two-pass separable Gaussian blur to [inputTextureId] and returns the resulting texture ID.
     * Uses a half-resolution intermediate buffer for high performance on mobile GPUs.
     */
    fun applySeparableBlur(
        inputTextureId: Int,
        width: Int,
        height: Int,
        radius: Float = 4.0f
    ): Int {
        if (radius <= 0.01f || blurHProgramId == 0 || blurVProgramId == 0) {
            return inputTextureId
        }

        // Downscale intermediate buffers 2x for optimal mobile fill-rate and wide blur radius
        val blurW = (width / 2).coerceAtLeast(64)
        val blurH = (height / 2).coerceAtLeast(64)

        val fboH = fboPool.acquire(blurW, blurH)
        val fboV = fboPool.acquire(blurW, blurH)

        try {
            // Pass 1: Horizontal Blur (input -> fboH)
            fboH.bind()
            GLES20.glUseProgram(blurHProgramId)

            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, inputTextureId)
            GLES20.glUniform1i(uTexHLoc, 0)
            GLES20.glUniform1f(uOffsetHLoc, (1.0f / blurW) * radius)

            GLES20.glEnableVertexAttribArray(aPosHLoc)
            GLES20.glVertexAttribPointer(aPosHLoc, 3, GLES20.GL_FLOAT, false, 12, quadVertexBuffer)
            GLES20.glEnableVertexAttribArray(aTexHLoc)
            GLES20.glVertexAttribPointer(aTexHLoc, 2, GLES20.GL_FLOAT, false, 8, quadTexBuffer)

            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

            GLES20.glDisableVertexAttribArray(aPosHLoc)
            GLES20.glDisableVertexAttribArray(aTexHLoc)

            // Pass 2: Vertical Blur (fboH.texture -> fboV)
            fboV.bind()
            GLES20.glUseProgram(blurVProgramId)

            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, fboH.textureId)
            GLES20.glUniform1i(uTexVLoc, 0)
            GLES20.glUniform1f(uOffsetVLoc, (1.0f / blurH) * radius)

            GLES20.glEnableVertexAttribArray(aPosVLoc)
            GLES20.glVertexAttribPointer(aPosVLoc, 3, GLES20.GL_FLOAT, false, 12, quadVertexBuffer)
            GLES20.glEnableVertexAttribArray(aTexVLoc)
            GLES20.glVertexAttribPointer(aTexVLoc, 2, GLES20.GL_FLOAT, false, 8, quadTexBuffer)

            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

            GLES20.glDisableVertexAttribArray(aPosVLoc)
            GLES20.glDisableVertexAttribArray(aTexVLoc)

            // Return texture from fboV, recycling fboH
            val resultTexId = fboV.textureId
            return resultTexId
        } finally {
            fboPool.recycle(fboH)
            fboPool.recycle(fboV)
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
        }
    }

    /**
     * Retrieves or uploads a 3D LUT texture from an asset or file path.
     */
    fun getOrLoadLutTexture(lutPath: String?): Int {
        if (lutPath.isNullOrBlank()) return 0
        lutTextureCache[lutPath]?.let { return it }

        try {
            val file = File(lutPath)
            val lutData = if (file.exists()) {
                LutParser.parseCubeFile(file)
            } else {
                // Try reading from app assets
                try {
                    context.assets.open(lutPath).use { LutParser.parseCube(it) }
                } catch (_: Exception) {
                    null
                }
            }

            if (lutData != null) {
                val texId = LutParser.uploadLutTexture(lutData)
                lutTextureCache[lutPath] = texId
                return texId
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load LUT texture: $lutPath", e)
        }

        return 0
    }

    fun release() {
        if (blurHProgramId != 0) {
            GLES20.glDeleteProgram(blurHProgramId)
            blurHProgramId = 0
        }
        if (blurVProgramId != 0) {
            GLES20.glDeleteProgram(blurVProgramId)
            blurVProgramId = 0
        }
        for ((_, texId) in lutTextureCache) {
            GLES20.glDeleteTextures(1, intArrayOf(texId), 0)
        }
        lutTextureCache.clear()
        Log.i(TAG, "GpuEffectPipeline released")
    }
}
