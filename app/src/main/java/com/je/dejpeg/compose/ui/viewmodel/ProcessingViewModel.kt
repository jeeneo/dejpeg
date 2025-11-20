package com.je.dejpeg.compose.ui.viewmodel

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.activity.result.ActivityResultLauncher
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.je.dejpeg.ImageProcessor
import com.je.dejpeg.ModelManager
import com.je.dejpeg.ProcessingService
import com.je.dejpeg.compose.utils.ImageActions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import android.os.Build
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import androidx.exifinterface.media.ExifInterface
import android.graphics.Matrix
import android.provider.OpenableColumns
import java.util.UUID

data class ImageItem(
    val id: String,
    val uri: Uri?,
    val filename: String,
    val inputBitmap: Bitmap,
    val outputBitmap: Bitmap? = null,
    val thumbnailBitmap: Bitmap? = null,
    var size: String,
    val isProcessing: Boolean = false,
    val progress: String = "",
    val strengthFactor: Float = 0.5f,
    val isCancelling: Boolean = false,
    val timeEstimateMillis: Long = 0L,
    val timeEstimateStartMillis: Long = 0L,
    val hasBeenSaved: Boolean = false
)

sealed class ProcessingUiState {
    object Idle : ProcessingUiState()
    data class Processing(val currentIndex: Int, val total: Int) : ProcessingUiState()
    data class Error(val message: String) : ProcessingUiState()
}

class ProcessingViewModel : ViewModel() {

    private val _images = MutableStateFlow<List<ImageItem>>(emptyList())
    val images: StateFlow<List<ImageItem>> = _images.asStateFlow()
    private val _uiState = MutableStateFlow<ProcessingUiState>(ProcessingUiState.Idle)
    val uiState: StateFlow<ProcessingUiState> = _uiState.asStateFlow()
    private val _globalStrength = MutableStateFlow(50f)
    val globalStrength: StateFlow<Float> = _globalStrength.asStateFlow()
    private val _chunkSize = MutableStateFlow(ImageProcessor.DEFAULT_CHUNK_SIZE)
    val chunkSize: StateFlow<Int> = _chunkSize.asStateFlow()
    private val _overlapSize = MutableStateFlow(ImageProcessor.OVERLAP)
    val overlapSize: StateFlow<Int> = _overlapSize.asStateFlow()
    private var imageProcessor: ImageProcessor? = null
    private var modelManager: ModelManager? = null
    private var appContext: Context? = null
    private val _installedModels = MutableStateFlow<List<String>>(emptyList())
    val installedModels: StateFlow<List<String>> = _installedModels.asStateFlow()
    private val _hasCheckedModels = MutableStateFlow(false)
    val hasCheckedModels: StateFlow<Boolean> = _hasCheckedModels.asStateFlow()
    private val _shouldShowNoModelDialog = MutableStateFlow(false)
    val shouldShowNoModelDialog: StateFlow<Boolean> = _shouldShowNoModelDialog.asStateFlow()
    private val _shouldShowBatteryOptimizationDialog = MutableStateFlow(false)
    val shouldShowBatteryOptimizationDialog: StateFlow<Boolean> = _shouldShowBatteryOptimizationDialog.asStateFlow()
    private val _deprecatedModelWarning = MutableStateFlow<ModelManager.ModelWarning?>(null)
    val deprecatedModelWarning: StateFlow<ModelManager.ModelWarning?> = _deprecatedModelWarning.asStateFlow()
    private val _isLoadingImages = MutableStateFlow(false)
    val isLoadingImages: StateFlow<Boolean> = _isLoadingImages.asStateFlow()
    private val _loadingImagesProgress = MutableStateFlow<Pair<Int, Int>?>(null)
    val loadingImagesProgress: StateFlow<Pair<Int, Int>?> = _loadingImagesProgress.asStateFlow()
    private val _isSavingImages = MutableStateFlow(false)
    val isSavingImages: StateFlow<Boolean> = _isSavingImages.asStateFlow()
    private val _savingImagesProgress = MutableStateFlow<Pair<Int, Int>?>(null)
    val savingImagesProgress: StateFlow<Pair<Int, Int>?> = _savingImagesProgress.asStateFlow()
    private var activeProcessingTotal = 0
    private val processingQueue = mutableListOf<String>()
    private var isProcessingQueue = false
    private var cancelInProgress = false
    private var currentProcessingId: String? = null
    private var currentPhotoUri: Uri? = null
    private var imagePickerLauncher: ActivityResultLauncher<Intent>? = null
    private var serviceProcessPid: Int? = null
    private var isInitialized = false
    
