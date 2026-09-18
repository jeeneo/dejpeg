/*
 * SPDX-FileCopyrightText: 2025 - 2026 dryerlint <https://codeberg.org/dryerlint>
 * SPDX-License-Identifier: GNU Affero General Public License v3.0 or later
 */


package com.je.dejpeg.data

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.je.dejpeg.App
import com.je.dejpeg.ui.viewmodel.ImageItem
import com.je.dejpeg.utils.ImageLoadingHelper
import com.je.dejpeg.utils.ImageSource
import com.je.dejpeg.utils.ModelType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

enum class SettingsSection {
    OnnxSettings, OidnSettings, MainSettings
}

class AppState(prefs: AppPreferences) {
    val appTheme: MutableState<AppTheme> = mutableStateOf(prefs.loadAppTheme())
}

object ThreadUtils {
    fun resolveThreadCount(configuredThreads: Int?): Int {
        val detected = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
        val configured = configuredThreads ?: AppPreferences.DEFAULT_ONNX_DEVICE_THREADS
        return if (configured <= 0) {
            when {
                detected >= 8 -> 4
                detected >= 6 -> 2
                else -> 1
            }
        } else {
            configured.coerceIn(1, detected)
        }
    }
}

object HapticPatterns {
    var appHapticsEnabled = true
    private val vibrator: Vibrator?
        get() = App.ctx.getSystemService(Vibrator::class.java)
    private val useHaptics get() = appHapticsEnabled
    private const val TAP_DURATION_MS = 20L

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun vibratePredefined(effectId: Int) {
        val vibrator = vibrator ?: return
        val effect = VibrationEffect.createPredefined(effectId)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            vibrator.vibrate(
                effect,
                VibrationAttributes.Builder().setUsage(VibrationAttributes.USAGE_TOUCH).build()
            )
        } else {
            @Suppress("DEPRECATION") vibrator.vibrate(effect)
        }
    }

    private fun vibrateOldSdk() {
        if (!useHaptics) return
        val vibrator = vibrator ?: return
        @Suppress("DEPRECATION") vibrator.vibrate(TAP_DURATION_MS)
    }

    fun tap(force: Boolean = false) {
        if (!useHaptics && !force) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            vibratePredefined(VibrationEffect.EFFECT_TICK)
        } else {
            vibrateOldSdk()
        }
    }

    fun longPress(force: Boolean = false) {
        if (!useHaptics && !force) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            vibratePredefined(VibrationEffect.EFFECT_HEAVY_CLICK)
        } else {
            vibrateOldSdk()
        }
    }
}

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "app_prefs")

data class BrisqueSettings(
    val coarseStep: Int = AppPreferences.DEFAULT_BRISQUE_COARSE_STEP,
    val fineStep: Int = AppPreferences.DEFAULT_BRISQUE_FINE_STEP,
    val fineRange: Int = AppPreferences.DEFAULT_BRISQUE_FINE_RANGE,
    val minWidthRatio: Float = AppPreferences.DEFAULT_BRISQUE_MIN_WIDTH_RATIO,
    val brisqueWeight: Float = AppPreferences.DEFAULT_BRISQUE_WEIGHT,
    val sharpnessWeight: Float = AppPreferences.DEFAULT_BRISQUE_SHARPNESS_WEIGHT
)

