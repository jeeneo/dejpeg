package com.je.dejpeg.models

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.je.dejpeg.ImageProcessor
import com.je.dejpeg.ModelManager
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.roundToInt
import kotlin.math.max
import kotlin.math.min
import com.je.dejpeg.utils.NotificationHandler

/**
 * Manages processing time statistics and estimates using exponential moving averages
 */
object ProcessingTimeStats {
    private val processingTimeMap = mutableMapOf<String, MutableMap<String, MovingAverage>>()
    private var avgChunkTimePerMP: Double? = null
    private var avgSingleImageTimePerMP: Double? = null
    
    private const val PREFS_NAME = "processing_time_stats"
    private const val KEY_CHUNK = "avg_chunk_time_per_mp"
    private const val KEY_SINGLE = "avg_single_time_per_mp"
    
    private class MovingAverage(private val alpha: Double = 0.3) {
        private var avg: Double? = null
        
        fun update(value: Long): Double {
            avg = avg?.let { it * (1 - alpha) + value * alpha } ?: value.toDouble()
            return avg!!
        }
        
        fun get(): Double? = avg
    }

    fun recordProcessingTime(model: String, width: Int, height: Int, timeMs: Long) {
        val key = "${width}x${height}"
        val modelMap = processingTimeMap.getOrPut(model) { mutableMapOf() }
        val average = modelMap.getOrPut(key) { MovingAverage() }
        average.update(timeMs)
    }

    fun getEstimatedTime(model: String, width: Int, height: Int): Long? {
        val key = "${width}x${height}"
        return processingTimeMap[model]?.get(key)?.get()?.toLong()
    }

    fun recordChunkTime(width: Int, height: Int, millis: Long, context: Context? = null) {
        updateTimePerMP(width, height, millis, context) { newTimePerMP ->
            avgChunkTimePerMP = avgChunkTimePerMP?.let { it * 0.7 + newTimePerMP * 0.3 } ?: newTimePerMP
        }
    }

    fun recordSingleImageTime(width: Int, height: Int, millis: Long, context: Context? = null) {
        updateTimePerMP(width, height, millis, context) { newTimePerMP ->
            avgSingleImageTimePerMP = avgSingleImageTimePerMP?.let { it * 0.7 + newTimePerMP * 0.3 } ?: newTimePerMP
        }
    }

    private inline fun updateTimePerMP(
        width: Int, height: Int, millis: Long, context: Context?,
        updateFn: (Double) -> Unit
    ) {
        val mp = (width * height) / 1_000_000.0
        if (mp > 0) {
            updateFn(millis / mp)
            context?.let { save(it) }
        }
    }

    fun estimateChunkTimeMillis(width: Int, height: Int): Long? {
        return avgChunkTimePerMP?.let { (it * (width * height) / 1_000_000.0).toLong() }
    }

    fun estimateSingleImageTimeMillis(width: Int, height: Int): Long? {
        return avgSingleImageTimePerMP?.let { (it * (width * height) / 1_000_000.0).toLong() }
    }

    fun hasChunkEstimate() = avgChunkTimePerMP != null
    fun hasSingleImageEstimate() = avgSingleImageTimePerMP != null

    fun save(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit {
            avgChunkTimePerMP?.let { putString(KEY_CHUNK, it.toString()) } ?: remove(KEY_CHUNK)
            avgSingleImageTimePerMP?.let { putString(KEY_SINGLE, it.toString()) } ?: remove(KEY_SINGLE)
        }
    }

    fun load(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        avgChunkTimePerMP = prefs.getString(KEY_CHUNK, null)?.toDoubleOrNull()
        avgSingleImageTimePerMP = prefs.getString(KEY_SINGLE, null)?.toDoubleOrNull()
    }
}

/**
 * Handles time formatting and countdown display
 */
object TimeFormatter {
    private const val MILLIS_PER_SECOND = 1000L
    private const val SECONDS_PER_MINUTE = 60L
    
    fun formatTimeRemaining(millis: Long): String {
        val seconds = millis / MILLIS_PER_SECOND
        val minutes = seconds / SECONDS_PER_MINUTE
        val remainingSeconds = seconds % SECONDS_PER_MINUTE
        return when {
            minutes > 0 -> "$minutes min ${remainingSeconds}s remaining"
            else -> "${remainingSeconds}s remaining"
        }
    }
    
