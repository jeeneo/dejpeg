package com.je.dejpeg.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.foundation.text.ClickableText
import android.content.Intent
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.je.dejpeg.ui.viewmodel.ProcessingViewModel
import com.je.dejpeg.R
import androidx.core.content.edit
import androidx.core.net.toUri
import kotlin.math.pow
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: ProcessingViewModel) {
    val context = LocalContext.current
    val installedModels by viewModel.installedModels.collectAsState()
    var dialogState by remember { mutableStateOf<DialogState>(DialogState.None) }
    var importProgress by remember { mutableIntStateOf(0) }
    var pendingImportUri by remember { mutableStateOf<android.net.Uri?>(null) }
    val chunkSize by viewModel.chunkSize.collectAsState()
    val overlapSize by viewModel.overlapSize.collectAsState()
    var activeModelName by remember { mutableStateOf(viewModel.getActiveModelName()) }
    var pendingModelSelection by remember { mutableStateOf<String?>(null) }
    var warningState by remember { mutableStateOf<ModelWarningState?>(null) }
    LaunchedEffect(installedModels, dialogState) { activeModelName = viewModel.getActiveModelName() }
    val modelPickerLauncher = rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.GetContent()
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
                    android.widget.Toast.makeText(context, context.getString(R.string.imported_model, name), android.widget.Toast.LENGTH_SHORT).show()
                },
                onError = { err -> 
                    dialogState = DialogState.None
                    warningState = parseWarningError(err, context, isImport = true)
                }
            )
        }
    }

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        SettingsSection(stringResource(R.string.model_management)) { SettingsItem(stringResource(R.string.active_model, ""), activeModelName ?: stringResource(R.string.no_model_loaded)) { dialogState = DialogState.Model } }
        SettingsSection(stringResource(R.string.processing)) {
            SettingsItem(stringResource(R.string.chunk_size), stringResource(R.string.chunk_size_px, chunkSize)) { dialogState = DialogState.Chunk }
            HorizontalDivider()
            SettingsItem(stringResource(R.string.overlap_size), stringResource(R.string.overlap_size_px, overlapSize)) { dialogState = DialogState.Chunk }
        }
        SettingsSection(stringResource(R.string.app)) {
            SettingsItem(stringResource(R.string.preferences), stringResource(R.string.manage_save_dialog_and_source)) { dialogState = DialogState.Preferences }
            HorizontalDivider()
            SettingsItem(stringResource(R.string.about), stringResource(R.string.version_info_and_credits)) { dialogState = DialogState.About }
            HorizontalDivider()
            SettingsItem(stringResource(R.string.faqs), stringResource(R.string.show_frequently_asked_questions)) { dialogState = DialogState.FAQ }
        }
    }

    when (dialogState) {
        DialogState.Model -> ModelDialog(installedModels, activeModelName, viewModel,
            onSelect = { modelName ->
                val warning = viewModel.getModelWarning(modelName)
                if (warning != null) {
                    pendingModelSelection = modelName
                    warningState = ModelWarningState.Selection(modelName, warning)
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
            onConfirm = { selected -> dialogState = DialogState.None; viewModel.deleteModels(selected) { android.widget.Toast.makeText(context, context.getString(R.string.deleted_model, it), android.widget.Toast.LENGTH_SHORT).show() } },
            onDismiss = { dialogState = DialogState.None })
        DialogState.ImportProgress -> ImportProgressDialog(importProgress)
        DialogState.Chunk -> ChunkDialog(chunkSize, overlapSize,
            onChunkChange = { viewModel.setChunkSize(it) }, 
            onOverlapChange = { viewModel.setOverlapSize(it) },
            onDismiss = { dialogState = DialogState.None })
        DialogState.Preferences -> PreferencesDialog(context, onDismiss = { dialogState = DialogState.None })
        DialogState.About -> AboutDialog { dialogState = DialogState.None }
        DialogState.FAQ -> FAQDialog { dialogState = DialogState.None }
        DialogState.Download -> DownloadDialog(context, onDismiss = { dialogState = DialogState.Model })
        DialogState.None -> {}
    }

    warningState?.let { state ->
        when (state) {
            is ModelWarningState.Import -> ModelWarningDialog(
                modelName = state.modelName,
                warning = state.warning,
                onConfirm = { 
                    forceImport(viewModel, context, pendingImportUri, { importProgress = it }, { dialogState = if (it) DialogState.ImportProgress else DialogState.None }, { warningState = null; pendingImportUri = null })
                },
                onDismiss = { warningState = null; pendingImportUri = null }
            )
            is ModelWarningState.Selection -> ModelWarningDialog(
                modelName = state.modelName,
                warning = state.warning,
                onConfirm = { 
                    pendingModelSelection?.let { viewModel.setActiveModelByName(it); activeModelName = it }
                    pendingModelSelection = null
                    warningState = null
                },
                onDismiss = { warningState = null; pendingModelSelection = null }
            )
            ModelWarningState.GenericImport -> GenericWarningDialog(
                onConfirm = { forceImport(viewModel, context, pendingImportUri, { importProgress = it }, { dialogState = if (it) DialogState.ImportProgress else DialogState.None }, { warningState = null; pendingImportUri = null }) },
                onDismiss = { warningState = null; pendingImportUri = null }
            )
            is ModelWarningState.Error -> AlertDialog(
                onDismissRequest = { warningState = null }, 
                title = { Text(stringResource(R.string.import_error)) }, 
                text = { Text(state.message) }, 
                confirmButton = { TextButton(onClick = { warningState = null }) { Text(stringResource(R.string.ok)) } }
            )
        }
    }
}

sealed class DialogState { object None : DialogState(); object Model : DialogState(); object Delete : DialogState(); object ImportProgress : DialogState(); object Chunk : DialogState(); object About : DialogState(); object FAQ : DialogState(); object Preferences : DialogState(); object Download : DialogState() }

sealed class ModelWarningState {
    data class Import(
        val modelName: String,
        val warning: com.je.dejpeg.ModelManager.ModelWarning
    ) : ModelWarningState()
    
    data class Selection(
        val modelName: String,
        val warning: com.je.dejpeg.ModelManager.ModelWarning
    ) : ModelWarningState()
    
    object GenericImport : ModelWarningState()
    data class Error(val message: String) : ModelWarningState()
}

fun parseWarningError(err: String, context: android.content.Context, isImport: Boolean): ModelWarningState {
    return when {
        err.startsWith("MODEL_WARNING:") -> {
            val parts = err.removePrefix("MODEL_WARNING:").split(":", limit = 5)
            val warning = com.je.dejpeg.ModelManager.ModelWarning(
                title = parts.getOrNull(1) ?: context.getString(R.string.warning),
                message = parts.getOrNull(2) ?: "",
                positiveButtonText = parts.getOrNull(3) ?: context.getString(if (isImport) R.string.import_anyway else R.string.use_anyway),
                negativeButtonText = parts.getOrNull(4) ?: context.getString(R.string.cancel)
            )
            val modelName = parts.getOrNull(0) ?: ""
            if (isImport) ModelWarningState.Import(modelName, warning) else ModelWarningState.Selection(modelName, warning)
        }
        err == "GENERIC_MODEL_WARNING" -> ModelWarningState.GenericImport
        else -> ModelWarningState.Error(err)
    }
}

data class FAQSectionData(
    val title: String,
    val content: String?,
    val subSections: List<Pair<String, String>>?
)

@Composable fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(bottom = 8.dp))
    Card(modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)) { Column { content() } }
}

