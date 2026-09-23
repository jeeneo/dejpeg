package com.je.dejpeg.processing

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.util.Log
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale
import com.je.dejpeg.R
import kotlinx.coroutines.yield
import java.io.File
import java.lang.ref.WeakReference
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

class BRISQUEAssessor {
    companion object {
        private const val TAG = "BRISQUEAssessor"
        private var contextRef: WeakReference<Context>? = null
        fun initialize(context: Context) {
            contextRef = WeakReference(context.applicationContext)
        }

        private fun getModel(): BrisqueSVMModel? {
            val ctx = contextRef?.get() ?: return null
            return BrisqueModelLoader.loadFromAssets(ctx)
        }
    }

    fun assessImageQualityFromBitmap(bitmap: Bitmap): Float {
        return try {
            val model = getModel()
            if (model == null) {
                Log.e(TAG, "Failed to load BRISQUE model - call initialize(context) first")
                return -2.0f
            }
            Log.d(TAG, "Computing BRISQUE score for bitmap (${bitmap.width}x${bitmap.height})")
            when (val result = BrisqueCore.computeScore(bitmap, model)) {
                is BrisqueResult.Success -> {
                    Log.d(TAG, "BRISQUE score computed: ${result.score}")
                    result.score
                }

                is BrisqueResult.Error -> {
                    Log.e(TAG, "BRISQUE computation error: ${result.message}")
                    -1.0f
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error computing BRISQUE score: ${e.message}", e)
            -1.0f
        }
    }
}


class BRISQUEDescaler(
    private val brisqueAssessor: BRISQUEAssessor
) {
    companion object {
        private const val TAG = "BRISQUEDescaler"
        private const val DEFAULT_COARSE_STEP = 20 // pixels step for coarse scan
        private const val DEFAULT_FINE_STEP = 5 // pixels step for fine scan
        private const val DEFAULT_FINE_RANGE = 30 // pixels range around coarse best
        private const val DEFAULT_MIN_WIDTH_RATIO = 0.5f // minimum width as fraction of original
        private const val BRISQUE_WEIGHT = 0.7f // 70% weight for BRISQUE
        private const val SHARPNESS_WEIGHT = 0.3f // 30% weight for sharpness
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
        Log.d(TAG, "Starting: ${origW}x${origH}, min $minW, coarse $coarseStep, fine $fineStep")

        fun report(phase: String, step: Int, size: String, message: String) {
            onProgress?.invoke(ProgressUpdate(phase, step, 100, size, message))
        }

        Scorer(bitmap, origW, origH).use { scorer ->
            report(
                context.getString(R.string.brisque_phase_initialization),
                0,
                "${origW}x${origH}",
                context.getString(R.string.brisque_analyzing_original)
            )

            val original = scorer.score(origW)
            Log.d(
                TAG, "Original BRISQUE: %.2f, Sharpness: %.2f".format(
                    original.brisqueScore, original.sharpness
                )
            )

            val coarseResults = mutableListOf<ScanResult>()
            var bestCoarse: ScanResult = original
            val totalCoarseSteps = ((origW - minW) / coarseStep) + 1
            var count = 0
            for (w in origW downTo minW step coarseStep) {
                val result = scorer.score(w)
                coarseResults += result
                if (result.brisqueScore < bestCoarse.brisqueScore) bestCoarse = result
                count++
                if (count % 4 == 0 || count == totalCoarseSteps) {
                    report(
                        context.getString(R.string.brisque_phase_coarse_scan),
                        5 + (count * 45 / totalCoarseSteps),
                        "${result.width}x${result.height}",
                        context.getString(
                            R.string.brisque_scaling_progress,
                            "${result.width}x${result.height}",
                            count,
                            totalCoarseSteps
                        )
                    )
                }
            }
            Log.d(
                TAG, "Coarse best: ${bestCoarse.width}x${bestCoarse.height} BRISQUE: %.2f".format(
                    bestCoarse.brisqueScore
                )
            )
            report(
                context.getString(R.string.brisque_phase_coarse_complete),
                50,
                "${bestCoarse.width}x${bestCoarse.height}",
                context.getString(
                    R.string.brisque_best_coarse,
                    "${bestCoarse.width}x${bestCoarse.height}",
                    bestCoarse.brisqueScore
                )
            )

            // ---- Fine scan: cached widths (incl. coarse best) are re-scored free ----
            val startW = maxOf(minW, bestCoarse.width - fineRange)
            val endW = minOf(origW, bestCoarse.width + fineRange)
            val fineResults = mutableListOf<ScanResult>()
            val totalFineSteps = ((endW - startW) / fineStep) + 1
            count = 0
            for (w in startW..endW step fineStep) {
                fineResults += scorer.score(w)
                count++
                if (count % 4 == 0 || count == totalFineSteps) {
                    val r = fineResults.last()
                    report(
                        context.getString(R.string.brisque_phase_fine_scan),
                        50 + (count * 40 / totalFineSteps),
                        "${r.width}x${r.height}",
                        context.getString(
                            R.string.brisque_refining_progress,
                            "${r.width}x${r.height}",
                            count,
                            totalFineSteps
                        )
                    )
                }
            }

            if (fineResults.isEmpty()) {
                Log.w(TAG, "No fine results, using coarse best")
                return DescaleResult(
                    originalWidth = origW,
                    originalHeight = origH,
                    detectedOptimalWidth = bestCoarse.width,
                    detectedOptimalHeight = bestCoarse.height,
                    bestBrisqueScore = bestCoarse.brisqueScore,
                    bestSharpness = bestCoarse.sharpness,
                    combinedScore = bestCoarse.brisqueScore,
                    scaleBitmap = resizeBitmap(bitmap, bestCoarse.width, bestCoarse.height),
                    coarseScanResults = coarseResults,
                    fineScanResults = fineResults
                )
            }

            report(
                context.getString(R.string.brisque_phase_fine_done),
                90,
                "",
                context.getString(R.string.brisque_analyzing)
            )

            val minBrisque = fineResults.minOf { it.brisqueScore }
            val maxBrisque = fineResults.maxOf { it.brisqueScore }
            val minSharpness = fineResults.minOf { it.sharpness }
            val maxSharpness = fineResults.maxOf { it.sharpness }
            val brisqueRange = if (maxBrisque > minBrisque) maxBrisque - minBrisque else 1f
            val sharpnessRange =
                if (maxSharpness > minSharpness) maxSharpness - minSharpness else 1f

            val combinedScores = fineResults.map { result ->
                val brisqueNorm = (result.brisqueScore - minBrisque) / brisqueRange
                val sharpnessNorm =
                    if (sharpnessRange > 0) (result.sharpness - minSharpness) / sharpnessRange else 0f
                result.copy(
                    combinedScore = brisqueWeight * brisqueNorm + sharpnessWeight * (1f - sharpnessNorm)
                )
            }
            val best = combinedScores.minByOrNull { it.combinedScore } ?: combinedScores.first()

            Log.d(
                TAG,
                "Fine best: ${best.width}x${best.height} - BRISQUE: %.2f, Sharpness: %.2f, Combined: %.4f".format(
                    best.brisqueScore, best.sharpness, best.combinedScore
                )
            )
            report(
                context.getString(R.string.brisque_phase_finalizing),
                95,
                "${best.width}x${best.height}",
                context.getString(R.string.brisque_analyzing)
            )

            return DescaleResult(
                originalWidth = origW,
                originalHeight = origH,
                detectedOptimalWidth = best.width,
                detectedOptimalHeight = best.height,
                bestBrisqueScore = best.brisqueScore,
                bestSharpness = best.sharpness,
                combinedScore = best.combinedScore,
                scaleBitmap = resizeBitmap(bitmap, best.width, best.height),
                coarseScanResults = coarseResults,
                fineScanResults = combinedScores
            )
        }
    }

    private inner class Scorer(
        private val source: Bitmap, private val origW: Int, private val origH: Int
    ) : AutoCloseable {
        private val scratch = createBitmap(origW, origH)
        private val canvas = Canvas(scratch)
        private val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        private val srcRect = Rect(0, 0, source.width, source.height)

        private val cache = HashMap<Int, ScanResult>()

        suspend fun score(width: Int): ScanResult {
            cache[width]?.let { return it }
            yield()
            val h = (origH * (width.toFloat() / origW)).roundToInt()
            canvas.drawBitmap(
                source, srcRect, RectF(0f, 0f, width.toFloat(), h.toFloat()), paint
            )
            val candidate = Bitmap.createBitmap(scratch, 0, 0, width, h)
            return ScanResult(
                width, h, computeBRISQUE(candidate), estimateSharpness(candidate), 0f
            ).also {
                cache[width] = it
            }
        }

        override fun close() {
            scratch.recycle()
        }
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

/**
 * An attempt at a pure Kotlin implementation of BRISQUE
 * Primarily based on `qualitybrisque.cpp` in opencv_contrib (https://github.com/opencv/opencv_contrib/blob/master/modules/quality/src/qualitybrisque.cpp)
 * ---
 * AI transparency: LLMs were used in the creation of the Kotlin implementation of BRISQUE, which is in a legal grey area because of the use of LLM-based tools and therefore
 * (I would guess?) subject to Public Domain licenses? (IANAL)
 * Full credit is the OpenCV contributors, et. al. for the original C++ implementation, which was used as a reference for this Kotlin code.
 * ---
 * wait lol you idiot, why didnt you just port qualitybrisque.cpp into a standalone binary instead of bringing it to kotlin if its basically math and it didnt really need opencv's internals?
 */
sealed class BrisqueResult {
    data class Success(val score: Float) : BrisqueResult()
    data class Error(val message: String, val exception: Exception? = null) : BrisqueResult()
}

object BrisqueCore {
    private const val TAG = "BrisqueCore"
    private const val GAUSSIAN_KERNEL_SIZE = 7
    private const val GAUSSIAN_SIGMA = 7.0 / 6.0
    private val gaussianKernel: DoubleArray by lazy {
        createGKernel()
    }

    private fun createGKernel(): DoubleArray {
        val kernel = DoubleArray(GAUSSIAN_KERNEL_SIZE)
        val half = GAUSSIAN_KERNEL_SIZE / 2
        var sum = 0.0
        for (i in 0 until GAUSSIAN_KERNEL_SIZE) {
            val x = i - half
            kernel[i] = exp(-x * x / (2 * GAUSSIAN_SIGMA * GAUSSIAN_SIGMA))
            sum += kernel[i]
        }
        for (i in 0 until GAUSSIAN_KERNEL_SIZE) {
            kernel[i] = kernel[i] / sum
        }
        return kernel
    }

    fun bitmapToGrayscaleFloat(bitmap: Bitmap): FloatArray {
        val width = bitmap.width
        val height = bitmap.height
        val gray = FloatArray(width * height)
        val chunkRows = 64
        val rowPixels = IntArray(width * chunkRows)
        var y = 0
        while (y < height) {
            val rows = minOf(chunkRows, height - y)
            bitmap.getPixels(rowPixels, 0, width, 0, y, width, rows)
            val baseIdx = y * width
            val count = rows * width
            for (i in 0 until count) {
                val pixel = rowPixels[i]
                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr 8) and 0xFF
                val b = pixel and 0xFF
                // match OpenCV's cvtColor(BGR2GRAY) for 8-bit input:
                // Y = (R*4899 + G*9617 + B*1868 + 8192) >> 14
                // rounds to an 8-bit integer, then divide by 255
                // to match the convertTo(CV_32F, 1./255.) step.
                val yInt = (r * 4899 + g * 9617 + b * 1868 + 8192) shr 14
                gray[baseIdx + i] = yInt / 255.0f
            }
            y += rows
        }
        return gray
    }

    /**
     * computes the Mean Subtracted Contrast Normalized (MSCN) coefficients.
     *
     * [buf1] and [buf2] are used as scratch space during Gaussian blur.
     * on return, [buf1] contains the MSCN output image,
     * [buf2] is left in an undefined state and should not be read after this call
     */
    fun computeMSCN(
        image: FloatArray, buf1: FloatArray, buf2: FloatArray, width: Int, height: Int
    ) {
        val n = width * height
        val kernel = gaussianKernel
        val half = GAUSSIAN_KERNEL_SIZE / 2
        for (i in 0 until n) {
            buf1[i] = image[i]
            buf2[i] = image[i] * image[i]
        }
        val rowBuf1 = FloatArray(width)
        val rowBuf2 = FloatArray(width)
        for (y in 0 until height) {
            val rowOff = y * width
            for (x in 0 until width) {
                var s1 = 0.0
                var s2 = 0.0
                for (k in -half..half) {
                    val nx = (x + k).coerceIn(0, width - 1)
                    val w = kernel[k + half]
                    s1 += buf1[rowOff + nx] * w
                    s2 += buf2[rowOff + nx] * w
                }
                rowBuf1[x] = s1.toFloat()
                rowBuf2[x] = s2.toFloat()
            }
            System.arraycopy(rowBuf1, 0, buf1, rowOff, width)
            System.arraycopy(rowBuf2, 0, buf2, rowOff, width)
        }
        val colBuf1 = FloatArray(height)
        val colBuf2 = FloatArray(height)
        for (x in 0 until width) {
            for (y in 0 until height) {
                val idx = y * width + x
                colBuf1[y] = buf1[idx]
                colBuf2[y] = buf2[idx]
            }
            for (y in 0 until height) {
                var s1 = 0.0
                var s2 = 0.0
                for (k in -half..half) {
                    val ny = (y + k).coerceIn(0, height - 1)
                    val w = kernel[k + half]
                    s1 += colBuf1[ny] * w
                    s2 += colBuf2[ny] * w
                }
                val idx = y * width + x
                buf1[idx] = s1.toFloat()
                buf2[idx] = s2.toFloat()
            }
        }
        // compute MSCN = (image - mu) / (sigma + eps)
        // after this, buf1 holds the MSCN coefficients.
        val eps = 1.0f / 255.0f
        for (i in 0 until n) {
            val mu = buf1[i]
            val variance = buf2[i].toDouble() - mu.toDouble() * mu.toDouble()
            if (variance <= 0.0) {
                buf1[i] = 0.0f
            } else {
                val sigma = sqrt(variance) + eps
                buf1[i] = ((image[i].toDouble() - mu.toDouble()) / sigma).toFloat()
            }
        }
    }

    fun tgamma(x: Double): Double {
        if (x <= 0.0) return Double.NaN
        if (x < 0.5) {
            return PI / (sin(PI * x) * tgamma(1.0 - x))
        }
        val g = 7.0
        val coefficients = doubleArrayOf(
            0.9999999999998099,
            676.5203681218851,
            -1259.1392167224028,
            771.3234287776531,
            -176.6150291621406,
            12.507343278686905,
            -0.13857109526572012,
            9.984369578019572E-6,
            1.5056327351493116e-7
        )
        val z = x - 1.0
        var ag = coefficients[0]
        for (i in 1 until coefficients.size) {
            ag += coefficients[i] / (z + i)
        }
        val t = z + g + 0.5
        return sqrt(2.0 * PI) * t.pow(z + 0.5) * exp(-t) * ag
    }

    private class AGGDStats {
        var posCount = 0L
        var negCount = 0L
        var posSqSum = 0.0
        var negSqSum = 0.0
        var absSum = 0.0
        var totalCount = 0L
        fun add(value: Float) {
            totalCount++
            // all accumulation in AGGD uses double precision for the pixel value.
            val dv = value.toDouble()
            if (value > 0) {
                posCount++
                posSqSum += dv * dv
                absSum += dv
            } else if (value < 0) {
                negCount++
                negSqSum += dv * dv
                absSum -= dv
            }
        }

        fun compute(): Triple<Double, Double, Double> {
            if (totalCount == 0L || posCount == 0L || negCount == 0L) {
                return Triple(0.0, 0.0, 1.0)
            }
            val sumSq = negSqSum + posSqSum
            if (sumSq == 0.0) {
                return Triple(0.0, 0.0, 1.0)
            }
            val leftSigma = sqrt(negSqSum / negCount)
            val rightSigmaRaw = sqrt(posSqSum / posCount)
            val epsilon = 1e-10
            val rightSigma = if (rightSigmaRaw == 0.0) epsilon else rightSigmaRaw
            val gammaHat = leftSigma / rightSigma
            val rHat = (absSum / totalCount).pow(2) / (sumSq / totalCount)
            val rHatNorm =
                rHat * (gammaHat.pow(3) + 1) * (gammaHat + 1) / (gammaHat.pow(2) + 1).pow(2)
            var prevGamma = 0.0
            var prevDiff = 1e10
            val sampling = 0.001
            var gam = 0.2
            while (gam < 10.0) {
                val rGam = tgamma(2.0 / gam).pow(2) / (tgamma(1.0 / gam) * tgamma(3.0 / gam))
                val diff = abs(rGam - rHatNorm)
                if (diff > prevDiff) break
                prevDiff = diff
                prevGamma = gam
                gam += sampling
            }
            return Triple(leftSigma, rightSigma, prevGamma)
        }
    }

    fun fitAGGD(data: FloatArray, count: Int = data.size): Triple<Double, Double, Double> {
        val stats = AGGDStats()
        for (i in 0 until count) {
            stats.add(data[i])
        }
        return stats.compute()
    }

    fun computeForScale(mscn: FloatArray, width: Int, height: Int): FloatArray {
        val n = width * height
        val features = FloatArray(18)
        val (leftSigma, rightSigma, gamma) = fitAGGD(mscn, n)
        features[0] = gamma.toFloat()
        features[1] = ((leftSigma * leftSigma + rightSigma * rightSigma) / 2).toFloat()
        val shifts = arrayOf(
            intArrayOf(0, 1), intArrayOf(1, 0), intArrayOf(1, 1), intArrayOf(-1, 1)
        )
        val pairProduct = FloatArray(n)
        for (shiftIdx in shifts.indices) {
            val dy = shifts[shiftIdx][0]
            val dx = shifts[shiftIdx][1]
            for (y in 0 until height) {
                val ny = y + dy
                val rowOff = y * width
                for (x in 0 until width) {
                    val nx = x + dx
                    pairProduct[rowOff + x] = if (ny in 0 until height && nx in 0 until width) {
                        mscn[rowOff + x] * mscn[ny * width + nx]
                    } else {
                        0.0f
                    }
                }
            }
            val (ls, rs, g) = fitAGGD(pairProduct, n)
            val constant = sqrt(tgamma(1.0 / g)) / sqrt(tgamma(3.0 / g))
            val meanParam = (rs - ls) * (tgamma(2.0 / g) / tgamma(1.0 / g)) * constant
            val baseIdx = 2 + shiftIdx * 4
            features[baseIdx] = g.toFloat()
            features[baseIdx + 1] = meanParam.toFloat()
            features[baseIdx + 2] = (ls * ls).toFloat()
            features[baseIdx + 3] = (rs * rs).toFloat()
        }
        return features
    }

    fun calcBrisqueFeatures(bitmap: Bitmap): FloatArray {
        val features = FloatArray(36)
        val width = bitmap.width
        val height = bitmap.height
        val n = width * height
        val gray = bitmapToGrayscaleFloat(bitmap)
        val buf1 = FloatArray(n)
        val buf2 = FloatArray(n)
        computeMSCN(gray, buf1, buf2, width, height)
        val features1 = computeForScale(buf1, width, height)
        System.arraycopy(features1, 0, features, 0, 18)
        val halfWidth = width / 2
        val halfHeight = height / 2
        if (halfWidth > 0 && halfHeight > 0) {
            val scale2 = FloatArray(halfWidth * halfHeight)
            ImageResampler.resizeInterCubic(gray, width, height, scale2, halfWidth, halfHeight)
            computeMSCN(scale2, buf1, buf2, halfWidth, halfHeight)
            val features2 = computeForScale(buf1, halfWidth, halfHeight)
            System.arraycopy(features2, 0, features, 18, 18)
        }
        return features
    }

    fun scaleFeatures(
        features: FloatArray, rangeMin: FloatArray, rangeMax: FloatArray
    ): FloatArray {
        val scaled = FloatArray(features.size)
        for (i in features.indices) {
            val min = rangeMin[i]
            val max = rangeMax[i]
            val range = max - min
            scaled[i] = if (range != 0f) {
                2.0f * (features[i] - min) / range - 1.0f
            } else {
                0.0f
            }
        }
        return scaled
    }

    fun predictSVM(features: FloatArray, model: BrisqueSVMModel): Float {
        val numFeatures = BrisqueSVMModel.NUM_FEATURES
        val numSV = BrisqueSVMModel.NUM_SUPPORT_VECTORS
        require(model.supportVectors.size == numSV * numFeatures) { "Support vector array size (${model.supportVectors.size}) " + "doesn't match expected ${numSV}x${numFeatures}" }
        require(model.alphas.size == numSV) { "Alphas array size (${model.alphas.size}) doesn't match expected $numSV" }
        var sum = 0.0
        val svData = model.supportVectors
        val gamma = model.gamma
        for (i in 0 until BrisqueSVMModel.NUM_SUPPORT_VECTORS) {
            val svStart = i * numFeatures
            var distSq = 0.0
            for (j in 0 until numFeatures) {
                val diff = features[j].toDouble() - svData[svStart + j].toDouble()
                distSq += diff * diff
            }
            val preExp = (-gamma * distSq).toFloat()
            sum += model.alphas[i] * exp(preExp.toDouble())
        }
        val rawScore = sum - model.rho
        return rawScore.coerceIn(0.0, 100.0).toFloat()
    }

    fun computeScore(bitmap: Bitmap, model: BrisqueSVMModel): BrisqueResult {
        return try {
            val features = calcBrisqueFeatures(bitmap)
            val scaledFeatures = scaleFeatures(features, model.rangeMin, model.rangeMax)
            BrisqueResult.Success(predictSVM(scaledFeatures, model))
        } catch (e: Exception) {
            Log.e(TAG, "Error computing BRISQUE score: ${e.message}")
            BrisqueResult.Error(e.message ?: "Unknown error", e)
        }
    }
}


data class BrisqueSVMModel(
    val supportVectors: FloatArray,
    val alphas: FloatArray,
    val rho: Float,
    val gamma: Float,
    val rangeMin: FloatArray,
    val rangeMax: FloatArray
) {
    companion object {
        const val NUM_FEATURES = 36
        const val NUM_SUPPORT_VECTORS = 774
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as BrisqueSVMModel
        if (!supportVectors.contentEquals(other.supportVectors)) return false
        if (!alphas.contentEquals(other.alphas)) return false
        if (rho != other.rho) return false
        if (gamma != other.gamma) return false
        if (!rangeMin.contentEquals(other.rangeMin)) return false
        if (!rangeMax.contentEquals(other.rangeMax)) return false
        return true
    }

    override fun hashCode(): Int {
        var result = supportVectors.contentHashCode()
        result = 31 * result + alphas.contentHashCode()
        result = 31 * result + rho.hashCode()
        result = 31 * result + gamma.hashCode()
        result = 31 * result + rangeMin.contentHashCode()
        result = 31 * result + rangeMax.contentHashCode()
        return result
    }
}

object BrisqueModelLoader {
    private const val TAG = "BrisqueModelLoader"
    private const val MAGIC = "BRSQ"

    @Volatile
    private var cachedModel: BrisqueSVMModel? = null

    fun loadFromAssets(context: Context): BrisqueSVMModel? {
        cachedModel?.let { return it }
        return synchronized(this) {
            cachedModel?.let { return it }
            try {
                val startTime = System.currentTimeMillis()
                val inputFile = File(context.cacheDir, "brisque_model.bin")
                if (!inputFile.exists() || inputFile.length() == 0L) {
                    context.assets.open("brisque_model.bin").use { input ->
                        inputFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                }
                val model = loadFromBinary(inputFile)
                val elapsed = System.currentTimeMillis() - startTime
                if (model != null) {
                    cachedModel = model
                    Log.i(TAG, "BRISQUE model loaded in ${elapsed}ms")
                }
                model
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load BRISQUE model: ${e.message}")
                null
            }
        }
    }

    private fun loadFromBinary(file: File): BrisqueSVMModel? {
        return try {
            val bytes = file.readBytes()
            val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            val magic = ByteArray(4)
            buffer.get(magic)
            if (String(magic) != MAGIC) {
                Log.e(TAG, "Invalid magic number")
                return null
            }
            buffer.int // version
            val numFeatures = buffer.int
            buffer.int // numSupportVectors
            val gamma = buffer.float
            val rho = buffer.float
            val numSvValues = buffer.int
            val supportVectors = FloatArray(numSvValues)
            for (i in 0 until numSvValues) {
                supportVectors[i] = buffer.float
            }
            val numAlphas = buffer.int
            val alphas = FloatArray(numAlphas)
            for (i in 0 until numAlphas) {
                alphas[i] = buffer.float
            }
            val rangeMin = FloatArray(numFeatures)
            val rangeMax = FloatArray(numFeatures)
            for (i in 0 until numFeatures) {
                rangeMin[i] = buffer.float
            }
            for (i in 0 until numFeatures) {
                rangeMax[i] = buffer.float
            }
            BrisqueSVMModel(
                supportVectors = supportVectors,
                alphas = alphas,
                rho = rho,
                gamma = gamma,
                rangeMin = rangeMin,
                rangeMax = rangeMax
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error loading binary model: ${e.message}")
            null
        }
    }

}

internal object ImageResampler {
    fun reflect101(index: Int, length: Int): Int {
        if (length <= 1) return 0
        return when {
            index < 0 -> minOf(-index, length - 1)
            index >= length -> maxOf(2 * length - index - 2, 0)
            else -> index
        }
    }

    fun cubicWeight(x: Double): Double {
        val a = -0.75
        val ax = abs(x)
        return when {
            ax <= 1.0 -> ((a + 2.0) * ax - (a + 3.0)) * ax * ax + 1.0
            ax < 2.0 -> (((a * ax - 5.0 * a) * ax + 8.0 * a) * ax) - 4.0 * a
            else -> 0.0
        }
    }

    fun resizeInterCubic(
        src: FloatArray,
        srcWidth: Int,
        srcHeight: Int,
        dst: FloatArray,
        dstWidth: Int,
        dstHeight: Int
    ) {
        val scaleX = srcWidth.toDouble() / dstWidth
        val scaleY = srcHeight.toDouble() / dstHeight
        val xWeights = DoubleArray(dstWidth * 4)
        val xSrcCols = IntArray(dstWidth * 4)

        for (dx in 0 until dstWidth) {
            val fx = (dx + 0.5) * scaleX - 0.5
            val sx = floor(fx).toInt()
            val tx = fx - sx
            val base = dx * 4
            xWeights[base] = cubicWeight(1.0 + tx)
            xWeights[base + 1] = cubicWeight(tx)
            xWeights[base + 2] = cubicWeight(1.0 - tx)
            xWeights[base + 3] = cubicWeight(2.0 - tx)
            for (n in 0..3) xSrcCols[base + n] = reflect101(sx + n - 1, srcWidth)
        }

        val yWeights = DoubleArray(dstHeight * 4)
        val ySrcRowOffs = IntArray(dstHeight * 4)
        for (dy in 0 until dstHeight) {
            val fy = (dy + 0.5) * scaleY - 0.5
            val sy = floor(fy).toInt()
            val ty = fy - sy
            val base = dy * 4
            yWeights[base] = cubicWeight(1.0 + ty)
            yWeights[base + 1] = cubicWeight(ty)
            yWeights[base + 2] = cubicWeight(1.0 - ty)
            yWeights[base + 3] = cubicWeight(2.0 - ty)
            for (m in 0..3) ySrcRowOffs[base + m] = reflect101(sy + m - 1, srcHeight) * srcWidth
        }

        for (dy in 0 until dstHeight) {
            val yBase = dy * 4
            val dstRowOff = dy * dstWidth
            for (dx in 0 until dstWidth) {
                val xBase = dx * 4
                var sum = 0.0
                for (m in 0..3) {
                    val rowOff = ySrcRowOffs[yBase + m]
                    val wY = yWeights[yBase + m]
                    for (n in 0..3) {
                        sum += src[rowOff + xSrcCols[xBase + n]] * wY * xWeights[xBase + n]
                    }
                }
                dst[dstRowOff + dx] = sum.toFloat()
            }
        }
    }
}
