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
    
    fun clearChunks(context: Context) {
        val chunksDir = File(context.cacheDir, CHUNKS_DIR)
        if (chunksDir.exists()) {
            val count = chunksDir.listFiles()?.size ?: 0
            if (count > 0) {
                Log.d(TAG, "Clearing $count interupted chunk files")
            }
            chunksDir.deleteRecursively()
        }
        abandonedImages(context).forEach { file ->
            if (file.delete()) {
                Log.d(TAG, "Deleted abandoned image: ${file.name}")
            }
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

    suspend fun saveUnprocessedImage(context: Context, imageId: String, bitmap: android.graphics.Bitmap) = withContext(Dispatchers.IO) {
        val file = File(context.cacheDir, "${imageId}${UNPROCESSED_SUFFIX}")
        try {
            FileOutputStream(file).use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            Log.d(TAG, "Saved unprocessed image: ${file.name}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save unprocessed image: ${e.message}")
        }
    }

    fun deleteRecoveryPair(context: Context, imageId: String) {
        Log.d(TAG, "Deleting recovery pair for imageId: $imageId")
        val file = File(context.cacheDir, "${imageId}${UNPROCESSED_SUFFIX}")
        if (file.exists() && file.delete()) {
            Log.d(TAG, "Deleted unprocessed image cache: ${file.name}")
        }
        val processedFile = File(context.cacheDir, "${imageId}${PROCESSED_SUFFIX}")
        if (processedFile.exists() && processedFile.delete()) {
            Log.d(TAG, "Deleted processed image cache: ${processedFile.name}")
        }
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

    fun abandonedImages(context: Context): List<File> {
        val result = mutableListOf<File>()
        context.cacheDir.listFiles()?.forEach { file ->
            if (file.isFile && file.name.endsWith(UNPROCESSED_SUFFIX)) {
                val imageId = file.name.removeSuffix(UNPROCESSED_SUFFIX)
                val processedFile = File(context.cacheDir, "${imageId}${PROCESSED_SUFFIX}")
                if (!processedFile.exists()) {
                    result.add(file)
                }
            }
        }
        return result
    }
}
