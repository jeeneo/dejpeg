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

package com.je.dejpeg.utils

class ProcessingQueueManager {

    private val processingQueue = mutableListOf<String>()
    var activeProcessingTotal = 0
        private set
    var isProcessingQueue = false
        private set
    var cancelInProgress = false
    var currentProcessingId: String? = null
        private set

    val queueSize: Int get() = processingQueue.size
    val isEmpty: Boolean get() = processingQueue.isEmpty()

    fun enqueue(ids: List<String>) {
        processingQueue.clear()
        processingQueue.addAll(ids)
        activeProcessingTotal = processingQueue.size
        isProcessingQueue = true
    }

    fun enqueueSingle(id: String) {
        processingQueue.add(id)
        isProcessingQueue = true
    }

    fun dequeue(): String? {
        if (processingQueue.isEmpty()) return null
        return processingQueue.removeAt(0)
    }

    fun remove(id: String): Boolean {
        return processingQueue.remove(id)
    }

    fun contains(id: String): Boolean = processingQueue.contains(id)

    fun setCurrentProcessing(id: String?) {
        currentProcessingId = id
    }

    fun setActiveTotal(total: Int) {
        activeProcessingTotal = total
    }

    fun decrementActiveTotal() {
        if (activeProcessingTotal > 0) activeProcessingTotal--
    }

    fun updateIsProcessingQueue() {
        isProcessingQueue = processingQueue.isNotEmpty()
    }

    fun clear() {
        processingQueue.clear()
        isProcessingQueue = false
        activeProcessingTotal = 0
    }

    fun resetProcessingState() {
        isProcessingQueue = false
        activeProcessingTotal = 0
        currentProcessingId = null
    }

    fun isActive(id: String): Boolean = currentProcessingId == id

    /** Compute the current index for UI progress display */
    fun currentIndex(): Int {
        val total = if (activeProcessingTotal > 0) activeProcessingTotal else 0
        return (total - processingQueue.size - 1).coerceAtLeast(0)
    }
}
