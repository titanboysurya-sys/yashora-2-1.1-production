package com.ritvyom.yashoraReelgenerator.engine.renderer

import android.opengl.GLES20
import android.opengl.Matrix
import com.ritvyom.yashoraReelgenerator.core.model.ColorGradingConfig
import com.ritvyom.yashoraReelgenerator.core.model.LayerBlendMode
import com.ritvyom.yashoraReelgenerator.core.model.NormalizedCropRect
import com.ritvyom.yashoraReelgenerator.core.model.Transform2D
import com.ritvyom.yashoraReelgenerator.core.model.VisualEffectSpec
import com.ritvyom.yashoraReelgenerator.engine.egl.GlShaderUtil
import java.nio.FloatBuffer

/**
 * High-performance Unified GPU Shader for 2D Transforms, Crop, Masking, Color Grading, 3D LUT, and Visual Effects.
 * Executed identically across both OpenGlPreviewRenderer and OpenGlExportRenderer.
 *
 * Deterministic Color Processing Order:
 * Input -> Exposure -> Temp/Tint -> Contrast -> Highlights/Shadows -> Saturation -> 3D LUT -> Creative FX -> Vignette -> Output
 */
class UnifiedVideoShader {

    companion object {
        private const val VERTEX_SHADER = """
            uniform mat4 uMVPMatrix;
            uniform mat4 uTexMatrix;
            attribute vec4 aPosition;
            attribute vec4 aTextureCoord;
            varying vec2 vTextureCoord;
            void main() {
                gl_Position = uMVPMatrix * aPosition;
                vTextureCoord = (uTexMatrix * aTextureCoord).xy;
            }
        """

        private const val FRAGMENT_SHADER = """
            precision mediump float;
            varying vec2 vTextureCoord;
            uniform sampler2D uTexture;
            uniform vec4 uCropRect; // left, top, right, bottom
            uniform float uOpacity;

            // Color grading parameters
            uniform float uBrightness;
            uniform float uContrast;
            uniform float uSaturation;
            uniform float uExposure;
            uniform float uWarmth;
            uniform float uTint;
            uniform float uHighlights;
            uniform float uShadows;
            uniform float uVignette;
            uniform float uSharpen;
            uniform vec2 uTexelSize;

            // 3D LUT support (packed 2D atlas)
            uniform float uHasLut;
            uniform sampler2D uLutTexture;
            uniform float uLutSize;
            uniform float uLutIntensity;

            // Creative Visual Effect
            uniform int uEffectType; // 0=None, 1=Grayscale, 2=Sepia, 3=Invert, 4=HueShift, 5=Pixelate, 6=Noise, 7=Fade, 8=Glow
            uniform float uEffectIntensity;

            // Chroma Key (Green / Blue screen removal)
            uniform int uChromaEnabled;
            uniform vec3 uChromaKeyColor;
            uniform float uChromaSensitivity;
            uniform float uChromaSoftness;
            uniform float uChromaSpill;

            // Shape Masking
            uniform int uMaskEnabled;
            uniform int uMaskShape; // 0=Rect, 1=Ellipse, 2=Linear
            uniform vec2 uMaskCenter;
            uniform vec2 uMaskSize;
            uniform float uMaskFeather;
            uniform int uMaskInverted;

            vec4 sampleLut(sampler2D lutTex, vec3 color, float lutSize) {
                float maxColor = lutSize - 1.0;
                float b = color.b * maxColor;
                float b0 = floor(b);
                float b1 = ceil(b);
                float f = b - b0;

                float sliceW = 1.0 / lutSize;
                float u0 = (b0 + (color.r * maxColor + 0.5) / lutSize) * sliceW;
                float u1 = (b1 + (color.r * maxColor + 0.5) / lutSize) * sliceW;
                float v = (color.g * maxColor + 0.5) / lutSize;

                vec4 col0 = texture2D(lutTex, vec2(u0, v));
                vec4 col1 = texture2D(lutTex, vec2(u1, v));
                return mix(col0, col1, f);
            }

            float rand(vec2 co) {
                return fract(sin(dot(co.xy, vec2(12.9898, 78.233))) * 43758.5453);
            }

            vec3 rgb2hsv(vec3 c) {
                vec4 K = vec4(0.0, -1.0 / 3.0, 2.0 / 3.0, -1.0);
                vec4 p = mix(vec4(c.bg, K.wz), vec4(c.gb, K.xy), step(c.b, c.g));
                vec4 q = mix(vec4(p.xyw, c.r), vec4(c.r, p.yzx), step(p.x, c.r));
                float d = q.x - min(q.w, q.y);
                float e = 1.0e-10;
                return vec3(abs(q.z + (q.w - q.y) / (6.0 * d + e)), d / (q.x + e), q.x);
            }

            vec3 hsv2rgb(vec3 c) {
                vec4 K = vec4(1.0, 2.0 / 3.0, 1.0 / 3.0, 3.0);
                vec3 p = abs(fract(c.xxx + K.xyz) * 6.0 - K.www);
                return c.z * mix(K.xxx, clamp(p - K.xxx, 0.0, 1.0), c.y);
            }

            void main() {
                // 1. Crop clipping
                if (vTextureCoord.x < uCropRect.x || vTextureCoord.x > uCropRect.z ||
                    vTextureCoord.y < uCropRect.y || vTextureCoord.y > uCropRect.w) {
                    discard;
                }

                vec2 coord = vTextureCoord;

                // Pixelation effect (quantize coordinates before texture sample)
                if (uEffectType == 5 && uEffectIntensity > 0.01) {
                    float pixelSize = 1.0 + uEffectIntensity * 40.0;
                    vec2 pixelTexel = uTexelSize * pixelSize;
                    coord = floor(coord / pixelTexel) * pixelTexel;
                }

                vec4 texColor = texture2D(uTexture, coord);

                // Sharpen filter pass
                if (uSharpen > 0.01) {
                    vec4 up = texture2D(uTexture, coord + vec2(0.0, -uTexelSize.y));
                    vec4 down = texture2D(uTexture, coord + vec2(0.0, uTexelSize.y));
                    vec4 left = texture2D(uTexture, coord + vec2(-uTexelSize.x, 0.0));
                    vec4 right = texture2D(uTexture, coord + vec2(uTexelSize.x, 0.0));
                    vec4 edge = 4.0 * texColor - up - down - left - right;
                    texColor = clamp(texColor + edge * (uSharpen * 0.7), 0.0, 1.0);
                }

                // Deterministic Color Pipeline
                // 1. Exposure
                vec3 color = texColor.rgb;
                if (uExposure != 0.0) {
                    color = color * exp2(uExposure);
                }

                // 2. Brightness
                if (uBrightness != 0.0) {
                    color += vec3(uBrightness);
                }

                // 3. Temperature & Tint
                if (uWarmth != 0.0) {
                    color.r += uWarmth * 0.1;
                    color.b -= uWarmth * 0.1;
                }
                if (uTint != 0.0) {
                    color.g -= uTint * 0.1;
                    color.r += uTint * 0.05;
                    color.b += uTint * 0.05;
                }

                // 4. Contrast
                if (uContrast != 1.0) {
                    color = (color - 0.5) * uContrast + 0.5;
                }

                // 5. Highlights and Shadows
                float luma = dot(color, vec3(0.2126, 0.7152, 0.0722));
                if (uHighlights != 0.0) {
                    float hWeight = smoothstep(0.5, 1.0, luma);
                    color += color * (uHighlights * hWeight * 0.5);
                }
                if (uShadows != 0.0) {
                    float sWeight = 1.0 - smoothstep(0.0, 0.5, luma);
                    color += (1.0 - color) * (uShadows * sWeight * 0.5);
                }

                // 6. Saturation
                if (uSaturation != 1.0) {
                    luma = dot(color, vec3(0.2126, 0.7152, 0.0722));
                    color = mix(vec3(luma), color, uSaturation);
                }

                // 7. 3D LUT Mapping
                if (uHasLut > 0.5 && uLutIntensity > 0.001) {
                    vec3 clampedForLut = clamp(color, 0.0, 1.0);
                    vec4 lutColor = sampleLut(uLutTexture, clampedForLut, uLutSize);
                    color = mix(color, lutColor.rgb, uLutIntensity);
                }

                // 8. Creative Visual Effects
                if (uEffectType == 1 && uEffectIntensity > 0.001) {
                    float gray = dot(color, vec3(0.299, 0.587, 0.114));
                    color = mix(color, vec3(gray), uEffectIntensity);
                } else if (uEffectType == 2 && uEffectIntensity > 0.001) {
                    vec3 sepia = vec3(
                        dot(color, vec3(0.393, 0.769, 0.189)),
                        dot(color, vec3(0.349, 0.686, 0.168)),
                        dot(color, vec3(0.272, 0.534, 0.131))
                    );
                    color = mix(color, sepia, uEffectIntensity);
                } else if (uEffectType == 3 && uEffectIntensity > 0.001) {
                    color = mix(color, 1.0 - color, uEffectIntensity);
                } else if (uEffectType == 4 && uEffectIntensity > 0.001) {
                    vec3 hsv = rgb2hsv(clamp(color, 0.0, 1.0));
                    hsv.x = fract(hsv.x + uEffectIntensity);
                    color = hsv2rgb(hsv);
                } else if (uEffectType == 6 && uEffectIntensity > 0.001) {
                    float noise = (rand(coord) - 0.5) * uEffectIntensity * 0.25;
                    color += vec3(noise);
                } else if (uEffectType == 7 && uEffectIntensity > 0.001) {
                    color = mix(color, vec3(0.0), uEffectIntensity);
                } else if (uEffectType == 8 && uEffectIntensity > 0.001) {
                    float glowLuma = dot(color, vec3(0.2126, 0.7152, 0.0722));
                    if (glowLuma > 0.6) {
                        color += color * (uEffectIntensity * 0.5);
                    }
                }

                // 9. Vignette Falloff
                if (uVignette > 0.005) {
                    vec2 centerDist = vTextureCoord - vec2(0.5, 0.5);
                    float d = length(centerDist) * 1.4142;
                    float vFactor = smoothstep(0.8, 0.8 - (uVignette * 0.6), d);
                    color *= vFactor;
                }

                // 10. Chroma Key (Green / Blue screen matting)
                float alpha = texColor.a * uOpacity;
                if (uChromaEnabled == 1) {
                    // Color distance in RGB space to key color
                    float diff = length(color - uChromaKeyColor);
                    float edge0 = uChromaSensitivity;
                    float edge1 = uChromaSensitivity + max(uChromaSoftness, 0.001);
                    float chromaAlpha = smoothstep(edge0, edge1, diff);
                    alpha *= chromaAlpha;

                    // Spill suppression: desaturate key color fringes
                    if (chromaAlpha < 0.95 && uChromaSpill > 0.01) {
                        float spillFactor = (1.0 - chromaAlpha) * uChromaSpill;
                        float gray = dot(color, vec3(0.299, 0.587, 0.114));
                        color = mix(color, vec3(gray), spillFactor);
                    }
                }

                // 11. Shape Masking
                if (uMaskEnabled == 1) {
                    vec2 maskOffset = abs(vTextureCoord - uMaskCenter);
                    float maskVal = 1.0;
                    float feather = max(uMaskFeather, 0.001);

                    if (uMaskShape == 0) {
                        // Rectangle mask
                        vec2 halfSize = uMaskSize * 0.5;
                        float dx = smoothstep(halfSize.x, halfSize.x - feather, maskOffset.x);
                        float dy = smoothstep(halfSize.y, halfSize.y - feather, maskOffset.y);
                        maskVal = dx * dy;
                    } else if (uMaskShape == 1) {
                        // Ellipse mask
                        vec2 normDist = maskOffset / max(uMaskSize * 0.5, vec2(0.001));
                        float dist = length(normDist);
                        maskVal = smoothstep(1.0, 1.0 - feather * 2.0, dist);
                    } else if (uMaskShape == 2) {
                        // Linear split
                        float splitDist = (vTextureCoord.y - uMaskCenter.y) / max(uMaskSize.y, 0.001);
                        maskVal = smoothstep(0.0, feather, splitDist + 0.5);
                    }

                    if (uMaskInverted == 1) {
                        maskVal = 1.0 - maskVal;
                    }
                    alpha *= clamp(maskVal, 0.0, 1.0);
                }

                color = clamp(color, 0.0, 1.0);
                gl_FragColor = vec4(color, alpha);
            }
        """

        private const val BACKGROUND_VERTEX_SHADER = """
            attribute vec4 aPosition;
            void main() {
                gl_Position = aPosition;
            }
        """

        private const val BACKGROUND_FRAGMENT_SHADER = """
            precision mediump float;
            uniform vec4 uColorTop;
            uniform vec4 uColorBottom;
            uniform vec2 uResolution;
            void main() {
                float t = gl_FragCoord.y / uResolution.y;
                gl_FragColor = mix(uColorBottom, uColorTop, t);
            }
        """

        private const val BLUR_FRAGMENT_SHADER = """
            precision mediump float;
            varying vec2 vTextureCoord;
            uniform sampler2D uTexture;
            uniform vec2 uTexelSize;
            uniform float uBlurRadius;

            void main() {
                vec4 sum = vec4(0.0);
                float total = 0.0;
                for (float x = -2.0; x <= 2.0; x += 1.0) {
                    for (float y = -2.0; y <= 2.0; y += 1.0) {
                        float weight = 1.0 / (1.0 + length(vec2(x, y)));
                        sum += texture2D(uTexture, vTextureCoord + vec2(x, y) * uTexelSize * uBlurRadius) * weight;
                        total += weight;
                    }
                }
                vec4 blurred = sum / total;
                // Subtle dark tint to provide contrast for foreground video
                gl_FragColor = vec4(blurred.rgb * 0.7, 1.0);
            }
        """

        private val FULL_QUAD_COORDS = floatArrayOf(
            -1.0f, -1.0f, 0.0f, // 0 bottom left
             1.0f, -1.0f, 0.0f, // 1 bottom right
            -1.0f,  1.0f, 0.0f, // 2 top left
             1.0f,  1.0f, 0.0f  // 3 top right
        )

        private val FULL_TEX_COORDS = floatArrayOf(
            0.0f, 1.0f, // bottom left (flipped for GL texture orientation)
            1.0f, 1.0f, // bottom right
            0.0f, 0.0f, // top left
            1.0f, 0.0f  // top right
        )
    }

