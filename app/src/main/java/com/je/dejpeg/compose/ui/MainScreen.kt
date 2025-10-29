package com.je.dejpeg.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import android.net.Uri
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.compose.ui.res.stringResource
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.je.dejpeg.ui.screens.ProcessingScreen
import com.je.dejpeg.ui.screens.SettingsScreen
import com.je.dejpeg.ui.viewmodel.ProcessingViewModel
import com.je.dejpeg.R
import com.je.dejpeg.ui.utils.rememberHapticFeedback

sealed class Screen(val route: String, val title: String) {
    object Processing : Screen("processing", "Processing")
    object Settings : Screen("settings", "Settings")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(sharedUris: List<Uri> = emptyList()) {
    val navController = rememberNavController()
    val viewModel: ProcessingViewModel = viewModel()
    val haptic = rememberHapticFeedback()
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(when(navController.currentBackStackEntryAsState().value?.destination?.route) {
                        Screen.Settings.route -> stringResource(R.string.settings)
                        else -> stringResource(R.string.app_name)
                    })
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        bottomBar = {
            NavigationBar {
                val currentDest = navController.currentBackStackEntryAsState().value?.destination
                NavigationBarItem(
                    icon = { Icon(Icons.Filled.Image, contentDescription = stringResource(R.string.processing)) },
                    label = { Text(stringResource(R.string.processing)) },
                    selected = currentDest?.hierarchy?.any { it.route == Screen.Processing.route } == true,
                    onClick = { 
                        haptic.light()
                        navController.navigate(Screen.Processing.route) { popUpTo(Screen.Processing.route) { inclusive = true } }
                    }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.settings)) },
                    label = { Text(stringResource(R.string.settings)) },
                    selected = currentDest?.hierarchy?.any { it.route == Screen.Settings.route } == true,
                    onClick = { 
                        haptic.light()
                        navController.navigate(Screen.Settings.route) { popUpTo(Screen.Processing.route) }
                    }
                )
            }
        }
    ) { paddingValues ->
        NavHost(navController, Screen.Processing.route, Modifier.padding(paddingValues)) {
            composable(Screen.Processing.route) { ProcessingScreen(viewModel, navController, sharedUris) }
            composable(Screen.Settings.route) { SettingsScreen(viewModel) }
        }
    }
}
