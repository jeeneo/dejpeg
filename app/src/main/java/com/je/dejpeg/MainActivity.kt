/*
 * SPDX-FileCopyrightText: 2025 - 2026 dryerlint <https://codeberg.org/dryerlint>
 * SPDX-License-Identifier: GNU Affero General Public License v3.0 or later
 */

package com.je.dejpeg

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.je.dejpeg.ui.MainScreen
import com.je.dejpeg.ui.theme.DeJPEGAppTheme
import com.je.dejpeg.ui.viewmodel.SettingsViewModel
import com.je.dejpeg.utils.ModelManager

class MainActivity : ComponentActivity() {
    private var handledIntentHash: Int? = null
    private val settingsViewModel: SettingsViewModel by viewModels()
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
            DeJPEGAppTheme {
                val sharedUris by imageRepository.sharedUris.collectAsState()
                MainScreen(sharedUris = sharedUris)
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
        } catch (_: Exception) { /* o */
        }
        imageRepository.addSharedUri(uri)
    }
}