@Composable fun SettingsItem(title: String, subtitle: String, onClick: () -> Unit) {
    val haptic = com.je.dejpeg.ui.utils.rememberHapticFeedback()
    Row(modifier = Modifier.fillMaxWidth().clickable(onClick = { haptic.light(); onClick() }).padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Spacer(modifier = Modifier.height(4.dp))
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable fun ModelDialog(models: List<String>, active: String?, viewModel: ProcessingViewModel, onSelect: (String) -> Unit, onImport: () -> Unit, onDelete: () -> Unit, onDownload: () -> Unit, onDismiss: () -> Unit) {
    val haptic = com.je.dejpeg.ui.utils.rememberHapticFeedback()
    val context = LocalContext.current
    AlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(R.string.model_management)) },
        text = {
            Column {
                if (models.isEmpty()) Text(stringResource(R.string.no_models_installed))
                else models.forEach { name ->
                    val warning = viewModel.getModelWarning(name)
                    Row(modifier = Modifier.fillMaxWidth().clickable { haptic.medium(); onSelect(name) }.padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(name, fontWeight = if (name == active) FontWeight.Bold else FontWeight.Normal)
                                if (warning != null) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(stringResource(R.string.warning_emoji), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                                }
                            }
                            if (name == active) Text(stringResource(R.string.active), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                            if (warning != null) {
                                val warningTitleId = context.resources.getIdentifier(warning.title, "string", context.packageName)
                                val warningTitle = if (warningTitleId != 0) stringResource(warningTitleId) else warning.title
                                Text(warningTitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                            }
                        }
                        RadioButton(selected = name == active, onClick = { haptic.medium(); onSelect(name) })
                    }
                }
            }
        },
        confirmButton = { Row { TextButton(onClick = { haptic.light(); onDownload() }) { Text(stringResource(R.string.download)) }; Spacer(modifier = Modifier.width(8.dp)); TextButton(onClick = { haptic.medium(); onImport() }) { Text(stringResource(R.string.import_text)) }; if (models.isNotEmpty()) { Spacer(modifier = Modifier.width(8.dp)); TextButton(onClick = { haptic.light(); onDelete() }) { Text(stringResource(R.string.delete)) } }; Spacer(modifier = Modifier.width(8.dp)); } })
}

