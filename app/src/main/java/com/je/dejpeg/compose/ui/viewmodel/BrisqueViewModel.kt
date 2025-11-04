package com.je.dejpeg.ui.viewmodel

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.je.dejpeg.utils.BrisqueAssessor
import com.je.dejpeg.utils.BrisqueDescaler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Job
import java.io.File
import java.io.IOException

data class BrisqueImageState(
    val originalBitmap: Bitmap,
    val filename: String,
    val brisqueScore: Float? = null,
    val isAssessing: Boolean = false,
    val assessError: String? = null,
    val descaledBitmap: Bitmap? = null,
    val descaleInfo: DescaleInfo? = null,
    val isDescaling: Boolean = false,
    val descaleError: String? = null,
    val descaleProgress: com.je.dejpeg.utils.BrisqueDescaler.ProgressUpdate? = null,
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
    private val brisqueAssessor = BrisqueAssessor()
    private var brisqueDescaler: BrisqueDescaler? = null
    private var descaleJob: Job? = null
    
    private val _imageState = MutableStateFlow<BrisqueImageState?>(null)
    val imageState: StateFlow<BrisqueImageState?> = _imageState.asStateFlow()
    
    companion object {
        private const val TAG = "BrisqueViewModel"
    }
    
    fun initialize(context: Context, bitmap: Bitmap, filename: String) {
        if (brisqueDescaler == null) {
            brisqueDescaler = BrisqueDescaler(brisqueAssessor, context.applicationContext)
        }
        _imageState.value = BrisqueImageState(
            originalBitmap = bitmap,
            filename = filename
        )
    }
    
    fun assessQuality(context: Context) {
        val state = _imageState.value ?: return
        if (state.isAssessing) return
        _imageState.value = state.copy(isAssessing = true, assessError = null)
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val bmp = state.descaledBitmap ?: state.originalBitmap
                val temp = File.createTempFile("brisque_assess_${System.currentTimeMillis()}_", ".png", context.cacheDir)
                bmp.compress(Bitmap.CompressFormat.PNG, 100, temp.outputStream())
                try {
                    val model = File(context.cacheDir, "brisque_model_live.yml")
                    val range = File(context.cacheDir, "brisque_range_live.yml")
                    if (!model.exists()) context.assets.open("brisque_model_live.yml").use { it.copyTo(model.outputStream()) }
                    if (!range.exists()) context.assets.open("brisque_range_live.yml").use { it.copyTo(range.outputStream()) }
                    val score = brisqueAssessor.assessImageQuality(temp.absolutePath, model.absolutePath, range.absolutePath)
                    withContext(Dispatchers.Main) {
                        _imageState.value = _imageState.value?.copy(
                            brisqueScore = if (score < 0) null else score,
                            isAssessing = false,
                            assessError = if (score < 0) "Failed to compute BRISQUE score (error code: $score)" else null
                        )
                    }
                } finally {
                    temp.delete()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _imageState.value = _imageState.value?.copy(
                        isAssessing = false,
                        assessError = e.message ?: "Unknown error occurred"
                    )
                }
            }
        }
    }
    
    fun descaleImage(context: Context) {
        val state = _imageState.value ?: return
        if (state.isDescaling) return
        _imageState.value = state.copy(
            isDescaling = true,
            descaleError = null,
            descaleProgress = null,
            descaleLog = listOf("Starting descale analysis...")
        )
        descaleJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val descaler = brisqueDescaler ?: throw IllegalStateException("Descaler not initialized")
                val bmp = state.descaledBitmap ?: state.originalBitmap
                val result = descaler.analyzeAndDescale(
                    bitmap = bmp,
                    onProgress = { progress ->
                        viewModelScope.launch(Dispatchers.Main) {
                            val log = _imageState.value?.descaleLog ?: emptyList()
                            _imageState.value = _imageState.value?.copy(
                                descaleProgress = progress,
                                descaleLog = log + progress.message
                            )
                        }
                    }
                )
                val info = DescaleInfo(
                    originalWidth = result.originalWidth,
                    originalHeight = result.originalHeight,
                    detectedWidth = result.detectedOptimalWidth,
                    detectedHeight = result.detectedOptimalHeight,
                    brisqueScore = result.bestBrisqueScore,
                    sharpness = result.bestSharpness
                )
                withContext(Dispatchers.Main) {
                    _imageState.value = _imageState.value?.copy(
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
                    _imageState.value = _imageState.value?.copy(
                        isDescaling = false,
                        descaleProgress = null,
                        descaleLog = emptyList()
                    )
                    descaleJob = null
                }
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Error descaling image: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    _imageState.value = _imageState.value?.copy(
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
        Log.d(TAG, "Cancelling descaling...")
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
        _imageState.value = _imageState.value?.copy(
            isDescaling = false,
            descaleProgress = null,
            descaleLog = emptyList()
        )
    }
    
    fun saveCurrentImage(context: Context, onSuccess: () -> Unit, onError: (String) -> Unit) {
        val state = _imageState.value ?: return
        val bmp = state.descaledBitmap ?: state.originalBitmap
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val suffix = if (state.descaledBitmap != null) "_descaled" else "_brisque"
                val base = state.filename.substringBeforeLast(".")
                val name = "${base}${suffix}.png"
                val values = android.content.ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, name)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                        put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES)
                        put(MediaStore.Images.Media.IS_PENDING, 1)
                    }
                }
                val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                if (uri != null) {
                    context.contentResolver.openOutputStream(uri)?.use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                        values.clear()
                        values.put(MediaStore.Images.Media.IS_PENDING, 0)
                        context.contentResolver.update(uri, values, null, null)
                    }
                    withContext(Dispatchers.Main) { onSuccess() }
                } else throw IOException("Failed to create media store entry")
            } catch (e: Exception) {
                Log.e(TAG, "Error saving image: ${e.message}", e)
                withContext(Dispatchers.Main) { onError(e.message ?: "Failed to save image") }
            }
        }
    }
    
    fun clearDescaleError() {
        _imageState.value = _imageState.value?.copy(descaleError = null)
    }
    
    fun clearAssessError() {
        _imageState.value = _imageState.value?.copy(assessError = null)
    }
    
    override fun onCleared() {
        super.onCleared()
        _imageState.value?.descaledBitmap?.recycle()
    }
}
