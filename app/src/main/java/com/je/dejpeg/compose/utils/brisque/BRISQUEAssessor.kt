/**
* Copyright (C) 2026 dryerlint <codeberg.org/dryerlint>
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
* Also please don't steal my work and claim it as your own, thanks.
*/

package com.je.dejpeg.compose.utils.brisque

import android.content.Context
import android.graphics.BitmapFactory
import android.util.Log
import java.io.File

class BRISQUEAssessor {
    companion object {
        private const val TAG = "BRISQUEAssessor"
        private var contextRef: Context? = null
        fun initialize(context: Context) {
            contextRef = context.applicationContext
        }
        private fun getModel(): BrisqueSVMModel? {
            val ctx = contextRef ?: return null
            return BrisqueModelLoader.loadFromAssets(ctx)
        }
    }

    fun assessImageQuality(imagePath: String, modelPath: String, rangePath: String): Float {
        return assessImageQuality(imagePath)
    }

    fun assessImageQuality(imagePath: String): Float {
        if (!File(imagePath).exists()) {
            Log.e(TAG, "Image file does not exist: $imagePath")
            return -1.0f
        }
        
        return try {
            val model = getModel()
            if (model == null) {
                Log.e(TAG, "Failed to load BRISQUE model - call initialize(context) first")
                return -2.0f
            }
            val bitmap = BitmapFactory.decodeFile(imagePath)
            if (bitmap == null) {
                Log.e(TAG, "Failed to decode image: $imagePath")
                return -1.0f
            }
            try {
                Log.d(TAG, "Computing BRISQUE score for image: $imagePath (${bitmap.width}x${bitmap.height})")
                val score = BrisqueCore.computeScore(bitmap, model)
                Log.d(TAG, "BRISQUE score computed: $score")
                score
            } finally {
                bitmap.recycle()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error computing BRISQUE score: ${e.message}", e)
            -1.0f
        }
    }
    
    fun assessImageQualityFromBitmap(
        bitmap: android.graphics.Bitmap, 
        modelPath: String, 
        rangePath: String
    ): Float {
        return assessImageQualityFromBitmap(bitmap)
    }

    fun assessImageQualityFromBitmap(bitmap: android.graphics.Bitmap): Float {
        return try {
            val model = getModel()
            if (model == null) {
                Log.e(TAG, "Failed to load BRISQUE model - call initialize(context) first")
                return -2.0f
            }
            Log.d(TAG, "Computing BRISQUE score for bitmap (${bitmap.width}x${bitmap.height})")
            val score = BrisqueCore.computeScore(bitmap, model)
            Log.d(TAG, "BRISQUE score computed: $score")
            score
        } catch (e: Exception) {
            Log.e(TAG, "Error computing BRISQUE score: ${e.message}", e)
            -1.0f
        }
    }
}
