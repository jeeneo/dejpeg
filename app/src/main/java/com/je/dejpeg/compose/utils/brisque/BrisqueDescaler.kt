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

package com.je.dejpeg.compose.utils.brisque

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import kotlinx.coroutines.yield
import kotlin.math.sqrt
import androidx.core.graphics.scale

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
        onProgress?.invoke(ProgressUpdate(
            phase = "initialization",
            currentStep = 0,
            totalSteps = 100,
            currentSize = "${origW}x${origH}",
            message = "Analyzing original image..."
        ))
        val originalBrisqueScore = computeBRISQUE(bitmap)
        val originalSharpness = estimateSharpness(bitmap)
        Log.d(TAG, "Original image BRISQUE: %.2f, Sharpness: %.2f".format(originalBrisqueScore, originalSharpness))
        onProgress?.invoke(ProgressUpdate(
            phase = "initialization",
            currentStep = 5,
            totalSteps = 100,
            currentSize = "${origW}x${origH}",
            message = "Original BRISQUE: %.2f, Sharpness: %.2f".format(originalBrisqueScore, originalSharpness)
        ))
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
            onProgress?.invoke(ProgressUpdate(
                phase = "coarse scan",
                currentStep = 5 + (coarseStepCount * 45 / totalCoarseSteps),
                totalSteps = 100,
                currentSize = "${w}x${h}",
                message = "Scaling ${w}x${h} (${coarseStepCount}/${totalCoarseSteps})"
            ))
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
                Log.d(TAG, "Coarse: ${w}x${h} - BRISQUE: %.2f, Sharpness: %.2f".format(brisqueScore, sharpness))
            } finally {
                resized.recycle()
            }
            w -= coarseStep
        }
        val bestCoarseResult = coarseResults[bestCoarseIdx]
        Log.d(TAG, "Coarse best: ${bestCoarseResult.width}x${bestCoarseResult.height} with BRISQUE: %.2f".format(bestCoarseResult.brisqueScore))
        
        onProgress?.invoke(ProgressUpdate(
            phase = "coarse scan complete",
            currentStep = 50,
            totalSteps = 100,
            currentSize = "${bestCoarseResult.width}x${bestCoarseResult.height}",
            message = "Best coarse result: ${bestCoarseResult.width}x${bestCoarseResult.height} (BRISQUE: %.2f)".format(bestCoarseResult.brisqueScore)
        ))
        
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
            onProgress?.invoke(ProgressUpdate(
                phase = "fine scan",
                currentStep = 50 + (fineStepCount * 40 / totalFineSteps),
                totalSteps = 100,
                currentSize = "${w}x${h}",
                message = "Refining ${w}x${h} (${fineStepCount}/${totalFineSteps})"
            ))
            val resized = resizeBitmap(bitmap, w, h)
            try {
                val brisqueScore = computeBRISQUE(resized)
                val sharpness = estimateSharpness(resized)
                val result = ScanResult(w, h, brisqueScore, sharpness, 0f)
                fineResults.add(result)
                Log.d(TAG, "Fine: ${w}x${h} - BRISQUE: %.2f, Sharpness: %.2f".format(brisqueScore, sharpness))
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
        onProgress?.invoke(ProgressUpdate(
            phase = "fine scan done",
            currentStep = 90,
            totalSteps = 100,
            currentSize = "",
            message = "Analyzing..."
        ))

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
            val sharpnessNorm = if (sharpnessRange > 0) (result.sharpness - minSharpness) / sharpnessRange else 0f
            val combined = brisqueWeight * brisqueNorm + sharpnessWeight * (1f - sharpnessNorm)
            result.copy(combinedScore = combined)
        }

        val bestFineIdx = combinedScores.indices.minByOrNull { combinedScores[it].combinedScore } ?: 0
        val bestFineResult = combinedScores[bestFineIdx]

        Log.d(TAG, "Fine best: ${bestFineResult.width}x${bestFineResult.height} - BRISQUE: %.2f, Sharpness: %.2f, Combined: %.4f".format(
            bestFineResult.brisqueScore,
            bestFineResult.sharpness,
            bestFineResult.combinedScore
        ))

        onProgress?.invoke(ProgressUpdate(
            phase = "finalizing",
            currentStep = 95,
            totalSteps = 100,
            currentSize = "${bestFineResult.width}x${bestFineResult.height}",
            message = "Optimal size: ${bestFineResult.width}x${bestFineResult.height} (BRISQUE: %.2f)".format(bestFineResult.brisqueScore)
        ))
        val descaled = resizeBitmap(bitmap, bestFineResult.width, bestFineResult.height)
        onProgress?.invoke(ProgressUpdate(
            phase = "complete",
            currentStep = 100,
            totalSteps = 100,
            currentSize = "${bestFineResult.width}x${bestFineResult.height}",
            message = "Descaling done! ${origW}x${origH} → ${bestFineResult.width}x${bestFineResult.height}"
        ))

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
            fun bright(c: Int): Float = ((c shr 16 and 0xFF) * 0.299f +
                                        (c shr 8 and 0xFF) * 0.587f +
                                        (c and 0xFF) * 0.114f)
            var sum = 0.0
            var n = 0
            for (y in 1 until h - 1) {
                for (x in 1 until w - 1) {
                    val i = y * w + x
                    val gx = -bright(p[i-w-1]) + bright(p[i-w+1]) +
                            -2f*bright(p[i-1]) + 2f*bright(p[i+1]) +
                            -bright(p[i+w-1]) + bright(p[i+w+1])
                    val gy = -bright(p[i-w-1]) -2f*bright(p[i-w]) -bright(p[i-w+1]) +
                            bright(p[i+w-1]) +2f*bright(p[i+w]) +bright(p[i+w+1])
                    sum += gx*gx + gy*gy
                    n++
                }
            }
            if (n == 0) return 0f
            val rawSharpness = sqrt(sum / n).toFloat()
            val normalized = 100f * (1f - kotlin.math.exp(-rawSharpness / 30f))
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
        if (bitmap.width == width && bitmap.height == height)
            return bitmap.copy(bitmap.config ?: Bitmap.Config.ARGB_8888, false)
        return bitmap.scale(width, height)
    }
}
