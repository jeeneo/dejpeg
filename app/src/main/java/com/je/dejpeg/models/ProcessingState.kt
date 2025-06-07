package com.je.dejpeg.models

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.je.dejpeg.ImageProcessor
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.roundToInt
import kotlin.reflect.KClass

object ProcessingTimeStats {
    private var avgChunkTimePerMP: Double? = null
    private var avgSingleImageTimePerMP: Double? = null

    private const val PREFS_NAME = "processing_time_stats"
    private const val KEY_CHUNK = "avg_chunk_time_per_mp"
    private const val KEY_SINGLE = "avg_single_time_per_mp"

    fun recordChunkTime(width: Int, height: Int, millis: Long, context: Context? = null) {
        calculateAndUpdateTime(width, height, millis, context) { newTimePerMP ->
            avgChunkTimePerMP = avgChunkTimePerMP?.let { it * 0.7 + newTimePerMP * 0.3 } ?: newTimePerMP
        }
    }

    fun recordSingleImageTime(width: Int, height: Int, millis: Long, context: Context? = null) {
        calculateAndUpdateTime(width, height, millis, context) { newTimePerMP ->
            avgSingleImageTimePerMP = avgSingleImageTimePerMP?.let { it * 0.7 + newTimePerMP * 0.3 } ?: newTimePerMP
        }
    }

    private inline fun calculateAndUpdateTime(
        width: Int,
        height: Int,
        millis: Long,
        context: Context?,
        updateFn: (Double) -> Unit
    ) {
        val mp = calculateMegapixels(width, height)
        if (mp > 0) {
            val timePerMP = millis / mp
            updateFn(timePerMP)
            context?.let { save(it) }
        }
    }

    private fun calculateMegapixels(width: Int, height: Int): Double {
        return (width * height) / 1_000_000.0
    }

    fun estimateChunkTimeMillis(width: Int, height: Int): Long? {
        return estimateTime(width, height, avgChunkTimePerMP)
    }

    fun estimateSingleImageTimeMillis(width: Int, height: Int): Long? {
        return estimateTime(width, height, avgSingleImageTimePerMP)
    }

