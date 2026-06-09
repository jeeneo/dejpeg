/*
 * SPDX-FileCopyrightText: 2025 - 2026 dryerlint <https://codeberg.org/dryerlint>
 * SPDX-License-Identifier: GNU Affero General Public License v3.0 or later
 */

@file:Suppress("SpellCheckingInspection")

package com.je.dejpeg

import android.content.Context
import android.net.Uri
import android.os.Build
import android.util.Log
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.je.dejpeg.ui.viewmodel.ImageItem
import com.je.dejpeg.utils.ImageLoadingHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

object HapticPatterns {

    fun light(view: View, isEnabled: Boolean) {
        if (!isEnabled) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            view.performHapticFeedback(HapticFeedbackConstants.GESTURE_START)
        } else {
            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        }
    }

    fun medium(view: View, isEnabled: Boolean) {
        if (!isEnabled) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
        } else {
            view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
        }
    }

    fun heavy(view: View, isEnabled: Boolean) {
        if (!isEnabled) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            view.performHapticFeedback(HapticFeedbackConstants.REJECT)
        } else {
            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        }
    }

    fun error(view: View, isEnabled: Boolean) {
        if (!isEnabled) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            view.performHapticFeedback(HapticFeedbackConstants.REJECT)
        } else {
            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        }
    }

    fun gestureStart(view: View, isEnabled: Boolean) {
        if (!isEnabled) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            view.performHapticFeedback(HapticFeedbackConstants.GESTURE_START)
        } else {
            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        }
    }
}

@Composable
fun rememberHaptics(): HapticFeedbacks {
    val view = LocalView.current
    val appPreferences = remember { AppPreferences() }
    val isEnabled by appPreferences.hapticFeedbackEnabled.collectAsState(initial = true)
    return remember(view, isEnabled) { HapticFeedbacks(view, isEnabled) }
}

class HapticFeedbacks(private val view: View, private val isEnabled: Boolean) {
    fun light() = HapticPatterns.light(view, isEnabled)
    fun medium() = HapticPatterns.medium(view, isEnabled)
    fun heavy() = HapticPatterns.heavy(view, isEnabled)
    fun error() = HapticPatterns.error(view, isEnabled)
    fun gestureStart() = HapticPatterns.gestureStart(view, isEnabled)
}

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "app_prefs")

enum class ProcessingMode {
    ONNX, OIDN;

    companion object {
        fun fromString(value: String?): ProcessingMode = entries.find { it.name == value } ?: ONNX
    }
}

object PreferenceKeys {
    val SHOW_SAVE_DIALOG = booleanPreferencesKey("showSaveDialog")
    val DEFAULT_IMAGE_SOURCE = stringPreferencesKey("defaultImageSource")
    val HAPTIC_FEEDBACK_ENABLED = booleanPreferencesKey("hapticFeedbackEnabled")
    val SWAP_SWIPE_ACTIONS = booleanPreferencesKey("swapSwipeActions")
    val COMPAT_MODEL_CLEANUP = booleanPreferencesKey("compatModelCleanup")
    val STARTER_MODEL_EXTRACTED = booleanPreferencesKey("starterModelExtracted")
    val ACTIVE_MODEL = stringPreferencesKey("activeModel")
    val CURRENT_PROCESSING_MODEL = stringPreferencesKey("current_processing_model")
    val CHUNK_SIZE = intPreferencesKey("chunk_size")
    val OVERLAP_SIZE = intPreferencesKey("overlap_size")
    val ONNX_DEVICE_THREADS = intPreferencesKey("onnx_device_threads")
    val GLOBAL_STRENGTH = floatPreferencesKey("global_strength")
    val BRISQUE_COARSE_STEP = intPreferencesKey("brisque_coarse_step")
    val BRISQUE_FINE_STEP = intPreferencesKey("brisque_fine_step")
    val BRISQUE_FINE_RANGE = intPreferencesKey("brisque_fine_range")
    val BRISQUE_MIN_WIDTH_RATIO = floatPreferencesKey("brisque_min_width_ratio")
    val BRISQUE_WEIGHT = floatPreferencesKey("brisque_weight")
    val BRISQUE_SHARPNESS_WEIGHT = floatPreferencesKey("brisque_sharpness_weight")

