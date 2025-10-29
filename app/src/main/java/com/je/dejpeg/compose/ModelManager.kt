package com.je.dejpeg

import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtException
import ai.onnxruntime.OrtSession
import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import androidx.core.content.edit
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

class ModelManager(private val context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private var currentSession: OrtSession? = null
    private var ortEnv: OrtEnvironment? = null
    private var currentModelName: String? = null

    companion object {
        private const val PREFS_NAME = "ModelPrefs"
        private const val ACTIVE_MODEL_KEY = "activeModel"

        private val VALID_MODELS = listOf(
            // f32 models (legacy)
            "fbcnn_color.onnx", "fbcnn_gray.onnx", "fbcnn_gray_double.onnx",
            "scunet_color_real_gan.onnx", "scunet_color_real_psnr.onnx",
            "scunet_gray_15.onnx", "scunet_gray_25.onnx", "scunet_gray_50.onnx",
            // f16 models
            "fbcnn_color_f16.onnx", "fbcnn_gray_f16.onnx", "fbcnn_gray_double_f16.onnx",
            "scunet_color_real_gan_f16.onnx", "scunet_color_real_psnr_f16.onnx",
            "scunet_gray_15_f16.onnx", "scunet_gray_25_f16.onnx", "scunet_gray_50_f16.onnx"
        )

        private val MODEL_HASHES = mapOf(
            "fbcnn_color.onnx" to "3bb0ff3060c217d3b3af95615157fca8a65506455cf4e3d88479e09efffec97f",
            "fbcnn_gray.onnx" to "041b360fc681ae4b134e7ec98da1ae4c7ea57435e5abe701530d5a995a7a27b3",
            "fbcnn_gray_double.onnx" to "83aca9febba0da828dbb5cc6e23e328f60f5ad07fa3de617ab1030f0a24d4f67",
            "scunet_color_real_gan.onnx" to "5eb9a8015cf24477980d3a4eec2e35107c470703b98d257f7560cd3cf3f02922",
            "scunet_color_real_psnr.onnx" to "341eb061ed4d7834dbe6cdab3fb509c887f82aa29be8819c7d09b3d9bfa4892d",
            "scunet_gray_15.onnx" to "10d33552b5754ab9df018cb119e20e1f2b18546eff8e28954529a51e5a6ae255",
            "scunet_gray_25.onnx" to "01b5838a85822ae21880062106a80078f06e7a82aa2ffc8847e32f4462b4c928",
            "scunet_gray_50.onnx" to "a8d9cbbbb2696ac116a87a5055496291939ed873fe28d7f560373675bb970833",

            "fbcnn_color_f16.onnx" to "1a678ff4f721b557fd8a7e560b99cb94ba92f201545c7181c703e7808b93e922",
            "fbcnn_gray_f16.onnx" to "e220b9637a9f2c34a36c98b275b2c9d2b9c2c029e365be82111072376afbec54",
            "fbcnn_gray_double_f16.onnx" to "17feadd8970772f5ff85596cb9fb152ae3c2b82bca4deb52a7c8b3ecb2f7ac14",
            "scunet_color_real_gan_f16.onnx" to "50411164ee869605161be9cafd674c241cf0c104f5ee6b73e7c3ea69d69f94bd",
            "scunet_color_real_psnr_f16.onnx" to "8923b09e240e0078b3247964e9b105cbfbb4da01e260b29a961d038f8fa7791a",
            "scunet_gray_15_f16.onnx" to "8e8740cea4306c9a61215194f315e5c0dc9e06c726a9ddea77d978d804da7663",
            "scunet_gray_25_f16.onnx" to "dec631fbdca7705bbff1fc779cf85a657dcb67f55359c368464dd6e734e1f2b7",
            "scunet_gray_50_f16.onnx" to "48b7d07229a03d98b892d2b33aa4c572ea955301772e7fcb5fd10723552a1874",

            "1x_DitherDeleterV3-Smooth-32._115000_G.onnx" to "4d36e4e33ac49d46472fe77b232923c1731094591a7b5646326698be851c80d7",
            "1x_Bandage-Smooth-64._105000_G.onnx" to "ff04b61a9c19508bfa70431dbffc89e218ab0063de31396e5ce9ac9a2f117d20"
        )

        private val F32_LEGACY_MODELS = listOf(
            "fbcnn_color.onnx", "fbcnn_gray.onnx", "fbcnn_gray_double.onnx",
            "scunet_color_real_gan.onnx", "scunet_color_real_psnr.onnx",
            "scunet_gray_15.onnx", "scunet_gray_25.onnx", "scunet_gray_50.onnx"
        )

        private val MODEL_WARNINGS = buildMap {
            put("1x_DitherDeleterV3-Smooth-32._115000_G.onnx", ModelWarning(
                "model_warning_performance_title",
                "model_warning_ditherdeleter_message",
                "import_anyway",
                "cancel"
            ))
            put("1x_Bandage-Smooth-64._105000_G.onnx", ModelWarning(
                "model_warning_performance_title",
                "model_warning_bandage_message",
                "import_anyway",
                "cancel"
            ))
            F32_LEGACY_MODELS.forEach { modelName ->
                val f16ModelName = modelName.replace(".onnx", "_f16.onnx")
                put(modelName, ModelWarning(
                    "model_warning_outdated_title",
                    "model_warning_outdated_message",
                    "use_anyway",
                    "cancel"
                ))
            }
        }
    }

    data class ModelWarning(
        val title: String,
        val message: String,
        val positiveButtonText: String,
        val negativeButtonText: String
    )

    data class ResolveResult(
        val matchedModel: String?,
        val hashMatches: Boolean,
        val expectedHash: String?,
        val actualHash: String,
        val filename: String,
        val modelWarning: ModelWarning?
    )

    fun hasActiveModel(): Boolean {
        val activeModel = getActiveModelName()
        return activeModel != null && File(context.filesDir, activeModel).exists()
    }

    fun getActiveModelName(): String? {
        return prefs.getString(ACTIVE_MODEL_KEY, null)
    }

    fun setActiveModel(modelName: String) {
        prefs.edit { putString(ACTIVE_MODEL_KEY, modelName) }
        unloadModel()
    }

    fun getInstalledModels(): List<String> {
        val files = context.filesDir.listFiles { _, name -> 
            name.lowercase().endsWith(".onnx") 
        }
        return files?.map { it.name } ?: emptyList()
    }

    fun loadModel(): OrtSession? {
        val activeModel = getActiveModelName() ?: return null
        currentSession?.let { session ->
            if (activeModel == currentModelName) return session
        }
        unloadModel()
        val modelFile = File(context.filesDir, activeModel)
        if (!modelFile.exists()) return null
        return try {
            if (ortEnv == null) {
                ortEnv = OrtEnvironment.getEnvironment()
            }
            val opts = OrtSession.SessionOptions()
            configureSessionOptions(opts, activeModel)
            currentSession = ortEnv?.createSession(modelFile.absolutePath, opts)
            currentModelName = activeModel
            prefs.edit { putString("current_processing_model", activeModel) }
            currentSession
        } catch (e: Exception) {
            Log.e("ModelManager", "Error loading model: ${e.message}", e)
            null
        }
    }

    private fun configureSessionOptions(opts: OrtSession.SessionOptions, modelName: String) {
        val processors = Runtime.getRuntime().availableProcessors()
        try { opts.setIntraOpNumThreads(if (processors <= 2) 1 else (processors * 3) / 4) } catch (e: OrtException) { Log.e("ModelManager", "Error setting IntraOpNumThreads: ${e.message}") }
        try { opts.setInterOpNumThreads(4) } catch (e: OrtException) { Log.e("ModelManager", "Error setting InterOpNumThreads: ${e.message}") }
        try {
            val modelBaseName = modelName.replace("_f16", "", ignoreCase = true)
            when {
                modelBaseName.startsWith("fbcnn_") -> opts.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.EXTENDED_OPT)
                modelBaseName.startsWith("scunet_") -> opts.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.NO_OPT)
            }
        } catch (e: OrtException) { Log.e("ModelManager", "Error setting OptimizationLevel: ${e.message}") }
    }

    fun unloadModel() {
        try { currentSession?.close() } catch (e: Exception) { Log.e("ModelManager", "Error closing session: ${e.message}") }
        currentSession = null
        currentModelName = null
        try { ortEnv?.close() } catch (e: Exception) { Log.e("ModelManager", "Error closing environment: ${e.message}") }
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
            val result = resolveHashOnly(modelUri)
        
            if (result.modelWarning != null && !force) {
                if (onWarning != null) {
                    onWarning(result.matchedModel ?: "", result.modelWarning)
                } else {
                    onError("MODEL_WARNING:${result.matchedModel}:${result.modelWarning.title}:${result.modelWarning.message}:${result.modelWarning.positiveButtonText}:${result.modelWarning.negativeButtonText}")
                }
                return
            }
            if (result.matchedModel == null && !force) {
                onError("GENERIC_MODEL_WARNING")
                return
            }
            importModelInternal(modelUri, result.filename, onProgress, onSuccess, onError)
            
        } catch (e: Exception) {
            onError(e.message ?: "Unknown error during import")
        }
    }

    private fun resolveHashOnly(modelUri: Uri): ResolveResult {
        val filename = resolveFilename(modelUri)
        val actualHash = computeFileHash(modelUri)
        
        for ((modelName, expectedHash) in MODEL_HASHES) {
            if (expectedHash.equals(actualHash, ignoreCase = true)) {
                return ResolveResult(
                    matchedModel = modelName,
                    hashMatches = true,
                    expectedHash = expectedHash,
                    actualHash = actualHash,
                    filename = filename,
                    modelWarning = MODEL_WARNINGS[modelName]
                )
            }
        }
        
        return ResolveResult(
            matchedModel = null,
            hashMatches = false,
            expectedHash = null,
            actualHash = actualHash,
            filename = filename,
            modelWarning = null
        )
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

    private fun computeFileHash(fileUri: Uri): String {
        val inputStream = context.contentResolver.openInputStream(fileUri)
            ?: throw Exception("Could not open file")
        return inputStream.use { stream ->
            val digest = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(8192)
            var read: Int
            while (stream.read(buffer).also { read = it } != -1) {
                digest.update(buffer, 0, read)
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        }
    }

    private fun importModelInternal(
        uri: Uri,
        filename: String,
        onProgress: (Int) -> Unit,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        try {
        val modelFile = File(context.filesDir, filename)
        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            val fileDescriptor = context.contentResolver.openFileDescriptor(uri, "r")
            val size = fileDescriptor?.statSize ?: 0L
            fileDescriptor?.close()
            FileOutputStream(modelFile).use { outputStream ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                var totalRead = 0L
                var lastProgress = 0
                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    outputStream.write(buffer, 0, bytesRead)
                    totalRead += bytesRead
                    if (size > 0) {
                        val progress = ((totalRead * 100) / size).toInt()
                        if (progress != lastProgress) {
                            onProgress(progress)
                            lastProgress = progress
                        }
                    }
                }
            }
        }
        } catch (e: Exception) {
            onError(e.message ?: "Failed to import model")
        } finally {
            onProgress(100)
            onSuccess(filename)
        }
    }

    fun deleteModel(modelName: String, onDeleted: (String) -> Unit = {}) {
        val modelFile = File(context.filesDir, modelName)
        if (modelFile.exists()) {
            modelFile.delete()
            onDeleted(modelName)
        }
        
        if (modelName == getActiveModelName()) {
            val remaining = getInstalledModels()
            if (remaining.isNotEmpty()) {
                setActiveModel(remaining.first())
            } else {
                prefs.edit { remove(ACTIVE_MODEL_KEY) }
            }
        }
    }

    fun isColorModel(modelName: String?): Boolean {
        return modelName?.contains("color", ignoreCase = true) == true
    }

    fun isKnownModel(modelName: String?): Boolean {
        return modelName != null && VALID_MODELS.contains(modelName)
    }

    fun supportsStrengthAdjustment(modelName: String?): Boolean {
        return modelName?.contains("fbcnn", ignoreCase = true) == true
    }

    fun isF32Model(modelName: String?): Boolean {
        if (modelName == null) return false
        return !modelName.contains("_f16", ignoreCase = true) && 
               (modelName.startsWith("fbcnn_") || modelName.startsWith("scunet_"))
    }

    fun isF16Model(modelName: String?): Boolean {
        return modelName?.contains("_f16", ignoreCase = true) == true
    }

    fun getModelWarning(modelName: String?): ModelWarning? {
        return if (modelName != null) MODEL_WARNINGS[modelName] else null
    }
}