package com.je.dejpeg.compose.utils.brisque

import android.util.Log
import java.io.File

class BRISQUEAssessor {
    companion object {
        private const val TAG = "BRISQUEAssessor"
        private var libraryLoaded = false

        fun loadLibrary() {
            synchronized(this) {
                if (libraryLoaded) return
                try {
                    // Single library with all OpenCV code statically linked
                    System.loadLibrary("brisque_jni")
                    libraryLoaded = true
                    Log.i(TAG, "BRISQUE library loaded")
                } catch (e: UnsatisfiedLinkError) {
                    Log.e(TAG, "Failed to load brisque_jni: ${e.message}")
                }
            }
        }

        init {
            loadLibrary()
        }
    }

    external fun computeBRISQUEFromFile(imagePath: String, modelPath: String, rangePath: String): Float

    fun assessImageQuality(imagePath: String, modelPath: String, rangePath: String): Float {
        if (!libraryLoaded) {
            Log.e(TAG, "Library not loaded")
            return -2.0f
        }
        if (!File(imagePath).exists() || !File(modelPath).exists() || !File(rangePath).exists()) {
            Log.e(TAG, "One or more files do not exist")
            return -1.0f
        }
        return try {
            Log.d(TAG, "Computing BRISQUE score for image: $imagePath")
            computeBRISQUEFromFile(imagePath, modelPath, rangePath).also {
                Log.d(TAG, "BRISQUE score computed: $it")
            }
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "Native method not found: ${e.message}", e)
            -2.0f
        } catch (e: Exception) {
            Log.e(TAG, "Error computing BRISQUE score: ${e.message}", e)
            -1.0f
        }
    }
}
