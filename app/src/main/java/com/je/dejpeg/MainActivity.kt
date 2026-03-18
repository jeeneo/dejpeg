/*
 * SPDX-FileCopyrightText: 2025 - 2026 dryerlint <https://codeberg.org/dryerlint>
 * SPDX-License-Identifier: GNU Affero General Public License v3.0 or later
 */

package com.je.dejpeg

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.je.dejpeg.data.ImageRepository
import com.je.dejpeg.ui.MainScreen
import com.je.dejpeg.ui.components.StarterModelDialog
import com.je.dejpeg.ui.theme.DeJPEGTheme
import com.je.dejpeg.ui.viewmodel.SettingsViewModel

class MainActivity : ComponentActivity() {
    private var handledIntentHash: Int? = null
    private val settingsViewModel: SettingsViewModel by viewModels()
    private val imageRepository by lazy { ImageRepository.getInstance(this) }
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
        val modelManager = ModelManager(this)
        val starterModelExtracted = modelManager.initializeStarterModel()

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
            val isDarkTheme = isSystemInDarkTheme()
            val showStarterModelDialog = remember { mutableStateOf(starterModelExtracted) }
            SideEffect {
                if (!isDarkTheme) {
                    val lightTransparentStyle = SystemBarStyle.light(
                        scrim = Color.TRANSPARENT, darkScrim = Color.TRANSPARENT
                    )
                    enableEdgeToEdge(
                        statusBarStyle = lightTransparentStyle,
                        navigationBarStyle = lightTransparentStyle
                    )
                } else {
                    val darkTransparentStyle = SystemBarStyle.dark(
                        scrim = Color.TRANSPARENT
                    )
                    enableEdgeToEdge(
                        statusBarStyle = darkTransparentStyle,
                        navigationBarStyle = darkTransparentStyle
                    )
                }
            }
            DeJPEGTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background
                ) {
                    val sharedUris by imageRepository.sharedUris.collectAsState()
                    MainScreen(sharedUris = sharedUris)
                }
                if (showStarterModelDialog.value) {
                    StarterModelDialog(
                        onDismiss = {
                            showStarterModelDialog.value = false
                            settingsViewModel.refreshInstalledModels()
                        })
                }
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
