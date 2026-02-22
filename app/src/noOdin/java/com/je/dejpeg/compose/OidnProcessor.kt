package com.je.dejpeg.compose

import android.content.Context
import android.graphics.Bitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class OidnProcessor(private val context: Context) {

    interface ProcessCallback {
        fun onComplete(result: Bitmap)
        fun onError(error: String)
        fun onProgress(message: String)
    }

    fun cancelProcessing() { /* meow */ }

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
        withContext(Dispatchers.Main) {
            callback.onError("im a cat")
        }
    }
}
