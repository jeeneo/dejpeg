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
    LITERT(listOf(".tflite"), R.string.invalid_file_type),
    OIDN(listOf(".tza"), R.string.invalid_tza_file_type);

    fun matches(filename: String): Boolean {
        val lower = filename.lowercase()
        return extensions.any { lower.endsWith(it) }
    }
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
        ModelType.LITERT -> ModelMigrationHelper.getLiteRtModelsDir(context)
        ModelType.OIDN -> ModelMigrationHelper.getTzaModelsDir(context)
    }

    companion object {
        private const val STARTER_MODELS_ASSET_DIR = "embedonnx"

        private val MODEL_INFO_RES_IDS = mapOf(
            "fbcnn_color_fp16.tflite" to R.string.model_info_fbcnn_color_fp16
        )

        private val MIN_SPATIAL_SIZE_BY_NAME = mapOf(
            "nafnet" to 512
        )

        private val MIN_OVERLAP_SIZE_BY_NAME = mapOf(
            "scunet" to 128
        )
    }

    fun getMinSpatialSize(modelName: String?): Int {
        val normalized = modelName?.lowercase() ?: return 256
        for ((pattern, size) in MIN_SPATIAL_SIZE_BY_NAME) {
            if (normalized.contains(pattern)) {
                return size
            }
        }
        return 256
    }

    fun getMinOverlapSize(modelName: String?): Int {
        val normalized = modelName?.lowercase() ?: return 0
        for ((pattern, size) in MIN_OVERLAP_SIZE_BY_NAME) {
            if (normalized.contains(pattern)) {
                return size
            }
        }
        return 0
    }

    data class ModelWarning(
        val titleResId: Int,
        val messageResId: Int,
        val positiveButtonTextResId: Int,
        val negativeButtonTextResId: Int
    )

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

    fun loadModel(useGpu: Boolean = true): Interpreter {
        val activeModel =
            getActiveModelName(ModelType.LITERT) ?: throw Exception("No active model set")
        if (currentInterpreter != null && activeModel == currentModelName) {
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
        
        val modelFile = File(getModelsDir(ModelType.LITERT), activeModel)
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
                        isPrecisionLossAllowed = false
                        val serializationDir = File(context.cacheDir, "gpu_delegate_cache").apply { mkdirs() }
                        setSerializationParams(
                            serializationDir.absolutePath,
                            "model_${activeModel.hashCode()}"
                        )
                    }
                    gpuDelegate = GpuDelegate(delegateOptions)
                    opts.addDelegate(gpuDelegate)
                    Log.d("ModelManager", "GPU delegate enabled for $activeModel (fp32, serialized)")
                } else {
                    Log.w("ModelManager", "GPU delegate not supported, using CPU")
                    opts.numThreads = Runtime.getRuntime().availableProcessors().coerceIn(1, 4)
                }
            } else {
                opts.numThreads = Runtime.getRuntime().availableProcessors().coerceIn(1, 4)
            }
            currentInterpreter = Interpreter(mapped, opts)
            currentModelName = activeModel
            setCurrentProcessingModel(activeModel)
            Log.d("ModelManager", "Successfully loaded LiteRT model: $activeModel (gpu=${gpuDelegate != null})")
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
        val interpreter = currentInterpreter
        val delegate = gpuDelegate
        currentInterpreter = null
        currentModelName = null
        gpuDelegate = null
        
        try {
            interpreter?.close()
        } catch (e: Exception) {
            Log.e("ModelManager", "Error closing interpreter: ${e.message}")
        }
        try {
            delegate?.close()
        } catch (e: Exception) {
            Log.e("ModelManager", "Error closing GPU delegate: ${e.message}")
        }
        System.gc()
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
            onDeleted(modelName)
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

    fun initializeStarterModel(): Boolean {
        try {
            val alreadyExtracted = runBlocking {
                appPreferences.getStarterModelExtractedImmediate()
            }
            if (alreadyExtracted) {
                Log.d("ModelManager", "Starter model already extracted, skipping")
                return false
            }
            val modelsDir = getModelsDir(ModelType.LITERT)
            val hasModels = modelsDir.exists() && modelsDir.listFiles { _, name ->
                ModelType.LITERT.matches(name)
            }?.isNotEmpty() == true
            if (hasModels) {
                Log.d("ModelManager", "Models already exist, skipping starter model extraction")
                markStarterModelExtracted()
                return false
            }
            return extractStarterModel(setAsActive = true)
        } catch (e: Exception) {
            Log.e("ModelManager", "Error initializing starter model: ${e.message}", e)
            return false
        }
    }

    private fun markStarterModelExtracted() {
        coroutineScope.launch {
            appPreferences.setStarterModelExtracted(true)
        }
    }

    fun extractStarterModel(
        setAsActive: Boolean = false, onSuccess: () -> Unit = {}, onError: (String) -> Unit = {}
    ): Boolean {
        return try {
            val modelsDir = getModelsDir(ModelType.LITERT)
            val shouldSetAsActive =
                setAsActive || !modelsDir.exists() || modelsDir.listFiles { _, name ->
                    ModelType.LITERT.matches(name)
                }?.isEmpty() == true
            val extracted = copyStarterModelsFromAssets(modelsDir)
            if (extracted) {
                if (shouldSetAsActive) {
                    setActiveModel("1x-span-anime-pretrain-fp16.tflite", ModelType.LITERT)
                }
                markStarterModelExtracted()
                onSuccess()
                return true
            }
            onError(context.getString(R.string.failed_to_extract_starter_models))
            false
        } catch (e: Exception) {
            Log.e("ModelManager", "Error extracting starter models: ${e.message}", e)
            onError(e.message ?: context.getString(R.string.unknown_error))
            false
        }
    }

    private fun copyStarterModelsFromAssets(targetDir: File): Boolean {
        return try {
            if (!targetDir.exists()) targetDir.mkdirs()
            val assetFiles = context.assets.list(STARTER_MODELS_ASSET_DIR) ?: emptyArray()
            if (assetFiles.isEmpty()) {
                Log.w(
                    "ModelManager",
                    "No starter model files found in assets/$STARTER_MODELS_ASSET_DIR"
                )
                return false
            }
            for (filename in assetFiles) {
                val outFile = File(targetDir, filename)
                context.assets.open("$STARTER_MODELS_ASSET_DIR/$filename").use { input ->
                    FileOutputStream(outFile).use { output -> input.copyTo(output) }
                }
                Log.d("ModelManager", "Copied starter model: $filename")
            }
            Log.d("ModelManager", "Successfully copied ${assetFiles.size} starter model(s)")
            true
        } catch (e: Exception) {
            Log.e("ModelManager", "Error copying starter models from assets: ${e.message}", e)
            false
        }
    }
}
