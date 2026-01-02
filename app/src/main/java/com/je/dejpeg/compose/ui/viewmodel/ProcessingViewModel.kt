package com.je.dejpeg.compose.ui.viewmodel

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.je.dejpeg.compose.ModelManager
import com.je.dejpeg.compose.ProcessingService
import com.je.dejpeg.compose.utils.CacheManager
import com.je.dejpeg.compose.utils.ImageActions
import com.je.dejpeg.compose.utils.helpers.ModelMigrationHelper
import com.je.dejpeg.compose.utils.helpers.ImageLoadingHelper
import com.je.dejpeg.compose.utils.helpers.ImagePickerHelper
import com.je.dejpeg.compose.utils.helpers.ModelRepository
import com.je.dejpeg.compose.utils.helpers.ServiceCommunicationHelper
import com.je.dejpeg.data.AppPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    val completedChunks: Int = 0,
    val totalChunks: Int = 0,
    val hasBeenSaved: Boolean = false
)

sealed class ProcessingUiState {
    object Idle : ProcessingUiState()
    data class Processing(val currentIndex: Int, val total: Int) : ProcessingUiState()
    data class Error(val message: String) : ProcessingUiState()
}
class ProcessingViewModel : ViewModel() {
    val images = MutableStateFlow<List<ImageItem>>(emptyList())
    val sharedUris = MutableStateFlow<List<Uri>>(emptyList())
    val uiState = MutableStateFlow<ProcessingUiState>(ProcessingUiState.Idle)
    val globalStrength = MutableStateFlow(AppPreferences.DEFAULT_GLOBAL_STRENGTH)
    val chunkSize = MutableStateFlow(AppPreferences.DEFAULT_CHUNK_SIZE)
    val overlapSize = MutableStateFlow(AppPreferences.DEFAULT_OVERLAP_SIZE)
    val installedModels = MutableStateFlow<List<String>>(emptyList())
    val hasCheckedModels = MutableStateFlow(false)
    val shouldShowNoModelDialog = MutableStateFlow(false)
    val deprecatedModelWarning = MutableStateFlow<ModelManager.ModelWarning?>(null)
    val isLoadingImages = MutableStateFlow(false)
    val loadingImagesProgress = MutableStateFlow<Pair<Int, Int>?>(null)
    val isSavingImages = MutableStateFlow(false)
    val savingImagesProgress = MutableStateFlow<Pair<Int, Int>?>(null)
    val processingErrorDialog = MutableStateFlow<String?>(null)

    private var appContext: Context? = null
    private var appPreferences: AppPreferences? = null
    private var modelRepository: ModelRepository? = null
    private var serviceHelper: ServiceCommunicationHelper? = null
    private var imagePickerHelper: ImagePickerHelper? = null
    private var activeProcessingTotal = 0
    private val processingQueue = mutableListOf<String>()
    private var isProcessingQueue = false
    private var cancelInProgress = false
    private var currentProcessingId: String? = null
    private var isInitialized = false
    private fun status(resId: Int, fallback: String) = appContext?.getString(resId) ?: fallback
    private val statusPreparing get() = status(com.je.dejpeg.R.string.status_preparing, "Preparing...")
    private val statusComplete get() = status(com.je.dejpeg.R.string.status_complete, "Complete")
    private val statusCancelled get() = status(com.je.dejpeg.R.string.status_cancelled, "Cancelled")
    private val statusCanceling get() = status(com.je.dejpeg.R.string.status_canceling, "Canceling...")
    private val statusQueued get() = status(com.je.dejpeg.R.string.status_queued, "queued")

    fun initialize(context: Context) {
        if (isInitialized) return
        isInitialized = true
        val appCtx = context.applicationContext
        appContext = appCtx
        appPreferences = AppPreferences(appCtx)
        modelRepository = ModelRepository(context)
        imagePickerHelper = ImagePickerHelper(context)
        serviceHelper = ServiceCommunicationHelper(appCtx, createServiceCallbacks())
        serviceHelper?.register()
        collectPreferences()
        loadInitialModels()
    }

    private fun collectPreferences() {
        viewModelScope.launch { appPreferences?.chunkSize?.collect { chunkSize.value = it } }
        viewModelScope.launch { appPreferences?.overlapSize?.collect { overlapSize.value = it } }
        viewModelScope.launch { appPreferences?.globalStrength?.collect { globalStrength.value = it } }
    }

