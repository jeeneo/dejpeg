package com.je.dejpeg.compose.utils.helpers

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import com.je.dejpeg.compose.ProcessingService
import java.io.File

class ServiceCommunicationHelper(
    private val context: Context,
    private val callbacks: ServiceCallbacks
) {
    interface ServiceCallbacks {
        fun onPidReceived(pid: Int)
        fun onProgress(imageId: String, message: String)
        fun onChunkProgress(imageId: String, completedChunks: Int, totalChunks: Int)
        fun onComplete(imageId: String, path: String)
        fun onError(imageId: String?, message: String)
        fun onServiceCrash(imageId: String?)
    }

    private var serviceProcessPid: Int? = null
    private var isRegistered = false
    private var currentProcessingImageId: String? = null
    private val pidCheckHandler = Handler(Looper.getMainLooper())
    private val pidCheckInterval = 500L // Check every 500ms
    
    private val pidCheckRunnable = object : Runnable {
        override fun run() {
            val pid = serviceProcessPid ?: return
            val imageId = currentProcessingImageId ?: return
            
            if (!isProcessAlive(pid)) {
                Log.e("ServiceCommHelper", "Service process $pid crashed while processing $imageId")
                onServiceProcessDied(imageId)
                return
            }
            
            pidCheckHandler.postDelayed(this, pidCheckInterval)
        }
    }
    
    private fun isProcessAlive(pid: Int): Boolean {
        return try {
            File("/proc/$pid").exists()
        } catch (e: Exception) {
            Log.w("ServiceCommHelper", "Failed to check process status: ${e.message}")
            true
        }
    }
    
    private fun onServiceProcessDied(imageId: String) {
        stopPidMonitoring()
        callbacks.onServiceCrash(imageId)
    }
    
    private fun startPidMonitoring() {
        currentProcessingImageId?.let { imageId ->
            Log.d("ServiceCommHelper", "Started PID monitoring for: $imageId (pid=$serviceProcessPid)")
            pidCheckHandler.removeCallbacks(pidCheckRunnable)
            pidCheckHandler.postDelayed(pidCheckRunnable, pidCheckInterval)
        }
    }
    
    private fun stopPidMonitoring() {
        if (currentProcessingImageId != null) {
            Log.d("ServiceCommHelper", "Stopped PID monitoring for: $currentProcessingImageId")
        }
        pidCheckHandler.removeCallbacks(pidCheckRunnable)
        currentProcessingImageId = null
        serviceProcessPid = null
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            val action = intent?.action ?: return
            val imageId = intent.getStringExtra(ProcessingService.EXTRA_IMAGE_ID)
            
            when (action) {
                ProcessingService.PID_ACTION -> {
                    intent.getIntExtra(ProcessingService.PID_EXTRA_VALUE, -1)
                        .takeIf { it != -1 }
                        ?.let { pid ->
                            serviceProcessPid = pid
                            callbacks.onPidReceived(pid)
                            startPidMonitoring()
                        }
                }
                ProcessingService.PROGRESS_ACTION -> {
                    intent.getStringExtra(ProcessingService.PROGRESS_EXTRA_MESSAGE)?.let { message ->
                        imageId?.let { callbacks.onProgress(it, message) }
                    }
                    val completed = intent.getIntExtra(ProcessingService.PROGRESS_EXTRA_COMPLETED_CHUNKS, -1)
                    val total = intent.getIntExtra(ProcessingService.PROGRESS_EXTRA_TOTAL_CHUNKS, -1)
                    if (completed >= 0 && total > 0 && imageId != null) {
                        callbacks.onChunkProgress(imageId, completed, total)
                    }
                }
                ProcessingService.COMPLETE_ACTION -> {
                    stopPidMonitoring()
                    val path = intent.getStringExtra(ProcessingService.COMPLETE_EXTRA_PATH)
                    if (path != null && !imageId.isNullOrEmpty()) {
                        callbacks.onComplete(imageId, path)
                    }
                }
                ProcessingService.ERROR_ACTION -> {
                    stopPidMonitoring()
                    val message = intent.getStringExtra(ProcessingService.ERROR_EXTRA_MESSAGE) ?: "Error"
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
        stopPidMonitoring()
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
        modelName: String?
    ) {
        currentProcessingImageId = imageId
        startService(ProcessingService.ACTION_PROCESS) {
            putExtra(ProcessingService.EXTRA_URI, uriString)
            putExtra(ProcessingService.EXTRA_FILENAME, filename)
            putExtra(ProcessingService.EXTRA_IMAGE_ID, imageId)
            putExtra(ProcessingService.EXTRA_STRENGTH, strength)
            putExtra(ProcessingService.EXTRA_CHUNK_SIZE, chunkSize)
            putExtra(ProcessingService.EXTRA_OVERLAP_SIZE, overlapSize)
            modelName?.let { putExtra(ProcessingService.EXTRA_MODEL_NAME, it) }
        }
    }

    fun cancelProcessing(onCleanup: () -> Unit): Boolean {
        val pid = serviceProcessPid
        stopPidMonitoring()
        if (pid != null && pid > 0) {
            try {
                android.os.Process.killProcess(pid)
                onCleanup()
                return true
            } catch (e: Exception) {
                Log.e("ServiceCommunicationHelper", "Failed to kill service process: ${e.message}")
            }
        }
        Log.w("ServiceCommunicationHelper", "No valid service PID available, sending cancel intent")
        startService(ProcessingService.ACTION_CANCEL) { }
        return false
    }

    private fun startService(action: String, configure: Intent.() -> Unit): Boolean {
        val intent = Intent(context, ProcessingService::class.java).apply {
            this.action = action
            configure()
        }
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ContextCompat.startForegroundService(context, intent)
            } else {
                context.startService(intent)
            }
            true
        } catch (e: Exception) {
            Log.e("ServiceCommunicationHelper", "Failed to start service: ${e.javaClass.simpleName} - ${e.message}")
            false
        }
    }
}
