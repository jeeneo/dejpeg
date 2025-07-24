package com.je.dejpeg.models

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.je.dejpeg.ImageProcessor
import com.je.dejpeg.ModelManager
import java.util.concurrent.atomic.AtomicInteger
import com.je.dejpeg.utils.NotificationHandler
import com.je.dejpeg.R
import com.je.dejpeg.utils.TimeEstimator

class ProcessingProgress {
    val currentImageIndex = AtomicInteger(0)
    val totalImages = AtomicInteger(0)
    val completedChunks = AtomicInteger(0)
    val totalChunks = AtomicInteger(0)

    private var processingOrder: List<Int> = emptyList()
    private var chunksPerImage: List<Int> = emptyList()
    public var chunksCompletedPerImage: MutableList<Int> = mutableListOf()
    private var lastImageWidth: Int = 0
    private var lastImageHeight: Int = 0
    private var queuedImages: Int = 0
    private var allImagesCompleted: Boolean = false
    private var originalEstimate: Long? = null

    public var timeEstimator: TimeEstimator? = null
    private var lastEstimateUpdate: Long = 0

    interface StatusListener {
        fun onStatusChanged(status: String)
    }
    private var statusListener: StatusListener? = null
    fun setStatusListener(listener: StatusListener?) {
        statusListener = listener
    }
    private fun notifyStatusChanged(status: String) {
        statusListener?.onStatusChanged(status)
    }

    fun setProcessingOrder(order: List<Int>) {
        processingOrder = order
    }

    fun setImageChunks(chunkCounts: List<Int>) {
        chunksPerImage = chunkCounts
        chunksCompletedPerImage = MutableList(chunkCounts.size) { 0 }
        totalChunks.set(chunkCounts.sum())
    }

    fun initializeTimeEstimation(context: Context, modelName: String, totalChunks: Int) {
        timeEstimator = TimeEstimator(context, modelName)
        timeEstimator?.startProcessing()
        val initialEstimate = timeEstimator?.getInitialEstimate(totalChunks) ?: 0
        notifyStatusChanged("Starting processing... Estimated time: ${timeEstimator?.formatTimeRemaining(initialEstimate)}")
    }

    fun onChunkCompleted(imageIdx: Int) {
        timeEstimator?.endChunk()
        if (imageIdx in chunksCompletedPerImage.indices) {
            chunksCompletedPerImage[imageIdx]++
        }
        completedChunks.incrementAndGet()
        timeEstimator?.startChunk()
        updateEstimateIfNeeded()
    }

    fun onChunkCompleted() {
        timeEstimator?.endChunk()
        completedChunks.incrementAndGet()
        timeEstimator?.startChunk()
        updateEstimateIfNeeded()
    }

    private fun updateEstimateIfNeeded() {
        val now = System.currentTimeMillis()
        if (now - lastEstimateUpdate > 3000) {
            lastEstimateUpdate = now
            val remainingTime = timeEstimator?.getEstimatedTimeRemaining(
                completedChunks.get(),
                totalChunks.get()
            ) ?: 0
            val timeString = timeEstimator?.formatTimeRemaining(remainingTime) ?: ""
            if (timeString.isNotEmpty()) {
                notifyStatusChanged(timeString)
            }
        }
    }

    fun updateImageProgress(current: Int, total: Int) {
        originalEstimate = null
        currentImageIndex.set(current)
        totalImages.set(total)
        queuedImages = total
        allImagesCompleted = false
        chunksCompletedPerImage.clear()
    }

    fun updateChunkProgress(totalChunks: Int) {
        this.totalChunks.set(totalChunks)
        completedChunks.set(0)
    }

    fun updateImageDimensions(width: Int, height: Int) {
        lastImageWidth = width
        lastImageHeight = height
    }

    fun getStatusString(context: Context): String {
        if (currentImageIndex.get() == 0 && totalImages.get() == 0) {
            return context.getString(R.string.loading_status)
        }

        val currentImage = currentImageIndex.get()
        val totalImage = totalImages.get()
        val currentImageIdx = currentImage - 1
        val (activeChunk, totalChunk) = getChunkProgress(currentImageIdx)
        val baseStatus = context.getString(R.string.processing_status_format, currentImage, totalImage, activeChunk, totalChunk)
        val remainingTime = timeEstimator?.getEstimatedTimeRemaining(
            completedChunks.get(),
            totalChunks.get()
        ) ?: 0
        val timeString = timeEstimator?.formatTimeRemaining(remainingTime) ?: ""
        return if (timeString.isNotEmpty()) {
            "$baseStatus • $timeString"
        } else {
            baseStatus
        }
    }

