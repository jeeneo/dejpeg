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

package com.je.dejpeg.compose

import android.app.Service
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Binder
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Process
import android.util.Log
import com.je.dejpeg.compose.utils.CacheManager
import com.je.dejpeg.compose.utils.helpers.ImageLoadingHelper
import com.je.dejpeg.data.AppPreferences
import kotlinx.coroutines.*
import java.io.File
import java.io.FileOutputStream
import androidx.core.net.toUri
import com.je.dejpeg.R

class ProcessingService : Service() {
    companion object {
        const val ACTION_PROCESS = "com.je.dejpeg.action.PROCESS"
        const val ACTION_CANCEL = "com.je.dejpeg.action.CANCEL"
        const val EXTRA_URI = "extra_uri"
        const val EXTRA_FILENAME = "extra_filename"
        const val EXTRA_IMAGE_ID = "extra_image_id"
        const val EXTRA_STRENGTH = "extra_strength"
        const val EXTRA_CHUNK_SIZE = "extra_chunk_size"
        const val EXTRA_OVERLAP_SIZE = "extra_overlap_size"
        const val EXTRA_MODEL_NAME = "extra_model_name"
        const val PROGRESS_ACTION = "com.je.dejpeg.action.PROGRESS"
        const val PROGRESS_EXTRA_MESSAGE = "extra_message"
        const val PROGRESS_EXTRA_COMPLETED_CHUNKS = "extra_completed_chunks"
        const val PROGRESS_EXTRA_TOTAL_CHUNKS = "extra_total_chunks"
        const val COMPLETE_ACTION = "com.je.dejpeg.action.COMPLETE"
        const val COMPLETE_EXTRA_PATH = "extra_path"
        const val ERROR_ACTION = "com.je.dejpeg.action.ERROR"
        const val ERROR_EXTRA_MESSAGE = "extra_message"
        const val PID_ACTION = "com.je.dejpeg.action.PID"
        const val PID_EXTRA_VALUE = "extra_pid"
    }

    class LocalBinder : Binder()

    private val binder = LocalBinder()

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var currentJob: Job? = null
    private var currentImageId: String? = null
    private var modelManager: ModelManager? = null
    private var imageProcessor: ImageProcessor? = null
    private var chunkProgressTotal: Int = 0
    private var chunkProgressCompleted: Int = 0
    private var currentProgressMessage: String = "Processing..."
    private val stopHandler = Handler(Looper.getMainLooper())
    private val autoStopRunnable = Runnable {
        Log.d("ProcessingService", "Auto-stopping service after idle period")
        cleanup()
    }

