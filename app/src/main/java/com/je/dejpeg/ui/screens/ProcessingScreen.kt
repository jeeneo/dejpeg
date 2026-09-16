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
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.animateColor
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.EaseInOutSine
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Slider
import androidx.compose.material3.SplitButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSliderState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import androidx.compose.ui.window.DialogProperties
import com.je.dejpeg.App
import com.je.dejpeg.AppPreferences
import com.je.dejpeg.HapticFeedbacks
import com.je.dejpeg.ImageRepository
import com.je.dejpeg.R
import com.je.dejpeg.ui.components.CancelProcessingDialog
import com.je.dejpeg.ui.components.CornerRole
import com.je.dejpeg.ui.components.ErrorAlertDialog
import com.je.dejpeg.ui.components.GroupedListSpacing
import com.je.dejpeg.ui.components.ImageSourceDialog
import com.je.dejpeg.ui.components.PreparingShareDialog
import com.je.dejpeg.ui.components.RemoveImageDialog
import com.je.dejpeg.ui.components.SaveImageDialog
import com.je.dejpeg.ui.components.SimpleAlertDialog
import com.je.dejpeg.ui.components.SnackbarController
import com.je.dejpeg.ui.components.SnackbarDuration
import com.je.dejpeg.ui.components.SnackySnackbarEvents
import com.je.dejpeg.ui.components.SwipeToDismissBox
import com.je.dejpeg.ui.components.rememberMaterialPressState
import com.je.dejpeg.ui.components.toListItemShapes
import com.je.dejpeg.ui.viewmodel.ImageItem
import com.je.dejpeg.ui.viewmodel.ProcessingUiState
import com.je.dejpeg.ui.viewmodel.ProcessingViewModel
import com.je.dejpeg.ui.viewmodel.SaveState
import com.je.dejpeg.ui.viewmodel.SettingsViewModel
import com.je.dejpeg.utils.ImageActions
import com.je.dejpeg.utils.ModelType
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

private enum class CardState { Idle, Processing, Complete, Stale }

