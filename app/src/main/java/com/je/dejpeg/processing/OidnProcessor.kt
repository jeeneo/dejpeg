package com.je.dejpeg.processing

import android.content.Context
import android.graphics.Bitmap
import com.je.dejpeg.BuildConfig
import com.je.dejpeg.R
import com.je.dejpeg.utils.PortableFloatMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class OIDNProcessor(private val context: Context) : Processor {
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

    override fun cancelProcessing() {
        isCancelled = true
    }

    override suspend fun processImage(
        inputBitmap: Bitmap,
        params: ProcessingParams,
        callback: Processor.ProcessCallback
    ) = withContext(Dispatchers.Default) {
        if (params !is ProcessingParams.Oidn) {
            throw IllegalArgumentException("OIDNProcessor requires ProcessingParams.Oidn")
        }
        isCancelled = false
        try {
            withContext(Dispatchers.Main) { callback.onProgress(context.getString(R.string.processing)) }
            val config = Bitmap.Config.ARGB_8888
            val result = processChunk(
                inputBitmap,
                params.weightsPath,
                params.numThreads,
                params.quality,
                params.maxMemoryMB,
                params.hdr,
                params.srgb,
                params.inputScale,
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