    private fun loadInitialModels() {
        viewModelScope.launch {
            appContext?.let { ctx -> 
                ModelMigrationHelper.migrateModelsIfNeeded(ctx) // attempt to migrate and set previous model
            }
            installedModels.value = modelRepository?.getInstalledModels() ?: emptyList()
            hasCheckedModels.value = true
            if (installedModels.value.isEmpty()) {
                shouldShowNoModelDialog.value = true
            } else {
                // should not do this, i dont want inconsistent selection
                // if (!hasActiveModel() && installedModels.value.isNotEmpty()) {
                //     modelRepository?.setActiveModel(installedModels.value.first())
                // }
                modelRepository?.getActiveModelName()?.let { modelName ->
                    deprecatedModelWarning.value = modelRepository?.getModelWarning(modelName)
                }
            }
        }
    }

    private fun createServiceCallbacks() = object : ServiceCommunicationHelper.ServiceCallbacks {
        override fun onPidReceived(pid: Int) {
            Log.d("ProcessingViewModel", "Service PID received: $pid")
        }
        override fun onProgress(imageId: String, message: String) {
            updateImageState(imageId) { it.copy(isProcessing = true, progress = message) }
        }
        override fun onChunkProgress(imageId: String, completedChunks: Int, totalChunks: Int) {
            updateImageState(imageId) { item ->
                item.copy(
                    completedChunks = completedChunks,
                    totalChunks = totalChunks
                )
            }
        }
        override fun onComplete(imageId: String, path: String) {
            handleProcessingComplete(imageId, path)
        }
        override fun onError(imageId: String?, message: String) {
            handleProcessingError(imageId, message)
        }
    }

    override fun onCleared() {
        super.onCleared()
        serviceHelper?.unregister()
    }

    fun serviceHelperRegister() {
        serviceHelper?.register()
    }

    fun setImagePickerLauncher(launcher: ActivityResultLauncher<Intent>) {
        imagePickerHelper?.setLauncher(launcher)
    }

    fun launchGalleryPicker() = imagePickerHelper?.launchGalleryPicker()
    fun launchInternalPhotoPicker() = imagePickerHelper?.launchInternalPhotoPicker()
    fun launchDocumentsPicker() = imagePickerHelper?.launchDocumentsPicker()
    fun launchCamera() {
        imagePickerHelper?.launchCamera()?.onFailure { e ->
            uiState.value = ProcessingUiState.Error("Camera error: ${e.message}")
        }
    }
    fun getCameraPhotoUri(): Uri? = imagePickerHelper?.getCameraPhotoUri()
    fun clearCameraPhotoUri() = imagePickerHelper?.clearCameraPhotoUri()
    fun addImage(item: ImageItem) {
        images.value += item
    }

    fun addSharedUri(uri: Uri) {
        if (sharedUris.value.any { it == uri }) return
        sharedUris.value = sharedUris.value + uri
    }

