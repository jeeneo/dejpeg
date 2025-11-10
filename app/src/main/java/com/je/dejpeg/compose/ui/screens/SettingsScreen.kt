package com.je.dejpeg.compose.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import com.je.dejpeg.ModelManager
import com.je.dejpeg.compose.ui.viewmodel.ProcessingViewModel
import com.je.dejpeg.R
import com.je.dejpeg.compose.ui.components.DownloadModelDialog
import com.je.dejpeg.compose.ui.components.ModelDialog
import com.je.dejpeg.compose.ui.components.DeleteDialog
import com.je.dejpeg.compose.ui.components.ImportProgressDialog
import com.je.dejpeg.compose.ui.components.ChunkDialog
import com.je.dejpeg.compose.ui.components.PreferencesDialog
import com.je.dejpeg.compose.ui.components.AboutDialog
import com.je.dejpeg.compose.ui.components.FAQDialog
import com.je.dejpeg.compose.utils.rememberHapticFeedback

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: ProcessingViewModel) {
    val context = LocalContext.current
    val installedModels by viewModel.installedModels.collectAsState()
    var dialogState by remember { mutableStateOf<DialogState>(DialogState.None) }
    var importProgress by remember { mutableIntStateOf(0) }
    var pendingImportUri by remember { mutableStateOf<Uri?>(null) }
    val chunkSize by viewModel.chunkSize.collectAsState()
    val overlapSize by viewModel.overlapSize.collectAsState()
    var activeModelName by remember { mutableStateOf(viewModel.getActiveModelName()) }
    var pendingModelSelection by remember { mutableStateOf<String?>(null) }
    var warningState by remember { mutableStateOf<ModelWarningState?>(null) }
    LaunchedEffect(installedModels, dialogState) { activeModelName = viewModel.getActiveModelName() }
    val modelPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { it ->
            pendingImportUri = it
            dialogState = DialogState.ImportProgress
            importProgress = 0
            viewModel.importModel(context, it,
                onProgress = { importProgress = it },
                onSuccess = { name ->
                    dialogState = DialogState.None
                    pendingImportUri = null
                    Toast.makeText(context, context.getString(R.string.imported_model, name), Toast.LENGTH_SHORT).show()
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

    Scaffold(
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { modelPickerLauncher.launch("*/*") },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.import_model_text)) },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            )
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            SettingsSectionCard(stringResource(R.string.model_management)) { 
                SettingsItem(stringResource(R.string.active_model, ""), activeModelName ?: stringResource(R.string.no_model_loaded)) { dialogState = DialogState.Model } 
            }
            SettingsSectionCard(stringResource(R.string.processing)) {
                SettingsItem(
                    stringResource(R.string.chunk_settings), 
                    stringResource(R.string.chunk_size_px, chunkSize) + " • " + stringResource(R.string.overlap_size_px, overlapSize)
                ) { dialogState = DialogState.Chunk }
            }
            SettingsSectionCard(stringResource(R.string.app)) {
                SettingsItem(stringResource(R.string.preferences), stringResource(R.string.manage_save_dialog_and_source)) { dialogState = DialogState.Preferences }
                SettingsItem(stringResource(R.string.about), stringResource(R.string.version_info_and_credits)) { dialogState = DialogState.About }
                SettingsItem(stringResource(R.string.faqs), stringResource(R.string.show_frequently_asked_questions)) { dialogState = DialogState.FAQ }
            }
            Spacer(modifier = Modifier.height(88.dp))
        }
    }

    when (dialogState) {
        DialogState.Model -> ModelDialog(installedModels, activeModelName, viewModel,
            onSelect = { modelName ->
                val warning = viewModel.getModelWarning(modelName)
                if (warning != null) {
                    pendingModelSelection = modelName
                    warningState = ModelWarningState.ModelWarning(modelName, warning, isImport = false)
                } else {
                    viewModel.setActiveModelByName(modelName)
                    activeModelName = modelName
                }
            },
            onImport = { modelPickerLauncher.launch("*/*") },
            onDelete = { dialogState = DialogState.Delete },
            onDownload = { dialogState = DialogState.Download },
            onDismiss = { dialogState = DialogState.None })
        DialogState.Delete -> DeleteDialog(installedModels,
            onConfirm = { selected -> dialogState = DialogState.None; viewModel.deleteModels(selected) { Toast.makeText(context, context.getString(R.string.deleted_model, it), Toast.LENGTH_SHORT).show() } },
            onDismiss = { dialogState = DialogState.None })
        DialogState.ImportProgress -> ImportProgressDialog(importProgress)
        DialogState.Chunk -> ChunkDialog(chunkSize, overlapSize,
            onChunkChange = { viewModel.setChunkSize(it) }, 
            onOverlapChange = { viewModel.setOverlapSize(it) },
            onDismiss = { dialogState = DialogState.None })
        DialogState.Preferences -> PreferencesDialog(context, onDismiss = { dialogState = DialogState.None })
        DialogState.About -> AboutDialog { dialogState = DialogState.None }
        DialogState.FAQ -> FAQDialog { dialogState = DialogState.None }
        DialogState.Download -> DownloadModelDialog(onDismiss = { dialogState = DialogState.Model })
        DialogState.None -> {}
    }

    warningState?.let { state ->
        when (state) {
            is ModelWarningState.ModelWarning -> {
                val context = LocalContext.current
                
                AlertDialog(
                    onDismissRequest = { 
                        warningState = null
                        pendingModelSelection = null
                        pendingImportUri = null
                    },
                    shape = RoundedCornerShape(28.dp),
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    title = { Text(stringResource(state.warning.titleResId)) },
                    text = { 
                        Column {
                            Text("Model: ${state.modelName}", fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(stringResource(state.warning.messageResId))
                        }
                    },
                    confirmButton = { 
                        TextButton(onClick = { 
                            warningState = null
                            pendingModelSelection = null
                            
                            if (state.isImport) {
                                pendingImportUri?.let { uri ->
                                    dialogState = DialogState.ImportProgress
                                    viewModel.importModel(context, uri, force = true,
                                        onProgress = { importProgress = it },
                                        onSuccess = { name -> 
                                            dialogState = DialogState.None
                                            pendingImportUri = null
                                            Toast.makeText(context, context.getString(R.string.imported_model, name), Toast.LENGTH_SHORT).show()
                                        },
                                        onError = { err -> 
                                            dialogState = DialogState.None
                                            pendingImportUri = null
                                            warningState = ModelWarningState.Error(err)
                                        }
                                    )
                                }
                            } else {
                                pendingModelSelection?.let { 
                                    viewModel.setActiveModelByName(it)
                                    activeModelName = it
                                }
                                pendingImportUri = null
                            }
                        }) { 
                            Text(stringResource(state.warning.positiveButtonTextResId)) 
                        } 
                    },
                    dismissButton = { 
                        TextButton(onClick = { 
                            warningState = null
                            pendingModelSelection = null
                            pendingImportUri = null
                        }) { 
                            Text(stringResource(state.warning.negativeButtonTextResId)) 
                        } 
                    }
                )
            }
            is ModelWarningState.Error -> {
                AlertDialog(
                    onDismissRequest = { warningState = null }, 
                    shape = RoundedCornerShape(28.dp),
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    title = { Text(stringResource(R.string.import_error)) }, 
                    text = { Text(state.message) }, 
                    confirmButton = { TextButton(onClick = { warningState = null }) { Text(stringResource(R.string.ok)) } }
                )
            }
        }
    }
}

sealed class DialogState { object None : DialogState(); object Model : DialogState(); object Delete : DialogState(); object ImportProgress : DialogState(); object Chunk : DialogState(); object About : DialogState(); object FAQ : DialogState(); object Preferences : DialogState(); object Download : DialogState() }

sealed class ModelWarningState {
    data class ModelWarning(
        val modelName: String,
        val warning: ModelManager.ModelWarning,
        val isImport: Boolean
    ) : ModelWarningState()
    
    data class Error(val message: String) : ModelWarningState()
}

@Composable fun SettingsSectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            content()
        }
    }
}

@Composable fun SettingsItem(title: String, subtitle: String, onClick: () -> Unit) {
    val haptic = rememberHapticFeedback()
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = { haptic.light(); onClick() }),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp), 
            horizontalArrangement = Arrangement.SpaceBetween, 
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title, 
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    subtitle, 
                    style = MaterialTheme.typography.bodySmall, 
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                Icons.Filled.ChevronRight, 
                contentDescription = null, 
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}