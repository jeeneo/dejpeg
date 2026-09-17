/*
 * SPDX-FileCopyrightText: 2025 - 2026 dryerlint <https://codeberg.org/dryerlint>
 * SPDX-License-Identifier: GNU Affero General Public License v3.0 or later
 */

@file:Suppress(
    "KotlinConstantConditions", "SimplifyBooleanWithConstants", "SpellCheckingInspection"
)

package com.je.dejpeg.ui.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.je.dejpeg.App
import com.je.dejpeg.data.AppPreferences

import com.je.dejpeg.utils.ModelManager
import com.je.dejpeg.utils.ModelMigrationHelper
import com.je.dejpeg.utils.ModelType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ActiveSelection(
    val type: ModelType? = null, val modelName: String? = null
)

class SettingsViewModel : ViewModel() {
    val activeSelection = MutableStateFlow(ActiveSelection())
    val hasCheckedModels = MutableStateFlow(false)
    val shouldShowNoModelDialog = MutableStateFlow(false)
    val chunkSize = MutableStateFlow(AppPreferences.DEFAULT_CHUNK_SIZE)
    val overlapSize = MutableStateFlow(AppPreferences.DEFAULT_OVERLAP_SIZE)
    val onnxDeviceThreads = MutableStateFlow(AppPreferences.DEFAULT_ONNX_DEVICE_THREADS)
    val globalStrength = MutableStateFlow(AppPreferences.DEFAULT_GLOBAL_STRENGTH)
    val importedModels = MutableStateFlow<Map<ModelType, List<String>>>(emptyMap())
    val oidnHdr = MutableStateFlow(false)
    val oidnSrgb = MutableStateFlow(false)
    val oidnQuality = MutableStateFlow(AppPreferences.DEFAULT_OIDN_QUALITY)
    val oidnMaxMemoryMB = MutableStateFlow(AppPreferences.DEFAULT_OIDN_MAX_MEMORY_MB)
    val oidnNumThreads = MutableStateFlow(AppPreferences.DEFAULT_OIDN_NUM_THREADS)
    val oidnInputScale = MutableStateFlow(AppPreferences.DEFAULT_OIDN_INPUT_SCALE)

    private var appPreferences: AppPreferences? = null
    var modelManager: ModelManager? = null
        private set
    private var isInitialized = false

    private fun <T> persistPref(flow: MutableStateFlow<T>, value: T, save: (T) -> Unit) {
        flow.value = value
        save(value)
    }

    private fun updateSelection(selection: ActiveSelection) {
        activeSelection.value = selection
        appPreferences?.saveProcessingMode(selection.type)
    }

    fun setActiveModel(name: String) {
        val type = ModelType.fromFilename(name) ?: return
        modelManager?.setActiveModel(name)
        updateSelection(ActiveSelection(type, name))
    }

    fun initialize() {
        if (isInitialized) return
        isInitialized = true
        val context = App.ctx
        appPreferences = AppPreferences()
        modelManager = ModelManager.create(context)

        val prefs = appPreferences!!
        chunkSize.value = prefs.loadChunkSize()
        overlapSize.value = prefs.loadOverlapSize()
        onnxDeviceThreads.value = prefs.loadOnnxDeviceThreads()
        globalStrength.value = prefs.loadGlobalStrength()
        oidnHdr.value = prefs.loadOidnHdr()
        oidnSrgb.value = prefs.loadOidnSrgb()
        oidnQuality.value = prefs.loadOidnQuality()
        oidnNumThreads.value = prefs.loadOidnNumThreads()
        oidnInputScale.value = prefs.loadOidnInputScale()

        viewModelScope.launch {
            ModelMigrationHelper.migrateModelsIfNeeded()

            val newInstalled = mutableMapOf<ModelType, List<String>>()

            ModelType.entries.forEach { type ->
                newInstalled[type] = withContext(Dispatchers.IO) {
                    modelManager?.getInstalledModels(type) ?: emptyList()
                }
            }

            importedModels.value = newInstalled

            val savedType = prefs.loadProcessingMode()?.takeIf { it.enabled }
            val savedName = savedType?.let { type ->
                withContext(Dispatchers.IO) { modelManager?.getActiveModelName(type) }?.takeIf { name ->
                    newInstalled[type]?.contains(
                        name
                    ) == true
                }
            }
            activeSelection.value = ActiveSelection(savedType, savedName)
            hasCheckedModels.value = true

            val starterExtracted = withContext(Dispatchers.IO) {
                modelManager?.initializeStarterModel() ?: false
            }
            if (starterExtracted) {
                val onnxInstalled = withContext(Dispatchers.IO) {
                    modelManager?.getInstalledModels(ModelType.ONNX) ?: emptyList()
                }
                importedModels.value += (ModelType.ONNX to onnxInstalled)
                if (activeSelection.value.type == null) {
                    val starterName = withContext(Dispatchers.IO) {
                        modelManager?.getActiveModelName(ModelType.ONNX)
                    }
                    updateSelection(ActiveSelection(ModelType.ONNX, starterName))
                }
            }

            val anyModelInstalled = importedModels.value.values.any { it.isNotEmpty() }
            if (!anyModelInstalled) {
                shouldShowNoModelDialog.value = true
            }
        }
    }

