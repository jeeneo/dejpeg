/*
 * SPDX-FileCopyrightText: 2025 - 2026 dryerlint <https://codeberg.org/dryerlint>
 * SPDX-License-Identifier: GNU Affero General Public License v3.0 or later
 */

@file:Suppress("KotlinConstantConditions", "SpellCheckingInspection")

package com.je.dejpeg.utils

import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtException
import ai.onnxruntime.OrtLoggingLevel
import ai.onnxruntime.OrtSession
import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import com.je.dejpeg.AppPreferences
import com.je.dejpeg.BuildConfig
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

enum class ModelType(val extensions: List<String>) {
    ONNX(listOf(".onnx", ".ort")), OIDN(listOf(".tza")), LITERT(listOf(".tflite"));

    fun matches(filename: String): Boolean {
        val lower = filename.lowercase()
        return extensions.any { lower.endsWith(it) }
    }

    companion object {
        fun fromFilename(filename: String): ModelType? = entries.find { it.matches(filename) }
        fun fromString(value: String?): ModelType = entries.find { it.name == value } ?: ONNX
    }
}

class ModelManager(
    private val context: Context,
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) {
    private var currentSession: OrtSession? = null
    private var ortEnv: OrtEnvironment? = null

    private var currentInterpreter: Interpreter? = null
    private var gpuDelegate: GpuDelegate? = null

    private val cachedActiveModels = mutableMapOf<ModelType, String?>()
    private val appPreferences = AppPreferences()

    private fun getModelsDir(type: ModelType = ModelType.ONNX): File = when (type) {
        ModelType.ONNX -> ModelMigrationHelper.getOnnxModelsDir(context)
        ModelType.OIDN -> ModelMigrationHelper.getTzaModelsDir(context)
        ModelType.LITERT -> ModelMigrationHelper.getLiteRtModelsDir(context)
    }

    companion object {
        private const val STARTER_MODELS_ASSET_DIR = "embedonnx"
        private const val GPU_DELEGATE_CACHE_DIR = "gpu_delegate_cache"

        private val MODEL_INFO_RES_IDS = mapOf(
            // fbcnn (jpeg model)
            "fbcnn_color_fp16.onnx" to R.string.model_info_fbcnn_color_fp16,
            "fbcnn_gray_fp16.onnx" to R.string.model_info_fbcnn_gray_fp16,
            "fbcnn_gray_double_fp16.onnx" to R.string.model_info_fbcnn_gray_double_fp16,

            // fbcnn litert variant
            "fbcnn_color_fp16.tflite" to R.string.model_info_fbcnn_color_fp16,

            // scunet (noise model)
            "scunet_color_real_gan_fp16.onnx" to R.string.model_info_scunet_color_real_gan_fp16,
            "scunet_color_real_psnr_fp16.onnx" to R.string.model_info_scunet_color_real_psnr_fp16,
            "scunet_color_15_fp16.onnx" to R.string.model_info_scunet_color_15_fp16,
            "scunet_color_25_fp16.onnx" to R.string.model_info_scunet_color_25_fp16,
            "scunet_color_50_fp16.onnx" to R.string.model_info_scunet_color_50_fp16,
            "scunet_gray_15_fp16.onnx" to R.string.model_info_scunet_gray_15_fp16,
            "scunet_gray_25_fp16.onnx" to R.string.model_info_scunet_gray_25_fp16,
            "scunet_gray_50_fp16.onnx" to R.string.model_info_scunet_gray_50_fp16,

            "deblurring_nafnet_2025may.onnx" to R.string.model_info_deblurring_nafnet_2025may,

            // small models
            "1x-AnimeUndeint-Compact-fp16.onnx" to R.string.model_info_1x_anime_undeint_compact_fp16,
            "1x-BroadcastToStudio_Compact-fp16.onnx" to R.string.model_info_1x_broadcast_to_studio_compact_fp16,
            "1x-WB-Denoise-fp16.onnx" to R.string.model_info_1x_wb_denoise_fp16,
            "1xBook-Compact-fp16.onnx" to R.string.model_info_1x_book_compact_fp16,
            "1xOverExposureCorrection_compact-fp16.onnx" to R.string.model_info_1x_over_exposure_correction_compact_fp16,
            "1x-RGB-max-Denoise-fp16.onnx" to R.string.model_info_1x_rgb_max_denoise_fp16,
            "1x-span-anime-pretrain-fp16.onnx" to R.string.model_info_1x_span_anime_pretrain_fp16,

            // other compression
            "1x_JPEGDestroyerV2_96000G-fp16.onnx" to R.string.model_info_1x_jpeg_destroyer_v2_96000g_fp16,
            "1x-NMKD-Jaywreck3-Lite-fp16.onnx" to R.string.model_info_1x_nmkd_jaywreck3_lite_fp16,
            "1x_NMKD-h264Texturize-fp16.onnx" to R.string.model_info_1x_nmkd_h264_texturize_fp16,
            "VHS-Sharpen-1x_46000_G-fp16.onnx" to R.string.model_info_vhs_sharpen_1x_46000_g_fp16,
            "1x_BCGone_Smooth_110000_G-fp16.onnx" to R.string.model_info_1x_bc_gone_smooth_110000_g_fp16,
            "1x-cinepak-fp16.onnx" to R.string.model_info_1x_cinepak_fp16,
            "1x_BCGone-DetailedV2_40-60_115000_G-fp16.onnx" to R.string.model_info_1x_bc_gone_detailed_v2_40_60_115000_g_fp16,
            "1x-DeBink-v4.onnx" to R.string.model_info_1x_de_bink_v4,
            "1x-DeBink-v5.onnx" to R.string.model_info_1x_de_bink_v5,
            "1x-DeBink-v6.onnx" to R.string.model_info_1x_de_bink_v6,

            // JPEG quality range models
            "1x_JPEG_00-20-fp16.ort" to R.string.model_info_1x_jpeg_00_20_fp16,
            "1x_JPEG_20-40-fp16.ort" to R.string.model_info_1x_jpeg_20_40_fp16,
            "1x_JPEG_40-60-fp16.ort" to R.string.model_info_1x_jpeg_40_60_fp16,
            "1x_JPEG_60-80-fp16.ort" to R.string.model_info_1x_jpeg_60_80_fp16,
            "1x_JPEG_80-100-fp16.ort" to R.string.model_info_1x_jpeg_80_100_fp16,
            "1x_artifacts_jpg_00_20_alsa-fp16.onnx" to R.string.model_info_1x_artifacts_jpg_00_20_alsa_fp16,
            "1x_artifacts_jpg_20_40_alsa-fp16.onnx" to R.string.model_info_1x_artifacts_jpg_20_40_alsa_fp16,
            "1x_artifacts_jpg_40_60_alsa-fp16.onnx" to R.string.model_info_1x_artifacts_jpg_40_60_alsa_fp16,
            "1x_artifacts_jpg_60_80_alsa-fp16.onnx" to R.string.model_info_1x_artifacts_jpg_60_80_alsa_fp16,
            "1x_artifacts_jpg_80_100_alsa-fp16.onnx" to R.string.model_info_1x_artifacts_jpg_80_100_alsa_fp16,

            // miscellaneous
            "1x-Anti-Aliasing-fp16.onnx" to R.string.model_info_1x_anti_aliasing_fp16,
            "1x-KDM003-scans-fp16.onnx" to R.string.model_info_1x_kdm003_scans_fp16,
            "1x-SpongeColor-Lite-fp16.onnx" to R.string.model_info_1x_sponge_color_lite_fp16,
            "1x_Bandage-Smooth-fp16.onnx" to R.string.model_info_1x_bandage_smooth_fp16,
            "1x_Bendel_Halftone-fp32.onnx" to R.string.model_info_1x_bendel_halftone_fp32,
            "1x_ColorizerV2_22000G-fp16.onnx" to R.string.model_info_1x_colorizer_v2_22000g_fp16,
            "1x_DeEdge-fp16.onnx" to R.string.model_info_1x_de_edge_fp16,
            "1x_DeSharpen-fp16.onnx" to R.string.model_info_1x_de_sharpen_fp16,
            "1x_DitherDeleterV3-Smooth-fp16.onnx" to R.string.model_info_1x_dither_deleter_v3_smooth_fp16,
            "1x_GainresV4-fp16.onnx" to R.string.model_info_1x_gainres_v4_fp16,
            "1x-Debandurh-FS-Ultra-lite-fp16.onnx" to R.string.model_info_1x_debandurh_fs_ultra_lite_fp16,
            "1x_NMKD-BrightenRedux_200k-fp16.onnx" to R.string.model_info_1x_nmkd_brighten_redux_200k_fp16,
            "1x_NMKDDetoon_97500_G-fp16.onnx" to R.string.model_info_1x_nmkd_detoon_97500_g_fp16,
            "1x_NoiseToner-Poisson-Detailed_108000_G-fp16.onnx" to R.string.model_info_1x_noise_toner_poisson_detailed_108000_g_fp16,
            "1x_NoiseToner-Poisson-Soft_101000_G-fp16.onnx" to R.string.model_info_1x_noise_toner_poisson_soft_101000_g_fp16,
            "1x_NoiseToner-Uniform-Detailed_100000_G-fp16.onnx" to R.string.model_info_1x_noise_toner_uniform_detailed_100000_g_fp16,
            "1x_NoiseToner-Uniform-Soft_100000_G-fp16.onnx" to R.string.model_info_1x_noise_toner_uniform_soft_100000_g_fp16,
            "1x_ReDetail_v2_126000_G-fp16.onnx" to R.string.model_info_1x_re_detail_v2_126000_g_fp16,
            "1x_Repainter_20000_G-fp16.onnx" to R.string.model_info_1x_repainter_20000_g_fp16,
            "1x_artifacts_dithering_alsa-fp16.onnx" to R.string.model_info_1x_artifacts_dithering_alsa_fp16,
            "1x_nmkdbrighten_10000_G-fp16.onnx" to R.string.model_info_1x_nmkd_brighten_10000_g_fp16,

            // special
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

        fun gpuCacheToken(modelName: String): String =
            modelName.replace("[^a-zA-Z0-9_-]".toRegex(), "_").trimEnd('_')

        fun gpuCacheDir(context: Context): File = File(context.cacheDir, GPU_DELEGATE_CACHE_DIR)

        fun gpuCacheFiles(context: Context, modelName: String): List<File> {
            val dir = gpuCacheDir(context)
            if (!dir.exists()) return emptyList()
            val prefix = gpuCacheToken(modelName)
            return dir.listFiles { f -> f.name.startsWith(prefix) }?.toList() ?: emptyList()
        }

//        fun gpuCacheExists(context: Context, modelName: String): Boolean =
//            gpuCacheFiles(context, modelName).isNotEmpty()
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

    fun hasActiveModel(type: ModelType = ModelType.ONNX): Boolean {
        val activeModel = getActiveModelName(type)
        return activeModel != null && File(getModelsDir(type), activeModel).exists()
    }

    fun getActiveModelName(type: ModelType = ModelType.ONNX): String? {
        cachedActiveModels[type]?.let { return it }
        return runBlocking {
            val name = appPreferences.getActiveModel()
            name?.also {
                val detectedType = ModelType.fromFilename(name) ?: type
                cachedActiveModels[detectedType] = name
            }
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
        when (modelType) {
            ModelType.ONNX -> unloadModel()
            ModelType.LITERT -> unloadLiteRtModel()
            ModelType.OIDN -> {}
        }
        coroutineScope.launch {
            appPreferences.setActiveModel(modelName)
            Log.d("ModelManager", "Active $modelType model saved to DataStore: $modelName")
        }
    }

    private fun clearActiveModel(type: ModelType) {
        cachedActiveModels.remove(type)
        coroutineScope.launch { appPreferences.clearActiveModel() }
    }

    private fun setCurrentProcessingModel(modelName: String) {
        coroutineScope.launch { appPreferences.setCurrentProcessingModel(modelName) }
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

        val oldSession = currentSession
        val oldEnv = ortEnv
        currentSession = null
        ortEnv = null

        try {
            oldSession?.close()
        } catch (e: Exception) {
            Log.e("ModelManager", "Error closing old session: ${e.message}")
        }
        try {
            oldEnv?.close()
        } catch (e: Exception) {
            Log.e("ModelManager", "Error closing old env: ${e.message}")
        }
        System.gc(); System.runFinalization(); System.gc()

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

    fun loadLiteRtModel(modelName: String? = null, useGpu: Boolean = true): Interpreter {
        val modelToLoad = modelName ?: getActiveModelName(ModelType.LITERT)
        ?: throw Exception("No active LiteRT model set")
        if (currentInterpreter != null && modelToLoad == cachedActiveModels[ModelType.LITERT]) {
            return currentInterpreter!!
        }

        val modelFile = File(getModelsDir(ModelType.LITERT), modelToLoad)
        if (!modelFile.exists()) throw Exception("LiteRT model file does not exist: ${modelFile.absolutePath}")

        // Create new interpreter and delegate first, before closing old ones
        val mapped = FileInputStream(modelFile).use { fis ->
            fis.channel.map(FileChannel.MapMode.READ_ONLY, 0, modelFile.length())
        }
        val opts = Interpreter.Options()
        var newDelegate: GpuDelegate? = null

        try {
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
                    newDelegate = GpuDelegate(delegateOptions)
                    opts.addDelegate(newDelegate)
                    Log.d("ModelManager", "GPU delegate enabled for $modelToLoad")
                } else {
                    Log.w("ModelManager", "GPU delegate not supported for $modelToLoad, using CPU")
                    opts.numThreads = Runtime.getRuntime().availableProcessors().coerceIn(1, 4)
                }
            } else {
                opts.numThreads = Runtime.getRuntime().availableProcessors().coerceIn(1, 4)
            }

            // Create new interpreter with the new delegate
            val newInterpreter = Interpreter(mapped, opts)

            // Only now close the old resources - new ones are confirmed valid
            val oldInterpreter = currentInterpreter
            val oldDelegate = gpuDelegate
            currentInterpreter = newInterpreter
            gpuDelegate = newDelegate

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

            System.runFinalization()
            System.gc()

            cachedActiveModels[ModelType.LITERT] = modelToLoad
            setCurrentProcessingModel(modelToLoad)
            Log.d("ModelManager", "Successfully loaded LiteRT model: $modelToLoad (gpu=$useGpu)")
            return newInterpreter

        } catch (e: Exception) {
            Log.e("ModelManager", "Error loading LiteRT model: ${e.message}", e)
            // Clean up new delegate if creation failed
            newDelegate?.close()
            throw e
        }
    }

    fun unloadLiteRtModel() {
        val currentModel = cachedActiveModels[ModelType.LITERT]
        Log.d("ModelManager", "unloadLiteRtModel called for: $currentModel")
        try {
            currentInterpreter?.close(); currentInterpreter = null
        } catch (e: Exception) {
            Log.e("ModelManager", "Error closing interpreter: ${e.message}")
        }
        try {
            gpuDelegate?.close(); gpuDelegate = null
        } catch (e: Exception) {
            Log.e("ModelManager", "Error closing GPU delegate: ${e.message}")
        }
        System.runFinalization(); System.gc()
    }

//    fun getGpuCacheStatus(
//        modelName: String, type: ModelType = ModelType.LITERT
//    ): GpuCacheStatus {
//        if (type != ModelType.LITERT) return GpuCacheStatus.NOT_AVAILABLE
//        return if (gpuCacheExists(context, modelName)) GpuCacheStatus.READY
//        else GpuCacheStatus.NOT_AVAILABLE
//    }

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

    @Suppress("KotlinConstantConditions")
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
                    if (!BuildConfig.OIDN_ENABLED) {
                        onError(context.getString(R.string.invalid_file_type))
                        return
                    }
                    ModelType.OIDN
                }

                ModelType.LITERT.matches(filename) -> ModelType.LITERT
                ModelType.ONNX.matches(filename) -> ModelType.ONNX
                else -> {
                    onError(context.getString(R.string.invalid_file_type))
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

    @SuppressLint("Recycle")
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
            else clearActiveModel(type)
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

    // ── Starter model (ONNX) ─────────────────────────────────────────────────

    fun initializeStarterModel(): Boolean {
        return try {
            val alreadyExtracted =
                runBlocking { appPreferences.getStarterModelExtractedImmediate() }
            if (alreadyExtracted) {
                Log.d("ModelManager", "Starter model already extracted, skipping")
                return false
            }
            val modelsDir = getModelsDir(ModelType.ONNX)
            val hasModels =
                modelsDir.exists() && modelsDir.listFiles { _, name -> ModelType.ONNX.matches(name) }
                    ?.isNotEmpty() == true
            if (hasModels) {
                Log.d("ModelManager", "Models already exist, skipping starter model extraction")
                markStarterModelExtracted()
                return false
            }
            extractStarterModel(setAsActive = true)
        } catch (e: Exception) {
            Log.e("ModelManager", "Error initializing starter model: ${e.message}", e)
            false
        }
    }

    private fun markStarterModelExtracted() {
        coroutineScope.launch { appPreferences.setStarterModelExtracted(true) }
    }

    fun extractStarterModel(
        setAsActive: Boolean = false, onSuccess: () -> Unit = {}, onError: (String) -> Unit = {}
    ): Boolean {
        return try {
            val modelsDir = getModelsDir(ModelType.ONNX)
            val shouldSetAsActive =
                setAsActive || !modelsDir.exists() || modelsDir.listFiles { _, name ->
                    ModelType.ONNX.matches(name)
                }?.isEmpty() == true
            val extracted = copyStarterModelsFromAssets(modelsDir)
            if (extracted) {
                if (shouldSetAsActive) setActiveModel(
                    "1x-span-anime-pretrain-fp16.onnx"
                )
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
