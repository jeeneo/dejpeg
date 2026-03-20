/*
 * SPDX-FileCopyrightText: 2025 - 2026 dryerlint <https://codeberg.org/dryerlint>
 * SPDX-License-Identifier: GNU Affero General Public License v3.0 or later
 */

package com.je.dejpeg.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.je.dejpeg.R
import com.je.dejpeg.data.ImageRepository
import com.je.dejpeg.ui.components.RecoveryDialog
import com.je.dejpeg.ui.components.SnackySnackbarBox
import com.je.dejpeg.ui.components.SnackySnackbarHostState
import com.je.dejpeg.ui.screens.ProcessingScreen
import com.je.dejpeg.ui.screens.SettingsScreen
import com.je.dejpeg.ui.viewmodel.ProcessingViewModel
import com.je.dejpeg.ui.viewmodel.SettingsViewModel
import com.je.dejpeg.utils.rememberHapticFeedback

private val PillOuter = 50.dp
private val PillInner = 6.dp

sealed class Screen(val route: String) {
    object Home : Screen("home")

}

@Composable
fun MainScreen(
    sharedUris: List<Uri> = emptyList()
) {
    val context = LocalContext.current
    val viewModel: ProcessingViewModel = viewModel()
    val settingsViewModel: SettingsViewModel = viewModel()
    val imageRepository = remember { ImageRepository.getInstance(context) }
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackySnackbarHostState() }
    val snackbarController =
        remember { com.je.dejpeg.ui.components.ActivitySnackySnackbarController() }
    androidx.compose.runtime.DisposableEffect(snackbarController) {
        com.je.dejpeg.ui.components.SnackySnackbarController.bind(snackbarController)
        onDispose { com.je.dejpeg.ui.components.SnackySnackbarController.unbind(snackbarController) }
    }
    LaunchedEffect(Unit) {
        viewModel.imageRepository = imageRepository
        viewModel.settingsViewModel = settingsViewModel
        settingsViewModel.initialize(context)
    }
    // no longer photosynthesizes the diode
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HomeWrapperScreen(
    viewModel: ProcessingViewModel,
    settingsViewModel: SettingsViewModel,
    imageRepository: ImageRepository,
    sharedUris: List<Uri>
) {
    val context = LocalContext.current
    val haptic = rememberHapticFeedback()
    var currentTab by rememberSaveable { mutableStateOf("processing") }

    val lazyListState = androidx.compose.foundation.lazy.rememberLazyListState()
    val isScrollingUp = remember {
        derivedStateOf {
            lazyListState.firstVisibleItemIndex == 0 || lazyListState.firstVisibleItemScrollOffset == 0
        }
    }
    val toolbarVisible by remember { derivedStateOf { isScrollingUp.value } }

    Scaffold { paddingValues ->
        Box(Modifier.fillMaxSize()) {
            Box(Modifier.padding(paddingValues)) {
                androidx.compose.animation.AnimatedContent(
                    targetState = currentTab, transitionSpec = {
                        val toSettings = targetState == "settings"
                        (slideInHorizontally { if (toSettings) it else -it } + fadeIn()).togetherWith(
                            slideOutHorizontally { if (toSettings) -it else it } + fadeOut())
                    }, label = "tab_transition"
                ) { tab ->
                    if (tab == "processing") {
                        ProcessingScreen(
                            viewModel = viewModel,
                            settingsViewModel = settingsViewModel,
                            imageRepository = imageRepository,
                            onNavigateToBeforeAfter = { id ->
                                val intent = Intent(
                                    context, BeforeAfterActivity::class.java
                                ).putExtra("imageId", id)
                                context.startActivity(intent)
                            },
                            onNavigateToBrisque = { id ->
                                val intent = Intent(context, BrisqueActivity::class.java).putExtra(
                                    "imageId", id
                                )
                                context.startActivity(intent)
                            },
                            initialSharedUris = sharedUris,
                            onRemoveSharedUri = { /* handle removal */ },
                            lazyListState = lazyListState
                        )
                    } else {
                        SettingsScreen(
                            settingsViewModel, viewModel, onBack = { currentTab = "processing" })
                    }
                }
            }

            AnimatedVisibility(
                visible = toolbarVisible,
                enter = fadeIn(spring(stiffness = Spring.StiffnessMedium)) + slideInVertically(
                    spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMedium
                    )
                ) { it },
                exit = fadeOut(spring(stiffness = Spring.StiffnessMedium)) + slideOutVertically(
                    spring(stiffness = Spring.StiffnessMedium)
                ) { it },
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(bottom = 30.dp),
                    contentAlignment = Alignment.Center
                ) {
                    HorizontalFloatingToolbar(
                        expanded = true,
                        colors = FloatingToolbarDefaults.standardFloatingToolbarColors(),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val processingActive = currentTab == "processing"
                            val processingIconColor by animateColorAsState(
                                targetValue = if (processingActive) MaterialTheme.colorScheme.onSecondaryContainer
                                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                animationSpec = spring(stiffness = Spring.StiffnessMedium),
                                label = "processing_icon_color"
                            )
                            val processingContainerColor by animateColorAsState(
                                targetValue = if (processingActive) MaterialTheme.colorScheme.secondaryContainer
                                else MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.18f),
                                animationSpec = spring(stiffness = Spring.StiffnessMedium),
                                label = "processing_container"
                            )
                            val processingContentColor by animateColorAsState(
                                targetValue = if (processingActive) MaterialTheme.colorScheme.onSecondaryContainer
                                else MaterialTheme.colorScheme.onSurface,
                                animationSpec = spring(stiffness = Spring.StiffnessMedium),
                                label = "processing_content"
                            )
                            Button(
                                onClick = { haptic.light(); currentTab = "processing" },
                                shapes = ButtonDefaults.shapes(
                                    shape = RoundedCornerShape(
                                        topStart = PillOuter,
                                        bottomStart = PillOuter,
                                        topEnd = PillInner,
                                        bottomEnd = PillInner
                                    ), pressedShape = RoundedCornerShape(PillOuter)
                                ),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = processingContainerColor,
                                    contentColor = processingContentColor
                                ),
                                modifier = Modifier
                                    .height(52.dp)
                                    .animateContentSize(
                                        animationSpec = spring(
                                            dampingRatio = Spring.DampingRatioMediumBouncy,
                                            stiffness = Spring.StiffnessMedium
                                        )
                                    ),
                                contentPadding = PaddingValues(horizontal = 24.dp),
                            ) {
                                Icon(
                                    Icons.Filled.Image,
                                    contentDescription = stringResource(R.string.processing),
                                    tint = processingIconColor
                                )
                                if (!processingActive) {
                                    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                                    Text(stringResource(R.string.processing))
                                }
                            }

                            Spacer(Modifier.width(2.dp))

                            val settingsActive = currentTab == "settings"
                            val settingsIconColor by animateColorAsState(
                                targetValue = if (settingsActive) MaterialTheme.colorScheme.onSecondaryContainer
                                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                animationSpec = spring(stiffness = Spring.StiffnessMedium),
                                label = "settings_icon_color"
                            )
                            val settingsContainerColor by animateColorAsState(
                                targetValue = if (settingsActive) MaterialTheme.colorScheme.secondaryContainer
                                else MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.18f),
                                animationSpec = spring(stiffness = Spring.StiffnessMedium),
                                label = "settings_container"
                            )
                            val settingsContentColor by animateColorAsState(
                                targetValue = if (settingsActive) MaterialTheme.colorScheme.onSecondaryContainer
                                else MaterialTheme.colorScheme.onSurface,
                                animationSpec = spring(stiffness = Spring.StiffnessMedium),
                                label = "settings_content"
                            )
                            Button(
                                onClick = { haptic.light(); currentTab = "settings" },
                                shapes = ButtonDefaults.shapes(
                                    shape = RoundedCornerShape(
                                        topStart = PillInner,
                                        bottomStart = PillInner,
                                        topEnd = PillOuter,
                                        bottomEnd = PillOuter
                                    ), pressedShape = RoundedCornerShape(PillOuter)
                                ),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = settingsContainerColor,
                                    contentColor = settingsContentColor
                                ),
                                modifier = Modifier
                                    .height(52.dp)
                                    .animateContentSize(
                                        animationSpec = spring(
                                            dampingRatio = Spring.DampingRatioMediumBouncy,
                                            stiffness = Spring.StiffnessMedium
                                        )
                                    ),
                                contentPadding = PaddingValues(horizontal = 24.dp),
                            ) {
                                Icon(
                                    Icons.Filled.Settings,
                                    contentDescription = stringResource(R.string.settings),
                                    tint = settingsIconColor
                                )
                                if (!settingsActive) {
                                    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                                    Text(stringResource(R.string.settings))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
