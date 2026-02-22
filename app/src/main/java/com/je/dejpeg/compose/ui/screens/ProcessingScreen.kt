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

package com.je.dejpeg.compose.ui.screens

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.je.dejpeg.R
import com.je.dejpeg.compose.ui.components.BaseDialog
import com.je.dejpeg.compose.ui.components.CancelProcessingDialog
import com.je.dejpeg.compose.ui.components.DeprecatedModelWarningDialog
import com.je.dejpeg.compose.ui.components.ErrorAlertDialog
import com.je.dejpeg.compose.ui.components.ImageSourceDialog
import com.je.dejpeg.compose.ui.components.LoadingDialog
import com.je.dejpeg.compose.ui.components.RemoveImageDialog
import com.je.dejpeg.compose.ui.components.SaveImageDialog
import com.je.dejpeg.compose.ui.viewmodel.ImageItem
import com.je.dejpeg.compose.ui.viewmodel.ProcessingUiState
import com.je.dejpeg.compose.ui.viewmodel.ProcessingViewModel
import com.je.dejpeg.compose.utils.HapticFeedbackPerformer
import com.je.dejpeg.compose.utils.ImageActions
import com.je.dejpeg.compose.utils.rememberHapticFeedback
import com.je.dejpeg.data.AppPreferences
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import com.je.dejpeg.data.ProcessingMode

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ProcessingScreen(
    viewModel: ProcessingViewModel,
    onNavigateToBeforeAfter: (String) -> Unit = {},
    onNavigateToBrisque: (String) -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    initialSharedUris: List<Uri> = emptyList(),
    onRemoveSharedUri: (Uri) -> Unit = {},
) {
    val context = LocalContext.current
    val appPreferences = remember { AppPreferences(context.applicationContext) }
    val defaultImageSource by appPreferences.defaultImageSource.collectAsState(initial = null)
    val swapSwipeActions by appPreferences.swapSwipeActions.collectAsState(initial = false)
    
    val images by viewModel.images.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    val globalStrength by viewModel.globalStrength.collectAsState()
    val processingMode by viewModel.processingMode.collectAsState()
    val oidnInputScale by viewModel.oidnInputScale.collectAsState()
    val isOidnMode = processingMode == ProcessingMode.OIDN
    val supportsStrength = if (isOidnMode) true else viewModel.getActiveModelName()?.contains("fbcnn", ignoreCase = true) == true && processingMode == ProcessingMode.ONNX
    val shouldShowNoModelDialog by viewModel.shouldShowNoModelDialog.collectAsState()
    val haptic = rememberHapticFeedback()
    val deprecatedModelWarning by viewModel.deprecatedModelWarning.collectAsState()
    val isLoadingImages by viewModel.isLoadingImages.collectAsState()
    val loadingImagesProgress by viewModel.loadingImagesProgress.collectAsState()
    val processingErrorDialog by viewModel.processingErrorDialog.collectAsState()
    var imageIdToRemove by remember { mutableStateOf<String?>(null) }
    var imageIdToCancel by remember { mutableStateOf<String?>(null) }
    var showImageSourceDialog by remember { mutableStateOf(false) }
    var showCancelAllDialog by remember { mutableStateOf(false) }
    var saveErrorMessage by remember { mutableStateOf<String?>(null) }
    var overwriteDialogState by remember { mutableStateOf<Pair<String, String>?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            imageIdToRemove = null
            imageIdToCancel = null
            showImageSourceDialog = false
            showCancelAllDialog = false
            saveErrorMessage = null
            overwriteDialogState = null
        }
    }

    var headerHeightPx by remember { mutableIntStateOf(0) }

    val handleImageRemoval: (String) -> Unit = { imageId ->
        images.firstOrNull { it.id == imageId }?.let { image ->
            when {
                image.isProcessing && viewModel.isCurrentlyProcessing(imageId) -> imageIdToCancel = imageId
                image.isProcessing && !viewModel.isCurrentlyProcessing(imageId) -> {
                    viewModel.removeImage(imageId, cleanupCache = true)
                }
                image.outputBitmap != null && !image.hasBeenSaved -> imageIdToRemove = imageId
                else -> {
                    image.uri?.let { uri ->
                        try { context.contentResolver.releasePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) { }
                        try { onRemoveSharedUri(uri) } catch (_: Exception) { }
                    }
                    viewModel.removeImage(imageId, cleanupCache = true)
                }
            }
        }
    }

    val performRemoval: (String) -> Unit = { imageId ->
        val targetUri = images.firstOrNull { it.id == imageId }?.uri
        targetUri?.let { uri ->
            try { context.contentResolver.releasePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) { }
            try { onRemoveSharedUri(uri) } catch (_: Exception) { }
        }
        viewModel.removeImage(imageId, force = true, cleanupCache = true)
        imageIdToRemove = null
        imageIdToCancel = null
    }
    
    LaunchedEffect(images) {
        imageIdToRemove = imageIdToRemove?.takeIf { id -> images.any { it.id == id } }
        imageIdToCancel = imageIdToCancel?.takeIf { id -> images.any { it.id == id } }
        overwriteDialogState = overwriteDialogState?.takeIf { (id, _) -> images.any { it.id == id } }
    }

    LaunchedEffect(Unit) {
        viewModel.initialize(context)
        viewModel.serviceHelperRegister()
    }

    val processedShareUris = remember { mutableStateOf(setOf<String>()) }
    LaunchedEffect(initialSharedUris) {
        if (initialSharedUris.isNotEmpty()) {
            val existing = images.mapNotNull { it.uri?.toString() }.toSet()
            val toAdd = initialSharedUris.filter { uri -> uri.toString() !in existing && uri.toString() !in processedShareUris.value }
            if (toAdd.isNotEmpty()) {
                viewModel.addImagesFromUris(context, toAdd)
                processedShareUris.value += toAdd.map { it.toString() }
            }
        }
    }

    val imagePickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uris = mutableListOf<Uri>()
            result.data?.clipData?.let { clipData ->
                for (i in 0 until clipData.itemCount) clipData.getItemAt(i).uri?.let { uris.add(it) }
            } ?: result.data?.data?.let { uris.add(it) } ?: viewModel.getCameraPhotoUri()?.let {
                uris.add(it)
                viewModel.clearCameraPhotoUri()
            }
            viewModel.addImagesFromUris(context, uris)
        }
    }
    LaunchedEffect(Unit) { viewModel.setImagePickerLauncher(imagePickerLauncher) }

    fun importImage() {
        haptic.light()
        when (defaultImageSource) {
            "gallery" -> viewModel.launchGalleryPicker()
            "internal" -> viewModel.launchInternalPhotoPicker()
            "documents" -> viewModel.launchDocumentsPicker()
            "camera" -> viewModel.launchCamera()
            else -> showImageSourceDialog = true
        }
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
                .onSizeChanged { headerHeightPx = it.height },
            Arrangement.SpaceBetween,
            Alignment.CenterVertically
        ) {
            Text(stringResource(R.string.images, images.size), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            FloatingActionButton(
                onClick = { importImage() },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                shape = RoundedCornerShape(16.dp)
            ) { Icon(Icons.Filled.Add, stringResource(R.string.add_images)) }
        }

        if (images.isNotEmpty() && supportsStrength) {
            Card(Modifier.fillMaxWidth().padding(bottom = 16.dp), colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surfaceContainer), shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.padding(12.dp)) {
                    if (isOidnMode) {
                        val displayValue = (oidnInputScale * 100).roundToInt()
                        Text(stringResource(R.string.input_scale, displayValue), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        Spacer(Modifier.height(8.dp))
                        var prevScale by remember { mutableFloatStateOf(oidnInputScale) }
                        Slider(value = oidnInputScale * 100f, onValueChange = { val v = (it / 5).roundToInt() * 5f; val scaled = v / 100f; if (scaled != prevScale) { haptic.light(); prevScale = scaled }; viewModel.setOidnInputScale(scaled) }, valueRange = 0f..100f, steps = 19, modifier = Modifier.fillMaxWidth().height(24.dp))
                    } else {
                        Text(stringResource(R.string.strength, globalStrength.toInt()), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        Spacer(Modifier.height(8.dp))
                        var prevStrength by remember { mutableFloatStateOf(globalStrength) }
                        Slider(value = globalStrength, onValueChange = { val v = (it / 5).roundToInt() * 5f; if (v != prevStrength) { haptic.light(); prevStrength = v }; viewModel.setGlobalStrength(v) }, valueRange = 0f..100f, steps = 19, modifier = Modifier.fillMaxWidth().height(24.dp))
                    }
                }
            }
        }

        if (images.isEmpty()) {
            Box(Modifier.fillMaxSize().weight(1f).offset { IntOffset(0, -(headerHeightPx / 2)) }, contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Spacer(Modifier.height(48.dp))
                    Box(
                    Modifier.width(240.dp).height(180.dp).clip(RoundedCornerShape(20.dp)).clickable { importImage() }.drawBehind {
                        val strokeWidth = 2.dp.toPx()
                        val cornerRadius = 20.dp.toPx()
                        drawRoundRect(Color.Gray, androidx.compose.ui.geometry.Offset(0f, 0f), size, CornerRadius(cornerRadius, cornerRadius), Stroke(strokeWidth, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)))
                    }.padding(32.dp),
                    contentAlignment = Alignment.Center
                    ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(stringResource(R.string.no_images_yet), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(8.dp))
                        Text(stringResource(R.string.tap_to_add_images), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    }
                }
            }
        }
        else {
            CompositionLocalProvider(LocalOverscrollFactory provides null) {
                LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(images, { it.id }) { image ->
                    val swipeState = remember { mutableFloatStateOf(0f) }
                    val hasOutput = image.outputBitmap != null
                    val onRightSwipe = remember(image.id) { { handleImageRemoval(image.id) } }
                    val onProcess = remember(image.id) { { if (!viewModel.hasActiveModel()) viewModel.showNoModelDialog() else viewModel.processImage(image.id) } }
                    val onBrisque = remember(image.id) { { haptic.light(); onNavigateToBrisque(image.id) } }
                    val onClick = remember(image.id) { { onNavigateToBeforeAfter(image.id) } }
                    val onLeftSwipe = remember(image.id, hasOutput) { {
                        if (hasOutput) {
                        if (ImageActions.checkFileExists(image.filename)) overwriteDialogState = Pair(image.id, image.filename)
                        else viewModel.saveImage(context, image.id, onSuccess = { performRemoval(image.id) }, onError = { saveErrorMessage = it })
                        } else onProcess()
                    } }
                    SwipeToDismissWrapper(swipeState, image.isProcessing, hasOutput, onRightSwipe, onLeftSwipe, swapSwipeActions) {
                        ImageCard(image, onRightSwipe, onProcess, onBrisque, onClick, onClick, image.isProcessing, haptic)
                    }
                    }
                }
            }
        }

        if (images.isNotEmpty()){
            Button(
                { if (uiState is ProcessingUiState.Processing) { haptic.heavy(); showCancelAllDialog = true } else { if (!viewModel.hasActiveModel()) viewModel.showNoModelDialog() else { haptic.medium(); viewModel.processImages() } } },
                Modifier.fillMaxWidth().padding(top = 16.dp).height(56.dp),
                colors = if (uiState is ProcessingUiState.Processing) ButtonDefaults.buttonColors(MaterialTheme.colorScheme.error) else ButtonDefaults.buttonColors(MaterialTheme.colorScheme.primary),
                shape = RoundedCornerShape(16.dp)
            ) { Text(if (uiState is ProcessingUiState.Processing) stringResource(R.string.cancel_processing) else stringResource(R.string.process_all), fontSize = 16.sp, fontWeight = FontWeight.Medium) }
        }
    }

    imageIdToRemove?.let { targetId ->
        images.firstOrNull { it.id == targetId }?.let { image ->
            RemoveImageDialog(
                imageFilename = image.filename,
                hasOutput = image.outputBitmap != null,
                imageId = targetId,
                context = context,
                onDismissRequest = { imageIdToRemove = null },
                onRemove = { performRemoval(targetId) },
                onSaveAndRemove = {
                    if (ImageActions.checkFileExists(image.filename)) {
                        imageIdToRemove = null
                        overwriteDialogState = Pair(targetId, image.filename)
                    } else {
                        viewModel.saveImage(
                            context = context,
                            imageId = targetId,
                            onSuccess = { performRemoval(targetId) },
                            onError = { 
                                imageIdToRemove = null
                                saveErrorMessage = it
                            }
                        )
                    }
                }
            )
        } ?: run { imageIdToRemove = null }
    }

    imageIdToCancel?.let { targetId ->
        images.firstOrNull { it.id == targetId }?.let { image ->
            CancelProcessingDialog(
                imageFilename = image.filename,
                onDismissRequest = { imageIdToCancel = null },
                onConfirm = { 
                    viewModel.cancelProcessingForImage(targetId)
                    imageIdToCancel = null
                }
            )
        } ?: run { imageIdToCancel = null }
    }

    if (showImageSourceDialog) {
        ImageSourceDialog(
            onDismiss = { showImageSourceDialog = false },
            onGallerySelected = { viewModel.launchGalleryPicker() },
            onInternalSelected = { viewModel.launchInternalPhotoPicker() },
            onDocumentsSelected = { viewModel.launchDocumentsPicker() },
            onCameraSelected = { viewModel.launchCamera() }
        )
    }

    if (showCancelAllDialog) {
        CancelProcessingDialog(
            imageFilename = null,
            onDismissRequest = { showCancelAllDialog = false },
            onConfirm = {
                viewModel.cancelProcessing()
                showCancelAllDialog = false
            }
        )
    }

    if (shouldShowNoModelDialog) {
        NoModelDialog(
            onDismiss = { viewModel.dismissNoModelDialog() },
            onGoToSettings = {
                haptic.medium()
                viewModel.dismissNoModelDialog()
                onNavigateToSettings()
            }
        )
    }

    deprecatedModelWarning?.let { warning ->
        val activeModelName = viewModel.getActiveModelName() ?: ""
        DeprecatedModelWarningDialog(
            modelName = activeModelName,
            warning = warning,
            onContinue = { viewModel.dismissDeprecatedModelWarning() },
            onGoToSettings = {
                haptic.medium()
                viewModel.dismissDeprecatedModelWarning()
                onNavigateToSettings()
            }
        )
    }

    saveErrorMessage?.let { errorMsg ->
        BaseDialog(
            title = stringResource(R.string.error_saving_image_title),
            message = errorMsg,
            onDismiss = { saveErrorMessage = null },
            confirmButtonText = stringResource(R.string.ok),
            onConfirm = { haptic.light(); saveErrorMessage = null }
        )
    }

    overwriteDialogState?.let { (id, fn) ->
        SaveImageDialog(
            defaultFilename = fn,
            showSaveAllOption = false,
            initialSaveAll = false,
            hideOptions = true,
            onDismissRequest = { overwriteDialogState = null },
            onSave = { name, _, _ ->
                viewModel.saveImage(context, id, name, 
                    onSuccess = { performRemoval(id); overwriteDialogState = null },
                    onError = { overwriteDialogState = null; saveErrorMessage = it }
                )
            }
        )
    }

    processingErrorDialog?.let { errorMsg ->
        val context = LocalContext.current
        ErrorAlertDialog(
            title = stringResource(R.string.error_processing_title),
            errorMessage = errorMsg,
            onDismiss = { viewModel.dismissProcessingErrorDialog() },
            context = context
        )
    }

    if (isLoadingImages) {
        val progress = loadingImagesProgress
        LoadingDialog(
            title = stringResource(R.string.loading_images),
            progress = progress?.let { it.first.toFloat() / it.second.toFloat() },
            progressText = progress?.let { stringResource(R.string.loading_image_progress, it.first, it.second) }
        )
    }
}