    private var programId = 0
    private var bgProgramId = 0
    private var blurProgramId = 0

    // Handles for main shader
    private var uMVPMatrixLoc = 0
    private var uTexMatrixLoc = 0
    private var uTextureLoc = 0
    private var uCropRectLoc = 0
    private var uOpacityLoc = 0
    private var uBrightnessLoc = 0
    private var uContrastLoc = 0
    private var uSaturationLoc = 0
    private var uExposureLoc = 0
    private var uWarmthLoc = 0
    private var uTintLoc = 0
    private var uHighlightsLoc = 0
    private var uShadowsLoc = 0
    private var uVignetteLoc = 0
    private var uSharpenLoc = 0
    private var uTexelSizeLoc = 0
    private var uHasLutLoc = 0
    private var uLutTextureLoc = 0
    private var uLutSizeLoc = 0
    private var uLutIntensityLoc = 0
    private var uEffectTypeLoc = 0
    private var uEffectIntensityLoc = 0

    // Handles for Chroma Key & Masking
    private var uChromaEnabledLoc = 0
    private var uChromaKeyColorLoc = 0
    private var uChromaSensitivityLoc = 0
    private var uChromaSoftnessLoc = 0
    private var uChromaSpillLoc = 0
    private var uMaskEnabledLoc = 0
    private var uMaskShapeLoc = 0
    private var uMaskCenterLoc = 0
    private var uMaskSizeLoc = 0
    private var uMaskFeatherLoc = 0
    private var uMaskInvertedLoc = 0

