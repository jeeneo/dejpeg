/*
 * SPDX-FileCopyrightText: 2026 dryerlint <https://codeberg.org/dryerlint>
 * SPDX-License-Identifier: GNU Affero General Public License v3.0 or later
 */

package com.je.dejpeg.utils

import android.graphics.Bitmap
import android.graphics.Matrix
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream

object ExifOrientation {
    const val ORIENTATION_NORMAL = 1
    const val ORIENTATION_FLIP_HORIZONTAL = 2
    const val ORIENTATION_ROTATE_180 = 3
    const val ORIENTATION_FLIP_VERTICAL = 4
    const val ORIENTATION_TRANSPOSE = 5
    const val ORIENTATION_ROTATE_90 = 6
    const val ORIENTATION_TRANSVERSE = 7
    const val ORIENTATION_ROTATE_270 = 8
    private const val MARKER_SOI = 0xFFD8
    private const val MARKER_APP1 = 0xFFE1
    private const val EXIF_HEADER = "Exif"
    private const val TAG_ORIENTATION = 0x0112
    fun getOrientation(file: File): Int = try {
        BufferedInputStream(FileInputStream(file)).use { getOrientation(it) }
    } catch (_: Exception) {
        ORIENTATION_NORMAL
    }

    fun getOrientation(inputStream: InputStream): Int {
        try {
            val stream = if (inputStream.markSupported()) inputStream
            else BufferedInputStream(inputStream)
            if (readUShort(stream) != MARKER_SOI) return ORIENTATION_NORMAL
            while (true) {
                val marker = readUShort(stream)
                if (marker == -1) return ORIENTATION_NORMAL
                if ((marker and 0xFF00) != 0xFF00) return ORIENTATION_NORMAL

                val length = readUShort(stream)
                if (length == -1) return ORIENTATION_NORMAL

                if (marker == MARKER_APP1) {
                    val segment = readBytes(stream, length - 2) ?: return ORIENTATION_NORMAL
                    val orientation = parseApp1(segment)
                    if (orientation != null) return orientation
                } else if (marker == 0xFFDA) {
                    return ORIENTATION_NORMAL
                } else {
                    skipBytes(stream, length - 2)
                }
            }
        } catch (_: Exception) {
            return ORIENTATION_NORMAL
        }
    }

    private fun parseApp1(segment: ByteArray): Int? {
        if (segment.size < 8) return null
        val header = String(segment, 0, 4, Charsets.US_ASCII)
        if (header != EXIF_HEADER || segment[4].toInt() != 0 || segment[5].toInt() != 0) return null
        val tiffStart = 6
        if (segment.size < tiffStart + 8) return null
        val bigEndian = when {
            segment[tiffStart] == 'M'.code.toByte() && segment[tiffStart + 1] == 'M'.code.toByte() -> true
            segment[tiffStart] == 'I'.code.toByte() && segment[tiffStart + 1] == 'I'.code.toByte() -> false
            else -> return null
        }

        fun u16(offset: Int): Int {
            val a = segment[offset].toInt() and 0xFF
            val b = segment[offset + 1].toInt() and 0xFF
            return if (bigEndian) (a shl 8) or b else (b shl 8) or a
        }

        fun u32(offset: Int): Int {
            val a = segment[offset].toInt() and 0xFF
            val b = segment[offset + 1].toInt() and 0xFF
            val c = segment[offset + 2].toInt() and 0xFF
            val d = segment[offset + 3].toInt() and 0xFF
            return if (bigEndian) (a shl 24) or (b shl 16) or (c shl 8) or d
            else (d shl 24) or (c shl 16) or (b shl 8) or a
        }

        val magic = u16(tiffStart + 2)
        if (magic != 0x002A) return null
        val ifd0Offset = tiffStart + u32(tiffStart + 4)
        if (ifd0Offset < tiffStart + 8 || ifd0Offset + 2 > segment.size) return null
        val entryCount = u16(ifd0Offset)
        val entriesStart = ifd0Offset + 2
        for (i in 0 until entryCount) {
            val entryOffset = entriesStart + i * 12
            if (entryOffset + 12 > segment.size) break
            val tag = u16(entryOffset)
            if (tag == TAG_ORIENTATION) {
                val valueOffset = entryOffset + 8
                val value = u16(valueOffset)
                return if (value in 1..8) value else ORIENTATION_NORMAL
            }
        }
        return null
    }

    private fun readUShort(stream: InputStream): Int {
        val hi = stream.read()
        val lo = stream.read()
        if (hi == -1 || lo == -1) return -1
        return (hi shl 8) or lo
    }

    private fun readBytes(stream: InputStream, count: Int): ByteArray? {
        if (count < 0) return null
        val buffer = ByteArray(count)
        var read = 0
        while (read < count) {
            val n = stream.read(buffer, read, count - read)
            if (n == -1) return null
            read += n
        }
        return buffer
    }

    private fun skipBytes(stream: InputStream, count: Int) {
        var remaining = count.toLong()
        while (remaining > 0) {
            val skipped = stream.skip(remaining)
            if (skipped <= 0) {
                // Some streams don't support skip reliably; fall back to reading.
                if (stream.read() == -1) return
                remaining -= 1
            } else {
                remaining -= skipped
            }
        }
    }

    fun applyOrientation(bitmap: Bitmap, orientation: Int): Bitmap = when (orientation) {
        ORIENTATION_ROTATE_90 -> rotate(bitmap, 90f)
        ORIENTATION_ROTATE_180 -> rotate(bitmap, 180f)
        ORIENTATION_ROTATE_270 -> rotate(bitmap, 270f)
        ORIENTATION_FLIP_HORIZONTAL -> flip(bitmap, horizontal = true, vertical = false)
        ORIENTATION_FLIP_VERTICAL -> flip(bitmap, horizontal = false, vertical = true)
        ORIENTATION_TRANSPOSE -> flip(rotate(bitmap, 90f), horizontal = true, vertical = false)
        ORIENTATION_TRANSVERSE -> flip(rotate(bitmap, 270f), horizontal = true, vertical = false)
        else -> bitmap
    }

    private fun rotate(bitmap: Bitmap, degrees: Float): Bitmap {
        val matrix = Matrix().apply { postRotate(degrees) }
        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, false)
        if (rotated != bitmap) bitmap.recycle()
        return rotated
    }

    private fun flip(bitmap: Bitmap, horizontal: Boolean, vertical: Boolean): Bitmap {
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
}
