/*
 * SPDX-FileCopyrightText: 2025 - 2026 dryerlint <https://codeberg.org/dryerlint>
 * SPDX-License-Identifier: GNU Affero General Public License v3.0 or later
 */

@file:Suppress("SpellCheckingInspection", "AssignedValueIsNeverRead")

package com.je.dejpeg.ui.screens

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.animateColor
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import androidx.compose.ui.window.DialogProperties
import com.je.dejpeg.ModelType
import com.je.dejpeg.R
import com.je.dejpeg.data.AppPreferences
import com.je.dejpeg.data.ImageRepository
import com.je.dejpeg.data.ProcessingMode
import com.je.dejpeg.ui.components.CancelProcessingDialog
import com.je.dejpeg.ui.components.ErrorAlertDialog
import com.je.dejpeg.ui.components.ImageSourceDialog
import com.je.dejpeg.ui.components.LoadingDialog
import com.je.dejpeg.ui.components.RemoveImageDialog
import com.je.dejpeg.ui.components.SaveImageDialog
import com.je.dejpeg.ui.components.SnackbarDuration
import com.je.dejpeg.ui.components.SnackySnackbarController
import com.je.dejpeg.ui.components.SnackySnackbarEvents
import com.je.dejpeg.ui.components.rememberMaterialPressState
import com.je.dejpeg.ui.viewmodel.ImageItem
import com.je.dejpeg.ui.viewmodel.ProcessingUiState
import com.je.dejpeg.ui.viewmodel.ProcessingViewModel
import com.je.dejpeg.ui.viewmodel.SaveState
import com.je.dejpeg.ui.viewmodel.SettingsViewModel
import com.je.dejpeg.utils.HapticFeedbackPerformer
import com.je.dejpeg.utils.helpers.ImageActions
import com.je.dejpeg.utils.rememberHapticFeedback
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.roundToInt

private enum class CardState { Idle, Processing, Complete, Stale }

@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalFoundationApi::class,
    ExperimentalAnimationApi::class
)

private val springStandard = spring<Float>(
    dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium
)

