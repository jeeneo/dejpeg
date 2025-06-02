package com.je.dejpeg

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.Context
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

class AppBackgroundService : Service() {
    companion object {
        private const val NOTIFICATION_ID = 192
        private const val CHANNEL_ID = "background_processing_channel"
        private const val ACTION_STOP_SERVICE = "com.je.dejpeg.STOP_SERVICE"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP_SERVICE) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        startForeground(NOTIFICATION_ID, createNotification())
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        NotificationHandler(this).clearAllNotifications()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        android.os.Process.killProcess(android.os.Process.myPid())
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Background Processing",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = (getString(R.string.background_service_message))
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification() = NotificationCompat.Builder(this, CHANNEL_ID)
        .setContentTitle(getString(R.string.background_service_title))
        .setContentText("service is active")
        .setSmallIcon(R.drawable.ic_launcher_foreground)
        .setPriority(NotificationCompat.PRIORITY_LOW)
        // .setOngoing(true) - useless in android 14+
        .setCategory(NotificationCompat.CATEGORY_SERVICE)
        .setAutoCancel(false)
        .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
        .apply {
        val openAppIntent = Intent(this@AppBackgroundService, MainActivity::class.java).apply {
            putExtra("show_service_info", true)
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this@AppBackgroundService,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        setContentIntent(pendingIntent)


            val stopIntent = Intent(this@AppBackgroundService, AppBackgroundService::class.java).apply {
                action = ACTION_STOP_SERVICE
            }
            val stopPendingIntent = PendingIntent.getService(
                this@AppBackgroundService,
                1,
                stopIntent,
                PendingIntent.FLAG_IMMUTABLE
            )
            addAction(android.R.drawable.ic_menu_close_clear_cancel, getString(R.string.stop_background_service_text), stopPendingIntent)
        }
        .build()
}