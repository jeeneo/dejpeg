/*
 * SPDX-FileCopyrightText: 2025 - 2026 dryerlint <https://codeberg.org/dryerlint>
 * SPDX-License-Identifier: GNU Affero General Public License v3.0 or later
 */

@file:Suppress("SpellCheckingInspection")

package com.je.dejpeg.utils.brisque

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.core.graphics.scale
import com.je.dejpeg.R
import com.je.dejpeg.utils.HashUtils
import kotlinx.coroutines.yield
import java.io.File
import java.lang.ref.WeakReference
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import com.je.dejpeg.utils.helpers.AssetExtractor

class BRISQUEDescaler(
    private val brisqueAssessor: BRISQUEAssessor
) {
    companion object {
        private const val TAG = "BRISQUEDescaler"
        private const val DEFAULT_COARSE_STEP = 20           // pixels step for coarse scan
        private const val DEFAULT_FINE_STEP = 5              // pixels step for fine scan
        private const val DEFAULT_FINE_RANGE = 30            // pixels range around coarse best
        private const val DEFAULT_MIN_WIDTH_RATIO = 0.5f     // minimum width as fraction of original
        private const val BRISQUE_WEIGHT = 0.7f              // 70% weight for BRISQUE
        private const val SHARPNESS_WEIGHT = 0.3f            // 30% weight for sharpness
        fun initialize(context: Context) {
            BRISQUEAssessor.initialize(context)
        }
    }

    data class ProgressUpdate(
        val phase: String,
        val currentStep: Int,
        val totalSteps: Int,
        val currentSize: String,
        val message: String
    )

    data class DescaleResult(
        val originalWidth: Int,
        val originalHeight: Int,
        val detectedOptimalWidth: Int,
        val detectedOptimalHeight: Int,
        val bestBrisqueScore: Float,
        val bestSharpness: Float,
        val combinedScore: Float,
        val scaleBitmap: Bitmap,
        val coarseScanResults: List<ScanResult>,
        val fineScanResults: List<ScanResult>
    )

    data class ScanResult(
        val width: Int,
        val height: Int,
        val brisqueScore: Float,
        val sharpness: Float,
        val combinedScore: Float
    )

    suspend fun descale(
        context: Context,
        bitmap: Bitmap,
        coarseStep: Int = DEFAULT_COARSE_STEP,
        fineStep: Int = DEFAULT_FINE_STEP,
        fineRange: Int = DEFAULT_FINE_RANGE,
        minWidthRatio: Float = DEFAULT_MIN_WIDTH_RATIO,
        brisqueWeight: Float = BRISQUE_WEIGHT,
        sharpnessWeight: Float = SHARPNESS_WEIGHT,
        onProgress: ((ProgressUpdate) -> Unit)? = null
    ): DescaleResult {
        val origW = bitmap.width
        val origH = bitmap.height
        val minW = (origW * minWidthRatio).toInt()
        Log.d(TAG, "Starting: ${origW}x${origH}")
        Log.d(TAG, "Min width: $minW, Coarse step: $coarseStep, Fine step: $fineStep")
        onProgress?.invoke(
            ProgressUpdate(
                phase = context.getString(R.string.brisque_phase_initialization),
                currentStep = 0,
                totalSteps = 100,
                currentSize = "${origW}x${origH}",
                message = context.getString(R.string.brisque_analyzing_original)
            )
        )
        val originalBrisqueScore = computeBRISQUE(bitmap)
        val originalSharpness = estimateSharpness(bitmap)
        Log.d(
            TAG, "Original image BRISQUE: %.2f, Sharpness: %.2f".format(
                originalBrisqueScore, originalSharpness
            )
        )
        onProgress?.invoke(
            ProgressUpdate(
                phase = context.getString(R.string.brisque_phase_initialization),
                currentStep = 5,
                totalSteps = 100,
                currentSize = "${origW}x${origH}",
                message = context.getString(
                    R.string.brisque_original_scores, originalBrisqueScore, originalSharpness
                )
            )
        )
        Log.d(TAG, "Scanning (coarse)...")
        val coarseResults = mutableListOf<ScanResult>()
        var bestCoarseIdx = 0
        var bestCoarseScore = Float.MAX_VALUE

        val totalCoarseSteps = ((origW - minW) / coarseStep) + 1
        var coarseStepCount = 0

        var w = origW
        while (w >= minW) {
            yield()
            val h = (origH * (w.toFloat() / origW)).toInt()
            coarseStepCount++
            onProgress?.invoke(
                ProgressUpdate(
                    phase = context.getString(R.string.brisque_phase_coarse_scan),
                    currentStep = 5 + (coarseStepCount * 45 / totalCoarseSteps),
                    totalSteps = 100,
                    currentSize = "${w}x${h}",
                    message = context.getString(
                        R.string.brisque_scaling_progress,
                        "${w}x${h}",
                        coarseStepCount,
                        totalCoarseSteps
                    )
                )
            )
            val resized = resizeBitmap(bitmap, w, h)
            try {
                val brisqueScore = computeBRISQUE(resized)
                val sharpness = estimateSharpness(resized)
                val result = ScanResult(w, h, brisqueScore, sharpness, brisqueScore)
                coarseResults.add(result)
                if (brisqueScore < bestCoarseScore) {
                    bestCoarseScore = brisqueScore
                    bestCoarseIdx = coarseResults.size - 1
                }
                Log.d(
                    TAG, "Coarse: ${w}x${h} - BRISQUE: %.2f, Sharpness: %.2f".format(
                        brisqueScore, sharpness
                    )
                )
            } finally {
                resized.recycle()
            }
            w -= coarseStep
        }
        val bestCoarseResult = coarseResults[bestCoarseIdx]
        Log.d(
            TAG,
            "Coarse best: ${bestCoarseResult.width}x${bestCoarseResult.height} with BRISQUE: %.2f".format(
                bestCoarseResult.brisqueScore
            )
        )

        onProgress?.invoke(
            ProgressUpdate(
                phase = context.getString(R.string.brisque_phase_coarse_complete),
                currentStep = 50,
                totalSteps = 100,
                currentSize = "${bestCoarseResult.width}x${bestCoarseResult.height}",
                message = context.getString(
                    R.string.brisque_best_coarse,
                    "${bestCoarseResult.width}x${bestCoarseResult.height}",
                    bestCoarseResult.brisqueScore
                )
            )
        )

        Log.d(TAG, "Scanning (fine)...")
        val fineResults = mutableListOf<ScanResult>()
        val startW = maxOf(minW, bestCoarseResult.width - fineRange)
        val endW = minOf(origW, bestCoarseResult.width + fineRange)
        Log.d(TAG, "Fine scan range: $startW to $endW px")

        val totalFineSteps = ((endW - startW) / fineStep) + 1
        var fineStepCount = 0
        w = startW
        while (w <= endW) {
            yield()
            val h = (origH * (w.toFloat() / origW)).toInt()
            fineStepCount++
            onProgress?.invoke(
                ProgressUpdate(
                    phase = context.getString(R.string.brisque_phase_fine_scan),
                    currentStep = 50 + (fineStepCount * 40 / totalFineSteps),
                    totalSteps = 100,
                    currentSize = "${w}x${h}",
                    message = context.getString(
                        R.string.brisque_refining_progress,
                        "${w}x${h}",
                        fineStepCount,
                        totalFineSteps
                    )
                )
            )
            val resized = resizeBitmap(bitmap, w, h)
            try {
                val brisqueScore = computeBRISQUE(resized)
                val sharpness = estimateSharpness(resized)
                val result = ScanResult(w, h, brisqueScore, sharpness, 0f)
                fineResults.add(result)
                Log.d(
                    TAG, "Fine: ${w}x${h} - BRISQUE: %.2f, Sharpness: %.2f".format(
                        brisqueScore, sharpness
                    )
                )
            } finally {
                resized.recycle()
            }
            w += fineStep
        }
        if (fineResults.isEmpty()) {
            Log.w(TAG, "No fine results, using coarse best")
            val descaled = resizeBitmap(bitmap, bestCoarseResult.width, bestCoarseResult.height)
            return DescaleResult(
                originalWidth = origW,
                originalHeight = origH,
                detectedOptimalWidth = bestCoarseResult.width,
                detectedOptimalHeight = bestCoarseResult.height,
                bestBrisqueScore = bestCoarseResult.brisqueScore,
                bestSharpness = bestCoarseResult.sharpness,
                combinedScore = bestCoarseResult.brisqueScore,
                scaleBitmap = descaled,
                coarseScanResults = coarseResults,
                fineScanResults = fineResults
            )
        }
        onProgress?.invoke(
            ProgressUpdate(
                phase = context.getString(R.string.brisque_phase_fine_done),
                currentStep = 90,
                totalSteps = 100,
                currentSize = "",
                message = context.getString(R.string.brisque_analyzing)
            )
        )

        val brisqueScores = fineResults.map { it.brisqueScore }
        val sharpnessScores = fineResults.map { it.sharpness }

        val minBrisque = brisqueScores.minOrNull() ?: 0f
        val maxBrisque = brisqueScores.maxOrNull() ?: 1f
        val minSharpness = sharpnessScores.minOrNull() ?: 0f
        val maxSharpness = sharpnessScores.maxOrNull() ?: 1f

        val brisqueRange = if (maxBrisque > minBrisque) maxBrisque - minBrisque else 1f
        val sharpnessRange = if (maxSharpness > minSharpness) maxSharpness - minSharpness else 1f

        val combinedScores = fineResults.mapIndexed { _, result ->
            val brisqueNorm = (result.brisqueScore - minBrisque) / brisqueRange
            val sharpnessNorm =
                if (sharpnessRange > 0) (result.sharpness - minSharpness) / sharpnessRange else 0f
            val combined = brisqueWeight * brisqueNorm + sharpnessWeight * (1f - sharpnessNorm)
            result.copy(combinedScore = combined)
        }

        val bestFineIdx =
            combinedScores.indices.minByOrNull { combinedScores[it].combinedScore } ?: 0
        val bestFineResult = combinedScores[bestFineIdx]

        Log.d(
            TAG,
            "Fine best: ${bestFineResult.width}x${bestFineResult.height} - BRISQUE: %.2f, Sharpness: %.2f, Combined: %.4f".format(
                bestFineResult.brisqueScore, bestFineResult.sharpness, bestFineResult.combinedScore
            )
        )

        onProgress?.invoke(
            ProgressUpdate(
                phase = context.getString(R.string.brisque_phase_finalizing),
                currentStep = 95,
                totalSteps = 100,
                currentSize = "${bestFineResult.width}x${bestFineResult.height}",
                message = context.getString(R.string.brisque_analyzing)
            )
        )
        val descaled = resizeBitmap(bitmap, bestFineResult.width, bestFineResult.height)

        return DescaleResult(
            originalWidth = origW,
            originalHeight = origH,
            detectedOptimalWidth = bestFineResult.width,
            detectedOptimalHeight = bestFineResult.height,
            bestBrisqueScore = bestFineResult.brisqueScore,
            bestSharpness = bestFineResult.sharpness,
            combinedScore = bestFineResult.combinedScore,
            scaleBitmap = descaled,
            coarseScanResults = coarseResults,
            fineScanResults = combinedScores
        )
    }

    fun estimateSharpness(bmp: Bitmap): Float {
        return try {
            val w = bmp.width
            val h = bmp.height
            val p = IntArray(w * h)
            bmp.getPixels(p, 0, w, 0, 0, w, h)
            fun bright(c: Int): Float =
                ((c shr 16 and 0xFF) * 0.299f + (c shr 8 and 0xFF) * 0.587f + (c and 0xFF) * 0.114f)

            var sum = 0.0
            var n = 0
            for (y in 1 until h - 1) {
                for (x in 1 until w - 1) {
                    val i = y * w + x
                    val gx =
                        -bright(p[i - w - 1]) + bright(p[i - w + 1]) + -2f * bright(p[i - 1]) + 2f * bright(
                            p[i + 1]
                        ) + -bright(p[i + w - 1]) + bright(p[i + w + 1])
                    val gy =
                        -bright(p[i - w - 1]) - 2f * bright(p[i - w]) - bright(p[i - w + 1]) + bright(
                            p[i + w - 1]
                        ) + 2f * bright(p[i + w]) + bright(p[i + w + 1])
                    sum += gx * gx + gy * gy
                    n++
                }
            }
            if (n == 0) return 0f
            val rawSharpness = sqrt(sum / n).toFloat()
            val normalized = 100f * (1f - exp(-rawSharpness / 30f))
            normalized
        } catch (e: Exception) {
            Log.e("Sharpness", e.message ?: "err")
            0f
        }
    }

    private fun computeBRISQUE(bitmap: Bitmap): Float {
        return try {
            brisqueAssessor.assessImageQualityFromBitmap(bitmap).takeIf { it >= 0 } ?: 100f
        } catch (e: Exception) {
            Log.e(TAG, "Error computing BRISQUE score: ${e.message}")
            100f
        }
    }

    private fun resizeBitmap(bitmap: Bitmap, width: Int, height: Int): Bitmap {
        if (bitmap.width == width && bitmap.height == height) return bitmap.copy(
            bitmap.config ?: Bitmap.Config.ARGB_8888, false
        )
        return bitmap.scale(width, height)
    }
}

