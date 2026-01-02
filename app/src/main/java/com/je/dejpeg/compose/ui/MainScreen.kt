package com.je.dejpeg.ui

import android.app.Activity
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import com.je.dejpeg.ExitActivity
import com.je.dejpeg.compose.ui.screens.ProcessingScreen
import com.je.dejpeg.compose.ui.screens.SettingsScreen
import com.je.dejpeg.compose.ui.screens.BeforeAfterScreen
import com.je.dejpeg.compose.ui.screens.BRISQUEScreen
import com.je.dejpeg.compose.ui.viewmodel.ProcessingViewModel
import com.je.dejpeg.R
import com.je.dejpeg.compose.ui.components.RecoveryImagesDialog
import com.je.dejpeg.compose.utils.rememberHapticFeedback

sealed class AppScreen {
    object Processing : AppScreen()
    object Settings : AppScreen()
    data class BeforeAfter(val imageId: String) : AppScreen()
    data class Brisque(val imageId: String) : AppScreen()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    sharedUris: List<Uri> = emptyList()
) {
    val context = LocalContext.current
    val viewModel: ProcessingViewModel = viewModel()
    val haptic = rememberHapticFeedback()
    
    RecoveryImagesDialog(viewModel = viewModel)
    
    var currentScreen by remember { mutableStateOf<AppScreen>(AppScreen.Processing) }
    var screenStack by remember { mutableStateOf(listOf<AppScreen>()) }
    var lastBackPressTime by remember { mutableStateOf(0L) }
    
    fun navigateToScreen(screen: AppScreen) {
        screenStack = screenStack + currentScreen
        currentScreen = screen
    }
    
    fun goBack() {
        if (screenStack.isNotEmpty()) {
            currentScreen = screenStack.last()
            screenStack = screenStack.dropLast(1)
        }
    }
    
    BackHandler {
        when (currentScreen) {
            AppScreen.Processing -> {
                val currentTime = System.currentTimeMillis()
                if (currentTime - lastBackPressTime < 2000) {
                    viewModel.cancelProcessing()
                    ExitActivity.exitApplication(context)
                } else {
                    Toast.makeText(context, context.getString(R.string.back_exit_confirm), Toast.LENGTH_SHORT).show()
                    lastBackPressTime = currentTime
                }
            }
            else -> goBack()
        }
    }
    
    Box(Modifier.fillMaxSize()) {
        when (currentScreen) {
            AppScreen.Processing -> {
                Scaffold(
                    bottomBar = {
                        NavigationBar {
                            NavigationBarItem(
                                icon = { 
                                    Icon(
                                        Icons.Filled.Image,
                                        contentDescription = stringResource(R.string.processing)
                                    )
                                },
                                label = { Text(stringResource(R.string.processing)) },
                                selected = true,
                                onClick = { 
                                    haptic.light()
                                }
                            )
                            NavigationBarItem(
                                icon = { 
                                    Icon(
                                        Icons.Filled.Settings,
                                        contentDescription = stringResource(R.string.settings)
                                    )
                                },
                                label = { Text(stringResource(R.string.settings)) },
                                selected = false,
                                onClick = { 
                                    haptic.light()
                                    navigateToScreen(AppScreen.Settings)
                                }
                            )
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                ) { paddingValues ->
                    Box(Modifier.padding(paddingValues).fillMaxSize()) {
                        ProcessingScreen(
                            viewModel = viewModel,
                            onNavigateToBeforeAfter = { imageId ->
                                navigateToScreen(AppScreen.BeforeAfter(imageId))
                            },
                            onNavigateToBrisque = { imageId ->
                                navigateToScreen(AppScreen.Brisque(imageId))
                            },
                            onNavigateToSettings = {
                                navigateToScreen(AppScreen.Settings)
                            },
                            initialSharedUris = sharedUris,
                            onRemoveSharedUri = { uri -> 
                                (sharedUris as? MutableList<Uri>)?.remove(uri)
                            }
                        ) 
                    }
                }
            }
            
            AppScreen.Settings -> {
                Scaffold(
                    bottomBar = {
                        NavigationBar {
                            NavigationBarItem(
                                icon = { 
                                    Icon(
                                        Icons.Filled.Image,
                                        contentDescription = stringResource(R.string.processing)
                                    )
                                },
                                label = { Text(stringResource(R.string.processing)) },
                                selected = false,
                                onClick = { 
                                    haptic.light()
                                    goBack()
                                }
                            )
                            NavigationBarItem(
                                icon = { 
                                    Icon(
                                        Icons.Filled.Settings,
                                        contentDescription = stringResource(R.string.settings)
                                    )
                                },
                                label = { Text(stringResource(R.string.settings)) },
                                selected = true,
                                onClick = { 
                                    haptic.light()
                                }
                            )
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                ) { paddingValues ->
                    Box(Modifier.padding(paddingValues).fillMaxSize()) {
                        SettingsScreen(viewModel) 
                    }
                }
            }
            
            is AppScreen.BeforeAfter -> {
                BeforeAfterScreen(
                    viewModel = viewModel,
                    imageId = (currentScreen as AppScreen.BeforeAfter).imageId,
                    onBack = { goBack() }
                )
            }
            
            is AppScreen.Brisque -> {
                BRISQUEScreen(
                    processingViewModel = viewModel,
                    imageId = (currentScreen as AppScreen.Brisque).imageId,
                    onBack = { goBack() }
                )
            }
        }
    }
}
