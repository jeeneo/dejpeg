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
*/

/*
* If you use this code in your own project, please give credit
*/

package com.je.dejpeg.compose.utils.brisque

import kotlin.math.abs
import kotlin.math.floor

/**
 * helper for image resampling operations (bicubic interpolation)
 */

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
            xWeights[base]     = cubicWeight(1.0 + tx)
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
            yWeights[base]     = cubicWeight(1.0 + ty)
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