class BRISQUEAssessor {
    companion object {
        private const val TAG = "BRISQUEAssessor"
        private var libraryLoaded = false
        private var appContextRef: WeakReference<Context>? = null
        private var modelFileRef: WeakReference<File>? = null
        private var rangeFileRef: WeakReference<File>? = null

        fun loadLibrary() {
            synchronized(this) {
                if (libraryLoaded) return
                try {
                    System.loadLibrary("brisque_jni")
                    libraryLoaded = true
                    Log.i(TAG, "BRISQUE library loaded")
                } catch (e: UnsatisfiedLinkError) {
                    Log.e(TAG, "Failed to load brisque_jni: ${e.message}")
                }
            }
        }

        fun initialize(context: Context) {
            loadLibrary()
            appContextRef = WeakReference(context.applicationContext)
            val brisqueDir = File(context.applicationContext.filesDir, "models/brisque")
            AssetExtractor.extractZipAsset(context.applicationContext, "embedbrisque.zip", brisqueDir)
            modelFileRef = WeakReference(File(brisqueDir, "brisque_model_live.yml"))
            rangeFileRef = WeakReference(File(brisqueDir, "brisque_range_live.yml"))
        }

        private fun getModelFile(context: Context): File? {
            val cached = modelFileRef?.get()
            if (cached?.exists() == true) return cached
            initialize(context)
            return modelFileRef?.get()
        }

        private fun getRangeFile(context: Context): File? {
            val cached = rangeFileRef?.get()
            if (cached?.exists() == true) return cached
            initialize(context)
            return rangeFileRef?.get()
        }

        init {
            loadLibrary()
        }
    }
    external fun computeBRISQUEFromFile(imagePath: String, modelPath: String, rangePath: String): Float
    fun assessImageQuality(imagePath: String, modelPath: String, rangePath: String): Float {
        if (!libraryLoaded) {
            Log.e(TAG, "Library not loaded")
            return -2.0f
        }
        if (!File(imagePath).exists() || !File(modelPath).exists() || !File(rangePath).exists()) {
            Log.e(TAG, "One or more files do not exist")
            return -1.0f
        }
        return try {
            Log.d(TAG, "Computing BRISQUE score for image: $imagePath")
            computeBRISQUEFromFile(imagePath, modelPath, rangePath).also {
                Log.d(TAG, "BRISQUE score computed: $it")
            }
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "Native method not found: ${e.message}", e)
            -2.0f
        } catch (e: Exception) {
            Log.e(TAG, "Error computing BRISQUE score: ${e.message}", e)
            -1.0f
        }
    }

    fun assessImageQualityFromBitmap(bitmap: Bitmap): Float {
        val context = appContextRef?.get()
        if (context == null) {
            Log.e(TAG, "BRISQUEAssessor not initialized with application context")
            return -2.0f
        }

        val modelFile = getModelFile(context)
        val rangeFile = getRangeFile(context)
        if (modelFile == null || rangeFile == null) {
            Log.e(TAG, "BRISQUE support files are unavailable")
            return -2.0f
        }

        val tempFile = File.createTempFile("brisque_assess_", ".png", context.cacheDir)
        return try {
            tempFile.outputStream().use { output ->
                if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                    Log.e(TAG, "Failed to encode bitmap for BRISQUE assessment")
                    return -1.0f
                }
            }
            assessImageQuality(
                imagePath = tempFile.absolutePath,
                modelPath = modelFile.absolutePath,
                rangePath = rangeFile.absolutePath
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error preparing bitmap for BRISQUE assessment: ${e.message}", e)
            -1.0f
        } finally {
            tempFile.delete()
        }
    }
}