    private var aPositionLoc = 0
    private var aTextureCoordLoc = 0

    // Handles for background shader
    private var bgPositionLoc = 0
    private var uColorTopLoc = 0
    private var uColorBottomLoc = 0
    private var uResolutionLoc = 0

    // Handles for blur background shader
    private var blurPositionLoc = 0
    private var blurTexCoordLoc = 0
    private var uBlurTextureLoc = 0
    private var uBlurTexelSizeLoc = 0
    private var uBlurRadiusLoc = 0

    private val quadVertexBuffer: FloatBuffer = GlShaderUtil.createFloatBuffer(FULL_QUAD_COORDS)
    private val quadTexBuffer: FloatBuffer = GlShaderUtil.createFloatBuffer(FULL_TEX_COORDS)

    private val mvpMatrix = FloatArray(16)
    private val modelMatrix = FloatArray(16)
    private val texMatrix = FloatArray(16)

    init {
        programId = GlShaderUtil.createProgram(VERTEX_SHADER, FRAGMENT_SHADER)
        aPositionLoc = GLES20.glGetAttribLocation(programId, "aPosition")
        aTextureCoordLoc = GLES20.glGetAttribLocation(programId, "aTextureCoord")
        uMVPMatrixLoc = GLES20.glGetUniformLocation(programId, "uMVPMatrix")
        uTexMatrixLoc = GLES20.glGetUniformLocation(programId, "uTexMatrix")
        uTextureLoc = GLES20.glGetUniformLocation(programId, "uTexture")
        uCropRectLoc = GLES20.glGetUniformLocation(programId, "uCropRect")
        uOpacityLoc = GLES20.glGetUniformLocation(programId, "uOpacity")
        uBrightnessLoc = GLES20.glGetUniformLocation(programId, "uBrightness")
        uContrastLoc = GLES20.glGetUniformLocation(programId, "uContrast")
        uSaturationLoc = GLES20.glGetUniformLocation(programId, "uSaturation")
        uExposureLoc = GLES20.glGetUniformLocation(programId, "uExposure")
        uWarmthLoc = GLES20.glGetUniformLocation(programId, "uWarmth")
        uTintLoc = GLES20.glGetUniformLocation(programId, "uTint")
        uHighlightsLoc = GLES20.glGetUniformLocation(programId, "uHighlights")
        uShadowsLoc = GLES20.glGetUniformLocation(programId, "uShadows")
        uVignetteLoc = GLES20.glGetUniformLocation(programId, "uVignette")
        uSharpenLoc = GLES20.glGetUniformLocation(programId, "uSharpen")
        uTexelSizeLoc = GLES20.glGetUniformLocation(programId, "uTexelSize")
        uHasLutLoc = GLES20.glGetUniformLocation(programId, "uHasLut")
        uLutTextureLoc = GLES20.glGetUniformLocation(programId, "uLutTexture")
        uLutSizeLoc = GLES20.glGetUniformLocation(programId, "uLutSize")
        uLutIntensityLoc = GLES20.glGetUniformLocation(programId, "uLutIntensity")
        uEffectTypeLoc = GLES20.glGetUniformLocation(programId, "uEffectType")
        uEffectIntensityLoc = GLES20.glGetUniformLocation(programId, "uEffectIntensity")
        uChromaEnabledLoc = GLES20.glGetUniformLocation(programId, "uChromaEnabled")
        uChromaKeyColorLoc = GLES20.glGetUniformLocation(programId, "uChromaKeyColor")
        uChromaSensitivityLoc = GLES20.glGetUniformLocation(programId, "uChromaSensitivity")
        uChromaSoftnessLoc = GLES20.glGetUniformLocation(programId, "uChromaSoftness")
        uChromaSpillLoc = GLES20.glGetUniformLocation(programId, "uChromaSpill")
        uMaskEnabledLoc = GLES20.glGetUniformLocation(programId, "uMaskEnabled")
        uMaskShapeLoc = GLES20.glGetUniformLocation(programId, "uMaskShape")
        uMaskCenterLoc = GLES20.glGetUniformLocation(programId, "uMaskCenter")
        uMaskSizeLoc = GLES20.glGetUniformLocation(programId, "uMaskSize")
        uMaskFeatherLoc = GLES20.glGetUniformLocation(programId, "uMaskFeather")
        uMaskInvertedLoc = GLES20.glGetUniformLocation(programId, "uMaskInverted")

        bgProgramId = GlShaderUtil.createProgram(BACKGROUND_VERTEX_SHADER, BACKGROUND_FRAGMENT_SHADER)
        bgPositionLoc = GLES20.glGetAttribLocation(bgProgramId, "aPosition")
        uColorTopLoc = GLES20.glGetUniformLocation(bgProgramId, "uColorTop")
        uColorBottomLoc = GLES20.glGetUniformLocation(bgProgramId, "uColorBottom")
        uResolutionLoc = GLES20.glGetUniformLocation(bgProgramId, "uResolution")

        blurProgramId = GlShaderUtil.createProgram(VERTEX_SHADER, BLUR_FRAGMENT_SHADER)
        blurPositionLoc = GLES20.glGetAttribLocation(blurProgramId, "aPosition")
        blurTexCoordLoc = GLES20.glGetAttribLocation(blurProgramId, "aTextureCoord")
        uBlurTextureLoc = GLES20.glGetUniformLocation(blurProgramId, "uTexture")
        uBlurTexelSizeLoc = GLES20.glGetUniformLocation(blurProgramId, "uTexelSize")
        uBlurRadiusLoc = GLES20.glGetUniformLocation(blurProgramId, "uBlurRadius")
    }

