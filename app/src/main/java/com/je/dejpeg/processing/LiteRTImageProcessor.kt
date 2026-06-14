package com.je.dejpeg.processing

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.util.Log
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale
import com.je.dejpeg.R
import com.je.dejpeg.utils.LiteRtModelManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder

class LiteRtImageProcessor(
    private val context: Context, private val modelManager: LiteRtModelManager
) {
    private companion object {
        const val PAD_FACTOR = 8
    }

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

    // hardcode the test model filename here for the quick check
    var modelFileName: String = "fbcnn_color_fp16_float16.tflite"

    suspend fun processImage(
        inputBitmap: Bitmap, strength: Float, callback: ProcessCallback
    ) = withContext(Dispatchers.Default) {
        isCancelled = false
        try {
            val interpreter = modelManager.loadModel(modelFileName)

            // read fixed model size once
            val imageInputIndex = (0 until interpreter.inputTensorCount).first {
                interpreter.getInputTensor(it).shape().size == 4
            }
            val imageShape = interpreter.getInputTensor(imageInputIndex).shape()
            val isNCHW = imageShape[1] == 3
            val modelH = if (isNCHW) imageShape[2] else imageShape[1]
            val modelW = if (isNCHW) imageShape[3] else imageShape[2]

            val config = Bitmap.Config.ARGB_8888
            val bitmapToProcess =
                if (inputBitmap.config != config) inputBitmap.copy(config, true) else inputBitmap

            val width = bitmapToProcess.width
            val height = bitmapToProcess.height
            val overlap = 16 // small fixed overlap to reduce seams

            val mustTile = width > modelW || height > modelH

            val result = if (!mustTile) {
                withContext(Dispatchers.Main) { callback.onProgress(context.getString(R.string.processing)) }
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
        val total = cols * rows

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

                // processed comes back at extractW x extractH (already rescaled to original tile size)
                val cropped =
                    if (expandLeft > 0 || expandTop > 0 || processed.width != tileW || processed.height != tileH) {
                        Bitmap.createBitmap(processed, expandLeft, expandTop, tileW, tileH)
                    } else processed

                canvas.drawBitmap(cropped, tileX.toFloat(), tileY.toFloat(), null)
                if (cropped !== processed) processed.recycle()
                cropped.recycle()

                done++
                withContext(Dispatchers.Main) {
                    callback.onProgress("Tile $done/$total")
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

        val scaled = if (chunk.width != modelW || chunk.height != modelH) {
            chunk.scale(modelW, modelH)
        } else chunk

        val pixels = IntArray(modelW * modelH)
        scaled.getPixels(pixels, 0, modelW, 0, 0, modelW, modelH)

        val inputArray = FloatArray(3 * modelW * modelH)
        for (i in 0 until modelW * modelH) {
            val c = pixels[i]
            if (isNCHW) {
                inputArray[i] = Color.red(c) / 255f
                inputArray[modelW * modelH + i] = Color.green(c) / 255f
                inputArray[2 * modelW * modelH + i] = Color.blue(c) / 255f
            } else {
                // NHWC: interleaved RGB
                inputArray[i * 3] = Color.red(c) / 255f
                inputArray[i * 3 + 1] = Color.green(c) / 255f
                inputArray[i * 3 + 2] = Color.blue(c) / 255f
            }
        }

        val inputBuffer =
            ByteBuffer.allocateDirect(4 * inputArray.size).order(ByteOrder.nativeOrder())
        inputArray.forEach { inputBuffer.putFloat(it) }
        inputBuffer.rewind()

        val imageOutputIndex = (0 until interpreter.outputTensorCount).first {
            interpreter.getOutputTensor(it).shape().size == 4
        }

        val outputSize = 3 * modelW * modelH
        val outputBuffer = ByteBuffer.allocateDirect(4 * outputSize).order(ByteOrder.nativeOrder())


        val inputs = arrayOfNulls<Any>(interpreter.inputTensorCount)
        for (i in 0 until interpreter.inputTensorCount) {
            val shape = interpreter.getInputTensor(i).shape()
            inputs[i] = when {
                i == imageInputIndex -> inputBuffer
                shape.fold(1) { a, b -> a * b } == 1 -> {
                    val qfBuffer = ByteBuffer.allocateDirect(4).order(ByteOrder.nativeOrder())
                    qfBuffer.putFloat(strength / 100f)
                    qfBuffer.rewind()
                    qfBuffer
                }

                else -> throw RuntimeException("Unhandled input tensor shape: ${shape.joinToString()}")
            }
        }
        val outputs = mutableMapOf<Int, Any>()
        for (i in 0 until interpreter.outputTensorCount) {
            outputs[i] = if (i == imageOutputIndex) {
                outputBuffer
            } else {
                ByteBuffer.allocateDirect(4 * interpreter.getOutputTensor(i).shape().fold(1){a,b->a*b})
                    .order(ByteOrder.nativeOrder())
            }
        }
        interpreter.runForMultipleInputsOutputs(inputs, outputs)
        outputBuffer.rewind()
        val outputArray = FloatArray(outputSize) { outputBuffer.getFloat() }
        val outTensor = interpreter.getOutputTensor(imageOutputIndex)
        Log.d(
            "LiteRtDebug",
            "output dtype=${outTensor.dataType()}, shape=${outTensor.shape().joinToString()}"
        )
        Log.d(
            "LiteRtDebug",
            "output range: min=${outputArray.min()}, max=${outputArray.max()}, sample=${
                outputArray.take(10)
            }"
        )

        val outputCount = interpreter.outputTensorCount
        for (i in 0 until outputCount) {
            val t = interpreter.getOutputTensor(i)
            Log.d("LiteRtDebug", "output $i: shape=${t.shape().joinToString()}, dtype=${t.dataType()}")
        }

        val outPixels = IntArray(modelW * modelH)
        for (i in 0 until modelW * modelH) {
            val (r, g, b) = if (isNCHW) {
                Triple(
                    clamp255(outputArray[i] * 255f),
                    clamp255(outputArray[modelW * modelH + i] * 255f),
                    clamp255(outputArray[2 * modelW * modelH + i] * 255f)
                )
            } else {
                Triple(
                    clamp255(outputArray[i * 3] * 255f),
                    clamp255(outputArray[i * 3 + 1] * 255f),
                    clamp255(outputArray[i * 3 + 2] * 255f)
                )
            }
            outPixels[i] = Color.argb(255, r, g, b)
        }
        val modelResult = createBitmap(modelW, modelH, Bitmap.Config.ARGB_8888)
        modelResult.setPixels(outPixels, 0, modelW, 0, 0, modelW, modelH)

        if (scaled !== chunk) scaled.recycle()

        // scale back up to original size
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

    private fun clamp255(v: Float): Int = 0.coerceAtLeast(255.coerceAtMost(v.toInt()))
}
