package com.je.dejpeg.ui

import android.net.Uri
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.je.dejpeg.R
import com.je.dejpeg.data.ImageRepository
import com.je.dejpeg.ui.components.RecoveryDialog
import com.je.dejpeg.ui.screens.BRISQUEScreen
import com.je.dejpeg.ui.screens.BeforeAfterScreen
import com.je.dejpeg.ui.screens.ProcessingScreen
import com.je.dejpeg.ui.screens.SettingsScreen
import com.je.dejpeg.ui.viewmodel.ProcessingViewModel
import com.je.dejpeg.ui.viewmodel.SettingsViewModel
import com.je.dejpeg.utils.rememberHapticFeedback

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object BeforeAfter : Screen("beforeAfter/{imageId}") {
        fun createRoute(imageId: String) = "beforeAfter/$imageId"
    }

    object Brisque : Screen("brisque/{imageId}") {
        fun createRoute(imageId: String) = "brisque/$imageId"
    }
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

    LaunchedEffect(Unit) {
        viewModel.imageRepository = imageRepository
        viewModel.settingsViewModel = settingsViewModel
        settingsViewModel.initialize(context)
    }

    RecoveryDialog(imageRepository = imageRepository)

    NavHost(
        navController = navController,
        startDestination = Screen.Home.route,
        modifier = Modifier.fillMaxSize(),
        enterTransition = {
            slideIntoContainer(
                AnimatedContentTransitionScope.SlideDirection.Start, tween(400)
            ) + fadeIn(tween(400))
        },
        exitTransition = {
            slideOutOfContainer(
                AnimatedContentTransitionScope.SlideDirection.Start, tween(400)
            ) + fadeOut(tween(400))
        },
        popEnterTransition = {
            slideIntoContainer(
                AnimatedContentTransitionScope.SlideDirection.End, tween(400)
            ) + fadeIn(tween(400))
        },
        popExitTransition = {
            slideOutOfContainer(
                AnimatedContentTransitionScope.SlideDirection.End, tween(400)
            ) + fadeOut(tween(400))
        }) {
        composable(Screen.Home.route) {
            HomeWrapperScreen(
                navController = navController,
                viewModel = viewModel,
                settingsViewModel = settingsViewModel,
                imageRepository = imageRepository,
                sharedUris = sharedUris
            )
        }

        composable(
            route = Screen.BeforeAfter.route,
            arguments = listOf(navArgument("imageId") { type = NavType.StringType })
        ) { backStackEntry ->
            val imageId = backStackEntry.arguments?.getString("imageId") ?: ""
            BeforeAfterScreen(
                imageRepository = imageRepository,
                imageId = imageId,
                onBack = { navController.popBackStack() })
        }

        composable(
            route = Screen.Brisque.route,
            arguments = listOf(navArgument("imageId") { type = NavType.StringType })
        ) { backStackEntry ->
            val imageId = backStackEntry.arguments?.getString("imageId") ?: ""
            BRISQUEScreen(
                imageRepository = imageRepository,
                imageId = imageId,
                onBack = { navController.popBackStack() })
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeWrapperScreen(
    navController: NavController,
    viewModel: ProcessingViewModel,
    settingsViewModel: SettingsViewModel,
    imageRepository: ImageRepository,
    sharedUris: List<Uri>
) {
    val haptic = rememberHapticFeedback()

    var currentTab by rememberSaveable { mutableStateOf("processing") }

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = currentTab == "processing",
                    onClick = { haptic.light(); currentTab = "processing" },
                    label = { Text(stringResource(R.string.processing)) },
                    icon = {
                        Icon(
                            if (currentTab == "processing") Icons.Filled.Image else Icons.Outlined.Image,
                            null
                        )
                    })
                NavigationBarItem(
                    selected = currentTab == "settings",
                    onClick = { haptic.light(); currentTab = "settings" },
                    label = { Text(stringResource(R.string.settings)) },
                    icon = {
                        Icon(
                            if (currentTab == "settings") Icons.Filled.Settings else Icons.Outlined.Settings,
                            null
                        )
                    })
            }
        }) { paddingValues ->
        Box(Modifier.padding(paddingValues)) {
            if (currentTab == "processing") {
                ProcessingScreen(
                    viewModel = viewModel,
                    settingsViewModel = settingsViewModel,
                    imageRepository = imageRepository,
                    onNavigateToBeforeAfter = { id ->
                        navController.navigate(
                            Screen.BeforeAfter.createRoute(
                                id
                            )
                        )
                    },
                    onNavigateToBrisque = { id ->
                        navController.navigate(
                            Screen.Brisque.createRoute(
                                id
                            )
                        )
                    },
                    onNavigateToSettings = { currentTab = "settings" },
                    initialSharedUris = sharedUris,
                    onRemoveSharedUri = { /* handle removal */ })
            } else {
                SettingsScreen(settingsViewModel, onBack = { currentTab = "processing" })
            }
        }
    }
}
