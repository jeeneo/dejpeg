/*
 * SPDX-FileCopyrightText: 2025 - 2026 dryerlint <https://codeberg.org/dryerlint>
 * SPDX-License-Identifier: GNU Affero General Public License v3.0 or later
 */

@file:Suppress(
    "KotlinConstantConditions", "SimplifyBooleanWithConstants", "SpellCheckingInspection"
)

package com.je.dejpeg.ui.components

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
import androidx.compose.material.icons.rounded.BlurOn
import androidx.compose.material.icons.rounded.Deblur
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Settings
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
import androidx.compose.runtime.LaunchedEffect
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
import com.je.dejpeg.data.HapticPatterns
import com.je.dejpeg.data.SettingsSection
import com.je.dejpeg.data.ThreadUtils
import com.je.dejpeg.ui.theme.AppTheme
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
    settingsViewModel: SettingsViewModel,
    processingViewModel: ProcessingViewModel,
    modifier: Modifier = Modifier,
) {
    val modelManager = remember { ModelManager.create(App.ctx) }
    val appPreferences = remember { AppPreferences() }
    val scope = rememberCoroutineScope()
    val showImportProgress = remember { mutableStateOf(false) }
    var expandedSection by remember { mutableStateOf<SettingsSection?>(null) }
    fun toggle(section: SettingsSection) {
        expandedSection = if (expandedSection == section) null else section
    }

    var importProgress by remember { mutableIntStateOf(0) }
    val importedModelMessage = stringResource(R.string.imported_model)
    val deletedModelMessage = stringResource(R.string.deleted_model)
    val blockedSwitchingMessage = stringResource(R.string.model_switch_blocked_processing)
    val chunkSize by settingsViewModel.chunkSize.collectAsState()
    val overlapSize by settingsViewModel.overlapSize.collectAsState()
    val onnxDeviceThreads by settingsViewModel.onnxDeviceThreads.collectAsState()
    var showSaveDialog by remember { mutableStateOf(appPreferences.loadShowSaveDialog()) }
    var defaultImageSource by remember { mutableStateOf(appPreferences.loadDefaultImageSource()) }
    var hapticsEnabled by remember { mutableStateOf(appPreferences.loadHapticFeedbackEnabled()) }
    var swapSwipeActions by remember { mutableStateOf(appPreferences.loadSwapSwipeActions()) }
    val modelInfoDialog = remember { mutableStateOf<Pair<String, String>?>(null) }
    val activeSelection by settingsViewModel.activeSelection.collectAsState()
    val processingMode = activeSelection.type
    val oidnHDR by settingsViewModel.oidnHdr.collectAsState()
    LaunchedEffect(processingMode) {
        if (expandedSection == SettingsSection.OnnxSettings || expandedSection == SettingsSection.OidnSettings) {
            expandedSection = null
        }
    }

    val oidnSRGB by settingsViewModel.oidnSrgb.collectAsState()
    val oidnQuality by settingsViewModel.oidnQuality.collectAsState()
    val oidnNumThreads by settingsViewModel.oidnNumThreads.collectAsState()
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
                            SnackySnackbarEvents.MessageEvent(
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
                    top = 8.dp,
                    bottom = WindowInsets.navigationBars.asPaddingValues()
                        .calculateBottomPadding() + 90.dp,
                    start = 12.dp,
                    end = 12.dp
                )
        ) {
            Heading(stringResource(R.string.settings_title_models))
            val hasModels = allModels.isNotEmpty()
            val hasCard = processingMode == ModelType.OIDN || processingMode == ModelType.ONNX
            val extractedMsg = stringResource(R.string.extracted_starter_models)
            val failedMsg = stringResource(R.string.failed_to_extract_starter_models)
            val colors = ListItemDefaults.segmentedColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                selectedContainerColor = MaterialTheme.colorScheme.outlineVariant
            )
            val cardColors =
                ListItemDefaults.segmentedColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)

            val currentTheme = App.state.appTheme.value
            var glassSlider by remember { mutableStateOf(appPreferences.loadGlassSlider()) }

            Spacer(modifier = Modifier.height(GroupedListSpacing))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(GroupedListSpacing),
            ) {
                SegmentedListItem(
                    modifier = Modifier.weight(1f), colors = colors, onClick = {
                        modelPickerLauncher.launch(arrayOf("*/*"))
                    }, onLongClick = {
                        scope.launch {
                            if (processingViewModel.isProcessingOrQueueActive()) {
                                return@launch
                            }
                            val extracted = withContext(Dispatchers.IO) {
                                modelManager.extractStarterModel()
                            }
                            if (extracted.isNotEmpty()) {
                                settingsViewModel.setActiveModel(ModelManager.STARTER_MODEL_NAME)
                                settingsViewModel.refreshInstalledModels(ModelType.ONNX)
                                SnackbarController.pushEvent(
                                    SnackySnackbarEvents.MessageEvent(
                                        message = extractedMsg, duration = SnackbarDuration.Short
                                    )
                                )
                            } else {
                                SnackbarController.pushEvent(
                                    SnackySnackbarEvents.MessageEvent(
                                        message = failedMsg, duration = SnackbarDuration.Short
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
                    val last = index == allModels.lastIndex && !hasCard
                    Spacer(modifier = Modifier.height(GroupedListSpacing))
                    SegmentedListItem(
                        colors = colors, selected = isActive, onClick = {
                            if (processingViewModel.isProcessingOrQueueActive()) {
                                scope.launch {
                                    SnackbarController.pushEvent(
                                        SnackySnackbarEvents.MessageEvent(
                                            message = blockedSwitchingMessage,
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
                                        settingsViewModel.deleteModel(
                                            modelName, modelType
                                        ) {
                                            scope.launch {
                                                SnackbarController.pushEvent(
                                                    SnackySnackbarEvents.MessageEvent(
                                                        message = deletedModelMessage.format(
                                                            it
                                                        ), duration = SnackbarDuration.Short
                                                    )
                                                )
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
            Spacer(modifier = Modifier.height(GroupedListSpacing))
            AnimatedVisibility(
                visible = hasModels,
                enter = fadeIn() + expandVertically(expandFrom = Alignment.Top),
                exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Top)
            ) {
                val isExpanded =
                    expandedSection == SettingsSection.OidnSettings || expandedSection == SettingsSection.OnnxSettings
                val resolvedThreads = ThreadUtils.resolveThreadCount(onnxDeviceThreads)
                val threadValue = if (onnxDeviceThreads == 0) {
                    stringResource(R.string.thread_value_auto, resolvedThreads)
                } else {
                    onnxDeviceThreads.toString()
                }
                val threadLabel =
                    "${stringResource(R.string.processing_threads_desc)} • $threadValue"
                AnimatedVisibility(
                    visible = processingMode == ModelType.ONNX,
                    enter = fadeIn() + expandVertically(expandFrom = Alignment.Top),
                    exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Top)
                ) {
                    PreferenceItem(
                        colors = colors,
                        index = 1,
                        count = 2,
                        icon = Icons.Rounded.BlurOn,
                        iconTint = MaterialTheme.colorScheme.onSecondaryContainer,
                        title = stringResource(R.string.settings_item_onnx_processing),
                        subtitle = if (isExpanded) "" else stringResource(
                            R.string.chunk_size_px, chunkSize
                        ) + " • " + stringResource(
                            R.string.overlap_size_px, overlapSize
                        ) + " × $resolvedThreads",
                        expanded = isExpanded,
                        expandedContent = {
                            val maxThreads = remember {
                                Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
                            }
                            SegmentedListItem(
                                colors = cardColors, shapes = segmentedShapes(1, 3), content = {
                                    PowerSlider(
                                        label = stringResource(R.string.chunk_size),
                                        value = chunkSize,
                                        powers = listOf(512, 1024, 2048),
                                        onChange = { settingsViewModel.setChunkSize(it) })
                                })
                            SegmentedListItem(
                                colors = cardColors, shapes = segmentedShapes(2, 3), content = {
                                    PowerSlider(
                                        label = stringResource(R.string.overlap_size),
                                        value = overlapSize,
                                        powers = listOf(16, 32, 64, 128),
                                        onChange = { settingsViewModel.setOverlapSize(it) })

                                })
                            SegmentedListItem(
                                colors = cardColors, shapes = segmentedShapes(3, 3), content = {
                                    PowerSlider(
                                        label = threadLabel,
                                        value = onnxDeviceThreads,
                                        hideValue = true,
                                        powers = (0..maxThreads).toList(),
                                        onChange = { settingsViewModel.setOnnxDeviceThreads(it) })
                                })

                        },
                        onClick = { toggle(SettingsSection.OnnxSettings) })
                }
                AnimatedVisibility(
                    visible = processingMode == ModelType.OIDN,
                    enter = fadeIn() + expandVertically(expandFrom = Alignment.Top),
                    exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Top)
                ) {
                    PreferenceItem(
                        colors = colors,
                        index = 1,
                        count = 2,
                        icon = Icons.Rounded.Deblur,
                        iconTint = MaterialTheme.colorScheme.onSecondaryContainer,
                        title = stringResource(R.string.oidn_settings),
                        subtitle = if (isExpanded) "" else stringResource(R.string.oidn_settings_desc),
                        expanded = isExpanded,
                        expandedContent = {
                            SegmentedListItem(
                                colors = cardColors, shapes = segmentedShapes(1, 2), onClick = {
                                    settingsViewModel.setOidnHdrPref(
                                        !oidnHDR
                                    )
                                }) {
                                LabeledSwitch(
                                    title = stringResource(R.string.oidn_hdr),
                                    desc = stringResource(R.string.oidn_hdr_desc),
                                    checked = oidnHDR,
                                    onCheckedChange = { settingsViewModel.setOidnHdrPref(it) })
                            }
                            SegmentedListItem(
                                colors = cardColors,
                                shapes = segmentedShapes(2, 2),
                                onClick = {
                                    settingsViewModel.setOidnSrgbPref(
                                        !oidnSRGB
                                    )
                                },
                            ) {
                                LabeledSwitch(
                                    title = stringResource(R.string.oidn_srgb),
                                    desc = stringResource(R.string.oidn_srgb_desc),
                                    checked = oidnSRGB,
                                    onCheckedChange = { settingsViewModel.setOidnSrgbPref(it) })
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                stringResource(R.string.oidn_quality),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            val qualityOptions = listOf(
                                0 to stringResource(R.string.oidn_quality_default),
                                4 to stringResource(R.string.oidn_quality_fast),
                                5 to stringResource(R.string.oidn_quality_balanced),
                                6 to stringResource(R.string.oidn_quality_high)
                            )
                            SegmentedOptionGrid(
                                options = qualityOptions,
                                selected = oidnQuality,
                                colors = cardColors,
                                onSelect = { value -> settingsViewModel.setOidnQualityPref(value) })
                            Spacer(modifier = Modifier.height(8.dp))
                            val resolvedOidnThreads = ThreadUtils.resolveThreadCount(oidnNumThreads)
                            val maxThreads = remember {
                                Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
                            }
                            val threadValue = if (oidnNumThreads == 0) {
                                stringResource(
                                    R.string.thread_value_auto, resolvedOidnThreads
                                )
                            } else {
                                oidnNumThreads.toString()
                            }
                            Text(
                                text = "${stringResource(R.string.oidn_num_threads)} • $threadValue",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            SegmentedListItem(
                                colors = cardColors,
                                shapes = segmentedShapes(1, 1),
                            ) {
                                PowerSlider(
                                    hideValue = true,
                                    value = oidnNumThreads,
                                    powers = (0..maxThreads).toList(),
                                    onChange = { settingsViewModel.setOidnNumThreadsPref(it) })
                            }
                        },
                        onClick = {
                            toggle(SettingsSection.OidnSettings)
                        })
                }
            }
            Spacer(Modifier.height(6.dp))
            Heading("Settings")
            PreferenceItem(
                colors = colors,
                index = 1,
                count = 1,
                icon = Icons.Rounded.Settings,
                iconTint = MaterialTheme.colorScheme.onSurfaceVariant,
                title = stringResource(R.string.settings_item_title_options),
                expanded = expandedSection == SettingsSection.MainSettings,
                expandedContent = {
                    SegmentedListItem(
                        colors = cardColors, shapes = segmentedShapes(1, 6), onClick = {
                            scope.launch {
                                hapticsEnabled = !hapticsEnabled
                                if (hapticsEnabled) {
                                    HapticPatterns.tap(force = true)
                                }
                                appPreferences.saveHapticToggle(hapticsEnabled)
                                HapticPatterns.appHapticsEnabled = hapticsEnabled
                            }
                        }) {
                        LabeledSwitch(
                            title = stringResource(R.string.vibration_on_touch),
                            checked = hapticsEnabled,
                            onCheckedChange = { new ->
                                scope.launch {
                                    hapticsEnabled = new
                                    if (hapticsEnabled) {
                                        HapticPatterns.tap(force = true)
                                    }
                                    appPreferences.saveHapticToggle(new)
                                    HapticPatterns.appHapticsEnabled = new
                                }
                            })
                    }
                    SegmentedListItem(
                        colors = cardColors, shapes = segmentedShapes(2, 6), onClick = {
                            scope.launch {
                                showSaveDialog = !showSaveDialog
                                appPreferences.saveShowSaveDialog(showSaveDialog)
                            }
                        }) {
                        LabeledSwitch(
                            title = stringResource(R.string.show_save_dialog),
                            checked = showSaveDialog,
                            onCheckedChange = { new ->
                                scope.launch {
                                    showSaveDialog = new
                                    appPreferences.saveShowSaveDialog(new)
                                }
                            })
                    }
                    SegmentedListItem(
                        colors = cardColors, shapes = segmentedShapes(3, 6), onClick = {
                            scope.launch {
                                swapSwipeActions = !swapSwipeActions
                                appPreferences.saveSwapSwipeActions(swapSwipeActions)
                            }
                        }) {
                        LabeledSwitch(
                            title = stringResource(R.string.swap_swipe_actions),
                            checked = swapSwipeActions,
                            onCheckedChange = { new ->
                                scope.launch {
                                    swapSwipeActions = new
                                    appPreferences.saveSwapSwipeActions(new)
                                }
                            })
                    }
                    SegmentedListItem(
                        colors = cardColors, shapes = segmentedShapes(4, 6), onClick = {
                            scope.launch {
                                glassSlider = !glassSlider
                                appPreferences.saveGlassSlider(glassSlider)
                            }
                        }) {
                        LabeledSwitch(
                            title = stringResource(R.string.glass_slider),
                            checked = glassSlider,
                            onCheckedChange = { new ->
                                glassSlider = new
                                appPreferences.saveGlassSlider(new)
                            })
                    }
                    val clearedDefaultSourceMsg = stringResource(R.string.cleared_default_source)
                    SegmentedListItem(
                        colors = cardColors,
                        shapes = segmentedShapes(5, 6),
                        onClick = {
                            scope.launch {
                                defaultImageSource = null
                                appPreferences.saveDefaultImageSource(null)
                                SnackbarController.pushEvent(
                                    SnackySnackbarEvents.MessageEvent(
                                        message = clearedDefaultSourceMsg,
                                        duration = SnackbarDuration.Short
                                    )
                                )
                            }
                        },
                        content = {
                            Text(
                                stringResource(R.string.default_image_source),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                        },
                        supportingContent = {
                            Text(
                                defaultImageSource ?: stringResource(R.string.none),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        trailingContent = {
                            TextButton(
                                onClick = {
                                    scope.launch {
                                        defaultImageSource = null
                                        appPreferences.saveDefaultImageSource(null)
                                        SnackbarController.pushEvent(
                                            SnackySnackbarEvents.MessageEvent(
                                                message = clearedDefaultSourceMsg,
                                                duration = SnackbarDuration.Short
                                            )
                                        )
                                    }
                                }) { Text(stringResource(R.string.clear_default_source)) }
                        })
                    val themeOptions = AppTheme.entries.map { theme ->
                        theme to when (theme) {
                            AppTheme.Dynamic -> stringResource(R.string.theme_dynamic)
                            AppTheme.Light -> stringResource(R.string.theme_light)
                            AppTheme.Dark -> stringResource(R.string.theme_dark)
                            AppTheme.OLED -> stringResource(R.string.theme_oled)
                        }
                    }
                    SegmentedOptionGrid(
                        options = themeOptions,
                        selected = currentTheme,
                        colors = cardColors,
                        dontRound = true,
                        onSelect = { theme ->
                            appPreferences.saveAppTheme(theme)
                            App.state.appTheme.value = theme
                        })
                },
                onClick = {
                    toggle(SettingsSection.MainSettings)
                },
            )
        }

        val interaction = remember { MutableInteractionSource() }
        val press by rememberMaterialPressState(interaction)
        FloatingActionButton(
            onClick = {
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
                    onClick = { onSelect(value) }) {
                    Text(label, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
