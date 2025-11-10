package com.je.dejpeg.compose.utils

import android.content.Context
import android.util.Log
import java.io.File
import java.util.zip.ZipInputStream

object ZipExtractor {
    private const val TAG = "ZipExtractor"
    fun extractFromAssets(
        context: Context,
        assetFileName: String,
        targetDir: File = File(context.filesDir, "models")
    ): Boolean {
        return try {
            if (!targetDir.exists()) {
                targetDir.mkdirs()
            }

            context.assets.open(assetFileName).use { inputStream ->
                ZipInputStream(inputStream).use { zipInputStream ->
                    var entry = zipInputStream.nextEntry
                    while (entry != null) {
                        if (!entry.isDirectory) {
                            val outputFile = File(targetDir, entry.name)
                            outputFile.parentFile?.mkdirs()
                            
                            outputFile.outputStream().use { fileOutputStream ->
                                zipInputStream.copyTo(fileOutputStream)
                            }
                            Log.d(TAG, "Extracted: ${entry.name}")
                        }
                        entry = zipInputStream.nextEntry
                    }
                }
            }
            Log.d(TAG, "Successfully extracted $assetFileName to ${targetDir.absolutePath}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting $assetFileName: ${e.message}", e)
            false
        }
    }

    fun modelsExist(
        context: Context,
        targetDir: File = File(context.filesDir, "models")
    ): Boolean {
        val modelFile = File(targetDir, "brisque_model_live.yml")
        val rangeFile = File(targetDir, "brisque_range_live.yml")
        return modelFile.exists() && rangeFile.exists()
    }

    fun getModelsDir(context: Context): File {
        return File(context.filesDir, "models")
    }

    fun getModelPath(context: Context, filename: String): String {
        return File(getModelsDir(context), filename).absolutePath
    }
}
