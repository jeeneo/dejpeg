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

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
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
import com.je.dejpeg.ExitActivity
import com.je.dejpeg.ModelType
import com.je.dejpeg.R
import com.je.dejpeg.data.AppPreferences
import com.je.dejpeg.data.ImageRepository
import com.je.dejpeg.data.ProcessingMode
import com.je.dejpeg.ui.components.BaseDialog
import com.je.dejpeg.ui.components.CancelProcessingDialog
import com.je.dejpeg.ui.components.ErrorAlertDialog
import com.je.dejpeg.ui.components.ImageSourceDialog
import com.je.dejpeg.ui.components.LoadingDialog
import com.je.dejpeg.ui.components.RemoveImageDialog
import com.je.dejpeg.ui.components.SaveImageDialog
import com.je.dejpeg.ui.viewmodel.ImageItem
import com.je.dejpeg.ui.viewmodel.ProcessingUiState
import com.je.dejpeg.ui.viewmodel.ProcessingViewModel
import com.je.dejpeg.ui.viewmodel.SettingsViewModel
import com.je.dejpeg.utils.HapticFeedbackPerformer
import com.je.dejpeg.utils.helpers.ImageActions
import com.je.dejpeg.utils.rememberHapticFeedback
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.roundToInt

@Suppress("AssignedValueIsNeverRead")
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ProcessingScreen(
    viewModel: ProcessingViewModel,
    settingsViewModel: SettingsViewModel,
    imageRepository: ImageRepository,
    onNavigateToBeforeAfter: (String) -> Unit = {},
    onNavigateToBrisque: (String) -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    initialSharedUris: List<Uri> = emptyList(),
    onRemoveSharedUri: (Uri) -> Unit = {},
    lazyListState: androidx.compose.foundation.lazy.LazyListState = androidx.compose.foundation.lazy.rememberLazyListState(),
) {
    val context = LocalContext.current
    val backExitMessage = stringResource(R.string.back_exit_confirm)
    val appPreferences = remember { AppPreferences(context.applicationContext) }
    val defaultImageSource by appPreferences.defaultImageSource.collectAsState(initial = null)
    val swapSwipeActions by appPreferences.swapSwipeActions.collectAsState(initial = false)

    val images by imageRepository.images.collectAsState()
    val globalStrength by settingsViewModel.globalStrength.collectAsState()
    val processingMode by settingsViewModel.processingMode.collectAsState()
    val oidnInputScale by settingsViewModel.oidnInputScale.collectAsState()
    val isOidnMode = processingMode == ProcessingMode.OIDN
    val activeModelType = if (isOidnMode) ModelType.OIDN else ModelType.ONNX
    val supportsStrength = if (isOidnMode) true else settingsViewModel.getActiveModelName()
        ?.contains("fbcnn", ignoreCase = true) == true && processingMode == ProcessingMode.ONNX
    val shouldShowNoModelDialog by settingsViewModel.shouldShowNoModelDialog.collectAsState()
    val haptic = rememberHapticFeedback()
    val deprecatedModelWarning by settingsViewModel.deprecatedModelWarning.collectAsState()
    val isLoadingImages by imageRepository.isLoadingImages.collectAsState()
    val loadingImagesProgress by imageRepository.loadingImagesProgress.collectAsState()
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
                image.isProcessing && viewModel.isCurrentlyProcessing(imageId) -> imageIdToCancel =
                    imageId

                image.isProcessing && !viewModel.isCurrentlyProcessing(imageId) -> {
                    viewModel.removeImage(imageId, cleanupCache = true)
                }

                image.outputBitmap != null && !image.hasBeenSaved -> imageIdToRemove = imageId
                else -> {
                    image.uri?.let { uri ->
                        try {
                            context.contentResolver.releasePersistableUriPermission(
                                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                            )
                        } catch (_: Exception) {
                        }
                        try {
                            onRemoveSharedUri(uri)
                        } catch (_: Exception) {
                        }
                    }
                    viewModel.removeImage(imageId, cleanupCache = true)
                }
            }
        }
    }

    val performRemoval: (String) -> Unit = { imageId ->
        val targetUri = images.firstOrNull { it.id == imageId }?.uri
        targetUri?.let { uri ->
            try {
                context.contentResolver.releasePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) {
            }
            try {
                onRemoveSharedUri(uri)
            } catch (_: Exception) {
            }
        }
        viewModel.removeImage(imageId, force = true, cleanupCache = true)
        imageIdToRemove = null
        imageIdToCancel = null
    }

    LaunchedEffect(images) {
        imageIdToRemove = imageIdToRemove?.takeIf { id -> images.any { it.id == id } }
        imageIdToCancel = imageIdToCancel?.takeIf { id -> images.any { it.id == id } }
        overwriteDialogState =
            overwriteDialogState?.takeIf { (id, _) -> images.any { it.id == id } }
    }

    LaunchedEffect(Unit) {
        viewModel.initialize(context)
        viewModel.serviceHelperRegister()
    }

    var lastBackPressTime by remember { mutableLongStateOf(0L) }

    BackHandler {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastBackPressTime < 2000) {
            viewModel.cancelProcessing()
            ExitActivity.exitApplication(context)
        } else {
            Toast.makeText(context, backExitMessage, Toast.LENGTH_SHORT).show()
            lastBackPressTime = currentTime
        }
    }

    val processedShareUris = remember { mutableStateOf(setOf<String>()) }
    LaunchedEffect(initialSharedUris) {
        if (initialSharedUris.isNotEmpty()) {
            val existing = images.mapNotNull { it.uri?.toString() }.toSet()
            val toAdd =
                initialSharedUris.filter { uri -> uri.toString() !in existing && uri.toString() !in processedShareUris.value }
            if (toAdd.isNotEmpty()) {
                imageRepository.addImagesFromUris(
                    context, toAdd, settingsViewModel.globalStrength.value / 100f
                )
                processedShareUris.value += toAdd.map { it.toString() }
            }
        }
    }

    val imagePickerLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val uris = mutableListOf<Uri>()
                result.data?.clipData?.let { clipData ->
                    for (i in 0 until clipData.itemCount) clipData.getItemAt(i).uri?.let {
                        uris.add(
                            it
                        )
                    }
                } ?: result.data?.data?.let { uris.add(it) } ?: viewModel.getCameraPhotoUri()?.let {
                    uris.add(it)
                    viewModel.clearCameraPhotoUri()
                }
                imageRepository.addImagesFromUris(
                    context, uris, settingsViewModel.globalStrength.value / 100f
                )
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

    Column(
        Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
                .onSizeChanged { headerHeightPx = it.height },
            Arrangement.SpaceBetween,
            Alignment.CenterVertically
        ) {
            Text(
                stringResource(R.string.images, images.size),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val uiState by viewModel.uiState.collectAsState()
                val isProcessing = uiState is ProcessingUiState.Processing
                val procInteraction =
                    remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                val isProcPressed by procInteraction.collectIsPressedAsState()
                val procCorner by animateFloatAsState(
                    targetValue = if (isProcPressed) 28f else 18f,
                    animationSpec = spring(),
                    label = "proc_corner"
                )
                FloatingActionButton(
                    onClick = {
                        if (isProcessing) {
                            haptic.heavy(); showCancelAllDialog = true
                        } else {
                            if (!settingsViewModel.hasActiveModel(activeModelType)) settingsViewModel.showNoModelDialog()
                            else {
                                haptic.medium(); viewModel.processImages()
                            }
                        }
                    },
                    containerColor = if (isProcessing) MaterialTheme.colorScheme.errorContainer
                    else MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = if (isProcessing) MaterialTheme.colorScheme.onErrorContainer
                    else MaterialTheme.colorScheme.onSecondaryContainer,
                    shape = RoundedCornerShape(procCorner.dp),
                    interactionSource = procInteraction
                ) {
                    androidx.compose.animation.Crossfade(
                        isProcessing, label = "proc_icon"
                    ) { processing ->
                        Icon(if (processing) Icons.Filled.Close else Icons.Filled.PlayArrow, null)
                    }
                }

                val addInteraction =
                    remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                val isAddPressed by addInteraction.collectIsPressedAsState()
                val addCorner by animateFloatAsState(
                    targetValue = if (isAddPressed) 28f else 18f,
                    animationSpec = spring(),
                    label = "add_corner"
                )
                FloatingActionButton(
                    onClick = { importImage() },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    shape = RoundedCornerShape(addCorner.dp),
                    interactionSource = addInteraction
                ) {
                    Icon(Icons.Filled.Add, stringResource(R.string.add_images))
                }
            }
        }

        if (images.isNotEmpty() && supportsStrength) {
            Card(
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surfaceContainer),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(Modifier.padding(12.dp)) {
                    if (isOidnMode) {
                        val displayValue =
                            if (oidnInputScale == 0f) stringResource(R.string.text_auto) else String.format(
                                Locale.ROOT, "%.1f", oidnInputScale
                            )
                        Text(
                            stringResource(R.string.input_scale, displayValue),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(Modifier.height(8.dp))
                        var prevScale by remember { mutableFloatStateOf(oidnInputScale) }
                        Slider(
                            value = oidnInputScale,
                            onValueChange = {
                                val v = (it * 2).roundToInt() / 2f; if (v != prevScale) {
                                haptic.light(); prevScale = v
                            }; settingsViewModel.setOidnInputScale(v)
                            },
                            valueRange = 0f..10f,
                            steps = 19,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(24.dp)
                        )
                    } else {
                        Text(
                            stringResource(R.string.strength, globalStrength.toInt()),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(Modifier.height(8.dp))
                        var prevStrength by remember { mutableFloatStateOf(globalStrength) }
                        Slider(
                            value = globalStrength,
                            onValueChange = {
                                val v = (it / 5).roundToInt() * 5f; if (v != prevStrength) {
                                haptic.light(); prevStrength = v
                            }; settingsViewModel.setGlobalStrength(v)
                            },
                            valueRange = 0f..100f,
                            steps = 19,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(24.dp)
                        )
                    }
                }
            }
        }

        if (images.isEmpty()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .weight(1f)
                    .offset { IntOffset(0, -(headerHeightPx / 2)) },
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Spacer(Modifier.height(48.dp))
                    val buttonInteractionSource =
                        remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                    val isButtonAreaPressed by buttonInteractionSource.collectIsPressedAsState()
                    val animatedCornerRadius by animateFloatAsState(
                        targetValue = if (isButtonAreaPressed) 24f else 12f, animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMedium
                        ), label = "button_corner"
                    )
                    val emptyStateDashedColor = MaterialTheme.colorScheme.surfaceVariant
                    Box(
                        Modifier
                            .width(280.dp)
                            .height(220.dp)
                            .clip(RoundedCornerShape(28.dp))
                            .clickable(
                                interactionSource = buttonInteractionSource, indication = null
                            ) { importImage() }
                            .drawBehind {
                                val strokeWidth = 2.dp.toPx()
                                val cornerRadius = 28.dp.toPx()
                                drawRoundRect(
                                    emptyStateDashedColor,
                                    androidx.compose.ui.geometry.Offset(0f, 0f),
                                    size,
                                    CornerRadius(cornerRadius, cornerRadius),
                                    Stroke(
                                        strokeWidth, pathEffect = PathEffect.dashPathEffect(
                                            floatArrayOf(10f, 10f), 0f
                                        )
                                    )
                                )
                            }
                            .padding(20.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Outlined.AddPhotoAlternate,
                                stringResource(R.string.add_images),
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(
                                stringResource(R.string.no_images_yet),
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                stringResource(R.string.tap_to_add_images),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(12.dp))
                            FilledTonalButton(
                                onClick = { importImage() },
                                shape = RoundedCornerShape(animatedCornerRadius.dp),
                                interactionSource = buttonInteractionSource
                            ) {
                                Text(
                                    stringResource(R.string.add_images),
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                        }
                    }
                }
            }
        } else {
            CompositionLocalProvider(LocalOverscrollFactory provides null) {
                LazyColumn(
                    state = lazyListState,
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(images, { it.id }) { image ->
                        val swipeState = remember { mutableFloatStateOf(0f) }
                        val hasOutput = image.outputBitmap != null
                        val onRightSwipe = remember(image.id) { { handleImageRemoval(image.id) } }
                        val onProcess = remember(image.id) {
                            {
                                if (!settingsViewModel.hasActiveModel(activeModelType)) settingsViewModel.showNoModelDialog() else viewModel.processImage(
                                    image.id
                                )
                            }
                        }
                        val onBrisque =
                            remember(image.id) { { haptic.light(); onNavigateToBrisque(image.id) } }
                        val onClick = remember(image.id) { { onNavigateToBeforeAfter(image.id) } }
                        val onLeftSwipe = remember(image.id, hasOutput) {
                            {
                                if (hasOutput) {
                                    if (ImageActions.checkFileExists(image.filename)) overwriteDialogState =
                                        Pair(image.id, image.filename)
                                    else imageRepository.saveImage(
                                        context,
                                        image.id,
                                        onSuccess = { performRemoval(image.id) },
                                        onError = { saveErrorMessage = it })
                                } else onProcess()
                            }
                        }
                        SwipeToDismissWrapper(
                            swipeState,
                            image.isProcessing,
                            hasOutput,
                            onRightSwipe,
                            onLeftSwipe,
                            swapSwipeActions
                        ) {
                            ImageCard(
                                image,
                                onRightSwipe,
                                onProcess,
                                onBrisque,
                                onClick,
                                onClick,
                                image.isProcessing,
                                haptic
                            )
                        }
                    }
                }
            }
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
                        imageRepository.saveImage(
                            context = context,
                            imageId = targetId,
                            onSuccess = { performRemoval(targetId) },
                            onError = {
                                imageIdToRemove = null
                                saveErrorMessage = it
                            })
                    }
                })
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
                })
        } ?: run { imageIdToCancel = null }
    }

    if (showImageSourceDialog) {
        ImageSourceDialog(
            onDismiss = { showImageSourceDialog = false },
            onGallerySelected = { viewModel.launchGalleryPicker() },
            onInternalSelected = { viewModel.launchInternalPhotoPicker() },
            onDocumentsSelected = { viewModel.launchDocumentsPicker() },
            onCameraSelected = { viewModel.launchCamera() })
    }

    if (showCancelAllDialog) {
        CancelProcessingDialog(
            imageFilename = null,
            onDismissRequest = { showCancelAllDialog = false },
            onConfirm = {
                viewModel.cancelProcessing()
                showCancelAllDialog = false
            })
    }

    if (shouldShowNoModelDialog) {
        NoModelDialog(onDismiss = { settingsViewModel.dismissNoModelDialog() }, onGoToSettings = {
            haptic.medium()
            settingsViewModel.dismissNoModelDialog()
            onNavigateToSettings()
        })
    }

    saveErrorMessage?.let { errorMsg ->
        BaseDialog(
            title = stringResource(R.string.error_saving_image_title),
            message = errorMsg,
            onDismiss = { saveErrorMessage = null },
            confirmButtonText = stringResource(R.string.ok),
            onConfirm = { haptic.light(); saveErrorMessage = null })
    }

    overwriteDialogState?.let { (id, fn) ->
        SaveImageDialog(
            defaultFilename = fn,
            showSaveAllOption = false,
            initialSaveAll = false,
            hideOptions = true,
            onDismissRequest = { overwriteDialogState = null },
            onSave = { name, _, _ ->
                imageRepository.saveImage(
                    context,
                    id,
                    name,
                    onSuccess = { performRemoval(id); overwriteDialogState = null },
                    onError = { overwriteDialogState = null; saveErrorMessage = it })
            })
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
            progressText = progress?.let {
                stringResource(
                    R.string.loading_image_progress, it.first, it.second
                )
            })
    }
}

