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

package com.je.dejpeg.ui.viewmodel

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.je.dejpeg.ModelType
import com.je.dejpeg.ProcessingService
import com.je.dejpeg.data.ImageRepository
import com.je.dejpeg.data.ProcessingMode
import com.je.dejpeg.utils.CacheManager
import com.je.dejpeg.utils.ProcessingQueueManager
import com.je.dejpeg.utils.helpers.ImagePickerHelper
import com.je.dejpeg.utils.helpers.ServiceCommunicationHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    val uiState = MutableStateFlow<ProcessingUiState>(ProcessingUiState.Idle)
    val processingErrorDialog = MutableStateFlow<String?>(null)

    private var appContext: Context? = null
    private var serviceHelper: ServiceCommunicationHelper? = null
    private var imagePickerHelper: ImagePickerHelper? = null
    private val queue = ProcessingQueueManager()
    private var isInitialized = false

    lateinit var imageRepository: ImageRepository
    lateinit var settingsViewModel: SettingsViewModel

    private fun status(resId: Int) = appContext!!.getString(resId)
    private val statusPreparing get() = status(com.je.dejpeg.R.string.status_preparing)
    private val statusComplete get() = status(com.je.dejpeg.R.string.status_complete)
    private val statusCancelled get() = status(com.je.dejpeg.R.string.status_cancelled)
    private val statusCanceling get() = status(com.je.dejpeg.R.string.status_canceling)
    private val statusQueued get() = status(com.je.dejpeg.R.string.status_queued)
    private val statusNativeCrash get() = status(com.je.dejpeg.R.string.error_native_crash)

    fun initialize(context: Context) {
        if (isInitialized) return
        isInitialized = true
        val appCtx = context.applicationContext
        appContext = appCtx
        imagePickerHelper = ImagePickerHelper(context)
        serviceHelper = ServiceCommunicationHelper(
            appCtx, object : ServiceCommunicationHelper.ServiceCallbacks {
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

                override fun onServiceCrash(imageId: String?) {
                    Log.e(
                        "ProcessingViewModel",
                        "Service process crashed while processing image: $imageId"
                    )
                    handleServiceCrash(imageId)
                }
            })
        serviceHelper?.register()
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

    fun removeImage(id: String, force: Boolean = false, cleanupCache: Boolean = false) {
        val target = imageRepository.getImageById(id) ?: run {
            queue.remove(id)
            return
        }
        if (!force && target.isProcessing && !target.isCancelling) {
            if (queue.contains(id) && !queue.isActive(id)) {
                queue.remove(id)
                queue.decrementActiveTotal()
                imageRepository.updateImageState(id) { resetImageProcessingState(it) }
                return
            }
            queue.cancelInProgress = true
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
            appContext?.let { ctx ->
                viewModelScope.launch {
                    Log.d("ProcessingViewModel", "removeImage: Cleaning up cache for imageId: $id")
                    CacheManager.deleteRecoveryPair(
                        ctx, id, deleteProcessed = true, deleteUnprocessed = true
                    )
                }
            }
        }
    }

    private fun resetChunkProgress(item: ImageItem) = item.copy(
        completedChunks = 0, totalChunks = 0
    )

    private fun resetImageProcessingState(
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

    fun processImages() {
        viewModelScope.launch {
            if (queue.cancelInProgress) return@launch
            val imagesToProcess = imageRepository.images.value.filter { it.uri != null }
            if (imagesToProcess.isEmpty()) return@launch

            queue.enqueue(imagesToProcess.map { it.id })

            imagesToProcess.forEach { image ->
                imageRepository.updateImageState(image.id) {
                    resetChunkProgress(it).copy(
                        isProcessing = true, progress = statusQueued, isCancelling = false
                    )
                }
            }

            uiState.value = ProcessingUiState.Processing(0, imagesToProcess.size)
            processNextInQueue()
        }
    }

    fun processImage(id: String) {
        viewModelScope.launch {
            val image = imageRepository.getImageById(id) ?: return@launch
            if (image.uri == null) return@launch
            if (queue.cancelInProgress || queue.currentProcessingId != null || !queue.isEmpty) {
                if (queue.isActive(id) || queue.contains(id)) return@launch
                queue.enqueueSingle(id)
                queue.setActiveTotal(
                    maxOf(
                        queue.activeProcessingTotal,
                        queue.queueSize + if (queue.currentProcessingId != null) 1 else 0
                    )
                )
                imageRepository.updateImageState(id) {
                    resetChunkProgress(it).copy(
                        isProcessing = true, progress = statusQueued, isCancelling = false
                    )
                }
                uiState.value =
                    ProcessingUiState.Processing(queue.currentIndex(), queue.activeProcessingTotal)
                return@launch
            }

            queue.setActiveTotal(1)
            uiState.value = ProcessingUiState.Processing(0, 1)
            queue.setCurrentProcessing(id)
            startProcessingImage(id, image.strengthFactor * 100f)
        }
    }

    private fun processNextInQueue() {
        val imageId = queue.dequeue()
        if (imageId == null) {
            queue.resetProcessingState()
            uiState.value = ProcessingUiState.Idle
            return
        }

        val image = imageRepository.getImageById(imageId) ?: return processNextInQueue()

        val total = if (queue.activeProcessingTotal > 0) queue.activeProcessingTotal
        else imageRepository.images.value.count { it.uri != null }
        queue.setActiveTotal(total)
        uiState.value = ProcessingUiState.Processing(queue.currentIndex(), total)

        queue.setCurrentProcessing(imageId)
        queue.updateIsProcessingQueue()
        startProcessingImage(imageId, image.strengthFactor * 100f)
    }

    private fun startProcessingImage(imageId: String, strength: Float) {
        val ctx = appContext ?: return
        val image = imageRepository.getImageById(imageId) ?: return
        val uri = image.uri ?: return
        val uriString = uri.toString()

        imageRepository.updateImageState(imageId) {
            it.copy(
                isProcessing = true,
                progress = statusPreparing,
                completedChunks = 0,
                totalChunks = 0
            )
        }

        viewModelScope.launch {
            CacheManager.saveUnprocessedImage(ctx, imageId, uri)
            val mode = settingsViewModel.processingMode.value
            serviceHelper?.startProcessing(
                imageId = imageId,
                uriString = uriString,
                filename = image.filename,
                strength = strength,
                chunkSize = settingsViewModel.chunkSize.value,
                overlapSize = settingsViewModel.overlapSize.value,
                onnxDeviceThreads = settingsViewModel.onnxDeviceThreads.value,
                modelName = settingsViewModel.getActiveModelName(),
                processingMode = mode.name,
                oidnWeightsPath = if (mode == ProcessingMode.OIDN) settingsViewModel.modelManager?.getActiveModelPath(
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
        queue.clear()
        queue.cancelInProgress = true

        queue.currentProcessingId?.let {
            imageRepository.updateImageState(it) { img ->
                img.copy(
                    isCancelling = true, progress = statusCanceling
                )
            }
            cancelProcessingService(it)
        }
        imageRepository.images.value.filter { it.isProcessing && it.id != queue.currentProcessingId }
            .forEach { image ->
                imageRepository.updateImageState(image.id) { resetImageProcessingState(it) }
            }
        uiState.value = ProcessingUiState.Idle
    }

    private fun cancelProcessingService(imageId: String?) {
        val targetImageId = imageId ?: queue.currentProcessingId
        val ctx = appContext ?: return
        val wasKilled = serviceHelper?.cancelProcessing {
            viewModelScope.launch(Dispatchers.IO) {
                CacheManager.clearChunks(ctx)
                CacheManager.clearAbandonedImages(ctx)
            }
        } ?: false
        if (targetImageId != null && queue.isActive(targetImageId)) {
            queue.setCurrentProcessing(null)
        }
        if (targetImageId != null) {
            stopProcessing(
                targetImageId, statusCancelled, isCancelled = true, serviceAlreadyDead = wasKilled
            )
        }
    }

    private fun handleProcessingComplete(imageId: String, path: String) {
        viewModelScope.launch {
            try {
                val bitmap = withContext(Dispatchers.IO) { BitmapFactory.decodeFile(path) }
                if (bitmap != null) {
                    CacheManager.saveProcessedImage(appContext!!, imageId, bitmap)
                    imageRepository.updateImageState(imageId) {
                        it.copy(
                            outputBitmap = bitmap,
                            isProcessing = false,
                            progress = statusComplete,
                            completedChunks = 0,
                            totalChunks = 0
                        )
                    }
                } else {
                    imageRepository.updateImageState(imageId) {
                        resetImageProcessingState(
                            it, progress = "Decode failed"
                        )
                    }
                }
            } catch (e: Exception) {
                imageRepository.updateImageState(imageId) {
                    resetImageProcessingState(
                        it, progress = "${e.message}"
                    )
                }
            } finally {
                advanceQueue(imageId)
            }
        }
    }

    private fun handleProcessingError(imageId: String?, message: String) {
        val isCancelled = message.contains(statusCancelled, true)
        val displayMessage = if (isCancelled) statusCancelled else message
        stopProcessing(imageId, displayMessage, isCancelled)
    }

    private fun handleServiceCrash(imageId: String?) {
        stopProcessing(imageId, statusNativeCrash, isCancelled = false, serviceAlreadyDead = true)
    }

    private fun stopProcessing(
        imageId: String?,
        displayMessage: String,
        isCancelled: Boolean,
        serviceAlreadyDead: Boolean = false
    ) {
        if (!imageId.isNullOrEmpty()) {
            imageRepository.updateImageState(imageId) {
                resetImageProcessingState(
                    it, progress = displayMessage
                )
            }
            queue.remove(imageId)

            if (isCancelled) {
                appContext?.let { ctx ->
                    viewModelScope.launch(Dispatchers.IO) {
                        Log.d("ProcessingViewModel", "Cleaning up cache for imageId: $imageId")
                        CacheManager.deleteRecoveryPair(
                            ctx, imageId, deleteProcessed = true, deleteUnprocessed = true
                        )
                    }
                }
            }
        }
        if (isCancelled && imageId == queue.currentProcessingId) {
            queue.cancelInProgress = false
            queue.setCurrentProcessing(null)
            advanceQueue(imageId)
            return
        }
        if (!serviceAlreadyDead) {
            appContext?.let { ctx ->
                val stopIntent = Intent(ctx, ProcessingService::class.java)
                stopIntent.action = ProcessingService.ACTION_CANCEL
                ctx.startService(stopIntent)
            }
        }
        queue.clear()
        queue.cancelInProgress = false
        queue.setCurrentProcessing(null)
        imageRepository.images.value = imageRepository.images.value.map {
            if (it.isProcessing || it.isCancelling) {
                it.copy(isProcessing = false, isCancelling = false, progress = "")
            } else it
        }
        if (!isCancelled) {
            processingErrorDialog.value = displayMessage
        }
        uiState.value = ProcessingUiState.Idle
    }

    private fun advanceQueue(completedImageId: String? = null) {
        if (!queue.isEmpty) {
            queue.updateIsProcessingQueue()
            viewModelScope.launch { processNextInQueue() }
        } else {
            if (queue.currentProcessingId == null || completedImageId == null || completedImageId == queue.currentProcessingId) {
                queue.resetProcessingState()
                uiState.value = ProcessingUiState.Idle
            }
        }
    }

    fun isCurrentlyProcessing(imageId: String) = queue.isActive(imageId)

    fun cancelProcessingForImage(imageId: String) {
        queue.cancelInProgress = true
        imageRepository.updateImageState(imageId) {
            it.copy(
                isCancelling = true, progress = statusCanceling
            )
        }
        cancelProcessingService(imageId)
    }

    fun dismissProcessingErrorDialog() {
        processingErrorDialog.value = null
    }
}
