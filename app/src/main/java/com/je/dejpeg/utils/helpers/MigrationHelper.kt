/* SPDX-FileCopyrightText: 2025 - 2026 dryerlint <https://codeberg.org/dryerlint>
 * SPDX-License-Identifier: GNU Affero General Public License v3.0 or later
 */

package com.je.dejpeg.utils.helpers

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import com.je.dejpeg.data.AppPreferences
import com.je.dejpeg.data.PreferenceKeys
import com.je.dejpeg.data.dataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

// to be removed sometime in 4.x.x prior to 5.x.x (if we even get there, honestly ive put way too much work into a simple image processor like frfr bro its not rocket science)
object ModelMigrationHelper {
    private const val TAG = "ModelMigrationHelper"
    fun getOnnxModelsDir(context: Context): File = File(context.filesDir, "models/onnx")
    fun getTzaModelsDir(context: Context): File = File(context.filesDir, "models/tza")
    fun getBrisqueModelsDir(context: Context): File = File(context.filesDir, "models/brisque")

    suspend fun migrateModelsIfNeeded(context: Context): Boolean = withContext(Dispatchers.IO) {
        val prefs = AppPreferences(context.applicationContext)
        context.dataStore.edit { store ->
            val oldKey = booleanPreferencesKey("skipSaveDialog")
            if (store[oldKey] != null) {
                store[PreferenceKeys.SHOW_SAVE_DIALOG] =
                    !(store[oldKey]!!) // convert to bool, invert, and delete old key
                store.remove(oldKey)
                Log.d(TAG, "Migrated 'skipSaveDialog' to 'showSaveDialog'")
            }
        }
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

    private fun deleteDeprecated(dirs: List<File>, names: List<String>): Boolean = try {
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
