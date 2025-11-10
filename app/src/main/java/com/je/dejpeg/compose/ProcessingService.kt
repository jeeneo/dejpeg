package com.je.dejpeg

import android.app.Service
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.*
import java.io.File
import java.io.FileOutputStream
import androidx.core.net.toUri

class ProcessingService : Service() {
    companion object {
        const val ACTION_PROCESS = "com.je.dejpeg.action.PROCESS"
        const val EXTRA_URI = "extra_uri"
        const val EXTRA_FILENAME = "extra_filename"
        const val EXTRA_IMAGE_ID = "extra_image_id"
        const val EXTRA_STRENGTH = "extra_strength"
        const val EXTRA_CHUNK_SIZE = "extra_chunk_size"
        const val EXTRA_OVERLAP_SIZE = "extra_overlap_size"
        const val PROGRESS_ACTION = "com.je.dejpeg.action.PROGRESS"
        const val PROGRESS_EXTRA_MESSAGE = "extra_message"
        const val TIME_ESTIMATE_ACTION = "com.je.dejpeg.action.TIME_ESTIMATE"
        const val TIME_ESTIMATE_EXTRA_MILLIS = "extra_time_millis"
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
    private val stopHandler = Handler(Looper.getMainLooper())
    private val autoStopRunnable = Runnable {
        Log.d("ProcessingService", "Auto-stopping service after idle period")
        cleanup()
    }

    override fun onCreate() {
        super.onCreate()
        NotificationHelper.ensureChannel(this)
        startForeground(NotificationHelper.NOTIFICATION_ID, NotificationHelper.build(this, "Initializing..."))
        val pid = android.os.Process.myPid()
        Intent(PID_ACTION).apply {
            setPackage(packageName)
            putExtra(PID_EXTRA_VALUE, pid)
        }.also { sendBroadcast(it) }
        Log.d("ProcessingService", "Service started with PID: $pid")
        modelManager = ModelManager(applicationContext)
        imageProcessor = ImageProcessor(applicationContext, modelManager!!).apply {
            val prefs = applicationContext.getSharedPreferences("ProcessingPrefs", android.content.Context.MODE_PRIVATE)
            customChunkSize = prefs.getInt("chunk_size", ImageProcessor.DEFAULT_CHUNK_SIZE)
            customOverlapSize = prefs.getInt("overlap_size", ImageProcessor.OVERLAP)
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
                currentImageId = imageId
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
                val chunkSize = intent.getIntExtra(EXTRA_CHUNK_SIZE, ImageProcessor.DEFAULT_CHUNK_SIZE)
                val overlapSize = intent.getIntExtra(EXTRA_OVERLAP_SIZE, ImageProcessor.OVERLAP)
                imageProcessor?.apply {
                    customChunkSize = chunkSize
                    customOverlapSize = overlapSize
                    Log.d("ProcessingService", "Loaded settings from Intent - chunk_size: $customChunkSize, overlap_size: $customOverlapSize")
                }
                currentJob = serviceScope.launch {
                    try {
                        NotificationHelper.show(this@ProcessingService, "Processing $filename...")
                        val uri = uriString.toUri()
                        val inputStream = applicationContext.contentResolver.openInputStream(uri)
                            ?: throw Exception("Unable to open input stream")
                        val bitmap = BitmapFactory.decodeStream(inputStream).also { inputStream.close() }
                            ?: throw Exception("Failed to decode bitmap")
                        imageProcessor?.processImage(bitmap, strength, object : ImageProcessor.ProcessCallback {
                            override fun onComplete(result: android.graphics.Bitmap) {
                                try {
                                    NotificationHelper.show(this@ProcessingService, "Processing complete")
                                    val safeName = if (!imageId.isNullOrEmpty()) "${imageId}_$filename" else filename
                                    val outFile = File(cacheDir, "${safeName}_processed.png")
                                    FileOutputStream(outFile).use { result.compress(android.graphics.Bitmap.CompressFormat.PNG, 95, it) }
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
                                NotificationHelper.show(this@ProcessingService, message)
                                broadcast(PROGRESS_ACTION, PROGRESS_EXTRA_MESSAGE to message, imageId = imageId)
                            }
                            override fun onTimeEstimate(timeRemaining: Long) {
                                broadcastTimeEstimate(timeRemaining, imageId)
                            }
                        }, 0, 1)
                    } catch (e: Exception) {
                        val msg = if (e is CancellationException) "Cancelled" else "Error: ${e.message}"
                        broadcast(ERROR_ACTION, ERROR_EXTRA_MESSAGE to msg, imageId = imageId)
                        Log.d("ProcessingService", "exception: ${e.message}")
                        scheduleAutoStop()
                    } finally {
                        currentJob = null
                        currentImageId = null
                    }
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun scheduleAutoStop() {
        stopHandler.postDelayed(autoStopRunnable, 3000)
    }

    private fun broadcast(action: String, vararg extras: Pair<String, String?>, imageId: String?) {
        Intent(action).apply {
            setPackage(packageName)
            extras.forEach { (key, value) -> value?.let { putExtra(key, it) } }
            imageId?.let { putExtra(EXTRA_IMAGE_ID, it) }
        }.also { sendBroadcast(it) }
    }
    
    private fun broadcastTimeEstimate(timeMillis: Long, imageId: String?) {
        Intent(TIME_ESTIMATE_ACTION).apply {
            setPackage(packageName)
            putExtra(TIME_ESTIMATE_EXTRA_MILLIS, timeMillis)
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