/*
 * SPDX-FileCopyrightText: 2025 - 2026 dryerlint <https://codeberg.org/dryerlint>
 * SPDX-License-Identifier: GNU Affero General Public License v3.0 or later
 */

@file:Suppress("SpellCheckingInspection")

package com.je.dejpeg.utils

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.CompatibilityList
import org.tensorflow.lite.gpu.GpuDelegate
import java.io.File
import java.io.FileInputStream
import java.nio.channels.FileChannel

class LiteRtModelManager(
    context: Context,
    coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) : ModelManager(context, coroutineScope) {

    private var currentInterpreter: Interpreter? = null
    private var gpuDelegate: GpuDelegate? = null

    override fun loadLiteRtModel(modelName: String?, useGpu: Boolean): Interpreter {
        val modelToLoad = modelName ?: getActiveModelName(ModelType.LITERT)
        ?: throw Exception("No active LiteRT model set")
        if (currentInterpreter != null && modelToLoad == cachedActiveModels[ModelType.LITERT]) {
            return currentInterpreter!!
        }
        val modelFile = File(getModelsDir(ModelType.LITERT), modelToLoad)
        if (!modelFile.exists()) throw Exception("LiteRT model file does not exist: ${modelFile.absolutePath}")
        val mapped = FileInputStream(modelFile).use { fis ->
            fis.channel.map(FileChannel.MapMode.READ_ONLY, 0, modelFile.length())
        }
        val opts = Interpreter.Options()
        var newDelegate: GpuDelegate? = null
        try {
            if (useGpu) {
                val compatList = CompatibilityList()
                if (compatList.isDelegateSupportedOnThisDevice) {
                    val delegateOptions = compatList.bestOptionsForThisDevice.apply {
                        isPrecisionLossAllowed = true
                        val serializationDir = gpuCacheDir(context).apply { mkdirs() }
                        setSerializationParams(
                            serializationDir.absolutePath, gpuCacheToken(modelToLoad)
                        )
                    }
                    newDelegate = GpuDelegate(delegateOptions)
                    opts.addDelegate(newDelegate)
                    Log.d("ModelManager", "GPU delegate enabled for $modelToLoad")
                } else {
                    Log.w("ModelManager", "GPU delegate not supported for $modelToLoad, using CPU")
                    opts.numThreads = Runtime.getRuntime().availableProcessors().coerceIn(1, 4)
                }
            } else {
                opts.numThreads = Runtime.getRuntime().availableProcessors().coerceIn(1, 4)
            }
            val newInterpreter = Interpreter(mapped, opts)
            val oldInterpreter = currentInterpreter
            val oldDelegate = gpuDelegate
            currentInterpreter = newInterpreter
            gpuDelegate = newDelegate
            try {
                oldInterpreter?.close()
            } catch (e: Exception) {
                Log.e("ModelManager", "Error closing old interpreter: ${e.message}")
            }
            try {
                oldDelegate?.close()
            } catch (e: Exception) {
                Log.e("ModelManager", "Error closing old GPU delegate: ${e.message}")
            }
            System.runFinalization()
            System.gc()
            cachedActiveModels[ModelType.LITERT] = modelToLoad
            setCurrentProcessingModel(modelToLoad)
            Log.d("ModelManager", "Successfully loaded LiteRT model: $modelToLoad (gpu=$useGpu)")
            return newInterpreter
        } catch (e: Exception) {
            Log.e("ModelManager", "Error loading LiteRT model: ${e.message}", e)
            newDelegate?.close()
            throw e
        }
    }

    override fun unloadLiteRtModel() {
        val currentModel = cachedActiveModels[ModelType.LITERT]
        Log.d("ModelManager", "unloadLiteRtModel called for: $currentModel")
        try {
            currentInterpreter?.close(); currentInterpreter = null
        } catch (e: Exception) {
            Log.e("ModelManager", "Error closing interpreter: ${e.message}")
        }
        try {
            gpuDelegate?.close(); gpuDelegate = null
        } catch (e: Exception) {
            Log.e("ModelManager", "Error closing GPU delegate: ${e.message}")
        }
        System.runFinalization(); System.gc()
    }

    override fun deleteGpuCache(modelName: String, type: ModelType): Boolean {
        if (type != ModelType.LITERT) return false
        val files = gpuCacheFiles(context, modelName)
        return if (files.isNotEmpty()) {
            val result = files.all { it.delete() }
            Log.d("ModelManager", "GPU cache deleted for $modelName: $result (${files.size} files)")
            result
        } else {
            false
        }
    }
}
