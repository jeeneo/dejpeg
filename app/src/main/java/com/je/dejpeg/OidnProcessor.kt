/* SPDX-FileCopyrightText: 2026 dryerlint <https://codeberg.org/dryerlint>
 * SPDX-License-Identifier: GNU Affero General Public License v3.0 or later
 */

@file:Suppress("SpellCheckingInspection")

package com.je.dejpeg

import android.content.Context
import android.graphics.Bitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class OidnProcessor(private val context: Context) {

    companion object {

        init {
            @Suppress("KotlinConstantConditions")
            if (BuildConfig.OIDN_ENABLED) {
                System.loadLibrary("oidn_jni")
            }
        }
    }

    private external fun nativeDenoise(
        color: FloatArray,
        width: Int,
        height: Int,
        weightsPath: String?,
        numThreads: Int,
        quality: Int,
        maxMemoryMB: Int,
        hdr: Boolean,
        srgb: Boolean,
        inputScale: Float
    ): FloatArray?

    @Volatile
    private var isCancelled = false

    interface ProcessCallback {
        fun onComplete(result: Bitmap)
        fun onError(error: String)
        fun onProgress(message: String)
    }

    fun cancelProcessing() {
        isCancelled = true
    }

    suspend fun processImage(
        inputBitmap: Bitmap,
        weightsPath: String? = null,
        numThreads: Int = 0,
        quality: Int = 0,
        maxMemoryMB: Int = 0,
        hdr: Boolean = false,
        srgb: Boolean = false,
        inputScale: Float = 0f,
        callback: ProcessCallback
    ) = withContext(Dispatchers.Default) {
        isCancelled = false
        try {
            withContext(Dispatchers.Main) { callback.onProgress(context.getString(R.string.processing)) }
            val config = Bitmap.Config.ARGB_8888
            val result = processChunk(
                inputBitmap,
                weightsPath,
                numThreads,
                quality,
                maxMemoryMB,
                hdr,
                srgb,
                inputScale,
                config
            )
            withContext(Dispatchers.Main) { callback.onComplete(result) }
        } catch (e: Exception) {
            val msg = if (isCancelled) context.getString(R.string.error_processing_cancelled)
            else e.message?.takeIf { it.isNotBlank() } ?: e.javaClass.simpleName
            withContext(Dispatchers.Main) { callback.onError(msg) }
        }
    }

    private fun processChunk(
        src: Bitmap,
        weightsPath: String?,
        numThreads: Int,
        quality: Int,
        maxMemoryMB: Int,
        hdr: Boolean,
        srgb: Boolean,
        inputScale: Float,
        config: Bitmap.Config
    ): Bitmap {
        return try {
            val (pfm, alphaBuf) = PortableFloatMap.fromBitmap(src)

            val denoised = nativeDenoise(
                pfm.pixels,
                pfm.width,
                pfm.height,
                weightsPath,
                numThreads,
                quality,
                maxMemoryMB,
                hdr,
                srgb,
                inputScale
            ) ?: throw RuntimeException("Oidn native denoise failed (is a model loaded?)")

            PortableFloatMap.toBitmap(denoised, pfm.width, pfm.height, alphaBuf, config)
        } catch (e: Exception) {
            throw RuntimeException("Error: ${e.message}", e)
        }
    }
}