    fun formatTimeEstimate(millis: Long): String {
        val seconds = millis / MILLIS_PER_SECOND
        val minutes = seconds / SECONDS_PER_MINUTE
        val remainingSeconds = seconds % SECONDS_PER_MINUTE
        return if (minutes > 0) " (~$minutes min remaining)" else " (~$remainingSeconds sec remaining)"
    }
    
    fun formatDualTimeEstimate(currentMillis: Long, totalMillis: Long): String {
        val currentSeconds = currentMillis / MILLIS_PER_SECOND
        val currentMinutes = currentSeconds / SECONDS_PER_MINUTE
        val currentRemaining = currentSeconds % SECONDS_PER_MINUTE
        
        val totalSeconds = totalMillis / MILLIS_PER_SECOND
        val totalMinutes = totalSeconds / SECONDS_PER_MINUTE
        val totalRemaining = totalSeconds % SECONDS_PER_MINUTE
        
        val currentStr = if (currentMinutes > 0) "$currentMinutes min" else "$currentRemaining sec"
        val totalStr = if (totalMinutes > 0) "$totalMinutes min" else "$totalRemaining sec"
        
        return "$currentStr for current, $totalStr for all"
    }
}

/**
 * Manages processing progress and statistics
 */
class ProcessingProgress {
    val currentImageIndex = AtomicInteger(0)
    val totalImages = AtomicInteger(0)
    val completedChunks = AtomicInteger(0)
    val totalChunks = AtomicInteger(0)
    
    private var processingOrder: List<Int> = emptyList()
    private var chunksPerImage: List<Int> = emptyList()
    private var chunksCompletedPerImage: MutableList<Int> = mutableListOf()
    private var lastImageWidth: Int = 0
    private var lastImageHeight: Int = 0
    private var queuedImages: Int = 0
    private var allImagesCompleted: Boolean = false
    private var startTime: Long = 0

    private var imageStartTime: Long = 0
    private var originalEstimate: Long? = null

    // Add listener for status updates
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

    fun onChunkCompleted(imageIdx: Int) {
        if (imageIdx in chunksCompletedPerImage.indices) {
            chunksCompletedPerImage[imageIdx]++
        }
        completedChunks.incrementAndGet()
    }
    
    fun onChunkCompleted() {
        completedChunks.incrementAndGet()
    }