class AppPreferences {
    companion object {
        const val PREFS_NAME = "app_prefs"
        const val KEY_SHOW_SAVE_DIALOG = "show_save_confirmation"
        const val KEY_DEFAULT_IMAGE_SOURCE = "default_image_picker"
        const val KEY_HAPTIC_FEEDBACK_ENABLED = "haptics_enabled"
        const val KEY_SWAP_SWIPE_ACTIONS = "swap_swipe_actions"
        const val KEY_COMPAT_MODEL_CLEANUP = "compat_model_cleanup"
        const val KEY_CHUNK_SIZE = "chunk_size"
        const val KEY_OVERLAP_SIZE = "overlap_size"
        const val KEY_ONNX_DEVICE_THREADS = "onnx_device_threads"
        const val KEY_GLOBAL_STRENGTH = "global_strength"
        const val KEY_ACTIVE_MODEL = "active_model"
        const val KEY_STARTER_MODEL_EXTRACTED = "starter_models_extracted"
        const val KEY_BRISQUE_COARSE_STEP = "brisque_coarse_step"
        const val KEY_BRISQUE_FINE_STEP = "brisque_fine_step"
        const val KEY_BRISQUE_FINE_RANGE = "brisque_fine_range"
        const val KEY_BRISQUE_MIN_WIDTH_RATIO = "brisque_min_width_ratio"
        const val KEY_BRISQUE_WEIGHT = "brisque_weight"
        const val KEY_BRISQUE_SHARPNESS_WEIGHT = "brisque_sharpness_weight"
        const val KEY_PROCESSING_MODE = "processing_mode"
        const val KEY_CURRENT_PROCESSING_MODEL = "current_processing_model"
        const val KEY_OIDN_HDR = "oidn_hdr"
        const val KEY_OIDN_SRGB = "oidn_srgb"
        const val KEY_OIDN_QUALITY = "oidn_quality"
        const val KEY_OIDN_NUM_THREADS = "oidn_num_threads"
        const val KEY_OIDN_INPUT_SCALE = "oidn_input_scale"
        const val KEY_APP_THEME = "app_theme"
        const val KEY_GLASS_SLIDER = "before_after_screen_glass_slider"

        const val DEFAULT_CHUNK_SIZE = 512
        const val DEFAULT_OVERLAP_SIZE = 16
        const val DEFAULT_ONNX_DEVICE_THREADS = 0
        const val DEFAULT_GLOBAL_STRENGTH = 50f

        const val DEFAULT_OIDN_QUALITY = 0
        const val DEFAULT_OIDN_MAX_MEMORY_MB = 0
        const val DEFAULT_OIDN_NUM_THREADS = 0
        const val DEFAULT_OIDN_INPUT_SCALE = 0f

        const val DEFAULT_BRISQUE_COARSE_STEP = 20
        const val DEFAULT_BRISQUE_FINE_STEP = 5
        const val DEFAULT_BRISQUE_FINE_RANGE = 30
        const val DEFAULT_BRISQUE_MIN_WIDTH_RATIO = 0.5f
        const val DEFAULT_BRISQUE_WEIGHT = 0.7f
        const val DEFAULT_BRISQUE_SHARPNESS_WEIGHT = 0.3f
    }

    internal fun prefs() = App.ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun loadShowSaveDialog(): Boolean = prefs().getBoolean(KEY_SHOW_SAVE_DIALOG, true)
    fun saveShowSaveDialog(show: Boolean) = prefs().edit { putBoolean(KEY_SHOW_SAVE_DIALOG, show) }

    fun loadDefaultImageSource(): String? = prefs().getString(KEY_DEFAULT_IMAGE_SOURCE, null)
    fun saveDefaultImageSource(source: String?) = prefs().edit {
        if (source == null) remove(KEY_DEFAULT_IMAGE_SOURCE) else putString(
            KEY_DEFAULT_IMAGE_SOURCE, source
        )
    }

    fun loadHapticFeedbackEnabled(): Boolean = prefs().getBoolean(KEY_HAPTIC_FEEDBACK_ENABLED, true)
    fun saveHapticToggle(enabled: Boolean) =
        prefs().edit { putBoolean(KEY_HAPTIC_FEEDBACK_ENABLED, enabled) }

    fun loadSwapSwipeActions(): Boolean = prefs().getBoolean(KEY_SWAP_SWIPE_ACTIONS, false)
    fun saveSwapSwipeActions(swap: Boolean) =
        prefs().edit { putBoolean(KEY_SWAP_SWIPE_ACTIONS, swap) }

