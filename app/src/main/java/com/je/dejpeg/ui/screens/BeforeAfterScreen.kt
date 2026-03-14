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

package com.je.dejpeg.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.je.dejpeg.R
import com.je.dejpeg.data.AppPreferences
import com.je.dejpeg.data.ImageRepository
import com.je.dejpeg.ui.components.BeforeAfterSlider
import com.je.dejpeg.ui.components.LoadingDialog
import com.je.dejpeg.ui.components.SaveImageDialog
import com.je.dejpeg.utils.helpers.ImageActions
import com.je.dejpeg.utils.rememberHapticFeedback
import kotlinx.coroutines.launch
import me.saket.telephoto.zoomable.OverzoomEffect
import me.saket.telephoto.zoomable.ZoomLimit
import me.saket.telephoto.zoomable.ZoomSpec
import me.saket.telephoto.zoomable.rememberZoomableState
import me.saket.telephoto.zoomable.zoomable

private val PillOuter = 50.dp
private val PillInner = 6.dp

@Suppress("AssignedValueIsNeverRead")
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun BeforeAfterScreen(
    imageRepository: ImageRepository,
    imageId: String,
    onBack: () -> Unit = {},
    showAfter: Boolean = true
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptic = rememberHapticFeedback()
    val appPreferences = remember { AppPreferences(context.applicationContext) }
    val skipSaveDialog by appPreferences.skipSaveDialog.collectAsState(initial = false)

    val images by imageRepository.images.collectAsState()
    val image = images.firstOrNull { it.id == imageId }
    var showSaveDialog by remember { mutableStateOf(false) }
    var overwriteDialogFilename by remember { mutableStateOf<String?>(null) }
    var saveErrorMessage by remember { mutableStateOf<String?>(null) }
    val isSavingImages by imageRepository.isSavingImages.collectAsState()
    val savingImagesProgress by imageRepository.savingImagesProgress.collectAsState()

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
            onSuccess = { imageRepository.markImageAsSaved(imageId) },
            onError = { errorMsg -> saveErrorMessage = errorMsg })
    }

    Column(
        Modifier.fillMaxSize()
    ) {
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
        ) {
            Box(
                Modifier.fillMaxSize()
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
            Row(
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 28.dp)
                    .zIndex(1f)
            ) {
                Button(
                    modifier = Modifier.height(56.dp),
                    onClick = {
                        haptic.light()
                        ImageActions.shareImage(context, afterBitmap ?: beforeBitmap)
                    },
                    shapes = ButtonDefaults.shapes(
                        shape = RoundedCornerShape(
                            topStart = PillOuter,
                            bottomStart = PillOuter,
                            topEnd = PillInner,
                            bottomEnd = PillInner
                        ), pressedShape = RoundedCornerShape(PillOuter)
                    ),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.inverseOnSurface,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    ),
                    contentPadding = PaddingValues(horizontal = 20.dp),
                ) {
                    Icon(
                        Icons.Filled.Share,
                        contentDescription = "Share",
                    )
                    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                    Text(
                        text = "Share",
                    )
                }

                Button(
                    modifier = Modifier.height(56.dp),
                    onClick = {
                        haptic.medium()
                        if (skipSaveDialog) {
                            if (ImageActions.checkFileExists(filename)) {
                                overwriteDialogFilename = filename
                            } else {
                                imageRepository.saveImage(
                                    context = context,
                                    imageId = imageId,
                                    onSuccess = {},
                                    onError = { errorMsg -> saveErrorMessage = errorMsg })
                            }
                        } else {
                            showSaveDialog = true
                        }
                    },
                    shapes = ButtonDefaults.shapes(
                        shape = RoundedCornerShape(
                            topStart = PillInner,
                            bottomStart = PillInner,
                            topEnd = PillOuter,
                            bottomEnd = PillOuter
                        ), pressedShape = RoundedCornerShape(PillOuter)
                    ),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.inverseOnSurface,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    ),
                    contentPadding = PaddingValues(horizontal = 20.dp),
                ) {
                    Icon(
                        Icons.Filled.Save,
                        contentDescription = stringResource(id = R.string.save),
                    )
                    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                    Text(
                        text = stringResource(id = R.string.save),
                    )
                }
            }
        }
    }

    if (showSaveDialog) {
        SaveImageDialog(
            filename,
            showSaveAllOption,
            false,
            hideOptions = false,
            onDismissRequest = { showSaveDialog = false }) { name, all, skip ->
            if (skip) {
                scope.launch { appPreferences.setSkipSaveDialog(true) }
            }
            if (all) {
                imageRepository.saveAllImages(
                    context = context,
                    onComplete = {},
                    onError = { errorMsg -> saveErrorMessage = errorMsg })
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
        SaveImageDialog(
            fname,
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
            })
    }

    if (isSavingImages) {
        val progress = savingImagesProgress
        LoadingDialog(
            title = stringResource(R.string.saving_images),
            progress = progress?.let { it.first.toFloat() / it.second.toFloat() },
            progressText = progress?.let {
                stringResource(R.string.saving_image_progress, it.first, it.second)
            })
    }
}

@Composable
private fun SingleImageView(bitmap: Bitmap) {
    val context = LocalContext.current
    val appPreferences = remember { AppPreferences(context.applicationContext) }
    val isHapticEnabled by appPreferences.hapticFeedbackEnabled.collectAsState(initial = true)

    val zoomableState = rememberZoomableState(
        ZoomSpec(
            maximum = ZoomLimit(
                factor = 20f,
                overzoomEffect = if (isHapticEnabled) OverzoomEffect.RubberBanding else OverzoomEffect.Disabled
            ), minimum = ZoomLimit(
                factor = 1f,
                overzoomEffect = if (isHapticEnabled) OverzoomEffect.RubberBanding else OverzoomEffect.Disabled
            )
        )
    )

    Box(
        Modifier
            .fillMaxSize()
            .zoomable(zoomableState), Alignment.Center
    ) {
        Image(
            bitmap.asImageBitmap(),
            null,
            Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit,
            filterQuality = FilterQuality.None
        )
    }
}
