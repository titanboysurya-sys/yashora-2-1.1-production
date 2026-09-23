package com.ritvyom.yashoraReelgenerator.presentation.utils

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import com.ritvyom.yashoraReelgenerator.data.local.entities.ExportHistoryEntity
import com.ritvyom.yashoraReelgenerator.data.local.entities.ProjectEntity
import java.io.File

/**
 * File Management Utility that maps Room database metadata (ProjectEntity, ExportHistoryEntity)
 * to persistent video file paths in local storage and MediaStore, enabling 100% offline playback.
 */
object VideoFileManager {
    private const val TAG = "VideoFileManager"
    const val INTERNAL_COMPILED_DIR = "YashoraCompiledVideos"
    const val PUBLIC_REELS_SUBDIR = "Yashora"

    /**
     * Resolves a physical File for a project or export history item,
     * checking primary external storage, MediaStore, and internal master backups.
     * Strictly isolates files to the requested project so no other project's video plays.
     */
    fun resolvePlayableFile(context: Context, filePath: String?, projectId: Int? = null): File? {
        // If projectId is provided, prioritize project-isolated master video
        if (projectId != null && projectId > 0) {
            val internalMasterDir = File(context.filesDir, INTERNAL_COMPILED_DIR)
            if (internalMasterDir.exists()) {
                val projMaster = File(internalMasterDir, "project_${projectId}_master.mp4")
                if (projMaster.exists() && projMaster.length() > 0) {
                    return projMaster
                }
            }
        }

        if (filePath.isNullOrBlank()) return resolveInternalMasterByProjectId(context, projectId)

        // 1. Direct path lookup (verify it does not belong to a different project)
        val directFile = File(filePath)
        if (directFile.exists() && directFile.length() > 0) {
            if (projectId != null && projectId > 0) {
                // If it's a project master file for another ID, reject to prevent cross-project leaks
                val fname = directFile.name
                if (fname.startsWith("project_") && fname.endsWith("_master.mp4")) {
                    if (fname != "project_${projectId}_master.mp4") {
                        return resolveInternalMasterByProjectId(context, projectId)
                    }
                }
            }
            return directFile
        }

        // 2. Check Internal Master Copy in app filesDir
        val fileName = filePath.substringAfterLast("/")
        val internalMasterDir = File(context.filesDir, INTERNAL_COMPILED_DIR)
        if (internalMasterDir.exists()) {
            if (projectId != null && projectId > 0) {
                val projMaster = File(internalMasterDir, "project_${projectId}_master.mp4")
                if (projMaster.exists() && projMaster.length() > 0) {
                    return projMaster
                }
            }
            val fileByName = File(internalMasterDir, fileName)
            if (fileByName.exists() && fileByName.length() > 0) {
                if (projectId != null && projectId > 0) {
                    if (fileName.startsWith("project_") && fileName.endsWith("_master.mp4") && fileName != "project_${projectId}_master.mp4") {
                        // Belongs to another project, do not return
                    } else {
                        return fileByName
                    }
                } else {
                    return fileByName
                }
            }
        }

        // 3. Public Movies/Yashora folder check
        try {
            val moviesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
            val yashoraFolder = File(moviesDir, PUBLIC_REELS_SUBDIR)
            val publicFile = File(yashoraFolder, fileName)
            if (publicFile.exists() && publicFile.length() > 0) {
                return publicFile
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed public Movies dir lookup", e)
        }

        // 4. Cache directory fallback
        val cacheFile = File(context.cacheDir, fileName)
        if (cacheFile.exists() && cacheFile.length() > 0) {
            return cacheFile
        }

        return resolveInternalMasterByProjectId(context, projectId)
    }

    private fun resolveInternalMasterByProjectId(context: Context, projectId: Int?): File? {
        if (projectId == null || projectId <= 0) return null
        val internalMasterDir = File(context.filesDir, INTERNAL_COMPILED_DIR)
        if (!internalMasterDir.exists()) return null
        val masterFile = File(internalMasterDir, "project_${projectId}_master.mp4")
        return if (masterFile.exists() && masterFile.length() > 0) masterFile else null
    }

    /**
     * Resolves a Uri suitable for ExoPlayer/VideoView offline playback.
     */
    fun getPlayableUri(context: Context, filePath: String?, projectId: Int? = null): Uri? {
        val file = resolvePlayableFile(context, filePath, projectId)
        if (file != null && file.exists()) {
            return Uri.fromFile(file)
        }

        // Query MediaStore by display name as fallback
        if (!filePath.isNullOrBlank()) {
            try {
                val fileName = filePath.substringAfterLast("/")
                val projection = arrayOf(MediaStore.Video.Media._ID)
                val selection = "${MediaStore.Video.Media.DISPLAY_NAME} = ?"
                val selectionArgs = arrayOf(fileName)

                context.contentResolver.query(
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                    projection,
                    selection,
                    selectionArgs,
                    null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                        val id = cursor.getLong(idColumn)
                        return Uri.withAppendedPath(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id.toString())
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "MediaStore URI lookup failed for $filePath", e)
            }
        }

        return null
    }

    /**
     * Maps Room ProjectEntity metadata to local video file path.
     */
    fun mapProjectToFilePath(context: Context, project: ProjectEntity): String? {
        return resolvePlayableFile(context, null, project.id)?.absolutePath
    }

    /**
     * Maps Room ExportHistoryEntity metadata to local video file path.
     */
    fun mapExportHistoryToFilePath(context: Context, history: ExportHistoryEntity): String? {
        return resolvePlayableFile(context, history.filePath, history.projectId)?.absolutePath
    }

    /**
     * Calculates total temporary cache size in bytes.
     */
    fun getCacheSizeBytes(context: Context): Long {
        var total = 0L
        try {
            total += getFolderSizeBytes(context.cacheDir)
            context.externalCacheDir?.let { total += getFolderSizeBytes(it) }
        } catch (e: Exception) {
            Log.e(TAG, "Error calculating cache size", e)
        }
        return total
    }

    /**
     * Calculates internal video master backup size in bytes.
     */
    fun getMasterVideosSizeBytes(context: Context): Long {
        return getFolderSizeBytes(File(context.filesDir, INTERNAL_COMPILED_DIR))
    }

    private fun getFolderSizeBytes(folder: File?): Long {
        if (folder == null || !folder.exists()) return 0L
        var size = 0L
        val files = folder.listFiles() ?: return 0L
        for (f in files) {
            size += if (f.isDirectory) getFolderSizeBytes(f) else f.length()
        }
        return size
    }

    /**
     * Formats byte length to human readable string (e.g., 42.5 MB).
     */
    fun formatSizeBytes(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val kb = bytes / 1024.0
        if (kb < 1) return "$bytes B"
        val mb = kb / 1024.0
        if (mb < 1) return String.format("%.1f KB", kb)
        val gb = mb / 1024.0
        return if (gb < 1) String.format("%.1f MB", mb) else String.format("%.2f GB", gb)
    }

    /**
     * Clears local temporary app cache (voice clips, image downloads, temp video chunks).
     * Optionally clears internal master video copies.
     */
    fun clearLocalCache(context: Context, clearCompiledMasters: Boolean = false): Long {
        var freedBytes = 0L
        try {
            val cacheSizeBefore = getCacheSizeBytes(context)
            deleteDirectoryContents(context.cacheDir)
            context.externalCacheDir?.let { deleteDirectoryContents(it) }
            freedBytes += cacheSizeBefore

            if (clearCompiledMasters) {
                val masterDir = File(context.filesDir, INTERNAL_COMPILED_DIR)
                val masterSizeBefore = getFolderSizeBytes(masterDir)
                deleteDirectoryContents(masterDir)
                freedBytes += masterSizeBefore
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing local cache", e)
        }
        return freedBytes
    }

    /**
     * Cleans up local temporary files, partial/failed video generation outputs,
     * and orphan temporary chunks in cache and temp directories before database operations.
     */
    fun cleanupTemporaryFilesAndFailedAttempts(context: Context, activeVideoPath: String? = null) {
        try {
            val cacheFiles = context.cacheDir.listFiles() ?: emptyArray()
            for (f in cacheFiles) {
                if (f.isFile && f.absolutePath != activeVideoPath) {
                    val name = f.name.lowercase()
                    if (name.endsWith(".tmp") || name.endsWith(".temp") || f.length() == 0L ||
                        (name.endsWith(".mp4") && name.startsWith("temp_")) ||
                        (name.endsWith(".mp4") && name.contains("failed"))) {
                        try { f.delete() } catch (e: Exception) { Log.w(TAG, "Failed deleting temp file ${f.name}", e) }
                    }
                }
            }

            context.externalCacheDir?.listFiles()?.forEach { f ->
                if (f.isFile && f.absolutePath != activeVideoPath) {
                    val name = f.name.lowercase()
                    if (name.endsWith(".tmp") || name.endsWith(".temp") || f.length() == 0L ||
                        (name.endsWith(".mp4") && name.startsWith("temp_"))) {
                        try { f.delete() } catch (e: Exception) { Log.w(TAG, "Failed deleting ext temp file ${f.name}", e) }
                    }
                }
            }
            Log.d(TAG, "Completed cleanup of temporary files and failed video generation attempts.")
        } catch (e: Exception) {
            Log.e(TAG, "Error cleaning up temporary files and failed attempts", e)
        }
    }

    private fun deleteDirectoryContents(dir: File?): Boolean {
        if (dir == null || !dir.exists()) return true
        val files = dir.listFiles() ?: return true
        var success = true
        for (file in files) {
            if (file.isDirectory) {
                success = success && deleteDirectoryContents(file)
                file.delete()
            } else {
                success = success && file.delete()
            }
        }
        return success
    }
}
