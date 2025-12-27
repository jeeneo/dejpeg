package com.je.dejpeg.compose.utils

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object CacheManager {
    private const val TAG = "CacheManager"
    private const val CHUNKS_DIR = "processing_chunks"
    private const val CAMERA_IMPORTS_DIR = "cameraimports"
    private const val SHARED_IMAGE_NAME = "shared_image.png"

    fun getCameraImportsDir(context: Context): File {
        return File(context.cacheDir, CAMERA_IMPORTS_DIR).apply {
            if (!exists()) mkdirs()
        }
    }

    fun getChunksDir(context: Context): File {
        return File(context.cacheDir, CHUNKS_DIR).apply {
            if (!exists()) mkdirs()
        }
    }
    
    suspend fun clearChunks(context: Context) = withContext(Dispatchers.IO) {
        val chunksDir = File(context.cacheDir, CHUNKS_DIR)
        if (chunksDir.exists()) {
            val files = chunksDir.listFiles()
            val count = files?.size ?: 0
            if (count > 0) {
                Log.d(TAG, "Clearing $count chunk files")
                chunksDir.deleteRecursively()
            }
        }
    }
    
    fun clearChunksSync(context: Context) {
        val chunksDir = File(context.cacheDir, CHUNKS_DIR)
        if (chunksDir.exists()) {
            val count = chunksDir.listFiles()?.size ?: 0
            if (count > 0) {
                chunksDir.deleteRecursively()
                Log.d(TAG, "Cleared $count chunks (sync)")
            }
        }
    }

    suspend fun cleanupProcessedImage(context: Context, imageId: String) = withContext(Dispatchers.IO) {
        context.cacheDir.listFiles()?.forEach { file ->
            if (file.isFile && file.name.contains(imageId) && file.name.endsWith("_processed.png")) {
                if (file.delete()) {
                    Log.d(TAG, "Deleted processed cache: ${file.name}")
                }
            }
        }
    }

    fun cleanEntireCacheSync(context: Context) {
        var deletedCount = 0
        context.cacheDir.listFiles()?.forEach { file ->
            if (file.isFile && file.name.endsWith("_processed.png")) {
                if (file.delete()) deletedCount++
            }
        }
        File(context.cacheDir, SHARED_IMAGE_NAME).let {
            if (it.exists() && it.delete()) deletedCount++
        }
        clearChunksSync(context)
        clearCameraImportsSync(context)
        context.cacheDir.listFiles()?.forEach { file ->
            if (file.isFile && file.name.startsWith("brisque_assess_")) {
                if (file.delete()) deletedCount++
            }
        }
        if (deletedCount > 0) {
            Log.d(TAG, "Cleaned entire cache: $deletedCount files")
        }
    }

    private fun clearCameraImportsSync(context: Context) {
        val cameraDir = File(context.cacheDir, CAMERA_IMPORTS_DIR)
        if (cameraDir.exists()) {
            val count = cameraDir.listFiles()?.size ?: 0
            if (count > 0) {
                cameraDir.deleteRecursively()
                Log.d(TAG, "Cleared $count camera import files")
            }
        }
    }
}