// im not a real programmer, i throw together things until it works then i move on.
@Composable
fun ProcessingScreen(
    viewModel: ProcessingViewModel,
    settingsViewModel: SettingsViewModel,
    imageRepository: ImageRepository,
    onNavigateToBeforeAfter: (String) -> Unit = {},
    onNavigateToBrisque: (String) -> Unit = {},
    initialSharedUris: List<Uri> = emptyList(),
    onRemoveSharedUri: (Uri) -> Unit = {},
    lazyListState: androidx.compose.foundation.lazy.LazyListState = androidx.compose.foundation.lazy.rememberLazyListState(),
) {
    val context = LocalContext.current
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
    val haptic = rememberHapticFeedback()
    val noModelMessage = stringResource(R.string.no_model_installed_title)
    val scope = rememberCoroutineScope()
    val isLoadingImages by imageRepository.isLoadingImages.collectAsState()
    val loadingImagesProgress by imageRepository.loadingImagesProgress.collectAsState()
    val processingErrorDialog by viewModel.processingErrorDialog.collectAsState()
    var imageIdToRemove by remember { mutableStateOf<String?>(null) }
    var imageIdToCancel by remember { mutableStateOf<String?>(null) }
    var showImageSourceDialog by remember { mutableStateOf(false) }
    var showCancelAllDialog by remember { mutableStateOf(false) }
    var saveDialogState by remember { mutableStateOf<Pair<String, String>?>(null) }
    var overwriteDialogState by remember { mutableStateOf<Pair<String, String>?>(null) }
    val showSaveDialog by appPreferences.showSaveDialog.collectAsState(initial = true)

    val performRemoval: (String) -> Unit = { imageId ->
        val targetUri = images.firstOrNull { it.id == imageId }?.uri
        targetUri?.let { uri -> releaseUri(uri, context, onRemoveSharedUri) }
        viewModel.removeImage(imageId, force = true, cleanupCache = true)
        imageIdToRemove = null
        imageIdToCancel = null
    }

    val handleImageRemoval: (String) -> Unit = { imageId ->
        images.firstOrNull { it.id == imageId }?.let { image ->
            when {
                image.isProcessing && viewModel.isCurrentlyProcessing(imageId) -> imageIdToCancel =
                    imageId

                image.isProcessing && !viewModel.isCurrentlyProcessing(imageId) -> performRemoval(
                    imageId
                )

                image.outputBitmap != null && !image.hasBeenSaved -> imageIdToRemove = imageId
                else -> performRemoval(imageId)
            }
        }
    }

    fun tryProcess(block: () -> Unit) {
        if (!settingsViewModel.hasActiveModel(activeModelType)) scope.launch {
            SnackySnackbarController.pushEvent(
                SnackySnackbarEvents.MessageEvent(
                    message = noModelMessage, duration = SnackbarDuration.Long
                )
            )
        } else block()
    }

    fun <T> Pair<String, T>?.prune(images: List<ImageItem>): Pair<String, T>? =
        this?.takeIf { (id, _) -> images.any { it.id == id } }

    val saveOrPrompt = rememberSaveOrPrompt(
        showSaveDialog = showSaveDialog,
        context = context,
        viewModel = viewModel,
        performRemoval = performRemoval,
        setSaveDialogState = { p -> saveDialogState = p },
        setOverwriteDialogState = { p -> overwriteDialogState = p })

    LaunchedEffect(images) {
        imageIdToRemove = imageIdToRemove?.takeIf { id -> images.any { it.id == id } }
        imageIdToCancel = imageIdToCancel?.takeIf { id -> images.any { it.id == id } }
        overwriteDialogState = overwriteDialogState.prune(images)
        saveDialogState = saveDialogState.prune(images)
    }

    LaunchedEffect(Unit) {
        viewModel.initialize(context)
        viewModel.serviceHelperRegister()
    }

    LaunchedEffect(initialSharedUris) {
        if (initialSharedUris.isNotEmpty()) {
            val existing = images.mapNotNull { it.uri?.toString() }.toSet()
            val toAdd = initialSharedUris.filter { it.toString() !in existing }
            if (toAdd.isNotEmpty()) {
                imageRepository.addImagesFromUris(context, toAdd)
            }
            imageRepository.sharedUris.value = emptyList()
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
                imageRepository.addImagesFromUris(context, uris)
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
            .padding(start = 16.dp, end = 16.dp, top = 16.dp)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
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
                val allComplete =
                    images.isNotEmpty() && images.all { it.outputBitmap != null && !it.isOutputStale && !it.isProcessing }
                val procInteraction = remember { MutableInteractionSource() }
                val procPress by rememberMaterialPressState(procInteraction)

                val fabCorner = lerp(if (allComplete) 16f else 18f, 28f, procPress)

                val fabWidthDp by animateDpAsState(
                    targetValue = if (allComplete) 160.dp else 56.dp, animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMedium
                    ), label = "fab_width"
                )

                val fabContainerColor by animateColorAsState(
                    targetValue = if (isProcessing) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMedium
                    ),
                    label = "fab_container"
                )

                val fabContentColor by animateColorAsState(
                    targetValue = if (isProcessing) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMedium
                    ),
                    label = "fab_content"
                )

                if (images.isNotEmpty()) {
                    FloatingActionButton(
                        onClick = {
                            haptic.medium()
                            if (isProcessing) {
                                showCancelAllDialog = true
                            } else if (allComplete) {
                                val imageIds =
                                    images.filter { it.outputBitmap != null }.map { it.id }
                                val conflicting =
                                    images.filter { it.outputBitmap != null }.firstOrNull {
                                        ImageActions.checkFileExists(context, it.filename)
                                    }
                                if (conflicting != null) {
                                    saveOrPrompt(conflicting.id, conflicting.filename)
                                } else {
                                    viewModel.saveImage(
                                        context = context, imageIds = imageIds, onComplete = {
                                            imageIds.forEach { id -> performRemoval(id) }
                                        })
                                }
                            } else {
                                tryProcess { haptic.medium(); viewModel.processImages() }
                            }
                        },
                        containerColor = fabContainerColor,
                        contentColor = fabContentColor,
                        shape = RoundedCornerShape(fabCorner.dp),
                        interactionSource = procInteraction,
                        modifier = Modifier
                            .height(56.dp)
                            .widthIn(min = 56.dp)
                            .width(fabWidthDp)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            androidx.compose.animation.AnimatedContent(
                                targetState = when {
                                    isProcessing -> 0
                                    allComplete -> 1
                                    else -> 2
                                }, label = "proc_icon", transitionSpec = {
                                    fadeIn(animationSpec = spring(stiffness = Spring.StiffnessMedium)).togetherWith(
                                        fadeOut(animationSpec = spring(stiffness = Spring.StiffnessMedium))
                                    )
                                }, modifier = Modifier.size(24.dp)
                            ) { state ->
                                Icon(
                                    when (state) {
                                        0 -> Icons.Filled.Close
                                        1 -> Icons.Filled.Save
                                        else -> Icons.Filled.PlayArrow
                                    }, null
                                )
                            }
                            AnimatedVisibility(
                                visible = allComplete,
                                enter = fadeIn(spring(stiffness = Spring.StiffnessMedium)) + expandHorizontally(
                                    spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                                    expandFrom = Alignment.Start
                                ),
                                exit = fadeOut(spring(stiffness = Spring.StiffnessMedium)) + shrinkHorizontally(
                                    spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                                    shrinkTowards = Alignment.Start
                                )
                            ) {
                                Row {
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        stringResource(R.string.save_all),
                                        style = MaterialTheme.typography.labelLarge
                                    )
                                }
                            }
                        }
                    }
                }

                val addInteraction = remember { MutableInteractionSource() }
                val addPress by rememberMaterialPressState(addInteraction)
                val addCorner = lerp(18f, 28f, addPress)
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
                    .padding(
                        bottom = WindowInsets.navigationBars.asPaddingValues()
                            .calculateBottomPadding() + 80.dp
                    ), contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val buttonInteractionSource = remember { MutableInteractionSource() }
                    val buttonAreaPress by rememberMaterialPressState(buttonInteractionSource)
                    val animatedCornerRadius = lerp(12f, 24f, buttonAreaPress)
                    Box(
                        Modifier
                            .width(280.dp)
                            .height(220.dp)
                            .clip(RoundedCornerShape(28.dp))
                            .clickable(
                                interactionSource = buttonInteractionSource, indication = null
                            ) { importImage() }
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
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(
                        bottom = WindowInsets.navigationBars.asPaddingValues()
                            .calculateBottomPadding() + 8.dp
                    )
                ) {
                    items(images, { it.id }) { image ->
                        val swipeState = remember { mutableFloatStateOf(0f) }
                        val hasOutput = image.outputBitmap != null
                        SwipeToDismissWrapper(
                            swipeState,
                            image.isProcessing,
                            hasOutput,
                            onRightSwipe = { handleImageRemoval(image.id) },
                            onLeftSwipe = {
                                if (image.outputBitmap != null) saveOrPrompt(
                                    image.id, image.filename
                                )
                                else tryProcess { viewModel.processImage(image.id) }
                            },
                            swapActions = swapSwipeActions,
                            modifier = Modifier.animateItem(
                                fadeInSpec = spring(
                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                    stiffness = Spring.StiffnessMedium
                                ), fadeOutSpec = spring(
                                    dampingRatio = Spring.DampingRatioNoBouncy,
                                    stiffness = Spring.StiffnessMedium
                                ), placementSpec = spring(
                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                    stiffness = Spring.StiffnessMedium
                                )
                            ),
                            rightSwipeImmediate = !image.isProcessing && !(image.outputBitmap != null && !image.hasBeenSaved),
                        ) {
                            ImageCard(
                                image = image,
                                onRemove = { handleImageRemoval(image.id) },
                                onProcess = { tryProcess { viewModel.processImage(image.id) } },
                                onBrisque = { haptic.light(); onNavigateToBrisque(image.id) },
                                onClick = { onNavigateToBeforeAfter(image.id) },
                                onBeforeOnly = { onNavigateToBeforeAfter(image.id) },
                                onSave = { saveOrPrompt(image.id, image.filename) },
                                isProcessing = image.isProcessing,
                                haptic = haptic
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
                    imageIdToRemove = null
                    saveOrPrompt(targetId, image.filename)
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

    val saveState by viewModel.saveState.collectAsState()
    (saveState as? SaveState.Error)?.let { err ->
        ErrorAlertDialog(
            title = stringResource(R.string.error_saving_image_title),
            errorMessage = err.message,
            onDismiss = { viewModel.dismissSaveError() },
            context = context
        )
    }

    (saveState as? SaveState.Saving)?.let { state ->
        SaveProgressDialog(state)
    }

    overwriteDialogState?.let { (id, fn) ->
        SaveImageDialog(
            defaultFilename = fn,
            showSaveAllOption = false,
            initialSaveAll = false,
            hideOptions = true,
            onDismissRequest = { overwriteDialogState = null },
            onSave = { name, _, _ ->
                viewModel.saveImage(
                    context = context,
                    imageIds = listOf(id),
                    baseFilename = name,
                    onComplete = { performRemoval(id); overwriteDialogState = null })
            })
    }

    saveDialogState?.let { (id, fn) ->
        val showSaveAllOption = images.any { it.outputBitmap != null }
        SaveImageDialog(
            defaultFilename = fn,
            showSaveAllOption = showSaveAllOption,
            initialSaveAll = false,
            hideOptions = false,
            onDismissRequest = { saveDialogState = null },
            onSave = { name, all, skip ->
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
                            baseFilename = name,
                            onComplete = { performRemoval(id) })
                    }
                }
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
    modifier: Modifier = Modifier,
    swapActions: Boolean = false,
    rightSwipeImmediate: Boolean = !isProcessing,
    leftSwipeImmediate: Boolean = false,
    content: @Composable () -> Unit
) {
    val scope = rememberCoroutineScope()
    val haptic = rememberHapticFeedback()
    var widthPx by remember { mutableIntStateOf(0) }
    var hasReachedThreshold by remember { mutableStateOf(false) }
    val swipeThreshold = 0.35f
    val currentAllowLeftSwipe by rememberUpdatedState(!isProcessing)
    val currentOnRightSwipe by rememberUpdatedState(if (swapActions) onLeftSwipe else onRightSwipe)
    val currentOnLeftSwipe by rememberUpdatedState(if (swapActions) onRightSwipe else onLeftSwipe)
    val animatedOffset by animateFloatAsState(
        targetValue = swipeState.value, animationSpec = if (swipeState.value == 0f) spring(
            dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium
        )
        else springStandard, label = "swipe"
    )
    val revealSide = when {
        widthPx > 0 && animatedOffset > 1f && animatedOffset < widthPx * 0.98f -> 1
        widthPx > 0 && animatedOffset < -1f && -animatedOffset < widthPx * 0.98f && !isProcessing -> -1
        else -> 0
    }
    Box(
        modifier
            .fillMaxWidth()
            .onSizeChanged { widthPx = it.width }) {
        if (revealSide != 0) {
            val isRight = revealSide == 1
            val fraction =
                (kotlin.math.abs(animatedOffset) / (widthPx * swipeThreshold)).coerceIn(0f, 1f)
            SwipeRevealBackground(
                fraction = fraction,
                color = when {
                    swapActions && isRight -> MaterialTheme.colorScheme.tertiaryContainer
                    swapActions && !isRight -> MaterialTheme.colorScheme.errorContainer
                    isRight -> MaterialTheme.colorScheme.errorContainer
                    else -> MaterialTheme.colorScheme.primaryContainer
                },
                icon = when {
                    swapActions && isRight -> if (hasOutput) Icons.Filled.Save else Icons.Filled.PlayArrow
                    swapActions && !isRight -> Icons.Filled.Delete
                    isRight -> if (isProcessing) Icons.Filled.Close else Icons.Filled.Delete
                    else -> if (hasOutput) Icons.Filled.Save else Icons.Filled.PlayArrow
                },
                tint = when {
                    swapActions && isRight -> MaterialTheme.colorScheme.onTertiaryContainer
                    swapActions && !isRight -> MaterialTheme.colorScheme.onErrorContainer
                    isRight -> MaterialTheme.colorScheme.onErrorContainer
                    else -> MaterialTheme.colorScheme.onPrimaryContainer
                },
                alignment = if (isRight) Alignment.CenterStart else Alignment.CenterEnd,
                startPadding = if (isRight) 20.dp else 0.dp,
                endPadding = if (isRight) 0.dp else 20.dp,
                modifier = Modifier.matchParentSize()
            )
        }

        Box(
            Modifier
                .fillMaxWidth()
                .offset { IntOffset(animatedOffset.roundToInt(), 0) }
                .pointerInput(widthPx) {
                    detectHorizontalDragGestures(onHorizontalDrag = { _, dragAmount ->
                        val newValue = swipeState.value + dragAmount
                        swipeState.value =
                            if (currentAllowLeftSwipe) newValue else maxOf(0f, newValue)
                        val absOffset = kotlin.math.abs(swipeState.value)
                        val threshold = widthPx * swipeThreshold
                        when {
                            widthPx > 0 && absOffset > threshold && !hasReachedThreshold -> {
                                haptic.medium(); hasReachedThreshold = true
                            }

                            absOffset <= threshold -> hasReachedThreshold = false
                        }
                    }, onDragEnd = {
                        val absOffset = kotlin.math.abs(swipeState.value)
                        val threshold = widthPx * swipeThreshold
                        if (widthPx > 0 && absOffset > threshold) {
                            haptic.heavy()
                            val isRight = swipeState.value > 0
                            val willSlideOff =
                                if (isRight) rightSwipeImmediate else (currentAllowLeftSwipe && leftSwipeImmediate)
                            scope.launch {
                                if (willSlideOff) {
                                    animate(
                                        initialValue = swipeState.value,
                                        targetValue = if (isRight) widthPx * 2f else -widthPx * 2f,
                                        animationSpec = spring(
                                            dampingRatio = Spring.DampingRatioNoBouncy,
                                            stiffness = Spring.StiffnessHigh
                                        )
                                    ) { value, _ -> swipeState.value = value }
                                    if (isRight) currentOnRightSwipe() else currentOnLeftSwipe()
                                    swipeState.value = 0f
                                } else {
                                    if (isRight) currentOnRightSwipe()
                                    else if (currentAllowLeftSwipe) currentOnLeftSwipe()
                                    swipeState.value = 0f
                                }
                                hasReachedThreshold = false
                            }
                        } else {
                            scope.launch {
                                swipeState.value = 0f
                                hasReachedThreshold = false
                            }
                        }
                    })
                }) { content() }
    }
}

@Composable
fun SwipeRevealBackground(
    fraction: Float,
    color: Color,
    icon: ImageVector,
    tint: Color,
    modifier: Modifier = Modifier,
    alignment: Alignment,
    startPadding: Dp = 20.dp,
    endPadding: Dp = 20.dp,
) {
    val atThreshold = fraction >= 1f
    val shakeOffset = remember { Animatable(0f) }
    val density = LocalDensity.current

    LaunchedEffect(atThreshold) {
        if (atThreshold) {
            shakeOffset.animateTo(
                targetValue = 0f, animationSpec = keyframes {
                    durationMillis = 350
                    0f at 0
                    6f at 60
                    (-6f) at 130
                    4f at 200
                    (-2f) at 260
                    0f at 350
                })
        } else {
            shakeOffset.snapTo(0f)
        }
    }

    Box(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(color)
            .graphicsLayer {
                val f = fraction.coerceIn(0f, 1f)
                scaleX = lerp(0.6f, 1f, f)
                scaleY = lerp(0.6f, 1f, f)
                alpha = lerp(0f, 1f, f)
            }, contentAlignment = alignment
    ) {
        Box(Modifier.padding(start = startPadding, end = endPadding)) {
            Icon(
                icon,
                null,
                Modifier
                    .size(28.dp)
                    .offset { IntOffset((shakeOffset.value * density.density).roundToInt(), 0) },
                tint = tint
            )
        }
    }
}

private fun releaseUri(uri: Uri, context: Context, onRemoveSharedUri: (Uri) -> Unit) {
    runCatching {
        context.contentResolver.releasePersistableUriPermission(
            uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
        )
    }
    runCatching { onRemoveSharedUri(uri) }
}

@Composable
fun rememberSaveOrPrompt(
    showSaveDialog: Boolean,
    context: Context,
    viewModel: ProcessingViewModel,
    performRemoval: (String) -> Unit,
    setSaveDialogState: (Pair<String, String>?) -> Unit,
    setOverwriteDialogState: (Pair<String, String>?) -> Unit
): (String, String) -> Unit {
    val currentShowSaveDialog by rememberUpdatedState(showSaveDialog)
    val currentViewModel by rememberUpdatedState(viewModel)
    val currentContext by rememberUpdatedState(context)
    val currentPerformRemoval by rememberUpdatedState(performRemoval)
    val currentSetSaveDialog by rememberUpdatedState(setSaveDialogState)
    val currentSetOverwriteDialog by rememberUpdatedState(setOverwriteDialogState)

    return { imageId, filename ->
        if (currentShowSaveDialog) {
            currentSetSaveDialog.invoke(Pair(imageId, filename))
        } else {
            if (ImageActions.checkFileExists(currentContext, filename)) {
                currentSetOverwriteDialog.invoke(Pair(imageId, filename))
            } else {
                currentViewModel.saveImage(
                    context = currentContext,
                    imageIds = listOf(imageId),
                    baseFilename = filename,
                    onComplete = { currentPerformRemoval.invoke(imageId) })
            }
        }
    }
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
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
fun SaveProgressDialog(saveState: SaveState.Saving) {
    val progress = if (saveState.total > 0) saveState.current.toFloat() / saveState.total.toFloat()
    else 0f

    val thickStrokeWidth = with(LocalDensity.current) { 8.dp.toPx() }
    val thickStroke = remember(thickStrokeWidth) {
        Stroke(width = thickStrokeWidth, cap = StrokeCap.Round)
    }

    val animatedProgress by animateFloatAsState(
        targetValue = progress, animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessVeryLow,
            visibilityThreshold = 1f / 1000f
        ), label = "save_progress"
    )

    BasicAlertDialog(
        onDismissRequest = {},
        modifier = Modifier,
        properties = DialogProperties(),
        content = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(enabled = false) {},
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    shape = RoundedCornerShape(28.dp),
                    tonalElevation = 6.dp,
                    modifier = Modifier.size(240.dp),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            stringResource(R.string.saving_images),
                            style = MaterialTheme.typography.titleLarge,
                            textAlign = TextAlign.Center
                        )
                        Box(
                            modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    ContainedLoadingIndicator(
                                        modifier = Modifier.size(80.dp)
                                    )
                                    if (saveState.total > 1) {
                                        CircularWavyProgressIndicator(
                                            progress = { animatedProgress },
                                            modifier = Modifier.size(88.dp),
                                            stroke = thickStroke,
                                            trackStroke = thickStroke,
                                            color = MaterialTheme.colorScheme.onSurface,
                                        )
                                    } else {
                                        CircularWavyProgressIndicator(
                                            modifier = Modifier.size(88.dp),
                                            stroke = thickStroke,
                                            trackStroke = thickStroke,
                                            color = MaterialTheme.colorScheme.onSurface,
                                        )
                                    }
                                }
                                if (saveState.total > 1) {
                                    Text(
                                        stringResource(
                                            R.string.saving_image_progress,
                                            saveState.current,
                                            saveState.total
                                        ),
                                        style = MaterialTheme.typography.bodySmall,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    }
                }
            }
        })
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
    onSave: () -> Unit,
    isProcessing: Boolean = false,
    haptic: HapticFeedbackPerformer
) {
    val cardInteractionSource = remember { MutableInteractionSource() }
    val cardPress by rememberMaterialPressState(cardInteractionSource)
    val processInteraction = remember { MutableInteractionSource() }
    val removeInteraction = remember { MutableInteractionSource() }
    val brisqueInteraction = remember { MutableInteractionSource() }
    val animatedBadgeCorner = lerp(8f, 24f, cardPress)
    val processPress by rememberMaterialPressState(processInteraction)
    val removePress by rememberMaterialPressState(removeInteraction)
    val brisquePress by rememberMaterialPressState(brisqueInteraction)

    val bouncySpringFloat = spring<Float>(
        dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium
    )
    val bouncySpringDp = spring<Dp>(
        dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium
    )

    val cardState = when {
        isProcessing -> CardState.Processing
        image.outputBitmap != null && image.isOutputStale -> CardState.Stale
        image.outputBitmap != null -> CardState.Complete
        else -> CardState.Idle
    }

    val transition = updateTransition(targetState = cardState, label = "card_morph")

    val morphContainerColor by transition.animateColor(
        transitionSpec = { spring(dampingRatio = Spring.DampingRatioMediumBouncy) },
        label = "container_color"
    ) { state ->
        when (state) {
            CardState.Processing -> MaterialTheme.colorScheme.errorContainer
            CardState.Stale -> MaterialTheme.colorScheme.tertiaryContainer
            CardState.Complete -> MaterialTheme.colorScheme.primaryContainer
            CardState.Idle -> MaterialTheme.colorScheme.secondaryContainer
        }
    }

    val morphContentColor by transition.animateColor(
        transitionSpec = { spring(dampingRatio = Spring.DampingRatioMediumBouncy) },
        label = "content_color"
    ) { state ->
        when (state) {
            CardState.Processing -> MaterialTheme.colorScheme.onErrorContainer
            CardState.Stale -> MaterialTheme.colorScheme.onTertiaryContainer
            CardState.Complete -> MaterialTheme.colorScheme.onPrimaryContainer
            CardState.Idle -> MaterialTheme.colorScheme.onSecondaryContainer
        }
    }

    val cardCorner by transition.animateDp(
        transitionSpec = {
            spring(
                dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium
            )
        }, label = "card_corner"
    ) { state -> if (state == CardState.Processing) 20.dp else 16.dp }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(cardCorner))
            .clickable(
                interactionSource = cardInteractionSource,
                indication = ripple(),
                enabled = !isProcessing
            ) {
                haptic.light()
                if (image.outputBitmap != null) onClick() else onBeforeOnly()
            },
        shape = RoundedCornerShape(cardCorner),
        colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val imageBitmap =
                remember(image.thumbnailBitmap, image.outputBitmap, image.inputBitmap) {
                    (image.thumbnailBitmap ?: image.outputBitmap
                    ?: image.inputBitmap).asImageBitmap()
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

        Box(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp)
        ) {
            Row(
                Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(
                    animateDpAsState(
                        targetValue = if (isProcessing) 0.dp else 6.dp, animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMedium
                        ), label = "button_spacing"
                    ).value
                ), verticalAlignment = Alignment.CenterVertically
            ) {
                val pressOffset = 0.18f

                val primaryWeight by animateFloatAsState(
                    targetValue = when {
                        isProcessing -> 1f
                        processPress > 0f -> 1f + pressOffset
                        removePress > 0f -> 1f
                        else -> 1f
                    }, animationSpec = bouncySpringFloat, label = "primary_weight"
                )

                val brisqueExpansion by animateFloatAsState(
                    targetValue = brisquePress,
                    animationSpec = bouncySpringFloat,
                    label = "brisque_expansion"
                )

                val removeWidth = when {
                    isProcessing -> 0.dp
                    else -> (lerp(145f, 129f, brisqueExpansion) + lerp(0f, -25f, processPress)).dp
                }

                val brisqueWidth = when {
                    isProcessing -> 0.dp
                    else -> lerp(40f, 56f, brisqueExpansion).dp
                }

                val brisqueCorner by animateDpAsState(
                    targetValue = lerp(28f, 6f, brisquePress).dp,
                    animationSpec = bouncySpringDp,
                )

                val removeCorner by animateDpAsState(
                    targetValue = lerp(6f, 28f, removePress).dp,
                    animationSpec = bouncySpringDp,
                    label = "remove_corner"
                )

                val processStartCorner by animateDpAsState(
                    targetValue = lerp(28f, 6f, processPress).dp,
                    animationSpec = bouncySpringDp,
                    label = "process_start_corner"
                )
                val processEndCorner by animateDpAsState(
                    targetValue = if (isProcessing) lerp(28f, 6f, processPress).dp else 6.dp,
                    animationSpec = bouncySpringDp,
                    label = "process_end_corner"
                )

                val processShape = RoundedCornerShape(
                    topStart = processStartCorner,
                    bottomStart = processStartCorner,
                    topEnd = processEndCorner,
                    bottomEnd = processEndCorner
                )

                Box(Modifier.weight(primaryWeight)) {
                    Button(
                        onClick = {
                            when {
                                isProcessing -> {
                                    haptic.heavy(); onRemove()
                                }

                                image.outputBitmap != null && !image.isOutputStale -> {
                                    haptic.medium(); onSave()
                                }

                                else -> {
                                    haptic.medium(); onProcess()
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = processShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = morphContainerColor, contentColor = morphContentColor
                        ),
                        interactionSource = processInteraction
                    ) {
                        val (icon, label) = when {
                            isProcessing -> Icons.Filled.Close to stringResource(R.string.cancel)
                            image.outputBitmap != null && image.isOutputStale -> Icons.Filled.PlayArrow to stringResource(
                                R.string.reprocess
                            )

                            image.outputBitmap != null -> Icons.Filled.Save to stringResource(R.string.save)
                            else -> Icons.Filled.PlayArrow to stringResource(R.string.process)
                        }
                        Icon(icon, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(label, style = MaterialTheme.typography.labelLarge)
                    }
                }

                if (!isProcessing && removeWidth > 90.dp) {
                    Box(Modifier.width(removeWidth)) {
                        Button(
                            onClick = { haptic.heavy(); onRemove() },
                            modifier = Modifier.width(removeWidth),
                            shape = RoundedCornerShape(removeCorner),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.onErrorContainer
                            ),
                            contentPadding = PaddingValues(0.dp),
                            interactionSource = removeInteraction
                        ) {
                            Icon(Icons.Filled.Delete, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(
                                stringResource(R.string.remove),
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }

                    Box(Modifier.width(brisqueWidth)) {
                        Button(
                            onClick = { haptic.light(); onBrisque() },
                            modifier = Modifier.width(brisqueWidth),
                            shape = RoundedCornerShape(
                                topStart = 6.dp,
                                bottomStart = 6.dp,
                                topEnd = brisqueCorner,
                                bottomEnd = brisqueCorner
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
    }
}
