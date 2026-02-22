/**
* Copyright (C) 2025/2026 dryerlint <codeberg.org/dryerlint>
*
* This program is free software: you can redistribute it and/or modify
* it under the terms of the GNU General Public License as published by
* the Free Software Foundation, either version 3 of the License, or
* (at your option) any later version.
*
* This program is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
* GNU General Public License for more details.
*
* You should have received a copy of the GNU General Public License
* along with this program.  If not, see <https://www.gnu.org/licenses/>.
*/

/*
* If you use this code in your own project, please give credit
*/

package com.je.dejpeg.compose.ui.screens

import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.QuestionAnswer
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.je.dejpeg.R
import com.je.dejpeg.compose.ModelManager
import com.je.dejpeg.compose.ui.components.AboutDialog
import com.je.dejpeg.compose.ui.components.BaseDialog
import com.je.dejpeg.compose.ui.components.ChunkDialog
import com.je.dejpeg.compose.ui.components.DeleteDialog
import com.je.dejpeg.compose.ui.components.FAQDialog
import com.je.dejpeg.compose.ui.components.ImportProgressDialog
import com.je.dejpeg.compose.ui.components.ModelDialog
import com.je.dejpeg.compose.ui.components.ModelInfoDialog
import com.je.dejpeg.compose.ui.components.OidnSettingsDialog
import com.je.dejpeg.compose.ui.components.PreferencesDialog
import com.je.dejpeg.compose.ui.viewmodel.ProcessingViewModel
import com.je.dejpeg.data.ProcessingMode
import com.je.dejpeg.BuildConfig
import com.je.dejpeg.compose.utils.rememberHapticFeedback
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: ProcessingViewModel) {
    val context = LocalContext.current
    val modelManager = remember { ModelManager(context) }
    val appPreferences = remember { com.je.dejpeg.data.AppPreferences(context) }
    val scope = rememberCoroutineScope()
    val installedModels by viewModel.installedModels.collectAsState()
    var dialogState by remember { mutableStateOf<DialogState>(DialogState.None) }
    var importProgress by remember { mutableIntStateOf(0) }
    val importedModelMessage = stringResource(R.string.imported_model)
    val deletedModelMessage = stringResource(R.string.deleted_model)
    var pendingImportUri by remember { mutableStateOf<Uri?>(null) }
    val chunkSize by viewModel.chunkSize.collectAsState()
    val overlapSize by viewModel.overlapSize.collectAsState()
    var activeModelName by remember { 
        mutableStateOf(runBlocking { viewModel.getActiveModelName() })
    }
    var pendingModelSelection by remember { mutableStateOf<String?>(null) }
    var warningState by remember { mutableStateOf<ModelWarningState?>(null) }
    val skipSaveDialog by appPreferences.skipSaveDialog.collectAsState(initial = false)
    val defaultImageSource by appPreferences.defaultImageSource.collectAsState(initial = null)
    val hapticFeedbackEnabled by appPreferences.hapticFeedbackEnabled.collectAsState(initial = true)
    val swapSwipeActions by appPreferences.swapSwipeActions.collectAsState(initial = false)
    var modelInfoDialog by remember { mutableStateOf<Pair<String, String>?>(null) }

    val processingMode by viewModel.processingMode.collectAsState()
    val installedOidnModels by viewModel.installedOidnModels.collectAsState()
    val oidnHdr by viewModel.oidnHdr.collectAsState()
    val oidnSrgb by viewModel.oidnSrgb.collectAsState()
    val oidnQuality by viewModel.oidnQuality.collectAsState()
    val oidnMaxMemoryMB by viewModel.oidnMaxMemoryMB.collectAsState()
    val oidnNumThreads by viewModel.oidnNumThreads.collectAsState()
    var activeOidnModelName by remember {
        mutableStateOf(runBlocking { viewModel.getActiveOidnModelName() })
    }

    DisposableEffect(Unit) {
        onDispose {
            dialogState = DialogState.None
            warningState = null
            pendingModelSelection = null
            pendingImportUri = null
        }
    }

    LaunchedEffect(installedModels) {
        activeModelName = withContext(Dispatchers.IO) { viewModel.getActiveModelName() }
    }

    LaunchedEffect(installedOidnModels) {
        activeOidnModelName = withContext(Dispatchers.IO) { viewModel.getActiveOidnModelName() }
    }

    val modelPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { it ->
            pendingImportUri = it
            dialogState = DialogState.ImportProgress
            importProgress = 0
            viewModel.importModel(
                it,
                onProgress = { importProgress = it },
                onSuccess = { name ->
                    dialogState = DialogState.None
                    pendingImportUri = null
                    Toast.makeText(
                        context,
                        importedModelMessage.format(name),
                        Toast.LENGTH_SHORT
                    ).show()
                },
                onError = { err ->
                    dialogState = DialogState.None
                    pendingImportUri = null
                    warningState = ModelWarningState.Error(err)
                },
                onWarning = { modelName, warning ->
                    dialogState = DialogState.None
                    warningState = ModelWarningState.ModelWarning(modelName, warning, isImport = true)
                }
            )
        }
    }

    var pendingOidnImportUri by remember { mutableStateOf<Uri?>(null) }

    val oidnModelPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { selectedUri ->
            pendingOidnImportUri = selectedUri
            dialogState = DialogState.ImportProgress
            importProgress = 0
            viewModel.importOidnModel(
                selectedUri,
                onProgress = { importProgress = it },
                onSuccess = { name ->
                    dialogState = DialogState.None
                    pendingOidnImportUri = null
                    Toast.makeText(
                        context,
                        importedModelMessage.format(name),
                        Toast.LENGTH_SHORT
                    ).show()
                },
                onError = { err ->
                    dialogState = DialogState.None
                    pendingOidnImportUri = null
                    warningState = ModelWarningState.Error(err)
                },
                onWarning = { modelName, warning ->
                    dialogState = DialogState.None
                    warningState = ModelWarningState.OidnModelWarning(modelName, warning)
                }
            )
        }
    }

    Scaffold(
        floatingActionButton = {
            val haptic = rememberHapticFeedback()
            ExtendedFloatingActionButton(
                onClick = {
                    haptic.light()
                    if (BuildConfig.OIDN_ENABLED && processingMode == ProcessingMode.OIDN) {
                        oidnModelPickerLauncher.launch("*/*")
                    } else {
                        modelPickerLauncher.launch("*/*")
                    }
                },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = {
                    Text(
                        if (BuildConfig.OIDN_ENABLED && processingMode == ProcessingMode.OIDN)
                            stringResource(R.string.import_tza_model)
                        else
                            stringResource(R.string.import_model_text)
                    )
                },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                expanded = true
            )
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
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
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            if (processingMode == ProcessingMode.OIDN)
                                stringResource(R.string.mode_oidn_desc)
                            else
                                stringResource(R.string.mode_onnx_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        SingleChoiceSegmentedButtonRow(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            SegmentedButton(
                                selected = processingMode == ProcessingMode.ONNX,
                                onClick = { viewModel.setProcessingMode(ProcessingMode.ONNX) },
                                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                            ) {
                                Text(stringResource(R.string.mode_onnx))
                            }
                            SegmentedButton(
                                selected = processingMode == ProcessingMode.OIDN,
                                onClick = { viewModel.setProcessingMode(ProcessingMode.OIDN) },
                                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                            ) {
                                Text(stringResource(R.string.mode_oidn))
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
            }

            if (processingMode == ProcessingMode.ONNX || !BuildConfig.OIDN_ENABLED) {
                PreferenceGroupCard {
                    PreferenceItemWithDivider(
                        icon = painterResource(id = R.drawable.ic_model),
                        iconBackgroundColor = MaterialTheme.colorScheme.surfaceVariant,
                        iconTint = MaterialTheme.colorScheme.onSurfaceVariant,
                        title = stringResource(R.string.model_management, ""),
                        subtitle = activeModelName ?: stringResource(R.string.no_model_loaded),
                        ellipsizeSubtitle = true,
                        showDivider = true,
                        onClick = { dialogState = DialogState.Model }
                    )

                    PreferenceItem(
                        icon = Icons.Filled.GridOn,
                        iconBackgroundColor = MaterialTheme.colorScheme.secondaryContainer,
                        iconTint = MaterialTheme.colorScheme.onSecondaryContainer,
                        title = stringResource(R.string.chunk_settings),
                        subtitle = stringResource(R.string.chunk_size_px, chunkSize) + " • " + 
                                  stringResource(R.string.overlap_size_px, overlapSize),
                        onClick = { dialogState = DialogState.Chunk }
                    )
                }
            } else {
                PreferenceGroupCard {
                    PreferenceItemWithDivider(
                        icon = painterResource(id = R.drawable.ic_model),
                        iconBackgroundColor = MaterialTheme.colorScheme.tertiaryContainer,
                        iconTint = MaterialTheme.colorScheme.onTertiaryContainer,
                        title = stringResource(R.string.oidn_model_management),
                        subtitle = activeOidnModelName ?: stringResource(R.string.oidn_no_model_loaded),
                        ellipsizeSubtitle = true,
                        showDivider = true,
                        onClick = { dialogState = DialogState.OidnModel }
                    )

                    PreferenceItem(
                        icon = Icons.Filled.Tune,
                        iconBackgroundColor = MaterialTheme.colorScheme.tertiaryContainer,
                        iconTint = MaterialTheme.colorScheme.onTertiaryContainer,
                        title = stringResource(R.string.oidn_settings),
                        subtitle = stringResource(R.string.oidn_settings_desc),
                        onClick = { dialogState = DialogState.OidnSettings }
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            PreferenceGroupHeading(stringResource(R.string.app))

            PreferenceGroupCard {
                PreferenceItemWithDivider(
                    icon = Icons.Filled.Settings,
                    iconBackgroundColor = MaterialTheme.colorScheme.surfaceVariant,
                    iconTint = MaterialTheme.colorScheme.onSurfaceVariant,
                    title = stringResource(R.string.preferences),
                    subtitle = stringResource(R.string.preferences_desc),
                    showDivider = true,
                    onClick = { dialogState = DialogState.Preferences }
                )

                if (processingMode == ProcessingMode.ONNX || !BuildConfig.OIDN_ENABLED) {
                    PreferenceItemWithDivider(
                        icon = Icons.Filled.QuestionAnswer,
                        iconBackgroundColor = MaterialTheme.colorScheme.primaryContainer,
                        iconTint = MaterialTheme.colorScheme.onPrimaryContainer,
                        title = stringResource(R.string.faqs),
                        subtitle = stringResource(R.string.show_frequently_asked_questions),
                        showDivider = true,
                        onClick = { dialogState = DialogState.FAQ }
                    )
                }

                PreferenceItem(
                    icon = Icons.Filled.Info,
                    iconBackgroundColor = MaterialTheme.colorScheme.primaryContainer,
                    iconTint = MaterialTheme.colorScheme.onPrimaryContainer,
                    title = stringResource(R.string.about),
                    subtitle = stringResource(R.string.version_info_and_credits),
                    onClick = { dialogState = DialogState.About }
                )
            }

            Spacer(modifier = Modifier.height(96.dp))
        }
    }

    when (dialogState) {
        DialogState.Model -> ModelDialog(
            installedModels,
            activeModelName,
            modelManager,
            onSelect = { modelName ->
                Log.d("SettingsScreen", "onSelect called for model: $modelName")
                val warning = modelManager.getModelWarning(modelName)
                Log.d("SettingsScreen", "Model warning: $warning")
                if (warning != null) {
                    Log.d("SettingsScreen", "Setting pendingModelSelection to: $modelName")
                    pendingModelSelection = modelName
                    warningState = ModelWarningState.ModelWarning(modelName, warning, isImport = false)
                } else {
                    Log.d("SettingsScreen", "No warning, setting active model directly")
                    viewModel.setActiveModelByName(modelName)
                    activeModelName = modelName
                }
            },
            onImport = { modelPickerLauncher.launch("*/*") },
            onDelete = { dialogState = DialogState.Delete },
            onDismiss = { dialogState = DialogState.None },
            onShowInfo = { modelName ->
                modelManager.getModelInfo(modelName)?.let { info ->
                    modelInfoDialog = modelName to info
                }
            }
        )
        DialogState.Delete -> DeleteDialog(
            installedModels,
            onConfirm = { selected ->
                dialogState = DialogState.None
                viewModel.deleteModels(selected) {
                    Toast.makeText(
                        context,
                        deletedModelMessage.format(it),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            },
            onDismiss = { dialogState = DialogState.Model }
        )
        DialogState.ImportProgress -> ImportProgressDialog(importProgress)
        DialogState.Chunk -> ChunkDialog(
            chunkSize,
            overlapSize,
            onChunkChange = { viewModel.setChunkSize(it) },
            onOverlapChange = { viewModel.setOverlapSize(it) },
            onDismiss = { dialogState = DialogState.None }
        )
        DialogState.Preferences -> PreferencesDialog(
            context = context,
            skipSaveDialog = skipSaveDialog,
            defaultImageSource = defaultImageSource,
            hapticFeedbackEnabled = hapticFeedbackEnabled,
            swapSwipeActions = swapSwipeActions,
            onSkipSaveDialogChange = { skip ->
                scope.launch { appPreferences.setSkipSaveDialog(skip) }
            },
            onDefaultImageSourceChange = { source ->
                scope.launch { appPreferences.setDefaultImageSource(source) }
            },
            onHapticFeedbackChange = { enabled ->
                scope.launch { appPreferences.setHapticFeedbackEnabled(enabled) }
            },
            onSwapSwipeActionsChange = { swap ->
                scope.launch { appPreferences.setSwapSwipeActions(swap) }
            },
            onDismiss = { dialogState = DialogState.None },
            onModelExtracted = { 
                viewModel.refreshInstalledModels()
                activeModelName = viewModel.getActiveModelName()
            }
        )
        DialogState.About -> AboutDialog { dialogState = DialogState.None }
        DialogState.FAQ -> FAQDialog { dialogState = DialogState.None }
        is DialogState.ModelInfo -> {
            val state = dialogState as DialogState.ModelInfo
            ModelInfoDialog(
                modelName = state.modelName,
                infoText = state.infoText,
                onDismiss = { dialogState = DialogState.None }
            )
        }
        DialogState.OidnModel -> OidnModelDialog(
            installedOidnModels,
            activeOidnModelName,
            onSelect = { modelName ->
                viewModel.setActiveOidnModelByName(modelName)
                activeOidnModelName = modelName
            },
            onImport = { oidnModelPickerLauncher.launch("*/*") },
            onDelete = { dialogState = DialogState.OidnDelete },
            onDismiss = { dialogState = DialogState.None }
        )
        DialogState.OidnDelete -> DeleteDialog(
            installedOidnModels,
            onConfirm = { selected ->
                dialogState = DialogState.None
                viewModel.deleteOidnModels(selected) {
                    Toast.makeText(
                        context,
                        deletedModelMessage.format(it),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            },
            onDismiss = { dialogState = DialogState.OidnModel }
        )
        DialogState.OidnSettings -> OidnSettingsDialog(
            hdr = oidnHdr,
            srgb = oidnSrgb,
            quality = oidnQuality,
            maxMemoryMB = oidnMaxMemoryMB,
            numThreads = oidnNumThreads,
            onHdrChange = { viewModel.setOidnHdrPref(it) },
            onSrgbChange = { viewModel.setOidnSrgbPref(it) },
            onQualityChange = { viewModel.setOidnQualityPref(it) },
            onMaxMemoryChange = { viewModel.setOidnMaxMemoryMBPref(it) },
            onNumThreadsChange = { viewModel.setOidnNumThreadsPref(it) },
            onDismiss = { dialogState = DialogState.None }
        )
        DialogState.None -> {}
    }

    modelInfoDialog?.let { (modelName, infoText) ->
        ModelInfoDialog(
            modelName = modelName,
            infoText = infoText,
            onDismiss = { modelInfoDialog = null }
        )
    }

    warningState?.let { state ->
        val haptic = rememberHapticFeedback()
        when (state) {
            is ModelWarningState.ModelWarning -> {
                val context = LocalContext.current

                AlertDialog(
                    onDismissRequest = { warningState = null },
                    shape = RoundedCornerShape(28.dp),
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    title = { Text(stringResource(state.warning.titleResId)) },
                    text = {
                        Column {
                            Text(
                                "Model: ${state.modelName}",
                                fontWeight = FontWeight.Bold,
                                maxLines = 2,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(stringResource(state.warning.messageResId))
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            haptic.medium()
                            if (state.isImport) {
                                pendingImportUri?.let { uri ->
                                    dialogState = DialogState.ImportProgress
                                    viewModel.importModel(
                                        uri,
                                        force = true,
                                        onProgress = { },
                                        onSuccess = { name ->
                                            dialogState = DialogState.None
                                            warningState = null
                                            Toast.makeText(
                                                context,
                                                importedModelMessage.format(name),
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        },
                                        onError = { err ->
                                            dialogState = DialogState.None
                                            warningState = ModelWarningState.Error(err)
                                        }
                                    )
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
                    },
                    dismissButton = {
                        TextButton(onClick = {
                            haptic.light()
                            warningState = null
                        }) {
                            Text(stringResource(state.warning.negativeButtonTextResId))
                        }
                    }
                )
            }

            is ModelWarningState.OidnModelWarning -> {
                BaseDialog(
                    title = stringResource(state.warning.titleResId),
                    content = {
                        Column {
                            Text(
                                "Model: ${state.modelName}",
                                fontWeight = FontWeight.Bold,
                                maxLines = 2,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(stringResource(state.warning.messageResId))
                        }
                    },
                    onDismiss = { warningState = null; pendingOidnImportUri = null },
                    confirmButtonText = stringResource(state.warning.positiveButtonTextResId),
                    onConfirm = {
                        pendingOidnImportUri?.let { uri ->
                            dialogState = DialogState.ImportProgress
                            viewModel.importOidnModel(
                                uri,
                                force = true,
                                onProgress = { importProgress = it },
                                onSuccess = { name ->
                                    dialogState = DialogState.None
                                    warningState = null
                                    pendingOidnImportUri = null
                                    Toast.makeText(
                                        context,
                                        importedModelMessage.format(name),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                },
                                onError = { err ->
                                    dialogState = DialogState.None
                                    pendingOidnImportUri = null
                                    warningState = ModelWarningState.Error(err)
                                }
                            )
                        }
                    },
                    dismissButtonText = stringResource(state.warning.negativeButtonTextResId),
                    onDismissButton = { warningState = null; pendingOidnImportUri = null }
                )
            }

            is ModelWarningState.Error -> {
                BaseDialog(
                    title = stringResource(R.string.import_error),
                    message = state.message,
                    onDismiss = { warningState = null },
                    confirmButtonText = stringResource(R.string.ok),
                    onConfirm = { warningState = null }
                )
            }
        }
    }
}

sealed class DialogState {
    object None : DialogState()
    object Model : DialogState()
    object Delete : DialogState()
    object ImportProgress : DialogState()
    object Chunk : DialogState()
    object About : DialogState()
    object FAQ : DialogState()
    object Preferences : DialogState()
    object OidnModel : DialogState()
    object OidnDelete : DialogState()
    object OidnSettings : DialogState()
    data class ModelInfo(val modelName: String, val infoText: String) : DialogState()
}

sealed class ModelWarningState {
    data class ModelWarning(
        val modelName: String,
        val warning: ModelManager.ModelWarning,
        val isImport: Boolean
    ) : ModelWarningState()
    data class OidnModelWarning(
        val modelName: String,
        val warning: ModelManager.ModelWarning
    ) : ModelWarningState()
    data class Error(val message: String) : ModelWarningState()
}

@Composable
fun PreferenceGroupHeading(title: String, modifier: Modifier = Modifier) {
    Column(
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .height(48.dp)
            .padding(horizontal = 32.dp)
            .fillMaxWidth()
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
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
        tonalElevation = 1.dp,
        color = MaterialTheme.colorScheme.surface
    ) {
        Column {
            content()
        }
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
    onClick: () -> Unit
) {
    val haptic = rememberHapticFeedback()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                haptic.light()
                onClick()
            }
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(48.dp)
                .background(iconBackgroundColor, RoundedCornerShape(14.dp))
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

        Icon(
            Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.size(24.dp)
        )
    }
}

@Composable
fun PreferenceItemWithDivider(
    icon: Any,
    iconBackgroundColor: Color,
    iconTint: Color,
    title: String,
    subtitle: String,
    ellipsizeSubtitle: Boolean = false,
    showDivider: Boolean = true,
    onClick: () -> Unit
) {
    Column {
        PreferenceItem(
            icon = icon,
            iconBackgroundColor = iconBackgroundColor,
            iconTint = iconTint,
            title = title,
            subtitle = subtitle,
            ellipsizeSubtitle = ellipsizeSubtitle,
            onClick = onClick
        )
        if (showDivider) {
            HorizontalDivider(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth(), thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant
            )
        }
    }
}