@Composable fun DeleteDialog(models: List<String>, onConfirm: (List<String>) -> Unit, onDismiss: () -> Unit) {
    val selected = remember { mutableStateListOf<String>() }
    val haptic = com.je.dejpeg.ui.utils.rememberHapticFeedback()
    AlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(R.string.delete_models)) },
        text = { Column { models.forEach { name -> Row(modifier = Modifier.fillMaxWidth().clickable { haptic.light(); if (selected.contains(name)) selected.remove(name) else selected.add(name) }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) { Checkbox(checked = selected.contains(name), onCheckedChange = { haptic.light(); if (it) selected.add(name) else selected.remove(name) }); Spacer(modifier = Modifier.width(8.dp)); Text(name) } } } },
        confirmButton = { TextButton(onClick = { haptic.heavy(); onConfirm(selected.toList()) }) { Text(stringResource(R.string.delete)) } },
        dismissButton = { TextButton(onClick = { haptic.light(); onDismiss() }) { Text(stringResource(R.string.cancel)) } })
}

@Composable
fun DownloadDialog(context: android.content.Context, onDismiss: () -> Unit) {
    val models = listOf(
        stringResource(R.string.fbcnn_model) to "https://github.com/jeeneo/fbcnn-mobile/releases/latest",
        stringResource(R.string.scunet_model) to "https://github.com/jeeneo/scunet-mobile/releases/latest",
        stringResource(R.string.experimental_models) to "https://github.com/jeeneo/dejpeg-experimental/releases/latest"
    )
    
    AlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(R.string.download_models)) },
        text = {
            Column(Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.choose_model_to_download), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(16.dp))
                models.forEach { (name, link) ->
                    Button(onClick = {
                        try { context.startActivity(Intent(Intent.ACTION_VIEW, link.toUri())) }
                        catch (_: Exception) { android.widget.Toast.makeText(context, context.getString(R.string.cannot_open_link), android.widget.Toast.LENGTH_SHORT).show() }
                    }, Modifier.fillMaxWidth()) { Text(name) }
                    Spacer(Modifier.height(8.dp))
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } })
}

