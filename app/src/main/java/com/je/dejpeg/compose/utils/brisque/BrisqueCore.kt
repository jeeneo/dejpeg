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

import android.graphics.Bitmap
import android.util.Log
import kotlin.math.*

/**
 * attempt at a pure Kotlin implementation of BRISQUE
 * primarily based on `qualitybrisque.cpp` in opencv_contrib (https://github.com/opencv/opencv_contrib/blob/master/modules/quality/src/qualitybrisque.cpp)
 * 
 * NOTE: LLMs (Generative AIs) were used to help with algorithm reimplementation and porting, however the final code was manually reviewed
 * it's not a direct 1-to-1 translation of the original C++, but aims to follow the same algorithmic steps
 *
 * more work will need to be done but the current implementation should be functionally correct and produce similar or close results to the original.
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
        createGKernel(GAUSSIAN_KERNEL_SIZE, GAUSSIAN_SIGMA)
    }

    private fun createGKernel(size: Int, sigma: Double): DoubleArray {
        val kernel = DoubleArray(size)
        val half = size / 2
        var sum = 0.0
        for (i in 0 until size) {
            val x = i - half
            kernel[i] = exp(-x * x / (2 * sigma * sigma))
            sum += kernel[i]
        }
        for (i in 0 until size) {
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
    fun computeMSCN(image: FloatArray, buf1: FloatArray, buf2: FloatArray, width: Int, height: Int) {
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

    /**
     * Lanczos approximation of gamma function (tgamma)
     */
    fun tgamma(x: Double): Double {
        if (x <= 0.0) return Double.NaN
        if (x < 0.5) {
            return PI / (sin(PI * x) * tgamma(1.0 - x)) // reflection formula: Gamma(x) * Gamma(1-x) = PI / sin(PI*x)
        }
        val g = 7.0
        val coefficients = doubleArrayOf( // Lanczos coefficients for g=7, n=9
            0.99999999999980993,
            676.5203681218851,
            -1259.1392167224028,
            771.32342877765313,
            -176.61502916214059,
            12.507343278686905,
            -0.13857109526572012,
            9.9843695780195716e-6,
            1.5056327351493116e-7
        )
        var z = x - 1.0
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
            val rHatNorm = rHat * (gammaHat.pow(3) + 1) * (gammaHat + 1) / (gammaHat.pow(2) + 1).pow(2)
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
            intArrayOf(0, 1),
            intArrayOf(1, 0),
            intArrayOf(1, 1),
            intArrayOf(-1, 1) 
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

    fun scaleFeatures(features: FloatArray, rangeMin: FloatArray, rangeMax: FloatArray): FloatArray {
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
