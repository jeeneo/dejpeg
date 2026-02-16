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
import android.util.Log
import java.io.File
import java.util.zip.ZipInputStream

object ZipExtractor {
    private const val TAG = "ZipExtractor"
    fun extractFromAssets(
        context: Context,
        assetFileName: String,
        targetDir: File
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
}
