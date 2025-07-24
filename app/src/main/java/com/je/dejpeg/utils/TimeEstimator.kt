package com.je.dejpeg.utils

import android.content.Context

class TimeEstimator(private val context: Context, private val modelName: String) {
    private val prefs = context.getSharedPreferences("TimeEstimates", Context.MODE_PRIVATE)
    private var chunkStartTime: Long = 0
    private var processingStartTime: Long = 0
    private val chunkTimes = mutableListOf<Long>()
    private var inMemoryAverage: Long = 0
    private var lastInitialEstimate: Long = 0

    private fun getStoredAverageTime(): Long {
        return prefs.getLong("avg_${modelName}", getDefaultEstimate())
    }

    private fun saveAverageTime(avgTime: Long) {
        prefs.edit().putLong("avg_${modelName}", avgTime).apply()
    }

    private fun getDefaultEstimate(): Long {
        return when {
            modelName.startsWith("fbcnn_") -> 10000L
            modelName.startsWith("scunet_") -> 11000L
            else -> 1110L
        }
    }

    fun startProcessing() {
        processingStartTime = System.currentTimeMillis()
        // Load stored average as in-memory average if no chunks yet
        if (chunkTimes.isEmpty()) {
            inMemoryAverage = getStoredAverageTime()
        }
    }

    fun startChunk() {
        chunkStartTime = System.currentTimeMillis()
    }

    fun endChunk() {
        if (chunkStartTime > 0) {
            val chunkTime = System.currentTimeMillis() - chunkStartTime
            chunkTimes.add(chunkTime)
            updateAverageTime()
            chunkStartTime = 0
        }
    }

    private fun updateAverageTime() {
        if (chunkTimes.isEmpty()) return
        val currentAverage = if (inMemoryAverage > 0) inMemoryAverage else getStoredAverageTime()
        val newChunkTime = chunkTimes.last()
        val updatedAverage = (currentAverage * 0.8 + newChunkTime * 0.2).toLong()
        inMemoryAverage = updatedAverage
        saveAverageTime(updatedAverage)
    }

    fun getEstimatedTimeRemaining(completedChunks: Int, totalChunks: Int): Long {
        if (totalChunks <= 0 || completedChunks >= totalChunks) return 0
        val remainingChunks = totalChunks - completedChunks
        val averageTime = getAdaptiveAverageTime(totalChunks)
        return remainingChunks * averageTime
    }

    fun getInitialEstimate(totalChunks: Int): Long {
        val avg = getAdaptiveAverageTime(totalChunks)
        lastInitialEstimate = totalChunks * avg
        return lastInitialEstimate
    }

    /**
     * Returns an adaptive average time per chunk, factoring in the number of chunks.
     * For small images (few chunks), if we have a measured chunk time, weight it more.
     * For large images, rely more on the stored average.
     */
    private fun getAdaptiveAverageTime(totalChunks: Int): Long {
        // If we have measured chunk times, use the first as a base for small images
        val measuredAvg = if (chunkTimes.isNotEmpty()) chunkTimes.first() else 0L
        val storedAvg = if (inMemoryAverage > 0) inMemoryAverage else getStoredAverageTime()

        // Use a smooth weighting: for small chunk counts, weight measured more
        // For example, weight = min(1.0, 8.0 / totalChunks)
        val weight = if (totalChunks > 0) {
            (8.0 / totalChunks).coerceAtMost(1.0)
        } else {
            1.0
        }

        // If no measured, just use stored
        return if (measuredAvg > 0) {
            (measuredAvg * weight + storedAvg * (1.0 - weight)).toLong()
        } else {
            storedAvg
        }
    }

    fun formatTimeRemaining(milliseconds: Long): String {
        if (milliseconds <= 0) return "Finishing up..."
        val seconds = milliseconds / 1000
        return when {
            seconds < 60 -> "${seconds}s remaining"
            seconds < 3600 -> {
                val minutes = seconds / 60
                val remainingSeconds = seconds % 60
                "${minutes}m ${remainingSeconds}s remaining"
            }
            else -> {
                val hours = seconds / 3600
                val remainingMinutes = (seconds % 3600) / 60
                "${hours}h ${remainingMinutes}m remaining"
            }
        }
    }
}
