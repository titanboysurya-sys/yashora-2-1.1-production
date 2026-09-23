package com.ritvyom.yashoraReelgenerator.presentation.utils

import android.util.Log
import java.io.File

/**
 * FFmpegWrapper logic ensuring modern and legacy media players can read exported MP4s properly.
 * Uses reflection to check if FFmpegKit is dynamically available, executing high-compatibility
 * H.264 video + AAC audio transcoding. FALLS BACK to local MediaMuxer logic where dependencies aren't present.
 */
object FFmpegVideoWrapper {
    private const val TAG = "FFmpegVideoWrapper"

    /**
     * Checks if the `com.arthenica.ffmpegkit.FFmpegKit` library is loaded.
     */
    fun isFFmpegAvailable(): Boolean {
        return try {
            Class.forName("com.arthenica.ffmpegkit.FFmpegKit")
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Executes FFmpeg commands to ensure the final output is flawlessly coded as .mp4 with H.264/AAC.
     */
    fun executeFFmpegMux(
        videoFile: File,
        audioFile: File,
        outputFile: File
    ): Boolean {
        if (!isFFmpegAvailable()) {
            Log.d(TAG, "FFmpegKit is not available. Falling back to native MediaMuxer.")
            return false
        }
        try {
            Log.d(TAG, "FFmpegKit detected! Executing robust transcode/mux...")
            
            val ffmpegKitClass = Class.forName("com.arthenica.ffmpegkit.FFmpegKit")
            val executeMethod = ffmpegKitClass.getMethod("execute", String::class.java)

            // Construct CMD arguments for H.264 (libx264) with yuv420p pixels and AAC
            val command = "-y -i \"${videoFile.absolutePath}\" -i \"${audioFile.absolutePath}\" " +
                    "-c:v libx264 -pix_fmt yuv420p -profile:v high -level:v 4.1 " +
                    "-c:a aac -b:a 128k -map 0:v:0 -map 1:a:0 \"${outputFile.absolutePath}\""

            Log.d(TAG, "FFmpeg Command: $command")
            val session = executeMethod.invoke(null, command)

            // Determine check return codes
            val getReturnCodeMethod = session.javaClass.getMethod("getReturnCode")
            val returnCode = getReturnCodeMethod.invoke(session)

            val isSuccessMethod = returnCode.javaClass.getMethod("isValueSuccess")
            val isSuccess = isSuccessMethod.invoke(returnCode) as Boolean

            Log.d(TAG, "FFmpeg processing finished. Success is: $isSuccess")
            return isSuccess
        } catch (e: Exception) {
            Log.e(TAG, "Failed executing FFmpeg reflection wrapper", e)
            return false
        }
    }
}
