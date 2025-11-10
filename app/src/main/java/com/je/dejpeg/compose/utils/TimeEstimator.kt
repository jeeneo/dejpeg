package com.je.dejpeg

import android.content.Context

class TimeEstimator(private val context: Context, private val modelName: String) {
    private val prefs = context.getSharedPreferences("TimeEstimates", Context.MODE_PRIVATE)
    private var chunkStartTime: Long = 0
    private var processingStartTime: Long = 0
    private val chunkTimes = mutableListOf<Long>()
    private var inMemoryAverage: Long = 0
    private var lastInitialEstimate: Long = 0
    private var lastOverallEstimate: Long = 0
    private var expectedTotalChunks: Int = 0

    fun getStoredAverageTime(): Long {
        return prefs.getLong("avg_${modelName}", getDefaultEstimate())
    }

    private fun saveAverageTime(avgTime: Long) {
        prefs.edit().putLong("avg_${modelName}", avgTime).apply()
    }

    private fun getDefaultEstimate(): Long {
        return when {
            modelName.startsWith("fbcnn_") -> 10000L
            modelName.startsWith("scunet_") -> 11000L
            else -> Long.MAX_VALUE
        }
    }

    fun startProcessing() {
        processingStartTime = System.currentTimeMillis()
        if (chunkTimes.isEmpty()) {
            inMemoryAverage = getStoredAverageTime()
        }
        lastInitialEstimate = 0
        lastOverallEstimate = 0
        expectedTotalChunks = 0
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
        expectedTotalChunks = totalChunks
        if (chunkTimes.isEmpty()) return 0
        val totalEstimate = computeOverallEstimate(totalChunks)
        if (totalEstimate > lastOverallEstimate) {
            lastOverallEstimate = totalEstimate
        } else if (lastOverallEstimate == 0L) {
            lastOverallEstimate = totalEstimate
        }
        val elapsed = (System.currentTimeMillis() - processingStartTime).coerceAtLeast(0)
        val remaining = (lastOverallEstimate - elapsed).coerceAtLeast(0)
        return remaining
    }

    fun getInitialEstimate(totalChunks: Int): Long {
        expectedTotalChunks = totalChunks
        lastInitialEstimate = 0
        lastOverallEstimate = 0
        return 0
    }

    private fun getAdaptiveAverageTime(totalChunks: Int): Long {
        val measuredAvg = if (chunkTimes.isNotEmpty()) chunkTimes.first() else 0L
        val storedAvg = if (inMemoryAverage > 0) inMemoryAverage else getStoredAverageTime()
        val weight = if (totalChunks > 0) {
            (8.0 / totalChunks).coerceAtMost(1.0)
        } else {
            1.0
        }
        return if (measuredAvg > 0) {
            (measuredAvg * weight + storedAvg * (1.0 - weight)).toLong()
        } else {
            storedAvg
        }
    }

    private fun computeOverallEstimate(totalChunks: Int): Long {
        val perChunk = computeWeightedAverageMs()
        val overhead = computeWarmupOverheadMs()

        if (perChunk == Double.POSITIVE_INFINITY) return Long.MAX_VALUE
        val base = safeMultiply(perChunk, totalChunks)
        val total = safeAdd(base, overhead)
        return total
    }

    private fun computeWeightedAverageMs(): Double {
        val stored = (if (inMemoryAverage > 0) inMemoryAverage else getStoredAverageTime()).toDouble()
        if (stored == Long.MAX_VALUE.toDouble()) return Double.POSITIVE_INFINITY
        if (chunkTimes.isEmpty()) return stored
        val measured = robustAverage(chunkTimes)
        var weightMeasured = (chunkTimes.size / 4.0).coerceAtMost(1.0)
        if (measured > stored) weightMeasured = (weightMeasured * 1.25).coerceAtMost(1.0)
        val measuredDownwardClamped = if (chunkTimes.size < 3) {
            kotlin.math.max(measured, stored * 0.9)
        } else measured

        return measuredDownwardClamped * weightMeasured + stored * (1.0 - weightMeasured)
    }

    private fun computeWarmupOverheadMs(): Long {
        if (chunkTimes.size <= 1) return 0
        val first = chunkTimes.first()
        val othersAvg = if (chunkTimes.size > 1) {
            chunkTimes.drop(1).average()
        } else 0.0
        val overhead = (first - othersAvg).toLong()
        return overhead.coerceAtLeast(0)
    }

    private fun robustAverage(times: List<Long>): Double {
        if (times.isEmpty()) return 0.0
        if (times.size < 3) return times.average()
        val sorted = times.sorted()
        val trimmed = sorted.drop(1).dropLast(1)
        return if (trimmed.isEmpty()) sorted.average() else trimmed.average()
    }

    private fun safeMultiply(aMs: Double, b: Int): Long {
        if (aMs.isInfinite() || aMs.isNaN()) return Long.MAX_VALUE
        val res = aMs * b
        return if (res >= Long.MAX_VALUE.toDouble()) Long.MAX_VALUE else res.toLong()
    }

    private fun safeAdd(a: Long, b: Long): Long {
        val res = a + b
        if ((a xor b) >= 0 && (a xor res) < 0) return Long.MAX_VALUE
        return res
    }
}
