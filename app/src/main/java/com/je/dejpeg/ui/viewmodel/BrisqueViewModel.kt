/*
 * SPDX-FileCopyrightText: 2025 - 2026 dryerlint <https://codeberg.org/dryerlint>
 * SPDX-License-Identifier: GNU Affero General Public License v3.0 or later
 */

package com.je.dejpeg.ui.viewmodel

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.je.dejpeg.App
import com.je.dejpeg.R
import com.je.dejpeg.data.AppPreferences
import com.je.dejpeg.data.BrisqueSettings
import com.je.dejpeg.processing.BRISQUEAssessor
import com.je.dejpeg.processing.BRISQUEDescaler
import com.je.dejpeg.ui.components.SnackbarController
import com.je.dejpeg.ui.components.SnackbarDuration
import com.je.dejpeg.ui.components.SnackbarEvents
import com.je.dejpeg.utils.ImageActions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException

sealed class SaveState {
    object Idle : SaveState()
    data class Saving(val current: Int, val total: Int) : SaveState()
    data class Error(val message: String) : SaveState()
}

data class BrisqueImageState(
    val originalBitmap: Bitmap,
    val filename: String,
    val brisqueScore: Float? = null,
    val sharpnessScore: Float? = null,
    val isBusy: Boolean = false,
    val assessError: String? = null,
    val descaledBitmap: Bitmap? = null,
    val descaleInfo: DescaleInfo? = null,
    val isDescaling: Boolean = false,
    val descaleError: String? = null,
    val descaleProgress: BRISQUEDescaler.ProgressUpdate? = null,
)

data class DescaleInfo(
    val originalWidth: Int,
    val originalHeight: Int,
    val detectedWidth: Int,
    val detectedHeight: Int,
    val brisqueScore: Float,
    val sharpness: Float
)

