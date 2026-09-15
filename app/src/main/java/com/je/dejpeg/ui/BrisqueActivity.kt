/*
 * SPDX-FileCopyrightText: 2026 dryerlint <https://codeberg.org/dryerlint>
 * SPDX-License-Identifier: GNU Affero General Public License v3.0 or later
 */

@file:Suppress("SpellCheckingInspection")

package com.je.dejpeg.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import com.je.dejpeg.ImageRepository
import com.je.dejpeg.ui.components.SnackBarBox
import com.je.dejpeg.ui.components.SnackySnackbarHostState
import com.je.dejpeg.ui.screens.BRISQUEScreen
import com.je.dejpeg.ui.theme.AppTheme

class BrisqueActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val imageId = intent.getStringExtra("imageId") ?: return finish()

        setContent {
            AppTheme {
                val imageRepository = remember { ImageRepository.getInstance() }
                val snackbarHostState = remember { SnackySnackbarHostState() }
                val snackbarController =
                    remember { com.je.dejpeg.ui.components.ActivitySnackySnackbarController() }
                DisposableEffect(snackbarController) {
                    com.je.dejpeg.ui.components.SnackbarController.bind(snackbarController)
                    onDispose {
                        com.je.dejpeg.ui.components.SnackbarController.unbind(
                            snackbarController
                        )
                    }
                }
                SnackBarBox(
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
