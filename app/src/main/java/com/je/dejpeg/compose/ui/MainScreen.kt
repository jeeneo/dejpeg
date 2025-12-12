package com.je.dejpeg.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import android.net.Uri
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.res.stringResource
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.NavType
import com.je.dejpeg.compose.ui.screens.ProcessingScreen
import com.je.dejpeg.compose.ui.screens.SettingsScreen
import com.je.dejpeg.compose.ui.screens.BeforeAfterScreen
import com.je.dejpeg.compose.ui.screens.BRISQUEScreen
import com.je.dejpeg.compose.ui.viewmodel.ProcessingViewModel
import com.je.dejpeg.R
import com.je.dejpeg.compose.utils.rememberHapticFeedback

sealed class Screen(val route: String, val title: String) {
    object Processing : Screen("processing", "Processing")
    object Settings : Screen("settings", "Settings")
    object BeforeAfter : Screen("beforeafter/{imageId}", "Before/After") {
        fun createRoute(imageId: String) = "beforeafter/$imageId"
    }
    object BRISQUE : Screen("brisque/{imageId}", "BRISQUE") {
        fun createRoute(imageId: String) = "brisque/$imageId"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(sharedUris: List<Uri> = emptyList()) {
    val navController = rememberNavController()
    val viewModel: ProcessingViewModel = viewModel()
    val haptic = rememberHapticFeedback()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val isBeforeAfterScreen = currentRoute?.startsWith("beforeafter") ?: false
    val isBRISQUEScreen = currentRoute?.startsWith("brisque") ?: false
    val isFullscreenScreen = isBeforeAfterScreen || isBRISQUEScreen

    Scaffold(
        topBar = {
            if (!isFullscreenScreen) {
                TopAppBar(
                    title = { 
                        Text(when(currentRoute) {
                            Screen.Settings.route -> stringResource(R.string.settings)
                            else -> stringResource(R.string.app_name)
                        })
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
                )
            }
        },
        bottomBar = {
            if (!isFullscreenScreen) {
                NavigationBar {
                    NavigationBarItem(
                        icon = { 
                            TooltipBox(
                                positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                                tooltip = { PlainTooltip { Text(stringResource(R.string.processing)) } },
                                state = rememberTooltipState()
                            ) {
                                Icon(Icons.Filled.Image, contentDescription = stringResource(R.string.processing))
                            }
                        },
                        label = { Text(stringResource(R.string.processing)) },
                        selected = currentRoute == Screen.Processing.route,
                        onClick = { 
                            haptic.light()
                            if (currentRoute != Screen.Processing.route) {
                                navController.navigate(Screen.Processing.route) {
                                    popUpTo(navController.graph.startDestinationId) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        }
                    )
                    NavigationBarItem(
                        icon = { 
                            TooltipBox(
                                positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                                tooltip = { PlainTooltip { Text(stringResource(R.string.settings)) } },
                                state = rememberTooltipState()
                            ) {
                                Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.settings))
                            }
                        },
                        label = { Text(stringResource(R.string.settings)) },
                        selected = currentRoute == Screen.Settings.route,
                        onClick = { 
                            haptic.light()
                            if (currentRoute != Screen.Settings.route) {
                                navController.navigate(Screen.Settings.route) {
                                    popUpTo(navController.graph.startDestinationId) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        }
                    )
                }
            }
        }
    ) { paddingValues ->
        NavHost(
            navController = navController,
            startDestination = Screen.Processing.route,
            modifier = Modifier.fillMaxSize()
        ) {
            composable(Screen.Processing.route) { 
                Box(Modifier.padding(paddingValues)) {
                    ProcessingScreen(viewModel, navController, sharedUris, onRemoveSharedUri = { uri -> (sharedUris as? MutableList<android.net.Uri>)?.remove(uri) }) 
                }
            }
            composable(Screen.Settings.route) { 
                Box(Modifier.padding(paddingValues)) {
                    SettingsScreen(viewModel) 
                }
            }
            composable(
                Screen.BeforeAfter.route,
                arguments = listOf(navArgument("imageId") { type = NavType.StringType })
            ) { backStackEntry ->
                val imageId = backStackEntry.arguments?.getString("imageId")
                if (imageId != null) {
                    BeforeAfterScreen(viewModel, imageId, navController)
                }
            }
            composable(
                Screen.BRISQUE.route,
                arguments = listOf(navArgument("imageId") { type = NavType.StringType })
            ) { backStackEntry ->
                val imageId = backStackEntry.arguments?.getString("imageId")
                if (imageId != null) {
                    BRISQUEScreen(viewModel, imageId, navController)
                }
            }
        }
    }
}