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
 */

/*
* If you use this code in your own project, please give credit
*/

package com.je.dejpeg.compose

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
import com.je.dejpeg.compose.ui.MainScreen
import com.je.dejpeg.compose.ui.components.StarterModelDialog
import com.je.dejpeg.compose.ui.viewmodel.ProcessingViewModel
import com.je.dejpeg.ui.theme.DeJPEGTheme

class MainActivity : ComponentActivity() {
    private val viewModel: ProcessingViewModel by viewModels()
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
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
                    val sharedUris by viewModel.sharedUris.collectAsState()
                    MainScreen(sharedUris = sharedUris)
                }
                if (showStarterModelDialog.value) {
                    StarterModelDialog(
                        onDismiss = {
                            showStarterModelDialog.value = false
                            viewModel.refreshInstalledModels()
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
                    intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM) ?: emptyList()
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
        viewModel.addSharedUri(uri)
    }
}