    fun addImagesFromUris(context: Context, uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            isLoadingImages.value = true
            loadingImagesProgress.value = Pair(0, uris.size)

            withContext(Dispatchers.IO) {
                uris.forEachIndexed { index, uri ->
                    try {
                        ImageLoadingHelper.loadBitmapWithRotation(context, uri)?.let { bmp ->
                            val imageItem = createImageItem(context, uri, bmp)
                            withContext(Dispatchers.Main) {
                                addImage(imageItem)
                                loadingImagesProgress.value = Pair(index + 1, uris.size)
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("ProcessingViewModel", "Failed to load image: $uri - ${e.message}")
                    }
                }
            }

            isLoadingImages.value = false
            loadingImagesProgress.value = null
        }
    }

    private fun createImageItem(context: Context, uri: Uri, bitmap: Bitmap) = ImageItem(
        id = UUID.randomUUID().toString(),
        uri = uri,
        filename = ImageLoadingHelper.getFileNameFromUri(context, uri),
        inputBitmap = bitmap,
        thumbnailBitmap = ImageLoadingHelper.generateThumbnail(bitmap),
        size = "${bitmap.width}x${bitmap.height}",
        strengthFactor = globalStrength.value / 100f
    )

    fun removeImage(id: String, force: Boolean = false, cleanupCache: Boolean = false) {
        val target = getImageById(id) ?: run {
            processingQueue.remove(id)
            return
        }
        if (!force && target.isProcessing && !target.isCancelling) {
            if (processingQueue.contains(id) && id != currentProcessingId) {
                processingQueue.remove(id)
                if (activeProcessingTotal > 0) activeProcessingTotal--
                updateImageState(id) { resetChunkProgress(it).copy(isProcessing = false, progress = "", isCancelling = false) }
                return
            }
            cancelInProgress = true
            updateImageState(id) { it.copy(isCancelling = true, progress = statusCanceling) }
            cancelProcessingService(id)
            return
        }
        if (processingQueue.contains(id) && activeProcessingTotal > 0) activeProcessingTotal--
        processingQueue.remove(id)
        images.value = images.value.filter { it.id != id }
        if (cleanupCache) {
            appContext?.let { ctx ->
                viewModelScope.launch {
                    Log.d("ProcessingViewModel", "removeImage: Cleaning up cache for imageId: $id")
                    CacheManager.deleteRecoveryPair(ctx, id)
                    }
            }
        }
    }

    private fun getImageById(id: String) = images.value.find { it.id == id }

    private fun updateImageState(id: String, transform: (ImageItem) -> ImageItem) {
        images.value = images.value.map { if (it.id == id) transform(it) else it }
    }

    private fun resetChunkProgress(item: ImageItem) = item.copy(
        completedChunks = 0,
        totalChunks = 0
    )

    fun processImages() {
        viewModelScope.launch {
            if (cancelInProgress) return@launch
            val imagesToProcess = images.value.filter { it.uri != null }
            if (imagesToProcess.isEmpty()) return@launch

            processingQueue.clear()
            processingQueue.addAll(imagesToProcess.map { it.id })
            activeProcessingTotal = processingQueue.size
            isProcessingQueue = true

            imagesToProcess.forEach { image ->
                updateImageState(image.id) {
                    resetChunkProgress(it).copy(isProcessing = true, progress = statusQueued, isCancelling = false)
                }
            }

            uiState.value = ProcessingUiState.Processing(0, imagesToProcess.size)
            processNextInQueue()
        }
    }

    fun processImage(id: String) {
        viewModelScope.launch {
            val image = getImageById(id) ?: return@launch
            if (image.uri == null) return@launch
            if (cancelInProgress || currentProcessingId != null || processingQueue.isNotEmpty()) {
                if (id == currentProcessingId || processingQueue.contains(id)) return@launch
                queueImage(id)
                return@launch
            }

            activeProcessingTotal = 1
            uiState.value = ProcessingUiState.Processing(0, 1)
            currentProcessingId = id
            startProcessingImage(id, image.strengthFactor * 100f)
        }
    }

    private fun queueImage(id: String) {
        processingQueue.add(id)
        isProcessingQueue = true
        activeProcessingTotal = maxOf(
            activeProcessingTotal,
            processingQueue.size + if (currentProcessingId != null) 1 else 0
        )
        updateImageState(id) {
            resetChunkProgress(it).copy(isProcessing = true, progress = statusQueued, isCancelling = false)
        }
        val currentIndex = (activeProcessingTotal - processingQueue.size - 1).coerceAtLeast(0)
        uiState.value = ProcessingUiState.Processing(currentIndex, activeProcessingTotal)
    }

    private fun processNextInQueue() {
        if (processingQueue.isEmpty()) {
            resetProcessingState()
            return
        }

        val imageId = processingQueue.removeAt(0)
        val image = images.value.find { it.id == imageId } ?: return processNextInQueue()

        val total = if (activeProcessingTotal > 0) activeProcessingTotal else images.value.count { it.uri != null }
        activeProcessingTotal = total
        val currentIndex = total - processingQueue.size - 1
        uiState.value = ProcessingUiState.Processing(currentIndex, total)

        currentProcessingId = imageId
        isProcessingQueue = processingQueue.isNotEmpty()
        startProcessingImage(imageId, image.strengthFactor * 100f)
    }

    private fun startProcessingImage(imageId: String, strength: Float) {
        val ctx = appContext ?: return
        val image = getImageById(imageId) ?: return
        val uriString = image.uri?.toString() ?: return

        updateImageState(imageId) {
            it.copy(isProcessing = true, progress = statusPreparing, completedChunks = 0, totalChunks = 0)
        }
        viewModelScope.launch {
            CacheManager.saveUnprocessedImage(ctx, imageId, image.inputBitmap)
        }
        serviceHelper?.startProcessing(
            imageId = imageId,
            uriString = uriString,
            filename = image.filename,
            strength = strength,
            chunkSize = chunkSize.value,
            overlapSize = overlapSize.value,
            modelName = modelRepository?.getActiveModelName()
        )
    }

    fun cancelProcessing() {
        processingQueue.clear()
        isProcessingQueue = false
        activeProcessingTotal = 0
        cancelInProgress = true

        currentProcessingId?.let {
            updateImageState(it) { img -> img.copy(isCancelling = true, progress = statusCanceling) }
            cancelProcessingService(it)
        }

        images.value.filter { it.isProcessing && it.id != currentProcessingId }.forEach { image ->
            updateImageState(image.id) { resetChunkProgress(it).copy(isProcessing = false, progress = "") }
        }

        uiState.value = ProcessingUiState.Idle
    }

    private fun cancelProcessingService(imageId: String?) {
        val targetImageId = imageId ?: currentProcessingId
        val ctx = appContext ?: return

        serviceHelper?.cancelProcessing {
            viewModelScope.launch(Dispatchers.IO) {
                CacheManager.clearChunks(ctx)
            }
        }

        if (targetImageId != null && targetImageId == currentProcessingId) {
            currentProcessingId = null
        }
        if (targetImageId != null) {
            handleProcessingError(targetImageId, statusCancelled)
        }
    }

    private fun handleProcessingComplete(imageId: String, path: String) {
        viewModelScope.launch {
            try {
                val bitmap = withContext(Dispatchers.IO) { BitmapFactory.decodeFile(path) }
                if (bitmap != null) {
                    // Save to cache for recovery if app closes before saving
                    CacheManager.saveProcessedImage(appContext!!, imageId, bitmap)
                    
                    updateImageState(imageId) {
                        it.copy(
                            outputBitmap = bitmap,
                            isProcessing = false,
                            progress = statusComplete,
                            completedChunks = 0,
                            totalChunks = 0
                        )
                    }
                } else {
                    updateImageState(imageId) { it.copy(isProcessing = false, progress = "Decode failed", completedChunks = 0, totalChunks = 0) }
                }
            } catch (e: Exception) {
                updateImageState(imageId) { it.copy(isProcessing = false, progress = "${e.message}", completedChunks = 0, totalChunks = 0) }
            } finally {
                advanceQueue(imageId)
            }
        }
    }

    private fun handleProcessingError(imageId: String?, message: String) {
        val isCancelled = message.contains(statusCancelled, true)
        if (!imageId.isNullOrEmpty()) {
            updateImageState(imageId) {
                resetChunkProgress(it).copy(
                    isProcessing = false,
                    progress = if (isCancelled) statusCancelled else message,
                    isCancelling = false
                )
            }
            processingQueue.remove(imageId)
            if (isCancelled) {
                appContext?.let { ctx ->
                    viewModelScope.launch(Dispatchers.IO) {
                        Log.d("ProcessingViewModel", "handleProcessingError: Cleaning up cache for cancelled imageId: $imageId")
                        CacheManager.deleteRecoveryPair(ctx, imageId)
                    }
                }
            }
        }

        if (isCancelled) {
            cancelInProgress = false
            if (imageId == currentProcessingId) currentProcessingId = null
            advanceQueue(imageId)
            return
        }

        appContext?.let { ctx ->
            val stopIntent = Intent(ctx, ProcessingService::class.java)
            stopIntent.action = ProcessingService.ACTION_CANCEL
            ctx.startService(stopIntent)
        }
        
        processingQueue.clear()
        isProcessingQueue = false
        activeProcessingTotal = 0
        currentProcessingId = null
        cancelInProgress = false

        images.value = images.value.map {
            if (it.isProcessing || it.isCancelling) {
                it.copy(isProcessing = false, isCancelling = false, progress = "")
            } else it
        }
        
        processingErrorDialog.value = message
        uiState.value = ProcessingUiState.Idle
    }

    private fun advanceQueue(completedImageId: String? = null) {
        if (processingQueue.isNotEmpty()) {
            isProcessingQueue = true
            viewModelScope.launch { processNextInQueue() }
        } else {
            if (currentProcessingId == null || completedImageId == null || completedImageId == currentProcessingId) {
                resetProcessingState()
            }
        }
    }

    private fun resetProcessingState() {
        isProcessingQueue = false
        activeProcessingTotal = 0
        currentProcessingId = null
        uiState.value = ProcessingUiState.Idle
    }

    fun isCurrentlyProcessing(imageId: String) = currentProcessingId == imageId
    
    fun cancelProcessingForImage(imageId: String) {
        cancelInProgress = true
        updateImageState(imageId) { it.copy(isCancelling = true, progress = statusCanceling) }
        cancelProcessingService(imageId)
    }

    fun updateImageStrength(id: String, strength: Float) {
        updateImageState(id) { it.copy(strengthFactor = strength) }
    }

    fun setGlobalStrength(strength: Float) {
        globalStrength.value = strength
        images.value = images.value.map { it.copy(strengthFactor = strength / 100f) }
        viewModelScope.launch { appPreferences?.setGlobalStrength(strength) }
    }

    fun setChunkSize(size: Int) {
        chunkSize.value = size
        viewModelScope.launch { appPreferences?.setChunkSize(size) }
    }

    fun setOverlapSize(size: Int) {
        overlapSize.value = size
        viewModelScope.launch { appPreferences?.setOverlapSize(size) }
    }

    fun refreshInstalledModels() {
        viewModelScope.launch {
            installedModels.value = modelRepository?.getInstalledModels() ?: emptyList()
        }
    }

    fun importModel(
        uri: Uri,
        force: Boolean = false,
        onProgress: (Int) -> Unit = {},
        onSuccess: (String) -> Unit = {},
        onError: (String) -> Unit = {},
        onWarning: ((String, ModelManager.ModelWarning) -> Unit)? = null
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            modelRepository?.importModel(
                uri = uri,
                force = force,
                onProgress = { launch(Dispatchers.Main) { onProgress(it) } },
                onSuccess = {
                    refreshInstalledModels()
                    launch(Dispatchers.Main) { onSuccess(it) }
                },
                onError = { launch(Dispatchers.Main) { onError(it) } },
                onWarning = onWarning?.let { cb -> { n, w -> launch(Dispatchers.Main) { cb(n, w) } } }
            )
        }
    }