@Composable fun ImportProgressDialog(progress: Int) {
    AlertDialog(onDismissRequest = {}, title = { Text(stringResource(R.string.importing_model)) }, text = { Column(horizontalAlignment = Alignment.CenterHorizontally) { LinearProgressIndicator(progress = { progress / 100f }, modifier = Modifier.fillMaxWidth()); Spacer(modifier = Modifier.height(8.dp)); Text(stringResource(R.string.progress_percent, progress)) } }, confirmButton = {})
}

@Composable
fun ChunkDialog(chunk: Int, overlap: Int, onChunkChange: (Int) -> Unit, onOverlapChange: (Int) -> Unit, onDismiss: () -> Unit) {
    var chunkSize by remember { mutableIntStateOf(chunk) }
    var overlapSize by remember { mutableIntStateOf(overlap) }
    val haptic = com.je.dejpeg.ui.utils.rememberHapticFeedback()
    val chunkPowers = generateSequence(16) { it * 2 }.takeWhile { it <= 2048 }.toList()
    val overlapPowers = generateSequence(8) { it * 2 }.takeWhile { it <= 256 }.toList()

    @Composable
    fun powerSlider(label: String, value: Int, powers: List<Int>, onChange: (Int) -> Unit) {
        var index by remember(value) { mutableIntStateOf(powers.indexOf(value).coerceAtLeast(0)) }
        Column {
            Text(label)
            Slider(value = index.toFloat(), onValueChange = { val newIdx = it.roundToInt().coerceIn(powers.indices); if (newIdx != index) { index = newIdx; haptic.light(); onChange(powers[newIdx]) } }, valueRange = 0f..(powers.lastIndex.toFloat()), steps = powers.size - 2)
            Text("${powers[index]}")
        }
    }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(R.string.chunk_settings)) },
        text = { Column { powerSlider(stringResource(R.string.chunk_size), chunkSize, chunkPowers) { chunkSize = it }; Spacer(Modifier.height(16.dp)); powerSlider(stringResource(R.string.overlap_size), overlapSize, overlapPowers) { overlapSize = it }; Spacer(Modifier.height(16.dp)); Text(stringResource(R.string.note_chunk_overlap), style = MaterialTheme.typography.bodySmall) } },
        confirmButton = { TextButton(onClick = { haptic.medium(); android.util.Log.d("ChunkDialog", "Saving chunk_size: $chunkSize, overlap_size: $overlapSize"); onChunkChange(chunkSize); onOverlapChange(overlapSize); onDismiss() }) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onClick = { haptic.light(); onDismiss() }) { Text(stringResource(R.string.cancel)) } })
}

@Composable
fun AboutDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text( stringResource(R.string.dejpeg_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold )},
        text = {
            Column {
                Text(stringResource(R.string.non_destructive_restoration))
                Spacer(modifier = Modifier.height(16.dp))
                Text(stringResource(R.string.open_source_app_description))
                Spacer(modifier = Modifier.height(8.dp))
                Text(stringResource(R.string.license_text), style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.ok)) } }
    )
}

