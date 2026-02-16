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

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.je.dejpeg.R

object NotificationHelper {
    const val CHANNEL_ID = "processing_channel"
    const val NOTIFICATION_ID = 1001

    fun checkChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mgr = context.getSystemService(NotificationManager::class.java)
            val existing = mgr?.getNotificationChannel(CHANNEL_ID)
            if (existing == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.notification_channel_name),
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = context.getString(R.string.notification_channel_description)
                    setShowBadge(false)
                }
                mgr?.createNotificationChannel(channel)
            }
        }
    }

    private fun baseBuilder(context: Context): NotificationCompat.Builder {
        checkChannel(context)
        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or (PendingIntent.FLAG_IMMUTABLE)
        )
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setContentIntent(contentPendingIntent)
    }

    fun build(context: Context, message: String): Notification {
        return baseBuilder(context)
            .setContentTitle(context.getString(R.string.notification_title_processing))
            .setContentText(message)
            .build()
    }

    fun show(context: Context, message: String) {
        val mgr = context.getSystemService(NotificationManager::class.java)
        mgr?.notify(NOTIFICATION_ID, build(context, message))
    }

    fun showProgress(
        context: Context,
        message: String,
        currentChunkIndex: Int? = null,
        totalChunks: Int? = null,
        cancellable: Boolean = true
    ) {
        val builder = baseBuilder(context)
            .setContentTitle(context.getString(R.string.notification_title_processing))
            .setContentText(message)
        val hasChunkInfo = currentChunkIndex != null && totalChunks != null && totalChunks > 1
        if (hasChunkInfo) {
            val displayChunk = (currentChunkIndex + 1).coerceIn(1, totalChunks)
            builder.setProgress(totalChunks, currentChunkIndex, false)
            builder.setSubText(context.getString(R.string.notification_chunk_progress, displayChunk, totalChunks))
        } else {
            builder.setProgress(0, 0, true)
        }
        if (cancellable) {
            val cancelIntent = Intent(context, ProcessingService::class.java).setAction(ProcessingService.ACTION_CANCEL)
            val pendingCancel = PendingIntent.getService(
                context,
                0,
                cancelIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or (PendingIntent.FLAG_IMMUTABLE)
            )
            builder.addAction(android.R.drawable.ic_menu_close_clear_cancel, context.getString(R.string.cancel), pendingCancel)
        }
        val mgr = context.getSystemService(NotificationManager::class.java)
        mgr?.notify(NOTIFICATION_ID, builder.build())
    }

    fun cancel(context: Context) {
        val mgr = context.getSystemService(NotificationManager::class.java)
        mgr?.cancel(NOTIFICATION_ID)
    }
}
