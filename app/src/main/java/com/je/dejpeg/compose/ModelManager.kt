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

package com.je.dejpeg.compose

import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtException
import ai.onnxruntime.OrtSession
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import com.je.dejpeg.R
import com.je.dejpeg.compose.utils.HashUtils
import com.je.dejpeg.compose.utils.ZipExtractor
import com.je.dejpeg.compose.utils.helpers.ModelMigrationHelper
import com.je.dejpeg.data.AppPreferences
import com.je.dejpeg.data.ProcessingMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.File
import java.io.FileOutputStream

class ModelManager(
    private val context: Context,
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) {
    private var currentSession: OrtSession? = null
    private var ortEnv: OrtEnvironment? = null
    private var currentModelName: String? = null
    private var cachedActiveModel: String? = null
    private val appPreferences = AppPreferences(context)
    
    private fun getModelsDir(): File = ModelMigrationHelper.getOnnxModelsDir(context)

    companion object {
        private val MODEL_HASHES = mapOf(
            // older fp32 models (legacy, download links removed)
            "fbcnn_color.onnx" to "3bb0ff3060c217d3b3af95615157fca8a65506455cf4e3d88479e09efffec97f",
            "fbcnn_gray.onnx" to "041b360fc681ae4b134e7ec98da1ae4c7ea57435e5abe701530d5a995a7a27b3",
            "fbcnn_gray_double.onnx" to "83aca9febba0da828dbb5cc6e23e328f60f5ad07fa3de617ab1030f0a24d4f67",
            "scunet_color_real_gan.onnx" to "5eb9a8015cf24477980d3a4eec2e35107c470703b98d257f7560cd3cf3f02922",
            "scunet_color_real_psnr.onnx" to "341eb061ed4d7834dbe6cdab3fb509c887f82aa29be8819c7d09b3d9bfa4892d",
            "scunet_gray_15.onnx" to "10d33552b5754ab9df018cb119e20e1f2b18546eff8e28954529a51e5a6ae255",
            "scunet_gray_25.onnx" to "01b5838a85822ae21880062106a80078f06e7a82aa2ffc8847e32f4462b4c928",
            "scunet_gray_50.onnx" to "a8d9cbbbb2696ac116a87a5055496291939ed873fe28d7f560373675bb970833",

            // fp16 models (current)
            "fbcnn_color_fp16.onnx" to "1a678ff4f721b557fd8a7e560b99cb94ba92f201545c7181c703e7808b93e922",
            "fbcnn_gray_fp16.onnx" to "e220b9637a9f2c34a36c98b275b2c9d2b9c2c029e365be82111072376afbec54",
            "fbcnn_gray_double_fp16.onnx" to "17feadd8970772f5ff85596cb9fb152ae3c2b82bca4deb52a7c8b3ecb2f7ac14",
            "scunet_color_real_gan_fp16.onnx" to "50411164ee869605161be9cafd674c241cf0c104f5ee6b73e7c3ea69d69f94bd",
            "scunet_color_real_psnr_fp16.onnx" to "8923b09e240e0078b3247964e9b105cbfbb4da01e260b29a961d038f8fa7791a",
            "scunet_color_15_fp16.onnx" to "25a3a07de278867df9d29e9d08fe555523bb0f9f78f8956c4af943a4eeb8c934",
            "scunet_color_25_fp16.onnx" to "34d25ec2187d24f9f25b9dc9d918e94e87217c129471adda8c9fdf2e5a1cb62a",
            "scunet_color_50_fp16.onnx" to "1c6bdc6d9e0c1dea314cf22d41c261d4c744bf0ae1ae6c59b9505c4b4d50febb",
            "scunet_gray_15_fp16.onnx" to "8e8740cea4306c9a61215194f315e5c0dc9e06c726a9ddea77d978d804da7663",
            "scunet_gray_25_fp16.onnx" to "dec631fbdca7705bbff1fc779cf85a657dcb67f55359c368464dd6e734e1f2b7",
            "scunet_gray_50_fp16.onnx" to "48b7d07229a03d98b892d2b33aa4c572ea955301772e7fcb5fd10723552a1874",
        )

        private val F32_LEGACY_MODELS = listOf(
            "fbcnn_color.onnx", "fbcnn_gray.onnx", "fbcnn_gray_double.onnx",
            "scunet_color_real_gan.onnx", "scunet_color_real_psnr.onnx",
            "scunet_gray_15.onnx", "scunet_gray_25.onnx", "scunet_gray_50.onnx"
        )

        private val MODEL_WARNINGS = buildMap {
            F32_LEGACY_MODELS.forEach { modelName ->
                put(modelName, ModelWarning(
                    R.string.model_warning_outdated_title,
                    R.string.model_warning_outdated_message,
                    R.string.use_anyway,
                    R.string.cancel
                ))
            }
        }

        private val MODEL_INFO_RES_IDS = mapOf(
            // starter models
            "1x-RGB-max-Denoise-fp16.onnx" to R.string.model_info_1x_rgb_max_denoise_fp16,
            "1x-span-anime-pretrain-fp16.onnx" to R.string.model_info_1x_span_anime_pretrain_fp16,

            // fbcnn (jpeg model)
            "fbcnn_color_fp16.onnx" to R.string.model_info_fbcnn_color_fp16,
            "fbcnn_gray_fp16.onnx" to R.string.model_info_fbcnn_gray_fp16,
            "fbcnn_gray_double_fp16.onnx" to R.string.model_info_fbcnn_gray_double_fp16,

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

            // small models (for low-end devices)
            "1x-AnimeUndeint-Compact-fp16.onnx" to R.string.model_info_1x_anime_undeint_compact_fp16,
            "1x-BroadcastToStudio_Compact-fp16.onnx" to R.string.model_info_1x_broadcast_to_studio_compact_fp16,
            "1x-WB-Denoise-fp16.onnx" to R.string.model_info_1x_wb_denoise_fp16,
            "1xBook-Compact-fp16.onnx" to R.string.model_info_1x_book_compact_fp16,
            "1xOverExposureCorrection_compact-fp16.onnx" to R.string.model_info_1x_over_exposure_correction_compact_fp16,

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
            "1x_artifacts_jpg_60_800_alsa-fp16.onnx" to R.string.model_info_1x_artifacts_jpg_60_800_alsa_fp16,
            "1x_artifacts_jpg_80_100_alsa-fp16.onnx" to R.string.model_info_1x_artifacts_jpg_80_100_alsa_fp16,

            // miscellaneous models
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
        )

        private val MIN_SPATIAL_SIZE_BY_NAME = mapOf(
            "nafnet" to 512 // nafnet deblurring, set to 512
        )

        private val MIN_OVERLAP_SIZE_BY_NAME = mapOf(
            "scunet" to 128 // scunet models need large
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

    fun hasActiveModel(): Boolean {
        val activeModel = getActiveModelName()
        return activeModel != null && File(getModelsDir(), activeModel).exists()
    }

    fun getActiveModelName(): String? {
        cachedActiveModel?.let { return it }
        return runBlocking {
            appPreferences.getActiveModel().also { cachedActiveModel = it }
        }
    }

    fun setActiveModel(modelName: String) {
        Log.d("ModelManager", "setActiveModel called with: $modelName")
        unloadModel()
        cachedActiveModel = modelName
        coroutineScope.launch {
            appPreferences.setActiveModel(modelName)
            Log.d("ModelManager", "Active model saved to DataStore: $modelName")
        }
        Log.d("ModelManager", "Active model set to: $modelName")
    }

    private fun clearActiveModel() {
        cachedActiveModel = null
        coroutineScope.launch {
            appPreferences.clearActiveModel()
        }
    }

    private fun setCurrentProcessingModel(modelName: String) {
        coroutineScope.launch {
            appPreferences.setCurrentProcessingModel(modelName)
        }
    }

    fun getInstalledModels(): List<String> {
        val modelsDir = getModelsDir()
        if (!modelsDir.exists()) return emptyList()
        val files = modelsDir.listFiles { _, name -> 
            val lower = name.lowercase()
            lower.endsWith(".onnx") || lower.endsWith(".ort")
        }
        return files?.map { it.name } ?: emptyList()
    }

    fun loadModel(): OrtSession {
        val activeModel = getActiveModelName()
        Log.d("ModelManager", "loadModel called, activeModel: $activeModel, currentModelName: $currentModelName")
        if (activeModel == null) {
            Log.e("ModelManager", "No active model set")
            throw Exception("No active model set")
        }
        if (currentSession != null && activeModel == currentModelName) {
            Log.d("ModelManager", "Returning cached session for: $activeModel")
            return currentSession!!
        }
        Log.d("ModelManager", "Loading new model: $activeModel (previous: $currentModelName)")
        unloadModel()
        val modelFile = File(getModelsDir(), activeModel)
        if (!modelFile.exists()) {
            Log.e("ModelManager", "Model file does not exist: ${modelFile.absolutePath}")
            throw Exception("Model file does not exist: ${modelFile.absolutePath}")
        }
        try {
            if (ortEnv == null) {
                ortEnv = OrtEnvironment.getEnvironment()
            }
            val opts = OrtSession.SessionOptions()
            configureSessionOptions(opts, activeModel)
            currentSession = ortEnv?.createSession(modelFile.absolutePath, opts)
            currentModelName = activeModel
            setCurrentProcessingModel(activeModel)
            Log.d("ModelManager", "Successfully loaded model: $activeModel")
            return currentSession!!
        } catch (e: Exception) {
            Log.e("ModelManager", "Error loading model: ${e.message}", e)
            currentSession = null
            currentModelName = null
            throw e
        }
    }

    private fun configureSessionOptions(opts: OrtSession.SessionOptions, modelName: String) {
        val processors = Runtime.getRuntime().availableProcessors()
        try { opts.setIntraOpNumThreads(if (processors <= 2) 1 else (processors * 3) / 4) } catch (e: OrtException) { Log.e("ModelManager", "Error setting IntraOpNumThreads: ${e.message}") }
        try { opts.setInterOpNumThreads(4) } catch (e: OrtException) { Log.e("ModelManager", "Error setting InterOpNumThreads: ${e.message}") }
        try {
            when {
                modelName.endsWith(".ort") -> { // prevent double optimizations (.ort models are already optimized)
                    opts.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.NO_OPT)
                }
                modelName.startsWith("fbcnn_") -> opts.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.EXTENDED_OPT)
                modelName.startsWith("scunet_") -> opts.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.NO_OPT)
            }
        } catch (e: OrtException) { Log.e("ModelManager", "Error setting OptimizationLevel: ${e.message}") }
    }

    fun unloadModel() {
        Log.d("ModelManager", "unloadModel called, clearing session for: $currentModelName")
        try { 
            currentSession?.close() 
            Log.d("ModelManager", "Session closed successfully")
        } catch (e: Exception) { 
            Log.e("ModelManager", "Error closing session: ${e.message}") 
        }
        currentSession = null
        currentModelName = null
        try { 
            ortEnv?.close() 
            Log.d("ModelManager", "OrtEnvironment closed successfully")
        } catch (e: Exception) { 
            Log.e("ModelManager", "Error closing environment: ${e.message}") 
        }
        ortEnv = null
        System.gc()
    }

    fun importModel(
        modelUri: Uri,
        onProgress: (Int) -> Unit = {},
        onSuccess: (String) -> Unit = {},
        onError: (String) -> Unit = {},
        onWarning: ((String, ModelWarning) -> Unit)? = null,
        force: Boolean = false
    ) {
        try {
            val filename = resolveFilename(modelUri)
            if (!filename.lowercase().let { it.endsWith(".onnx") || it.endsWith(".ort") }) {
                onError(context.getString(R.string.invalid_file_type))
                return
            }
            val actualHash = HashUtils.computeSHA256(modelUri, context)
            val (matchedModel, modelWarning) = findModelByHash(actualHash)
            if (modelWarning != null && !force) {
                onWarning?.invoke(matchedModel ?: "", modelWarning)
                return
            }
            if (matchedModel == null && !force) {
                val unrecognizedWarning = ModelWarning(
                    R.string.model_warning_unrecognized_title,
                    R.string.model_warning_unrecognized_message,
                    R.string.import_anyway,
                    R.string.cancel
                )
                onWarning?.invoke(filename, unrecognizedWarning)
                return
            }
            importModelInternal(modelUri, filename, onProgress, onSuccess, onError)
        } catch (e: Exception) {
            onError(e.message ?: context.getString(R.string.unknown_error))
        }
    }

    private fun findModelByHash(actualHash: String): Pair<String?, ModelWarning?> {
        for ((modelName, expectedHash) in MODEL_HASHES) {
            if (expectedHash.equals(actualHash, ignoreCase = true)) {
                return modelName to MODEL_WARNINGS[modelName]
            }
        }
        return null to null
    }

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
        return uri.lastPathSegment?.trim() ?: "model.onnx"
    }

    private fun importModelInternal(
        uri: Uri,
        filename: String,
        onProgress: (Int) -> Unit,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        try {
            val modelsDir = getModelsDir()
            if (!modelsDir.exists()) {
                modelsDir.mkdirs()
            }
            val modelFile = File(getModelsDir(), filename)
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val size = context.contentResolver.openFileDescriptor(uri, "r")?.use { it.statSize } ?: 0L
                FileOutputStream(modelFile).use { outputStream ->
                    copyWithProgress(inputStream, outputStream, size, onProgress)
                }
            }
        } catch (e: Exception) {
            onError(e.message ?: context.getString(R.string.failed_to_import_model))
        } finally {
            onProgress(100)
            onSuccess(filename)
        }
    }

    private fun copyWithProgress(
        input: java.io.InputStream,
        output: java.io.OutputStream,
        totalSize: Long,
        onProgress: (Int) -> Unit
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

    fun deleteModel(modelName: String, onDeleted: (String) -> Unit = {}) {
        val modelFile = File(getModelsDir(), modelName)
        if (modelFile.exists()) {
            modelFile.delete()
            onDeleted(modelName)
        }
        if (modelName == getActiveModelName()) {
            val remaining = getInstalledModels()
            if (remaining.isNotEmpty()) {
                setActiveModel(remaining.first())
            } else {
                clearActiveModel()
            }
        }
    }

    fun getModelWarning(modelName: String?): ModelWarning? {
        return if (modelName != null) MODEL_WARNINGS[modelName] else null
    }

    fun getModelInfo(modelName: String?): String? {
        if (modelName == null) return null
        val resId = MODEL_INFO_RES_IDS[modelName] ?: return null
        return context.getString(resId)
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
            val modelsDir = getModelsDir()
            val hasModels = modelsDir.exists() && modelsDir.listFiles { _, name -> 
            val lower = name.lowercase()
            lower.endsWith(".onnx") || lower.endsWith(".ort")
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
        setAsActive: Boolean = false,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ): Boolean {
        return try {
            val modelsDir = getModelsDir()
            val shouldSetAsActive = setAsActive || !modelsDir.exists() || modelsDir.listFiles { _, name -> 
            val lower = name.lowercase()
            lower.endsWith(".onnx") || lower.endsWith(".ort")
            }?.isEmpty() == true
            val extracted = ZipExtractor.extractFromAssets(context, "embedonnx.zip", modelsDir)
            if (extracted) {
                if (shouldSetAsActive) {
                    setActiveModel("1x-span-anime-pretrain-fp16.onnx")
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

    // ---- TZA / Odin model management ----

    private fun getTzaModelsDir(): File = ModelMigrationHelper.getTzaModelsDir(context)

    private var cachedActiveOdinModel: String? = null

    fun getInstalledOdinModels(): List<String> {
        val modelsDir = getTzaModelsDir()
        if (!modelsDir.exists()) return emptyList()
        val files = modelsDir.listFiles { _, name ->
            name.lowercase().endsWith(".tza")
        }
        return files?.map { it.name } ?: emptyList()
    }

    fun hasActiveOdinModel(): Boolean {
        val activeModel = getActiveOdinModelName()
        return activeModel != null && File(getTzaModelsDir(), activeModel).exists()
    }

    fun getActiveOdinModelName(): String? {
        cachedActiveOdinModel?.let { return it }
        return runBlocking {
            appPreferences.getActiveOdinModel().also { cachedActiveOdinModel = it }
        }
    }

    fun setActiveOdinModel(modelName: String) {
        Log.d("ModelManager", "setActiveOdinModel called with: $modelName")
        cachedActiveOdinModel = modelName
        coroutineScope.launch {
            appPreferences.setActiveOdinModel(modelName)
            Log.d("ModelManager", "Active Odin model saved to DataStore: $modelName")
        }
    }

    private fun clearActiveOdinModel() {
        cachedActiveOdinModel = null
        coroutineScope.launch {
            appPreferences.clearActiveOdinModel()
        }
    }

    fun getActiveOdinModelPath(): String? {
        val modelName = getActiveOdinModelName() ?: return null
        val modelFile = File(getTzaModelsDir(), modelName)
        return if (modelFile.exists()) modelFile.absolutePath else null
    }

    fun isIncompatibleTzaModel(filename: String): Boolean {
        val lower = filename.lowercase()
        return lower.contains("_alb") || lower.contains("_nrm")
    }

    fun importOdinModel(
        modelUri: Uri,
        force: Boolean = false,
        onProgress: (Int) -> Unit = {},
        onSuccess: (String) -> Unit = {},
        onError: (String) -> Unit = {},
        onWarning: ((String, ModelWarning) -> Unit)? = null
    ) {
        try {
            val filename = resolveFilename(modelUri)
            if (!filename.lowercase().endsWith(".tza")) {
                onError(context.getString(R.string.invalid_tza_file_type))
                return
            }
            if (!force && isIncompatibleTzaModel(filename)) {
                val warning = ModelWarning(
                    titleResId = R.string.odin_incompatible_model_title,
                    messageResId = R.string.odin_incompatible_model_message,
                    positiveButtonTextResId = R.string.import_anyway,
                    negativeButtonTextResId = R.string.cancel
                )
                onWarning?.invoke(filename, warning)
                return
            }
            val modelsDir = getTzaModelsDir()
            if (!modelsDir.exists()) modelsDir.mkdirs()
            val modelFile = File(modelsDir, filename)
            context.contentResolver.openInputStream(modelUri)?.use { inputStream ->
                val size = context.contentResolver.openFileDescriptor(modelUri, "r")?.use { it.statSize } ?: 0L
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

    fun deleteOdinModel(modelName: String, onDeleted: (String) -> Unit = {}) {
        val modelFile = File(getTzaModelsDir(), modelName)
        if (modelFile.exists()) {
            modelFile.delete()
            onDeleted(modelName)
        }
        if (modelName == getActiveOdinModelName()) {
            val remaining = getInstalledOdinModels()
            if (remaining.isNotEmpty()) {
                setActiveOdinModel(remaining.first())
            } else {
                clearActiveOdinModel()
            }
        }
    }

    fun getProcessingMode(): ProcessingMode {
        return runBlocking { appPreferences.getProcessingModeImmediate() }
    }

    fun setProcessingMode(mode: ProcessingMode) {
        coroutineScope.launch {
            appPreferences.setProcessingMode(mode)
        }
    }
}
