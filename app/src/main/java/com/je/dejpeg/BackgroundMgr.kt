package com.je.dejpeg

import android.app.Application
import android.app.Activity
import android.os.Bundle
import android.content.Intent
import android.app.ActivityManager
import android.content.Context

class dejpeg : Application() {
    override fun onCreate() {
        super.onCreate()
        registerActivityLifecycleCallbacks(AppLifecycleTracker)
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= TRIM_MEMORY_UI_HIDDEN) {
            stopService(Intent(this, AppBackgroundService::class.java))
        }
    }
}