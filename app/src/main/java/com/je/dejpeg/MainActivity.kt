/*
 * SPDX-FileCopyrightText: 2025 - 2026 dryerlint <https://codeberg.org/dryerlint>
 * SPDX-License-Identifier: GNU Affero General Public License v3.0 or later
 */

package com.je.dejpeg

import android.Manifest
import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.je.dejpeg.data.AppPreferences
import com.je.dejpeg.data.AppState
import com.je.dejpeg.data.HapticPatterns
import com.je.dejpeg.data.ImageRepository
import com.je.dejpeg.ui.screens.MainScreen
import com.je.dejpeg.ui.theme.AppTheme
import com.je.dejpeg.utils.ModelManager

class App : Application() {
    companion object {
        @SuppressLint("StaticFieldLeak")
        lateinit var ctx: Context
            private set

        val prefs: AppPreferences by lazy {
            val p = AppPreferences()
            HapticPatterns.appHapticsEnabled = p.loadHapticFeedbackEnabled()
            p
        }

        val state: AppState by lazy {
            AppState(prefs)
        }
    }

    override fun onCreate() {
        super.onCreate()
        ctx = applicationContext
    }
}

class MainActivity : ComponentActivity() {
    private var handledIntentHash: Int? = null
    private val imageRepository by lazy { ImageRepository.getInstance() }
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        @Suppress("ControlFlowWithEmptyBody") if (isGranted) {
            // granted
        } else {
            // denied - nothing shown cause its annoying
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        val modelManager = ModelManager.create(this)
        modelManager.initializeStarterModel()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this, Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        handleShareIntent(intent)

        // https://stackoverflow.com/a/79267436
        setContent {
            AppTheme {
                MainScreen()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleShareIntent(intent)
    }

    @Suppress("DEPRECATION")
    private fun handleShareIntent(intent: Intent?) {
        if (intent == null) return
        val hash = System.identityHashCode(intent)
        if (hash == handledIntentHash) return
        handledIntentHash = hash
        when (intent.action) {
            Intent.ACTION_SEND -> {
                val uri: Uri? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    intent.getParcelableExtra(Intent.EXTRA_STREAM)
                }
                uri?.let { addSharedUri(it) }
            }

            Intent.ACTION_SEND_MULTIPLE -> {
                val uris: List<Uri> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
                        ?: emptyList()
                } else {
                    intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM) ?: emptyList()
                }
                uris.forEach { addSharedUri(it) }
            }
        }
    }

    private fun addSharedUri(uri: Uri) {
        try {
            contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION and Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (_: Exception) {
        }
        imageRepository.addSharedUri(uri)
    }
}
