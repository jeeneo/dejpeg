package com.je.dejpeg

import android.app.Service
import android.content.Intent
import android.os.IBinder

class AppBackgroundService : Service() {

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        NotificationHandler(this).clearAllNotifications()
        stopSelf()
    }
}
