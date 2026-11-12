/*
 * SPDX-FileCopyrightText: 2025 - 2026 dryerlint <https://codeberg.org/dryerlint>
 * SPDX-License-Identifier: GNU Affero General Public License v3.0 or later
 */

package com.je.dejpeg.ui.processing

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.res.Configuration
import android.net.Uri
import android.view.animation.PathInterpolator
import androidx.activity.compose.PredictiveBackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColor
import androidx.compose.animation.core.Spring
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
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
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
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.je.dejpeg.R
import com.je.dejpeg.data.AppTheme
import com.je.dejpeg.data.HapticPatterns
import com.je.dejpeg.ui.brisque.SaveState
import com.je.dejpeg.ui.components.CancelProcessingDialog
import com.je.dejpeg.ui.components.CardWrapper
import com.je.dejpeg.ui.components.CornerRole
import com.je.dejpeg.ui.components.GroupedListSpacing
import com.je.dejpeg.ui.components.ImageSourceDialog
import com.je.dejpeg.ui.components.MorphButton
import com.je.dejpeg.ui.components.PreparingShareDialog
import com.je.dejpeg.ui.components.RemoveImageDialog
import com.je.dejpeg.ui.components.SaveImageDialog
import com.je.dejpeg.ui.components.ScreenHorizontalPadding
import com.je.dejpeg.ui.components.StyledAlertDialog
import com.je.dejpeg.ui.components.SwipeConfig
import com.je.dejpeg.ui.components.SwipeSide
import com.je.dejpeg.ui.components.toListItemShapes
import com.je.dejpeg.ui.components.toShape
import com.je.dejpeg.ui.settings.EdgeSheet
import com.je.dejpeg.ui.settings.SettingsSheetContent
import com.je.dejpeg.ui.settings.SettingsViewModel
import com.je.dejpeg.ui.settings.SheetEdge
import com.je.dejpeg.ui.settings.StrengthControl
import com.je.dejpeg.ui.settings.rememberEdgeSheetState
import com.je.dejpeg.ui.settings.reserveSheetSpace
import com.je.dejpeg.ui.view.ImageMode
import com.je.dejpeg.ui.view.viewerMode
import kotlinx.coroutines.launch

/**
 * Owns no business state: everything that survives configuration changes comes from the
 * ViewModel. Only UI mechanics (settings sheet, back progress) live here.
 */
