/*
 * SPDX-FileCopyrightText: 2025 - 2026 dryerlint <https://codeberg.org/dryerlint>
 * SPDX-License-Identifier: GNU Affero General Public License v3.0 or later
 */

package com.je.dejpeg.ui.processing

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import androidx.compose.runtime.Immutable
import androidx.core.net.toUri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.je.dejpeg.R
import com.je.dejpeg.data.AppPreferences
import com.je.dejpeg.data.ImageRepository
import com.je.dejpeg.processing.ProcessingService
import com.je.dejpeg.processing.ServiceCommunicationHelper
import com.je.dejpeg.ui.brisque.SaveState
import com.je.dejpeg.ui.components.SnackbarController
import com.je.dejpeg.ui.components.SnackbarDuration
import com.je.dejpeg.ui.components.SnackbarEvents
import com.je.dejpeg.ui.settings.SettingsViewModel
import com.je.dejpeg.utils.CacheManager
import com.je.dejpeg.utils.ImageActions
import com.je.dejpeg.utils.ImagePickerHelper
import com.je.dejpeg.utils.ModelManager
import com.je.dejpeg.utils.ModelType
import com.je.dejpeg.utils.ProcessingQueueManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.time.Duration.Companion.milliseconds

@Immutable
data class ImageItem(
    val id: String,
    val uri: Uri?,
    val filename: String,
    val inputBitmap: Bitmap,
    val outputBitmap: Bitmap? = null,
    val thumbnailBitmap: Bitmap? = null,
    val size: String,
    val isProcessing: Boolean = false,
    val progress: String = "",
    val isCancelling: Boolean = false,
    val completedChunks: Int = 0,
    val totalChunks: Int = 0,
    val hasBeenSaved: Boolean = false,
    val isOutputStale: Boolean = false
) {
    val cardState: CardState
        get() = when {
            isProcessing -> CardState.Processing
            outputBitmap != null && isOutputStale -> CardState.Stale
            outputBitmap != null -> CardState.Complete
            else -> CardState.Idle
        }

    /** Chunk progress in 0..1, or -1 when the output is not chunked enough to show a bar. */
    val chunkProgress: Float
        get() = if (totalChunks > 1) {
            completedChunks.toFloat() / totalChunks.coerceAtLeast(1)
        } else {
            -1f
        }
}

enum class CardState { Idle, Processing, Complete, Stale }

sealed class ProcessingUiState {
    data object Idle : ProcessingUiState()
    data class Processing(val currentIndex: Int, val total: Int) : ProcessingUiState()
    data class Error(val message: String) : ProcessingUiState()
}

/** The source an import comes from. Persisted values are kept stable for existing prefs. */
enum class ImageSource(val preferenceValue: String) {
    Gallery("gallery"),
    Internal("internal"),
    Documents("documents"),
    Camera("camera");

    companion object {
        fun fromPreference(value: String?): ImageSource? =
            entries.firstOrNull { it.preferenceValue == value }
    }
}

sealed interface ProcessingDialog {
    data class CancelImage(val imageId: String, val filename: String) : ProcessingDialog
    data object CancelAll : ProcessingDialog
    data object ImageSource : ProcessingDialog
    data class ProcessingError(val message: String) : ProcessingDialog
    data class SaveError(val message: String) : ProcessingDialog
    data object GpuCacheCreating : ProcessingDialog
    data class SaveOptions(
        val imageIds: List<String>,
        val defaultFilename: String,
        val removeAfter: Boolean = false,
    ) : ProcessingDialog

    data class ConfirmOverwrite(
        val imageIds: List<String>,
        val filename: String,
        val removeAfter: Boolean = false,
    ) : ProcessingDialog

    data class ConfirmRemoval(val imageIds: List<String>, val filename: String?) : ProcessingDialog
}

