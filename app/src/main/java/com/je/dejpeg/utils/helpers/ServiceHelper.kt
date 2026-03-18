/* SPDX-FileCopyrightText: 2025 - 2026 dryerlint <https://codeberg.org/dryerlint>
 * SPDX-License-Identifier: GNU Affero General Public License v3.0 or later
 */

package com.je.dejpeg.utils.helpers

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import com.je.dejpeg.ProcessingService

class ServiceCommunicationHelper(
    private val context: Context, private val callbacks: ServiceCallbacks
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
    private var serviceBinder: IBinder? = null
    private var isBound = false
    private val mainHandler = Handler(Looper.getMainLooper())
    private var intentionalDisconnect = false
    private var crashReported = false

    private val deathRecipient = IBinder.DeathRecipient {
        mainHandler.post {
            if (crashReported) return@post
            Log.e(
                "ServiceCommHelper",
                "Service process died (DeathRecipient triggered) while processing: $currentProcessingImageId"
            )
            val imageId = currentProcessingImageId
            cleanupBinding()
            if (imageId != null) {
                callbacks.onServiceCrash(imageId)
            }
        }
    }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            Log.d("ServiceCommHelper", "Service connected, registering DeathRecipient")
            serviceBinder = binder
            isBound = true
            try {
                binder?.linkToDeath(deathRecipient, 0)
                Log.d("ServiceCommHelper", "DeathRecipient linked successfully")
            } catch (e: Exception) {
                Log.e("ServiceCommHelper", "Failed to link DeathRecipient: ${e.message}")
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            if (intentionalDisconnect) return
            crashReported = true
            Log.w("ServiceCommHelper", "Service disconnected unexpectedly")
            val imageId = currentProcessingImageId
            cleanupBinding()
            if (imageId != null) {
                callbacks.onServiceCrash(imageId)
            }
        }
    }

    private fun bindToService() {
        if (isBound) return
        try {
            val intent = Intent(context, ProcessingService::class.java)
            context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
            Log.d("ServiceCommHelper", "Binding to service...")
        } catch (e: Exception) {
            Log.e("ServiceCommHelper", "Failed to bind to service: ${e.message}")
        }
    }

    private fun unbindFromService() {
        if (!isBound) return
        intentionalDisconnect = true
        try {
            serviceBinder?.unlinkToDeath(deathRecipient, 0)
        } catch (e: Exception) {
        }
        try {
            context.unbindService(serviceConnection)
            Log.d("ServiceCommHelper", "Unbound from service")
        } catch (e: Exception) {
        }
        intentionalDisconnect = false
        cleanupBinding()
    }

    private fun cleanupBinding() {
        serviceBinder = null
        isBound = false
        currentProcessingImageId = null
        serviceProcessPid = null
        crashReported = false
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
                        ?.let { message ->
                            imageId?.let { callbacks.onProgress(it, message) }
                        }
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
        currentProcessingImageId = imageId
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

    fun cancelProcessing(onCleanup: () -> Unit): Boolean {
        val pid = serviceProcessPid
        val imageId = currentProcessingImageId
        unbindFromService()
        if (pid != null && pid > 0) {
            try {
                android.os.Process.killProcess(pid)
                onCleanup()
                mainHandler.post { callbacks.onServiceCrash(imageId) }
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
            Log.e(
                "ServiceCommunicationHelper",
                "Failed to start service: ${e.javaClass.simpleName} - ${e.message}"
            )
            false
        }
    }
}
