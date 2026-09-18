/*
 * SPDX-FileCopyrightText: 2025 - 2026 dryerlint <https://codeberg.org/dryerlint>
 * SPDX-License-Identifier: GNU Affero General Public License v3.0 or later
 */

package com.je.dejpeg.utils

import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtException
import ai.onnxruntime.OrtLoggingLevel
import ai.onnxruntime.OrtSession
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import com.je.dejpeg.BuildConfig
import com.je.dejpeg.R
import com.je.dejpeg.data.AppPreferences
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream

enum class ModelType(val extensions: List<String>, val enabled: Boolean = true) {
    ONNX(listOf(".onnx", ".ort"), true), OIDN(listOf(".tza"), BuildConfig.OIDN_ENABLED), LITERT(
        listOf(".tflite"), BuildConfig.LITERT_ENABLED
    );

    fun matches(filename: String): Boolean {
        if (!enabled) return false
        val lower = filename.lowercase()
        return extensions.any { lower.endsWith(it) }
    }

    companion object {
        fun fromFilename(filename: String): ModelType? =
            entries.find { it.enabled && it.matches(filename) }

        fun fromString(value: String?): ModelType =
            entries.find { it.enabled && it.name == value } ?: ONNX
    }
}

open class ModelManager(
    protected val context: Context
) {
    private var currentSession: OrtSession? = null
    private var ortEnv: OrtEnvironment? = null
    protected val cachedActiveModels = mutableMapOf<ModelType, String?>()
    private val appPreferences = AppPreferences()

    protected fun getModelsDir(type: ModelType = ModelType.ONNX): File = when (type) {
        ModelType.ONNX -> ModelMigrationHelper.getOnnxModelsDir(context)
        ModelType.OIDN -> ModelMigrationHelper.getTzaModelsDir(context)
        ModelType.LITERT -> ModelMigrationHelper.getLiteRtModelsDir(context)
    }

    companion object {
        private const val STARTER_MODELS_ASSET_DIR = "embedonnx"
        const val STARTER_MODEL_NAME = "1x_StarSample_V2.0_Lite_NS.onnx"
        private const val GPU_DELEGATE_CACHE_DIR = "gpu_delegate_cache"
        private val MODEL_INFO_RES_IDS = mapOf(
            // fbcnn (jpeg model)
            "fbcnn_color" to R.string.model_info_fbcnn_color,
            "fbcnn_gray" to R.string.model_info_fbcnn_gray,
            "fbcnn_gray_double" to R.string.model_info_fbcnn_gray_double,

            // scunet (noise model)
            "scunet_color_real_gan" to R.string.model_info_scunet_color_real_gan,
            "scunet_color_real_psnr" to R.string.model_info_scunet_color_real_psnr,
            "scunet_color_15" to R.string.model_info_scunet_color_15,
            "scunet_color_25" to R.string.model_info_scunet_color_25,
            "scunet_color_50" to R.string.model_info_scunet_color_50,
            "scunet_gray_15" to R.string.model_info_scunet_gray_15,
            "scunet_gray_25" to R.string.model_info_scunet_gray_25,
            "scunet_gray_50" to R.string.model_info_scunet_gray_50,

            "deblurring_nafnet_2025may" to R.string.model_info_deblurring_nafnet_2025may,

            // small models
            "1x-AnimeUndeint-Compact-fp16" to R.string.model_info_1x_anime_undeint_compact,
            "1x-BroadcastToStudio_Compact-fp16" to R.string.model_info_1x_broadcast_to_studio_compact,
            "1x-WB-Denoise-fp16" to R.string.model_info_1x_wb_denoise,
            "1xBook-Compact-fp16" to R.string.model_info_1x_book_compact,
            "1xOverExposureCorrection_compact-fp16" to R.string.model_info_1x_over_exposure_correction_compact,
            "1x-RGB-max-Denoise-fp16" to R.string.model_info_1x_rgb_max_denoise,
            "1x-span-anime-pretrain-fp16" to R.string.model_info_1x_span_anime_pretrain,

            // other compression
            "1x_JPEGDestroyerV2_96000G-fp16" to R.string.model_info_1x_jpeg_destroyer_v2_96000g,
            "1x-NMKD-Jaywreck3-Lite-fp16" to R.string.model_info_1x_nmkd_jaywreck3_lite,
            "1x_NMKD-h264Texturize-fp16" to R.string.model_info_1x_nmkd_h264_texturize,
            "VHS-Sharpen-1x_46000_G-fp16" to R.string.model_info_vhs_sharpen_1x_46000_g,
            "1x_BCGone_Smooth_110000_G-fp16" to R.string.model_info_1x_bc_gone_smooth_110000_g,
            "1x-cinepak-fp16" to R.string.model_info_1x_cinepak,
            "1x_BCGone-DetailedV2_40-60_115000_G-fp16" to R.string.model_info_1x_bc_gone_detailed_v2_40_60_115000_g,
            "1x-DeBink-v4" to R.string.model_info_1x_de_bink_v4,
            "1x-DeBink-v5" to R.string.model_info_1x_de_bink_v5,
            "1x-DeBink-v6" to R.string.model_info_1x_de_bink_v6,

            // miscellaneous
            "1x-Anti-Aliasing-fp16" to R.string.model_info_1x_anti_aliasing,
            "1x-KDM003-scans-fp16" to R.string.model_info_1x_kdm003_scans,
            "1x-SpongeColor-Lite-fp16" to R.string.model_info_1x_sponge_color_lite,
            "1x_Bandage-Smooth-fp16" to R.string.model_info_1x_bandage_smooth,
            "1x_Bendel_Halftone-fp32" to R.string.model_info_1x_bendel_halftone_fp32,
            "1x_ColorizerV2_22000G-fp16" to R.string.model_info_1x_colorizer_v2_22000g,
            "1x_DeEdge-fp16" to R.string.model_info_1x_de_edge,
            "1x_DeSharpen-fp16" to R.string.model_info_1x_de_sharpen,
            "1x_DitherDeleterV3-Smooth-fp16" to R.string.model_info_1x_dither_deleter_v3_smooth,
            "1x_GainresV4-fp16" to R.string.model_info_1x_gainres_v4,
            "1x-Debandurh-FS-Ultra-lite-fp16" to R.string.model_info_1x_debandurh_fs_ultra_lite,
            "1x_NMKD-BrightenRedux_200k-fp16" to R.string.model_info_1x_nmkd_brighten_redux_200k,
            "1x_NMKDDetoon_97500_G-fp16" to R.string.model_info_1x_nmkd_detoon_97500_g,
            "1x_NoiseToner-Poisson-Detailed_108000_G-fp16" to R.string.model_info_1x_noise_toner_poisson_detailed_108000_g,
            "1x_NoiseToner-Poisson-Soft_101000_G-fp16" to R.string.model_info_1x_noise_toner_poisson_soft_101000_g,
            "1x_NoiseToner-Uniform-Detailed_100000_G-fp16" to R.string.model_info_1x_noise_toner_uniform_detailed_100000_g,
            "1x_NoiseToner-Uniform-Soft_100000_G-fp16" to R.string.model_info_1x_noise_toner_uniform_soft_100000_g,
            "1x_ReDetail_v2_126000_G-fp16" to R.string.model_info_1x_re_detail_v2_126000_g,
            "1x_Repainter_20000_G-fp16" to R.string.model_info_1x_repainter_20000_g,
            "1x_artifacts_dithering_alsa-fp16" to R.string.model_info_1x_artifacts_dithering_alsa,
            "1x_nmkdbrighten_10000_G-fp16" to R.string.model_info_1x_nmkd_brighten_10000_g,

            // "special", like me
            "rmbg" to R.string.model_info_background_removal_bria_rmbg,
            "u2net" to R.string.model_info_background_removal_u2net
        )

        private val MIN_SPATIAL_SIZE_BY_NAME = mapOf(
            "nafnet" to 512
        )

        private val MIN_OVERLAP_SIZE_BY_NAME = mapOf(
            "scunet" to 128
        )

        private val FIXED_INPUT_SIZE_BY_NAME = mapOf(
            "rmbg" to Pair(1024, 1024), "u2net" to Pair(320, 320)
        )

        private val FORCE_GRAYSCALE_BY_NAME = setOf(
            "1xBook-Compact-fp16"
        )

        fun gpuCacheToken(modelName: String): String =
            modelName.replace("[^a-zA-Z0-9_-]".toRegex(), "_").trimEnd('_')

        fun gpuCacheDir(context: Context): File = File(context.cacheDir, GPU_DELEGATE_CACHE_DIR)

        fun gpuCacheFiles(context: Context, modelName: String): List<File> {
            val dir = gpuCacheDir(context)
            if (!dir.exists()) return emptyList()
            val prefix = gpuCacheToken(modelName)
            return dir.listFiles { f -> f.name.startsWith(prefix) }?.toList() ?: emptyList()
        }

        fun gpuCacheExists(context: Context, modelName: String): Boolean =
            gpuCacheFiles(context, modelName).isNotEmpty()

        fun create(context: Context): ModelManager {
            return if (BuildConfig.LITERT_ENABLED) {
                Class.forName("com.je.dejpeg.utils.LiteRtModelManager")
                    .getDeclaredConstructor(Context::class.java)
                    .newInstance(context) as ModelManager
            } else {
                ModelManager(context)
            }
        }
    }

    fun getFixedInputSize(modelName: String?): Pair<Int, Int>? {
        val normalized = modelName?.lowercase() ?: return null
        return FIXED_INPUT_SIZE_BY_NAME.entries.firstOrNull { normalized.contains(it.key) }?.value
    }

    fun getMinSpatialSize(modelName: String?): Int {
        val normalized = modelName?.lowercase() ?: return 256
        for ((pattern, size) in MIN_SPATIAL_SIZE_BY_NAME) {
            if (normalized.contains(pattern)) return size
        }
        return 256
    }

    fun getMinOverlapSize(modelName: String?): Int {
        val normalized = modelName?.lowercase() ?: return 0
        for ((pattern, size) in MIN_OVERLAP_SIZE_BY_NAME) {
            if (normalized.contains(pattern)) return size
        }
        return 0
    }

    fun forcesGrayscale(modelName: String?): Boolean {
        val normalized = modelName?.lowercase() ?: return false
        return FORCE_GRAYSCALE_BY_NAME.any { normalized.contains(it.lowercase()) }
    }

    fun hasActiveModel(type: ModelType = ModelType.ONNX): Boolean {
        val activeModel = getActiveModelName(type)
        return activeModel != null && File(getModelsDir(type), activeModel).exists()
    }

    fun getActiveModelName(type: ModelType = ModelType.ONNX): String? {
        cachedActiveModels[type]?.let { return it }
        val name = appPreferences.loadActiveModel()
        return name?.also {
            val detectedType = ModelType.fromFilename(name) ?: type
            cachedActiveModels[detectedType] = name
        }
    }

    fun getCurrentModelName(type: ModelType = ModelType.ONNX): String? = cachedActiveModels[type]

    fun getActiveModelName(): String? = cachedActiveModels.values.firstOrNull { it != null }

    fun setActiveModel(modelName: String) {
        val modelType = ModelType.fromFilename(modelName)
        if (modelType == null) {
            Log.w(
                "ModelManager",
                "setActiveModel called with filename that doesn't match any known type: $modelName"
            )
            return
        }
        Log.d("ModelManager", "setActiveModel($modelType) called with: $modelName")
        cachedActiveModels[modelType] = modelName
        appPreferences.saveActiveModel(modelName)
        Log.d("ModelManager", "Active $modelType model saved to SharedPreferences: $modelName")
    }

    private fun clearActiveModel() {
        cachedActiveModels.clear()
        appPreferences.clearActiveModel()
    }

    protected fun setCurrentProcessingModel(modelName: String) {
        appPreferences.saveCurrentProcessingModel(modelName)
    }

    fun getInstalledModels(type: ModelType = ModelType.ONNX): List<String> {
        val modelsDir = getModelsDir(type)
        if (!modelsDir.exists()) return emptyList()
        return modelsDir.listFiles { _, name -> type.matches(name) }?.map { it.name } ?: emptyList()
    }

    fun getActiveModelPath(type: ModelType = ModelType.OIDN): String? {
        val modelName = getActiveModelName(type) ?: return null
        val modelFile = File(getModelsDir(type), modelName)
        return if (modelFile.exists()) modelFile.absolutePath else null
    }

    fun loadModel(): OrtSession {
        val activeModel =
            getActiveModelName(ModelType.ONNX) ?: throw Exception("No active model set")
        if (currentSession != null && activeModel == cachedActiveModels[ModelType.ONNX]) {
            return currentSession!!
        }
        unloadModel()
        System.runFinalization()
        System.gc()
        val modelFile = File(getModelsDir(ModelType.ONNX), activeModel)
        if (!modelFile.exists()) throw Exception("Model file does not exist: ${modelFile.absolutePath}")
        try {
            ortEnv = OrtEnvironment.getEnvironment(OrtLoggingLevel.ORT_LOGGING_LEVEL_VERBOSE, "ort")
            val opts = OrtSession.SessionOptions()
            configureSessionOptions(opts, activeModel)
            currentSession = ortEnv?.createSession(modelFile.absolutePath, opts)
            cachedActiveModels[ModelType.ONNX] = activeModel
            setCurrentProcessingModel(activeModel)
            Log.d("ModelManager", "Successfully loaded ONNX model: $activeModel")
            return currentSession!!
        } catch (e: Exception) {
            Log.e("ModelManager", "Error loading ONNX model: ${e.message}", e)
            currentSession = null
            throw e
        }
    }

    private fun configureSessionOptions(opts: OrtSession.SessionOptions, modelName: String) {
        val processors = Runtime.getRuntime().availableProcessors()
        try {
            opts.setIntraOpNumThreads(if (processors <= 2) 1 else 2)
        } catch (e: OrtException) {
            Log.e("ModelManager", "Error setting IntraOpNumThreads: ${e.message}")
        }
        try {
            opts.setInterOpNumThreads(if (processors <= 2) 1 else 2)
        } catch (e: OrtException) {
            Log.e("ModelManager", "Error setting InterOpNumThreads: ${e.message}")
        }
        try {
            when {
                modelName.endsWith(".ort") -> opts.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.NO_OPT)
                modelName.startsWith("fbcnn_") -> opts.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
                modelName.startsWith("scunet_") -> opts.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.NO_OPT)
                modelName.contains("rmbg") -> opts.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.NO_OPT)
                else -> opts.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
            }
        } catch (e: OrtException) {
            Log.e("ModelManager", "Error setting OptimizationLevel: ${e.message}")
        }
    }

    fun unloadModel() {
        val currentModel = cachedActiveModels[ModelType.ONNX]
        Log.d("ModelManager", "unloadModel (ONNX) called for: $currentModel")
        val session = currentSession
        val env = ortEnv
        currentSession = null
        ortEnv = null
        try {
            session?.close()
        } catch (e: Exception) {
            Log.e("ModelManager", "Error closing session: ${e.message}")
        }
        try {
            env?.close()
        } catch (e: Exception) {
            Log.e("ModelManager", "Error closing environment: ${e.message}")
        }
        System.gc(); System.runFinalization(); System.gc()
    }

    open fun loadLiteRtModel(modelName: String? = null, useGpu: Boolean = true): Any =
        throw UnsupportedOperationException("LiteRT not available in this build")

    open fun unloadLiteRtModel() {
        // no-op in ONNX-only builds
    }

    open fun deleteGpuCache(modelName: String, type: ModelType = ModelType.LITERT): Boolean = false

    fun importModel(
        modelUri: Uri,
        onProgress: (Int) -> Unit = {},
        onSuccess: (String, ModelType) -> Unit = { _, _ -> },
        onError: (String) -> Unit = {}
    ) {
        try {
            val filename = resolveFilename(modelUri)
            val type = when {
                ModelType.OIDN.matches(filename) -> {
                    ModelType.OIDN
                }

                ModelType.LITERT.matches(filename) -> ModelType.LITERT
                ModelType.ONNX.matches(filename) -> ModelType.ONNX
                else -> {
                    onError(invalidFileTypeMessage())
                    return
                }
            }
            importModelInternal(
                modelUri,
                filename,
                type,
                onProgress,
                onSuccess = { name -> onSuccess(name, type) },
                onError
            )
        } catch (e: Exception) {
            onError(e.message ?: context.getString(R.string.unknown_error))
        }
    }

    private fun invalidFileTypeMessage(): String {
        val supportedTypes =
            ModelType.entries.filter { it.enabled }.flatMap { it.extensions }.joinToString(", ")
        return context.getString(R.string.invalid_file_type, supportedTypes)
    }

    private fun resolveFilename(uri: Uri): String {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) return cursor.getString(idx).trim()
            }
        }
        val path = uri.path
        if (path != null && path.contains("/")) return path.substring(path.lastIndexOf('/') + 1)
            .trim()
        return uri.lastPathSegment?.trim() ?: "model.onnx"
    }

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
            if (modelFile.exists()) {
                onError(context.getString(R.string.model_already_imported, filename))
                return
            }
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

    fun importModels(
        modelUris: List<Uri>,
        onProgress: (Int) -> Unit = {},
        onSuccess: (String, ModelType) -> Unit = { _, _ -> },
        onError: (String) -> Unit = {}
    ) {
        val total = modelUris.size
        var index = 0
        for (uri in modelUris) {
            index++
            importModel(
                modelUri = uri, onProgress = { p ->
                    val adjusted = ((index - 1) * 100 + p) / total
                    onProgress(adjusted)
                }, onSuccess = { name, type -> onSuccess(name, type) }, onError = onError
            )
        }
    }

    fun deleteModel(
        modelName: String, type: ModelType = ModelType.ONNX, onDeleted: (String) -> Unit = {}
    ) {
        val modelFile = File(getModelsDir(type), modelName)
        if (modelFile.exists()) {
            modelFile.delete()
            if (type == ModelType.LITERT) {
                deleteGpuCache(modelName, type)
                val cacheDir = gpuCacheDir(context)
                if (cacheDir.exists()) {
                    val modelsDir = getModelsDir(ModelType.LITERT)
                    val installedTokens = if (modelsDir.exists()) {
                        modelsDir.listFiles { f -> ModelType.LITERT.matches(f.name) }
                            ?.map { gpuCacheToken(it.name) }.orEmpty().toSet()
                    } else emptySet()
                    var swept = 0
                    cacheDir.listFiles()?.forEach { file ->
                        if (installedTokens.none { token -> file.name.startsWith(token) }) {
                            file.delete(); swept++
                        }
                    }
                    if (swept > 0) Log.d("ModelManager", "Swept $swept abandoned GPU cache file(s)")
                }
            }
            onDeleted(modelName)
        }
        if (modelName == getActiveModelName(type)) {
            val remaining = getInstalledModels(type)
            if (remaining.isNotEmpty()) setActiveModel(remaining.first())
            else clearActiveModel()
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

    fun initializeStarterModel(): List<String> {
        return try {
            if (appPreferences.loadStarterModelExtracted()) {
                Log.d("ModelManager", "initializeStarterModel: already extracted, skipping")
                return emptyList()
            }
            val modelsDir = getModelsDir(ModelType.ONNX)
            Log.d(
                "ModelManager",
                "initializeStarterModel: modelsDir=$modelsDir, exists=${modelsDir.exists()}"
            )
            val hasModels =
                modelsDir.exists() && modelsDir.listFiles { _, name -> ModelType.ONNX.matches(name) }
                    ?.isNotEmpty() == true
            if (hasModels) {
                Log.d(
                    "ModelManager",
                    "initializeStarterModel: ONNX models already exist on disk, marking extracted"
                )
                appPreferences.saveStarterModelExtracted(true)
                return emptyList()
            }
            Log.d(
                "ModelManager", "initializeStarterModel: no ONNX models found, extracting starter"
            )
            val result = extractStarterModel()
            Log.d("ModelManager", "initializeStarterModel: extractStarterModel returned $result")
            result
        } catch (e: Exception) {
            Log.e("ModelManager", "Error initializing starter model: ${e.message}", e)
            emptyList()
        }
    }

    fun extractStarterModel(onError: (String) -> Unit = {}): List<String> {
        return try {
            val modelsDir = getModelsDir(ModelType.ONNX)
            Log.d("ModelManager", "extractStarterModel: targetDir=$modelsDir")
            val extracted = copyStarterModelsFromAssets(modelsDir)
            Log.d("ModelManager", "extractStarterModel: copied ${extracted.size} file(s)")
            if (extracted.isEmpty()) {
                onError(context.getString(R.string.failed_to_extract_starter_models))
                return emptyList()
            }
            appPreferences.saveStarterModelExtracted(true)
            extracted
        } catch (e: Exception) {
            Log.e("ModelManager", "Error extracting starter models: ${e.message}", e)
            onError(e.message ?: context.getString(R.string.unknown_error))
            emptyList()
        }
    }

    private fun copyStarterModelsFromAssets(targetDir: File): List<String> {
        return try {
            if (!targetDir.exists()) targetDir.mkdirs()
            val assetFiles = context.assets.list(STARTER_MODELS_ASSET_DIR) ?: emptyArray()
            Log.d(
                "ModelManager",
                "copyStarterModelsFromAssets: assetFiles=${assetFiles.size}, names=${assetFiles.contentToString()}"
            )
            if (assetFiles.isEmpty()) {
                Log.w(
                    "ModelManager",
                    "No starter model files found in assets/$STARTER_MODELS_ASSET_DIR"
                )
                return emptyList()
            }
            for (filename in assetFiles) {
                val outFile = File(targetDir, filename)
                context.assets.open("$STARTER_MODELS_ASSET_DIR/$filename").use { input ->
                    FileOutputStream(outFile).use { output -> input.copyTo(output) }
                }
                Log.d("ModelManager", "Copied starter model: $filename")
            }
            Log.d("ModelManager", "Successfully copied ${assetFiles.size} starter model(s)")
            assetFiles.toList()
        } catch (e: Exception) {
            Log.e("ModelManager", "Error copying starter models from assets: ${e.message}", e)
            emptyList()
        }
    }
}
