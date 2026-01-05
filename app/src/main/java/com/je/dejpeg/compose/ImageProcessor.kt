package com.je.dejpeg.compose

import ai.onnxruntime.NodeInfo
import ai.onnxruntime.OnnxJavaType
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.TensorInfo
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.util.Log
import androidx.core.graphics.createBitmap
import com.je.dejpeg.R
import com.je.dejpeg.compose.utils.CacheManager
import com.je.dejpeg.data.AppPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.ceil

class ImageProcessor(
    private val context: Context,
    private val modelManager: ModelManager
) {
    var customChunkSize: Int? = null
    var customOverlapSize: Int? = null

    @Volatile
    private var isCancelled = false

    interface ProcessCallback {
        fun onComplete(result: Bitmap)
        fun onError(error: String)
        fun onProgress(message: String)
        fun onChunkProgress(currentChunkIndex: Int, totalChunks: Int)
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
            val modelInfo = ModelInfo(modelName, strength, session, customChunkSize, customOverlapSize)
            val result = processBitmap(session, inputBitmap, callback, modelInfo, index, total)
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

    private suspend fun processBitmap(
        session: OrtSession,
        inputBitmap: Bitmap,
        callback: ProcessCallback,
        info: ModelInfo,
        index: Int,
        total: Int
    ): Bitmap {
        val width = inputBitmap.getWidth()
        val height = inputBitmap.getHeight()
        val hasTransparency = detectTransparency(inputBitmap)

        val processingConfig = Bitmap.Config.ARGB_8888
        val mustTile = width > info.chunkSize || height > info.chunkSize
        return if (mustTile) processTiled(session, inputBitmap, callback, info, processingConfig, hasTransparency, index, total)
        else
        {
            val bitmapToProcess = if (inputBitmap.config != processingConfig) inputBitmap.copy(processingConfig, true)
            else inputBitmap
            val progressMessage = { context.getString(R.string.processing) }
            withContext(Dispatchers.Main) {
                callback.onProgress(progressMessage())
            }
            val result = processChunkUnified(session, bitmapToProcess, processingConfig, hasTransparency, info)
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
        index: Int,
        total: Int
    ): Bitmap {
        val width = inputBitmap.width
        val height = inputBitmap.height
        val maxChunkSize = info.chunkSize
        val overlap = info.overlap
        val cols = 1.coerceAtLeast(ceil(width.toDouble() / maxChunkSize).toInt())
        val rows = 1.coerceAtLeast(ceil(height.toDouble() / maxChunkSize).toInt())
        val actualChunkWidth = (width + (cols - 1) * overlap) / cols
        val actualChunkHeight = (height + (rows - 1) * overlap) / rows
        Log.d("ImageProcessor", "Processing tiled: image=${width}x${height}, max=$maxChunkSize, actual=${actualChunkWidth}x${actualChunkHeight}, grid=${cols}x${rows}, overlap=$overlap")
        val totalChunks = cols * rows
        val chunksDir = CacheManager.getChunksDir(context)
        data class ChunkInfo(
            val index: Int,
            val file: File,
            val x: Int,
            val y: Int,
            val width: Int,
            val height: Int,
            val col: Int,
            val row: Int
        )
        
        val chunkInfoList = mutableListOf<ChunkInfo>()
        
        try {
            Log.d("ImageProcessor", "Phase 1: Extracting $totalChunks chunks to disk")
            var chunkIndex = 0
            for (row in 0 until rows) {
                for (col in 0 until cols) {
                    if (isCancelled) throw Exception(context.getString(R.string.error_processing_cancelled))
                    val chunkX = 0.coerceAtLeast(col * (actualChunkWidth - overlap))
                    val chunkY = 0.coerceAtLeast(row * (actualChunkHeight - overlap))
                    val chunkW = actualChunkWidth.coerceAtMost(width - chunkX)
                    val chunkH = actualChunkHeight.coerceAtMost(height - chunkY)
                    if (chunkW <= 0 || chunkH <= 0) continue
                    val chunk = Bitmap.createBitmap(inputBitmap, chunkX, chunkY, chunkW, chunkH)
                    val converted = if (chunk.config != config) {
                        val temp = chunk.copy(config, true)
                        chunk.recycle()
                        temp
                    } else {
                        chunk
                    }
                    val chunkFile = File(chunksDir, "chunk_${chunkIndex}.png")
                    withContext(Dispatchers.IO) {
                        FileOutputStream(chunkFile).use { 
                            converted.compress(Bitmap.CompressFormat.PNG, 100, it) 
                        }
                    }
                    converted.recycle()
                    chunkInfoList.add(ChunkInfo(
                        index = chunkIndex,
                        file = chunkFile,
                        x = chunkX,
                        y = chunkY,
                        width = chunkW,
                        height = chunkH,
                        col = col,
                        row = row
                    ))
                    chunkIndex++
                }
            }
            Log.d("ImageProcessor", "Saved ${chunkInfoList.size} chunks to ${chunksDir.absolutePath}")
            Log.d("ImageProcessor", "Phase 2: Processing $totalChunks chunks")
            if (totalChunks > 1) {
                withContext(Dispatchers.Main) {
                    callback.onChunkProgress(0, totalChunks)
                }
            }
            for (chunkInfo in chunkInfoList) {
                if (isCancelled) throw Exception(context.getString(R.string.error_processing_cancelled))
                val progressMessage = if (totalChunks > 1) {
                    context.getString(R.string.processing_chunk_x_of_y, chunkInfo.index + 1, totalChunks)
                } else {
                    context.getString(R.string.processing)
                }
                withContext(Dispatchers.Main) {
                    callback.onProgress(progressMessage)
                }
                val loadedChunk = withContext(Dispatchers.IO) {
                    BitmapFactory.decodeFile(chunkInfo.file.absolutePath)
                } ?: throw Exception("Failed to load chunk ${chunkInfo.index}")
                val processed = processChunkUnified(session, loadedChunk, config, hasTransparency, info)
                loadedChunk.recycle()
                val processedChunkFile = File(chunksDir, "chunk_${chunkInfo.index}_processed.png")
                withContext(Dispatchers.IO) {
                    FileOutputStream(processedChunkFile).use {
                        processed.compress(Bitmap.CompressFormat.PNG, 100, it)
                    }
                    chunkInfo.file.delete()
                }
                processed.recycle()
                if (totalChunks > 1) {
                    val nextChunkIndex = chunkInfo.index + 1
                    withContext(Dispatchers.Main) {
                        callback.onChunkProgress(nextChunkIndex, totalChunks)
                    }
                }
            }
            Log.d("ImageProcessor", "Phase 3: Merging processed chunks")
            withContext(Dispatchers.Main) {
                callback.onProgress(context.getString(R.string.finishing_up))
            }
            val result = createBitmap(width, height, config)
            val canvas = Canvas(result)
            for (chunkInfo in chunkInfoList) {
                if (isCancelled) throw Exception(context.getString(R.string.error_processing_cancelled))
                val processedChunkFile = File(chunksDir, "chunk_${chunkInfo.index}_processed.png")
                val loadedProcessed = withContext(Dispatchers.IO) {
                    BitmapFactory.decodeFile(processedChunkFile.absolutePath)
                } ?: throw Exception("Failed to load processed chunk ${chunkInfo.index}")
                val feathered = createFeatheredChunk(
                    loadedProcessed, chunkInfo.x, chunkInfo.y, width, height, 
                    overlap, cols, rows, chunkInfo.col, chunkInfo.row
                )
                val paint = Paint()
                paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_OVER)
                canvas.drawBitmap(feathered, chunkInfo.x.toFloat(), chunkInfo.y.toFloat(), paint)
                loadedProcessed.recycle()
                feathered.recycle()
                withContext(Dispatchers.IO) {
                    processedChunkFile.delete()
                }
            }
            CacheManager.clearChunks(context)
            return result
        } catch (e: Exception) {
            throw e
        }
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
        val originalW = chunk.width
        val originalH = chunk.height

        val padFactor = 8
        val w = ((originalW + padFactor - 1) / padFactor) * padFactor
        val h = ((originalH + padFactor - 1) / padFactor) * padFactor
        val needsPadding = w != originalW || h != originalH
        
        val paddedChunk = if (needsPadding) {
            Log.d("ImageProcessor", "Padding chunk from ${originalW}x${originalH} to ${w}x${h}")
            val padded = createBitmap(w, h, config)
            val canvas = Canvas(padded)
            canvas.drawBitmap(chunk, 0f, 0f, null)
            if (w > originalW) {
                val rightStrip = Bitmap.createBitmap(chunk, originalW - 1, 0, 1, originalH)
                for (x in originalW until w) {
                    canvas.drawBitmap(rightStrip, x.toFloat(), 0f, null)
                }
                rightStrip.recycle()
            }
            if (h > originalH) {
                val bottomStrip = Bitmap.createBitmap(padded, 0, originalH - 1, w, 1)
                for (y in originalH until h) {
                    canvas.drawBitmap(bottomStrip, 0f, y.toFloat(), null)
                }
                bottomStrip.recycle()
            }
            padded
        } else {
            chunk
        }
        val channels = if (info.isGrayscale) 1 else 3
        val pixels = IntArray(w * h)
        paddedChunk.getPixels(pixels, 0, w, 0, 0, w, h)
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
                val fullResultBitmap = createBitmap(w, h, config)
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
                fullResultBitmap.setPixels(outPixels, 0, w, 0, 0, w, h)
                if (needsPadding) {
                    val cropped = Bitmap.createBitmap(fullResultBitmap, 0, 0, originalW, originalH)
                    fullResultBitmap.recycle()
                    cropped
                } else {
                    fullResultBitmap
                }
            }
        } finally {
            inputs.values.forEach { it.close() }
            if (needsPadding) {
                paddedChunk.recycle()
            }
        }
        return result
    }

    private fun extractOutputArray(outputValue: Any, channels: Int, h: Int, w: Int): FloatArray {
        Log.d("ImageProcessor", "Output type received: ${outputValue.javaClass.name}")
        return when (outputValue) {
            is FloatArray -> {
                Log.d("ImageProcessor", "Output is FloatArray (FP32 or auto-converted from FP16)")
                outputValue
            }
            is ShortArray -> {
                Log.d("ImageProcessor", "Output is ShortArray (FP16) - converting to Float32")
                FloatArray(outputValue.size) { i -> float16ToFloat(outputValue[i]) }
            }
            is Array<*> -> {
                try {
                    @Suppress("UNCHECKED_CAST")
                    val arr = outputValue as Array<Array<Array<FloatArray>>>
                    Log.d("ImageProcessor", "Output is multi-dimensional FloatArray")
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
                        Log.d("ImageProcessor", "Output is multi-dimensional ShortArray (FP16)")
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
        return bitmap.hasAlpha()
    }

    private fun clamp255(v: Float): Int {
        return 0.coerceAtLeast(255.coerceAtMost(v.toInt()))
    }

    private fun formatError(e: Exception): String {
        return "${e.javaClass.simpleName}${if (e.message != null) ": ${e.message}" else ""}"
    }

    private fun floatToFloat16(value: Float): Short {
        val bits = java.lang.Float.floatToIntBits(value)
        val sign = (bits ushr 16) and 0x8000
        val exponent = ((bits ushr 23) and 0xFF) - 127 + 15
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
        val chunkSize: Int = customChunkSize ?: AppPreferences.DEFAULT_CHUNK_SIZE
        val overlap: Int = customOverlapSize ?: AppPreferences.DEFAULT_OVERLAP_SIZE

        init {
            Log.d("ModelInfo", "Initialized with customChunkSize: $customChunkSize, customOverlapSize: $customOverlapSize -> chunkSize: $chunkSize, overlap: $overlap")
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
            Log.d("ModelInfo", "Model input type: ${if (isFp16) "FP16" else "FP32"}, grayscale: $isGrayscale")
        }
    }
}