@Composable
fun ProcessingScreen(
    processingViewModel: ProcessingViewModel,
    settingsViewModel: SettingsViewModel,
    onOpenImage: (ImageMode) -> Unit = {},
    onNavigateToBrisque: (String) -> Unit = {},
    isActive: Boolean = true,
    initialSharedUris: List<Uri> = emptyList(),
    initiallySettingsExpanded: Boolean = false,
) {
    val images by processingViewModel.images.collectAsStateWithLifecycle()
    val isLoadingImages by processingViewModel.isLoadingImages.collectAsStateWithLifecycle()
    val loadingImagesProgress by processingViewModel.loadingImagesProgress.collectAsStateWithLifecycle()
    val selectedImageIds by processingViewModel.selectedImageIds.collectAsStateWithLifecycle()
    val dialog by processingViewModel.dialog.collectAsStateWithLifecycle()
    val saveState by processingViewModel.saveState.collectAsStateWithLifecycle()
    val uiState by processingViewModel.uiState.collectAsStateWithLifecycle()
    val swapSwipeActions by processingViewModel.swapSwipeActions.collectAsStateWithLifecycle()
    val strengthControl by settingsViewModel.strengthControl.collectAsStateWithLifecycle()

    var settingsExpanded by rememberSaveable { mutableStateOf(initiallySettingsExpanded) }
    var settingsBackProgress by remember { mutableFloatStateOf(0f) }
    val isSelectionMode = selectedImageIds.isNotEmpty()

    LifecycleEventEffect(Lifecycle.Event.ON_START) { processingViewModel.setAppVisible(true) }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { processingViewModel.setAppVisible(false) }

    LaunchedEffect(initialSharedUris) { processingViewModel.onSharedUris(initialSharedUris) }

    val pickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        processingViewModel.onPickerResult(result.resultCode, result.data)
    }
    LaunchedEffect(pickerLauncher) {
        processingViewModel.pickerRequests.collect { source ->
            processingViewModel.pickerIntent(source)
                .onSuccess { intent -> pickerLauncher.launch(intent) }
                .onFailure { error -> processingViewModel.onPickerLaunchFailed(error) }
        }
    }

    val backInterpolator = remember { PathInterpolator(0f, 0f, 0f, 1f) }
    PredictiveBackHandler(enabled = isActive && (settingsExpanded || isSelectionMode)) { progress ->
        try {
            progress.collect { backEvent ->
                val interpolated = backInterpolator.getInterpolation(
                    backEvent.progress.coerceIn(0f, 1f)
                )
                settingsBackProgress = if (settingsExpanded) interpolated else 0f
            }
            if (settingsExpanded) settingsExpanded = false else processingViewModel.clearSelection()
        } finally {
            settingsBackProgress = 0f
        }
    }

    val containerSize = LocalWindowInfo.current.containerSize
    val isLandscape = containerSize.width > containerSize.height

    val currentOnOpenImage by rememberUpdatedState(onOpenImage)
    val currentOnNavigateToBrisque by rememberUpdatedState(onNavigateToBrisque)
    val actions = remember(processingViewModel) {
        ProcessingActions(
            onProcess = processingViewModel::process,
            onSettingsExpandedChange = { settingsExpanded = it },
            onRequestCancel = processingViewModel::requestCancel,
            onRequestCancelAll = processingViewModel::requestCancelAll,
            onImport = processingViewModel::onImportClicked,
            onConfirmDialog = processingViewModel::confirmDialog,
            onDismissDialog = processingViewModel::dismissDialog,
            onPickSource = processingViewModel::onSourcePicked,
            onConfirmSave = processingViewModel::confirmSave,
            onConfirmOverwrite = processingViewModel::confirmOverwrite,
            onConfirmRemoval = processingViewModel::confirmRemoval,
            onToggleSelection = processingViewModel::onToggleSelection,
            onClearSelection = processingViewModel::clearSelection,
            onRequestSave = { ids, removeAfter ->
                processingViewModel.requestSave(ids, removeAfter)
            },
            onRequestRemoval = processingViewModel::requestRemoval,
            onCancelQueued = processingViewModel::cancelQueuedImage,
            onSaveSelection = {
                processingViewModel.requestSave(
                    processingViewModel.selectedImageIds.value, false
                )
            },
            onRemoveSelection = {
                processingViewModel.requestRemoval(processingViewModel.selectedImageIds.value)
            },
            onCompareSelection = {
                val ids = processingViewModel.selectedImageIds.value
                if (ids.size == 2) currentOnOpenImage(ImageMode.Compare(ids[0], ids[1]))
            },
            onSaveAll = processingViewModel::saveAllNow,
            onImportOutput = processingViewModel::importOutputAsNewImage,
            onOpenImage = { currentOnOpenImage(it) },
            onOpenBrisque = { currentOnNavigateToBrisque(it) },
            onGlobalStrengthChange = settingsViewModel::setGlobalStrength,
            onOidnScaleChange = settingsViewModel::setOidnInputScale,
            isCurrent = processingViewModel::isCurrent,
        )
    }

    ProcessingScreenContent(
        state = ProcessingScreenState(
            images = images,
            isLoadingImages = isLoadingImages,
            loadingImagesProgress = loadingImagesProgress,
            selectedImageIds = selectedImageIds,
            dialog = dialog,
            saveState = saveState,
            uiState = uiState,
            strengthControl = strengthControl,
            swapSwipeActions = swapSwipeActions,
            settingsExpanded = settingsExpanded,
            settingsBackProgress = settingsBackProgress,
            isLandscape = isLandscape,
        ),
        actions = actions,
        settingsSheet = {
            SettingsSheetContent(
                settingsViewModel = settingsViewModel,
                processingViewModel = processingViewModel,
            )
        },
    )
}

