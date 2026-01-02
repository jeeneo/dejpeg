package com.je.dejpeg.compose.utils.helpers

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.provider.OpenableColumns
import androidx.exifinterface.media.ExifInterface
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale

object ImageLoadingHelper {
    private const val THUMBNAIL_SIZE = 256
    
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

    private fun applyExifOrientation(bitmap: Bitmap, exif: ExifInterface): Bitmap {
        return when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> rotateBitmap(bitmap, 90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> rotateBitmap(bitmap, 180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> rotateBitmap(bitmap, 270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> flipBitmap(bitmap, horizontal = true, vertical = false)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> flipBitmap(bitmap, horizontal = false, vertical = true)
            ExifInterface.ORIENTATION_TRANSPOSE -> flipBitmap(rotateBitmap(bitmap, 90f), horizontal = true, vertical = false)
            ExifInterface.ORIENTATION_TRANSVERSE -> flipBitmap(rotateBitmap(bitmap, 270f), horizontal = true, vertical = false)
            else -> bitmap
        }
    }

    private fun rotateBitmap(bitmap: Bitmap, degrees: Float): Bitmap {
        val matrix = Matrix().apply { postRotate(degrees) }
        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
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
        val flipped = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (flipped != bitmap) bitmap.recycle()
        return flipped
    }

    fun generateThumbnail(bitmap: Bitmap, size: Int = THUMBNAIL_SIZE, blurRadius: Int = 15): Bitmap {
        val cropSize = minOf(bitmap.width, bitmap.height)
        val x = (bitmap.width - cropSize) / 2
        val y = (bitmap.height - cropSize) / 2
        val croppedBitmap =
            createBitmap(cropSize, cropSize, bitmap.config ?: Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(croppedBitmap)
        canvas.drawBitmap(bitmap, -x.toFloat(), -y.toFloat(), null)
        val resizedBitmap = croppedBitmap.scale(size, size)
        croppedBitmap.recycle()
        return if (blurRadius > 0) stackBlur(resizedBitmap, blurRadius) else resizedBitmap
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

    /**
     * Lazy: stackBlur was created by Claude Sonnet 4 and not thoroughly tested.
     * Apply Stack Blur algorithm to a bitmap
     * 
     * @param sentBitmap Source bitmap
     * @param radius Blur radius (1-25)
     * @return Blurred bitmap
     */
    fun stackBlur(sentBitmap: Bitmap, radius: Int): Bitmap {
        val bitmap = sentBitmap.copy(sentBitmap.config ?: Bitmap.Config.ARGB_8888, true)
        
        if (radius < 1) return bitmap
        
        val w = bitmap.width
        val h = bitmap.height
        val pix = IntArray(w * h)
        bitmap.getPixels(pix, 0, w, 0, 0, w, h)
        
        val wm = w - 1
        val hm = h - 1
        val wh = w * h
        val div = radius + radius + 1
        
        val r = IntArray(wh)
        val g = IntArray(wh)
        val b = IntArray(wh)
        
        val vmin = IntArray(maxOf(w, h))
        
        var divsum = (div + 1) shr 1
        divsum *= divsum
        val dv = IntArray(256 * divsum)
        for (i in 0 until 256 * divsum) {
            dv[i] = i / divsum
        }
        
        var yw = 0
        var yi = 0
        
        val stack = Array(div) { IntArray(3) }
        val r1 = radius + 1
        
        for (y in 0 until h) {
            var rsum = 0
            var gsum = 0
            var bsum = 0
            var routsum = 0
            var goutsum = 0
            var boutsum = 0
            var rinsum = 0
            var ginsum = 0
            var binsum = 0
            
            for (i in -radius..radius) {
                val p = pix[yi + minOf(wm, maxOf(i, 0))]
                val sir = stack[i + radius]
                sir[0] = (p and 0xff0000) shr 16
                sir[1] = (p and 0x00ff00) shr 8
                sir[2] = p and 0x0000ff
                
                val rbs = r1 - kotlin.math.abs(i)
                rsum += sir[0] * rbs
                gsum += sir[1] * rbs
                bsum += sir[2] * rbs
                
                if (i > 0) {
                    rinsum += sir[0]
                    ginsum += sir[1]
                    binsum += sir[2]
                } else {
                    routsum += sir[0]
                    goutsum += sir[1]
                    boutsum += sir[2]
                }
            }
            
            var stackpointer = radius
            
            for (x in 0 until w) {
                r[yi] = dv[rsum]
                g[yi] = dv[gsum]
                b[yi] = dv[bsum]
                
                rsum -= routsum
                gsum -= goutsum
                bsum -= boutsum
                
                val stackstart = stackpointer - radius + div
                var sir = stack[stackstart % div]
                
                routsum -= sir[0]
                goutsum -= sir[1]
                boutsum -= sir[2]
                
                if (y == 0) {
                    vmin[x] = minOf(x + radius + 1, wm)
                }
                val p = pix[yw + vmin[x]]
                
                sir[0] = (p and 0xff0000) shr 16
                sir[1] = (p and 0x00ff00) shr 8
                sir[2] = p and 0x0000ff
                
                rinsum += sir[0]
                ginsum += sir[1]
                binsum += sir[2]
                
                rsum += rinsum
                gsum += ginsum
                bsum += binsum
                
                stackpointer = (stackpointer + 1) % div
                sir = stack[stackpointer % div]
                
                routsum += sir[0]
                goutsum += sir[1]
                boutsum += sir[2]
                
                rinsum -= sir[0]
                ginsum -= sir[1]
                binsum -= sir[2]
                
                yi++
            }
            yw += w
        }
        
        for (x in 0 until w) {
            var rsum = 0
            var gsum = 0
            var bsum = 0
            var routsum = 0
            var goutsum = 0
            var boutsum = 0
            var rinsum = 0
            var ginsum = 0
            var binsum = 0
            
            var yp = -radius * w
            for (i in -radius..radius) {
                yi = maxOf(0, yp) + x
                
                val sir = stack[i + radius]
                
                sir[0] = r[yi]
                sir[1] = g[yi]
                sir[2] = b[yi]
                
                val rbs = r1 - kotlin.math.abs(i)
                
                rsum += r[yi] * rbs
                gsum += g[yi] * rbs
                bsum += b[yi] * rbs
                
                if (i > 0) {
                    rinsum += sir[0]
                    ginsum += sir[1]
                    binsum += sir[2]
                } else {
                    routsum += sir[0]
                    goutsum += sir[1]
                    boutsum += sir[2]
                }
                
                if (i < hm) {
                    yp += w
                }
            }
            
            yi = x
            var stackpointer = radius
            
            for (y in 0 until h) {
                pix[yi] = (0xff000000.toInt() or (dv[rsum] shl 16) or (dv[gsum] shl 8) or dv[bsum])
                
                rsum -= routsum
                gsum -= goutsum
                bsum -= boutsum
                
                val stackstart = stackpointer - radius + div
                var sir = stack[stackstart % div]
                
                routsum -= sir[0]
                goutsum -= sir[1]
                boutsum -= sir[2]
                
                if (x == 0) {
                    vmin[y] = minOf(y + r1, hm) * w
                }
                val p = x + vmin[y]
                
                sir[0] = r[p]
                sir[1] = g[p]
                sir[2] = b[p]
                
                rinsum += sir[0]
                ginsum += sir[1]
                binsum += sir[2]
                
                rsum += rinsum
                gsum += ginsum
                bsum += binsum
                
                stackpointer = (stackpointer + 1) % div
                sir = stack[stackpointer]
                
                routsum += sir[0]
                goutsum += sir[1]
                boutsum += sir[2]
                
                rinsum -= sir[0]
                ginsum -= sir[1]
                binsum -= sir[2]
                
                yi += w
            }
        }
        bitmap.setPixels(pix, 0, w, 0, 0, w, h)
        return bitmap
    }
}
