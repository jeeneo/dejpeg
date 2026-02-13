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
*
*/

/*
* If you use this code in your own project, please give credit
*/

package com.je.dejpeg.compose.utils.brisque

import android.content.Context
import android.util.Log
import com.je.dejpeg.compose.utils.HashUtils
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

data class BrisqueSVMModel(
    val supportVectors: FloatArray,
    val alphas: FloatArray,
    val rho: Float,
    val gamma: Float,
    val rangeMin: FloatArray,
    val rangeMax: FloatArray
) {
    companion object {
        const val NUM_FEATURES = 36
        const val NUM_SUPPORT_VECTORS = 774
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as BrisqueSVMModel
        if (!supportVectors.contentEquals(other.supportVectors)) return false
        if (!alphas.contentEquals(other.alphas)) return false
        if (rho != other.rho) return false
        if (gamma != other.gamma) return false
        if (!rangeMin.contentEquals(other.rangeMin)) return false
        if (!rangeMax.contentEquals(other.rangeMax)) return false
        return true
    }

    override fun hashCode(): Int {
        var result = supportVectors.contentHashCode()
        result = 31 * result + alphas.contentHashCode()
        result = 31 * result + rho.hashCode()
        result = 31 * result + gamma.hashCode()
        result = 31 * result + rangeMin.contentHashCode()
        result = 31 * result + rangeMax.contentHashCode()
        return result
    }
}

object BrisqueModelLoader {
    private const val TAG = "BrisqueModelLoader"
    private const val MAGIC = "BRSQ"
    private const val BRISQUE_MDL_HASH = "fe4a6bcee5aa2357e34ce845133cfc2d707dcc2613627c38d4ef805bf81fc59b"

    @Volatile
    private var cachedModel: BrisqueSVMModel? = null

    fun loadFromAssets(context: Context): BrisqueSVMModel? {
        cachedModel?.let { return it }
        return synchronized(this) {
            cachedModel?.let { return it }
            try {
                val startTime = System.currentTimeMillis()
                val inputFile = File(context.cacheDir, "brisque_model.bin")
                if (!inputFile.exists() || inputFile.length() == 0L) {
                    context.assets.open("brisque_model.bin").use { input ->
                        inputFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                }
                val realHash = HashUtils.computeSHA256(inputFile)
                if (realHash != BRISQUE_MDL_HASH) {
                    Log.e(TAG, "BRISQUE model SHA256 verification failed. Expected: $BRISQUE_MDL_HASH, Actual: $realHash")
                    inputFile.delete()
                    return null
                }
                val model = loadFromBinary(inputFile)
                val elapsed = System.currentTimeMillis() - startTime
                if (model != null) {
                    cachedModel = model
                    Log.i(TAG, "BRISQUE model loaded in ${elapsed}ms")
                }
                model
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load BRISQUE model: ${e.message}")
                null
            }
        }
    }

    private fun loadFromBinary(file: File): BrisqueSVMModel? {
        return try {
            val bytes = file.readBytes()
            val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            val magic = ByteArray(4)
            buffer.get(magic)
            if (String(magic) != MAGIC) {
                Log.e(TAG, "Invalid magic number")
                return null
            }
            buffer.int // version
            val numFeatures = buffer.int
            buffer.int // numSupportVectors
            val gamma = buffer.float
            val rho = buffer.float
            val numSvValues = buffer.int
            val supportVectors = FloatArray(numSvValues)
            for (i in 0 until numSvValues) {
                supportVectors[i] = buffer.float
            }
            val numAlphas = buffer.int
            val alphas = FloatArray(numAlphas)
            for (i in 0 until numAlphas) {
                alphas[i] = buffer.float
            }
            val rangeMin = FloatArray(numFeatures)
            val rangeMax = FloatArray(numFeatures)
            for (i in 0 until numFeatures) {
                rangeMin[i] = buffer.float
            }
            for (i in 0 until numFeatures) {
                rangeMax[i] = buffer.float
            }
            BrisqueSVMModel(
                supportVectors = supportVectors,
                alphas = alphas,
                rho = rho,
                gamma = gamma,
                rangeMin = rangeMin,
                rangeMax = rangeMax
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error loading binary model: ${e.message}")
            null
        }
    }

    fun clearCache() {
        synchronized(this) {
            cachedModel = null
        }
    }
}
