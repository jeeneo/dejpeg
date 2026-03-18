/*
 * SPDX-FileCopyrightText: 2025 - 2026 dryerlint <https://codeberg.org/dryerlint>
 * SPDX-License-Identifier: GNU Affero General Public License v3.0 or later
 */

package com.je.dejpeg.utils

import java.io.File
import java.security.MessageDigest

object HashUtils {

    fun computeSHA256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { stream ->
            val buffer = ByteArray(8192)
            var read: Int
            while (stream.read(buffer).also { read = it } != -1) {
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

//    fun computeSHA256(fileUri: Uri, context: Context): String {
//        val inputStream = context.contentResolver.openInputStream(fileUri)
//            ?: throw Exception("Could not open file")
//        return inputStream.use { stream ->
//            val digest = MessageDigest.getInstance("SHA-256")
//            val buffer = ByteArray(8192)
//            var read: Int
//            while (stream.read(buffer).also { read = it } != -1) {
//                digest.update(buffer, 0, read)
//            }
//            digest.digest().joinToString("") { "%02x".format(it) }
//        }
//    }
}