    val PROCESSING_MODE = stringPreferencesKey("processing_mode")
    val ACTIVE_OIDN_MODEL = stringPreferencesKey("activeOidnModel")
    val OIDN_HDR = booleanPreferencesKey("oidn_hdr")
    val OIDN_SRGB = booleanPreferencesKey("oidn_srgb")
    val OIDN_QUALITY = intPreferencesKey("oidn_quality")
    val OIDN_MAX_MEMORY_MB = intPreferencesKey("oidn_max_memory_mb")
    val OIDN_NUM_THREADS = intPreferencesKey("oidn_num_threads")
    val OIDN_INPUT_SCALE = floatPreferencesKey("oidn_input_scale")
}

data class BrisqueSettings(
    val coarseStep: Int = DEFAULT_BRISQUE_COARSE_STEP,
    val fineStep: Int = DEFAULT_BRISQUE_FINE_STEP,
    val fineRange: Int = DEFAULT_BRISQUE_FINE_RANGE,
    val minWidthRatio: Float = DEFAULT_BRISQUE_MIN_WIDTH_RATIO,
    val brisqueWeight: Float = DEFAULT_BRISQUE_WEIGHT,
    val sharpnessWeight: Float = DEFAULT_BRISQUE_SHARPNESS_WEIGHT
)

private const val DEFAULT_BRISQUE_COARSE_STEP = 20
private const val DEFAULT_BRISQUE_FINE_STEP = 5
private const val DEFAULT_BRISQUE_FINE_RANGE = 30
private const val DEFAULT_BRISQUE_MIN_WIDTH_RATIO = 0.5f
private const val DEFAULT_BRISQUE_WEIGHT = 0.7f
private const val DEFAULT_BRISQUE_SHARPNESS_WEIGHT = 0.3f

class AppPreferences {

    companion object {
        const val DEFAULT_CHUNK_SIZE = 512
        const val DEFAULT_OVERLAP_SIZE = 16
        const val DEFAULT_ONNX_DEVICE_THREADS = 0
        const val DEFAULT_GLOBAL_STRENGTH = 50f
        const val DEFAULT_OIDN_QUALITY = 0
        const val DEFAULT_OIDN_MAX_MEMORY_MB = 0
        const val DEFAULT_OIDN_NUM_THREADS = 0
        const val DEFAULT_OIDN_INPUT_SCALE = 0f
    }

    val showSaveDialog: Flow<Boolean> = App.ctx.dataStore.data.map { prefs ->
        prefs[PreferenceKeys.SHOW_SAVE_DIALOG] ?: true
    }

    val defaultImageSource: Flow<String?> = App.ctx.dataStore.data.map { prefs ->
        prefs[PreferenceKeys.DEFAULT_IMAGE_SOURCE]
    }

    val hapticFeedbackEnabled: Flow<Boolean> = App.ctx.dataStore.data.map { prefs ->
        prefs[PreferenceKeys.HAPTIC_FEEDBACK_ENABLED] ?: true
    }

    val swapSwipeActions: Flow<Boolean> = App.ctx.dataStore.data.map { prefs ->
        prefs[PreferenceKeys.SWAP_SWIPE_ACTIONS] ?: false
    }

    val compatModelCleanup: Flow<Boolean> = App.ctx.dataStore.data.map { prefs ->
        prefs[PreferenceKeys.COMPAT_MODEL_CLEANUP] ?: false
    }

    val chunkSize: Flow<Int> = App.ctx.dataStore.data.map { prefs ->
        prefs[PreferenceKeys.CHUNK_SIZE] ?: DEFAULT_CHUNK_SIZE
    }

    val overlapSize: Flow<Int> = App.ctx.dataStore.data.map { prefs ->
        prefs[PreferenceKeys.OVERLAP_SIZE] ?: DEFAULT_OVERLAP_SIZE
    }