    private fun getDisplayImageNumber(currentImage: Int, currentImageIdx: Int): Int {
        return if (processingOrder.isNotEmpty() && currentImage in 1..processingOrder.size) {
            processingOrder.indexOf(currentImageIdx).let { if (it >= 0) it + 1 else currentImage }
        } else {
            currentImage
        }
    }

    private fun getChunkProgress(currentImageIdx: Int): Pair<Int, Int> {
        return if (currentImageIdx in chunksPerImage.indices) {
            chunksCompletedPerImage[currentImageIdx] to chunksPerImage[currentImageIdx]
        } else {
            completedChunks.get() to totalChunks.get()
        }
    }

    private fun invokeCallbackSafely(callback: Any, message: String) {
        try {
            callback::class.java.getMethod("onProgress", String::class.java)
                .invoke(callback, message)
        } catch (e: Exception) {
            //
        }
    }

    fun markAllImagesCompleted(context: Context) {
        allImagesCompleted = true
        queuedImages = 0
        val totalImagesProcessed = totalImages.get()
        val notificationHandler = NotificationHandler(context)
        notificationHandler.dismissProcessingNotification()
        notificationHandler.showCompletionNotification(totalImagesProcessed)
    }
}

sealed class ProcessingState {
    object Idle : ProcessingState()
    object Loading : ProcessingState()
    data class Processing(val progress: Int) : ProcessingState()
    data class Error(val message: String) : ProcessingState()
    data class Success(val message: String) : ProcessingState()

    companion object {
        @JvmStatic
        var modelManager: ModelManager? = null

        private val processingProgress = ProcessingProgress()

        fun setProcessingOrder(order: List<Int>) = processingProgress.setProcessingOrder(order)
        fun setImageChunks(chunkCounts: List<Int>) = processingProgress.setImageChunks(chunkCounts)
        fun onChunkCompleted(imageIdx: Int) = processingProgress.onChunkCompleted(imageIdx)
        fun onChunkCompleted() = processingProgress.onChunkCompleted()
        fun updateImageProgress(current: Int, total: Int) = processingProgress.updateImageProgress(current, total)
        fun updateChunkProgress(totalChunks: Int) = processingProgress.updateChunkProgress(totalChunks)
        fun getStatusString(context: Context): String =  processingProgress.getStatusString(context)
        fun markAllImagesCompleted(context: Context) = processingProgress.markAllImagesCompleted(context)
        fun updateImageDimensions(width: Int, height: Int) {
            processingProgress.updateImageDimensions(width, height)
        }

        fun initializeTimeEstimation(context: Context, modelName: String, totalChunks: Int) {
            processingProgress.initializeTimeEstimation(context, modelName, totalChunks)
        }

        fun getTimeEstimate(completedChunks: Int, totalChunks: Int): String {
            return processingProgress.timeEstimator?.formatTimeRemaining(
                processingProgress.timeEstimator?.getEstimatedTimeRemaining(completedChunks, totalChunks) ?: 0
            ) ?: ""
        }

        fun getCompletedChunks() = processingProgress.completedChunks
        fun getTotalChunks() = processingProgress.totalChunks

        @JvmField var lastImageWidth: Int = 0
        @JvmField var lastImageHeight: Int = 0
        @JvmField var queuedImages: Int = 0
        @JvmField var allImagesCompleted: Boolean = false
        @JvmField var processingOrder: List<Int> = emptyList()
        @JvmField var chunksPerImage: List<Int> = emptyList()
        @JvmField var chunksCompletedPerImage: MutableList<Int> = mutableListOf()

        fun updateChunkProgress(start: Int, end: Int, total: Int, threadCount: Int) {
            processingProgress.updateChunkProgress(total)
        }

        fun setStatusListener(listener: ProcessingProgress.StatusListener?) =
            processingProgress.setStatusListener(listener)

        fun resetProgress() {
            processingProgress.currentImageIndex.set(0)
            processingProgress.totalImages.set(0)
            processingProgress.completedChunks.set(0)
            processingProgress.totalChunks.set(0)
            processingProgress.chunksCompletedPerImage.clear()
        }

        fun getTimeEstimator(): TimeEstimator? = processingProgress.timeEstimator
    }
}