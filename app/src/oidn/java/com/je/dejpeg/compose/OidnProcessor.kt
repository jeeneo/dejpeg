/**
 * Copyright (C) 2026 dryerlint <codeberg.org/dryerlint>
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

/*
* If you use this code in your own project, please give credit
*/

package com.je.dejpeg.compose

import android.content.Context
import android.graphics.Bitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.je.dejpeg.R

class OidnProcessor(private val context: Context) {

    companion object {
        private const val TAG = "OidnProcessor"

        init {
            System.loadLibrary("oidn_jni")
        }

        @JvmStatic
        external fun nativeGetDeviceCount(): Int

        @JvmStatic
        external fun nativeGetDeviceName(): String
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