class ProcessingViewModel(
    application: Application,
    private val imageRepository: ImageRepository,
    private val settingsViewModel: SettingsViewModel,
    private val savedStateHandle: SavedStateHandle,
) : AndroidViewModel(application) {
    companion object {
        private const val CANCEL_WATCHDOG_TIMEOUT_MS = 3000L
        private const val KEY_CAMERA_PHOTO_URI = "camera_photo_uri"

        fun factory(settingsViewModel: SettingsViewModel): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    val application = checkNotNull(
                        this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                    )
                    ProcessingViewModel(
                        application = application,
                        imageRepository = ImageRepository.getInstance(),
                        settingsViewModel = settingsViewModel,
                        savedStateHandle = createSavedStateHandle(),
                    )
                }
            }
    }

    private val appContext = application.applicationContext
    private val appPreferences = AppPreferences()
    private val queue = ProcessingQueueManager()

    private val serviceHelper = ServiceCommunicationHelper(
        appContext, object : ServiceCommunicationHelper.ServiceCallbacks {
            override fun onPidReceived(pid: Int) {
                Log.d("ProcessingViewModel", "Service PID received: $pid")
            }

            override fun onProgress(imageId: String, message: String) {
                imageRepository.updateImageState(imageId) {
                    it.copy(
                        isProcessing = true, progress = message
                    )
                }
            }

            override fun onChunkProgress(
                imageId: String, completedChunks: Int, totalChunks: Int
            ) {
                imageRepository.updateImageState(imageId) { item ->
                    item.copy(
                        completedChunks = completedChunks, totalChunks = totalChunks
                    )
                }
            }

            override fun onComplete(imageId: String, path: String) {
                handleProcessingComplete(imageId, path)
            }

            override fun onError(imageId: String?, message: String) {
                handleProcessingError(imageId, message)
            }

            override fun onServiceCrash(imageId: String?, intentional: Boolean) {
                if (intentional) {
                    Log.d(
                        "ProcessingViewModel",
                        "Service process terminated intentionally (cancel) for image: $imageId"
                    )
                } else {
                    Log.e(
                        "ProcessingViewModel",
                        "Service process crashed while processing image: $imageId"
                    )
                }
                handleServiceCrash(imageId, intentional)
            }
        })

    private val _uiState = MutableStateFlow<ProcessingUiState>(ProcessingUiState.Idle)
    val uiState: StateFlow<ProcessingUiState> = _uiState.asStateFlow()

    private val _dialog = MutableStateFlow<ProcessingDialog?>(null)
    val dialog: StateFlow<ProcessingDialog?> = _dialog.asStateFlow()

    private val _saveState = MutableStateFlow<SaveState>(SaveState.Idle)
    val saveState: StateFlow<SaveState> = _saveState.asStateFlow()

    private val _selectedImageIds = MutableStateFlow<List<String>>(emptyList())
    val selectedImageIds: StateFlow<List<String>> = _selectedImageIds.asStateFlow()

    private val _pickerRequests = MutableSharedFlow<ImageSource>(extraBufferCapacity = 1)
    val pickerRequests: SharedFlow<ImageSource> = _pickerRequests.asSharedFlow()

    val images: StateFlow<List<ImageItem>> = imageRepository.images.asStateFlow()
    val isLoadingImages: StateFlow<Boolean> = imageRepository.isLoadingImages.asStateFlow()
    val loadingImagesProgress: StateFlow<Pair<Int, Int>?> =
        imageRepository.loadingImagesProgress.asStateFlow()

    val swapSwipeActions: StateFlow<Boolean> = callbackFlow {
        trySend(appPreferences.loadSwapSwipeActions())
        val prefs = appPreferences.prefs()
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == AppPreferences.KEY_SWAP_SWIPE_ACTIONS) {
                trySend(appPreferences.loadSwapSwipeActions())
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        appPreferences.loadSwapSwipeActions()
    )

    private var isAppVisible = false
    private var cancelWatchdogJob: Job? = null

    init {
        serviceHelper.register()
        observeSettingsForStaleOutputs()
        viewModelScope.launch {
            imageRepository.images.collect { images ->
                _selectedImageIds.update { selected ->
                    selected.filter { id -> images.any { it.id == id } }
                }
                // A cancel prompt is stale once its image stopped processing on its own.
                val current = _dialog.value
                if (current is ProcessingDialog.CancelImage &&
                    images.none { it.id == current.imageId && it.isProcessing }
                ) {
                    _dialog.value = null
                }
            }
        }
    }

    private fun status(resId: Int): String = appContext.getString(resId)
    private val statusPreparing get() = status(R.string.status_preparing)
    private val statusComplete get() = status(R.string.status_complete)
    private val statusCancelled get() = status(R.string.status_cancelled)
    private val statusCanceling get() = status(R.string.status_canceling)
    private val statusQueued get() = status(R.string.status_queued)
    private val statusNativeCrash get() = status(R.string.error_native_crash)

    /**
     * Shows [next]. A second dialog replaces the open one, except that background notices never
     * replace a prompt the user opened (image cancel or cancel-all).
     */
    private fun showDialog(next: ProcessingDialog) {
        val current = _dialog.value
        if (next is ProcessingDialog.GpuCacheCreating &&
            (current is ProcessingDialog.CancelImage || current is ProcessingDialog.CancelAll)
        ) {
            return
        }
        _dialog.value = next
    }

    fun dismissDialog() {
        _dialog.value = null
    }

    fun requestCancel(imageId: String) {
        val image = imageRepository.getImageById(imageId) ?: return
        showDialog(ProcessingDialog.CancelImage(imageId, image.filename))
    }

    fun requestCancelAll() {
        showDialog(ProcessingDialog.CancelAll)
    }

    fun confirmDialog() {
        when (val current = _dialog.value) {
            is ProcessingDialog.CancelImage -> {
                cancelQueuedImage(current.imageId)
                dismissDialog()
            }

            is ProcessingDialog.CancelAll -> {
                cancelProcessing()
                dismissDialog()
            }

            else -> dismissDialog()
        }
    }

    fun setAppVisible(visible: Boolean) {
        isAppVisible = visible
    }

    fun onToggleSelection(id: String) {
        _selectedImageIds.update { selected ->
            if (selected.contains(id)) selected - id else selected + id
        }
    }

    fun clearSelection() {
        _selectedImageIds.value = emptyList()
    }

    fun onImportClicked() {
        val source = ImageSource.fromPreference(appPreferences.loadDefaultImageSource())
        if (source == null) showDialog(ProcessingDialog.ImageSource) else onSourcePicked(source)
    }

    /** Dismisses the source sheet (if open) and asks the UI to launch that picker. */
    fun onSourcePicked(source: ImageSource) {
        if (_dialog.value == ProcessingDialog.ImageSource) _dialog.value = null
        _pickerRequests.tryEmit(source)
    }

    fun pickerIntent(source: ImageSource): Result<Intent> = when (source) {
        ImageSource.Gallery -> Result.success(ImagePickerHelper.galleryIntent())
        ImageSource.Internal -> Result.success(ImagePickerHelper.internalPhotoPickerIntent())
        ImageSource.Documents -> Result.success(ImagePickerHelper.documentsPickerIntent())
        ImageSource.Camera -> ImagePickerHelper.cameraIntent(appContext)
            .onSuccess { (_, uri) -> savedStateHandle[KEY_CAMERA_PHOTO_URI] = uri.toString() }
            .map { (intent, _) -> intent }
    }

    fun onPickerLaunchFailed(error: Throwable) {
        showDialog(
            ProcessingDialog.ProcessingError(
                appContext.getString(R.string.error_camera, error.message.orEmpty())
            )
        )
    }

    fun onPickerResult(resultCode: Int, data: Intent?) {
        val cameraUri = savedStateHandle.get<String>(KEY_CAMERA_PHOTO_URI)?.toUri()
        savedStateHandle.remove<String>(KEY_CAMERA_PHOTO_URI)
        if (resultCode != Activity.RESULT_OK) return
        val uris = buildList {
            val clipData = data?.clipData
            if (clipData != null) {
                for (i in 0 until clipData.itemCount) clipData.getItemAt(i).uri?.let(::add)
            } else {
                data?.data?.let(::add)
            }
            if (isEmpty() && cameraUri != null) add(cameraUri)
        }
        if (uris.isNotEmpty()) imageRepository.addImagesFromUris(appContext, uris)
    }

    fun onSharedUris(uris: List<Uri>) {
        if (uris.isEmpty()) return
        val existing = imageRepository.images.value.mapNotNull { it.uri?.toString() }.toSet()
        val toAdd = uris.filter { it.toString() !in existing }
        if (toAdd.isNotEmpty()) imageRepository.addImagesFromUris(appContext, toAdd)
        imageRepository.sharedUris.value = emptyList()
    }

    fun process(id: String? = null) {
        val mode = settingsViewModel.activeSelection.value.type
        if (!settingsViewModel.hasActiveModel(mode)) {
            viewModelScope.launch {
                SnackbarController.pushEvent(
                    SnackbarEvents.MessageEvent(
                        message = status(R.string.no_model_installed_title),
                        duration = SnackbarDuration.Long,
                    )
                )
            }
            return
        }
        val ids = when {
            id != null -> listOf(id)
            _selectedImageIds.value.isNotEmpty() -> _selectedImageIds.value
            else -> imageRepository.images.value.map { it.id }
        }
        processImages(ids)
    }

    fun processImages(ids: List<String>) {
        val valid = ids.filter { id -> imageRepository.getImageById(id)?.uri != null }
        if (valid.isEmpty()) return
        val queueBusy = queue.cancelInProgress ||
                queue.currentProcessingId != null ||
                !queue.isEmpty

        if (queueBusy) {
            val toEnqueue = valid.filterNot { queue.isActive(it) || queue.contains(it) }
            if (toEnqueue.isEmpty()) return
            toEnqueue.forEach { id ->
                queue.enqueue(id)
                markQueued(id)
            }
            queue.setActiveTotal(
                maxOf(
                    queue.activeProcessingTotal,
                    queue.queueSize + if (queue.currentProcessingId != null) 1 else 0
                )
            )
            _uiState.value =
                ProcessingUiState.Processing(queue.currentIndex(), queue.activeProcessingTotal)
            return
        }

        val first = valid.first()
        queue.setActiveTotal(valid.size)
        _uiState.value = ProcessingUiState.Processing(0, valid.size)
        queue.setCurrentProcessing(first)
        valid.drop(1).forEach { id ->
            queue.enqueue(id)
            markQueued(id)
        }
        startProcessingImage(first, settingsViewModel.globalStrength.value)
    }

    private fun markQueued(id: String) {
        imageRepository.updateImageState(id) {
            resetChunkProgress(it).copy(
                isProcessing = true, progress = statusQueued, isCancelling = false
            )
        }
    }

    private fun startCancelWatchdog(imageId: String?) {
        cancelWatchdogJob?.cancel()
        cancelWatchdogJob = viewModelScope.launch {
            delay(CANCEL_WATCHDOG_TIMEOUT_MS.milliseconds)
            if (queue.cancelInProgress) {
                Log.w(
                    "ProcessingViewModel",
                    "Cancel watchdog fired: no service callback for imageId=$imageId, force-resetting state"
                )
                resetProcessingState()
            }
        }
    }

    private fun cancelCancelWatchdog() {
        cancelWatchdogJob?.cancel()
        cancelWatchdogJob = null
    }

    private fun resetProcessingState() {
        queue.clear()
        queue.cancelInProgress = false
        queue.setCurrentProcessing(null)
        imageRepository.resetAllProcessing()
        _uiState.value = ProcessingUiState.Idle
    }

    private fun observeSettingsForStaleOutputs() {
        viewModelScope.launch {
            combine(
                settingsViewModel.globalStrength,
                settingsViewModel.activeSelection,
                settingsViewModel.oidnInputScale,
            ) { _, _, _ -> }.drop(1).collect { markOutputsStale() }
        }
    }

    private fun markOutputsStale() {
        imageRepository.images.update { list ->
            list.map { image ->
                if (image.outputBitmap != null) image.copy(isOutputStale = true) else image
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        serviceHelper.unregister()
    }

    fun removeImage(id: String, force: Boolean = false, cleanupCache: Boolean = false) {
        val target = imageRepository.getImageById(id) ?: run {
            queue.remove(id)
            return
        }
        if (!force && target.isProcessing && !target.isCancelling) {
            if (queue.contains(id) && !queue.isActive(id)) {
                queue.remove(id)
                queue.decrementActiveTotal()
                imageRepository.updateImageState(id) { resetProcessing(it) }
                return
            }
            queue.cancelInProgress = true
            startCancelWatchdog(id)
            imageRepository.updateImageState(id) {
                it.copy(
                    isCancelling = true, progress = statusCanceling
                )
            }
            cancelProcessingService(id)
            return
        }
        if (queue.contains(id)) queue.decrementActiveTotal()
        queue.remove(id)
        imageRepository.removeImage(id)
        if (cleanupCache) {
            viewModelScope.launch {
                Log.d("ProcessingViewModel", "removeImage: Cleaning up cache for imageId: $id")
                CacheManager.deleteRecoveryPair(
                    appContext, id, deleteProcessed = true, deleteUnprocessed = true
                )
            }
        }
    }

    private fun resetChunkProgress(item: ImageItem) = item.copy(
        completedChunks = 0, totalChunks = 0
    )

    private fun resetProcessing(
        item: ImageItem,
        isProcessing: Boolean = false,
        progress: String = "",
        isCancelling: Boolean = false
    ) = item.copy(
        isProcessing = isProcessing,
        progress = progress,
        isCancelling = isCancelling,
        completedChunks = 0,
        totalChunks = 0
    )

    private fun processNextInQueue() {
        val imageId = queue.dequeue()
        if (imageId == null) {
            queue.resetProcessingState()
            _uiState.value = ProcessingUiState.Idle
            return
        }

        val total = if (queue.activeProcessingTotal > 0) queue.activeProcessingTotal
        else imageRepository.images.value.count { it.uri != null }
        queue.setActiveTotal(total)
        _uiState.value = ProcessingUiState.Processing(queue.currentIndex(), total)

        queue.setCurrentProcessing(imageId)
        queue.updateIsProcessingQueue()
        startProcessingImage(imageId, settingsViewModel.globalStrength.value)
    }

    private fun startProcessingImage(imageId: String, strength: Float) {
        val image = imageRepository.getImageById(imageId) ?: return
        val uri = image.uri ?: return
        val uriString = uri.toString()

        val selection = settingsViewModel.activeSelection.value
        val mode = selection.type
        val modelName = selection.modelName
        if (mode == null) {
            stopProcessing(
                imageId,
                status(R.string.no_model_installed_title),
                isCancelled = false,
                serviceAlreadyDead = true,
            )
            return
        }

        imageRepository.updateImageState(imageId) {
            it.copy(
                isProcessing = true,
                progress = statusPreparing,
                completedChunks = 0,
                totalChunks = 0
            )
        }

        viewModelScope.launch {
            CacheManager.saveUnprocessedImage(appContext, imageId, uri)
            if (mode == ModelType.LITERT && modelName != null &&
                !ModelManager.gpuCacheExists(appContext, modelName)
            ) {
                showDialog(ProcessingDialog.GpuCacheCreating)
            }
            serviceHelper.startProcessing(
                imageId = imageId,
                uriString = uriString,
                filename = image.filename,
                strength = strength,
                chunkSize = settingsViewModel.chunkSize.value,
                overlapSize = settingsViewModel.overlapSize.value,
                onnxDeviceThreads = settingsViewModel.onnxDeviceThreads.value,
                modelName = modelName,
                processingMode = mode.name,
                oidnWeightsPath = if (mode == ModelType.OIDN) settingsViewModel.modelManager?.getActiveModelPath(
                    ModelType.OIDN
                ) else null,
                oidnHdr = settingsViewModel.oidnHdr.value,
                oidnSrgb = settingsViewModel.oidnSrgb.value,
                oidnQuality = settingsViewModel.oidnQuality.value,
                oidnMaxMemoryMB = settingsViewModel.oidnMaxMemoryMB.value,
                oidnNumThreads = settingsViewModel.oidnNumThreads.value,
                oidnInputScale = settingsViewModel.oidnInputScale.value
            )
        }
    }

    fun cancelProcessing() {
        val current = queue.currentProcessingId
        queue.clear()
        queue.cancelInProgress = true
        startCancelWatchdog(current)
        if (current != null) {
            imageRepository.updateImageState(current) { img ->
                img.copy(
                    isCancelling = true, progress = statusCanceling
                )
            }
            cancelProcessingService(current)
        }
        imageRepository.images.value.filter { it.isProcessing && it.id != current }
            .forEach { image ->
                imageRepository.updateImageState(image.id) { resetProcessing(it) }
            }
        _uiState.value = ProcessingUiState.Idle
    }

    private fun cancelProcessingService(imageId: String?) {
        val targetImageId = imageId ?: queue.currentProcessingId
        val wasActive = targetImageId != null && queue.isActive(targetImageId)
        val wasKilled = serviceHelper.cancelProcessing {
            viewModelScope.launch(Dispatchers.IO) {
                CacheManager.clearChunks(appContext)
            }
        }
        if (wasActive) {
            queue.setCurrentProcessing(null)
        }
        if (!wasKilled && targetImageId != null) {
            stopProcessing(
                targetImageId, statusCancelled, isCancelled = true, serviceAlreadyDead = !wasActive
            )
        }
    }

    private fun handleProcessingComplete(imageId: String, path: String) {
        viewModelScope.launch {
            val bitmap = runCatching {
                withContext(Dispatchers.IO) { BitmapFactory.decodeFile(path) }
            }.onFailure {
                Log.e("ProcessingViewModel", "handleProcessingComplete: decode of $path failed", it)
            }.getOrNull()
            if (bitmap == null) {
                imageRepository.updateImageState(imageId) {
                    resetProcessing(it, progress = status(R.string.error_decode_failed))
                }
            } else {
                imageRepository.updateImageState(imageId) {
                    it.copy(
                        outputBitmap = bitmap,
                        isProcessing = false,
                        progress = statusComplete,
                        completedChunks = 0,
                        totalChunks = 0,
                        isOutputStale = false
                    )
                }
                serviceHelper.confirmUiApplied(isAppVisible)
            }
            advanceQueue(imageId)
        }
    }

    private fun handleProcessingError(imageId: String?, message: String) {
        Log.d(
            "ProcessingViewModel",
            "handleProcessingError: imageId=$imageId, message=$message, singleCancelId=${queue.singleImageCancelId}, currentProcessingId=${queue.currentProcessingId}"
        )
        val resolvedId = imageId ?: queue.singleImageCancelId
        val isCancelled = message.contains(statusCancelled, true)
        val displayMessage = if (isCancelled) statusCancelled else message
        val isSingleCancel = resolvedId != null && resolvedId == queue.singleImageCancelId
        Log.d(
            "ProcessingViewModel",
            "handleProcessingError: resolvedId=$resolvedId, isCancelled=$isCancelled, isSingleCancel=$isSingleCancel"
        )
        if (isSingleCancel) queue.singleImageCancelId = null
        stopProcessing(resolvedId, displayMessage, isCancelled, singleImageCancel = isSingleCancel)
    }

    private fun handleServiceCrash(imageId: String?, intentional: Boolean = false) {
        Log.d(
            "ProcessingViewModel",
            "handleServiceCrash: imageId=$imageId, intentional=$intentional, singleCancelId=${queue.singleImageCancelId}, currentProcessingId=${queue.currentProcessingId}"
        )
        val resolvedId = imageId ?: queue.singleImageCancelId
        val isSingleCancel = resolvedId != null && resolvedId == queue.singleImageCancelId
        val isCancelled = intentional || isSingleCancel
        Log.d(
            "ProcessingViewModel",
            "handleServiceCrash: resolvedId=$resolvedId, isSingleCancel=$isSingleCancel, isCancelled=$isCancelled"
        )
        if (isSingleCancel) queue.singleImageCancelId = null
        stopProcessing(
            resolvedId,
            if (isCancelled) statusCancelled else statusNativeCrash,
            isCancelled = isCancelled,
            serviceAlreadyDead = true,
            singleImageCancel = isSingleCancel
        )
    }

    private fun stopProcessing(
        imageId: String?,
        displayMessage: String,
        isCancelled: Boolean,
        serviceAlreadyDead: Boolean = false,
        singleImageCancel: Boolean = false
    ) {
        Log.d(
            "ProcessingViewModel",
            "stopProcessing: imageId=$imageId, displayMessage=$displayMessage, isCancelled=$isCancelled, singleImageCancel=$singleImageCancel, currentProcessingId=${queue.currentProcessingId}"
        )
        cancelCancelWatchdog()
        if (!imageId.isNullOrEmpty()) {
            imageRepository.updateImageState(imageId) {
                resetProcessing(
                    it, progress = displayMessage
                )
            }
            queue.remove(imageId)
        }
        if (isCancelled && imageId == queue.currentProcessingId) {
            queue.cancelInProgress = false
            queue.setCurrentProcessing(null)
            if (singleImageCancel) {
                Log.d(
                    "ProcessingViewModel",
                    "stopProcessing: single-image cancel path for imageId=$imageId"
                )
                advanceQueue(imageId)
            } else {
                resetProcessingState()
            }
            return
        }
        if (!serviceAlreadyDead) {
            val stopIntent = Intent(appContext, ProcessingService::class.java)
            stopIntent.action = ProcessingService.ACTION_CANCEL
            appContext.startService(stopIntent)
        }
        resetProcessingState()
        if (!isCancelled) {
            showDialog(ProcessingDialog.ProcessingError(displayMessage))
        }
    }

    private fun advanceQueue(completedImageId: String? = null) {
        if (!queue.isEmpty) {
            queue.updateIsProcessingQueue()
            processNextInQueue()
        } else {
            if (queue.currentProcessingId == null || completedImageId == null || completedImageId == queue.currentProcessingId) {
                queue.resetProcessingState()
                _uiState.value = ProcessingUiState.Idle
            }
        }
    }

    fun isCurrent(imageId: String) = queue.isActive(imageId)

    fun isProcessingOrQueueActive(): Boolean {
        return queue.cancelInProgress || queue.currentProcessingId != null || !queue.isEmpty
    }

    fun cancelQueuedImage(imageId: String) {
        if (queue.isActive(imageId)) {
            queue.cancelInProgress = true
            queue.singleImageCancelId = imageId
            startCancelWatchdog(imageId)
            imageRepository.updateImageState(imageId) {
                it.copy(isCancelling = true, progress = statusCanceling)
            }
            serviceHelper.cancelProcessing {
                viewModelScope.launch(Dispatchers.IO) {
                    CacheManager.clearChunks(appContext)
                }
            }
        } else if (queue.contains(imageId)) {
            queue.remove(imageId)
            queue.decrementActiveTotal()
            imageRepository.updateImageState(imageId) { resetProcessing(it) }
        }
    }

    fun requestRemoval(ids: Collection<String>) {
        val targets = ids.mapNotNull { id -> imageRepository.getImageById(id) }
        removeImagesNow(targets.filter { it.outputBitmap == null && !it.isProcessing }
            .map { it.id })
        val needsConfirmation = targets.filter { it.outputBitmap != null }
        if (needsConfirmation.isNotEmpty()) {
            showDialog(
                ProcessingDialog.ConfirmRemoval(
                    imageIds = needsConfirmation.map { it.id },
                    filename = needsConfirmation.singleOrNull()?.filename,
                )
            )
        }
    }

    fun requestSave(ids: Collection<String>, removeAfter: Boolean): Boolean {
        val validIds = ids.mapNotNull { id -> imageRepository.getImageById(id) }
            .filter { it.outputBitmap != null }.map { it.id }
        return validIds.isNotEmpty() && if (validIds.size > 1) {
            saveImages(validIds, null, overwrite = false, removeAfter = removeAfter)
            false
        } else if (appPreferences.loadShowSaveDialog()) {
            val image = imageRepository.getImageById(validIds.single())
            showDialog(
                ProcessingDialog.SaveOptions(
                    imageIds = validIds,
                    defaultFilename = image?.filename.orEmpty(),
                    removeAfter = removeAfter,
                )
            )
            true
        } else {
            gateSave(validIds, removeAfter, filename = null)
            false
        }
    }

    fun saveAllNow() {
        val ids = imageRepository.images.value.filter { it.outputBitmap != null }.map { it.id }
        if (ids.isNotEmpty()) saveImages(ids, null, overwrite = false)
    }

    fun confirmRemoval(saveFirst: Boolean) {
        val action = _dialog.value as? ProcessingDialog.ConfirmRemoval ?: return
        dismissDialog()
        if (saveFirst) requestSave(action.imageIds, removeAfter = true)
        else removeImagesNow(action.imageIds)
    }

    fun confirmSave(name: String?, saveAll: Boolean, disablePrompt: Boolean) {
        val action = _dialog.value as? ProcessingDialog.SaveOptions ?: return
        dismissDialog()
        if (disablePrompt) appPreferences.saveShowSaveDialog(false)
        if (saveAll) {
            val ids = imageRepository.images.value.filter { it.outputBitmap != null }.map { it.id }
            if (ids.isNotEmpty()) saveImages(ids, null, overwrite = false)
        } else {
            gateSave(action.imageIds, action.removeAfter, name)
        }
    }

    fun confirmOverwrite(name: String) {
        val action = _dialog.value as? ProcessingDialog.ConfirmOverwrite ?: return
        dismissDialog()
        saveImages(action.imageIds, name, overwrite = true, removeAfter = action.removeAfter)
    }

    private fun gateSave(imageIds: List<String>, removeAfter: Boolean, filename: String?) {
        val single = imageIds.singleOrNull()?.let { id -> imageRepository.getImageById(id) }
        val resolved = filename ?: single?.filename
        if (single != null && resolved != null && ImageActions.checkFileExists(
                appContext,
                resolved
            )
        ) {
            showDialog(
                ProcessingDialog.ConfirmOverwrite(
                    imageIds = imageIds,
                    filename = resolved,
                    removeAfter = removeAfter,
                )
            )
        } else {
            saveImages(
                imageIds,
                baseFilename = resolved.takeIf { single != null },
                overwrite = false,
                removeAfter = removeAfter,
            )
        }
    }

    private fun removeImagesNow(ids: List<String>) {
        ids.forEach { id ->
            imageRepository.getImageById(id)?.uri?.let(::releasePersistedUri)
            removeImage(id, force = true, cleanupCache = true)
        }
    }

    private fun releasePersistedUri(uri: Uri) {
        runCatching {
            appContext.contentResolver.releasePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }
    }

    private fun saveImages(
        imageIds: List<String>,
        baseFilename: String?,
        overwrite: Boolean,
        removeAfter: Boolean = false,
    ) {
        viewModelScope.launch {
            val targets = imageIds.mapNotNull { id ->
                imageRepository.getImageById(id)?.takeIf { it.outputBitmap != null }
            }
            if (targets.isEmpty()) return@launch
            _saveState.value = SaveState.Saving(0, targets.size)
            var savedCount = 0
            val usedBatchNames = mutableSetOf<String>()
            try {
                targets.forEachIndexed { index, image ->
                    val bitmap = image.outputBitmap ?: return@forEachIndexed
                    val resolvedName = resolveFilename(
                        image.filename, baseFilename, index, targets.size
                    )
                    val name = if (targets.size > 1) {
                        uniqueBatchFilename(resolvedName, usedBatchNames)
                    } else {
                        resolvedName
                    }
                    _saveState.value = SaveState.Saving(index + 1, targets.size)
                    ImageActions.saveImageAwait(
                        context = appContext,
                        bitmap = bitmap,
                        filename = name,
                        overwrite = overwrite,
                        imageId = image.id,
                    ).onSuccess {
                        imageRepository.markImageAsSaved(image.id)
                        savedCount++
                    }.onFailure { error ->
                        showDialog(ProcessingDialog.SaveError(error.message.orEmpty()))
                    }
                }
                if (savedCount > 0) {
                    val message = appContext.resources.getQuantityString(
                        R.plurals.image_saved_to_gallery, savedCount, savedCount
                    )
                    SnackbarController.pushEvent(
                        SnackbarEvents.MessageEvent(
                            message = message, duration = SnackbarDuration.Short
                        )
                    )
                }
                if (removeAfter) removeImagesNow(imageIds)
            } finally {
                _saveState.value = SaveState.Idle
            }
        }
    }

    fun importOutputAsNewImage(imageId: String) {
        val image = imageRepository.getImageById(imageId) ?: return
        imageRepository.addImageFromOutputCache(appContext, imageId, image.filename)
    }

    private fun resolveFilename(original: String, base: String?, index: Int, total: Int): String {
        val chosenBase = base?.takeIf { it.isNotBlank() }
        val raw = when {
            total == 1 && chosenBase != null -> chosenBase
            total > 1 -> original.ifBlank { "${chosenBase ?: "DeJPEG"}_${index + 1}" }
            else -> original.ifBlank { chosenBase ?: "DeJPEG" }
        }
        return raw.substringBeforeLast('.', raw)
    }

    private fun uniqueBatchFilename(
        filename: String,
        usedNames: MutableSet<String>,
    ): String {
        val key = filename.lowercase(Locale.ROOT)
        if (usedNames.add(key)) return filename

        var suffix = 2
        var candidate = "${filename}_$suffix"
        while (!usedNames.add(candidate.lowercase(Locale.ROOT))) {
            suffix++
            candidate = "${filename}_$suffix"
        }
        return candidate
    }
}
