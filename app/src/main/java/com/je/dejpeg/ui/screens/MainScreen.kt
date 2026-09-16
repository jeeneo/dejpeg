package com.je.dejpeg.ui.screens

/*
 * SPDX-FileCopyrightText: 2026 dryerlint <https://codeberg.org/dryerlint>
 * SPDX-License-Identifier: GNU Affero General Public License v3.0 or later
 */
 
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.je.dejpeg.App
import com.je.dejpeg.ImageRepository
import com.je.dejpeg.ui.components.ActivitySnackySnackbarController
import com.je.dejpeg.ui.components.RecoveryDialog
import com.je.dejpeg.ui.components.SnackBarBox
import com.je.dejpeg.ui.components.SnackbarController
import com.je.dejpeg.ui.components.SnackySnackbarHostState
import com.je.dejpeg.ui.theme.AppTheme
import com.je.dejpeg.ui.viewmodel.ProcessingViewModel
import com.je.dejpeg.ui.viewmodel.SettingsViewModel

@Composable
fun MainScreen(
    sharedUris: List<Uri> = emptyList()
) {
    val viewModel: ProcessingViewModel = viewModel()
    val settingsViewModel: SettingsViewModel = viewModel()
    val imageRepository = remember { ImageRepository.getInstance() }
    val snackbarHostState = remember { SnackySnackbarHostState() }
    val snackbarController = remember { ActivitySnackySnackbarController() }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, snackbarController) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                SnackbarController.bind(snackbarController)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        SnackbarController.bind(snackbarController)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            SnackbarController.unbind(snackbarController)
        }
    }
    LaunchedEffect(Unit) {
        viewModel.imageRepository = imageRepository
        viewModel.settingsViewModel = settingsViewModel
        settingsViewModel.initialize()
    }
    val context = App.ctx
    RecoveryDialog(imageRepository = imageRepository)
    SnackBarBox(snackbarHostState = snackbarHostState, controller = snackbarController) {
        Scaffold(
            contentWindowInsets = WindowInsets.systemBars.only(WindowInsetsSides.Top)
        ) { paddingValues ->
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                ProcessingScreen(
                    viewModel = viewModel,
                    settingsViewModel = settingsViewModel,
                    imageRepository = imageRepository,
                    onNavigateToBeforeAfter = { id ->
                        context.startActivity(
                            Intent(context, BeforeAfterActivity::class.java).putExtra(
                                "imageId", id
                            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    },
                    onNavigateToBrisque = { id ->
                        context.startActivity(
                            Intent(context, BrisqueActivity::class.java).putExtra("imageId", id)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    },
                    onNavigateToCompare = { idA, idB ->
                        context.startActivity(
                            Intent(context, CompareActivity::class.java).putExtra(
                                "imageIdA", idA
                            ).putExtra("imageIdB", idB)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    },
                    isActive = true,
                    initialSharedUris = sharedUris,
                    onRemoveSharedUri = { })
            }
        }
    }
}

private const val IMAGE_ID = "imageId"

@Composable
private fun ActivityContent(
    screen: @Composable (imageRepository: ImageRepository) -> Unit
) {
    AppTheme {
        val imageRepository = remember { ImageRepository.getInstance() }
        val snackbarHostState = remember { SnackySnackbarHostState() }
        val snackbarController = remember { ActivitySnackySnackbarController() }
        DisposableEffect(snackbarController) {
            SnackbarController.bind(snackbarController)
            onDispose {
                SnackbarController.unbind(
                    snackbarController
                )
            }
        }
        SnackBarBox(
            snackbarHostState = snackbarHostState, controller = snackbarController
        ) {
            screen(imageRepository)
        }
    }
}

class BrisqueActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val imageId = intent.getStringExtra(IMAGE_ID) ?: return finish()
        setContent {
            ActivityContent { imageRepository ->
                BRISQUEScreen(
                    imageRepository = imageRepository,
                    imageId = imageId,
                    onBack = { finish() })
            }
        }
    }
}

class BeforeAfterActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val imageId = intent.getStringExtra(IMAGE_ID) ?: return finish()
        setContent {
            ActivityContent { imageRepository ->
                val viewModel: ProcessingViewModel = viewModel()
                LaunchedEffect(Unit) {
                    viewModel.imageRepository = imageRepository
                    viewModel.initialize(this@BeforeAfterActivity)
                }
                ImageScreen(
                    viewModel = viewModel,
                    imageRepository = imageRepository,
                    imageId = imageId,
                    onBack = { finish() })
            }
        }
    }
}

class CompareActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val imageIdA = intent.getStringExtra("imageIdA") ?: return finish()
        val imageIdB = intent.getStringExtra("imageIdB") ?: return finish()
        setContent {
            ActivityContent { imageRepository ->
                val viewModel: ProcessingViewModel = viewModel()
                LaunchedEffect(Unit) {
                    viewModel.imageRepository = imageRepository
                    viewModel.initialize(this@CompareActivity)
                }
                ImageScreen(
                    viewModel = viewModel,
                    imageRepository = imageRepository,
                    imageId = imageIdA,
                    compareImageId = imageIdB,
                    onBack = { finish() })
            }
        }
    }
}