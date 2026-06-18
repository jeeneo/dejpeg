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
import com.je.dejpeg.AppPreferences
import com.je.dejpeg.BuildConfig

import com.je.dejpeg.utils.ModelManager
import com.je.dejpeg.utils.ModelMigrationHelper
import com.je.dejpeg.utils.ModelType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsViewModel : ViewModel() {
    val installedModels = MutableStateFlow<Map<ModelType, List<String>>>(emptyMap())
    val activeModels = MutableStateFlow<Map<ModelType, String?>>(emptyMap())
    val hasCheckedModels = MutableStateFlow(false)
    val shouldShowNoModelDialog = MutableStateFlow(false)
    val chunkSize = MutableStateFlow(AppPreferences.DEFAULT_CHUNK_SIZE)
    val overlapSize = MutableStateFlow(AppPreferences.DEFAULT_OVERLAP_SIZE)
    val onnxDeviceThreads = MutableStateFlow(AppPreferences.DEFAULT_ONNX_DEVICE_THREADS)
    val globalStrength = MutableStateFlow(AppPreferences.DEFAULT_GLOBAL_STRENGTH)
    private val _processingMode = MutableStateFlow(ModelType.ONNX)
    val processingMode: StateFlow<ModelType> =
        _processingMode.map { saved -> if (!BuildConfig.OIDN_ENABLED && saved == ModelType.OIDN) ModelType.ONNX else saved }
            .stateIn(viewModelScope, SharingStarted.Eagerly, ModelType.ONNX)

    val installedAllModels: StateFlow<List<Pair<String, ModelType>>> =
        installedModels
            .map { map -> map.flatMap { (type, names) -> names.map { name -> name to type } } }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val currentActiveModelName: StateFlow<String?> = combine(
        activeModels, processingMode
    ) { active, mode -> active[mode] }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

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

    private fun <T> syncPref(flow: MutableStateFlow<T>, prefFlow: Flow<T>) {
        viewModelScope.launch { prefFlow.collect { flow.value = it } }
    }

    private fun <T> persistPref(flow: MutableStateFlow<T>, value: T, save: suspend (T) -> Unit) {
        flow.value = value
        viewModelScope.launch { save(value) }
    }

    fun setActiveModel(name: String) {
        // Derive model type from filename
        val modelType = ModelType.fromFilename(name)
        modelType?.let { type ->
            modelManager?.setActiveModel(name)
            activeModels.value += (type to name)
            setProcessingMode(type)
        }
    }

    fun initialize() {
        if (isInitialized) return
        isInitialized = true
        val context = App.ctx
        appPreferences = AppPreferences()
        modelManager = ModelManager(context)

        val prefs = appPreferences!!
        syncPref(chunkSize, prefs.chunkSize)
        syncPref(overlapSize, prefs.overlapSize)
        syncPref(onnxDeviceThreads, prefs.onnxDeviceThreads)
        syncPref(globalStrength, prefs.globalStrength)
        syncPref(_processingMode, prefs.processingMode)
        syncPref(oidnHdr, prefs.oidnHdr)
        syncPref(oidnSrgb, prefs.oidnSrgb)
        syncPref(oidnQuality, prefs.oidnQuality)
        syncPref(oidnMaxMemoryMB, prefs.oidnMaxMemoryMB)
        syncPref(oidnNumThreads, prefs.oidnNumThreads)
        syncPref(oidnInputScale, prefs.oidnInputScale)

        viewModelScope.launch {
            ModelMigrationHelper.migrateModelsIfNeeded()

            val newInstalled = mutableMapOf<ModelType, List<String>>()
            val newActive = mutableMapOf<ModelType, String?>()

            ModelType.entries.forEach { type ->
                newInstalled[type] = withContext(Dispatchers.IO) {
                    modelManager?.getInstalledModels(type) ?: emptyList()
                }
                newActive[type] = withContext(Dispatchers.IO) {
                    modelManager?.getActiveModelName(type)
                }
            }

            installedModels.value = newInstalled
            activeModels.value = newActive
            hasCheckedModels.value = true

            val starterExtracted = withContext(Dispatchers.IO) {
                modelManager?.initializeStarterModel() ?: false
            }
            if (starterExtracted) {
                installedModels.value += (ModelType.ONNX to (modelManager?.getInstalledModels(
                    ModelType.ONNX
                ) ?: emptyList()))
                activeModels.value += (ModelType.ONNX to (modelManager?.getActiveModelName(ModelType.ONNX)))
            }

            val anyModelInstalled = installedModels.value.values.any { it.isNotEmpty() }
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
            installedModels.value = installedModels.value + (type to installed)
            activeModels.value = activeModels.value + (type to active)
        }
    }

    fun importModel(
        uri: Uri,
        onProgress: (Int) -> Unit = {},
        onSuccess: (String, ModelType) -> Unit = { _, _ -> },
        onError: (String) -> Unit = {}
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            modelManager?.importModel(
                modelUri = uri,
                onProgress = { launch(Dispatchers.Main) { onProgress(it) } },
                onSuccess = { modelName, modelType ->
                    installedModels.value = installedModels.value +
                            (modelType to (installedModels.value[modelType].orEmpty() + modelName))
                    setActiveModel(modelName)
                    activeModels.value = activeModels.value + (modelType to modelName)
                    shouldShowNoModelDialog.value = false
                    setProcessingMode(modelType)
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
            val anyLeft = installedModels.value.values.any { it.isNotEmpty() }
            if (!anyLeft) {
                withContext(Dispatchers.Main) { shouldShowNoModelDialog.value = true }
            }
        }
    }

    fun hasActiveModel(type: ModelType = ModelType.ONNX) =
        modelManager?.hasActiveModel(type) ?: false

    fun setChunkSize(size: Int) =
        persistPref(chunkSize, size) { appPreferences?.setChunkSize(it) ?: Unit }

    fun setOverlapSize(size: Int) =
        persistPref(overlapSize, size) { appPreferences?.setOverlapSize(it) ?: Unit }

    fun setOnnxDeviceThreads(numThreads: Int) = persistPref(onnxDeviceThreads, numThreads) {
        appPreferences?.setOnnxDeviceThreads(it) ?: Unit
    }

    fun setGlobalStrength(strength: Float) {
        persistPref(globalStrength, strength) { appPreferences?.setGlobalStrength(it) ?: Unit }
    }

    fun setProcessingMode(mode: ModelType) {
        persistPref(_processingMode, mode) { appPreferences?.setProcessingMode(it) ?: Unit }
    }

    fun setOidnInputScale(scale: Float) =
        persistPref(oidnInputScale, scale) { appPreferences?.setOidnInputScale(it) ?: Unit }

    fun setOidnHdrPref(hdr: Boolean) =
        persistPref(oidnHdr, hdr) { appPreferences?.setOidnHdr(it) ?: Unit }

    fun setOidnSrgbPref(srgb: Boolean) =
        persistPref(oidnSrgb, srgb) { appPreferences?.setOidnSrgb(it) ?: Unit }

    fun setOidnQualityPref(quality: Int) =
        persistPref(oidnQuality, quality) { appPreferences?.setOidnQuality(it) ?: Unit }

    fun setOidnNumThreadsPref(numThreads: Int) =
        persistPref(oidnNumThreads, numThreads) { appPreferences?.setOidnNumThreads(it) ?: Unit }
}
