package com.je.dejpeg.compose.utils.helpers

import android.content.Context
import android.net.Uri
import com.je.dejpeg.compose.ModelManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ModelRepository(context: Context) {
    
    private val modelManager = ModelManager(context)

    suspend fun getInstalledModels(): List<String> = withContext(Dispatchers.IO) {
        modelManager.getInstalledModels()
    }
    fun hasActiveModel(): Boolean = modelManager.hasActiveModel()
    fun getActiveModelName(): String? = modelManager.getActiveModelName()
    fun setActiveModel(name: String) {
        modelManager.setActiveModel(name)
    }
    fun getModelWarning(modelName: String?): ModelManager.ModelWarning? {
        return modelName?.let { modelManager.getModelWarning(it) }
    }
    fun supportsStrengthAdjustment(): Boolean {
        val modelName = getActiveModelName()
        return modelName?.contains("fbcnn", ignoreCase = true) == true
    }
    suspend fun importModel(
        uri: Uri,
        force: Boolean = false,
        onProgress: (Int) -> Unit = {},
        onSuccess: (String) -> Unit = {},
        onError: (String) -> Unit = {},
        onWarning: ((String, ModelManager.ModelWarning) -> Unit)? = null
    ) = withContext(Dispatchers.IO) {
        try {
            modelManager.importModel(
                uri,
                onProgress,
                { modelName ->
                    modelManager.setActiveModel(modelName)
                    onSuccess(modelName)
                },
                onError,
                onWarning,
                force
            )
        } catch (e: Exception) {
            onError(e.message ?: "Unknown error")
        }
    }

    suspend fun deleteModels(models: List<String>, onDeleted: (String) -> Unit = {}) = withContext(Dispatchers.IO) {
        models.forEach { name ->
            modelManager.deleteModel(name)
            withContext(Dispatchers.Main) { onDeleted(name) }
        }
    }
}
