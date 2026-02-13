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

package com.je.dejpeg.compose.utils.helpers

import android.content.Context
import android.util.Log
import com.je.dejpeg.data.AppPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object ModelMigrationHelper {
    private const val TAG = "ModelMigrationHelper"
    fun getOnnxModelsDir(context: Context): File = File(context.filesDir, "models/onnx")
    fun getBrisqueModelsDir(context: Context): File = File(context.filesDir, "models/brisque")
    suspend fun migrateModelsIfNeeded(context: Context): Boolean = withContext(Dispatchers.IO) {
        val appPreferences = AppPreferences(context.applicationContext)
        val onnxResult = migrateFiles(
            label = "ONNX",
            sourceDir = context.filesDir,
            targetDir = getOnnxModelsDir(context),
            fileFilter = { it.isFile && it.name.lowercase().endsWith(".onnx") },
            isComplete = { appPreferences.getCompatModelCleanupImmediate() },
            markComplete = { appPreferences.setCompatModelCleanup(true) }
        )
        val brisqueResult = migrateFiles(
            label = "BRISQUE",
            sourceDir = File(context.filesDir, "models"),
            targetDir = getBrisqueModelsDir(context),
            fileFilter = { it.isFile && it.name in listOf("brisque_model_live.yml", "brisque_range_live.yml") },
            isComplete = { appPreferences.getCompatBrisqueCleanupImmediate() },
            markComplete = { appPreferences.setCompatBrisqueCleanup(true) }
        )
        onnxResult && brisqueResult
    }
    
    private suspend fun migrateFiles(
        label: String,
        sourceDir: File,
        targetDir: File,
        fileFilter: (File) -> Boolean,
        isComplete: suspend () -> Boolean,
        markComplete: suspend () -> Unit
    ): Boolean {
        if (isComplete()) {
            Log.d(TAG, "$label migration already completed, skipping")
            return true
        }
        
        return try {
            val filesToMigrate = sourceDir.listFiles(fileFilter) ?: emptyArray()
            
            if (filesToMigrate.isEmpty()) {
                Log.d(TAG, "No $label files found, marking migration as complete")
                markComplete()
                return true
            }
            
            if (!targetDir.exists() && !targetDir.mkdirs()) {
                Log.e(TAG, "Failed to create $label directory: ${targetDir.absolutePath}")
                return false
            }
            
            var successCount = 0
            for (file in filesToMigrate) {
                if (moveFile(file, File(targetDir, file.name))) successCount++
            }
            
            Log.d(TAG, "$label migration complete: $successCount/${filesToMigrate.size} files migrated")
            markComplete()
            true
        } catch (e: Exception) {
            Log.e(TAG, "$label migration failed: ${e.message}", e)
            false
        }
    }
    
    private fun moveFile(source: File, target: File): Boolean {
        return try {
            if (target.exists()) {
                Log.d(TAG, "File already exists in target, deleted old: ${source.name}")
                source.delete()
            } else if (!source.renameTo(target)) {
                source.copyTo(target, overwrite = true)
                source.delete()
                Log.d(TAG, "Copied and deleted: ${source.name}")
            } else {
                Log.d(TAG, "Moved: ${source.name}")
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to migrate ${source.name}: ${e.message}")
            false
        }
    }
}