@Composable
fun SwipeToDismissWrapper(swipeState: MutableState<Float>, isProcessing: Boolean, hasOutput: Boolean, onRightSwipe: () -> Unit, onLeftSwipe: () -> Unit, swapActions: Boolean = false, content: @Composable () -> Unit) {
    val scope = rememberCoroutineScope()
    val animatedOffset by animateFloatAsState(swipeState.value, label = "swipe")
    var widthPx by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current.density
    val haptic = rememberHapticFeedback()
    var hasStartedDrag by remember { mutableStateOf(false) }
    var hasReachedThreshold by remember { mutableStateOf(false) }
    val swipeThreshold = 0.35f
    val actualRightSwipe = if (swapActions) onLeftSwipe else onRightSwipe
    val actualLeftSwipe = if (swapActions) onRightSwipe else onLeftSwipe
    val currentOnRightSwipe by rememberUpdatedState(actualRightSwipe)
    val currentOnLeftSwipe by rememberUpdatedState(actualLeftSwipe)
    val allowLeftSwipe = !isProcessing
    
    Box(Modifier.fillMaxWidth().height(96.dp).onSizeChanged { widthPx = it.width }) {
        if (animatedOffset > 0f) Box(Modifier.width((animatedOffset / density).dp).height(96.dp).clip(RoundedCornerShape(16.dp)).background(if (swapActions) Color(0xFF4CAF50) else Color(0xFFEF5350)), Alignment.Center) {
            Icon(if (swapActions) (if (hasOutput) Icons.Filled.Save else Icons.Filled.PlayArrow) else (if (isProcessing) Icons.Filled.Close else Icons.Filled.Delete), if (swapActions) (if (hasOutput) "Save" else "Process") else (if (isProcessing) "Cancel" else "Delete"), Modifier.size(28.dp), tint = Color.White)
        }
        if (animatedOffset < 0f && allowLeftSwipe) Box(Modifier.fillMaxWidth().height(96.dp), contentAlignment = Alignment.CenterEnd) {
            Box(Modifier.width((-animatedOffset / density).dp).height(96.dp).clip(RoundedCornerShape(16.dp)).background(if (swapActions) Color(0xFFEF5350) else Color(0xFF4CAF50)), Alignment.Center) {
                Icon(if (swapActions) (Icons.Filled.Delete) else (if (hasOutput) Icons.Filled.Save else Icons.Filled.PlayArrow), if (swapActions) ("Delete") else (if (hasOutput) "Save" else "Process"), Modifier.size(28.dp), tint = Color.White)
            }
        }
        Box(Modifier.fillMaxWidth().height(96.dp).offset { IntOffset(animatedOffset.roundToInt(), 0) }.pointerInput(widthPx, allowLeftSwipe) {
            detectHorizontalDragGestures(
                onDragStart = { if (!hasStartedDrag) { haptic.gestureStart(); hasStartedDrag = true } },
                onHorizontalDrag = { _, dragAmount ->
                    val newValue = swipeState.value + dragAmount
                    swipeState.value = if (allowLeftSwipe) newValue else maxOf(0f, newValue)
                    val absOffset = kotlin.math.abs(swipeState.value)
                    val threshold = widthPx * swipeThreshold
                    if (widthPx > 0 && absOffset > threshold && !hasReachedThreshold) { haptic.medium(); hasReachedThreshold = true } else if (absOffset <= threshold) hasReachedThreshold = false
                },
                onDragEnd = {
                    val absOffset = kotlin.math.abs(swipeState.value)
                    val threshold = widthPx * swipeThreshold
                    if (widthPx > 0 && absOffset > threshold) { haptic.heavy(); if (swipeState.value > 0) currentOnRightSwipe() else if (allowLeftSwipe) currentOnLeftSwipe() }
                    scope.launch { swipeState.value = 0f; hasStartedDrag = false; hasReachedThreshold = false }
                }
            )
        }) { content() }
    }
}

