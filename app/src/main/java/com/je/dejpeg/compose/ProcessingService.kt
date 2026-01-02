package com.je.dejpeg.compose

import android.app.Service
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Process
import android.util.Log
import com.je.dejpeg.compose.utils.CacheManager
import com.je.dejpeg.data.AppPreferences
import kotlinx.coroutines.*
import java.io.File
import java.io.FileOutputStream
import androidx.core.net.toUri

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
        serviceScope.launch {
            val appPreferences = AppPreferences(applicationContext)
            imageProcessor?.apply {
                customChunkSize = appPreferences.getChunkSizeImmediate()
                customOverlapSize = appPreferences.getOverlapSizeImmediate()
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
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
                imageProcessor?.apply {
                    customChunkSize = chunkSize
                    customOverlapSize = overlapSize
                    Log.d("ProcessingService", "Loaded settings from Intent - chunk_size: $customChunkSize, overlap_size: $customOverlapSize")
                }
                chunkProgressCompleted = 0
                chunkProgressTotal = 0
                currentProgressMessage = "Processing $filename..."
                notifyProgressChange()
                currentJob = serviceScope.launch {
                    try {
                        val uri = uriString.toUri()
                        val inputStream = applicationContext.contentResolver.openInputStream(uri)
                            ?: throw Exception("Unable to open input stream")
                        val bitmap = BitmapFactory.decodeStream(inputStream).also { inputStream.close() }
                            ?: throw Exception("Failed to decode bitmap")
                        imageProcessor?.processImage(bitmap, strength, object : ImageProcessor.ProcessCallback {
                            override fun onComplete(result: Bitmap) {
                                try {
                                    NotificationHelper.show(this@ProcessingService, "Processing complete")
                                    val safeName = if (!imageId.isNullOrEmpty()) imageId else filename
                                    val outFile = File(cacheDir, "${safeName}_processed.png")
                                    FileOutputStream(outFile).use { result.compress(Bitmap.CompressFormat.PNG, 95, it) }
                                    broadcast(PROGRESS_ACTION, PROGRESS_EXTRA_MESSAGE to "Complete", imageId = imageId)
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
                        }, 0, 1)
                    } catch (e: Exception) {
                        val msg = if (e is CancellationException) "Cancelled" else "${e.message}"
                        broadcast(ERROR_ACTION, ERROR_EXTRA_MESSAGE to msg, imageId = imageId)
                        Log.d("ProcessingService", "exception: ${e.message}")
                        scheduleAutoStop()
                    } finally {
                        currentJob = null
                        currentImageId = null
                        chunkProgressCompleted = 0
                        chunkProgressTotal = 0
                    }
                }
            }
            ACTION_CANCEL -> {
                Log.d("ProcessingService", "Cancel action received")
                val id = currentImageId
                val wasRunning = currentJob != null
                runCatching { imageProcessor?.cancelProcessing() }
                runCatching { currentJob?.cancel() }
                currentJob = null
                chunkProgressCompleted = 0
                chunkProgressTotal = 0
                currentProgressMessage = "Processing..."
                if (wasRunning) {
                    broadcast(ERROR_ACTION, ERROR_EXTRA_MESSAGE to "Cancelled", imageId = id)
                    NotificationHelper.show(this@ProcessingService, "Cancelled")
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

    private fun broadcast(action: String, vararg extras: Pair<String, Any?>, imageId: String?) {
        Intent(action).apply {
            setPackage(packageName)
            extras.forEach { (key, value) ->
                when (value) {
                    null -> Unit
                    is String -> putExtra(key, value)
                    is Int -> putExtra(key, value)
                    is Long -> putExtra(key, value)
                    is Boolean -> putExtra(key, value)
                    else -> putExtra(key, value.toString())
                }
            }
            imageId?.let { putExtra(EXTRA_IMAGE_ID, it) }
        }.also { sendBroadcast(it) }
    }

    private fun cleanup() {
        tryStopForeground()
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d("ProcessingService", "onDestroy() - cleaning up")
        currentJob?.cancel()
        serviceScope.cancel()
        imageProcessor?.cancelProcessing()
        CacheManager.clearChunksSync(applicationContext)
        modelManager = null
        imageProcessor = null
    }

    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onTaskRemoved(rootIntent: Intent?) {
        Log.d("ProcessingService", "onTaskRemoved() -> cancelling")
        runCatching {
            imageProcessor?.cancelProcessing()
            currentJob?.cancel()
        }
        CacheManager.clearChunksSync(applicationContext)
        tryStopForeground()
        stopSelf()
    }
    
    private fun tryStopForeground() {
        runCatching {
            stopForeground(STOP_FOREGROUND_REMOVE)
        }
        runCatching { NotificationHelper.cancel(this) }
    }
}
