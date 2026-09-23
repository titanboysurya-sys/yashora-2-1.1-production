package com.ritvyom.yashoraReelgenerator.engine.effects

import android.graphics.Bitmap
import android.opengl.GLES20
import android.opengl.GLUtils
import android.util.Log
import com.ritvyom.yashoraReelgenerator.engine.egl.GlShaderUtil
import java.io.BufferedReader
import java.io.File
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Production-grade 3D LUT (.cube) and 2D Atlas Parser and GPU Texture Manager.
 * Compliant with Adobe/DaVinci Resolve .cube specifications.
 * Packs 3D LUTs into a 2D texture atlas (N * N x N) compatible across both OpenGL ES 2.0 and 3.0.
 */
object LutParser {

    private const val TAG = "LutParser"

    data class LutData(
        val title: String,
        val size: Int,
        val domainMin: FloatArray = floatArrayOf(0f, 0f, 0f),
        val domainMax: FloatArray = floatArrayOf(1f, 1f, 1f),
        val table: FloatArray // Size = size * size * size * 3
    ) {
        val totalEntries: Int get() = size * size * size

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as LutData
            return title == other.title && size == other.size && table.contentEquals(other.table)
        }

        override fun hashCode(): Int {
            var result = title.hashCode()
            result = 31 * result + size
            result = 31 * result + table.contentHashCode()
            return result
        }
    }

    /**
     * Parses a .cube format from an input stream safely without crashing.
     * Returns null if the data is invalid, malformed, or incomplete.
     */
    fun parseCube(inputStream: InputStream): LutData? {
        return try {
            val reader = BufferedReader(InputStreamReader(inputStream))
            var title = "LUT"
            var size = 0
            val domainMin = floatArrayOf(0f, 0f, 0f)
            val domainMax = floatArrayOf(1f, 1f, 1f)
            val values = mutableListOf<Float>()

            reader.forEachLine { rawLine ->
                val line = rawLine.trim()
                if (line.isNotEmpty() && !line.startsWith("#")) {
                    when {
                        line.startsWith("TITLE", ignoreCase = true) -> {
                            val parts = line.split("\"", limit = 3)
                            if (parts.size >= 2) title = parts[1]
                        }
                        line.startsWith("LUT_3D_SIZE", ignoreCase = true) -> {
                            val parts = line.split("\\s+".toRegex())
                            if (parts.size >= 2) {
                                size = parts[1].toIntOrNull() ?: 0
                            }
                        }
                        line.startsWith("DOMAIN_MIN", ignoreCase = true) -> {
                            val parts = line.split("\\s+".toRegex())
                            if (parts.size >= 4) {
                                domainMin[0] = parts[1].toFloatOrNull() ?: 0f
                                domainMin[1] = parts[2].toFloatOrNull() ?: 0f
                                domainMin[2] = parts[3].toFloatOrNull() ?: 0f
                            }
                        }
                        line.startsWith("DOMAIN_MAX", ignoreCase = true) -> {
                            val parts = line.split("\\s+".toRegex())
                            if (parts.size >= 4) {
                                domainMax[0] = parts[1].toFloatOrNull() ?: 1f
                                domainMax[1] = parts[2].toFloatOrNull() ?: 1f
                                domainMax[2] = parts[3].toFloatOrNull() ?: 1f
                            }
                        }
                        else -> {
                            // RGB float triplet line: "r g b"
                            val parts = line.split("\\s+".toRegex())
                            if (parts.size >= 3) {
                                val r = parts[0].toFloatOrNull()
                                val g = parts[1].toFloatOrNull()
                                val b = parts[2].toFloatOrNull()
                                if (r != null && g != null && b != null) {
                                    values.add(r)
                                    values.add(g)
                                    values.add(b)
                                }
                            }
                        }
                    }
                }
            }

            if (size <= 0) {
                Log.w(TAG, "Invalid or missing LUT_3D_SIZE in .cube file")
                return null
            }

            val expectedCount = size * size * size * 3
            if (values.size < expectedCount) {
                Log.w(TAG, "Insufficient data entries in .cube: got ${values.size}, expected $expectedCount")
                return null
            }

            val table = FloatArray(expectedCount)
            for (i in 0 until expectedCount) {
                table[i] = values[i].coerceIn(0f, 1f)
            }

            LutData(title = title, size = size, domainMin = domainMin, domainMax = domainMax, table = table)
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing .cube LUT file", e)
            null
        }
    }

    /**
     * Parses a .cube file from the given file path.
     */
    fun parseCubeFile(file: File): LutData? {
        if (!file.exists() || !file.canRead()) {
            Log.w(TAG, "LUT file does not exist or cannot be read: ${file.absolutePath}")
            return null
        }
        return file.inputStream().use { parseCube(it) }
    }

    /**
     * Generates a deterministic neutral identity 3D LUT of given dimension [size].
     */
    fun createIdentityLut(size: Int = 16): LutData {
        val total = size * size * size * 3
        val table = FloatArray(total)
        var idx = 0
        for (b in 0 until size) {
            val blue = b.toFloat() / (size - 1).coerceAtLeast(1)
            for (g in 0 until size) {
                val green = g.toFloat() / (size - 1).coerceAtLeast(1)
                for (r in 0 until size) {
                    val red = r.toFloat() / (size - 1).coerceAtLeast(1)
                    table[idx++] = red
                    table[idx++] = green
                    table[idx++] = blue
                }
            }
        }
        return LutData(title = "Identity", size = size, table = table)
    }

    /**
     * Packs [LutData] into a 2D texture atlas byte buffer of dimensions:
     * width = size * size, height = size.
     * Format: RGBA_8888 for optimal hardware texture compatibility.
     */
    fun packTo2DAtlas(lutData: LutData): ByteBuffer {
        val n = lutData.size
        val width = n * n
        val height = n
        val buffer = ByteBuffer.allocateDirect(width * height * 4).order(ByteOrder.nativeOrder())

        // Standard arrangement: blue index selects horizontal slice
        // x = b * n + r, y = g
        val rgbaArray = ByteArray(width * height * 4)
        var tableIdx = 0

        for (b in 0 until n) {
            for (g in 0 until n) {
                for (r in 0 until n) {
                    val redByte = (lutData.table[tableIdx++].coerceIn(0f, 1f) * 255f).toInt().toByte()
                    val greenByte = (lutData.table[tableIdx++].coerceIn(0f, 1f) * 255f).toInt().toByte()
                    val blueByte = (lutData.table[tableIdx++].coerceIn(0f, 1f) * 255f).toInt().toByte()
                    val alphaByte = 255.toByte()

                    val pixelX = b * n + r
                    val pixelY = g
                    val byteOffset = (pixelY * width + pixelX) * 4

                    rgbaArray[byteOffset] = redByte
                    rgbaArray[byteOffset + 1] = greenByte
                    rgbaArray[byteOffset + 2] = blueByte
                    rgbaArray[byteOffset + 3] = alphaByte
                }
            }
        }

        buffer.put(rgbaArray)
        buffer.position(0)
        return buffer
    }

    /**
     * Uploads the [LutData] to an OpenGL 2D texture.
     */
    fun uploadLutTexture(lutData: LutData): Int {
        val n = lutData.size
        val width = n * n
        val height = n
        val byteBuffer = packTo2DAtlas(lutData)

        val texId = GlShaderUtil.create2DTexture()
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texId)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)

        GLES20.glTexImage2D(
            GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA,
            width, height, 0,
            GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, byteBuffer
        )
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
        return texId
    }
}
