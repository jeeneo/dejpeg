/*
 * SPDX-FileCopyrightText: 2025 - 2026 dryerlint <https://codeberg.org/dryerlint>
 * SPDX-License-Identifier: GNU Affero General Public License v3.0 or later
 */

package com.je.dejpeg.ui

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.je.dejpeg.ImageRepository
import com.je.dejpeg.ui.components.ActivitySnackySnackbarController
import com.je.dejpeg.ui.components.RecoveryDialog
import com.je.dejpeg.ui.components.SnackySnackbarBox
import com.je.dejpeg.ui.components.SnackySnackbarController
import com.je.dejpeg.ui.components.SnackySnackbarHostState
import com.je.dejpeg.ui.screens.ImageScreen
import com.je.dejpeg.ui.screens.ProcessingScreen
import com.je.dejpeg.ui.theme.DeJPEGTheme
import com.je.dejpeg.ui.viewmodel.ProcessingViewModel
import com.je.dejpeg.ui.viewmodel.SettingsViewModel

sealed class Screen(val route: String) {
    object Home : Screen("home")
}

@Composable
fun MainScreen(
    sharedUris: List<Uri> = emptyList()
) {
    val viewModel: ProcessingViewModel = viewModel()
    val settingsViewModel: SettingsViewModel = viewModel()
    val imageRepository = remember { ImageRepository.getInstance() }
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackySnackbarHostState() }
    val snackbarController = remember { ActivitySnackySnackbarController() }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, snackbarController) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                SnackySnackbarController.bind(snackbarController)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        SnackySnackbarController.bind(snackbarController)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            SnackySnackbarController.unbind(snackbarController)
        }
    }
    LaunchedEffect(Unit) {
        viewModel.imageRepository = imageRepository
        viewModel.settingsViewModel = settingsViewModel
        settingsViewModel.initialize()
    }
    RecoveryDialog(imageRepository = imageRepository)
    SnackySnackbarBox(snackbarHostState = snackbarHostState, controller = snackbarController) {
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
        ) {
            composable(Screen.Home.route) {
                HomeWrapperScreen(
                    viewModel = viewModel,
                    settingsViewModel = settingsViewModel,
                    imageRepository = imageRepository,
                    sharedUris = sharedUris
                )
            }
        }
    }
}

@Composable
fun HomeWrapperScreen(
    viewModel: ProcessingViewModel,
    settingsViewModel: SettingsViewModel,
    imageRepository: ImageRepository,
    sharedUris: List<Uri>
) {
    val context = LocalContext.current

    Scaffold { paddingValues ->
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
                        )
                    )
                },
                onNavigateToBrisque = { id ->
                    context.startActivity(
                        Intent(context, BrisqueActivity::class.java).putExtra("imageId", id)
                    )
                },
                onNavigateToCompare = { idA, idB ->
                    context.startActivity(
                        Intent(context, CompareActivity::class.java).putExtra(
                            "imageIdA", idA
                        ).putExtra("imageIdB", idB)
                    )
                },
                isActive = true,
                initialSharedUris = sharedUris,
                onRemoveSharedUri = { }
            )
        }
    }
}

class BeforeAfterActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val imageId = intent.getStringExtra("imageId") ?: return finish()
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
                    val viewModel: ProcessingViewModel = viewModel()
                    val imageRepository = remember { ImageRepository.getInstance() }
                    val snackbarHostState = remember { SnackySnackbarHostState() }
                    val snackbarController = remember { ActivitySnackySnackbarController() }
                    DisposableEffect(snackbarController) {
                        SnackySnackbarController.bind(snackbarController)
                        onDispose {
                            SnackySnackbarController.unbind(
                                snackbarController
                            )
                        }
                    }
                    LaunchedEffect(Unit) {
                        viewModel.imageRepository = imageRepository
                        viewModel.initialize(this@BeforeAfterActivity)
                    }
                    SnackySnackbarBox(
                        snackbarHostState = snackbarHostState, controller = snackbarController
                    ) {
                        ImageScreen(
                            viewModel = viewModel,
                            imageRepository = imageRepository,
                            imageId = imageId,
                            onBack = { finish() })
                    }
                }
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
                    val viewModel: ProcessingViewModel = viewModel()
                    val imageRepository = remember { ImageRepository.getInstance() }
                    val snackbarHostState = remember { SnackySnackbarHostState() }
                    val snackbarController = remember { ActivitySnackySnackbarController() }
                    DisposableEffect(snackbarController) {
                        SnackySnackbarController.bind(snackbarController)
                        onDispose {
                            SnackySnackbarController.unbind(
                                snackbarController
                            )
                        }
                    }
                    LaunchedEffect(Unit) {
                        viewModel.imageRepository = imageRepository
                        viewModel.initialize(this@CompareActivity)
                    }
                    SnackySnackbarBox(
                        snackbarHostState = snackbarHostState, controller = snackbarController
                    ) {
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
    }
}
