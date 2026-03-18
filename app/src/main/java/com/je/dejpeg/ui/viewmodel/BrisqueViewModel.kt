/* SPDX-FileCopyrightText: 2025 - 2026 dryerlint <https://codeberg.org/dryerlint>
 * SPDX-License-Identifier: GNU Affero General Public License v3.0 or later
 */

package com.je.dejpeg.ui.viewmodel

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.je.dejpeg.R
import com.je.dejpeg.data.AppPreferences
import com.je.dejpeg.data.BrisqueSettings
import com.je.dejpeg.ui.components.SnackbarDuration
import com.je.dejpeg.ui.components.SnackySnackbarController
import com.je.dejpeg.ui.components.SnackySnackbarEvents
import com.je.dejpeg.utils.brisque.BRISQUEAssessor
import com.je.dejpeg.utils.brisque.BRISQUEDescaler
import com.je.dejpeg.utils.helpers.ImageActions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    val isAssessing: Boolean = false,
    val assessError: String? = null,
    val descaledBitmap: Bitmap? = null,
    val descaleInfo: DescaleInfo? = null,
    val isDescaling: Boolean = false,
    val descaleError: String? = null,
    val descaleProgress: BRISQUEDescaler.ProgressUpdate? = null,
    val descaleLog: List<String> = emptyList()
)

data class DescaleInfo(
    val originalWidth: Int,
    val originalHeight: Int,
    val detectedWidth: Int,
    val detectedHeight: Int,
    val brisqueScore: Float,
    val sharpness: Float
)

class BrisqueViewModel : ViewModel() {
    private val brisqueAssessor = BRISQUEAssessor()
    private var brisqueDescaler: BRISQUEDescaler? = null
    private var descaleJob: Job? = null
    private var appContext: Context? = null
    private var appPreferences: AppPreferences? = null

    val imageState = MutableStateFlow<BrisqueImageState?>(null)
    val settings = MutableStateFlow(BrisqueSettings())
    val saveState = MutableStateFlow<SaveState>(SaveState.Idle)

    companion object {
        private const val TAG = "BrisqueViewModel"
    }

    fun updateSettings(newSettings: BrisqueSettings) {
        settings.value = newSettings
        viewModelScope.launch {
            appPreferences?.setBrisqueSettings(newSettings)
        }
    }

    fun initialize(context: Context, bitmap: Bitmap, filename: String) {
        appContext = context.applicationContext
        appPreferences = AppPreferences(context.applicationContext)

        BRISQUEAssessor.initialize(context.applicationContext)
        viewModelScope.launch {
            appPreferences?.brisqueSettings?.collect { loadedSettings ->
                settings.value = loadedSettings
            }
        }

        imageState.value = BrisqueImageState(
            originalBitmap = bitmap, filename = filename
        )
    }

    private fun checkDescaleInit(context: Context): BRISQUEDescaler {
        BRISQUEDescaler.initialize(context.applicationContext)
        return brisqueDescaler ?: BRISQUEDescaler(brisqueAssessor).also {
            brisqueDescaler = it
        }
    }

