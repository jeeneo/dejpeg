/* SPDX-FileCopyrightText: 2025 - 2026 dryerlint <https://codeberg.org/dryerlint>
 * SPDX-License-Identifier: GNU Affero General Public License v3.0 or later
 */

package com.je.dejpeg.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import com.je.dejpeg.ui.components.PreparingShareDialog
import com.je.dejpeg.ui.components.SaveImageDialog
import com.je.dejpeg.ui.viewmodel.ProcessingViewModel
import com.je.dejpeg.ui.viewmodel.SaveState
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ImageScreen(
    viewModel: ProcessingViewModel,
    imageRepository: ImageRepository,
    imageId: String,
    onBack: () -> Unit = {},
    showAfter: Boolean = true
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptic = rememberHapticFeedback()
    val appPreferences = remember { AppPreferences(context.applicationContext) }
    val showSaveDialog by appPreferences.showSaveDialog.collectAsState(initial = true)

    val images by imageRepository.images.collectAsState()
    val image = images.firstOrNull { it.id == imageId }
    var saveDialogState by remember { mutableStateOf<Pair<String, String>?>(null) }
    var overwriteDialogState by remember { mutableStateOf<Pair<String, String>?>(null) }
    var isPreparingShare by remember { mutableStateOf(false) }
    val saveState by viewModel.saveState.collectAsState()

    val saveOrPrompt = rememberSaveOrPrompt(
        showSaveDialog = showSaveDialog,
        context = context,
        viewModel = viewModel,
        performRemoval = { _ -> /* nom */ },
        setSaveDialogState = { p -> saveDialogState = p },
        setOverwriteDialogState = { p -> overwriteDialogState = p }
    )

    if (image == null) {
        LaunchedEffect(Unit) { onBack() }
        return
    }

    val beforeBitmap = image.inputBitmap
    val afterBitmap = if (showAfter) image.outputBitmap else null
    val filename = image.filename
    val showSaveAllOption = images.any { it.outputBitmap != null }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
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
                            isPreparingShare = true
                            ImageActions.shareImage(
                                context = context,
                                bitmap = afterBitmap ?: beforeBitmap,
                                onReady = { isPreparingShare = false },
                                onError = { isPreparingShare = false }
                            )
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
                        onClick = { haptic.medium(); saveOrPrompt(imageId, filename) },
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

        if (isPreparingShare) PreparingShareDialog()

        saveDialogState?.let { (id, fn) ->
            SaveImageDialog(
                defaultFilename = fn,
                showSaveAllOption = showSaveAllOption,
                initialSaveAll = false,
                hideOptions = false,
                onDismissRequest = { saveDialogState = null }) { name, all, skip ->
                saveDialogState = null
                if (skip) scope.launch { appPreferences.setShowSaveDialog(false) }
                if (all) {
                    val imageIds = images.filter { it.outputBitmap != null }.map { it.id }
                    if (imageIds.isNotEmpty()) viewModel.saveImage(context, imageIds)
                } else {
                    if (ImageActions.checkFileExists(context, name)) {
                        overwriteDialogState = Pair(id, name)
                    } else {
                        viewModel.saveImage(
                            context = context,
                            imageIds = listOf(id),
                            baseFilename = name
                        )
                    }
                }
            }
        }

        overwriteDialogState?.let { (id, fname) ->
            SaveImageDialog(
                defaultFilename = fname,
                showSaveAllOption = false,
                initialSaveAll = false,
                hideOptions = true,
                onDismissRequest = { overwriteDialogState = null }) { name, _, _ ->
                viewModel.saveImage(
                    context = context,
                    imageIds = listOf(id),
                    baseFilename = name
                )
                overwriteDialogState = null
            }
        }
        (saveState as? SaveState.Error)?.let { err ->
            AlertDialog(
                onDismissRequest = { viewModel.dismissSaveError() },
                title = { Text(stringResource(R.string.error_saving_image_title)) },
                text = { Text(err.message) },
                confirmButton = {
                    TextButton(onClick = { viewModel.dismissSaveError() }) {
                        Text(stringResource(R.string.ok))
                    }
                })
        }

        (saveState as? SaveState.Saving)?.let { state ->
            SaveProgressDialog(state)
        }
    }
}

// yeah but you're leaking memory everywhere
// ill just restart apache every 20 requests

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