    fun updateImageProgress(current: Int, total: Int) {
        imageStartTime = System.currentTimeMillis()
        originalEstimate = null  // Reset estimate for new image
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

    fun getStatusString(context: Context, threadCount: Int, modelManager: ModelManager?): String {
        val currentImage = currentImageIndex.get()
        val totalImage = totalImages.get()
        val currentImageIdx = currentImage - 1

        val status = if (com.je.dejpeg.MainActivity.isProgressPercentage) {
            buildPercentageStatus(currentImage, totalImage, threadCount, modelManager)
        } else {
            buildChunkStatus(currentImage, totalImage, currentImageIdx, threadCount, modelManager)
        }
        notifyStatusChanged(status)
        return status
    }
    
    private fun buildPercentageStatus(currentImage: Int, totalImage: Int, threadCount: Int, modelManager: ModelManager?): String {
        val totalWork = chunksPerImage.sum().takeIf { it > 0 } ?: totalImage
        val workDone = chunksCompletedPerImage.sum()
        val percentage = if (totalWork > 0) (workDone * 100 / totalWork) else 0
        
        val timeEstimate = calculateTimeEstimate(threadCount, modelManager)
        val queuedInfo = getQueuedInfo()
        
        return "processing $percentage% complete...$timeEstimate$queuedInfo"
    }
    
    private fun buildChunkStatus(currentImage: Int, totalImage: Int, currentImageIdx: Int, threadCount: Int, modelManager: ModelManager?): String {
        val displayImageNumber = getDisplayImageNumber(currentImage, currentImageIdx)
        val (activeChunk, totalChunk) = getChunkProgress(currentImageIdx)
        val timeEstimate = calculateTimeEstimate(threadCount, modelManager)
        val queuedInfo = getQueuedInfo()
        
        return "processing image $displayImageNumber/$totalImage - chunks $activeChunk/$totalChunk...$timeEstimate$queuedInfo"
    }

    private fun getDisplayImageNumber(currentImage: Int, currentImageIdx: Int): Int {
        return if (processingOrder.isNotEmpty() && currentImage in 1..processingOrder.size) {
            processingOrder.indexOf(currentImageIdx).let { if (it >= 0) it + 1 else currentImage }
        } else {
            currentImage
        }
    }

    private fun getQueuedInfo(): String {
        val queuedCount = chunksCompletedPerImage.count { it == 0 }
        return if (queuedCount > 0) " ($queuedCount queued)" else ""
    }

    private fun getChunkProgress(currentImageIdx: Int): Pair<Int, Int> {
        return if (currentImageIdx in chunksPerImage.indices) {
            chunksCompletedPerImage[currentImageIdx] to chunksPerImage[currentImageIdx]
        } else {
            completedChunks.get() to totalChunks.get()
        }
    }

    private fun calculateTimeEstimate(threadCount: Int, modelManager: ModelManager?): String {
        if (totalChunks.get() <= 0) return ""

        // Initialize original estimate if not set
        if (originalEstimate == null) {
            originalEstimate = ProcessingTimeStats.estimateSingleImageTimeMillis(lastImageWidth, lastImageHeight)
            if (originalEstimate == null) return ""
        }

        val completedRatio = completedChunks.get().toDouble() / totalChunks.get()
        val elapsedTime = System.currentTimeMillis() - imageStartTime
        val estimatedTimeLeft = (originalEstimate!! * (1 - completedRatio)).toLong()

        // Use max of estimated time left and a minimum threshold to avoid showing "0 seconds" when almost done
        val adjustedTimeLeft = maxOf(estimatedTimeLeft - elapsedTime, 500)
        return " " + TimeFormatter.formatTimeRemaining(adjustedTimeLeft)
    }

    fun updateSingleImageProgress(width: Int, height: Int, callback: Any?) {
        updateImageDimensions(width, height)
        callback ?: return

        val currentImage = currentImageIndex.get()
        val totalImage = totalImages.get()
        val isLastImage = currentImage == totalImage
        val isChunkableImage = width > ImageProcessor.DEFAULT_CHUNK_SIZE || height > ImageProcessor.DEFAULT_CHUNK_SIZE

        val message = if (isChunkableImage) {
            buildChunkableImageMessage(width, height, isLastImage)
        } else {
            buildSingleImageMessage(currentImage, totalImage, width, height, isLastImage)
        }

        invokeCallbackSafely(callback, message)
        notifyStatusChanged(message)
    }
    
    private fun buildChunkableImageMessage(width: Int, height: Int, isLastImage: Boolean): String {
        // Calculate number of chunks
        val effectiveChunkSize = if (ProcessingState.modelManager?.getActiveModelName()?.startsWith("scunet_") == true) 
            ImageProcessor.SCUNET_CHUNK_SIZE else ImageProcessor.DEFAULT_CHUNK_SIZE
        val overlap = ImageProcessor.OVERLAP
        
        val numHorizontalChunks = (width - overlap) / (effectiveChunkSize - overlap) + 
            (if ((width - overlap) % (effectiveChunkSize - overlap) > 0) 1 else 0)
        val numVerticalChunks = (height - overlap) / (effectiveChunkSize - overlap) + 
            (if ((height - overlap) % (effectiveChunkSize - overlap) > 0) 1 else 0)
        val totalChunks = numHorizontalChunks * numVerticalChunks

        // Calculate actual chunk dimensions for each position
        var totalEstimateMillis = 0L
        for (y in 0 until numVerticalChunks) {
            for (x in 0 until numHorizontalChunks) {
                val chunkWidth = if (x == numHorizontalChunks - 1) {
                    width - x * (effectiveChunkSize - overlap)
                } else {
                    effectiveChunkSize
                }
                
                val chunkHeight = if (y == numVerticalChunks - 1) {
                    height - y * (effectiveChunkSize - overlap)
                } else {
                    effectiveChunkSize
                }

                // Add estimate for this chunk plus overhead
                ProcessingTimeStats.estimateChunkTimeMillis(chunkWidth, chunkHeight)?.let { baseTime -> 
                    // Add 15% overhead for chunk processing and memory management
                    val chunkTime = (baseTime * 1.15).toLong()
                    // Add extra overhead for larger number of chunks
                    val sequenceOverhead = if (totalChunks > 10) {
                        (baseTime * 0.05 * (totalChunks / 10.0)).toLong()
                    } else 0L
                    totalEstimateMillis += chunkTime + sequenceOverhead
                }
            }
        }

        val timeEstimate = if (totalEstimateMillis > 0) {
            // Add 10% to the total estimate for general processing overhead
            val adjustedBaseTime = (totalEstimateMillis * 1.1).toLong()
            val adjustedTime = adjustTimeForThreads(adjustedBaseTime)
            if (isLastImage) " ~${TimeFormatter.formatTimeRemaining(adjustedTime)}" 
            else TimeFormatter.formatTimeEstimate(adjustedTime)
        } else ""
        
        return "loading...$timeEstimate"
    }
    
    private fun buildSingleImageMessage(currentImage: Int, totalImage: Int, width: Int, height: Int, isLastImage: Boolean): String {
        val estimateMillis = ProcessingTimeStats.estimateChunkTimeMillis(width, height) // Unified source
        val timeEstimate = estimateMillis?.let {
            if (isLastImage) {
                TimeFormatter.formatTimeRemaining(it)
            } else {
                val remainingImages = totalImage - currentImage + 1
                TimeFormatter.formatDualTimeEstimate(it, it * remainingImages)
            }
        } ?: ""
        
        return "processing $currentImage/$totalImage" + if (timeEstimate.isNotEmpty()) ", $timeEstimate" else ""
    }
    
    private fun adjustTimeForThreads(timeMillis: Long): Long {
        val threadCount = Runtime.getRuntime().availableProcessors()
        return ((timeMillis * (queuedImages + 1)) / threadCount.toDouble()).roundToInt().toLong()
    }

    private fun invokeCallbackSafely(callback: Any, message: String) {
        try {
            callback::class.java.getMethod("onProgress", String::class.java)
                .invoke(callback, message)
        } catch (e: Exception) {
            // Callback invocation failed - continue silently
        }
    }
    
    fun markAllImagesCompleted(context: Context) {
        allImagesCompleted = true
        queuedImages = 0
        // Notify completion
        val totalImagesProcessed = totalImages.get()
        val notificationHandler = NotificationHandler(context)
        notificationHandler.dismissProcessingNotification()
        notificationHandler.showCompletionNotification(totalImagesProcessed)
    }
}

/**
 * Main processing state management
 */
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
        
