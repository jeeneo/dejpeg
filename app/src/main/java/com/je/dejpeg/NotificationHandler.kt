package com.je.dejpeg

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

class NotificationHandler(private val context: Context) {
    private val NOTIFICATION_ID_PROCESSING = 1
    private val NOTIFICATION_ID_COMPLETION = 2
    private val NOTIFICATION_ID_ERROR = 3

    fun setupNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "processing_channel",
                context.getString(R.string.processing_updates),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = context.getString(R.string.processing_updates_description)
                setShowBadge(true)
                enableLights(false)
                enableVibration(false)
            }
            val notificationManager = context.getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun showProcessingNotification(currentImage: Int, totalImages: Int) {
        
        val notificationTitle = if (totalImages > 1) {
            context.getString(R.string.processing_batch, currentImage, totalImages)
        } else {
            context.getString(R.string.processing_single)
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(context, "processing_channel")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(notificationTitle)
            .setProgress(0, 0, true)
            .setOngoing(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setOnlyAlertOnce(true)
            .setAutoCancel(false)
            .setContentIntent(pendingIntent)
            .build()

        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_PROCESSING, notification)
    }

    fun showErrorNotification(error: String) {
        if (AppLifecycleTracker.isAppInForeground) return

        val notification = NotificationCompat.Builder(context, "processing_channel")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.processing_error))
            .setContentText(error)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        NotificationManagerCompat.from(context).apply {
            cancel(NOTIFICATION_ID_PROCESSING)
            notify(NOTIFICATION_ID_ERROR, notification)
        }
    }

    fun showCompletionNotification(totalImages: Int) {
        if (AppLifecycleTracker.isAppInForeground) return

        val notificationTitle = if (totalImages > 1) {
            context.getString(R.string.processing_complete_batch, totalImages)
        } else {
            context.getString(R.string.processing_complete_single)
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(context, "processing_channel")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(notificationTitle)
            .setContentText(context.getString(R.string.processing_complete_description))
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        NotificationManagerCompat.from(context).apply {
            cancel(NOTIFICATION_ID_PROCESSING)
            notify(NOTIFICATION_ID_COMPLETION, notification)
        }
    }

    fun dismissProcessingNotification() {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID_PROCESSING)
    }

    fun clearAllNotifications() {
        NotificationManagerCompat.from(context).apply {
            cancelAll()
        }
    }
}