    /**
     * Renders background gradient or solid color filling the canonical viewport.
     */
    fun drawBackground(width: Int, height: Int, topRgba: FloatArray, bottomRgba: FloatArray) {
        GLES20.glUseProgram(bgProgramId)
        GLES20.glEnableVertexAttribArray(bgPositionLoc)
        GLES20.glVertexAttribPointer(bgPositionLoc, 3, GLES20.GL_FLOAT, false, 12, quadVertexBuffer)

        GLES20.glUniform4f(uColorTopLoc, topRgba[0], topRgba[1], topRgba[2], topRgba[3])
        GLES20.glUniform4f(uColorBottomLoc, bottomRgba[0], bottomRgba[1], bottomRgba[2], bottomRgba[3])
        GLES20.glUniform2f(uResolutionLoc, width.toFloat(), height.toFloat())

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GLES20.glDisableVertexAttribArray(bgPositionLoc)
    }

    /**
     * Renders blurred background from source media filling the entire canvas.
     */
    fun drawBlurredBackground(textureId: Int, width: Int, height: Int, blurRadius: Float = 3.5f) {
        GLES20.glUseProgram(blurProgramId)

        val mvpLoc = GLES20.glGetUniformLocation(blurProgramId, "uMVPMatrix")
        val texM = FloatArray(16)
        Matrix.setIdentityM(mvpMatrix, 0)
        Matrix.setIdentityM(texM, 0)
        GLES20.glUniformMatrix4fv(mvpLoc, 1, false, mvpMatrix, 0)
        GLES20.glUniformMatrix4fv(GLES20.glGetUniformLocation(blurProgramId, "uTexMatrix"), 1, false, texM, 0)

        GLES20.glUniform2f(uBlurTexelSizeLoc, 1.0f / width.coerceAtLeast(1), 1.0f / height.coerceAtLeast(1))
        GLES20.glUniform1f(uBlurRadiusLoc, blurRadius)

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
        GLES20.glUniform1i(uBlurTextureLoc, 0)

        GLES20.glEnableVertexAttribArray(blurPositionLoc)
        GLES20.glVertexAttribPointer(blurPositionLoc, 3, GLES20.GL_FLOAT, false, 12, quadVertexBuffer)

        GLES20.glEnableVertexAttribArray(blurTexCoordLoc)
        GLES20.glVertexAttribPointer(blurTexCoordLoc, 2, GLES20.GL_FLOAT, false, 8, quadTexBuffer)

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

        GLES20.glDisableVertexAttribArray(blurPositionLoc)
        GLES20.glDisableVertexAttribArray(blurTexCoordLoc)
    }