    fun deleteModels(models: List<String>, onDeleted: (String) -> Unit = {}) {
        viewModelScope.launch {
            modelRepository?.deleteModels(models, onDeleted)
            refreshInstalledModels()
        }
    }

    fun setActiveModelByName(name: String) {
        modelRepository?.setActiveModel(name) ?: Log.e("ProcessingViewModel", "modelRepository is null!")
    }
    fun hasActiveModel() = modelRepository?.hasActiveModel() ?: false
    fun getActiveModelName() = modelRepository?.getActiveModelName()
    fun getModelWarning(modelName: String?) = modelRepository?.getModelWarning(modelName)
    fun supportsStrengthAdjustment() = modelRepository?.supportsStrengthAdjustment() ?: false

    fun showNoModelDialog() { shouldShowNoModelDialog.value = true }
    fun dismissNoModelDialog() { shouldShowNoModelDialog.value = false }
    fun dismissDeprecatedModelWarning() { deprecatedModelWarning.value = null }
    fun dismissProcessingErrorDialog() { processingErrorDialog.value = null }

    fun markImageAsSaved(imageId: String) {
        updateImageState(imageId) { it.copy(hasBeenSaved = true) }
    }

    fun saveImage(
        context: Context,
        imageId: String,
        filename: String? = null,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        val image = getImageById(imageId)
        val bitmap = image?.outputBitmap

        if (bitmap == null) {
            onError("Image not found or has no output")
            return
        }

        ImageActions.saveImage(
            context = context,
            bitmap = bitmap,
            filename = filename ?: image.filename,
            imageId = imageId,
            onSuccess = {
                updateImageState(imageId) { it.copy(hasBeenSaved = true) }
                onSuccess()
            },
            onError = onError
        )
    }
    fun saveAllImages(
        context: Context,
        onComplete: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        val imagesToSave = images.value.filter { it.outputBitmap != null }
        val imagesData = imagesToSave.map { it.filename to it.outputBitmap!! }

        if (imagesData.isEmpty()) return

        isSavingImages.value = true
        savingImagesProgress.value = Pair(0, imagesData.size)

        ImageActions.saveAllImages(
            context = context,
            images = imagesData,
            onProgress = { current, total ->
                savingImagesProgress.value = Pair(current, total)
            },
            onComplete = {
                viewModelScope.launch {
                    imagesToSave.forEach { image ->
                        updateImageState(image.id) { it.copy(hasBeenSaved = true) }
                    }
                }
                isSavingImages.value = false
                savingImagesProgress.value = null
                onComplete()
            },
            onError = { errorMsg ->
                isSavingImages.value = false
                savingImagesProgress.value = null
                onError(errorMsg)
            }
        )
    }
}