@Immutable
data class ProcessingScreenState(
    val images: List<ImageItem>,
    val isLoadingImages: Boolean,
    val loadingImagesProgress: Pair<Int, Int>?,
    val selectedImageIds: List<String>,
    val dialog: ProcessingDialog?,
    val saveState: SaveState,
    val uiState: ProcessingUiState,
    val strengthControl: StrengthControl,
    val swapSwipeActions: Boolean,
    val settingsExpanded: Boolean,
    val settingsBackProgress: Float,
    val isLandscape: Boolean,
) {
    val isSelectionMode: Boolean get() = selectedImageIds.isNotEmpty()
}

@Stable
class ProcessingActions(
    val onProcess: (String?) -> Unit,
    val onSettingsExpandedChange: (Boolean) -> Unit,
    val onRequestCancel: (String) -> Unit,
    val onRequestCancelAll: () -> Unit,
    val onImport: () -> Unit,
    val onConfirmDialog: () -> Unit,
    val onDismissDialog: () -> Unit,
    val onPickSource: (ImageSource) -> Unit,
    val onConfirmSave: (String?, Boolean, Boolean) -> Unit,
    val onConfirmOverwrite: (String) -> Unit,
    val onConfirmRemoval: (Boolean) -> Unit,
    val onToggleSelection: (String) -> Unit,
    val onClearSelection: () -> Unit,
    val onRequestSave: (List<String>, Boolean) -> Boolean,
    val onRequestRemoval: (List<String>) -> Unit,
    val onCancelQueued: (String) -> Unit,
    val onSaveSelection: () -> Unit,
    val onRemoveSelection: () -> Unit,
    val onCompareSelection: () -> Unit,
    val onSaveAll: () -> Unit,
    val onImportOutput: (String) -> Unit,
    val onOpenImage: (ImageMode) -> Unit,
    val onOpenBrisque: (String) -> Unit,
    val onGlobalStrengthChange: (Float) -> Unit,
    val onOidnScaleChange: (Float) -> Unit,
    val isCurrent: (String) -> Boolean,
)

@Composable
fun ProcessingScreenContent(
    state: ProcessingScreenState,
    actions: ProcessingActions,
    settingsSheet: @Composable () -> Unit,
) {
    val isLandscape = state.isLandscape
    val edge = if (isLandscape) SheetEdge.End else SheetEdge.Bottom
    val containerSize = LocalWindowInfo.current.containerSize
    val expandedSizePx = (if (isLandscape) containerSize.width else containerSize.height) / 2f
    val sheetState = rememberEdgeSheetState(state.settingsExpanded, expandedSizePx)
    val sheetColor = MaterialTheme.colorScheme.surface
    val shape = if (isLandscape) {
        RoundedCornerShape(bottomEnd = ScreenHorizontalPadding)
    } else {
        RoundedCornerShape(
            bottomStart = ScreenHorizontalPadding, bottomEnd = ScreenHorizontalPadding
        )
    }

    val strengthVisible = state.images.isNotEmpty() && state.strengthControl != StrengthControl.None

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .reserveSheetSpace(sheetState, edge),
        ) {
            TopBar(
                state = TopBarState(
                    isLandscape = isLandscape,
                    isSelectionMode = state.isSelectionMode,
                    settingsExpanded = state.settingsExpanded,
                    images = state.images,
                    selectedImageIds = state.selectedImageIds,
                    uiState = state.uiState,
                ),
                actions = actions,
            )
            AnimatedVisibility(
                visible = strengthVisible,
                enter = fadeIn() + expandVertically(expandFrom = Alignment.Top),
                exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Top)
            ) {
                StrengthCard(
                    control = state.strengthControl,
                    cornerRole = CornerRole.forPosition(1, state.images.size + 1),
                    onGlobalStrengthChange = actions.onGlobalStrengthChange,
                    onOidnScaleChange = actions.onOidnScaleChange,
                )
            }
            if (state.images.isEmpty()) {
                EmptyState(
                    modifier = Modifier
                        .weight(1f)
                        .clip(shape)
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.background),
                    onAdd = actions.onImport,
                )
            } else {
                ImageList(
                    state = state,
                    actions = actions,
                    shape = shape,
                    strengthVisible = strengthVisible,
                )
            }
        }

        EdgeSheet(
            modifier = Modifier.align(
                if (isLandscape) Alignment.CenterEnd else Alignment.BottomCenter
            ),
            state = sheetState,
            edge = edge,
            expanded = state.settingsExpanded,
            onExpandedChange = actions.onSettingsExpandedChange,
            expandedSizePx = expandedSizePx,
            backProgress = state.settingsBackProgress,
            background = sheetColor,
        ) {
            settingsSheet()
        }
    }

    ProcessingDialogHost(
        dialog = state.dialog,
        saveState = state.saveState,
        actions = ProcessingDialogActions(
            onConfirm = actions.onConfirmDialog,
            onDismiss = actions.onDismissDialog,
            onPickSource = actions.onPickSource,
            onConfirmSave = actions.onConfirmSave,
            onConfirmOverwrite = actions.onConfirmOverwrite,
            onConfirmRemoval = actions.onConfirmRemoval,
        ),
    )

    if (state.isLoadingImages) {
        val progress = state.loadingImagesProgress
        PreparingShareDialog(
            title = stringResource(R.string.loading_images),
            progress = progress?.let { it.first.toFloat() / it.second.toFloat() },
            progressText = progress?.let {
                stringResource(R.string.loading_image_progress, it.first, it.second)
            })
    }
}