    fun assessQuality(context: Context) {
        val state = imageState.value ?: return
        if (state.isAssessing) return
        imageState.value = state.copy(isAssessing = true, assessError = null)
        viewModelScope.launch(Dispatchers.IO) {
            try {
                BRISQUEDescaler.initialize(context.applicationContext)
                val descaler = checkDescaleInit(context)
                val bmp = state.descaledBitmap ?: state.originalBitmap
                val score = brisqueAssessor.assessImageQualityFromBitmap(bmp)
                val sharpness = descaler.estimateSharpness(bmp)
                withContext(Dispatchers.Main) {
                    imageState.value = imageState.value?.copy(
                        brisqueScore = if (score >= 0) score else null,
                        sharpnessScore = sharpness,
                        isAssessing = false,
                        assessError = if (score < 0) {
                            when (score) {
                                -1.0f -> "BRISQUE error: Image processing failed"
                                -2.0f -> "BRISQUE error: Model not loaded (check brisque_model.bin in assets)"
                                else -> "BRISQUE error (code: $score)"
                            }
                        } else null
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "BRISQUE assessment error", e)
                withContext(Dispatchers.Main) {
                    imageState.value = imageState.value?.copy(
                        isAssessing = false, assessError = "Error: ${e.message}"
                    )
                }
            }
        }
    }

    fun descaleImage(context: Context) {
        val state = imageState.value ?: return
        if (state.isDescaling) return
        imageState.value = state.copy(
            isDescaling = true,
            descaleError = null,
            descaleProgress = null,
            descaleLog = listOf(context.getString(R.string.brisque_starting_descale))
        )
        descaleJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                BRISQUEDescaler.initialize(context.applicationContext)
                val descaler = checkDescaleInit(context)
                val bmp = state.descaledBitmap ?: state.originalBitmap
                val currentSettings = settings.value
                val result = descaler.descale(
                    context = context.applicationContext,
                    bitmap = bmp,
                    coarseStep = currentSettings.coarseStep,
                    fineStep = currentSettings.fineStep,
                    fineRange = currentSettings.fineRange,
                    minWidthRatio = currentSettings.minWidthRatio,
                    brisqueWeight = currentSettings.brisqueWeight,
                    sharpnessWeight = currentSettings.sharpnessWeight,
                    onProgress = { progress ->
                        viewModelScope.launch(Dispatchers.Main) {
                            val log = imageState.value?.descaleLog ?: emptyList()
                            imageState.value = imageState.value?.copy(
                                descaleProgress = progress, descaleLog = log + progress.message
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
                    imageState.value = imageState.value?.copy(
                        descaledBitmap = result.scaleBitmap,
                        descaleInfo = info,
                        isDescaling = false,
                        brisqueScore = null,
                        descaleProgress = null,
                        descaleLog = emptyList()
                    )
                    descaleJob = null
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                Log.d(TAG, "Descaling cancelled")
                withContext(Dispatchers.Main) {
                    imageState.value = imageState.value?.copy(
                        isDescaling = false, descaleProgress = null, descaleLog = emptyList()
                    )
                    descaleJob = null
                }
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Error descaling image: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    imageState.value = imageState.value?.copy(
                        isDescaling = false,
                        descaleError = e.message ?: "Descaling failed",
                        descaleProgress = null,
                        descaleLog = emptyList()
                    )
                    descaleJob = null
                }
            }
        }
    }

    fun cancelDescaling(context: Context) {
        Log.d(TAG, "Cancelling descale...")
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
        imageState.value = imageState.value?.copy(
            isDescaling = false, descaleProgress = null, descaleLog = emptyList()
        )
    }

    fun saveCurrentImage(context: Context) {
        val state = imageState.value ?: return
        val bmp = state.descaledBitmap ?: state.originalBitmap
        val suffix = if (state.descaledBitmap != null) "_descaled" else "_brisque"
        val name = "${state.filename.substringBeforeLast(".")}${suffix}"
        saveState.value = SaveState.Saving(0, 1)
        ImageActions.saveImage(
            context = context,
            bitmap = bmp,
            filename = name,
            onSuccess = {
                viewModelScope.launch {
                    SnackySnackbarController.pushEvent(
                        SnackySnackbarEvents.MessageEvent(
                            message = context.resources.getQuantityString(
                                R.plurals.image_saved_to_gallery,
                                1,
                                1
                            ),
                            duration = SnackbarDuration.Short
                        )
                    )
                    saveState.value = SaveState.Idle
                }
            },
            onError = { error ->
                saveState.value = SaveState.Error(error)
            }
        )
    }

    fun dismissSaveError() {
        saveState.value = SaveState.Idle
    }

    override fun onCleared() {
        super.onCleared()
        imageState.value?.descaledBitmap?.recycle()
    }
}
