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
*
*/

/*
* If you use this code in your own project, please give credit
*/

package com.je.dejpeg.compose.utils

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaScannerConnection
import android.os.Environment
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.content.ClipData
import android.util.Log
import com.je.dejpeg.R

object ImageActions {
    fun checkFileExists(filename: String): Boolean {
        val fileNameRaw = filename.takeIf { it.isNotBlank() } ?: return false
        val fileName = fileNameRaw.substringBeforeLast('.', fileNameRaw)
        val picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
        val outputFile = File(picturesDir, "$fileName.png")
        return outputFile.exists()
    }

    fun saveImage(context: Context, bitmap: Bitmap, filename: String? = null, imageId: String? = null, onSuccess: () -> Unit = {}, onError: (String) -> Unit = {}) {
        @OptIn(DelicateCoroutinesApi::class)
        GlobalScope.launch(Dispatchers.IO) {
            try {
                val fileNameRaw = filename?.takeIf { it.isNotBlank() } ?: "DeJPEG_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}"
                val fileName = fileNameRaw.substringBeforeLast('.', fileNameRaw)
                val picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                val outputFile = File(picturesDir, "$fileName.png")
                if (outputFile.exists()) outputFile.delete()
                FileOutputStream(outputFile).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                if (imageId != null) {
                    CacheManager.deleteRecoveryPair(context, imageId, deleteProcessed = true, deleteUnprocessed = false)
                }
                withContext(Dispatchers.Main) {
                    MediaScannerConnection.scanFile(context, arrayOf(outputFile.toString()), null, null)
                    Toast.makeText(context, context.getString(R.string.image_saved_to_gallery), Toast.LENGTH_SHORT).show()
                    onSuccess()
                }
            } catch (e: Exception) {
                val errorMsg = context.getString(R.string.error_saving_image, e.message)
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, errorMsg, Toast.LENGTH_SHORT).show()
                    onError(errorMsg)
                }
            }
        }
    }
    
    fun shareImage(context: Context, bitmap: Bitmap, onError: (String) -> Unit = {}) {
        try {
            val cachePath = File(context.cacheDir, "shared_image.png")
            FileOutputStream(cachePath).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            val contentUri = FileProvider.getUriForFile(context, "${context.packageName}.provider", cachePath)
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                setType("image/png")
                putExtra(Intent.EXTRA_STREAM, contentUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                clipData = ClipData.newRawUri(null, contentUri)
            }
            context.startActivity(Intent.createChooser(shareIntent, context.getString(R.string.share_image)))
        } catch (e: Exception) {
            val errorMsg = context.getString(R.string.error_sharing_image, e.message)
            Toast.makeText(context, errorMsg, Toast.LENGTH_SHORT).show()
            onError(errorMsg)
        }
    }

    fun saveAllImages(context: Context, images: List<Pair<String, Bitmap>>, baseFilename: String? = null, onProgress: (Int, Int) -> Unit = { _, _ -> }, onComplete: () -> Unit = {}, onError: (String) -> Unit = {}) {
        @OptIn(DelicateCoroutinesApi::class)
        GlobalScope.launch(Dispatchers.IO) {
            try {
                val usedFilenames = mutableSetOf<String>()
                images.forEachIndexed { index, (originalFilename, bitmap) ->
                    val preferredFilenameRaw = if (images.size > 1) originalFilename.ifBlank { "${baseFilename ?: "DeJPEG"}_${index + 1}" } else originalFilename.ifBlank { baseFilename ?: "DeJPEG" }
                    val preferredFilename = preferredFilenameRaw.substringBeforeLast('.', preferredFilenameRaw)
                    val fileName = generateUniqueFilename(preferredFilename, usedFilenames)
                    usedFilenames.add(fileName)
                    val outputFile = saveBitmapToPictures(fileName, bitmap)
                    MediaScannerConnection.scanFile(context, arrayOf(outputFile.toString()), null, null)
                    withContext(Dispatchers.Main) {
                        onProgress(index + 1, images.size)
                    }
                }
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, context.resources.getQuantityString(R.plurals.all_images_saved_to_gallery, images.size, images.size), Toast.LENGTH_SHORT).show()
                    onComplete()
                }
            } catch (e: Exception) {
                val errorMsg = context.getString(R.string.error_saving_images, e.message)
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, errorMsg, Toast.LENGTH_SHORT).show()
                    onError(errorMsg)
                }
            }
        }
    }

    private fun saveBitmapToPictures(baseName: String, bitmap: Bitmap): File {
        val outputFile = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "$baseName.png")
        FileOutputStream(outputFile).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return outputFile
    }

    private fun generateUniqueFilename(base: String, existing: Set<String>): String {
        if (!existing.contains(base)) return base
        var counter = 1
        var newName = "${base}_$counter"
        while (existing.contains(newName)) { counter++; newName = "${base}_$counter" }
        return newName
    }
}
