/*
 * SPDX-FileCopyrightText: 2025 - 2026 dryerlint <https://codeberg.org/dryerlint>
 * SPDX-License-Identifier: GNU Affero General Public License v3.0 or later
 */

@file:Suppress("SpellCheckingInspection")

package com.je.dejpeg.ui.processing

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.Configuration
import android.net.Uri
import android.view.animation.PathInterpolator
import androidx.activity.BackEventCompat
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColor
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Slider
import androidx.compose.material3.SplitButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.WavyProgressIndicatorDefaults
import androidx.compose.material3.rememberSliderState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import androidx.core.graphics.createBitmap
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.je.dejpeg.App
import com.je.dejpeg.R
import com.je.dejpeg.data.AppPreferences
import com.je.dejpeg.data.AppTheme
import com.je.dejpeg.data.HapticPatterns
import com.je.dejpeg.data.ImageFlowDialogs
import com.je.dejpeg.data.ImageRepository
import com.je.dejpeg.data.rememberImageFlows
import com.je.dejpeg.ui.brisque.SaveState
import com.je.dejpeg.ui.components.CancelProcessingDialog
import com.je.dejpeg.ui.components.CardWrapper
import com.je.dejpeg.ui.components.CornerRole
import com.je.dejpeg.ui.components.GroupedListSpacing
import com.je.dejpeg.ui.components.ImageSourceDialog
import com.je.dejpeg.ui.components.MorphButton
import com.je.dejpeg.ui.components.PreparingShareDialog
import com.je.dejpeg.ui.components.ScreenHorizontalPadding
import com.je.dejpeg.ui.components.SnackbarController
import com.je.dejpeg.ui.components.SnackbarDuration
import com.je.dejpeg.ui.components.SnackbarEvents
import com.je.dejpeg.ui.components.StyledAlertDialog
import com.je.dejpeg.ui.components.SwipeConfig
import com.je.dejpeg.ui.components.SwipeSide
import com.je.dejpeg.ui.components.toListItemShapes
import com.je.dejpeg.ui.components.toShape
import com.je.dejpeg.ui.settings.EdgeSheet
import com.je.dejpeg.ui.settings.SettingsSheetContent
import com.je.dejpeg.ui.settings.SettingsViewModel
import com.je.dejpeg.ui.settings.SheetEdge
import com.je.dejpeg.ui.settings.rememberEdgeSheetState
import com.je.dejpeg.ui.settings.reserveSheetSpace
import com.je.dejpeg.ui.view.ImageMode
import com.je.dejpeg.ui.view.viewerMode
import com.je.dejpeg.utils.ModelType
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
fun ProcessingScreen(
    processingViewModel: ProcessingViewModel,
    settingsViewModel: SettingsViewModel,
    imageRepository: ImageRepository,
    onOpenImage: (ImageMode) -> Unit = {},
    onNavigateToBrisque: (String) -> Unit = {},
    isActive: Boolean = true,
    initialSharedUris: List<Uri> = emptyList(),
    onRemoveSharedUri: (Uri) -> Unit = {},
    initiallySettingsExpanded: Boolean = false,
) {
    val context = App.ctx
    val inPreview = LocalInspectionMode.current
    val appPreferences = remember { AppPreferences() }
    val defaultImageSource = remember { appPreferences.loadDefaultImageSource() }
    val swapSwipeActions by produceState(initialValue = appPreferences.loadSwapSwipeActions()) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == AppPreferences.KEY_SWAP_SWIPE_ACTIONS) {
                value = appPreferences.loadSwapSwipeActions()
            }
        }
        appPreferences.prefs().registerOnSharedPreferenceChangeListener(listener)
        awaitDispose {
            appPreferences.prefs().unregisterOnSharedPreferenceChangeListener(listener)
        }
    }
    val images by imageRepository.images.collectAsState()
    val globalStrength by settingsViewModel.globalStrength.collectAsState()
    val activeSelection by settingsViewModel.activeSelection.collectAsState()
    val processingMode = activeSelection.type
    val oidnInputScale by settingsViewModel.oidnInputScale.collectAsState()
    val noModelMessage = stringResource(R.string.no_model_installed_title)
    val scope = rememberCoroutineScope()
    val isLoadingImages by imageRepository.isLoadingImages.collectAsState()
    val loadingImagesProgress by imageRepository.loadingImagesProgress.collectAsState()
    val processingErrorDialog by processingViewModel.processingErrorDialog.collectAsState()
    val gpuCacheCreatingDialog by processingViewModel.gpuCacheCreatingDialog.collectAsState()
    var imageIdToCancel by remember { mutableStateOf<String?>(null) }
    var showImageSourceDialog by remember { mutableStateOf(false) }
    var settingsExpanded by rememberSaveable { mutableStateOf(initiallySettingsExpanded) }
    var settingsBackProgress by remember { mutableFloatStateOf(0f) }
    var showCancelAllDialog by remember { mutableStateOf(false) }
    val showSaveDialog = remember { appPreferences.loadShowSaveDialog() }
    var selectedImageIds by remember { mutableStateOf<List<String>>(emptyList()) }
    val isSelectionMode = selectedImageIds.isNotEmpty()

    val flows = rememberImageFlows(
        images = images,
        showSaveDialog = showSaveDialog,
        processingViewModel = processingViewModel,
        appPreferences = appPreferences,
        onRemoveSharedUri = { uri -> releaseUri(uri, context, onRemoveSharedUri) },
    )

    val toggleSelection: (String) -> Unit = { id ->
        selectedImageIds = if (selectedImageIds.contains(id)) {
            selectedImageIds - id
        } else {
            selectedImageIds + id
        }
    }
    val clearSelection: () -> Unit = { selectedImageIds = emptyList() }

    val dispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentIsActive by rememberUpdatedState(isActive)
    val currentSettingsExpanded by rememberUpdatedState(settingsExpanded)
    val currentIsSelectionMode by rememberUpdatedState(isSelectionMode)
    val predictiveBackCallback = remember {
        object : OnBackPressedCallback(false) {
            override fun handleOnBackProgressed(backEvent: BackEventCompat) {
                val interpolatedProgress = PathInterpolator(0f, 0f, 0f, 1f).getInterpolation(
                    backEvent.progress.coerceIn(0f, 1f)
                )
                settingsBackProgress = if (currentSettingsExpanded) {
                    interpolatedProgress
                } else {
                    0f
                }
            }

            override fun handleOnBackPressed() {
                if (currentSettingsExpanded) {
                    settingsExpanded = false
                } else if (currentIsSelectionMode) {
                    clearSelection()
                }
                settingsBackProgress = 0f
            }

            override fun handleOnBackCancelled() {
                settingsBackProgress = 0f
            }
        }
    }

    DisposableEffect(dispatcher, lifecycleOwner, predictiveBackCallback) {
        dispatcher?.addCallback(lifecycleOwner, predictiveBackCallback)
        onDispose { predictiveBackCallback.remove() }
    }

    SideEffect {
        predictiveBackCallback.isEnabled =
            currentIsActive && (currentSettingsExpanded || currentIsSelectionMode)
    }

    fun tryProcess(block: () -> Unit) {
        if (!settingsViewModel.hasActiveModel(processingMode)) scope.launch {
            SnackbarController.pushEvent(
                SnackbarEvents.MessageEvent(
                    message = noModelMessage, duration = SnackbarDuration.Long
                )
            )
        } else block()
    }

    fun takeProcess(id: String? = null) {
        val ids = when {
            id != null -> listOf(id)
            isSelectionMode -> selectedImageIds
            else -> images.map { it.id }
        }
        ids.forEach(processingViewModel::processImage)
    }

    LaunchedEffect(images) {
        flows.prune()
        imageIdToCancel = imageIdToCancel?.takeIf { id -> images.any { it.id == id } }
        selectedImageIds = selectedImageIds.filter { id -> images.any { it.id == id } }
    }

    LaunchedEffect(Unit) {
        if (!inPreview) {
            processingViewModel.initialize(context)
            processingViewModel.serviceHelperRegister()
        }
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

    val imagePickerLauncher = if (inPreview) {
        null
    } else {
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val uris = mutableListOf<Uri>()
                result.data?.clipData?.let { clipData ->
                    for (i in 0 until clipData.itemCount) clipData.getItemAt(i).uri?.let {
                        uris.add(
                            it
                        )
                    }
                } ?: result.data?.data?.let { uris.add(it) }
                ?: processingViewModel.getCameraPhotoUri()?.let {
                    uris.add(it)
                    processingViewModel.clearCameraPhotoUri()
                }
                if (uris.isNotEmpty()) {
                    imageRepository.addImagesFromUris(context, uris)
                    processingViewModel.notifyImagePicked()
                }
            }
        }
    }
    LaunchedEffect(imagePickerLauncher) {
        if (imagePickerLauncher != null) {
            processingViewModel.setImagePickerLauncher(imagePickerLauncher)
        }
    }

    fun launchImportIntent() {
        when (defaultImageSource) {
            "gallery" -> processingViewModel.launchGalleryPicker()
            "internal" -> processingViewModel.launchInternalPhotoPicker()
            "documents" -> processingViewModel.launchDocumentsPicker()
            "camera" -> processingViewModel.launchCamera()
            else -> showImageSourceDialog = true
        }
    }

    val density = LocalDensity.current
    val containerSize = LocalWindowInfo.current.containerSize
    val containerWidthDp = with(density) { containerSize.width.toDp() }
    val containerHeightDp = with(density) { containerSize.height.toDp() }
    val isLandscape = containerWidthDp > containerHeightDp
    val edge = if (isLandscape) SheetEdge.End else SheetEdge.Bottom
    val expandedSizePx = (if (isLandscape) containerSize.width else containerSize.height) / 2f
    val sheetState = rememberEdgeSheetState(settingsExpanded, expandedSizePx)
    val sheetColor = MaterialTheme.colorScheme.surface
    val shape = if (isLandscape) {
        RoundedCornerShape(bottomEnd = ScreenHorizontalPadding)
    } else {
        RoundedCornerShape(
            bottomStart = ScreenHorizontalPadding, bottomEnd = ScreenHorizontalPadding
        )
    }

    val processingContent: @Composable ColumnScope.() -> Unit = {
        ProcessingTopBar(
            isLandscape = isLandscape,
            settingsExpanded = settingsExpanded,
            isSelectionMode = isSelectionMode,
            selectedImageIds = selectedImageIds,
            images = images,
            clearSelection = clearSelection,
            tryProcess = { block -> tryProcess(block) },
            takeProcess = { takeProcess() },
            flows = flows
        )

        val isFBCNN = activeSelection.modelName?.contains("fbcnn", ignoreCase = true) == true
        val strengthVisible = images.isNotEmpty() && (isFBCNN || processingMode == ModelType.OIDN)
        Row(
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background)
        ) {
            AnimatedVisibility(
                visible = strengthVisible,
                enter = fadeIn() + expandVertically(expandFrom = Alignment.Top),
                exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Top)
            ) {
                Card(
                    Modifier
                        .fillMaxWidth()
                        .padding(
                            bottom = GroupedListSpacing,
                            start = ScreenHorizontalPadding,
                            end = ScreenHorizontalPadding
                        ),
                    colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surfaceContainer),
                    shape = CornerRole.forPosition(1, images.count() + 1).toShape(),
                ) {
                    Column(Modifier.padding(ScreenHorizontalPadding)) {
                        if (processingMode == ModelType.OIDN) {
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
                            val oidnSliderState = rememberSliderState(
                                value = oidnInputScale,
                                steps = 19,
                                trackRange = 0f..10f,
                            )
                            LaunchedEffect(oidnInputScale) {
                                oidnSliderState.value = oidnInputScale
                            }
                            Slider(
                                state = oidnSliderState, onValueChange = {
                                    if (it != oidnInputScale) {
                                        HapticPatterns.tap()
                                        settingsViewModel.setOidnInputScale(it)
                                    }
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
                                    if (it != globalStrength) {
                                        HapticPatterns.tap()
                                        settingsViewModel.setGlobalStrength(it)
                                    }
                                }, modifier = Modifier
                                    .fillMaxWidth()
                                    .height(24.dp)
                            )
                        }
                    }
                }
            }
        }
        val buttonInteractionSource = remember { MutableInteractionSource() }
        val noImages = images.isEmpty()
        val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

        val toolbarGap = 10.dp
        var toolbarWidthPx by remember { mutableIntStateOf(0) }
        val toolbarReserve by animateDpAsState(
            targetValue = if (isLandscape && !isSelectionMode && toolbarWidthPx > 0) {
                with(density) { toolbarWidthPx.toDp() } + toolbarGap
            } else 0.dp,
            label = "toolbar_reserve",
        )
        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(shape)
                .background(MaterialTheme.colorScheme.background)
        ) {
            if (noImages) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                ) {
                    Box(
                        Modifier
                            .widthIn(min = 280.dp, max = 360.dp)
                            .heightIn(min = 240.dp)
                            .clickable(
                                interactionSource = buttonInteractionSource, indication = null
                            ) { HapticPatterns.tap(); launchImportIntent() }
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
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                stringResource(R.string.tap_to_add_images),
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(8.dp))
                            MorphButton(
                                interactionSource = buttonInteractionSource,
                                onClick = { HapticPatterns.tap(); launchImportIntent() },
                                label = stringResource(R.string.add_images),
                            )
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.padding(
                            start = ScreenHorizontalPadding,
                            end = ScreenHorizontalPadding
                        ),
                    verticalArrangement = Arrangement.spacedBy(GroupedListSpacing),
                    contentPadding = PaddingValues(
                        end = toolbarReserve,
                        // the 96.dp clearance is only needed when the toolbar sits at the bottom
                        bottom = if (isLandscape) navBarBottom else navBarBottom + 96.dp
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
                            viewModel = processingViewModel,
                            onToggleSelection = toggleSelection,
                            swapSwipeActions = swapSwipeActions,
                            onRequestSave = flows::requestSave,
                            tryProcess = { block -> tryProcess(block) },
                            takeProcess = { id -> takeProcess(id) },
                            onCancelProcessing = { imageIdToCancel = it },
                            onRequestRemoval = flows::requestRemoval,
                            onOpenImage = onOpenImage,
                            onNavigateToBrisque = onNavigateToBrisque,
                            onClearSelection = clearSelection,
                            strengthVisible = strengthVisible
                        )
                    }
                }
            }
            val space = if (isLandscape) toolbarGap else 0.dp
            val align = if (isLandscape) Alignment.CenterEnd else Alignment.BottomCenter
            Box(
                Modifier
                    .align(align)
                    .padding(bottom = navBarBottom + space, end = space)
                    .onSizeChanged { toolbarWidthPx = it.width }) {
                AnimatedContent(
                    targetState = !isSelectionMode,
                    label = "toolbar_visibility",
                    transitionSpec = {
                        fadeIn(spring(stiffness = Spring.StiffnessMedium)) + slideInVertically(
                            spring(dampingRatio = Spring.DampingRatioNoBouncy)
                        ) { it / 2 } togetherWith fadeOut(
                            spring(stiffness = Spring.StiffnessMedium)
                        ) + slideOutVertically(
                            spring(dampingRatio = Spring.DampingRatioNoBouncy)
                        ) { it / 2 }
                    },
                ) { visible ->
                    if (!visible) return@AnimatedContent
                    ProcessingFloatingToolbar(
                        isLandscape = isLandscape,
                        settingsExpanded = settingsExpanded,
                        onSettingsExpandedChange = { settingsExpanded = it },
                        onShowCancelAllDialogChange = { showCancelAllDialog = it },
                        images = images,
                        processingViewModel = processingViewModel,
                        flows = flows,
                        clearSelection = clearSelection,
                        tryProcess = { block -> tryProcess(block) },
                        takeProcess = { takeProcess() },
                        launchImportIntent = { launchImportIntent() },
                    )
                }
            }
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .reserveSheetSpace(sheetState, edge),
            content = processingContent
        )
        EdgeSheet(
            modifier = Modifier.align(
                if (isLandscape) Alignment.CenterEnd else Alignment.BottomCenter
            ),
            state = sheetState,
            edge = edge,
            expanded = settingsExpanded,
            onExpandedChange = { settingsExpanded = it },
            expandedSizePx = expandedSizePx,
            backProgress = settingsBackProgress,
            background = sheetColor,
        ) {
            SettingsSheetContent(
                settingsViewModel = settingsViewModel, processingViewModel = processingViewModel
            )
        }
    }

    imageIdToCancel?.let { targetId ->
        images.firstOrNull { it.id == targetId }?.let { image ->
            CancelProcessingDialog(
                imageFilename = image.filename,
                dismiss = { imageIdToCancel = null },
                onConfirm = {
                    processingViewModel.cancelQueuedImage(targetId)
                    imageIdToCancel = null
                })
        } ?: run { imageIdToCancel = null }
    }

    if (showImageSourceDialog) {
        ImageSourceDialog(
            onDismiss = { showImageSourceDialog = false }, viewModel = processingViewModel
        )
    }

    if (showCancelAllDialog) {
        CancelProcessingDialog(
            imageFilename = null,
            dismiss = { showCancelAllDialog = false },
            onConfirm = {
                processingViewModel.cancelProcessing()
                showCancelAllDialog = false
            })
    }

    val saveState by processingViewModel.saveState.collectAsState()
    (saveState as? SaveState.Error)?.let { err ->
        StyledAlertDialog(
            onDismissRequest = { processingViewModel.dismissSaveError() },
            confirmButton = { processingViewModel.dismissSaveError() },
            dismissButton = { processingViewModel.dismissSaveError() },
            title = { Text(stringResource(R.string.error_saving_image_title)) },
            contents = { Text(err.message) },
            confirmButtonText = stringResource(R.string.ok),
            dismissButtonText = stringResource(R.string.copy)
        )
    }

    (saveState as? SaveState.Saving)?.let { state ->
        SaveProgressDialog(state)
    }

    ImageFlowDialogs(flows)
    processingErrorDialog?.let { errorMsg ->
        StyledAlertDialog(
            onDismissRequest = { processingViewModel.dismissProcessingErrorDialog() },
            confirmButton = { processingViewModel.dismissProcessingErrorDialog() },
            dismissButton = { processingViewModel.dismissProcessingErrorDialog() },
            title = { Text(stringResource(R.string.error_processing_title)) },
            contents = { Text(errorMsg) },
            confirmButtonText = stringResource(R.string.ok),
            dismissButtonText = stringResource(R.string.copy)
        )
    }
    if (gpuCacheCreatingDialog) {
        StyledAlertDialog(
            onDismissRequest = { processingViewModel.dismissGpuCacheCreatingDialog() },
            confirmButton = { processingViewModel.dismissGpuCacheCreatingDialog() },
            dismissButton = { processingViewModel.dismissGpuCacheCreatingDialog() },
            title = { Text(stringResource(R.string.gpu_cache_title)) },
            contents = { Text(stringResource(R.string.gpu_cache_text)) },
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

private enum class CardState { Idle, Processing, Complete, Stale }

private fun cardStateOf(image: ImageItem): CardState = when {
    image.isProcessing -> CardState.Processing
    image.outputBitmap != null && image.isOutputStale -> CardState.Stale
    image.outputBitmap != null -> CardState.Complete
    else -> CardState.Idle
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
    onRequestSave: (List<String>, Boolean) -> Boolean,
    tryProcess: (() -> Unit) -> Unit,
    takeProcess: (String?) -> Unit,
    onCancelProcessing: (String) -> Unit,
    onRequestRemoval: (List<String>) -> Unit,
    onOpenImage: (ImageMode) -> Unit,
    onNavigateToBrisque: (String) -> Unit,
    onClearSelection: () -> Unit,
    strengthVisible: Boolean,
) {
    val realIndex = if (strengthVisible) index + 2 else index + 1
    val imagesCount = if (strengthVisible) images.count() + 1 else images.count()
    val isSelected = selectedImageIds.contains(image.id)
    val isProcessing = image.isProcessing
    val cardState = cardStateOf(image)
    val positiveAction: () -> (() -> Unit)? = {
        HapticPatterns.tap()
        if (image.outputBitmap != null) {
            onRequestSave(listOf(image.id), false)
            null
        } else {
            tryProcess { viewModel.processImage(image.id) }
            null
        }
    }

    val negativeAction: () -> (() -> Unit)? = {
        HapticPatterns.tap()
        when {
            isProcessing && viewModel.isCurrent(image.id) -> {
                onCancelProcessing(image.id)
                null
            }

            isProcessing -> {
                run { viewModel.cancelQueuedImage(image.id) }
                null
            }

            image.outputBitmap != null -> {
                onRequestRemoval(listOf(image.id))
                null
            }

            else -> {
                { onRequestRemoval(listOf(image.id)) }
            }
        }
    }
    val config = SwipeConfig(
        right = SwipeSide(
            action = positiveAction,
            icon = if (image.outputBitmap != null) Icons.Rounded.Save else Icons.Rounded.PlayArrow,
            iconTint = MaterialTheme.colorScheme.tertiary,
        ),
        left = SwipeSide(
            action = negativeAction,
            icon = if (isProcessing) Icons.Rounded.Close else Icons.Rounded.Delete,
            iconTint = MaterialTheme.colorScheme.error,
        ),
        swap = swapSwipeActions,
    )
    val imageCorner = if (isSelected) 24.dp else 12.dp

    CardWrapper(
        modifier = modifier.animateItem(
            fadeInSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium
            ), fadeOutSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium
            ), placementSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium
            )
        ), config = config
    ) {
        val progress = if (image.totalChunks > 1) {
            image.completedChunks.toFloat() / image.totalChunks.coerceAtLeast(1)
        } else -1f
        SegmentedListItem(
            selected = isSelected,
            colors = ListItemDefaults.segmentedColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                selectedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest
            ),
            shapes = CornerRole.forPosition(realIndex, imagesCount).toListItemShapes(),
            contentPadding = PaddingValues(0.dp),
            modifier = modifier,
            onClick = {
                HapticPatterns.tap()
                when {
                    isProcessing -> Unit
                    isSelectionMode -> onToggleSelection(image.id)
                    else -> onOpenImage(image.viewerMode())
                }
            },
            onLongClick = {
                if (!isProcessing) {
                    onToggleSelection(image.id)
                }
            },
            content = {
                Box(
                    Modifier.fillMaxSize()
                ) {
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
                                    .clip(RoundedCornerShape(imageCorner)),
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
                            ImageCardStatusChip(cardState, image.progress)
                            Spacer(Modifier.weight(1f))
                            ImageCardSplitButton(
                                image = image,
                                cardState = cardState,
                                onProcess = { id ->
                                    tryProcess {
                                        takeProcess(id)
                                    }
                                },
                                chunkProgress = progress,
                                onRemove = {
                                    if (isSelectionMode) {
                                        onRequestRemoval(selectedImageIds.toList())
                                    } else {
                                        negativeAction()?.invoke()
                                    }
                                },
                                onBrisque = {
                                    onNavigateToBrisque(
                                        image.id
                                    )
                                },
                                onSave = {
                                    onRequestSave(
                                        if (isSelectionMode) selectedImageIds else listOf(image.id),
                                        false
                                    )
                                },
                                onImportOutput = {
                                    viewModel.importOutputAsNewImage(image.id)
                                },
                                isCompareReady = selectedImageIds.size == 2,
                                selectedCount = if (isSelected) selectedImageIds.size else 0,
                                onCompare = {
                                    val (idA, idB) = selectedImageIds
                                    onOpenImage(ImageMode.Compare(idA, idB))
                                },
                                clearSelection = onClearSelection
                            )
                        }
                    }
                }
            })
    }
}

