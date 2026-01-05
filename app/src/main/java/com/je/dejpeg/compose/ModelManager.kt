package com.je.dejpeg.compose

import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtException
import ai.onnxruntime.OrtSession
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import com.je.dejpeg.data.dataStore
import com.je.dejpeg.data.PreferenceKeys
import com.je.dejpeg.compose.utils.helpers.ModelMigrationHelper
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.je.dejpeg.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import kotlin.collections.iterator

class ModelManager(
    private val context: Context,
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) {
    private var currentSession: OrtSession? = null
    private var ortEnv: OrtEnvironment? = null
    private var currentModelName: String? = null
    private var cachedActiveModel: String? = null
    
    private fun getModelsDir(): File = ModelMigrationHelper.getOnnxModelsDir(context)

    companion object {
        private val ACTIVE_MODEL_KEY = stringPreferencesKey("activeModel")
        private val CURRENT_PROCESSING_MODEL_KEY = stringPreferencesKey("current_processing_model")
        private val MODEL_HASHES = mapOf(

            // starter model
            "1xDeJPG_OmniSR-fp16.onnx" to "7c2fcc4e6e671dfab79cabea6c97384c157870fbfa0d951daf045c28f267d942",

            // older F32 models
            "fbcnn_color.onnx" to "3bb0ff3060c217d3b3af95615157fca8a65506455cf4e3d88479e09efffec97f",
            "fbcnn_gray.onnx" to "041b360fc681ae4b134e7ec98da1ae4c7ea57435e5abe701530d5a995a7a27b3",
            "fbcnn_gray_double.onnx" to "83aca9febba0da828dbb5cc6e23e328f60f5ad07fa3de617ab1030f0a24d4f67",
            "scunet_color_real_gan.onnx" to "5eb9a8015cf24477980d3a4eec2e35107c470703b98d257f7560cd3cf3f02922",
            "scunet_color_real_psnr.onnx" to "341eb061ed4d7834dbe6cdab3fb509c887f82aa29be8819c7d09b3d9bfa4892d",
            "scunet_gray_15.onnx" to "10d33552b5754ab9df018cb119e20e1f2b18546eff8e28954529a51e5a6ae255",
            "scunet_gray_25.onnx" to "01b5838a85822ae21880062106a80078f06e7a82aa2ffc8847e32f4462b4c928",
            "scunet_gray_50.onnx" to "a8d9cbbbb2696ac116a87a5055496291939ed873fe28d7f560373675bb970833",

            // F16 models
            "fbcnn_color_f16.onnx" to "1a678ff4f721b557fd8a7e560b99cb94ba92f201545c7181c703e7808b93e922",
            "fbcnn_gray_f16.onnx" to "e220b9637a9f2c34a36c98b275b2c9d2b9c2c029e365be82111072376afbec54",
            "fbcnn_gray_double_f16.onnx" to "17feadd8970772f5ff85596cb9fb152ae3c2b82bca4deb52a7c8b3ecb2f7ac14",
            "scunet_color_real_gan_f16.onnx" to "50411164ee869605161be9cafd674c241cf0c104f5ee6b73e7c3ea69d69f94bd",
            "scunet_color_real_psnr_f16.onnx" to "8923b09e240e0078b3247964e9b105cbfbb4da01e260b29a961d038f8fa7791a",
            "scunet_gray_15_f16.onnx" to "8e8740cea4306c9a61215194f315e5c0dc9e06c726a9ddea77d978d804da7663",
            "scunet_gray_25_f16.onnx" to "dec631fbdca7705bbff1fc779cf85a657dcb67f55359c368464dd6e734e1f2b7",
            "scunet_gray_50_f16.onnx" to "48b7d07229a03d98b892d2b33aa4c572ea955301772e7fcb5fd10723552a1874",

            // special models - slow, needs optimization
            "1x_DitherDeleterV3-Smooth-32._115000_G.onnx" to "4d36e4e33ac49d46472fe77b232923c1731094591a7b5646326698be851c80d7",
            "1x_Bandage-Smooth-64._105000_G.onnx" to "ff04b61a9c19508bfa70431dbffc89e218ab0063de31396e5ce9ac9a2f117d20"
        )

        private val F32_LEGACY_MODELS = listOf(
            "fbcnn_color.onnx", "fbcnn_gray.onnx", "fbcnn_gray_double.onnx",
            "scunet_color_real_gan.onnx", "scunet_color_real_psnr.onnx",
            "scunet_gray_15.onnx", "scunet_gray_25.onnx", "scunet_gray_50.onnx"
        )

        private val MODEL_WARNINGS = buildMap {
            // put("1x_DitherDeleterV3-Smooth-32._115000_G.onnx", ModelWarning(
            //     R.string.model_warning_performance_title,
            //     R.string.model_warning_ditherdeleter_message,
            //     R.string.import_anyway,
            //     R.string.cancel
            // ))
            // put("1x_Bandage-Smooth-64._105000_G.onnx", ModelWarning(
            //     R.string.model_warning_performance_title,
            //     R.string.model_warning_bandage_message,
            //     R.string.import_anyway,
            //     R.string.cancel
            // ))
            F32_LEGACY_MODELS.forEach { modelName ->
                put(modelName, ModelWarning(
                    R.string.model_warning_outdated_title,
                    R.string.model_warning_outdated_message,
                    R.string.use_anyway,
                    R.string.cancel
                ))
            }
        }
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
            context.dataStore.data.map { prefs ->
                prefs[ACTIVE_MODEL_KEY]
            }.first().also { cachedActiveModel = it }
        }
    }

    fun setActiveModel(modelName: String) {
        Log.d("ModelManager", "setActiveModel called with: $modelName")
        unloadModel()
        cachedActiveModel = modelName
        coroutineScope.launch {
            context.dataStore.edit { prefs ->
                prefs[ACTIVE_MODEL_KEY] = modelName
                Log.d("ModelManager", "Active model saved to DataStore: $modelName")
            }
        }
        Log.d("ModelManager", "Active model set to: $modelName")
    }

    private fun clearActiveModel() {
        cachedActiveModel = null
        coroutineScope.launch {
            context.dataStore.edit { prefs ->
                prefs.remove(ACTIVE_MODEL_KEY)
            }
        }
    }

    private fun setCurrentProcessingModel(modelName: String) {
        coroutineScope.launch {
            context.dataStore.edit { prefs ->
                prefs[CURRENT_PROCESSING_MODEL_KEY] = modelName
            }
        }
    }

    fun getInstalledModels(): List<String> {
        val modelsDir = getModelsDir()
        if (!modelsDir.exists()) return emptyList()
        val files = modelsDir.listFiles { _, name -> 
            name.lowercase().endsWith(".onnx") 
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
            val modelBaseName = modelName.replace("_f16", "", ignoreCase = true)
            when {
                modelBaseName.startsWith("fbcnn_") -> opts.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.EXTENDED_OPT)
                modelBaseName.startsWith("scunet_") -> opts.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.NO_OPT)
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
            if (!filename.lowercase().endsWith(".onnx")) {
                onError("Only .onnx model files are supported")
                return
            }
            val actualHash = computeFileHash(modelUri)
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
            onError(e.message ?: "Unknown error during import")
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
            ensureModelsDir()
            val modelFile = File(getModelsDir(), filename)
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val size = context.contentResolver.openFileDescriptor(uri, "r")?.statSize ?: 0L
                FileOutputStream(modelFile).use { outputStream ->
                    copyWithProgress(inputStream, outputStream, size, onProgress)
                }
            }
        } catch (e: Exception) {
            onError(e.message ?: "Failed to import model")
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

    fun initializeStarterModel(): Boolean {
        try {
            if (isStarterModelAlreadyExtracted()) {
                Log.d("ModelManager", "Starter model already extracted, skipping")
                return false
            }
            if (hasModelsInstalled()) {
                Log.d("ModelManager", "Models already exist, skipping starter model extraction")
                markStarterModelExtracted()
                return false
            }
            val success = performStarterModelExtraction(setAsActive = true)
            return success
        } catch (e: Exception) {
            Log.e("ModelManager", "Error initializing starter model: ${e.message}", e)
            return false
        }
    }
    
    private fun isStarterModelAlreadyExtracted(): Boolean = runBlocking {
        context.dataStore.data.map { prefs ->
            prefs[PreferenceKeys.STARTER_MODEL_EXTRACTED] ?: false
        }.first()
    }
    
    private fun hasModelsInstalled(): Boolean {
        val modelsDir = getModelsDir()
        return modelsDir.exists() && modelsDir.listFiles { _, name -> 
            name.lowercase().endsWith(".onnx") 
        }?.isNotEmpty() == true
    }
    
    private fun performStarterModelExtraction(
        setAsActive: Boolean = false,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ): Boolean {
        return try {
            ensureModelsDir()
            val extracted = extractStarterModel(getModelsDir(), setAsActive)
            if (extracted) {
                markStarterModelExtracted()
                Log.d("ModelManager", "Starter model extracted successfully")
                onSuccess()
            } else {
                onError("Failed to extract starter model")
            }
            extracted
        } catch (e: Exception) {
            Log.e("ModelManager", "Error extracting starter model: ${e.message}", e)
            onError(e.message ?: "Unknown error")
            false
        }
    }
    
    private fun ensureModelsDir() {
        val modelsDir = getModelsDir()
        if (!modelsDir.exists()) {
            modelsDir.mkdirs()
        }
    }
    
    private fun markStarterModelExtracted() {
        coroutineScope.launch {
            context.dataStore.edit { prefs ->
                prefs[PreferenceKeys.STARTER_MODEL_EXTRACTED] = true
                Log.d("ModelManager", "Marked starter model as extracted in preferences")
            }
        }
    }
    
    fun resetStarterModelFlag() {
        coroutineScope.launch {
            context.dataStore.edit { prefs ->
                prefs.remove(PreferenceKeys.STARTER_MODEL_EXTRACTED)
                Log.d("ModelManager", "Reset starter model extraction flag")
            }
        }
    }
    
    fun extractStarterModelManually(onSuccess: () -> Unit = {}, onError: (String) -> Unit = {}) {
        coroutineScope.launch {
            val setAsActive = !hasModelsInstalled()
            performStarterModelExtraction(
                setAsActive = setAsActive,
                onSuccess = onSuccess,
                onError = onError
            )
        }
    }

    private fun extractStarterModel(modelsDir: File, setAsActive: Boolean = false): Boolean {
        return try {
            val zipInputStream = context.assets.open("1xDeJPG_OmniSR-fp16.zip")
            java.util.zip.ZipInputStream(zipInputStream).use { zipFile ->
                var entry = zipFile.nextEntry
                while (entry != null) {
                    if (entry.name.endsWith(".onnx")) {
                        val modelFile = File(modelsDir, entry.name)
                        FileOutputStream(modelFile).use { out ->
                            zipFile.copyTo(out, 8192)
                        }
                        Log.d("ModelManager", "Extracted starter model: ${entry.name}")
                        if (setAsActive) {
                            setActiveModel(entry.name)
                        }
                        return true
                    }
                    entry = zipFile.nextEntry
                }
            }
            false
        } catch (e: Exception) {
            Log.e("ModelManager", "Error extracting starter model: ${e.message}", e)
            false
        }
    }
}
