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
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, createNotification())
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        NotificationHandler(this).clearAllNotifications()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Background Processing",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "prevents the system from killing the app while open"
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification() = NotificationCompat.Builder(this, CHANNEL_ID)
        .setContentTitle("DeJPEG service")
        .setContentText("service active")
        .setSmallIcon(R.drawable.ic_launcher_foreground)
        .setPriority(NotificationCompat.PRIORITY_LOW)
        .setOngoing(true)
        .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
        .setCategory(NotificationCompat.CATEGORY_SERVICE)
        .setAutoCancel(false)
        .apply {
            val pendingIntent = PendingIntent.getActivity(
                this@AppBackgroundService,
                0,
                packageManager.getLaunchIntentForPackage(packageName),
                PendingIntent.FLAG_IMMUTABLE
            )
            setContentIntent(pendingIntent)
        }
        .build()
}
