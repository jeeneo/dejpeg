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
*
*/

/*
* If you use this code in your own project, please give credit
*/

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
    var chunkSize: Int? = null
    var overlapSize: Int? = null

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
            val modelInfo = ModelInfo(modelName, strength, session, chunkSize, overlapSize)
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
                    callback.onError("${e.javaClass.simpleName}${if (e.message != null) ": ${e.message}" else ""}")
                }
            }
        }
    }

    private fun addBlackBorder(bitmap: Bitmap, borderSize: Int): Bitmap {
        val newWidth = bitmap.width + 2 * borderSize
        val newHeight = bitmap.height + 2 * borderSize
        val borderedBitmap = createBitmap(newWidth, newHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(borderedBitmap)
        canvas.drawColor(Color.BLACK)
        canvas.drawBitmap(bitmap, borderSize.toFloat(), borderSize.toFloat(), null)
        return borderedBitmap
    }

    private fun removeBlackBorder(bitmap: Bitmap, borderSize: Int): Bitmap {
        val croppedWidth = bitmap.width - 2 * borderSize
        val croppedHeight = bitmap.height - 2 * borderSize
        if (croppedWidth <= 0 || croppedHeight <= 0) {
            return bitmap
        }
        return Bitmap.createBitmap(bitmap, borderSize, borderSize, croppedWidth, croppedHeight)
    }

    private suspend fun processBitmap(
        session: OrtSession,
        inputBitmap: Bitmap,
        callback: ProcessCallback,
        info: ModelInfo,
        index: Int,
        total: Int
    ): Bitmap {
        val borderSize = 8 // to handle edge artifacts, dunno if it's needed for *all* models, but it helped with SCUNet
        val borderedBitmap = addBlackBorder(inputBitmap, borderSize)
        val width = borderedBitmap.getWidth()
        val height = borderedBitmap.getHeight()
        val hasTransparency = borderedBitmap.hasAlpha()
        val processingConfig = Bitmap.Config.ARGB_8888
        val effectiveMaxChunkSize = if (info.expectedWidth != null && info.expectedHeight != null) {
            minOf(info.chunkSize, info.expectedWidth, info.expectedHeight)
        } else {
            info.chunkSize
        }
        val mustTile = width > effectiveMaxChunkSize + (2 * borderSize) || height > effectiveMaxChunkSize + (2 * borderSize)
        val processedBitmap = if (mustTile) processTiled(session, borderedBitmap, callback, info, processingConfig, hasTransparency, index, total, effectiveMaxChunkSize)
        else
        {
            val bitmapToProcess = if (borderedBitmap.config != processingConfig) borderedBitmap.copy(processingConfig, true)
            else borderedBitmap
            val progressMessage = { context.getString(R.string.processing) }
            withContext(Dispatchers.Main) {
                callback.onProgress(progressMessage())
            }
            val result = processChunk(session, bitmapToProcess, processingConfig, hasTransparency, info)
            result
        }
        borderedBitmap.recycle()
        val finalResult = removeBlackBorder(processedBitmap, borderSize)
        if (processedBitmap != finalResult) {
            processedBitmap.recycle()
        }
        return finalResult
    }

    private suspend fun processTiled(
        session: OrtSession,
        inputBitmap: Bitmap,
        callback: ProcessCallback,
        info: ModelInfo,
        config: Bitmap.Config,
        hasTransparency: Boolean,
        index: Int,
        total: Int,
        maxChunkSize: Int
    ): Bitmap {
        val width = inputBitmap.width
        val height = inputBitmap.height
        val overlap = info.overlap
        val stride = maxChunkSize - overlap
        val cols = if (width <= maxChunkSize) 1 else ceil((width - overlap).toFloat() / stride).toInt()
        val rows = if (height <= maxChunkSize) 1 else ceil((height - overlap).toFloat() / stride).toInt()
        Log.d("ImageProcessor", "Processing tiled: image=${width}x${height}, chunkSize=$maxChunkSize, stride=$stride, grid=${cols}x${rows}, overlap=$overlap")
        val totalChunks = cols * rows
        val chunksDir = CacheManager.getChunksDir(context)
        val chunkInfoList = mutableListOf<ChunkInfo>()
        try {
            Log.d("ImageProcessor", "Phase 1: Extracting $totalChunks chunks to disk")
            var chunkIndex = 0
            for (row in 0 until rows) {
                for (col in 0 until cols) {
                    if (isCancelled) throw Exception(context.getString(R.string.error_processing_cancelled))
                    val chunkX = col * stride
                    val chunkY = row * stride
                    val chunkW = minOf(chunkX + maxChunkSize, width) - chunkX
                    val chunkH = minOf(chunkY + maxChunkSize, height) - chunkY
                    if (chunkW <= 0 || chunkH <= 0) continue
                    val chunk = Bitmap.createBitmap(inputBitmap, chunkX, chunkY, chunkW, chunkH)
                    val converted = if (chunk.config != config) {
                        val temp = chunk.copy(config, true)
                        chunk.recycle()
                        temp
                    } else {
                        chunk
                    }
                    val inputChunkFile = File(chunksDir, "chunk_${chunkIndex}.png")
                    val processedChunkFile = File(chunksDir, "chunk_${chunkIndex}_processed.png")
                    withContext(Dispatchers.IO) {
                        FileOutputStream(inputChunkFile).use { 
                            converted.compress(Bitmap.CompressFormat.PNG, 100, it) 
                        }
                    }
                    converted.recycle()
                    chunkInfoList.add(ChunkInfo(
                        index = chunkIndex,
                        inputFile = inputChunkFile,
                        processedFile = processedChunkFile,
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
                    BitmapFactory.decodeFile(chunkInfo.inputFile.absolutePath)
                } ?: throw Exception("Failed to load chunk ${chunkInfo.index}")
                val processed = processChunk(session, loadedChunk, config, hasTransparency, info)
                loadedChunk.recycle()
                withContext(Dispatchers.IO) {
                    FileOutputStream(chunkInfo.processedFile).use {
                        processed.compress(Bitmap.CompressFormat.PNG, 100, it)
                    }
                    chunkInfo.inputFile.delete()
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
            for (chunkInfo in chunkInfoList) {
                if (isCancelled) throw Exception(context.getString(R.string.error_processing_cancelled))
                val loadedProcessed = withContext(Dispatchers.IO) {
                    BitmapFactory.decodeFile(chunkInfo.processedFile.absolutePath)
                } ?: throw Exception("Failed to load processed chunk ${chunkInfo.index}")
                mergeChunkWithBlending(result, loadedProcessed, chunkInfo, cols, rows, overlap)
                loadedProcessed.recycle()
            }
            CacheManager.clearChunks(context)
            return result
        } catch (e: Exception) {
            throw e
        }
    }

    private fun processChunk(
        session: OrtSession,
        chunk: Bitmap,
        config: Bitmap.Config,
        hasAlpha: Boolean,
        info: ModelInfo
    ): Bitmap {
        val originalW = chunk.width
        val originalH = chunk.height
        val minImgSize = modelManager.getMinSpatialSize(info.modelName)
        val w = if (info.expectedWidth != null && info.expectedWidth > 0) {
            info.expectedWidth
        } else {
            val padFactor = 8
            val paddedW = ((originalW + padFactor - 1) / padFactor) * padFactor
            maxOf(paddedW, minImgSize)
        }
        val h = if (info.expectedHeight != null && info.expectedHeight > 0) {
            info.expectedHeight
        } else {
            val padFactor = 8
            val paddedH = ((originalH + padFactor - 1) / padFactor) * padFactor
            maxOf(paddedH, minImgSize)
        }
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
        val inputChannels = info.inputChannels
        val outputChannels = info.outputChannels
        val pixels = IntArray(w * h)
        paddedChunk.getPixels(pixels, 0, w, 0, 0, w, h)
        val inputArray = FloatArray(inputChannels * w * h)
        val alphaChannel = if (hasAlpha) FloatArray(w * h) else null
        for (i in 0 until w * h) {
            val color = pixels[i]
            if (inputChannels == 1) {
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
        val inputShape = longArrayOf(1, inputChannels.toLong(), h.toLong(), w.toLong())
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
                val (outputArray, actualOutputChannels) = extractOutputArray(sessionResult[0].value, outputChannels, h, w)
                val fullResultBitmap = createBitmap(w, h, config)
                val outPixels = IntArray(w * h)
                for (i in 0 until w * h) {
                    val alpha = if (hasAlpha) clamp255(alphaChannel!![i] * 255f) else 255
                    
                    if (actualOutputChannels == 1) {
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

    private fun clamp255(v: Float): Int {
        return 0.coerceAtLeast(255.coerceAtMost(v.toInt()))
    }

    @Suppress("UNCHECKED_CAST")
    private fun extractOutputArray(outputValue: Any, channels: Int, h: Int, w: Int): Pair<FloatArray, Int> {
        Log.d("ImageProcessor", "Output type received: ${outputValue.javaClass.name}")
        return when (outputValue) {
            is FloatArray -> {
                Log.d("ImageProcessor", "Output is FloatArray (FP32 or auto-converted from FP16)")
                outputValue to channels
            }
            is ShortArray -> {
                Log.d("ImageProcessor", "Output is ShortArray (FP16) - converting to Float32")
                FloatArray(outputValue.size) { i -> float16ToFloat(outputValue[i]) } to channels
            }
            is Array<*> -> {
                try {
                    val arr = outputValue as Array<Array<Array<FloatArray>>>
                    val actualChannels = arr[0].size
                    Log.d("ImageProcessor", "Expected channels: $channels, Actual channels: $actualChannels")
                    val out = FloatArray(channels * h * w)
                    val channelsToProcess = minOf(channels, actualChannels)
                    for (ch in 0 until channelsToProcess) {
                        for (y in 0 until h) {
                            for (x in 0 until w) {
                                out[ch * h * w + y * w + x] = arr[0][ch][y][x]
                            }
                        }
                    }
                    out to actualChannels
                } catch (e: Exception) {
                    try {
                        val arr = outputValue as Array<Array<Array<ShortArray>>>
                        val actualChannels = arr[0].size
                        Log.d("ImageProcessor", "Expected channels: $channels, Actual channels: $actualChannels")
                        val out = FloatArray(channels * h * w)
                        val channelsToProcess = minOf(channels, actualChannels)
                        for (ch in 0 until channelsToProcess) {
                            for (y in 0 until h) {
                                for (x in 0 until w) {
                                    out[ch * h * w + y * w + x] = float16ToFloat(arr[0][ch][y][x])
                                }
                            }
                        }
                        out to actualChannels
                    } catch (e2: Exception) {
                        throw RuntimeException("Failed to extract output array: ${e.message}, ${e2.message}")
                    }
                }
            }
            else -> throw RuntimeException("Unexpected ONNX output type: ${outputValue.javaClass}")
        }
    }

    private fun mergeChunkWithBlending(
        result: Bitmap,
        processedChunk: Bitmap,
        chunkInfo: ChunkInfo,
        cols: Int,
        rows: Int,
        overlap: Int
    ) {
        val width = processedChunk.width
        val height = processedChunk.height
        val x = chunkInfo.x
        val y = chunkInfo.y
        val needsLeftBlend = chunkInfo.col > 0
        val needsTopBlend = chunkInfo.row > 0
        if (!needsLeftBlend && !needsTopBlend) {
            val canvas = Canvas(result)
            canvas.drawBitmap(processedChunk, x.toFloat(), y.toFloat(), null)
            return
        }
        val existingPixels = IntArray(width * height)
        try {
            result.getPixels(existingPixels, 0, width, x, y, width, height)
        } catch (e: Exception) {
            val canvas = Canvas(result)
            canvas.drawBitmap(processedChunk, x.toFloat(), y.toFloat(), null)
            return
        }
        val newPixels = IntArray(width * height)
        processedChunk.getPixels(newPixels, 0, width, 0, 0, width, height)
        for (localY in 0 until height) {
            for (localX in 0 until width) {
                val inLeftOverlap = needsLeftBlend && localX < overlap
                val inTopOverlap = needsTopBlend && localY < overlap
                if (!inLeftOverlap && !inTopOverlap) continue
                val idx = localY * width + localX
                var blendFactor = 1.0f
                if (inLeftOverlap) {
                    val t = (localX.toFloat() / (overlap - 1).coerceAtLeast(1)).coerceIn(0f, 1f)
                    blendFactor = minOf(blendFactor, t * t * (3f - 2f * t))
                }
                if (inTopOverlap) {
                    val t = (localY.toFloat() / (overlap - 1).coerceAtLeast(1)).coerceIn(0f, 1f)
                    blendFactor = minOf(blendFactor, t * t * (3f - 2f * t))
                }
                val existingColor = existingPixels[idx]
                val newColor = newPixels[idx]
                val r = ((1 - blendFactor) * Color.red(existingColor) + blendFactor * Color.red(newColor)).toInt()
                val g = ((1 - blendFactor) * Color.green(existingColor) + blendFactor * Color.green(newColor)).toInt()
                val b = ((1 - blendFactor) * Color.blue(existingColor) + blendFactor * Color.blue(newColor)).toInt()
                val a = ((1 - blendFactor) * Color.alpha(existingColor) + blendFactor * Color.alpha(newColor)).toInt()
                newPixels[idx] = Color.argb(a, r, g, b)
            }
        }
        result.setPixels(newPixels, 0, width, x, y, width, height)
    }

    private data class ChunkInfo(
        val index: Int,
        val inputFile: File,
        val processedFile: File,
        val x: Int,
        val y: Int,
        val width: Int,
        val height: Int,
        val col: Int,
        val row: Int
    )

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
        chunkSize: Int?,
        overlapSize: Int?
    ) {
        val modelName: String? = modelName
        val env: OrtEnvironment?
        val inputName: String
        val inputInfoMap: Map<String, NodeInfo>
        val inputChannels: Int
        val outputChannels: Int
        val isFp16: Boolean
        val chunkSize: Int = chunkSize ?: AppPreferences.DEFAULT_CHUNK_SIZE
        val overlap: Int = overlapSize ?: AppPreferences.DEFAULT_OVERLAP_SIZE
        val expectedWidth: Int?
        val expectedHeight: Int?
        init {
            Log.d("ModelInfo", "Initialized with chunkSize: $chunkSize, overlapSize: $overlapSize -> chunkSize: $chunkSize, overlap: $overlap")
            inputInfoMap = session.inputInfo
            env = OrtEnvironment.getEnvironment()
            var foundInputName: String? = null
            var foundInputChannels = 3
            var foundOutputChannels = 3
            var foundIsFp16 = false
            var foundExpectedWidth: Int? = null
            var foundExpectedHeight: Int? = null
            for ((key, nodeInfo) in inputInfoMap) {
                val tensorInfo = nodeInfo.info as? TensorInfo ?: continue
                val shape = tensorInfo.shape
                if ((tensorInfo.type == OnnxJavaType.FLOAT || tensorInfo.type == OnnxJavaType.FLOAT16) && shape.size == 4) {
                    foundInputName = key
                    foundInputChannels = if (shape[1] == 1L) 1 else 3
                    foundIsFp16 = (tensorInfo.type == OnnxJavaType.FLOAT16)
                    if (shape[2] > 0) foundExpectedHeight = shape[2].toInt()
                    if (shape[3] > 0) foundExpectedWidth = shape[3].toInt()
                    break
                }
            }
            val outputInfoMap = session.outputInfo
            for ((_, nodeInfo) in outputInfoMap) {
                val tensorInfo = nodeInfo.info as? TensorInfo ?: continue
                val shape = tensorInfo.shape
                if ((tensorInfo.type == OnnxJavaType.FLOAT || tensorInfo.type == OnnxJavaType.FLOAT16) && shape.size == 4) {
                    foundOutputChannels = if (shape[1] == 1L) 1 else 3
                    break
                }
            }
            inputName = foundInputName ?: throw RuntimeException("Could not find valid input tensor")
            inputChannels = foundInputChannels
            outputChannels = foundOutputChannels
            isFp16 = foundIsFp16
            expectedWidth = foundExpectedWidth
            expectedHeight = foundExpectedHeight
            Log.d("ModelInfo", "Model input type: ${if (isFp16) "FP16" else "FP32"}, input channels: $inputChannels, output channels: $outputChannels, expected dimensions: ${expectedWidth ?: "dynamic"}x${expectedHeight ?: "dynamic"}")
        }
    }
}
