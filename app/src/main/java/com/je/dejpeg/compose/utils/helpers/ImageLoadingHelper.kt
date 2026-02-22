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

package com.je.dejpeg.compose.utils.helpers

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale
import androidx.exifinterface.media.ExifInterface
import java.io.File

object ImageLoadingHelper {
    private const val THUMBNAIL_SIZE = 144
    private fun safabs(value: Int): Int = if (value < 0) -value else value

    fun loadBitmapWithRotation(context: Context, uri: Uri): Bitmap? = try {
        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            val bitmap = BitmapFactory.decodeStream(inputStream) ?: return null
            context.contentResolver.openInputStream(uri)?.use { exifStream ->
                val exif = ExifInterface(exifStream)
                applyExifOrientation(bitmap, exif)
            } ?: bitmap
        }
    } catch (_: Exception) {
        null
    }

    fun loadBitmapWithRotation(file: File): Bitmap? = try {
        val bitmap = BitmapFactory.decodeFile(file.absolutePath) ?: return null
        val exif = ExifInterface(file.absolutePath)
        applyExifOrientation(bitmap, exif)
    } catch (_: Exception) {
        null
    }

    private fun applyExifOrientation(bitmap: Bitmap, exif: ExifInterface): Bitmap {
        return when (exif.getAttributeInt(
            ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL
        )) {
            ExifInterface.ORIENTATION_ROTATE_90 -> rotateBitmap(bitmap, 90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> rotateBitmap(bitmap, 180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> rotateBitmap(bitmap, 270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> flipBitmap(
                bitmap, horizontal = true, vertical = false
            )

            ExifInterface.ORIENTATION_FLIP_VERTICAL -> flipBitmap(
                bitmap, horizontal = false, vertical = true
            )

            ExifInterface.ORIENTATION_TRANSPOSE -> flipBitmap(
                rotateBitmap(bitmap, 90f), horizontal = true, vertical = false
            )

            ExifInterface.ORIENTATION_TRANSVERSE -> flipBitmap(
                rotateBitmap(bitmap, 270f), horizontal = true, vertical = false
            )

            else -> bitmap
        }
    }

    private fun rotateBitmap(bitmap: Bitmap, degrees: Float): Bitmap {
        val matrix = Matrix().apply { postRotate(degrees) }
        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, false)
        if (rotated != bitmap) bitmap.recycle()
        return rotated
    }

    private fun flipBitmap(bitmap: Bitmap, horizontal: Boolean, vertical: Boolean): Bitmap {
        val matrix = Matrix().apply {
            postScale(
                if (horizontal) -1f else 1f,
                if (vertical) -1f else 1f,
                bitmap.width / 2f,
                bitmap.height / 2f
            )
        }
        val flipped = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, false)
        if (flipped != bitmap) bitmap.recycle()
        return flipped
    }

    fun generateThumbnail(bitmap: Bitmap, size: Int = THUMBNAIL_SIZE, blurRadius: Int = 5): Bitmap {
        val cropSize = minOf(bitmap.width, bitmap.height)
        val x = (bitmap.width - cropSize) / 2
        val y = (bitmap.height - cropSize) / 2
        val croppedBitmap =
            createBitmap(cropSize, cropSize, bitmap.config ?: Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(croppedBitmap)
        canvas.drawBitmap(bitmap, -x.toFloat(), -y.toFloat(), null)
        val resizedBitmap = croppedBitmap.scale(size, size)
        croppedBitmap.recycle()
        return if (blurRadius > 0) fastblur(resizedBitmap, 1f, blurRadius)
            ?: resizedBitmap else resizedBitmap
    }

    fun getFileNameFromUri(context: Context, uri: Uri): String {
        return try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val columnIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (columnIndex >= 0) {
                        cursor.getString(columnIndex) ?: "unknown"
                    } else {
                        uri.lastPathSegment ?: "unknown"
                    }
                } else {
                    uri.lastPathSegment ?: "unknown"
                }
            } ?: uri.lastPathSegment ?: "unknown"
        } catch (_: Exception) {
            uri.lastPathSegment ?: "unknown"
        }
    }

    // Source - https://stackoverflow.com/a/10028267
    // Posted by Yahel, modified by community. See post 'Timeline' for change history
    // Retrieved 2026-01-14, License - CC BY-SA 4.0

    /**
     * Stack Blur v1.0 from
     * http://www.quasimondo.com/StackBlurForCanvas/StackBlurDemo.html
     * Java Author: Mario Klingemann <mario at quasimondo.com>
     * http://incubator.quasimondo.com
     *
     * created Feburary 29, 2004
     * Android port : Yahel Bouaziz <yahel at kayenko.com>
     * http://www.kayenko.com
     * ported april 5th, 2012
     *
     * This is a compromise between Gaussian Blur and Box blur
     * It creates much better looking blurs than Box Blur, but is
     * 7x faster than my Gaussian Blur implementation.
     *
     * I called it Stack Blur because this describes best how this
     * filter works internally: it creates a kind of moving stack
     * of colors whilst scanning through the image. Thereby it
     * just has to add one new block of color to the right side
     * of the stack and remove the leftmost color. The remaining
     * colors on the topmost layer of the stack are either added on
     * or reduced by one, depending on if they are on the right or
     * on the left side of the stack.
     *
     * If you are using this algorithm in your code please add
     * the following line:
     * Stack Blur Algorithm by Mario Klingemann <mario@quasimondo.com>
     */

    fun fastblur(sentBitmap: Bitmap, scale: Float, radius: Int): Bitmap? {
        if (radius < 1) return null
        val bmp = sentBitmap.scale(
            (sentBitmap.width * scale).toInt(), (sentBitmap.height * scale).toInt(), false
        )
        val result = bmp.copy(bmp.config ?: Bitmap.Config.ARGB_8888, true) ?: return null
        val w = result.width
        val h = result.height
        val wm = w - 1
        val hm = h - 1
        val wh = w * h
        val div = radius * 2 + 1
        val pix = IntArray(wh).also { result.getPixels(it, 0, w, 0, 0, w, h) }
        val r = IntArray(wh)
        val g = IntArray(wh)
        val b = IntArray(wh)
        val vmin = IntArray(maxOf(w, h))
        val r1 = radius + 1
        var divsum = ((div + 1) shr 1); divsum *= divsum
        val dv = IntArray(256 * divsum) { it / divsum }
        val stack = Array(div) { IntArray(3) }
        var yi = 0
        var yw = 0
        for (y in 0 until h) {
            var rsum = 0
            var gsum = 0
            var bsum = 0
            var rinsum = 0
            var ginsum = 0
            var binsum = 0
            var routsum = 0
            var goutsum = 0
            var boutsum = 0
            for (i in -radius..radius) {
                val p = pix[yi + minOf(wm, maxOf(i, 0))]
                val sir = stack[i + radius]
                sir[0] = (p shr 16) and 0xff; sir[1] = (p shr 8) and 0xff; sir[2] = p and 0xff
                val rbs =
                    r1 - safabs(i); rsum += sir[0] * rbs; gsum += sir[1] * rbs; bsum += sir[2] * rbs
                if (i > 0) {
                    rinsum += sir[0]; ginsum += sir[1]; binsum += sir[2]
                } else {
                    routsum += sir[0]; goutsum += sir[1]; boutsum += sir[2]
                }
            }
            var stackpointer = radius
            for (x in 0 until w) {
                r[yi] = dv[rsum]; g[yi] = dv[gsum]; b[yi] = dv[bsum]
                rsum -= routsum; gsum -= goutsum; bsum -= boutsum
                var sir = stack[(stackpointer - radius + div) % div]
                routsum -= sir[0]; goutsum -= sir[1]; boutsum -= sir[2]
                if (y == 0) vmin[x] = minOf(x + r1, wm)
                val p = pix[yw + vmin[x]]; sir[0] = (p shr 16) and 0xff; sir[1] =
                    (p shr 8) and 0xff; sir[2] = p and 0xff
                rinsum += sir[0]; ginsum += sir[1]; binsum += sir[2]
                rsum += rinsum; gsum += ginsum; bsum += binsum
                stackpointer = (stackpointer + 1) % div; sir = stack[stackpointer]
                routsum += sir[0]; goutsum += sir[1]; boutsum += sir[2]
                rinsum -= sir[0]; ginsum -= sir[1]; binsum -= sir[2]; yi++
            }
            yw += w
        }
        for (x in 0 until w) {
            var rsum = 0
            var gsum = 0
            var bsum = 0
            var rinsum = 0
            var ginsum = 0
            var binsum = 0
            var routsum = 0
            var goutsum = 0
            var boutsum = 0
            var yp = -radius * w
            for (i in -radius..radius) {
                yi = maxOf(0, yp) + x
                val sir = stack[i + radius]
                sir[0] = r[yi]; sir[1] = g[yi]; sir[2] = b[yi]
                val rbs =
                    r1 - safabs(i); rsum += r[yi] * rbs; gsum += g[yi] * rbs; bsum += b[yi] * rbs
                if (i > 0) {
                    rinsum += sir[0]; ginsum += sir[1]; binsum += sir[2]
                } else {
                    routsum += sir[0]; goutsum += sir[1]; boutsum += sir[2]
                }
                if (i < hm) yp += w
            }
            yi = x
            var stackpointer = radius
            for (y in 0 until h) {
                // Preserve alpha channel: ( 0xff000000.toInt() )
                pix[yi] =
                    (pix[yi] and 0xff000000.toInt()) or (dv[rsum] shl 16) or (dv[gsum] shl 8) or dv[bsum]
                rsum -= routsum; gsum -= goutsum; bsum -= boutsum
                var sir = stack[(stackpointer - radius + div) % div]
                routsum -= sir[0]; goutsum -= sir[1]; boutsum -= sir[2]
                if (x == 0) vmin[y] = minOf(y + r1, hm) * w
                val p = x + vmin[y]; sir[0] = r[p]; sir[1] = g[p]; sir[2] = b[p]
                rinsum += sir[0]; ginsum += sir[1]; binsum += sir[2]
                rsum += rinsum; gsum += ginsum; bsum += binsum
                stackpointer = (stackpointer + 1) % div; sir = stack[stackpointer]
                routsum += sir[0]; goutsum += sir[1]; boutsum += sir[2]
                rinsum -= sir[0]; ginsum -= sir[1]; binsum -= sir[2]; yi += w
            }
        }
        result.setPixels(pix, 0, w, 0, 0, w, h)
        return result
    }
}
