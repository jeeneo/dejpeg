package com.je.dejpeg.compose.ui.components

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.net.toUri
import com.je.dejpeg.R
import com.je.dejpeg.compose.utils.CacheManager
import com.je.dejpeg.compose.utils.rememberHapticFeedback

@Composable
fun AboutDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val haptic = rememberHapticFeedback()
    val versionName = try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "Unknown"
    } catch (_: Exception) { "Unknown" }
    
    StyledAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${stringResource(R.string.app_name)} v$versionName", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text(stringResource(R.string.non_destructive_restoration))
                Spacer(Modifier.height(16.dp))
                Text(stringResource(R.string.open_source_app_description))
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.license_text), style = MaterialTheme.typography.bodySmall)
            }
        },
        dismissButton = {
            TextButton(onClick = {
                haptic.medium()
                try { context.startActivity(Intent(Intent.ACTION_VIEW, "https://codeberg.org/dryerlint/dejpeg".toUri())) } catch (_: Exception) { }
            }) { Text(stringResource(R.string.source_code)) }
        },
        confirmButton = { Button(onClick = { haptic.light(); onDismiss() }) { Text(stringResource(R.string.ok)) } }
    )
}

@Composable
fun ImportProgressDialog(progress: Int) {
    StyledAlertDialog(
        onDismissRequest = {},
        title = { Text(stringResource(R.string.importing_model)) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                LinearProgressIndicator(progress = { progress / 100f }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.progress_percent, progress))
            }
        },
        confirmButton = {}
    )
}

@Composable
fun LoadingDialog(
    title: String,
    message: String? = null,
    progress: Float? = null,
    progressText: String? = null
) {
    Dialog(
        onDismissRequest = { },
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = DialogDefaults.Shape,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                message?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                if (progress != null) {
                    LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                } else {
                    CircularProgressIndicator(modifier = Modifier.size(48.dp))
                }
                progressText?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
    }
}

@Composable
fun SaveImageDialog(
    defaultFilename: String,
    showSaveAllOption: Boolean = false,
    initialSaveAll: Boolean = false,
    hideOptions: Boolean = false,
    onDismissRequest: () -> Unit,
    onSave: (String, Boolean, Boolean) -> Unit
) {
    val fileExt = defaultFilename.substringBeforeLast('.', defaultFilename)
    var textState by remember { mutableStateOf(TextFieldValue(fileExt, TextRange(0, fileExt.length))) }
    var saveAll by remember { mutableStateOf(initialSaveAll) }
    var skipNext by remember { mutableStateOf(false) }
    val haptic = rememberHapticFeedback()
    
    StyledAlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(stringResource(if (hideOptions) R.string.overwrite_image else R.string.save_image)) },
        text = {
            Column {
                if (hideOptions) Text(stringResource(R.string.already_exists), Modifier.padding(bottom = 12.dp))
                OutlinedTextField(textState, { textState = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text(stringResource(R.string.filename)) })
                if (!hideOptions) {
                    Spacer(Modifier.height(12.dp))
                    if (showSaveAllOption) {
                        MaterialSwitchRow(stringResource(R.string.save_all), saveAll, { haptic.light(); saveAll = it }, Modifier.fillMaxWidth())
                    }
                    MaterialSwitchRow(stringResource(R.string.dont_show_dialog), skipNext, { haptic.light(); skipNext = it }, Modifier.fillMaxWidth())
                }
            }
        },
        dismissButton = {
            TextButton(onClick = { haptic.light(); onDismissRequest() }) {
                Text(stringResource(R.string.cancel))
            }
        },
        confirmButton = {
            Button(onClick = {
                haptic.medium()
                val sanitizedFilename = textState.text.trim().replace(Regex("[/\\\\:*?\"<>|]"), "_").takeIf { it.isNotBlank() } ?: "image"
                onSave(sanitizedFilename, saveAll, skipNext)
                onDismissRequest()
            }) {
                Text(stringResource(R.string.save))
            }
        }
    )
}

@Composable
fun RemoveImageDialog(
    imageFilename: String,
    hasOutput: Boolean,
    imageId: String,
    context: android.content.Context,
    onDismissRequest: () -> Unit,
    onRemove: () -> Unit,
    onSaveAndRemove: () -> Unit
) {
    val haptic = rememberHapticFeedback()
    StyledAlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(stringResource(R.string.remove_image_title)) },
        text = { Text(stringResource(R.string.remove_image_question, imageFilename)) },
        dismissButton = {
            TextButton({ haptic.light(); onDismissRequest() }) {
                Text(stringResource(R.string.nope))
            }
        },
        confirmButton = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton({ 
                    haptic.heavy()
                    CacheManager.deleteRecoveryPair(context, imageId, deleteProcessed = true, deleteUnprocessed = true)
                    onRemove()
                    onDismissRequest()
                }) {
                    Text(
                        stringResource(R.string.remove),
                        color = MaterialTheme.colorScheme.error
                    )
                }
                if (hasOutput) {
                    Button({ haptic.medium(); onSaveAndRemove(); onDismissRequest() }) {
                        Text(stringResource(R.string.save))
                    }
                }
            }
        }
    )
}

@Composable
fun CancelProcessingDialog(imageFilename: String? = null, onDismissRequest: () -> Unit, onConfirm: () -> Unit) {
    val haptic = rememberHapticFeedback()
    BaseDialog(
        title = stringResource(R.string.stop_processing_title),
        message = if (imageFilename != null) {
            stringResource(R.string.stop_processing_question, imageFilename)
        } else {
            stringResource(R.string.stop_processing_all_question)
        },
        onDismiss = onDismissRequest,
        confirmButtonText = stringResource(R.string.yes_stop),
        onConfirm = { onConfirm(); onDismissRequest() },
        dismissButtonText = stringResource(R.string.nope),
        onDismissButton = onDismissRequest,
        onConfirmHaptic = { haptic.heavy() },
        onDismissHaptic = { haptic.light() }
    )
}

@Composable
fun DeprecatedModelWarningDialog(
    modelName: String,
    warning: com.je.dejpeg.compose.ModelManager.ModelWarning,
    onContinue: () -> Unit,
    onGoToSettings: () -> Unit
) {
    val haptic = rememberHapticFeedback()
    BaseDialog(
        title = stringResource(warning.titleResId),
        content = {
            Column {
                Text(
                    "Active model: $modelName",
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(stringResource(warning.messageResId))
            }
        },
        onDismiss = onContinue,
        confirmButtonText = stringResource(R.string.ok),
        onConfirm = onContinue,
        dismissButtonText = stringResource(R.string.go_to_settings),
        onDismissButton = onGoToSettings,
        onConfirmHaptic = { haptic.medium() },
        onDismissHaptic = { haptic.light() }
    )
}

@Composable
fun StarterModelDialog(onDismiss: () -> Unit) {
    val haptic = rememberHapticFeedback()
    StyledAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.starter_model_title)) },
        text = {
            Column {
                Text(stringResource(R.string.starter_model_message))
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    haptic.light()
                    onDismiss()
                }
            ) {
                Text(stringResource(R.string.got_it))
            }
        }
    )
}

@Composable
fun ModelInfoDialog(modelName: String, infoText: String, onDismiss: () -> Unit) {
    val haptic = rememberHapticFeedback()
    
    StyledAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(modelName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) },
        text = {
            Text(infoText, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        },
        confirmButton = {
            Button(onClick = { haptic.light(); onDismiss() }) {
                Text(stringResource(R.string.ok))
            }
        }
    )
}


