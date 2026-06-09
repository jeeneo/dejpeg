package com.je.dejpeg

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context

class App : Application() {
    companion object {
        @SuppressLint("StaticFieldLeak") // application context
        lateinit var ctx: Context
            private set
    }

    override fun onCreate() {
        super.onCreate()
        ctx = applicationContext
    }
}