    val onnxDeviceThreads: Flow<Int> = App.ctx.dataStore.data.map { prefs ->
        prefs[PreferenceKeys.ONNX_DEVICE_THREADS] ?: DEFAULT_ONNX_DEVICE_THREADS
    }

    val globalStrength: Flow<Float> = App.ctx.dataStore.data.map { prefs ->
        prefs[PreferenceKeys.GLOBAL_STRENGTH] ?: DEFAULT_GLOBAL_STRENGTH
    }

    val activeModel: Flow<String?> = App.ctx.dataStore.data.map { prefs ->
        prefs[PreferenceKeys.ACTIVE_MODEL]
    }

    val starterModelExtracted: Flow<Boolean> = App.ctx.dataStore.data.map { prefs ->
        prefs[PreferenceKeys.STARTER_MODEL_EXTRACTED] ?: false
    }

    val brisqueSettings: Flow<BrisqueSettings> = App.ctx.dataStore.data.map { prefs ->
        BrisqueSettings(
            coarseStep = prefs[PreferenceKeys.BRISQUE_COARSE_STEP] ?: DEFAULT_BRISQUE_COARSE_STEP,
            fineStep = prefs[PreferenceKeys.BRISQUE_FINE_STEP] ?: DEFAULT_BRISQUE_FINE_STEP,
            fineRange = prefs[PreferenceKeys.BRISQUE_FINE_RANGE] ?: DEFAULT_BRISQUE_FINE_RANGE,
            minWidthRatio = prefs[PreferenceKeys.BRISQUE_MIN_WIDTH_RATIO]
                ?: DEFAULT_BRISQUE_MIN_WIDTH_RATIO,
            brisqueWeight = prefs[PreferenceKeys.BRISQUE_WEIGHT] ?: DEFAULT_BRISQUE_WEIGHT,
            sharpnessWeight = prefs[PreferenceKeys.BRISQUE_SHARPNESS_WEIGHT]
                ?: DEFAULT_BRISQUE_SHARPNESS_WEIGHT
        )
    }

    suspend fun setShowSaveDialog(show: Boolean) {
        App.ctx.dataStore.edit { prefs ->
            prefs[PreferenceKeys.SHOW_SAVE_DIALOG] = show
        }
    }

    suspend fun setDefaultImageSource(source: String?) {
        App.ctx.dataStore.edit { prefs ->
            if (source == null) {
                prefs.remove(PreferenceKeys.DEFAULT_IMAGE_SOURCE)
            } else {
                prefs[PreferenceKeys.DEFAULT_IMAGE_SOURCE] = source
            }
        }
    }

    suspend fun setHapticFeedbackEnabled(enabled: Boolean) {
        App.ctx.dataStore.edit { prefs ->
            prefs[PreferenceKeys.HAPTIC_FEEDBACK_ENABLED] = enabled
        }
    }

    suspend fun setSwapSwipeActions(swap: Boolean) {
        App.ctx.dataStore.edit { prefs ->
            prefs[PreferenceKeys.SWAP_SWIPE_ACTIONS] = swap
        }
    }

    suspend fun setCompatModelCleanup(completed: Boolean) {
        App.ctx.dataStore.edit { prefs ->
            prefs[PreferenceKeys.COMPAT_MODEL_CLEANUP] = completed
        }
    }

    suspend fun getCompatModelCleanupImmediate(): Boolean = compatModelCleanup.first()

    suspend fun setChunkSize(size: Int) {
        App.ctx.dataStore.edit { prefs ->
            prefs[PreferenceKeys.CHUNK_SIZE] = size
        }
    }

    suspend fun setOverlapSize(size: Int) {
        App.ctx.dataStore.edit { prefs ->
            prefs[PreferenceKeys.OVERLAP_SIZE] = size
        }
    }

    suspend fun setOnnxDeviceThreads(numThreads: Int) {
        App.ctx.dataStore.edit { prefs ->
            prefs[PreferenceKeys.ONNX_DEVICE_THREADS] = numThreads
        }
    }

    suspend fun setGlobalStrength(strength: Float) {
        App.ctx.dataStore.edit { prefs ->
            prefs[PreferenceKeys.GLOBAL_STRENGTH] = strength
        }
    }