@Composable
private fun StrengthCard(
    control: StrengthControl,
    cornerRole: CornerRole,
    onGlobalStrengthChange: (Float) -> Unit,
    onOidnScaleChange: (Float) -> Unit,
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
        shape = cornerRole.toShape(),
    ) {
        Column(Modifier.padding(ScreenHorizontalPadding)) {
            when (control) {
                is StrengthControl.OidnScale -> {
                    val locale = LocalConfiguration.current.locales[0]
                    val displayValue = if (control.value == 0f) {
                        stringResource(R.string.text_auto)
                    } else {
                        String.format(locale, "%.1f", control.value)
                    }
                    StrengthSlider(
                        label = stringResource(R.string.input_scale, displayValue),
                        value = control.value,
                        steps = 19,
                        range = 0f..10f,
                        onChange = onOidnScaleChange,
                    )
                }

                is StrengthControl.FBCNNStrength -> StrengthSlider(
                    label = stringResource(R.string.strength, control.value.toInt()),
                    value = control.value,
                    steps = 19,
                    range = 0f..100f,
                    onChange = onGlobalStrengthChange,
                )

                StrengthControl.None -> Unit
            }
        }
    }
}

@Suppress("SameParameterValue")
@Composable
private fun StrengthSlider(
    label: String,
    value: Float,
    steps: Int,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit,
) {
    Text(
        label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium
    )
    Spacer(Modifier.height(8.dp))
    val sliderState = rememberSliderState(
        value = value,
        steps = steps,
        trackRange = range,
    )
    LaunchedEffect(value) {
        sliderState.value = value
    }
    Slider(
        state = sliderState, onValueChange = {
            if (it != value) {
                HapticPatterns.tap()
                onChange(it)
            }
        }, modifier = Modifier
            .fillMaxWidth()
            .height(24.dp)
    )
}

@SuppressLint("MissingHapticFeedback")
@Composable
private fun EmptyState(modifier: Modifier, onAdd: () -> Unit) {
    val buttonInteractionSource = remember { MutableInteractionSource() }
    val onAddClick = {
        HapticPatterns.tap()
        onAdd()
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier.verticalScroll(rememberScrollState()),
    ) {
        Box(
            Modifier
                .widthIn(min = 280.dp, max = 360.dp)
                .heightIn(min = 240.dp)
                .clickable(
                    onClickLabel = stringResource(R.string.add_images),
                    role = Role.Button,
                    interactionSource = buttonInteractionSource,
                    indication = null,
                ) { onAddClick() }
                .padding(20.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Rounded.AddPhotoAlternate,
                    contentDescription = null,
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
                    onClick = onAddClick,
                    label = stringResource(R.string.add_images),
                )
            }
        }
    }
}

