/*
 * SPDX-FileCopyrightText: 2025 - 2026 dryerlint <https://codeberg.org/dryerlint>
 * SPDX-License-Identifier: GNU Affero General Public License v3.0 or later
 */

@file:Suppress("KotlinConstantConditions", "SimplifyBooleanWithConstants",
    "SpellCheckingInspection"
)

package com.je.dejpeg.ui.viewmodel

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.je.dejpeg.BuildConfig
import com.je.dejpeg.ModelManager
import com.je.dejpeg.ModelType
import com.je.dejpeg.data.AppPreferences
import com.je.dejpeg.data.ProcessingMode
import com.je.dejpeg.utils.helpers.ModelMigrationHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsViewModel : ViewModel() {
    val installedModels = MutableStateFlow<List<String>>(emptyList())
    val installedOidnModels = MutableStateFlow<List<String>>(emptyList())
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

    fun initialize(context: Context) {
        if (isInitialized) return
        isInitialized = true
        val appCtx = context.applicationContext
        appPreferences = AppPreferences(appCtx)
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
            appCtx.let { ctx ->
                ModelMigrationHelper.migrateModelsIfNeeded(ctx)
            }
            installedModels.value = withContext(Dispatchers.IO) {
                modelManager?.getInstalledModels(ModelType.ONNX) ?: emptyList()
            }
            installedOidnModels.value = withContext(Dispatchers.IO) {
                modelManager?.getInstalledModels(ModelType.OIDN) ?: emptyList()
            }
            hasCheckedModels.value = true
            activeModelName.value = withContext(Dispatchers.IO) {
                modelManager?.getActiveModelName(ModelType.ONNX)
            }
            activeOidnModelName.value = withContext(Dispatchers.IO) {
                modelManager?.getActiveModelName(ModelType.OIDN)
            }
            val activeType =
                if (processingMode.value == ProcessingMode.OIDN) ModelType.OIDN else ModelType.ONNX
            val activeList =
                if (activeType == ModelType.OIDN) installedOidnModels.value else installedModels.value
            if (activeList.isEmpty()) {
                shouldShowNoModelDialog.value = true
            }
        }
    }

    fun refreshInstalledModels(type: ModelType = ModelType.ONNX) {
        viewModelScope.launch {
            when (type) {
                ModelType.ONNX -> installedModels.value = withContext(Dispatchers.IO) {
                    modelManager?.getInstalledModels(ModelType.ONNX) ?: emptyList()
                }

                ModelType.OIDN -> installedOidnModels.value = withContext(Dispatchers.IO) {
                    modelManager?.getInstalledModels(ModelType.OIDN) ?: emptyList()
                }
            }
        }
    }

    fun importModel(
        uri: Uri,
        type: ModelType = ModelType.ONNX,
        force: Boolean = false,
        onProgress: (Int) -> Unit = {},
        onSuccess: (String) -> Unit = {},
        onError: (String) -> Unit = {},
        onWarning: ((String, ModelManager.ModelWarning) -> Unit)? = null
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                modelManager?.importModel(
                    modelUri = uri,
                    type = type,
                    onProgress = { launch(Dispatchers.Main) { onProgress(it) } },
                    onSuccess = { modelName ->
                        modelManager?.setActiveModel(modelName, type)
                        when (type) {
                            ModelType.ONNX -> activeModelName.value = modelName
                            ModelType.OIDN -> activeOidnModelName.value = modelName
                        }
                        refreshInstalledModels(type)
                        launch(Dispatchers.Main) { onSuccess(modelName) }
                    },
                    onError = { launch(Dispatchers.Main) { onError(it) } },
                    onWarning = onWarning?.let { cb ->
                        { n, w ->
                            launch(Dispatchers.Main) {
                                cb(
                                    n, w
                                )
                            }
                        }
                    },
                    force = force
                )
            } catch (e: Exception) {
                launch(Dispatchers.Main) { onError(e.message ?: "Unknown error") }
            }
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

    fun setActiveModelByName(name: String, type: ModelType = ModelType.ONNX) {
        modelManager?.setActiveModel(name, type) ?: Log.e(
            "SettingsViewModel", "modelManager is null!"
        )
        when (type) {
            ModelType.ONNX -> activeModelName.value = name
            ModelType.OIDN -> activeOidnModelName.value = name
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