@Composable
fun PreferencesDialog(context: android.content.Context, onDismiss: () -> Unit) {
    val prefs = context.getSharedPreferences("AppPrefs", android.content.Context.MODE_PRIVATE)
    var skipSaveDialog by remember { mutableStateOf(prefs.getBoolean("skipSaveDialog", false)) }
    var defaultImageSource by remember { mutableStateOf(prefs.getString("defaultImageSource", null)) }
    var hapticFeedbackEnabled by remember { mutableStateOf(prefs.getBoolean("hapticFeedbackEnabled", true)) }
    val haptic = com.je.dejpeg.ui.utils.rememberHapticFeedback()
    
    AlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(R.string.preferences)) },
        text = {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                Text(stringResource(R.string.haptic_feedback), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.vibration_on_touch), style = MaterialTheme.typography.bodyMedium)
                        Text(stringResource(R.string.haptic_feedback_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(
                        checked = hapticFeedbackEnabled,
                        onCheckedChange = { enabled ->
                            prefs.edit { putBoolean("hapticFeedbackEnabled", enabled) }
                            hapticFeedbackEnabled = enabled
                        }
                    )
                }
                Spacer(Modifier.height(24.dp))
                HorizontalDivider()
                Spacer(Modifier.height(16.dp))
                Text(stringResource(R.string.save_dialog), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                    Text(stringResource(R.string.currently_shown_hidden, stringResource(if (skipSaveDialog) R.string.hidden else R.string.shown)), style = MaterialTheme.typography.bodyMedium)
                }
                Spacer(Modifier.height(8.dp))
                if (skipSaveDialog) Button(onClick = { 
                    haptic.medium()
                    prefs.edit { putBoolean("skipSaveDialog", false) }
                    skipSaveDialog = false 
                }, Modifier.fillMaxWidth()) { Text(stringResource(R.string.show_save_dialog)) }
                else Text(stringResource(R.string.save_dialog_shown), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(24.dp))
                HorizontalDivider()
                Spacer(Modifier.height(16.dp))
                Text(stringResource(R.string.default_image_source), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                    Text(stringResource(R.string.currently_default_source, defaultImageSource?.let { when (it) { "gallery" -> stringResource(R.string.gallery); "internal" -> stringResource(R.string.photos); "documents" -> stringResource(R.string.documents); "camera" -> stringResource(R.string.camera); else -> stringResource(R.string.none) } } ?: stringResource(R.string.none)), style = MaterialTheme.typography.bodyMedium)
                }
                Spacer(Modifier.height(8.dp))
                if (defaultImageSource != null) Button(onClick = { 
                    haptic.medium()
                    prefs.edit { remove("defaultImageSource") }
                    defaultImageSource = null 
                }, Modifier.fillMaxWidth()) { Text(stringResource(R.string.clear_default_source)) }
                else Text(stringResource(R.string.no_default_source), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = { TextButton(onClick = { haptic.light(); onDismiss() }) { Text(stringResource(R.string.close)) } })
}

@Composable
fun SliderSetting(
    label: String,
    value: Int,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    onChange: (Float) -> Unit
) {
    Text(label, fontWeight = FontWeight.Bold)
    Spacer(modifier = Modifier.height(8.dp))
    Slider(value = value.toFloat(), onValueChange = onChange, valueRange = range, steps = steps)
    Text(stringResource(R.string.chunk_size_px, value))
}

@Composable
fun ModelWarningDialog(
    modelName: String,
    warning: com.je.dejpeg.ModelManager.ModelWarning,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(context.resources.getIdentifier(warning.title, "string", context.packageName))) },
        text = { 
            Column {
                Text("Model: $modelName", fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                Text(stringResource(context.resources.getIdentifier(warning.message, "string", context.packageName)))
            }
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(context.resources.getIdentifier(warning.positiveButtonText, "string", context.packageName))) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(context.resources.getIdentifier(warning.negativeButtonText, "string", context.packageName))) } }
    )
}

@Composable
fun GenericWarningDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.unknown_model)) },
        text = { Text(stringResource(R.string.model_incompatible)) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.import_text)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}

fun forceImport(vm: ProcessingViewModel, ctx: android.content.Context, uri: android.net.Uri?, onProgress: (Int) -> Unit, setProgress: (Boolean) -> Unit, onComplete: () -> Unit) {
    uri?.let {
        setProgress(true)
        vm.importModel(ctx, it, force = true, onProgress = onProgress,
            onSuccess = { name -> setProgress(false); onComplete(); android.widget.Toast.makeText(ctx, ctx.getString(R.string.imported_model, name), android.widget.Toast.LENGTH_SHORT).show() },
            onError = { err -> setProgress(false); onComplete(); android.widget.Toast.makeText(ctx, err, android.widget.Toast.LENGTH_LONG).show() })
    }
}

@Composable
fun FAQDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val faqSections = remember { loadFAQSections(context) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(R.string.faqs), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) },
        text = { LazyColumn(modifier = Modifier.fillMaxWidth()) { items(faqSections.size) { FAQSection(faqSections[it].title, faqSections[it].content, faqSections[it].subSections) } } },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } })
}