@Composable
fun NoModelDialog(onDismiss: () -> Unit, onGoToSettings: () -> Unit) {
    BaseDialog(
        title = stringResource(R.string.no_model_installed_title),
        message = stringResource(R.string.no_model_installed_desc),
        onDismiss = onDismiss,
        confirmButtonText = stringResource(R.string.go_to_settings),
        onConfirm = onGoToSettings,
        dismissButtonText = stringResource(R.string.cancel),
        onDismissButton = onDismiss
    )
}

@Composable
fun ChunkProgressIndicator(completedChunks: Int, totalChunks: Int) {
    val target = if (totalChunks > 0) (completedChunks.toFloat() / totalChunks.toFloat()).coerceIn(0f, 1f) else 0f
    val animatedProgress by animateFloatAsState(targetValue = target, label = "chunk_progress")
    LinearProgressIndicator(
        progress = { animatedProgress },
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.primary,
        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageCard(image: ImageItem, onRemove: () -> Unit, onProcess: () -> Unit, onBrisque: () -> Unit, onClick: () -> Unit, onBeforeOnly: () -> Unit, isProcessing: Boolean = false, haptic: HapticFeedbackPerformer) {
    Box(Modifier.fillMaxWidth().height(96.dp).clip(RoundedCornerShape(16.dp)).clickable(enabled = !isProcessing) { haptic.light(); if (image.outputBitmap != null) onClick() else onBeforeOnly() }) {
        Card(Modifier.fillMaxSize(), RoundedCornerShape(16.dp), CardDefaults.cardColors(MaterialTheme.colorScheme.surfaceContainer), CardDefaults.cardElevation(2.dp, 4.dp)) {
            Box(Modifier.fillMaxSize().padding(12.dp)) {
                Row(Modifier.fillMaxSize(), Arrangement.spacedBy(12.dp)) {
                    val imageBitmap = remember(image.thumbnailBitmap ?: image.outputBitmap ?: image.inputBitmap) { (image.thumbnailBitmap ?: image.outputBitmap ?: image.inputBitmap).asImageBitmap() }
                    Surface(Modifier.size(72.dp).clip(RoundedCornerShape(12.dp)), color = MaterialTheme.colorScheme.surfaceVariant) { Image(imageBitmap, image.filename, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
                    Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.Top) {
                        Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(8.dp), Alignment.CenterVertically) {
                            Text(image.filename, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 1, modifier = Modifier.weight(1f, false))
                            Text(image.size, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                        }
                        if (image.isProcessing) {
                            Spacer(Modifier.height(6.dp))
                            if (image.totalChunks > 1) ChunkProgressIndicator(image.completedChunks, image.totalChunks) else LinearProgressIndicator(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.primary, trackColor = MaterialTheme.colorScheme.surfaceContainerHighest)
                            Spacer(Modifier.height(2.dp))
                            if (image.progress.isNotEmpty()) Text(image.progress, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary, fontSize = 10.sp, maxLines = 1)
                        } else if (image.outputBitmap != null) {
                            Spacer(Modifier.height(4.dp))
                            Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.primaryContainer) { Text(stringResource(R.string.status_complete_ui), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.Medium, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) }
                        }
                    }
                }
                Row(Modifier.align(Alignment.BottomEnd), Arrangement.spacedBy(0.dp), Alignment.CenterVertically) {
                    if (image.isProcessing) { IconButton({ haptic.heavy(); onRemove() }, Modifier.size(36.dp)) { Icon(Icons.Filled.Close, stringResource(R.string.cancel_processing), tint = Color(0xFFEF5350), modifier = Modifier.size(20.dp)) } }
                    else {
                        IconButton({ haptic.medium(); onProcess() }, Modifier.size(36.dp)) { Icon(Icons.Filled.PlayArrow, stringResource(R.string.process), tint = Color(0xFF4CAF50), modifier = Modifier.size(20.dp)) }
                        IconButton({ haptic.heavy(); onRemove() }, Modifier.size(36.dp)) { Icon(Icons.Filled.Delete, stringResource(R.string.remove), tint = Color(0xFFEF5350), modifier = Modifier.size(18.dp)) }
                        IconButton({ onBrisque() }, Modifier.size(36.dp)) { Box(Modifier.fillMaxSize(), Alignment.Center) { Text("B", fontStyle = FontStyle.Italic, color = MaterialTheme.colorScheme.primary, fontSize = 20.sp, fontWeight = FontWeight.Bold) } }
                    }
                }
            }
        }
    }
}