    fun loadCompatModelCleanup(): Boolean = prefs().getBoolean(KEY_COMPAT_MODEL_CLEANUP, false)
    fun saveCompatModelCleanup(completed: Boolean) =
        prefs().edit { putBoolean(KEY_COMPAT_MODEL_CLEANUP, completed) }

    fun loadChunkSize(): Int = prefs().getInt(KEY_CHUNK_SIZE, DEFAULT_CHUNK_SIZE)
    fun saveChunkSize(size: Int) = prefs().edit { putInt(KEY_CHUNK_SIZE, size) }

    fun loadOverlapSize(): Int = prefs().getInt(KEY_OVERLAP_SIZE, DEFAULT_OVERLAP_SIZE)
    fun saveOverlapSize(size: Int) = prefs().edit { putInt(KEY_OVERLAP_SIZE, size) }

    fun loadOnnxDeviceThreads(): Int =
        prefs().getInt(KEY_ONNX_DEVICE_THREADS, DEFAULT_ONNX_DEVICE_THREADS)

    fun saveOnnxDeviceThreads(numThreads: Int) =
        prefs().edit { putInt(KEY_ONNX_DEVICE_THREADS, numThreads) }

    fun loadGlobalStrength(): Float = prefs().getFloat(KEY_GLOBAL_STRENGTH, DEFAULT_GLOBAL_STRENGTH)
    fun saveGlobalStrength(strength: Float) =
        prefs().edit { putFloat(KEY_GLOBAL_STRENGTH, strength) }

    fun loadActiveModel(): String? = prefs().getString(KEY_ACTIVE_MODEL, null)
    fun saveActiveModel(modelName: String) = prefs().edit { putString(KEY_ACTIVE_MODEL, modelName) }
    fun clearActiveModel() = prefs().edit { remove(KEY_ACTIVE_MODEL) }

    fun loadStarterModelExtracted(): Boolean =
        prefs().getBoolean(KEY_STARTER_MODEL_EXTRACTED, false)

    fun saveStarterModelExtracted(extracted: Boolean) =
        prefs().edit { putBoolean(KEY_STARTER_MODEL_EXTRACTED, extracted) }

    fun loadBrisqueSettings(): BrisqueSettings {
        return BrisqueSettings(
            coarseStep = prefs().getInt(KEY_BRISQUE_COARSE_STEP, DEFAULT_BRISQUE_COARSE_STEP),
            fineStep = prefs().getInt(KEY_BRISQUE_FINE_STEP, DEFAULT_BRISQUE_FINE_STEP),
            fineRange = prefs().getInt(KEY_BRISQUE_FINE_RANGE, DEFAULT_BRISQUE_FINE_RANGE),
            minWidthRatio = prefs().getFloat(
                KEY_BRISQUE_MIN_WIDTH_RATIO, DEFAULT_BRISQUE_MIN_WIDTH_RATIO
            ),
            brisqueWeight = prefs().getFloat(KEY_BRISQUE_WEIGHT, DEFAULT_BRISQUE_WEIGHT),
            sharpnessWeight = prefs().getFloat(
                KEY_BRISQUE_SHARPNESS_WEIGHT, DEFAULT_BRISQUE_SHARPNESS_WEIGHT
            )
        )
    }

    fun saveBrisqueSettings(settings: BrisqueSettings) = prefs().edit {
        putInt(KEY_BRISQUE_COARSE_STEP, settings.coarseStep)
        putInt(KEY_BRISQUE_FINE_STEP, settings.fineStep)
        putInt(KEY_BRISQUE_FINE_RANGE, settings.fineRange)
        putFloat(KEY_BRISQUE_MIN_WIDTH_RATIO, settings.minWidthRatio)
        putFloat(KEY_BRISQUE_WEIGHT, settings.brisqueWeight)
        putFloat(KEY_BRISQUE_SHARPNESS_WEIGHT, settings.sharpnessWeight)
    }

