/*
 * SPDX-FileCopyrightText: 2025 - 2026 dryerlint <https://codeberg.org/dryerlint>
 * SPDX-License-Identifier: GNU Affero General Public License v3.0 or later
 */

package com.je.dejpeg.processing

import android.graphics.Bitmap
import com.je.dejpeg.AppPreferences

interface Processor {
    interface ProcessCallback {
        fun onComplete(result: Bitmap)
        fun onError(error: String)
        fun onProgress(message: String)
    }

    interface OnnxProcessCallback : ProcessCallback {
        fun onChunkProgress(currentChunkIndex: Int, totalChunks: Int, parallelWorkers: Int) {}
    }

    suspend fun processImage(
        inputBitmap: Bitmap,
        params: ProcessingParams,
        callback: ProcessCallback
    )

    fun cancelProcessing()
}

sealed class ProcessingParams {
    data class Onnx(
        val modelName: String? = null,
        val strength: Float = 50f,
        val chunkSize: Int = AppPreferences.DEFAULT_CHUNK_SIZE,
        val overlapSize: Int = AppPreferences.DEFAULT_OVERLAP_SIZE,
        val onnxDeviceThreads: Int = AppPreferences.DEFAULT_ONNX_DEVICE_THREADS,
    ) : ProcessingParams()

    data class Oidn(
        val weightsPath: String? = null,
        val hdr: Boolean = false,
        val srgb: Boolean = false,
        val quality: Int = 0,
        val maxMemoryMB: Int = 0,
        val numThreads: Int = 0,
        val inputScale: Float = 0f,
    ) : ProcessingParams()
}