        // Delegate to ProcessingProgress instance
        fun setProcessingOrder(order: List<Int>) = processingProgress.setProcessingOrder(order)
        fun setImageChunks(chunkCounts: List<Int>) = processingProgress.setImageChunks(chunkCounts)
        fun onChunkCompleted(imageIdx: Int) = processingProgress.onChunkCompleted(imageIdx)
        fun onChunkCompleted() = processingProgress.onChunkCompleted()
        fun updateImageProgress(current: Int, total: Int) = processingProgress.updateImageProgress(current, total)
        fun updateChunkProgress(totalChunks: Int) = processingProgress.updateChunkProgress(totalChunks)
        fun getStatusString(context: Context, threadCount: Int): String = 
            processingProgress.getStatusString(context, threadCount, modelManager)
        fun updateSingleImageProgress(width: Int, height: Int, callback: Any?) = 
            processingProgress.updateSingleImageProgress(width, height, callback)
        fun markAllImagesCompleted(context: Context) = processingProgress.markAllImagesCompleted(context)
        
        // Getters for chunk progress
        fun getCompletedChunks() = processingProgress.completedChunks
        fun getTotalChunks() = processingProgress.totalChunks
        
        // Legacy compatibility - these can be removed if not used elsewhere
        // val currentImageIndex = AtomicInteger(0)
        // val totalImages = AtomicInteger(0)
        // val activeChunkStart = AtomicInteger(0)
        // val activeChunkEnd = AtomicInteger(0)
        // val totalChunks = AtomicInteger(0)
        // val completedChunks = AtomicInteger(0)
        // var startTime: Long = 0
        
        @JvmField var lastImageWidth: Int = 0
        @JvmField var lastImageHeight: Int = 0
        @JvmField var queuedImages: Int = 0
        @JvmField var allImagesCompleted: Boolean = false
        @JvmField var processingOrder: List<Int> = emptyList()
        @JvmField var chunksPerImage: List<Int> = emptyList()
        @JvmField var chunksCompletedPerImage: MutableList<Int> = mutableListOf()
        
        fun updateChunkProgress(start: Int, end: Int, total: Int, threadCount: Int) {
            // activeChunkStart.set(start)
            // activeChunkEnd.set(end)
            // totalChunks.set(total)
            // Also update the ProcessingProgress instance
            processingProgress.updateChunkProgress(total)
        }

        // Add this function to expose setStatusListener
        fun setStatusListener(listener: ProcessingProgress.StatusListener?) =
            processingProgress.setStatusListener(listener)
    }
}