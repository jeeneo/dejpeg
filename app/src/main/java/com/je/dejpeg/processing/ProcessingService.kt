package com.je.dejpeg.processing

import android.annotation.SuppressLint
import android.app.Notification
import android.app.Service
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Process
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import com.je.dejpeg.AppPreferences
import com.je.dejpeg.ProcessingMode
import com.je.dejpeg.R
import com.je.dejpeg.utils.CacheManager
import com.je.dejpeg.utils.ImageLoadingHelper
import com.je.dejpeg.utils.ImageSource
import com.je.dejpeg.utils.ModelManager
import com.je.dejpeg.utils.NotificationService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

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
        const val EXTRA_ONNX_DEVICE_THREADS = "extra_onnx_device_threads"
        const val EXTRA_MODEL_NAME = "extra_model_name"
        const val EXTRA_PROCESSING_MODE = "extra_processing_mode"
        const val EXTRA_OIDN_WEIGHTS_PATH = "extra_oidn_weights_path"
        const val EXTRA_OIDN_HDR = "extra_oidn_hdr"
        const val EXTRA_OIDN_SRGB = "extra_oidn_srgb"
        const val EXTRA_OIDN_QUALITY = "extra_oidn_quality"
        const val EXTRA_OIDN_MAX_MEMORY_MB = "extra_oidn_max_memory_mb"
        const val EXTRA_OIDN_NUM_THREADS = "extra_oidn_num_threads"
        const val EXTRA_OIDN_INPUT_SCALE = "extra_oidn_input_scale"
        const val PROGRESS_ACTION = "com.je.dejpeg.action.PROGRESS"
        const val PROGRESS_EXTRA_MESSAGE = "extra_message"
        const val PROGRESS_EXTRA_COMPLETED_CHUNKS = "extra_completed_chunks"
        const val PROGRESS_EXTRA_TOTAL_CHUNKS = "extra_total_chunks"
        const val PROGRESS_EXTRA_PARALLEL_CHUNKS = "extra_parallel_chunks"
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
    private var oidnProcessor: OidnProcessor? = null
    private var chunkProgressTotal: Int = 0
    private var chunkProgressCompleted: Int = 0
    private var chunkProgressParallelWorkers: Int = 1
    private var currentProgressMessage: String = "Processing..."
    private val stopHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var cancelBroadcastSent = false
    private val autoStopRunnable = Runnable {
        if (currentJob?.isActive == true) {
            Log.d("ProcessingService", "Stop skipped: job still active")
            return@Runnable
        }
        Log.d("ProcessingService", "Stopping service after idle period")
        cleanup("autoStop")
    }

    private fun startForegroundCompat(message: String) {
        val notification = NotificationService.build(this, message)
        try {
            when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM -> startForegroundMediaProcessing(
                    notification
                )

                Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> startForeground(
                    NotificationService.NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                )

                else -> startForeground(NotificationService.NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            Log.e("ProcessingService", "Failed to start foreground service: ${e.message}", e)
        }
    }

    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    private fun startForegroundMediaProcessing(notification: Notification) {
        startForeground(
            NotificationService.NOTIFICATION_ID,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING
        )
    }

    override fun onCreate() {
        super.onCreate()
        NotificationService.checkChannel(this)
        startForegroundCompat("Initializing...")
        val pid = Process.myPid()
        Intent(PID_ACTION).apply {
            setPackage(packageName)
            putExtra(PID_EXTRA_VALUE, pid)
        }.also { sendBroadcast(it) }
        Log.d("ProcessingService", "Service started with PID: $pid")
        modelManager = ModelManager(applicationContext)
        imageProcessor = ImageProcessor(applicationContext, modelManager!!)
        oidnProcessor = OidnProcessor(applicationContext)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundCompat("Processing...")
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
                val processingMode =
                    ProcessingMode.fromString(intent.getStringExtra(EXTRA_PROCESSING_MODE))
                if (currentJob?.isActive == true) {
                    broadcast(
                        ERROR_ACTION, ERROR_EXTRA_MESSAGE to "Already processing", imageId = imageId
                    )
                    return START_NOT_STICKY
                }
                currentImageId = imageId
                if (processingMode == ProcessingMode.ONNX) {
                    if (modelName != null) {
                        try {
                            val currentModel = modelManager?.getCurrentModelName()
                            if (currentModel == modelName) {
                                Log.d(
                                    "ProcessingService", "Same model ($modelName), skipping reload"
                                )
                            } else {
                                Log.d(
                                    "ProcessingService",
                                    "Using ONNX model from Intent: $modelName (current: $currentModel)"
                                )
                                modelManager?.setActiveModel(modelName)
                            }
                        } catch (e: Exception) {
                            Log.e(
                                "ProcessingService", "Error checking/loading model: ${e.message}", e
                            )
                        }
                    } else {
                        Log.w("ProcessingService", "No model name in Intent, using DataStore")
                    }
                }
                if (uriString == null) {
                    broadcast(ERROR_ACTION, ERROR_EXTRA_MESSAGE to "Missing uri", imageId = imageId)
                    stopSelf()
                    return START_NOT_STICKY
                }
                Log.d("ProcessingService", "Processing $filename (mode: ${processingMode.name})")
                val chunkSize = intent.getIntExtra(
                    EXTRA_CHUNK_SIZE, AppPreferences.DEFAULT_CHUNK_SIZE
                )
                val overlapSize = intent.getIntExtra(
                    EXTRA_OVERLAP_SIZE, AppPreferences.DEFAULT_OVERLAP_SIZE
                )
                val onnxDeviceThreads = intent.getIntExtra(
                    EXTRA_ONNX_DEVICE_THREADS, AppPreferences.DEFAULT_ONNX_DEVICE_THREADS
                )
                imageProcessor?.also {
                    it.chunkSize = chunkSize
                    it.overlapSize = overlapSize
                    it.deviceThreadCount = onnxDeviceThreads
                    Log.d(
                        "ProcessingService",
                        "Loaded settings from Intent - chunk_size: $chunkSize, overlap_size: $overlapSize, onnx_device_threads: $onnxDeviceThreads"
                    )
                }
                chunkProgressCompleted = 0
                chunkProgressTotal = 0
                chunkProgressParallelWorkers = 1
                currentProgressMessage = getString(R.string.processing)
                notifyProgressChange()
                broadcast(
                    PROGRESS_ACTION,
                    PROGRESS_EXTRA_MESSAGE to currentProgressMessage,
                    imageId = imageId
                )
                cancelBroadcastSent = false
                currentJob = serviceScope.launch {
                    try {
                        val unprocessedFile =
                            if (!imageId.isNullOrEmpty()) CacheManager.getUnprocessedImage(
                                applicationContext, imageId
                            ) else null
                        if (unprocessedFile == null || !unprocessedFile.exists()) {
                            throw Exception("Cached unprocessed image not found for imageId: $imageId")
                        }
                        Log.d(
                            "ProcessingService",
                            "Loading cached unprocessed file: ${unprocessedFile.name}"
                        )
                        val bitmap =
                            ImageLoadingHelper.loadBitmap(ImageSource.FromFile(unprocessedFile))
                                ?: throw Exception("Failed to decode bitmap")

                        if (processingMode == ProcessingMode.OIDN) {
                            val weightsPath = intent.getStringExtra(EXTRA_OIDN_WEIGHTS_PATH)
                            val oidnHdr = intent.getBooleanExtra(EXTRA_OIDN_HDR, false)
                            val oidnSrgb = intent.getBooleanExtra(EXTRA_OIDN_SRGB, false)
                            val oidnQuality = intent.getIntExtra(EXTRA_OIDN_QUALITY, 0)
                            val oidnMaxMemoryMB = intent.getIntExtra(EXTRA_OIDN_MAX_MEMORY_MB, 0)
                            val oidnNumThreads = intent.getIntExtra(EXTRA_OIDN_NUM_THREADS, 0)
                            val oidnInputScale = intent.getFloatExtra(EXTRA_OIDN_INPUT_SCALE, 0f)
                            Log.d(
                                "ProcessingService",
                                "Oidn mode: weightsPath=$weightsPath, hdr=$oidnHdr, srgb=$oidnSrgb, quality=$oidnQuality, inputScale=$oidnInputScale"
                            )
                            oidnProcessor?.processImage(
                                inputBitmap = bitmap,
                                weightsPath = weightsPath,
                                numThreads = oidnNumThreads,
                                quality = oidnQuality,
                                maxMemoryMB = oidnMaxMemoryMB,
                                hdr = oidnHdr,
                                srgb = oidnSrgb,
                                inputScale = oidnInputScale,
                                callback = object : OidnProcessor.ProcessCallback {
                                    override fun onComplete(result: Bitmap) {
                                        try {
                                            if (cancelBroadcastSent) {
                                                scheduleAutoStop()
                                                return
                                            }
                                            val safeName =
                                                if (!imageId.isNullOrEmpty()) imageId else filename
                                            val outFile =
                                                File(cacheDir, "${safeName}_processed.png")
                                            FileOutputStream(outFile).use {
                                                result.compress(
                                                    Bitmap.CompressFormat.PNG, 100, it
                                                )
                                            }
                                            broadcast(
                                                COMPLETE_ACTION,
                                                COMPLETE_EXTRA_PATH to outFile.absolutePath,
                                                imageId = imageId
                                            )
                                            NotificationService.show(
                                                this@ProcessingService,
                                                getString(R.string.processing_complete_notification)
                                            )
                                        } catch (e: Exception) {
                                            if (!cancelBroadcastSent) {
                                                broadcast(
                                                    ERROR_ACTION,
                                                    ERROR_EXTRA_MESSAGE to "Save error: ${e.message}",
                                                    imageId = imageId
                                                )
                                            }
                                        } finally {
                                            scheduleAutoStop()
                                        }
                                    }

                                    override fun onError(error: String) {
                                        if (!cancelBroadcastSent) {
                                            broadcast(
                                                ERROR_ACTION,
                                                ERROR_EXTRA_MESSAGE to error,
                                                imageId = imageId
                                            )
                                        }
                                        scheduleAutoStop()
                                    }

                                    override fun onProgress(message: String) {
                                        currentProgressMessage = message
                                        broadcast(
                                            PROGRESS_ACTION,
                                            PROGRESS_EXTRA_MESSAGE to message,
                                            imageId = imageId
                                        )
                                        notifyProgressChange()
                                    }
                                })
                        } else {
                            imageProcessor?.processImage(
                                bitmap, strength, object : ImageProcessor.ProcessCallback {
                                    override fun onComplete(result: Bitmap) {
                                        try {
                                            if (cancelBroadcastSent) {
                                                scheduleAutoStop()
                                                return
                                            }
                                            val safeName =
                                                if (!imageId.isNullOrEmpty()) imageId else filename
                                            val outFile =
                                                File(cacheDir, "${safeName}_processed.png")
                                            FileOutputStream(outFile).use {
                                                result.compress(
                                                    Bitmap.CompressFormat.PNG, 100, it
                                                )
                                            }
                                            broadcast(
                                                COMPLETE_ACTION,
                                                COMPLETE_EXTRA_PATH to outFile.absolutePath,
                                                imageId = imageId
                                            )
                                            NotificationService.show(
                                                this@ProcessingService,
                                                getString(R.string.processing_complete_notification)
                                            )
                                        } catch (e: Exception) {
                                            if (!cancelBroadcastSent) {
                                                broadcast(
                                                    ERROR_ACTION,
                                                    ERROR_EXTRA_MESSAGE to "Save error: ${e.message}",
                                                    imageId = imageId
                                                )
                                            }
                                        } finally {
                                            Log.d(
                                                "ProcessingService",
                                                "processing complete, scheduling auto-stop"
                                            )
                                            scheduleAutoStop()
                                        }
                                    }

                                    override fun onError(error: String) {
                                        if (!cancelBroadcastSent) {
                                            broadcast(
                                                ERROR_ACTION,
                                                ERROR_EXTRA_MESSAGE to error,
                                                imageId = imageId
                                            )
                                        }
                                        Log.d("ProcessingService", "processing error: $error")
                                        scheduleAutoStop()
                                    }

                                    override fun onProgress(message: String) {
                                        currentProgressMessage = message
                                        chunkProgressTotal = 0
                                        chunkProgressCompleted = 0
                                        broadcast(
                                            PROGRESS_ACTION,
                                            PROGRESS_EXTRA_MESSAGE to message,
                                            imageId = imageId
                                        )
                                        notifyProgressChange()
                                    }

                                    override fun onChunkProgress(
                                        currentChunkIndex: Int,
                                        totalChunks: Int,
                                        parallelWorkers: Int
                                    ) {
                                        chunkProgressCompleted = currentChunkIndex
                                        chunkProgressTotal = totalChunks
                                        chunkProgressParallelWorkers = parallelWorkers
                                        currentProgressMessage = formatChunkProgressMessage(
                                            completedChunks = currentChunkIndex,
                                            totalChunks = totalChunks,
                                            parallelWorkers = parallelWorkers
                                        )
                                        broadcast(
                                            PROGRESS_ACTION,
                                            PROGRESS_EXTRA_MESSAGE to currentProgressMessage,
                                            PROGRESS_EXTRA_COMPLETED_CHUNKS to currentChunkIndex,
                                            PROGRESS_EXTRA_TOTAL_CHUNKS to totalChunks,
                                            PROGRESS_EXTRA_PARALLEL_CHUNKS to parallelWorkers,
                                            imageId = imageId
                                        )
                                        notifyProgressChange()
                                    }
                                })
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        broadcast(
                            ERROR_ACTION, ERROR_EXTRA_MESSAGE to "${e.message}", imageId = imageId
                        )
                        Log.d("ProcessingService", "exception: ${e.message}")
                        scheduleAutoStop()
                    } finally {
                        currentJob = null
                    }
                }
            }

            ACTION_CANCEL -> {
                val id = intent.getStringExtra(EXTRA_IMAGE_ID) ?: currentImageId
                NotificationService.show(this, getString(R.string.status_canceling))
                runCatching { imageProcessor?.cancelProcessing() }
                runCatching { oidnProcessor?.cancelProcessing() }
                cancelBroadcastSent = true
                broadcast(
                    ERROR_ACTION, ERROR_EXTRA_MESSAGE to "Cancelled", imageId = id
                )
                runCatching { currentJob?.cancel() }
                currentJob = null
                currentImageId = null
                scheduleAutoStop()
            }
        }
        return START_NOT_STICKY
    }

    private fun scheduleAutoStop() {
        stopHandler.postDelayed(autoStopRunnable, 3000)
    }

    override fun onTimeout(startId: Int) {
        Log.w("ProcessingService", "Foreground service timeout reached, stopping")
        val id = currentImageId
        if (currentJob?.isActive == true) {
            cancelBroadcastSent = true
            broadcast(ERROR_ACTION, ERROR_EXTRA_MESSAGE to "Cancelled", imageId = id)
        }
        cancelAndCleanupResources()
        cleanup("onTimeout")
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        onTimeout(startId)
    }

    private fun formatChunkProgressMessage(
        completedChunks: Int, totalChunks: Int, parallelWorkers: Int
    ): String {
        if (totalChunks <= 1) return getString(R.string.processing)
        if (parallelWorkers > 1) {
            val rangeStart = (completedChunks + 1).coerceAtMost(totalChunks)
            val rangeEnd = (completedChunks + parallelWorkers).coerceAtMost(totalChunks)
            return resources.getQuantityString(
                R.plurals.processing_chunk_range_of_y_threads,
                parallelWorkers,
                rangeStart,
                rangeEnd,
                totalChunks,
                parallelWorkers
            )
        }
        val displayChunk = completedChunks.coerceAtLeast(1).coerceAtMost(totalChunks)
        return getString(R.string.processing_chunk_x_of_y, displayChunk, totalChunks)
    }

    private fun notifyProgressChange() {
        val determinateChunks = chunkProgressTotal > 1
        NotificationService.showProgress(
            context = this,
            message = currentProgressMessage,
            imageId = currentImageId,
            currentChunkIndex = if (determinateChunks) chunkProgressCompleted else null,
            totalChunks = if (determinateChunks) chunkProgressTotal else null,
            parallelWorkers = if (determinateChunks) chunkProgressParallelWorkers else null,
            cancellable = true
        )
    }

    private fun broadcast(
        action: String, vararg extras: Pair<String, Any?>, imageId: String? = currentImageId
    ) {
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
                    else -> { /* null - skip */
                    }
                }
            }
        })
    }

    private fun cleanup(source: String = "unknown") {
        if (currentJob?.isActive == true) {
            Log.d("ProcessingService", "cleanup() from $source aborted: job still active")
            return
        }
        Log.d("ProcessingService", "cleanup() from $source")
        stopHandler.removeCallbacks(autoStopRunnable)
        runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
        runCatching { NotificationService.cancel(this) }
        stopSelf()
    }

    private fun cancelAndCleanupResources() {
        runCatching {
            imageProcessor?.cancelProcessing()
            oidnProcessor?.cancelProcessing()
            currentJob?.cancel()
        }
        currentJob = null
        currentImageId = null
        chunkProgressCompleted = 0
        chunkProgressTotal = 0
        chunkProgressParallelWorkers = 1
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
        oidnProcessor = null
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onTaskRemoved(rootIntent: Intent?) {
        Log.d("ProcessingService", "onTaskRemoved() -> cancelling")
        cancelAndCleanupResources()
        runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
        runCatching { NotificationService.cancel(this) }
        stopSelf()
    }
}

