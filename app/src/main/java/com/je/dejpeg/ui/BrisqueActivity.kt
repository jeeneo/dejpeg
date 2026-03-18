/* SPDX-FileCopyrightText: 2026 dryerlint <https://codeberg.org/dryerlint>
 * SPDX-License-Identifier: GNU Affero General Public License v3.0 or later
 */

@file:Suppress("SpellCheckingInspection")

package com.je.dejpeg.ui

import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.je.dejpeg.R
import com.je.dejpeg.data.ImageRepository
import com.je.dejpeg.ui.components.SnackySnackbarBox
import com.je.dejpeg.ui.components.SnackySnackbarHostState
import com.je.dejpeg.ui.screens.BRISQUEScreen
import com.je.dejpeg.ui.theme.DeJPEGTheme

class BrisqueActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val imageId = intent.getStringExtra("imageId") ?: return finish()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(
                OVERRIDE_TRANSITION_OPEN, R.anim.activity_open_enter, R.anim.activity_open_exit
            )
            overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, 0, 0)
        }

        setContent {
            val isDarkTheme = isSystemInDarkTheme()
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
                    enableEdgeToEdge(
                        statusBarStyle = SystemBarStyle.dark(scrim = Color.TRANSPARENT),
                        navigationBarStyle = SystemBarStyle.dark(scrim = Color.TRANSPARENT)
                    )
                }
            }
            DeJPEGTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background
                ) {
                    val imageRepository = remember { ImageRepository.getInstance(this) }
                    val snackbarHostState = remember { SnackySnackbarHostState() }
                    val snackbarController =
                        remember { com.je.dejpeg.ui.components.ActivitySnackySnackbarController() }
                    DisposableEffect(snackbarController) {
                        com.je.dejpeg.ui.components.SnackySnackbarController.bind(snackbarController)
                        onDispose {
                            com.je.dejpeg.ui.components.SnackySnackbarController.unbind(
                                snackbarController
                            )
                        }
                    }
                    SnackySnackbarBox(
                        snackbarHostState = snackbarHostState, controller = snackbarController
                    ) {
                        BRISQUEScreen(
                            imageRepository = imageRepository,
                            imageId = imageId,
                            onBack = { finish() })
                    }
                }
            }
        }
    }
}
