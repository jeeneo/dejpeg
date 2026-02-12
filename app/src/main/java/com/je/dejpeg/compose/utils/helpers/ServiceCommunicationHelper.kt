/**
* Copyright (C) 2026 dryerlint <codeberg.org/dryerlint>
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
* Also please don't steal my work and claim it as your own, thanks.
*/

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.os.Build
import android.os.IBinder
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import com.je.dejpeg.compose.ProcessingService

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
    private var serviceBinder: IBinder? = null
    private var isBound = false
    private val mainHandler = Handler(Looper.getMainLooper())

    private val deathRecipient = IBinder.DeathRecipient {
        mainHandler.post {
            Log.e("ServiceCommHelper", "Service process died (DeathRecipient triggered) while processing: $currentProcessingImageId")
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
        try {
            serviceBinder?.unlinkToDeath(deathRecipient, 0)
        } catch (e: Exception) {
            Log.w("ServiceCommHelper", "Failed to unlink DeathRecipient: ${e.message}")
        }
        try {
            context.unbindService(serviceConnection)
            Log.d("ServiceCommHelper", "Unbound from service")
        } catch (e: Exception) {
            Log.w("ServiceCommHelper", "Failed to unbind from service: ${e.message}")
        }
        cleanupBinding()
    }

    private fun cleanupBinding() {
        serviceBinder = null
        isBound = false
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
                    unbindFromService()
                    val path = intent.getStringExtra(ProcessingService.COMPLETE_EXTRA_PATH)
                    if (path != null && !imageId.isNullOrEmpty()) {
                        callbacks.onComplete(imageId, path)
                    }
                }
                ProcessingService.ERROR_ACTION -> {
                    unbindFromService()
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
        modelName: String?
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
            modelName?.let { putExtra(ProcessingService.EXTRA_MODEL_NAME, it) }
        }
    }

    fun cancelProcessing(onCleanup: () -> Unit): Boolean {
        val pid = serviceProcessPid
        unbindFromService()
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