    private fun estimateTime(width: Int, height: Int, timePerMP: Double?): Long? {
        val mp = calculateMegapixels(width, height)
        return timePerMP?.let { (it * mp).toLong() }
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

sealed class ProcessingState {
    object Idle : ProcessingState()
    object Loading : ProcessingState()
    data class Processing(val progress: Int) : ProcessingState()
    data class Error(val message: String) : ProcessingState()
    data class Success(val message: String) : ProcessingState()

    companion object {
        private const val MILLIS_PER_SECOND = 1000L
        private const val SECONDS_PER_MINUTE = 60

        val currentImageIndex = AtomicInteger(0)
        val totalImages = AtomicInteger(0)
        val activeChunkStart = AtomicInteger(0)
        val activeChunkEnd = AtomicInteger(0)
        val totalChunks = AtomicInteger(0)
        val completedChunks = AtomicInteger(0)
        var startTime: Long = 0

        @JvmField
        var lastImageWidth: Int = 0
        
        @JvmField
        var lastImageHeight: Int = 0

        @JvmField
        var queuedImages: Int = 0

        @JvmField
        var allImagesCompleted: Boolean = false

        @JvmField
        var processingOrder: List<Int> = emptyList()

        @JvmField
        var chunksPerImage: List<Int> = emptyList()

        @JvmField
        var chunksCompletedPerImage: MutableList<Int> = mutableListOf()

        private var lastSingleImageTimeEstimate: String? = null
        private var lastSingleImageMillis: Long? = null
        private var lastTimeEstimate: String? = null

        fun setProcessingOrder(order: List<Int>) {
            processingOrder = order
        }

        fun setImageChunks(chunkCounts: List<Int>) {
            chunksPerImage = chunkCounts
            chunksCompletedPerImage = MutableList(chunkCounts.size) { 0 }
        }

        fun onChunkCompleted(imageIdx: Int) {
            if (imageIdx in chunksCompletedPerImage.indices) {
                chunksCompletedPerImage[imageIdx]++
            }
        }

        fun updateImageProgress(current: Int, total: Int) {
            startTime = System.currentTimeMillis()
            currentImageIndex.set(current)
            totalImages.set(total)
            completedChunks.set(0)
            queuedImages = total
            allImagesCompleted = false
            chunksCompletedPerImage.clear()
            lastTimeEstimate = null
        }

        fun updateChunkProgress(start: Int, end: Int, total: Int, threadCount: Int) {
            activeChunkStart.set(start)
            activeChunkEnd.set(end)
            totalChunks.set(total)
        }

        fun getStatusString(context: Context, threadCount: Int): String {
            val currentImage = currentImageIndex.get()
            val totalImage = totalImages.get()
            val currentImageIdx = currentImage - 1 // 0-based

            val totalWork = chunksPerImage.sum().takeIf { it > 0 } ?: totalImage
            val workDone = chunksCompletedPerImage.sum()
            val percentage = if (totalWork > 0) (workDone * 100 / totalWork) else 0

            val displayImageNumber = getDisplayImageNumber(currentImage, currentImageIdx)
            val queuedInfo = getQueuedInfo()
            val timeEstimate = calculateTimeEstimate(threadCount, totalImage, currentImage)

            return if (com.je.dejpeg.MainActivity.isProgressPercentage) {
                "processing $percentage% complete...${timeEstimate ?: ""}$queuedInfo"
            } else {
                val (activeChunk, totalChunk) = getChunkProgress(currentImageIdx)
                "processing image $displayImageNumber/$totalImage - chunks $activeChunk/$totalChunk...${timeEstimate ?: ""}$queuedInfo"
            }
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

        private fun calculateTimeEstimate(threadCount: Int, totalImage: Int, currentImage: Int): String? {
            val showFullImageEstimate = totalChunks.get() > 1 && lastSingleImageMillis != null
            val shouldUpdateEstimate = completedChunks.get() % threadCount == 0

            return when {
                showFullImageEstimate && shouldUpdateEstimate -> {
                    calculateChunkBasedEstimate(threadCount).also { lastTimeEstimate = it }
                }
                showFullImageEstimate && !shouldUpdateEstimate -> {
                    lastTimeEstimate
                }
                totalChunks.get() == 1 && ProcessingTimeStats.hasSingleImageEstimate() -> {
                    calculateSingleImageEstimate(totalImage, currentImage)
                }
                else -> null
            }
        }

        private fun calculateChunkBasedEstimate(threadCount: Int): String? {
            val chunksLeft = totalChunks.get() - completedChunks.get()
            return if (completedChunks.get() > 0 && lastSingleImageMillis != null) {
                val avgChunkMillis = lastSingleImageMillis!! / totalChunks.get()
                val effectiveMillis = (avgChunkMillis * chunksLeft) / threadCount
                formatTimeEstimate(effectiveMillis)
            } else {
                lastSingleImageTimeEstimate
            }
        }

        private fun calculateSingleImageEstimate(totalImage: Int, currentImage: Int): String? {
            val remainingImages = totalImage - currentImage + 1
            return ProcessingTimeStats.estimateSingleImageTimeMillis(lastImageWidth, lastImageHeight)?.let {
                formatTimeEstimate(it * remainingImages)
            }
        }

        private fun formatTimeEstimate(millis: Long): String {
            val remainingSeconds = millis / MILLIS_PER_SECOND
            val minutes = remainingSeconds / SECONDS_PER_MINUTE
            val seconds = remainingSeconds % SECONDS_PER_MINUTE
            
            return if (minutes > 0) " (~$minutes min remaining)" else " (~$seconds sec remaining)"
        }

        fun markAllImagesCompleted() {
            allImagesCompleted = true
            queuedImages = 0
        }

        fun updateSingleImageProgress(width: Int, height: Int, callback: Any?) {
            lastImageWidth = width
            lastImageHeight = height
            callback ?: return

            val estimateMillis = getTimeEstimateForImage(width, height)
            val isLastImage = currentImageIndex.get() == totalImages.get()
            val timeEstimate = formatSingleImageTimeEstimate(estimateMillis, isLastImage)

            val message = buildStatusMessage(width, height, isLastImage, timeEstimate)
            invokeCallbackSafely(callback, message)
        }

        private fun getTimeEstimateForImage(width: Int, height: Int): Long? {
            return if (width > ImageProcessor.CHUNK_SIZE || height > ImageProcessor.CHUNK_SIZE) {
                ProcessingTimeStats.estimateChunkTimeMillis(width, height)?.also {
                    lastSingleImageMillis = it
                }
            } else {
                ProcessingTimeStats.estimateSingleImageTimeMillis(width, height)
            }
        }

        private fun formatSingleImageTimeEstimate(estimateMillis: Long?, isLastImage: Boolean): String {
            return estimateMillis?.let {
                val seconds = it / MILLIS_PER_SECOND
                val minutes = seconds / SECONDS_PER_MINUTE
                val remainingSeconds = seconds % SECONDS_PER_MINUTE
                
                if (isLastImage) {
                    if (minutes > 0) "$minutes min" else "$remainingSeconds sec"
                } else {
                    val totalRemainingImages = totalImages.get() - currentImageIndex.get() + 1
                    val totalSeconds = (it * totalRemainingImages) / MILLIS_PER_SECOND
                    val totalMinutes = totalSeconds / SECONDS_PER_MINUTE
                    val totalRemainingSeconds = totalSeconds % SECONDS_PER_MINUTE
                    
                    val currentEstimate = if (minutes > 0) "$minutes min" else "$remainingSeconds sec"
                    val totalEstimate = if (totalMinutes > 0) "$totalMinutes min" else "$totalRemainingSeconds sec"
                    
                    "$currentEstimate for current, $totalEstimate for all"
                }
            } ?: ""
        }

        private fun adjustTimeEstimate(timeEstimate: String): String {
            return if (timeEstimate.isNotEmpty()) {
                val threadCount = Runtime.getRuntime().availableProcessors()
                timeEstimate.replace(Regex("(\\d+)"), { matchResult ->
                    ((matchResult.value.toInt() * (queuedImages + 1) / threadCount.toDouble()).roundToInt()).toString()
                })
            } else timeEstimate
        }

        private fun buildStatusMessage(
            width: Int,
            height: Int,
            isLastImage: Boolean,
            timeEstimate: String
        ): String {
            return if (width > ImageProcessor.CHUNK_SIZE || height > ImageProcessor.CHUNK_SIZE) {
                val adjustedTime = adjustTimeEstimate(timeEstimate)
                "loading..." + if (isLastImage) " ~$adjustedTime remaining" else adjustedTime
            } else {
                "processing ${currentImageIndex.get()}/${totalImages.get()}, " +
                    if (isLastImage) "~$timeEstimate remaining" else timeEstimate
            }
        }

        private fun invokeCallbackSafely(callback: Any, message: String) {
            try {
                callback::class.java.getMethod("onProgress", String::class.java)
                    .invoke(callback, message)
            } catch (e: Exception) {
                // idek
            }
        }
    }
}