@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalFoundationApi::class,
    ExperimentalAnimationApi::class
)
@Composable
fun ProcessingScreen(
    viewModel: ProcessingViewModel,
    settingsViewModel: SettingsViewModel,
    imageRepository: ImageRepository,
    onNavigateToBeforeAfter: (String) -> Unit = {},
    onNavigateToBrisque: (String) -> Unit = {},
    onNavigateToCompare: (String, String) -> Unit = { _, _ -> },
    isActive: Boolean = true,
    initialSharedUris: List<Uri> = emptyList(),
    onRemoveSharedUri: (Uri) -> Unit = {},
) {
    val context = App.ctx
    val appPreferences = remember { AppPreferences() }
    val defaultImageSource by appPreferences.defaultImageSource.collectAsState(initial = null)
    val swapSwipeActions by appPreferences.swapSwipeActions.collectAsState(initial = false)
    val images by imageRepository.images.collectAsState()
    val globalStrength by settingsViewModel.globalStrength.collectAsState()
    val activeSelection by settingsViewModel.activeSelection.collectAsState()
    val processingMode = activeSelection.type
    val oidnInputScale by settingsViewModel.oidnInputScale.collectAsState()
    val isOidnMode = processingMode == ModelType.OIDN
    val activeModelName = activeSelection.modelName
    val supportsStrength = activeModelName?.contains("fbcnn", ignoreCase = true) == true
    val noModelMessage = stringResource(R.string.no_model_installed_title)
    val scope = rememberCoroutineScope()
    val isLoadingImages by imageRepository.isLoadingImages.collectAsState()
    val loadingImagesProgress by imageRepository.loadingImagesProgress.collectAsState()
    val processingErrorDialog by viewModel.processingErrorDialog.collectAsState()
    val gpuCacheCreatingDialog by viewModel.gpuCacheCreatingDialog.collectAsState()
    var imageIdToRemove by remember { mutableStateOf<String?>(null) }
    var imageIdToCancel by remember { mutableStateOf<String?>(null) }
    var showImageSourceDialog by remember { mutableStateOf(false) }
    var showSettingsSheet by remember { mutableStateOf(false) }
    var showCancelAllDialog by remember { mutableStateOf(false) }
    var saveDialogState by remember { mutableStateOf<Pair<String, String>?>(null) }
    var overwriteDialogState by remember { mutableStateOf<Pair<String, String>?>(null) }
    val showSaveDialog by appPreferences.showSaveDialog.collectAsState(initial = true)
    var selectedImageIds by remember { mutableStateOf<List<String>>(emptyList()) }
    val isSelectionMode = selectedImageIds.isNotEmpty()

    val toggleSelection: (String) -> Unit = { id ->
        HapticFeedbacks.light()
        selectedImageIds = if (selectedImageIds.contains(id)) {
            selectedImageIds - id
        } else {
            selectedImageIds + id
        }
    }
    val clearSelection: () -> Unit = { selectedImageIds = emptyList() }

    val performRemoval: (String) -> Unit = { imageId ->
        val targetUri = images.firstOrNull { it.id == imageId }?.uri
        targetUri?.let { uri -> releaseUri(uri, context, onRemoveSharedUri) }
        viewModel.removeImage(imageId, force = true, cleanupCache = true)
        imageIdToRemove = null
        imageIdToCancel = null
        selectedImageIds = selectedImageIds - imageId
    }

    BackHandler(enabled = isActive && isSelectionMode) {
        HapticFeedbacks.light()
        clearSelection()
    }

    fun tryProcess(block: () -> Unit) {
        if (!settingsViewModel.hasActiveModel(processingMode)) scope.launch {
            SnackbarController.pushEvent(
                SnackySnackbarEvents.MessageEvent(
                    message = noModelMessage, duration = SnackbarDuration.Long
                )
            )
        } else block()
    }

    fun <T> Pair<String, T>?.prune(images: List<ImageItem>): Pair<String, T>? =
        this?.takeIf { (id, _) -> images.any { it.id == id } }

    LaunchedEffect(images) {
        imageIdToRemove = imageIdToRemove?.takeIf { id -> images.any { it.id == id } }
        imageIdToCancel = imageIdToCancel?.takeIf { id -> images.any { it.id == id } }
        overwriteDialogState = overwriteDialogState.prune(images)
        saveDialogState = saveDialogState.prune(images)
        selectedImageIds = selectedImageIds.filter { id -> images.any { it.id == id } }
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
                if (uris.isNotEmpty()) {
                    imageRepository.addImagesFromUris(context, uris)
                    viewModel.notifyImagePicked()
                }
            }
        }
    LaunchedEffect(Unit) { viewModel.setImagePickerLauncher(imagePickerLauncher) }

    fun launchImportIntent() {
        HapticFeedbacks.light()
        when (defaultImageSource) {
            "gallery" -> viewModel.launchGalleryPicker()
            "internal" -> viewModel.launchInternalPhotoPicker()
            "documents" -> viewModel.launchDocumentsPicker()
            "camera" -> viewModel.launchCamera()
            else -> showImageSourceDialog = true
        }
    }

    val displayCount = if (isSelectionMode) selectedImageIds.size else images.size
    Column(
        Modifier
            .fillMaxSize()
            .padding(start = 16.dp, end = 16.dp)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            Arrangement.SpaceBetween,
            Alignment.CenterVertically
        ) {
            AnimatedContent(
                targetState = isSelectionMode, label = "header_text", transitionSpec = {
                    fadeIn(spring(stiffness = Spring.StiffnessMedium)) + slideInVertically(
                        spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                        initialOffsetY = { if (targetState) it else -it }) togetherWith fadeOut(
                        spring(stiffness = Spring.StiffnessMedium)
                    ) + slideOutVertically(
                        spring(dampingRatio = Spring.DampingRatioNoBouncy),
                        targetOffsetY = { if (targetState) -it else it })
                }) { selecting ->
                Text(
                    if (selecting) context.resources.getQuantityString(
                        R.plurals.selected_count, displayCount, displayCount
                    )
                    else stringResource(R.string.images, images.size),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }
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
                    targetValue = if (allComplete) 121.dp else 56.dp, animationSpec = spring(
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
                val settingsInteraction = remember { MutableInteractionSource() }
                val settingsPress by rememberMaterialPressState(settingsInteraction)
                FloatingActionButton(
                    onClick = {
                        HapticFeedbacks.medium()
                        showSettingsSheet = true
                    },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    shape = RoundedCornerShape(lerp(16f, 28f, settingsPress).dp),
                    interactionSource = settingsInteraction,
                ) {
                    Icon(
                        Icons.Rounded.Settings,
                        contentDescription = stringResource(R.string.settings)
                    )
                }
                if (images.isNotEmpty()) {
                    FloatingActionButton(
                        onClick = {
                            HapticFeedbacks.medium()
                            if (isProcessing) {
                                showCancelAllDialog = true
                            } else if (allComplete) {
                                val imageIds =
                                    images.filter { it.outputBitmap != null }.map { it.id }
                                if (imageIds.isNotEmpty()) {
                                    viewModel.saveImage(context, imageIds)
                                }
                            } else {
                                tryProcess { HapticFeedbacks.medium(); viewModel.processImages() }
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
                            AnimatedContent(
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
                                        0 -> Icons.Rounded.Close
                                        1 -> Icons.Rounded.Save
                                        else -> Icons.Rounded.PlayArrow
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
                    onClick = { launchImportIntent() },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    shape = RoundedCornerShape(addCorner.dp),
                    interactionSource = addInteraction
                ) {
                    Icon(Icons.Rounded.Add, stringResource(R.string.add_images))
                }
            }
        }
        val showCard = images.isNotEmpty() && supportsStrength
        AnimatedVisibility(
            visible = showCard,
            enter = fadeIn() + expandVertically(expandFrom = Alignment.Top),
            exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Top)
        ) {
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
                        val oidnSliderState = rememberSliderState(
                            value = oidnInputScale,
                            steps = 19,
                            trackRange = 0f..10f,
                        )
                        LaunchedEffect(oidnInputScale) { oidnSliderState.value = oidnInputScale }
                        Slider(
                            state = oidnSliderState, onValueChange = {
                                val v = (it * 2).roundToInt() / 2f; if (v != prevScale) {
                                HapticFeedbacks.light(); prevScale = v
                            }; settingsViewModel.setOidnInputScale(v)
                            }, modifier = Modifier
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
                        val strengthSliderState = rememberSliderState(
                            value = globalStrength,
                            steps = 19,
                            trackRange = 0f..100f,
                        )
                        LaunchedEffect(globalStrength) {
                            strengthSliderState.value = globalStrength
                        }
                        Slider(
                            state = strengthSliderState, onValueChange = {
                                val v = (it / 5).roundToInt() * 5f; if (v != prevStrength) {
                                HapticFeedbacks.light(); prevStrength = v
                            }; settingsViewModel.setGlobalStrength(v)
                            }, modifier = Modifier
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
                            .height(240.dp)
                            .clip(RoundedCornerShape(28.dp))
                            .clickable(
                                interactionSource = buttonInteractionSource, indication = null
                            ) { launchImportIntent() }
                            .padding(20.dp),
                        contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Rounded.AddPhotoAlternate,
                                stringResource(R.string.add_images),
                                modifier = Modifier.size(82.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.height(8.dp))
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
                            Spacer(Modifier.height(8.dp))
                            FilledTonalButton(
                                onClick = { launchImportIntent() },
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
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(GroupedListSpacing),
                contentPadding = PaddingValues(
                    bottom = WindowInsets.navigationBars.asPaddingValues()
                        .calculateBottomPadding() + 8.dp
                )
            ) {
                itemsIndexed(
                    items = images, key = { _, image -> image.id }) { index, image ->
                    ImageCard(
                        index = index,
                        image = image,
                        images = images,
                        isSelectionMode = isSelectionMode,
                        selectedImageIds = selectedImageIds,
                        viewModel = viewModel,
                        onToggleSelection = toggleSelection,
                        swapSwipeActions = swapSwipeActions,
                        showSaveDialog = showSaveDialog,
                        onShowSaveDialog = { id, filename -> saveDialogState = Pair(id, filename) },
                        onSaveImage = { id, filename ->
                            if (ImageActions.checkFileExists(context, filename)) {
                                overwriteDialogState = Pair(id, filename)
                            } else {
                                viewModel.saveImage(
                                    context = context,
                                    imageIds = listOf(id),
                                    baseFilename = filename,
                                    onComplete = { performRemoval(id) })
                            }
                        },
                        tryProcess = { block -> tryProcess(block) },
                        onCancelProcessing = { imageIdToCancel = it },
                        onShowRemoveDialog = { imageIdToRemove = it },
                        performRemoval = performRemoval,
                        onNavigateToBeforeAfter = onNavigateToBeforeAfter,
                        onNavigateToBrisque = onNavigateToBrisque,
                        onNavigateToCompare = onNavigateToCompare,
                        onClearSelection = clearSelection
                    )
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
                    if (showSaveDialog) saveDialogState = Pair(targetId, image.filename)
                    else if (ImageActions.checkFileExists(
                            context, image.filename
                        )
                    ) overwriteDialogState = Pair(
                        targetId, image.filename
                    )
                    else viewModel.saveImage(
                        context = context,
                        imageIds = listOf(targetId),
                        baseFilename = image.filename,
                        onComplete = { performRemoval(targetId) })
                })
        } ?: run { imageIdToRemove = null }
    }

    imageIdToCancel?.let { targetId ->
        images.firstOrNull { it.id == targetId }?.let { image ->
            CancelProcessingDialog(
                imageFilename = image.filename,
                onDismissRequest = { imageIdToCancel = null },
                onConfirm = {
                    viewModel.cancelQueuedImage(targetId)
                    imageIdToCancel = null
                })
        } ?: run { imageIdToCancel = null }
    }

    if (showImageSourceDialog) {
        ImageSourceDialog(
            onDismiss = { showImageSourceDialog = false }, viewModel = viewModel
        )
    }

    if (showSettingsSheet) {
        SettingsSheet(
            viewModel = settingsViewModel,
            processingViewModel = viewModel,
            onDismiss = { showSettingsSheet = false })
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
                    overwrite = true,
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
    if (gpuCacheCreatingDialog) {
        SimpleAlertDialog(
            title = stringResource(R.string.gpu_cache_title),
            message = stringResource(R.string.gpu_cache_text),
            onDismiss = { viewModel.dismissGpuCacheCreatingDialog() },
            confirmButtonText = stringResource(R.string.ok)
        )
    }
    if (isLoadingImages) {
        val progress = loadingImagesProgress
        PreparingShareDialog(
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
fun LazyItemScope.ImageCard(
    modifier: Modifier = Modifier,
    index: Int,
    image: ImageItem,
    images: List<ImageItem>,
    isSelectionMode: Boolean,
    selectedImageIds: List<String>,
    viewModel: ProcessingViewModel,
    onToggleSelection: (String) -> Unit,
    swapSwipeActions: Boolean,
    showSaveDialog: Boolean,
    onShowSaveDialog: (String, String) -> Unit,
    onSaveImage: (String, String) -> Unit,
    tryProcess: (() -> Unit) -> Unit,
    onCancelProcessing: (String) -> Unit,
    onShowRemoveDialog: (String) -> Unit,
    performRemoval: (String) -> Unit,
    onNavigateToBeforeAfter: (String) -> Unit,
    onNavigateToBrisque: (String) -> Unit,
    onNavigateToCompare: (String, String) -> Unit,
    onClearSelection: () -> Unit,
) {
    val isSelected = selectedImageIds.contains(image.id)
    val isProcessing = image.isProcessing
    val positiveAction: () -> (() -> Unit)? = {
        if (image.outputBitmap != null) {
            if (showSaveDialog) {
                onShowSaveDialog(image.id, image.filename)
                null
            } else {
                { onSaveImage(image.id, image.filename) }
            }
        } else {
            tryProcess { viewModel.processImage(image.id) }
            null
        }
    }

    val negativeAction: () -> (() -> Unit)? = {
        when {
            isProcessing && viewModel.isCurrent(image.id) -> {
                onCancelProcessing(image.id)
                null
            }

            isProcessing -> {
                // cancel queue
                run { viewModel.cancelQueuedImage(image.id) }
                null
            }

            image.outputBitmap != null -> {
                onShowRemoveDialog(image.id)
                null
            }

            else -> {
                { performRemoval(image.id) }
            }
        }
    }

    val onSwipeLeft: () -> (() -> Unit)? = if (swapSwipeActions) negativeAction else positiveAction
    val onSwipeRight: () -> (() -> Unit)? = if (swapSwipeActions) positiveAction else negativeAction

    SwipeToDismissWrapper(
        modifier = modifier.animateItem(
            fadeInSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium
            ), fadeOutSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium
            ), placementSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium
            )
        ),
        onSwipeLeft = onSwipeLeft,
        onSwipeRight = onSwipeRight,
        rightSwipeEnabled = !isSelectionMode && !isProcessing,
        hasOutputBitmap = image.outputBitmap != null,
        swapSwipeActions = swapSwipeActions,
        isProcessing = isProcessing,
    ) {
        val cardShapes = CornerRole.forPosition(index + 1, images.count()).toListItemShapes()
        val progressTint = MaterialTheme.colorScheme.primary
        val baseColor by animateColorAsState(
            targetValue = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
            label = "item_base_color"
        )
        val chunkFraction = if (image.totalChunks > 1) {
            image.completedChunks.toFloat() / image.totalChunks.coerceAtLeast(1)
        } else -1f
        val pulseAlpha by rememberInfiniteTransition().animateFloat(
            initialValue = 0.04f, targetValue = 0.11f, animationSpec = infiniteRepeatable(
                animation = tween(1200, easing = EaseInOutSine), repeatMode = RepeatMode.Reverse
            ), label = "pulse_alpha"
        )
        SegmentedListItem(
            selected = isSelected,
            colors = ListItemDefaults.segmentedColors(containerColor = Color.Transparent),
            shapes = cardShapes,
            contentPadding = PaddingValues(0.dp),
            modifier = Modifier
                .clip(cardShapes.shape)
                .drawBehind {
                    drawRect(baseColor)
                    if (!isProcessing) return@drawBehind
                    if (chunkFraction >= 0f) {
                        if (chunkFraction >= 1f) {
                            drawRect(progressTint.copy(alpha = 0.12f))
                        } else {
                            val fillEnd = size.width * chunkFraction
                            val gradientEnd = (fillEnd + 12.dp.toPx()).coerceAtMost(size.width)
                            if (gradientEnd > 0f) {
                                val solidStop = (fillEnd / gradientEnd).coerceIn(0f, 1f)
                                drawRect(
                                    brush = Brush.horizontalGradient(
                                        colorStops = arrayOf(
                                            0f to progressTint.copy(alpha = 0.16f),
                                            solidStop to progressTint.copy(alpha = 0.10f),
                                            1f to Color.Transparent
                                        ), startX = 0f, endX = gradientEnd
                                    )
                                )
                            }
                        }
                    } else {
                        drawRect(progressTint.copy(alpha = pulseAlpha))
                    }
                },
            onClick = {
                if (image.outputBitmap != null) if (isSelectionMode) onToggleSelection(
                    image.id
                )
                else onNavigateToBeforeAfter(image.id) else if (isSelectionMode) onToggleSelection(
                    image.id
                )
                else onNavigateToBeforeAfter(image.id)
            },
            onLongClick = { onToggleSelection(image.id) },
            content = {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(end = 8.dp, top = 8.dp, bottom = 8.dp)
                ) {
                    val imagePreview = remember(
                        image.thumbnailBitmap, image.outputBitmap, image.inputBitmap
                    ) {
                        (image.thumbnailBitmap ?: image.outputBitmap
                        ?: image.inputBitmap).asImageBitmap()
                    }
                    Box(
                        Modifier.padding(start = 8.dp, end = 8.dp),
                    ) {
                        Surface(
                            Modifier
                                .size(84.dp)
                                .clip(RoundedCornerShape(12.dp)),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Image(
                                imagePreview,
                                image.filename,
                                Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        }
                    }
                    Column(
                        Modifier
                            .weight(1f)
                            .height(84.dp)
                    ) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                image.filename,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .weight(1f, fill = false)
                                    .alignByBaseline()
                            )
                            Text(
                                image.size,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.alignByBaseline()
                            )
                        }
                        Spacer(Modifier.weight(1f))
                        if (image.outputBitmap != null && !isProcessing) {
                            Surface(
                                shape = RoundedCornerShape(32.dp),
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Text(
                                    stringResource(R.string.status_complete_ui),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.padding(
                                        horizontal = 8.dp, vertical = 2.dp
                                    )
                                )
                            }
                        } else if (!isProcessing) {
                            Surface(
                                shape = RoundedCornerShape(32.dp),
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Text(
                                    stringResource(R.string.status_ready),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.padding(
                                        horizontal = 8.dp, vertical = 2.dp
                                    )
                                )
                            }
                        }
                        if (isProcessing && image.progress.isNotEmpty()) {
                            Surface(
                                shape = RoundedCornerShape(32.dp),
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Text(
                                    image.progress,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.padding(
                                        horizontal = 8.dp, vertical = 2.dp
                                    )
                                )
                            }
                        }
                        Spacer(Modifier.weight(1f))
                        ImageCardSplitButton(
                            image = image,
                            isProcessing = isProcessing,
                            onProcess = { tryProcess { viewModel.processImage(image.id) } },
                            onRemove = { onSwipeRight() },
                            onBrisque = {
                                HapticFeedbacks.light(); onNavigateToBrisque(
                                image.id
                            )
                            },
                            onSave = {
                                if (showSaveDialog) onShowSaveDialog(image.id, image.filename)
                                else onSaveImage(image.id, image.filename)
                            },
                            onImportOutput = {
                                HapticFeedbacks.light()
                                viewModel.importOutputAsNewImage(image.id)
                            },
                            isCompareReady = selectedImageIds.size == 2,
                            onCompare = {
                                val (idA, idB) = selectedImageIds
                                onNavigateToCompare(idA, idB)
                                onClearSelection()
                            },
                        )
                    }
                }
            })
    }
}

@Composable
fun SwipeToDismissWrapper(
    modifier: Modifier = Modifier,
    onSwipeLeft: () -> (() -> Unit)?,
    onSwipeRight: () -> (() -> Unit)?,
    swapSwipeActions: Boolean,
    rightSwipeEnabled: Boolean = true,
    isProcessing: Boolean = false,
    hasOutputBitmap: Boolean = false,
    content: @Composable () -> Unit
) {
    val currentOnSwipeLeft by rememberUpdatedState(onSwipeLeft)
    val currentOnSwipeRight by rememberUpdatedState(onSwipeRight)

    val leftSwipeIcon = if (swapSwipeActions) {
        if (isProcessing) Icons.Rounded.Close else Icons.Rounded.Delete
    } else {
        if (hasOutputBitmap) Icons.Rounded.Save else Icons.Rounded.PlayArrow
    }
    val leftSwipeIconTint =
        if (swapSwipeActions) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary
    val leftSwipeBgColor =
        if (swapSwipeActions) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.tertiaryContainer
    val rightSwipeIcon = if (swapSwipeActions) {
        if (hasOutputBitmap) Icons.Rounded.Save else Icons.Rounded.PlayArrow
    } else {
        if (isProcessing) Icons.Rounded.Close else Icons.Rounded.Delete
    }
    val rightSwipeIconTint =
        if (swapSwipeActions) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error
    val rightSwipeBgColor =
        if (swapSwipeActions) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.onError

    SwipeToDismissBox(
        onQualifiedStartToEnd = { HapticFeedbacks.light(); currentOnSwipeRight() },
        onQualifiedEndToStart = { HapticFeedbacks.light(); currentOnSwipeLeft() },
        modifier = modifier,
        enableDismissFromStartToEnd = true,
        enableDismissFromEndToStart = rightSwipeEnabled,
        positionalThreshold = { totalWidth -> totalWidth * 0.6f },
        backgroundContent = { offsetPx, maxWidthPx ->
            Box(Modifier.fillMaxSize()) {
                val density = LocalDensity.current
                val isRight = offsetPx > 0f
                val revealedPx = abs(offsetPx).coerceIn(0f, maxWidthPx)
                val icon = if (isRight) rightSwipeIcon else leftSwipeIcon
                val contColor = if (isRight) rightSwipeBgColor else leftSwipeBgColor
                val iconTint = if (isRight) rightSwipeIconTint else leftSwipeIconTint

                val revealedDp = with(density) { revealedPx.toDp() }
                val edgeAlignment = if (isRight) Alignment.CenterStart else Alignment.CenterEnd
                val visible = revealedPx > 1f
                val alpha by animateFloatAsState(
                    targetValue = if (visible) 1f else 0f,
                    animationSpec = tween(durationMillis = 60),
                    label = "swipeBgAlpha"
                )

                // visually inspired from Gmail
                val iconSize = 24.dp
                val halfIconSize = iconSize / 2
                val fixedInset = 34.dp - 2.dp
                val iconCenterFromEdge = maxOf(fixedInset, revealedDp / 2)
                val iconOffset = iconCenterFromEdge - halfIconSize
                Box(
                    modifier = Modifier
                        .align(edgeAlignment)
                        .width(revealedDp)
                        .fillMaxHeight()
                        .padding(start = 2.dp, end = 2.dp)
                        .graphicsLayer { this.alpha = alpha }
                        .clip(RoundedCornerShape(revealedDp))
                        .background(contColor)) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier
                            .align(edgeAlignment)
                            .offset(x = if (isRight) iconOffset else -iconOffset)
                            .graphicsLayer { this.alpha = alpha }
                            .requiredSize(24.dp))
                }
            }
        }) {
        content()
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

//@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
//@Composable
//fun ImageCard(
//    image: ImageItem,
//    onRemove: () -> Unit,
//    onProcess: () -> Unit,
//    onBrisque: () -> Unit,
//    onSave: () -> Unit,
//    onImportOutput: () -> Unit,
//    isProcessing: Boolean = false,
//    isCompareReady: Boolean = false,
//    onCompare: () -> Unit = {}
//) {
//}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ImageCardSplitButton(
    modifier: Modifier = Modifier,
    image: ImageItem,
    isProcessing: Boolean,
    onProcess: () -> Unit,
    onRemove: () -> Unit,
    onBrisque: () -> Unit,
    onSave: () -> Unit,
    onImportOutput: () -> Unit,
    isCompareReady: Boolean = false,
    onCompare: () -> Unit = {},
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val fastSpatialSpec = MaterialTheme.motionScheme.fastSpatialSpec<Float>()
    val cardState = when {
        isProcessing -> CardState.Processing
        image.outputBitmap != null && image.isOutputStale -> CardState.Stale
        image.outputBitmap != null -> CardState.Complete
        else -> CardState.Idle
    }
    val transition = updateTransition(targetState = cardState, label = "card_morph")
    val containerColor by transition.animateColor(
        label = "container_color"
    ) { state ->
        when (state) {
            CardState.Processing -> MaterialTheme.colorScheme.errorContainer
            CardState.Stale -> MaterialTheme.colorScheme.tertiaryContainer
            CardState.Complete -> MaterialTheme.colorScheme.primaryContainer
            CardState.Idle -> MaterialTheme.colorScheme.secondaryContainer
        }
    }
    val contentColor by transition.animateColor(
        label = "content_color"
    ) { state ->
        when (state) {
            CardState.Processing -> MaterialTheme.colorScheme.onErrorContainer
            CardState.Stale -> MaterialTheme.colorScheme.onTertiaryContainer
            CardState.Complete -> MaterialTheme.colorScheme.onPrimaryContainer
            CardState.Idle -> MaterialTheme.colorScheme.onSecondaryContainer
        }
    }
    val leadingLabel = when (cardState) {
        CardState.Processing -> stringResource(R.string.cancel)
        CardState.Stale -> stringResource(R.string.reprocess)
        CardState.Complete -> stringResource(R.string.save_image)
        CardState.Idle -> stringResource(R.string.process)
    }
    val leadingIcon = when (cardState) {
        CardState.Processing -> Icons.Rounded.Close
        CardState.Stale, CardState.Idle -> Icons.Rounded.PlayArrow
        CardState.Complete -> Icons.Rounded.Save
    }
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.5.dp)
    ) {
        SplitButtonDefaults.LeadingButton(
            onClick = {
                when (cardState) {
                    CardState.Processing -> {
                        HapticFeedbacks.light(); onRemove()
                    }

                    CardState.Complete -> {
                        HapticFeedbacks.light(); onSave()
                    }

                    CardState.Idle, CardState.Stale -> {
                        HapticFeedbacks.light(); onProcess()
                    }
                }
            },
            colors = ButtonDefaults.buttonColors(
                containerColor = containerColor,
                contentColor = contentColor,
            ),
            modifier = Modifier
                .weight(1f)
                .height(36.dp),
        ) {
            Icon(leadingIcon, contentDescription = null)
            Spacer(Modifier.width(4.dp))
            Text(leadingLabel, style = MaterialTheme.typography.labelMedium)
        }
        Box {
            SplitButtonDefaults.TrailingButton(
                checked = menuExpanded,
                onCheckedChange = { HapticFeedbacks.light(); menuExpanded = it },
                colors = ButtonDefaults.buttonColors(
                    containerColor = containerColor,
                    contentColor = contentColor,
                ),
                modifier = Modifier.height(36.dp),
            ) {
                val chevronRotation by animateFloatAsState(
                    targetValue = if (menuExpanded) 0f else -90f,
                    animationSpec = fastSpatialSpec,
                    label = "chevronRotation"
                )
                Icon(
                    Icons.Rounded.KeyboardArrowDown,
                    contentDescription = null,
                    modifier = Modifier
                        .size(SplitButtonDefaults.TrailingIconSize)
                        .graphicsLayer { rotationZ = chevronRotation })
            }
            DropdownMenu(
                expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                if (isCompareReady) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.compare)) },
                        leadingIcon = { Icon(Icons.Rounded.SwapHoriz, null) },
                        onClick = {
                            HapticFeedbacks.medium()
                            menuExpanded = false
                            onCompare()
                        })
                }
                if (cardState == CardState.Complete) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.reprocess)) },
                        leadingIcon = {
                            Icon(Icons.Rounded.Refresh, null, modifier = Modifier.size(26.dp))
                        },
                        onClick = {
                            HapticFeedbacks.medium()
                            menuExpanded = false
                            onProcess()
                        })
                }
                if (cardState == CardState.Complete) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.import_output)) },
                        leadingIcon = { Icon(Icons.Rounded.AddPhotoAlternate, null) },
                        onClick = {
                            HapticFeedbacks.light()
                            menuExpanded = false
                            onImportOutput()
                        })
                }
                if (cardState == CardState.Stale) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.save)) },
                        leadingIcon = { Icon(Icons.Rounded.Save, null) },
                        onClick = {
                            HapticFeedbacks.light()
                            menuExpanded = false
                            onSave()
                        })

                }
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.brisque_analysis)) },
                    leadingIcon = {
                        Box(
                            modifier = Modifier.size(24.dp), contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "B",
                                fontStyle = FontStyle.Italic,
                                fontWeight = FontWeight.Bold,
                                fontSize = 21.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    },
                    onClick = {
                        HapticFeedbacks.light()
                        menuExpanded = false
                        onBrisque()
                    })
                if (cardState != CardState.Processing) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.remove)) },
                        leadingIcon = {
                            Icon(
                                Icons.Rounded.Delete, null, tint = MaterialTheme.colorScheme.error
                            )
                        },
                        onClick = {
                            HapticFeedbacks.heavy()
                            menuExpanded = false
                            onRemove()
                        })
                }
            }
        }
    }
}
