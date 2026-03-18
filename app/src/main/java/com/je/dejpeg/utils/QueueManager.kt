/*
 * SPDX-FileCopyrightText: 2025 - 2026 dryerlint <https://codeberg.org/dryerlint>
 * SPDX-License-Identifier: GNU Affero General Public License v3.0 or later
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
    var singleImageCancelId: String? = null
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
        singleImageCancelId = null
    }

    fun resetProcessingState() {
        isProcessingQueue = false
        activeProcessingTotal = 0
        currentProcessingId = null
        singleImageCancelId = null
    }

    fun isActive(id: String): Boolean = currentProcessingId == id

    fun currentIndex(): Int {
        val total = if (activeProcessingTotal > 0) activeProcessingTotal else 0
        return (total - processingQueue.size - 1).coerceAtLeast(0)
    }
}
