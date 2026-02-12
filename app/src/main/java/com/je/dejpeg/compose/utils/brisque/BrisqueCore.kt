/**
* Copyright (C) 2026 dryerlint <codeberg.org/dryerlint>
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
* Also please don't steal my work and claim it as your own, thanks.
*/

package com.je.dejpeg.compose.utils.brisque

import android.graphics.Bitmap
import android.util.Log
import kotlin.math.*
/**
 * pure Kotlin implementation of BRISQUE
 * primarily based on `qualitybrisque.cpp` in opencv_contrib
 */

object BrisqueCore {
    private const val TAG = "BrisqueCore"
    private const val GAUSSIAN_KERNEL_SIZE = 7
    private const val GAUSSIAN_SIGMA = 7.0 / 6.0
    private val gaussianKernel: FloatArray by lazy {
        createGaussianKernel(GAUSSIAN_KERNEL_SIZE, GAUSSIAN_SIGMA)
    }
    private fun createGaussianKernel(size: Int, sigma: Double): FloatArray {
        val kernel = FloatArray(size)
        val half = size / 2
        var sum = 0.0
        for (i in 0 until size) {
            val x = i - half
            kernel[i] = exp(-x * x / (2 * sigma * sigma)).toFloat()
            sum += kernel[i]
        }
        for (i in 0 until size) {
            kernel[i] = (kernel[i] / sum).toFloat()
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
                gray[baseIdx + i] = (0.299f * r + 0.587f * g + 0.114f * b) / 255.0f
            }
            y += rows
        }
        return gray
    }

    fun gaussianBlurInPlace(data: FloatArray, buf: FloatArray, width: Int, height: Int) {
        val kernel = gaussianKernel
        val half = GAUSSIAN_KERNEL_SIZE / 2
        for (y in 0 until height) {
            val rowOff = y * width
            for (x in 0 until width) {
                var sum = 0.0f
                for (k in -half..half) {
                    val nx = (x + k).coerceIn(0, width - 1)
                    sum += data[rowOff + nx] * kernel[k + half]
                }
                buf[rowOff + x] = sum
            }
        }
        for (y in 0 until height) {
            for (x in 0 until width) {
                var sum = 0.0f
                for (k in -half..half) {
                    val ny = (y + k).coerceIn(0, height - 1)
                    sum += buf[ny * width + x] * kernel[k + half]
                }
                data[y * width + x] = sum
            }
        }
    }
    
    fun computeMSCN(image: FloatArray, buf1: FloatArray, buf2: FloatArray, width: Int, height: Int) {
        val n = width * height        
        System.arraycopy(image, 0, buf1, 0, n)
        gaussianBlurInPlace(buf1, buf2, width, height)
        for (i in 0 until n) {
            buf2[i] = image[i] * image[i]
        }
        gaussianBlurInPlaceWithRowTemp(buf2, width, height)
        val eps = 1.0f / 255.0f
        for (i in 0 until n) {
            val mu = buf1[i]
            val variance = maxOf(0.0f, buf2[i] - mu * mu)
            val sigma = sqrt(variance) + eps
            buf1[i] = (image[i] - mu) / sigma
        }
    }

    private fun gaussianBlurInPlaceWithRowTemp(data: FloatArray, width: Int, height: Int) {
        val kernel = gaussianKernel
        val half = GAUSSIAN_KERNEL_SIZE / 2
        val rowBuf = FloatArray(width)        
        for (y in 0 until height) {
            val rowOff = y * width
            for (x in 0 until width) {
                var sum = 0.0f
                for (k in -half..half) {
                    val nx = (x + k).coerceIn(0, width - 1)
                    sum += data[rowOff + nx] * kernel[k + half]
                }
                rowBuf[x] = sum
            }
            System.arraycopy(rowBuf, 0, data, rowOff, width)
        }
        val colBuf = FloatArray(height)
        for (x in 0 until width) {
            for (y in 0 until height) {
                colBuf[y] = data[y * width + x]
            }
            for (y in 0 until height) {
                var sum = 0.0f
                for (k in -half..half) {
                    val ny = (y + k).coerceIn(0, height - 1)
                    sum += colBuf[ny] * kernel[k + half]
                }
                data[y * width + x] = sum
            }
        }
    }
    
    /**
     * Lanczos approximation of gamma function (tgamma)
     * More accurate than Stirling's approximation
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
    
    /**
     * AGGD statistics accumulator, avoids allocating a full array.
     */
    private class AGGDStats {
        var posCount = 0L
        var negCount = 0L
        var posSqSum = 0.0
        var negSqSum = 0.0
        var absSum = 0.0
        var totalCount = 0L
        fun add(value: Float) {
            totalCount++
            if (value > 0) {
                posCount++
                posSqSum += value * value
                absSum += value
            } else if (value < 0) {
                negCount++
                negSqSum += value * value
                absSum -= value
            }
        }
        fun compute(): Triple<Double, Double, Double> {
            if (posCount == 0L || negCount == 0L) {
                return Triple(1.0, 1.0, 1.0)
            }
            val leftSigma = sqrt(negSqSum / negCount)
            val rightSigma = sqrt(posSqSum / posCount)
            val gammaHat = leftSigma / rightSigma
            val rHat = (absSum / totalCount).pow(2) / ((negSqSum + posSqSum) / totalCount)
            val rHatNorm = rHat * (gammaHat.pow(3) + 1) * (gammaHat + 1) /
                    (gammaHat.pow(2) + 1).pow(2)
            var bestGamma = 0.2
            var prevDiff = Double.MAX_VALUE
            val step = 0.001
            var gamma = 0.2
            while (gamma < 10.0) {
                val rGamma = tgamma(2.0 / gamma).pow(2) /
                        (tgamma(1.0 / gamma) * tgamma(3.0 / gamma))
                val diff = abs(rGamma - rHatNorm)
                
                if (diff > prevDiff) break
                
                prevDiff = diff
                bestGamma = gamma
                gamma += step
            }
            return Triple(leftSigma, rightSigma, bestGamma)
        }
    }

    fun fitAGGD(data: FloatArray): Triple<Double, Double, Double> {
        val stats = AGGDStats()
        for (value in data) {
            stats.add(value)
        }
        return stats.compute()
    }

    fun computeFeaturesForScale(mscn: FloatArray, width: Int, height: Int): FloatArray {
        val features = FloatArray(18)
        val (leftSigma, rightSigma, gamma) = fitAGGD(mscn)
        features[0] = gamma.toFloat()
        features[1] = ((leftSigma * leftSigma + rightSigma * rightSigma) / 2).toFloat()
        val shifts = arrayOf(
            intArrayOf(0, 1),
            intArrayOf(1, 0),
            intArrayOf(1, 1),
            intArrayOf(-1, 1) 
        )
        
        for (shiftIdx in shifts.indices) {
            val dy = shifts[shiftIdx][0]
            val dx = shifts[shiftIdx][1]            
            val stats = AGGDStats()
            for (y in 0 until height) {
                val ny = y + dy
                if (ny < 0 || ny >= height) {
                    stats.totalCount += width
                    continue
                }
                val rowOff = y * width
                val nRowOff = ny * width
                for (x in 0 until width) {
                    val nx = x + dx
                    if (nx in 0 until width) {
                        stats.add(mscn[rowOff + x] * mscn[nRowOff + nx])
                    } else {
                        stats.totalCount++
                    }
                }
            }
            val (ls, rs, g) = stats.compute()
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

    fun computeBrisqueFeatures(bitmap: Bitmap): FloatArray {
        val allFeatures = FloatArray(36)
        val width = bitmap.width
        val height = bitmap.height
        val n = width * height        
        val gray = bitmapToGrayscaleFloat(bitmap)
        val buf1 = FloatArray(n)
        val buf2 = FloatArray(n)        
        computeMSCN(gray, buf1, buf2, width, height)
        val features1 = computeFeaturesForScale(buf1, width, height)
        System.arraycopy(features1, 0, allFeatures, 0, 18)
        val halfWidth = width / 2
        val halfHeight = height / 2
        if (halfWidth > 0 && halfHeight > 0) {
            for (y in 0 until halfHeight) {
                for (x in 0 until halfWidth) {
                    val sx = x * 2
                    val sy = y * 2
                    val idx00 = sy * width + sx
                    val idx01 = idx00 + 1
                    val idx10 = idx00 + width
                    val idx11 = idx10 + 1
                    gray[y * halfWidth + x] = (gray[idx00] + gray[idx01] + gray[idx10] + gray[idx11]) * 0.25f
                }
            }
            computeMSCN(gray, buf1, buf2, halfWidth, halfHeight)
            val features2 = computeFeaturesForScale(buf1, halfWidth, halfHeight)
            System.arraycopy(features2, 0, allFeatures, 18, 18)
        }
        return allFeatures
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

    fun rbfKernel(x: FloatArray, y: FloatArray, gamma: Float): Double {
        var distSq = 0.0
        for (i in x.indices) {
            val diff = x[i] - y[i]
            distSq += diff * diff
        }
        return exp(-gamma * distSq)
    }

    fun predictSVM(features: FloatArray, model: BrisqueSVMModel): Float {
        var sum = 0.0
        val svData = model.supportVectors
        val numFeatures = BrisqueSVMModel.NUM_FEATURES
        val gamma = model.gamma
        
        for (i in 0 until BrisqueSVMModel.NUM_SUPPORT_VECTORS) {
            val svStart = i * numFeatures
            var distSq = 0.0
            for (j in 0 until numFeatures) {
                val diff = features[j] - svData[svStart + j]
                distSq += diff * diff
            }
            sum += model.alphas[i] * exp(-gamma * distSq)
        }
        val rawScore = sum - model.rho
        return rawScore.coerceIn(0.0, 100.0).toFloat()
    }
    
    fun computeScore(bitmap: Bitmap, model: BrisqueSVMModel): Float {
        try {
            val features = computeBrisqueFeatures(bitmap)
            val scaledFeatures = scaleFeatures(features, model.rangeMin, model.rangeMax)
            return predictSVM(scaledFeatures, model)
        } catch (e: Exception) {
            Log.e(TAG, "Error computing BRISQUE score: ${e.message}")
            return -1.0f
        }
    }
}
