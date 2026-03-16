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

@file:Suppress("KotlinConstantConditions", "SimplifyBooleanWithConstants")

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
import com.je.dejpeg.data.Prefs
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
        val bindings = listOf(
            chunkSize to Prefs.CHUNK_SIZE,
            overlapSize to Prefs.OVERLAP_SIZE,
            onnxDeviceThreads to Prefs.ONNX_DEVICE_THREADS,
            globalStrength to Prefs.GLOBAL_STRENGTH,
            _processingMode to Prefs.PROCESSING_MODE,
            oidnHdr to Prefs.OIDN_HDR,
            oidnSrgb to Prefs.OIDN_SRGB,
            oidnQuality to Prefs.OIDN_QUALITY,
            oidnMaxMemoryMB to Prefs.OIDN_MAX_MEMORY_MB,
            oidnNumThreads to Prefs.OIDN_NUM_THREADS,
            oidnInputScale to Prefs.OIDN_INPUT_SCALE
        )

        bindings.forEach { (stateFlow, pref) ->
            @Suppress("UNCHECKED_CAST") syncPref(
                stateFlow as MutableStateFlow<Any>, (pref.flow(prefs) as Flow<Any>)
            )
        }

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
