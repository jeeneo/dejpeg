/*
 * SPDX-FileCopyrightText: 2025 - 2026 dryerlint <https://codeberg.org/dryerlint>
 * SPDX-License-Identifier: GNU Affero General Public License v3.0 or later
 */

@file:Suppress("GrazieInspection", "SpellCheckingInspection")

package com.je.dejpeg.processing

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.util.Log
import org.tensorflow.lite.Interpreter
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale
import com.je.dejpeg.AppPreferences
import com.je.dejpeg.R
import com.je.dejpeg.ThreadUtils
import com.je.dejpeg.utils.CacheManager
import com.je.dejpeg.utils.ModelManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.ceil

class ImageProcessor(
    private val context: Context, private val modelManager: ModelManager
) {

    private companion object {
        const val MODEL_PAD_FACTOR = 8
    }

    var chunkSize: Int? = null
    var overlapSize: Int? = null
    var deviceThreadCount: Int? = null

    @Volatile
    private var isCancelled = false

    interface ProcessCallback {
        fun onComplete(result: Bitmap)
        fun onError(error: String)
        fun onProgress(message: String)
        fun onChunkProgress(currentChunkIndex: Int, totalChunks: Int, parallelWorkers: Int)
    }

    fun cancelProcessing() {
        isCancelled = true
    }

    suspend fun processImage(
        inputBitmap: Bitmap, strength: Float, callback: ProcessCallback
    ) = withContext(Dispatchers.Default) {
        isCancelled = false
        try {
            val coresToUse = ThreadUtils.resolveThreadCount(deviceThreadCount)
            val modelName = modelManager.getActiveModelName()
            val interpreter = modelManager.loadModel()
            val minOverlap = modelManager.getMinOverlapSize(modelName)
            val baseOverlap = overlapSize ?: AppPreferences.DEFAULT_OVERLAP_SIZE
            val effectiveOverlap = maxOf(baseOverlap, minOverlap)
            if (effectiveOverlap > baseOverlap) {
                Log.d(
                    "ImageProcessor",
                    "Enforcing minimum overlap for $modelName: $baseOverlap -> $effectiveOverlap"
                )
            }
            val fixedSize = modelManager.getFixedInputSize(modelName)
            val modelInfo =
                ModelInfo(modelName, strength, interpreter, chunkSize, effectiveOverlap, fixedSize)
            val result = processBitmap(
                interpreter = interpreter,
                inputBitmap = inputBitmap,
                callback = callback,
                info = modelInfo,
                coresToUse = coresToUse
            )
            withContext(Dispatchers.Main) {
                callback.onComplete(result)
            }
        } catch (e: Exception) {
            val errorMessage = if (isCancelled) {
                context.getString(R.string.error_processing_cancelled)
            } else {
                val message = e.message
                if (message.isNullOrBlank()) {
                    e.javaClass.simpleName
                } else {
                    "${e.javaClass.simpleName}: $message"
                }
            }
            withContext(Dispatchers.Main) {
                callback.onError(errorMessage)
            }
        }
    }

    private suspend fun processBitmap(
        interpreter: Interpreter,
        inputBitmap: Bitmap,
        callback: ProcessCallback,
        info: ModelInfo,
        coresToUse: Int
    ): Bitmap {
        val mustTile: Boolean
        val effectiveMaxChunkSize: Int

        val width = inputBitmap.width
        val height = inputBitmap.height
        val hasTransparency = inputBitmap.hasAlpha()
        val processingConfig = Bitmap.Config.ARGB_8888

        if (info.skipTiling) {
            val bitmapToProcess =
                if (inputBitmap.config != processingConfig) inputBitmap.copy(processingConfig, true)
                else inputBitmap
            withContext(Dispatchers.Main) {
                callback.onProgress(context.getString(R.string.processing))
            }
            return processChunk(interpreter, bitmapToProcess, processingConfig, hasTransparency, info)
        }

        if (info.expectedWidth != null && info.expectedHeight != null) {
            val expectedW = info.expectedWidth!!
            val expectedH = info.expectedHeight!!
            effectiveMaxChunkSize = minOf(expectedW, expectedH)
            mustTile = width > expectedW || height > expectedH
        } else {
            effectiveMaxChunkSize = info.chunkSize
            mustTile = width > effectiveMaxChunkSize || height > effectiveMaxChunkSize
        }

        val processedBitmap = if (mustTile) processTiled(
            interpreter,
            inputBitmap,
            callback,
            info,
            processingConfig,
            hasTransparency,
            effectiveMaxChunkSize,
            coresToUse
        )
        else {
            val bitmapToProcess =
                if (inputBitmap.config != processingConfig) inputBitmap.copy(processingConfig, true)
                else inputBitmap
            val progressMessage = { context.getString(R.string.processing) }
            withContext(Dispatchers.Main) {
                callback.onProgress(progressMessage())
            }
            val result =
                processChunk(interpreter, bitmapToProcess, processingConfig, hasTransparency, info)
            result
        }
        return processedBitmap
    }

    private fun computeEvenTileBoundaries(
        totalSize: Int, maxTileSize: Int
    ): List<Pair<Int, Int>> {
        if (totalSize <= maxTileSize) return listOf(0 to totalSize)

        val count = ceil(totalSize.toFloat() / maxTileSize).toInt().coerceAtLeast(1)
        val baseSize = totalSize / count
        val remainder = totalSize % count

        val result = mutableListOf<Pair<Int, Int>>()
        var offset = 0
        for (i in 0 until count) {
            val size = if (i < count - remainder) baseSize else baseSize + 1
            result.add(offset to size)
            offset += size
        }
        return result
    }

    private suspend fun processTiled(
        interpreter: Interpreter,
        inputBitmap: Bitmap,
        callback: ProcessCallback,
        info: ModelInfo,
        config: Bitmap.Config,
        hasTransparency: Boolean,
        maxChunkSize: Int,
        coresToUse: Int
    ): Bitmap {
        val width = inputBitmap.width
        val height = inputBitmap.height
        val overlap = info.overlap
        val maxTileSize = (maxChunkSize - 2 * overlap).coerceAtLeast(overlap)
        val colBounds = computeEvenTileBoundaries(width, maxTileSize)
        val rowBounds = computeEvenTileBoundaries(height, maxTileSize)
        val cols = colBounds.size
        val rows = rowBounds.size
        Log.d(
            "ImageProcessor",
            "Processing tiled: image=${width}x${height}, maxChunkSize=$maxChunkSize, maxTileSize=$maxTileSize, grid=${cols}x${rows}, overlap=$overlap"
        )
        Log.d("ImageProcessor", "  Column bounds: $colBounds")
        Log.d("ImageProcessor", "  Row bounds: $rowBounds")
        val totalChunks = cols * rows
        val chunksDir = CacheManager.getChunksDir(context)
        val chunkInfoList = mutableListOf<ChunkInfo>()
        try {
            Log.d("ImageProcessor", "Phase 1: Extracting $totalChunks chunks to disk")
            var chunkIndex = 0
            for (row in 0 until rows) {
                for (col in 0 until cols) {
                    if (isCancelled) throw Exception(context.getString(R.string.error_processing_cancelled))
                    val (tileX, tileW) = colBounds[col]
                    val (tileY, tileH) = rowBounds[row]
                    if (tileW <= 0 || tileH <= 0) continue
                    val expandLeft = if (col > 0) overlap else 0
                    val expandRight = if (col < cols - 1) overlap else 0
                    val expandTop = if (row > 0) overlap else 0
                    val expandBottom = if (row < rows - 1) overlap else 0
                    val extractX = tileX - expandLeft
                    val extractY = tileY - expandTop
                    val extractW = tileW + expandLeft + expandRight
                    val extractH = tileH + expandTop + expandBottom
                    val chunk =
                        Bitmap.createBitmap(inputBitmap, extractX, extractY, extractW, extractH)
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
                    chunkInfoList.add(
                        ChunkInfo(
                            index = chunkIndex,
                            inputFile = inputChunkFile,
                            processedFile = processedChunkFile,
                            x = tileX,
                            y = tileY,
                            width = tileW,
                            height = tileH,
                            col = col,
                            row = row,
                            expandLeft = expandLeft,
                            expandTop = expandTop
                        )
                    )
                    chunkIndex++
                }
            }
            Log.d(
                "ImageProcessor", "Saved ${chunkInfoList.size} chunks to ${chunksDir.absolutePath}"
            )
            Log.d("ImageProcessor", "Phase 2: Processing $totalChunks chunks")
            val parallelWorkers = coresToUse.coerceIn(1, totalChunks.coerceAtLeast(1))
            Log.d(
                "ImageProcessor",
                "Using $parallelWorkers worker(s) for chunk processing (deviceThreads=$coresToUse)"
            )
            if (totalChunks > 1) {
                withContext(Dispatchers.Main) {
                    callback.onChunkProgress(0, totalChunks, parallelWorkers)
                }
            }
            val completedChunks = AtomicInteger(0)

            suspend fun processSingleChunk(chunkInfo: ChunkInfo) {
                if (isCancelled) throw Exception(context.getString(R.string.error_processing_cancelled))
                val loadedChunk = withContext(Dispatchers.IO) {
                    BitmapFactory.decodeFile(chunkInfo.inputFile.absolutePath)
                } ?: throw Exception("Failed to load chunk ${chunkInfo.index}")
                val processed = processChunk(interpreter, loadedChunk, config, hasTransparency, info)
                loadedChunk.recycle()
                val cropped =
                    if (chunkInfo.expandLeft > 0 || chunkInfo.expandTop > 0 || processed.width > chunkInfo.width || processed.height > chunkInfo.height) {
                        val c = Bitmap.createBitmap(
                            processed,
                            chunkInfo.expandLeft,
                            chunkInfo.expandTop,
                            chunkInfo.width,
                            chunkInfo.height
                        )
                        processed.recycle()
                        c
                    } else {
                        processed
                    }
                withContext(Dispatchers.IO) {
                    FileOutputStream(chunkInfo.processedFile).use {
                        cropped.compress(Bitmap.CompressFormat.PNG, 100, it)
                    }
                    chunkInfo.inputFile.delete()
                }
                cropped.recycle()
                val completed = completedChunks.incrementAndGet()
                withContext(Dispatchers.Main) {
                    if (totalChunks > 1) {
                        callback.onChunkProgress(completed, totalChunks, parallelWorkers)
                    } else {
                        callback.onProgress(context.getString(R.string.processing))
                    }
                }
            }

            if (parallelWorkers <= 1) {
                for (chunkInfo in chunkInfoList) {
                    processSingleChunk(chunkInfo)
                }
            } else {
                val semaphore = Semaphore(parallelWorkers)
                coroutineScope {
                    for (chunkInfo in chunkInfoList) {
                        launch {
                            semaphore.withPermit {
                                processSingleChunk(chunkInfo)
                            }
                        }
                    }
                }
            }
            Log.d("ImageProcessor", "Phase 3: Merging processed chunks")
            withContext(Dispatchers.Main) {
                callback.onProgress(context.getString(R.string.finishing_up))
            }
            val result = createBitmap(width, height, config)
            val resultCanvas = Canvas(result)
            for (chunkInfo in chunkInfoList) {
                if (isCancelled) throw Exception(context.getString(R.string.error_processing_cancelled))
                val loadedProcessed = withContext(Dispatchers.IO) {
                    BitmapFactory.decodeFile(chunkInfo.processedFile.absolutePath)
                } ?: throw Exception("Failed to load processed chunk ${chunkInfo.index}")
                resultCanvas.drawBitmap(
                    loadedProcessed, chunkInfo.x.toFloat(), chunkInfo.y.toFloat(), null
                )
                loadedProcessed.recycle()
            }
            CacheManager.clearChunks(context)
            return result
        } catch (e: Exception) {
            throw e
        }
    }

    private fun processChunk(
        interpreter: Interpreter,
        chunk: Bitmap,
        config: Bitmap.Config,
        hasAlpha: Boolean,
        info: ModelInfo
    ): Bitmap {
        val originalW = chunk.width
        val originalH = chunk.height
        val modelW = info.expectedWidth ?: throw RuntimeException("Model width not set")
        val modelH = info.expectedHeight ?: throw RuntimeException("Model height not set")

        // Scale to model input size if needed
        val scaled = if (chunk.width != modelW || chunk.height != modelH) {
            chunk.scale(modelW, modelH)
        } else chunk

        val pixels = IntArray(modelW * modelH)
        scaled.getPixels(pixels, 0, modelW, 0, 0, modelW, modelH)

        // Convert pixels to input array (NCHW or NHWC)
        val inputArray = FloatArray(info.inputChannels * modelW * modelH)
        for (i in 0 until modelW * modelH) {
            val c = pixels[i]
            if (info.isNCHW) {
                inputArray[i] = Color.red(c) / 255f
                inputArray[modelW * modelH + i] = Color.green(c) / 255f
                if (info.inputChannels == 3) {
                    inputArray[2 * modelW * modelH + i] = Color.blue(c) / 255f
                }
            } else {
                // NHWC: interleaved
                inputArray[i * info.inputChannels] = Color.red(c) / 255f
                inputArray[i * info.inputChannels + 1] = Color.green(c) / 255f
                if (info.inputChannels == 3) {
                    inputArray[i * info.inputChannels + 2] = Color.blue(c) / 255f
                }
            }
        }

        val inputBuffer = ByteBuffer.allocateDirect(4 * inputArray.size).order(ByteOrder.nativeOrder())
        inputArray.forEach { inputBuffer.putFloat(it) }
        inputBuffer.rewind()

        // Find image input and output tensor indices
        var imageInputIndex = 0
        for (i in 0 until interpreter.inputTensorCount) {
            val shape = interpreter.getInputTensor(i).shape()
            if (shape.size == 4) {
                imageInputIndex = i
                break
            }
        }

        var imageOutputIndex = 0
        for (i in 0 until interpreter.outputTensorCount) {
            val shape = interpreter.getOutputTensor(i).shape()
            if (shape.size == 4) {
                imageOutputIndex = i
                break
            }
        }

        // Prepare input arrays for runForMultipleInputsOutputs
        val inputs = arrayOfNulls<Any>(interpreter.inputTensorCount)
        for (i in 0 until interpreter.inputTensorCount) {
            val shape = interpreter.getInputTensor(i).shape()
            inputs[i] = when {
                i == imageInputIndex -> inputBuffer
                shape.fold(1L) { a, b -> a * b } == 1L -> {
                    // Strength parameter
                    val strengthBuffer = ByteBuffer.allocateDirect(4).order(ByteOrder.nativeOrder())
                    strengthBuffer.putFloat(info.strength / 100f)
                    strengthBuffer.rewind()
                    strengthBuffer
                }
                else -> {
                    // Unknown input, allocate buffer for its shape
                    val size = shape.fold(1) { a, b -> (a * b).toInt() }
                    ByteBuffer.allocateDirect(4 * size).order(ByteOrder.nativeOrder())
                }
            }
        }

        // Prepare output buffers
        val outputs = mutableMapOf<Int, Any>()
        for (i in 0 until interpreter.outputTensorCount) {
            val outputTensor = interpreter.getOutputTensor(i)
            val size = outputTensor.shape().fold(1) { a, b -> (a * b).toInt() }
            val outputBuffer = ByteBuffer.allocateDirect(4 * size).order(ByteOrder.nativeOrder())
            outputs[i] = outputBuffer
        }

        // Run inference
        interpreter.runForMultipleInputsOutputs(inputs, outputs)

        // Extract output
        val outputBuffer = outputs[imageOutputIndex] as ByteBuffer
        outputBuffer.rewind()
        val outputSize = info.outputChannels * modelW * modelH
        val outputArray = FloatArray(outputSize) { outputBuffer.getFloat() }

        // Convert output back to pixels
        val outPixels = IntArray(modelW * modelH)
        for (i in 0 until modelW * modelH) {
            val (r, g, b) = if (info.isNCHW) {
                Triple(
                    clamp255(outputArray[i] * 255f),
                    clamp255(outputArray[modelW * modelH + i] * 255f),
                    if (info.outputChannels == 3) clamp255(outputArray[2 * modelW * modelH + i] * 255f) else clamp255(outputArray[i] * 255f)
                )
            } else {
                Triple(
                    clamp255(outputArray[i * info.outputChannels] * 255f),
                    clamp255(outputArray[i * info.outputChannels + 1] * 255f),
                    if (info.outputChannels == 3) clamp255(outputArray[i * info.outputChannels + 2] * 255f) else clamp255(outputArray[i * info.outputChannels + 1] * 255f)
                )
            }
            outPixels[i] = Color.argb(255, r, g, b)
        }

        val modelResult = createBitmap(modelW, modelH, Bitmap.Config.ARGB_8888)
        modelResult.setPixels(outPixels, 0, modelW, 0, 0, modelW, modelH)

        if (scaled !== chunk) scaled.recycle()

        // Scale back to original size if needed
        return if (modelW != originalW || modelH != originalH) {
            val resized = modelResult.scale(originalW, originalH)
            modelResult.recycle()
            resized
        } else {
            modelResult
        }
    }

    private fun clamp255(v: Float): Int {
        return 0.coerceAtLeast(255.coerceAtMost(v.toInt()))
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
        val row: Int,
        val expandLeft: Int,
        val expandTop: Int
    )

    private class ModelInfo(
        val modelName: String?,
        val strength: Float,
        interpreter: Interpreter,
        chunkSize: Int?,
        overlapSize: Int?,
        fixedInputSize: Pair<Int, Int>? = null
    ) {
        val chunkSize: Int = chunkSize ?: AppPreferences.DEFAULT_CHUNK_SIZE
        val overlap: Int = overlapSize ?: AppPreferences.DEFAULT_OVERLAP_SIZE
        var expectedWidth: Int? = null
        var expectedHeight: Int? = null
        val skipTiling: Boolean = fixedInputSize != null
        var isNCHW: Boolean = false
        var inputChannels: Int = 3
        var outputChannels: Int = 3

        init {
            Log.d(
                "ModelInfo",
                "Initialized $modelName:: chunkSize: ${this.chunkSize}, overlapSize: ${this.overlap}, fixedInputSize: $fixedInputSize, skipTiling: $skipTiling"
            )
            
            // Detect image input tensor (4D with at least 3 channels)
            var imageInputIndex = 0
            var foundImageInput = false
            for (i in 0 until interpreter.inputTensorCount) {
                val shape = interpreter.getInputTensor(i).shape()
                if (shape.size == 4) {
                    imageInputIndex = i
                    foundImageInput = true
                    break
                }
            }
            
            if (!foundImageInput) {
                throw RuntimeException("Could not find 4D input tensor")
            }
            
            val imageShape = interpreter.getInputTensor(imageInputIndex).shape()
            // Determine layout: NCHW or NHWC
            isNCHW = false || imageShape[1] == 1
            
            val (modelH, modelW, channels) = if (isNCHW) {
                Triple(imageShape[2], imageShape[3], imageShape[1])
            } else {
                Triple(imageShape[1], imageShape[2], imageShape[3])
            }
            
            inputChannels = if (channels == 1) 1 else 3
            
            // Detect output channels from first 4D output tensor
            outputChannels = 3 // default
            for (i in 0 until interpreter.outputTensorCount) {
                val shape = interpreter.getOutputTensor(i).shape()
                if (shape.size == 4) {
                    val outChannels = if (isNCHW) shape[1].toInt() else shape[3].toInt()
                    outputChannels = if (outChannels == 1) 1 else 3
                    break
                }
            }
            
            // Use fixed size or inferred from model shape
            expectedWidth = fixedInputSize?.first ?: modelW
            expectedHeight = fixedInputSize?.second ?: modelH
            
            Log.d(
                "ModelInfo",
                "Model $modelName data:: layout: ${if (isNCHW) "NCHW" else "NHWC"}, input channels: $inputChannels, output channels: $outputChannels, expected dimensions: $expectedWidth x $expectedHeight, fixedInputSize: $fixedInputSize, skipTiling: $skipTiling"
            )
        }
    }
}