@Composable
private fun ColumnScope.ImageList(
    state: ProcessingScreenState,
    actions: ProcessingActions,
    shape: RoundedCornerShape,
    strengthVisible: Boolean,
) {
    val accountForSlider = if (strengthVisible) state.images.size + 1 else state.images.size
    val cardActions = remember(actions) {
        ImageCardActions(
            onProcess = actions.onProcess,
            onToggleSelection = actions.onToggleSelection,
            onClearSelection = actions.onClearSelection,
            onRequestSave = actions.onRequestSave,
            onRequestRemoval = actions.onRequestRemoval,
            onCancelProcessing = actions.onRequestCancel,
            onCancelQueued = actions.onCancelQueued,
            isCurrent = actions.isCurrent,
            onSaveSelection = actions.onSaveSelection,
            onRemoveSelection = actions.onRemoveSelection,
            onCompareSelection = actions.onCompareSelection,
            onImportOutput = actions.onImportOutput,
            onOpenImage = actions.onOpenImage,
            onNavigateToBrisque = actions.onOpenBrisque,
        )
    }
    val placementSpec = MaterialTheme.motionScheme.defaultSpatialSpec<IntOffset>()
    val fadeSpec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    val isSelectionMode = state.isSelectionMode
    val selectedIds = state.selectedImageIds

    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .weight(1f)
            .clip(shape)
            .background(MaterialTheme.colorScheme.background)
            .padding(start = ScreenHorizontalPadding, end = ScreenHorizontalPadding),
        verticalArrangement = Arrangement.spacedBy(GroupedListSpacing),
        contentPadding = PaddingValues(
            bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 8.dp
        )
    ) {
        itemsIndexed(items = state.images, key = { _, image -> image.id }) { index, image ->
            val isSelected = selectedIds.contains(image.id)
            ImageCard(
                modifier = Modifier.animateItem(
                    fadeInSpec = fadeSpec,
                    fadeOutSpec = fadeSpec,
                    placementSpec = placementSpec,
                ),
                image = image,
                isSelected = isSelected,
                selectedCount = if (isSelected) selectedIds.size else 0,
                isSelectionMode = isSelectionMode,
                isCompareReady = selectedIds.size == 2,
                cornerRole = CornerRole.forPosition(
                    if (strengthVisible) index + 2 else index + 1,
                    accountForSlider,
                ),
                swapSwipeActions = state.swapSwipeActions,
                actions = cardActions,
            )
        }
    }
}

@Stable
class ImageCardActions(
    val onProcess: (String?) -> Unit,
    val onToggleSelection: (String) -> Unit,
    val onClearSelection: () -> Unit,
    val onRequestSave: (List<String>, Boolean) -> Boolean,
    val onRequestRemoval: (List<String>) -> Unit,
    val onCancelProcessing: (String) -> Unit,
    val onCancelQueued: (String) -> Unit,
    val isCurrent: (String) -> Boolean,
    val onSaveSelection: () -> Unit,
    val onRemoveSelection: () -> Unit,
    val onCompareSelection: () -> Unit,
    val onImportOutput: (String) -> Unit,
    val onOpenImage: (ImageMode) -> Unit,
    val onNavigateToBrisque: (String) -> Unit,
)

