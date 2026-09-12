/*
 * SPDX-FileCopyrightText: 2025 - 2026 dryerlint <https://codeberg.org/dryerlint>
 * SPDX-License-Identifier: GNU Affero General Public License v3.0 or later
 */

@file:Suppress("SpellCheckingInspection")

package com.je.dejpeg.utils

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

class ExifOrientationTest {

    @Test
    fun `big endian exif returns stored orientation for all values`() {
        for (orientation in 1..8) {
            val jpeg = jpegWithOrientation(bigEndian = true, orientation = orientation)
            assertEquals(orientation, readOrientation(jpeg))
        }
    }

    @Test
    fun `little endian exif returns stored orientation for all values`() {
        for (orientation in 1..8) {
            val jpeg = jpegWithOrientation(bigEndian = false, orientation = orientation)
            assertEquals(orientation, readOrientation(jpeg))
        }
    }

    @Test
    fun `no exif segment yields normal orientation`() {
        val jpeg = ByteArrayOutputStream().apply {
            writeShort(0xFFD8)
            writeShort(0xFFDA)
            writeShort(2)
            write(byteArrayOf(1, 2, 3, 4))
        }.toByteArray()
        assertEquals(ExifOrientation.ORIENTATION_NORMAL, readOrientation(jpeg))
    }

    @Test
    fun `not a jpeg yields normal orientation`() {
        assertEquals(ExifOrientation.ORIENTATION_NORMAL, readOrientation("plain text".toByteArray()))
    }

    @Test
    fun `empty and tiny inputs yield normal orientation`() {
        assertEquals(ExifOrientation.ORIENTATION_NORMAL, readOrientation(ByteArray(0)))
        assertEquals(ExifOrientation.ORIENTATION_NORMAL, readOrientation(ByteArray(4) { 0xFF.toByte() }))
        assertEquals(ExifOrientation.ORIENTATION_NORMAL, readOrientation(byteArrayOf(0xFF.toByte(), 0xD8.toByte())))
    }

    @Test
    fun `orientation absent from ifd yields normal orientation`() {
        val jpeg = jpegWithEntries(
            bigEndian = true,
            entries = listOf(ifdEntry(0x0100, 3, 1, 640, bigEndian = true))
        )
        assertEquals(ExifOrientation.ORIENTATION_NORMAL, readOrientation(jpeg))
    }

    @Test
    fun `xmp segment before exif is skipped`() {
        val xmp = ByteArrayOutputStream().apply {
            write("http://ns.adobe.com/xap/1.0/".toByteArray(Charsets.US_ASCII))
            write(ByteArray(16))
        }.toByteArray()
        val exifJpeg = jpegWithOrientation(bigEndian = false, orientation = 6)
        val jpeg = ByteArrayOutputStream().apply {
            writeShort(0xFFD8)
            writeShort(0xFFE1)
            writeShort(xmp.size + 2)
            write(xmp)
            write(exifJpeg, 2, exifJpeg.size - 2)
        }.toByteArray()
        assertEquals(6, readOrientation(jpeg))
    }

    @Test
    fun `scanning stops at start of scan`() {
        val trailingExif = jpegWithOrientation(bigEndian = false, orientation = 8)
        val jpeg = ByteArrayOutputStream().apply {
            writeShort(0xFFD8)
            writeShort(0xFFE1) // non-exif segment before scan
            val filler = byteArrayOf(65, 66, 67, 68)
            writeShort(filler.size + 2)
            write(filler)
            writeShort(0xFFDA) // SOS - metadata scanning must stop here
            writeShort(2)
            write(trailingExif, 2, trailingExif.size - 2) // exif after SOS must be ignored
        }.toByteArray()
        assertEquals(ExifOrientation.ORIENTATION_NORMAL, readOrientation(jpeg))
    }

    @Test
    fun `out of range orientation value is sanitized to normal`() {
        val jpeg = jpegWithEntries(
            bigEndian = true,
            entries = listOf(ifdEntry(0x0112, 3, 1, 9, bigEndian = true))
        )
        assertEquals(ExifOrientation.ORIENTATION_NORMAL, readOrientation(jpeg))
    }

    @Test
    fun `orientation tag not first in ifd is still found`() {
        val jpeg = jpegWithEntries(
            bigEndian = true,
            entries = listOf(
                ifdEntry(0x0100, 3, 1, 640, bigEndian = true),
                ifdEntry(0x0101, 3, 1, 480, bigEndian = true),
                ifdEntry(0x0112, 3, 1, 3, bigEndian = true),
                ifdEntry(0x0102, 3, 1, 8, bigEndian = true)
            )
        )
        assertEquals(3, readOrientation(jpeg))
    }

    @Test
    fun `orientation in little endian with multiple entries is found`() {
        val jpeg = jpegWithEntries(
            bigEndian = false,
            entries = listOf(
                ifdEntry(0x0100, 3, 1, 320, bigEndian = false),
                ifdEntry(0x0112, 3, 1, 5, bigEndian = false)
            )
        )
        assertEquals(5, readOrientation(jpeg))
    }

