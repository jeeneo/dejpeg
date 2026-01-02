package com.je.dejpeg.compose.utils

import android.content.Context
import com.je.dejpeg.data.dataStore
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class TimeEstimator(
    private val context: Context,
    private val modelName: String,
    private val chunkSize: Int = DEFAULT_CHUNK_SIZE,
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) {
    companion object {
        private const val DEFAULT_CHUNK_SIZE = 512
        private const val REFERENCE_CHUNK_SIZE = 512
        
        fun formatTimeRemaining(millis: Long, finishingUpText: String = "Finishing up..."): String {
            if (millis < 0) return ""
            val seconds = (millis / 1000).coerceAtLeast(0)
            return when {
                seconds <= 0 -> finishingUpText
                seconds < 60 -> "$seconds s"
                seconds < 3600 -> {
                    val minutes = seconds / 60
                    val remainingSeconds = seconds % 60
                    if (remainingSeconds == 0L) {
                        if (minutes == 1L) "1 minute" else "$minutes m"
                    } else {
                        if (minutes == 1L) "1m, $remainingSeconds s" else "$minutes m, $remainingSeconds s"
                    }
                }
                else -> {
                    val hours = seconds / 3600
                    val remainingMinutes = (seconds % 3600) / 60
                    if (remainingMinutes == 0L) {
                        if (hours == 1L) "1 h" else "$hours h"
                    } else {
                        if (hours == 1L) "1 h, $remainingMinutes m" else "$hours h, $remainingMinutes m"
                    }
                }
            }
        }
    }
    
    private val avgTimeKey = longPreferencesKey("time_avg_normalized_${modelName}")
    private var chunkStartTime: Long = 0
    private val chunkTimes = mutableListOf<Long>()
    private var inMemoryAverage: Long = 0
    private var cachedStoredAverage: Long? = null
    private var hasStoredHistory: Boolean = false
    private val chunkScaleFactor: Double
        get() {
            val currentArea = chunkSize.toLong() * chunkSize
            val referenceArea = REFERENCE_CHUNK_SIZE.toLong() * REFERENCE_CHUNK_SIZE
            return currentArea.toDouble() / referenceArea
        }

    fun getStoredAverageTime(): Long? {
        if (cachedStoredAverage != null) return cachedStoredAverage
        return runBlocking {
            context.dataStore.data.map { prefs ->
                prefs[avgTimeKey]?.also { hasStoredHistory = true }
            }.first().also { cachedStoredAverage = it }
        }
    }
    
    private fun getScaledStoredAverage(): Long? {
        val normalizedAvg = getStoredAverageTime() ?: return null
        return (normalizedAvg * chunkScaleFactor).toLong()
    }

    fun hasHistory(): Boolean {
        if (cachedStoredAverage == null) {
            getStoredAverageTime()
        }
        return hasStoredHistory || chunkTimes.isNotEmpty()
    }

    private suspend fun saveAverageTimeAsync(avgTime: Long) {
        cachedStoredAverage = avgTime
        context.dataStore.edit { prefs ->
            prefs[avgTimeKey] = avgTime
        }
    }

    private fun saveAverageTime(avgTime: Long) {
        cachedStoredAverage = avgTime
        coroutineScope.launch {
            saveAverageTimeAsync(avgTime)
        }
    }

    fun startProcessing() {
        chunkTimes.clear()
        if (inMemoryAverage == 0L) {
            inMemoryAverage = getScaledStoredAverage() ?: 0L
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
        val newChunkTime = chunkTimes.last()
        val normalizedNewTime = (newChunkTime / chunkScaleFactor).toLong()
        val currentNormalizedAverage = getStoredAverageTime()
        val updatedNormalizedAverage = if (currentNormalizedAverage != null && currentNormalizedAverage > 0) {
            (currentNormalizedAverage * 0.8 + normalizedNewTime * 0.2).toLong()
        } else {
            normalizedNewTime
        }
        inMemoryAverage = (updatedNormalizedAverage * chunkScaleFactor).toLong()
        saveAverageTime(updatedNormalizedAverage)
    }

    fun getEstimatedTimeRemaining(completedChunks: Int, totalChunks: Int): Long {
        if (totalChunks <= 0 || completedChunks >= totalChunks) return 0
        val remainingChunks = totalChunks - completedChunks
        
        val perChunkMs: Double = if (chunkTimes.isNotEmpty()) {
            val recentTimes = chunkTimes.takeLast(3)
            recentTimes.average()
        } else if (inMemoryAverage > 0) {
            inMemoryAverage.toDouble()
        } else {
            val scaled = getScaledStoredAverage()
            if (scaled != null && scaled > 0) scaled.toDouble() else return -1L
        }
        
        return if (chunkStartTime > 0) {
            val currentChunkElapsed = System.currentTimeMillis() - chunkStartTime
            val currentChunkRemaining = (perChunkMs - currentChunkElapsed).coerceAtLeast(0.0)
            val futureChunksTime = perChunkMs * (remainingChunks - 1).coerceAtLeast(0)
            (currentChunkRemaining + futureChunksTime).toLong()
        } else {
            (perChunkMs * remainingChunks).toLong()
        }
    }

    fun getInitialEstimate(totalChunks: Int): Long {
        if (totalChunks <= 0) return -1L
        
        val perChunkMs: Long = when {
            chunkTimes.isNotEmpty() -> chunkTimes.average().toLong()
            inMemoryAverage > 0 -> inMemoryAverage
            else -> getScaledStoredAverage() ?: return -1L
        }
        
        return perChunkMs * totalChunks
    }
}
