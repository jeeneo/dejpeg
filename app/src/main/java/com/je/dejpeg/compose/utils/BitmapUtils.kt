package com.je.dejpeg.compose.utils

import android.graphics.Bitmap
import android.graphics.Matrix

object BitmapUtils {
    fun matchBitmapOrientation(
        input: Bitmap,
        output: Bitmap
    ): Bitmap {
        val candidates = generateOrientationCandidates(output)

        var bestBitmap = output
        var lowestDiff = Long.MAX_VALUE

        for (candidate in candidates) {
            if (candidate.width != input.width || candidate.height != input.height) {
                continue
            }

            val diff = calculateBitmapDifference(input, candidate)
            if (diff < lowestDiff) {
                lowestDiff = diff
                bestBitmap = candidate
            }
        }

        return bestBitmap
    }
    private fun generateOrientationCandidates(bitmap: Bitmap): List<Bitmap> {
        val list = mutableListOf<Bitmap>()

        list.add(bitmap)
        list.add(rotate(bitmap, 90f))
        list.add(rotate(bitmap, 180f))
        list.add(rotate(bitmap, 270f))

        list.add(flip(bitmap, horizontal = true))
        list.add(flip(bitmap, horizontal = false))

        list.add(flip(rotate(bitmap, 90f), true))
        list.add(flip(rotate(bitmap, 90f), false))

        return list.distinct()
    }
    private fun rotate(src: Bitmap, degrees: Float): Bitmap {
        val matrix = Matrix()
        matrix.postRotate(degrees)
        return Bitmap.createBitmap(src, 0, 0, src.width, src.height, matrix, true)
    }
    private fun flip(src: Bitmap, horizontal: Boolean): Bitmap {
        val matrix = Matrix()
        if (horizontal) {
            matrix.preScale(-1f, 1f)
        } else {
            matrix.preScale(1f, -1f)
        }
        return Bitmap.createBitmap(src, 0, 0, src.width, src.height, matrix, true)
    }
    private fun calculateBitmapDifference(b1: Bitmap, b2: Bitmap): Long {
        var diff = 0L

        for (y in 0 until b1.height step 10) {
            for (x in 0 until b1.width step 10) {
                diff += kotlin.math.abs(b1.getPixel(x, y) - b2.getPixel(x, y))
            }
        }
        return diff
    }

}