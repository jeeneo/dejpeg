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

package com.je.dejpeg.utils.helpers

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
import android.provider.MediaStore
import androidx.activity.result.ActivityResultLauncher
import androidx.core.content.FileProvider
import java.io.IOException
import android.content.ClipData
import android.content.Intent
import android.media.MediaScannerConnection
import android.os.Environment
import android.widget.Toast
import com.je.dejpeg.R
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.je.dejpeg.utils.CacheManager

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

class ImagePickerHelper(
    private val context: Context, private var launcher: ActivityResultLauncher<Intent>? = null
) {
    private var currentPhotoUri: Uri? = null

    fun setLauncher(launcher: ActivityResultLauncher<Intent>) {
        this.launcher = launcher
    }

    fun launchGalleryPicker() {
        launch(
            Intent(Intent.ACTION_PICK).apply {
                setDataAndType(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, "image/*")
                putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            })
    }

    fun launchInternalPhotoPicker() {
        launch(
            Intent(Intent.ACTION_GET_CONTENT).apply {
                type = "image/*"
                putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            })
    }

    fun launchDocumentsPicker() {
        launch(
            Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                type = "image/*"
                putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            })
    }

    fun launchCamera(): Result<Uri> {
        return try {
            val tempFile = File(
                context.cacheDir, "temp_camera_${System.currentTimeMillis()}.jpg"
            )
            val photoUri = FileProvider.getUriForFile(
                context, "${context.packageName}.provider", tempFile
            )
            currentPhotoUri = photoUri

            launch(
                Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
                    putExtra(MediaStore.EXTRA_OUTPUT, photoUri)
                })
            Result.success(photoUri)
        } catch (e: IOException) {
            Result.failure(e)
        }
    }

    fun getCameraPhotoUri(): Uri? = currentPhotoUri

    fun clearCameraPhotoUri() {
        currentPhotoUri?.path?.let { path ->
            File(path).takeIf { it.exists() }?.delete()
        }
        currentPhotoUri = null
    }

    private fun launch(intent: Intent) {
        launcher?.launch(intent)
    }
}

object ImageActions {
    fun checkFileExists(filename: String): Boolean {
        val fileNameRaw = filename.takeIf { it.isNotBlank() } ?: return false
        val fileName = fileNameRaw.substringBeforeLast('.', fileNameRaw)
        val picturesDir =
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
        val outputFile = File(picturesDir, "$fileName.png")
        return outputFile.exists()
    }

    fun saveImage(
        context: Context,
        bitmap: Bitmap,
        filename: String? = null,
        imageId: String? = null,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        @OptIn(DelicateCoroutinesApi::class) GlobalScope.launch(Dispatchers.IO) {
            try {
                val fileNameRaw = filename?.takeIf { it.isNotBlank() } ?: "DeJPEG_${
                    SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(
                        Date()
                    )
                }"
                val fileName = fileNameRaw.substringBeforeLast('.', fileNameRaw)
                val picturesDir =
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                val outputFile = File(picturesDir, "$fileName.png")
                if (outputFile.exists()) outputFile.delete()
                FileOutputStream(outputFile).use {
                    bitmap.compress(
                        Bitmap.CompressFormat.PNG, 100, it
                    )
                }
                if (imageId != null) {
                    CacheManager.deleteRecoveryPair(
                        context, imageId, deleteProcessed = true, deleteUnprocessed = false
                    )
                }
                withContext(Dispatchers.Main) {
                    MediaScannerConnection.scanFile(
                        context, arrayOf(outputFile.toString()), null, null
                    )
                    Toast.makeText(
                        context,
                        context.getString(R.string.image_saved_to_gallery),
                        Toast.LENGTH_SHORT
                    ).show()
                    onSuccess()
                }
            } catch (e: Exception) {
                val errorMsg = context.getString(R.string.error_saving_image, e.message)
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, errorMsg, Toast.LENGTH_SHORT).show()
                    onError(errorMsg)
                }
            }
        }
    }

    fun shareImage(context: Context, bitmap: Bitmap, onError: (String) -> Unit = {}) {
        try {
            val cachePath = File(context.cacheDir, "shared_image.png")
            if (cachePath.exists()) cachePath.delete()
            FileOutputStream(cachePath).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            val contentUri =
                FileProvider.getUriForFile(context, "${context.packageName}.provider", cachePath)
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, contentUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                clipData = ClipData.newRawUri(null, contentUri)
            }
            context.startActivity(
                Intent.createChooser(
                    shareIntent, context.getString(R.string.share_image)
                )
            )
        } catch (e: Exception) {
            val errorMsg = context.getString(R.string.error_sharing_image, e.message)
            Toast.makeText(context, errorMsg, Toast.LENGTH_SHORT).show()
            onError(errorMsg)
        }
    }

    fun saveAllImages(
        context: Context,
        images: List<Pair<String, Bitmap>>,
        baseFilename: String? = null,
        onProgress: (Int, Int) -> Unit = { _, _ -> },
        onComplete: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        @OptIn(DelicateCoroutinesApi::class) GlobalScope.launch(Dispatchers.IO) {
            try {
                val usedFilenames = mutableSetOf<String>()
                images.forEachIndexed { index, (originalFilename, bitmap) ->
                    val preferredFilenameRaw =
                        if (images.size > 1) originalFilename.ifBlank { "${baseFilename ?: "DeJPEG"}_${index + 1}" } else originalFilename.ifBlank {
                            baseFilename ?: "DeJPEG"
                        }
                    val preferredFilename =
                        preferredFilenameRaw.substringBeforeLast('.', preferredFilenameRaw)
                    val fileName = generateUniqueFilename(preferredFilename, usedFilenames)
                    usedFilenames.add(fileName)
                    val outputFile = saveBitmapToPictures(fileName, bitmap)
                    MediaScannerConnection.scanFile(
                        context, arrayOf(outputFile.toString()), null, null
                    )
                    withContext(Dispatchers.Main) {
                        onProgress(index + 1, images.size)
                    }
                }
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        context, context.resources.getQuantityString(
                            R.plurals.all_images_saved_to_gallery, images.size, images.size
                        ), Toast.LENGTH_SHORT
                    ).show()
                    onComplete()
                }
            } catch (e: Exception) {
                val errorMsg = context.getString(R.string.error_saving_images, e.message)
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, errorMsg, Toast.LENGTH_SHORT).show()
                    onError(errorMsg)
                }
            }
        }
    }

    private fun saveBitmapToPictures(baseName: String, bitmap: Bitmap): File {
        val outputFile = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
            "$baseName.png"
        )
        FileOutputStream(outputFile).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return outputFile
    }

    private fun generateUniqueFilename(base: String, existing: Set<String>): String {
        if (!existing.contains(base)) return base
        var counter = 1
        var newName = "${base}_$counter"
        while (existing.contains(newName)) {
            counter++; newName = "${base}_$counter"
        }
        return newName
    }
}