class ServiceCommunicationHelper(
    private val context: Context, private val callbacks: ServiceCallbacks
) {
    interface ServiceCallbacks {
        fun onPidReceived(pid: Int)
        fun onProgress(imageId: String, message: String)
        fun onChunkProgress(imageId: String, completedChunks: Int, totalChunks: Int)
        fun onComplete(imageId: String, path: String)
        fun onError(imageId: String?, message: String)
        fun onServiceCrash(imageId: String?, intentional: Boolean)
    }

    private var startEpoch = 0
    private var pendingStart: (() -> Unit)? = null
    private var serviceProcessPid: Int? = null
    private var isRegistered = false
    private var currentProcessingImageId: String? = null
    private var isBound = false
    private val mainHandler = Handler(Looper.getMainLooper())
    private var bindingEpoch = 0
    private var crashHandled = false
    private var intentionalKill = false

    private fun dispatchServiceCrash(imageId: String?, intentional: Boolean = intentionalKill) {
        if (crashHandled) return
        crashHandled = true
        intentionalKill = false
        val pending = pendingStart
        pendingStart = null
        callbacks.onServiceCrash(imageId, intentional)
        pending?.invoke()
    }

    private data class Binding(
        val connection: ServiceConnection,
        val deathRecipient: IBinder.DeathRecipient,
        val binder: IBinder?
    )

    private var activeBinding: Binding? = null
    private fun bindToService() {
        if (isBound) return
        val generation = ++bindingEpoch
        crashHandled = false
        intentionalKill = false

        val deathRecipient = IBinder.DeathRecipient {
            mainHandler.post {
                if (generation != bindingEpoch) return@post
                Log.e(
                    "ServiceCommHelper",
                    "Service process died (DeathRecipient) while processing: $currentProcessingImageId"
                )
                val imageId = currentProcessingImageId
                cleanupBinding()
                dispatchServiceCrash(imageId)
            }
        }

        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                if (generation != bindingEpoch) return
                Log.d(
                    "ServiceCommHelper",
                    "Service connected [gen=$generation], registering DeathRecipient"
                )
                isBound = true
                activeBinding = Binding(this, deathRecipient, binder)
                try {
                    binder?.linkToDeath(deathRecipient, 0)
                    Log.d("ServiceCommHelper", "DeathRecipient linked successfully")
                } catch (e: Exception) {
                    Log.e("ServiceCommHelper", "Failed to link DeathRecipient: ${e.message}")
                }
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                if (generation != bindingEpoch) return
                Log.w("ServiceCommHelper", "Service disconnected unexpectedly [gen=$generation]")
                val imageId = currentProcessingImageId
                cleanupBinding()
                dispatchServiceCrash(imageId)
            }
        }

        try {
            val intent = Intent(context, ProcessingService::class.java)
            context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
            Log.d("ServiceCommHelper", "Binding to service... [gen=$generation]")
        } catch (e: Exception) {
            Log.e("ServiceCommHelper", "Failed to bind to service: ${e.message}")
        }
    }

    private fun unbindFromService() {
        if (!isBound) return
        bindingEpoch++
        val binding = activeBinding
        if (binding == null) {
            cleanupBinding()
            return
        }
        try {
            binding.binder?.unlinkToDeath(binding.deathRecipient, 0)
        } catch (_: Exception) {
        }
        try {
            context.unbindService(binding.connection)
            Log.d("ServiceCommHelper", "Unbound from service")
        } catch (_: Exception) {
        }
        cleanupBinding()
    }

    private fun cleanupBinding() {
        activeBinding = null
        isBound = false
        currentProcessingImageId = null
        serviceProcessPid = null
        intentionalKill = false
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            val action = intent?.action ?: return
            val imageId = intent.getStringExtra(ProcessingService.EXTRA_IMAGE_ID)
            when (action) {
                ProcessingService.PID_ACTION -> {
                    intent.getIntExtra(ProcessingService.PID_EXTRA_VALUE, -1).takeIf { it != -1 }
                        ?.let { pid ->
                            serviceProcessPid = pid
                            callbacks.onPidReceived(pid)
                        }
                }

                ProcessingService.PROGRESS_ACTION -> {
                    intent.getStringExtra(ProcessingService.PROGRESS_EXTRA_MESSAGE)
                        ?.let { message -> imageId?.let { callbacks.onProgress(it, message) } }
                    val completed =
                        intent.getIntExtra(ProcessingService.PROGRESS_EXTRA_COMPLETED_CHUNKS, -1)
                    val total =
                        intent.getIntExtra(ProcessingService.PROGRESS_EXTRA_TOTAL_CHUNKS, -1)
                    if (completed >= 0 && total > 0 && imageId != null) {
                        callbacks.onChunkProgress(imageId, completed, total)
                    }
                }

                ProcessingService.COMPLETE_ACTION -> {
                    unbindFromService()
                    val path = intent.getStringExtra(ProcessingService.COMPLETE_EXTRA_PATH)
                    if (path != null && !imageId.isNullOrEmpty()) {
                        callbacks.onComplete(imageId, path)
                    }
                }

                ProcessingService.ERROR_ACTION -> {
                    unbindFromService()
                    val message =
                        intent.getStringExtra(ProcessingService.ERROR_EXTRA_MESSAGE) ?: "Error"
                    callbacks.onError(imageId, message)
                }
            }
        }
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    fun register() {
        if (isRegistered) return
        try {
            val filter = IntentFilter().apply {
                addAction(ProcessingService.PID_ACTION)
                addAction(ProcessingService.PROGRESS_ACTION)
                addAction(ProcessingService.COMPLETE_ACTION)
                addAction(ProcessingService.ERROR_ACTION)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                context.registerReceiver(receiver, filter)
            }
            isRegistered = true
            Log.d("ServiceCommHelper", "Receiver registered successfully")
        } catch (e: Exception) {
            Log.e("ServiceCommHelper", "Failed to register receiver: ${e.message}")
        }
    }

    fun unregister() {
        if (!isRegistered) return
        unbindFromService()
        try {
            context.unregisterReceiver(receiver)
            Log.d("ServiceCommHelper", "Receiver unregistered successfully")
        } catch (e: Exception) {
            Log.e("ServiceCommHelper", "Failed to unregister receiver: ${e.message}")
        }
        isRegistered = false
    }

    fun startProcessing(
        imageId: String,
        uriString: String,
        filename: String,
        strength: Float,
        chunkSize: Int,
        overlapSize: Int,
        onnxDeviceThreads: Int,
        modelName: String?,
        processingMode: String = "ONNX",
        oidnWeightsPath: String? = null,
        oidnHdr: Boolean = false,
        oidnSrgb: Boolean = false,
        oidnQuality: Int = 0,
        oidnMaxMemoryMB: Int = 0,
        oidnNumThreads: Int = 0,
        oidnInputScale: Float = 0f
    ) {
        val epoch = startEpoch
        currentProcessingImageId = imageId
        val doStart = {
            if (epoch == startEpoch) {
                bindToService()
                startService(ProcessingService.ACTION_PROCESS) {
                    putExtra(ProcessingService.EXTRA_URI, uriString)
                    putExtra(ProcessingService.EXTRA_FILENAME, filename)
                    putExtra(ProcessingService.EXTRA_IMAGE_ID, imageId)
                    putExtra(ProcessingService.EXTRA_STRENGTH, strength)
                    putExtra(ProcessingService.EXTRA_CHUNK_SIZE, chunkSize)
                    putExtra(ProcessingService.EXTRA_OVERLAP_SIZE, overlapSize)
                    putExtra(ProcessingService.EXTRA_ONNX_DEVICE_THREADS, onnxDeviceThreads)
                    putExtra(ProcessingService.EXTRA_PROCESSING_MODE, processingMode)
                    modelName?.let { putExtra(ProcessingService.EXTRA_MODEL_NAME, it) }
                    oidnWeightsPath?.let { putExtra(ProcessingService.EXTRA_OIDN_WEIGHTS_PATH, it) }
                    putExtra(ProcessingService.EXTRA_OIDN_HDR, oidnHdr)
                    putExtra(ProcessingService.EXTRA_OIDN_SRGB, oidnSrgb)
                    putExtra(ProcessingService.EXTRA_OIDN_QUALITY, oidnQuality)
                    putExtra(ProcessingService.EXTRA_OIDN_MAX_MEMORY_MB, oidnMaxMemoryMB)
                    putExtra(ProcessingService.EXTRA_OIDN_NUM_THREADS, oidnNumThreads)
                    putExtra(ProcessingService.EXTRA_OIDN_INPUT_SCALE, oidnInputScale)
                }
            }
        }
        if (intentionalKill) {
            pendingStart = doStart
            mainHandler.postDelayed(doStart, 150)
        } else {
            doStart()
        }
    }

    fun cancelProcessing(onCleanup: () -> Unit): Boolean {
        val pid = serviceProcessPid
        val imageId = currentProcessingImageId
        intentionalKill = true
        startEpoch++
        unbindFromService()
        if (pid != null && pid > 0) {
            try {
                Process.killProcess(pid)
                onCleanup()
                mainHandler.post { dispatchServiceCrash(imageId, intentional = true) }
                return true
            } catch (e: Exception) {
                Log.e("ServiceCommunicationHelper", "Failed to kill service process: ${e.message}")
            }
        }
        Log.w("ServiceCommunicationHelper", "No valid PID, sending cancel intent")
        startService(ProcessingService.ACTION_CANCEL) {}
        return false
    }

    private fun startService(action: String, configure: Intent.() -> Unit): Boolean {
        val intent = Intent(context, ProcessingService::class.java).apply {
            this.action = action
            configure()
        }
        return try {
            ContextCompat.startForegroundService(context, intent)
            true
        } catch (e: Exception) {
            Log.e(
                "ServiceCommunicationHelper",
                "Failed to start service: ${e.javaClass.simpleName} - ${e.message}"
            )
            false
        }
    }
}