    /**
     * Applies hardware blend mode for multi-layer compositing.
     */
    private fun applyBlendMode(blendMode: LayerBlendMode) {
        GLES20.glEnable(GLES20.GL_BLEND)
        when (blendMode) {
            LayerBlendMode.NORMAL -> GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
            LayerBlendMode.SCREEN -> GLES20.glBlendFunc(GLES20.GL_ONE, GLES20.GL_ONE_MINUS_SRC_COLOR)
            LayerBlendMode.MULTIPLY -> GLES20.glBlendFunc(GLES20.GL_DST_COLOR, GLES20.GL_ONE_MINUS_SRC_ALPHA)
            LayerBlendMode.LIGHTEN -> GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE)
            LayerBlendMode.DARKEN -> GLES20.glBlendFunc(GLES20.GL_DST_COLOR, GLES20.GL_ZERO)
            else -> GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        }
    }

    /**
     * Draws a texture layer with full transform, crop, anchor point, color grading, 3D LUT, and visual effect passes.
     */
    fun drawLayer(
        textureId: Int,
        transform: Transform2D,
        crop: NormalizedCropRect,
        colorGrading: ColorGradingConfig,
        viewportWidth: Int,
        viewportHeight: Int,
        sourceWidth: Int = viewportWidth,
        sourceHeight: Int = viewportHeight,
        lutTextureId: Int = 0,
        lutSize: Float = 16f,
        effectSpec: VisualEffectSpec? = null,
        chromaKey: com.ritvyom.yashoraReelgenerator.engine.effects.ChromaKeySpec? = null,
        maskSpec: com.ritvyom.yashoraReelgenerator.engine.effects.MaskSpec? = null
    ) {
        GLES20.glUseProgram(programId)

        // Compute 2D affine model matrix
        Matrix.setIdentityM(modelMatrix, 0)

        // 1. Translation: normalized [-0.5 .. 0.5] mapped to NDC [-1.0 .. 1.0]
        val ndcTransX = transform.translationX * 2.0f
        val ndcTransY = -transform.translationY * 2.0f // Invert Y for screen space
        Matrix.translateM(modelMatrix, 0, ndcTransX, ndcTransY, 0.0f)

        // 2. Rotation around Z axis
        if (transform.rotationDegrees != 0f) {
            Matrix.rotateM(modelMatrix, 0, -transform.rotationDegrees, 0.0f, 0.0f, 1.0f)
        }

        // 3. Scaling
        Matrix.scaleM(modelMatrix, 0, transform.scaleX, transform.scaleY, 1.0f)

        // 4. Anchor Point Offset
        val anchorOffsetX = (transform.anchorX - 0.5f) * 2.0f
        val anchorOffsetY = -(transform.anchorY - 0.5f) * 2.0f
        if (anchorOffsetX != 0f || anchorOffsetY != 0f) {
            Matrix.translateM(modelMatrix, 0, -anchorOffsetX, -anchorOffsetY, 0.0f)
        }

        // Identity texture matrix
        Matrix.setIdentityM(texMatrix, 0)

        // Set uniforms
        GLES20.glUniformMatrix4fv(uMVPMatrixLoc, 1, false, modelMatrix, 0)
        GLES20.glUniformMatrix4fv(uTexMatrixLoc, 1, false, texMatrix, 0)

        // Crop rect
        GLES20.glUniform4f(uCropRectLoc, crop.left, crop.top, crop.right, crop.bottom)
        GLES20.glUniform1f(uOpacityLoc, transform.opacity.coerceIn(0f, 1f))

        // Color grading uniforms
        GLES20.glUniform1f(uBrightnessLoc, colorGrading.brightness)
        GLES20.glUniform1f(uContrastLoc, colorGrading.contrast)
        GLES20.glUniform1f(uSaturationLoc, colorGrading.saturation)
        GLES20.glUniform1f(uExposureLoc, colorGrading.exposure)
        GLES20.glUniform1f(uWarmthLoc, colorGrading.warmth)
        GLES20.glUniform1f(uTintLoc, colorGrading.tint)
        GLES20.glUniform1f(uHighlightsLoc, colorGrading.highlights)
        GLES20.glUniform1f(uShadowsLoc, colorGrading.shadows)
        GLES20.glUniform1f(uVignetteLoc, colorGrading.vignette)
        GLES20.glUniform1f(uSharpenLoc, colorGrading.sharpen)
        GLES20.glUniform2f(uTexelSizeLoc, 1.0f / viewportWidth.coerceAtLeast(1), 1.0f / viewportHeight.coerceAtLeast(1))

        // 3D LUT Uniforms
        if (lutTextureId > 0 && colorGrading.filterIntensity > 0) {
            GLES20.glUniform1f(uHasLutLoc, 1.0f)
            GLES20.glUniform1f(uLutSizeLoc, lutSize)
            GLES20.glUniform1f(uLutIntensityLoc, (colorGrading.filterIntensity / 100.0f).coerceIn(0f, 1f))
            GLES20.glActiveTexture(GLES20.GL_TEXTURE1)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, lutTextureId)
            GLES20.glUniform1i(uLutTextureLoc, 1)
        } else {
            GLES20.glUniform1f(uHasLutLoc, 0.0f)
            GLES20.glUniform1f(uLutIntensityLoc, 0.0f)
        }

        // Creative Visual Effect Uniforms
        val (effectType, effectIntensity) = if (effectSpec != null) {
            when (effectSpec.effectId.lowercase()) {
                "grayscale", "bw", "black & white" -> 1 to effectSpec.intensity
                "sepia", "vintage" -> 2 to effectSpec.intensity
                "invert", "negative" -> 3 to effectSpec.intensity
                "hue", "hueshift", "hue_shift" -> 4 to effectSpec.intensity
                "pixelate", "pixelation" -> 5 to effectSpec.intensity
                "noise", "grain", "film_grain" -> 6 to effectSpec.intensity
                "fade" -> 7 to effectSpec.intensity
                "glow", "bloom" -> 8 to effectSpec.intensity
                else -> 0 to 0.0f
            }
        } else {
            0 to 0.0f
        }
        GLES20.glUniform1i(uEffectTypeLoc, effectType)
        GLES20.glUniform1f(uEffectIntensityLoc, effectIntensity.coerceIn(0f, 1f))

        // Chroma Key Uniforms
        if (chromaKey != null && chromaKey.isEnabled) {
            GLES20.glUniform1i(uChromaEnabledLoc, 1)
            GLES20.glUniform3f(uChromaKeyColorLoc, chromaKey.keyColorR, chromaKey.keyColorG, chromaKey.keyColorB)
            GLES20.glUniform1f(uChromaSensitivityLoc, chromaKey.sensitivity)
            GLES20.glUniform1f(uChromaSoftnessLoc, chromaKey.softness)
            GLES20.glUniform1f(uChromaSpillLoc, chromaKey.spillSuppression)
        } else {
            GLES20.glUniform1i(uChromaEnabledLoc, 0)
        }

        // Shape Masking Uniforms
        if (maskSpec != null && maskSpec.isEnabled) {
            GLES20.glUniform1i(uMaskEnabledLoc, 1)
            val shapeInt = when (maskSpec.shape) {
                com.ritvyom.yashoraReelgenerator.engine.effects.MaskShape.RECTANGLE -> 0
                com.ritvyom.yashoraReelgenerator.engine.effects.MaskShape.ELLIPSE -> 1
                com.ritvyom.yashoraReelgenerator.engine.effects.MaskShape.LINEAR_SPLIT -> 2
            }
            GLES20.glUniform1i(uMaskShapeLoc, shapeInt)
            GLES20.glUniform2f(uMaskCenterLoc, maskSpec.centerX, maskSpec.centerY)
            GLES20.glUniform2f(uMaskSizeLoc, maskSpec.width, maskSpec.height)
            GLES20.glUniform1f(uMaskFeatherLoc, maskSpec.feather)
            GLES20.glUniform1i(uMaskInvertedLoc, if (maskSpec.isInverted) 1 else 0)
        } else {
            GLES20.glUniform1i(uMaskEnabledLoc, 0)
        }

        // Bind Main Source Texture
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
        GLES20.glUniform1i(uTextureLoc, 0)

        // Apply Multi-Layer Blend Mode
        applyBlendMode(transform.blendMode)

        // Attributes
        GLES20.glEnableVertexAttribArray(aPositionLoc)
        GLES20.glVertexAttribPointer(aPositionLoc, 3, GLES20.GL_FLOAT, false, 12, quadVertexBuffer)

        GLES20.glEnableVertexAttribArray(aTextureCoordLoc)
        GLES20.glVertexAttribPointer(aTextureCoordLoc, 2, GLES20.GL_FLOAT, false, 8, quadTexBuffer)

        // Draw quad
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

        GLES20.glDisableVertexAttribArray(aPositionLoc)
        GLES20.glDisableVertexAttribArray(aTextureCoordLoc)
        GLES20.glDisable(GLES20.GL_BLEND)
    }

    fun release() {
        if (programId != 0) {
            GLES20.glDeleteProgram(programId)
            programId = 0
        }
        if (bgProgramId != 0) {
            GLES20.glDeleteProgram(bgProgramId)
            bgProgramId = 0
        }
        if (blurProgramId != 0) {
            GLES20.glDeleteProgram(blurProgramId)
            blurProgramId = 0
        }
    }
}
