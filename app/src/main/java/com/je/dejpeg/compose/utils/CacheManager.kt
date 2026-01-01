package com.je.dejpeg.compose.utils

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

object CacheManager {
    private const val TAG = "CacheManager"
    private const val CHUNKS_DIR = "processing_chunks"
    private const val CAMERA_IMPORTS_DIR = "cameraimports"
    private const val SHARED_IMAGE_NAME = "shared_image.png"
    private const val PROCESSED_SUFFIX = "_processed.png"
    private const val UNPROCESSED_SUFFIX = "_unprocessed.png"

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
            }
            chunksDir.deleteRecursively()
        }
    }
    
    fun clearChunksSync(context: Context) {
        val chunksDir = File(context.cacheDir, CHUNKS_DIR)
        if (chunksDir.exists()) {
            val count = chunksDir.listFiles()?.size ?: 0
            if (count > 0) {
                Log.d(TAG, "Cleared $count chunks (sync)")
            }
            chunksDir.deleteRecursively()
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
                Log.d(TAG, "Cleared $count camera import files")
            }
            cameraDir.deleteRecursively()
        }
    }

    suspend fun saveProcessedImage(context: Context, imageId: String, bitmap: android.graphics.Bitmap) = withContext(Dispatchers.IO) {
        val file = File(context.cacheDir, "${imageId}${PROCESSED_SUFFIX}")
        try {
            FileOutputStream(file).use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            Log.d(TAG, "Saved processed image: ${file.name}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save processed image: ${e.message}")
        }
    }

    fun deleteProcessedImage(context: Context, imageId: String) {
        val file = File(context.cacheDir, "${imageId}${PROCESSED_SUFFIX}")
        if (file.exists() && file.delete()) {
            Log.d(TAG, "Deleted processed image cache: ${file.name}")
        }
    }

    suspend fun saveUnprocessedImage(context: Context, imageId: String, bitmap: android.graphics.Bitmap) = withContext(Dispatchers.IO) {
        val file = File(context.cacheDir, "${imageId}${UNPROCESSED_SUFFIX}")
        try {
            FileOutputStream(file).use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            Log.d(TAG, "Saved unprocessed image: ${file.name}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save unprocessed image: ${e.message}")
        }
    }

    fun deleteUnprocessedImage(context: Context, imageId: String) {
        val file = File(context.cacheDir, "${imageId}${UNPROCESSED_SUFFIX}")
        if (file.exists() && file.delete()) {
            Log.d(TAG, "Deleted unprocessed image cache: ${file.name}")
        }
    }

    fun deleteRecoveryPair(context: Context, imageId: String) {
        deleteProcessedImage(context, imageId)
        deleteUnprocessedImage(context, imageId)
    }

    fun getRecoveryImages(context: Context): List<Pair<String, File>> {
        val result = mutableListOf<Pair<String, File>>()
        context.cacheDir.listFiles()?.forEach { file ->
            if (file.isFile && file.name.endsWith(PROCESSED_SUFFIX)) {
                val imageId = file.name.removeSuffix(PROCESSED_SUFFIX)
                result.add(imageId to file)
            }
        }
        return result
    }

    fun getUnprocessedImage(context: Context, imageId: String): File? {
        val file = File(context.cacheDir, "${imageId}${UNPROCESSED_SUFFIX}")
        return if (file.exists()) file else null
    }
}
