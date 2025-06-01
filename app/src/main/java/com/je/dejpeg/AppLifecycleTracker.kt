package com.je.dejpeg

import android.app.Activity
import android.app.Application
import android.os.Bundle

object AppLifecycleTracker : Application.ActivityLifecycleCallbacks {
    var isAppInForeground = false 
    private set
    override fun onActivityStarted(activity: Activity) {
        isAppInForeground = true
    }
    override fun onActivityStopped(activity: Activity) {
        isAppInForeground = false
    }
    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
    override fun onActivityResumed(activity: Activity) {}
    override fun onActivityPaused(activity: Activity) {}
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
    override fun onActivityDestroyed(activity: Activity) {}
}
