/* SPDX-FileCopyrightText: 2025 - 2026 dryerlint <https://codeberg.org/dryerlint>
 * SPDX-License-Identifier: GNU Affero General Public License v3.0 or later
 */

package com.je.dejpeg

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat

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
        val launchIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
            setPackage(context.packageName)
            setClass(context, MainActivity::class.java)
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or (PendingIntent.FLAG_IMMUTABLE)
        )
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_LOW).setOngoing(true)
            .setContentIntent(contentPendingIntent)
    }

    fun build(context: Context, message: String): Notification {
        return baseBuilder(context).setContentTitle(context.getString(R.string.notification_title_processing))
            .setContentText(message).build()
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
        parallelWorkers: Int? = null,
        cancellable: Boolean = true
    ) {
        val builder =
            baseBuilder(context).setContentTitle(context.getString(R.string.notification_title_processing))
                .setContentText(message)
        val hasChunkInfo = currentChunkIndex != null && totalChunks != null && totalChunks > 1
        if (hasChunkInfo) {
            val displayChunk = currentChunkIndex.coerceAtLeast(1).coerceAtMost(totalChunks)
            builder.setProgress(totalChunks, currentChunkIndex, false)
            val subText = if (parallelWorkers != null && parallelWorkers > 1) {
                val rangeStart = (currentChunkIndex + 1).coerceAtMost(totalChunks)
                val rangeEnd = (currentChunkIndex + parallelWorkers).coerceAtMost(totalChunks)
                context.resources.getQuantityString(
                    R.plurals.notification_chunk_progress_range_threads,
                    parallelWorkers,
                    rangeStart,
                    rangeEnd,
                    totalChunks,
                    parallelWorkers
                )
            } else {
                context.getString(
                    R.string.notification_chunk_progress, displayChunk, totalChunks
                )
            }
            builder.setSubText(subText)
        } else {
            builder.setProgress(0, 0, true)
        }
        if (cancellable) {
            val cancelIntent = Intent(
                context, ProcessingService::class.java
            ).setAction(ProcessingService.ACTION_CANCEL)
            val pendingCancel = PendingIntent.getService(
                context,
                0,
                cancelIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or (PendingIntent.FLAG_IMMUTABLE)
            )
            builder.addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                context.getString(R.string.cancel),
                pendingCancel
            )
        }
        val mgr = context.getSystemService(NotificationManager::class.java)
        mgr?.notify(NOTIFICATION_ID, builder.build())
    }

    fun cancel(context: Context) {
        val mgr = context.getSystemService(NotificationManager::class.java)
        mgr?.cancel(NOTIFICATION_ID)
    }
}
