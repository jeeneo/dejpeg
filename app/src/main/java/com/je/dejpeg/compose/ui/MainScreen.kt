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

package com.je.dejpeg.compose.ui

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import com.je.dejpeg.ExitActivity
import com.je.dejpeg.R
import com.je.dejpeg.compose.ui.components.RecoveryDialog
import com.je.dejpeg.compose.ui.screens.BRISQUEScreen
import com.je.dejpeg.compose.ui.screens.BeforeAfterScreen
import com.je.dejpeg.compose.ui.screens.ProcessingScreen
import com.je.dejpeg.compose.ui.screens.SettingsScreen
import com.je.dejpeg.compose.ui.viewmodel.ProcessingViewModel
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
    val backExitMessage = stringResource(R.string.back_exit_confirm)

    RecoveryDialog(viewModel = viewModel)
    
    var currentScreen by remember { mutableStateOf<AppScreen>(AppScreen.Processing) }
    var screenStack by remember { mutableStateOf(listOf<AppScreen>()) }
    var lastBackPressTime by remember { mutableLongStateOf(0L) }
    
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
                    Toast.makeText(context, backExitMessage, Toast.LENGTH_SHORT).show()
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
                                    Crossfade(
                                        targetState = true
                                    ) { isSelected ->
                                        Icon(
                                            if (isSelected) Icons.Filled.Image else Icons.Outlined.Image,
                                            contentDescription = stringResource(R.string.processing)
                                        )
                                    }
                                },
                                label = { Text(stringResource(R.string.processing)) },
                                selected = true,
                                onClick = { 
                                    haptic.light()
                                }
                            )
                            NavigationBarItem(
                                icon = { 
                                    Crossfade(
                                        targetState = false,
                                        animationSpec = tween(1000)
                                    ) { isSelected ->
                                        Icon(
                                            if (isSelected) Icons.Filled.Settings else Icons.Outlined.Settings,
                                            contentDescription = stringResource(R.string.settings)
                                        )
                                    }
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
                                    Crossfade(
                                        targetState = false
                                    ) { isSelected ->
                                        Icon(
                                            if (isSelected) Icons.Filled.Image else Icons.Outlined.Image,
                                            contentDescription = stringResource(R.string.processing)
                                        )
                                    }
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
                                    Crossfade(
                                        targetState = true
                                    ) { isSelected ->
                                        Icon(
                                            if (isSelected) Icons.Filled.Settings else Icons.Outlined.Settings,
                                            contentDescription = stringResource(R.string.settings)
                                        )
                                    }
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
