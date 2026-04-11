/*
 * SPDX-FileCopyrightText: 2025 - 2026 dryerlint <https://codeberg.org/dryerlint>
 * SPDX-License-Identifier: GNU Affero General Public License v3.0 or later
 */


@file:Suppress("KotlinConstantConditions", "SimplifyBooleanWithConstants")

package com.je.dejpeg.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.QuestionAnswer
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import androidx.core.net.toUri
import com.je.dejpeg.BuildConfig
import com.je.dejpeg.ModelManager
import com.je.dejpeg.ModelType
import com.je.dejpeg.R
import com.je.dejpeg.data.ProcessingMode
import com.je.dejpeg.ui.components.AboutDialog
import com.je.dejpeg.ui.components.ErrorAlertDialog
import com.je.dejpeg.ui.components.SnackbarDuration
import com.je.dejpeg.ui.components.SnackySnackbarController
import com.je.dejpeg.ui.components.SnackySnackbarEvents
import com.je.dejpeg.ui.components.loadFAQSections
import com.je.dejpeg.ui.components.rememberMaterialPressState
import com.je.dejpeg.ui.viewmodel.ProcessingViewModel
import com.je.dejpeg.ui.viewmodel.SettingsViewModel
import com.je.dejpeg.utils.rememberHapticFeedback
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

