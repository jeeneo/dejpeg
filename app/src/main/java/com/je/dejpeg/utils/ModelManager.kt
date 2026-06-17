/*
 * SPDX-FileCopyrightText: 2025 - 2026 dryerlint <https://codeberg.org/dryerlint>
 * SPDX-License-Identifier: GNU Affero General Public License v3.0 or later
 */

@file:Suppress("SpellCheckingInspection")

package com.je.dejpeg.utils

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import com.je.dejpeg.AppPreferences
import com.je.dejpeg.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.CompatibilityList
import org.tensorflow.lite.gpu.GpuDelegate
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.channels.FileChannel

enum class ModelType(val extensions: List<String>, val invalidFileTypeResId: Int) {
    LITERT(listOf(".tflite"), R.string.invalid_file_type), OIDN(
        listOf(".tza"), R.string.invalid_tza_file_type
    );

    fun matches(filename: String): Boolean {
        val lower = filename.lowercase()
        return extensions.any { lower.endsWith(it) }
    }
}

/**
 * Represents the GPU delegate cache status for a model
 */
enum class GpuCacheStatus {
    /** GPU cache does not exist yet (first run) */
    NOT_AVAILABLE,

    /** GPU cache exists and is ready for use */
    READY,

    /** GPU cache is currently being prepared */
    PREPARING
}