@Composable
fun FAQSection(title: String, content: String?, subSections: List<Pair<String, String>>? = null) {
    var expanded by remember { mutableStateOf(false) }
    val context = LocalContext.current
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(title, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Icon(Icons.Filled.ExpandMore, contentDescription = if (expanded) stringResource(R.string.collapse) else stringResource(R.string.expand), modifier = Modifier.rotate(if (expanded) 180f else 0f))
        }
        if (expanded) {
            Column(modifier = Modifier.padding(start = 8.dp, bottom = 8.dp)) {
                content?.let { MarkdownText(it, MaterialTheme.typography.bodyMedium, MaterialTheme.colorScheme.onSurfaceVariant, context) }
                subSections?.forEach { (subTitle, subContent) ->
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(subTitle, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyLarge)
                    Spacer(modifier = Modifier.height(4.dp))
                    MarkdownText(subContent, MaterialTheme.typography.bodyMedium, MaterialTheme.colorScheme.onSurfaceVariant, context)
                }
            }
        }
        HorizontalDivider()
    }
}

@Composable
fun MarkdownText(text: String, style: androidx.compose.ui.text.TextStyle, color: androidx.compose.ui.graphics.Color, context: android.content.Context) {
    val annotatedString = buildAnnotatedString {
        var lastIndex = 0
        """\[([^]]+)]\(([^)]+)\)""".toRegex().findAll(text).forEach { m ->
            append(text.substring(lastIndex, m.range.first))
            val start = length
            append(m.groupValues[1])
            addStyle(SpanStyle(color = color, textDecoration = TextDecoration.Underline), start, length)
            addStringAnnotation("URL", m.groupValues[2], start, length)
            lastIndex = m.range.last + 1
        }
        if (lastIndex < text.length) append(text.substring(lastIndex))
    }
    ClickableText(text = annotatedString, style = style.copy(color = color)) { offset ->
        annotatedString.getStringAnnotations("URL", offset, offset).firstOrNull()?.let { a ->
            try { context.startActivity(Intent(Intent.ACTION_VIEW, a.item.toUri())) }
            catch (_: Exception) { android.widget.Toast.makeText(context, context.getString(R.string.cannot_open_link_detail, a.item), android.widget.Toast.LENGTH_SHORT).show() }
        }
    }
}

fun loadFAQSections(context: android.content.Context): List<FAQSectionData> {
    val sections = mutableListOf<FAQSectionData>()
    try {
        context.assets.list("faq")?.filter { it.endsWith(".md") }?.forEach { fileName ->
            val lines = context.assets.open("faq/$fileName").bufferedReader().readText().lines()
            var title: String? = null
            var content = StringBuilder()
            var subSections = mutableListOf<Pair<String, String>>()
            var subTitle: String? = null
            var subContent = StringBuilder()
            lines.forEach { line ->
                when {
                    line.startsWith("## ") -> {
                        title?.let { sections.add(FAQSectionData(it, content.toString().trim().ifEmpty { null }, subSections.ifEmpty { null })) }
                        title = line.removePrefix("## ").trim()
                        content = StringBuilder()
                        subSections = mutableListOf()
                        subTitle = null
                        subContent = StringBuilder()
                    }
                    line.startsWith("### ") -> {
                        subTitle?.let { subSections.add(it to subContent.toString().trim()) }
                        subTitle = line.removePrefix("### ").trim()
                        subContent = StringBuilder()
                    }
                    else -> if (subTitle != null) subContent.appendLine(line) else content.appendLine(line)
                }
            }
            title?.let { mainTitle ->
                subTitle?.let { subT -> subSections.add(subT to subContent.toString().trim()) }
                sections.add(FAQSectionData(mainTitle, content.toString().trim().ifEmpty { null }, subSections.ifEmpty { null }))
            }
        }
    } catch (e: Exception) { e.printStackTrace() }
    return sections
}