@Suppress("AssignedValueIsNeverRead")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel, processingViewModel: ProcessingViewModel, onBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val modelManager = remember { ModelManager(context) }
    val appPreferences = remember { com.je.dejpeg.data.AppPreferences(context) }
    val scope = rememberCoroutineScope()
    val installedModels by viewModel.installedModels.collectAsState()
    var showImportProgress by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }
    var expandedSection by remember { mutableStateOf<SettingsSection?>(null) }
    fun toggle(section: SettingsSection) {
        expandedSection = if (expandedSection == section) null else section
    }

    var importProgress by remember { mutableIntStateOf(0) }
    val importedModelMessage = stringResource(R.string.imported_model)
    val deletedModelMessage = stringResource(R.string.deleted_model)
    val blockedSwitchingMessage = stringResource(R.string.model_switch_blocked_processing)
    var pendingImportUri by remember { mutableStateOf<Uri?>(null) }
    val chunkSize by viewModel.chunkSize.collectAsState()
    val overlapSize by viewModel.overlapSize.collectAsState()
    val onnxDeviceThreads by viewModel.onnxDeviceThreads.collectAsState()
    var activeModelName by remember {
        mutableStateOf(runBlocking { viewModel.getActiveModelName() })
    }
    var pendingModelSelection by remember { mutableStateOf<String?>(null) }
    var warningState by remember { mutableStateOf<ModelWarningState?>(null) }
    val showSaveDialog by appPreferences.showSaveDialog.collectAsState(initial = true)
    val defaultImageSource by appPreferences.defaultImageSource.collectAsState(initial = null)
    val hapticFeedbackEnabled by appPreferences.hapticFeedbackEnabled.collectAsState(initial = true)
    val swapSwipeActions by appPreferences.swapSwipeActions.collectAsState(initial = false)
    var modelInfoDialog by remember { mutableStateOf<Pair<String, String>?>(null) }

    val processingMode by viewModel.processingMode.collectAsState()
    val installedOidnModels by viewModel.installedOidnModels.collectAsState()
    val oidnHdr by viewModel.oidnHdr.collectAsState()
    @Suppress("SpellCheckingInspection") val oidnSrgb by viewModel.oidnSrgb.collectAsState()
    val oidnQuality by viewModel.oidnQuality.collectAsState()
    val oidnNumThreads by viewModel.oidnNumThreads.collectAsState()
    var activeOidnModelName by remember {
        mutableStateOf(runBlocking { viewModel.getActiveModelName(ModelType.OIDN) })
    }

    fun threadLabel(value: Int, autoString: String) = if (value == 0) autoString else "$value"

    DisposableEffect(Unit) {
        onDispose {
            showImportProgress = false
            showAbout = false
            warningState = null
            pendingModelSelection = null
            pendingImportUri = null
        }
    }

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
            pendingImportUri = it
            showImportProgress = true
            importProgress = 0
            viewModel.importModel(it, onProgress = { importProgress = it }, onSuccess = { name ->
                showImportProgress = false
                pendingImportUri = null
                scope.launch {
                    SnackySnackbarController.pushEvent(
                        SnackySnackbarEvents.MessageEvent(
                            message = importedModelMessage.format(name),
                            duration = SnackbarDuration.Short
                        )
                    )
                }
            }, onError = { err ->
                showImportProgress = false
                pendingImportUri = null
                warningState = ModelWarningState.Error(err)
            }, onWarning = { modelName, warning ->
                showImportProgress = false
                warningState = ModelWarningState.ModelWarning(modelName, warning, isImport = true)
            })
        }
    }

    val oidnModelPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { selectedUri ->
            showImportProgress = true
            importProgress = 0
            viewModel.importModel(
                selectedUri,
                type = ModelType.OIDN,
                onProgress = { importProgress = it },
                onSuccess = { name ->
                    showImportProgress = false
                    scope.launch {
                        SnackySnackbarController.pushEvent(
                            SnackySnackbarEvents.MessageEvent(
                                message = importedModelMessage.format(name),
                                duration = SnackbarDuration.Short
                            )
                        )
                    }
                },
                onError = { err ->
                    showImportProgress = false
                    warningState = ModelWarningState.Error(err)
                })
        }
    }

    Scaffold(
        floatingActionButton = {
            Box(Modifier.padding(bottom = 100.dp)) {
                val haptic = rememberHapticFeedback()
                val fabInteractionSource = remember { MutableInteractionSource() }
                val fabPress by rememberMaterialPressState(fabInteractionSource)
                val animatedFabCorner = lerp(16f, 28f, fabPress)

                ExtendedFloatingActionButton(
                    onClick = {
                    haptic.light() // huh wonky, reformatting doesn't like you
                    if (BuildConfig.NATIVE_ENABLED && processingMode == ProcessingMode.OIDN) {
                        oidnModelPickerLauncher.launch("*/*")
                    } else {
                        modelPickerLauncher.launch("*/*")
                    }
                },
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = {
                        Text(
                            if (BuildConfig.NATIVE_ENABLED && processingMode == ProcessingMode.OIDN) stringResource(
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(top = 8.dp, bottom = 16.dp)
        ) {
            PreferenceGroupHeading(stringResource(R.string.processing))

            if (BuildConfig.NATIVE_ENABLED) {
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
                        if (processingMode == ProcessingMode.OIDN) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                stringResource(R.string.mode_oidn_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        SingleChoiceSegmentedButtonRow(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            val haptic = rememberHapticFeedback()
                            SegmentedButton(
                                selected = processingMode == ProcessingMode.ONNX, onClick = {
                                    haptic.light()
                                    viewModel.setProcessingMode(ProcessingMode.ONNX)
                                }, shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                            ) {
                                Text(stringResource(R.string.mode_onnx))
                            }
                            SegmentedButton(
                                selected = processingMode == ProcessingMode.OIDN, onClick = {
                                    haptic.light()
                                    viewModel.setProcessingMode(ProcessingMode.OIDN)
                                }, shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
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
                        title = stringResource(R.string.model_management, ""),
                        subtitle = activeModelName ?: stringResource(R.string.no_model_loaded),
                        ellipsizeSubtitle = true,
                        showDivider = true,
                        expanded = expandedSection == SettingsSection.ModelManagement,
                        onClick = {
                            toggle(SettingsSection.ModelManagement)
                        })
                    AnimatedVisibility(visible = expandedSection == SettingsSection.ModelManagement) {
                        val haptic = rememberHapticFeedback()
                        val extractedMsg = stringResource(R.string.extracted_starter_models)
                        val failedMsg = stringResource(R.string.failed_to_extract_starter_models)
                        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                            installedModels.forEach { modelName ->
                                val isActive = modelName == activeModelName
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(
                                            if (isActive) MaterialTheme.colorScheme.primaryContainer.copy(
                                                alpha = 0.4f
                                            ) else Color.Transparent
                                        )
                                        .clickable {
                                            haptic.light()
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
                                            haptic.light(); modelInfoDialog = modelName to it
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
                                            haptic.heavy(); viewModel.deleteModels(
                                            listOf(
                                                modelName
                                            )
                                        ) {
                                            scope.launch {
                                                SnackySnackbarController.pushEvent(
                                                    SnackySnackbarEvents.MessageEvent(
                                                        message = deletedModelMessage.format(it),
                                                        duration = SnackbarDuration.Short
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
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Spacer(Modifier.weight(1f))
                                TextButton(onClick = {
                                    haptic.light()
                                    modelPickerLauncher.launch("*/*")
                                }) {
                                    Icon(
                                        Icons.Filled.Add, null, modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(stringResource(R.string.import_model_text))
                                }
                                TextButton(onClick = {
                                    haptic.light()
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
                                        Icons.Filled.Archive, null, modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(stringResource(R.string.extract))
                                }
                                TextButton(onClick = {
                                    haptic.light()
                                    val intent = Intent(
                                        Intent.ACTION_VIEW,
                                        "https://codeberg.org/dryerlint/dejpeg/src/branch/main/models".toUri()
                                    ); context.startActivity(intent)
                                }) {
                                    Icon(
                                        Icons.Filled.Download, null, modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(stringResource(R.string.download))
                                }
                            }
                        }
                    }
                    PreferenceItem(
                        icon = Icons.Filled.GridOn,
                        iconBackgroundColor = MaterialTheme.colorScheme.secondaryContainer,
                        iconTint = MaterialTheme.colorScheme.onSecondaryContainer,
                        title = stringResource(R.string.chunk_settings),
                        subtitle = stringResource(
                            R.string.chunk_size_px, chunkSize
                        ) + " • " + stringResource(R.string.overlap_size_px, overlapSize),
                        expanded = expandedSection == SettingsSection.Chunk,
                        onClick = { toggle(SettingsSection.Chunk) })
                    AnimatedVisibility(visible = expandedSection == SettingsSection.Chunk) {
                        val haptic = rememberHapticFeedback()
                        val maxThreads = remember {
                            Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
                        }
                        Column(Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
                            PowerSlider(
                                label = stringResource(R.string.chunk_size),
                                value = chunkSize,
                                powers = listOf(512, 1024, 2048),
                                onChange = { viewModel.setChunkSize(it) },
                                hapticAction = { haptic.light() })
                            Spacer(modifier = Modifier.height(8.dp))
                            PowerSlider(
                                label = stringResource(R.string.overlap_size),
                                value = overlapSize,
                                powers = listOf(16, 32, 64, 128),
                                onChange = { viewModel.setOverlapSize(it) },
                                hapticAction = { haptic.light() })
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                stringResource(R.string.processing_threads_desc),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Medium,
                            )
                            Slider(
                                value = onnxDeviceThreads.toFloat(),
                                onValueChange = { value ->
                                    haptic.light(); viewModel.setOnnxDeviceThreads(
                                    value.roundToInt()
                                )
                                },
                                valueRange = 0f..maxThreads.toFloat(),
                                steps = (maxThreads - 1).coerceAtLeast(0),
                                modifier = Modifier.fillMaxWidth()
                            )
                            Text(
                                threadLabel(
                                    onnxDeviceThreads,
                                    stringResource(R.string.thread_value_auto, maxThreads)
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.align(Alignment.End)
                            )
                        }
                    }
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
                        subtitle = activeOidnModelName ?: stringResource(R.string.no_model_loaded),
                        ellipsizeSubtitle = true,
                        showDivider = true,
                        expanded = expandedSection == SettingsSection.OidnModelManagement,
                        onClick = {
                            toggle(SettingsSection.OidnModelManagement)
                        })
                    AnimatedVisibility(visible = expandedSection == SettingsSection.OidnModelManagement) {
                        val haptic = rememberHapticFeedback()
                        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                            installedOidnModels.forEach { modelName ->
                                val isActive = modelName == activeOidnModelName
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable {
                                            haptic.light()
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
                                            haptic.heavy(); viewModel.deleteModels(
                                            listOf(
                                                modelName
                                            ), ModelType.OIDN
                                        ) {
                                            scope.launch {
                                                SnackySnackbarController.pushEvent(
                                                    SnackySnackbarEvents.MessageEvent(
                                                        message = deletedModelMessage.format(it),
                                                        duration = SnackbarDuration.Short
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
                                        Icons.Filled.Add, null, modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(stringResource(R.string.import_tza_model))
                                }
                            }
                        }
                    }
                    PreferenceItem(
                        icon = Icons.Filled.Settings,
                        iconBackgroundColor = MaterialTheme.colorScheme.secondaryContainer,
                        iconTint = MaterialTheme.colorScheme.onSecondaryContainer,
                        title = stringResource(R.string.oidn_settings),
                        subtitle = stringResource(R.string.oidn_settings_desc),
                        expanded = expandedSection == SettingsSection.OidnSettings,
                        onClick = {
                            toggle(SettingsSection.OidnSettings)
                        })

                    AnimatedVisibility(visible = expandedSection == SettingsSection.OidnSettings) {
                        val haptic = rememberHapticFeedback()
                        val maxThreads = remember {
                            Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
                        }

                        Column(Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
                            LabeledSwitch(
                                title = stringResource(R.string.oidn_hdr),
                                desc = stringResource(R.string.oidn_hdr_desc),
                                checked = oidnHdr,
                                onCheckedChange = { viewModel.setOidnHdrPref(it) },
                                hapticAction = { haptic.light() })
                            Spacer(modifier = Modifier.height(8.dp))
                            LabeledSwitch(
                                title = stringResource(R.string.oidn_srgb),
                                desc = stringResource(R.string.oidn_srgb_desc),
                                checked = oidnSrgb,
                                onCheckedChange = { viewModel.setOidnSrgbPref(it) },
                                hapticAction = { haptic.light() })
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
                                                haptic.light(); viewModel.setOidnQualityPref(
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

                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                stringResource(R.string.oidn_num_threads),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                stringResource(R.string.oidn_num_threads_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Slider(
                                value = oidnNumThreads.toFloat(),
                                onValueChange = {
                                    haptic.light(); viewModel.setOidnNumThreadsPref(
                                    it.roundToInt().coerceIn(0, maxThreads)
                                )
                                },
                                valueRange = 0f..maxThreads.toFloat(),
                                steps = (maxThreads - 1).coerceAtLeast(0),
                                modifier = Modifier.fillMaxWidth()
                            )
                            Text(
                                threadLabel(
                                    oidnNumThreads,
                                    stringResource(R.string.thread_value_auto, maxThreads)
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.align(Alignment.End)
                            )
                        }
                    }
                }
            }

            val isOidn = BuildConfig.NATIVE_ENABLED && processingMode == ProcessingMode.OIDN

            Box(modifier = Modifier
                .fillMaxWidth()
                .onSizeChanged { containerWidth = it.width }
                .clip(MaterialTheme.shapes.large)
                .pointerInput(processingMode) {
                    if (!BuildConfig.NATIVE_ENABLED) return@pointerInput
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

            PreferenceGroupHeading(stringResource(R.string.app))

            PreferenceGroupCard {
                PreferenceItem(
                    icon = Icons.Filled.Settings,
                    iconBackgroundColor = MaterialTheme.colorScheme.surfaceVariant,
                    iconTint = MaterialTheme.colorScheme.onSurfaceVariant,
                    title = stringResource(R.string.preferences),
                    subtitle = stringResource(R.string.preferences_desc),
                    showDivider = true,
                    expanded = expandedSection == SettingsSection.Preferences,
                    onClick = {
                        toggle(SettingsSection.Preferences)
                    })

                AnimatedVisibility(visible = expandedSection == SettingsSection.Preferences) {
                    Column(Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
                        val haptic = rememberHapticFeedback()
                        val localView = androidx.compose.ui.platform.LocalView.current
                        LabeledSwitch(
                            title = stringResource(R.string.vibration_on_touch),
                            desc = "",
                            checked = hapticFeedbackEnabled,
                            onCheckedChange = { new ->
                                if (new) {
                                    com.je.dejpeg.utils.HapticFeedback.light(
                                        localView, true
                                    )
                                }
                                scope.launch {
                                    appPreferences.setHapticFeedbackEnabled(new)
                                }
                            })
                        Spacer(modifier = Modifier.height(8.dp))

                        LabeledSwitch(
                            title = stringResource(R.string.show_save_dialog),
                            desc = "",
                            checked = showSaveDialog,
                            onCheckedChange = { new ->
                                scope.launch {
                                    appPreferences.setShowSaveDialog(new)
                                }
                            },
                            hapticAction = { haptic.light() })
                        Spacer(modifier = Modifier.height(8.dp))

                        LabeledSwitch(
                            title = stringResource(R.string.swap_swipe_actions),
                            desc = "",
                            checked = swapSwipeActions,
                            onCheckedChange = { new ->
                                scope.launch {
                                    appPreferences.setSwapSwipeActions(new)
                                }
                            },
                            hapticAction = { haptic.light() })
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
                                haptic.light()
                            }) { Text(stringResource(R.string.clear_default_source)) }
                        }
                    }
                }

                AnimatedVisibility(visible = processingMode == ProcessingMode.ONNX || !BuildConfig.NATIVE_ENABLED) {
                    Column {
                        PreferenceItem(
                            icon = Icons.Filled.QuestionAnswer,
                            iconBackgroundColor = MaterialTheme.colorScheme.primaryContainer,
                            iconTint = MaterialTheme.colorScheme.onPrimaryContainer,
                            title = stringResource(R.string.faqs),
                            subtitle = stringResource(R.string.show_frequently_asked_questions),
                            showDivider = true,
                            expanded = expandedSection == SettingsSection.FAQ,
                            onClick = {
                                toggle(SettingsSection.FAQ)
                            })
                        AnimatedVisibility(visible = expandedSection == SettingsSection.FAQ) {
                            val faqSections = remember { loadFAQSections(context) }
                            Column(Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
                                faqSections.forEach { section ->
                                    FAQSection(section.title, section.content, section.subSections)
                                }
                            }
                        }
                    }
                }

                PreferenceItem(
                    icon = Icons.Filled.Info,
                    iconBackgroundColor = MaterialTheme.colorScheme.primaryContainer,
                    iconTint = MaterialTheme.colorScheme.onPrimaryContainer,
                    title = stringResource(R.string.about),
                    subtitle = stringResource(R.string.version_info_and_credits),
                    trailing = { },
                    onClick = { showAbout = true })
            }

            Spacer(modifier = Modifier.height(100.dp))
        }
    }

    if (showImportProgress) {
        ModalBottomSheet(onDismissRequest = {
            showImportProgress = false; pendingImportUri = null
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
                    TextButton(onClick = { showImportProgress = false; pendingImportUri = null }) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            }
        }
    }

    if (showAbout) AboutDialog { showAbout = false }

    modelInfoDialog?.let { (modelName, infoText) ->
        ModalBottomSheet(onDismissRequest = { modelInfoDialog = null }) {
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

    warningState?.let { state ->
        val haptic = rememberHapticFeedback()
        when (state) {
            is ModelWarningState.ModelWarning -> {

                ModalBottomSheet(onDismissRequest = { warningState = null }) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            stringResource(state.warning.titleResId),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Model: ${state.modelName}",
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(stringResource(state.warning.messageResId))
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            TextButton(onClick = {
                                haptic.medium()
                                if (state.isImport) {
                                    pendingImportUri?.let { uri ->
                                        showImportProgress = true
                                        viewModel.importModel(
                                            uri,
                                            force = true,
                                            onProgress = { },
                                            onSuccess = { name ->
                                                showImportProgress = false
                                                warningState = null
                                                scope.launch {
                                                    SnackySnackbarController.pushEvent(
                                                        SnackySnackbarEvents.MessageEvent(
                                                            message = importedModelMessage.format(
                                                                name
                                                            ), duration = SnackbarDuration.Short
                                                        )
                                                    )
                                                }
                                            },
                                            onError = { err ->
                                                showImportProgress = false
                                                warningState = ModelWarningState.Error(err)
                                            })
                                    }
                                } else {
                                    pendingModelSelection?.let {
                                        viewModel.setActiveModelByName(it)
                                        activeModelName = it
                                    }
                                }
                            }) {
                                Text(stringResource(state.warning.positiveButtonTextResId))
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            TextButton(onClick = {
                                haptic.light()
                                warningState = null
                            }) {
                                Text(stringResource(state.warning.negativeButtonTextResId))
                            }
                        }
                    }
                }
            }

            is ModelWarningState.Error -> {
                ErrorAlertDialog(
                    title = stringResource(R.string.import_error),
                    errorMessage = state.message,
                    onDismiss = { warningState = null },
                    context = context
                )
            }
        }
    }
}

private enum class SettingsSection {
    Chunk, Preferences, OidnSettings, ModelManagement, OidnModelManagement, FAQ
}

sealed class ModelWarningState {
    data class ModelWarning(
        val modelName: String, val warning: ModelManager.ModelWarning, val isImport: Boolean
    ) : ModelWarningState()

    data class Error(val message: String) : ModelWarningState()
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
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = MaterialTheme.shapes.large,
        tonalElevation = 0.dp,
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column {
            content()
        }
    }
}

@Composable
fun PowerSlider(
    label: String,
    value: Int,
    powers: List<Int>,
    maxAllowed: Int = Int.MAX_VALUE,
    onChange: (Int) -> Unit,
    hapticAction: () -> Unit
) {
    val availablePowers = remember(powers, maxAllowed) { powers.filter { it < maxAllowed } }
    val effectivePowers = availablePowers.ifEmpty { listOf(powers.first()) }
    val clampedValue = value.coerceAtMost(effectivePowers.last())
    var index by remember(
        clampedValue, effectivePowers
    ) { mutableIntStateOf(effectivePowers.indexOf(clampedValue).coerceAtLeast(0)) }
    LaunchedEffect(maxAllowed) {
        if (value >= maxAllowed && effectivePowers.isNotEmpty()) {
            onChange(effectivePowers.last())
        }
    }
    Column {
        Text(label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
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
        Row {
            Text(
                "${effectivePowers[index]}px",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
fun LabeledSwitch(
    title: String,
    desc: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    hapticAction: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() }, indication = ripple()
            ) { hapticAction?.invoke(); onCheckedChange(!checked) }
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
        Switch(checked = checked, onCheckedChange = { hapticAction?.invoke(); onCheckedChange(it) })
    }
}

@Composable
fun PreferenceItem(
    icon: Any,
    iconBackgroundColor: Color,
    iconTint: Color,
    title: String,
    subtitle: String,
    ellipsizeSubtitle: Boolean = false,
    showDivider: Boolean = false,
    expanded: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
    onClick: () -> Unit
) {
    val haptic = rememberHapticFeedback()
    val interactionSource = remember { MutableInteractionSource() }
    val press by rememberMaterialPressState(interactionSource)
    val animatedIconCorner = lerp(14f, 22f, press)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(interactionSource = interactionSource, indication = ripple()) {
                haptic.light()
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

    if (showDivider) { // possibly redundant
        HorizontalDivider(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth(),
            thickness = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant
        )
    }
}

@Composable
fun FAQSection(title: String, content: String?, subSections: List<Pair<String, String>>? = null) {
    var expanded by remember { mutableStateOf(false) }
    val haptic = rememberHapticFeedback()
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 90f else 0f, animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium
        ), label = "chevron"
    )

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier
            .fillMaxWidth()
            .clickable { haptic.light(); expanded = !expanded }
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
        HorizontalDivider()
    }
}
