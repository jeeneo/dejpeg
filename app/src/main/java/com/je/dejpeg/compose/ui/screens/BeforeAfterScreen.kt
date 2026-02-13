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
*
*/

/*
* If you use this code in your own project, please give credit
*/

package com.je.dejpeg.compose.ui.screens

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.je.dejpeg.R
import com.je.dejpeg.compose.ui.components.BeforeAfterSlider
import com.je.dejpeg.compose.ui.components.SaveImageDialog
import com.je.dejpeg.compose.ui.components.LoadingDialog
import com.je.dejpeg.compose.utils.ImageActions
import com.je.dejpeg.compose.utils.rememberHapticFeedback
import com.je.dejpeg.compose.ui.viewmodel.ProcessingViewModel
import com.je.dejpeg.data.AppPreferences
import kotlinx.coroutines.launch
import me.saket.telephoto.zoomable.ZoomSpec
import me.saket.telephoto.zoomable.ZoomLimit
import me.saket.telephoto.zoomable.OverzoomEffect
import me.saket.telephoto.zoomable.rememberZoomableState
import me.saket.telephoto.zoomable.zoomable

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BeforeAfterScreen(
    viewModel: ProcessingViewModel,
    imageId: String,
    onBack: () -> Unit = {},
    showAfter: Boolean = true
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptic = rememberHapticFeedback()
    val appPreferences = remember { AppPreferences(context.applicationContext) }
    val skipSaveDialog by appPreferences.skipSaveDialog.collectAsState(initial = false)

    val images by viewModel.images.collectAsState()
    val image = images.firstOrNull { it.id == imageId }
    var showSaveDialog by remember { mutableStateOf(false) }
    var overwriteDialogFilename by remember { mutableStateOf<String?>(null) }
    var saveErrorMessage by remember { mutableStateOf<String?>(null) }
    val isSavingImages by viewModel.isSavingImages.collectAsState()
    val savingImagesProgress by viewModel.savingImagesProgress.collectAsState()

    BackHandler(onBack = onBack)

    if (image == null) {
        LaunchedEffect(Unit) { onBack() }
        return
    }

    val beforeBitmap = image.inputBitmap
    val afterBitmap = if (showAfter) image.outputBitmap else null
    val filename = image.filename
    val showSaveAllOption = images.any { it.outputBitmap != null }

    val performSave = { bitmap: Bitmap, name: String ->
        ImageActions.saveImage(
            context = context,
            bitmap = bitmap,
            filename = name,
            imageId = imageId,
            onSuccess = {
                viewModel.markImageAsSaved(imageId)
            },
            onError = { errorMsg ->
                saveErrorMessage = errorMsg
            }
        )
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        TopAppBar(
            title = { Text(filename, style = MaterialTheme.typography.titleMedium) },
            navigationIcon = {
                IconButton(onClick = { haptic.light(); onBack() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
        )

        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
        ) {
            if (afterBitmap != null) {
                BeforeAfterSlider(
                    beforeBitmap = beforeBitmap,
                    afterBitmap = afterBitmap,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                SingleImageView(beforeBitmap)
            }
        }

        Surface(
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp, horizontal = 16.dp)
                    .navigationBarsPadding(),
                Arrangement.SpaceEvenly
            ) {
                IconButton(onClick = {
                    haptic.light()
                    ImageActions.shareImage(context, afterBitmap ?: beforeBitmap)
                }) {
                    Icon(Icons.Filled.Share, "Share", modifier = Modifier.size(32.dp))
                }

                IconButton(onClick = {
                    haptic.medium()
                    if (skipSaveDialog) {
                        if (ImageActions.checkFileExists(filename)) {
                            overwriteDialogFilename = filename
                        } else {
                            viewModel.saveImage(
                                context = context,
                                imageId = imageId,
                                onSuccess = {},
                                onError = { errorMsg -> saveErrorMessage = errorMsg }
                            )
                        }
                    } else {
                        showSaveDialog = true
                    }
                }) {
                    Icon(Icons.Filled.Save, stringResource(id = R.string.save), modifier = Modifier.size(32.dp))
                }
            }
        }
    }

    if (showSaveDialog) {
        SaveImageDialog(filename, showSaveAllOption, false,
            hideOptions = false,
            onDismissRequest = { showSaveDialog = false }) { name, all, skip ->
            if (skip) {
                scope.launch { appPreferences.setSkipSaveDialog(true) }
            }
            if (all) {
                viewModel.saveAllImages(
                    context = context,
                    onComplete = {},
                    onError = { errorMsg -> saveErrorMessage = errorMsg }
                )
            } else {
                if (ImageActions.checkFileExists(name)) {
                    overwriteDialogFilename = name
                } else {
                    performSave(afterBitmap ?: beforeBitmap, name)
                }
            }
        }
    }

    overwriteDialogFilename?.let { fname ->
        SaveImageDialog(fname,
            showSaveAllOption = false,
            initialSaveAll = false,
            hideOptions = true,
            onDismissRequest = { overwriteDialogFilename = null }) { name, _, _ ->
            performSave(afterBitmap ?: beforeBitmap, name)
        }
    }

    saveErrorMessage?.let { errorMsg ->
        AlertDialog(
            onDismissRequest = { saveErrorMessage = null },
            title = { Text(stringResource(R.string.error_saving_image_title)) },
            text = { Text(errorMsg) },
            confirmButton = {
                TextButton(onClick = { saveErrorMessage = null }) {
                    Text(stringResource(R.string.ok))
                }
            }
        )
    }

    if (isSavingImages) {
        val progress = savingImagesProgress
        LoadingDialog(
            title = stringResource(R.string.saving_images),
            progress = progress?.let { it.first.toFloat() / it.second.toFloat() },
            progressText = progress?.let { stringResource(R.string.saving_image_progress, it.first, it.second) }
        )
    }
}

@Composable
private fun SingleImageView(bitmap: Bitmap) {
    val context = LocalContext.current
    val appPreferences = remember { AppPreferences(context.applicationContext) }
    val isHapticEnabled by appPreferences.hapticFeedbackEnabled.collectAsState(initial = true)
    
    val zoomableState = rememberZoomableState(
        ZoomSpec(
            maximum = ZoomLimit(factor = 20f, overzoomEffect = if (isHapticEnabled) OverzoomEffect.RubberBanding else OverzoomEffect.Disabled),
            minimum = ZoomLimit(factor = 1f, overzoomEffect = if (isHapticEnabled) OverzoomEffect.RubberBanding else OverzoomEffect.Disabled)
        )
    )
    
    Box(Modifier.fillMaxSize().zoomable(zoomableState), Alignment.Center) {
        Image(bitmap.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit, filterQuality = FilterQuality.None)
    }
}