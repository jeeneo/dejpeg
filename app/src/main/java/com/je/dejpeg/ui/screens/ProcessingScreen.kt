/*
 * SPDX-FileCopyrightText: 2025 - 2026 dryerlint <https://codeberg.org/dryerlint>
 * SPDX-License-Identifier: GNU Affero General Public License v3.0 or later
 */

package com.je.dejpeg.ui.screens

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.view.animation.PathInterpolator
import androidx.activity.BackEventCompat
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.animateColor
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.EaseInOutSine
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.material3.ExtendedFloatingActionButton
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.je.dejpeg.App
import com.je.dejpeg.R
import com.je.dejpeg.data.AppPreferences
import com.je.dejpeg.data.HapticPatterns
import com.je.dejpeg.data.ImageFlowDialogs
import com.je.dejpeg.data.ImageRepository
import com.je.dejpeg.data.rememberImageFlows
import com.je.dejpeg.ui.components.BottomSheet
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
import com.je.dejpeg.ui.components.rememberMaterialPressState
import com.je.dejpeg.ui.components.toListItemShapes
import com.je.dejpeg.ui.viewmodel.ImageItem
import com.je.dejpeg.ui.viewmodel.ProcessingUiState
import com.je.dejpeg.ui.viewmodel.ProcessingViewModel
import com.je.dejpeg.ui.viewmodel.SaveState
import com.je.dejpeg.ui.viewmodel.SettingsViewModel
import com.je.dejpeg.utils.ModelType
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.roundToInt

private enum class CardState { Idle, Processing, Complete, Stale }

