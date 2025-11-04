package com.je.dejpeg.utils

import android.util.Log
import java.io.File

class BrisqueAssessor {
    companion object {
        private const val TAG = "BrisqueAssessor"
        private var librariesLoaded = false
        
        fun loadLibraries() {
            synchronized(this) {
                if (librariesLoaded) return
                
                try {
                    loadLibrarySafely("opencv_core")
                    loadLibrarySafely("opencv_imgproc")
                    loadLibrarySafely("opencv_imgcodecs")
                    loadLibrarySafely("opencv_quality")
                    loadLibrarySafely("brisque_jni")
                    librariesLoaded = true
                    Log.d(TAG, "All libraries loaded")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to load libraries: ${e.message}", e)
                }
            }
        }
        
        private fun loadLibrarySafely(name: String) {
            try {
                Log.d(TAG, "Loading $name...")
                System.loadLibrary(name)
                Log.d(TAG, "$name loaded")
            } catch (e: UnsatisfiedLinkError) {
                Log.w(TAG, "Library $name not found (this may be okay): ${e.message}")
            } catch (e: Exception) {
                Log.e(TAG, "Error loading $name: ${e.message}", e)
                throw e
            }
        }
        
        init {
            loadLibraries()
        }
    }

    external fun computeBRISQUEFromFile(
        imagePath: String,
        modelPath: String,
        rangePath: String
    ): Float

    fun assessImageQuality(
        imagePath: String,
        modelPath: String,
        rangePath: String
    ): Float {
        loadLibraries()
        
        if (!File(imagePath).exists()) {
            Log.e(TAG, "Image file does not exist: $imagePath")
            return -1.0f
        }
        if (!File(modelPath).exists()) {
            Log.e(TAG, "Model file does not exist: $modelPath")
            return -1.0f
        }
        if (!File(rangePath).exists()) {
            Log.e(TAG, "Range file does not exist: $rangePath")
            return -1.0f
        }
        
        return try {
            Log.d(TAG, "Computing BRISQUE score for image: $imagePath")
            val score = computeBRISQUEFromFile(imagePath, modelPath, rangePath)
            Log.d(TAG, "BRISQUE score computed: $score")
            score
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "Native method not found: ${e.message}", e)
            -2.0f
        } catch (e: Exception) {
            Log.e(TAG, "Error computing BRISQUE score: ${e.message}", e)
            -1.0f
        }
    }
}
