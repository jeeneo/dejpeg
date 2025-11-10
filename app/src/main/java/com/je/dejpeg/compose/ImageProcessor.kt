package com.je.dejpeg

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import ai.onnxruntime.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.FloatBuffer
import java.nio.ByteBuffer
import java.nio.ByteOrder
import androidx.core.graphics.createBitmap
import kotlin.math.ceil

class ImageProcessor(
    private val context: Context,
    private val modelManager: ModelManager
) {
    var customChunkSize: Int? = null
    var customOverlapSize: Int? = null

    companion object {
        const val DEFAULT_CHUNK_SIZE = 512
        const val OVERLAP = 16
        private const val TILE_MEMORY_THRESHOLD = 4096 * 4096
    }

    @Volatile
    private var isCancelled = false

    interface ProcessCallback {
        fun onComplete(result: Bitmap)
        fun onError(error: String)
        fun onProgress(message: String)
        fun onTimeEstimate(timeRemaining: Long)
    }

    fun cancelProcessing() {
        isCancelled = true
    }

    suspend fun processImage(
        inputBitmap: Bitmap,
        strength: Float,
        callback: ProcessCallback,
        index: Int,
        total: Int
    ) = withContext(Dispatchers.Default) {
        isCancelled = false

        try {
            val modelName = modelManager.getActiveModelName()
            val session = modelManager.loadModel()
                ?: throw Exception(context.getString(R.string.error_failed_to_load_model))
            val modelInfo = ModelInfo(modelName, strength, session, customChunkSize, customOverlapSize)
            val timeEstimator = TimeEstimator(context, modelName ?: "unknown")
            timeEstimator.startProcessing()
            val result = processBitmapUnified(session, inputBitmap, callback, modelInfo, timeEstimator, index, total)
            withContext(Dispatchers.Main) {
                callback.onComplete(result)
            }
        } catch (e: Exception) {
            if (isCancelled) {
                withContext(Dispatchers.Main) {
                    callback.onError(context.getString(R.string.error_processing_cancelled))
                }
            } else {
                withContext(Dispatchers.Main) {
                    callback.onError(formatError(e))
                }
            }
        }
    }

    private suspend fun processBitmapUnified(
        session: OrtSession,
        inputBitmap: Bitmap,
        callback: ProcessCallback,
        info: ModelInfo,
        timeEstimator: TimeEstimator,
        index: Int,
        total: Int
    ): Bitmap {
        val width = inputBitmap.getWidth()
        val height = inputBitmap.getHeight()
        val hasTransparency = detectTransparency(inputBitmap)

        val processingConfig = Bitmap.Config.ARGB_8888
        val mustTile = (width > info.chunkSize || height > info.chunkSize) || (width * height) > TILE_MEMORY_THRESHOLD
        return if (mustTile) processTiled(session, inputBitmap, callback, info, processingConfig, hasTransparency, timeEstimator, index, total)
        else
        {
            val bitmapToProcess = if (inputBitmap.config != processingConfig) inputBitmap.copy(processingConfig, true)
            else inputBitmap
            timeEstimator.startChunk()
            val initialEstimate = timeEstimator.getInitialEstimate(1)
            val progressMessage = { context.getString(R.string.processing) }
            withContext(Dispatchers.Main) {
                callback.onProgress(progressMessage())
                callback.onTimeEstimate(initialEstimate)
            }
            val result = processChunkUnified(session, bitmapToProcess, processingConfig, hasTransparency, info)
            timeEstimator.endChunk()
            result
        }
    }

    private suspend fun processTiled(
        session: OrtSession,
        inputBitmap: Bitmap,
        callback: ProcessCallback,
        info: ModelInfo,
        config: Bitmap.Config,
        hasTransparency: Boolean,
        timeEstimator: TimeEstimator,
        index: Int,
        total: Int
    ): Bitmap {
        val width = inputBitmap.width
        val height = inputBitmap.height
        val maxChunkSize = info.chunkSize
        val overlap = info.overlap
        val cols = Math.max(1, ceil(width.toDouble() / maxChunkSize).toInt())
        val rows = Math.max(1, ceil(height.toDouble() / maxChunkSize).toInt())
        val actualChunkWidth = (width + (cols - 1) * overlap) / cols
        val actualChunkHeight = (height + (rows - 1) * overlap) / rows
        android.util.Log.d("ImageProcessor", "Processing tiled: image=${width}x${height}, max=$maxChunkSize, actual=${actualChunkWidth}x${actualChunkHeight}, grid=${cols}x${rows}, overlap=$overlap")
        val totalChunks = cols * rows
        val result = createBitmap(width, height, config)
        val canvas = android.graphics.Canvas(result)
        var chunkIndex = 0

        for (row in 0 until rows) {
            for (col in 0 until cols) {
                if (isCancelled) throw Exception(context.getString(R.string.error_processing_cancelled))

                val chunkX = Math.max(0, col * (actualChunkWidth - overlap))
                val chunkY = Math.max(0, row * (actualChunkHeight - overlap))
                val chunkW = Math.min(actualChunkWidth, width - chunkX)
                val chunkH = Math.min(actualChunkHeight, height - chunkY)
                
                if (chunkW <= 0 || chunkH <= 0) continue
                
                val chunk = Bitmap.createBitmap(inputBitmap, chunkX, chunkY, chunkW, chunkH)
                val converted = if (chunk.config != config) {
                    val temp = chunk.copy(config, true)
                    chunk.recycle()
                    temp
                } else {
                    chunk
                }
                val currentChunkNumber = chunkIndex + 1
                val progressMessage = if (totalChunks > 1) {
                    context.getString(R.string.processing_chunk_x_of_y, currentChunkNumber, totalChunks)
                } else {
                    context.getString(R.string.processing)
                }
                val timeRemaining = timeEstimator.getEstimatedTimeRemaining(chunkIndex, totalChunks)
                withContext(Dispatchers.Main) {
                    callback.onProgress(progressMessage)
                    callback.onTimeEstimate(timeRemaining)
                }
                timeEstimator.startChunk()
                val processed = processChunkUnified(session, converted, config, hasTransparency, info)
                timeEstimator.endChunk()
                val feathered = createFeatheredChunk(processed, chunkX, chunkY, width, height, overlap, cols, rows, col, row)
                val paint = android.graphics.Paint()
                paint.xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.SRC_OVER)
                canvas.drawBitmap(feathered, chunkX.toFloat(), chunkY.toFloat(), paint)
                converted.recycle()
                processed.recycle()
                feathered.recycle()
                chunkIndex++
            }
        }
        return result
    }

    private fun createFeatheredChunk(
        chunk: Bitmap, 
        chunkX: Int, 
        chunkY: Int, 
        totalWidth: Int, 
        totalHeight: Int, 
        overlap: Int,
        totalCols: Int,
        totalRows: Int,
        col: Int,
        row: Int
    ): Bitmap {
        val chunkW = chunk.width
        val chunkH = chunk.height
        val feathered = chunk.copy(Bitmap.Config.ARGB_8888, true)
        val pixels = IntArray(chunkW * chunkH).apply { feathered.getPixels(this, 0, chunkW, 0, 0, chunkW, chunkH) }
        val featherSize = overlap / 2

        for (y in 0 until chunkH) for (x in 0 until chunkW) {
            val idx = y * chunkW + x
            var alpha = 1.0f
            
            // Only feather edges that border other chunks
            if (col > 0 && x < featherSize) alpha = alpha.coerceAtMost(x.toFloat() / featherSize)
            if (row > 0 && y < featherSize) alpha = alpha.coerceAtMost(y.toFloat() / featherSize)
            if (col < totalCols - 1 && x >= chunkW - featherSize) alpha = alpha.coerceAtMost((chunkW - x).toFloat() / featherSize)
            if (row < totalRows - 1 && y >= chunkH - featherSize) alpha = alpha.coerceAtMost((chunkH - y).toFloat() / featherSize)
            
            pixels[idx] = (pixels[idx] and 0x00FFFFFF) or ((alpha * 255).toInt() shl 24)
        }

        feathered.setPixels(pixels, 0, chunkW, 0, 0, chunkW, chunkH)
        return feathered
    }

    private fun processChunkUnified(
        session: OrtSession,
        chunk: Bitmap,
        config: Bitmap.Config,
        hasAlpha: Boolean,
        info: ModelInfo
    ): Bitmap {
        val w = chunk.width
        val h = chunk.height
        val channels = if (info.isGrayscale) 1 else 3
        val pixels = IntArray(w * h)
        chunk.getPixels(pixels, 0, w, 0, 0, w, h)

        val inputArray = FloatArray(channels * w * h)
        val alphaChannel = if (hasAlpha) FloatArray(w * h) else null

        for (i in 0 until w * h) {
            val color = pixels[i]
            if (channels == 1) {
                val gray = (Color.red(color) + Color.green(color) + Color.blue(color)) / 3
                inputArray[i] = gray / 255f
            } else {
                inputArray[i] = Color.red(color) / 255f
                inputArray[w * h + i] = Color.green(color) / 255f
                inputArray[2 * w * h + i] = Color.blue(color) / 255f
            }
            if (hasAlpha) {
                alphaChannel!![i] = Color.alpha(color) / 255f
            }
        }

        val env = info.env ?: OrtEnvironment.getEnvironment()
        val inputShape = longArrayOf(1, channels.toLong(), h.toLong(), w.toLong())
        val inputs = mutableMapOf<String, OnnxTensor>()
        val inputTensor = if (info.isFp16) {
            val fp16Array = ShortArray(inputArray.size) { i -> floatToFloat16(inputArray[i]) }
            val byteBuffer = ByteBuffer.allocateDirect(fp16Array.size * 2).order(ByteOrder.nativeOrder())
            val shortBuffer = byteBuffer.asShortBuffer()
            shortBuffer.put(fp16Array)
            byteBuffer.rewind()
            OnnxTensor.createTensor(env, byteBuffer, inputShape, OnnxJavaType.FLOAT16)
        } else {
            OnnxTensor.createTensor(env, FloatBuffer.wrap(inputArray), inputShape)
        }
        inputs[info.inputName] = inputTensor

        for ((key, nodeInfo) in info.inputInfoMap) {
            if (key == info.inputName) continue
            val tensorInfo = nodeInfo.info as? TensorInfo ?: continue
            if (tensorInfo.type == OnnxJavaType.FLOAT || tensorInfo.type == OnnxJavaType.FLOAT16) {
                val shape = tensorInfo.shape.clone()
                for (i in shape.indices) {
                    if (shape[i] == -1L) shape[i] = 1L
                }
                if (shape.size == 2 && shape[0] == 1L && shape[1] == 1L) {
                    val strengthTensor = if (tensorInfo.type == OnnxJavaType.FLOAT16) {
                        val strengthFp16 = floatToFloat16(info.strength / 100f)
                        val byteBuffer = ByteBuffer.allocateDirect(2).order(ByteOrder.nativeOrder())
                        byteBuffer.asShortBuffer().put(strengthFp16)
                        byteBuffer.rewind()
                        OnnxTensor.createTensor(env, byteBuffer, shape, OnnxJavaType.FLOAT16)
                    } else {
                        OnnxTensor.createTensor(env, FloatBuffer.wrap(floatArrayOf(info.strength / 100f)), shape)
                    }
                    inputs[key] = strengthTensor
                }
            }
        }

        val result = try {
            session.run(inputs).use { sessionResult ->
                val outputArray = extractOutputArray(sessionResult[0].value, channels, h, w)
                val resultBitmap = createBitmap(w, h, config)
                val outPixels = IntArray(w * h)

                for (i in 0 until w * h) {
                    val alpha = if (hasAlpha) clamp255(alphaChannel!![i] * 255f) else 255
                    
                    if (channels == 1) {
                        val gray = clamp255(outputArray[i] * 255f)
                        outPixels[i] = Color.argb(alpha, gray, gray, gray)
                    } else {
                        val r = clamp255(outputArray[i] * 255f)
                        val g = clamp255(outputArray[w * h + i] * 255f)
                        val b = clamp255(outputArray[2 * w * h + i] * 255f)
                        outPixels[i] = Color.argb(alpha, r, g, b)
                    }
                }

                resultBitmap.setPixels(outPixels, 0, w, 0, 0, w, h)
                resultBitmap
            }
        } finally {
            inputs.values.forEach { it.close() }
        }
        return result
    }

    private fun extractOutputArray(outputValue: Any, channels: Int, h: Int, w: Int): FloatArray {
        android.util.Log.d("ImageProcessor", "Output type received: ${outputValue.javaClass.name}")
        return when (outputValue) {
            is FloatArray -> {
                android.util.Log.d("ImageProcessor", "Output is FloatArray (FP32 or auto-converted from FP16)")
                outputValue
            }
            is ShortArray -> {
                android.util.Log.d("ImageProcessor", "Output is ShortArray (FP16) - converting to Float32")
                FloatArray(outputValue.size) { i -> float16ToFloat(outputValue[i]) }
            }
            is Array<*> -> {
                try {
                    @Suppress("UNCHECKED_CAST")
                    val arr = outputValue as Array<Array<Array<FloatArray>>>
                    android.util.Log.d("ImageProcessor", "Output is multi-dimensional FloatArray")
                    val out = FloatArray(channels * h * w)
                    for (ch in 0 until channels) {
                        for (y in 0 until h) {
                            for (x in 0 until w) {
                                out[ch * h * w + y * w + x] = arr[0][ch][y][x]
                            }
                        }
                    }
                    out
                } catch (e: Exception) {
                    try {
                        @Suppress("UNCHECKED_CAST")
                        val arr = outputValue as Array<Array<Array<ShortArray>>>
                        android.util.Log.d("ImageProcessor", "Output is multi-dimensional ShortArray (FP16)")
                        val out = FloatArray(channels * h * w)
                        for (ch in 0 until channels) {
                            for (y in 0 until h) {
                                for (x in 0 until w) {
                                    out[ch * h * w + y * w + x] = float16ToFloat(arr[0][ch][y][x])
                                }
                            }
                        }
                        out
                    } catch (e2: Exception) {
                        throw RuntimeException("Failed to extract output array: ${e.message}, ${e2.message}")
                    }
                }
            }
            else -> throw RuntimeException("Unexpected ONNX output type: ${outputValue.javaClass}")
        }
    }

    private fun detectTransparency(bitmap: Bitmap): Boolean {
        if (!bitmap.hasAlpha()) return false
        
        val w = bitmap.width
        val h = bitmap.height
        val sampleW = Math.min(w, 64)
        val sampleH = Math.min(h, 64)
        val pixels = IntArray(sampleW * sampleH)
        bitmap.getPixels(pixels, 0, sampleW, 0, 0, sampleW, sampleH)
        
        for (pixel in pixels) {
            if ((pixel ushr 24) != 0xFF) return true
        }
        return false
    }

    private fun clamp255(v: Float): Int {
        return 0.coerceAtLeast(255.coerceAtMost(v.toInt()))
    }

    private fun formatError(e: Exception): String {
        return "Error: ${e.javaClass.simpleName}${if (e.message != null) ": ${e.message}" else ""}"
    }

    private fun floatToFloat16(value: Float): Short {
        val bits = java.lang.Float.floatToIntBits(value)
        val sign = (bits ushr 16) and 0x8000
        var exponent = ((bits ushr 23) and 0xFF) - 127 + 15
        var mantissa = bits and 0x7FFFFF
        
        if (exponent <= 0) {
            if (exponent < -10) {
                return sign.toShort()
            }
            mantissa = mantissa or 0x800000
            mantissa = mantissa shr (1 - exponent)
            return (sign or (mantissa shr 13)).toShort()
        } else if (exponent >= 0x1F) {
            return (sign or 0x7C00).toShort()
        }
        
        return (sign or (exponent shl 10) or (mantissa shr 13)).toShort()
    }

    private fun float16ToFloat(fp16: Short): Float {
        val bits = fp16.toInt() and 0xFFFF
        val sign = (bits and 0x8000) shl 16
        val exponent = (bits and 0x7C00) ushr 10
        val mantissa = bits and 0x3FF
        
        if (exponent == 0) {
            if (mantissa == 0) {
                return java.lang.Float.intBitsToFloat(sign)
            }
            var e = -14
            var m = mantissa
            while ((m and 0x400) == 0) {
                m = m shl 1
                e--
            }
            m = m and 0x3FF
            return java.lang.Float.intBitsToFloat(sign or ((e + 127) shl 23) or (m shl 13))
        } else if (exponent == 0x1F) {
            return java.lang.Float.intBitsToFloat(sign or 0x7F800000 or (mantissa shl 13))
        }
        
        return java.lang.Float.intBitsToFloat(sign or ((exponent - 15 + 127) shl 23) or (mantissa shl 13))
    }

    private class ModelInfo(
        modelName: String?,
        val strength: Float,
        session: OrtSession,
        customChunkSize: Int?,
        customOverlapSize: Int?
    ) {
        val env: OrtEnvironment?
        val inputName: String
        val inputInfoMap: Map<String, NodeInfo>
        val isGrayscale: Boolean
        val isFp16: Boolean
        val chunkSize: Int = customChunkSize ?: DEFAULT_CHUNK_SIZE
        val overlap: Int = customOverlapSize ?: OVERLAP

        init {
            android.util.Log.d("ModelInfo", "Initialized with customChunkSize: $customChunkSize, customOverlapSize: $customOverlapSize -> chunkSize: $chunkSize, overlap: $overlap")
            inputInfoMap = session.inputInfo
            env = OrtEnvironment.getEnvironment()
            var foundInputName: String? = null
            var foundIsGrayscale = false
            var foundIsFp16 = false
            for ((key, nodeInfo) in inputInfoMap) {
                val tensorInfo = nodeInfo.info as? TensorInfo ?: continue
                val shape = tensorInfo.shape
                if ((tensorInfo.type == OnnxJavaType.FLOAT || tensorInfo.type == OnnxJavaType.FLOAT16) && shape.size == 4) {
                    foundInputName = key
                    foundIsGrayscale = (shape[1] == 1L || shape[1] == -1L)
                    foundIsFp16 = (tensorInfo.type == OnnxJavaType.FLOAT16)
                    break
                }
            }
            inputName = foundInputName ?: throw RuntimeException("Could not find valid input tensor")
            isGrayscale = foundIsGrayscale
            isFp16 = foundIsFp16
            android.util.Log.d("ModelInfo", "Model input type: ${if (isFp16) "FP16" else "FP32"}, grayscale: $isGrayscale")
        }
    }
}