@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalFoundationApi::class,
    ExperimentalAnimationApi::class
)
@Composable
fun ProcessingScreen(
    processingViewModel: ProcessingViewModel,
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
    val isOidnMode = processingMode == ModelType.OIDN
    val activeModelName = activeSelection.modelName
    val supportsStrength = activeModelName?.contains("fbcnn", ignoreCase = true) == true
    val noModelMessage = stringResource(R.string.no_model_installed_title)
    val scope = rememberCoroutineScope()
    val isLoadingImages by imageRepository.isLoadingImages.collectAsState()
    val loadingImagesProgress by imageRepository.loadingImagesProgress.collectAsState()
    val processingErrorDialog by processingViewModel.processingErrorDialog.collectAsState()
    val gpuCacheCreatingDialog by processingViewModel.gpuCacheCreatingDialog.collectAsState()
    var imageIdToCancel by remember { mutableStateOf<String?>(null) }
    var showImageSourceDialog by remember { mutableStateOf(false) }
    var settingsExpanded by remember { mutableStateOf(false) }
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
        processingViewModel.initialize(context)
        processingViewModel.serviceHelperRegister()
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
    LaunchedEffect(Unit) { processingViewModel.setImagePickerLauncher(imagePickerLauncher) }

    fun launchImportIntent() {
        when (defaultImageSource) {
            "gallery" -> processingViewModel.launchGalleryPicker()
            "internal" -> processingViewModel.launchInternalPhotoPicker()
            "documents" -> processingViewModel.launchDocumentsPicker()
            "camera" -> processingViewModel.launchCamera()
            else -> showImageSourceDialog = true
        }
    }

    @Composable
    fun rememberMorphingFabCorner(
        baseCorner: Float, targetCorner: Float = 28f
    ): Pair<MutableInteractionSource, Dp> {
        val interaction = remember { MutableInteractionSource() }
        val press by rememberMaterialPressState(interaction)
        val corner = lerp(baseCorner, targetCorner, press)
        return interaction to corner.dp
    }

    val displayCount = if (isSelectionMode) selectedImageIds.size else images.size
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background)
                .padding(
                    bottom = 8.dp, start = ScreenHorizontalPadding, end = ScreenHorizontalPadding
                ), Arrangement.SpaceBetween, Alignment.CenterVertically
        ) {
            AnimatedContent(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .padding(end = 8.dp),
                targetState = isSelectionMode,
                label = "header_text",
                transitionSpec = {
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
                    fontWeight = FontWeight.Bold,
                    overflow = TextOverflow.Ellipsis,
                    maxLines = 1
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val uiState by processingViewModel.uiState.collectAsState()
                val isProcessing = uiState is ProcessingUiState.Processing
                val allComplete =
                    images.isNotEmpty() && images.all { it.outputBitmap != null && !it.isOutputStale && !it.isProcessing }
                val baseCorner = if (allComplete) 16f else 18f
                val (settingsInteraction, settingsCorner) = rememberMorphingFabCorner(baseCorner)
                FloatingActionButton(
                    shape = RoundedCornerShape(settingsCorner),
                    interactionSource = settingsInteraction,
                    onClick = {
                        HapticPatterns.tap()
                        if (!settingsExpanded) clearSelection()
                        settingsExpanded = !settingsExpanded
                    },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ) {
                    Icon(
                        if (settingsExpanded) Icons.Rounded.KeyboardArrowDown else Icons.Rounded.Settings,
                        contentDescription = stringResource(R.string.settings)
                    )
                }
                val (otherInteraction, otherCorner) = rememberMorphingFabCorner(baseCorner)
                if (images.isNotEmpty()) {
                    val containerColor by animateColorAsState(
                        if (isProcessing) MaterialTheme.colorScheme.errorContainer
                        else MaterialTheme.colorScheme.secondaryContainer, label = "fab_container"
                    )
                    val contentColor by animateColorAsState(
                        if (isProcessing) MaterialTheme.colorScheme.onErrorContainer
                        else MaterialTheme.colorScheme.onSecondaryContainer, label = "fab_content"
                    )
                    val (icon, labelRes) = when {
                        isProcessing -> Icons.Rounded.Close to R.string.cancel
                        allComplete -> Icons.Rounded.Save to R.string.save_all
                        else -> Icons.Rounded.PlayArrow to R.string.process
                    }
                    val contentDescription = stringResource(labelRes)
                    ExtendedFloatingActionButton(
                        shape = RoundedCornerShape(otherCorner),
                        interactionSource = otherInteraction,
                        expanded = allComplete,
                        icon = {
                            Crossfade(targetState = icon, label = "fab_icon") { animatedIcon ->
                                Icon(
                                    animatedIcon,
                                    contentDescription = if (allComplete) null else contentDescription
                                )
                            }
                        },
                        text = {
                            Crossfade(targetState = labelRes, label = "fab_text") { animatedLabel ->
                                Text(
                                    stringResource(animatedLabel),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        },
                        onClick = {
                            HapticPatterns.tap()
                            when {
                                isProcessing -> showCancelAllDialog = true
                                allComplete -> flows.saveAllNow()
                                else -> tryProcess { takeProcess() }
                            }
                        },
                        containerColor = containerColor,
                        contentColor = contentColor
                    )
                }
                val (addInteraction, addCorner) = rememberMorphingFabCorner(baseCorner)
                FloatingActionButton(
                    shape = RoundedCornerShape(addCorner),
                    interactionSource = addInteraction,
                    onClick = { HapticPatterns.tap(); launchImportIntent() },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ) {
                    Icon(
                        Icons.Rounded.Add, contentDescription = stringResource(R.string.add_images)
                    )
                }
            }
        }
        AnimatedVisibility(
            visible = images.isNotEmpty() && (supportsStrength || isOidnMode),
            enter = fadeIn() + expandVertically(expandFrom = Alignment.Top),
            exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Top)
        ) {
            Card(
                Modifier
                    .fillMaxWidth()
                    .padding(
                        bottom = ScreenHorizontalPadding,
                        start = ScreenHorizontalPadding,
                        end = ScreenHorizontalPadding
                    ),
                colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surfaceContainer),
                shape = RoundedCornerShape(ScreenHorizontalPadding)
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
                                prevScale = v
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
                                prevStrength = v
                            }; settingsViewModel.setGlobalStrength(v)
                            }, modifier = Modifier
                                .fillMaxWidth()
                                .height(24.dp)
                        )
                    }
                }
            }
        }
        val density = LocalDensity.current
        val containerHeightDp = with(density) {
            LocalWindowInfo.current.containerSize.height.toDp()
        }
        val sheetHeight = containerHeightDp * 0.5f
        val sheetBackground = MaterialTheme.colorScheme.surface

        Column(Modifier.fillMaxSize()) {
            if (images.isEmpty()) {
                Box(
                    Modifier
                        .weight(1f)
                        .clip(
                            RoundedCornerShape(
                                bottomStart = 16.dp,
                                bottomEnd = 16.dp,
                            )
                        )
                        .background(MaterialTheme.colorScheme.background)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val buttonInteractionSource = remember { MutableInteractionSource() }
                        Box(
                            Modifier
                                .width(280.dp)
                                .height(240.dp)
                                .clip(RoundedCornerShape(28.dp))
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
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    stringResource(R.string.tap_to_add_images),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.height(8.dp))
                                //noinspection MissingHapticFeedback
                                MorphButton(
                                    interactionSource = buttonInteractionSource,
                                    onClick = { HapticPatterns.tap(); launchImportIntent() },
                                    label = stringResource(R.string.add_images),
                                )
                            }
                        }
                    }
                }
            } else {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(
                            RoundedCornerShape(
                                bottomStart = 16.dp, bottomEnd = 16.dp
                            )
                        )
                        .background(MaterialTheme.colorScheme.background)
                        .padding(start = ScreenHorizontalPadding, end = ScreenHorizontalPadding),
                    Arrangement.SpaceBetween
                ) {
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
                                viewModel = processingViewModel,
                                onToggleSelection = toggleSelection,
                                swapSwipeActions = swapSwipeActions,
                                onRequestSave = flows::requestSave,
                                tryProcess = { block -> tryProcess(block) },
                                takeProcess = { id -> takeProcess(id) },
                                onCancelProcessing = { imageIdToCancel = it },
                                onRequestRemoval = flows::requestRemoval,
                                onNavigateToBeforeAfter = onNavigateToBeforeAfter,
                                onNavigateToBrisque = onNavigateToBrisque,
                                onNavigateToCompare = onNavigateToCompare,
                                onClearSelection = clearSelection
                            )
                        }
                    }
                }
            }
            BottomSheet(
                expanded = settingsExpanded,
                onExpandedChange = { settingsExpanded = it },
                expandedHeight = sheetHeight,
                modifier = Modifier.fillMaxWidth(),
                backProgress = settingsBackProgress,
                background = sheetBackground
            ) {
                SettingsSheetContent(
                    settingsViewModel = settingsViewModel, processingViewModel = processingViewModel
                )
            }
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
    onNavigateToBeforeAfter: (String) -> Unit,
    onNavigateToBrisque: (String) -> Unit,
    onNavigateToCompare: (String, String) -> Unit,
    onClearSelection: () -> Unit,
) {
    val isSelected = selectedImageIds.contains(image.id)
    val isProcessing = image.isProcessing

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

    CardWrapper(
        modifier = modifier.animateItem(
            fadeInSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium
            ), fadeOutSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium
            ), placementSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium
            )
        ),
        config = config,
        rightSwipeEnabled = !isSelectionMode && !isProcessing,
    ) {
        val progressTint = MaterialTheme.colorScheme.primary
        val chunkFraction = if (image.totalChunks > 1) {
            image.completedChunks.toFloat() / image.totalChunks.coerceAtLeast(1)
        } else -1f
        val pulseAlpha by rememberInfiniteTransition().animateFloat(
            initialValue = 0.04f, targetValue = 0.11f, animationSpec = infiniteRepeatable(
                animation = tween(1200, easing = EaseInOutSine), repeatMode = RepeatMode.Reverse
            )
        )
        val baseColor by animateColorAsState(
            targetValue = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh
        )

        SegmentedListItem(
            selected = isSelected,
            colors = ListItemDefaults.segmentedColors(
                containerColor = Color.Transparent,
                selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer
            ),
            shapes = CornerRole.forPosition(index + 1, images.count()).toListItemShapes(),
            contentPadding = PaddingValues(0.dp),
            modifier = modifier,
            onClick = {
                HapticPatterns.tap()
                if (isProcessing) return@SegmentedListItem
                if (isSelectionMode) onToggleSelection(image.id)
                else onNavigateToBeforeAfter(image.id)
            },
            onLongClick = if (isProcessing) null else { -> run { onToggleSelection(image.id) } },
            content = {
                Box(
                    Modifier
                        .fillMaxSize()
                        .drawBehind {
                            drawRect(baseColor)
                            if (!isProcessing) return@drawBehind
                            if (chunkFraction >= 0f) {
                                val fadeWidth = 28.dp.toPx()
                                val baseAlpha = 0.15f
                                if (chunkFraction >= 1f) {
                                    drawRect(progressTint.copy(alpha = baseAlpha))
                                } else {
                                    val fillWidth = size.width * chunkFraction
                                    val fadeEnd = (fillWidth + fadeWidth).coerceAtMost(size.width)
                                    if (fadeEnd > 0f) {
                                        val fadeStartFraction =
                                            (fillWidth / fadeEnd).coerceIn(0f, 1f)
                                        drawRect(
                                            brush = Brush.horizontalGradient(
                                                colorStops = arrayOf(
                                                    0f to progressTint.copy(alpha = baseAlpha),
                                                    fadeStartFraction to progressTint.copy(alpha = baseAlpha),
                                                    1f to Color.Transparent
                                                ), startX = 0f, endX = fadeEnd
                                            )
                                        )
                                    }
                                }
                            } else {
                                drawRect(progressTint.copy(alpha = pulseAlpha))
                            }
                        }) {
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
                                            horizontal = 8.dp, vertical = GroupedListSpacing
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
                                            horizontal = 8.dp, vertical = GroupedListSpacing
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
                                            horizontal = 8.dp, vertical = GroupedListSpacing
                                        )
                                    )
                                }
                            }
                            Spacer(Modifier.weight(1f))
                            ImageCardSplitButton(
                                image = image,
                                isProcessing = isProcessing,
                                onProcess = { id ->
                                    tryProcess {
                                        takeProcess(id)
                                    }
                                },
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
                                    onNavigateToCompare(idA, idB)
                                },
                                clearSelection = onClearSelection
                            )
                        }
                    }
                }
            })
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

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ImageCardSplitButton(
    modifier: Modifier = Modifier,
    image: ImageItem,
    isProcessing: Boolean,
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

    val saveLabel = stringResource(R.string.save).let { label ->
        if (selectedCount > 0) "$label ($selectedCount)" else label
    }
    val removeLabel = stringResource(R.string.remove).let { label ->
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
                    CardState.Processing -> {
                        onRemove()
                    }

                    CardState.Complete -> {
                        onSave()
                    }

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
            Icon(leadingIcon, contentDescription = null)
            Spacer(Modifier.width(4.dp))
            Text(leadingLabel, style = MaterialTheme.typography.labelMedium)
        }
        //noinspection MissingHapticFeedback
        Box {
            SplitButtonDefaults.TrailingButton(
                checked = menuExpanded,
                onCheckedChange = { HapticPatterns.tap(); menuExpanded = it },
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
                if (cardState == CardState.Complete) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.reprocess)) },
                        leadingIcon = {
                            Icon(Icons.Rounded.Refresh, null, modifier = Modifier.size(26.dp))
                        },
                        onClick = {
                            HapticPatterns.tap()
                            menuExpanded = false
                            if (selectedCount > 1) onProcess(null)
                            else onProcess(image.id)
                            clearSelection()
                        })
                }
                if (cardState == CardState.Complete) {
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
