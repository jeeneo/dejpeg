package com.je.dejpeg.compose.ui.screens

import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import com.je.dejpeg.compose.ModelManager
import com.je.dejpeg.compose.ui.viewmodel.ProcessingViewModel
import com.je.dejpeg.R
import com.je.dejpeg.compose.ui.components.ModelDialog
import com.je.dejpeg.compose.ui.components.DeleteDialog
import com.je.dejpeg.compose.ui.components.ImportProgressDialog
import com.je.dejpeg.compose.ui.components.ChunkDialog
import com.je.dejpeg.compose.ui.components.PreferencesDialog
import com.je.dejpeg.compose.ui.components.AboutDialog
import com.je.dejpeg.compose.ui.components.FAQDialog
import com.je.dejpeg.compose.ui.components.BaseDialog
import com.je.dejpeg.compose.ui.components.ModelInfoDialog
import com.je.dejpeg.compose.utils.rememberHapticFeedback
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
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
    var activeModelName by remember { mutableStateOf<String?>(null) }
    var pendingModelSelection by remember { mutableStateOf<String?>(null) }
    var warningState by remember { mutableStateOf<ModelWarningState?>(null) }
    val skipSaveDialog by appPreferences.skipSaveDialog.collectAsState(initial = false)
    val defaultImageSource by appPreferences.defaultImageSource.collectAsState(initial = null)
    val hapticFeedbackEnabled by appPreferences.hapticFeedbackEnabled.collectAsState(initial = true)
    val swapSwipeActions by appPreferences.swapSwipeActions.collectAsState(initial = false)
    var modelInfoDialog by remember { mutableStateOf<Pair<String, String>?>(null) }

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

    Scaffold(
        floatingActionButton = {
            val haptic = rememberHapticFeedback()
            ExtendedFloatingActionButton(
                onClick = {
                    haptic.light()
                    modelPickerLauncher.launch("*/*")
                },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.import_model_text)) },
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
        ) {
            Spacer(modifier = Modifier.height(20.dp))

            SettingsSectionCard(stringResource(R.string.processing)) {
                ModernSettingsItem(
                    icon = painterResource(id = R.drawable.ic_model),
                    iconBackgroundColor = MaterialTheme.colorScheme.surfaceVariant,
                    iconTint = MaterialTheme.colorScheme.onSurfaceVariant,
                    title = stringResource(R.string.model_management, ""),
                    subtitle = activeModelName ?: stringResource(R.string.no_model_loaded),
                    ellipsizeSubtitle = true
                ) { dialogState = DialogState.Model }

                Spacer(modifier = Modifier.height(8.dp))

                ModernSettingsItem(
                    icon = Icons.Filled.GridOn,
                    iconBackgroundColor = MaterialTheme.colorScheme.secondaryContainer,
                    iconTint = MaterialTheme.colorScheme.onSecondaryContainer,
                    title = stringResource(R.string.chunk_settings),
                    subtitle = stringResource(R.string.chunk_size_px, chunkSize) + " • " + 
                              stringResource(R.string.overlap_size_px, overlapSize)
                ) { dialogState = DialogState.Chunk }
            }

            SettingsSectionCard(stringResource(R.string.app)) {
                ModernSettingsItem(
                    icon = Icons.Filled.Settings,
                    iconBackgroundColor = MaterialTheme.colorScheme.surfaceVariant,
                    iconTint = MaterialTheme.colorScheme.onSurfaceVariant,
                    title = stringResource(R.string.preferences),
                    subtitle = stringResource(R.string.preferences_desc)
                ) { dialogState = DialogState.Preferences }

                Spacer(modifier = Modifier.height(8.dp))

                ModernSettingsItem(
                    icon = Icons.Filled.QuestionAnswer,
                    iconBackgroundColor = MaterialTheme.colorScheme.primaryContainer,
                    iconTint = MaterialTheme.colorScheme.onPrimaryContainer,
                    title = stringResource(R.string.faqs),
                    subtitle = stringResource(R.string.show_frequently_asked_questions)
                ) { dialogState = DialogState.FAQ }

                Spacer(modifier = Modifier.height(8.dp))

                ModernSettingsItem(
                    icon = Icons.Filled.Info,
                    iconBackgroundColor = MaterialTheme.colorScheme.primaryContainer,
                    iconTint = MaterialTheme.colorScheme.onPrimaryContainer,
                    title = stringResource(R.string.about),
                    subtitle = stringResource(R.string.version_info_and_credits)
                ) { dialogState = DialogState.About }
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
    data class ModelInfo(val modelName: String, val infoText: String) : DialogState()
}

sealed class ModelWarningState {
    data class ModelWarning(
        val modelName: String,
        val warning: ModelManager.ModelWarning,
        val isImport: Boolean
    ) : ModelWarningState()
    data class Error(val message: String) : ModelWarningState()
}

@Composable
fun SettingsSectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 12.dp)
        )
        content()
    }
}

@Composable
fun ModernSettingsItem(
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
            .clip(RoundedCornerShape(20.dp))
            .clickable { 
                haptic.light()
                onClick() 
            }
            .padding(horizontal = 16.dp, vertical = 14.dp),
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
fun SettingsItem(title: String, subtitle: String, onClick: () -> Unit) {
    val haptic = rememberHapticFeedback()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { haptic.light(); onClick() }
            .padding(horizontal = 12.dp, vertical = 10.dp),
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