    fun refreshInstalledModels(type: ModelType = ModelType.ONNX) {
        viewModelScope.launch {
            val installed = withContext(Dispatchers.IO) {
                modelManager?.getInstalledModels(type) ?: emptyList()
            }
            val active = withContext(Dispatchers.IO) {
                val name = modelManager?.getActiveModelName(type)
                if (name != null && !installed.contains(name)) null else name
            }
            importedModels.value += (type to installed)
            activeSelection.update { sel ->
                if (sel.type == type) sel.copy(modelName = active) else sel
            }
        }
    }

    fun importModels(
        uris: List<Uri>,
        onProgress: (Int) -> Unit = {},
        onSuccess: (String, ModelType) -> Unit = { _, _ -> },
        onError: (String) -> Unit = {}
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            modelManager?.importModels(
                modelUris = uris,
                onProgress = { launch(Dispatchers.Main) { onProgress(it) } },
                onSuccess = { modelName, modelType ->
                    importedModels.value += (modelType to (importedModels.value[modelType].orEmpty() + modelName))
                    setActiveModel(modelName)
                    shouldShowNoModelDialog.value = false
                    launch(Dispatchers.Main) { onSuccess(modelName, modelType) }
                },
                onError = { launch(Dispatchers.Main) { onError(it) } })
        }
    }

    fun deleteModel(
        modelName: String, type: ModelType = ModelType.ONNX, onDeleted: (String) -> Unit = {}
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            modelManager?.deleteModel(modelName, type)
            withContext(Dispatchers.Main) { onDeleted(modelName) }
            refreshInstalledModels(type)
            val remaining = modelManager?.getInstalledModels(type).orEmpty()
            if (remaining.isEmpty() && activeSelection.value.type == type) {
                updateSelection(ActiveSelection())
            }
            val anyLeft = importedModels.value.values.any { it.isNotEmpty() }
            if (!anyLeft) {
                withContext(Dispatchers.Main) { shouldShowNoModelDialog.value = true }
            }
        }
    }

    fun hasActiveModel(type: ModelType? = ModelType.ONNX) =
        type?.let { modelManager?.hasActiveModel(it) } ?: false

    fun setChunkSize(size: Int) = persistPref(chunkSize, size) { appPreferences?.saveChunkSize(it) }

    fun setOverlapSize(size: Int) =
        persistPref(overlapSize, size) { appPreferences?.saveOverlapSize(it) }

    fun setOnnxDeviceThreads(numThreads: Int) = persistPref(onnxDeviceThreads, numThreads) {
        appPreferences?.saveOnnxDeviceThreads(it)
    }

    fun setGlobalStrength(strength: Float) {
        persistPref(globalStrength, strength) { appPreferences?.saveGlobalStrength(it) }
    }

    fun setOidnInputScale(scale: Float) =
        persistPref(oidnInputScale, scale) { appPreferences?.saveOidnInputScale(it) }

    fun setOidnHdrPref(hdr: Boolean) = persistPref(oidnHdr, hdr) { appPreferences?.saveOidnHdr(it) }

    fun setOidnSrgbPref(srgb: Boolean) =
        persistPref(oidnSrgb, srgb) { appPreferences?.saveOidnSrgb(it) }

    fun setOidnQualityPref(quality: Int) =
        persistPref(oidnQuality, quality) { appPreferences?.saveOidnQuality(it) }

    fun setOidnNumThreadsPref(numThreads: Int) =
        persistPref(oidnNumThreads, numThreads) { appPreferences?.saveOidnNumThreads(it) }
}
