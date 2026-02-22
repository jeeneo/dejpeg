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
    fun getTzaModelsDir(context: Context): File = File(context.filesDir, "models/tza")
    fun getBrisqueModelsDir(context: Context): File = File(context.filesDir, "models/brisque")

    suspend fun migrateModelsIfNeeded(context: Context): Boolean = withContext(Dispatchers.IO) {
        val prefs = AppPreferences(context.applicationContext)
        val onnx = migrateFiles(
            "ONNX",
            context.filesDir,
            getOnnxModelsDir(context),
            { it.isFile && it.name.lowercase().endsWith(".onnx") },
            { prefs.getCompatModelCleanupImmediate() },
            { prefs.setCompatModelCleanup(true) })
        val brisque = deleteDeprecated(
            listOf(File(context.filesDir, "models"), getBrisqueModelsDir(context)),
            listOf("brisque_model_live.yml", "brisque_range_live.yml")
        )
        onnx && brisque
    }

    private suspend fun migrateFiles(
        label: String,
        src: File,
        dst: File,
        filter: (File) -> Boolean,
        done: suspend () -> Boolean,
        mark: suspend () -> Unit
    ): Boolean {
        if (done()) return true
        return try {
            val files = src.listFiles(filter) ?: emptyArray()
            if (files.isEmpty()) {
                mark(); return true
            }
            if (!dst.exists() && !dst.mkdirs()) return false
            val count = files.count { moveFile(it, File(dst, it.name)) }
            Log.d(TAG, "$label migration: $count/${files.size} moved")
            mark(); true
        } catch (e: Exception) {
            Log.e(TAG, "$label migration failed: ${e.message}", e); false
        }
    }

    private fun deleteDeprecated(dirs: List<File>, names: List<String>): Boolean =
        try {
            val count = dirs.sumOf { dir ->
                (dir.listFiles { f -> f.isFile && f.name in names }
                    ?: emptyArray()).count { f -> f.delete() }
            }
            dirs.forEach { dir ->
                if (dir.exists() && dir.isDirectory && (dir.listFiles()
                        ?.isEmpty() == true)
                ) dir.delete()
            }
            Log.d(
                TAG,
                if (count > 0) "Deleted $count deprecated BRISQUE file(s)" else "No deprecated BRISQUE files"
            ); true
        } catch (e: Exception) {
            Log.e(TAG, "BRISQUE cleanup failed: ${e.message}", e); false
        }

    private fun moveFile(src: File, dst: File): Boolean = try {
        when {
            dst.exists() -> src.delete()
            !src.renameTo(dst) -> {
                src.copyTo(dst, true); src.delete()
            }
        }; true
    } catch (_: Exception) {
        false
    }
}