    companion object {
        private const val PREFS_NAME = "ProcessingPrefs"
        private const val KEY_CHUNK_SIZE = "chunk_size"
        private const val KEY_OVERLAP_SIZE = "overlap_size"
        private const val KEY_GLOBAL_STRENGTH = "global_strength"
    }

    private val STATUS_PREPARING get() = appContext?.getString(com.je.dejpeg.R.string.status_preparing) ?: "Preparing..."
    private val STATUS_COMPLETE get() = appContext?.getString(com.je.dejpeg.R.string.status_complete) ?: "Complete"
    private val STATUS_CANCELLED get() = appContext?.getString(com.je.dejpeg.R.string.status_cancelled) ?: "Cancelled"
    private val STATUS_CANCELING get() = appContext?.getString(com.je.dejpeg.R.string.status_canceling) ?: "Canceling..."
    private val STATUS_QUEUED get() = appContext?.getString(com.je.dejpeg.R.string.status_queued) ?: "queued"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private fun getImageById(id: String) = _images.value.find { it.id == id }
    private fun resetTimeEstimates(item: ImageItem) = item.copy(timeEstimateMillis = 0L, timeEstimateStartMillis = 0L)
    private fun launchPicker(intent: Intent) { imagePickerLauncher?.launch(intent) }

    fun setImagePickerLauncher(launcher: ActivityResultLauncher<Intent>) {
        imagePickerLauncher = launcher
    }

    fun launchGalleryPicker(context: Context) {
        launchPicker(
            Intent(Intent.ACTION_PICK).apply {
                setDataAndType(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, "image/*")
                putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            }
        )
    }

    fun launchInternalPhotoPicker(context: Context) {
        launchPicker(
            Intent(Intent.ACTION_GET_CONTENT).apply {
                type = "image/*"
                putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            }
        )
    }