    suspend fun setBrisqueSettings(settings: BrisqueSettings) {
        App.ctx.dataStore.edit { prefs ->
            prefs[PreferenceKeys.BRISQUE_COARSE_STEP] = settings.coarseStep
            prefs[PreferenceKeys.BRISQUE_FINE_STEP] = settings.fineStep
            prefs[PreferenceKeys.BRISQUE_FINE_RANGE] = settings.fineRange
            prefs[PreferenceKeys.BRISQUE_MIN_WIDTH_RATIO] = settings.minWidthRatio
            prefs[PreferenceKeys.BRISQUE_WEIGHT] = settings.brisqueWeight
            prefs[PreferenceKeys.BRISQUE_SHARPNESS_WEIGHT] = settings.sharpnessWeight
        }
    }

    suspend fun setActiveModel(modelName: String) {
        App.ctx.dataStore.edit { prefs ->
            prefs[PreferenceKeys.ACTIVE_MODEL] = modelName
        }
    }

    suspend fun clearActiveModel() {
        App.ctx.dataStore.edit { prefs ->
            prefs.remove(PreferenceKeys.ACTIVE_MODEL)
        }
    }

    suspend fun setCurrentProcessingModel(modelName: String) {
        App.ctx.dataStore.edit { prefs ->
            prefs[PreferenceKeys.CURRENT_PROCESSING_MODEL] = modelName
        }
    }

    suspend fun getActiveModel(): String? = activeModel.first()

    suspend fun setStarterModelExtracted(extracted: Boolean) {
        App.ctx.dataStore.edit { prefs ->
            prefs[PreferenceKeys.STARTER_MODEL_EXTRACTED] = extracted
        }
    }

    suspend fun getStarterModelExtractedImmediate(): Boolean = starterModelExtracted.first()

    val processingMode: Flow<ProcessingMode> = App.ctx.dataStore.data.map { prefs ->
        ProcessingMode.fromString(prefs[PreferenceKeys.PROCESSING_MODE])
    }

    suspend fun setProcessingMode(mode: ProcessingMode) {
        App.ctx.dataStore.edit { prefs ->
            prefs[PreferenceKeys.PROCESSING_MODE] = mode.name
        }
    }

    val activeOidnModel: Flow<String?> = App.ctx.dataStore.data.map { prefs ->
        prefs[PreferenceKeys.ACTIVE_OIDN_MODEL]
    }

    suspend fun setActiveOidnModel(modelName: String) {
        App.ctx.dataStore.edit { prefs ->
            prefs[PreferenceKeys.ACTIVE_OIDN_MODEL] = modelName
        }
    }

    suspend fun clearActiveOidnModel() {
        App.ctx.dataStore.edit { prefs ->
            prefs.remove(PreferenceKeys.ACTIVE_OIDN_MODEL)
        }
    }

    suspend fun getActiveOidnModel(): String? = activeOidnModel.first()

    val oidnHdr: Flow<Boolean> = App.ctx.dataStore.data.map { prefs ->
        prefs[PreferenceKeys.OIDN_HDR] ?: false
    }

    val oidnSrgb: Flow<Boolean> = App.ctx.dataStore.data.map { prefs ->
        prefs[PreferenceKeys.OIDN_SRGB] ?: false
    }

    val oidnQuality: Flow<Int> = App.ctx.dataStore.data.map { prefs ->
        prefs[PreferenceKeys.OIDN_QUALITY] ?: DEFAULT_OIDN_QUALITY
    }

    val oidnMaxMemoryMB: Flow<Int> = App.ctx.dataStore.data.map { prefs ->
        prefs[PreferenceKeys.OIDN_MAX_MEMORY_MB] ?: DEFAULT_OIDN_MAX_MEMORY_MB
    }

    val oidnNumThreads: Flow<Int> = App.ctx.dataStore.data.map { prefs ->
        prefs[PreferenceKeys.OIDN_NUM_THREADS] ?: DEFAULT_OIDN_NUM_THREADS
    }

    val oidnInputScale: Flow<Float> = App.ctx.dataStore.data.map { prefs ->
        prefs[PreferenceKeys.OIDN_INPUT_SCALE] ?: DEFAULT_OIDN_INPUT_SCALE
    }