    fun loadProcessingMode(): ModelType? =
        prefs().getString(KEY_PROCESSING_MODE, null)?.let { ModelType.fromString(it) }

    fun saveProcessingMode(mode: ModelType?) = prefs().edit {
        if (mode == null) remove(KEY_PROCESSING_MODE)
        else putString(KEY_PROCESSING_MODE, mode.name)
    }

    fun loadOidnHdr(): Boolean = prefs().getBoolean(KEY_OIDN_HDR, false)
    fun saveOidnHdr(hdr: Boolean) = prefs().edit { putBoolean(KEY_OIDN_HDR, hdr) }
    fun loadOidnSrgb(): Boolean = prefs().getBoolean(KEY_OIDN_SRGB, false)
    fun saveOidnSrgb(srgb: Boolean) = prefs().edit { putBoolean(KEY_OIDN_SRGB, srgb) }
    fun loadOidnQuality(): Int = prefs().getInt(KEY_OIDN_QUALITY, DEFAULT_OIDN_QUALITY)
    fun saveOidnQuality(quality: Int) = prefs().edit { putInt(KEY_OIDN_QUALITY, quality) }
    fun loadOidnNumThreads(): Int = prefs().getInt(KEY_OIDN_NUM_THREADS, DEFAULT_OIDN_NUM_THREADS)
    fun saveOidnNumThreads(numThreads: Int) =
        prefs().edit { putInt(KEY_OIDN_NUM_THREADS, numThreads) }

    fun loadOidnInputScale(): Float =
        prefs().getFloat(KEY_OIDN_INPUT_SCALE, DEFAULT_OIDN_INPUT_SCALE)

    fun saveOidnInputScale(inputScale: Float) =
        prefs().edit { putFloat(KEY_OIDN_INPUT_SCALE, inputScale) }

    fun loadAppTheme(): AppTheme {
        return when (prefs().getString(KEY_APP_THEME, "dynamic")) {
            "light" -> AppTheme.Light
            "dark" -> AppTheme.Dark
            "oled" -> AppTheme.OLED
            else -> AppTheme.Dynamic
        }
    }

    fun saveAppTheme(theme: AppTheme) = prefs().edit {
        putString(
            KEY_APP_THEME, when (theme) {
                AppTheme.Dynamic -> "dynamic"
                AppTheme.Light -> "light"
                AppTheme.Dark -> "dark"
                AppTheme.OLED -> "oled"
            }
        )
    }

    fun loadGlassSlider(): Boolean = prefs().getBoolean(KEY_GLASS_SLIDER, false)
    fun saveGlassSlider(enabled: Boolean) = prefs().edit { putBoolean(KEY_GLASS_SLIDER, enabled) }

    fun saveCurrentProcessingModel(modelName: String) =
        prefs().edit { putString(KEY_CURRENT_PROCESSING_MODEL, modelName) }
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
                        ImageLoadingHelper.loadBitmap(ImageSource.FromUri(context, uri))
                            ?.let { bmp ->
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

    fun addImageFromOutputCache(context: Context, sourceImageId: String, sourceFilename: String) {
        scope.launch(Dispatchers.IO) {
            val cacheFile = File(context.cacheDir, "${sourceImageId}_processed.png")
            if (!cacheFile.exists()) return@launch
            val uri = cacheFile.toUri()
            val bitmap = BitmapFactory.decodeFile(cacheFile.absolutePath) ?: return@launch
            val id = UUID.randomUUID().toString()
            val item = ImageItem(
                id = id,
                uri = uri,
                filename = "output_$sourceFilename",
                inputBitmap = bitmap,
                thumbnailBitmap = ImageLoadingHelper.generateThumbnail(bitmap),
                size = "${bitmap.width}x${bitmap.height}"
            )
            withContext(Dispatchers.Main) { addImage(item) }
        }
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

