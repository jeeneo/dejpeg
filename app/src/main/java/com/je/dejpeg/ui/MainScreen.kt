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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.je.dejpeg.HapticFeedbacks
import com.je.dejpeg.ImageRepository
import com.je.dejpeg.R
import com.je.dejpeg.ui.components.RecoveryDialog
import com.je.dejpeg.ui.components.SnackySnackbarBox
import com.je.dejpeg.ui.components.SnackySnackbarHostState
import com.je.dejpeg.ui.screens.ImageScreen
import com.je.dejpeg.ui.screens.ProcessingScreen
import com.je.dejpeg.ui.screens.SettingsScreen
import com.je.dejpeg.ui.theme.DeJPEGTheme
import com.je.dejpeg.ui.viewmodel.ProcessingViewModel
import com.je.dejpeg.ui.viewmodel.SettingsViewModel
import kotlinx.coroutines.launch

private val PillOuter = 50.dp
private val PillInner = 6.dp

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
    val snackbarController =
        remember { com.je.dejpeg.ui.components.ActivitySnackySnackbarController() }
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner, snackbarController) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                com.je.dejpeg.ui.components.SnackySnackbarController.bind(snackbarController)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        com.je.dejpeg.ui.components.SnackySnackbarController.bind(snackbarController)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            com.je.dejpeg.ui.components.SnackySnackbarController.unbind(snackbarController)
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HomeWrapperScreen(
    viewModel: ProcessingViewModel,
    settingsViewModel: SettingsViewModel,
    imageRepository: ImageRepository,
    sharedUris: List<Uri>
) {
    val context = LocalContext.current
    val currentTab = rememberSaveable { mutableStateOf("processing") }
    val lazyListState = androidx.compose.foundation.lazy.rememberLazyListState()
    val isScrollingUp = remember {
        derivedStateOf {
            lazyListState.firstVisibleItemIndex == 0 || lazyListState.firstVisibleItemScrollOffset == 0
        }
    }
    val toolbarVisible by remember { derivedStateOf { isScrollingUp.value } }
    val scope = rememberCoroutineScope()
    var containerWidthPx by remember { mutableIntStateOf(0) }
    val pageOffset = remember { Animatable(0f) }
    val settleSpec = tween<Float>(durationMillis = 250, easing = LinearOutSlowInEasing)

    val isSettingsActive by remember { derivedStateOf { pageOffset.value >= 0.5f } }

    Scaffold { paddingValues ->
        Box(Modifier
            .fillMaxSize()
            .onSizeChanged { containerWidthPx = it.width }
            .padding(paddingValues)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = true)
                    var velocity = 0f
                    var lastTime = down.uptimeMillis
                    var lastX = down.position.x
                    var totalDx = 0f

                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Main)
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break

                        val now = change.uptimeMillis
                        val dt = (now - lastTime).coerceAtLeast(1L)
                        val dx = change.position.x - lastX
                        totalDx += dx
                        velocity = dx / dt * 1000f
                        lastTime = now
                        lastX = change.position.x

                        if (containerWidthPx > 0 && kotlin.math.abs(totalDx) > 10f) {
                            val delta = -dx / containerWidthPx
                            scope.launch {
                                pageOffset.snapTo((pageOffset.value + delta).coerceIn(0f, 1f))
                            }
                        }
                    }
                    if (kotlin.math.abs(totalDx) < 16f) return@awaitEachGesture
                    scope.launch {
                        val target = when {
                            velocity > 10f -> 0f
                            velocity < -10f -> 1f
                            pageOffset.value > 0.5f -> 1f
                            else -> 0f
                        }
                        pageOffset.animateTo(target, settleSpec)
                        currentTab.value = if (target == 0f) "processing" else "settings"
                    }
                }
            }) {
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        translationX = -pageOffset.value * containerWidthPx
                    }) {
                ProcessingScreen(
                    viewModel = viewModel,
                    settingsViewModel = settingsViewModel,
                    imageRepository = imageRepository,
                    onNavigateToBeforeAfter = { id ->
                        context.startActivity(
                            Intent(context, BeforeAfterActivity::class.java).putExtra("imageId", id)
                        )
                    },
                    onNavigateToBrisque = { id ->
                        context.startActivity(
                            Intent(context, BrisqueActivity::class.java).putExtra("imageId", id)
                        )
                    },
                    onNavigateToCompare = { idA, idB ->
                        context.startActivity(
                            Intent(context, CompareActivity::class.java).putExtra("imageIdA", idA)
                                .putExtra("imageIdB", idB)
                        )
                    },
                    isActive = !isSettingsActive,
                    initialSharedUris = sharedUris,
                    onRemoveSharedUri = { },
                    lazyListState = lazyListState
                )
            }

            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        translationX = containerWidthPx - (pageOffset.value * containerWidthPx)
                    }) {
                SettingsScreen(
                    settingsViewModel, viewModel, onBack = {
                        scope.launch {
                            pageOffset.animateTo(0f, settleSpec)
                            currentTab.value = "processing"
                        }
                    }, isActive = isSettingsActive
                )
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
                        .navigationBarsPadding(),
                    contentAlignment = Alignment.Center
                ) {
                    HorizontalFloatingToolbar(
                        expanded = true,
                        colors = FloatingToolbarDefaults.standardFloatingToolbarColors(),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val processingActive = pageOffset.value < 0.5f
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
                                onClick = {
                                    HapticFeedbacks.light()
                                    scope.launch {
                                        pageOffset.animateTo(0f, settleSpec)
                                        currentTab.value = "processing"
                                    }
                                },
                                shapes = ButtonDefaults.shapes(
                                    shape = RoundedCornerShape(
                                        topStart = PillOuter,
                                        bottomStart = PillOuter,
                                        topEnd = PillOuter,
                                        bottomEnd = PillOuter
                                    ),
                                    pressedShape = RoundedCornerShape(
                                        topStart = PillOuter,
                                        bottomStart = PillOuter,
                                        topEnd = PillInner,
                                        bottomEnd = PillInner
                                    ),
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

                            val settingsActive = pageOffset.value >= 0.5f
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
                                onClick = {
                                    HapticFeedbacks.light()
                                    scope.launch {
                                        pageOffset.animateTo(1f, settleSpec)
                                        currentTab.value = "settings"
                                    }
                                },
                                shapes = ButtonDefaults.shapes(
                                    shape = RoundedCornerShape(
                                        topStart = PillOuter,
                                        bottomStart = PillOuter,
                                        topEnd = PillOuter,
                                        bottomEnd = PillOuter
                                    ),
                                    pressedShape = RoundedCornerShape(
                                        topStart = PillInner,
                                        bottomStart = PillInner,
                                        topEnd = PillOuter,
                                        bottomEnd = PillOuter
                                    ),
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
