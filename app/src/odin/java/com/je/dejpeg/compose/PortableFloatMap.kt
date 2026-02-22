/*
 * Portable FloatMap (PFM) reader/writer – Kotlin port
 *
 * Original Java version Copyright (c) 2017 Project Nayuki. (MIT License)
 * https://www.nayuki.io/page/portable-floatmap-format-io-java
 *
 * Adapted for Android/Kotlin use.
 */

package com.je.dejpeg.compose

import android.graphics.Bitmap
import android.graphics.Color
import androidx.core.graphics.createBitmap

class PortableFloatMap {

    var width: Int = 0
    var height: Int = 0
    var pixels: FloatArray = FloatArray(0)

    companion object {
        fun fromBitmap(src: Bitmap): Pair<PortableFloatMap, FloatArray?> {
            val w = src.width
            val h = src.height
            val hasAlpha = src.hasAlpha()
            val bmp = if (src.config != Bitmap.Config.ARGB_8888) src.copy(
                Bitmap.Config.ARGB_8888,
                true
            ) else src

            val pixInts = IntArray(w * h)
            bmp.getPixels(pixInts, 0, w, 0, 0, w, h)
            if (bmp !== src) bmp.recycle()

            val pfmPixels = FloatArray(w * h * 3)
            val alphaBuf = if (hasAlpha) FloatArray(w * h) else null

            for (row in 0 until h) {
                val pfmRow = h - 1 - row
                for (col in 0 until w) {
                    val px = pixInts[row * w + col]
                    val i = (pfmRow * w + col) * 3
                    pfmPixels[i] = Color.red(px) / 255f
                    pfmPixels[i + 1] = Color.green(px) / 255f
                    pfmPixels[i + 2] = Color.blue(px) / 255f
                    alphaBuf?.set(row * w + col, Color.alpha(px) / 255f)
                }
            }

            val pfm = PortableFloatMap().apply {
                this.width = w
                this.height = h
                pixels = pfmPixels
            }
            return pfm to alphaBuf
        }

        fun toBitmap(
            pfmPixels: FloatArray,
            width: Int,
            height: Int,
            alphaBuf: FloatArray?,
            config: Bitmap.Config = Bitmap.Config.ARGB_8888
        ): Bitmap {
            val outPixels = IntArray(width * height)
            for (pfmRow in 0 until height) {
                val bitmapRow = height - 1 - pfmRow
                for (col in 0 until width) {
                    val i = (pfmRow * width + col) * 3
                    val r = clamp255(pfmPixels[i] * 255f)
                    val g = clamp255(pfmPixels[i + 1] * 255f)
                    val b = clamp255(pfmPixels[i + 2] * 255f)
                    val a =
                        if (alphaBuf != null) clamp255(alphaBuf[bitmapRow * width + col] * 255f) else 255
                    outPixels[bitmapRow * width + col] = Color.argb(a, r, g, b)
                }
            }
            val bmp = createBitmap(width, height, config)
            bmp.setPixels(outPixels, 0, width, 0, 0, width, height)
            return bmp
        }

        private fun clamp255(v: Float) = v.toInt().coerceIn(0, 255)
    }
}
