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
}