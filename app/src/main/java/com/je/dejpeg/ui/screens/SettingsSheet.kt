package com.je.dejpeg.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItemColors
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.je.dejpeg.App
import com.je.dejpeg.R
import com.je.dejpeg.data.AppPreferences
import com.je.dejpeg.data.AppTheme
import com.je.dejpeg.data.HapticPatterns
import com.je.dejpeg.data.ThreadUtils
import com.je.dejpeg.ui.components.CardWrapper
import com.je.dejpeg.ui.components.CornerRole
import com.je.dejpeg.ui.components.GroupedListSpacing
import com.je.dejpeg.ui.components.Heading
import com.je.dejpeg.ui.components.LabeledSwitch
import com.je.dejpeg.ui.components.PowerSlider
import com.je.dejpeg.ui.components.ScreenHorizontalPadding
import com.je.dejpeg.ui.components.SnackbarController
import com.je.dejpeg.ui.components.SnackbarDuration
import com.je.dejpeg.ui.components.SnackbarEvents
import com.je.dejpeg.ui.components.SwipeConfig
import com.je.dejpeg.ui.components.SwipeSide
import com.je.dejpeg.ui.components.rememberMaterialPressState
import com.je.dejpeg.ui.components.segmentedListShapes
import com.je.dejpeg.ui.components.toListItemShapes
import com.je.dejpeg.ui.viewmodel.ProcessingViewModel
import com.je.dejpeg.ui.viewmodel.SettingsViewModel
import com.je.dejpeg.utils.ModelManager
import com.je.dejpeg.utils.ModelType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheetContent(
    modifier: Modifier = Modifier,
    settingsViewModel: SettingsViewModel,
    processingViewModel: ProcessingViewModel,
) {
    val modelManager = remember { ModelManager.create(App.ctx) }
    val appPreferences = remember { AppPreferences() }
    val scope = rememberCoroutineScope()
    val showImportProgress = remember { mutableStateOf(false) }
    var importProgress by remember { mutableIntStateOf(0) }
    val importedModelMessage = stringResource(R.string.imported_model)
    var showSaveDialog by remember { mutableStateOf(appPreferences.loadShowSaveDialog()) }
    var defaultImageSource by remember { mutableStateOf(appPreferences.loadDefaultImageSource()) }
    var hapticsEnabled by remember { mutableStateOf(appPreferences.loadHapticFeedbackEnabled()) }
    var swapSwipeActions by remember { mutableStateOf(appPreferences.loadSwapSwipeActions()) }
    val modelInfoDialog = remember { mutableStateOf<Pair<String, String>?>(null) }
    val activeSelection by settingsViewModel.activeSelection.collectAsState()
    val processingMode = activeSelection.type
    val uriHandler = LocalUriHandler.current
    val importError = remember { mutableStateOf<String?>(null) }
    val importedModels by settingsViewModel.importedModels.collectAsState()
    val allModels = importedModels.flatMap { (type, names) -> names.map { name -> name to type } }

    val modelPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            showImportProgress.value = true
            importProgress = 0
            settingsViewModel.importModels(
                uris,
                onProgress = { importProgress = it },
                onSuccess = { name, _ ->
                    showImportProgress.value = false
                    scope.launch {
                        SnackbarController.pushEvent(
                            SnackbarEvents.MessageEvent(
                                message = importedModelMessage.format(name),
                                duration = SnackbarDuration.Short
                            )
                        )
                    }
                },
                onError = { error ->
                    importProgress = 0
                    importError.value = error
                })
        }
    }

    val colors = ListItemDefaults.segmentedColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        selectedContainerColor = MaterialTheme.colorScheme.outlineVariant
    )
    val currentTheme = App.state.appTheme.value
    var glassSlider by remember { mutableStateOf(appPreferences.loadGlassSlider()) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .fillMaxHeight()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(
                    top = 12.dp,
                    bottom = WindowInsets.navigationBars.asPaddingValues()
                        .calculateBottomPadding() + 90.dp,
                    start = 12.dp,
                    end = 12.dp
                )
        ) {
            Heading(stringResource(R.string.settings_title_models))
            val hasModels = allModels.isNotEmpty()
            val extractedMsg = stringResource(R.string.extracted_starter_models)
            val deletedMsg = stringResource(R.string.deleted_model)

            Spacer(modifier = Modifier.height(GroupedListSpacing))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(GroupedListSpacing),
            ) {
                SegmentedListItem(
                    modifier = Modifier.weight(1f), colors = colors, onClick = {
                        HapticPatterns.tap()
                        modelPickerLauncher.launch(arrayOf("*/*"))
                    }, onLongClick = {
                        scope.launch {
                            val extracted = withContext(Dispatchers.IO) {
                                modelManager.extractStarterModel()
                            }
                            if (extracted.isNotEmpty()) {
                                if (allModels.isEmpty() && !processingViewModel.isProcessingOrQueueActive()) {
                                    settingsViewModel.setActiveModel(ModelManager.STARTER_MODEL_NAME)
                                }
                                settingsViewModel.refreshInstalledModels(ModelType.ONNX)
                                SnackbarController.pushEvent(
                                    SnackbarEvents.MessageEvent(
                                        message = extractedMsg, duration = SnackbarDuration.Short
                                    )
                                )
                            }
                        }
                    }, shapes = CornerRole(
                        topStart = true, bottomStart = !hasModels
                    ).toListItemShapes(), leadingContent = {
                        Icon(
                            Icons.Rounded.Add, null, modifier = Modifier.size(24.dp)
                        )
                    }, content = {
                        Text(
                            stringResource(R.string.import_model_text),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    })
                SegmentedListItem(
                    modifier = Modifier.weight(1f), colors = colors, onClick = {
                        HapticPatterns.tap()
                        uriHandler.openUri("https://codeberg.org/dryerlint/dejpeg/src/branch/main/models")
                    }, shapes = CornerRole(
                        topEnd = true, bottomEnd = !hasModels
                    ).toListItemShapes(), leadingContent = {
                        Icon(
                            Icons.Rounded.Download, null, modifier = Modifier.size(24.dp)
                        )
                    }, content = {
                        Text(
                            stringResource(R.string.download),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    })
            }

            allModels.forEachIndexed { index, (modelName, modelType) ->
                key(modelName, modelType) {
                    val isActive =
                        modelName == activeSelection.modelName && processingMode == modelType
                    val last = index == allModels.lastIndex
                    val cantSwitchModel = stringResource(R.string.cant_switch_model)
                    val cantDeleteModel = stringResource(R.string.cant_delete_model)

                    Spacer(modifier = Modifier.height(GroupedListSpacing))
                    val isProcessing = processingViewModel.isProcessingOrQueueActive()
                    val deleteAction: () -> (() -> Unit)? = {
                        if (isProcessing) {
                            scope.launch {
                                SnackbarController.pushEvent(
                                    SnackbarEvents.MessageEvent(
                                        message = cantDeleteModel, duration = SnackbarDuration.Short
                                    )
                                )
                            }
                            null
                        } else {
                            {
                                settingsViewModel.deleteModel(
                                    modelName, modelType
                                ) { deletedName ->
                                    scope.launch {
                                        SnackbarController.pushEvent(
                                            SnackbarEvents.MessageEvent(
                                                message = deletedMsg.format(
                                                    deletedName
                                                ), duration = SnackbarDuration.Short
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
                    val infoAction: () -> (() -> Unit)? = {
                        if (!isProcessing) {
                            modelManager.getModelInfo(modelName)?.let {
                                modelInfoDialog.value = modelName to it
                            }
                        }
                        null
                    }
                    val config = SwipeConfig(
                        right = SwipeSide(
                            action = infoAction,
                            icon = Icons.Rounded.Info,
                            iconTint = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                        left = SwipeSide(
                            action = deleteAction,
                            icon = Icons.Rounded.Delete,
                            iconTint = MaterialTheme.colorScheme.error,
                        ),
                        swap = swapSwipeActions,
                    )
                    CardWrapper(
                        config = config,
                        rightSwipeEnabled = !isProcessing,
                    ) {
                        SegmentedListItem(
                            colors = colors, selected = isActive, onClick = {
                            HapticPatterns.tap()
                            if (isProcessing) {
                                scope.launch {
                                    SnackbarController.pushEvent(
                                        SnackbarEvents.MessageEvent(
                                            message = cantSwitchModel,
                                            duration = SnackbarDuration.Short
                                        )
                                    )
                                }
                            } else {
                                settingsViewModel.setActiveModel(modelName)
                            }
                        }, shapes = CornerRole(
                            bottomStart = last, bottomEnd = last
                        ).toListItemShapes(), content = {
                            Text(
                                modelName,
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }, trailingContent = {
                            Row {
                                modelManager.getModelInfo(modelName)?.let {
                                    IconButton(onClick = {
                                        HapticPatterns.tap()
                                        modelInfoDialog.value = modelName to it
                                    }, modifier = Modifier.size(32.dp)) {
                                        Icon(
                                            Icons.Rounded.Info,
                                            contentDescription = stringResource(R.string.info),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(21.dp)
                                        )
                                    }
                                }
                                IconButton(
                                    onClick = {
                                        HapticPatterns.tap()
                                        if (isProcessing) {
                                            scope.launch {
                                                SnackbarController.pushEvent(
                                                    SnackbarEvents.MessageEvent(
                                                        message = cantDeleteModel,
                                                        duration = SnackbarDuration.Short
                                                    )
                                                )
                                            }
                                        } else {
                                            settingsViewModel.deleteModel(
                                                modelName, modelType
                                            ) {
                                                scope.launch {
                                                    SnackbarController.pushEvent(
                                                        SnackbarEvents.MessageEvent(
                                                            message = deletedMsg.format(
                                                                it
                                                            ), duration = SnackbarDuration.Short
                                                        )
                                                    )
                                                }
                                            }
                                        }
                                    }, modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        Icons.Rounded.Delete,
                                        contentDescription = stringResource(R.string.delete),
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(21.dp)
                                    )
                                }
                            }
                        })
                    }
                }
            }

//            Spacer(modifier = Modifier.height(GroupedListSpacing))

            AnimatedVisibility(
                visible = processingMode == ModelType.ONNX,
                enter = fadeIn() + expandVertically(expandFrom = Alignment.Top),
                exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Top)
            ) {
                val chunkSize by settingsViewModel.chunkSize.collectAsState()
                val overlapSize by settingsViewModel.overlapSize.collectAsState()
                val onnxDeviceThreads by settingsViewModel.onnxDeviceThreads.collectAsState()
                val maxThreads =
                    remember { Runtime.getRuntime().availableProcessors().coerceAtLeast(1) }
                val threadValue = if (onnxDeviceThreads == 0) stringResource(
                    R.string.thread_value_auto, ThreadUtils.resolveThreadCount(onnxDeviceThreads)
                )
                else onnxDeviceThreads.toString()

                Column {
                    Heading(stringResource(R.string.settings_item_onnx_processing))
                    SegmentedListItem(colors = colors, shapes = segmentedListShapes(1, 3)) {
                        PowerSlider(
                            label = stringResource(R.string.chunk_size),
                            value = chunkSize,
                            powers = listOf(512, 1024, 2048),
                            onChange = settingsViewModel::setChunkSize
                        )
                    }
                    Spacer(Modifier.height(GroupedListSpacing))
                    SegmentedListItem(colors = colors, shapes = segmentedListShapes(2, 3)) {
                        PowerSlider(
                            label = stringResource(R.string.overlap_size),
                            value = overlapSize,
                            powers = listOf(16, 32, 64, 128),
                            onChange = settingsViewModel::setOverlapSize
                        )
                    }
                    Spacer(Modifier.height(GroupedListSpacing))
                    SegmentedListItem(colors = colors, shapes = segmentedListShapes(3, 3)) {
                        PowerSlider(
                            label = "${stringResource(R.string.processing_threads_desc)} • $threadValue",
                            value = onnxDeviceThreads,
                            hideValue = true,
                            powers = (0..maxThreads).toList(),
                            onChange = settingsViewModel::setOnnxDeviceThreads
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = processingMode == ModelType.OIDN,
                enter = fadeIn() + expandVertically(expandFrom = Alignment.Top),
                exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Top)
            ) {
                val hdr by settingsViewModel.oidnHdr.collectAsState()
                val sRGB by settingsViewModel.oidnSrgb.collectAsState()
                val quality by settingsViewModel.oidnQuality.collectAsState()
                val threads by settingsViewModel.oidnNumThreads.collectAsState()
                Column {
                    Heading(stringResource(R.string.oidn_settings))
                    Spacer(modifier = Modifier.height(GroupedListSpacing))
                    SegmentedListItem(colors = colors, shapes = segmentedListShapes(1, 2)) {
                        LabeledSwitch(
                            title = stringResource(R.string.oidn_hdr),
                            desc = stringResource(R.string.oidn_hdr_desc),
                            checked = hdr,
                            onCheckedChange = { settingsViewModel.setOidnHdrPref(it) })
                    }
                    Spacer(modifier = Modifier.height(GroupedListSpacing))
                    SegmentedListItem(colors = colors, shapes = segmentedListShapes(2, 2)) {
                        LabeledSwitch(
                            title = stringResource(R.string.oidn_srgb),
                            desc = stringResource(R.string.oidn_srgb_desc),
                            checked = sRGB,
                            onCheckedChange = { settingsViewModel.setOidnSrgbPref(it) })
                    }
                    Spacer(Modifier.height(GroupedListSpacing))
                    Heading(stringResource(R.string.oidn_quality))
                    Spacer(modifier = Modifier.height(GroupedListSpacing))
                    val qualityOptions = listOf(
                        0 to stringResource(R.string.oidn_quality_default),
                        4 to stringResource(R.string.oidn_quality_fast),
                        5 to stringResource(R.string.oidn_quality_balanced),
                        6 to stringResource(R.string.oidn_quality_high)
                    )
                    SegmentedOptionGrid(
                        options = qualityOptions,
                        selected = quality,
                        colors = colors,
                        onSelect = { value -> settingsViewModel.setOidnQualityPref(value) })
                    Spacer(Modifier.height(GroupedListSpacing))
                    val resolvedOidnThreads = ThreadUtils.resolveThreadCount(threads)
                    val maxThreads = remember {
                        Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
                    }
                    val threadValue = if (threads == 0) {
                        stringResource(
                            R.string.thread_value_auto, resolvedOidnThreads
                        )
                    } else {
                        threads.toString()
                    }
                    Heading(stringResource(R.string.oidn_num_threads) + " • $threadValue")
                    Spacer(modifier = Modifier.height(GroupedListSpacing))
                    SegmentedListItem(colors = colors, shapes = segmentedListShapes(1, 1)) {
                        PowerSlider(
                            hideValue = true,
                            value = threads,
                            powers = (0..maxThreads).toList(),
                            onChange = { settingsViewModel.setOidnNumThreadsPref(it) })
                    }
                    Spacer(modifier = Modifier.height(GroupedListSpacing))
                }
            }

            Heading(stringResource(R.string.settings))
            Spacer(modifier = Modifier.height(GroupedListSpacing))
            SegmentedListItem(colors = colors, shapes = segmentedListShapes(1, 6)) {
                LabeledSwitch(
                    title = stringResource(R.string.vibration_on_touch),
                    checked = hapticsEnabled,
                    onCheckedChange = { new -> hapticsEnabled = new })
            }
            Spacer(modifier = Modifier.height(GroupedListSpacing))
            SegmentedListItem(colors = colors, shapes = segmentedListShapes(2, 6)) {
                LabeledSwitch(
                    title = stringResource(R.string.show_save_dialog),
                    checked = showSaveDialog,
                    onCheckedChange = { new -> showSaveDialog = new })
            }
            Spacer(modifier = Modifier.height(GroupedListSpacing))
            SegmentedListItem(colors = colors, shapes = segmentedListShapes(3, 6)) {
                LabeledSwitch(
                    title = stringResource(R.string.swap_swipe_actions),
                    checked = swapSwipeActions,
                    onCheckedChange = { new -> swapSwipeActions = new })
            }
            Spacer(modifier = Modifier.height(GroupedListSpacing))
            SegmentedListItem(colors = colors, shapes = segmentedListShapes(4, 6)) {
                LabeledSwitch(
                    title = stringResource(R.string.glass_slider),
                    checked = glassSlider,
                    onCheckedChange = { new -> glassSlider = new })
            }
            Spacer(modifier = Modifier.height(GroupedListSpacing))
            val clearedDefaultSourceMsg = stringResource(R.string.cleared_default_source)
            val defaultSourceLabel = defaultImageSource ?: stringResource(R.string.none)
            SegmentedListItem(colors = colors, shapes = segmentedListShapes(5, 6), onClick = {
                HapticPatterns.tap()
                scope.launch {
                    appPreferences.saveDefaultImageSource(null)
                    SnackbarController.pushEvent(
                        SnackbarEvents.MessageEvent(
                            message = clearedDefaultSourceMsg, duration = SnackbarDuration.Short
                        )
                    )
                }
            }, content = {
                Text(
                    stringResource(R.string.default_image_source),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold
                )
            }, supportingContent = {
                Text(
                    defaultSourceLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }, trailingContent = {
                TextButton(
                    onClick = {
                        HapticPatterns.tap()
                        scope.launch {
                            appPreferences.saveDefaultImageSource(null)
                            SnackbarController.pushEvent(
                                SnackbarEvents.MessageEvent(
                                    message = clearedDefaultSourceMsg,
                                    duration = SnackbarDuration.Short
                                )
                            )
                        }
                    }) { Text(stringResource(R.string.clear_default_source)) }
            })
            Spacer(modifier = Modifier.height(GroupedListSpacing))
            val themeOptions = AppTheme.entries.map { theme ->
                theme to when (theme) {
                    AppTheme.Dynamic -> stringResource(R.string.theme_dynamic)
                    AppTheme.Light -> stringResource(R.string.theme_light)
                    AppTheme.Dark -> stringResource(R.string.theme_dark)
                    AppTheme.OLED -> stringResource(R.string.theme_oled)
                }
            }
            SegmentedOptionGrid(
                dontRound = true,
                options = themeOptions,
                selected = currentTheme,
                colors = colors,
                onSelect = { theme ->
                    appPreferences.saveAppTheme(theme)
                    App.state.appTheme.value = theme
                })
        }

        val interaction = remember { MutableInteractionSource() }
        val press by rememberMaterialPressState(interaction)
        FloatingActionButton(
            onClick = {
                HapticPatterns.tap()
                modelPickerLauncher.launch(arrayOf("*/*"))
            },
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            shape = RoundedCornerShape(lerp(16f, 28f, press).dp),
            interactionSource = interaction,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(
                    end = 12.dp,
                    bottom = WindowInsets.navigationBars.asPaddingValues()
                        .calculateBottomPadding() + ScreenHorizontalPadding
                )
                .height(56.dp)
                .width(110.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Rounded.Add,
                    contentDescription = stringResource(R.string.import_model_text)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(R.string.import_model_text),
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }

    if (showImportProgress.value) {
        ModalBottomSheet(onDismissRequest = {
            showImportProgress.value = false
            importError.value = null
        }) {
            Column(Modifier.padding(ScreenHorizontalPadding)) {
                Text(
                    stringResource(R.string.importing_model),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(8.dp))
                if (importError.value != null) {
                    Text(
                        importError.value!!,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    val animatedProgress by animateFloatAsState(
                        targetValue = importProgress.coerceIn(0, 100) / 100f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = Spring.StiffnessLow
                        ),
                        label = "import_progress"
                    )
                    LinearProgressIndicator(
                        progress = { animatedProgress },
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                if (importError.value == null) {
                    Text(
                        "${importProgress.coerceIn(0, 100)}%",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(ScreenHorizontalPadding))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = {
                        HapticPatterns.tap()
                        showImportProgress.value = false
                        importError.value = null
                    }) {
                        Text(
                            if (importError.value != null) stringResource(R.string.ok)
                            else stringResource(R.string.cancel)
                        )
                    }
                }
            }
        }
    }
    modelInfoDialog.value?.let { (modelName, infoText) ->
        ModalBottomSheet(onDismissRequest = { modelInfoDialog.value = null }) {
            Column(Modifier.padding(ScreenHorizontalPadding)) {
                Text(
                    modelName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    infoText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(64.dp))
            }
        }
    }
}

@Composable
fun <T> SegmentedOptionGrid(
    options: List<Pair<T, String>>,
    selected: T,
    colors: ListItemColors,
    columns: Int = 2,
    onSelect: (T) -> Unit,
    dontRound: Boolean = false,
) {
    val rows = options.chunked(columns)
    rows.forEachIndexed { rowIndex, rowOptions ->
        val isTopRow = rowIndex == 0
        val isBottomRow = rowIndex == rows.lastIndex
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(GroupedListSpacing)
        ) {
            rowOptions.forEachIndexed { colIndex, (value, label) ->
                val isFirstCol = colIndex == 0
                val isLastCol = colIndex == rowOptions.lastIndex
                SegmentedListItem(
                    colors = colors,
                    modifier = Modifier.weight(1f),
                    shapes = CornerRole(
                        topStart = if (dontRound) false else isTopRow && isFirstCol,
                        topEnd = if (dontRound) false else isTopRow && isLastCol,
                        bottomStart = isBottomRow && isFirstCol,
                        bottomEnd = isBottomRow && isLastCol
                    ).toListItemShapes(),
                    selected = selected == value,
                    onClick = { HapticPatterns.tap(); onSelect(value) }) {
                    Text(label, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        Spacer(modifier = Modifier.height(GroupedListSpacing))
    }
}
