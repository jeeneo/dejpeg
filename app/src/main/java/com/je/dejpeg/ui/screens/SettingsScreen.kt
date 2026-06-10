/*
 * SPDX-FileCopyrightText: 2025 - 2026 dryerlint <https://codeberg.org/dryerlint>
 * SPDX-License-Identifier: GNU Affero General Public License v3.0 or later
 */


@file:Suppress(
    "KotlinConstantConditions", "SimplifyBooleanWithConstants", "SpellCheckingInspection"
)

package com.je.dejpeg.ui.screens

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.QuestionMark
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import androidx.core.net.toUri
import com.je.dejpeg.App
import com.je.dejpeg.AppPreferences
import com.je.dejpeg.BuildConfig
import com.je.dejpeg.HapticFeedbacks
import com.je.dejpeg.ProcessingMode
import com.je.dejpeg.R
import com.je.dejpeg.ThreadUtils
import com.je.dejpeg.ui.components.SnackbarDuration
import com.je.dejpeg.ui.components.SnackySnackbarController
import com.je.dejpeg.ui.components.SnackySnackbarEvents
import com.je.dejpeg.ui.components.loadFAQSections
import com.je.dejpeg.ui.components.rememberMaterialPressState
import com.je.dejpeg.ui.theme.AppTheme
import com.je.dejpeg.ui.viewmodel.ProcessingViewModel
import com.je.dejpeg.ui.viewmodel.SettingsViewModel
import com.je.dejpeg.utils.ModelManager
import com.je.dejpeg.utils.ModelType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel, processingViewModel: ProcessingViewModel, onBack: () -> Unit = {}
) {
    val context = App.ctx
    val modelManager = remember { ModelManager(context) }
    val appPreferences = remember { AppPreferences() }
    val scope = rememberCoroutineScope()
    val installedModels by viewModel.installedModels.collectAsState()
    val showImportProgress = remember { mutableStateOf(false) }
    var expandedSection by remember { mutableStateOf<SettingsSection?>(null) }
    fun toggle(section: SettingsSection) {
        HapticFeedbacks.light()
        expandedSection = if (expandedSection == section) null else section
    }

    var importProgress by remember { mutableIntStateOf(0) }
    val importedModelMessage = stringResource(R.string.imported_model)
    val deletedModelMessage = stringResource(R.string.deleted_model)
    val blockedSwitchingMessage = stringResource(R.string.model_switch_blocked_processing)
    val chunkSize by viewModel.chunkSize.collectAsState()
    val overlapSize by viewModel.overlapSize.collectAsState()
    val onnxDeviceThreads by viewModel.onnxDeviceThreads.collectAsState()
    var activeModelName by remember {
        mutableStateOf(runBlocking { viewModel.getActiveModelName() })
    }
    val showSaveDialog by appPreferences.showSaveDialog.collectAsState(initial = true)
    val defaultImageSource by appPreferences.defaultImageSource.collectAsState(initial = null)
    val hapticFeedbackEnabled by appPreferences.hapticFeedbackEnabled.collectAsState(initial = true)
    val swapSwipeActions by appPreferences.swapSwipeActions.collectAsState(initial = false)
    val modelInfoDialog = remember { mutableStateOf<Pair<String, String>?>(null) }

    val processingMode by viewModel.processingMode.collectAsState()
    val installedOidnModels by viewModel.installedOidnModels.collectAsState()
    val oidnHdr by viewModel.oidnHdr.collectAsState()
    @Suppress("SpellCheckingInspection") val oidnSrgb by viewModel.oidnSrgb.collectAsState()
    val oidnQuality by viewModel.oidnQuality.collectAsState()
    val oidnNumThreads by viewModel.oidnNumThreads.collectAsState()
    var activeOidnModelName by remember {
        mutableStateOf(runBlocking { viewModel.getActiveModelName(ModelType.OIDN) })
    }
    val uriHandler = LocalUriHandler.current

    BackHandler {
        if (expandedSection != null) expandedSection = null else onBack()
    }

    LaunchedEffect(installedModels) {
        activeModelName = withContext(Dispatchers.IO) { viewModel.getActiveModelName() }
    }

    LaunchedEffect(installedOidnModels) {
        activeOidnModelName =
            withContext(Dispatchers.IO) { viewModel.getActiveModelName(ModelType.OIDN) }
    }

    LaunchedEffect(processingMode) {
        expandedSection = null
    }

    val modelPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { it ->
            showImportProgress.value = true
            importProgress = 0
            viewModel.importModel(it, onProgress = { importProgress = it }, onSuccess = { name ->
                showImportProgress.value = false
                scope.launch {
                    SnackySnackbarController.pushEvent(
                        SnackySnackbarEvents.MessageEvent(
                            message = importedModelMessage.format(name),
                            duration = SnackbarDuration.Short
                        )
                    )
                }
            })
        }
    }

    val oidnModelPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { selectedUri ->
            showImportProgress.value = true
            importProgress = 0
            viewModel.importModel(
                selectedUri,
                type = ModelType.OIDN,
                onProgress = { importProgress = it },
                onSuccess = { name ->
                    showImportProgress.value = false
                    scope.launch {
                        SnackySnackbarController.pushEvent(
                            SnackySnackbarEvents.MessageEvent(
                                message = importedModelMessage.format(name),
                                duration = SnackbarDuration.Short
                            )
                        )
                    }
                })
        }
    }

    val selectedColor = MaterialTheme.colorScheme.primary
    val selectedContentColor = MaterialTheme.colorScheme.onPrimary
    val unselectedColor = MaterialTheme.colorScheme.surfaceVariant
    val unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant

    Scaffold(
        floatingActionButton = {
            Box(Modifier.padding(bottom = 100.dp)) {
                val fabInteractionSource = remember { MutableInteractionSource() }
                val fabPress by rememberMaterialPressState(fabInteractionSource)
                val animatedFabCorner = lerp(16f, 28f, fabPress)

                ExtendedFloatingActionButton(
                    onClick = {
                    HapticFeedbacks.light()
                    if (BuildConfig.OIDN_ENABLED && processingMode == ProcessingMode.OIDN) {
                        oidnModelPickerLauncher.launch("*/*")
                    } else {
                        modelPickerLauncher.launch("*/*")
                    }
                },
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = {
                        Text(
                            if (BuildConfig.OIDN_ENABLED && processingMode == ProcessingMode.OIDN) stringResource(
                                R.string.import_tza_model
                            )
                            else stringResource(R.string.import_model_text)
                        )
                    },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    shape = RoundedCornerShape(animatedFabCorner.dp),
                    interactionSource = fabInteractionSource,
                    expanded = true
                )
            }
        }, contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { padding ->
        CompositionLocalProvider(LocalOverscrollFactory provides null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(top = 8.dp, bottom = 16.dp)
            ) {
                PreferenceGroupHeading(stringResource(R.string.header_settings))
                if (BuildConfig.OIDN_ENABLED) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        shape = MaterialTheme.shapes.large,
                        tonalElevation = 1.dp,
                        color = MaterialTheme.colorScheme.surface
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                stringResource(R.string.processing_mode),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Button(
                                    onClick = {
                                        HapticFeedbacks.light()
                                        viewModel.setProcessingMode(ProcessingMode.ONNX)
                                    }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(
                                        topStart = 24.dp,
                                        bottomStart = 24.dp,
                                        topEnd = 8.dp,
                                        bottomEnd = 8.dp
                                    ), colors = ButtonDefaults.buttonColors(
                                        containerColor = if (processingMode == ProcessingMode.ONNX) {
                                            selectedColor
                                        } else {
                                            unselectedColor
                                        },
                                        contentColor = if (processingMode == ProcessingMode.ONNX) {
                                            selectedContentColor
                                        } else {
                                            unselectedContentColor
                                        }
                                    )
                                ) {
                                    Text(stringResource(R.string.mode_onnx))
                                }

                                Button(
                                    onClick = {
                                        HapticFeedbacks.light()
                                        viewModel.setProcessingMode(ProcessingMode.OIDN)
                                    }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(
                                        topStart = 8.dp,
                                        bottomStart = 8.dp,
                                        topEnd = 24.dp,
                                        bottomEnd = 24.dp
                                    ), colors = ButtonDefaults.buttonColors(
                                        containerColor = if (processingMode == ProcessingMode.OIDN) {
                                            selectedColor
                                        } else {
                                            unselectedColor
                                        },
                                        contentColor = if (processingMode == ProcessingMode.OIDN) {
                                            selectedContentColor
                                        } else {
                                            unselectedContentColor
                                        }
                                    )
                                ) {
                                    Text(stringResource(R.string.mode_oidn))
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                var containerWidth by remember { mutableIntStateOf(0) }
                val animatedOffset = remember { Animatable(0f) }

                @Composable
                fun OnnxPreferenceGroupCard() {
                    PreferenceGroupCard {
                        PreferenceItem(
                            icon = painterResource(id = R.drawable.ic_model),
                            iconBackgroundColor = MaterialTheme.colorScheme.surfaceVariant,
                            iconTint = MaterialTheme.colorScheme.onSurfaceVariant,
                            title = stringResource(R.string.settings_title_models),
                            subtitle = activeModelName ?: stringResource(R.string.no_model_loaded),
                            ellipsizeSubtitle = true,
                            expanded = expandedSection == SettingsSection.ModelManagement,
                            expandedContent = {
                                val extractedMsg = stringResource(R.string.extracted_starter_models)
                                val failedMsg =
                                    stringResource(R.string.failed_to_extract_starter_models)
                                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                                    installedModels.forEachIndexed { index, modelName ->
                                        val isActive = modelName == activeModelName
                                        val position = when {
                                            installedModels.size == 1 -> CardPosition.Solo
                                            index == 0 -> CardPosition.Leading
                                            index == installedModels.lastIndex -> CardPosition.Trailing
                                            else -> CardPosition.Center
                                        }
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(
                                                    cardShape(
                                                        position, outer = 12.dp, inner = 4.dp
                                                    )
                                                )
                                                .background(
                                                    if (isActive) MaterialTheme.colorScheme.primaryContainer.copy(
                                                        alpha = 0.4f
                                                    )
                                                    else MaterialTheme.colorScheme.surfaceContainerHigh.copy(
                                                        alpha = 0.5f
                                                    )
                                                )
                                                .clickable {
                                                    HapticFeedbacks.light()
                                                    if (processingViewModel.isProcessingOrQueueActive()) {
                                                        scope.launch {
                                                            SnackySnackbarController.pushEvent(
                                                                SnackySnackbarEvents.MessageEvent(
                                                                    message = blockedSwitchingMessage,
                                                                    duration = SnackbarDuration.Short
                                                                )
                                                            )
                                                        }
                                                    } else {
                                                        viewModel.setActiveModelByName(modelName)
                                                        activeModelName = modelName
                                                    }
                                                }
                                                .padding(horizontal = 8.dp, vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                modelName,
                                                modifier = Modifier.weight(1f),
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                maxLines = 1,
                                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                            )
                                            modelManager.getModelInfo(modelName)?.let {
                                                IconButton(onClick = {
                                                    HapticFeedbacks.light(); modelInfoDialog.value =
                                                    modelName to it
                                                }, modifier = Modifier.size(32.dp)) {
                                                    Icon(
                                                        Icons.Filled.Info,
                                                        contentDescription = stringResource(R.string.info),
                                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                            }
                                            IconButton(
                                                onClick = {
                                                    HapticFeedbacks.light(); viewModel.deleteModels(
                                                    listOf(
                                                        modelName
                                                    )
                                                ) {
                                                    scope.launch {
                                                        SnackySnackbarController.pushEvent(
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
                                                    Icons.Filled.Delete,
                                                    contentDescription = stringResource(R.string.delete),
                                                    tint = MaterialTheme.colorScheme.error,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }
                                        if (index < installedModels.lastIndex) Spacer(
                                            Modifier.height(
                                                4.dp
                                            )
                                        )
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Spacer(Modifier.weight(1f))
                                        TextButton(onClick = {
                                            HapticFeedbacks.light()
                                            modelPickerLauncher.launch("*/*")
                                        }) {
                                            Icon(
                                                Icons.Filled.Add,
                                                null,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(Modifier.width(4.dp))
                                            Text(stringResource(R.string.import_model_text))
                                        }
                                        TextButton(onClick = {
                                            HapticFeedbacks.light()
                                            scope.launch {
                                                val extracted = withContext(Dispatchers.IO) {
                                                    modelManager.extractStarterModel(setAsActive = true)
                                                }
                                                if (extracted) {
                                                    viewModel.refreshInstalledModels(ModelType.ONNX)
                                                    SnackySnackbarController.pushEvent(
                                                        SnackySnackbarEvents.MessageEvent(
                                                            message = extractedMsg,
                                                            duration = SnackbarDuration.Short
                                                        )
                                                    )
                                                } else {
                                                    SnackySnackbarController.pushEvent(
                                                        SnackySnackbarEvents.MessageEvent(
                                                            message = failedMsg,
                                                            duration = SnackbarDuration.Short
                                                        )
                                                    )
                                                }
                                            }
                                        }) {
                                            Icon(
                                                Icons.Filled.Archive,
                                                null,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(Modifier.width(4.dp))
                                            Text(stringResource(R.string.extract))
                                        }
                                        TextButton(onClick = {
                                            HapticFeedbacks.light()
                                            val intent = Intent(
                                                Intent.ACTION_VIEW,
                                                "https://codeberg.org/dryerlint/dejpeg/src/branch/main/models".toUri()
                                            ); context.startActivity(intent)
                                        }) {
                                            Icon(
                                                Icons.Filled.Download,
                                                null,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(Modifier.width(4.dp))
                                            Text(stringResource(R.string.download))
                                        }
                                    }
                                }
                            },
                            onClick = {
                                toggle(SettingsSection.ModelManagement)
                            },
                            position = CardPosition.Leading,
                        )
                        PreferenceItem(
                            icon = Icons.Filled.GridOn,
                            iconBackgroundColor = MaterialTheme.colorScheme.secondaryContainer,
                            iconTint = MaterialTheme.colorScheme.onSecondaryContainer,
                            title = stringResource(R.string.settings_item_title_processing),
                            subtitle = stringResource(
                                R.string.chunk_size_px, chunkSize
                            ) + " • " + stringResource(R.string.overlap_size_px, overlapSize),
                            expanded = expandedSection == SettingsSection.Chunk,
                            expandedContent = {
                                val maxThreads = remember {
                                    Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
                                }
                                Column(Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
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

                                    val resolvedThreads =
                                        ThreadUtils.resolveThreadCount(onnxDeviceThreads)

                                    val threadValue = if (onnxDeviceThreads == 0) {
                                        stringResource(R.string.thread_value_auto, resolvedThreads)
                                    } else {
                                        onnxDeviceThreads.toString()
                                    }
                                    val threadLabel =
                                        "${stringResource(R.string.processing_threads_desc)} • $threadValue"

                                    PowerSlider(
                                        label = threadLabel,
                                        value = onnxDeviceThreads,
                                        hideValue = true,
                                        powers = (0..maxThreads).toList(),
                                        onChange = { viewModel.setOnnxDeviceThreads(it) },
                                        hapticAction = { HapticFeedbacks.light() })
                                }
                            },
                            onClick = { toggle(SettingsSection.Chunk) },
                            position = CardPosition.Trailing
                        )
                    }
                }

                @Composable
                fun OidnPreferenceGroupCard() {
                    PreferenceGroupCard {
                        PreferenceItem(
                            icon = painterResource(id = R.drawable.ic_model),
                            iconBackgroundColor = MaterialTheme.colorScheme.secondaryContainer,
                            iconTint = MaterialTheme.colorScheme.onSecondaryContainer,
                            title = stringResource(R.string.oidn_model_management),
                            subtitle = activeOidnModelName
                                ?: stringResource(R.string.no_model_loaded),
                            ellipsizeSubtitle = true,
                            expanded = expandedSection == SettingsSection.OidnModelManagement,
                            expandedContent = {
                                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                                    installedOidnModels.forEachIndexed { index, modelName ->
                                        val isActive = modelName == activeOidnModelName
                                        val position = when {
                                            installedOidnModels.size == 1 -> CardPosition.Solo
                                            index == 0 -> CardPosition.Leading
                                            index == installedOidnModels.lastIndex -> CardPosition.Trailing
                                            else -> CardPosition.Center
                                        }
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(
                                                    cardShape(
                                                        position, outer = 12.dp, inner = 4.dp
                                                    )
                                                )
                                                .background(
                                                    if (isActive) MaterialTheme.colorScheme.primaryContainer.copy(
                                                        alpha = 0.4f
                                                    )
                                                    else MaterialTheme.colorScheme.surfaceContainerHigh.copy(
                                                        alpha = 0.5f
                                                    )
                                                )
                                                .clickable {
                                                    HapticFeedbacks.light()
                                                    if (processingViewModel.isProcessingOrQueueActive()) {
                                                        scope.launch {
                                                            SnackySnackbarController.pushEvent(
                                                                SnackySnackbarEvents.MessageEvent(
                                                                    message = blockedSwitchingMessage,
                                                                    duration = SnackbarDuration.Short
                                                                )
                                                            )
                                                        }
                                                    } else {
                                                        viewModel.setActiveModelByName(
                                                            modelName, ModelType.OIDN
                                                        )
                                                        activeOidnModelName = modelName
                                                    }
                                                }
                                                .padding(horizontal = 8.dp, vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically) {
                                            Spacer(Modifier.width(12.dp))
                                            Text(
                                                modelName,
                                                modifier = Modifier.weight(1f),
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                                                color = if (isActive) MaterialTheme.colorScheme.primary
                                                else MaterialTheme.colorScheme.onSurface,
                                                maxLines = 1,
                                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                            )
                                            IconButton(
                                                onClick = {
                                                    HapticFeedbacks.light(); viewModel.deleteModels(
                                                    listOf(
                                                        modelName
                                                    ), ModelType.OIDN
                                                ) {
                                                    scope.launch {
                                                        SnackySnackbarController.pushEvent(
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
                                                    Icons.Filled.Delete,
                                                    contentDescription = stringResource(R.string.delete),
                                                    tint = MaterialTheme.colorScheme.error,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }
                                        if (index < installedOidnModels.lastIndex) Spacer(
                                            Modifier.height(
                                                4.dp
                                            )
                                        )
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        if (installedOidnModels.isEmpty()) {
                                            Text(
                                                stringResource(R.string.no_model_loaded),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.padding(
                                                    horizontal = 8.dp, vertical = 4.dp
                                                )
                                            )
                                        } else {
                                            Spacer(Modifier.weight(1f))
                                        }
                                        TextButton(onClick = { oidnModelPickerLauncher.launch("*/*") }) {
                                            Icon(
                                                Icons.Filled.Add,
                                                null,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(Modifier.width(4.dp))
                                            Text(stringResource(R.string.import_tza_model))
                                        }
                                    }
                                }
                            },
                            onClick = {
                                toggle(SettingsSection.OidnModelManagement)
                            },
                            position = CardPosition.Leading
                        )
                        PreferenceItem(
                            icon = Icons.Filled.GridOn,
                            iconBackgroundColor = MaterialTheme.colorScheme.secondaryContainer,
                            iconTint = MaterialTheme.colorScheme.onSecondaryContainer,
                            title = stringResource(R.string.oidn_settings),
                            subtitle = stringResource(R.string.oidn_settings_desc),
                            expanded = expandedSection == SettingsSection.OidnSettings,
                            expandedContent = {
                                val maxThreads = remember {
                                    Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
                                }

                                Column(Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
                                    LabeledSwitch(
                                        title = stringResource(R.string.oidn_hdr),
                                        desc = stringResource(R.string.oidn_hdr_desc),
                                        checked = oidnHdr,
                                        onCheckedChange = { viewModel.setOidnHdrPref(it) })
                                    Spacer(modifier = Modifier.height(8.dp))
                                    LabeledSwitch(
                                        title = stringResource(R.string.oidn_srgb),
                                        desc = stringResource(R.string.oidn_srgb_desc),
                                        checked = oidnSrgb,
                                        onCheckedChange = { viewModel.setOidnSrgbPref(it) })
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
                                    qualityOptions.chunked(2).forEach { rowOptions ->
                                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                                            rowOptions.forEachIndexed { index, (value, label) ->
                                                SegmentedButton(
                                                    selected = oidnQuality == value, onClick = {
                                                        HapticFeedbacks.light(); viewModel.setOidnQualityPref(
                                                        value
                                                    )
                                                    }, shape = SegmentedButtonDefaults.itemShape(
                                                        index = index, count = rowOptions.size
                                                    )
                                                ) { Text(label) }
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(8.dp))
                                    }
                                    val resolvedOidnThreads =
                                        ThreadUtils.resolveThreadCount(oidnNumThreads)

                                    Spacer(modifier = Modifier.height(8.dp))
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
                                }

                            },
                            onClick = {
                                toggle(SettingsSection.OidnSettings)
                            },
                            position = CardPosition.Trailing
                        )
                    }
                }

                val isOidn = BuildConfig.OIDN_ENABLED && processingMode == ProcessingMode.OIDN

                Box(modifier = Modifier
                    .fillMaxWidth()
                    .onSizeChanged { containerWidth = it.width }
                    .clip(MaterialTheme.shapes.large)
                    .pointerInput(processingMode) {
                        if (!BuildConfig.OIDN_ENABLED) return@pointerInput
                        detectHorizontalDragGestures(onDragEnd = {
                            val threshold = containerWidth * 0.35f
                            scope.launch {
                                if (animatedOffset.value > threshold && processingMode == ProcessingMode.OIDN) {
                                    animatedOffset.animateTo(containerWidth.toFloat(), spring())
                                    viewModel.setProcessingMode(ProcessingMode.ONNX)
                                    animatedOffset.snapTo(0f)
                                } else if (animatedOffset.value < -threshold && processingMode == ProcessingMode.ONNX) {
                                    animatedOffset.animateTo(
                                        -containerWidth.toFloat(), spring()
                                    )
                                    viewModel.setProcessingMode(ProcessingMode.OIDN)
                                    animatedOffset.snapTo(0f)
                                } else {
                                    animatedOffset.animateTo(0f, spring())
                                }
                            }
                        }, onDragCancel = {
                            scope.launch { animatedOffset.animateTo(0f, spring()) }
                        }, onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            val newOffset = animatedOffset.value + dragAmount
                            val clamped = when (processingMode) {
                                ProcessingMode.OIDN -> newOffset.coerceAtLeast(0f)
                                ProcessingMode.ONNX -> newOffset.coerceAtMost(0f)
                            }
                            scope.launch { animatedOffset.snapTo(clamped) }
                        })
                    }) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .graphicsLayer {
                                translationX =
                                    if (isOidn) animatedOffset.value - containerWidth else animatedOffset.value
                            }) {
                        OnnxPreferenceGroupCard()
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .graphicsLayer {
                                translationX =
                                    if (isOidn) animatedOffset.value else animatedOffset.value + containerWidth
                            }) {
                        OidnPreferenceGroupCard()
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))

                val currentTheme = App.state.appTheme.value

                var themeMenuExpanded by remember { mutableStateOf(false) }

                PreferenceGroupCard {
                    PreferenceItem(
                        icon = Icons.Filled.Settings,
                        iconBackgroundColor = MaterialTheme.colorScheme.surfaceVariant,
                        iconTint = MaterialTheme.colorScheme.onSurfaceVariant,
                        title = stringResource(R.string.settings_item_title_options),
                        subtitle = stringResource(R.string.settings_item_subtitle_options),
                        expanded = expandedSection == SettingsSection.Preferences,
                        expandedContent = {
                            Column(Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
                                LabeledSwitch(
                                    title = stringResource(R.string.vibration_on_touch),
                                    checked = hapticFeedbackEnabled,
                                    onCheckedChange = { new ->
                                        scope.launch {
                                            appPreferences.setHapticFeedbackEnabled(new)
                                        }
                                    })
                                Spacer(modifier = Modifier.height(8.dp))

                                LabeledSwitch(
                                    title = stringResource(R.string.show_save_dialog),
                                    checked = showSaveDialog,
                                    onCheckedChange = { new ->
                                        scope.launch {
                                            appPreferences.setShowSaveDialog(new)
                                        }
                                    })
                                Spacer(modifier = Modifier.height(8.dp))

                                LabeledSwitch(
                                    title = stringResource(R.string.swap_swipe_actions),
                                    checked = swapSwipeActions,
                                    onCheckedChange = { new ->
                                        scope.launch {
                                            appPreferences.setSwapSwipeActions(new)
                                        }
                                    })
                                Spacer(modifier = Modifier.height(8.dp))
                                val clearedDefaultSourceMsg =
                                    stringResource(R.string.cleared_default_source)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
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
                                    TextButton(onClick = {
                                        scope.launch {
                                            appPreferences.setDefaultImageSource(null)
                                            SnackySnackbarController.pushEvent(
                                                SnackySnackbarEvents.MessageEvent(
                                                    message = clearedDefaultSourceMsg,
                                                    duration = SnackbarDuration.Short
                                                )
                                            )
                                        }
                                        HapticFeedbacks.light()
                                    }) { Text(stringResource(R.string.clear_default_source)) }
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
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
                                            onClick = { themeMenuExpanded = true }) {
                                            Text(currentTheme.name)
                                        }
                                        DropdownMenu(
                                            expanded = themeMenuExpanded,
                                            onDismissRequest = { themeMenuExpanded = false }) {
                                            AppTheme.entries.forEach { theme ->
                                                val label = when (theme) {
                                                    AppTheme.Dynamic -> stringResource(R.string.theme_dynamic)
                                                    AppTheme.Light -> stringResource(R.string.theme_light)
                                                    AppTheme.Dark -> stringResource(R.string.theme_dark)
                                                    AppTheme.OLED -> stringResource(R.string.theme_oled)
                                                }
                                                DropdownMenuItem(text = { Text(label) }, onClick = {
                                                    themeMenuExpanded = false
                                                    scope.launch {
                                                        appPreferences.setAppTheme(theme)
                                                    }
                                                    App.state.appTheme.value = theme
                                                })
                                            }
                                        }
                                    }
                                }
                            }
                        },
                        onClick = {
                            toggle(SettingsSection.Preferences)
                        },
                        position = CardPosition.Leading,
                    )

                    AnimatedVisibility(visible = processingMode == ProcessingMode.ONNX || !BuildConfig.OIDN_ENABLED) {
                        Column {
                            PreferenceItem(
                                icon = Icons.Filled.QuestionMark,
                                iconBackgroundColor = MaterialTheme.colorScheme.primaryContainer,
                                iconTint = MaterialTheme.colorScheme.onPrimaryContainer,
                                title = stringResource(R.string.settings_item_title_help),
                                subtitle = stringResource(R.string.settings_item_subtitle_help_faq),
                                expanded = expandedSection == SettingsSection.FAQ,
                                expandedContent = {
                                    val faqSections = remember { loadFAQSections(context) }
                                    Column(Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
                                        faqSections.forEach { section ->
                                            FAQSection(
                                                section.title, section.content, section.subSections
                                            )
                                        }
                                    }
                                },
                                onClick = {
                                    toggle(SettingsSection.FAQ)
                                },
                                position = CardPosition.Center
                            )
                        }
                    }

                    PreferenceItem(
                        icon = Icons.Filled.Code,
                        iconBackgroundColor = MaterialTheme.colorScheme.primaryContainer,
                        iconTint = MaterialTheme.colorScheme.onPrimaryContainer,
                        title = stringResource(R.string.settings_item_title_sourcecodelink),
                        subtitle = stringResource(R.string.settings_item_subtitle_sourcecode),
                        trailing = {
                            Icon(
                                Icons.AutoMirrored.Filled.OpenInNew,
                                contentDescription = stringResource(R.string.settings_item_sourcecode_description),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                        },
                        onClick = { uriHandler.openUri("https://codeberg.org/dryerlint/dejpeg") },
                        position = CardPosition.Trailing
                    )
                }

                Spacer(modifier = Modifier.height(100.dp))
            }
        }
    }

    if (showImportProgress.value) {
        ModalBottomSheet(onDismissRequest = {
            showImportProgress.value = false
        }) {
            Column(Modifier.padding(16.dp)) {
                Text(
                    stringResource(R.string.importing_model),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(8.dp))
                val animatedProgress by animateFloatAsState(
                    targetValue = importProgress.coerceIn(0, 100) / 100f, animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessLow
                    ), label = "import_progress"
                )
                LinearProgressIndicator(
                    progress = { animatedProgress },
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "${importProgress.coerceIn(0, 100)}%",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = {
                        showImportProgress.value = false
                    }) {
                        Text(stringResource(R.string.cancel))
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
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

private enum class SettingsSection {
    Chunk, Preferences, OidnSettings, ModelManagement, OidnModelManagement, FAQ
}

@Composable
fun PreferenceGroupHeading(title: String, modifier: Modifier = Modifier) {
    Column(
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .height(48.dp)
            .padding(horizontal = 24.dp)
            .fillMaxWidth()
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun PreferenceGroupCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        content()
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
                label,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary
            )
            if (!hideValue) Text(
                " • ${effectivePowers[index]}",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary
            )
        }

    }
    Slider(
        value = index.toFloat(),
        onValueChange = {
            val newIdx = it.roundToInt().coerceIn(effectivePowers.indices)
            if (newIdx != index) {
                index = newIdx
                hapticAction()
                onChange(effectivePowers[newIdx])
            }
        },
        valueRange = 0f..(effectivePowers.lastIndex.toFloat().coerceAtLeast(0f)),
        steps = (effectivePowers.size - 2).coerceAtLeast(0),
        enabled = effectivePowers.size > 1
    )
}

@Composable
fun LabeledSwitch(
    title: String, desc: String = "", checked: Boolean, onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() }, indication = ripple()
            ) { onCheckedChange(!checked) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold
            )
            if (desc.isNotEmpty()) {
                Text(
                    desc,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Switch(
            checked = checked, onCheckedChange = {
            onCheckedChange(it)
            HapticFeedbacks.light()
        }, thumbContent = if (checked) {
            {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    modifier = Modifier.size(SwitchDefaults.IconSize),
                )
            }
        } else {
            {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = null,
                    modifier = Modifier.size(SwitchDefaults.IconSize),
                )
            }
        }, colors = SwitchDefaults.colors(
            checkedThumbColor = MaterialTheme.colorScheme.primaryContainer,
            checkedTrackColor = MaterialTheme.colorScheme.onSecondaryContainer,
            uncheckedThumbColor = MaterialTheme.colorScheme.secondary,
            uncheckedTrackColor = MaterialTheme.colorScheme.secondaryContainer,
            uncheckedBorderColor = Color.Transparent,
        ))
    }
}

enum class CardPosition { Leading, Center, Trailing, Solo }

fun cardShape(
    position: CardPosition, outer: Dp = 16.dp, inner: Dp = 6.dp
): RoundedCornerShape = when (position) {
    CardPosition.Leading -> RoundedCornerShape(
        topStart = outer, topEnd = outer, bottomStart = inner, bottomEnd = inner
    )

    CardPosition.Center -> RoundedCornerShape(6.dp)
    CardPosition.Trailing -> RoundedCornerShape(
        topStart = inner, topEnd = inner, bottomStart = outer, bottomEnd = outer
    )

    CardPosition.Solo -> RoundedCornerShape(outer)
}

@Composable
fun PreferenceItem(
    icon: Any,
    iconBackgroundColor: Color,
    iconTint: Color,
    title: String,
    subtitle: String,
    ellipsizeSubtitle: Boolean = false,
    expanded: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
    expandedContent: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
    position: CardPosition = CardPosition.Solo,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val press by rememberMaterialPressState(interactionSource)
    val animatedIconCorner = lerp(14f, 22f, press)

    Surface(
        shape = cardShape(position),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(interactionSource = interactionSource, indication = ripple()) {
                        HapticFeedbacks.light()
                        onClick()
                    }
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(48.dp)
                        .background(iconBackgroundColor, RoundedCornerShape(animatedIconCorner.dp))
                ) {
                    when (icon) {
                        is ImageVector -> Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = iconTint,
                            modifier = Modifier.size(24.dp)
                        )

                        is Painter -> Icon(
                            painter = icon,
                            contentDescription = null,
                            tint = iconTint,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        title,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = if (ellipsizeSubtitle) 1 else Int.MAX_VALUE,
                        overflow = if (ellipsizeSubtitle) androidx.compose.ui.text.style.TextOverflow.Ellipsis else androidx.compose.ui.text.style.TextOverflow.Clip
                    )
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
                        Icons.Filled.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier
                            .size(24.dp)
                            .rotate(chevronRotation)
                    )
                }
            }
            AnimatedVisibility(visible = expanded) {
                expandedContent?.invoke()
            }
        }
    }
}

@Composable
fun FAQSection(title: String, content: String?, subSections: List<Pair<String, String>>? = null) {
    var expanded by remember { mutableStateOf(false) }
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 90f else 0f, animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium
        ), label = "chevron"
    )

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { HapticFeedbacks.light(); expanded = !expanded }
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically) {
            Text(title, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Icon(
                Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier
                    .size(20.dp)
                    .rotate(chevronRotation)
            )
        }
        AnimatedVisibility(visible = expanded) {
            Column(modifier = Modifier.padding(start = 8.dp, bottom = 8.dp)) {
                content?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                subSections?.forEach { (subTitle, subContent) ->
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        subTitle,
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        subContent,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
    }
}