    @Test
    fun `truncation only matters while the app1 body is incomplete`() {
        val exif = buildExifSegment(
            bigEndian = false,
            entries = listOf(ifdEntry(0x0112, 3, 1, 6, bigEndian = false))
        )
        val full = ByteArrayOutputStream().apply {
            writeShort(0xFFD8)
            writeShort(0xFFE1)
            writeShort(exif.size + 2)
            write(exif)
            writeShort(0xFFDA)
            writeShort(2)
        }.toByteArray()
        val app1CompletesAt = 6 + exif.size
        for (cut in 0 until app1CompletesAt) {
            val truncated = full.copyOf(cut)
            assertEquals("cut=$cut", ExifOrientation.ORIENTATION_NORMAL, readOrientation(truncated))
        }
        for (cut in app1CompletesAt until full.size) {
            val truncated = full.copyOf(cut)
            assertEquals("cut=$cut", 6, readOrientation(truncated))
        }
    }

    @Test
    fun `malformed marker stream yields normal orientation`() {
        val inner = jpegWithOrientation(bigEndian = true, orientation = 2)
        val jpeg = ByteArrayOutputStream().apply {
            writeShort(0xFFD8)
            write(0x42) // not a marker prefix -> drift off sync
            writeShort(inner.size - 2)
            write(inner, 2, inner.size - 2)
        }.toByteArray()
        assertEquals(ExifOrientation.ORIENTATION_NORMAL, readOrientation(jpeg))
    }

    @Test
    fun `file based lookup works and missing file yields normal`() {
        val tmp = File.createTempFile("exif_test", ".jpg")
        tmp.deleteOnExit()
        FileOutputStream(tmp).use { it.write(jpegWithOrientation(bigEndian = true, orientation = 3)) }
        assertEquals(3, ExifOrientation.getOrientation(tmp))

        val missing = File(tmp.parentFile, "does_not_exist_${System.nanoTime()}.jpg")
        missing.deleteOnExit()
        assertEquals(ExifOrientation.ORIENTATION_NORMAL, ExifOrientation.getOrientation(missing))
    }

    @Test
    fun `exif app1 with bad tiff header yields normal`() {
        val app1 = "Exif\u0000\u0000NN\u0000\u0000\u0000\u0000".toByteArray(Charsets.US_ASCII)
        val jpeg = ByteArrayOutputStream().apply {
            writeShort(0xFFD8)
            writeShort(0xFFE1)
            writeShort(app1.size + 2)
            write(app1)
            writeShort(0xFFDA)
            writeShort(2)
        }.toByteArray()
        assertEquals(ExifOrientation.ORIENTATION_NORMAL, readOrientation(jpeg))
    }

    // --- helpers ------------------------------------------------------------

    private fun readOrientation(jpeg: ByteArray): Int =
        ExifOrientation.getOrientation(ByteArrayInputStream(jpeg))

    private fun jpegWithOrientation(bigEndian: Boolean, orientation: Int): ByteArray =
        jpegWithEntries(
            bigEndian = bigEndian,
            entries = listOf(ifdEntry(0x0112, 3, 1, orientation, bigEndian = bigEndian))
        )

    private fun jpegWithEntries(bigEndian: Boolean, entries: List<ByteArray>): ByteArray {
        val exif = buildExifSegment(bigEndian, entries)
        return ByteArrayOutputStream().apply {
            writeShort(0xFFD8)
            writeShort(0xFFE1)
            writeShort(exif.size + 2)
            write(exif)
            writeShort(0xFFDA)
            writeShort(2)
        }.toByteArray()
    }

    private fun buildExifSegment(bigEndian: Boolean, entries: List<ByteArray>): ByteArray {
        val tiff = ByteArrayOutputStream()
        tiff.write(if (bigEndian) 'M'.code else 'I'.code)
        tiff.write(if (bigEndian) 'M'.code else 'I'.code)
        writeU16(tiff, 0x002A, bigEndian)
        writeU32(tiff, 8, bigEndian)
        writeU16(tiff, entries.size, bigEndian)
        entries.forEach { tiff.write(it) }
        return ByteArrayOutputStream().apply {
            write("Exif".toByteArray(Charsets.US_ASCII))
            write(0); write(0)
            write(tiff.toByteArray())
        }.toByteArray()
    }

    /** Builds one 12-byte IFD entry: tag(2) type(2) count(4) value(4). */
    private fun ifdEntry(tag: Int, type: Int, count: Int, value: Int, bigEndian: Boolean): ByteArray {
        // A SHORT value sits in the leading 2 bytes of the 4-byte value field.
        val encodedValue = if (bigEndian) value shl 16 else value
        val out = ByteArrayOutputStream()
        writeU16(out, tag, bigEndian)
        writeU16(out, type, bigEndian)
        writeU32(out, count, bigEndian)
        writeU32(out, encodedValue, bigEndian)
        return out.toByteArray()
    }

    private fun ByteArrayOutputStream.writeShort(value: Int) {
        write((value ushr 8) and 0xFF)
        write(value and 0xFF)
    }

    private fun writeU16(out: ByteArrayOutputStream, value: Int, bigEndian: Boolean) {
        val hi = (value ushr 8) and 0xFF
        val lo = value and 0xFF
        if (bigEndian) { out.write(hi); out.write(lo) } else { out.write(lo); out.write(hi) }
    }

    private fun writeU32(out: ByteArrayOutputStream, value: Int, bigEndian: Boolean) {
        val b0 = (value ushr 24) and 0xFF
        val b1 = (value ushr 16) and 0xFF
        val b2 = (value ushr 8) and 0xFF
        val b3 = value and 0xFF
        if (bigEndian) {
            out.write(b0); out.write(b1); out.write(b2); out.write(b3)
        } else {
            out.write(b3); out.write(b2); out.write(b1); out.write(b0)
        }
    }
}