class ModelManager(
    private val context: Context,
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) {
    private var currentInterpreter: Interpreter? = null
    private var gpuDelegate: GpuDelegate? = null
    private var currentModelName: String? = null
    private val cachedActiveModels = mutableMapOf<ModelType, String?>()
    private val appPreferences = AppPreferences()

    private fun getModelsDir(type: ModelType = ModelType.LITERT): File = when (type) {
        ModelType.LITERT -> File(context.filesDir, "models/litert")
        ModelType.OIDN -> File(context.filesDir, "models/tza")
    }

    companion object {
        private val MODEL_INFO_RES_IDS = mapOf(
            "fbcnn_color_fp16.tflite" to R.string.model_info_fbcnn_color_fp16
        )
        private const val GPU_DELEGATE_CACHE_DIR = "gpu_delegate_cache"
        fun gpuCacheToken(modelName: String): String {
            val stem = modelName.replace("[^a-zA-Z0-9_-]".toRegex(), "_").trimEnd('_')
            return stem
        }
        fun gpuCacheDir(context: Context) = File(context.cacheDir, GPU_DELEGATE_CACHE_DIR)
        fun gpuCacheFiles(context: Context, modelName: String): List<File> {
            val dir = gpuCacheDir(context)
            if (!dir.exists()) return emptyList()
            val prefix = gpuCacheToken(modelName)
            return dir.listFiles { f -> f.name.startsWith(prefix) }?.toList() ?: emptyList()
        }
        fun gpuCacheExists(context: Context, modelName: String) =
            gpuCacheFiles(context, modelName).isNotEmpty()
    }

    fun hasActiveModel(type: ModelType = ModelType.LITERT): Boolean {
        val activeModel = getActiveModelName(type)
        return activeModel != null && File(getModelsDir(type), activeModel).exists()
    }

    fun getActiveModelName(type: ModelType = ModelType.LITERT): String? {
        cachedActiveModels[type]?.let { return it }
        return runBlocking {
            val name = when (type) {
                ModelType.LITERT -> appPreferences.getActiveModel()
                ModelType.OIDN -> appPreferences.getActiveOidnModel()
            }
            name.also { cachedActiveModels[type] = it }
        }
    }

    fun getCurrentModelName(): String? {
        return currentModelName
    }

    fun setActiveModel(modelName: String, type: ModelType = ModelType.LITERT) {
        Log.d("ModelManager", "setActiveModel($type) called with: $modelName")
        if (type == ModelType.LITERT) unloadModel()
        cachedActiveModels[type] = modelName
        coroutineScope.launch {
            when (type) {
                ModelType.LITERT -> appPreferences.setActiveModel(modelName)
                ModelType.OIDN -> appPreferences.setActiveOidnModel(modelName)
            }
            Log.d("ModelManager", "Active $type model saved to DataStore: $modelName")
        }
    }

    private fun clearActiveModel(type: ModelType = ModelType.LITERT) {
        cachedActiveModels.remove(type)
        coroutineScope.launch {
            when (type) {
                ModelType.LITERT -> appPreferences.clearActiveModel()
                ModelType.OIDN -> appPreferences.clearActiveOidnModel()
            }
        }
    }

    private fun setCurrentProcessingModel(modelName: String) {
        coroutineScope.launch {
            appPreferences.setCurrentProcessingModel(modelName)
        }
    }

    fun getInstalledModels(type: ModelType = ModelType.LITERT): List<String> {
        // should run sweep
        val modelsDir = getModelsDir(type)
        if (!modelsDir.exists()) return emptyList()
        val files = modelsDir.listFiles { _, name -> type.matches(name) }
        return files?.map { it.name } ?: emptyList()
    }

    fun getActiveModelPath(type: ModelType = ModelType.OIDN): String? {
        val modelName = getActiveModelName(type) ?: return null
        val modelFile = File(getModelsDir(type), modelName)
        return if (modelFile.exists()) modelFile.absolutePath else null
    }

    fun loadModel(modelName: String? = null, useGpu: Boolean = true): Interpreter {
        val modelToLoad = modelName ?: getActiveModelName(ModelType.LITERT)
        ?: throw Exception("No active model set")
        if (currentInterpreter != null && modelToLoad == currentModelName) {
            return currentInterpreter!!
        }

        val oldInterpreter = currentInterpreter
        val oldDelegate = gpuDelegate
        currentInterpreter = null
        currentModelName = null
        gpuDelegate = null

        try {
            oldInterpreter?.close()
        } catch (e: Exception) {
            Log.e("ModelManager", "Error closing old interpreter: ${e.message}")
        }
        try {
            oldDelegate?.close()
        } catch (e: Exception) {
            Log.e("ModelManager", "Error closing old GPU delegate: ${e.message}")
        }
        System.gc()
        System.runFinalization()
        System.gc()

        val modelFile = File(getModelsDir(ModelType.LITERT), modelToLoad)
        if (!modelFile.exists()) throw Exception("Model file does not exist: ${modelFile.absolutePath}")

        try {
            val mapped = FileInputStream(modelFile).use { fis ->
                fis.channel.map(FileChannel.MapMode.READ_ONLY, 0, modelFile.length())
            }
            val opts = Interpreter.Options()
            if (useGpu) {
                val compatList = CompatibilityList()
                if (compatList.isDelegateSupportedOnThisDevice) {
                    val delegateOptions = compatList.bestOptionsForThisDevice.apply {
                        isPrecisionLossAllowed = true
                        val serializationDir = gpuCacheDir(context).apply { mkdirs() }
                        setSerializationParams(
                            serializationDir.absolutePath, gpuCacheToken(modelToLoad)
                        )
                    }
                    gpuDelegate = GpuDelegate(delegateOptions)
                    opts.addDelegate(gpuDelegate)
                    Log.d(
                        "ModelManager",
                        "GPU delegate enabled for $modelToLoad with options: $delegateOptions"
                    )
                } else {
                    Log.w("ModelManager", "GPU delegate not supported, using CPU")
                    opts.numThreads = Runtime.getRuntime().availableProcessors().coerceIn(1, 4)
                }
            } else {
                opts.numThreads = Runtime.getRuntime().availableProcessors().coerceIn(1, 4)
            }
            currentInterpreter = Interpreter(mapped, opts)
            currentModelName = modelToLoad
            setCurrentProcessingModel(modelToLoad)
            Log.d(
                "ModelManager", "Successfully loaded LiteRT model: $modelToLoad (gpu=$useGpu)"
            )
            return currentInterpreter!!
        } catch (e: Exception) {
            Log.e("ModelManager", "Error loading model: ${e.message}", e)
            currentInterpreter = null
            currentModelName = null
            throw e
        }
    }

    fun unloadModel() {
        Log.d("ModelManager", "unloadModel called, clearing interpreter for: $currentModelName")
        currentModelName = null
        try {
            currentInterpreter?.close()
            currentInterpreter = null
        } catch (e: Exception) {
            Log.e("ModelManager", "Error closing interpreter: ${e.message}")
        }
        try {
            gpuDelegate?.close()
            gpuDelegate = null
        } catch (e: Exception) {
            Log.e("ModelManager", "Error closing GPU delegate: ${e.message}")
        }
        System.runFinalization()
        System.gc()
    }

    fun importModel(
        modelUri: Uri,
        type: ModelType = ModelType.LITERT,
        onProgress: (Int) -> Unit = {},
        onSuccess: (String) -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        try {
            val filename = resolveFilename(modelUri)
            if (!type.matches(filename)) {
                onError(context.getString(type.invalidFileTypeResId))
                return
            }
            importModelInternal(modelUri, filename, type, onProgress, onSuccess, onError)
        } catch (e: Exception) {
            onError(e.message ?: context.getString(R.string.unknown_error))
        }
    }

    @SuppressLint("Recycle")
    private fun resolveFilename(uri: Uri): String {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) {
                    return cursor.getString(idx).trim()
                }
            }
        }
        val path = uri.path
        if (path != null && path.contains("/")) {
            return path.substring(path.lastIndexOf('/') + 1).trim()
        }
        return uri.lastPathSegment?.trim() ?: "model.tflite"
    }

    @SuppressLint("Recycle")
    private fun importModelInternal(
        uri: Uri,
        filename: String,
        type: ModelType,
        onProgress: (Int) -> Unit,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        try {
            val modelsDir = getModelsDir(type)
            if (!modelsDir.exists()) modelsDir.mkdirs()
            val modelFile = File(modelsDir, filename)
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val size =
                    context.contentResolver.openFileDescriptor(uri, "r")?.use { it.statSize } ?: 0L
                FileOutputStream(modelFile).use { outputStream ->
                    copyWithProgress(inputStream, outputStream, size, onProgress)
                }
            }
            onProgress(100)
            onSuccess(filename)
        } catch (e: Exception) {
            onError(e.message ?: context.getString(R.string.failed_to_import_model))
        }
    }

    private fun copyWithProgress(
        input: InputStream, output: OutputStream, totalSize: Long, onProgress: (Int) -> Unit
    ) {
        val buffer = ByteArray(8192)
        var bytesRead: Int
        var totalRead = 0L
        var lastProgress = 0
        while (input.read(buffer).also { bytesRead = it } != -1) {
            output.write(buffer, 0, bytesRead)
            totalRead += bytesRead
            if (totalSize > 0) {
                val progress = ((totalRead * 100) / totalSize).toInt()
                if (progress != lastProgress) {
                    onProgress(progress)
                    lastProgress = progress
                }
            }
        }
    }

    fun deleteModel(
        modelName: String, type: ModelType = ModelType.LITERT, onDeleted: (String) -> Unit = {}
    ) {
        val modelFile = File(getModelsDir(type), modelName)
        if (modelFile.exists()) {
            modelFile.delete()
            deleteGpuCache(modelName, type)
            onDeleted(modelName)
            val cacheDir = gpuCacheDir(context)
            if (!cacheDir.exists()) return
            val modelsDir = File(context.filesDir, "models/litert")
            val installedTokens = if (modelsDir.exists()) {
                modelsDir.listFiles { f -> ModelType.LITERT.matches(f.name) }
                    ?.map { gpuCacheToken(it.name) }.orEmpty().toSet()
            } else {
                emptySet()
            }
            var swept = 0
            cacheDir.listFiles()?.forEach { file ->
                val isOrphaned = installedTokens.none { token -> file.name.startsWith(token) }
                if (isOrphaned) {
                    file.delete()
                    swept++
                }
            }
            if (swept > 0) {
                Log.d("ModelManager", "Swept $swept abandoned GPU cache file(s)")
            }
        }
        if (modelName == getActiveModelName(type)) {
            val remaining = getInstalledModels(type)
            if (remaining.isNotEmpty()) {
                setActiveModel(remaining.first(), type)
            } else {
                clearActiveModel(type)
            }
        }
    }

    fun getModelInfo(modelName: String?): String? {
        if (modelName == null) return null
        val match = MODEL_INFO_RES_IDS.keys.firstOrNull { key ->
            modelName.contains(key)
        } ?: return null
        val resId = MODEL_INFO_RES_IDS[match] ?: return null
        val info = context.getString(resId)
        return when {
            modelName.contains(
                "fbcnn", ignoreCase = true
            ) -> info + "\n\n" + context.getString(R.string.model_info_fbcnn_suffix)

            modelName.contains(
                "scunet", ignoreCase = true
            ) -> info + "\n\n" + context.getString(R.string.model_info_scunet_suffix)

            else -> info
        }
    }

    fun getGpuCacheStatus(modelName: String, type: ModelType = ModelType.LITERT): GpuCacheStatus {
        if (type != ModelType.LITERT) return GpuCacheStatus.NOT_AVAILABLE
        return if (gpuCacheExists(context, modelName)) GpuCacheStatus.READY
        else GpuCacheStatus.NOT_AVAILABLE
    }

    fun deleteGpuCache(modelName: String, type: ModelType = ModelType.LITERT): Boolean {
        if (type != ModelType.LITERT) return false
        val files = gpuCacheFiles(context, modelName)
        return if (files.isNotEmpty()) {
            val result = files.all { it.delete() }
            Log.d("ModelManager", "GPU cache deleted for $modelName: $result (${files.size} files)")
            result
        } else {
            false
        }
    }
}