@Composable
fun SwipeToDismissWrapper(
    swipeState: MutableState<Float>,
    isProcessing: Boolean,
    hasOutput: Boolean,
    onRightSwipe: () -> Unit,
    onLeftSwipe: () -> Unit,
    swapActions: Boolean = false,
    content: @Composable () -> Unit
) {
    val scope = rememberCoroutineScope()
    val animatedOffset by animateFloatAsState(
        targetValue = swipeState.value, animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium
        ), label = "swipe"
    )
    var widthPx by remember { mutableIntStateOf(0) }
    var heightPx by remember { mutableIntStateOf(0) }
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

    Box(
        Modifier
            .fillMaxWidth()
            .onSizeChanged { widthPx = it.width; heightPx = it.height }) {
        if (animatedOffset > 0f) Box(
            Modifier
                .width((animatedOffset / density).dp)
                .height((heightPx / density).dp)
                .clip(RoundedCornerShape(16.dp))
                .background(if (swapActions) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.errorContainer),
            Alignment.Center
        ) {
            Icon(
                if (swapActions) (if (hasOutput) Icons.Filled.Save else Icons.Filled.PlayArrow) else (if (isProcessing) Icons.Filled.Close else Icons.Filled.Delete),
                if (swapActions) (if (hasOutput) "Save" else "Process") else (if (isProcessing) "Cancel" else "Delete"),
                Modifier.size(28.dp),
                tint = if (swapActions) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onErrorContainer
            )
        }
        if (animatedOffset < 0f && allowLeftSwipe) Box(
            Modifier
                .fillMaxWidth()
                .height((heightPx / density).dp),
            contentAlignment = Alignment.CenterEnd
        ) {
            Box(
                Modifier
                    .width((-animatedOffset / density).dp)
                    .height((heightPx / density).dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (swapActions) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer),
                Alignment.Center
            ) {
                Icon(
                    if (swapActions) Icons.Filled.Delete else (if (hasOutput) Icons.Filled.Save else Icons.Filled.PlayArrow),
                    if (swapActions) "Delete" else (if (hasOutput) "Save" else "Process"),
                    Modifier.size(28.dp),
                    tint = if (swapActions) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .offset { IntOffset(animatedOffset.roundToInt(), 0) }
                .pointerInput(widthPx, allowLeftSwipe) {
                    detectHorizontalDragGestures(onDragStart = {
                        if (!hasStartedDrag) {
                            haptic.gestureStart(); hasStartedDrag = true
                        }
                    }, onHorizontalDrag = { _, dragAmount ->
                        val newValue = swipeState.value + dragAmount
                        swipeState.value = if (allowLeftSwipe) newValue else maxOf(0f, newValue)
                        val absOffset = kotlin.math.abs(swipeState.value)
                        val threshold = widthPx * swipeThreshold
                        if (widthPx > 0 && absOffset > threshold && !hasReachedThreshold) {
                            haptic.medium(); hasReachedThreshold = true
                        } else if (absOffset <= threshold) hasReachedThreshold = false
                    }, onDragEnd = {
                        val absOffset = kotlin.math.abs(swipeState.value)
                        val threshold = widthPx * swipeThreshold
                        if (widthPx > 0 && absOffset > threshold) {
                            haptic.heavy()
                            if (swipeState.value > 0) currentOnRightSwipe()
                            else if (allowLeftSwipe) currentOnLeftSwipe()
                        }
                        scope.launch {
                            swipeState.value = 0f
                            hasStartedDrag = false
                            hasReachedThreshold = false
                        }
                    })
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

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ChunkProgressIndicator(completedChunks: Int, totalChunks: Int) {
    val target = if (totalChunks > 0) (completedChunks.toFloat() / totalChunks.toFloat()).coerceIn(
        0f, 1f
    ) else 0f
    val animatedProgress by animateFloatAsState(
        targetValue = target,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium
        ),
        label = "chunk_progress",
    )
    LinearWavyProgressIndicator(
        progress = { animatedProgress },
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.primary,
        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        // amplitude = { progress -> progress },
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ImageCard(
    image: ImageItem,
    onRemove: () -> Unit,
    onProcess: () -> Unit,
    onBrisque: () -> Unit,
    onClick: () -> Unit,
    onBeforeOnly: () -> Unit,
    isProcessing: Boolean = false,
    haptic: HapticFeedbackPerformer
) {
    val cardInteractionSource =
        remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val isCardPressed by cardInteractionSource.collectIsPressedAsState()
    val animatedBadgeCorner by animateFloatAsState(
        targetValue = if (isCardPressed) 24f else 8f, animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium
        ), label = "badge_corner"
    )
    val animatedCardCorner by animateFloatAsState(
        targetValue = if (isCardPressed) 24f else 16f, animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium
        ), label = "card_corner"
    )

    val processInteraction =
        remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val removeInteraction =
        remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val brisqueInteraction =
        remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val cancelInteraction =
        remember { androidx.compose.foundation.interaction.MutableInteractionSource() }

    val isProcessPressed by processInteraction.collectIsPressedAsState()
    val isRemovePressed by removeInteraction.collectIsPressedAsState()
    val isBrisquePressed by brisqueInteraction.collectIsPressedAsState()
    val isCancelPressed by cancelInteraction.collectIsPressedAsState()

    val removeStartCorner by animateFloatAsState(
        targetValue = if (isRemovePressed) 28f else 6f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "remove_start"
    )
    val removeEndCorner by animateFloatAsState(
        targetValue = if (isRemovePressed) 28f else 6f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "remove_end"
    )

    val brisqueStartCorner by animateFloatAsState(
        targetValue = if (isBrisquePressed) 28f else 6f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "brisque_start"
    )
    val brisqueEndCorner by animateFloatAsState(
        targetValue = 28f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "brisque_end"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(animatedCardCorner.dp))
            .clickable(
                interactionSource = cardInteractionSource,
                indication = ripple(),
                enabled = !isProcessing
            ) {
                haptic.light()
                if (image.outputBitmap != null) onClick() else onBeforeOnly()
            },
        shape = RoundedCornerShape(animatedCardCorner.dp),
        colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val imageBitmap = remember(
                image.thumbnailBitmap ?: image.outputBitmap ?: image.inputBitmap
            ) {
                (image.thumbnailBitmap ?: image.outputBitmap ?: image.inputBitmap).asImageBitmap()
            }
            Surface(
                Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(12.dp)),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Image(
                    imageBitmap,
                    image.filename,
                    Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
            Column(
                Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        image.filename,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        modifier = Modifier.weight(1f, false)
                    )
                    Text(
                        image.size,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (image.isProcessing) {
                    if (image.totalChunks > 1) ChunkProgressIndicator(
                        image.completedChunks, image.totalChunks
                    )
                    else LinearWavyProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                    )
                    if (image.progress.isNotEmpty()) Text(
                        image.progress,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1
                    )
                } else if (image.outputBitmap != null) {
                    Surface(
                        shape = RoundedCornerShape(animatedBadgeCorner.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            stringResource(R.string.status_complete_ui),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }

        HorizontalDivider(
            Modifier.padding(horizontal = 12.dp),
            thickness = 0.5.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
        )

        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val isActive = image.isProcessing
            val morphContainerColor by animateColorAsState(
                targetValue = if (isActive) MaterialTheme.colorScheme.errorContainer
                else MaterialTheme.colorScheme.secondaryContainer,
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                label = "morph_container"
            )
            val morphContentColor by animateColorAsState(
                targetValue = if (isActive) MaterialTheme.colorScheme.onErrorContainer
                else MaterialTheme.colorScheme.onSecondaryContainer,
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                label = "morph_content"
            )
            val morphStartCorner by animateFloatAsState(
                targetValue = when {
                    isActive && isCancelPressed -> 6f
                    isProcessPressed -> 28f
                    else -> 28f
                },
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                label = "morph_start_corner"
            )
            val morphEndCorner by animateFloatAsState(
                targetValue = when {
                    isActive && isCancelPressed -> 6f
                    isActive -> 28f
                    isProcessPressed -> 28f
                    else -> 6f
                },
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                label = "morph_end_corner"
            )

            Button(
                onClick = {
                    if (isActive) {
                        haptic.heavy(); onRemove()
                    } else {
                        haptic.medium(); onProcess()
                    }
                }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(
                    topStart = morphStartCorner.dp,
                    bottomStart = morphStartCorner.dp,
                    topEnd = morphEndCorner.dp,
                    bottomEnd = morphEndCorner.dp
                ), colors = ButtonDefaults.buttonColors(
                    containerColor = morphContainerColor, contentColor = morphContentColor
                ), interactionSource = if (isActive) cancelInteraction else processInteraction
            ) {
                androidx.compose.animation.Crossfade(
                    targetState = isActive,
                    animationSpec = spring(stiffness = Spring.StiffnessMedium),
                    label = "morph_icon"
                ) { processing ->
                    Icon(
                        if (processing) Icons.Filled.Close else Icons.Filled.PlayArrow,
                        null,
                        Modifier.size(16.dp)
                    )
                }
                Spacer(Modifier.width(4.dp))
                androidx.compose.animation.Crossfade(
                    targetState = isActive,
                    animationSpec = spring(stiffness = Spring.StiffnessMedium),
                    label = "morph_label"
                ) { processing ->
                    Text(
                        if (processing) stringResource(R.string.cancel_processing)
                        else stringResource(R.string.process),
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }

            androidx.compose.animation.AnimatedVisibility(
                visible = !isActive,
                enter = androidx.compose.animation.fadeIn(spring(stiffness = Spring.StiffnessMedium)) + androidx.compose.animation.expandHorizontally(
                    spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                    expandFrom = Alignment.Start
                ),
                exit = androidx.compose.animation.fadeOut(spring(stiffness = Spring.StiffnessMedium)) + androidx.compose.animation.shrinkHorizontally(
                    spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                    shrinkTowards = Alignment.Start
                )
            ) {
                Button(
                    onClick = { haptic.heavy(); onRemove() },
                    modifier = Modifier.width(140.dp),
                    shape = RoundedCornerShape(
                        topStart = removeStartCorner.dp,
                        bottomStart = removeStartCorner.dp,
                        topEnd = removeEndCorner.dp,
                        bottomEnd = removeEndCorner.dp
                    ),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    ),
                    interactionSource = removeInteraction
                ) {
                    Icon(Icons.Filled.Delete, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(
                        stringResource(R.string.remove), style = MaterialTheme.typography.labelLarge
                    )
                }
            }

            androidx.compose.animation.AnimatedVisibility(
                visible = !isActive,
                enter = androidx.compose.animation.fadeIn(spring(stiffness = Spring.StiffnessMedium)) + androidx.compose.animation.expandHorizontally(
                    spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                    expandFrom = Alignment.Start
                ),
                exit = androidx.compose.animation.fadeOut(spring(stiffness = Spring.StiffnessMedium)) + androidx.compose.animation.shrinkHorizontally(
                    spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                    shrinkTowards = Alignment.Start
                )
            ) {
                Button(
                    onClick = { haptic.light(); onBrisque() },
                    modifier = Modifier.width(48.dp),
                    shape = RoundedCornerShape(
                        topStart = brisqueStartCorner.dp,
                        bottomStart = brisqueStartCorner.dp,
                        topEnd = brisqueEndCorner.dp,
                        bottomEnd = brisqueEndCorner.dp
                    ),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    contentPadding = PaddingValues(0.dp),
                    interactionSource = brisqueInteraction
                ) {
                    Text(
                        "B",
                        fontStyle = FontStyle.Italic,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}
