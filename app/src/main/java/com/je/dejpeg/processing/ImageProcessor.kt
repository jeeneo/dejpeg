/*
 * SPDX-FileCopyrightText: 2025 - 2026 dryerlint <https://codeberg.org/dryerlint>
 * SPDX-License-Identifier: GNU Affero General Public License v3.0 or later
 */

@file:Suppress("GrazieInspection", "SpellCheckingInspection")

package com.je.dejpeg.processing

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.util.Log
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale
import com.je.dejpeg.AppPreferences
import com.je.dejpeg.R
import com.je.dejpeg.utils.ModelManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder
import android.graphics.Paint

class ImageProcessor(
    private val context: Context, private val modelManager: ModelManager
) {
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
            val interpreter = modelManager.loadModel()
            val imageInputIndex = (0 until interpreter.inputTensorCount).first {
                interpreter.getInputTensor(it).shape().size == 4
            }
            val imageShape = interpreter.getInputTensor(imageInputIndex).shape()
            val isNCHW = imageShape[1] == 3
            val modelH = if (isNCHW) imageShape[2] else imageShape[1]
            val modelW = if (isNCHW) imageShape[3] else imageShape[2]
            Log.d(
                "LiteRtImageProcessor",
                "Model layout: ${if (isNCHW) "NCHW" else "NHWC"}, tile size: ${modelW}x${modelH}"
            )
            val config = Bitmap.Config.ARGB_8888
            val bitmapToProcess =
                if (inputBitmap.config != config) inputBitmap.copy(config, true) else inputBitmap
            val width = bitmapToProcess.width
            val height = bitmapToProcess.height
            val overlap = overlapSize ?: AppPreferences.DEFAULT_OVERLAP_SIZE
    
            val isRmbg = modelManager.getActiveModelName()?.contains("rmbg", ignoreCase = true) == true
            val mustTile = !isRmbg && (width > modelW || height > modelH)
    
            val result = if (!mustTile) {
                withContext(Dispatchers.Main) {
                    callback.onProgress(context.getString(R.string.processing))
                }
                processChunk(
                    interpreter, bitmapToProcess, strength, modelW, modelH, isNCHW, imageInputIndex
                )
            } else {
                processTiled(
                    interpreter,
                    bitmapToProcess,
                    strength,
                    modelW,
                    modelH,
                    isNCHW,
                    imageInputIndex,
                    overlap,
                    callback
                )
            }
            withContext(Dispatchers.Main) { callback.onComplete(result) }
        } catch (e: Exception) {
            val msg = if (isCancelled) context.getString(R.string.error_processing_cancelled)
            else e.message?.takeIf { it.isNotBlank() } ?: e.javaClass.simpleName
            withContext(Dispatchers.Main) { callback.onError(msg) }
        }
    }
    
    private suspend fun processTiled(
        interpreter: Interpreter,
        inputBitmap: Bitmap,
        strength: Float,
        modelW: Int,
        modelH: Int,
        isNCHW: Boolean,
        imageInputIndex: Int,
        overlap: Int,
        callback: ProcessCallback
    ): Bitmap {
        val width = inputBitmap.width
        val height = inputBitmap.height
        val maxTileW = (modelW - 2 * overlap).coerceAtLeast(overlap)
        val maxTileH = (modelH - 2 * overlap).coerceAtLeast(overlap)
        val colBounds = computeEvenTileBoundaries(width, maxTileW)
        val rowBounds = computeEvenTileBoundaries(height, maxTileH)
        val cols = colBounds.size
        val rows = rowBounds.size
        val totalChunks = cols * rows
        Log.d(
            "LiteRtImageProcessor",
            "Tiling: image=${width}x${height}, tileMax=${maxTileW}x${maxTileH}, grid=${cols}x${rows}, overlap=$overlap"
        )
        withContext(Dispatchers.Main) {
            callback.onChunkProgress(0, totalChunks, 1)
        }
        val result = createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        var done = 0
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
                val tileBitmap =
                    Bitmap.createBitmap(inputBitmap, extractX, extractY, extractW, extractH)
                val processed = processChunk(
                    interpreter, tileBitmap, strength, modelW, modelH, isNCHW, imageInputIndex
                )
                tileBitmap.recycle()
                val needsCrop =
                    expandLeft > 0 || expandTop > 0 || processed.width != tileW || processed.height != tileH
                val cropped = if (needsCrop) {
                    Bitmap.createBitmap(processed, expandLeft, expandTop, tileW, tileH)
                } else {
                    processed
                }
                canvas.drawBitmap(cropped, tileX.toFloat(), tileY.toFloat(), null)
                if (cropped !== processed) processed.recycle()
                cropped.recycle()
                done++
                withContext(Dispatchers.Main) {
                    callback.onChunkProgress(done, totalChunks, 1)
                }
            }
        }

        return result
    }

    private fun processChunk(
        interpreter: Interpreter,
        chunk: Bitmap,
        strength: Float,
        modelW: Int,
        modelH: Int,
        isNCHW: Boolean,
        imageInputIndex: Int
    ): Bitmap {
        val originalW = chunk.width
        val originalH = chunk.height
    
        val isRmbg = modelManager.getActiveModelName()?.contains("rmbg", ignoreCase = true) == true
    
        val scaled = if (chunk.width != modelW || chunk.height != modelH) {
            scaleBitmap(chunk, modelW, modelH)  // same helper as ONNX version
        } else chunk
    
        val pixels = IntArray(modelW * modelH)
        scaled.getPixels(pixels, 0, modelW, 0, 0, modelW, modelH)
    
        val inputBuffer =
            ByteBuffer.allocateDirect(4 * 3 * modelW * modelH).order(ByteOrder.nativeOrder())
        if (isNCHW) {
            for (i in 0 until modelW * modelH) inputBuffer.putFloat(Color.red(pixels[i]) / 255f)
            for (i in 0 until modelW * modelH) inputBuffer.putFloat(Color.green(pixels[i]) / 255f)
            for (i in 0 until modelW * modelH) inputBuffer.putFloat(Color.blue(pixels[i]) / 255f)
        } else {
            for (i in 0 until modelW * modelH) {
                val c = pixels[i]
                inputBuffer.putFloat(Color.red(c) / 255f)
                inputBuffer.putFloat(Color.green(c) / 255f)
                inputBuffer.putFloat(Color.blue(c) / 255f)
            }
        }
        inputBuffer.rewind()
    
        val imageOutputIndex = (0 until interpreter.outputTensorCount).first {
            interpreter.getOutputTensor(it).shape().size == 4
        }
        val inputs = arrayOfNulls<Any>(interpreter.inputTensorCount)
        for (i in 0 until interpreter.inputTensorCount) {
            inputs[i] = when {
                i == imageInputIndex -> inputBuffer
                interpreter.getInputTensor(i).shape().fold(1) { a, b -> a * b } == 1 -> {
                    ByteBuffer.allocateDirect(4).order(ByteOrder.nativeOrder()).also {
                        it.putFloat(strength / 100f)
                        it.rewind()
                    }
                }
                else -> throw RuntimeException(
                    "Unhandled input tensor [${i}] shape: ${
                        interpreter.getInputTensor(i).shape().joinToString()
                    }"
                )
            }
        }
    
        val outputs = mutableMapOf<Int, Any>()
        for (i in 0 until interpreter.outputTensorCount) {
            val bytes = interpreter.getOutputTensor(i).numBytes()
            outputs[i] = ByteBuffer.allocateDirect(bytes).order(ByteOrder.nativeOrder())
        }
        interpreter.runForMultipleInputsOutputs(inputs, outputs)
    
        val outputBuffer = (outputs[imageOutputIndex] as ByteBuffer).also { it.rewind() }
        val outputShape = interpreter.getOutputTensor(imageOutputIndex).shape()
        val outputChannels = if (isNCHW) outputShape[1] else outputShape[3]
        val outputSize = outputChannels * modelW * modelH
        val outputArray = FloatArray(outputSize) { outputBuffer.getFloat() }
    
        if (scaled !== chunk) scaled.recycle()
    
        if (isRmbg) {
            // RMBG: single-channel mask output -> normalize, scale to original size, apply as alpha
            var ma = outputArray[0]
            var mi = outputArray[0]
            for (v in outputArray) {
                if (v > ma) ma = v
                if (v < mi) mi = v
            }
            val range = (ma - mi).coerceAtLeast(1e-6f)
    
            val maskPixels = IntArray(modelW * modelH)
            for (i in 0 until modelW * modelH) {
                val a = clamp255(((outputArray[i] - mi) / range) * 255f)
                maskPixels[i] = Color.argb(a, 0, 0, 0)
            }
            val maskBitmap = createBitmap(modelW, modelH, Bitmap.Config.ALPHA_8)
            maskBitmap.setPixels(maskPixels, 0, modelW, 0, 0, modelW, modelH)
    
            val scaledMask = if (originalW != modelW || originalH != modelH) {
                val resized = maskBitmap.scale(originalW, originalH)
                maskBitmap.recycle()
                resized
            } else {
                maskBitmap
            }
                
            val origPixels = IntArray(originalW * originalH)
            chunk.getPixels(origPixels, 0, originalW, 0, 0, originalW, originalH)
            
            val maskAlphas = IntArray(originalW * originalH)
            scaledMask.getPixels(maskAlphas, 0, originalW, 0, 0, originalW, originalH)
            scaledMask.recycle()
            
            // reuse origPixels in place instead of allocating outPixels
            for (i in origPixels.indices) {
                val orig = origPixels[i]
                origPixels[i] = Color.argb(
                    Color.alpha(maskAlphas[i]),
                    Color.red(orig), Color.green(orig), Color.blue(orig)
                )
            }
            val result = createBitmap(originalW, originalH, Bitmap.Config.ARGB_8888)
            result.setPixels(origPixels, 0, originalW, 0, 0, originalW, originalH)
            return result
        }
    
        // Non-RMBG branch (unchanged)
        val outPixels = IntArray(modelW * modelH)
        if (outputChannels == 1) {
            for (i in 0 until modelW * modelH) {
                val alpha = clamp255(outputArray[i] * 255f)
                val orig = pixels[i]
                outPixels[i] =
                    Color.argb(alpha, Color.red(orig), Color.green(orig), Color.blue(orig))
            }
        } else {
            for (i in 0 until modelW * modelH) {
                val r: Int
                val g: Int
                val b: Int
                if (isNCHW) {
                    r = clamp255(outputArray[i] * 255f)
                    g = clamp255(outputArray[modelW * modelH + i] * 255f)
                    b = clamp255(outputArray[2 * modelW * modelH + i] * 255f)
                } else {
                    r = clamp255(outputArray[i * 3] * 255f)
                    g = clamp255(outputArray[i * 3 + 1] * 255f)
                    b = clamp255(outputArray[i * 3 + 2] * 255f)
                }
                outPixels[i] = Color.argb(255, r, g, b)
            }
        }
        val modelResult = createBitmap(modelW, modelH, Bitmap.Config.ARGB_8888)
        modelResult.setPixels(outPixels, 0, modelW, 0, 0, modelW, modelH)
        return if (modelW != originalW || modelH != originalH) {
            val resized = modelResult.scale(originalW, originalH)
            modelResult.recycle()
            resized
        } else {
            modelResult
        }
    }
    
    private fun computeEvenTileBoundaries(totalSize: Int, maxTileSize: Int): List<Pair<Int, Int>> {
        if (totalSize <= maxTileSize) return listOf(0 to totalSize)
        val count = kotlin.math.ceil(totalSize.toFloat() / maxTileSize).toInt().coerceAtLeast(1)
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
    
    private fun scaleBitmap(src: Bitmap, w: Int, h: Int): Bitmap {
        var current = src
        while (current.width > w * 2 || current.height > h * 2) {
            val next = drawScaled(
                current, (current.width / 2).coerceAtLeast(w), (current.height / 2).coerceAtLeast(h)
            )
            if (current !== src) current.recycle()
            current = next
        }
        val result = drawScaled(current, w, h)
        if (current !== src) current.recycle()
        return result
    }

    private fun drawScaled(src: Bitmap, w: Int, h: Int): Bitmap {
        val dst = createBitmap(w, h, src.config ?: Bitmap.Config.ARGB_8888)
        val canvas = Canvas(dst)
        val paint = Paint().apply {
            isAntiAlias = true
            isFilterBitmap = true
        }
        canvas.drawBitmap(
            src,
            android.graphics.Rect(0, 0, src.width, src.height),
            android.graphics.Rect(0, 0, w, h),
            paint
        )
        return dst
    }

    private fun clamp255(v: Float): Int = 0.coerceAtLeast(255.coerceAtMost(v.toInt()))
}