@Composable
fun ImageCard(
    modifier: Modifier = Modifier,
    image: ImageItem,
    isSelected: Boolean,
    selectedCount: Int,
    isSelectionMode: Boolean,
    isCompareReady: Boolean,
    cornerRole: CornerRole,
    swapSwipeActions: Boolean,
    actions: ImageCardActions,
) {
    val isProcessing = image.isProcessing
    val cardState = image.cardState
    val positiveAction: () -> (() -> Unit)? = {
        HapticPatterns.tap()
        if (image.outputBitmap != null) {
            actions.onRequestSave(listOf(image.id), false)
        } else {
            actions.onProcess(image.id)
        }
        null
    }

    val negativeAction: () -> (() -> Unit)? = {
        HapticPatterns.tap()
        when {
            isProcessing && actions.isCurrent(image.id) -> {
                actions.onCancelProcessing(image.id)
                null
            }

            isProcessing -> {
                actions.onCancelQueued(image.id)
                null
            }

            image.outputBitmap != null -> {
                actions.onRequestRemoval(listOf(image.id))
                null
            }

            else -> {
                { actions.onRequestRemoval(listOf(image.id)) }
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

    CardWrapper(
        modifier = modifier,
        config = config,
    ) {
        SegmentedListItem(
            selected = isSelected,
            colors = ListItemDefaults.segmentedColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                selectedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest
            ),
            shapes = cornerRole.toListItemShapes(),
            contentPadding = PaddingValues(0.dp),
            modifier = Modifier,
            onClick = {
                HapticPatterns.tap()
                when {
                    isProcessing -> Unit
                    isSelectionMode -> actions.onToggleSelection(image.id)
                    else -> actions.onOpenImage(image.viewerMode())
                }
            },
            onLongClick = {
                if (!isProcessing) {
                    actions.onToggleSelection(image.id)
                }
            },
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
                                .clip(RoundedCornerShape(if (isSelected) 24.dp else 12.dp)),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Image(
                                imagePreview,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
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
                            chunkProgress = image.chunkProgress,
                            onProcess = actions.onProcess,
                            onRemove = {
                                if (isSelectionMode) {
                                    HapticPatterns.tap()
                                    actions.onRemoveSelection()
                                    null
                                } else {
                                    negativeAction()
                                }
                            },
                            onBrisque = { actions.onNavigateToBrisque(image.id) },
                            onSave = {
                                if (isSelectionMode) {
                                    actions.onSaveSelection()
                                } else {
                                    actions.onRequestSave(listOf(image.id), false)
                                }
                            },
                            onImportOutput = { actions.onImportOutput(image.id) },
                            isCompareReady = isCompareReady,
                            selectedCount = selectedCount,
                            onCompare = actions.onCompareSelection,
                            clearSelection = actions.onClearSelection,
                        )
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
        onDismissRequest = {}, properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
        ), content = {
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

@Immutable
data class ProcessingDialogActions(
    val onConfirm: () -> Unit = {},
    val onDismiss: () -> Unit = {},
    val onPickSource: (ImageSource) -> Unit = {},
    val onConfirmSave: (String?, Boolean, Boolean) -> Unit = { _, _, _ -> },
    val onConfirmOverwrite: (String) -> Unit = {},
    val onConfirmRemoval: (Boolean) -> Unit = {},
)

@Composable
fun ProcessingDialogHost(
    dialog: ProcessingDialog?,
    saveState: SaveState,
    actions: ProcessingDialogActions,
) {
    when (dialog) {
        is ProcessingDialog.CancelImage -> CancelProcessingDialog(
            imageFilename = dialog.filename,
            dismiss = actions.onDismiss,
            onConfirm = actions.onConfirm,
        )

        ProcessingDialog.CancelAll -> CancelProcessingDialog(
            imageFilename = null,
            dismiss = actions.onDismiss,
            onConfirm = actions.onConfirm,
        )

        ProcessingDialog.ImageSource -> ImageSourceDialog(
            onDismiss = actions.onDismiss,
            onPick = actions.onPickSource,
        )

        is ProcessingDialog.ProcessingError -> ErrorDialog(
            title = stringResource(R.string.error_processing_title),
            message = dialog.message,
            copyText = stringResource(R.string.copy),
            onDismiss = actions.onDismiss,
        )

        is ProcessingDialog.SaveError -> ErrorDialog(
            title = stringResource(R.string.error_saving_image_title),
            message = dialog.message,
            copyText = stringResource(R.string.copy),
            onDismiss = actions.onDismiss,
        )

        ProcessingDialog.GpuCacheCreating -> ErrorDialog(
            title = stringResource(R.string.gpu_cache_title),
            message = stringResource(R.string.gpu_cache_text),
            onDismiss = actions.onDismiss,
        )

        is ProcessingDialog.SaveOptions -> SaveImageDialog(
            defaultFilename = dialog.defaultFilename,
            overwriteMode = false,
            onDismissRequest = actions.onDismiss,
            onSave = actions.onConfirmSave,
        )

        is ProcessingDialog.ConfirmOverwrite -> SaveImageDialog(
            defaultFilename = dialog.filename,
            overwriteMode = true,
            onDismissRequest = actions.onDismiss,
            onSave = { name, _, _ -> actions.onConfirmOverwrite(name) },
        )

        is ProcessingDialog.ConfirmRemoval -> RemoveImageDialog(
            imageFilename = dialog.filename,
            count = dialog.imageIds.size,
            onDismissRequest = actions.onDismiss,
            onDismissButton = { actions.onConfirmRemoval(false) },
            onSaveAndRemove = { actions.onConfirmRemoval(true) },
        )

        null -> Unit
    }
    if (dialog !is ProcessingDialog.SaveError) {
        (saveState as? SaveState.Saving)?.let { SaveProgressDialog(it) }
    }
}

@Composable
private fun ErrorDialog(
    title: String,
    message: String,
    copyText: String? = null,
    onDismiss: () -> Unit,
) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    StyledAlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = onDismiss,
        dismissButton = {
            scope.launch {
                try {
                    clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(null, message)))
                } finally {
                    onDismiss()
                }
            }
        },
        title = { Text(title) },
        contents = { Text(message) },
        confirmButtonText = stringResource(R.string.ok),
        dismissButtonText = copyText.orEmpty(),
    )
}

@Composable
private fun ImageCardSplitButton(
    modifier: Modifier = Modifier,
    image: ImageItem,
    cardState: CardState,
    chunkProgress: Float = -1f,
    onProcess: (String?) -> Unit,
    onRemove: () -> (() -> Unit)?,
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
                        onRemove()?.invoke()
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
                            contentDescription = stringResource(R.string.cancel),
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
                            contentDescription = stringResource(R.string.more_options),
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
                        menuExpanded = false
                        onRemove()?.invoke()
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
    val images = remember {
        listOf(
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
    val actions = remember {
        ProcessingActions(
            onProcess = {},
            onSettingsExpandedChange = {},
            onRequestCancel = {},
            onRequestCancelAll = {},
            onImport = {},
            onConfirmDialog = {},
            onDismissDialog = {},
            onPickSource = {},
            onConfirmSave = { _, _, _ -> },
            onConfirmOverwrite = {},
            onConfirmRemoval = {},
            onToggleSelection = {},
            onClearSelection = {},
            onRequestSave = { _, _ -> false },
            onRequestRemoval = {},
            onCancelQueued = {},
            onSaveSelection = {},
            onRemoveSelection = {},
            onCompareSelection = {},
            onSaveAll = {},
            onImportOutput = {},
            onOpenImage = {},
            onOpenBrisque = {},
            onGlobalStrengthChange = {},
            onOidnScaleChange = {},
            isCurrent = { false },
        )
    }

    AppTheme {
        ProcessingScreenContent(
            state = ProcessingScreenState(
                images = images,
                isLoadingImages = false,
                loadingImagesProgress = null,
                selectedImageIds = emptyList(),
                dialog = null,
                saveState = SaveState.Idle,
                uiState = ProcessingUiState.Idle,
                strengthControl = StrengthControl.FBCNNStrength(50f),
                swapSwipeActions = false,
                settingsExpanded = true,
                settingsBackProgress = 0f,
                isLandscape = LocalWindowInfo.current.containerSize.width > LocalWindowInfo.current.containerSize.height,
            ),
            actions = actions,
            settingsSheet = {},
        )
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
