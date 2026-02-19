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

import android.content.Context
import android.util.Log
import java.lang.ref.WeakReference

class BRISQUEAssessor {
    companion object {
        private const val TAG = "BRISQUEAssessor"
        private var contextRef: WeakReference<Context>? = null
        fun initialize(context: Context) {
            contextRef = WeakReference(context.applicationContext)
        }
        private fun getModel(): BrisqueSVMModel? {
            val ctx = contextRef?.get() ?: return null
            return BrisqueModelLoader.loadFromAssets(ctx)
        }
    }

    fun assessImageQualityFromBitmap(bitmap: android.graphics.Bitmap): Float {
        return try {
            val model = getModel()
            if (model == null) {
                Log.e(TAG, "Failed to load BRISQUE model - call initialize(context) first")
                return -2.0f
            }
            Log.d(TAG, "Computing BRISQUE score for bitmap (${bitmap.width}x${bitmap.height})")
            when (val result = BrisqueCore.computeScore(bitmap, model)) {
                is BrisqueResult.Success -> {
                    Log.d(TAG, "BRISQUE score computed: ${result.score}")
                    result.score
                }
                is BrisqueResult.Error -> {
                    Log.e(TAG, "BRISQUE computation error: ${result.message}")
                    -1.0f
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error computing BRISQUE score: ${e.message}", e)
            -1.0f
        }
    }
}
