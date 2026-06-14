package com.je.dejpeg.utils

import android.content.Context
import android.util.Log
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.CompatibilityList
import org.tensorflow.lite.gpu.GpuDelegate
import java.io.File
import java.io.FileInputStream
import java.nio.channels.FileChannel

class LiteRtModelManager(private val context: Context) {
    private var interpreter: Interpreter? = null
    private var gpuDelegate: GpuDelegate? = null
    private var currentModelName: String? = null

    fun getModelsDir(): File = File(context.filesDir, "models/litert")

    fun loadModel(modelFileName: String, useGpu: Boolean = true): Interpreter {
        if (interpreter != null && currentModelName == modelFileName) return interpreter!!

        unloadModel()

        val modelFile = File(getModelsDir(), modelFileName)
        if (!modelFile.exists()) throw Exception("LiteRT model not found: ${modelFile.absolutePath}")

        val mapped = FileInputStream(modelFile).use { fis ->
            fis.channel.map(FileChannel.MapMode.READ_ONLY, 0, modelFile.length())
        }

        val opts = Interpreter.Options()

        if (useGpu) {
            val compatList = CompatibilityList()
            if (compatList.isDelegateSupportedOnThisDevice) {
                val delegateOptions = compatList.bestOptionsForThisDevice
                gpuDelegate = GpuDelegate(delegateOptions)
                opts.addDelegate(gpuDelegate)
                Log.d("LiteRtModelManager", "GPU delegate enabled")
            } else {
                Log.w("LiteRtModelManager", "GPU delegate not supported on this device, falling back to CPU")
                opts.numThreads = Runtime.getRuntime().availableProcessors().coerceIn(1, 4)
            }
        } else {
            opts.numThreads = Runtime.getRuntime().availableProcessors().coerceIn(1, 4)
        }

        interpreter = Interpreter(mapped, opts)
        currentModelName = modelFileName
        Log.d("LiteRtModelManager", "Loaded LiteRT model: $modelFileName (gpu=${gpuDelegate != null})")
        return interpreter!!
    }

    fun getCurrentModelName(): String? = currentModelName

    fun unloadModel() {
        interpreter?.close()
        interpreter = null
        gpuDelegate?.close()
        gpuDelegate = null
        currentModelName = null
    }
}
