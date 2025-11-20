package com.je.dejpeg.compose.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.je.dejpeg.R

@Composable
fun SaveImageDialog(
    defaultFilename: String,
    showSaveAllOption: Boolean = false,
    initialSaveAll: Boolean = false,
    hideOptions: Boolean = false,
    onDismissRequest: () -> Unit,
    onSave: (String, Boolean, Boolean) -> Unit
) {
    val lastDot = defaultFilename.lastIndexOf('.')
    var textState by remember { mutableStateOf(TextFieldValue(defaultFilename, TextRange(0, if (lastDot > 0) lastDot else defaultFilename.length))) }
    var saveAll by remember { mutableStateOf(initialSaveAll) }
    var skipNext by remember { mutableStateOf(false) }
    val haptic = com.je.dejpeg.compose.utils.rememberHapticFeedback()
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(stringResource(if (hideOptions) R.string.overwrite_image else R.string.save_image)) },
        text = {
            Column {
                if (hideOptions) Text(stringResource(R.string.already_exists), Modifier.padding(bottom = 12.dp))
                OutlinedTextField(
                    textState, { textState = it }, Modifier.fillMaxWidth(),
                    singleLine = true, label = { Text(stringResource(R.string.filename)) }
                )
                if (!hideOptions) {
                    Spacer(Modifier.height(12.dp))
                    if (showSaveAllOption) Row(
                        Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(stringResource(R.string.save_all))
                        Switch(
                            checked = saveAll,
                            onCheckedChange = {
                                haptic.light()
                                saveAll = it
                            }
                        )
                    }
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(stringResource(R.string.dont_show_dialog))
                        Switch(
                            checked = skipNext,
                            onCheckedChange = {
                                haptic.light()
                                skipNext = it
                            }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = { haptic.light(); onDismissRequest() },
                    modifier = Modifier.align(Alignment.CenterVertically)
                ) {
                    Text(stringResource(R.string.cancel))
                }
                Spacer(Modifier.weight(1f))
                Button({
                    haptic.medium()
                    onSave(textState.text.trim(), saveAll, skipNext)
                    onDismissRequest()
                }) { Text(stringResource(R.string.save)) }
            }
        },
        dismissButton = {}
    )
}

@Composable
fun RemoveImageDialog(
    imageFilename: String,
    hasOutput: Boolean,
    onDismissRequest: () -> Unit,
    onRemove: () -> Unit,
    onSaveAndRemove: () -> Unit
) {
    val haptic = com.je.dejpeg.compose.utils.rememberHapticFeedback()
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(stringResource(R.string.remove_image_title)) },
        text = { Text(stringResource(R.string.remove_image_question, imageFilename)) },
        confirmButton = {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton({ haptic.light(); onDismissRequest() }) {
                    Text(stringResource(R.string.nope))
                }
                Spacer(Modifier.weight(0.4f))
                TextButton({ haptic.heavy(); onRemove(); onDismissRequest() }) {
                    Text(
                        stringResource(R.string.remove),
                        color = MaterialTheme.colorScheme.error
                    )
                }
                Spacer(Modifier.weight(0.1f))
                if (hasOutput) {
                    Button({ haptic.medium(); onSaveAndRemove(); onDismissRequest() }) {
                        Text(stringResource(R.string.save))
                    }
                }
            }
        },
        dismissButton = {}
    )
}

@Composable
fun CancelProcessingDialog(imageFilename: String, onDismissRequest: () -> Unit, onConfirm: () -> Unit) {
    val haptic = com.je.dejpeg.compose.utils.rememberHapticFeedback()
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(stringResource(R.string.stop_processing_title)) },
        text = { Text(stringResource(R.string.stop_processing_question, imageFilename)) },
        confirmButton = {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton({ haptic.light(); onDismissRequest() }) {
                    Text(stringResource(R.string.nope))
                }
                TextButton({ haptic.heavy(); onConfirm(); onDismissRequest() }) {
                    Text(stringResource(R.string.yes_stop), color = MaterialTheme.colorScheme.error)
                }
            }
        },
        dismissButton = {}
    )
}

@Composable
fun BatteryOptimizationDialog(onDismissRequest: () -> Unit, onOpenSettings: () -> Unit) {
    val haptic = com.je.dejpeg.compose.utils.rememberHapticFeedback()
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(stringResource(R.string.background_service_error)) },
        text = {
            Column {
                Text(stringResource(R.string.battery_optimization_explanation), Modifier.padding(bottom = 8.dp))
                Text(stringResource(R.string.open_battery_optimization_settings), style = MaterialTheme.typography.bodyMedium)
            }
        },
        dismissButton = { TextButton({ haptic.light(); onDismissRequest() }) { Text(stringResource(R.string.cancel)) } },
        confirmButton = { Button({ haptic.medium(); onOpenSettings(); onDismissRequest() }) { Text(stringResource(R.string.open_settings)) } }
    )
}