    fun launchCamera(context: Context) {
        try {
            val tempFile = File.createTempFile(
                "JPEG_${System.currentTimeMillis()}_",
                ".jpg",
                context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
            )
            val photoURI = androidx.core.content.FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                tempFile
            )
            currentPhotoUri = photoURI
            
            imagePickerLauncher?.launch(
                Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
                    putExtra(MediaStore.EXTRA_OUTPUT, photoURI)
                }
            )
        } catch (e: IOException) {
            _uiState.value = ProcessingUiState.Error("Camera error: ${e.message}")
        }
    }

    fun launchDocumentsPicker(context: Context) {
        launchPicker(
            Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                type = "image/*"
                putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            }
        )
    }

    fun getCameraPhotoUri(): Uri? = currentPhotoUri

    fun clearCameraPhotoUri() {
        currentPhotoUri = null
    }

    fun initialize(context: Context) {
        if (isInitialized) return
        isInitialized = true
        modelManager = ModelManager(context)
        imageProcessor = ImageProcessor(context, modelManager!!)
        appContext = context.applicationContext
        loadProcessingPreferences(context)
        
        viewModelScope.launch {
            _installedModels.value = modelManager?.getInstalledModels() ?: emptyList()
            _hasCheckedModels.value = true
            if (_installedModels.value.isEmpty()) {
                _shouldShowNoModelDialog.value = true
            } else {
                val activeModel = modelManager?.getActiveModelName()
                activeModel?.let { modelName ->
                    val warning = modelManager?.getModelWarning(modelName)
                    _deprecatedModelWarning.value = warning
                }
            }
        }
        registerProcessingReceiver()
    }
    
    private fun loadProcessingPreferences(context: Context) {
        val p = prefs(context)
        _chunkSize.value = p.getInt(KEY_CHUNK_SIZE, ImageProcessor.DEFAULT_CHUNK_SIZE)
        _overlapSize.value = p.getInt(KEY_OVERLAP_SIZE, ImageProcessor.OVERLAP)
        _globalStrength.value = p.getFloat(KEY_GLOBAL_STRENGTH, 50f)
    }
    
    fun setChunkSize(size: Int) {
        _chunkSize.value = size
        appContext?.let { context ->
            val p = prefs(context)
            p.edit()
                .putInt(KEY_CHUNK_SIZE, size)
                .commit()
            android.util.Log.d("ProcessingViewModel", "Saved $KEY_CHUNK_SIZE: $size, verified: ${p.getInt(KEY_CHUNK_SIZE, -1)}")
        }
    }
    
    fun setOverlapSize(size: Int) {
        _overlapSize.value = size
        appContext?.let { context ->
            val p = prefs(context)
            p.edit()
                .putInt(KEY_OVERLAP_SIZE, size)
                .commit()
            android.util.Log.d("ProcessingViewModel", "Saved $KEY_OVERLAP_SIZE: $size, verified: ${p.getInt(KEY_OVERLAP_SIZE, -1)}")
        }
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    private fun registerProcessingReceiver() {
        val filter = android.content.IntentFilter().apply {
            addAction(ProcessingService.PID_ACTION)
            addAction(ProcessingService.PROGRESS_ACTION)
            addAction(ProcessingService.TIME_ESTIMATE_ACTION)
            addAction(ProcessingService.COMPLETE_ACTION)
            addAction(ProcessingService.ERROR_ACTION)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            appContext?.registerReceiver(processingReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            appContext?.registerReceiver(processingReceiver, filter)
        }
    }

    override fun onCleared() {
        super.onCleared()
        try { appContext?.unregisterReceiver(processingReceiver) } catch (_: Exception) { }
    }

    private val processingReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: android.content.Context?, intent: android.content.Intent?) {
            val action = intent?.action ?: return
            val imageId = intent.getStringExtra(ProcessingService.EXTRA_IMAGE_ID)
            when (action) {
                ProcessingService.PID_ACTION -> intent.getIntExtra(ProcessingService.PID_EXTRA_VALUE, -1).takeIf { it != -1 }?.let { serviceProcessPid = it }
                ProcessingService.PROGRESS_ACTION -> intent.getStringExtra(ProcessingService.PROGRESS_EXTRA_MESSAGE)?.let { updateImageProcessingState(imageId ?: return, true, it) }
                ProcessingService.TIME_ESTIMATE_ACTION -> intent.getLongExtra(ProcessingService.TIME_ESTIMATE_EXTRA_MILLIS, 0L).let { timeEstimate -> updateImageState(imageId ?: return) { item -> item.copy(timeEstimateMillis = timeEstimate, timeEstimateStartMillis = System.currentTimeMillis()) } }
                ProcessingService.COMPLETE_ACTION -> handleComplete(intent.getStringExtra(ProcessingService.COMPLETE_EXTRA_PATH), imageId)
                ProcessingService.ERROR_ACTION -> handleError(intent.getStringExtra(ProcessingService.ERROR_EXTRA_MESSAGE) ?: "Error", imageId)
            }
        }
    }

    private fun handleComplete(path: String?, imageId: String?) {
        if (path == null || imageId.isNullOrEmpty()) return
        viewModelScope.launch {
            try {
                val bmp = withContext(Dispatchers.IO) { BitmapFactory.decodeFile(path) }
                if (bmp != null) updateImageOutput(imageId, bmp)
                else updateImageProcessingState(imageId, false, "Decode failed")
                updateImageState(imageId) {
                    it.copy(isProcessing = false, progress = STATUS_COMPLETE, timeEstimateMillis = 0L, timeEstimateStartMillis = 0L)
                }
            } catch (e: Exception) {
                updateImageProcessingState(imageId, false, "Error: ${e.message}")
            } finally {
                advanceOrIdle()
            }
        }
    }

    private fun handleError(message: String, imageId: String?) {
        val cancelled = message.contains(STATUS_CANCELLED, true)
        if (!imageId.isNullOrEmpty()) {
            updateImageProcessingState(imageId, false, if (cancelled) STATUS_CANCELLED else message)
            updateImageState(imageId) { resetTimeEstimates(it).copy(isCancelling = false) }
            processingQueue.remove(imageId)
        }
        if (cancelled) {
            cancelInProgress = false
            currentProcessingId = null
        }
        advanceOrIdle()
    }

    private fun advanceOrIdle() {
        if (processingQueue.isNotEmpty()) {
            isProcessingQueue = true
            viewModelScope.launch { processNextInQueue() }
        } else {
            isProcessingQueue = false
            currentProcessingId = null
            activeProcessingTotal = 0
            _uiState.value = ProcessingUiState.Idle
        }
    }

    fun refreshInstalledModels() {
        viewModelScope.launch {
            _installedModels.value = withContext(Dispatchers.IO) {
                modelManager?.getInstalledModels() ?: emptyList()
            }
        }
    }

    fun dismissNoModelDialog() {
        _shouldShowNoModelDialog.value = false
    }

    fun dismissDeprecatedModelWarning() {
        _deprecatedModelWarning.value = null
    }

    fun showNoModelDialog() {
        _shouldShowNoModelDialog.value = true
    }

    fun dismissBatteryOptimizationDialog() {
        _shouldShowBatteryOptimizationDialog.value = false
    }

    private fun showBatteryOptimizationDialog() {
        _shouldShowBatteryOptimizationDialog.value = true
    }

    fun importModel(
        context: Context, uri: Uri, force: Boolean = false,
        onProgress: (Int) -> Unit = {}, onSuccess: (String) -> Unit = {},
        onError: (String) -> Unit = {}, onWarning: ((String, ModelManager.ModelWarning) -> Unit)? = null
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                modelManager?.importModel(uri,
                    { launch(Dispatchers.Main) { onProgress(it) } },
                    {
                        modelManager?.setActiveModel(it)
                        refreshInstalledModels()
                        launch(Dispatchers.Main) { onSuccess(it) }
                    },
                    { launch(Dispatchers.Main) { onError(it) } },
                    onWarning?.let { cb -> { n, w -> launch(Dispatchers.Main) { cb(n, w) } } },
                    force
                )
            } catch (e: Exception) {
                launch(Dispatchers.Main) { onError(e.message ?: "Unknown error") }
            }
        }
    }

    fun deleteModels(models: List<String>, onDeleted: (String) -> Unit = {}) {
        viewModelScope.launch {
            models.forEach { name ->
                modelManager?.deleteModel(name)
                onDeleted(name)
            }
            refreshInstalledModels()
        }
    }

    fun setActiveModelByName(name: String) {
        modelManager?.setActiveModel(name)
    }

    fun addImage(item: ImageItem) {
        _images.value = _images.value + item
    }

    fun removeImage(id: String, force: Boolean = false) {
        val target = getImageById(id)
        if (target == null) {
            processingQueue.remove(id)
            return
        }
        val wasQueued = processingQueue.contains(id)
        if (!force && target.isProcessing && !target.isCancelling) {
            if (wasQueued && target.id != currentProcessingId) {
                processingQueue.remove(id)
                if (activeProcessingTotal > 0) activeProcessingTotal -= 1
                updateImageState(id) { resetTimeEstimates(it).copy(isProcessing = false, progress = "", isCancelling = false) }
                return
            }
            cancelInProgress = true
            updateImageState(id) { it.copy(isCancelling = true, progress = STATUS_CANCELING) }
            cancelProcessingService(id)
            return
        }
        if (wasQueued && activeProcessingTotal > 0) activeProcessingTotal -= 1
        processingQueue.remove(id)
        _images.value = _images.value.filter { it.id != id }
    }

    fun updateImageStrength(id: String, strength: Float) {
        updateImageState(id) { it.copy(strengthFactor = strength) }
    }
    fun setGlobalStrength(strength: Float) {
        _globalStrength.value = strength
        _images.value = _images.value.map { it.copy(strengthFactor = strength / 100f) }
        appContext?.let { context ->
            prefs(context).edit()
                .putFloat(KEY_GLOBAL_STRENGTH, strength)
                .apply()
        }
    }

    private suspend fun processImageInternal(image: ImageItem, index: Int, total: Int, strength: Float) {
        try {
            updateImageProcessingState(image.id, true, STATUS_PREPARING)
            imageProcessor?.processImage(image.inputBitmap, strength, object : ImageProcessor.ProcessCallback {
                override fun onComplete(result: Bitmap) {
                    updateImageOutput(image.id, result)
                    updateImageProcessingState(image.id, false, STATUS_COMPLETE)
                }
                override fun onError(error: String) {
                    updateImageProcessingState(image.id, false, error)
                    _uiState.value = ProcessingUiState.Error(error)
                }
                override fun onProgress(message: String) {
                    updateImageProcessingState(image.id, true, message)
                }
                override fun onTimeEstimate(timeRemaining: Long) {
                    updateImageState(image.id) {
                        it.copy(timeEstimateMillis = timeRemaining, timeEstimateStartMillis = System.currentTimeMillis())
                    }
                }
            }, index, total)
        } catch (e: Exception) {
            _uiState.value = ProcessingUiState.Error(e.message ?: "Unknown error")
        }
    }

    fun processImages() {
        viewModelScope.launch {
            if (cancelInProgress) return@launch
            val imagesToProcess = _images.value.filter { it.uri != null }
            if (imagesToProcess.isEmpty()) return@launch
            
            processingQueue.clear()
            processingQueue.addAll(imagesToProcess.map { it.id })
            activeProcessingTotal = processingQueue.size
            isProcessingQueue = true
            _uiState.value = ProcessingUiState.Processing(0, imagesToProcess.size)
            processNextInQueue()
        }
    }
    
    private fun processNextInQueue() {
        if (processingQueue.isEmpty()) {
            isProcessingQueue = false
            activeProcessingTotal = 0
            _uiState.value = ProcessingUiState.Idle
            currentProcessingId = null
            return
        }
        
        val imageId = processingQueue.removeAt(0)
        val image = _images.value.find { it.id == imageId } ?: return processNextInQueue()
        
        val total = if (activeProcessingTotal > 0) activeProcessingTotal else _images.value.count { it.uri != null }
        activeProcessingTotal = total
        val currentIndex = total - processingQueue.size - 1
        _uiState.value = ProcessingUiState.Processing(currentIndex, total)
        
        currentProcessingId = imageId
        isProcessingQueue = processingQueue.isNotEmpty()
        startProcessingInService(imageId, image.strengthFactor * 100f)
    }

    fun cancelProcessing() {
        processingQueue.clear()
        isProcessingQueue = false
        activeProcessingTotal = 0
        cancelInProgress = true
        currentProcessingId?.let { 
            updateImageState(it) { img -> img.copy(isCancelling = true, progress = STATUS_CANCELING) }
            cancelProcessingService(it)
        }
        _images.value.filter { it.isProcessing && it.id != currentProcessingId }.forEach { image ->
            updateImageState(image.id) { resetTimeEstimates(it).copy(isProcessing = false, progress = "") }
        }
        _uiState.value = ProcessingUiState.Idle
    }

    private fun updateImageProcessingState(id: String, isProcessing: Boolean, progress: String) {
        updateImageState(id) { it.copy(isProcessing = isProcessing, progress = progress) }
    }

    private fun updateImageOutput(id: String, output: Bitmap) {
        updateImageState(id) { it.copy(outputBitmap = output, isProcessing = false) }
    }

    private fun updateImageState(id: String, transform: (ImageItem) -> ImageItem) {
        _images.value = _images.value.map { if (it.id == id) transform(it) else it }
    }

    fun markImageAsSaved(imageId: String) {
        updateImageState(imageId) { it.copy(hasBeenSaved = true) }
    }

    fun clearAll() {
        _images.value = emptyList()
        _uiState.value = ProcessingUiState.Idle
    }

    fun hasActiveModel(): Boolean = modelManager?.hasActiveModel() ?: false

    fun getActiveModelName(): String? = modelManager?.getActiveModelName()

    fun getModelWarning(modelName: String?): com.je.dejpeg.ModelManager.ModelWarning? = 
        modelManager?.getModelWarning(modelName)

    fun supportsStrengthAdjustment(): Boolean {
        val modelName = getActiveModelName()
        return modelName?.contains("fbcnn", ignoreCase = true) == true
    }

    fun saveImage(context: Context, imageId: String, filename: String? = null, onSuccess: () -> Unit = {}, onError: (String) -> Unit = {}) {
        val image = _images.value.find { it.id == imageId }
        image?.outputBitmap?.let { bitmap ->
            ImageActions.saveImage(
                context = context,
                bitmap = bitmap,
                filename = filename ?: image.filename,
                onSuccess = {
                    updateImageState(imageId) { it.copy(hasBeenSaved = true) }
                    onSuccess()
                },
                onError = { errorMsg ->
                    onError(errorMsg)
                }
            )
        } ?: run {
            onError("Image not found or has no output")
        }
    }

    fun shareImage(context: Context, imageId: String) {
        val image = _images.value.find { it.id == imageId }
        image?.outputBitmap?.let { bitmap ->
            ImageActions.shareImage(context, bitmap)
        }
    }

    fun saveAllImages(context: Context, onComplete: () -> Unit = {}, onError: (String) -> Unit = {}) {
        val imagesToSave = _images.value.filter { it.outputBitmap != null }.map { it.filename to it.outputBitmap!! }
        if (imagesToSave.isNotEmpty()) {
            _isSavingImages.value = true
            _savingImagesProgress.value = Pair(0, imagesToSave.size)
            ImageActions.saveAllImages(
                context = context,
                images = imagesToSave,
                onProgress = { current, total ->
                    _savingImagesProgress.value = Pair(current, total)
                },
                onComplete = {
                    _images.value.filter { it.outputBitmap != null }.forEach { image ->
                        updateImageState(image.id) { it.copy(hasBeenSaved = true) }
                    }
                    _isSavingImages.value = false
                    _savingImagesProgress.value = null
                    onComplete()
                },
                onError = { errorMsg ->
                    _isSavingImages.value = false
                    _savingImagesProgress.value = null
                    onError(errorMsg)
                }
            )
        }
    }

    fun startProcessingInService(imageId: String, strength: Float? = null) {
        val ctx = appContext ?: return
        val image = getImageById(imageId) ?: return
        val uriStr = image.uri?.toString() ?: return
        updateImageState(imageId) { it.copy(isProcessing = true, progress = STATUS_PREPARING, timeEstimateMillis = 0L, timeEstimateStartMillis = 0L) }
        com.je.dejpeg.NotificationHelper.show(ctx, STATUS_PREPARING)
        val p = prefs(ctx)
        val chunkSize = p.getInt(KEY_CHUNK_SIZE, ImageProcessor.DEFAULT_CHUNK_SIZE)
        val overlapSize = p.getInt(KEY_OVERLAP_SIZE, ImageProcessor.OVERLAP)
        startService(ctx, ProcessingService.ACTION_PROCESS) {
            putExtra(ProcessingService.EXTRA_URI, uriStr)
            putExtra(ProcessingService.EXTRA_FILENAME, image.filename)
            putExtra(ProcessingService.EXTRA_IMAGE_ID, imageId)
            strength?.let { putExtra(ProcessingService.EXTRA_STRENGTH, it) }
            putExtra(ProcessingService.EXTRA_CHUNK_SIZE, chunkSize)
            putExtra(ProcessingService.EXTRA_OVERLAP_SIZE, overlapSize)
        }
    }

    fun cancelProcessingService(imageId: String? = null) {
        val ctx = appContext ?: return
        val pid = serviceProcessPid
        com.je.dejpeg.NotificationHelper.cancel(ctx)
        if (pid != null && pid > 0) {
            android.util.Log.d("ProcessingViewModel", "Killing service process with PID: $pid")
            try {
                android.os.Process.killProcess(pid)
                serviceProcessPid = null
                val targetImageId = imageId ?: currentProcessingId
                if (targetImageId != null) handleError(STATUS_CANCELLED, targetImageId)
            } catch (e: Exception) {
                android.util.Log.e("ProcessingViewModel", "Failed to kill service process: ${e.message}")
                val targetImageId = imageId ?: currentProcessingId
                if (targetImageId != null) {
                    handleError("Cancel failed: ${e.message}", targetImageId)
                }
            }
        } else {
            android.util.Log.w("ProcessingViewModel", "No valid service PID available for cancellation")
        }
    }

    private fun startService(ctx: Context, action: String, intentConfig: Intent.() -> Unit) {
        val intent = Intent(ctx, ProcessingService::class.java).apply {
            this.action = action
            intentConfig()
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                androidx.core.content.ContextCompat.startForegroundService(ctx, intent)
            } else {
                ctx.startService(intent)
            }
        } catch (e: Exception) {
            android.util.Log.e("ProcessingViewModel", "Failed to start service: ${e.javaClass.simpleName} - ${e.message}")
            if ((Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && e is android.app.ForegroundServiceStartNotAllowedException) ||
                (Build.VERSION.SDK_INT < Build.VERSION_CODES.S && (
                    e.javaClass.simpleName.contains("ForegroundServiceStartNotAllowed", true) ||
                    e.message?.contains("startForeground", true) == true ||
                    e.message?.contains("background", true) == true
                ))
            ) showBatteryOptimizationDialog()
            intent.getStringExtra(ProcessingService.EXTRA_IMAGE_ID)?.let {
                updateImageProcessingState(it, false, "Start failed: ${e.message}")
            }
        }
    }

    fun processImage(id: String) {
        viewModelScope.launch {
            if (cancelInProgress) return@launch
            val image = getImageById(id) ?: return@launch
            if (image.uri == null) return@launch

            val isProcessingActive = currentProcessingId != null || _images.value.any { it.isProcessing }
            if (isProcessingActive || processingQueue.isNotEmpty()) {
                if (id == currentProcessingId || processingQueue.contains(id)) return@launch
                processingQueue.add(id)
                isProcessingQueue = true
                activeProcessingTotal = if (activeProcessingTotal == 0) {
                    processingQueue.size + if (currentProcessingId != null) 1 else 0
                } else {
                    activeProcessingTotal + 1
                }
                val total = activeProcessingTotal
                val queuedProgress = STATUS_QUEUED
                updateImageState(id) { resetTimeEstimates(it).copy(isProcessing = true, progress = queuedProgress, isCancelling = false) }
                val currentIndex = (total - processingQueue.size - 1).coerceAtLeast(0)
                _uiState.value = ProcessingUiState.Processing(currentIndex, total)
                return@launch
            }
            
            activeProcessingTotal = 1
            _uiState.value = ProcessingUiState.Processing(0, 1)
            currentProcessingId = id
            startProcessingInService(id, image.strengthFactor * 100f)
        }
    }

    private fun loadBitmapWithRotation(context: Context, uri: Uri): Bitmap? = try {
        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            val bitmap = BitmapFactory.decodeStream(inputStream) ?: return null
            context.contentResolver.openInputStream(uri)?.use { exifStream ->
                val exif = ExifInterface(exifStream)
                when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> rotateBitmap(bitmap, 90f)
                    ExifInterface.ORIENTATION_ROTATE_180 -> rotateBitmap(bitmap, 180f)
                    ExifInterface.ORIENTATION_ROTATE_270 -> rotateBitmap(bitmap, 270f)
                    ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> flipBitmap(bitmap, true, false)
                    ExifInterface.ORIENTATION_FLIP_VERTICAL -> flipBitmap(bitmap, false, true)
                    ExifInterface.ORIENTATION_TRANSPOSE -> flipBitmap(rotateBitmap(bitmap, 90f), true, false)
                    ExifInterface.ORIENTATION_TRANSVERSE -> flipBitmap(rotateBitmap(bitmap, 270f), true, false)
                    else -> bitmap
                }
            } ?: bitmap
        }
    } catch (e: Exception) { null }

    private fun rotateBitmap(bitmap: Bitmap, degrees: Float): Bitmap {
        val matrix = Matrix().apply { postRotate(degrees) }
        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (rotated != bitmap) bitmap.recycle()
        return rotated
    }
    
    private fun flipBitmap(bitmap: Bitmap, horizontal: Boolean, vertical: Boolean): Bitmap {
        val matrix = Matrix().apply {
            postScale(
                if (horizontal) -1f else 1f,
                if (vertical) -1f else 1f,
                bitmap.width / 2f,
                bitmap.height / 2f
            )
        }
        val flipped = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (flipped != bitmap) bitmap.recycle()
        return flipped
    }

    fun addImageFromUri(context: Context, uri: Uri) {
        loadBitmapWithRotation(context, uri)?.let { bmp ->
            val thumbnail = generateThumbnail(bmp)
            addImage(
                ImageItem(
                    id = UUID.randomUUID().toString(),
                    uri = uri,
                    filename = getFileNameFromUri(context, uri),
                    inputBitmap = bmp,
                    thumbnailBitmap = thumbnail,
                    size = "${bmp.width}x${bmp.height}",
                    strengthFactor = _globalStrength.value / 100f
                )
            )
        }
    }
    
    fun addImagesFromUris(context: Context, uris: List<Uri>) {
        if (uris.isEmpty()) return
        
        viewModelScope.launch {
            _isLoadingImages.value = true
            _loadingImagesProgress.value = Pair(0, uris.size)
            
            withContext(Dispatchers.IO) {
                uris.forEachIndexed { index, uri ->
                    try {
                        loadBitmapWithRotation(context, uri)?.let { bmp ->
                            val thumbnail = generateThumbnail(bmp)
                            val imageItem = ImageItem(
                                id = UUID.randomUUID().toString(),
                                uri = uri,
                                filename = getFileNameFromUri(context, uri),
                                inputBitmap = bmp,
                                thumbnailBitmap = thumbnail,
                                size = "${bmp.width}x${bmp.height}",
                                strengthFactor = _globalStrength.value / 100f
                            )
                            withContext(Dispatchers.Main) {
                                addImage(imageItem)
                                _loadingImagesProgress.value = Pair(index + 1, uris.size)
                            }
                        }
                    } catch (e: Exception) {
                        // Silently skip failed images
                    }
                }
            }
            
            _isLoadingImages.value = false
            _loadingImagesProgress.value = null
        }
    }
    
    private fun generateThumbnail(bitmap: Bitmap): Bitmap {
        val thumbnailSize = 256
        val size = minOf(bitmap.width, bitmap.height)
        val x = (bitmap.width - size) / 2
        val y = (bitmap.height - size) / 2
        val croppedBitmap = Bitmap.createBitmap(size, size, bitmap.config ?: Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(croppedBitmap)
        canvas.drawBitmap(bitmap, -x.toFloat(), -y.toFloat(), null)
        val resizedBitmap = Bitmap.createScaledBitmap(croppedBitmap, thumbnailSize, thumbnailSize, true)
        croppedBitmap.recycle()
        return resizedBitmap
    }

    private fun getFileNameFromUri(context: Context, uri: Uri): String {
        return try {
            context.contentResolver.query(uri, null, null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(c.getColumnIndex(OpenableColumns.DISPLAY_NAME)) ?: "unknown"
                else uri.lastPathSegment ?: "unknown"
            } ?: uri.lastPathSegment ?: "unknown"
        } catch (_: Exception) {
            uri.lastPathSegment ?: "unknown"
        }
    }
}