@Composable
private fun ImageCardStatusChip(state: CardState, progressText: String) {
    val label = when (state) {
        CardState.Complete -> stringResource(R.string.status_complete_ui)
        CardState.Processing -> progressText
        else -> stringResource(R.string.status_ready)
    }
    val visible = state != CardState.Processing || progressText.isNotEmpty()
    val fastFloat = MaterialTheme.motionScheme.fastSpatialSpec<Float>()
    val fastOffset = MaterialTheme.motionScheme.fastSpatialSpec<IntOffset>()
    AnimatedContent(
        targetState = visible to label,
        transitionSpec = {
            fadeIn(fastFloat) + slideInVertically(fastOffset) { it / 2 } togetherWith fadeOut(
                fastFloat
            ) + slideOutVertically(fastOffset) { -it / 2 }
        },
        label = "status_chip",
    ) { (show, text) ->
        if (show) {
            Surface(
                shape = RoundedCornerShape(32.dp),
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Text(
                    text,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(
                        horizontal = 8.dp, vertical = GroupedListSpacing
                    )
                )
            }
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

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
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
                modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center
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

@Composable
private fun ImageCardSplitButton(
    modifier: Modifier = Modifier,
    image: ImageItem,
    cardState: CardState,
    chunkProgress: Float = -1f,
    onProcess: (String?) -> Unit,
    onRemove: () -> Unit,
    onBrisque: () -> Unit,
    onSave: () -> Unit,
    onImportOutput: () -> Unit,
    isCompareReady: Boolean = false,
    onCompare: () -> Unit = {},
    selectedCount: Int,
    clearSelection: () -> Unit = {},
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val fastSpatialSpec = MaterialTheme.motionScheme.fastSpatialSpec<Float>()
    val fastOffsetSpec = MaterialTheme.motionScheme.fastSpatialSpec<IntOffset>()

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

    val saveLabel = stringResource(R.string.save).let { label ->
        if (selectedCount > 0) "$label ($selectedCount)" else label
    }
    val removeLabel = stringResource(R.string.remove).let { label ->
        if (selectedCount > 0) "$label ($selectedCount)" else label
    }
    val reprocessLabel = stringResource(R.string.reprocess).let { label ->
        if (selectedCount > 0) "$label ($selectedCount)" else label
    }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.5.dp)
    ) {
        SplitButtonDefaults.LeadingButton(
            onClick = {
                HapticPatterns.tap()
                when (cardState) {
                    CardState.Processing -> {}
                    CardState.Complete -> onSave()
                    CardState.Idle, CardState.Stale -> {
                        if (selectedCount > 1) onProcess(null)
                        else onProcess(image.id)
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
            AnimatedContent(
                targetState = cardState,
                transitionSpec = {
                    fadeIn(fastSpatialSpec) + slideInVertically(fastOffsetSpec) { it / 2 } togetherWith fadeOut(
                        fastSpatialSpec
                    ) + slideOutVertically(fastOffsetSpec) { -it / 2 }
                },
                label = "leading_button_content",
            ) { state ->
                when (state) {
                    CardState.Processing -> {
                        if (chunkProgress > 0f && chunkProgress < 1f) {
                            val anmProgress by animateFloatAsState(
                                targetValue = chunkProgress.coerceIn(0f, 1f),
                                animationSpec = WavyProgressIndicatorDefaults.ProgressAnimationSpec,
                                label = "chunk_progress"
                            )
                            LinearWavyProgressIndicator(
                                progress = { anmProgress },
                                color = contentColor,
                                trackColor = contentColor.copy(alpha = 0.3f),
                                modifier = Modifier.fillMaxWidth()
                            )
                        } else {
                            LinearWavyProgressIndicator(
                                color = contentColor,
                                trackColor = contentColor.copy(alpha = 0.3f),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    CardState.Complete, CardState.Idle, CardState.Stale -> {
                        val icon = when (state) {
                            CardState.Stale, CardState.Idle -> Icons.Rounded.PlayArrow
                            else -> Icons.Rounded.Save
                        }
                        val label = when (state) {
                            CardState.Stale -> stringResource(R.string.reprocess)
                            CardState.Complete -> stringResource(R.string.save_image)
                            else -> stringResource(R.string.process)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(icon, contentDescription = null)
                            Spacer(Modifier.width(4.dp))
                            Text(label, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }

        //noinspection MissingHapticFeedback
        Box {
            SplitButtonDefaults.TrailingButton(
                checked = menuExpanded,
                onCheckedChange = {
                    if (cardState == CardState.Processing) {
                        onRemove()
                    } else {
                        HapticPatterns.tap()
                        menuExpanded = it
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = containerColor,
                    contentColor = contentColor,
                ),
                modifier = Modifier.height(36.dp),
            ) {
                when (cardState) {
                    CardState.Processing -> {
                        Icon(
                            Icons.Rounded.Cancel,
                            contentDescription = null,
                            modifier = Modifier.size(SplitButtonDefaults.TrailingIconSize)
                        )
                    }

                    CardState.Complete, CardState.Idle, CardState.Stale -> {
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
                }
            }
            DropdownMenu(
                expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                if ((selectedCount > 1 && cardState != CardState.Processing) || cardState == CardState.Stale) {
                    DropdownMenuItem(
                        text = { Text(saveLabel) },
                        leadingIcon = { Icon(Icons.Rounded.Save, null) },
                        onClick = {
                            HapticPatterns.tap()
                            menuExpanded = false
                            onSave()
                            clearSelection()
                        })
                }
                if (cardState != CardState.Processing) {
                    DropdownMenuItem(text = { Text(removeLabel) }, leadingIcon = {
                        Icon(
                            Icons.Rounded.Delete, null, tint = MaterialTheme.colorScheme.error
                        )
                    }, onClick = {
                        HapticPatterns.tap()
                        menuExpanded = false
                        onRemove()
                        clearSelection()
                    })
                }
                if (cardState == CardState.Complete) {
                    DropdownMenuItem(text = { Text(reprocessLabel) }, leadingIcon = {
                        Icon(Icons.Rounded.Refresh, null, modifier = Modifier.size(26.dp))
                    }, onClick = {
                        HapticPatterns.tap()
                        menuExpanded = false
                        if (selectedCount > 1) onProcess(null)
                        else onProcess(image.id)
                        clearSelection()
                    })
                }
                if (isCompareReady) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.compare)) },
                        leadingIcon = { Icon(Icons.Rounded.SwapHoriz, null) },
                        onClick = {
                            HapticPatterns.tap()
                            menuExpanded = false
                            onCompare()
                            clearSelection()
                        })
                }
                if (cardState == CardState.Complete && selectedCount <= 1) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.import_output)) },
                        leadingIcon = { Icon(Icons.Rounded.AddPhotoAlternate, null) },
                        onClick = {
                            HapticPatterns.tap()
                            menuExpanded = false
                            onImportOutput()
                            clearSelection()
                        })
                }
                if (selectedCount <= 1) {
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
                            HapticPatterns.tap()
                            menuExpanded = false
                            onBrisque()
                            clearSelection()
                        })
                }
            }
        }
    }
}

@Preview(
    name = "Processing settings expanded (portrait)",
    showBackground = true,
    widthDp = 412,
    heightDp = 915,
//    uiMode = Configuration.UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_NORMAL
)
@Preview(
    name = "Processing settings expanded (landscape)",
    showBackground = true,
    widthDp = 915,
    heightDp = 412,
    uiMode = Configuration.UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_NORMAL
)
@Composable
private fun ProcessingScreenSettingsExpandedPreview() {
    val context = LocalContext.current
    val previewContext = remember(context) {
        App.initializeForPreview(context)
        context.applicationContext
    }

    val repository = remember {
        ImageRepository().apply {
            images.value = listOf(
                previewImageItem(
                    id = "preview-1",
                    filename = "photo.jpg",
                    size = "2x2",
                    color = android.graphics.Color.rgb(56, 92, 128),
                    outputColor = android.graphics.Color.rgb(78, 124, 168),
                ),
                previewImageItem(
                    id = "preview-2",
                    filename = "pixel.jpg",
                    size = "1x1",
                    color = android.graphics.Color.rgb(112, 82, 60),
                    processing = true,
                    progress = "Processing 45%",
                    completedChunks = 3,
                    totalChunks = 8,
                ),
                previewImageItem(
                    id = "preview-3",
                    filename = "image.jpg",
                    size = "3x2",
                    color = android.graphics.Color.rgb(64, 104, 72),
                ),
            )
        }
    }
    val processingViewModel = remember(repository) {
        ProcessingViewModel().apply { imageRepository = repository }
    }
    val settingsViewModel = remember { SettingsViewModel() }

    AppTheme {
        CompositionLocalProvider(LocalContext provides previewContext) {
            ProcessingScreen(
                processingViewModel = processingViewModel,
                settingsViewModel = settingsViewModel,
                imageRepository = repository,
                initiallySettingsExpanded = true,
            )
        }
    }
}

private fun previewImageItem(
    id: String,
    filename: String,
    size: String,
    color: Int,
    outputColor: Int? = null,
    processing: Boolean = false,
    progress: String = "",
    completedChunks: Int = 0,
    totalChunks: Int = 0,
): ImageItem {
    val input = createBitmap(160, 120).apply {
        eraseColor(color)
    }
    val output = outputColor?.let { outputTint ->
        createBitmap(160, 120).apply {
            eraseColor(outputTint)
        }
    }
    return ImageItem(
        id = id,
        uri = null,
        filename = filename,
        inputBitmap = input,
        outputBitmap = output,
        thumbnailBitmap = input,
        size = size,
        isProcessing = processing,
        progress = progress,
        completedChunks = completedChunks,
        totalChunks = totalChunks,
    )
}
