/**
 * Copyright (C) 2025/2026 dryerlint <codeberg.org/dryerlint>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

/*
* If you use this code in your own project, please give credit
*/

package com.je.dejpeg.compose.utils

import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

object CacheManager {
    private const val TAG = "CacheManager"
    private const val CHUNKS_DIR = "processing_chunks"
    private const val UNPROCESSED_SUFFIX = "_unprocessed"

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
                Log.d(TAG, "Clearing $count interrupted chunk files")
            }
            chunksDir.deleteRecursively()
        }
    }

    fun clearAbandonedImages(context: Context) {
        abandonedImages(context).forEach { file ->
            if (file.delete()) {
                Log.d(TAG, "Deleted abandoned image: ${file.name}")
            }
        }
    }

    suspend fun saveProcessedImage(
        context: Context, imageId: String, bitmap: android.graphics.Bitmap
    ) = withContext(Dispatchers.IO) {
        val file = File(context.cacheDir, "${imageId}_processed.png")
        try {
            FileOutputStream(file).use {
                bitmap.compress(
                    android.graphics.Bitmap.CompressFormat.PNG, 100, it
                )
            }
            Log.d(TAG, "Saved processed image: ${file.name}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save processed image: ${e.message}")
        }
    }

    suspend fun saveUnprocessedImage(context: Context, imageId: String, uri: Uri) =
        withContext(Dispatchers.IO) {
            try {
                val extension = getFileExtension(context, uri)
                val file = File(context.cacheDir, "${imageId}${UNPROCESSED_SUFFIX}.${extension}")
                if (file.exists()) {
                    Log.d(
                        TAG,
                        "Unprocessed image already exists for given imageId ($imageId), skipping save: ${file.name}"
                    )
                    return@withContext
                }
                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(file).use { output ->
                        input.copyTo(output)
                    }
                }
                Log.d(TAG, "Saved unprocessed for restore: ${file.name}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save unprocessed image: ${e.message}")
            }
        }

    private fun getFileExtension(context: Context, uri: Uri): String {
        val path = uri.path
        if (path != null) {
            val ext = path.substringAfterLast('.', "")
            if (ext.isNotEmpty() && ext.length <= 4) {
                return ext.lowercase()
            }
        }
        val mimeType = context.contentResolver.getType(uri)
        return when (mimeType) {
            "image/jpeg" -> "jpg"
            "image/png" -> "png"
            "image/webp" -> "webp"
            else -> "jpg"
        }
    }

    fun deleteRecoveryPair(
        context: Context,
        imageId: String,
        deleteProcessed: Boolean = true,
        deleteUnprocessed: Boolean = true
    ) {
        if (deleteUnprocessed) {
            context.cacheDir.listFiles()?.forEach { file ->
                if (file.name.startsWith("${imageId}${UNPROCESSED_SUFFIX}.")) {
                    if (file.delete()) {
                        Log.d(TAG, "Deleted unprocessed image cache: ${file.name}")
                    }
                }
            }
        }
        if (deleteProcessed) {
            val processedFile = File(context.cacheDir, "${imageId}_processed.png")
            if (processedFile.exists() && processedFile.delete()) {
                Log.d(TAG, "Deleted processed image cache: ${processedFile.name}")
            }
        }
        Log.d(
            TAG,
            "Deleted recovery pair for imageId: $imageId (processed=$deleteProcessed, unprocessed=$deleteUnprocessed)"
        )
    }

    fun getRecoveryImages(context: Context): List<Pair<String, File>> {
        val result = mutableListOf<Pair<String, File>>()
        context.cacheDir.listFiles()?.forEach { file ->
            if (file.isFile && file.name.endsWith("_processed.png")) {
                val imageId = file.name.removeSuffix("_processed.png")
                result.add(imageId to file)
            }
        }
        return result
    }

    fun getUnprocessedImage(context: Context, imageId: String): File? {
        return context.cacheDir.listFiles()?.find { file ->
            file.name.startsWith("${imageId}${UNPROCESSED_SUFFIX}.")
        }
    }

    fun abandonedImages(context: Context): List<File> {
        val result = mutableListOf<File>()
        context.cacheDir.listFiles()?.forEach { file ->
            if (file.isFile && file.name.contains(UNPROCESSED_SUFFIX)) {
                val imageId = file.name.substringBefore(UNPROCESSED_SUFFIX)
                val processedFile = File(context.cacheDir, "${imageId}_processed.png")
                if (!processedFile.exists()) {
                    result.add(file)
                }
            }
        }
        return result
    }
}