    override fun onCreate() {
        super.onCreate()
        NotificationHelper.checkChannel(this)
        try {
            startForeground(NotificationHelper.NOTIFICATION_ID, NotificationHelper.build(this, "Initializing..."))
        } catch (e: Exception) {
            Log.e("ProcessingService", "Failed to start foreground service: ${e.message}", e)
        }
        val pid = Process.myPid()
        Intent(PID_ACTION).apply {
            setPackage(packageName)
            putExtra(PID_EXTRA_VALUE, pid)
        }.also { sendBroadcast(it) }
        Log.d("ProcessingService", "Service started with PID: $pid")
        modelManager = ModelManager(applicationContext)
        imageProcessor = ImageProcessor(applicationContext, modelManager!!)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            startForeground(NotificationHelper.NOTIFICATION_ID, NotificationHelper.build(this, "Processing..."))
        } catch (e: Exception) {
            Log.e("ProcessingService", "Failed to start foreground in onStartCommand: ${e.message}", e)
        }
        if (intent == null) return START_NOT_STICKY
        Log.d("ProcessingService", "onStartCommand action=${intent.action}")
        when (intent.action) {
            ACTION_PROCESS -> {
                stopHandler.removeCallbacks(autoStopRunnable)
                val uriString = intent.getStringExtra(EXTRA_URI)
                val filename = intent.getStringExtra(EXTRA_FILENAME) ?: "processed.png"
                val imageId = intent.getStringExtra(EXTRA_IMAGE_ID)
                val strength = intent.getFloatExtra(EXTRA_STRENGTH, 50f)
                val modelName = intent.getStringExtra(EXTRA_MODEL_NAME)
                currentImageId = imageId
                if (modelName != null) {
                    Log.d("ProcessingService", "Using model from Intent: $modelName")
                    modelManager?.setActiveModel(modelName)
                } else {
                    Log.w("ProcessingService", "No model name in Intent, using DataStore")
                }
                if (uriString == null) {
                    broadcast(ERROR_ACTION, ERROR_EXTRA_MESSAGE to "Missing uri", imageId = imageId)
                    stopSelf()
                    return START_NOT_STICKY
                }
                if (currentJob != null) {
                    broadcast(ERROR_ACTION, ERROR_EXTRA_MESSAGE to "Already processing", imageId = imageId)
                    return START_NOT_STICKY
                }
                Log.d("ProcessingService", "Processing $filename")
                val chunkSize = intent.getIntExtra(EXTRA_CHUNK_SIZE, AppPreferences.DEFAULT_CHUNK_SIZE)
                val overlapSize = intent.getIntExtra(EXTRA_OVERLAP_SIZE, AppPreferences.DEFAULT_OVERLAP_SIZE)
                imageProcessor?.also {
                    it.chunkSize = chunkSize
                    it.overlapSize = overlapSize
                    Log.d("ProcessingService", "Loaded settings from Intent - chunk_size: $chunkSize, overlap_size: $overlapSize")
                }
                chunkProgressCompleted = 0
                chunkProgressTotal = 0
                currentProgressMessage = getString(R.string.processing_filename, filename)
                notifyProgressChange()
                currentJob = serviceScope.launch {
                    try {
                        val uri = uriString.toUri()
                        val unprocessedFile = if (!imageId.isNullOrEmpty()) { CacheManager.getUnprocessedImage(applicationContext, imageId) } else { null }
                        val bitmap = if (unprocessedFile != null && unprocessedFile.exists()) { 
                            Log.d("ProcessingService", "Loading pre-existing unprocessed file: ${unprocessedFile.name}") // trust, should be a camera import
                            BitmapFactory.decodeFile(unprocessedFile.absolutePath)
                        } else {
                            Log.d("ProcessingService", "Loading from original URI: $uri")
                            ImageLoadingHelper.loadBitmapWithRotation(applicationContext, uri)
                        } ?: throw Exception("Failed to decode bitmap")
                        imageProcessor?.processImage(bitmap, strength, object : ImageProcessor.ProcessCallback {
                            override fun onComplete(result: Bitmap) {
                                try {
                                    NotificationHelper.show(this@ProcessingService, getString(R.string.processing_complete_notification))
                                    val safeName = if (!imageId.isNullOrEmpty()) imageId else filename
                                    val outFile = File(cacheDir, "${safeName}_processed.png")
                                    FileOutputStream(outFile).use { result.compress(Bitmap.CompressFormat.PNG, 95, it) }
                                    broadcast(COMPLETE_ACTION, COMPLETE_EXTRA_PATH to outFile.absolutePath, imageId = imageId)
                                } catch (e: Exception) {
                                    broadcast(ERROR_ACTION, ERROR_EXTRA_MESSAGE to "Save error: ${e.message}", imageId = imageId)
                                } finally {
                                    Log.d("ProcessingService", "processing complete, scheduling auto-stop")
                                    scheduleAutoStop()
                                }
                            }
                            override fun onError(error: String) {
                                broadcast(ERROR_ACTION, ERROR_EXTRA_MESSAGE to error, imageId = imageId)
                                Log.d("ProcessingService", "processing error: $error")
                                scheduleAutoStop()
                            }
                            override fun onProgress(message: String) {
                                currentProgressMessage = message
                                broadcast(PROGRESS_ACTION, PROGRESS_EXTRA_MESSAGE to message, imageId = imageId)
                                notifyProgressChange()
                            }
                            override fun onChunkProgress(currentChunkIndex: Int, totalChunks: Int) {
                                chunkProgressCompleted = currentChunkIndex
                                chunkProgressTotal = totalChunks
                                broadcast(
                                    PROGRESS_ACTION,
                                    PROGRESS_EXTRA_COMPLETED_CHUNKS to currentChunkIndex,
                                    PROGRESS_EXTRA_TOTAL_CHUNKS to totalChunks,
                                    imageId = imageId
                                )
                                notifyProgressChange()
                            }
                        })
                    } catch (e: Exception) {
                        val msg = if (e is CancellationException) "Cancelled" else "${e.message}"
                        broadcast(ERROR_ACTION, ERROR_EXTRA_MESSAGE to msg, imageId = imageId)
                        Log.d("ProcessingService", "exception: ${e.message}")
                        scheduleAutoStop()
                    } finally {
                        currentJob = null
                    }
                }
            }
            ACTION_CANCEL -> {
                Log.d("ProcessingService", "Cancel action received")
                val id = currentImageId
                val wasRunning = currentJob != null
                NotificationHelper.show(this, getString(R.string.status_canceling))
                runCatching { imageProcessor?.cancelProcessing() }
                runCatching { currentJob?.cancel() }
                currentJob = null
                currentImageId = null
                if (wasRunning) {
                    broadcast(ERROR_ACTION, ERROR_EXTRA_MESSAGE to "Cancelled", imageId = id)
                }
                scheduleAutoStop()
            }
        }
        return START_NOT_STICKY
    }

    private fun scheduleAutoStop() {
        stopHandler.postDelayed(autoStopRunnable, 3000)
    }

    private fun notifyProgressChange() {
        val determinateChunks = chunkProgressTotal > 1
        NotificationHelper.showProgress(
            context = this,
            message = currentProgressMessage,
            currentChunkIndex = if (determinateChunks) chunkProgressCompleted else null,
            totalChunks = if (determinateChunks) chunkProgressTotal else null,
            cancellable = true
        )
    }

    private fun broadcast(action: String, vararg extras: Pair<String, Any?>, imageId: String? = currentImageId) {
        sendBroadcast(Intent(action).apply {
            setPackage(packageName)
            imageId?.let { putExtra(EXTRA_IMAGE_ID, it) }
            extras.forEach { (key, value) ->
                when (value) {
                    is String -> putExtra(key, value)
                    is Int -> putExtra(key, value)
                    is Long -> putExtra(key, value)
                    is Boolean -> putExtra(key, value)
                    is Any -> putExtra(key, value.toString())
                    else -> { /* null - skip */ }
                }
            }
        })
    }

    private fun cleanup(source: String = "unknown") {
        Log.d("ProcessingService", "cleanup() from $source")
        stopHandler.removeCallbacks(autoStopRunnable)
        runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
        runCatching { NotificationHelper.cancel(this) }
        stopSelf()
    }

    private fun cancelAndCleanupResources() {
        runCatching {
            imageProcessor?.cancelProcessing()
            currentJob?.cancel()
        }
        currentJob = null
        currentImageId = null
        chunkProgressCompleted = 0
        chunkProgressTotal = 0
        CacheManager.clearChunks(applicationContext)
        CacheManager.clearAbandonedImages(applicationContext)
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d("ProcessingService", "onDestroy() - cleaning up")
        cancelAndCleanupResources()
        serviceScope.cancel()
        modelManager = null
        imageProcessor = null
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onTaskRemoved(rootIntent: Intent?) {
        Log.d("ProcessingService", "onTaskRemoved() -> cancelling")
        cancelAndCleanupResources()
        runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
        runCatching { NotificationHelper.cancel(this) }
        stopSelf()
    }
}