    suspend fun setOidnHdr(hdr: Boolean) {
        App.ctx.dataStore.edit { prefs -> prefs[PreferenceKeys.OIDN_HDR] = hdr }
    }

    suspend fun setOidnSrgb(srgb: Boolean) {
        App.ctx.dataStore.edit { prefs -> prefs[PreferenceKeys.OIDN_SRGB] = srgb }
    }

    suspend fun setOidnQuality(quality: Int) {
        App.ctx.dataStore.edit { prefs -> prefs[PreferenceKeys.OIDN_QUALITY] = quality }
    }

    suspend fun setOidnNumThreads(numThreads: Int) {
        App.ctx.dataStore.edit { prefs -> prefs[PreferenceKeys.OIDN_NUM_THREADS] = numThreads }
    }

    suspend fun setOidnInputScale(inputScale: Float) {
        App.ctx.dataStore.edit { prefs -> prefs[PreferenceKeys.OIDN_INPUT_SCALE] = inputScale }
    }
}

class ImageRepository {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    val images = MutableStateFlow<List<ImageItem>>(emptyList())
    val sharedUris = MutableStateFlow<List<Uri>>(emptyList())
    val isLoadingImages = MutableStateFlow(false)
    val loadingImagesProgress = MutableStateFlow<Pair<Int, Int>?>(null)

    fun addImage(item: ImageItem) {
        images.value += item
    }

    fun addSharedUri(uri: Uri) {
        if (sharedUris.value.any { it == uri }) return
        sharedUris.value += uri
    }

    fun addImagesFromUris(context: Context, uris: List<Uri>) {
        if (uris.isEmpty()) return
        scope.launch {
            isLoadingImages.value = true
            loadingImagesProgress.value = Pair(0, uris.size)
            withContext(Dispatchers.IO) {
                uris.forEachIndexed { index, uri ->
                    try {
                        ImageLoadingHelper.loadBitmapWithRotation(context, uri)?.let { bmp ->
                            val imageId = UUID.randomUUID().toString()
                            uri.path?.substringAfterLast('/')?.let { filename ->
                                if (filename.startsWith("temp_camera_")) {
                                    val tempFile = File(App.ctx.cacheDir, filename)
                                    if (tempFile.exists()) {
                                        val unprocessedFile =
                                            File(App.ctx.cacheDir, "${imageId}_unprocessed.jpg")
                                        if (tempFile.renameTo(unprocessedFile)) {
                                            Log.d(
                                                "ImageRepository",
                                                "Renamed camera temp file to ${unprocessedFile.name}"
                                            )
                                        }
                                    }
                                }
                            }
                            val imageItem = ImageItem(
                                id = imageId,
                                uri = uri,
                                filename = ImageLoadingHelper.getFileNameFromUri(context, uri),
                                inputBitmap = bmp,
                                thumbnailBitmap = ImageLoadingHelper.generateThumbnail(bmp),
                                size = "${bmp.width}x${bmp.height}",

                                )
                            withContext(Dispatchers.Main) {
                                addImage(imageItem)
                                loadingImagesProgress.value = Pair(index + 1, uris.size)
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("ImageRepository", "Failed to load image: $uri - ${e.message}")
                    }
                }
            }

            isLoadingImages.value = false
            loadingImagesProgress.value = null
        }
    }

    fun removeImage(id: String) {
        images.value = images.value.filter { it.id != id }
    }

    fun getImageById(id: String) = images.value.find { it.id == id }

    fun updateImageState(id: String, transform: (ImageItem) -> ImageItem) {
        images.value = images.value.map { if (it.id == id) transform(it) else it }
    }

    fun markImageAsSaved(imageId: String) {
        updateImageState(imageId) { it.copy(hasBeenSaved = true) }
    }

    companion object {
        @Volatile
        private var instance: ImageRepository? = null

        fun getInstance(): ImageRepository {
            return instance ?: synchronized(this) {
                instance ?: ImageRepository().also { instance = it }
            }
        }
    }
}

