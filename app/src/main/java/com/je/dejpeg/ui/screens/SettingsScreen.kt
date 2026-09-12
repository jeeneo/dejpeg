/*
 * SPDX-FileCopyrightText: 2025 - 2026 dryerlint <https://codeberg.org/dryerlint>
 * SPDX-License-Identifier: GNU Affero General Public License v3.0 or later
 */

@file:Suppress(
    "KotlinConstantConditions", "SimplifyBooleanWithConstants", "SpellCheckingInspection"
)

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
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.BlurOn
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Deblur
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.material3.rememberSliderState
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.je.dejpeg.App
import com.je.dejpeg.AppPreferences
import com.je.dejpeg.HapticFeedbacks
import com.je.dejpeg.R
import com.je.dejpeg.ThreadUtils
import com.je.dejpeg.ui.components.CardPosition
import com.je.dejpeg.ui.components.CornerRole
import com.je.dejpeg.ui.components.GroupedListSpacing
import com.je.dejpeg.ui.components.GroupedRow
import com.je.dejpeg.ui.components.SnackbarController
import com.je.dejpeg.ui.components.SnackbarDuration
import com.je.dejpeg.ui.components.SnackySnackbarEvents
import com.je.dejpeg.ui.components.positionFor
import com.je.dejpeg.ui.components.rememberMaterialPressState
import com.je.dejpeg.ui.theme.AppTheme
import com.je.dejpeg.ui.viewmodel.ProcessingViewModel
import com.je.dejpeg.ui.viewmodel.SettingsViewModel
import com.je.dejpeg.utils.ModelManager
import com.je.dejpeg.utils.ModelType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun SettingsSheet(
    viewModel: SettingsViewModel,
    processingViewModel: ProcessingViewModel,
    onDismiss: () -> Unit,
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
    val chunkSize by viewModel.chunkSize.collectAsState()
    val overlapSize by viewModel.overlapSize.collectAsState()
    val onnxDeviceThreads by viewModel.onnxDeviceThreads.collectAsState()
    val showSaveDialog by appPreferences.showSaveDialog.collectAsState(initial = true)
    val defaultImageSource by appPreferences.defaultImageSource.collectAsState(initial = null)
    val hapticFeedbackEnabled by appPreferences.hapticFeedbackEnabled.collectAsState(initial = true)
    val swapSwipeActions by appPreferences.swapSwipeActions.collectAsState(initial = false)
    val modelInfoDialog = remember { mutableStateOf<Pair<String, String>?>(null) }
    val activeSelection by viewModel.activeSelection.collectAsState()
    val processingMode = activeSelection.type
    val oidnHDR by viewModel.oidnHdr.collectAsState()
    LaunchedEffect(processingMode) {
        if (expandedSection == SettingsSection.OnnxSettings || expandedSection == SettingsSection.OidnSettings) {
            expandedSection = null
        }
    }

    val oidnSRGB by viewModel.oidnSrgb.collectAsState()
    val oidnQuality by viewModel.oidnQuality.collectAsState()
    val oidnNumThreads by viewModel.oidnNumThreads.collectAsState()
    val uriHandler = LocalUriHandler.current
    val importError = remember { mutableStateOf<String?>(null) }
    val importedModels by viewModel.importedModels.collectAsState()
    val allModels = importedModels.flatMap { (type, names) -> names.map { name -> name to type } }

    val modelPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { it ->
            showImportProgress.value = true
            importProgress = 0
            viewModel.importModel(it, onProgress = { importProgress = it }, onSuccess = { name, _ ->
                showImportProgress.value = false
                scope.launch {
                    SnackbarController.pushEvent(
                        SnackySnackbarEvents.MessageEvent(
                            message = importedModelMessage.format(name),
                            duration = SnackbarDuration.Short
                        )
                    )
                }
            }, onError = { error ->
                importProgress = 0
                importError.value = error
            })
        }
    }

    val sheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
    )
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.background
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(top = 8.dp, bottom = 88.dp, start = 12.dp, end = 12.dp)
            ) {
                PreferenceGroupHeading(stringResource(R.string.settings_title_models))

                val hasModels = allModels.isNotEmpty()
                val hasCard = processingMode == ModelType.OIDN || processingMode == ModelType.ONNX
                val extractedMsg = stringResource(R.string.extracted_starter_models)
                val failedMsg = stringResource(R.string.failed_to_extract_starter_models)

                Spacer(modifier = Modifier.height(GroupedListSpacing))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(GroupedListSpacing),
                ) {
                    GroupedRow(
                        modifier = Modifier.weight(1f),
                        position = CardPosition.Solo,
                        cornerRole = CornerRole(topStart = true, bottomStart = !hasModels),
                        hideExtras = true,
                        horizontalArrangement = Arrangement.Center,
                        onClick = {
                            modelPickerLauncher.launch("*/*")
                        },
                        onLongClick = {
                            HapticFeedbacks.heavy()
                            scope.launch {
                                val extracted = withContext(Dispatchers.IO) {
                                    modelManager.extractStarterModel(setAsActive = true)
                                }
                                if (extracted) {
                                    viewModel.refreshInstalledModels(ModelType.ONNX)
                                    SnackbarController.pushEvent(
                                        SnackySnackbarEvents.MessageEvent(
                                            message = extractedMsg,
                                            duration = SnackbarDuration.Short
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
                        },
                        tooltip = stringResource(R.string.settings_tooltip_extract),
                        verticalPadding = 12.dp,
                    ) {
                        Icon(Icons.Rounded.Add, null, modifier = Modifier.size(21.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(
                            stringResource(R.string.import_model_text),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    GroupedRow(
                        modifier = Modifier.weight(1f),
                        position = CardPosition.Solo,
                        cornerRole = CornerRole(topEnd = true, bottomEnd = !hasModels),
                        hideExtras = true,
                        horizontalArrangement = Arrangement.Center,
                        onClick = {
                            uriHandler.openUri("https://codeberg.org/dryerlint/dejpeg/src/branch/main/models")
                        },
                        tooltip = stringResource(R.string.settings_tooltip_download),
                        verticalPadding = 12.dp,
                    ) {
                        Icon(Icons.Rounded.Download, null, modifier = Modifier.size(21.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(
                            stringResource(R.string.download),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                allModels.forEachIndexed { index, (modelName, modelType) ->
                    val isActive =
                        modelName == activeSelection.modelName && processingMode == modelType
                    key(modelName, modelType) {
                        val last = index == allModels.lastIndex && !hasCard
                        Spacer(modifier = Modifier.height(GroupedListSpacing))
                        GroupedRow(
                            position = positionFor(
                                (index - 1), (allModels.size + 1)
                            ),
                            cornerRole = CornerRole(bottomStart = last, bottomEnd = last),
                            onClick = {
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
                                    viewModel.setActiveModel(modelName)
                                }
                            },
                            selected = isActive,
                            hideExtras = true,
                            elevation = 24.dp,
                            verticalPadding = 8.dp,
                        ) {
                            Text(
                                modelName,
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            modelManager.getModelInfo(modelName)?.let {
                                IconButton(onClick = {
                                    HapticFeedbacks.light()
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
                                    HapticFeedbacks.light()
                                    viewModel.deleteModel(
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
                    }
                }

                val resolvedThreads = ThreadUtils.resolveThreadCount(onnxDeviceThreads)
                val threadValue = if (onnxDeviceThreads == 0) {
                    stringResource(R.string.thread_value_auto, resolvedThreads)
                } else {
                    onnxDeviceThreads.toString()
                }
                val threadLabel =
                    "${stringResource(R.string.processing_threads_desc)} • $threadValue"
                Spacer(modifier = Modifier.height(GroupedListSpacing))
                AnimatedVisibility(
                    visible = hasModels,
                    enter = fadeIn() + expandVertically(expandFrom = Alignment.Top),
                    exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Top)
                ) {
                    val isExpanded =
                        expandedSection == SettingsSection.OidnSettings || expandedSection == SettingsSection.OnnxSettings
                    val count = if (isExpanded) 3 else 2
                    AnimatedVisibility(
                        visible = processingMode == ModelType.ONNX,
                        enter = fadeIn() + expandVertically(expandFrom = Alignment.Top),
                        exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Top)
                    ) {
                        PreferenceItem(
                            position = positionFor(2, count),
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
                                PowerSlider(
                                    label = stringResource(R.string.chunk_size),
                                    value = chunkSize,
                                    powers = listOf(512, 1024, 2048),
                                    onChange = { viewModel.setChunkSize(it) },
                                    hapticAction = { HapticFeedbacks.light() })
                                Spacer(modifier = Modifier.height(8.dp))
                                PowerSlider(
                                    label = stringResource(R.string.overlap_size),
                                    value = overlapSize,
                                    powers = listOf(16, 32, 64, 128),
                                    onChange = { viewModel.setOverlapSize(it) },
                                    hapticAction = { HapticFeedbacks.light() })
                                Spacer(modifier = Modifier.height(8.dp))
                                PowerSlider(
                                    label = threadLabel,
                                    value = onnxDeviceThreads,
                                    hideValue = true,
                                    powers = (0..maxThreads).toList(),
                                    onChange = { viewModel.setOnnxDeviceThreads(it) },
                                    hapticAction = { HapticFeedbacks.light() })
                            },
                            onClick = { toggle(SettingsSection.OnnxSettings) })
                    }
                    AnimatedVisibility(
                        visible = processingMode == ModelType.OIDN,
                        enter = fadeIn() + expandVertically(expandFrom = Alignment.Top),
                        exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Top)
                    ) {
                        PreferenceItem(
                            position = positionFor(2, count),
                            icon = Icons.Rounded.Deblur,
                            iconTint = MaterialTheme.colorScheme.onSecondaryContainer,
                            title = stringResource(R.string.oidn_settings),
                            subtitle = if (isExpanded) "" else stringResource(R.string.oidn_settings_desc),
                            expanded = isExpanded,
                            expandedContent = {
                                GroupedRow(
                                    position = CardPosition.Leading,
                                    onClick = { viewModel.setOidnHdrPref(!oidnHDR) },
                                    elevation = 24.dp
                                ) {
                                    LabeledSwitch(
                                        title = stringResource(R.string.oidn_hdr),
                                        desc = stringResource(R.string.oidn_hdr_desc),
                                        checked = oidnHDR,
                                        onCheckedChange = { viewModel.setOidnHdrPref(it) })
                                }
                                GroupedRow(
                                    position = CardPosition.Trailing,
                                    onClick = { viewModel.setOidnSrgbPref(!oidnSRGB) },
                                    elevation = 24.dp
                                ) {
                                    LabeledSwitch(
                                        title = stringResource(R.string.oidn_srgb),
                                        desc = stringResource(R.string.oidn_srgb_desc),
                                        checked = oidnSRGB,
                                        onCheckedChange = { viewModel.setOidnSrgbPref(it) })
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
                                val rows = qualityOptions.chunked(2)
                                rows.forEachIndexed { rowIndex, rowOptions ->
                                    val isTopRow = rowIndex == 0
                                    val isBottomRow = rowIndex == rows.lastIndex
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(
                                            GroupedListSpacing
                                        )
                                    ) {
                                        rowOptions.forEachIndexed { colIndex, (value, label) ->
                                            val isFirstCol = colIndex == 0
                                            val isLastCol = colIndex == rowOptions.lastIndex
                                            GroupedRow(
                                                modifier = Modifier.weight(1f),
                                                elevation = 24.dp,
                                                hideExtras = true,
                                                cornerRole = CornerRole(
                                                    topStart = isTopRow && isFirstCol,
                                                    topEnd = isTopRow && isLastCol,
                                                    bottomStart = isBottomRow && isFirstCol,
                                                    bottomEnd = isBottomRow && isLastCol
                                                ),
                                                selected = oidnQuality == value,
                                                horizontalArrangement = Arrangement.Center,
                                                onClick = {
                                                    HapticFeedbacks.light()
                                                    viewModel.setOidnQualityPref(value)
                                                }) {
                                                Text(label)
                                            }
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                val resolvedOidnThreads =
                                    ThreadUtils.resolveThreadCount(oidnNumThreads)
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
                                val threadLabel =
                                    "${stringResource(R.string.oidn_num_threads)} • $threadValue"
                                PowerSlider(
                                    label = threadLabel,
                                    hideValue = true,
                                    value = oidnNumThreads,
                                    powers = (0..maxThreads).toList(),
                                    onChange = { viewModel.setOidnNumThreadsPref(it) },
                                    hapticAction = { HapticFeedbacks.light() })
                            },
                            onClick = {
                                toggle(SettingsSection.OidnSettings)
                            })
                    }
                }
                val currentTheme = App.state.appTheme.value
                var themeMenuExpanded by remember { mutableStateOf(false) }
                val glassSlider by appPreferences.glassSlider.collectAsState(initial = true)
                val isExpanded = expandedSection == SettingsSection.MainSettings
                val count = if (isExpanded) 2 else 1
                Spacer(Modifier.height(6.dp))
                PreferenceGroupHeading("Settings")
                PreferenceItem(
                    position = positionFor(1, count),
                    icon = Icons.Rounded.Settings,
                    iconTint = MaterialTheme.colorScheme.onSurfaceVariant,
                    title = stringResource(R.string.settings_item_title_options),
                    expanded = isExpanded,
                    expandedContent = {
                        GroupedRow(
                            position = positionFor(1, 6), elevation = 24.dp, onClick = {
                                scope.launch {
                                    appPreferences.setHapticFeedbackEnabled(!hapticFeedbackEnabled)
                                }
                            }) {
                            LabeledSwitch(
                                title = stringResource(R.string.vibration_on_touch),
                                checked = hapticFeedbackEnabled,
                                onCheckedChange = { new ->
                                    scope.launch {
                                        appPreferences.setHapticFeedbackEnabled(new)
                                    }
                                })
                        }
                        GroupedRow(
                            position = positionFor(2, 6), elevation = 24.dp, onClick = {
                                scope.launch {
                                    appPreferences.setShowSaveDialog(!showSaveDialog)
                                }
                            }) {
                            LabeledSwitch(
                                title = stringResource(R.string.show_save_dialog),
                                checked = showSaveDialog,
                                onCheckedChange = { new ->
                                    scope.launch {
                                        appPreferences.setShowSaveDialog(new)
                                    }
                                })
                        }
                        GroupedRow(
                            position = positionFor(3, 6), elevation = 24.dp, onClick = {
                                scope.launch {
                                    appPreferences.setSwapSwipeActions(!swapSwipeActions)
                                }
                            }) {
                            LabeledSwitch(
                                title = stringResource(R.string.swap_swipe_actions),
                                checked = swapSwipeActions,
                                onCheckedChange = { new ->
                                    scope.launch {
                                        appPreferences.setSwapSwipeActions(new)
                                    }
                                })
                        }
                        GroupedRow(
                            position = positionFor(4, 6),
                            elevation = 24.dp,
                            onClick = { scope.launch { appPreferences.setGlassSlider(!glassSlider) } }) {
                            LabeledSwitch(
                                title = stringResource(R.string.glass_slider),
                                checked = glassSlider,
                                onCheckedChange = { new ->
                                    scope.launch { appPreferences.setGlassSlider(new) }
                                })
                        }
                        val clearedDefaultSourceMsg =
                            stringResource(R.string.cleared_default_source)
                        GroupedRow(
                            position = positionFor(5, 6), elevation = 24.dp, onClick = {
                                scope.launch {
                                    appPreferences.setDefaultImageSource(null)
                                    SnackbarController.pushEvent(
                                        SnackySnackbarEvents.MessageEvent(
                                            message = clearedDefaultSourceMsg,
                                            duration = SnackbarDuration.Short
                                        )
                                    )
                                }
                            }, verticalPadding = 4.dp
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    stringResource(R.string.default_image_source),
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    defaultImageSource ?: stringResource(R.string.none),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            TextButton(
                                onClick = {
                                    HapticFeedbacks.light()
                                    scope.launch {
                                        appPreferences.setDefaultImageSource(null)
                                        SnackbarController.pushEvent(
                                            SnackySnackbarEvents.MessageEvent(
                                                message = clearedDefaultSourceMsg,
                                                duration = SnackbarDuration.Short
                                            )
                                        )
                                    }
                                }) { Text(stringResource(R.string.clear_default_source)) }
                        }
                        GroupedRow(
                            position = positionFor(6, 6), elevation = 24.dp, onClick = {
                                themeMenuExpanded = true
                            }, verticalPadding = 4.dp
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.theme),
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Box {
                                TextButton(
                                    onClick = {
                                        HapticFeedbacks.light(); themeMenuExpanded = true
                                    }) {
                                    Text(currentTheme.name)
                                }
                                DropdownMenu(
                                    expanded = themeMenuExpanded, onDismissRequest = {
                                        HapticFeedbacks.light(); themeMenuExpanded = false
                                    }) {
                                    AppTheme.entries.forEach { theme ->
                                        val label = when (theme) {
                                            AppTheme.Dynamic -> stringResource(R.string.theme_dynamic)
                                            AppTheme.Light -> stringResource(R.string.theme_light)
                                            AppTheme.Dark -> stringResource(R.string.theme_dark)
                                            AppTheme.OLED -> stringResource(R.string.theme_oled)
                                        }
                                        DropdownMenuItem(text = { Text(label) }, onClick = {
                                            themeMenuExpanded = false
                                            HapticFeedbacks.light()
                                            scope.launch {
                                                appPreferences.setAppTheme(theme)
                                            }
                                            App.state.appTheme.value = theme
                                        })
                                    }
                                }
                            }
                        }
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
                    HapticFeedbacks.light()
                    modelPickerLauncher.launch("*/*")
                },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                shape = RoundedCornerShape(lerp(16f, 28f, press).dp),
                interactionSource = interaction,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 12.dp, bottom = 16.dp)
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
    }

    if (showImportProgress.value) {
        ModalBottomSheet(onDismissRequest = {
            showImportProgress.value = false
            importError.value = null
        }) {
            Column(Modifier.padding(16.dp)) {
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
                Spacer(modifier = Modifier.height(16.dp))
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
            Column(Modifier.padding(16.dp)) {
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

private enum class SettingsSection {
    OnnxSettings, OidnSettings, MainSettings
}

@Composable
fun PreferenceGroupHeading(title: String, modifier: Modifier = Modifier) {
    Column(
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .padding(horizontal = 12.dp)
            .fillMaxWidth()
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(6.dp))
    }
}

@Composable
fun PowerSlider(
    label: String,
    value: Int? = null,
    powers: List<Int>,
    maxAllowed: Int = Int.MAX_VALUE,
    onChange: (Int) -> Unit,
    hapticAction: () -> Unit,
    hideValue: Boolean = false,
) {
    val effectivePowers = remember(powers, maxAllowed) {
        powers.filter { it <= maxAllowed }.ifEmpty { listOf(powers.first()) }
    }
    val clampedValue = value?.coerceAtMost(effectivePowers.last())
    var index by remember(clampedValue, effectivePowers) {
        mutableIntStateOf(maxOf(effectivePowers.indexOf(clampedValue), 0))
    }
    LaunchedEffect(maxAllowed) {
        if (value != null && value >= maxAllowed && effectivePowers.isNotEmpty()) {
            onChange(effectivePowers.last())
        }
    }
    Column {
        Row {
            Text(
                label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium
            )
            if (!hideValue) Text(
                " • ${effectivePowers[index]}",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium
            )
        }

    }
    val sliderState = rememberSliderState(
        value = index.toFloat(),
        steps = (effectivePowers.size - 2).coerceAtLeast(0),
        trackRange = 0f..(effectivePowers.lastIndex.toFloat().coerceAtLeast(0f)),
    )
    LaunchedEffect(index) { sliderState.value = index.toFloat() }
    Slider(
        state = sliderState, onValueChange = {
            val newIdx = it.roundToInt().coerceIn(effectivePowers.indices)
            if (newIdx != index) {
                index = newIdx
                hapticAction()
                onChange(effectivePowers[newIdx])
            }
        }, enabled = effectivePowers.size > 1
    )
}


@Composable
fun LabeledSwitch(
    title: String,
    desc: String = "",
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
            )
            if (desc.isNotEmpty()) {
                Text(
                    text = desc,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Switch(
            checked = checked,
            thumbContent = {
                Icon(
                    imageVector = if (checked) Icons.Rounded.Check else Icons.Rounded.Close,
                    contentDescription = null,
                    modifier = Modifier.size(SwitchDefaults.IconSize),
                )
            },
            onCheckedChange = {
                HapticFeedbacks.light(); onCheckedChange(!checked)
            },
            modifier = Modifier
                .padding(end = 2.dp)
                .height(24.dp)
                .aspectRatio(2f)
                .wrapContentSize(Alignment.Center),
        )
    }
}

@Composable
fun PreferenceItem(
    modifier: Modifier = Modifier,
    icon: Any,
    iconTint: Color? = null,
    title: String,
    subtitle: String? = "",
    expanded: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
    expandedContent: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
    position: CardPosition,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        GroupedRow(position = position, onClick = { onClick() }, verticalPadding = 14.dp) {
            Spacer(modifier = Modifier.width(8.dp))
            when (icon) {
                is ImageVector -> Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint ?: Color.Unspecified,
                    modifier = Modifier.size(21.dp)
                )

                is Painter -> Icon(
                    painter = icon,
                    contentDescription = null,
                    tint = iconTint ?: Color.Unspecified,
                    modifier = Modifier.size(21.dp)
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                AnimatedVisibility(
                    visible = !subtitle.isNullOrEmpty(),
                    enter = fadeIn() + expandVertically(expandFrom = Alignment.Top),
                    exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Top)
                ) {
                    Spacer(modifier = Modifier.height(3.dp))
                    if (subtitle != null) {
                        Text(
                            subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
            if (trailing != null) {
                trailing()
            } else {
                val chevronRotation by animateFloatAsState(
                    targetValue = if (expanded) 90f else 0f, animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMedium
                    ), label = "chevron"
                )
                Icon(
                    Icons.Rounded.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier
                        .size(32.dp)
                        .rotate(chevronRotation)
                )
            }
        }
        Spacer(modifier = Modifier.height(GroupedListSpacing))
        AnimatedVisibility(visible = expanded) {
            Column(modifier = Modifier.fillMaxWidth()) {
                GroupedRow(
                    position = CardPosition.Trailing,
                    verticalPadding = 0.dp,
                    horizontalPadding = 0.dp
                ) {
                    Column(
                        Modifier.padding(
                            horizontal = 18.dp, vertical = 18.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(GroupedListSpacing),
                    ) {
                        expandedContent?.invoke()
                    }
                }
            }
        }
    }
}