class BrisqueViewModel(
    originalBitmap: Bitmap,
    filename: String,
) : ViewModel() {
    private val brisqueAssessor = BRISQUEAssessor()
    private var brisqueDescaler: BRISQUEDescaler? = null
    private var assessJob: Job? = null
    private var descaleJob: Job? = null
    private var appPreferences: AppPreferences? = null

    val imageState = MutableStateFlow(BrisqueImageState(originalBitmap, filename))
    val settings = MutableStateFlow(BrisqueSettings())
    val saveState = MutableStateFlow<SaveState>(SaveState.Idle)

    companion object {
        private const val TAG = "BrisqueViewModel"
    }

    fun updateSettings(newSettings: BrisqueSettings) {
        settings.value = newSettings
        appPreferences?.saveBrisqueSettings(newSettings)
    }

    fun initialize(context: Context) {
        appPreferences = AppPreferences()
        BRISQUEAssessor.initialize(context.applicationContext)
        settings.value = appPreferences?.loadBrisqueSettings() ?: BrisqueSettings()
    }

    private fun checkDescaleInit(context: Context): BRISQUEDescaler {
        BRISQUEDescaler.initialize(context.applicationContext)
        return brisqueDescaler ?: BRISQUEDescaler(brisqueAssessor).also {
            brisqueDescaler = it
        }
    }

    fun assessQuality(context: Context) {
        val state = imageState.value
        if (state.isBusy) return
        imageState.value = state.copy(isBusy = true, assessError = null)
        assessJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                BRISQUEDescaler.initialize(context.applicationContext)
                val descaler = checkDescaleInit(context)
                val bmp = state.descaledBitmap ?: state.originalBitmap
                val score = brisqueAssessor.assessImageQualityFromBitmap(bmp)
                val sharpness = descaler.estimateSharpness(bmp)
                withContext(Dispatchers.Main) {
                    imageState.value = imageState.value.copy(
                        brisqueScore = if (score >= 0) score else null,
                        sharpnessScore = sharpness,
                        isBusy = false,
                        assessError = if (score < 0) {
                            when (score) {
                                -1.0f -> "BRISQUE error: Image processing failed"
                                -2.0f -> "BRISQUE error: Model not loaded (check brisque_model.bin in assets)"
                                else -> "BRISQUE error (code: $score)"
                            }
                        } else null
                    )
                    assessJob = null
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                Log.d(TAG, "Assessment cancelled")
                imageState.value = imageState.value.copy(isBusy = false)
                assessJob = null
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "BRISQUE assessment error", e)
                withContext(Dispatchers.Main) {
                    imageState.value = imageState.value.copy(
                        isBusy = false, assessError = "Error: ${e.message}"
                    )
                    assessJob = null
                }
            }
        }
    }

    fun descaleImage() {
        val state = imageState.value
        if (state.isDescaling) return
        val context = App.ctx
        imageState.value = state.copy(
            isBusy = true,
            isDescaling = true,
            descaleError = null,
            descaleProgress = null,
        )
        descaleJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                BRISQUEDescaler.initialize(context)
                val descaler = checkDescaleInit(context)
                val bmp = state.descaledBitmap ?: state.originalBitmap
                val currentSettings = settings.value
                val result = descaler.descale(
                    context = context,
                    bitmap = bmp,
                    coarseStep = currentSettings.coarseStep,
                    fineStep = currentSettings.fineStep,
                    fineRange = currentSettings.fineRange,
                    minWidthRatio = currentSettings.minWidthRatio,
                    brisqueWeight = currentSettings.brisqueWeight,
                    sharpnessWeight = currentSettings.sharpnessWeight,
                    onProgress = { progress ->
                        viewModelScope.launch(Dispatchers.Main) {
                            imageState.value = imageState.value.copy(
                                descaleProgress = progress
                            )
                        }
                    })
                val info = DescaleInfo(
                    originalWidth = result.originalWidth,
                    originalHeight = result.originalHeight,
                    detectedWidth = result.detectedOptimalWidth,
                    detectedHeight = result.detectedOptimalHeight,
                    brisqueScore = result.bestBrisqueScore,
                    sharpness = result.bestSharpness
                )
                withContext(Dispatchers.Main) {
                    imageState.value = imageState.value.copy(
                        descaledBitmap = result.scaleBitmap,
                        descaleInfo = info,
                        isBusy = false,
                        isDescaling = false,
                        brisqueScore = null,
                        sharpnessScore = null,
                        descaleProgress = null,
                    )
                    descaleJob = null
                }
            } catch (e: CancellationException) {
                Log.d(TAG, "Descaling cancelled")
                withContext(Dispatchers.Main) {
                    imageState.value = imageState.value.copy(
                        isBusy = false,
                        isDescaling = false,
                        descaleProgress = null,
                    )
                    descaleJob = null
                }
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Error descaling image: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    imageState.value = imageState.value.copy(
                        isBusy = false,
                        isDescaling = false,
                        descaleError = e.message ?: "Descaling failed",
                        descaleProgress = null,
                    )
                    descaleJob = null
                }
            }
        }
    }

    fun cancelWork(context: Context) {
        Log.d(TAG, "Cancelling work...")
        assessJob?.cancel()
        assessJob = null
        descaleJob?.cancel()
        descaleJob = null
        viewModelScope.launch(Dispatchers.IO) {
            try {
                context.cacheDir.listFiles()
                    ?.filter { it.name.startsWith("brisque_descaler_") || it.name.startsWith("brisque_assess_") }
                    ?.forEach { file ->
                        try {
                            if (file.delete()) Log.d(TAG, "Deleted temp file: ${file.name}")
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed to delete temp file: ${file.name}", e)
                        }
                    }
            } catch (e: Exception) {
                Log.e(TAG, "Error cleaning up temp files: ${e.message}", e)
            }
        }
        imageState.value = imageState.value.copy(
            isBusy = false, isDescaling = false, descaleProgress = null
        )
    }

    fun saveCurrentImage(context: Context) {
        val state = imageState.value
        val bmp = state.descaledBitmap ?: state.originalBitmap
        val suffix = if (state.descaledBitmap != null) "_descaled" else "_brisque"
        val name = "${state.filename.substringBeforeLast(".")}${suffix}"
        saveState.value = SaveState.Saving(0, 1)
        ImageActions.saveImage(
            scope = viewModelScope,
            context = context,
            bitmap = bmp,
            filename = name,
            onSuccess = {
                viewModelScope.launch {
                    SnackbarController.pushEvent(
                        SnackbarEvents.MessageEvent(
                            message = context.resources.getQuantityString(
                                R.plurals.image_saved_to_gallery, 1, 1
                            ), duration = SnackbarDuration.Short
                        )
                    )
                    saveState.value = SaveState.Idle
                }
            },
            onError = { error ->
                saveState.value = SaveState.Error(error)
            })
    }

    fun dismissSaveError() {
        saveState.value = SaveState.Idle
    }

    override fun onCleared() {
        imageState.value.descaledBitmap?.recycle()
    }
}
