package com.je.dejpeg.compose.utils.brisque

import android.util.Log
import java.io.File

class BRISQUEAssessor {
    companion object { // awww it's cute
        private const val TAG = "BRISQUEAssessor"
        private var librariesLoaded = false

        fun loadLibraries() {
            synchronized(this) {
                if (librariesLoaded) return
                try {
                    listOf("opencv_core", "opencv_imgproc", "opencv_imgcodecs", "opencv_quality", "brisque_jni").forEach { loadLib(it) }
                    librariesLoaded = true
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to load libraries: ${e.message}", e)
                }
            }
        }

        private fun loadLib(name: String) {
            try {
                Log.d(TAG, "Loading $name...")
                System.loadLibrary(name)
            } catch (e: UnsatisfiedLinkError) {
                Log.w(TAG, "Library $name not found: ${e.message}")
            } catch (e: Exception) {
                Log.e(TAG, "Error loading $name: ${e.message}", e)
                throw e
            }
        }

        init {
            loadLibraries()
        }
    }

    external fun computeBRISQUEFromFile(imagePath: String, modelPath: String, rangePath: String): Float

    fun assessImageQuality(imagePath: String, modelPath: String, rangePath: String): Float {
        loadLibraries()
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
