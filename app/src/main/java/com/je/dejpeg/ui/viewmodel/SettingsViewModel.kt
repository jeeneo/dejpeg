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
import com.je.dejpeg.ProcessingMode
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
    val installedModels = MutableStateFlow<List<String>>(emptyList())
    val installedOidnModels = MutableStateFlow<List<String>>(emptyList())
    val installedAllModels = MutableStateFlow<List<Pair<String, ModelType>>>(emptyList())
    val activeModelName = MutableStateFlow<String?>(null)
    val activeOidnModelName = MutableStateFlow<String?>(null)
    val hasCheckedModels = MutableStateFlow(false)
    val shouldShowNoModelDialog = MutableStateFlow(false)

    val chunkSize = MutableStateFlow(AppPreferences.DEFAULT_CHUNK_SIZE)
    val overlapSize = MutableStateFlow(AppPreferences.DEFAULT_OVERLAP_SIZE)
    val onnxDeviceThreads = MutableStateFlow(AppPreferences.DEFAULT_ONNX_DEVICE_THREADS)
    val globalStrength = MutableStateFlow(AppPreferences.DEFAULT_GLOBAL_STRENGTH)
    private val _processingMode = MutableStateFlow(ProcessingMode.ONNX)
    val processingMode: StateFlow<ProcessingMode> =
        _processingMode.map { saved -> if (!BuildConfig.OIDN_ENABLED && saved == ProcessingMode.OIDN) ProcessingMode.ONNX else saved }
            .stateIn(viewModelScope, SharingStarted.Eagerly, ProcessingMode.ONNX)

    val currentActiveModelName: StateFlow<String?> =
        combine(activeModelName, activeOidnModelName, processingMode) { onnxName, oidnName, mode ->
            when (mode) {
                ProcessingMode.ONNX -> onnxName
                ProcessingMode.OIDN -> oidnName
            }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

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
            installedModels.value = withContext(Dispatchers.IO) {
                modelManager?.getInstalledModels(ModelType.ONNX) ?: emptyList()
            }
            if (BuildConfig.OIDN_ENABLED) {
                installedOidnModels.value = withContext(Dispatchers.IO) {
                    modelManager?.getInstalledModels(ModelType.OIDN) ?: emptyList()
                }
            }
            installedAllModels.value =
                installedModels.value.map { it to ModelType.ONNX } + if (BuildConfig.OIDN_ENABLED) installedOidnModels.value.map { it to ModelType.OIDN } else emptyList()
            hasCheckedModels.value = true
            activeModelName.value = withContext(Dispatchers.IO) {
                modelManager?.getActiveModelName(ModelType.ONNX)
            }
            if (BuildConfig.OIDN_ENABLED) {
                activeOidnModelName.value = withContext(Dispatchers.IO) {
                    modelManager?.getActiveModelName(ModelType.OIDN)
                }
            }
            val activeType =
                if (processingMode.value == ProcessingMode.OIDN) ModelType.OIDN else ModelType.ONNX
            val activeList =
                if (activeType == ModelType.OIDN && BuildConfig.OIDN_ENABLED) installedOidnModels.value else installedModels.value
            if (activeList.isEmpty()) {
                shouldShowNoModelDialog.value = true
            }
        }
    }

    fun refreshInstalledModels(type: ModelType = ModelType.ONNX) {
        viewModelScope.launch {
            when (type) {
                ModelType.ONNX -> {
                    installedModels.value = withContext(Dispatchers.IO) {
                        modelManager?.getInstalledModels(ModelType.ONNX) ?: emptyList()
                    }
                    activeModelName.value = withContext(Dispatchers.IO) {
                        val name = modelManager?.getActiveModelName(ModelType.ONNX)
                        if (name != null && !installedModels.value.contains(name)) null else name
                    }
                }

                ModelType.OIDN -> {
                    installedOidnModels.value = withContext(Dispatchers.IO) {
                        modelManager?.getInstalledModels(ModelType.OIDN) ?: emptyList()
                    }
                    activeOidnModelName.value = withContext(Dispatchers.IO) {
                        val name = modelManager?.getActiveModelName(ModelType.OIDN)
                        if (name != null && !installedOidnModels.value.contains(name)) null else name
                    }
                }
            }
            installedAllModels.value =
                installedModels.value.map { it to ModelType.ONNX } + installedOidnModels.value.map { it to ModelType.OIDN }
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
                    when (modelType) {
                        ModelType.ONNX -> {
                            installedModels.value += modelName
                            activeModelName.value = modelName
                        }

                        ModelType.OIDN -> {
                            installedOidnModels.value += modelName
                            activeOidnModelName.value = modelName
                        }
                    }
                    installedAllModels.value =
                        installedModels.value.map { it to ModelType.ONNX } + installedOidnModels.value.map { it to ModelType.OIDN }
                    if (BuildConfig.OIDN_ENABLED) {
                        setProcessingMode(if (modelType == ModelType.OIDN) ProcessingMode.OIDN else ProcessingMode.ONNX)
                    }
                    launch(Dispatchers.Main) { onSuccess(modelName, modelType) }
                },
                onError = { launch(Dispatchers.Main) { onError(it) } })
        }
    }

    fun deleteModels(
        models: List<String>, type: ModelType = ModelType.ONNX, onDeleted: (String) -> Unit = {}
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            models.forEach { name ->
                modelManager?.deleteModel(name, type)
                withContext(Dispatchers.Main) { onDeleted(name) }
            }
            refreshInstalledModels(type)
        }
    }

    fun setActiveModelByName(
        name: String, type: ModelType = ModelType.ONNX, switchMode: Boolean = false
    ) {
        modelManager?.setActiveModel(name, type)
        when (type) {
            ModelType.ONNX -> activeModelName.value = name
            ModelType.OIDN -> activeOidnModelName.value = name
        }
        if (BuildConfig.OIDN_ENABLED && switchMode) {
            setProcessingMode(if (type == ModelType.OIDN) ProcessingMode.OIDN else ProcessingMode.ONNX)
        }
    }

    fun hasActiveModel(type: ModelType = ModelType.ONNX) =
        modelManager?.hasActiveModel(type) ?: false

    fun getActiveModelName(type: ModelType = ModelType.ONNX) =
        modelManager?.getActiveModelName(type)

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

    fun setProcessingMode(mode: ProcessingMode) {
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
